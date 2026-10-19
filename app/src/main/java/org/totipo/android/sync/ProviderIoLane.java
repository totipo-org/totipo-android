package org.totipo.android.sync;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.totipo.android.provider.ProviderSnapshot.Scan;

/** One transport thread, bounded handoff slot, no retries/replacement for blocked IPC.
 * Controller admission permits only one logical operation until its result is consumed.
 * No live provider resources cross this boundary. Retirement never interrupts Binder. */
public final class ProviderIoLane implements AutoCloseable {
    public record Result(SyncFolderBinding.Request request, boolean importing, boolean accessible, Scan scan) {}
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), action -> {
                Thread thread = new Thread(action, "Totipo-provider-io"); thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy()) {
                @Override protected void terminated() {
                    synchronized (ProviderIoLane.this) { terminated = true; }
                    notifyDrained();
                }
            };
    private boolean retiring, terminated, notified;
    private int handoffs;
    private Runnable drained;

    private void execute(Runnable action) {
        synchronized (this) {
            if (retiring) throw new java.util.concurrent.RejectedExecutionException("Provider retired");
            executor.execute(() -> {
                synchronized (ProviderIoLane.this) { if (retiring) return; }
                action.run(); // Entered work finishes without interruption, even after retire().
            });
        }
    }
    /** Count the entire completion handoff, including time queued on main or worker.
     * Retirement discards callbacks which have not entered; entered callbacks retain
     * their original controller and are accounted for until they return. */
    public void dispatch(java.util.concurrent.Executor target, Runnable action) {
        synchronized (this) { handoffs++; }
        var finished = new java.util.concurrent.atomic.AtomicBoolean();
        Runnable acknowledge = () -> { if (finished.compareAndSet(false, true)) handoffFinished(); };
        try {
            target.execute(() -> {
                try {
                    synchronized (ProviderIoLane.this) { if (retiring) return; }
                    action.run();
                } finally { acknowledge.run(); }
            });
        } catch (RuntimeException rejected) { acknowledge.run(); throw rejected; }
    }
    private void handoffFinished() {
        synchronized (this) { handoffs--; }
        notifyDrained();
    }
    private void notifyDrained() {
        Runnable callback;
        synchronized (this) {
            if (!retiring || !terminated || handoffs != 0 || drained == null || notified) return;
            notified = true; callback = drained;
        }
        callback.run();
    }
    /** One irreversible admission closure. No timeout, cancellation future or shutdown
     * return is interpreted as completed I/O. Queued operations skip provider entry. */
    public void retire(Runnable completion) {
        synchronized (this) {
            if (drained != null) return;
            retiring = true; drained = java.util.Objects.requireNonNull(completion);
        }
        executor.shutdown();
        notifyDrained();
    }
    private synchronized boolean accepting() { return !retiring; }
    private <T> void deliver(Consumer<T> delivery, T result) {
        synchronized (this) { if (retiring) return; }
        try { delivery.accept(result); }
        catch (Throwable dispatchUnavailable) { /* Never replace a thread on failed delivery. */ }
    }
    public void submit(SyncFolderBinding.Port transport, SyncFolderBinding.Request request,
                       boolean importing, Consumer<Result> delivery) {
        execute(() -> {
            Scan scan = null;
            boolean accessible = false;
            try {
                transport.probe(request.uri());
                accessible = true;
                if (importing && accepting()) scan = transport.scan(request.uri());
            } catch (Throwable unavailable) {
                // A provider failure must not kill the sole worker and trigger replacement.
                accessible = false;
            }
            deliver(delivery, new Result(request, importing, accessible, scan));
        });
    }
    public void publish(SyncFolderBinding.Port transport, SyncFolderBinding.Request request,
                        org.totipo.android.provider.ProviderSnapshot.Document target,
                        java.util.List<DetachedImmutableObject> missing,
                        java.util.function.BooleanSupplier cancelled,
                        Consumer<ProviderObjectWriter.Result> delivery) {
        execute(() -> {
            var result = ProviderObjectWriter.publish(transport, request.uri(), target, missing, cancelled);
            deliver(delivery, result);
        });
    }
    public void initialize(SyncFolderBinding.Port transport, SyncFolderBinding.Request request,
                           byte[] exact, java.util.function.BooleanSupplier cancelled,
                           Consumer<ProviderVaultWriter.Result> delivery) {
        byte[] owned = exact.clone();
        execute(() -> {
            var result = ProviderVaultWriter.initialize(transport, request.uri(), owned, cancelled);
            deliver(delivery, result);
        });
    }
    @Override public void close() { retire(() -> {}); }
}
