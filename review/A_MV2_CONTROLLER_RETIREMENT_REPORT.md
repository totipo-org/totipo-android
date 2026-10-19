# A-MV2 — Android controller retirement and selection ownership

## Status and starting evidence

Implementation and required qualification complete, including the human-reported
final `nix flake check` PASS. Classification recorded before edits:
**PRODUCT_CODE, LIFECYCLE_SECURITY, TEST_OR_QUALIFICATION_TOOLING**.
Starting branch `main`, HEAD `a4fb097db257283b3aa70c934e013e0bd2306880`;
`git status --short` empty. A-MV1 is committed; no preexisting work was overwritten.
No commit, tag, release or push has occurred.

Read AGENTS.md, README, TOTIPO_JAVA_DEPENDENCY.md, package.nix, flake.nix, CI,
A-MV0 and A-MV1 reports, the biometric/inactivity and stale-subscriber ownership
reports, Java guidance/F08 clarification and current Sync/bootstrap qualification,
and related controller/coordinator/provider/storage tests and device tooling.
Fetched the exact read-only Java guidance at
`f0a028676c1801a10b2d8d2650bf9c24357d1fa1` into ignored local evidence;
operation taxonomy, replay/subscriber delivery, observation ordering, deterministic
bases, merge freshness, persistence handles and blocking/close contracts apply.
No Java/spec, dependency, lock, crypto, protocol, manifest, SDK or version changes.

## Lifecycle findings and ownership design

Previously Application held a single controller and LocalReplicaOwner.Admission
forever. Lock closed Java/coordinated storage via the existing worker afterExecute
boundary while detached SAF could remain blocked. Provider shutdown neither waited
for Binder nor proved that queued callback handoffs were consumed. Activities
assumed their controller stayed globally selected. Failed close already retained
session/domain/lease, and those semantics remain the cleanup foundation.

Application now holds one VaultSelection containing the sole Admission, selected
local entry ID, owner and controller. Each owner has a fresh immutable activation
UUID, fixed registry/root binding and exactly one controller binding. Controller
construction uses an entry-bound AndroidSyncFolderPort. Construction failure retains
admission and requires process recovery rather than creating replacement executors.
Concurrent selection/replacement attempts cannot construct another controller while
selected or partially constructed ownership exists. Internal selectLocalEntry uses
the same production factory for startup and disposable device qualification.
There is no public navigation or debug admission bypass.

## Retirement state machine and close ordering

Lock retains selection and permits later ordinary authentication. Permanent retire
sets an irreversible admission flag synchronously, cancels pending outbound admission
and Sync requests, invalidates presentation/session generations, retires pending
forms/changes and enrollment, conceals codes and retires owned clipboard content.
Queued normal commands keep their existing explicit credential/request cleanup;
entered KDF, local authoring and bridge work finishes on the existing worker.
No generic cancellation is inferred from VaultState arrival.

Retirement results are NOT_REQUESTED, WAITING_WORK, CLOSE_FAILED,
WAITING_PROVIDER, SUCCESS and SHUTDOWN. UI uses RETIRING/RETIRED neutral snapshots
and FAILED_CLOSE for closure retry. Repeated requests observe the same retirement;
only explicit retry after CLOSE_FAILED reattempts close on existing resources.

Worker afterExecute advances retirement without blocking main. It seals further
owner acquisitions, cancels the observation subscription, calls the existing
ForegroundVaultCoordinator.close ordering (Java session, logical facade completion,
physical coordinated-store domain, then LocalReplicaOwner lease). Failed closure
retains exact resources and selection. Once closed, the session is never reopened or
rediscovered while awaiting SAF. Only after provider drain does the worker shut down;
its terminated callback posts the final main-thread completion. Admission release
requires successful cleanup and absence of an active lease. Application observes
SUCCESS before transitioning to an explicit null/unselected snapshot. B can be
constructed only afterward. No timed ownership release exists.

Shutdown requests best-effort closure of admitted local work, including retirement
already pending, but reports SHUTDOWN and retains selection. Executor termination
alone cannot report successful retirement. A failed shutdown close is not repaired
by constructing another controller.

## Provider admission, drain and uncertainty

