package org.totipo.android;

import android.content.Context;
import android.hardware.biometrics.BiometricManager;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Entry-bound auth-per-use crypto. Platform policy is unchanged; no legacy credential access. */
@android.annotation.TargetApi(30)
final class AndroidBiometricCredentials implements BiometricCredentials {
    interface Keys {
        SecretKey find(String alias) throws Exception;
        SecretKey create(String alias) throws Exception;
        void delete(String alias) throws Exception;
    }
    private final String localId, alias;
    private final BiometricCredentialStorage storage;
    private final Keys keys;
    private final BooleanSupplier available;
    private long generation;
    AndroidBiometricCredentials(Context context, VaultRegistry registry, String localId) throws java.io.IOException {
        this(localId, new BiometricCredentialStorage(registry, localId), new PlatformKeys(), () -> {
            var manager = context.getSystemService(BiometricManager.class);
            return manager != null && manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    == BiometricManager.BIOMETRIC_SUCCESS;
        });
        // Registry is rooted in canonical getNoBackupFilesDir(), never a provider folder.
    }
    AndroidBiometricCredentials(String localId, BiometricCredentialStorage storage, Keys keys, BooleanSupplier available) {
        this.localId = localId; alias = BiometricRecord.alias(localId);
        if (!localId.equals(storage.localId)) throw new IllegalArgumentException("Credential storage ownership mismatch");
        this.storage = storage; this.keys = keys; this.available = available;
    }
    public String localId() { return localId; }
    public boolean available() { return available.getAsBoolean(); }
    public synchronized boolean configured() {
        try { BiometricRecord.decode(storage.read(), localId); return keys.find(alias) != null; }
        catch (Exception unavailable) { return false; }
    }
    public synchronized Operation enrollment(String vaultId) throws Exception {
        if (!available() || storage.recovery()) throw new IllegalStateException("Biometric enrollment unavailable; disable before recovery");
        // Reuse this entry's key with a fresh auth-per-use encryption operation. Cancel
        // leaves the prior ciphertext/key intact. Missing keys never revive old records.
        SecretKey key = keys.find(alias);
        if (key == null) {
            if (storage.present()) throw new IllegalStateException("Credential key missing; disable before reenrollment");
            key = keys.create(alias);
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key);
        return new Transaction(cipher, vaultId, null, ++generation);
    }
    public synchronized Operation unlock() throws Exception {
        BiometricRecord saved = BiometricRecord.decode(storage.read(), localId);
        SecretKey key = keys.find(alias);
        if (key == null) throw new IllegalStateException("Credential key missing");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, saved.iv));
        return new Transaction(cipher, saved.vaultId, saved, ++generation);
    }
    public synchronized void delete() throws Exception {
        generation++;
        storage.beginDelete(); // Durable fail-closed marker precedes either cleanup action.
        Exception failure = null;
        try { storage.deleteRecord(); } catch (Exception failed) { failure = failed; }
        try {
            keys.delete(alias);
            if (keys.find(alias) != null) throw new java.io.IOException("Credential key deletion incomplete");
        } catch (Exception failed) { if (failure == null) failure = failed; }
        if (failure != null) throw failure; // marker retained for explicit bounded retry
        storage.finishDelete();
    }
    private final class Transaction implements Operation {
        private final Cipher cipher;
        private final String id;
        private final BiometricRecord saved;
        private final long request;
        private boolean used;
        Transaction(Cipher cipher, String id, BiometricRecord saved, long request) {
            this.cipher = cipher; this.id = id; this.saved = saved; this.request = request;
        }
        public Cipher cipher() { return cipher; }
        public String vaultId() { return id; }
        private void once() {
            if (used || request != generation) throw new IllegalStateException("Credential operation retired"); used = true;
        }
        public void encrypt(PasswordBuffer password) throws Exception {
            synchronized (AndroidBiometricCredentials.this) {
                once(); byte[] plaintext = null;
                try {
                    // Keystore updateAAD consumes authorization: only after CryptoObject success.
                    cipher.updateAAD(BiometricRecord.aad(localId, id));
                    plaintext = password.encode();
                    storage.write(new BiometricRecord(localId, id, cipher.getIV(), cipher.doFinal(plaintext)).encode());
                } finally { if (plaintext != null) Arrays.fill(plaintext, (byte) 0); }
            }
        }
        public char[] decrypt() throws Exception {
            synchronized (AndroidBiometricCredentials.this) {
                once(); byte[] plaintext = null;
                try {
                    cipher.updateAAD(BiometricRecord.aad(localId, id));
                    plaintext = cipher.doFinal(saved.ciphertext);
                    return PasswordBuffer.decode(plaintext);
                } finally { if (plaintext != null) Arrays.fill(plaintext, (byte) 0); }
            }
        }
    }
    private static final class PlatformKeys implements Keys {
        private KeyStore store() throws Exception { var keys = KeyStore.getInstance("AndroidKeyStore"); keys.load(null); return keys; }
        public SecretKey find(String alias) throws Exception {
            var key = store().getKey(alias, null); return key instanceof SecretKey secret ? secret : null;
        }
        public SecretKey create(String alias) throws Exception {
            if (store().containsAlias(alias)) throw new IllegalStateException("Credential key already exists");
            var generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true).setUserAuthenticationRequired(true)
                    .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                    .setInvalidatedByBiometricEnrollment(true).build());
            return generator.generateKey(); // Non-exportable; key bytes are never requested.
        }
        public void delete(String alias) throws Exception { store().deleteEntry(alias); }
    }
}
