package org.totipo.android;

import android.content.Context;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Immutable selection-bound owner. A single Application Admission is held for the
 * selected controller's entire lifetime, including locked periods and provider work.
 * Selection release requires complete controller/provider retirement.
 * A lease additionally excludes overlapping discovery/store/session access. Close the
 * Java session, facade and coordinated domain before releasing it. Single process only.
 */
public final class LocalReplicaOwner {
    public static final class Admission {
        private LocalReplicaOwner selected;
        private Lease active;
        public synchronized LocalReplicaOwner select(VaultRegistry registry, String id) throws IOException {
            Objects.requireNonNull(registry);
            if (selected != null) {
                if (selected.registry == registry && selected.id.equals(id)) return selected;
                throw new IllegalStateException("A controller is already selected; switching requires full retirement");
            }
            registry.select(id);
            selected = new LocalReplicaOwner(registry, id, this);
            return selected;
        }
    }
    private boolean sealed;
    void seal() { synchronized (admission) { sealed = true; } }
    private AndroidVaultController controller;
    private final String activation = java.util.UUID.randomUUID().toString();
    String activationId() { return activation; }
    void bindController(AndroidVaultController value) {
        synchronized (admission) {
            if (admission.selected != this || controller != null) throw new IllegalStateException("Controller ownership already bound");
            controller = value;
        }
    }
    boolean releaseRetired(AndroidVaultController value) {
        synchronized (admission) {
            if (controller != value || !value.retirementReady()
                    || admission.selected != this || admission.active != null)
                return false;
            admission.selected = null;
            return true;
        }
    }
    private final VaultRegistry registry;
    private final String id;
    private final Path root;
    private final Admission admission;

    public static LocalReplicaOwner from(Context context) {
        var application = (TotipoApplication) Objects.requireNonNull(context.getApplicationContext());
        return Objects.requireNonNull(application.replicaOwner(), "No selected local vault");
    }
    private LocalReplicaOwner(VaultRegistry registry, String id, Admission admission) {
        this.registry = registry; this.id = id; this.admission = admission; root = registry.root(id);
    }
    public String localId() { return id; }
    public Lease acquire() throws IOException { return acquire(false); }
    public Lease acquireForCreation() throws IOException { return acquire(true); }
    private Lease acquire(boolean creation) throws IOException {
        synchronized (admission) {
            if (sealed || admission.selected != this || admission.active != null)
                throw new IllegalStateException("Local canonical store already owned");
            if (!registry.validateRoot(id).equals(root)) throw new IOException("Selection root changed");
            if (creation) registry.beginCreation(id);
            admission.active = new Lease();
            return admission.active;
        }
    }
    public final class Lease implements AutoCloseable {
        private Lease() {}
        private void held() {
            if (admission.active != this) throw new IllegalStateException("Local canonical store lease closed");
        }
        public Path root() { synchronized (admission) { held(); return root; } }
        public boolean permitsAbsentVault() throws IOException {
            synchronized (admission) {
                held(); var entry = registry.entry(id);
                if (entry.state() != VaultRegistry.State.PENDING || entry.creationAttempted()) return false;
                try (var children = java.nio.file.Files.list(registry.validateRoot(id))) {
                    return children.findAny().isEmpty();
                }
            }
        }
        /** Only after authenticated Java open/create; no protocol identity is cached here. */
        public void authenticated() throws IOException {
            synchronized (admission) { held(); registry.established(id); }
        }
        @Override public void close() {
            synchronized (admission) { if (admission.active == this) admission.active = null; }
        }
    }
}
