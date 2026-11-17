# Totipo Android user guide

Start with the [README](../README.md) for the download and a short introduction.
This guide describes the current app. Release procedures belong in the
[maintainer documentation](../release/README.md).

## Compatibility

**Requires Android 11 or newer (API 30+).** The installation minimum matches
the vault library's Java Flow runtime requirement; Android enforces it when
installing the APK.

The installation minimum is not full runtime qualification. Java 0.3.2 removes
the known newer-library API references. Scoped Android 17/local-provider tests
have passed; actual API 30 and remaining device qualification is pending. See the
[R4D qualification report](../review/BACKUP_RESTORE_R4D_ANDROID_FINAL_QUALIFICATION_REPORT.md).

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

**Vaults** lists remembered vaults under **Your vaults** and Create/Join/Restore under
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

### Back up an open vault

Unlock the intended vault, then choose **More options → Back up vault**. Totipo
prepares a portable encrypted ZIP, then opens Android's save picker. Choose a location
independent of the application and save the suggested neutral timestamped filename. This works
for local-only vaults with no Sync folder. No new backup password is requested:
the archive uses the vault's existing password, including an empty password if
that is how the vault was created. Keep that password separately; biometric unlock
does not replace it.

The ZIP preserves locally available encrypted history, including historical
revisions, deleted entries, conflicts and their metadata. It excludes biometric
credentials, Keystore keys, the registry and private application settings. It
represents the history available when prepared, not necessarily changes on other
devices or changes made while choosing the destination. Missing ancestry is
reported explicitly. Even without a reported gap, complete global history is not
established. The current shared implementation supports at most 4,096 objects,
8,192 observed directory entries and a 6 MiB ZIP; exceeding a limit fails rather
than silently exporting a prefix. Do not delete history to fit the limit.

**Backup saved.** means the provider returned the complete matching bytes after output closed. It does not prove durable remote storage or completed
cloud upload. **Backup saved, but couldn't be verified. Check the file before relying
on it.** means output closed but readback could not be opened; check the copy
independently. **Backup failed. The file may be incomplete.** means the file must not
be relied on. A partial or incorrect document may remain after a failure. Use **More options → Cancel backup** to cancel;
once output has started, cancellation may leave an empty, partial or complete file.
Totipo does not delete destination documents as cleanup.

Rotation can retain an in-memory picker request. Lock, inactivity expiry, vault
switching or process restart revoke it. Leaving the app during preparation/output
cancels further work; choosing a destination in the system picker is allowed, but
writing requires returning to the same authenticated vault. A stuck provider may
delay vault switching until its I/O actually returns or the process is restarted.
After process interruption, an interrupted-output notice asks you to inspect the
destination; nothing resumes automatically.

Keep independent copies in more than one safe location, confirm provider-specific
upload status separately, and retain service recovery codes. ZIP entry names and
sizes reveal some metadata; the archive does not add an outer encryption layer.
Android also provides new-local-vault ZIP restoration and Add History from Backup,
as described below. **Backup/restore is not generally qualified:**
actual API 30 and remaining device/provider testing is pending. Scoped physical
Android 17/local-provider results are recorded in the R4D qualification report.
Do not rely on this feature as the sole disaster-recovery path or reset an installation based only on host test results.

### Add History from Backup

With the current vault unlocked, choose **More options → Add History from Backup**
and select a Totipo backup ZIP in the system picker. Totipo verifies the complete
backup using the authenticated vault, then asks **Add missing history to this vault?
Existing data will be kept, but older versions or conflicts may appear.** Choose
**Cancel** or **Add History**. No additional password is needed.

These operations have different purposes:

- **Back up vault:** create an independent encrypted copy of locally available history.
- **Restore from backup:** reconstruct the vault as a new local allocation.
- **Add History from Backup:** add missing historical evidence to the current vault.

Add History is not a rollback to the backup date. It keeps newer objects and all
existing data. Added history may change the current visible token state, expose
older or deleted alternatives, or reveal conflicts. Review conflicts through the
ordinary Resolve controls; importing never automatically reveals or copies codes.
A backup belonging to another vault is refused.

**History added.** and **No new history to add.** appear in the usual status area.
A partial or uncertain import keeps any objects already stored. Keep the original
ZIP and the app’s data; unlock this vault and select the original ZIP again to
retry explicitly. Nothing retries automatically. After Lock, switching vaults or
process restart, select and verify the ZIP again. Leaving the app during checking,
confirmation or application cancels future work; entered storage work may finish.
The system picker itself is allowed to background Totipo.

Local-only vaults stay local-only. For a vault with Sync configured, import excludes
Sync while active. Only a fully acknowledged import followed by ordinary successful
local observation makes imported evidence eligible for the existing Sync workflow.
Interrupted, partial, uncertain or unobserved application blocks outbound Sync,
including after restart or changing folders. Retry the original backup to complete
recovery. If storage or observation still needs attention, preserve the evidence
and investigate it; do not clear app data or uninstall. Completion confirms local
history, not delivery to another device.

