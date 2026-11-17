# A-BR-R4C — Android Add History from Backup

Status: implementation, final host qualification, human-reported Nix qualification
and targeted API37 physical acceptance complete. API30 and broader provider/device
coverage remain unexecuted as specified below. All changes are uncommitted.
No release preparation or publication is authorized.

## Starting boundary and classification

Classification before editing: **PRODUCT_CODE, UI_ONLY, LIFECYCLE_SECURITY,
TEST_OR_QUALIFICATION_TOOLING**. No dependency, Gradle, packaging, version, protocol,
cryptography, Java repository or Desktop changes.

```text
git branch --show-current: main
git rev-parse HEAD: f59e4d51b2cea82576e86c9a36e01a891c58afe1
git status --short: <empty>
```

UX1 is committed in the starting tree. There was no preexisting uncommitted work
to preserve. Reviewed AGENTS, MULTI_VAULT, architecture/user/development guides,
README, Java provenance, R0/R4A/R4B/UX1 evidence, package/flake/CI inputs and relevant
selection, controller, coordinator, store, provider-lane, Sync, retirement and
inactivity implementations. Inspection/logs live in ignored `.qualification/r4c/`.

## Shared Java authority and actual APIs

Runtime remains published Maven Central NIO/core **0.3.2**, BC **1.86**. No source
substitution or dependency refresh. Read the published source JAR and the exact
released contracts at `af11553659dd8bd4157064c7e3de27bed8980ac5`, together with the
required operation/observation guidance at
`f0a028676c1801a10b2d8d2650bf9c24357d1fa1`. Downloaded guidance SHA-256 is the
existing reviewed `338837e3563446f8ec11f7bb47c0e4f3ff5502b7608e397f9b8c4d75372715fd`.
Released API_DESIGN SHA-256:
`bf168036f9ac18a9d1f58a4ce6f4a5af0c2755941af1de6f5f8afff61ae5b4b4`;
BACKUP_ARCHIVE SHA-256:
`6886c99a5e87f8ebfc3716c77aadb378e6c2cb24a51f110063ba2cceecbd9918`.

Consumed APIs:

- `VaultSession.prepareImport(InputStream, BackupLimits.DEFAULT)`.
- Owned `BackupImport.summary()`, `apply()`, `close()`.
- Descriptive `BackupSummary.archiveSha256()` for recovery recognition only.
- `ImportResult.outcome()` and the released COMPLETE/NO_OP/PARTIAL/UNCERTAIN/FAILED
  classifications. No outcome inference from object counts or exception strings.
- Typed `BackupException.reason()` for different-vault versus verification failure.
- Existing session observation, `requestRefresh`, states and the existing Android
  `observeForSync` two-pass barrier, under the pinned composition assumptions.

Preparation performs complete bounded immutable ingress, exact authenticated
bootstrap matching, candidate authentication and target preflight. Apply performs
create-only publication and exact-existing acknowledgement with cumulative
knowledge. Java owns the ZIP profile, limits (4,096 objects / 6 MiB), crypto,
canonical IDs, publication and graph semantics. No Android parser, extraction,
object copier, overwrite, new assertion, rollback or automatic conflict resolution.
Java preparatory validation is secret-backed/provider-gated work; apply is frozen
publication, summaries are descriptive (A), observation requests D and session
closure G. These are not token merge E freshness operations. Archive-wide exclusion
is Android application/transport policy rather than a generic Java BUSY rule.

## UX, SAF and lifecycle

Selected-vault overflow exposes **Add History from Backup**, alongside Back up
vault. Admission requires the foreground selected authenticated OPEN session and
excludes another archive, token change or provider operation. It hides existing
code presentation. ACTION_OPEN_DOCUMENT requests only transient read access,
CATEGORY_OPENABLE and ZIP MIME; ActivityNotFound has an all-files fallback. No
persisted archive grant, tree binding, storage permission or network capability.

After complete Java preparation, exactly one native confirmation uses the requested
title, message and Cancel/Add History actions. No preliminary explanation, extra
review page, technical identity/count/hash display or ordinary success dialog.
COMPLETE/NO_OP and warnings use the existing non-modal live status area. Imported
codes are never automatically generated, revealed or copied.

