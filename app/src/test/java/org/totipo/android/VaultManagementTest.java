package org.totipo.android;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.totipo.android.reconcile.ForegroundVaultCoordinator;
import static org.junit.Assert.*;

public final class VaultManagementTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private Path base() { return temporary.getRoot().toPath(); }
    private VaultRegistry registry() throws IOException { return new VaultRegistry(base()); }
    private final VaultSelection selection = new VaultSelection();
    private final List<String> cleaned = new ArrayList<>();
    private VaultForget.Cleanup cleanup(String failure) {
        return new VaultForget.Cleanup() {
            public void biometrics() throws Exception { cleaned.add("biometric"); if (failure.equals("biometric")) throw new IOException(); }
            public void syncBinding() throws Exception { cleaned.add("sync"); if (failure.equals("sync")) throw new IOException(); }
        };
    }
    private VaultRegistry.Entry create(VaultRegistry registry, String label, String password) throws Exception {
        var entry = registry.reserve(label);
        var owner = new LocalReplicaOwner.Admission().select(registry, entry.id());
        var created = ForegroundVaultCoordinator.create(owner, password.toCharArray());
        assertNull(created.cause()); assertNull(created.failure()); created.vault().close();
        return registry.entry(entry.id());
    }
    @Test public void startupRequiresExplicitSelectionForMultiplePendingMissingOrForgottenLast() throws Exception {
        var registry = registry();
        assertNull(VaultUiModel.startupSelection(registry.entries(), null));
        var pending = registry.reserve(null);
        assertNull(VaultUiModel.startupSelection(registry.entries(), pending.id()));
        registry.established(pending.id());
        assertNull(VaultUiModel.startupSelection(registry.entries(), null));
        assertNull(VaultUiModel.startupSelection(registry.entries(), "0".repeat(32)));
        assertEquals(pending.id(), VaultUiModel.startupSelection(registry.entries(), pending.id()));
        var second = registry.reserve("B"); registry.established(second.id());
        assertNull(VaultUiModel.startupSelection(registry.entries(), second.id()));
        registry.beginForget(second.id());
        assertNull(VaultUiModel.startupSelection(registry.entries(), second.id()));
        registry.finishForget(second.id());
        assertNull(VaultUiModel.startupSelection(registry.entries(), second.id()));
        assertEquals(pending.id(), VaultUiModel.startupSelection(registry.entries(), pending.id()));
    }
    @Test public void renameDoesNotChangeIdentityRootStateOrVaultBytesAndDuplicateLabelsHaveContext() throws Exception {
        var registry = registry(); var a = create(registry, null, "A"); var b = create(registry, "Personal", "B");
        Path root = registry.root(a.id()); byte[] exact = Files.readAllBytes(root.resolve("vault"));
        registry.rename(a.id(), "Personal"); var renamed = registry.entry(a.id());
        assertEquals(a.state(), renamed.state()); assertEquals(root, registry.root(renamed.id()));
        assertArrayEquals(exact, Files.readAllBytes(root.resolve("vault")));
        assertNotEquals(VaultUiModel.name(registry.entries(), renamed), VaultUiModel.name(registry.entries(), b));
        for (String invalid : List.of("x".repeat(129), "bad\nlabel", "\ud800"))
            assertThrows(IllegalArgumentException.class, () -> registry.rename(a.id(), invalid));
        assertEquals("Personal", registry().entry(a.id()).label());
        registry.rename(a.id(), null); assertEquals("Vault 1", VaultUiModel.name(registry.entries(), registry.entry(a.id())));
    }
    @Test public void schemaOneRemainsReadableAndNextWriteIsExplicitlyVersionTwo() throws Exception {
        var registry = registry(); var entry = registry.reserve("v1");
        Path file = base().resolve("totipo-local-v1/registry"); byte[] bytes = Files.readAllBytes(file);
        byte[] payload = Arrays.copyOf(bytes, bytes.length - 32); payload[7] = 1;
        var output = new ByteArrayOutputStream(); output.write(payload);
        output.write(java.security.MessageDigest.getInstance("SHA-256").digest(payload)); Files.write(file, output.toByteArray());
        var restart = registry(); assertEquals(entry, restart.entry(entry.id()));
        restart.rename(entry.id(), "v2"); assertEquals(2, Files.readAllBytes(file)[7]);
    }
    @Test public void invalidLastSelectionIsDiscardedWithoutChangingOtherwiseValidRegistryData() throws Exception {
        var registry = registry(); var entry = registry.reserve(null); registry.established(entry.id()); registry.select(entry.id());
        Path file = base().resolve("totipo-local-v1/registry"); byte[] bytes = Files.readAllBytes(file);
        byte[] payload = Arrays.copyOf(bytes, bytes.length - 32);
        // The final UTF field is the last-selected hint. Leave the real entry untouched.
        byte[] missing = "f".repeat(32).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(missing, 0, payload, payload.length - 32, 32);
        var output = new ByteArrayOutputStream(); output.write(payload);
        output.write(java.security.MessageDigest.getInstance("SHA-256").digest(payload));
        byte[] stale = output.toByteArray(); Files.write(file, stale);
        var restart = registry(); assertNull(restart.lastSelected());
        assertEquals(VaultRegistry.State.ESTABLISHED, restart.entry(entry.id()).state());
        assertNull(VaultUiModel.startupSelection(restart.entries(), restart.lastSelected()));
        assertArrayEquals(stale, Files.readAllBytes(file)); // No automatic metadata rewrite/repair.
        assertTrue(Files.isDirectory(restart.root(entry.id())));
        restart.select(entry.id()); assertEquals(entry.id(), registry().lastSelected());
    }
    @Test public void forgottenRootIsNeverDeletedOrRecycledAndRecoveryRequiresAuthentication() throws Exception {
        var registry = registry(); var entry = create(registry, "Retained", "correct");
        byte[] exact = Files.readAllBytes(registry.root(entry.id()).resolve("vault"));
        VaultForget.run(registry, selection, entry.id(), cleanup(""));
        assertEquals(List.of("biometric", "sync"), cleaned);
        var restart = registry(); assertEquals(VaultRegistry.State.DETACHED, restart.entry(entry.id()).state());
        assertNull(VaultUiModel.startupSelection(restart.entries(), entry.id()));
        assertArrayEquals(exact, Files.readAllBytes(restart.root(entry.id()).resolve("vault")));
        assertNotEquals(entry.id(), restart.reserve("New").id());
        var owner = new LocalReplicaOwner.Admission().select(restart, entry.id());
        assertThrows(IOException.class, owner::acquireForCreation);
        var wrong = ForegroundVaultCoordinator.open(owner, "incorrect".toCharArray());
        assertNotNull(wrong.failure()); wrong.vault().close();
        assertEquals(VaultRegistry.State.DETACHED, restart.entry(entry.id()).state());
        var opened = ForegroundVaultCoordinator.open(owner, "correct".toCharArray());
        assertNull(opened.failure()); assertNull(opened.cause()); opened.vault().close();
        assertEquals(VaultRegistry.State.ESTABLISHED, registry().entry(entry.id()).state());
        assertArrayEquals(exact, Files.readAllBytes(restart.root(entry.id()).resolve("vault")));
    }
    @Test public void interruptionAtEachCleanupBoundaryRetainsRetryAuthorityAndOrdering() throws Exception {
        var registry = registry();
        for (String boundary : List.of("biometric", "sync")) {
            var entry = registry.reserve(boundary); cleaned.clear();
            assertThrows(IOException.class, () -> VaultForget.run(registry, selection, entry.id(), cleanup(boundary)));
            assertEquals(boundary.equals("biometric") ? List.of("biometric") : List.of("biometric", "sync"), cleaned);
            assertEquals(VaultRegistry.State.FORGETTING, registry().entry(entry.id()).state());
            assertThrows(IOException.class, () -> registry.select(entry.id()));
            assertTrue(Files.isDirectory(registry.root(entry.id())));
            VaultForget.run(registry, selection, entry.id(), cleanup(""));
            assertEquals(VaultRegistry.State.DETACHED, registry().entry(entry.id()).state());
            int count = cleaned.size(); VaultForget.run(registry, selection, entry.id(), cleanup("")); assertEquals(count, cleaned.size());
        }
    }
    @Test public void intentWriteFailureCannotStartCleanupAndFinalWriteFailureCannotLoseRoot() throws Exception {
        var registry = registry(); var entry = registry.reserve(null);
        var intentFailure = new VaultRegistry(base(), (destination, next, bytes) -> { throw new IOException(); });
        assertThrows(IOException.class, () -> VaultForget.run(intentFailure, selection, entry.id(), cleanup("")));
        assertTrue(cleaned.isEmpty()); assertEquals(VaultRegistry.State.PENDING, registry().entry(entry.id()).state());
        registry.beginForget(entry.id());
        var finalFailure = new VaultRegistry(base(), (destination, next, bytes) -> { throw new IOException(); });
        assertThrows(IOException.class, () -> VaultForget.run(finalFailure, selection, entry.id(), cleanup("")));
        assertEquals(List.of("biometric", "sync"), cleaned);
        assertEquals(VaultRegistry.State.FORGETTING, registry().entry(entry.id()).state());
        VaultForget.run(registry(), selection, entry.id(), cleanup(""));
        assertEquals(VaultRegistry.State.DETACHED, registry().entry(entry.id()).state());
    }
    @Test public void finalRenameWithoutAcknowledgementKeepsDetachedDataAndIdempotentRecovery() throws Exception {
        var registry = registry(); var entry = registry.reserve(null); registry.beginForget(entry.id());
        var uncertain = new VaultRegistry(base(), (destination, next, bytes) -> {
            VaultRegistry.atomicWrite(destination, next, bytes); throw new IOException();
        });
        assertThrows(IOException.class, () -> VaultForget.run(uncertain, selection, entry.id(), cleanup("")));
        var restart = registry(); assertEquals(VaultRegistry.State.DETACHED, restart.entry(entry.id()).state());
        assertTrue(Files.isDirectory(restart.root(entry.id())));
        VaultForget.run(restart, selection, entry.id(), cleanup("biometric"));
    }
    @Test public void detachedRootsRemainInInventoryAndUnknownOrMissingRootsNeverBecomeFreshSetup() throws Exception {
        var registry = registry(); var entry = registry.reserve(null);
        VaultForget.run(registry, selection, entry.id(), cleanup(""));
        assertEquals(1, registry.bindingEntries().size());
        Files.delete(registry.root(entry.id()));
        assertThrows(IOException.class, () -> registry().select(entry.id()));
        Files.createDirectory(base().resolve("totipo-local-v1/roots/" + "a".repeat(32)));
        assertThrows(IOException.class, this::registry);
    }
    @Test public void slowCleanupReservesAdmissionWithoutHoldingTheSelectionMonitor() throws Exception {
        var registry = registry(); var entry = registry.reserve("A"); var other = registry.reserve("B");
        var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1);
        var worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        var task = worker.submit(() -> {
            try {
                VaultForget.run(registry, selection, entry.id(), new VaultForget.Cleanup() {
                    public void biometrics() throws Exception {
                        entered.countDown(); assertTrue(release.await(10, java.util.concurrent.TimeUnit.SECONDS));
                    }
                    public void syncBinding() {}
                });
            } catch (Exception failure) { throw new AssertionError(failure); }
        });
        try {
            assertTrue(entered.await(10, java.util.concurrent.TimeUnit.SECONDS));
            assertNull(selection.snapshot());
            assertThrows(IllegalStateException.class, () -> selection.select(registry, other.id(), owner -> { fail("Activation during cleanup"); return null; }));
            assertThrows(IllegalStateException.class, () -> selection.beginMaintenance(other.id()));
        } finally { release.countDown(); task.get(10, java.util.concurrent.TimeUnit.SECONDS); worker.shutdownNow(); }
        selection.beginMaintenance(other.id()); selection.endMaintenance(other.id());
        assertEquals(VaultRegistry.State.DETACHED, registry.entry(entry.id()).state());
    }
    @Test public void duplicateRecognitionRefusesWithoutOpeningMergingOrChangingAnyBytes() throws Exception {
        var registry = registry(); var a = create(registry, "Original", "password"); var b = registry.reserve("Replica");
        byte[] exact = Files.readAllBytes(registry.root(a.id()).resolve("vault"));
        var identity = org.totipo.Totipo.vaultId(exact);
        assertThrows(VaultRegistry.RecoveryRequired.class, () -> registry.refusePossibleDuplicate(b.id(), identity));
        assertFalse(Files.exists(registry.root(b.id()).resolve("vault")));
        Files.write(registry.root(b.id()).resolve("vault"), exact);
        assertThrows(VaultRegistry.RecoveryRequired.class, () -> registry.refusePossibleDuplicate(a.id()));
        VaultForget.run(registry, selection, b.id(), cleanup(""));
        assertThrows(VaultRegistry.RecoveryRequired.class, () -> registry.refusePossibleDuplicate(a.id()));
        assertArrayEquals(exact, Files.readAllBytes(registry.root(a.id()).resolve("vault")));
        assertArrayEquals(exact, Files.readAllBytes(registry.root(b.id()).resolve("vault")));
    }
}
