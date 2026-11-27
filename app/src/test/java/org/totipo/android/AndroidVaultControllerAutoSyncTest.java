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

/** Real controller AutoSync regressions; isolated fixture per test. */
public final class AndroidVaultControllerAutoSyncTest extends AndroidVaultControllerFixture {
    @Test public void failedFolderChoiceRemainsVisibleAfterSyncAndCancellationUntilSuccessfulChoice() throws Exception {
        var port = dailyFixture();
        String previous = controller.initialTreeUri();
        var session = real.session;
        var objects = new LinkedHashMap<String, byte[]>();
        port.objects.forEach((id, bytes) -> objects.put(id, bytes.clone()));
        port.failSave = true;
        accept(() -> controller.chooseSyncFolder(false, "content://fixture/tree/b", 3)); await(this::providerDone);
        assertEquals(previous, controller.initialTreeUri());
        assertTrue(controller.folderConfigurationNotice().contains("Folder choice was not saved"));
        String refusal = controller.folderConfigurationNotice();
        accept(() -> controller.sync()); await(this::providerDone);
        assertEquals("No local changes to publish", controller.syncView().message());
        assertEquals("ordinary Sync must not hide an explicit configuration failure", refusal, controller.folderConfigurationNotice());
        accept(() -> controller.chooseSyncFolder(true, null, 0)); await(this::providerDone);
        assertEquals("canceling a picker is not successful configuration", refusal, controller.folderConfigurationNotice());
        assertSame(session, real.session); assertEquals(objects.keySet(), port.objects.keySet());
        objects.forEach((id, bytes) -> assertArrayEquals(bytes, port.objects.get(id)));
        port.failSave = false;
        accept(() -> controller.chooseSyncFolder(false, previous, 3)); await(this::providerDone);
        assertEquals("", controller.folderConfigurationNotice());
        assertEquals(previous, controller.initialTreeUri()); assertSame(session, real.session);
    }
    @Test public void failedDisconnectNoticeSurvivesSyncUntilExplicitRecovery() throws Exception {
        var port = dailyFixture();
        String previous = controller.initialTreeUri();
        port.failSave = true;
        accept(() -> controller.disconnectSyncFolder()); await(this::providerDone);
        String refusal = controller.folderConfigurationNotice();
        assertTrue(refusal.contains("Folder disconnection was not saved"));
        assertEquals(previous, controller.initialTreeUri());
        accept(() -> controller.sync()); await(this::providerDone);
        assertEquals(refusal, controller.folderConfigurationNotice());
        accept(() -> controller.checkSyncFolder()); await(this::providerDone);
        assertEquals("explicit Retry access acknowledges the prior notice", "", controller.folderConfigurationNotice());
        accept(() -> controller.disconnectSyncFolder()); await(this::providerDone);
        assertFalse(controller.folderConfigurationNotice().isEmpty());
        port.failSave = false;
        accept(() -> controller.disconnectSyncFolder()); await(this::providerDone);
        assertEquals("", controller.folderConfigurationNotice()); assertNull(controller.initialTreeUri());
        assertEquals("Local only", controller.dailySyncStatus());
    }
    @Test public void dailyManualSyncImportsAndObservesRealConcurrentBranchBeforePublication() throws Exception {
        var port = dailyConcurrentFixture(); var row = lifecycleRow();
        List<String> order = new java.util.concurrent.CopyOnWriteArrayList<>();
        real.operationHook = name -> { if (name.equals("publishObject")) order.add("import"); };
        AtomicBoolean conflictAtPublication = new AtomicBoolean();
        port.onCreate = () -> { order.add("publish"); conflictAtPublication.set(real.session.state().token(row.id()).orElseThrow().alternatives().size() == 2); };
        assertTrue(controller.sync()); await(this::providerDone);
        assertEquals(List.of("import", "import", "publish"), order);
        assertTrue(conflictAtPublication.get()); assertTrue(lifecycleRow().conflict());
        assertEquals("Conflict needs attention", controller.dailySyncStatus());
        assertFalse(port.pending); assertEquals(3, port.objects.size());
    }
    @Test public void dailySyncBlockingObservationNeverPublishes() throws Exception {
        for (String fault : List.of("different", "unavailable", "incomplete", "duplicate", "missing", "orphan", "invalid")) {
            var port = outboundFixture(1); await(this::providerDone);
            if (fault.equals("different")) { port.vaultBytes = port.vaultBytes.clone(); port.vaultBytes[86] ^= 1; }
            if (fault.equals("unavailable")) port.offline = true;
            if (fault.equals("incomplete")) port.coverage = org.totipo.android.provider.ProviderSnapshot.State.INCOMPLETE;
            if (fault.equals("missing")) port.vaultBytes = null;
            if (fault.equals("orphan")) { port.vaultBytes = null; port.objects.put("a".repeat(64), new byte[1024]); }
            if (fault.equals("invalid")) port.objects.put("a".repeat(64), new byte[1024]);
            if (fault.equals("duplicate")) port.snapshotTransform = scan -> {
                var rows = new ArrayList<>(scan.root().rows()); rows.add(scan.vaultCandidates().get(0).document());
                return new org.totipo.android.provider.ProviderSnapshot.Scan(scan.epoch(), scan.tree(),
                        new org.totipo.android.provider.ProviderSnapshot.Listing(scan.epoch(), scan.tree().rootId(), rows, scan.root().state(), scan.root().issues()),
                        scan.directories(), scan.state(), scan.issues(), scan.vaultCandidates());
            };
            var inventory = new HashMap<>(port.objects); byte[] exact = Files.readAllBytes(localRoot().resolve("vault"));
            assertTrue(controller.sync()); await(this::providerDone);
            assertEquals(fault, 0, port.creates); assertEquals(inventory.keySet(), port.objects.keySet());
            assertArrayEquals(exact, Files.readAllBytes(localRoot().resolve("vault")));
            if (fault.equals("different")) assertEquals("This sync folder belongs to a different Totipo vault.", controller.dailySyncStatus());
            lock(); controller.shutdown();
        }
    }
    @Test public void dailyUnlockAndForegroundAutoSyncAreCoalescedAndLockedForegroundDoesNothing() throws Exception {
        var port = dailyFixture(); automaticSync = false; lock();
        int scans = port.scans; automaticSync = true;
        controller.foregroundChanged(false); controller.foregroundChanged(true); pump();
        assertEquals(scans, port.scans);
        accept(() -> controller.unlock(password())); state(State.OPEN);
        await(() -> providerDone() && port.scans > scans);
        int afterUnlock = port.scans;
        controller.foregroundChanged(true); controller.foregroundChanged(true); pump();
        assertEquals(afterUnlock, port.scans);
        controller.foregroundChanged(false); controller.foregroundChanged(true);
        await(() -> providerDone() && port.scans > afterUnlock);
        assertEquals("", controller.dailySyncStatus());
    }
    @Test public void dailyAddCommitsOfflineDoesNotLoopAndManualSyncRetries() throws Exception {
        var port = dailyFixture(); port.offline = true;
        int before = real.coordinator.outboundSnapshot().size();
        add(enrollment("offline", "kept", org.totipo.TotpAlgorithm.SHA1, 6, 30, rfcSecret()), AddTokenOutcome.Status.ADDED);
        await(this::providerDone);
        assertEquals(before + 1, real.coordinator.outboundSnapshot().size());
        assertEquals(State.OPEN, controller.snapshot().state()); assertTrue(port.pending);
        assertEquals("Changes not synced", controller.dailySyncStatus());
        int probes = port.probes;
        for (int i=0; i<100; i++) pump();
        assertEquals(probes, port.probes);
        port.offline = false; assertTrue(controller.sync()); await(this::providerDone);
        assertFalse(port.pending); assertEquals("", controller.dailySyncStatus()); assertEquals(before + 1, port.objects.size());
    }
    @Test public void dailyEditAutoSyncKeepsLocalSuccessOnProviderFailure() throws Exception {
        var port = dailyFixture(); port.offline = true;
        finishChange(controller.beginTokenChange(lifecycleRow().id(), TokenChange.Kind.EDIT), "edited", "offline", 0);
        await(this::providerDone);
        assertEquals(TokenChange.Result.SAVED, controller.tokenChangeResult());
        await(() -> lifecycleRow().alternatives().get(0).account().equals("offline"));
        assertEquals("Changes not synced", controller.dailySyncStatus());
        port.offline = false; assertTrue(controller.sync()); await(this::providerDone);
        assertFalse(port.pending); assertEquals(2, port.objects.size());
    }
    @Test public void dailyDeleteAutomaticallyPublishesTombstone() throws Exception {
        var port = dailyFixture();
        finishChange(controller.beginTokenChange(lifecycleRow().id(), TokenChange.Kind.DELETE), null, null, 0);
        await(() -> providerDone() && port.objects.size() == 2);
        assertEquals(TokenChange.Result.SAVED, controller.tokenChangeResult());
        await(() -> lifecycleRow().alternatives().get(0).status() == org.totipo.TokenStatus.TOMBSTONED);
        assertFalse(port.pending);
    }
    @Test public void dailyResolveAutomaticallyPublishesExplicitChoice() throws Exception {
        var port = outboundFixture(1); await(this::providerDone); conflictBranch(port, false);
        automaticSync = true;
        int before = real.coordinator.outboundSnapshot().size();
        finishChange(controller.beginTokenChange(lifecycleRow().id(), TokenChange.Kind.RESOLVE), null, null, 0);
        await(() -> providerDone() && port.objects.size() == before + 1);
        assertEquals(TokenChange.Result.SAVED, controller.tokenChangeResult());
        await(() -> !lifecycleRow().conflict()); assertFalse(port.pending);
    }
    @Test public void dailyRequestsBoundedDuringActiveSyncAndLocalAdmissionCannotRace() throws Exception {
        var port = dailyFixture();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger visits = new AtomicInteger();
        port.onScan = () -> { if (visits.incrementAndGet() == 1) { entered.countDown();
            try { assertTrue(release.await(30, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new AssertionError(e); } } };
        int before = port.scans;
        assertTrue(controller.sync()); assertTrue(entered.await(10, TimeUnit.SECONDS));
        try {
            for (int i=0; i<100; i++) { controller.foregroundChanged(false); controller.foregroundChanged(true); assertTrue(controller.sync()); }
            assertFalse(controller.canAddToken());
            assertFalse(controller.addToken(enrollment("rejected", "rejected", org.totipo.TotpAlgorithm.SHA1, 6, 30, rfcSecret())));
            assertNull(controller.beginTokenChange(lifecycleRow().id(), TokenChange.Kind.EDIT));
            assertFalse(controller.initializeSyncFolder()); assertFalse(controller.prepareJoin());
        } finally { release.countDown(); }
        await(() -> providerDone() && port.scans >= before + 4);
        // Two no-write Sync attempts, each with import and fresh publication preflight.
        assertEquals(before + 4, port.scans);
        for (int i=0; i<100; i++) pump(); assertEquals(before + 4, port.scans);
    }
    @Test public void dailyBindingChangeDuringObservationPreventsPublication() throws Exception {
        var port = outboundFixture(1); await(this::providerDone);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        port.onScan = () -> { entered.countDown(); try { assertTrue(release.await(30, TimeUnit.SECONDS)); }
            catch (InterruptedException e) { throw new AssertionError(e); } };
        assertTrue(controller.sync()); assertTrue(entered.await(10, TimeUnit.SECONDS));
        assertTrue(controller.disconnectSyncFolder()); await(this::idle);
        release.countDown(); await(this::providerDone);
        assertEquals(0, port.creates); assertNull(controller.initialTreeUri());
    }
    @Test public void dailyPendingHintSurvivesControllerRestartAndDiagnosticsAreReadOnlyAndSanitized() throws Exception {
        var port = dailyFixture(); port.offline = true;
        finishChange(controller.beginTokenChange(lifecycleRow().id(), TokenChange.Kind.EDIT), "edited", "retained", 0);
        await(this::providerDone); assertTrue(port.pending);
        automaticSync = false; lock(); restartProcessAdmission();
        controller = new AndroidVaultController(owner, dispatcher, new RealBackend(), new TotpPresentation.Time() {
            public java.time.Instant wall() { return java.time.Instant.now(); }
            public long elapsedMillis() { return 0; }
        }, null, new org.totipo.android.sync.SyncFolderBinding(port));
        await(this::providerDone); assertEquals(State.LOCKED, controller.snapshot().state());
        accept(() -> controller.unlock(password())); state(State.OPEN); await(this::providerDone);
        assertEquals("Changes not synced", controller.dailySyncStatus());
        int scans = port.scans, writes = port.creates; var objects = real.coordinator.outboundSnapshot();
        String diagnostic = controller.diagnostics();
        assertTrue(diagnostic.contains("Vault ID: ")); assertTrue(diagnostic.contains("v1/r19"));
        for (String secret : List.of("M1J disposable", "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", "12345678901234567890", "IllegalStateException", "java.io", "retained"))
            assertFalse(secret, diagnostic.contains(secret));
        assertEquals(scans, port.scans); assertEquals(writes, port.creates); assertEquals(objects.size(), real.coordinator.outboundSnapshot().size());
    }
    @Test public void dailyFailedLocalImportStopsBeforeAnyProviderPublication() throws Exception {
        var port = dailyConcurrentFixture();
        real.tokenWriteFault = new org.totipo.spi.ObjectWrite.Failed(org.totipo.spi.StoreFailure.UNAVAILABLE);
        try {
            assertTrue(controller.sync()); await(this::providerDone);
            assertEquals(0, port.creates); assertEquals(State.ERROR_OPEN, controller.snapshot().state());
            assertTrue(port.pending);
        } finally { real.tokenWriteFault = null; }
    }
    @Test public void dailyFailedObservationBarrierCannotBeBypassedToPublish() throws Exception {
        var port = outboundFixture(1); await(this::providerDone);
        real.operationHook = name -> { if (name.equals("requestRefresh")) throw new IllegalStateException("private exception details"); };
        try {
            assertTrue(controller.sync()); await(this::providerDone);
            assertEquals(0, port.creates); assertTrue(port.pending);
            assertEquals("Changes not synced", controller.dailySyncStatus());
            assertFalse(controller.diagnostics().contains("private exception details"));
        } finally { real.operationHook = name -> {}; }
    }
    @Test public void dailyReadOnlySyncObservesRemoteConflictButDoesNotPublish() throws Exception {
        var port = dailyConcurrentFixture();
        port.permissions.put(port.stored.uri(), new org.totipo.android.sync.SyncFolderBinding.Grants(true, false));
        assertTrue(controller.sync()); await(this::providerDone);
        assertEquals(0, port.creates); await(() -> lifecycleRow().conflict());
        assertTrue(port.pending); assertEquals("Changes not synced", controller.dailySyncStatus());
    }
}
