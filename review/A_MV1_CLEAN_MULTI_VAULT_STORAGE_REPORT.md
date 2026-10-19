# A-MV1 — Clean multi-vault storage

Status: **COMPLETE**. Implementation, host, device and final human Nix
qualification PASS. No commit, tag, release or push is authorized or performed.

## Scope and starting evidence

Classification: **PRODUCT_CODE + LIFECYCLE_SECURITY + TEST_OR_QUALIFICATION_TOOLING**.
The small UI changes provide startup/recovery diagnostics and disable production
biometrics; no token row/view layout changes. Started from clean `main` at
`804235bb295fb564608bcdf4d20c0fe021396cc4`; initial `git status --short` was empty.

Read AGENTS.md, README.md, TOTIPO_JAVA_DEPENDENCY.md, A-MV0, package.nix,
flake.nix, CI, controller/store/security/Sync code, relevant tests and qualification
harnesses, and the stale-subscriber and guidance qualification records. Read the
exact Java guidance at `f0a028676c1801a10b2d8d2650bf9c24357d1fa1`, downloaded
read-only; SHA-256 `338837e3563446f8ec11f7bb47c0e4f3ff5502b7608e397f9b8c4d75372715fd`
matches provenance. Included operation classes/state semantics, replay-latest,
editing, merge freshness, persistence handles, blocking/close and observation
ordering/composition. Also inspected the released NIO composition/files/store
sources at `d6310c177ae930df188fd4f5798622c935698b2e`. No other repository changed.
Runtime dependencies and protocol remain Java 0.2.0 / format v1/r19.

The owner's A-MV1 instruction supersedes A-MV0's reserved legacy root, adoption,
credential migration and compatibility proposals. There is no reserved entry,
legacy adapter, physical migration, or automatic data cleanup.

## Architecture and changed components

- `VaultRegistry`: small local-only registry, allocator and checked persistence;
  immutable entry values and ordered immutable snapshots, strict local IDs,
  optional bounded labels, pending/established markers, creation-attempt marker,
  last-selected ID, and explicit allocation recovery.
- `LocalReplicaOwner`: immutable selected ID/path, shared Application Admission,
  one exclusive storage/session lease, creation admission and authenticated
  promotion. `from(Context)` returns the Application's selected owner. There is
  no root-creating acquire, Path constructor or independently admitted production
  owner. Selection cannot be released or retargeted in A-MV1.
- `TotipoApplication`: one admission authority and one controller. Rejects legacy,
  invalid, missing or ambiguous storage before controller construction. Fresh
  empty registries reserve the first entry; multiple entries require later
  selection support. No automatic vault authentication or switching.
- `ForegroundVaultCoordinator`: existing coordinated Java store/session flow,
  with durable creation admission before Create/Join and authenticated promotion
  before returning OPEN. Missing established VAULT or uncertain empty creation
  returns UNAVAILABLE, never ABSENT. Promotion failure uses existing failed-close
  ownership and closes the real session before later recovery.
- `AndroidVaultController`/`MainActivity`: clear recovery/startup diagnostic paths.
  Existing state subscriptions, observation barrier, close-failure ownership,
  inactivity policy and provider scheduling remain unchanged.
- `AndroidSyncFolderPort`: existing port/algorithm with preferences addressed by
  strictly validated local ID (`sync_binding_mv1_<id>`). No old binding or retry
  hint is read or copied.
- `PendingVaultBiometricCredentials`: production password-only restriction.
  Existing biometric cryptography is unchanged and unreachable from the normal
  production Activity. No old record/key is read for unlock, reused or deleted.
- `VaultRegistryUiRegression`: ordered cold-process password, missing/corrupt data,
  legacy diagnostic, explicit repair and scoped Android Sync metadata checks in a
  disposable package using exact qualified release DEX.
- JVM fixtures and affected device fixtures now reserve managed roots through the
  same registry/admission APIs. Debug disposable creation is disabled: it must not create a synthetic
  session through a second admission authority beside the selected Application. Device security tooling adds a focused registry phase; historical
  global-biometric UI phases fail explicitly pending A-MV4.

## Storage layout and validation

