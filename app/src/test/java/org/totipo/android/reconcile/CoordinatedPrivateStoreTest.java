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
        public VaultCreate createVault(byte[] bytes) { return call(VaultCreate.Created::new); }
        public void close() { closes.incrementAndGet(); }
    }
    @Test public void ordinarySpiForwardsAndSessionCloseIsLogicalFinalCloseIsPhysical() {
        var delegate = new Delegate(); var store = new CoordinatedPrivateStore(delegate);
        var view = store.transferSessionView();
        view.readVault(1); view.scanObjects(); view.readObject(new ObjectName("opaque"), 1);
        view.publishObject(new ObjectName("opaque"), new byte[1]);
        view.createVault(new byte[1]);
        assertEquals(5, delegate.calls.get());
        assertThrows(IllegalStateException.class, store::close);
        view.close(); view.close(); assertEquals(0, delegate.closes.get());
        assertThrows(IllegalStateException.class, view::scanObjects);
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
    @Test public void bridgeScopePausesEveryOrdinaryOperation() throws Exception {
        for (String operation : java.util.List.of("readVault", "createVault", "scanObjects", "readObject", "publishObject")) {
            var delegate = new Delegate(); var store = new CoordinatedPrivateStore(delegate);
            var view = store.transferSessionView(); var error = new AtomicReference<Throwable>(); Thread next;
            try (var bridge = store.bridge()) {
                next = thread(error, () -> {
                    switch (operation) {
                        case "readVault" -> view.readVault(87);
                        case "createVault" -> view.createVault(new byte[87]);
                        case "scanObjects" -> view.scanObjects();
                        case "readObject" -> view.readObject(new ObjectName("opaque"), 1024);
                        case "publishObject" -> view.publishObject(new ObjectName("opaque"), new byte[1024]);
                    }
                });
                queued(store, next); assertEquals(0, delegate.calls.get());
            }
            next.join(10000); assertFalse(next.isAlive()); assertNull(error.get()); assertEquals(1, delegate.calls.get());
            view.close(); store.close();
        }
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
        view.close(); store.close(); assertEquals(1, delegate.closes.get());
    }
    @Test public void boundedDetachedSnapshotUsesOneGateAndNeverTruncates() {
        var size = new AtomicInteger(512); var byteSize = new AtomicInteger(1024);
        var delegate = new Delegate() {
            public ObjectScan scanObjects() {
                return call(() -> new ObjectScan.Complete(java.util.stream.IntStream.range(0, size.get()).mapToObj(i ->
                    new ObjectEntry(new ObjectName(String.format("%064x", i)), EntryKind.REGULAR, java.util.OptionalLong.empty())).toList()));
            }
            public BoundedRead readObject(ObjectName name, int maximum) {
                assertEquals(1024, maximum); return call(() -> new BoundedRead.Present(new byte[byteSize.get()]));
            }
        };
        var store = new CoordinatedPrivateStore(delegate); var view = store.transferSessionView();
        delegate.action = () -> assertTrue(store.exclusiveHeldByCurrentThread());
        java.util.List<org.totipo.android.sync.DetachedImmutableObject> detached;
        try (var bridge = store.bridge()) { detached = bridge.snapshotObjects(); }
        assertFalse(store.exclusiveHeldByCurrentThread()); assertEquals(512, detached.size());
        assertEquals(512 * 1024, detached.stream().mapToInt(o -> o.representation().length).sum());
        byte[] exposed = detached.get(0).representation(); exposed[0] = 1;
        assertEquals(0, detached.get(0).representation()[0]);
        size.set(513);
        assertThrows(org.totipo.android.sync.DetachedImmutableObject.CapacityExceeded.class,
            () -> { try (var bridge = store.bridge()) { bridge.snapshotObjects(); } });
        size.set(1);
        for (int bad : new int[]{0, 1023, 1025}) {
            byteSize.set(bad);
            assertThrows(IllegalStateException.class, () -> { try (var bridge = store.bridge()) { bridge.snapshotObjects(); } });
        }
        view.close(); store.close();
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
