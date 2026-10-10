package org.totipo.android;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.widget.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.totipo.*;
import org.totipo.android.reconcile.ForegroundVaultCoordinator;

/** Test-package-only deadline override. Never compiled into a production APK.
 * Uses final production DEX, disposable no-backup vault, and real framework biometric crypto. */
public final class SecurityRegression extends Instrumentation {
    private Bundle args;
    private AndroidVaultController controller;
    private MainActivity activity;
    private TokenLifecycleRegression.FixturePort port;
    private int checks;
    private String step = "startup";
    private Object field(Object value, String name) {
        try { var f = value.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(value); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    private void check(boolean value, String label) {
        step = label; if (!value) throw new AssertionError(label); checks++;
        status(label + " PASS");
    }
    private void status(String message) { Bundle b = new Bundle(); b.putString("stream", "SECURITY " + message + "\n"); sendStatus(0, b); }
    private boolean idle() { synchronized (controller) { return !(boolean)field(controller, "operating") && !(boolean)field(controller, "viewQueued") && !(boolean)field(controller, "providerActive") && !(boolean)field(controller, "revealing") && !(boolean)field(controller, "syncPending") && !(boolean)field(controller, "syncDrainQueued"); } }
    private void await(BooleanSupplier condition) {
        long end = SystemClock.uptimeMillis() + 150_000;
        while (SystemClock.uptimeMillis() < end) { if (condition.getAsBoolean()) return; SystemClock.sleep(20); }
        throw new AssertionError("timeout at " + step);
    }
    private void command(BooleanSupplier action) { await(this::idle); runOnMainSync(() -> check(action.getAsBoolean(), "command_admitted")); await(this::idle); waitForIdleSync(); }
    private char[] password() { return new char[]{'P','u','b','l','i','c',' ','s','m','o','k','e',' ','v','a','u','l','t'}; }
    private ForegroundVaultCoordinator vault() { return (ForegroundVaultCoordinator)field(controller, "vault"); }
    private void launch() {
        activity = (MainActivity)startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        runOnMainSync(() -> activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
        waitForIdleSync();
    }
    private void passwordOpen(boolean enroll) {
        runOnMainSync(() -> {
            controller.checkInactivity();
            EditText input = (EditText)field(activity, "password");
            char[] value = password(); try { input.setText(value, 0, value.length); } finally { Arrays.fill(value, '\0'); }
            var enable = (CheckBox)field(activity, "enableBiometric");
            if (enroll) check(enable != null, "explicit_enrollment_option");
            if (enable != null) enable.setChecked(enroll);
            ((Button)field(activity, "action")).performClick();
        });
        await(() -> controller.snapshot().state() == AndroidVaultController.State.OPEN && idle()); waitForIdleSync();
    }
    private void lock() {
        command(controller::lock);
        await(() -> controller.snapshot().state() == AndroidVaultController.State.LOCKED); waitForIdleSync();
    }
    private void expire() {
        // Only this detached qualification APK can alter the private deadline.
        runOnMainSync(() -> {
            try { var timer = field(controller, "inactivity"); var f = timer.getClass().getDeclaredField("deadline");
                f.setAccessible(true); f.setLong(timer, SystemClock.elapsedRealtime() - 1); controller.checkInactivity(); }
            catch (Exception e) { throw new AssertionError(e); }
        });
        await(() -> controller.snapshot().state() == AndroidVaultController.State.LOCKED && idle()); waitForIdleSync();
        check(controller.snapshot().view() == null && controller.snapshot().revealedCode() == null, "timeout_has_no_sensitive_snapshot");
        check(field(controller, "vault") == null, "timeout_retired_real_session_owner");
    }
    private void biometricOpen(String label) {
        int before = port.scans;
        step = label; status("HUMAN: authenticate the strong biometric prompt for " + label);
        await(() -> controller.snapshot().state() == AndroidVaultController.State.OPEN && idle()); waitForIdleSync();
        check(controller.snapshot().state() == AndroidVaultController.State.OPEN, label);
        await(() -> port.scans > before && idle());
        String sync = controller.dailySyncStatus();
        boolean conflict = controller.snapshot().view().tokens().stream().anyMatch(t -> t.conflict());
        check(conflict ? sync.contains("attention") || sync.equals("Changes not synced") : sync.isEmpty(),
                label + "_normal_automatic_sync");
    }
    private Path record() { return getTargetContext().getNoBackupFilesDir().toPath().resolve("biometric-password-v1"); }
    private void enrollment(String label) {
        step = label; status("HUMAN: authenticate the enrollment crypto prompt for " + label);
        await(() -> Files.exists(record())); waitForIdleSync(); check(Files.exists(record()), label);
    }
    private void modal(TokenId id, TokenChange.Kind kind) throws Exception {
        int before = vault().outboundSnapshot().size();
        runOnMainSync(() -> {
            try { var m = MainActivity.class.getDeclaredMethod("openTokenChange", TokenId.class, TokenChange.Kind.class); m.setAccessible(true); m.invoke(activity, id, kind); }
            catch (Exception e) { throw new AssertionError(e); }
        });
        AlertDialog dialog = (AlertDialog)field(activity, "tokenDialog"); check(dialog != null && dialog.isShowing(), kind + "_modal_visible");
        Button stale = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        expire(); check(!dialog.isShowing() && field(activity, "tokenDialog") == null, kind + "_modal_retired");
        runOnMainSync(stale::performClick); passwordOpen(false);
        check(vault().outboundSnapshot().size() == before, kind + "_late_confirmation_cannot_author");
    }
    private void lifecycle() throws Exception {
        command(() -> controller.addToken(new AddTokenRequest("Public security fixture", "public", TotpAlgorithm.SHA1, 6, 30, new char[]{'M','Y'})));
        await(() -> controller.snapshot().view().tokens().size() == 1);
        TokenId id = controller.snapshot().view().tokens().get(0).id();
        await(() -> {
            if (controller.snapshot().revealedCode() != null) return true;
            if (idle()) runOnMainSync(() -> controller.showCode(id));
            return false;
        }); check(controller.snapshot().revealedCode() != null, "reveal_visible");
        expire(); passwordOpen(false);
        runOnMainSync(() -> ((Button)field(activity, "add")).performClick());
        EditText secret = (EditText)field(activity, "secret");
        runOnMainSync(() -> secret.setText(new char[]{'M','Y'}, 0, 2)); expire();
        check(secret.length() == 0 && field(activity, "secret") == null, "add_draft_wiped_on_timeout"); passwordOpen(false);
        modal(id, TokenChange.Kind.EDIT); modal(id, TokenChange.Kind.DELETE);
        // Create actual concurrent Java-authored history; import through the normal controller.
        Path base = Files.createTempDirectory(getTargetContext().getCacheDir().toPath(), "security-branch-");
        Path remote = Files.createDirectory(base.resolve("remote")); Files.createDirectory(remote.resolve("objects-v1"));
        Files.write(remote.resolve("vault"), vault().snapshotVault());
        for (var object : vault().outboundSnapshot()) Files.write(remote.resolve("objects-v1").resolve(object.id().hex()), object.representation());
        char[] remotePassword = password();
        try (var session = ((OpenResult.Opened)Totipo.open(org.totipo.storage.nio.NioStoreComposition.coordinatedDelegate(remote, new org.totipo.storage.nio.NioDurability()), remotePassword)).session()) {
            await(() -> session.state().observation() instanceof ObservationProgress.Finished);
            try (var update = session.state().update(session.state().token(id).orElseThrow().alternatives().get(0))) {
                check(update.account("remote").save() instanceof SaveResult.Saved, "remote_branch_authored");
            }
        } finally { Arrays.fill(remotePassword, '\0'); }
        final TokenChange[] change = {null}; runOnMainSync(() -> change[0] = controller.beginTokenChange(id, TokenChange.Kind.EDIT));
        command(() -> controller.confirmTokenChange(change[0], "Public security fixture", "local", 0));
        var port = new TokenLifecycleRegression.FixturePort(Files.createDirectory(base.resolve("transport")));
        Files.write(port.root.resolve("vault"), Files.readAllBytes(remote.resolve("vault")));
        try (var paths = Files.list(remote.resolve("objects-v1"))) { for (Path path : paths.toList()) Files.copy(path, port.root.resolve("objects-v1").resolve(path.getFileName())); }
        command(() -> controller.sync(port.scan(port.tree.locator())));
        await(() -> controller.snapshot().view().tokens().get(0).conflict()); modal(id, TokenChange.Kind.RESOLVE);
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); this.args = args; start(); }
    @Override public void onStart() {
        Bundle output = new Bundle(); int result = 0;
        try {
            // Instrumentation.onStart can race Application.onCreate on a fresh process.
            waitForIdleSync();
            controller = ((TotipoApplication)getTargetContext().getApplicationContext()).vaultController();
            await(() -> controller.snapshot().state() != AndroidVaultController.State.STARTING && idle());
            // Replace the process controller only after its real initial discovery is idle.
            // Same private canonical owner, final production backend/time, isolated file transport.
            controller.shutdown(); await(() -> idle() && controller.snapshot().state() != AndroidVaultController.State.LOCKING);
            Path transport = getTargetContext().getNoBackupFilesDir().toPath().resolve("security-transport");
            Files.createDirectories(transport); port = new TokenLifecycleRegression.FixturePort(transport);
            Handler main = new Handler(Looper.getMainLooper());
            controller = new AndroidVaultController(LocalReplicaOwner.from(getTargetContext()), new AndroidVaultController.Dispatcher() {
                public void post(Runnable action) { main.post(action); }
                public Runnable after(long delay, Runnable action) { main.postDelayed(action, delay); return () -> main.removeCallbacks(action); }
                public void assertDispatchThread() { if (Looper.myLooper() != Looper.getMainLooper()) throw new AssertionError("main thread"); }
                public void assertWorkerThread() { if (Looper.myLooper() == Looper.getMainLooper()) throw new AssertionError("worker thread"); }
            }, new AndroidVaultController.Backend(), new TotpPresentation.Time() {
                public Instant wall() { return Instant.ofEpochSecond(59); }
                public long elapsedMillis() { return SystemClock.elapsedRealtime(); }
            }, null, new org.totipo.android.sync.SyncFolderBinding(port));
            var controllerField = TotipoApplication.class.getDeclaredField("vaultController"); controllerField.setAccessible(true);
            controllerField.set(getTargetContext().getApplicationContext(), controller);
            await(() -> controller.snapshot().state() != AndroidVaultController.State.STARTING && idle());
            check(InactivityLock.TIMEOUT_MILLIS == 900_000, "production_constant_exactly_fifteen_minutes");
            var credentials = new AndroidBiometricCredentials(getTargetContext()); check(credentials.available(), "framework_strong_biometric_available");
            if (!"restart".equals(args.getString("phase"))) {
                check(controller.snapshot().state() == AndroidVaultController.State.NO_LOCAL_VAULT, "disposable_vault_absent");
                command(() -> controller.create(password())); Files.write(port.root.resolve("vault"), vault().snapshotVault()); launch(); lifecycle();
                lock(); passwordOpen(true); enrollment("first_enrollment");
                lock(); biometricOpen("manual_lock_biometric_open");
                lock(); runOnMainSync(() -> ((BiometricUnlock)field(activity, "biometric")).close());
                check(controller.snapshot().state() == AndroidVaultController.State.LOCKED, "cancel_remains_locked"); passwordOpen(false);
                check(controller.snapshot().state() == AndroidVaultController.State.OPEN, "cancel_password_fallback");
                lock();
            } else {
                check(controller.snapshot().state() == AndroidVaultController.State.LOCKED, "process_restart_starts_locked");
                launch(); biometricOpen("process_restart_biometric_open");
                lock(); runOnMainSync(() -> ((BiometricUnlock)field(activity, "biometric")).close());
                check(controller.snapshot().state() == AndroidVaultController.State.LOCKED, "cancel_remains_locked");
                passwordOpen(false);
                check(controller.snapshot().state() == AndroidVaultController.State.OPEN, "cancel_password_fallback");
                var session = vault(); runOnMainSync(() -> activity.moveTaskToBack(true)); SystemClock.sleep(300);
                launch(); check(vault() == session, "short_background_retains_session");
                runOnMainSync(() -> activity.moveTaskToBack(true)); expire(); launch(); biometricOpen("background_timeout_biometric_open");
                runOnMainSync(() -> check(((BiometricUnlock)field(activity, "biometric")).disable(), "disable_succeeds"));
                check(!credentials.configured(), "disable_removes_record");
                check(!java.security.KeyStore.getInstance("AndroidKeyStore").getType().isEmpty(), "framework_keystore_present");
                lock(); check(field(activity, "enableBiometric") != null, "disabled_password_option_present"); passwordOpen(false);
                lock(); passwordOpen(true); enrollment("reenrollment");
                // Safe corruption of only the isolated test package's record.
                Files.write(record(), new byte[]{1, 2, 3}); lock();
                await(() -> !credentials.configured()); check(controller.snapshot().state() == AndroidVaultController.State.LOCKED, "corrupt_credential_fails_closed");
                waitForIdleSync(); passwordOpen(true); enrollment("corruption_recovery_enrollment");
                check(!controller.diagnostics().contains("Public smoke vault"), "diagnostics_omit_fixture_password");
                credentials.delete(); check(!credentials.configured(), "final_record_cleanup");
                var keys = java.security.KeyStore.getInstance("AndroidKeyStore"); keys.load(null);
                check(!keys.containsAlias(BiometricRecord.ALIAS), "delete_removes_key_ownership");
                lock();
            }
            output.putString("stream", "SECURITY_PASS phase=" + args.getString("phase", "first") + " checks=" + checks + "\n"); result = -1;
        } catch (Throwable failure) {
            output.putString("stream", "SECURITY_FAIL step=" + step + " type=" + failure.getClass().getSimpleName() + " location=" + Arrays.toString(Arrays.copyOf(failure.getStackTrace(), Math.min(3, failure.getStackTrace().length))) + "\n");
        } finally { if (activity != null) runOnMainSync(() -> activity.finish()); }
        finish(result, output);
    }
}
