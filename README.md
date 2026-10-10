# Totipo Android

Unreleased (`0.0.0-dev`). The platform Android shell creates, unlocks and explicitly
locks an app-private local Totipo vault in no-backup storage. One Application-owned
`AndroidVaultController` keeps one live foreground coordinator/session across Activity
recreation. Activities render immutable detached token descriptors, observation status,
and unresolved/conflict/integrity diagnostics. Refresh requests local observation without
closing or reauthenticating the session. Vault work uses one bounded application worker;
UI callbacks return to the main thread. Credentials are not persisted and operation-owned
mutable buffers are cleared on completion, as best effort rather than guaranteed JVM erasure.
Empty-password create/unlock/Join requires explicit confirmation.

Token management supports **Add**, **Edit**, **Delete**, **Resolve**, and **Show code**.
Live rows keep Show code as the default action and offer Edit/Delete in the overflow menu.
Edit changes issuer/account while retaining the hidden authenticator setup; **Change setup**
is deferred. Delete requires confirmation and authors a Totipo tombstone, retaining immutable
encrypted history rather than securely erasing it. Conflict rows offer Resolve instead of
Show code. Resolve requires choosing one complete Alternative, including Deleted where present;
there is no automatic winner or field combination. Detailed **Combine** is deferred.
Edit/Delete/Resolve author only the local canonical store through the existing session.
Use **Publish local changes** separately. They perform no provider Import or Publish.
Changed token/conflict bases require review and a fresh user choice; no silent retry/rebase.
Lock, dismissal and Activity interruption retire pending forms; sensitive setup is never
saved through Android widget state or Bundle.

Production consumes released Java NIO/core 0.2.0 and Bouncy Castle 1.86 directly
from Maven Central, targeting Totipo Vault Format v1/r19. One app-private local canonical
store has one persistent `NioStoreComposition.coordinatedDelegate(root, new NioDurability())`
wrapped by `CoordinatedPrivateStore`, whose fair gate owns whole-root exclusivity for
session calls and bridge batches. No second delegate or independent writer is allowed.

Explicit **Import changes** and **Publish local changes** exchange immutable token objects
through one bounded provider I/O lane. Every operation first reads the provider's root
immutable VAULT and compares its structural `VaultId` with the currently open session.
Import and Publish require the selected folder to contain the same immutable VAULT. Missing, malformed,
unavailable, different or duplicate VAULT candidates block object mutation; duplicate
candidates are conservatively ambiguous even if identical. No password/KDF is used for
provider recognition. Folder READY means transport accessibility, not matching identity.
Only explicit Initialize may create an absent provider VAULT; only explicit Join may
enroll an authenticated provider VAULT locally. Totipo never replaces or repairs VAULT. VAULT is immutable;
password change and migration are absent. Cross-vault migration is separate future work.

Inbound import retains the same authenticated session, Java object validation and exact
ciphertext, exclusive local publication, then refresh after releasing the store gate.
Outbound publication retains bounded fresh preflight, create-only canonical names,
immediate read-back, fresh authenticated postflight and explicit manual retry.
There is no automatic polling, background synchronization, cloud SDK or Internet permission.
Java's existing-token-data creation veto is presented without bypass.

Manual enrollment, otpauth enrollment, reveal/copy, vault shell, explicit lock and token
list behavior remain. Credentials are not persisted. Empty-password create/unlock/Join requires
confirmation. Backgrounding/rotation does not lock. Java Flow requires API 30 for vault
operations; minSdk remains 26 and older devices receive the unsupported-runtime message.

The launcher uses canonical adaptive/round/density resources copied unchanged from
`totipo-spec/design/icons` at r19 commit `cdb4e91be1c6d3704874b2b92457ffe7be5e9084`.
Master: `design/icons/totipo-app-icon.svg`. Approved artwork gradient colors are
`#46FB70`, `#08D267`, `#028B55`, `#026344`; no UI palette changes are implied.
[Branding provenance](branding-provenance.json) records every copied source path/hash.
No monochrome variant is supplied; none is invented. No icon-generation dependency is added.

See [Java provenance](TOTIPO_JAVA_DEPENDENCY.md) and the
[0.2.0/r19 reconciliation report](review/JAVA_0_2_0_R19_ANDROID_RECONCILIATION_REPORT.md)
for current evidence. Historical M1/M3 reports remain unchanged.

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

Do not evaluate authentication performance using the ordinary debuggable APK on
the tested Pixel 6a runtime. Its ~20.5-second KDF is not representative of the
signed non-debug release's natural install compilation. See the
[M1M release qualification](review/M1M_RELEASE_AUTH_PERFORMANCE_QUALIFICATION_REPORT.md)
for real create/unlock timings and the direct ADB installation scope.

Launch **Totipo**. Qualification uses disposable isolated app-private roots and a designated disposable
provider fixture; no user real vault is modified. Standalone harnesses add no app dependency.

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

The cache also pins lint's Google Play SDK Index at
`https://dl.google.com/play-sdk/index/snapshot.gz`. Google updates this unversioned
URL, so a fresh runner can fail with a `snapshot.gz` fixed-output hash mismatch
even when application dependencies have not changed. Verify the downloaded
snapshot and update its `gz` hash under `https://dl.google.com` →
`play-sdk/index/snapshot` in `package-deps.json`. Update the reviewed whole-file
SHA-256 in `OtpAuthSupplyChainGuardTest` as well, then rerun the checks above.
This refresh can recur whenever Google replaces the snapshot.

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

M3C adds two explicit same-vault bootstrap actions. **Join existing vault** authenticates
an exact provider immutable VAULT through Java before installing those same bytes into
an empty local canonical store, then opens normally. Wrong passwords leave local VAULT
absent. A fresh provider recheck precedes installation; local orphan token evidence blocks it.
**Initialize sync folder** publishes the exact open local canonical VAULT create-only,
verifies readback and fresh provider evidence, and establishes the exact objects-v1 directory.
Provider token evidence without a VAULT vetoes initialization. A verified VAULT is retained
if directory initialization fails; an explicit retry can create only the missing directory.
Neither operation replaces VAULT, changes a password or root, performs migration or copies
across vaults. Different local/provider VaultIds are never reconciled automatically.
Join does not import tokens; Initialize does not publish tokens. Use **Import changes** and
**Publish local changes** separately, each with its own fresh matching-VAULT preflight.
No bootstrap runs on startup, unlock, folder selection or access restoration.
The next task is to **resume/rerun M3D real Syncthing qualification from Scenario E onward**,
after committing this milestone with new Android HEAD and APK identities. Retain A–D as
historical prior-run evidence only and collect fresh E–M evidence. The old incomplete
M3D report remains incomplete. See the [token lifecycle report](review/ANDROID_TOKEN_LIFECYCLE_REPORT.md).
There is no background sync, Syncthing integration, biometric unlock, inactivity lock timer,
field-by-field conflict combination, DI, AndroidX, Compose, service or WorkManager dependency. Production signing
and universal Android filesystem/runtime or interoperability qualification are not claimed.
JVM/build evidence complements the isolated physical-device qualification recorded in the report.
