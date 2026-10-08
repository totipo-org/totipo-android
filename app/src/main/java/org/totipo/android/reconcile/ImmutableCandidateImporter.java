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
public final class ImmutableCandidateImporter {
    private ImmutableCandidateImporter() {}

    public enum Status { IMPORTED, ALREADY_PRESENT, BLOCKED_EXISTING_DIFFERENT,
        IMPORT_UNCERTAIN, STORAGE_FAILED, NOTHING_TO_IMPORT, CONTRADICTION,
        DEFERRED_VALIDATION, AUTHENTICATION_UNAVAILABLE }

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

    // Selection and publication are deliberately package-private. Structural checks cannot
    // authenticate forged descriptive values; callers inside this package remain application TCB.
    static final class Selection {
        final Status status;
        final RevisionId id;
        private final byte[] bytes;
        private Selection(Status status, RevisionId id, byte[] bytes) {
            this.status = status; this.id = id; this.bytes = bytes == null ? null : bytes.clone();
        }
    }
    static Selection select(Group group) {
        if (group == null || group.authenticated() == Authenticated.NONE_OBSERVED) {
            return new Selection(Status.NOTHING_TO_IMPORT, null, null);
        }
        if (group.authenticated() == Authenticated.INTEGRITY_CONTRADICTION) {
            return new Selection(Status.CONTRADICTION, null, null);
        }
        var valid = Objects.requireNonNull(group.representation(), "Missing selected representation");
        var id = Objects.requireNonNull(group.objectId(), "Missing ID");
        byte[] bytes = valid.representation();
        if (!id.equals(valid.objectId()) || bytes.length != 1024) throw new IllegalArgumentException("Selection mismatch");
        boolean observed = false;
        boolean unavailable = false;
        for (Candidate sibling : group.siblings()) {
            if (!id.hex().equals(sibling.transport().document().displayName())) {
                throw new IllegalArgumentException("Mixed group IDs");
            }
            if (sibling.kind() == Kind.VALIDATION_UNAVAILABLE) unavailable = true;
            if (sibling.kind() == Kind.VALID) {
                if (!valid.equals(sibling.valid())) return new Selection(Status.CONTRADICTION, null, null);
                if (sibling.transport().expectedMaximum() != 1024
                        || sibling.transport().state() != org.totipo.android.provider.ProviderSnapshot.ByteState.PRESENT
                        || !Arrays.equals(bytes, sibling.transport().bytes())) {
                    throw new IllegalArgumentException("Validated transport mismatch");
                }
                observed = true;
            }
        }
        if (!observed) throw new IllegalArgumentException("No validated sibling");
        if (unavailable) return new Selection(Status.DEFERRED_VALIDATION, null, null);
        return new Selection(null, id, bytes);
    }

    /** Small result-mapping seam; store must be exclusively owned and validating session closed.
     * No retry: Uncertain remains uncertain, retaining its actual reason. */
    static ObjectWrite publishExact(TotipoStore store, Selection selected) {
        if (selected.status != null || selected.id == null || selected.bytes == null || selected.bytes.length != 1024) {
            throw new IllegalArgumentException("Missing exact selection");
        }
        return store.publishObject(new ObjectName(selected.id.hex()), selected.bytes.clone());
    }
    static Status map(ObjectWrite write) {
        if (write instanceof ObjectWrite.Written) return Status.IMPORTED;
        if (write instanceof ObjectWrite.AlreadyPresentExact) return Status.ALREADY_PRESENT;
        if (write instanceof ObjectWrite.ExistingDifferent) return Status.BLOCKED_EXISTING_DIFFERENT;
        if (write instanceof ObjectWrite.Uncertain) return Status.IMPORT_UNCERTAIN;
        if (write instanceof ObjectWrite.Failed) return Status.STORAGE_FAILED;
        throw new IllegalStateException("Missing publication outcome");
    }
}
