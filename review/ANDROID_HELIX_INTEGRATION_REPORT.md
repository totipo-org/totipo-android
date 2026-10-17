# Helix integration and qualification report

## Final integration and scope

Classification: BUILD_DEPENDENCY (development-shell/editor integration),
TEST_OR_QUALIFICATION_TOOLING (shared editor adapter/tests), and DOCS_ONLY.
Started on clean main at `06a228a677d0ccb96c94769d3de5b30fc4e7c32b`.
The final consumer uses the published `java-dev-tools.lib.mkAndroidEditor` helper.
No local .helix files, copied Gradle adapter or launcher text override remain.

Toolkit pin: `d18e1767593b3ac16da6c76a8f36fed36ac4c807`.
NAR hash: `sha256-90TXj8KLEhjvTjb/4o9eFpLDegt2cgV+6pHXEJ9+e9g=`.
Remote HEAD and clean sibling checkout match the user-published commit. Toolkit
files match their human-qualified source freeze. The consumer lock follows root
nixpkgs and preserves every preexisting node/pin; only the new toolkit node/root
edge differs from starting HEAD.

Default shell adds Helix and Android-aware JDTLS. The toolkit owns startup settings,
Gradle discovery and the Android model. It runs JDTLS on a private JDK 21 while
retaining the supplied JDK 17 in JAVA_HOME for Gradle compiler discovery. Gradle
9.8.0 and the existing SDK/patched AAPT2 are passed unchanged. CI/build tooling,
Android bytecode targets and ordinary Gradle/package definitions are unchanged.
User flake formatting and jailed-codex sibling-toolkit/log access are preserved.

The editor model selects main/debug and test/testDebug, locked dependencies,
SDK jars and generated R classes, with IDE classes under app/build/eclipse.
Release/instrumented sources are outside this model. Optional publisher source
archive downloads remain disabled until their checksums are reviewed. Binary
verification remains strict; class documents supplied by JDTLS are navigable.
Normal builds do not load the adapter. The separate mkJavaEditor integration
retains ordinary Java defaults, verified by shared regressions.

## Reviewed inputs and shared implementation

Inspected current AGENTS, README, flake/lock, package filtering, workflows, relevant
Gradle/toolchain/verification configuration and historical qualification/index
refresh evidence. Initially reviewed toolkit b5fe56e, including INTEGRATION.md,
library/source helper, proxy packaging, correctness patch and test entry points.
Later, at the user's direction, extracted reusable Android support into the same
tooling repo, with a separate mkAndroidEditor helper. No separate repo was created.

The shared implementation adds serverJdk separation, opt-in preference merging
before first import and on configuration updates, packaged Android model code,
protocol/settings regressions and plain Java source/model regression fixtures.
Client preferences/arguments and unrelated initialization fields are preserved;
required adapter arguments remain present without duplication. Empty opt-in
settings leave normal Java startup/configuration unchanged. The generic source
helper and all toolkit dependency pins are byte-identical to the earlier revision.
User ran toolkit nix flake check PASS, then personally committed/pushed d18e176.
Tooling review: ../java-dev-tools/review/ANDROID_EDITOR_INTEGRATION_REPORT.md.
No agent commit/push or actual staged content.

## Diagnosis and failure evidence

1. Initial focused offline javaToolchains/Maven-boundary invocation stopped at
   AGP 9.4.0 plugin resolution before tasks: marker absent from local cache.
2. Initial human Android Nix check failed on Google's mutable master-index.xml.
   Independent HTTPS download redirected from maven.google.com to dl.google.com:
   application/xml, 10,309 bytes, metadata root, 336 entries. SHA-256 matched the
   received failure value. Changed only package-deps.json's index response hash:
   `sha256-TY5pdjdTh6YY8xjvPuyvu051HkN2jO/njbNfYIjGibY=` to
   `sha256-A50cGTtYF/oHm8+QxLqSWihc1xpXg6IUkYEozsBhD08=`.
   Structural comparison proves every other cache value unchanged.
3. First editor import used downloaded Gradle 8.9, selecting seven alternate
   unverified Kotlin artifacts. Passing the supplied Gradle restores GRADLE_HOME.
