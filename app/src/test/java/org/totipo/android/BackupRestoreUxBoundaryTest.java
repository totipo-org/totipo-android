package org.totipo.android;

import java.nio.file.*;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Activity source wiring checks complement queued orchestration and real archive/store
 * tests. These do not execute widgets, TalkBack, picker delivery or rotation. */
public final class BackupRestoreUxBoundaryTest {
    private String source(String file) throws Exception { return Files.readString(Path.of("src/main/java/org/totipo/android/" + file + ".java")); }
    @Test public void backupActionHidesCodeAndStartsPreparationWithoutDialogAndPickerWaitsForReady() throws Exception {
        String ui = source("MainActivity");
        String menu = ui.substring(ui.indexOf("menu.getMenu().add(\"Back up vault\")"), ui.indexOf("if (controller.backup()"));
        assertTrue(menu.contains("controller.canBackUpVault()"));
        assertTrue(menu.indexOf("controller.hideCode()") < menu.indexOf("controller.beginBackup()"));
        assertFalse(ui.contains("explainBackup")); assertFalse(ui.contains("Choose destination"));
        String picker = ui.substring(ui.indexOf("private void maybeLaunchBackupPicker()"), ui.indexOf("@Override protected void onActivityResult"));
        assertTrue(picker.indexOf("BackupExport.Phase.READY") < picker.indexOf("startActivityForResult"));
        assertFalse(picker.contains("AlertDialog")); assertFalse(source("BackupExport").contains("AlertDialog"));
    }
    @Test public void passwordAndOptionalNameHavePersistentAssociatedLabelsAndRetryHasNoName() throws Exception {
        String ui = source("MainActivity");
        String form = ui.substring(ui.indexOf("case PASSWORD ->"), ui.indexOf("case CONFIRM ->"));
        for (String required : List.of("passwordField(\"Original vault password\")", "if (!flow.recovery())", "label(\"Vault name (optional)\")", "nameLabel.setLabelFor(name.getId())", "name.setSaveEnabled(false)", "name.setSaveFromParentEnabled(false)", "button(\"Restore\"", "password.getText().clear()", "hideSoftInputFromWindow", "password.requestFocus()")) assertTrue(required, form.contains(required));
        String field = ui.substring(ui.indexOf("private EditText passwordField"), ui.indexOf("private Button button("));
        for (String required : List.of("label.setLabelFor(field.getId())", "setFreezesText(false)", "IMPORTANT_FOR_AUTOFILL_NO", "setSaveEnabled(false)", "setSaveFromParentEnabled(false)")) assertTrue(required, field.contains(required));
        for (String forbidden : List.of("Authenticate backup", "Confirm restore", "Open restored vault", "Restore limited history")) assertFalse(forbidden, ui.contains(forbidden));
    }
    @Test public void conditionalReviewAndQuietStatusArePresentationOnlyAndSuccessFollowsNormalOpen() throws Exception {
        String ui = source("MainActivity"), app = source("TotipoApplication");
        String render = ui.substring(ui.indexOf("private void renderRestore"), ui.indexOf("private void pickRestore"));
        assertTrue(render.contains("flow.needsHistoryReview() ? \"Some history is missing\""));
        assertTrue(render.contains("button(\"Cancel\"")); assertTrue(render.contains("button(\"Restore anyway\""));
        assertFalse(render.contains("case DONE")); assertFalse(render.contains("selectFromUi"));
        assertTrue(render.contains("message.equals(restoreAnnouncement)"));
        assertTrue(render.contains("ACCESSIBILITY_LIVE_REGION_NONE"));
        assertTrue(ui.contains("status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE)"));
        assertTrue(ui.contains("flow.stage() == renderedRestoreStage"));
        for (String action : List.of("Recover retained vault", "Resume restore from backup", "Finish restoring", "Open existing vault")) assertTrue(action, ui.contains(action));
        String success = app.substring(app.indexOf("private void restoredSelection"), app.indexOf("private int started"));
        assertTrue(success.contains("state.state() == AndroidVaultController.State.OPEN"));
        assertTrue(success.contains("restoreOpeningNotice = \"Vault restored.\""));
        assertFalse(success.contains("unlock(")); assertFalse(success.contains("char[]"));
        assertTrue(app.contains("restoreJourney.background()"));
        assertTrue(app.contains("return foregroundAuthorized && selection.snapshot() == null && selection.owner() == null"));
        assertTrue(app.contains("foregroundAuthorized = false;"));
        assertTrue(app.contains("foregroundAuthorized = true;"));
        assertTrue(app.contains("--started == 0 && !activity.isChangingConfigurations()"));
    }
    @Test public void pickerResultIsConsumedOnceAndOnlyResumedOriginMayRead() throws Exception {
        String ui = source("MainActivity");
        String result = ui.substring(ui.indexOf("if (restorePicker != null && request == restoreRequest)"), ui.indexOf("if (backupPicker != null && request == backupRequest)"));
        assertTrue(result.indexOf("restoreRequest = 0") < result.indexOf("restoreSource = data.getData()"));
        assertTrue(result.contains("restorePicker != application().restoreFlow()"));
        assertTrue(ui.contains("restoreSource = null;"));
        assertTrue(ui.contains("application().readRestore(restorePicker, uri)"));
        assertTrue(ui.contains("getLastNonConfigurationInstance()"));
        assertFalse(ui.contains("onSaveInstanceState("));
    }
}
