package org.totipo.android;

import org.totipo.android.reconcile.ForegroundVaultCoordinator.ObservedToken;

/** Detached, non-secret form basis. Identity is an admission capability, never persisted. */
public final class TokenChange {
    public enum Kind { EDIT, DELETE, RESOLVE }
    public enum Result { SAVED, STALE, INVALID, FAILED, PUBLICATION_UNCERTAIN }
    private final Kind kind;
    private final ObservedToken basis;
    TokenChange(Kind kind, ObservedToken basis) { this.kind = kind; this.basis = basis; }
    public Kind kind() { return kind; }
    public ObservedToken basis() { return basis; }
    public static boolean live(ObservedToken token) {
        return !token.conflict() && token.unresolved().isEmpty() && token.alternatives().size() == 1
                && token.alternatives().get(0).status() == org.totipo.TokenStatus.ACTIVE;
    }
    public static boolean resolvable(ObservedToken token) {
        return token.conflict() && token.unresolved().isEmpty() && token.alternatives().size() > 1;
    }
    @Override public String toString() { return "TokenChange[" + kind + "]"; }
}
