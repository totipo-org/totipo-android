# Android release version provenance correction

Status: implementation, focused tests, final Gradle/artifact gates and disposable
signing smoke PASS. Human `nix flake check path:.` PASS, reported by the user
after input freeze; all frozen inputs rechecked unchanged.
Everything remains unstaged/uncommitted. All required qualification gates are complete.

## 1. Starting HEAD/state

Clean main, HEAD `d5fe0b0b7742e6e16d77549b909432daa394a393`,
`Add protected Android signed-release machinery`. Initial `git status --short`
was empty. AGENTS.md was read before other repository inspection/editing.
The committed machinery report records human Nix PASS and intentionally absent
production identity/secrets. Locally release/signing contains only README.md;
public production certificate/fingerprint are absent. No production identity or
secrets were generated/configured. Current GitHub secret/environment inventory
could not be independently queried because gh is unavailable in this environment;
absence remotely relies on the supplied starting state and committed milestone evidence.
Java runtime remains 0.2.0, protocol v1/r19.

## 2. Classification

PACKAGING_RELEASE and TEST_OR_QUALIFICATION_TOOLING. No BUILD_DEPENDENCY:
package.nix, dependency/toolchain/cache/lock/Gradle inputs are unchanged. Existing
package installCheckPhase already discovers the release Python tests.

## 3. Original duplicated versionCode expectation

At starting HEAD, tools/release/verify-signed-apk.py:30 in verify() matched
`package: name='org.totipo.android' versionCode='1' versionName='` plus the
separately supplied --version string. release.py prepare() also matched code 1,
constructed provenance with version_code=1 and version_name=args.version;
bundle_check() required version_code == 1 and version_name == args.version.

Pre-edit focused suite: 12 tests PASS. A deterministic SDK-response fixture
supplied valid signature/schemes/fingerprint, pinned application ID, versionName
1.0.0, and versionCode 42 to the original verify():
`PRE-FIX RED: otherwise valid versionCode 42 rejected: Application/version mismatch`.
No production metadata was bumped to reproduce this.

## 4. Release-process hazard

Deliberately increasing app/build.gradle.kts versionCode would still fail these
independent literals. Operators previously needed additional verifier/qualification
edits for every release increment. The duplicated assumptions could drift from
the qualified APK. Removed that second manual maintenance responsibility.

## 5. Metadata authority model

Source/build -> qualified unsigned APK -> observed aapt2 metadata and unsigned
SHA-256 -> qualification provenance -> signed candidate must match provenance.
JSON is evidence carried within the trusted same-run workflow artifact chain,
not a self-authenticating attestation. A supplied path alone is insufficient:
validate schema, pinned identity/certificate, unsigned hash and unsigned metadata.
The signed APK supplies actual metadata only, never expected metadata.

## 6. Application ID policy

APPLICATION_ID = org.totipo.android is independently pinned in release.py.
apk_metadata() enforces it for both unsigned and signed APKs, and schema validation
enforces it for provenance. Agreement between two artifacts on another application
ID cannot bypass this pin. Changing app identity remains an explicit code review.

## 7. versionName authority

build.gradle.kts reads VERSION into rootProject.version; app/build.gradle.kts uses
rootProject.version.toString() for versionName. This is intentional source policy.
qualified_provenance() checks the observed unsigned versionName against that source
version once. It records the observed value. Signed verification never reads VERSION
or compares against an independently reconstructed mutable versionName.

## 8. versionCode authority

app/build.gradle.kts remains canonical, unchanged at 1. Qualification observes
whatever deliberate source value was built. No release-tool authority pins code 1.
Range lower bound 1 is a validation rule, not a current-release expectation.

## 9. Provenance schema/validation

Retain existing qualification field names and format, without introducing a second
format or schema-version field (none existed). Exactly these fields are required:
version, source_commit, tag, application_id, version_name, version_code,
unsigned_sha256, nix_derivation, nix_output, certificate_sha256, java_runtime,
protocol. version_code must be a true JSON integer, excluding boolean/floating point,
in 1..2100000000. All remaining fields are strings. versionName is nonempty and
contains no control characters; version/source/tag shapes, lowercase 64-digit hashes,
canonical app identity, Nix store path shapes/derivation suffix, runtime/protocol pins
are validated. Missing/unknown/duplicate keys fail closed. The earlier parser used
plain json.loads and accepted duplicates/unknowns; the shared deterministic parser
now explicitly rejects them. Source dispatch/tag/VERSION validation remains in identity().

