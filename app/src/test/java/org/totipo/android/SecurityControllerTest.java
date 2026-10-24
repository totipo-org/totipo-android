package org.totipo.android;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.totipo.android.reconcile.*;
import static org.totipo.android.AndroidVaultController.*;

/** Real Java session ownership with deterministic platform crypto/prompt/time seams. */
public class SecurityControllerTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    final ProductControllerFixtures real = new ProductControllerFixtures();
    final BlockingQueue<Runnable> ui = new LinkedBlockingQueue<>();
    final List<Job> jobs = new ArrayList<>();
    long now; int opens;
    AndroidVaultController controller;
    BiometricUnlock biometric;
    final MemoryCredentials credentials = new MemoryCredentials();
    final FakePrompt prompt = new FakePrompt();
    static class Job { long due; Runnable action; boolean cancelled; Job(long due, Runnable action) { this.due = due; this.action = action; } }
    Runnable after(long delay, Runnable action) {
        synchronized (jobs) { var job = new Job(now + delay, action); jobs.add(job); return () -> job.cancelled = true; }
    }
    void advance(long millis) {
        now += millis;
        List<Job> due;
        synchronized (jobs) { due = jobs.stream().filter(j -> !j.cancelled && j.due <= now).toList(); }
        for (var job : due) { job.cancelled = true; job.action.run(); }
    }
    @Before public void setup() throws Exception {
        var owner = TestReplicaOwners.create(temporary.newFolder().toPath());
        controller = new AndroidVaultController(owner, new Dispatcher() {
            public void post(Runnable action) { ui.add(action); }
            public void assertDispatchThread() { }
            public void assertWorkerThread() { }
            public Runnable after(long delay, Runnable action) { return SecurityControllerTest.this.after(delay, action); }
        }, new Backend() {
            ForegroundVaultCoordinator.Discovery discover(LocalReplicaOwner owner) throws IOException { return real.discover(owner); }
            ForegroundVaultCoordinator.Creation create(LocalReplicaOwner owner, char[] password) throws IOException { return real.create(owner, password); }
            ForegroundVaultCoordinator.Opening open(LocalReplicaOwner owner, char[] password) throws IOException { opens++; return real.open(owner, password); }
        }, new TotpPresentation.Time() {
            public Instant wall() { return Instant.ofEpochSecond(59); }
            public long elapsedMillis() { return now; }
        }, null);
        credentials.local = controller.localId();
        biometric = new BiometricUnlock(controller, credentials, prompt, this::after);
        await(() -> controller.snapshot().state() == State.NO_LOCAL_VAULT && idle());
        assertTrue(controller.create(password())); awaitOpen();
    }
    char[] password() { return new char[]{'s','e','c','u','r','e','\u00e9'}; }
    void pump() { for (Runnable next; (next = ui.poll()) != null;) next.run(); }
    boolean idle() {
        try { var f = AndroidVaultController.class.getDeclaredField("operating"); f.setAccessible(true); return !f.getBoolean(controller); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    void await(BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        do { pump(); if (condition.getAsBoolean()) return; Runnable next = ui.poll(100, TimeUnit.MILLISECONDS); if (next != null) next.run(); } while (System.nanoTime() < end);
        fail("Timed out: " + controller.snapshot());
    }
    void awaitOpen() throws Exception { await(() -> controller.snapshot().state() == State.OPEN && idle()); }
    void lock() throws Exception { assertTrue(controller.lock()); awaitLocked(); }
    void awaitLocked() throws Exception { await(() -> controller.snapshot().state() == State.LOCKED && idle()); assertNull(controller.snapshot().view()); }
    void enroll() throws Exception {
        lock(); assertTrue(controller.unlockAndEnroll(password(), biometric::enroll));
        await(() -> prompt.result != null); prompt.succeed(); awaitOpen(); assertTrue(credentials.configured());
    }
    @After public void cleanup() throws Exception {
        biometric.close();
        if (controller.snapshot().state() == State.OPEN || controller.snapshot().state() == State.BUSY) lock();
        controller.shutdown();
    }
    @Test public void automaticOfferOncePerEpisodeCancelExplicitRetryAndNewLock() throws Exception {
        enroll(); lock(); int before = prompt.prompts;
        biometric.automaticUnlock(); assertEquals(before + 1, prompt.prompts);
        prompt.cancel(); biometric.automaticUnlock(); biometric.automaticUnlock();
        assertEquals(before + 1, prompt.prompts); assertEquals(State.LOCKED, controller.snapshot().state());
        biometric.unlock(); assertEquals(before + 2, prompt.prompts); prompt.succeed(); awaitOpen();
        lock(); biometric.automaticUnlock(); assertEquals(before + 3, prompt.prompts);
    }
    @Test public void automaticUnavailableOrUnenrolledOpportunityDoesNotLoop() throws Exception {
        lock(); biometric.automaticUnlock(); assertEquals(0, prompt.prompts);
        assertFalse(controller.claimAutomaticBiometricUnlock());
        assertTrue(controller.unlock(password())); awaitOpen(); enroll(); lock();
        credentials.strong = false; int before = prompt.prompts; biometric.automaticUnlock();
        credentials.strong = true; biometric.automaticUnlock(); assertEquals(before, prompt.prompts);
        biometric.unlock(); assertEquals(before + 1, prompt.prompts);
    }
    @Test public void automaticPreparationFailureAndFailedAuthenticationDoNotRearm() throws Exception {
        enroll(); lock(); credentials.invalid = true; int before = prompt.prompts;
        biometric.automaticUnlock(); credentials.invalid = false; biometric.automaticUnlock();
        assertEquals(before, prompt.prompts);
        credentials.wrongPassword = true; biometric.unlock(); prompt.succeed(); awaitLocked();
        biometric.automaticUnlock(); assertEquals(before + 1, prompt.prompts);
        credentials.wrongPassword = false; biometric.unlock(); prompt.succeed(); awaitOpen();
    }
    @Test public void automaticAttemptSurvivesHelperRecreationAndForegroundTransitions() throws Exception {
        enroll(); lock(); biometric.automaticUnlock(); int before = prompt.prompts;
        var late = prompt.result; biometric.close();
        biometric = new BiometricUnlock(controller, credentials, prompt, this::after);
        biometric.automaticUnlock(); controller.foregroundChanged(false); controller.foregroundChanged(true);
        biometric.automaticUnlock(); assertEquals(before, prompt.prompts);
        late.authorized(); assertEquals(State.LOCKED, controller.snapshot().state());
        biometric.unlock(); prompt.succeed(); awaitOpen();
        controller.foregroundChanged(false); now += InactivityLock.TIMEOUT_MILLIS;
        controller.foregroundChanged(true); awaitLocked(); biometric.automaticUnlock();
        assertEquals(before + 2, prompt.prompts);
    }
    @Test public void retirementRevokesAutomaticPromptAndNeverRearmsIt() throws Exception {
        enroll(); lock(); biometric.automaticUnlock(); var late = prompt.result; int before = opens;
        controller.retire(null); late.authorized(); biometric.automaticUnlock();
        assertEquals(before, opens); assertFalse(controller.claimAutomaticBiometricUnlock());
        await(() -> controller.retirement() == Retirement.SUCCESS);
    }
    @Test public void manualPasswordDoesNotEnrollAndWipesInput() throws Exception {
        lock(); char[] input = password(); assertTrue(controller.unlock(input)); awaitOpen();
        assertArrayEquals(new char[input.length], input); assertFalse(credentials.configured()); assertEquals(1, opens);
    }
    @Test public void explicitEnrollmentAndBiometricReopenUseOrdinaryPasswordOpen() throws Exception {
        enroll(); assertEquals(1, opens); lock(); biometric.unlock(); assertEquals(State.LOCKED, controller.snapshot().state());
        prompt.succeed(); awaitOpen(); assertEquals(2, opens); assertArrayEquals(new char[credentials.recovered.length], credentials.recovered);
        assertEquals(real.session.vaultId().hex(), credentials.record.vaultId);
    }
    @Test public void enrollmentCancellationLeavesOpenStoresNothingAndWipesPassword() throws Exception {
        lock(); final PasswordBuffer[] retained = {null};
        assertTrue(controller.unlockAndEnroll(password(), e -> { retained[0] = e.password(); biometric.enroll(e); }));
        await(() -> prompt.result != null); prompt.cancel(); awaitOpen(); assertFalse(credentials.configured());
        assertThrows(IllegalStateException.class, retained[0]::encode);
    }
    @Test public void boundedEnrollmentExpiresAndLateSuccessCannotStore() throws Exception {
        lock(); assertTrue(controller.unlockAndEnroll(password(), biometric::enroll)); await(() -> prompt.result != null);
        var late = prompt.result; advance(60_000); late.authorized(); assertFalse(credentials.configured()); awaitOpen();
    }
    @Test public void cancelUnlockRemainsLockedPasswordStillOpens() throws Exception {
        enroll(); lock(); biometric.unlock(); prompt.cancel(); assertEquals(State.LOCKED, controller.snapshot().state());
        assertTrue(controller.unlock(password())); awaitOpen();
    }
    @Test public void invalidatedKeyAndCorruptCredentialFailClosedAndPermitReenrollment() throws Exception {
        enroll(); lock(); credentials.invalid = true; biometric.unlock(); assertEquals(State.LOCKED, controller.snapshot().state());
        assertTrue(credentials.configured()); assertNotNull(credentials.key);
        assertTrue(biometric.disable()); credentials.invalid = false;
        assertTrue(controller.unlockAndEnroll(password(), biometric::enroll)); await(() -> prompt.result != null);
        prompt.succeed(); awaitOpen(); assertTrue(credentials.configured());
        lock(); credentials.record.ciphertext[0] ^= 1; biometric.unlock(); prompt.succeed();
        assertEquals(State.LOCKED, controller.snapshot().state()); assertTrue(credentials.configured());
        assertTrue(biometric.disable());
    }
    @Test public void wrongRecoveredPasswordFailsThroughNormalAuthentication() throws Exception {
        enroll(); lock(); credentials.wrongPassword = true; biometric.unlock(); prompt.succeed(); awaitLocked();
        assertEquals(AndroidVaultController.Error.AUTHENTICATION_FAILED, controller.snapshot().error()); assertEquals(2, opens);
    }
    @Test public void differentAuthenticatedVaultIdClosesCandidateWithoutReassigningCredential() throws Exception {
        enroll(); lock(); credentials.wrongIdentity = true; biometric.unlock(); prompt.succeed(); awaitLocked();
        assertTrue(credentials.configured()); assertNotNull(credentials.key);
        assertEquals(org.totipo.android.reconcile.ForegroundVaultCoordinator.State.CLOSED, real.coordinator.lifecycle());
    }
    @Test public void disableRemovesRecordAndKeyWithoutLockingOrChangingPassword() throws Exception {
        enroll(); biometric.disable(); assertFalse(credentials.configured()); assertNull(credentials.key);
        assertEquals(State.OPEN, controller.snapshot().state()); lock(); assertTrue(controller.unlock(password())); awaitOpen();
    }
    @Test public void weakOnlyUnavailableOffersNoUnlockOperation() throws Exception {
        enroll(); lock(); credentials.strong = false; assertFalse(biometric.available());
        int before = prompt.prompts; biometric.unlock(); assertEquals(before, prompt.prompts); assertEquals(State.LOCKED, controller.snapshot().state());
    }
    @Test public void fixedDeadlineReallyClosesSessionAndLateInteractionCannotExtendIt() throws Exception {
        advance(14 * 60_000); assertEquals(State.OPEN, controller.snapshot().state()); controller.userInteraction();
        advance(14 * 60_000); assertEquals(State.OPEN, controller.snapshot().state());
        advance(60_000); assertEquals(State.LOCKING, controller.snapshot().state()); controller.userInteraction(); awaitLocked();
        assertEquals(org.totipo.android.reconcile.ForegroundVaultCoordinator.State.CLOSED, real.coordinator.lifecycle());
    }
    @Test public void suspendedBackgroundRechecksBeforeUseAndFourteenMinutesDoesNotLock() throws Exception {
        controller.foregroundChanged(false); now += 840_000; controller.foregroundChanged(true);
        assertEquals(State.OPEN, controller.snapshot().state()); controller.foregroundChanged(false); now += 120_000;
        controller.checkInactivity(); assertEquals(State.LOCKING, controller.snapshot().state()); awaitLocked();
    }
    @Test public void shutdownCancelsDeadlineAndClosesSession() throws Exception {
        controller.shutdown(); awaitLocked(); controller.shutdown(); advance(960_000); assertEquals(State.LOCKED, controller.snapshot().state());
        assertTrue(jobs.stream().noneMatch(j -> !j.cancelled));
    }
    @Test public void shutdownDuringPasswordOpenCannotStartSessionDeadlineOrEnrollment() throws Exception {
        lock();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var first = new java.util.concurrent.atomic.AtomicBoolean(true);
        real.operationHook = name -> {
            if (name.equals("readVault") && first.getAndSet(false)) {
                entered.countDown();
                try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { throw new AssertionError(e); }
            }
        };
        assertTrue(controller.unlockAndEnroll(password(), biometric::enroll)); assertTrue(entered.await(10, TimeUnit.SECONDS));
        try { controller.shutdown(); } finally { release.countDown(); }
        awaitLocked(); assertNull(prompt.result);
        assertTrue(jobs.stream().noneMatch(j -> !j.cancelled));
        assertEquals(org.totipo.android.reconcile.ForegroundVaultCoordinator.State.CLOSED, real.coordinator.lifecycle());
    }
    @Test public void lockedOrForeignEnrollmentCannotPrepareOrPersistCredential() throws Exception {
        lock();
        try (var secret = new PasswordBuffer(password())) {
            var forged = new Enrollment(controller.localId(), controller.activationId(), 0, "a".repeat(64), secret);
            biometric.enroll(forged); assertNull(prompt.result); assertFalse(credentials.configured());
        }
        final Enrollment[] captured = {null};
        assertTrue(controller.unlockAndEnroll(password(), e -> captured[0] = e)); await(() -> captured[0] != null);
        var enrollment = captured[0];
        assertFalse(controller.completeBiometricEnrollment(new Enrollment("0".repeat(32), enrollment.activationId(),
                enrollment.session(), enrollment.vaultId(), enrollment.password()), () -> fail("foreign write")));
        assertFalse(controller.completeBiometricEnrollment(new Enrollment(enrollment.localId(), enrollment.activationId(),
                enrollment.session(), "b".repeat(64), enrollment.password()), () -> fail("wrong session identity")));
        lock(); assertTrue(controller.unlock(password())); awaitOpen();
        biometric.enroll(enrollment); assertNull(prompt.result); assertFalse(credentials.configured());
    }
    @Test public void lockAndPasswordReopenCannotAuthorizeOldEnrollmentWithSameVaultId() throws Exception {
        lock(); assertTrue(controller.unlockAndEnroll(password(), biometric::enroll)); await(() -> prompt.result != null);
        var late = prompt.result; lock(); assertTrue(controller.unlock(password())); awaitOpen();
        late.authorized(); assertFalse(credentials.configured());
    }
    @Test public void cancellationOfReplacementRetainsPriorUsableCredential() throws Exception {
        enroll(); var prior = credentials.record; var key = credentials.key;
        lock(); assertTrue(controller.unlockAndEnroll(password(), biometric::enroll)); await(() -> prompt.result != null);
        prompt.cancel(); assertSame(prior, credentials.record); assertSame(key, credentials.key);
        lock(); biometric.unlock(); prompt.succeed(); awaitOpen();
    }
    @Test public void failedPersistenceNeverPublishesEnrollmentSuccess() throws Exception {
        credentials.failWrite = true; lock();
        assertTrue(controller.unlockAndEnroll(password(), biometric::enroll)); await(() -> prompt.result != null);
        prompt.succeed(); awaitOpen(); assertFalse(credentials.configured());
        assertTrue(biometric.message().contains("unavailable"));
    }
    @Test public void activityCloseAndBackgroundCancelPromptWithoutDeletingCredential() throws Exception {
        enroll(); lock(); biometric.unlock(); var late = prompt.result; biometric.close(); late.authorized();
        assertEquals(State.LOCKED, controller.snapshot().state()); assertTrue(credentials.configured());
        biometric.unlock(); late = prompt.result; controller.foregroundChanged(false); late.authorized();
        assertEquals(State.LOCKED, controller.snapshot().state()); assertTrue(credentials.configured());
        credentials.local = controller.localId();
        biometric = new BiometricUnlock(controller, credentials, prompt, this::after);
        controller.foregroundChanged(true); biometric.unlock(); prompt.succeed(); awaitOpen();
    }
    @Test public void activityCancellationDuringActualPasswordOpenClosesCandidateBeforeObservation() throws Exception {
        enroll(); lock(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var once = new java.util.concurrent.atomic.AtomicBoolean(true);
        real.operationHook = name -> { if (name.equals("readVault") && once.getAndSet(false)) {
            entered.countDown(); try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
            catch (InterruptedException failure) { throw new AssertionError(failure); }
        } };
        var states = new ArrayList<State>(); controller.attach(s -> states.add(s.state())); pump();
        biometric.unlock(); prompt.succeed(); assertTrue(entered.await(10, TimeUnit.SECONDS));
        try { biometric.close(); } finally { release.countDown(); }
        awaitLocked(); assertFalse(states.contains(State.OPEN));
        assertNull(field("observationOwner"));
        assertEquals(ForegroundVaultCoordinator.State.CLOSED, real.coordinator.lifecycle());
    }
    private Object field(String name) throws Exception {
        var field = AndroidVaultController.class.getDeclaredField(name); field.setAccessible(true); return field.get(controller);
    }
    @Test public void cancelledActivityDuringPasswordOpenCannotOfferLateEnrollment() throws Exception {
        lock(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var once = new java.util.concurrent.atomic.AtomicBoolean(true);
        real.operationHook = name -> { if (name.equals("readVault") && once.getAndSet(false)) {
            entered.countDown(); try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
            catch (InterruptedException failure) { throw new AssertionError(failure); }
        } };
        assertTrue(controller.unlockAndEnroll(password(), biometric::enroll)); assertTrue(entered.await(10, TimeUnit.SECONDS));
        try { controller.cancelBiometricEnrollment(); biometric.close(); } finally { release.countDown(); }
        awaitOpen(); assertNull(prompt.result); assertFalse(credentials.configured());
    }
    @Test public void enrollmentUsesImmutableAuthenticatedIdentityWhileCoordinatorRefreshIsInFlight() throws Exception {
        lock(); assertTrue(controller.unlockAndEnroll(password(), biometric::enroll)); await(() -> prompt.result != null);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        real.operationHook = name -> { if (name.equals("requestRefresh")) {
            entered.countDown(); try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
            catch (InterruptedException failure) { throw new AssertionError(failure); }
        } };
        var coordinator = (ForegroundVaultCoordinator)field("vault");
        // Use the real existing worker and a real coordinator operation; no second
        // session, fake VaultId or manually assigned coordinator state.
        ((java.util.concurrent.Executor)field("worker")).execute(coordinator::requestRefresh);
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        try {
            assertEquals(ForegroundVaultCoordinator.State.REQUESTING_REFRESH, coordinator.lifecycle());
            prompt.succeed(); assertTrue(credentials.configured());
            assertEquals(real.session.vaultId().hex(), credentials.record.vaultId);
        } finally { release.countDown(); }
        await(() -> coordinator.lifecycle() == ForegroundVaultCoordinator.State.OPEN);
    }
    static class FakePrompt implements BiometricUnlock.Prompt {
        Result result; int prompts;
        public Runnable authenticate(BiometricCredentials.Operation op, Result result) { this.result = result; prompts++; return () -> {}; }
        void succeed() { var next = result; result = null; next.authorized(); }
        void cancel() { var next = result; result = null; next.cancelled(); }
    }
    static class MemoryCredentials implements BiometricCredentials {
        String local;
        public String localId() { return local; }
        SecretKey key; BiometricRecord record; boolean strong = true, invalid, wrongPassword, wrongIdentity, failWrite; char[] recovered;
        public boolean available() { return strong; }
        public boolean configured() { return record != null; }
        public void delete() { key = null; record = null; }
        public Operation enrollment(String id) throws Exception {
            if (key == null) key = KeyGenerator.getInstance("AES").generateKey(); Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key); return operation(c, id);
        }
        public Operation unlock() throws Exception {
            if (invalid) throw new Exception(); Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, record.iv));
            return operation(c, wrongIdentity ? "b".repeat(64) : record.vaultId);
        }
        Operation operation(Cipher c, String id) {
            return new Operation() {
                public Cipher cipher() { return c; }
                public String vaultId() { return id; }
                public void encrypt(PasswordBuffer password) throws Exception {
                    if (failWrite) throw new IOException();
                    c.updateAAD(BiometricRecord.aad(local, id));
                    byte[] plain = password.encode(); try { record = new BiometricRecord(local, id, c.getIV(), c.doFinal(plain)); }
                    finally { Arrays.fill(plain, (byte) 0); }
                }
                public char[] decrypt() throws Exception {
                    c.updateAAD(BiometricRecord.aad(local, record.vaultId));
                    byte[] plain = c.doFinal(record.ciphertext);
                    try { recovered = wrongPassword ? new char[]{'x'} : PasswordBuffer.decode(plain); return recovered; }
                    finally { Arrays.fill(plain, (byte) 0); }
                }
            };
        }
    }
}
