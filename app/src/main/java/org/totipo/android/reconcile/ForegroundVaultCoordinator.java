package org.totipo.android.reconcile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import org.totipo.*;
import org.totipo.android.LocalReplicaOwner;
import org.totipo.android.RevealedTotp;
import java.time.Instant;
import org.totipo.android.provider.ImmutableCandidateClassifier;
import org.totipo.android.provider.ImmutableCandidateClassifier.*;
import org.totipo.android.provider.ProviderSnapshot.Scan;
import org.totipo.spi.ObjectWrite;
import static org.totipo.android.reconcile.ImmutableCandidateImporter.*;

/** Worker-thread foreground owner of one root lease, coordinated storage domain and session.
 * Scans are already captured outside the store gate. Sync needs no credential. The same
 * authenticated session validates candidates and observes local changes after refresh.
 * Busy foreground operations are rejected; callers dispatch through a bounded worker.
 * Cancel cooperatively, never interrupt filesystem publication. */
public final class ForegroundVaultCoordinator implements AutoCloseable {
    public enum State { OPENING, OPEN, RECONCILING_READ_ONLY, CHANGING_PASSWORD,
        IMPORTING, REQUESTING_REFRESH, GENERATING_TOTP, STORAGE_UNSAFE, CLOSING, FAILED_CLOSED, CLOSED }
    public enum Completion { RETAINED_SESSION, REFRESH_REQUESTED, CANCELLED,
        SESSION_FAILURE, BATCH_STOPPED }
    public enum Refresh { NOT_REQUESTED, REQUESTED, SKIPPED_UNSAFE }
    /** Thread-safe, fast, nonthrowing flag query; do not cancel by interrupting a mutation worker. */
    public interface Cancellation { boolean requested(); }
    /** The caller owns vault even on authentication/observation failure, and must close it.
     * A failed close retains that ownership for explicit retry; failure is never an Opened value. */
    public record Opening(ForegroundVaultCoordinator vault, OpenResult failure, Throwable cause) {}
    /** Detached descriptive projection: no state editor or session is exposed. */
    public record View(VaultFingerprint fingerprint, ObservationProgress observation,
                       List<ObservedToken> tokens, List<VaultDiagnostic> diagnostics, List<RevisionId> integrityProblems) {
        public View { tokens = frozen(tokens); diagnostics = frozen(diagnostics); integrityProblems = frozen(integrityProblems); }
    }
    public record ObservedToken(TokenId id, List<TokenDescriptor> alternatives,
                                List<RevisionId> heads, List<UnresolvedReference> unresolved, boolean conflict) {
        public ObservedToken { alternatives = frozen(alternatives); heads = frozen(heads); unresolved = frozen(unresolved); }
    }
    public record Item(RevisionId id, Status status, ObjectWrite publication) {}
    /** Actual outcomes only; unattempted IDs are separate. Evidence retains epoch/coverage and
     * all siblings. A refresh request is not a correlated observation completion acknowledgement. */
    public record Report(Completion completion, ImmutableCandidateClassifier.Result evidence,
                         List<Item> items, List<RevisionId> unattempted, RevisionId publicationFailureId, Refresh refresh,
                         ObservationProgress latestObservation, Throwable failure) {
        public Report { items = frozen(items); unattempted = frozen(unattempted); }
        public long count(Status status) { return items.stream().filter(i -> i.status() == status).count(); }
        public List<RevisionId> contradictions() { return excluded(Status.CONTRADICTION); }
        public List<RevisionId> deferredValidation() { return excluded(Status.DEFERRED_VALIDATION); }
        private List<RevisionId> excluded(Status status) {
            return evidence == null ? List.of() : evidence.groups().stream()
                    .filter(g -> select(g).status == status).map(Group::objectId).collect(Collectors.toList());
        }
    }

    private final LocalReplicaOwner.Lease lease;
    private final Operations operations;
    private State state = State.OPENING;
    private VaultSession session;
    private CoordinatedPrivateStore store;
    private List<RevisionId> integrityProblems = List.of();

