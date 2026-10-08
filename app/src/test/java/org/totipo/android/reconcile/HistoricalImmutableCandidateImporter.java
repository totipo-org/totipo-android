package org.totipo.android.reconcile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import org.totipo.OpenResult;
import org.totipo.RevisionId;
import org.totipo.Totipo;
import org.totipo.VaultSession;
import org.totipo.android.LocalReplicaOwner;
import org.totipo.android.provider.ImmutableCandidateClassifier;
import org.totipo.android.provider.ImmutableCandidateClassifier.*;
import org.totipo.android.provider.ProviderSnapshot.Scan;
import org.totipo.spi.ObjectName;
import org.totipo.spi.ObjectWrite;
import org.totipo.spi.TotipoStore;
import org.totipo.storage.nio.NioTotipoStore;
import static org.totipo.android.reconcile.ImmutableCandidateImporter.*;

/**
 * Worker-thread, foreground building block only; not connected to product UI.
 * Caller holds one exclusive lease with NO existing session/store handle, and must
 * neither close that lease nor use its root concurrently until this call returns.
 * Snapshot I/O precedes this operation. The normal core owns the validating store;
 * synchronous session closure precedes opening the publication store on the SAME lease.
 * No lease is acquired/released here, and no credential is retained or automatically reused.
 *
 * Valid is publicly constructible descriptive TCB input, not a security capability.
 * Only this reviewed entry point supplies production groups to the internal publication
 * primitive: actual session validation, group analysis, defensive selection, session close.
 * No crypto/parser is implemented here. Only bounded 1024-byte ciphertext is retained;
 * no bytes are logged, no plaintext/root key/password is retained, and provider locators
 * never become protocol authority. No provider writes, VAULT changes or absence actions.
 */
final class HistoricalImmutableCandidateImporter {
    private HistoricalImmutableCandidateImporter() {}

    /** Evidence/coverage are preserved verbatim. Storage failure is never provider Invalid.
     * ExistingDifferent is an unvalidated local obstruction, not an authenticated contradiction. */
    public record Result(Status status, ImmutableCandidateClassifier.Result evidence,
                         Group group, ObjectWrite publication, OpenResult openFailure) {}

    /** Opens/authenticates normally; the supplied password remains caller-owned. */
    public static Result importOne(LocalReplicaOwner.Lease lease, Scan scan, RevisionId selectedId,
                                   char[] password) throws IOException {
        Objects.requireNonNull(lease); Objects.requireNonNull(scan); Objects.requireNonNull(selectedId);
        OpenResult opened = Totipo.open(NioTotipoStore.openPrivate(lease.root()), password);
        if (!(opened instanceof OpenResult.Opened success)) {
            return new Result(Status.AUTHENTICATION_UNAVAILABLE, null, null, null, opened);
        }
        try (var transition = new Transition(lease, success.session())) {
            var evidence = transition.classify(scan);
            Group group = evidence.groups().stream().filter(g -> selectedId.equals(g.objectId()))
                    .findFirst().orElse(null);
            Selection selection = select(group);
            transition.closeValidation();
            if (selection.status != null) return new Result(selection.status, evidence, group, null, null);
            ObjectWrite write = transition.publish(selection);
            return new Result(map(write), evidence, group, write, null);
        }
    }

    // Internal lifecycle gate. Production constructs it only from the session opened above.
    // Thread-confined like the enclosing operation; cannot publish before close completes.
    static final class Transition implements AutoCloseable {
        private final LocalReplicaOwner.Lease lease;
        private final VaultSession session;
        private boolean closed;
        Transition(LocalReplicaOwner.Lease lease, VaultSession session) {
            this.lease = Objects.requireNonNull(lease); this.session = Objects.requireNonNull(session);
        }
        ImmutableCandidateClassifier.Result classify(Scan scan) {
            if (closed) throw new IllegalStateException("Validation phase closed");
            lease.root();
            return ImmutableCandidateClassifier.classify(scan, session);
        }
        void closeValidation() {
            if (!closed) {
                session.close(); // Core closure waits for owned work and closes its store.
                closed = true;   // Never grant publication if close throws.
            }
        }
        ObjectWrite publish(Selection selected) throws IOException {
            if (!closed) throw new IllegalStateException("Validation session must close before publication");
            if (selected.status != null) throw new IllegalArgumentException("No eligible representation");
            try (var store = NioTotipoStore.openPrivate(lease.root())) {
                return publishExact(store, selected);
            }
        }
        @Override public void close() { closeValidation(); }
    }


}
