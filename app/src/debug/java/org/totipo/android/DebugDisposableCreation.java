package org.totipo.android;

import android.content.Context;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import org.totipo.android.reconcile.DebugVaultTiming;

/** One synthetic create in an exclusively owned fresh cache directory; never clears app data. */
public final class DebugDisposableCreation {
    private DebugDisposableCreation() {}
    public static void run(Context context) throws Exception {
        Path directory = Files.createTempDirectory(context.getCacheDir().toPath(), "m1k-auth-");
        char[] password = "M1K disposable synthetic password".toCharArray();
        try {
            var result = DebugVaultTiming.create(new LocalReplicaOwner(directory), password);
            try { if (result.failure() != null || result.cause() != null) throw new IllegalStateException("Diagnostic create failed"); }
            finally { result.vault().close(); }
        } finally {
            Arrays.fill(password, '\0');
            try (var entries = Files.walk(directory)) {
                var iterator = entries.sorted(Comparator.reverseOrder()).iterator();
                while (iterator.hasNext()) Files.delete(iterator.next());
            }
        }
    }
}
