# Android production public signing identity provisioning

Status: public identity installed and locally qualified, ready for review/commit.
All changes remain unstaged/uncommitted. No production signing or release occurred.

## 1. Starting state and classification

Classification: **PACKAGING_RELEASE — release identity provisioning**. No Android
product behavior change, tooling implementation change or dependency upgrade.

Recorded starting commands/results:

```text
git branch --show-current: main
git rev-parse HEAD: 53f14974f30fd58d4bad3c9ece76c3b82acc8c69
git status --short:
?? android-release-cert.pem
?? android-release-cert.sha256
cat VERSION: 0.0.0-dev
```

The tracked checkout and index were clean. The human explicitly approved treating
these two supplied public inputs as the clean-checkout exception and preserving
both root copies. No existing tracked work was discarded. Starting main includes
signed-release machinery `d5fe0b0b7742e6e16d77549b909432daa394a393` and the committed
version-provenance fix at starting HEAD. Read AGENTS.md, README, signing/release
docs, package.nix, flake.nix, CI/release workflows, all relevant release helpers/tests
and prior machinery/version-provenance qualification evidence before editing.

## 2. Installed public identity and certificate details

Copied the supplied files byte-for-byte to:

- `release/signing/android-release-cert.pem`
- `release/signing/android-release-cert.sha256`

Original root copies remain unchanged. No private key or password was supplied,
requested, opened, copied or installed. The permanent private key was never handled.

| Public property | Verified value |
| --- | --- |
| Subject | CN=Totipo Android Release, O=Totipo |
| Issuer | CN=Totipo Android Release, O=Totipo |
| Serial, hexadecimal | 7f754aea2092bae3 |
| X.509 version | 3 |
| Not before, UTC | 2026-10-10T23:42:20Z |
| Not after, UTC | 2076-10-10T23:42:20Z |
| Public key | RSA, 4096 bits |
| Certificate signature algorithm | SHA256withRSA |
| JDK validation time, UTC | 2026-10-10T23:57:12.224791539Z |

Exact certificate DER SHA-256 and expected Android signer fingerprint:

```text
74fd4c313856e1773fd174fba4ecd2a12cc4fde85ae34a3d8a3e986353b01632
```

## 3. Independent certificate/fingerprint verification

Used the existing pinned Nix-provided JDK 17.0.20.1+1, with JAVA_HOME:
`/nix/store/zanji5hpfg98zl65a1j8lxy1na04rhmx-openjdk-headless-17.0.20.1+1/lib/openjdk`.
No Nix command was invoked to resolve it. `keytool -printcert` parsed the supplied
public certificate and printed matching identity, RSA size and fingerprint.

An independent temporary Java source-mode harness used CertificateFactory X.509:
exactly one certificate, checkValidity at the current clock, RSA-4096 key,
more than 25 years of remaining validity (actual interval 50 years), and valid
self-signature. MessageDigest SHA-256 over X509Certificate.getEncoded() equals
the installed fingerprint. Its DER bytes also equal the independently decoded
Python DER bytes. Python separately checked a full single-certificate PEM match,
strict base64 decoding, exactly one lowercase 64-hex digest followed by one LF,
and hashlib SHA-256 equality. Both installed files equal their supplied originals.

Initial scratch validation incorrectly assumed LF-only PEM bytes; the original
contains 26 CRLF body lines. That check rejected the input before writing the
scratch DER, so its dependent Java attempt failed for missing DER. Corrected the
scratch check to use universal newlines, matching existing release tooling, and
reran successfully. This is valid PEM and did not require a provisioning/tooling
fix or modifying the supplied certificate. No qualified gate was invalidated.
Scratch harness, public DER and certificate-validation output are under ignored,
excluded `build/reports/production-signing-identity/`.

## 4. Private-material absence and ignore protection

Existing `python3 -B tools/release/release.py guard` passes before and after
provisioning. Only its three allowed filenames are in release/signing.

A repository-only Git path search covered tracked, untracked and ignored paths.
It initially found four ignored files left from earlier qualification:

