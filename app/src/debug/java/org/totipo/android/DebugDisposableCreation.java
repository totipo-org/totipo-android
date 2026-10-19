package org.totipo.android;

import android.content.Context;

/** A-MV1 holds process selection until restart. A disposable create in this process
 * would bypass Application admission. Use the isolated qualification harness instead.
 */
public final class DebugDisposableCreation {
    private DebugDisposableCreation() {}
    public static void run(Context context) {
        throw new IllegalStateException("Disposable creation is unavailable while application selection is held");
    }
}
