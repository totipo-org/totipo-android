# Android production signing identity — not provisioned

This directory intentionally contains no production certificate, fingerprint or
private key. The protected workflow fails before environment approval until both
`android-release-cert.pem` and `android-release-cert.sha256` are committed here.
The PEM must contain exactly one X.509 certificate. The fingerprint file contains
exactly its DER certificate SHA-256 as 64 lowercase hexadecimal characters and a
newline. These are public identity, not secrets. One signer is supported; rotation
and proof-of-rotation are outside this milestone.

Private PKCS#12, JKS, keystore and key files are ignored in this directory. The
release guard permits tracking only this README and the two named public files.
Never place private signing material anywhere in the repository or Nix inputs.

## Separate human-controlled provisioning milestone

1. Generate the permanent RSA Android signing key locally/offline in PKCS#12.
2. Create encrypted offline recovery copies and verify recovery before use.
3. Export its public certificate as `android-release-cert.pem`.
4. Compute the SHA-256 of the DER certificate and write `android-release-cert.sha256`.
5. Review and commit only these public files. Keep the private key outside source.
6. Create the GitHub Environment named exactly `android-release`.
7. Configure environment secrets `ANDROID_RELEASE_KEYSTORE_B64`,
   `ANDROID_RELEASE_KEYSTORE_PASSWORD`, `ANDROID_RELEASE_KEY_ALIAS`, and
   `ANDROID_RELEASE_KEY_PASSWORD`. The first is base64 of the PKCS#12 keystore.
8. Require a manual reviewer; restrict deployments to intended release refs
   (the workflow dispatches on main), and prevent self-review where supported.
   Ordinary CI must never reference this environment.
9. Prepare/review the first release source/version and existing annotated tag,
   then perform the first protected release according to [release procedure](../README.md).

No provisioning, environment creation, secret installation or production signing
is authorized by the machinery milestone. The first identity is intended to last
for the lifetime of the application; protect its recoverability accordingly.