Finalization no longer adds signed_sha256 to qualification provenance. Signed hash
remains in the already-existing APK checksum file. This preserves qualification JSON
byte-for-byte through publication, with no schema v2 or post-sign regeneration.
No previous production releases/provenance migration are involved.

## 10. Unsigned APK to provenance binding

prepare() still requires reviewed source/tag/public identity, identical Android
check/package derivations and qualified Nix output. qualified_provenance() invokes
the existing unsigned release security verifier, extracts metadata with pinned
Build Tools 36.0.0 aapt2, checks source versionName and writes metadata/hash.
bundle_check() validates JSON and independently rehashes, checks metadata/checksum,
and reruns unsigned security boundaries. No signed=True bypass remains.

## 11. Signed APK to provenance verification

CLI now requires --provenance, --unsigned and independent --fingerprint alongside
signed APK. It validates provenance, matches provenance certificate to independent
fingerprint, binds unsigned SHA/metadata, enforces unsigned release security boundaries (so a
signed APK cannot serve as its own unsigned oracle), verifies actual signature/schemes/certificate,
and compares signed metadata against provenance before alignment and release boundaries.
Wrong metadata reports versionCode/versionName mismatch explicitly.

## 12. Workflow propagation

Existing YAML already transports whole bundles, so no workflow edit is needed.
qualify uploads unsigned APK/checksum/provenance; sign downloads/revalidates them
before and after the unchanged secret step and uploads signed-candidate bundle.
verify finalizes, copies original provenance verbatim, retains unsigned evidence
internally and uploads verified-release. publish downloads it and revalidates
unsigned evidence/provenance/signed metadata. Release upload still lists only APK,
signed checksum and original provenance. Downloaded provenance and all public bytes
must match before publication. No provenance is regenerated after signing.

## 13. Secret-step invariance

.github/workflows/release.yml is byte-identical to starting HEAD. Exactly four
secret expressions in one step, pinned platform signing plus shell/core utilities,
no repository helpers or provenance parsing during production secret scope.
All parsing occurs before/after it. Local smoke qualifies metadata before keys
exist; while disposable keys exist, shell substitutes only the test public fingerprint
into the fixture before signing. No version metadata is rewritten. All repository
verification resumes only after both temporary keystores are erased.

## 14. Mutable constants inventory

Removed authority literals from signed verifier, prepare() metadata matcher,
provenance construction and bundle_check(). Removed --version downstream interface.
No current VERSION literal remains in release-tool authority. Synthetic fixture
versions 1.0.0/1.0.1 and versionCodes 42/43 are TEST FIXTURE data. Runtime 0.2.0 and
protocol v1/r19 are reviewed compatibility pins, not Android release version copies.
Canonical application ID remains an identity pin. Initial code 1/current dev version
in the prior machinery report are historical CURRENT RELEASE DOCUMENTATION and are
preserved. Live release README no longer instructs verifier edits on version bumps.
Production versionCode=1 remains solely its deliberately unchanged build authority.

## 15. Non-1 versionCode positive regression

PASS: test_non_one_signed_metadata_and_mismatches exercises signed.verify with
validated provenance code 42 and unsigned/signed metadata code 42, mocked external
SDK responses, valid single signer/schemes, and successful downstream tool exits.
Qualification test independently proves observed code 42 is propagated automatically.
Production versionCode remains 1.

## 16. versionCode mismatch negative

PASS: provenance/unsigned code 42, signed code 43 fails specifically with
versionCode mismatch, despite otherwise valid signer/schemes/application identity.

## 17. versionName mismatch negative

PASS: provenance/unsigned name 1.0.0, signed name 1.0.1 fails with versionName
mismatch. Qualification also rejects unsigned name differing from source VERSION.

## 18. Application ID mismatch negative

PASS: schema rejects foreign provenance application_id; real badging parser rejects
foreign APK application ID independently of provenance. Matching foreign identities
cannot satisfy the canonical pin.

## 19. Provenance/unsigned mismatch negatives

PASS: unsigned versionCode/name differing from provenance fails. SHA mismatch fails
before accepting metadata; existing mutated-bundle unsigned hash regression retained.
Missing/unknown/duplicate schema fields, malformed hashes/fingerprint, invalid source,
Nix paths/runtime/protocol and invalid versionCode types/ranges also fail closed.

