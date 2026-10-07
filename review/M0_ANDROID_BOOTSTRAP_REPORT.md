# M0 Android bootstrap report

M0 is complete: repository/toolchain/build criteria are satisfied. The application remains
unreleased (`0.0.0-dev`), with minimal bootstrap UI only. Human-operated device
smoke passed: the bootstrap Activity renders. Remote CI has not run.
All implementation changes remain unstaged/uncommitted for manual review.

## Starting state

- Branch: `main`.
- Exact starting HEAD: `0db3ff119601d9002937f9b8e6339b81f3c8668a`.
- Starting `git status --short`: empty, clean.
- Committed seed: `.envrc`, `.gitignore`, `README.md`, `flake.nix`, `flake.lock`.
  The flake supplied JDK 17, Gradle 9, Python and jailed-codex. No Android
  project, SDK composition, wrapper, package, tests or CI existed.
- Existing llm-agents/jailed-agents inputs and Numtide cache configuration were
  retained. The existing flake.lock and its input pins are unchanged.

## Repositories inspected

Current committed default-branch archives were inspected on 2026-10-06, outside
Android production sources. Temporary inspection copies are not build inputs;
no other Totipo repository was modified.

| Repository | Exact commit inspected |
| --- | --- |
| [totipo-java](https://github.com/totipo-org/totipo-java/tree/e2326aca5f5aac661d74925a30fcc57cd91014e8) | `e2326aca5f5aac661d74925a30fcc57cd91014e8` |
| [totipo-desktop](https://github.com/totipo-org/totipo-desktop/tree/547a905a94243bb8aea300de7374f7c0643537de) | `547a905a94243bb8aea300de7374f7c0643537de` |
| [totipo-spec](https://github.com/totipo-org/totipo-spec/tree/91b6ed6bf01c3f45cb166082ffe72e92291be3a4) | `91b6ed6bf01c3f45cb166082ffe72e92291be3a4` |

Java inspection covered README, flake, bootstrap, build/settings/properties,
wrapper configuration, dependency locks and verification metadata, CI/release
workflows, SPI_DESIGN, API_DESIGN, SPEC_PIN, VERSION and published release/tag
provenance. Desktop inspection covered README, flake, package/cache, bootstrap,
build/settings/properties, wrapper, locks/verification, CI, dependency provenance,
VERSION and relevant M4a/M4b/S4 reproducibility/review reports. Spec inspection
covered current v1/r18 protocol/storage/conformance scope and current non-normative
design v0.9, native platform adaptation and deferred design scope.

Adopted: Apache-2.0 license, centralized repositories, strict dependency locking
and SHA-256 verification, wrapper distribution/JAR pins, explicit refresh,
automated external Maven-boundary validation, `0.0.0-dev` sentinel, Gradle fetchDeps
Nix cache and separate human/local/remote qualification evidence.

Intentionally not copied: Java's JDK 25 build JVM/JUnit 6 assumptions/publication
jobs/spec snapshot; desktop's Swing/NIO architecture, source/application design
and distribution layout. Android uses JDK 17, consumes core directly, and chooses
no storage provider or product architecture. No Java implementation, specification
or conformance corpus was copied into Android.

## Final toolchain

| Input | Exact selection |
| --- | --- |
| JDK/build JVM | Nix `jdk17_headless`, OpenJDK `17.0.20.1+1-nixos` |
| Java source / target | 17 / 17; observed class-file major 61, non-preview |
| Gradle wrapper | 9.8.0, bin distribution |
| Distribution SHA-256 | `bafd5ce9cfaea0fbccfdc8439a1ac42fbd4cd9c89dc9a988228d8a2639a58e6c` |
| Wrapper JAR SHA-256 | `238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5` |
| AGP | 9.4.0 |
| compileSdk / targetSdk / minSdk | 37 / 37 / 26 |
| SDK platform | 37.0, catalog archive `platform-37.0_r02.zip` |
| Build Tools | 36.0.0 |
| Platform tools | 37.0.1; adb 1.0.41, build 15733141 |
| Command-line tools | 22.0 |
| SDK source | `androidenv.composeAndroidPackages` from existing locked nixpkgs |
| Root nixpkgs commit | `aa48d347080940b8a2b8d2f48228674e280a3514` |

SDK platform directory `android-37.0` works with integer compileSdk 37 without
an alias. No emulator, system images, NDK, CMake, legacy tools, sources or extras
are selected. Android SDK license acceptance is explicit; unfree allowance is
limited to the selected androidenv package/archive names, not global allowUnfree.

JAVA_HOME is explicitly the managed JDK. ANDROID_HOME and ANDROID_SDK_ROOT point
to the same composed SDK. JDK, Gradle, Python and SDK are supplied to both shell
and jailed-codex. Those variables plus GRADLE_OPTS are forwarded into jail.
No machine-local `local.properties`, host SDK/JDK or manual SDK download is used.
Gradle JDK auto-download/auto-detect and SDK component auto-download are disabled.
UTF-8 is explicit. Normal developer/CI Gradle entry point is the wrapper; Nix's
fetchDeps/setup-hook package mechanism uses the same pinned Gradle 9.8.0.

### Toolchain findings and source fixes

AGP's Maven AAPT2 ELF executable existed but raised ENOENT on direct invocation
in this Nix runtime. The SDK's Nix-patched AAPT2 ran (`2.20-13193326`); builds now
select it through `android.aapt2FromMavenOverride` at Gradle startup. The shell
sets GRADLE_OPTS; bootstrap/package pass the property explicitly. Build Tools
remains 36.0.0. AGP's experimental-option warning and configuration-visibility
deprecation remain visible. No dependency was excluded/replaced to pass D8.
[AGP's compatibility table](https://developer.android.com/build/releases/agp-9-4-0-release-notes)
supports the selected JDK, SDK and Build Tools; executed builds establish the
requested Gradle 9.8.0 combination.

Initial human Nix cache generation failed while AGP tried to create
`?/.android/analytics.settings` / AndroidLocationsBuildService. The inspected
nixpkgs cache-update sandbox supplies HOME `/homeless-shelter`; the package
previously supplied no writable Android user directory. `preConfigure` now creates
`$TMPDIR/totipo-android-user/.android`, sets the documented
[ANDROID_USER_HOME](https://developer.android.com/tools/variables) preferences
path, and passes `-Duser.home=$TMPDIR/totipo-android-user`. HOME is not repurposed.
Both package build and cache generation use this hook. Human cache generation
and final package validation subsequently passed.

The jail supplies `/bin/sh` and bash on PATH, but no `/usr/bin/env`. Bootstrap
uses `/bin/sh` with an explicit bash handoff; direct final invocation passed.
An initial nonexistent release unit-test task was removed in favor of AGP's
conventional debug local-test layer; release packaging is separately verified.
Only corrected final build invocations are counted as passes.

## Dependency boundary

Direct production dependency: `org.totipo:totipo-core:0.1.3`, from Maven Central.
Runtime transitive: `org.bouncycastle:bcprov-jdk18on:1.86`.

Published source tag `v0.1.3` is annotated tag
`f2cc94f426bdcdffb59c12440c47d660f248e39d`, peeling to release commit
`e2326aca5f5aac661d74925a30fcc57cd91014e8`. The GitHub release is published,
neither draft nor prerelease. Core POM/module/JAR GETs from Central succeeded;
Java's stale README "pending" wording does not override those actual release
bytes/tag or desktop's matching 0.1.3 consumer pin.
The Java release targets Totipo v1/r18 at spec commit
`4623a7e1718e23504903096c92332597057bd8f0`, distinct from current spec HEAD
inspected above. Provenance is also recorded in `TOTIPO_JAVA_DEPENDENCY.md`.

| Core artifact | Reviewed Central SHA-256 |
| --- | --- |
| JAR | `1e3db6ca15273941549dfa821b9f7dc9a00c6ad81e7e66642a1f09c114eeee19` |
| module | `f78bf8f63d52418d03c7feb8167591167929a513ed98edbd77f402e442684f22` |
| POM | `e3a4f22392479bde9941ffb7c0c744d1f77d77f25a60be268e046809f337b83f` |

`verifyMavenBoundary`, wired into check, examines debug/release compile/runtime
graphs and artifact identities. It requires direct external core 0.1.3 and core's
BC 1.86 runtime edge; rejects non-module project/file artifacts, unexpected Totipo
modules, obsolete coordinates and storage-nio. All four graphs passed. Runtime
contains exactly core and BC. Totipo is excluded from Google Maven; consumer
resolution uses Central. No Maven Local, composite/local-source/JAR/snapshot
fallback exists. Android does not inherit platform/application conformance from
Java's operation-scoped library claims.

## Repository design

One module `:app`, namespace/applicationId `org.totipo.android`. Production source
is Java. Kotlin plugin is not applied; AGP built-in Kotlin is selectively disabled
with `android.enableKotlin = false`. MainActivity extends platform Activity and
renders one TextView saying "Totipo Android bootstrap". Manifest contains a
launcher/exported Activity, platform theme/icon, no permissions or other components.

One JUnit 4.13.2 local smoke test compiles/loads public VaultSession. No otherwise
useless production abstraction, Robolectric, AndroidX application dependency,
Compose, AppCompat, Material, DI, navigation, coroutine or storage framework was
introduced. AGP's Kotlin/UTP/lint transitive tooling is build/test state, not product
architecture. Release is unminified and unsigned, retaining the real core/BC graph
for D8/package processing. Product/storage/Syncthing and security/lifecycle choices
are deliberately deferred.

## Reproducibility

Wrapper generated by managed Gradle 9.8.0 in an ignored isolated seed project.
Actual wrapper JAR and published Gradle checksum endpoints match the pins above.
`tools/verify-wrapper.py` checks pins without rewriting them. Normal bootstrap
requires existing locks/metadata and runs strict check/debug/release. Explicit
`--refresh-dependencies` writes locks and SHA-256 metadata, prints changes and
requires review. Normal builds do not auto-repair trusted inputs.

Strict locks cover app resolvable configurations (98 entries) and root AGP build
classpath (113 entries); check explicitly verifies the root build classpath too.
Verification metadata: 203 components, 355 artifacts, metadata verification
enabled, one SHA-256 per artifact and no wildcard trust. No dynamic dependency
versions or snapshot dependencies were found. Publisher-byte comparisons for
AGP JAR/module, core JAR/module, BC JAR/POM and JUnit JAR/POM all matched;
`M0_REVIEWED_DEPENDENCY_HASHES.json` retains exact reviewed URLs/hashes. Core/BC
also match previously reviewed desktop pins. This is inventory/TLS publisher-byte
review, not an independent signature audit of every dependency.

Configuration cache is disabled: custom verification resolves configurations at
execution; no cache-compatibility claim is made. Build cache remains available
for iteration and was disabled for forced offline checks. Ordinary/forced offline
builds preserved lock/verification/flake inputs exactly. Gradle internally used
its task graph cache during metadata writing; that does not establish normal
configuration-cache support.

### Nix dependency cache and package

Human-generated `package-deps.json` SHA-256:
`b6a947d036ca916f34f255819a6b6f611617f79af49e68cdf4c4cc802040afd6`.
338 Maven artifacts: all 312 entries represented in Gradle verification metadata
match its SHA-256 exactly; all 26 additional POM observations matched fresh
publisher GETs. No additional unverified binary/module substitution appeared.
AGP 9.4.0, core 0.1.3 and BC 1.86 are present; no storage-nio or other Totipo module.
`M0_NIX_CACHE_REVIEW.json` records this review. No cache hashes were fabricated
or hand-edited. Cache generation preserved Gradle locks/metadata and flake.lock.

Origins: Google Maven, Google master index/Play SDK snapshot, Maven Central and
Gradle tooling API metadata. Lint's repo1/Gradle metadata requests are frozen
lookup data, not added application repositories or dynamic dependencies; a newer
Gradle milestone mentioned in tooling metadata does not substitute Gradle 9.8.0.
Lint's BC 1.80.2 is tooling-only; production BC remains 1.86.
Android's cache compresses Central at `/maven2` with `org/totipo#...` keys. The
package readiness guard was corrected for this actual layout; no cache hash changed.

`package.nix` uses pinned JDK/SDK/Gradle and nixpkgs fetchDeps/setup hook with strict
verification. Actual package builds receive repository responses from the pinned
cache in the Nix sandbox, without live Maven downloads. This cold-cache proxy
workflow differs from the developer Gradle --offline repeat. Source filtering
excludes `.git`, generated/local state and review files. Package tasks assemble
release and run check. Output contains only the unsigned release APK under
`share/totipo-android/`; installation checks validate ZIP integrity, bootstrap/core/
BC DEX presence, storage-nio absence and absence of APK/JAR signing. No production
key/signing/AAB/publication infrastructure exists.

## Validation

### Agent-executed non-Nix validation

Exact read-only Git commands: `git branch --show-current`, `git rev-parse HEAD`,
`git status --short`, `git ls-tree -r --name-only HEAD`; initial state above.
`java -version`, `gradle --version`: managed JDK 17.0.20.1 and Gradle 9.8.0.
`bash tools/verify-m0-toolchain.sh` passed in the relaunched jail, including SDK
properties/adb; current GRADLE_OPTS points to managed SDK AAPT2. Python urllib
GitHub/Central/catalog reads, SHA-256/cache/inventory comparisons passed as recorded.

Successful intentional dependency generation:

```sh
./gradlew --no-daemon :app:dependencies --write-locks --write-verification-metadata sha256
./gradlew --no-daemon --no-configuration-cache --warning-mode all "-Pandroid.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/36.0.0/aapt2" check :app:assembleDebug :app:assembleRelease --write-locks --write-verification-metadata sha256
./gradlew --no-daemon --no-configuration-cache "-Pandroid.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/36.0.0/aapt2" buildEnvironment --write-locks --write-verification-metadata sha256
```

Final generation/build passed; buildEnvironment generated root buildscript lock.
Initial missing-test-task/AAPT2 failures were diagnosed/corrected, not counted as
passes. No protocol/dependency workaround was used to pass D8.

Successful normal and forced offline verification:

```sh
./bootstrap-m0.sh
GRADLE_OPTS="-Dorg.gradle.project.android.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/36.0.0/aapt2" ./gradlew --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks --dependency-verification=strict --warning-mode all clean check :app:assembleDebug :app:assembleRelease
python3 tools/verify-wrapper.py
python3 tools/verify-apk.py app/build/outputs/apk/debug/app-debug.apk
python3 tools/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk --unsigned
bash -n bootstrap-m0.sh tools/verify-m0-toolchain.sh
git diff --check
```

Normal: 89 actionable tasks, 3 executed, 86 up-to-date. Forced offline: 91 tasks,
all 91 executed, no build-cache reuse. Local test XML: one test, no failures/errors/
skips. Lint: "No issues found." Bytecode headers: major 61/non-preview. Dependency
boundary and both APK inventories passed. SHA-256 snapshots show no changes to
locks, verification metadata or flake.lock during normal/offline verification.

Temporary user-directory fix also passed an agent non-Nix isolated validation,
with Python TemporaryDirectory, fresh Java user.home/ANDROID_USER_HOME and existing
managed dependency cache:

```sh
./gradlew -Duser.home=<temporary-directory> -Pandroid.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/36.0.0/aapt2 --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks --dependency-verification=strict :app:assembleRelease check
```

73 tasks, all 73 executed; no metrics/directory initialization error. Trusted
inputs unchanged. Final APK inspection still passed. No Nix command was run by
the agent at any point.

### Human-operated Nix validation

| Exact command | Reported result |
| --- | --- |
| `nix flake check --no-build path:.` | Initial environment evaluation passed; user reported no errors |
| `nix develop path:. --command bash tools/verify-m0-toolchain.sh` | Initial shell/toolchain checks passed; user reported no errors, re-entered shell and relaunched agent |
| `nix run path:.#update-package-deps` | Initial Android preferences failure; passed after package temporary-home fix; generated cache independently reviewed |
| `nix develop` / updated shell re-entry | User reported success; relaunched agent independently confirmed SDK/JDK and forwarded AAPT2 property |
| `nix flake check path:.` | PASS; supplied output only warns of omitted incompatible systems |
| `nix build path:.` | PASS; supplied output shows no error |
| `nix build --rebuild path:.` | PASS; supplied output shows no error |

Final flake check omitted aarch64-darwin, aarch64-linux and x86_64-darwin. Evidence
is scoped to x86_64-linux; no all-systems/macOS/ARM package pass is claimed.

The user supplied matching output from `sha256sum "$m0_apk"` before and after
forced rebuild, and a passing
`python3 tools/verify-apk.py "$m0_apk" --unsigned`, where m0_apk is
`result/share/totipo-android/totipo-android-0.0.0-dev-unsigned.apk`.
SHA-256: `5f06adae74c042461899b4889b1530899c5b59dd44775b213ec013b5c9daf5c8`.

The agent can read the result link target
`/nix/store/5ip7lsncadp9g7znfqas45a6jz3nx0s0-totipo-android-0.0.0-dev`, but that
new store output is not mounted into the current jail. Packaged-APK verification
and hash evidence above are therefore **human-operated**, not agent file inspection.
This establishes the reported Nix package/rebuild boundary, not equality between
all development/Nix build modes or unrelated future inputs.

### Human-operated Android device smoke

PASS based on the user's report: after replacing the USB cable, the application
launched on a physical Android device and displayed "Totipo Android bootstrap".
The user identified the cable as the cause of the earlier absent USB device.
The exact installation command, device model and Android version were not supplied;
no specific values are inferred. This is human-operated installation/launch/rendering
evidence, not an agent device test or crypto/filesystem/storage qualification.

During troubleshooting, the agent observed an empty `adb devices -l` and confirmed
`/dev/bus/usb` and `/run/udev` are absent inside jailed-codex. Manual USB adb/install
commands should run from an ordinary terminal in the managed Nix shell outside
the jail. Host `lsusb` initially showed no phone, consistent with the subsequently
reported cable problem. No jail permissions or host NixOS configuration were changed.

### Remote CI

Workflow structure prepared: pinned checkout/Nix installer, Nix-defined wrapper/
unit/check/lint/debug/release validation, forced offline repeat, APK inspection,
Nix check/build/rebuild and unchanged-input check. No emulator, signing,
publication or automatic dependency refresh. **Remote CI has not run**; workflow
configuration and local/human validation are not a remote CI pass.

## Artifacts

| Artifact | SHA-256 | Evidence |
| --- | --- | --- |
| `app/build/outputs/apk/debug/app-debug.apk` | `302e135232b2632c293d09ec085993a2ddb83cea4445e3b44627bdcd8dcc0760` | Agent inspection after forced offline build |
| `app/build/outputs/apk/release/app-release-unsigned.apk` | `b561cdcc04e84d320d554cce48c2d526fc7f6ae217892f4b7e8029666d69963b` | Agent inspection, including isolated preferences validation |
| `result/share/totipo-android/totipo-android-0.0.0-dev-unsigned.apk` | `5f06adae74c042461899b4889b1530899c5b59dd44775b213ec013b5c9daf5c8` | Human inspection; identical before/after Nix rebuild |

Local Gradle and Nix APK hashes differ; cross-mode byte identity is not claimed.
The local APK includes AGP Git revision metadata; the Nix source filter omits
`.git`. No full cross-mode ZIP comparison was performed. Nix's own normal/rebuild
APK identity is the demonstrated reproducibility boundary. Generated APKs/result
are ignored and are not repository review inputs or releases.

## Known limitations

- No Android storage provider or Syncthing integration.
- No application-conformance claim, Android runtime/filesystem qualification,
  independent interoperability or product crypto/runtime test.
- No production signing, release/tag/publication, AAB or Play distribution.
- Minimal bootstrap UI only; no vault, credentials, TOTP, clipboard, background,
  biometric, permission or lifecycle-security behavior.
- No real-application UI toolkit, architecture, DI, navigation, preferences or
  storage-provider choice. Those remain deferred to later milestones.
- Package evidence scoped to x86_64-linux; SDK AAPT2 override remains experimental
  in AGP and must be revalidated on future toolchain changes.
- Remote CI unrun; device smoke establishes bootstrap launch/rendering only.

## Final Git state

`git status --short`:

```text
 M .gitignore
 M README.md
 M flake.nix
?? .gitattributes
?? .github/
?? LICENSE
?? TOTIPO_JAVA_DEPENDENCY.md
?? VERSION
?? app/
?? bootstrap-m0.sh
?? build.gradle.kts
?? buildscript-gradle.lockfile
?? gradle.properties
?? gradle/
?? gradlew
?? gradlew.bat
?? package-deps.json
?? package.nix
?? review/
?? settings.gradle.kts
?? tools/
```

`git diff --stat` (tracked files; excludes the 29 untracked review files):

```text
 .gitignore |   3 ++
 README.md  | 137 ++++++++++++++++++++++++++++++++++++++++++++++++++++++++-----
 flake.nix  |  73 ++++++++++++++++++++++++++++----
 3 files changed, 193 insertions(+), 20 deletions(-)
```

`git diff --check`: PASS, no output. `git diff --cached --stat`: empty.
Branch remains main; HEAD remains `0db3ff119601d9002937f9b8e6339b81f3c8668a`. Nothing staged, committed,
tagged, released or pushed by the agent. All implementation and generated
reproducibility inputs are ready for manual review. Documentation-only completion
edits are outside the Nix package source filter; validated build inputs remain
unchanged after final human package validation.
