# Android Actions and release interface maintenance

Status: complete. Implementation, all local qualification and human Nix PASS; frozen inputs unchanged.

## 1. Starting state and classification

Started on clean `main`, HEAD `f763008a1564359bb16c79ee08d236a1eb0f7b91`.
`git status --short` was empty. VERSION was `0.1.2`, VERSION_CODE `3`.
Classification: **PACKAGING_RELEASE, TEST_OR_QUALIFICATION_TOOLING, BUILD_DEPENDENCY**.
No Android product behavior change. Focused selection: release Python suites,
disposable Git/CLI integration, workflow static tests/actionlint and the affected
JVM supply-chain guard. Final normal/strict Gradle, external artifacts, runtime
boundary, disposable signing smoke and human Nix remain required.

Preparation automation is committed at `252b7ee`; recovery workflow/tool/tests are
removed. The `072bd28` normal publication correction remains in release.py and its
draft/listed-release tests. Public certificate/fingerprint match `7ed065b` exactly.

## 2. Published 0.1.2 baseline

The [published release](https://github.com/totipo-org/totipo-android/releases/tag/v0.1.2)
was independently read through the canonical GitHub API: release ID `409339487`,
non-draft, non-prerelease, published `2026-10-11T02:10:31Z`, with
`release-provenance.json`, `totipo-android-0.1.2.apk`, and its `.sha256` asset.
Release notes identify source `f763008a1564359bb16c79ee08d236a1eb0f7b91`
and certificate SHA-256 `74fd4c313856e1773fd174fba4ecd2a12cc4fde85ae34a3d8a3e986353b01632`.
Local annotated tag object `f0c2803ed42386dbb8098da57484ebaa1a802c3e`
peels directly to that source and has message `Totipo Android 0.1.2`.
No release assets, real refs, versions or production signing material are mutated.
The local `gh` command was unavailable; read-only HTTPS API access established the
release baseline instead.

## 3. Complete uses inventory and final exact pins

All `.github` YAML and local/reusable workflow candidates were inspected. There
are two workflows, sixteen uses entries, four external action repositories, and no
local, Docker or reusable-workflow uses. No cache/setup/other action is present.
The following aliases refer to the fully resolved old/new table below.

| Workflow | Job | Step/action | Pin alias |
| --- | --- | --- | --- |
| ci.yml | qualification | Checkout | checkout |
| ci.yml | qualification | Install Nix | install-nix |
| release.yml | qualify | Checkout | checkout |
| release.yml | qualify | Install Nix | install-nix |
| release.yml | qualify | Upload qualified-unsigned | upload |
| release.yml | sign | Checkout | checkout |
| release.yml | sign | Install Nix | install-nix |
| release.yml | sign | Download qualified-unsigned | download |
| release.yml | sign | Upload signed-candidate | upload |
| release.yml | verify | Checkout | checkout |
| release.yml | verify | Install Nix | install-nix |
| release.yml | verify | Download signed-candidate | download |
| release.yml | verify | Upload verified-release | upload |
| release.yml | publish | Checkout | checkout |
| release.yml | publish | Install Nix | install-nix |
| release.yml | publish | Download verified-release | download |

Canonical repository identity, non-archived/non-disabled status, latest stable
release API, release-tag commit resolution and exact-commit action.yml were checked
for every repository. All executable pins use full lowercase SHA plus exact semver.

| Action/alias | Old version / SHA / runtime | Final version / SHA / runtime |
| --- | --- | --- |
| actions/checkout (checkout) | v7.0.1 / `3d3c42e5aac5ba805825da76410c181273ba90b1` / JavaScript node24 | v7.0.1 / `3d3c42e5aac5ba805825da76410c181273ba90b1` / JavaScript node24; already current |
| cachix/install-nix-action (install-nix) | v31.11.1 / `13d8dd58da0234aa297dedd986986ccb8e7f3e24` / composite | v31.11.1 / `13d8dd58da0234aa297dedd986986ccb8e7f3e24` / composite; already current |
| actions/upload-artifact (upload) | v4.6.2 / `ea165f8d65b6e75b540449e92b4886f43607fa02` / JavaScript node20 | v7.0.2 / `cf430e030ddbb5b0abf93d22962f4752f3646cd9` / JavaScript node24 |
| actions/download-artifact (download) | v4.3.0 / `d3f86a106a0bac45b974a628896c90dbdf5c8093` / JavaScript node20 | v8.0.2 / `9000827ccba6bdab643e8b6fd33ac0654aef8333` / JavaScript node24 |

Every old semantic version was proved by upstream tag resolution, not inferred
from comments. Download's latest release is v8.0.2, beyond the planning orientation.

## 4. Upstream change and trust review

Read the canonical release history between pinned and selected artifact releases,
exact metadata, current READMEs and upload/download implementation.
[Upload releases](https://github.com/actions/upload-artifact/releases): v5 updates
runtime/artifact dependency support, v6 selects Node 24 by default (runner minimum
2.327.1), v7 adds optional direct single-file uploads and migrates to ESM; v7.0.1
updates transport dependencies/docs, v7.0.2 updates artifact to 6.3.1 and retries
429 responses with Retry-After. GHES-only v3 backports explicitly deprecated on
github.com were reviewed and excluded.

[Download releases](https://github.com/actions/download-artifact/releases): v5
normalizes single-ID downloads to the requested directory; name downloads already
use that layout. v6 updates runtime/dependencies, v7 selects Node 24 by default
(runner minimum 2.327.1), v8 uses content type to distinguish ZIP/direct downloads,
migrates to ESM, and fails digest mismatch by default. v8.0.1 fixes CJK/name and
content-type handling; v8.0.2 updates artifact 6.3.1 and retry handling. Totipo uses
name-based ZIP downloads, so the ID layout change does not affect it. Fatal digest
mismatch strengthens transport integrity; it is retained without an override.
Hosted ubuntu-24.04 runners satisfy the supported runtime requirement.

[Checkout v7.0.1](https://github.com/actions/checkout/releases/tag/v7.0.1)
is already current. Metadata is node24; no executable upgrade was needed. Reviewed
v6's separate credential-file storage and v7's ESM/fork-PR safety behavior plus
v7.0.1 ASCII whitespace/config escaping fixes. Totipo uses push/pull_request and
manual dispatch, never pull_request_target/workflow_run. Exact source retains
credential removal in `finally` when persistCredentials is false. Every checkout
keeps `persist-credentials: false`; all four release checkouts keep `fetch-depth: 0`
and explicit github.sha. No credential/worktree/tag-source guarantee is loosened.

[Cachix v31.11.1](https://github.com/cachix/install-nix-action/releases/tag/v31.11.1)
is already current, maintained by the canonical Cachix project. Exact action.yml
is composite, invoking only its own install-nix.sh through bash; Node runtime does
not directly apply and there are no nested actions. Inspected the entire shell:
existing curl/shell/sudo/python3/system utilities install pinned Nix 2.35.2 from
releases.nixos.org; no new installer URL override or third-party trust was added.
Latest release fixes the Nix 2.35.1 crash. Existing token-backed Nix access/trusted
user behavior and accept-flake-config stay unchanged. No broader GitHub permissions.

No pinned JavaScript action remains on Node 20; no runtime override or warning
suppression was introduced. Local evidence establishes runtime selection, not a
claim about unexecuted remote logs. Later ordinary CI/release must confirm no
repository-controlled Node-20 warnings. No production workflow was dispatched.

## 5. Artifact-transfer and permission invariance

Graph remains qualify -> sign -> verify -> publish. Three unique immutable artifacts
remain qualified-unsigned, signed-candidate and verified-release. Each is uploaded
once as a directory archive; archive=true, overwrite=false, include-hidden-files=false
are now explicit. Seven-day retention and missing-file error stay unchanged.
Bundle filenames are nonhidden. A single directory root preserves relative contents;
no wildcard flattening, repeated-name upload, direct-file upload or merge is used.
Name-based downloads extract directly into runner.temp/bundle; same-run/repository
scope remains implicit, without token/run-id/repository overrides. No digest output
is consumed. Source/provenance/signer/payload revalidation remains between phases.

Global contents:read remains; sign alone has android-release environment and
contents:read, publish alone contents:write. Four production secret expressions
remain isolated in one signing shell step. Publish has no Android signing secret.
No OIDC, added permission, changed job dependency or new action project.
The only dispatch-interface change is `commit` renamed `source_commit`, still
mapped to REQUESTED_COMMIT and subject to identical source validation.

## 6. Two-command release interface and readiness

Previous public surface required prepare-release, check-release, tag-release,
push-release-tag. Public release apps now expose only prepare-release and release-tag;
update-package-deps remains unrelated. Repository search found no non-human consumer
requiring the removed apps. Obsolete local-tag/push-tag CLI branches were deleted;
check-release remains internal, read-only and documented only as a diagnostic.
No new Python module, language, runtime dependency or wrapper was introduced.

Prepare preserves consumed annotated history-derived code allocation, canonical
stable semantic policy, exact metadata-only diff, public signer/source guards,
idempotence and no commit/tag/push behavior. Its output explicitly names VERSION
and VERSION_CODE. It never asks for a subsequent check-release command.

release-tag independently validates caller worktree/main/actual HEAD, empty index,
clean tree, exact target VERSION, expected history-derived VERSION_CODE, canonical
Gradle wiring/application ID, source-pin inventory, public certificate and private
material guard. Dev/SNAPSHOT/noncanonical semantic targets and incoherent history
fail. Both local origin/main and live remote main must equal HEAD. Single matching
fetch/push endpoint is required before mutation. Target alone is excluded from
consumed history while its code is checked, making repeat invocation possible.

## 7. Tag state, remote verification and output

No local/remote target: create exactly unsigned annotated v<VERSION> with --no-sign,
exact message and explicit HEAD. Correct local-only: validate and push it. Correct
local/remote: validate, do no tag/push mutation, report identical coordinates.
Any wrong local/remote object, lightweight/nested/signed tag, wrong header/message,
wrong commit/source metadata or remote-only unreviewed tag fails closed. Tags are
never deleted, moved, recreated, forced or automatically adopted.

Validation inspects raw annotated tag headers/message, direct commit, regular
VERSION/VERSION_CODE blobs and exact metadata. Remote object/peel must match the
local validated object/HEAD. Target is fetched into a temporary bare object database,
with no worktree ref or FETCH_HEAD write and automatic maintenance disabled.
This permits full remote message/source inspection, not just ls-remote equality.
Before mutation remote refs/source cleanliness/HEAD are checked again. Push uses
one immutable validated object ID and one explicit refs/tags/v<VERSION> destination,
--no-follow-tags, --no-force and mirror=false. Explicit refspec overrides configured
wildcard/force push refspecs. It never pushes main or unrelated tags.

After push (also for no-op), live main and target ref/object/peel are read again;
the remote annotated object and source blobs are fetched/read/validated, then remote
refs and local HEAD/tag/cleanliness are checked once more. Only then success prints
full lowercase source_commit with local HEAD == tag peel == live remote main.
Git cannot transactionally freeze main across an external network round trip;
failures after local creation or push leave only the partial tag state for safe
validation/retry, never rollback/movement or unverified success. A deterministic
post-push main drift fixture rejects success.

Human output ends with release coordinates and copy-ready GitHub Release inputs
version/source_commit; JSON mode prints deterministic version, version_code, tag,
source_commit only on stdout. Git progress/errors remain on stderr. No signing,
protected environment entry, GitHub release creation or workflow dispatch in this
command. Publication remains a separate manual protected approval boundary.

## 8. Focused validation and failure evidence

Python suite includes existing preparation/history/schema/payload/publication tests,
full actual CLI integration with committed public identity in disposable repos,
plus create/push/exact-only remote delta, no main push, full SHA/output/JSON,
repeat/no-op, local-only resume, dirty/staged/wrong metadata, tracking/live main
drift, wrong/lightweight/message/source/object tags, remote-only state, endpoint
mismatch, unreadable remote object, post-push drift and concurrent wrong-remote-tag
push rejection despite global followTags/mirror/force refspec settings.

First suite exposed the expected old flake digest; reviewed app-surface-only flake
change was rebaselined in OtpAuthSupplyChainGuardTest (no runtime edits). Two suites
then passed 45 tests. After additional race cases, direct CLI exposed detached Git
maintenance racing temporary database cleanup. A narrow direct-CLI reproduction
confirmed OSError directory-not-empty; fetch now disables automatic maintenance.
No broad gate was repeated for diagnosis. Final suite: **47 tests PASS**,
18.327s, log `/tmp/totipo-actions-tests-qualified.log`. Affected JVM guard: **PASS**,
1m7s, 22 tasks (7 executed/15 up-to-date), `/tmp/totipo-actions-focused-gradle.log`.

Workflow static tests cover all discovered uses, exact reviewed allowlist/SHA/semver,
expected workflow set, credentials/history settings, immutable artifact settings,
job graph, permission/secret isolation and the renamed source_commit input.
They intentionally skip absent workflow inputs inside package.nix's filtered source;
full checkout qualification executes them before source freeze.

Actionlint v1.7.12 from canonical rhysd/actionlint release: archive verified against
published SHA-256 `8aca8db96f1b94770f1b0d72b6dddcb1ebb8123cb3712530b08cc387b349a3d8`.
Both workflows passed twice with no diagnostics (the second pass was the stable
check). No runtime dependency was added. Fetching the upstream download implementation
initially used a nonexistent main.ts path; corrected canonical tree lookup located
src/download-artifact.ts and the complete source was read.

## 9. Effective input freeze

package.nix was re-read: app/src, gradle, tools and the exact enumerated Gradle,
metadata/license/branding files are source inputs; .git/.gradle/build/.direnv path
components and symlinks are excluded. flake.nix/flake.lock/package.nix/package-deps.json
are evaluator/cache inputs. Workflow YAML is outside Android package filtering,
but included in this milestone's supplemental freeze, along with release docs.
README/AGENTS/review reports are not otherwise consumed. Report-only updates after
freeze do not invalidate Gradle or Nix. Hash evidence stored outside repository
inputs: `/tmp/totipo-actions-input-freeze.json`, 172 files, manifest SHA-256
`902601469f6226ae2f09717137a5e87517ab761ce8a8951748b3fc4a1af799b3`.
All four freeze statements below correspond to this manifest and focused PASS.

- Production source stable: no app runtime/resource/manifest change; VERSION 0.1.2/code 3 unchanged.
- JVM tests stable: only reviewed flake digest/comment changes in supply-chain guard.
- Build/package configuration stable: only two-command release app list changed; no toolchain/lock/cache edit.
- Qualification harness/tooling stable: source/release tools, tests and workflow pins frozen after focused PASS.

## 10. Qualification execution counts and results

| Gate | Count / result |
| --- | --- |
| Baseline full Gradle | 0; packaging/tooling scope starts with focused checks, as prior release-tooling milestone |
| Focused JVM Gradle | 1 PASS, supply-chain guard |
| Python release suites | 5: initial flake-pin RED, 45 PASS, 45 PASS, maintenance-race RED, final 47 PASS |
| Narrow direct CLI diagnosis | 1 RED, confirms maintenance cleanup race before fix |
| Actionlint | 2 PASS, both workflows each time |
| Final normal full | 1 PASS, 2m48s; 90 tasks, 26 executed/64 up-to-date; 338 JVM tests, zero failures/errors/skips |
| Strict offline clean | 1 PASS, 2m41s; 92/92 tasks executed; 338 JVM tests, zero failures/errors/skips |
| Final external artifact set | 1 PASS set: wrapper, debug APK, unsigned release APK (3 commands) |
| Embedded Gradle APK verifier | 2 PASS executions, normal and strict release boundary |
| Offline runtime/Maven boundary | 1 PASS; 818ms, 2 tasks executed |
| Disposable signing smoke | 1 PASS; 2 TEST keys, 3 signatures, 6 expected negative rejections; keys erased |
| Physical/device install | 0; not required |
| Human Nix | 1 final routine `nix flake check path:.` PASS, human-reported |

Physical harness/install count: 0, not
required; no Android runtime behavior changed. Agent Nix invocations: 0. Real
release/tag/push/commit/stage/production-signing mutations: 0. Disposable fixture
Git mutations are confined to temporary repositories and bare remotes.

### Final normal and strict evidence

Normal PASS command (one invocation, `/tmp/totipo-actions-final-normal.log`):

```sh
./gradlew check :app:assembleDebug :app:assembleRelease
```

Strict PASS command (one invocation, `/tmp/totipo-actions-final-strict.log`):

```sh
./gradlew --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug :app:assembleRelease
```

Both gates passed JVM tests, lint, both Maven boundaries, embedded release APK
boundary and assemblies. No included-input change or invalidated pass followed
normal/strict. Existing Gradle 10 deprecation and AGP AAPT2 override notices remain
visible; they are separate from GitHub Actions Node runtime warnings.

### Final artifacts, runtime and signing evidence

One final external set against strict-clean outputs:

```sh
python3 tools/verify-wrapper.py
python3 tools/verify-apk.py app/build/outputs/apk/debug/app-debug.apk --debug-probe
python3 tools/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk --unsigned --no-debug-probe
./gradlew --offline :app:dependencies --configuration releaseRuntimeClasspath :app:verifyMavenBoundary
```

All PASS; logs `/tmp/totipo-actions-verify-wrapper.log`,
`/tmp/totipo-actions-verify-debug.log`, `/tmp/totipo-actions-verify-release.log`,
`/tmp/totipo-actions-runtime-boundary.log`. Manifest/permission/service/ingress/
backup/branding/DEX/debug-release exclusions pass. Current runtime graph remains
Totipo NIO/core 0.2.0 and BC 1.86 without added external modules.
Pinned aapt2 reports application ID org.totipo.android, versionName **0.1.2** and
versionCode **3** for both APKs. Strict debug SHA-256:
`cefd6f5afe73fbaae8f765dabdb2ea1a3ab6726b8c9bcbfe31cd6bf2e2ec9dd4`.
Strict unsigned release SHA-256:
`b70501790d32ff383665c8f7c1a599d750036b91a3b1fb0b804f706b8341e012`.

One unchanged `bash tools/release/signing-smoke.sh app/build/outputs/apk/release/app-release-unsigned.apk`
PASS (`/tmp/totipo-actions-signing-smoke.log`): disposable test certificates only,
pinned signer/v2/v3/payload verification, six rejected negatives, original unsigned
hash/provenance unchanged, private test keys erased. Internal smoke verifier calls
are separate from the final three-command external set. No production signer.
Physical qualification is NOT REQUIRED and no APK is installed.

### Human Nix gate

PASS, human-reported in the follow-up “flake check passed”. One final routine
invocation; the agent did not run Nix. All four freeze statements above remain true,
and all 172 hashes were rechecked after the human result. Qualified command:

```sh
nix flake check path:.
```

This covers the final two-command flake app/wrapper wiring and Android package
checks. No routine nix build or agent Nix execution. Remote CI is also unexecuted;
static/local evidence is sufficient here without dispatching production release.

## 11. Future human procedure

From repository root, before commit:

```sh
nix run path:.#prepare-release -- <VERSION>
```

Review the exact metadata diff, commit normally, push main normally, wait for
ordinary CI as desired. Then exactly:

```sh
nix run path:.#release-tag -- <VERSION>
```

Use printed version/source_commit in the manual normal Release workflow on main,
then approve android-release. There is no mandatory third repository command.
The published 0.1.2 release is not prepared/tagged/published again.

## 12. Final Git and invariance

Everything remains unstaged/uncommitted on main at starting HEAD
`f763008a1564359bb16c79ee08d236a1eb0f7b91`. Index is empty.
No VERSION/VERSION_CODE, production source, signing certificate, dependency lock,
flake.lock, package-deps.json or package.nix change occurred. Signing/publication/
provenance/payload implementation remains unchanged. Public input name and action
transport upgrades are the documented workflow changes. Human Nix PASS completes
the milestone. Only this excluded report was edited after the human result; no
qualified input was changed and no gate was invalidated.

Changed files: both workflow YAMLs, flake.nix, release.py/source.py, test_release.py/
test_source.py, the JVM supply-chain guard flake pin/comment, root/release READMEs,
and this report. No AGENTS change was needed. Documentation local links and
`git diff --check` PASS. This report is outside effective package/check inputs;
updating human results here after freeze does not invalidate qualified artifacts.

Final recorded Git state after the human Nix PASS (unchanged):

```text
 M .github/workflows/ci.yml
 M .github/workflows/release.yml
 M README.md
 M app/src/test/java/org/totipo/android/OtpAuthSupplyChainGuardTest.java
 M flake.nix
 M release/README.md
 M tools/release/release.py
 M tools/release/source.py
 M tools/release/test_release.py
 M tools/release/test_source.py
?? review/ANDROID_ACTIONS_RELEASE_UX_REPORT.md
```