```text
noBackupFilesDir/
  totipo-local-v1/
    registry                  # local metadata only; never a protocol store
    registry.next             # uncertain write evidence, never silently cleaned
    roots/
      <32 lowercase hex local ID>/
        store/                # existing Java canonical VAULT/objects namespace
```

Every entry, including the first, uses this fixed-depth layout. UUID-generated
opaque local IDs are unrelated to protocol VaultIds, passwords, labels and SAF
URIs. No serialized path/location kind exists. Unique IDs and fixed-depth derived
paths exclude equal/ancestor/descendant roots. Labels never enter a path.

The private base must exist and have canonical spelling. Every path component is
checked as a real directory without following links; metadata and temporary files
are opened without following links. The allocator creates individual directories
and refuses existing unsafe or uncertain evidence. Unknown root inventory blocks
startup. Missing registered roots remain registered and fail selection/acquire;
no acquire creates directories. Managed roots stay fixed for the owning controller.
Android's trusted application-data parent is canonicalized to handle platform
`/data/user/0` aliases; the no-backup directory itself and every managed descendant
must still pass no-symlink validation.

Trust boundary: app-private storage, one application process/writer, normal Android
UID isolation, no independent same-root writers. Checks do not claim protection
against a compromised app UID replacing paths concurrently or against arbitrary
filesystem rollback/power loss. Manifest process/backup policy is unchanged.

## Registry format and persistence guarantees

Schema 1 is a bounded binary envelope: fixed magic/version/count; ordered ID,
optional label, state and attempted flag per entry; optional last selection; and
SHA-256 over the payload. Maximum 128 entries, 128 UTF-16 code units per label,
64 KiB envelope. Strict booleans/enums, ID grammar, label text, count, duplicate
IDs, selection membership, checksum, truncation and trailing bytes are validated.
The checksum detects corruption; it is not authentication or rollback resistance.
No password, key, token data, Java authorization handle or protocol identity is
stored in registry metadata.

Updates verify the prior committed bytes, write a create-new temporary file,
fully write and force it, atomically rename it over metadata, force the parent
directory, and check exact bounded readback. There is no nonatomic fallback.
Only then is the in-memory snapshot published. Any write failure/uncertainty
poisons the writer instance. A surviving `registry.next`, missing registry beside
managed data, corruption, unexpected external edit or unsupported version enters
explicit recovery; there is no automatic rollback, reset, root deletion or retry.

Creation ordering:

1. Persist pending entry before creating its container/store directories.
2. Force new directory entries. A crash can leave a known pending entry with no
   root; explicit `allocatePending(id)` can finish only an unattempted empty
   allocation. Production startup never invokes that recovery automatically.
3. Before Create/Join, persist `creationAttempted=true` under the selected lease.
   Refuse established, previously attempted or nonempty roots before Java creation.
4. Reuse existing Java create/enrollment/open and coordinated store. Authenticated
   success promotes the entry before returning OPEN. Data is never rolled back
   because a metadata acknowledgement failed.
5. On restart, pending data with a VAULT is password-openable and can promote.
   Pending attempted data without a VAULT requires recovery, even if it looks
   empty. Established absent VAULT/root requires recovery; no replacement identity
   is created. Interrupted Join follows the same reservation/promotion boundaries.

The caller retains ownership on close failure. Registry-write failure does not
release the application selection or pretend provider retirement completed.
Selection/open/create/close are class G application lifecycle work. Metadata
ordering is Android policy; no change to Java operation freshness, BUSY policy,
observation completion, publication/retry or session APIs is made.

## Legacy behavior and intermediate restrictions

Before writing managed metadata, detect any `totipo-vault` path (even empty or a
symlink), old biometric file/base/backup/new files, old Sync preferences/base/backup,
or old Android Keystore alias. Show an incompatible-installation diagnostic and
preserve files, grants and keys. Registry-present installations also reject such
legacy evidence. There is no adoption, migration, credential compatibility or
silent fresh installation over old data.

Manual reset must be deliberate. First use the previous application to preserve
or synchronize every local vault and verify another copy. Clearing app data or
uninstalling permanently loses unsynchronized local vaults. The application never
clears data itself. README and the diagnostic state this risk.

