package org.totipo.android;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.os.Build;
import android.os.PersistableBundle;

/** Main-thread platform adapter. Conditional cleanup is best effort, not atomic. */
final class PlatformCodeClipboard implements TotpPresentation.Clipboard {
    static final String OWNER = "org.totipo.android.COPY_OWNER";
    private final ClipboardManager clipboard;
    PlatformCodeClipboard(ClipboardManager clipboard) { this.clipboard = clipboard; }
    static ClipData clip(String marker, String text) {
        ClipData clip = ClipData.newPlainText("Totipo code", text);
        PersistableBundle extras = new PersistableBundle();
        extras.putString(OWNER, marker);
        extras.putBoolean(Build.VERSION.SDK_INT >= 33 ? ClipDescription.EXTRA_IS_SENSITIVE
                : "android.content.extra.IS_SENSITIVE", true);
        clip.getDescription().setExtras(extras);
        return clip;
    }
    static boolean matches(String marker, String text, String actualMarker, CharSequence actualText, boolean simple) {
        return simple && marker.equals(actualMarker) && actualText != null && text.contentEquals(actualText);
    }
    public boolean copy(String marker, String text) {
        try {
            clipboard.setPrimaryClip(clip(marker, text)); return true;
        } catch (RuntimeException unavailable) { return false; }
    }
    public void clearIfOwned(String marker, String text) {
        try {
            ClipData clip = clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() != 1) return;
            var description = clip.getDescription();
            var extras = description.getExtras();
            var item = clip.getItemAt(0);
            if (!matches(marker, text, extras == null ? null : extras.getString(OWNER), item.getText(),
                    description.getMimeTypeCount() == 1 && description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
                    && item.getUri() == null && item.getIntent() == null && item.getHtmlText() == null)) return;
            // Global clipboard may change between this comparison and clear; Android has no CAS.
            if (Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip();
        } catch (RuntimeException unavailable) { /* No ownership established: skip cleanup. */ }
    }
}
