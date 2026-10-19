# A-MV0 — Android multi-vault architecture audit

## 1. Scope, starting state, and conclusion

Classification recorded before editing: **DOCS_ONLY / architecture audit**.
Date: 2026-10-19. Starting branch: `main`. Starting HEAD:
`b8b2f551f566895e24236f62e02ab767d444b586`.
`git status --short` was empty. Only this report is changed. No implementation,
test, dependency, build, harness, protocol, or vault-data changes are made.

Multiple independent vaults appear supportable with the existing Java 0.2.0 API.
The smallest design is a local registry, deferred selection of an immutable local
root, one Application selection/admission owner, and one instance of the existing
controller for each selection. Retain the coordinated store, foreground
coordinator, Sync algorithm, and password-open path. Retire the entire selected
controller's work before constructing the next. Keep the legacy canonical root
in place. Do not introduce simultaneous sessions, independent writers for one
root, inactive-vault Sync, cross-vault lists, or a general vault-management framework.

This is a source-grounded proposal, not qualification of multi-vault behavior.
The major new lifecycle requirement is stronger than ordinary Lock: **switching
must wait for old provider I/O to finish**, even though Lock can close the local
session without waiting for SAF. A permanently blocked provider can therefore
prevent switching until process restart. No Java/protocol change is needed to
express this conservative policy; prompt cancellation of arbitrary Binder I/O
is not supplied by the current Android transport.

## 2. Evidence and current ownership map

Read current AGENTS.md, README.md, TOTIPO_JAVA_DEPENDENCY.md, package.nix,
flake.nix, CI, relevant source/tests/device tooling, and these architecture and
qualification records:

- [Java operation audit](ANDROID_JAVA_OPERATION_MODEL_AUDIT.md), with its later
  [guidance/F08 disposition](ANDROID_JAVA_GUIDANCE_PIN_REFRESH_REPORT.md), §§9–15.
- [Java 0.2.0 reconciliation](JAVA_0_2_0_R19_ANDROID_RECONCILIATION_REPORT.md),
  §§9–24, 32–33; this supersedes historical openPrivate/SPI descriptions.
- [Local ownership design](M1D_PRODUCTION_LOCAL_REPLICA_DESIGN_REPORT.md), §3;
  [coordinated live store](M1I_COORDINATED_STORE_LIVE_SYNC_REPORT.md), §§5–11.
- [SAF binding/hardening](M3A_SAF_BINDING_INBOUND_SYNC_REPORT.md), §33;
  [outbound publication](M3B_OUTBOUND_IMMUTABLE_PUBLICATION_REPORT.md), §§3–20.
- [Daily-driver behavior](ANDROID_DAILY_DRIVER_UX_REPORT.md), §§7–12, and
  [current bootstrap qualification](ANDROID_SAF_BOOTSTRAP_CURRENT_SYNC_RECONCILIATION_REPORT.md),
  §§3–6. Successful Join now triggers ordinary automatic Sync; the older
  no-automatic-import test is not the current product contract.
- [Biometric/inactivity qualification](ANDROID_BIOMETRIC_INACTIVITY_LOCK_REPORT.md),
  §§2–19, and [stale-subscriber correction](ANDROID_STALE_SUBSCRIBER_OWNERSHIP_FIX_REPORT.md),
  §§3–16. Preserve the latter's ownership check at the controller mutation boundary.

Read the exact [Java guidance at f0a028676c1801a10b2d8d2650bf9c24357d1fa1][guidance]
read-only from GitHub, including operation taxonomy, replay-latest, observation
composition, editing, merge freshness, persistence handles, and blocking/close.
Fetched UTF-8 SHA-256:
`338837e3563446f8ec11f7bb47c0e4f3ff5502b7608e397f9b8c4d75372715fd`, matching
TOTIPO_JAVA_DEPENDENCY.md. Runtime remains released source
`d6310c177ae930df188fd4f5798622c935698b2e`, Java 0.2.0 / format v1/r19.
Inspected released NioStoreComposition and published 0.2.0 NIO source in memory;
no other repository was modified or built.

Source references below are relative to this report; line numbers describe the
starting HEAD, not an assumed future implementation.