One `HistoryImport` belongs to its original controller and session generation.
The existing worker owns stream ingress, preparation, handle methods, application
and disposal. The URI is opened once before confirmation and never reopened to
apply. Its authenticated Java plan owns frozen ciphertext. The task captures an
Application resolver, not an Activity. Null/zero-progress provider reads fail;
Java enforces byte/work limits. Stream close failure also rejects confirmation.

Activity recreation retains only the in-memory originating operation, unique
request code and already-returned URI, using the existing Pickers mechanism.
Duplicate callbacks are consumed once; retired/session-mismatched operations cannot
transfer to a new selection. Nothing is stored in Bundle. Process death requires
normal authentication, new selection of the ZIP and fresh complete preparation.
Ordinary state emissions never revoke a prepared import. Presentation projection
coalesces while archive admission is reserved, then resumes from ordinary Java
state; it never patches rows from import receipts.

Lock, inactivity expiry, backgrounding outside the picker and Change Vault revoke
future work immediately. The existing bounded worker retains the session and plan
until entered work and cleanup retire. The coordinated session facade refuses
future publications after cancellation with `ObjectWrite.Failed(UNAVAILABLE)`;
entered filesystem publication is not interrupted. Java accounts for all earlier
receipts, including uncertain ones. Completion may win late cancellation; no
rollback is claimed. Every terminal path closes the handle on the worker. There
is no separate executor/session/retirement manager. Retry uses a freshly selected
and authenticated original ZIP; the UI deliberately does not retain terminal
handles for live retry. Real Java same-handle retry is covered separately in tests.

## Sync and durable recovery boundary

The production controller always has the existing per-entry Sync metadata adapter,
including local-only configurations. Before applying, it synchronously commits
`history_import_recovery = authenticated archive digest` in the same per-entry
private preferences, independently of folder/grant keys. It is recognition evidence,
not an authentication capability or archive staging format. Failure to save it
prevents every import publication. Unknown/unreadable metadata fails closed.

This write-ahead marker covers process death at every apply/observation handoff.
It survives Lock, controller replacement, disconnect and folder changes. While
archive work is active, automatic/manual Sync and incompatible bridge work are
excluded by existing controller admission. While recovery remains, all outbound
paths (ordinary Sync, explicit publication and initialization) are blocked. No
outbound pass can take an imported prefix as a successful import. Provider events,
requestRefresh return and object counts never clear the marker.

After apply, refresh occurs outside bridge/storage ownership, including incomplete
or canceled applications. COMPLETE/NO_OP additionally require the existing two
fresh completed observation passes with no diagnostics/integrity problems and
current session authority. Only then can a successful durable clear release the
block. The existing `localMutationSaved` path supplies normal pending-publication
hints and ordinary import-before-publish Sync. No new transport publication path
or automatic uncertainty retry is introduced. The successful barrier uses the
pinned non-reentrant session, serial observation, immutable distinct state and
single whole-root owner assumptions; request/result correlation is not invented.

Local-only operation performs no probe/scan, grant acquisition or transport work.
A later configured folder still respects any retained recovery marker. Recovery
accepts only the original digest after fresh Java authentication. Diagnostic or
storage failures retain the block and evidence for explicit inspection. A missing
original ZIP or persistently unavailable/invalid store may require investigation;
there is no unsafe override, destructive repair or automatic marker dismissal.

## Result behavior

| Java result / boundary | Ordinary status and policy |
| --- | --- |
| COMPLETE | **History added.**; observation checked separately before Sync release |
| NO_OP | **No new history to add.**; same observation/recovery rules |
| PARTIAL | **Some history was added, but the import couldn't finish.**; original ZIP/app-data retention and explicit retry guidance; outbound block retained |
| UNCERTAIN | **Some history may have been added, but Totipo couldn't confirm completion.**; same recovery guidance, no automatic retry |
| FAILED | **History couldn't be added.**; conservative original-evidence recovery retained after entered apply |
| Preparation VAULT_CHANGED | **This backup belongs to a different vault.**; no application |
| Invalid/unsupported/authentication/read failure | **This backup couldn't be verified.**; no application |
| Observation exception after a returned result | Preserve Java's actual outcome message, append observation/recovery warning; do not relabel COMPLETE as UNCERTAIN |
| Unexpected application exception without result | Completion could not be confirmed; recovery required, without manufacturing a Java outcome |

