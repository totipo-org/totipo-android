# BACKUP_RESTORE_R4A — Android backup export

Status: implementation, local Gradle/artifact qualification, reviewed dependency
cache refresh and final human-reported Nix qualification complete. Physical SAF
qualification and the API30–33 compatibility gap remain unresolved.
No Android release, tag or commit is authorized or performed.

## Scope and starting evidence

Classification before editing: **PRODUCT_CODE, UI_ONLY, LIFECYCLE_SECURITY,
BUILD_DEPENDENCY, TEST_OR_QUALIFICATION_TOOLING**. Scope did not expand to restore,
history import, schedules, cloud integration or protocol changes.

Started on clean `main`:

```text
git branch --show-current: main
git rev-parse HEAD: e6f323f25932ef47c9fa3b642803d55f3dc45c5c
git status --short: <empty>
```

All work and inspection downloads are confined to totipo-android. Other repositories
were read over HTTPS, never modified or checked out. Read AGENTS, README, USER_GUIDE,
MULTI_VAULT, ARCHITECTURE, DEVELOPMENT, Java dependency provenance, R0 design,
package/flake filters, CI, related tools and A-MV2/A-MV3/A-MV6 evidence. Inspected
selection, registry, private-root owner, coordinator, Sync bridge/provider lane,
biometric and Activity lifecycle implementations and their JVM seams. Read the exact
f0a028676c1801a10b2d8d2650bf9c24357d1fa1 Java guidance, including all required
operation/state-snapshot, observation ordering, replay, editing, merge, persistence
and close sections. Also read released Java R1–R3 reports, the frozen profile-1
contract and actual published implementation/API sources. Ignored inspection and
command evidence is under `.gradle/r4a-inspection/`.

## Supported shared dependency

Production uses Maven Central **org.totipo:totipo-storage-nio:0.3.1**, exposing
**org.totipo:totipo-core:0.3.1** transitively. Source release commit:
`27cb53a1a5f2cc3d5f221ef790f694b26b90f8a8`. Both modules' JAR, source and Javadoc
SHA-256 hashes match their published module metadata. All **96 core and 13 NIO**
published Java source files match that exact released commit byte for byte.
Downloaded POM/module metadata are independently pinned by SHA-256 in Android's
verification/provenance records; module metadata does not itself authenticate its
own bytes or publisher identity. All published JAR classes target Java 17 (major
61), module variants require JVM 17, and BC remains 1.86. Sources/Javadocs are
inspection evidence, never build substitutions. No unpublished artifact, vendored
shared implementation or new production dependency is used.

[Java provenance](../TOTIPO_JAVA_DEPENDENCY.md) records all ten artifact hashes and
separates the older reviewed operation guidance from the released new backup API.
Gradle's direct NIO edge, transitive core, locks, strict verification and Maven
boundary advance together; diagnostics and current device/release tooling follow
the same version. Existing release provenance records/historical milestone reports
are not rewritten. The Nix cache readiness check advances to 0.3.1; the current
human-generated `package-deps.json` has now been refreshed and independently
reviewed as recorded below. The final human-reported Nix PASS is recorded below.

Public APIs used: `VaultSession.captureBackup(BackupLimits.DEFAULT)`, owned
`BackupCapture.summary()/writeTo()/close()`. Java owns profile-1 grammar,
authentication, exact ciphertext, capacity, gap accounting and ZIP construction.
No Android ZIP writer, manifest parser, token regeneration, re-encryption, root
export, history import or restore API is introduced. New JVM golden resources are
unchanged public Java fixtures with recorded provenance/digests and public password.

## UI and session ownership

**Back up vault** is in the selected vault's native overflow menu and requires
an open authenticated foreground session. Existing password or biometric unlock
suffices. One short explanation covers encrypted historical/deleted data, the
existing password, independent storage/password retention and local-history scope.
There is no new password form or wizard. The archive is prepared before
ACTION_CREATE_DOCUMENT with CATEGORY_OPENABLE and `application/zip`. The suggested
name is `totipo-backup-uuuuMMdd-HHmmss.zip` in UTC, without vault/account/token names.
No manifest component, permission, network access, persisted URI grant or Sync
configuration is added. `allowBackup=false` and Keystore behavior are unchanged.

