package org.totipo.android;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.totipo.android.sync.*;
import static org.junit.Assert.*;

/** Real registry inventory; metadata enumeration never constructs a controller. */
public final class SyncGrantOwnershipTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private VaultRegistry registry;
    private String a, b;
    private final Map<String, Metadata> ports = new LinkedHashMap<>();
    private final Set<String> grants = new HashSet<>();
    private boolean independent = true, releaseFails;
    private int releases;
    private SyncGrantOwnership ownership;
    private static final class Metadata implements SyncGrantOwnership.Metadata {
        SyncFolderBinding.Stored stored;
        Set<String> retained = new HashSet<>();
        boolean uncertain, failSave;
        public SyncFolderBinding.Stored load() { return stored; }
        public boolean uncertain() { return uncertain; }
        public Set<String> retained() { return Set.copyOf(retained); }
        public boolean retained(Set<String> value) { if (failSave) return false; retained = new HashSet<>(value); return true; }
    }
    @Before public void setup() throws Exception {
        registry = new VaultRegistry(temporary.newFolder().toPath());
        a = registry.reserve("A").id(); b = registry.reserve("B").id();
        ports.put(a, new Metadata()); ports.put(b, new Metadata());
        ownership = new SyncGrantOwnership(() -> {
            try {
                var result = new LinkedHashMap<String, SyncGrantOwnership.Metadata>();
                for (var entry : registry.bindingEntries()) result.put(entry.id(), ports.get(entry.id()));
                return result;
            } catch (Exception failure) { throw new IllegalStateException(failure); }
        }, new SyncGrantOwnership.Platform() {
            public boolean independent(String uri) { return independent; }
            public void release(String uri) { releases++; if (releaseFails) throw new SecurityException(); grants.remove(uri); }
        });
    }
    private void bind(String id, String uri) {
        ports.get(id).stored = new SyncFolderBinding.Stored(uri, true, true);
        grants.add(uri); ownership.remember(ports.get(id), uri);
    }
    @Test public void sharedGrantRetainedUntilLastRegisteredConsumerDisconnects() {
        bind(a, "shared"); bind(b, "shared"); ports.get(a).stored = null;
        ownership.cleanup(); assertTrue(grants.contains("shared")); assertEquals(0, releases);
        ports.get(b).stored = null; ownership.cleanup(); assertFalse(grants.contains("shared"));
        assertTrue(ports.get(a).retained.isEmpty()); assertTrue(ports.get(b).retained.isEmpty());
    }
    @Test public void admissionRetainsGrantUntilActualProviderReturnEvenAfterDisconnect() throws Exception {
        bind(a, "A"); var use = ownership.admit(a, "A");
        ports.get(a).stored = null; ownership.cleanup(); assertEquals(0, releases);
        assertThrows(RejectedExecutionException.class, () -> ownership.admit(a, "A"));
        use.close(); use.close(); assertEquals(1, releases); assertTrue(grants.isEmpty());
    }
    @Test public void actualProviderLaneHoldsSharedPermissionThroughRetirementDrain() throws Exception {
        bind(a, "A");
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var drained = new CountDownLatch(1);
        var port = new SyncFolderBindingTest.MemoryPort() {
            @Override public SyncGrantOwnership.Use retain(String uri) { return ownership.admit(a, uri); }
            @Override public void probe(String uri) {
                entered.countDown();
                try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
                catch (InterruptedException interrupted) { throw new AssertionError(interrupted); }
            }
        };
        var lane = new ProviderIoLane();
        lane.submit(port, new SyncFolderBinding.Request(0, "A"), false, value -> fail("Retired delivery"));
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        try {
            synchronized (ownership) { ports.get(a).stored = null; ownership.cleanup(); }
            lane.retire(drained::countDown); assertEquals(1, drained.getCount()); assertEquals(0, releases);
        } finally { release.countDown(); }
        assertTrue(drained.await(10, TimeUnit.SECONDS)); assertEquals(1, releases); assertTrue(grants.isEmpty());
    }
    @Test public void aliasesAndUnknownOverlapRetainDespiteDifferentUriStrings() {
        bind(a, "alias-A"); bind(b, "alias-B"); ports.get(a).stored = null;
        ownership.cleanup(); assertEquals(0, releases);
        ports.get(b).stored = null; independent = false;
        ownership.cleanup(); assertEquals(0, releases); assertEquals(2, grants.size());
    }
    @Test public void failedOrUncertainPersistenceRetainsOldAndCandidateAuthority() {
        bind(a, "old"); ownership.remember(ports.get(a), "candidate"); grants.add("candidate");
        ports.get(a).uncertain = true; ownership.cleanup(); assertEquals(0, releases);
        assertEquals("old", ports.get(a).stored.uri());
        assertThrows(RejectedExecutionException.class, () -> ownership.admit(a, "old"));
        ports.get(b).failSave = true;
        assertThrows(IllegalStateException.class, () -> ownership.remember(ports.get(b), "never acquired"));
        assertFalse(grants.contains("never acquired"));
    }
    @Test public void explicitRecoveryMayReplaceOwnUncertainBindingButNeverAnotherEntries() {
        bind(a, "old"); ports.get(a).uncertain = true;
        ownership.checkSelection(a, "recovered");
        assertThrows(IllegalStateException.class, () -> ownership.checkSelection(b, "old"));
        ports.get(a).uncertain = false;
        assertThrows(IllegalStateException.class, () -> ownership.checkSelection(b, "old"));
        ownership.checkSelection(b, "different");
    }
    @Test public void failedReleaseLeavesDurableVisibleCleanupRecordAndRetryWorks() {
        bind(a, "A"); ports.get(a).stored = null; releaseFails = true;
        ownership.cleanup(); assertTrue(grants.contains("A")); assertTrue(ports.get(a).retained.contains("A"));
        releaseFails = false; ownership.cleanup(); assertTrue(grants.isEmpty()); assertTrue(ports.get(a).retained.isEmpty());
    }
    @Test public void registryCorruptionNeverRevokesEvenWhenCachedEntriesLookEmpty() throws Exception {
        bind(a, "A"); ports.get(a).stored = null;
        // Corrupt the actual registry rather than substituting a permissive enumeration.
        var path = java.nio.file.Files.walk(temporary.getRoot().toPath()).filter(p -> p.getFileName().toString().equals("registry")).findFirst().orElseThrow();
        java.nio.file.Files.write(path, new byte[]{0}); ownership.cleanup(); assertEquals(0, releases);
        assertThrows(IllegalStateException.class, () -> ownership.admit(a, "A"));
    }
    @Test public void interruptedRegistryWriteNeverTriggersCleanup() throws Exception {
        bind(a, "A"); ports.get(a).stored = null;
        try (var paths = java.nio.file.Files.walk(temporary.getRoot().toPath())) {
            var path = paths.filter(p -> p.getFileName().toString().equals("registry")).findFirst().orElseThrow();
            java.nio.file.Files.write(path.resolveSibling("registry.next"), new byte[]{0});
        }
        ownership.cleanup(); assertEquals(0, releases); assertTrue(grants.contains("A"));
    }
    @Test public void configurationAndAdmissionShareOneCoherentBoundary() throws Exception {
        bind(a, "A"); var entered = new CountDownLatch(1); var proceed = new CountDownLatch(1);
        var denied = new AtomicBoolean();
        Thread thread;
        synchronized (ownership) {
            thread = new Thread(() -> { entered.countDown(); try { ownership.admit(a, "A"); }
                catch (RejectedExecutionException expected) { denied.set(true); } finally { proceed.countDown(); } });
            thread.start(); assertTrue(entered.await(10, TimeUnit.SECONDS));
            ports.get(a).stored = null;
        }
        assertTrue(proceed.await(10, TimeUnit.SECONDS)); thread.join(); assertTrue(denied.get());
        ownership.cleanup(); assertEquals(1, releases);
    }
}
