# Two Gradle test workers

## Scope and starting state

- Classification: BUILD_DEPENDENCY and TEST_OR_QUALIFICATION_TOOLING.
- Starting branch: `main`.
- Starting HEAD: `24a2f652ab0b68bca1bc21ac6b10c66c2a1362d0`.
- Existing modification: `review/GOOGLE_PLAY_SDK_INDEX_2026_11_26_REFRESH_REPORT.md`.
  The user explicitly authorized proceeding while preserving that file unchanged.
- Change: set `maxParallelForks = 2` for Gradle `Test` tasks in
  `app/build.gradle.kts`. This allows two test JVMs, subject to Gradle's worker
  limit. It does not enable parallel methods within a class.
- No application, JVM test, dependency, package or CI workflow changes.

## CI and source membership

CI runs `nix flake check --print-build-logs` on `ubuntu-24.04`; its Android
derivation uses the same included `app/build.gradle.kts`. No workflow adjustment
is required. Two workers can consume more memory than one; local validation
cannot establish performance or memory use on the hosted runner. CI execution
remains pending.

Inspected `AGENTS.md`, README, development guidance, `package.nix`, `flake.nix`,
Gradle configuration, the Nix Gradle setup hook and CI. The package includes
`app/build.gradle.kts`, `app/src`, `gradle`, and `tools`; it excludes `review/`.
The separate release checks do not consume this report. Report-only updates do
not invalidate the final Gradle or Nix gates.

## Validation plan and evidence

No ordinary product baseline: this milestone changes only test task scheduling;
the ordinary baseline requirement applies to product/UI/lifecycle work.

Selected checks:

1. Focused full debug JVM suite with `--info`, to check parallel worker execution.
2. Final normal `gradle check :app:assembleDebug :app:assembleRelease`.
3. Final strict offline clean Gradle gate from `AGENTS.md`.
4. Debug and unsigned release APK verification, plus offline runtime graph and
   Maven boundary review.
5. `git diff --check`, preservation of the user's existing report, no staged
   content, and final human `nix flake check` after freezing included inputs.

No device qualification: no application behavior, resources or device harness
changes. Local command logs are in ignored `.qualification/two-test-workers/`.

## Results

- Focused command PASS in 55 s; tests were UP-TO-DATE. This is configuration
  compatibility evidence only, not evidence of fresh parallel test execution.
- Final normal full gate PASS in 55 s: 90 tasks, 24 executed, 66 up-to-date.
- Strict offline clean gate PASS in 3 min 4 s: 92 tasks executed. The prescribed
  command additionally used `--info` to capture both `Gradle Test Executor 1`
  and `Gradle Test Executor 2` starting and executing tests. Both had `-Xmx512m`.
- Fresh debug suite: 51 classes, 535 tests, zero failures/errors/skips.
  The largest controller class took 81.853 s. These timings are not a controlled
  before/after benchmark and do not establish a speedup ratio.
- Debug APK verification PASS; SHA-256:
  `1992bde0b6a9f0100f2247fd3d0fdf57f52c248412051f7630132fe3389117b8`.
- Release APK verification PASS; SHA-256:
  `9b7d2c439262680ccdd0dfd545a23d4e8af6b1a484087cf559a9722d57ff59b2`.
- Offline runtime graph and Maven-boundary check PASS in 1 s. Reviewed graph:
  NIO 0.3.2 -> core 0.3.2 -> BC 1.86, with matching strict lock constraints.
- Manifest boundaries remain unchanged: biometric permission, backup disabled,
  exported launcher and otpauth/text ingress components. Both final verifiers
  checked variant policy and reviewed identity/SDK metadata; source VERSION is
  0.2.5 and VERSION_CODE is 10.
- `git diff --check` PASS; the existing user report's SHA-256 remains unchanged.
- No failures or invalidated gates. No performance or hosted-CI result claimed.

## Input freeze and handoff

- Production source stable: no production source/resource/manifest changes.
- JVM tests stable: no test source changes; all 535 freshly executed tests pass.
- Build/package configuration stable: the only build change is the two-worker
  setting; final normal, strict and artifact checks passed after that edit.
- Qualification harness/tooling stable: no harness/verifier changes.

All intended new files are Git-known using intent-to-add, with no staged file
content. The final status before the human Nix gate is:

```text
 M app/build.gradle.kts
 M review/GOOGLE_PLAY_SDK_INDEX_2026_11_26_REFRESH_REPORT.md
 A review/TWO_TEST_WORKERS_REPORT.md
```

`git diff --name-only` lists those same three paths; `git diff --cached --name-only`
is empty and `git diff --cached --quiet` passes. No files were committed.
Human gate requested: exactly `nix flake check`. The user reported PASS before
the four-worker follow-up below. Hosted CI remains pending. Later report-only
updates do not invalidate included inputs.

