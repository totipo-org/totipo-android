package org.totipo.android;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.totipo.android.reconcile.ForegroundVaultCoordinator.ObservedToken;

/** Orders detached display items only; never selects a conflicting alternative. */
final class TokenDisplayOrder {
    private TokenDisplayOrder() {}
    private static String fold(String value) { return value.toLowerCase(Locale.ROOT); }
    private static String primary(ObservedToken token) {
        if (token.conflict() || token.alternatives().size() != 1) return "";
        var descriptor = token.alternatives().get(0);
        return fold(descriptor.issuer().isEmpty() ? descriptor.account() : descriptor.issuer());
    }
    private static String account(ObservedToken token) {
        return token.conflict() || token.alternatives().size() != 1 ? "" : fold(token.alternatives().get(0).account());
    }
    static final Comparator<ObservedToken> ORDER = Comparator.comparing(TokenDisplayOrder::primary)
            .thenComparing(TokenDisplayOrder::account).thenComparing(t -> t.id().hex());
    static List<ObservedToken> sorted(List<ObservedToken> tokens) { return tokens.stream().sorted(ORDER).collect(java.util.stream.Collectors.toList()); }
}
