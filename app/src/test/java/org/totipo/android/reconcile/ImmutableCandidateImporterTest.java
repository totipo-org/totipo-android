package org.totipo.android.reconcile;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.totipo.*;
import org.totipo.android.*;
import org.totipo.android.provider.ImmutableCandidateClassifier;
import org.totipo.android.provider.ImmutableCandidateClassifier.*;
import org.totipo.android.provider.ProviderSnapshot.*;
import org.totipo.spi.*;
import org.totipo.storage.nio.NioTotipoStore;
import static org.junit.Assert.*;
import static org.totipo.android.reconcile.ImmutableCandidateImporter.*;
import org.totipo.android.reconcile.HistoricalImmutableCandidateImporter.Result;
import static org.totipo.android.reconcile.HistoricalImmutableCandidateImporter.*;

public final class ImmutableCandidateImporterTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static Path fixture;
    private static byte[] vault, unrelated, remote, child;
    private static RevisionId unrelatedId, id, childId;
    private static TokenId tokenId;
    private static final Tree TREE = new Tree("fixture", "content://fixture/root", "root");
    private static char[] password() { return "M1G disposable fixture".toCharArray(); }

    @BeforeClass public static void authorThroughRealCore() throws Exception {
        fixture = Files.createTempDirectory("m1g-fixture-");
        Path a = Files.createDirectory(fixture.resolve("A"));
        Path b = Files.createDirectory(fixture.resolve("B"));
        char[] credential = password();
        try {
            try (var session = ((CreateVaultResult.Created) Totipo.create(NioTotipoStore.openPrivate(a), credential)).session()) {
                finished(session);
                unrelatedId = author(session, "local unrelated").revisions().get(0);
            }
            vault = Files.readAllBytes(a.resolve("vault"));
            unrelated = read(a, unrelatedId);
            // Same-root wrapper copying is ONLY disposable test fixture construction.
            Files.write(b.resolve("vault"), vault);
            try (var session = open(b, credential)) {
                finished(session);
                SaveResult.Saved saved = author(session, "remote fixture");
                id = saved.revisions().get(0); tokenId = saved.tokenId();
            }
            remote = read(b, id);
            try (var session = open(b, credential)) {
                finished(session);
                try (var update = session.state().update(session.state().token(tokenId).orElseThrow().heads().get(0))) {
                    childId = ((SaveResult.Saved) update.issuer("child fixture").save()).revisions().get(0);
                }
            }
            child = read(b, childId);
        } finally { Arrays.fill(credential, '\0'); }
    }
    @AfterClass public static void cleanup() throws Exception {
        if (fixture != null) try (var paths = Files.walk(fixture)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
        }
    }
    private static SaveResult.Saved author(VaultSession session, String issuer) {
        try (var secret = NewSecret.copyOf(new byte[]{1,2,3,4}); var create = session.state().createToken()) {
            return (SaveResult.Saved) create.issuer(issuer).secret(secret).save();
        }
    }
    private static VaultSession open(Path root, char[] credential) throws Exception {
        return ((OpenResult.Opened) Totipo.open(NioTotipoStore.openPrivate(root), credential)).session();
    }
    private static byte[] read(Path root, RevisionId revision) throws Exception {
        try (var store = NioTotipoStore.openPrivate(root)) {
            return ((BoundedRead.Present) store.readObject(new ObjectName(revision.hex()), 1024)).bytes();
        }
    }
    private LocalReplicaOwner owner() throws Exception {
        var owner = TestReplicaOwners.create(temporary.newFolder().toPath());
        try (var lease = owner.acquire()) {
            Files.write(lease.root().resolve("vault"), vault);
            try (var store = NioTotipoStore.openPrivate(lease.root())) {
                assertTrue(store.publishObject(new ObjectName(unrelatedId.hex()), unrelated) instanceof ObjectWrite.Written);
            }
        }
        return owner;
    }
    private static Bytes bytes(RevisionId revision, String providerId, ByteState state, byte[] bytes) {
        var document = new Document(TREE, "content://fixture/" + providerId, providerId, "objects",
                revision.hex(), "application/octet-stream", null, null);
        return new Bytes("epoch", document, 1024, state, bytes, Issue.NONE);
    }
    private static Bytes good(String providerId) { return bytes(id, providerId, ByteState.PRESENT, remote); }
    private static Scan scan(State coverage, Bytes... bytes) {
        var directory = new Document(TREE, "content://fixture/objects", "objects", "root", "objects-v1",
                "vnd.android.document/directory", null, null);
        return new Scan("epoch", TREE, new Listing("epoch", "root", List.of(directory), coverage, List.of()),
                List.of(new Directory(directory, new Listing("epoch", "objects",
                        Arrays.stream(bytes).map(Bytes::document).toList(), coverage, List.of()), Arrays.asList(bytes))),
                coverage, List.of());
    }
    private static Result run(LocalReplicaOwner.Lease lease, Scan scan, RevisionId revision) throws Exception {
        char[] credential = password();
        try { return importOne(lease, scan, revision, credential); }
        finally { Arrays.fill(credential, '\0'); }
    }
    private static void neutral(Path root) throws Exception {
        assertArrayEquals(vault, Files.readAllBytes(root.resolve("vault")));
        assertArrayEquals(unrelated, read(root, unrelatedId));
    }
    private static void observe(Path root, RevisionId revision) throws Exception {
        char[] credential = password();
        try (var session = open(root, credential)) {
            finished(session);
            assertTrue(session.state().token(tokenId).orElseThrow().heads().stream()
                    .anyMatch(head -> head.revision().equals(revision)));
        } finally { Arrays.fill(credential, '\0'); }
    }

    @Test public void newImportAndExactIdempotenceReopenThroughCore() throws Exception {
        var owner = owner();
        try (var lease = owner.acquire()) {
            Scan scan = scan(State.COMPLETE, good("remote"));
            var result = run(lease, scan, id);
            assertEquals(Status.IMPORTED, result.status());
            assertTrue(result.publication() instanceof ObjectWrite.Written);
            assertEquals(Kind.VALID, result.group().siblings().get(0).kind());
            assertSame(scan, result.evidence().transport());
            assertArrayEquals(remote, read(lease.root(), id));
            observe(lease.root(), id);
            var again = run(lease, scan, id);
            assertEquals(Status.ALREADY_PRESENT, again.status());
            assertTrue(again.publication() instanceof ObjectWrite.AlreadyPresentExact);
            assertArrayEquals(remote, read(lease.root(), id));
            neutral(lease.root());
        }
    }
    @Test public void duplicatesCollapseAndProviderEvidenceIsUntouched() throws Exception {
        var owner = owner();
        try (var lease = owner.acquire()) {
            Scan scan = scan(State.COMPLETE, good("copy-A"), good("copy-B"));
            var result = run(lease, scan, id);
            assertEquals(Status.IMPORTED, result.status());
            assertEquals(2, result.group().siblings().size());
            assertEquals(List.of("copy-A", "copy-B"), result.group().siblings().stream()
                    .map(c -> c.transport().document().id()).toList());
            assertArrayEquals(remote, scan.directories().get(0).candidates().get(1).bytes());
            // Publication seam proves duplicate selection makes exactly one SPI call.
            var selection = select(result.group());
            var spy = new PublicationSpy(new ObjectWrite.Written());
            publishExact(spy, selection); assertEquals(1, spy.calls);
            assertArrayEquals(remote, spy.bytes); assertEquals(id.hex(), spy.name.value());
            neutral(lease.root());
        }
    }
    @Test public void partialInvalidOversizedUnavailableSiblingsKeepPositiveFactAndCoverage() throws Exception {
        for (State coverage : State.values()) {
            var owner = owner();
            try (var lease = owner.acquire()) {
                var result = run(lease, scan(coverage, good("valid"),
                        bytes(id, "partial", ByteState.SHORT, new byte[256]),
                        bytes(id, "invalid", ByteState.PRESENT, new byte[1024]),
                        bytes(id, "oversized", ByteState.OVERSIZED, new byte[1025]),
                        bytes(id, "unavailable", ByteState.UNAVAILABLE, new byte[0])), id);
                assertEquals(Status.IMPORTED, result.status());
                assertEquals(coverage, result.group().providerCoverage());
                assertEquals(List.of(Kind.VALID, Kind.TRANSPORT_SHORT, Kind.INVALID,
                        Kind.TRANSPORT_OVERSIZED, Kind.TRANSPORT_UNAVAILABLE), result.group().siblings().stream().map(Candidate::kind).toList());
                neutral(lease.root());
            }
        }
    }
    @Test public void prepopulatedExactIsAcknowledgedAndDifferentIsPreserved() throws Exception {
        for (boolean exact : List.of(true, false)) {
            var owner = owner();
            try (var lease = owner.acquire()) {
                byte[] before = remote.clone(); if (!exact) before[0] ^= 1;
                try (var store = NioTotipoStore.openPrivate(lease.root())) {
                    assertTrue(store.publishObject(new ObjectName(id.hex()), before) instanceof ObjectWrite.Written);
                }
                var result = run(lease, scan(State.COMPLETE, good("remote")), id);
                assertEquals(exact ? Status.ALREADY_PRESENT : Status.BLOCKED_EXISTING_DIFFERENT, result.status());
                assertTrue(exact ? result.publication() instanceof ObjectWrite.AlreadyPresentExact
                        : result.publication() instanceof ObjectWrite.ExistingDifferent);
                assertArrayEquals(before, read(lease.root(), id)); neutral(lease.root());
                try (var paths = Files.list(lease.root().resolve("objects-v1"))) { assertEquals(2, paths.count()); }
                assertArrayEquals(remote, result.group().representation().representation());
            }
        }
    }
    @Test public void sameLeaseSurvivesCloseAndLiveSessionCannotPublish() throws Exception {
        var owner = owner();
        try (var lease = owner.acquire()) {
            char[] credential = password();
            VaultSession session;
            var ownedStore = new TrackingStore(NioTotipoStore.openPrivate(lease.root()));
            try { session = ((OpenResult.Opened) Totipo.open(ownedStore, credential)).session(); } finally { Arrays.fill(credential, '\0'); }
            finished(session);
            try (var transition = new HistoricalImmutableCandidateImporter.Transition(lease, session)) {
                Group group = transition.classify(scan(State.COMPLETE, good("remote"))).groups().get(0);
                var selected = select(group);
                assertThrows(IllegalStateException.class, () -> transition.publish(selected));
                assertFalse(Files.exists(lease.root().resolve("objects-v1").resolve(id.hex())));
                transition.closeValidation();
                assertTrue(ownedStore.closed);
                assertThrows(SessionClosedException.class, () -> session.validateObject(id, remote));
                ExecutorService contender = Executors.newSingleThreadExecutor();
                try {
                    assertTrue(contender.submit(() -> {
                        try (var unexpected = owner.acquire()) { return false; }
                        catch (IllegalStateException expected) { return true; }
                    }).get(5, TimeUnit.SECONDS));
                } finally { contender.shutdownNow(); }
                assertTrue(transition.publish(selected) instanceof ObjectWrite.Written);
                assertThrows(IllegalStateException.class, owner::acquire);
                observe(lease.root(), id); neutral(lease.root());
            }
        }
        try (var next = owner.acquire()) { assertNotNull(next.root()); }
    }
    @Test public void contradictionSymbolicFixtureCannotSelectOrPublish() throws Exception {
        // Symbolic descriptive values only, NOT an honest cryptographic collision.
        var first = new ObjectCandidateValidation.Valid(id, remote);
        byte[] other = remote.clone(); other[0] ^= 1;
        var second = new ObjectCandidateValidation.Valid(id, other);
        var siblings = List.of(new Candidate(good("A"), Kind.VALID, Unavailability.NONE, first),
                new Candidate(bytes(id, "B", ByteState.PRESENT, other), Kind.VALID, Unavailability.NONE, second));
        var contradiction = new Group(id, State.COMPLETE, siblings, Authenticated.INTEGRITY_CONTRADICTION, null);
        var selected = select(contradiction);
        assertEquals(Status.CONTRADICTION, selected.status); assertNull(selected.id);
        var spy = new PublicationSpy(new ObjectWrite.Written());
        assertThrows(IllegalArgumentException.class, () -> publishExact(spy, selected)); assertEquals(0, spy.calls);
        // Rechecking also rejects a forged ONE_REPRESENTATION label over contradictory siblings.
        assertEquals(Status.CONTRADICTION, select(new Group(id, State.COMPLETE, siblings,
                Authenticated.ONE_REPRESENTATION, first)).status);
        var owner = owner();
        try (var lease = owner.acquire()) {
            neutral(lease.root());
            assertFalse(Files.exists(lease.root().resolve("objects-v1").resolve(id.hex())));
        }
    }
    @Test public void observedFullSizeUnclassifiedSiblingDefersAndRetryLiveClassificationWorks() throws Exception {
        var owner = owner();
        try (var lease = owner.acquire()) {
            char[] credential = password();
            Group completed;
            try (var session = open(lease.root(), credential)) {
                completed = ImmutableCandidateClassifier.classify(scan(State.INCOMPLETE, good("A")), session).groups().get(0);
            } finally { Arrays.fill(credential, '\0'); }
            Candidate unavailable = ImmutableCandidateClassifier.classify(good("B"), null);
            var group = new Group(id, State.INCOMPLETE, List.of(completed.siblings().get(0), unavailable),
                    Authenticated.ONE_REPRESENTATION, completed.representation());
            var selected = select(group); assertEquals(Status.DEFERRED_VALIDATION, selected.status);
            var spy = new PublicationSpy(new ObjectWrite.Written());
            assertThrows(IllegalArgumentException.class, () -> publishExact(spy, selected)); assertEquals(0, spy.calls);
            assertFalse(Files.exists(lease.root().resolve("objects-v1").resolve(id.hex())));
            assertEquals(Status.IMPORTED, run(lease, scan(State.INCOMPLETE, good("A"), good("B")), id).status());
        }
    }
    @Test public void noValidAndEmptyScanNeverAlterCanonicalState() throws Exception {
        var owner = owner();
        try (var lease = owner.acquire()) {
            for (Scan scan : List.of(scan(State.INCOMPLETE), scan(State.COMPLETE,
                    bytes(id, "bad", ByteState.PRESENT, new byte[1024])))) {
                assertEquals(Status.NOTHING_TO_IMPORT, run(lease, scan, id).status()); neutral(lease.root());
                assertFalse(Files.exists(lease.root().resolve("objects-v1").resolve(id.hex())));
            }
        }
    }
    @Test public void missingParentIsAuthenticatedAndPublishedBeforeGraphResolves() throws Exception {
        var owner = owner();
        try (var lease = owner.acquire()) {
            var result = run(lease, scan(State.COMPLETE, bytes(childId, "child", ByteState.PRESENT, child)), childId);
            assertEquals(Status.IMPORTED, result.status()); assertEquals(Kind.VALID, result.group().siblings().get(0).kind());
            assertArrayEquals(child, read(lease.root(), childId));
            char[] credential = password();
            try (var session = open(lease.root(), credential)) {
                awaitObservation(session);
                assertEquals(1, session.state().token(tokenId).orElseThrow().unresolvedReferences().size());
                assertTrue(session.state().token(tokenId).orElseThrow().heads().stream()
                        .anyMatch(head -> head.revision().equals(childId))); // Valid head with unresolved ancestry.
            } finally { Arrays.fill(credential, '\0'); }
            assertEquals(Status.IMPORTED, run(lease, scan(State.COMPLETE, good("parent")), id).status());
            observe(lease.root(), childId); neutral(lease.root());
        }
    }
    @Test public void everySpiOutcomeMapsWithoutRetryOrLosingReason() {
        var valid = new ObjectCandidateValidation.Valid(id, remote);
        var selected = select(new Group(id, State.COMPLETE,
                List.of(new Candidate(good("A"), Kind.VALID, Unavailability.NONE, valid)), Authenticated.ONE_REPRESENTATION, valid));
        Map<ObjectWrite, Status> outcomes = new LinkedHashMap<>();
        outcomes.put(new ObjectWrite.Written(), Status.IMPORTED);
        outcomes.put(new ObjectWrite.AlreadyPresentExact(), Status.ALREADY_PRESENT);
        outcomes.put(new ObjectWrite.ExistingDifferent(), Status.BLOCKED_EXISTING_DIFFERENT);
        for (StoreFailure reason : StoreFailure.values()) {
            outcomes.put(new ObjectWrite.Failed(reason), Status.STORAGE_FAILED);
            outcomes.put(new ObjectWrite.Uncertain(reason), Status.IMPORT_UNCERTAIN);
        }
        outcomes.forEach((write, status) -> {
            var spy = new PublicationSpy(write); assertSame(write, publishExact(spy, selected));
            assertEquals(status, map(write)); assertEquals(1, spy.calls);
            assertArrayEquals(remote, spy.bytes); assertEquals(id.hex(), spy.name.value());
        });
    }
    @Test public void structuralMismatchesAreIntegrationErrors() {
        var valid = new ObjectCandidateValidation.Valid(id, remote);
        var mismatch = new Candidate(bytes(id, "bad", ByteState.PRESENT, new byte[1024]), Kind.VALID, Unavailability.NONE, valid);
        assertThrows(IllegalArgumentException.class, () -> select(new Group(id, State.COMPLETE,
                List.of(mismatch), Authenticated.ONE_REPRESENTATION, valid)));
        assertThrows(IllegalArgumentException.class, () -> select(new Group(unrelatedId, State.COMPLETE,
                List.of(new Candidate(good("A"), Kind.VALID, Unavailability.NONE, valid)), Authenticated.ONE_REPRESENTATION, valid)));
    }
    @Test public void reconciliationSourceUsesOnlyLocalOpaquePublication() throws Exception {
        try (var paths = Files.walk(Path.of("src/main/java/org/totipo/android/reconcile"))) {
            for (Path p : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String code = Files.readString(p);
                for (String forbidden : List.of("createDocument", "deleteDocument", "renameDocument", "openOutputStream",
                        "ContentResolver", "Files.", "org.totipo.internal", "org.totipo.format")) {
                    assertFalse(p + " contains " + forbidden, code.contains(forbidden));
                }
            }
        }
    }
    private static final class TrackingStore implements TotipoStore {
        final TotipoStore delegate; boolean closed;
        TrackingStore(TotipoStore delegate) { this.delegate = delegate; }
        public BoundedRead readVault(int size) { return delegate.readVault(size); }
        public ObjectScan scanObjects() { return delegate.scanObjects(); }
        public BoundedRead readObject(ObjectName name, int size) { return delegate.readObject(name, size); }
        public ObjectWrite publishObject(ObjectName name, byte[] bytes) { return delegate.publishObject(name, bytes); }
        public VaultPrepare prepareVault(byte[] bytes) { return delegate.prepareVault(bytes); }
        public void close() { delegate.close(); closed = true; }
    }
    private static final class PublicationSpy implements TotipoStore {
        final ObjectWrite outcome; int calls; ObjectName name; byte[] bytes;
        PublicationSpy(ObjectWrite outcome) { this.outcome = outcome; }
        public ObjectWrite publishObject(ObjectName name, byte[] bytes) {
            calls++; this.name = name; this.bytes = bytes.clone(); Arrays.fill(bytes, (byte) 0); return outcome;
        }
        public BoundedRead readVault(int size) { throw new AssertionError(); }
        public ObjectScan scanObjects() { throw new AssertionError(); }
        public BoundedRead readObject(ObjectName name, int size) { throw new AssertionError(); }
        public VaultPrepare prepareVault(byte[] bytes) { throw new AssertionError(); }
        public void close() { throw new AssertionError(); }
    }
    private static void finished(VaultSession session) throws Exception {
        awaitObservation(session); assertTrue(session.state().diagnostics().isEmpty());
    }
    private static void awaitObservation(VaultSession session) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        session.states().subscribe(new Flow.Subscriber<>() {
            public void onSubscribe(Flow.Subscription s) { subscription.set(s); s.request(Long.MAX_VALUE); }
            public void onNext(VaultState state) { if (state.observation() instanceof ObservationProgress.Finished) latch.countDown(); }
            public void onError(Throwable t) { error.set(t); latch.countDown(); }
            public void onComplete() { latch.countDown(); }
        });
        try {
            assertTrue(latch.await(30, TimeUnit.SECONDS)); assertNull(error.get());
            assertTrue(session.state().observation() instanceof ObservationProgress.Finished);
        } finally { if (subscription.get() != null) subscription.get().cancel(); }
    }
}
