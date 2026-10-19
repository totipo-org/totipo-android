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
    private AndroidVaultController vaultController;
    private LocalReplicaOwner replicaOwner;
    private final LocalReplicaOwner.Admission admission = new LocalReplicaOwner.Admission();
    private String startupDiagnostic;
    LocalReplicaOwner replicaOwner() { return replicaOwner; }
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
                if (keys.containsAlias(BiometricRecord.ALIAS))
                    throw new VaultRegistry.RecoveryRequired(VaultRegistry.LEGACY_DIAGNOSTIC);
                var registry = new VaultRegistry(privateBase);
                // A-MV1 exposes only one selected controller. No implicit switch or new vault
                // when remembered storage is missing, corrupt or ambiguous.
                if (registry.entries().isEmpty()) registry.reserve(null);
                if (registry.entries().size() != 1)
                    throw new VaultRegistry.RecoveryRequired("Multiple local vaults require selection support in a later version. All data was preserved.");
                replicaOwner = admission.select(registry, registry.entries().get(0).id());
            } catch (Exception failure) {
                startupDiagnostic = failure instanceof VaultRegistry.RecoveryRequired recovery
                        ? recovery.diagnostic() : "Local vault registry is unavailable. Preserve application data and recover storage before retrying.";
                return;
            }
            Handler main = new Handler(Looper.getMainLooper());
            vaultController = new AndroidVaultController(replicaOwner, new AndroidVaultController.Dispatcher() {
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
            registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
                private int started;
                public void onActivityStarted(android.app.Activity activity) {
                    if (++started == 1) vaultController.foregroundChanged(true);
                }
                public void onActivityStopped(android.app.Activity activity) {
                    if (--started == 0 && !activity.isChangingConfigurations()) vaultController.foregroundChanged(false);
                }
                public void onActivityCreated(android.app.Activity activity, android.os.Bundle state) {}
                public void onActivityResumed(android.app.Activity activity) {}
                public void onActivityPaused(android.app.Activity activity) {}
                public void onActivitySaveInstanceState(android.app.Activity activity, android.os.Bundle state) {}
                public void onActivityDestroyed(android.app.Activity activity) {}
            });
        }
    }
    public AndroidVaultController vaultController() { return vaultController; }
}
