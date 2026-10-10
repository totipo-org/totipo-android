# Android signed-release machinery milestone

Status: implementation, local qualification and final human Nix PASS. Production
identity intentionally unprovisioned. Nothing staged, committed or published.

## 1. Starting HEAD/state

Started 2026-10-10 on clean `main`, HEAD
`cd039a87b442395fef51ccfaa238e18de2141df5`. Recorded branch, HEAD and empty
`git status --short` before editing; read AGENTS.md first. Committed prerequisites:
F08 guidance refresh cd039a8, F09 ad55118, qualification ladder ac5e789,
biometric/inactivity lock 45a7006. Current committed F09/F08 reports preserve final
unsigned packaging/Gradle/artifact and human Nix PASS evidence. Runtime remains
Java 0.2.0 / Totipo v1/r19.

## 2. Qualification classification

Classified before edits as PACKAGING_RELEASE and TEST_OR_QUALIFICATION_TOOLING.
Added BUILD_DEPENDENCY when choosing the single package.nix install-check hook
that exercises focused release-tool tests. No product behavior changes.

## 3. Existing build/Nix artifact topology

Inspected AGENTS.md, README, package.nix, flake.nix, CI, wrapper/APK verifiers,
Gradle boundaries, VERSION, package-deps.json and locks/verification metadata.
Both `checks.x86_64-linux.android` and `packages.x86_64-linux.default` reference
`androidPackage`. Its Gradle build/check and install checks assemble debug/release,
run tests/lint/strict Maven verification and verify both APK boundaries. Only
`share/totipo-android/totipo-android-<VERSION>-unsigned.apk` is installed.
No debug APK is installed. Local release output remains
`app/build/outputs/apk/release/app-release-unsigned.apk`.

The workflow executes `nix flake check --print-build-logs path:.`, then evaluates
both drvPaths and refuses a mismatch before `nix build --no-link --print-out-paths`
materializes the default package. No separate Gradle release build path exists.
The agent has not run any Nix command. Runtime derivation equality will be
confirmed by the future workflow; current source wiring is identical by inspection.

## 4. Java release-pattern lessons adopted

Read canonical totipo-org/totipo-java release.yml and publishing/release.py
read-only from GitHub; current main resolved to
`f0a028676c1801a10b2d8d2650bf9c24357d1fa1`.
Reference: https://github.com/totipo-org/totipo-java/blob/f0a028676c1801a10b2d8d2650bf9c24357d1fa1/.github/workflows/release.yml

Adopted deliberate dispatch on reviewed current main/full commit, version/source
checks, annotated tag convention, immutable action pins, protected approval,
read-only defaults, separate publication authority and remote verification.
Android requires an already-existing tag; it never adopts Java's tag-creation
operation. No Java key, Maven publication, signing plugin or Central credentials
are reused. Artifact action pins were resolved read-only from the canonical
first-party action repositories: upload ea165f8d65b6e75b540449e92b4886f43607fa02,
download d3f86a106a0bac45b974a628896c90dbdf5c8093.

## 5. Release threat model

Trust the exact reviewed release source, locked Nix SDK/JDK, SHA-pinned actions,
GitHub artifact transport within this run, protected reviewers and ephemeral
runners. Fail closed on source/tag drift, absent identity, bundle corruption,
wrong signer, unexpected schemes, changed ZIP payload and hosted-byte changes.
A signing key cannot authorize payload changes relative to the qualified source.
The public certificate pins identity independently of whichever keystore is supplied.
No claim is made to defend against a compromised trusted runner/platform or
malicious reviewed source. Normal shell cleanup cannot run after SIGKILL.

Explicit security review before freeze:

| Boundary | Review result |
| --- | --- |
| Private key tracking/survival | PASS: none tracked or present; temporary TEST keys/directories erased |
| Nix store/build outputs | PASS: keys generated only outside source after builds, erased before tooling resumes |
| Secret scope | PASS: four expressions in one step only; no Gradle/Nix/repository helper during that step |
| Sign job write authority | PASS: contents read only |
| Publish private key | PASS: none; GitHub token only |
| Qualified input immutability | PASS: strict unsigned SHA unchanged |
| Expected signer public pin | PASS machinery; actual public production identity intentionally pending |
| Schemes | PASS: explicit v1/v4 disabled, v2/v3 enabled |
| Signed ZIP payload | PASS: comparator unchanged, all local headers/content preserved |
| Hosted bytes before publication | IMPLEMENTED and mocked failure tested; no remote release executed |
| Action pins/signing actions | PASS: full immutable SHAs; no third-party signing/release action |
| Tag creation | PASS: existing annotated tag required; no create/push operation |

## 6. Workflow job/permission graph

