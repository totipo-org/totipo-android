# Git-aware local Nix worktree qualification

Status: COMPLETE. Directly affected validation passed; final human `nix flake check`
PASS reported. All changes remain uncommitted.

## Repository and starting state

Only `/home/niki/Sources/totipo-android` was inspected. Origin is
`git@github.com:totipo-org/totipo-android.git`; Android app source, Gradle build,
Android Nix package and protected Android release apps confirm repository identity.
No sibling repository was inspected or modified.

Starting branch: `main`. Starting HEAD: `7b900e68e7d8e0bf73fe282ea748068a5a048eed`.
Before inspection, `git status --short` was empty and `git diff --cached --quiet`
passed. No pre-existing dirty, staged or untracked files were present.

Classification established before editing: **BUILD_TOOLING / QUALIFICATION_POLICY**;
repository ladder mapping **TEST_OR_QUALIFICATION_TOOLING**, with package evaluator
and workflow inputs affected. This is not product work or dependency migration.

Read AGENTS.md, README.md, flake.nix, package.nix, .envrc, both CI/release workflows,
release/README.md, release/signing/README.md, release/source tooling and tests,
the supply-chain guard, qualification ladder and committed Wave 2B report.
No Makefile or additional nested AGENTS.md exists in the tracked layout.

## Complete source-reference inventory

Repository-wide searches covered `path:`, `path:.`, `nix flake check`, `nix build`,
`nix run`, `nix develop`, `nix eval` and `nix flake metadata`, including hidden
workflow files and all tracked reports. No metadata invocation was found.
The following inventory records every original literal `path:` occurrence,
grouped by file with original line numbers. C includes historical examples and
recorded invocations: changing these would rewrite prior qualification evidence.
Non-source lexical matches are explicitly separated rather than treated as Nix URLs.

