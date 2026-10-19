package org.totipo.android.sync;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import org.totipo.android.provider.ProviderSnapshot.*;
import static org.junit.Assert.*;

public final class ProviderIoRetirementTest {
    private static void gate(CountDownLatch entered, CountDownLatch release) {
        entered.countDown();
        try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
        catch (InterruptedException failure) { throw new AssertionError("Retirement interrupted provider", failure); }
    }
    @Test public void enteredProbeDrainsAndQueuedWorkNeverEntersEvenAfterShutdownReturns() throws Exception {
        var lane = new ProviderIoLane(); var port = new PublicationPort();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var drained = new CountDownLatch(1);
        port.onProbe = () -> gate(entered, release);
        var request = new SyncFolderBinding.Request(0, PublicationPort.TREE.locator());
        var callbacks = new AtomicInteger();
        lane.submit(port, request, true, result -> callbacks.incrementAndGet());
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        lane.submit(port, request, true, result -> fail("Queued callback ran"));
        lane.retire(drained::countDown); lane.retire(() -> fail("Duplicate retirement"));
        assertEquals(1, drained.getCount()); assertEquals(1, port.probes); assertEquals(0, port.scans);
        assertThrows(RejectedExecutionException.class, () -> lane.submit(port, request, false, value -> {}));
        release.countDown(); assertTrue(drained.await(10, TimeUnit.SECONDS));
        assertEquals(0, callbacks.get()); assertEquals(1, port.probes); assertEquals(0, port.scans);
    }
    @Test public void queuedCompletionHandoffMustBeRetiredBeforeDrain() throws Exception {
        var lane = new ProviderIoLane(); var port = new PublicationPort();
        var queued = new LinkedBlockingQueue<Runnable>(); var entered = new CountDownLatch(1); var drained = new CountDownLatch(1);
        lane.submit(port, new SyncFolderBinding.Request(0, PublicationPort.TREE.locator()), false,
                result -> { lane.dispatch(queued::add, () -> fail("Retired handoff entered")); entered.countDown(); });
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        lane.retire(drained::countDown);
        assertEquals(1, drained.getCount());
        queued.take().run(); assertTrue(drained.await(10, TimeUnit.SECONDS));
    }
    @Test public void enteredHandoffAndNestedWorkerHandoffAreBothAccountedFor() throws Exception {
        var lane = new ProviderIoLane(); var pool = Executors.newSingleThreadExecutor();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var drained = new CountDownLatch(1);
        var next = new LinkedBlockingQueue<Runnable>();
        lane.dispatch(pool, () -> { gate(entered, release); lane.dispatch(next::add, () -> fail("Late nested callback entered")); });
        assertTrue(entered.await(10, TimeUnit.SECONDS)); lane.retire(drained::countDown);
        assertEquals(1, drained.getCount()); release.countDown();
        next.take().run(); assertTrue(drained.await(10, TimeUnit.SECONDS)); pool.shutdown();
    }
    @Test public void rejectedAndInlineFailedHandoffsDoNotForgeOrLoseDrain() throws Exception {
        var lane = new ProviderIoLane();
        assertThrows(RejectedExecutionException.class, () -> lane.dispatch(action -> { throw new RejectedExecutionException(); }, () -> {}));
        assertThrows(IllegalStateException.class, () -> lane.dispatch(Runnable::run, () -> { throw new IllegalStateException(); }));
        var drained = new CountDownLatch(1); lane.retire(drained::countDown);
        assertTrue(drained.await(10, TimeUnit.SECONDS));
    }
    @Test public void createWriteReadbackAndPostflightMustActuallyReturnBeforeDrain() throws Exception {
        for (String phase : List.of("scan", "create", "write", "readback", "postflight")) {
            var lane = new ProviderIoLane(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            var drained = new CountDownLatch(1); var streamClosed = new AtomicBoolean();
            var template = new PublicationPort();
            var bytes = new byte[1024];
            var object = new DetachedImmutableObject(new org.totipo.RevisionId("a".repeat(64)), bytes);
            class Port extends SyncFolderBindingTest.MemoryPort implements ProviderObjectWriter.Port {
                @Override public SyncFolderBinding.Grants grants(String uri) { return new SyncFolderBinding.Grants(true, true); }
                @Override public Scan scan(String uri) { gate(entered, release); return null; }
                public Document create(Document parent, String name) {
                    if (phase.equals("create")) gate(entered, release);
                    return template.doc(name);
                }
                public Document metadata(Document created) { return created; }
                public java.io.OutputStream output(Document created) {
                    return new java.io.ByteArrayOutputStream() {
                        @Override public void write(byte[] value) throws java.io.IOException {
                            if (phase.equals("write")) gate(entered, release);
                            super.write(value);
                        }
                        @Override public void close() { streamClosed.set(true); }
                    };
                }
                public Bytes readBack(Document created) {
                    if (phase.equals("readback")) gate(entered, release);
                    return new Bytes("read", created, 1024, ByteState.PRESENT, bytes, Issue.NONE);
                }
            }
            var port = new Port();
            var request = new SyncFolderBinding.Request(0, PublicationPort.TREE.locator());
            if (phase.equals("scan")) lane.submit(port, request, true, value -> {});
            else lane.publish(port, request, template.directory, List.of(object), () -> false, value -> {});
            assertTrue(phase, entered.await(10, TimeUnit.SECONDS)); lane.retire(drained::countDown);
            assertEquals(phase, 1, drained.getCount()); release.countDown();
            assertTrue(phase, drained.await(10, TimeUnit.SECONDS));
            if (!phase.equals("scan")) assertTrue(phase, streamClosed.get());
        }
    }
}
