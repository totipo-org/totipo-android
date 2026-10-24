# Totipo Android user guide

Start with the [README](../README.md) for the download and a short introduction.
This guide describes the current app. Release procedures belong in the
[maintainer documentation](../release/README.md).

## Compatibility

**Requires Android 11 or newer (API 30+).** The installation minimum matches
the vault library's Java Flow runtime requirement; Android enforces it when
installing the APK.

Strong biometric support and enrolled biometrics are required for biometric unlock.
Password unlock works without biometric enrollment. Folder access depends on the
Android document provider selected in the system picker. Totipo has no Internet
permission, cloud SDK, built-in camera/QR scanner, background sync or polling.
An external scanner can open or share a supported TOTP link with Totipo.
The app uses a light theme; a dark system setting does not imply a dedicated dark
app theme.

The [integrated multi-vault qualification report](../review/A_MV6_INTEGRATED_MULTI_VAULT_QUALIFICATION_REPORT.md)
records successful physical Join, bidirectional Syncthing changes, conflicts,
biometrics and retained-vault recovery in its specific Pixel/external-storage
provider/Linux Desktop/Syncthing 2.0.x environment. It does not establish universal
support for Android devices, document providers, accessibility services or peers.
Historical reports apply to their recorded versions and environments.

## Create or join a vault

On a fresh installation, **Vaults → Create Local Vault** creates an independent
vault on this device. Enter an optional **Vault name**, a password and its
confirmation, then choose **Create Vault**. Local-only vaults work without network,
folder access or biometric enrollment. An empty password requires explicit
confirmation and provides no password protection.

To use an existing vault, first make its encrypted files available through an
Android document provider. Choose **Vaults → Join Existing Vault**, select the
folder containing the existing vault, let Totipo check it, and enter its existing
password. A successful Join opens a local copy and attempts Sync. An empty folder
cannot join or recover a vault. **Create Local Vault** makes a different vault,
even if you reuse the same name or password.

Names identify vaults only on this device; they are not shared cryptographic
identities. Names can repeat and contain at most 128 UTF-16 code units, without
control characters or malformed Unicode. Leave the name blank to use a generated
label such as **8F2A6C · Unnamed**. Join may suggest the folder's display name;
you can edit or clear it.

## Manage multiple vaults

**Vaults** lists remembered vaults under **Your vaults** and Create/Join under
**Add vault**. Tap a row to reach that vault's locked screen. With one established
vault and a valid last selection, startup goes directly to its locked screen;
with several vaults, choose one explicitly. **Last selected** is a hint, not an
unlocked session.

Use **More options → Change Vault** from a locked or unlocked vault to return to
Vaults. Switching clears forms, search and revealed codes and closes the current
vault before another can open. The next vault requires its own authentication.
A slow or stuck folder provider can delay switching; follow any retry notice,
and a process restart may be necessary.

The row's **⋮ → Rename** changes only its local name. **Rename Vault** is also
available in the selected vault's overflow menu. Each vault independently retains
its password, biometric setting and sync-folder association. Inactive vaults do
not synchronize.

### Forget and recover

From **Vaults**, choose the entry's **⋮ → Forget** and read the confirmation.
Forget removes it from normal selection, removes its biometric credential and
sync-folder association, and **keeps its encrypted local data**. It is not secure
erasure and does not delete files in the former shared folder.

Use **Vaults → Recovery → ⋮ → Recover** to unlock retained data with its password.
Recovery requires the private app data from this installation. After recovery,
configure Sync and biometric unlock again if wanted. **Retry Forget cleanup**
means cleanup did not finish; retry explicitly. Do not treat it as success.

Interrupted Create/Join may appear in Recovery. Follow the offered resume/open
action rather than creating a replacement over retained data. Missing files,
corrupt metadata, duplicate vault identities and uncertain storage writes need
explicit investigation; there is no general import, automatic repair or merge
of local vault roots. Preserve app data before recovery work. Clearing data or
uninstalling is not a recovery procedure.

## Add and use tokens

Unlock the intended vault before adding tokens. **Add token** accepts issuer,
account, Base32 setup secret, algorithm, code length and period. Match the
service's settings: SHA-1/SHA-256/SHA-512, 6/7/8 digits and a whole-second period
from 1 to 4294967295 are supported. Keep service recovery codes separately.

