package org.totipo.android;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.totipo.android.reconcile.*;
import org.totipo.android.sync.*;
import org.totipo.backup.*;
import org.totipo.spi.*;
import static org.junit.Assert.*;
import static org.totipo.android.reconcile.HistoryImportStoreTest.*;

public final class HistoryImportControllerTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final Queue<Runnable> main = new ConcurrentLinkedQueue<>();
    private final Thread ui = Thread.currentThread();
    private final ProductControllerFixtures fixtures = new ProductControllerFixtures();
    private AndroidVaultController c;
    private LocalReplicaOwner owner;
    private Path root, base;
    private SyncFolderBindingTest.MemoryPort port = new SyncFolderBindingTest.MemoryPort();
    private final AndroidVaultController.Dispatcher dispatcher = new AndroidVaultController.Dispatcher() {
        public void post(Runnable task) { main.add(task); }
        public Runnable after(long delay, Runnable action) { return () -> {}; }
        public void assertDispatchThread() { assertSame(ui, Thread.currentThread()); }
        public void assertWorkerThread() { assertNotSame(ui, Thread.currentThread()); }
    };
    private final AndroidVaultController.Backend backend = new AndroidVaultController.Backend() {
        ForegroundVaultCoordinator.Discovery discover(LocalReplicaOwner o) throws IOException { return fixtures.discover(o); }
        ForegroundVaultCoordinator.Opening open(LocalReplicaOwner o, char[] p) throws IOException { return fixtures.open(o,p); }
    };
    private void pump() { for (Runnable r; (r=main.poll())!=null;) r.run(); }
    private void await(BooleanSupplier condition) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < until) { pump(); if (condition.getAsBoolean()) return; Thread.sleep(2); }
        fail("Timed out: " + (c == null ? "null" : c.snapshot()) + " " + (c == null ? "" : c.historyMessage()));
    }
    private void open() throws Exception {
        base = temporary.newFolder().toPath();
        owner = TestReplicaOwners.create(base);
        try (var lease = owner.acquire()) { root = lease.root(); Files.write(root.resolve("vault"), contents("empty").get("vault")); }
        startController();
    }
    private void startController() throws Exception {
        c = new AndroidVaultController(owner, dispatcher, backend, new TotpPresentation.Time() {
            public java.time.Instant wall() { return java.time.Instant.now(); }
            public long elapsedMillis() { return System.nanoTime()/1_000_000; }
        }, null, new SyncFolderBinding(port));
        c.foregroundChanged(true); await(() -> c.snapshot().state() == AndroidVaultController.State.LOCKED);
        await(() -> c.unlock("fixture password".toCharArray())); await(c::canAddHistory);
    }
    private HistoryImport prepare(String name) throws Exception {
        await(c::canAddHistory);
        var op = c.beginHistoryImport(); assertNotNull(op);
        assertTrue(c.prepareHistory(op, () -> new ByteArrayInputStream(zip(name))));
        await(() -> op.phase == HistoryImport.Phase.CONFIRM || !op.active());
        assertEquals(op.message, HistoryImport.Phase.CONFIRM, op.phase);
        // Worker completion, rather than a delivered state, releases command admission.
        await(() -> c.canConfirmHistory(op));
        return op;
    }
    private void apply(HistoryImport op) throws Exception {
        // A rejected occupied worker slot does not authorize retry of this operation.
        pump(); assertTrue(c.applyHistory(op)); await(() -> !op.active()); await(c::canAddHistory);
    }
    @After public void retire() throws Exception {
        fixtures.operationHook = ignored -> {};
        if (c != null) { c.retire(null); await(() -> c.retirement() == AndroidVaultController.Retirement.SUCCESS); }
    }
    @Test public void localOnlyCompleteRepeatedNoOpFrozenEvidenceAndNoSecretsOrTransport() throws Exception {
        open(); var opens = new AtomicInteger(); var op = c.beginHistoryImport();
        assertTrue(c.prepareHistory(op, () -> { opens.incrementAndGet(); return new ByteArrayInputStream(zip("history")); }));
        await(() -> op.phase == HistoryImport.Phase.CONFIRM && c.snapshot().state() == AndroidVaultController.State.OPEN);
        assertFalse(c.canAddToken()); assertFalse(c.canBackUpVault()); assertNull(c.beginHistoryImport());
        apply(op); assertEquals("History added.", c.historyMessage()); assertNull(c.snapshot().revealedCode());
        assertEquals(1, opens.get()); assertEquals("", port.recovery);
        for (var e : contents("history").entrySet()) assertArrayEquals(e.getValue(), Files.readAllBytes(root.resolve(e.getKey())));
        apply(prepare("history")); assertEquals("No new history to add.", c.historyMessage());
        assertEquals(0, port.probes + port.scans + port.takes + port.releases); assertFalse(port.pending);
    }
    @Test public void pickerCancellationDuplicateDeliveryAndListenerRecreation() throws Exception {
        open(); var op = c.beginHistoryImport(); c.foregroundChanged(false); assertTrue(op.active()); c.foregroundChanged(true);
        var calls = new AtomicInteger();
        assertTrue(c.prepareHistory(op, () -> { calls.incrementAndGet(); return new ByteArrayInputStream(zip("history")); }));
        assertFalse(c.prepareHistory(op, () -> { calls.incrementAndGet(); return null; }));
        await(() -> op.phase == HistoryImport.Phase.CONFIRM);
        AndroidVaultController.Listener listener = ignored -> {}; c.attach(listener); c.detach(listener); c.attach(listener); pump();
        assertSame(op, c.historyImport()); c.cancelHistory(op); await(() -> !op.active());
        assertFalse(c.applyHistory(op)); assertEquals(1, calls.get()); assertFalse(Files.exists(root.resolve("objects-v1")));
    }
    @Test public void backgroundRevokesPreparationAndKeepsSessionUntilBlockedIngressRetires() throws Exception {
        open(); var entered = new CountDownLatch(1); var leave = new CountDownLatch(1); var op = c.beginHistoryImport();
        assertTrue(c.prepareHistory(op, () -> {
            entered.countDown(); gate(leave); return new ByteArrayInputStream(zip("history"));
        }));
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        try {
            c.foregroundChanged(false); c.lock(); assertEquals(AndroidVaultController.State.LOCKING, c.snapshot().state());
            assertEquals(0, fixtures.closes);
        } finally { leave.countDown(); }
        await(() -> c.snapshot().state() == AndroidVaultController.State.LOCKED);
        assertFalse(op.active()); assertFalse(Files.exists(root.resolve("objects-v1"))); assertFalse(c.applyHistory(op));
    }
    @Test public void lockDuringEnteredPublicationPreservesWritesAndRequiresFreshAuthentication() throws Exception {
        open(); var entered = new CountDownLatch(1); var leave = new CountDownLatch(1); var calls = new AtomicInteger();
        var op = prepare("history");
        fixtures.operationHook = name -> { if (name.equals("publishObject")) { calls.incrementAndGet(); entered.countDown(); gate(leave); } };
        assertTrue(c.applyHistory(op)); assertTrue(entered.await(10, TimeUnit.SECONDS));
        try { c.lock(); assertEquals(0, fixtures.closes); assertFalse(c.canSync()); }
        finally { leave.countDown(); }
        await(() -> c.snapshot().state() == AndroidVaultController.State.LOCKED); assertEquals(1, calls.get());
        assertFalse(port.recovery.isEmpty()); assertFalse(c.applyHistory(op));
        await(() -> c.unlock("fixture password".toCharArray())); await(c::canAddHistory);
        apply(prepare("history")); assertEquals("", port.recovery);
    }
    @Test public void partialUncertainAndExplicitReselectionRecoveryNeverPublishPrematurely() throws Exception {
        open();
        for (boolean uncertain : List.of(false, true)) {
            // Start each fault on a missing object in a new disposable vault.
            if (uncertain) {
                c.retire(null); await(() -> c.retirement() == AndroidVaultController.Retirement.SUCCESS);
                open();
            }
            var calls = new AtomicInteger();
            fixtures.operationHook = name -> {
                if (name.equals("publishObject") && calls.incrementAndGet() == 2) {
                    fixtures.tokenWriteFault = uncertain ? new ObjectWrite.Uncertain(StoreFailure.UNAVAILABLE) : new ObjectWrite.Failed(StoreFailure.UNAVAILABLE);
                    fixtures.persistBeforeTokenFault = uncertain;
                }
            };
            apply(prepare("history"));
            assertTrue(c.historyMessage(), c.historyMessage().startsWith(uncertain ? "Some history may have been added" : "Some history was added"));
            assertFalse(port.recovery.isEmpty()); assertFalse(c.canSync()); assertFalse(c.canPublishLocalChanges());
            var wrong = c.beginHistoryImport(); assertNotNull(wrong);
            assertTrue(c.prepareHistory(wrong, () -> new ByteArrayInputStream(zip("empty")))); await(() -> !wrong.active());
            assertTrue(c.historyMessage().contains("original backup"));
            fixtures.operationHook = ignored -> {}; fixtures.tokenWriteFault = null; fixtures.persistBeforeTokenFault = false;
            apply(prepare("history")); assertEquals("", port.recovery);
        }
    }
    @Test public void writeAheadFailurePreventsEveryCanonicalWrite() throws Exception {
        open(); port.failRecovery = true; apply(prepare("history"));
        assertFalse(Files.exists(root.resolve("objects-v1"))); assertTrue(c.historyMessage().contains("could not be saved"));
    }
    @Test public void ordinaryStateEmissionDoesNotInvalidatePreparedPlan() throws Exception {
        open(); var op = prepare("history"); var before = fixtures.session.state(); fixtures.session.requestRefresh();
        await(() -> fixtures.session.state() != before); pump(); assertEquals(HistoryImport.Phase.CONFIRM, op.phase);
        apply(op); assertEquals("History added.", c.historyMessage());
    }
    @Test public void allReleasedOutcomesHaveNontechnicalHonestMessages() {
        for (var outcome : ImportResult.Outcome.values()) {
            var message = HistoryImport.outcome(new ImportResult(outcome, 0, 0, 0, 0, ImportResult.Reason.STORAGE));
            assertFalse(message.contains("object")); assertFalse(message.contains("provider"));
            assertEquals(outcome == ImportResult.Outcome.COMPLETE, message.equals("History added."));
        }
    }
    @Test public void configuredSyncExcludedUntilFullObservationThenUsesOrdinaryPath() throws Exception {
        var provider = new PublicationPort(); port = provider;
        provider.vaultBytes = contents("empty").get("vault");
        open(); await(c::canSync);
        var op = prepare("history"); int scans = provider.scans;
        assertFalse(c.sync()); assertFalse(c.publishLocalChanges()); assertFalse(c.canManageSyncFolder());
        c.foregroundChanged(true); pump(); assertEquals(scans, provider.scans);
        var observed = new AtomicInteger();
        fixtures.operationHook = name -> { if (name.equals("requestRefresh")) observed.incrementAndGet(); };
        apply(op);
        await(() -> provider.objects.size() == 3 && c.canSync());
        assertTrue(observed.get() >= 3); // explicit local refresh plus existing two-pass barrier
        assertEquals("", port.recovery); assertEquals(0, port.takes); // existing binding only
        for (var e : contents("history").entrySet()) if (e.getKey().startsWith("objects"))
            assertArrayEquals(e.getValue(), provider.objects.get(e.getKey().substring("objects-v1/".length())));
    }
    @Test public void recoverySurvivesNewControllerAndFolderReconfiguration() throws Exception {
        var provider = new PublicationPort(); port = provider;
        provider.vaultBytes = contents("empty").get("vault");
        port.recovery = org.totipo.backup.BackupArchives.inspect(new ByteArrayInputStream(zip("history")), BackupLimits.DEFAULT).archiveSha256();
        open(); assertFalse(c.canSync()); assertFalse(c.canPublishLocalChanges());
        assertTrue(c.historyMessage().contains("recovery"));
        assertTrue(c.disconnectSyncFolder()); await(c::canAddHistory);
        assertFalse(port.recovery.isEmpty()); assertFalse(c.canSync());
        apply(prepare("history")); assertEquals("", port.recovery); assertEquals(0, provider.outputs);
    }
    @Test public void cancelledBeforeApplyAndRetiredSelectionCannotReuseAuthority() throws Exception {
        open(); var op = prepare("history"); c.foregroundChanged(false);
        await(() -> !op.active()); assertFalse(c.applyHistory(op));
        c.foregroundChanged(true); await(c::canAddHistory); var picker = c.beginHistoryImport();
        c.retire(null); await(() -> c.retirement() == AndroidVaultController.Retirement.SUCCESS);
        assertFalse(c.prepareHistory(picker, () -> { fail("Late callback opened ZIP"); return null; }));
        open(); assertFalse(c.applyHistory(op)); assertFalse(c.prepareHistory(picker, () -> null));
    }
    @Test public void processReplacementRetainsPartialEvidenceAndRequiresFreshSelection() throws Exception {
        open(); var op = prepare("history"); fixtures.tokenWriteFault = new ObjectWrite.Uncertain(StoreFailure.UNAVAILABLE);
        fixtures.persistBeforeTokenFault = true; apply(op); assertFalse(port.recovery.isEmpty());
        c.retire(null); await(() -> c.retirement() == AndroidVaultController.Retirement.SUCCESS);
        var registry = new VaultRegistry(base);
        owner = new LocalReplicaOwner.Admission().select(registry, registry.entries().get(0).id());
        fixtures.tokenWriteFault = null; fixtures.persistBeforeTokenFault = false;
        startController(); assertNull(c.historyImport()); assertFalse(c.applyHistory(op)); assertFalse(c.canPublishLocalChanges());
        apply(prepare("history")); assertEquals("", port.recovery);
        for (var e : contents("history").entrySet()) assertArrayEquals(e.getValue(), Files.readAllBytes(root.resolve(e.getKey())));
    }
    @Test public void observationFailureRetainsJavaSuccessOutcomeAndRecoveryBlock() throws Exception {
        open(); var op = prepare("history");
        fixtures.operationHook = name -> { if (name.equals("requestRefresh")) throw new IllegalStateException("observation unavailable"); };
        apply(op); assertTrue(c.historyMessage(), c.historyMessage().startsWith("History added."));
        assertTrue(c.historyMessage().contains("observation")); assertFalse(port.recovery.isEmpty()); assertFalse(c.canPublishLocalChanges());
        fixtures.operationHook = ignored -> {};
        apply(prepare("history")); assertEquals("", port.recovery);
    }
    private static void gate(CountDownLatch latch) {
        try { assertTrue(latch.await(15, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new AssertionError(e); }
    }
}
