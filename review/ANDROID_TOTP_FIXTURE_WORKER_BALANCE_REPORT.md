# TOTP fixture and test-worker balance investigation

Classification: TEST_OR_QUALIFICATION_TOOLING. Started on clean `main` at
`a7cc3f1e80decea4553070636fb75a1671bbe7fd`; starting status empty. The user
requested investigation of remaining setup-only fixture creation and worker
balance after committing the preceding performance milestone. No ordinary
product baseline is required for this test-only scope.

Inspected AGENTS.md, README, development documentation, package.nix, flake.nix,
CI, Gradle configuration, existing performance/qualification evidence, affected
fixtures and saved test XML. Pinned Nix-managed Gradle 9.8.2/JDK 17/Android SDK
remain in use. No production, build, dependency, lock, package or harness input
changes are planned. No Nix command is run by the agent.

## Setup-only fixture

TotpCoordinatorTest previously created a vault before every case, despite
asserting TOTP/token/session behavior rather than vault creation. It now creates
one encrypted bootstrap per class with released NioTotipo, closes that seed
session, and copies only ciphertext into each case's fresh registry/root with
CREATE_NEW. Each case performs real password authentication and authors its own
initial token through the unchanged released Java path. Password parameters,
KDF, token publication, state observation, cryptography and all test assertions
remain real. No session, writable directory, credential buffer or TOKEN file is
shared across cases. The shared protocol identity is setup data; no assertion
requires independent protocol identities between these isolated cases.

All 12 @Test method bodies, including RFC vectors, conflicts, missing parents,
stale projection rejection and closed-session refusal, remain byte-identical.
The setup Operations seam now captures the real opened session, rather than
the real created session. Setup also checks the open result and password wipe.
The existing author/await helpers are unchanged.

The exact Java guidance at f0a028676c1801a10b2d8d2650bf9c24357d1fa1 and released
0.3.2 API_DESIGN/docs/BACKUP_ARCHIVE at
af11553659dd8bd4157064c7e3de27bed8980ac5 were reviewed in this session. Verified
local hashes match the exact-revision copies already compared with GitHub.
Class G open/create/close work retains independent ownership; class A/B/C
projection/construction/generation in the test bodies is unchanged. There is no
change to Android scheduling, Java state validity, subscription demand,
observation completion, admission or provider coordination policy.

Fresh focused measurements use a temporary init script that disables only test
task up-to-date/cache reuse. Before: 12 PASS, test task 8.201 seconds, command
9.487 seconds. After: 12 PASS, task 3.964 seconds, command 5.472 seconds including
compilation. The task includes seed generation and all case setup. This is one
local observed comparison, not a statistical or whole-build speedup claim.

## Worker-balance experiment

Four test processes and their default heaps remain unchanged. No method
parallelism, task partitioning or custom class scheduler is introduced.
Before/after class listeners record worker identity, class start/end, count and
duration. Diagnostic scripts/logs/source snapshots remain ignored under
`.qualification/fixture-balance/` and are not package inputs.

The first complete suite with the improved TOTP fixture and original retirement
class passed all 535 tests in 57 classes. Task duration: 88.833 seconds; class
span 87.500 seconds. Worker finish times from first class start: 59.279, 69.020,
83.988 and 87.500 seconds. Exactly one/two/three/four classes were active for
3.548/14.987/10.006/58.957 seconds respectively. These are active class intervals,
not measured CPU utilization. Largest indivisible retirement class: 33.076
seconds in that isolated run (43.136 in the earlier contended strict build).

The candidate redistributes 26 retirement methods into 17 lifecycle/biometric
and nine provider cases, using an abstract per-instance fixture with no @Test
methods. The real registry, owner, coordinator, backend, latches, cleanup and
all operation-specific assertions remain intact. Only top-level fixture helper
visibility changes from private to protected. A mechanical comparison verifies
every test body appears byte-identically exactly once.

Audited shared state: each test has a fresh TemporaryFolder, registry, selection,
controller and platform fixtures. The existing static ForegroundVaultCoordinatorTest
fixture is used serially within each JVM, and process workers do not share its
memory. Its temporary filesystem paths are unique. The default one-class-at-a-time
execution within each worker must be retained; enabling method parallelism would
require a separate fixture design. No external class-name/reflection dependency
on ControllerRetirementTest was found.

The focused candidate passed all 26 retirement cases (18.616-second command,
including compilation). No performance result is inferred from focused
retirement timing versus the earlier contended class duration.

