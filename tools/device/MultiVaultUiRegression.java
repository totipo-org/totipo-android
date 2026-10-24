package org.totipo.android;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Real UI in the disposable security package only. No alternate selection/session owner.
 * first -> restart -> recover use the same private fixture. Human biometric prompts are
 * optional separate phases on that fixture; authorization is never simulated. */
public final class MultiVaultUiRegression extends Instrumentation {
    private Bundle args;
    private TotipoApplication app;
    private VaultRegistry registry;
    private final AtomicReference<MainActivity> activity = new AtomicReference<>();
    private int checks;
    private String step = "startup";
    private Object field(Object value, String name) {
        try { var f = value.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(value); }
        catch (Exception failure) { throw new AssertionError(failure); }
    }
    private void check(boolean value, String label) { step = label; if (!value) throw new AssertionError(label); checks++; say(label + " PASS"); }
    private void say(String message) { var value = new Bundle(); value.putString("stream", "MV5 " + message + "\n"); sendStatus(0, value); }
    private void await(BooleanSupplier ready) {
        long end = SystemClock.uptimeMillis() + 180_000;
        while (!ready.getAsBoolean()) {
            if (SystemClock.uptimeMillis() >= end) throw new AssertionError("timeout at " + step);
            SystemClock.sleep(20); // Positive completion only; latches prove blocked retirement in its dedicated harness.
        }
        waitForIdleSync();
    }
    private View find(View root, String name) {
        if (root instanceof TextView text && (name.contentEquals(text.getText()) || text.getText().toString().startsWith(name + "\n"))) return root;
        if (name.contentEquals(root.getContentDescription() == null ? "" : root.getContentDescription())) return root;
        if (root instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            View found = find(group.getChildAt(i), name); if (found != null) return found;
        }
        return null;
    }
    private boolean shown(String name) { return find(activity.get().getWindow().getDecorView(), name) != null; }
    private void tap(String name) {
        step = "tap_control";
        runOnMainSync(() -> {
            View view = find(activity.get().getWindow().getDecorView(), name);
            check(view != null && view.isEnabled() && view.performClick(), "visible_control_admitted");
        }); waitForIdleSync();
    }
    private AccessibilityNodeInfo accessible(String text) {
        var root = getUiAutomation().getRootInActiveWindow(); if (root == null) return null;
        for (var node : root.findAccessibilityNodeInfosByText(text)) if (text.equalsIgnoreCase(node.getText() == null ? "" : node.getText().toString())) return node;
        return null;
    }
    private void nativeTap(String text) {
        await(() -> accessible(text) != null);
        try { getUiAutomation().waitForIdle(300, 5000); }
        catch (java.util.concurrent.TimeoutException busy) { throw new AssertionError("Native UI did not settle"); }
        var node = accessible(text); check(node != null && node.refresh(), "native_node_current");
        android.graphics.Rect bounds = new android.graphics.Rect(); node.getBoundsInScreen(bounds);
        check(!bounds.isEmpty() && node.isEnabled(), "native_control_available");
        say("native_bounds=" + bounds + " class=" + node.getClassName());
        long down = SystemClock.uptimeMillis();
        sendPointerSync(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, bounds.exactCenterX(), bounds.exactCenterY(), 0));
        sendPointerSync(MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, bounds.exactCenterX(), bounds.exactCenterY(), 0));
        waitForIdleSync();
    }
    private void overflow(String name) { tap("More options"); nativeTap(name); }
    private AndroidVaultController controller() { return app.vaultController(); }
    private void bound() {
        await(() -> activity.get() != null && field(activity.get(), "controller") == controller()
                && !(boolean)field(activity.get(), "replacing"));
    }
    private void state(AndroidVaultController.State state) {
        step = "waiting_state_" + state; say(step);
        await(() -> controller() != null && controller().snapshot().state() == state); bound();
    }
    private void launch() {
        startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        bound();
    }
    private void selector() { await(() -> controller() == null); bound(); check(shown("Vaults") && shown("Your vaults") && shown("Add vault"), "selector_visible_without_controller"); }
    private void change() {
        var old = controller(); overflow("Change Vault"); selector();
        check(old.retirement() == AndroidVaultController.Retirement.SUCCESS, "UI_waited_for_complete_retirement");
        check(old.snapshot().view() == null, "old_sensitive_snapshot_retired");
    }
    private void password(String value, boolean create, boolean enroll) {
        runOnMainSync(() -> {
            ((EditText)field(activity.get(), "password")).setText(value);
            if (create) ((EditText)field(activity.get(), "confirmation")).setText(value);
            var option = (CheckBox)field(activity.get(), "enableBiometric");
            if (enroll) check(option != null, "enrollment_available");
            if (option != null) option.setChecked(enroll);
            check(((Button)field(activity.get(), "action")).performClick(), "password_UI_submitted");
        });
    }
    private void add(String label) {
        tap("Add token");
        runOnMainSync(() -> {
            ((EditText)field(activity.get(), "issuer")).setText("Public MV5 fixture");
            ((EditText)field(activity.get(), "account")).setText(label);
            ((EditText)field(activity.get(), "secret")).setText("MY");
        }); tap("Add");
        await(() -> controller().snapshot().view() != null && controller().snapshot().view().tokens().size() == 1);
    }
    private void token(String expected) {
        await(() -> controller().snapshot().view() != null && controller().snapshot().view().tokens().size() == 1);
        check(controller().snapshot().view().tokens().get(0).alternatives().get(0).account().equals(expected), "independent_token_contents");
    }
    private Map<String, String> inventory(Path root) throws Exception {
        var result = new TreeMap<String, String>();
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).toList())
                result.put(root.relativize(file).toString(), java.util.HexFormat.of().formatHex(
                        java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));
        }
        return result;
    }
    private void selectEntry(int index) {
        var entry = registry.entries().get(index);
        tap("Open " + app.localName(entry) + " · " + app.entrySummary(entry));
    }
    private void entryAction(int index, String action) {
        tap("Manage " + app.localName(registry.entries().get(index))); nativeTap(action);
    }
    private void first() throws Exception {
        check(controller() == null && registry.entries().size() == 1, "fresh_setup_has_no_activation");
        String initial = registry.entries().get(0).id(); launch();
        check(shown("Vaults") && shown("Create Local Vault") && shown("Join Existing Vault"), "initial_setup_actions");
        tap("Create Local Vault"); state(AndroidVaultController.State.NO_LOCAL_VAULT);
        check(controller().localId().equals(initial) && registry.entries().size() == 1, "initial_reservation_reused");
        password("Disposable MV5 A", true, false); state(AndroidVaultController.State.OPEN); add("A");
        check(controller().initialTreeUri() == null, "local_create_needs_no_provider");
        overflow("Lock"); state(AndroidVaultController.State.LOCKED);
        password("Disposable MV5 A", false, false); state(AndroidVaultController.State.OPEN); token("A"); change();
        tap("Create Local Vault"); state(AndroidVaultController.State.NO_LOCAL_VAULT);
        password("Disposable MV5 B", true, false); state(AndroidVaultController.State.OPEN); add("B");
        check(registry.entries().size() == 2, "two_independent_entries");
        byte[] a = Files.readAllBytes(registry.root(initial).resolve("vault"));
        byte[] b = Files.readAllBytes(registry.root(controller().localId()).resolve("vault"));
        check(!Arrays.equals(a, b), "independent_canonical_identities");
        check(controller().initialTreeUri() == null, "B_remains_local_only");
        change(); check(shown(app.localName(registry.entries().get(1)) + " · Last selected"), "last_selection_highlighted");
        tap("Join Existing Vault"); state(AndroidVaultController.State.NO_LOCAL_VAULT);
        check(shown("Join existing vault") && !shown("Create Vault"), "join_cannot_fall_through_to_create");
        tap("Cancel setup"); selector();
        check(registry.entries().size() == 3 && registry.entries().get(2).state() == VaultRegistry.State.PENDING, "cancel_preserves_reserved_join");
        selectEntry(1); state(AndroidVaultController.State.LOCKED); change();
    }
    private void restart() throws Exception {
        check(controller() == null, "multi_vault_process_restart_requires_selection"); launch();
        check(shown(app.localName(registry.entries().get(1)) + " · Last selected"), "restart_highlights_last_without_activation");
        await(() -> activity.get().hasWindowFocus());
        MainActivity selectionScreen = activity.get();
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); await(selectionScreen::isFinishing);
        check(controller() == null, "back_does_not_activate_last_selection"); launch();
        var ingress = (OtpAuthEnrollmentActivity)startActivitySync(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("otpauth://totp/Public?secret=MY"))
                .setClass(getTargetContext(), OtpAuthEnrollmentActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        check(field(ingress, "draft") == null && controller() == null, "selector_ingress_cannot_select_or_retain_secret");
        runOnMainSync(ingress::finish); waitForIdleSync();
        selectEntry(0); state(AndroidVaultController.State.LOCKED);
        password("Disposable MV5 B", false, false); await(() -> controller().snapshot().error() == AndroidVaultController.Error.AUTHENTICATION_FAILED); state(AndroidVaultController.State.LOCKED);
        password("Disposable MV5 A", false, false); state(AndroidVaultController.State.OPEN); token("A");
        var selected = controller(); var old = activity.get(); runOnMainSync(old::recreate); await(() -> activity.get() != old); bound();
        check(controller() == selected && controller().snapshot().state() == AndroidVaultController.State.OPEN, "rotation_preserves_authenticated_selection");
        change(); selectEntry(1); state(AndroidVaultController.State.LOCKED);
        password("Disposable MV5 A", false, false); await(() -> controller().snapshot().error() == AndroidVaultController.Error.AUTHENTICATION_FAILED); state(AndroidVaultController.State.LOCKED);
        check(selected.retirement() == AndroidVaultController.Retirement.SUCCESS, "failed_B_auth_never_reopens_A");
        password("Disposable MV5 B", false, false); state(AndroidVaultController.State.OPEN); token("B"); change();
        entryAction(0, "Rename");
        await(() -> accessible("Save") != null);
        try { getUiAutomation().waitForIdle(300, 5000); }
        catch (java.util.concurrent.TimeoutException busy) { throw new AssertionError("Rename dialog did not settle"); }
        var root = getUiAutomation().getRootInActiveWindow();
        var pending = new ArrayDeque<AccessibilityNodeInfo>(); pending.add(root); boolean renamed = false;
        while (!pending.isEmpty()) {
            var node = pending.remove();
            if ("android.widget.EditText".contentEquals(node.getClassName())) {
                var value = new Bundle(); value.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "Personal A");
                renamed = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, value); break;
            }
            for (int i = 0; i < node.getChildCount(); i++) if (node.getChild(i) != null) pending.add(node.getChild(i));
        }
        check(renamed, "native_rename_field"); nativeTap("Save"); check(shown("Personal A"), "rename_persisted_and_presented");
        Path canonical = registry.root(registry.entries().get(0).id());
        var beforeForget = inventory(canonical);
        // Wrong-kind blocker is confined to an absent convenience-metadata path in
        // this disposable fixture. It must never become a false cleanup success.
        Path blocker = canonical.getParent().resolve("biometric-v2.disabled");
        check(!Files.exists(blocker, LinkOption.NOFOLLOW_LINKS), "disposable_blocker_path_unused");
        Files.createDirectory(blocker);
        entryAction(0, "Forget…"); nativeTap("Forget"); await(() -> !app.managementBusy());
        check(registry.entries().get(0).state() == VaultRegistry.State.FORGETTING && shown("Recovery"), "incomplete_credential_cleanup_is_not_success");
        check(beforeForget.equals(inventory(canonical)), "failed_forget_preserves_all_canonical_bytes");
        Files.delete(blocker); // Only the known empty synthetic directory, never canonical data.
        entryAction(0, "Retry Forget cleanup"); await(() -> !app.managementBusy());
        check(registry.entries().get(0).state() == VaultRegistry.State.DETACHED, "forget_durably_detached");
        check(beforeForget.equals(inventory(canonical)), "forget_preserves_all_canonical_bytes");
        check(!shown("Open Personal A · Local only") && shown("Recovery"), "forgotten_entry_separate_from_normal_selection");
    }
    private void recover() throws Exception {
        launch();
        check(registry.entries().get(0).state() == VaultRegistry.State.DETACHED, "cold_restart_does_not_resurrect_forgotten");
        if (controller() != null) change();
        entryAction(0, "Recover"); state(AndroidVaultController.State.LOCKED);
        check(registry.entries().get(0).state() == VaultRegistry.State.DETACHED, "recovery_selection_is_not_authentication");
        password("Disposable MV5 A", false, false); state(AndroidVaultController.State.OPEN); token("A");
        check(registry.entries().get(0).state() == VaultRegistry.State.ESTABLISHED, "authenticated_recovery_reregisters");
        change();
        // Safely simulate an interrupted unused allocation: these directories contain no vault data.
        var pending = registry.entries().get(2); Path root = registry.root(pending.id());
        try (var children = Files.list(root)) { check(!pending.creationAttempted() && children.findAny().isEmpty(), "unused_join_reservation"); }
        Files.delete(root); Files.delete(root.getParent());
        runOnMainSync(() -> app.selection().presentationChanged());
        entryAction(2, "Recover unused allocation"); check(Files.isDirectory(root), "explicit_allocation_recovery_UI");
        // Missing established storage is retained, never allocated by selector entry actions.
        var a = registry.entries().get(0); Path canonical = registry.root(a.id()), held = canonical.resolveSibling("held-store");
        Files.move(canonical, held);
        try {
            runOnMainSync(() -> app.selection().presentationChanged());
            selectEntry(0); check(controller() == null && !Files.exists(canonical), "missing_root_refuses_activation_and_recreation");
        } finally { Files.move(held, canonical); }
    }
    private void menuCheck() {
        launch(); state(AndroidVaultController.State.LOCKED);
        password("Disposable MV5 A", false, false); state(AndroidVaultController.State.OPEN);
        overflow("Lock");
        say("after_lock_touch=" + (controller() == null ? "NONE" : controller().snapshot().state()));
        state(AndroidVaultController.State.LOCKED);
    }
    private void layoutCheck() {
        launch();
        if (controller() != null) change();
        check(controller() == null, "layout_check_has_no_session");
        await(() -> find(activity.get().getWindow().getDecorView(), "Create Local Vault") != null
                && find(activity.get().getWindow().getDecorView(), "Create Local Vault").getHeight() > 0);
        runOnMainSync(() -> {
            float density = activity.get().getResources().getDisplayMetrics().density;
            for (String title : List.of("Create Local Vault", "Join Existing Vault")) {
                var control = find(activity.get().getWindow().getDecorView(), title);
                check(control instanceof Button && control.getHeight() >= (int)(48 * density), "native_touch_target");
                var button = (Button)control;
                check(button.getLayout() != null && button.getWidth() > 0 && button.isFocusable(), "scalable_focusable_action");
                for (int line = 0; line < button.getLayout().getLineCount(); line++)
                    check(button.getLayout().getEllipsisCount(line) == 0, "action_text_not_ellipsized");
            }
            say("font_scale=" + activity.get().getResources().getConfiguration().fontScale);
        });
    }
    /** Test-only inter-phase waiting window. Owns no controller, session or credential.
     * It preserves the existing KEEP_SCREEN_ON qualification policy without changing
     * the owner's global stay-awake, keyguard or security settings. */
    public static final class IdleActivity extends Activity {
        @Override public void onCreate(Bundle state) {
            super.onCreate(state);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_SECURE);
            TextView text = new TextView(this);
            text.setText("Disposable Totipo qualification is waiting. Device settings are unchanged.");
            text.setPadding(40, 80, 40, 40); setContentView(text);
        }
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); this.args = args; start(); }
    @Override public void onStart() {
        var result = new Bundle(); int code = 0;
        try {
            check(getTargetContext().getPackageName().equals("org.totipo.securityqualification"), "disposable_package_only");
            say("HUMAN unlock the test device if needed; device security settings are unchanged");
            await(() -> !getTargetContext().getSystemService(KeyguardManager.class).isKeyguardLocked());
            check(!getTargetContext().getSystemService(KeyguardManager.class).isKeyguardLocked(), "device_unlocked");
            runOnMainSync(() -> {});
            app = (TotipoApplication)getTargetContext().getApplicationContext(); registry = (VaultRegistry)field(app, "registry");
            app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                public void onActivityCreated(Activity value, Bundle saved) { if (value instanceof MainActivity main) activity.set(main); }
                public void onActivityResumed(Activity value) { value.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); }
                public void onActivityStarted(Activity value) {} public void onActivityStopped(Activity value) {}
                public void onActivityPaused(Activity value) {} public void onActivitySaveInstanceState(Activity value, Bundle state) {}
                public void onActivityDestroyed(Activity value) {}
            });
            switch (args.getString("phase")) {
                case "mv5-first" -> first(); case "mv5-restart" -> restart(); case "mv5-recover" -> recover(); case "mv5-layout" -> layoutCheck(); case "mv5-menu" -> menuCheck();
                default -> throw new AssertionError("unknown phase");
            }
            result.putString("stream", "MULTI_VAULT_UI_PASS phase=" + args.getString("phase") + " checks=" + checks + "\n"); code = -1;
        } catch (Throwable failure) {
            if (app != null) say("selection_state=" + (controller() == null ? "NONE" : controller().snapshot().state()));
            result.putString("stream", "MULTI_VAULT_UI_FAIL step=" + step + " type=" + failure.getClass().getSimpleName() + " location="
                    + Arrays.toString(Arrays.copyOf(failure.getStackTrace(), Math.min(4, failure.getStackTrace().length))) + "\n");
        } finally { if (activity.get() != null) runOnMainSync(() -> activity.get().finish()); finish(code, result); }
    }
}
