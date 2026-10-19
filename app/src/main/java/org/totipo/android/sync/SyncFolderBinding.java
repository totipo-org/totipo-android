package org.totipo.android.sync;

import org.totipo.android.provider.ProviderSnapshot.Scan;

/** Worker-confined configuration policy. URI is private Android metadata, never protocol identity. */
public final class SyncFolderBinding {
    public enum Status { NOT_CONFIGURED, CHECKING, READY, ACCESS_LOST, UNAVAILABLE }
    public record Stored(String uri, boolean readable, boolean writable) {
        @Override public String toString() { return "Stored[private configuration]"; }
    }
    public record Grants(boolean read, boolean write) {}
    public record View(Status status, boolean readable, boolean writable) {}
    public interface Port {
        Stored load();
        default Object configurationLock() { return this; }
        default SyncGrantOwnership.Use retain(String uri) { return () -> {}; }
        default String configurationNotice() { return ""; }
        default void selectionAllowed(String uri) {}
        default boolean pendingPublication() { return false; }
        default void pendingPublication(boolean pending) {}
        boolean save(Stored value);
        boolean validTree(String uri);
        Grants grants(String uri);
        void take(String uri, boolean read, boolean write);
        void release(String uri, boolean read, boolean write);
        void probe(String uri);
        Scan scan(String uri);
    }
    private final Port port;
    public record Request(long generation, String uri, Object owner) {
        public Request(long generation, String uri) { this(generation, uri, null); }
        @Override public String toString() { return "Request[private configuration]"; }
    }
    private final Object requestOwner = new Object();
    private long generation;
    private Stored remembered;
    private View view = new View(Status.NOT_CONFIGURED, false, false);
    public SyncFolderBinding(Port port) { this.port = port; }
    /** UI retry hint only, never an authority for deciding which immutable objects to publish. */
    public synchronized boolean pendingPublication() {
        try { return port.pendingPublication(); } catch (RuntimeException unavailable) { return true; }
    }
    public synchronized boolean pendingPublication(boolean pending) {
        try { port.pendingPublication(pending); return true; }
        catch (RuntimeException unavailable) { return false; } // Hint only; local save remains complete.
    }
    public synchronized String configurationNotice() { return port.configurationNotice(); }
    public synchronized View view() { return view; }
    public synchronized String initialUri() { return remembered == null ? null : remembered.uri(); }
    public synchronized View restore() {
        generation++;
        remembered = port.load();
        return check();
    }
    public synchronized View check() {
        if (remembered == null) return view = new View(Status.NOT_CONFIGURED, false, false);
        if (!port.validTree(remembered.uri())) return invalidate(new View(Status.ACCESS_LOST, false, false));
        Grants grants;
        try { grants = port.grants(remembered.uri()); }
        catch (RuntimeException unavailable) { return invalidate(new View(Status.UNAVAILABLE, false, false)); }
        if (!grants.read()) return invalidate(new View(Status.ACCESS_LOST, false, grants.write()));
        View next = new View(Status.CHECKING, true, grants.write());
        if (!next.equals(view)) generation++;
        return view = next;
    }
    private View invalidate(View next) {
        if (!next.equals(view)) generation++;
        return view = next;
    }
    public synchronized Request request() { return new Request(generation, initialUri(), requestOwner); }
    public synchronized boolean current(Request request) {
        return request.owner() == requestOwner && request.generation() == generation && java.util.Objects.equals(request.uri(), initialUri());
    }
    public synchronized Port transport() { return port; }
    public synchronized void accessibility(Request request, boolean available) {
        if (current(request)) view = new View(available ? Status.READY : Status.UNAVAILABLE,
                view.readable(), view.writable());
    }
    public synchronized void unavailable(Request request) { accessibility(request, false); }
    /** Cancellation preserves the old binding. Failed replacement never commits a new active root. */
    public synchronized boolean choose(boolean cancelled, String uri, boolean read, boolean write) {
        synchronized (port.configurationLock()) { return chooseLocked(cancelled, uri, read, write); }
    }
    private boolean chooseLocked(boolean cancelled, String uri, boolean read, boolean write) {
        if (cancelled) return true;
        if (uri == null || !read || !port.validTree(uri)) return false;
        Grants before = null;
        try {
            port.selectionAllowed(uri);
            before = port.grants(uri);
            port.take(uri, read, write);
            Grants persisted = port.grants(uri);
            if (!persisted.read()) throw new IllegalStateException("Read grant absent");
            Stored next = new Stored(uri, persisted.read(), persisted.write());
            if (!port.save(next)) throw new IllegalStateException("Configuration unavailable");
            Stored previous = remembered;
            generation++;
            remembered = next;
            if (previous != null && !previous.uri().equals(uri)) release(previous.uri());
            check();
            return true;
        } catch (RuntimeException failure) {
            // Release only capabilities acquired by this failed attempt, preserving an old grant.
            if (before != null) {
                try { port.release(uri, read && !before.read(), write && !before.write()); }
                catch (RuntimeException ignored) { }
            }
            return false;
        }
    }
    public synchronized boolean disconnect() {
        synchronized (port.configurationLock()) { return disconnectLocked(); }
    }
    private boolean disconnectLocked() {
        try { if (!port.save(null)) return false; }
        catch (RuntimeException failure) { return false; }
        Stored previous = remembered;
        generation++;
        remembered = null;
        view = new View(Status.NOT_CONFIGURED, false, false);
        if (previous != null && port.validTree(previous.uri())) release(previous.uri());
        return true;
    }
    private void release(String uri) {
        try { Grants g = port.grants(uri); port.release(uri, g.read(), g.write()); }
        catch (RuntimeException ignored) { }
    }
}
