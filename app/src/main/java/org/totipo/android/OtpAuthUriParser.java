package org.totipo.android;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.totipo.TotpAlgorithm;

/** Bounded Key URI parsing. No form decoding, logging, retained source or second Base32 decoder. */
public final class OtpAuthUriParser {
    public static final int MAX_INPUT_UNITS = 8192;
    private static final Set<String> SEMANTIC = Set.of("secret", "issuer", "algorithm", "digits", "period", "counter");
    private OtpAuthUriParser() { }
    public enum Reason { INVALID, UNSUPPORTED }
    public static final class Rejected extends Exception {
        public final Reason reason;
        private Rejected(Reason reason) { super("Authenticator link rejected"); this.reason = reason; }
    }

    /** Instance-local owner. Transfer is exclusive and one-shot; close is idempotent. */
    public static final class Draft implements AutoCloseable {
        private char[] secret;
        private String issuer, account;
        public final TotpAlgorithm algorithm;
        public final int digits;
        public final long periodSeconds;
        private Draft(String issuer, String account, TotpAlgorithm algorithm, int digits, long period, char[] secret) {
            this.issuer = issuer; this.account = account; this.algorithm = algorithm;
            this.digits = digits; this.periodSeconds = period; this.secret = secret;
        }
        public String issuer() { return issuer; }
        public String account() { return account; }
        public AddTokenRequest transfer() {
            if (secret == null) throw new IllegalStateException("Enrollment expired");
            AddTokenRequest request = new AddTokenRequest(issuer, account, algorithm, digits, periodSeconds, secret);
            secret = null; issuer = account = "";
            return request;
        }
        @Override public void close() {
            if (secret != null) { Arrays.fill(secret, '\0'); secret = null; }
            issuer = account = "";
        }
        @Override public String toString() { return "EnrollmentDraft[redacted]"; }
    }

