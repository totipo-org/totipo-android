package org.totipo.android;

import java.time.Instant;
import org.junit.Before;
import org.junit.Test;
import org.totipo.TokenId;
import static org.junit.Assert.*;

/** Deterministic wall/monotonic scheduling and clipboard policy, with no Android runtime. */
public final class TotpPresentationTest {
    static final class FakeClock implements TotpPresentation.Time {
        Instant wall = Instant.ofEpochSecond(59);
        long elapsed = 10000;
        public Instant wall() { return wall; }
        public long elapsedMillis() { return elapsed; }
        void advance(long millis) { wall = wall.plusMillis(millis); elapsed += millis; }
    }
    static final class Timer implements TotpPresentation.Scheduler {
        Runnable task;
        long delay;
        int schedules;
        public Runnable after(long millis, Runnable action) {
            assertNull("Only one pending timer", task);
            task = action; delay = millis; schedules++;
            return () -> { if (task == action) task = null; };
        }
        void fire() { Runnable action = task; task = null; if (action != null) action.run(); }
    }
    static final class FakeClipboard implements TotpPresentation.Clipboard {
        String marker, text;
        int copies, clears, attempts;
        boolean unavailable, simple = true;
        public boolean copy(String marker, String text) {
            if (unavailable) return false;
            this.marker = marker; this.text = text; copies++; return true;
        }
        public void clearIfOwned(String marker, String text) {
            attempts++;
            if (!unavailable && PlatformCodeClipboard.matches(marker, text, this.marker, this.text, simple)) {
                this.marker = this.text = null; clears++;
            }
        }
    }
    private FakeClock clock;
    private Timer timer;
    private FakeClipboard clipboard;
    private TotpPresentation presentation;
    private RevealedTotp code(String id) {
        return new RevealedTotp(new TokenId(id.repeat(64)), "94287082", Instant.ofEpochSecond(30), Instant.ofEpochSecond(60), 8);
    }
    @Before public void setup() {
        clock = new FakeClock(); timer = new Timer(); clipboard = new FakeClipboard();
        presentation = new TotpPresentation(clock, timer, clipboard, () -> {});
    }
    @Test public void revealUsesRemainingActualIntervalAndDoesNotAutoCopy() {
        assertTrue(presentation.reveal(code("a"))); assertEquals(1, presentation.display().seconds());
        assertEquals(1000, timer.delay); assertEquals(0, clipboard.copies);
    }
    @Test public void oneAtATimeAndCancelledOldCallbackCannotAffectNewReveal() {
        presentation.reveal(code("a")); Runnable old = timer.task;
        presentation.reveal(code("b")); Runnable current = timer.task;
        old.run(); assertSame(current, timer.task);
        assertEquals(code("b"), presentation.display().code());
    }
    @Test public void explicitHideClearsTimerAndMatchingClipboardAndForgetsOwnership() {
        presentation.reveal(code("a")); assertTrue(presentation.copy());
        presentation.clear(); assertNull(presentation.display().code()); assertNull(timer.task);
        assertEquals(1, clipboard.clears); int attempts = clipboard.attempts;
        presentation.clear(); assertEquals(attempts, clipboard.attempts);
    }
    @Test public void exactExpiryConcealsAndNoNextPeriodAutoReveal() {
        presentation.reveal(code("a")); presentation.copy(); clock.advance(1000); timer.fire();
        assertNull(presentation.display().code()); assertNull(timer.task); assertEquals(1, clipboard.clears);
        clock.advance(90000); timer.fire(); assertNull(presentation.display().code());
    }
    @Test public void forwardWallMovementExpiresBeforeMonotonicDeadline() {
        presentation.reveal(code("a")); clock.wall = Instant.ofEpochSecond(60);
        assertNull(presentation.display().code()); assertNull(timer.task);
    }
    @Test public void backwardWallMovementDoesNotExtendMonotonicDeadline() {
        presentation.reveal(code("a")); clock.wall = Instant.ofEpochSecond(45); clock.elapsed += 1000;
        timer.fire(); assertNull(presentation.display().code());
    }
    @Test public void pastAndFutureIntervalsAreNotRevealed() {
        clock.wall = Instant.ofEpochSecond(60); assertFalse(presentation.reveal(code("a")));
        clock.wall = Instant.ofEpochSecond(29); assertFalse(presentation.reveal(code("a"))); assertNull(timer.task);
    }
    @Test public void copyRechecksValidityBeforeTimerFires() {
        presentation.reveal(code("a")); clock.advance(1000);
        assertFalse(presentation.copy()); assertNull(presentation.display().code()); assertEquals(0, clipboard.copies);
    }
    @Test public void copyUsesUnformattedExactDigitsAndFreshMarker() {
        presentation.reveal(code("a")); assertTrue(presentation.copy());
        assertEquals("94287082", clipboard.text); String marker = clipboard.marker;
        assertNotNull(marker); assertTrue(presentation.copy()); assertNotEquals(marker, clipboard.marker);
    }
    @Test public void differentMarkerPreservesObservedExternalReplacement() {
        presentation.reveal(code("a")); presentation.copy(); clipboard.marker = "external";
        presentation.clear(); assertEquals(0, clipboard.clears); assertEquals("94287082", clipboard.text);
    }
    @Test public void sameMarkerDifferentTextIsPreserved() {
        presentation.reveal(code("a")); presentation.copy(); clipboard.text = "external value";
        presentation.clear(); assertEquals(0, clipboard.clears); assertEquals("external value", clipboard.text);
    }
    @Test public void unavailableClipboardSkipsCleanupAndOwnershipIsForgotten() {
        presentation.reveal(code("a")); presentation.copy(); clipboard.unavailable = true;
        presentation.clear(); assertEquals(0, clipboard.clears); assertEquals(1, clipboard.attempts);
        clipboard.unavailable = false; presentation.clear(); assertEquals(1, clipboard.attempts);
    }
    @Test public void uriOrIntentShapeCannotEstablishOwnership() {
        presentation.reveal(code("a")); presentation.copy(); clipboard.simple = false;
        presentation.clear(); assertEquals(0, clipboard.clears);
    }
    @Test public void countdownTicksUseAbsoluteTimeAndAtMostOneTask() {
        clock.wall = Instant.ofEpochSecond(31); presentation.reveal(code("a"));
        assertEquals(29, presentation.display().seconds());
        clock.advance(4200); timer.fire(); assertEquals(25, presentation.display().seconds());
        assertEquals(1000, timer.delay); assertEquals(2, timer.schedules);
    }
    @Test public void detachedValueToStringIsRedacted() {
        assertEquals("RevealedTotp[redacted]", code("a").toString());
    }
}
