package org.totipo.android;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

/** The Application's sole selection authority. Production startup still selects only
 * a single registry entry. Package access is for controlled lifecycle qualification;
 * there is no public multi-entry switching UI before A-MV3/4/5. */
public final class VaultSelection {
    public record Snapshot(String activationId, String localId, AndroidVaultController controller) {}
    interface Factory { AndroidVaultController create(LocalReplicaOwner owner); }
    private final LocalReplicaOwner.Admission admission = new LocalReplicaOwner.Admission();
    private final Set<Consumer<Snapshot>> observers = new LinkedHashSet<>();
    private Snapshot selected;
    private LocalReplicaOwner owner;
    public synchronized Snapshot snapshot() { return selected; }
    synchronized LocalReplicaOwner owner() { return owner; }
    synchronized Snapshot select(VaultRegistry registry, String id, Factory factory) throws IOException {
        if (selected != null || owner != null) throw new IllegalStateException("Selection still owned");
        owner = admission.select(registry, id);
        // Construction failure deliberately retains admission; never recover by constructing
        // another controller/executor against an uncertain partially initialized activation.
        var controller = factory.create(owner);
        selected = new Snapshot(controller.activationId(), id, controller);
        changed(); return selected;
    }
    public synchronized void observe(Consumer<Snapshot> observer) { observers.add(observer); observer.accept(selected); }
    public synchronized void detach(Consumer<Snapshot> observer) { observers.remove(observer); }
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
    private void changed() {
        for (var observer : new java.util.ArrayList<>(observers)) observer.accept(selected);
    }
}
