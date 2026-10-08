package org.totipo.android.provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import org.totipo.ObjectCandidateValidation;
import org.totipo.RevisionId;
import org.totipo.SessionClosedException;
import org.totipo.VaultSession;
import org.totipo.android.provider.ProviderSnapshot.*;

/**
 * Synchronous, read-only classification above M1E. Call on a worker thread during an
 * unlocked operation, using its already owned session. No session/credentials are retained.
 * Only immutable objects are validated; vault and other names remain noncandidates.
 * Results are in-memory observations, not persistence, current heads, freshness or absence.
 */
public final class ImmutableCandidateClassifier {
    private ImmutableCandidateClassifier() {}

    public enum Kind { NONCANDIDATE, TRANSPORT_UNAVAILABLE, TRANSPORT_SHORT,
        TRANSPORT_OVERSIZED, VALIDATION_UNAVAILABLE, INVALID, VALID }
    public enum Unavailability { NONE, NO_SESSION, SESSION_CLOSED, UNSUPPORTED }
    public enum Authenticated { NONE_OBSERVED, ONE_REPRESENTATION, INTEGRITY_CONTRADICTION }

    /** Descriptive value; authenticated evidence originates only from classify's session call. */
    public record Candidate(Bytes transport, Kind kind, Unavailability unavailability,
                            ObjectCandidateValidation.Valid valid) {
        public Candidate {
            Objects.requireNonNull(transport); Objects.requireNonNull(kind); Objects.requireNonNull(unavailability);
            if ((kind == Kind.VALID) != (valid != null)) throw new IllegalArgumentException("Valid result mismatch");
            if ((kind == Kind.VALIDATION_UNAVAILABLE) != (unavailability != Unavailability.NONE)) {
                throw new IllegalArgumentException("Unavailability mismatch");
            }
        }
    }
    /** ONE_REPRESENTATION collapses exact copies while siblings retain every provider identity.
     * Contradiction exposes no selected representation. NONE_OBSERVED never asserts absence.
     * Coverage describes provider observation only, even when COMPLETE. */
    public record Group(RevisionId objectId, State providerCoverage, List<Candidate> siblings,
                        Authenticated authenticated, ObjectCandidateValidation.Valid representation) {
        public Group { siblings = Collections.unmodifiableList(new ArrayList<>(siblings)); }
    }
    /** Keeps full scan provenance, including local listing/read issues and noncandidate rows. */
    public record Result(Scan transport, List<Group> groups) {
        public Result { groups = Collections.unmodifiableList(new ArrayList<>(groups)); }
    }

    /** Groups are validated here with one supplied session/root, never mixed across sessions.
     * A null session leaves exact candidates validation-unavailable. Closure races are resolved
     * by Java's own gate: completed Valid facts survive, subsequent calls are unavailable. */
    public static Result classify(Scan scan, VaultSession session) {
        Objects.requireNonNull(scan);
        var grouped = new LinkedHashMap<RevisionId, List<Candidate>>();
        for (Directory directory : scan.directories()) {
            for (Bytes bytes : directory.candidates()) {
                Candidate candidate = classify(bytes, session);
                if (candidate.kind() == Kind.NONCANDIDATE) continue;
                RevisionId id = new RevisionId(bytes.document().displayName());
                grouped.computeIfAbsent(id, ignored -> new ArrayList<>()).add(candidate);
            }
        }
        List<Group> groups = new ArrayList<>();
        grouped.forEach((id, siblings) -> groups.add(analyze(id, scan.state(), siblings)));
        return new Result(scan, groups);
    }

    /** Invalid names/directories bypass Java; inconsistent bounded evidence is an integration bug. */
    public static Candidate classify(Bytes observation, VaultSession session) {
        Objects.requireNonNull(observation);
        var document = Objects.requireNonNull(observation.document());
        if (document.isDirectory() || !ProviderTraversal.isObjectName(document.displayName())) {
            return outcome(observation, Kind.NONCANDIDATE);
        }
        if (observation.expectedMaximum() != 1024) throw new IllegalArgumentException("Expected object read bound 1024");
        switch (observation.state()) {
            case MISSING, UNAVAILABLE: return outcome(observation, Kind.TRANSPORT_UNAVAILABLE);
            case SHORT:
                if (observation.bytes().length >= 1024) throw new IllegalArgumentException("Inconsistent short read");
                return outcome(observation, Kind.TRANSPORT_SHORT);
            case OVERSIZED:
                if (observation.bytes().length != 1025) throw new IllegalArgumentException("Inconsistent overflow probe");
                return outcome(observation, Kind.TRANSPORT_OVERSIZED);
            case PRESENT: break;
        }
        byte[] representation = observation.bytes();
        if (representation.length != 1024) throw new IllegalArgumentException("Inconsistent exact read");
        // The public Java type enforces canonical ID syntax. Rejection after M1E's predicate
        // is an integration error and intentionally propagates, never authenticated Invalid.
        RevisionId id = new RevisionId(document.displayName());
        if (session == null) return unavailable(observation, Unavailability.NO_SESSION);
        final ObjectCandidateValidation validation;
        try {
            validation = session.validateObject(id, representation);
        } catch (SessionClosedException closed) {
            return unavailable(observation, Unavailability.SESSION_CLOSED);
        } catch (UnsupportedOperationException unsupported) {
            return unavailable(observation, Unavailability.UNSUPPORTED);
        }
        if (validation instanceof ObjectCandidateValidation.Valid valid) {
            if (!id.equals(valid.objectId()) || !java.util.Arrays.equals(representation, valid.representation())) {
                throw new IllegalStateException("Java validation result differs from supplied candidate");
            }
            return new Candidate(observation, Kind.VALID, Unavailability.NONE, valid);
        }
        if (validation instanceof ObjectCandidateValidation.Invalid) return outcome(observation, Kind.INVALID);
        throw new IllegalStateException("Missing Java validation result");
    }

    // Package-private comparison seam for symbolic contradiction tests. Production enters
    // only through classify(Scan, session); do not combine independently sourced roots here.
    static Group analyze(RevisionId id, State coverage, List<Candidate> siblings) {
        ObjectCandidateValidation.Valid first = null;
        boolean contradiction = false;
        for (Candidate sibling : siblings) {
            if (!id.hex().equals(sibling.transport().document().displayName())) {
                throw new IllegalArgumentException("Mixed candidate names");
            }
            if (sibling.valid() == null) continue;
            if (!id.equals(sibling.valid().objectId())) throw new IllegalArgumentException("Mixed validated IDs");
            if (first == null) first = sibling.valid();
            else if (!first.equals(sibling.valid())) contradiction = true;
        }
        return new Group(id, coverage, siblings, contradiction ? Authenticated.INTEGRITY_CONTRADICTION
                : first == null ? Authenticated.NONE_OBSERVED : Authenticated.ONE_REPRESENTATION,
                contradiction ? null : first);
    }
    private static Candidate outcome(Bytes bytes, Kind kind) {
        return new Candidate(bytes, kind, Unavailability.NONE, null);
    }
    private static Candidate unavailable(Bytes bytes, Unavailability reason) {
        return new Candidate(bytes, Kind.VALIDATION_UNAVAILABLE, reason, null);
    }
}
