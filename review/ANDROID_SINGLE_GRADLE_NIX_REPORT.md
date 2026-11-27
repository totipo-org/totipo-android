# Single Gradle invocation in the Android Nix package

Classification: BUILD_DEPENDENCY and PACKAGING_RELEASE (package phase orchestration
only). Started on clean `main` at
`9ca91a2338ccf076b76dc297e2d564ec72c30805`; starting status was empty.

## Change and inspected dependencies

Inspected AGENTS.md, README, docs/DEVELOPMENT.md, package.nix, flake.nix,
Gradle configuration, CI and existing performance/qualification reports.
The pinned Gradle 9.8.2 setup hook launches separate no-daemon commands for
gradleBuildTask and gradleCheckTask. The package now includes `check` in
gradleBuildTask alongside both assemblies, and overrides checkPhase to retain
preCheck/postCheck hooks without another Gradle invocation. No package-specific
preCheck/postCheck hooks currently exist. Future hooks needing to precede Gradle
checks must account for checks executing during buildPhase.

Qualification remains mandatory during the build even if a caller disables the
separate check phase. doCheck and doInstallCheck remain enabled. Both external
APK verifiers, including verification of the installed release bytes, remain
unchanged. The cache-update task list already contains all three tasks and is
unchanged. No dependency cache refresh, source filter change, Gradle input edit,
or qualification-policy change occurred. Development documentation describes
the combined invocation. No performance improvement is claimed before measurement.

## Validation and limitations

A temporary shell harness used the installed pinned Gradle setup hook and the
task list/checkPhase extracted from the edited package. Gradle was replaced with
a recording stub. Shell syntax passed; build/check phases launched exactly one
command with `--parallel :app:assembleDebug :app:assembleRelease check`, and all
four phase hooks ran. A stub exit status of 23 propagated and stopped execution
before postBuild/check hooks. Static checks confirmed retained doCheck,
doInstallCheck, both APK verifier calls, and matching cache-update task coverage.
Result: `.qualification/single-gradle/phase-validation.txt` (ignored local evidence).

The first harness attempt failed because its temporary stub used `/bin/bash`,
which does not exist on this host. Correcting the stub to use the discovered
Bash path made the harness pass; no package edit was needed for this harness
failure. No actual Gradle or Nix command ran during either attempt. This focused
check validates shell orchestration, not Nix evaluation or an Android build.

Per the package-only row of the invalidation matrix, no local Gradle rebuild or
artifact revalidation is required: package.nix is an evaluator input, not a
Gradle source input. The changed orchestration requires final human Nix
qualification, which builds/tests and verifies both APKs through the package.
No affected device subsystem exists.

## Input freeze and handoff

- Production source stable: no app source, resources or manifest edits.
- JVM tests stable: no JVM test edits.
- Build/package configuration stable: only the reviewed package.nix phase change;
  focused phase validation passed. Gradle, locks, dependency cache and flake unchanged.
- Qualification harness/tooling stable: no included tools changed; temporary
  validation remained outside Git source membership.

The package filter selects app/src, gradle, tools and its explicit build/metadata
files. package.nix affects evaluation and invalidates prior Nix qualification.
docs/ and review/ are excluded from the Android package and separate release
checks; report-only updates after the human result will not invalidate it.

Changed files: package.nix, docs/DEVELOPMENT.md and this report. The new report is
Git-known via intent-to-add only. Final `git diff --check` and
`git diff --cached --quiet` pass; cached name list is empty. Branch/HEAD unchanged;
no staged contents or commits. Final status and unstaged name list contain only
these three paths.

Execution counts: baseline full Gradle 0; focused Gradle 0; final normal Gradle 0;
strict-clean 0; external/embedded artifact verifiers 0; physical harnesses 0;
agent Nix 0; human Nix 1 user-reported PASS, plus 1 user-reported forced rebuild
for measurement. Focused shell harness: two attempts, one
host-path failure and one PASS. No repeated full gates or post-freeze input edits.

The user reported final `nix flake check` PASS, completing the required human
qualification gate. A subsequent forced rebuild took 190.89 seconds versus the
preceding 200.49-second pre-change measurement: 9.60 seconds (4.79%) shorter.
This single before/after comparison is observational and does not establish a
repeatable speedup or isolate configuration overhead from host variability.
The forced rebuild was a user-operated timing measurement, not an additional
requested routine qualification gate.

After the reported PASS, rechecked branch, HEAD, status and the complete package
and development-documentation diffs against the frozen implementation: unchanged.
Only this excluded report was updated to record the result; no qualified input
or prior PASS was invalidated. `git diff --check` and
`git diff --cached --quiet` pass; no staged contents or commits.
