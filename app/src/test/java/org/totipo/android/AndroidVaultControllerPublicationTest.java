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

/** Real controller Publication regressions; isolated fixture per test. */
public final class AndroidVaultControllerPublicationTest extends AndroidVaultControllerFixture {
    @Test public void providerCompletionWaitIncludesWorkerIdentityClassification() throws Exception {
        var port = startBound();
        accept(() -> controller.create(password())); state(State.OPEN); await(this::providerDone);
        var tree = org.totipo.android.sync.PublicationPort.TREE;
        port.snapshot = new org.totipo.android.provider.ProviderSnapshot.Scan("e", tree,
                new org.totipo.android.provider.ProviderSnapshot.Listing("e", tree.rootId(), List.of(),
                        org.totipo.android.provider.ProviderSnapshot.State.COMPLETE, List.of()),
                List.of(), org.totipo.android.provider.ProviderSnapshot.State.COMPLETE, List.of());
        var scanned = new AtomicBoolean();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        port.onScan = () -> scanned.set(true);
        port.onGrants = () -> {
            // providerReturned clears providerActive before this worker-side recheck.
            if (scanned.compareAndSet(true, false)) block(entered, release);
        };
        try {
            accept(() -> controller.importProviderChanges());
            await(() -> entered.getCount() == 0);
            assertEquals("Importing…", controller.syncView().message());
            assertFalse("Transport returned, but identity classification is still running", providerDone());
        } finally { release.countDown(); }
        await(this::providerDone);
        assertEquals("Sync folder has no Totipo vault.", controller.syncView().message());
    }
    @Test public void identityFailureStatusesKeepReadyAndBlockBothControllerDirections() throws Exception {
        var port = outboundFixture(1); var session = real.session;
        byte[] canonical = port.vaultBytes.clone();
        for (String scenario : List.of("missing", "short", "oversized", "magic", "version", "different", "unavailable", "duplicate", "duplicateDifferent", "directory", "loading")) {
            port.snapshotTransform = scan -> {
                var original = scan.vaultCandidates().get(0);
                var doc = original.document();
                var rows = new ArrayList<>(scan.root().rows());
                byte[] bytes = canonical.clone();
                var state = org.totipo.android.provider.ProviderSnapshot.ByteState.PRESENT;
                var rootState = org.totipo.android.provider.ProviderSnapshot.State.COMPLETE;
                switch (scenario) {
                    case "missing" -> rows.remove(doc);
                    case "short" -> bytes = new byte[86];
                    case "oversized" -> bytes = new byte[88];
                    case "magic" -> bytes[0] ^= 1;
                    case "version" -> bytes[10] = 2;
                    case "different", "duplicateDifferent" -> bytes[86] ^= 1;
                    case "unavailable" -> state = org.totipo.android.provider.ProviderSnapshot.ByteState.UNAVAILABLE;
                    case "duplicate" -> rows.add(doc);
                    case "directory" -> {
                        rows.remove(doc);
                        doc = new org.totipo.android.provider.ProviderSnapshot.Document(doc.tree(), doc.locator(), doc.id(), doc.parentId(),
                                "vault", "vnd.android.document/directory", null, null); rows.add(doc);
                    }
                    case "loading" -> rootState = org.totipo.android.provider.ProviderSnapshot.State.INCOMPLETE_LOADING;
                }
                var candidates = new ArrayList<org.totipo.android.provider.ProviderSnapshot.Bytes>();
                if (!scenario.equals("missing")) candidates.add(new org.totipo.android.provider.ProviderSnapshot.Bytes(scan.epoch(), doc, 87,
                        state, bytes, org.totipo.android.provider.ProviderSnapshot.Issue.NONE));
                if (scenario.equals("duplicate") || scenario.equals("duplicateDifferent")) {
                    if (scenario.equals("duplicateDifferent")) rows.add(doc);
                    candidates.add(original);
                }
                return new org.totipo.android.provider.ProviderSnapshot.Scan(scan.epoch(), scan.tree(),
                        new org.totipo.android.provider.ProviderSnapshot.Listing(scan.epoch(), scan.tree().rootId(), rows, rootState, List.of()),
                        scan.directories(), scan.state(), scan.issues(), candidates);
            };
            String expected = scenario.equals("missing") ? "Sync folder has no Totipo vault."
                    : scenario.equals("different") ? "Sync folder belongs to a different Totipo vault."
                    : "Sync folder vault cannot be verified.";
            for (BooleanSupplier operation : List.<BooleanSupplier>of(controller::importProviderChanges, controller::publishLocalChanges)) {
                accept(operation); await(this::providerDone);
                assertEquals(scenario, expected, controller.syncView().message());
                assertEquals(org.totipo.android.sync.SyncFolderBinding.Status.READY, controller.syncView().binding().status());
                assertEquals(0, port.creates); assertEquals(0, port.outputs); assertTrue(port.objects.isEmpty());
                assertEquals(1, controller.snapshot().view().tokens().size()); assertSame(session, real.session);
                assertEquals(1, real.creates); assertEquals(0, real.opens);
                assertArrayEquals(canonical, Files.readAllBytes(localRoot().resolve("vault")));
            }
        }
    }
    @Test public void outboundExactPostflightRetryPreservesSessionStoreAndRevealedCode() throws Exception {
        var port = outboundFixture(); var session = real.session; var coordinator = real.coordinator;
        int refreshes = real.refreshes;
        accept(() -> controller.showCode(controller.snapshot().view().tokens().get(0).id()));
        await(() -> controller.snapshot().revealedCode() != null);
        var shown = controller.snapshot().revealedCode();
        accept(() -> controller.publishLocalChanges()); await(this::providerDone);
        assertEquals("Local changes published", controller.syncView().message());
        assertEquals(3, port.creates); assertEquals(3, port.outputs);
        assertEquals(shown, controller.snapshot().revealedCode());
        assertSame(session, real.session); assertSame(coordinator, real.coordinator); assertEquals(refreshes, real.refreshes);
        assertEquals(1, real.creates); assertEquals(0, real.opens); assertEquals(0, real.closes);
        accept(() -> controller.publishLocalChanges()); await(this::providerDone);
        assertEquals("No local changes to publish", controller.syncView().message()); assertEquals(3, port.creates);
    }
    @Test public void uncertainWriteRetryFreshPreflightAvoidsDuplicateAndExceptionCanBeVerified() throws Exception {
        var port = outboundFixture(); port.fault = "close";
        accept(() -> controller.publishLocalChanges()); await(this::providerDone);
        assertTrue(controller.syncView().message().startsWith("Publication uncertain")); assertEquals(1, port.creates);
        port.fault = "";
        accept(() -> controller.publishLocalChanges()); await(this::providerDone);
        assertEquals("Local changes published", controller.syncView().message()); assertEquals(3, port.creates);
        accept(() -> controller.publishLocalChanges()); await(this::providerDone);
        assertEquals("No local changes to publish", controller.syncView().message()); assertEquals(3, port.creates);
    }
    @Test public void uncertainButPersistedSingleObjectRetryDoesNoCreateAndFreshEvidenceCanVerifyCloseFailure() throws Exception {
        var port = outboundFixture(1);
        var firstPort = port;
        port.onScan = () -> { if (firstPort.scans == 2) throw new IllegalStateException("postflight unavailable"); };
        accept(() -> controller.publishLocalChanges()); await(this::providerDone);
        assertTrue(controller.syncView().message().startsWith("Publication uncertain")); assertEquals(1, port.creates);
        port.onScan = () -> {};
        accept(() -> controller.publishLocalChanges()); await(this::providerDone);
        assertEquals("No local changes to publish", controller.syncView().message()); assertEquals(1, port.creates);
        lock(); controller.shutdown();
        port = outboundFixture(1); port.fault = "close";
        accept(() -> controller.publishLocalChanges()); await(this::providerDone);
        assertEquals("Local changes published", controller.syncView().message()); assertEquals(1, port.creates);
    }
    @Test public void explicitReadBackFailureRemainsUncertainDespiteExactNamespaceAndRetryDoesNotDuplicate() throws Exception {
        for (String fault : List.of("different", "unavailable")) {
            var port = outboundFixture(1); port.fault = fault;
            accept(() -> controller.publishLocalChanges()); await(this::providerDone);
            assertTrue(controller.syncView().message().startsWith("Publication uncertain")); assertEquals(1, port.creates);
            port.fault = "";
            accept(() -> controller.publishLocalChanges()); await(this::providerDone);
            assertEquals("No local changes to publish", controller.syncView().message()); assertEquals(1, port.creates);
            lock(); controller.shutdown();
        }
    }
    @Test public void noSuccessBeforeFreshPostflightReturns() throws Exception {
        var port = outboundFixture(1);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        port.onScan = () -> { if (port.scans == 2) block(entered, release); };
        accept(() -> controller.publishLocalChanges()); assertTrue(entered.await(10, TimeUnit.SECONDS));
        try {
            assertEquals(1, port.creates); assertEquals("Publishing…", controller.syncView().message());
            assertEquals(State.OPEN, controller.snapshot().state()); assertEquals(1L, release.getCount());
        } finally { release.countDown(); }
        await(this::providerDone); assertEquals("Local changes published", controller.syncView().message());
    }
    @Test public void outboundIncompleteReadOnlyCapabilityAndLocalInvalidNeverMutate() throws Exception {
        var port = outboundFixture();
        for (var state : org.totipo.android.provider.ProviderSnapshot.State.values()) if (state != org.totipo.android.provider.ProviderSnapshot.State.COMPLETE) {
            port.coverage = state;
            accept(() -> controller.publishLocalChanges()); await(this::providerDone); assertEquals(0, port.creates);
            if (!controller.canPublishLocalChanges()) { port.coverage = org.totipo.android.provider.ProviderSnapshot.State.COMPLETE;
                accept(() -> controller.checkSyncFolder()); await(this::providerDone); }
        }
        port.coverage = org.totipo.android.provider.ProviderSnapshot.State.COMPLETE;
        port.permissions.put(port.stored.uri(), new org.totipo.android.sync.SyncFolderBinding.Grants(true, false));
        accept(() -> controller.checkSyncFolder()); await(this::providerDone);
        assertTrue(controller.canImportProviderChanges()); assertFalse(controller.canPublishLocalChanges());
        port.permissions.put(port.stored.uri(), new org.totipo.android.sync.SyncFolderBinding.Grants(true, true));
        accept(() -> controller.checkSyncFolder()); await(this::providerDone);
        // Test-only corruption of disposable fixture, never production filesystem access.
        try (var paths = Files.list(localRoot().resolve("objects-v1"))) { Files.write(paths.filter(p -> p.getFileName().toString().matches("[0-9a-f]{64}")).findFirst().orElseThrow(), new byte[1024]); }
        accept(() -> controller.publishLocalChanges()); await(this::providerDone);
        assertTrue(controller.syncView().message(), controller.syncView().message().startsWith("Local publication source invalid")); assertEquals(0, port.creates);
    }
    @Test public void outboundBlockedCreateWriteLockAndBindingChangeRemainResponsiveAndBounded() throws Exception {
        for (String phase : List.of("create", "write")) for (String action : List.of("lock", "disconnect", "replace")) {
            var port = outboundFixture(); int refreshes = real.refreshes;
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            Runnable blocked = () -> block(entered, release);
            if (phase.equals("create")) port.onCreate = blocked; else port.onWrite = blocked;
            accept(() -> controller.publishLocalChanges()); assertTrue(entered.await(10, TimeUnit.SECONDS));
            try {
                await(this::idle); assertEquals(State.OPEN, controller.snapshot().state());
                assertEquals("Publishing…", controller.syncView().message());
                for (int i = 0; i < 100; i++) { assertFalse(controller.publishLocalChanges()); assertFalse(controller.importProviderChanges()); assertFalse(controller.checkSyncFolder()); }
                var laneField = AndroidVaultController.class.getDeclaredField("providerIo"); laneField.setAccessible(true);
                var lane = laneField.get(controller); var executorField = lane.getClass().getDeclaredField("executor"); executorField.setAccessible(true);
                var executor = (ThreadPoolExecutor)executorField.get(lane);
                assertEquals(1, executor.getLargestPoolSize()); assertEquals(0, executor.getQueue().size());
                // Detached batch does not block new local authorship.
                add(enrollment("public", "later", org.totipo.TotpAlgorithm.SHA1, 6, 30, rfcSecret()), AddTokenOutcome.Status.ADDED);
                if (action.equals("lock")) { lock(); accept(() -> controller.unlock(password())); state(State.OPEN); }
                else if (action.equals("disconnect")) { accept(() -> controller.disconnectSyncFolder()); await(this::idle); assertNull(controller.initialTreeUri()); }
                else { accept(() -> controller.chooseSyncFolder(false, "content://fixture/tree/b", 3)); await(this::idle); }
                assertEquals(1L, release.getCount());
            } finally { release.countDown(); }
            await(this::providerDone); assertEquals(1, port.creates); assertEquals(refreshes, real.refreshes);
            assertFalse(controller.syncView().message().contains("Local changes published"));
            if (action.equals("replace")) assertEquals("content://fixture/tree/b", controller.initialTreeUri());
            lock(); controller.shutdown();
        }
    }
}
