package org.totipo.qualification;

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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;

/** Standalone framework-only instrumentation, built outside Gradle; public fixture only. */
public final class OtpAuthShareQualification extends Instrumentation {
    private static final String COMPONENT = "org.totipo.android.debug.OtpAuthIntentProbeActivity";
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
    private void test(String name, Intent incoming, boolean expected, String shape) {
        test(name, incoming, expected, shape, false);
    }
    private void test(String name, Intent incoming, boolean expected, String shape, boolean redeliver) {
        current = name;
        incoming.setClassName("org.totipo.android", COMPONENT).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        boolean isView = Intent.ACTION_VIEW.equals(incoming.getAction());
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
            StringWriter out = new StringWriter();
            activity.dump("", null, new PrintWriter(out), new String[0]);
            String result = out.toString(); // Fixed facts only: probe overrides dump, skipping framework dump.
            Intent retained = activity.getIntent();
            String label = isView
                    ? (expected ? "otpauth TOTP link received" : "Unsupported link")
                    : (expected ? "otpauth TOTP share received" : "Unsupported shared content");
            ok[0] = result.contains(" accepted=" + expected) && (shape == null || result.contains(shape))
                    && retained.getExtras() == null && retained.getData() == null
                    && retained.getClipData() == null && retained.getAction() == null
                    && hasLabel(activity.getWindow().getDecorView(), label);
            activity.finish();
        });
        waitForIdleSync();
        current = name + "_check";
        if (!ok[0]) throw new AssertionError("Fixed-result check failed");
        passed++;
        Bundle status = new Bundle();
        status.putString("stream", "M2C2_TEST " + name + " PASS\n");
        sendStatus(0, status);
    }
    @Override public void onStart() {
        try {
            test("exact_public_fixture", send(), true, "fixture_shape=EXACT");
            test("char_sequence", send().putExtra(Intent.EXTRA_TEXT, new SpannedString(FIXTURE)), true, null);
            test("ordinary_text", send().putExtra(Intent.EXTRA_TEXT, "ordinary public test text"), false, null);
            test("https", send().putExtra(Intent.EXTRA_TEXT, "https://example.com"), false, null);
            test("hotp", send().putExtra(Intent.EXTRA_TEXT, "otpauth://hotp/public"), false, null);
            Intent missing = new Intent(Intent.ACTION_SEND).setType("text/plain");
            test("missing_text", missing, false, null);
            test("null_text", send().putExtra(Intent.EXTRA_TEXT, (String) null), false, null);
            test("empty_text", send().putExtra(Intent.EXTRA_TEXT, ""), false, null);
            test("missing_type", send().setType(null), false, null);
            test("wrong_mime", send().setType("text/html"), false, null);
            test("send_multiple", send().setAction(Intent.ACTION_SEND_MULTIPLE), false, null);
            test("non_text_integer", send().putExtra(Intent.EXTRA_TEXT, 42), false, "payload_type=NON_TEXT");
            test("text_array", send().putExtra(Intent.EXTRA_TEXT, new String[] {FIXTURE, FIXTURE}), false, null);
            ArrayList<String> texts = new ArrayList<>(); texts.add(FIXTURE); texts.add(FIXTURE);
            test("text_list", send().putStringArrayListExtra(Intent.EXTRA_TEXT, texts), false, null);
            test("stream", send().putExtra(Intent.EXTRA_STREAM, Uri.parse("content://public.invalid/test")), false, null);
            test("unexpected_extra", send().putExtra(Intent.EXTRA_SUBJECT, "public test"), false, null);
            test("unexpected_data", send().setDataAndType(Uri.parse("https://example.com"), "text/plain"), false, null);
            test("uri_grant", send().addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), false, null);
            test("unexpected_category", send().addCategory(Intent.CATEGORY_BROWSABLE), false, null);
            test("default_category", send().addCategory(Intent.CATEGORY_DEFAULT), true, null);
            test("view_send_shape", send().setAction(Intent.ACTION_VIEW), false, null);
            test("view_preserved", new Intent(Intent.ACTION_VIEW, Uri.parse(FIXTURE)), true, null);
            test("whitespace_wrapper", send().putExtra(Intent.EXTRA_TEXT, "\n" + FIXTURE + "\n"), false, "fixture_shape=SURROUNDING_WHITESPACE");
            test("prose_wrapper", send().putExtra(Intent.EXTRA_TEXT, "Public test: " + FIXTURE), false, "fixture_shape=EMBEDDED");
            test("oversized_text", send().putExtra(Intent.EXTRA_TEXT, "x".repeat(8193)), false, null);
            test("clip_text_mirror", withClip(ClipData.newPlainText(null, FIXTURE)), true, "clip_text_mirror=true");
            test("clip_uri_attachment", withClip(ClipData.newRawUri(null, Uri.parse("content://public.invalid/test"))), false, null);
            test("clip_html_attachment", withClip(ClipData.newHtmlText(null, FIXTURE, "<p>public</p>")), false, null);
            test("clip_intent_attachment", withClip(ClipData.newIntent(null, new Intent(Intent.ACTION_VIEW))), false, null);
            test("clip_different_text", withClip(ClipData.newPlainText(null, "public different text")), false, null);
            ClipData multiple = ClipData.newPlainText(null, FIXTURE); multiple.addItem(new ClipData.Item(FIXTURE));
            test("clip_multiple_items", withClip(multiple), false, null);
            test("new_intent_send", send(), true, "fixture_shape=EXACT", true);
            test("new_intent_reject", send().putExtra(Intent.EXTRA_TEXT, "public ordinary text"), false, null, true);
            test("new_intent_view", new Intent(Intent.ACTION_VIEW, Uri.parse(FIXTURE)), true, null, true);
            Bundle result = new Bundle(); result.putString("stream", "M2C2_TESTS PASS count=" + passed + "\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable ignored) {
            // Never emit exception messages or input contents.
            Bundle result = new Bundle(); result.putString("stream", "M2C2_TESTS FAIL case=" + current + " passed=" + passed + " exception_type=" + ignored.getClass().getName() + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