## Execution counts

| Check | Invocations |
| --- | --- |
| Baseline full Gradle | 0 (build/test-tooling scope) |
| Focused Gradle tests | 1 (UP-TO-DATE) |
| Focused compile | 0 |
| Final normal full Gradle | 1 |
| Strict-clean Gradle | 1 |
| Final artifact verifiers | 1 set, 2 external commands |
| Embedded Gradle full APK verifier | 0 |
| Offline runtime graph / boundary Gradle | 1 |
| Physical harness | 0 (no affected device subsystem) |
| Human Nix | 1 user-reported PASS |

## Follow-up: four workers

The user reported the two-worker Nix PASS and requested an increase for a
4-core/8-thread CPU. Continuing the same authorized milestone on `main` at
`24a2f652ab0b68bca1bc21ac6b10c66c2a1362d0`, preserving the existing user report
and prior uncommitted work. Classification remains BUILD_DEPENDENCY and
TEST_OR_QUALIFICATION_TOOLING. The build now allows four test JVMs, subject to
Gradle's worker limit; hosted CI uses the same setting without a workflow change.
Four 512 MiB test heaps can total 2 GiB before JVM/native overhead, in addition to
the Gradle daemon and other tools. A speedup is not guaranteed.

This build-input change invalidates the previous final normal, strict, artifact
and human Nix evidence for the final configuration. Renew those gates once at
the new stable boundary. No product baseline or device check is required for
this test-task-only change. The strict gate will use `--info` to verify actual
worker startup. Local logs: `.qualification/four-test-workers/`.

Follow-up results:

- Final normal full gate PASS in 10 s: 90 tasks, 12 executed, 78 up-to-date.
- Strict offline clean gate with `--info` PASS in 2 min 25 s: 92 tasks executed.
  Logs confirm all four test executors started and executed tests.
- All 535 tests in 51 suites passed, zero failures/errors/skips. Largest class:
  `AndroidVaultControllerTest`, 83.995 s.
- The complete strict run took 145 s versus the earlier two-worker run's 184 s.
  These single observations suggest improvement but are not a controlled
  benchmark or a guarantee for another machine or CI.
- Both external APK verifiers PASS. Debug and release SHA-256 values are
  identical to the two-worker outputs recorded above; reviewed manifest,
  VERSION 0.2.5 and VERSION_CODE 10 boundaries remain unchanged.
- Offline runtime graph / Maven boundary PASS in 1 s; graph remains NIO 0.3.2
  -> core 0.3.2 -> BC 1.86 with matching strict constraints.
- User's pre-existing report checksum unchanged; no staged-content diff.
- No failures and no further included-input edits after these passes.

Renewed input freeze:

- Production source stable: untouched by this follow-up; artifact policies pass.
- JVM tests stable: unchanged sources, all 535 freshly executed tests pass.
- Build/package configuration stable: only `maxParallelForks` increased from
  two to four; renewed normal, strict and artifact gates pass.
- Qualification harness/tooling stable: unchanged.

Final status and diff path lists remain the same three paths recorded above.
The report remains intent-to-add, and `git diff --cached --name-only` is empty;
`git diff --cached --quiet` passes. `git diff --check` passes. Report-only edits
do not invalidate qualification. No content staged or committed.
Request exactly `nix flake check` for the frozen four-worker configuration;
its human result and hosted CI result remain pending.

Follow-up execution counts: baseline 0; focused test/compile 0 (fresh execution
and worker evidence captured in strict gate); final normal 1; strict-clean 1;
external artifact verifier 1 set / 2 commands; embedded Gradle APK verifiers 0;
offline runtime graph/boundary 1; device 0; human Nix 0 results / 1 requested.
The renewed gates are necessary because the user-requested worker change
invalidated the previously qualified two-worker build input. Across both phases,
there were two normal gates, two strict gates, two final artifact sets, one
focused test command, two runtime/boundary commands, and one human Nix PASS
reported so far; no agent Nix invocation.

## Follow-up: backup admission test failure under four workers

Classification: TEST_OR_QUALIFICATION_TOOLING (within the existing build milestone).
Starting branch/HEAD remain `main` /
`24a2f652ab0b68bca1bc21ac6b10c66c2a1362d0`, with the authorized existing build/report
edits preserved. The user reported a subsequent test failure:

```text
> Task :app:testDebugUnitTest
BackupControllerTest > captureAdmissionExcludesAuthoringAndLockWaitsForAdmittedCapture FAILED
    java.lang.AssertionError at BackupControllerTest.java:119
```

No full user log or timing was supplied. The original line contains both
`assertTrue(c.beginBackup())` and `assertTrue(entered.await(...))`, so it cannot
identify which assertion failed. One focused, unmodified-method reproduction
passed in 2 s. Do not claim the original failure was reproduced locally.

