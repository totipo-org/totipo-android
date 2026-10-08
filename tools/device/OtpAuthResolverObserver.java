package org.totipo.qualification;

import android.app.Instrumentation;
import android.app.Activity;
import android.app.UiAutomation;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;

/** Selects only Totipo in the direct public-fixture resolver; emits fixed labels only. */
public final class OtpAuthResolverObserver extends Instrumentation {
    private static boolean totipoSelected;
    private static boolean click(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = AccessibilityNodeInfo.obtain(node);
        try {
            while (current != null) {
                if (current.isClickable()) return current.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                AccessibilityNodeInfo parent = current.getParent(); current.recycle(); current = parent;
            }
            return false;
        } finally { if (current != null) current.recycle(); }
    }
    private static int scan(AccessibilityNodeInfo node, boolean resolver) {
        try {
            CharSequence label = node.getText();
            if (label != null) {
                if (!resolver && "otpauth TOTP share received".contentEquals(label)) return 2;
                if (resolver && ("Totipo otpauth transport probe".contentEquals(label)
                        || "Totipo".contentEquals(label))) {
                    if (click(node)) { totipoSelected = true; return 1; }
                }
                if (resolver && totipoSelected && "Just once".contentEquals(label)) {
                    if (click(node)) return 1;
                }
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) { int result = scan(child, resolver); if (result != 0) return result; }
            }
            return 0;
        } finally { node.recycle(); }
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        UiAutomation ui = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        long deadline = SystemClock.uptimeMillis() + 15000;
        boolean success = false;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = ui.getRootInActiveWindow();
            if (root != null) {
                CharSequence pkg = root.getPackageName();
                boolean resolver = "android".contentEquals(pkg == null ? "" : pkg)
                        || "com.android.intentresolver".contentEquals(pkg == null ? "" : pkg);
                if (resolver || "org.totipo.android".contentEquals(pkg == null ? "" : pkg)) {
                    if (scan(root, resolver) == 2) { success = true; break; }
                } else root.recycle();
            }
            SystemClock.sleep(250);
        }
        Bundle result = new Bundle(); result.putString("stream", success
                ? "M2C2_DIRECT_LABEL otpauth TOTP share received\n" : "M2C2_DIRECT_LABEL UNOBSERVED\n");
        finish(success ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
}
