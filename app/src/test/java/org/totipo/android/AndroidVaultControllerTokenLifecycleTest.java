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

/** Real controller TokenLifecycle regressions; isolated fixture per test. */
public final class AndroidVaultControllerTokenLifecycleTest extends AndroidVaultControllerFixture {
    @Test public void lifecycleEditRetainsCredentialHistorySessionAndExplicitPublish() throws Exception {
        var port = outboundFixture(1); var session = real.session;
        await(this::providerDone);
        var row = lifecycleRow(); assertTrue(TokenChange.live(row));
        var old = session.state(); var alternative = old.token(row.id()).orElseThrow().alternatives().get(0);
        var code = old.generateTotp(alternative, java.time.Instant.ofEpochSecond(59)).code();
        var objects = real.coordinator.outboundSnapshot(); int scans = port.scans;
        var edit = controller.beginTokenChange(row.id(), TokenChange.Kind.EDIT);
        assertNotNull(edit); assertEquals("public", edit.basis().alternatives().get(0).issuer());
        assertEquals("test 0", edit.basis().alternatives().get(0).account());
        assertFalse(controller.canAddToken()); assertFalse(controller.canPublishLocalChanges());
        assertFalse(controller.refresh()); assertFalse(controller.canManageSyncFolder());
        finishChange(edit, " Public <&> ", "new account", 0);
        assertEquals(TokenChange.Result.SAVED, controller.tokenChangeResult());
        await(() -> lifecycleRow().alternatives().get(0).account().equals("new account"));
        assertEquals(" Public <&> ", lifecycleRow().alternatives().get(0).issuer());
        assertEquals("test 0", alternative.descriptor().account());
        assertEquals(code, session.state().generateTotp(session.state().token(row.id()).orElseThrow().alternatives().get(0), java.time.Instant.ofEpochSecond(59)).code());
        assertSame(session, real.session); assertNull(controller.snapshot().revealedCode());
        assertEquals(scans, port.scans); assertTrue(port.objects.isEmpty());
        var after = real.coordinator.outboundSnapshot(); assertEquals(objects.size() + 1, after.size());
        for (var object : objects) assertArrayEquals(object.representation(), after.stream().filter(o -> o.id().equals(object.id())).findFirst().orElseThrow().representation());
        publishLifecycle(port);
    }
    @Test public void lifecycleDeleteIsTombstoneConcealsRetainsHistoryAndExplicitPublish() throws Exception {
        var port = outboundFixture(1); await(this::providerDone); var session = real.session;
        var row = lifecycleRow(); var objects = real.coordinator.outboundSnapshot();
        accept(() -> controller.showCode(row.id())); await(this::idle); assertNotNull(controller.snapshot().revealedCode());
        var cancel = controller.beginTokenChange(row.id(), TokenChange.Kind.DELETE); assertNotNull(cancel);
        assertNull(controller.snapshot().revealedCode()); controller.cancelTokenChange(cancel);
        assertEquals(objects.size(), real.coordinator.outboundSnapshot().size());
        var change = controller.beginTokenChange(row.id(), TokenChange.Kind.DELETE);
        finishChange(change, null, null, 0); assertEquals(TokenChange.Result.SAVED, controller.tokenChangeResult());
        await(() -> lifecycleRow().alternatives().get(0).status() == org.totipo.TokenStatus.TOMBSTONED);
        assertFalse(TokenChange.live(lifecycleRow())); assertFalse(TokenListAdapter.usable(lifecycleRow()));
        assertNull(controller.beginTokenChange(row.id(), TokenChange.Kind.DELETE));
        assertNull(controller.beginTokenChange(row.id(), TokenChange.Kind.EDIT));
        assertNull(controller.snapshot().revealedCode()); assertSame(session, real.session); assertTrue(port.objects.isEmpty());
        assertEquals(objects.size() + 1, real.coordinator.outboundSnapshot().size());
        for (var object : objects) assertTrue(Files.exists(localRoot().resolve("objects-v1").resolve(object.id().hex())));
        publishLifecycle(port);
    }
    @Test public void lifecycleCancelInvalidAndLockNeverAuthor() throws Exception {
        var port = outboundFixture(1); await(this::providerDone); var row = lifecycleRow();
        int before = real.coordinator.outboundSnapshot().size();
        var edit = controller.beginTokenChange(row.id(), TokenChange.Kind.EDIT); controller.cancelTokenChange(edit);
        assertFalse(controller.confirmTokenChange(edit, "x", "x", 0));
        var invalid = controller.beginTokenChange(row.id(), TokenChange.Kind.EDIT);
        finishChange(invalid, "x".repeat(257), "account", 0); assertEquals(TokenChange.Result.INVALID, controller.tokenChangeResult());
        assertEquals(before, real.coordinator.outboundSnapshot().size());
        for (var kind : List.of(TokenChange.Kind.EDIT, TokenChange.Kind.DELETE)) {
            await(this::idle); var pending = controller.beginTokenChange(row.id(), kind); assertNotNull(pending);
            lock(); assertNull(controller.pendingTokenChange()); assertFalse(controller.confirmTokenChange(pending, "x", "x", 0));
            accept(() -> controller.unlock(password())); state(State.OPEN); await(this::idle);
        }
        assertEquals(before, real.coordinator.outboundSnapshot().size()); assertTrue(port.objects.isEmpty());
    }
    @Test public void lifecycleStaleEditAndDeleteDoNotRetry() throws Exception {
        var port = outboundFixture(1); await(this::providerDone);
        for (var kind : List.of(TokenChange.Kind.EDIT, TokenChange.Kind.DELETE)) {
            await(this::idle); var row = lifecycleRow(); var pending = controller.beginTokenChange(row.id(), kind);
            try (var update = real.session.state().update(real.session.state().token(row.id()).orElseThrow().alternatives().get(0))) {
                assertTrue(update.account(kind.name()).save() instanceof org.totipo.SaveResult.Saved);
            }
            await(() -> lifecycleRow().alternatives().get(0).account().equals(kind.name())); await(this::idle);
            int count = real.coordinator.outboundSnapshot().size();
            finishChange(pending, "stale", "stale", 0); assertEquals(TokenChange.Result.STALE, controller.tokenChangeResult());
            assertEquals(count, real.coordinator.outboundSnapshot().size()); assertTrue(controller.snapshot().message().contains("This token changed."));
        }
        assertTrue(port.objects.isEmpty());
    }
    @Test public void lifecycleResolveCompleteLocalAlternative() throws Exception { resolveLifecycle(false, "local"); }
    @Test public void lifecycleResolveCompleteRemoteAlternative() throws Exception { resolveLifecycle(false, "remote"); }
    @Test public void lifecycleResolveDeletedAlternative() throws Exception { resolveLifecycle(true, "remote"); }
    @Test public void lifecycleStaleConflictAndLockRejectChoice() throws Exception {
        var port = outboundFixture(1); await(this::providerDone); conflictBranch(port, false);
        var row = lifecycleRow(); var pending = controller.beginTokenChange(row.id(), TokenChange.Kind.RESOLVE);
        try (var update = real.session.state().update(real.session.state().token(row.id()).orElseThrow().alternatives().get(0))) {
            assertTrue(update.account("advanced").save() instanceof org.totipo.SaveResult.Saved);
        }
        await(() -> lifecycleRow().alternatives().stream().anyMatch(d -> d.account().equals("advanced"))); await(this::idle);
        int before = real.coordinator.outboundSnapshot().size(); finishChange(pending, null, null, 0);
        assertEquals(TokenChange.Result.STALE, controller.tokenChangeResult()); assertEquals(before, real.coordinator.outboundSnapshot().size());
        assertTrue(controller.snapshot().message().contains("This conflict changed."));
        var locked = controller.beginTokenChange(row.id(), TokenChange.Kind.RESOLVE); assertNotNull(locked); lock();
        assertFalse(controller.confirmTokenChange(locked, null, null, 0));
    }
    @Test public void lifecycleJavaFreshnessGateSeesUnobservedLegitimateRevision() throws Exception {
        var port=outboundFixture(1); await(this::providerDone); var row=lifecycleRow();
        Path root=temporary.newFolder().toPath();Files.write(root.resolve("vault"),port.vaultBytes);Files.createDirectory(root.resolve("objects-v1"));
        var old=real.coordinator.outboundSnapshot();
        for(var object:old)Files.write(root.resolve("objects-v1").resolve(object.id().hex()),object.representation());
        try(var branch=((org.totipo.OpenResult.Opened)org.totipo.Totipo.open(org.totipo.storage.nio.NioStoreComposition.coordinatedDelegate(root,new org.totipo.storage.nio.NioDurability()),password())).session()) {
            await(()->branch.state().observation() instanceof org.totipo.ObservationProgress.Finished);
            try(var update=branch.state().update(branch.state().token(row.id()).orElseThrow().alternatives().get(0))){assertTrue(update.account("new unseen head").save() instanceof org.totipo.SaveResult.Saved);}
        }
        var inject=new AtomicBoolean(true); List<Path> paths;try(var files=Files.list(root.resolve("objects-v1"))){paths=files.toList();}
        var pending=controller.beginTokenChange(row.id(),TokenChange.Kind.EDIT);
        real.beforeScan=delegate->{if(inject.getAndSet(false))for(Path path:paths)try {
            var result=delegate.publishObject(new org.totipo.spi.ObjectName(path.getFileName().toString()),Files.readAllBytes(path));
            assertTrue(result instanceof org.totipo.spi.ObjectWrite.Written || result instanceof org.totipo.spi.ObjectWrite.AlreadyPresentExact);
        }catch(Exception failure){throw new AssertionError(failure);}};
        finishChange(pending,"stale","must not save",0);real.beforeScan=store->{};
        assertEquals(TokenChange.Result.STALE,controller.tokenChangeResult());
        assertEquals(old.size()+1,real.coordinator.outboundSnapshot().size());
        assertTrue(port.objects.isEmpty());
        await(()->lifecycleRow().alternatives().get(0).account().equals("new unseen head"));
    }
    @Test public void lifecycleActualFailedAndUncertainLocalWritesDoNotRetryOrPublish() throws Exception {
        var port=outboundFixture(1);await(this::providerDone);var row=lifecycleRow();int count=real.coordinator.outboundSnapshot().size();
        real.tokenWriteFault=new org.totipo.spi.ObjectWrite.Failed(org.totipo.spi.StoreFailure.UNAVAILABLE);
        var failed=controller.beginTokenChange(row.id(),TokenChange.Kind.EDIT);finishChange(failed,"x","x",0);
        assertEquals(TokenChange.Result.PUBLICATION_UNCERTAIN,controller.tokenChangeResult());assertEquals(count,real.coordinator.outboundSnapshot().size());
        real.tokenWriteFault=new org.totipo.spi.ObjectWrite.Uncertain(org.totipo.spi.StoreFailure.UNAVAILABLE);real.persistBeforeTokenFault=true;
        var uncertain=controller.beginTokenChange(row.id(),TokenChange.Kind.DELETE);finishChange(uncertain,null,null,0);
        assertEquals(TokenChange.Result.PUBLICATION_UNCERTAIN,controller.tokenChangeResult());real.tokenWriteFault=null;
        assertEquals(count+1,real.coordinator.outboundSnapshot().size());assertTrue(port.objects.isEmpty());
        accept(()->controller.refresh());await(()->lifecycleRow().alternatives().get(0).status()==org.totipo.TokenStatus.TOMBSTONED);
    }
    @Test public void lifecycleIncompleteLocalObservationFailsWithoutAuthoring() throws Exception {
        var port=outboundFixture(1);await(this::providerDone);var row=lifecycleRow();int count=real.coordinator.outboundSnapshot().size();
        var change=controller.beginTokenChange(row.id(),TokenChange.Kind.EDIT);real.incompleteChangeScan=true;
        try{finishChange(change,"must not save","must not save",0);assertEquals(TokenChange.Result.FAILED,controller.tokenChangeResult());}
        finally{real.incompleteChangeScan=false;}
        assertTrue(controller.snapshot().message().contains("Totipo could not save this change."));
        assertEquals(count,real.coordinator.outboundSnapshot().size());assertTrue(port.objects.isEmpty());
    }
    @Test public void timeoutRetiresEditAndRejectsLateConfirmation() throws Exception { timeoutPendingChange(TokenChange.Kind.EDIT); }
    @Test public void timeoutRetiresDeleteAndRejectsLateConfirmation() throws Exception { timeoutPendingChange(TokenChange.Kind.DELETE); }
    @Test public void timeoutRetiresResolveAndRejectsLateConfirmation() throws Exception { timeoutPendingChange(TokenChange.Kind.RESOLVE); }
    @Test public void localOnlyAuthoringNeverTouchesProviderOrCreatesTransportHintAndReopens() throws Exception {
        var port = new org.totipo.android.sync.SyncFolderBindingTest.MemoryPort();
        port.onProbe = () -> { throw new AssertionError("Local-only provider probe"); };
        port.onScan = () -> { throw new AssertionError("Local-only provider scan"); };
        var binding = new org.totipo.android.sync.SyncFolderBinding(port);
        controller = new AndroidVaultController(owner, dispatcher, new RealBackend(), new TotpPresentation.Time() {
            public java.time.Instant wall() { return java.time.Instant.ofEpochSecond(59); }
            public long elapsedMillis() { return 0; }
        }, null, binding);
        automaticSync = true; state(State.NO_LOCAL_VAULT);
        accept(() -> controller.create(password())); state(State.OPEN);
        add(enrollment("local", "only", org.totipo.TotpAlgorithm.SHA1, 6, 30, rfcSecret()), AddTokenOutcome.Status.ADDED);
        await(() -> controller.snapshot().view().tokens().size() == 1); await(this::providerDone);
        finishChange(controller.beginTokenChange(lifecycleRow().id(), TokenChange.Kind.EDIT), "local", "edited", 0);
        await(() -> lifecycleRow().alternatives().get(0).account().equals("edited")); await(this::providerDone);
        assertEquals("Local only", controller.dailySyncStatus()); assertFalse(port.pending);
        controller.foregroundChanged(false); controller.foregroundChanged(true); await(this::idle);
        assertFalse(controller.sync()); assertEquals(0, port.probes); assertEquals(0, port.scans);
        lock(); accept(() -> controller.unlock(password())); state(State.OPEN);
        await(() -> controller.snapshot().view() != null && controller.snapshot().view().tokens().size() == 1);
        assertEquals("edited", lifecycleRow().alternatives().get(0).account());
        lock(); restartProcessAdmission();
        controller = new AndroidVaultController(owner, dispatcher, new RealBackend(), new TotpPresentation.Time() {
            public java.time.Instant wall() { return java.time.Instant.ofEpochSecond(59); }
            public long elapsedMillis() { return 0; }
        }, null, new org.totipo.android.sync.SyncFolderBinding(port));
        state(State.LOCKED); accept(() -> controller.unlock(password())); state(State.OPEN);
        await(() -> controller.snapshot().view() != null && controller.snapshot().view().tokens().size() == 1); await(this::idle);
        finishChange(controller.beginTokenChange(lifecycleRow().id(), TokenChange.Kind.DELETE), null, null, 0);
        await(() -> lifecycleRow().alternatives().get(0).status() == org.totipo.TokenStatus.TOMBSTONED); await(this::providerDone);
        assertEquals("Local only", controller.dailySyncStatus()); assertFalse(port.pending);
        assertEquals(0, port.takes); assertEquals(0, port.probes); assertEquals(0, port.scans);
    }
    @Test public void disconnectClearsTransportHintAndLocalOnlyResolveDoesNotRecreateIt() throws Exception {
        var port = outboundFixture(1); await(this::providerDone); conflictBranch(port, false);
        accept(() -> controller.disconnectSyncFolder()); await(this::providerDone);
        assertFalse(port.pending); int scans = port.scans, takes = port.takes;
        automaticSync = true;
        finishChange(controller.beginTokenChange(lifecycleRow().id(), TokenChange.Kind.RESOLVE), null, null, 0);
        await(() -> !lifecycleRow().conflict()); await(this::providerDone);
        assertFalse(port.pending); assertEquals("Local only", controller.dailySyncStatus());
        assertEquals(scans, port.scans); assertEquals(takes, port.takes);
    }
    @Test public void bindingAfterLocalOnlySavesUsesFreshEvidenceDespiteAbsentRetryHint() throws Exception {
        var port = outboundFixture(1); await(this::providerDone);
        accept(() -> controller.disconnectSyncFolder()); await(this::providerDone); automaticSync = true;
        finishChange(controller.beginTokenChange(lifecycleRow().id(), TokenChange.Kind.EDIT), "local", "unbound edit", 0);
        await(this::providerDone); assertFalse(port.pending);
        int scans = port.scans;
        accept(() -> controller.chooseSyncFolder(false, org.totipo.android.sync.PublicationPort.TREE.locator(), 3));
        await(this::providerDone); assertFalse(port.pending);
        accept(() -> controller.sync()); await(this::providerDone);
        assertTrue(port.scans > scans); assertEquals(2, port.objects.size()); assertEquals("", controller.dailySyncStatus());
    }
    @Test public void revokedBindingStaysConfiguredAndLocalChangesRetainRetryHint() throws Exception {
        var port = dailyFixture();
        accept(() -> controller.checkSyncFolder());
        // The provider lane becomes idle before its callback installs binding readiness.
        await(() -> providerDone() && controller.syncView().binding().status()
                == org.totipo.android.sync.SyncFolderBinding.Status.READY);
        assertEquals("Sync folder configured", controller.syncView().message());
        port.permissions.clear(); accept(() -> controller.checkSyncFolder()); await(this::providerDone);
        assertEquals("Folder access needed. Choose sync folder again.", controller.dailySyncStatus());
        assertNotNull(controller.initialTreeUri());
        assertEquals("Folder access needed. Choose sync folder again.", controller.syncView().message());
        finishChange(controller.beginTokenChange(lifecycleRow().id(), TokenChange.Kind.EDIT), "local", "revoked", 0);
        await(this::providerDone); assertTrue(port.pending); assertEquals("Changes not synced", controller.dailySyncStatus());
        assertNotNull(port.stored);
    }
}
