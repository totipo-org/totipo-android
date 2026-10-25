# Android release publication reliability

Status: implementation, all host qualification and human `nix flake check` PASS.
All changes remain uncommitted. No live GitHub publication, workflow dispatch,
release mutation, production signing, push or repository tag operation performed.
The completed v0.2.3 release was neither inspected for recovery nor modified.

## Scope and starting state

Classification: **PACKAGING_RELEASE** and **TEST_OR_QUALIFICATION_TOOLING**.
Started on clean `main`, HEAD `f69ba4e62a312178bd5bccff622ef54c17328b0b`;
`git status --short` was empty. Read AGENTS.md, README, development and release
documentation, package.nix, flake.nix, CI/release workflows, release.py, source.py,
release tests and prior release qualification reports.

No production Java/resources/manifests, JVM tests, Gradle/package configuration,
version metadata, dependencies, signing identity or workflow permissions changed.
The Android/Java application operation model is outside this GitHub publication
change. Existing source/tag/version/provenance and signing checks remain in force.

## Root cause

The supplied incident evidence states that v0.2.3 was signed and verified, the
new draft and three assets existed, and the maintainer independently checked the
assets and signing before publishing manually. This report accepts that evidence;
it does not claim a new inspection or reproduction against the live release.

Previously `gh release create --draft` also uploaded all assets, but its response
did not supply a machine-readable release ID to this code. Publication then searched
a fresh paginated release listing and failed with `Expected newly created draft
release` if that listing did not expose the draft. Creation/upload success and
immediate list visibility were incorrectly coupled. Adding a sleep to that lookup
would retain the dependency and ambiguous mutation behavior.

## Implementation and preserved boundaries

1. Retain bundle/source/public signer validation, signed checksum, native signed
   verification, unsigned policy and complete signed/unsigned payload equivalence.
2. Reject a known published release or observable draft before creation, using the
   tag endpoint and authenticated paginated listing. Neither absence check proves
   that no draft exists. Server-side duplicate rejection remains fatal.
3. Revalidate exact source identity immediately before a single REST creation POST.
   Capture a positive integer release ID and require the exact tag and draft state.
   Explicitly supply the reviewed source commit and `prerelease=false`.
4. Upload exactly APK, APK checksum and provenance to the captured release ID.
   Capture each positive integer asset ID. Construct the GitHub upload endpoint
   directly, supply content type/length and raw file input, and reject invalid or
   duplicate IDs, names, states or sizes.
5. Query that release ID and its asset collection, require the exact asset set,
   retrieve each asset's metadata and binary by asset ID, and compare whole-file
   SHA-256 to its qualified local file. No post-creation release listing or tag
   download is used. Binary header parsing preserves all payload bytes.
6. Revalidate source identity and draft state immediately before one publication
   PATCH to that exact release ID.
7. Confirm the same release is published and the exact asset IDs, names, sizes,
   states and downloaded hashes still match before reporting success.

`source.identity()` is unchanged: dispatch main/SHA, checkout HEAD, clean tree,
VERSION, VERSION_CODE grammar, local annotated tag, remote tag object/peel, live
main and committed public certificate remain checked. It replaces the removed
CLI `--verify-tag` preflight with the stronger existing annotated/source checks.
Consumed-code history, tag preparation/immutability policy, provenance schema,
APK verifier and payload/signature implementations are unchanged. The same Actions
GH_TOKEN and runner-provided gh are used; no credentials/dependencies are added.
The protected signing environment, all job dependencies and first-party artifact
handoffs remain unchanged. No recovery or resume command is introduced.

## Retry and failure-state rules

