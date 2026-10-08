package org.totipo.android;

import java.util.Arrays;

/** Strict RFC 4648 ingress only. Does not retain input or include it in errors. */
public final class Base32 {
    private Base32() { }

    public static byte[] decode(char[] input) {
        int symbols = 0;
        int padding = 0;
        for (char c : input) {
            if (ignored(c)) { continue; }
            if (c == '=') { padding++; }
            else {
                if (padding != 0 || value(c) < 0) { throw invalid(); }
                symbols++;
            }
        }
        int remainder = symbols % 8;
        int expectedPadding = switch (remainder) {
            case 0 -> 0;
            case 2 -> 6;
            case 4 -> 4;
            case 5 -> 3;
            case 7 -> 1;
            default -> throw invalid();
        };
        long size = (long) symbols * 5 / 8;
        if (size < 1 || size > 128 || (padding != 0 && padding != expectedPadding)) { throw invalid(); }
        byte[] decoded = new byte[(int) size];
        int accumulator = 0;
        int bits = 0;
        int index = 0;
        for (char c : input) {
            if (ignored(c) || c == '=') { continue; }
            accumulator = (accumulator << 5) | value(c);
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                decoded[index++] = (byte) (accumulator >>> bits);
            }
            accumulator &= (1 << bits) - 1;
        }
        if (accumulator != 0) {
            Arrays.fill(decoded, (byte) 0);
            throw invalid();
        }
        return decoded;
    }

    private static boolean ignored(char c) {
        return c == ' ' || c == '\t' || c == '\r' || c == '\n' || c == '-';
    }
    private static int value(char c) {
        if (c >= 'A' && c <= 'Z') { return c - 'A'; }
        if (c >= 'a' && c <= 'z') { return c - 'a'; }
        if (c >= '2' && c <= '7') { return c - '2' + 26; }
        return -1;
    }
    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Enter a valid Base32 secret containing 1–128 decoded bytes.");
    }
}
