# Android stale subscriber ownership fix (F09)

Status: COMPLETE. Implementation, deterministic regressions, focused/final Gradle,
artifact, physical lifecycle and final human Nix qualification PASS.
All changes remain unstaged/uncommitted. Only F09 is addressed.

## 1. Starting HEAD/state

Started on clean `main`, HEAD `11d5c1215217470beff62093e0249817b1f8f2cd`;
`git status --short` was empty. Committed prerequisites: biometric/inactivity Lock
`45a7006`, qualification ladder `ac5e789`, Java guidance/audit `11d5c12`.
`git show HEAD:review/ANDROID_JAVA_OPERATION_MODEL_AUDIT.md` contains F09,
“Long-lived subscriber signals lack controller session ownership.”
Read AGENTS.md first, then README, provenance, package.nix, flake.nix, CI,
controller/coordinator code, tests, security/lifecycle harnesses and biometric report.
The existing managed environment supplies pinned Nix JDK 17, SDK 37/build tools 36
and wrapper Gradle 9.8.0; no Nix command was run by the agent.

## 2. Qualification classification

`PRODUCT_CODE` + `CONTROLLER_LIFECYCLE`. Client ownership/cancellation correction.
No `UI_PRESENTATION`: existing presentation behavior is untouched; irrelevant
retired-owner callbacks are discarded before reaching it.

## 3. F09 restatement

FC.observe checks its cancelled AtomicBoolean before invoking an application
Runnable. An entered Runnable can resume after Lock and replacement ownership.
Previously synchronized AC.signal had no session/coordinator ownership proof and
could mutate replacement dirty/presentation or observation-failure state.

## 4. Exact pre-fix race

Real Java delivery on session A enters FC's subscriber, passes cancelled check,
then enters a test-only wrapper around the captured AC Runnable and waits on a
latch. The test Locks A, verifies coordinator A CLOSED, password-opens B, waits
for B's initial subscription delivery and healthy idle state, then releases A.
Java cancellation/close never waits for that entered application callback.

## 5. Deterministic reproduction

Two behavioral tests run before any production edit. Test-only
ProductControllerFixtures wraps the real released VaultSession's states publisher
with a forwarding proxy. At subscription establishment it decorates FC's captured
`val$changed`/`val$failed` Runnable fields using reflection. Decoration-count
assertions prove both callbacks on both owners were instrumented; a seam mismatch
fails the test. All actual signal delivery, demand, cancellation and terminal
serialization still belong to released Java. No production timing hook was added.

CountDownLatch establishes entry, release and callback completion; semaphores
acknowledge initial/current state delivery. Existing test readiness helpers poll
idle/UI state; their polling is not the race mechanism. No new sleep was added.
Bounded waits fail on missing progress, never establish correctness by elapsed time.

First pre-fix run: both tests fail with presentation epoch expected 12, actual 13.
Second pre-fix run moved observationFailed ahead of epoch in assertion order to
capture both distinct consequences: ordinary signal expected epoch 12, actual 13;
terminal expected observationFailed false, actual true. Production remained at HEAD
for both runs. Evidence is preserved in `.gradle/f09-qualification/` as
`totipo-f09-prefix{,-direct}.{log,xml}` (ignored, excluded build evidence).

## 6. Java callback/cancellation boundary

Read exact API_DESIGN at guidance commit
`b03f5b22f367723ce4a3bddf0a56b159a06f32cf`, fetched read-only from canonical GitHub
because no sibling checkout exists. SHA-256 matches provenance:
`054c2f432420e9b7a39f5f661bde2b9ae773d2d2ccb29f3adfa949078ee44c0d`.
Reviewed operation classes/state semantics, replay-latest, editing/causal bases,
merge freshness, persistence handles and blocking/threading/close sections.

onSubscribe is synchronous on the subscribing thread. Subsequent callbacks are
asynchronous, serialized per subscription; different subscriptions may overlap.
Cancellation abandons delivery and cannot retract entered Android application
code. Close does not wait for subscriber callbacks. Lifecycle/late-result relevance
belongs to the client; newer VaultState is not a generic cancellation token.
No Java completion wait, API or implementation change is needed.

## 7. Chosen Android ownership token

The exact ForegroundVaultCoordinator instance installed by opened(), captured
immutably by both callback lambdas. `observationOwner` is a controller-monitor
protected identity reference, not another generation counter. Existing
sessionGeneration intentionally advances at requestLock (request/result retirement)
before actual resource close, so it does not precisely represent failed-close
ownership. Coordinator identity represents that ownership directly.

