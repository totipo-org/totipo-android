package org.totipo.android;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Owned under the controller monitor. No Java/session references or crypto on ticks. */
final class TotpPresentation {
    interface Time { Instant wall(); long elapsedMillis(); }
    interface Scheduler { Runnable after(long delayMillis, Runnable action); }
    interface Clipboard {
        boolean copy(String marker, String text);
        void clearIfOwned(String marker, String text);
    }
    private record Ownership(String marker, String text, Instant expiry) {
        @Override public String toString() { return "Ownership[redacted]"; }
    }
    private final Time time;
    private final Scheduler scheduler;
    private final Clipboard clipboard;
    private final Runnable changed;
    private RevealedTotp shown;
    private long deadline;
    private Runnable cancel;
    private Ownership ownership;
    private long generation;

    TotpPresentation(Time time, Scheduler scheduler, Clipboard clipboard, Runnable changed) {
        this.time = time; this.scheduler = scheduler; this.clipboard = clipboard;
        this.changed = changed;
    }
    record Display(RevealedTotp code, long seconds) {}
    Display display() {
        expire();
        if (shown == null) return new Display(null, 0);
        long millis = remainingMillis();
        if (millis <= 0) { clear(); return new Display(null, 0); }
        return new Display(shown, millis / 1000 + (millis % 1000 == 0 ? 0 : 1));
    }
    private long remainingMillis() {
        return Math.min(Duration.between(time.wall(), shown.validUntil()).toMillis(),
                deadline - time.elapsedMillis());
    }
    boolean reveal(RevealedTotp value) {
        clear();
        Instant now = time.wall();
        if (now.isBefore(value.validFrom()) || !now.isBefore(value.validUntil())) return false;
        long remaining = Duration.between(now, value.validUntil()).toMillis();
        if (remaining <= 0) return false; // Round down: never extend exposure.
        deadline = time.elapsedMillis() + remaining;
        shown = value;
        schedule(); changed.run(); return true;
    }
    private void tick() {
        cancel = null;
        expire();
        if (shown != null) { changed.run(); if (shown != null) schedule(); }
    }
    private void schedule() {
        long expected = generation;
        cancel = scheduler.after(Math.max(1, Math.min(1000, remainingMillis())),
                () -> { if (generation == expected) tick(); });
    }
    private void expire() {
        if (shown != null && (time.wall().isBefore(shown.validFrom())
                || !time.wall().isBefore(shown.validUntil()) || time.elapsedMillis() >= deadline)) clear();
    }
    void clear() {
        generation++;
        if (cancel != null) { cancel.run(); cancel = null; }
        shown = null; deadline = 0;
        Ownership previous = ownership; ownership = null;
        if (previous != null && clipboard != null) clipboard.clearIfOwned(previous.marker(), previous.text());
        changed.run();
    }
    boolean copy() {
        expire();
        if (shown == null || clipboard == null) return false;
        var next = new Ownership(UUID.randomUUID().toString(), shown.code(), shown.validUntil());
        if (!clipboard.copy(next.marker(), next.text())) return false;
        ownership = next; return true;
    }
}