### Restore from a portable backup

On a fresh installation after reinstalling or moving devices, use the independently
stored ZIP and its original vault password. On an existing installation, finish
**Change Vault** to return to Vaults, then choose **Add vault → Restore from
backup → Choose backup ZIP**. If a provider mislabels ZIPs, use **Browse all files**.
Totipo reads and validates the complete selected ZIP before asking for its original
vault password. The provider document is frozen in private memory; changing it later
does not change the candidate. Canceling before submitting **Restore** creates no new vault.
Wrong passwords, corrupt archives or invalid encrypted objects cannot authorize
installation. Authentication failure does not prove the password is wrong.

Enter **Original vault password** and, if wanted, **Vault name (optional)** on the
same screen, then choose **Restore**. A name changes only the local label. Totipo
checks the complete encrypted archive and existing vaults before installation.
If authenticated history has gaps, **Some history is missing** explains:
“This backup is missing some earlier history. Restoring it won't recover that history.”
Choose **Cancel** or **Restore anyway**. Ordinary backups need no separate confirmation.

Restore keeps the original vault identity, password, encrypted historical assertions,
tombstones and conflicts. It creates a new private allocation, with no Sync association,
grants, publication settings or biometric credentials. No Sync configuration or network
permission is required. A document provider may itself retrieve a ZIP from cloud storage.

When installation finishes in the same foreground journey, Totipo continues to the
normal locked vault screen. Enter the original password again through ordinary
**Unlock** to finish checking the installed vault. The password submitted to **Restore**
is wiped and never reused for unlock. After successful opening, a short non-modal
**Vault restored.** status appears. Biometric convenience can be enabled later through
the normal workflow. Until installation and this authentication finish, the vault
remains in **Recovery**, rather than Your vaults. **Finish restoring** opens an already
installed backup for this last step. If canceled, backgrounded, expired or restarted,
the restore journey does not automatically open or retry; use Recovery explicitly.

If the authenticated identity is already retained, a second copy is refused.
An established match offers **Open existing vault** without adding history. A
forgotten match belongs to **Recover retained vault**; a Forget in progress needs
its cleanup retry. Missing, unreadable or ambiguous retained identity evidence must
be investigated before a new allocation can be authorized. No existing root is
deleted, overwritten or replaced to make room.

### Interrupted ZIP restoration

Cancellation, backgrounding outside the picker, a fixed 15-minute journey deadline,
provider failure or process termination may leave a reserved or partly installed
allocation. Rotation preserves the in-memory journey and picker identity but clears
password widgets; process restart does not resume work. Entered blocking I/O must
retire before another journey or vault can take ownership. The deadline does not
reset on provider callbacks or user interaction. It is stricter than normal vault
inactivity policy.

**PARTIAL** means bootstrap acknowledgement with incomplete history; **UNCERTAIN**
means persistence acknowledgement is missing. **REJECTED** can still leave an owned
allocation. None means completed restoration. Even opening a bootstrap cannot
promote a partial restore. Existing canonical objects are always retained.

Use **Vaults → Recovery → ⋮ → Resume restore from backup**, reselect the **exact
original ZIP bytes**, enter its original password and choose **Restore**. Retry has no
optional name field; the existing local name stays unchanged. Retry uses the original
allocation,
its durable Java intent and recorded archive digest, and acknowledges exact objects
without replacing them. A repacked or different ZIP is refused. This action is also
available after installation if subsequent normal opening detects lost data.
A missing root after installation was marked attempted, missing/torn/mismatching
provider intent, occupied incompatible objects, or an interrupted registry temporary require explicit storage investigation; Totipo
preserves them and does not automatically repair or erase them.

**Recover retained vault** uses encrypted private data already on this installation.
**Resume restore from backup** replays an authenticated portable ZIP into its owned
restore allocation. Neither can recover app-private data removed by clearing data
or uninstalling. When restore is unfinished, **keep the app’s data and original
backup**, as well as the password, until recovery is verified. Do not clear app data or uninstall to try
to fix an interrupted restore.

### Private storage and recovery limits

Vaults use app-private no-backup storage and the manifest disables Android backup.
Do not rely on Android backup or device transfer to preserve vaults:
[manufacturer-specific transfer behavior varies](https://developer.android.com/guide/topics/manifest/application-element#allowbackup).
**App-data clearing, uninstalling or loss of the device can permanently destroy
local-only and forgotten vaults.** Recover works only while the retained private
data and password remain available.

Synchronization is not backup. Maintain independent encrypted copies of the
vault files, retain the password separately and verify you can open another usable
copy before any reset. Preserve the complete vault folder, including `vault` and
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
