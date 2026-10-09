package org.totipo.android.debug;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Configuration/equivalence/security guards; no timing thresholds. */
public final class AuthPerfSafetyTest {
    @Test public void exactProductionInvocationMatchesOriginalSyntheticOutput() throws Exception {
        var timing = new AuthPerfBenchmark().run(65536, 3, 4, System::nanoTime);
        assertEquals(1, timing.outputMatch());
        assertFalse(AuthPerfBenchmark.matchesSyntheticOutput(new byte[32]));
        assertFalse(AuthPerfBenchmark.matchesSyntheticOutput(new byte[0]));
    }
    @Test public void appSourcesNeverExecuteCompilerOrGlobalRuntimeControls() throws Exception {
        for (String sourceSet : List.of("main", "release", "debug")) {
            try (var paths = Files.walk(Path.of("src/" + sourceSet))) {
                for (Path path : paths.filter(Files::isRegularFile).filter(p -> !p.toString().endsWith(".png")).toList()) {
                    String source = Files.readString(path);
                    for (String forbidden : List.of("ProcessBuilder", "Runtime.getRuntime().exec", "cmd package",
                            "pm compile", "setprop", "SystemProperties.set", "dalvik.vm.", "EXPECTED_SYNTHETIC_SHA256")) {
                        if (forbidden.equals("EXPECTED_SYNTHETIC_SHA256") && sourceSet.equals("debug")) continue;
                        assertFalse(path + ": " + forbidden, source.contains(forbidden));
                    }
                }
            }
        }
    }
    @Test public void parametersMatchInspectedReleased020Invocation() throws Exception {
        // Released sources SHA256 e2661770e0e59ca733959b4931689a9253988352605087c390bfc3a045dd9b6c.
        var benchmark = new AuthPerfBenchmark();
        Object parameters = benchmark.productionParameters();
        Class<?> type = parameters.getClass();
        try {
            assertEquals(2, type.getMethod("getType").invoke(parameters));
            assertEquals(0x13, type.getMethod("getVersion").invoke(parameters));
            assertEquals(65536, type.getMethod("getMemory").invoke(parameters));
            assertEquals(3, type.getMethod("getIterations").invoke(parameters));
            assertEquals(4, type.getMethod("getLanes").invoke(parameters));
            assertEquals(16, ((byte[]) type.getMethod("getSalt").invoke(parameters)).length);
            assertEquals(0, ((byte[]) type.getMethod("getSecret").invoke(parameters)).length);
            assertEquals(0, ((byte[]) type.getMethod("getAdditional").invoke(parameters)).length);
            assertNull(type.getMethod("getBlockPool").invoke(parameters));
            assertEquals(32, AuthPerfBenchmark.OUTPUT_BYTES);
            assertEquals(16, AuthPerfBenchmark.SALT_BYTES);
        } finally { type.getMethod("clear").invoke(parameters); }
    }
    @Test public void inputsAreFixedSyntheticFreshArrays() {
        assertArrayEquals("M1K disposable synthetic password".getBytes(StandardCharsets.UTF_8), AuthPerfBenchmark.syntheticPassword());
        assertArrayEquals(new byte[]{0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15}, AuthPerfBenchmark.syntheticSalt());
        byte[] password = AuthPerfBenchmark.syntheticPassword(), salt = AuthPerfBenchmark.syntheticSalt();
        password[0] = 0; salt[0] = 99;
        assertEquals('M', AuthPerfBenchmark.syntheticPassword()[0]);
        assertEquals(0, AuthPerfBenchmark.syntheticSalt()[0]);
    }
    @Test public void diagnosticsStayInDebugWithNoCredentialStorageOrProductionDependency() throws Exception {
        String benchmark = Files.readString(Path.of("src/debug/java/org/totipo/android/debug/AuthPerfBenchmark.java"));
        for (String forbidden : List.of("org.totipo.format", "LocalReplicaOwner", "Totipo.open", "Totipo.create",
                "readVault", "SharedPreferences", "getIntent", "java.nio.file", "android.util.Log", "Cipher")) {
            assertFalse(forbidden, benchmark.contains(forbidden));
        }
        assertTrue(benchmark.contains("getMethod(\"generateBytes\", byte[].class, byte[].class)"));
        assertTrue(benchmark.contains("generatorConstructor.newInstance()"));
        assertTrue(benchmark.contains("Arrays.fill(key, (byte) 0)"));
        for (String sourceSet : List.of("main", "release")) {
            try (var paths = Files.walk(Path.of("src/" + sourceSet))) {
                for (Path path : paths.filter(Files::isRegularFile).filter(p -> !p.toString().endsWith(".png")).toList()) {
                    String source = Files.readString(path);
                    for (String forbidden : List.of("AuthPerf", "DebugDisposableCreation", "DebugVaultTiming", "TotipoVaultTiming")) {
                        assertFalse(path + ": " + forbidden, source.contains(forbidden));
                    }
                }
            }
        }
        String verifier = Files.readString(Path.of("../tools/verify-apk.py"));
        for (String required : List.of("TotipoAuthPerf", "AuthPerfBenchmark", "AuthPerfActivity", "DebugDisposableCreation", "TotipoVaultTiming")) {
            assertTrue(required, verifier.contains(required));
        }
    }
    @Test public void loggingUsesFixedLabelsAndNumericResultsWithDevicePropertiesOnly() throws Exception {
        String activity = Files.readString(Path.of("src/debug/java/org/totipo/android/debug/AuthPerfActivity.java"));
        String timing = Files.readString(Path.of("src/debug/java/org/totipo/android/reconcile/DebugVaultTiming.java"));
        for (String source : List.of(activity, timing)) {
            for (String forbidden : List.of("getMessage()", "getStackTrace", "Log.e", "Log.w", "credential.toString",
                    "Arrays.toString", "vaultId()", "getSerial", "getStringExtra(\"password\"")) {
                assertFalse(forbidden, source.contains(forbidden));
            }
            assertTrue(source.contains("SystemClock.elapsedRealtimeNanos()") || source.contains("SystemClock::elapsedRealtimeNanos"));
        }
        assertTrue(activity.contains("1; iteration <= 3") || activity.contains("0; iteration <= 3"));
        assertTrue(activity.contains("Log.i(TAG, \"failed=1\")"));
        assertFalse(activity.contains("Log.i(TAG, action"));
        assertTrue(activity.contains("addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)"));
        assertTrue(activity.contains("clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)"));
        assertTrue(activity.contains("setShowWhenLocked(true)"));
        assertTrue(activity.contains("setShowWhenLocked(false)"));
        assertTrue(activity.contains("setTurnScreenOn(true)"));
        assertTrue(activity.contains("setTurnScreenOn(false)"));
        assertTrue(activity.contains("onNewIntent(Intent intent)"));
        assertTrue(activity.contains("setIntent(intent)"));
        assertTrue(timing.contains("authElapsed(\"open correct\", start)"));
        assertTrue(timing.contains("authElapsed(\"open wrong\", start)"));
    }
}
