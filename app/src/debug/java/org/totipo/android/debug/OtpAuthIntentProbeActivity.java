package org.totipo.android.debug;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.ClipData;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Spanned;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Debug transport qualification only. No enrollment or Activity-result contract. */
public final class OtpAuthIntentProbeActivity extends Activity {
    // Only fixed enums/booleans survive consumption, never caller-owned objects.
    private enum ActionShape { VIEW, SEND, SEND_MULTIPLE, OTHER }
    private enum MimeShape { TEXT_PLAIN, MISSING, OTHER }
    private enum PayloadType { STRING, CHAR_SEQUENCE, NON_TEXT, MISSING }
    private enum FixtureShape { EXACT, SURROUNDING_WHITESPACE, EMBEDDED, OTHER }
    private ActionShape actionShape = ActionShape.OTHER;
    private MimeShape mimeShape = MimeShape.MISSING;
    private PayloadType payloadType = PayloadType.MISSING;
    private FixtureShape fixtureShape = FixtureShape.OTHER;
    private boolean textPresent, clipPresent, streamPresent, acceptedResult, cleanShape;
    private boolean clipTextMirror;
    private static final int MAX_TEXT_LENGTH = 8192;
    // M2C's disposable public RFC fixture only. This comparison is not a production parser.
    private static final String PUBLIC_FIXTURE =
            "otpauth://totp/Totipo%20Camera%20Test:test@example.invalid?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
            + "&issuer=Totipo%20Camera%20Test&algorithm=SHA1&digits=6&period=30";

    @Override protected void onCreate(Bundle ignoredState) {
        boolean accepted = consume(getIntent());
        super.onCreate(null);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        showResult(accepted);
    }

    @Override protected void onNewIntent(Intent incoming) {
        boolean accepted = consume(incoming);
        super.onNewIntent(incoming);
        showResult(accepted);
    }

    private boolean consume(Intent incoming) {
        // Framework URI/String storage cannot be wiped. Retain only a boolean in our UI.
        boolean accepted = false;
        actionShape = ActionShape.OTHER;
        mimeShape = MimeShape.MISSING;
        payloadType = PayloadType.MISSING;
        fixtureShape = FixtureShape.OTHER;
        textPresent = clipPresent = streamPresent = cleanShape = clipTextMirror = false;
        try {
            if (incoming != null) {
                actionShape = Intent.ACTION_SEND.equals(incoming.getAction()) ? ActionShape.SEND
                        : Intent.ACTION_SEND_MULTIPLE.equals(incoming.getAction()) ? ActionShape.SEND_MULTIPLE
                        : Intent.ACTION_VIEW.equals(incoming.getAction()) ? ActionShape.VIEW : ActionShape.OTHER;
                mimeShape = incoming.getType() == null ? MimeShape.MISSING
                        : "text/plain".equals(incoming.getType()) ? MimeShape.TEXT_PLAIN : MimeShape.OTHER;
                textPresent = incoming.hasExtra(Intent.EXTRA_TEXT);
                clipPresent = incoming.getClipData() != null;
                streamPresent = incoming.hasExtra(Intent.EXTRA_STREAM);
            }
            if (actionShape == ActionShape.SEND || actionShape == ActionShape.SEND_MULTIPLE) {
                accepted = consumeShare(incoming);
                return accepted;
            }
            Uri data = incoming == null ? null : incoming.getData();
            accepted = incoming != null && Intent.ACTION_VIEW.equals(incoming.getAction())
                    && data != null && data.isHierarchical()
                    && "otpauth".equals(data.getScheme()) && "totp".equals(data.getHost())
                    && "totp".equals(data.getEncodedAuthority());
        } catch (RuntimeException ignored) {
            // No exception details or input are exposed.
        } finally {
            if (incoming != null) {
                try {
                    incoming.removeExtra(Intent.EXTRA_TEXT);
                } catch (RuntimeException ignored) {
                    // Malformed extras must not prevent dropping the entire Bundle.
                }
                incoming.setData(null);
                incoming.replaceExtras((Bundle) null);
                incoming.setClipData(null);
                incoming.setSelector(null);
            }
            setIntent(new Intent());
            acceptedResult = accepted;
        }
        return accepted;
    }

