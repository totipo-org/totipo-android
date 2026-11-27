package org.totipo.android;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.totipo.android.reconcile.*;
import org.totipo.android.AndroidVaultController.Error;
import static org.totipo.android.AndroidVaultController.*;
import static org.junit.Assert.*;

/** Real controller Inbound regressions; isolated fixture per test. */
public final class AndroidVaultControllerInboundTest extends AndroidVaultControllerFixture {
    @Test public void credentialFreeProductSyncImportsM1IFixtureAndPublishesLaterDetachedState() throws Exception {
        ForegroundVaultCoordinatorTest.realCoreFixture();
        try {
            try (var lease = owner.acquire()) { Files.write(lease.root().resolve("vault"), ForegroundVaultCoordinatorTest.productFixtureVault()); }
            start(); state(State.LOCKED);
            accept(() -> controller.unlock("M1H disposable fixture".toCharArray())); state(State.OPEN);
            var coordinator = real.coordinator; var session = real.session;
            List<Snapshot> updates = new ArrayList<>(); controller.attach(updates::add); pump();
            assertTrue(controller.snapshot().view().tokens().isEmpty());
            accept(() -> controller.sync(ForegroundVaultCoordinatorTest.productFixtureScan()));
            await(() -> controller.snapshot().state() == State.OPEN && controller.snapshot().view().tokens().size() == 3);
            await(() -> updates.stream().anyMatch(s -> s.view() != null && s.view().tokens().size() == 3));
            assertSame(coordinator, real.coordinator); assertSame(session, real.session);
            assertEquals(1, real.opens); assertEquals(0, real.creates); assertEquals(0, real.closes);
            assertThrows(UnsupportedOperationException.class, () -> controller.snapshot().view().tokens().clear());
        } finally { ForegroundVaultCoordinatorTest.removeFixture(); }
    }
    @Test public void manualProviderImportUsesWorkerSameSessionAndHonestCoverage() throws Exception {
        // This case asserts manual transport outcomes, not presentation/completion races.
        // Retain real Java change callbacks and replay them after transport assertions.
        var changes = new ArrayList<Runnable>();
        var deferChanges = new AtomicBoolean(true);
        real.observationCallback = (failed, callback) -> failed ? callback : () -> {
            synchronized (changes) {
                if (deferChanges.get()) changes.add(callback); else callback.run();
            }
        };
        Runnable resumeChanges = () -> {
            synchronized (changes) {
                deferChanges.set(false);
                changes.forEach(Runnable::run);
                changes.clear();
            }
        };
        ForegroundVaultCoordinatorTest.realCoreFixture();
        try {
            byte[] wrapper = ForegroundVaultCoordinatorTest.productFixtureVault();
            try (var lease = owner.acquire()) { Files.write(lease.root().resolve("vault"), wrapper); }
            var port = startBound();
            assertFalse(controller.canImportProviderChanges()); assertFalse(controller.importProviderChanges());
            assertFalse(controller.syncView().toString().contains("content://"));
            accept(() -> controller.unlock("M1H disposable fixture".toCharArray())); state(State.OPEN); await(this::idle);
            assertTrue(controller.canImportProviderChanges());
            var same = real.session;
            var scan = ForegroundVaultCoordinatorTest.productFixtureScan();
            port.snapshot = org.totipo.android.provider.ProviderSnapshotTest.boundedFixture(scan);
            accept(() -> controller.importProviderChanges());
            await(this::providerDone); resumeChanges.run();
            await(() -> providerDone() && controller.snapshot().view().tokens().size() == 3);
            assertTrue(controller.syncView().message().contains("Provider view incomplete"));
            assertTrue(controller.syncView().message().contains("Changes imported"));
            assertNull(controller.snapshot().revealedCode()); assertSame(same, real.session);
            assertEquals(1, real.opens); assertEquals(0, real.closes);
            assertArrayEquals(wrapper, Files.readAllBytes(localRoot().resolve("vault")));
            // Rows above required the real asynchronous observation. The remaining
            // no-op/offline transport checks retain their signals until they finish.
            synchronized (changes) { deferChanges.set(true); }
            real.session.requestRefresh();
            await(() -> { synchronized (changes) { return !changes.isEmpty(); } });
            await(this::providerDone);
            port.snapshot = new org.totipo.android.provider.ProviderSnapshot.Scan(scan.epoch(), scan.tree(), scan.root(),
                    scan.directories(), org.totipo.android.provider.ProviderSnapshot.State.INCOMPLETE_LOADING, scan.issues(), scan.vaultCandidates());
            accept(() -> controller.importProviderChanges()); await(this::providerDone);
            assertEquals("Provider view incomplete. No new objects.", controller.syncView().message());
            port.snapshot = scan; accept(() -> controller.importProviderChanges()); await(this::providerDone);
            assertEquals("No new objects.", controller.syncView().message());
            port.offline = true; accept(() -> controller.importProviderChanges()); await(this::providerDone);
            assertEquals(State.OPEN, controller.snapshot().state());
            assertEquals(org.totipo.android.sync.SyncFolderBinding.Status.UNAVAILABLE, controller.syncView().binding().status());
            assertFalse(controller.canImportProviderChanges());
            port.offline = false; accept(() -> controller.checkSyncFolder()); await(this::providerDone);
            assertTrue(controller.canImportProviderChanges());
            await(() -> { synchronized (changes) { return !changes.isEmpty(); } });
            resumeChanges.run(); await(this::providerDone);
            assertSame(same, real.session);
            assertEquals(3, controller.snapshot().view().tokens().size());
        } finally {
            resumeChanges.run();
            ForegroundVaultCoordinatorTest.removeFixture();
        }
    }
    @Test public void importBusyAdmissionAndEmptyTreeStatus() throws Exception {
        var port = startBound(); accept(() -> controller.create(password())); state(State.OPEN); await(this::idle);
        var tree = new org.totipo.android.provider.ProviderSnapshot.Tree("fixture", "content://fixture/tree/a", "a");
        port.snapshot = new org.totipo.android.provider.ProviderSnapshot.Scan("epoch", tree,
                new org.totipo.android.provider.ProviderSnapshot.Listing("epoch", "a", List.of(),
                        org.totipo.android.provider.ProviderSnapshot.State.COMPLETE, List.of()), List.of(),
                org.totipo.android.provider.ProviderSnapshot.State.COMPLETE, List.of());
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        port.onScan = () -> {
            assertNotSame(ui, Thread.currentThread()); entered.countDown();
            try { assertTrue(release.await(30, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new AssertionError(e); }
        };
        accept(() -> controller.importProviderChanges()); assertTrue(entered.await(10, TimeUnit.SECONDS));
        try {
            assertFalse(controller.importProviderChanges()); assertFalse(controller.canImportProviderChanges());
            assertFalse(controller.checkSyncFolder());
            accept(() -> controller.refresh()); state(State.OPEN);
            assertEquals(1, port.scans);
        } finally { release.countDown(); }
        await(this::providerDone); assertEquals("Sync folder has no Totipo vault.", controller.syncView().message());
    }
    @Test public void productPublicationFailureAndExistingContradictionAreVisible() throws Exception {
        ForegroundVaultCoordinatorTest.realCoreFixture();
        try {
            try (var lease = owner.acquire()) { Files.write(lease.root().resolve("vault"), ForegroundVaultCoordinatorTest.productFixtureVault()); }
            var port = startBound(); port.snapshot = ForegroundVaultCoordinatorTest.productFixtureScan();
            accept(() -> controller.unlock("M1H disposable fixture".toCharArray())); state(State.OPEN);
            real.tokenWriteFault = new org.totipo.spi.ObjectWrite.ExistingDifferent();
            accept(() -> controller.importProviderChanges()); await(this::providerDone);
            assertTrue(controller.syncView().message().contains("Integrity problem"));
            accept(() -> controller.checkSyncFolder()); await(this::providerDone);
            assertTrue(controller.syncView().message().contains("Integrity problem"));
            real.tokenWriteFault = new org.totipo.spi.ObjectWrite.Failed(org.totipo.spi.StoreFailure.UNAVAILABLE);
            accept(() -> controller.importProviderChanges()); await(this::providerDone);
            assertEquals(State.ERROR_OPEN, controller.snapshot().state());
            assertTrue(controller.syncView().message().contains("Local publication failed"));
            assertEquals(1, real.opens); assertEquals(0, real.closes);
            real.tokenWriteFault = null;
        } finally { ForegroundVaultCoordinatorTest.removeFixture(); }
    }
    @Test public void blockedProviderLockDisconnectAndReplacementDiscardWithoutRefresh() throws Exception {
        ForegroundVaultCoordinatorTest.realCoreFixture();
        try {
            for (String action : List.of("lock", "disconnect", "replace", "permission")) {
                owner = TestReplicaOwners.create(temporary.newFolder().toPath());
                try (var lease = owner.acquire()) { Files.write(lease.root().resolve("vault"), ForegroundVaultCoordinatorTest.productFixtureVault()); }
                var port = startBound(); port.snapshot = ForegroundVaultCoordinatorTest.productFixtureScan();
                accept(() -> controller.unlock("M1H disposable fixture".toCharArray())); state(State.OPEN);
                var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
                var threads = new HashSet<Thread>();
                port.onScan = () -> { synchronized (threads) { threads.add(Thread.currentThread()); }
                    assertEquals("Totipo-provider-io", Thread.currentThread().getName()); block(entered, release); };
                Set<Path> beforeFiles;
                try (var files = Files.walk(localRoot())) { beforeFiles = files.collect(java.util.stream.Collectors.toSet()); }
                int refreshes = real.refreshes;
                var vaultOps = new ArrayList<String>();
                real.operationHook = name -> {
                    if (name.equals("publishObject") || name.equals("requestRefresh"))
                        assertEquals("Totipo-vault", Thread.currentThread().getName());
                    synchronized (vaultOps) { vaultOps.add(name); }
                };
                accept(() -> controller.importProviderChanges()); assertTrue(entered.await(10, TimeUnit.SECONDS));
                try {
                    await(this::idle);
                    assertEquals(State.OPEN, controller.snapshot().state()); // UI Lock remains enabled.
                    assertEquals("Importing…", controller.syncView().message());
                    for (int i = 0; i < 100; i++) assertFalse(controller.importProviderChanges());
                    assertFalse(controller.checkSyncFolder()); assertEquals(1, port.scans);
                    var laneField = AndroidVaultController.class.getDeclaredField("providerIo"); laneField.setAccessible(true);
                    Object lane = laneField.get(controller);
                    var executorField = lane.getClass().getDeclaredField("executor"); executorField.setAccessible(true);
                    var executor = (ThreadPoolExecutor) executorField.get(lane);
                    assertEquals(1, executor.getLargestPoolSize()); assertEquals(0, executor.getQueue().size());
                    if (action.equals("lock")) {
                        lock(); assertEquals(1, real.closes);
                        accept(() -> controller.unlock("M1H disposable fixture".toCharArray())); state(State.OPEN);
                    } else if (action.equals("disconnect")) {
                        accept(() -> controller.disconnectSyncFolder()); await(this::idle);
                        assertEquals(org.totipo.android.sync.SyncFolderBinding.Status.NOT_CONFIGURED, controller.syncView().binding().status());
                    } else if (action.equals("permission")) {
                        port.permissions.clear(); // Grant revoked while a provider ignores cancellation.
                    } else {
                        accept(() -> controller.chooseSyncFolder(false, "content://fixture/tree/b", 1)); await(this::idle);
                        assertEquals("content://fixture/tree/b", controller.initialTreeUri());
                    }
                    assertEquals(1L, release.getCount()); assertEquals(1, threads.size());
                    synchronized (vaultOps) { vaultOps.clear(); }
                } finally { release.countDown(); }
                await(this::providerDone);
                assertEquals(refreshes, real.refreshes);
                assertTrue(controller.snapshot().view().tokens().isEmpty());
                try (var files = Files.walk(localRoot())) {
                    assertEquals(beforeFiles, files.collect(java.util.stream.Collectors.toSet()));
                }
                synchronized (vaultOps) { assertFalse(vaultOps.contains("requestRefresh")); assertFalse(vaultOps.contains("publishObject")); }
                if (action.equals("replace")) {
                    assertEquals("content://fixture/tree/b", controller.initialTreeUri());
                    assertEquals(org.totipo.android.sync.SyncFolderBinding.Status.READY, controller.syncView().binding().status());
                }
                if (action.equals("disconnect")) assertNull(controller.initialTreeUri());
                if (action.equals("permission")) assertEquals(org.totipo.android.sync.SyncFolderBinding.Status.ACCESS_LOST,
                        controller.syncView().binding().status());
                lock(); controller.shutdown(); real.operationHook = name -> {};
            }
        } finally { ForegroundVaultCoordinatorTest.removeFixture(); }
    }
    @Test public void startupAndRetryProbeCannotBlockLocalCreateOrAddAndUseOneThread() throws Exception {
        var port = new org.totipo.android.sync.SyncFolderBindingTest.MemoryPort();
        port.stored = new org.totipo.android.sync.SyncFolderBinding.Stored("content://fixture/tree/a", true, false);
        port.permissions.put(port.stored.uri(), new org.totipo.android.sync.SyncFolderBinding.Grants(true, false));
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var threads = new HashSet<Thread>();
        port.onProbe = () -> { synchronized (threads) { threads.add(Thread.currentThread()); }
            assertEquals("Totipo-provider-io", Thread.currentThread().getName()); block(entered, release); };
        controller = new AndroidVaultController(owner, dispatcher, new RealBackend(), new TotpPresentation.Time() {
            public java.time.Instant wall() { return java.time.Instant.now(); }
            public long elapsedMillis() { return 0; }
        }, null, new org.totipo.android.sync.SyncFolderBinding(port));
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        try {
            state(State.NO_LOCAL_VAULT); await(this::idle);
            assertEquals(org.totipo.android.sync.SyncFolderBinding.Status.CHECKING, controller.syncView().binding().status());
            assertFalse(controller.canImportProviderChanges());
            for (int i = 0; i < 100; i++) assertFalse(controller.checkSyncFolder());
            accept(() -> controller.create(password())); state(State.OPEN);
            add(enrollment("local", "", org.totipo.TotpAlgorithm.SHA1, 6, 30, rfcSecret()), AddTokenOutcome.Status.ADDED);
            assertEquals(1, port.probes); assertEquals(1, threads.size()); assertEquals(1L, release.getCount());
        } finally { release.countDown(); }
        await(this::providerDone);
        assertEquals(org.totipo.android.sync.SyncFolderBinding.Status.READY, controller.syncView().binding().status());
        port.offline = true; accept(() -> controller.checkSyncFolder()); await(this::providerDone);
        assertEquals(org.totipo.android.sync.SyncFolderBinding.Status.UNAVAILABLE, controller.syncView().binding().status());
        assertEquals(1, threads.size());
    }
    @Test public void joinWrongPasswordThenExactEnrollmentThenManualImportOnly() throws Exception {
        ForegroundVaultCoordinatorTest.realCoreFixture();
        try {
            var port = startBound(); port.snapshot = ForegroundVaultCoordinatorTest.productFixtureScan();
            prepareJoin(); char[] wrong = "wrong".toCharArray(); accept(() -> controller.joinExistingVault(wrong)); await(this::providerDone);
            assertEquals(State.NO_LOCAL_VAULT, controller.snapshot().state()); assertArrayEquals(new char[wrong.length],wrong);
            assertFalse(Files.exists(localRoot().resolve("vault")));
            char[] correct = "M1H disposable fixture".toCharArray(); accept(() -> controller.joinExistingVault(correct)); await(this::providerDone);
            assertEquals(State.OPEN,controller.snapshot().state()); assertArrayEquals(new char[correct.length],correct);
            byte[] exact = port.snapshot.vaultCandidates().get(0).bytes();
            assertArrayEquals(exact,Files.readAllBytes(localRoot().resolve("vault")));
            assertEquals(org.totipo.Totipo.vaultId(exact),real.session.vaultId()); assertTrue(controller.snapshot().view().tokens().isEmpty());
            var session = real.session; accept(() -> controller.importProviderChanges()); await(this::providerDone);
            await(() -> controller.snapshot().view().tokens().size()==3); assertSame(session,real.session);
        } finally { ForegroundVaultCoordinatorTest.removeFixture(); }
    }
    @Test public void authenticatedDuplicateJoinNeverEnrollsOrMarksCreationAttempted() throws Exception {
        ForegroundVaultCoordinatorTest.realCoreFixture();
        try {
            var field = LocalReplicaOwner.class.getDeclaredField("registry"); field.setAccessible(true);
            var registry = (VaultRegistry)field.get(owner);
            var retained = registry.reserve("Remembered replica");
            byte[] exact = ForegroundVaultCoordinatorTest.productFixtureVault();
            Files.write(registry.root(retained.id()).resolve("vault"), exact);
            var port = startBound(); port.snapshot = ForegroundVaultCoordinatorTest.productFixtureScan();
            prepareJoin(); char[] password = "M1H disposable fixture".toCharArray();
            accept(() -> controller.joinExistingVault(password)); await(this::providerDone);
            assertEquals(1, candidateAuthentications.get());
            assertArrayEquals(new char[password.length], password);
            assertEquals(State.NO_LOCAL_VAULT, controller.snapshot().state());
            assertFalse(Files.exists(localRoot().resolve("vault")));
            assertFalse(registry.entry(owner.localId()).creationAttempted());
            assertArrayEquals(exact, Files.readAllBytes(registry.root(retained.id()).resolve("vault")));
            assertTrue(controller.syncView().message().contains("duplicate"));
        } finally { ForegroundVaultCoordinatorTest.removeFixture(); }
    }
    @Test public void joinInvalidInputMalformedMissingAndUnavailableLeaveAbsent() throws Exception {
        ForegroundVaultCoordinatorTest.realCoreFixture();
        try {
            var port=startBound(); var scan=ForegroundVaultCoordinatorTest.productFixtureScan();
            port.snapshot=scan;
            prepareJoin(); char[] invalid=new char[]{'\ud800'}; accept(() -> controller.joinExistingVault(invalid)); await(this::providerDone);
            assertArrayEquals(new char[1],invalid); assertFalse(Files.exists(localRoot().resolve("vault")));
            for(String kind:List.of("missing","malformed","size","directory","duplicate","loading")) {
                port.snapshot=scan;
                var row=scan.vaultCandidates().get(0).document();
                var rows=new ArrayList<>(scan.root().rows());
                var candidates=new ArrayList<>(scan.vaultCandidates());
                if(kind.equals("missing")) { rows.remove(row); candidates.clear(); }
                if(kind.equals("duplicate")) rows.add(row);
                if(kind.equals("directory")) { rows.remove(row); rows.add(new org.totipo.android.provider.ProviderSnapshot.Document(row.tree(),row.locator(),row.id(),row.parentId(),"vault","vnd.android.document/directory",null,null)); }
                if(kind.equals("malformed") || kind.equals("size")) {
                    byte[] bytes=kind.equals("size")?new byte[86]:row==null?null:scan.vaultCandidates().get(0).bytes();
                    if(kind.equals("malformed")) bytes[0]^=1;
                    candidates= new ArrayList<>(List.of(new org.totipo.android.provider.ProviderSnapshot.Bytes(scan.epoch(),row,87,org.totipo.android.provider.ProviderSnapshot.ByteState.PRESENT,bytes,org.totipo.android.provider.ProviderSnapshot.Issue.NONE)));
                }
                port.snapshot=new org.totipo.android.provider.ProviderSnapshot.Scan(scan.epoch(),scan.tree(),
                    new org.totipo.android.provider.ProviderSnapshot.Listing(scan.epoch(),scan.tree().rootId(),rows,kind.equals("loading")?org.totipo.android.provider.ProviderSnapshot.State.INCOMPLETE_LOADING:org.totipo.android.provider.ProviderSnapshot.State.COMPLETE,List.of()),scan.directories(),scan.state(),scan.issues(),candidates);
                prepareJoin(); char[] credential=password();assertFalse(controller.joinExistingVault(credential));await(this::providerDone);
                assertArrayEquals(new char[credential.length],credential);assertFalse(kind,Files.exists(localRoot().resolve("vault")));assertEquals(0,real.opens);assertEquals(1,candidateAuthentications.get());
            }
            port.offline=true;prepareJoin();await(this::providerDone);
            assertFalse(Files.exists(localRoot().resolve("vault")));
        } finally { ForegroundVaultCoordinatorTest.removeFixture(); }
    }
    @Test public void joinFreshRecheckRejectsChangedCandidateAndLocalOrphans() throws Exception {
        ForegroundVaultCoordinatorTest.realCoreFixture();
        try {
            var port=startBound();var scan=ForegroundVaultCoordinatorTest.productFixtureScan();port.snapshot=scan;
            var count=new AtomicInteger();port.onScan=() -> { if(count.incrementAndGet()==3) {
                byte[] bytes=scan.vaultCandidates().get(0).bytes();bytes[86]^=1;
                port.snapshot=ForegroundVaultCoordinatorTest.withVault(new org.totipo.android.provider.ProviderSnapshot.Scan(scan.epoch(),scan.tree(),
                    new org.totipo.android.provider.ProviderSnapshot.Listing(scan.epoch(),scan.tree().rootId(),scan.root().rows().stream().filter(r -> !"vault".equals(r.displayName())).toList(),scan.root().state(),List.of()),scan.directories(),scan.state(),List.of()),bytes);
            }};
            prepareJoin(); accept(() -> controller.joinExistingVault("M1H disposable fixture".toCharArray()));await(this::providerDone);
            assertEquals("Sync folder changed. Try again.",controller.syncView().message());assertFalse(Files.exists(localRoot().resolve("vault")));
            port.onScan=() -> {};port.snapshot=scan;
            Files.createDirectories(localRoot().resolve("objects-v1"));Files.write(localRoot().resolve("objects-v1").resolve("a".repeat(64)),new byte[]{1});
            prepareJoin(); assertFalse(controller.joinExistingVault("M1H disposable fixture".toCharArray()));await(this::providerDone);
            assertFalse(Files.exists(localRoot().resolve("vault")));assertEquals(0,real.opens);
        } finally { ForegroundVaultCoordinatorTest.removeFixture(); }
    }
    @Test public void joinCancellationDuringBlockedProviderWipesCredentialAndLeavesLaneOccupied() throws Exception {
        ForegroundVaultCoordinatorTest.realCoreFixture();
        try {
            var port=startBound();port.snapshot=ForegroundVaultCoordinatorTest.productFixtureScan();
            prepareJoin(); var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
            port.onScan=() -> { assertEquals("Totipo-provider-io",Thread.currentThread().getName());block(entered,release); };
            char[] credential="M1H disposable fixture".toCharArray();accept(() -> controller.joinExistingVault(credential));assertTrue(entered.await(10,TimeUnit.SECONDS));
            try {
                controller.cancelJoin();await(() -> Arrays.equals(new char[credential.length],credential));
                assertFalse(controller.canJoinExistingVault());assertFalse(controller.checkSyncFolder());
                assertFalse(Files.exists(localRoot().resolve("vault")));
            } finally { release.countDown(); }
            await(this::providerDone);assertEquals(0,real.opens);assertEquals(State.NO_LOCAL_VAULT,controller.snapshot().state());
        } finally { ForegroundVaultCoordinatorTest.removeFixture(); }
    }
    @Test public void folderChangeBeforeAuthenticationResultSuppressesEnrollment() throws Exception {
        ForegroundVaultCoordinatorTest.realCoreFixture();
        try {
            var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
            var port=startBound(new RealBackend() {
                boolean authenticateCandidate(byte[] exact,char[] credential) { block(entered,release);return super.authenticateCandidate(exact,credential); }
            });
            port.snapshot=ForegroundVaultCoordinatorTest.productFixtureScan();prepareJoin();
            char[] credential="M1H disposable fixture".toCharArray();accept(() -> controller.joinExistingVault(credential));assertTrue(entered.await(10,TimeUnit.SECONDS));
            try { assertTrue(controller.disconnectSyncFolder()); } finally { release.countDown(); }
            await(this::providerDone);assertFalse(Files.exists(localRoot().resolve("vault")));assertArrayEquals(new char[credential.length],credential);
            assertEquals(0,real.opens);assertEquals(org.totipo.android.sync.SyncFolderBinding.Status.NOT_CONFIGURED,controller.syncView().binding().status());
        } finally { ForegroundVaultCoordinatorTest.removeFixture(); }
    }
    @Test public void initializeLockDuringBlockedCreatePreventsWriteAndStaleCompletion() throws Exception {
        var port=outboundFixture(1);port.vaultBytes=null;port.directoryPresent=false;
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        port.onVaultCreate=() -> block(entered,release);
        accept(() -> controller.initializeSyncFolder());assertTrue(entered.await(10,TimeUnit.SECONDS));
        try {
            assertFalse(controller.initializeSyncFolder());accept(() -> controller.lock());state(State.LOCKED);
            assertFalse(controller.canPublishLocalChanges());
        } finally { release.countDown(); }
        await(this::providerDone);assertEquals(0,port.vaultOutputs);assertEquals(0,port.directoryCreates);assertEquals(State.LOCKED,controller.snapshot().state());
    }
    @Test public void initializeThenManualPublishOnlyAndRepeatedInitializationNoWrites() throws Exception {
        var port=outboundFixture(1);var session=real.session;byte[] exact=port.vaultBytes.clone();port.vaultBytes=null;port.directoryPresent=false;
        accept(() -> controller.initializeSyncFolder());await(this::providerDone);
        assertEquals("Sync folder initialized. Use Sync to publish local changes.", controller.syncView().message());
        assertArrayEquals(exact,port.vaultBytes);assertTrue(port.directoryPresent);assertTrue(port.objects.isEmpty());assertEquals(1,port.vaultCreates);
        accept(() -> controller.publishLocalChanges());await(this::providerDone);assertEquals(1,port.objects.size());assertSame(session,real.session);
        accept(() -> controller.initializeSyncFolder());await(this::providerDone);assertEquals(1,port.vaultCreates);assertEquals(1,port.directoryCreates);
        assertArrayEquals(exact,Files.readAllBytes(localRoot().resolve("vault")));
    }
}
