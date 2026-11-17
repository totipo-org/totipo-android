# Android vault, Sync and credential internals

This technical reference retains the implementation detail moved out of the README.
For app workflows, use the [user guide](USER_GUIDE.md); for selection and registry
state transitions, see [multi-vault architecture](../MULTI_VAULT.md). Historical
reports remain evidence for their recorded revisions and environments.

Current source version is recorded in `VERSION`; Android versionCode is recorded
in `VERSION_CODE`. Totipo creates, unlocks and explicitly locks an
app-private local vault in no-backup storage. After setup, the main screen contains
**Sync**, search, tokens and **Add token**, with **Lock**, **Manage sync folder** and
**Diagnostics**, **Rename Vault** and **Change Vault** in overflow. The selected
vault name is the main heading. Tokens sort by issuer, account and stable token ID;
missing issuers sort by account, and conflicts retain neutral deterministic ordering
without choosing an alternative. Search preserves this order. Show code and Copy code retain their conceal/expiry
behavior. Diagnostics shows public vault/provider/build information, never passwords,
setup secrets, root keys or raw exception dumps. The App version comes from the
installed package metadata (version name and version code), with an honest unavailable
value if Android cannot supply it.

Vaults work fully **local only**. Create, password unlock, Add, Edit, Delete, Resolve,
local observation, Lock/reopen and restart use the coordinated private canonical
store without a storage provider, SAF grant or network. The neutral **Local only**
status does not mark successful local saves as unsynced or create transport retry hints.
Synchronization is optional and belongs independently to each local registry entry.

Once a shared folder is bound, Totipo automatically attempts **Sync** after successful
unlock/open, after a meaningful return from the background, and after successful
**Add**, **Edit**, **Delete** or **Resolve**. Rotation and repeated active callbacks do
not trigger repeated attempts. Manual **Sync** retries at any time admission is free,
including after a provider outage. There is no routine Import/Publish decision.

Sync always **imports and observes before publishing**: it checks the bound folder's
immutable VAULT identity, imports remote evidence through the existing Java session,
waits for Java observation, and then uses fresh publication preflight/readback/postflight
checks. Blocking identity, provider, import or observation failures stop publication.
Java remains the merge/conflict authority. Real concurrent edits remain conflicts;
Resolve requires an explicit complete Alternative, including Deleted where present.
There is no automatic winner or field combination.

Local authoring succeeds independently of the subsequent Sync attempt. For a configured
binding, an unavailable provider leaves the valid local change committed and shows **Changes not synced**.
A persisted local retry hint survives restart; immutable local evidence is the source
of publication decisions. A later foreground event, mutation or manual Sync retries.
Requests coalesce to one active Sync plus one pending request; failures never start a
retry loop. Successful Sync is quiet and confirms only the bound shared folder, never
receipt by other devices. External replication between devices is managed separately.

Edit changes issuer/account while retaining the hidden authenticator setup; **Change
setup** is deferred. Delete requires confirmation and authors a Totipo tombstone,
retaining immutable encrypted history rather than securely erasing it. Live rows offer
Show code and Edit/Delete; conflict rows offer Resolve. Detailed **Combine** is deferred.
Changed token/conflict bases require review and a fresh user choice, with no silent
retry/rebase. Lock, dismissal and Activity interruption retire pending forms; sensitive
setup is never saved through Android widget state or Bundle.

One Application-owned `AndroidVaultController` retains the live coordinator/session
across Activity recreation. The existing bounded vault worker and provider I/O lane
serialize operations, with session/binding retirement and cancellation guards. Explicit
Lock and a fixed **15-minute user inactivity timeout** close the real Java VaultSession
and retire its in-memory vault/session keys. Touch/key interaction resets a monotonic
`elapsedRealtime` deadline; Sync, TOTP ticks and provider callbacks do not. Backgrounding
alone does not immediately lock, but background time counts. Foreground re-entry checks
expiration before presenting vault content. Rotation does not reset the deadline.