| Original file | Original lines | Classification and disposition |
| --- | --- | --- |
| `.github/workflows/ci.yml` | 28 | A — current Git worktree (changed) |
| `.github/workflows/release.yml` | 53, 57, 59, 94, 97, 134, 166, 203 | A — current Git worktree (changed) |
| `.github/workflows/release.yml` | 63, 92, 138, 164, 170, 198 | Not a Nix source — language syntax, YAML key, diagnostic or device output (retained) |
| `AGENTS.md` | 140, 152 | A — current Git worktree (changed) |
| `README.md` | 121, 126, 130, 222, 234, 243, 247, 264, 274 | A — current Git worktree (changed) |
| `app/src/test/java/org/totipo/android/AndroidVaultControllerTest.java` | 1195 | Not a Nix source — language syntax, YAML key, diagnostic or device output (retained) |
| `package.nix` | 71 | A — current Git worktree (changed) |
| `package.nix` | 31 | Not a Nix source — language syntax, YAML key, diagnostic or device output (retained) |
| `release/README.md` | 19, 26, 117 | A — current Git worktree (changed) |
| `review/ANDROID_ACTIONS_RELEASE_UX_REPORT.md` | 258, 320, 332, 339 | C — archived command/evidence or historical source explanation |
| `review/ANDROID_BIOMETRIC_INACTIVITY_LOCK_REPORT.md` | 285 | C — archived command/evidence or historical source explanation |
| `review/ANDROID_DAILY_DRIVER_UX_REPORT.md` | 324 | C — archived command/evidence or historical source explanation |
| `review/ANDROID_JAVA_GUIDANCE_PIN_REFRESH_REPORT.md` | 37 | C — archived command/evidence or historical source explanation |
| `review/ANDROID_JAVA_OPERATION_MODEL_AUDIT.md` | 892 | C — archived command/evidence or historical source explanation |
| `review/ANDROID_NIX_CI_UNIFICATION_REPORT.md` | 123, 124, 125, 135, 305, 339, 343, 356 | C — archived command/evidence or historical source explanation |
| `review/ANDROID_PRODUCTION_SIGNING_IDENTITY_REPORT.md` | 213 | C — archived command/evidence or historical source explanation |
| `review/ANDROID_QUALIFICATION_LADDER_REPORT.md` | 41, 43, 62, 94 | C — archived command/evidence or historical source explanation |
| `review/ANDROID_RELEASE_PREPARATION_AUTOMATION_REPORT.md` | 270, 362, 368, 377, 379, 380, 381 | C — archived command/evidence or historical source explanation |
| `review/ANDROID_RELEASE_PUBLISH_RECOVERY_FIX_REPORT.md` | 4, 326 | C — archived command/evidence or historical source explanation |
| `review/ANDROID_RELEASE_VERSION_PROVENANCE_FIX_REPORT.md` | 4, 297, 308 | C — archived command/evidence or historical source explanation |
| `review/ANDROID_REVEAL_REPAINT_FIX_REPORT.md` | 310 | C — archived command/evidence or historical source explanation |
| `review/ANDROID_ROW_REVEAL_COPY_UX_REPORT.md` | 214 | C — archived command/evidence or historical source explanation |
| `review/ANDROID_ROW_STABILITY_COUNTDOWN_RING_REPORT.md` | 288 | C — archived command/evidence or historical source explanation |
| `review/ANDROID_SAF_BOOTSTRAP_CURRENT_SYNC_RECONCILIATION_REPORT.md` | 398 | C — archived command/evidence or historical source explanation |
| `review/ANDROID_SAF_BOOTSTRAP_CURRENT_SYNC_RECONCILIATION_REPORT.md` | 166 | Not a Nix source — language syntax, YAML key, diagnostic or device output (retained) |
| `review/ANDROID_SAF_OUTBOUND_AUTOSYNC_RECONCILIATION_REPORT.md` | 186 | C — archived command/evidence or historical source explanation |
| `review/ANDROID_SIGNED_RELEASE_MACHINERY_REPORT.md` | 33, 318, 327 | C — archived command/evidence or historical source explanation |
| `review/ANDROID_STALE_SUBSCRIBER_OWNERSHIP_FIX_REPORT.md` | 309 | C — archived command/evidence or historical source explanation |
| `review/ANDROID_SUPPORT_TOOLING_AUDIT.md` | 382 | C — archived command/evidence or historical source explanation |
| `review/ANDROID_SUPPORT_TOOLING_AUDIT.md` | 495 | Not a Nix source — language syntax, YAML key, diagnostic or device output (retained) |
| `review/ANDROID_SUPPORT_TOOLING_WAVE1_REPORT.md` | 265 | C — archived command/evidence or historical source explanation |
| `review/ANDROID_SUPPORT_TOOLING_WAVE2A_COMPLETION_REPORT.md` | 146 | C — archived command/evidence or historical source explanation |
| `review/ANDROID_SUPPORT_TOOLING_WAVE2A_FINAL_COMPLETION_REPORT.md` | 428 | C — archived command/evidence or historical source explanation |
| `review/ANDROID_SUPPORT_TOOLING_WAVE2A_FINAL_REPORT.md` | 227 | C — archived command/evidence or historical source explanation |
| `review/ANDROID_SUPPORT_TOOLING_WAVE2A_REPORT.md` | 322 | C — archived command/evidence or historical source explanation |
| `review/ANDROID_SUPPORT_TOOLING_WAVE2B_REPORT.md` | 4, 52, 54, 350, 498 | C — archived command/evidence or historical source explanation |
| `review/ANDROID_SYNC_STATUS_LAYOUT_REPORT.md` | 3, 117 | C — archived command/evidence or historical source explanation |
| `review/ANDROID_TOKEN_LIFECYCLE_REPORT.md` | 291, 292 | C — archived command/evidence or historical source explanation |
| `review/ANDROID_TOKEN_LIFECYCLE_REPORT.md` | 219 | Not a Nix source — language syntax, YAML key, diagnostic or device output (retained) |
| `review/JAVA_0_2_0_R19_ANDROID_RECONCILIATION_REPORT.md` | 88, 327 | C — archived command/evidence or historical source explanation |
| `review/M0_ANDROID_BOOTSTRAP_REPORT.md` | 268, 269, 270, 272, 273, 274 | C — archived command/evidence or historical source explanation |
| `review/M1C_LOCAL_REPLICA_RECONCILIATION_REPORT.md` | 302, 316, 317, 320 | C — archived command/evidence or historical source explanation |
| `review/M1D_PRODUCTION_LOCAL_REPLICA_DESIGN_REPORT.md` | 559, 560 | C — archived command/evidence or historical source explanation |
| `review/M1E_READ_ONLY_PROVIDER_SNAPSHOT_REPORT.md` | 206 | C — archived command/evidence or historical source explanation |
| `review/M1F_READ_ONLY_CANDIDATE_VALIDATION_REPORT.md` | 84, 91, 265, 266 | C — archived command/evidence or historical source explanation |
| `review/M1G_VALIDATED_IMMUTABLE_IMPORT_REPORT.md` | 271, 272 | C — archived command/evidence or historical source explanation |
| `review/M1H_FOREGROUND_RECONCILIATION_ORCHESTRATION_REPORT.md` | 344, 345 | C — archived command/evidence or historical source explanation |
| `review/M1I_COORDINATED_STORE_LIVE_SYNC_REPORT.md` | 455, 456 | C — archived command/evidence or historical source explanation |
| `review/M1J_ANDROID_VAULT_SHELL_REPORT.md` | 249, 250 | C — archived command/evidence or historical source explanation |
| `review/M1K_ANDROID_AUTH_PERFORMANCE_REPORT.md` | 279, 280 | C — archived command/evidence or historical source explanation |
| `review/M1K_ANDROID_AUTH_PERFORMANCE_REPORT.md` | 308 | Not a Nix source — language syntax, YAML key, diagnostic or device output (retained) |
| `review/M1L_ART_ARGON2_EXECUTION_REPORT.md` | 277, 278 | C — archived command/evidence or historical source explanation |
| `review/M1M_RELEASE_AUTH_PERFORMANCE_QUALIFICATION_REPORT.md` | 363, 364 | C — archived command/evidence or historical source explanation |
| `review/M1M_RELEASE_AUTH_PERFORMANCE_QUALIFICATION_REPORT.md` | 135, 242 | Not a Nix source — language syntax, YAML key, diagnostic or device output (retained) |
| `review/M2A_TOTP_INTERACTION_REPORT.md` | 316, 317 | C — archived command/evidence or historical source explanation |
| `review/M2B_MANUAL_TOKEN_ENROLLMENT_REPORT.md` | 128 | C — archived command/evidence or historical source explanation |
| `review/M2B_MANUAL_TOKEN_ENROLLMENT_REPORT.md` | 14 | Not a Nix source — language syntax, YAML key, diagnostic or device output (retained) |
| `review/M2C_NATIVE_OTPAUTH_CAMERA_QUALIFICATION_REPORT.md` | 142, 319 | C — archived command/evidence or historical source explanation |
| `review/M2D_OTPAUTH_ENROLLMENT_REPORT.md` | 194, 195 | C — archived command/evidence or historical source explanation |
| `review/M3A_SAF_BINDING_INBOUND_SYNC_REPORT.md` | 140, 141, 249 | C — archived command/evidence or historical source explanation |
| `review/M3B_OUTBOUND_IMMUTABLE_PUBLICATION_REPORT.md` | 323, 324 | C — archived command/evidence or historical source explanation |
| `review/M3C_IMMUTABLE_VAULT_BOOTSTRAP_REPORT.md` | 354 | C — archived command/evidence or historical source explanation |
| `review/M3D_SYNCTHING_END_TO_END_REPORT.md` | 377, 378 | C — archived command/evidence or historical source explanation |
| `review/M3D_SYNCTHING_END_TO_END_RESUME_REPORT.md` | 163, 164 | C — archived command/evidence or historical source explanation |
| `review/POST_M0_CI_SIMPLIFICATION_REPORT.md` | 30, 32, 76, 81 | C — archived command/evidence or historical source explanation |
| `review/TOTIPO_JAVA_0_1_4_ANDROID_REPIN_REPORT.md` | 292, 300, 301 | C — archived command/evidence or historical source explanation |
| `review/TOTIPO_JAVA_0_1_4_ANDROID_REPIN_REPORT.md` | 99, 101, 104, 107 | Not a Nix source — language syntax, YAML key, diagnostic or device output (retained) |
| `tools/device/M3D_SYNCTHING_CHECKPOINTS.md` | 164 | A — current Git worktree (changed) |
| `tools/device/SafBootstrapRegression.java` | 185, 246 | Not a Nix source — language syntax, YAML key, diagnostic or device output (retained) |
| `tools/device/TokenLifecycleRegression.java` | 68, 135, 153, 170 | Not a Nix source — language syntax, YAML key, diagnostic or device output (retained) |
| `tools/release/release.py` | 106, 107, 109 | A — current Git worktree (changed) |
| `tools/release/source.py` | 198, 249 | Not a Nix source — language syntax, YAML key, diagnostic or device output (retained) |

