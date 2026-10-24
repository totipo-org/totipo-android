# Totipo Android

Totipo is an Android authenticator for time-based one-time passwords (TOTP).
It keeps your tokens in password-protected encrypted vaults, generates login codes
offline, and lets you manage multiple vaults on one device. Shared-folder
synchronization and biometric unlocking are optional.

## Install

Download the signed APK from
[GitHub Releases](https://github.com/totipo-org/totipo-android/releases/latest).
Expand **Assets** and select the signed **`totipo-android-<version>.apk`**;
the adjacent `.apk.sha256` file is its checksum. Verify the APK as described below,
then open it on your Android device and, if prompted, allow that browser or file
manager to install apps from this source. For later updates, install the new signed
APK over the existing app; avoid uninstalling or clearing app data.

**Requires Android 11 or newer (API 30+).** Android enforces this requirement
when installing the APK. Biometric unlock requires available strong biometric
hardware and enrollment. Arbitrary devices and shared-folder providers are not
universally qualified; see the [user guide](docs/USER_GUIDE.md#compatibility).

Optionally, use [Obtainium](https://github.com/ImranR98/Obtainium) to follow
[this repository](https://github.com/totipo-org/totipo-android) and manage updates
from GitHub Releases. Verify the signing identity below when setting it up;
following a repository alone does not establish the expected signer.

## Verify the APK

Production package ID: **`org.totipo.android`**. Signing certificate SHA-256:

```text
74fd4c313856e1773fd174fba4ecd2a12cc4fde85ae34a3d8a3e986353b01632
```

This value is copied from the canonical [public fingerprint](release/signing/android-release-cert.sha256)
for the committed [public certificate](release/signing/android-release-cert.pem).

On Android, use [AppVerifier](https://github.com/soupslurpr/AppVerifier) to compare
Totipo's package ID and signing certificate SHA-256 with the values above.
Alternatively, on a computer with [Android SDK Build Tools](https://developer.android.com/tools/apksigner),
run this command, replacing the filename with your downloaded APK:

```sh
apksigner verify --print-certs "totipo-android-<version>.apk"
```

The command must succeed and its **Signer #1 certificate SHA-256 digest** must
match the fingerprint above (ignore letter case and colon separators). Stop if
verification fails or the identity differs. The certificate fingerprint identifies
the signer across releases; the release's `.apk.sha256` checksum identifies that
specific APK file and is not a substitute for checking the signing certificate.

## Start using Totipo

On the **Vaults** screen:

- Choose **Create Local Vault** to start a new vault. Give it an optional name and
  a password; no shared folder or network is needed.
- Choose **Join Existing Vault** to use a vault already available in a shared
  folder. Select that vault's folder and unlock it with its existing password.
  Creating a new vault does not join an existing one.

Inside an unlocked vault, use **Add token** to enter the service's setup secret
and TOTP settings. Totipo also accepts supported `otpauth://totp` links or text
shared by another app; it has no built-in QR scanner. Search your tokens, choose
**Show code** to view a code, and **Copy code** to copy it. Token menus let you
edit issuer/account labels or confirm deletion. Codes hide when they expire;
clipboard cleanup is best effort. Deletion can leave encrypted token history.

Use **More options → Change Vault** to switch vaults, or rename and forget entries
from **Vaults**. Each vault has its own password, optional biometric unlock and
optional sync folder. Switching requires unlocking the chosen vault.

Password unlock is always available. You can opt into biometric unlock on the
password screen. This stores an **encrypted reusable vault password on this
device**, protected by Android Keystore and a fresh strong-biometric authorization
for each use. Keep your password for recovery. **Lock** locks immediately; Totipo
also locks after **15 minutes without user interaction**, including time spent in
the background. Backgrounding alone does not immediately lock it.

## Optional synchronization

Use **Manage sync folder** to choose a folder accessible through Android's folder
picker. For a new empty folder, explicitly **Initialize sync folder**, then **Sync**.
Use a separate folder for each independent vault.

Totipo reads and writes encrypted vault files there. Tools such as **Syncthing**
can separately replicate that folder between devices; they are not integrated
into Totipo. Sync attempts run while the selected vault is open, after unlocking,
returning to the foreground, and saving token changes. Manual **Sync** retries.
There is no background sync or polling, and inactive vaults do not sync.

A successful Sync describes the selected folder, **not receipt by another device**.
Local changes survive folder failures; concurrent edits may need an explicit
conflict choice. Follow the [user guide](docs/USER_GUIDE.md#shared-folder-synchronization)
for setup, status meanings and troubleshooting.

## Protect your vaults

Vaults live in app-private no-backup storage. **Uninstalling or clearing app data
can permanently destroy local-only and forgotten vaults.** Forget retains local
encrypted data; it is not secure deletion. Synchronization does not replace backup:
keep independent encrypted copies and verify that you can open them with your
password. There is no password-change or legacy-vault migration workflow.
Before resetting an older incompatible installation, preserve its vaults using the
previous app and verify another usable copy.

Biometric protection depends on Android's security model and cannot protect a
decrypted password from a compromised app process. See the
[security and backup limitations](docs/USER_GUIDE.md#security-and-backup).

## More documentation

- [User guide](docs/USER_GUIDE.md): vaults, tokens, biometrics, Sync and recovery.
- [Development](docs/DEVELOPMENT.md): Nix, Gradle, Android SDK, IDE and qualification.
- [Releases and signing](release/README.md): maintainer procedures and public identity.
- [Vault and Sync internals](docs/ARCHITECTURE.md), [multi-vault architecture](MULTI_VAULT.md)
  and [Java dependency provenance](TOTIPO_JAVA_DEPENDENCY.md).
- [Review reports](review/): historical qualification evidence and its limits.
