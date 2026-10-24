package org.totipo.android;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** A-UX2 journeys on the actual production Activity/Keystore in a fresh disposable UID.
 * All biometric success requires the human and the strong platform CryptoObject prompt. */
public final class DailyUseUxRegression extends Instrumentation {
    private Bundle args;
    private TotipoApplication app;
    private VaultRegistry registry;
    private final AtomicReference<MainActivity> activity = new AtomicReference<>();
    private int checks;
    private volatile boolean reviewed;
    private float reviewFontScale;
    private String step = "startup";
    private Object field(Object value, String name) {
        try { var f = value.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(value); }
        catch (Exception failure) { throw new AssertionError(failure); }
    }
    private void set(Object value, String name, Object data) {
        try { var f = value.getClass().getDeclaredField(name); f.setAccessible(true); f.set(value, data); }
        catch (Exception failure) { throw new AssertionError(failure); }
    }
    private void say(String value) { var b = new Bundle(); b.putString("stream", "UX2 " + value + "\n"); sendStatus(0, b); }
    private void check(boolean value, String label) { step = label; if (!value) throw new AssertionError(label); checks++; say(label + " PASS"); }
    private void await(BooleanSupplier ready) {
        long end = SystemClock.uptimeMillis() + 180_000;
        while (!ready.getAsBoolean()) {
            if (SystemClock.uptimeMillis() >= end) throw new AssertionError("timeout at " + step);
            SystemClock.sleep(20); // Positive completion only, never negative loop evidence.
        }
        waitForIdleSync();
    }
    private AndroidVaultController controller() { return app.vaultController(); }
    private BiometricUnlock biometric() { return (BiometricUnlock)field(activity.get(), "biometric"); }
    private void bound() {
        await(() -> activity.get() != null && field(activity.get(), "controller") == controller()
                && !(boolean)field(activity.get(), "replacing"));
    }
    private boolean idle() {
        var current = controller(); if (current == null) return true;
        synchronized (current) { return !(boolean)field(current, "operating") && !(boolean)field(current, "viewQueued"); }
    }
    private void state(AndroidVaultController.State value) {
        step = "wait_state_" + value; await(() -> controller() != null && controller().snapshot().state() == value && idle()); bound();
    }
    private View find(View root, String name) {
        if (root instanceof TextView text && name.contentEquals(text.getText())) return root;
        if (name.contentEquals(root.getContentDescription() == null ? "" : root.getContentDescription())) return root;
        if (root instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            View found = find(group.getChildAt(i), name); if (found != null) return found;
        }
        return null;
    }
    private View find(String name) { return find(activity.get().getWindow().getDecorView(), name); }
    private View action(View root, String name) {
        if (root.isClickable() && ((root instanceof TextView text && name.contentEquals(text.getText()))
                || name.contentEquals(root.getContentDescription() == null ? "" : root.getContentDescription()))) return root;
        if (root instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            View found = action(group.getChildAt(i), name); if (found != null) return found;
        }
        return null;
    }
    private void tap(String name) {
        step = "tap_" + name; await(() -> {
            View found = action(activity.get().getWindow().getDecorView(), name); return found != null && found.isEnabled();
        });
        runOnMainSync(() -> {
            View view = action(activity.get().getWindow().getDecorView(), name);
            check(view.isEnabled() && view.performClick(), "control_admitted_" + name);
        });
        waitForIdleSync();
    }
    private AccessibilityNodeInfo node(String text) {
        var root = getUiAutomation().getRootInActiveWindow(); if (root == null) return null;
        for (var item : root.findAccessibilityNodeInfosByText(text)) if (text.equalsIgnoreCase(String.valueOf(item.getText()))) return item;
        return null;
    }
    private void nativeTap(String text) {
        step = "native_" + text; await(() -> node(text) != null);
        try { getUiAutomation().waitForIdle(300, 5000); }
        catch (java.util.concurrent.TimeoutException busy) { throw new AssertionError("Native menu did not settle"); }
        var item = node(text); check(item != null && item.refresh(), "native_node_current");
        android.graphics.Rect bounds = new android.graphics.Rect(); item.getBoundsInScreen(bounds);
        check(item.isEnabled() && !bounds.isEmpty(), "native_control_available");
        long down = SystemClock.uptimeMillis();
        check(getUiAutomation().injectInputEvent(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN,
                bounds.exactCenterX(), bounds.exactCenterY(), 0), true), "native_pointer_down");
        check(getUiAutomation().injectInputEvent(MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP,
                bounds.exactCenterX(), bounds.exactCenterY(), 0), true), "native_pointer_up");
        waitForIdleSync();
    }
    private void overflow(String text) { tap("More options"); nativeTap(text); }
    private void launch() {
        startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)); bound();
    }
    private void rotate() {
        var old = activity.get(); runOnMainSync(old::recreate); await(() -> activity.get() != old); bound();
        await(() -> activity.get().hasWindowFocus());
    }
    private void change() {
        var old = controller(); overflow("Change Vault"); await(() -> controller() == null); bound();
        check(old.retirement() == AndroidVaultController.Retirement.SUCCESS, "complete_retirement_before_selector");
        check(find("Vaults") != null && find("Your vaults") != null && find("Add vault") != null, "unified_sections");
        check(find("Manage Vaults") == null && find("Back to vault selection") == null, "no_redundant_navigation");
    }
    private void select(VaultRegistry.Entry entry) {
        tap("Open " + app.localName(entry) + " · " + app.entrySummary(entry)); state(AndroidVaultController.State.LOCKED);
    }
    private void name(String text) { runOnMainSync(() -> ((EditText)field(activity.get(), "vaultName")).setText(text)); }
    private void password(boolean create, boolean enroll) {
        runOnMainSync(() -> {
            ((EditText)field(activity.get(), "password")).setText("Disposable A-UX2 password");
            if (create) ((EditText)field(activity.get(), "confirmation")).setText("Disposable A-UX2 password");
            if (enroll) ((CheckBox)field(activity.get(), "enableBiometric")).setChecked(true);
            ((Button)field(activity.get(), "action")).performClick();
        }); state(AndroidVaultController.State.OPEN);
        if (enroll) {
            say("HUMAN authorize strong biometric ENROLLMENT now");
            await(() -> !((boolean)field(biometric(), "active")) && biometric().configured());
            check(biometric().configured(), "credential_enrolled");
        }
    }
    private void lock() { overflow("Lock"); state(AndroidVaultController.State.LOCKED); }
    private void autoPrompt() {
        step = "automatic_prompt"; await(() -> (boolean)field(biometric(), "active"));
        check((boolean)field(controller(), "automaticBiometricAttempted"), "controller_consumed_automatic_opportunity");
        check(controller().snapshot().state() == AndroidVaultController.State.LOCKED, "prompt_does_not_open_vault");
    }
    private void humanOpen(String context) {
        say("HUMAN authorize strong biometric UNLOCK for " + context + " now");
        state(AndroidVaultController.State.OPEN); check(controller().snapshot().view() != null, "authenticated_open_" + context);
    }
    private void first() {
        check(registry.entries().size() == 1, "single_first_phase_reservation"); launch();
        if (registry.entries().get(0).state() == VaultRegistry.State.PENDING) {
            check(controller() == null, "fresh_setup_without_controller");
            check(find("Vaults") != null && find("Create Local Vault") != null && find("Join Existing Vault") != null, "zero_vault_setup");
            tap("Create Local Vault"); state(AndroidVaultController.State.NO_LOCAL_VAULT);
            check(((EditText)field(activity.get(), "vaultName")).length() == 0, "optional_name_initially_empty");
            name("Personal UX2"); rotate(); check("Personal UX2".contentEquals(((EditText)field(activity.get(), "vaultName")).getText()), "name_survives_rotation_without_credentials");
            password(true, false); check("Personal UX2".equals(registry.entries().get(0).label()), "create_name_persisted");
        } else if (biometric().configured()) {
            check("Personal UX2".equals(registry.entries().get(0).label()), "known_enrolled_fixture_resume");
            state(AndroidVaultController.State.LOCKED);
            runOnMainSync(() -> biometric().close()); password(false, false); lock(); autoPrompt();
            nativeTap("Use vault password"); await(() -> !(boolean)field(biometric(), "active"));
            check(controller().snapshot().state() == AndroidVaultController.State.LOCKED, "cancel_keeps_password_screen");
            return;
        } else {
            // Resume only the exact known single fixture after a harness interruption;
            // never allocate another entry or rewrite canonical data to rerun setup.
            check("Personal UX2".equals(registry.entries().get(0).label()) && !biometric().configured(), "known_created_unenrolled_fixture_resume");
            state(AndroidVaultController.State.LOCKED); password(false, false);
        }
        lock(); check(!(boolean)field(biometric(), "active"), "unenrolled_no_prompt");
        password(false, true); lock(); autoPrompt();
        nativeTap("Use vault password"); await(() -> !(boolean)field(biometric(), "active"));
        check(controller().snapshot().state() == AndroidVaultController.State.LOCKED, "cancel_keeps_password_screen");
    }
    private void cold() {
        check(registry.entries().size() == 1 && controller() != null, "single_vault_cold_start_selected"); launch(); state(AndroidVaultController.State.LOCKED); autoPrompt();
        nativeTap("Use vault password"); await(() -> !(boolean)field(biometric(), "active"));
        for (int i = 0; i < 3; i++) { rotate(); check(!(boolean)field(biometric(), "active"), "rotation_does_not_restart_prompt"); }
        var selected = controller();
        startActivitySync(new Intent(getTargetContext(), MultiVaultUiRegression.IdleActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        launch(); check(controller() == selected && !(boolean)field(biometric(), "active"), "foreground_does_not_restart_prompt");
        tap("Use biometrics"); humanOpen("explicit retry A");
        check(find("Personal UX2") != null && find("Totipo") == null && find("Rename Vault") == null, "vault_first_compact_heading");
        lock(); autoPrompt(); humanOpen("new lock episode A");
        change(); tap("Create Local Vault"); state(AndroidVaultController.State.NO_LOCAL_VAULT);
        name("  "); password(true, false);
        var b = registry.entries().get(1); check(b.label() == null && app.localName(b).endsWith(" · Unnamed"), "whitespace_name_remains_unnamed");
        lock(); password(false, true); lock(); autoPrompt(); humanOpen("independent B");
        change(); select(registry.entries().get(0)); autoPrompt(); humanOpen("selected A after B");
        lock(); autoPrompt(); var oldPrompt = biometric(); var old = controller();
        runOnMainSync(app::changeVault); await(() -> controller() == null); bound();
        check(!(boolean)field(oldPrompt, "active") && old.retirement() == AndroidVaultController.Retirement.SUCCESS, "retirement_cancels_platform_prompt_authority");
        select(b); autoPrompt(); humanOpen("B after retiring A prompt");
        check(InactivityLock.TIMEOUT_MILLIS == 900_000, "fixed_fifteen_minute_policy");
        runOnMainSync(() -> {
            try {
                var deadline = InactivityLock.class.getDeclaredField("deadline"); deadline.setAccessible(true);
                deadline.setLong(field(controller(), "inactivity"), SystemClock.elapsedRealtime() - 1);
            } catch (Exception failure) { throw new AssertionError(failure); }
        });
        startActivitySync(new Intent(getTargetContext(), MultiVaultUiRegression.IdleActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        launch(); state(AndroidVaultController.State.LOCKED); autoPrompt(); humanOpen("inactivity return B");
    }
    private void add(String issuer, String account) {
        tap("Add token");
        runOnMainSync(() -> {
            ((EditText)field(activity.get(), "issuer")).setText(issuer);
            ((EditText)field(activity.get(), "account")).setText(account);
            ((EditText)field(activity.get(), "secret")).setText("MY");
        }); tap("Add"); state(AndroidVaultController.State.OPEN);
        await(() -> !(boolean)field(activity.get(), "adding"));
    }
    private void ui() throws Exception {
        launch();
        if (controller() == null) { select(registry.entries().get(1)); autoPrompt(); humanOpen("UI fixture B"); }
        else if (controller().snapshot().state() == AndroidVaultController.State.LOCKED) { autoPrompt(); humanOpen("UI fixture B"); }
        if (controller().snapshot().view().tokens().isEmpty()) {
            add("Zulu", "z"); add("alpha", "z"); add("ALPHA", "a"); add("", "Beta");
        } // A harness-only retry reuses the exact four public tokens; never duplicate them.
        var adapter = (TokenListAdapter)field(activity.get(), "tokens");
        check(adapter.getCount() == 4 && adapter.getItem(0).alternatives().get(0).account().equals("a")
                && adapter.getItem(1).alternatives().get(0).account().equals("z")
                && adapter.getItem(2).alternatives().get(0).account().equals("Beta"), "actual_token_adapter_sorted");
        runOnMainSync(() -> adapter.search("alpha")); check(adapter.getCount() == 2 && adapter.getItem(0).alternatives().get(0).account().equals("a"), "filter_preserves_sort");
        runOnMainSync(() -> adapter.search(""));
        if (!"true".equals(args.getString("reviewed"))) {
        reviewFontScale = 1.5f; rotate();
        check(activity.get().getResources().getConfiguration().fontScale == 1.5f, "attached_enlarged_font_without_global_settings");
        android.content.BroadcastReceiver continueReview = new android.content.BroadcastReceiver() {
            @Override public void onReceive(android.content.Context context, Intent intent) { reviewed = true; }
        };
        getTargetContext().registerReceiver(continueReview, new android.content.IntentFilter("org.totipo.ux2qualification.CONTINUE"), android.content.Context.RECEIVER_EXPORTED);
        try {
            say("HUMAN REVIEW enlarged-font token screen and menus; tell the agent when ready to continue");
            await(() -> reviewed);
        } finally { getTargetContext().unregisterReceiver(continueReview); }
        reviewFontScale = 0; rotate();
        } else say("prior human enlarged-font review retained for the identical production APK");
        await(() -> activity.get().hasWindowFocus());
        tap("ALPHA\na"); await(() -> controller().snapshot().revealedCode() != null);
        tap("ALPHA\na");
        var clipboard = getTargetContext().getSystemService(android.content.ClipboardManager.class);
        var clip = clipboard.getPrimaryClip(); var shown = controller().snapshot().revealedCode();
        check(shown != null && clip != null && clip.getDescription().getExtras() != null
                && clip.getDescription().getExtras().getString(PlatformCodeClipboard.OWNER) != null
                && shown.code().contentEquals(clip.getItemAt(0).getText()), "actual_reveal_and_owned_clipboard_copy");
        lock(); autoPrompt(); nativeTap("Use vault password");
        await(() -> !(boolean)field(biometric(), "active") && activity.get().hasWindowFocus());
        check(controller().snapshot().revealedCode() == null && clipboard.getPrimaryClip() == null, "lock_retires_code_and_owned_clipboard");
        password(false, false);
        overflow("Diagnostics");
        var info = getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(), 0);
        String expectedApp = "App: " + info.versionName + " (" + info.getLongVersionCode() + ")";
        var versionMethod = MainActivity.class.getDeclaredMethod("installedVersion"); versionMethod.setAccessible(true);
        check(expectedApp.equals("App: " + versionMethod.invoke(activity.get())), "shell_installed_version_matches_metadata");
        step = "wait_diagnostics_layout";
        await(() -> getUiAutomation().getRootInActiveWindow() != null
                && !getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText(expectedApp).isEmpty());
        check(true, "installed_package_version_in_diagnostics");
        nativeTap("Close");
        // Real Activity rendering at alternate fonts/widths without changing global settings.
        for (float scale : new float[]{1f, 1.5f, 2f}) for (int width : new int[]{320, 640}) for (int night : new int[]{android.content.res.Configuration.UI_MODE_NIGHT_NO, android.content.res.Configuration.UI_MODE_NIGHT_YES}) {
            runOnMainSync(() -> {
                try {
                    var configuration = new android.content.res.Configuration(getTargetContext().getResources().getConfiguration()); configuration.fontScale = scale;
                    configuration.uiMode = (configuration.uiMode & ~android.content.res.Configuration.UI_MODE_NIGHT_MASK) | night;
                    var context = getTargetContext().createConfigurationContext(configuration);
                    var activityInfo = new android.content.pm.ActivityInfo(); activityInfo.theme = android.R.style.Theme_Material_Light_NoActionBar;
                    var detached = (MainActivity)newActivity(MainActivity.class, context, null, app,
                            new Intent(context, MainActivity.class), activityInfo, "UX2", null, null, null);
                    detached.setTheme(activityInfo.theme);
                    android.util.TypedValue light = new android.util.TypedValue();
                    check(detached.getTheme().resolveAttribute(android.R.attr.isLightTheme, light, true) && light.data != 0, "explicit_light_theme_in_both_system_modes");
                    set(detached, "controller", controller()); set(detached, "biometric", biometric());
                    var render = MainActivity.class.getDeclaredMethod("render", AndroidVaultController.Snapshot.class); render.setAccessible(true); render.invoke(detached, controller().snapshot());
                    View root = (View)field(detached, "content"); float density = context.getResources().getDisplayMetrics().density;
                    root.measure(View.MeasureSpec.makeMeasureSpec((int)(width * density), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((int)(640 * density), View.MeasureSpec.EXACTLY));
                    root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
                    check(find(root, "Totipo") == null && find(root, app.selectedVaultLabel(controller())) != null, "scaled_vault_heading_" + scale + "_" + width);
                    for (String name : List.of("Sync", "Add token", "More options")) {
                        View action = find(root, name); check(action != null && action.getHeight() >= 48 * density && action.isFocusable(), "scaled_touch_focus_" + name);
                    }
                } catch (Exception failure) { throw new AssertionError(failure); }
            });
        }
        change();
        if (registry.entries().size() == 3 && registry.entries().get(2).state() == VaultRegistry.State.PENDING
                && !registry.entries().get(2).creationAttempted()) {
            tap("Manage " + app.localName(registry.entries().get(2))); nativeTap("Resume Join");
        } else tap("Join Existing Vault");
        state(AndroidVaultController.State.NO_LOCAL_VAULT);
        check(((EditText)field(activity.get(), "vaultName")).length() == 0, "join_name_initially_empty");
        String expectedFolder = args.getString("folder"); check(expectedFolder != null, "exact_disposable_join_folder_supplied");
        if (expectedFolder.equals(controller().initialTreeUri())) repeatSuggestion(expectedFolder);
        else {
            say("HUMAN choose and authorize ONLY the new Work-TOTP folder printed by the runner");
            tap("Choose sync folder");
        }
        await(() -> expectedFolder.equals(controller().initialTreeUri()) && "Work-TOTP".equals(app.setupName(controller()).value()));
        check("Work-TOTP".contentEquals(((EditText)field(activity.get(), "vaultName")).getText()), "actual_SAF_display_name_prefill");
        name("Manual Join name"); rotate();
        check("Manual Join name".contentEquals(((EditText)field(activity.get(), "vaultName")).getText()), "join_manual_name_survives_recreation");
        repeatSuggestion(expectedFolder);
        check("Manual Join name".equals(app.setupName(controller()).value()), "manual_join_name_not_overwritten");
        name(""); repeatSuggestion(expectedFolder);
        check(app.setupName(controller()).value().isEmpty(), "cleared_suggestion_stays_unnamed");
        name("Joined UX2"); tap("Join existing vault");
        await(() -> controller().hasJoinCandidate() && field(activity.get(), "password") != null);
        runOnMainSync(() -> { ((EditText)field(activity.get(), "password")).setText(""); ((Button)field(activity.get(), "action")).performClick(); });
        nativeTap("Unlock"); state(AndroidVaultController.State.OPEN);
        check("Joined UX2".equals(registry.entries().get(2).label()) && registry.entries().get(2).state() == VaultRegistry.State.ESTABLISHED, "authenticated_join_name_on_same_reservation");
        awaitJoinedTokens(); finishManagement();
    }
    private void awaitJoinedTokens() {
        await(() -> find("Joined UX2") != null && controller().snapshot().view() != null
                && controller().snapshot().view().tokens().size() == 3 && idle());
        check(true, "joined_name_heading_and_fixture_tokens");
    }
    private void finishJoined() throws Exception {
        launch();
        check(controller() == null && registry.entries().size() == 3
                && "Joined UX2".equals(registry.entries().get(2).label())
                && registry.entries().get(2).state() == VaultRegistry.State.ESTABLISHED, "known_authenticated_join_fixture_retained");
        select(registry.entries().get(2));
        runOnMainSync(() -> { ((EditText)field(activity.get(), "password")).setText(""); ((Button)field(activity.get(), "action")).performClick(); });
        nativeTap("Unlock"); state(AndroidVaultController.State.OPEN); awaitJoinedTokens(); finishManagement();
    }
    private void finishManagement() throws Exception {
        change();
        var b = registry.entries().get(1); byte[] canonical = java.nio.file.Files.readAllBytes(registry.root(b.id()).resolve("vault"));
        tap("Manage " + app.localName(b)); nativeTap("Forget…"); nativeTap("Forget"); await(() -> !app.managementBusy());
        check(registry.entry(b.id()).state() == VaultRegistry.State.DETACHED && find("Recovery") != null, "contextual_forget_retains_recovery");
        check(Arrays.equals(canonical, java.nio.file.Files.readAllBytes(registry.root(b.id()).resolve("vault"))), "forget_preserves_canonical_data");
        tap("Manage " + app.localName(registry.entry(b.id()))); nativeTap("Recover"); state(AndroidVaultController.State.LOCKED);
        check(!(boolean)field(biometric(), "active"), "recovered_entry_has_no_biometric_credential");
        password(false, false); check(registry.entry(b.id()).state() == VaultRegistry.State.ESTABLISHED, "recovery_requires_normal_password");
        check("Personal UX2".equals(registry.entries().get(0).label()) && "Joined UX2".equals(registry.entries().get(2).label()), "context_actions_scoped_to_B");
        overflow("Rename Vault"); setDialogText("Work UX2"); nativeTap("Save");
        await(() -> "Work UX2".equals(app.selectedVaultLabel(controller())) && find("Work UX2") != null);
        check(find("Rename Vault") == null, "rename_removes_fallback_without_prominent_control");
        change(); tap("Manage Work UX2"); nativeTap("Rename"); setDialogText("Work renamed UX2"); nativeTap("Save");
        await(() -> "Work renamed UX2".equals(registry.entries().get(1).label()));
        select(registry.entries().get(1)); password(false, false);
        check(find("Work renamed UX2") != null && controller().snapshot().view().tokens().size() == 4, "contextual_rename_preserves_identity_and_tokens");
    }
    private void navigation() {
        launch(); check(controller() == null && registry.entries().size() == 1, "fresh_navigation_fixture");
        tap("Create Local Vault"); state(AndroidVaultController.State.NO_LOCAL_VAULT);
        runOnMainSync(() -> {
            EditText field = (EditText)field(activity.get(), "vaultName"); field.requestFocus(); field.setText("Navigation UX2");
            activity.get().getSystemService(android.view.inputmethod.InputMethodManager.class).showSoftInput(field, 0);
        });
        await(() -> activity.get().getWindow().getDecorView().getRootWindowInsets() != null
                && activity.get().getWindow().getDecorView().getRootWindowInsets().isVisible(WindowInsets.Type.ime()));
        check(true, "actual_keyboard_and_name_focus");
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        await(() -> !activity.get().getWindow().getDecorView().getRootWindowInsets().isVisible(WindowInsets.Type.ime()));
        check(controller().snapshot().state() == AndroidVaultController.State.NO_LOCAL_VAULT, "keyboard_back_preserves_setup");
        password(true, false); var oldController = controller(); change();
        await(() -> activity.get().hasWindowFocus()); var oldActivity = activity.get();
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); await(oldActivity::isFinishing);
        check(controller() == null, "selector_back_does_not_restore_session"); launch();
        check(controller() == null && oldController.retirement() == AndroidVaultController.Retirement.SUCCESS, "relaunch_cannot_resurrect_retired_controller");
        select(registry.entries().get(0));
        check(controller() != oldController && controller().snapshot().view() == null, "selection_requires_new_locked_activation");
        password(false, false); var current = controller(); rotate();
        check(current == controller() && find("Navigation UX2") != null, "normal_font_heading_after_rotation");
    }
    private void setDialogText(String text) {
        try { getUiAutomation().waitForIdle(300, 5000); }
        catch (java.util.concurrent.TimeoutException busy) { throw new AssertionError("Name dialog did not settle"); }
        var queue = new ArrayDeque<AccessibilityNodeInfo>();
        queue.add(getUiAutomation().getRootInActiveWindow());
        while (!queue.isEmpty()) {
            var item = queue.remove();
            if ("android.widget.EditText".contentEquals(item.getClassName())) {
                var value = new Bundle(); value.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                check(item.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, value), "native_name_edit"); return;
            }
            for (int i = 0; i < item.getChildCount(); i++) if (item.getChild(i) != null) queue.add(item.getChild(i));
        }
        throw new AssertionError("Name field unavailable");
    }
    private void repeatSuggestion(String folder) throws Exception {
        runOnMainSync(() -> app.suggestJoinName(controller(), android.net.Uri.parse(folder)));
        ((java.util.concurrent.ExecutorService)field(app, "metadataWorker")).submit(() -> {}).get(30, java.util.concurrent.TimeUnit.SECONDS);
        waitForIdleSync();
    }
    @Override public Activity newActivity(ClassLoader loader, String className, Intent intent)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        Activity value = super.newActivity(loader, className, intent);
        // Apply before ActivityThread attaches/reads resources. Pre-created lifecycle
        // callbacks are already too late on this platform. Instance stays production MainActivity.
        if (value instanceof MainActivity && reviewFontScale > 0) {
            var configuration = new android.content.res.Configuration();
            configuration.fontScale = reviewFontScale;
            configuration.uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES;
            value.applyOverrideConfiguration(configuration);
        }
        return value;
    }
    @Override public void onCreate(Bundle value) { super.onCreate(value); args = value; start(); }
    @Override public void onStart() {
        Bundle result = new Bundle(); int code = 0;
        try {
            check(getTargetContext().getPackageName().equals("org.totipo.ux2qualification"), "fresh_disposable_uid_only");
            // Instrumentation starts before Application.onCreate completes; synchronize
            // with main before reading its registry, as the existing harnesses do.
            runOnMainSync(() -> {});
            app = (TotipoApplication)getTargetContext().getApplicationContext(); registry = (VaultRegistry)field(app, "registry");
            check(!getTargetContext().getSystemService(KeyguardManager.class).isKeyguardLocked(), "device_unlocked");
            app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                public void onActivityCreated(Activity value, Bundle saved) { if (value instanceof MainActivity main) activity.set(main); }
                public void onActivityResumed(Activity value) { value.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); }
                public void onActivityStarted(Activity value) {} public void onActivityStopped(Activity value) {}
                public void onActivityPaused(Activity value) {} public void onActivitySaveInstanceState(Activity value, Bundle saved) {}
                public void onActivityDestroyed(Activity value) {}
            });
            switch (args.getString("phase")) { case "ux2-first" -> first(); case "ux2-cold" -> cold(); case "ux2-ui" -> ui(); case "ux2-finish" -> finishJoined(); case "ux2-navigation" -> navigation(); default -> throw new AssertionError(); }
            result.putString("stream", "DAILY_USE_UX_PASS phase=" + args.getString("phase") + " checks=" + checks + "\n"); code = -1;
        } catch (Throwable failure) {
            result.putString("stream", "DAILY_USE_UX_FAIL step=" + step + " type=" + failure.getClass().getSimpleName() + " location="
                    + Arrays.toString(Arrays.copyOf(failure.getStackTrace(), Math.min(5, failure.getStackTrace().length))) + "\n");
        } finally { if (activity.get() != null) runOnMainSync(() -> activity.get().finish()); finish(code, result); }
    }
}