There are **no B intentional raw/non-Git Nix source URLs** and **no D unknown
Nix source URLs** in this checkout. Retained archived `path:.` references are C,
not active policy. Archived reports are unchanged, including Wave 2B, qualification
ladder, prior Nix CI unification, and biometric evidence. Newly added bare `path:`
mentions in AGENTS/README describe the intentional raw-source exception; this
report quotes prior forms for audit only. YAML `path:` values, Java loops, the Nix
filter lambda and diagnostic text are not flake source references.

## Changes and preserved behavior

- AGENTS.md: canonical Git-aware local qualification, authorized intent-to-add,
  no-staged-content invariant, membership preparation and final `nix flake check`.
- README.md: default/ci development shells, check, build, deliberate reproducibility
  example and dependency-cache app references now use Git-aware local forms.
  Removed the obsolete instruction to include untracked files through raw paths.
- package.nix: only the stale-cache diagnostic changes to
  `nix run .#update-package-deps`; source filter, package contents and build tasks
  are unchanged.
- release/README.md: exactly two normal release commands remain:
  `nix run .#prepare-release -- <VERSION>` and
  `nix run .#release-tag -- <VERSION>`; qualification table aligned.
- tools/release/release.py: both drvPath evaluations and the existing single
  materialization build use `.#…`. Repository.run still executes in the explicit
  caller repository root. Equal-derivation refusal and artifact/provenance logic
  remain unchanged. Flake app wrappers already discover the caller worktree.
