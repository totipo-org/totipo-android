package org.totipo.android;

import java.util.Arrays;
import java.util.concurrent.Executor;

/** Main-thread orchestration, independent of Activity recreation. Worker return includes
 * plan/delegate closure and maintenance release. Rendering confers no continuation authority. */
final class RestoreJourney {
    interface Host {
        BackupRestoreFlow current();
        boolean busy();
        void busy(boolean value);
        boolean foregroundUnselected();
        void changed();
        void selected(BackupRestoreFlow origin, String id, boolean restored);
    }
    private final Host host;
    private final VaultRegistry registry;
    private final VaultSelection selection;
    private final Executor worker, main;
    private BackupRestoreFlow openIntent;
    private String submittedLabel;
    private long generation;
    RestoreJourney(Host host, VaultRegistry registry, VaultSelection selection, Executor worker, Executor main) {
        this.host = host; this.registry = registry; this.selection = selection; this.worker = worker; this.main = main;
    }
    private boolean admitted(BackupRestoreFlow origin) {
        return origin == host.current() && !host.busy() && host.foregroundUnselected() && origin.currentJourney();
    }
    /** Invalid names leave PASSWORD actionable. Every submitted password is consumed/wiped. */
    boolean submit(BackupRestoreFlow origin, char[] password, String name) {
        boolean accepted = false;
        try {
            if (!admitted(origin)) return false;
            String label = origin.recovery() ? null : VaultUiModel.optionalLabel(name);
            new VaultRegistry.Entry("00000000000000000000000000000000", label, VaultRegistry.State.PENDING, false);
            if (!origin.authenticating()) return false;
            submittedLabel = label; accepted = true;
            // Only this accepted task owns the credential after this method returns.
            work(origin, () -> origin.authenticate(password, registry));
            return true;
        } finally {
            // Accepted password belongs to the queued worker, all other paths wipe here.
            if (!accepted) Arrays.fill(password, '\0');
        }
    }
    void confirm(BackupRestoreFlow origin) {
        if (!admitted(origin) || !origin.needsHistoryReview() || !origin.installing()) return;
        work(origin, () -> origin.install(submittedLabel, registry, selection));
    }
    void work(BackupRestoreFlow origin, Runnable action) {
        long ticket = ++generation;
        host.busy(true); host.changed();
        worker.execute(() -> {
            try { action.run(); }
            finally { main.execute(() -> retired(origin, ticket)); }
        });
    }
    private void retired(BackupRestoreFlow origin, long ticket) {
        if (ticket != generation || host.current() != origin) return;
        if (origin.cancelled() && origin.stage() != BackupRestoreFlow.Stage.CANCELLED) {
            work(origin, origin::dispose); return;
        }
        host.busy(false);
        if (origin.stage() == BackupRestoreFlow.Stage.CONFIRM && origin.duplicate() == null
                && !origin.needsHistoryReview() && admitted(origin) && origin.installing()) {
            work(origin, () -> origin.install(submittedLabel, registry, selection)); return;
        }
        if (origin.stage() == BackupRestoreFlow.Stage.DONE && admitted(origin)) {
            finish(origin, origin.localId(), true); return;
        }
        host.changed();
    }
    void background() {
        var origin = host.current();
        if (origin != null && origin.stage() != BackupRestoreFlow.Stage.PICKING
                && (origin.active() || origin.stage() == BackupRestoreFlow.Stage.DONE)) cancel(origin);
    }
    void cancel(BackupRestoreFlow origin) {
        if (origin == null || host.current() != origin) return;
        openIntent = null; origin.cancel();
        if (!host.busy()) work(origin, origin::dispose);
        else host.changed();
    }
    void finish(BackupRestoreFlow origin, String id, boolean restored) {
        if (!admitted(origin)) return;
        // Explicit duplicate navigation is only to the authenticated established match.
        if (restored ? origin.stage() != BackupRestoreFlow.Stage.DONE || !id.equals(origin.localId())
                : origin.duplicate() == null || origin.duplicate().state() != VaultRegistry.State.ESTABLISHED
                    || !id.equals(origin.duplicate().id())) return;
        long ticket = ++generation;
        openIntent = origin; host.busy(true); host.changed();
        worker.execute(() -> {
            origin.dispose();
            main.execute(() -> {
                if (ticket != generation || host.current() != origin) return;
                host.busy(false);
                if (openIntent != origin || !admitted(origin)) { host.changed(); return; }
                openIntent = null;
                host.selected(origin, id, restored);
            });
        });
    }
}