For enrollment from another app, open a supported `otpauth://totp` link or share
it as plain text with Totipo. Review the displayed details and confirm **Add**.
Link enrollment accepts 6- or 8-digit TOTP codes; 7-digit codes can be entered
manually. HOTP is unsupported. **Scan QR with camera** launches the external
camera app; there is no built-in QR scanner. Use a camera/scanner that can hand
off a supported link. External enrollment cannot select or unlock
a vault: unlock it first and repeat the handoff. Interrupted enrollment drafts
must be entered or shared again.

Tokens sort by issuer and account, with a stable order for ties. Search filters
the list. **Show code** reveals a code and its remaining lifetime; **Copy code**
copies it. When the revealed code expires it hides rather than remaining visible
indefinitely. Codes depend on the device's clock. Clipboard cleanup is conditional
and best effort; another app or the platform may already have read a copied code.

A live token's menu offers **Edit** and **Delete**. Edit changes issuer/account
labels while keeping the hidden authenticator setup; changing the setup is not
available. Delete asks for confirmation. It removes the token from the current
view while retaining encrypted historical revisions; it does not securely erase
its setup secret from all stored history.

Concurrent edits can show a conflict. **Resolve** asks you to choose one complete
alternative, including **Deleted** where offered. Totipo does not silently choose
a winner or combine fields. If the token changes while you are reviewing it,
review the current alternatives and choose again. Locking, leaving a form or
Activity interruption can discard unfinished changes.

## Passwords, biometrics and locking

Use the vault password on its locked screen. There is no password-change workflow.
An optional **Enable biometric unlock for this vault** checkbox on the password
screen enrolls after successful password unlock and explicit biometric authorization.
A configured vault may instead offer **Replace biometric unlock for this vault**.
Each vault enrolls independently.

Biometric enrollment stores that vault's **reusable password encrypted on this
device**, outside its vault files and shared folder. A separate non-exportable
Android Keystore AES-256-GCM key requires fresh strong biometric authorization for
each encryption/decryption. A device PIN is not a fallback for this biometric
operation. This is password convenience storage, not a passwordless vault format.

An eligible locked screen offers one automatic biometric prompt per lock episode.
Canceling or failing does not start a prompt loop. **Use biometrics** explicitly
retries, and password unlock remains available. **More options → Disable biometric
unlock** removes the selected vault's credential/key; retry if cleanup fails.
Invalidated keys or interrupted credential writes can require Disable followed by
new enrollment. Changes to enrolled biometrics or platform security may invalidate
several vaults' keys. Keep every vault password independently.

**Lock** closes the current vault immediately. Totipo also locks after **15 minutes
without user interaction**. Time in the background counts, but simply backgrounding
the app does not lock immediately. Rotation does not reset the deadline; Sync
and code updates do not count as user activity. Returning after expiry requires
unlocking before vault content is shown.

## Shared-folder synchronization

A sync folder is optional. Totipo keeps its working vault in private app storage
and reads/writes encrypted vault files in the folder selected with Android's
folder picker. A selected folder can be entirely local; configuration alone does
not mean any other device can see it.

### Configure a folder

For an existing shared vault, use **Join Existing Vault** as described above.
For a vault created on Android:

1. Unlock it and choose **More options → Manage sync folder**.
2. Choose **Choose sync folder**, select the intended folder, and grant access.
3. For a new empty folder, explicitly choose **Initialize sync folder**.
4. Choose **Sync** to send the local token changes to that folder.

Initialization sets up the vault in the folder; it does not by itself publish
all tokens. Selecting a folder does not initialize or replace a vault. A folder
already containing a vault must match the open vault; a different or ambiguous
identity blocks Sync. Totipo never replaces or repairs the vault identity file.
Use a separate folder for every independent vault and never direct another
writer at Totipo's private app storage.

For use with **Syncthing** or another file-replication tool, configure that tool
separately to replicate the selected folder's encrypted vault files to your other
device. The Android folder picker must expose the folder where those files
actually arrive. Totipo does not embed, configure or monitor Syncthing. On the
other device, join the same vault using its password and check that expected
tokens and changes appear after both replication and that device's Sync.
A connected peer alone does not establish delivery.

