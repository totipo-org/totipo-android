# Android API 30 signature-verification regression

## Status and scope

Implementation and all host qualification, including signing smoke, PASS.
The final human `nix flake check` PASS is human-reported. Changes remain uncommitted. Classification: **TEST_OR_QUALIFICATION_TOOLING**
and **PACKAGING_RELEASE**. No production source, JVM test, version, dependency,
signing configuration, public signing identity, signing secret or tag changes.
No commit, push, workflow dispatch, production signing or release publication.

Started on clean `main`, HEAD `dcad06e7384287ec4e7abba03bd9a0605435f25d`;
`git status --short` was empty. VERSION is `0.2.2`, VERSION_CODE is `7`.
Read AGENTS.md, README, development/release documentation, package.nix, flake.nix,
CI and release workflows, the signed verifier, release Python tests, signing smoke,
and prior API30/release-tooling/publication-recovery qualification evidence.
Java operation guidance is inapplicable: no application operation changes.

Pinned active environment: Gradle 9.8.0, JDK 17.0.20.1+1, Android SDK platform
37.0 and Build Tools 36.0.0. SDK root:
`/nix/store/x9gvmw0icgnpqz4wbvfr8dnnnlyan8sk-androidsdk/libexec/android-sdk`.
Agent runs no Nix. Local evidence lives under ignored `.qualification/api30-signatures/`.

## Failed release evidence and limits