    public static Draft parse(String input) throws Rejected {
        char[] secret = null;
        try {
            if (input == null || input.isEmpty() || input.length() > MAX_INPUT_UNITS) throw invalid();
            URI uri;
            try { uri = new URI(input); } catch (URISyntaxException failure) { throw invalid(); }
            if (!"otpauth".equalsIgnoreCase(uri.getScheme())) throw unsupported();
            if ("hotp".equalsIgnoreCase(uri.getRawAuthority())) throw unsupported();
            if (uri.isOpaque() || !"totp".equalsIgnoreCase(uri.getRawAuthority())
                    || uri.getRawFragment() != null) throw invalid();
            String path = uri.getRawPath();
            if (path == null || path.length() < 2 || path.charAt(0) != '/' || path.indexOf('/', 1) >= 0) throw invalid();
            String label = text(path.substring(1));
            int colon = label.indexOf(':');
            if (colon != label.lastIndexOf(':')) throw invalid();
            String prefix = null, account = label;
            if (colon >= 0) {
                prefix = label.substring(0, colon);
                int start = colon + 1;
                while (start < label.length() && label.charAt(start) == ' ') start++;
                account = label.substring(start);
                field(prefix);
            }
            field(account);
            String query = uri.getRawQuery();
            if (query == null || query.isEmpty()) throw invalid();
            String issuer = null;
            TotpAlgorithm algorithm = TotpAlgorithm.SHA1;
            int digits = 6;
            long period = 30;
            Set<String> seen = new HashSet<>();
            for (String pair : query.split("&", -1)) {
                int equals = pair.indexOf('=');
                if (equals <= 0) throw invalid();
                String name = text(pair.substring(0, equals));
                if (name.isEmpty()) throw invalid();
                boolean recognized = SEMANTIC.contains(name);
                if (recognized && !seen.add(name)) throw invalid();
                char[] value = decode(pair.substring(equals + 1));
                try {
                    switch (name) {
                        case "secret" -> {
                            lexicalSecret(value);
                            byte[] check = null;
                            try { check = Base32.decode(value); }
                            catch (IllegalArgumentException failure) { throw invalid(); }
                            finally { if (check != null) Arrays.fill(check, (byte) 0); }
                            secret = value; value = null;
                        }
                        case "issuer" -> { issuer = new String(value); field(issuer); }
                        case "algorithm" -> {
                            if (asciiEquals(value, "SHA1")) algorithm = TotpAlgorithm.SHA1;
                            else if (asciiEquals(value, "SHA256")) algorithm = TotpAlgorithm.SHA256;
                            else if (asciiEquals(value, "SHA512")) algorithm = TotpAlgorithm.SHA512;
                            else throw unsupported();
                        }
                        case "digits" -> {
                            if (value.length != 1 || (value[0] != '6' && value[0] != '8')) throw unsupported();
                            digits = value[0] - '0';
                        }
                        case "period" -> { period = decimal(value); }
                        case "counter" -> throw unsupported();
                        default -> { /* Syntactically decoded before being ignored. */ }
                    }
                } finally { if (value != null) Arrays.fill(value, '\0'); }
            }
            if (secret == null) throw invalid();
            if (prefix != null && issuer != null && !prefix.equals(issuer)) throw invalid();
            if (issuer == null) issuer = prefix == null ? "" : prefix;
            Draft result = new Draft(issuer, account, algorithm, digits, period, secret);
            secret = null;
            return result;
        } finally { if (secret != null) Arrays.fill(secret, '\0'); }
    }
    private static void lexicalSecret(char[] value) throws Rejected {
        if (value.length == 0) throw invalid();
        for (char c : value) {
            if (!(c >= 'A' && c <= 'Z') && !(c >= 'a' && c <= 'z')
                    && !(c >= '2' && c <= '7') && c != '=') throw invalid();
        }
    }
    private static boolean asciiEquals(char[] value, String expected) {
        if (value.length != expected.length()) return false;
        for (int i = 0; i < value.length; i++) {
            char c = value[i];
            if (c >= 'a' && c <= 'z') c -= 32;
            if (c != expected.charAt(i)) return false;
        }
        return true;
    }
    private static long decimal(char[] value) throws Rejected {
        if (value.length == 0) throw invalid();
        long number = 0;
        for (char c : value) {
            if (c < '0' || c > '9' || number > (4294967295L - (c - '0')) / 10) throw invalid();
            number = number * 10 + c - '0';
        }
        if (number == 0) throw invalid();
        return number;
    }
    private static void field(String value) throws Rejected {
        if (value.isEmpty() || value.indexOf(':') >= 0 || value.getBytes(StandardCharsets.UTF_8).length > 256) throw invalid();
        for (int i = 0; i < value.length();) {
            int c = value.codePointAt(i);
            if (Character.isISOControl(c) || Character.getType(c) == Character.FORMAT
                    || c == '\u2028' || c == '\u2029') throw invalid();
            i += Character.charCount(c);
        }
    }
    private static String text(String raw) throws Rejected {
        char[] chars = decode(raw);
        try { return new String(chars); } finally { Arrays.fill(chars, '\0'); }
    }
    /** Percent octets and literal Unicode share a strict UTF-8 codec; '+' is never special. */
    private static char[] decode(String raw) throws Rejected {
        WipingBytes bytes = new WipingBytes(raw.length() * 3);
        byte[] octets = null;
        CharBuffer decoded = null;
        try {
            for (int i = 0; i < raw.length();) {
                if (raw.charAt(i) == '%') {
                    if (i + 2 >= raw.length()) throw invalid();
                    int high = hex(raw.charAt(i + 1)), low = hex(raw.charAt(i + 2));
                    if (high < 0 || low < 0) throw invalid();
                    bytes.write(high * 16 + low); i += 3;
                } else {
                    int end = raw.indexOf('%', i);
                    if (end < 0) end = raw.length();
                    ByteBuffer literal = StandardCharsets.UTF_8.newEncoder()
                            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                            .encode(CharBuffer.wrap(raw, i, end));
                    try { while (literal.hasRemaining()) bytes.write(literal.get()); }
                    finally { if (literal.hasArray()) Arrays.fill(literal.array(), (byte) 0); }
                    i = end;
                }
            }
            octets = bytes.toByteArray();
            decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(octets));
            char[] result = new char[decoded.remaining()]; decoded.get(result); return result;
        } catch (CharacterCodingException failure) { throw invalid(); }
        finally {
            if (octets != null) Arrays.fill(octets, (byte) 0);
            if (decoded != null && decoded.hasArray()) Arrays.fill(decoded.array(), '\0');
            bytes.clear();
        }
    }
    private static final class WipingBytes extends ByteArrayOutputStream {
        WipingBytes(int capacity) { super(capacity); }
        void clear() { Arrays.fill(buf, (byte) 0); reset(); }
    }
    private static int hex(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        return -1;
    }
    private static Rejected invalid() { return new Rejected(Reason.INVALID); }
    private static Rejected unsupported() { return new Rejected(Reason.UNSUPPORTED); }
}
