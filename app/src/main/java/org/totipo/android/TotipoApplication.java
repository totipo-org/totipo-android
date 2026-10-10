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
    @Override public void onCreate() {
        super.onCreate();
        // Released Java Flow requires Android 11. Older runtimes get an explicit UI message.
        if (Build.VERSION.SDK_INT >= 30) {
            Handler main = new Handler(Looper.getMainLooper());
            vaultController = new AndroidVaultController(LocalReplicaOwner.from(this), new AndroidVaultController.Dispatcher() {
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
                    new org.totipo.android.sync.SyncFolderBinding(new org.totipo.android.sync.AndroidSyncFolderPort(this)));
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
