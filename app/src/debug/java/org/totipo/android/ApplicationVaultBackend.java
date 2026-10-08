package org.totipo.android;

import java.io.IOException;
import org.totipo.android.reconcile.DebugVaultTiming;
import org.totipo.android.reconcile.ForegroundVaultCoordinator;
import org.totipo.android.reconcile.DebugTotpFixture;

/** Debug-only timing around the actual product path; no alternate storage or session. */
final class ApplicationVaultBackend extends AndroidVaultController.Backend {
    @Override ForegroundVaultCoordinator.Opening open(LocalReplicaOwner owner, char[] credential) throws IOException {
        if (DebugTotpFixture.takeRequest()) return DebugTotpFixture.open(owner, credential);
        return DebugVaultTiming.open(owner, credential);
    }
    @Override ForegroundVaultCoordinator.Creation create(LocalReplicaOwner owner, char[] credential) throws IOException {
        if (DebugTotpFixture.takeRequest()) return DebugTotpFixture.create(owner, credential);
        return DebugVaultTiming.create(owner, credential);
    }
}
