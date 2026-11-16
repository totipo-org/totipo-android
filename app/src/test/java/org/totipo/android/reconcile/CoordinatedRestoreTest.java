package org.totipo.android.reconcile;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.totipo.backup.*;
import org.totipo.storage.nio.*;
import static org.junit.Assert.*;

public final class CoordinatedRestoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private BackupRestore plan() throws IOException {
        try (var input = getClass().getResourceAsStream("/backup-profile-1/history.zip")) {
            return BackupArchives.prepareRestore(input, BackupLimits.DEFAULT, "fixture password".toCharArray());
        }
    }
    @Test public void durabilityFailuresPreserveDistinctUncertaintyAndExactRecovery() throws Exception {
        for (String phase : List.of("location", "bootstrap", "object-1", "object-2", "object-3")) {
            Path root = temporary.getRoot().toPath().resolve(phase); final int[] roots = {0}, objects = {0};
            StorageDurability durability = path -> {
                new NioDurability().syncDirectory(path);
                if (path.equals(root)) {
                    roots[0]++;
                    if ((phase.equals("location") && roots[0] == 1) || (phase.equals("bootstrap") && roots[0] == 2)) throw new IOException("lost barrier");
                }
                if (phase.startsWith("object-") && path.getFileName().toString().equals("objects-v1")
                        && ++objects[0] == Integer.parseInt(phase.substring(7))) throw new IOException("lost object barrier");
            };
            try (var plan = plan()) {
                var result = CoordinatedPrivateStore.restore(root, plan, false, durability);
                assertEquals(phase, RestoreResult.Outcome.UNCERTAIN, result.outcome());
                assertTrue(Files.exists(root.resolve(".totipo-restore-v1")));
                if (!phase.equals("location")) assertTrue(Files.exists(root.resolve("vault")));
            }
            try (var retry = plan()) { assertEquals(RestoreResult.Outcome.COMPLETE, CoordinatedPrivateStore.restore(root, retry, true).outcome()); }
        }
    }
    @Test public void bootstrapWithConflictingObjectIsPartialAndIsNeverOverwritten() throws Exception {
        Path root = temporary.getRoot().toPath().resolve("partial");
        String digest;
        try (var plan = plan()) {
            digest = plan.summary().archiveSha256();
            try (var reserved = NioStoreComposition.reserveRestore(root.getParent(), "partial", digest, new NioDurability())) { assertTrue(reserved.acknowledgeLocation()); }
        }
        byte[] bootstrap; String object;
        try (var input = new java.util.zip.ZipInputStream(getClass().getResourceAsStream("/backup-profile-1/history.zip"))) {
            input.getNextEntry(); input.readAllBytes(); input.getNextEntry(); bootstrap = input.readAllBytes();
            object = input.getNextEntry().getName();
        }
        Files.write(root.resolve("vault"), bootstrap); Files.createDirectories(root.resolve("objects-v1"));
        byte[] conflict = new byte[]{9}; Files.write(root.resolve(object), conflict);
        try (var retry = plan()) {
            var result = CoordinatedPrivateStore.restore(root, retry, true);
            assertEquals(RestoreResult.Outcome.PARTIAL, result.outcome());
            assertEquals(RestoreResult.Bootstrap.ACKNOWLEDGED, result.bootstrap());
        }
        assertArrayEquals(conflict, Files.readAllBytes(root.resolve(object))); assertArrayEquals(bootstrap, Files.readAllBytes(root.resolve("vault")));
    }
    @Test public void occupiedDestinationsMissingTornOrDifferentIntentAndCancellationNeverOverwrite() throws Exception {
        Path root = temporary.getRoot().toPath().resolve("occupied"); Files.createDirectory(root);
        try (var plan = plan()) { assertThrows(IOException.class, () -> CoordinatedPrivateStore.restore(root, plan, false)); }
        try (var plan = plan()) { assertThrows(IOException.class, () -> CoordinatedPrivateStore.restore(root, plan, true)); }
        Files.writeString(root.resolve(".totipo-restore-v1"), "torn");
        try (var plan = plan()) { assertThrows(IOException.class, () -> CoordinatedPrivateStore.restore(root, plan, true)); }
        assertEquals("torn", Files.readString(root.resolve(".totipo-restore-v1")));
        Path cancelled = temporary.getRoot().toPath().resolve("cancelled");
        try (var plan = plan()) {
            plan.cancel(); assertEquals(RestoreResult.Outcome.REJECTED, CoordinatedPrivateStore.restore(cancelled, plan, false).outcome());
        }
        assertFalse(Files.exists(cancelled.resolve("vault"))); assertTrue(Files.isDirectory(cancelled));
    }
}
