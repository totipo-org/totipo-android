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

/** Real controller session and ownership regressions; isolated fixture per test. */
public final class AndroidVaultControllerTest extends AndroidVaultControllerFixture {
    @Test public void r19ObjectDataVetoPreservesOrphanAndDoesNotCreateVault() throws Exception {
        byte[] orphan = new byte[]{1,2,3}; Path object;
        try (var lease = owner.acquire()) {
            Files.createDirectories(lease.root().resolve("objects-v1"));
            object = Files.write(lease.root().resolve("objects-v1").resolve("a".repeat(64)), orphan);
        }
        start(); state(State.ERROR_LOCKED);
        assertFalse(controller.create(password()));
        assertEquals(0, real.creates); // Registry refuses uncertain roots before Java creation.
        assertTrue(controller.snapshot().message().contains("recover storage"));
        assertFalse(Files.exists(localRoot().resolve("vault"))); assertArrayEquals(orphan, Files.readAllBytes(object));
        assertNull(real.session);
    }
    @Test public void r19CreateAndReopenHaveExactCanonicalVaultId() throws Exception {
        create(); var id = real.session.vaultId();
        assertEquals(org.totipo.Totipo.vaultId(Files.readAllBytes(localRoot().resolve("vault"))), id);
        lock(); accept(() -> controller.unlock(password())); state(State.OPEN);
        assertEquals(id, real.session.vaultId());
    }
    @Test public void freshDiscoveryReleasesAllOwnership() throws Exception {
        start(); state(State.NO_LOCAL_VAULT);
        try (var lease = owner.acquire()) { assertNotNull(lease.root()); }
        assertEquals(0, real.opens); assertEquals(0, real.creates);
    }
    @Test public void createFreshProcessWrongPasswordCorrectOpenAndRepeatedOpen() throws Exception {
        create(); assertEquals(1, real.creates); assertEquals(0, real.opens);
        assertNotNull(controller.snapshot().view()); assertTrue(controller.snapshot().view().tokens().isEmpty());
        assertThrows(IllegalStateException.class, owner::acquire);
        char[] duplicate = password(); assertFalse(controller.unlock(duplicate)); assertArrayEquals(new char[duplicate.length], duplicate);
        lock(); assertEquals(1, real.closes);
        try (var lease = owner.acquire(); var files = Files.list(lease.root())) {
            assertEquals(1, files.filter(p -> p.getFileName().toString().equals("vault")).count());
        }
        restartProcessAdmission();
        start(); state(State.LOCKED); assertEquals(0, real.opens); // fresh process analogue; no auto-auth
        char[] wrong = "wrong".toCharArray(); accept(() -> controller.unlock(wrong));
        await(() -> controller.snapshot().error() == Error.AUTHENTICATION_FAILED);
        assertEquals(State.LOCKED, controller.snapshot().state()); assertArrayEquals(new char[wrong.length], wrong);
        try (var lease = owner.acquire()) { assertNotNull(lease.root()); }
        char[] correct = password(); accept(() -> controller.unlock(correct)); state(State.OPEN);
        assertArrayEquals(new char[correct.length], correct); assertEquals(2, real.opens);
        assertEquals(ForegroundVaultCoordinator.State.OPEN, real.coordinator.lifecycle());
        lock(); assertNull(controller.snapshot().view());
    }
    @Test public void observerRecreationMultipleObserversAndRefreshRetainSession() throws Exception {
        create(); var coordinator = real.coordinator; var session = real.session;
        List<Snapshot> a = new ArrayList<>(), b = new ArrayList<>();
        Listener first = a::add, second = b::add;
        controller.attach(first); controller.attach(second); pump();
        assertEquals(State.OPEN, a.get(a.size()-1).state()); assertEquals(State.OPEN, b.get(b.size()-1).state());
        controller.detach(first); controller.detach(second);
        assertEquals(State.OPEN, controller.snapshot().state()); assertEquals(0, real.closes);
        controller.attach(second); pump(); assertSame(coordinator, real.coordinator); assertSame(session, real.session);
        accept(() -> controller.refresh());
        await(() -> controller.snapshot().message().equals("Refresh requested"));
        assertSame(session, real.session); assertEquals(1, real.creates); assertEquals(0, real.opens); assertEquals(0, real.closes);
        assertTrue(workerChecks.get() > 0); assertTrue(uiChecks.get() > 0); controller.detach(second);
        lock(); assertFalse(controller.refresh());
    }
    @Test public void busyAdmissionWipesRejectedCredentialAndDoesNotQueueDuplicateOperations() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        start(new RealBackend() {
            boolean authenticateCandidate(byte[] exact, char[] credential) {
            assertEquals("Totipo-vault", Thread.currentThread().getName()); candidateAuthentications.incrementAndGet(); return super.authenticateCandidate(exact,credential);
        }
        ForegroundVaultCoordinator.Opening join(LocalReplicaOwner owner, byte[] exact, char[] password, BooleanSupplier cancelled) throws IOException {
            assertEquals("Totipo-vault", Thread.currentThread().getName()); return real.join(owner, exact, password, cancelled);
        }
        ForegroundVaultCoordinator.Creation create(LocalReplicaOwner owner, char[] password) throws IOException {
                entered.countDown();
                try { assertTrue(release.await(30, TimeUnit.SECONDS)); }
                catch (InterruptedException failure) { throw new AssertionError(failure); }
                return super.create(owner, password);
            }
        });
        state(State.NO_LOCAL_VAULT); char[] accepted = password(); accept(() -> controller.create(accepted));
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        try {
            char[] rejected = password(); assertFalse(controller.create(rejected)); assertArrayEquals(new char[rejected.length], rejected);
            assertFalse(controller.unlock(password())); assertFalse(controller.lock()); assertFalse(controller.refresh());
            assertEquals(State.CREATING, controller.snapshot().state()); assertEquals(0, real.creates);
        } finally { release.countDown(); }
        state(State.OPEN); assertEquals(1, real.creates);
    }
    @Test public void failedCloseRetainsOwnershipAndOnlyExplicitRetryReleasesIt() throws Exception {
        create(); real.failClose.set(true); accept(() -> controller.lock()); state(State.FAILED_CLOSE);
        assertEquals(Error.CLOSE_FAILED, controller.snapshot().error()); assertNull(controller.snapshot().view());
        assertThrows(IllegalStateException.class, owner::acquire);
        assertFalse(controller.unlock(password())); assertFalse(controller.refresh());
        real.failClose.set(false); lock();
        try (var lease = owner.acquire()) { assertNotNull(lease.root()); }
        assertEquals(ForegroundVaultCoordinator.State.CLOSED, real.coordinator.lifecycle());
    }
    @Test public void malformedVaultIsNeverAbsenceAndUnavailableHasSeparateError() throws Exception {
        for (String kind : List.of("short", "long", "directory")) {
            owner = TestReplicaOwners.create(temporary.newFolder().toPath());
            try (var lease = owner.acquire()) {
                Path vault = lease.root().resolve("vault");
                if (kind.equals("directory")) Files.createDirectory(vault);
                else Files.write(vault, new byte[kind.equals("short") ? 86 : 88]);
            }
            start(); state(State.ERROR_LOCKED); assertEquals(Error.LOCAL_STORAGE_UNSAFE, controller.snapshot().error());
            assertFalse(controller.create(password()));
            try (var lease = owner.acquire()) { assertNotNull(lease.root()); }
        }
        owner = TestReplicaOwners.create(temporary.newFolder().toPath());
        start(new RealBackend() {
            ForegroundVaultCoordinator.Discovery discover(LocalReplicaOwner owner) throws IOException { throw new IOException("test unavailable"); }
        });
        state(State.ERROR_LOCKED); assertEquals(Error.LOCAL_STORAGE_UNAVAILABLE, controller.snapshot().error());
    }
    @Test public void discoveryDomainCloseFailureIsRetainedForExplicitRetry() throws Exception {
        real.failDomainClose.set(true); start(); state(State.FAILED_CLOSE);
        assertEquals(Error.CLOSE_FAILED, controller.snapshot().error());
        assertThrows(IllegalStateException.class, owner::acquire);
        real.failDomainClose.set(false); accept(() -> controller.lock()); state(State.NO_LOCAL_VAULT);
        try (var lease = owner.acquire()) { assertNotNull(lease.root()); }
    }
    @Test public void openDomainCloseFailureDoesNotReleaseLeaseAfterSessionClosed() throws Exception {
        create(); real.failDomainClose.set(true); accept(() -> controller.lock()); state(State.FAILED_CLOSE);
        assertEquals(1, real.closes); assertThrows(IllegalStateException.class, owner::acquire);
        real.failDomainClose.set(false); lock(); assertEquals(1, real.closes);
        try (var lease = owner.acquire()) { assertNotNull(lease.root()); }
    }
    @Test public void boundedSpiUnavailableAndUnsafeNamespaceRemainDistinct() throws Exception {
        real.discoveryRead = new org.totipo.spi.BoundedRead.Unavailable(org.totipo.spi.StoreFailure.UNAVAILABLE);
        start(); state(State.ERROR_LOCKED); assertEquals(Error.LOCAL_STORAGE_UNAVAILABLE, controller.snapshot().error());
        real.discoveryRead = new org.totipo.spi.BoundedRead.Unavailable(org.totipo.spi.StoreFailure.UNSAFE_NAMESPACE);
        accept(() -> controller.retryDiscovery()); state(State.ERROR_LOCKED);
        assertEquals(Error.LOCAL_STORAGE_UNSAFE, controller.snapshot().error());
        real.discoveryRead = null; accept(() -> controller.retryDiscovery()); state(State.NO_LOCAL_VAULT);
    }
    @Test public void emptyPasswordRemainsProtocolValidAtControllerBoundary() throws Exception {
        start(); state(State.NO_LOCAL_VAULT); accept(() -> controller.create(new char[0])); state(State.OPEN);
        lock(); accept(() -> controller.unlock(new char[0])); state(State.OPEN);
    }
    @Test public void creationAlreadyExistsDoesNotCreateSecondSession() throws Exception {
        start(); state(State.NO_LOCAL_VAULT);
        try (var lease = owner.acquire()) { Files.write(lease.root().resolve("vault"), new byte[87]); }
        accept(() -> controller.create(password()));
        await(() -> controller.snapshot().error() == Error.CREATE_FAILED);
        assertEquals(State.LOCKED, controller.snapshot().state()); assertEquals(0, real.creates); assertNull(real.session);
        try (var lease = owner.acquire()) { assertNotNull(lease.root()); }
    }
    @Test public void uncertainCreationNeverPublishesOpenAndDiscoversInstalledVault() throws Exception {
        start(); state(State.NO_LOCAL_VAULT);
        real.creationInstallFault = org.totipo.spi.StoreFailure.UNAVAILABLE;
        char[] password = password(); accept(() -> controller.create(password));
        await(() -> controller.snapshot().error() == Error.LOCAL_STORAGE_UNSAFE);
        assertEquals(State.LOCKED, controller.snapshot().state()); assertNull(real.session);
        assertArrayEquals(new char[password.length], password);
        try (var lease = owner.acquire()) { assertTrue(Files.exists(lease.root().resolve("vault"))); }
        real.creationInstallFault = null; accept(() -> controller.unlock(password())); state(State.OPEN);
    }
    @Test public void staleObservationSignalFromRetiredSessionCannotMutateReopenedSession() throws Exception {
        observationOwnership(false, false);
    }
    @Test public void staleObservationTerminalFromRetiredSessionCannotFailReopenedSession() throws Exception {
        observationOwnership(true, false);
    }
    @Test public void enteredObservationRetainsOwnershipAfterFailedClose() throws Exception {
        observationOwnership(true, true);
    }
    @Test public void endedJavaSubscriptionProducesErrorAndGatesOperations() throws Exception {
        create(); real.session.close();
        state(State.ERROR_OPEN); assertEquals(Error.OBSERVATION_DIAGNOSTICS, controller.snapshot().error());
        assertFalse(controller.refresh()); assertNull(controller.snapshot().view());
        lock();
    }
    @Test public void exactSizeDiscoveryStillLeavesFormatValidationToJava() throws Exception {
        try (var lease = owner.acquire()) { Files.write(lease.root().resolve("vault"), new byte[87]); }
        start(); state(State.LOCKED); accept(() -> controller.unlock(password()));
        await(() -> controller.snapshot().error() == Error.LOCAL_STORAGE_UNSAFE);
        assertEquals(State.LOCKED, controller.snapshot().state());
        try (var lease = owner.acquire()) { assertNotNull(lease.root()); }
    }
}
