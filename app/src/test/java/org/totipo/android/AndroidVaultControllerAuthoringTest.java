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

/** Real controller Authoring regressions; isolated fixture per test. */
public final class AndroidVaultControllerAuthoringTest extends AndroidVaultControllerFixture {
    @Test public void manualDefaultSameSessionObservedConcealedAndJavaTotp() throws Exception {
        create(); var session = real.session; var coordinator = real.coordinator;
        char[] input = rfcSecret();
        add(enrollment("RFC", "account", org.totipo.TotpAlgorithm.SHA1, 6, 30, input), AddTokenOutcome.Status.ADDED);
        await(() -> controller.snapshot().view().tokens().size() == 1);
        assertArrayEquals(new char[input.length], input);
        assertNull(controller.snapshot().revealedCode());
        var row = controller.snapshot().view().tokens().get(0);
        assertEquals("RFC", row.alternatives().get(0).issuer());
        assertEquals(30, row.alternatives().get(0).period().getSeconds());
        var state = session.state();
        assertEquals("287082", state.generateTotp(state.token(row.id()).orElseThrow().alternatives().get(0), java.time.Instant.ofEpochSecond(59)).code());
        assertSame(session, real.session); assertSame(coordinator, real.coordinator);
        assertEquals(1, real.creates); assertEquals(0, real.opens); assertEquals(0, real.closes);
        accept(() -> controller.showCode(row.id())); await(() -> controller.snapshot().revealedCode() != null);
        add(enrollment("RFC", "account", org.totipo.TotpAlgorithm.SHA1, 6, 30, rfcSecret()), AddTokenOutcome.Status.ADDED);
        assertNull(controller.snapshot().revealedCode());
        await(() -> controller.snapshot().view().tokens().size() == 2);
        assertEquals(2, controller.snapshot().view().tokens().stream().map(t -> t.id()).distinct().count());
        lock(); accept(() -> controller.unlock(password())); state(State.OPEN);
        await(() -> controller.snapshot().view().tokens().size() == 2);
    }
    @Test public void supportedAlgorithmsDigitsAndCustomPeriodThroughRealAuthoring() throws Exception {
        create(); int count = 0;
        for (var algorithm : org.totipo.TotpAlgorithm.values()) for (int digits : new int[]{6,7,8}) {
            add(enrollment("", "", algorithm, digits, 45, rfcSecret()), AddTokenOutcome.Status.ADDED);
            int expected = ++count;
            await(() -> controller.snapshot().view().tokens().size() == expected);
            assertTrue(controller.snapshot().view().tokens().stream().anyMatch(t -> t.alternatives().get(0).algorithm() == algorithm
                    && t.alternatives().get(0).digits() == digits && t.alternatives().get(0).period().getSeconds() == 45));
            var token = real.session.state().tokens().stream().filter(t -> t.alternatives().get(0).descriptor().algorithm() == algorithm
                    && t.alternatives().get(0).descriptor().digits() == digits).findFirst().orElseThrow();
            assertTrue(real.session.state().generateTotp(token.alternatives().get(0), java.time.Instant.ofEpochSecond(59)).code().matches("[0-9]{" + digits + "}"));
            assertNull(controller.snapshot().revealedCode());
        }
    }
    @Test public void malformedSecretNeverReachesEditorSaveAndWipesInput() throws Exception {
        create();
        for (String value : List.of("", "M0", "MZ", "MY=")) {
            char[] input = value.toCharArray();
            add(enrollment("RFC", "account", org.totipo.TotpAlgorithm.SHA1, 6, 30, input), AddTokenOutcome.Status.INVALID_SECRET);
            assertArrayEquals(new char[input.length], input);
            assertEquals(0, real.saves); assertTrue(controller.snapshot().view().tokens().isEmpty());
        }
    }
    @Test public void javaFieldBoundsNoTruncationOrNormalization() throws Exception {
        create();
        for (String field : List.of("x".repeat(257), "é".repeat(129), "\ud800")) {
            add(enrollment(field, "", org.totipo.TotpAlgorithm.SHA1, 6, 30, rfcSecret()), AddTokenOutcome.Status.INVALID_FIELDS);
            add(enrollment("", field, org.totipo.TotpAlgorithm.SHA1, 6, 30, rfcSecret()), AddTokenOutcome.Status.INVALID_FIELDS);
            assertTrue(controller.snapshot().view().tokens().isEmpty());
        }
        for (int digits : new int[]{5,9}) add(enrollment("", "", org.totipo.TotpAlgorithm.SHA1, digits, 30, rfcSecret()), AddTokenOutcome.Status.INVALID_FIELDS);
        for (long period : new long[]{0,4294967296L}) add(enrollment("", "", org.totipo.TotpAlgorithm.SHA1, 6, period, rfcSecret()), AddTokenOutcome.Status.INVALID_FIELDS);
        add(enrollment("", "", null, 6, 30, rfcSecret()), AddTokenOutcome.Status.INVALID_FIELDS);
        add(enrollment("é".repeat(128), "  account  ", org.totipo.TotpAlgorithm.SHA1, 6, 4294967295L, rfcSecret()), AddTokenOutcome.Status.ADDED);
        await(() -> controller.snapshot().view().tokens().size() == 1);
        var d = controller.snapshot().view().tokens().get(0).alternatives().get(0);
        assertEquals("é".repeat(128), d.issuer()); assertEquals("  account  ", d.account());
    }
    @Test public void uncertainPublicationKeepsActualOutcomeNoAutomaticRetryOrFabricatedRow() throws Exception {
        create(); real.tokenWriteFault = new org.totipo.spi.ObjectWrite.Failed(org.totipo.spi.StoreFailure.UNAVAILABLE);
        add(enrollment("", "", org.totipo.TotpAlgorithm.SHA1, 6, 30, rfcSecret()), AddTokenOutcome.Status.PUBLICATION_UNCERTAIN);
        assertEquals(1, real.saves); assertTrue(controller.snapshot().view().tokens().isEmpty());
        assertFalse(controller.snapshot().message().contains("Token added"));
        accept(() -> controller.refresh()); state(State.OPEN);
        assertEquals(AddTokenOutcome.Status.PUBLICATION_UNCERTAIN, controller.snapshot().addOutcome().status());
        real.tokenWriteFault = null;
    }
    @Test public void uncertaintyCanHavePersistedAndExplicitRefreshObservesSameSession() throws Exception {
        create(); var session = real.session;
        real.persistBeforeTokenFault = true;
        real.tokenWriteFault = new org.totipo.spi.ObjectWrite.Uncertain(org.totipo.spi.StoreFailure.UNAVAILABLE);
        add(enrollment("", "", org.totipo.TotpAlgorithm.SHA1, 6, 30, rfcSecret()), AddTokenOutcome.Status.PUBLICATION_UNCERTAIN);
        assertFalse(controller.snapshot().message().contains("Token added"));
        real.tokenWriteFault = null;
        accept(() -> controller.refresh());
        await(() -> controller.snapshot().state() == State.OPEN && controller.snapshot().view().tokens().size() == 1);
        assertEquals(AddTokenOutcome.Status.PUBLICATION_UNCERTAIN, controller.snapshot().addOutcome().status());
        assertSame(session, real.session); assertEquals(1, real.saves); assertNull(controller.snapshot().revealedCode());
    }
    @Test public void lockOwnershipRejectsAddBeforeClosureCompletes() throws Exception {
        create(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        real.operationHook = name -> {
            if (name.equals("close")) { entered.countDown();
                try { assertTrue(release.await(30, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new AssertionError(e); }
            }
        };
        accept(() -> controller.lock()); assertTrue(entered.await(10, TimeUnit.SECONDS));
        try {
            assertEquals(State.LOCKING, controller.snapshot().state());
            char[] text = rfcSecret();
            assertFalse(controller.addToken(enrollment("", "", org.totipo.TotpAlgorithm.SHA1, 6, 30, text)));
            assertArrayEquals(new char[text.length], text); assertEquals(0, real.saves);
        } finally { release.countDown(); real.operationHook = name -> {}; }
        state(State.LOCKED); assertNull(controller.snapshot().view()); assertEquals(0, real.saves);
    }
    @Test public void definiteJavaFailureReasonRetainedAndNoFabricatedRow() throws Exception {
        create();
        for (var reason : org.totipo.SaveResult.Reason.values()) {
            real.saveFailure = reason;
            add(enrollment("", "", org.totipo.TotpAlgorithm.SHA1, 6, 30, rfcSecret()), AddTokenOutcome.Status.FAILED);
            assertEquals(reason, controller.snapshot().addOutcome().reason());
            assertTrue(controller.snapshot().view().tokens().isEmpty());
        }
        real.saveFailure = null;
    }
    @Test public void busyAddRejectsDuplicateAndLockRetiresAfterInFlightSave() throws Exception {
        create(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        real.operationHook = name -> {
            if (name.equals("publishObject")) { entered.countDown();
                try { assertTrue(release.await(30, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new AssertionError(e); }
            }
        };
        accept(() -> controller.addToken(enrollment("", "", org.totipo.TotpAlgorithm.SHA1, 6, 30, rfcSecret())));
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        try {
            char[] duplicate = rfcSecret();
            assertFalse(controller.addToken(enrollment("", "", org.totipo.TotpAlgorithm.SHA1, 6, 30, duplicate)));
            assertArrayEquals(new char[duplicate.length], duplicate);
            assertTrue(controller.lock()); assertEquals(State.LOCKING, controller.snapshot().state());
            assertFalse(controller.refresh()); assertFalse(controller.sync(null));
            assertEquals(1, real.saves);
        } finally { release.countDown(); real.operationHook = name -> {}; }
        await(() -> idle() && controller.snapshot().state() == State.LOCKED);
        assertNull(controller.snapshot().view());
        char[] rejected = rfcSecret();
        assertFalse(controller.addToken(enrollment("", "", org.totipo.TotpAlgorithm.SHA1, 6, 30, rejected)));
        assertArrayEquals(new char[rejected.length], rejected); assertEquals(1, real.saves);
    }
    @Test public void productSourceBoundaryHasNoRawActivityOwnershipOrCredentialPersistence() throws Exception {
        String activity = Files.readString(Path.of("src/main/java/org/totipo/android/MainActivity.java"));
        for (String forbidden : List.of("VaultSession", "Totipo.open", "Totipo.create", "LocalReplicaOwner", "NioTotipoStore", "ForegroundVaultCoordinator", "Flow.", "Future", "SharedPreferences"))
            assertFalse(forbidden, java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(forbidden)).matcher(activity).find());
        for (var field : AndroidVaultController.class.getDeclaredFields()) assertNotEquals(char[].class, field.getType());
        String source = Files.readString(Path.of("src/main/java/org/totipo/android/AndroidVaultController.java"));
        int begin = source.indexOf("public synchronized boolean sync(Scan scan)");
        String sync = source.substring(begin, source.indexOf("public synchronized boolean lock()", begin));
        for (String forbidden : List.of("char[]", "password", "backend.open", "backend.create", "closeOwned")) assertFalse(forbidden, sync.contains(forbidden));
        assertTrue(source.contains("new ArrayBlockingQueue<>(1)"));
        assertFalse(source.contains("newSingleThreadExecutor"));
        assertTrue(activity.contains("setSaveEnabled(false)"));
        assertFalse(activity.contains("putString")); assertFalse(activity.contains("putCharArray"));
    }
}
