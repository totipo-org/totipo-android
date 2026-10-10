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
        assertTrue(crypto.contains("getNoBackupFilesDir()")); assertTrue(crypto.contains("AtomicFile"));
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
}
