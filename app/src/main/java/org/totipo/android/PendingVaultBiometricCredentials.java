package org.totipo.android;

/** A-MV1 production restriction: password only until per-entry credentials (A-MV4).
 * Never reads, adopts or deletes the historical global credential or Keystore alias.
 * Existing cryptography remains available only to its isolated regression harness.
 */
final class PendingVaultBiometricCredentials implements BiometricCredentials {
    public boolean available() { return false; }
    public boolean configured() { return false; }
    public Operation enrollment(String vaultId) { throw new IllegalStateException("Per-vault biometric enrollment is not available yet"); }
    public Operation unlock() { throw new IllegalStateException("Per-vault biometric unlock is not available yet"); }
    public void delete() { }
}