    private ForegroundVaultCoordinator(LocalReplicaOwner.Lease lease, Operations operations) {
        this.lease = lease; this.operations = operations;
    }
    public static Opening open(LocalReplicaOwner owner, char[] credential) throws IOException {
        return open(owner, credential, new Operations());
    }
    static Opening open(LocalReplicaOwner owner, char[] credential, Operations operations) throws IOException {
        Objects.requireNonNull(credential);
        try {
            var vault = new ForegroundVaultCoordinator(owner.acquire(), operations);
            try {
                OpenResult failure = vault.establish(credential);
                if (failure == null) vault.transition(State.OPEN);
                return new Opening(vault, failure, null);
            } catch (Exception failure) {
                vault.failClosed(failure);
                return new Opening(vault, null, failure);
            }
        } finally { Arrays.fill(credential, '\0'); }
    }
    public record Creation(ForegroundVaultCoordinator vault, CreateVaultResult failure, Throwable cause) {}
    public enum LocalStatus { ABSENT, PRESENT, UNSAFE, UNAVAILABLE }
    public record Discovery(ForegroundVaultCoordinator vault, LocalStatus status, Throwable cause) {}
    /** Pre-session bounded SPI observation using the same lease/domain. Failed closure
     * returns the owned coordinator for explicit retry, never releases an assumed close. */
    public static Discovery discover(LocalReplicaOwner owner) throws IOException {
        return discover(owner, new Operations());
    }
    static Discovery discover(LocalReplicaOwner owner, Operations operations) throws IOException {
        var vault = new ForegroundVaultCoordinator(owner.acquire(), operations);
        LocalStatus status = LocalStatus.UNAVAILABLE;
        Throwable failure = null;
        try {
            vault.store = vault.operations.storage(vault.lease);
            var read = vault.store.observeVault();
            status = read instanceof org.totipo.spi.BoundedRead.Absent ? LocalStatus.ABSENT
                    : read instanceof org.totipo.spi.BoundedRead.Present ? LocalStatus.PRESENT
                    : read instanceof org.totipo.spi.BoundedRead.Unavailable unavailable
                            && unavailable.reason() != org.totipo.spi.StoreFailure.UNSAFE_NAMESPACE
                            ? LocalStatus.UNAVAILABLE : LocalStatus.UNSAFE;
        } catch (Exception cause) { failure = cause; }
        vault.transition(State.FAILED_CLOSED);
        try { vault.close(); }
        catch (RuntimeException cause) { failure = cause; }
        return new Discovery(vault, status, failure);
    }
    public static Creation create(LocalReplicaOwner owner, char[] credential) throws IOException {
        return create(owner, credential, new Operations());
    }
    static Creation create(LocalReplicaOwner owner, char[] credential, Operations operations) throws IOException {
        Objects.requireNonNull(credential);
        try {
            var vault = new ForegroundVaultCoordinator(owner.acquire(), operations);
            try {
                vault.store = operations.storage(vault.lease);
                CreateVaultResult result = operations.create(vault.store, credential);
                if (!(result instanceof CreateVaultResult.Created created)) {
                    vault.transition(State.FAILED_CLOSED);
                    return new Creation(vault, result, null);
                }
                vault.session = created.session();
                operations.observe(vault.session);
                vault.transition(State.OPEN);
                return new Creation(vault, null, null);
            } catch (Exception failure) {
                vault.failClosed(failure);
                return new Creation(vault, null, failure);
            }
        } finally { Arrays.fill(credential, '\0'); }
    }
    public void requestRefresh() {
        begin(State.REQUESTING_REFRESH);
        try { operations.refresh(session, store); }
        finally { transition(State.OPEN); }
    }
    /** Signals only; callers schedule detached view reads outside the Java callback.
     * No Activity subscribes to Java or receives a state editor/session. */
    @android.annotation.TargetApi(30)
    public AutoCloseable observe(Runnable changed, Runnable failed) {
        requireOpen();
        var subscription = new AtomicReference<Flow.Subscription>();
        var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        session.states().subscribe(new Flow.Subscriber<VaultState>() {
            public void onSubscribe(Flow.Subscription next) {
                subscription.set(next);
                if (cancelled.get()) next.cancel(); else next.request(Long.MAX_VALUE);
            }
            public void onNext(VaultState ignored) { if (!cancelled.get()) changed.run(); }
            public void onError(Throwable ignored) { if (!cancelled.get()) failed.run(); }
            public void onComplete() { if (!cancelled.get()) failed.run(); }
        });
        return () -> { cancelled.set(true); var current = subscription.get(); if (current != null) current.cancel(); };
    }
    public synchronized State lifecycle() { return state; }
    public synchronized View view() {
        requireOpen();
        var observed = session.state();
        var tokens = observed.tokens().stream().map(token -> new ObservedToken(token.id(),
                token.alternatives().stream().map(TokenAlternative::descriptor).collect(Collectors.toList()),
                token.heads().stream().map(TokenHead::revision).collect(Collectors.toList()),
                token.unresolvedReferences(), token.hasConflict())).collect(Collectors.toList());
        return new View(session.fingerprint(), observed.observation(), tokens, observed.diagnostics(), integrityProblems);
    }
    public enum TotpStatus { AVAILABLE, UNAVAILABLE_NEEDS_ATTENTION, STALE, FAILED }
    public record TotpResult(TotpStatus status, RevealedTotp revealed) {
        @Override public String toString() { return "TotpResult[" + status + "]"; }
    }
    /** Only this operation resolves/uses session-scoped alternatives. Never retains one. */
    public TotpResult generateTotp(TokenId id, Instant now) {
        return generateTotp(id, now, null);
    }
    /** Product callers supply the detached row basis to reject a superseded displayed descriptor,
     * including changes whose asynchronous observation signal has not arrived yet. */
    public TotpResult generateTotp(TokenId id, Instant now, ObservedToken expected) {
        Objects.requireNonNull(id); Objects.requireNonNull(now);
        begin(State.GENERATING_TOTP);
        try {
            VaultState captured = session.state();
            var token = captured.token(id).orElse(null);
            if (!(captured.observation() instanceof ObservationProgress.Finished)
                    || !captured.diagnostics().isEmpty() || !integrityProblems.isEmpty()
                    || token == null || token.hasConflict() || !token.unresolvedReferences().isEmpty()
                    || token.alternatives().size() != 1
                    || token.alternatives().get(0).descriptor().status() != TokenStatus.ACTIVE)
                return new TotpResult(TotpStatus.UNAVAILABLE_NEEDS_ATTENTION, null);
            var alternative = token.alternatives().get(0);
            if (expected != null && (!id.equals(expected.id())
                    || expected.alternatives().size() != 1
                    || !alternative.descriptor().equals(expected.alternatives().get(0))
                    || !token.heads().stream().map(TokenHead::revision).collect(Collectors.toList()).equals(expected.heads())))
                return new TotpResult(TotpStatus.STALE, null);
            var code = operations.generateTotp(captured, alternative, now);
            if (session.state() != captured) return new TotpResult(TotpStatus.STALE, null);
            int digits = alternative.descriptor().digits();
            if (now.isBefore(code.validFrom()) || !now.isBefore(code.validUntil())
                    || !code.code().matches("[0-9]{" + digits + "}"))
                return new TotpResult(TotpStatus.FAILED, null);
            return new TotpResult(TotpStatus.AVAILABLE,
                    new RevealedTotp(id, code.code(), code.validFrom(), code.validUntil(), digits));
        } catch (RuntimeException unavailable) {
            return new TotpResult(TotpStatus.FAILED, null);
        } finally { transition(State.OPEN); }
    }
    private void requireOpen() {
        if (state != State.OPEN) throw new IllegalStateException("Foreground vault is " + state);
    }
    private synchronized void begin(State next) { requireOpen(); state = next; }
    private synchronized void transition(State next) { state = next; }