Recovery copy explicitly says to keep the original backup and app data, unlock this
vault, select Add History from Backup with the original ZIP, and retry. It explains
that Sync publication stays blocked. User docs distinguish independent backup,
new allocation restore and additive history, including no rollback/removal of
newer objects and possible visible-state/conflict changes.

## Deterministic host evidence

`HistoryImportStoreTest` uses actual released Java sessions/plans and real coordinated
move-based NIO delegates. Trusted golden ZIP extraction is test fixture setup only.
It covers empty/exact-existing NO_OP, cumulative repeated apply and fresh-plan
idempotence, missing ancestors, newer descendants, and byte equality of every
preexisting canonical `vault` and object. Generated real Java branches exercise
conflicts, tombstones and two distinct equal-valued heads through normal observation.

Validation covers foreign bootstrap/vault, corrupt container, unsupported compressed
ZIP, a structurally valid CRC-correct archive with a late authenticated-invalid
object, wrong-kind/different/unavailable targets and changed targets at apply
preflight. Zero publication is asserted before full validation. Defensive bootstrap
substitution uses a test store seam, not a manufactured cryptographic collision.
Concurrent same/different-byte publishers test NIO no-replace behavior. Definite
prefix failure, persisted bytes with lost acknowledgement, actual NIO directory
barrier failure, explicit same-evidence retry, cancellation and concurrent session
close exercise honest persistence and preservation.

`HistoryImportControllerTest` covers frozen single ingress, zero local-only transport
and grants, duplicate/canceled/retired admission, recreation via listener ownership,
ordinary state emissions, blocked ingress plus Lock, entered publication plus Lock,
partial/uncertain explicit reauthentication, recovery save failure, observation
failure preserving typed success, process/controller replacement on the same retained
root, configured Sync exclusion and normal post-observation publication, and marker
survival across disconnect. Real NIO remains underneath the controller fault seam.

Related backup/controller/retirement/biometric/inactivity suites were included during
focused regression. A preexisting backup test assumed immediate admission despite a
queued observation; its helper now waits for actual command admission. Backup finish
now publishes FINISHED after outcome text and cleanup, preventing phase/message
publication races. No backup semantics or format changed.

Focused execution evidence (all logs retained):

1. `store-1`: test compile failed on an incorrect descriptor type assumption; changed
   tests to released `TokenStatus`.
2. `import-2`: 17 tests, one incorrect golden token-count assertion.
3. `import-3`: 121 tests, four failures: golden semantic assumption, two new setup
   admission timing assumptions, existing backup outcome publication timing.
4. `import-4`: 24 tests, one fault-placement assumption: failing namespace creation
   correctly returned FAILED, rather than UNCERTAIN. Fault moved after object entry.
5. `import-5`: 24 tests PASS.
6. `regression-6`: 157 tests, 156 PASS; existing backup immediate-admission assumption
   reproduced narrowly and corrected as above.
7. `import-7`: 34 tests, one new setup wait still used backup rather than import
   admission after tightening projection ownership.
8. `import-8`: 36 tests PASS.
9. `import-9`: final import/controller focused tests PASS, including additional
   unavailable-target and exact-bootstrap defensive checks.

No broad full gate was rerun to diagnose these focused failures. No clean or
rerun-tasks option was used during iteration.

## Final qualification and execution counts

Baseline `gradle check :app:assembleDebug :app:assembleRelease`: **PASS**, 1m55s,
90 actionable tasks (9 executed, 81 up-to-date). Managed Nix-provided Gradle 9.8.2,
JDK17 and SDK37; no agent Nix execution. `compile-1` focused product compilation PASS.
Documentation relative links and `git diff --check`: PASS.

Final normal, strict-clean, external APK/runtime evidence, physical results and
input freeze are pending completion and will be recorded before human Nix handoff.

First final normal invocation: **FAIL**, 3m23s, 535 tests / one source-guard
failure (`SyncSourceGuardTest.platformAndHonestUiBoundary`, line 53). MainActivity
had obtained the resolver directly. Moved the source factory into TotipoApplication
without changing stream/handle semantics or weakening the guard. Narrow reproduction
and correction: `guard-10`, 15 tests PASS, 4s. This production edit requires a renewed
normal checkpoint; no successful strict, artifact, device or Nix gate existed to
invalidate. The renewed normal command is `final-normal-2.log`.

