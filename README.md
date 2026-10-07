# Totipo Android

M0 repository/bootstrap build is validated, unreleased (`0.0.0-dev`). One Java
`:app` module displays a platform Activity with a bootstrap label. It consumes released
`org.totipo:totipo-storage-nio:0.1.4` from Maven Central, transitively packaging
core 0.1.4 and Bouncy Castle 1.86. The production local replica uses private NIO
mode below Android no-backup storage, controlled by one exclusive root owner.
Production SAF code now observes read-only bounded transport snapshots; no
synchronization, import, export, or product functionality is implemented. The
provider tree must already be selected and authorized by a future product flow.
See the [M1E report](review/M1E_READ_ONLY_PROVIDER_SNAPSHOT_REPORT.md).
See [dependency provenance](TOTIPO_JAVA_DEPENDENCY.md) and the
[M0 evidence report](review/M0_ANDROID_BOOTSTRAP_REPORT.md) for validated status.

## Managed development environment

Use the repository's pinned Nix environment:

```sh
nix develop path:.
jailed-codex
```

Or use `.envrc` with `direnv allow`. After changing the shell, exit/re-enter it
and relaunch jailed-codex. `path:.` includes uncommitted/untracked files during
M0 review; nothing needs to be staged for Nix validation.

The default shell includes jailed-codex for development. For the lightweight
Android build/CI environment without agent tooling, use `nix develop path:.#ci`.
Both shells share the Android/JDK environment and Python; Nix Gradle remains
available for the bootstrap toolchain verifier. Ordinary builds use `./gradlew`.

The flake provides JDK 17, Gradle 9.8.0, Python and a composed Android SDK from
its existing locked nixpkgs input: API 37.0, Build Tools 36.0.0, platform tools
37.0.1, command-line tools 22.0. Compile/target SDK are 37; min SDK is 26.
AGP is 9.4.0. No Android Studio, emulator, images, NDK or CMake are required.
The flake accepts the Android SDK license and allows only the selected SDK
package names as unfree. `ANDROID_HOME` and `ANDROID_SDK_ROOT` point to the
same Nix-managed SDK; `JAVA_HOME` is explicitly JDK 17 and forwarded into jail.
Do not use a host SDK/JDK, sdkmanager to fill gaps, or `local.properties`.

The shell supplies `GRADLE_OPTS` selecting Nix's patched SDK AAPT2. AGP's Maven
AAPT2 ELF binary does not start in this Nix environment. The bootstrap script
and package also pass this selection explicitly. AGP labels the option
experimental; its warning remains visible. Build Tools version stays 36.0.0.

## Build and install

Use the wrapper as the ordinary developer/CI entry point:

```sh
./bootstrap-m0.sh
./gradlew --no-daemon check :app:assembleDebug :app:assembleRelease
```

The forced offline repeat remains a manual M0/toolchain qualification check:

```sh
./gradlew --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks clean check :app:assembleDebug :app:assembleRelease
```

`check` includes the debug JVM smoke test, Android lint and debug/release
Maven-boundary validation. Release is deliberately unminified and unsigned, so
the real NIO/core/BC graph must survive D8 and packaging. Configuration cache is
disabled because the custom verification action resolves configurations at
execution; no cache compatibility is claimed. Build cache remains available for
ordinary iteration. Toolchain and SDK auto-download are disabled, UTF-8 is explicit.

Outputs:

- `app/build/outputs/apk/debug/app-debug.apk` (ordinary local debug signing).
- `app/build/outputs/apk/release/app-release-unsigned.apk` (no signing configuration).
- Unit test results: `app/build/test-results/testDebugUnitTest/`.
- Lint report: `app/build/reports/lint-results-debug.html`.

With a connected Android device and device debugging authorized, use an ordinary
terminal in the Nix development shell, outside jailed-codex. The jail does not
expose `/dev/bus/usb`; start the adb server from the ordinary terminal.

```sh
./gradlew installDebug
```

Launch **Totipo Android Bootstrap** and confirm that the bootstrap label renders.
This optional human smoke test is recorded separately; it does not qualify vault,
crypto, Android filesystem or application behavior. No emulator/instrumentation
suite is part of M0.

## Intentional dependency refresh

Normal builds never regenerate trusted inputs. To intentionally populate/update
the locks and SHA-256 metadata, within the managed shell:

```sh
./bootstrap-m0.sh --refresh-dependencies
```

Review all changed lockfiles and `gradle/verification-metadata.xml`, including
plugin/build/test dependencies. Generated hashes are observations, not proof of
publisher identity. Independently compare expected artifacts to publisher bytes
and existing reviewed pins. No dynamic versions, Maven Local, source/composite
substitution or automatic metadata repair is configured. Wrapper pins are checked
with `python3 tools/verify-wrapper.py` before bootstrap validation. Updating the
wrapper is a separate deliberate edit/generation and checksum review operation.

## Nix package

M0 package scope is x86_64-linux. It uses the same pinned JDK, SDK and Gradle
9.8.0, strict dependency verification and nixpkgs Gradle `fetchDeps` MITM cache.
Actual package builds obtain Maven responses only from the pinned download cache
inside the Nix build sandbox; they do not fetch live Maven bytes.
The derivation creates a writable temporary Android preferences directory and
sets Java `user.home` and `ANDROID_USER_HOME` before Gradle starts. This also
applies to cache generation; no real user home or repository signing state is
required. A cold Nix build uses cached proxy responses rather than Gradle's populated user cache, so
its Gradle invocation differs from the developer `--offline` repeat above.

After an intentional dependency change, the human operator runs:

```sh
nix run path:.#update-package-deps
```

This generates `package-deps.json`; review the resulting origins, versions and
hashes. The review candidate cache was generated by the human operator and
reviewed against Gradle verification metadata and publisher POM bytes. Cache
refresh runs release assembly and check with strict verification; it does not
write Gradle locks or metadata. Then validate:

```sh
nix flake check path:.
nix build path:.
nix build --rebuild path:.
```

Human-operated flake check, package build and forced rebuild passed on
x86_64-linux; the unsigned APK hash matched before and after rebuild. Remote
CI and optional device evidence are recorded separately in the M0 report.

The package installs only
`result/share/totipo-android/totipo-android-0.0.0-dev-unsigned.apk`. Installation
checks validate ZIP integrity, bootstrap/NIO/core/BC DEX presence, debug probe/test exclusion
and absence of APK/JAR signing. No signing secret, AAB, Play or publication
infrastructure exists. Debug signing keys are local generated development state.

Push/PR CI uses the lightweight `#ci` shell for one bootstrap build, APK inspection
and an unchanged-input check. Nix flake/package checks run on main pushes or
manual workflow dispatch. Forced offline builds and `nix build --rebuild path:.`
remain manual qualification actions. CI does not refresh dependency state or
publish releases. The revised workflow has not yet run remotely.

## Limitations

There is no synchronization or Syncthing integration yet. The private
canonical local replica boundary is defined in
[M1D](review/M1D_PRODUCTION_LOCAL_REPLICA_DESIGN_REPORT.md); transport reconciliation
awaits Java candidate-validation integration. No real UI toolkit, DI, navigation or lifecycle
security architecture is implemented. There is no vault/TOTP UI, permissions,
background behavior,
production signing or application-conformance claim. Build evidence does not
establish Android crypto/runtime/filesystem qualification or interoperability.
