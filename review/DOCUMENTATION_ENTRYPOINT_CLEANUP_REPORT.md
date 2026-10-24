# Documentation entrypoint cleanup report

## Scope and starting state

Classification: **DOCS_ONLY**, recorded before editing. Work was confined to
`totipo-android`; no production, resource, dependency, version, manifest,
Gradle/Nix, signing or release-tooling changes were made.

Initial commands and results:

```text
git branch --show-current
main
git rev-parse HEAD
fe335ec8ca847de12b80a0daf91ea8cc7d7e5d72
git status --short
(empty)
```

There were no unrelated changes to preserve. Read AGENTS, README, MULTI_VAULT,
TOTIPO_JAVA_DEPENDENCY, release and public signing documentation, VERSION (0.2.1),
VERSION_CODE (6), package.nix, flake.nix, CI, relevant release tools and subsystem
qualification evidence before editing. Inspected app UI, enrollment/parser,
code/clipboard, biometric, manifest and build declarations read-only.

## README organization and size

Measured using `wc -l -w -c`, with the original from `git show HEAD:README.md`:

| Metric | Before | After |
| --- | ---: | ---: |
| Lines | 807 | 90 |
| Words | 6701 | 678 |
| Bytes | 52237 | 5036 |

Before: long implementation introduction; managed environment and Helix; build,
dependency refresh and Nix; artifact policy; bootstrap limitations; qualification
signing; A-MV1 storage; A-MV3 Sync; biometric internals; multi-vault workflows;
optional folder setup; integrated qualification result.

After: product introduction; signed APK installation and runtime requirement;
Create/Join and daily token/vault/unlock workflows; optional Sync and its limits;
essential security/backup disclosures; links to user, developer, release,
architecture, provenance and historical evidence documents.

## Material moved and documentation ownership

| Document | Responsibility |
| --- | --- |
| README.md | Short introduction, installation and essential limitations. |
| docs/USER_GUIDE.md | Current user workflows, token settings, multi-vault naming/switching/Forget/Recover, password/biometric opt-in, folder configuration/status/troubleshooting, security and backup. |
| docs/DEVELOPMENT.md | Moved Nix/direnv, SDK/JDK/Gradle, Helix, build/install, dependency refresh, qualification/artifact policy, CI and local non-production signing procedures. Commands run from repository root. |
| docs/ARCHITECTURE.md | Moved local-store, operation/lifecycle, import-before-publish, provider identity, bootstrap, per-entry Sync and biometric storage/ownership invariants; links to scoped milestone evidence and integrated A-MV6 result. |
| MULTI_VAULT.md | Existing technical selection/registry state machine, retirement and Forget transaction; added navigation to user guide and other internals. |
| TOTIPO_JAVA_DEPENDENCY.md | Unchanged Java runtime/guidance identities and dependency provenance. |
| AGENTS.md | Qualification policy remains authoritative; environment link now points to development documentation. |
| release/README.md | Production release procedures remain here; installation/development links added and moved artifact-policy link repaired. |
| release/signing/ | Public identity and its provisioning disclosure remain unchanged. |
| review/ | Historical milestone evidence remains unchanged; this new report records only the documentation cleanup. |

The user guide consolidates previously scattered workflows into practical steps.
The technical reference preserves detail that does not belong in an installation
introduction. Development content is relocated with relative links adjusted;
qualification commands, required gates and declared support are not weakened.

## Contradictions and outdated framing corrected

- README previously led with source metadata rather than an APK download. It now
  links to actual GitHub releases and names the real 0.2.1 signed asset. No Play
  Store availability is claimed.
- The install minimum (API 26) is clearly distinguished from the existing vault
  runtime requirement (API 30/Android 11), without changing declared support.
- Removed the blanket obsolete “production signing ... not claimed” wording from
  relocated limitations; linked to the existing protected release procedure.
  The real published APK was verified against the committed public identity.
- Removed the stale “revised workflow has not yet run remotely” sentence from
  relocated current procedural guidance. No new CI qualification claim replaces it;
  results belong to their scoped reports and workflow runs.
- Older A-MV4 and daily-driver outstanding-gate wording is explicitly historical;
  the previously documented A-MV6 completion remains linked with its environment
  limits. Historical reports and their claims are untouched.
- Setup instructions distinguish creating a new vault, joining an existing vault,
  choosing a folder, explicitly initializing it, and synchronizing tokens.
- User wording distinguishes local saves, folder writes, separate Syncthing
  replication and receipt by another app. No remote-delivery or backup guarantee
  is implied. Inactive vaults do not sync; no background polling is claimed.
- Security disclosures retain encrypted reusable biometric password storage,
  per-use strong biometrics, password fallback, no-backup private storage,
  uninstall/reset loss, retained token history, conditional clipboard cleanup,
  process-compromise limits and lack of rollback resistance/secure erasure.
- Manual 6/7/8-digit entry is distinguished from the parser's 6/8-digit link
  support. Camera handoff is described as external rather than built-in scanning.

## Validation evidence

Read-only GitHub inspection on 2026-10-24:

- [Latest release](https://github.com/totipo-org/totipo-android/releases/latest)
  resolved to [v0.2.1](https://github.com/totipo-org/totipo-android/releases/tag/v0.2.1),
  published `2026-10-24T03:20:29Z`, source
  `fe335ec8ca847de12b80a0daf91ea8cc7d7e5d72`.
- Downloaded all three published assets: `totipo-android-0.2.1.apk` (2516538 bytes),
  its `.sha256`, and `release-provenance.json`. APK hash matched both checksum and
  release metadata:
  `0c213c6a46dc6afd332d1b28fa8297a1fa9e8c5c6b463b08ab7101b741db4464`.
  ZIP integrity passed. Provenance matched source, tag, version 0.2.1, code 6,
  application ID, Java 0.2.0 and protocol v1/r19.
- Used the already available pinned SDK's `apkanalyzer manifest print` on the
  published APK: package `org.totipo.android`, version 0.2.1/code 6, min/target
  SDK 26/37, only USE_BIOMETRIC permission, allowBackup=false and the two expected
  launcher/enrollment activities. No build or Nix execution was needed.
- SDK `apksigner verify --print-certs` passed for that APK and reported certificate
  SHA-256 `74fd4c313856e1773fd174fba4ecd2a12cc4fde85ae34a3d8a3e986353b01632`,
  equal to the committed public fingerprint. Temporary inspection files were
  created inside the checkout and removed; no downloaded artifact is included.

Feature statements were checked against MainActivity, strings.xml,
OtpAuthEnrollmentActivity, OtpAuthUriParser, TotpPresentation,
PlatformCodeClipboard, AndroidVaultController, AndroidBiometricCredentials,
AndroidManifest.xml, app/build.gradle.kts and the A-MV6 evidence. This is a
source/artifact documentation check, not a new installed-device qualification.

Markdown validation uses a lightweight standard-library checker for heading
syntax/separation, balanced fenced blocks, relative target existence and Markdown
heading anchors, plus manual review of lists/tables and code blocks. It is not a
full Markdown linter. Seven edited documentation files initially passed all 50
relative links/anchors; nine unique external documentation URLs returned HTTP 200,
including the releases/latest redirect and retained Android/GitHub/editor/guidance
references. Published asset URLs were also successfully downloaded. The report
and subsequent wording edits receive a final structural/link pass below.

## Input membership, freeze and execution counts

Inspected actual `package.nix` trees/exact-file filter and `flake.nix` check sources.
Changed root Markdown, `docs/`, `release/README.md` and `review/` are outside the
Android package source and are not consumed by checks or release command wrappers.
The release-tooling fixture does include **release/signing/**, including its README;
that directory was deliberately left unchanged. Root documentation links do not
make their targets package inputs.

- Production source stable: unchanged from starting HEAD.
- JVM tests stable: unchanged from starting HEAD.
- Build/package configuration stable: unchanged from starting HEAD.
- Qualification harness/tooling stable: unchanged from starting HEAD.

No effective qualification input changed, so no existing Gradle, artifact, device
or human Nix result was invalidated. Excluded report-only edits also do not
invalidate them. Human Nix was neither requested nor run; the DOCS_ONLY exception
applies. No release, commit, tag, push or remote workflow dispatch occurred.

| Execution category | Count |
| --- | ---: |
| Baseline full Gradle | 0 |
| Focused Gradle (compile/test/boundary) | 0 |
| Final normal full Gradle | 0 |
| Strict-clean Gradle | 0 |
| Repository APK policy verifier / embedded Gradle verifier | 0 / 0 |
| Read-only published APK inspection | 1 set: checksum/ZIP, manifest, signature |
| Physical/device harness | 0 |
| Human Nix / agent Nix | 0 / 0 |

## Remaining separate work

- No new device/provider/accessibility qualification or legacy migration is added.
  Existing scoped support and security limitations still apply.
- `release/signing/README.md` retains a prose reference to local qualification
  signing “described in the root README.” The root now links to Development,
  which owns that section. A direct-link cleanup there would change a release
  fixture input and require the applicable qualification/Nix gate; it is deferred
  rather than expanding this excluded-input documentation milestone.
- Future releases must update the README's concrete release example and the
  user guide's version description when behavior changes. The latest-release
  link continues to serve as the download entrypoint.
- Historical reports describe their original UI and then-pending gates; they are
  preserved as evidence, not rewritten into current user instructions.

## Final review state

Changed files: AGENTS.md, MULTI_VAULT.md, README.md, release/README.md,
docs/ARCHITECTURE.md, docs/DEVELOPMENT.md, docs/USER_GUIDE.md and this report.
New files have intent-to-add source membership only; no file content is staged.
Final lightweight Markdown structure and all 50 local links/anchors: PASS across
all eight changed documents. The first whitespace pass caught one extra blank
line at the end of the moved development guide; removed it. Final
`git diff --check`: PASS. `git diff --cached --name-only`: empty;
`git diff --cached --quiet`: PASS. Branch remains main at the starting HEAD.

Final `git status --short`:

```text
 M AGENTS.md
 M MULTI_VAULT.md
 M README.md
 A docs/ARCHITECTURE.md
 A docs/DEVELOPMENT.md
 A docs/USER_GUIDE.md
 M release/README.md
 A review/DOCUMENTATION_ENTRYPOINT_CLEANUP_REPORT.md
```

The ` A` entries are intent-to-add, with no staged content. All work is left
uncommitted for review. No excluded-document correction invalidated any gate.