| Ownership boundary | Actual source and behavior |
| --- | --- |
| Process/Application | [TotipoApplication.java:12–47](../app/src/main/java/org/totipo/android/TotipoApplication.java#L12) creates one controller in onCreate on API 30+, supplies main dispatcher, elapsedRealtime clock, clipboard, one Sync binding; Activity started/stopped counting supplies foreground transitions. |
| Root selection and exclusion | [LocalReplicaOwner.java:23–69](../app/src/main/java/org/totipo/android/LocalReplicaOwner.java#L23): static singleton; constructor fixes `noBackupFilesDir/totipo-vault`; one active lease; acquire creates the directory, checks final directory without following links, releases only explicitly. |
| Controller | [AndroidVaultController.java:70–109](../app/src/main/java/org/totipo/android/AndroidVaultController.java#L70): final owner and binding, one bounded vault worker, one provider lane, session/binding/presentation generations. [160–198](../app/src/main/java/org/totipo/android/AndroidVaultController.java#L160) retains coordinator/observation and starts binding restoration plus local discovery. |
| Storage construction | [ForegroundVaultCoordinator.java:83–157](../app/src/main/java/org/totipo/android/reconcile/ForegroundVaultCoordinator.java#L83) leases for discovery/open/create. [593–600](../app/src/main/java/org/totipo/android/reconcile/ForegroundVaultCoordinator.java#L593) constructs storage from lease.root and transfers facade to Totipo. |
| Physical storage domain | [CoordinatedPrivateStore.java:16–50](../app/src/main/java/org/totipo/android/reconcile/CoordinatedPrivateStore.java#L16) constructs `NioStoreComposition.coordinatedDelegate(root, new NioDurability())`; persistent delegate and fair instance gate; one logical session facade. [51–125](../app/src/main/java/org/totipo/android/reconcile/CoordinatedPrivateStore.java#L51) excludes session SPI during bridge batches; [130–152](../app/src/main/java/org/totipo/android/reconcile/CoordinatedPrivateStore.java#L130) separates facade close from physical close. |
| Java session | [ForegroundVaultCoordinator.java:73–81](../app/src/main/java/org/totipo/android/reconcile/ForegroundVaultCoordinator.java#L73) owns session/store/lease; [574–584](../app/src/main/java/org/totipo/android/reconcile/ForegroundVaultCoordinator.java#L574) closes Java session, logical facade, domain, then lease. Failure retains ownership for retry. |
| Sync configuration | [AndroidSyncFolderPort.java:14–69](../app/src/main/java/org/totipo/android/sync/AndroidSyncFolderPort.java#L14) fixes `sync_binding_v1` SharedPreferences; SAF grants live in ContentResolver. [SyncFolderBinding.java:25–113](../app/src/main/java/org/totipo/android/sync/SyncFolderBinding.java#L25) owns remembered binding and generation, saves before replacing, releases previous grant on replacement/disconnect. |
| Provider work | [ProviderIoLane.java:9–57](../app/src/main/java/org/totipo/android/sync/ProviderIoLane.java#L9): bounded one-thread executor, detached data handoff, no session/store/credentials, shutdown without await. |
| Activity shell | [MainActivity.java:75–127](../app/src/main/java/org/totipo/android/MainActivity.java#L75) captures controller, constructs biometric helper, attaches/detaches snapshots, preserves controller across rotation; forms derive from controller state. |
| Other Activity | [OtpAuthEnrollmentActivity.java:32–43](../app/src/main/java/org/totipo/android/OtpAuthEnrollmentActivity.java#L32), [60–118](../app/src/main/java/org/totipo/android/OtpAuthEnrollmentActivity.java#L60) captures same Application controller; only active/open controller can accept external enrollment. |
| Biometric storage | [AndroidBiometricCredentials.java:21–100](../app/src/main/java/org/totipo/android/AndroidBiometricCredentials.java#L21) uses one no-backup AtomicFile and fixed Keystore alias; [BiometricRecord.java:9–44](../app/src/main/java/org/totipo/android/BiometricRecord.java#L9) bounds envelope and authenticates VaultId in AAD. |

## 3. Single-vault assumptions and storage proposal

The root is fixed when Application calls LocalReplicaOwner.from, **not** an
intrinsic Java process-wide storage identity. Compositions are constructed later
by worker discovery/open/create and closed by their coordinators. Discovery is a
short-lived composition even before authentication; normal open has its own
composition. Root choice can be deferred until selection without changing the
Java factory or coordinated store semantics.

The released [composition contract][composition] requires exclusivity across each
whole root. Released NioTotipoStore, NioObjectStorage, and NioVaultStorage hold
instance Path/closed/operations state; file channels are scoped to operations.
No singleton root cache or global persistent file handle appears in those classes.
Android's domain lock, session facade, integrity list, worker, projections, retry
flags and provider snapshots are also instance-owned. The **production static
LocalReplicaOwner singleton** is the deliberate root identity/exclusion point.
Cryptographic providers and stateless utilities do not select vault roots.

Separate non-overlapping private roots can use separately constructed compositions,
provided each obeys the contract and no old composition/handle remains usable.
This is an architectural inference from these implementations, still requiring
Android two-root qualification. Do not put two delegates on the same root or
use ordinary shared hard-link NIO as a fallback. Do not move canonical storage
into Syncthing or SAF directories.

Recommended minimal changes:

1. Extend LocalReplicaOwner's process arbitration to admit one selected immutable
   location at a time. Supply the controller a bound owner handle for that entry;
   keep the familiar acquire/lease/root/close flow. Bound handles must delegate
   to the **same process admission authority**, rather than each having an
   independent `active` field that permits overlapping selections.
2. Resolve registered locations only inside no-backup storage: one reserved
   legacy location and generated managed locations such as
   `totipo-vaults/<entry-id>/store`. Metadata/credentials sit outside `store`.
   Registry records never supply arbitrary absolute paths or SAF-derived paths.
   Validate containment, all path components, no symlinks, uniqueness and no
   ancestor/descendant roots. Current acquire's final-component check alone is
   not a complete validator for a new nested-path scheme.
3. Distinguish existing-entry discovery from new-root allocation. Current acquire
   creates missing directories. For a remembered established vault, a missing
   root/VAULT must be an unavailable/recovery state, **not** permission to create
   a replacement. Only explicit new-entry creation allocates a fresh location.
4. Reuse CoordinatedPrivateStore and ForegroundVaultCoordinator unchanged where
   possible. A controller holds one immutable location for its lifetime. Avoid
   a mutable global “current path” read by asynchronous work.
5. Keep an Application selection permit until the old controller is fully
   retired, including provider drain. Releasing the coordinator's local lease
   alone does not authorize another selection.

Single-vault assumptions to remove are: eager Application construction; singleton
owner's fixed root; globally named binding and retry preferences; globally named
biometric record/key; Activities caching a forever-current controller; untagged
folder-picker results; diagnostics/retry/presentation fields implicitly describing
the only vault; and Lock rediscovering the same root rather than terminating a
selection. The coordinator and store themselves already carry explicit ownership.

## 4. Persistent metadata inventory and minimal registry

Repository searches of production `main` and `release` find only the binding
SharedPreferences and biometric AtomicFile as explicit application metadata
writers. Vault protocol storage is written through the coordinated Java delegate.
There is no current registry, last-selection preference, persisted controller
snapshot, persisted token list, or separate cached VaultId database.

| Existing value / source | Classification and proposed treatment |
| --- | --- |
| `noBackupFilesDir/totipo-vault` and its immutable VAULT/objects | Vault-specific canonical data. Root selector is implicit today; register legacy location without moving bytes. Protocol evidence, not preferences, determines contents. |
| `sync_binding_v1`: `schema`, `tree_uri`, `read`, `write` ([port:27–42](../app/src/main/java/org/totipo/android/sync/AndroidSyncFolderPort.java#L27)) | Vault-specific binding metadata. Namespace by local entry; URI is transport location, never VaultId. Saved flags are historical hints; recheck actual platform grants. |
| ContentResolver persisted URI permissions ([port:48–69](../app/src/main/java/org/totipo/android/sync/AndroidSyncFolderPort.java#L48)) | Application-global capability set, with vault-specific references. Require grant bookkeeping across all registered bindings and in-flight work. |
| `pending_publication` in same preferences | Vault-specific retry hint, not a durable Java retry handle or evidence of missing objects. Preserve legacy value; migrate idempotently; never use it to authorize publication. Failed commit is currently ignored by setter. |
| `biometric-password-v1`: version, fixed alias, VaultId, IV, encrypted password/tag | Vault-specific credential; currently implicitly belongs to the sole root. Move to per-entry addressing or retain a legacy-only adapter. No plaintext persistence. |
| AndroidKeyStore `org.totipo.biometric-password.v1` | Vault-specific non-exportable key despite global alias namespace. Strong-only auth-per-use; preserve legacy key exclusively for legacy entry pending explicit reenrollment. |
| Controller `vaultId` at [775, 833–840](../app/src/main/java/org/totipo/android/AndroidVaultController.java#L775) | Session/presentation cache, not persistent; set on opened(), reset on close. Derive authoritative identity from authenticated session. |
| Binding status, generations, lastSyncResult, importIntegrityAttention, syncPending/providerActive | Session/selection-specific, recomputable; do not persist as authority. Only pending-publication hint currently persists. |
| Passwords, enrollment buffers, builders, handles, drafts, token bases, reveals, deadline | Session/workflow-specific; never registry content. Discard on retirement; deadline remains monotonic in memory. |
| Search, adding/managing/joining flags, rendered rows | Transient Activity presentation; clear when selection changes, even if a TokenId happens to match. |
| Clipboard marker/text ([PlatformCodeClipboard.java:9–45](../app/src/main/java/org/totipo/android/PlatformCodeClipboard.java#L9)) | OS-visible, session-owned temporary exposure, not registry persistence. Clear only if still owned; Android cleanup is best effort, with no clipboard CAS. |
| Enrollment Bundle `consumed` ([OtpAuthEnrollmentActivity.java:175–178](../app/src/main/java/org/totipo/android/OtpAuthEnrollmentActivity.java#L175)) | Recreation marker only; no secret or vault selection authority. Expire imported drafts rather than replaying them. |
| SDK/runtime policy, branding, fixed 15-minute timeout | Application-global source configuration. No user-persisted settings for these found. |
| Proposed registry version, entries, last-selected local ID | Application-global **local-only metadata**; individual entry attributes are vault-specific. Last selection is a highlight preference only. |

Use a small versioned, bounded, strictly parsed registry under noBackupFilesDir,
written atomically with checked success, serialized by the Application owner.
Suggested entry: opaque local ID, user label, fixed location kind (`legacy` or
`managed`), lifecycle marker (`pending` or `established`), optional last verified
VaultId, optional binding metadata and retry hint (or an explicitly scoped port
file). Last selected ID and completed legacy-adoption marker belong to the registry.
Keep one authority for each field; do not mirror mutable binding state in both
registry and preferences. Simplest eventual storage is registry-owned binding
metadata behind the existing Port interface, with the legacy preference adapter
used only until migration commits. Credential bytes remain separate.

A generated random local entry ID is a storage/UI identity. It is **not** the
protocol VaultId, which identifies the exact immutable VAULT and is shared by
replicas of that cryptographic vault. Choose a reserved stable local ID for the
legacy location; never recycle forgotten IDs. Labels need not be unique and
must not become filesystem or key aliases. Local IDs must have a strict grammar;
managed locations derive from them, with collision refusal.

Creation/validation rules:

- Commit a pending entry/location reservation before Create or Join. A crash after
  VAULT installation must leave discoverable data, not an unregistered orphan
  silently deleted on next launch. A pending marker does not authorize overwriting.
- New local Create uses Java's fresh generation; independent vaults receive
  independent credentials/root material. Join copies only the authenticated exact
  provider VAULT via existing enrollment. It intentionally represents an existing
  cryptographic vault, not a newly independent identity.
- Promote after successful normal open/create and record session.vaultId. On uncertain
  creation or interrupted Join retain the entry and inspect/open existing evidence;
  never repeatedly create because metadata still says pending.
- Structural `Totipo.vaultId` on bounded canonical bytes is recognition only.
  Authenticated normal open establishes the session used by the app. Neither a
  cached expected ID nor a registry “established” bit proves current validity.
- Reject duplicate local IDs/locations and overlapping roots. Do not automatically
  coalesce two different roots with the same observed VaultId: they may contain
  different histories. Recommend one normal registered entry per verified VaultId;
  block duplicate Join/adoption and offer existing entry/recovery. Preserve both
  roots if a conflict is discovered after data already exists. No copying, merging,
  overwriting, or biometric reassignment follows a duplicate.
- Expected-ID mismatch on an established entry blocks OPEN exposure and automatic
  Sync, closes the candidate, and enters explicit recovery. Password success does
  not silently rebind an entry, provider, or biometric credential to a new identity.
  This extends the existing biometric mismatch behavior to selection integrity.

## 5. Legacy migration, recovery, and backup

**Physical vault migration is unnecessary and not recommended.** Register the
existing `totipo-vault` as a reserved legacy location. No rename/copy/delete of
VAULT, objects, or staged evidence is needed. This preserves current Java root
semantics and avoids a new crash-sensitive data-migration algorithm.

Proposed crash-safe, idempotent metadata adoption:

1. With no live selection, inspect registry state and legacy location using bounded
   read-only discovery. Distinguish absent registry from invalid/unreadable registry.
   Do not first invoke an acquire path that creates a missing established root.
2. If adoption has not completed, reserve the fixed legacy entry whenever legacy
   data or legacy configuration exists. Unknown, unsafe, orphan-object, or unavailable
   evidence stays recoverable; absence of usable VAULT does not prove disposable data.
   A genuinely empty installation gets initial setup, without a phantom established vault.
3. Preserve old preferences and biometric file/key while atomically committing the
   registry entry, legacy binding/retry values, and adoption marker together. Check
   write completion. Treat a malformed old binding as access-needed, not a new URI.
   Read platform grants; do not revoke/reacquire them as a migration side effect.
4. Retain v1 biometric storage through a legacy-only adapter, so no password decrypt,
   key export, or weaker authorization is needed for migration. Normal expected-ID
   checks still apply before exposure. Adoption can leave expected VaultId unset
   until real authentication; do not fill it from unchecked registry guesses.
5. Only after successful durable registry readback may the new schema become the
   normal binding metadata authority. Retain old migration inputs inert initially;
   never fallback to stale legacy preferences after a completed migration. A repeat
   startup with a committed marker does not overwrite changed per-entry settings.

Interruptions before the registry commit reuse untouched old state; after commit
use the committed schema. Atomic replacement must recover its prior complete file
on interrupted write; test platform AtomicFile behavior, write failures, and restart
at every boundary. AtomicFile is not evidence of arbitrary power-loss durability
of a transaction spanning registry, Keystore and permissions; avoid such a transaction
by retaining the legacy credential and grants. Persist adoption metadata before
performing any destructive cleanup. No cleanup is needed to make the vault usable.

Recovery rules:

| Failure | Required behavior |
| --- | --- |
| Corrupt/unknown-version/unreadable registry | Preserve file and roots; enter recovery, not empty setup. Offer legacy discovery and bounded enumeration of known managed directory names. Reconstruct candidates one at a time under the same admission gate; do not open multiple sessions to list entries. |
| Registry entirely lost | Discover reserved legacy and managed locations without writing protocol data; obtain labels again. Do not infer binding from available SAF grants or credentials from similar IDs. Explicitly rebind/re-enroll where association cannot be proven. |
| Missing established root or absent VAULT with object evidence | Keep entry and show unavailable/recovery. Never create a replacement identity in the remembered location. Preserve orphan evidence and Java OBJECT_DATA_OBSERVED veto. |
| Registry points to wrong VAULT / duplicate identity | Close and quarantine entry for owner resolution. Never treat registry as authority over bytes or merge histories automatically. |
| Pending allocation with no usable VAULT | Offer retry only after safe inspection; uncertainty may have installed VAULT. Preserve nonempty evidence. A truly empty allocation can be explicitly abandoned. |
| Metadata write fails after successful Create/Join | Report local data exists but remembering it failed; retain reservation/location and recover it next launch. Never undo a successful vault creation to “roll back” metadata. |
| Forgotten entry | Recommended “Forget” removes selection/configuration/credential association after retirement; retains canonical encrypted data. Keep a detached marker/location record for recovery so legacy adoption does not silently resurrect it. Secure deletion is a separate explicitly authorized workflow. |

Current [manifest:4](../app/src/main/AndroidManifest.xml#L4) has allowBackup=false;
canonical vault and credential files use noBackupFilesDir. Keep registry and
last-selection there too; do not add cloud, shared-folder or protocol registry
files. Legacy SharedPreferences are private app data but rely on the backup policy;
new registry placement makes local-only intent explicit. Normal uninstall/data
clear loses private vaults and registry; external Syncthing copies are unaffected.
Do not promise recovery of a local-only vault after uninstall. Reinstallation
must require password Join and fresh permissions/enrollment; registry restoration
cannot reconstruct local root secrets, Keystore authorization, or SAF authority.
Device/backup qualification must verify supported platform behavior rather than
claiming every OEM transfer mechanism obeys a manifest flag.

## 6. Vault-specific synchronization and retirement

Reuse AndroidVaultController's Sync chain and ForegroundVaultCoordinator, supplying
an immutable per-entry Port/binding. SyncFolderBinding already takes a Port; most
configuration isolation belongs in construction and persistence addressing.
Provider recognition/planning/import/publication need no multi-vault algorithm.

Current protections worth retaining:

- [Controller:793–823](../app/src/main/java/org/totipo/android/AndroidVaultController.java#L793)
  gates automatic/manual Sync on a remembered URI, coalesces one active plus one
  pending, and avoids a failure retry loop. Open, foreground and successful local
  mutation trigger requests. Foreground here is existing event policy, not a new
  background scheduler; do not change it incidentally.
- [Controller:853–889](../app/src/main/java/org/totipo/android/AndroidVaultController.java#L853)
  imports through the live session, checks safeToPublishAfterImport, awaits Java
  observation, then publishes. [Coordinator:209–243](../app/src/main/java/org/totipo/android/reconcile/ForegroundVaultCoordinator.java#L209)
  has two distinct Finished waits, 30-second bounds and cancellation checks.
  Preserve this implementation and every pinned F08 assumption: same session,
  successful import before baseline, one coordinated root, bridge released before
  Java calls, positive demand, terminal/error/cancel/timeout refusal, final policy checks.
  Refresh return is not completion or request-correlated acknowledgment.
- [ProviderVaultIdentity.java:22–37](../app/src/main/java/org/totipo/android/provider/ProviderVaultIdentity.java#L22)
  requires complete root coverage, exactly one valid candidate, structural identity
  equality with the session. Duplicate identical VAULT documents also block.
  Folder READY means access, not identity/authentication/freshness.
- [Controller:986–1046](../app/src/main/java/org/totipo/android/AndroidVaultController.java#L986)
  captures binding/session generation and cancellation, fresh preflight, detached
  snapshot, readback/postflight. [ProviderObjectWriter.java:25–59](../app/src/main/java/org/totipo/android/sync/ProviderObjectWriter.java#L25)
  checks cancellation around create/write/readback, but an entered write can finish.

Local-only behavior needs a small explicit policy change. Local authoring already
uses only the private session ([coordinator:270–303, 356–401](../app/src/main/java/org/totipo/android/reconcile/ForegroundVaultCoordinator.java#L270)).
An unconfigured binding restores NOT_CONFIGURED and does not probe. However
[controller:784–791](../app/src/main/java/org/totipo/android/AndroidVaultController.java#L784)
unconditionally sets pending-publication and “Changes not synced” after local saves.
For an intentionally local-only entry, keep normal Create/Add/Edit/Delete/Resolve,
suppress provider admission and sync-error wording, and avoid a pending transport
hint. Permit attaching a folder later; the first bound Sync compares immutable
local evidence regardless of any old hint. A disconnected entry likewise must
not pretend a pending hint proves remote state. Use an unconfigured binding object
with the scoped Port if needed to retain Manage sync folder; no actual remembered
provider binding means **zero provider operations**, not even startup scans.

Each synchronized entry gets its own URI, retry hint, and remembered binding.
A's scan/plans/results belong to A's immutable entry ID, controller/activation
identity, binding request, and session generation. Existing Request(generation,
uri) alone is not globally unique across replacement binding objects. Capture the
controller/activation identity at every Application/UI delivery boundary; do not
route a late callback through `application.currentController()`.

SAF grants require modest adapter changes: current choose/disconnect unconditionally
release the previous URI. AndroidSyncFolderPort must consult all registry references
and active requests before releasing read/write capabilities. Compare granted URI
identity, and retain a grant conservatively where overlapping tree capabilities
or aliases cannot be proved independent. Reject obvious reuse of one folder for
different verified VaultIds; fresh immutable VAULT recognition still protects
against URI aliases, replacement and externally changed folders. Do not broaden
permissions or assume Syncthing folder names prove identity.

Switch retirement must:

1. Stop new automatic/manual Sync and binding commands; clear pending logical
   requests; cancel import/observe/outbound/Join/Initialize continuations.
2. Retire worker-owned Join passwords, detached candidates, plans, readbacks,
   pending publication completions, and all scheduled handoffs. An in-flight
   local import/save finishes under A's ownership; cancellation is not rollback.
3. Close A's session/domain through the normal close path and retain failed-close
   ownership. Preserve A's retry hint conservatively if completion is uncertain.
4. Stop provider submission and wait asynchronously for every accepted A provider
   task to finish and close its cursors/streams/output. Also drain/discard resulting
   worker and main handoffs; no finishOutbound may restart a provider probe during
   retirement. Count pending tasks through callback handoff, not merely providerActive.
5. Only then release selection admission. Never build a fresh provider executor
   beside a stuck old one to make switching seem successful.

This strengthens switching, while ordinary inactivity/manual Lock can preserve
its current immediate-sensitive-retirement/nonwaiting-provider behavior. “Cancel”
cannot undo a remote create/write already entered; incomplete remote evidence and
uncertain local persistence survive switching. Reopen A and run the existing fresh
Sync workflow to reconcile; never reinterpret those bytes as B's work.

## 7. Single-active-session and Activity lifecycle

Prefer **reusing the controller class, constructing a fresh instance per selection**.
Its final owner/binding and numerous mutable selection fields make in-place retargeting
riskier. Add a small Application-owned selection state and asynchronous controller
retirement completion; do not replace the controller/coordinator operation model.
Application lifecycle callbacks must route to the selected controller only, and a
new controller must receive the actual current foreground value (no assumption
that a new Activity-start event will arrive).

Proposed state sequence:

```text
SELECTED(A, LOCKED/OPEN/OPENING/CREATING/JOINING)
  -> RETIRING(A)
  -> SELECTING (no session, storage domain, provider work, or credential buffer)
  -> SELECTED(B, CHECKING/LOCKED)
  -> normal password/biometric authentication -> OPEN(B)
```

Change Vault clears visible sensitive material immediately, before asynchronous
close. Cancel biometrics/enrollment, clear password/setup forms, pending Edit/Delete/
Resolve choices and external enrollment drafts, conceal reveals and retire owned
clipboard content. Remove A's row/search/status cache from all Activities. Await
main-thread cleanup acknowledgment before exposing B. Already entered Java
subscriber callbacks may finish later; “retired” means they have no mutation or
presentation authority, not a promise that common-pool callbacks are joinable.

Use existing requestLock/afterExecute/closeOwned ordering
([controller:1203–1236](../app/src/main/java/org/totipo/android/AndroidVaultController.java#L1203)),
but introduce a distinct retirement purpose: successful switch closure must not
call discover on A again (current retireRequestedLock does). Switching must also
work while LOCKED/STARTING/UNLOCKING/CREATING/Join is active; public lock currently
accepts only OPEN/BUSY/ERROR_OPEN/FAILED_CLOSE. A new retirement command must
cover all states and wipe rejected or cancelled buffers. Preserve ongoing
operation ownership rather than interrupting KDF/save and assuming nothing persisted.

Do **not** treat existing `shutdown()` as a completed switch. At
[1166–1176](../app/src/main/java/org/totipo/android/AndroidVaultController.java#L1166)
it requests lock, invalidates results, and shuts executors without awaiting
provider IPC; it offers no completion/failed-close switching contract. New
retirement should stop admission first, retain executors long enough for cleanup
and retry-close, then terminate them after drain. Mark callback delivery inert
before destroying old controller references. Retain the old controller while
closure fails, even if its ordinary screen is gone.

Preserve these guarantees:

- Inactivity remains exactly 15 minutes from explicit user interaction/open,
  using elapsedRealtime. [InactivityLock.java:7–39](../app/src/main/java/org/totipo/android/InactivityLock.java#L7)
  carries one generation-guarded deadline; background time counts. Recheck on
  Application foreground and Activity resume before reading/rendering content.
  Rotation neither closes nor reauthenticates nor resets the deadline. Selection
  navigation with no open session needs no inactivity timer.
- `observationOwner` is retained until actual close success; [signal:459–464](../app/src/main/java/org/totipo/android/AndroidVaultController.java#L459)
  compares coordinator identity under the monitor before any mutation. Keep that
  check and request/presentation generations. New VaultState alone is not generic
  cancellation. A descriptive projection may remain valid but becomes irrelevant
  to a retired selection.
- Activity reattachment needs an Application selection snapshot/listener, not a
  permanently cached initial controller. Bind/unbind controller listeners as selection
  changes and tag delivery with activation identity. MainActivity currently obtains
  its controller only once and its folder result uses the current Activity field
  ([407–411](../app/src/main/java/org/totipo/android/MainActivity.java#L407)). Tag
  folder pickers with entry/activation/request identity across recreation; reject an
  A picker result after switching. It must never configure B or take new grants.
- OtpAuthEnrollmentActivity may exist beside MainActivity. Pin drafts to their
  original activation and expire them on switching; never carry incoming setup
  secrets through selection or silently add them to a newly active vault. No
  external intent chooses/unlocks a vault or becomes an alternate session owner.
- [TotpPresentation.java:68–81](../app/src/main/java/org/totipo/android/TotpPresentation.java#L68)
  clears only its own clipboard marker; wait for requested main cleanup on switching,
  while preserving the existing best-effort Android clipboard limitation.

**Close failure:** keep RETIRING/FAILED_CLOSE with neutral sensitive-free UI and
explicit retry. A session already closed but a failed domain close still owns its
lease. Neither a timeout, a new root, nor user selection releases that ownership.
B is unavailable until closure and provider drain succeed. A hung provider may
require actual process restart, not Activity recreation or an extra executor.

**Process death:** memory sessions/handles/executors disappear; no session is
reconstructed from Bundle or registry. Startup recovers metadata reservations and
shows setup, locked selection, or recovery. Multiple entries return to selection
with last selection highlighted; never auto-open B using A's prompt. Uncertain
writes may have persisted; reobserve the selected root/provider using normal Java
semantics. Do not persist a pending switch as permission to bypass authentication.

Operation taxonomy remains explicit: selection/close/open are G; token projection
A, reveal B, builders C, refresh D, merge gate E, frozen publication/retry F, candidate
validation H. Sync composes transport, H validation, coordinated import, D observation,
and publication. Global switch admission is Android lifecycle policy, not a rule
that all Java calls require BUSY or a newer state invalidates every operation.

## 8. Independent biometric credentials

Current credentials use one `biometric-password-v1` file and fixed
`org.totipo.biometric-password.v1` key. Enrollment calls delete first, so merely
constructing another helper with the current implementation would replace the
other vault's credential. Current AAD covers context/version/alias/VaultId; it has
no local entry identity. Current [BiometricUnlock.java:13–73](../app/src/main/java/org/totipo/android/BiometricUnlock.java#L13)
has Activity prompt generation, one-shot operations, 60-second expiry and password
fallback. [FrameworkBiometricPrompt.java:13–30](../app/src/main/java/org/totipo/android/FrameworkBiometricPrompt.java#L13)
verifies the exact CryptoObject Cipher and requires strong biometrics.

Proposed minimal v2 isolation:

- Construct AndroidBiometricCredentials for one immutable local entry. Derive
  file path and alias from a strict local ID and fixed version/prefix, never a
  user label, URI, VaultId from untrusted input, or record-supplied arbitrary alias.
  Each entry gets its own AES-GCM auth-per-use non-exportable strong-only key.
- Authenticate envelope context, schema/key version, expected derived alias,
  local entry ID **and** exact VaultId in AAD. Bound decode as today. Swapping
  credential files between entries must fail, even for duplicated vault bytes.
- Normal password authentication proves the selected entry's actual session ID
  before enrollment. Enrollment callback must also match entry/activation and
  intended session, not just “controller state is OPEN.” Disable/invalidation
  deletes only that entry's record and key; cancel its outstanding prompt first.
- Biometric unlock decrypts only the selected entry's credential and uses the
  same normal password-open path. Compare authenticated session VaultId against
  credential ID and any established expected ID **before** opened(), observation
  exposure or auto-Sync. Also require the same selected local entry/activation.
  A success for A cannot unlock B, even when passwords are equal or a prompt
  callback arrives late.
- Switching, Activity stop/recreation and expiry invalidate the prompt generation,
  wipe enrollment/decrypted buffers and cancel controller enrollment. A recreated
  Activity may offer a new prompt for the selected locked vault; it never reuses
  authorization. No prompt or password survives into selection.
- Missing/invalidated key, malformed record, tag failure or invalid password leaves
  password fallback. Retire only the affected unusable credential; cancellation,
  temporary unavailability and biometric lockout do not disable other entries.
- Forget requires complete retirement, then per-entry key/record cleanup with
  checked outcome. Incomplete cleanup needs a retryable tombstone/cleanup marker;
  do not reuse the ID or describe failure as successful credential removal.

Legacy enrollment can remain readable by the reserved legacy entry alone using
its original fixed alias/AAD. Do not reinterpret v1 ciphertext as v2, copy it to
another entry, export a key, or decrypt without a fresh authorized operation.
After password unlock offer explicit v2 reenrollment, and retire v1 only after
new credential persistence succeeds and old key deletion is verified/retryable.
Cancelling upgrade preserves usable old enrollment. If association cannot be
proved after metadata corruption or VaultId mismatch, require password and fresh
enrollment; vault data remains intact. The password is always independently usable.
No biometric changes are made in this milestone.

## 9. UI/navigation proposal

Implement selection above the existing MainActivity state-to-surface shell; retain
normal token views/forms for one selected controller. The desktop conceptual model
in the request is a design input; no desktop source was changed or independently
audited here.

| Registry state | Entry presentation |
| --- | --- |
| Zero configured recoverable entries | Initial setup: Create local vault or Join synchronized vault. Storage/registry errors get recovery, never false zero-vault setup. |
| One established entry | Its locked/unlock presentation after safe discovery; provide Change Vault/manage entries so another can be added. |
| Multiple entries | Selection list, last-selected local entry highlighted. Labels/local-only vs synchronized/access-needed summaries; no token listings or live sessions. |
| Change Vault | Immediately retire sensitive UI, show neutral retiring status, then selection only after successful retirement. Even with one entry, explicit Change Vault stays in selection instead of bouncing straight back to unlock. |

Selection actions: choose remembered vault; create a **new local** vault; Join an
existing synchronized vault into a reserved fresh local root; rename/manage/forget
remembered entries. “Select existing local vault” normally selects a remembered
private root; recovery can rediscover valid managed/legacy locations. Arbitrary
filesystem import, protocol migration, deleting canonical data, and merging two
local replicas are separate workflows. A display label is convenience, not a
security identity. Show selected label/context in unlock, biometric prompt, folder
management and destructive confirmations so users know which vault owns an action.

Last selection is stored only after deliberate selection succeeds as a local
selection (authentication may still be pending); it never selects a key or proves
vault validity. Invalid/missing last ID falls back to an unhighlighted list. Local
Create never requires a folder; optional folder configuration/Initialize happens
for that selected entry. Join still authenticates detached exact VAULT, closes
its transient session, fresh-rechecks, enrolls create-only, then opens normally
([controller:625–650](../app/src/main/java/org/totipo/android/AndroidVaultController.java#L625)).
Application admission must include this transient validation session in the
**one VaultSession application-wide** rule. It is sequential with canonical open,
not an exemption for “temporary” sessions.

## 10. Principal risks and failure scenarios

| Risk | Required prevention / evidence |
| --- | --- |
| Independent owners admit A and B concurrently | Shared Application permit plus root lease; tests count live sessions, including detached Join and failed-close paths. |
| A provider completion accesses B | Immutable controller/root/port ownership, activation and session/binding guards, drain before selection; release late callbacks in deterministic tests. |
| Hung or partially successful provider operation | Lock still retires secrets; switching waits without blocking main; no replacement lane. Preserve uncertainty and data; restart is explicit recovery. |
| A picker/biometric/editor/enrollment completion applies to B | Pin every workflow to entry and activation; cancel/wipe on retirement; mutation-boundary checks, not only UI hiding. |
| Registry corruption overwrites old data | No fallback-to-empty setup, no physical migration, reserved pending allocations, discovery and explicit recovery. Registry is not evidence of validity. |
| Same root represented twice or symlinked/overlapping roots | Strict generated locations, component checks and process arbiter; reject duplicate ownership before any composition. |
| Same VaultId in two roots | Recognize duplicate cryptographic identity; retain both histories, block routine duplicate activation/enrollment pending explicit recovery. Do not merge or discard. |
| Disconnect A revokes B's grant | App-level reference/active-operation-aware release in Port; fresh permissions and immutable VAULT checks remain mandatory. |
| Credential transplant / enrollment overwrites other key | Per-entry derived namespaces plus entry/VaultId AAD and actual open-ID comparison; isolated delete and late prompt tests. |
| Empty/missing VAULT misclassified as new vault | Inspect namespace; retain orphan objects and uncertain writes; explicit allocation only for new roots. |
| Activity rotation or foreground reset extends exposure | Same selected controller/deadline across rotation, synchronous expiry check before snapshot display, no automatic reauthentication from saved state. |
| Local-only save looks failed because no transport | Preserve local save result; no provider calls and no unsynced warning for intentional local-only state. |
| Forgotten entry becomes inaccessible forever | Forget retains data/recovery locator; credential/permission cleanup status is explicit; no automatic local-data deletion. |

Concrete boundary limitations: Java coordinates per session, not independent
sessions/processes, so Application exclusion remains mandatory. There is no public
password-change/re-key/root-export or protocol-vault migration API to invoke;
none is required for independent roots. Java close invalidates builders and retry/
partial handles and waits for admitted configured-store operations; it does not
undo possibly persisted objects or synchronously retract subscriber callbacks.
Android already closes unused retry/partial handles in local authoring. Pending
refreshes may never run after closure. Preserve these outcomes rather than inventing
cross-session continuation. SAF IPC has no enforced completion deadline in the
current lane. These are visible limitations, not reasons to change Java/spec or
silently permit incomplete retirement.

## 11. Recommended small implementation milestones

All following items require separate implementation authorization. Each production/
test/build milestone follows the AGENTS qualification ladder: one ordinary baseline,
focused checks during iteration, final normal then strict offline clean, final two
APK verifiers/runtime boundary review, relevant device checks, freeze all included
inputs/tools, then one human `nix flake check`. No broad gate is requested for A-MV0.

### A-MV1 — Registry and legacy adoption, retain one selected vault

**Scope/components:** add small `VaultEntry`/`LocalVaultRegistry` data/persistence
abstractions; extend LocalReplicaOwner location validation and bound selection;
adjust TotipoApplication construction. Keep only the legacy entry selectable
initially. Preserve old sync port and v1 biometric adapter exclusively for it.
No Sync algorithm or biometric crypto/prompt internals need change.

**Compatibility/invariants:** register legacy path in place; pending reservation
and schema recovery; no data deletion or implicit identity replacement; one process
lease. Decide reserved legacy ID/schema before shipping data.

**Tests:** registry bounded/strict decode, duplicate IDs/roots/links/traversal,
atomic-write failure and restart at each adoption boundary; real Java legacy
Create/open, malformed/orphan/uncertain/missing-root cases. Extend
LocalReplicaOwnerTest, CoordinatedPrivateStoreTest and VaultEnrollmentTest.

**Device:** isolated legacy-upgrade/no-backup-root and restart recovery smoke;
compare canonical fixture bytes/identity before/after, preserve v1 credential.
**Dependency:** approved A-MV0 decisions.

### A-MV2 — Explicit complete retirement and selection ownership

**Scope/components:** AndroidVaultController retirement purpose/result, bounded
worker/provider drain accounting in ProviderIoLane, Application selection state;
listener/Activity and OtpAuthEnrollmentActivity ownership. Keep user selection
limited to existing entry while proving the boundary. Reuse closeOwned and
ForegroundVaultCoordinator; avoid touching Sync import/planning/barrier or
biometric key/envelope internals. Cancellation/drain of Sync scheduling is affected.

**Compatibility/invariants:** ordinary Lock remains responsive and keeps its
15-minute policy; switch requires actual completion; failed close retains owner;
no re-discovery during final retirement, no executor replacement on hung IPC.

**Tests:** latches at KDF/open, save, import, both observation waits, provider
probe/create/write/readback/postflight and callback handoff. Assert no second
session or root access until release; retry close failures; stale entered terminal
callbacks; repeated switch requests and shutdown. Extend AndroidVaultControllerTest,
SecurityControllerTest, InactivityLockTest and existing stale-owner regressions.

**Device:** lifecycle/security lifecycle phase, rotation/short and expired background,
external enrollment and clipboard/form retirement, blocked disposable provider.
No human fingerprint phase needed solely for an unchanged credential format,
but exercise prompt cancellation if Activity wiring changes it.
**Dependency:** A-MV1.

### A-MV3 — Per-entry binding/retry persistence and local-only policy

**Scope/components:** registry-backed scoped AndroidSyncFolderPort, grant-reference
release policy, SyncFolderBinding adapter integration, controller localMutationSaved/
status/admission. Add multi-entry fixtures without exposing incomplete multi-vault
UI. Leave ProviderVaultIdentity, planners, Java observation barrier and cryptographic
biometric internals unchanged.

**Compatibility/invariants:** idempotent adoption of old binding/retry hint; no
startup/provider operation without a binding; separate retry state; exact matching
VAULT required; immutable local evidence remains authority. No background inactive Sync.

**Tests:** real local-only create/add/edit/delete/resolve with a Port that fails on
any provider call; A/B bindings and equal generation values; retry-hint restart
isolation; grant reuse/disconnect/failed replacement; mismatched and duplicated
VAULT; stale A scan released after retirement; existing daily Sync/barrier tests.
Extend SyncFolderBindingTest, ProviderObjectWriterTest and controller Sync tests.

**Device:** two disposable Syncthing folders with distinct vault IDs, provider
offline/revoke/regrant, switch during active I/O, local-only mutations, restart;
prove zero cross-folder publication and unchanged local success on transport failure.
Use focused SAF/bootstrap/inbound/outbound harness scenarios; a full unrelated M3D
campaign is not automatic. **Dependency:** A-MV1–2. No biometric internals change.

### A-MV4 — Per-entry biometric namespace and legacy compatibility

**Scope/components:** BiometricCredentials interface as needed,
AndroidBiometricCredentials, BiometricRecord v2, BiometricUnlock,
FrameworkBiometricPrompt context, controller enrollment/expected-entry checks,
MainActivity construction. This is the first required biometric-internals change.
Sync algorithms remain unchanged.

**Compatibility/invariants:** legacy-only v1 adapter, explicit authorized upgrade;
per-entry key/record/AAD, no transplant or inherited authorization; password works
when any credential is missing/corrupt/invalidated; scoped forget cleanup.

**Tests:** extend BiometricRecordTest, SecurityControllerTest and source-boundary
tests with cross-entry swaps, equal passwords, duplicate VaultIds, delayed prompt
success after switch, invalidation/disable isolation, crash during enrollment/
upgrade/delete, no OPEN before expected ID validation, bounded buffer wiping.

**Device:** actual strong biometric enrollment/open for two entries, cancelled/late
prompts during switch/rotation/background, missing/invalidated key recovery, disable
one without breaking the other, process restart, legacy upgrade with original
password fallback. Extend security first/restart phases before final Nix freeze.
**Dependency:** A-MV1–2; integrate with A-MV3 before general multi-entry release.

### A-MV5 — Selection UI, Create/Join, and remembered-entry management

**Scope/components:** MainActivity shell and menu, Application selection listener,
entry-management views using existing native UI conventions; bind selected controller
and credentials. Add allocation/duplicate checks around existing Create and Join.
OtpAuthEnrollmentActivity must expire selection-owned drafts. No new general
navigation or vault/session framework, crypto, or Sync semantic rewrite.

**Compatibility/invariants:** zero/one/multiple startup states; explicit Change
Vault stays in selection; last-selection highlight only; fresh root for new vault;
Join transient session obeys app-wide exclusion; rename/forget do not mutate
canonical data. No cross-vault row/reveal/search cache. Preserve old one-entry unlock.

**Tests:** deterministic selection transitions, failed retirement, registry errors,
new entry reservation/crash recovery, duplicate Join, last-ID missing, delayed
picker results across recreation, external intents during selection, forget/recovery;
extend DailyDriverUxTest and Activity source guards plus controller tests.

**Device:** full focused two-vault user journey (local-only plus two synchronized
entries as fixtures), distinct credentials, switch/rotation/process death at every
boundary, 15-minute background expiry, biometric A never opens B, forget recovery,
SAF folder choice returning late. Include relevant enrollment/token UI smoke;
row harness only if row/view behavior changes.
**Dependency:** A-MV1–4. Multi-entry UI must not ship ahead of credential/Sync isolation.

### A-MV6 — Integrated migration/fault qualification and documentation

**Scope/components:** update README/architecture evidence and only necessary
multi-vault device harnesses under tools/device; fix included tooling before source
freeze. Avoid product changes after final gates; any such fix invalidates them.

**Tests/device:** consolidate deterministic failures and physical phases from prior
steps against final artifacts; clean-install, legacy upgrade, lost/corrupt registry,
interrupted allocation/adoption, process death while provider mutation is uncertain,
per-vault biometric fallback, two-folder identity isolation. Record exact counts,
fixture cleanup and supported-device limits. Harness-only changes follow actual
input dependencies; tools are Nix inputs even when not Gradle consumers.
**Dependency:** A-MV1–5. No additional crypto/Sync algorithm changes anticipated.

A-MV1 and local registry/storage work can be completed without changing biometric
or synchronization internals. A-MV2 preserves biometric crypto and Sync semantics
but must extend provider scheduling retirement. A-MV3 changes Sync configuration/
retry/grant policy only. A-MV4 changes biometric internals. A-MV5 should then be
mostly selection/navigation integration, not a place to defer isolation fixes.

## 12. Decisions requiring owner approval before implementation

1. Accept conservative complete provider drain on Change Vault, including a neutral
   waiting/error state and process-restart recovery for hung IPC. Opening B beside
   retired-but-running A provider work would change the stated requirement and is
   not recommended here.
2. Approve the reserved legacy ID, generated managed-root layout, registry schema,
   pending/established semantics, and atomic persistence/recovery format. Keep the
   legacy root indefinitely; no physical migration or automatic cleanup.
3. Approve one normal entry per verified VaultId and explicit recovery for duplicates
   or identity mismatch. There is no silent merge, replacement, or adoption of a
   different identity just because its password opens.
4. Approve Forget as metadata/credential/binding retirement with encrypted data
   retained and rediscoverable; deletion/export/secure erasure are separate work.
5. Approve legacy biometric compatibility until explicit reenrollment, with v2
   per-entry AAD/alias separation. Require password when association is uncertain.
6. Decide final user labels and whether last-selected highlight is recorded at
   selection or successful unlock (proposal: selection); neither authenticates.
   UI should explain intentional local-only mode without “Changes not synced.”

No owner decision is needed to finish this audit. These are implementation design
choices for the next authorization, not permission requests to modify code now.

## 13. Validation, input freeze, and final Git state

Validation performed: read-only source/report inspection and cross-checks, exact
pinned Java guidance download and SHA-256 match, released NIO composition/state
inspection, persistent-writer searches across main/release, package/check source
membership review, report source-link/line-reference checks, and `git diff --check`.
No runtime multi-vault behavior is claimed tested.

[package.nix:16–38](../package.nix#L16) selects app/src, gradle, tools and exact
build/metadata files; it excludes review/. [flake.nix:75–84, 108–143](../flake.nix#L75)
adds release-tooling and wrapper checks whose filesets also exclude this report.
[CI](../.github/workflows/ci.yml) retains package-inclusive Nix and unchanged-input
checks; nothing is weakened. This report is excluded from effective Android/check
inputs even if made Git-known. No Gradle, artifact, device, or Nix qualification
is necessary for this documentation-only change.

Input freeze disposition:

- Production source stable: no production changes from starting HEAD.
- JVM tests stable: no test changes from starting HEAD.
- Build/package configuration stable: no configuration/dependency changes.
- Qualification harness/tooling stable: no tools changes.

No included-input edits or qualification invalidations. Report-only edits do not
invalidate prior package evidence under the inspected filters. No new human Nix
result is required or claimed. No secrets, installed app data, or connected device
state were inspected or modified. Network inspection was read-only upstream source.

| Execution count | A-MV0 |
| --- | --- |
| Baseline full Gradle | 0 |
| Focused Gradle: test / compile / boundary | 0 / 0 / 0 |
| Final normal full Gradle | 0 |
| Strict-clean Gradle | 0 |
| External debug/release APK verifiers | 0 / 0 |
| Embedded Gradle APK verifier executions | 0 |
| Physical harness phases/runs | 0 |
| Agent Nix / human Nix | 0 / 0 |

Final intended Git state: branch `main`, HEAD unchanged; only
`review/A_MV0_MULTI_VAULT_ARCHITECTURE_AUDIT.md` added as an unstaged report
(intent-to-add source membership only; no staged file contents).
`git diff --cached --quiet` passes. `git diff --check` passes. No commit, tag,
release, push, or implementation change.

[guidance]: https://github.com/totipo-org/totipo-java/blob/f0a028676c1801a10b2d8d2650bf9c24357d1fa1/API_DESIGN.md
[composition]: https://github.com/totipo-org/totipo-java/blob/d6310c177ae930df188fd4f5798622c935698b2e/storage-nio/src/main/java/org/totipo/storage/nio/NioStoreComposition.java#L7-L23
