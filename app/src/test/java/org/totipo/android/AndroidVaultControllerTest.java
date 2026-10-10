package org.totipo.android;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.totipo.android.reconcile.*;
import org.totipo.android.AndroidVaultController.Error;
import static org.totipo.android.AndroidVaultController.*;
import static org.junit.Assert.*;

public final class AndroidVaultControllerTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final Thread ui = Thread.currentThread();
    private final Queue<Runnable> deliveries = new ConcurrentLinkedQueue<>();
    private final AtomicReference<Throwable> threadError = new AtomicReference<>();
    private final AtomicInteger workerChecks = new AtomicInteger(), uiChecks = new AtomicInteger();
    private final AtomicInteger candidateAuthentications = new AtomicInteger();
    private final ProductControllerFixtures real = new ProductControllerFixtures();
    private LocalReplicaOwner owner;
    private AndroidVaultController controller;
    private final Dispatcher dispatcher = new Dispatcher() {
        public void post(Runnable action) { deliveries.add(action); }
        public void assertDispatchThread() {
            if (Thread.currentThread() != ui) threadError.set(new AssertionError("callback not UI"));
            uiChecks.incrementAndGet();
        }
        public Runnable after(long delay, Runnable action) { return () -> {}; } // Enrollment tests do not advance the reveal clock.
        public void assertWorkerThread() {
            if (Thread.currentThread() == ui) threadError.set(new AssertionError("operation on UI"));
            workerChecks.incrementAndGet();
        }
    };
    private class RealBackend extends Backend {
        ForegroundVaultCoordinator.Discovery discover(LocalReplicaOwner owner) throws IOException {
            assertNotSame(ui, Thread.currentThread()); return real.discover(owner);
        }
        ForegroundVaultCoordinator.Opening open(LocalReplicaOwner owner, char[] password) throws IOException {
            assertNotSame(ui, Thread.currentThread()); return real.open(owner, password);
        }
        boolean authenticateCandidate(byte[] exact, char[] credential) {
            assertEquals("Totipo-vault", Thread.currentThread().getName()); candidateAuthentications.incrementAndGet(); return super.authenticateCandidate(exact,credential);
        }
        ForegroundVaultCoordinator.Opening join(LocalReplicaOwner owner, byte[] exact, char[] password, BooleanSupplier cancelled) throws IOException {
            assertEquals("Totipo-vault", Thread.currentThread().getName()); return real.join(owner, exact, password, cancelled);
        }
        ForegroundVaultCoordinator.Creation create(LocalReplicaOwner owner, char[] password) throws IOException {
            assertNotSame(ui, Thread.currentThread()); return real.create(owner, password);
        }
    }
    @Before public void setup() throws Exception { owner = TestReplicaOwners.create(temporary.newFolder().toPath()); }
    private void start() { start(new RealBackend()); }
    private void start(Backend backend) { controller = new AndroidVaultController(owner, dispatcher, backend); }
    private void pump() { for (Runnable next; (next = deliveries.poll()) != null;) next.run(); }
    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            pump();
            if (condition.getAsBoolean()) { assertNull(threadError.get()); return; }
            Thread.sleep(5);
        }
        fail("Timed out: " + (controller == null ? "" : controller.snapshot()));
    }
    private boolean idle() {
        synchronized (controller) {
            try {
                var field = AndroidVaultController.class.getDeclaredField("operating"); field.setAccessible(true);
                var views = AndroidVaultController.class.getDeclaredField("viewQueued"); views.setAccessible(true);
                return !field.getBoolean(controller) && !views.getBoolean(controller);
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
    }
    private boolean providerDone() {
        synchronized (controller) {
            try {
                var field = AndroidVaultController.class.getDeclaredField("providerActive"); field.setAccessible(true);
                return idle() && !field.getBoolean(controller);
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        }
    }
    private void accept(BooleanSupplier operation) throws Exception { await(this::idle); assertTrue(operation.getAsBoolean()); }
    private void state(State next) throws Exception { await(() -> controller.snapshot().state() == next); }
    private char[] password() { return "M1J disposable".toCharArray(); }
    private void create() throws Exception {
        start(); state(State.NO_LOCAL_VAULT);
        char[] password = password(); accept(() -> controller.create(password)); state(State.OPEN);
        assertArrayEquals(new char[password.length], password);
    }
    private void lock() throws Exception { accept(() -> controller.lock()); state(State.LOCKED); }
    @After public void cleanup() throws Exception {
        real.failClose.set(false); real.failDomainClose.set(false); real.discoveryRead = null;
        if (controller != null && EnumSet.of(State.OPEN, State.ERROR_OPEN, State.FAILED_CLOSE).contains(controller.snapshot().state())) lock();
        assertNull(threadError.get());
    }
    @Test public void r19ObjectDataVetoPreservesOrphanAndDoesNotCreateVault() throws Exception {
        byte[] orphan = new byte[]{1,2,3}; Path object;
        try (var lease = owner.acquire()) {
            Files.createDirectories(lease.root().resolve("objects-v1"));
            object = Files.write(lease.root().resolve("objects-v1").resolve("a".repeat(64)), orphan);
        }
        start(); state(State.NO_LOCAL_VAULT); accept(() -> controller.create(password())); await(this::idle);
        assertEquals(State.NO_LOCAL_VAULT, controller.snapshot().state());
        assertEquals("Totipo found existing token data but no usable vault. A new vault was not created.", controller.snapshot().message());
        assertFalse(Files.exists(localRoot().resolve("vault"))); assertArrayEquals(orphan, Files.readAllBytes(object));
        assertNull(real.session);
    }
    @Test public void r19CreateAndReopenHaveExactCanonicalVaultId() throws Exception {
        create(); var id = real.session.vaultId();
        assertEquals(org.totipo.Totipo.vaultId(Files.readAllBytes(localRoot().resolve("vault"))), id);
        lock(); accept(() -> controller.unlock(password())); state(State.OPEN);
        assertEquals(id, real.session.vaultId());
    }
    @Test public void freshDiscoveryReleasesAllOwnership() throws Exception {
        start(); state(State.NO_LOCAL_VAULT);
        try (var lease = owner.acquire()) { assertNotNull(lease.root()); }
        assertEquals(0, real.opens); assertEquals(0, real.creates);
    }
    @Test public void createFreshProcessWrongPasswordCorrectOpenAndRepeatedOpen() throws Exception {
        create(); assertEquals(1, real.creates); assertEquals(0, real.opens);
        assertNotNull(controller.snapshot().view()); assertTrue(controller.snapshot().view().tokens().isEmpty());
        assertThrows(IllegalStateException.class, owner::acquire);
        char[] duplicate = password(); assertFalse(controller.unlock(duplicate)); assertArrayEquals(new char[duplicate.length], duplicate);
        lock(); assertEquals(1, real.closes);
        try (var lease = owner.acquire(); var files = Files.list(lease.root())) {
            assertEquals(1, files.filter(p -> p.getFileName().toString().equals("vault")).count());
        }
        start(); state(State.LOCKED); assertEquals(0, real.opens); // fresh process analogue; no auto-auth
        char[] wrong = "wrong".toCharArray(); accept(() -> controller.unlock(wrong));
        await(() -> controller.snapshot().error() == Error.AUTHENTICATION_FAILED);
        assertEquals(State.LOCKED, controller.snapshot().state()); assertArrayEquals(new char[wrong.length], wrong);
        try (var lease = owner.acquire()) { assertNotNull(lease.root()); }
        char[] correct = password(); accept(() -> controller.unlock(correct)); state(State.OPEN);
        assertArrayEquals(new char[correct.length], correct); assertEquals(2, real.opens);
        assertEquals(ForegroundVaultCoordinator.State.OPEN, real.coordinator.lifecycle());
        lock(); assertNull(controller.snapshot().view());
    }
    @Test public void observerRecreationMultipleObserversAndRefreshRetainSession() throws Exception {
        create(); var coordinator = real.coordinator; var session = real.session;
        List<Snapshot> a = new ArrayList<>(), b = new ArrayList<>();
        Listener first = a::add, second = b::add;
        controller.attach(first); controller.attach(second); pump();
        assertEquals(State.OPEN, a.get(a.size()-1).state()); assertEquals(State.OPEN, b.get(b.size()-1).state());
        controller.detach(first); controller.detach(second);
        assertEquals(State.OPEN, controller.snapshot().state()); assertEquals(0, real.closes);
        controller.attach(second); pump(); assertSame(coordinator, real.coordinator); assertSame(session, real.session);
        accept(() -> controller.refresh());
        await(() -> controller.snapshot().message().equals("Refresh requested"));
        assertSame(session, real.session); assertEquals(1, real.creates); assertEquals(0, real.opens); assertEquals(0, real.closes);
        assertTrue(workerChecks.get() > 0); assertTrue(uiChecks.get() > 0); controller.detach(second);
        lock(); assertFalse(controller.refresh());
    }
    @Test public void busyAdmissionWipesRejectedCredentialAndDoesNotQueueDuplicateOperations() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        start(new RealBackend() {
            boolean authenticateCandidate(byte[] exact, char[] credential) {
            assertEquals("Totipo-vault", Thread.currentThread().getName()); candidateAuthentications.incrementAndGet(); return super.authenticateCandidate(exact,credential);
        }
        ForegroundVaultCoordinator.Opening join(LocalReplicaOwner owner, byte[] exact, char[] password, BooleanSupplier cancelled) throws IOException {
            assertEquals("Totipo-vault", Thread.currentThread().getName()); return real.join(owner, exact, password, cancelled);
        }
        ForegroundVaultCoordinator.Creation create(LocalReplicaOwner owner, char[] password) throws IOException {
                entered.countDown();
                try { assertTrue(release.await(30, TimeUnit.SECONDS)); }
                catch (InterruptedException failure) { throw new AssertionError(failure); }
                return super.create(owner, password);
            }
        });
        state(State.NO_LOCAL_VAULT); char[] accepted = password(); accept(() -> controller.create(accepted));
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        try {
            char[] rejected = password(); assertFalse(controller.create(rejected)); assertArrayEquals(new char[rejected.length], rejected);
            assertFalse(controller.unlock(password())); assertFalse(controller.lock()); assertFalse(controller.refresh());
            assertEquals(State.CREATING, controller.snapshot().state()); assertEquals(0, real.creates);
        } finally { release.countDown(); }
        state(State.OPEN); assertEquals(1, real.creates);
    }
    @Test public void failedCloseRetainsOwnershipAndOnlyExplicitRetryReleasesIt() throws Exception {
        create(); real.failClose.set(true); accept(() -> controller.lock()); state(State.FAILED_CLOSE);
        assertEquals(Error.CLOSE_FAILED, controller.snapshot().error()); assertNull(controller.snapshot().view());
        assertThrows(IllegalStateException.class, owner::acquire);
        assertFalse(controller.unlock(password())); assertFalse(controller.refresh());
        real.failClose.set(false); lock();
        try (var lease = owner.acquire()) { assertNotNull(lease.root()); }
        assertEquals(ForegroundVaultCoordinator.State.CLOSED, real.coordinator.lifecycle());
    }
    @Test public void malformedVaultIsNeverAbsenceAndUnavailableHasSeparateError() throws Exception {
        for (String kind : List.of("short", "long", "directory")) {
            owner = TestReplicaOwners.create(temporary.newFolder().toPath());
            try (var lease = owner.acquire()) {
                Path vault = lease.root().resolve("vault");
                if (kind.equals("directory")) Files.createDirectory(vault);
                else Files.write(vault, new byte[kind.equals("short") ? 86 : 88]);
            }
            start(); state(State.ERROR_LOCKED); assertEquals(Error.LOCAL_STORAGE_UNSAFE, controller.snapshot().error());
            assertFalse(controller.create(password()));
            try (var lease = owner.acquire()) { assertNotNull(lease.root()); }
        }
        owner = TestReplicaOwners.create(temporary.newFolder().toPath());
        start(new RealBackend() {
            ForegroundVaultCoordinator.Discovery discover(LocalReplicaOwner owner) throws IOException { throw new IOException("test unavailable"); }
        });
        state(State.ERROR_LOCKED); assertEquals(Error.LOCAL_STORAGE_UNAVAILABLE, controller.snapshot().error());
    }
    @Test public void discoveryDomainCloseFailureIsRetainedForExplicitRetry() throws Exception {
        real.failDomainClose.set(true); start(); state(State.FAILED_CLOSE);
        assertEquals(Error.CLOSE_FAILED, controller.snapshot().error());
        assertThrows(IllegalStateException.class, owner::acquire);
        real.failDomainClose.set(false); accept(() -> controller.lock()); state(State.NO_LOCAL_VAULT);
        try (var lease = owner.acquire()) { assertNotNull(lease.root()); }
    }
    @Test public void openDomainCloseFailureDoesNotReleaseLeaseAfterSessionClosed() throws Exception {
        create(); real.failDomainClose.set(true); accept(() -> controller.lock()); state(State.FAILED_CLOSE);
        assertEquals(1, real.closes); assertThrows(IllegalStateException.class, owner::acquire);
        real.failDomainClose.set(false); lock(); assertEquals(1, real.closes);
        try (var lease = owner.acquire()) { assertNotNull(lease.root()); }
    }
    @Test public void boundedSpiUnavailableAndUnsafeNamespaceRemainDistinct() throws Exception {
        real.discoveryRead = new org.totipo.spi.BoundedRead.Unavailable(org.totipo.spi.StoreFailure.UNAVAILABLE);
        start(); state(State.ERROR_LOCKED); assertEquals(Error.LOCAL_STORAGE_UNAVAILABLE, controller.snapshot().error());
        real.discoveryRead = new org.totipo.spi.BoundedRead.Unavailable(org.totipo.spi.StoreFailure.UNSAFE_NAMESPACE);
        accept(() -> controller.retryDiscovery()); state(State.ERROR_LOCKED);
        assertEquals(Error.LOCAL_STORAGE_UNSAFE, controller.snapshot().error());
        real.discoveryRead = null; accept(() -> controller.retryDiscovery()); state(State.NO_LOCAL_VAULT);
    }
    @Test public void emptyPasswordRemainsProtocolValidAtControllerBoundary() throws Exception {
        start(); state(State.NO_LOCAL_VAULT); accept(() -> controller.create(new char[0])); state(State.OPEN);
        lock(); accept(() -> controller.unlock(new char[0])); state(State.OPEN);
    }
    @Test public void creationAlreadyExistsDoesNotCreateSecondSession() throws Exception {
        start(); state(State.NO_LOCAL_VAULT);
        try (var lease = owner.acquire()) { Files.write(lease.root().resolve("vault"), new byte[87]); }
        accept(() -> controller.create(password()));
        await(() -> controller.snapshot().error() == Error.CREATE_FAILED);
        assertEquals(State.LOCKED, controller.snapshot().state()); assertEquals(1, real.creates); assertNull(real.session);
        try (var lease = owner.acquire()) { assertNotNull(lease.root()); }
    }
    @Test public void uncertainCreationNeverPublishesOpenAndDiscoversInstalledVault() throws Exception {
        start(); state(State.NO_LOCAL_VAULT);
        real.creationInstallFault = org.totipo.spi.StoreFailure.UNAVAILABLE;
        char[] password = password(); accept(() -> controller.create(password));
        await(() -> controller.snapshot().error() == Error.LOCAL_STORAGE_UNSAFE);
        assertEquals(State.LOCKED, controller.snapshot().state()); assertNull(real.session);
        assertArrayEquals(new char[password.length], password);
        try (var lease = owner.acquire()) { assertTrue(Files.exists(lease.root().resolve("vault"))); }
        real.creationInstallFault = null; accept(() -> controller.unlock(password())); state(State.OPEN);
    }
    @Test public void endedJavaSubscriptionProducesErrorAndGatesOperations() throws Exception {
        create(); real.session.close();
        state(State.ERROR_OPEN); assertEquals(Error.OBSERVATION_DIAGNOSTICS, controller.snapshot().error());
        assertFalse(controller.refresh()); assertNull(controller.snapshot().view());
        lock();
    }
    @Test public void exactSizeDiscoveryStillLeavesFormatValidationToJava() throws Exception {
        try (var lease = owner.acquire()) { Files.write(lease.root().resolve("vault"), new byte[87]); }
        start(); state(State.LOCKED); accept(() -> controller.unlock(password()));
        await(() -> controller.snapshot().error() == Error.LOCAL_STORAGE_UNSAFE);
        assertEquals(State.LOCKED, controller.snapshot().state());
        try (var lease = owner.acquire()) { assertNotNull(lease.root()); }
    }
    @Test public void credentialFreeProductSyncImportsM1IFixtureAndPublishesLaterDetachedState() throws Exception {
        ForegroundVaultCoordinatorTest.realCoreFixture();
        try {
            try (var lease = owner.acquire()) { Files.write(lease.root().resolve("vault"), ForegroundVaultCoordinatorTest.productFixtureVault()); }
            start(); state(State.LOCKED);
            accept(() -> controller.unlock("M1H disposable fixture".toCharArray())); state(State.OPEN);
            var coordinator = real.coordinator; var session = real.session;
            List<Snapshot> updates = new ArrayList<>(); controller.attach(updates::add); pump();
            assertTrue(controller.snapshot().view().tokens().isEmpty());
            accept(() -> controller.sync(ForegroundVaultCoordinatorTest.productFixtureScan()));
            await(() -> controller.snapshot().state() == State.OPEN && controller.snapshot().view().tokens().size() == 3);
            await(() -> updates.stream().anyMatch(s -> s.view() != null && s.view().tokens().size() == 3));
            assertSame(coordinator, real.coordinator); assertSame(session, real.session);
            assertEquals(1, real.opens); assertEquals(0, real.creates); assertEquals(0, real.closes);
            assertThrows(UnsupportedOperationException.class, () -> controller.snapshot().view().tokens().clear());
        } finally { ForegroundVaultCoordinatorTest.removeFixture(); }
    }
    private Path localRoot() throws Exception {
        var field = LocalReplicaOwner.class.getDeclaredField("root"); field.setAccessible(true); return (Path) field.get(owner);
    }
    private org.totipo.android.sync.SyncFolderBindingTest.MemoryPort startBound() throws Exception { return startBound(new RealBackend()); }
    private org.totipo.android.sync.SyncFolderBindingTest.MemoryPort startBound(Backend backend) throws Exception {
        var port = new org.totipo.android.sync.SyncFolderBindingTest.MemoryPort();
        port.stored = new org.totipo.android.sync.SyncFolderBinding.Stored("content://fixture/tree/a", true, false);
        port.permissions.put(port.stored.uri(), new org.totipo.android.sync.SyncFolderBinding.Grants(true, false));
        controller = new AndroidVaultController(owner, dispatcher, backend, new TotpPresentation.Time() {
            public java.time.Instant wall() { return java.time.Instant.now(); }
            public long elapsedMillis() { return System.nanoTime() / 1000000; }
        }, null, new org.totipo.android.sync.SyncFolderBinding(port));
        await(this::providerDone); return port;
    }
    @Test public void manualProviderImportUsesWorkerSameSessionAndHonestCoverage() throws Exception {
        ForegroundVaultCoordinatorTest.realCoreFixture();
        try {
            byte[] wrapper = ForegroundVaultCoordinatorTest.productFixtureVault();
            try (var lease = owner.acquire()) { Files.write(lease.root().resolve("vault"), wrapper); }
            var port = startBound();
            assertFalse(controller.canImportProviderChanges()); assertFalse(controller.importProviderChanges());
            assertFalse(controller.syncView().toString().contains("content://"));
            accept(() -> controller.unlock("M1H disposable fixture".toCharArray())); state(State.OPEN); await(this::idle);
            assertTrue(controller.canImportProviderChanges());
            var same = real.session;
            var scan = ForegroundVaultCoordinatorTest.productFixtureScan();
            port.snapshot = org.totipo.android.provider.ProviderSnapshotTest.boundedFixture(scan);
            accept(() -> controller.importProviderChanges());
            await(() -> providerDone() && controller.snapshot().view().tokens().size() == 3);
            assertTrue(controller.syncView().message().contains("Provider view incomplete"));
            assertTrue(controller.syncView().message().contains("Changes imported"));
            assertNull(controller.snapshot().revealedCode()); assertSame(same, real.session);
            assertEquals(1, real.opens); assertEquals(0, real.closes);
            assertArrayEquals(wrapper, Files.readAllBytes(localRoot().resolve("vault")));
            port.snapshot = new org.totipo.android.provider.ProviderSnapshot.Scan(scan.epoch(), scan.tree(), scan.root(),
                    scan.directories(), org.totipo.android.provider.ProviderSnapshot.State.INCOMPLETE_LOADING, scan.issues(), scan.vaultCandidates());
            accept(() -> controller.importProviderChanges()); await(this::providerDone);
            assertEquals("Provider view incomplete. No new objects.", controller.syncView().message());
            port.snapshot = scan; accept(() -> controller.importProviderChanges()); await(this::providerDone);
            assertEquals("No new objects.", controller.syncView().message());
            port.offline = true; accept(() -> controller.importProviderChanges()); await(this::providerDone);
            assertEquals(State.OPEN, controller.snapshot().state());
            assertEquals(org.totipo.android.sync.SyncFolderBinding.Status.UNAVAILABLE, controller.syncView().binding().status());
            assertFalse(controller.canImportProviderChanges());
            port.offline = false; accept(() -> controller.checkSyncFolder()); await(this::providerDone);
            assertTrue(controller.canImportProviderChanges());
        } finally { ForegroundVaultCoordinatorTest.removeFixture(); }
    }
    @Test public void importBusyAdmissionAndEmptyTreeStatus() throws Exception {
        var port = startBound(); accept(() -> controller.create(password())); state(State.OPEN); await(this::idle);
        var tree = new org.totipo.android.provider.ProviderSnapshot.Tree("fixture", "content://fixture/tree/a", "a");
        port.snapshot = new org.totipo.android.provider.ProviderSnapshot.Scan("epoch", tree,
                new org.totipo.android.provider.ProviderSnapshot.Listing("epoch", "a", List.of(),
                        org.totipo.android.provider.ProviderSnapshot.State.COMPLETE, List.of()), List.of(),
                org.totipo.android.provider.ProviderSnapshot.State.COMPLETE, List.of());
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        port.onScan = () -> {
            assertNotSame(ui, Thread.currentThread()); entered.countDown();
            try { assertTrue(release.await(30, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new AssertionError(e); }
        };
        accept(() -> controller.importProviderChanges()); assertTrue(entered.await(10, TimeUnit.SECONDS));
        try {
            assertFalse(controller.importProviderChanges()); assertFalse(controller.canImportProviderChanges());
            assertFalse(controller.checkSyncFolder());
            accept(() -> controller.refresh()); state(State.OPEN);
            assertEquals(1, port.scans);
        } finally { release.countDown(); }
        await(this::providerDone); assertEquals("Sync folder has no Totipo vault.", controller.syncView().message());
    }
    @Test public void productPublicationFailureAndExistingContradictionAreVisible() throws Exception {
        ForegroundVaultCoordinatorTest.realCoreFixture();
        try {
            try (var lease = owner.acquire()) { Files.write(lease.root().resolve("vault"), ForegroundVaultCoordinatorTest.productFixtureVault()); }
            var port = startBound(); port.snapshot = ForegroundVaultCoordinatorTest.productFixtureScan();
            accept(() -> controller.unlock("M1H disposable fixture".toCharArray())); state(State.OPEN);
            real.tokenWriteFault = new org.totipo.spi.ObjectWrite.ExistingDifferent();
            accept(() -> controller.importProviderChanges()); await(this::providerDone);
            assertTrue(controller.syncView().message().contains("Integrity problem"));
            accept(() -> controller.checkSyncFolder()); await(this::providerDone);
            assertTrue(controller.syncView().message().contains("Integrity problem"));
            real.tokenWriteFault = new org.totipo.spi.ObjectWrite.Failed(org.totipo.spi.StoreFailure.UNAVAILABLE);
            accept(() -> controller.importProviderChanges()); await(this::providerDone);
            assertEquals(State.ERROR_OPEN, controller.snapshot().state());
            assertTrue(controller.syncView().message().contains("Local publication failed"));
            assertEquals(1, real.opens); assertEquals(0, real.closes);
            real.tokenWriteFault = null;
        } finally { ForegroundVaultCoordinatorTest.removeFixture(); }
    }
    private static void block(CountDownLatch entered, CountDownLatch release) {
        entered.countDown();
        boolean interrupted = false;
        for (;;) {
            try { if (!release.await(30, TimeUnit.SECONDS)) throw new AssertionError("provider not released"); break; }
            catch (InterruptedException ignored) { interrupted = true; }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }
    @Test public void blockedProviderLockDisconnectAndReplacementDiscardWithoutRefresh() throws Exception {
        ForegroundVaultCoordinatorTest.realCoreFixture();
        try {
            for (String action : List.of("lock", "disconnect", "replace", "permission")) {
                owner = TestReplicaOwners.create(temporary.newFolder().toPath());
                try (var lease = owner.acquire()) { Files.write(lease.root().resolve("vault"), ForegroundVaultCoordinatorTest.productFixtureVault()); }
                var port = startBound(); port.snapshot = ForegroundVaultCoordinatorTest.productFixtureScan();
                accept(() -> controller.unlock("M1H disposable fixture".toCharArray())); state(State.OPEN);
                var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
                var threads = new HashSet<Thread>();
                port.onScan = () -> { synchronized (threads) { threads.add(Thread.currentThread()); }
                    assertEquals("Totipo-provider-io", Thread.currentThread().getName()); block(entered, release); };
                Set<Path> beforeFiles;
                try (var files = Files.walk(localRoot())) { beforeFiles = files.collect(java.util.stream.Collectors.toSet()); }
                int refreshes = real.refreshes;
                var vaultOps = new ArrayList<String>();
                real.operationHook = name -> {
                    if (name.equals("publishObject") || name.equals("requestRefresh"))
                        assertEquals("Totipo-vault", Thread.currentThread().getName());
                    synchronized (vaultOps) { vaultOps.add(name); }
                };
                accept(() -> controller.importProviderChanges()); assertTrue(entered.await(10, TimeUnit.SECONDS));
                try {
                    await(this::idle);
                    assertEquals(State.OPEN, controller.snapshot().state()); // UI Lock remains enabled.
                    assertEquals("Importing…", controller.syncView().message());
                    for (int i = 0; i < 100; i++) assertFalse(controller.importProviderChanges());
                    assertFalse(controller.checkSyncFolder()); assertEquals(1, port.scans);
                    var laneField = AndroidVaultController.class.getDeclaredField("providerIo"); laneField.setAccessible(true);
                    Object lane = laneField.get(controller);
                    var executorField = lane.getClass().getDeclaredField("executor"); executorField.setAccessible(true);
                    var executor = (ThreadPoolExecutor) executorField.get(lane);
                    assertEquals(1, executor.getLargestPoolSize()); assertEquals(0, executor.getQueue().size());
                    if (action.equals("lock")) {
                        lock(); assertEquals(1, real.closes);
                        accept(() -> controller.unlock("M1H disposable fixture".toCharArray())); state(State.OPEN);
                    } else if (action.equals("disconnect")) {
                        accept(() -> controller.disconnectSyncFolder()); await(this::idle);
                        assertEquals(org.totipo.android.sync.SyncFolderBinding.Status.NOT_CONFIGURED, controller.syncView().binding().status());
                    } else if (action.equals("permission")) {
                        port.permissions.clear(); // Grant revoked while a provider ignores cancellation.
                    } else {
                        accept(() -> controller.chooseSyncFolder(false, "content://fixture/tree/b", 1)); await(this::idle);
                        assertEquals("content://fixture/tree/b", controller.initialTreeUri());
                    }
                    assertEquals(1L, release.getCount()); assertEquals(1, threads.size());
                    synchronized (vaultOps) { vaultOps.clear(); }
                } finally { release.countDown(); }
                await(this::providerDone);
                assertEquals(refreshes, real.refreshes);
                assertTrue(controller.snapshot().view().tokens().isEmpty());
                try (var files = Files.walk(localRoot())) {
                    assertEquals(beforeFiles, files.collect(java.util.stream.Collectors.toSet()));
                }
                synchronized (vaultOps) { assertFalse(vaultOps.contains("requestRefresh")); assertFalse(vaultOps.contains("publishObject")); }
                if (action.equals("replace")) {
                    assertEquals("content://fixture/tree/b", controller.initialTreeUri());
                    assertEquals(org.totipo.android.sync.SyncFolderBinding.Status.READY, controller.syncView().binding().status());
                }
                if (action.equals("disconnect")) assertNull(controller.initialTreeUri());
                if (action.equals("permission")) assertEquals(org.totipo.android.sync.SyncFolderBinding.Status.ACCESS_LOST,
                        controller.syncView().binding().status());
                lock(); controller.shutdown(); real.operationHook = name -> {};
            }
        } finally { ForegroundVaultCoordinatorTest.removeFixture(); }
    }
    @Test public void startupAndRetryProbeCannotBlockLocalCreateOrAddAndUseOneThread() throws Exception {
        var port = new org.totipo.android.sync.SyncFolderBindingTest.MemoryPort();
        port.stored = new org.totipo.android.sync.SyncFolderBinding.Stored("content://fixture/tree/a", true, false);
        port.permissions.put(port.stored.uri(), new org.totipo.android.sync.SyncFolderBinding.Grants(true, false));
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var threads = new HashSet<Thread>();
        port.onProbe = () -> { synchronized (threads) { threads.add(Thread.currentThread()); }
            assertEquals("Totipo-provider-io", Thread.currentThread().getName()); block(entered, release); };
        controller = new AndroidVaultController(owner, dispatcher, new RealBackend(), new TotpPresentation.Time() {
            public java.time.Instant wall() { return java.time.Instant.now(); }
            public long elapsedMillis() { return 0; }
        }, null, new org.totipo.android.sync.SyncFolderBinding(port));
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        try {
            state(State.NO_LOCAL_VAULT); await(this::idle);
            assertEquals(org.totipo.android.sync.SyncFolderBinding.Status.CHECKING, controller.syncView().binding().status());
            assertFalse(controller.canImportProviderChanges());
            for (int i = 0; i < 100; i++) assertFalse(controller.checkSyncFolder());
            accept(() -> controller.create(password())); state(State.OPEN);
            add(enrollment("local", "", org.totipo.TotpAlgorithm.SHA1, 6, 30, rfcSecret()), AddTokenOutcome.Status.ADDED);
            assertEquals(1, port.probes); assertEquals(1, threads.size()); assertEquals(1L, release.getCount());
        } finally { release.countDown(); }
        await(this::providerDone);
        assertEquals(org.totipo.android.sync.SyncFolderBinding.Status.READY, controller.syncView().binding().status());
        port.offline = true; accept(() -> controller.checkSyncFolder()); await(this::providerDone);
        assertEquals(org.totipo.android.sync.SyncFolderBinding.Status.UNAVAILABLE, controller.syncView().binding().status());
        assertEquals(1, threads.size());
    }
    private void prepareJoin() throws Exception { accept(() -> controller.prepareJoin()); await(this::providerDone); }
    @Test public void joinWrongPasswordThenExactEnrollmentThenManualImportOnly() throws Exception {
        ForegroundVaultCoordinatorTest.realCoreFixture();
        try {
            var port = startBound(); port.snapshot = ForegroundVaultCoordinatorTest.productFixtureScan();
            prepareJoin(); char[] wrong = "wrong".toCharArray(); accept(() -> controller.joinExistingVault(wrong)); await(this::providerDone);
            assertEquals(State.NO_LOCAL_VAULT, controller.snapshot().state()); assertArrayEquals(new char[wrong.length],wrong);
            assertFalse(Files.exists(localRoot().resolve("vault")));
            char[] correct = "M1H disposable fixture".toCharArray(); accept(() -> controller.joinExistingVault(correct)); await(this::providerDone);
            assertEquals(State.OPEN,controller.snapshot().state()); assertArrayEquals(new char[correct.length],correct);
            byte[] exact = port.snapshot.vaultCandidates().get(0).bytes();
            assertArrayEquals(exact,Files.readAllBytes(localRoot().resolve("vault")));
            assertEquals(org.totipo.Totipo.vaultId(exact),real.session.vaultId()); assertTrue(controller.snapshot().view().tokens().isEmpty());
            var session = real.session; accept(() -> controller.importProviderChanges()); await(this::providerDone);
            await(() -> controller.snapshot().view().tokens().size()==3); assertSame(session,real.session);
        } finally { ForegroundVaultCoordinatorTest.removeFixture(); }
    }
    @Test public void joinInvalidInputMalformedMissingAndUnavailableLeaveAbsent() throws Exception {
        ForegroundVaultCoordinatorTest.realCoreFixture();
        try {
            var port=startBound(); var scan=ForegroundVaultCoordinatorTest.productFixtureScan();
            port.snapshot=scan;
            prepareJoin(); char[] invalid=new char[]{'\ud800'}; accept(() -> controller.joinExistingVault(invalid)); await(this::providerDone);
            assertArrayEquals(new char[1],invalid); assertFalse(Files.exists(localRoot().resolve("vault")));
            for(String kind:List.of("missing","malformed","size","directory","duplicate","loading")) {
                port.snapshot=scan;
                var row=scan.vaultCandidates().get(0).document();
                var rows=new ArrayList<>(scan.root().rows());
                var candidates=new ArrayList<>(scan.vaultCandidates());
                if(kind.equals("missing")) { rows.remove(row); candidates.clear(); }
                if(kind.equals("duplicate")) rows.add(row);
                if(kind.equals("directory")) { rows.remove(row); rows.add(new org.totipo.android.provider.ProviderSnapshot.Document(row.tree(),row.locator(),row.id(),row.parentId(),"vault","vnd.android.document/directory",null,null)); }
                if(kind.equals("malformed") || kind.equals("size")) {
                    byte[] bytes=kind.equals("size")?new byte[86]:row==null?null:scan.vaultCandidates().get(0).bytes();
                    if(kind.equals("malformed")) bytes[0]^=1;
                    candidates= new ArrayList<>(List.of(new org.totipo.android.provider.ProviderSnapshot.Bytes(scan.epoch(),row,87,org.totipo.android.provider.ProviderSnapshot.ByteState.PRESENT,bytes,org.totipo.android.provider.ProviderSnapshot.Issue.NONE)));
                }
                port.snapshot=new org.totipo.android.provider.ProviderSnapshot.Scan(scan.epoch(),scan.tree(),
                    new org.totipo.android.provider.ProviderSnapshot.Listing(scan.epoch(),scan.tree().rootId(),rows,kind.equals("loading")?org.totipo.android.provider.ProviderSnapshot.State.INCOMPLETE_LOADING:org.totipo.android.provider.ProviderSnapshot.State.COMPLETE,List.of()),scan.directories(),scan.state(),scan.issues(),candidates);
                prepareJoin(); char[] credential=password();assertFalse(controller.joinExistingVault(credential));await(this::providerDone);
                assertArrayEquals(new char[credential.length],credential);assertFalse(kind,Files.exists(localRoot().resolve("vault")));assertEquals(0,real.opens);assertEquals(1,candidateAuthentications.get());
            }
            port.offline=true;prepareJoin();await(this::providerDone);
            assertFalse(Files.exists(localRoot().resolve("vault")));
        } finally { ForegroundVaultCoordinatorTest.removeFixture(); }
    }
    @Test public void joinFreshRecheckRejectsChangedCandidateAndLocalOrphans() throws Exception {
        ForegroundVaultCoordinatorTest.realCoreFixture();
        try {
            var port=startBound();var scan=ForegroundVaultCoordinatorTest.productFixtureScan();port.snapshot=scan;
            var count=new AtomicInteger();port.onScan=() -> { if(count.incrementAndGet()==3) {
                byte[] bytes=scan.vaultCandidates().get(0).bytes();bytes[86]^=1;
                port.snapshot=ForegroundVaultCoordinatorTest.withVault(new org.totipo.android.provider.ProviderSnapshot.Scan(scan.epoch(),scan.tree(),
                    new org.totipo.android.provider.ProviderSnapshot.Listing(scan.epoch(),scan.tree().rootId(),scan.root().rows().stream().filter(r -> !"vault".equals(r.displayName())).toList(),scan.root().state(),List.of()),scan.directories(),scan.state(),List.of()),bytes);
            }};
            prepareJoin(); accept(() -> controller.joinExistingVault("M1H disposable fixture".toCharArray()));await(this::providerDone);
            assertEquals("Sync folder changed. Try again.",controller.syncView().message());assertFalse(Files.exists(localRoot().resolve("vault")));
            port.onScan=() -> {};port.snapshot=scan;
            Files.createDirectories(localRoot().resolve("objects-v1"));Files.write(localRoot().resolve("objects-v1").resolve("a".repeat(64)),new byte[]{1});
            prepareJoin(); assertFalse(controller.joinExistingVault("M1H disposable fixture".toCharArray()));await(this::providerDone);
            assertFalse(Files.exists(localRoot().resolve("vault")));assertEquals(0,real.opens);
        } finally { ForegroundVaultCoordinatorTest.removeFixture(); }
    }
    @Test public void joinCancellationDuringBlockedProviderWipesCredentialAndLeavesLaneOccupied() throws Exception {
        ForegroundVaultCoordinatorTest.realCoreFixture();
        try {
            var port=startBound();port.snapshot=ForegroundVaultCoordinatorTest.productFixtureScan();
            prepareJoin(); var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
            port.onScan=() -> { assertEquals("Totipo-provider-io",Thread.currentThread().getName());block(entered,release); };
            char[] credential="M1H disposable fixture".toCharArray();accept(() -> controller.joinExistingVault(credential));assertTrue(entered.await(10,TimeUnit.SECONDS));
            try {
                controller.cancelJoin();await(() -> Arrays.equals(new char[credential.length],credential));
                assertFalse(controller.canJoinExistingVault());assertFalse(controller.checkSyncFolder());
                assertFalse(Files.exists(localRoot().resolve("vault")));
            } finally { release.countDown(); }
            await(this::providerDone);assertEquals(0,real.opens);assertEquals(State.NO_LOCAL_VAULT,controller.snapshot().state());
        } finally { ForegroundVaultCoordinatorTest.removeFixture(); }
    }
    @Test public void folderChangeBeforeAuthenticationResultSuppressesEnrollment() throws Exception {
        ForegroundVaultCoordinatorTest.realCoreFixture();
        try {
            var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
            var port=startBound(new RealBackend() {
                boolean authenticateCandidate(byte[] exact,char[] credential) { block(entered,release);return super.authenticateCandidate(exact,credential); }
            });
            port.snapshot=ForegroundVaultCoordinatorTest.productFixtureScan();prepareJoin();
            char[] credential="M1H disposable fixture".toCharArray();accept(() -> controller.joinExistingVault(credential));assertTrue(entered.await(10,TimeUnit.SECONDS));
            try { assertTrue(controller.disconnectSyncFolder()); } finally { release.countDown(); }
            await(this::providerDone);assertFalse(Files.exists(localRoot().resolve("vault")));assertArrayEquals(new char[credential.length],credential);
            assertEquals(0,real.opens);assertEquals(org.totipo.android.sync.SyncFolderBinding.Status.NOT_CONFIGURED,controller.syncView().binding().status());
        } finally { ForegroundVaultCoordinatorTest.removeFixture(); }
    }
    @Test public void initializeLockDuringBlockedCreatePreventsWriteAndStaleCompletion() throws Exception {
        var port=outboundFixture(1);port.vaultBytes=null;port.directoryPresent=false;
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        port.onVaultCreate=() -> block(entered,release);
        accept(() -> controller.initializeSyncFolder());assertTrue(entered.await(10,TimeUnit.SECONDS));
        try {
            assertFalse(controller.initializeSyncFolder());accept(() -> controller.lock());state(State.LOCKED);
            assertFalse(controller.canPublishLocalChanges());
        } finally { release.countDown(); }
        await(this::providerDone);assertEquals(0,port.vaultOutputs);assertEquals(0,port.directoryCreates);assertEquals(State.LOCKED,controller.snapshot().state());
    }
    @Test public void initializeThenManualPublishOnlyAndRepeatedInitializationNoWrites() throws Exception {
        var port=outboundFixture(1);var session=real.session;byte[] exact=port.vaultBytes.clone();port.vaultBytes=null;port.directoryPresent=false;
        accept(() -> controller.initializeSyncFolder());await(this::providerDone);
        assertArrayEquals(exact,port.vaultBytes);assertTrue(port.directoryPresent);assertTrue(port.objects.isEmpty());assertEquals(1,port.vaultCreates);
        accept(() -> controller.publishLocalChanges());await(this::providerDone);assertEquals(1,port.objects.size());assertSame(session,real.session);
        accept(() -> controller.initializeSyncFolder());await(this::providerDone);assertEquals(1,port.vaultCreates);assertEquals(1,port.directoryCreates);
        assertArrayEquals(exact,Files.readAllBytes(localRoot().resolve("vault")));
    }
    private org.totipo.android.sync.PublicationPort outboundFixture() throws Exception { return outboundFixture(3); }
    private org.totipo.android.sync.PublicationPort outboundFixture(int count) throws Exception {
        owner = TestReplicaOwners.create(temporary.newFolder().toPath());
        var port = new org.totipo.android.sync.PublicationPort();
        controller = new AndroidVaultController(owner, dispatcher, new RealBackend(), new TotpPresentation.Time() {
            public java.time.Instant wall() { return java.time.Instant.now(); }
            public long elapsedMillis() { return 0; }
        }, null, new org.totipo.android.sync.SyncFolderBinding(port));
        state(State.NO_LOCAL_VAULT); await(this::providerDone);
        accept(() -> controller.create(password())); state(State.OPEN);
        port.vaultBytes = Files.readAllBytes(localRoot().resolve("vault"));
        for (int i = 0; i < count; i++) add(enrollment("public", "test " + i, org.totipo.TotpAlgorithm.SHA1, 6, 30, rfcSecret()), AddTokenOutcome.Status.ADDED);
        await(() -> controller.snapshot().view().tokens().size() == count);
        return port;
    }
    @Test public void identityFailureStatusesKeepReadyAndBlockBothControllerDirections() throws Exception {
        var port = outboundFixture(1); var session = real.session;
        byte[] canonical = port.vaultBytes.clone();
        for (String scenario : List.of("missing", "short", "oversized", "magic", "version", "different", "unavailable", "duplicate", "duplicateDifferent", "directory", "loading")) {
            port.snapshotTransform = scan -> {
                var original = scan.vaultCandidates().get(0);
                var doc = original.document();
                var rows = new ArrayList<>(scan.root().rows());
                byte[] bytes = canonical.clone();
                var state = org.totipo.android.provider.ProviderSnapshot.ByteState.PRESENT;
                var rootState = org.totipo.android.provider.ProviderSnapshot.State.COMPLETE;
                switch (scenario) {
                    case "missing" -> rows.remove(doc);
                    case "short" -> bytes = new byte[86];
                    case "oversized" -> bytes = new byte[88];
                    case "magic" -> bytes[0] ^= 1;
                    case "version" -> bytes[10] = 2;
                    case "different", "duplicateDifferent" -> bytes[86] ^= 1;
                    case "unavailable" -> state = org.totipo.android.provider.ProviderSnapshot.ByteState.UNAVAILABLE;
                    case "duplicate" -> rows.add(doc);
                    case "directory" -> {
                        rows.remove(doc);
                        doc = new org.totipo.android.provider.ProviderSnapshot.Document(doc.tree(), doc.locator(), doc.id(), doc.parentId(),
                                "vault", "vnd.android.document/directory", null, null); rows.add(doc);
                    }
                    case "loading" -> rootState = org.totipo.android.provider.ProviderSnapshot.State.INCOMPLETE_LOADING;
                }
                var candidates = new ArrayList<org.totipo.android.provider.ProviderSnapshot.Bytes>();
                if (!scenario.equals("missing")) candidates.add(new org.totipo.android.provider.ProviderSnapshot.Bytes(scan.epoch(), doc, 87,
                        state, bytes, org.totipo.android.provider.ProviderSnapshot.Issue.NONE));
                if (scenario.equals("duplicate") || scenario.equals("duplicateDifferent")) {
                    if (scenario.equals("duplicateDifferent")) rows.add(doc);
                    candidates.add(original);
                }
                return new org.totipo.android.provider.ProviderSnapshot.Scan(scan.epoch(), scan.tree(),
                        new org.totipo.android.provider.ProviderSnapshot.Listing(scan.epoch(), scan.tree().rootId(), rows, rootState, List.of()),
                        scan.directories(), scan.state(), scan.issues(), candidates);
            };
            String expected = scenario.equals("missing") ? "Sync folder has no Totipo vault."
                    : scenario.equals("different") ? "Sync folder belongs to a different Totipo vault."
                    : "Sync folder vault cannot be verified.";
            for (BooleanSupplier operation : List.<BooleanSupplier>of(controller::importProviderChanges, controller::publishLocalChanges)) {
                accept(operation); await(this::providerDone);
                assertEquals(scenario, expected, controller.syncView().message());
                assertEquals(org.totipo.android.sync.SyncFolderBinding.Status.READY, controller.syncView().binding().status());
                assertEquals(0, port.creates); assertEquals(0, port.outputs); assertTrue(port.objects.isEmpty());
                assertEquals(1, controller.snapshot().view().tokens().size()); assertSame(session, real.session);
                assertEquals(1, real.creates); assertEquals(0, real.opens);
                assertArrayEquals(canonical, Files.readAllBytes(localRoot().resolve("vault")));
            }
        }
    }
    @Test public void outboundExactPostflightRetryPreservesSessionStoreAndRevealedCode() throws Exception {
        var port = outboundFixture(); var session = real.session; var coordinator = real.coordinator;
        int refreshes = real.refreshes;
        accept(() -> controller.showCode(controller.snapshot().view().tokens().get(0).id()));
        await(() -> controller.snapshot().revealedCode() != null);
        var shown = controller.snapshot().revealedCode();
        accept(() -> controller.publishLocalChanges()); await(this::providerDone);
        assertEquals("Local changes published", controller.syncView().message());
        assertEquals(3, port.creates); assertEquals(3, port.outputs);
        assertEquals(shown, controller.snapshot().revealedCode());
        assertSame(session, real.session); assertSame(coordinator, real.coordinator); assertEquals(refreshes, real.refreshes);
        assertEquals(1, real.creates); assertEquals(0, real.opens); assertEquals(0, real.closes);
        accept(() -> controller.publishLocalChanges()); await(this::providerDone);
        assertEquals("No local changes to publish", controller.syncView().message()); assertEquals(3, port.creates);
    }
    @Test public void uncertainWriteRetryFreshPreflightAvoidsDuplicateAndExceptionCanBeVerified() throws Exception {
        var port = outboundFixture(); port.fault = "close";
        accept(() -> controller.publishLocalChanges()); await(this::providerDone);
        assertTrue(controller.syncView().message().startsWith("Publication uncertain")); assertEquals(1, port.creates);
        port.fault = "";
        accept(() -> controller.publishLocalChanges()); await(this::providerDone);
        assertEquals("Local changes published", controller.syncView().message()); assertEquals(3, port.creates);
        accept(() -> controller.publishLocalChanges()); await(this::providerDone);
        assertEquals("No local changes to publish", controller.syncView().message()); assertEquals(3, port.creates);
    }
    @Test public void uncertainButPersistedSingleObjectRetryDoesNoCreateAndFreshEvidenceCanVerifyCloseFailure() throws Exception {
        var port = outboundFixture(1);
        var firstPort = port;
        port.onScan = () -> { if (firstPort.scans == 2) throw new IllegalStateException("postflight unavailable"); };
        accept(() -> controller.publishLocalChanges()); await(this::providerDone);
        assertTrue(controller.syncView().message().startsWith("Publication uncertain")); assertEquals(1, port.creates);
        port.onScan = () -> {};
        accept(() -> controller.publishLocalChanges()); await(this::providerDone);
        assertEquals("No local changes to publish", controller.syncView().message()); assertEquals(1, port.creates);
        lock(); controller.shutdown();
        port = outboundFixture(1); port.fault = "close";
        accept(() -> controller.publishLocalChanges()); await(this::providerDone);
        assertEquals("Local changes published", controller.syncView().message()); assertEquals(1, port.creates);
    }
    @Test public void explicitReadBackFailureRemainsUncertainDespiteExactNamespaceAndRetryDoesNotDuplicate() throws Exception {
        for (String fault : List.of("different", "unavailable")) {
            var port = outboundFixture(1); port.fault = fault;
            accept(() -> controller.publishLocalChanges()); await(this::providerDone);
            assertTrue(controller.syncView().message().startsWith("Publication uncertain")); assertEquals(1, port.creates);
            port.fault = "";
            accept(() -> controller.publishLocalChanges()); await(this::providerDone);
            assertEquals("No local changes to publish", controller.syncView().message()); assertEquals(1, port.creates);
            lock(); controller.shutdown();
        }
    }
    @Test public void noSuccessBeforeFreshPostflightReturns() throws Exception {
        var port = outboundFixture(1);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        port.onScan = () -> { if (port.scans == 2) block(entered, release); };
        accept(() -> controller.publishLocalChanges()); assertTrue(entered.await(10, TimeUnit.SECONDS));
        try {
            assertEquals(1, port.creates); assertEquals("Publishing…", controller.syncView().message());
            assertEquals(State.OPEN, controller.snapshot().state()); assertEquals(1L, release.getCount());
        } finally { release.countDown(); }
        await(this::providerDone); assertEquals("Local changes published", controller.syncView().message());
    }
    @Test public void outboundIncompleteReadOnlyCapabilityAndLocalInvalidNeverMutate() throws Exception {
        var port = outboundFixture();
        for (var state : org.totipo.android.provider.ProviderSnapshot.State.values()) if (state != org.totipo.android.provider.ProviderSnapshot.State.COMPLETE) {
            port.coverage = state;
            accept(() -> controller.publishLocalChanges()); await(this::providerDone); assertEquals(0, port.creates);
            if (!controller.canPublishLocalChanges()) { port.coverage = org.totipo.android.provider.ProviderSnapshot.State.COMPLETE;
                accept(() -> controller.checkSyncFolder()); await(this::providerDone); }
        }
        port.coverage = org.totipo.android.provider.ProviderSnapshot.State.COMPLETE;
        port.permissions.put(port.stored.uri(), new org.totipo.android.sync.SyncFolderBinding.Grants(true, false));
        accept(() -> controller.checkSyncFolder()); await(this::providerDone);
        assertTrue(controller.canImportProviderChanges()); assertFalse(controller.canPublishLocalChanges());
        port.permissions.put(port.stored.uri(), new org.totipo.android.sync.SyncFolderBinding.Grants(true, true));
        accept(() -> controller.checkSyncFolder()); await(this::providerDone);
        // Test-only corruption of disposable fixture, never production filesystem access.
        try (var paths = Files.list(localRoot().resolve("objects-v1"))) { Files.write(paths.filter(p -> p.getFileName().toString().matches("[0-9a-f]{64}")).findFirst().orElseThrow(), new byte[1024]); }
        accept(() -> controller.publishLocalChanges()); await(this::providerDone);
        assertTrue(controller.syncView().message(), controller.syncView().message().startsWith("Local publication source invalid")); assertEquals(0, port.creates);
    }
    @Test public void outboundBlockedCreateWriteLockAndBindingChangeRemainResponsiveAndBounded() throws Exception {
        for (String phase : List.of("create", "write")) for (String action : List.of("lock", "disconnect", "replace")) {
            var port = outboundFixture(); int refreshes = real.refreshes;
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            Runnable blocked = () -> block(entered, release);
            if (phase.equals("create")) port.onCreate = blocked; else port.onWrite = blocked;
            accept(() -> controller.publishLocalChanges()); assertTrue(entered.await(10, TimeUnit.SECONDS));
            try {
                await(this::idle); assertEquals(State.OPEN, controller.snapshot().state());
                assertEquals("Publishing…", controller.syncView().message());
                for (int i = 0; i < 100; i++) { assertFalse(controller.publishLocalChanges()); assertFalse(controller.importProviderChanges()); assertFalse(controller.checkSyncFolder()); }
                var laneField = AndroidVaultController.class.getDeclaredField("providerIo"); laneField.setAccessible(true);
                var lane = laneField.get(controller); var executorField = lane.getClass().getDeclaredField("executor"); executorField.setAccessible(true);
                var executor = (ThreadPoolExecutor)executorField.get(lane);
                assertEquals(1, executor.getLargestPoolSize()); assertEquals(0, executor.getQueue().size());
                // Detached batch does not block new local authorship.
                add(enrollment("public", "later", org.totipo.TotpAlgorithm.SHA1, 6, 30, rfcSecret()), AddTokenOutcome.Status.ADDED);
                if (action.equals("lock")) { lock(); accept(() -> controller.unlock(password())); state(State.OPEN); }
                else if (action.equals("disconnect")) { accept(() -> controller.disconnectSyncFolder()); await(this::idle); assertNull(controller.initialTreeUri()); }
                else { accept(() -> controller.chooseSyncFolder(false, "content://fixture/tree/b", 3)); await(this::idle); }
                assertEquals(1L, release.getCount());
            } finally { release.countDown(); }
            await(this::providerDone); assertEquals(1, port.creates); assertEquals(refreshes, real.refreshes);
            assertFalse(controller.syncView().message().contains("Local changes published"));
            if (action.equals("replace")) assertEquals("content://fixture/tree/b", controller.initialTreeUri());
            lock(); controller.shutdown();
        }
    }
    private AddTokenRequest enrollment(String issuer, String account, org.totipo.TotpAlgorithm algorithm, int digits, long period, char[] text) {
        return new AddTokenRequest(issuer, account, algorithm, digits, period, text);
    }
    private char[] rfcSecret() { return "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ".toCharArray(); }
    private void add(AddTokenRequest request, AddTokenOutcome.Status expected) throws Exception {
        accept(() -> controller.addToken(request));
        await(() -> idle() && controller.snapshot().addOutcome() != null);
        assertEquals(expected, controller.snapshot().addOutcome().status());
    }
    @Test public void manualDefaultSameSessionObservedConcealedAndJavaTotp() throws Exception {
        create(); var session = real.session; var coordinator = real.coordinator;
        char[] input = rfcSecret();
        add(enrollment("RFC", "account", org.totipo.TotpAlgorithm.SHA1, 6, 30, input), AddTokenOutcome.Status.ADDED);
        await(() -> controller.snapshot().view().tokens().size() == 1);
        assertArrayEquals(new char[input.length], input);
        assertNull(controller.snapshot().revealedCode());
        var row = controller.snapshot().view().tokens().get(0);
        assertEquals("RFC", row.alternatives().get(0).issuer());
        assertEquals(30, row.alternatives().get(0).period().getSeconds());
        var state = session.state();
        assertEquals("287082", state.generateTotp(state.token(row.id()).orElseThrow().alternatives().get(0), java.time.Instant.ofEpochSecond(59)).code());
        assertSame(session, real.session); assertSame(coordinator, real.coordinator);
        assertEquals(1, real.creates); assertEquals(0, real.opens); assertEquals(0, real.closes);
        accept(() -> controller.showCode(row.id())); await(() -> controller.snapshot().revealedCode() != null);
        add(enrollment("RFC", "account", org.totipo.TotpAlgorithm.SHA1, 6, 30, rfcSecret()), AddTokenOutcome.Status.ADDED);
        assertNull(controller.snapshot().revealedCode());
        await(() -> controller.snapshot().view().tokens().size() == 2);
        assertEquals(2, controller.snapshot().view().tokens().stream().map(t -> t.id()).distinct().count());
        lock(); accept(() -> controller.unlock(password())); state(State.OPEN);
        await(() -> controller.snapshot().view().tokens().size() == 2);
    }
    @Test public void supportedAlgorithmsDigitsAndCustomPeriodThroughRealAuthoring() throws Exception {
        create(); int count = 0;
        for (var algorithm : org.totipo.TotpAlgorithm.values()) for (int digits : new int[]{6,7,8}) {
            add(enrollment("", "", algorithm, digits, 45, rfcSecret()), AddTokenOutcome.Status.ADDED);
            int expected = ++count;
            await(() -> controller.snapshot().view().tokens().size() == expected);
            assertTrue(controller.snapshot().view().tokens().stream().anyMatch(t -> t.alternatives().get(0).algorithm() == algorithm
                    && t.alternatives().get(0).digits() == digits && t.alternatives().get(0).period().getSeconds() == 45));
            var token = real.session.state().tokens().stream().filter(t -> t.alternatives().get(0).descriptor().algorithm() == algorithm
                    && t.alternatives().get(0).descriptor().digits() == digits).findFirst().orElseThrow();
            assertTrue(real.session.state().generateTotp(token.alternatives().get(0), java.time.Instant.ofEpochSecond(59)).code().matches("[0-9]{" + digits + "}"));
            assertNull(controller.snapshot().revealedCode());
        }
    }
    @Test public void malformedSecretNeverReachesEditorSaveAndWipesInput() throws Exception {
        create();
        for (String value : List.of("", "M0", "MZ", "MY=")) {
            char[] input = value.toCharArray();
            add(enrollment("RFC", "account", org.totipo.TotpAlgorithm.SHA1, 6, 30, input), AddTokenOutcome.Status.INVALID_SECRET);
            assertArrayEquals(new char[input.length], input);
            assertEquals(0, real.saves); assertTrue(controller.snapshot().view().tokens().isEmpty());
        }
    }
    @Test public void javaFieldBoundsNoTruncationOrNormalization() throws Exception {
        create();
        for (String field : List.of("x".repeat(257), "é".repeat(129), "\ud800")) {
            add(enrollment(field, "", org.totipo.TotpAlgorithm.SHA1, 6, 30, rfcSecret()), AddTokenOutcome.Status.INVALID_FIELDS);
            add(enrollment("", field, org.totipo.TotpAlgorithm.SHA1, 6, 30, rfcSecret()), AddTokenOutcome.Status.INVALID_FIELDS);
            assertTrue(controller.snapshot().view().tokens().isEmpty());
        }
        for (int digits : new int[]{5,9}) add(enrollment("", "", org.totipo.TotpAlgorithm.SHA1, digits, 30, rfcSecret()), AddTokenOutcome.Status.INVALID_FIELDS);
        for (long period : new long[]{0,4294967296L}) add(enrollment("", "", org.totipo.TotpAlgorithm.SHA1, 6, period, rfcSecret()), AddTokenOutcome.Status.INVALID_FIELDS);
        add(enrollment("", "", null, 6, 30, rfcSecret()), AddTokenOutcome.Status.INVALID_FIELDS);
        add(enrollment("é".repeat(128), "  account  ", org.totipo.TotpAlgorithm.SHA1, 6, 4294967295L, rfcSecret()), AddTokenOutcome.Status.ADDED);
        await(() -> controller.snapshot().view().tokens().size() == 1);
        var d = controller.snapshot().view().tokens().get(0).alternatives().get(0);
        assertEquals("é".repeat(128), d.issuer()); assertEquals("  account  ", d.account());
    }
    @Test public void uncertainPublicationKeepsActualOutcomeNoAutomaticRetryOrFabricatedRow() throws Exception {
        create(); real.tokenWriteFault = new org.totipo.spi.ObjectWrite.Failed(org.totipo.spi.StoreFailure.UNAVAILABLE);
        add(enrollment("", "", org.totipo.TotpAlgorithm.SHA1, 6, 30, rfcSecret()), AddTokenOutcome.Status.PUBLICATION_UNCERTAIN);
        assertEquals(1, real.saves); assertTrue(controller.snapshot().view().tokens().isEmpty());
        assertFalse(controller.snapshot().message().contains("Token added"));
        accept(() -> controller.refresh()); state(State.OPEN);
        assertEquals(AddTokenOutcome.Status.PUBLICATION_UNCERTAIN, controller.snapshot().addOutcome().status());
        real.tokenWriteFault = null;
    }
    @Test public void uncertaintyCanHavePersistedAndExplicitRefreshObservesSameSession() throws Exception {
        create(); var session = real.session;
        real.persistBeforeTokenFault = true;
        real.tokenWriteFault = new org.totipo.spi.ObjectWrite.Uncertain(org.totipo.spi.StoreFailure.UNAVAILABLE);
        add(enrollment("", "", org.totipo.TotpAlgorithm.SHA1, 6, 30, rfcSecret()), AddTokenOutcome.Status.PUBLICATION_UNCERTAIN);
        assertFalse(controller.snapshot().message().contains("Token added"));
        real.tokenWriteFault = null;
        accept(() -> controller.refresh());
        await(() -> controller.snapshot().state() == State.OPEN && controller.snapshot().view().tokens().size() == 1);
        assertEquals(AddTokenOutcome.Status.PUBLICATION_UNCERTAIN, controller.snapshot().addOutcome().status());
        assertSame(session, real.session); assertEquals(1, real.saves); assertNull(controller.snapshot().revealedCode());
    }
    @Test public void lockOwnershipRejectsAddBeforeClosureCompletes() throws Exception {
        create(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        real.operationHook = name -> {
            if (name.equals("close")) { entered.countDown();
                try { assertTrue(release.await(30, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new AssertionError(e); }
            }
        };
        accept(() -> controller.lock()); assertTrue(entered.await(10, TimeUnit.SECONDS));
        try {
            assertEquals(State.LOCKING, controller.snapshot().state());
            char[] text = rfcSecret();
            assertFalse(controller.addToken(enrollment("", "", org.totipo.TotpAlgorithm.SHA1, 6, 30, text)));
            assertArrayEquals(new char[text.length], text); assertEquals(0, real.saves);
        } finally { release.countDown(); real.operationHook = name -> {}; }
        state(State.LOCKED); assertNull(controller.snapshot().view()); assertEquals(0, real.saves);
    }
    @Test public void definiteJavaFailureReasonRetainedAndNoFabricatedRow() throws Exception {
        create();
        for (var reason : org.totipo.SaveResult.Reason.values()) {
            real.saveFailure = reason;
            add(enrollment("", "", org.totipo.TotpAlgorithm.SHA1, 6, 30, rfcSecret()), AddTokenOutcome.Status.FAILED);
            assertEquals(reason, controller.snapshot().addOutcome().reason());
            assertTrue(controller.snapshot().view().tokens().isEmpty());
        }
        real.saveFailure = null;
    }
    @Test public void busyAddRejectsDuplicateLockRefreshSyncAndLockFirstRejectsAdd() throws Exception {
        create(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        real.operationHook = name -> {
            if (name.equals("publishObject")) { entered.countDown();
                try { assertTrue(release.await(30, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new AssertionError(e); }
            }
        };
        accept(() -> controller.addToken(enrollment("", "", org.totipo.TotpAlgorithm.SHA1, 6, 30, rfcSecret())));
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        try {
            char[] duplicate = rfcSecret();
            assertFalse(controller.addToken(enrollment("", "", org.totipo.TotpAlgorithm.SHA1, 6, 30, duplicate)));
            assertArrayEquals(new char[duplicate.length], duplicate);
            assertFalse(controller.lock()); assertFalse(controller.refresh()); assertFalse(controller.sync(null));
            assertEquals(1, real.saves);
        } finally { release.countDown(); real.operationHook = name -> {}; }
        await(() -> idle() && controller.snapshot().state() == State.OPEN);
        await(() -> controller.snapshot().view().tokens().size() == 1);
        lock(); char[] rejected = rfcSecret();
        assertFalse(controller.addToken(enrollment("", "", org.totipo.TotpAlgorithm.SHA1, 6, 30, rejected)));
        assertArrayEquals(new char[rejected.length], rejected); assertEquals(1, real.saves);
    }
    @Test public void productSourceBoundaryHasNoRawActivityOwnershipOrCredentialPersistence() throws Exception {
        String activity = Files.readString(Path.of("src/main/java/org/totipo/android/MainActivity.java"));
        for (String forbidden : List.of("VaultSession", "Totipo.open", "Totipo.create", "LocalReplicaOwner", "NioTotipoStore", "ForegroundVaultCoordinator", "Flow.", "Future", "SharedPreferences")) assertFalse(forbidden, activity.contains(forbidden));
        for (var field : AndroidVaultController.class.getDeclaredFields()) assertNotEquals(char[].class, field.getType());
        String source = Files.readString(Path.of("src/main/java/org/totipo/android/AndroidVaultController.java"));
        int begin = source.indexOf("public synchronized boolean sync(Scan scan)");
        String sync = source.substring(begin, source.indexOf("public synchronized boolean lock()", begin));
        for (String forbidden : List.of("char[]", "password", "backend.open", "backend.create", "closeOwned")) assertFalse(forbidden, sync.contains(forbidden));
        assertTrue(source.contains("new ArrayBlockingQueue<>(1)"));
        assertFalse(source.contains("newSingleThreadExecutor"));
        assertTrue(activity.contains("setSaveEnabled(false)"));
        assertFalse(activity.contains("putString")); assertFalse(activity.contains("putCharArray"));
    }

    private ForegroundVaultCoordinator.ObservedToken lifecycleRow() {
        return controller.snapshot().view().tokens().get(0);
    }
    private void finishChange(TokenChange change, String issuer, String account, int option) throws Exception {
        assertTrue(controller.confirmTokenChange(change, issuer, account, option));
        await(() -> idle() && controller.tokenChangeResult() != null);
    }
    private void publishLifecycle(org.totipo.android.sync.PublicationPort port) throws Exception {
        int before = port.objects.size();
        accept(() -> controller.publishLocalChanges()); await(this::providerDone);
        assertTrue(controller.syncView().message(), controller.syncView().message().contains("Local changes published"));
        assertTrue(port.objects.size() > before);
    }
    @Test public void lifecycleEditRetainsCredentialHistorySessionAndExplicitPublish() throws Exception {
        var port = outboundFixture(1); var session = real.session;
        await(this::providerDone);
        var row = lifecycleRow(); assertTrue(TokenChange.live(row));
        var old = session.state(); var alternative = old.token(row.id()).orElseThrow().alternatives().get(0);
        var code = old.generateTotp(alternative, java.time.Instant.ofEpochSecond(59)).code();
        var objects = real.coordinator.outboundSnapshot(); int scans = port.scans;
        var edit = controller.beginTokenChange(row.id(), TokenChange.Kind.EDIT);
        assertNotNull(edit); assertEquals("public", edit.basis().alternatives().get(0).issuer());
        assertEquals("test 0", edit.basis().alternatives().get(0).account());
        assertFalse(controller.canAddToken()); assertFalse(controller.canPublishLocalChanges());
        assertFalse(controller.refresh()); assertFalse(controller.canManageSyncFolder());
        finishChange(edit, " Public <&> ", "new account", 0);
        assertEquals(TokenChange.Result.SAVED, controller.tokenChangeResult());
        await(() -> lifecycleRow().alternatives().get(0).account().equals("new account"));
        assertEquals(" Public <&> ", lifecycleRow().alternatives().get(0).issuer());
        assertEquals("test 0", alternative.descriptor().account());
        assertEquals(code, session.state().generateTotp(session.state().token(row.id()).orElseThrow().alternatives().get(0), java.time.Instant.ofEpochSecond(59)).code());
        assertSame(session, real.session); assertNull(controller.snapshot().revealedCode());
        assertEquals(scans, port.scans); assertTrue(port.objects.isEmpty());
        var after = real.coordinator.outboundSnapshot(); assertEquals(objects.size() + 1, after.size());
        for (var object : objects) assertArrayEquals(object.representation(), after.stream().filter(o -> o.id().equals(object.id())).findFirst().orElseThrow().representation());
        publishLifecycle(port);
    }
    @Test public void lifecycleDeleteIsTombstoneConcealsRetainsHistoryAndExplicitPublish() throws Exception {
        var port = outboundFixture(1); await(this::providerDone); var session = real.session;
        var row = lifecycleRow(); var objects = real.coordinator.outboundSnapshot();
        accept(() -> controller.showCode(row.id())); await(this::idle); assertNotNull(controller.snapshot().revealedCode());
        var cancel = controller.beginTokenChange(row.id(), TokenChange.Kind.DELETE); assertNotNull(cancel);
        assertNull(controller.snapshot().revealedCode()); controller.cancelTokenChange(cancel);
        assertEquals(objects.size(), real.coordinator.outboundSnapshot().size());
        var change = controller.beginTokenChange(row.id(), TokenChange.Kind.DELETE);
        finishChange(change, null, null, 0); assertEquals(TokenChange.Result.SAVED, controller.tokenChangeResult());
        await(() -> lifecycleRow().alternatives().get(0).status() == org.totipo.TokenStatus.TOMBSTONED);
        assertFalse(TokenChange.live(lifecycleRow())); assertFalse(TokenListAdapter.usable(lifecycleRow()));
        assertNull(controller.beginTokenChange(row.id(), TokenChange.Kind.DELETE));
        assertNull(controller.beginTokenChange(row.id(), TokenChange.Kind.EDIT));
        assertNull(controller.snapshot().revealedCode()); assertSame(session, real.session); assertTrue(port.objects.isEmpty());
        assertEquals(objects.size() + 1, real.coordinator.outboundSnapshot().size());
        for (var object : objects) assertTrue(Files.exists(localRoot().resolve("objects-v1").resolve(object.id().hex())));
        publishLifecycle(port);
    }
    @Test public void lifecycleCancelInvalidAndLockNeverAuthor() throws Exception {
        var port = outboundFixture(1); await(this::providerDone); var row = lifecycleRow();
        int before = real.coordinator.outboundSnapshot().size();
        var edit = controller.beginTokenChange(row.id(), TokenChange.Kind.EDIT); controller.cancelTokenChange(edit);
        assertFalse(controller.confirmTokenChange(edit, "x", "x", 0));
        var invalid = controller.beginTokenChange(row.id(), TokenChange.Kind.EDIT);
        finishChange(invalid, "x".repeat(257), "account", 0); assertEquals(TokenChange.Result.INVALID, controller.tokenChangeResult());
        assertEquals(before, real.coordinator.outboundSnapshot().size());
        for (var kind : List.of(TokenChange.Kind.EDIT, TokenChange.Kind.DELETE)) {
            await(this::idle); var pending = controller.beginTokenChange(row.id(), kind); assertNotNull(pending);
            lock(); assertNull(controller.pendingTokenChange()); assertFalse(controller.confirmTokenChange(pending, "x", "x", 0));
            accept(() -> controller.unlock(password())); state(State.OPEN); await(this::idle);
        }
        assertEquals(before, real.coordinator.outboundSnapshot().size()); assertTrue(port.objects.isEmpty());
    }
    @Test public void lifecycleStaleEditAndDeleteDoNotRetry() throws Exception {
        var port = outboundFixture(1); await(this::providerDone);
        for (var kind : List.of(TokenChange.Kind.EDIT, TokenChange.Kind.DELETE)) {
            await(this::idle); var row = lifecycleRow(); var pending = controller.beginTokenChange(row.id(), kind);
            try (var update = real.session.state().update(real.session.state().token(row.id()).orElseThrow().alternatives().get(0))) {
                assertTrue(update.account(kind.name()).save() instanceof org.totipo.SaveResult.Saved);
            }
            await(() -> lifecycleRow().alternatives().get(0).account().equals(kind.name())); await(this::idle);
            int count = real.coordinator.outboundSnapshot().size();
            finishChange(pending, "stale", "stale", 0); assertEquals(TokenChange.Result.STALE, controller.tokenChangeResult());
            assertEquals(count, real.coordinator.outboundSnapshot().size()); assertTrue(controller.snapshot().message().contains("This token changed."));
        }
        assertTrue(port.objects.isEmpty());
    }
    /** Independent Java session/store authors a legitimate sibling of the captured local head. */
    private void conflictBranch(org.totipo.android.sync.PublicationPort port, boolean deleted) throws Exception {
        var row = lifecycleRow(); Path root = temporary.newFolder().toPath();
        Files.write(root.resolve("vault"), port.vaultBytes); Files.createDirectory(root.resolve("objects-v1"));
        for (var object : real.coordinator.outboundSnapshot()) Files.write(root.resolve("objects-v1").resolve(object.id().hex()), object.representation());
        try (var remote = ((org.totipo.OpenResult.Opened)org.totipo.Totipo.open(
                org.totipo.storage.nio.NioStoreComposition.coordinatedDelegate(root, new org.totipo.storage.nio.NioDurability()), password())).session()) {
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (!(remote.state().observation() instanceof org.totipo.ObservationProgress.Finished) && System.nanoTime() < end) Thread.sleep(5);
            try (var update = remote.state().update(remote.state().token(row.id()).orElseThrow().alternatives().get(0))) {
                update.account("remote"); if (deleted) update.status(org.totipo.TokenStatus.TOMBSTONED);
                assertTrue(update.save() instanceof org.totipo.SaveResult.Saved);
            }
        }
        var edit = controller.beginTokenChange(row.id(), TokenChange.Kind.EDIT); finishChange(edit, "public", "local", 0);
        await(() -> lifecycleRow().alternatives().get(0).account().equals("local")); await(this::idle);
        try (var files = Files.list(root.resolve("objects-v1"))) { for (Path path : files.toList()) port.objects.put(path.getFileName().toString(), Files.readAllBytes(path)); }
        accept(() -> controller.importProviderChanges()); await(this::providerDone);
        await(() -> lifecycleRow().conflict()); await(this::idle);
    }
    private void resolveLifecycle(boolean deleted, String account) throws Exception {
        var port = outboundFixture(1); await(this::providerDone); var session = real.session;
        conflictBranch(port, deleted); var row = lifecycleRow(); assertTrue(TokenChange.resolvable(row));
        assertFalse(TokenListAdapter.usable(row)); assertTrue(TokenListAdapter.rowText(row).startsWith("Token conflict"));
        assertNull(controller.beginTokenChange(row.id(), TokenChange.Kind.EDIT));
        accept(() -> controller.showCode(row.id())); await(this::idle); assertNull(controller.snapshot().revealedCode());
        var change = controller.beginTokenChange(row.id(), TokenChange.Kind.RESOLVE); assertNotNull(change);
        assertEquals(2, change.basis().alternatives().size());
        controller.cancelTokenChange(change); int before = real.coordinator.outboundSnapshot().size();
        change = controller.beginTokenChange(row.id(), TokenChange.Kind.RESOLVE);
        int option = -1; for (int i=0; i<row.alternatives().size(); i++) if (row.alternatives().get(i).account().equals(account)) option=i;
        assertTrue(option >= 0); var chosen = row.alternatives().get(option); var inventory = new HashMap<>(port.objects); int scans = port.scans;
        finishChange(change, null, null, option); assertEquals(TokenChange.Result.SAVED, controller.tokenChangeResult());
        await(() -> !lifecycleRow().conflict()); assertEquals(chosen, lifecycleRow().alternatives().get(0));
        assertSame(session, real.session); assertNull(controller.snapshot().revealedCode());
        assertEquals(before+1, real.coordinator.outboundSnapshot().size()); assertEquals(scans, port.scans);
        assertEquals(inventory.keySet(), port.objects.keySet());
        for (String key : inventory.keySet()) assertArrayEquals(inventory.get(key), port.objects.get(key));
        publishLifecycle(port);
    }
    @Test public void lifecycleResolveCompleteLocalAlternative() throws Exception { resolveLifecycle(false, "local"); }
    @Test public void lifecycleResolveCompleteRemoteAlternative() throws Exception { resolveLifecycle(false, "remote"); }
    @Test public void lifecycleResolveDeletedAlternative() throws Exception { resolveLifecycle(true, "remote"); }
    @Test public void lifecycleStaleConflictAndLockRejectChoice() throws Exception {
        var port = outboundFixture(1); await(this::providerDone); conflictBranch(port, false);
        var row = lifecycleRow(); var pending = controller.beginTokenChange(row.id(), TokenChange.Kind.RESOLVE);
        try (var update = real.session.state().update(real.session.state().token(row.id()).orElseThrow().alternatives().get(0))) {
            assertTrue(update.account("advanced").save() instanceof org.totipo.SaveResult.Saved);
        }
        await(() -> lifecycleRow().alternatives().stream().anyMatch(d -> d.account().equals("advanced"))); await(this::idle);
        int before = real.coordinator.outboundSnapshot().size(); finishChange(pending, null, null, 0);
        assertEquals(TokenChange.Result.STALE, controller.tokenChangeResult()); assertEquals(before, real.coordinator.outboundSnapshot().size());
        assertTrue(controller.snapshot().message().contains("This conflict changed."));
        var locked = controller.beginTokenChange(row.id(), TokenChange.Kind.RESOLVE); assertNotNull(locked); lock();
        assertFalse(controller.confirmTokenChange(locked, null, null, 0));
    }

    @Test public void lifecycleJavaFreshnessGateSeesUnobservedLegitimateRevision() throws Exception {
        var port=outboundFixture(1); await(this::providerDone); var row=lifecycleRow();
        Path root=temporary.newFolder().toPath();Files.write(root.resolve("vault"),port.vaultBytes);Files.createDirectory(root.resolve("objects-v1"));
        var old=real.coordinator.outboundSnapshot();
        for(var object:old)Files.write(root.resolve("objects-v1").resolve(object.id().hex()),object.representation());
        try(var branch=((org.totipo.OpenResult.Opened)org.totipo.Totipo.open(org.totipo.storage.nio.NioStoreComposition.coordinatedDelegate(root,new org.totipo.storage.nio.NioDurability()),password())).session()) {
            await(()->branch.state().observation() instanceof org.totipo.ObservationProgress.Finished);
            try(var update=branch.state().update(branch.state().token(row.id()).orElseThrow().alternatives().get(0))){assertTrue(update.account("new unseen head").save() instanceof org.totipo.SaveResult.Saved);}
        }
        var inject=new AtomicBoolean(true); List<Path> paths;try(var files=Files.list(root.resolve("objects-v1"))){paths=files.toList();}
        var pending=controller.beginTokenChange(row.id(),TokenChange.Kind.EDIT);
        real.beforeScan=delegate->{if(inject.getAndSet(false))for(Path path:paths)try {
            var result=delegate.publishObject(new org.totipo.spi.ObjectName(path.getFileName().toString()),Files.readAllBytes(path));
            assertTrue(result instanceof org.totipo.spi.ObjectWrite.Written || result instanceof org.totipo.spi.ObjectWrite.AlreadyPresentExact);
        }catch(Exception failure){throw new AssertionError(failure);}};
        finishChange(pending,"stale","must not save",0);real.beforeScan=store->{};
        assertEquals(TokenChange.Result.STALE,controller.tokenChangeResult());
        assertEquals(old.size()+1,real.coordinator.outboundSnapshot().size());
        assertTrue(port.objects.isEmpty());
        await(()->lifecycleRow().alternatives().get(0).account().equals("new unseen head"));
    }
    @Test public void lifecycleActualFailedAndUncertainLocalWritesDoNotRetryOrPublish() throws Exception {
        var port=outboundFixture(1);await(this::providerDone);var row=lifecycleRow();int count=real.coordinator.outboundSnapshot().size();
        real.tokenWriteFault=new org.totipo.spi.ObjectWrite.Failed(org.totipo.spi.StoreFailure.UNAVAILABLE);
        var failed=controller.beginTokenChange(row.id(),TokenChange.Kind.EDIT);finishChange(failed,"x","x",0);
        assertEquals(TokenChange.Result.PUBLICATION_UNCERTAIN,controller.tokenChangeResult());assertEquals(count,real.coordinator.outboundSnapshot().size());
        real.tokenWriteFault=new org.totipo.spi.ObjectWrite.Uncertain(org.totipo.spi.StoreFailure.UNAVAILABLE);real.persistBeforeTokenFault=true;
        var uncertain=controller.beginTokenChange(row.id(),TokenChange.Kind.DELETE);finishChange(uncertain,null,null,0);
        assertEquals(TokenChange.Result.PUBLICATION_UNCERTAIN,controller.tokenChangeResult());real.tokenWriteFault=null;
        assertEquals(count+1,real.coordinator.outboundSnapshot().size());assertTrue(port.objects.isEmpty());
        accept(()->controller.refresh());await(()->lifecycleRow().alternatives().get(0).status()==org.totipo.TokenStatus.TOMBSTONED);
    }

    @Test public void lifecycleIncompleteLocalObservationFailsWithoutAuthoring() throws Exception {
        var port=outboundFixture(1);await(this::providerDone);var row=lifecycleRow();int count=real.coordinator.outboundSnapshot().size();
        var change=controller.beginTokenChange(row.id(),TokenChange.Kind.EDIT);real.incompleteChangeScan=true;
        try{finishChange(change,"must not save","must not save",0);assertEquals(TokenChange.Result.FAILED,controller.tokenChangeResult());}
        finally{real.incompleteChangeScan=false;}
        assertTrue(controller.snapshot().message().contains("Totipo could not save this change."));
        assertEquals(count,real.coordinator.outboundSnapshot().size());assertTrue(port.objects.isEmpty());
    }
}
