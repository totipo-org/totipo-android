package org.totipo.android;

import android.content.Context;
import android.hardware.biometrics.BiometricManager;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Device-local authenticated reusable password representation, protected by an auth-per-use key. */
@android.annotation.TargetApi(30)
final class AndroidBiometricCredentials implements BiometricCredentials {
    private final Context context;
    private final AtomicFile record;
    AndroidBiometricCredentials(Context context) {
        this.context = context;
        record = new AtomicFile(new File(context.getNoBackupFilesDir(), "biometric-password-v1"));
    }
    public boolean available() {
        if (Build.VERSION.SDK_INT < 30) return false;
        var manager = context.getSystemService(BiometricManager.class);
        return manager != null && manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                == BiometricManager.BIOMETRIC_SUCCESS;
    }
    public boolean configured() { return record.getBaseFile().exists(); }
    private KeyStore store() throws Exception {
        KeyStore keys = KeyStore.getInstance("AndroidKeyStore"); keys.load(null); return keys;
    }
    public Operation enrollment(String vaultId) throws Exception {
        if (!available()) throw new IllegalStateException("Biometrics unavailable");
        delete();
        var generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(BiometricRecord.ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                .setInvalidatedByBiometricEnrollment(true).build());
        SecretKey key = generator.generateKey(); // Non-exportable; never request getEncoded().
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return new Transaction(cipher, vaultId, null);
    }
    public Operation unlock() throws Exception {
        byte[] bytes;
        try (var input = record.openRead()) {
            byte[] bounded = new byte[BiometricRecord.MAX_BYTES + 1];
            int size = 0, count;
            while (size < bounded.length && (count = input.read(bounded, size, bounded.length - size)) != -1) size += count;
            bytes = Arrays.copyOf(bounded, size);
        }
        BiometricRecord saved = BiometricRecord.decode(bytes);
        var key = store().getKey(BiometricRecord.ALIAS, null);
        if (!(key instanceof SecretKey)) throw new IllegalStateException("Credential unavailable");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, saved.iv));
        return new Transaction(cipher, saved.vaultId, saved);
    }
    public void delete() throws Exception {
        // Delete the key first: even if filesystem deletion fails, ciphertext cannot reopen.
        try { store().deleteEntry(BiometricRecord.ALIAS); }
        finally { record.delete(); }
        if (configured()) throw new java.io.IOException("Credential deletion failed");
    }
    private final class Transaction implements Operation {
        private final Cipher cipher;
        private final String id;
        private final BiometricRecord saved;
        private boolean used;
        Transaction(Cipher cipher, String id, BiometricRecord saved) { this.cipher = cipher; this.id = id; this.saved = saved; }
        public Cipher cipher() { return cipher; }
        public String vaultId() { return id; }
        private void once() { if (used) throw new IllegalStateException("Credential operation retired"); used = true; }
        public void encrypt(PasswordBuffer password) throws Exception {
            once();
            byte[] plaintext = null;
            try {
                // Keystore updateAAD consumes authorization: invoke only after CryptoObject success.
                cipher.updateAAD(BiometricRecord.aad(id));
                plaintext = password.encode();
                byte[] ciphertext = cipher.doFinal(plaintext);
                byte[] encoded = new BiometricRecord(id, cipher.getIV(), ciphertext).encode();
                FileOutputStream output = null;
                try { output = record.startWrite(); output.write(encoded); record.finishWrite(output); }
                catch (Exception failed) { if (output != null) record.failWrite(output); throw failed; }
            } finally { if (plaintext != null) Arrays.fill(plaintext, (byte) 0); }
        }
        public char[] decrypt() throws Exception {
            once();
            byte[] plaintext = null;
            try {
                cipher.updateAAD(BiometricRecord.aad(id));
                plaintext = cipher.doFinal(saved.ciphertext);
                return PasswordBuffer.decode(plaintext);
            }
            finally { if (plaintext != null) Arrays.fill(plaintext, (byte) 0); }
        }
    }
}