The first split full suite passed all 535 methods in 58 classes but took 93.477
seconds (class span 92.750). Worker finishes: 49.014, 57.553, 85.955 and 92.750
seconds. Exactly one/two/three/four classes were active for
6.900/28.430/8.692/48.726 seconds: worse balance than the first unsplit run.
The comparison used A-B-B-A ordering, keeping the improved TOTP fixture fixed:

| Run | Retirement layout | Test task seconds | Command seconds | Classes / tests |
| --- | --- | ---: | ---: | --- |
| A1 | Original | 88.833 | 89.769 | 57 / 535 |
| B1 | Split | 93.477 | 94.218 | 58 / 535 |
| B2 | Split | 89.385 | 90.113 | 58 / 535 |
| A2 | Original restored | 83.474 | 84.266 | 57 / 535 |

All four runs passed the original 535-method inventory, normalizing only the
nine moved retirement cases in B. No methods were dropped, duplicated or skipped.
B2 worker finishes were 44.939/51.270/78.715/88.587 seconds; A2 finishes were
52.317/58.895/78.862/82.599 seconds. Time with only one or two active classes
was 35.330/37.389 seconds for B1/B2, versus 18.535/23.769 for A1/A2. No test
failure occurred. The split provided no measured benefit, so it was discarded.
The original retirement source was restored byte-for-byte; the two candidate
source files were removed only after verifying their exact match to saved
agent-generated copies. Candidate source/logs remain in ignored diagnostics.
No retirement change or new test class is included in the final implementation.
This small local comparison establishes no universal performance ranking.

Read-only bytecode inspection of pinned Gradle 9.8.2's
MaxNParallelTestDefinitionProcessor shows cyclic assignment of test definitions
to process queues. That dispatch method does not select an idle worker or
estimate class duration. Inference: splitting one class shifts assignments of
later classes; it does not inherently distribute total work more evenly. This
is diagnostic evidence, not a dependency on internal Gradle APIs in the build.

## Qualification and handoff

Because the TOTP fixture change is retained, selected final gates are the ordinary
normal Gradle checkpoint, strict offline clean Gradle checkpoint, both external
APK verifiers and release runtime/Maven-boundary review, followed by one final
human `nix flake check`. All starting 535 methods must remain, normalizing only
the retirement-class move if kept. No affected product/device subsystem requires
a physical run.

The current package filter includes app/src, gradle, tools and explicit
build/metadata files; evaluator/cache inputs remain unchanged. Separate release
checks use explicit tools/build/signing/workflow inputs. This excluded review
report and ignored diagnostic evidence do not invalidate qualification. New
intended source/report files must be Git-known via intent-to-add before the
final human Nix gate, with no staged contents.

Only the TOTP fixture improvement is retained. Its source and unchanged
Gradle/package/evaluator input hashes were frozen before the final normal gate
in `.qualification/fixture-balance/frozen-inputs.json`.

The final normal `gradle check :app:assembleDebug :app:assembleRelease` passed
in 87.392 seconds: 89 actionable tasks, nine executed, two from cache and
78 up-to-date. All 535 tests in 57 classes passed with zero failures/errors/skips
and the original complete inventory. Frozen input hashes remain unchanged.
Strict offline clean Gradle passed in 115.562 seconds, all 91 actionable tasks
executed. Its fresh suite again passed all 535 methods in 57 classes, with zero
failures/errors/skips and the complete starting inventory unchanged. The normal
and strict wall times are qualification observations, not a controlled whole-build
before/after benchmark. No included input changed after either final gate.

Final external artifact checks against strict-clean outputs:

| Check | Result | Seconds |
| --- | --- | ---: |
| Debug APK policy with debug probe | PASS | 3.458 |
| Unsigned release APK policy without debug probe | PASS | 2.807 |
| Offline release runtime graph / Maven boundary | PASS | 1.252 |

Debug SHA-256:
`5e53a71a56a02854016ca1bd5bf5697873c8846eaaa8c402073b2e46c18c411e`.
Release SHA-256:
`6e1446e57b620f5dce78bedf67038c5b398661cb26319107efad39ca689b34e4`.
The debug APK equals the saved starting artifact byte-for-byte. Release differs
from the pre-commit starting APK; its packaged version-control metadata now
records current HEAD a7cc3f1e80decea4553070636fb75a1671bbe7fd. This is consistent
with rebuilt commit metadata after the user's commit; the old release bytes were
not retained for a full ZIP-entry comparison, so release byte equality or an
exhaustive attribution of that hash change is not claimed.

