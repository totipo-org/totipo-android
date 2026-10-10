package org.totipo.android;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.Test;
import org.totipo.*;
import org.totipo.android.reconcile.ForegroundVaultCoordinator.ObservedToken;
import static org.junit.Assert.*;

/** Framework source boundary checks complement the required physical UI smoke; no UI emulator is claimed. */
public final class DailyDriverUxTest {
    private String read(String file) throws Exception {
        return Files.readString(Path.of("src/main/java/org/totipo/android/" + file + ".java"));
    }
    @Test public void dailySurfaceHasSyncSearchListAddAndOverflowWithoutTransportControls() throws Exception {
        String ui = read("MainActivity");
        for (String forbidden : List.of("Import changes", "Publish local changes", "VaultId", "objects-v1", "State.READY", "controller.refresh()", "controller.importProviderChanges()", "controller.publishLocalChanges()"))
            assertFalse(forbidden, ui.contains(forbidden));
        for (String required : List.of("\"Sync\"", "Search tokens…", "new ListView(this)", "Add token", "More options", "showOverflow(more)"))
            assertTrue(required, ui.contains(required));
        String daily = ui.substring(ui.indexOf("} else if (next.equals(\"open\"))"), ui.indexOf("private String mainStatus"));
        for (String forbidden : List.of("Initialize sync folder", "Join existing vault", "syncView", "diagnostics()", "initialTreeUri"))
            assertFalse(forbidden, daily.contains(forbidden));
    }
    @Test public void setupAndReadOnlyDiagnosticsAreNamedAndReachableFromOverflow() throws Exception {
        String ui = read("MainActivity");
        String overflow = ui.substring(ui.indexOf("private void showOverflow"), ui.indexOf("private void buildFolderManagement"));
        assertTrue(overflow.contains("Manage sync folder")); assertTrue(overflow.contains("Diagnostics"));
        assertTrue(overflow.contains("setMessage(controller.diagnostics())"));
        for (String forbidden : List.of("controller.sync()", "initializeSyncFolder", "prepareJoin", "unlock(", "create("))
            assertFalse(forbidden, overflow.contains(forbidden));
        assertTrue(ui.contains("next.equals(\"manage\") || next.equals(\"create\") || next.equals(\"join\")"));
        String setup = ui.substring(ui.indexOf("private void buildFolderManagement"), ui.indexOf("private void dismissTokenChange"));
        assertTrue(setup.contains("Join existing vault")); assertTrue(setup.contains("Initialize sync folder"));
        String controller = read("AndroidVaultController");
        String diagnostics = controller.substring(controller.indexOf("public synchronized String diagnostics()"), controller.indexOf("private String productSyncError"));
        for (String forbidden : List.of("vault.", "providerIo", "requestRefresh", "credential", "secret", "password", "getMessage()", "Throwable"))
            assertFalse(forbidden, diagnostics.contains(forbidden));
        assertTrue(diagnostics.contains("Vault ID:")); assertTrue(diagnostics.contains("Java/core: 0.2.0"));
    }
    @Test public void syncStatusAccessibilityIsNamedQuietAndDoesNotAnnounceCodes() throws Exception {
        String ui = read("MainActivity");
        assertTrue(ui.contains("syncAction.setContentDescription(\"Sync\")"));
        assertTrue(ui.contains("ACCESSIBILITY_LIVE_REGION_POLITE"));
        assertTrue(ui.contains("if (!TextUtils.equals(status.getText(), message))"));
        assertTrue(ui.contains("message.isEmpty() ? View.GONE : View.VISIBLE"));
        assertTrue(read("TokenListAdapter").contains("row.countdown.setText"));
        assertFalse(ui.contains("code.setAccessibilityLiveRegion"));
        assertTrue(read("TokenListAdapter").contains("text.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_NONE)"));
    }
    @Test public void searchMatchesIssuerAccountAndConflictAlternativesWithLocaleIndependentCase() {
        var first = new TokenDescriptor(TokenStatus.ACTIVE, "GitHub", "alice@example.test", TotpAlgorithm.SHA1, 6, Duration.ofSeconds(30));
        var second = new TokenDescriptor(TokenStatus.ACTIVE, "AWS", "production", TotpAlgorithm.SHA1, 6, Duration.ofSeconds(30));
        var token = new ObservedToken(new TokenId("a".repeat(64)), List.of(first, second), List.of(), List.of(), true);
        assertTrue(TokenListAdapter.matchesSearch(token, " GITHUB "));
        assertTrue(TokenListAdapter.matchesSearch(token, "ALICE@"));
        assertTrue(TokenListAdapter.matchesSearch(token, "production"));
        assertTrue(TokenListAdapter.matchesSearch(token, ""));
        assertFalse(TokenListAdapter.matchesSearch(token, "not present"));
    }
    @Test public void appForegroundUsesStartedActivityCountAndPreservesRotationLockPolicy() throws Exception {
        String app = read("TotipoApplication");
        assertTrue(app.contains("if (++started == 1)"));
        assertTrue(app.contains("--started == 0 && !activity.isChangingConfigurations()"));
        assertTrue(app.contains("foregroundChanged(true)")); assertTrue(app.contains("foregroundChanged(false)"));
        assertFalse(app.contains("vaultController.lock()"));
        for (String forbidden : List.of("WorkManager", "JobScheduler", "startForegroundService", "AlarmManager")) assertFalse(app.contains(forbidden));
    }
    @Test public void realBusyMessagesRemainVisibleAndGenerationHasNoGlobalStatus() throws Exception {
        var saving = new AndroidVaultController.Snapshot(AndroidVaultController.State.BUSY,
                AndroidVaultController.Error.NONE, "Saving local change…", null);
        for (String sync : List.of("", "Changes not synced"))
            assertEquals("Saving local change…" + (sync.isEmpty() ? "" : "\n" + sync), MainActivity.mainStatus(saving, sync, null));
        assertFalse(read("AndroidVaultController").contains("Generating code…"));
    }
}