Inspection found an admission race consistent with this failure: `open()` waits
for `canBackUpVault()`, which does not reserve the worker. An initial asynchronous
state callback may schedule presentation in the bounded worker before the next
`beginBackup()` call; the latter may legitimately reject busy admission. Another
backup test already isolates initial observation delivery for this reason.

The affected test now uses the existing fixture callback seam before opening to
omit unrelated changed-state presentation callbacks, retaining terminal callbacks.
Real Java open/session/capture/close and the bounded-scan latch remain exercised.
The latch asserts capture has entered before authoring exclusion and lock ordering
are checked. Admission and scan-entry assertions now have distinct messages, and
the admission/entry checks are inside the release-latch `try/finally` so failures
cannot leave the admitted scan waiting for its timeout. Timeouts were not increased.
No production code or worker limit changed; four test workers remain configured.

Reviewed the exact Java guidance at
`f0a028676c1801a10b2d8d2650bf9c24357d1fa1`, the released 0.3.2 API design at
`af11553659dd8bd4157064c7e3de27bed8980ac5`, and that release's BACKUP_ARCHIVE
contract. Asynchronous state delivery is not admission acknowledgement; capture
owns provider work and session close waits for admitted work. No Java or Android
operation policy is changed by this test-fixture control.

The user failure invalidates confidence in the prior four-worker qualification;
the test-source fix also invalidates prior final Gradle/artifact/Nix passes for
the new inputs. Diagnose with the affected backup class before renewing gates.
No device qualification: only JVM fixture scheduling changes.
Logs: `.qualification/backup-admission-race/`.

Results:

- Original-method focused reproduction: PASS in 2 s; failure not reproduced.
- Fixed full `BackupControllerTest` class: PASS in 7 s.
- Renewed final normal full gate: PASS in 2 min; 90 tasks, 7 executed, 3 from
  cache, 80 up-to-date. The full JVM suite executed after the focused filter.
- Renewed strict offline clean gate with `--info`: PASS in 2 min 42 s; all 92
  tasks executed. All four test executors started. All 535 tests in 51 suites
  passed with zero failures/errors/skips; the backup class's 10 tests passed.
- Final external debug/release APK verifiers PASS. SHA-256 values remain exactly
  the debug/release values recorded above. Source metadata and manifest boundaries
  are unchanged and checked against the final strict artifacts.
- Offline runtime graph / Maven boundary PASS in 1 s; reviewed locked graph
  remains NIO 0.3.2 -> core 0.3.2 -> BC 1.86.
- Existing user report checksum unchanged; `git diff --check` passes and the
  staged-content diff remains empty.

Renewed freeze:

- Production source stable: no production/resource/manifest edit; APK policies pass.
- JVM tests stable: only the affected test's callback control, diagnostics and
  release-latch protection changed; focused, normal and strict runs pass.
- Build/package configuration stable: four-worker setting unchanged throughout
  diagnosis and final gates; no dependency/package input edit.
- Qualification harness/tooling stable: existing fixtures and verifier unchanged.

Final changed paths:

```text
 M app/build.gradle.kts
 M app/src/test/java/org/totipo/android/BackupControllerTest.java
 M review/GOOGLE_PLAY_SDK_INDEX_2026_11_26_REFRESH_REPORT.md
 A review/TWO_TEST_WORKERS_REPORT.md
```

`git diff --name-only` contains those four paths. `git diff --cached --name-only`
is empty and `git diff --cached --quiet` passes. No staged content or commits;
the report retains intent-to-add. Report-only updates are excluded from package
and release-check consumers and do not invalidate this freeze.
Request exactly `nix flake check` for the fixed test and four-worker setting;
the user subsequently reported **PASS**. Hosted CI remains pending.
The fixed test and four-worker configuration are now qualified through the final
human gate. Only this excluded report was updated after that PASS; included
inputs remain frozen and no Gradle, artifact or Nix gate is invalidated.

Failure-fix execution counts: baseline full 0; focused Gradle test commands 2
(original method + fixed class); focused compile 0; final normal full 1;
strict-clean 1; external artifact verifier 1 set / 2 commands; embedded Gradle APK
verifiers 0; runtime/boundary Gradle 1; device 0; renewed human Nix 1 user-reported
PASS. These extra final gates are necessary because of the reported
failure and subsequent JVM-test edit. Across all three phases: baseline 0;
focused test commands 3; final normal 3; strict-clean 3; external artifact sets 3
(6 commands); runtime/boundary commands 3; device runs 0; human Nix reports
2 PASS results (two-worker phase and fixed four-worker phase) and 1 intervening
test failure (four-worker phase, full invocation/output not supplied); no agent
Nix invocation. Final changed paths and empty staged-content diff remain as above.
