package org.totipo.android.debug;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.widget.TextView;
import android.view.WindowManager;
import java.util.concurrent.atomic.AtomicBoolean;
import org.totipo.android.DebugDisposableCreation;
import org.totipo.android.LocalReplicaOwner;
import org.totipo.android.reconcile.DebugVaultTiming;

/** ADB entrypoint with fixed actions; credentials are never accepted through intents. */
public final class AuthPerfActivity extends Activity {
    private static final String TAG = "TotipoAuthPerf";
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView status = new TextView(this);
        status.setText("Authentication diagnostics: wait for done=1 in logcat.\nDo not unlock while a probe runs.");
        setContentView(status);
        startProbe();
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        startProbe();
    }
    private void startProbe() {
        if (!RUNNING.compareAndSet(false, true)) { Log.i(TAG, "busy=1"); return; }
        // This fixed-label debug screen exposes no credentials, keys or vault content.
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        String action = getIntent().getStringExtra("probe");
        new Thread(() -> {
            try {
                // Only standard non-sensitive device properties; no identifiers or paths.
                Log.i(TAG, "device model=" + Build.MODEL + " api=" + Build.VERSION.SDK_INT
                        + " android=" + Build.VERSION.RELEASE + " abi=" + Build.SUPPORTED_ABIS[0]);
                if ("read".equals(action)) DebugVaultTiming.readVault(LocalReplicaOwner.from(this));
                else if ("create".equals(action)) DebugDisposableCreation.run(this);
                else if ("matrix".equals(action)) matrix(new AuthPerfBenchmark());
                else if (action == null || "production".equals(action)) production(new AuthPerfBenchmark());
                else { Log.i(TAG, "unsupported=1"); return; }
                Log.i(TAG, "done=1");
            } catch (Exception | OutOfMemoryError failure) {
                // Never log exception text: it could include filesystem or crypto context.
                Log.i(TAG, "failed=1");
            } finally {
                runOnUiThread(() -> {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    if (Build.VERSION.SDK_INT >= 27) {
                        setShowWhenLocked(false);
                        setTurnScreenOn(false);
                    }
                    RUNNING.set(false);
                });
            }
        }, "totipo-auth-perf").start();
    }
    private static void production(AuthPerfBenchmark benchmark) throws ReflectiveOperationException {
        for (int iteration = 0; iteration <= 3; iteration++) {
            Log.i(TAG, "argon2 production begin=" + iteration);
            AuthPerfBenchmark.Timing result = benchmark.run(AuthPerfBenchmark.MEMORY_KIB,
                    AuthPerfBenchmark.ITERATIONS, AuthPerfBenchmark.LANES, SystemClock::elapsedRealtimeNanos);
            String label = iteration == 0 ? "argon2 warmup iter=0" : "argon2 production iter=" + iteration;
            timing(label, result);
        }
    }
    private static void matrix(AuthPerfBenchmark benchmark) throws ReflectiveOperationException {
        // Production 64/3/4 is already measured above. Four extra calls isolate each axis.
        int[][] configurations = {{16384, 3, 4}, {32768, 3, 4}, {65536, 1, 4}, {65536, 3, 1}};
        for (int[] configuration : configurations) {
            String label = "argon2 m=" + configuration[0] + " t=" + configuration[1] + " p=" + configuration[2];
            Log.i(TAG, label + " begin=1");
            timing(label, benchmark.run(configuration[0], configuration[1], configuration[2], SystemClock::elapsedRealtimeNanos));
        }
    }
    private static void timing(String label, AuthPerfBenchmark.Timing timing) {
        Log.i(TAG, label + " setup_ms=" + timing.setupNanos() / 1_000_000.0
                + " generate_ms=" + timing.generateNanos() / 1_000_000.0
                + " elapsed_ms=" + timing.totalNanos() / 1_000_000.0
                + (timing.outputMatch() == 1 ? " output_match=1" : ""));
    }
}
