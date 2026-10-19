package org.totipo.android;

import java.nio.file.Files;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public final class LocalReplicaOwnerTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test public void rootIsStableAndExclusiveAcrossFlows() throws Exception {
        var registry = new VaultRegistry(temporary.getRoot().toPath());
        var entry = registry.reserve(null);
        var authority = new LocalReplicaOwner.Admission();
        var owner = authority.select(registry, entry.id());
        var first = owner.acquire();
        var root = registry.root(entry.id());
        assertEquals(root, first.root());
        var worker = Executors.newSingleThreadExecutor();
        try {
            assertTrue(worker.submit(() -> {
                try (var ignored = owner.acquire()) { return false; }
                catch (IllegalStateException expected) { return true; }
            }).get(5, TimeUnit.SECONDS));
        } finally { worker.shutdownNow(); first.close(); }
        try (var second = owner.acquire()) {
            first.close();
            assertThrows(IllegalStateException.class, owner::acquire);
            assertThrows(IllegalStateException.class, first::root);
            assertEquals(root, second.root());
        }
        var other = registry.reserve("Second");
        assertThrows(IllegalStateException.class, () -> authority.select(registry, other.id()));
        assertSame(owner, authority.select(registry, entry.id()));
        // A locked controller still owns the selection; no second-root session is admitted.
    }
    @Test public void missingRootIsNeverRecreatedAndDoesNotConsumeLease() throws Exception {
        var registry = new VaultRegistry(temporary.getRoot().toPath());
        var entry = registry.reserve(null);
        var owner = new LocalReplicaOwner.Admission().select(registry, entry.id());
        Files.delete(registry.root(entry.id()));
        assertThrows(java.io.IOException.class, owner::acquire);
        assertFalse(Files.exists(registry.root(entry.id())));
        registry.allocatePending(entry.id()); // Explicit recovery only, before any creation attempt.
        try (var ignored = owner.acquire()) { assertNotNull(ignored.root()); }
    }
}