Renewed final normal checkpoint: **PASS**, 3m30s, all **535 JVM tests** pass,
no failures/errors/skips; 90 tasks (21 executed, 69 up-to-date). Lint and both
Maven boundaries pass; debug/release APKs assemble. Complete XML retained in
`.qualification/r4c/normal-pass-tests/`. The single required renewed invocation
followed the concrete Application source-ownership correction above. The strict
command is now running once against the frozen included inputs.

## Remaining limits and environment scope

API30 hardware/emulator is unavailable in this environment. As agreed, that absence
alone is not a release blocker; it is not reported as a test PASS. API37 physical
coverage is recorded separately below. Cloud/removable providers, spoken TalkBack,
15-minute real-time expiry, OS late-result permutations and physical storage-fault
campaigns are not claimed by host tests. Existing biometric/inactivity coverage
and unchanged policies are distinct from new sensor/device qualification.

The original recovery ZIP must remain available. A retained marker with unavailable
or diagnostic local observation deliberately blocks outbound publication; recovery
requires successful original-evidence replay and ordinary observation, or explicit
investigation. No override or generic repair is supplied. Imported objects cannot
be retracted after entered writes. Third-party provider calls may block indefinitely;
retirement retains ownership rather than timing out and releasing a live owner.

No shared Java/protocol limitation required a bypass or dependency change. Existing
conditional observation composition supplies the needed local admission boundary.
It establishes no peer delivery, remote freshness, complete history or rollback
resistance. Archive authentication establishes neither producer identity nor
absence of omitted/newer history.

Strict offline clean gate: **PASS**, 3m58s, **92/92 tasks executed**, all **535 JVM
tests PASS**, no failures/errors/skips. Exact command:

```sh
gradle --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug :app:assembleRelease
```

One external final artifact set, against those strict outputs: **both PASS**.

```sh
python3 tools/verify-apk.py app/build/outputs/apk/debug/app-debug.apk --debug-probe
python3 tools/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk --unsigned --no-debug-probe
gradle --offline :app:dependencies --configuration releaseRuntimeClasspath :app:verifyMavenBoundary
```

Final SHA-256:

| Artifact | SHA-256 |
| --- | --- |
| Debug | `a78b19a4df75c02d87ab32e3a80b0ea91fe15aef3ab00e3969e363956aa43358` |
| Unsigned release | `505f498ba7e02209b9d3bcc81944585b5aad7d324919b1de360537d0dd51915c` |

Runtime graph/boundary command: PASS, 1s. Reviewed graph remains direct NIO 0.3.2 →
core 0.3.2 → BC 1.86, with strict matching constraints and no local replacement.
Full APK policy confirms source VERSION/VERSION_CODE, identity, SDK30/37, permissions,
components/exports/intents, backup and variant flags, branding and release/debug
exclusions. No manifest or build metadata changed. Gradle has no duplicate embedded
full APK verifier; external executions are exactly two.

## Physical qualification setup and retained failures

Available device: Pixel 6a, Android **17 / API37**,
`google/bluejay/bluejay:17/CP3A.261005.002.A1/2026111301:user/release-keys`.
The owner `org.totipo.android` package is not updated or exercised. Its starting
APK hash is `2a86bd0573ee58c91a71a26224ae202bf985d88abd8adc12c116cd6697d7cdcc`.

Standalone ignored instrumentation copies every production DEX/resource entry from
the final strict unsigned release APK and verifies exact byte equality. A scoped
manifest changes package/label, adds instrumentation and marks the disposable
package testOnly; it retains the production Application/Activity and non-debug
execution. This is isolated runtime evidence for exact final production code,
not installation of the owner's production manifest or a new release artifact.
Signing uses the existing validated LOCAL NON-PRODUCTION qualification identity
`d862bbf279d306e7062440d299440d54ecdf594e1cf8ac3046cdf5d3a0ed7f49`;
no key is generated/rotated. Receipt JSON records all copied entry hashes and source
APK hash. Product APK files remain unchanged.

Two initial physical history phases failed during setup before ZIP ingress. The
phone was still/suddenly behind keyguard; restore was canceled by the resulting
background transition. `history-1.log` and `history-2.log` retain the actual failures
at Browse all files. Added a disposable-window KEEP_SCREEN_ON flag and an explicit
interactive/unlocked-state preflight after wakeup; no persistent device setting or
product source changed. Fresh packages preserve the prior failed allocations and
evidence, without resetting owner data. Ignored harness development does not alter
any selected `tools/`/package/check input or invalidate the final host gates.

