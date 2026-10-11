# Android release preparation automation milestone

## 1. Starting HEAD/state

Started on clean `main` at `87cd21a5b0a952cd40d638901af8fe25a52600cd`.
`git status --short` was empty. VERSION was `0.1.1`; the only Android
versionCode assignment was `versionCode = 2`. Committed prerequisites:
`072bd28` normal publication correction/recovery, `7ed065b` public production
identity, and `87cd21a` 0.1.1 source preparation. No runtime source edits intended.

## 2. Classification

PACKAGING_RELEASE, TEST_OR_QUALIFICATION_TOOLING, BUILD_DEPENDENCY.
VERSION_CODE migration is a real Gradle/Nix input change. This is not a product
milestone; the initial focused reproduction replaces an ordinary product baseline.
Final normal, strict, artifact, signing and human Nix gates remain mandatory.

## 3. Actual 0.1.1 Nix failure

Public Actions metadata for protected release run
[38100888131](https://github.com/totipo-org/totipo-android/actions/runs/38100888131)
shows `qualify` failed at **Authoritative qualification**, after source identity
and focused guardrails succeeded; sign/verify/publish were skipped. CI runs
38100854595 and 38100843267 also failed at this HEAD. The supplied task identifies
the supply-chain test failure during Nix qualification. Unauthenticated job-log
retrieval returned HTTP 403, so the remote traceback itself was not independently
retrieved. The exact same guard failure was independently reproduced locally
before editing, as recorded below; no agent Nix invocation occurred.

## 4. OtpAuthSupplyChainGuardTest root cause

Before editing, ran:

```sh
./gradlew :app:testDebugUnitTest --tests org.totipo.android.OtpAuthSupplyChainGuardTest.reviewedSupplyChainInputsRemainPinned
```

FAIL: one test, ComparisonFailure at line 42. It pins the entire raw bytes of
`app/build.gradle.kts`, without normalization or region extraction.
Expected SHA-256:
`69b3f5c737e5f98a1d99be4fe02204f6f2cfadef1b85323c66916a9619db8736`.
Actual SHA-256:
`3db2376007595763bb2b824c35e4789c2ad1e10cea0c8df3f6be201bfeeaf945`.
`git show v0.1.0:app/build.gradle.kts | sha256sum` exactly matches expected.
The v0.1.0..v0.1.1 diff of this file contains only `versionCode = 1` -> `2`.
Thus mutable release metadata, not dependency drift, triggered the guard.
Original XML evidence was preserved in `/tmp/totipo-release-milestone/baseline-guard.xml`.

## 5. Original security intent

The whole-file digest inventory protects reviewed plugins, dependencies,
repositories, resolution rules, dependency verification/locks, toolchain and
build-security logic. It must still reject unexpected changes to those surfaces.
Its accidental inclusion of mutable versionCode did not represent a distinct
dependency security property.

## 6. Structural guard correction

Keep full app build-script pinning. Rebaseline it once for the reviewed file-reading
parser: `eb6dbefbbdef74771d5d428969248dd936b40955c0b8286adcb07ff8db41e98c`.
Rebaseline the flake pin for the explicitly reviewed apps/check wiring only.
All other expected digests are unchanged. VERSION and VERSION_CODE are deliberately
outside this stable supply-chain inventory; release policy validates them separately.
Future code 2 -> 3 changes no pinned script bytes. No locks, dependency verification
metadata, dependency versions, package cache or flake lock were refreshed.

## 7. VERSION_CODE design

Root VERSION_CODE contains exactly `2\n` for this migration. One canonical positive
base-10 integer, no spaces/comments/sign/leading zeros, one LF. It is the sole
current numeric release authority; no preparation of real 0.1.2 occurred.

## 8. Gradle parsing/failure behavior

`providers.fileContents(rootProject.layout.projectDirectory.file("VERSION_CODE"))`
reads the tracked input. Exact regex validation precedes suffix removal and
`toIntOrNull`, then Android range 1..2100000000 is required. Missing input has no
fallback; no trimming hides malformed source. Android receives `releaseVersionCode`.
VERSION/versionName wiring is unchanged. Standalone temporary Gradle harness runs
the exact parser text, testing code 2, 13 malformed variants and missing input.
Its final versionName case uses synthetic VERSION 9.8.7, so future releases do not
require rewriting a hard-coded current-version expectation.

## 9. Nix/package propagation

VERSION_CODE is added to package.nix's exact source allowlist. Changing its bytes
changes filtered source and therefore Android/check derivation input. Existing
checks.android/packages.default identity is preserved. Git is now an explicit
package native input for temporary-repository release tests. Flake adds release
apps and a wrapper check; dependency/cache/toolchain pins remain unchanged.
AGENTS.md's inventory now lists VERSION_CODE. All selected tools/tests remain inputs.

## 10. Recovery removal

Removed `.github/workflows/recover-release.yml`, `tools/release/recover.py` and
`tools/release/test_recovery.py`. Removed the recovery identity injection hook from
bundle/publication helpers. Removed prospective verified-artifact recovery guidance
from release/README.md. The historical
`review/ANDROID_RELEASE_PUBLISH_RECOVERY_FIX_REPORT.md` remains byte-identical.
Private-key backup/recoverability language still describes key custody, not artifact recovery.

## 11. Normal publication preservation

Normal release.yml and signed verifier/payload comparator remain unchanged.
Published lookup still discriminates machine-readable HTTP status. Authenticated
paginated list rejects drafts before creation and retrieves the newly created
draft afterwards. Create/upload, redownload, whole-file hashes, signer/boundaries,
payload evidence, final source/tag identity and PATCH publication remain in order.
Normal HTTP/refusal regressions moved to test_publication.py; positive publication
tests explicitly assert local and hosted verification before final publication.
No published-only tag lookup is used to retrieve a newly created draft.

## 12. Consumed-tag policy

Every eligible annotated Android release tag consumes its semantic version and
Android code, regardless of subsequent qualification/publication outcome.
Prepare alone consumes neither. Never recycle a code from an aborted tagged release.
Target must be canonical stable X.Y.Z, numerically newer than all consumed versions.
No prerelease/build metadata or leading-zero semantics were added.

## 13. Legacy parser

Historical tagged sources with VERSION_CODE read that file and validate its syntax,
range and regular-file mode. Only tagged sources lacking it use one unambiguous
literal `versionCode = <integer>` assignment in app/build.gradle.kts. Missing,
ambiguous, invalid or overflowing values fail. New-format invalid metadata never
falls back. Current worktrees always require VERSION_CODE and canonical Gradle wiring.

## 14. History and expected next code

Read-only HTTPS comparison confirmed local/remote object and peel equality:

| Tag | Annotated object | Commit | Code/disposition |
| --- | --- | --- | --- |
| v0.1.0 | 3fbd1ae2d588adbee6a45ece36bd92a32599b4cb | ccfd090c9723c65543c2b4fde71c9538fb0d46d7 | 1 consumed; aborted publication |
| v0.1.1 | 3aa5ea8417f4a50b72532a6e28a18e970336a1cb | 87cd21a5b0a952cd40d638901af8fe25a52600cd | 2 consumed; failed qualification |

Their VERSION files match their tags; legacy code authorities are unambiguous.
Expected next allocation is max(1, 2) + 1 = **3**, for target **0.1.2**.
The implemented history/next_code functions also read the actual repository and
remote tags and returned exactly `[((0, 1, 0), 1), ((0, 1, 1), 2)]`, next code 3,
using a process-local HTTPS insteadOf transport override without changing Git config.
Neither release is claimed successfully published. No tag was moved/deleted/reused.
Origin SSH inspection initially failed because ssh is absent in the current shell;
read-only `git ls-remote https://github.com/totipo-org/totipo-android.git ...` succeeded.
New app wrappers explicitly supply OpenSSH rather than depending on host PATH.

## 15. Prepare behavior

Canonical CLI is release.py prepare-release, delegating source policy to source.py;
existing workflow bundle `prepare` remains a distinct operation. From caller's main
worktree, inspect remote/local history, derive next code, reject existing target
and unrelated/staged changes, validate source pins/wiring/machinery/public signer,
then atomically replace only VERSION and VERSION_CODE and recheck. Print metadata
and changed paths. No commit/index/ref/signing/GitHub mutation. Replacement is atomic
per file; interrupted partial preparation requires review/reset before retrying.

## 16. Prepare idempotence

Exact expected unstaged metadata diff is permitted on rerun. The code is recomputed
from tag history, not incremented from the working file. Same target reports already
prepared and changes nothing further. Tests snapshot HEAD, refs, remote and index.
Abandoning/resetting an untagged preparation leaves the code available.

## 17. Check behavior

Read-only check-release accepts exact prepared unstaged metadata or clean committed
source. Require main, matching target/code, no existing target tag, coherent history,
canonical source authorities/application ID, public certificate/fingerprint and
private-material guard, release prerequisites and stable supply-chain digests.
Tests snapshot dirty source contents and refs before/after checking.

## 18. Tag behavior

Require clean main/index, matching source metadata, absent target locally/remotely,
valid source and HEAD commit, both live origin main and local origin/main equal HEAD.
Create exactly one local unsigned annotated tag, `v<VERSION>`, message
`Totipo Android <VERSION>`, with explicit --no-sign even if tag.gpgSign is enabled.
No push. Temporary fixtures verify type/message/peel and all relevant refusals.

## 19. Push behavior

Implemented push-release-tag. Require clean coherent source and history, absent
remote target, annotated local target peeling to HEAD, and both main checks.
Explicit one-ref push, --no-force, --no-follow-tags and remote mirror disabled;
no implicit main push or force update. Bare-remote tests enable push.followTags and
prove an unrelated annotated tag stays local. Existing remote target, main drift
and wrong peel reject before mutation. Ordinary Git transport errors fail closed.

## 20. Nix app architecture

On x86_64-linux expose prepare-release, check-release, tag-release and push-release-tag.
writeShellScriptBin wrappers invoke the same store-resident release.py/source.py
implementation, with explicit Python/Git/OpenSSH/JDK/coreutils PATH, JAVA_HOME and CA
certificates. No Android SDK, gh or signing secret is needed. Existing update-package-deps
app pattern is retained. Rendered wrapper shell shape passed `bash -n`; Nix syntax
and evaluation were initially reviewed statically. The final human-reported flake
check PASS qualifies the Nix wiring and checks.release-apps execution. No Makefile
frontend exists or was needed.

## 21. Actual-worktree guarantee

Source operations discover git rev-parse --show-toplevel from caller cwd and reset
shared helper ROOT/CERT/FP to that worktree. They never cd into the executable source
snapshot and reject /nix/store worktrees. Real CLI integration runs outside a temporary
worktree with the actual public certificate, source pin inventory and bare remote;
it prepares/checks/tags/pushes only that fixture. Nix checks.release-apps executes
all packaged help entry points and invokes all commands in an incomplete disposable
caller repo, requiring missing-metadata rejection and no metadata creation.

## 22. Release-history tests

Temporary repositories cover legacy code1/code2 -> next3, new-format tags, duplicate
codes, VERSION malformed/mismatch, lightweight tags, tree-peeling tags, missing or
ambiguous legacy authority, invalid new metadata without fallback, malformed tag
syntax, stale/mismatched local/remote tags and overflow. No network is required.

## 23. Supply-chain regressions

JVM synthetic inventories vary VERSION_CODE 2/3 without changing protected script
hash, and change plugin/dependency/build-security/repository inputs to distinguish
hashes. Python exercises the shared reviewed-inventory checker: synthetic metadata
changes pass; every protected-input mutation fails. No test mutates the real repository.

## 24. Release-tool tests

Latest suite: 42 tests PASS, including actual CLI/public identity integration.
Earlier runs: 38 PASS; 39 with one fixture failure; 41 PASS; 42 PASS.
The fixture failure followed deliberately enabling follow-tags: fixture cleanup
then accidentally re-pushed its deleted test tag while pushing main. Fixed the
fixture's main-only synchronization to disable follow-tags; production push already
used explicit --no-follow-tags. No full gate ran during diagnosis.
Publication tests retain 404/create, draft-capable retrieval, hosted verification,
final-publish ordering, existing draft/published refusal and HTTP/network/malformed
fail-closed coverage. Real public identity integration is explicitly skipped in
filtered Android package sources where signing/workflow/evaluator inputs are absent;
other deterministic fixtures remain executable there.

## 25. No duplicated authority

Current numeric release value exists only in VERSION_CODE. Gradle reads it;
release source tools read it. Literal Gradle parsing is confined to historical tag
inspection. Constants in tests are synthetic cases, and 2100000000 is a range bound.
VERSION remains the sole current versionName authority. Signed provenance metadata
continues to come from the qualified unsigned APK, not current-version constants.

## 26. Production/dependency invariance

No app runtime/resources/manifest changes. Java remains released 0.2.0, protocol
v1/r19. applicationId, SDK/AGP/JDK/Gradle, dependencies, locks, verification metadata,
flake.lock, package-deps.json, public production identity, normal release workflow
and signed verifier/payload logic are unchanged. No Java-operation guidance change
is implicated. No production key is accessed.

## 27. Qualification counts

| Gate | Invocations/result |
| --- | --- |
| Baseline ordinary full | 0; packaging milestone begins with required focused RED reproduction |
| Focused JVM Gradle | 3: initial expected RED; two corrected PASS runs |
| Focused metadata Gradle | 2 PASS: initial case, then durable synthetic VERSION case |
| Python release suites | 4: PASS, fixture RED, PASS, final 42 PASS |
| Final normal full | 1 PASS; 2m50s, 90 tasks (26 executed / 64 up-to-date) |
| Strict offline clean | 1 PASS; 2m43s, 92/92 tasks executed |
| External artifact set | 1 PASS set: exactly 3 external commands (wrapper/debug/release) |
| Embedded Gradle APK verifier | 2 PASS executions, one per normal/strict full gate |
| Offline runtime/Maven check | 1 PASS; 2 tasks executed |
| Disposable signing smoke | 1 PASS, including six expected negative rejections |
| Physical harness/device installs | 0; not required |
| Human Nix | 1 final routine `nix flake check path:.` PASS, human-reported |
| Agent Nix / actual release mutation | 0 / 0 |

No repeated full gates or invalidated final passes. Both final full gates contain
338 JVM tests with zero failures/errors/skips. Signing-smoke internal verifier
calls are separate from the three-command external artifact set: qualified unsigned
validation, signed/unsigned boundary checks, payload proof and six negative cases. Focused logs are under
`/tmp/totipo-release-milestone/`; these are local execution evidence, not Nix inputs.

## 28. Final normal gate

PASS, one invocation after input stability:

```sh
./gradlew check :app:assembleDebug :app:assembleRelease
```

2m50s, 90 actionable tasks: 26 executed, 64 up-to-date. 338 JVM tests,
zero failures/errors/skips. Lint, debug/release Maven boundaries, strict locked
build resolution, embedded release APK boundary, both assemblies passed.
Log: `/tmp/totipo-release-milestone/final-normal.log`.

## 29. Strict-clean gate

PASS, one invocation after normal PASS and no input edits:

```sh
./gradlew --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug :app:assembleRelease
```

2m43s; 92/92 tasks executed. 338 JVM tests, zero failures/errors/skips.
Lint, both Maven boundaries, build verification, embedded APK boundary and assemblies
passed. Log: `/tmp/totipo-release-milestone/final-strict.log`.

## 30. Artifact verification

PASS, exactly one external final set against strict outputs:

```sh
python3 tools/verify-wrapper.py
python3 tools/verify-apk.py app/build/outputs/apk/debug/app-debug.apk --debug-probe
python3 tools/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk --unsigned --no-debug-probe
./gradlew --offline :app:dependencies --configuration releaseRuntimeClasspath :app:verifyMavenBoundary
```

Actual pinned-aapt2 metadata for both APKs: applicationId org.totipo.android,
versionName **0.1.1**, versionCode **2**. Debug SHA-256:
`e756cab3ce7164ed89b21c61563ae7d845c0e24d7c8a9cb7cf857e9c33c370e7`.
Unsigned release SHA-256:
`0fe05721e1c2d26c1b38bd06700becfad71d90096709aa4a3ae6261687bef2d6`.
Runtime graph remains NIO/core 0.2.0 and BC 1.86, with strict constraints and no
unreviewed external production modules. Both APK manifest/permission/service/
backup/ingress/branding/DEX/debug-release boundaries passed existing verifiers.
Logs: verify-wrapper.log, verify-debug.log, verify-release.log and
runtime-boundary.log under `/tmp/totipo-release-milestone/`.

## 31. Disposable signing smoke

PASS, one invocation:

```sh
bash tools/release/signing-smoke.sh app/build/outputs/apk/release/app-release-unsigned.apk
```

The unchanged provenance builder extracts 0.1.1/code2 from the same final unsigned
APK verified above; the signed verifier binds metadata to that provenance and the
unchanged unsigned hash. One TEST signer, v2/v3 enabled, v1/v4 disabled, alignment,
release boundaries and exact ZIP payload comparison passed. All six negative cases
rejected. Original unsigned APK and provenance hashes stayed unchanged; temporary
private TEST keys were removed before repository verification resumed, and the
whole temporary directory was removed on exit. No production key used.
Log: `/tmp/totipo-release-milestone/signing-smoke.log`.

## 32. Physical-device disposition

NOT REQUIRED: no product/runtime behavior changed. No installation or physical
harness run. Release/build boundaries are exercised by artifact/signing evidence.

## 33. Input freeze and human Nix

Production source stable: no runtime changes; build metadata migration finalized.
JVM tests stable: supply-chain boundary test and reviewed pin updates finalized.
Build/package configuration stable: VERSION=0.1.1, VERSION_CODE=2, app parser,
package allowlist, explicit test Git input, app/check wiring finalized.
Qualification harness/tooling stable: release source/history tools, normal
publication tests and metadata harness finalized; all effective tools inputs freeze.
Final normal/strict/artifact/runtime/signing evidence above is PASS.
All four freeze statements confirmed against a 170-path inventory (including absence
of the currently nonexistent allowlisted app/buildscript-gradle.lockfile).
Inventory: `/tmp/totipo-release-milestone/frozen-inputs.json`; canonical JSON digest
`88be401c8189c9638ea4d4da989be15413df8f7a5e0d3c7d31e31b70e3561c9d`.
No effective-input edits occurred after normal or strict qualification. The human
reported **PASS** for `nix flake check path:.` after freeze. All 170 frozen paths
and the selected-source inventory were rechecked afterward and remain unchanged.
The package-inclusive Android check and release-app wrapper check are qualified
by that human result; no detailed human build log was supplied. No additional
human app smoke is currently needed: checks.release-apps exercises installed wrapper
entry points and caller rejection, and successful operations passed real CLI fixtures.
Human command is exactly `nix flake check path:.`; agent does not run Nix.
README.md, AGENTS.md and this review report are excluded from Android source and
are not captured by the app's tools-only executable snapshot or wrapper check;
report-only result updates do not invalidate qualification. release/README.md is
also outside package source; no excluded report is consumed by a check.

## 34. Intended next commands after commit/qualification

```sh
nix run path:.#prepare-release -- 0.1.2
# Review diff; commit normally; push main normally; wait for CI.
nix run path:.#check-release -- 0.1.2
nix run path:.#tag-release -- 0.1.2
nix run path:.#push-release-tag -- 0.1.2
# Run normal protected Release workflow deliberately, with reviewed source SHA.
```

The first command should yield VERSION=0.1.2 and VERSION_CODE=3, still unstaged.
None of these real-repository operations is performed by this milestone.

## 35. Remote release disposition

Only read-only origin/HTTPS/public Actions queries occurred. No real push/tag change,
workflow dispatch, GitHub release/draft mutation, production secret access or signing.
No claim of authenticated draft absence or successful publication. Historical failed
release dispositions above follow the task/evidence, not public release success.

## 36. Final Git state and changed files

All milestone qualification is complete, including the final human-reported Nix
PASS. HEAD is still
main at 87cd21a5b0a952cd40d638901af8fe25a52600cd. The index is empty of changes;
all work remains unstaged/uncommitted. VERSION=0.1.1 and VERSION_CODE=2. Changed inputs: root code file,
app build script, JVM guard, package/flake, release.py, new source.py and source/
metadata/publication tests, existing publication assertions. Documentation: README,
AGENTS, release README and this report. Three recovery files are deleted; historical
recovery report retained. No real 0.1.2 preparation, staging or commit occurs.


Final post-human-gate `git status --short` (unchanged):

```text
 D .github/workflows/recover-release.yml
 M AGENTS.md
 M README.md
 M app/build.gradle.kts
 M app/src/test/java/org/totipo/android/OtpAuthSupplyChainGuardTest.java
 M flake.nix
 M package.nix
 M release/README.md
 D tools/release/recover.py
 M tools/release/release.py
 D tools/release/test_recovery.py
 M tools/release/test_release.py
?? VERSION_CODE
?? review/ANDROID_RELEASE_PREPARATION_AUTOMATION_REPORT.md
?? tools/release/source.py
?? tools/release/test-build-metadata.py
?? tools/release/test_publication.py
?? tools/release/test_source.py
```

`git diff --check` and local documentation-link checks pass. Existing release tag
object/peel IDs remain exactly those in section 14. Historical recovery report and
normal release workflow have no diff. No effective input changed for report updates.

Only this excluded report was edited after the human Nix PASS to record the result.
No frozen Gradle/package/app/check input changed, so no gate was invalidated.
Human Nix invocations: one; agent Nix invocations: zero. The milestone is complete;
real 0.1.2 preparation remains the human’s next operation after committing this work.
