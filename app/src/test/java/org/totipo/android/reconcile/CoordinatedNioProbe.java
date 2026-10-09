package org.totipo.android.reconcile;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.totipo.spi.*;
import static org.junit.Assert.*;

/** Real NIO delegate instrumentation, entirely test-only. */
final class CoordinatedNioProbe implements TotipoStore {
    final TotipoStore nio;
    final AtomicInteger active = new AtomicInteger();
    volatile java.util.function.Consumer<String> hook = name -> {};
    CoordinatedNioProbe(TotipoStore nio) { this.nio = nio; }
    private <T> T call(String name, Supplier<T> action) {
        assertEquals("NIO calls cannot overlap", 1, active.incrementAndGet());
        try { hook.accept(name); return action.get(); }
        finally { active.decrementAndGet(); }
    }
    public BoundedRead readVault(int size) { return call("readVault", () -> nio.readVault(size)); }
    public ObjectScan scanObjects() { return call("scanObjects", nio::scanObjects); }
    public BoundedRead readObject(ObjectName name, int size) { return call("readObject", () -> nio.readObject(name, size)); }
    public ObjectWrite publishObject(ObjectName name, byte[] bytes) { return call("publishObject", () -> nio.publishObject(name, bytes)); }
    public VaultCreate createVault(byte[] bytes) { return call("createVault", () -> nio.createVault(bytes)); }
    public void close() { call("close", () -> { nio.close(); return null; }); }
}