## 8. Why cancelled was insufficient

FC's AtomicBoolean comparison and AC's later mutation are separate events on
separate synchronization boundaries. Setting cancelled after the first event cannot
prevent the second. FC's existing fast cancellation guard remains unchanged.

## 9. Controller mutation-boundary fix

opened() installs observationOwner under the AC monitor before subscribing and
passes that same coordinator to signal. Synchronized signal returns immediately
if source differs from observationOwner or shutdown has begun. The comparison
precedes clearPresentation, presentationChanged, dirty, observationFailed,
scheduleView and any listener-visible or admission effect. Successful close clears
the owner under the same monitor. Failed close retains it.
Only AndroidVaultController.java changes in production (nine additions, three
removed lines). ForegroundVaultCoordinator is unchanged.

## 10. Stale onNext regression

`staleObservationSignalFromRetiredSessionCannotMutateReopenedSession` uses a real
refresh delivery from A, holds it beyond FC cancellation relevance, then exercises
actual Lock/password reopen to B. After release: observationFailed false, dirty
false, presentation epoch unchanged, entire healthy snapshot unchanged, state OPEN,
error NONE, Add admission true, and Refresh admitted. Epoch proves absence of
mutation even if a queued view worker could otherwise erase dirty evidence.
No active reveal/UI text coupling is needed: the underlying presentation mutation
is directly asserted, as allowed by the milestone.

## 11. Stale terminal regression

`staleObservationTerminalFromRetiredSessionCannotFailReopenedSession` triggers
real Java completion with session.close, holds its entered terminal Runnable,
then Locks/reopens. It checks the same healthy replacement invariants, including
observationFailed remaining false and Add/Refresh admission.
FC maps onComplete and onError to the same failed Runnable and guarded signal(true);
the deterministic terminal test exercises onComplete, and the guard is shared
unchanged for onError.

## 12. Current-session positive controls

Both races request a real current-session refresh afterward and acknowledge its
state callback; the presentation epoch still advances under existing policy.
The terminal race then closes B and verifies ERROR_OPEN, observationFailed true,
Add denied and Refresh denied. Existing
endedJavaSubscriptionProducesErrorAndGatesOperations remains unchanged.

## 13. Lock/reopen ownership behavior

Manual and inactivity Lock share requestLock/retireRequestedLock/closeOwned.
Request generations, deadlines, LOCKING presentation and worker retirement remain
unchanged. Successful close removes observationOwner; ordinary reopened() installs
a new exact coordinator before its callbacks. Join success also calls opened().
Create/open failures retain or clear resources through existing closeOwned;
no alternate subscription install exists. Shutdown sets existing shuttingDown under
the monitor before requestLock, so callbacks cannot mutate during teardown,
including failed best-effort close. No lifecycle state enum is used as ownership.

## 14. Failed-close ownership behavior

`enteredObservationRetainsOwnershipAfterFailedClose` holds an entered terminal,
injects close failure and verifies FAILED_CLOSE retains the exact owner. Releasing
the callback still sets observationFailed and advances the presentation epoch;
FAILED_CLOSE remains truthful and new open/Add remain denied. Retry Lock succeeds
and clears observationOwner. Existing session/domain-close retry tests also remain.
Subscription cancellation still happens before attempted close; this control concerns
already-entered application code, not delivery after cancellation.

## 15. Password/biometric reopen implications

Both authenticate branches call the same opened() after successful ordinary open.
Biometric expected-VaultId mismatch closes through closeOwned instead. No biometric
production change or distinct subscription ownership path exists. Existing security
JVM tests cover biometric reopen; no new biometric fixture/enrollment is needed.

## 16. Threading and lock order

The new work inside synchronized signal is an identity comparison and existing
shutdown boolean read. Owner installation/clearing also uses only the AC monitor.
No Java/session/provider/store method is added to that critical section; no
publisher lock, provider bridge or coordinated store gate is acquired by the check.
Java callback threads enter AC briefly and follow the existing mutation/queue path.
The worker never waits for subscriber cancellation/drain or callback completion.
The test waits are outside the controller monitor. No new W-waits-S/S-waits-W cycle
or blocking cancellation is introduced. Existing render/snapshot behavior is retained.

## 17. F02 explicitly unchanged

Current-owner state arrival still clears presentation and marks dirty as before.
No token-local TOTP relevance redesign or broader state-snapshot policy change.

