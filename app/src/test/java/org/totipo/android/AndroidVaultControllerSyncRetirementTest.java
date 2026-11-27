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

/** Real controller SyncRetirement regressions; isolated fixture per test. */
public final class AndroidVaultControllerSyncRetirementTest extends AndroidVaultControllerFixture {
    @Test public void timeoutDuringProviderObservationClosesWithoutWaitingForProvider() throws Exception {
        timeoutLock = true; dailyLockDuringObservationRetiresSyncWithoutPublication();
    }
    @Test public void timeoutDuringLocalImportCancelsOutboundAndCloses() throws Exception {
        timeoutLock = true; dailyLockDuringLocalImportCancelsBeforeOutboundPhase();
    }
    @Test public void timeoutDuringJavaObservationCancelsOutboundAndCloses() throws Exception {
        timeoutLock = true; dailyLockDuringJavaObservationWaitCancelsBeforeOutboundPhase();
    }
    @Test public void dailyLockDuringObservationRetiresSyncWithoutPublication() throws Exception {
        var port = outboundFixture(1); await(this::providerDone);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        port.onScan = () -> { entered.countDown(); try { assertTrue(release.await(30, TimeUnit.SECONDS)); }
            catch (InterruptedException e) { throw new AssertionError(e); } };
        assertTrue(controller.sync()); assertTrue(entered.await(10, TimeUnit.SECONDS));
        assertTrue(securityLock()); state(State.LOCKED);
        release.countDown(); await(this::providerDone);
        assertEquals(0, port.creates); assertNull(controller.snapshot().view());
        assertEquals(State.LOCKED, controller.snapshot().state()); assertFalse(controller.canSync());
    }
    @Test public void dailyLockDuringLocalImportCancelsBeforeOutboundPhase() throws Exception {
        var port = dailyConcurrentFixture();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        real.operationHook = name -> { if (name.equals("publishObject")) { entered.countDown();
            try { assertTrue(release.await(30, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new AssertionError(e); } } };
        assertTrue(controller.sync()); assertTrue(entered.await(10, TimeUnit.SECONDS));
        try { assertTrue(securityLock()); assertEquals(State.LOCKING, controller.snapshot().state()); }
        finally { release.countDown(); }
        state(State.LOCKED); await(this::providerDone);
        assertEquals(0, port.creates); assertNull(controller.snapshot().view());
    }
    @Test public void dailyLockDuringJavaObservationWaitCancelsBeforeOutboundPhase() throws Exception {
        var port = outboundFixture(1); await(this::providerDone);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        real.beforeScan = store -> { entered.countDown();
            try { assertTrue(release.await(30, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new AssertionError(e); } };
        assertTrue(controller.sync()); assertTrue(entered.await(10, TimeUnit.SECONDS));
        try { assertTrue(securityLock()); assertEquals(State.LOCKING, controller.snapshot().state()); }
        finally { release.countDown(); real.beforeScan = store -> {}; }
        state(State.LOCKED); await(this::providerDone);
        assertEquals(0, port.creates); assertNull(controller.snapshot().view());
    }
}