ProviderIoLane has irreversible admission closure. The entry guard is the operation
linearization point: work already entered finishes without interruption; queued work
skips provider entry. An entered probe returning after retirement does not begin its
subsequent scan. Outbound writers retain their existing cancellation checkpoints,
try-with-resources stream ownership and conservative persistence outcomes. No writer
rollback, provider deletion, output retry or new transport algorithm is introduced.
A write already inside SAF may finish remotely after retirement was requested.
It remains A's write against A's immutable port/root.

Drain requires actual provider executor termination **and** zero counted completion
handoffs. All production provider completion routes count time queued/executing on
main and vault worker, including nested handoffs. Retired queued callbacks discard
detached data; entered callbacks retain A's captured controller and finish before
the boundary clears. Rejection and inline failure acknowledge their tickets exactly
once. A late provider result is discarded rather than delivered into retired state.
No Future cancellation, interruption, shutdown return or fixed delay proves drain.
A stuck provider keeps A selected indefinitely, with a sensitive-free explanation
that process restart may be required. No replacement lane is constructed beside it.

## Activity and callback ownership

Activities observe Application selection snapshots. MainActivity retains one
activation-bound controller and recreates its presentation when selection changes;
it never redirects an old button, biometric callback or controller callback to B
through a late current-controller lookup. Retirement rebuilds the neutral surface,
clears password/setup widgets, token rows/search presentation and dialogs, and closes
pending prompts. Activity stop/destruction retains established sensitive cleanup.
Application foreground delivery tolerates an explicit unselected state; the existing
15-minute monotonic inactivity policy remains unchanged.

SAF launches remember the originating activation; only that activation can accept
the result while still authoritative. Its non-secret activation is retained only through the platform nonconfiguration
instance across configuration recreation; no picker identity, credential or draft is
saved in a Bundle. Process death drops the capability. Process restart creates a different
activation and rejects stale results. No stale result takes/releases a persisted
grant. External enrollment stays with its captured controller; retirement or
replacement expires its draft and submission presentation. Recreation still requires
fresh external input. Late Java callbacks are logically retired by controller and
coordinator identity checks even if callback cancellation cannot retract entered code.

## Deterministic coverage and diagnosis

ControllerRetirementTest uses the exact VaultSelection held by Application, real
coordinated storage/leases and released Java with existing test-only fault hooks.
It covers A → B → A → B with independent password authentication; locked/error/open/
closing retirement; idempotence and concurrent construction; blocked open/KDF,
create, local save, coordinated import, Java observation, candidate authentication
and Join enrollment; session/domain closure failure and retry; an outstanding lease;
provider scan/create/write/readback/postflight; queued main/worker handoffs and late
entered Java callbacks; shutdown during admitted work, provider wait, and a queued
Application success notification. No new negative assertion uses a sleep as evidence.
Existing process-restart fixtures now wait for worker termination and construct fresh
process admission against the same persistent root.

ProviderIoRetirementTest separately holds queued, entered and nested handoffs,
rejects late provider submissions, proves queued work never enters, and verifies
actual stream closure and completion of entered create/write/readback/postflight.
Rejected and inline-failed handoffs acknowledge once. No cancellable Future is
used as completion evidence. An accepted write can leave A's provider bytes even
when its completion is discarded; B never acquires that operation.

Main-thread retirement must also survive **metadata** I/O. Grant validation,
provider-result permission checks, and post-authentication canonical rereads now
run off the controller monitor and recheck activation/session ownership afterward.
Main admission/presentation uses cached URI/generation/binding metadata. A rejected
provider completion defers binding cleanup to the existing worker's afterExecute
rather than waiting on the binding monitor from provider/main. Retry-hint writes
run outside the controller monitor, while command/provider completion remains held;
the existing worker boundary persists any remaining dirty hint before closure.
No third executor is introduced. Latch cases hold each of these boundaries and
assert that the actual I/O callback does not hold the controller monitor, that UI
queries and retirement return while it remains held, and that B is unavailable.

Debug work is activation-bound too. The RFC fixture's former global boolean request
is now a one-shot originating-activation request; a different activation discards it.
The debug Activity captures that selection rather than looking up another controller
when an old button fires. Authentication read probes capture the selected owner on
main and never rediscover B from their worker. The shared lease still gates them;
an outstanding lease prevents successful retirement and permits explicit retry.
The disposable-create bypass remains disabled.