    @SuppressWarnings("deprecation") // Inspect actual type rather than coercing a non-text extra.
    private boolean consumeShare(Intent incoming) {
        Bundle extras = incoming.getExtras();
        Object payload = extras == null ? null : extras.get(Intent.EXTRA_TEXT);
        payloadType = payload == null ? PayloadType.MISSING : payload instanceof String ? PayloadType.STRING
                : payload instanceof CharSequence ? PayloadType.CHAR_SEQUENCE : PayloadType.NON_TEXT;
        if (!(payload instanceof CharSequence)) return false;
        CharSequence chars = (CharSequence) payload;
        if (chars.length() == 0 || chars.length() > MAX_TEXT_LENGTH || hasSpans(chars)) return false;
        // Bounded temporary immutable copy; it cannot be guaranteed erased by the framework/JVM.
        String text = copyText(chars);
        fixtureShape = text.equals(PUBLIC_FIXTURE) ? FixtureShape.EXACT
                : text.trim().equals(PUBLIC_FIXTURE) ? FixtureShape.SURROUNDING_WHITESPACE
                : text.contains(PUBLIC_FIXTURE) ? FixtureShape.EMBEDDED : FixtureShape.OTHER;
        if (!Intent.ACTION_SEND.equals(incoming.getAction()) || !"text/plain".equals(incoming.getType())
                || incoming.getData() != null || incoming.getSelector() != null || streamPresent
                || extras == null || extras.size() != 1 || !textPresent) return false;
        if (incoming.getCategories() != null
                && (incoming.getCategories().size() != 1
                    || !incoming.hasCategory(Intent.CATEGORY_DEFAULT))) return false;
        int grants = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;
        if ((incoming.getFlags() & grants) != 0) return false;
        ClipData clip = incoming.getClipData();
        if (clip != null) {
            if (clip.getItemCount() != 1 || clip.getDescription().getMimeTypeCount() != 1
                    || !"text/plain".equals(clip.getDescription().getMimeType(0))) return false;
            ClipData.Item item = clip.getItemAt(0);
            if (item.getUri() != null || item.getIntent() != null || item.getHtmlText() != null
                    || item.getText() == null || item.getText().length() > MAX_TEXT_LENGTH
                    || hasSpans(item.getText()) || !text.equals(copyText(item.getText()))) return false;
            clipTextMirror = true;
        }
        cleanShape = true;
        // No extraction from prose or normalization; transport envelope only, no enrollment fields.
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i)) || Character.isISOControl(text.charAt(i))) return false;
        }
        Uri data = Uri.parse(text);
        return data.isHierarchical() && "otpauth".equals(data.getScheme())
                && "totp".equals(data.getHost()) && "totp".equals(data.getEncodedAuthority());
    }

    private static boolean hasSpans(CharSequence text) {
        return text instanceof Spanned && ((Spanned) text).getSpans(0, text.length(), Object.class).length != 0;
    }

    private static String copyText(CharSequence text) {
        char[] copy = new char[text.length()];
        try {
            for (int i = 0; i < copy.length; i++) copy[i] = text.charAt(i);
            return new String(copy);
        } finally {
            java.util.Arrays.fill(copy, '\0');
        }
    }

    @Override public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        // Do not call super.dump: only these fixed structural facts are qualification evidence.
        writer.println("M2C2 action=" + actionShape + " mime=" + mimeShape
                + " extra_text_present=" + textPresent + " clip_present=" + clipPresent
                + " stream_present=" + streamPresent + " payload_type=" + payloadType
                + " clip_text_mirror=" + clipTextMirror + " clean_shape=" + cleanShape
                + " fixture_shape=" + fixtureShape + " exact_public_fixture_match=" + (fixtureShape == FixtureShape.EXACT)
                + " accepted=" + acceptedResult);
    }

    private void showResult(boolean accepted) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(24, 80, 24, 24);
        content.setSaveEnabled(false);
        content.setSaveFromParentEnabled(false);
        TextView result = new TextView(this);
        result.setSaveEnabled(false);
        boolean share = actionShape == ActionShape.SEND || actionShape == ActionShape.SEND_MULTIPLE;
        result.setText(share ? (accepted ? "otpauth TOTP share received" : "Unsupported shared content")
                : (accepted ? "otpauth TOTP link received" : "Unsupported link"));
        content.addView(result);
        Button camera = new Button(this);
        camera.setSaveEnabled(false);
        camera.setText("Open native camera (debug)");
        camera.setOnClickListener(view -> {
            try {
                startActivity(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));
            } catch (ActivityNotFoundException | SecurityException ignored) {
                result.setText("Native camera unavailable");
            }
        });
        if (!share) content.addView(camera);
        setContentView(content);
    }

    @Override protected void onSaveInstanceState(Bundle ignoredState) {
        // Deliberately omit framework/view state saving.
    }

    @Override protected void onRestoreInstanceState(Bundle ignoredState) {
        // Fixed label only; no restoration of transport input.
    }
}
