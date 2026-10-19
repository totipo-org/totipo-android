package org.totipo.android;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.widget.EditText;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import org.totipo.android.sync.*;

/** Disposable-package-only A -> B through the actual Application authority/factory.
 * Synthetic blocked transport enters the actual provider lane; no second admission,
 * executor replacement, user installation, grant or external provider is used. */
public final class ControllerRetirementRegression extends Instrumentation {
    private int checks;
    private String step = "startup";
    private final AtomicReference<MainActivity> activity = new AtomicReference<>();
    private void check(boolean condition, String label) {
        step = label; if (!condition) throw new AssertionError(label); checks++;
    }
    private Object field(Object target, String name) {
        try { var f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target); }
        catch (Exception failure) { throw new AssertionError(failure); }
    }
    private void await(BooleanSupplier condition) {
        long end = SystemClock.uptimeMillis() + 150_000;
        while (!condition.getAsBoolean()) {
            if (SystemClock.uptimeMillis() >= end) throw new AssertionError("timeout");
            // Observation polling only. Negative ordering assertions below use held latches.
            SystemClock.sleep(20);
        }
        waitForIdleSync();
    }
    private void command(BooleanSupplier action) {
        var admitted = new AtomicBoolean();
        await(() -> { runOnMainSync(() -> admitted.set(action.getAsBoolean())); return admitted.get(); });
    }
    private void copyRevealed(AndroidVaultController controller, String label) {
        var copied = new AtomicBoolean();
        await(() -> {
            runOnMainSync(() -> {
                if (controller.snapshot().revealedCode() != null && controller.copyShownCode()) copied.set(true);
                else controller.showCode(controller.snapshot().view().tokens().get(0).id());
            });
            return copied.get();
        });
        check(copied.get(), label);
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle(); int code = 0; var release = new CountDownLatch(1);
        try {
            check(getTargetContext().getPackageName().equals("org.totipo.securityqualification"), "isolated_package");
            check(!getTargetContext().getSystemService(KeyguardManager.class).isKeyguardLocked(), "device_unlocked");
            runOnMainSync(() -> {}); // Application startup completion barrier.
            var app = (TotipoApplication)getTargetContext().getApplicationContext();
            var registry = (VaultRegistry)field(app, "registry");
            var authority = app.selection(); var a = app.vaultController();
            check(a != null && registry.entries().size() == 1, "fresh_single_entry");
            await(() -> a.snapshot().state() == AndroidVaultController.State.NO_LOCAL_VAULT);
            app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                public void onActivityCreated(Activity value, Bundle state) { if (value instanceof MainActivity main) activity.set(main); }
                public void onActivityResumed(Activity value) { value.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); }
                public void onActivityStarted(Activity value) {}
                public void onActivityStopped(Activity value) {}
                public void onActivityPaused(Activity value) {}
                public void onActivitySaveInstanceState(Activity value, Bundle state) {}
                public void onActivityDestroyed(Activity value) {}
            });
            startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            command(() -> a.create("Public A retirement fixture".toCharArray()));
            await(() -> a.snapshot().state() == AndroidVaultController.State.OPEN);
            command(() -> a.addToken(new AddTokenRequest("Public retirement", "A", org.totipo.TotpAlgorithm.SHA1, 6, 30, new char[]{'M','Y'})));
            await(() -> a.snapshot().view() != null && a.snapshot().view().tokens().size() == 1);
            copyRevealed(a, "copy_owned_code");
            command(() -> {
                var button = (android.widget.Button)field(activity.get(), "add");
                return button != null && button.performClick();
            });
            var secret = (EditText)field(activity.get(), "secret");
            runOnMainSync(() -> secret.setText("MY"));
            final MainActivity original = activity.get(); runOnMainSync(original::recreate);
            await(() -> activity.get() != original);
            check(field(activity.get(), "controller") == a, "rotation_retains_activation");
            check(secret.length() == 0, "rotation_clears_form");
            copyRevealed(a, "copy_before_retirement");
            command(() -> {
                var button = (android.widget.Button)field(activity.get(), "add");
                return button != null && button.performClick();
            });
            var retirementSecret = (EditText)field(activity.get(), "secret");
            runOnMainSync(() -> retirementSecret.setText("MY"));
            // External picker callback carries A's activation through recreation.
            var picker = MainActivity.class.getDeclaredField("pickerActivation"); picker.setAccessible(true);
            runOnMainSync(() -> { try { picker.set(activity.get(), a.activationId()); } catch (Exception e) { throw new AssertionError(e); } });
            var entered = new CountDownLatch(1); var late = new AtomicBoolean();
            var lane = (ProviderIoLane)field(a, "providerIo");
            var port = new SyncFolderBinding.Port() {
                public SyncFolderBinding.Stored load() { return null; }
                public boolean save(SyncFolderBinding.Stored stored) { return true; }
                public boolean validTree(String uri) { return true; }
                public SyncFolderBinding.Grants grants(String uri) { return new SyncFolderBinding.Grants(true, true); }
                public void take(String uri, boolean read, boolean write) { throw new AssertionError(); }
                public void release(String uri, boolean read, boolean write) { throw new AssertionError(); }
                public void probe(String uri) {}
                public org.totipo.android.provider.ProviderSnapshot.Scan scan(String uri) {
                    entered.countDown();
                    try { if (!release.await(150, TimeUnit.SECONDS)) throw new AssertionError(); }
                    catch (InterruptedException failure) { throw new AssertionError(failure); }
                    return null;
                }
            };
            var handler = new Handler(Looper.getMainLooper());
            lane.submit(port, new SyncFolderBinding.Request(0, "public-fixture"), true,
                    value -> lane.dispatch(handler::post, () -> late.set(true)));
            check(entered.await(10, TimeUnit.SECONDS), "provider_entered");
            var bId = registry.reserve("Public B retirement fixture").id();
            runOnMainSync(() -> { authority.retire(); authority.retire(); });
            check(a.snapshot().view() == null && a.snapshot().revealedCode() == null, "retirement_sensitive_snapshot_empty");
            waitForIdleSync();
            check(retirementSecret.length() == 0, "retirement_clears_form");
            await(() -> a.retirement() == AndroidVaultController.Retirement.WAITING_PROVIDER);
            runOnMainSync(() -> {
                var clipboard = getTargetContext().getSystemService(android.content.ClipboardManager.class);
                check(!clipboard.hasPrimaryClip(), "owned_clipboard_retired");
            });
            var ingress = (OtpAuthEnrollmentActivity)startActivitySync(new Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse("otpauth://totp/Public?secret=MY"))
                    .setClass(getTargetContext(), OtpAuthEnrollmentActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            check(field(ingress, "draft") == null, "external_enrollment_during_retirement_rejected");
            runOnMainSync(ingress::finish); waitForIdleSync();
            check(field(a, "vault") == null, "session_closed_before_provider_drain");
            check(authority.snapshot().controller() == a, "stuck_provider_holds_A");
            runOnMainSync(() -> {
                try { app.selectLocalEntry(bId); throw new AssertionError("B constructed before drain"); }
                catch (IllegalStateException expected) { checks++; }
                catch (java.io.IOException failure) { throw new AssertionError(failure); }
            });
            final MainActivity retiringActivity = activity.get();
            runOnMainSync(retiringActivity::recreate); await(() -> activity.get() != retiringActivity);
            check(field(activity.get(), "controller") == a, "rotation_during_retirement");
            check(a.snapshot().view() == null, "rotation_never_restores_tokens");
            release.countDown(); await(() -> authority.snapshot() == null);
            check(a.retirement() == AndroidVaultController.Retirement.SUCCESS, "complete_retirement");
            check(!late.get(), "late_provider_handoff_discarded");
            runOnMainSync(() -> {
                try {
                    var selected = app.selectLocalEntry(bId);
                    check((boolean)field(selected.controller(), "foreground") == ((int)field(app, "started") > 0), "B_receives_actual_foreground");
                } catch (Exception e) { throw new AssertionError(e); }
            });
            var b = app.vaultController(); await(() -> b.snapshot().state() == AndroidVaultController.State.NO_LOCAL_VAULT);
            check(b != a && !b.activationId().equals(a.activationId()), "independent_B_activation");
            await(() -> field(activity.get(), "controller") == b);
            // A stale result must neither acquire a grant nor configure B.
            final MainActivity bActivity = activity.get();
            runOnMainSync(() -> {
                try {
                    picker.set(bActivity, a.activationId());
                    var returned = MainActivity.class.getDeclaredMethod("onActivityResult", int.class, int.class, Intent.class);
                    returned.setAccessible(true);
                    returned.invoke(bActivity, 310, Activity.RESULT_OK, new Intent().setData(android.net.Uri.parse("content://fixture/tree/A"))
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
                } catch (Exception e) { throw new AssertionError(e); }
            });
            check(b.initialTreeUri() == null, "stale_picker_cannot_change_B");
            check(!a.unlock("Public A retirement fixture".toCharArray()), "A_cannot_reopen");
            command(() -> b.create("Public B retirement fixture".toCharArray())); await(() -> b.snapshot().state() == AndroidVaultController.State.OPEN);
            check(InactivityLock.TIMEOUT_MILLIS == 15 * 60 * 1000L, "fixed_15_minute_policy");
            runOnMainSync(() -> {
                b.foregroundChanged(false);
                try {
                    var timer = field(b, "inactivity"); var deadline = timer.getClass().getDeclaredField("deadline");
                    deadline.setAccessible(true); deadline.setLong(timer, SystemClock.elapsedRealtime() - 1);
                } catch (Exception e) { throw new AssertionError(e); }
                b.foregroundChanged(true);
            });
            await(() -> b.snapshot().state() == AndroidVaultController.State.LOCKED);
            check(b.snapshot().view() == null, "background_expiry_closes_B");
            command(() -> b.unlock("Public A retirement fixture".toCharArray()));
            await(() -> b.snapshot().error() == AndroidVaultController.Error.AUTHENTICATION_FAILED);
            check(b.snapshot().state() == AndroidVaultController.State.LOCKED, "B_independent_password_required");
            runOnMainSync(authority::retire); await(() -> authority.snapshot() == null);
            result.putString("stream", "CONTROLLER_RETIREMENT_PASS checks=" + checks + "\n"); code = -1;
        } catch (Throwable failure) { result.putString("stream", "CONTROLLER_RETIREMENT_FAIL step=" + step + " checks=" + checks + "\n"); }
        finally { release.countDown(); finish(code, result); }
    }
}