## 18. F08 explicitly unchanged

No change to Sync barrier, two-Finished logic, requestRefresh implementation,
observation counting, timeout or Java composition assumptions.

## 19. Dependency/API/protocol invariance

Runtime NIO/core remain 0.2.0, BC 1.86, protocol v1/r19; released Java source pin
remains d6310c177ae930df188fd4f5798622c935698b2e. No dependency, build, lock,
verification metadata, package-deps, package.nix, flake input, CI, manifest permission
or production signing input changes. Final Git path allowlist verifies this.

## 20. Focused test results

Three new ownership regressions PASS after correction. Broader affected
AndroidVaultControllerTest, SecurityControllerTest, InactivityLockTest and
ForegroundVaultCoordinatorTest: PASS, 126 tests (83 controller, 14 security,
6 inactivity, 23 coordinator), zero failures/errors; 1m 18s.
Logs: `.gradle/f09-qualification/totipo-f09-fixed.log` and focused log.

## 21. Qualification-ladder invocation counts

| Category | Count/result |
| --- | --- |
| Baseline ordinary full Gradle | 1 PASS (1m 52s; 90 tasks, 13 executed) |
| Focused F09 invocations | 4: 1 task-selection typo (no tests), 2 expected red runs, 1 green three-test run |
| Other focused tests | 1 PASS, 126 tests across four affected suites |
| Focused compile-only | 0 |
| Final normal full | 1 PASS, 337 tests, 2m 35s |
| Strict offline clean | 1 PASS, 337 tests, 3m 4s; 92/92 tasks executed |
| External artifact verifiers | 3 PASS: wrapper + strict debug APK + strict release APK |
| Embedded Gradle APK verifier executions | 3 total: baseline 1, final normal 1, strict 1 |
| Final runtime/Maven boundary invocation | 1 PASS, offline; 2 tasks executed |
| Physical harness phase runs | 1 PASS: existing lifecycle phase, 51 checks |
| Human Nix reported runs | 1 PASS, human-reported after final input freeze |

The typo accidentally passed `fix.log` as a task (space in log redirection), and
failed task selection before test execution. Its output is saved as
`.gradle/f09-qualification/totipo-f09-pref`. The second red run improved failure
evidence by asserting observationFailed first; it was focused, not a repeated full
gate. No repeated or invalidated broad gate.

## 22. Final normal gate

PASS: `./gradlew check :app:assembleDebug :app:assembleRelease`; 2m 35s,
90 tasks (22 executed, 68 up-to-date), 337 tests, zero failures/errors/skips, lint,
both assemblies, Maven boundaries and embedded release APK verification.
Log: `.gradle/f09-qualification/totipo-f09-final-normal.log`.

## 23. Final strict gate

PASS: `./gradlew --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug :app:assembleRelease`.
3m 4s, 92/92 tasks executed, 337 tests with zero failures/errors/skips.
Log: `.gradle/f09-qualification/totipo-f09-strict.log`.
No input changed afterward; this is the only strict-clean invocation.

## 24. Final artifact verification

All four commands PASS against strict outputs:

```sh
python3 tools/verify-wrapper.py
python3 tools/verify-apk.py app/build/outputs/apk/debug/app-debug.apk --debug-probe
python3 tools/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk --unsigned --no-debug-probe
./gradlew --offline :app:dependencies --configuration releaseRuntimeClasspath :app:verifyMavenBoundary
```

Reviewed runtime graph: sole direct NIO 0.2.0 -> core 0.2.0 -> BC 1.86, with
strict constraints; external Maven boundary passes. Reviewed unchanged source
manifest plus packaged verifier checks: biometric permission retained, Internet
permission absent, services/ingress/backup boundaries unchanged, branding and DEX
presence valid, debug probes required only in debug, release unsigned and free of
probe/test classes. Wrapper JAR/distribution pins verified.

Strict APK SHA-256:

- Debug: `794638e6848e10bcdc6a47310089dcc2416da9f43e64cbd5af39dd17150ba9f9`.
- Unsigned release: `ba4ea497e5af53fad43ed1ca725cd9afbf12bac4e3d3ea87aa7ba8eee4c255ae`.

Logs: `.gradle/f09-qualification/totipo-f09-artifact-{wrapper,debug,release,runtime}.log`.
The runtime command took 1s, two tasks executed. No intermediate APK was verified
outside the existing embedded Gradle check.

