package org.totipo.android;

import java.nio.file.Path;

/** JVM-only construction; never packaged in either APK. */
public final class TestReplicaOwners {
    private TestReplicaOwners() {}
    public static LocalReplicaOwner create(Path base) { try {
            java.nio.file.Files.createDirectories(base);
            var registry = new VaultRegistry(base.toRealPath());
            if (registry.entries().isEmpty()) registry.reserve(null);
            if (registry.entries().size() != 1) throw new java.io.IOException("Ambiguous fixture");
            return new LocalReplicaOwner.Admission().select(registry, registry.entries().get(0).id());
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); } }
}
