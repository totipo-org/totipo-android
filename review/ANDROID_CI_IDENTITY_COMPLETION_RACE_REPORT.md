# CI identity-status test completion race

## Scope and starting state

Classification: **TEST_OR_QUALIFICATION_TOOLING**, recorded before editing.
Started on clean `main` at `7d666bcc9de8064a4124d89949d4526e6dfd2e1c`;
`git status --short` was empty. Production, dependencies, build configuration,
and device behavior are unchanged.

Reviewed AGENTS.md, README, managed development instructions, package.nix,
flake.nix, CI, APK verifier/build wiring, the Java 0.2.0 reconciliation report,
and the Java guidance refresh report. Read API_DESIGN.md at the required exact
guidance commit `f0a028676c1801a10b2d8d2650bf9c24357d1fa1` read-only from
GitHub. All builds use the already active pinned Gradle 9.8.0 / JDK 17 / Android
SDK environment. No Nix command was executed by the agent.

## Failure and correction

The supplied CI log reports one comparison failure at the identity-status
assertion in `identityFailureStatusesKeepReadyAndBlockBothControllerDirections`
among 461 tests. It does not include the scenario or expected/actual strings.
An isolated local run of that unchanged test passed, consistent with a race.

`providerReturned` clears `providerActive` on the vault worker before rechecking
binding grants outside the controller monitor. During this interval neither
`operating` nor `providerActive` need be set. The test's `providerDone()` could
therefore return before the identity status was produced, allowing an assertion
against an intermediate message or a premature change to the next fixture.
This is a test completion predicate issue; no Java observation completion or
remote synchronization guarantee is inferred from the flags.

The helper now also requires no active vault-worker task and an empty worker
queue. Existing controller synchronization and operation flags remain in the
predicate. This covers the in-flight completion and its queued follow-up work
before assertions or fixture changes.

Added a no-op grants hook to the existing in-memory test port. The regression
uses latches and an atomic scan marker to hold the worker exactly inside the
post-transport grants recheck. It checks that the intermediate `Importing…`
message remains visible, completion is false while blocked, and the final
missing-vault message appears after release. With the old predicate this test
failed deterministically at its completion assertion; its failure XML was saved
before applying the correction. No timing sleeps or weakened identity assertions
were added.

Changed files:

- `app/src/test/java/org/totipo/android/AndroidVaultControllerTest.java`
- `app/src/test/java/org/totipo/android/sync/SyncFolderBindingTest.java`
- This report.

## Qualification evidence

Local logs and pre-fix failure XML are retained under the ignored directory
`.gradle/ci-identity-fix/`, outside Git/Nix source membership.

1. Original isolated identity test: PASS (`totipo-ci-identity-before.log`).
2. New latch-controlled regression with the old helper: expected FAIL
   (`totipo-ci-identity-race.log`, `race-before.xml`), reporting
   `Transport returned, but identity classification is still running`.
3. Controller and binding focused suites: PASS, 102 tests, zero failures/errors/
   skips (91 controller, 11 binding; `focused-after.log`, 1m 9s).
4. Final normal `gradle check :app:assembleDebug :app:assembleRelease`: PASS,
   462 tests, zero failures/errors/skips, 3m 56s (`final-normal.log`).
5. Strict `gradle --offline --no-daemon --no-configuration-cache --no-build-cache
   --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug
   :app:assembleRelease`: PASS, 462 tests, zero failures/errors/skips, 3m 34s,
   all 91 tasks executed (`final-strict.log`).
6. Final external artifact commands: both PASS (`verify-debug.log`,
   `verify-release.log`): `python3 tools/verify-apk.py
   app/build/outputs/apk/debug/app-debug.apk --debug-probe` and `python3
   tools/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk
   --unsigned --no-debug-probe`.
7. `gradle --offline :app:dependencies --configuration releaseRuntimeClasspath
   :app:verifyMavenBoundary`: PASS (`runtime-boundary.log`). Reviewed the graph:
   storage-nio 0.2.0 → core 0.2.0 → bcprov-jdk18on 1.86, with strict locks and
   no additional runtime edges.