| Operation/result | Behavior |
| --- | --- |
| Read-only API HTTP 408/429/500/502/503/504, timeout, or interrupted transport | At most 3 attempts, backoff 1s then 2s; each subprocess timeout 60s |
| Known release/asset ID temporarily returns 404 | Same bounded read retry; never interpreted as permission to create/upload again |
| Expected uploaded assets temporarily absent from collection | At most 3 collection observations, 1s/2s backoff; each HTTP read independently has the transport bound above |
| Interrupted binary download | Discard the entire incomplete response, restart GET, then require complete SHA-256 equality |
| Initial published-tag lookup 404 | Absence only with the existing validated HTTP/error-body/CLI-status contract |
| HTTP 401/403, CLI usage/authentication exits 2/4 | Stop immediately; no retry |
| Malformed successful response, conflicting identity/state, extra/duplicate assets, wrong sizes/hashes | Stop immediately |
| Creation failure/timeout/missing or invalid response | Stop after one POST; no draft adoption, recreation, deletion or upload |
| Partial/ambiguous upload | Stop without repeating POST, replacement or cleanup; known draft ID is logged |
| Successful publication response | Require exact ID/tag and published state, then independently verify remote state/assets/bytes |
| Transient/ambiguous publication response | Never repeat PATCH; read-only reconciliation must prove that exact ID is published with the verified assets/bytes |
| Still-draft/conflicting/unverifiable publication state | Fail, preserving evidence for deliberate human inspection |

No sleeps follow successful expected operations. No retry wraps a mutation or an
arbitrary subprocess. Missing local executables and other local OS errors fail.
An ambiguous creation may leave an unknown draft; partial uploads may leave a
known draft; failed final verification may leave an already published release.
A new invocation rejects observable existing releases rather than resuming them.

## Regression evidence

All API publication tests mock the subprocess boundary and use disposable local
files, with binary fixtures containing NUL, non-UTF-8 and header delimiters. No
GitHub authentication or network is used. Source/history tests use disposable Git
repositories and local bare remotes only; their commits/tags/pushes are fixtures.

The publication suite covers:

- Complete create/upload/verify/publish with exact mutation counts, source checks,
  upload coordinates and final downloaded hashes, without any successful-operation
  backoff; the new draft never appears in the release list.
- Transient metadata queries, interrupted downloads and bounded exhaustion;
  temporary known release/asset 404 and incomplete asset collection.
- Remote creation success with ambiguous HTTP, malformed JSON, lost response or
  timeout; no second creation or adoption.
- Existing published release, observable draft, ambiguous duplicate listings,
  paginated preflight and server-side duplicate rejection.
- Partial/ambiguous upload and refusal to resume an existing draft on another run.
- Missing/corrupt/extra/duplicate assets and wrong asset ID/name/state/size.
- Incorrect release ID/tag, malformed and incorrect draft states at creation,
  before upload/publication and during final verification.
- Source drift before publication; publication timeout/lost response/5xx/invalid
  JSON, both published reconciliation and unresolved still-draft failure.
- Wrong PATCH identity and asset replacement after publication.
- Strict initial tag-404 handling, malformed HTTP/JSON, local CLI failures,
  authentication/permission failures, binary-safe LF/CRLF response framing.

Two older orchestration tests from test_release.py and the previous publication
fixtures were replaced by these transport-level regressions. Artifact/provenance,
APK policy, source/history and real disposable source-CLI tests remain.

Initial focused run: 17 tests, one fixture failure. The mock incorrectly asserted
that every query used ID 42 when the test deliberately supplied creation ID 43;
it now returns the conflicting identity for the code to reject. The next focused
run passed. Subsequent coverage added binary framing, additional ambiguity,
wrong-boolean draft state, non-adoption and CLI authentication-exit cases. Final
review restricted 404 retry eligibility explicitly to known IDs: an unknown
preflight-list 404 fails immediately, with its own regression.
Full-checkout and source-aware discovery are repeated only for these additions;
they do not invalidate Gradle outputs because Gradle does not consume this Python.

## Qualification

The managed Nix/direnv environment supplies Python 3.14.7, Gradle 9.8.0,
JDK 17 and the pinned Android SDK. No agent Nix invocation is performed.

Baseline standalone release discovery: **64 tests PASS**, 13.375s, no skips.
Final standalone release discovery: **80 tests PASS**, 17.175s, no skips.
Final source-aware discovery using exactly the flake release-tooling fileset:
**80 tests PASS**, 16.342s, no skips. Publication-specific suite: **23 tests PASS**. Direct source readiness/public identity/private-material
guard: PASS.