### What Sync means

While the selected vault is open, automatic attempts follow unlock/open, a
meaningful return to the foreground, and successful Add/Edit/Delete/Resolve.
Manual **Sync** retries. There is no periodic polling, background sync or
synchronization of inactive vaults. Failures do not start an endless retry loop.

Sync first reads changes available in the folder, then sends eligible local
changes. Conflicting edits require your explicit choice. Saving locally succeeds
independently of a later folder failure. Keep these events distinct: saving on
this device, writing the chosen folder, external replication, and another device
reading the replicated files. A successful Sync confirms only the chosen folder.

| Status | Meaning / action |
| --- | --- |
| Local only | No folder is configured; local saves still work. |
| Sync folder configured | An association exists; this is not a delivery result. |
| Folder access needed / Access needed. Choose folder again. | The association remains, but access must be restored. |
| Changes not synced | A local change remains saved; restore folder access and retry Sync. |
| Local changes published / No local changes to publish | Results against the configured folder only; neither confirms another device received them. |

Successful Sync is quiet on the main screen. Folder management and **Diagnostics**
retain operation details. Diagnostics includes public vault/folder/build information
and the installed app version, without passwords, setup secrets, root keys or raw
exception dumps.

### Troubleshooting and disconnecting

Use **Retry access** to check access again, or **Change sync folder** to reselect
the intended folder and restore permission. A rejected folder choice keeps the
previous association; a notice may remain until a successful configuration action
or explicit retry. If the folder belongs to another vault, choose the right one;
creating another local vault is not a substitute for Join.

If initialization partially succeeds, keep the files and retry explicitly. An
empty folder or token files without valid vault identity cannot substitute for a
complete existing vault. Do not delete identity files to bypass a refusal.

If another device misses a change, check Totipo's folder result, the replication
tool's actual file delivery, then the receiving app's Sync separately. Restore
provider access and use manual Sync after an outage. A local retry hint survives
restart, but is not proof of external delivery.

**Disconnect sync folder** removes the selected vault's association and returns it
to local-only use. Local data and files in the former folder remain. A failed
configuration write is not a successful disconnect. Some Android folder permissions
can remain while another vault or an unfinished provider operation still needs
them; deferred cleanup can be retried in folder management.

## Security and backup

Vaults use app-private no-backup storage and the manifest disables Android backup.
Do not rely on Android backup or device transfer to preserve vaults:
[manufacturer-specific transfer behavior varies](https://developer.android.com/guide/topics/manifest/application-element#allowbackup).
**App-data clearing, uninstalling or loss of the device can permanently destroy
local-only and forgotten vaults.** Recover works only while the retained private
data and password remain available.

Synchronization is not backup. Maintain independent encrypted copies of the
vault files, retain the password separately and verify you can open another usable
copy before any reset. Preserve the complete vault folder, including `VAULT` and
`objects-v1`; the password or identity file alone does not back up tokens. A folder association, a successful local save or a connected
replication peer is insufficient evidence. Biometric records are device-local
and do not travel with the vault; another installation needs its password.

Deleting tokens and forgetting vaults do not securely erase encrypted history.
Older revisions can remain in private storage, shared folders, replicas and
backups. There is no secure filesystem erasure or rollback resistance. Keystore
protection depends on Android's security model and cannot protect the password
after authorized decryption from a compromised app process. Password-buffer wiping
and clipboard cleanup are best effort, not guaranteed erasure or secrecy.

Older installations using the pre-multi-vault layout are not automatically migrated.
If startup reports an incompatible installation, use the previous app to preserve
or synchronize every local vault and verify another usable copy before any deliberate
reset. Totipo never clears the old data itself. Do not clear data to troubleshoot
corrupt metadata, missing files or interrupted recovery.

Implementation details are in [vault/Sync internals](ARCHITECTURE.md) and
[multi-vault architecture](../MULTI_VAULT.md). Build and local qualification
procedures are in [development](DEVELOPMENT.md); historical evidence remains in
[review](../review/).