## 25. Physical qualification disposition/results

AGENTS.md requires relevant lifecycle/security device qualification for a lifecycle
production change. Selected existing `qualify-security.py --phase lifecycle`,
using an isolated disposable package containing final strict-release DEX/resources.
This verifies healthy controller/UI operations and Lock. Its existing phase does
not perform password reopen; deterministic JVM tests directly prove cross-session
Lock/reopen ownership and current-session admission. No new timing-based physical
race or harness edit is needed. No unrelated SAF, M3D, row geometry, otpauth or
biometric enrollment phases are selected. Connected device: Pixel 6a, Android 17;
human confirmed unlocked. PASS, 51 checks, one phase run:
`python3 tools/device/qualify-security.py --prepare --phase lifecycle`.
The helper recorded qualified source SHA-256 matching the final unsigned release
`ba4ea497e5af53fad43ed1ca725cd9afbf12bac4e3d3ea87aa7ba8eee4c255ae`.
Log: `.gradle/f09-qualification/totipo-f09-physical.log`; original helper phase log
is `.gradle/security-qualification/lifecycle.log`.

Removed only the disposable package using `qualify-security.py --remove`; removal
PASS, and `pm path` confirms absence. The cleanup assertion initially treated
pm's expected exit 1 for an absent package as an exception; corrected the read-only
assertion to accept exit 0/1 with empty output. No harness/gate rerun or input change.
Temporarily extended screen timeout after the device relocked, then restored and
verified its original 30000 ms value. No production app installation/replacement.

## 26. Input freeze and human Nix result

Actual package.nix selects app/src, gradle, tools and exact listed build/lock/version/
license/branding files, excluding .git/.gradle/build/.direnv components and symlinks.
Evaluator/cache inputs include package.nix, flake.nix, flake.lock, package-deps.json.
flake/CI/tool consumers inspected: review/ is excluded and not read by checks.
The two modified JVM files and production file are included qualification inputs;
this report is excluded. Later report-only edits do not invalidate Gradle/Nix.

Freeze recorded before final normal gate; no subsequent included-input edit:

- Production source stable: final controller hash recorded in frozen-source.sha256.
- JVM tests stable: both changed JVM files hashed there; three new tests and 126
  affected-suite tests passed before the final checkpoint.
- Build/package configuration stable: no configuration/dependency/lock/CI path changed;
  all effective evaluator/cache inputs are hashed.
- Qualification harness/tooling stable: tools/ entirely unchanged; existing physical
  phase selected, with no new harness code or prose.

`.gradle/f09-qualification/frozen-effective-inputs.sha256` covers 156 effective
source/evaluator/cache inputs. Manifest SHA-256:
`bfe94e3b477a64f50f23ecb0708574a503d2dfbdbaea74f81a72b796e3f92c52`.
Rechecked all 156 entries after Gradle/artifact/physical qualification: PASS.
Only excluded report text changed after the source freeze. No pass was invalidated.
All four freeze statements held before requesting the final human Nix check.
Human `nix flake check path:.`: **PASS**, reported by the user after the final
input freeze. Exactly one human run reported; duration/output not supplied.
No agent-run Nix command. Rechecked all 156 frozen inputs after the reported PASS:
all match. Only this excluded report is updated afterward; the human Nix PASS
remains valid under the inspected source/evaluator dependency rules.

## 27. Remote CI/release disposition

No staging, commit, tag, release, push or CI dispatch. No Java repository mutation.
No production signing configuration change. A disposable physical harness may use
its existing test-only signing mechanism; production release remains unsigned.
Remote CI is not claimed as fresh evidence.

## 28. Changed files and final Git state

Expected exact changed-path allowlist:

- app/src/main/java/org/totipo/android/AndroidVaultController.java
- app/src/test/java/org/totipo/android/AndroidVaultControllerTest.java
- app/src/test/java/org/totipo/android/reconcile/ProductControllerFixtures.java
- review/ANDROID_STALE_SUBSCRIBER_OWNERSHIP_FIX_REPORT.md

Branch/HEAD remain main/11d5c1215217470beff62093e0249817b1f8f2cd.
Final status after human Nix: exactly the three modified app source/test files
and untracked report listed above; all unstaged, index empty. `git diff --check`
PASS. Build/evidence outputs remain ignored under .gradle/ and app/build/.
No included-input edit followed the final gates or human Nix PASS. Only this
excluded report was updated to record the human result and final Git state.