- tools/release/test_release.py: one regression verifies the two Git-aware evals
  and single build in order through the existing artifact boundary. Existing
  derivation-mismatch refusal test remains intact.
- Both workflows: current-checkout check/develop references converted only.
  Verified preceding steps: action-policy/identity read source; Python tests use
  disposable fixture directories and `-B`; downloaded bundles and signing material
  are in RUNNER_TEMP; GITHUB_ENV is runner state. No generated untracked repository
  source is required. Action pins/count, dependencies, permissions, secrets,
  command count, signing shell, jobs, network behavior and CI unchanged-input guard
  are preserved.
- tools/device/M3D_SYNCTHING_CHECKPOINTS.md: only normal human Nix command wording
  changes. No device code, signer cleanup or physical qualification changes.
- .envrc already contains `use flake`, so stays unchanged. flake.nix is unchanged.

Committed Wave 2B semantics remain unchanged: source ownership, reviewed digests,
release metadata/history, publication/refusal behavior, provenance, payload proof,
signer identity and release trust phases. No app source/resources/JVM tests,
Gradle inputs, dependency versions/locks, VERSION/VERSION_CODE, branding, public
certificate or fingerprint changed. No private/qualification signing material was read, moved or generated;
no `.qualification` directory, build, installation, signing or physical run occurred.
No custom source-copy/filtering framework was introduced.

## Intent-to-add and index invariant

Local Git-aware flakes qualify current unstaged tracked modifications. New intended
milestone files must become Git-known using `git add --intent-to-add <file>`.
This establishes membership only; actual contents stay unstaged. Explicit user
authorization remains required for staging actual contents or committing. Entries
remain for human review/commit and are not reset after qualification.

The sole new intended eventual-commit path is:
`review/NIX_GIT_WORKTREE_QUALIFICATION_POLICY_REPORT.md`.
It must be intent-added before the final human gate. All other milestone paths
were already tracked. No ignored output/cache/secret was added. `git ls-files`
confirmed no .gradle, .direnv, .qualification, local.properties or private signing
extensions in release/signing are Git source members. No private files were opened.

## Non-Nix validation and execution counts

- Standard-library release suite: **58 tests PASS**, 12.074 seconds; includes source,
  release apps, publication, reviewed action-pin/security policy and command-form
  regression. Expected negative fixture diagnostics are test evidence, not failures.
  All tag pushes in test output target disposable /tmp fixture remotes.
- `python3 -B tools/release/release.py action-policy`: **PASS**.
- Python AST validation of changed Python files: **PASS**.
- `bash -n` on .envrc and all **18 workflow run blocks**: **PASS**; GitHub expressions
  substituted with inert fixture text solely for syntax validation.
- Command-only equivalence for workflow/package/release implementation/device prose:
  **PASS**; exact prior content with only source-reference substitutions.
- Changed-path scope and Git source-membership exclusions: **PASS**.
- `git diff --check`: **PASS**.

Baseline/focused/final normal/strict Gradle invocations: **0 each**. Wrapper/APK
verifiers and signing smoke: **0**. Physical harness runs/installs: **0**. Release
Python suite: **1** (58 tests). Agent Nix invocations: **0**. Human Nix: **1 final routine check, PASS (human-reported)**,
requested only after freeze. No repeated full gate or invalidated pass.

