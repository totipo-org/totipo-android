package org.totipo.android.reconcile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.totipo.spi.*;
import org.totipo.storage.nio.NioTotipoStore;

/** One private storage domain under the owner's lifetime lease. Worker threads only.
 * A fair mutex serializes EVERY delegate call (NIO is not concurrent), including staged
 * vault handles. The Java-owned facade and Android-owned bridge have distinct lifetimes.
 * Never call a VaultSession method while holding a Bridge. No task queue is installed.
 */
final class CoordinatedPrivateStore implements AutoCloseable {
    private final TotipoStore delegate;
    private final ReentrantLock gate = new ReentrantLock(true);
    private final Set<Stage> stages = new HashSet<>();
    private final SessionView session = new SessionView();
    private boolean transferred, sessionClosed, closed, unsafe;

    static CoordinatedPrivateStore open(Path root) throws IOException {
        return new CoordinatedPrivateStore(NioTotipoStore.openPrivate(root));
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
    // Core cleanup is best effort; retry staged cleanup before closing our backing domain.
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
        public VaultPrepare prepareVault(byte[] bytes) {
            return ordinary(() -> {
                VaultPrepare result = delegate.prepareVault(bytes);
                if (!(result instanceof VaultPrepare.Prepared ready)) return result;
                Stage stage = new Stage(ready.vault()); stages.add(stage);
                return new VaultPrepare.Prepared(stage);
            });
        }
        public void close() {
            gate.lock();
            try {
                if (sessionClosed) return;
                // A failed stage cleanup retains logical ownership for retry.
                for (Stage stage : Set.copyOf(stages)) stage.close();
                sessionClosed = true;
            } finally { gate.unlock(); }
        }
    }
    private final class Stage implements PreparedVault {
        private final PreparedVault prepared;
        private boolean ended;
        Stage(PreparedVault prepared) { this.prepared = prepared; }
        private <T> T call(Supplier<T> action) {
            return ordinary(() -> {
                if (ended) throw new IllegalStateException("Prepared vault closed");
                return action.get();
            });
        }
        public BoundedRead readBack(int size) { return call(() -> prepared.readBack(size)); }
        public VaultInstall installCanonicalIfAbsent() { return call(prepared::installCanonicalIfAbsent); }
        public VaultReplace replaceCanonical() { return call(prepared::replaceCanonical); }
        public void close() {
            gate.lock();
            try {
                if (ended) return;
                prepared.close(); ended = true; stages.remove(this);
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