`qualify -> sign -> verify -> publish`; serialized release concurrency.
Global contents read. Only sign references protected `android-release` and only
publish overrides contents write. Sign explicitly has contents read. No other
write permission, OIDC permission, pull_request_target, or signing/release action.
Every checkout disables persisted credentials and selects the dispatch SHA.

## 7. Source/version/tag checks

Validate version syntax/full lowercase commit before checkout. Require dispatch
on main, dispatch SHA = requested commit = checked-out HEAD, exact VERSION,
release version (not dev/SNAPSHOT), clean checkout, annotated `v<VERSION>` tag,
tag peel = source, remote tag object/peel = local tag/source, and remote main =
source. Revalidate after approval and immediately before final publication.
Moving main fails closed. No tag creation/push command exists; gh uses --verify-tag.

## 8. Qualified unsigned artifact provenance

Run the existing verifier with --unsigned --no-debug-probe against the actual
Nix output; inspect package/version with pinned aapt2. Record exact unsigned
SHA-256, application ID, versionName/code, VERSION/source/tag, Nix derivation/output,
expected certificate digest, Java 0.2.0 and v1/r19. Bundle contains only unsigned
APK, checksum and sorted deterministic JSON. Downstream rehashes original bytes.

## 9. Signer public-identity model

Future files: release/signing/android-release-cert.pem and
release/signing/android-release-cert.sha256. Exactly one PEM certificate, parsed
with pinned keytool; DER SHA must equal 64 lowercase hex fingerprint. Both files
are absent by design. No fake identity or unpinned-signer bypass. One signer only;
no key rotation/proof-of-rotation machinery.

## 10. GitHub environment/secret contract

Future environment `android-release`: required reviewer, intended release-ref
restrictions (main dispatch), prevent self-review where supported. Four secrets:
ANDROID_RELEASE_KEYSTORE_B64, ANDROID_RELEASE_KEYSTORE_PASSWORD,
ANDROID_RELEASE_KEY_ALIAS, ANDROID_RELEASE_KEY_PASSWORD. Operational keystore
PKCS#12. No environment or secret was created; protections require human setup.

## 11. Secret-scope analysis

Exactly four secret expressions, all in one signing step's env. No workflow/job
env references, provenance, logs or artifact includes private secrets. Before that
step, resolve pinned toolchain and verify the bundle. During it, no Python,
repository helper, Gradle, Nix or gh executes. After step termination, no signing
secrets are available to verification. Publish has a GitHub token but no Android key.
The signing shell selects pinned JDK java and clears Java injection-option env vars.

## 12. Signing command/scheme policy

Existing Build Tools 36.0.0 provides apksigner (reports 0.9) and zipalign; existing
JDK 17 provides keytool. Current minSdk 26 supports v2 without v1. Explicit
--v1-signing-enabled false, --v2-signing-enabled true,
--v3-signing-enabled true, --v4-signing-enabled false.
`--alignment-preserved true` explicitly preserves existing ZIP local headers.
PKCS#12 alias configured explicitly; passwords via env: mechanisms. Output is a
new path, never the qualified unsigned APK. No SDK/dependency upgrade.

## 13. Keystore cleanup

Private mktemp directory under runner temp, umask 077, chmod 600 PKCS#12,
EXIT removal trap plus INT/TERM exit handlers and immediate removal after signing.
Only the signed APK is created as persistent signing-step output. Local smoke
removes both temporary keystores before repository verifiers resume and removes
the entire temporary directory on exit. No private material enters source/Nix.

## 14. Signed APK verification

Focused verify-signed-apk.py requires successful apksigner verify --verbose
--print-certs, exactly one signer, exact expected SHA-256, v1/v4 false and v2/v3
true. Check application ID org.totipo.android, versionName and initial versionCode
1, zipalign check, and existing --no-debug-probe release security/branding/DEX
boundaries. Later versionCode increases require deliberate reviewed verifier updates.

## 15. Signed-vs-unsigned payload proof

Separate payload.py verifies identical ZIP entry set/order, no duplicate names or
JAR signing entries, CRC/compression/sizes, timestamps, comments, extra metadata,
flags/attributes/versions, raw local headers, compressed-byte hashes and every
uncompressed entry SHA-256. ZIP archive comments also match. Central-directory
file offsets/signing-block insertion are permitted. Whole-file identity is not
claimed for signing. Original unsigned SHA is separately rechecked.

## 16. Negative verifier tests

Fast standard-library suite: 12 tests covering payload mutations/order/metadata,
duplicates/JAR signature, malformed fingerprints/public identity, invalid source
inputs, private tracking, workflow static trust graph/pins/secret scope, mismatched
Nix derivations, provenance/unsigned corruption, signer/scheme output rejection,
and remote-byte mismatch leaving draft unpublished. Workflow-static test is
intentionally skipped in filtered package source where .github is absent.
Real signature negatives are described in section 26. No new Python dependency.

