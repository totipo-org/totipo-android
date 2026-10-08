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
    private final ProductControllerFixtures real = new ProductControllerFixtures();
    private LocalReplicaOwner owner;
    private AndroidVaultController controller;
    private final Dispatcher dispatcher = new Dispatcher() {
        public void post(Runnable action) { deliveries.add(action); }
        public void assertDispatchThread() {
            if (Thread.currentThread() != ui) threadError.set(new AssertionError("callback not UI"));
            uiChecks.incrementAndGet();
        }
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
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) { pump(); Thread.sleep(5); }
        pump(); assertTrue("Timed out: " + (controller == null ? "" : controller.snapshot()), condition.getAsBoolean());
        assertNull(threadError.get());
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
}
