package org.totipo.android.sync;

import java.util.*;
import org.junit.Test;
import org.totipo.android.provider.ProviderSnapshot.Scan;
import org.totipo.android.sync.SyncFolderBinding.*;
import static org.junit.Assert.*;

public final class SyncFolderBindingTest {
    public static class MemoryPort implements Port {
        public Stored stored;
        public boolean pending;
        public boolean pendingPublication() { return pending; }
        public void pendingPublication(boolean value) { pending = value; }
        public final Map<String, Grants> permissions = new HashMap<>();
        public boolean offline, failTake, failSave, failRelease, discardTake;
        public int takes, releases, probes, scans;
        public Scan snapshot;
        public Runnable onScan = () -> {};
        public Runnable onProbe = () -> {};
        public Stored load() { return stored; }
        public boolean save(Stored value) { if (failSave) return false; stored = value; return true; }
        public boolean validTree(String uri) { return uri != null && uri.startsWith("content://fixture/tree/"); }
        public Grants grants(String uri) { return permissions.getOrDefault(uri, new Grants(false, false)); }
        public void take(String uri, boolean read, boolean write) {
            takes++; if (failTake) throw new SecurityException();
            if (!discardTake) { var old = grants(uri); permissions.put(uri, new Grants(old.read() || read, old.write() || write)); }
        }
        public void release(String uri, boolean read, boolean write) {
            if (!read && !write) return;
            releases++; if (failRelease) throw new SecurityException();
            var old = grants(uri); permissions.put(uri, new Grants(old.read() && !read, old.write() && !write));
        }
        public void probe(String uri) { probes++; onProbe.run(); if (offline) throw new IllegalStateException(); }
        public Scan scan(String uri) { scans++; onScan.run(); return snapshot; }
    }
    private static final String A = "content://fixture/tree/a", B = "content://fixture/tree/b";
    private static View ready(SyncFolderBinding binding, View local) {
        if (local.status() == Status.CHECKING) {
            boolean available;
            try { binding.transport().probe(binding.initialUri()); available = true; }
            catch (RuntimeException failure) { available = false; }
            binding.accessibility(binding.request(), available);
        }
        return binding.view();
    }
    @Test public void absentAndMalformedConfigurationNeverProbe() {
        var p = new MemoryPort(); var b = new SyncFolderBinding(p);
        assertEquals(Status.NOT_CONFIGURED, b.restore().status());
        p.stored = new Stored("malformed", true, true);
        assertEquals(Status.ACCESS_LOST, b.restore().status()); assertEquals(0, p.probes);
    }
    @Test public void readOnlyAndReadWriteRestoreFromActualPermissionsAfterRestart() {
        for (boolean write : new boolean[]{false, true}) {
            var p = new MemoryPort(); var b = new SyncFolderBinding(p);
            b.restore(); assertTrue(b.choose(false, A, true, write));
            assertEquals(new Stored(A, true, write), p.stored);
            var restarted = new SyncFolderBinding(p);
            assertEquals(new View(Status.READY, true, write), ready(restarted, restarted.restore()));
            assertEquals(1, p.takes);
        }
    }
    @Test public void absentReadGrantIsAccessLostAndNeverAccessed() {
        var p = new MemoryPort(); p.stored = new Stored(A, true, true);
        p.permissions.put(A, new Grants(false, true));
        var b = new SyncFolderBinding(p);
        assertEquals(Status.ACCESS_LOST, b.restore().status()); assertEquals(Status.ACCESS_LOST, b.view().status());
        assertEquals(0, p.probes); assertEquals(0, p.scans);
    }
    @Test public void transientUnavailableRetainsBindingAndRecovers() {
        var p = new MemoryPort(); var b = new SyncFolderBinding(p); b.restore();
        assertTrue(b.choose(false, A, true, false)); p.offline = true;
        assertEquals(Status.UNAVAILABLE, ready(b, b.check()).status()); assertEquals(A, p.stored.uri());
        p.offline = false; assertEquals(Status.READY, ready(b, b.check()).status());
    }
    @Test public void cancellationNullDataNoReadAndMalformedSelection() {
        var p = new MemoryPort(); var b = new SyncFolderBinding(p); b.restore();
        assertTrue(b.choose(true, null, false, false));
        assertFalse(b.choose(false, null, true, true));
        assertFalse(b.choose(false, A, false, true));
        assertFalse(b.choose(false, "file:///bad", true, true));
        assertNull(p.stored); assertEquals(0, p.takes);
    }
    @Test public void persistenceFailureNeverActivatesBinding() {
        for (int failure = 0; failure < 3; failure++) {
            var p = new MemoryPort(); p.failTake = failure == 0; p.discardTake = failure == 1; p.failSave = failure == 2;
            var b = new SyncFolderBinding(p); b.restore();
            assertFalse(b.choose(false, A, true, false));
            assertEquals(Status.NOT_CONFIGURED, b.view().status()); assertNull(p.stored);
            assertFalse(p.grants(A).read());
        }
    }
    @Test public void replacementCommitsOnlyAfterPermissionAndStorageSucceed() {
        var p = new MemoryPort(); var b = new SyncFolderBinding(p); b.restore();
        assertTrue(b.choose(false, A, true, false));
        p.failTake = true; assertFalse(b.choose(false, B, true, true));
        assertEquals(A, p.stored.uri()); assertEquals(Status.CHECKING, b.view().status());
        p.failTake = false; p.failSave = true; assertFalse(b.choose(false, B, true, true));
        assertEquals(A, p.stored.uri()); assertTrue(p.grants(A).read()); assertFalse(p.grants(B).read());
        p.failSave = false; assertTrue(b.choose(false, B, true, false));
        assertEquals(B, p.stored.uri()); assertFalse(p.grants(A).read()); assertTrue(p.grants(B).read());
    }
    @Test public void failedSameTreeAttemptPreservesExistingGrant() {
        var p = new MemoryPort(); var b = new SyncFolderBinding(p); b.restore(); b.choose(false, A, true, false);
        p.failSave = true; assertFalse(b.choose(false, A, true, true));
        assertTrue(p.grants(A).read()); assertFalse(p.grants(A).write());
    }
    @Test public void disconnectClearsAndReleasesBestEffort() {
        for (boolean fail : new boolean[]{false, true}) {
            var p = new MemoryPort(); var b = new SyncFolderBinding(p); b.restore(); b.choose(false, A, true, true);
            p.failRelease = fail; assertTrue(b.disconnect());
            assertNull(p.stored); assertNull(b.initialUri()); assertEquals(Status.NOT_CONFIGURED, b.view().status());
            assertEquals(1, p.releases);
        }
    }
    @Test public void failedDisconnectRetainsBinding() {
        var p = new MemoryPort(); var b = new SyncFolderBinding(p); b.restore(); b.choose(false, A, true, false);
        p.failSave = true; assertFalse(b.disconnect()); assertEquals(A, p.stored.uri()); assertEquals(0, p.releases);
    }
}
