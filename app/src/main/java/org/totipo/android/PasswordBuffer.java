package org.totipo.android;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Mutable UTF-8 intermediates only; never construct an immutable password String. */
final class PasswordBuffer implements AutoCloseable {
    private char[] value;
    PasswordBuffer(char[] value) { this.value = value; }
    synchronized byte[] encode() throws CharacterCodingException {
        if (value == null) throw new IllegalStateException("Credential expired");
        if (value.length > BiometricRecord.MAX_BYTES) throw new IllegalArgumentException("Credential too large");
        byte[] temporary = new byte[value.length * 3];
        try {
            ByteBuffer encoded = ByteBuffer.wrap(temporary);
            var encoder = StandardCharsets.UTF_8.newEncoder();
            var result = encoder.encode(CharBuffer.wrap(value), encoded, true); if (result.isError()) result.throwException();
            result = encoder.flush(encoded); if (result.isError()) result.throwException();
            return Arrays.copyOf(temporary, encoded.position());
        } finally { Arrays.fill(temporary, (byte) 0); }
    }
    static char[] decode(byte[] value) throws CharacterCodingException {
        char[] temporary = new char[value.length];
        try {
            CharBuffer decoded = CharBuffer.wrap(temporary);
            var decoder = StandardCharsets.UTF_8.newDecoder();
            var result = decoder.decode(ByteBuffer.wrap(value), decoded, true); if (result.isError()) result.throwException();
            result = decoder.flush(decoded); if (result.isError()) result.throwException();
            return Arrays.copyOf(temporary, decoded.position());
        } finally { Arrays.fill(temporary, '\0'); }
    }
    @Override public synchronized void close() { if (value != null) Arrays.fill(value, '\0'); value = null; }
}
