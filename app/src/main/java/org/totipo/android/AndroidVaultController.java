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
import org.totipo.android.provider.ProviderSnapshot.Scan;
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
    public record Snapshot(State state, Error error, String message, View view) {}
    public interface Listener { void changed(Snapshot state); }
    // Android dispatch/thread policy is supplied by Application; JVM tests exercise this seam.
    interface Dispatcher { void post(Runnable action); void assertDispatchThread(); void assertWorkerThread(); }
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
    }
    private final LocalReplicaOwner owner;
    private final Dispatcher dispatcher;
    private final Backend backend;
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), action -> {
                Thread thread = new Thread(action, "Totipo-vault"); thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private final Set<Listener> listeners = new LinkedHashSet<>();
    private Snapshot snapshot = new Snapshot(State.STARTING, Error.NONE, "Checking local vault…", null);
    // Only worker accesses owned resources. Admission and snapshot fields use this monitor.
    private ForegroundVaultCoordinator vault;
    private AutoCloseable observation;
    private boolean operating, dirty, viewQueued, observationFailed;

    AndroidVaultController(LocalReplicaOwner owner, Dispatcher dispatcher) { this(owner, dispatcher, new Backend()); }
    AndroidVaultController(LocalReplicaOwner owner, Dispatcher dispatcher, Backend backend) {
        this.owner = owner; this.dispatcher = dispatcher; this.backend = backend;
        submit(State.STARTING, "Checking local vault…", this::discover);
    }
    public synchronized Snapshot snapshot() { return snapshot; }
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
            synchronized (this) { if (!listeners.contains(listener)) return; value = snapshot; }
            listener.changed(value);
        });
    }
    private void publish(State state, Error error, String message, View view) {
        ArrayList<Listener> targets;
        synchronized (this) {
            snapshot = new Snapshot(state, error, message, view);
            targets = new ArrayList<>(listeners);
        }
        for (Listener listener : targets) deliver(listener);
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
                finally { synchronized (this) { operating = false; scheduleView(); } }
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
        publish(State.OPEN, diagnostics ? Error.OBSERVATION_DIAGNOSTICS : Error.NONE,
                diagnostics ? "Vault needs attention: unresolved, conflicting, or diagnostic observations are present." : message, view);
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
    public synchronized boolean lock() {
        State state = snapshot.state();
        return (state == State.OPEN || state == State.ERROR_OPEN || state == State.FAILED_CLOSE)
                && submit(State.LOCKING, "Locking vault…", () -> { if (closeOwned()) discover(); });
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