Only one selected controller is exposed; multiple registry entries are supported
by storage abstractions and preserved, but production UI does not select/switch
among them yet. The selection authority remains held while locked, so releasing
a session lease cannot authorize a different-root controller. No simultaneous
sessions or inactive-vault Sync are introduced or qualified.

Sync algorithms/transport/observation are unchanged. Scoped preferences retain the
existing private SharedPreferences port and backup policy; complete registry-wide
SAF grant/retry management is deferred to A-MV3. They are not registry/protocol
metadata. Multiple-entry use is blocked before binding restoration. Biometrics
remain disabled in production until A-MV4 provides per-entry records/aliases/AAD.
Historical global cryptography tests remain regression evidence, not a supported
multi-vault biometric capability.

## Tests and qualification evidence

Focused tests cover empty/fresh registry, ordered multiple allocations, labels,
ID grammar/duplicates/overlap exclusion, path traversal and ancestor/store/metadata/
temporary symlinks, interrupted allocation, uncertain creation and restart,
missing established VAULT/root, corruption/missing registry, failed prewrite,
partial temporary file, lost postrename acknowledgement, unexpected live metadata
changes, reservation-before-data ordering, real Java create/open/pending recovery,
real promotion failure/close/reopen, one selected controller/one active lease,
stale lease close and credential-disabled behavior. Existing orphan/controller
regressions now assert earlier registry refusal and preserved bytes; lower Java
storage/session invariants retain their existing tests.

`VaultRegistryRegression` exercises sequential real Java create/open plus real
Android private-filesystem registry force/atomic move, restart reconstruction,
missing data, symlinks and corrupt/interrupted metadata in a disposable isolated
qualification package. No simultaneous sessions are supported by this harness.
All ten affected/new device fixture families compile against pinned Android and
Java artifacts. Security build-only compilation and the final four-family physical
harness packaging pass.

Final execution ledger:

| Gate | Count / result |
| --- | --- |
| Baseline full Gradle | 2 attempts: initial invalid; isolated original-HEAD PASS |
| Focused Gradle test / standalone compile / boundary | 8 / 0 / 0; run 2 had two behavior regressions, all other runs PASS |
| Final normal full Gradle | 3 attempts: first failed; second PASS then invalidated by debug admission fix; renewed final PASS (2m11s) |
| Strict offline clean Gradle | 2 PASS: first invalidated; final 2m19s, all 91 tasks executed, 358 tests, zero failures/errors/skips |
| Final external debug/release verifiers | 1 / 1, both PASS on final strict-clean outputs |
| Final offline runtime graph / Maven boundary | 1 invocation, PASS |
| Embedded Gradle APK verifiers | 0; none run by Gradle check |
| Physical harness phases | 10 runs: 9 PASS, 1 harness startup-order failure corrected and rerun |
| Agent Nix / human Nix | 0 / 2; first PASS invalidated by device tooling; final renewed PASS |

Initial baseline mistake: source edits and a focused Gradle invocation overlapped
the initial full baseline, producing a source-guard failure and a shared test-output
collision (`in-progress-results-generic.bin`). That invocation is invalid evidence.
A detached clean worktree at the exact starting HEAD ran the unchanged ordinary
full command successfully. No clean/rerun flags were used for baseline or iteration.
All subsequent Gradle commands in the working checkout run sequentially.
Focused run 2's only failures were old expected Java-create invocation counts and
orphan wording/state; registry checks now refuse before Java creation. Narrow
regressions passed before returning to the final gate.

The first final-normal attempt failed (354 tests, three failures): the unchanged
EnrollmentSourceGuard forbids generic exception `getMessage()` in controllers,
and two enrollment tests expected Join to inspect roots already containing data.
Standalone JUnit reproduced the source guard exactly. RecoveryRequired now exposes
only its fixed application-authored diagnostic through a typed accessor, retaining
the guard. Enrollment tests now assert preexisting-root refusal; a separate late-
evidence injection preserves the existing create-only/exact-equality/normal-open
regression after registry admission. Focused runs 6–7 PASS, including all enrollment
and registry regressions. This production/test correction required the second
final-normal invocation; no strict or artifact pass had yet occurred. Two extra
strict-codec tests check duplicate IDs and malformed metadata with valid checksums.