    /** Serializes password rewrap with reconciliation and closure without changing core semantics.
     * No wrapper projection or transport VAULT adoption occurs here. */
    public PasswordChangeResult changePassword(char[] current, char[] replacement) {
        Objects.requireNonNull(current); Objects.requireNonNull(replacement);
        boolean entered = false;
        try {
            begin(State.CHANGING_PASSWORD); entered = true;
            PasswordChangeResult result = session.changePassword(current, replacement);
            if (result == PasswordChangeResult.UNCERTAIN) {
                failClosed(new IllegalStateException("Password change uncertain; authentication must be re-established"));
            }
            return result;
        } catch (RuntimeException failure) {
            if (entered) failClosed(failure);
            throw failure;
        } finally {
            Arrays.fill(current, '\0'); Arrays.fill(replacement, '\0');
            if (entered && lifecycle() == State.CHANGING_PASSWORD) transition(State.OPEN);
        }
    }

    /** Explicit inbound immutable sync. No authentication, KDF, open or close on this path.
     * Refresh is requested only after the bridge scope releases its gate. The report records
     * the latest observation without claiming correlation to this refresh request. */
    public Report sync(Scan scan) { return sync(scan, () -> false); }
    public Report sync(Scan scan, Cancellation cancellation) {
        Objects.requireNonNull(scan); Objects.requireNonNull(cancellation);
        begin(State.RECONCILING_READ_ONLY);
        ImmutableCandidateClassifier.Result evidence = null;
        List<Selection> plan = List.of();
        List<Item> items = new ArrayList<>();
        try {
            if (cancelled(cancellation)) return report(Completion.CANCELLED, null, items, plan, Refresh.NOT_REQUESTED, null);
            evidence = ImmutableCandidateClassifier.classify(scan, session);
            if (evidence.groups().stream().flatMap(g -> g.siblings().stream())
                    .anyMatch(c -> c.unavailability() == Unavailability.SESSION_CLOSED)) {
                transition(State.FAILED_CLOSED);
                return report(Completion.SESSION_FAILURE, evidence, items, plan, Refresh.NOT_REQUESTED,
                        new SessionClosedException());
            }
            var accumulated = new java.util.TreeSet<RevisionId>(Comparator.comparing(RevisionId::hex));
            accumulated.addAll(integrityProblems);
            evidence.groups().stream().filter(g -> select(g).status == Status.CONTRADICTION)
                    .map(Group::objectId).forEach(accumulated::add);
            integrityProblems = frozen(new ArrayList<>(accumulated));
            // Validation-unavailable siblings defer their ID; unrelated positive evidence remains useful.
            plan = plan(evidence).objects;
            boolean stopBeforeImport = cancelled(cancellation);
            if (stopBeforeImport || plan.isEmpty()) return report(stopBeforeImport
                    ? Completion.CANCELLED : Completion.RETAINED_SESSION, evidence, items, plan, Refresh.NOT_REQUESTED, null);
            transition(State.IMPORTING);
            RevisionId failedId = null;
            Throwable failure = null;
            boolean unsafe = false;
            try (var bridge = store.bridge()) {
                for (Selection selected : plan) {
                    if (cancelled(cancellation)) break;
                    ObjectWrite write;
                    try { write = operations.publish(bridge, selected); }
                    catch (RuntimeException fault) { failedId = selected.id; failure = fault; unsafe = true; break; }
                    items.add(new Item(selected.id, map(write), write));
                    if (write instanceof ObjectWrite.Uncertain || write instanceof ObjectWrite.Failed) { unsafe = true; break; }
                }
                if (unsafe) bridge.markUnsafe();
            }
            // NEVER enter Java while the Android store gate is held.
            if (unsafe) {
                transition(State.STORAGE_UNSAFE);
                return new Report(Completion.BATCH_STOPPED, evidence, items, remaining(plan, items, failedId),
                        failedId, Refresh.SKIPPED_UNSAFE, null, failure);
            }
            transition(State.REQUESTING_REFRESH);
            operations.refresh(session, store);
            return report(cancelled(cancellation) ? Completion.CANCELLED : Completion.REFRESH_REQUESTED,
                    evidence, items, plan, Refresh.REQUESTED, null);
        } catch (RuntimeException failure) {
            // Keep the same session owned. Explicit close can always clean it up; no automatic reopen.
            transition(State.STORAGE_UNSAFE);
            return report(Completion.SESSION_FAILURE, evidence, items, plan, Refresh.SKIPPED_UNSAFE, failure);
        } finally {
            if (lifecycle() == State.RECONCILING_READ_ONLY || lifecycle() == State.IMPORTING
                    || lifecycle() == State.REQUESTING_REFRESH) transition(State.OPEN);
        }
    }
    // Internal application TCB only: public callers cannot supply descriptive groups/plans.
    // Real production evidence always comes from classify(scan, this.session) above.
    static final class ImportPlan {
        final String epoch;
        final org.totipo.android.provider.ProviderSnapshot.State coverage;
        final List<Selection> objects;
        final List<Provenance> provenance;
        private ImportPlan(ImmutableCandidateClassifier.Result evidence, List<Selection> objects) {
            epoch = evidence.transport().epoch(); coverage = evidence.transport().state();
            this.objects = frozen(objects);
            provenance = frozen(objects.stream().map(selected -> {
                Group group = evidence.groups().stream().filter(g -> g.objectId().equals(selected.id)).findFirst().orElseThrow();
                return new Provenance(selected.id, frozen(group.siblings().stream()
                        .map(c -> c.transport().document()).collect(Collectors.toList())));
            }).collect(Collectors.toList()));
        }
    }
    private record Provenance(RevisionId id, List<org.totipo.android.provider.ProviderSnapshot.Document> siblings) {}
    static ImportPlan plan(ImmutableCandidateClassifier.Result evidence) {
        return new ImportPlan(evidence, evidence.groups().stream().map(ImmutableCandidateImporter::select)
                .filter(s -> s.status == null).sorted(Comparator.comparing(s -> s.id.hex())).collect(Collectors.toList()));
    }
    private static <T> List<T> frozen(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
    private static boolean cancelled(Cancellation cancellation) {
        return cancellation.requested() || Thread.currentThread().isInterrupted();
    }
    private static List<RevisionId> remaining(List<Selection> plan, List<Item> items, RevisionId failureId) {
        var attempted = items.stream().map(Item::id).collect(Collectors.toList());
        return plan.stream().map(s -> s.id).filter(id -> !attempted.contains(id) && !id.equals(failureId))
                .collect(Collectors.toList());
    }
    private Report report(Completion completion, ImmutableCandidateClassifier.Result evidence,
                          List<Item> items, List<Selection> plan, Refresh refresh, Throwable failure) {
        return new Report(completion, evidence, items, remaining(plan, items, null), null, refresh,
                refresh == Refresh.SKIPPED_UNSAFE ? null : session.state().observation(), failure);
    }
    private OpenResult establish(char[] credential) throws Exception {
        store = operations.storage(lease);
        OpenResult result = operations.open(store, credential);
        if (!(result instanceof OpenResult.Opened opened)) {
            transition(State.FAILED_CLOSED); return result;
        }
        session = opened.session();
        operations.observe(session);
        return null;
    }
    private void failClosed(Throwable failure) {
        if (session != null) {
            try { operations.close(session); session = null; }
            catch (RuntimeException closeFailure) { failure.addSuppressed(closeFailure); }
        }
        transition(State.FAILED_CLOSED);
    }
    /** Busy close is rejected. Failed-close ownership is retained until a later close succeeds. */
    @Override public void close() {
        synchronized (this) {
            if (state == State.CLOSED) return;
            if (state != State.OPEN && state != State.FAILED_CLOSED && state != State.STORAGE_UNSAFE) throw new IllegalStateException("Foreground vault busy");
            state = State.CLOSING;
        }
        try {
            if (session != null) { operations.close(session); session = null; }
            if (store != null) { store.finishSessionClosure(); store.close(); store = null; }
            lease.close(); transition(State.CLOSED);
        } catch (RuntimeException failure) { transition(State.FAILED_CLOSED); throw failure; }
    }

    // Package-private faults/phase instrumentation only. No injection API ships to public callers.
    static class Operations {
        TotpCode generateTotp(VaultState state, TokenAlternative alternative, Instant now) {
            return state.generateTotp(alternative, now);
        }
        CoordinatedPrivateStore storage(LocalReplicaOwner.Lease lease) throws IOException {
            return CoordinatedPrivateStore.open(lease.root());
        }
        OpenResult open(CoordinatedPrivateStore store, char[] password) {
            return Totipo.open(store.transferSessionView(), password);
        }
        CreateVaultResult create(CoordinatedPrivateStore store, char[] password) {
            return Totipo.create(store.transferSessionView(), password);
        }
        void close(VaultSession session) { session.close(); }
        ObjectWrite publish(CoordinatedPrivateStore.Bridge bridge, Selection selection) {
            return bridge.publish(selection);
        }
        void refresh(VaultSession session, CoordinatedPrivateStore store) {
            if (store.exclusiveHeldByCurrentThread()) throw new IllegalStateException("Lock order violation");
            session.requestRefresh();
        }
        void observe(VaultSession session) throws Exception { awaitObservation(session); }
    }
    // Same released-core Flow boundary exercised by M1C on API 37. Flow needs API 30;
    // minSdk 26 is not a runtime qualification. Future unlock UX must gate supported runtime.
    @android.annotation.TargetApi(30)
    private static void awaitObservation(VaultSession session) throws Exception {
        var finished = new CountDownLatch(1);
        var subscription = new AtomicReference<Flow.Subscription>();
        var failure = new AtomicReference<Throwable>();
        session.states().subscribe(new Flow.Subscriber<>() {
            public void onSubscribe(Flow.Subscription next) { subscription.set(next); next.request(Long.MAX_VALUE); }
            public void onNext(VaultState next) {
                if (next.observation() instanceof ObservationProgress.Finished) finished.countDown();
            }
            public void onError(Throwable cause) { failure.set(cause); finished.countDown(); }
            public void onComplete() { finished.countDown(); }
        });
        // Suspension has already begun: preserve interruption, but do not abandon ownership
        // or expose an incompletely observed session. Cancellation is delivered in the report.
        boolean interrupted = Thread.interrupted();
        try {
            while (true) {
                try { finished.await(); break; }
                catch (InterruptedException stop) { interrupted = true; }
            }
            if (failure.get() != null) throw new IllegalStateException("Core observation failed", failure.get());
            if (!(session.state().observation() instanceof ObservationProgress.Finished)) {
                throw new IllegalStateException("Core closed before observation finished");
            }
        } finally {
            if (subscription.get() != null) subscription.get().cancel();
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}
