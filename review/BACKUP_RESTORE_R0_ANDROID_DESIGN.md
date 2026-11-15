# BACKUP_RESTORE_R0_ANDROID_DESIGN

Status: **research/design complete; implementation not started**. Decisions below
are proposed Android product policy and shared API requirements, not claims that
these operations already exist. Research date: 2026-10-26.

## 1. Scope and source evidence

Classification recorded before editing: **DOCS_ONLY**. Started on clean `main`:

```text
git branch --show-current: main
git rev-parse HEAD: f0ce51ab1c159dd366d25032d55064a775eb39a0
git status --short: <empty>
```

Only this report is intended to change. No production, test, build, dependency,
harness, user-guide or release changes are part of R0. All research and writes
were performed from `totipo-android`; remote repositories were read over HTTPS,
not checked out or modified. No Nix, Gradle, device operation, commit, tag or
release was performed or requested.

Read [AGENTS.md](../AGENTS.md), [README](../README.md),
[user guide](../docs/USER_GUIDE.md), [MULTI_VAULT](../MULTI_VAULT.md),
[architecture](../docs/ARCHITECTURE.md),
[development environment](../docs/DEVELOPMENT.md#managed-development-environment),
[Java provenance](../TOTIPO_JAVA_DEPENDENCY.md), package/flake source filters and
CI. Implementation inspection covered registry, owner/selection, Application,
controller, private-store coordinator, detached authentication, candidate import,
SAF traversal/read/write, grant ownership, password and biometric boundaries.
Qualification evidence consulted includes
[A-MV1 storage](A_MV1_CLEAN_MULTI_VAULT_STORAGE_REPORT.md),
[A-MV2 retirement](A_MV2_CONTROLLER_RETIREMENT_REPORT.md),
[A-MV3 Sync](A_MV3_PER_VAULT_SYNC_REPORT.md),
[M3A SAF](M3A_SAF_BINDING_INBOUND_SYNC_REPORT.md),
[A-MV6 integration](A_MV6_INTEGRATED_MULTI_VAULT_QUALIFICATION_REPORT.md), and
[guidance/observation follow-up](ANDROID_JAVA_GUIDANCE_PIN_REFRESH_REPORT.md).
Relevant existing JVM suites and `tools/device/` harnesses were inventoried; their
historical passes are evidence for existing subsystems, not ZIP qualification.

Read the exact reviewed Java
[API_DESIGN at f0a028676c1801a10b2d8d2650bf9c24357d1fa1](https://github.com/totipo-org/totipo-java/blob/f0a028676c1801a10b2d8d2650bf9c24357d1fa1/API_DESIGN.md),
including operation classes, replay-latest and observation ordering, deterministic
editing, merge freshness, persistence handles and blocking/close. Runtime remains
Java 0.2.0, released source `d6310c177ae930df188fd4f5798622c935698b2e`.
The reviewed format is
[v1/r19 at cdb4e91be1c6d3704874b2b92457ffe7be5e9084](https://github.com/totipo-org/totipo-spec/blob/cdb4e91be1c6d3704874b2b92457ffe7be5e9084/spec/totipo-vault-format-v1.md).
This specifies the encrypted store, not a portable ZIP backup profile.

Read-only recursive tree inventories of released Java and the pinned spec found
no backup/archive API or archive profile. Discovery also checked Java `main`
`8f3adc0b19751cd3e9f6094644708a3f6869ff7b` and Desktop `main`
`175dc90c93f568b73ecc9ff95a1d1974c0cdd088`: neither tree had a
backup/restore/archive-named path. This is bounded discovery, not proof that no
external proposal exists. Newer main was not substituted for reviewed guidance.
A shared contract must be located or agreed before any Android ZIP writer ships.

## 2. Product decisions

| Operation | Entry point | Result | Existing data |
| --- | --- | --- | --- |
| **Back up vault** | Open selected vault → More options | Independent encrypted ZIP in a user-selected document | Read only; no Sync required |
| **Restore from backup** | Vaults → Add vault | New local registry entry containing the backup's existing protocol identity/history | Never overwrites another root |
| **Add missing history** | Open selected vault → More options | Additive import of compatible immutable history | Preserves every existing object and canonical identity |

A backup is a portable copy of the encrypted vault history, including historical
revisions, tombstones, conflicts and exact represented metadata. It is not a list
of current tokens, an otpauth export, or an Android installation snapshot. Restoring
creates a new **local allocation**, not a new cryptographic vault: canonical
identity, root, password and immutable object bytes remain those of the backup.
Do not call Java Create to implement Restore.

Local-only vaults must have all three operations without a Sync binding, tree
permission, provider scan, initialization, transport retry hint or Syncthing.
The document provider used for the ZIP is independent of optional Sync. Choosing
a cloud-backed document location can involve that provider's network activity;
Totipo still needs no direct network or broad storage permission.

Keep existing **Recovery / Recover retained vault** for this installation's
forgotten roots. Its meaning and data retention remain unchanged. Put Restore
under Add vault, with an explanation that it requires a ZIP and its password.
No ZIP selection implicitly recovers a detached entry, repairs registry metadata,
completes Forget, changes a password, replaces storage, or deletes a vault.

R0 recommends one explicitly initiated foreground operation at a time within the
existing selection/admission domain. No periodic backups, WorkManager/service,
automatic retry, inactive-vault sessions or new exported ZIP intent handler.
These are application choices, not shared Java correctness requirements.

## 3. Existing implementation constraints

* `VaultRegistry` derives `noBackupFilesDir/totipo-local-v1/roots/<local-id>/store`.
  Schema 2 keeps PENDING, ESTABLISHED, FORGETTING and DETACHED entries, bounded to
  128 total. IDs are never recycled. Reservation precedes allocation; attempted
  creation precedes protocol writes; authenticated success precedes promotion.
  Metadata uncertainty poisons the writer. `registry.next`, unknown roots, unsafe
  paths and corrupt metadata block ordinary startup rather than authorize repair.
* `LocalReplicaOwner`, `VaultSelection` and `TotipoApplication` provide one selected
  controller and one lifetime store lease. Complete retirement drains provider
  work and completion handoffs before a new controller can own storage. Lock alone
  does not release selection. Restore must enter through this authority.
* `CoordinatedPrivateStore` has one persistent Java NIO coordinated delegate and
  a whole-root gate. A bridge excludes ordinary store calls. No independent
  filesystem copier or second delegate may read/write its canonical root around
  that gate. Never call a session method while holding the bridge.
* `ForegroundVaultCoordinator.outboundSnapshot()` captures ciphertext under that
  gate and validates it after release. It is a useful ownership precedent, not a
  complete backup API: `DetachedImmutableObject` caps a batch at 512 objects;
  provider traversal also has independent limits. A vault can exceed this after
  many edits even with few visible tokens. Export must fail explicitly on a bound,
  never truncate or reuse a limited Sync inventory as proof of all history.
* `DetachedVaultAuthentication` opens an isolated read-only candidate and closes
  it. Join then freshly checks transport identity and enrolls exact bootstrap
  bytes create-only. Restore can reuse that separation concept, but needs archive
  validation and a durable installation transaction absent from current Join.
* `ImmutableCandidateImporter` authenticates through Java, publishes exact
  representations, distinguishes exact duplicates, different existing bytes,
  failure and uncertainty, and never replaces local evidence. Sync can admit
  useful objects from incomplete provider observations. Normal ZIP import should
  instead validate the entire frozen archive before its first canonical write;
  current Sync's partial-ingress policy is not automatically a restore policy.
* Duplicate recognition scans **all retained roots**, including detached and
  pending ones. `Totipo.vaultId` is structural recognition, not password
  authentication or freshness. Same password, display name or ZIP filename does
  not imply same vault. Unknown/unreadable relevant inventory cannot authorize
  creating a duplicate.
* Existing Sync imports, observes, then publishes; successful local work and
  provider publication have separate results. Ordinary open triggers automatic
  Sync for configured entries. Backup must not invoke that path as a prerequisite;
  history import must control when automatic publication is admitted.
* Biometric records are siblings of `store/`, scoped to local ID and authenticated
  VaultId with Android Keystore keys. Neither record nor key belongs in a backup.
  Existing manifest `allowBackup=false` and no-backup placement stay in force.

The protocol pathname is lowercase **`vault`**. Existing prose uses “VAULT” as the
bootstrap object's name; an archive contract must not turn that into an uppercase
pathname alias. Exact protocol paths and ZIP mapping belong to the shared owner.

## 4. Shared archive contract: prerequisite, not an Android format

Do not implement a private Android ZIP layout, JSON manifest, encrypted wrapper,
checksum scheme, root-key export, token serializer or password-based ZIP encryption.
The pinned format does not establish any of these. Android should consume a
shared Java archive API and its cross-client fixtures. ZIP transport does not
change the existing encrypted vault format or require password change/re-keying.

The shared design must specify, before implementation:

1. Exact entry mapping for canonical `vault` and supported object namespaces;
   directory entries, case sensitivity, optional top-level directories, permitted
   ZIP methods/features, version negotiation and whether a manifest is necessary.
   Android accepts exactly that contract, without heuristic path aliasing.
2. Whether backup covers only supported v1 protocol material or also preserves
   unknown namespaces. R0 recommends a clearly scoped supported-vault-history
   export and explicit unsupported-content outcomes rather than silently claiming
   a lossless filesystem backup. Unknown namespaces remain outside v1 semantic
   interpretation; any archival refusal is archive policy, not a v1 invalid-vault
   judgment. No recursive copying of unrelated private files.
3. Exact validation of the ZIP container as well as every included protocol
   candidate: terminal directory/truncation, entry consistency, supported extras,
   duplicate names, path types, unexpected content and trailing-data policy.
   ZIP CRC alone is not authentication, completeness or provenance.
4. Cross-platform resource limits: compressed bytes, entries, aggregate expanded
   bytes, per-entry bytes, name/extra-field lengths, depth, memory and work.
   Enforce actual bytes and overflow-safe counters, not declared ZIP sizes. Limits
   are documented capacity outcomes; Android may choose stricter device budgets
   and must never advertise a clipped backup as successful.
5. What an inventory proves. An internally valid archive cannot prove it includes
   all history ever authored, remote changes or the newest object set. A valid
   empty vault is allowed. Missing ancestry and observation diagnostics must be
   surfaced separately from bad ZIP bytes and failed authentication.
6. Snapshot and import outcomes, cancellation points, persistence knowledge and
   exact-retry behavior. Shared tests must cover complete histories and corrupt,
   unsupported, malicious and interrupted inputs, not only round trips.

Normal R0 workflows reject malformed/unsupported archives and known-invalid or
unreadable supported candidates before local installation/import. Do not silently
salvage a subset. An otherwise valid historical archive with missing ancestry
requires an explicit limited-history warning and acknowledgment before proceeding;
it cannot be presented as a complete recovery. A separate salvage/forensic export
is deferred. For local backup, unreadable/invalid source candidates or incomplete
inventory block normal success; known history gaps must be disclosed, not hidden.
Shared API review must make these distinctions mechanically observable.

## 5. User workflows

### 5.1 Back up vault

1. Require the selected vault to be open. Password or the normal enrolled biometric
   unlock is sufficient; do not ask for a new backup password. Explain once that
   the ZIP uses the vault's existing password, includes deleted-token history, and
   needs independent storage and password retention. An empty-password vault
   receives an explicit warning: the archive has no meaningful password secrecy.
2. Conceal any displayed code. Capture an operation ID, activation, local ID and
   session generation. Under coordinated ownership, freeze the entire available
   supported-history inventory and exact ciphertext into private staging. Include
   one matching canonical bootstrap. Authoring/import/publication batches cannot
   straddle that capture. No implicit Sync or remote freshness claim.
3. Release the canonical-store gate, then let shared Java validate staged material
   and prepare a complete ZIP. Finish/close it, verify its structure and expected
   inventory, record byte count and a whole-file digest, and force local staging
   as supported. No plaintext tokens/passwords are written to staging.
4. Launch **Save backup** using `ACTION_CREATE_DOCUMENT`, `CATEGORY_OPENABLE`,
   `application/zip`, and a neutral suggested filename such as
   `totipo-backup-20261026-153000.zip`. Avoid token names and local vault labels
   by default. Picker cancellation discards this operation without altering source.
5. After the result, recheck originating authority, expiry and available grants.
   Copy the frozen ZIP to the exact selected document, closing all output handles
   and treating write, flush and close failures as failures/uncertainty. Reopen
   for bounded readback where read access is actually granted; compare length,
   digest and EOF with the prepared file. Do not regenerate from a changed vault
   during retry or readback.
6. Report **Backup saved and checked** only after exact readback. Explain that it
   covers local history captured at preparation, not changes saved while choosing
   the destination, and that cloud replication is managed by the provider.
   Successful write/close without verifiable readback reports **Backup written;
   verification unavailable**; a failed/mismatching readback reports **Backup
   could not be verified**. Neither is the normal verified-success state.

Preparing before the picker avoids making a visible empty document for a source
validation/low-private-space failure. Selection can take time; success must not
suggest the backup includes later edits. R0 allows no automatic continuation
through lock/timeout: revoke authorization to start further I/O, dispose of staged
work once safely drained, and require an explicit new attempt. Already-entered
provider writes may finish; cancellation cannot retract their side effects.

### 5.2 Restore from backup

1. From unselected Vaults, choose Restore. Fully retire an existing selection before
   entering this journey. Do not temporarily open a second canonical session.
2. Select a ZIP using `ACTION_OPEN_DOCUMENT`, `CATEGORY_OPENABLE`, read permission
   and ZIP MIME filtering. Offer an explicit broader file filter when a provider
   mislabels ZIPs; extension/MIME are never validation. Copy the selected bytes,
   with a compressed-byte cap, into private operation staging. Close ingress,
   then validate the complete immutable copy. Subsequent steps never reread a
   mutable provider document as though it were the same candidate.
3. Obtain the backup's **existing password** using the normal protected form.
   Shared Java authenticates bootstrap and validates every supported object.
   Ask for a local display name through the existing naming policy; it changes
   no archive bytes or identity. Report authentication failure as “Could not
   unlock this backup. Check the password or try another copy.” Corruption can
   cause the same outcome; do not assert that the password is definitely wrong.
4. Check duplicate identity against complete fresh retained-root inventory, after
   authentication and again at installation admission. If present, refuse a new
   allocation and offer the explicit existing-vault journey described below.
5. Only after validation/confirmation reserve a new PENDING entry, record a durable
   restore intent and attempted-install state, then install through one exclusive
   store owner using shared create-only storage operations. No canonical file is
   filled progressively, replaced, or copied by arbitrary Android filesystem I/O.
   Close the isolated validation session before opening the canonical session.
6. Reopen and authenticate installed bytes normally, verify exact identity and
   inventory outcomes, complete Java observation, then promote to ESTABLISHED.
   Do not expose a success/selectable ordinary entry while object installation is
   incomplete. A successful bootstrap open alone is insufficient. A known-gap
   acknowledgment remains visible in the final result.
7. Finish as a **local-only vault**, with no inherited folder, permissions,
   publication hint or biometric credential. Explain that later optional Sync
   configuration is separate. Default to a locked result with an explicit Open
   action; biometric opt-in belongs to a subsequent normal password unlock.

If a matching ESTABLISHED entry exists, offer **Open existing vault** and explain
that **Add missing history** is available there; never silently switch or import.
If it is DETACHED, explain **Recover retained vault** first, then Add missing
history. FORGETTING must finish scoped cleanup first. Pending/ambiguous/unreadable
matches require explicit storage recovery while preserving evidence. Never offer
“restore another copy anyway,” identity rewriting, deleting the retained root,
or merging histories as a side effect of duplicate detection.

### 5.3 Add missing history

1. Require the target to be selected and open. Explain: “Adds compatible history
   from a backup. Existing files are kept. Tokens and conflicts may change.”
   This is not an undo-to-date or token replacement operation.
2. Pick and freeze a ZIP as for Restore; validate its whole container. Require its
   exact canonical bootstrap/VaultId to match the target's authenticated identity.
   A different identity is rejected even with the same password. This operation
   normally needs no password prompt: the open target session authenticates
   candidates using its existing root. The shared API must support this path.
3. Shared validation builds a bounded immutable import plan before the first
   write. Exact existing objects are no-ops. Missing valid objects are eligible.
   Different bytes at an existing canonical name block that object and, for R0,
   stop the normal batch before writes when detected in preflight. Two validated
   representations contradicting one identity are an integrity failure; never
   choose a winner or repair by overwrite. Recheck at create-only publication,
   since preflight is not a write reservation.
4. Obtain explicit confirmation, then add exact encrypted objects under the
   existing bridge/domain, checking ownership/cancellation between objects.
   Preserve `vault` and all existing objects, including invalid local evidence;
   this workflow cannot repair an occupied corrupt object. No tombstones,
   resolutions, new TokenIds or user edits are authored by import itself.
5. Release the bridge before requesting observation. Await evidence of a local
   pass after import under the pinned ordering/composition assumptions, inspect
   diagnostics, and separately report import persistence and presentation status.
   Returning from `requestRefresh()` is never completion. Reuse the reviewed
   two-pass barrier only where its same-session/coordinated-import assumptions
   hold; a future correlated shared API would need its own reviewed contract.
6. Report counts of added/already-present/unattempted objects and failures, not
   a claimed number of recovered tokens. Show existing Java conflicts through
   ordinary Resolve. Newer heads/tombstones can dominate old imported history;
   missing concurrent heads can expose conflicts or previously unknown tokens.
   Additive storage does not promise an unchanged token list or resurrect every
   deleted token. Archive timestamps never choose winners.

For a configured target, hold automatic Sync admission through validation/import
and its observation result. Import never reads the configured folder. After an
explicit successful local completion, use the existing local-change notification
policy to schedule a separate ordinary Sync and set its retry hint as appropriate.
Disclose before confirmation that added history can subsequently be published to
that folder. Partial/uncertain import never authorizes immediate outbound
publication; require explicit inspection/reopen and the normal import-before-
publish checks. Local-only success creates no transport hint or provider work.

## 6. SAF and filesystem boundaries

Android's [document access guide](https://developer.android.com/training/data-storage/shared/documents-files)
supports document pickers without broad storage permissions. Create-document
selects a new file and resolves filename collisions; it does not overwrite an
existing document. R0 uses operation-scoped document grants, not a Sync tree.
Process-death policy is restart/reselect, so no persisted grant is required.

| Boundary | Allowed material and authority |
| --- | --- |
| Canonical private root | Exact encrypted protocol data, existing owner/lease and coordinated Java delegate only |
| Private operation workspace | Random operation-ID directories under a dedicated no-backup area, outside registry `roots/` and protocol stores; ZIP, frozen ciphertext, bounded non-secret transaction metadata |
| Registry and restore journal | Android installation intent/state, local IDs and operation identity; never portable archive material |
| SAF document | Opaque `content:` URI from the originating picker, opened via ContentResolver; never converted to a filesystem path |
| Archive contents | Only the shared profile; never registry, Sync preferences/URIs, retry hints, logs, biometric files, aliases, key material or password buffers |

The workspace/journal placement above is proposed, not an existing directory.
It requires reviewed startup accounting, no-follow path checks and bounded cleanup.
Do not place a staging directory under `roots/`: the current inventory deliberately
rejects unknown roots. Do not export the root container that holds `biometric-v2`.
No-backup storage, rather than disposable cache alone, is needed for durable
installation intents. All staging remains encrypted; deletion is best effort,
not secure erasure.

Provider metadata is advisory. Documents can be nonseekable streams; size can be
unknown and partial-download/virtual flags exist. Use bounded reads to actual EOF,
reject unavailable/partial inputs, and require raw byte access rather than virtual
file conversion. See [document metadata and flags](https://developer.android.com/reference/android/provider/DocumentsContract.Document).
Use progress bytes/phase labels, not invented percentages when size is unknown.

[ContentResolver](https://developer.android.com/reference/android/content/ContentResolver)
documents that `w` may not truncate and exclusive access can return pipes. Request
a reviewed truncate-capable mode where supported for the new document; do not
fall back to append or blindly reuse an earlier failed destination. A retry opens
a new create-document journey. Failed/canceled output may remain visible as an
empty or incomplete document; explain that the user can remove it in their file
provider. R0 performs no automatic document deletion, rename or overwrite, and
never deletes a selected import source. Unsupported
mode/stream/null handle is a surfaced provider failure. Readback must detect
extra trailing bytes as well as truncation. There is no portable atomic
create-write-rename transaction or durability acknowledgment for arbitrary SAF
providers. A local force/flush is not proof of cloud upload. These are design
inferences from the provider interface, not universal measured provider behavior.

[ParcelFileDescriptor](https://developer.android.com/reference/android/os/ParcelFileDescriptor)
can communicate errors for reliable pipes, with limitations. Use available error
reporting correctly; no error is not proof of remote durability. Cancellation
signals may help supported platform calls, but interruption, timeout and executor
shutdown do not prove provider I/O stopped. Retain admission until actual work and
handoffs drain, as current retirement already requires. A hung provider can require
process restart; no timeout releases ownership to another vault.

Do not persist document grants routinely or release pre-existing app grants during
cleanup. One-shot document work must not revoke a Sync tree or another operation's
access. If a later phase adds persisted access/resume, extend app-wide grant
inventory and durable acquisition/cleanup accounting first; current
`SyncGrantOwnership` accounts for bindings, not arbitrary document jobs.

## 7. Interruption, security and failure behavior

### Durable restore transaction

Current PENDING/attempted flags cannot distinguish Create, Join and Restore or
record an expected archive inventory. A reviewed registry-compatible transaction
extension is required; it cannot be simulated by ordinary startup open/promotion.
Proposed non-secret phases:

```text
private copy validated → user confirmed → entry reserved
→ restore intent durable → attempted marker durable
→ exact bootstrap enrolled → objects installed/read back
→ canonical authentication/observation verified → established → intent completed
```

Persist intent before protocol writes; record enough bounded information to bind
operation, local ID, archive digest, expected identity/inventory and current phase.
It is local recovery metadata, never a shared ZIP-format extension. Ordering,
checksums, atomic replacement/parent persistence, journal-version handling and
lost acknowledgments require fault-injection tests. Do not mark ESTABLISHED and
then discover that history is only partially installed.

On restart, a reserved entry remains accounted for even if allocation was
interrupted. Any attempted Restore stays blocked from ordinary Create/Join or
bootstrap-only promotion until the restore transaction is reconciled. Resume is
explicit, requires fresh password authentication and revalidated identical staged
input or reselected digest-matching input, and uses exact idempotent writes.
Missing staging does not grant permission to recreate the root or forget its
partial bytes. Unknown/corrupt journal or registry temporary keeps evidence and
requires explicit recovery. A process death after ESTABLISHED but before journal
completion is reconciled against actual registry/root contents, never blindly
installed twice. This reconciliation is a prerequisite for Restore shipping.

### Failure matrix

| Event | Required result / preserved evidence |
| --- | --- |
| Picker canceled or no URI | No import/install; no Sync/binding change; dispose private uncommitted workspace |
| Late/duplicate result, process recreated, changed selection | Reject originating request; no lookup of replacement controller; no automatic resume |
| Bad ZIP, duplicate/path-escaping entry, unsupported feature, size/work cap | Reject before canonical mutation; bounded sanitized error |
| Wrong password or damaged authenticated bytes | No installation; distinguish structural corruption where established, otherwise ambiguous unlock failure |
| Different vault identity | Refuse Add missing history and duplicate routing; existing target unchanged |
| Known missing ancestry | Explicit limited-history result/acknowledgment; no completeness or freshness claim |
| Low private storage during ingress/preparation | Stop before document picker or canonical mutation where possible; source untouched |
| Low storage or I/O failure during restore installation | Preserve PENDING root, intent and actual bytes; no success/promotion or automatic rollback |
| Low destination space, revoked access, provider crash/disconnect | Partial/uncertain ZIP may exist; no verified-success message; source unchanged |
| Output close succeeds but readback unavailable | Written/unverified outcome; do not claim checked backup or remote durability |
| Cancellation/Lock during a canonical object write | Let admitted write reach a persistence outcome; stop further writes; preserve partial/uncertain state |
| Process killed during ZIP output | Destination may be empty/truncated/complete; durable operation intent allows an interrupted notice, never success |
| Import partly succeeds or acknowledgment is lost | Report added/uncertain/unattempted separately; preserve all objects; exact retry after explicit reconciliation |
| Registry/journal persistence uncertain | Retain ownership/evidence, block blind retry/promotion; restart/recovery required |
| Biometric enrollment canceled/fails after Restore | Restored vault remains valid and password-openable; convenience setup has separate result |

R0 should record a small non-secret export/import intent before external/canonical
side effects so restart can explain that an operation was interrupted. It grants
no authority to reopen a URI or resume writes. Uncommitted workspace cleanup is
limited to positively recognized operation directories with no live task or restore
reference. Canonical partial data and uncertain metadata are never swept as
“temporary.” Storage estimates should include ZIP input/output, expanded staged
ciphertext, installation copies, journaling and filesystem overhead; available
space is only a preflight hint, not a reservation. Never delete existing history
to make room. Cancellation after publication is not rollback.

### Untrusted archive and credential handling

Android documents [ZIP path traversal risk](https://developer.android.com/privacy-and-security/risks/zip-path-traversal):
ZIP libraries alone do not safely validate entry paths. Shared code must reject
absolute/parent/alternate-separator escapes, ambiguous normalized names, duplicate
canonical targets, symlink/special entries, overlong/deep paths and decompression
bombs. Prefer exact-name shared extraction into isolated staging with no-follow,
create-new targets; no archive-derived path reaches a managed root. Validate the
complete container, actual expanded bytes and supported protocol material before
installation. CRC or file extension is insufficient. Do not log entry names or
raw provider exceptions that could contain sensitive user text.

The archive retains existing vault encryption and password authentication. ZIP
entry counts, sizes, names and the selected document filename can reveal metadata;
it is not an opaque encrypted container. An attacker with a copy can attempt
password guesses, and valid old copies can replay history. No backup provides
rollback resistance, origin proof, secure deletion, password recovery or revocation
of previously copied TOTP secrets. Import may expose historical complete secrets
again through the existing vault model; it does not erase anything.

Use caller-owned mutable password buffers with worker-scoped ownership, existing
encoding/length validation, explicit empty-password acknowledgment, and best-effort
wiping on every rejection/completion/cancellation path. Never serialize passwords
in Bundle, saved state, journals, filenames, intents, work queues or logs; disable
sensitive widget state saving. Do not normalize/trim the password. No password
is handed to the provider I/O lane. Rotation may retain a non-secret operation
reference in memory; it must not retain a password-bearing form/request authority.
Process restart always asks again. For the empty-password export warning, carry
only an authenticated in-memory protection-status fact from normal unlock, or
obtain it through a reviewed shared API; VaultId and ciphertext shape cannot
establish password strength. If unavailable, show the general existing-password
warning without claiming password protection strength. Keep the 15-minute monotonic inactivity policy;
archive work and provider callbacks are not user activity.

Restore does not restore biometrics. Optional re-enrollment is a separate explicit
normal password-unlock action bound to the new local ID and authenticated VaultId,
with a new device-local credential/key and fresh strong-biometric authorization.
A canceled offer never rolls back Restore or creates a prompt loop. Add missing
history neither replaces nor enrolls target credentials. Device key invalidation
uses existing Disable/re-enroll behavior; it does not make the backup unusable.

## 8. Responsibility split and shared API needs

| Shared Java / shared archive design | Android |
| --- | --- |
| Portable ZIP profile, version/unknown-content policy and cross-client fixtures | Native entry points, explanatory text, confirmations, accessibility and naming |
| Vault bootstrap/object authentication, grammar, graph/conflicts and exact metadata | SAF intents, URI grants, streams, copy/readback, provider errors and lifecycle |
| Complete bounded archive reader/writer and typed validation/capacity outcomes | Private workspace budgets, safe placement, cleanup and interrupted-job notice |
| Snapshot/inventory contract and supported-history coverage evidence | Selection/activation ownership and freeze admission around canonical capture |
| Same-vault compatibility and exact immutable import plan/results | Registry duplicate routing across all retained roots and restore transaction journal |
| NIO create-only publication, durability outcomes and exact retry semantics | One canonical delegate/lease, bridge scheduling, no session-under-bridge calls |
| Authentication/validation-only entry point; open-session history import support | Password form/buffer lifetime, lock/timeout, optional fresh biometric enrollment |
| Observation completion semantics, or documented existing composition | Main-thread marshalling, late-result relevance and separate Sync admission/results |

The shared API should expose capabilities, not secret roots or Android paths. The
following are **requirements, not existing method names or promised signatures**:

* Prepare an immutable bounded backup from an exclusively captured store inventory,
  with exact identity/object membership, coverage diagnostics and ciphertext-only
  retained state. Stream from disk if necessary; never turn current token
  projections into an archive. Avoid the current Sync batch's 512-object ceiling.
* Write/read/validate the agreed ZIP through portable streams or seekable private
  files with explicit resource limits and close/ownership contracts. Finish
  validation before returning a trusted candidate; public descriptive values
  alone must not become forged import authority.
* Authenticate a staged restore candidate without mutating any canonical store;
  distinguish unsupported, malformed, authentication failure, limited history,
  unavailable crypto/storage, canceled and capacity-exceeded outcomes.
* Install a validated candidate into an exclusively owned empty configured store,
  preserving exact bootstrap/object bytes and reporting per-stage persistence.
  Support Android's coordinated ordinary-move delegate; do not require hard links
  unsupported by the qualified private filesystem. No replacement-path fallback.
* Plan/apply additive same-vault history against an open session/store domain,
  without a new password/KDF or extra canonical session. Preflight all candidates;
  preserve exact duplicates, refuse occupied-different targets, preserve valid
  siblings, and stop on uncertain publication with honest partial results.
* Define session-bound versus detached plan lifetime, cancellation boundaries,
  total-work/progress reporting and exact retry/revalidation requirements after
  close or process death. Do not imply an all-or-nothing object transaction.

Existing public SPI/validation can support experiments, but Android must not own
another long-term implementation of archive grammar or cryptographic import.
There is no required dependency upgrade in this design milestone. A future shared
release and Android dependency provenance/lock/cache review are implementation
prerequisites, separately qualified.

Operation mapping follows the pinned Java taxonomy: immutable summaries are A;
secret-backed validation is H (and authentication/open is G); canonical
publication uses the coordinated store boundary. Archive streaming/provider I/O
has no invented A–H Java classification. Import is not a token builder or merge
save and does not automatically acquire E freshness semantics. Any retry must
follow its own reviewed persistence contract; current `PublicationRetry` handles
are session-owned token-publication capabilities, not archive job resume tokens.
New VaultState emissions do not cancel valid local computations by themselves.
Use captured activation/session/request identities and explicit lifecycle revocation.

## 9. Qualification scenarios for implementation

These are acceptance requirements, **not tests run in R0**. Use deterministic
latches, fake streams/stores and fault boundaries before physical provider tests.
Extend existing registry, owner, coordinator, controller, grant and biometric
suites rather than certify ZIP behavior through source-text guards alone.

| Area | Required scenarios and assertions |
| --- | --- |
| Shared interoperability | Java/shared writer → reader; Android ↔ another shared client; fixed golden ZIP fixtures; byte-identical bootstrap/object set and metadata after round trip |
| History coverage | Empty vault; >512 revisions; old/deleted/conflicting/equal-valued heads; missing ancestry; unsupported sibling namespace; no silent truncation or heads-only export |
| Local-only | Create/Add/Edit/Delete/Lock/restart, backup, restore into fresh disposable installation, import old backup; zero Sync calls, tree grants or transport hints |
| Snapshot concurrency | Save/import at capture boundary; no partial canonical representation; no session API under bridge; stage frozen despite later edits; gate released before provider I/O |
| Archive hostility | Truncated headers/data/terminal directory, CRC/size mismatch, duplicate names, traversal/absolute/backslash/case aliases, symlinks, nested ZIP/bomb, unsupported methods/versions, over-budget input/entries/work |
| Authentication | Correct/wrong/empty/Unicode/malformed/oversized password; damaged bootstrap; wrong-root object; truthful ambiguous failure; every password buffer terminal path |
| Duplicate identity | Matching established, detached, forgetting and attempted-pending roots; registry full; unreadable inventory; equal passwords/different identities; names irrelevant; no extra root/session |
| Additive safety | New/missing/exact-existing objects; occupied invalid/different bytes; symbolic validated contradiction; idempotent repeat; original bytes and `vault` unchanged; expected conflicts/tombstone behavior |
| Restore crash ordering | Inject death/failure before/after reserve, intent, attempted marker, bootstrap, each object, canonical open, promotion and journal completion; never blind Create, orphan root, false success or silent cleanup |
| Partial persistence | Definite failure vs lost acknowledgment; successful prefix retained; exact retry; missing/corrupt staging and journal; no rollback deletion; unsafe domain retains ownership |
| SAF fault transport | Null handles, unknown/false size, short/zero reads, extra bytes, read-only destination, unsupported modes, failed flush/close, denied readback, changing bytes, reliable-pipe error, revoked grants, offline/removable media and low destination storage |
| Lifecycle | Rotation at picker/copy/auth/write/readback, process death, Lock/inactivity expiry, background, Change Vault, repeated taps/results, late callback and blocked provider; no stale controller admission or early lease release |
| Security/credentials | ZIP contains no registry/biometrics/preferences/plaintext secrets; no password saved state/logging; optional reenrollment cancel/failure/invalidated key; target and other-vault credentials unchanged |
| Sync interaction | Configured and disconnected targets, automatic Sync queued during import, partial import refuses publish, successful import followed by separate Sync; provider receipt never presented as peer receipt |
| UX/accessibility | Native picker cancellation/retry, clear progress/uncertainty/limited-history text, long names/font scaling/TalkBack, Back navigation and separate retained-vault Recovery |

Physical qualification must include API 30 minimum support and a current supported
Android release, a real local DocumentsProvider plus available cloud/removable
providers, and process interruption/low-space cases in disposable storage. Provider
fakes prove deterministic branches; actual cloud upload persistence requires its
own provider-specific evidence. Test import without editing existing real vaults
or clearing owner-app data. A fresh disposable installation is needed for genuine
restore acceptance because retained roots correctly block duplicate identities.
Use existing qualification signing ownership and exact final APKs. Add biometric
hardware runs only when enrollment/lifecycle integration is implemented; do not
repeat unrelated full Syncthing or row-harness campaigns by default.

Each product phase follows AGENTS: ordinary baseline, focused compile/controller/
store tests during iteration, one stable final normal Gradle checkpoint, strict
offline clean checkpoint, external debug/release APK verification and Maven
boundary/graph review, affected device evidence, frozen included inputs and the
final human `nix flake check`. Any new manifest component/permission or dependency
requires explicit boundary review; R0 proposes neither. Harness changes under
`tools/` are included inputs and must stabilize before that human gate.

## 10. Phased implementation and decisions to close

1. **Shared contract and fixtures.** Agree/discover the common archive profile,
   namespace/feature/limit policy, snapshot coverage and candidate/import APIs.
   Publish shared cross-client fixtures and security/failure tests. Do not start
   an Android ZIP encoder in parallel with unresolved format choices.
2. **Back up vault.** Add coordinated complete ciphertext capture, private staging,
   document create/copy/readback, scoped authority and interrupted-export notice.
   Qualify local-only first, then configured-vault independence and provider
   failures. Keep unverified/uncertain outcomes distinct from checked success.
3. **Restore from backup.** Add unselected ingress and authentication, duplicate
   routing, durable restore-intent/registry recovery and create-only installation.
   Crash reconciliation and no-partial-promotion tests are release blockers.
   Ship local-only output with explicit ordinary Open; use existing biometric
   opt-in afterward.
4. **Add missing history.** Add open-session shared validation/plan, coordinated
   additive writes, partial persistence reporting and local observation. Integrate
   separate optional Sync only after deterministic lifecycle/ordering evidence.
5. **Integrated usability/interoperability.** Exercise backup from a local-only
   vault, loss of a disposable installation, restore, old-history import with
   newer edits/tombstones, conflicts, optional biometric enrollment and independent
   Sync setup. Update user documentation only when actual features qualify.

Before implementation, resolve the shared archive owner/profile, exact resource
budgets, unknown-content policy, supported ZIP features, capability lifetime, and
restore journal/schema reconciliation. Resolve whether product policy allows
written-but-unverified backups beyond a warning result; R0 recommends retaining
that honest result while reserving normal success for readback. None requires
inventing Android wire data. Automatic scheduled backup, overwrite/replace
restore, cross-vault transfer, salvage/repair, password changes and archive-level
re-keying remain outside this milestone.

## 11. R0 validation, freeze and final state

Reinspected actual dependencies: `package.nix` selects `app/src`, `gradle`, `tools`
and its explicit build/version/license/provenance files. `review/` is excluded.
`flake.nix`'s release-tooling fileset and release wrapper checks also do not consume
this report. CI's unchanged-input assertion checks repository cleanliness after
builds; it does not make report prose a build input. Therefore this report does
not invalidate Android Gradle/artifact/device or effective Nix qualification inputs.

Freeze statements for R0:

* **Production source stable:** unchanged from starting HEAD.
* **JVM tests stable:** unchanged from starting HEAD.
* **Build/package configuration stable:** unchanged, including dependency pins.
* **Qualification harness/tooling stable:** unchanged, including `tools/`.

Selected validation: local Markdown-link existence, source/design consistency
review and `git diff --check`. **PASS:** all 13 relative Markdown links resolve,
fenced blocks balance, and whitespace validation passes. The final edit was
checked again. No runtime feature was tested or certified by this design.

| Execution count | R0 result |
| --- | --- |
| Baseline full Gradle | 0 — excluded documentation only |
| Focused Gradle (test / compile / boundary) | 0 / 0 / 0 |
| Final normal full Gradle | 0 |
| Strict-clean Gradle | 0 |
| External artifact verifier / embedded Gradle verifier | 0 / 0 |
| Physical harness phases/runs | 0 |
| Agent Nix / human Nix | 0 / 0 — prohibited/unnecessary for this scope |
| Commits / tags / releases | 0 / 0 / 0 |

No gate was repeated or invalidated. No human result or approval is required for
this excluded report-only milestone. Future implementation qualification above is
prospective, not authorization to execute it during R0.

Final Git evidence:

```text
branch: main
HEAD: f0ce51ab1c159dd366d25032d55064a775eb39a0
git status --short:
 A review/BACKUP_RESTORE_R0_ANDROID_DESIGN.md
git diff --name-only:
review/BACKUP_RESTORE_R0_ANDROID_DESIGN.md
git diff --cached --name-only: <empty>
git diff --cached --quiet: PASS
git diff --check: PASS
```

The new report has intent-to-add membership only; no file contents are staged.
No other file changed. Excluded report-only edits leave all four freeze
statements intact and do not invalidate prior included-input qualification.
