package org.totipo.android;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.Test;
import org.totipo.*;
import org.totipo.android.reconcile.ForegroundVaultCoordinator.ObservedToken;
import static org.junit.Assert.*;

public final class DailyUseUxTest {
    private VaultRegistry.Entry entry(String id, String label) {
        return new VaultRegistry.Entry(id, label, VaultRegistry.State.PENDING, false);
    }
    @Test public void localIdFallbackIsStableCollisionAwareAndNeverAppendedToNames() {
        var a = entry("8f2a6c00" + "0".repeat(24), null);
        var b = entry("8f2a6c01" + "0".repeat(24), "Work");
        var c = entry("9".repeat(32), null);
        assertEquals("8F2A6C · Unnamed", VaultUiModel.name(List.of(a), a));
        assertEquals("8F2A6C00 · Unnamed", VaultUiModel.name(List.of(a, b, c), a));
        assertEquals(VaultUiModel.name(List.of(a, b, c), a), VaultUiModel.name(List.of(c, b, a), a));
        assertEquals("Work", VaultUiModel.name(List.of(a, b), b));
        var renamed = entry(a.id(), "Work");
        assertEquals("Work", VaultUiModel.name(List.of(renamed, b), renamed));
        assertEquals("999999 · Unnamed", VaultUiModel.name(List.of(a, b, c), c));
        assertNull(a.label());
    }
    @Test public void optionalNamesUseRegistryUnicodeAndUtf16Rules() {
        for (String empty : List.of("", " \t\n", "\u2003\u00a0")) assertNull(VaultUiModel.optionalLabel(empty));
        assertEquals(" Work ", VaultUiModel.optionalLabel(" Work "));
        entry("a".repeat(32), "😀".repeat(64));
        for (String invalid : List.of("😀".repeat(65), "x".repeat(129), "bad\nname", "\ud800"))
            assertThrows(IllegalArgumentException.class, () -> entry("a".repeat(32), VaultUiModel.optionalLabel(invalid)));
    }
    @org.junit.Rule public org.junit.rules.TemporaryFolder temporary = new org.junit.rules.TemporaryFolder();
    @Test public void nameWriteFailureAndUncertainCreationRetainExactReservedAssociation() throws Exception {
        Path base = temporary.getRoot().toPath(); var registry = new VaultRegistry(base);
        var entry = registry.reserve(null); Path root = registry.root(entry.id());
        var uncertainName = new VaultRegistry(base, (destination, next, bytes) -> {
            VaultRegistry.atomicWrite(destination, next, bytes); throw new java.io.IOException("lost acknowledgement");
        });
        assertThrows(java.io.IOException.class, () -> uncertainName.rename(entry.id(), "Personal"));
        var restarted = new VaultRegistry(base);
        assertEquals(1, restarted.entries().size()); assertEquals(entry.id(), restarted.entries().get(0).id());
        assertEquals("Personal", restarted.entry(entry.id()).label()); assertEquals(root, restarted.root(entry.id()));
        restarted.beginCreation(entry.id());
        // Interrupted Create/Join retains label and attempted association; never a new reservation.
        var recovery = new VaultRegistry(base);
        assertTrue(recovery.entry(entry.id()).creationAttempted());
        assertEquals("Personal", recovery.entry(entry.id()).label());
        assertThrows(VaultRegistry.RecoveryRequired.class, () -> recovery.beginCreation(entry.id()));
        assertEquals(1, recovery.entries().size());
    }
    @Test public void folderSuggestionRemainsEditableAndManualClearIsAuthoritative() {
        var draft = new SetupVaultName(); assertEquals("", draft.value());
        draft.suggest(null); assertEquals("", draft.value());
        draft.suggest("Work-TOTP"); assertEquals("Work-TOTP", draft.value());
        draft.edit("Personal"); draft.suggest("Other folder"); assertEquals("Personal", draft.value());
        draft.edit(""); draft.suggest("Work-TOTP"); assertEquals("", draft.value());
        var invalid = new SetupVaultName(); invalid.suggest("bad\nname"); invalid.suggest("x".repeat(129));
        assertEquals("", invalid.value());
    }
    private ObservedToken token(char id, String issuer, String account) {
        return new ObservedToken(new TokenId(String.valueOf(id).repeat(64)), List.of(
                new TokenDescriptor(TokenStatus.ACTIVE, issuer, account, TotpAlgorithm.SHA1, 6, Duration.ofSeconds(30))), List.of(), List.of(), false);
    }
    @Test public void issuerAccountMissingDuplicateCaseAndUnicodeOrderIsLocaleStable() {
        var a = token('1', "alpha", "Z"); var b = token('2', "ALPHA", "a");
        var c = token('3', "", "Beta"); var d = token('4', "alpha", "A");
        var e = token('5', "", ""); var f = token('6', "École", "é");
        var g = token('7', "İ", "I");
        var input = List.of(g, f, d, c, b, a, e);
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals(List.of(e, b, d, a, c, g, f), TokenDisplayOrder.sorted(input));
        } finally { Locale.setDefault(original); }
        assertEquals(List.of(g, f, d, c, b, a, e), input);
        var filtered = input.stream().filter(t -> TokenListAdapter.matchesSearch(t, "alpha")).sorted(TokenDisplayOrder.ORDER).toList();
        assertEquals(List.of(b, d, a), filtered);
    }
    @Test public void conflictOrderUsesNeutralIdentityAndNeverChoosesAnAlternative() {
        var a = token('1', "Z", "z"); var b = token('2', "A", "a");
        var conflict = new ObservedToken(new TokenId("3".repeat(64)), List.of(a.alternatives().get(0), b.alternatives().get(0)), List.of(), List.of(), true);
        var reversed = new ObservedToken(conflict.id(), List.of(b.alternatives().get(0), a.alternatives().get(0)), List.of(), List.of(), true);
        assertEquals(0, TokenDisplayOrder.ORDER.compare(conflict, reversed));
        assertEquals(List.of(conflict, b, a), TokenDisplayOrder.sorted(List.of(a, conflict, b)));
        assertTrue(conflict.conflict()); assertEquals(2, conflict.alternatives().size());
        assertTrue(TokenListAdapter.rowText(conflict).startsWith("Token conflict"));
    }
    @Test public void shellMetadataAndNamingDoNotChangeAuthenticationOrRegistryOwnership() throws Exception {
        String ui = Files.readString(Path.of("src/main/java/org/totipo/android/MainActivity.java"));
        String app = Files.readString(Path.of("src/main/java/org/totipo/android/TotipoApplication.java"));
        String controller = Files.readString(Path.of("src/main/java/org/totipo/android/AndroidVaultController.java"));
        assertFalse(ui.contains("title.setText(\"Totipo\")")); assertFalse(ui.contains("vaultManagement"));
        assertTrue(ui.contains("title.setText(heading(next))"));
        assertTrue(ui.contains("getPackageInfo(getPackageName(), 0)")); assertTrue(ui.contains("info.getLongVersionCode()"));
        assertFalse(controller.contains("0.0.0-dev")); assertFalse(controller.contains("getPackageManager"));
        assertTrue(ui.indexOf("!application().saveSelectedName") < ui.indexOf("char[] credential ="));
        assertTrue(app.contains("COLUMN_DISPLAY_NAME")); assertTrue(app.contains("MIME_TYPE_DIR"));
        String suggestion = app.substring(app.indexOf("void suggestJoinName"), app.indexOf("private final java.util.concurrent.ExecutorService"));
        assertFalse(suggestion.contains("getLastPathSegment")); assertFalse(suggestion.contains("split("));
        assertTrue(suggestion.contains("vaultController() != controller || !controller.selectedAuthority() || setupName != draft"));
        String ordinary = ui.substring(ui.indexOf("} else if (next.equals(\"open\"))"), ui.indexOf("private static void reserveSyncControlWidth"));
        assertFalse(ordinary.contains("Rename"));
    }
}
