package org.totipo.android;

import java.time.Instant;
import org.totipo.TokenId;

/** Detached, sensitive, process-only presentation. Never save or log its contents. */
public record RevealedTotp(TokenId tokenId, String code, Instant validFrom,
                           Instant validUntil, int digits) {
    @Override public String toString() { return "RevealedTotp[redacted]"; }
}
