package org.totipo.android;

import java.util.Arrays;
import java.util.Objects;
import org.totipo.TotpAlgorithm;

/** One operation owns the mutable secret text, including on rejected admission. Never persisted. */
public final class AddTokenRequest implements AutoCloseable {
    public final String issuer, account;
    public final TotpAlgorithm algorithm;
    public final int digits;
    public final long periodSeconds;
    private final char[] secret;
    public AddTokenRequest(String issuer, String account, TotpAlgorithm algorithm,
                           int digits, long periodSeconds, char[] secret) {
        this.secret = Objects.requireNonNull(secret);
        this.issuer = issuer; this.account = account; this.algorithm = algorithm;
        this.digits = digits; this.periodSeconds = periodSeconds;
    }
    /** Worker-only ingress; decoder retains no text. */
    public byte[] decodeSecret() { return Base32.decode(secret); }
    @Override public void close() { Arrays.fill(secret, '\0'); }
    @Override public String toString() { return "AddTokenRequest[redacted]"; }
}
