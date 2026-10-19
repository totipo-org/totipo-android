package org.totipo.android;

import android.app.Instrumentation;
import android.os.Bundle;
import java.nio.file.*;
import java.util.Comparator;
import org.totipo.android.reconcile.ForegroundVaultCoordinator;

/** Isolated release-DEX storage qualification, with sequential real Java sessions only.
 * No user app installation, legacy data, provider, biometric credential or key is touched.
 */
public final class VaultRegistryRegression extends Instrumentation {
    private static void check(boolean condition) { if (!condition) throw new AssertionError("Registry qualification assertion failed"); }
    private interface Checked { void run() throws Exception; }
    private static void refuses(Checked action) throws Exception {
        try { action.run(); } catch (java.io.IOException | IllegalStateException expected) { return; }
        throw new AssertionError("Unsafe action was admitted");
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle(); int code = 0; Path fixture = null;
        try {
            // The target is an isolated qualification package, never org.totipo.android.
            check(getTargetContext().getPackageName().equals("org.totipo.securityqualification"));
            fixture = Files.createTempDirectory(getTargetContext().getNoBackupFilesDir().toPath(), "mv1-fixture-").toRealPath();
            var registry = new VaultRegistry(fixture);
            check(registry.entries().isEmpty());
            var first = registry.reserve("First");
            var second = registry.reserve("Second");
            check(!registry.root(first.id()).startsWith(registry.root(second.id())));
            var authority = new LocalReplicaOwner.Admission();
            var owner = authority.select(registry, first.id());
            refuses(() -> authority.select(registry, second.id()));
            check(ForegroundVaultCoordinator.discover(owner).status() == ForegroundVaultCoordinator.LocalStatus.ABSENT);
            var created = ForegroundVaultCoordinator.create(owner, "Public registry fixture".toCharArray());
            try { check(created.failure() == null && created.cause() == null); refuses(owner::acquire); }
            finally { created.vault().close(); }
            var restart = new VaultRegistry(fixture);
            check(restart.entries().get(0).state() == VaultRegistry.State.ESTABLISHED);
            var remembered = new LocalReplicaOwner.Admission().select(restart, first.id());
            var opened = ForegroundVaultCoordinator.open(remembered, "Public registry fixture".toCharArray());
            try { check(opened.failure() == null && opened.cause() == null); }
            finally { opened.vault().close(); }
            Files.delete(restart.root(first.id()).resolve("vault"));
            check(ForegroundVaultCoordinator.discover(remembered).status() == ForegroundVaultCoordinator.LocalStatus.UNAVAILABLE);
            refuses(remembered::acquireForCreation);
            Files.delete(restart.root(first.id())); refuses(remembered::acquire);
            check(!Files.exists(restart.root(first.id())));
            // Symlink refusal on the real Android private filesystem, including parent escape.
            Path secondRoot = restart.root(second.id()); Files.delete(secondRoot);
            Files.createSymbolicLink(secondRoot, fixture);
            refuses(() -> restart.validateRoot(second.id()));
            // Corruption and interrupted writes never reset or discard roots.
            Path metadata = fixture.resolve("totipo-local-v1/registry");
            byte[] good = Files.readAllBytes(metadata); Files.write(metadata, new byte[]{1, 2});
            Path stableFixture = fixture;
            refuses(() -> new VaultRegistry(stableFixture)); Files.write(metadata, good);
            Files.write(fixture.resolve("totipo-local-v1/registry.next"), new byte[]{1});
            refuses(() -> new VaultRegistry(stableFixture));
            result.putString("stream", "VAULT_REGISTRY_PASS\n"); code = -1;
        } catch (Throwable failure) {
            // No exception/path/credential text leaves the fixture.
            result.putString("stream", "VAULT_REGISTRY_FAIL\n");
        } finally {
            if (fixture != null) try (var paths = Files.walk(fixture)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).collect(java.util.stream.Collectors.toList())) Files.delete(path);
            } catch (Exception failure) { result.putString("stream", "VAULT_REGISTRY_CLEANUP_FAIL\n"); code = 0; }
            finish(code, result);
        }
    }
}
