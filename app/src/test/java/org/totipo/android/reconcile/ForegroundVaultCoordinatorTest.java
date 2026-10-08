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
    private static byte[] wrapper;
    private static final Map<RevisionId, byte[]> objects = new TreeMap<>(Comparator.comparing(RevisionId::hex));
    private static final Tree TREE = new Tree("fixture", "content://fixture/root", "root");
    private static char[] credential() { return "M1H disposable fixture".toCharArray(); }
    private static final Cancellation CONTINUE = () -> false;

    @BeforeClass public static void realCoreFixture() throws Exception {
        fixture = Files.createTempDirectory("m1h-fixture-");
        char[] password = credential();
        try (var session = ((CreateVaultResult.Created) Totipo.create(NioTotipoStore.openPrivate(fixture), password)).session()) {
            new Operations().observe(session);
            for (int i = 0; i < 3; i++) {
                try (var secret = NewSecret.copyOf(new byte[]{1,2,3,4}); var create = session.state().createToken()) {
                    RevisionId id = ((SaveResult.Saved) create.issuer("fixture " + i).secret(secret).save()).revisions().get(0);
                    objects.put(id, null);
                }
            }
        } finally { Arrays.fill(password, '\0'); }
        wrapper = Files.readAllBytes(fixture.resolve("vault"));
        try (var store = NioTotipoStore.openPrivate(fixture)) {
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
        try (var lease = owner.acquire()) { Files.write(lease.root().resolve("vault"), wrapper); }
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
                "vnd.android.document/directory", null, null);
        return new Scan("epoch", TREE, new Listing("epoch", "root", List.of(directory), coverage, List.of()),
                List.of(new Directory(directory, new Listing("epoch", "objects", Arrays.stream(rows).map(Bytes::document).toList(),
                        coverage, List.of()), Arrays.asList(rows))), coverage, List.of());
    }
    private static Scan all() { return scan(State.COMPLETE, good(2), good(0), good(1)); }
    private static ForegroundVaultCoordinator opened(LocalReplicaOwner owner, Operations operations) throws Exception {
        char[] password = credential();
        Opening opening = ForegroundVaultCoordinator.open(owner, password, operations);
        assertNull(opening.failure()); assertNull(opening.cause());
        assertArrayEquals(new char[password.length], password);
        assertEquals(ForegroundVaultCoordinator.State.OPEN, opening.vault().lifecycle());
        return opening.vault();
    }
    private static Report run(ForegroundVaultCoordinator vault, Scan scan) {
        char[] password = credential();
        Report report = vault.reconcile(scan, password, CONTINUE);
        assertArrayEquals(new char[password.length], password);
        return report;
    }
    private static class Tracked extends Operations {
        int opens, closes, publications, observations;
        VaultSession first, last;
        List<RevisionId> order = new ArrayList<>();
        OpenResult open(LocalReplicaOwner.Lease lease, char[] password) throws java.io.IOException {
            opens++;
            OpenResult result = super.open(lease, password);
            if (result instanceof OpenResult.Opened opened) {
                if (first == null) first = opened.session();
                last = opened.session();
            }
            return result;
        }
        void close(VaultSession session) { closes++; super.close(session); }
        ObjectWrite publish(LocalReplicaOwner.Lease lease, ImmutableCandidateImporter.Selection selection) throws java.io.IOException {
            publications++; order.add(selection.id);
            assertThrows(SessionClosedException.class, () -> first.validateObject(selection.id, objects.get(selection.id)));
            return super.publish(lease, selection);
        }
        void observe(VaultSession session) throws Exception { observations++; super.observe(session); }
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
    @Test public void singleRealScanValidationPublicationReopenAndExactIdempotence() throws Exception {
        var owner = owner(); var ops = new Tracked();
        try (var vault = opened(owner, ops)) {
            Scan scan = scan(State.COMPLETE, good(0));
            Report result = run(vault, scan);
            assertSame(scan, result.evidence().transport());
            assertEquals(1, result.count(Status.IMPORTED)); assertEquals(Reopen.RESTORED, result.reopen());
            assertNotSame(ops.first, ops.last); assertEquals(2, ops.observations);
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
        try (var lease = owner.acquire(); var store = NioTotipoStore.openPrivate(lease.root())) {
            assertTrue(store.publishObject(new ObjectName(ids().get(0).hex()), obstruction) instanceof ObjectWrite.Written);
        }
        try (var vault = opened(owner, new Tracked())) {
            var result = run(vault, all()); assertEquals(1, result.count(Status.BLOCKED_EXISTING_DIFFERENT));
            assertEquals(2, result.count(Status.IMPORTED)); assertEquals(Reopen.RESTORED, result.reopen());
        }
        try (var lease = owner.acquire(); var store = NioTotipoStore.openPrivate(lease.root())) {
            assertArrayEquals(obstruction, ((BoundedRead.Present)store.readObject(new ObjectName(ids().get(0).hex()),1024)).bytes());
        }
    }
    @Test public void everyUncertaintyReasonStopsWithoutReopen() throws Exception {
        for (StoreFailure reason : StoreFailure.values()) {
            var ops = new Tracked() {
                ObjectWrite publish(LocalReplicaOwner.Lease lease, ImmutableCandidateImporter.Selection selection) throws java.io.IOException {
                    super.publish(lease, selection); return new ObjectWrite.Uncertain(reason);
                }
            };
            var owner = owner();
            try (var vault = opened(owner, ops)) {
                var result = run(vault, all());
                assertEquals(1, result.count(Status.IMPORT_UNCERTAIN)); assertEquals(2, result.unattempted().size());
                assertEquals(reason, ((ObjectWrite.Uncertain) result.items().get(0).publication()).reason());
                assertEquals(Reopen.SKIPPED_UNSAFE, result.reopen()); assertEquals(1, ops.opens);
                assertEquals(ForegroundVaultCoordinator.State.FAILED_CLOSED, vault.lifecycle());
                assertThrows(IllegalStateException.class, vault::view); assertThrows(IllegalStateException.class, owner::acquire);
            }
            try (var recovered = opened(owner, new Tracked())) { assertEquals(1, recovered.view().tokens().size()); }
        }
    }
    @Test public void everyStorageFailureReasonStopsWithoutFabricatedSuccess() throws Exception {
        for (StoreFailure reason : StoreFailure.values()) {
            var ops = new Tracked() {
                ObjectWrite publish(LocalReplicaOwner.Lease lease, ImmutableCandidateImporter.Selection selection) {
                    publications++; return new ObjectWrite.Failed(reason);
                }
            };
            try (var vault = opened(owner(), ops)) {
                var result = run(vault, all()); assertEquals(1, result.count(Status.STORAGE_FAILED));
                assertEquals(reason, ((ObjectWrite.Failed)result.items().get(0).publication()).reason());
                assertEquals(2, result.unattempted().size()); assertEquals(1, ops.opens);
                assertEquals(0, result.count(Status.IMPORTED));
            }
        }
    }
    @Test public void closeFailureRetainsOwnershipAndNeverOpensAnotherStore() throws Exception {
        var fail = new AtomicBoolean(true);
        var ops = new Tracked() {
            void close(VaultSession session) {
                if (fail.get()) throw new IllegalStateException("injected close failure");
                super.close(session);
            }
        };
        var owner = owner(); var vault = opened(owner, ops);
        try {
            var result = run(vault, all()); assertEquals(Completion.CLOSE_FAILED, result.completion());
            assertEquals(0, ops.publications); assertEquals(1, ops.opens);
            assertThrows(IllegalStateException.class, vault::view); assertThrows(IllegalStateException.class, owner::acquire);
            assertThrows(IllegalStateException.class, vault::close); assertThrows(IllegalStateException.class, owner::acquire);
        } finally { fail.set(false); vault.close(); }
        try (var lease = owner.acquire()) { assertNotNull(lease.root()); }
    }
    @Test public void wrongReopenCredentialDoesNotUndoAcknowledgedImport() throws Exception {
        var owner = owner(); var ops = new Tracked();
        try (var vault = opened(owner, ops)) {
            char[] wrong = "wrong current password".toCharArray();
            var result = vault.reconcile(all(), wrong, CONTINUE);
            assertArrayEquals(new char[wrong.length], wrong);
            assertEquals(Completion.RECONCILIATION_APPLIED_BUT_SESSION_REOPEN_FAILED, result.completion());
            assertTrue(result.openFailure() instanceof OpenResult.AuthenticationFailed);
            assertEquals(3, result.count(Status.IMPORTED)); assertEquals(Reopen.FAILED, result.reopen());
            assertThrows(IllegalStateException.class, owner::acquire);
        }
        try (var vault = opened(owner, new Tracked())) { assertEquals(3, vault.view().tokens().size()); }
    }
    @Test public void reopenObservationBoundaryAndFailureAreExplicit() throws Exception {
        var owner = owner(); var reference = new AtomicReference<ForegroundVaultCoordinator>();
        var ops = new Tracked() {
            void observe(VaultSession session) throws Exception {
                if (opens > 1) {
                    assertEquals(ForegroundVaultCoordinator.State.REOPENING, reference.get().lifecycle());
                    assertThrows(IllegalStateException.class, reference.get()::view);
                    assertThrows(IllegalStateException.class, owner::acquire);
                    throw new IllegalStateException("injected observation failure");
                }
                super.observe(session);
            }
        };
        try (var vault = opened(owner, ops)) {
            reference.set(vault);
            var result = run(vault, all()); assertEquals(3, result.count(Status.IMPORTED));
            assertEquals(Completion.RECONCILIATION_APPLIED_BUT_SESSION_REOPEN_FAILED, result.completion());
            assertNotNull(result.failure()); assertEquals(ForegroundVaultCoordinator.State.FAILED_CLOSED, vault.lifecycle());
        }
        try (var vault = opened(owner, new Tracked())) { assertEquals(3, vault.view().tokens().size()); }
    }
    @Test public void sameLeaseAndSessionGateAtAllTransitionPhases() throws Exception {
        var owner = owner(); var reference = new AtomicReference<ForegroundVaultCoordinator>();
        var ops = new Tracked() {
            void check(ForegroundVaultCoordinator.State expected) {
                assertThrows(IllegalStateException.class, owner::acquire);
                if (reference.get() != null) {
                    assertEquals(expected, reference.get().lifecycle());
                    assertThrows(IllegalStateException.class, reference.get()::view);
                    assertThrows(IllegalStateException.class, reference.get()::close);
                }
            }
            void close(VaultSession session) {
                if (reference.get().lifecycle() == ForegroundVaultCoordinator.State.CLOSING_FOR_IMPORT)
                    check(ForegroundVaultCoordinator.State.CLOSING_FOR_IMPORT);
                super.close(session);
            }
            ObjectWrite publish(LocalReplicaOwner.Lease lease, ImmutableCandidateImporter.Selection selection) throws java.io.IOException {
                check(ForegroundVaultCoordinator.State.IMPORTING); return super.publish(lease, selection);
            }
            OpenResult open(LocalReplicaOwner.Lease lease, char[] password) throws java.io.IOException {
                check(ForegroundVaultCoordinator.State.REOPENING); return super.open(lease, password);
            }
        };
        try (var vault = opened(owner, ops)) {
            reference.set(vault);
            var result = vault.reconcile(all(), credential(), () -> {
                if (vault.lifecycle() == ForegroundVaultCoordinator.State.RECONCILING_READ_ONLY)
                    ops.check(ForegroundVaultCoordinator.State.RECONCILING_READ_ONLY);
                if (vault.lifecycle() == ForegroundVaultCoordinator.State.REOPENING)
                    ops.check(ForegroundVaultCoordinator.State.REOPENING);
                assertThrows(IllegalStateException.class, owner::acquire); return false;
            });
            assertEquals(3, result.count(Status.IMPORTED));
        }
    }
    @Test public void concurrentReconciliationPasswordChangeAndCloseRejected() throws Exception {
        var entered = new CountDownLatch(1); var finish = new CountDownLatch(1);
        var worker = Executors.newSingleThreadExecutor();
        try (var vault = opened(owner(), new Tracked())) {
            var first = worker.submit(() -> vault.reconcile(all(), credential(), () -> {
                entered.countDown();
                try { assertTrue(finish.await(10, TimeUnit.SECONDS)); }
                catch (InterruptedException failure) { throw new AssertionError(failure); }
                return false;
            }));
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS));
                char[] rejected = credential();
                assertThrows(IllegalStateException.class, () -> vault.reconcile(all(), rejected, CONTINUE));
                assertArrayEquals(new char[rejected.length], rejected);
                char[] old = credential(), replacement = credential();
                assertThrows(IllegalStateException.class, () -> vault.changePassword(old, replacement));
                assertArrayEquals(new char[old.length], old); assertArrayEquals(new char[replacement.length], replacement);
                assertThrows(IllegalStateException.class, vault::close);
            } finally { finish.countDown(); }
            assertEquals(3, first.get(30, TimeUnit.SECONDS).count(Status.IMPORTED));
        } finally { worker.shutdownNow(); }
    }
    @Test public void cancellationBeforeAndAfterClassificationPreservesSession() throws Exception {
        for (int cancelAt : List.of(1,2)) {
            var calls = new AtomicInteger(); var ops = new Tracked();
            try (var vault = opened(owner(), ops)) {
                var result = vault.reconcile(all(), credential(), () -> calls.incrementAndGet() == cancelAt);
                assertEquals(Completion.CANCELLED, result.completion()); assertEquals(0, ops.closes);
                assertEquals(0, ops.publications); assertEquals(1, ops.opens); assertNotNull(vault.view());
            }
        }
    }
    @Test public void cancellationDuringPublicationFinishesObjectThenReopens() throws Exception {
        var cancelled = new AtomicBoolean();
        var ops = new Tracked() {
            ObjectWrite publish(LocalReplicaOwner.Lease lease, ImmutableCandidateImporter.Selection selection) throws java.io.IOException {
                cancelled.set(true); return super.publish(lease, selection);
            }
        };
        try (var vault = opened(owner(), ops)) {
            var result = vault.reconcile(all(), credential(), cancelled::get);
            assertEquals(Completion.CANCELLED, result.completion()); assertEquals(1, result.count(Status.IMPORTED));
            assertEquals(2, result.unattempted().size()); assertEquals(Reopen.RESTORED, result.reopen());
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
                var report = new Report(Completion.RETAINED_SESSION, symbolic, List.of(), List.of(), null, Reopen.NOT_NEEDED, null, null);
                assertEquals(excluded == contradiction ? List.of(base.objectId()) : List.of(), report.contradictions());
                assertEquals(excluded == deferred ? List.of(base.objectId()) : List.of(), report.deferredValidation());
            }
            // Independent positively authenticated facts are publishable and observed normally.
            assertEquals(1, run(vault, scan(State.COMPLETE, good(1))).count(Status.IMPORTED));
            assertEquals(1, vault.view().tokens().size());
        }
    }
    @Test public void activeUnsupportedSessionIsFailureNotValidationRetry() throws Exception {
        var ops = new Tracked() {
            OpenResult open(LocalReplicaOwner.Lease lease, char[] password) throws java.io.IOException {
                var real = (OpenResult.Opened) super.open(lease, password);
                var calls = new AtomicInteger();
                VaultSession unsupported = (VaultSession) java.lang.reflect.Proxy.newProxyInstance(
                        VaultSession.class.getClassLoader(), new Class<?>[]{VaultSession.class}, (proxy, method, args) -> {
                            if (method.getName().equals("validateObject") && calls.incrementAndGet() == 2)
                                throw new UnsupportedOperationException("fault");
                            try { return method.invoke(real.session(), args); }
                            catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
                        });
                return new OpenResult.Opened(unsupported);
            }
        };
        try (var vault = opened(owner(), ops)) {
            var result = run(vault, scan(State.COMPLETE, good(0), good(0), good(1)));
            assertEquals(Completion.SESSION_FAILURE, result.completion());
            assertEquals(List.of(ids().get(0)), result.deferredValidation());
            assertEquals(0, ops.publications); assertEquals(1, ops.opens);
            assertEquals(2, result.evidence().groups().size());
            assertEquals(ForegroundVaultCoordinator.State.FAILED_CLOSED, vault.lifecycle());
        }
    }
    @Test public void publicationExceptionRetainsFailedOwnershipAndActualPriorOutcomes() throws Exception {
        var ops = new Tracked() {
            ObjectWrite publish(LocalReplicaOwner.Lease lease, ImmutableCandidateImporter.Selection selection) throws java.io.IOException {
                if (publications == 1) throw new java.io.IOException("injected store-open failure");
                return super.publish(lease, selection);
            }
        };
        try (var vault = opened(owner(), ops)) {
            var result = run(vault, all()); assertEquals(1, result.count(Status.IMPORTED));
            assertEquals(ids().get(1), result.publicationFailureId()); assertEquals(List.of(ids().get(2)), result.unattempted());
            assertEquals(Reopen.SKIPPED_UNSAFE, result.reopen()); assertNotNull(result.failure());
        }
    }
    @Test public void passwordChangeBeforeScanReconciliationRequiresNewCredential() throws Exception {
        var owner = owner();
        try (var vault = opened(owner, new Tracked())) {
            Scan captured = all(); char[] current = credential(), replacement = "new M1H password".toCharArray();
            assertEquals(PasswordChangeResult.CHANGED, vault.changePassword(current, replacement));
            assertArrayEquals(new char[current.length], current); assertArrayEquals(new char[replacement.length], replacement);
            var result = vault.reconcile(captured, "new M1H password".toCharArray(), CONTINUE);
            assertEquals(3, result.count(Status.IMPORTED)); assertEquals(Reopen.RESTORED, result.reopen());
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
}