```text
.gradle/m1l-inspection/m1k-debug.keystore
.gradle/row-reveal-baseline-device/test-only.p12
.gradle/row-reveal-device/test-only.p12
.gradle/security-qualification/test-only.p12
```

The human explicitly authorized deleting exactly these four files. They were
removed without opening, inspecting contents or copying them. No other existing
files were removed. The repeated scan passes: no .p12, .pfx, .jks, .keystore or
.key paths. A content-marker scan of 5,310 repository regular files found no PEM
private keys, OpenSSH private keys or PuTTY private-key markers. It excluded .git
metadata, did not follow symlinks, and did not search outside the repository.
These checks establish absence of recognized signing material; they do not claim
arbitrary opaque bytes can be proven never to encode a secret.

`.gitignore` is byte-identical to HEAD. `git check-ignore -v` confirmed protection
for .p12/.jks/.keystore/.key in release/signing, lines 21–24. Both named public
files remain unignored and visible to Git.

## 5. Release-tool acceptance and exact signer enforcement

Before copying, calling the actual `release.expected_identity()` produced the
expected intentional unprovisioned rejection. After copying, that same function
passes using the real pinned keytool and returns exactly the fingerprint above.
The identity absence condition is now satisfied.

Reviewed workflow/helper chain, unchanged from HEAD:

- qualify runs identity before protected approval and prepare afterward;
- sign validates bundle before and after its isolated signing step;
- verify runs finalize; publish revalidates and verifies downloaded bytes;
- identity calls expected_identity, which hashes the single public certificate
  and compares the committed-source fingerprint;
- prepare binds that fingerprint into provenance; bundle_check compares it back
  against the exact source identity;
- verify_signed always passes the fixed release/signing fingerprint path;
- signed verifier requires provenance fingerprint equality, exactly one actual
  signer with precisely that digest, and the required v2/v3 schemes.

No workflow fallback, optional fingerprint, unpinned signer acceptance or identity
bypass exists in this chain. A supplied keystore alone cannot redefine expected
identity. Source/tag/clean-checkout gates remain intact. The full `identity` CLI
was deliberately not invoked: this milestone keeps changes uncommitted and VERSION
is still developmental. Public-identity acceptance is proven independently of those
future release-source gates. No protected workflow was invoked.

## 6. Version-provenance and product/dependency invariance

The committed correction remains active: APPLICATION_ID is independently pinned
to `org.totipo.android`; qualified_provenance extracts versionName/versionCode
using pinned aapt2 from the qualified unsigned APK and checks versionName against
VERSION. bind_unsigned rehashes the original unsigned APK and checks its metadata;
signed verification compares candidate metadata with validated provenance. There
is no hard-coded current versionCode in signed-verification authority. The focused
suite still exercises positive versionCode 42 and rejection of wrong code/name,
foreign application ID, changed unsigned input, malformed provenance and wrong
signer/schemes. No production APK or current version is used as a second version
constant in the signing verifier.

VERSION remains `0.0.0-dev`; production versionCode remains unchanged. app/src,
JVM tests, Gradle configuration, locks, verification metadata, package/cache/flake
inputs, runtime graph declarations, .github workflows and tools are byte-identical
to starting HEAD. Java remains 0.2.0 and protocol v1/r19. No product, manifest,
permissions, UI, lifecycle, Sync, dependency or build behavior changed.

## 7. Selected qualification and execution counts

| Category | This milestone |
| --- | --- |
| Baseline full Gradle | 0; public release trust input only |
| Focused Gradle | 0 |
| Final normal full Gradle | 0; no production/test/build input change |
| Strict-clean Gradle | 0; no production/test/build input change |
| Wrapper/debug/release artifact verifier | 0; no APK change |
| Embedded Gradle APK verifier | 0 |
| Release Python suite | 2 runs, each 18 tests PASS, no skips |
| Existing release guard | 2 runs PASS |
| Pre-install absent-identity check | 1 expected rejection |
| Installed expected_identity checks | 2 PASS, actual keytool |
| Standalone keytool certificate inspection | 1 PASS |
| Independent Java/Python certificate checks | Final PASS after scratch newline assumption correction described above |
| Private-material scan | 1 rejected scan, 1 PASS after authorized cleanup |
| Static workflow/provenance/invariance checks | PASS |
| Disposable signing smoke | 0; unchanged signing implementation, prior committed smoke evidence retained |
| Physical/device harness | 0; no changed subsystem |
| Human Nix | 0; no effective input change |
| Agent Nix | 0 |
| Production signing / remote CI / release dispatch | 0 / 0 / 0 |

