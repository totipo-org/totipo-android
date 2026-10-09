package org.totipo.android;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Spanned;
import java.util.Arrays;

/** Transport shape only. Both outputs go to the same semantic parser. No retained framework input. */
final class OtpAuthTransport {
    private OtpAuthTransport() { }
    static String extract(Intent incoming) {
        if (incoming == null || incoming.getSelector() != null) return null;
        int grants = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;
        if ((incoming.getFlags() & grants) != 0) return null;
        if (Intent.ACTION_VIEW.equals(incoming.getAction())) {
            Uri data = incoming.getData();
            Bundle extras = incoming.getExtras();
            if (!envelope(data)
                    || incoming.getType() != null || incoming.getClipData() != null
                    || (extras != null && !extras.isEmpty())) return null;
            String text = data.toString(); // Parsing only; never emitted or stored in Activity state.
            return text.length() <= OtpAuthUriParser.MAX_INPUT_UNITS ? text : null;
        }
        return extractShare(incoming);
    }
    @SuppressWarnings("deprecation")
    private static String extractShare(Intent incoming) {
        if (!Intent.ACTION_SEND.equals(incoming.getAction()) || !"text/plain".equals(incoming.getType())
                || incoming.getData() != null) return null;
        Bundle extras = incoming.getExtras();
        if (extras == null || extras.size() != 1 || !extras.containsKey(Intent.EXTRA_TEXT)) return null;
        Object payload = extras.get(Intent.EXTRA_TEXT);
        if (!(payload instanceof CharSequence)) return null;
        CharSequence chars = (CharSequence) payload;
        if (!plain(chars)) return null;
        if (incoming.getCategories() != null && (incoming.getCategories().size() != 1
                || !incoming.hasCategory(Intent.CATEGORY_DEFAULT))) return null;
        ClipData clip = incoming.getClipData();
        if (clip != null) {
            if (clip.getItemCount() != 1 || clip.getDescription().getMimeTypeCount() != 1
                    || !"text/plain".equals(clip.getDescription().getMimeType(0))) return null;
            ClipData.Item item = clip.getItemAt(0);
            CharSequence mirror = item.getText();
            if (item.getUri() != null || item.getIntent() != null || item.getHtmlText() != null
                    || mirror == null || !plain(mirror) || mirror.length() != chars.length()) return null;
            for (int i = 0; i < chars.length(); i++) if (chars.charAt(i) != mirror.charAt(i)) return null;
        }
        // The physically qualified shape is a single URI, without whitespace or prose wrapping.
        for (int i = 0; i < chars.length(); i++) {
            if (Character.isWhitespace(chars.charAt(i)) || Character.isISOControl(chars.charAt(i))) return null;
        }
        char[] copy = new char[chars.length()];
        try {
            for (int i = 0; i < copy.length; i++) copy[i] = chars.charAt(i);
            String text = new String(copy);
            return envelope(Uri.parse(text)) ? text : null;
        } finally { Arrays.fill(copy, '\0'); }
    }
    private static boolean envelope(Uri data) {
        return data != null && data.isHierarchical() && "otpauth".equalsIgnoreCase(data.getScheme())
                && "totp".equalsIgnoreCase(data.getEncodedAuthority()) && data.getFragment() == null;
    }
    private static boolean plain(CharSequence chars) {
        return chars.length() > 0 && chars.length() <= OtpAuthUriParser.MAX_INPUT_UNITS
                && (!(chars instanceof Spanned)
                    || ((Spanned) chars).getSpans(0, chars.length(), Object.class).length == 0);
    }
    static void clear(Intent incoming) {
        if (incoming == null) return;
        try { incoming.removeExtra(Intent.EXTRA_TEXT); } catch (RuntimeException ignored) { }
        incoming.replaceExtras((Bundle) null);
        incoming.setData(null);
        incoming.setClipData(null);
        incoming.setSelector(null);
    }
}
