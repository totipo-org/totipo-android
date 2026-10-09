package org.totipo.ingresstest;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannedString;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.ArrayList;

/** Standalone framework-only instrumentation, built outside Gradle; public fixture only. */
public final class OtpAuthIngressRegression extends Instrumentation {
    private static final String COMPONENT = "org.totipo.android.OtpAuthEnrollmentActivity";
    private static final String FIXTURE = "otpauth://totp/Totipo%20Camera%20Test:test@example.invalid?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
            + "&issuer=Totipo%20Camera%20Test&algorithm=SHA1&digits=6&period=30";
    private int passed;
    private String current = "setup";
    private static Intent send() {
        return new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, FIXTURE);
    }
    private static Intent withClip(ClipData clip) {
        Intent intent = send(); intent.setClipData(clip); return intent;
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    private static boolean hasLabel(View view, String label) {
        if (view instanceof TextView && label.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (hasLabel(group.getChildAt(i), label)) return true;
        }
        return false;
    }
    private void test(String name, Intent incoming, boolean expected) {
        test(name, incoming, expected, false);
    }
    private void test(String name, Intent incoming, boolean expected, boolean redeliver) {
        current = name;
        incoming.setClassName("org.totipo.android", COMPONENT).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        current = name + "_launch";
        Activity activity = startActivitySync(redeliver
                ? new Intent().setClassName("org.totipo.android", COMPONENT).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) : incoming);
        current = name + "_inspect";
        waitForIdleSync();
        final boolean[] ok = {false};
        runOnMainSync(() -> {
            if (redeliver) {
                try {
                    var method = activity.getClass().getDeclaredMethod("onNewIntent", Intent.class);
                    method.setAccessible(true); method.invoke(activity, incoming);
                } catch (ReflectiveOperationException ignored) {
                    activity.finish(); return;
                }
            }
            Intent retained = activity.getIntent();
            boolean confirmation = hasLabel(activity.getWindow().getDecorView(), "Add to Totipo");
            boolean fixedRejection = hasLabel(activity.getWindow().getDecorView(), "This authenticator link isn't supported by Totipo.")
                    || hasLabel(activity.getWindow().getDecorView(), "This authenticator link is invalid.");
            boolean publicMetadata = hasLabel(activity.getWindow().getDecorView(), "Totipo Camera Test")
                    && hasLabel(activity.getWindow().getDecorView(), "test@example.invalid")
                    && hasLabel(activity.getWindow().getDecorView(), "SHA1")
                    && hasLabel(activity.getWindow().getDecorView(), "6") && hasLabel(activity.getWindow().getDecorView(), "30");
            ok[0] = (expected ? confirmation && publicMetadata : fixedRejection)
                    && retained.getExtras() == null && retained.getData() == null
                    && retained.getClipData() == null && retained.getAction() == null;
            if (!ok[0]) {
                Bundle facts = new Bundle();
                facts.putString("stream", "M2D_FACTS confirmation=" + confirmation + " rejected=" + fixedRejection
                        + " expired=" + hasLabel(activity.getWindow().getDecorView(), "Enrollment expired. Scan or share the QR again.")
                        + " busy=" + hasLabel(activity.getWindow().getDecorView(), "Totipo is busy. Scan or share the QR again.")
                        + " unlock_retry=" + hasLabel(activity.getWindow().getDecorView(), "Unlock Totipo, then scan or share the QR again.")
                        + " clean_intent=" + (retained.getExtras() == null && retained.getData() == null && retained.getClipData() == null)
                        + "\n"); sendStatus(0, facts);
            }
            activity.finish();
        });
        waitForIdleSync();
        current = name + "_check";
        if (!ok[0]) throw new AssertionError("Fixed-result check failed");
        passed++;
        Bundle status = new Bundle();
        status.putString("stream", "M2D_TEST " + name + " PASS\n");
        sendStatus(0, status);
    }
    private Object isolated, original;
    private java.nio.file.Path testDirectory;
    private Class<?> controllerClass;
    private Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        return target.getClass().getMethod(name, types).invoke(target, args);
    }
    private Object call(Object target, String name) throws Exception { return call(target, name, new Class<?>[0]); }
    private String state() throws Exception { return call(call(isolated, "snapshot"), "state").toString(); }
    private boolean reached(String expected) throws Exception {
        synchronized (isolated) {
            var field = controllerClass.getDeclaredField("operating"); field.setAccessible(true);
            return expected.equals(state()) && !field.getBoolean(isolated);
        }
    }
    private void awaitState(String expected) throws Exception {
        long deadline = android.os.SystemClock.uptimeMillis() + 120000;
        while (!reached(expected) && android.os.SystemClock.uptimeMillis() < deadline) android.os.SystemClock.sleep(50);
        if (!reached(expected)) throw new AssertionError("Controller state check failed");
    }
    private void command(String name, boolean credential) {
        final boolean[] admitted = {false};
        runOnMainSync(() -> {
            try { admitted[0] = (Boolean) (credential ? call(isolated, name, new Class<?>[]{char[].class}, new char[0]) : call(isolated, name)); }
            catch (Exception failure) { throw new AssertionError("Command failed"); }
        });
        if (!admitted[0]) throw new AssertionError("Command rejected");
    }
    private void setupIsolatedVault() throws Exception {
        current = "isolated_setup";
        ClassLoader loader = getTargetContext().getClassLoader();
        controllerClass = loader.loadClass("org.totipo.android.AndroidVaultController");
        Class<?> ownerClass = loader.loadClass("org.totipo.android.LocalReplicaOwner");
        Class<?> dispatcherClass = loader.loadClass("org.totipo.android.AndroidVaultController$Dispatcher");
        testDirectory = java.nio.file.Files.createTempDirectory(getTargetContext().getCacheDir().toPath(), "ingress-regression-");
        var ownerConstructor = ownerClass.getDeclaredConstructor(java.nio.file.Path.class); ownerConstructor.setAccessible(true);
        Object owner = ownerConstructor.newInstance(testDirectory);
        android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        Object dispatcher = java.lang.reflect.Proxy.newProxyInstance(loader, new Class<?>[]{dispatcherClass}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "post": main.post((Runnable) args[0]); return null;
                case "after": Runnable action = (Runnable) args[1]; main.postDelayed(action, (Long) args[0]);
                    return (Runnable) () -> main.removeCallbacks(action);
                case "assertDispatchThread": if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) throw new AssertionError("Dispatch thread"); return null;
                case "assertWorkerThread": if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) throw new AssertionError("Worker thread"); return null;
                default: return null;
            }
        });
        var constructor = controllerClass.getDeclaredConstructor(ownerClass, dispatcherClass); constructor.setAccessible(true);
        runOnMainSync(() -> {
            try {
                isolated = constructor.newInstance(owner, dispatcher);
                var field = getTargetContext().getApplicationContext().getClass().getDeclaredField("vaultController"); field.setAccessible(true);
                original = field.get(getTargetContext().getApplicationContext()); field.set(getTargetContext().getApplicationContext(), isolated);
            } catch (Exception failure) { throw new AssertionError("Isolated setup failed"); }
        });
        awaitState("NO_LOCAL_VAULT");
        discardedTest("no_vault_discard");
        test("no_vault_hotp_rejected", send().putExtra(Intent.EXTRA_TEXT, "otpauth://hotp/public"), false);
        command("create", true); awaitState("OPEN");
    }
    private void cleanupIsolatedVault() throws Exception {
        if (isolated != null && (state().equals("OPEN") || state().equals("ERROR_OPEN"))) { command("lock", false); awaitState("LOCKED"); }
        if (original != null) {
            var field = getTargetContext().getApplicationContext().getClass().getDeclaredField("vaultController"); field.setAccessible(true);
            field.set(getTargetContext().getApplicationContext(), original);
        }
        if (testDirectory != null) {
            try (var files = java.nio.file.Files.walk(testDirectory)) {
                for (var path : files.sorted(java.util.Comparator.reverseOrder()).toList()) java.nio.file.Files.delete(path);
            }
        }
    }
    private Activity launchFixture() {
        Activity activity = startActivitySync(send().setClassName("org.totipo.android", COMPONENT).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitForIdleSync(); return activity;
    }
    private Object pending(Activity activity) throws Exception {
        var field = activity.getClass().getDeclaredField("draft"); field.setAccessible(true); return field.get(activity);
    }
    private void invoke(Activity activity, String name, Class<?>[] types, Object... args) throws Exception {
        var method = activity.getClass().getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(activity, args);
    }
    private void check(boolean ok) { if (!ok) throw new AssertionError("Lifecycle check failed"); }
    private void passed(String name) { passed++; Bundle status = new Bundle(); status.putString("stream", "M2D_TEST " + name + " PASS\n"); sendStatus(0, status); }
    private int tokenCount() throws Exception { return ((java.util.List<?>) call(call(call(isolated, "snapshot"), "view"), "tokens")).size(); }
    private void discardedTest(String name) throws Exception {
        current = name; Activity activity = launchFixture();
        runOnMainSync(() -> {
            try { check(pending(activity) == null && hasLabel(activity.getWindow().getDecorView(), "Unlock Totipo, then scan or share the QR again.")); activity.finish(); }
            catch (Exception failure) { throw new AssertionError("Discard check failed"); }
        }); waitForIdleSync(); passed(name);
    }
    private void lifecycleTests() throws Exception {
        check(tokenCount() == 0); // All transport confirmations were cancelled without authoring.
        for (String exit : new String[]{"cancel", "onBackPressed"}) {
            current = exit; Activity activity = launchFixture();
            runOnMainSync(() -> {
                try { Object draft = pending(activity); var field = draft.getClass().getDeclaredField("secret"); field.setAccessible(true);
                    char[] secret = (char[]) field.get(draft); invoke(activity, exit, new Class<?>[0]);
                    for (char c : secret) check(c == 0); check(pending(activity) == null);
                } catch (Exception failure) { throw new AssertionError("Exit check failed"); }
            }); waitForIdleSync(); check(tokenCount() == 0); passed(exit + "_wipes_no_authorship");
        }
        current = "recreation"; Activity activity = launchFixture(); Bundle saved = new Bundle();
        ActivityMonitor monitor = addMonitor(COMPONENT, null, false);
        runOnMainSync(() -> {
            try { Object draft = pending(activity); var field = draft.getClass().getDeclaredField("secret"); field.setAccessible(true); char[] secret = (char[]) field.get(draft);
                invoke(activity, "onSaveInstanceState", new Class<?>[]{Bundle.class}, saved);
                check(saved.size() == 1 && saved.getBoolean("consumed")); for (char c : secret) check(c == 0);
                check(pending(activity) == null);
                // Deliberately repopulate a public launch Intent to prove saved-state precedence.
                activity.setIntent(send()); activity.recreate();
            } catch (Exception failure) { throw new AssertionError("Recreation check failed"); }
        });
        Activity restored = waitForMonitorWithTimeout(monitor, 10000); removeMonitor(monitor);
        check(restored != null); waitForIdleSync();
        runOnMainSync(() -> {
            try { check(pending(restored) == null && hasLabel(restored.getWindow().getDecorView(), "Enrollment expired. Scan or share the QR again."));
                check(restored.getIntent().getExtras() == null); restored.finish();
            } catch (Exception failure) { throw new AssertionError("Restoration check failed"); }
        }); waitForIdleSync(); check(tokenCount() == 0); passed("recreation_wipes_marker_only_no_reconsume");
        current = "admission_failure"; Activity rejected = launchFixture();
        runOnMainSync(() -> {
            try { Object draft = pending(rejected); var field = draft.getClass().getDeclaredField("secret"); field.setAccessible(true); char[] secret = (char[]) field.get(draft);
                check((Boolean) call(isolated, "lock")); invoke(rejected, "add", new Class<?>[0]);
                check(pending(rejected) == null); for (char c : secret) check(c == 0); rejected.finish();
            } catch (Exception failure) { throw new AssertionError("Admission check failed"); }
        }); awaitState("LOCKED"); waitForIdleSync(); command("unlock", true); awaitState("OPEN"); check(tokenCount() == 0); passed("rejected_add_wipes");
        command("lock", false); awaitState("LOCKED"); discardedTest("locked_discard");
        test("locked_hotp_rejected", send().putExtra(Intent.EXTRA_TEXT, "otpauth://hotp/public"), false);
        command("unlock", true); awaitState("OPEN"); check(tokenCount() == 0); passed("unlock_no_resurrection");
        current = "add"; Activity addActivity = launchFixture();
        runOnMainSync(() -> {
            try { invoke(addActivity, "add", new Class<?>[0]); check(pending(addActivity) == null); invoke(addActivity, "add", new Class<?>[0]); }
            catch (Exception failure) { throw new AssertionError("Add check failed"); }
        }); awaitState("OPEN"); waitForIdleSync(); check(tokenCount() == 1);
        check(call(call(isolated, "snapshot"), "revealedCode") == null); passed("add_once_concealed");
        command("lock", false); awaitState("LOCKED"); command("unlock", true); awaitState("OPEN");
        check(tokenCount() == 1); passed("added_token_persists");
    }
    @Override public void onStart() {
        try {
            setupIsolatedVault();
            test("exact_public_fixture", send(), true);
            test("char_sequence", send().putExtra(Intent.EXTRA_TEXT, new SpannedString(FIXTURE)), true);
            test("ordinary_text", send().putExtra(Intent.EXTRA_TEXT, "ordinary public test text"), false);
            test("https", send().putExtra(Intent.EXTRA_TEXT, "https://example.com"), false);
            test("hotp", send().putExtra(Intent.EXTRA_TEXT, "otpauth://hotp/public"), false);
            test("view_wrong_scheme", new Intent(Intent.ACTION_VIEW, Uri.parse("https://totp/a")), false);
            test("view_hotp", new Intent(Intent.ACTION_VIEW, Uri.parse("otpauth://hotp/a")), false);
            test("view_malformed", new Intent(Intent.ACTION_VIEW, Uri.parse("otpauth://totp/a?secret=%FF")), false);
            Intent selected = send(); selected.setSelector(new Intent(Intent.ACTION_VIEW));
            test("selector", selected, false);
            Intent missing = new Intent(Intent.ACTION_SEND).setType("text/plain");
            test("missing_text", missing, false);
            test("null_text", send().putExtra(Intent.EXTRA_TEXT, (String) null), false);
            test("empty_text", send().putExtra(Intent.EXTRA_TEXT, ""), false);
            test("missing_type", send().setType(null), false);
            test("wrong_mime", send().setType("text/html"), false);
            test("send_multiple", send().setAction(Intent.ACTION_SEND_MULTIPLE), false);
            test("non_text_integer", send().putExtra(Intent.EXTRA_TEXT, 42), false);
            test("text_array", send().putExtra(Intent.EXTRA_TEXT, new String[] {FIXTURE, FIXTURE}), false);
            ArrayList<String> texts = new ArrayList<>(); texts.add(FIXTURE); texts.add(FIXTURE);
            test("text_list", send().putStringArrayListExtra(Intent.EXTRA_TEXT, texts), false);
            test("stream", send().putExtra(Intent.EXTRA_STREAM, Uri.parse("content://public.invalid/test")), false);
            test("unexpected_extra", send().putExtra(Intent.EXTRA_SUBJECT, "public test"), false);
            test("unexpected_data", send().setDataAndType(Uri.parse("https://example.com"), "text/plain"), false);
            test("uri_grant", send().addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), false);
            test("unexpected_category", send().addCategory(Intent.CATEGORY_BROWSABLE), false);
            test("default_category", send().addCategory(Intent.CATEGORY_DEFAULT), true);
            test("view_send_shape", send().setAction(Intent.ACTION_VIEW), false);
            test("view_preserved", new Intent(Intent.ACTION_VIEW, Uri.parse(FIXTURE)), true);
            test("whitespace_wrapper", send().putExtra(Intent.EXTRA_TEXT, "\n" + FIXTURE + "\n"), false);
            test("prose_wrapper", send().putExtra(Intent.EXTRA_TEXT, "Public test: " + FIXTURE), false);
            test("oversized_text", send().putExtra(Intent.EXTRA_TEXT, "x".repeat(8193)), false);
            test("clip_text_mirror", withClip(ClipData.newPlainText(null, FIXTURE)), true);
            test("clip_uri_attachment", withClip(ClipData.newRawUri(null, Uri.parse("content://public.invalid/test"))), false);
            test("clip_html_attachment", withClip(ClipData.newHtmlText(null, FIXTURE, "<p>public</p>")), false);
            test("clip_intent_attachment", withClip(ClipData.newIntent(null, new Intent(Intent.ACTION_VIEW))), false);
            test("clip_different_text", withClip(ClipData.newPlainText(null, "public different text")), false);
            ClipData multiple = ClipData.newPlainText(null, FIXTURE); multiple.addItem(new ClipData.Item(FIXTURE));
            test("clip_multiple_items", withClip(multiple), false);
            test("new_intent_send", send(), true, true);
            test("new_intent_reject", send().putExtra(Intent.EXTRA_TEXT, "public ordinary text"), false, true);
            test("new_intent_view", new Intent(Intent.ACTION_VIEW, Uri.parse(FIXTURE)), true, true);
            lifecycleTests();
            cleanupIsolatedVault();
            Bundle result = new Bundle(); result.putString("stream", "M2D_TESTS PASS count=" + passed + "\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable ignored) {
            try { cleanupIsolatedVault(); } catch (Throwable cleanupFailure) { }
            // Never emit exception messages or input contents.
            Bundle result = new Bundle(); result.putString("stream", "M2D_TESTS FAIL case=" + current + " passed=" + passed + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