## Included-input freeze and human gate

- **Production source stable:** final Application source factory and archive/
  cancellation/admission changes precede renewed normal PASS and strict PASS.
- **JVM tests stable:** all 535 tests pass in the normal and fresh strict gates;
  no test edit follows either successful checkpoint.
- **Build/package configuration stable:** unchanged from starting HEAD, including
  Maven pins, locks, verification metadata, package source filtering and Nix inputs.
- **Qualification harness/tooling stable:** selected `tools/` files are unchanged.
  Disposable ignored instrumentation is outside Git source membership; its setup
  fixes neither change production bytes nor invalidate package/check qualification.

The Git-aware source rules were reinspected: app/src, gradle, tools and the exact
package build/version/license/provenance files are selected, excluding .git,
.gradle, build, .direnv and symlinks. package/flake/lock/cache evaluator inputs and
the release-tooling fileset were also inspected. The frozen 214-file included/
evaluator/release input hash inventory still matches. README, dependency prose,
architecture/user docs and review are outside effective Android/release-check
inputs. Later excluded report-only edits do not invalidate host or human Nix
qualification. No successful Nix result exists yet for this milestone.

All four new intended files are Git-known with intent-to-add only. No staged
contents; `git diff --cached --name-only` is empty and `git diff --cached --quiet`
passes. Ignored build/cache/device/signing/inspection state remains outside source
membership. Human `nix flake check` remains pending; the agent executes no Nix.

## Exact Git state and file inventory

Branch remains main; HEAD remains f59e4d51b2cea82576e86c9a36e01a891c58afe1.
All milestone changes are uncommitted. No version bump, release preparation,
commit, tag, push or publication. README unchanged. VERSION remains 0.2.4 and
VERSION_CODE remains 9.

`git status --short`:

```text
 M TOTIPO_JAVA_DEPENDENCY.md
 M app/src/main/java/org/totipo/android/AndroidVaultController.java
 M app/src/main/java/org/totipo/android/BackupExport.java
 A app/src/main/java/org/totipo/android/HistoryImport.java
 M app/src/main/java/org/totipo/android/MainActivity.java
 M app/src/main/java/org/totipo/android/TotipoApplication.java
 M app/src/main/java/org/totipo/android/reconcile/CoordinatedPrivateStore.java
 M app/src/main/java/org/totipo/android/reconcile/ForegroundVaultCoordinator.java
 M app/src/main/java/org/totipo/android/sync/AndroidSyncFolderPort.java
 M app/src/main/java/org/totipo/android/sync/SyncFolderBinding.java
 M app/src/test/java/org/totipo/android/BackupControllerTest.java
 A app/src/test/java/org/totipo/android/HistoryImportControllerTest.java
 A app/src/test/java/org/totipo/android/reconcile/HistoryImportStoreTest.java
 M app/src/test/java/org/totipo/android/sync/SyncFolderBindingTest.java
 M docs/ARCHITECTURE.md
 M docs/USER_GUIDE.md
 A review/BACKUP_RESTORE_R4C_ANDROID_HISTORY_IMPORT_REPORT.md
```

`git diff --name-only`:

```text
TOTIPO_JAVA_DEPENDENCY.md
app/src/main/java/org/totipo/android/AndroidVaultController.java
app/src/main/java/org/totipo/android/BackupExport.java
app/src/main/java/org/totipo/android/HistoryImport.java
app/src/main/java/org/totipo/android/MainActivity.java
app/src/main/java/org/totipo/android/TotipoApplication.java
app/src/main/java/org/totipo/android/reconcile/CoordinatedPrivateStore.java
app/src/main/java/org/totipo/android/reconcile/ForegroundVaultCoordinator.java
app/src/main/java/org/totipo/android/sync/AndroidSyncFolderPort.java
app/src/main/java/org/totipo/android/sync/SyncFolderBinding.java
app/src/test/java/org/totipo/android/BackupControllerTest.java
app/src/test/java/org/totipo/android/HistoryImportControllerTest.java
app/src/test/java/org/totipo/android/reconcile/HistoryImportStoreTest.java
app/src/test/java/org/totipo/android/sync/SyncFolderBindingTest.java
docs/ARCHITECTURE.md
docs/USER_GUIDE.md
review/BACKUP_RESTORE_R4C_ANDROID_HISTORY_IMPORT_REPORT.md
```