## 20. Existing signature/payload negatives retained

payload.py is unchanged. Existing ZIP content/order/entry/metadata, duplicate/JAR
signature, signer/count/scheme/public-identity, private-material guard, workflow
secret isolation and remote-byte mismatch/draft regressions remain. v4-enabled
signature-output rejection added alongside existing v1/v2/v3 negatives.
Finalization still independently compares the full unsigned/signed ZIP payload;
metadata checks do not replace that proof or signer fingerprint checks.

## 21. Disposable signing smoke

PASS against the exact strict unsigned APK (SHA below). Updated smoke uses qualified_provenance() on the real
unsigned input before generating TEST-only keys. It binds a disposable public
fingerprint before signing, deletes both PKCS#12 files before repository checks,
and consumes --provenance/--unsigned with no embedded version expectations.
It retains six actual cryptographic/payload negatives and verifies both original
unsigned SHA and complete provenance SHA are unchanged across signing/verification.
Two disposable RSA-2048 keypairs and three signing operations, one smoke run.
All six negatives rejected: wrong fingerprint, unrelated signer, unsigned candidate,
tampered signed candidate, correctly signed altered payload, malformed fingerprint.
Provenance SHA unchanged throughout signing; original unsigned SHA unchanged.
Disposable signed APK SHA: `ac740385f840cf673d3ff93642019cf31a4be1147146d2bd01a3420e6a567597`.
Fake Nix paths and dev source version describe explicitly local fixture evidence,
not production qualification or a provisioned identity. Temp directory EXIT cleanup
removes private/public disposable material and candidates; nothing is installed.

## 22. Production/version/dependency invariance

VERSION, app/build.gradle.kts, all app/src production/JVM tests, application ID,
permissions/manifest, Sync/biometrics/inactivity and runtime/dependency/build inputs
are byte-identical to starting HEAD. Java 0.2.0 / v1/r19 unchanged. No permanent
key, public production identity or production secret configured.

## 23. Documentation changes

release/README.md documents pinned identity versus propagated mutable metadata,
intentional future VERSION/versionCode preparation, exact provenance validation,
immutable provenance and internal unsigned evidence transport. No verifier edit is
needed solely for version changes. Historical report evidence is preserved.

## 24. Qualification-ladder execution counts

| Category | Count/result |
| --- | --- |
| Baseline full Gradle | 0; tooling/packaging classification does not mandate ordinary product baseline; pre-fix Python baseline used |
| Focused Gradle | 0 |
| Final normal full | 1 PASS, 1m36s; 90 tasks, 13 executed |
| Strict offline clean | 1 completed PASS (2m24s; 92 executed) + 1 aborted startup attempt, no tasks |
| External artifact set | 1 final set: wrapper/debug/release, 3 commands PASS |
| Offline runtime/Maven boundary | 1 PASS, 847ms, 2 tasks |
| Embedded Gradle release verifier | 2 PASS: normal and strict; aborted startup ran no tasks |
| Release Python suite | 4 PASS runs: pre-fix 12; three post-fix 18 |
| Pre-fix RED fixture | 1 expected rejection |
| Disposable signing smoke | 1 PASS; 2 TEST-only keys, 3 signatures, 6 rejected negatives |
| Physical harness | 0 |
| Human Nix | 1 final routine check PASS, human-reported after freeze |
| Agent Nix / remote CI / release | 0 / 0 / 0 |

Strict was mistakenly launched while normal was still running. It was terminated
with SIGTERM during daemon startup before any Gradle tasks ran. This execution
mistake requires one replacement strict invocation after normal PASS, rather than
counting a concurrently started command as sequential gate evidence. Abort log
preserved. No production/test/build inputs changed during normal gate; final
Python/shell edits affect tools only, which Gradle does not consume.

## 25. Final normal gate

PASS: `./gradlew check :app:assembleDebug :app:assembleRelease`, 1m36s;
90 tasks, 13 executed/77 up-to-date. Existing lint, Maven and release APK verifier
passed. Later tools-only boundary hardening reuses the existing unsigned verifier
in standalone signed verification; tools are not Gradle consumers, so this does not
invalidate Gradle evidence. Focused suite rerun PASS before final source freeze.

## 26. Strict-clean gate

