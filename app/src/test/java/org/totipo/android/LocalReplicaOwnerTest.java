package org.totipo.android;

import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public final class LocalReplicaOwnerTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void rootIsStableAndExclusiveAcrossFlows() throws Exception {
        var base = temporary.getRoot().toPath();
        var owner = new LocalReplicaOwner(base);
        var first = owner.acquire();
        assertEquals(base.resolve("totipo-vault"), first.root());
        assertTrue(Files.isDirectory(first.root()));
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            assertTrue(worker.submit(() -> {
                try (var unexpected = owner.acquire()) { return false; }
                catch (IllegalStateException expected) { return true; }
            }).get(5, TimeUnit.SECONDS));
        } finally { worker.shutdownNow(); first.close(); }
        try (var second = owner.acquire()) {
            first.close(); // A stale close cannot release a newer lease.
            assertThrows(IllegalStateException.class, owner::acquire);
            assertThrows(IllegalStateException.class, first::root);
            assertEquals(base.resolve("totipo-vault"), second.root());
        }
        try (var restartedOwnerLease = new LocalReplicaOwner(base).acquire()) {
            assertEquals(base.resolve("totipo-vault"), restartedOwnerLease.root());
        }
    }

    @Test public void failedDirectoryCreationDoesNotConsumeLease() throws Exception {
        var base = temporary.getRoot().toPath();
        var root = base.resolve("totipo-vault");
        Files.write(root, new byte[] {1});
        var owner = new LocalReplicaOwner(base);
        assertThrows(java.io.IOException.class, owner::acquire);
        Files.delete(root);
        try (var lease = owner.acquire()) { assertEquals(root, lease.root()); }
    }
}