`git diff --cached --name-only`: empty. `git diff --cached --quiet`: PASS.
`git diff --check`: PASS. Inventory includes nine production files, four test
files, three documentation files and this report (17 paths).

## Pre-resumption physical boundary and current execution totals

The third package was built/installed, but its preflight positively found the device
still requires user unlock. **No third instrumentation phase was launched.** The
user has been asked to unlock the Pixel or explicitly defer physical testing.
No successful Add History physical journey, conflict/idempotence test or configured
provider case is claimed. Those scenarios are prepared in the ignored harness and
remain pending. This is a device-access boundary, not a discovered Java/Sync gap.
The two actual setup failures remain failures, not passes inferred from adb's
successful transport exit status. API30 is also untested, as recorded above.

Disposable packages org.totipo.r4chistory16/17 retain failed-setup evidence and have
been force-stopped; org.totipo.r4chistory18 is installed for the pending qualified
run. Owner data/APK are untouched. Before/after owner APK hashes match. The original
stay-awake setting remains unchanged; no font, night-mode, security or network
setting was changed. Only a unique public golden ZIP and an empty uniquely named
Sync test folder were added under Download, after proving the paths absent.
No existing public file was overwritten or deleted. The fixture's per-window
keep-awake flag is confined to disposable Activities. Final qualified APK hashes
and all included input hashes remain unchanged after fixture preparation.

| Execution count | Actual evidence |
| --- | --- |
| Baseline full Gradle | 1 ordinary PASS |
| Focused Gradle compilation | 1 PASS |
| Focused Gradle tests | 10 invocations; diagnosis/failure details above, final affected guards/import tests PASS |
| Final normal full Gradle | 2: first FAIL on architectural source guard, renewed PASS after narrow fix |
| Strict-clean Gradle | 1 offline clean PASS |
| External artifact verifiers | 1 final set, 2 commands, both PASS |
| Embedded Gradle full APK verifiers | 0, as designed |
| Standalone runtime graph / boundary Gradle | 1 PASS |
| Physical instrumentation phases | 9 total: 3 completed PASS, 6 retained setup/harness failures; detailed ledger below |
| Successful physical journey / restart / configured provider | 1 / 1 / 1, 216 checks across completed phases |
| Human Nix | 1 final human-reported PASS on frozen inputs |
| Agent Nix | 0, prohibited |
| Commits / tags / pushes / publication | 0 / 0 / 0 / 0 |

No successful host gate has been invalidated since renewed normal/strict PASS.
The only repeated full invocation was necessary for the failed source guard and
is explained above. Report-only edits after the freeze are excluded and do not
invalidate the pending human gate. Final whitespace and no-staged-content checks
PASS; exact Git inventory above remains current.


## Final human Nix result

The user reported **`nix flake check` PASS**. This is human-reported evidence;
the agent did not execute Nix. After receiving the result, verified that all 214
included/evaluator/release input hashes still match the frozen inventory. Branch
and HEAD remain unchanged; no staged content and `git diff --check` PASS.

This report-only update is excluded from effective package/check inputs and does
not invalidate that PASS. Earlier pending-Nix statements describe the prior
handoff; the human gate is now satisfied, with one human invocation reported.
All four included-input freeze statements remain in effect. Physical acceptance
is still pending device unlock; the Nix result does not supply device evidence.
Changes remain uncommitted, with the same 17-path inventory.

## Resumed API37 physical acceptance

The user requested resumption and explicitly requested keeping the device unlocked.
After ordinary wake/swipe, preflight confirmed `showing=false`,
`inputRestricted=false`, SCREEN_STATE_ON and INTERACTIVE_STATE_AWAKE. On the user's
follow-up, temporarily enabled plugged-in stay-awake: original value **0**, actual
framework-enabled value **15** on this OS. No lock credential/security setting was
disabled. Restore of the original value is recorded with final preservation below.

All resumed tests use the retained disposable package org.totipo.r4chistory18,
with the strict release production DEX/resources unchanged. Before each fixture
update, pulled its actual installed APK, verified its certificate with pinned
apksigner and compared it to the validated local qualification identity. Updates
replace only standalone instrumentation and preserve the private vault. No owner
package update, app-data reset, deletion or root replacement occurs.