Execution history (all logs under ignored `.gradle/amv2-qualification/`):

- Baseline ordinary full PASS, 1m42s; compile/tests were up to date, without cache
  forcing or clean. It started from the recorded clean committed checkout.
- Focused 1: compilation failed on illegal forward references; corrected.
- Focused 2: old Sync source-guard region included newly inserted retirement
  methods; the methods moved outside that region. A simulated process restart
  also tried binding a second controller to an activation; its test admission was
  corrected. Source guards were preserved.
- Focused 3–4 PASS: initial and expanded retirement/provider cases.
- Focused 5: two existing shutdown tests exposed an overbroad discovery guard.
  Ordinary shutdown's established locked-state behavior was restored; permanent
  retirement continues to suppress rediscovery. Focused 6 PASS.
- Final-normal attempt 1 FAIL (2m10s): a restart-fixture race plus unchanged source
  guards against saved form state and the foreground-count shape. Restart fixtures
  now await actual worker termination. Picker ownership uses a nonconfiguration
  activation capability instead of Bundle state; foreground guards retain their
  existing shape and checks. Focused 7–8 PASS, including the original guards.
- Focused 9–10 PASS: expanded switching, shutdown handoff, foreground/debug ownership.
- Final-normal attempt 2 emitted PASS (2m19s) but was invalidated by in-flight
  production foreground/debug ownership edits; it is not final evidence.
- Final-normal attempt 3 PASS (2m30s), subsequently invalidated by the metadata-I/O
  monitor correction and its tests. This extra full run was necessary because those
  included production/test changes superseded its checkpoint.
- Focused 11: standalone compilation PASS. Focused 12: existing controller/Sync/
  security tests passed; one new return-check fixture waited without pumping its
  required main handoff. That fixture now pumps controlled main deliveries.
  Focused 13 PASS (34s), including all new retirement cases, security tests and
  stale-observer ownership/source-guard regressions.
- Final-normal attempt 4 PASS (2m20s), the stable host checkpoint.

No existing source guard or qualification requirement was weakened. The multiple
ordinary full invocations above do not substitute for the final stable normal and
strict checkpoints. Strict-clean PASS (3m34s; all 91 tasks executed). All 386 JVM tests passed
with zero failures/errors/skips, including 22 controller and five provider retirement
tests. Both external APK policy checks PASS; offline runtime/Maven boundary PASS
(2s). Runtime remains core/NIO 0.2.0 plus BC 1.86 only. Manifest/version boundaries
passed the external verifier (0.1.3 / code 4, unchanged).

Final strict-clean APK SHA-256:

- Debug: `206bed3722d531eb9f98c6e3e40540eed7d952b1fd4d09f5f1d2ec0fd6ac8a80`
- Release unsigned: `7912ce05f1042d3bcaae4e8fa223e8b152abcbee7967eb9e79ef894ebc04b5c7`

## Device qualification

A Pixel 6a (serial `37311JEGR05916`, Android API 37) was connected and authorized
by the owner. The disposable package was verified absent before the first install.
No owner application installation/data, vault, credential, grant or file was touched.
Only fresh `org.totipo.securityqualification` installations were used.
ControllerRetirementRegression is integrated with the existing disposable runner.

Retirement physical attempts:

1. Harness crashed on a null Add control before rendered UI was ready. The harness
   now waits for the actual control, and checks the unlocked-device prerequisite.
2. Stopped behind keyguard: the phone had relocked during the host build and UI-idle
   synchronization could not finish. The owner unlocked it again; no bypass was used.
3. **PASS, 26 checks** against final release DEX, including an explicit filled
   form-secret retirement assertion. Log: `.gradle/amv2-qualification/device-retirement-3.log`.

