# Totipo Android

## What is Totipo?

Totipo is an authenticator for time-based one-time passwords (TOTP), the short-lived codes used for two-factor authentication.

It stores your authenticator entries in password-protected, encrypted vaults that you control.

## Why use Totipo?

**Your secrets stay encrypted.** The secret keys used to generate your one-time codes are encrypted while stored in your vault. You unlock the vault with your password when you need them.

**Your data is yours.** Totipo uses an [open-source, publicly documented vault format](https://github.com/totipo-org/totipo-spec). The format isn't tied to an online account or hosted service, and its encrypted files can be backed up independently.

**Use your vault across devices.** Compatible Totipo clients share the same vault format. You can synchronize vault folders between devices using a file synchronization tool of your choice.

Synchronization is optional and doesn't replace independent backups.

## Getting started

### Install Totipo

Download the signed APK from the [latest release](https://github.com/totipo-org/totipo-android/releases/latest). Under **Assets**, choose `totipo-android-<version>.apk`. Totipo requires **Android 11 or newer**.

Before installing, verify the APK's signing identity with [AppVerifier](https://github.com/soupslurpr/AppVerifier) or Android SDK's `apksigner`. The package ID is `org.totipo.android`, and the signing certificate's **SHA-256 fingerprint** is:

```text
74fd4c313856e1773fd174fba4ecd2a12cc4fde85ae34a3d8a3e986353b01632
```

Compare this against the downloaded APK, not just the file checksum. The [public signing certificate](release/signing/android-release-cert.pem) and [fingerprint](release/signing/android-release-cert.sha256) are in this repository. [Obtainium](https://github.com/ImranR98/Obtainium) can optionally follow GitHub releases for updates, but following a repository alone does not verify its signer. See the [user guide](docs/USER_GUIDE.md) for installation and verification details.

### Create your first vault

1. On **Vaults**, choose **Create Local Vault**, name it if you like, and set a strong password.
2. Unlock the vault and choose **Add token** to enter an authenticator setup.
3. Choose **Show code** to see a code, then **Copy code** if needed.

You can also **Join Existing Vault** if its files are available through a folder on your device, or enable optional biometric unlocking. See the [user guide](docs/USER_GUIDE.md) for these workflows.

**Important:** Local-only vaults live in Android's private app storage. Uninstalling Totipo or clearing its data can permanently remove them. **Totipo Android does not yet provide a standalone backup/export operation.** If you need a recoverable copy, arrange and verify an independent copy of the complete encrypted vault through the existing shared-folder workflow; keep your vault password and service recovery codes safe. [Details](docs/USER_GUIDE.md#shared-folder-synchronization).

## Learn more

- [User guide](docs/USER_GUIDE.md) — Vaults, biometrics, synchronization, security, and recovery.
- **Totipo apps** — [Desktop](https://github.com/totipo-org/totipo-desktop) · iOS (planned).
- [Development](docs/DEVELOPMENT.md) — Building, contributing, and testing.
- [Release signing](release/signing/README.md) — Signing identity and verification details.
