package org.totipo.android;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.totipo.OpenResult;
import org.totipo.CreateVaultResult;
import org.totipo.TokenId;
import java.time.Instant;
import org.totipo.android.provider.ProviderSnapshot.Scan;
import org.totipo.android.sync.SyncFolderBinding;
import org.totipo.android.reconcile.ImmutableCandidateImporter;
import org.totipo.android.reconcile.ForegroundVaultCoordinator;
import org.totipo.android.reconcile.ForegroundVaultCoordinator.View;

/** Application-owned product lifecycle. Credentials are exclusively transferred to commands.
 * One worker, one pending slot; product commands reject busy admission, observations coalesce.
 * Activity listeners receive detached snapshots on the dispatcher, never Java ownership.
 * Explicit lock and process death are the only M1J lock policies. */
public final class AndroidVaultController {
    public enum State { STARTING, NO_LOCAL_VAULT, LOCKED, UNLOCKING, CREATING, OPEN,
        BUSY, ERROR_LOCKED, LOCKING, FAILED_CLOSE, ERROR_OPEN }
    public enum Error { NONE, AUTHENTICATION_FAILED, LOCAL_VAULT_ABSENT, LOCAL_STORAGE_UNAVAILABLE,
        LOCAL_STORAGE_UNSAFE, OPEN_FAILED, CREATE_FAILED, OBSERVATION_DIAGNOSTICS, CLOSE_FAILED, BUSY }
    public record Snapshot(State state, Error error, String message, View view,
                           RevealedTotp revealedCode, long remainingSeconds, AddTokenOutcome addOutcome) {
        Snapshot(State state, Error error, String message, View view, RevealedTotp code, long seconds) {
            this(state, error, message, view, code, seconds, null);
        }
        Snapshot(State state, Error error, String message, View view) { this(state, error, message, view, null, 0); }
        @Override public String toString() { return "Snapshot[" + state + ", " + error + "]"; }
    }
    public interface Listener {
        void changed(Snapshot state);
        default void revealChanged(Snapshot state) { changed(state); }
    }
    // Android dispatch/thread policy is supplied by Application; JVM tests exercise this seam.
    interface Dispatcher {
        void post(Runnable action); void assertDispatchThread(); void assertWorkerThread();
        default Runnable after(long millis, Runnable action) { throw new UnsupportedOperationException("Scheduler required"); }
    }
    static class Backend {
        ForegroundVaultCoordinator.Discovery discover(LocalReplicaOwner owner) throws IOException {
            return ForegroundVaultCoordinator.discover(owner);
        }
        ForegroundVaultCoordinator.Opening open(LocalReplicaOwner owner, char[] credential) throws IOException {
            return ForegroundVaultCoordinator.open(owner, credential);
        }
        ForegroundVaultCoordinator.Creation create(LocalReplicaOwner owner, char[] credential) throws IOException {
            return ForegroundVaultCoordinator.create(owner, credential);
        }
        boolean authenticateCandidate(byte[] exact, char[] credential) {
            return org.totipo.android.reconcile.DetachedVaultAuthentication.authenticate(exact, credential);
        }
        ForegroundVaultCoordinator.Opening join(LocalReplicaOwner owner, byte[] exact, char[] credential,
                                                java.util.function.BooleanSupplier cancelled) throws IOException {
            return ForegroundVaultCoordinator.join(owner, exact, credential, cancelled);
        }
        ForegroundVaultCoordinator.TotpResult generateTotp(ForegroundVaultCoordinator vault, TokenId id, Instant now,
                                                         ForegroundVaultCoordinator.ObservedToken expected) {
            return vault.generateTotp(id, now, expected);
        }
    }
    public record SyncView(SyncFolderBinding.View binding, String message) {}
    private final SyncFolderBinding syncBinding;
    private final org.totipo.android.sync.ProviderIoLane providerIo = new org.totipo.android.sync.ProviderIoLane();
    private boolean providerActive;
    private java.util.concurrent.atomic.AtomicBoolean outboundCancelled;
    private SyncFolderBinding.Request outboundIdentity;
    private void cancelOutbound() { if (outboundCancelled != null) outboundCancelled.set(true); }
    private void notifySync() {
        for (Listener listener : new ArrayList<>(listeners)) deliver(listener);
    }
    private long sessionGeneration;
    private SyncView syncView = new SyncView(new SyncFolderBinding.View(
            SyncFolderBinding.Status.NOT_CONFIGURED, false, false), "Not configured");
    private String initialTreeUri;
    private long bindingGeneration;
    private boolean importIntegrityAttention;
    private final LocalReplicaOwner owner;
    private final Dispatcher dispatcher;
    private final Backend backend;
    private final TotpPresentation presentation;
    private final TotpPresentation.Time time;
    private long presentationEpoch;
    private boolean revealing;
    private Runnable revealTask;
    private boolean clearingPresentation;
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), action -> {
                Thread thread = new Thread(action, "Totipo-vault"); thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private final Set<Listener> listeners = new LinkedHashSet<>();
    private Snapshot snapshot = new Snapshot(State.STARTING, Error.NONE, "Checking local vault…", null);
    // Only worker accesses owned resources. Admission and snapshot fields use this monitor.
    private AddTokenOutcome addOutcome;
    private TokenChange pendingChange;
    private TokenChange.Result changeResult;
    public synchronized TokenChange pendingTokenChange() { return pendingChange; }
    public synchronized TokenChange.Result tokenChangeResult() { return changeResult; }
    /** Reserve the existing foreground admission slot; retain only detached public metadata. */
    public synchronized TokenChange beginTokenChange(TokenId id, TokenChange.Kind kind) {
        dispatcher.assertDispatchThread();
        if (operating || providerActive || pendingChange != null || snapshot.state() != State.OPEN
                || snapshot.view() == null
                || !(snapshot.view().observation() instanceof org.totipo.ObservationProgress.Finished)
                || !snapshot.view().diagnostics().isEmpty() || !snapshot.view().integrityProblems().isEmpty()) return null;
        var token = snapshot.view().tokens().stream().filter(t -> t.id().equals(id)).findFirst().orElse(null);
        if (token == null || (kind == TokenChange.Kind.RESOLVE ? !TokenChange.resolvable(token) : !TokenChange.live(token))) return null;
        pendingChange = new TokenChange(kind, token); changeResult = null; addOutcome = null;
        hideCode();
        return pendingChange;
    }
    public synchronized void cancelTokenChange(TokenChange change) {
        dispatcher.assertDispatchThread();
        if (pendingChange == change) { pendingChange = null; notifySync(); queueSyncDrain(); }
    }
    public synchronized boolean confirmTokenChange(TokenChange change, String issuer, String account, int option) {
        dispatcher.assertDispatchThread();
        if (change == null || pendingChange != change || operating || providerActive || snapshot.state() != State.OPEN) return false;
        pendingChange = null;
        long generation = sessionGeneration;
        return submit(State.BUSY, "Saving local change…", () -> {
            var result = vault.changeToken(change, issuer, account, option);
            String message = switch (result) {
                case SAVED -> "Change saved locally.";
                case STALE -> change.kind() == TokenChange.Kind.RESOLVE
                        ? "This conflict changed. Review it and try again." : "This token changed. Review it and try again.";
                case INVALID -> "Check issuer/account (up to 256 UTF-8 bytes).";
                case FAILED -> "Totipo could not save this change.";
                case PUBLICATION_UNCERTAIN -> "Local save uncertain. Observe local state before deciding whether to try again.";
            };
            synchronized (this) {
                if (sessionGeneration != generation) return;
                changeResult = result;
                if (result == TokenChange.Result.SAVED) localMutationSaved();
                try { render(message); }
                catch (RuntimeException failure) { publish(State.ERROR_OPEN, Error.OBSERVATION_DIAGNOSTICS,
                        "Observation unavailable; lock before retrying.", null); }
            }
        });
    }
    private ForegroundVaultCoordinator vault;
    private AutoCloseable observation;
    private boolean operating, dirty, viewQueued, observationFailed;

    AndroidVaultController(LocalReplicaOwner owner, Dispatcher dispatcher) { this(owner, dispatcher, new Backend()); }
    AndroidVaultController(LocalReplicaOwner owner, Dispatcher dispatcher, Backend backend) {
        this(owner, dispatcher, backend, new TotpPresentation.Time() {
            public Instant wall() { return Instant.now(); }
            public long elapsedMillis() { return System.nanoTime() / 1_000_000; }
        }, null);
    }
    AndroidVaultController(LocalReplicaOwner owner, Dispatcher dispatcher, Backend backend,
                           TotpPresentation.Time time, TotpPresentation.Clipboard clipboard) {
        this(owner, dispatcher, backend, time, clipboard, null);
    }
    AndroidVaultController(LocalReplicaOwner owner, Dispatcher dispatcher, Backend backend,
                           TotpPresentation.Time time, TotpPresentation.Clipboard clipboard,
                           SyncFolderBinding binding) {
        this.syncBinding = binding;
        this.owner = owner; this.dispatcher = dispatcher; this.backend = backend;
        this.time = time;
        TotpPresentation.Clipboard marshalled = clipboard == null ? null : new TotpPresentation.Clipboard() {
            public boolean copy(String marker, String text) {
                dispatcher.assertDispatchThread(); return clipboard.copy(marker, text);
            }
            public void clearIfOwned(String marker, String text) {
                dispatcher.post(() -> { dispatcher.assertDispatchThread(); clipboard.clearIfOwned(marker, text); });
            }
        };
        presentation = new TotpPresentation(time, (delay, action) -> dispatcher.after(delay,
                () -> { synchronized (this) { action.run(); } }), marshalled, this::presentationChanged);
        submit(State.STARTING, "Checking local vault…", () -> {
            if (syncBinding != null) { syncBinding.restore(); pendingPublication = syncBinding.pendingPublication(); updateBinding(null); startProvider(false); }
            discover();
        });
    }
    public synchronized Snapshot snapshot() { presentation.display(); return snapshot; }
    public void attach(Listener listener) {
        dispatcher.assertDispatchThread();
        synchronized (this) { listeners.add(listener); }
        deliver(listener);
    }
    public void detach(Listener listener) {
        dispatcher.assertDispatchThread();
        synchronized (this) { listeners.remove(listener); }
    }
    private void deliver(Listener listener) { deliver(listener, false); }
    private void deliver(Listener listener, boolean revealOnly) {
        dispatcher.post(() -> {
            dispatcher.assertDispatchThread();
            Snapshot value;
            synchronized (this) { if (!listeners.contains(listener)) return; value = snapshot(); }
            if (revealOnly) listener.revealChanged(value); else listener.changed(value);
        });
    }
    private void publish(State state, Error error, String message, View view) {
        ArrayList<Listener> targets;
        synchronized (this) {
            clearPresentation();
            snapshot = new Snapshot(state, error, message, view, null, 0, addOutcome);
            targets = new ArrayList<>(listeners);
        }
        for (Listener listener : targets) deliver(listener);
    }
    private synchronized void presentationChanged() {
        if (clearingPresentation) return;
        var display = presentation.display();
        snapshot = new Snapshot(snapshot.state(), snapshot.error(), snapshot.message(), snapshot.view(),
                display.code(), display.seconds(), addOutcome);
        for (Listener listener : new ArrayList<>(listeners)) deliver(listener, true);
    }
    private void clearPresentation() {
        presentationEpoch++;
        clearingPresentation = true;
        try { presentation.clear(); }
        finally { clearingPresentation = false; }
    }
    public synchronized void hideCode() {
        dispatcher.assertDispatchThread(); clearPresentation(); presentationChanged();
    }
    public synchronized boolean copyShownCode() {
        dispatcher.assertDispatchThread();
        if (operating || (unifiedSync && providerActive) || pendingChange != null || snapshot.state() != State.OPEN) return false;
        boolean copied = presentation.copy();
        if (snapshot.remainingSeconds() != presentation.display().seconds()) presentationChanged();
        return copied;
    }
    /** Presentation work shares the bounded worker, but does not reserve vault admission. */
    public synchronized boolean showCode(TokenId id) {
        dispatcher.assertDispatchThread();
        if (revealing || viewQueued || operating || (unifiedSync && providerActive)
                || pendingChange != null || snapshot.state() != State.OPEN || snapshot.view() == null) return false;
        var expected = snapshot.view().tokens().stream().filter(token -> token.id().equals(id)).findFirst().orElse(null);
        if (expected == null || !TokenListAdapter.usable(expected)) return false;
        clearPresentation(); presentationChanged();
        long epoch = presentationEpoch, session = sessionGeneration;
        var current = vault;
        revealing = true;
        try {
            revealTask = () -> {
                dispatcher.assertWorkerThread();
                try {
                    synchronized (this) {
                        if (!revealCurrent(epoch, session, expected) || vault != current) return;
                    }
                    var result = backend.generateTotp(current, id, time.wall(), expected);
                    synchronized (this) {
                        if (!revealCurrent(epoch, session, expected) || vault != current) return;
                        if (result.status() == ForegroundVaultCoordinator.TotpStatus.AVAILABLE)
                            presentation.reveal(result.revealed());
                    }
                } catch (RuntimeException unavailable) {
                    // Leave the row concealed; no global generation/failure status.
                } finally {
                    synchronized (this) { revealing = false; revealTask = null; scheduleView(); queueSyncDrain(); }
                }
            };
            worker.execute(revealTask);
            return true;
        } catch (RejectedExecutionException busy) { revealing = false; revealTask = null; scheduleView(); return false; }
    }
    private boolean revealCurrent(long epoch, long session, ForegroundVaultCoordinator.ObservedToken expected) {
        return epoch == presentationEpoch && session == sessionGeneration && !dirty && !observationFailed
                && !operating && !unifiedSync && pendingChange == null && snapshot.state() == State.OPEN
                && snapshot.view() != null && snapshot.view().tokens().contains(expected);
    }
    /** A queued presentation must not consume the sole pending slot of a real command. */
    private void retireQueuedReveal() {
        if (revealTask != null && worker.remove(revealTask)) {
            revealing = false; revealTask = null;
        }
    }
    private synchronized boolean submit(State state, String message, Runnable operation) {
        if (operating || pendingChange != null) return false;
        retireQueuedReveal();
        operating = true;
        Snapshot before = snapshot;
        publish(state, Error.NONE, message, state == State.BUSY ? before.view() : null);
        try {
            worker.execute(() -> {
                dispatcher.assertWorkerThread();
                try { discardCancelledJoin(); operation.run(); }
                finally { synchronized (this) {
                    discardCancelledJoin(); operating = false; scheduleView(); queueSyncDrain();
                    // Admission-dependent controls must also observe worker completion.
                    for (Listener listener : new ArrayList<>(listeners)) deliver(listener);
                } }
            });
            return true;
        } catch (RejectedExecutionException rejected) {
            operating = false;
            publish(before.state(), Error.BUSY, "Vault worker is busy. Try again.", before.view());
            return false;
        }
    }
    private void discover() {
        try {
            var result = backend.discover(owner);
            if (result.vault().lifecycle() != ForegroundVaultCoordinator.State.CLOSED) {
                vault = result.vault(); failedClose(); return;
            }
            switch (result.status()) {
                case ABSENT -> publish(State.NO_LOCAL_VAULT, Error.NONE, "Create a local vault", null);
                case PRESENT -> publish(State.LOCKED, Error.NONE, "Local vault locked", null);
                case UNSAFE -> publish(State.ERROR_LOCKED, Error.LOCAL_STORAGE_UNSAFE,
                        "Local VAULT has an unsafe type or size. It was not treated as absent.", null);
                case UNAVAILABLE -> publish(State.ERROR_LOCKED, Error.LOCAL_STORAGE_UNAVAILABLE,
                        "Local vault storage is unavailable. Retry checking storage.", null);
            }
        } catch (IOException | RuntimeException failure) {
            publish(State.ERROR_LOCKED, Error.LOCAL_STORAGE_UNAVAILABLE, "Local vault storage is unavailable.", null);
        }
    }
    public synchronized boolean retryDiscovery() {
        return snapshot.state() == State.ERROR_LOCKED && submit(State.STARTING, "Checking local vault…", this::discover);
    }
    /** Takes exclusive buffer ownership even on rejected admission; always wipes it. */
    public synchronized boolean unlock(char[] credential) { return authenticate(credential, false); }
    public synchronized boolean create(char[] credential) { return authenticate(credential, true); }
    private boolean authenticate(char[] credential, boolean create) {
        boolean admitted = false;
        try {
            State expected = create ? State.NO_LOCAL_VAULT : State.LOCKED;
            if (snapshot.state() != expected) return false;
            cancelOutbound();
            admitted = submit(create ? State.CREATING : State.UNLOCKING,
                    create ? "Creating vault…" : "Unlocking vault…", () -> {
                try {
                    Error error;
                    boolean objectDataObserved = false;
                    if (create) {
                        var result = backend.create(owner, credential); vault = result.vault();
                        if (result.failure() == null && result.cause() == null) { opened(); return; }
                        objectDataObserved = result.failure() instanceof CreateVaultResult.Failed failed
                                && failed.reason() == CreateVaultResult.FailureReason.OBJECT_DATA_OBSERVED;
                        error = result.failure() instanceof CreateVaultResult.Uncertain
                                ? Error.LOCAL_STORAGE_UNSAFE : Error.CREATE_FAILED;
                    } else {
                        var result = backend.open(owner, credential); vault = result.vault();
                        if (result.failure() == null && result.cause() == null) { opened(); return; }
                        error = result.failure() instanceof OpenResult.AuthenticationFailed ? Error.AUTHENTICATION_FAILED
                                : result.failure() instanceof OpenResult.Absent ? Error.LOCAL_VAULT_ABSENT
                                : result.failure() instanceof OpenResult.Unavailable ? Error.LOCAL_STORAGE_UNAVAILABLE
                                : result.failure() instanceof OpenResult.InvalidVault ? Error.LOCAL_STORAGE_UNSAFE : Error.OPEN_FAILED;
                    }
                    if (!closeOwned()) return;
                    // Re-observe after failures: uncertain creation can have installed VAULT.
                    discover();
                    Snapshot current = snapshot();
                    if (current.state() != State.FAILED_CLOSE) publish(current.state(), error, objectDataObserved
                            ? "Totipo found existing token data but no usable vault. A new vault was not created."
                            : errorMessage(error), null);
                } catch (IOException | RuntimeException failure) {
                    if (!closeOwned()) return;
                    discover();
                    Snapshot current = snapshot();
                    if (current.state() != State.FAILED_CLOSE) publish(current.state(),
                            create ? Error.CREATE_FAILED : Error.OPEN_FAILED,
                            create ? "Vault creation failed. Check local storage and retry." : "Vault open failed. Check local storage and retry.", null);
                } finally { Arrays.fill(credential, '\0'); }
            });
            return admitted;
        } finally { if (!admitted) Arrays.fill(credential, '\0'); }
    }
    private static String errorMessage(Error error) {
        return switch (error) {
            case AUTHENTICATION_FAILED -> "Password did not unlock this vault.";
            case LOCAL_VAULT_ABSENT -> "Local vault is absent.";
            case LOCAL_STORAGE_UNAVAILABLE -> "Local vault storage is unavailable.";
            case LOCAL_STORAGE_UNSAFE -> "Local vault storage needs attention. Creation/open was not affirmed.";
            case CREATE_FAILED -> "Vault creation failed. Check local storage and retry.";
            default -> "Vault open failed. Check local storage and retry.";
        };
    }
    private void opened() {
        ForegroundVaultCoordinator current = vault;
        synchronized (this) { addOutcome = null; changeResult = null; }
        synchronized (this) { vaultId = org.totipo.Totipo.vaultId(current.snapshotVault()).hex(); }
        observation = current.observe(() -> signal(false), () -> signal(true));
        render("Vault open");
        synchronized (this) { requestAutomaticSync(); }
    }
    private synchronized void signal(boolean failed) {
        clearPresentation(); presentationChanged();
        dirty = true; observationFailed |= failed; scheduleView();
    }
    private synchronized void scheduleView() {
        if (!dirty || operating || revealing || viewQueued || unifiedSync) return;
        viewQueued = true;
        try {
            worker.execute(() -> {
                dispatcher.assertWorkerThread();
                synchronized (this) { if (operating) { viewQueued = false; return; } dirty = false; }
                try {
                    if (vault != null && vault.lifecycle() == ForegroundVaultCoordinator.State.OPEN
                            && (snapshot().state() == State.OPEN || snapshot().state() == State.BUSY)) render(snapshot().message());
                } finally { synchronized (this) { viewQueued = false; scheduleView(); queueSyncDrain(); } }
            });
        } catch (RejectedExecutionException rejected) { viewQueued = false; }
    }
    private void render(String message) {
        View view = vault.view();
        synchronized (this) {
            if (observationFailed) {
                publish(State.ERROR_OPEN, Error.OBSERVATION_DIAGNOSTICS,
                        "Vault observation ended unexpectedly. Lock the vault before retrying.", null);
                return;
            }
        }
        boolean diagnostics = !view.diagnostics().isEmpty() || !view.integrityProblems().isEmpty()
                || view.tokens().stream().anyMatch(token -> token.conflict() || !token.unresolved().isEmpty());
        String attention = "Vault needs attention: unresolved, conflicting, or diagnostic observations are present.";
        publish(State.OPEN, diagnostics ? Error.OBSERVATION_DIAGNOSTICS : Error.NONE,
                diagnostics && !message.contains(attention) ? message + " " + attention : message, view);
    }
    /** Admission observation only; addToken still rechecks atomically and owns every rejection. */
    public synchronized boolean canAddToken() { return snapshot.state() == State.OPEN && !operating && !unifiedSync && pendingChange == null; }
    /** Takes ownership on every path. Uses existing bounded admission and the live session. */
    public synchronized boolean addToken(AddTokenRequest request) {
        dispatcher.assertDispatchThread();
        boolean admitted = false;
        try {
            if (!canAddToken()) return false;
            addOutcome = null; changeResult = null;
            admitted = submit(State.BUSY, "Adding token…", () -> {
                try {
                    var result = vault.addToken(request);
                    synchronized (this) { addOutcome = result; if (result.status() == AddTokenOutcome.Status.ADDED) localMutationSaved(); }
                    String message = switch (result.status()) {
                        case ADDED -> "Token added";
                        case INVALID_SECRET -> "Enter a valid Base32 secret containing 1–128 decoded bytes.";
                        case INVALID_FIELDS -> "Check issuer/account (up to 256 UTF-8 bytes), algorithm, digits and period.";
                        case PUBLICATION_UNCERTAIN -> "Token publication uncertain. Observe local state before deciding whether to add again.";
                        case CONFLICT -> "Vault changed; token was not added. Observe local state before retrying.";
                        case FAILED -> "Token was not added. Check fields and local storage.";
                        case SESSION_UNAVAILABLE -> "Session unavailable. Lock the vault before retrying.";
                        case BUSY -> "Vault busy. Try again.";
                    };
                    try { render(message); }
                    catch (RuntimeException observationUnavailable) {
                        publish(State.ERROR_OPEN, Error.OBSERVATION_DIAGNOSTICS,
                                message + " Observation unavailable; lock before retrying.", null);
                    }
                } finally { request.close(); }
            });
            return admitted;
        } finally { if (!admitted) request.close(); }
    }
    public synchronized boolean refresh() {
        return !unifiedSync && snapshot.state() == State.OPEN && submit(State.BUSY, "Requesting refresh…", () -> {
            try { vault.requestRefresh(); render("Refresh requested"); }
            catch (RuntimeException failure) { publish(State.ERROR_OPEN, Error.OPEN_FAILED, "Refresh failed. Lock the vault before retrying.", null); }
        });
    }
    public synchronized boolean canJoinExistingVault() {
        return !operating && pendingChange == null && !providerActive && syncBinding != null && snapshot.state() == State.NO_LOCAL_VAULT
                && syncView.binding().status() == SyncFolderBinding.Status.READY && syncView.binding().readable();
    }
    private byte[] preparedJoinCandidate; // Detached non-secret bytes; never a cached mutation authorization.
    private SyncFolderBinding.Request preparedJoinBinding;
    public synchronized boolean hasJoinCandidate() {
        return preparedJoinCandidate != null && preparedJoinBinding != null && syncBinding.current(preparedJoinBinding);
    }
    /** Collect and structurally identify before the UI asks for a credential. */
    public synchronized boolean prepareJoin() {
        dispatcher.assertDispatchThread();
        if (!canJoinExistingVault()) return false;
        preparedJoinCandidate = null; preparedJoinBinding = null;
        providerActive = true;
        var cancelled = new java.util.concurrent.atomic.AtomicBoolean(); outboundCancelled = cancelled;
        long generation = sessionGeneration;
        updateBinding("Checking existing vault…"); notifySync();
        outboundDispatch(cancelled, () -> {
            ForegroundVaultCoordinator.Discovery local;
            try { local = ForegroundVaultCoordinator.inspectEnrollment(owner); }
            catch (IOException unavailable) { finishOutbound(cancelled, "Local canonical store unavailable."); return; }
            if (local.vault().lifecycle() != ForegroundVaultCoordinator.State.CLOSED) {
                vault = local.vault(); failedClose(); finishOutbound(cancelled, "Local canonical store closure needs attention."); return;
            }
            if (local.status() != ForegroundVaultCoordinator.LocalStatus.ABSENT) {
                finishOutbound(cancelled, local.cause() instanceof ForegroundVaultCoordinator.LocalObjectEvidence
                        ? "Totipo found existing local token data and will not replace it." : "Local canonical store is not empty and usable."); return;
            }
            syncBinding.check(); var request = syncBinding.request(); outboundIdentity = request;
            providerIo.submit(syncBinding.transport(), request, true, result -> outboundDispatch(cancelled, () -> {
                if (!joinCurrent(request, generation, cancelled)) { finishOutbound(cancelled, null); return; }
                syncBinding.accessibility(request, result.accessible());
                if (!result.accessible()) { finishOutbound(cancelled, "Sync folder vault cannot be verified."); return; }
                try {
                    byte[] exact = org.totipo.android.sync.VaultBootstrapEvidence.joinCandidate(result.scan());
                    synchronized (this) { preparedJoinCandidate = exact; preparedJoinBinding = request; }
                    finishOutbound(cancelled, "Enter the existing vault password.");
                } catch (IllegalStateException invalid) {
                    finishOutbound(cancelled, org.totipo.android.sync.VaultBootstrapEvidence.completeRoot(result.scan())
                            && result.scan().root().rows().stream().noneMatch(r -> "vault".equals(r.displayName()))
                            ? "Sync folder has no Totipo vault." : "Sync folder vault cannot be verified.");
                }
            }));
        });
        return true;
    }
    /** Exclusive credential ownership stays on the vault worker; transport callbacks only hand off evidence. */
    public synchronized boolean joinExistingVault(char[] credential) {
        dispatcher.assertDispatchThread();
        if (!canJoinExistingVault() || !hasJoinCandidate()) { Arrays.fill(credential, '\0'); return false; }
        providerActive = true;
        var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        outboundCancelled = cancelled;
        long generation = sessionGeneration;
        updateBinding("Joining existing vault…"); notifySync();
        try { worker.execute(() -> {
            dispatcher.assertWorkerThread();
            joinCredential = new JoinCredential(credential);
            beginJoin(cancelled, generation);
        }); } catch (RejectedExecutionException busy) {
            Arrays.fill(credential, '\0'); finishOutbound(cancelled, "Provider busy. Try again."); return false;
        }
        return true;
    }
    private JoinCredential joinCredential; // Accessed by vault worker, never handed to provider callbacks.
    private static final class JoinCredential {
        final char[] value;
        JoinCredential(char[] value) { this.value = value; }
        void clear() { Arrays.fill(value, '\0'); }
    }
    private void beginJoin(java.util.concurrent.atomic.AtomicBoolean cancelled, long generation) {
        try {
            syncBinding.check();
            var request = syncBinding.request(); outboundIdentity = request;
            providerIo.submit(syncBinding.transport(), request, true, result -> joinDispatch(cancelled, () -> {
                if (!joinCurrent(request, generation, cancelled)) { finishJoin(cancelled, null); return; }
                syncBinding.accessibility(request, result.accessible());
                if (!result.accessible()) { finishJoin(cancelled, "Sync folder vault cannot be verified."); return; }
                byte[] exact;
                try { exact = org.totipo.android.sync.VaultBootstrapEvidence.joinCandidate(result.scan()); }
                catch (IllegalStateException blocked) {
                    finishJoin(cancelled, result.scan() != null
                            && org.totipo.android.sync.VaultBootstrapEvidence.completeRoot(result.scan())
                            && result.scan().root().rows().stream().noneMatch(r -> "vault".equals(r.displayName()))
                            ? "Sync folder has no Totipo vault." : "Sync folder vault cannot be verified."); return;
                }
                if (!Arrays.equals(exact, preparedJoinCandidate)) {
                    synchronized (this) { preparedJoinCandidate = null; preparedJoinBinding = null; }
                    finishJoin(cancelled, "Sync folder changed. Try again."); return;
                }
                synchronized (this) { preparedJoinBinding = request; }
                boolean authenticated;
                try { authenticated = backend.authenticateCandidate(exact, joinCredential.value); }
                catch (IllegalArgumentException invalid) { authenticated = false; }
                if (!authenticated) { finishJoin(cancelled, "Could not unlock this vault."); return; }
                if (!joinCurrent(request, generation, cancelled)) { finishJoin(cancelled, null); return; }
                // Temporary session has closed. Never authenticate a changed replacement candidate.
                providerIo.submit(syncBinding.transport(), request, true, fresh -> joinDispatch(cancelled, () -> {
                    if (!joinCurrent(request, generation, cancelled)) { finishJoin(cancelled, null); return; }
                    byte[] rechecked;
                    try { rechecked = fresh.accessible() ? org.totipo.android.sync.VaultBootstrapEvidence.joinCandidate(fresh.scan()) : null; }
                    catch (IllegalStateException stale) { rechecked = null; }
                    if (!Arrays.equals(exact, rechecked)) { finishJoin(cancelled, "Sync folder changed. Try again."); return; }
                    synchronized (this) {
                        if (!joinCurrent(request, generation, cancelled) || operating) { finishJoin(cancelled, null); return; }
                        operating = true;
                    }
                    try {
                        ForegroundVaultCoordinator.Opening enrolled;
                        try { enrolled = backend.join(owner, exact, joinCredential.value,
                                () -> cancelled.get() || generation != sessionGeneration); }
                        catch (IOException unavailable) { throw new IllegalStateException("Local unavailable"); }
                        vault = enrolled.vault();
                        if (cancelled.get() || generation != sessionGeneration || !syncBinding.current(request)) { if (closeOwned()) discover(); return; }
                        if (enrolled.failure() == null && enrolled.cause() == null) {
                            opened(); render("Joined existing Totipo vault.");
                        } else if (closeOwned()) {
                            discover();
                            if (enrolled.cause() instanceof ForegroundVaultCoordinator.LocalObjectEvidence)
                                publish(snapshot().state(), Error.CREATE_FAILED,
                                    "Totipo found existing local token data and will not replace it.", null);
                        }
                    } finally {
                        synchronized (this) { operating = false; scheduleView(); }
                        finishJoin(cancelled, snapshot().state() == State.OPEN
                                ? "Joined existing Totipo vault." : snapshot().message().equals("Totipo found existing local token data and will not replace it.")
                                    ? snapshot().message() : "Local enrollment was not affirmed. Check local storage.");
                    }
                }));
            }));
        } catch (RuntimeException failure) { finishJoin(cancelled, "Sync folder vault cannot be verified."); }
    }
    public synchronized void cancelJoin() {
        if (snapshot.state() != State.NO_LOCAL_VAULT || outboundCancelled == null) return;
        var cancelled = outboundCancelled; cancelled.set(true);
        try { worker.execute(() -> { if (joinCredential != null) { joinCredential.clear(); joinCredential = null; } }); }
        catch (RejectedExecutionException closed) { }
    }
    private synchronized boolean joinCurrent(SyncFolderBinding.Request request, long generation,
                                              java.util.concurrent.atomic.AtomicBoolean cancelled) {
        return !cancelled.get() && generation == sessionGeneration && syncBinding.current(request)
                && snapshot.state() == State.NO_LOCAL_VAULT && vault == null
                && syncBinding.transport().grants(request.uri()).read();
    }
    private void joinDispatch(java.util.concurrent.atomic.AtomicBoolean cancelled, Runnable action) {
        try { worker.execute(() -> {
            dispatcher.assertWorkerThread();
            try { action.run(); }
            catch (RuntimeException failure) { finishJoin(cancelled, "Local enrollment was not affirmed. Check local storage."); }
        }); }
        catch (RejectedExecutionException busy) {
            // No handoff was queued: revoke enrollment and release this finished transport operation.
            // Incompatible queued commands/shutdown own worker-side credential cleanup.
            cancelled.set(true);
            finishOutbound(cancelled, null);
        }
    }
    private void discardCancelledJoin() {
        if (joinCredential != null && outboundCancelled != null && outboundCancelled.get()) {
            joinCredential.clear(); joinCredential = null;
        }
    }
    private void finishJoin(java.util.concurrent.atomic.AtomicBoolean cancelled, String message) {
        if (joinCredential != null) { joinCredential.clear(); joinCredential = null; }
        finishOutbound(cancelled, message);
    }
    public synchronized boolean canInitializeSyncFolder() { return canPublishLocalChanges(); }
    public synchronized boolean initializeSyncFolder() {
        dispatcher.assertDispatchThread();
        if (!canInitializeSyncFolder()) return false;
        providerActive = true;
        var cancelled = new java.util.concurrent.atomic.AtomicBoolean(); outboundCancelled = cancelled;
        long generation = sessionGeneration;
        updateBinding("Initializing sync folder…"); notifySync();
        outboundDispatch(cancelled, () -> {
            syncBinding.check(); var request = syncBinding.request(); outboundIdentity = request;
            if (!outboundCurrent(request, generation, cancelled)) { finishOutbound(cancelled, null); return; }
            byte[] exact = vault.snapshotVault();
            providerIo.submit(syncBinding.transport(), request, true, result -> outboundDispatch(cancelled, () -> {
                if (!outboundCurrent(request, generation, cancelled)) { finishOutbound(cancelled, null); return; }
                syncBinding.accessibility(request, result.accessible());
                if (!result.accessible()) { finishOutbound(cancelled, "Sync folder vault cannot be verified."); return; }
                byte[] existing;
                try { existing = org.totipo.android.sync.VaultBootstrapEvidence.candidate(result.scan()); }
                catch (IllegalStateException unusable) { finishOutbound(cancelled, "Sync folder vault cannot be verified."); return; }
                if (existing != null) {
                    var status = org.totipo.android.provider.ProviderVaultIdentity.classify(result.scan(), org.totipo.Totipo.vaultId(exact));
                    if (status != org.totipo.android.provider.ProviderVaultIdentity.Status.MATCH || !Arrays.equals(existing, exact)) {
                        finishOutbound(cancelled, status.message()); return;
                    }
                }
                try { org.totipo.android.sync.VaultBootstrapEvidence.namespace(result.scan(), existing == null); }
                catch (IllegalStateException veto) {
                    finishOutbound(cancelled, existing == null
                            ? "Sync folder contains unverified token data or namespace evidence but no vault. Totipo will not initialize it automatically."
                            : "Sync folder namespace cannot be verified."); return;
                }
                providerIo.initialize(syncBinding.transport(), request, exact, cancelled::get,
                    written -> outboundDispatch(cancelled, () -> {
                        if (!outboundCurrent(request, generation, cancelled)) { finishOutbound(cancelled, null); return; }
                        var status = written.status();
                        if (status == org.totipo.android.sync.ProviderVaultWriter.Status.VERIFIED
                                || status == org.totipo.android.sync.ProviderVaultWriter.Status.ALREADY_INITIALIZED) {
                            if (!Arrays.equals(exact, org.totipo.android.sync.VaultBootstrapEvidence.joinCandidate(written.postflight()))
                                    || !vault.matchesVault(org.totipo.android.sync.VaultBootstrapEvidence.joinCandidate(written.postflight()))) {
                                finishOutbound(cancelled, "Initialization uncertain. Check again before retrying."); return;
                            }
                        }
                        finishOutbound(cancelled, switch (status) {
                            case VERIFIED -> "Sync folder initialized. Publish local changes explicitly.";
                            case ALREADY_INITIALIZED -> "Sync folder already initialized.";
                            case PARTIAL -> "Sync folder vault verified; token directory initialization incomplete. Try Initialize again.";
                            case UNCERTAIN -> "Initialization uncertain. Check again before retrying.";
                            case BLOCKED -> "Sync folder cannot be initialized safely.";
                            case CANCELLED -> "Initialization cancelled; provider changes may have finished.";
                        });
                    }));
            }));
        });
        return true;
    }
    /** Internal detached inbound boundary retained for qualification; ordinary UI uses sync().
     * Uses the SAME coordinator/session and accepts no credential. */
    public synchronized boolean sync(Scan scan) {
        return !providerActive && snapshot.state() == State.OPEN && submit(State.BUSY, "Observing inbound immutable objects…", () -> {
            try {
                var report = vault.sync(scan);
                if (vault.lifecycle() == ForegroundVaultCoordinator.State.OPEN) render(
                        report.refresh() == ForegroundVaultCoordinator.Refresh.REQUESTED ? "Refresh requested" : "Inbound scan observed");
                else publish(State.ERROR_OPEN, Error.LOCAL_STORAGE_UNSAFE, "Local storage needs attention. Lock the vault before retrying.", null);
            } catch (org.totipo.android.provider.ProviderVaultIdentity.Blocked blocked) {
                render(blocked.status().message());
            } catch (RuntimeException failure) {
                publish(State.ERROR_OPEN, Error.LOCAL_STORAGE_UNSAFE, "Inbound observation failed. Lock the vault before retrying.", null);
            }
        });
    }
    private volatile boolean unifiedSync;
    private boolean syncPending, syncDrainQueued, foreground;
    private boolean pendingPublication;
    private String syncStatus = "", lastSyncResult = "Not attempted", vaultId = "Locked";
    /** Application-level foreground transitions, including enrollment Activities; rotation is coalesced. */
    public synchronized void foregroundChanged(boolean active) {
        dispatcher.assertDispatchThread();
        boolean returned = active && !foreground;
        foreground = active;
        if (returned) requestAutomaticSync();
    }
    private synchronized void localMutationSaved() {
        setPendingPublication(true);
        syncStatus = "Changes not synced";
        requestAutomaticSync();
    }
    private void setPendingPublication(boolean pending) {
        pendingPublication = pending;
        if (syncBinding != null) syncBinding.pendingPublication(pending);
    }
    private synchronized void requestAutomaticSync() {
        if (syncBinding == null || syncBinding.initialUri() == null) return;
        syncPending = true;
        queueSyncDrain();
    }
    private synchronized void queueSyncDrain() {
        if (!syncPending || syncDrainQueued) return;
        syncDrainQueued = true;
        dispatcher.post(() -> {
            synchronized (this) {
                syncDrainQueued = false;
                if (!syncPending || operating || viewQueued || providerActive || pendingChange != null) return;
                if (snapshot.state() != State.OPEN || syncBinding == null || syncBinding.initialUri() == null) {
                    syncPending = false; return;
                }
                syncPending = false;
                startSync();
            }
        });
    }
    public synchronized boolean canSync() {
        return snapshot.state() == State.OPEN && syncBinding != null && syncBinding.initialUri() != null
                && !operating && pendingChange == null && !providerActive;
    }
    /** One active operation plus one pending request. Failed attempts never schedule their own retry. */
    public synchronized boolean sync() {
        dispatcher.assertDispatchThread();
        if (unifiedSync) { syncPending = true; return true; }
        if (!canSync()) return false;
        syncPending = false;
        return startSync();
    }
    public synchronized String dailySyncStatus() {
        if (unifiedSync) return "Syncing…";
        if (!syncStatus.isEmpty()) return syncStatus;
        if (pendingPublication) return "Changes not synced";
        if (snapshot.view() != null && snapshot.view().tokens().stream().anyMatch(t -> t.conflict()))
            return "Conflict needs attention";
        return "";
    }
    /** Cached public metadata only. Opening Diagnostics performs no vault/provider operation. */
    public synchronized String diagnostics() {
        long conflicts = snapshot.view() == null ? 0 : snapshot.view().tokens().stream().filter(t -> t.conflict()).count();
        return "Vault ID: " + vaultId + "\nSync folder: " + (initialTreeUri == null ? "Not configured" : initialTreeUri)
                + "\nProvider: " + syncView.binding().status() + "\nBinding generation: " + bindingGeneration
                + "\nLast Sync: " + lastSyncResult + "\nLast operation detail: " + syncView.message()
                + "\nPending local publication: " + pendingPublication + "\nConflicts: " + conflicts
                + "\nJava/core: 0.2.0\nVault format: v1/r19\nApp: 0.0.0-dev";
    }
    private String productSyncError(String detail) {
        if (detail != null && detail.contains("different Totipo vault"))
            return "This sync folder belongs to a different Totipo vault.";
        if (pendingPublication) return "Changes not synced";
        if (syncBinding.view().status() == SyncFolderBinding.Status.UNAVAILABLE
                || syncBinding.view().status() == SyncFolderBinding.Status.ACCESS_LOST) return "Sync folder unavailable";
        if (detail != null && (detail.contains("vault") || detail.contains("incomplete")
                || detail.contains("Integrity") || detail.contains("attention") || detail.contains("read-only")))
            return "Sync folder needs attention";
        return "Sync failed";
    }
    private synchronized boolean startSync() {
        retireQueuedReveal();
        providerActive = unifiedSync = true;
        var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        outboundCancelled = cancelled;
        long session = sessionGeneration;
        syncStatus = "Syncing…"; notifySync();
        outboundDispatch(cancelled, () -> {
            if (cancelled.get() || session != sessionGeneration || snapshot().state() != State.OPEN) { finishOutbound(cancelled, null); return; }
            syncBinding.check();
            var request = syncBinding.request(); outboundIdentity = request;
            if (!syncBinding.view().readable()) {
                finishOutbound(cancelled, "Folder is read-only or unavailable"); return;
            }
            providerIo.submit(syncBinding.transport(), request, true, observed -> outboundDispatch(cancelled, () -> {
                if (!syncObservationCurrent(request, session, cancelled)) { finishOutbound(cancelled, null); return; }
                syncBinding.accessibility(request, observed.accessible());
                if (!observed.accessible() || observed.scan() == null) {
                    finishOutbound(cancelled, "Provider unavailable"); return;
                }
                var report = vault.sync(observed.scan(), cancelled::get);
                if (!safeToPublishAfterImport(report)) {
                    if (vault.lifecycle() != ForegroundVaultCoordinator.State.OPEN)
                        publish(State.ERROR_OPEN, Error.LOCAL_STORAGE_UNSAFE, "Local storage needs attention. Lock the vault before retrying.", null);
                    finishOutbound(cancelled, "Sync folder needs attention"); return;
                }
                // Java refresh is asynchronous. Await fresh completed observations, never infer merge outcomes.
                if (!vault.observeForSync(cancelled::get)) {
                    finishOutbound(cancelled, "Observation incomplete"); return;
                }
                synchronized (this) {
                    if (!syncObservationCurrent(request, session, cancelled)) { finishOutbound(cancelled, null); return; }
                    render("Vault open");
                    if (!syncBinding.view().writable()) { finishOutbound(cancelled, "Folder is read-only for Totipo."); return; }
                }
                publishOnWorker(cancelled, session, request);
            }));
        });
        return true;
    }
    static boolean safeToPublishAfterImport(ForegroundVaultCoordinator.Report report) {
        return (report.completion() == ForegroundVaultCoordinator.Completion.RETAINED_SESSION
                || report.completion() == ForegroundVaultCoordinator.Completion.REFRESH_REQUESTED)
                && report.failure() == null && report.unattempted().isEmpty()
                && report.refresh() != ForegroundVaultCoordinator.Refresh.SKIPPED_UNSAFE
                && report.evidence() != null
                && report.evidence().transport().state() == org.totipo.android.provider.ProviderSnapshot.State.COMPLETE
                && report.contradictions().isEmpty() && report.deferredValidation().isEmpty()
                && report.count(ImmutableCandidateImporter.Status.BLOCKED_EXISTING_DIFFERENT) == 0
                && report.evidence().groups().stream().flatMap(g -> g.siblings().stream())
                    .allMatch(c -> c.kind() == org.totipo.android.provider.ImmutableCandidateClassifier.Kind.VALID);
    }
    public synchronized SyncView syncView() { return syncView; }
    /** Private picker hint only; never rendered in product status. */
    public synchronized String initialTreeUri() { return initialTreeUri; }
    public synchronized boolean canManageSyncFolder() { return syncBinding != null && !operating && pendingChange == null; }
    public synchronized boolean canImportProviderChanges() {
        return canManageSyncFolder() && !providerActive && snapshot.state() == State.OPEN
                && syncView.binding().status() == SyncFolderBinding.Status.READY;
    }
    private synchronized void updateBinding(String message) {
        var binding = syncBinding.view();
        initialTreeUri = syncBinding.initialUri();
        bindingGeneration = syncBinding.request().generation();
        String detail = message == null ? switch (binding.status()) {
            case NOT_CONFIGURED -> "Not configured";
            case CHECKING -> "Checking sync folder…";
            case READY -> binding.writable() ? "Connected" : "Folder is read-only for Totipo.";
            case ACCESS_LOST -> "Access needed. Choose folder again.";
            case UNAVAILABLE -> "Sync folder unavailable. Retry access.";
        } : message;
        if (importIntegrityAttention && !detail.contains("Integrity problem")) detail += " Integrity problem. Token changes need attention.";
        syncView = new SyncView(binding, detail);
    }
    private boolean folderOperation(Runnable operation) {
        if (!canManageSyncFolder()) return false;
        Snapshot before = snapshot;
        return submit(before.state(), before.message(), () -> {
            if (joinCredential != null && outboundCancelled != null && outboundCancelled.get()) { joinCredential.clear(); joinCredential = null; }
            operation.run();
            if (before.state() == State.OPEN) render(before.message());
            else publish(before.state(), before.error(), before.message(), before.view());
        });
    }
    public synchronized boolean chooseSyncFolder(boolean cancelled, String uri, int flags) {
        dispatcher.assertDispatchThread();
        if (!cancelled && canManageSyncFolder()) cancelOutbound();
        return folderOperation(() -> {
            if (!cancelled) cancelOutbound();
            boolean accepted = syncBinding.choose(cancelled, uri,
                    (flags & android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0,
                    (flags & android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0);
            updateBinding(accepted ? null : "Folder access could not be saved. Choose folder again.");
            if (accepted && !cancelled) startProvider(false);
        });
    }
    public synchronized boolean disconnectSyncFolder() {
        if (canManageSyncFolder()) cancelOutbound();
        return folderOperation(() -> { cancelOutbound(); updateBinding(syncBinding.disconnect() ? null
                : "Folder configuration could not be cleared. Try again."); });
    }
    public synchronized boolean checkSyncFolder() {
        if (providerActive) return false;
        return folderOperation(() -> { syncBinding.check(); updateBinding(null); startProvider(false); });
    }
    public synchronized boolean importProviderChanges() {
        dispatcher.assertDispatchThread();
        if (!canImportProviderChanges()) return false;
        clearPresentation(); presentationChanged();
        updateBinding("Importing…");
        return folderOperation(() -> {
            syncBinding.check();
            if (syncBinding.view().status() == SyncFolderBinding.Status.CHECKING) startProvider(true);
            else updateBinding(null);
        });
    }
    public synchronized boolean canPublishLocalChanges() {
        return canImportProviderChanges() && syncView.binding().writable();
    }
    /** Foreground sync admission only. Preserve revealed code and global OPEN state. */
    public synchronized boolean publishLocalChanges() {
        dispatcher.assertDispatchThread();
        if (!canPublishLocalChanges()) return false;
        providerActive = true;
        var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        outboundCancelled = cancelled;
        long session = sessionGeneration;
        updateBinding("Publishing…"); notifySync();
        try {
            worker.execute(() -> publishOnWorker(cancelled, session, null));
            return true;
        } catch (RejectedExecutionException busy) { finishOutbound(cancelled, "Provider busy. Try again."); return false; }
    }
    private void publishOnWorker(java.util.concurrent.atomic.AtomicBoolean cancelled, long session,
                                 SyncFolderBinding.Request observed) {
        SyncFolderBinding.Request request = observed;
        try {
            synchronized (this) {
                if (request == null) { syncBinding.check(); request = syncBinding.request(); }
                outboundIdentity = request;
                if (cancelled.get() || session != sessionGeneration || vault == null
                        || !syncBinding.view().writable() || !syncBinding.view().readable()) {
                    finishOutbound(cancelled, null); return;
                }
            }
            var detached = vault.outboundSnapshot();
            var identity = request;
            providerIo.submit(syncBinding.transport(), identity, true, preflight ->
                outboundDispatch(cancelled, () -> {
                    synchronized (this) {
                        if (!outboundCurrent(identity, session, cancelled)) { finishOutbound(cancelled, null); return; }
                        syncBinding.accessibility(identity, preflight.accessible());
                    }
                    if (!preflight.accessible() || preflight.scan() == null) {
                        finishOutbound(cancelled, "Provider view incomplete; nothing published."); return;
                    }
                    var plan = vault.outboundPlan(detached, preflight.scan(), syncBinding.view().writable());
                    if (unifiedSync && plan.blocked()) { finishOutbound(cancelled, "Sync folder needs attention"); return; }
                    if (plan.limitation() != null) { finishOutbound(cancelled, plan.limitation()); return; }
                    if (plan.missing().isEmpty()) {
                        finishOutbound(cancelled, plan.blocked() ? "Existing provider candidates block publication."
                                : "No local changes to publish"); return;
                    }
                    providerIo.publish(syncBinding.transport(), identity, plan.target(), plan.missing(), cancelled::get,
                        result -> outboundDispatch(cancelled, () -> {
                            synchronized (this) {
                                if (!outboundCurrent(identity, session, cancelled)) { finishOutbound(cancelled, null); return; }
                            }
                            finishOutbound(cancelled, vault.outboundConfirmation(plan, result));
                        }));
                }));
        } catch (RuntimeException failure) {
            finishOutbound(cancelled, failure instanceof org.totipo.android.sync.DetachedImmutableObject.CapacityExceeded
                    ? "Local publication capacity exceeded; nothing published." : "Local publication source invalid; nothing published.");
        }
    }
    private synchronized boolean syncObservationCurrent(SyncFolderBinding.Request request, long session,
                                                         java.util.concurrent.atomic.AtomicBoolean cancelled) {
        if (cancelled.get() || session != sessionGeneration || !syncBinding.current(request)
                || snapshot.state() != State.OPEN || vault == null) return false;
        if (!syncBinding.transport().grants(request.uri()).read()) {
            syncBinding.check(); cancelled.set(true); return false;
        }
        return true;
    }
    private synchronized boolean outboundCurrent(SyncFolderBinding.Request request, long session,
                                                 java.util.concurrent.atomic.AtomicBoolean cancelled) {
        if (cancelled.get() || session != sessionGeneration || !syncBinding.current(request)
                || snapshot.state() != State.OPEN || vault == null) return false;
        var grants = syncBinding.transport().grants(request.uri());
        if (!grants.read() || !grants.write()) {
            syncBinding.check(); cancelled.set(true); return false;
        }
        return true;
    }
    private void outboundDispatch(java.util.concurrent.atomic.AtomicBoolean cancelled, Runnable action) {
        try {
            worker.execute(() -> {
                dispatcher.assertWorkerThread();
                try { action.run(); }
                catch (org.totipo.android.provider.ProviderVaultIdentity.Blocked blocked) { finishOutbound(cancelled, blocked.status().message()); }
                catch (RuntimeException failure) { finishOutbound(cancelled, "Publication uncertain. Check again before retrying."); }
            });
        } catch (RejectedExecutionException busy) { finishOutbound(cancelled, null); }
    }
    private synchronized void finishOutbound(java.util.concurrent.atomic.AtomicBoolean cancelled, String message) {
        if (outboundCancelled != cancelled) return;
        if (unifiedSync) {
            boolean success = !cancelled.get() && ("Local changes published".equals(message)
                    || "No local changes to publish".equals(message));
            if (success) setPendingPublication(false);
            syncStatus = success ? "" : cancelled.get() ? (pendingPublication ? "Changes not synced" : "")
                    : productSyncError(message);
            lastSyncResult = success ? "Verified against bound shared folder" : syncStatus;
            unifiedSync = false;
        }
        providerActive = false;
        outboundCancelled = null;
        if (!cancelled.get() && outboundIdentity != null && syncBinding.current(outboundIdentity)
                && syncBinding.view().status() == SyncFolderBinding.Status.CHECKING)
            syncBinding.accessibility(outboundIdentity, true); // Prior READY retained after local-only failure.
        outboundIdentity = null;
        updateBinding(cancelled.get() ? null : message);
        // Binding replacement may have been waiting for the occupied provider lane.
        if (syncBinding.view().status() == SyncFolderBinding.Status.CHECKING) startProvider(false);
        scheduleView(); notifySync(); queueSyncDrain();
    }
    private synchronized boolean startProvider(boolean importing) {
        if (providerActive || syncBinding.view().status() != SyncFolderBinding.Status.CHECKING) return false;
        var request = syncBinding.request();
        long session = sessionGeneration;
        providerActive = true;
        try {
            providerIo.submit(syncBinding.transport(), request, importing,
                    result -> dispatcher.post(() -> providerReturned(result, session)));
            return true;
        } catch (RejectedExecutionException rejected) {
            providerActive = false;
            syncBinding.unavailable(request); updateBinding("Provider busy. Retry access.");
            return false;
        }
    }
    private synchronized void providerReturned(org.totipo.android.sync.ProviderIoLane.Result result, long session) {
        // The callback only admits detached transport data. All binding/session work runs on vault worker.
        // A result arriving during an incompatible command is discarded, never retained for unlock.
        try {
            worker.execute(() -> {
                dispatcher.assertWorkerThread();
                synchronized (this) {
                    providerActive = false;
                    queueSyncDrain();
                    if (!syncBinding.current(result.request())) {
                        // A replacement binding can be checked only after the old lane returns.
                        startProvider(false);
                        for (Listener listener : new ArrayList<>(listeners)) deliver(listener);
                        return;
                    }
                    // Persisted grants are local/platform metadata; recheck permission loss during IPC.
                    syncBinding.check();
                    if (!syncBinding.current(result.request())) {
                        updateBinding(null);
                        for (Listener listener : new ArrayList<>(listeners)) deliver(listener);
                        return;
                    }
                    if (result.importing() && (operating || session != sessionGeneration
                            || snapshot.state() != State.OPEN || vault == null
                            || vault.lifecycle() != ForegroundVaultCoordinator.State.OPEN)) {
                        updateBinding(null);
                        for (Listener listener : new ArrayList<>(listeners)) deliver(listener);
                        return;
                    }
                    syncBinding.accessibility(result.request(), result.accessible());
                    updateBinding(null);
                    if (!result.importing() || !result.accessible() || result.scan() == null) {
                        for (Listener listener : new ArrayList<>(listeners)) deliver(listener);
                        return;
                    }
                    // Reserve normal vault admission only for local validation/publication.
                    operating = true;
                }
                try {
                    var scan = result.scan();
                    if (scan.state() == org.totipo.android.provider.ProviderSnapshot.State.UNAVAILABLE)
                        syncBinding.unavailable(result.request());
                    var report = vault.sync(scan);
                    synchronized (this) {
                        importIntegrityAttention |= !report.contradictions().isEmpty()
                                || report.count(ImmutableCandidateImporter.Status.BLOCKED_EXISTING_DIFFERENT) > 0;
                    }
                    updateBinding(importMessage(report));
                    if (vault.lifecycle() == ForegroundVaultCoordinator.State.OPEN) render("Vault open");
                    else publish(State.ERROR_OPEN, Error.LOCAL_STORAGE_UNSAFE,
                            "Local publication failed. Lock the vault before retrying.", null);
                } catch (org.totipo.android.provider.ProviderVaultIdentity.Blocked blocked) {
                    updateBinding(blocked.status().message());
                } finally {
                    synchronized (this) {
                        operating = false; scheduleView(); queueSyncDrain();
                        for (Listener listener : new ArrayList<>(listeners)) deliver(listener);
                    }
                }
            });
        } catch (RejectedExecutionException busy) {
            providerActive = false;
            updateBinding(null);
        }
    }
    /** Process/controller teardown: best effort, no wait for provider IPC termination. */
    public synchronized void shutdown() {
        pendingChange = null; syncPending = false;
        cancelOutbound();
        sessionGeneration++; // Invalidate even results already queued before executor shutdown.
        try { worker.execute(() -> {
            if (joinCredential != null) { joinCredential.clear(); joinCredential = null; }
        }); } catch (RejectedExecutionException busy) { }
        providerIo.close(); worker.shutdown();
    }
    static String importMessage(ForegroundVaultCoordinator.Report report) {
        if (!report.contradictions().isEmpty()
                || report.count(ImmutableCandidateImporter.Status.BLOCKED_EXISTING_DIFFERENT) > 0)
            return "Integrity problem. Token changes need attention.";
        if (report.refresh() == ForegroundVaultCoordinator.Refresh.SKIPPED_UNSAFE
                || report.completion() == ForegroundVaultCoordinator.Completion.SESSION_FAILURE)
            return "Local publication failed. Lock the vault before retrying.";
        String admitted = report.count(ImmutableCandidateImporter.Status.IMPORTED) > 0
                ? "Changes imported; refresh requested." : "No new objects.";
        var coverage = report.evidence().transport().state();
        if (coverage == org.totipo.android.provider.ProviderSnapshot.State.UNAVAILABLE)
            return "Provider unavailable. " + admitted;
        if (coverage != org.totipo.android.provider.ProviderSnapshot.State.COMPLETE)
            return "Provider view incomplete. " + admitted;
        boolean ignored = report.evidence().groups().stream().flatMap(g -> g.siblings().stream())
                .anyMatch(c -> c.kind() != org.totipo.android.provider.ImmutableCandidateClassifier.Kind.VALID);
        return admitted + (ignored ? " Invalid or unavailable candidates ignored." : "");
    }
    public synchronized boolean lock() {
        State state = snapshot.state();
        syncPending = false;
        if (!operating) pendingChange = null;
        if (!operating && (state == State.OPEN || state == State.ERROR_OPEN || state == State.FAILED_CLOSE)) cancelOutbound();
        return (state == State.OPEN || state == State.ERROR_OPEN || state == State.FAILED_CLOSE)
                && submit(State.LOCKING, "Locking vault…", () -> {
                    synchronized (this) { cancelOutbound(); sessionGeneration++; }
                    if (closeOwned()) discover();
                });
    }
    private boolean closeOwned() {
        try {
            if (observation != null) { observation.close(); observation = null; }
            if (vault != null) { vault.close(); vault = null; }
            synchronized (this) { vaultId = "Locked"; dirty = false; observationFailed = false; }
            return true;
        } catch (Exception failure) { failedClose(); return false; }
    }
    private void failedClose() {
        publish(State.FAILED_CLOSE, Error.CLOSE_FAILED,
                "Vault closure failed; ownership is retained. Retry Lock.", null);
    }
}