The controller retains one `BackupExport`, tied by object identity to its originating
activation/controller and authenticated session generation. MainActivity retains
only the in-memory operation, unique picker request code and any already returned
URI across configuration recreation; nothing enters Bundle or persistent picker
state. Picker codes are not reused during the process lifetime. A consumed result
cannot replace the destination or start output twice. A different controller,
retired session, Lock/reopen or process restart cannot adopt an old operation.
No callback discovers a replacement controller. Tasks retain the Application
resolver and URI, not the Activity.

The picker is an expected background transition and grants no output authority.
Actual output admission waits for resume, foreground/inactivity checks and the
same authenticated session. Backgrounding during preparation/output, Lock,
inactivity expiry or retirement permanently cancels further work. Rotation alone
retains the Application session and its deadline. Worker/provider callbacks do
not reset inactivity. This stricter live-session authorization is Android policy:
Java's immutable capture itself can survive session close.

## Whole-root coordination and admission

`LocalReplicaOwner` continues to hold one selection-bound lifetime lease and one
persistent coordinated NIO delegate. `CoordinatedPrivateStore.SessionView` now
forwards **scanObjects(int)** through its ordinary fair gate. It never invokes
legacy unbounded enumeration as a backup fallback. The Java default would return
UNSUPPORTED; forgetting this forwarder would make export fail, not safely capture.

Capture reserves existing controller admission on the bounded vault worker only
when no local command, pending edit or provider operation owns incompatible work.
Authoring and Sync bridge publication cannot enter during capture. Automatic Sync
admission observes the same operating flag; entered local bridge work completes
before a later capture can start. Every SPI call still serializes in the same root
fair gate. Java observation can run, but is read-only and serializes with capture
through Java's provider gate. No independent filesystem copier or second delegate
bypasses ownership. The call order is Java provider gate → Android store gate;
**no session call is made while holding a bridge**. The coordinator also rejects
a capture-under-bridge call. This is the smallest boundary change: forward bounded
SPI plus existing controller admission, without a new root lock around session work.

After Java capture, Android invokes the shared writer into one fixed bounded
private-memory buffer (maximum 6 MiB), checks its digest against the Java summary,
retains an exact-sized immutable ZIP and closes the Java capture on the worker.
Memory is bounded, including temporary capture/output buffers; there is no private
archive staging file or plaintext state. The frozen output is never regenerated
from a changed live vault. Capture covers all supported locally available encrypted
objects, including history, conflicts, tombstones and metadata, not the Sync batch
or current heads. 4,097 objects or other capacity exhaustion fails explicitly;
incomplete enumeration/unreadable/invalid supported objects return no capture.
Known missing/wrong-token ancestry is retained and reported by count. Zero gaps
still establishes neither global completeness nor remote freshness.

After preparation, local reads/reveal and ordinary local authoring may continue.
A subsequent mutation does not invalidate the frozen ciphertext. Provider output
uses the existing bounded provider lane and excludes simultaneous transport-lane
Sync; the vault worker stays available for local work and Lock. Sync is not called
as a prerequisite, and a local-only vault never obtains a tree grant or transport
hint. Output may be refused if an incompatible provider operation gained admission
while the picker was open; the user can start another explicit backup.

## SAF guarantees and interrupted output

The prepared ZIP is frozen before final external output. Before provider open,
`BackupExportJournal` persists and forces a fixed non-secret interrupted-output
marker under app-private no-backup storage, outside canonical roots. It has no URI,
password, root key, token content, registry state or resume authority. Restart
reports that a document may be empty, partial or complete, and never reopens it or
resumes output. A failed marker acknowledgement refuses provider entry. A marker
cleanup failure adds a notice and may show the interruption message again. Only
this positively recognized private marker is deleted; destination documents are
never deleted as cleanup.

Output-open admission linearizes against cancellation using the operation monitor,
released before provider I/O. An already admitted open may complete after cancel;
it may itself create/truncate a document. Writes check cancellation between bounded
chunks. Output write, flush and close must all return successfully before readback.
Readback consumes through EOF, requiring the exact prepared size and SHA-256,
including every ZIP byte. Short/extra/changed bytes, read or readback-close failures
refuse verified success. Null/denied/unavailable readback-open gives the distinct
written/unverified outcome. The shared writer's finish and successful stream close
are never called durable remote storage evidence.

