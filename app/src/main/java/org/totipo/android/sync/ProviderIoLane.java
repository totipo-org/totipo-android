package org.totipo.android.sync;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.totipo.android.provider.ProviderSnapshot.Scan;

/** One transport thread, bounded handoff slot, no retries/replacement for blocked IPC.
 * Controller admission permits only one logical operation until its result is consumed.
 * No live provider resources cross this boundary. Shutdown never awaits Binder. */
public final class ProviderIoLane implements AutoCloseable {
    public record Result(SyncFolderBinding.Request request, boolean importing, boolean accessible, Scan scan) {}
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), action -> {
                Thread thread = new Thread(action, "Totipo-provider-io"); thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    public void submit(SyncFolderBinding.Port transport, SyncFolderBinding.Request request,
                       boolean importing, Consumer<Result> delivery) {
        executor.execute(() -> {
            Scan scan = null;
            boolean accessible = false;
            try {
                transport.probe(request.uri());
                accessible = true;
                if (importing) scan = transport.scan(request.uri());
            } catch (Throwable unavailable) {
                // A provider failure must not kill the sole worker and trigger replacement.
                accessible = false;
            }
            try { delivery.accept(new Result(request, importing, accessible, scan)); }
            catch (Throwable dispatchUnavailable) {
                // Do not replace the provider thread if application dispatch has shut down.
            }
        });
    }
    public void publish(SyncFolderBinding.Port transport, SyncFolderBinding.Request request,
                        org.totipo.android.provider.ProviderSnapshot.Document target,
                        java.util.List<DetachedImmutableObject> missing,
                        java.util.function.BooleanSupplier cancelled,
                        Consumer<ProviderObjectWriter.Result> delivery) {
        executor.execute(() -> {
            var result = ProviderObjectWriter.publish(transport, request.uri(), target, missing, cancelled);
            try { delivery.accept(result); } catch (Throwable dispatchUnavailable) { }
        });
    }
    public void initialize(SyncFolderBinding.Port transport, SyncFolderBinding.Request request,
                           byte[] exact, java.util.function.BooleanSupplier cancelled,
                           Consumer<ProviderVaultWriter.Result> delivery) {
        byte[] owned = exact.clone();
        executor.execute(() -> {
            var result = ProviderVaultWriter.initialize(transport, request.uri(), owned, cancelled);
            try { delivery.accept(result); } catch (Throwable dispatchUnavailable) { }
        });
    }
    @Override public void close() { executor.shutdown(); }
}
