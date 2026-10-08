package org.totipo.android;

import android.content.Context;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Process-wide ownership of noBackupFilesDir/totipo-vault, the canonical local replica.
 * Production code obtains this singleton using application context. The app must remain
 * single-process for root access: no independent process, provider, or sync software may
 * mutate this directory. A future multi-process design needs a different ownership gate.
 *
 * The foreground coordinator holds one exclusive lease for the lifetime of its coordinated
 * storage domain, session and bridge, including asynchronous observation and closure. That
 * domain serializes every SPI call and bridge batch through one fair gate, using one
 * NioTotipoStore.openPrivate delegate with default durability. Close the session view and
 * domain before releasing the lease; never retain the Path or a handle beyond it. Per-session
 * Totipo serialization alone is insufficient. This owner holds no credentials or session.
 */
public final class LocalReplicaOwner {
    private static LocalReplicaOwner instance;
    private final Path root;
    private Lease active;

    public static synchronized LocalReplicaOwner from(Context context) {
        if (instance == null) {
            Context application = Objects.requireNonNull(context.getApplicationContext());
            instance = new LocalReplicaOwner(application.getNoBackupFilesDir().toPath());
        }
        return instance;
    }

    // Package-private construction separates Android path lookup from JVM ownership tests.
    LocalReplicaOwner(Path noBackupDirectory) {
        root = Objects.requireNonNull(noBackupDirectory).resolve("totipo-vault");
    }

    /** Acquire on a worker thread. Busy callers must retry later, never bypass the owner. */
    public synchronized Lease acquire() throws IOException {
        if (active != null) throw new IllegalStateException("Local replica already owned");
        Files.createDirectories(root);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Local replica root is not a private directory");
        }
        active = new Lease();
        return active;
    }

    /** Exclusive root access; release only after all owned work and handles have closed. */
    public final class Lease implements AutoCloseable {
        private Lease() {}

        public Path root() {
            synchronized (LocalReplicaOwner.this) {
                if (active != this) throw new IllegalStateException("Local replica lease closed");
                return root;
            }
        }

        @Override public void close() {
            synchronized (LocalReplicaOwner.this) {
                if (active == this) active = null;
            }
        }
    }
}