## 17. Disposable-key signing qualification

signing-smoke.sh generates two temporary RSA-2048 TEST-only PKCS#12 keys using
keytool, exports public DER only to temporary files, signs separate candidate
paths, deletes keystores, then executes repository verifiers. No production
identity needed and no APK installation. Strict unsigned APK is the integration input.

## 18. Publish/draft/remote verification design

Only verified signed APK, checksum and public provenance reach publish. Validate
source/provenance/hash/certificate again, reject existing release/draft, create
DRAFT for existing tag, upload three assets, redownload all three and compare SHA.
Reverify downloaded signature/certificate and ordinary release boundaries, then
revalidate source/tag and PATCH draft=false. On failure, draft remains unpublished.
Exact downloaded APK bytes preserve verify job's payload-equivalence proof.
Runner/platform gh CLI/API is outside the signing-secret step. No production path
was executed, including no dry-run remote mutation.

## 19. Private-key tracking guards

Scoped ignore rules for release/signing/*.p12, *.jks, *.keystore, *.key;
public PEM/fingerprint remain visible. Guard permits only README and the two exact
public identity files, checks tracked names and unexpected files on disk, with
negative tests. Guard runs during workflow identity and was checked locally.

## 20. Production provisioning still pending

No permanent key, certificate or fingerprint generated. No production signing,
secret installation, environment creation or production-signed installation.
Absent public identity intentionally blocks release before protected approval.

## 21. Dependency/build/product invariance

app/src/main and JVM tests unchanged. Gradle files, VERSION, app ID, minSdk,
permissions, Java runtime, dependency locks/verification metadata, package cache,
flake configuration/lock all unchanged. package.nix adds only a fast unittest hook
to existing installCheckPhase. README, .gitignore, release docs/workflow and five
tools/release files are the remaining changes. No parallel verifier replaces the
existing APK boundary verifier; signed verification invokes it.

## 22. Qualification-ladder execution counts

| Category | Count/result |
| --- | --- |
| Baseline full Gradle | 1 PASS, 2m21s; 90 tasks, 13 executed |
| Focused Gradle | 0; Python tooling scope |
| Final normal full Gradle | 1 PASS, 2s; 90 tasks, 4 executed |
| Strict offline clean | 1 PASS, 3m8s; 92/92 tasks executed, 337 tests |
| External artifact set | 1 final set: wrapper/debug/release, 3 commands PASS |
| Final offline runtime/Maven boundary | 1 PASS, 768ms, 2 tasks |
| Embedded Gradle release verifier | 3 PASS: baseline, final normal, strict |
| Focused Python suite | 6 PASS runs (6, 6, 8, 12, 12, 12 tests); last against frozen tools |
| Workflow lint | 3 PASS runs, actionlint 1.7.12 |
| Disposable signing smoke | 3 attempts: PATH failure before keys; alignment failure; final PASS; 2 focused diagnoses |
| Physical harness | 0, not required |
| Human Nix | 1 final routine check PASS, human-reported after input freeze |
| Agent Nix / remote CI / release | 0 / 0 / 0 |

No repeated full Gradle checkpoint. Subsequent smoke-harness and workflow fixes
did not change production, JVM tests, Gradle inputs or APK bytes. Under the
ladder's harness-outside-Gradle matrix, rerun the harness without rebuilding; all
changes preceded the Nix freeze. The Python test update is also outside Gradle
consumers. Failure logs and focused diagnosis are preserved alongside successes. actionlint downloaded
read-only to /tmp from its canonical release with published checksum comparison;
not added as a project/runtime dependency. Shell syntax, Python AST and
`git diff --check` pass. Logs retained outside effective source.

## 23. Final normal gate

PASS: `./gradlew check :app:assembleDebug :app:assembleRelease` (2s).
No subsequent production/test/Gradle changes.

## 24. Strict-clean gate

PASS: `./gradlew --offline --no-daemon --no-configuration-cache --no-build-cache
--rerun-tasks --dependency-verification=strict clean check :app:assembleDebug
:app:assembleRelease`. Duration 3m8s, 92/92 executed; 337 JVM tests, zero
failures/errors/skips. Existing lint, Maven and release APK boundary checks PASS.

## 25. Artifact verification

PASS: wrapper, strict debug --debug-probe, strict release --unsigned
--no-debug-probe and offline releaseRuntimeClasspath/verifyMavenBoundary.
Runtime graph is NIO 0.2.0 -> core 0.2.0 -> BC 1.86; no extra runtime dependency.

Strict debug SHA-256:
`c2748f76163ab5a8b61311497e5fcfad3c59572d4dcaf2cd65c28c74a207f3cf`.
Strict release unsigned SHA-256:
`e5a2051a280be2c36eea60069a3214959c4e4abaf76ce7fc78767a70cd125f4d`.

## 26. Disposable signing smoke results

PASS against exactly the strict unsigned APK above, without rebuilding.
Positive: one expected signer; v1/v4 false, v2/v3 true; application/version,
alignment and ordinary release boundaries; all 24 ZIP entries/local metadata and
compressed/uncompressed payloads identical; original unsigned SHA unchanged.
All six negative cases rejected: wrong fingerprint, unrelated signer, unsigned
input, tampered signed input, correctly signed changed payload, malformed fingerprint.

Final ephemeral signed APK SHA-256:
`15d3a438929f5f9e29ec3ce41ce6ecc429783bf8b8c9870f0265c0319524196c`.
This is disposable evidence, not a production identity or distributable release.
All temporary directories, keys, certificates and signed candidates were removed.

Attempt 1 failed before key generation because PATH incorrectly assumed /usr/bin
utilities in the Nix shell. Preserve managed PATH and prioritize pinned JDK.
Attempt 2 passed signature/boundaries but raw local-header comparison failed:
apksigner defaults add alignment extra-field 0xd935 to some entries (res/8c.png
was the first). A focused temporary-key diagnosis established identical central
metadata/content but changed local alignment headers. Tool help documents
--alignment-preserved default false. A second focused diagnosis with true
established all entries and raw headers identical. Added that flag to workflow
and smoke; comparator remained unchanged. Attempt 3 passed in full. Six temporary
TEST-only keypairs total across smoke/diagnosis, all erased; eight disposable
signing operations. No full Gradle rerun or intermediate APK qualification was
needed for these harness/signing-command corrections.

## 27. Physical-device disposition

Not required: no behavior/runtime/manifest changes; cryptographic, alignment and
payload checks are host-side. No device harness run or signed APK installed,
including no use of the primary phone.

## 28. Input freeze and human Nix result

All four freeze statements recorded after final tooling checks:

- Production source stable: byte-identical to starting HEAD (git diff HEAD empty).
- JVM tests stable: byte-identical to starting HEAD; strict 337-test PASS retained.
- Build/package configuration stable: only reviewed package.nix unittest hook;
  Gradle/flake/cache/locks unchanged. All gate-consumed Android inputs stable.
- Qualification harness/tooling stable: final 12-test suite, workflow lint,
  shell syntax, guard and disposable smoke PASS; no more included-input edits.

`build/reports/signed-release/input-freeze.json` hashes 161 existing effective
Android source/evaluation/cache inputs and six supplemental release/workflow/docs
inputs. The selected optional app/buildscript-gradle.lockfile is absent and
recorded as such. No Nix source or evaluator edit will follow this request.
Logs are in build/reports/signed-release/ (ignored, excluded from Nix source).

Human `nix flake check path:.`: **PASS**, reported by the user after the final
input freeze. Rechecked all 161 effective Android inputs and six supplemental
release inputs after that report: every hash matches; the absent selected file
remains absent. Only this excluded report changed afterward, so the human Nix
PASS remains valid. Agent Nix executions: zero. Actual package filter still
selects app/src, gradle, tools and named build files; tools/release files are included.
package.nix itself affects evaluation. Workflow/release docs, README, .gitignore,
AGENTS.md and review reports are excluded from effective package/check inputs.
Report-only edits after freeze do not invalidate Nix. Human must run exactly
`nix flake check path:.` after all included inputs stabilize; agent never runs Nix.

## 29. Remote CI/release disposition

No workflow dispatched. No CI run, GitHub environment/secret/release mutation,
tag creation, push, signing with production key, or production installation.
Implemented production path has only static/local evidence until future human use.

## 30. Remaining steps before first production release

Separate human provisioning milestone: generate permanent key offline, encrypted
recovery copies, export/review/commit public certificate/fingerprint, configure
protected android-release and matching private PKCS#12 secrets. Then prepare
release VERSION/versionCode/source and annotated tag, commit/review machinery,
and deliberately invoke first protected release. See release/signing/README.md.

## 31. Final Git state

Branch/HEAD remain main/cd039a87b442395fef51ccfaa238e18de2141df5.
Everything unstaged/uncommitted; git diff --cached empty, git diff --check PASS.
Final status after human Nix and frozen-input recheck:

```text
 M .gitignore
 M README.md
 M package.nix
?? .github/workflows/release.yml
?? release/
?? review/ANDROID_SIGNED_RELEASE_MACHINERY_REPORT.md
?? tools/release/
```

New release files: release/README.md, release/signing/README.md; tools:
release.py, payload.py, verify-signed-apk.py, signing-smoke.sh, test_release.py.
No stage/commit/tag/release/push. Only this excluded report was updated after
human Nix; no frozen input changed. Final documentation validation and
`git diff --check` PASS. The machinery milestone is qualified; production
provisioning and the first protected release remain separate human-controlled work.