Reviewed runtime graph: external storage-nio 0.3.2 -> core 0.3.2 -> BC 1.86,
with matching strict lock constraints. Both APK verifiers checked reviewed
VERSION 0.2.5 / VERSION_CODE 10, API 30/37, complete manifests and component
exports/intent filters, variant flags, backup/branding and packaged exclusions.
The biometric permission, backup-disabled application, launcher/otpauth/text
ingress and debug-only probes remain under the unchanged artifact policy.

## Freeze and execution counts

- Production source stable: no production Java/resource/manifest edit; both
  strict APKs pass the full external artifact policy, and debug bytes are unchanged.
- JVM tests stable: only TotpCoordinatorTest setup/helpers changed. All 12 test
  bodies are byte-identical, final full inventories equal the original 535
  methods, and the frozen test hash remains unchanged through all final gates.
  Retirement source is restored byte-for-byte; no candidate class remains.
- Build/package configuration stable: Gradle, package, evaluator, dependency,
  lock, toolchain and cache inputs are unchanged and match recorded hashes.
  Four test forks and the default heaps remain in place.
- Qualification harness/tooling stable: no included tool or device harness
  change. Experimental scripts, extracted bytecode, candidate sources and logs
  remain ignored under .qualification/ and outside Git source membership.

| Category | Invocations |
| --- | --- |
| Baseline full Gradle | 0 (test-tooling scope) |
| Focused TOTP coordinator tests | 2 PASS, before and after |
| Focused retirement candidate tests | 1 PASS, all 26 cases |
| Focused complete-suite measurements | 4 PASS, A-B-B-A |
| Focused compile-only | 0 |
| Final normal full Gradle | 1 PASS |
| Strict offline clean Gradle | 1 PASS |
| Final external APK verifiers | 1 set / 2 commands, both PASS |
| Embedded Gradle full APK verifier | 0 |
| Offline runtime graph / boundary | 1 PASS |
| Physical/device harness | 0 (no changed product/device subsystem) |
| Agent Nix | 0 |
| Human Nix | 1 user-reported final routine PASS; 1 subsequent forced rebuild measurement |

The four complete-suite focused measurements are the deliberate worker-balance
experiment, not repeated qualification gates: two layouts were each measured
twice in reverse order, holding TOTP fixture inputs and process/heap settings
fixed. Every run freshly executed its full inventory. No clean, rerun-tasks or
APK verifier ran during iteration. The split was removed before final normal
and strict qualification. No failing build/test, repeated full gate or invalidated
final PASS occurred. No post-gate timing experiment is planned.

## Final Git state and human handoff

Only two paths differ from clean starting HEAD:

```text
 M app/src/test/java/org/totipo/android/reconcile/TotpCoordinatorTest.java
 A review/ANDROID_TOTP_FIXTURE_WORKER_BALANCE_REPORT.md
```

The new report is Git-known via intent-to-add only. `git diff --name-only` lists
the same two paths; `git diff --cached --name-only` is empty and
`git diff --cached --quiet` passes. Branch remains main, HEAD remains
a7cc3f1e80decea4553070636fb75a1671bbe7fd. No staged contents or commits;
`git diff --check` passes. All intended milestone files are Git-known and included
inputs are frozen. Later updates confined to this excluded report do not
invalidate Gradle, artifact or Nix qualification under the inspected filters.

The requested final human gate was exactly `nix flake check`; the user
subsequently reported PASS, completing qualification.

## Human result and whole-build timing

The user reported a forced rebuild of 181.90 seconds versus 181.87 seconds after
the preceding milestone: 0.03 seconds (about 0.02%) longer, effectively unchanged
at the observed variability. The focused TOTP class improvement did not produce
a measurable whole-Nix speedup in this individual comparison. It must not be
presented as an overall qualification-speed improvement. The additional forced
rebuild was the user's performance measurement, not another requested routine
qualification gate.

After the reported PASS, branch/HEAD/status and all frozen included-input hashes
were rechecked: unchanged. The original retirement source remains byte-identical.
Only this excluded report was updated to record the result, so no Gradle,
artifact or Nix PASS is invalidated. `git diff --check` and
`git diff --cached --quiet` pass; no staged contents or commits.
