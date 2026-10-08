package org.totipo.android.reconcile;

import java.util.Arrays;
import java.util.Objects;
import org.totipo.RevisionId;
import org.totipo.android.provider.ImmutableCandidateClassifier.*;
import org.totipo.spi.ObjectName;
import org.totipo.spi.ObjectWrite;
import org.totipo.spi.TotipoStore;

/** Internal selection/publication TCB. Production evidence comes only from active-session
 * validation. Provider IDs have no protocol authority. No lifecycle or crypto lives here. */
public final class ImmutableCandidateImporter {
    private ImmutableCandidateImporter() {}
    public enum Status { IMPORTED, ALREADY_PRESENT, BLOCKED_EXISTING_DIFFERENT,
        IMPORT_UNCERTAIN, STORAGE_FAILED, NOTHING_TO_IMPORT, CONTRADICTION,
        DEFERRED_VALIDATION, AUTHENTICATION_UNAVAILABLE }

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
        if (group == null) {
            return new Selection(Status.NOTHING_TO_IMPORT, null, null);
        }
        if (group.authenticated() == Authenticated.INTEGRITY_CONTRADICTION) {
            return new Selection(Status.CONTRADICTION, null, null);
        }
        if (group.authenticated() == Authenticated.NONE_OBSERVED) {
            boolean unavailable = group.siblings().stream().anyMatch(c -> c.kind() == Kind.VALIDATION_UNAVAILABLE);
            return new Selection(unavailable ? Status.DEFERRED_VALIDATION : Status.NOTHING_TO_IMPORT, null, null);
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

    /** Small result-mapping seam; store must be under coordinated exclusive bridge access (or isolated test ownership).
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
