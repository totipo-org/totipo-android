package org.totipo.android;

import android.app.Application;
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
    private BackupExportJournal backupJournal;
    private String interruptedBackup = "";
    BackupExport.Journal backupJournal() { return backupJournal; }
    BackupExport.Destination backupDestination(android.net.Uri uri) { return new AndroidBackupDocument(getContentResolver(), uri); }
    String interruptedBackup() { return interruptedBackup; }
    HistoryImport.Source historySource(android.net.Uri uri) {
        var resolver = getContentResolver();
        return () -> resolver.openInputStream(uri);
    }
    private BackupRestoreFlow restoreFlow;
    private RestoreJourney restoreJourney;
    private String restoredActivation;
    private String restoreOpeningNotice = "";
    String restoreOpeningNotice(AndroidVaultController controller) {
        return controller != null && controller.activationId().equals(restoredActivation) ? restoreOpeningNotice : "";
    }
    BackupRestoreFlow restoreFlow() { return restoreFlow; }
    void beginRestore(String recoveryId) {
        try {
            requireUnselected();
            if (recoveryId != null && registry.entry(recoveryId).restore() == null) return;
            managementNotice = "";
            restoreFlow = new BackupRestoreFlow(recoveryId, SystemClock::elapsedRealtime);
            var origin = restoreFlow;
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (restoreFlow == origin && (origin.active() || origin.stage() == BackupRestoreFlow.Stage.DONE)) cancelRestore(origin);
            }, 15 * 60 * 1000L);
            selection.presentationChanged();
        } catch (Exception unavailable) { managementNotice = "Wait for the current operation to finish before restoring."; selection.presentationChanged(); }
    }
    void readRestore(BackupRestoreFlow origin, android.net.Uri uri) {
        if (origin != restoreFlow || managementBusy || selection.owner() != null || !origin.reading()) return;
        restoreWork(origin, () -> origin.freeze(() -> getContentResolver().openInputStream(uri)));
    }
    boolean authenticateRestore(BackupRestoreFlow origin, char[] password, String name) {
        try { return restoreJourney.submit(origin, password, name); }
        catch (IllegalArgumentException invalid) {
            managementNotice = "Use a vault name of at most 128 characters without control characters.";
            return false;
        }
    }
    void installRestore(BackupRestoreFlow origin) { restoreJourney.confirm(origin); }
    private void restoreWork(BackupRestoreFlow origin, Runnable action) { restoreJourney.work(origin, action); }
    void cancelRestore(BackupRestoreFlow origin) { restoreJourney.cancel(origin); }
    void dismissRestore(BackupRestoreFlow origin) {
        if (restoreFlow != origin || managementBusy || origin.active()) return;
        restoreFlow = null; managementNotice = ""; selection.presentationChanged();
    }
    void finishRestoreSelection(BackupRestoreFlow origin, String id) { restoreJourney.finish(origin, id, false); }
    private void restoredSelection(String id) {
        var controller = vaultController();
        if (controller == null || !controller.localId().equals(id)) return;
        restoredActivation = controller.activationId();
        restoreOpeningNotice = "Backup installed. Enter the original vault password to finish restoring.";
        controller.attach(new AndroidVaultController.Listener() {
            public void changed(AndroidVaultController.Snapshot state) {
                if (!controller.activationId().equals(restoredActivation) || !controller.selectedAuthority()) {
                    controller.detach(this); return;
                }
                if (state.state() == AndroidVaultController.State.OPEN) {
                    // OPEN follows normal observation, authenticated inventory and registry promotion.
                    restoreOpeningNotice = "Vault restored."; controller.detach(this);
                    selection.presentationChanged();
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (!controller.activationId().equals(restoredActivation)) return;
                        restoreOpeningNotice = ""; selection.presentationChanged();
                    }, 6000);
                }
            }
        });
    }
    private int started;
    // Rotation retains foreground authorization through the temporary zero-Activity gap.
    private boolean foregroundAuthorized;
    private volatile boolean managementBusy;
    private volatile String managementNotice = "";
    private String joinActivation;
    private String setupNameActivation;
    private SetupVaultName setupName;
    SetupVaultName setupName(AndroidVaultController controller) {
        if (controller == null || !controller.selectedAuthority() || vaultController() != controller)
            throw new IllegalStateException("Setup activation retired");
        if (!controller.activationId().equals(setupNameActivation)) {
            setupNameActivation = controller.activationId(); setupName = new SetupVaultName();
            String existing = selectedAssignedName(controller);
            if (!existing.isEmpty()) setupName.edit(existing);
        }
        return setupName;
    }
    boolean saveSelectedName(AndroidVaultController controller, String value) {
        try {
            if (managementBusy || vaultController() != controller || !controller.selectedAuthority()) return false;
            String label = VaultUiModel.optionalLabel(value);
            if (!java.util.Objects.equals(registry.entry(controller.localId()).label(), label)) registry.rename(controller.localId(), label);
            return true;
        } catch (IllegalArgumentException invalid) {
            managementNotice = "Use at most 128 UTF-16 code units without control characters or invalid encoding.";
        } catch (Exception unavailable) {
            managementNotice = "Name could not be saved. The same vault allocation is retained. Preserve app data and restart before retrying.";
        }
        return false;
    }
    void suggestJoinName(AndroidVaultController controller, android.net.Uri tree) {
        SetupVaultName draft = setupName(controller);
        metadataWorker.execute(() -> {
            String display = null;
            try {
                var document = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree,
                        android.provider.DocumentsContract.getTreeDocumentId(tree));
                try (var cursor = getContentResolver().query(document, new String[]{
                        android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()
                            && android.provider.DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(1))) display = cursor.getString(0);
                }
            } catch (Exception unavailable) { /* No trustworthy metadata: leave unnamed. */ }
            String suggestion = display;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (vaultController() != controller || !controller.selectedAuthority() || setupName != draft) return;
                draft.suggest(suggestion); selection.presentationChanged();
            });
        });
    }
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
            if (entry.restore() != null) return entry.restore().phase() == VaultRegistry.RestorePhase.INSTALLED
                    ? "Backup installed. Enter the original password to finish restoring."
                    : "Restore unfinished. Keep the app’s data and original backup. Choose the exact original ZIP and enter its password to continue.";
            registry.validateRoot(entry.id());
            if (entry.state() == VaultRegistry.State.FORGETTING) return "Forget incomplete. Retry scoped cleanup.";
            if (entry.state() == VaultRegistry.State.DETACHED) return "Encrypted vault data is kept on this device. You’ll need the vault password. "
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
        if (restoreFlow != null || managementBusy || selection.snapshot() != null || selection.owner() != null || registry == null)
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
        try { requireUnselected(); registry.rename(id, VaultUiModel.optionalLabel(label)); managementNotice = "Local name saved."; }
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
        setupName = null; setupNameActivation = null;
        selection.retire();
    }
    LocalReplicaOwner replicaOwner() { return selection.owner(); }
    public VaultSelection selection() { return selection; }
    public String startupDiagnostic() { return startupDiagnostic; }
    @Override public void onCreate() {
        super.onCreate();
        try {
            // Android may expose /data/user/0 through a platform-owned alias. Resolve
            // only the trusted parent; the no-backup directory and all managed
            // descendants must themselves remain real directories.
            java.io.File noBackup = getNoBackupFilesDir();
            java.nio.file.Path privateBase = noBackup.getParentFile().getCanonicalFile().toPath().resolve(noBackup.getName());
            backupJournal = new BackupExportJournal(privateBase);
            if (backupJournal.interrupted()) interruptedBackup = "A backup was interrupted. Check the chosen location; the file may be empty, incomplete or complete. Start a new backup if needed.";
            java.security.KeyStore keys = java.security.KeyStore.getInstance("AndroidKeyStore");
            keys.load(null);
            if (keys.containsAlias(BiometricRecord.LEGACY_ALIAS))
                throw new VaultRegistry.RecoveryRequired(VaultRegistry.LEGACY_DIAGNOSTIC);
            registry = new VaultRegistry(privateBase);
            syncGrants = org.totipo.android.sync.AndroidSyncFolderPort.ownership(this, registry);
            restoreJourney = new RestoreJourney(new RestoreJourney.Host() {
                public BackupRestoreFlow current() { return restoreFlow; }
                public boolean busy() { return managementBusy; }
                public void busy(boolean value) { managementBusy = value; }
                public boolean foregroundUnselected() {
                    return foregroundAuthorized && selection.snapshot() == null && selection.owner() == null;
                }
                public void changed() { selection.presentationChanged(); }
                public void selected(BackupRestoreFlow origin, String id, boolean restored) {
                    restoreFlow = null; selectFromUi(id, false);
                    if (restored) restoredSelection(id);
                }
            }, registry, selection, metadataWorker, action -> new Handler(Looper.getMainLooper()).post(action));
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
                    foregroundAuthorized = true;
                    var controller = vaultController();
                    if (controller != null) controller.foregroundChanged(true);
                }
            }
            public void onActivityStopped(android.app.Activity activity) {
                if (--started == 0 && !activity.isChangingConfigurations()) {
                    foregroundAuthorized = false;
                    if (vaultController() != null) vaultController().foregroundChanged(false);
                    restoreJourney.background();
                }
            }
            public void onActivityCreated(android.app.Activity activity, android.os.Bundle state) {}
            public void onActivityResumed(android.app.Activity activity) {}
            public void onActivityPaused(android.app.Activity activity) {}
            public void onActivitySaveInstanceState(android.app.Activity activity, android.os.Bundle state) {}
            public void onActivityDestroyed(android.app.Activity activity) {}
        });
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
    String selectedAssignedName(AndroidVaultController controller) {
        if (vaultController() == controller && controller.selectedAuthority()) try {
            String label = registry.entry(controller.localId()).label(); return label == null ? "" : label;
        } catch (java.io.IOException unavailable) { /* presentation only */ }
        return "";
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
