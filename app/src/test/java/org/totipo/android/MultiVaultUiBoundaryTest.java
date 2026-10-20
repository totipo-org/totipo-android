package org.totipo.android;

import java.nio.file.*;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Static boundary tripwires; runtime UI evidence belongs to the disposable device harness. */
public final class MultiVaultUiBoundaryTest {
    private String read(String name) throws Exception { return Files.readString(Path.of("src/main/java/org/totipo/android/" + name + ".java")); }
    @Test public void selectorUsesApplicationMetadataAndExplicitAuthenticationWithoutInactiveControllers() throws Exception {
        String activity = read("MainActivity"), app = read("TotipoApplication");
        String selector = activity.substring(activity.indexOf("private void renderSelection()"), activity.indexOf("private void renameEntry"));
        for (String expected : List.of("localEntries()", "entrySummary(entry)", "lastSelected()", "selectFromUi(entry.id()", "Create Local Vault", "Join Existing Vault", "Manage Vaults")) assertTrue(expected, selector.contains(expected));
        for (String forbidden : List.of("new AndroidVaultController", "new BiometricUnlock", "passwordField", "PasswordBuffer", "vaultController()", "session", "ACTION_OPEN_DOCUMENT_TREE")) assertFalse(forbidden, selector.contains(forbidden));
        assertTrue(activity.contains("selection().observe(selectionObserver)"));
        assertFalse(activity.contains("offerBiometric"));
        assertTrue(activity.contains("button(\"Use biometrics\", () -> { biometric.unlock(); })"));
        assertTrue(app.contains("VaultUiModel.startupSelection"));
        assertFalse(app.contains("Multiple local vaults require selection support"));
        assertTrue(app.contains("registry.allocatePending(entry.id())"));
    }
    @Test public void changeHidesPresentationAndUsesCompleteRetirementWithNoBackRestoration() throws Exception {
        String activity = read("MainActivity"), app = read("TotipoApplication");
        String change = activity.substring(activity.indexOf("private void changeVault()"), activity.indexOf("@Override public void onUserInteraction"));
        for (String required : List.of("clearPasswords()", "clearSecret()", "clearCodeWidgets()", "dismissTokenChange()", "biometric.close()", "cancelBiometricEnrollment()", "pickerActivation = null", "application().changeVault()", "render(controller.snapshot())")) assertTrue(required, change.contains(required));
        assertTrue(app.contains("selection.retire()"));
        String back = activity.substring(activity.indexOf("private void navigateBack()"), activity.indexOf("private void submitToken()"));
        for (String forbidden : List.of("selectLocalEntry", "selectFromUi", "unlock(", "lastSelected", "new AndroidVaultController")) assertFalse(forbidden, back.contains(forbidden));
    }
    @Test public void detachedRecoveryUsesNormalAuthenticationAndForgetHasNoCanonicalDeletion() throws Exception {
        String transaction = read("VaultForget"), app = read("TotipoApplication"), registry = read("VaultRegistry");
        int begin = transaction.indexOf("registry.beginForget"), credentials = transaction.indexOf("cleanup.biometrics"), binding = transaction.indexOf("cleanup.syncBinding"), finish = transaction.indexOf("registry.finishForget");
        assertTrue(transaction.indexOf("selection.beginMaintenance") < begin);
        assertTrue(begin < credentials && credentials < binding && binding < finish);
        assertTrue(app.contains("if (!port.save(null)) throw"));
        assertTrue(app.contains("syncGrants.cleanup()"));
        for (String forbidden : List.of("Files.delete", "deleteRecursively", "releasePersistableUriPermission", "Totipo.open", "Totipo.create")) assertFalse(forbidden, transaction.contains(forbidden));
        assertTrue(registry.contains("if (version != 1 && version != VERSION)"));
        assertTrue(registry.contains("State.FORGETTING"));
    }
}