Biometric unlock is optional and belongs independently to the selected local vault.
On an eligible locked-screen entry, an enrolled available credential automatically
offers one strong-biometric prompt. Cancellation, non-match, errors, rotation and
routine foreground transitions do not restart it; **Use biometrics** explicitly
retries. A successful open rearms the next lock episode. No prompt runs in Vaults.
Password unlock remains available. After password unlock, explicit opt-in authorizes
an auth-per-use strong-biometric AES-GCM operation storing that entry's reusable
password in private no-backup storage. Every biometric unlock requires a fresh
framework CryptoObject authorization, then uses the normal password-open path.
The authenticated session's exact VaultId must match the credential before OPEN,
token observation or automatic Sync. See [per-vault biometrics](#per-vault-biometric-credentials)
and the [A-MV4 report](../review/A_MV4_PER_VAULT_BIOMETRICS_REPORT.md) for qualification status.
Passwords remain mutable caller-owned buffers and are wiped on completion as best
effort rather than guaranteed JVM erasure. Empty-password Create/unlock/Join requires
explicit confirmation.

Production consumes released Java NIO/core 0.3.2 and Bouncy Castle 1.86 directly
from Maven Central, targeting Totipo Vault Format v1/r19. One app-private local canonical
store has one persistent `NioStoreComposition.coordinatedDelegate(root, new NioDurability())`
wrapped by `CoordinatedPrivateStore`, whose fair gate owns whole-root exclusivity for
session calls and bridge batches. No second delegate or independent writer is allowed.

**Sync** exchanges immutable token objects through one bounded provider I/O lane.
The lower-level Import and Publish operations remain internal for qualification. Every operation first reads the provider's root
immutable VAULT and compares its structural `VaultId` with the currently open session.
Both phases require the selected folder to contain the same immutable VAULT. Missing, malformed,
unavailable, different or duplicate VAULT candidates block object mutation; duplicate
candidates are conservatively ambiguous even if identical. No password/KDF is used for
provider recognition. Folder READY means transport accessibility, not matching identity.
Only explicit Initialize may create an absent provider VAULT; only explicit Join may
enroll an authenticated provider VAULT locally. Totipo never replaces or repairs VAULT. VAULT is immutable;
password change and migration are absent. Cross-vault migration is separate future work.

Inbound import retains the same authenticated session, Java object validation and exact
ciphertext, exclusive local publication, then refresh after releasing the store gate.
Outbound publication retains bounded fresh preflight, create-only canonical names,
immediate read-back and fresh authenticated postflight. Manual Sync remains the retry fallback.
There is no automatic polling, background synchronization, cloud SDK or Internet permission.
Java's existing-token-data creation veto is presented without bypass.

Manual enrollment, otpauth enrollment, reveal/copy, vault shell, explicit lock and token
list behavior remain. Empty-password create/unlock/Join requires
confirmation. Backgrounding alone does not lock; elapsed inactivity does.
Requires Android 11 or newer (API 30+): the APK minimum SDK enforces the Java Flow
runtime requirement for vault operations.

The launcher uses canonical adaptive/round/density resources copied unchanged from
`totipo-spec/design/icons` at r19 commit `cdb4e91be1c6d3704874b2b92457ffe7be5e9084`.
Master: `design/icons/totipo-app-icon.svg`. Approved artwork gradient colors are
`#46FB70`, `#08D267`, `#028B55`, `#026344`; no UI palette changes are implied.
[Branding provenance](../branding-provenance.json) records every copied source path/hash.
No monochrome variant is supplied; none is invented. No icon-generation dependency is added.

See [Java provenance](../TOTIPO_JAVA_DEPENDENCY.md) and the
[0.2.0/r19 reconciliation report](../review/JAVA_0_2_0_R19_ANDROID_RECONCILIATION_REPORT.md)
for historical evidence; [R4A](../review/BACKUP_RESTORE_R4A_ANDROID_EXPORT_REPORT.md) tracks the 0.3.1 export integration. Historical M1/M3 reports remain unchanged.


## Limitations

M3C adds two explicit same-vault bootstrap actions. **Join existing vault** authenticates
an exact provider immutable VAULT through Java before installing those same bytes into
an empty local canonical store, then opens normally. Wrong passwords leave local VAULT
absent. A fresh provider recheck precedes installation; local orphan token evidence blocks it.
**Initialize sync folder** publishes the exact open local canonical VAULT create-only,
verifies readback and fresh provider evidence, and establishes the exact objects-v1 directory.
Provider token evidence without a VAULT vetoes initialization. A verified VAULT is retained
if directory initialization fails; an explicit retry can create only the missing directory.
Neither operation replaces VAULT, changes a password or root, performs migration or copies
across vaults. Different local/provider VaultIds are never reconciled automatically.
Join and Initialize remain deliberate setup operations under **Manage sync folder**;
Join is also reachable during initial setup. Join opens the vault and triggers ordinary
Sync. Initialize creates the provider VAULT/directory only; ordinary **Sync** then handles
token publication. No ordinary Sync auto-joins, bootstraps or replaces a vault.
A different provider VaultId blocks publication with a fixed message; use **Manage sync
folder** to choose the intended folder. No replacement or merge is offered.
No bootstrap runs on startup, unlock, folder selection or access restoration.
Historical M3D reports and checkpoints record the earlier explicit Import/Publish UX
and remain unchanged. The daily-driver milestone's historical validation and its then-outstanding
human gates are recorded in [the daily-driver UX report](../review/ANDROID_DAILY_DRIVER_UX_REPORT.md).
There is no background sync or embedded external replication service,
field-by-field conflict combination, DI, AndroidX, Compose, service or WorkManager dependency. Universal Android filesystem/runtime or interoperability qualification is not claimed.
Production release signing is documented in [Android releases](../release/README.md).
JVM/build evidence complements the isolated physical-device qualification recorded in the report.


## A-MV1 local vault storage

New installations use a local-only versioned registry in
`noBackupFilesDir/totipo-local-v1/registry`. Every vault has a generated opaque
local ID and its own `roots/<local-id>/store` beneath that directory. Local IDs
are independent of protocol VaultIds; registry metadata never enters a protocol
store or Sync folder. A durable pending reservation precedes allocation and a
durable creation-attempt marker precedes Create/Join. Authentication promotes an
entry to established. Uncertain attempts, missing established data, unsafe paths,
and corrupt metadata require explicit recovery; the app never replaces them.
Preserve the entire application data before attempting recovery. There is no
in-app metadata repair or automatic cleanup.

This version targets **fresh application data**. An old `totipo-vault` root,
global Sync preferences/retry hints, or global biometric credential/key blocks
startup with an incompatible-installation diagnostic. No legacy adoption or
migration is supported, and no old files, grants or keys are changed. Before a
manual application-data reset, use the previous application to preserve or
synchronize every local vault and verify the other copy. Resetting permanently
loses unsynchronized local vaults. The app never clears its own data.

A-MV2 keeps one Application selection authority and an immutable activation/root
binding for each controller. Ordinary Lock closes secrets/session and retains that
selection, including the existing 15-minute inactivity and background policy.
Internal complete retirement permanently rejects new commands, clears sensitive
presentation, finishes admitted local work, closes the Java session/facade/store
and lease, and drains entered provider work and completion handoffs before releasing
selection. Only then can the next controller be constructed. A failed close retains
the existing resources for explicit retry. A blocked provider retains selection in
a neutral waiting state even after the local session closes; arbitrary SAF/Binder
I/O cannot be proved cancelled by interruption, Future cancellation, executor
shutdown or a timeout. Process restart may be required. Application shutdown remains
best effort and never releases selection on the strength of executor shutdown.

Activities observe selection and recreate their presentation for a new activation;
old controller, observer, enrollment and picker callbacks retain their originating
activation. Only the non-secret picker activation is retained in memory across configuration
recreation; process death drops it and rejects the outstanding result.
The registry supports multiple remembered entries through the A-MV5 selection
surface described in [multi-vault architecture](../MULTI_VAULT.md). Optional biometric credentials are scoped to the selected
entry; global credentials are never reused. Inactive vaults never synchronize.

The debug disposable-create probe remains disabled while the Application holds
selection. Use the isolated registry qualification harness for synthetic creation;
a diagnostic must not admit a second session beside the selected controller.


## A-MV3 optional per-vault Sync

Each immutable controller receives its entry's `AndroidSyncFolderPort`. The existing
`sync_binding_mv1_<local-id>` namespace owns URI, remembered read/write metadata,
retry hint and binding-update recovery/permission-cleanup records. The registry
contains no second copy. Binding generations, request ownership, session generations,
Sync status and last result belong to that activation; callbacks never look up a
replacement controller. Inactive entries are inspected only for configuration metadata.

**Manage sync folder → Choose folder** attaches synchronization to an existing local
vault without changing its cryptographic identity. Use **Initialize sync folder** only
for an intended new provider replica; ordinary Sync requires matching provider VAULT
and performs fresh import, Java observation and publication assessment regardless of
retry hints. A different or ambiguous provider VAULT blocks publication. **Join** still
authenticates and enrolls the provider's exact VAULT; choosing a folder cannot replace Join.

**Disconnect** durably removes only that entry's binding and transport hint, preserving
all private and provider data. It cancels future work logically; an entered provider call
retains its grant until it actually returns. Local-only status follows the durable
configuration change. Rebinding performs a fresh complete Sync assessment. Failed or
interrupted configuration writes preserve the previous binding in recovery metadata;
choose the intended folder again or retry Disconnect. No successful disconnection is
reported for a failed preference write.

A remembered URI with missing/revoked SAF permission stays configured and shows
**Access needed. Choose folder again.** It is distinct from intentional local-only mode.
Local saves remain available after a failed transport attempt and retain their scoped
retry hint. The existing bounded active-Sync admission policy remains unchanged.

The Application serializes binding changes with SAF authority admission and cleanup.
Cleanup freshly validates the entire registry and examines every scoped binding,
recovery record and admitted provider use without constructing inactive controllers.
Obvious same-folder references, including equivalent decoded tree identifiers, are
refused during folder selection. Historical duplicate references still retain access.
Grant release is deliberately conservative: any remembered binding, active use,
uncertain inventory/write or different persisted platform grant prevents revocation.
Textually different SAF trees are not assumed independent. Cleanup records remain
visible in folder management when release fails or is deferred. Some unused grants
may therefore remain until explicit recovery; no broad startup cleanup is performed.

A-MV2's complete retirement and single-active-session guarantees remain in force.
A-MV5 exposes public selection and management while retaining these per-entry boundaries.
See the [A-MV3 report](../review/A_MV3_PER_VAULT_SYNC_REPORT.md) for qualification evidence
and conservative cleanup limitations.


## Per-vault biometric credentials

A local entry ID is the registry's immutable generated 32-character lowercase hex
storage identity. It differs from the protocol VaultId, which identifies the exact
canonical VAULT and may be equal across separate local replicas. Neither labels,
SAF URIs nor record-supplied metadata choose keys or paths. Schema-v2 aliases are
`org.totipo.biometric-password.v2.<local-id>`. Each entry has its own non-exportable
AndroidKeyStore AES-256 key: GCM/NoPadding, randomized encryption, authentication
required for each operation, strong biometrics only, and biometric-enrollment
invalidation. No device-credential fallback, cached authorization or shared key exists.

Records live at `noBackupFilesDir/totipo-local-v1/roots/<local-id>/biometric-v2`,
beside `store/`, never inside VAULT, objects-v1 or a synchronized folder. The bounded
binary envelope contains version, exact local ID, authenticated session VaultId,
expected alias, 12-byte nonce and ciphertext with a 128-bit tag. Canonical
length-prefixed AAD binds a fixed application context, version, derived alias,
local ID and VaultId. Strict parsing rejects unexpected identity/alias/version,
invalid encodings, truncation, oversize and trailing bytes. Path validation checks
every ancestor without following symlinks. Registry metadata contains no password.

Enrollment follows explicit password authentication, captures the entry, activation,
session generation, actual session VaultId and owned password buffer, and rechecks
those values at the durable write boundary. Replacement uses the same entry's key
with a fresh operation and nonce; cancellation preserves the prior working record.
Writes force a create-new temporary file, atomically replace the record, force the
parent and check exact readback. A durable `.pending` marker blocks use until success.
An interrupted or failed replacement conservatively requires explicit Disable and
reenrollment, even if old bytes remain; no failure is reported as successful enrollment.
An unused key after canceled initial enrollment is bounded to the entry's one alias
and may be reused by a later explicit enrollment or removed by Disable.

Unlock callbacks retain their original controller and request. Lock, controller
retirement, Activity pause/stop, background transitions, cancellation and the bounded
prompt deadline revoke the relevant ownership. If a callback has already submitted
password-open work, cancellation is rechecked after authentication and after the
existing canonical read, before subscription or OPEN exposure. A VaultId mismatch
closes the candidate through the existing lifecycle, preserves uncertain store
and failed-close evidence, and never reassigns the credential or registry identity.
Normal rotation preserves an already authenticated session, never prompt authority
or decrypted passwords. Process restart requires a new biometric operation.
The 15-minute inactivity policy is unchanged.

The locked password form offers enrollment or replacement; the open selected vault's
overflow offers **Disable biometric unlock**, also for incomplete cleanup. Disable
revokes the Activity request and enrollment ownership, durably marks `.disabled`,
removes that entry's record/temporary metadata and its own key, and checks completion.
Partial cleanup reports failure, keeps a blocker and can be retried. A-MV5 has a
small Application cleanup hook that refuses a still-selected or incompletely retired
entry; it never deletes the canonical encrypted store. A-MV5 uses it during recoverable Forget.

Missing/malformed/incompatible records, missing or invalidated keys and tag failures
leave password authentication available. Temporary hardware unavailability, lockout,
non-match or prompt cancellation never automatically delete credentials. Explicit
reenrollment can replace malformed ciphertext with a usable existing key; missing
or invalidated keys and interrupted persistence require Disable first. No key is
silently regenerated beside an old record. A failure to validate private storage
uses an unavailable adapter that cannot claim successful deletion.

Local-only and synchronized entries use the same credential implementation. Provider
attachment/disconnection, inaccessible folders and Sync retries never change key or
record ownership. There is no inactive-vault authentication or background Sync.
Legacy global files/aliases remain incompatible-installation evidence: no adoption,
decryption, migration or automatic deletion is performed.

Keystore non-exportability and auth-per-use protect device-local key use under the
platform's security model; they do not protect a reusable password after authorized
decryption from a compromised application process. JVM buffer wiping is best effort.
Application-directed cleanup of A never touches B, but platform security changes,
including changes to enrolled biometrics, may invalidate several keys. There is no
rollback resistance, secure filesystem erasure or guarantee against a compromised
app UID concurrently replacing private files. Milestone-specific physical qualification status and
then-remaining hardware scenarios are recorded explicitly in the A-MV4 report.
See the integrated evidence below for the later A-MV6 result.



## Integrated qualification evidence

The [A-MV6 qualification report](../review/A_MV6_INTEGRATED_MULTI_VAULT_QUALIFICATION_REPORT.md)
separates current integrated results from A-MV1–5 and historical M3D evidence.
Physical desktop-created Join and bidirectional Add/update/delete through real
Syncthing now have captured evidence, including conflict resolution and independent
local-only and synchronized identities. Final corrected-APK Join, bidirectional
delivery, hardware biometrics, retained
recovery and focused UI checks pass. The human Nix gate passed on frozen inputs;
A-MV6 qualification is complete for the documented environment and ready for a
subsequent release decision. This does not authorize publishing a release.
Its tested environment and documented limitations govern interoperability claims;
connected peers or a configured folder are insufficient evidence. Arbitrary SAF
providers, OEMs and accessibility services are not universally qualified. The
current native Activity theme is explicitly light; a dark system setting does
not imply a dedicated dark application theme.

## Explicit encrypted ZIP export

The open selected vault offers **Back up vault**. Controller admission reserves the
vault worker during shared Java authenticated capture, excluding local saves and
Sync bridge publication. `CoordinatedPrivateStore.SessionView` forwards the bounded
SPI enumeration without invoking its unbounded predecessor. Capture enters Java's
provider gate before the private store gate, never under a bridge. No second root
owner/delegate or Sync inventory is used. Java owns encrypted-object validation,
complete supported-history membership, exact canonical bytes, capacity policy,
ancestry diagnostics and the portable ZIP writer.

`BackupExport` freezes the complete shared ZIP in at most 6 MiB of private memory,
checks its digest against Java's summary and releases the capture before document
selection. No archive staging files, plaintext, password or key enters task state.
The picker uses ACTION_CREATE_DOCUMENT, CATEGORY_OPENABLE and application/zip with
operation-scoped read/write permission; there is no persistent grant, broad storage
permission, Internet permission or new manifest component. Provider output uses the
existing bounded provider lane, independently of Sync configuration. Local UI and
local authoring can continue after capture; they cannot regenerate the frozen ZIP.

The controller binds the in-memory operation to its activation and authenticated
session generation. A unique process-local picker request code is consumed once;
nonconfiguration retention can carry its originating operation across rotation.
No Bundle/persistent picker authority exists. Output is admitted only after resume,
foreground/inactivity checks and matching controller/session. Duplicate/late results
never retarget a new operation. Lock, retirement, inactivity and backgrounding during
preparation/output revoke further work. System-picker waiting is exempt from ordinary
background cancellation but authorizes no I/O. The session can close while detached
output drains; complete selection retirement still waits for real provider return.
Cancellation never interrupts canonical publication or treats a timeout as drain.

Before provider open, `BackupExportJournal` forces a small fixed interrupted-output
marker in no-backup storage, outside all canonical roots. It contains no URI,
identity, password, key, token data or resume authority. Restart shows an uncertain
output notice and never resumes. Normal completion removes only this recognized
private marker. A failed marker acknowledgement refuses external output. Failed
completion cleanup retains a warning. No destination deletion/rollback is attempted.

Output, flush and close must all succeed before readback. Readback hashes every byte
through EOF and requires exact prepared length and SHA-256. An unavailable readback
open yields written/unverified; a failed read/close, mismatch or output failure yields
failed/could-not-verify. Cancellation distinguishes no admitted output from uncertain
output effects. Verified means matching provider-returned bytes, not durable cloud
upload or remote receipt. A blocked SAF call retains the lane and may delay switching
until actual return or process restart. Physical provider qualification is separate
from JVM fault coverage. ZIP restore uses the lifecycle below; additive history import
uses the selected authenticated session as described below.

## New-local-vault ZIP restore

`BackupRestoreFlow` separates picker, bounded frozen ingress/structural inspection,
password authentication, confirmation, installation and retired result phases.
`BackupArchives.prepareRestore` authenticates every object before a durable
allocation is reserved. It uses released NIO/core 0.3.2 and frozen profile 1; Android
implements no ZIP codec, extraction, canonical object copier or cryptography.
All passwords remain caller-owned mutable buffers, wiped after authentication;
no URI, password, root or session enters registry metadata, Bundle or logs.

The Application metadata worker performs blocking ingress, KDF, installation and
plan closure. Its management admission remains occupied until the main-thread
completion handoff. Picker requests use unique process-local codes and retain
only their originating operation across recreation. Backgrounding outside the
picker and a fixed 15-minute lifetime cancel further admission. Neither cancellation
nor a late callback grants another selection access to still-entered work.

The [registry/restore state model](../MULTI_VAULT.md#portable-zip-restore-admission)
records durable intent before shared provider writes. `CoordinatedPrivateStore`
wraps `NioStoreComposition.reserveRestore/recoverRestore` in the existing whole-root
gate, forwarding bounded scans and restore-specific location/bootstrap barriers.
Installation has no session; the plan and its sole persistent delegate close before
normal controller opening. No session operation runs under a bridge or Application
monitor. COMPLETE/PARTIAL/UNCERTAIN/REJECTED receipts remain distinct. Only COMPLETE
can advance to INSTALLED, and normal authenticated observation plus full installed
inventory authentication must precede ESTABLISHED. Sync settings and biometric
credentials are never copied. Java 0.3.2 removes the known newer-library API
references; actual API 30/current-device and SAF qualification remains pending.
See [R4D](../review/BACKUP_RESTORE_R4D_ANDROID_FINAL_QUALIFICATION_REPORT.md);
JVM and static evidence do not establish Android execution.


## Additive history from backup

The selected controller reserves archive admission across SAF selection, Java
preparation, confirmation and apply. One `HistoryImport` captures the controller
and session generation; ordinary state emissions do not retire it. The existing
worker owns ingress, `prepareImport`, the `BackupImport` handle, `apply` and close.
The session provider gate precedes `CoordinatedPrivateStore`'s fair whole-root gate;
no session or handle call runs under a bridge. Presentation projection coalesces
while archive admission is reserved. Token heads and conflicts always come from
ordinary Java observation. No import result patches token rows or reveals codes.

Before apply, the per-entry private preferences durably record the authenticated
archive digest independently of folder settings. A partial/uncertain/unfinished
import blocks every outbound Sync admission across Lock, restart and folder changes.
Only fresh authentication of that original ZIP, Java COMPLETE/NO_OP and the existing
two-pass diagnostic-free observation check can clear the marker. The digest is
recognition evidence, never authority; Java authenticates every new plan. Marker
write failure prevents application; clearing failure retains the block. There is
no automatic retry, ignored marker or provider-event completion shortcut.

A successful import uses `localMutationSaved` after observation, preserving normal
retry hints and import-before-publish Sync. Local-only imports acquire no tree
grant and start no transport. Lock, backgrounding outside the picker and retirement
revoke future ingress/publication. A scoped cancellation check in the coordinated
session facade refuses future object publications with a definite no-write result;
entered filesystem calls finish normally and Java accounts for their receipts.
All terminal plans close on the worker, after entered work, without rollback.
