# Totipo Java 0.1.4 Android repin and private-local qualification

Released provenance, agent qualification, regenerated dependency-cache review,
physical private-mode qualification and final human Nix validation passed.
Android Gate: PASS. Storage-NIO remains debug-only; no production dependency
decision is made here. All changes are unstaged/uncommitted for review.

## 1. Starting state

- Branch: `main`.
- HEAD: `669ee0381258c735253b100774946b8d55c8728d` (committed M1C).
- Agent ran `git branch --show-current`, `git rev-parse HEAD`, and
  `git status --short` before editing. Starting status was empty.
- Baseline: released core 0.1.3 production, released NIO 0.1.3 debug only;
  BC 1.86, Java 17, Android compile/target API 37, min API 26,
  AGP 9.4.0, Gradle 9.8.0, JUnit 4.13.2.
- Inspected app/root build scripts, settings, dependency documentation, locks,
  verification metadata, reviewed hashes, package cache/derivation, flake,
  bootstrap refresh, Maven boundary checks, APK/wrapper/toolchain checks, CI,
  existing probe Activity/helper and the historical M1C report before editing.

## 2. Java 0.1.4 release provenance

Verified actual bytes from Maven Central over HTTPS, not a local artifact or
source substitution. Both modules have published POM/module/JAR files and release
module status. Inspected the [published release](https://github.com/totipo-org/totipo-java/releases/tag/v0.1.4)
via GitHub REST API: published `2026-10-07T19:34:10Z`, draft=false,
prerelease=false. Annotated tag `v0.1.4`, object
`6d59303fc2e3cdae9f57032b0d889841421cbb96`, resolves to Java commit
`44332d459e0cbec74e93ea5fb77280596c505732`.

[Release SPEC_PIN.md](https://github.com/totipo-org/totipo-java/blob/44332d459e0cbec74e93ea5fb77280596c505732/SPEC_PIN.md)
confirms v1/r18 and spec commit `4623a7e1718e23504903096c92332597057bd8f0`.
Its exact bytes are unchanged from 0.1.3, SHA-256
`f4212f2c1d22d3acb553751755e5ca30689c7138776140a59b69383e27557b08`.
This is a committed revision pin, not an r18 release tag.

Both published module API/runtime variants target Java 17; all production classes
in both downloaded JARs have class major version 61. Core runtime dependency is
BC 1.86. NIO API and runtime variants require core 0.1.4; its POM agrees.
JAR sizes and SHA-256 hashes agree with published module metadata: core 200,360
bytes, NIO 41,543 bytes. Core JAR bytes are identical to the 0.1.3 core JAR;
its coordinate/module metadata have advanced. NIO JAR contains the public
`openPrivate` factory. All 15 published NIO source files match the exact release
commit byte-for-byte. Sources JAR SHA-256:
`bee2a5c9f941360b9c499f1518e3b2201e1e94405a34a8d2709033419e2ed67f`.

| Maven artifact | SHA-256 of downloaded bytes |
| --- | --- |
| [totipo-core-0.1.4.jar](https://repo.maven.apache.org/maven2/org/totipo/totipo-core/0.1.4/totipo-core-0.1.4.jar) | `1e3db6ca15273941549dfa821b9f7dc9a00c6ad81e7e66642a1f09c114eeee19` |
| [totipo-core-0.1.4.module](https://repo.maven.apache.org/maven2/org/totipo/totipo-core/0.1.4/totipo-core-0.1.4.module) | `6dc1052306ed1e25dd06c7484a0378e7e3f19c74607f5bc5b7ab3be54802c564` |
| [totipo-core-0.1.4.pom](https://repo.maven.apache.org/maven2/org/totipo/totipo-core/0.1.4/totipo-core-0.1.4.pom) | `498abaca7446d84fbb0da3bd92246424fe2eabe839ff5c02c1f5d7b2b7e54bc8` |
| [totipo-storage-nio-0.1.4.jar](https://repo.maven.apache.org/maven2/org/totipo/totipo-storage-nio/0.1.4/totipo-storage-nio-0.1.4.jar) | `9e559ec75fb09af068f876751f32d696c50c40988a16d28419dfa05d1d1c4dae` |
| [totipo-storage-nio-0.1.4.module](https://repo.maven.apache.org/maven2/org/totipo/totipo-storage-nio/0.1.4/totipo-storage-nio-0.1.4.module) | `2d9b9e6bf66e21a7258eeeecc85550f11dc4071896e847c2d57d7319f3c2af24` |
| [totipo-storage-nio-0.1.4.pom](https://repo.maven.apache.org/maven2/org/totipo/totipo-storage-nio/0.1.4/totipo-storage-nio-0.1.4.pom) | `f954ae9643d0a33b3e2ee8c0024a62e4b5fc555c6c233795a986eb6c12a48b4c` |

POMs/sources were inspected for provenance; they are not local build inputs.
Historical 0.1.3 evidence remains in the earlier milestone reports.

## 3. Dependency changes

- `implementation("org.totipo:totipo-core:0.1.4")`, advanced from 0.1.3.
- `debugImplementation("org.totipo:totipo-storage-nio:0.1.4")`, advanced from 0.1.3.
- Storage-NIO remains a debug qualification dependency. Release excludes it.
- No bridge, product UI, Java implementation or protocol changes.
- BC 1.86 and all unrelated locked libraries (including AGP/JUnit) unchanged.
  No dynamic/snapshot dependency, local substitution or mixed current Totipo version.

## 4. Trusted dependency state

Used existing explicit `bash bootstrap-m0.sh --refresh-dependencies` path after
inspecting it: wrapper/toolchain checks and Gradle only, no Nix invocation.
Refresh passed. `app/gradle.lockfile` differs only in the two Totipo versions;
configuration membership is unchanged. `buildscript-gradle.lockfile` is byte-for-byte
unchanged. No tracked app buildscript lock exists in the baseline. Verification
metadata generated four 0.1.4 JAR/module hashes; each was checked against actual
Central bytes. Removed the two obsolete 0.1.3 components after refresh. All unrelated
verification components are byte-for-byte unchanged. All six new reviewed JSON
hash entries match Central; unrelated reviewed entries remain unchanged.
Reviewed hash evidence follows the existing URL/SHA-256 JSON convention,
updated for actual Central 0.1.4 bytes, including inspected POMs.
`package.nix` cache readiness requires core/NIO 0.1.4 plus unchanged BC 1.86.
The human-regenerated `package-deps.json` was inspected directly. Core/NIO 0.1.4
each has JAR/module/POM entries, including debug NIO required by package `check`.
All six SRI hashes match fresh actual Central downloads and reviewed JSON hashes;
the four JAR/module hashes also match strict Gradle verification metadata. No
0.1.3 entry remains. BC and every unrelated downloaded dependency/hash are unchanged.
Totipo publisher metadata now identifies 0.1.4. One unrelated inert metadata field
changed: Gradle tooling-api publisher `lastUpdated`, `20260924135448` →
`20261007170937`; its release remains `9.9.0-milestone-2`. This changes no resolved
library, artifact URL or artifact hash. No cache hashes were fabricated or edited
by the agent.

## 5. Maven boundary

`verifyMavenBoundary`, wired into `check`, requires these external graphs:

```text
releaseCompileClasspath:
  app -> org.totipo:totipo-core:0.1.4
releaseRuntimeClasspath:
  app -> org.totipo:totipo-core:0.1.4 -> org.bouncycastle:bcprov-jdk18on:1.86

debugCompileClasspath:
  app -> org.totipo:totipo-core:0.1.4
  app -> org.totipo:totipo-storage-nio:0.1.4 -> org.totipo:totipo-core:0.1.4
debugRuntimeClasspath:
  app -> org.totipo:totipo-core:0.1.4 -> org.bouncycastle:bcprov-jdk18on:1.86
  app -> org.totipo:totipo-storage-nio:0.1.4 -> org.totipo:totipo-core:0.1.4
```

Checks retain direct external core/NIO edges, exact Totipo module sets, BC runtime
edge, and rejection of project/file components/artifacts. The new explicit NIO
edge assertion checks both requested and selected 0.1.4. All Totipo module requests
must match their selected coordinate and version 0.1.4, rejecting a mixed request
that Gradle might otherwise upgrade. Settings retain Central as the only Totipo
repository, with no Maven Local, source substitution, snapshots or composite build.
Actual check results: passed in refresh, normal build and forced offline build.
Release APK excludes NIO/debug probes; debug APK contains NIO/probe classes.

## 6. Probe change

Updated the existing M1C `LocalNioQualification`, preserving its Activity, worker
thread, saved diagnostic file, primitives and core workflow. Every store opening
now calls the published `NioTotipoStore.openPrivate(root)` with default durability.
There is no reflection, implementation-class call, injected operation, Android
fallback or copied Java storage code. This explicitly selects the released
private/exclusive-local installation mode after the historical shared hard-link
workflow failed; it is not a runtime fallback.

The helper owns a new UUID subtree below `Context.getNoBackupFilesDir()` exclusively.
Runs are synchronized; sessions/store handles close before the next handle opens.
No independent Totipo writer, Syncthing, SAF or cloud process writes into that root.
Disposable evidence directories remain retained; cleanup checks concern stages and
canonical partials, not deleting all evidence.

Required sequence: absent VAULT/namespace observation; actual create and close;
authenticate with same fingerprint; core-authored test TOKEN save and revision;
1024-byte bounded SPI read; exact retry requiring `AlreadyPresentExact`; changed
input to the existing target requiring `ExistingDifferent` and exact preservation;
reopen with Finished observation, no diagnostics and token/revision; password
rewrap requiring CHANGED; old credential AuthenticationFailed; new credential
Opened with unchanged fingerprint and token/revision; final clean close/layout.
Credentials and fixed test secret are visibly disposable and non-secret.
Changed bytes are rejected-input evidence only, never published as a new envelope.

Hard-link/collision primitive remains optional shared-mode evidence. A new ordinary
move primitive supplements the actual workflow results. Successful Created/Saved
outcomes identify the private initial-VAULT and immutable-object paths separately:
complete forced stage → ordinary move without replacement options → canonical file
force → containing namespace/root force. Those statements derive from the inspected
released source and successful public acknowledgement, not internal instrumentation.
VAULT rewrap retains the common released replacement path. No crash/power-loss
claim follows from force acceptance or reopen.

## 7. Agent non-device validation

All final non-Nix checks below passed on the existing JDK 17/SDK toolchain.
No Nix command was executed. Initial refresh caught a syntax error in the new
Kotlin boundary assertion; corrected before dependency files changed. Final
refresh ran all three Gradle phases successfully.

```sh
bash bootstrap-m0.sh --refresh-dependencies
./gradlew check :app:assembleDebug :app:assembleRelease
./gradlew --offline --no-daemon --no-configuration-cache --no-build-cache \
  --rerun-tasks --dependency-verification=strict \
  clean check :app:assembleDebug :app:assembleRelease
python3 tools/verify-wrapper.py
python3 tools/verify-apk.py app/build/outputs/apk/debug/app-debug.apk --debug-nio
python3 tools/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk \
  --unsigned --no-debug-probe
git diff --check
```

Normal Gradle: BUILD SUCCESSFUL, 90 tasks (4 executed, 86 up-to-date).
Forced offline Gradle: BUILD SUCCESSFUL, 92 tasks, all 92 executed. Seven unit
tests passed (one CoreDependencySmokeTest, six ProbeLogicTest), zero failures,
errors or skips. Lint and Maven/release APK boundary tasks passed. Existing AGP
experimental-option/Gradle deprecation warnings remain; no toolchain/library
changes were introduced to suppress them. Wrapper 9.8.0 JAR/distribution pins verified.

| APK | SHA-256 |
| --- | --- |
| debug (NIO/probe required) | `e639ee20ea5344df5227841ad8fec734b5cf3810af4518724d9ce44c8d005bee` |
| release unsigned (NIO/probe excluded) | `b197bd85da2c256b051e424a5dc778715f7c4598f9161e15de00c62dfa4a3d57` |

Core 0.1.4 is established by the verified external resolution/artifact graph;
DEX class presence alone cannot distinguish the identical 0.1.3/0.1.4 core bytes.

## 8. Human Android qualification

Human supplied the generated report from a physical API-37 run using the v2
private-mode harness. Run ID: `63475279-1c22-4b6c-9b81-fcc8de6388d6`.
Device model/OS build were not supplied; no wider device claim is made.
The following table precisely summarizes all required workflow evidence.

| Check | Actual result / evidence |
| --- | --- |
| Private store initial observation | `org.totipo.spi.BoundedRead$Absent`; `org.totipo.spi.ObjectScan$Complete`, empty namespace; close completed, both canonical namespaces absent |
| Initial VAULT creation | `org.totipo.CreateVaultResult$Created`; created session closed; no abandoned VAULT stages |
| Authenticate reopen | `org.totipo.OpenResult$Opened`; same fingerprint; Finished observation without diagnostics |
| Core-authored immutable TOKEN | `org.totipo.SaveResult$Saved`; one revision; private immutable installation acknowledged |
| Object scan / bounded read | `org.totipo.spi.ObjectScan$Complete`; `org.totipo.spi.BoundedRead$Present`; exactly 1024 bytes, expected 1024 |
| Exact retry | `org.totipo.spi.ObjectWrite$AlreadyPresentExact`; exact original bytes preserved |
| Different existing target | `org.totipo.spi.ObjectWrite$ExistingDifferent`; canonical bytes preserved; immutable stages cleaned |
| Close / state reopen | `org.totipo.OpenResult$Opened`; same fingerprint; Finished observation without diagnostics; token/revision present |
| Password replacement | `org.totipo.PasswordChangeResult:CHANGED` |
| Old credential | `org.totipo.OpenResult$AuthenticationFailed` |
| New credential | `org.totipo.OpenResult$Opened`; same fingerprint; Finished observation without diagnostics; same token/revision present |
| Final cleanup / lifecycle | One complete VAULT and one complete immutable object; no abandoned stages or canonical partials; clean final close |

Core revision: `5646d2fab2d0497b5539e1864d764d7d8c2e12ce7654422c53afbfca5d45faa1`.
Harness outcome: `ALL REQUIRED CHECKS SUCCEEDED; human Gate-A review required`.
Its final `Gate-A verdict=INCONCLUSIVE until physical evidence is reviewed` is a
review placeholder; the reviewed verdict is in section 9.

Primitive evidence:

| Facility | Reported outcome |
| --- | --- |
| Directory creation / no-follow type observation | SUPPORTED + succeeded |
| Temporary file / regular-file observation | SUPPORTED + succeeded |
| Bounded actual-byte read with NOFOLLOW_LINKS | SUPPORTED + succeeded |
| Optional symlink observation / rejection | SUPPORTED + succeeded; symlink open rejected with `java.io.IOException` |
| Exact direct-child filenames | SUPPORTED + succeeded |
| FileChannel write loop / force(true) | SUPPORTED + succeeded |
| Optional hard link / collision (shared-mode evidence) | Invocation attempted, failed with `java.nio.file.AccessDeniedException` |
| Directory READ/NOFOLLOW / force(true) | SUPPORTED + succeeded |
| Released default NioDurability root persistence request | SUPPORTED + succeeded |
| ATOMIC_MOVE + REPLACE_EXISTING | SUPPORTED + succeeded |
| Ordinary move without replacement options | SUPPORTED + succeeded |
| Direct replacement move facility | SUPPORTED + succeeded |
| Released replacement fallback selection | NOT EXERCISED; no injected AtomicMoveNotSupportedException |
| POSIX mode / owner mutation | NOT EXERCISED; unused by released implementation |

Actual private initial VAULT and immutable-object workflow acknowledgements
separately establish the inspected released paths: complete forced stage → ordinary
move without replacement options → canonical file force → containing namespace/root
force. This is source-derived interpretation of successful released operations,
not a claim that the probe traced internal syscalls. The standalone move primitive
is supplementary evidence. VAULT password replacement also succeeded through the
common released replacement path. Hard-link denial remains useful evidence about
shared mode; private mode does not require that facility and no required private
workflow failed. No uncertainty or unexpected diagnostic was reported.

Commands supplied for an ordinary terminal outside jailed-codex (APK already built),
using `adb` available in the user's dev shell:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n \
  org.totipo.android/org.totipo.android.debug.LocalNioProbeActivity
```

Tap **Run local NIO qualification** once, wait for completion, then
**Copy diagnostic report**. Alternative exact saved-report retrieval:

```sh
adb shell run-as org.totipo.android \
  cat no_backup/m1c-nio-qualification/latest-report.txt
```

No user Git/Gradle rerun is required.

## 9. Android Gate result

PASS

Every required private-mode workflow succeeded under the intended deployment
preconditions. The optional shared-mode hard-link primitive failed; it is outside
the qualified private installation path and imposes no non-fatal limitation on
these private-mode workflows. No PASS WITH QUALIFICATION is needed. Historical
0.1.3 shared-mode FAIL remains unchanged.

## 10. Qualified scope

Released totipo-storage-nio 0.1.4 private mode is qualified on this one physical
API-37 app-private test configuration for the exercised workflows, using released
core/NIO 0.1.4 and default NioDurability. The disposable no-backup root is exclusively
controlled by the harness; all operations/handles are serialized, with no independent
writer, Syncthing, SAF or cloud process mutating it directly.

This does not qualify arbitrary shared directories. Explicitly excluded:
Android-wide support, crash/power-loss durability, provider/cloud bridge correctness,
cross-client convergence, and any production release dependency decision. Release
continues to exclude NIO and debug probe code.

## 11. Human Nix validation

Human cache regeneration produced the reviewed 0.1.4 cache described in section 4
using the existing requested `nix run path:.#update-package-deps` checkpoint.
The agent inspected the resulting file; it did not execute Nix or observe the
regeneration command's terminal output.

The human explicitly reported that both final commands passed:

| Command | Result |
| --- | --- |
| `nix flake check path:.` | PASS — human reported |
| `nix build path:.` | PASS — human reported |

These exact Nix-only commands were derived from the current flake/package and
requested after source/Gradle/cache/device evidence was incorporated. Terminal
logs were not supplied; the results are human-attested. No agent Nix invocation
or additional Gradle rerun was requested. Regeneration changed `package-deps.json`.

## 12. Remote CI

not run

Uncommitted changes; no push or CI trigger requested or performed.

## 13. Recommended next step

Review these unstaged/uncommitted changes. The Gate passes: recommend review of promoting `totipo-storage-nio:0.1.4` from debug
qualification to the Android production local-replica dependency, followed by resuming
M1C reconciliation design. Neither promotion nor SAF reconciliation is performed
in this task; no product UI work was started.

## 14. Final Git state

Final agent checkpoint after human Nix validation and all evidence incorporation.
Ran `git status --short`, `git diff --stat`, `git diff --check`, and
`git diff --cached --stat` directly.

```text
 M TOTIPO_JAVA_DEPENDENCY.md
 M app/build.gradle.kts
 M app/gradle.lockfile
 M app/src/debug/java/org/totipo/android/debug/LocalNioQualification.java
 M gradle/verification-metadata.xml
 M package-deps.json
 M package.nix
 M review/M0_REVIEWED_DEPENDENCY_HASHES.json
?? review/TOTIPO_JAVA_0_1_4_ANDROID_REPIN_REPORT.md
 TOTIPO_JAVA_DEPENDENCY.md                          | 57 ++++++++++++++---
 app/build.gradle.kts                               | 39 +++++++++---
 app/gradle.lockfile                                |  4 +-
 .../android/debug/LocalNioQualification.java       | 72 +++++++++++++++++-----
 gradle/verification-metadata.xml                   | 18 +++---
 package-deps.json                                  | 24 ++++----
 package.nix                                        |  8 +--
 review/M0_REVIEWED_DEPENDENCY_HASHES.json          | 22 ++++---
 8 files changed, 180 insertions(+), 64 deletions(-)
```

`git diff --check`: passed with no output. `git diff --cached --stat`: empty.
The new report is untracked and therefore absent from the tracked diff statistic.
HEAD remains `669ee0381258c735253b100774946b8d55c8728d`.
All edits remain unstaged/uncommitted; nothing was staged, committed, tagged,
released or pushed by the agent.
