package org.totipo.android.reconcile;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.totipo.*;
import org.totipo.android.*;
import static org.junit.Assert.*;
import static org.totipo.android.reconcile.ForegroundVaultCoordinator.*;

public final class TotpCoordinatorTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private LocalReplicaOwner owner;
    private ForegroundVaultCoordinator vault;
    private VaultSession session;
    private int generations;
    private Runnable duringGenerate = () -> {};
    private boolean failGenerate;
    private TokenId id;
    private final Operations ops = new Operations() {
        CreateVaultResult create(CoordinatedPrivateStore store, char[] password) {
            var result = super.create(store, password);
            session = ((CreateVaultResult.Created) result).session(); return result;
        }
        TotpCode generateTotp(VaultState state, TokenAlternative alternative, Instant now) {
            generations++;
            if (failGenerate) throw new IllegalStateException("Internal failure");
            var result = super.generateTotp(state, alternative, now); duringGenerate.run(); return result;
        }
    };
    @Before public void setup() throws Exception {
        owner = TestReplicaOwners.create(temporary.newFolder().toPath());
        var created = ForegroundVaultCoordinator.create(owner, "M2A disposable".toCharArray(), ops);
        assertNull(created.cause()); vault = created.vault();
        id = author(session, TotpAlgorithm.SHA1, 8, 30, "12345678901234567890");
    }
    @After public void close() { if (vault != null) vault.close(); }
    public static void await(VaultSession session, Predicate<VaultState> predicate) {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
        session.states().subscribe(new Flow.Subscriber<>() {
            public void onSubscribe(Flow.Subscription value) { subscription.set(value); value.request(Long.MAX_VALUE); }
            public void onNext(VaultState state) { if (predicate.test(state)) done.countDown(); }
            public void onError(Throwable ignored) { done.countDown(); }
            public void onComplete() { done.countDown(); }
        });
        try { assertTrue(done.await(20, TimeUnit.SECONDS)); assertTrue(predicate.test(session.state())); }
        catch (InterruptedException e) { throw new AssertionError(e); }
        finally { subscription.get().cancel(); }
    }
    public static TokenId author(VaultSession session, TotpAlgorithm algorithm, int digits, long period, String publicSecret) {
        byte[] bytes = publicSecret.getBytes(StandardCharsets.US_ASCII);
        TokenId id;
        try (var secret = NewSecret.copyOf(bytes); var editor = session.state().createToken()) {
            id = ((SaveResult.Saved) editor.issuer("RFC fixture").account("Disposable account")
                    .algorithm(algorithm).digits(digits).period(Duration.ofSeconds(period)).secret(secret).save()).tokenId();
        } finally { Arrays.fill(bytes, (byte) 0); }
        await(session, state -> state.observation() instanceof ObservationProgress.Finished && state.token(id).isPresent());
        return id;
    }
    @Test public void actualRfc6238VectorAndDetachedLifetime() {
        var result = vault.generateTotp(id, Instant.ofEpochSecond(59));
        assertEquals(TotpStatus.AVAILABLE, result.status());
        assertEquals("94287082", result.revealed().code());
        assertEquals(Instant.ofEpochSecond(30), result.revealed().validFrom());
        assertEquals(Instant.ofEpochSecond(60), result.revealed().validUntil());
        assertEquals(8, result.revealed().digits()); assertEquals(1, generations);
        assertEquals(State.OPEN, vault.lifecycle());
    }
    @Test public void actualRfcSha256AndSha512Vectors() {
        var sha256 = author(session, TotpAlgorithm.SHA256, 8, 30, "12345678901234567890123456789012");
        assertEquals("46119246", vault.generateTotp(sha256, Instant.ofEpochSecond(59)).revealed().code());
        var sha512 = author(session, TotpAlgorithm.SHA512, 8, 30, "1234567890123456789012345678901234567890123456789012345678901234");
        assertEquals("90693936", vault.generateTotp(sha512, Instant.ofEpochSecond(59)).revealed().code());
    }
    @Test public void actualDescriptorPeriodAndSixDigitsNotAssumed() {
        var other = author(session, TotpAlgorithm.SHA1, 6, 15, "12345678901234567890");
        var result = vault.generateTotp(other, Instant.ofEpochSecond(59)).revealed();
        assertEquals(6, result.digits()); assertEquals(6, result.code().length());
        assertEquals(Instant.ofEpochSecond(45), result.validFrom()); assertEquals(Instant.ofEpochSecond(60), result.validUntil());
    }
    @Test public void missingTokenRefusesWithoutCrypto() {
        assertEquals(TotpStatus.UNAVAILABLE_NEEDS_ATTENTION, vault.generateTotp(new TokenId("f".repeat(64)), Instant.ofEpochSecond(59)).status());
        assertEquals(0, generations);
    }
    @Test public void tombstoneRefusesWithoutCrypto() {
        try (var editor = session.state().update(session.state().token(id).orElseThrow().heads().get(0))) {
            assertTrue(editor.status(TokenStatus.TOMBSTONED).save() instanceof SaveResult.Saved);
        }
        await(session, s -> s.token(id).orElseThrow().alternatives().get(0).descriptor().status() == TokenStatus.TOMBSTONED);
        assertEquals(TotpStatus.UNAVAILABLE_NEEDS_ATTENTION, vault.generateTotp(id, Instant.ofEpochSecond(59)).status());
        assertEquals(0, generations);
    }
    @Test public void twoRealCurrentAlternativesRefuseWithoutArbitraryChoice() {
        var captured = session.state(); var head = captured.token(id).orElseThrow().heads().get(0);
        try (var first = captured.update(head); var second = captured.update(head)) {
            assertTrue(first.account("Branch A").save() instanceof SaveResult.Saved);
            assertTrue(second.account("Branch B").save() instanceof SaveResult.Saved);
        }
        await(session, s -> s.token(id).orElseThrow().hasConflict());
        assertEquals(TotpStatus.UNAVAILABLE_NEEDS_ATTENTION, vault.generateTotp(id, Instant.ofEpochSecond(59)).status());
        assertEquals(0, generations);
    }
    @Test public void missingParentRealJavaStateRefuses() throws Exception {
        var parent = session.state().token(id).orElseThrow().heads().get(0).revision();
        try (var editor = session.state().update(session.state().token(id).orElseThrow().heads().get(0))) {
            assertTrue(editor.account("Child").save() instanceof SaveResult.Saved);
        }
        await(session, s -> s.token(id).orElseThrow().alternatives().get(0).descriptor().account().equals("Child"));
        // Test-only absence fixture; no Android protocol encoding or independent production writer.
        var root = temporary.getRoot().toPath();
        try (var paths = Files.walk(root)) {
            var parentFile = paths.filter(p -> p.getFileName().toString().equals(parent.hex())).findFirst().orElseThrow();
            Files.delete(parentFile);
        }
        vault.requestRefresh();
        await(session, s -> !s.token(id).orElseThrow().unresolvedReferences().isEmpty());
        assertEquals(TotpStatus.UNAVAILABLE_NEEDS_ATTENTION, vault.generateTotp(id, Instant.ofEpochSecond(59)).status());
        assertEquals(0, generations);
    }
    @Test public void authoritativeStateChangesDuringGenerationRejectResult() {
        duringGenerate = () -> author(session, TotpAlgorithm.SHA1, 8, 30, "12345678901234567890");
        assertEquals(TotpStatus.STALE, vault.generateTotp(id, Instant.ofEpochSecond(59)).status());
        assertEquals(State.OPEN, vault.lifecycle());
    }
    @Test public void supersededDetachedRowRefusesBeforeCryptoEvenWithoutObservationCallback() {
        var row = vault.view().tokens().get(0);
        try (var editor = session.state().update(session.state().token(id).orElseThrow().heads().get(0))) {
            assertTrue(editor.account("New descriptor").save() instanceof SaveResult.Saved);
        }
        await(session, state -> state.token(id).orElseThrow().alternatives().get(0).descriptor().account().equals("New descriptor"));
        assertEquals(TotpStatus.STALE, vault.generateTotp(id, Instant.ofEpochSecond(59), row).status());
        assertEquals(0, generations);
    }
    @Test public void cryptoFailureIsSafeAndSessionRemainsOpen() {
        failGenerate = true; var result = vault.generateTotp(id, Instant.ofEpochSecond(59));
        assertEquals(TotpStatus.FAILED, result.status()); assertNull(result.revealed());
        assertEquals(State.OPEN, vault.lifecycle()); assertFalse(result.toString().contains("Internal failure"));
    }
    @Test public void closedCoordinatorRejectsGeneration() {
        vault.close(); assertThrows(IllegalStateException.class, () -> vault.generateTotp(id, Instant.ofEpochSecond(59)));
    }
    @Test public void detachedProjectionOrderedByCanonicalId() {
        author(session, TotpAlgorithm.SHA1, 8, 30, "12345678901234567890");
        var ids = vault.view().tokens().stream().map(t -> t.id().hex()).toList();
        assertEquals(ids.stream().sorted().toList(), ids);
    }
}