The resumed history-3 run successfully selected the original golden ZIP through
real DocumentsUI (native search assistance for an offscreen file), restored it,
normally unlocked and created/read back an actual backup. It then failed waiting
for Edit. A narrow continuation made the reason explicit: the golden token already
has concurrent ACTIVE/TOMBSTONED alternatives. Android correctly refuses Edit for
that row. An initial handoff-timing suspicion was ruled out by that row-admission
check; no product defect or weakened admission followed. The continuation also
made ordinary explicit selection necessary after process restart, because the
retained initial reservation and restored entry correctly prevent implicit startup
selection. The harness now selects the retained entry through its normal UI row.

Kept all golden history intact and added a separate fresh token through ordinary
Add. Created a new independent backup rather than overwriting the earlier ZIP.
The completed continuation (`history-continue-2.log`) **PASS, 148 checks**, covers:

- Actual normal Add and verified native SAF backup of four encrypted objects.
- Normal Edit advances the fresh token; all five existing object bytes and exact
  canonical vault bytes are retained as the comparison baseline.
- Import the older backup: exact requested confirmation, Activity recreation on
  the same Java plan, injected duplicate Activity-result rejection, ordinary
  **No new history to add.** status and no rollback of the newer issuer/state.
- Build an independent concurrent branch from that actual backup with released
  Java in a separate disposable cache root. The helper authenticates/restores it,
  authors one branch and captures it with shared Java. A separate newly created
  provider document receives these explicit fixture bytes; this test-only write
  is not a product import/export path or replacement of a canonical target.
- Native picker → complete authenticated preparation → confirmation → **History
  added.**. The fresh token acquires the distinct newer/concurrent alternatives
  through ordinary Java observation. Assertions target this new token, rather
  than mistaking the preserved golden conflict for the newly imported conflict.
- Byte-for-byte preservation of all previously stored objects/bootstrap after
  both older and concurrent imports. Repeat import returns **No new history to
  add.** without removing the conflict or generating/revealing a code.
- Zero Sync binding throughout the local-only journey. Native picker cancellation,
  duplicate result rejection and Lock revoking an already prepared confirmation.
  Normal unlock preserves history, followed by another freshly prepared plan for
  the process-interruption test. No old operation is reused after Lock.

Cold process phase (`restart-1.log`): **PASS, 20 checks**. Explicit force-stop,
normal retained-entry selection and password unlock show no retained picker/plan.
Fresh native ZIP selection/authentication returns NO_OP and the new concurrent
conflict survives. There is no automatic continuation or imported secret reveal.

Pulled independent public test ZIPs into ignored evidence:

| ZIP | Objects | SHA-256 |
| --- | --- | --- |
| Actual fresh-token backup | 4 | `a27ebbf2439254871a96c323c0899de0f14eb7d6f4f635f5f52fc296597faea6` |
| Independent concurrent-branch fixture | 5 | `e09e6aafb1fc7f0e8aee20a6cf204c1ca363d8b94b772c47f3331434361eab72` |

Both carry exact vault SHA-256
`8bef25c2a2b81a0691b4f719eabed9c001a1778af415ddccab380eba9b408351`.
These are public/disposable test credentials and archives, not owner data.

The first configured-Sync attempt (`sync-1.log`) stopped in native tree navigation:
Download in the drawer exposed the storage root, so the harness needed an explicit
Download subdirectory click before selecting the uniquely named test folder. No
tree grant/configured publication had yet occurred. Corrected only that ignored
harness navigation and reran only the bounded configured-provider phase. No
production, JVM, build or selected tooling input changed; host/Nix PASS remains valid.


## Final physical results, preservation and qualification status

Configured provider phase `sync-3.log`: **PASS, 48 checks**. Selected the exact
uniquely named Download subdirectory through native ACTION_OPEN_DOCUMENT_TREE and
its Allow prompt. Asserted the configured URI names that disposable folder and
exactly one persisted tree grant exists. Explicitly initialized the empty folder
for this vault. A freshly selected/authenticated original branch ZIP returns NO_OP;
while its confirmation is active, Sync and token authoring admission are disabled.
After successful Java replay and ordinary observation, the existing local-change
path performs ordinary Sync, reaching **Verified against bound shared folder**.
Conflicts remain visible; no automatic reveal/copy or conflict resolution occurs.
This bounded configured case uses exact-existing history and publishes the local
history previously imported in the local-only case. It does not claim a new missing
branch import while already bound, peer delivery or Syncthing convergence.

