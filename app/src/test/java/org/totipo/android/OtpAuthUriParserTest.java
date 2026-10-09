package org.totipo.android;

import java.lang.reflect.Field;
import org.junit.Test;
import org.totipo.TotpAlgorithm;
import static org.junit.Assert.*;

public final class OtpAuthUriParserTest {
    private static final String SECRET = "MY======";
    private static String uri(String label, String query) { return "otpauth://totp/" + label + "?" + query; }
    private static OtpAuthUriParser.Draft parse(String label, String query) throws Exception {
        return OtpAuthUriParser.parse(uri(label, query));
    }
    private static void rejected(String input) throws Exception {
        try (var unexpected = OtpAuthUriParser.parse(input)) { fail("Accepted invalid public test case"); }
        catch (OtpAuthUriParser.Rejected expected) {
            assertEquals("Authenticator link rejected", expected.getMessage());
            assertFalse(expected.toString().contains("secret="));
        }
    }
    @Test public void defaultsAndBase32Reuse() throws Exception {
        try (var draft = parse("alice+tag@example.com", "secret=" + SECRET)) {
            assertEquals("", draft.issuer()); assertEquals("alice+tag@example.com", draft.account());
            assertEquals(TotpAlgorithm.SHA1, draft.algorithm); assertEquals(6, draft.digits); assertEquals(30, draft.periodSeconds);
            try (var request = draft.transfer()) { assertArrayEquals(new byte[]{102}, request.decodeSecret()); }
        }
    }
    @Test public void labelIssuerAndUnicode() throws Exception {
        String[][] cases = {
            {"Example:alice", "", "Example", "alice"},
            {"Example%3Aalice", "", "Example", "alice"},
            {"Example:%20%20alice", "", "Example", "alice"},
            {"alice", "&issuer=Example", "Example", "alice"},
            {"Example:alice", "&issuer=Example", "Example", "alice"},
            {"%E4%BE%8B:%C3%A9%2Fname", "&issuer=%E4%BE%8B", "例", "é/name"},
            {"Example:%2Balice", "", "Example", "+alice"},
            {"alice", "&issuer=E+X", "E+X", "alice"},
            {"alice%20", "", "", "alice "},
            {"%20Example:alice", "", " Example", "alice"}
        };
        for (String[] c : cases) try (var draft = parse(c[0], "secret=" + SECRET + c[1])) {
            assertEquals(c[2], draft.issuer()); assertEquals(c[3], draft.account());
        }
    }
    @Test public void acceptedOptionsAndOrder() throws Exception {
        for (String algorithm : new String[]{"SHA1", "SHA256", "SHA512", "sha256", "sHa512"})
            for (int digits : new int[]{6, 8}) for (long period : new long[]{30, 45, 1, 4294967295L}) {
                for (String query : new String[]{
                        "secret=my======&algorithm=" + algorithm + "&digits=" + digits + "&period=" + period,
                        "period=" + period + "&digits=" + digits + "&algorithm=" + algorithm + "&secret=MY&unknown=a+b%3D%C3%A9",
                        "digits=" + digits + "&secret=MY&algorithm=" + algorithm + "&period=" + period}) {
                    try (var draft = parse("account", query)) {
                        assertEquals(TotpAlgorithm.valueOf(algorithm.toUpperCase(java.util.Locale.ROOT)), draft.algorithm);
                        assertEquals(digits, draft.digits); assertEquals(period, draft.periodSeconds);
                    }
                }
            }
        try (var draft = OtpAuthUriParser.parse("OTPAUTH://TOTP/account?%73ecret=MY")) { assertEquals("account", draft.account()); }
    }
    @Test public void structuralFailures() throws Exception {
        for (String input : new String[]{
                "https://totp/a?secret=MY", "otpauth://hotp/a?secret=MY", "otpauth-migration://offline/a",
                "otpauth:///a?secret=MY", "otpauth://other/a?secret=MY", "otpauth://x@totp/a?secret=MY",
                "otpauth://totp:123/a?secret=MY", "otpauth://%74otp/a?secret=MY", "otpauth:totp/a?secret=MY",
                "otpauth://totp/a?secret=MY#fragment", "otpauth://totp?secret=MY", "otpauth://totp/?secret=MY",
                "otpauth://totp/a/b?secret=MY", "otpauth://totp/:a?secret=MY", "otpauth://totp/i:%20?secret=MY",
                "otpauth://totp/i:a:b?secret=MY", "otpauth://totp/i%3Aa%3Ab?secret=MY", "otpauth://totp/a",
                "otpauth://totp/a?", "otpauth://totp/a?issuer=i", "otpauth://totp/a?Secret=MY",
                "otpauth://totp/a?secret=", "otpauth://totp/a?secret=MY&", "otpauth://totp/a?secret=MY&&x=y",
                "otpauth://totp/a?secret=MY&x", "otpauth://totp/a?secret=MY&=y",
                "otpauth://totp/a?secret=MY&secret=MY", "otpauth://totp/a?secret=MY&%73ecret=MY",
                "otpauth://totp/a?secret=MY&issuer=i&issuer=i", "otpauth://totp/i:a?secret=MY&issuer=I",
                "otpauth://totp/i:a?secret=MY&issuer=i%20", "otpauth://totp/a?secret=MY&issuer=",
                "otpauth://totp/a?secret=MY&ISSUER=i&Secret=MY&secret=MY",
                "otpauth://totp/a?secret=MY&unknown=%GG", "otpauth://totp/a?secret=MY&%FF=v",
                "otpauth://totp/%C0%AF?secret=MY", "otpauth://totp/%ED%A0%80?secret=MY",
                "otpauth://totp/%F4%90%80%80?secret=MY", "otpauth://totp/a%?secret=MY",
                "otpauth://totp/a%0?secret=MY", "otpauth://totp/%FF?secret=MY"}) {
            // One case above deliberately combines an encoded duplicate with case-variant unknown fields.
            rejected(input);
        }
    }
    @Test public void secretFailures() throws Exception {
        for (String secret : new String[]{"", "A", "M1", "M8", "MY%20", "M-Y", "MY%09", "MY%0D%0A",
                "MY=", "MY=======", "M=Y", "MZ", "========", "A".repeat(208), "MY+"})
            rejected(uri("a", "secret=" + secret));
        for (String value : new String[]{"MD5", "SHA224", "SHA384", "unknown", "", "%20SHA1"})
            rejected(uri("a", "secret=MY&algorithm=" + value));
        for (String value : new String[]{"5", "7", "9", "06", "", "+6"}) rejected(uri("a", "secret=MY&digits=" + value));
        for (String value : new String[]{"0", "-30", "+30", "4294967296", "99999999999999999999999", "3.0", "3e1", "%2030", "30%20", ""})
            rejected(uri("a", "secret=MY&period=" + value));
        rejected(uri("a", "secret=MY&counter=0"));
        for (String name : new String[]{"algorithm", "digits", "period", "issuer"})
            rejected(uri("a", "secret=MY&" + name + "=6&" + name + "=6"));
    }
    @Test public void stringAndGlobalBounds() throws Exception {
        rejected(uri("a".repeat(257), "secret=MY"));
        rejected(uri("a", "secret=MY&issuer=" + "a".repeat(257)));
        rejected(uri("%C3%A9".repeat(129), "secret=MY"));
        for (String control : new String[]{"%00", "%09", "%0A", "%0D", "%7F", "%C2%85", "%E2%80%A8", "%E2%80%AE", "%F3%A0%80%81"}) {
            rejected(uri("a" + control, "secret=MY")); rejected(uri("a", "secret=MY&issuer=i" + control));
        }
        rejected(uri("a", "secret=MY&unknown=" + "a".repeat(8192)));
        rejected(uri("a", "secret=MY&unknown=%C3"));
        rejected(uri("a", "secret=MY&issuer=i%3Ax"));
        rejected(uri("a\uD800", "secret=MY"));
        String prefix = uri("a", "secret=MY&unknown=");
        try (var draft = OtpAuthUriParser.parse(prefix + "a".repeat(8192 - prefix.length()))) { assertEquals("a", draft.account()); }
        try (var draft = parse("a", "secret=" + "A".repeat(205))) {
            try (var request = draft.transfer()) { byte[] decoded = request.decodeSecret(); assertEquals(128, decoded.length); java.util.Arrays.fill(decoded, (byte) 0); }
        }
        try (var draft = parse("a".repeat(256), "secret=MY&issuer=" + "a".repeat(256))) { assertEquals(256, draft.account().length()); }
    }
    @Test public void ownershipWipedOnCloseAndTransferOnlyOnce() throws Exception {
        var draft = parse("a", "secret=MY");
        Field field = draft.getClass().getDeclaredField("secret"); field.setAccessible(true);
        char[] owned = (char[]) field.get(draft);
        assertFalse(draft.toString().contains("MY"));
        draft.close(); draft.close(); assertArrayEquals(new char[owned.length], owned);
        assertEquals("", draft.account());
        try { draft.transfer(); fail(); } catch (IllegalStateException expected) { }
        draft = parse("a", "secret=MY"); owned = (char[]) field.get(draft);
        AddTokenRequest request = draft.transfer(); draft.close();
        assertArrayEquals(new char[]{'M', 'Y'}, owned);
        try { draft.transfer(); fail(); } catch (IllegalStateException expected) { }
        request.close(); assertArrayEquals(new char[2], owned);
    }
}
