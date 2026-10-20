package org.totipo.android;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

/** The Application's sole selection authority. Replacement requires complete retirement. */
public final class VaultSelection {
    public record Snapshot(String activationId, String localId, AndroidVaultController controller) {}
    interface Factory { AndroidVaultController create(LocalReplicaOwner owner); }
    private final LocalReplicaOwner.Admission admission = new LocalReplicaOwner.Admission();
    private final Set<Consumer<Snapshot>> observers = new LinkedHashSet<>();
    private Snapshot selected;
    private LocalReplicaOwner owner;
    private Thread maintenanceThread;
    private String maintenanceId;
    private int maintenanceDepth;
    public synchronized Snapshot snapshot() { return selected; }
    synchronized LocalReplicaOwner owner() { return owner; }
    synchronized Snapshot select(VaultRegistry registry, String id, Factory factory) throws IOException {
        if (selected != null || owner != null || maintenanceThread != null) throw new IllegalStateException("Selection still owned");
        owner = admission.select(registry, id);
        // Construction failure deliberately retains admission; never recover by constructing
        // another controller/executor against an uncertain partially initialized activation.
        var controller = factory.create(owner);
        selected = new Snapshot(controller.activationId(), id, controller);
        changed(); return selected;
    }
    public synchronized void observe(Consumer<Snapshot> observer) { observers.add(observer); observer.accept(selected); }
    public synchronized void detach(Consumer<Snapshot> observer) { observers.remove(observer); }
    synchronized void presentationChanged() { changed(); }
    synchronized AndroidVaultController.Retirement retire() {
        if (selected == null) return AndroidVaultController.Retirement.NOT_REQUESTED;
        Snapshot origin = selected;
        var result = origin.controller().retire(completion -> {
            synchronized (VaultSelection.this) {
                if (selected != origin) return;
                if (completion == AndroidVaultController.Retirement.SUCCESS
                        && origin.controller().retirement() == AndroidVaultController.Retirement.SUCCESS) {
                    selected = null; owner = null;
                }
                changed();
            }
        });
        changed();
        return result;
    }
    synchronized void requireRetiredEntry(String id) {
        VaultRegistry.validateId(id);
        // A partially constructed or failed-close owner still prevents cleanup.
        if ((owner != null && owner.localId().equals(id)) || (selected != null && selected.localId().equals(id)))
            throw new IllegalStateException("Entry must be fully retired before credential cleanup");
    }
    /** Reserve admission without holding this monitor across filesystem or Keystore I/O.
     * Nested scoped cleanup on the same worker/entry is allowed; another actor is refused. */
    synchronized void beginMaintenance(String id) {
        requireRetiredEntry(id);
        if (maintenanceThread != null && (maintenanceThread != Thread.currentThread() || !id.equals(maintenanceId)))
            throw new IllegalStateException("Entry cleanup already owned");
        maintenanceThread = Thread.currentThread(); maintenanceId = id; maintenanceDepth++;
    }
    synchronized void endMaintenance(String id) {
        if (maintenanceThread != Thread.currentThread() || !id.equals(maintenanceId))
            throw new IllegalStateException("Entry cleanup ownership mismatch");
        if (--maintenanceDepth == 0) { maintenanceThread = null; maintenanceId = null; }
    }
    private void changed() {
        for (var observer : new java.util.ArrayList<>(observers)) observer.accept(selected);
    }
}
