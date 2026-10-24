# Android development and qualification

Build, editor, dependency and local qualification procedures live here. Run all
commands from the repository root. Start with
the [README](../README.md) for installation and the [user guide](USER_GUIDE.md)
for app workflows. The [agent policy](../AGENTS.md) owns the qualification ladder;
the [release procedure](../release/README.md) owns production signing and publishing.

## Managed development environment

Nix is the supported command-line development prerequisite. Use the repository's
pinned Nix environment, directly or through direnv:

```sh
nix develop
jailed-codex
```

Or use `.envrc` with `direnv allow`. After changing the shell, exit/re-enter it
and relaunch jailed-codex. Local pre-commit Nix qualification uses the Git-aware
working tree: unstaged modifications to tracked files participate. For each new
intended milestone file, including its review report, the agent may run
`git add --intent-to-add <file>` to establish source membership only, then require
`git diff --cached --quiet` to pass. Existing modified tracked files remain
unstaged. Staging actual file contents requires explicit user authorization.
Leave intent-to-add entries in place for human review/commit. Ignored/untracked
build, cache and local-secret state must remain outside Git source membership.
Use explicit `path:` sources only for intentional raw/non-Git paths, such as
generated or exported source. See [agent policy](../AGENTS.md#local-nix-source-membership).

The default shell includes jailed-codex for development. For the lightweight
Android development environment without agent tooling, use `nix develop .#ci`.
Both shells and jailed-codex share the same Nix Gradle/JDK/SDK derivations and
Python. All supported command-line builds use `gradle` in this environment.

The flake provides JDK 17, Gradle 9.8.0, Python and a composed Android SDK from
its existing locked nixpkgs input: API 37.0, Build Tools 36.0.0, platform tools
37.0.1, command-line tools 22.0. Compile/target SDK are 37; min SDK is 26.
AGP is 9.4.0. No Android Studio, emulator, images, NDK or CMake are required.
The flake accepts the Android SDK license and allows only the selected SDK
package names as unfree. `ANDROID_HOME` and `ANDROID_SDK_ROOT` point to the
same Nix-managed SDK; `JAVA_HOME` is explicitly JDK 17 and forwarded into jail.
Do not use a host SDK/JDK, sdkmanager to fill gaps, or `local.properties`.

The shell supplies `GRADLE_OPTS` selecting Nix's patched SDK AAPT2. AGP's Maven
AAPT2 ELF binary does not start in this Nix environment. The package also passes
this selection explicitly. AGP labels the option
experimental; its warning remains visible. Build Tools version stays 36.0.0.

### Helix

The default development shell includes `hx` and the Android-aware `jdtls` source
bridge from [java-dev-tools](https://github.com/ingon/java-dev-tools), using its
[`mkAndroidEditor` integration](https://github.com/ingon/java-dev-tools/blob/d18e1767593b3ac16da6c76a8f36fed36ac4c807/INTEGRATION.md#android-instead-of-plain-java).
The lightweight `ci` shell keeps its existing build tools.

Reload direnv or re-enter `nix develop`, then start Helix from that shell:

```sh
hx --health java
hx app/src/main/java/org/totipo/android/MainActivity.java
```

Helix's built-in Java configuration uses the shell's `jdtls` command. The toolkit
owns Android startup settings, its Gradle model adapter and a workspace per
checkout; no project-local `.helix` files or launcher override are needed.
`JDTLS_WORKSPACE` selects a different workspace when needed.

JDTLS runs on a private JDK 21 while `JAVA_HOME`, command-line Gradle and Android
compilation retain JDK 17. Buildship discovers pinned Gradle 9.8.0 through
`GRADLE_HOME` and uses this SDK's patched AAPT2. The editor model selects main/debug
sources and their JVM tests, locked dependencies, SDK jars and generated debug R
classes. IDE output stays under `app/build/eclipse`; release and device-test
sources are outside this model. Normal builds do not load the editor adapter.

Verify actual app/SDK/dependency navigation; `hx --health java` only establishes
server discovery. Optional dependency source-archive downloads remain disabled
until their checksums are reviewed under the existing strict verification policy.
Binary dependencies still resolve and verify normally. Generated Eclipse/JDTLS
state is ignored. To update the toolkit deliberately, the human runs
`nix flake update java-dev-tools`, reviews the new revision and unchanged unrelated
pins, then reloads the shell and repeats editor smoke and required qualification.

## Build and install

Follow the [Qualification ladder](../AGENTS.md#qualification-ladder) for interactive
agent work: full qualification at stable boundaries, focused checks during iteration.
Docs outside package/check inputs need only documentation validation.

Use Nix-provided Gradle in the active managed shell. The full command is the
ordinary baseline/final checkpoint for product milestones:

```sh
gradle check :app:assembleDebug :app:assembleRelease
```

After final normal PASS, run the strict offline clean gate once at the stable
boundary, followed by the ladder's final artifact and affected device checks:

```sh
gradle --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug :app:assembleRelease
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
gradle installDebug
```

Do not evaluate authentication performance using the ordinary debuggable APK on
the tested Pixel 6a runtime. Its ~20.5-second KDF is not representative of the
signed non-debug release's natural install compilation. See the
[M1M release qualification](../review/M1M_RELEASE_AUTH_PERFORMANCE_QUALIFICATION_REPORT.md)
for real create/unlock timings and the direct ADB installation scope.

Launch **Totipo**. Qualification uses disposable isolated app-private roots and a designated disposable
provider fixture; no user real vault is modified. Standalone harnesses add no app dependency.

## Intentional dependency refresh

Normal builds never regenerate trusted inputs. To intentionally populate/update
the locks and SHA-256 metadata, within the managed shell:

```sh
gradle --no-daemon --no-configuration-cache --dependency-verification=strict --warning-mode=all buildEnvironment :app:dependencies --write-locks --write-verification-metadata sha256
gradle --no-daemon --no-configuration-cache --dependency-verification=strict --warning-mode=all check :app:assembleDebug :app:assembleRelease --write-locks --write-verification-metadata sha256
```

Review all changed lockfiles and `gradle/verification-metadata.xml`, including
plugin/build/test dependencies. Generated hashes are observations, not proof of
publisher identity. Independently compare expected artifacts to publisher bytes
and existing reviewed pins. No dynamic versions, Maven Local, source/composite
substitution or automatic metadata repair is configured. After review, run the
normal and strict qualification commands above. Update the Nix dependency-response
cache with the human-operated procedure below only for a deliberate input change.

## Nix qualification and package

The normal developer, human and CI qualification gate on x86_64-linux is:

```sh
nix flake check
```

`checks.android` and `packages.default` reference the same `androidPackage`
derivation. Flake check includes the actual Android package build/test/check work:
debug/release assembly, JVM tests, lint, strict dependency verification,
Maven-boundary checks, debug APK verification with `--debug-probe` and unsigned
release APK verification with `--unsigned --no-debug-probe`. The separate
`checks.release-tooling` runs the release behavior suite with its required source
and public signing identity; `checks.release-apps` checks packaged command entry points.
A second ordinary `nix build` is not required for qualification. The strict offline Gradle gate
above remains final local Android validation, outside the iteration loop.

Agent does not run Nix. After all package/check inputs (including `tools/` and
physical harness fixes) are frozen, request one final human `nix flake check`.
Included-input edits invalidate that result. Under the current source filter,
`AGENTS.md`, `README.md`, `MULTI_VAULT.md`, `TOTIPO_JAVA_DEPENDENCY.md`,
`docs/` and `review/` are excluded from Android qualification inputs;
documentation-only edits there do not require a new Nix gate. Inspect actual inputs
for every milestone. Do not request an additional ordinary `nix build`.

To materialize the package and create a `result` link for output inspection:

```sh
nix build .
```

For a deliberate release/reproducibility comparison only, a human may use
`nix build --rebuild .`. Compare the resulting unsigned APK bytes; this
bounded check does not prove universal reproducibility and is not a routine
milestone gate.

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
nix run .#update-package-deps
```

This generates `package-deps.json`; review the resulting origins, versions and
hashes. The review candidate cache was generated by the human operator and
reviewed against Gradle verification metadata and publisher POM bytes. Cache
refresh runs debug/release assembly and check with strict verification; it does not
write Gradle locks or metadata. Then validate:

```sh
nix flake check
```

The cache also pins lint's Google Play SDK Index at
`https://dl.google.com/play-sdk/index/snapshot.gz`. Google updates this unversioned
URL, so a fresh runner can fail with a `snapshot.gz` fixed-output hash mismatch
even when application dependencies have not changed. Verify the downloaded
snapshot and update its `gz` hash under `https://dl.google.com` →
`play-sdk/index/snapshot` in `package-deps.json`, then rerun the checks above.
This refresh can recur whenever Google replaces the snapshot.

The same applies to Google's Maven group index at
`https://maven.google.com/master-index.xml`. For a `master-index.xml` mismatch,
verify that the downloaded response is the expected XML metadata and update
`https://maven.google.com` → `master-index` → `xml` in `package-deps.json`. This unversioned index can change
independently of the application's pinned library versions; a metadata refresh does not require
upgrading those libraries.

Human-operated flake check, package build and forced rebuild passed on
x86_64-linux; the unsigned APK hash matched before and after rebuild. Remote
CI and optional device evidence are recorded separately in the M0 report.

The package installs only
`result/share/totipo-android/totipo-android-<VERSION>-unsigned.apk`.
The debug APK is built and verified but not installed into the package output.
Nix package installCheck is the authoritative full APK-policy boundary: one check
per variant, with release checked at its installed output path. Gradle `check`
does not invoke it again. After the strict host checkpoint, explicitly run:

```sh
python3 -B tools/verify-apk.py app/build/outputs/apk/debug/app-debug.apk --debug-probe
python3 -B tools/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk --unsigned --no-debug-probe
```

The verifier uses pinned SDK `apkanalyzer manifest print` and Python stdlib XML;
AAPT2 supplies resource names/configurations. It rejects inspection failures,
missing/duplicate/unknown declarations and unsupported output. Both variants must
have application ID `org.totipo.android`, versionName equal to reviewed `VERSION`,
versionCode equal to reviewed `VERSION_CODE`, min/target SDK 26/37 and compile SDK 37.
It uses the existing canonical source metadata parsers; APK/provenance fields cannot
supply their own expected version.

The final manifest permits only the unqualified `USE_BIOMETRIC` request. Permission
definitions, SDK-qualified variants and all other requests require policy review.
Release contains exactly MainActivity (MAIN/LAUNCHER) and OtpAuthEnrollmentActivity
(VIEW/BROWSABLE/DEFAULT `otpauth://totp`, SEND/DEFAULT `text/plain`), both enabled and
exported. No aliases, services, receivers, providers, extra filters, process changes,
provider authorities/grants or other manifest constructs are approved. Debug adds
exactly the four existing exported fixture/storage/NIO/authentication probe activities;
only the storage probe adds another MAIN/LAUNCHER filter. Unknown attributes also
require review. Names here are Android manifest entry points, not a DEX class inventory.

Both variants require `allowBackup=false`, the normal package process and an enabled
application. Release debuggable/testOnly must be false (absence uses Android defaults).
Debug requires debuggable=true and testOnly=false, plus its existing fullBackupContent
and extraction-rule exclusions. Essential launcher resources, density configurations,
adaptive layers and application labels remain checked. Source branding provenance
remains maintenance evidence bound to Git review; final verification no longer
rehashes source artwork or inventories ordinary production classes/retired APIs.
ZIP integrity, DEX presence and small debug/test exclusions remain. These checks do
not prove pixel equivalence, SAF grants, clipboard secrecy or installed device behavior.

`allowBackup=false` is not universal proof against OEM device-to-device migration;
[Android documents manufacturer-specific limits](https://developer.android.com/guide/topics/manifest/application-element#allowbackup).
App-private no-backup storage and historical device evidence remain separate guarantees.
Clipboard cleanup is best effort; manifest verification adds no device evidence.

No production signing secret, AAB or Play integration exists in the build. Protected release machinery
is documented in [Android releases](../release/README.md); the public production signing identity is committed; protected GitHub environment
provisioning is human-reported. Debug signing keys are local generated
development state.

Push/PR and manual-dispatch CI run one package-inclusive flake check on Linux,
followed by an unchanged-input check. The SHA-pinned Nix installer explicitly
accepts the repository's reviewed flake cache configuration; cache URLs, trusted
keys and signature policy are unchanged. Nix owns the build environment and
qualification; CI does not separately provision Java, Android or Gradle, refresh
dependency state or publish releases. Remote CI results belong to the relevant reports and workflow runs.

The two release-specific Nix apps are `prepare-release` before human review/commit
and `release-tag` after pushing main. `release-tag` validates, creates/pushes the
annotated tag and prints the exact source commit and protected workflow inputs. See [the release procedure](../release/README.md) for
consumed-tag code allocation, validation and the deliberate commit/approval boundaries.

## Persistent local qualification signing

`.qualification/signing/` holds persistent **LOCAL NON-PRODUCTION** qualification
state, per checkout by default. It is ignored and excluded from Git-aware Nix
source membership. It must survive `.gradle` and `.direnv` cleanup;
**`git clean -fdx` WILL delete it**. Back it up deliberately if preserving this
disposable qualification environment matters. Losing the private key means an
unrelated replacement cannot update the currently installed qualification product.
Never silently regenerate or rotate it. Another checkout targeting the same
provisioned device must explicitly import the **same** identity; there is no
HOME/XDG, Android debug-keystore, production-secret or historical-evidence fallback.
Production private keys remain forbidden anywhere in the repository/Nix inputs.
This narrow ignored-state exception applies only to local non-production qualification.
Detached row/security test keys and ephemeral release signing-smoke keys are separate.

Use the pinned environment and the single supported owner:

```sh
python3 -B tools/device/qualification_signing.py status
python3 -B tools/device/qualification_signing.py sign unsigned-helper.apk --aligned helper-aligned.apk --out helper-signed.apk
```

The generic signing interface supports SAF inbound and otpauth ingress helpers.
Their existing builders construct unsigned APKs; scenario procedures own install,
run and cleanup. The signer aligns with `-P 16`, disables v1/v4 as before, checks
the actual signer and alignment, and performs no device operations. Before any
product update/instrumentation installation, independently compare the installed
product certificate to the validated local identity. Installed APKs are compatibility
observations, never the local signing authority.

The strict loader requires current-user ownership, no symlink components, exact
0700 directory and 0600 file modes, PKCS12 with exactly one private-key entry,
and `identity.json` containing only `schema` (integer 1), `purpose`
(`totipo-android-local-qualification-signing`), nonempty `alias`, and lowercase
64-hex `certificateSha256`. Duplicate/unknown/missing fields fail. Every load
exports the actual public certificate and checks alias/fingerprint against metadata;
metadata cannot adopt another key. The committed production public fingerprint is
refused. Tool failures suppress subprocess output, including potentially private data.

Migration is explicit, byte-preserving and limited to the existing Wave 2A
certificate/alias. It never searches for keys:

```sh
python3 -B tools/device/qualification_signing.py migrate SOURCE_KEYSTORE SOURCE_PASSWORD_FILE
```

It refuses an existing/partial destination, verifies the expected source identity,
stages private files, verifies them, publishes `.qualification/signing`, checks
keystore/password byte equality, then removes only the two supplied private files.
Historical APKs/reports/directories remain evidence. A failure before publication
retains the source authority. A failure after publication retains the complete
verified canonical identity and may leave old private copies: stop and inspect,
then deliberately retire duplicates; never choose another identity or retry by
regenerating. An interrupted publication reservation is partial state and fails
closed. Do not run concurrent migration/signing or modify identity files during use.
Fresh provisioning is deliberately deferred; missing state always fails, and any
future provisioning must be explicit after deliberate device reprovisioning.
