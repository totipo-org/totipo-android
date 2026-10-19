package org.totipo.android.sync;

import java.util.*;
import java.util.function.Supplier;

/** Application-wide SAF ownership, independent of sessions and protocol identity.
 * Configuration transactions and operation admission share this monitor. Unknown
 * aliases/overlap, uncertain persistence and incomplete inventories always retain. */
public final class SyncGrantOwnership {
    public interface Metadata {
        SyncFolderBinding.Stored load();
        boolean uncertain();
        Set<String> retained();
        boolean retained(Set<String> uris);
    }
    public interface Platform {
        boolean independent(String uri);
        default boolean sameFolder(String first, String second) { return first.equals(second); }
        void release(String uri);
    }
    public interface Use extends AutoCloseable { @Override void close(); }
    private final Supplier<Map<String, Metadata>> inventory;
    private final Platform platform;
    private int admitted;
    public SyncGrantOwnership(Supplier<Map<String, Metadata>> inventory, Platform platform) {
        this.inventory = inventory; this.platform = platform;
    }
    public synchronized void checkSelection(String id, String uri) {
        var entries = inventory.get();
        if (!entries.containsKey(id)) throw new IllegalStateException("Unknown entry");
        for (var entry : entries.entrySet()) {
            if (!entry.getKey().equals(id) && entry.getValue().uncertain()) throw new IllegalStateException("Configuration needs recovery");
            var stored = entry.getValue().load();
            if (!entry.getKey().equals(id) && stored != null && platform.sameFolder(stored.uri(), uri))
                throw new IllegalStateException("Folder already bound to another local entry");
        }
    }
    /** Persist before acquiring authority; process interruption leaves a recoverable
     * cleanup record even if no binding ever became active. */
    public synchronized void remember(Metadata metadata, String uri) {
        var retained = new HashSet<>(metadata.retained()); retained.add(uri);
        if (!metadata.retained(retained)) throw new IllegalStateException("Permission ownership could not be saved");
    }
    public synchronized Use admit(String id, String uri) {
        var metadata = inventory.get().get(id);
        var stored = metadata == null ? null : metadata.load();
        if (metadata == null || metadata.uncertain() || stored == null || !stored.uri().equals(uri))
            throw new java.util.concurrent.RejectedExecutionException("Binding retired");
        admitted++;
        return new Use() {
            private boolean closed;
            public void close() {
                synchronized (SyncGrantOwnership.this) {
                    if (closed) return;
                    closed = true; admitted--; cleanup();
                }
            }
        };
    }
    /** No speculative independence from URI strings. This deliberately retains
     * grants while ANY binding/use exists, including aliases on other entries. */
    public synchronized void cleanup() {
        if (admitted != 0) return;
        try {
            var entries = inventory.get();
            for (var metadata : entries.values())
                if (metadata.uncertain() || metadata.load() != null) return;
            for (var metadata : entries.values()) for (String uri : metadata.retained()) {
                if (!platform.independent(uri)) continue;
                try {
                    platform.release(uri);
                    var remaining = new HashSet<>(metadata.retained()); remaining.remove(uri);
                    metadata.retained(remaining); // Failed cleanup bookkeeping safely repeats release later.
                } catch (RuntimeException unavailable) { /* Durable record remains visible. */ }
            }
        } catch (RuntimeException incompleteInventory) { /* Never revoke on incomplete evidence. */ }
    }
}
