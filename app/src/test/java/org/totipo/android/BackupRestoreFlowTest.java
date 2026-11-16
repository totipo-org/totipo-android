package org.totipo.android;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.totipo.*;
import org.totipo.backup.*;
import org.totipo.android.reconcile.ForegroundVaultCoordinator;
import static org.junit.Assert.*;

public final class BackupRestoreFlowTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    static byte[] fixture(String name) throws IOException {
        try (var input = BackupRestoreFlowTest.class.getResourceAsStream("/backup-profile-1/" + name + ".zip")) { return input.readAllBytes(); }
    }
    static Map<String, byte[]> entries(String name) throws IOException {
        var result = new TreeMap<String, byte[]>();
        try (var input = new ZipInputStream(new ByteArrayInputStream(fixture(name)))) {
            ZipEntry e; while ((e = input.getNextEntry()) != null) if (!e.getName().equals("totipo-backup.txt")) result.put(e.getName(), input.readAllBytes());
        }
        return result;
    }
    private Path base() { return temporary.getRoot().toPath(); }
    private VaultRegistry registry() throws IOException { return new VaultRegistry(base()); }
    private BackupRestoreFlow authenticated(String name, VaultRegistry registry, String id) throws Exception {
        var flow = new BackupRestoreFlow(id, () -> 0); assertTrue(flow.reading());
        flow.freeze(() -> new ByteArrayInputStream(fixture(name)));
        assertEquals(BackupRestoreFlow.Stage.PASSWORD, flow.stage()); assertTrue(flow.authenticating());
        char[] password = "fixture password".toCharArray(); flow.authenticate(password, registry);
        assertArrayEquals(new char[password.length], password); assertEquals(flow.notice(), BackupRestoreFlow.Stage.CONFIRM, flow.stage());
        return flow;
    }
    @Test public void goldenArchivesRestoreExactBytesThenNormalPasswordOpenPromotesOnlyAfterObservation() throws Exception {
        for (String name : List.of("empty", "history", "missing-parent")) {
            Path sub = Files.createDirectory(base().resolve(name)); var registry = new VaultRegistry(sub);
            var flow = authenticated(name, registry, null); assertTrue(flow.installing());
            flow.install("Restored", registry, new VaultSelection());
            assertEquals(flow.notice(), BackupRestoreFlow.Stage.DONE, flow.stage());
            assertEquals(RestoreResult.Outcome.COMPLETE, flow.result().outcome());
            var entry = registry.entry(flow.localId()); assertEquals(VaultRegistry.State.RESTORING, entry.state());
            assertEquals(VaultRegistry.RestorePhase.INSTALLED, entry.restore().phase());
            assertThrows(IOException.class, () -> registry.established(entry.id()));
            Path root = registry.root(entry.id());
            for (var e : entries(name).entrySet()) assertArrayEquals(e.getKey(), e.getValue(), Files.readAllBytes(root.resolve(e.getKey())));
            try (var paths = Files.walk(root.getParent())) {
                // Only provider marker + canonical bytes: no Sync, biometrics or private metadata imported.
                assertEquals(entries(name).size() + 1, paths.filter(Files::isRegularFile).count());
            }
            var restart = new VaultRegistry(sub);
            var owner = new LocalReplicaOwner.Admission().select(restart, entry.id());
            var wrong = ForegroundVaultCoordinator.open(owner, "wrong".toCharArray());
            assertNotNull(wrong.failure()); wrong.vault().close();
            assertEquals(VaultRegistry.State.RESTORING, restart.entry(entry.id()).state());
            var opened = ForegroundVaultCoordinator.open(owner, "fixture password".toCharArray());
            try {
                assertNull(opened.cause()); assertNull(opened.failure());
                assertEquals(VaultRegistry.State.ESTABLISHED, restart.entry(entry.id()).state());
                assertEquals(flow.summary().vaultId(), opened.vault().sessionVaultId());
                if (name.equals("history")) {
                    var tokens = opened.vault().view().tokens(); assertEquals(1, tokens.size());
                }
            } finally { opened.vault().close(); }
        }
    }
    @Test public void corruptWrongPasswordAndInvalidEncryptedObjectAllocateNothing() throws Exception {
        var registry = registry();
        var bad = fixture("history"); bad[bad.length - 1] ^= 1;
        var flow = new BackupRestoreFlow(null, () -> 0); flow.reading(); flow.freeze(() -> new ByteArrayInputStream(bad));
        assertEquals(BackupRestoreFlow.Stage.FAILED, flow.stage()); assertTrue(registry.entries().isEmpty());
        flow = new BackupRestoreFlow(null, () -> 0); flow.reading(); flow.freeze(() -> new ByteArrayInputStream(fixture("history")));
        flow.authenticating(); char[] wrong = "wrong".toCharArray(); flow.authenticate(wrong, registry);
        assertArrayEquals(new char[wrong.length], wrong); assertEquals(BackupRestoreFlow.Stage.FAILED, flow.stage());
        // Repack structurally legal profile bytes with an AEAD-invalid object; Java authenticates every object.
        var original = new LinkedHashMap<String, byte[]>();
        try (var input = new ZipInputStream(new ByteArrayInputStream(fixture("history")))) {
            ZipEntry e; while ((e = input.getNextEntry()) != null) original.put(e.getName(), input.readAllBytes());
        }
        original.entrySet().stream().filter(e -> e.getKey().startsWith("objects-v1/")).findFirst().get().getValue()[35] ^= 1;
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            for (var e : original.entrySet()) {
                var entry = new ZipEntry(e.getKey()); entry.setMethod(ZipEntry.STORED); entry.setTimeLocal(java.time.LocalDateTime.of(1980,1,2,0,0));
                entry.setSize(e.getValue().length); var crc = new CRC32(); crc.update(e.getValue()); entry.setCrc(crc.getValue());
                zip.putNextEntry(entry); zip.write(e.getValue()); zip.closeEntry();
            }
        }
        flow = new BackupRestoreFlow(null, () -> 0); flow.reading(); flow.freeze(() -> new ByteArrayInputStream(output.toByteArray()));
        assertEquals(BackupRestoreFlow.Stage.PASSWORD, flow.stage()); flow.authenticating(); flow.authenticate("fixture password".toCharArray(), registry);
        assertEquals(BackupRestoreFlow.Stage.FAILED, flow.stage()); assertTrue(registry.entries().isEmpty());
    }
    @Test public void allRetainedStatesAndUnknownEvidenceRefuseSecondAllocation() throws Exception {
        var registry = registry(); var entry = registry.reserve(null);
        Files.write(registry.root(entry.id()).resolve("vault"), entries("empty").get("vault"));
        for (var state : List.of(VaultRegistry.State.PENDING, VaultRegistry.State.ESTABLISHED, VaultRegistry.State.FORGETTING, VaultRegistry.State.DETACHED)) {
            if (state == VaultRegistry.State.ESTABLISHED) registry.established(entry.id());
            if (state == VaultRegistry.State.FORGETTING) registry.beginForget(entry.id());
            if (state == VaultRegistry.State.DETACHED) registry.finishForget(entry.id());
            var flow = authenticated("empty", registry, null);
            assertEquals(state, flow.duplicate().state()); assertFalse(flow.installing()); flow.dispose();
            assertEquals(1, registry.entries().size());
        }
        Files.write(registry.root(entry.id()).resolve("vault"), new byte[]{1});
        var unknown = new BackupRestoreFlow(null, () -> 0); unknown.reading(); unknown.freeze(() -> new ByteArrayInputStream(fixture("empty")));
        unknown.authenticating(); unknown.authenticate("fixture password".toCharArray(), registry);
        assertEquals(BackupRestoreFlow.Stage.FAILED, unknown.stage()); assertEquals(1, registry.entries().size());
    }
    @Test public void interruptedReservationAndBootstrapRequireExactExplicitRetryAfterRestart() throws Exception {
        var registry = registry();
        try (var plan = BackupArchives.prepareRestore(new ByteArrayInputStream(fixture("history")), BackupLimits.DEFAULT, "fixture password".toCharArray())) {
            var entry = registry.reserveRestore("Interrupted", plan.summary());
            assertFalse(Files.exists(registry.root(entry.id())));
            assertThrows(IOException.class, () -> registry.select(entry.id()));
            assertThrows(IOException.class, () -> registry.allocatePending(entry.id()));
            var root = registry.beginRestore(entry.id(), plan.summary());
            // Death after provider reservation/bootstrap, before the first object.
            try (var reservation = org.totipo.storage.nio.NioStoreComposition.reserveRestore(root.getParent(), "store", plan.summary().archiveSha256(), new org.totipo.storage.nio.NioDurability())) {
                reservation.acknowledgeLocation(); reservation.installVault(entries("history").get("vault"));
            }
            var restart = registry(); assertEquals(VaultRegistry.State.RESTORING, restart.entry(entry.id()).state());
            assertThrows(IOException.class, () -> new LocalReplicaOwner.Admission().select(restart, entry.id()));
            var retry = authenticated("history", restart, entry.id()); assertTrue(retry.installing()); retry.install(null, restart, new VaultSelection());
            assertEquals(retry.notice(), BackupRestoreFlow.Stage.DONE, retry.stage()); assertEquals(entry.id(), retry.localId());
            assertEquals(1, restart.entries().size());
            for (var e : entries("history").entrySet()) assertArrayEquals(e.getValue(), Files.readAllBytes(root.resolve(e.getKey())));
        }
    }
    @Test public void everyRegistryWriteBoundarySurvivesPrewriteAndLostAcknowledgementInterruption() throws Exception {
        for (int phase = 1; phase <= 3; phase++) for (boolean after : List.of(false, true)) {
            Path base = Files.createDirectory(base().resolve("cut-" + phase + "-" + after));
            new VaultRegistry(base); int cut = phase; var writes = new java.util.concurrent.atomic.AtomicInteger();
            var registry = new VaultRegistry(base, (destination, next, bytes) -> {
                boolean fail = writes.incrementAndGet() == cut;
                if (fail && !after) throw new IOException("prewrite");
                VaultRegistry.atomicWrite(destination, next, bytes);
                if (fail) throw new IOException("lost acknowledgement");
            });
            var flow = authenticated("history", registry, null); flow.installing(); flow.install(null, registry, new VaultSelection());
            assertEquals(BackupRestoreFlow.Stage.FAILED, flow.stage());
            var restart = new VaultRegistry(base);
            if (phase == 1 && !after) { assertTrue(restart.entries().isEmpty()); continue; }
            var entry = restart.entries().get(0); assertEquals(VaultRegistry.State.RESTORING, entry.state());
            assertThrows(IOException.class, () -> restart.established(entry.id()));
            if (phase < 3 || !after) assertThrows(IOException.class, () -> restart.select(entry.id()));
            var retry = authenticated("history", restart, entry.id()); retry.installing(); retry.install(null, restart, new VaultSelection());
            if (phase == 2 && after) {
                // ATTEMPTED reached disk but no provider allocation exists: preserve uncertainty,
                // never recreate a root whose historical allocation cannot be proved absent.
                assertEquals(BackupRestoreFlow.Stage.FAILED, retry.stage()); assertFalse(Files.exists(restart.root(entry.id())));
            } else assertEquals(retry.notice(), BackupRestoreFlow.Stage.DONE, retry.stage());
            assertEquals(1, restart.entries().size());
        }
    }
    @Test public void installedReceiptCannotPromoteAfterObjectLossAndRecoveryRequiresOriginalZip() throws Exception {
        var registry = registry(); var flow = authenticated("history", registry, null); flow.installing(); flow.install(null, registry, new VaultSelection());
        var id = flow.localId(); var root = registry.root(id);
        String object = entries("history").keySet().stream().filter(k -> k.startsWith("objects-v1/")).findFirst().get();
        Files.delete(root.resolve(object)); // Simulated storage loss; production never rolls back objects.
        var owner = new LocalReplicaOwner.Admission().select(registry, id);
        var opened = ForegroundVaultCoordinator.open(owner, "fixture password".toCharArray());
        try { assertNotNull(opened.cause()); assertEquals(VaultRegistry.State.RESTORING, registry.entry(id).state()); }
        finally { opened.vault().close(); }
        var wrongZip = new BackupRestoreFlow(id, () -> 0); wrongZip.reading(); wrongZip.freeze(() -> new ByteArrayInputStream(fixture("empty")));
        wrongZip.authenticating(); wrongZip.authenticate("fixture password".toCharArray(), registry);
        assertEquals(BackupRestoreFlow.Stage.FAILED, wrongZip.stage()); assertFalse(Files.exists(root.resolve(object)));
        var retry = authenticated("history", registry, id); retry.installing(); retry.install(null, registry, new VaultSelection());
        assertEquals(BackupRestoreFlow.Stage.DONE, retry.stage()); assertArrayEquals(entries("history").get(object), Files.readAllBytes(root.resolve(object)));
    }
    @Test public void restoreIntentDuplicatesUnknownRootsAndSelectedAuthorityAreConservative() throws Exception {
        var registry = registry();
        try (var plan = BackupArchives.prepareRestore(new ByteArrayInputStream(fixture("history")), BackupLimits.DEFAULT, "fixture password".toCharArray())) {
            var entry = registry.reserveRestore(null, plan.summary());
            var same = authenticated("history", registry, null); assertEquals(entry.id(), same.duplicate().id()); same.dispose();
            // Explicit recovery of the durable reservation before provider allocation remains possible.
            var retry = authenticated("history", registry, entry.id()); retry.installing(); retry.install(null, registry, new VaultSelection());
            assertEquals(BackupRestoreFlow.Stage.DONE, retry.stage());
            assertThrows(IOException.class, () -> registry.beginForget(entry.id()));
        }
        var fresh = registry.reserve(null); Files.delete(registry.root(fresh.id()));
        var blocked = new BackupRestoreFlow(null, () -> 0); blocked.reading(); blocked.freeze(() -> new ByteArrayInputStream(fixture("empty")));
        blocked.authenticating(); blocked.authenticate("fixture password".toCharArray(), registry);
        assertEquals(BackupRestoreFlow.Stage.FAILED, blocked.stage());
        // Maintenance exclusion is the same selection authority used by ordinary controllers.
        var selection = new VaultSelection(); selection.beginRestoreMaintenance(fresh.id());
        assertThrows(IllegalStateException.class, () -> selection.select(registry, fresh.id(), owner -> null));
        selection.endMaintenance(fresh.id());
    }
    @Test public void promotionWriteFailureClosesNormalSessionAndRetainsExactRoot() throws Exception {
        var registry = registry(); var flow = authenticated("history", registry, null); flow.installing(); flow.install(null, registry, new VaultSelection());
        String id = flow.localId();
        for (boolean after : List.of(false, true)) {
            // Restore phase is durable even when the normal-open promotion acknowledgement is lost.
            var retry = authenticated("history", registry, id); retry.installing(); retry.install(null, registry, new VaultSelection());
            var writes = new java.util.concurrent.atomic.AtomicInteger();
            var fault = new VaultRegistry(base(), (destination, next, bytes) -> {
                boolean fail = writes.incrementAndGet() == 2; // select hint, then final promotion.
                if (fail && !after) throw new IOException("promotion prewrite");
                VaultRegistry.atomicWrite(destination, next, bytes);
                if (fail) throw new IOException("promotion lost acknowledgement");
            });
            var owner = new LocalReplicaOwner.Admission().select(fault, id);
            var opened = ForegroundVaultCoordinator.open(owner, "fixture password".toCharArray());
            try { assertNotNull(opened.cause()); } finally { opened.vault().close(); }
            registry = registry();
            assertEquals(after ? VaultRegistry.State.ESTABLISHED : VaultRegistry.State.RESTORING, registry.entry(id).state());
        }
    }
    @Test public void ambiguousRetainedIdentityDoesNotOfferAnArbitraryExistingVault() throws Exception {
        var registry = registry();
        for (int i = 0; i < 2; i++) {
            var e = registry.reserve(null); Files.write(registry.root(e.id()).resolve("vault"), entries("empty").get("vault"));
            registry.established(e.id());
        }
        var flow = new BackupRestoreFlow(null, () -> 0); flow.reading(); flow.freeze(() -> new ByteArrayInputStream(fixture("empty")));
        flow.authenticating(); flow.authenticate("fixture password".toCharArray(), registry);
        assertEquals(BackupRestoreFlow.Stage.FAILED, flow.stage()); assertNull(flow.duplicate()); assertEquals(2, registry.entries().size());
    }
    @Test public void pickerCancellationExpiryAndBlockedIngressCannotTransferAuthority() throws Exception {
        var clock = new AtomicLong(); var flow = new BackupRestoreFlow(null, clock::get);
        assertTrue(flow.reading()); assertFalse(flow.reading()); clock.set(15*60*1000);
        flow.freeze(() -> { fail("Expired ingress opened"); return null; }); assertEquals(BackupRestoreFlow.Stage.CANCELLED, flow.stage());
        var blocked = new BackupRestoreFlow(null, () -> 0); blocked.reading();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var worker = Executors.newSingleThreadExecutor();
        try {
            var work = worker.submit(() -> blocked.freeze(() -> new ByteArrayInputStream(fixture("history")) {
                public synchronized int read(byte[] bytes, int offset, int count) {
                    entered.countDown(); try { assertTrue(release.await(5, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new AssertionError(e); }
                    return super.read(bytes, offset, count);
                }
            }));
            assertTrue(entered.await(5, TimeUnit.SECONDS)); blocked.cancel(); assertFalse(work.isDone()); release.countDown(); work.get(10, TimeUnit.SECONDS);
            assertEquals(BackupRestoreFlow.Stage.CANCELLED, blocked.stage()); assertFalse(blocked.authenticating());
        } finally { release.countDown(); worker.shutdownNow(); }
    }
}