Logs are preserved under ignored `.gradle/amv1-qualification/`; the isolated
baseline ran in `/tmp/totipo-amv1-baseline` (336 tests, zero failures/errors/skips).
Its results were captured and the temporary worktree was removed.

## Physical device qualification

The owner connected a Pixel 6a, Android API 37. The disposable
`org.totipo.securityqualification` package was absent before preparation. One fresh
install and three harness updates used the local detached security-test identity;
the installed certificate was independently compared to that identity before
updates. The final installed harness retains every production DEX entry byte for
byte from the recorded strict-clean unsigned release APK. Its source-APK hash
matches the final release hash. No product rebuild, signing or update occurred.

| Physical run | Result / evidence |
| --- | --- |
| registry | PASS: real private-filesystem atomic registry writes, sequential Java create/open, reservations, immutable roots, missing VAULT/root, symlink refusal, corrupt/interrupted metadata |
| lifecycle | PASS, 51 checks: actual UI/controller, released Java, coordinated storage, Edit/Delete/Resolve and immutable file-transport Sync; one run, waiting for owner unlock before obtaining focus |
| mv1-first attempt 1 | FAIL in harness startup sequencing: Instrumentation.onStart read the Application controller before Application.onCreate completed; no test vault had been created |
| mv1-first attempt 2 | PASS, 22 checks: actual password Create UI, one established managed entry, Lock, empty sensitive snapshot, absent biometric controls, scoped Android Sync preferences |
| mv1-restart | PASS, 19 checks: cold-process locked startup, wrong password refusal, correct password open, same local/protocol IDs and registry bytes; known synthetic VAULT then moved aside under its lease |
| mv1-missing | PASS, 19 checks: cold-process recovery UI, missing established VAULT not recreated, Create rejected, explicit byte-preserving restore followed by normal password open; synthetic registry then deliberately corrupted |
| mv1-corrupt | PASS, 9 checks: cold startup has no controller, visible corrupt-registry diagnostic, metadata and managed VAULT preserved; exact saved registry restored explicitly |
| mv1-restore | PASS, 14 checks: restored registry opens the same vault; known synthetic legacy directory/file prepared only after Lock |
| mv1-legacy | PASS, 12 checks: no controller, visible incompatible-installation and unsynchronized-vault-loss diagnostics, exact registry/managed/legacy bytes preserved; only the verified synthetic legacy fixture removed |
| mv1-finish | PASS, 15 checks: final healthy cold restart, password-only unlock of the same managed vault, normal Lock and cleared snapshot |

The seven successful UI phases total **110 checks**. Each is a separate process
launch through the physical runner. Fault injection is confined to the fresh
qualification package's own public synthetic vault/metadata. Normal application
code does not perform the deliberate file movement, corruption or repair used by
this harness. The lifecycle peer fixture authors conflict evidence; it does not
qualify simultaneous application sessions or multi-vault switching.

The scoped preference smoke uses two generated test IDs and the real
AndroidSyncFolderPort: checked save, binding and retry-hint restore, no inheritance
by the second ID, no implicit SAF authority from a remembered URI, checked
disconnect and preservation of the other ID. All synthetic preferences are cleaned
up. No real SAF grant was taken, no provider folder modified, and no full external
SAF/Syncthing or row suite was rerun for this storage/configuration milestone.

Harness correction: a main-loop barrier now observes completed Application startup,
including intentional fail-closed startup with no controller. Activity lifecycle
callbacks hold FLAG_KEEP_SCREEN_ON while UI checks run, and a harmless modifier
key event bridges process phases before window closure. No device lock, timeout,
biometric or global runtime setting changed. The initial lifecycle run passed
without repetition after the owner unlocked the device.

Device preparation/build counts in this follow-up: one initial prepare/compile/
install, one UI build-only compile, and three compile/package/update operations
(add UI phases, correct startup ordering, and bridge screen-idle transitions).
These supplement the original security build-only and nine-family host compile.
Only qualification tooling changed; production/JVM/build inputs stayed frozen.
After all checks, the fresh disposable package was uninstalled once and its absence
verified. The temporary UI dump was removed. The installed user application,
its vaults, credentials, grants and files were untouched.


