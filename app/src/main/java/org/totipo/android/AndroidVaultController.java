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
    public interface Listener { void changed(Snapshot state); }
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
        ForegroundVaultCoordinator.TotpResult generateTotp(ForegroundVaultCoordinator vault, TokenId id, Instant now,
                                                         ForegroundVaultCoordinator.ObservedToken expected) {
            return vault.generateTotp(id, now, expected);
        }
    }
    public record SyncView(SyncFolderBinding.View binding, String message) {}
    private final SyncFolderBinding syncBinding;
    private final org.totipo.android.sync.ProviderIoLane providerIo = new org.totipo.android.sync.ProviderIoLane();
    private boolean providerActive;
    private long sessionGeneration;
    private SyncView syncView = new SyncView(new SyncFolderBinding.View(
            SyncFolderBinding.Status.NOT_CONFIGURED, false, false), "Not configured");
    private String initialTreeUri;
    private boolean importIntegrityAttention;
    private final LocalReplicaOwner owner;
    private final Dispatcher dispatcher;
    private final Backend backend;
    private final TotpPresentation presentation;
    private final TotpPresentation.Time time;
    private long presentationEpoch;
    private boolean clearingPresentation;
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), action -> {
                Thread thread = new Thread(action, "Totipo-vault"); thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private final Set<Listener> listeners = new LinkedHashSet<>();
    private Snapshot snapshot = new Snapshot(State.STARTING, Error.NONE, "Checking local vault…", null);
    // Only worker accesses owned resources. Admission and snapshot fields use this monitor.
    private AddTokenOutcome addOutcome;
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
            if (syncBinding != null) { syncBinding.restore(); updateBinding(null); startProvider(false); }
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
    private void deliver(Listener listener) {
        dispatcher.post(() -> {
            dispatcher.assertDispatchThread();
            Snapshot value;
            synchronized (this) { if (!listeners.contains(listener)) return; value = snapshot(); }
            listener.changed(value);
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
        for (Listener listener : new ArrayList<>(listeners)) deliver(listener);
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
        if (operating || snapshot.state() != State.OPEN) return false;
        boolean copied = presentation.copy();
        if (copied || presentation.display().code() != null) {
            snapshot = new Snapshot(snapshot.state(), snapshot.error(),
                    copied ? "Code copied" : "Clipboard unavailable; code was not copied.",
                    snapshot.view(), snapshot.revealedCode(), snapshot.remainingSeconds());
            presentationChanged();
        }
        return copied;
    }
    public synchronized boolean showCode(TokenId id) {
        dispatcher.assertDispatchThread();
        if (operating || snapshot.state() != State.OPEN) return false;
        var expected = snapshot.view().tokens().stream().filter(token -> token.id().equals(id)).findFirst().orElse(null);
        if (expected == null) return false;
        long epoch = presentationEpoch + 1; // submit's publication revokes the previous reveal.
        return submit(State.BUSY, "Generating code…", () -> {
            ForegroundVaultCoordinator.TotpResult result;
            try { result = backend.generateTotp(vault, id, time.wall(), expected); }
            catch (RuntimeException unavailable) {
                result = new ForegroundVaultCoordinator.TotpResult(ForegroundVaultCoordinator.TotpStatus.FAILED, null);
            }
            synchronized (this) {
                // Observation/Hide can revoke an in-flight reveal while crypto runs.
                if (epoch != presentationEpoch || dirty || observationFailed) {
                    publish(State.OPEN, Error.NONE, "Vault changed; show the code again.", snapshot.view()); return;
                }
                snapshot = new Snapshot(State.OPEN, snapshot.error(),
                        result.status() == ForegroundVaultCoordinator.TotpStatus.AVAILABLE
                                ? "Vault open" : "Code unavailable; token needs attention.", snapshot.view());
                if (result.status() != ForegroundVaultCoordinator.TotpStatus.AVAILABLE
                        || !presentation.reveal(result.revealed())) presentationChanged();
            }
        });
    }
    private synchronized boolean submit(State state, String message, Runnable operation) {
        if (operating) return false;
        operating = true;
        Snapshot before = snapshot;
        publish(state, Error.NONE, message, state == State.BUSY ? before.view() : null);
        try {
            worker.execute(() -> {
                dispatcher.assertWorkerThread();
                try { operation.run(); }
                finally { synchronized (this) {
                    operating = false; scheduleView();
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
            admitted = submit(create ? State.CREATING : State.UNLOCKING,
                    create ? "Creating vault…" : "Unlocking vault…", () -> {
                try {
                    Error error;
                    if (create) {
                        var result = backend.create(owner, credential); vault = result.vault();
                        if (result.failure() == null && result.cause() == null) { opened(); return; }
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
                    if (current.state() != State.FAILED_CLOSE) publish(current.state(), error, errorMessage(error), null);
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
        observation = current.observe(() -> signal(false), () -> signal(true));
        render("Vault open");
    }
    private synchronized void signal(boolean failed) {
        clearPresentation(); presentationChanged();
        dirty = true; observationFailed |= failed; scheduleView();
    }
    private synchronized void scheduleView() {
        if (!dirty || operating || viewQueued) return;
        viewQueued = true;
        try {
            worker.execute(() -> {
                dispatcher.assertWorkerThread();
                synchronized (this) { if (operating) { viewQueued = false; return; } dirty = false; }
                try {
                    if (vault != null && vault.lifecycle() == ForegroundVaultCoordinator.State.OPEN
                            && (snapshot().state() == State.OPEN || snapshot().state() == State.BUSY)) render(snapshot().message());
                } finally { synchronized (this) { viewQueued = false; scheduleView(); } }
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
    public synchronized boolean canAddToken() { return snapshot.state() == State.OPEN && !operating; }
    /** Takes ownership on every path. Uses existing bounded admission and the live session. */
    public synchronized boolean addToken(AddTokenRequest request) {
        dispatcher.assertDispatchThread();
        boolean admitted = false;
        try {
            if (!canAddToken()) return false;
            addOutcome = null;
            admitted = submit(State.BUSY, "Adding token…", () -> {
                try {
                    var result = vault.addToken(request);
                    synchronized (this) { addOutcome = result; }
                    String message = switch (result.status()) {
                        case ADDED -> "Token added";
                        case INVALID_SECRET -> "Enter a valid Base32 secret containing 1–128 decoded bytes.";
                        case INVALID_FIELDS -> "Check issuer/account (up to 256 UTF-8 bytes), algorithm, digits and period.";
                        case PUBLICATION_UNCERTAIN -> "Token publication uncertain. Refresh before deciding whether to add again.";
                        case CONFLICT -> "Vault changed; token was not added. Refresh before retrying.";
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
        return snapshot.state() == State.OPEN && submit(State.BUSY, "Requesting refresh…", () -> {
            try { vault.requestRefresh(); render("Refresh requested"); }
            catch (RuntimeException failure) { publish(State.ERROR_OPEN, Error.OPEN_FAILED, "Refresh failed. Lock the vault before retrying.", null); }
        });
    }
    /** Internal production boundary; no provider selection or product Sync button yet.
     * Uses the SAME coordinator/session and accepts no credential. */
    public synchronized boolean sync(Scan scan) {
        return snapshot.state() == State.OPEN && submit(State.BUSY, "Observing inbound immutable objects…", () -> {
            try {
                var report = vault.sync(scan);
                if (vault.lifecycle() == ForegroundVaultCoordinator.State.OPEN) render(
                        report.refresh() == ForegroundVaultCoordinator.Refresh.REQUESTED ? "Refresh requested" : "Inbound scan observed");
                else publish(State.ERROR_OPEN, Error.LOCAL_STORAGE_UNSAFE, "Local storage needs attention. Lock the vault before retrying.", null);
            } catch (RuntimeException failure) {
                publish(State.ERROR_OPEN, Error.LOCAL_STORAGE_UNSAFE, "Inbound observation failed. Lock the vault before retrying.", null);
            }
        });
    }
    public synchronized SyncView syncView() { return syncView; }
    /** Private picker hint only; never rendered in product status. */
    public synchronized String initialTreeUri() { return initialTreeUri; }
    public synchronized boolean canManageSyncFolder() { return syncBinding != null && !operating; }
    public synchronized boolean canImportProviderChanges() {
        return canManageSyncFolder() && !providerActive && snapshot.state() == State.OPEN
                && syncView.binding().status() == SyncFolderBinding.Status.READY;
    }
    private synchronized void updateBinding(String message) {
        var binding = syncBinding.view();
        initialTreeUri = syncBinding.initialUri();
        String detail = message == null ? switch (binding.status()) {
            case NOT_CONFIGURED -> "Not configured";
            case CHECKING -> "Checking sync folder…";
            case READY -> "Connected";
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
            operation.run();
            if (before.state() == State.OPEN) render(before.message());
            else publish(before.state(), before.error(), before.message(), before.view());
        });
    }
    public synchronized boolean chooseSyncFolder(boolean cancelled, String uri, int flags) {
        dispatcher.assertDispatchThread();
        return folderOperation(() -> {
            boolean accepted = syncBinding.choose(cancelled, uri,
                    (flags & android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0,
                    (flags & android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0);
            updateBinding(accepted ? null : "Folder access could not be saved. Choose folder again.");
            if (accepted && !cancelled) startProvider(false);
        });
    }
    public synchronized boolean disconnectSyncFolder() {
        return folderOperation(() -> updateBinding(syncBinding.disconnect() ? null
                : "Folder configuration could not be cleared. Try again."));
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
                } finally {
                    synchronized (this) {
                        operating = false; scheduleView();
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
        sessionGeneration++; // Invalidate even results already queued before executor shutdown.
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
        return (state == State.OPEN || state == State.ERROR_OPEN || state == State.FAILED_CLOSE)
                && submit(State.LOCKING, "Locking vault…", () -> {
                    synchronized (this) { sessionGeneration++; }
                    if (closeOwned()) discover();
                });
    }
    private boolean closeOwned() {
        try {
            if (observation != null) { observation.close(); observation = null; }
            if (vault != null) { vault.close(); vault = null; }
            synchronized (this) { dirty = false; observationFailed = false; }
            return true;
        } catch (Exception failure) { failedClose(); return false; }
    }
    private void failedClose() {
        publish(State.FAILED_CLOSE, Error.CLOSE_FAILED,
                "Vault closure failed; ownership is retained. Retry Lock.", null);
    }
}
