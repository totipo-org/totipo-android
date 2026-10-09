package org.totipo.safqualification;

import java.nio.file.*;
import org.totipo.*;
import org.totipo.storage.nio.NioTotipoStore;

/** Disposable public-data fixture authored by released Java; never opens the user's vault. */
public final class InboundFixture {
    private static void observed(VaultSession session, int tokens) throws Exception {
        long end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < end) {
            if (session.state().observation() instanceof ObservationProgress.Finished
                    && session.state().tokens().size() == tokens) return;
            Thread.sleep(5);
        }
        throw new AssertionError("Fixture observation timed out");
    }
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]); Files.createDirectories(root);
        try (var session = ((CreateVaultResult.Created) Totipo.create(NioTotipoStore.openPrivate(root), new char[0])).session()) {
            observed(session, 0);
            for (int i = 0; i < 3; i++) {
                try (var secret = NewSecret.copyOf(new byte[]{1, 2, 3, 4}); var editor = session.state().createToken()) {
                    if (!(editor.issuer("M3A public fixture").account("test " + i).secret(secret).save() instanceof SaveResult.Saved))
                        throw new AssertionError("Fixture authoring failed");
                }
                observed(session, i + 1);
            }
        }
    }
}
