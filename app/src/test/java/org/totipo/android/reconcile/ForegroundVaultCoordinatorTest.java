package org.totipo.android.reconcile;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.totipo.*;
import org.totipo.android.*;
import org.totipo.android.provider.*;
import org.totipo.android.provider.ImmutableCandidateClassifier.*;
import org.totipo.android.provider.ProviderSnapshot.*;
import org.totipo.android.provider.ProviderSnapshot.State;
import org.totipo.spi.*;
import org.totipo.storage.nio.NioTotipoStore;
import static org.junit.Assert.*;
import static org.totipo.android.reconcile.ForegroundVaultCoordinator.*;
import static org.totipo.android.reconcile.ImmutableCandidateImporter.Status;

public final class ForegroundVaultCoordinatorTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static Path fixture;
    private static byte[] canonicalVault;
    private static RevisionId childId, parentId;
    private static byte[] childBytes, parentBytes;
    private static TokenId ancestryToken;
    private static final Map<RevisionId, byte[]> objects = new TreeMap<>(Comparator.comparing(RevisionId::hex));
    private static final Tree TREE = new Tree("fixture", "content://fixture/root", "root");
    private static char[] credential() { return "M1H disposable fixture".toCharArray(); }
    private static final Cancellation CONTINUE = () -> false;

    @BeforeClass public static void realCoreFixture() throws Exception {
        objects.clear();
        fixture = Files.createTempDirectory("m1h-fixture-");
        char[] password = credential();
        try (var session = ((CreateVaultResult.Created) Totipo.create(org.totipo.storage.nio.NioStoreComposition.coordinatedDelegate(fixture, new org.totipo.storage.nio.NioDurability()), password)).session()) {
            new Operations().observe(session);
            for (int i = 0; i < 3; i++) {
                try (var secret = NewSecret.copyOf(new byte[]{1,2,3,4}); var create = session.state().createToken()) {
                    RevisionId id = ((SaveResult.Saved) create.issuer("fixture " + i).secret(secret).save()).revisions().get(0);
                    objects.put(id, null);
                }
            }
            try (var secret = NewSecret.copyOf(new byte[]{4,3,2,1}); var create = session.state().createToken()) {
                var saved = (SaveResult.Saved)create.issuer("parent").secret(secret).save();
                parentId = saved.revisions().get(0); ancestryToken = saved.tokenId();
            }
            awaitContent(session, List.of(parentId));
            try (var update = session.state().update(session.state().token(ancestryToken).orElseThrow().heads().get(0))) {
                childId = ((SaveResult.Saved)update.issuer("child").save()).revisions().get(0);
            }
        } finally { Arrays.fill(password, '\0'); }
        canonicalVault = Files.readAllBytes(fixture.resolve("vault"));
        try (var store = org.totipo.storage.nio.NioStoreComposition.coordinatedDelegate(fixture, new org.totipo.storage.nio.NioDurability())) {
            parentBytes = ((BoundedRead.Present)store.readObject(new ObjectName(parentId.hex()), 1024)).bytes();
            childBytes = ((BoundedRead.Present)store.readObject(new ObjectName(childId.hex()), 1024)).bytes();
            for (RevisionId id : objects.keySet()) objects.put(id, ((BoundedRead.Present)
                    store.readObject(new ObjectName(id.hex()), 1024)).bytes());
        }
    }
    @AfterClass public static void removeFixture() throws Exception {
        if (fixture != null) try (var paths = Files.walk(fixture)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
    private LocalReplicaOwner owner() throws Exception {
        var owner = TestReplicaOwners.create(temporary.newFolder().toPath());
        try (var lease = owner.acquire()) { Files.write(lease.root().resolve("vault"), canonicalVault); }
        return owner;
    }
    private static List<RevisionId> ids() { return List.copyOf(objects.keySet()); }
    private static Bytes bytes(RevisionId id, String name, ByteState state, byte[] bytes) {
        return new Bytes("epoch", new Document(TREE, "content://fixture/" + name, name, "objects",
                id.hex(), "application/octet-stream", null, null), 1024, state, bytes, Issue.NONE);
    }
    private static Bytes good(int index) { return bytes(ids().get(index), "remote" + index, ByteState.PRESENT, objects.get(ids().get(index))); }
    private static Scan scan(State coverage, Bytes... rows) {
        var directory = new Document(TREE, "content://fixture/objects", "objects", "root", "objects-v1",
                "vnd.android.document/directory", null, 8L);
        return withVault(new Scan("epoch", TREE, new Listing("epoch", "root", List.of(directory), State.COMPLETE, List.of()),
                List.of(new Directory(directory, new Listing("epoch", "objects", Arrays.stream(rows).map(Bytes::document).toList(),
                        coverage, List.of()), Arrays.asList(rows))), coverage, List.of()), canonicalVault);
    }
    public static Scan withVault(Scan scan, byte[] representation) {
        var doc = new Document(scan.tree(), "content://fixture/vault", "vault-document", scan.tree().rootId(),
                "vault", "application/octet-stream", null, null);
        var rows = new ArrayList<>(scan.root().rows()); rows.add(doc);
        var candidates = new ArrayList<>(scan.vaultCandidates());
        candidates.add(new Bytes(scan.epoch(), doc, 87, ByteState.PRESENT, representation, Issue.NONE));
        return new Scan(scan.epoch(), scan.tree(), new Listing(scan.epoch(), scan.tree().rootId(), rows,
                scan.root().state(), scan.root().issues()), scan.directories(), scan.state(), scan.issues(),
                candidates);
    }
    public static byte[] productFixtureVault() { return canonicalVault.clone(); }
    public static Scan productFixtureScan() { return all(); }
    private static Scan all() { return scan(State.COMPLETE, good(2), good(0), good(1)); }
    private static ForegroundVaultCoordinator opened(LocalReplicaOwner owner, Operations operations) throws Exception {
        char[] password = credential();
        Opening opening = ForegroundVaultCoordinator.open(owner, password, operations);
        assertNull(opening.failure()); assertNull(opening.cause());
        assertArrayEquals(new char[password.length], password);
        assertEquals(ForegroundVaultCoordinator.State.OPEN, opening.vault().lifecycle());
        return opening.vault();
    }
    @Test public void sharedIdentityGateBlocksBothDirectionsBeforeObjectPublication() throws Exception {
        var ops = new Tracked(); var owner = owner();
        try (var vault = opened(owner, ops)) {
            Scan matching = all();
            assertEquals(org.totipo.android.provider.ProviderVaultIdentity.Status.MATCH,
                    org.totipo.android.provider.ProviderVaultIdentity.classify(matching, ops.first.vaultId()));
            var invalid = new ArrayList<Scan>();
            var rootWithoutVault = new Listing(matching.epoch(), TREE.rootId(), matching.root().rows().stream()
                    .filter(d -> !"vault".equals(d.displayName())).toList(), State.COMPLETE, List.of());
            invalid.add(new Scan(matching.epoch(), TREE, rootWithoutVault, matching.directories(), State.COMPLETE, List.of()));
            for (int size : new int[]{0, 86, 88}) invalid.add(withVault(invalid.get(0), new byte[size]));
            byte[] malformed = canonicalVault.clone(); malformed[0] ^= 1; invalid.add(withVault(invalid.get(0), malformed));
            malformed = canonicalVault.clone(); malformed[10] = 2; invalid.add(withVault(invalid.get(0), malformed));
            byte[] different = canonicalVault.clone(); different[86] ^= 1; invalid.add(withVault(invalid.get(0), different));
            assertFalse(Totipo.vaultId(different).equals(ops.first.vaultId()));
            Bytes original = matching.vaultCandidates().get(0);
            invalid.add(new Scan(matching.epoch(), TREE, matching.root(), matching.directories(), State.COMPLETE, List.of(),
                    List.of(new Bytes(matching.epoch(), original.document(), 87, ByteState.UNAVAILABLE, new byte[0], Issue.EXCEPTION))));
            invalid.add(withVault(matching, canonicalVault)); // identical duplicates: conservatively blocked
            invalid.add(withVault(matching, different));
            var directory = new Document(TREE, original.document().locator(), original.document().id(), TREE.rootId(),
                    "vault", "vnd.android.document/directory", null, null);
            invalid.add(new Scan(matching.epoch(), TREE, new Listing(matching.epoch(), TREE.rootId(), List.of(directory), State.COMPLETE, List.of()),
                    matching.directories(), State.COMPLETE, List.of(), List.of(new Bytes(matching.epoch(), directory, 87, ByteState.PRESENT, canonicalVault, Issue.NONE))));
            for (State coverage : List.of(State.INCOMPLETE_LOADING, State.INCOMPLETE, State.UNAVAILABLE))
                invalid.add(new Scan(matching.epoch(), TREE, new Listing(matching.epoch(), TREE.rootId(), matching.root().rows(), coverage, List.of()),
                        matching.directories(), coverage, List.of(), matching.vaultCandidates()));
            for (Scan scan : invalid) {
                assertThrows(org.totipo.android.provider.ProviderVaultIdentity.Blocked.class, () -> vault.sync(scan));
                assertThrows(org.totipo.android.provider.ProviderVaultIdentity.Blocked.class, () -> vault.outboundPlan(List.of(), scan, true));
                assertEquals(0, ops.publications); assertEquals(0, ops.refreshes); assertEquals(1, ops.opens);
                assertFalse(ops.domain.exclusiveHeldByCurrentThread()); assertSame(ops.first, ops.last);
                assertEquals(ForegroundVaultCoordinator.State.OPEN, vault.lifecycle());
            }
            assertArrayEquals(canonicalVault, Files.readAllBytes(ownerRoot(owner, vault)));
            assertEquals(3, vault.sync(matching).count(Status.IMPORTED));
            assertNull(vault.outboundPlan(vault.outboundSnapshot(), matching, true).limitation());
        }
    }
    private static Path ownerRoot(LocalReplicaOwner owner, ForegroundVaultCoordinator vault) throws Exception {
        var lease = (LocalReplicaOwner.Lease)fieldOf(vault, "lease"); return lease.root().resolve("vault");
    }
    @Test public void outboundSnapshotExactValidationOutsideGateSameDomainAndNoRefresh() throws Exception {
        var operations = new Tracked();
        try (var vault = opened(owner(), operations)) {
            run(vault, all());
            int refreshes = operations.refreshes;
            var field = ForegroundVaultCoordinator.class.getDeclaredField("session"); field.setAccessible(true);
            var live = (VaultSession)field.get(vault); var validations = new AtomicInteger();
            VaultSession spy = (VaultSession)java.lang.reflect.Proxy.newProxyInstance(VaultSession.class.getClassLoader(),
                new Class<?>[]{VaultSession.class}, (proxy, method, args) -> {
                    if (method.getName().equals("vaultId")) assertFalse(operations.domain.exclusiveHeldByCurrentThread());
                    if (method.getName().equals("validateObject")) {
                        assertFalse(operations.domain.exclusiveHeldByCurrentThread()); validations.incrementAndGet();
                    }
                    try { return method.invoke(live, args); }
                    catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
                });
            field.set(vault, spy);
            try {
                vault.outboundPlan(List.of(), all(), true);
                validations.set(0);
                var detached = vault.outboundSnapshot(); assertEquals(3, detached.size()); assertEquals(3, validations.get());
                for (var object : detached) assertArrayEquals(objects.get(object.id()), object.representation());
                assertSame(operations.domain, fieldOf(vault, "store"));
                assertEquals(refreshes, operations.refreshes); assertEquals(1, operations.opens); assertEquals(0, operations.closes);
            } finally { field.set(vault, live); }
        }
    }
    private static Object fieldOf(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static Report run(ForegroundVaultCoordinator vault, Scan scan) {
        return vault.sync(scan, CONTINUE);
    }
    private static class Tracked extends Operations {
        int opens, closes, publications, observations;
        VaultSession first, last;
        List<RevisionId> order = new ArrayList<>();
        List<RevisionId> expected = new ArrayList<>();
        int refreshes; CoordinatedPrivateStore domain;
        final Set<CoordinatedPrivateStore.Bridge> scopes = Collections.newSetFromMap(new IdentityHashMap<>());
        OpenResult open(CoordinatedPrivateStore store, char[] password) {
            domain = store; opens++;
            OpenResult result = super.open(store, password);
            if (result instanceof OpenResult.Opened opened) {
                if (first == null) first = opened.session();
                last = opened.session();
            }
            return result;
        }
        void close(VaultSession session) { closes++; super.close(session); }
        ObjectWrite publish(CoordinatedPrivateStore.Bridge bridge, ImmutableCandidateImporter.Selection selection) {
            scopes.add(bridge); publications++; order.add(selection.id);
            var result = super.publish(bridge, selection);
            if (result instanceof ObjectWrite.Written || result instanceof ObjectWrite.AlreadyPresentExact) expected.add(selection.id);
            return result;
        }
        void observe(VaultSession session) throws Exception { observations++; super.observe(session); }
        void refresh(VaultSession session, CoordinatedPrivateStore store) {
            assertFalse(store.exclusiveHeldByCurrentThread());
            assertSame(first, session); assertEquals(0, closes); assertEquals(1, opens);
            refreshes++; super.refresh(session, store);
            awaitContent(session, expected);
        }
    }
    private static void awaitContent(VaultSession session, List<RevisionId> expected) {
        awaitState(session, state -> state.observation() instanceof ObservationProgress.Finished
                && state.tokens().stream().flatMap(t -> t.heads().stream()).map(TokenHead::revision).toList().containsAll(expected));
    }
    private static void awaitState(VaultSession session, java.util.function.Predicate<VaultState> predicate) {
        var done = new CountDownLatch(1);
        var subscription = new AtomicReference<Flow.Subscription>();
        var failure = new AtomicReference<Throwable>();
        session.states().subscribe(new Flow.Subscriber<>() {
            public void onSubscribe(Flow.Subscription next) { subscription.set(next); next.request(Long.MAX_VALUE); }
            public void onNext(VaultState state) { if (predicate.test(state)) done.countDown(); }
            public void onError(Throwable cause) { failure.set(cause); done.countDown(); }
            public void onComplete() { failure.set(new AssertionError("Session closed")); done.countDown(); }
        });
        try { assertTrue("Expected content observation", done.await(20, TimeUnit.SECONDS)); assertNull(failure.get()); }
        catch (InterruptedException failureValue) { throw new AssertionError(failureValue); }
        finally { subscription.get().cancel(); }
    }
    @Test public void matchingProviderVaultNeverCreatesOrReplacesLocalVault() throws Exception {
        var owner = owner();
        var ops = new Tracked();
        Scan immutable = all();
        Scan withVault = immutable;
        Path root;
        try (var lease = owner.acquire()) { root = lease.root(); }
        byte[] before = Files.readAllBytes(root.resolve("vault"));
        try (var vault = opened(owner, ops)) {
            var same = ops.first;
            assertEquals(3, vault.sync(withVault).count(Status.IMPORTED));
            assertArrayEquals(before, Files.readAllBytes(root.resolve("vault")));
            assertSame(same, ops.first); assertEquals(1, ops.opens); assertEquals(0, ops.closes);
        }
    }
    @Test public void noEligibleKeepsSameSessionWithoutPublication() throws Exception {
        var ops = new Tracked();
        try (var vault = opened(owner(), ops)) {
            Report result = run(vault, scan(State.COMPLETE, bytes(ids().get(0), "invalid", ByteState.PRESENT, new byte[1024])));
            assertEquals(Completion.RETAINED_SESSION, result.completion());
            assertEquals(1, ops.opens); assertEquals(0, ops.closes); assertEquals(0, ops.publications);
            assertSame(ops.first, ops.last); assertNotNull(vault.view());
            assertTrue(ops.first.validateObject(ids().get(0), objects.get(ids().get(0))) instanceof ObjectCandidateValidation.Valid);
        }
    }
    @Test public void singleRealScanSameSessionAndExactIdempotence() throws Exception {
        var owner = owner(); var ops = new Tracked();
        try (var vault = opened(owner, ops)) {
            Scan scan = scan(State.COMPLETE, good(0));
            Report result = run(vault, scan);
            assertSame(scan, result.evidence().transport());
            assertEquals(1, result.count(Status.IMPORTED)); assertEquals(Refresh.REQUESTED, result.refresh());
            assertSame(ops.first, ops.last); assertEquals(1, ops.observations);
            assertEquals(1, ops.opens); assertEquals(0, ops.closes);
            assertTrue(vault.view().observation() instanceof ObservationProgress.Finished);
            assertEquals(1, vault.view().tokens().size());
            assertEquals(1, run(vault, scan).count(Status.ALREADY_PRESENT));
            assertThrows(IllegalStateException.class, owner::acquire);
        }
        try (var lease = owner.acquire()) { assertNotNull(lease.root()); }
    }
    @Test public void multipleImportsSortedAndAllObserved() throws Exception {
        var ops = new Tracked();
        try (var vault = opened(owner(), ops)) {
            Report result = run(vault, all());
            assertEquals(3, result.count(Status.IMPORTED)); assertEquals(ids(), ops.order);
            assertEquals(1, ops.refreshes); assertEquals(1, ops.scopes.size());
            assertEquals(3, vault.view().tokens().size()); assertTrue(result.unattempted().isEmpty());
        }
    }
    @Test public void duplicatesPublishOnceAndRetainEverySibling() throws Exception {
        var ops = new Tracked();
        Bytes duplicate = bytes(ids().get(0), "copy", ByteState.PRESENT, objects.get(ids().get(0)));
        Scan scan = scan(State.COMPLETE, good(0), duplicate);
        try (var vault = opened(owner(), ops)) {
            var result = run(vault, scan);
            assertEquals(1, ops.publications); assertEquals(2, result.evidence().groups().get(0).siblings().size());
            assertSame(scan, result.evidence().transport()); assertArrayEquals(objects.get(ids().get(0)), duplicate.bytes());
        }
    }
    @Test public void incompleteAndMalformedTransportDoNotBlockPositiveImports() throws Exception {
        var ops = new Tracked();
        Scan scan = scan(State.INCOMPLETE_LOADING, good(0),
                bytes(ids().get(0), "invalid", ByteState.PRESENT, new byte[1024]),
                bytes(ids().get(0), "short", ByteState.SHORT, new byte[7]),
                bytes(ids().get(0), "overflow", ByteState.OVERSIZED, new byte[1025]),
                bytes(ids().get(0), "unavailable", ByteState.UNAVAILABLE, new byte[0]));
        try (var vault = opened(owner(), ops)) {
            var result = run(vault, scan); assertEquals(1, result.count(Status.IMPORTED));
            assertEquals(State.INCOMPLETE_LOADING, result.evidence().transport().state());
            assertEquals("epoch", result.evidence().transport().epoch());
            assertEquals(5, result.evidence().groups().get(0).siblings().size());
        }
    }
    @Test public void existingDifferentPreservedAndUnrelatedImportsContinue() throws Exception {
        var owner = owner(); byte[] obstruction = new byte[1024];
        try (var lease = owner.acquire(); var store = org.totipo.storage.nio.NioStoreComposition.coordinatedDelegate(lease.root(), new org.totipo.storage.nio.NioDurability())) {
            assertTrue(store.publishObject(new ObjectName(ids().get(0).hex()), obstruction) instanceof ObjectWrite.Written);
        }
        try (var vault = opened(owner, new Tracked())) {
            var result = run(vault, all()); assertEquals(1, result.count(Status.BLOCKED_EXISTING_DIFFERENT));
            assertEquals(2, result.count(Status.IMPORTED)); assertEquals(Refresh.REQUESTED, result.refresh());
        }
        try (var lease = owner.acquire(); var store = org.totipo.storage.nio.NioStoreComposition.coordinatedDelegate(lease.root(), new org.totipo.storage.nio.NioDurability())) {
            assertArrayEquals(obstruction, ((BoundedRead.Present)store.readObject(new ObjectName(ids().get(0).hex()),1024)).bytes());
        }
    }
    @Test public void everyUncertaintyReasonStopsWithoutReopen() throws Exception {
        for (StoreFailure reason : StoreFailure.values()) {
            var ops = new Tracked() {
                ObjectWrite publish(CoordinatedPrivateStore.Bridge bridge, ImmutableCandidateImporter.Selection selection) {
                    super.publish(bridge, selection); return new ObjectWrite.Uncertain(reason);
                }
            };
            var owner = owner();
            try (var vault = opened(owner, ops)) {
                var result = run(vault, all());
                assertEquals(1, result.count(Status.IMPORT_UNCERTAIN)); assertEquals(2, result.unattempted().size());
                assertEquals(reason, ((ObjectWrite.Uncertain) result.items().get(0).publication()).reason());
                assertEquals(Refresh.SKIPPED_UNSAFE, result.refresh()); assertEquals(1, ops.opens);
                assertEquals(0, ops.closes); assertEquals(0, ops.refreshes);
                assertTrue(ops.first.validateObject(ids().get(0), objects.get(ids().get(0))) instanceof ObjectCandidateValidation.Valid);
                assertEquals(ForegroundVaultCoordinator.State.STORAGE_UNSAFE, vault.lifecycle());
                assertThrows(IllegalStateException.class, vault::view); assertThrows(IllegalStateException.class, owner::acquire);
            }
            try (var recovered = opened(owner, new Tracked())) { assertEquals(1, recovered.view().tokens().size()); }
        }
    }
    @Test public void everyStorageFailureReasonStopsWithoutFabricatedSuccess() throws Exception {
        for (StoreFailure reason : StoreFailure.values()) {
            var ops = new Tracked() {
                ObjectWrite publish(CoordinatedPrivateStore.Bridge bridge, ImmutableCandidateImporter.Selection selection) {
                    publications++; return new ObjectWrite.Failed(reason);
                }
            };
            try (var vault = opened(owner(), ops)) {
                var result = run(vault, all()); assertEquals(1, result.count(Status.STORAGE_FAILED));
                assertEquals(reason, ((ObjectWrite.Failed)result.items().get(0).publication()).reason());
                assertEquals(2, result.unattempted().size()); assertEquals(1, ops.opens);
                assertEquals(0, result.count(Status.IMPORTED));
                assertEquals(0, ops.closes); assertEquals(0, ops.refreshes);
                assertEquals(ForegroundVaultCoordinator.State.STORAGE_UNSAFE, vault.lifecycle());
            }
        }
    }
    @Test public void concurrentReconciliationAndCloseRejected() throws Exception {
        var entered = new CountDownLatch(1); var finish = new CountDownLatch(1);
        var worker = Executors.newSingleThreadExecutor();
        try (var vault = opened(owner(), new Tracked())) {
            var first = worker.submit(() -> vault.sync(all(), () -> {
                entered.countDown();
                try { assertTrue(finish.await(10, TimeUnit.SECONDS)); }
                catch (InterruptedException failure) { throw new AssertionError(failure); }
                return false;
            }));
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS));
                assertThrows(IllegalStateException.class, () -> vault.sync(all(), CONTINUE));
                assertThrows(IllegalStateException.class, vault::close);
            } finally { finish.countDown(); }
            assertEquals(3, first.get(30, TimeUnit.SECONDS).count(Status.IMPORTED));
        } finally { worker.shutdownNow(); }
    }
    @Test public void cancellationBeforeAndAfterClassificationPreservesSession() throws Exception {
        for (int cancelAt : List.of(1,2)) {
            var calls = new AtomicInteger(); var ops = new Tracked();
            try (var vault = opened(owner(), ops)) {
                var result = vault.sync(all(), () -> calls.incrementAndGet() == cancelAt);
                assertEquals(Completion.CANCELLED, result.completion()); assertEquals(0, ops.closes);
                assertEquals(0, ops.publications); assertEquals(1, ops.opens); assertNotNull(vault.view());
            }
        }
    }
    @Test public void cancellationDuringPublicationFinishesObjectThenRefreshes() throws Exception {
        var cancelled = new AtomicBoolean();
        var ops = new Tracked() {
            ObjectWrite publish(CoordinatedPrivateStore.Bridge bridge, ImmutableCandidateImporter.Selection selection) {
                cancelled.set(true); return super.publish(bridge, selection);
            }
        };
        try (var vault = opened(owner(), ops)) {
            var result = vault.sync(all(), cancelled::get);
            assertEquals(Completion.CANCELLED, result.completion()); assertEquals(1, result.count(Status.IMPORTED));
            assertEquals(2, result.unattempted().size()); assertEquals(Refresh.REQUESTED, result.refresh());
            assertEquals(1, vault.view().tokens().size());
        }
    }
    @Test public void symbolicContradictionAndDeferredPlansExcludeOnlyAffectedIds() throws Exception {
        // Honest same-ID authenticated contradictions cannot be authored without breaking crypto.
        // Planning fixture uses real validated safe groups, symbolic evidence only for exclusions.
        var ops = new Tracked();
        try (var vault = opened(owner(), ops)) {
            var evidence = ImmutableCandidateClassifier.classify(all(), ops.first);
            Group base = evidence.groups().stream().filter(g -> g.objectId().equals(ids().get(0))).findFirst().orElseThrow();
            Group safe = evidence.groups().stream().filter(g -> g.objectId().equals(ids().get(1))).findFirst().orElseThrow();
            Group contradiction = new Group(base.objectId(), State.INCOMPLETE, base.siblings(), Authenticated.INTEGRITY_CONTRADICTION, null);
            Candidate unavailable = new Candidate(good(0), Kind.VALIDATION_UNAVAILABLE, Unavailability.NO_SESSION, null);
            Group deferred = new Group(base.objectId(), State.INCOMPLETE, List.of(base.siblings().get(0), unavailable),
                    Authenticated.ONE_REPRESENTATION, base.representation());
            for (Group excluded : List.of(contradiction, deferred)) {
                var symbolic = new ImmutableCandidateClassifier.Result(all(), List.of(excluded, safe));
                var importPlan = plan(symbolic);
                assertEquals(List.of(safe.objectId()), importPlan.objects.stream().map(s -> s.id).toList());
                assertEquals(symbolic.transport().epoch(), importPlan.epoch);
                assertEquals(symbolic.transport().state(), importPlan.coverage);
                assertEquals(1, importPlan.provenance.size());
                assertThrows(UnsupportedOperationException.class, () -> importPlan.objects.clear());
                var report = new Report(Completion.RETAINED_SESSION, symbolic, List.of(), List.of(), null, Refresh.NOT_REQUESTED, null, null);
                assertEquals(excluded == contradiction ? List.of(base.objectId()) : List.of(), report.contradictions());
                assertEquals(excluded == deferred ? List.of(base.objectId()) : List.of(), report.deferredValidation());
            }
            // Independent positively authenticated facts are publishable and observed normally.
            assertEquals(1, run(vault, scan(State.COMPLETE, good(1))).count(Status.IMPORTED));
            assertEquals(1, vault.view().tokens().size());
        }
    }
    @Test public void unsupportedValidationDefersOnlyAffectedId() throws Exception {
        var ops = new Tracked() {
            OpenResult open(CoordinatedPrivateStore store, char[] password) {
                var real = (OpenResult.Opened) super.open(store, password);
                var calls = new AtomicInteger();
                VaultSession unsupported = (VaultSession) java.lang.reflect.Proxy.newProxyInstance(
                        VaultSession.class.getClassLoader(), new Class<?>[]{VaultSession.class}, (proxy, method, args) -> {
                            if (method.getName().equals("validateObject") && calls.incrementAndGet() == 2)
                                throw new UnsupportedOperationException("fault");
                            try { return method.invoke(real.session(), args); }
                            catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
                        });
                first = unsupported; last = unsupported;
                return new OpenResult.Opened(unsupported);
            }
        };
        try (var vault = opened(owner(), ops)) {
            var result = run(vault, scan(State.COMPLETE, good(0), good(0), good(1)));
            assertEquals(Completion.REFRESH_REQUESTED, result.completion());
            assertEquals(List.of(ids().get(0)), result.deferredValidation());
            assertEquals(1, ops.publications); assertEquals(1, ops.opens);
            assertEquals(2, result.evidence().groups().size());
            assertEquals(ForegroundVaultCoordinator.State.OPEN, vault.lifecycle());
        }
    }
    @Test public void publicationExceptionRetainsFailedOwnershipAndActualPriorOutcomes() throws Exception {
        var ops = new Tracked() {
            ObjectWrite publish(CoordinatedPrivateStore.Bridge bridge, ImmutableCandidateImporter.Selection selection) {
                if (publications == 1) throw new IllegalStateException("injected publication failure");
                return super.publish(bridge, selection);
            }
        };
        try (var vault = opened(owner(), ops)) {
            var result = run(vault, all()); assertEquals(1, result.count(Status.IMPORTED));
            assertEquals(ids().get(1), result.publicationFailureId()); assertEquals(List.of(ids().get(2)), result.unattempted());
            assertEquals(Refresh.SKIPPED_UNSAFE, result.refresh()); assertNotNull(result.failure());
        }
    }

    @Test public void credentialsNeverBecomePersistentFieldsOrProductStorage() throws Exception {
        for (var field : ForegroundVaultCoordinator.class.getDeclaredFields()) {
            assertNotEquals(char[].class, field.getType()); assertNotEquals(String.class, field.getType());
        }
        String source = Files.readString(Path.of("src/main/java/org/totipo/android/reconcile/ForegroundVaultCoordinator.java"));
        for (String forbidden : List.of("Bundle", "SharedPreferences", "getRootKey", "Executor", "WorkManager", "Thread.sleep",
                "moveDocument", "removeDocument", "createDocument", "deleteDocument", "renameDocument", "openOutputStream",
                "ContentResolver", "org.totipo.internal", "org.totipo.format")) {
            assertFalse(forbidden, source.contains(forbidden));
        }
    }
    @Test public void missingParentResolvesOnLaterSameSessionSync() throws Exception {
        // Content wait uses child as the head even after the parent arrives.
        var ops = new Tracked() {
            void refresh(VaultSession session, CoordinatedPrivateStore store) {
                assertFalse(store.exclusiveHeldByCurrentThread()); refreshes++;
                session.requestRefresh(); awaitContent(session, List.of(childId));
            }
        };
        try (var vault = opened(owner(), ops)) {
            var child = bytes(childId, "child", ByteState.PRESENT, childBytes);
            assertEquals(1, vault.sync(scan(State.COMPLETE, child)).count(Status.IMPORTED));
            assertEquals(1, ops.first.state().token(ancestryToken).orElseThrow().unresolvedReferences().size());
            var parent = bytes(parentId, "parent", ByteState.PRESENT, parentBytes);
            assertEquals(1, vault.sync(scan(State.COMPLETE, parent)).count(Status.IMPORTED));
            awaitState(ops.first, state -> state.token(ancestryToken).map(t -> t.unresolvedReferences().isEmpty()).orElse(false));
            assertSame(ops.first, ops.last); assertEquals(1, ops.opens); assertEquals(0, ops.closes); assertEquals(2, ops.refreshes);
        }
    }
    @Test public void bridgeHeldThenRealJavaSaveQueuesAndRefreshNeverInvertsLocks() throws Exception {
        var bridgeEntered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var error = new AtomicReference<Throwable>(); var save = new AtomicReference<SaveResult>();
        var ops = new Tracked() {
            void refresh(VaultSession session, CoordinatedPrivateStore store) {
                // Save owns Java's internal gate and is waiting for Android's gate.
                // Explicit evidence of release precedes EVERY Java callback below.
                assertFalse(store.exclusiveHeldByCurrentThread());
                assertTrue(session.validateObject(ids().get(0), objects.get(ids().get(0))) instanceof ObjectCandidateValidation.Valid);
                super.refresh(session, store);
            }
            ObjectWrite publish(CoordinatedPrivateStore.Bridge bridge, ImmutableCandidateImporter.Selection selected) {
                bridgeEntered.countDown(); CoordinatedPrivateStoreTest.await(release);
                return super.publish(bridge, selected);
            }
        };
        try (var vault = opened(owner(), ops);
             var secret = NewSecret.copyOf(new byte[]{9,8,7,6}); var create = ops.first.state().createToken()) {
            create.issuer("concurrent Java save").secret(secret);
            var result = new AtomicReference<Report>();
            Thread sync = CoordinatedPrivateStoreTest.thread(error, () -> result.set(vault.sync(scan(State.COMPLETE, good(0)))));
            CoordinatedPrivateStoreTest.await(bridgeEntered);
            Thread javaSave = CoordinatedPrivateStoreTest.thread(error, () -> save.set(create.save()));
            try {
                CoordinatedPrivateStoreTest.queued(ops.domain, javaSave);
                assertNull(save.get()); // Java owns its gate but has not entered the NIO delegate.
            } finally { release.countDown(); }
            sync.join(20000); javaSave.join(20000);
            assertFalse(sync.isAlive()); assertFalse(javaSave.isAlive()); assertNull(error.get());
            assertTrue(save.get() instanceof SaveResult.Saved); assertEquals(1, result.get().count(Status.IMPORTED));
            awaitContent(ops.first, List.of(ids().get(0), ((SaveResult.Saved)save.get()).revisions().get(0)));
            assertEquals(2, vault.view().tokens().size()); assertEquals(1, ops.opens); assertEquals(0, ops.closes);
        }
    }
    @Test public void runningRealSaveFinishesBeforeBridgePublication() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var syncStarted = new CountDownLatch(1);
        var error = new AtomicReference<Throwable>(); var activePublication = new AtomicBoolean();
        var probe = new AtomicReference<CoordinatedNioProbe>();
        var ops = new Tracked() {
            CoordinatedPrivateStore storage(LocalReplicaOwner.Lease lease) throws java.io.IOException {
                var next = new CoordinatedNioProbe(org.totipo.storage.nio.NioStoreComposition.coordinatedDelegate(lease.root(), new org.totipo.storage.nio.NioDurability())); probe.set(next);
                return new CoordinatedPrivateStore(next);
            }
            ObjectWrite publish(CoordinatedPrivateStore.Bridge bridge, ImmutableCandidateImporter.Selection selected) {
                assertFalse(activePublication.get()); return super.publish(bridge, selected);
            }
        };
        try (var vault = opened(owner(), ops); var secret = NewSecret.copyOf(new byte[]{5,6,7,8});
             var create = ops.first.state().createToken()) {
            create.secret(secret); var saved = new AtomicReference<SaveResult>();
            probe.get().hook = name -> {
                if (name.equals("publishObject") && activePublication.compareAndSet(false, true)) {
                    entered.countDown(); CoordinatedPrivateStoreTest.await(release); activePublication.set(false);
                }
            };
            Thread javaSave = CoordinatedPrivateStoreTest.thread(error, () -> saved.set(create.save()));
            CoordinatedPrivateStoreTest.await(entered);
            var result = new AtomicReference<Report>();
            Thread sync = CoordinatedPrivateStoreTest.thread(error, () -> {
                syncStarted.countDown(); result.set(vault.sync(scan(State.COMPLETE, good(0))));
            });
            try {
                CoordinatedPrivateStoreTest.await(syncStarted);
                assertEquals(0, ops.publications); assertEquals(1, probe.get().active.get());
            } finally { release.countDown(); }
            javaSave.join(20000); sync.join(20000); assertNull(error.get());
            assertFalse(sync.isAlive()); assertFalse(javaSave.isAlive());
            assertTrue(saved.get() instanceof SaveResult.Saved); assertEquals(1, result.get().count(Status.IMPORTED));
        }
    }

    @Test public void explicitCloseFailureRetainsLeaseUntilRetryWithoutClosingDuringSync() throws Exception {
        var fail = new AtomicBoolean(true); var owner = owner();
        var ops = new Tracked() {
            void close(VaultSession session) { if (fail.get()) throw new IllegalStateException("close fault"); super.close(session); }
        };
        var vault = opened(owner, ops);
        assertEquals(3, vault.sync(all()).count(Status.IMPORTED)); assertEquals(0, ops.closes);
        try {
            assertThrows(IllegalStateException.class, vault::close);
            assertThrows(IllegalStateException.class, owner::acquire);
        } finally { fail.set(false); vault.close(); }
        try (var lease = owner.acquire()) { assertNotNull(lease.root()); }
    }
    @Test public void refreshReportDoesNotMistakeReplayedFinishedForImportCompletion() throws Exception {
        var observationEntered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var probe = new AtomicReference<CoordinatedNioProbe>();
        var ops = new Tracked() {
            CoordinatedPrivateStore storage(LocalReplicaOwner.Lease lease) throws java.io.IOException {
                var next = new CoordinatedNioProbe(org.totipo.storage.nio.NioStoreComposition.coordinatedDelegate(lease.root(), new org.totipo.storage.nio.NioDurability())); probe.set(next);
                return new CoordinatedPrivateStore(next);
            }
            void refresh(VaultSession session, CoordinatedPrivateStore store) {
                assertFalse(store.exclusiveHeldByCurrentThread()); refreshes++;
                session.requestRefresh(); // Deliberately no content wait in this test.
            }
        };
        try (var vault = opened(owner(), ops)) {
            VaultState before = ops.first.state();
            assertTrue(before.observation() instanceof ObservationProgress.Finished);
            probe.get().hook = name -> {
                if (name.equals("scanObjects")) { observationEntered.countDown(); CoordinatedPrivateStoreTest.await(release); }
            };
            try {
                var report = vault.sync(scan(State.COMPLETE, good(0)));
                CoordinatedPrivateStoreTest.await(observationEntered);
                assertEquals(Refresh.REQUESTED, report.refresh());
                assertEquals(Completion.REFRESH_REQUESTED, report.completion());
                assertSame(before.observation(), report.latestObservation());
                assertSame(before, ops.first.state()); assertTrue(vault.view().tokens().isEmpty());
            } finally { release.countDown(); }
            awaitContent(ops.first, List.of(ids().get(0)));
            assertNotSame(before, ops.first.state()); assertEquals(1, vault.view().tokens().size());
        }
    }

}
