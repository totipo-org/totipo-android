package org.totipo.android;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.totipo.android.reconcile.ForegroundVaultCoordinator;
import static org.junit.Assert.*;

public final class VaultRegistryTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private Path base() { return temporary.getRoot().toPath(); }
    private Path metadata() { return base().resolve("totipo-local-v1/registry"); }
    private VaultRegistry registry() throws IOException { return new VaultRegistry(base()); }
    @Test public void freshEmptyRegistryAndOrderedIndependentAllocationsPersist() throws Exception {
        var registry = registry(); assertTrue(registry.entries().isEmpty()); assertNull(registry.lastSelected());
        var first = registry.reserve(null); var second = registry.reserve("Personal");
        assertNotEquals(first.id(), second.id());
        var a = registry.root(first.id()); var b = registry.root(second.id());
        assertFalse(a.startsWith(b)); assertFalse(b.startsWith(a));
        assertTrue(a.startsWith(base().resolve("totipo-local-v1/roots")));
        assertEquals(VaultRegistry.State.PENDING, first.state()); assertFalse(first.creationAttempted());
        registry.select(second.id());
        var restart = registry(); assertEquals(List.of(first, second), restart.entries());
        assertEquals(second.id(), restart.lastSelected()); assertEquals(a, restart.validateRoot(first.id()));
    }
    @Test public void duplicateAndTraversalIdsCannotDescribeOverlappingRoots() throws Exception {
        var registry = registry(); var entry = registry.reserve(null);
        assertThrows(IllegalArgumentException.class, () -> VaultRegistry.encode(List.of(entry, entry), null));
        for (String id : List.of("../escape", "/tmp/root", ".", entry.id() + "/store", "A".repeat(32), "00"))
            assertThrows(IllegalArgumentException.class, () -> new VaultRegistry.Entry(id, null, VaultRegistry.State.PENDING, false));
        assertThrows(IllegalArgumentException.class, () -> VaultRegistry.encode(List.of(entry), "0".repeat(32)));
        assertThrows(IllegalArgumentException.class, () -> registry.reserve("bad\nlabel"));
    }
    @Test public void symlinksAtEveryManagedLevelAndAncestorAreRejected() throws Exception {
        var registry = registry(); var entry = registry.reserve(null);
        Path outside = temporary.newFolder("outside").toPath();
        Path root = registry.root(entry.id()); Files.delete(root); Files.createSymbolicLink(root, outside);
        assertThrows(IOException.class, () -> registry.validateRoot(entry.id()));
        assertThrows(IOException.class, () -> registry.allocatePending(entry.id()));
        Files.delete(root); Files.delete(root.getParent()); Files.createSymbolicLink(root.getParent(), outside);
        assertThrows(IOException.class, () -> registry.validateRoot(entry.id()));
        assertThrows(IOException.class, this::registry);
        Path alias = base().resolve("alias"); Files.createSymbolicLink(alias, outside);
        assertThrows(IOException.class, () -> new VaultRegistry(alias));
        assertFalse(Files.exists(outside.resolve("totipo-local-v1")));
    }
    @Test public void metadataSymlinkAndTemporarySymlinkAreNeverFollowed() throws Exception {
        var registry = registry(); Path outside = temporary.newFile("outside-metadata").toPath();
        byte[] original = Files.readAllBytes(outside);
        Files.delete(metadata()); Files.createSymbolicLink(metadata(), outside);
        assertThrows(IOException.class, this::registry);
        assertThrows(IOException.class, () -> registry.reserve(null));
        assertArrayEquals(original, Files.readAllBytes(outside));
    }
    @Test public void interruptedReservationRequiresExplicitAllocationAndRetainsEntry() throws Exception {
        var registry = registry(); var entry = registry.reserve(null);
        Files.delete(registry.root(entry.id())); Files.delete(registry.root(entry.id()).getParent());
        var restart = registry(); assertEquals(List.of(entry), restart.entries());
        assertThrows(IOException.class, () -> new LocalReplicaOwner.Admission().select(restart, entry.id()));
        assertFalse(Files.exists(restart.root(entry.id())));
        restart.allocatePending(entry.id()); assertTrue(Files.isDirectory(restart.validateRoot(entry.id())));
    }
    @Test public void attemptedCreationIsNotAutomaticallyRetriedEvenIfRootLooksEmpty() throws Exception {
        var registry = registry(); var entry = registry.reserve(null);
        var owner = new LocalReplicaOwner.Admission().select(registry, entry.id());
        try (var ignored = owner.acquireForCreation()) { }
        var restart = registry(); assertTrue(restart.entries().get(0).creationAttempted());
        var remembered = new LocalReplicaOwner.Admission().select(restart, entry.id());
        var discovery = ForegroundVaultCoordinator.discover(remembered);
        assertEquals(ForegroundVaultCoordinator.LocalStatus.UNAVAILABLE, discovery.status());
        assertThrows(IOException.class, remembered::acquireForCreation);
        assertThrows(IOException.class, () -> restart.allocatePending(entry.id()));
    }
    @Test public void javaCreationPromotionRestartOpenAndMissingDataNeverRecreates() throws Exception {
        var registry = registry(); var entry = registry.reserve("First");
        var owner = new LocalReplicaOwner.Admission().select(registry, entry.id());
        assertEquals(ForegroundVaultCoordinator.LocalStatus.ABSENT, ForegroundVaultCoordinator.discover(owner).status());
        var created = ForegroundVaultCoordinator.create(owner, "test password".toCharArray());
        assertNull(created.failure()); assertNull(created.cause());
        var protocolId = created.vault().sessionVaultId();
        assertEquals(VaultRegistry.State.ESTABLISHED, registry.entries().get(0).state());
        assertNotEquals(entry.id(), protocolId.toString());
        assertThrows(IllegalStateException.class, owner::acquire);
        created.vault().close();
        var restart = registry(); var remembered = new LocalReplicaOwner.Admission().select(restart, entry.id());
        var opened = ForegroundVaultCoordinator.open(remembered, "test password".toCharArray());
        assertNull(opened.failure()); assertNull(opened.cause()); assertEquals(protocolId, opened.vault().sessionVaultId());
        opened.vault().close();
        Files.delete(restart.root(entry.id()).resolve("vault"));
        assertEquals(ForegroundVaultCoordinator.LocalStatus.UNAVAILABLE, ForegroundVaultCoordinator.discover(remembered).status());
        assertThrows(IOException.class, remembered::acquireForCreation);
        assertFalse(Files.exists(restart.root(entry.id()).resolve("vault")));
        // Missing established root is retained and never allocated by acquire or restart.
        try (var children = Files.list(restart.root(entry.id()))) { for (Path child : children.toList()) Files.delete(child); }
        Files.delete(restart.root(entry.id()));
        assertEquals(VaultRegistry.State.ESTABLISHED, registry().entries().get(0).state());
        assertThrows(IOException.class, remembered::acquire);
        assertThrows(IOException.class, () -> restart.allocatePending(entry.id()));
    }
    @Test public void interruptedSuccessfulJavaCreationCanOpenPendingData() throws Exception {
        var registry = registry(); var entry = registry.reserve(null);
        // Simulate death after durable Java create but before metadata promotion.
        var owner = new LocalReplicaOwner.Admission().select(registry, entry.id());
        try (var lease = owner.acquireForCreation()) {
            var created = org.totipo.storage.nio.NioTotipo.create(lease.root(), "pending password".toCharArray());
            assertTrue(created instanceof org.totipo.CreateVaultResult.Created);
            ((org.totipo.CreateVaultResult.Created) created).session().close();
        }
        var restart = registry(); assertEquals(VaultRegistry.State.PENDING, restart.entries().get(0).state());
        var remembered = new LocalReplicaOwner.Admission().select(restart, entry.id());
        assertEquals(ForegroundVaultCoordinator.LocalStatus.PRESENT, ForegroundVaultCoordinator.discover(remembered).status());
        assertThrows(IOException.class, remembered::acquireForCreation);
        var opened = ForegroundVaultCoordinator.open(remembered, "pending password".toCharArray());
        try { assertNull(opened.cause()); assertNull(opened.failure());
            assertEquals(VaultRegistry.State.ESTABLISHED, restart.entries().get(0).state());
        } finally { opened.vault().close(); }
    }
    @Test public void corruptMissingUnsupportedAndTrailingMetadataFailClosed() throws Exception {
        var registry = registry(); var entry = registry.reserve(null); byte[] good = Files.readAllBytes(metadata());
        for (int position : new int[]{0, 4, 8, good.length - 1}) {
            byte[] bad = good.clone(); bad[position] ^= 1; Files.write(metadata(), bad);
            assertThrows(IOException.class, this::registry);
        }
        Files.write(metadata(), Arrays.copyOf(good, good.length - 1)); assertThrows(IOException.class, this::registry);
        Files.write(metadata(), Arrays.copyOf(good, good.length + 1)); assertThrows(IOException.class, this::registry);
        Files.delete(metadata()); assertThrows(IOException.class, this::registry);
        assertTrue(Files.isDirectory(registry.root(entry.id())));
    }
    @Test public void failedWriteNeverPublishesReservationOrAllocatesRootAndPoisonsWriter() throws Exception {
        registry(); byte[] original = Files.readAllBytes(metadata());
        var registry = new VaultRegistry(base(), (destination, temporary, bytes) -> { throw new IOException("injected pre-write failure"); });
        assertThrows(IOException.class, () -> registry.reserve(null)); assertTrue(registry.entries().isEmpty());
        assertArrayEquals(original, Files.readAllBytes(metadata()));
        assertThrows(IOException.class, () -> registry.reserve(null));
        assertTrue(registry().entries().isEmpty());
    }
    @Test public void partialAtomicWriteAndUnregisteredRootsRequireRecoveryWithoutCleanup() throws Exception {
        registry(); Path next = base().resolve("totipo-local-v1/registry.next");
        var registry = new VaultRegistry(base(), (destination, temporary, bytes) -> {
            Files.write(temporary, Arrays.copyOf(bytes, 12), StandardOpenOption.CREATE_NEW);
            throw new IOException("injected before atomic move");
        });
        assertThrows(IOException.class, () -> registry.reserve(null));
        assertThrows(IOException.class, this::registry); assertTrue(Files.exists(next));
        Files.delete(next); // Deliberate test recovery, never automatic production cleanup.
        Files.createDirectory(base().resolve("totipo-local-v1/roots/" + "1".repeat(32)));
        assertThrows(IOException.class, this::registry);
    }
    @Test public void legacyDataAndCredentialsAreReadOnlyIncompatibleEvenWithNewRegistry() throws Exception {
        var registry = registry(); Path old = base().resolve("totipo-vault"); Files.createDirectory(old);
        Path data = old.resolve("vault"); byte[] bytes = {1, 2, 3}; Files.write(data, bytes);
        var failure = assertThrows(VaultRegistry.RecoveryRequired.class, this::registry);
        assertTrue(failure.getMessage().contains("Incompatible single-vault")); assertTrue(failure.getMessage().contains("unsynchronized"));
        assertArrayEquals(bytes, Files.readAllBytes(data)); Files.delete(data); Files.delete(old);
        for (String name : List.of("biometric-password-v1", "biometric-password-v1.bak", "biometric-password-v1.new")) {
            Path credential = base().resolve(name); Files.write(credential, bytes);
            assertThrows(IOException.class, this::registry); assertArrayEquals(bytes, Files.readAllBytes(credential)); Files.delete(credential);
        }
        // Simulate Android's sibling SharedPreferences directory without touching host state.
        Path androidData = temporary.newFolder("android-data").toPath(); Path noBackup = Files.createDirectory(androidData.resolve("no_backup"));
        Path preferences = Files.createDirectory(androidData.resolve("shared_prefs")).resolve("sync_binding_v1.xml");
        Files.write(preferences, bytes); assertThrows(IOException.class, () -> new VaultRegistry(noBackup));
        assertArrayEquals(bytes, Files.readAllBytes(preferences)); assertFalse(Files.exists(noBackup.resolve("totipo-local-v1")));
    }
    @Test public void reservationIsPersistedBeforeAllocationAndCreationMarkerBeforeJavaWrites() throws Exception {
        registry();
        var writes = new java.util.concurrent.atomic.AtomicInteger();
        var registry = new VaultRegistry(base(), (destination, temporary, bytes) -> {
            if (writes.getAndIncrement() == 0) {
                try (var roots = Files.list(base().resolve("totipo-local-v1/roots"))) { assertTrue(roots.findAny().isEmpty()); }
            }
            VaultRegistry.atomicWrite(destination, temporary, bytes);
        });
        var entry = registry.reserve(null);
        var owner = new LocalReplicaOwner.Admission().select(registry, entry.id());
        try (var lease = owner.acquireForCreation()) {
            assertFalse(Files.exists(lease.root().resolve("vault")));
            var restart = registry(); assertTrue(restart.entries().get(0).creationAttempted());
        }
    }
    @Test public void uncertainPostRenameWriteRetainsReservationWithoutAllocatingData() throws Exception {
        registry();
        var registry = new VaultRegistry(base(), (destination, temporary, bytes) -> {
            VaultRegistry.atomicWrite(destination, temporary, bytes);
            throw new IOException("injected lost acknowledgement after rename/force");
        });
        assertThrows(IOException.class, () -> registry.reserve(null));
        assertTrue(registry.entries().isEmpty()); // Memory was not committed on failure.
        var restart = registry(); assertEquals(1, restart.entries().size());
        var entry = restart.entries().get(0); assertFalse(Files.exists(restart.root(entry.id())));
        restart.allocatePending(entry.id()); // Explicit recovery, no protocol data existed.
    }
    @Test public void failedPromotionClosesRealJavaSessionAndPendingDataCanBeRecovered() throws Exception {
        registry(); var writes = new java.util.concurrent.atomic.AtomicInteger();
        var registry = new VaultRegistry(base(), (destination, temporary, bytes) -> {
            if (writes.incrementAndGet() == 4) throw new IOException("injected promotion failure");
            VaultRegistry.atomicWrite(destination, temporary, bytes);
        });
        var entry = registry.reserve(null);
        var owner = new LocalReplicaOwner.Admission().select(registry, entry.id());
        var created = ForegroundVaultCoordinator.create(owner, "promotion password".toCharArray());
        assertNotNull(created.cause());
        assertEquals(ForegroundVaultCoordinator.State.FAILED_CLOSED, created.vault().lifecycle());
        created.vault().close();
        var restart = registry();
        assertEquals(VaultRegistry.State.PENDING, restart.entries().get(0).state());
        assertTrue(restart.entries().get(0).creationAttempted());
        var remembered = new LocalReplicaOwner.Admission().select(restart, entry.id());
        var opened = ForegroundVaultCoordinator.open(remembered, "promotion password".toCharArray());
        try { assertNull(opened.cause()); assertNull(opened.failure()); }
        finally { opened.vault().close(); }
        assertEquals(VaultRegistry.State.ESTABLISHED, restart.entries().get(0).state());
    }
    @Test public void rootParentAndTemporaryFileLinksCannotEscape() throws Exception {
        var registry = registry();
        Path roots = base().resolve("totipo-local-v1/roots"), outside = temporary.newFolder("other").toPath();
        Files.delete(roots); Files.createSymbolicLink(roots, outside);
        assertThrows(IOException.class, () -> registry.reserve(null));
        assertThrows(IOException.class, this::registry);
        Files.delete(roots); Files.createDirectory(roots);
        Path temporaryFile = base().resolve("totipo-local-v1/registry.next");
        Path target = Files.write(outside.resolve("target"), new byte[]{7}); Files.createSymbolicLink(temporaryFile, target);
        assertThrows(IOException.class, () -> registry.reserve(null));
        assertArrayEquals(new byte[]{7}, Files.readAllBytes(target));
        assertThrows(IOException.class, this::registry);
        assertThrows(IOException.class, () -> new VaultRegistry(base().resolve("other/..")));
    }
    @Test public void liveWriterNeverOverwritesUnexpectedRegistryChanges() throws Exception {
        var registry = registry(); byte[] corrupt = {1, 2, 3}; Files.write(metadata(), corrupt);
        assertThrows(IOException.class, () -> registry.reserve(null));
        assertArrayEquals(corrupt, Files.readAllBytes(metadata())); assertTrue(registry.entries().isEmpty());
    }
    private void writeChecksummedPayload(byte[] payload) throws Exception {
        var bytes = new ByteArrayOutputStream(); bytes.write(payload);
        bytes.write(java.security.MessageDigest.getInstance("SHA-256").digest(payload));
        Files.write(metadata(), bytes.toByteArray());
    }
    @Test public void duplicateIdsOnDiskAreRejectedEvenWithValidChecksum() throws Exception {
        var registry = registry(); var first = registry.reserve(null); var second = registry.reserve(null);
        byte[] encoded = Files.readAllBytes(metadata()); byte[] payload = Arrays.copyOf(encoded, encoded.length - 32);
        byte[] target = second.id().getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] duplicate = first.id().getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        int replacements = 0;
        for (int i = 0; i <= payload.length - target.length; i++) {
            if (Arrays.equals(Arrays.copyOfRange(payload, i, i + target.length), target)) {
                System.arraycopy(duplicate, 0, payload, i, duplicate.length); replacements++;
            }
        }
        assertEquals(1, replacements); writeChecksummedPayload(payload);
        assertThrows(IOException.class, this::registry);
        assertTrue(Files.isDirectory(registry.root(first.id()))); assertTrue(Files.isDirectory(registry.root(second.id())));
    }
    @Test public void validChecksumDoesNotAuthorizeUnknownVersionBooleanOrTrailingPayload() throws Exception {
        var registry = registry(); registry.reserve(null);
        byte[] encoded = Files.readAllBytes(metadata()); byte[] payload = Arrays.copyOf(encoded, encoded.length - 32);
        byte[] unsupported = payload.clone(); unsupported[7] = 2; writeChecksummedPayload(unsupported);
        assertThrows(IOException.class, this::registry);
        byte[] invalidBoolean = payload.clone(); invalidBoolean[12 + 2 + 32] = 2; writeChecksummedPayload(invalidBoolean);
        assertThrows(IOException.class, this::registry);
        writeChecksummedPayload(Arrays.copyOf(payload, payload.length + 1)); assertThrows(IOException.class, this::registry);
    }
    @Test public void unavailableBiometricStorageNeverTouchesGlobalCredentialsOrClaimsCleanup() {
        var credentials = new UnavailableVaultBiometricCredentials("0".repeat(32));
        assertFalse(credentials.available()); assertFalse(credentials.configured());
        assertThrows(IllegalStateException.class, credentials::unlock);
        assertThrows(IllegalStateException.class, () -> credentials.enrollment("id"));
        assertThrows(IllegalStateException.class, credentials::delete);
    }
}