PASS: `./gradlew --offline --no-daemon --no-configuration-cache --no-build-cache
--rerun-tasks --dependency-verification=strict clean check :app:assembleDebug
:app:assembleRelease`. 2m24s; 92/92 tasks executed. 337 JVM tests, zero failures,
errors or skips. Lint, both Maven boundaries and embedded release APK verifier PASS.
No rebuild occurred afterward.

## 27. External artifact verification

PASS: wrapper; strict debug --debug-probe; strict release --unsigned
--no-debug-probe; offline releaseRuntimeClasspath and verifyMavenBoundary.
Runtime graph: storage-nio 0.2.0 -> core 0.2.0 -> bcprov-jdk18on 1.86 only.
Existing artifact verifier checks permissions, services, ingress/backup, branding,
DEX and debug/test exclusions; no boundary weakened.

Strict debug SHA-256:
`ebdf63d1aa57e32d81d4946c30944102f3462e3118d7345ca51e1c11069f14d1`.
Strict unsigned release SHA-256:
`a192a820957ce697e8a1129523e0f26ebccbf9983e3c96c6590a403d06783b3a`.
External final set contains three commands; smoke additionally invokes existing
unsigned/signed security verifiers for its positive/negative integration paths.
This is integration evidence, not intermediate APK qualification.

## 28. Physical-device disposition

Not required: no product/build/manifest behavior change. Only release host tools
changed. No device qualification run, no disposable APK installed on any phone.

## 29. Input freeze and human Nix result

All four freeze statements recorded after final focused tooling/AST/shell/guard,
normal, strict, external artifact and disposable smoke PASS:

- Production source stable: git diff HEAD -- app/src/main empty; unchanged source.
- JVM tests stable: all app/src tests unchanged; strict 337-test PASS.
- Build/package configuration stable: Gradle, package.nix, flake/cache/locks and
  VERSION unchanged; no build/dependency changes required.
- Qualification harness/tooling stable: final 18-test PASS, Python AST, shell syntax,
  guard and real signing smoke PASS; no further included-input changes planned.

build/reports/version-provenance/input-freeze.json records SHA-256 for 161 effective
source/evaluation/cache inputs plus three supplemental workflow/release-doc files.
Optional app/buildscript-gradle.lockfile is absent and recorded. Logs preserved in
build/reports/version-provenance/ (ignored/excluded). Report-only changes after this
freeze do not invalidate the gates. Human `nix flake check path:.`: **PASS**, reported by the user after input freeze.
Rechecked all 161 effective input hashes, three supplemental hashes, selected-file
membership and the absent optional input after that report: unchanged. Only this
excluded report was edited afterward, preserving the human Nix result. Agent Nix
executions remain zero.

Actual package source filter inspected:
app/src, gradle, tools and named build files included; package.nix/flake/cache affect
evaluation. Existing package hook discovers test_*.py in tools/release. release docs,
workflow and review reports are outside effective package/check inputs. Report-only
edits after freeze do not invalidate Nix. Agent must not run Nix; final human command
is exactly `nix flake check path:.`.

## 30. Remote CI/release and security review disposition

No workflow dispatched, CI run, tags/releases/pushes, GitHub environment or secret
mutations. No stage/commit. Security review: expected versions cannot come from signed
APK alone; they originate at qualified unsigned extraction. Provenance hash/metadata,
canonical application ID and independent certificate pin are checked. Payload proof
is independent and unchanged. Wrong versions fail closed. Production secret scope
unchanged; production identity remains locally absent. Editing provenance alone cannot
change actual unsigned metadata/hash, signed signature/payload or source/tag/identity
checks. Same-run artifact transport remains the original trusted qualification-origin
boundary; arbitrary supplied JSON is not proof of source provenance by itself.

## 31. Final Git state

After local final gates: main / d5fe0b0b7742e6e16d77549b909432daa394a393.
Human Nix PASS recorded; all frozen inputs unchanged. All changes unstaged/uncommitted.
Changed files: tools/release/release.py, verify-signed-apk.py, signing-smoke.sh,
test_release.py, release/README.md and this new report. package.nix and workflow
unchanged. git diff --cached empty; git diff --check PASS after local final gates.

```text
 M release/README.md
 M tools/release/release.py
 M tools/release/signing-smoke.sh
 M tools/release/test_release.py
 M tools/release/verify-signed-apk.py
?? review/ANDROID_RELEASE_VERSION_PROVENANCE_FIX_REPORT.md
```

No tag, release, push, dispatch, permanent identity or secret provisioning. Only
this excluded report changed after input freeze to record the human result.
