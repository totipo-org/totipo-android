package org.totipo.android;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

public class BiometricRecordTest {
    final String id = "a".repeat(64);
    @Test public void authenticatedCiphertextRoundTripsWithoutPlaintextAndUsesFreshNonce() throws Exception {
        var key = KeyGenerator.getInstance("AES").generateKey();
        byte[][] ivs = new byte[2][];
        for (int i = 0; i < 2; i++) {
            char[] chars = {'v','a','u','l','t','\u00e9','\ud83d','\udd11'};
            try (var password = new PasswordBuffer(chars)) {
                byte[] plain = password.encode();
                try {
                    Cipher enc = Cipher.getInstance("AES/GCM/NoPadding"); enc.init(Cipher.ENCRYPT_MODE, key);
                    enc.updateAAD(BiometricRecord.aad(id)); ivs[i] = enc.getIV();
                    byte[] ct = enc.doFinal(plain);
                    var saved = BiometricRecord.decode(new BiometricRecord(id, ivs[i], ct).encode());
                    assertFalse(contains(saved.encode(), plain));
                    Cipher dec = Cipher.getInstance("AES/GCM/NoPadding"); dec.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, saved.iv));
                    dec.updateAAD(BiometricRecord.aad(saved.vaultId)); byte[] recovered = dec.doFinal(saved.ciphertext);
                    try { assertArrayEquals(chars, PasswordBuffer.decode(recovered)); }
                    finally { Arrays.fill(recovered, (byte) 0); }
                    for (int change = 0; change < 3; change++) {
                        Cipher bad = Cipher.getInstance("AES/GCM/NoPadding"); bad.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, saved.iv));
                        byte[] aad = BiometricRecord.aad(change == 1 ? "b".repeat(64) : id);
                        if (change == 2) aad[0] ^= 1;
                        bad.updateAAD(aad); byte[] tampered = ct.clone(); if (change == 0) tampered[0] ^= 1;
                        assertThrows(AEADBadTagException.class, () -> bad.doFinal(tampered));
                    }
                } finally { Arrays.fill(plain, (byte) 0); }
            }
            assertArrayEquals(new char[chars.length], chars);
        }
        assertFalse(Arrays.equals(ivs[0], ivs[1]));
    }
    private boolean contains(byte[] haystack, byte[] needle) {
        for (int i = 0; i <= haystack.length - needle.length; i++)
            if (Arrays.equals(Arrays.copyOfRange(haystack, i, i + needle.length), needle)) return true;
        return false;
    }
    @Test public void truncatedMalformedOversizedAndTrailingRecordsFailClosed() throws Exception {
        byte[] valid = new BiometricRecord(id, new byte[12], new byte[16]).encode();
        for (int i = 0; i < valid.length; i++) {
            byte[] partial = Arrays.copyOf(valid, i);
            assertThrows(java.io.IOException.class, () -> BiometricRecord.decode(partial));
        }
        assertThrows(java.io.IOException.class, () -> BiometricRecord.decode(Arrays.copyOf(valid, valid.length + 1)));
        valid[3] = 2; assertThrows(java.io.IOException.class, () -> BiometricRecord.decode(valid));
        assertThrows(java.io.IOException.class, () -> BiometricRecord.decode(new byte[BiometricRecord.MAX_BYTES + 1]));
    }
    @Test public void invalidUtf8FailsAndClosedPasswordCannotBeRead() throws Exception {
        assertThrows(java.nio.charset.CharacterCodingException.class, () -> PasswordBuffer.decode(new byte[]{(byte) 0xff}));
        var password = new PasswordBuffer(new char[]{'x'}); password.close();
        assertThrows(IllegalStateException.class, password::encode);
    }
}