| Outcome | Meaning |
| --- | --- |
| Backup saved and verified | Output/flush/close succeeded and complete provider readback matched exact size/digest. No remote durability or cloud-upload guarantee. |
| Backup written, but verification unavailable | Output/flush/close succeeded but readback could not be opened. Check independently. |
| Backup failed or could not be verified | Output or acquired readback failed, or bytes differed. Partial/incorrect output may remain. |
| Backup canceled | No admitted output, or uncertain empty/partial/complete effects if output-open was admitted. No rollback. |

Neither interruption nor a timeout proves arbitrary SAF/Binder I/O stopped. An
entered blocked provider retains the lane and frozen bytes until actual return.
Lock can close the live session independently. Complete vault-selection retirement
waits for real provider drain and completion handoffs, preserving A-MV2 ownership.
There is no forced canonical-worker interruption, executor replacement, silent
retry, unrelated-document overwrite/delete cleanup or persisted credential state.

## Tests and qualification

Ordinary baseline: **PASS**, 1m37s, 89 tasks (12 executed/77 up-to-date), using
pinned Nix-provided Gradle 9.8.2/JDK17/SDK37. No baseline clean/cache forcing.

Focused results before final gates:

- Backup/controller/store: PASS. Shared empty/history/missing-parent archives are
  byte-identical to the pinned Java goldens after real authenticated Java capture
  through the coordinated Android store. Exact-set equality also excludes registry,
  biometric sibling and unrelated private metadata fixtures. Empty and populated
  history, tombstones/conflicts/metadata and explicit ancestry gaps are retained.
- Local-only operation has no Sync binding/calls. Multiple independent selections
  reject old controller operations and export the correct new vault identity.
  Authoring during capture is refused; Lock waits for admitted capture, while
  a mutation during picker selection does not regenerate frozen output.
- Capacity beyond 4,096, incomplete enumeration and unavailable supported-object
  reads refuse capture. Bounded forwarding and bridge exclusion use the existing
  deterministic store-gate tests, including exact budget forwarding/no fallback.
- Provider faults cover null/open/write/flush/close, unavailable readback-open,
  failed read/close, short/extra/changed bytes, zero-progress reads and cancellation
  before/after output. Latches prove blocked output retains ownership, Lock can
  close secrets, and retirement waits for actual provider return. Marker creation,
  restart observation, cleanup and symlink refusal are exercised.
- Picker cancellation/duplicate results, session Lock/reopen, selection retirement,
  retained Activity-listener recreation and late ownership are tested at the JVM
  controller boundary. These are not claims of actual framework recreation/SAF
  instrumentation. Process interruption is modeled by retained durable marker and
  lost in-memory authority; physical kill timing is still unqualified.
- Existing focused controller/coordinator/owner/multi-vault/UI suites: PASS (1m12s).
  Release-tooling suite after current runtime metadata update: PASS, 80 tests.
  Python tool syntax, documentation relative links and whitespace checks: PASS.

Focused failure diagnosis: one compile-only invocation exposed 0.3.1's required
methods on the test SessionStub; added explicit throwing implementations. The
first focused backup test invocation stopped at compilation because the new test
used `EntryKind.REGULAR_FILE`; corrected to the actual published `REGULAR`. Neither
failure caused a full gate rerun. Duplicate-result handling was tightened before
full qualification so a duplicate cannot cancel already admitted output.

The first final normal gate FAILED in 2m58s: 480 tests, three failures. Its complete
XML is preserved under `final-normal-failed-xml`. One test expected the unsupported
candidate-validation stub to throw UnsupportedOperationException, while the new
required-method stub threw AssertionError. Two source tripwires rejected SAF
adapter code inside MainActivity. Moved the adapter into `AndroidBackupDocument`,
constructed with the Application resolver, and preserved the guards with one
explicit allowance for that adapter. The guard still forbids Activity output and
canonical-document deletion/rename/move/copy; it also checks the backup adapter
uses scoped `w` output without document creation. Restored the stub's intended
unsupported result. Focused reproduction/fix campaign (backup, candidate classifier,
Sync source guards): PASS in 8s. This production/test change invalidated the first
checkpoint attempt; one renewed final normal gate is necessary. Strict-clean has
not yet been invoked. No failure was bypassed or broad gate immediately repeated.