The affected existing lifecycle harness initially failed with NoSuchFieldException:
it reflected the removed permanent Application controller field. Its setup now
retires the startup controller and selects its fixture through the exact Application
VaultSelection/Admission, with only fixture clock/transport construction different.
No field replacement or separate admission authority remains for the selected test
controller. Existing edit/delete/resolve and automatic-publication assertions remain;
the peer Java branch is the existing isolated test-only remote-authoring fixture,
not an application-selected controller. Cleanup uses complete retirement before
removing cache transport files. Corrected physical rerun **PASS, 53 checks**: edit/delete/resolve UI, real Java
conflicts, same-session ownership and automatic publication. Log:
`.gradle/amv2-qualification/device-lifecycle-2.log`. All five known disposable
installations have been removed; final `pm path org.totipo.securityqualification`
returns no package. Both physical successes correspond to the strict-clean release
APK. The package receipt matches its SHA-256, and zip comparisons confirm both
release DEX files and all resources are byte-for-byte equal to the qualified APK.

Only tools changed after host qualification. No Gradle consumer reads either device
harness; these changes require compilation/affected physical rerun and complete
input freeze before Nix, without invalidating production/JVM/APK evidence.

The harness uses actual Application selection/factory and qualified release DEX.
It holds a synthetic scan in the actual provider lane; checks immediate snapshot,
clipboard and form retirement; closes the session before provider drain; denies B;
rotates while waiting; verifies replacement and current-foreground transfer; rejects
late callback, stale picker and external enrollment; and requires B's independent
password. A test-only deadline adjustment exercises the unchanged fixed 15-minute
policy on background/foreground return. This does not claim a real provider's Binder
cancellation, or peer/Syncthing delivery; the synthetic blocked port models an entered
call which cannot be interrupted. No unrelated row or full Syncthing campaign is run.

Run retirement against a **fresh** disposable package; it deliberately creates two
registry entries, so it is not a subsequent cold-start/navigation fixture:

```sh
python3 -B tools/device/qualify-security.py --serial SERIAL --prepare
python3 -B tools/device/qualify-security.py --serial SERIAL --phase retirement
python3 -B tools/device/qualify-security.py --serial SERIAL --remove
```

Then use a separate fresh disposable installation for the affected existing lifecycle
phase, with the same prepare/remove boundaries. `--prepare` refuses an existing test
package. Never remove an unreviewed installation to satisfy this prerequisite. Only
this run's known isolated `org.totipo.securityqualification` may be removed. The owner
application `org.totipo.android` is never installed, updated, reset or uninstalled.

## Qualification, freeze and execution counts

Final normal attempt 4, strict offline clean, both external artifact verifiers and
offline runtime/Maven boundary PASS. Final disposable packaging compiles the harness
against strict-clean release classes and copies the exact qualified release DEX.
Physical gates PASS. After the complete freeze below, the owner reported
**`nix flake check` PASS** on 2026-10-19. This is human-reported evidence; the agent
did not execute Nix or independently capture its terminal output. Post-result
verification found zero changes across all 178 frozen inputs, with the inventory
SHA-256 unchanged. Only this excluded report was updated to record the result;
no qualification was invalidated. All required milestone gates are complete.

Source membership was inspected from package.nix/flake.nix and actual consumers.
Android inputs include app/src, gradle and tools plus the exact Gradle/metadata files;
evaluator/cache and release-check inputs are also tracked. README, root AGENTS and
review prose are excluded and not read by the checks. Every new milestone file is
Git-known through intent-to-add only. No actual file contents are staged.

Execution counts:

| Check | Invocations and outcome |
| --- | --- |
| Baseline ordinary full Gradle | 1 PASS |
| Focused test Gradle | 12; failures/diagnosis above |
| Focused standalone compile | 1 PASS |
| Final normal full Gradle | 4: first FAIL, second/third invalidated, fourth PASS |
| Strict offline clean Gradle | 1 PASS |
| External final APK verifier | 2 commands, debug/release PASS (one final set) |
| Embedded APK verifier | 0, per current build policy |
| Final runtime/boundary Gradle | 1 PASS |
| Physical retirement | 3: harness crash, keyguard stop, PASS 26 checks |
| Physical lifecycle | 2: obsolete-field harness failure, PASS 53 checks |
| Host device build-only | 8: initial capture compilation failure, seven PASS |
| Host device-tool unit tests | 3 invocations, eight PASS each |
| Disposable prepare/install and removal | 5 each; final package absent |
| Human Nix | 1 final routine check, owner-reported PASS |
| Agent Nix | 0 |

The final normal and strict commands were respectively:

```sh
gradle check :app:assembleDebug :app:assembleRelease
gradle --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug :app:assembleRelease
```

