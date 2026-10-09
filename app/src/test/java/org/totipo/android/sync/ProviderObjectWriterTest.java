package org.totipo.android.sync;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.totipo.RevisionId;
import static org.junit.Assert.*;

public final class ProviderObjectWriterTest {
    static List<DetachedImmutableObject> objects() {
        return List.of("a", "b", "c").stream().map(n -> new DetachedImmutableObject(new RevisionId(n.repeat(64)), new byte[1024])).toList();
    }
    static ProviderObjectWriter.Result run(PublicationPort p, AtomicBoolean cancellation) throws Exception {
        var result = new java.util.concurrent.atomic.AtomicReference<ProviderObjectWriter.Result>();
        Thread thread = new Thread(() -> result.set(ProviderObjectWriter.publish(p, PublicationPort.TREE.locator(), p.directory,
                objects(), cancellation::get)), "Totipo-provider-io");
        thread.start(); thread.join(10000); assertFalse(thread.isAlive()); return result.get();
    }
    @Test public void exactCreationWritesOnlyNewCanonicalObjectsAndRescans() throws Exception {
        var port = new PublicationPort(); var result = run(port, new AtomicBoolean());
        assertEquals(3, port.creates); assertEquals(3, port.outputs); assertEquals(1, port.scans);
        assertFalse(result.uncertain()); assertNotNull(result.postflight());
        for (var object : objects()) assertArrayEquals(object.representation(), port.objects.get(object.id().hex()));
        assertFalse(port.objects.containsKey("vault")); assertFalse(port.objects.containsKey("objects-v1"));
    }
    @Test public void failuresStopBatchAndStillRequirePostflight() throws Exception {
        for (String fault : List.of("null", "throw", "suffix", "open", "partial", "close", "different", "unavailable")) {
            var p = new PublicationPort(); p.fault = fault;
            var result = run(p, new AtomicBoolean());
            assertTrue(fault, result.uncertain()); assertEquals(fault, 1, p.creates);
            assertEquals(fault, 1, p.scans); assertNotNull(result.postflight());
            if (List.of("null", "throw", "suffix").contains(fault)) assertEquals(0, p.outputs);
        }
        var p = new PublicationPort(); p.fault = "partial"; p.failAt = 2;
        assertTrue(run(p, new AtomicBoolean()).uncertain()); assertEquals(2, p.creates);
    }
    @Test public void persistedWriteLossNeverCreatesOrOpensOutput() throws Exception {
        var p = new PublicationPort();
        p.permissions.put(PublicationPort.TREE.locator(), new SyncFolderBinding.Grants(true, false));
        run(p, new AtomicBoolean()); assertEquals(0, p.creates); assertEquals(0, p.outputs);
    }
    @Test public void cancellationAfterCreateDoesNotOpenOutputOrStartNextObject() throws Exception {
        var p = new PublicationPort(); var cancelled = new AtomicBoolean(); p.onCreate = () -> cancelled.set(true);
        var result = run(p, cancelled); assertEquals(1, p.creates); assertEquals(0, p.outputs); assertNull(result.postflight());
    }
    @Test public void cancellationInsideWriteAllowsOnlyThatWriteToFinish() throws Exception {
        var p = new PublicationPort(); var cancelled = new AtomicBoolean(); p.onWrite = () -> cancelled.set(true);
        run(p, cancelled); assertEquals(1, p.creates); assertEquals(1, p.objects.size());
    }
}
