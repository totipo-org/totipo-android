package org.totipo.android.sync;

import org.totipo.RevisionId;

/** Detached exact ciphertext. No session or private-store authority. */
public record DetachedImmutableObject(RevisionId id, byte[] representation) {
    public static final class CapacityExceeded extends IllegalStateException {}
    public static final int REPRESENTATION_BYTES = org.totipo.android.provider.ImmutableCandidateClassifier.REPRESENTATION_BYTES;
    public static final int MAX_OBJECTS = 512;
    public static final int MAX_BATCH_BYTES = MAX_OBJECTS * REPRESENTATION_BYTES;
    public DetachedImmutableObject {
        java.util.Objects.requireNonNull(id);
        if (representation.length != REPRESENTATION_BYTES) throw new IllegalArgumentException("Unsupported immutable representation size");
        representation = representation.clone();
    }
    @Override public byte[] representation() { return representation.clone(); }
}
