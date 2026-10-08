package org.totipo.android.reconcile;

import android.util.Log;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.totipo.*;
import org.totipo.android.LocalReplicaOwner;

/** Debug-only elapsed phases, never credentials, paths, IDs, descriptors or exception text.
 * Times the released Java entry point as a whole, not its internal KDF in isolation. */
public final class DebugVaultTiming {
    private static final String TAG = "TotipoVaultTiming";
    private DebugVaultTiming() {}
    public static ForegroundVaultCoordinator.Opening open(LocalReplicaOwner owner, char[] credential) throws IOException {
        long start = System.nanoTime();
        try { return ForegroundVaultCoordinator.open(owner, credential, new TimedOperations("unlock")); }
        finally { elapsed("unlock", "coordinator_total", start); }
    }
    public static ForegroundVaultCoordinator.Creation create(LocalReplicaOwner owner, char[] credential) throws IOException {
        long start = System.nanoTime();
        try { return ForegroundVaultCoordinator.create(owner, credential, new TimedOperations("create")); }
        finally { elapsed("create", "coordinator_total", start); }
    }
    private static void elapsed(String operation, String phase, long start) {
        Log.i(TAG, operation + " phase=" + phase + " elapsed_ms="
                + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
    }
    private static final class TimedOperations extends ForegroundVaultCoordinator.Operations {
        private final String operation;
        TimedOperations(String operation) { this.operation = operation; }
        @Override CoordinatedPrivateStore storage(LocalReplicaOwner.Lease lease) throws IOException {
            long start = System.nanoTime();
            try { return super.storage(lease); }
            finally { elapsed(operation, "storage_open", start); }
        }
        @Override OpenResult open(CoordinatedPrivateStore store, char[] credential) {
            long start = System.nanoTime();
            try { return super.open(store, credential); }
            finally { elapsed(operation, "java_call", start); }
        }
        @Override CreateVaultResult create(CoordinatedPrivateStore store, char[] credential) {
            long start = System.nanoTime();
            try { return super.create(store, credential); }
            finally { elapsed(operation, "java_call", start); }
        }
        @Override void observe(VaultSession session) throws Exception {
            long start = System.nanoTime();
            try { super.observe(session); }
            finally { elapsed(operation, "initial_observation_wait", start); }
        }
    }
}
