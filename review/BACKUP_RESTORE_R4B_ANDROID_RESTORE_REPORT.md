# BACKUP_RESTORE_R4B — Android new-local-vault restore

Status: implementation, final normal Gradle, strict offline clean and external
artifact qualification and final human-reported Nix qualification complete.
Physical API 30/current-release qualification is not executed.
API 30–33 shared Java HexFormat compatibility remains an explicit release blocker.
No release, tag, commit or staged file contents are authorized or performed.

## Scope and starting point

Classification: **PRODUCT_CODE, UI_ONLY, LIFECYCLE_SECURITY,
TEST_OR_QUALIFICATION_TOOLING**. Build, dependency and archive profile unchanged.
Started clean on `main`, HEAD `32ac66f707007c7b97f26cbcb7bc8ae568238f5f`;
`git status --short` was empty. All writes/downloads confined to totipo-android.
Read-only Java inspection material and command logs: ignored `.qualification/r4b/`.
No agents, other checkouts, agent Nix execution or physical device operations used.

Reviewed AGENTS, README, MULTI_VAULT, architecture/user/development guides, R0/R4A,
Java provenance, actual package/flake filtering, CI and relevant tools. Inspected
registry, root owner/selection, Application/Activity/controller, coordinated store,
foreground coordinator, detached authentication and retained-root recovery. Read
Java R1/R2/R3 reports and profile-1/API_DESIGN at released source
`27cb53a1a5f2cc3d5f221ef790f694b26b90f8a8`, and exact guidance
`f0a028676c1801a10b2d8d2650bf9c24357d1fa1` (operation classes, replay/observation
ordering, causal bases, merge freshness, persistence and close).

Runtime stays Maven Central NIO/core **0.3.1**, BC **1.86**, Java **17**, Android
min/compile/target **30/37/37**. Reviewed published source JAR hashes against
TOTIPO_JAVA_DEPENDENCY and compared inspected restore API source files byte for
byte with published source entries. Both modules passed. Ordinary production
compilation and strict Maven verification use the published binaries, not downloaded
sources or unpublished artifacts. No newer dependency was selected.

## Workflow and authority

Vaults → Add vault → Restore from backup provides ZIP and broader all-files SAF
filters. Complete bounded immutable ingress is structurally checked by shared Java
before password entry; prepareRestore then independently authenticates bootstrap
and every archived encrypted object. Names change local labels only. Explicit
confirmation includes computed missing-ancestry diagnostics. Java owns profile 1,
authentication, exact ciphertext replay, create-only publication and final inventory
verification. Android implements no extraction, root generation, re-encryption,
additive import, overwrite restoration, migration or scheduling.

A restore journey requires an unselected Application. The existing metadata worker
runs blocking ingress, KDF, install and plan close. Application management admission
covers worker retirement and main-thread completion. VaultSelection maintenance
additionally excludes selection during installation, before registry reservation.
CoordinatedPrivateStore wraps NioStoreComposition.reserveRestore/recoverRestore in
its existing fair root gate. All reservation SPI methods, including bounded scan,
location acknowledgement and installVault, use this domain. No bridge, independent
canonical store, second delegate or live session runs alongside installation. Plan
and persistent delegate close before selected normal controller opening. Normal
session calls/capture happen outside bridge, admission and registry monitors.

The operation owns frozen ciphertext only. Password arrays are wiped in finally,
including authentication failure and rejected UI submissions. Password widgets have
save disabled and clear on rebuild/stop/destroy; no password enters Bundle, journal,
log or long-lived operation fields. URI transport is in-memory only and requests no
persisted permission, network or broad-storage capability. Sync grants/settings,
publication hints and biometric credentials are not imported. Restore needs no Sync
configuration; subsequent ordinary local-only opening uses the existing controller.

Unique process-local picker request codes and originating operation identity reject
late/duplicate callbacks. Configuration recreation retains only that origin/request
and any pending non-secret URI; process death drops them. Cancellation, backgrounding
outside picker and a fixed 15-minute journey deadline revoke further admission.
This deadline does not reset on interaction: a stricter application policy than
ordinary inactivity. Entered blocking reads/publications are not forcibly interrupted;
management stays busy until they and closure actually retire. Cancellation racing
a completion handoff queues worker disposal before releasing UI admission. Explicit
open handoffs also retain/recheck their originating selection intent.

