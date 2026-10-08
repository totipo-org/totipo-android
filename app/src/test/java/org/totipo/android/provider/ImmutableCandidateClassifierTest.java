package org.totipo.android.provider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.totipo.*;
import org.totipo.spi.*;
import org.totipo.storage.nio.NioTotipoStore;
import org.totipo.android.provider.ProviderSnapshot.*;
import org.totipo.android.provider.ImmutableCandidateClassifier.*;
import static org.junit.Assert.*;

public final class ImmutableCandidateClassifierTest {
    private static final Tree TREE = new Tree("synthetic", "content://synthetic/tree/root", "root");
    private static final String NAME = "a".repeat(64);
    private static Fixture authored;
    private static Fixture otherRoot;

    /** Real released core authors the fixture in disposable JVM-only private NIO roots.
     * No Android canonical replica or actual provider is touched by fixture setup. */
    private static final class Fixture implements AutoCloseable {
        final Path root;
        final GuardStore store;
        final VaultSession session;
        final RevisionId id;
        final byte[] bytes;
        Fixture() throws Exception {
            root = Files.createTempDirectory("m1f-jvm-");
            char[] password = "M1F disposable test password".toCharArray();
            try {
                var created = Totipo.create(NioTotipoStore.openPrivate(root), password);
                assertTrue(created instanceof CreateVaultResult.Created);
                try (var creating = ((CreateVaultResult.Created) created).session()) {
                    awaitFinished(creating);
                    try (var secret = NewSecret.copyOf(new byte[] {1,2,3,4,5,6,7,8,9,10});
                         var token = creating.state().createToken()) {
                        var saved = token.issuer("M1F fixture").account("test").secret(secret).save();
                        assertTrue(saved instanceof SaveResult.Saved);
                        id = ((SaveResult.Saved) saved).revisions().get(0);
                    }
                }
                bytes = Files.readAllBytes(root.resolve("objects-v1").resolve(id.hex()));
                store = new GuardStore(NioTotipoStore.openPrivate(root));
                var opened = Totipo.open(store, password);
                assertTrue(opened instanceof OpenResult.Opened);
                session = ((OpenResult.Opened) opened).session();
                awaitFinished(session);
                // Initial observation has finished. Every validation must now do zero SPI I/O.
                store.forbidAccess = true;
            } finally { Arrays.fill(password, '\0'); }
        }
        @Override public void close() throws Exception {
            store.forbidAccess = false;
            session.close();
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
    /** Storage spy delegates fixture authoring/observation to released NIO. It does not mock crypto. */
    private static final class GuardStore implements TotipoStore {
        final TotipoStore delegate;
        volatile boolean forbidAccess;
        GuardStore(TotipoStore delegate) { this.delegate = delegate; }
        void access() { if (forbidAccess) throw new AssertionError("Validation accessed local store"); }
        public BoundedRead readVault(int expected) { access(); return delegate.readVault(expected); }
        public ObjectScan scanObjects() { access(); return delegate.scanObjects(); }
        public BoundedRead readObject(ObjectName name, int expected) { access(); return delegate.readObject(name, expected); }
        public ObjectWrite publishObject(ObjectName name, byte[] bytes) { access(); return delegate.publishObject(name, bytes); }
        public VaultPrepare prepareVault(byte[] bytes) { access(); return delegate.prepareVault(bytes); }
        public void close() { access(); delegate.close(); }
    }
    private static void awaitFinished(VaultSession session) throws Exception {
        var latch = new CountDownLatch(1);
        var subscription = new AtomicReference<Flow.Subscription>();
        var error = new AtomicReference<Throwable>();
        session.states().subscribe(new Flow.Subscriber<VaultState>() {
            public void onSubscribe(Flow.Subscription s) { subscription.set(s); s.request(Long.MAX_VALUE); }
            public void onNext(VaultState state) {
                if (state.observation() instanceof ObservationProgress.Finished) latch.countDown();
            }
            public void onError(Throwable t) { error.set(t); latch.countDown(); }
            public void onComplete() { latch.countDown(); }
        });
        try {
            assertTrue("Initial observation timed out", latch.await(30, TimeUnit.SECONDS));
            assertNull(error.get());
            assertTrue(session.state().observation() instanceof ObservationProgress.Finished);
            assertTrue(session.state().diagnostics().isEmpty());
        } finally { if (subscription.get() != null) subscription.get().cancel(); }
    }
    @BeforeClass public static void setup() throws Exception { authored = new Fixture(); otherRoot = new Fixture(); }
    @AfterClass public static void cleanup() throws Exception {
        try { if (authored != null) authored.close(); } finally { if (otherRoot != null) otherRoot.close(); }
    }
    private static Bytes observation(String name, String providerId, ByteState state, byte[] bytes) {
        var document = new Document(TREE, "content://synthetic/" + providerId, providerId,
                "directory", name, "application/octet-stream", 9999L, 0L);
        return new Bytes("epoch", document, 1024, state, bytes, Issue.NONE);
    }
    private static Bytes good(String providerId) { return observation(authored.id.hex(), providerId, ByteState.PRESENT, authored.bytes); }
    private static Scan scan(State coverage, Bytes... siblings) {
        var directory = new Document(TREE, "content://synthetic/directory", "directory", "root", "objects-v1",
                "vnd.android.document/directory", null, null);
        return new Scan("epoch", TREE, new Listing("epoch", "root", List.of(directory), coverage, List.of()),
                List.of(new Directory(directory, new Listing("epoch", "directory",
                        Arrays.stream(siblings).map(Bytes::document).toList(), coverage, List.of()), Arrays.asList(siblings))),
                coverage, List.of());
    }
    private static Group group(State coverage, Bytes... siblings) {
        return ImmutableCandidateClassifier.classify(scan(coverage, siblings), authored.session).groups().get(0);
    }
    @Test public void zeroBytesAreShortWithoutValidation() {
        assertEquals(Kind.TRANSPORT_SHORT, ImmutableCandidateClassifier.classify(
                observation(NAME, "zero", ByteState.SHORT, new byte[0]), noValidation()).kind());
    }
    @Test public void partial256BytesAreShortWithoutValidation() {
        assertEquals(Kind.TRANSPORT_SHORT, ImmutableCandidateClassifier.classify(
                observation(NAME, "partial", ByteState.SHORT, new byte[256]), noValidation()).kind());
    }
    @Test public void overflow1025BytesIsTransportOversizedWithoutValidation() {
        assertEquals(Kind.TRANSPORT_OVERSIZED, ImmutableCandidateClassifier.classify(
                observation(NAME, "overflow", ByteState.OVERSIZED, new byte[1025]), noValidation()).kind());
    }
    @Test public void unavailableMissingInterruptedAndLoadingAreSeparateFromInvalid() {
        for (var state : List.of(ByteState.MISSING, ByteState.UNAVAILABLE)) {
            var bytes = observation(NAME, "unavailable", state, new byte[256]);
            var interrupted = new Bytes(bytes.epoch(), bytes.document(), 1024, state, bytes.bytes(), Issue.INTERRUPTED);
            assertEquals(Kind.TRANSPORT_UNAVAILABLE, ImmutableCandidateClassifier.classify(interrupted, noValidation()).kind());
        }
        Group group = group(State.INCOMPLETE_LOADING, good("captured"));
        assertEquals(State.INCOMPLETE_LOADING, group.providerCoverage());
        assertEquals(Authenticated.ONE_REPRESENTATION, group.authenticated());
    }
    @Test public void exactMalformedIsRealJavaInvalid() {
        assertEquals(Kind.INVALID, ImmutableCandidateClassifier.classify(
                observation(NAME, "malformed", ByteState.PRESENT, new byte[1024]), authored.session).kind());
    }
    @Test public void coreAuthoredObjectIsRealJavaValidAndDefensive() {
        Candidate result = ImmutableCandidateClassifier.classify(good("valid"), authored.session);
        assertEquals(Kind.VALID, result.kind());
        assertEquals(authored.id, result.valid().objectId());
        assertArrayEquals(authored.bytes, result.valid().representation());
        byte[] exposed = result.valid().representation(); exposed[0] ^= 1;
        byte[] observed = result.transport().bytes(); observed[0] ^= 1;
        assertArrayEquals(authored.bytes, result.valid().representation());
        assertArrayEquals(authored.bytes, result.transport().bytes());
        assertFalse(result.valid().toString().contains(Arrays.toString(authored.bytes)));
    }
    @Test public void otherAuthenticatedRootRejectsValidObject() {
        assertEquals(Kind.INVALID, ImmutableCandidateClassifier.classify(good("wrong-root"), otherRoot.session).kind());
    }
    @Test public void exactDuplicatesCollapseAndRetainProviderIdentities() {
        Group result = group(State.COMPLETE, good("copy-a"), good("copy-b"));
        assertEquals(Authenticated.ONE_REPRESENTATION, result.authenticated());
        assertEquals(List.of("copy-a", "copy-b"), result.siblings().stream().map(c -> c.transport().document().id()).toList());
        assertEquals(result.siblings().get(0).valid(), result.siblings().get(1).valid());
        assertThrows(UnsupportedOperationException.class, () -> result.siblings().clear());
    }
    @Test public void validAndPartialSiblingPreserveAuthenticatedFact() {
        Group result = group(State.COMPLETE, good("valid"),
                observation(authored.id.hex(), "partial", ByteState.SHORT, new byte[256]));
        assertEquals(Authenticated.ONE_REPRESENTATION, result.authenticated());
        assertEquals(Kind.TRANSPORT_SHORT, result.siblings().get(1).kind());
    }
    @Test public void validAndInvalidSiblingPreserveAuthenticatedFact() {
        Group result = group(State.COMPLETE, good("valid"),
                observation(authored.id.hex(), "invalid", ByteState.PRESENT, new byte[1024]));
        assertEquals(Authenticated.ONE_REPRESENTATION, result.authenticated());
        assertEquals(Kind.INVALID, result.siblings().get(1).kind());
    }
    @Test public void validAndUnavailableSiblingPreserveAuthenticatedFactAndCoverage() {
        Group result = group(State.INCOMPLETE, good("valid"),
                observation(authored.id.hex(), "missing", ByteState.MISSING, new byte[0]));
        assertEquals(Authenticated.ONE_REPRESENTATION, result.authenticated());
        assertEquals(State.INCOMPLETE, result.providerCoverage());
        assertEquals(Kind.TRANSPORT_UNAVAILABLE, result.siblings().get(1).kind());
    }
    @Test public void symbolicUnequalValidatedValuesReportContradictionWithoutSelection() {
        // Descriptive public Valid constructors exercise comparison only. This is not a
        // cryptographic fixture or a claim that an honest same-ID collision is constructible.
        byte[] a = new byte[1024]; byte[] b = a.clone(); b[0] = 1;
        var first = new Candidate(observation(NAME, "A", ByteState.PRESENT, a), Kind.VALID,
                Unavailability.NONE, new ObjectCandidateValidation.Valid(new RevisionId(NAME), a));
        var second = new Candidate(observation(NAME, "B", ByteState.PRESENT, b), Kind.VALID,
                Unavailability.NONE, new ObjectCandidateValidation.Valid(new RevisionId(NAME), b));
        for (var siblings : List.of(List.of(first, second, first), List.of(second, first))) {
            Group result = ImmutableCandidateClassifier.analyze(new RevisionId(NAME), State.INCOMPLETE, siblings);
            assertEquals(Authenticated.INTEGRITY_CONTRADICTION, result.authenticated());
            assertNull(result.representation());
            assertEquals(siblings.size(), result.siblings().size());
        }
    }
    @Test public void incompleteCoverageSurvivesValidAndNoValidGroups() {
        for (State coverage : List.of(State.INCOMPLETE, State.INCOMPLETE_LOADING, State.UNAVAILABLE)) {
            assertEquals(coverage, group(coverage, good("valid")).providerCoverage());
            Group noValid = group(coverage, observation(NAME, "invalid", ByteState.PRESENT, new byte[1024]));
            assertEquals(coverage, noValid.providerCoverage());
            assertEquals(Authenticated.NONE_OBSERVED, noValid.authenticated());
            assertNull(noValid.representation());
            Result empty = ImmutableCandidateClassifier.classify(scan(coverage), authored.session);
            assertEquals(coverage, empty.transport().state());
            assertTrue(empty.groups().isEmpty());
        }
    }
    @Test public void noSessionClosedSessionAndClosureRaceAreValidationUnavailable() throws Exception {
        Candidate absent = ImmutableCandidateClassifier.classify(good("no-session"), null);
        assertEquals(Kind.VALIDATION_UNAVAILABLE, absent.kind());
        assertEquals(Unavailability.NO_SESSION, absent.unavailability());
        try (var fixture = new Fixture()) {
            fixture.store.forbidAccess = false;
            fixture.session.close();
            Candidate closed = ImmutableCandidateClassifier.classify(good("closed"), fixture.session);
            assertEquals(Kind.VALIDATION_UNAVAILABLE, closed.kind());
            assertEquals(Unavailability.SESSION_CLOSED, closed.unavailability());
        }
        // Lifecycle seam closes the real session at call entry, then uses Java's actual gate.
        try (var fixture = new Fixture()) {
            var closing = new SessionStub() {
                @Override public ObjectCandidateValidation validateObject(RevisionId id, byte[] bytes) {
                    fixture.store.forbidAccess = false;
                    fixture.session.close();
                    return fixture.session.validateObject(id, bytes);
                }
            };
            assertEquals(Unavailability.SESSION_CLOSED,
                    ImmutableCandidateClassifier.classify(good("race"), closing).unavailability());
        }
    }
    @Test public void unsupportedSessionIsUnavailableAndOtherIntegrationErrorsPropagate() {
        Candidate unsupported = ImmutableCandidateClassifier.classify(good("unsupported"), new SessionStub());
        assertEquals(Kind.VALIDATION_UNAVAILABLE, unsupported.kind());
        assertEquals(Unavailability.UNSUPPORTED, unsupported.unavailability());
        assertThrows(IllegalArgumentException.class, () -> ImmutableCandidateClassifier.classify(good("bug"),
                new SessionStub() {
                    @Override public ObjectCandidateValidation validateObject(RevisionId id, byte[] bytes) {
                        throw new IllegalArgumentException("Integration bug");
                    }
                }));
    }
    @Test public void malformedNamesVaultAndDirectoriesNeverReachJava() {
        for (String name : List.of("vault", NAME.toUpperCase(), NAME + ".tmp", "bad", "a".repeat(63))) {
            assertEquals(Kind.NONCANDIDATE, ImmutableCandidateClassifier.classify(
                    observation(name, "noise", ByteState.PRESENT, new byte[1024]), noValidation()).kind());
        }
        Bytes bytes = good("directory"); Document d = bytes.document();
        var directory = new Document(d.tree(), d.locator(), d.id(), d.parentId(), d.displayName(),
                "vnd.android.document/directory", null, null);
        assertEquals(Kind.NONCANDIDATE, ImmutableCandidateClassifier.classify(
                new Bytes("epoch", directory, 1024, ByteState.PRESENT, bytes.bytes(), Issue.NONE), noValidation()).kind());
    }
    @Test public void inconsistentBoundsFailAsIntegrationErrorsWithoutValidation() {
        Bytes bytes = good("bad-bound");
        assertThrows(IllegalArgumentException.class, () -> ImmutableCandidateClassifier.classify(
                new Bytes("epoch", bytes.document(), 87, ByteState.PRESENT, bytes.bytes(), Issue.NONE), noValidation()));
        assertThrows(IllegalArgumentException.class, () -> ImmutableCandidateClassifier.classify(
                observation(NAME, "bad-present", ByteState.PRESENT, new byte[256]), noValidation()));
    }
    @Test public void m1eTraversalAndClassifierPreserveDuplicateDirectories() {
        var a = new Document(TREE, "content://synthetic/A", "A", "root", "objects-v1",
                "vnd.android.document/directory", null, null);
        var b = new Document(TREE, "content://synthetic/B", "B", "root", "objects-v1",
                "vnd.android.document/directory", null, null);
        ProviderTraversal.Source source = new ProviderTraversal.Source() {
            public Listing children(String epoch, String parent, int limit) {
                return new Listing(epoch, parent, parent.equals("root") ? List.of(a, b)
                        : List.of(good(parent + "-copy").document()), State.COMPLETE, List.of());
            }
            public Bytes read(String epoch, Document document, int maximum) {
                return BoundedProviderRead.read(epoch, document, maximum,
                        () -> new java.io.ByteArrayInputStream(authored.bytes));
            }
        };
        Scan snapshot = ProviderTraversal.scan(TREE, 1024, source);
        Result result = ImmutableCandidateClassifier.classify(snapshot, authored.session);
        assertEquals(2, snapshot.directories().size());
        assertEquals(1, result.groups().size());
        assertEquals(Authenticated.ONE_REPRESENTATION, result.groups().get(0).authenticated());
        assertEquals(List.of("A-copy", "B-copy"), result.groups().get(0).siblings().stream()
                .map(c -> c.transport().document().id()).toList());
    }
    @Test public void closureBetweenSiblingsKeepsCompletedAuthenticatedFact() throws Exception {
        try (var fixture = new Fixture()) {
            var session = new SessionStub() {
                int calls;
                @Override public ObjectCandidateValidation validateObject(RevisionId id, byte[] bytes) {
                    if (++calls == 2) {
                        fixture.store.forbidAccess = false;
                        fixture.session.close();
                    }
                    return fixture.session.validateObject(id, bytes);
                }
            };
            Bytes a = observation(fixture.id.hex(), "first", ByteState.PRESENT, fixture.bytes);
            Bytes b = observation(fixture.id.hex(), "second", ByteState.PRESENT, fixture.bytes);
            Group result = ImmutableCandidateClassifier.classify(scan(State.COMPLETE, a, b), session).groups().get(0);
            assertEquals(Authenticated.ONE_REPRESENTATION, result.authenticated());
            assertEquals(Kind.VALID, result.siblings().get(0).kind());
            assertEquals(Kind.VALIDATION_UNAVAILABLE, result.siblings().get(1).kind());
            assertEquals(Unavailability.SESSION_CLOSED, result.siblings().get(1).unavailability());
            assertArrayEquals(fixture.bytes, result.representation().representation());
        }
    }
    @Test public void classificationPreservesLocalFilesGraphHeadsAndProviderEvidence() throws Exception {
        VaultState before = authored.session.state();
        var tokens = before.tokens();
        Map<String, List<Byte>> files = snapshotFiles(authored.root);
        Scan provider = scan(State.INCOMPLETE, good("valid"),
                observation(authored.id.hex(), "partial", ByteState.SHORT, new byte[256]));
        var rows = provider.directories().get(0).children().rows();
        byte[] providerBytes = provider.directories().get(0).candidates().get(0).bytes();
        Result result = ImmutableCandidateClassifier.classify(provider, authored.session);
        assertSame(provider, result.transport());
        assertSame(before, authored.session.state());
        assertEquals(tokens, authored.session.state().tokens());
        assertEquals(files, snapshotFiles(authored.root));
        assertEquals(rows, provider.directories().get(0).children().rows());
        assertArrayEquals(providerBytes, provider.directories().get(0).candidates().get(0).bytes());
        assertThrows(UnsupportedOperationException.class, () -> result.groups().clear());
        // GuardStore also fails this and every real crypto test on ANY local SPI I/O.
        // Provider/grant access is impossible: classifier takes only immutable evidence/session.
    }
    private static Map<String, List<Byte>> snapshotFiles(Path root) throws Exception {
        var result = new LinkedHashMap<String, List<Byte>>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted().toList()) {
                List<Byte> bytes = new ArrayList<>();
                if (Files.isRegularFile(path)) for (byte b : Files.readAllBytes(path)) bytes.add(b);
                result.put(root.relativize(path).toString(), bytes);
            }
        }
        return result;
    }
    private static VaultSession noValidation() {
        return new SessionStub() {
            @Override public ObjectCandidateValidation validateObject(RevisionId id, byte[] bytes) {
                throw new AssertionError("Non-exact transport reached Java validation");
            }
        };
    }
    private static class SessionStub implements VaultSession {
        public VaultFingerprint fingerprint() { throw new AssertionError(); }
        public VaultState state() { throw new AssertionError(); }
        public Flow.Publisher<VaultState> states() { throw new AssertionError(); }
        public void requestRefresh() { throw new AssertionError(); }
        public PasswordChangeResult changePassword(char[] oldPassword, char[] newPassword) { throw new AssertionError(); }
        public void close() { throw new AssertionError(); }
    }
}