8. `git diff --check`, frozen-input hash recheck, and
   `git diff --cached --quiet`: PASS.

Reviewed manifest/build boundaries: application ID `org.totipo.android`, min/target
SDK 30/37, only USE_BIOMETRIC permission, backup disabled, launcher and otpauth/text
enrollment exports, and debug-only diagnostic components. Both artifact verifiers
enforce their complete variant policies and source metadata VERSION **0.2.1** /
VERSION_CODE **6**. Strict-clean APK SHA-256:

| Artifact | SHA-256 |
| --- | --- |
| Debug | `5668d63b261e9ad564303f3ed4d7472a18bb65e091a8bfcfd17c8986f6e6b38c` |
| Unsigned release | `9f1ab48d8ca234f7b84b3f5ec211aac1b27778deb7ab0c45af86d48a045cab62` |

Existing AGP experimental-option and Gradle deprecation warnings remain visible.

No ordinary product baseline was needed for this test-only milestone; the
unchanged failing test was run directly. Final normal/strict/artifact gates
remain required because JVM tests are included package inputs. Physical/device
qualification is unnecessary because neither production behavior nor artifacts'
source inputs changed.

## Input membership, freeze, and human gate

Reinspected package.nix's explicit source filter: `app/src` (including tests),
`gradle`, `tools` and its exact build/lock/version/license/branding file list
are included; `.git`, `.gradle`, `build`, `.direnv` components and symlinks are
excluded. flake.nix checks consume package.nix and separate release-tooling
filesets; neither consumes `review/`. This report is excluded from effective
check inputs; subsequent report-only result updates invalidate no gate.

All intended new files are Git-known; the new report has intent-to-add membership
only. Recorded 190 effective Android source/evaluator/cache input hashes in
`.gradle/ci-identity-fix/frozen-inputs.sha256`; manifest SHA-256:
`68e78b48f61dfa898fa54f87cd6e25981ddc4fe21e9290616a1cd8bfbf0cea26`.
Rechecked every entry after strict/artifact/boundary qualification: PASS.

- Production source stable: identical to starting HEAD; frozen hashes match.
- JVM tests stable: the two changed test files passed focused, final normal, and
  strict qualification; frozen hashes match.
- Build/package configuration stable: identical to starting HEAD; frozen hashes match.
- Qualification harness/tooling stable: identical to starting HEAD; frozen hashes match.

No included input changed after the final checkpoints. Only this excluded report
was updated to record results. Human `nix flake check`: **PASS**, reported by
the user after the freeze: “nix flake check passed”. One human invocation;
full terminal output and duration were not supplied. Rechecked all 190 frozen
inputs after the reported PASS: all match. This excluded report-only update
invalidates no gate; the human Nix PASS remains valid.
No staged file content, commit, push, or remote CI dispatch is authorized or done.

## Execution counts

| Category | Count |
| --- | --- |
| Baseline full Gradle | 0 (test-only) |
| Focused Gradle test / compile | 3 / 0 |
| Final normal full Gradle | 1 |
| Strict offline clean Gradle | 1 |
| External APK verifiers / embedded Gradle APK verifiers | 2 / 0 |
| Focused runtime graph + boundary Gradle | 1 combined invocation |
| Physical/device harness phases | 0 (test-only) |
| Human / agent Nix | 1 / 0 |

No repeated full gates or invalidated passes. The deliberate failing
regression is focused diagnosis, not a failed full gate.

## Final Git state after human Nix

Branch and HEAD remain `main` /
`7d666bcc9de8064a4124d89949d4526e6dfd2e1c`.
`git status --short`:

```text
 M app/src/test/java/org/totipo/android/AndroidVaultControllerTest.java
 M app/src/test/java/org/totipo/android/sync/SyncFolderBindingTest.java
 A review/ANDROID_CI_IDENTITY_COMPLETION_RACE_REPORT.md
```

`git diff --name-only` lists exactly those three paths.
`git diff --cached --name-only` is empty and `git diff --cached --quiet` passes:
all content remains unstaged. Build/evidence state remains ignored. No included
source edit followed the human Nix PASS. Only this excluded report was updated
to record the result and final Git state.