External verifiers used debug `--debug-probe` and release
`--unsigned --no-debug-probe`. The final runtime command was
`gradle --offline :app:dependencies --configuration releaseRuntimeClasspath :app:verifyMavenBoundary`.
No repeated strict or artifact gate was needed: later changes were standalone
physical harnesses without Gradle consumers. Their affected physical phases passed
before final Nix was requested. This follows the tooling row of the invalidation
matrix and does not weaken artifact evidence.

Final freeze, 2026-10-19:

- **Production source stable.** Normal/strict/artifact and device-source receipt
  correspond to unchanged production inputs; no production hash differences since
  the host checkpoint. Both device packages used exact qualified release DEX/resources.
- **JVM tests stable.** All 386 pass at strict-clean; no later JVM test edits.
- **Build/package configuration stable.** No dependency, evaluator or build edits;
  runtime/manifest/APK boundaries PASS.
- **Qualification harness/tooling stable.** The two updated device harnesses compile
  and pass their affected physical phases; eight host device-tool tests PASS at the
  freeze. No tooling edits follow this freeze.

A 178-file final included-input inventory is stored at
`.gradle/amv2-qualification/final-frozen-inputs.json`, SHA-256
`851d919a8a294f2b371c22e3baa7cef4528e8f0fdc41d388d1edbee23fd35ccd`.
Compared with host-frozen-inputs.json, only ControllerRetirementRegression.java and
TokenLifecycleRegression.java differ. Earlier invalidated inventories are retained.
Before requesting human Nix: `git status --short` and `git diff --name-only` show
exactly the 21 intended paths below; `git diff --cached --name-only` is empty and
`git diff --cached --quiet` PASS. Five new files remain intent-to-add; no ignored
build/cache/secret inputs entered source membership. `git diff --check` PASS.

Any later included-input change must follow the invalidation matrix. Report/README
updates alone are excluded and do not invalidate these gates or the final Nix PASS.

## Deferred scope and material limitations

A-MV3 still owns complete per-entry Sync/grant/retry management; A-MV4 owns per-entry
biometric records/keys; A-MV5 owns zero/one/multiple-entry selection and recovery UI.
Current production startup still refuses multiple entries and never auto-switches.
Production remains password-only. No inactive-vault Sync, simultaneous sessions,
protocol/format change, cross-vault operation, new dependency or navigation framework.
Arbitrary SAF/Binder cancellation cannot be guaranteed; a hung operation deliberately
prevents switching until process termination. Clipboard and JVM wiping remain best
effort under the established platform model. Physical evidence uses a controlled
blocked transport; it does not establish that arbitrary third-party SAF/Binder calls
can terminate. Retaining ownership indefinitely in that case is intentional.

## Changed files and final Git state

Changed files (21):

- README and this report.
- Application/selection/storage: TotipoApplication, new VaultSelection, LocalReplicaOwner.
- Controller/transport: AndroidVaultController, sync/ProviderIoLane.
- UI ingress: MainActivity, OtpAuthEnrollmentActivity.
- Debug activation ownership: ApplicationVaultBackend, DebugTotpFixtureActivity,
  debug/AuthPerfActivity, reconcile/DebugTotpFixture.
- JVM fixtures/tests: AndroidVaultControllerTest, new ControllerRetirementTest,
  new sync/ProviderIoRetirementTest, sync/PublicationPort, debug/AuthPerfSafetyTest.
- Device tooling: new tools/device/ControllerRetirementRegression.java and
  tools/device/qualify-security.py, and updated TokenLifecycleRegression.java.

All changes are unstaged and intended for review. Five new files have intent-to-add
source membership only. No staged-content diff exists; no commit, tag, release or
push occurred. Starting clean baseline is preserved in Git. Current status is 21 modified/intent-added paths listed above, on unchanged `main`
HEAD `a4fb097db257283b3aa70c934e013e0bd2306880`. `git diff --check` PASS;
`git diff --cached --name-only` empty and `git diff --cached --quiet` PASS.
Host/device success, complete input freeze and the human Nix PASS are recorded
above. Final HEAD remains the starting commit; final status has exactly the 21
intended unstaged/intent-added paths. No staged contents, commit, tag, release or
push occurred. Changes remain available for review.
