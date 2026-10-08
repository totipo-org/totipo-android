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
import org.totipo.android.provider.ImmutableCandidateClassifier;
import org.totipo.android.provider.ImmutableCandidateClassifier.*;
import org.totipo.android.provider.ProviderSnapshot.Scan;
import org.totipo.spi.ObjectWrite;
import org.totipo.storage.nio.NioTotipoStore;
import static org.totipo.android.reconcile.ImmutableCandidateImporter.*;

/**
 * Foreground, synchronous worker-thread lifecycle wrapper. This object exclusively owns its
 * lease and every session; callers own only this wrapper and must eventually close it.
 * No raw session/store/root escapes. UI code must dispatch calls to a bounded worker supplied
 * by its future product lifecycle. No Activity lifecycle or worker is installed here.
 *
 * Credentials are operation inputs, consumed and cleared even on rejection. Supply a fresh,
 * exclusively owned buffer containing the CURRENT password for each open/reconcile/change.
 * No credential is retained for session resumption. Read-only captured scans need no rescan.
 * Cancellation is cooperative: never interrupt the worker to cancel filesystem publication.
 */
public final class ForegroundVaultCoordinator implements AutoCloseable {
    public enum State { OPENING, OPEN, RECONCILING_READ_ONLY, CHANGING_PASSWORD,
        CLOSING_FOR_IMPORT, IMPORTING, REOPENING, CLOSING, FAILED_CLOSED, CLOSED }
    public enum Completion { RETAINED_SESSION, RESTORED_SESSION, CANCELLED,
        SESSION_FAILURE, CLOSE_FAILED, BATCH_STOPPED, SESSION_REOPEN_FAILED,
        RECONCILIATION_APPLIED_BUT_SESSION_REOPEN_FAILED }
    public enum Reopen { NOT_NEEDED, RESTORED, FAILED, SKIPPED_UNSAFE }
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
     * all siblings. Restored observation does not resolve an uncertain publication retroactively. */
    public record Report(Completion completion, ImmutableCandidateClassifier.Result evidence,
                         List<Item> items, List<RevisionId> unattempted, RevisionId publicationFailureId, Reopen reopen,
                         OpenResult openFailure, Throwable failure) {
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
    private void requireOpen() {
        if (state != State.OPEN) throw new IllegalStateException("Foreground vault is " + state);
    }
    private synchronized void begin(State next) { requireOpen(); state = next; }
    private synchronized void transition(State next) { state = next; }

    /** Serializes password rewrap with reconciliation and closure without changing core semantics.
     * Caller must use the correct current credential for subsequent reopening. */
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

    public Report reconcile(Scan scan, char[] credential, Cancellation cancellation) {
        Objects.requireNonNull(credential);
        boolean entered = false;
        boolean restoreOpen = false;
        ImmutableCandidateClassifier.Result evidence = null;
        List<Selection> plan = List.of();
        List<Item> items = new ArrayList<>();
        try {
            Objects.requireNonNull(scan); Objects.requireNonNull(cancellation);
            begin(State.RECONCILING_READ_ONLY); entered = true;
            if (cancelled(cancellation)) {
                restoreOpen = true;
                return report(Completion.CANCELLED, null, items, plan, Reopen.NOT_NEEDED, null, null);
            }
            evidence = ImmutableCandidateClassifier.classify(scan, session);
            // These conditions mean the supplied active session contract has failed. Do not
            // churn it to retry validation or publish previously validated unrelated groups.
            if (evidence.groups().stream().flatMap(g -> g.siblings().stream())
                    .anyMatch(c -> c.kind() == Kind.VALIDATION_UNAVAILABLE)) {
                var failure = new IllegalStateException("Active session validation unavailable");
                failClosed(failure);
                return report(Completion.SESSION_FAILURE, evidence, items, plan, Reopen.NOT_NEEDED, null, failure);
            }
            // Preserve integrity warnings across subsequent partial scans; absence cannot clear them.
            var contradictions = evidence.groups().stream()
                    .filter(g -> select(g).status == Status.CONTRADICTION).map(Group::objectId).collect(Collectors.toList());
            var accumulated = new java.util.TreeSet<RevisionId>(Comparator.comparing(RevisionId::hex));
            accumulated.addAll(integrityProblems); accumulated.addAll(contradictions);
            integrityProblems = Collections.unmodifiableList(new ArrayList<>(accumulated));
            ImportPlan importPlan = plan(evidence);
            plan = importPlan.objects;
            boolean stopBeforeClosure = cancelled(cancellation);
            if (stopBeforeClosure || plan.isEmpty()) {
                boolean stop = stopBeforeClosure;
                restoreOpen = true;
                return report(stop ? Completion.CANCELLED : Completion.RETAINED_SESSION,
                        evidence, items, plan, Reopen.NOT_NEEDED, null, null);
            }
            transition(State.CLOSING_FOR_IMPORT);
            try { operations.close(session); session = null; }
            catch (RuntimeException failure) {
                transition(State.FAILED_CLOSED); // Session remains owned; no second store, even if close partly ran.
                return report(Completion.CLOSE_FAILED, evidence, items, plan, Reopen.NOT_NEEDED, null, failure);
            }
            transition(State.IMPORTING);
            boolean stopped = false;
            for (Selection selected : plan) {
                if (cancelled(cancellation)) { stopped = true; break; }
                ObjectWrite write;
                try { write = operations.publish(lease, selected); }
                catch (Exception failure) {
                    transition(State.FAILED_CLOSED);
                    return new Report(Completion.BATCH_STOPPED, evidence, items,
                            plan.stream().map(s -> s.id).filter(id -> !id.equals(selected.id)
                                    && items.stream().noneMatch(i -> i.id().equals(id))).collect(Collectors.toList()),
                            selected.id, Reopen.SKIPPED_UNSAFE, null, failure);
                }
                items.add(new Item(selected.id, map(write), write));
                // All three StoreFailure reasons are coarse: none proves a healthy root or an
                // object-local fault. Abort on Failed; Uncertain additionally forbids auto-reopen.
                if (write instanceof ObjectWrite.Uncertain || write instanceof ObjectWrite.Failed) {
                    transition(State.FAILED_CLOSED);
                    return report(Completion.BATCH_STOPPED, evidence, items, plan, Reopen.SKIPPED_UNSAFE, null, null);
                }
            }
            transition(State.REOPENING);
            OpenResult failure = establish(credential);
            stopped = stopped || cancelled(cancellation);
            restoreOpen = failure == null;
            return report(failure == null ? stopped ? Completion.CANCELLED : Completion.RESTORED_SESSION
                            : reopenFailure(items),
                    evidence, items, plan, failure == null ? Reopen.RESTORED : Reopen.FAILED, failure, null);
        } catch (Exception failure) {
            if (!entered) {
                if (failure instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException(failure);
            }
            State phase = lifecycle();
            failClosed(failure);
            return report(phase == State.REOPENING ? reopenFailure(items)
                            : phase == State.IMPORTING ? Completion.BATCH_STOPPED : Completion.SESSION_FAILURE,
                    evidence, items, plan, phase == State.REOPENING ? Reopen.FAILED
                            : phase == State.IMPORTING ? Reopen.SKIPPED_UNSAFE : Reopen.NOT_NEEDED, null, failure);
        } finally {
            Arrays.fill(credential, '\0');
            // Keep the gate busy through result construction and credential cleanup.
            if (restoreOpen && (lifecycle() == State.RECONCILING_READ_ONLY || lifecycle() == State.REOPENING)) {
                transition(State.OPEN);
            }
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
    private static Completion reopenFailure(List<Item> items) {
        return items.stream().anyMatch(i -> i.status() == Status.IMPORTED)
                ? Completion.RECONCILIATION_APPLIED_BUT_SESSION_REOPEN_FAILED : Completion.SESSION_REOPEN_FAILED;
    }
    private static Report report(Completion completion, ImmutableCandidateClassifier.Result evidence,
                                 List<Item> items, List<Selection> plan, Reopen reopen,
                                 OpenResult openFailure, Throwable failure) {
        var attempted = items.stream().map(Item::id).collect(Collectors.toList());
        return new Report(completion, evidence, items, plan.stream().map(s -> s.id)
                .filter(id -> !attempted.contains(id)).collect(Collectors.toList()), null, reopen, openFailure, failure);
    }
    private OpenResult establish(char[] credential) throws Exception {
        OpenResult result = operations.open(lease, credential);
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
            if (state != State.OPEN && state != State.FAILED_CLOSED) throw new IllegalStateException("Foreground vault busy");
            state = State.CLOSING;
        }
        try {
            if (session != null) { operations.close(session); session = null; }
            lease.close(); transition(State.CLOSED);
        } catch (RuntimeException failure) { transition(State.FAILED_CLOSED); throw failure; }
    }

    // Package-private faults/phase instrumentation only. No injection API ships to public callers.
    static class Operations {
        OpenResult open(LocalReplicaOwner.Lease lease, char[] password) throws IOException {
            return Totipo.open(NioTotipoStore.openPrivate(lease.root()), password);
        }
        void close(VaultSession session) { session.close(); }
        ObjectWrite publish(LocalReplicaOwner.Lease lease, Selection selection) throws IOException {
            try (var store = NioTotipoStore.openPrivate(lease.root())) { return publishExact(store, selection); }
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
