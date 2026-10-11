# Android production public signing identity

The permanent public signing identity is installed here for review and commit:
`android-release-cert.pem` and `android-release-cert.sha256`. The PEM contains
exactly one X.509 RSA-4096 certificate. The fingerprint file contains its DER
certificate SHA-256 as 64 lowercase hexadecimal characters and a newline:

```text
74fd4c313856e1773fd174fba4ecd2a12cc4fde85ae34a3d8a3e986353b01632
```

Subject and issuer: `CN=Totipo Android Release, O=Totipo`. Validity:
`2026-10-10T23:42:20Z` through `2076-10-10T23:42:20Z`.
These public files must be committed in the reviewed release source before the
protected workflow can proceed. One signer is supported; rotation and
proof-of-rotation are outside this milestone.

Private PKCS#12, JKS, keystore and key files are ignored in this directory. The
release guard permits tracking only this README and the two named public files.
Never place private signing material anywhere in the repository or Nix inputs.

The human reports generating the permanent private key, making offline recovery
copies, and configuring the protected GitHub `android-release` environment and
secrets separately. These are **human-reported only**; this provisioning work
neither accesses nor independently verifies the private key, passwords, recovery
copies, GitHub environment protections or secrets. The identity is intended to
last for the lifetime of the application; protect its recoverability accordingly.

See the [public identity provisioning report](../../review/ANDROID_PRODUCTION_SIGNING_IDENTITY_REPORT.md)
for validation and the [release procedure](../README.md) for a future deliberate
release. Installing this public identity does not sign or publish an application.
