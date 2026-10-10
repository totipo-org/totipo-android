package org.totipo.android;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Strict bounded device credential envelope. The fixed alias is never selected by record input. */
final class BiometricRecord {
    static final int VERSION = 1;
    static final String ALIAS = "org.totipo.biometric-password.v1";
    static final String CONTEXT = "Totipo device-local biometric vault password";
    static final int MAX_BYTES = 65536;
    final String vaultId;
    final byte[] iv, ciphertext;
    BiometricRecord(String vaultId, byte[] iv, byte[] ciphertext) throws IOException {
        if (!vaultId.matches("[0-9a-f]{64}") || iv.length != 12 || ciphertext.length < 16
                || ciphertext.length > MAX_BYTES - 128) throw new IOException("Invalid credential record");
        this.vaultId = vaultId; this.iv = iv.clone(); this.ciphertext = ciphertext.clone();
    }
    static byte[] aad(String vaultId) {
        return (CONTEXT + "\n" + VERSION + "\n" + ALIAS + "\n" + vaultId).getBytes(StandardCharsets.US_ASCII);
    }
    byte[] encode() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(VERSION); out.writeUTF(vaultId); out.writeUTF(ALIAS);
            out.write(iv); out.writeInt(ciphertext.length); out.write(ciphertext);
        }
        return bytes.toByteArray();
    }
    static BiometricRecord decode(byte[] bytes) throws IOException {
        if (bytes.length > MAX_BYTES) throw new IOException("Invalid credential record");
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != VERSION) throw new IOException("Invalid credential record");
            String id = in.readUTF();
            if (!ALIAS.equals(in.readUTF())) throw new IOException("Invalid credential record");
            byte[] iv = new byte[12]; in.readFully(iv);
            int size = in.readInt();
            if (size < 16 || size > MAX_BYTES - 128 || size != in.available()) throw new IOException("Invalid credential record");
            byte[] ciphertext = new byte[size]; in.readFully(ciphertext);
            return new BiometricRecord(id, iv, ciphertext);
        } catch (RuntimeException malformed) { throw new IOException("Invalid credential record"); }
    }
}
