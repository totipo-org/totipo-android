package org.totipo.android;

import java.util.function.LongSupplier;

/** One monotonic deadline. Only explicit user interaction and successful open reset it. */
final class InactivityLock {
    static final long TIMEOUT_MILLIS = 15 * 60 * 1000L;
    interface Scheduler { Runnable after(long delay, Runnable action); }
    private final LongSupplier clock;
    private final Scheduler scheduler;
    private final Runnable lock;
    private Runnable cancel;
    private long deadline;
    private boolean active;
    private long generation;
    InactivityLock(LongSupplier clock, Scheduler scheduler, Runnable lock) {
        this.clock = clock; this.scheduler = scheduler; this.lock = lock;
    }
    void opened() { active = true; reset(); }
    void interaction() { if (check()) reset(); }
    boolean check() {
        if (!active) return false;
        if (clock.getAsLong() >= deadline) { stop(); lock.run(); return false; }
        return true;
    }
    private void reset() {
        if (cancel != null) cancel.run();
        deadline = clock.getAsLong() + TIMEOUT_MILLIS;
        schedule();
    }
    private void schedule() {
        long current = ++generation;
        cancel = scheduler.after(Math.max(0, deadline - clock.getAsLong()), () -> {
            if (current != generation) return;
            cancel = null; if (check()) schedule();
        });
    }
    void stop() { generation++; active = false; if (cancel != null) cancel.run(); cancel = null; }
}
