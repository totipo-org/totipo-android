package org.totipo.android;

import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;

public final class Base32Test {
    @Test public void desktopNormalizationAndRfcPaddingMatch() {
        for (String input : new String[]{"MY", "my======", " \tM-y\r\n====== "})
            assertArrayEquals(new byte[]{102}, Base32.decode(input.toCharArray()));
        assertArrayEquals("12345678901234567890".getBytes(StandardCharsets.US_ASCII),
                Base32.decode("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ".toCharArray()));
    }
    @Test public void malformedEmptyPaddingAndNonzeroTrailingBitsRejected() {
        for (String input : new String[]{"", " -- ", "M", "M0", "M1", "M8", "M9", "M!", "M=Y", "MY=", "MZ", "MY======A", "MY=======", "MY\u00a0"}) {
            var error = assertThrows(IllegalArgumentException.class, () -> Base32.decode(input.toCharArray()));
            assertEquals("Enter a valid Base32 secret containing 1–128 decoded bytes.", error.getMessage());
        }
    }
    @Test public void ingressLengthLimitsAndOwnedRequestClearing() {
        assertEquals(128, Base32.decode("A".repeat(205).toCharArray()).length);
        assertThrows(IllegalArgumentException.class, () -> Base32.decode("A".repeat(207).toCharArray()));
        char[] input = "MY".toCharArray();
        var request = new AddTokenRequest("", "", org.totipo.TotpAlgorithm.SHA1, 6, 30, input);
        assertEquals("AddTokenRequest[redacted]", request.toString());
        request.close(); assertArrayEquals(new char[2], input);
    }
}
