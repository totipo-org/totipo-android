package org.totipo.android;

import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import org.junit.Test;
import org.totipo.*;
import org.totipo.android.reconcile.ForegroundVaultCoordinator.ObservedToken;
import static org.junit.Assert.*;

/** Platform adapter source guards supplement the real Android debug adapter checks. */
public final class TotpSourceGuardTest {
    @Test public void rowsUseDetachedDescriptorsAndSummarizeAmbiguity() {
        var descriptor = new TokenDescriptor(TokenStatus.ACTIVE, "Issuer", "Account", TotpAlgorithm.SHA256, 7, Duration.ofSeconds(45));
        var token = new ObservedToken(new TokenId("a".repeat(64)), List.of(descriptor), List.of(), List.of(), false);
        assertEquals("Issuer\nAccount", TokenListAdapter.rowText(token)); assertTrue(TokenListAdapter.usable(token));
        var conflict = new ObservedToken(token.id(), List.of(descriptor, descriptor), List.of(), List.of(), true);
        assertTrue(TokenListAdapter.rowText(conflict).startsWith("Token conflict\nIssuer — Account")); assertFalse(TokenListAdapter.usable(conflict));
    }
    @Test public void completeListRecyclingAndEmptyStateAreWiredToPlatform() throws Exception {
        String adapter = read("TokenListAdapter"), activity = read("MainActivity");
        assertTrue(adapter.contains("return tokens.size()")); assertTrue(adapter.contains("if (convertView == null)"));
        assertTrue(adapter.contains("else row = (Row) convertView")); assertTrue(adapter.contains("return row;"));
        // The row binder must not truncate the token list. Countdown width measurement
        // separately iterates decimal digits, independently of list size.
        String binding = adapter.substring(adapter.indexOf("public View getView"), adapter.indexOf("static int countdownWidth"));
        assertFalse(binding.contains("for (")); assertFalse(adapter.contains(".limit("));
        assertTrue(activity.contains("new ListView(this)")); assertTrue(activity.contains("No matching tokens"));
        assertTrue(activity.contains("list.setEmptyView(empty)"));
        assertEquals("123 456", MainActivity.grouped("123456"));
        assertEquals("1234 567", MainActivity.grouped("1234567"));
        assertEquals("1234 5678", MainActivity.grouped("12345678"));
    }
    @Test public void noSecretLogsOrRawJavaOwnershipInActivityAndPresentation() throws Exception {
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String code = Files.readString(path);
                for (String forbidden : List.of("android.util.Log", "System.out", "System.err", "printStackTrace", "java.util.logging",
                        "javax.crypto", "Mac.getInstance", "94287082", "12345678901234567890"))
                    assertFalse(path + ": " + forbidden, code.contains(forbidden));
            }
        }
        for (String name : List.of("MainActivity", "TokenListAdapter", "RevealedTotp", "TotpPresentation")) {
            String source = read(name);
            for (String forbidden : List.of("VaultSession", "VaultState", "TokenAlternative", "NewSecret", "LocalReplicaOwner"))
                assertFalse(name + ": " + forbidden, source.contains(forbidden));
        }
        String coordinator = Files.readString(Path.of("src/main/java/org/totipo/android/reconcile/ForegroundVaultCoordinator.java"));
        assertTrue(coordinator.contains("state.generateTotp(alternative, now)"));
        assertTrue(coordinator.contains("session.state() != captured"));
        assertFalse(read("TotpPresentation").contains("generateTotp"));
    }
    @Test public void secureWindowNoSavedCodeAndNoLiveSecretAnnouncements() throws Exception {
        String activity = read("MainActivity");
        assertTrue(activity.contains("FLAG_SECURE")); assertTrue(read("TokenListAdapter").contains("text.setSaveEnabled(false)"));
        assertTrue(read("TokenListAdapter").contains("text.setFreezesText(false)")); assertTrue(read("TokenListAdapter").contains("ACCESSIBILITY_LIVE_REGION_NONE"));
        assertFalse(activity.contains("putString(")); assertFalse(activity.contains("onSaveInstanceState"));
        assertFalse(activity.contains("announceForAccessibility")); assertFalse(activity.contains("code.setContentDescription"));
        assertFalse(activity.contains("remaining.setContentDescription"));
        assertFalse(activity.contains("selected.setContentDescription"));
        assertFalse(activity.contains("controller.hideCode(); super.onStop"));
        assertTrue(read("RevealedTotp").contains("RevealedTotp[redacted]"));
    }
    @Test public void sensitiveClipboardConditionalMatchingAndNoUriCoercion() throws Exception {
        String source = read("PlatformCodeClipboard");
        assertTrue(source.contains("ClipDescription.EXTRA_IS_SENSITIVE")); assertTrue(source.contains("android.content.extra.IS_SENSITIVE"));
        assertTrue(source.contains("UUID") || read("TotpPresentation").contains("UUID.randomUUID()"));
        assertTrue(source.contains("if (!matches(")); assertTrue(source.contains("getItemCount() != 1"));
        assertTrue(source.contains("item.getUri() == null")); assertFalse(source.contains("coerceToText"));
        assertFalse(PlatformCodeClipboard.matches("own", "value", null, "value", true));
        assertFalse(PlatformCodeClipboard.matches("own", "value", "own", "value", false));
    }
    private static String read(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/org/totipo/android/" + name + ".java"));
    }
}
