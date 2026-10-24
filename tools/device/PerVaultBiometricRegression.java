package org.totipo.android;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.widget.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Disposable-package-only. Real Application selection, production Activity, Keystore
 * and CryptoObject prompt; every successful biometric operation requires a human.
 * No prompt success injection, second admission authority or owner-installation access.
 */
public final class PerVaultBiometricRegression extends Instrumentation {
    private Bundle arguments;
    private TotipoApplication app;
    private VaultRegistry registry;
    private AndroidVaultController controller;
    private final AtomicReference<MainActivity> activity = new AtomicReference<>();
    private int checks;
    private String step = "startup";
    private Object field(Object value, String name) {
        try { var f = value.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(value); }
        catch (Exception failure) { throw new AssertionError(failure); }
    }
    private void say(String value) { var b = new Bundle(); b.putString("stream", "MV4 " + value + "\n"); sendStatus(0, b); }
    private void check(boolean value, String label) {
        step = label; if (!value) throw new AssertionError(label); checks++; say(label + " PASS");
    }
    private void await(BooleanSupplier condition) {
        long end = SystemClock.uptimeMillis() + 180_000;
        while (!condition.getAsBoolean()) {
            if (SystemClock.uptimeMillis() >= end) throw new AssertionError("timeout");
            SystemClock.sleep(20); // Positive completion polling; no negative time-based assertions.
        }
        waitForIdleSync();
    }
    private boolean idle() {
        synchronized (controller) {
            for (String name : List.of("operating", "viewQueued", "providerActive", "syncPending", "syncDrainQueued"))
                if ((boolean)field(controller, name)) return false;
            return true;
        }
    }
    private void state(AndroidVaultController.State state) { await(() -> controller.snapshot().state() == state && idle()); }
    private void command(BooleanSupplier action) {
        await(this::idle); runOnMainSync(() -> check(action.getAsBoolean(), "command_admitted")); await(this::idle);
    }
    private AndroidBiometricCredentials credentials(String id) {
        try { return new AndroidBiometricCredentials(getTargetContext(), registry, id); }
        catch (Exception failure) { throw new AssertionError(failure); }
    }
    private BiometricUnlock biometric() { return (BiometricUnlock)field(activity.get(), "biometric"); }
    private void select(String id) {
        if (activity.get() == null) { controller = app.vaultController(); launch(); }
        if (app.selection().snapshot() != null) {
            if (biometric() != null && (boolean)field(biometric(), "active")) {
                // A platform prompt is modal. Exercise Application-initiated retirement
                // while it is outstanding; normal nonmodal switches use the real menu.
                runOnMainSync(app::changeVault);
            } else {
            tap("More options");
            await(() -> getUiAutomation().getRootInActiveWindow() != null && !getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText("Change Vault").isEmpty());
            var change = getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText("Change Vault").get(0);
            touchNative(change);
            }
            await(() -> app.selection().snapshot() == null && field(activity.get(), "controller") == null);
        }
        VaultRegistry.Entry entry;
        try { entry = registry.entry(id); } catch (Exception failure) { throw new AssertionError(failure); }
        if (entry.state() == VaultRegistry.State.ESTABLISHED)
            tap("Open " + app.localName(entry) + " · " + app.entrySummary(entry));
        else {
            tap("Manage " + app.localName(entry));
            await(() -> getUiAutomation().getRootInActiveWindow() != null && !getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText("Resume Create").isEmpty());
            touchNative(getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText("Resume Create").get(0));
        }
        controller = app.vaultController();
        await(() -> controller != null && controller.snapshot().state() != AndroidVaultController.State.STARTING && idle()
                && field(activity.get(), "controller") == controller);
    }
    private void launch() {
        startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        await(() -> activity.get() != null && field(activity.get(), "controller") == controller);
    }
    private char[] password(String label) { return (label.equals("B") ? "Disposable MV4 B" : "Disposable MV4 A and C").toCharArray(); }
    private android.view.View control(android.view.View root, String text) {
        if (root instanceof TextView label && text.contentEquals(label.getText())) return root;
        if (text.contentEquals(root.getContentDescription() == null ? "" : root.getContentDescription())) return root;
        if (root instanceof android.view.ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            var found = control(group.getChildAt(i), text); if (found != null) return found;
        }
        return null;
    }
    private void tap(String text) {
        await(() -> {
            var ready = new java.util.concurrent.atomic.AtomicBoolean();
            runOnMainSync(() -> {
                var view = control(activity.get().getWindow().getDecorView(), text);
                ready.set(view != null && view.isShown() && view.isEnabled());
            });
            return ready.get();
        });
        runOnMainSync(() -> {
            var view = control(activity.get().getWindow().getDecorView(), text);
            check(view != null && view.isEnabled() && view.performClick(), "explicit_UI_action");
        }); waitForIdleSync();
    }
    private void touchNative(android.view.accessibility.AccessibilityNodeInfo node) {
        try { getUiAutomation().waitForIdle(300, 5000); }
        catch (java.util.concurrent.TimeoutException busy) { throw new AssertionError("Native UI did not settle"); }
        check(node.refresh(), "native_node_current");
        android.graphics.Rect bounds = new android.graphics.Rect(); node.getBoundsInScreen(bounds);
        check(!bounds.isEmpty() && node.isEnabled(), "native_control_available");
        long down = SystemClock.uptimeMillis();
        sendPointerSync(android.view.MotionEvent.obtain(down, down, android.view.MotionEvent.ACTION_DOWN, bounds.exactCenterX(), bounds.exactCenterY(), 0));
        sendPointerSync(android.view.MotionEvent.obtain(down, SystemClock.uptimeMillis(), android.view.MotionEvent.ACTION_UP, bounds.exactCenterX(), bounds.exactCenterY(), 0));
    }
    private void overflow(String label) {
        tap("More options");
        await(() -> getUiAutomation().getRootInActiveWindow() != null && !getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText(label).isEmpty());
        var node = getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText(label).get(0);
        touchNative(node); waitForIdleSync();
    }
    private void passwordOpen(String label, boolean enroll) {
        step = "password_" + label;
        runOnMainSync(() -> {
            biometric().close();
            var input = (EditText)field(activity.get(), "password");
            char[] chars = password(label);
            try { input.setText(chars, 0, chars.length); } finally { Arrays.fill(chars, '\0'); }
            var option = (CheckBox)field(activity.get(), "enableBiometric");
            if (enroll) check(option != null, "explicit_enrollment_option_" + label);
            if (option != null) option.setChecked(enroll);
            ((Button)field(activity.get(), "action")).performClick();
        });
        state(AndroidVaultController.State.OPEN);
        if (enroll) {
            say("HUMAN authenticate enrollment for " + label);
            await(() -> credentials(controller.localId()).configured());
            check(credentials(controller.localId()).configured(), "durable_enrollment_" + label);
        }
    }
    private void lock() { command(controller::lock); state(AndroidVaultController.State.LOCKED); }
    private void promptUnlock() {
        if (!(boolean)field(biometric(), "active")) tap("Use biometrics");
    }
    private void biometricOpen(String label) {
        step = "biometric_" + label; say("HUMAN authenticate unlock for " + label);
        promptUnlock();
        state(AndroidVaultController.State.OPEN);
        check(controller.snapshot().view() != null, "authenticated_presentation_" + label);
    }
    private void create(String label) {
        state(AndroidVaultController.State.NO_LOCAL_VAULT);
        command(() -> controller.create(password(label))); state(AndroidVaultController.State.OPEN);
        lock(); passwordOpen(label, true); lock(); biometricOpen(label);
    }
    private void first() throws Exception {
        check(registry.entries().size() == 1, "fresh_single_entry");
        controller = app.vaultController(); launch();
        if (controller == null) {
            tap("Create Local Vault"); await(() -> app.vaultController() != null); controller = app.vaultController(); launch();
        }
        String a = controller.localId(); create("A");
        String va = ((org.totipo.android.reconcile.ForegroundVaultCoordinator)field(controller, "vault")).sessionVaultId().hex();
        lock(); runOnMainSync(() -> biometric().close()); passwordOpen("A", false);
        check(controller.snapshot().state() == AndroidVaultController.State.OPEN, "password_fallback_A");
        var b = registry.reserve("MV4 B").id(); select(b); create("B");
        String vb = ((org.totipo.android.reconcile.ForegroundVaultCoordinator)field(controller, "vault")).sessionVaultId().hex();
        check(!va.equals(vb), "independent_protocol_identities");
        check(!BiometricRecord.alias(a).equals(BiometricRecord.alias(b)), "independent_aliases");
        select(a); biometricOpen("A_after_B");
        overflow("Disable biometric unlock");
        await(() -> !credentials(a).configured());
        check(!credentials(a).configured(), "disable_selected_A");
        check(!credentials(a).configured() && credentials(b).configured(), "disable_A_preserves_B");
        lock(); passwordOpen("A", true);
        // Cancel a replacement prompt: preserve old credential bytes and actual key.
        byte[] prior = Files.readAllBytes(new BiometricCredentialStorage(registry, a).record);
        lock(); runOnMainSync(() -> biometric().close());
        var cancelledReplacement = new java.util.concurrent.atomic.AtomicBoolean();
        runOnMainSync(() -> controller.unlockAndEnroll(password("A"), value -> {
            biometric().enroll(value); biometric().close(); cancelledReplacement.set(true);
        })); await(cancelledReplacement::get); state(AndroidVaultController.State.OPEN);
        check(Arrays.equals(prior, Files.readAllBytes(new BiometricCredentialStorage(registry, a).record)), "cancel_replacement_preserves_record");
        lock(); promptUnlock();
        // Retire A with its real platform prompt outstanding. Actual late callback
        // delivery is platform-controlled; deterministic JVM tests force that ordering.
        select(b); biometricOpen("B_after_A_prompt_retirement");
        var c = registry.reserve("MV4 C same password as A").id(); select(c); create("C");
        check(!va.equals(((org.totipo.android.reconcile.ForegroundVaultCoordinator)field(controller, "vault"))
                .sessionVaultId().hex()), "equal_password_vaults_have_distinct_ids");
        select(a); biometricOpen("A_equal_password_peer");
        // Transplant only disposable ciphertext. Parsing rejects C's entry identity.
        var sa = new BiometricCredentialStorage(registry, a); var sc = new BiometricCredentialStorage(registry, c);
        byte[] original = Files.readAllBytes(sa.record); Files.write(sa.record, Files.readAllBytes(sc.record));
        check(!credentials(a).configured(), "cross_entry_swap_refused");
        Files.write(sa.record, original); check(credentials(a).configured(), "original_restored");
        // Fault injection is confined to this disposable package's selected A metadata/key.
        Files.delete(sa.record); check(!credentials(a).configured(), "missing_record_password_fallback");
        lock(); passwordOpen("A", true);
        byte[] damaged = Files.readAllBytes(sa.record); damaged[damaged.length - 1] ^= 1; Files.write(sa.record, damaged);
        lock(); say("HUMAN authenticate A's intentionally corrupt credential; unlock must fail closed"); promptUnlock();
        await(() -> !biometric().message().isEmpty());
        check(controller.snapshot().state() == AndroidVaultController.State.LOCKED, "bad_tag_never_exposes_open");
        passwordOpen("A", false); check(credentials(c).configured(), "corruption_A_preserves_C");
        var keys = java.security.KeyStore.getInstance("AndroidKeyStore"); keys.load(null);
        keys.deleteEntry(BiometricRecord.alias(a)); check(!credentials(a).configured(), "missing_key_detected");
        runOnMainSync(() -> check(biometric().disable(), "explicit_missing_key_cleanup"));
        lock(); passwordOpen("A", true);
        Files.write(sa.pending, new byte[0]); check(!credentials(a).configured(), "interrupted_replacement_blocks_use");
        lock(); passwordOpen("A", false);
        runOnMainSync(() -> check(biometric().disable(), "interrupted_replacement_explicit_cleanup"));
        check(credentials(b).configured() && credentials(c).configured(), "interrupted_A_cleanup_preserves_B_C");
        lock(); passwordOpen("A", true);
        select(c); biometricOpen("C_equal_password_peer");
        MainActivity old = activity.get(); runOnMainSync(old::recreate); await(() -> activity.get() != old);
        check(field(activity.get(), "controller") == controller, "rotation_preserves_authenticated_activation");
        runOnMainSync(() -> controller.foregroundChanged(false));
        runOnMainSync(() -> controller.foregroundChanged(true));
        check(controller.snapshot().state() == AndroidVaultController.State.OPEN, "short_background_preserves_session");
        lock(); promptUnlock(); MainActivity lockedActivity = activity.get(); var oldPrompt = biometric();
        runOnMainSync(lockedActivity::recreate); await(() -> activity.get() != lockedActivity);
        check(!(boolean)field(oldPrompt, "active") && field(oldPrompt, "password") == null, "rotation_retires_old_prompt_authority");
        biometricOpen("C_fresh_prompt_after_locked_rotation");
        lock(); promptUnlock(); var backgroundPrompt = biometric();
        runOnMainSync(() -> activity.get().moveTaskToBack(true));
        await(() -> !(boolean)field(activity.get(), "resumed"));
        check(!(boolean)field(backgroundPrompt, "active"), "background_cancels_prompt");
        launch(); biometricOpen("C_fresh_prompt_after_background");
        lock(); runOnMainSync(() -> biometric().close());
    }
    private void restart() {
        check(registry.entries().size() == 3, "three_entries_restored");
        check(app.vaultController() == null, "multi_entry_startup_requires_explicit_selection");
        for (var entry : registry.entries()) {
            check(credentials(entry.id()).configured(), "credential_restored_" + entry.label());
            select(entry.id()); biometricOpen("restart_" + entry.label());
            lock(); runOnMainSync(() -> biometric().close());
        }
    }
    private void synchronizedContext() throws Exception {
        var entry = registry.entries().stream().filter(e -> "MV4 B".equals(e.label())).findFirst().orElseThrow();
        select(entry.id()); biometricOpen("B_before_attachment");
        byte[] prior = Files.readAllBytes(new BiometricCredentialStorage(registry, entry.id()).record);
        String uri = arguments.getString("folder");
        var grant = PerVaultSyncRegression.GrantActivity.class;
        PerVaultSyncRegression.GrantActivity.returned = false;
        PerVaultSyncRegression.GrantActivity.selected = null;
        startActivitySync(new Intent(getTargetContext(), grant).putExtra("initial",
                android.provider.DocumentsContract.buildDocumentUriUsingTree(android.net.Uri.parse(uri),
                        android.provider.DocumentsContract.getTreeDocumentId(android.net.Uri.parse(uri))).toString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        say("HUMAN choose and authorize ONLY the fresh disposable folder " + uri);
        await(() -> PerVaultSyncRegression.GrantActivity.returned);
        check(android.net.Uri.parse(uri).equals(PerVaultSyncRegression.GrantActivity.selected), "exact_disposable_sync_folder");
        command(() -> controller.chooseSyncFolder(false, uri, PerVaultSyncRegression.GrantActivity.flags));
        overflow("Manage sync folder"); tap("Initialize sync folder"); await(this::idle); tap("Back");
        tap("Sync"); await(this::idle);
        check(controller.dailySyncStatus().isEmpty(), "explicit_sync_UI_quiet_success");
        for (var other : registry.entries()) if (!other.id().equals(entry.id()))
            check(new org.totipo.android.sync.AndroidSyncFolderPort(getTargetContext(), other.id()).load() == null, "inactive_vault_stays_local_only");
        check(Arrays.equals(prior, Files.readAllBytes(new BiometricCredentialStorage(registry, entry.id()).record)), "attach_preserves_credential");
        lock(); biometricOpen("B_synchronized");
        check(controller.initialTreeUri().equals(uri), "biometric_open_preserves_binding");
        overflow("Manage sync folder"); tap("Disconnect sync folder"); await(this::idle); tap("Back");
        check(controller.initialTreeUri() == null, "disconnect_UI_returns_local_only");
        check(Arrays.equals(prior, Files.readAllBytes(new BiometricCredentialStorage(registry, entry.id()).record)), "disconnect_preserves_credential");
        lock(); biometricOpen("B_local_again");
        lock(); runOnMainSync(() -> biometric().close());
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); arguments = args; start(); }
    @Override public void onStart() {
        var result = new Bundle(); int code = 0;
        try {
            check(getTargetContext().getPackageName().equals("org.totipo.securityqualification"), "disposable_package_only");
            say("HUMAN unlock the test device if needed");
            await(() -> !getTargetContext().getSystemService(KeyguardManager.class).isKeyguardLocked());
            check(!getTargetContext().getSystemService(KeyguardManager.class).isKeyguardLocked(), "device_unlocked");
            runOnMainSync(() -> {});
            app = (TotipoApplication)getTargetContext().getApplicationContext(); registry = (VaultRegistry)field(app, "registry");
            app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                public void onActivityCreated(Activity value, Bundle saved) { if (value instanceof MainActivity main) activity.set(main); }
                public void onActivityResumed(Activity value) { value.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); }
                public void onActivityStarted(Activity value) {} public void onActivityStopped(Activity value) {}
                public void onActivityPaused(Activity value) {} public void onActivitySaveInstanceState(Activity value, Bundle saved) {}
                public void onActivityDestroyed(Activity value) {}
            });
            if ("mv4-restart".equals(arguments.getString("phase"))) restart();
            else if ("mv4-sync".equals(arguments.getString("phase"))) synchronizedContext();
            else first();
            result.putString("stream", "PER_VAULT_BIOMETRIC_PASS phase=" + arguments.getString("phase") + " checks=" + checks + "\n"); code = -1;
        } catch (Throwable failure) {
            result.putString("stream", "PER_VAULT_BIOMETRIC_FAIL step=" + step + " type=" + failure.getClass().getSimpleName() + " location="
                    + Arrays.toString(Arrays.copyOf(failure.getStackTrace(), Math.min(4, failure.getStackTrace().length))) + "\n");
        } finally { if (activity.get() != null) runOnMainSync(() -> activity.get().finish()); finish(code, result); }
    }
}
