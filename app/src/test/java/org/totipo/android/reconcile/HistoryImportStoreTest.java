package org.totipo.android.reconcile;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.zip.ZipInputStream;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.totipo.*;
import org.totipo.backup.*;
import org.totipo.spi.*;
import org.totipo.storage.nio.*;
import static org.junit.Assert.*;

/** Real released session/plan and coordinated move-based NIO publication, with scoped faults. */
public final class HistoryImportStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    public static byte[] zip(String fixture) throws IOException {
        try (var in = HistoryImportStoreTest.class.getResourceAsStream("/backup-profile-1/" + fixture + ".zip")) {
            return Objects.requireNonNull(in).readAllBytes();
        }
    }
    public static SortedMap<String, byte[]> contents(String fixture) throws IOException {
        var map = new TreeMap<String, byte[]>();
        try (var in = new ZipInputStream(new ByteArrayInputStream(zip(fixture)))) {
            for (var entry = in.getNextEntry(); entry != null; entry = in.getNextEntry())
                if (!entry.getName().equals("totipo-backup.txt")) map.put(entry.getName(), in.readAllBytes());
        }
        return map;
    }
    private Path root;
    private CoordinatedPrivateStore domain;
    private VaultSession session;
    private final AtomicInteger writes = new AtomicInteger();
    private java.util.function.BiFunction<ObjectName, byte[], ObjectWrite> fault;
    private volatile String unavailableName;
    private volatile byte[] substitutedVault;
    private StorageDurability durability = new NioDurability();
    private void open(String fixture, boolean objects) throws Exception {
        root = temporary.newFolder().toPath();
        for (var e : contents(fixture).entrySet()) if (objects || e.getKey().equals("vault")) {
            var file = root.resolve(e.getKey()); Files.createDirectories(file.getParent()); Files.write(file, e.getValue());
        }
        var delegate = NioStoreComposition.coordinatedDelegate(root, durability);
        var proxy = (TotipoStore) java.lang.reflect.Proxy.newProxyInstance(TotipoStore.class.getClassLoader(), new Class<?>[]{TotipoStore.class},
                (ignored, method, args) -> {
                    if (method.getName().equals("readObject") && ((ObjectName) args[0]).value().equals(unavailableName))
                        return new BoundedRead.Unavailable(StoreFailure.UNAVAILABLE);
                    if (method.getName().equals("readVault") && substitutedVault != null) return new BoundedRead.Present(substitutedVault);
                    if (method.getName().equals("publishObject")) {
                        writes.incrementAndGet();
                        if (fault != null) {
                            var answer = fault.apply((ObjectName) args[0], (byte[]) args[1]);
                            if (answer != null) return answer;
                        }
                    }
                    try { return method.invoke(delegate, args); }
                    catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
                });
        domain = new CoordinatedPrivateStore(proxy);
        session = ((OpenResult.Opened) Totipo.open(domain.transferSessionView(), "fixture password".toCharArray())).session();
    }
    private BackupImport plan(String fixture) throws IOException {
        return session.prepareImport(new ByteArrayInputStream(zip(fixture)), BackupLimits.DEFAULT);
    }
    private void verifyPreserved(Map<String, byte[]> expected) throws IOException {
        for (var e : expected.entrySet()) assertArrayEquals(e.getKey(), e.getValue(), Files.readAllBytes(root.resolve(e.getKey())));
    }
    @After public void close() { if (session != null) session.close(); if (domain != null) domain.close(); }
    @Test public void emptyAndExactExistingAreNoOpAndAllCanonicalBytesSurvive() throws Exception {
        open("history", true); var before = contents("history");
        try (var empty = plan("empty")) { assertEquals(ImportResult.Outcome.NO_OP, empty.apply().outcome()); }
        try (var exact = plan("history")) {
            assertTrue(exact.summary().sourceAuthenticated());
            assertEquals(ImportResult.Outcome.NO_OP, exact.apply().outcome());
            assertEquals(ImportResult.Outcome.NO_OP, exact.apply().outcome());
        }
        verifyPreserved(before);
    }
    @Test public void goldenMissingAncestorsBecomeCompleteAndAllHistoricalBytesRemainExact() throws Exception {
        open("missing-parent", true); var before = contents("missing-parent");
        try (var plan = plan("history")) {
            assertEquals(ImportResult.Outcome.COMPLETE, plan.apply().outcome());
            assertEquals(ImportResult.Outcome.COMPLETE, plan.apply().outcome()); // cumulative handle knowledge
        }
        verifyPreserved(before); verifyPreserved(contents("history"));
        try (var repeated = plan("history")) { assertEquals(ImportResult.Outcome.NO_OP, repeated.apply().outcome()); }
        session.requestRefresh();
        await(() -> !session.state().tokens().isEmpty());
        assertTrue(session.state().diagnostics().isEmpty());
    }
    @Test public void olderArchiveDoesNotRollbackNewerDescendants() throws Exception {
        open("history", true); var before = contents("history");
        await(() -> session.state().observation() instanceof ObservationProgress.Finished);
        var token = session.state().tokens().stream().filter(t -> t.alternatives().stream().anyMatch(a -> a.descriptor().status() == TokenStatus.ACTIVE)).findFirst().orElseThrow();
        var live = token.alternatives().stream().filter(a -> a.descriptor().status() == TokenStatus.ACTIVE).findFirst().orElseThrow();
        assertTrue(session.state().update(live).issuer("Newer").save() instanceof SaveResult.Saved);
        var all = new TreeMap<String, byte[]>();
        try (var files = Files.list(root.resolve("objects-v1"))) { for (var f : files.toList()) all.put("objects-v1/" + f.getFileName(), Files.readAllBytes(f)); }
        try (var plan = plan("history")) { assertEquals(ImportResult.Outcome.NO_OP, plan.apply().outcome()); }
        verifyPreserved(before); verifyPreserved(all);
    }
    @Test public void invalidAndDifferentVaultPreparationNeverWrites() throws Exception {
        open("empty", false); byte[] invalid = zip("history"); invalid[0] ^= 1;
        assertThrows(BackupException.class, () -> session.prepareImport(new ByteArrayInputStream(invalid), BackupLimits.DEFAULT));
        var otherRoot = temporary.newFolder().toPath();
        try (var other = NioStoreComposition.coordinatedDelegate(otherRoot, new NioDurability())) {
            var created = (CreateVaultResult.Created) Totipo.create(other, "fixture password".toCharArray());
            try (var foreign = created.session(); var capture = foreign.captureBackup(BackupLimits.DEFAULT)) {
                var bytes = new ByteArrayOutputStream(); capture.writeTo(bytes);
                var failure = assertThrows(BackupException.class, () -> session.prepareImport(new ByteArrayInputStream(bytes.toByteArray()), BackupLimits.DEFAULT));
                assertEquals(BackupException.Reason.VAULT_CHANGED, failure.reason());
            }
        }
        assertEquals(0, writes.get()); verifyPreserved(contents("empty"));
    }
    @Test public void wrongKindAndDifferentExistingAreRefusedBeforeAnyWrites() throws Exception {
        open("empty", false); var entry = contents("history").entrySet().stream().filter(e -> e.getKey().startsWith("objects")).findFirst().orElseThrow();
        var target = root.resolve(entry.getKey()); Files.createDirectories(target);
        assertThrows(BackupException.class, () -> plan("history")); assertEquals(0, writes.get());
        Files.delete(target); Files.write(target, new byte[1024]);
        assertThrows(BackupException.class, () -> plan("history")); assertEquals(0, writes.get()); assertArrayEquals(new byte[1024], Files.readAllBytes(target));
    }
    @Test public void changedTargetAfterPreparationIsRepreflightedBeforePublication() throws Exception {
        open("empty", false);
        try (var plan = plan("history")) {
            var name = contents("history").keySet().stream().filter(n -> n.startsWith("objects")).reduce((a,b) -> b).orElseThrow();
            Files.createDirectories(root.resolve("objects-v1")); Files.write(root.resolve(name), new byte[1024]);
            assertEquals(ImportResult.Outcome.FAILED, plan.apply().outcome()); assertEquals(0, writes.get());
        }
    }
    @Test public void definitePartialAndLostAcknowledgementRetrySameFrozenEvidence() throws Exception {
        for (boolean uncertain : List.of(false, true)) {
            if (session != null) { close(); session = null; domain = null; }
            open("empty", false);
            try (var plan = plan("history")) {
                fault = (name, bytes) -> {
                    if (writes.get() != 2) return null;
                    if (uncertain) {
                        try { Files.write(root.resolve("objects-v1").resolve(name.value()), bytes); }
                        catch (IOException failure) { throw new UncheckedIOException(failure); }
                        return new ObjectWrite.Uncertain(StoreFailure.UNAVAILABLE);
                    }
                    return new ObjectWrite.Failed(StoreFailure.UNAVAILABLE);
                };
                writes.set(0);
                assertEquals(uncertain ? ImportResult.Outcome.UNCERTAIN : ImportResult.Outcome.PARTIAL, plan.apply().outcome());
                fault = null;
                assertEquals(ImportResult.Outcome.COMPLETE, plan.apply().outcome());
                verifyPreserved(contents("history"));
            }
        }
    }
    @Test public void concurrentSameOrDifferentPublisherCannotOverwrite() throws Exception {
        for (boolean same : List.of(true, false)) {
            if (session != null) { close(); session = null; domain = null; }
            open("empty", false); writes.set(0);
            try (var plan = plan("history")) {
                fault = (name, bytes) -> {
                    if (writes.get() != 1) return null;
                    try {
                        Files.createDirectories(root.resolve("objects-v1"));
                        Files.write(root.resolve("objects-v1").resolve(name.value()), same ? bytes : new byte[1024], StandardOpenOption.CREATE_NEW);
                    } catch (IOException failure) { throw new UncheckedIOException(failure); }
                    return null;
                };
                var result = plan.apply();
                assertEquals(same ? ImportResult.Outcome.COMPLETE : ImportResult.Outcome.FAILED, result.outcome());
                if (same) verifyPreserved(contents("history"));
                else assertEquals(1, writes.get());
            }
            fault = null;
        }
    }
    @Test public void cancellationRefusesFutureWritesAndSessionClosureRetiresHandle() throws Exception {
        open("empty", false);
        try (var plan = plan("history")) {
            var cancelled = new AtomicBoolean(); domain.publicationCancellation(cancelled::get);
            fault = (name, bytes) -> { cancelled.set(true); return null; };
            assertEquals(ImportResult.Outcome.PARTIAL, plan.apply().outcome()); assertEquals(1, writes.get());
            domain.publicationCancellation(() -> false); fault = null;
            assertEquals(ImportResult.Outcome.COMPLETE, plan.apply().outcome());
            session.close(); assertThrows(RuntimeException.class, plan::apply);
        }
    }
    @Test public void importedBranchesTombstoneAndEqualValuedDistinctHeadsUseOrdinaryObservation() throws Exception {
        open("history", true);
        await(() -> session.state().observation() instanceof ObservationProgress.Finished);
        var baseState = session.state();
        var base = baseState.tokens().stream().flatMap(t -> t.alternatives().stream())
                .filter(a -> a.descriptor().status() == TokenStatus.ACTIVE).findFirst().orElseThrow();
        var a = baseState.update(base).issuer("branch-A"); var b = baseState.update(base).issuer("branch-B");
        assertTrue(a.save() instanceof SaveResult.Saved); assertTrue(b.save() instanceof SaveResult.Saved);
        await(() -> session.state().tokens().stream().flatMap(t -> t.alternatives().stream()).anyMatch(v -> v.descriptor().issuer().equals("branch-B")));
        var branched = session.state();
        var left = branched.tokens().stream().flatMap(t -> t.alternatives().stream()).filter(v -> v.descriptor().issuer().equals("branch-A")).findFirst().orElseThrow();
        var right = branched.tokens().stream().flatMap(t -> t.alternatives().stream()).filter(v -> v.descriptor().issuer().equals("branch-B")).findFirst().orElseThrow();
        assertTrue(branched.update(left).issuer("equal").save() instanceof SaveResult.Saved);
        assertTrue(branched.update(right).issuer("equal").save() instanceof SaveResult.Saved);
        assertTrue(baseState.update(base).status(TokenStatus.TOMBSTONED).save() instanceof SaveResult.Saved);
        await(() -> session.state().tokens().stream().flatMap(t -> t.alternatives().stream()).anyMatch(v -> v.descriptor().status() == TokenStatus.TOMBSTONED));
        var bytes = new ByteArrayOutputStream();
        try (var capture = session.captureBackup(BackupLimits.DEFAULT)) { capture.writeTo(bytes); }
        close(); session = null; domain = null;
        open("empty", false);
        try (var plan = session.prepareImport(new ByteArrayInputStream(bytes.toByteArray()), BackupLimits.DEFAULT)) {
            assertEquals(ImportResult.Outcome.COMPLETE, plan.apply().outcome());
        }
        session.requestRefresh();
        await(() -> session.state().tokens().stream().flatMap(t -> t.alternatives().stream()).anyMatch(v -> v.descriptor().status() == TokenStatus.TOMBSTONED));
        assertTrue(session.state().tokens().stream().anyMatch(t -> t.alternatives().size() > 1));
        assertTrue(session.state().tokens().stream().flatMap(t -> t.alternatives().stream())
                .anyMatch(v -> v.descriptor().issuer().equals("equal") && v.heads().size() == 2));
    }
    @Test public void actualNioLostDurabilityAcknowledgementIsUncertainUntilExplicitReplay() throws Exception {
        var lose = new AtomicBoolean();
        durability = path -> { new NioDurability().syncDirectory(path); if (path.getFileName().toString().equals("objects-v1") && lose.get()) {
            try (var files = Files.list(path)) {
                if (files.anyMatch(f -> f.getFileName().toString().matches("[0-9a-f]{64}")) && lose.getAndSet(false))
                    throw new IOException("lost acknowledgement");
            }
        } };
        open("empty", false);
        try (var plan = plan("history")) {
            lose.set(true); assertEquals(ImportResult.Outcome.UNCERTAIN, plan.apply().outcome());
            assertEquals(ImportResult.Outcome.COMPLETE, plan.apply().outcome()); verifyPreserved(contents("history"));
        }
    }
    @Test public void authenticatedInvalidAndUnsupportedArchiveNeverWrite() throws Exception {
        open("empty", false);
        for (boolean compressed : List.of(false, true)) {
            var entries = contents("history");
            byte[] manifest;
            try (var in = new ZipInputStream(new ByteArrayInputStream(zip("history")))) { in.getNextEntry(); manifest = in.readAllBytes(); }
            // Preserve profile order: manifest, vault, objects in lexical order.
            var all = new LinkedHashMap<String, byte[]>(); all.put("totipo-backup.txt", manifest); all.put("vault", entries.remove("vault")); all.putAll(entries);
            var output = new ByteArrayOutputStream();
            try (var zip = new java.util.zip.ZipOutputStream(output)) {
                for (var e : all.entrySet()) {
                    byte[] value = e.getValue().clone();
                    if (!compressed && e.getKey().equals(entries.lastKey())) value[10] ^= 1;
                    var ze = new java.util.zip.ZipEntry(e.getKey()); ze.setTimeLocal(java.time.LocalDateTime.of(1980,1,2,0,0));
                    if (!compressed) { ze.setMethod(java.util.zip.ZipEntry.STORED); ze.setSize(value.length); var crc = new java.util.zip.CRC32(); crc.update(value); ze.setCrc(crc.getValue()); }
                    zip.putNextEntry(ze); zip.write(value); zip.closeEntry();
                }
            }
            if (!compressed) BackupArchives.inspect(new ByteArrayInputStream(output.toByteArray()), BackupLimits.DEFAULT);
            assertThrows(BackupException.class, () -> session.prepareImport(new ByteArrayInputStream(output.toByteArray()), BackupLimits.DEFAULT));
        }
        assertEquals(0, writes.get());
    }
    @Test public void sessionCloseWaitsForEnteredPublicationAndDoesNotRollItBack() throws Exception {
        open("empty", false);
        var entered = new CountDownLatch(1); var leave = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try (var plan = plan("history")) {
            fault = (name, bytes) -> {
                entered.countDown();
                try { assertTrue(leave.await(10, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new AssertionError(e); }
                return null;
            };
            var applying = pool.submit(plan::apply); assertTrue(entered.await(10, TimeUnit.SECONDS));
            var closing = pool.submit(() -> session.close());
            assertFalse(closing.isDone()); leave.countDown();
            var result = applying.get(10, TimeUnit.SECONDS); closing.get(10, TimeUnit.SECONDS);
            assertTrue(result.outcome() == ImportResult.Outcome.PARTIAL || result.outcome() == ImportResult.Outcome.COMPLETE);
            assertTrue(Files.isDirectory(root.resolve("objects-v1"))); assertThrows(RuntimeException.class, plan::apply);
        } finally { leave.countDown(); pool.shutdownNow(); }
    }
    @Test public void unavailableTargetAndChangedBootstrapFailWithoutCanonicalWrites() throws Exception {
        open("empty", false);
        unavailableName = contents("history").keySet().stream().filter(n -> n.startsWith("objects"))
                .findFirst().orElseThrow().substring("objects-v1/".length());
        assertThrows(BackupException.class, () -> plan("history")); assertEquals(0, writes.get()); unavailableName = null;
        try (var plan = plan("history")) {
            // Defensive exact-bootstrap refusal through the store seam, without inventing a hash collision.
            substitutedVault = contents("empty").get("vault").clone(); substitutedVault[86] ^= 1;
            var result = plan.apply(); assertEquals(ImportResult.Outcome.FAILED, result.outcome());
            assertEquals(ImportResult.Reason.VAULT_CHANGED, result.reason()); assertEquals(0, writes.get());
        }
        substitutedVault = null; verifyPreserved(contents("empty"));
    }
    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(2);
        assertTrue(condition.getAsBoolean());
    }
}
