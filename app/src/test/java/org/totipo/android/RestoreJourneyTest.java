package org.totipo.android;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.totipo.android.reconcile.ForegroundVaultCoordinator;
import org.totipo.backup.*;
import static org.junit.Assert.*;

/** Exercises production Application orchestration with queued worker/main delivery and
 * real released Java archive, registry and store. No Android Activity/device claim. */
public final class RestoreJourneyTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final class Queue implements Executor {
        final Deque<Runnable> tasks = new ArrayDeque<>();
        public void execute(Runnable action) { tasks.add(action); }
        Runnable next() { Runnable action = tasks.remove(); action.run(); return action; }
    }
    private final class Harness implements RestoreJourney.Host {
        final AtomicLong clock = new AtomicLong();
        final Queue worker = new Queue(), main = new Queue();
        final VaultRegistry registry;
        final VaultSelection selection = new VaultSelection();
        final RestoreJourney journey;
        BackupRestoreFlow flow;
        boolean busy, foreground = true, available = true;
        int selections, presentations;
        String selected;
        Harness(String fixture, String recovery, VaultRegistry existing) throws Exception {
            registry = existing == null ? new VaultRegistry(temporary.newFolder().toPath()) : existing;
            journey = new RestoreJourney(this, registry, selection, worker, main);
            flow = new BackupRestoreFlow(recovery, clock::get);
            assertTrue(flow.reading()); flow.freeze(() -> new ByteArrayInputStream(BackupRestoreFlowTest.fixture(fixture)));
            assertEquals(BackupRestoreFlow.Stage.PASSWORD, flow.stage());
        }
        public BackupRestoreFlow current() { return flow; }
        public boolean busy() { return busy; }
        public void busy(boolean value) { busy = value; }
        public boolean foregroundUnselected() { return foreground && available; }
        public void changed() { presentations++; }
        public void selected(BackupRestoreFlow origin, String id, boolean restored) {
            assertFalse(busy); assertSame(flow, origin);
            assertTrue(worker.tasks.isEmpty()); // Closure task has actually returned.
            assertTrue(main.tasks.isEmpty());
            selection.beginRestoreMaintenance("f".repeat(32));
            selection.endMaintenance("f".repeat(32)); // No install/close maintenance remains.
            selected = id; selections++; flow = null;
        }
        char[] submit(String name) {
            char[] password = "fixture password".toCharArray();
            assertTrue(journey.submit(flow, password, name)); return password;
        }
        void authenticate() { worker.next(); main.next(); }
        void install() { worker.next(); main.next(); }
        void drain() {
            while (!worker.tasks.isEmpty() || !main.tasks.isEmpty()) {
                if (!worker.tasks.isEmpty()) worker.next();
                if (!main.tasks.isEmpty()) main.next();
            }
        }
    }
    @Test public void oneSubmitAuthenticatesInstallsRetiresThenSelectsLockedAndNormalOpenPromotes() throws Exception {
        var h = new Harness("history", null, null); var origin = h.flow;
        char[] password = h.submit("My restored vault");
        char[] duplicate = "rejected".toCharArray();
        assertFalse(h.journey.submit(origin, duplicate, "second")); assertArrayEquals(new char[duplicate.length], duplicate);
        assertEquals(1, h.worker.tasks.size());
        h.authenticate(); assertArrayEquals(new char[password.length], password);
        assertEquals(BackupRestoreFlow.Stage.INSTALLING, origin.stage()); assertEquals(0, h.selections);
        h.worker.next(); // COMPLETE/INSTALLED and every store resource retired; callback not delivered.
        String id = origin.localId();
        assertEquals(VaultRegistry.RestorePhase.INSTALLED, h.registry.entry(id).restore().phase());
        assertEquals(0, h.selections); assertTrue(h.busy);
        Runnable completion = h.main.next(); // Queues final disposal, still no selection.
        assertTrue(h.busy); assertEquals(0, h.selections);
        completion.run(); // Duplicate install completion must not release disposal admission.
        assertTrue(h.busy); assertEquals(1, h.worker.tasks.size());
        h.worker.next(); assertEquals(0, h.selections);
        Runnable handoff = h.main.next(); handoff.run(); completion.run();
        assertEquals(1, h.selections); assertEquals(1, h.registry.entries().size());
        assertEquals("My restored vault", h.registry.entry(id).label());
        assertEquals(VaultRegistry.State.RESTORING, h.registry.entry(id).state());
        // No detached password reuse or promotion on completion. Normal open still required.
        var owner = new LocalReplicaOwner.Admission().select(h.registry, id);
        var wrong = ForegroundVaultCoordinator.open(owner, "wrong".toCharArray());
        assertNotNull(wrong.failure()); wrong.vault().close();
        assertEquals(VaultRegistry.State.RESTORING, h.registry.entry(id).state());
        var opened = ForegroundVaultCoordinator.open(owner, "fixture password".toCharArray());
        try { assertNull(opened.cause()); assertNull(opened.failure()); assertEquals(VaultRegistry.State.ESTABLISHED, h.registry.entry(id).state()); }
        finally { opened.vault().close(); }
    }
    @Test public void nameValidationPrecedesConsumptionAndAuthenticatedGapsRequireOnlyConditionalReview() throws Exception {
        var h = new Harness("missing-parent", null, null);
        assertNull(h.flow.summary()); assertFalse(h.flow.needsHistoryReview());
        for (String name : List.of("a".repeat(129), "bad\nname", "\ud800")) {
            char[] password = "fixture password".toCharArray();
            assertThrows(IllegalArgumentException.class, () -> h.journey.submit(h.flow, password, name));
            assertArrayEquals(new char[password.length], password);
            assertEquals(BackupRestoreFlow.Stage.PASSWORD, h.flow.stage()); assertTrue(h.worker.tasks.isEmpty());
        }
        h.submit("Limited"); h.authenticate();
        assertTrue(h.flow.needsHistoryReview()); assertTrue(h.registry.entries().isEmpty()); assertFalse(h.busy);
        h.journey.confirm(h.flow); h.journey.confirm(h.flow);
        assertEquals(1, h.worker.tasks.size()); h.drain();
        assertEquals(1, h.selections); assertEquals("Limited", h.registry.entry(h.selected).label());
    }
    private byte[] claimedGaps(String fixture, int gaps) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var input = new java.util.zip.ZipInputStream(new ByteArrayInputStream(BackupRestoreFlowTest.fixture(fixture)));
             var zip = new java.util.zip.ZipOutputStream(output)) {
            java.util.zip.ZipEntry original;
            while ((original = input.getNextEntry()) != null) {
                byte[] bytes = input.readAllBytes();
                if (original.getName().equals("totipo-backup.txt")) bytes = new String(bytes, java.nio.charset.StandardCharsets.US_ASCII)
                        .replaceAll("unresolved-references [0-9]+", "unresolved-references " + gaps).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                var entry = new java.util.zip.ZipEntry(original.getName()); entry.setMethod(java.util.zip.ZipEntry.STORED);
                entry.setTimeLocal(java.time.LocalDateTime.of(1980, 1, 2, 0, 0)); entry.setSize(bytes.length);
                var crc = new java.util.zip.CRC32(); crc.update(bytes); entry.setCrc(crc.getValue());
                zip.putNextEntry(entry); zip.write(bytes); zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
    @Test public void falseManifestGapClaimsNeitherSuppressNorTriggerConditionalReview() throws Exception {
        for (String fixture : List.of("missing-parent", "history")) {
            var h = new Harness(fixture, null, null);
            byte[] zip = claimedGaps(fixture, fixture.equals("missing-parent") ? 0 : 1);
            var structural = BackupArchives.inspect(new ByteArrayInputStream(zip), BackupLimits.DEFAULT);
            assertFalse(structural.sourceAuthenticated());
            assertEquals(fixture.equals("missing-parent") ? 0 : 1, structural.unresolvedReferences());
            h.flow = new BackupRestoreFlow(null, h.clock::get); h.flow.reading();
            h.flow.freeze(() -> new ByteArrayInputStream(zip));
            h.submit(null); h.authenticate();
            if (fixture.equals("missing-parent")) {
                assertTrue(h.flow.summary().sourceAuthenticated()); assertTrue(h.flow.needsHistoryReview());
                assertTrue(h.registry.entries().isEmpty()); h.journey.cancel(h.flow); h.drain();
            } else { assertEquals(BackupRestoreFlow.Stage.INSTALLING, h.flow.stage()); h.drain(); assertEquals(1, h.selections); }
        }
    }
    @Test public void cancelMissingHistoryNeverAllocates() throws Exception {
        var h = new Harness("missing-parent", null, null); h.submit(null); h.authenticate();
        h.journey.cancel(h.flow); h.drain(); assertTrue(h.registry.entries().isEmpty()); assertEquals(0, h.selections);
    }
    @Test public void cancellationBackgroundExpiryAndLostAdmissionAtEachContinuationNeverOpen() throws Exception {
        // After auth worker, after install worker, before/after final disposal worker.
        for (int boundary = 0; boundary < 4; boundary++) for (String stop : List.of("cancel", "background", "expiry", "admission")) {
            var h = new Harness("empty", null, null); h.submit(null);
            h.worker.next();
            if (boundary >= 1) { h.main.next(); h.worker.next(); }
            if (boundary >= 2) h.main.next();
            if (boundary >= 3) h.worker.next();
            switch (stop) {
                case "cancel" -> h.journey.cancel(h.flow);
                case "background" -> { h.foreground = false; h.journey.background(); }
                case "expiry" -> h.clock.set(15 * 60 * 1000);
                case "admission" -> h.available = false;
            }
            h.drain(); assertEquals(stop + " at " + boundary, 0, h.selections);
            if (boundary > 0) {
                var e = h.registry.entries().get(0);
                assertEquals(VaultRegistry.State.RESTORING, e.state());
                assertEquals(VaultRegistry.RestorePhase.INSTALLED, e.restore().phase());
            } else assertTrue(h.registry.entries().isEmpty());
        }
    }
    @Test public void recreationChangesOnlyPresentationAndStaleCompletionCannotSelectNewJourney() throws Exception {
        var h = new Harness("empty", null, null); h.submit(null); h.authenticate(); h.install();
        int before = h.presentations; h.changed(); h.changed(); // Activity observer detach/attach/re-render.
        assertEquals(before + 2, h.presentations); assertEquals(1, h.worker.tasks.size());
        h.drain(); assertEquals(1, h.selections);
        var stale = new Harness("empty", null, null); stale.submit(null); stale.authenticate(); stale.install();
        stale.worker.next(); var old = stale.main.tasks.remove();
        stale.flow = new BackupRestoreFlow(null, stale.clock::get); old.run(); old.run();
        assertEquals(0, stale.selections); assertEquals(BackupRestoreFlow.Stage.PICKING, stale.flow.stage());
    }
    @Test public void retryKeepsNameAndRequiresExactZipAndEstablishedDuplicatesNeedExplicitNavigation() throws Exception {
        var h = new Harness("history", null, null); h.submit("Original name"); h.authenticate();
        h.worker.next(); h.journey.cancel(h.flow); h.drain(); // Durable installed entry, no navigation.
        var id = h.registry.entries().get(0).id();
        var wrong = new Harness("empty", id, h.registry); wrong.submit("ignored"); wrong.drain();
        assertEquals(BackupRestoreFlow.Stage.FAILED, wrong.flow.stage()); assertEquals(0, wrong.selections);
        assertTrue(wrong.flow.notice().contains("exact original ZIP"));
        var retry = new Harness("history", id, h.registry); assertTrue(retry.flow.recovery());
        retry.submit("bad\nignored"); retry.drain();
        assertEquals(1, retry.selections); assertEquals(id, retry.selected); assertEquals("Original name", h.registry.entry(id).label());
        var owner = new LocalReplicaOwner.Admission().select(h.registry, id);
        var opened = ForegroundVaultCoordinator.open(owner, "fixture password".toCharArray());
        assertNull(opened.cause()); opened.vault().close();
        var same = new Harness("history", null, h.registry); same.submit(null); same.drain();
        assertEquals(0, same.selections); assertNotNull(same.flow.duplicate()); assertEquals(1, h.registry.entries().size());
        same.journey.finish(same.flow, id, false); same.drain(); assertEquals(1, same.selections);
    }
    @Test public void conflictingRetainedBytesRemainPartialAndRecoverySurvivesRestart() throws Exception {
        var base = temporary.newFolder().toPath(); var registry = new VaultRegistry(base);
        String id;
        try (var plan = BackupArchives.prepareRestore(new ByteArrayInputStream(BackupRestoreFlowTest.fixture("history")),
                BackupLimits.DEFAULT, "fixture password".toCharArray())) {
            id = registry.reserveRestore("Retained", plan.summary()).id();
            var root = registry.beginRestore(id, plan.summary());
            try (var reservation = org.totipo.storage.nio.NioStoreComposition.reserveRestore(root.getParent(), "store",
                    plan.summary().archiveSha256(), new org.totipo.storage.nio.NioDurability())) {
                assertTrue(reservation.acknowledgeLocation());
            }
            var bytes = BackupRestoreFlowTest.entries("history");
            Files.write(root.resolve("vault"), bytes.get("vault"));
            Files.createDirectory(root.resolve("objects-v1"));
            var object = bytes.keySet().stream().filter(k -> k.startsWith("objects-v1/")).findFirst().get();
            Files.write(root.resolve(object), new byte[]{9});
            var h = new Harness("history", id, registry); h.submit(null); h.drain();
            assertEquals(0, h.selections); assertEquals(BackupRestoreFlow.Stage.FAILED, h.flow.stage());
            assertEquals(RestoreResult.Outcome.PARTIAL, h.flow.result().outcome());
            String notice = h.flow.notice(); h.journey.background(); assertEquals(notice, h.flow.notice());
            assertTrue(notice.contains("Only part of the backup was restored."));
            assertTrue(notice.contains("Keep the app’s data and original backup"));
            assertArrayEquals(new byte[]{9}, Files.readAllBytes(root.resolve(object)));
            var restart = new VaultRegistry(base);
            assertEquals(VaultRegistry.RestorePhase.ATTEMPTED, restart.entry(id).restore().phase());
            assertThrows(IOException.class, () -> restart.select(id));
        }
    }
    @Test public void incompleteTypedOutcomesHaveDistinctPlainPreservationAndRecoveryMessages() {
        var messages = new HashSet<String>();
        for (var outcome : List.of(RestoreResult.Outcome.PARTIAL, RestoreResult.Outcome.UNCERTAIN, RestoreResult.Outcome.REJECTED)) {
            String message = BackupRestoreFlow.incompleteNotice(outcome); assertTrue(messages.add(message));
            assertTrue(message.contains("Keep the app’s data and original backup"));
            assertTrue(message.contains("exact original ZIP and password"));
            assertTrue(message.contains("Resume restore from backup"));
            assertFalse(message.contains(outcome.name())); assertFalse(message.contains("Vault restored."));
        }
        assertThrows(IllegalArgumentException.class, () -> BackupRestoreFlow.incompleteNotice(RestoreResult.Outcome.COMPLETE));
    }
    @Test public void canceledQueuedAuthenticationWipesPasswordAndPickerBackgroundDoesNotRelaunch() throws Exception {
        var h = new Harness("history", null, null); char[] password = h.submit(null);
        h.journey.cancel(h.flow); h.drain(); assertArrayEquals(new char[password.length], password);
        assertTrue(h.registry.entries().isEmpty()); assertEquals(0, h.selections);
        h.flow = new BackupRestoreFlow(null, h.clock::get); h.foreground = false; h.journey.background();
        assertEquals(BackupRestoreFlow.Stage.PICKING, h.flow.stage()); assertTrue(h.worker.tasks.isEmpty());
    }
    @Test public void failedInstalledJournalAcknowledgementDoesNotNavigateAndRestartNeedsRecovery() throws Exception {
        var base = temporary.newFolder().toPath(); new VaultRegistry(base);
        var count = new java.util.concurrent.atomic.AtomicInteger();
        var registry = new VaultRegistry(base, (destination, next, bytes) -> {
            VaultRegistry.atomicWrite(destination, next, bytes);
            if (count.incrementAndGet() == 3) throw new IOException("lost INSTALLED ack");
        });
        var h = new Harness("empty", null, registry); h.submit(null); h.drain();
        assertEquals(0, h.selections); assertEquals(BackupRestoreFlow.Stage.FAILED, h.flow.stage());
        var restart = new VaultRegistry(base); assertEquals(VaultRegistry.State.RESTORING, restart.entries().get(0).state());
        assertNull(VaultUiModel.startupSelection(restart.entries(), restart.lastSelected()));
        assertTrue(h.flow.notice().contains("Keep the app’s data and original backup"));
    }
}
