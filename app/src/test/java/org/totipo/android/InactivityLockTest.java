package org.totipo.android;

import org.junit.Test;
import static org.junit.Assert.*;

public class InactivityLockTest {
    long now, delay; Runnable task; int locks, schedules, cancels;
    InactivityLock timer = new InactivityLock(() -> now, (millis, action) -> {
        delay = millis; task = action; schedules++; return () -> { task = null; cancels++; };
    }, () -> locks++);
    void fire() { Runnable next = task; task = null; next.run(); }
    @Test public void successfulOpenStartsExactlyFifteenMinutesAndLocksAtBoundary() {
        timer.opened(); assertEquals(900_000, delay); now = 899_999;
        assertTrue(timer.check()); assertEquals(0, locks);
        now++; fire(); assertEquals(1, locks); assertFalse(timer.check()); assertNull(task);
    }
    @Test public void userInteractionAtFourteenMinutesResetsOneDeadline() {
        timer.opened(); now = 840_000; timer.interaction(); assertEquals(900_000, delay);
        now = 900_000; assertTrue(timer.check()); now = 1_740_000; fire(); assertEquals(1, locks);
        assertEquals(2, schedules); assertEquals(1, cancels);
    }
    @Test public void backgroundFourteenMinutesReturnsOpenSixteenLocksBeforeUse() {
        timer.opened(); now = 840_000; assertTrue(timer.check());
        now = 960_000; assertFalse(timer.check()); assertEquals(1, locks);
        timer.interaction(); assertEquals(1, schedules);
    }
    @Test public void automaticWorkAndWallClockHaveNoResetEntryPoint() {
        timer.opened(); for (int i = 0; i < 20; i++) assertTrue(timer.check());
        assertEquals(1, schedules); now = 900_000; timer.check(); assertEquals(1, locks);
    }
    @Test public void closureCancelsAndLateCallbackCannotLock() {
        timer.opened(); Runnable stale = task; timer.stop(); now = 960_000;
        stale.run(); assertEquals(0, locks); assertNull(task);
    }
    @Test public void cancelledOrFailedOpenCreatesNoDeadline() {
        now = 1_000_000; timer.interaction(); assertFalse(timer.check()); assertEquals(0, schedules);
    }
}
