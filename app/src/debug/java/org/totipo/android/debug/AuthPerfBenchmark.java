package org.totipo.android.debug;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.function.LongSupplier;

/** Synthetic BC-only diagnostic. No Android storage, product credentials or VAULT decoder.
 * BC is a runtime-only transitive dependency. Reflection preserves that dependency boundary;
 * method resolution happens once, outside measured invocations; no reflection in BC's loop. */
public final class AuthPerfBenchmark {
    public static final int MEMORY_KIB = 65536, ITERATIONS = 3, LANES = 4;
    public static final int TYPE = 2, VERSION = 0x13, SALT_BYTES = 16, OUTPUT_BYTES = 32;
    private static final String TEST_PASSWORD = "M1K disposable synthetic password";
    // SHA-256 of the ORIGINAL M1K probe's fixed synthetic 64/3/4 output, BC 1.86.
    // This is a public synthetic reference, never a credential or derived-key cache.
    private static final String EXPECTED_SYNTHETIC_SHA256 =
            "30eb8bf0a90f2cd624a1d00aa7093e2c8f11968586718195043150ca6ce50bb1";
    static boolean matchesSyntheticOutput(byte[] output) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(output);
            try {
                StringBuilder hex = new StringBuilder(64);
                for (byte value : digest) {
                    hex.append(Character.forDigit((value >>> 4) & 15, 16));
                    hex.append(Character.forDigit(value & 15, 16));
                }
                return output.length == OUTPUT_BYTES && EXPECTED_SYNTHETIC_SHA256.contentEquals(hex);
            } finally { Arrays.fill(digest, (byte) 0); }
        } catch (NoSuchAlgorithmException failure) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    private final Constructor<?> builderConstructor, generatorConstructor;
    private final Method version, memory, iterations, lanes, salt, secret, additional, build;
    private final Method builderClear, parameterClear, init, generate;

    public AuthPerfBenchmark() throws ReflectiveOperationException {
        Class<?> parameters = Class.forName("org.bouncycastle.crypto.params.Argon2Parameters");
        Class<?> builder = Class.forName("org.bouncycastle.crypto.params.Argon2Parameters$Builder");
        Class<?> generator = Class.forName("org.bouncycastle.crypto.generators.Argon2BytesGenerator");
        builderConstructor = builder.getConstructor(int.class);
        generatorConstructor = generator.getConstructor();
        version = builder.getMethod("withVersion", int.class);
        memory = builder.getMethod("withMemoryAsKB", int.class);
        iterations = builder.getMethod("withIterations", int.class);
        lanes = builder.getMethod("withParallelism", int.class);
        salt = builder.getMethod("withSalt", byte[].class);
        secret = builder.getMethod("withSecret", byte[].class);
        additional = builder.getMethod("withAdditional", byte[].class);
        build = builder.getMethod("build");
        builderClear = builder.getMethod("clear");
        parameterClear = parameters.getMethod("clear");
        init = generator.getMethod("init", parameters);
        generate = generator.getMethod("generateBytes", byte[].class, byte[].class);
    }

    static byte[] syntheticPassword() { return TEST_PASSWORD.getBytes(StandardCharsets.UTF_8); }
    static byte[] syntheticSalt() {
        byte[] value = new byte[SALT_BYTES];
        for (int i = 0; i < value.length; i++) value[i] = (byte) i;
        return value;
    }
    private Object builder(int m, int t, int p, byte[] syntheticSalt) throws ReflectiveOperationException {
        Object value = builderConstructor.newInstance(TYPE);
        version.invoke(value, VERSION);
        memory.invoke(value, m);
        iterations.invoke(value, t);
        lanes.invoke(value, p);
        salt.invoke(value, (Object) syntheticSalt);
        secret.invoke(value, (Object) new byte[0]);
        additional.invoke(value, (Object) new byte[0]);
        return value;
    }
    // Test configuration without invoking a memory-intensive KDF.
    Object productionParameters() throws ReflectiveOperationException {
        byte[] value = syntheticSalt();
        Object builder = builder(MEMORY_KIB, ITERATIONS, LANES, value);
        try { return build.invoke(builder); }
        finally { builderClear.invoke(builder); Arrays.fill(value, (byte) 0); }
    }
    public record Timing(long setupNanos, long generateNanos, long totalNanos, int outputMatch) {}
    public Timing run(int m, int t, int p, LongSupplier clock) throws ReflectiveOperationException {
        // Restrict diagnostics to the documented small matrix, never arbitrary ADB parameters.
        if ((m != 16384 && m != 32768 && m != MEMORY_KIB)
                || (t != 1 && t != ITERATIONS) || (p != 1 && p != LANES)) {
            throw new IllegalArgumentException("Unsupported diagnostic configuration");
        }
        long start = clock.getAsLong();
        byte[] password = syntheticPassword(), saltBytes = syntheticSalt(), key = new byte[OUTPUT_BYTES];
        Object builder = null, parameters = null;
        long setupEnd, generateEnd, totalEnd;
        int outputMatch = -1;
        byte[] verificationOutput = null;
        try {
            builder = builder(m, t, p, saltBytes);
            parameters = build.invoke(builder);
            Object generator = generatorConstructor.newInstance();
            init.invoke(generator, parameters);
            setupEnd = clock.getAsLong();
            generate.invoke(generator, password, key);
            generateEnd = clock.getAsLong();
            if (m == MEMORY_KIB && t == ITERATIONS && p == LANES) verificationOutput = key.clone();
        } finally {
            Arrays.fill(password, (byte) 0); Arrays.fill(saltBytes, (byte) 0); Arrays.fill(key, (byte) 0);
            if (builder != null) builderClear.invoke(builder);
            if (parameters != null) parameterClear.invoke(parameters);
        }
        totalEnd = clock.getAsLong();
        if (verificationOutput != null) {
            try {
                if (!matchesSyntheticOutput(verificationOutput)) {
                    throw new IllegalStateException("Synthetic output mismatch");
                }
                outputMatch = 1;
            } finally { Arrays.fill(verificationOutput, (byte) 0); }
        }
        return new Timing(setupEnd - start, generateEnd - setupEnd, totalEnd - start, outputMatch);
    }
}
