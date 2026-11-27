package org.totipo.android;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import org.totipo.android.reconcile.*;
import org.totipo.spi.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipInputStream;

public final class BackupControllerTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final Queue<Runnable> main = new ConcurrentLinkedQueue<>();
    private final Thread ui = Thread.currentThread();
    private final ProductControllerFixtures fixtures = new ProductControllerFixtures();
    private final List<AndroidVaultController> controllers = new ArrayList<>();
    private final AndroidVaultController.Dispatcher dispatcher = new AndroidVaultController.Dispatcher() {
        public void post(Runnable action) { main.add(action); }
        public Runnable after(long delay, Runnable action) { return () -> {}; }
        public void assertDispatchThread() { assertSame(ui, Thread.currentThread()); }
        public void assertWorkerThread() { assertNotSame(ui, Thread.currentThread()); }
    };
    private final AndroidVaultController.Backend backend = new AndroidVaultController.Backend() {
        ForegroundVaultCoordinator.Discovery discover(LocalReplicaOwner owner) throws IOException { return fixtures.discover(owner); }
        ForegroundVaultCoordinator.Opening open(LocalReplicaOwner owner, char[] password) throws IOException { return fixtures.open(owner, password); }
        ForegroundVaultCoordinator.Creation create(LocalReplicaOwner owner, char[] password) throws IOException { return fixtures.create(owner, password); }
    };
    private void pump() { for (Runnable r; (r = main.poll()) != null;) r.run(); }
    private void await(BooleanSupplier condition) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < until) { pump(); if (condition.getAsBoolean()) return; Thread.sleep(2); }
        fail("Controller condition timed out");
    }
    static byte[] golden(String name) throws IOException {
        try (var in = BackupControllerTest.class.getResourceAsStream("/backup-profile-1/" + name + ".zip")) {
            return Objects.requireNonNull(in).readAllBytes();
        }
    }
    private void install(LocalReplicaOwner owner, String fixture) throws Exception {
        try (var lease = owner.acquire(); var input = new ZipInputStream(new ByteArrayInputStream(golden(fixture)))) {
            // Trusted, pinned fixtures only. No product ZIP reader or restore mechanism.
            for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                if (!entry.getName().equals("vault") && !entry.getName().startsWith("objects-v1/")) continue;
                Path file = lease.root().resolve(entry.getName()); Files.createDirectories(file.getParent());
                Files.write(file, input.readAllBytes(), StandardOpenOption.CREATE_NEW);
            }
            Files.writeString(lease.root().getParent().resolve("biometric-v2"), "private application credential fixture");
            Files.writeString(lease.root().resolve("unrelated"), "private metadata fixture");
        }
    }
    private AndroidVaultController open(String name) throws Exception {
        var owner = TestReplicaOwners.create(temporary.newFolder().toPath()); install(owner, name);
        return open(owner);
    }
    private AndroidVaultController open(LocalReplicaOwner owner) throws Exception {
        var c = new AndroidVaultController(owner, dispatcher, backend); controllers.add(c);
        c.foregroundChanged(true);
        await(() -> c.snapshot().state() == AndroidVaultController.State.LOCKED);
        assertTrue(c.unlock("fixture password".toCharArray()));
        await(c::canBackUpVault); return c;
    }
    private BackupExport prepare(AndroidVaultController c) throws Exception {
        await(c::beginBackup); // A queued observation can transiently occupy the bounded worker slot.
        var op = c.backup(); await(() -> op.phase() != BackupExport.Phase.PREPARING);
        assertEquals(op.message(), BackupExport.Phase.READY, op.phase());
        assertTrue(c.launchBackupPicker(op)); return op;
    }
    private static final BackupExport.Journal JOURNAL = new BackupExport.Journal() {
        public boolean begin() { return true; } public boolean complete() { return true; }
    };
    private static class Document implements BackupExport.Destination {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream(); int opens;
        public OutputStream openOutput() { opens++; return bytes; }
        public InputStream openReadback() { return new ByteArrayInputStream(bytes.toByteArray()); }
    }
    @After public void retire() throws Exception {
        fixtures.beforeBoundedScan = () -> {};
        for (var c : controllers) { c.retire(null); await(() -> c.retirement() == AndroidVaultController.Retirement.SUCCESS); }
    }
    @Test public void localOnlyEmptyHistoryTombstoneConflictMetadataAndGapsMatchSharedGoldens() throws Exception {
        for (String name : List.of("empty", "history", "missing-parent")) {
            var c = open(name); var op = prepare(c); var doc = new Document();
            assertTrue(c.writeBackup(op, doc, JOURNAL)); await(() -> !op.active());
            assertArrayEquals(name, golden(name), doc.bytes.toByteArray());
            assertTrue(op.message(), op.message().startsWith("Backup saved."));
            assertEquals(name.equals("missing-parent"), op.message().contains("Some earlier history is missing"));
            assertEquals(0, fixtures.refreshes); assertEquals("Local only", c.dailySyncStatus());
            c.retire(null); await(() -> c.retirement() == AndroidVaultController.Retirement.SUCCESS);
        }
    }
    @Test public void mutationWhilePickerIsOpenDoesNotRegeneratePreparedArchive() throws Exception {
        var c = open("empty"); var op = prepare(c);
        await(c::canAddToken);
        assertTrue(c.addToken(new AddTokenRequest("new", "entry", org.totipo.TotpAlgorithm.SHA1, 6, 30,
                "JBSWY3DPEHPK3PXP".toCharArray())));
        await(() -> c.snapshot().addOutcome() != null && c.canAddToken());
        var doc = new Document(); assertTrue(c.writeBackup(op, doc, JOURNAL)); await(() -> !op.active());
        assertArrayEquals(golden("empty"), doc.bytes.toByteArray());
    }
    @Test public void capacityBeyond4096FailsBeforeOutputWithoutSelectingPrefix() throws Exception {
        var c = open("empty");
        var entries = new ArrayList<ObjectEntry>();
        for (int i = 0; i < 4097; i++) entries.add(new ObjectEntry(new ObjectName(String.format("%064x", i)),
                EntryKind.REGULAR, OptionalLong.of(1024)));
        fixtures.boundedScan = new ObjectScan.Complete(entries);
        assertTrue(c.beginBackup()); var op = c.backup(); await(() -> !op.active());
        assertTrue(op.message(), op.message().contains("4,096")); assertFalse(c.launchBackupPicker(op));
    }
    @Test public void captureAdmissionExcludesAuthoringAndLockWaitsForAdmittedCapture() throws Exception {
        // This test owns capture admission and lock ordering, not initial observation
        // delivery. A late presentation can otherwise occupy the bounded worker slot
        // between open() observing readiness and beginBackup() checking admission.
        fixtures.observationCallback = (terminal, callback) -> terminal ? callback : () -> {};
        var c = open("history"); var entered = new CountDownLatch(1); var leave = new CountDownLatch(1);
        fixtures.beforeBoundedScan = () -> {
            entered.countDown();
            try { assertTrue(leave.await(10, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new AssertionError(e); }
        };
        try {
            assertTrue("backup capture admitted", c.beginBackup());
            assertTrue("admitted capture reached bounded scan", entered.await(10, TimeUnit.SECONDS));
            assertFalse(c.canAddToken()); assertFalse(c.canSync()); assertFalse(c.beginBackup());
            assertTrue(c.lock()); assertEquals(AndroidVaultController.State.LOCKING, c.snapshot().state());
            assertFalse(c.launchBackupPicker(c.backup()));
        } finally { leave.countDown(); }
        await(() -> c.snapshot().state() == AndroidVaultController.State.LOCKED);
        assertFalse(c.backup().active());
    }
    @Test public void lockReopenAndDuplicatePickerResultsCannotWrite() throws Exception {
        var c = open("empty"); var op = prepare(c); var doc = new Document();
        assertTrue(c.lock()); await(() -> c.snapshot().state() == AndroidVaultController.State.LOCKED);
        assertTrue(c.unlock("fixture password".toCharArray())); await(c::canBackUpVault);
        assertFalse(c.writeBackup(op, doc, JOURNAL)); assertEquals(0, doc.opens);
        var fresh = prepare(c); assertTrue(c.writeBackup(fresh, doc, JOURNAL));
        assertFalse(c.writeBackup(fresh, doc, JOURNAL)); await(() -> !fresh.active());
        assertTrue(fresh.message(), fresh.message().startsWith("Backup saved."));
    }
    @Test public void pickerBackgroundSurvivesButOutputBackgroundCancels() throws Exception {
        var c = open("empty"); var op = prepare(c);
        c.foregroundChanged(false); assertTrue(op.active());
        var doc = new Document(); assertFalse(c.writeBackup(op, doc, JOURNAL)); assertEquals(0, doc.opens);
        c.foregroundChanged(true); await(c::canBackUpVault);
        var next = prepare(c); var entered = new CountDownLatch(1); var leave = new CountDownLatch(1);
        Document blocked = new Document() {
            public OutputStream openOutput() {
                entered.countDown();
                try { assertTrue(leave.await(10, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new AssertionError(e); }
                return super.openOutput();
            }
        };
        assertTrue(c.writeBackup(next, blocked, JOURNAL)); assertTrue(entered.await(10, TimeUnit.SECONDS));
        try {
            c.foregroundChanged(false); assertTrue(c.lock());
            await(() -> c.snapshot().state() == AndroidVaultController.State.LOCKED);
            assertTrue(next.active());
        } finally { leave.countDown(); }
        await(() -> !next.active()); assertEquals(0, blocked.bytes.size());
        assertTrue(next.message().contains("empty, incomplete or complete"));
    }
    @Test public void independentSelectionRejectsOriginatingPickerAfterCompleteRetirement() throws Exception {
        Path base = temporary.newFolder().toPath(); var registry = new VaultRegistry(base);
        var first = registry.reserve("one"); var second = registry.reserve("two");
        var selection = new VaultSelection();
        var old = selection.select(registry, first.id(), owner -> {
            try { install(owner, "history"); } catch (Exception e) { throw new AssertionError(e); }
            var c = new AndroidVaultController(owner, dispatcher, backend); controllers.add(c); return c;
        }).controller();
        old.foregroundChanged(true); await(() -> old.snapshot().state() == AndroidVaultController.State.LOCKED);
        assertTrue(old.unlock("fixture password".toCharArray())); await(old::canBackUpVault);
        var op = prepare(old); selection.retire(); await(() -> selection.snapshot() == null);
        var next = selection.select(registry, second.id(), owner -> {
            var c = new AndroidVaultController(owner, dispatcher, backend); controllers.add(c); return c;
        }).controller(); next.foregroundChanged(true);
        await(() -> next.snapshot().state() == AndroidVaultController.State.NO_LOCAL_VAULT);
        assertTrue(next.create("independent".toCharArray())); await(next::canBackUpVault);
        var doc = new Document(); assertFalse(old.writeBackup(op, doc, JOURNAL)); assertFalse(next.writeBackup(op, doc, JOURNAL));
        assertEquals(0, doc.opens); assertNotEquals(old.localId(), next.localId());
        assertNotEquals(fixtures.session.vaultId(), org.totipo.backup.BackupArchives.inspect(
                new ByteArrayInputStream(golden("history")), org.totipo.backup.BackupLimits.DEFAULT).vaultId());
        var fresh = prepare(next); assertTrue(next.writeBackup(fresh, doc, JOURNAL)); await(() -> !fresh.active());
        assertEquals(fixtures.session.vaultId(), org.totipo.backup.BackupArchives.inspect(
                new ByteArrayInputStream(doc.bytes.toByteArray()), org.totipo.backup.BackupLimits.DEFAULT).vaultId());
    }
    @Test public void activityListenerRecreationDoesNotReplacePickerAuthority() throws Exception {
        var c = open("history"); var op = prepare(c);
        AndroidVaultController.Listener first = state -> {};
        c.attach(first); c.detach(first); c.attach(state -> {}); pump();
        assertSame(op, c.backup()); assertFalse(c.launchBackupPicker(op));
        var doc = new Document(); assertTrue(c.writeBackup(op, doc, JOURNAL)); await(() -> !op.active());
        assertArrayEquals(golden("history"), doc.bytes.toByteArray());
    }
    @Test public void incompleteEnumerationAndMissingSupportedObjectRefuseCapture() throws Exception {
        // This test owns the bounded-scan faults, not asynchronous presentation.
        // Hold unrelated initial observation delivery out of the sole pending worker
        // slot so capture admission cannot race a queued projection after open.
        fixtures.observationCallback = (terminal, callback) -> () -> {};
        var c = open("empty");
        fixtures.boundedScan = new ObjectScan.Incomplete(List.of(), StoreFailure.UNAVAILABLE);
        assertTrue(c.beginBackup()); var incomplete = c.backup(); await(() -> !incomplete.active());
        assertTrue(incomplete.message().contains("could not enumerate")); await(c::canBackUpVault);
        fixtures.boundedScan = new ObjectScan.Complete(List.of(new ObjectEntry(new ObjectName("a".repeat(64)),
                EntryKind.REGULAR, OptionalLong.of(1024))));
        assertTrue(c.beginBackup()); var missing = c.backup(); await(() -> !missing.active());
        assertTrue(missing.message().contains("could not enumerate"));
        assertFalse(c.launchBackupPicker(missing));
    }
    @Test public void retirementWaitsForBlockedProviderAfterSecretsClose() throws Exception {
        var c = open("empty"); var op = prepare(c);
        var entered = new CountDownLatch(1); var leave = new CountDownLatch(1);
        var doc = new Document() {
            public OutputStream openOutput() {
                entered.countDown();
                try { assertTrue(leave.await(10, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new AssertionError(e); }
                return super.openOutput();
            }
        };
        assertTrue(c.writeBackup(op, doc, JOURNAL)); assertTrue(entered.await(10, TimeUnit.SECONDS));
        try {
            c.retire(null); await(() -> c.retirement() == AndroidVaultController.Retirement.WAITING_PROVIDER);
            assertEquals(ForegroundVaultCoordinator.State.CLOSED, fixtures.coordinator.lifecycle());
            assertTrue(op.active()); assertFalse(c.selectedAuthority());
        } finally { leave.countDown(); }
        await(() -> c.retirement() == AndroidVaultController.Retirement.SUCCESS);
        assertFalse(op.active()); assertEquals(0, doc.bytes.size());
    }
}