## Input freeze and remaining work

Final ownership review found that the historical debug disposable-create probe
could construct an independent fixture admission while the Application retained
its selection. That action was disabled and a focused debug regression added.
This debug APK/source input correction invalidated the prior normal and first
strict checkpoints. The debug regression passes (run 8), and both normal and
strict gates were renewed before artifact verification. No release behavior,
Java API or protocol change was needed.
Final normal gate PASS (2m11s). Final strict gate PASS (2m19s), **358 tests**,
zero failures/errors/skips; all 91 tasks executed offline with no configuration or
build cache and strict dependency verification. Both final APK verifiers PASS.
The offline releaseRuntimeClasspath and Maven-boundary invocation PASS: direct
NIO 0.2.0, transitive core 0.2.0 and BC 1.86, with unchanged strict locks.
App VERSION is 0.1.3 and VERSION_CODE is 4, both source metadata unchanged and
verified against packaged values. Manifest review and both verifiers retain the
single process, existing launcher/otpauth ingress exports, USE_BIOMETRIC-only
permission boundary, allowBackup=false, SDK/identity, branding and variant/debug
exclusion policies. No component, permission, export, SDK or version change.

Final strict-clean APK SHA-256:

- Debug: `096d7510fde7751e76152a461e50d865149829898ddbcedea4ef2b3d90d0419a`
- Unsigned release: `f9173e23b87209cfd1618595cc3ec2fa414b75702fefc5ac70aeb6cf84767eab`

Input freeze statements:

- **Production source stable:** final normal and strict passes, both final artifact
  checks PASS; no later production/resource/manifest edit.
- **JVM tests stable:** final 358-test suite PASS; no later test edit.
- **Build/package configuration stable:** unchanged from starting HEAD; offline
  strict build and runtime/Maven boundary PASS. No dependency or lock edits.
- **Qualification harness/tooling stable:** ten fixture families compile; registry,
  lifecycle and all seven ordered UI/recovery phases PASS against the exact final
  production DEX. No tooling edit after those physical passes.

All intended new files are Git-known through intent-to-add only. The final
included/evaluator/boundary input inventory in
`.gradle/amv1-qualification/included-inputs.json` contains 175 file hashes and
matches the final frozen inputs; the invalidated debug/test input hashes and
pre-device 174-file input inventory are retained separately. Git status, unstaged changed names, empty staged-content
name list and passing `git diff --cached --quiet` are recorded before requesting
human Nix. `git diff --check` PASS. Source, test, config and tool inputs are frozen;
only excluded report/README edits may follow without invalidating these gates.
Build/package, locks, dependency pins, manifest and SDK/version metadata are unchanged.
Actual package filtering selects app/src, gradle and tools plus exact build files;
review/ and README are excluded. Flake evaluator/release-tooling dependencies also
exclude these prose files. Report-only updates do not invalidate qualified inputs.
No agent Nix command is run; the human must run exactly `nix flake check` once
included sources and qualification tooling are frozen.

A-MV2 must add complete asynchronous controller/provider retirement with failed
close retention and handoff drain before any selection-release API. It must expire
old Activities, pickers, subscriptions, queued callbacks and credentials before
exposing a new controller. A-MV3 must complete per-entry Sync/grant/retry ownership;
A-MV4 must implement per-entry biometrics; later UI work must expose selection and
safe management/recovery. No forgotten-root deletion, arbitrary root adoption,
legacy migration or cross-vault merge is supplied here. Expected protocol-identity
persistence/mismatch and duplicate-Join policy remain later selection work; this
registry deliberately stores no protocol identity and does not authenticate bytes.

Final Git state: `main` at `804235bb295fb564608bcdf4d20c0fe021396cc4`;
27 changed files, all unstaged, including six intent-to-add new files. Staged-
content name list is empty and `git diff --cached --quiet` passes. No commit,
tag, release, push, installed-product update, application-data reset or user-data
mutation. The temporary baseline worktree and fresh disposable qualification
package were created and removed; their qualification output was retained.