4. Next import could not discover Java 17 because the old launcher overwrote
   JAVA_HOME with 21. Separating server runtime and build environment fixes this.
5. A temporary consumer launcher assertion used hasInfix on a store-context string,
   which Nix rejected as a regex. Replaced by string equality; the entire local
   workaround is now removed in favor of the shared serverJdk API.
6. JDK navigation then worked but app source was outside its classpath. Startup
   preferences require initializationOptions.settings; Helix passes its config
   directly as initializationOptions. Corrected startup nesting. A fresh workspace
   ruled out stale cache state. The bundled Android model script still generated
   no source roots with this AGP setup, reproduced directly with Gradle.
7. Added the editor-only model adapter and verified actual Buildship model data.
   A temporary probe failed on configuration resolution outside its owner's lock;
   final resolution is inside the Eclipse model callback. Initial offline adapter/
   resource checks failed on uncached Totipo/JUnit/BC metadata; narrow online
   retries passed without metadata/lock/version edits.
8. Extracting into the newly accessible toolkit checkout required one offline
   AGP-cache failure/online retry. A Tooling API harness launch had a classpath
   typo and failed compilation before execution; corrected invocation passed.
9. After user authorization to run targeted flake update, agent attempted it but
   shell returned 127 (nix not installed). User ran it; lock review passed.
   No agent Nix process executed, and no unperformed Nix result is claimed.

## Focused and editor evidence

- Local adapter classpath generation: online PASS, offline PASS. Exact four roots,
  no release root, platform/Totipo/JUnit/R classpath and separate IDE output checked.
- Debug R/resource + model generation with GRADLE_OPTS empty: PASS, establishing
  adapter-side patched-AAPT2 setup rather than reliance on the Gradle CLI.
- Local and extracted adapter Tooling API EclipseProject queries: 2 PASS,
  asserting intended roots and existing Android/Totipo/JUnit/R files.
- Extracted model on Totipo app and isolated AGP 9.4 library: PASS. Library's
  unitTests=false/custom-build-directory model: PASS. Final extracted app rerun PASS.
- Plain Java regression script: 2 PASS runs, each four offline Gradle invocations.
  Root Java and multi-project source helpers work; Android adapter leaves their
  source roots and complete generated classpath XML unchanged.
- Shared Go/proxy/settings and Java model tests: covered by human toolkit
  nix flake check PASS. Agent had no Go compiler; no independent Go run claimed.
- hx --health java: human discovery and all listed syntax facilities PASS.
- Working local adapter editor session at 19:18: user reports working; direct log
  confirms AndroidVaultController/TokenChange/ForegroundVaultCoordinator definitions,
  Android TextView/EditText/Bundle and Totipo CreateVaultResult class documents.
  MainActivity/opened app classes have zero diagnostics; prior classpath errors gone.
  Other diagnostics are existing deprecations/unused imports.
- Published shared-launcher editor smoke after local-file removal: PASS,
  user-reported. Latest accessible log inspected below; no prior classpath
  or import-error result is reused as a new PASS.
- git diff --check and no-staged-content invariant PASS.

Proxy protocol messages are logged to stderr; Helix's ERROR transport label alone
is not failure evidence. An existing shutdown {} versus unit warning appeared in
earlier sessions; no shutdown behavior change is claimed.

## Source membership, freeze and pending final gate

Actual Android package filtering still selects app/src, gradle, tools and its
explicit build/metadata files, excluding generated/cache state and symlinks.
Every selected Gradle/package source input remains unchanged. The temporary local
.helix files were outside those consumers and are now gone. README/review are
excluded from effective checks. Shell/evaluator/cache changes require human Nix,
not an Android product rebuild solely for this tooling milestone.

- Production source stable: byte-identical to starting HEAD.
- JVM tests stable: byte-identical to starting HEAD.
- Build/package configuration stable: shared helper, reviewed published pin and
  independently verified cache response; Android tools/build/dependency pins unchanged.
- Qualification harness/tooling stable: shared implementation qualified before
  publication; no further source change planned, editor smoke still pending.

Current final-source hashes (excluded report omitted):

