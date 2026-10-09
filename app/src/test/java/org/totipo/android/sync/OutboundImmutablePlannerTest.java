package org.totipo.android.sync;

import java.util.*;
import org.junit.Test;
import org.totipo.*;
import org.totipo.android.provider.*;
import org.totipo.android.provider.ProviderSnapshot.*;
import org.totipo.android.provider.ImmutableCandidateClassifier.*;
import static org.junit.Assert.*;
import static org.totipo.android.sync.OutboundImmutablePlanner.*;

public final class OutboundImmutablePlannerTest {
    final PublicationPort port = new PublicationPort();
    final DetachedImmutableObject object = ProviderObjectWriterTest.objects().get(0);
    Result evidence(State coverage, Authenticated authenticated, Kind... kinds) {
        port.coverage = coverage;
        if (kinds.length > 0) port.objects.put(object.id().hex(), object.representation());
        Scan scan = port.scan(PublicationPort.TREE.locator());
        List<Candidate> siblings = Arrays.stream(kinds).map(kind -> new Candidate(
                new Bytes("e", port.doc(object.id().hex()), 1024, ByteState.PRESENT, object.representation(), Issue.NONE),
                kind, kind == Kind.VALIDATION_UNAVAILABLE ? Unavailability.UNSUPPORTED : Unavailability.NONE,
                kind == Kind.VALID ? new ObjectCandidateValidation.Valid(object.id(), object.representation()) : null)).toList();
        return new Result(scan, kinds.length == 0 ? List.of() : List.of(new Group(object.id(), coverage, siblings, authenticated,
                authenticated == Authenticated.ONE_REPRESENTATION ? siblings.stream().filter(c -> c.valid() != null).findFirst().orElseThrow().valid() : null)));
    }
    @Test public void emptyAlreadyPresentMissingSeveralAndEqualDuplicates() {
        var empty = evidence(State.COMPLETE, Authenticated.NONE_OBSERVED);
        assertTrue(plan(List.of(), empty, true).missing().isEmpty());
        assertEquals(1, plan(List.of(object), empty, true).missing().size());
        assertEquals(3, plan(ProviderObjectWriterTest.objects(), empty, true).missing().size());
        for (Kind[] siblings : List.of(new Kind[]{Kind.VALID}, new Kind[]{Kind.VALID, Kind.VALID}, new Kind[]{Kind.VALID, Kind.INVALID})) {
            var exact = evidence(State.COMPLETE, Authenticated.ONE_REPRESENTATION, siblings);
            assertEquals(Outcome.ALREADY_PRESENT_EXACT, plan(List.of(object), exact, true).items().get(0).outcome());
            assertTrue(plan(List.of(object), exact, true).missing().isEmpty());
        }
    }
    @Test public void invalidUnavailableAndValidationUnavailableBlockOnlyTheirId() {
        for (Kind kind : List.of(Kind.INVALID, Kind.TRANSPORT_UNAVAILABLE, Kind.VALIDATION_UNAVAILABLE)) {
            var p = plan(ProviderObjectWriterTest.objects(), evidence(State.COMPLETE, Authenticated.NONE_OBSERVED, kind), true);
            assertEquals(2, p.missing().size()); assertTrue(p.blocked());
            assertEquals(kind == Kind.VALIDATION_UNAVAILABLE ? Outcome.VALIDATION_UNAVAILABLE : Outcome.BLOCKED_EXISTING_CANONICAL_CANDIDATE,
                    p.items().get(0).outcome());
        }
    }
    @Test public void contradictionsAndUnequalLocalValidComparisonStopWholeBatch() {
        var contradiction = evidence(State.COMPLETE, Authenticated.INTEGRITY_CONTRADICTION, Kind.VALID, Kind.VALID);
        assertTrue(plan(ProviderObjectWriterTest.objects(), contradiction, true).missing().isEmpty());
        var exact = evidence(State.COMPLETE, Authenticated.ONE_REPRESENTATION, Kind.VALID);
        byte[] unequal = object.representation(); unequal[0] = 1;
        var p = plan(List.of(new DetachedImmutableObject(object.id(), unequal)), exact, true);
        assertEquals(Outcome.INTEGRITY_CONTRADICTION, p.items().get(0).outcome()); assertTrue(p.missing().isEmpty());
    }
    @Test public void incompleteLoadingUnavailableAndReadOnlyNeverPlanWrites() {
        for (State state : State.values()) if (state != State.COMPLETE)
            assertTrue(plan(List.of(object), evidence(state, Authenticated.NONE_OBSERVED), true).missing().isEmpty());
        assertTrue(plan(List.of(object), evidence(State.COMPLETE, Authenticated.NONE_OBSERVED), false).missing().isEmpty());
    }
    @Test public void missingDuplicateAndUnsupportedDirectoryNeverPlanWrites() {
        var base = evidence(State.COMPLETE, Authenticated.NONE_OBSERVED);
        for (List<Document> roots : List.of(List.<Document>of(), List.of(port.directory, port.directory),
                List.of(new Document(port.directory.tree(), port.directory.locator(), "dir", "root", "objects-v1",
                        "vnd.android.document/directory", null, 0L)))) {
            Scan scan = new Scan("e", base.transport().tree(), new Listing("e", "root", roots, State.COMPLETE, List.of()),
                    base.transport().directories(), State.COMPLETE, List.of());
            assertTrue(plan(List.of(object), new Result(scan, List.of()), true).missing().isEmpty());
        }
    }
    @Test public void postflightEvidenceControlsSuccessIncludingExceptionWithExactObservation() {
        var intended = plan(List.of(object), evidence(State.COMPLETE, Authenticated.NONE_OBSERVED), true);
        for (Kind[] siblings : List.of(new Kind[]{Kind.VALID}, new Kind[]{Kind.VALID, Kind.VALID}, new Kind[]{Kind.VALID, Kind.INVALID}))
            assertEquals("Local changes published", confirm(intended, List.of(object), evidence(State.COMPLETE, Authenticated.ONE_REPRESENTATION, siblings)));
        assertTrue(confirm(intended, List.of(object), evidence(State.COMPLETE, Authenticated.INTEGRITY_CONTRADICTION, Kind.VALID)).startsWith("Integrity problem"));
        for (State state : State.values()) {
            port.objects.clear();
            var absent = evidence(state, Authenticated.NONE_OBSERVED);
            assertEquals(state == State.COMPLETE ? Verification.NOT_OBSERVED : Verification.UNCERTAIN, verify(object, absent));
            assertTrue(confirm(intended, List.of(object), absent).startsWith("Publication uncertain"));
        }
        assertTrue(confirm(intended, List.of(object), evidence(State.COMPLETE, Authenticated.NONE_OBSERVED, Kind.INVALID)).startsWith("Publication uncertain"));
    }
}
