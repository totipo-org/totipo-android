package org.totipo.android;

import java.nio.file.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class BiometricSourceBoundaryTest {
    @Test public void frameworkStrongOnlyAuthPerUseAndNoBackupBoundary() throws Exception {
        String crypto = Files.readString(Path.of("src/main/java/org/totipo/android/AndroidBiometricCredentials.java"));
        assertTrue(crypto.contains("setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)"));
        assertTrue(crypto.contains("setUserAuthenticationRequired(true)"));
        assertTrue(crypto.contains("setInvalidatedByBiometricEnrollment(true)"));
        assertTrue(crypto.contains("getNoBackupFilesDir()")); String storage = Files.readString(Path.of("src/main/java/org/totipo/android/BiometricCredentialStorage.java"));
        assertTrue(storage.contains("ATOMIC_MOVE")); assertTrue(storage.contains("channel.force(true)"));
        assertTrue(storage.contains("NOFOLLOW_LINKS")); assertTrue(storage.contains("biometric-v2.pending"));
        assertTrue(crypto.contains("AES/GCM/NoPadding"));
        // Auth-per-use Keystore updateAAD is an authorized operation, just like doFinal.
        assertFalse(crypto.substring(0, crypto.indexOf("private final class Transaction")).contains("updateAAD"));
        assertEquals(2, crypto.split("cipher.updateAAD", -1).length - 1);
        for (String file : new String[]{"AndroidBiometricCredentials", "FrameworkBiometricPrompt", "BiometricUnlock", "PasswordBuffer"}) {
            String source = Files.readString(Path.of("src/main/java/org/totipo/android/" + file + ".java"));
            for (String forbidden : new String[]{"AUTH_DEVICE_CREDENTIAL", "Authenticators.DEVICE_CREDENTIAL", "BIOMETRIC_WEAK", "androidx.biometric", ".getEncoded()", "new String(", "Log.", "SharedPreferences"})
                assertFalse(file + ": " + forbidden, source.contains(forbidden));
        }
        assertEquals(900_000, InactivityLock.TIMEOUT_MILLIS);
    }
    @Test public void productionFactoryAndMutationBoundariesRemainEntryAndActivationBound() throws Exception {
        String activity = Files.readString(Path.of("src/main/java/org/totipo/android/MainActivity.java"));
        String app = Files.readString(Path.of("src/main/java/org/totipo/android/TotipoApplication.java"));
        String callback = Files.readString(Path.of("src/main/java/org/totipo/android/BiometricUnlock.java"));
        String controller = Files.readString(Path.of("src/main/java/org/totipo/android/AndroidVaultController.java"));
        assertTrue(activity.contains("biometricCredentials(controller)"));
        assertTrue(app.contains("new AndroidBiometricCredentials(this, registry, selected.localId())"));
        assertTrue(app.contains("selection.requireRetiredEntry(id)"));
        assertFalse(callback.contains("vaultController()"));
        assertTrue(callback.contains("controller.biometricCurrent(originRequest)"));
        assertTrue(callback.contains("controller.completeBiometricEnrollment(originEnrollment"));
        assertTrue(controller.contains("!enrollmentVaultIdentity.hex().equals(enrollment.vaultId())"));
        assertTrue(controller.contains("enrollmentOwner != vault"));
        assertTrue(controller.contains("enrollmentVaultIdentity = vault.sessionVaultId()"));
        int validation = controller.indexOf("!expectedVaultId.equals(id)");
        assertTrue(validation > 0);
        assertTrue(validation < controller.indexOf("opened(biometricRequest)"));
        assertTrue(controller.contains("biometricRequest != null && !biometricCurrent(biometricRequest)"));
        assertFalse(activity.contains("PendingVaultBiometricCredentials"));
        String crypto = Files.readString(Path.of("src/main/java/org/totipo/android/AndroidBiometricCredentials.java"));
        assertFalse(crypto.contains("LEGACY_ALIAS")); assertFalse(crypto.contains("biometric-password-v1"));
    }

}
