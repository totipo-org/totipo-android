# Android release publication and verified-artifact recovery

Status: implementation, focused qualification, final normal/strict Gradle, artifact
checks and disposable signing smoke PASS. Human `nix flake check path:.` PASS,
reported by the user after input freeze. All frozen inputs rechecked unchanged.
Everything remains unstaged/uncommitted. No remote mutation, production signing,
recovery dispatch or publication performed.

## 1. Starting HEAD/state

Read AGENTS.md first. Clean `main`, HEAD
`ccfd090c9723c65543c2b4fde71c9538fb0d46d7`, VERSION `0.1.0`.
Prerequisites committed: protected machinery d5fe0b0, release-version provenance
53f1497, permanent public signing identity 7ed065b. Remote main independently
confirmed through public REST API at the same HEAD. Local origin uses SSH, but
this environment lacks ssh and gh; no remote configuration was changed.
Read README, package.nix, flake.nix, CI, release workflow/tools/docs and prior
release qualification reports. Used the provided pinned JDK/SDK/Gradle environment.

## 2. Classification

PACKAGING_RELEASE and TEST_OR_QUALIFICATION_TOOLING. No product behavior,
Android source/tests/build configuration, dependency or signing-identity change.

## 3. Real 0.1.0 failure

Original normal release run
[38097468827](https://github.com/totipo-org/totipo-android/actions/runs/38097468827)
was workflow_dispatch on main at the starting HEAD, first attempt, failure.
Public job metadata confirms qualify/sign/verify success and publish failure.
User-supplied traceback identifies release.py:224 after successful verification:
`gh api repos/totipo-org/totipo-android/releases/tags/v0.1.0`, HTTP 404,
CalledProcessError from check_output(check=True).

Correlation evidence only, never source constants:

- Unsigned APK SHA-256: `3b7da80606a0a0bc0454a9b09e7318662b8d5621a929ac7b27f9ee1d16b8ed40`.
- Signed APK SHA-256: `9021055164418c9d3e3e0a87296d007f856364da9b031d602a2bd4079ef6895f`.

## 4. Exact root cause and corrected premise

The actual committed code first listed releases, then called
`gh release create --verify-tag --draft` with assets, then queried the tag endpoint.
The traceback proves the create command had returned successfully before failure.
Thus the observed bug is **post-creation draft retrieval through a published-only
endpoint**, not a pre-creation absence check throwing before draft creation.
The user supplied the full traceback after this discrepancy was raised.
A deterministic pre-fix regression reproduced list -> successful draft creation ->
tag HTTP 404 -> CalledProcessError. The pre-fix 19-test suite failed exactly there;
the original 18-test baseline passed. No live mutation was used.

GitHub documents [release-by-tag](https://docs.github.com/en/rest/releases/releases#get-a-release-by-tag-name)
as retrieving a published release. Initial published absence and retrieval of a
new draft are distinct operations. Swallowing the post-creation 404 would not fix
this bug and must not authorize another creation.

## 5. Why HTTP 404 is expected absence

Before creation, a valid HTTP 404 on the published-tag lookup means no published
release was found. It says nothing about drafts. GitHub's
[list endpoint](https://docs.github.com/en/rest/releases/releases#list-releases)
includes drafts for push-authorized callers; a fully paginated authenticated list
is additionally mandatory before creation. Public API 404 independently observed
for v0.1.0. User checked signed-in Releases and reported one tag, no releases.
Draft absence remains a runtime authenticated check, not an assertion derived
from the public page or the supplied traceback.

## 6. HTTP-status discrimination

One explicit `lookup_release()` uses documented
[`gh api --include`](https://cli.github.com/manual/gh_api), GET, captured stdout,
check=False and a timeout. Parse the HTTP status line/header separator and JSON.
Accept only 200 with a valid release record or 404 with CLI exit 1 and a valid
error body. Do not match human stderr. Missing status, malformed HTTP/JSON or
inconsistent process status fails. Current Ubuntu 24.04 runner image inventory
lists GitHub CLI 2.102.0; its published API implementation emits included headers
and body before returning HTTP errors. Runner gh is not newly pinned here;
unsupported/missing interfaces fail closed. No Python dependency added.

References inspected read-only:
[runner inventory](https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md),
[CLI response implementation](https://github.com/cli/cli/blob/trunk/pkg/cmd/api/api.go).

## 7. Fail-closed non-404 behavior

401, 403, 429, 500/503, malformed records/HTTP/JSON, missing gh, generic CLI exit,
network error and timeout remain fatal. Other API calls retain checked subprocess
execution. Draft-list malformed/ambiguous records and pagination errors are fatal.
No exception-swallowing or all-nonzero-means-absent path exists.

## 8. Normal publication correction

Initial published lookup plus paginated draft lookup reject any existing target.
Then retain gh draft creation with --verify-tag and the same three uploaded assets.
Read the new draft through the draft-capable list, require one valid draft record,
download assets, compare hashes, verify hosted signer/boundaries, revalidate source
and publish by release ID. Also independently compare signed/unsigned payload at
publication entry. Signing and signed-verifier implementations remain unchanged.

## 9. Normal main/source invariant

release.yml is unchanged. Default `identity()` still requires dispatch source =
GITHUB_SHA = checkout HEAD = remote main = annotated tag peel = release source,
plus VERSION, clean checkout and public identity. Only input syntax validation was
extracted without changing checks. Recovery's identity callback is available to
the shared Python implementation; the normal CLI has no recovery mode/switch.

## 10. Recovery threat model

Trust reviewed current tooling, reviewed old tagged source, pinned SDK/JDK/actions,
GitHub's same-repository authenticated run/artifact metadata and same-run transport.
Never trust an artifact label, caller-supplied URL, checksum or JSON by itself.
Cryptographic signer and original unsigned evidence are independently reverified.
Compromised reviewed source/platform/runners remain outside the existing trust model.

## 11. Current tooling vs target source

recover.py requires main dispatch, clean current-tooling checkout and matching
remote main. Target validation separately requires existing annotated local/remote
tag, identical tag object and peel to the exact old commit, commit existence,
exact target VERSION blob, and byte-identical target/current public cert/fingerprint.
Current public identity is parsed and DER-hash checked. There is no requirement
that current main equal old source. The same-run receipt binds both identities
and the exact tag object across verification/publication.

## 12. Failed-run validation

Validate run/repository/head repository, normal workflow ID and file path,
workflow_dispatch event, main branch, exact head SHA, completed failure and first
attempt. Require qualify/sign/verify success and publish failure with unambiguous
jobs. Rerun attempts intentionally unsupported pending attempt-aware policy.
REST metadata exposes these facts, but not original dispatch input values; no
version/source input correlation is claimed. Target VERSION/tag and artifact
provenance supply the mandatory binding. New validators were also applied to the
actual public run/artifact metadata successfully.

## 13. Cross-run artifact acquisition

Only recover-verify has actions read, with contents read. Query the requested run's
paginated artifacts, select exactly one matching nonexpired artifact and check its
workflow_run/repository/head identity. Retrieve by validated numeric artifact ID
through authenticated gh using the documented
[Actions-read download endpoint](https://docs.github.com/en/rest/actions/artifacts#download-an-artifact),
check recorded archive digest, exact five entry names,
no duplicates/directories/symlinks, aggregate size limit and ZIP integrity before
writing only allowlisted files. No arbitrary archive path extraction or artifact
URL input. Download/API failures fail closed. No write authority here.

## 14. Exact verified artifact

Actual artifact name: `verified-release`, ID `11686402732`, unexpired at inspection,
expires `2026-10-18T00:16:47Z`, archive digest
`sha256:559c1ee072635d54132351b044ca4a619d0e655394b563a9034d9f3c6a1fd94f`.
Original verify needs sign; publish needs verify and downloads verified-release.
finalize() preserves signed APK/checksum, provenance and unsigned APK/checksum:
sufficient for independent publication re-verification without signing.
No original APK was downloaded/authenticated locally; byte/hash reproduction is
reserved for the deliberate recovery run. Metadata/evidence retained under
`.gradle/release-recovery-evidence/` (ignored; excluded package inputs).

## 15. Recovery re-verification

Exact existing provenance field schema (no schema-version field), version/source/
tag, app ID, versionName/versionCode, production fingerprint, unsigned hash and
checksum, unsigned metadata/boundaries, signed checksum, actual signature/signer
count/certificate, v1/v2/v3/v4 policy, signed metadata, alignment, ordinary release
boundaries and signed-vs-unsigned payload equality. No original JSON/APK rewriting.
Current and target public identities must match. APK hashes logged as evidence.
Publish repeats these checks and validates the receipt/current tooling/tag object.

## 16. No-signing proof

Recovery has no environment, ANDROID_RELEASE_* references, Android secrets,
apksigner signing, key generation, APK build or payload mutation. It calls the
existing verifier and ZIP comparator. Common publication has no signing code.
Only the separate local integration smoke uses disposable TEST keys.

## 17. Shared publication

recover.publish() invokes release.publish() with the separately checked recovery
identity callback. Absence/draft checks, creation/upload, redownload, hosted-byte/
signer verification and final publication are implemented once. Receipt is internal;
only APK, signed checksum and unchanged original provenance become release assets.

## 18. Existing release/draft policy

Any existing release or draft stops both paths. No adoption, overwrite, deletion,
automatic cleanup or draft resume. The actual traceback warrants particular
attention to draft state. If authenticated lookup discovers one, this recovery
refuses publication; resolving that condition needs a separately authorized policy.

## 19. Lookup tests

Deterministic 200/404/401/403/429/500/503, malformed HTTP/JSON/records,
inconsistent exit codes, generic process errors, missing executable and timeout.
Paginated draft detection, malformed list and ambiguous target records covered.
200 existing release and existing draft cause zero mutation calls.

## 20. Full mocked publication regression

Actual lookup abstraction receives HTTP 404, draft creation/upload succeeds,
draft list finds the created record, downloaded assets match exactly, hosted
verification succeeds and PATCH publication occurs. Tag endpoint for draft is
never used. Separate tampered-download test proves a draft is left unpublished.

## 21. Recovery negative tests

Wrong repo/head repo/workflow/source/run/event/branch/conclusion/attempt,
nonannotated tag/wrong peel/remote object, target VERSION/current-vs-target public
identity disagreement, absent/wrong/ambiguous/expired artifact or owner/digest,
archive unexpected/missing entries/digest corruption, source/version/certificate/
schema provenance mismatch, signed/unsigned/checksum/payload tampering and receipt
identity/shape errors rejected. Existing signed verifier tests cover actual signer
and scheme-response negatives. Mocked complete acquisition preserves all five
original bytes and produces the same-run receipt; recovery publication invokes
the common path only with valid receipt.

## 22. Workflow permissions and secret analysis

Manual-only workflow, shared android-release concurrency group. recover-verify:
contents read/actions read. recover-publish: contents write, no actions-read grant,
download only same-run recovery-verified (no run-id/token/URL overrides). All actions
SHA-pinned; no signing environment or secret references. Input shapes checked
before checkout and again by Python. Both checkouts use tooling github.sha,
fetch full history/tags and persist no credentials. Static tests and actionlint
pass against both release workflows.

## 23. Tag/version/signing invariance

Remote annotated v0.1.0 object
`3fbd1ae2d588adbee6a45ece36bd92a32599b4cb` peels to starting HEAD.
VERSION/versionCode/app ID/Java 0.2.0/protocol v1/r19 unchanged.
Production fingerprint unchanged:
`74fd4c313856e1773fd174fba4ecd2a12cc4fde85ae34a3d8a3e986353b01632`.
Current public certificate parsing/hash and exact target-source public blobs pass.
No tag mutation commands or release version burn.

## 24. Release documentation

release/README.md explains normal versus recovery paths, HTTP/draft distinction,
permissions, two source identities, dispatch inputs, verification/receipt contract,
first-attempt limitation, artifact retention, no-signing guarantee and stop-on-draft
policy. Historical qualification reports remain unchanged; AGENTS.md unchanged.

## 25. Qualification counts

| Category | Count/result |
| --- | --- |
| Baseline full Gradle | 0; packaging/tooling uses focused baseline |
| Focused Gradle | 0 |
| Final normal full | 1 PASS, 1m37s, 90 tasks (30 executed) |
| Strict offline clean | 1 PASS, 2m38s, 92 tasks executed |
| External artifact verifier set | 1 final set, 3 commands PASS |
| Offline runtime/Maven | 1 PASS, 908ms, 2 tasks |
| Embedded Gradle release verifier | 2 PASS: normal and strict |
| Release Python suites | 7: baseline 18 PASS, pre-fix 19 RED, post-fix 19 PASS, 31 static-test failure, 31 PASS, 33 PASS, final 33 PASS |
| Standalone pre-fix reproduction | 1 expected CalledProcessError after draft success |
| Actionlint | 3 PASS (first two optional external shell/Python lint disabled; final default invocation) |
| Disposable signing smoke | 1 PASS: 2 TEST keys, 3 signatures, 6 rejected negatives |
| Physical harness/device install | 0; not required |
| Human Nix | 1 final routine check PASS, human-reported after freeze |
| Agent Nix / dispatch / release mutation | 0 / 0 / 0 |

The interim static test incorrectly prohibited the literal job name 'sign' in
metadata validation. Corrected it to prohibit signing command invocation while
allowing checking the original sign job. No full gate repeated/invalidated.

## 26. Final normal/strict gates

PASS, once each, sequentially in pinned environment:

- Normal: `./gradlew check :app:assembleDebug :app:assembleRelease`.
- Strict: `./gradlew --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug :app:assembleRelease`.

Strict: 337 JVM tests, zero failures/errors/skips; lint, both Maven variant boundaries
and embedded release boundary verification PASS. No repeated or invalidated full
gates. Logs retained under .gradle/release-recovery-evidence; root clean removes
build/, so pre-fix/focused/normal evidence was copied there before strict clean.

## 27. Artifact/signing-smoke disposition

PASS: one final external set (wrapper, strict debug --debug-probe, strict release
--unsigned --no-debug-probe), plus offline releaseRuntimeClasspath/Maven boundary.
Runtime graph is storage-nio 0.2.0 -> core 0.2.0 -> bcprov-jdk18on 1.86 only.
Manifest permissions/services/ingress/backup, branding, DEX and debug/release
exclusions remain checked by the existing verifier. Strict outputs:

- Debug SHA-256: `dfc9537008c8efd13644600be2b815ff10991a4e4b4c459284ebef64742e14a6`.
- Unsigned release SHA-256: `43509bbc214edcb87289cdef658a5e3a48ae0b75f864d2a8037e28d4855c8a95`.

These are local qualification outputs, not the recovered original candidate.
One unchanged signing-smoke.sh integration PASS against that strict unsigned APK:
two disposable TEST keys, three signatures, six negatives rejected, original
unsigned/provenance unchanged and all temporary keys/APKs erased. Required by
existing release/README local qualification; no production key/secret used.
Signing and signed-verifier implementation files remain unchanged.

## 28. Physical-device disposition

NOT REQUIRED. No Android package/runtime behavior change; no installation or
physical harness run.

## 29. Input freeze / human Nix

All local gates PASS. Actual package filter includes app/src, gradle, tools
and enumerated build files; package.nix/flake.nix/flake.lock/package-deps.json are
evaluator/cache inputs. New recovery Python/tests are included. Workflows, release
docs and this report are excluded from effective package/check sources. Package
installCheckPhase already discovers all release tests; workflow static tests skip
when .github is absent. No source-filter/build changes.

All four freeze statements recorded before requesting human Nix:

- Production source stable: app/src/main unchanged; final normal/strict APK boundaries PASS.
- JVM tests stable: app/src/test unchanged; strict 337-test PASS.
- Build/package configuration stable: VERSION/Gradle/package/evaluator/cache/locks unchanged; final strict/offline boundary PASS.
- Qualification harness/tooling stable: final 33 Python tests, actionlint, AST/guard, artifacts and disposable smoke PASS; no more included-input edits planned.

`.gradle/release-recovery-evidence/input-freeze.json` records 163 effective input
hashes and six supplemental workflow/docs/public-identity hashes; membership and
hashes rechecked unchanged after all local gates. Optional app/buildscript lock
absence recorded. Included-input changes would require refreezing and a renewed
human Nix check according to actual consumers; none occurred after this freeze.
Only this excluded report has been edited afterward.

Human `nix flake check path:.`: **PASS**, reported by the user after input freeze.
Rechecked all 163 effective input hashes, six supplemental hashes, selected-tree
membership and recorded optional-input absence: unchanged. Main, starting HEAD,
protected product/build/dependency/signing inputs and empty index also unchanged.
Only this excluded report was edited to record the result; the human Nix evidence
remains valid. Agent Nix invocations zero; no nix build/rebuild requested.

## 30. Remote release disposition

Read-only public GitHub API/source inspection only. No dispatch, draft creation,
asset upload, publication, signing secret access, key provisioning, tag change,
commit, stage or push. Public release-by-tag 404; user reports no signed-in release.
No claim of authenticated draft absence or actual recovered-byte verification.

## 31. Exact human next step for 0.1.0

After final human Nix PASS, review, commit and push this fix and satisfy CI.
Then deliberately dispatch recover-release.yml on current main with:

- version: `0.1.0`
- source_commit: `ccfd090c9723c65543c2b4fde71c9538fb0d46d7`
- failed_run_id: `38097468827`

Do so while the original verified artifact remains available. Expect independently
verified signed SHA-256 `9021055164418c9d3e3e0a87296d007f856364da9b031d602a2bd4079ef6895f`.
If any draft/release exists, stop; this workflow does not resume it. No normal
release rerun, re-signing, VERSION bump or tag movement is needed/authorized.
This agent does not perform that future dispatch.

## 32. Final Git state

After all local gates, main/HEAD unchanged; all edits unstaged/uncommitted:
release/README.md, tools/release/release.py and test_release.py; new recover.py,
test_recovery.py, recover-release.yml and this report. Normal workflow unchanged.
No app/build/dependency/certificate files changed. git diff --cached empty;
git diff --check PASS after final report update. All milestone qualification gates
are complete, including human Nix PASS; no included inputs changed afterward.

```text
 M release/README.md
 M tools/release/release.py
 M tools/release/test_release.py
?? .github/workflows/recover-release.yml
?? review/ANDROID_RELEASE_PUBLISH_RECOVERY_FIX_REPORT.md
?? tools/release/recover.py
?? tools/release/test_recovery.py
```
