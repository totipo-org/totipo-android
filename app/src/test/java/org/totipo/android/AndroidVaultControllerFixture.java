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

/** Per-test real-backend fixture. No test methods or shared live sessions.
 * JUnit creates a fresh concrete instance and TemporaryFolder for every test. */
public abstract class AndroidVaultControllerFixture {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    protected final Thread ui = Thread.currentThread();
    protected final Queue<Runnable> deliveries = new ConcurrentLinkedQueue<>();
    protected final AtomicReference<Throwable> threadError = new AtomicReference<>();
    protected final AtomicInteger workerChecks = new AtomicInteger(), uiChecks = new AtomicInteger();
    protected final AtomicInteger candidateAuthentications = new AtomicInteger();
    protected final ProductControllerFixtures real = new ProductControllerFixtures();
    protected LocalReplicaOwner owner;
    protected AndroidVaultController controller;
    protected final Dispatcher dispatcher = new Dispatcher() {
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
    protected class RealBackend extends Backend {
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
    protected void start() { start(new RealBackend()); }
    protected void start(Backend backend) { controller = new AndroidVaultController(owner, dispatcher, backend); }
    // Older Import/Publish safety tests isolate those internal operations. Daily-driver tests
    // opt into automatic dispatch below; production has no switch to disable automatic Sync.
    protected boolean automaticSync;
    protected long securityElapsed;
    protected boolean timeoutLock;
    protected void pump() {
        if (!automaticSync && controller != null) synchronized (controller) {
            try { var pending = AndroidVaultController.class.getDeclaredField("syncPending"); pending.setAccessible(true); pending.setBoolean(controller, false); }
            catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
        for (Runnable next; (next = deliveries.poll()) != null;) next.run(); }
    protected void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            pump();
            if (condition.getAsBoolean()) { assertNull(threadError.get()); return; }
            Thread.sleep(5);
        }
        fail("Timed out: " + (controller == null ? "" : controller.snapshot()));
    }
    protected boolean idle() {
        synchronized (controller) {
            try {
                var field = AndroidVaultController.class.getDeclaredField("operating"); field.setAccessible(true);
                var views = AndroidVaultController.class.getDeclaredField("viewQueued"); views.setAccessible(true);
                var reveal = AndroidVaultController.class.getDeclaredField("revealing"); reveal.setAccessible(true);
                return !field.getBoolean(controller) && !views.getBoolean(controller) && !reveal.getBoolean(controller);
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
    }
    protected boolean providerDone() {
        synchronized (controller) {
            try {
                var field = AndroidVaultController.class.getDeclaredField("providerActive"); field.setAccessible(true);
                var workerField = AndroidVaultController.class.getDeclaredField("worker"); workerField.setAccessible(true);
                var worker = (ThreadPoolExecutor) workerField.get(controller);
                // Transport completion clears providerActive before the worker rechecks
                // grants and classifies identity. Wait for that task (and queued work)
                // before reading its final status or changing the provider fixture.
                return idle() && !field.getBoolean(controller)
                        && worker.getActiveCount() == 0 && worker.getQueue().isEmpty();
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        }
    }
    protected void accept(BooleanSupplier operation) throws Exception {
        await(() -> {
            synchronized (controller) {
                // Observation callbacks can queue work as soon as the monitor is released.
                // Keep the idle check and command admission in the same critical section.
                if (!idle()) return false;
                assertTrue(operation.getAsBoolean());
                return true;
            }
        });
    }
    protected void state(State next) throws Exception { await(() -> controller.snapshot().state() == next); }
    protected void restartProcessAdmission() throws Exception {
        Path base = localRoot().getParent().getParent().getParent().getParent();
        controller.shutdown();
        var field = AndroidVaultController.class.getDeclaredField("worker"); field.setAccessible(true);
        assertTrue(((ThreadPoolExecutor)field.get(controller)).awaitTermination(10, TimeUnit.SECONDS));
        pump();
        owner = TestReplicaOwners.create(base);
    }
    protected char[] password() { return "M1J disposable".toCharArray(); }
    protected void create() throws Exception {
        start(); state(State.NO_LOCAL_VAULT);
        char[] password = password(); accept(() -> controller.create(password)); state(State.OPEN);
        assertArrayEquals(new char[password.length], password);
    }
    protected void lock() throws Exception { accept(() -> controller.lock()); state(State.LOCKED); }
    @After public void cleanup() throws Exception {
        real.failClose.set(false); real.failDomainClose.set(false); real.discoveryRead = null;
        if (controller != null && EnumSet.of(State.OPEN, State.ERROR_OPEN, State.FAILED_CLOSE).contains(controller.snapshot().state())) lock();
        assertNull(threadError.get());
    }
    protected Object controllerField(String name) throws Exception {
        synchronized (controller) {
            var field = AndroidVaultController.class.getDeclaredField(name);
            field.setAccessible(true); return field.get(controller);
        }
    }
    protected void observationOwnership(boolean terminal, boolean failedClose) throws Exception {
        var armed = new AtomicBoolean();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        var decorated = new AtomicInteger();
        var stateDeliveries = new CopyOnWriteArrayList<Semaphore>();
        real.observationCallback = (failed, callback) -> {
            decorated.incrementAndGet();
            var delivered = new Semaphore(0);
            if (!failed) stateDeliveries.add(delivered);
            return () -> {
                boolean held = failed == terminal && armed.compareAndSet(true, false);
                try {
                    // FC has already passed its cancellation check before this Runnable.
                    if (held) {
                        entered.countDown();
                        if (!release.await(30, TimeUnit.SECONDS)) throw new AssertionError("callback release timeout");
                    }
                    callback.run();
                } catch (Throwable failure) { threadError.compareAndSet(null, failure); }
                finally { delivered.release(); if (held) finished.countDown(); }
            };
        };
        create();
        assertTrue(stateDeliveries.get(0).tryAcquire(10, TimeUnit.SECONDS)); await(this::idle);
        assertEquals(2, decorated.get());
        var oldSession = real.session;
        var oldCoordinator = real.coordinator;
        armed.set(true);
        Object epoch;
        Snapshot healthy;
        try {
            if (terminal) oldSession.close(); else oldSession.requestRefresh();
            assertTrue("old callback passed FC cancellation check", entered.await(10, TimeUnit.SECONDS));
            if (failedClose) {
                real.failClose.set(true);
                accept(() -> controller.lock()); state(State.FAILED_CLOSE); await(this::idle);
                assertSame(oldCoordinator, controllerField("observationOwner"));
                epoch = controllerField("presentationEpoch");
                release.countDown(); assertTrue(finished.await(10, TimeUnit.SECONDS));
                assertNull(threadError.get());
                assertEquals("entered callback still belongs to retained owner", true, controllerField("observationFailed"));
                assertTrue((Long)controllerField("presentationEpoch") > (Long)epoch);
                assertEquals(State.FAILED_CLOSE, controller.snapshot().state());
                assertFalse(controller.canAddToken()); assertFalse(controller.unlock(password()));
                real.failClose.set(false); lock();
                assertNull(controllerField("observationOwner"));
                return;
            }
            lock();
            assertNull(controllerField("observationOwner"));
            assertEquals(ForegroundVaultCoordinator.State.CLOSED, oldCoordinator.lifecycle());
            accept(() -> controller.unlock(password())); state(State.OPEN);
            assertTrue(stateDeliveries.get(1).tryAcquire(10, TimeUnit.SECONDS)); await(this::idle);
            assertNotSame(oldSession, real.session); assertNotSame(oldCoordinator, real.coordinator);
            assertSame(real.coordinator, controllerField("observationOwner"));
            assertEquals(4, decorated.get());
            synchronized (controller) {
                // Epoch retains evidence even if the worker subsequently clears dirty.
                assertEquals(false, controllerField("dirty"));
                assertEquals(false, controllerField("observationFailed"));
                epoch = controllerField("presentationEpoch");
                healthy = controller.snapshot();
                release.countDown();
                // Callback needs the monitor: await completion outside this critical section.
            }
            assertTrue("old callback returned", finished.await(10, TimeUnit.SECONDS));
            assertNull(threadError.get());
            synchronized (controller) {
                assertEquals("retired callback must not fail observation", false, controllerField("observationFailed"));
                assertEquals("retired callback must not invalidate presentation", epoch, controllerField("presentationEpoch"));
                assertEquals("retired callback must not render", healthy, controller.snapshot());
                assertEquals(false, controllerField("dirty"));
                assertEquals(false, controllerField("observationFailed"));
                assertEquals(State.OPEN, controller.snapshot().state());
                assertEquals(Error.NONE, controller.snapshot().error());
                assertTrue(controller.canAddToken());
            }
            accept(() -> controller.refresh());
            assertTrue(stateDeliveries.get(1).tryAcquire(10, TimeUnit.SECONDS)); await(this::idle);
            assertTrue("current signal still invalidates presentation",
                    (Long)controllerField("presentationEpoch") > (Long)epoch);
            assertEquals(State.OPEN, controller.snapshot().state());
            // Positive control uses the real replacement subscription, not a manual callback.
            if (terminal) {
                real.session.close(); state(State.ERROR_OPEN);
                assertEquals(true, controllerField("observationFailed"));
                assertFalse(controller.canAddToken()); assertFalse(controller.refresh());
            }
        } finally {
            release.countDown();
            if (entered.getCount() == 0) assertTrue(finished.await(10, TimeUnit.SECONDS));
        }
    }
    protected Path localRoot() throws Exception {
        var field = LocalReplicaOwner.class.getDeclaredField("root"); field.setAccessible(true); return (Path) field.get(owner);
    }
    protected org.totipo.android.sync.SyncFolderBindingTest.MemoryPort startBound() throws Exception { return startBound(new RealBackend()); }
    protected org.totipo.android.sync.SyncFolderBindingTest.MemoryPort startBound(Backend backend) throws Exception {
        var port = new org.totipo.android.sync.SyncFolderBindingTest.MemoryPort();
        port.stored = new org.totipo.android.sync.SyncFolderBinding.Stored("content://fixture/tree/a", true, false);
        port.permissions.put(port.stored.uri(), new org.totipo.android.sync.SyncFolderBinding.Grants(true, false));
        controller = new AndroidVaultController(owner, dispatcher, backend, new TotpPresentation.Time() {
            public java.time.Instant wall() { return java.time.Instant.now(); }
            public long elapsedMillis() { return System.nanoTime() / 1000000; }
        }, null, new org.totipo.android.sync.SyncFolderBinding(port));
        await(this::providerDone); return port;
    }
    protected static void block(CountDownLatch entered, CountDownLatch release) {
        entered.countDown();
        boolean interrupted = false;
        for (;;) {
            try { if (!release.await(30, TimeUnit.SECONDS)) throw new AssertionError("provider not released"); break; }
            catch (InterruptedException ignored) { interrupted = true; }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }
    protected void prepareJoin() throws Exception { accept(() -> controller.prepareJoin()); await(this::providerDone); }
    protected org.totipo.android.sync.PublicationPort outboundFixture() throws Exception { return outboundFixture(3); }
    protected org.totipo.android.sync.PublicationPort outboundFixture(int count) throws Exception {
        owner = TestReplicaOwners.create(temporary.newFolder().toPath());
        var port = new org.totipo.android.sync.PublicationPort();
        controller = new AndroidVaultController(owner, dispatcher, new RealBackend(), new TotpPresentation.Time() {
            // Lifecycle assertions must not race a real 30-second TOTP rollover.
            public java.time.Instant wall() { return java.time.Instant.ofEpochSecond(59); }
            public long elapsedMillis() { return securityElapsed; }
        }, null, new org.totipo.android.sync.SyncFolderBinding(port));
        state(State.NO_LOCAL_VAULT); await(this::providerDone);
        accept(() -> controller.create(password())); state(State.OPEN);
        port.vaultBytes = Files.readAllBytes(localRoot().resolve("vault"));
        for (int i = 0; i < count; i++) add(enrollment("public", "test " + i, org.totipo.TotpAlgorithm.SHA1, 6, 30, rfcSecret()), AddTokenOutcome.Status.ADDED);
        await(() -> controller.snapshot().view().tokens().size() == count);
        return port;
    }
    protected AddTokenRequest enrollment(String issuer, String account, org.totipo.TotpAlgorithm algorithm, int digits, long period, char[] text) {
        return new AddTokenRequest(issuer, account, algorithm, digits, period, text);
    }
    protected char[] rfcSecret() { return "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ".toCharArray(); }
    protected void add(AddTokenRequest request, AddTokenOutcome.Status expected) throws Exception {
        accept(() -> controller.addToken(request));
        await(() -> idle() && controller.snapshot().addOutcome() != null);
        assertEquals(expected, controller.snapshot().addOutcome().status());
    }

    protected ForegroundVaultCoordinator.ObservedToken lifecycleRow() {
        return controller.snapshot().view().tokens().get(0);
    }
    protected void finishChange(TokenChange change, String issuer, String account, int option) throws Exception {
        assertTrue(controller.confirmTokenChange(change, issuer, account, option));
        await(() -> idle() && controller.tokenChangeResult() != null);
    }
    protected void publishLifecycle(org.totipo.android.sync.PublicationPort port) throws Exception {
        int before = port.objects.size();
        accept(() -> controller.publishLocalChanges()); await(this::providerDone);
        assertTrue(controller.syncView().message(), controller.syncView().message().contains("Local changes published"));
        assertTrue(port.objects.size() > before);
    }
    /** Independent Java session/store authors a legitimate sibling of the captured local head. */
    protected void conflictBranch(org.totipo.android.sync.PublicationPort port, boolean deleted) throws Exception {
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
    protected void resolveLifecycle(boolean deleted, String account) throws Exception {
        var port = outboundFixture(1); await(this::providerDone); var session = real.session;
        conflictBranch(port, deleted); var row = lifecycleRow(); assertTrue(TokenChange.resolvable(row));
        assertFalse(TokenListAdapter.usable(row)); assertTrue(TokenListAdapter.rowText(row).startsWith("Token conflict"));
        assertNull(controller.beginTokenChange(row.id(), TokenChange.Kind.EDIT));
        assertFalse(controller.showCode(row.id())); assertNull(controller.snapshot().revealedCode());
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

    protected boolean securityLock() {
        if (!timeoutLock) return controller.lock();
        securityElapsed = InactivityLock.TIMEOUT_MILLIS;
        controller.checkInactivity();
        return controller.snapshot().state() == State.LOCKING || controller.snapshot().state() == State.LOCKED;
    }
    protected void timeoutPendingChange(TokenChange.Kind kind) throws Exception {
        var port = outboundFixture(1); await(this::providerDone);
        if (kind == TokenChange.Kind.RESOLVE) conflictBranch(port, false);
        await(this::providerDone);
        var change = controller.beginTokenChange(lifecycleRow().id(), kind); assertNotNull(change);
        int saves = real.saves;
        securityElapsed = InactivityLock.TIMEOUT_MILLIS; controller.checkInactivity();
        assertNull(controller.pendingTokenChange());
        assertFalse(controller.confirmTokenChange(change, "late", "late", 0)); state(State.LOCKED);
        assertEquals(saves, real.saves); assertNull(controller.snapshot().view());
    }

    protected org.totipo.android.sync.PublicationPort dailyFixture() throws Exception {
        var port = outboundFixture(1); await(this::providerDone);
        assertTrue(controller.sync()); await(this::providerDone);
        assertFalse(port.pending); automaticSync = true;
        return port;
    }
    protected org.totipo.android.sync.PublicationPort dailyConcurrentFixture() throws Exception {
        var port = outboundFixture(1); await(this::providerDone);
        Path remoteRoot = temporary.newFolder().toPath();
        Files.write(remoteRoot.resolve("vault"), port.vaultBytes); Files.createDirectory(remoteRoot.resolve("objects-v1"));
        for (var object : real.coordinator.outboundSnapshot()) Files.write(remoteRoot.resolve("objects-v1").resolve(object.id().hex()), object.representation());
        var row = lifecycleRow();
        try (var remote = ((org.totipo.OpenResult.Opened) org.totipo.Totipo.open(
                org.totipo.storage.nio.NioStoreComposition.coordinatedDelegate(remoteRoot, new org.totipo.storage.nio.NioDurability()), password())).session()) {
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (!(remote.state().observation() instanceof org.totipo.ObservationProgress.Finished) && System.nanoTime() < end) Thread.sleep(5);
            try (var update = remote.state().update(remote.state().token(row.id()).orElseThrow().alternatives().get(0))) {
                assertTrue(update.account("desktop concurrent").save() instanceof org.totipo.SaveResult.Saved);
            }
        }
        finishChange(controller.beginTokenChange(row.id(), TokenChange.Kind.EDIT), "public", "android concurrent", 0);
        await(() -> lifecycleRow().alternatives().get(0).account().equals("android concurrent")); await(this::providerDone);
        try (var files = Files.list(remoteRoot.resolve("objects-v1"))) {
            for (Path path : files.toList()) port.objects.put(path.getFileName().toString(), Files.readAllBytes(path));
        }
        return port;
    }

}