## Durable registry and exact recovery

Schema **3** reads schemas 1/2 without implicit rewrite; the next metadata change
writes schema 3. Prior state ordinals are preserved. Added RESTORING and a bounded
intent containing expected VaultId, whole-original-ZIP SHA-256, object count,
computed gap count and phase. Intent is local metadata, excluded from profile 1.
The registry byte limit is enforced before writing as well as when decoding.

1. RESERVED is atomic with the local ID reservation, before container allocation.
2. ATTEMPTED is durable before shared provider allocation/marker or canonical writes.
3. Java exclusively creates the previously absent store child and its durable
   `.totipo-restore-v1` marker. Existing entries of every kind are refused as fresh.
4. Java COMPLETE alone permits INSTALLED after exact bootstrap/planned inventory
   and positive location/object persistence acknowledgements. PARTIAL, UNCERTAIN,
   REJECTED and exceptions retain RESTORING; no cleanup removes canonical evidence.
5. INSTALLED remains in Recovery. Normal password opening completes initial Java
   observation and an authenticated full installed capture, comparing identity,
   object and computed gap counts with intent. The prior COMPLETE receipt established
   exact planned bytes; this check rejects unavailable/invalid/lost inventory.
6. Only then one atomic update promotes ESTABLISHED and completes the registry
   journal. No separate journal-completion crash gap exists. Provider marker remains
   local and outside portable archive membership.

Generic authenticated promotion, allocatePending, Create/Join and Forget cannot
promote or repurpose an incomplete restore. A bootstrap open cannot bypass these
checks. Ordinary controller opening is allowed only for INSTALLED restores. A failed
promotion closes the Java session through the existing coordinator fail-closed path.
A metadata write failure poisons its writer; registry.next and torn/unknown metadata
remain explicit investigation conditions, preserving existing registry policy.

Exact retry is explicit: select Recovery → Restore interrupted backup, freeze and
authenticate the original ZIP again, compare its exact digest/identity/counts, then
recover the original provider reservation. Only a RESERVED intent that never reached ATTEMPTED may receive its first
allocation during explicit retry. Once ATTEMPTED, a missing root cannot prove
allocation never happened and requires investigation; it is never recreated.
No existing empty directory is treated as fresh. Torn/missing/mismatching marker
or incompatible occupied bytes is retained for investigation. No automatic restart,
root recreation, replacement, rollback or new allocation substitutes for recovery.
Known incomplete outcome receipts are distinct in memory; after process death an
ATTEMPTED journal deliberately conservatively means incomplete/unknown, not success
or a reconstruction of prior per-object acknowledgements. Java reacknowledges exact
bytes on explicit retry.

Strict restore duplicate admission scans all retained entries before reservation
and again before install. Established matches offer Open existing vault; other
states direct to their existing Recovery actions. Unknown/malformed/missing or
ambiguous identity evidence fails closed, including unrelated uncertain roots.
Untouched positively empty ordinary reservations are identity-free. A restore's
durable expected identity also prevents duplicates before bootstrap installation.
Retained-vault Recovery still authenticates existing private installation data;
ZIP recovery is a separate exact-archive replay and never silently merges histories.

## Focused evidence and limitations

New BackupRestoreFlowTest and CoordinatedRestoreTest exercise released shared APIs
with all existing golden fixtures (empty, conflict/tombstone history, missing parent),
byte equality of every canonical representation, original-password normal open,
wrong password, corrupt ZIP and structurally valid AEAD-invalid material, duplicate
identity across retained states, unknown roots, selection maintenance exclusion,
process interruption before/after each restore registry write, lost promotion
acknowledgement, bootstrap-only interruption, explicit exact retry, wrong retry ZIP,
post-install object loss, no-overwrite/missing/torn intent, location/bootstrap/object
lost durability acknowledgement and partial conflicting-object replay. Cancelled
plans report REJECTED independently of allocation residue. Latches prove cancellation
cannot dispose/admit replacement while ingress is entered. Tests also check deadline,
single-use step admission, password wiping and absence of imported application files.