The repository invalidation matrix uses actual consumers: command-only package
warning and standalone release tests/harness prose do not change Gradle inputs,
JVM tests or APK behavior. Directly affected Python/static checks are sufficient
locally; the required package-inclusive final human Nix gate remains mandatory.
No unrelated Gradle/device suite or signing smoke is needed for reference wording.

## Input freeze and post-Nix policy

1. **Production source stable:** app/src/main and app/src/debug unchanged from HEAD.
2. **JVM tests stable:** all app/src/test unchanged from HEAD.
3. **Build/package configuration stable:** only package.nix diagnostic wording
   changes; source filter/tasks/toolchains/pins and flake.nix/lock/cache unchanged.
4. **Qualification harness/tooling stable:** release implementation/regression and
   device qualification prose finalized; 58 tests and affected static checks pass.
5. **Workflow/security policy stable:** command forms finalized; action-policy and
   existing workflow security tests pass.

package.nix still selects app/src, gradle, tools and the exact build/lock/version/
license/branding files enumerated in AGENTS.md, excluding symlinks and any .git,
.gradle, build or .direnv component. flake.nix, flake.lock, package.nix and
package-deps.json remain evaluator/cache inputs; flake release-apps consumes
tools/release. No app/Gradle source consumer reads review/, README.md or AGENTS.md.
The report is in the initial Git flake source once intent-added but is excluded
from effective package/check inputs and may alone be finalized after human PASS.
Freeze inventory details and Git state are appended below before the request.

Any later effective included-input edit invalidates the human result; refreeze and
renew applicable checks before requesting again. After PASS, recheck inventory,
HEAD, Git source membership and `git diff --cached --quiet`. Never reset intent-add.

## Human Nix and remote CI

Final required command from this repository root: `nix flake check`.
Human result: **PASS — human-reported**. After the frozen-input request for
`nix flake check`, the user replied: “flake check passed”. No output or artifact
hash was supplied; no independently observed Nix execution is claimed. Agent has
not run Nix.
Remote CI: not run/queried for these uncommitted changes; no push or dispatch.
No remote success is claimed. The required local human gate is complete.

## Suggested commit subject

`Adopt Git-aware Nix worktree qualification`

All changes remain uncommitted. No ordinary git add, commit, release, push or tag
operation was performed in this repository.

## Frozen inventory and pre-gate Git state

Freeze inventory: `/tmp/totipo-android-nix-git-worktree-freeze.jsonl`,
**242 Git-known regular files**, recording relative path, permission mode and
SHA-256. This deliberately freezes all Git-known files except this excluded report,
a superset of effective inputs and workflow/public-release supplemental inputs.
No custom Nix source mechanism is involved; the inventory is read-only evidence.
Inventory SHA-256: `80a13257f21aa1cd75ab7209cccb149745a87ae7bef67dbb0f897efbae8c84e1`.

All intended new milestone paths are Git-known: the sole new report has an
intent-to-add entry. `git ls-files --others --exclude-standard` is empty.
`git diff --cached --quiet`: **PASS**. `git diff --cached --name-only`: **empty**.
Branch remains `main`; HEAD remains the starting commit. Pre-gate short status:

```text
 M .github/workflows/ci.yml
 M .github/workflows/release.yml
 M AGENTS.md
 M README.md
 M package.nix
 M release/README.md
 A review/NIX_GIT_WORKTREE_QUALIFICATION_POLICY_REPORT.md
 M tools/device/M3D_SYNCTHING_CHECKPOINTS.md
 M tools/release/release.py
 M tools/release/test_release.py

```

`git diff --name-only`:

```text
.github/workflows/ci.yml
.github/workflows/release.yml
AGENTS.md
README.md
package.nix
release/README.md
review/NIX_GIT_WORKTREE_QUALIFICATION_POLICY_REPORT.md
tools/device/M3D_SYNCTHING_CHECKPOINTS.md
tools/release/release.py
tools/release/test_release.py

```

The leading-space ` A` report status means content is unstaged and membership is
intent-to-add. No staged-content diff exists. Post-PASS verification confirmed all 242 frozen files and modes, the inventory
hash, unchanged main/HEAD, unchanged Git source membership, no untracked intended
files, and no staged-content diff. Only this excluded report was finalized after
PASS; no effective included input changed and no qualification was invalidated.
Intent-to-add remains in place. Final `git diff --check` and
`git diff --cached --quiet` PASS; final short status matches the pre-gate status
above. Milestone complete, with all changes uncommitted.