An independent host pull of that actual local provider folder verifies exact
bootstrap equality and **six encrypted objects**. The ID set equals the union of
all five preexisting local objects checked before import and the independent
branch archive. Every archived representation matches the provider ciphertext
byte-for-byte. `provider-final-sha256.json` retains hashes of all seven canonical
provider files. This demonstrates the existing publication path with real
DocumentsProvider create/readback behavior, without touching an existing Sync tree.

The second configured attempt (`sync-2.log`) is retained as a harness FAIL: it
checked the still-disabled Use this folder button before subdirectory navigation
completed. The final harness uses the native accessibility click on the exact
subdirectory and waits for an enabled confirmation button. It does not weaken a
product/identity/storage check. Both configured failures preceded the folder grant
and any configured publication. No new product defect was diagnosed.

Complete phase ledger (all actual runs, including prior turns):

| Log | Result | Actual boundary |
| --- | --- | --- |
| history-1 | FAIL | Keyguard/background canceled initial restore setup, before ingress |
| history-2 | FAIL | Wake/swipe readiness still left keyguard showing, before ingress |
| history-3 | FAIL | Real restore/unlock/verified backup passed; harness incorrectly attempted Edit on the golden conflict |
| history-continue-1 | FAIL | Explicit ordinary selection/unlock passed; isolated admission assertion confirmed that golden row is a conflict |
| history-continue-2 | PASS, 148 checks | Fresh test token, verified backup, advance, older import, new branch/conflict, repeated NO_OP, preservation, lifecycle and callback checks |
| restart-1 | PASS, 20 checks | Cold process, fresh selection/authentication, retained conflict and NO_OP |
| sync-1 | FAIL | Native tree navigation stopped at storage root instead of Download subdirectory |
| sync-2 | FAIL | Native confirmation readiness check ran before directory transition completed |
| sync-3 | PASS, 48 checks | Exact disposable folder grant/initialization, authenticated NO_OP, admission exclusion and ordinary verified real-provider Sync |

Nine actual instrumentation phases: **three completed PASS / six retained harness
or setup failures**. The three successful phases total **216 checks**. The earlier
blocked third preflight is not a phase invocation. Fixture builds/installs 1–7 are
separate from product qualification: updates were needed for the recorded physical
harness diagnosis only, preserved the existing disposable root, and retained exact
qualified production bytes. There was no repeated product Gradle/artifact gate or
Nix invocation. `phase-ledger.json` records per-log result and digest; adb transport
success alone is never counted as a scenario PASS.

Final fixture APK SHA-256:
`c839e5155c6f531a1269c53fbebe45b26539e85259d774e65ad29fa1286d3a7d`.
Its payload receipt identifies strict release source SHA-256
`505f498ba7e02209b9d3bcc81944585b5aad7d324919b1de360537d0dd51915c`
and verifies exact equality of every original production DEX/resource entry.

After tests, force-stopped all three disposable packages and retained their data,
public ZIPs and the independently created Sync folder for review. The user-authorized
stay-awake setting was restored **15 → original 0**, with exact readback. The device
was awake and keyguard-free through the completed cases; no screen-lock credential,
font, theme, network or security setting was changed. Owner APK before/after stays
`2a86bd0573ee58c91a71a26224ae202bf985d88abd8adc12c116cd6697d7cdcc`.
No owner vault, existing provider folder or existing public file was altered.
Evidence: `final-preservation.json` and final awake-policy snapshot.

All **214 frozen included/evaluator/release hashes** and both strict APK hashes
still match. Only this excluded report changed among Git-known milestone files
during physical resumption; the 17-path uncommitted inventory remains exact. No staged contents; final whitespace
and cached-diff checks PASS. The human-reported Nix PASS remains valid and was not
requested again. Earlier pending-device/Nix paragraphs document their historical
handoffs and are superseded by the completed results here.

Targeted acceptance is complete for the documented API37/local-provider scope.
API30, cloud/removable providers, spoken TalkBack, real 15-minute expiry, biometric
hardware requalification, OS late-callback permutations and physical storage-fault
campaigns remain unexecuted. PARTIAL/UNCERTAIN and publication/lifecycle race evidence
remain deterministic host/NIO coverage, not new physical-fault claims. API30
unavailability alone is not treated as a release blocker. No release preparation,
commit, tag, push or publication is performed or authorized by this report.