Existing registry, management, root-owner, coordinator and controller regressions
are selected alongside these tests. Legacy schema fixtures now explicitly encode
schemas 1/2 rather than editing a newer payload's version byte. The existing Activity
source guard now uses Java-name boundaries: `BackupRestoreFlow.` is not direct
`Flow.` access. Its initial broader focused run failed on that lexical false positive;
the exact failure XML/log were preserved and the focused guard/new restore tests
passed after correction. No full gate was repeated to diagnose it.

No physical Activity/SAF/Android durability evidence is claimed. Host flow admission
and source guards do not execute framework recreation, real picker delivery,
TalkBack or Android Keystore. The new disposable-device procedure covers API 30
and a current supported Android release, packaged release/debug, provider failures,
low storage, lifecycle/Lock, every canonical durable boundary and exact recovery.
Remaining physical faults require instrumentation on disposable profiles. Shared
Java tests are supporting upstream evidence, not newly executed Android tests.

**API 30–33 is release-blocked by shared java.util.HexFormat use (API 34+).** minSdk
remains 30; no Android hex/cryptographic workaround or unpublished dependency was
introduced. A qualified published shared-runtime fix must follow normal provenance,
cache and pin review. Current-device physical qualification also remains pending.
Archive bounds (4,096 objects/6 MiB), provider liveness, platform force support,
finite captured history, possible omissions and best-effort JVM wiping remain.
This milestone does not establish generally qualified Android disaster recovery.

## Qualification accounting and freeze

Final results, source membership, exact Git inventory and human gate status are
recorded below after stable qualification. Included inputs are app/src, gradle,
selected tools, explicit Gradle/metadata files and evaluator/cache definitions.
Flake release-tooling inputs were inspected too. Docs, README, MULTI_VAULT,
TOTIPO_JAVA_DEPENDENCY and review are excluded from effective package/check inputs.
Report-only finalization therefore does not invalidate Gradle or Nix.

A final lifecycle review also reset stale Activity-local picker bookkeeping when
starting a different current restore operation. Unique request codes and origin
checks still reject the old callback. This prevents an outstanding retired picker
from blocking a later explicit journey. It invalidated the second ordinary run
already in progress; that run's result is retained as intermediate evidence only.
No strict/artifact/Nix gate had run. A renewed final normal gate is required after
focused validation, rather than treating its old compiled Activity as final evidence.

Restore operation taxonomy: structural frozen inspection is detached descriptive
work with bounded ingress; password authentication is independent candidate
validation (H); allocation/open/close are lifecycle (G), with exact frozen object
publication (F). Normal opening's completed observation and authenticated capture
are separate from the installation receipt. Whole-journey UI admission and its
fixed deadline are Android product policy, not a generic Java global BUSY rule or
cancellation on VaultState emission.

## Execution counts and preserved failures

| Gate | Invocations so far | Evidence |
| --- | ---: | --- |
| Ordinary baseline full Gradle | 1 | PASS, 1m30s; no clean/rerun/cache bypass |
| Explicit focused compile Gradle | 2 | Both PASS |
| Focused test Gradle | 7 | Six PASS; focused-3 failed only the Flow name-boundary guard |
| Normal full checkpoint attempts | 3 | First failed the naming-source guard; second PASS (493 tests) invalidated by final picker bookkeeping/comment edit; third PASS, 3m07s, 493 tests with zero failures/errors/skips; both APKs built |
| Strict offline clean | 1 | PASS, 3m30s; 92/92 tasks executed; 493 tests, zero failures/errors/skips |
| External APK verification | 1 final set (2 commands) | Both strict-clean APKs PASS |
| Embedded full APK verifier | 0 | check uses JVM/lint/Maven boundaries; no duplicate full APK verifier |
| Explicit offline runtime graph/Maven boundary | 1 | PASS, 1s; unchanged NIO/core/BC graph |
| Physical harness phases | 0 | Procedure prepared, no execution claimed |
| Agent / human Nix | 0 / 1 | User reported final `nix flake check` PASS on 2026-11-16 |
| Commit / tag / release | 0 / 0 / 0 | Changes remain uncommitted |

Preserved `.qualification/r4b/focused-3-xml` records its single guard failure.
`normal-1-xml` records 492 tests, one naming-source guard failure, no errors/skips.
`normal-2-xml` records 493 tests with zero failures/errors/skips; it is intermediate
evidence, not the final checkpoint. Focused-4/5 corrected and verified the respective
guards before renewed broad checks. Focused-6 ran all 13 new restore tests and
passed; focused-7 passed Activity shell/source boundaries after the final picker
correction. No production failure was hidden and no final strict gate was repeated.

