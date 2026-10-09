package org.totipo.android.reconcile;

import java.util.Arrays;
import java.util.List;
import org.totipo.OpenResult;
import org.totipo.Totipo;
import org.totipo.spi.*;

/** A transient public-SPI store: no I/O, mutation or persistent ownership domain.
 * Totipo owns it on every outcome; an opened session is immediately closed. */
public final class DetachedVaultAuthentication {
    private DetachedVaultAuthentication() {}
    public static boolean authenticate(byte[] exact, char[] credential) {
        Totipo.vaultId(exact); // Structural failure precedes credential handling.
        var result = Totipo.open(new CandidateStore(exact), credential);
        if (!(result instanceof OpenResult.Opened opened)) return false;
        try (var session = opened.session()) { return true; }
    }
    static final class CandidateStore implements TotipoStore {
        private final byte[] vault;
        private boolean closed;
        CandidateStore(byte[] exact) { vault = exact.clone(); }
        private synchronized void active() { if (closed) throw new IllegalStateException("Candidate closed"); }
        public synchronized BoundedRead readVault(int size) {
            active();
            return size == vault.length ? new BoundedRead.Present(vault)
                    : size < vault.length ? new BoundedRead.Oversized() : new BoundedRead.Undersized(vault.length);
        }
        public synchronized ObjectScan scanObjects() { active(); return new ObjectScan.Complete(List.of()); }
        public synchronized BoundedRead readObject(ObjectName name, int size) { active(); return new BoundedRead.Absent(); }
        public ObjectWrite publishObject(ObjectName name, byte[] bytes) { throw new UnsupportedOperationException("Read only"); }
        public VaultCreate createVault(byte[] bytes) { throw new UnsupportedOperationException("Read only"); }
        public synchronized void close() { if (!closed) { closed = true; Arrays.fill(vault, (byte) 0); } }
    }
}