Normal checkpoint `gradle check :app:assembleDebug :app:assembleRelease`: **PASS**,
1m54s, 89 actionable tasks (29 executed, 60 up-to-date).
Strict checkpoint:

```sh
gradle --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug :app:assembleRelease
```

**PASS**, 4m23s, 91 actionable tasks / 91 executed. Strict output XML records
**462 JVM tests**, zero failures/errors/skips. No full gate failed or was repeated.

Both final external `tools/verify-apk.py` commands passed against the strict outputs
(debug `--debug-probe`, release `--unsigned --no-debug-probe`). Identity/SDK,
manifest permissions/components/exports/filters/backup/variant flags, essential
branding and release/debug exclusions were accepted by the unchanged full policy.
Source VERSION **0.2.3** and VERSION_CODE **8** remain unchanged.

```text
debug APK:    a5a0582d9eb32f7edbc575957600e8293a55bcbbcdb811d95ae786c54e97852e
unsigned APK: abdb8a56f3486d0ca8f0cbbc2101876fdfd82c8df9a5341a364c01b9eed6016d
```

The final offline runtime graph / Maven-boundary command passed (2s, two tasks
executed). Reviewed graph contains only Totipo NIO/core 0.2.0 and BC 1.86, with the
existing strict constraints. Existing AAPT2 experimental-option and Gradle
deprecation warnings remain; no suppression or dependency-cache changes.

One unchanged disposable signing-smoke run **PASS** against the strict unsigned
APK: one TEST signer, native v2/v3 checks, alignment, payload/provenance equality,
ten expected verifier rejections and two SDK corruption rejections. Its nine nested
unsigned APK-policy checks are separate from the two final external artifact
commands. Temporary non-production keys were erased; no persistent qualification
identity or production private key was accessed. Documentation local-file links
and `git diff --check` passed. No physical device
qualification is relevant: application behavior and device harnesses are unchanged.

## Source membership, freeze and invalidations

Inspected actual package.nix filtering and flake.nix consumers. All tools/release
files enter Android package source, and the release-tooling check separately
includes that tree, tools/verify-apk.py, build scripts, release/signing and the
release workflow. release/README.md and review/ are excluded from both effective
check inputs. The included tooling changes require a final human Nix check even
though Gradle does not consume the changed Python. The package remains qualified
through the unchanged package-inclusive flake check.

Frozen at **2026-10-25 01:17:22 UTC**, after all host gates passed:

- **Production source stable:** unchanged from starting HEAD; normal/strict build,
  APK policy and disposable signing checks PASS.
- **JVM tests stable:** unchanged from starting HEAD; strict 462-test PASS.
- **Build/package configuration stable:** unchanged Gradle/Nix/cache/lock files,
  workflow and signing identity; strict offline build and runtime boundary PASS.
- **Qualification harness/tooling stable:** final publication/source suites 80/80
  PASS without skips in both source contexts, focused publication 23/23 PASS;
  original signing smoke/APK verifier/device tooling unchanged.

Included modified tooling SHA-256 values:

```text
0b3bd3e83444cc74b2ce2ebbffb349251466eed3b0871b47fcfe2412ad4ec4d7  tools/release/release.py
2076c400574eecdeda2faaecbfa5a901271c0727ce59c70db90d69d41036ff19  tools/release/test_publication.py
d1690b6e83c6936125b76138ca025663b750668d2f5199c57f4640342cf4da2a  tools/release/test_release.py
```

The report is Git-known via `git add --intent-to-add`; no staged file content.
Before the human gate, `git status --short` and `git diff --name-only` identify
exactly these five milestone files; `git diff --cached --name-only` is empty and
`git diff --cached --quiet` passes. Final branch/HEAD remain `main` /
`f69ba4e62a312178bd5bccff622ef54c17328b0b`.

```text
 M release/README.md
 A review/ANDROID_RELEASE_PUBLICATION_RELIABILITY_REPORT.md
 M tools/release/release.py
 M tools/release/test_publication.py
 M tools/release/test_release.py
```

