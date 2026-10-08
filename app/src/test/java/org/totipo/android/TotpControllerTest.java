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
    private boolean generationError;
    private final Dispatcher dispatcher = new Dispatcher() {
        public void post(Runnable action) { deliveries.add(action); }
        public void assertDispatchThread() { assertSame(ui, Thread.currentThread()); }
        public void assertWorkerThread() { assertNotSame(ui, Thread.currentThread()); }
        public Runnable after(long delay, Runnable action) { return timer.after(delay, action); }
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
                if (entered != null) {
                    entered.countDown();
                    try { assertTrue(release.await(20, TimeUnit.SECONDS)); }
                    catch (InterruptedException failure) { throw new AssertionError(failure); }
                }
                if (generationError) throw new IllegalStateException("Private internal failure");
                return super.generateTotp(vault, id, now, expected);
            }
        }, clock, clipboard);
        await(() -> controller.snapshot().state() == State.NO_LOCAL_VAULT && idle());
        assertTrue(controller.create("M2A disposable".toCharArray()));
        await(() -> controller.snapshot().state() == State.OPEN && idle());
    }
    private void pump() { for (Runnable task; (task = deliveries.poll()) != null;) task.run(); }
    private void await(BooleanSupplier condition) throws Exception {
        long stop = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!condition.getAsBoolean() && System.nanoTime() < stop) { pump(); Thread.sleep(5); }
        pump(); assertTrue(condition.getAsBoolean());
    }
    private boolean idle() {
        synchronized (controller) {
            try {
                var operating = AndroidVaultController.class.getDeclaredField("operating"); operating.setAccessible(true);
                var views = AndroidVaultController.class.getDeclaredField("viewQueued"); views.setAccessible(true);
                return !operating.getBoolean(controller) && !views.getBoolean(controller);
            } catch (ReflectiveOperationException failed) { throw new AssertionError(failed); }
        }
    }
    private void show(TokenId id) throws Exception {
        await(this::idle); assertTrue(controller.showCode(id));
        await(() -> idle() && controller.snapshot().state() == State.OPEN);
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
    @Test public void sameSessionOneRevealAndNoCryptoOnTicks() throws Exception {
        var session = real.session; var vault = real.coordinator;
        clock.wall = Instant.ofEpochSecond(31);
        show(a); assertEquals("94287082", controller.snapshot().revealedCode().code());
        for (int i = 0; i < 3; i++) { clock.advance(1000); timer.fire(); pump(); }
        assertEquals(1, generations); assertEquals(1, real.creates); assertEquals(0, real.opens); assertEquals(0, real.closes);
        assertSame(session, real.session); assertSame(vault, real.coordinator);
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
    @Test public void busyAdmissionAndHideRevokeInflightReveal() throws Exception {
        entered = new CountDownLatch(1); release = new CountDownLatch(1);
        assertTrue(controller.showCode(a)); assertTrue(entered.await(10, TimeUnit.SECONDS));
        assertFalse(controller.showCode(b)); assertFalse(controller.copyShownCode()); assertFalse(controller.lock());
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
