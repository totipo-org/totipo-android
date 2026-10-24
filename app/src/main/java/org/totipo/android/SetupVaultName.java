package org.totipo.android;

/** Non-secret, activation-scoped setup draft. Never contains provider identity or credentials. */
final class SetupVaultName {
    private String value = "";
    private boolean edited;
    String value() { return value; }
    void edit(String text) { value = text; edited = true; }
    void suggest(String displayName) {
        if (edited || displayName == null || displayName.isEmpty()) return;
        // Suggest only metadata acceptable to the existing registry; do not truncate
        // malformed provider text or synthesize a label from an opaque URI.
        try { new VaultRegistry.Entry("0".repeat(32), displayName, VaultRegistry.State.PENDING, false); }
        catch (IllegalArgumentException invalid) { return; }
        value = displayName;
    }
}
