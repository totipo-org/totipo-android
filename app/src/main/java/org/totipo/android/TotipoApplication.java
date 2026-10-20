package org.totipo.android;

import android.app.Application;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.content.ClipboardManager;
import java.time.Instant;

/** Process-scoped owner. Activity recreation does not close or reauthenticate the vault. */
public final class TotipoApplication extends Application {
    private final VaultSelection selection = new VaultSelection();
    private VaultRegistry registry;
    private org.totipo.android.sync.SyncGrantOwnership syncGrants;
    public org.totipo.android.sync.SyncGrantOwnership syncGrantOwnership() { return syncGrants; }
    private String startupDiagnostic;
    private int started;
    private volatile boolean managementBusy;
    private volatile String managementNotice = "";
    private String joinActivation;
    private final java.util.concurrent.ExecutorService metadataWorker = java.util.concurrent.Executors.newSingleThreadExecutor();
    public boolean managementBusy() { return managementBusy; }
    public String managementNotice() { return managementNotice; }
    public java.util.List<VaultRegistry.Entry> localEntries() { return registry == null ? java.util.List.of() : registry.entries(); }
    public String lastSelected() { return registry == null ? null : registry.lastSelected(); }
    boolean joining(AndroidVaultController value) { return value != null && value.activationId().equals(joinActivation); }
    String localName(VaultRegistry.Entry entry) { return VaultUiModel.name(localEntries(), entry); }
    boolean unusedReservation(VaultRegistry.Entry entry) {
        if (entry.state() != VaultRegistry.State.PENDING || entry.creationAttempted()) return false;
        try (var children = java.nio.file.Files.list(registry.validateRoot(entry.id()))) { return children.findAny().isEmpty(); }
        catch (Exception unavailable) { return false; }
    }
    String entrySummary(VaultRegistry.Entry entry) {
        try {
            registry.validateRoot(entry.id());
            if (entry.state() == VaultRegistry.State.FORGETTING) return "Forget incomplete. Retry scoped cleanup.";
            if (entry.state() == VaultRegistry.State.DETACHED) return "Forgotten · encrypted data retained on this installation. "
                    + new org.totipo.android.sync.AndroidSyncFolderPort(this, entry.id()).configurationNotice();
            if (entry.state() == VaultRegistry.State.PENDING) return !unusedReservation(entry)
                    ? "Creation or Join interrupted. Open retained data; never recreate it."
                    : "Unused reservation · resume setup or recover allocation";
            var port = new org.totipo.android.sync.AndroidSyncFolderPort(this, entry.id());
            if (port.uncertain()) return "Sync configuration needs recovery";
            var binding = port.load();
            if (binding == null) return "Local only";
            if (!port.validTree(binding.uri()) || !port.grants(binding.uri()).read()) return "Folder access needed";
            return "Sync folder configured";
        } catch (Exception unavailable) { return "Local storage unavailable. Preserve app data for recovery."; }
    }
    private void requireUnselected() {
        if (managementBusy || selection.snapshot() != null || selection.owner() != null || registry == null)
            throw new IllegalStateException("Selection or cleanup still owned");
    }
    void selectFromUi(String id, boolean join) {
        try {
            requireUnselected();
            registry.refusePossibleDuplicate(id);
            var value = selectLocalEntry(id);
            joinActivation = join ? value.activationId() : null;
            managementNotice = "";
        } catch (Exception unavailable) {
            if (selection.snapshot() == null && selection.owner() != null) startupDiagnostic = "Controller construction did not complete. Selection authority is retained; restart the application process before retrying.";
            managementNotice = unavailable instanceof VaultRegistry.RecoveryRequired recovery ? recovery.diagnostic()
                    : "Selection unavailable. Preserve app data; restart the process before retrying.";
            selection.presentationChanged();
        }
    }
    void beginSetup(boolean join) {
        try {
            requireUnselected();
            var entries = registry.entries();
            VaultRegistry.Entry entry;
            if (entries.size() == 1 && entries.get(0).state() == VaultRegistry.State.PENDING && !entries.get(0).creationAttempted()) {
                entry = entries.get(0);
                registry.allocatePending(entry.id());
            } else entry = registry.reserve(null);
            selectFromUi(entry.id(), join);
        } catch (Exception unavailable) {
            managementNotice = "Setup reservation needs recovery. Existing data was preserved. Inspect remembered entries before starting another vault.";
            selection.presentationChanged();
        }
    }
    void renameEntry(String id, String label) {
        try { requireUnselected(); registry.rename(id, label == null || label.isEmpty() ? null : label); managementNotice = "Local label saved."; }
        catch (IllegalArgumentException invalid) { managementNotice = "Use at most 128 characters without control characters or invalid encoding."; }
        catch (Exception unavailable) { managementNotice = "Label could not be saved. Preserve app data and restart before retrying."; }
        selection.presentationChanged();
    }
    void recoverAllocation(String id) {
        try { requireUnselected(); registry.allocatePending(id); managementNotice = "Unused reservation recovered. Select Resume Create or Resume Join."; }
        catch (Exception unavailable) { managementNotice = "Allocation cannot safely be resumed. Preserve app data; attempted or nonempty roots must be opened or recovered explicitly."; }
        selection.presentationChanged();
    }
    void forgetEntry(String id) {
        try { requireUnselected(); registry.entry(id); }
        catch (Exception unavailable) { managementNotice = "Vault must finish retirement before cleanup."; selection.presentationChanged(); return; }
        managementBusy = true; managementNotice = "Removing convenience credentials and Sync configuration…";
        selection.presentationChanged();
        metadataWorker.execute(() -> {
            String result;
            try {
                var port = new org.totipo.android.sync.AndroidSyncFolderPort(this, id);
                VaultForget.run(registry, selection, id, new VaultForget.Cleanup() {
                    public void biometrics() throws Exception { cleanupRetiredEntryBiometrics(id); }
                    public void syncBinding() throws Exception {
                        if (!port.save(null)) throw new java.io.IOException("Binding cleanup incomplete");
                        syncGrants.cleanup();
                    }
                });
                syncGrants.cleanup();
                result = "Vault removed from normal selection. Encrypted private data remains on this installation. "
                        + (port.retained().isEmpty() ? "" : "Unused folder permission cleanup is deferred for safety. ");
            } catch (Exception incomplete) {
                result = "Forget incomplete. Encrypted data and its registry association were retained. Retry cleanup; if registry persistence failed, restart and recover metadata first.";
            }
            String message = result;
            new Handler(Looper.getMainLooper()).post(() -> { managementBusy = false; managementNotice = message; selection.presentationChanged(); });
        });
    }
    void changeVault() {
        joinActivation = null;
        selection.retire();
    }
    LocalReplicaOwner replicaOwner() { return selection.owner(); }
    public VaultSelection selection() { return selection; }
    public String startupDiagnostic() { return startupDiagnostic; }
    @Override public void onCreate() {
        super.onCreate();
        // Released Java Flow requires Android 11. Older runtimes get an explicit UI message.
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                // Android may expose /data/user/0 through a platform-owned alias. Resolve
                // only the trusted parent; the no-backup directory and all managed
                // descendants must themselves remain real directories.
                java.io.File noBackup = getNoBackupFilesDir();
                java.nio.file.Path privateBase = noBackup.getParentFile().getCanonicalFile().toPath().resolve(noBackup.getName());
                java.security.KeyStore keys = java.security.KeyStore.getInstance("AndroidKeyStore");
                keys.load(null);
                if (keys.containsAlias(BiometricRecord.LEGACY_ALIAS))
                    throw new VaultRegistry.RecoveryRequired(VaultRegistry.LEGACY_DIAGNOSTIC);
                registry = new VaultRegistry(privateBase);
                syncGrants = org.totipo.android.sync.AndroidSyncFolderPort.ownership(this, registry);
                // Keep the initial durable reservation, but setup begins only on explicit action.
                if (registry.entries().isEmpty()) registry.reserve(null);
            } catch (Exception failure) {
                startupDiagnostic = failure instanceof VaultRegistry.RecoveryRequired recovery
                        ? recovery.diagnostic() : "Local vault registry is unavailable. Preserve application data and recover storage before retrying.";
                return;
            }
            String initial = VaultUiModel.startupSelection(registry.entries(), registry.lastSelected());
            if (initial != null) selectFromUi(initial, false);
            registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
                public void onActivityStarted(android.app.Activity activity) {
                    if (++started == 1) {
                        var controller = vaultController();
                        if (controller != null) controller.foregroundChanged(true);
                    }
                }
                public void onActivityStopped(android.app.Activity activity) {
                    if (--started == 0 && !activity.isChangingConfigurations() && vaultController() != null) vaultController().foregroundChanged(false);
                }
                public void onActivityCreated(android.app.Activity activity, android.os.Bundle state) {}
                public void onActivityResumed(android.app.Activity activity) {}
                public void onActivityPaused(android.app.Activity activity) {}
                public void onActivitySaveInstanceState(android.app.Activity activity, android.os.Bundle state) {}
                public void onActivityDestroyed(android.app.Activity activity) {}
            });
        }
    }
    // Production navigation and controlled qualification use this same factory/authority.
    VaultSelection.Snapshot selectLocalEntry(String id) throws java.io.IOException {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("Selection must run on main");
        Handler main = new Handler(Looper.getMainLooper());
        return selection.select(registry, id, replicaOwner -> {
            var controller = new AndroidVaultController(replicaOwner, new AndroidVaultController.Dispatcher() {
                public void post(Runnable action) { main.post(action); }
                public Runnable after(long millis, Runnable action) {
                    main.postDelayed(action, millis); return () -> main.removeCallbacks(action);
                }
                public void assertDispatchThread() {
                    if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("UI dispatch must be main thread");
                }
                public void assertWorkerThread() {
                    if (Looper.myLooper() == Looper.getMainLooper()) throw new IllegalStateException("Vault work must be off main thread");
                }
            }, new ApplicationVaultBackend(), new TotpPresentation.Time() {
                public Instant wall() { return Instant.now(); }
                public long elapsedMillis() { return SystemClock.elapsedRealtime(); }
            }, new PlatformCodeClipboard(getSystemService(ClipboardManager.class)),
                    new org.totipo.android.sync.SyncFolderBinding(new org.totipo.android.sync.AndroidSyncFolderPort(this, replicaOwner.localId())));
            controller.foregroundChanged(started > 0);
            return controller;
        });
    }
    String selectedVaultLabel(AndroidVaultController controller) {
        var selected = selection.snapshot();
        if (selected != null && selected.controller() == controller) try {
            return localName(registry.entry(selected.localId()));
        } catch (java.io.IOException unavailable) { /* presentation only */ }
        return "Selected vault";
    }
    BiometricCredentials biometricCredentials(AndroidVaultController controller) throws java.io.IOException {
        var selected = selection.snapshot();
        if (selected == null || selected.controller() != controller || !controller.selectedAuthority())
            throw new IllegalStateException("Biometric activation retired");
        return new AndroidBiometricCredentials(this, registry, selected.localId());
    }
    /** A-MV5 hook. No session/store deletion, and never usable while that entry is selected. */
    void cleanupRetiredEntryBiometrics(String id) throws Exception {
        selection.requireRetiredEntry(id);
        selection.beginMaintenance(id);
        try {
            registry.entry(id);
            new AndroidBiometricCredentials(this, registry, id).delete();
        } finally { selection.endMaintenance(id); }
    }
    public AndroidVaultController vaultController() { var value = selection.snapshot(); return value == null ? null : value.controller(); }
}
