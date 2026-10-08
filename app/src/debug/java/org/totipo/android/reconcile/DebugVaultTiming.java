package org.totipo.android.reconcile;

import android.util.Log;
import java.io.IOException;
import android.os.SystemClock;
import org.totipo.spi.BoundedRead;
import org.totipo.*;
import org.totipo.android.LocalReplicaOwner;

/** Debug-only elapsed phases, never credentials, paths, IDs, descriptors or exception text.
 * Times the released Java entry point as a whole, not its internal KDF in isolation. */
public final class DebugVaultTiming {
    private static final String TAG = "TotipoVaultTiming";
    private DebugVaultTiming() {}
    public static ForegroundVaultCoordinator.Opening open(LocalReplicaOwner owner, char[] credential) throws IOException {
        long start = SystemClock.elapsedRealtimeNanos();
        try { return ForegroundVaultCoordinator.open(owner, credential, new TimedOperations("unlock")); }
        finally { elapsed("unlock", "coordinator_total", start); }
    }
    public static ForegroundVaultCoordinator.Creation create(LocalReplicaOwner owner, char[] credential) throws IOException {
        long start = SystemClock.elapsedRealtimeNanos();
        try { return ForegroundVaultCoordinator.create(owner, credential, new TimedOperations("create")); }
        finally { elapsed("create", "coordinator_total", start); }
    }
    private static void elapsed(String operation, String phase, long start) {
        Log.i(TAG, operation + " phase=" + phase + " elapsed_ms="
                + (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0);
    }
    private static void authElapsed(String label, long start) {
        Log.i("TotipoAuthPerf", label + " elapsed_ms="
                + (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0);
    }
    /** The exact owner/domain/read used by unlock, without authenticating or retaining bytes. */
    public static void readVault(LocalReplicaOwner owner) throws IOException {
        for (int iteration = 1; iteration <= 5; iteration++) {
            long start = SystemClock.elapsedRealtimeNanos();
            try (var lease = owner.acquire()) {
                long setup = SystemClock.elapsedRealtimeNanos();
                try (var store = CoordinatedPrivateStore.open(lease.root())) {
                    authElapsed("vault_read storage_open iter=" + iteration, setup);
                    long read = SystemClock.elapsedRealtimeNanos();
                    BoundedRead result = store.observeVault();
                    authElapsed("vault_read iter=" + iteration, read);
                    Log.i("TotipoAuthPerf", "vault_read present=" + (result instanceof BoundedRead.Present ? 1 : 0));
                }
            }
            authElapsed("vault_read total iter=" + iteration, start);
        }
    }
    private static final class TimedOperations extends ForegroundVaultCoordinator.Operations {
        private final String operation;
        TimedOperations(String operation) { this.operation = operation; }
        @Override CoordinatedPrivateStore storage(LocalReplicaOwner.Lease lease) throws IOException {
            long start = SystemClock.elapsedRealtimeNanos();
            try { return super.storage(lease); }
            finally { elapsed(operation, "storage_open", start); }
        }
        @Override OpenResult open(CoordinatedPrivateStore store, char[] credential) {
            long start = SystemClock.elapsedRealtimeNanos();
            try {
                OpenResult result = super.open(store, credential);
                if (result instanceof OpenResult.Opened) authElapsed("open correct", start);
                else if (result instanceof OpenResult.AuthenticationFailed) authElapsed("open wrong", start);
                else android.util.Log.i("TotipoAuthPerf", "open other result=1");
                return result;
            }
            finally { elapsed(operation, "java_call", start); }
        }
        @Override CreateVaultResult create(CoordinatedPrivateStore store, char[] credential) {
            long start = SystemClock.elapsedRealtimeNanos();
            try {
                CreateVaultResult result = super.create(store, credential);
                if (result instanceof CreateVaultResult.Created) authElapsed("create", start);
                else android.util.Log.i("TotipoAuthPerf", "create other result=1");
                return result;
            }
            finally { elapsed(operation, "java_call", start); }
        }
        @Override void observe(VaultSession session) throws Exception {
            long start = SystemClock.elapsedRealtimeNanos();
            try { super.observe(session); }
            finally { elapsed(operation, "initial_observation_wait", start); }
        }
    }
}
