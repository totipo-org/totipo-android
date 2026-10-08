package org.totipo.android;

import java.nio.file.Path;

/** JVM-only construction; never packaged in either APK. */
public final class TestReplicaOwners {
    private TestReplicaOwners() {}
    public static LocalReplicaOwner create(Path base) { return new LocalReplicaOwner(base); }
}