The extra normal invocations were required by the observed naming-source assertion
failure and subsequent input invalidations. During review of the first attempt,
missing attempted-root recovery was tightened to refuse recreation; focused-5/6
cover that boundary. The second attempt's compiled Activity preceded the final
retired-picker bookkeeping fix, requiring the third checkpoint. Invocation targets
were not used to waive a changed-input gate.

## Frozen input evidence

Before the definitive normal and strict gates:

- **Production source stable:** Restore flow, Activity/Application admission, registry,
  selection, owner and coordinated store/coordinator were finalized. Final normal
  passed with those exact inputs; no production edit followed.
- **JVM tests stable:** All 13 new restore tests and affected existing regressions
  finalized before final normal. Full normal XML preserved independently of clean:
  `.qualification/r4b/final-normal-xml`, 493 tests, zero failures/errors/skips.
- **Build/package configuration stable:** No Gradle, dependency lock/verification,
  package/cache/evaluator input, SDK/minSdk, manifest or version change. Current
  Android version remains 0.2.3, versionCode 8. Published runtime stays 0.3.1.
- **Qualification harness/tooling stable:** New disposable-device procedure was
  finalized and Git-known before final normal/strict. No existing harness or APK
  verifier changed; device execution remains zero.

All new milestone files, including this report, were made Git-known using only
intent-to-add before final normal qualification. Cached content diff remains empty.
No cache, build output, inspection material or secret was intent-added. SHA-256 of
`git diff` over all included source/tool/build/evaluator/cache paths at freeze:
`b93b867e48dbaea48d70d5837514b85354941a57e20a613e7facb8d65d96182c`.
The hash is saved in `.qualification/r4b/included-diff.freeze`; its scope is
package.nix's selected trees/exact files plus package.nix, flake.nix, flake.lock and
package-deps.json. Report/docs-only finalization is outside those effective inputs. Final post-artifact
comparison and Git/source-membership evidence follow below.

## Final strict-clean artifacts and completed human gate

Executed the required final strict command once, after final normal PASS:

```sh
gradle --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug :app:assembleRelease
```

PASS, 3m30s, 92 tasks all executed. Strict XML preserved in
`.qualification/r4b/strict-xml`: **493 tests, zero failures/errors/skips**.
Lint and build/Maven boundaries passed. No production/test/build/tool input changed
after final normal or strict qualification.

The two external verifier commands ran once against these strict-clean bytes:

| APK | Policy | SHA-256 |
| --- | --- | --- |
| app-debug.apk | `--debug-probe`: PASS | `d4b7b7205b9013623810be4734e14c7b4960cf404c478b456b309e72324f0343` |
| app-release-unsigned.apk | `--unsigned --no-debug-probe`: PASS | `1500c82691a893f829e88a70a238656a17d290e92a82f3697ea5d04e55922f9c` |

Both package identities/SDK/version metadata match reviewed sources (0.2.3/8).
Reviewed main/debug manifests and the packaged verifier's complete policy. Main
permissions remain USE_BIOMETRIC only, allowBackup=false, with existing launcher
and otpauth enrollment components/filters. No backup ZIP exported intent handler,
network or storage permission was added. Release excludes debug components/probes;
branding, variant flags and complete artifact boundaries pass existing policy.

Required offline releaseRuntimeClasspath + verifyMavenBoundary: PASS. Runtime graph
remains exactly NIO 0.3.1 → core 0.3.1 → BC 1.86 plus their strict constraints.
No new production dependency, local substitution, lock/cache or provenance change.

The included-input diff SHA-256 was rechecked after artifact verification and again
when recording the human result. Both comparisons match the freeze above. On
2026-11-16 the user reported that the final routine command passed in this checkout:

```sh
nix flake check
```

This is **human-reported PASS**, not agent execution or a result inferred from
Gradle. AGENTS.md reserves this gate to the human; agent Nix invocations remain
zero and human invocations total one. This completes host qualification on the
frozen inputs. Only this excluded report was edited to record the result; no
production, JVM test, build/package or qualification-tool input changed. Therefore
no Gradle, artifact or Nix rerun is required. Any later included-input edit would
invalidate Nix and the affected Gradle/artifact evidence.