Read-only GitHub API inspection identified
[run 40140189434, attempt 1](https://github.com/totipo-org/totipo-android/actions/runs/40140189434),
created 2026-10-24T22:49:32Z, `workflow_dispatch`, branch main, HEAD equal to the
starting commit. Qualify and sign succeeded; verify failed at its signed acceptance
step; publish was skipped. The user supplied `Unexpected v2 signature scheme`.
The public annotation only says exit code 1; job-log download returned HTTP 403.
Qualification's authoritative Nix check and same-derivation preparation steps both
have success conclusions; this is GitHub job metadata, not an independently read
complete qualification log. Signing explicitly enabled v2/v3 and disabled v1/v4.
Successful signing alone is not independent proof of the candidate's signatures.

[Artifact metadata](https://api.github.com/repos/totipo-org/totipo-android/actions/runs/40140189434/artifacts)
reported these two nonexpired artifacts at inspection:

| Artifact | ID | Archive bytes | GitHub archive SHA-256 | Expiration UTC |
| --- | --- | --- | --- | --- |
| qualified-unsigned | 12578831887 | 2578838 | `2937b9231438dedbac2b9f14b50f55b4bdb71dc10875bc6fecbd802ea1831193` | 2026-10-31T22:56:36Z |
| signed-candidate | 12578517281 | 5159445 | `f73decc3bc99a731df95bf0a45a94a825131772f30e0b5ec6e6d4d2ebef05a11` | 2026-10-31T22:57:34Z |

Both records bind run 40140189434, repository/head repository ID 1408061027,
main and the starting SHA. Archive digests are **not APK digests**. There is no
verified-release artifact. Downloading signed-candidate through GitHub's artifact
endpoint returned HTTP 401; no GitHub CLI or authenticated download was available.
The user confirmed no local download was available. Neither artifact's contents
were read. No production APK hash, embedded certificate or signature validity is
claimed. **Signed verification against the failed release candidate: zero runs.**
The candidate's exact cause remains conditional on inspecting those bytes.

[The remote tag object](https://api.github.com/repos/totipo-org/totipo-android/git/tags/95a31e3a5990332b36ee5496ae0c949f5427b090)
is unsigned annotated tag `v0.2.2`, object
`95a31e3a5990332b36ee5496ae0c949f5427b090`, message `Totipo Android 0.2.2\n`,
peeling to `dcad06e7384287ec4e7abba03bd9a0605435f25d`, matching local Git.
Remote main also pointed there at inspection. Public release-by-tag returned 404;
that does not prove authenticated draft absence. No refs were changed or fetched.

## Reproduced root cause

The existing verifier requires v2 `true` in the default verbose output. With
manifest minSdk 30, the pinned SDK can successfully verify v3 and skip v2, reporting
v2 `false`. That result does not distinguish absent v2 from unexamined v2.
The minSdk increase therefore exposes a real acceptance bug, reproduced with real
signed APKs and disposable RSA keys. This precisely explains the reported error
**if** the failed candidate contains valid v2/v3 signatures under the pinned signer;
that final candidate-specific premise cannot be established without its bytes.

The SDK's own `apksigner verify --help` documents the manifest-derived minimum and
unbounded default maximum. [Android's tool documentation](https://developer.android.com/tools/apksigner)
also documents custom verification ranges. The reviewed
[AOSP verifier implementation](https://android.googlesource.com/platform/tools/apksig/+/6b787ef9f09f0ad09453b91e4830a32b61576127/src/main/java/com/android/apksig/ApkVerifier.java)
selects schemes according to platform range; its verification booleans describe
verification performed, rather than an exhaustive signature-block inventory.
The local pinned binary's reproduced behavior is the primary experimental evidence.

Initial reproduction used the existing local **0.2.1/versionCode 6 APK**, whose
manifest already has minSdk 30. It is not the failed 0.2.2 artifact. It was signed
with a generated TEST-only RSA key, immediately deleted. Three SDK commands all
returned zero:

```sh
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs signed.apk
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs --min-sdk-version 24 signed.apk
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs --min-sdk-version 24 --max-sdk-version 27 signed.apk
```

Complete stdout/stderr for those commands follows. This certificate is disposable,
not the production pin `74fd4c313856e1773fd174fba4ecd2a12cc4fde85ae34a3d8a3e986353b01632`.

### Manifest-derived API 30+

```text
Verifies
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): false
Verified using v3 scheme (APK Signature Scheme v3): true
Verified using v3.1 scheme (APK Signature Scheme v3.1): false
Verified using v4 scheme (APK Signature Scheme v4): false
Verified for SourceStamp: false
Number of signers: 1
Signer #1 certificate DN: CN=TEST ONLY API30 Regression
Signer #1 certificate SHA-256 digest: 2810b541acc185f37d8ad48302f4d501d77a60847052391fcc066a94e43c6ce6
Signer #1 certificate SHA-1 digest: 1292abf620220f963c645ce9c4bf038448d135f1
Signer #1 certificate MD5 digest: a199b864033337ad33b188b92cbb8d38
Signer #1 key algorithm: RSA
Signer #1 key size (bits): 2048
Signer #1 public key SHA-256 digest: 97cd3121aab236286d755df5fb7514173e0d433f274a4c8330989e7c8a1dab53
Signer #1 public key SHA-1 digest: 114d4be4d7032ef3a89521b9661f56cd043a859d
Signer #1 public key MD5 digest: 0df00f4ffa1cffb279360813e44d2044
```

### API 24+

```text
Verifies
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true
Verified using v3.1 scheme (APK Signature Scheme v3.1): false
Verified using v4 scheme (APK Signature Scheme v4): false
Verified for SourceStamp: false
Number of signers: 1
Signer #1 certificate DN: CN=TEST ONLY API30 Regression
Signer #1 certificate SHA-256 digest: 2810b541acc185f37d8ad48302f4d501d77a60847052391fcc066a94e43c6ce6
Signer #1 certificate SHA-1 digest: 1292abf620220f963c645ce9c4bf038448d135f1
Signer #1 certificate MD5 digest: a199b864033337ad33b188b92cbb8d38
Signer #1 key algorithm: RSA
Signer #1 key size (bits): 2048
Signer #1 public key SHA-256 digest: 97cd3121aab236286d755df5fb7514173e0d433f274a4c8330989e7c8a1dab53
Signer #1 public key SHA-1 digest: 114d4be4d7032ef3a89521b9661f56cd043a859d
Signer #1 public key MD5 digest: 0df00f4ffa1cffb279360813e44d2044
```

### API 24–27

```text
Verifies
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): false
Verified using v3.1 scheme (APK Signature Scheme v3.1): false
Verified using v4 scheme (APK Signature Scheme v4): false
Verified for SourceStamp: false
Number of signers: 1
Signer #1 certificate DN: CN=TEST ONLY API30 Regression
Signer #1 certificate SHA-256 digest: 2810b541acc185f37d8ad48302f4d501d77a60847052391fcc066a94e43c6ce6
Signer #1 certificate SHA-1 digest: 1292abf620220f963c645ce9c4bf038448d135f1
Signer #1 certificate MD5 digest: a199b864033337ad33b188b92cbb8d38
Signer #1 key algorithm: RSA
Signer #1 key size (bits): 2048
Signer #1 public key SHA-256 digest: 97cd3121aab236286d755df5fb7514173e0d433f274a4c8330989e7c8a1dab53
Signer #1 public key SHA-1 digest: 114d4be4d7032ef3a89521b9661f56cd043a859d
Signer #1 public key MD5 digest: 0df00f4ffa1cffb279360813e44d2044
WARNING: APK Signature Scheme v2 signer #1: APK Signature Scheme v2 signer: 0 references unknown APK signature scheme ID: 3
```

## Correction and regressions

`tools/release/verify-signed-apk.py` now performs two authoritative SDK verifications:

1. Default manifest-derived supported range, requiring verified v3.
2. Explicit API 24–27 range, requiring verified v2 independently of v3.

Both checks must succeed and each must report exactly one signer with the pinned
SHA-256 certificate. The default range is retained in full; lowering a check's
minimum is not a substitute for checking actual supported platforms. v2 `false`
in the default result is allowed only because the separate v2 check must establish
validity. Missing/ambiguous scheme or signer-count output fails closed. v1/v4
remain required `false` in both outputs. Existing provenance/schema, unsigned
hash/policy, source metadata, alignment and complete ZIP payload equality checks
remain in place. No production signature-block parser was introduced.

The standard-library suite tests actual command arguments, skipped-v2 acceptance,
independent v2 signer pinning, missing/duplicate output, signer counts and scheme
failures, alongside retained provenance, metadata, payload and publication tests.
Alignment failure remains fatal after both signature checks.

`tools/release/signing-smoke.sh` creates real v2+v3, v2-only and v3-only APKs with
temporary test keys. It also independently flips a cryptographic signature byte
inside each scheme in separate copies. Its small block traversal is exclusively
fixture mutation; apksigner is the sole cryptographic verifier. The other scheme
must remain valid in its own range, and the targeted SDK check must fail with
`signature over signed-data did not verify`. Thus malformed fixtures or general payload damage do
not masquerade as coverage of an invalid but skipped signature.
Keys are deleted before repository verifiers resume and temporary state is erased
on exit. Existing six verifier negatives are preserved and four are added:
missing v2, missing v3, invalid v2 and invalid v3. Two additional direct SDK
negative checks prove cryptographic rejection. No APK is installed.

`release/README.md` documents the two checks and updated smoke coverage.

## Qualification selection and evidence

Baseline full Gradle: zero, because this milestone changes standalone release
verification/test tooling, not Android production/JVM/build inputs. Baseline Python
suite: 63 tests PASS. Focused updated suite: 64 tests PASS; frozen suite: 64 tests
PASS. `bash -n tools/release/signing-smoke.sh` and release private-material guard PASS.

An initial smoke attempt rejected stale local 0.2.1 metadata against source 0.2.2
before key creation. This is expected fail-closed source binding, not a signature
failure; log `focused-signing.log`. It required building current artifacts before
full smoke qualification. Final normal/strict/artifact gates and the renewed signing smoke passed, as
recorded below. No full Gradle gate was repeated
for this failure. No device qualification is applicable to standalone tooling.

## Release recovery recommendation

**Prefer a separately reviewed 0.2.3 release through the corrected normal workflow.**
Keep v0.2.2 and its consumed versionCode 7 unchanged. A future ordinary preparation
must validate then-current consumed history and choose the next code (8 if history
has not advanced). This task does not perform preparation or change either version.
A corrected reviewed commit, subsequent version preparation/tag, new qualification,
protected signing approval, independent verification and publication can all retain
the normal same-source/current-main/tag invariants and within-run artifact transport.
This yields new source provenance and new APK bytes, rather than retroactively
claiming the old candidate was qualified by corrected tooling.

Reusing the exact 0.2.2 unsigned and signed candidate is technically possible
**only after separately authorized recovery design, artifact acquisition and full
independent acceptance**. Evidence here is insufficient to approve reuse. It would
avoid accessing the production signing key again, but would introduce a distinct
cross-run publication trust boundary. Required guarantees for a reviewable recovery:

- Authenticate the original repository/run/attempt/workflow/head/branch and job
  outcomes; authorize this verify-failed/sign-succeeded case explicitly. Do not
  treat signed-candidate as verified-release. Bind both artifacts by immutable
  numeric IDs and validate downloaded archive digests against trusted metadata.
- Safely unpack exact expected entries with duplicate/path/symlink/size checks.
  Require the unsigned APK, checksum and original provenance from both bundles to
  agree byte-for-byte. Bind their actual whole-file hashes and original Nix
  derivation/output to source/tag/version/code. Archive digest or signer alone
  cannot establish source provenance or qualification.
- Validate the unchanged annotated tag object and peel, source VERSION 0.2.2/code 7,
  original workflow and public certificate. Use old source metadata for artifact
  expectations and separately identified, qualified corrected tooling. Preserve
  original provenance bytes; never relabel a rebuilt APK as the original artifact.
- Run default and forced-range verbose/certificate checks on the candidate,
  requiring the sole production pin in both, plus independent unsigned policy,
  signed source metadata/SDK, alignment, exact payload equivalence and input
  immutability. Record complete output and whole-file signed/unsigned hashes.
- Separate read-only verification from contents-write publication, bind an immutable
  receipt containing source and tooling revisions, run/attempt/artifact identities,
  tag object, hashes and verification evidence. Recheck at every handoff. Use no
  signing secret. Revalidate authenticated release/draft absence and tag identity,
  create a draft for the existing tag, upload exactly accepted assets, redownload
  and compare whole-file hashes before deliberately publishing. A preexisting
  release/draft must fail closed and require separate inspection.

Current normal `source.identity()` requires dispatch SHA = checkout HEAD = reviewed
source = tag peel = current remote main and a clean tree. Committing this fix makes
main differ from the old tag. Rerunning old jobs uses the old verifier; dispatching
corrected main for 0.2.2 fails source/tag equality. Patching old checkout files or
removing identity checks is not an authorized recovery procedure. The historical
publication-recovery report addressed a different failure with successful verify
and a verified-release artifact; it does not authorize this verify-failed candidate.
No recovery machinery/workflow or bypass was implemented here.

Risks of recovery include artifact expiry (seven-day retention), unverified artifact
contents, conflating archive/APK hashes, mixing old source with new tooling,
weaker cross-run custody and accidental publication of a merely signed candidate.
Once artifacts expire, exact reuse is unavailable unless authenticated retained
copies exist; a local rebuild is not proof of original byte identity. With no
candidate bytes available here, 0.2.3 has the clearest preserved workflow guarantees.

## Input freeze, execution counts and final Git state

All intended files are Git-known, with the report intent-added only. Changed paths:

- tools/release/verify-signed-apk.py
- tools/release/test_release.py
- tools/release/signing-smoke.sh
- release/README.md
- review/ANDROID_API30_SIGNATURE_VERIFICATION_REPORT.md

`package.nix` still selects all tools/ files, including the modified smoke harness;
its Android source filter excludes release/README.md and review/. The separate
flake release-tooling fileset selects tools/release/, tools/verify-apk.py,
root/app Gradle scripts, release/signing/ and the release workflow. Thus the tool
changes invalidate Nix and require the final human check. Gradle does not consume
these release tools: tooling-only changes do not invalidate Gradle outputs after
host qualification. Report-only and release README edits do not invalidate either
check under these actual consumers. No build/package/evaluator configuration changed.

Final host gates:

| Gate | Result |
| --- | --- |
| `gradle check :app:assembleDebug :app:assembleRelease` | PASS, 1m39s, 89 tasks: 29 executed / 60 up-to-date |
| `gradle --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug :app:assembleRelease` | PASS, 3m41s, 91 tasks executed; 462 tests / 43 suites, zero failures/errors/skips |
| Debug external APK policy, `--debug-probe` | PASS; SHA-256 `59750ee4107c5b1096ed413d6b3564c15f3d24b30506df777a9d79588a8dcc81` |
| Release external APK policy, `--unsigned --no-debug-probe` | PASS; SHA-256 `097f5745229f4336b8dd2bab7d1ebf195037282c5857d46ebd592b3115959272` |
| Offline releaseRuntimeClasspath and verifyMavenBoundary | PASS, 1s; NIO/core 0.2.0 and BC 1.86 only |

Both decoded final manifests report org.totipo.android, 0.2.2/code 7, minSdk 30,
compile/target SDK 37. Full APK policy verifies permissions/components/exports/
intent filters, backup/variant flags, branding and release/debug exclusions.
Decoded manifests are retained locally. A manual manifest-export diagnostic first
used nonexistent `cmdline-tools/latest`; it was corrected to the existing policy
helper's pinned `cmdline-tools/22.0`. No gate failed or input changed for that
path error. Existing experimental AAPT2, deprecated Java API and Gradle 10
compatibility warnings remain visible; there were no qualification errors.

No later production/JVM/build edits occurred, and no final normal/strict gate was
invalidated or repeated. Logs: `final-normal.log`, `strict-clean.log`,
`debug-verifier.log`, `release-verifier.log`, `dependencies.log`, `final-tests.log`.

The first complete signing-smoke run (`final-signing.log`) accepted the valid
strict-built APK and rejected both missing-scheme APKs, but stopped at the new
v2-corruption diagnostic assertion. The assertion expected `signature did not
verify`; Build Tools 36.0.0 actually emits `signature over signed-data did not
verify`. Focused direct SDK checks on separately corrupted copies of the retained
reproduction fixture returned exit 1 for both schemes, with the specific scheme's
RSA signature failure. Logs: `corrupt-v2-diagnostic.log` and
`corrupt-v3-diagnostic.log`. The harness now matches the exact scheme-specific
failure and prints the native diagnostic before asserting. The repeated smoke
is required to qualify this harness correction. Only tools/release/signing-smoke.sh
changed after the initial input inventory: no Gradle or APK rebuild is required by
the invalidation matrix, no Nix pass existed to invalidate, and the final included
inputs were refrozen after the renewed smoke. This is a test-expectation error,
not a signature-verifier acceptance failure.

Renewed signing smoke: **PASS**, `final-signing-renewed.log`. The valid strict-built
0.2.2 test-signed APK passed full pinned-single-signer, both signature ranges,
provenance, source metadata, independent unsigned policy, alignment and payload
acceptance. All ten verifier negatives and both direct SDK corruption negatives
passed. Original unsigned SHA-256 and provenance were unchanged; keys and temporary
fixture directory were deleted. The test-signed APK SHA-256 was
`1e885f1b46a465afbb6b8b74cc1cadc5252a1d8b9e838ddadf24ef265f64f2cd`;
this is disposable qualification output, never a production release candidate.
The v2-only API range emits the SDK's warning about unknown referenced scheme ID 3;
it exits zero for valid v2 and rejects cryptographically invalid v2. The pinned SDK
remains authoritative; no warnings-as-errors policy was added or removed.

Complete scheme/certificate output from the renewed smoke against the current
strict-built APK follows (first default, then `--min-sdk-version 24`):

```text
Verifies
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): false
Verified using v3 scheme (APK Signature Scheme v3): true
Verified using v3.1 scheme (APK Signature Scheme v3.1): false
Verified using v4 scheme (APK Signature Scheme v4): false
Verified for SourceStamp: false
Number of signers: 1
Signer #1 certificate DN: CN=TEST ONLY Disposable Totipo Qualification
Signer #1 certificate SHA-256 digest: feeeee7b51a13f28ecbd04dd902da406046d3efb91fce3b19b5585044adfec52
Signer #1 certificate SHA-1 digest: 4c6a4368f34bc6cbf11916711cdd4be42b0a7954
Signer #1 certificate MD5 digest: af87b2612d60d405a16009be9e70675c
Signer #1 key algorithm: RSA
Signer #1 key size (bits): 2048
Signer #1 public key SHA-256 digest: 608d01b337ff9da74731ae825de6caf6ed5d7253e831d57e21e4fa0c37789067
Signer #1 public key SHA-1 digest: e753304869a910665141b19414e5d6b4af5b8158
Signer #1 public key MD5 digest: ad4a66d8156bfedc239a0ad1dad4db37
Verifies
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true
Verified using v3.1 scheme (APK Signature Scheme v3.1): false
Verified using v4 scheme (APK Signature Scheme v4): false
Verified for SourceStamp: false
Number of signers: 1
Signer #1 certificate DN: CN=TEST ONLY Disposable Totipo Qualification
Signer #1 certificate SHA-256 digest: feeeee7b51a13f28ecbd04dd902da406046d3efb91fce3b19b5585044adfec52
Signer #1 certificate SHA-1 digest: 4c6a4368f34bc6cbf11916711cdd4be42b0a7954
Signer #1 certificate MD5 digest: af87b2612d60d405a16009be9e70675c
Signer #1 key algorithm: RSA
Signer #1 key size (bits): 2048
Signer #1 public key SHA-256 digest: 608d01b337ff9da74731ae825de6caf6ed5d7253e831d57e21e4fa0c37789067
Signer #1 public key SHA-1 digest: e753304869a910665141b19414e5d6b4af5b8158
Signer #1 public key MD5 digest: ad4a66d8156bfedc239a0ad1dad4db37
```

Final freeze statements, after all host qualification:

- **Production source stable.** No production source/resources/manifest changes;
  both final artifact policies PASS on strict-clean bytes, source 0.2.2/code 7.
- **JVM tests stable.** No JVM test edits; strict-clean 462 tests / 43 suites PASS.
- **Build/package configuration stable.** No Gradle, dependency/lock/cache, SDK,
  Nix, workflow or signing configuration changes; full gates/boundary check PASS.
- **Qualification harness/tooling stable.** Python suite 64 PASS; corrected signing
  smoke PASS including missing/invalid schemes. The sole post-inventory harness
  edit is accounted for above and included in the renewed final inventory.

The conservative frozen inventory hashes 284 Git-known files, excluding only this
report and release/README.md (both proven outside the actual checks). Final local
`frozen-inputs.json` SHA-256:
`7810ff84ee3bddb25e3bb805b3a518db9fd4bfbbe62ab1af6181a30887532a32`.
It supersedes the initial inventory; no Nix result preceded that correction.
No further included-input edits are planned. Later report-only human-result updates
do not invalidate qualification under the inspected filters/filesets.

| Execution category | Count / evidence |
| --- | --- |
| Baseline full Gradle | 0; standalone release-tooling scope |
| Focused test/compile Gradle | 0; affected checks are standalone Python/SDK |
| Final normal full Gradle | 1 PASS |
| Strict offline clean Gradle | 1 PASS |
| External final APK verifiers | 1 set, 2 commands PASS |
| Embedded Gradle full APK verifiers | 0; unchanged check wiring |
| Offline runtime graph / Maven boundary | 1 PASS |
| Standard-library release suite | 3 invocations: baseline 63, focused 64, final 64; all PASS |
| Disposable reproduction | 1 test signing; default, API24+ and API24–27 SDK verifies PASS |
| Focused corruption diagnostic | 2 direct SDK verifications, expected cryptographic failures |
| Signing-smoke harness | 3 attempts: stale-metadata rejection before signing; diagnostic-text assertion failure; renewed complete PASS |
| Signed verifier calls inside smoke | 17: 6 in interrupted run, 11 in complete run (positive + ten negatives) |
| Nested unsigned-policy calls inside smoke | 14; rejected/positive signed cases still retain independent unsigned policy |
| Physical/device harness/install | 0; standalone release tooling |
| Failed production candidate signed verification | 0; artifact download inaccessible |
| Agent Nix | 0, per policy |
| Human Nix | 1 final `nix flake check` PASS, human-reported after freeze |

Pre-human Git evidence: branch `main`, HEAD remains
`dcad06e7384287ec4e7abba03bd9a0605435f25d`.
`git status --short`:

```text
 M release/README.md
 A review/ANDROID_API30_SIGNATURE_VERIFICATION_REPORT.md
 M tools/release/signing-smoke.sh
 M tools/release/test_release.py
 M tools/release/verify-signed-apk.py
```

`git diff --name-only` lists exactly those five paths. The report's ` A` is
intent-to-add, not staged content. `git diff --cached --name-only` is empty and
`git diff --cached --quiet` passes. `git diff --check` and changed-document local
link-target validation PASS. Ignored APKs/cache/logs/test key state are outside
Git/Nix source membership. Implementation and required qualification are complete. Candidate-specific
confirmation remains unavailable as described above and must not be inferred
from disposable fixture success. No release publication is authorized or performed.

## Human qualification completion

After the final input freeze, the user reported: “nix flake check passed”.
Record one final routine human Nix invocation, PASS, human-reported. No Nix log
or task/cache breakdown was supplied; the agent did not run Nix or independently
observe its execution.

After that report, all 284 frozen files were rehashed and matched the final
inventory, whose SHA-256 remains
`7810ff84ee3bddb25e3bb805b3a518db9fd4bfbbe62ab1af6181a30887532a32`.
Only this excluded review report was updated to record completion; the inspected
Android source filter and release-tooling fileset do not consume it, so no Gradle,
artifact, signing-smoke or Nix pass is invalidated. Final documentation validation
and `git diff --check` PASS. Branch, HEAD, five changed paths, report intent-to-add
and absence of staged content remain as recorded above. All changes are left
uncommitted for review. The production candidate access limitation and recommended
0.2.3 recovery path remain unchanged.