```text
0260ad2c84881ed2559a9e6d15af248a28c778a2605bd08d49bd933c5e1c17b6 .gitignore
6ef09988737c017ae92988a1828889eddfc28860bcff7de399706355f61b9cfe README.md
fd101f4f45601684719bdb6b84fbbf3d5a2155a59260e47e3af9c5041159872a flake.lock
16f7cbe14bad0e9011d1cbe2c95184f5d24d0543197213b201208a2024337ad1 flake.nix
03a71a899b656de2a7d9949ff3bcb6300f24c0c0b5a971cc853e41c73886d4f2 package-deps.json
```

All intended final files are Git-known. New report has intent-to-add only.
The removed local .helix files were new intent-only entries, not starting-HEAD
files; their zero-content deletion markers are left in the intent index per
policy. Diff against HEAD has exactly six final changed files: .gitignore,
README, flake.lock, flake.nix, package-deps.json and this report. Unqualified
`git diff --name-only`/status additionally display the two intent deletion markers.
`git diff --cached --name-only` is empty; cached-content quiet check passes.

Earlier Android Nix results: initial metadata failure, subsequent PASS, and PASS
after Gradle discovery fix. Those passes were invalidated by later included shell
changes. A final request during local-adapter work was deferred when the user
explicitly expanded scope to shared-toolkit extraction. After the published
migration/editor retest, the renewed final consumer nix flake check has now passed, user-reported.
No prior pass is presented as qualification of changed inputs. Report-only result
updates do not invalidate Nix under the inspected effective input dependencies.

## Execution counts and final status

| Category | Count/result so far |
| --- | --- |
| Full baseline/final-normal/strict Gradle | 0 / 0 / 0; Android source/build inputs unchanged |
| Direct focused Gradle CLI | 14; 9 initial diagnostics + 5 shared-extraction diagnostics |
| Plain Java regression Gradle CLI | 8 nested calls in 2 script runs, all PASS |
| Standalone Tooling API model queries | 2 PASS; separate initial harness compilation typo |
| APK verifiers / physical harness / installs | 0 / 0 / 0 |
| Human Android Nix checks | 4 reported: initial failure, 2 superseded passes, final published migration PASS |
| Human toolkit Nix checks | 1 PASS, published inputs revalidated identical |
| Agent Nix executions | 0; 1 authorized update attempt failed before execution |
| Human lock commands | Initial lock + targeted published toolkit update |

One online task inventory printed BUILD SUCCESSFUL but its client was interrupted
while collecting output and returned 130; distinguish completed task output from
clean client exit. All diagnosis used focused commands, with no clean, rerun-tasks,
APK rebuild or repeated full Gradle gate. Agent source remains main at starting
HEAD, unstaged/uncommitted; user alone published the sibling toolkit. Actual editor
retest and final consumer Nix both PASS, user-reported with direct editor-log
inspection and unchanged frozen-input verification.

Final shared-helper editor follow-up: user reports successful navigation after
reload/restart. Frozen consumer file hashes are unchanged, and all intended
files remain Git-known with no staged contents. Latest log session begins 2026-10-17T19:40:36.585.
Server error messages: 0; severity-1 diagnostics: 0.
Definition destinations observed: 7.
Four input categories are refrozen after this successful actual editor smoke.
One renewed final human `nix flake check` is now requested for these published
consumer inputs; final PASS is now user-reported. No further code/config/tooling edit planned.

## Final qualification result

Human reports final consumer `nix flake check` PASS after the published-toolkit
migration and successful fresh editor smoke. All recorded consumer hashes and
unchanged Android/package/check inputs were verified again afterward. Toolkit
pin remains d18e1767593b3ac16da6c76a8f36fed36ac4c807. No log/duration/cache
breakdown supplied for this gate; no independent Nix execution claimed.
Only this excluded report is updated after PASS, so the result remains valid.
Qualification is complete; no further gate required for report-only edits.

Final Git state remains main at starting HEAD, unstaged and uncommitted, with
no staged-content diff. Six final changed files against HEAD; two zero-content
intent deletion markers remain for the removed draft .helix files. No copied
editor adapter or launcher workaround remains in this repo. Toolkit publication
was performed by the user; consumer commit is left for normal human review.