Physical qualification and API 30–33 compatibility remain separate release blockers.
Host qualification authorizes no release, tagging or committing.

## Final Git/source membership

Branch remains `main`, HEAD remains `32ac66f707007c7b97f26cbcb7bc8ae568238f5f`.
All 22 changed paths below are uncommitted. New paths retain intent-to-add only;
existing modified files remain unstaged. Ignored inspection/build/cache state stays
outside source membership. `git diff --cached --quiet`: PASS. Documentation relative
links and `git diff --check` are validated after report finalization.

`git status --short`:

```text
 M MULTI_VAULT.md
 M README.md
 M TOTIPO_JAVA_DEPENDENCY.md
 A app/src/main/java/org/totipo/android/BackupRestoreFlow.java
 M app/src/main/java/org/totipo/android/LocalReplicaOwner.java
 M app/src/main/java/org/totipo/android/MainActivity.java
 M app/src/main/java/org/totipo/android/TotipoApplication.java
 M app/src/main/java/org/totipo/android/VaultRegistry.java
 M app/src/main/java/org/totipo/android/VaultSelection.java
 M app/src/main/java/org/totipo/android/reconcile/CoordinatedPrivateStore.java
 M app/src/main/java/org/totipo/android/reconcile/ForegroundVaultCoordinator.java
 M app/src/test/java/org/totipo/android/AndroidVaultControllerTest.java
 A app/src/test/java/org/totipo/android/BackupRestoreFlowTest.java
 M app/src/test/java/org/totipo/android/DailyUseUxTest.java
 M app/src/test/java/org/totipo/android/MultiVaultUiBoundaryTest.java
 M app/src/test/java/org/totipo/android/VaultManagementTest.java
 M app/src/test/java/org/totipo/android/VaultRegistryTest.java
 A app/src/test/java/org/totipo/android/reconcile/CoordinatedRestoreTest.java
 M docs/ARCHITECTURE.md
 M docs/USER_GUIDE.md
 A review/BACKUP_RESTORE_R4B_ANDROID_RESTORE_REPORT.md
 A tools/device/BACKUP_RESTORE_QUALIFICATION.md
```

`git diff --name-only`:

```text
MULTI_VAULT.md
README.md
TOTIPO_JAVA_DEPENDENCY.md
app/src/main/java/org/totipo/android/BackupRestoreFlow.java
app/src/main/java/org/totipo/android/LocalReplicaOwner.java
app/src/main/java/org/totipo/android/MainActivity.java
app/src/main/java/org/totipo/android/TotipoApplication.java
app/src/main/java/org/totipo/android/VaultRegistry.java
app/src/main/java/org/totipo/android/VaultSelection.java
app/src/main/java/org/totipo/android/reconcile/CoordinatedPrivateStore.java
app/src/main/java/org/totipo/android/reconcile/ForegroundVaultCoordinator.java
app/src/test/java/org/totipo/android/AndroidVaultControllerTest.java
app/src/test/java/org/totipo/android/BackupRestoreFlowTest.java
app/src/test/java/org/totipo/android/DailyUseUxTest.java
app/src/test/java/org/totipo/android/MultiVaultUiBoundaryTest.java
app/src/test/java/org/totipo/android/VaultManagementTest.java
app/src/test/java/org/totipo/android/VaultRegistryTest.java
app/src/test/java/org/totipo/android/reconcile/CoordinatedRestoreTest.java
docs/ARCHITECTURE.md
docs/USER_GUIDE.md
review/BACKUP_RESTORE_R4B_ANDROID_RESTORE_REPORT.md
tools/device/BACKUP_RESTORE_QUALIFICATION.md
```

`git diff --cached --name-only`:

```text
```

No actual staged-content diff, commit, tag, release or other repository mutation.
Remaining work is disposable-device execution of the prepared qualification matrix
and a qualified published shared-Java compatibility resolution
for API 30–33 followed by normal dependency/provenance/cache and renewed gates.
Interrupted attempts with missing/torn intent or missing attempted roots deliberately
require storage investigation; no unsafe automated recovery is substituted.
