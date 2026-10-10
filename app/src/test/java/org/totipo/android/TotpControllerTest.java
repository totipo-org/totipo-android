package org.totipo.android;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.totipo.*;
import org.totipo.android.reconcile.*;
import static org.totipo.android.AndroidVaultController.*;
import static org.junit.Assert.*;

public final class TotpControllerTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final ProductControllerFixtures real = new ProductControllerFixtures();
    private final Thread ui = Thread.currentThread();
    private final Queue<Runnable> deliveries = new ConcurrentLinkedQueue<>();
    private final TotpPresentationTest.FakeClock clock = new TotpPresentationTest.FakeClock();
    private final TotpPresentationTest.Timer timer = new TotpPresentationTest.Timer();
    private final TotpPresentationTest.FakeClipboard clipboard = new TotpPresentationTest.FakeClipboard();
    private AndroidVaultController controller;
    private TokenId a, b;
    private int generations;
    private CountDownLatch entered, release;
    private final CountDownLatch initialObservationRead = new CountDownLatch(1);
    private boolean generationError;
    private final org.totipo.android.sync.SyncFolderBindingTest.MemoryPort port = new org.totipo.android.sync.SyncFolderBindingTest.MemoryPort();
    private final Dispatcher dispatcher = new Dispatcher() {
        public void post(Runnable action) { deliveries.add(action); }
        public void assertDispatchThread() { assertSame(ui, Thread.currentThread()); }
        public void assertWorkerThread() {
            assertNotSame(ui, Thread.currentThread());
            // After create assigns the session, the fixture issues no further command
            // until the controller has read its initial subscription replay.
            if (real.session != null) initialObservationRead.countDown();
        }
        public Runnable after(long delay, Runnable action) { return delay == InactivityLock.TIMEOUT_MILLIS ? () -> {} : timer.after(delay, action); }
    };
    @Before public void setup() throws Exception {
        var owner = TestReplicaOwners.create(temporary.newFolder().toPath());
        controller = new AndroidVaultController(owner, dispatcher, new Backend() {
            ForegroundVaultCoordinator.Discovery discover(LocalReplicaOwner owner) throws IOException { return real.discover(owner); }
            ForegroundVaultCoordinator.Creation create(LocalReplicaOwner owner, char[] password) throws IOException {
                var result = real.create(owner, password);
                a = TotpCoordinatorTest.author(real.session, TotpAlgorithm.SHA1, 8, 30, "12345678901234567890");
                b = TotpCoordinatorTest.author(real.session, TotpAlgorithm.SHA1, 8, 30, "12345678901234567890");
                return result;
            }
            ForegroundVaultCoordinator.TotpResult generateTotp(ForegroundVaultCoordinator vault, TokenId id, Instant now,
                                                              ForegroundVaultCoordinator.ObservedToken expected) {
                assertNotSame(ui, Thread.currentThread()); generations++;
                if (generationError) throw new IllegalStateException("Private internal failure");
                var generated = super.generateTotp(vault, id, now, expected);
                if (entered != null) {
                    entered.countDown();
                    try { assertTrue(release.await(20, TimeUnit.SECONDS)); }
                    catch (InterruptedException failure) { throw new AssertionError(failure); }
                }
                return generated;
            }
        }, clock, clipboard, new org.totipo.android.sync.SyncFolderBinding(port));
        await(() -> controller.snapshot().state() == State.NO_LOCAL_VAULT && idle());
        assertTrue(controller.create("M2A disposable".toCharArray()));
        assertTrue(initialObservationRead.await(20, TimeUnit.SECONDS));
        await(() -> controller.snapshot().state() == State.OPEN && idle());
    }
    private void pump() { for (Runnable task; (task = deliveries.poll()) != null;) task.run(); }
    private void await(BooleanSupplier condition) throws Exception {
        long stop = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < stop) {
            pump();
            // Pump can schedule a new observation read; evaluate after dispatch, not before it.
            if (condition.getAsBoolean()) return;
            Thread.sleep(5);
        }
        fail("Timed out waiting for controller condition");
    }
    private boolean idle() {
        synchronized (controller) {
            try {
                var operating = AndroidVaultController.class.getDeclaredField("operating"); operating.setAccessible(true);
                var views = AndroidVaultController.class.getDeclaredField("viewQueued"); views.setAccessible(true);
                var reveal = AndroidVaultController.class.getDeclaredField("revealing"); reveal.setAccessible(true);
                return !operating.getBoolean(controller) && !views.getBoolean(controller) && !reveal.getBoolean(controller);
            } catch (ReflectiveOperationException failed) { throw new AssertionError(failed); }
        }
    }
    private void show(TokenId id) throws Exception {
        await(this::idle); assertTrue(controller.showCode(id));
        await(() -> idle() && controller.snapshot().revealedCode() != null);
        assertNotNull(controller.snapshot().revealedCode());
    }
    @After public void cleanup() throws Exception {
        real.failClose.set(false);
        if (release != null) release.countDown();
        await(this::idle);
        if (controller.snapshot().state() == State.OPEN || controller.snapshot().state() == State.FAILED_CLOSE) {
            assertTrue(controller.lock()); await(() -> idle() && controller.snapshot().state() == State.LOCKED);
        }
        pump();
    }
    @Test public void parsedEnrollmentUsesSameSessionAndOnlyAuthorsAfterTransfer() throws Exception {
        int before = controller.snapshot().view().tokens().size();
        var session = real.session;
        assertTrue(controller.canAddToken());
        try (var cancelled = OtpAuthUriParser.parse("otpauth://totp/Public:account?secret=MY&issuer=Public")) {
            assertEquals(before, controller.snapshot().view().tokens().size());
        }
        assertEquals(before, controller.snapshot().view().tokens().size());
        try (var draft = OtpAuthUriParser.parse("otpauth://totp/Public:account?secret=MY&issuer=Public")) {
            assertTrue(controller.addToken(draft.transfer()));
        }
        await(() -> idle() && controller.snapshot().state() == State.OPEN);
        assertEquals(AddTokenOutcome.Status.ADDED, controller.snapshot().addOutcome().status());
        // Saved acknowledgement precedes asynchronous session observation; controller idle
        // does not mean the newly authored token has reached the rendered snapshot yet.
        await(() -> controller.snapshot().view().tokens().size() == before + 1);
        assertEquals(before + 1, controller.snapshot().view().tokens().size());
        assertNull(controller.snapshot().revealedCode());
        assertSame(session, real.session); assertEquals(1, real.creates); assertEquals(0, real.opens);
    }
    @Test public void parsedEnrollmentRejectedAdmissionWipesTransferredBuffer() throws Exception {
        assertTrue(controller.lock());
        assertFalse(controller.canAddToken());
        try (var draft = OtpAuthUriParser.parse("otpauth://totp/account?secret=MY")) {
            var field = draft.getClass().getDeclaredField("secret"); field.setAccessible(true);
            char[] owned = (char[]) field.get(draft);
            assertFalse(controller.addToken(draft.transfer()));
            assertArrayEquals(new char[owned.length], owned);
        }
        await(() -> idle() && controller.snapshot().state() == State.LOCKED);
        assertFalse(controller.canAddToken()); assertEquals(0, real.opens);
    }
    @Test public void sameSessionOneRevealAndNoCryptoOnTicks() throws Exception {
        var session = real.session; var vault = real.coordinator;
        clock.wall = Instant.ofEpochSecond(31);
        show(a); assertEquals("94287082", controller.snapshot().revealedCode().code());
        for (int i = 0; i < 3; i++) { clock.advance(1000); timer.fire(); pump(); }
        assertEquals(1, generations); assertEquals(1, real.creates); assertEquals(0, real.opens); assertEquals(0, real.closes);
        assertSame(session, real.session); assertSame(vault, real.coordinator);
    }
    @Test public void copyingKeepsRevealedIdentityAndOriginalDeadline() throws Exception {
        clock.wall = Instant.ofEpochSecond(31); show(a);
        var before = controller.snapshot().revealedCode(); Runnable scheduled = timer.task;
        clock.advance(18000); assertTrue(controller.copyShownCode()); pump();
        assertSame(before, controller.snapshot().revealedCode());
        assertEquals(a, controller.snapshot().revealedCode().tokenId());
        assertEquals(11, controller.snapshot().remainingSeconds());
        assertSame(scheduled, timer.task); assertEquals(1, generations);
        clock.advance(11000); timer.fire(); pump();
        assertNull(controller.snapshot().revealedCode()); assertEquals(1, clipboard.clears);
    }
    @Test public void oneAtATimeAndImmediateHide() throws Exception {
        show(a); show(b); assertEquals(b, controller.snapshot().revealedCode().tokenId());
        controller.hideCode(); assertNull(controller.snapshot().revealedCode()); assertNull(timer.task);
        assertEquals(State.OPEN, controller.snapshot().state());
    }
    @Test public void rotationReattachKeepsOriginalDeadlineAndSession() throws Exception {
        show(a); Listener first = ignored -> {}; controller.attach(first); pump(); controller.detach(first);
        clock.advance(400); List<Snapshot> received = new ArrayList<>(); Listener next = received::add;
        controller.attach(next); pump(); assertNotNull(received.get(received.size()-1).revealedCode());
        clock.advance(600); timer.fire(); pump();
        assertNull(received.get(received.size()-1).revealedCode()); assertEquals(0, real.closes);
        controller.detach(next);
    }
    @Test public void reattachAfterExpiryChecksValidityEvenWithoutTimerDelivery() throws Exception {
        show(a); clock.advance(1000);
        List<Snapshot> received = new ArrayList<>(); Listener next = received::add; controller.attach(next); pump();
        assertNull(received.get(received.size()-1).revealedCode()); assertNull(timer.task); controller.detach(next);
    }
    @Test public void lockAdmissionConcealsAndClearsEvenWhenCloseFails() throws Exception {
        show(a); assertTrue(controller.copyShownCode()); pump();
        real.failClose.set(true); assertTrue(controller.lock());
        assertNull(controller.snapshot().revealedCode()); assertNull(controller.snapshot().view()); assertNull(timer.task);
        await(() -> idle() && controller.snapshot().state() == State.FAILED_CLOSE);
        pump(); assertEquals(1, clipboard.clears); assertNull(controller.snapshot().revealedCode());
    }
    @Test public void observedExternalClipboardSurvivesLock() throws Exception {
        show(a); assertTrue(controller.copyShownCode()); clipboard.marker = "external"; clipboard.text = "Other app";
        assertTrue(controller.lock()); await(() -> idle() && controller.snapshot().state() == State.LOCKED);
        pump(); assertEquals("Other app", clipboard.text); assertEquals(0, clipboard.clears);
    }
    @Test public void expiredCopyCannotBeatDelayedTimer() throws Exception {
        show(a); clock.advance(1000); assertFalse(controller.copyShownCode());
        assertNull(controller.snapshot().revealedCode()); assertEquals(0, clipboard.copies);
        clock.advance(60000); timer.fire(); assertNull(controller.snapshot().revealedCode()); assertEquals(1, generations);
    }
    @Test public void refreshConcealsEvenWhenTokenUnchanged() throws Exception {
        show(a); assertTrue(controller.copyShownCode()); assertTrue(controller.refresh());
        assertNull(controller.snapshot().revealedCode());
        await(() -> idle() && controller.snapshot().state() == State.OPEN);
        assertNull(controller.snapshot().revealedCode()); assertEquals(1, clipboard.clears);
        assertEquals(0, real.opens); assertEquals(0, real.closes);
    }
    @Test public void syncAdmissionConcealsWithoutReopeningSession() throws Exception {
        show(a);
        var tree = new org.totipo.android.provider.ProviderSnapshot.Tree("fixture", "content://fixture/root", "root");
        var complete = org.totipo.android.provider.ProviderSnapshot.State.COMPLETE;
        var scan = new org.totipo.android.provider.ProviderSnapshot.Scan("epoch", tree,
                new org.totipo.android.provider.ProviderSnapshot.Listing("epoch", "root", List.of(), complete, List.of()),
                List.of(), complete, List.of());
        assertTrue(controller.sync(scan)); assertNull(controller.snapshot().revealedCode());
        await(() -> idle() && controller.snapshot().state() == State.OPEN);
        assertNull(controller.snapshot().revealedCode()); assertEquals(0, real.opens); assertEquals(0, real.closes);
    }
    @Test public void authoritativeTokenReplacementConceals() throws Exception {
        show(a);
        try (var editor = real.session.state().update(real.session.state().token(a).orElseThrow().heads().get(0))) {
            assertTrue(editor.account("Replaced").save() instanceof SaveResult.Saved);
        }
        await(() -> controller.snapshot().revealedCode() == null && idle()
                && controller.snapshot().view().tokens().stream().anyMatch(t -> t.alternatives().get(0).account().equals("Replaced")));
    }
    private void delayedReveal() throws Exception {
        clock.wall = Instant.ofEpochSecond(31);
        entered = new CountDownLatch(1); release = new CountDownLatch(1);
        assertTrue(controller.showCode(a)); assertTrue(entered.await(10, TimeUnit.SECONDS));
    }
    @Test public void inactivityRetiresVisibleRevealAndOwnedClipboard() throws Exception {
        clock.wall = Instant.ofEpochSecond(31); show(a); assertTrue(controller.copyShownCode());
        clock.elapsed += InactivityLock.TIMEOUT_MILLIS;
        controller.checkInactivity(); assertNull(controller.snapshot().revealedCode());
        await(() -> idle() && controller.snapshot().state() == State.LOCKED);
        pump(); assertNull(clipboard.text); assertNull(controller.snapshot().view());
    }
    @Test public void inactivityRetiresPendingRevealAndRejectsLateResult() throws Exception {
        delayedReveal(); clock.elapsed += InactivityLock.TIMEOUT_MILLIS;
        controller.checkInactivity(); assertEquals(State.LOCKING, controller.snapshot().state());
        release.countDown(); await(() -> idle() && controller.snapshot().state() == State.LOCKED);
        assertNull(controller.snapshot().revealedCode()); assertNull(controller.snapshot().view()); assertEquals(0, clipboard.copies);
    }
    @Test public void lockAdmittedDuringGenerationRejectsAlreadyGeneratedResult() throws Exception {
        List<Snapshot> events = new ArrayList<>(); controller.attach(events::add); pump();
        delayedReveal(); pump(); int before = events.size();
        assertTrue(controller.lock()); assertEquals(State.LOCKING, controller.snapshot().state());
        release.countDown(); await(() -> idle() && controller.snapshot().state() == State.LOCKED);
        assertTrue(events.subList(before, events.size()).stream().allMatch(e -> e.revealedCode() == null));
        assertEquals(0, clipboard.copies);
    }
    @Test public void addAdmittedDuringGenerationKeepsRealBusyAndRejectsReveal() throws Exception {
        delayedReveal(); assertTrue(controller.canAddToken());
        try (var draft = OtpAuthUriParser.parse("otpauth://totp/new?secret=MY")) {
            assertTrue(controller.addToken(draft.transfer()));
        }
        assertEquals(State.BUSY, controller.snapshot().state()); assertFalse(controller.canAddToken());
        release.countDown(); await(() -> idle() && controller.snapshot().state() == State.OPEN);
        assertEquals(AddTokenOutcome.Status.ADDED, controller.snapshot().addOutcome().status());
        assertNull(controller.snapshot().revealedCode());
        // Add acknowledgement/worker completion can precede the asynchronous session
        // observation. Await the rendered token inventory, not just controller idle.
        await(() -> controller.snapshot().view().tokens().size() == 3);
        assertEquals(3, controller.snapshot().view().tokens().size());
        assertNull(controller.snapshot().revealedCode());
    }
    @Test public void syncEnabledAndAdmittedDuringGenerationUsesExistingWorkerOrdering() throws Exception {
        assertTrue(controller.chooseSyncFolder(false, "content://fixture/tree/a", 3));
        await(() -> idle() && controller.syncView().binding().status() == org.totipo.android.sync.SyncFolderBinding.Status.READY);
        assertTrue(controller.canSync()); boolean add = controller.canAddToken();
        delayedReveal(); assertTrue(controller.canSync()); assertEquals(add, controller.canAddToken());
        String message = controller.snapshot().message();
        release.countDown(); await(() -> idle() && controller.snapshot().revealedCode() != null);
        assertTrue(controller.canSync()); assertEquals(add, controller.canAddToken());
        assertEquals(message, controller.snapshot().message());
        controller.hideCode(); delayedReveal(); assertTrue(controller.canSync());
        int probes = port.probes;
        assertTrue(controller.sync()); assertFalse(controller.canSync());
        release.countDown(); await(() -> idle() && controller.canSync() && port.probes > probes);
        assertNull(controller.snapshot().revealedCode()); assertTrue(controller.canAddToken());
    }
    @Test public void editAdmittedDuringGenerationRetiresResult() throws Exception {
        delayedReveal(); var change = controller.beginTokenChange(a, TokenChange.Kind.EDIT); assertNotNull(change);
        assertTrue(controller.confirmTokenChange(change, "Updated", "Account", 0));
        release.countDown(); await(() -> idle() && controller.snapshot().state() == State.OPEN);
        assertEquals(TokenChange.Result.SAVED, controller.tokenChangeResult()); assertNull(controller.snapshot().revealedCode());
        await(() -> controller.snapshot().view().tokens().stream().anyMatch(t -> t.alternatives().get(0).issuer().equals("Updated")));
    }
    @Test public void deleteAdmittedDuringGenerationRetiresResult() throws Exception {
        delayedReveal(); var change = controller.beginTokenChange(a, TokenChange.Kind.DELETE); assertNotNull(change);
        assertTrue(controller.confirmTokenChange(change, null, null, 0));
        release.countDown(); await(() -> idle() && controller.snapshot().state() == State.OPEN);
        assertEquals(TokenChange.Result.SAVED, controller.tokenChangeResult()); assertNull(controller.snapshot().revealedCode());
    }
    @Test public void conflictBeforeDelayedResultRetiresPresentation() throws Exception {
        delayedReveal(); var captured = real.session.state(); var head = captured.token(a).orElseThrow().heads().get(0);
        try (var first = captured.update(head); var second = captured.update(head)) {
            assertTrue(first.account("Branch A").save() instanceof SaveResult.Saved);
            assertTrue(second.account("Branch B").save() instanceof SaveResult.Saved);
        }
        TotpCoordinatorTest.await(real.session, state -> state.token(a).orElseThrow().hasConflict());
        var dirty = AndroidVaultController.class.getDeclaredField("dirty"); dirty.setAccessible(true);
        await(() -> { synchronized (controller) { try { return dirty.getBoolean(controller); } catch (IllegalAccessException failure) { throw new AssertionError(failure); } } });
        release.countDown(); await(this::idle); assertNull(controller.snapshot().revealedCode());
        await(() -> controller.snapshot().view().tokens().stream().anyMatch(t -> t.id().equals(a) && t.conflict()));
        assertFalse(controller.showCode(a));
    }
    @Test public void reattachDuringDelayedRevealDoesNotGenerateAgainOrEnterBusy() throws Exception {
        delayedReveal(); List<Snapshot> states = new ArrayList<>(); Listener listener = states::add;
        controller.attach(listener); pump(); assertEquals(State.OPEN, states.get(0).state());
        controller.detach(listener); controller.attach(listener); pump();
        release.countDown(); await(() -> idle() && controller.snapshot().revealedCode() != null);
        assertEquals(1, generations); assertTrue(states.stream().allMatch(e -> e.state() == State.OPEN));
        controller.detach(listener);
    }
    @Test public void copyDoesNotDeliverOrChangeNormalScreenState() throws Exception {
        clock.wall = Instant.ofEpochSecond(31); show(a);
        List<Snapshot> states = new ArrayList<>(); Listener listener = states::add; controller.attach(listener); pump(); states.clear();
        var before = controller.snapshot(); assertTrue(controller.copyShownCode()); pump();
        assertTrue(states.isEmpty()); assertSame(before, controller.snapshot()); assertTrue(controller.canAddToken());
        controller.detach(listener);
    }
    @Test public void queuedRevealYieldsPendingSlotToRealAdd() throws Exception {
        var field = AndroidVaultController.class.getDeclaredField("worker"); field.setAccessible(true);
        var worker = (ThreadPoolExecutor) field.get(controller);
        var blocked = new CountDownLatch(1); var unblock = new CountDownLatch(1);
        worker.execute(() -> { blocked.countDown(); try { assertTrue(unblock.await(10, TimeUnit.SECONDS)); }
            catch (InterruptedException failure) { throw new AssertionError(failure); } });
        try {
            assertTrue(blocked.await(10, TimeUnit.SECONDS)); assertTrue(controller.showCode(a));
            assertTrue(controller.canAddToken()); assertEquals(1, worker.getQueue().size());
            try (var draft = OtpAuthUriParser.parse("otpauth://totp/new?secret=MY")) { assertTrue(controller.addToken(draft.transfer())); }
            assertEquals(State.BUSY, controller.snapshot().state());
        } finally { unblock.countDown(); }
        await(() -> idle() && controller.snapshot().state() == State.OPEN);
        assertEquals(0, generations); assertEquals(AddTokenOutcome.Status.ADDED, controller.snapshot().addOutcome().status());
        assertNull(controller.snapshot().revealedCode());
    }
    @Test public void revealScreenSequence() throws Exception {
        entered = new CountDownLatch(1); release = new CountDownLatch(1);
        var before = controller.snapshot(); assertTrue(controller.canAddToken());
        assertTrue(controller.showCode(a)); assertTrue(entered.await(10, TimeUnit.SECONDS));
        assertEquals(State.OPEN, controller.snapshot().state());
        assertEquals(before.message(), controller.snapshot().message());
        assertTrue(controller.canAddToken()); assertFalse(controller.canSync());
        assertSame(before.view(), controller.snapshot().view());
        release.countDown(); await(() -> idle() && controller.snapshot().revealedCode() != null);
        assertTrue(controller.canAddToken()); assertNotNull(controller.snapshot().revealedCode());
        assertEquals(before.message(), controller.snapshot().message());
    }
    @Test public void revealOwnershipAndHideRevokeInflightReveal() throws Exception {
        entered = new CountDownLatch(1); release = new CountDownLatch(1);
        assertTrue(controller.showCode(a)); assertTrue(entered.await(10, TimeUnit.SECONDS));
        assertFalse(controller.showCode(b)); assertFalse(controller.copyShownCode());
        controller.hideCode(); release.countDown();
        await(() -> idle() && controller.snapshot().state() == State.OPEN);
        assertNull(controller.snapshot().revealedCode());
        assertTrue(controller.lock()); assertFalse(controller.showCode(a));
        await(() -> idle() && controller.snapshot().state() == State.LOCKED); assertNull(controller.snapshot().revealedCode());
    }
    @Test public void stateReplacementBeforeControllerPublicationRejectsReveal() throws Exception {
        entered = new CountDownLatch(1); release = new CountDownLatch(1);
        assertTrue(controller.showCode(a)); assertTrue(entered.await(10, TimeUnit.SECONDS));
        try (var editor = real.session.state().update(real.session.state().token(a).orElseThrow().heads().get(0))) {
            assertTrue(editor.account("New state").save() instanceof SaveResult.Saved);
        }
        TotpCoordinatorTest.await(real.session, s -> s.token(a).orElseThrow().alternatives().get(0).descriptor().account().equals("New state"));
        // Wait for the controller's authoritative observation signal before releasing generation.
        var epoch = AndroidVaultController.class.getDeclaredField("dirty"); epoch.setAccessible(true);
        await(() -> { synchronized (controller) { try { return epoch.getBoolean(controller); } catch (IllegalAccessException e) { throw new AssertionError(e); } } });
        release.countDown(); await(() -> idle() && controller.snapshot().state() == State.OPEN);
        assertNull(controller.snapshot().revealedCode());
    }
    @Test public void safeGenerationFailureRetainsOpenSession() throws Exception {
        generationError = true; assertTrue(controller.showCode(a));
        await(() -> idle() && controller.snapshot().state() == State.OPEN);
        assertNull(controller.snapshot().revealedCode()); assertEquals(0, real.closes);
        assertFalse(controller.snapshot().message().contains("Private internal"));
    }
    @Test public void revealedSnapshotContainsOnlyDetachedFieldsAndRedactsToString() throws Exception {
        show(a); var snapshot = controller.snapshot(); assertFalse(snapshot.toString().contains("94287082"));
        assertEquals(Set.of("tokenId", "code", "validFrom", "validUntil", "digits"),
                Arrays.stream(RevealedTotp.class.getRecordComponents()).map(c -> c.getName()).collect(java.util.stream.Collectors.toSet()));
        for (var component : RevealedTotp.class.getRecordComponents()) {
            assertFalse(component.getType().getName().contains("Session")); assertFalse(component.getType().getName().contains("Alternative"));
        }
    }
}
