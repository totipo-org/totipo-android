package org.totipo.android;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.totipo.android.reconcile.*;
import org.totipo.android.sync.*;
import static org.junit.Assert.*;
import static org.totipo.android.AndroidVaultController.*;

/** Uses the same authority as TotipoApplication, real coordinator/lease and released
 * Java. Latches hold actual admitted boundaries; no cancellation future or sleeps. */
public final class ControllerRetirementTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final BlockingQueue<Runnable> main = new LinkedBlockingQueue<>();
    private final Thread ui = Thread.currentThread();
    private final VaultSelection selection = new VaultSelection();
    private final ProductControllerFixtures real = new ProductControllerFixtures();
    private VaultRegistry registry;
    private String a, b;
    private AndroidVaultController controller;
    private final Dispatcher dispatcher = new Dispatcher() {
        public void post(Runnable action) { main.add(action); }
        public void assertDispatchThread() { assertSame(ui, Thread.currentThread()); }
        public void assertWorkerThread() { assertNotSame(ui, Thread.currentThread()); }
        public Runnable after(long delay, Runnable action) { return () -> {}; }
    };
    private class Backend extends AndroidVaultController.Backend {
        ForegroundVaultCoordinator.Discovery discover(LocalReplicaOwner owner) throws java.io.IOException { return real.discover(owner); }
        ForegroundVaultCoordinator.Creation create(LocalReplicaOwner owner, char[] password) throws java.io.IOException { return real.create(owner, password); }
        ForegroundVaultCoordinator.Opening open(LocalReplicaOwner owner, char[] password) throws java.io.IOException { return real.open(owner, password); }
    }
    @Before public void setup() throws Exception {
        registry = new VaultRegistry(temporary.newFolder().toPath().toRealPath());
        a = registry.reserve("A").id(); b = registry.reserve("B").id();
    }
    private void select(String id, AndroidVaultController.Backend backend, SyncFolderBinding binding) throws Exception {
        controller = selection.select(registry, id, owner -> new AndroidVaultController(owner, dispatcher, backend,
                new TotpPresentation.Time() {
                    public java.time.Instant wall() { return java.time.Instant.now(); }
                    public long elapsedMillis() { return 0; }
                }, null, binding)).controller();
    }
    private void await(BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!condition.getAsBoolean()) {
            long left = end - System.nanoTime(); assertTrue("Timed out: " + (controller == null ? "" : controller.snapshot()), left > 0);
            Runnable action = main.poll(Math.min(left, TimeUnit.MILLISECONDS.toNanos(100)), TimeUnit.NANOSECONDS);
            if (action != null) action.run();
        }
    }
    private void state(State value) throws Exception { await(() -> controller.snapshot().state() == value); }
    private void create() throws Exception {
        select(a, new Backend(), null); state(State.NO_LOCAL_VAULT);
        await(() -> controller.create("A password".toCharArray())); state(State.OPEN);
    }
    private void unavailableB() {
        assertNotNull(selection.snapshot());
        assertThrows(IllegalStateException.class, () -> selection.select(registry, b, owner -> { fail("B constructed early"); return null; }));
    }
    private void finish() throws Exception { await(() -> selection.snapshot() == null); assertEquals(Retirement.SUCCESS, controller.retirement()); }
    private static void gate(CountDownLatch entered, CountDownLatch release) {
        entered.countDown();
        try { assertTrue(release.await(30, TimeUnit.SECONDS)); } catch (InterruptedException failure) { throw new AssertionError(failure); }
    }
    @After public void cleanup() throws Exception {
        real.failClose.set(false); real.failDomainClose.set(false); real.operationHook = name -> {};
        if (selection.snapshot() != null && controller != null && controller.retirement() != Retirement.SHUTDOWN) {
            selection.retire(); if (controller.retirement() == Retirement.CLOSE_FAILED) controller.retryRetirement(); finish();
        }
    }
    @Test public void lockedRetirementNeverRediscoversAndOldOwnerCannotAcquire() throws Exception {
        AtomicInteger discoveries = new AtomicInteger();
        select(a, new Backend() {
            @Override ForegroundVaultCoordinator.Discovery discover(LocalReplicaOwner owner) throws java.io.IOException { discoveries.incrementAndGet(); return super.discover(owner); }
        }, null);
        state(State.NO_LOCAL_VAULT); var owner = selection.owner(); String activation = controller.activationId();
        selection.retire(); selection.retire(); unavailableB(); finish();
        assertEquals(1, discoveries.get()); assertThrows(IllegalStateException.class, owner::acquire);
        select(b, new Backend(), null); state(State.NO_LOCAL_VAULT);
        assertNotEquals(activation, controller.activationId()); assertEquals(b, controller.localId());
    }
    @Test public void openRetiresSessionLeasePresentationAndIndependentPasswordIsRequired() throws Exception {
        create(); var old = controller; var session = real.session; var owner = selection.owner();
        selection.retire(); assertNull(old.snapshot().view()); assertNull(old.snapshot().revealedCode());
        assertFalse(old.canAddToken()); assertFalse(old.canManageSyncFolder());
        finish(); assertEquals(ForegroundVaultCoordinator.State.CLOSED, real.coordinator.lifecycle());
        assertThrows(org.totipo.SessionClosedException.class, session::requestRefresh);
        assertThrows(IllegalStateException.class, owner::acquire);
        select(b, new Backend(), null); state(State.NO_LOCAL_VAULT);
        await(() -> controller.create("B password".toCharArray())); state(State.OPEN);
        controller.lock(); state(State.LOCKED); selection.retire(); finish();
        select(a, new Backend(), null); state(State.LOCKED);
        await(() -> controller.unlock("A password".toCharArray())); state(State.OPEN);
        selection.retire(); finish();
        select(b, new Backend(), null); state(State.LOCKED); assertNull(controller.snapshot().view());
        await(() -> controller.unlock("A password".toCharArray()));
        await(() -> controller.snapshot().error() == AndroidVaultController.Error.AUTHENTICATION_FAILED);
        assertEquals(State.LOCKED, controller.snapshot().state());
        await(() -> controller.unlock("B password".toCharArray())); state(State.OPEN);
        assertFalse(old.unlock("B password".toCharArray())); assertFalse(old.refresh());
    }
    @Test public void blockedCreateAndOpenRetainSelectionUntilAdmittedWorkReallyReturns() throws Exception {
        for (boolean opening : new boolean[]{false, true}) {
            if (opening) { select(a, new Backend(), null); state(State.LOCKED); }
            else { select(a, new Backend(), null); state(State.NO_LOCAL_VAULT); }
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            real.operationHook = name -> { if (name.equals(opening ? "readVault" : "createVault")) gate(entered, release); };
            char[] credential = "A password".toCharArray();
            await(() -> opening ? controller.unlock(credential) : controller.create(credential));
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            try { selection.retire(); selection.retire(); unavailableB(); assertEquals(Retirement.WAITING_WORK, controller.retirement()); }
            finally { release.countDown(); }
            finish(); assertArrayEquals(new char[credential.length], credential);
            assertEquals(ForegroundVaultCoordinator.State.CLOSED, real.coordinator.lifecycle());
            real.operationHook = name -> {};
        }
    }
    @Test public void closeAndDomainFailureRetainExactResourcesForExplicitRetry() throws Exception {
        for (boolean domain : new boolean[]{false, true}) {
            select(a, new Backend(), null); state(domain ? State.LOCKED : State.NO_LOCAL_VAULT);
            if (domain) await(() -> controller.unlock("A password".toCharArray()));
            else await(() -> controller.create("A password".toCharArray()));
            state(State.OPEN); var exact = controller;
            (domain ? real.failDomainClose : real.failClose).set(true);
            selection.retire(); await(() -> controller.retirement() == Retirement.CLOSE_FAILED);
            unavailableB(); assertEquals(State.FAILED_CLOSE, controller.snapshot().state()); assertNull(controller.snapshot().view());
            selection.retire(); assertSame(exact, selection.snapshot().controller());
            real.failClose.set(false); real.failDomainClose.set(false); controller.retryRetirement(); finish();
        }
    }
    @Test public void stuckProviderClosesSessionButRetainsSelectionAndDoesNotReopen() throws Exception {
        var port = new SyncFolderBindingTest.MemoryPort();
        port.stored = new SyncFolderBinding.Stored("content://fixture/tree/a", true, true);
        port.permissions.put(port.stored.uri(), new SyncFolderBinding.Grants(true, true));
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        port.onProbe = () -> gate(entered, release);
        select(a, new Backend(), new SyncFolderBinding(port)); state(State.NO_LOCAL_VAULT);
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        await(() -> controller.create("A password".toCharArray())); state(State.OPEN);
        try {
            selection.retire(); await(() -> controller.retirement() == Retirement.WAITING_PROVIDER);
            unavailableB(); assertEquals(ForegroundVaultCoordinator.State.CLOSED, real.coordinator.lifecycle());
            assertNull(controller.snapshot().view()); assertFalse(controller.checkSyncFolder()); assertFalse(controller.sync());
            assertEquals(1, port.probes);
        } finally { release.countDown(); }
        finish(); assertEquals(1, real.creates); assertEquals(0, real.opens);
    }
    @Test public void shutdownDuringAdmittedCreateClosesButNeverReleasesSelection() throws Exception {
        select(a, new Backend(), null); state(State.NO_LOCAL_VAULT);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var closed = new CountDownLatch(1);
        real.operationHook = name -> { if (name.equals("createVault")) gate(entered, release); if (name.equals("close")) closed.countDown(); };
        await(() -> controller.create("A password".toCharArray())); assertTrue(entered.await(10, TimeUnit.SECONDS));
        selection.retire(); controller.shutdown(); release.countDown();
        assertTrue(closed.await(10, TimeUnit.SECONDS));
        await(() -> real.coordinator.lifecycle() == ForegroundVaultCoordinator.State.CLOSED);
        assertEquals(Retirement.SHUTDOWN, controller.retirement()); unavailableB();
    }
    @Test public void admittedSaveCannotBeDiscardedAndRetirementDoesNotAuthorAgain() throws Exception {
        create(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        real.operationHook = name -> { if (name.equals("publishObject")) gate(entered, release); };
        await(() -> controller.addToken(new AddTokenRequest("Public", "fixture", org.totipo.TotpAlgorithm.SHA1, 6, 30, new char[]{'M','Y'})));
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        try { selection.retire(); unavailableB(); assertEquals(Retirement.WAITING_WORK, controller.retirement()); }
        finally { release.countDown(); }
        finish(); assertEquals(1, real.saves);
        real.operationHook = name -> {};
        select(a, new Backend(), null); state(State.LOCKED);
        await(() -> controller.unlock("A password".toCharArray())); state(State.OPEN);
        await(() -> controller.snapshot().view().tokens().size() == 1);
    }
    @Test public void lateEnteredJavaObserverCannotReachReplacementController() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var finished = new CountDownLatch(1);
        real.observationCallback = (terminal, action) -> () -> {
            if (!terminal) { gate(entered, release); try { action.run(); } finally { finished.countDown(); } }
            else action.run();
        };
        create(); assertTrue(entered.await(10, TimeUnit.SECONDS));
        selection.retire(); finish(); real.observationCallback = null;
        select(b, new Backend(), null); state(State.NO_LOCAL_VAULT); var replacement = controller.snapshot();
        release.countDown(); assertTrue(finished.await(10, TimeUnit.SECONDS));
        assertSame(replacement, controller.snapshot()); assertEquals(b, controller.localId());
    }
    @Test public void pendingMainProviderHandoffBlocksSelectionUntilDiscarded() throws Exception {
        var port = new SyncFolderBindingTest.MemoryPort();
        port.stored = new SyncFolderBinding.Stored("content://fixture/tree/a", true, true);
        port.permissions.put(port.stored.uri(), new SyncFolderBinding.Grants(true, true));
        select(a, new Backend(), new SyncFolderBinding(port));
        // Hold the actual provider main handoff outside the queue. The provider can
        // terminate, but its completion ticket still prevents selection release.
        Runnable handoff = main.poll(10, TimeUnit.SECONDS); assertNotNull(handoff);
        await(() -> controller.snapshot().state() == State.NO_LOCAL_VAULT);
        selection.retire(); unavailableB();
        await(() -> controller.retirement() == Retirement.WAITING_PROVIDER); unavailableB();
        handoff.run(); finish();
        select(b, new Backend(), null); state(State.NO_LOCAL_VAULT);
        for (Runnable queued; (queued = main.poll()) != null;) queued.run();
        assertEquals(b, controller.localId()); assertEquals(State.NO_LOCAL_VAULT, controller.snapshot().state());
    }
    private boolean idle() {
        synchronized (controller) {
            try {
                for (String name : List.of("operating", "viewQueued", "providerActive", "syncPending", "syncDrainQueued")) {
                    var field = AndroidVaultController.class.getDeclaredField(name); field.setAccessible(true);
                    if (field.getBoolean(controller)) return false;
                }
                return true;
            } catch (Exception failure) { throw new AssertionError(failure); }
        }
    }
    private void fixture(AndroidVaultController.Backend backend, SyncFolderBinding binding) throws Exception {
        controller = selection.select(registry, a, owner -> {
            try (var lease = owner.acquire()) { Files.write(lease.root().resolve("vault"), ForegroundVaultCoordinatorTest.productFixtureVault()); }
            catch (java.io.IOException failure) { throw new AssertionError(failure); }
            return new AndroidVaultController(owner, dispatcher, backend, new TotpPresentation.Time() {
                public java.time.Instant wall() { return java.time.Instant.now(); }
                public long elapsedMillis() { return 0; }
            }, null, binding);
        }).controller();
        state(State.LOCKED);
    }
    @Test public void openAuthenticationBoundaryMustReturnBeforeOwnershipRelease() throws Exception {
        ForegroundVaultCoordinatorTest.realCoreFixture();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        try {
            fixture(new Backend() {
                @Override ForegroundVaultCoordinator.Opening open(LocalReplicaOwner owner, char[] password) throws java.io.IOException {
                    gate(entered, release); return super.open(owner, password);
                }
            }, null);
            await(() -> controller.unlock("M1H disposable fixture".toCharArray())); assertTrue(entered.await(10, TimeUnit.SECONDS));
            selection.retire(); unavailableB(); release.countDown(); finish();
            assertEquals(ForegroundVaultCoordinator.State.CLOSED, real.coordinator.lifecycle());
        } finally { release.countDown(); ForegroundVaultCoordinatorTest.removeFixture(); }
    }
    @Test public void coordinatedImportPublicationMustFinishBeforeSessionClosureAndSwitch() throws Exception {
        ForegroundVaultCoordinatorTest.realCoreFixture();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        try {
            fixture(new Backend(), null); await(() -> controller.unlock("M1H disposable fixture".toCharArray())); state(State.OPEN);
            real.operationHook = name -> { if (name.equals("publishObject")) gate(entered, release); };
            await(() -> controller.sync(ForegroundVaultCoordinatorTest.productFixtureScan())); assertTrue(entered.await(10, TimeUnit.SECONDS));
            selection.retire(); unavailableB(); assertNotEquals(ForegroundVaultCoordinator.State.CLOSED, real.coordinator.lifecycle());
            release.countDown(); finish(); assertEquals(ForegroundVaultCoordinator.State.CLOSED, real.coordinator.lifecycle());
        } finally { release.countDown(); real.operationHook = name -> {}; ForegroundVaultCoordinatorTest.removeFixture(); }
    }
    @Test public void javaObservationCompletionWaitCannotReleaseAWhileStoreScanIsEntered() throws Exception {
        ForegroundVaultCoordinatorTest.realCoreFixture();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        try {
            var port = new PublicationPort(); port.vaultBytes = ForegroundVaultCoordinatorTest.productFixtureVault();
            fixture(new Backend(), new SyncFolderBinding(port));
            await(() -> controller.unlock("M1H disposable fixture".toCharArray())); state(State.OPEN); await(this::idle);
            real.beforeScan = store -> gate(entered, release);
            assertTrue(controller.sync()); assertTrue(entered.await(10, TimeUnit.SECONDS));
            selection.retire(); unavailableB(); assertNull(controller.snapshot().view());
            release.countDown(); real.beforeScan = store -> {}; finish(); assertEquals(0, port.creates);
        } finally { release.countDown(); real.beforeScan = store -> {}; ForegroundVaultCoordinatorTest.removeFixture(); }
    }
    @Test public void joinCandidateAuthenticationAndLocalEnrollmentRetireOnExistingWorker() throws Exception {
        ForegroundVaultCoordinatorTest.realCoreFixture();
        try {
            for (boolean enrollment : new boolean[]{false, true}) {
                var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
                var port = new SyncFolderBindingTest.MemoryPort();
                port.stored = new SyncFolderBinding.Stored("content://fixture/tree/a", true, true);
                port.permissions.put(port.stored.uri(), new SyncFolderBinding.Grants(true, true));
                port.snapshot = ForegroundVaultCoordinatorTest.productFixtureScan();
                select(a, new Backend() {
                    @Override boolean authenticateCandidate(byte[] exact, char[] password) {
                        if (!enrollment) gate(entered, release);
                        return super.authenticateCandidate(exact, password);
                    }
                    @Override ForegroundVaultCoordinator.Opening join(LocalReplicaOwner owner, byte[] exact, char[] password,
                            java.util.function.BooleanSupplier cancelled) throws java.io.IOException {
                        gate(entered, release); return real.join(owner, exact, password, cancelled);
                    }
                }, new SyncFolderBinding(port));
                state(State.NO_LOCAL_VAULT); await(() -> controller.prepareJoin()); await(() -> controller.hasJoinCandidate() && idle());
                char[] password = "M1H disposable fixture".toCharArray(); assertTrue(controller.joinExistingVault(password));
                assertTrue(entered.await(10, TimeUnit.SECONDS));
                try { selection.retire(); unavailableB(); assertFalse(controller.canJoinExistingVault()); }
                finally { release.countDown(); }
                finish(); assertArrayEquals(new char[password.length], password);
                // A cancelled enrollment may leave uncertain registry evidence. Use B for the
                // next independent controlled attempt rather than silently repairing A.
                a = b;
            }
        } finally { ForegroundVaultCoordinatorTest.removeFixture(); }
    }
    @Test public void outstandingLeaseCannotProduceFalseSuccessEvenAfterExecutorsTerminate() throws Exception {
        select(a, new Backend(), null); state(State.NO_LOCAL_VAULT); await(this::idle);
        var lease = selection.owner().acquire();
        selection.retire(); await(() -> controller.retirement() == Retirement.CLOSE_FAILED);
        unavailableB(); lease.close(); controller.retryRetirement(); finish();
    }
    @Test public void providerPublicationPhasesRemainAsOperationsUntilActualReturnBeforeB() throws Exception {
        for (String phase : List.of("create", "write", "readback", "postflight")) {
            create(); var old = controller;
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var late = new AtomicBoolean();
            var port = new PublicationPort(); Runnable block = () -> gate(entered, release);
            switch (phase) {
                case "create" -> port.onCreate = block;
                case "write" -> port.onWrite = block;
                case "readback" -> port.onReadBack = block;
                case "postflight" -> port.onScan = block;
                default -> throw new AssertionError();
            }
            var field = AndroidVaultController.class.getDeclaredField("providerIo"); field.setAccessible(true);
            var lane = (ProviderIoLane)field.get(controller);
            var object = new DetachedImmutableObject(new org.totipo.RevisionId("a".repeat(64)), new byte[1024]);
            lane.publish(port, new SyncFolderBinding.Request(0, PublicationPort.TREE.locator()), port.directory,
                    List.of(object), () -> false, value -> late.set(true));
            assertTrue(phase, entered.await(10, TimeUnit.SECONDS));
            try {
                selection.retire(); await(() -> old.retirement() == Retirement.WAITING_PROVIDER); unavailableB();
                assertEquals(ForegroundVaultCoordinator.State.CLOSED, real.coordinator.lifecycle());
            } finally { release.countDown(); }
            finish(); assertFalse(late.get()); assertEquals(1, port.objects.size());
            select(b, new Backend(), null); state(State.NO_LOCAL_VAULT);
            assertNull(controller.snapshot().view()); assertFalse(old.selectedAuthority());
            selection.retire(); finish();
            a = registry.reserve("Next independent A fixture").id();
        }
    }
    @Test public void shutdownWithStuckProviderNeverReleasesSelectionWhenProviderLaterReturns() throws Exception {
        var port = new SyncFolderBindingTest.MemoryPort();
        port.stored = new SyncFolderBinding.Stored("content://fixture/tree/a", true, true);
        port.permissions.put(port.stored.uri(), new SyncFolderBinding.Grants(true, true));
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        port.onProbe = () -> gate(entered, release);
        select(a, new Backend(), new SyncFolderBinding(port)); state(State.NO_LOCAL_VAULT);
        assertTrue(entered.await(10, TimeUnit.SECONDS)); selection.retire();
        await(() -> controller.retirement() == Retirement.WAITING_PROVIDER);
        controller.shutdown(); release.countDown();
        var laneField = AndroidVaultController.class.getDeclaredField("providerIo"); laneField.setAccessible(true);
        var lane = laneField.get(controller); var executor = lane.getClass().getDeclaredField("executor"); executor.setAccessible(true);
        assertTrue(((ThreadPoolExecutor)executor.get(lane)).awaitTermination(10, TimeUnit.SECONDS));
        for (Runnable action; (action = main.poll()) != null;) action.run();
        assertEquals(Retirement.SHUTDOWN, controller.retirement()); unavailableB();
    }
    @Test public void lockedErrorRetiresWithoutRetryingDiscovery() throws Exception {
        var discoveries = new AtomicInteger();
        select(a, new Backend() {
            @Override ForegroundVaultCoordinator.Discovery discover(LocalReplicaOwner owner) throws java.io.IOException {
                discoveries.incrementAndGet(); throw new java.io.IOException("fixture unavailable");
            }
        }, null);
        state(State.ERROR_LOCKED); selection.retire(); unavailableB(); finish(); assertEquals(1, discoveries.get());
    }
    @Test public void retirementDuringOrdinaryLockUsesOneClosureAndNeverReopens() throws Exception {
        create(); assertTrue(controller.lock()); selection.retire(); selection.retire(); finish();
        assertEquals(1, real.closes); assertEquals(0, real.opens);
        assertEquals(State.RETIRED, controller.snapshot().state());
    }
    @Test public void shutdownInvalidatesSuccessStillQueuedForApplicationHandoff() throws Exception {
        select(a, new Backend(), null); state(State.NO_LOCAL_VAULT);
        selection.retire(); await(() -> controller.retirement() == Retirement.SUCCESS);
        // The final controller main callback ran, but its Application notification is
        // still queued. Shutdown must invalidate that notification before release.
        assertNotNull(selection.snapshot()); controller.shutdown();
        for (Runnable action; (action = main.poll()) != null;) action.run();
        assertEquals(Retirement.SHUTDOWN, controller.retirement()); unavailableB();
    }
    @Test public void platformGrantAndHintIoCannotHoldTheMonitorNeededForImmediateRetirement() throws Exception {
        for (String phase : List.of("grant-current", "binding-check", "return-check", "hint-write")) {
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            var armed = new AtomicBoolean(); var monitorHeld = new AtomicBoolean(); var queries = new AtomicInteger();
            var port = new SyncFolderBindingTest.MemoryPort() {
                private void block() {
                    monitorHeld.set(Thread.holdsLock(controller));
                    gate(entered, release);
                }
                @Override public SyncFolderBinding.Grants grants(String uri) {
                    if (armed.get() && Thread.currentThread().getName().equals("Totipo-vault") && !phase.equals("hint-write")) {
                        int query = queries.incrementAndGet();
                        if (query == (phase.equals("binding-check") ? 1 : 2)) block();
                    }
                    return super.grants(uri);
                }
                @Override public void pendingPublication(boolean value) {
                    if (armed.get() && phase.equals("hint-write")) block();
                    super.pendingPublication(value);
                }
            };
            port.stored = new SyncFolderBinding.Stored("content://fixture/tree/a", true, true);
            port.permissions.put(port.stored.uri(), new SyncFolderBinding.Grants(true, true));
            select(a, new Backend(), new SyncFolderBinding(port)); state(State.NO_LOCAL_VAULT);
            await(() -> controller.create("A password".toCharArray())); state(State.OPEN); await(this::idle);
            var snapshot = new PublicationPort(); snapshot.vaultBytes = real.coordinator.snapshotVault();
            port.snapshot = snapshot.scan(PublicationPort.TREE.locator());
            armed.set(true);
            if (phase.equals("hint-write")) {
                assertTrue(controller.addToken(new AddTokenRequest("Public", "fixture", org.totipo.TotpAlgorithm.SHA1, 6, 30, new char[]{'M','Y'})));
            } else if (phase.equals("grant-current")) assertTrue(controller.sync());
            else assertTrue(controller.checkSyncFolder());
            await(() -> entered.getCount() == 0);
            try {
                assertFalse(phase + " platform call held controller monitor", monitorHeld.get());
                // These are the real UI queries used while rendering a neutral surface.
                controller.canSync(); controller.hasJoinCandidate(); controller.canManageSyncFolder();
                selection.retire(); assertNull(controller.snapshot().view()); unavailableB();
                assertEquals(1, release.getCount());
            } finally { armed.set(false); release.countDown(); }
            finish(); if (phase.equals("hint-write")) assertTrue(port.pending);
            a = registry.reserve("Next metadata fixture").id();
        }
    }
    @Test public void postAuthenticationCanonicalReadCannotBlockImmediateRetirement() throws Exception {
        for (boolean opening : new boolean[]{false, true}) {
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var monitorHeld = new AtomicBoolean();
            var backend = new Backend() {
                private void arm() {
                    real.operationHook = name -> {
                        if (name.equals("readVault")) { monitorHeld.set(Thread.holdsLock(controller)); gate(entered, release); }
                    };
                }
                @Override ForegroundVaultCoordinator.Creation create(LocalReplicaOwner owner, char[] password) throws java.io.IOException {
                    var result = super.create(owner, password); arm(); return result;
                }
                @Override ForegroundVaultCoordinator.Opening open(LocalReplicaOwner owner, char[] password) throws java.io.IOException {
                    var result = super.open(owner, password); arm(); return result;
                }
            };
            select(a, backend, null); state(opening ? State.LOCKED : State.NO_LOCAL_VAULT);
            await(() -> opening ? controller.unlock("A password".toCharArray()) : controller.create("A password".toCharArray()));
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            try {
                assertFalse(monitorHeld.get()); selection.retire(); unavailableB();
                assertNull(controller.snapshot().view()); assertEquals(1, release.getCount());
            } finally { real.operationHook = name -> {}; release.countDown(); }
            finish(); assertEquals(ForegroundVaultCoordinator.State.CLOSED, real.coordinator.lifecycle());
        }
    }
    @Test public void concurrentSelectionsNeverConstructTwoControllers() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); AtomicInteger factories = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> selection.select(registry, a, owner -> {
                factories.incrementAndGet(); gate(entered, release); return new AndroidVaultController(owner, dispatcher, new Backend());
            }));
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            var second = pool.submit(() -> { assertThrows(IllegalStateException.class, () -> selection.select(registry, b, owner -> { factories.incrementAndGet(); return null; })); });
            release.countDown(); controller = first.get(10, TimeUnit.SECONDS).controller(); second.get(10, TimeUnit.SECONDS);
            assertEquals(1, factories.get());
        } finally { release.countDown(); pool.shutdown(); }
        state(State.NO_LOCAL_VAULT);
    }
}
