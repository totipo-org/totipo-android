# Totipo Android

Totipo is an Android authenticator for time-based one-time passwords (TOTP).
It keeps your tokens in password-protected encrypted vaults, generates login codes
offline, and lets you manage multiple vaults on one device. Shared-folder
synchronization and biometric unlocking are optional.

## Install

Download the signed APK from
[GitHub Releases](https://github.com/totipo-org/totipo-android/releases/latest).
The [0.2.1 release](https://github.com/totipo-org/totipo-android/releases/tag/v0.2.1)
provides **`totipo-android-0.2.1.apk`**, its SHA-256 checksum and release provenance.
Open the APK on your Android device and, if prompted, allow that browser or file
manager to install apps from this source. For later updates, install the new signed
APK over the existing app; avoid uninstalling or clearing app data.

**Vault operations require Android 11 (API 30) or later.** The APK permits
installation on Android 8–10, but those versions show an unsupported-runtime
message and cannot use vaults. Biometric unlock requires available strong biometric
hardware and enrollment. Arbitrary devices and shared-folder providers are not
universally qualified; see the [user guide](docs/USER_GUIDE.md#compatibility).

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
