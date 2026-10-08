package org.totipo.android.reconcile;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import org.totipo.spi.*;
import static org.junit.Assert.*;

public final class CoordinatedPrivateStoreTest {
    static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(10, TimeUnit.SECONDS)); }
        catch (InterruptedException failure) { throw new AssertionError(failure); }
    }
    static void queued(CoordinatedPrivateStore store, Thread thread) {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!store.queued(thread) && System.nanoTime() < end) Thread.yield();
        assertTrue("Contender admitted to gate wait queue", store.queued(thread));
    }
    static class Delegate implements TotipoStore {
        final AtomicInteger active = new AtomicInteger(), calls = new AtomicInteger(), closes = new AtomicInteger();
        volatile Runnable action = () -> {};
        <T> T call(java.util.function.Supplier<T> supplier) {
            assertEquals("No delegate overlap", 1, active.incrementAndGet());
            try { calls.incrementAndGet(); action.run(); return supplier.get(); }
            finally { active.decrementAndGet(); }
        }
        public BoundedRead readVault(int size) { return call(BoundedRead.Absent::new); }
        public ObjectScan scanObjects() { return call(() -> new ObjectScan.Complete(java.util.List.of())); }
        public BoundedRead readObject(ObjectName name, int size) { return call(BoundedRead.Absent::new); }
        public ObjectWrite publishObject(ObjectName name, byte[] bytes) { return call(ObjectWrite.Written::new); }
        public VaultPrepare prepareVault(byte[] bytes) { return call(() -> new VaultPrepare.Prepared(new PreparedVault() {
            public BoundedRead readBack(int size) { return call(BoundedRead.Absent::new); }
            public VaultInstall installCanonicalIfAbsent() { return call(VaultInstall.Installed::new); }
            public VaultReplace replaceCanonical() { return call(VaultReplace.Replaced::new); }
            public void close() { call(() -> null); }
        })); }
        public void close() { closes.incrementAndGet(); }
    }
    @Test public void ordinarySpiForwardsAndSessionCloseIsLogicalFinalCloseIsPhysical() {
        var delegate = new Delegate(); var store = new CoordinatedPrivateStore(delegate);
        var view = store.transferSessionView();
        view.readVault(1); view.scanObjects(); view.readObject(new ObjectName("opaque"), 1);
        view.publishObject(new ObjectName("opaque"), new byte[1]);
        var stage = ((VaultPrepare.Prepared)view.prepareVault(new byte[1])).vault();
        stage.readBack(1); stage.replaceCanonical(); stage.close(); stage.close();
        assertEquals(8, delegate.calls.get());
        assertThrows(IllegalStateException.class, store::close);
        view.close(); view.close(); assertEquals(0, delegate.closes.get());
        assertThrows(IllegalStateException.class, view::scanObjects);
        assertThrows(IllegalStateException.class, () -> stage.readBack(1));
        // Bridge authority remains independently owned after logical facade close.
        try (var bridge = store.bridge()) { assertTrue(store.exclusiveHeldByCurrentThread()); }
        store.close(); store.close(); assertEquals(1, delegate.closes.get());
        assertThrows(IllegalStateException.class, store::bridge);
        assertThrows(IllegalStateException.class, store::transferSessionView);
    }
    @Test public void exclusiveWaitsForRunningSpiAndFairGatePausesNewSpi() throws Exception {
        var delegate = new Delegate(); var store = new CoordinatedPrivateStore(delegate); var view = store.transferSessionView();
        var ordinaryEntered = new CountDownLatch(1); var ordinaryExit = new CountDownLatch(1);
        var bridgeEntered = new CountDownLatch(1); var bridgeExit = new CountDownLatch(1);
        var newSpiEntered = new CountDownLatch(1); var error = new AtomicReference<Throwable>();
        delegate.action = () -> { ordinaryEntered.countDown(); await(ordinaryExit); };
        Thread ordinary = thread(error, view::scanObjects);
        await(ordinaryEntered);
        Thread bridge = thread(error, () -> {
            try (var scope = store.bridge()) {
                assertEquals(0, delegate.active.get()); bridgeEntered.countDown(); await(bridgeExit);
            }
        });
        queued(store, bridge); assertEquals(1, bridgeEntered.getCount());
        Thread next = thread(error, () -> { view.readVault(1); newSpiEntered.countDown(); });
        queued(store, next);
        ordinaryExit.countDown(); await(bridgeEntered);
        assertEquals(1, newSpiEntered.getCount()); assertEquals(1, delegate.calls.get());
        bridgeExit.countDown(); ordinary.join(10000); bridge.join(10000); next.join(10000);
        assertEquals(0, newSpiEntered.getCount()); assertNull(error.get());
        view.close(); store.close();
    }
    @Test public void bridgeScopePausesPreparedVaultOperationsToo() throws Exception {
        var delegate = new Delegate(); var store = new CoordinatedPrivateStore(delegate); var view = store.transferSessionView();
        var prepared = ((VaultPrepare.Prepared)view.prepareVault(new byte[1])).vault();
        var error = new AtomicReference<Throwable>(); Thread next;
        try (var bridge = store.bridge()) {
            next = thread(error, prepared::replaceCanonical); queued(store, next);
            assertEquals(1, delegate.calls.get());
        }
        next.join(10000); assertNull(error.get()); assertEquals(2, delegate.calls.get());
        view.close(); assertEquals(3, delegate.calls.get()); store.close();
    }
    @Test public void exceptionsReleaseGateAndFailedCloseRetainsOwnershipForRetry() {
        var delegate = new Delegate(); var store = new CoordinatedPrivateStore(delegate); var view = store.transferSessionView();
        delegate.action = () -> { throw new IllegalArgumentException("read fault"); };
        assertThrows(IllegalArgumentException.class, view::scanObjects);
        delegate.action = () -> {};
        view.scanObjects();
        assertThrows(IllegalArgumentException.class, () -> {
            try (var bridge = store.bridge()) { throw new IllegalArgumentException("bridge fault before publication"); }
        });
        view.scanObjects();
        var stage = ((VaultPrepare.Prepared)view.prepareVault(new byte[1])).vault();
        delegate.action = () -> { throw new IllegalArgumentException("stage cleanup fault"); };
        assertThrows(IllegalArgumentException.class, view::close);
        assertThrows(IllegalStateException.class, store::close);
        delegate.action = () -> {}; view.close(); store.close(); assertEquals(1, delegate.closes.get());
        stage.close();
    }
    static Thread thread(AtomicReference<Throwable> failure, Runnable action) {
        Thread thread = new Thread(() -> { try { action.run(); } catch (Throwable fault) { failure.set(fault); } });
        thread.setDaemon(true); thread.start(); return thread;
    }
    @Test public void finalDelegateCloseFailureCanBeRetriedWithoutUseAfterClose() {
        var fail = new AtomicBoolean(true);
        var delegate = new Delegate() {
            public void close() { if (fail.get()) throw new IllegalStateException("close fault"); super.close(); }
        };
        var store = new CoordinatedPrivateStore(delegate); store.transferSessionView().close();
        assertThrows(IllegalStateException.class, store::close);
        fail.set(false); store.close(); store.close(); assertEquals(1, delegate.closes.get());
    }
}
