package org.totipo.android.reconcile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import org.totipo.*;
import org.totipo.android.LocalReplicaOwner;

/** Explicit debug-only one-shot fixture request. Uses public Java authorship, never encoding. */
public final class DebugTotpFixture {
    private static final AtomicBoolean ARMED = new AtomicBoolean();
    private DebugTotpFixture() {}
    public static void arm() { ARMED.set(true); }
    public static boolean takeRequest() { return ARMED.getAndSet(false); }
    public static ForegroundVaultCoordinator.Opening open(LocalReplicaOwner owner, char[] credential) throws IOException {
        return ForegroundVaultCoordinator.open(owner, credential, new Seed());
    }
    public static ForegroundVaultCoordinator.Creation create(LocalReplicaOwner owner, char[] credential) throws IOException {
        return ForegroundVaultCoordinator.create(owner, credential, new Seed());
    }
    private static final class Seed extends ForegroundVaultCoordinator.Operations {
        @Override void observe(VaultSession session) throws Exception {
            super.observe(session);
            byte[] publicTestSecret = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
            try (var secret = NewSecret.copyOf(publicTestSecret); var editor = session.state().createToken()) {
                if (!(editor.issuer("RFC 6238 disposable").account("Public test fixture")
                        .algorithm(TotpAlgorithm.SHA1).digits(8).period(Duration.ofSeconds(30))
                        .secret(secret).save() instanceof SaveResult.Saved))
                    throw new IllegalStateException("Disposable fixture unavailable");
            } finally { Arrays.fill(publicTestSecret, (byte) 0); }
            super.observe(session);
        }
    }
}