Changed files:

```text
README.md
app/src/debug/java/org/totipo/android/DebugDisposableCreation.java
app/src/main/java/org/totipo/android/AndroidVaultController.java
app/src/main/java/org/totipo/android/LocalReplicaOwner.java
app/src/main/java/org/totipo/android/MainActivity.java
app/src/main/java/org/totipo/android/PendingVaultBiometricCredentials.java
app/src/main/java/org/totipo/android/TotipoApplication.java
app/src/main/java/org/totipo/android/VaultRegistry.java
app/src/main/java/org/totipo/android/reconcile/ForegroundVaultCoordinator.java
app/src/main/java/org/totipo/android/sync/AndroidSyncFolderPort.java
app/src/test/java/org/totipo/android/AndroidVaultControllerTest.java
app/src/test/java/org/totipo/android/LocalReplicaOwnerTest.java
app/src/test/java/org/totipo/android/TestReplicaOwners.java
app/src/test/java/org/totipo/android/VaultRegistryTest.java
app/src/test/java/org/totipo/android/reconcile/VaultEnrollmentTest.java
app/src/test/java/org/totipo/android/sync/SyncSourceGuardTest.java
app/src/testDebug/java/org/totipo/android/debug/AuthPerfSafetyTest.java
review/A_MV1_CLEAN_MULTI_VAULT_STORAGE_REPORT.md
tools/device/OtpAuthIngressRegression.java
tools/device/RowRevealRegression.java
tools/device/SafBootstrapRegression.java
tools/device/SafInboundRegression.java
tools/device/SafOutboundRegression.java
tools/device/TokenLifecycleRegression.java
tools/device/VaultRegistryRegression.java
tools/device/VaultRegistryUiRegression.java
tools/device/qualify-security.py
```

Human Nix history: the owner reported “nix flake check passed” on 2026-10-19,
against the earlier 174-file freeze. Duration was not provided; all frozen hashes
and both APK hashes matched when that result was recorded. This result was valid
then, and is now **invalidated only by included qualification-tool changes**:
`tools/device/qualify-security.py` and the added
`tools/device/VaultRegistryUiRegression.java`. package.nix includes tools/;
Gradle/build files and app tests do not consume these device harness files.
The invalidation matrix therefore requires the affected physical checks (now
PASS) and a renewed human Nix check, with no Gradle/APK rebuild solely for tooling.

Final human gate: **PASS**. After all device-harness changes and physical passes,
the owner reported “nix flake check passed” for the renewed `nix flake check`.
Duration was not provided. The agent verified all 175 frozen included/boundary
input hashes and both final strict-clean APK hashes still match before recording
this result. No Nix command was run by the agent. This update changes only the
excluded report and does not invalidate the final check. No required qualification
work remains for A-MV1; the later A-MV2–4 work described above remains out of scope.

Device gate: **PASS** on the qualified release DEX. For reproducibility, a fresh
isolated package can run the registry/lifecycle phases and all seven UI phases:

```sh
python3 -B tools/device/qualify-security.py --serial SERIAL --prepare
python3 -B tools/device/qualify-security.py --serial SERIAL --phase registry
python3 -B tools/device/qualify-security.py --serial SERIAL --phase lifecycle
python3 -B tools/device/qualify-security.py --serial SERIAL --phase mv1-first
python3 -B tools/device/qualify-security.py --serial SERIAL --phase mv1-restart
python3 -B tools/device/qualify-security.py --serial SERIAL --phase mv1-missing
python3 -B tools/device/qualify-security.py --serial SERIAL --phase mv1-corrupt
python3 -B tools/device/qualify-security.py --serial SERIAL --phase mv1-restore
python3 -B tools/device/qualify-security.py --serial SERIAL --phase mv1-legacy
python3 -B tools/device/qualify-security.py --serial SERIAL --phase mv1-finish
python3 -B tools/device/qualify-security.py --serial SERIAL --remove
```

Unlock the device before UI checks and run the seven UI phases in order. They
require their own preceding synthetic evidence and never reset a partial fixture
automatically. --prepare requires an absent disposable package; do not remove an
unreviewed preexisting installation. --remove cleans only this disposable package.
