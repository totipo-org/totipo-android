package org.totipo.android;

/** Small resumable metadata transaction. Cleanup is scoped, idempotent, and never owns a session.
 * Deferred SAF grants keep their existing durable cleanup records in the registry inventory. */
final class VaultForget {
    interface Cleanup {
        void biometrics() throws Exception;
        void syncBinding() throws Exception;
    }
    static void run(VaultRegistry registry, VaultSelection selection, String id, Cleanup cleanup) throws Exception {
        selection.beginMaintenance(id);
        try {
            if (registry.entry(id).state() == VaultRegistry.State.DETACHED) return;
            registry.beginForget(id);
            cleanup.biometrics();
            cleanup.syncBinding();
            registry.finishForget(id);
        } finally { selection.endMaintenance(id); }
    }
    private VaultForget() {}
}