The leading-space ` A` report status denotes intent-to-add, not staged content.
Only this excluded report was completed after the included-input freeze.
Human `nix flake check`: **PASS**, reported by the maintainer after the final
input freeze (one final routine invocation; not run by the agent). The agent
rechecked the three frozen tooling hashes, unchanged starting HEAD, exact changed
file set and absence of staged content after that report. All included inputs
remain frozen; only this excluded report was updated to record the result.
Report-only updates after the freeze do not invalidate Nix under these filters.
No successful full gate has been invalidated by Android/Gradle input changes.

## Changed files

- tools/release/release.py — captured IDs, explicit upload/download API calls,
  narrow read retry and publication reconciliation/final verification.
- tools/release/test_publication.py — deterministic HTTP/transport/state regressions.
- tools/release/test_release.py — remove two superseded publication fixtures.
- release/README.md — publication flow, retry/failure rules and API references.
- review/ANDROID_RELEASE_PUBLICATION_RELIABILITY_REPORT.md — this report.

## API references and remaining limits

Reviewed GitHub's [release APIs](https://docs.github.com/en/rest/releases/releases)
and [asset APIs](https://docs.github.com/en/rest/releases/assets), plus the official
[gh api interface](https://cli.github.com/manual/gh_api) and its
[HTTP implementation](https://github.com/cli/cli/blob/trunk/pkg/cmd/api/http.go).
Creation returns the release ID; get/update use it. Draft listing visibility needs
push authority; release-by-tag documents published releases. Asset metadata/binary
GET uses asset ID; binary responses can stream or redirect. gh follows redirects.

GitHub supplies no transaction spanning tag checks, draft creation, asset upload,
hash verification and publication. A preflight list is neither an atomic absence
proof nor protection against concurrent actors. The existing tag protections and
source checks still matter: REST creation can create a tag if one disappears
between validation and POST, just as `--verify-tag` had a check/request race.
No remote objects are automatically repaired in response to such races.
A lost creation response has no idempotency key here and cannot safely authorize
another creation. Upload errors can leave partial/starter assets; these require
human inspection rather than automatic deletion/retry. Bounded retries cannot
cover indefinite service outages or visibility delays. Final checks establish
observed state and bytes, not permanence against later authorized changes.
Runner gh remains supplied by the existing Actions image rather than a new pin;
unsupported response formats fail closed. No live API integration/publication test
is claimed. No new local gh installation or access is required.

## Execution counts and recommendation

| Execution | Count / result |
| --- | --- |
| Baseline full Gradle | 0; standalone release-tooling baseline selected, no product changes |
| Focused Gradle compile/JVM tests | 0; affected tests are Python |
| Final normal full Gradle | 1 PASS |
| Strict-clean offline Gradle | 1 PASS |
| Final external artifact verifier | 1 final set: 2 commands, both PASS |
| Embedded Gradle APK verifier executions | 0; full policy remains external/Nix installCheck |
| Final offline runtime graph / Maven boundary | 1 PASS |
| Disposable signing smoke | 1 PASS; 9 nested unsigned APK-policy checks |
| Physical harness | 0; no affected device subsystem |
| Human Nix | 1 final routine check PASS, maintainer-reported |
| Full-checkout Python discovery | 5: baseline64, intermediate77, expanded78/79, frozen80; all PASS |
| Focused publication discovery | 6: 17 (one fixture failure), 17/20/21/22/23 PASS |
| Source-aware Python discovery | 3: expanded78/79 and frozen80, all PASS |
| Direct source readiness | 1 PASS |
| Production signing / live publication / workflow dispatch | 0 |

The final CLI auth-exit and known-ID-only 404 retry fixes superseded the
expanded78/79 Python results only. All Gradle-consumed inputs stayed unchanged;
no full Gradle/artifact gate was repeated or invalidated. These are intentional
host release-boundary checks, following the prior release-tooling milestone's
normal/strict/artifact/signing evidence; the final human Nix gate additionally
covers changed tools in both package source and the standalone check.
Recommendation: ready for normal review and committing after approval of this
diff; all required qualification, including the human Nix gate, has passed.
Do not rerun, recreate or republish v0.2.3 to test this change. Leave these changes
uncommitted for normal review; no commit/push/tag/dispatch is authorized here.
