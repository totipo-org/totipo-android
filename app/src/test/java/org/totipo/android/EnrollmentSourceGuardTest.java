package org.totipo.android;

import java.nio.file.*;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public final class EnrollmentSourceGuardTest {
    @Test public void productionEnrollmentUsesPublicJavaAndNeverPrintsSecrets() throws Exception {
        for (String name : List.of("MainActivity", "AddTokenRequest", "AddTokenOutcome", "Base32", "AndroidVaultController", "reconcile/ForegroundVaultCoordinator")) {
            String source = Files.readString(Path.of("src/main/java/org/totipo/android/" + name + ".java"));
            for (String forbidden : List.of("Log.", "System.out", "System.err", "printStackTrace", "getMessage()", "org.totipo.format", "javax.crypto", "Mac.getInstance", "MessageDigest", "GEZDGNBV", "12345678901234567890", "putCharArray", "putString", "SharedPreferences"))
                assertFalse(name + ": " + forbidden, source.contains(forbidden));
        }
        String coordinator = Files.readString(Path.of("src/main/java/org/totipo/android/reconcile/ForegroundVaultCoordinator.java"));
        assertTrue(coordinator.contains("session.state().createToken()"));
        assertTrue(coordinator.contains("uncertain.retry().close()"));
    }
    @Test public void addCancelAndRecreationProtectWidgetInput() throws Exception {
        String source = Files.readString(Path.of("src/main/java/org/totipo/android/MainActivity.java"));
        assertTrue(source.contains("button(\"Add token\""));
        assertTrue(source.contains("controller.hideCode(); adding = true"));
        assertTrue(source.contains("private void cancelAdd() { clearSecret();"));
        String stop = source.substring(source.indexOf("@Override protected void onStop()"), source.indexOf("// No session closure"));
        assertTrue(stop.contains("clearSecret();")); assertTrue(stop.contains("clearPasswords();"));
        assertTrue(stop.contains("biometric.close()")); assertTrue(stop.contains("cancelBiometricEnrollment()"));
        assertTrue(source.contains("setSaveFromParentEnabled(false)"));
        assertTrue(source.contains("IMPORTANT_FOR_AUTOFILL_NO"));
        assertFalse(source.contains("onSaveInstanceState"));
        assertFalse(source.contains("savedInstanceState.get"));
        assertTrue(source.contains("secret.getText().getChars"));
    }
}