Final normal/strict Gradle, full test accounting, artifact checks, runtime graph
and exact execution counts are appended after their commands complete.

## Device scope and remaining R4B work

`adb devices` returned an empty device list. **No physical Android/SAF pass is
claimed.** The included [physical procedure](../tools/device/BACKUP_EXPORT_QUALIFICATION.md)
covers actual local/cloud/removable providers where available, API30/current,
packaged release behavior, accessibility, rotation, Lock/background, process kill,
low storage and actual output/readback failures. It is a procedure, not executed
evidence. The existing historical A-MV6/biometric passes do not qualify these new
ZIP/SAF paths or Java 0.3.1 on a device. No unrelated Syncthing/8k-row campaign,
physical install, production data reset or key replacement was performed.

This milestone is not ready to ship the combined backup/recovery feature. R4B must
implement new-local-vault ZIP restore with shared detached authentication,
reservation/journal, retained-root duplicate checks, partial/uncertain recovery,
create-only exact installation and registry promotion. History import is separate.
The export's 4,096-object/6MiB shared ceiling, finite local coverage, metadata
visibility, existing-password reliance and arbitrary provider durability/liveness
limits remain. Empty-password vaults get no new protection. No password recovery,
rollback resistance, secure deletion or automatic remote completeness is promised.

## Historical freeze and initial human handoff (before cache refresh)

Actual `package.nix` trees/files and `flake.nix` release-tooling fileset were
reinspected. Production/tests/resources, `gradle/`, all selected `tools/` (including
physical procedure), locks, package/cache/evaluator inputs affect qualification.
AGENTS, README, MULTI_VAULT, TOTIPO_JAVA_DEPENDENCY, docs and review remain excluded
from effective build/check inputs. Report-only finalization does not invalidate
Gradle or Nix. Every new milestone file is Git-known through intent-to-add only;
no actual file contents are staged. Ignored caches/build/signing state remain out.

- **Production source stable:** export/controller/Activity/store/provider inputs
  finalized before final normal Gradle; no manifest/resource permission change.
- **JVM tests stable:** backup/controller/store/stub/golden fixtures finalized before
  final normal Gradle.
- **Build/package configuration:** Gradle pins/locks/strict hashes and cache readiness
  rule stable; human-generated dependency-response cache refresh still pending.
- **Qualification harness/tooling stable:** current-runtime device/release tools and
  physical procedure finalized before final normal Gradle.

No final human Nix gate has been requested or run yet. Intentional dependency cache
refresh must precede refreezing its included input and the single final routine
human `nix flake check`. The agent runs no Nix. Human results will be recorded as
human-reported evidence, never agent execution. Physical acceptance remains separate.