Final 18-test output preserved in
`build/reports/production-signing-identity/release-tests.txt`. The printed
`signed.apk` verification message comes from mocked unit-test SDK responses;
no actual APK was signed by this milestone. Documentation local links and
`git diff --check` pass. No repeated full gate or new signing smoke was necessary.

## 8. Nix disposition, freeze and invalidations

Inspected actual package.nix filter: trees app/src, gradle and tools plus the exact
named Gradle/version/license/branding files are selected. .git/.gradle/build/.direnv
components and symlinks are excluded. package.nix/flake.nix/flake.lock/package-deps.json
are evaluator/cache inputs. package installCheck runs the release tests from tools;
those tests use temporary synthetic identities, not the production certificate.
The workflow static test deliberately skips when .github is absent in filtered source.

release/signing (including both public files), release documentation, root supplied
public files and review reports are outside effective package/check inputs. The
flake does not separately read them. Their role as release-workflow trust inputs
does not make them Android package inputs. Removing ignored .gradle files likewise
changes no effective inputs. Existing human Nix evidence in the committed prior
report is retained, not represented as a new execution for this milestone.

Freeze statements:

- Production source stable: unchanged from starting HEAD.
- JVM tests stable: unchanged from starting HEAD.
- Build/package configuration stable: all Gradle, package, flake, cache, lock and
  VERSION inputs unchanged from starting HEAD.
- Qualification harness/tooling stable: tools unchanged from starting HEAD;
  final focused suite and static checks PASS.

No effective included-input edit and no gate invalidation. Therefore no new human
`nix flake check path:.` is required or requested. This report and release docs are
excluded and do not invalidate Nix. No Nix command was run by the agent.

## 9. Human-reported environment/secrets and release disposition

**HUMAN-REPORTED ONLY:** the permanent private PKCS#12 key was generated separately,
offline recovery copies were made, and the protected GitHub android-release
environment/secrets were configured. No GitHub environment, secrets, passwords,
private key, recovery copy or required-reviewer setting was fetched or inspected;
no independent verification of that human work is claimed.

Only public identity provisioning is complete. No production candidate signed,
production key tested, APK installed, GitHub release created, workflow/CI dispatched,
Nix run, version changed, tag created, push performed, file staged or commit made.
Future release requires committing/reviewing the identity and deliberate release
version/source/tag preparation followed by the existing protected procedure.

## 10. Changed files and final Git state

New public files and report:

- release/signing/android-release-cert.pem
- release/signing/android-release-cert.sha256
- review/ANDROID_PRODUCTION_SIGNING_IDENTITY_REPORT.md

Updated only release/signing/README.md and release/README.md to describe public
identity status, pending commit and human-reported private/environment setup.
Historical milestone reports remain unchanged. Four ignored signing leftovers
were deleted with explicit human approval; original root public files preserved.

Final branch/HEAD: main / 53f14974f30fd58d4bad3c9ece76c3b82acc8c69.
Index unchanged and empty diff against HEAD; all milestone edits unstaged.
Expected final `git status --short`:

```text
 M release/README.md
 M release/signing/README.md
?? android-release-cert.pem
?? android-release-cert.sha256
?? release/signing/android-release-cert.pem
?? release/signing/android-release-cert.sha256
?? review/ANDROID_PRODUCTION_SIGNING_IDENTITY_REPORT.md
```

The two root untracked inputs are preserved by explicit user instruction. Only
the destination public identity, release docs and this report constitute the
commit-ready milestone; no staging operation was performed.
