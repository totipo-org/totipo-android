package org.totipo.android.reconcile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.totipo.spi.*;

/** One private storage domain under the owner's lifetime lease. Worker threads only.
 * This domain enforces NioStoreComposition coordinatedDelegate exclusivity across the
 * whole root: one owner, one process, one persistent delegate and one fair gate for EVERY
 * call and bridge batch. No second delegate or independent writer is permitted.
 * The Java-owned facade and Android-owned bridge have distinct lifetimes.
 * Never call a VaultSession method while holding a Bridge. No task queue is installed.
 */
final class CoordinatedPrivateStore implements AutoCloseable {
    private final TotipoStore delegate;
    private final ReentrantLock gate = new ReentrantLock(true);
    private final SessionView session = new SessionView();
    private boolean transferred, sessionClosed, closed, unsafe;

    static CoordinatedPrivateStore open(Path root) throws IOException {
        return new CoordinatedPrivateStore(org.totipo.storage.nio.NioStoreComposition.coordinatedDelegate(root, new org.totipo.storage.nio.NioDurability()));
    }
    // Test instrumentation supplies an opaque SPI delegate; never a public injection API.
    CoordinatedPrivateStore(TotipoStore delegate) { this.delegate = java.util.Objects.requireNonNull(delegate); }
    TotipoStore transferSessionView() {
        gate.lock();
        try {
            active();
            if (transferred) throw new IllegalStateException("Session view already transferred");
            transferred = true;
            return session;
        } finally { gate.unlock(); }
    }
    private void active() { if (closed) throw new IllegalStateException("Storage domain closed"); }
    private void usable() { active(); if (unsafe) throw new IllegalStateException("Storage domain requires recovery"); }
    private <T> T ordinary(Supplier<T> action) {
        gate.lock();
        try {
            usable();
            if (sessionClosed) throw new IllegalStateException("Session store view closed");
            return action.get();
        } finally { gate.unlock(); }
    }
    // v1 VAULT is exactly 87 bytes. Only bounded SPI presence/shape is observed here;
    // authentication and format validation remain in released Java, never an Android parser.
    BoundedRead observeVault() { return ordinary(() -> delegate.readVault(87)); }
    // Only after Java close/open/create has returned and relinquished its facade.
    void finishSessionClosure() { session.close(); }
    Bridge bridge() {
        if (gate.isHeldByCurrentThread()) throw new IllegalStateException("Nested bridge access");
        gate.lock();
        try { usable(); return new Bridge(); }
        catch (RuntimeException | Error failure) { gate.unlock(); throw failure; }
    }
    final class Bridge implements AutoCloseable {
        private boolean released;
        private Bridge() {}
        private void held() {
            if (released || !gate.isHeldByCurrentThread()) throw new IllegalStateException("Bridge scope not held");
        }
        java.util.List<org.totipo.android.sync.DetachedImmutableObject> snapshotObjects() {
            held(); usable();
            var scan = delegate.scanObjects();
            if (!(scan instanceof ObjectScan.Complete)) throw new IllegalStateException("Local publication source invalid");
            var result = new java.util.ArrayList<org.totipo.android.sync.DetachedImmutableObject>();
            int total = 0;
            for (var entry : scan.entries()) {
                String name = entry.name().value();
                if (!name.matches("[0-9a-f]{64}")) continue;
                if (result.size() == org.totipo.android.sync.DetachedImmutableObject.MAX_OBJECTS)
                    throw new org.totipo.android.sync.DetachedImmutableObject.CapacityExceeded();
                var read = delegate.readObject(entry.name(), org.totipo.android.sync.DetachedImmutableObject.REPRESENTATION_BYTES);
                if (!(read instanceof BoundedRead.Present present)) throw new IllegalStateException("Local publication source invalid");
                byte[] bytes = present.bytes();
                if (bytes.length != org.totipo.android.sync.DetachedImmutableObject.REPRESENTATION_BYTES)
                    throw new IllegalStateException("Local publication source invalid");
                if (total > org.totipo.android.sync.DetachedImmutableObject.MAX_BATCH_BYTES - bytes.length)
                    throw new org.totipo.android.sync.DetachedImmutableObject.CapacityExceeded();
                total += bytes.length;
                result.add(new org.totipo.android.sync.DetachedImmutableObject(new org.totipo.RevisionId(name), bytes));
            }
            return java.util.List.copyOf(result);
        }
        void markUnsafe() { held(); unsafe = true; }
        ObjectWrite publish(ImmutableCandidateImporter.Selection selected) {
            held(); usable();
            try {
                ObjectWrite result = ImmutableCandidateImporter.publishExact(delegate, selected);
                if (result instanceof ObjectWrite.Uncertain || result instanceof ObjectWrite.Failed) unsafe = true;
                return result;
            } catch (RuntimeException | Error failure) { unsafe = true; throw failure; }
        }
        @Override public void close() {
            if (released) return;
            held(); released = true; gate.unlock();
        }
    }
    boolean exclusiveHeldByCurrentThread() { return gate.isHeldByCurrentThread(); }
    // Deterministic queue admission evidence for tests, not a timing-based overlap assertion.
    boolean queued(Thread thread) { return gate.hasQueuedThread(thread); }
    private final class SessionView implements TotipoStore {
        public BoundedRead readVault(int size) { return ordinary(() -> delegate.readVault(size)); }
        public ObjectScan scanObjects() { return ordinary(delegate::scanObjects); }
        public BoundedRead readObject(ObjectName name, int size) { return ordinary(() -> delegate.readObject(name, size)); }
        public ObjectWrite publishObject(ObjectName name, byte[] bytes) { return ordinary(() -> delegate.publishObject(name, bytes)); }
        public VaultCreate createVault(byte[] bytes) {
            return ordinary(() -> delegate.createVault(bytes));
        }
        public void close() {
            gate.lock();
            try {
                if (sessionClosed) return;
                sessionClosed = true;
            } finally { gate.unlock(); }
        }
    }
    @Override public void close() {
        gate.lock();
        try {
            if (closed) return;
            if (transferred && !sessionClosed) throw new IllegalStateException("Java still owns session view");
            delegate.close(); closed = true;
        } finally { gate.unlock(); }
    }
}
