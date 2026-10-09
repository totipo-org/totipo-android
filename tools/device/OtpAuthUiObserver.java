package org.totipo.ingresstest;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;

/** Reads only fixed public-fixture/fixed-product label matches. No raw UI tree or camera capture. */
public final class OtpAuthUiObserver extends Instrumentation {
    private final boolean[] facts = new boolean[14];
    private static final String[] LABELS = {"Add to Totipo", "Totipo Camera Test", "test@example.invalid",
            "SHA1", "6", "30", "Token added", "Hide code", "Unlock Vault",
            "Unlock Totipo, then scan or share the QR again.", "Enrollment expired. Scan or share the QR again.", "Show code",
            "Totipo Camera Test\ntest@example.invalid\nActive", "Totipo Camera Test — test@example.invalid"};
    private void scan(AccessibilityNodeInfo node) {
        try {
            CharSequence text = node.isPassword() ? null : node.getText();
            if (text != null) for (int i = 0; i < LABELS.length; i++) if (LABELS[i].contentEquals(text)) facts[i] = true;
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i); if (child != null) scan(child);
            }
        } finally { node.recycle(); }
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        boolean totipo = false;
        UiAutomation ui = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        for (int attempt = 0; attempt < 15 && !totipo; attempt++) {
            AccessibilityNodeInfo root = ui.getRootInActiveWindow();
            if (root != null) {
                if ("org.totipo.android".contentEquals(root.getPackageName() == null ? "" : root.getPackageName())) { totipo = true; scan(root); }
                else root.recycle();
            }
            if (!totipo) android.os.SystemClock.sleep(200);
        }
        Bundle result = new Bundle();
        result.putString("stream", "M2D_UI totipo=" + totipo + " confirmation=" + facts[0]
                + " public_metadata=" + (facts[1] && facts[2] && facts[3] && facts[4] && facts[5])
                + " public_token=" + ((facts[1] && facts[2]) || facts[12] || facts[13]) + " added=" + facts[6] + " revealed=" + facts[7]
                + " locked=" + facts[8] + " unlock_retry=" + facts[9] + " expired=" + facts[10] + " show=" + facts[11] + "\n");
        finish(Activity.RESULT_OK, result);
    }
}
