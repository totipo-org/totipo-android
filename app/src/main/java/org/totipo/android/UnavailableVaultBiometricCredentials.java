package org.totipo.android;

/** Storage-unavailable fallback; never accesses any record or key namespace. */
final class UnavailableVaultBiometricCredentials implements BiometricCredentials {
    private final String localId;
    UnavailableVaultBiometricCredentials(String localId) { VaultRegistry.validateId(localId); this.localId = localId; }
    public String localId() { return localId; }
    public boolean available() { return false; }
    public boolean configured() { return false; }
    public Operation enrollment(String vaultId) { throw new IllegalStateException("Biometric storage unavailable"); }
    public Operation unlock() { throw new IllegalStateException("Per-vault biometric unlock is not available yet"); }
    public void delete() { throw new IllegalStateException("Biometric storage unavailable"); }
}
