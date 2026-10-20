package org.totipo.android;

import java.io.*;
import java.util.Arrays;

/** Canonical bounded v2 envelope. Only the caller's validated local entry selects a key. */
final class BiometricRecord {
    static final int VERSION = 2;
    // Detection only. Never opened, adopted, or deleted by multi-vault authentication.
    static final String LEGACY_ALIAS = "org.totipo.biometric-password.v1";
    static final String CONTEXT = "Totipo device-local biometric vault password";
    static final int MAX_BYTES = 2048;
    final String localId, vaultId;
    final byte[] iv, ciphertext;
    static String alias(String localId) {
        VaultRegistry.validateId(localId);
        return "org.totipo.biometric-password.v2." + localId;
    }
    BiometricRecord(String localId, String vaultId, byte[] iv, byte[] ciphertext) throws IOException {
        try { VaultRegistry.validateId(localId); }
        catch (IllegalArgumentException bad) { throw new IOException("Invalid credential record"); }
        if (vaultId == null || !vaultId.matches("[0-9a-f]{64}") || iv.length != 12
                || ciphertext.length < 16 || ciphertext.length > 1040)
            throw new IOException("Invalid credential record");
        this.localId = localId; this.vaultId = vaultId; this.iv = iv.clone(); this.ciphertext = ciphertext.clone();
    }
    static byte[] aad(String localId, String vaultId) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {
                out.writeUTF(CONTEXT); out.writeInt(VERSION);
                out.writeUTF(alias(localId)); out.writeUTF(localId); out.writeUTF(vaultId);
            }
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new AssertionError(impossible); }
    }
    byte[] encode() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(VERSION); out.writeUTF(localId); out.writeUTF(vaultId); out.writeUTF(alias(localId));
            out.write(iv); out.writeInt(ciphertext.length); out.write(ciphertext);
        }
        return bytes.toByteArray();
    }
    static BiometricRecord decode(byte[] bytes, String expectedLocalId) throws IOException {
        VaultRegistry.validateId(expectedLocalId);
        if (bytes.length > MAX_BYTES) throw new IOException("Invalid credential record");
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != VERSION) throw new IOException("Unsupported credential version");
            String local = in.readUTF(), vault = in.readUTF(), key = in.readUTF();
            if (!expectedLocalId.equals(local) || !alias(expectedLocalId).equals(key)) throw new IOException("Credential ownership mismatch");
            byte[] iv = new byte[12]; in.readFully(iv);
            int size = in.readInt();
            if (size < 16 || size > 1040 || size != in.available()) throw new IOException("Invalid credential record");
            byte[] ciphertext = new byte[size]; in.readFully(ciphertext);
            var record = new BiometricRecord(local, vault, iv, ciphertext);
            // Reject noncanonical modified UTF encodings as well as extra/truncated fields.
            if (!Arrays.equals(bytes, record.encode())) throw new IOException("Noncanonical credential record");
            return record;
        } catch (RuntimeException malformed) { throw new IOException("Invalid credential record"); }
    }
}
