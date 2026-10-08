# Totipo Android

Unreleased (`0.0.0-dev`). The platform Android shell creates, unlocks and explicitly
locks an app-private local Totipo vault in no-backup storage. One Application-owned
`AndroidVaultController` keeps one live foreground coordinator/session across Activity
recreation. Activities render immutable detached token descriptors, observation status,
and unresolved/conflict/integrity diagnostics. Refresh requests local observation without
closing or reauthenticating the session. Vault work uses one bounded application worker;
UI callbacks return to the main thread. Credentials are not persisted and operation-owned
mutable buffers are cleared on completion, as best effort rather than guaranteed JVM erasure.
Empty-password create/unlock requires explicit confirmation.

Production consumes released `org.totipo:totipo-storage-nio:0.1.5` from Maven Central,
transitively packaging core 0.1.5 and Bouncy Castle 1.86. The exclusive local replica owner
and coordinated private NIO domain serialize storage access. Credential-free inbound
immutable sync is available internally through `controller.sync(scan)`: the same live
session validates candidates, the bridge materializes them under its exclusive gate, then
requests Java refresh after releasing that gate. Refresh is asynchronous; Java exposes
no request/completion correlation. The UI says “Refresh requested”, never promises a
request-correlated completion.

Provider selection/persisted tree configuration, export/journal, VAULT transport
reconciliation, background synchronization, biometrics, inactivity locking and final
token/TOTP interaction UX remain absent. There is no product Sync button or automatic
provider synchronization. Explicit lock and process death are the only current lock
policies; backgrounding/rotation does not lock. Sensitive windows use Android's secure
window flag. Released Java Flow needs Android 11/API 30 or newer for vault operations;
older devices show an unsupported-runtime message (minSdk remains 26). This is a
product lifecycle milestone, not completed synchronization or a finished authenticator.

See the [M1J report](review/M1J_ANDROID_VAULT_SHELL_REPORT.md) for validation status,
[M1I report](review/M1I_COORDINATED_STORE_LIVE_SYNC_REPORT.md) for storage/sync evidence,
and [dependency provenance](TOTIPO_JAVA_DEPENDENCY.md). Historical M1H close/import/reopen
scaffolding remains test-only.

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

Launch **Totipo**. M1J requires a narrow physical-device create/unlock/rotate/lock
smoke after agent checks pass; exact commands and the destructive fresh-data reset
are in the [M1J report](review/M1J_ANDROID_VAULT_SHELL_REPORT.md). Use a disposable
credential and test vault. No emulator/instrumentation suite is added.

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
checks validate ZIP integrity, product Application/controller/UI and coordinated NIO/core/BC DEX presence, debug probe/test exclusion
and absence of APK/JAR signing. No signing secret, AAB, Play or publication
infrastructure exists. Debug signing keys are local generated development state.

Push/PR CI uses the lightweight `#ci` shell for one bootstrap build, APK inspection
and an unchanged-input check. Nix flake/package checks run on main pushes or
manual workflow dispatch. Forced offline builds and `nix build --rebuild path:.`
remain manual qualification actions. CI does not refresh dependency state or
publish releases. The revised workflow has not yet run remotely.

## Limitations

Inbound immutable foreground sync is an architectural primitive; complete synchronization
and Syncthing integration are not implemented. Detached descriptors are displayed without
rotating codes, reveal/copy, token add/edit or conflict repair. There is no provider
configuration, export, background lock timer or biometric unlock. No DI, AndroidX,
Compose, service, WorkManager or reactive dependency is introduced. Production signing
and application conformance are not claimed. JVM/build evidence complements the required
physical-device smoke; it does not establish universal Android runtime/filesystem or
interoperability qualification.