Platform limitation discovered during inspection: the shared runtime uses
`java.util.HexFormat`, documented by Android as API34+. This was already present
in Java 0.2.0 identity/object handling and now also appears in the shared backup
writer. The manifest minimum remains API30; no desugaring/library fallback is
introduced here. API30–33 execution must not be certified from Java17 bytecode,
lint or JVM results. It remains an existing platform compatibility risk and needs
shared/runtime qualification before shipping on those devices. See
[Android HexFormat reference](https://developer.android.com/reference/java/util/HexFormat).

## Stable normal checkpoint and pre-cache freeze

Renewed normal `gradle check :app:assembleDebug :app:assembleRelease`: **PASS**,
3m05s, 90 actionable tasks (30 executed/60 up-to-date). Complete XML preserved as
`final-normal-xml`: **480 tests, zero failures/errors/skips**. Lint and locked
Maven/build boundaries pass. The necessary repeated full gate followed the focused
fixes documented above; no full gate was repeated without an invalidating change.

Freeze evidence before intentional cache generation:

- **Production source stable:** all intended implementation/adapters are Git-known;
  no production edit follows the renewed normal PASS. Input SHA-256 inventory
  saved as `included-inputs-before-cache.json`.
- **JVM tests stable:** focused fixes and the complete 480-test normal PASS match
  the final tests/resources; no later test edit.
- **Build/package configuration:** Gradle pins/locks/verification and package
  readiness are stable. The sole pending input is the intentional human-generated
  `package-deps.json` refresh for 0.3.1. Build/package freeze for human Nix is not
  claimed until that result is reviewed.
- **Qualification harness/tooling stable:** current device tooling pins, physical
  procedure and release metadata/tools are Git-known and stable. Release suite
  ran twice (80 tests each, both PASS; second was a redundant duplicate, not a
  necessary extra qualification gate). No later tooling edit.

`git status --short` and `git diff --name-only` were recorded before requesting the
cache refresh; `git diff --cached --name-only` was empty and `git diff --cached
--quiet` passed. New files have intent-to-add membership only. No build/cache/secret
state was added. The agent executed no Nix. Requested the human-only deliberate
`nix run .#update-package-deps`; this is a dependency acquisition checkpoint, not
an extra routine human qualification gate. Final human `nix flake check` has not
been requested yet: package/cache inputs must first be stable.

Reinspection confirms `package.nix` selects app/src, gradle, tools and the explicit
build/version/license/provenance list. package.nix/flake.nix/flake.lock/package-deps
also affect evaluation/cache. flake release checks select tools/release, verifier,
Gradle files and public signing material. README/AGENTS/MULTI_VAULT/provenance docs,
docs/ and review/ remain excluded from effective checks. Excluded report/user-guide
edits do not invalidate the Gradle passes or a later Nix result. Included-input
changes would require the appropriate renewed gates under the invalidation matrix.

## Final local Gradle, artifact and runtime evidence

One stable strict gate, after the renewed normal PASS:

```sh
gradle --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug :app:assembleRelease
```

**PASS**, 3m49s, all 92 actionable tasks executed. Complete strict XML retained
outside build outputs as `final-strict-xml`: **480 tests, zero failures/errors/skips**.
Strict SHA-256 dependency verification, lint and all Maven/build boundaries pass.
No production/test/build/tooling input changed after the successful normal gate.
Only excluded documentation/report changes followed; no pass was invalidated.

The final external artifact set, against strict-clean outputs, passes:

```sh
python3 tools/verify-apk.py app/build/outputs/apk/debug/app-debug.apk --debug-probe
python3 tools/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk --unsigned --no-debug-probe
gradle --offline :app:dependencies --configuration releaseRuntimeClasspath :app:verifyMavenBoundary
```

Both verifier commands **PASS**. Complete permission/component/intent/backup,
variant, identity/SDK, branding and debug/release exclusion policy remains unchanged.
Reviewed source metadata remains VERSION=0.2.3 and VERSION_CODE=8; this milestone
changes neither and does not prepare/publish a release. Final observed runtime
graph is exactly NIO 0.3.1 → core 0.3.1 → BC 1.86, with strict version constraints;
no new dependency edge appears. The standalone offline boundary command passes
in 1s, both tasks executed. The verifier is not duplicated inside Gradle check.

| Strict-clean artifact | SHA-256 (verifier and independent final hash agree) |
| --- | --- |
| Debug APK | `ac81c7a53744a9b4622a7a22b4056796ea584d3ff1d6e2810508f49d2800ce20` |
| Unsigned release APK | `de9820fc263d63a5ff32f94eda31ac59c625e5546afd0e7c6b2e640fadb04cd0` |

| Execution count | R4A evidence |
| --- | --- |
| Baseline full Gradle | 1 ordinary PASS |
| Focused compile Gradle | 2: dependency compile PASS; product/unit compile failed on the required stub, fixed afterward |
| Focused test Gradle | 5: first stopped at new test compilation; four subsequent campaigns PASS, including focused full-gate diagnosis/fix |
| Standalone final runtime/boundary Gradle | 1 PASS |
| Final normal full Gradle | 2: initial FAIL (3 tests); focused fixes; renewed PASS |
| Strict-clean Gradle | 1 offline clean PASS |
| External artifact verifier | 1 final set, 2 commands, both PASS |
| Embedded Gradle APK verifier | 0 (by design) |
| Release-tooling test suite | 2, 80 tests each, both PASS; redundant second invocation acknowledged above |
| Physical harness phases/runs | 0; no connected device |
| Agent Nix | 0 |
| Human cache updater / human Nix | 1 reported PASS / 1 reported PASS; final routine check on frozen inputs |
| Commits / tags / releases | 0 / 0 / 0 |

## Delivery limitations and remaining gates

The export implementation is reviewable and locally qualified, with changes left
uncommitted. Cache review, all four freeze statements and the final human-reported
`nix flake check` PASS are complete. **R4A is not fully qualified for release:**
actual physical SAF/framework/lifecycle evidence remains unavailable and must be
recorded separately.
The documented API30–33 shared-runtime compatibility gap also remains unresolved;
minimum-install metadata and JVM success do not certify those platforms.

R4B still needs Android new-local-vault restore: authenticated ZIP ingress,
private allocation and durable restore-intent/registry coordination, duplicate
identity checks across retained roots, partial/uncertain installation recovery,
ordinary authenticated open/promotion, and physical end-to-end loss/restore
qualification. Java R3 supplies a released shared prerequisite, not Android UX or
registry integration. Existing retained-vault Recovery is unchanged. No restore,
history import, scheduled job, cloud client or protocol change is advertised.

## Human dependency-cache result and final freeze

The user reported `nix run .#update-package-deps` succeeded. This is human-reported
command execution; no command log was supplied and the agent ran no Nix. Inspected
the actual resulting diff: only the two Totipo 0.2.0 cache entries advance to 0.3.1,
with the NIO Maven metadata release/timestamp updated. No unrelated origin,
dependency or artifact hash changes. All six JAR/module/POM cache SHA-256 values
match the independently inspected Maven Central bytes and strict Gradle verification
metadata. The reviewed `package-deps.json` SHA-256 is
`2b0c0aaac5fa4a72f79fede8e63067cc0422f18d4d573edfeaaa7d3fa6358d5e`.

Compared every recorded included input with the pre-cache freeze: **only
package-deps.json changed**. The cache is not consumed by local Gradle or the
artifact verifier; this intentional cache-only change needs no renewed Gradle,
APK or device invocation. No prior final human Nix pass exists to invalidate.
The final 200-path source/evaluator/cache hash record is retained as ignored
`.gradle/r4a-inspection/included-inputs-final.json`.

All four final freeze statements now hold:

- **Production source stable:** unchanged since the successful normal/strict gates,
  confirmed by the included-input hash comparison.
- **JVM tests stable:** unchanged since the 480-test strict PASS, confirmed likewise.
- **Build/package configuration stable:** Gradle, locks, verification, package/flake
  inputs unchanged; the intentional cache refresh is reviewed and now frozen.
- **Qualification harness/tooling stable:** unchanged since local qualification;
  all intended files are Git-known, including physical procedure and shared goldens.

The single final routine human `nix flake check` was requested on this Git-aware
working tree. The user subsequently reported: **“nix flake check passed”**. No
command log was supplied; this is human-reported evidence, not agent execution.
The agent ran no Nix and requests no extra build/rebuild or repeated routine gate.

After that report, independently reread all **200** frozen included-input hashes
and their Git-known membership: every byte and path matches
`included-inputs-final.json`. Branch remains main and HEAD remains the starting
commit. Production, JVM tests, build/package/cache inputs and qualification tooling
are still frozen. Only this excluded report is finalized after the human PASS;
no Gradle/artifact/Nix gate is invalidated. Later included-input edits would require
the appropriate renewed qualification. Physical provider/runtime acceptance and
R4B remain distinct from this completed host/Nix qualification.

## Exact final Git state after human Nix PASS

All intended new files remain Git-known via intent-to-add only. No actual file
content is staged. Ignored inspection/build/cache/key state stays outside source
membership. Excluded report-only finalization does not invalidate qualification.

`git branch --show-current`:

```text
main
```

`git rev-parse HEAD`:

```text
e6f323f25932ef47c9fa3b642803d55f3dc45c5c
```

`git status --short`:

```text
 M AGENTS.md
 M README.md
 M TOTIPO_JAVA_DEPENDENCY.md
 M app/build.gradle.kts
 M app/gradle.lockfile
 A app/src/main/java/org/totipo/android/AndroidBackupDocument.java
 M app/src/main/java/org/totipo/android/AndroidVaultController.java
 A app/src/main/java/org/totipo/android/BackupExport.java
 A app/src/main/java/org/totipo/android/BackupExportJournal.java
 M app/src/main/java/org/totipo/android/MainActivity.java
 M app/src/main/java/org/totipo/android/TotipoApplication.java
 M app/src/main/java/org/totipo/android/reconcile/CoordinatedPrivateStore.java
 M app/src/main/java/org/totipo/android/reconcile/ForegroundVaultCoordinator.java
 M app/src/main/java/org/totipo/android/sync/ProviderIoLane.java
 A app/src/test/java/org/totipo/android/BackupControllerTest.java
 A app/src/test/java/org/totipo/android/BackupExportTest.java
 M app/src/test/java/org/totipo/android/DailyDriverUxTest.java
 M app/src/test/java/org/totipo/android/provider/ImmutableCandidateClassifierTest.java
 M app/src/test/java/org/totipo/android/reconcile/CoordinatedPrivateStoreTest.java
 M app/src/test/java/org/totipo/android/reconcile/ProductControllerFixtures.java
 M app/src/test/java/org/totipo/android/sync/SyncSourceGuardTest.java
 A app/src/test/resources/backup-profile-1/README.md
 A app/src/test/resources/backup-profile-1/empty.zip
 A app/src/test/resources/backup-profile-1/history.zip
 A app/src/test/resources/backup-profile-1/missing-parent.zip
 M docs/ARCHITECTURE.md
 M docs/USER_GUIDE.md
 M gradle/verification-metadata.xml
 M package-deps.json
 M package.nix
 M release/README.md
 A review/BACKUP_RESTORE_R4A_ANDROID_EXPORT_REPORT.md
 A tools/device/BACKUP_EXPORT_QUALIFICATION.md
 M tools/device/build-saf-bootstrap-tests.py
 M tools/device/build-saf-inbound-tests.py
 M tools/device/build-saf-outbound-tests.py
 M tools/device/check-row-reveal.py
 M tools/device/qualify-saf-outbound.py
 M tools/device/qualify-security.py
 M tools/release/release.py
 M tools/release/test_release.py
```

`git diff --name-only`:

```text
AGENTS.md
README.md
TOTIPO_JAVA_DEPENDENCY.md
app/build.gradle.kts
app/gradle.lockfile
app/src/main/java/org/totipo/android/AndroidBackupDocument.java
app/src/main/java/org/totipo/android/AndroidVaultController.java
app/src/main/java/org/totipo/android/BackupExport.java
app/src/main/java/org/totipo/android/BackupExportJournal.java
app/src/main/java/org/totipo/android/MainActivity.java
app/src/main/java/org/totipo/android/TotipoApplication.java
app/src/main/java/org/totipo/android/reconcile/CoordinatedPrivateStore.java
app/src/main/java/org/totipo/android/reconcile/ForegroundVaultCoordinator.java
app/src/main/java/org/totipo/android/sync/ProviderIoLane.java
app/src/test/java/org/totipo/android/BackupControllerTest.java
app/src/test/java/org/totipo/android/BackupExportTest.java
app/src/test/java/org/totipo/android/DailyDriverUxTest.java
app/src/test/java/org/totipo/android/provider/ImmutableCandidateClassifierTest.java
app/src/test/java/org/totipo/android/reconcile/CoordinatedPrivateStoreTest.java
app/src/test/java/org/totipo/android/reconcile/ProductControllerFixtures.java
app/src/test/java/org/totipo/android/sync/SyncSourceGuardTest.java
app/src/test/resources/backup-profile-1/README.md
app/src/test/resources/backup-profile-1/empty.zip
app/src/test/resources/backup-profile-1/history.zip
app/src/test/resources/backup-profile-1/missing-parent.zip
docs/ARCHITECTURE.md
docs/USER_GUIDE.md
gradle/verification-metadata.xml
package-deps.json
package.nix
release/README.md
review/BACKUP_RESTORE_R4A_ANDROID_EXPORT_REPORT.md
tools/device/BACKUP_EXPORT_QUALIFICATION.md
tools/device/build-saf-bootstrap-tests.py
tools/device/build-saf-inbound-tests.py
tools/device/build-saf-outbound-tests.py
tools/device/check-row-reveal.py
tools/device/qualify-saf-outbound.py
tools/device/qualify-security.py
tools/release/release.py
tools/release/test_release.py
```

`git diff --cached --name-only`:

```text
```

`git diff --cached --quiet`: PASS. `git diff --check`: PASS.
