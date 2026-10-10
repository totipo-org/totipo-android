package org.totipo.android;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.Test;
import org.totipo.*;
import org.totipo.android.reconcile.ForegroundVaultCoordinator.ObservedToken;
import static org.junit.Assert.*;

/** Identity/state semantics plus framework wiring; measured view tests live in RowRevealRegression. */
public final class RowRevealCopyTest {
    private ObservedToken token(String id, boolean conflict, int digits) {
        var descriptor = new TokenDescriptor(TokenStatus.ACTIVE, "GitHub", "alice@example.com",
                TotpAlgorithm.SHA1, digits, Duration.ofSeconds(30));
        return new ObservedToken(new TokenId(id.repeat(64)), conflict ? List.of(descriptor, descriptor) : List.of(descriptor),
                List.of(), List.of(), conflict);
    }
    private String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/org/totipo/android/" + name + ".java"));
    }
    @Test public void presentationMatchesFullIdentityAndNeverConflictAlternatives() {
        var a = token("a", false, 6); var b = token("b", false, 6);
        var code = new RevealedTotp(a.id(), "123456", Instant.EPOCH, Instant.EPOCH.plusSeconds(30), 6);
        assertTrue(TokenListAdapter.displays(a, code)); assertFalse(TokenListAdapter.displays(b, code));
        assertFalse(TokenListAdapter.displays(token("a", true, 6), code));
        assertFalse(TokenListAdapter.displays(a, null));
        assertTrue(TokenChange.resolvable(token("a", true, 6)));
        assertTrue(TokenListAdapter.matchesSearch(a, "alice"));
        assertFalse(TokenListAdapter.matchesSearch(a, "other"));
    }
    @Test public void allSupportedCodeLengthsArePreservedByGrouping() {
        for (String value : List.of("012345", "0123456", "01234567")) {
            assertEquals(value, MainActivity.grouped(value).replace(" ", ""));
            assertEquals(value.length() + 1, MainActivity.grouped(value).length());
        }
    }
    @Test public void rowOwnsCodeAndSeparatePanelAndVisibleInstructionsAreAbsent() throws Exception {
        String activity = source("MainActivity"), row = source("TokenListAdapter");
        for (String forbidden : List.of("revealPanel", "Hide code", "new TextView[] {selected, code, remaining}"))
            assertFalse(forbidden, activity.contains(forbidden));
        for (String forbidden : List.of("Show code", "Tap to reveal", "Tap to copy")) assertFalse(row.contains(forbidden));
        assertTrue(row.contains("value.addView(code); value.addView(countdownLine)"));
        assertTrue(row.contains("String formatted = revealed ? MainActivity.grouped(shown.code()) : \"\""));
        assertTrue(row.contains("row.value.setVisibility(revealed ? View.VISIBLE : View.GONE)"));
    }
    @Test public void tapChecksCurrentControllerIdentityAndCopyDoesNotRevealOrHide() throws Exception {
        String activity = source("MainActivity");
        String tap = activity.substring(activity.indexOf("private void tapToken"), activity.indexOf("private void clearCodeWidgets"));
        assertTrue(tap.contains("controller.snapshot().revealedCode()"));
        assertTrue(tap.contains("shown.tokenId().equals(id)"));
        assertTrue(tap.contains("controller.copyShownCode()")); assertTrue(tap.contains("controller.showCode(id)"));
        assertTrue(tap.contains("Toast.LENGTH_SHORT")); assertFalse(tap.contains("hideCode"));
        String row = source("TokenListAdapter");
        assertTrue(row.contains("row.more.setOnClickListener(ignored -> overflow(row.more, token.id()).show())"));
        assertTrue(row.contains("if (token.conflict()) change.accept(token.id(), TokenChange.Kind.RESOLVE)"));
    }
    @Test public void filteredPresentationRetiresThroughExistingPolicyAndEveryBindClearsSecrets() throws Exception {
        String row = source("TokenListAdapter");
        assertTrue(row.contains("tokens.stream().noneMatch(t -> displays(t, shown))"));
        assertTrue(row.contains("updateRevealPresentation(null, 0); retire.run()"));
        assertTrue(row.contains("if (!hadShown) retire.run()")); // pending generation revoked on search
        assertTrue(source("MainActivity").contains("this::openTokenChange, controller::hideCode"));
        assertTrue(row.contains("row.countdown.setText(revealed ? seconds + \" s\" : \"\")"));
        assertTrue(row.contains("else row = (Row) convertView"));
        assertFalse(row.contains("setHasStableIds"));
    }
    @Test public void accessibleActionsHaveNoSecretLabelsAndCountdownIsQuiet() throws Exception {
        String row = source("TokenListAdapter");
        assertTrue(row.contains("revealed ? \"Copy code\" : \"Reveal code\""));
        assertTrue(row.contains("AccessibilityNodeInfo.ACTION_CLICK, action"));
        assertTrue(row.contains("row.body.setContentDescription(rowText(token))"));
        assertTrue(row.contains("text.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_NONE)"));
        assertFalse(row.contains("announceForAccessibility")); assertFalse(row.contains("ACCESSIBILITY_LIVE_REGION_POLITE"));
        assertTrue(row.contains("text.setSaveEnabled(false); text.setFreezesText(false)"));
    }

    @Test public void actualPeriodDefinesFraction() {
        for (long period : new long[]{30, 45, 60, 4294967295L}) {
            assertEquals(1f, CountdownRingView.fraction(period, period), 0f);
            assertEquals((float) ((double) (period / 2) / period), CountdownRingView.fraction(period / 2, period), 0f);
            assertEquals(1f / period, CountdownRingView.fraction(1, period), 0.000001f);
            assertEquals(0f, CountdownRingView.fraction(0, period), 0f);
        }
    }
    @Test public void invalidAndOutOfRangeInputIsClamped() {
        assertEquals(0f, CountdownRingView.fraction(10, 0), 0f);
        assertEquals(0f, CountdownRingView.fraction(10, -1), 0f);
        assertEquals(0f, CountdownRingView.fraction(-1, 30), 0f);
        assertEquals(1f, CountdownRingView.fraction(31, 30), 0f);
        assertEquals(1f, CountdownRingView.fraction(Long.MAX_VALUE, 30), 0f);
        assertTrue(Float.isFinite(CountdownRingView.fraction(Long.MAX_VALUE, Long.MAX_VALUE)));
    }
}
