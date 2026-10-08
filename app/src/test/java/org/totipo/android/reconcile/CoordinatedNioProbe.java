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
    public VaultPrepare prepareVault(byte[] bytes) {
        return call("prepareVault", () -> {
            var result = nio.prepareVault(bytes);
            if (!(result instanceof VaultPrepare.Prepared ready)) return result;
            var prepared = ready.vault();
            return new VaultPrepare.Prepared(new PreparedVault() {
                public BoundedRead readBack(int size) { return call("readBack", () -> prepared.readBack(size)); }
                public VaultInstall installCanonicalIfAbsent() { return call("install", prepared::installCanonicalIfAbsent); }
                public VaultReplace replaceCanonical() { return call("replace", prepared::replaceCanonical); }
                public void close() { call("stageClose", () -> { prepared.close(); return null; }); }
            });
        });
    }
    public void close() { call("close", () -> { nio.close(); return null; }); }
}
