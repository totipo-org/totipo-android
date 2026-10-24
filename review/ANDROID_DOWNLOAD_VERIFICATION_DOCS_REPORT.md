# Android download and signing identity documentation

## Scope and starting state

Classification: **DOCS_ONLY**. Starting branch: `main`. Starting HEAD:
`4a073f223425cb9f21f31514827925e3056b43d3`.
`git status --short` was empty; no existing work was discarded.

Read AGENTS.md, README.md, docs/USER_GUIDE.md, docs/DEVELOPMENT.md,
release/README.md, release/signing/README.md, the public identity provisioning
report, API30 signature-verification report, package.nix, flake.nix, CI and
relevant identity/signature helpers before editing. No operation-model code changed.

The user clarified that **0.2.2 is unreleased**, with 0.2.3 intended after this
work. This milestone performs no version preparation or release action.

## Documentation changes

Only [README](../README.md) and this report changed. Installation now uses the
[latest release](https://github.com/totipo-org/totipo-android/releases/latest),
explains expanding Assets and selecting `totipo-android-<version>.apk`, and
retains update/data-preservation instructions. No current-release number remains.

A nearby **Verify the APK** section displays `org.totipo.android` and the
production certificate SHA-256, copied directly from the existing canonical
[public fingerprint](../release/signing/android-release-cert.sha256), with a link
to its [public certificate](../release/signing/android-release-cert.pem).
These existing files remain the authority; no new signing metadata, generated
identity file, build consumer or independent pin was introduced.
The package ID was checked against app/build.gradle.kts and the downloaded APK.

The section describes [AppVerifier](https://github.com/soupslurpr/AppVerifier)
comparison and [apksigner](https://developer.android.com/tools/apksigner), requires
successful verification and matching certificate digest, and distinguishes the
stable signer fingerprint from each release's whole-file APK checksum.
[Obtainium](https://github.com/ImranR98/Obtainium) is optional release/update
management; following the repository alone is not claimed to verify its signer.
The command's placeholder filename is quoted to avoid shell redirection.

Reviewed the entire [user guide](../docs/USER_GUIDE.md): no unnecessary current
Android-release number was present, so it remains unchanged. Its Syncthing 2.0.x
reference describes a historical qualification environment and is preserved.
Historical release/signing documentation and reports remain unchanged.

## Validation evidence

Public read-only checks on 2026-10-24:

- Latest-release URL: HTTP 200 after redirect to `releases/tag/v0.2.1`.
  GitHub's public latest-release API independently reported v0.2.1, published
  `2026-10-24T03:20:29Z`, neither draft nor prerelease. The v0.2.2 public release
  endpoint returned HTTP 404, consistent with the user's clarification.
- Downloaded the published v0.2.1 signed APK, checksum and provenance from that
  release's Assets. APK asset ID: `671138365`; size: 2,516,538 bytes.
  The APK SHA-256 matched both the published `.apk.sha256` and GitHub asset digest:
  `0c213c6a46dc6afd332d1b28fa8297a1fa9e8c5c6b463b08ab7101b741db4464`.
- Decoded the canonical PEM certificate to DER with Python's standard library;
  its computed SHA-256 equals the canonical fingerprint file and README value:
  `74fd4c313856e1773fd174fba4ecd2a12cc4fde85ae34a3d8a3e986353b01632`.
- Using the already active pinned Nix SDK/JDK, ran Build Tools 36.0.0
  `apksigner verify --verbose --print-certs` against the downloaded APK: PASS,
  exactly one RSA-4096 signer, matching certificate SHA-256, v2/v3 verified,
  v1/v4 false. Also ran the independent API24–27 v2 range: PASS with the same
  sole signer; its warning about referenced scheme ID 3 is recorded in the log.
  Finally ran the README's exact `verify --print-certs` options against the real
  filename: exit 0 with the matching digest.
- Pinned SDK `apkanalyzer manifest` confirmed application ID `org.totipo.android`,
  versionName 0.2.1 and versionCode 6. Published provenance agrees with the package
  and certificate identity. This verifies the available published APK, not an
  unpublished 0.2.2 or future 0.2.3 artifact.
- Reviewed all README and user-guide Markdown links, checked local targets and
  heading anchors, and resolved external README links: PASS. AppVerifier,
  Obtainium and apksigner instructions were checked against their upstream docs.
- `git diff --check`: PASS. README fingerprint equality and absence of numbered
  Android release downloads: PASS.

Downloaded public artifacts and detailed SDK logs are retained only under ignored
`.gradle/download-verification-docs/`, outside Git/Nix source membership.
No device installation, signing, full APK-policy qualification or build occurred.

## Input stability and qualification validity

Inspected actual consumers, rather than assuming all Markdown is excluded:

- package.nix selects `app/src`, `gradle`, `tools` and its explicit build/version/
  license/branding files; it excludes `.gradle` path components. README.md,
  docs/USER_GUIDE.md and review/ are outside this source selection.
- flake.nix's release-tooling fileset selects tools/release, tools/verify-apk.py,
  root/app build scripts, release/signing and the release workflow. The release
  app wrappers use tools/release. Neither changed document is consumed.
- CI runs package-inclusive flake check and an unchanged-input check. No source
  filter, evaluator, cache, workflow or check definition changed.

Freeze statements:

- **Production source stable:** no application source, resource or manifest edits.
- **JVM tests stable:** no test edits.
- **Build/package configuration stable:** no Gradle/Nix/dependency/version/signing
  identity, workflow or release asset changes.
- **Qualification harness/tooling stable:** no tools or harness edits.

Only excluded documentation inputs changed. Existing Gradle, APK, device and
human Nix qualification evidence remains valid for its originally qualified inputs
and scope. No gate was invalidated; no new human Nix result is needed or claimed.
This documentation does not extend historical qualification to another release.

## Execution counts and final Git state

| Execution category | Count |
| --- | --- |
| Baseline full Gradle | 0 |
| Focused Gradle (test/compile/boundary) | 0 |
| Final normal full Gradle | 0 |
| Strict-clean Gradle | 0 |
| Full APK-policy verifiers (external or embedded) | 0 |
| Public APK signature verification with SDK apksigner | 3 PASS |
| Physical/device harness or installation | 0 |
| Agent Nix / human Nix | 0 / 0 |

Zero build/qualification runs follow the documented DOCS_ONLY exemption and
inspected input exclusions. No repeated full gates or invalidations occurred.

Final branch/HEAD remain `main` / `4a073f223425cb9f21f31514827925e3056b43d3`.
Final `git status --short` and `git diff --name-only` identify exactly:

```text
 M README.md
 A review/ANDROID_DOWNLOAD_VERIFICATION_DOCS_REPORT.md
```

The report is intent-to-add only. `git diff --cached --name-only` is empty and
`git diff --cached --quiet` passes: no staged file content. All other tracked
files match starting HEAD. No commit, tag, push, release or policy change occurred.
Changes remain uncommitted for review.
