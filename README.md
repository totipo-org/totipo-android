# Totipo Android

Current source version is recorded in `VERSION`; Android versionCode is recorded
in `VERSION_CODE`. Totipo creates, unlocks and explicitly locks an
app-private local vault in no-backup storage. After setup, the main screen contains
**Sync**, search, tokens and **Add token**, with **Lock**, **Manage sync folder** and
**Diagnostics** in overflow. Show code and Copy code retain their conceal/expiry
behavior. Diagnostics shows public vault/provider/build information, never passwords,
setup secrets, root keys or raw exception dumps.

Once a shared folder is bound, Totipo automatically attempts **Sync** after successful
unlock/open, after a meaningful return from the background, and after successful
**Add**, **Edit**, **Delete** or **Resolve**. Rotation and repeated active callbacks do
not trigger repeated attempts. Manual **Sync** retries at any time admission is free,
including after a provider outage. There is no routine Import/Publish decision.

Sync always **imports and observes before publishing**: it checks the bound folder's
immutable VAULT identity, imports remote evidence through the existing Java session,
waits for Java observation, and then uses fresh publication preflight/readback/postflight
checks. Blocking identity, provider, import or observation failures stop publication.
Java remains the merge/conflict authority. Real concurrent edits remain conflicts;
Resolve requires an explicit complete Alternative, including Deleted where present.
There is no automatic winner or field combination.

Local authoring succeeds independently of the subsequent Sync attempt. An unavailable
provider leaves the valid local change committed and shows **Changes not synced**.
A persisted local retry hint survives restart; immutable local evidence is the source
of publication decisions. A later foreground event, mutation or manual Sync retries.
Requests coalesce to one active Sync plus one pending request; failures never start a
retry loop. Successful Sync is quiet and confirms only the bound shared folder, never
receipt by other devices. **Syncthing remains the external transport** between folders.

Edit changes issuer/account while retaining the hidden authenticator setup; **Change
setup** is deferred. Delete requires confirmation and authors a Totipo tombstone,
retaining immutable encrypted history rather than securely erasing it. Live rows offer
Show code and Edit/Delete; conflict rows offer Resolve. Detailed **Combine** is deferred.
Changed token/conflict bases require review and a fresh user choice, with no silent
retry/rebase. Lock, dismissal and Activity interruption retire pending forms; sensitive
setup is never saved through Android widget state or Bundle.

One Application-owned `AndroidVaultController` retains the live coordinator/session
across Activity recreation. The existing bounded vault worker and provider I/O lane
serialize operations, with session/binding retirement and cancellation guards. Explicit
Lock and a fixed **15-minute user inactivity timeout** close the real Java VaultSession
and retire its in-memory vault/session keys. Touch/key interaction resets a monotonic
`elapsedRealtime` deadline; Sync, TOTP ticks and provider callbacks do not. Backgrounding
alone does not immediately lock, but background time counts. Foreground re-entry checks
expiration before presenting vault content. Rotation does not reset the deadline.

Optional **biometric unlock on this device** is available with API 30+ framework strong
biometrics. The vault password remains canonical and always works. Select **Enable
biometric unlock on this device** during password unlock; after the password succeeds,
a strong biometric crypto prompt protects a device-local encrypted reusable password
copy with a non-exportable AndroidKeyStore AES-GCM key. Every decryption requires a fresh
strong biometric operation; device PIN, pattern, device password and weak biometrics are
not accepted. The password fallback remains visible. Cancelled/failed enrollment leaves
the normally opened vault available and stores no credential. Enrollment and prompts
expire after 60 seconds or Activity interruption. **Disable biometric unlock** in overflow
removes both credential and Keystore key without locking or changing the vault/password.

The credential is in app-private no-backup storage, authenticated and bound to the exact
VaultId. Enrollment changes, key invalidation, or corruption may require password unlock
and re-enrollment. After process death the app starts locked; biometric reopening uses
the ordinary password-open path and normal automatic Sync.

Biometric convenience deliberately adds device-local attack surface: it stores a
device-encrypted reusable representation of the vault password. The authentication-gated
Keystore key cannot be exported, but successful biometric authorization lets Totipo
transiently recover the password required by the existing Java API. This is a usability
tradeoff, not a claim of security equivalence to the vault password. Plaintext is never
persisted, passwords are never converted to Java Strings, and transient mutable buffers
are wiped in finally blocks. Operation-owned mutable buffers are cleared on completion
as best effort rather than guaranteed JVM erasure. Empty-password create/unlock/Join
requires explicit confirmation.

Production consumes released Java NIO/core 0.2.0 and Bouncy Castle 1.86 directly
from Maven Central, targeting Totipo Vault Format v1/r19. One app-private local canonical
store has one persistent `NioStoreComposition.coordinatedDelegate(root, new NioDurability())`
wrapped by `CoordinatedPrivateStore`, whose fair gate owns whole-root exclusivity for
session calls and bridge batches. No second delegate or independent writer is allowed.

**Sync** exchanges immutable token objects through one bounded provider I/O lane.
The lower-level Import and Publish operations remain internal for qualification. Every operation first reads the provider's root
immutable VAULT and compares its structural `VaultId` with the currently open session.
Both phases require the selected folder to contain the same immutable VAULT. Missing, malformed,
unavailable, different or duplicate VAULT candidates block object mutation; duplicate
candidates are conservatively ambiguous even if identical. No password/KDF is used for
provider recognition. Folder READY means transport accessibility, not matching identity.
Only explicit Initialize may create an absent provider VAULT; only explicit Join may
enroll an authenticated provider VAULT locally. Totipo never replaces or repairs VAULT. VAULT is immutable;
password change and migration are absent. Cross-vault migration is separate future work.

Inbound import retains the same authenticated session, Java object validation and exact
ciphertext, exclusive local publication, then refresh after releasing the store gate.
Outbound publication retains bounded fresh preflight, create-only canonical names,
immediate read-back and fresh authenticated postflight. Manual Sync remains the retry fallback.
There is no automatic polling, background synchronization, cloud SDK or Internet permission.
Java's existing-token-data creation veto is presented without bypass.

Manual enrollment, otpauth enrollment, reveal/copy, vault shell, explicit lock and token
list behavior remain. Empty-password create/unlock/Join requires
confirmation. Backgrounding alone does not lock; elapsed inactivity does. Java Flow requires API 30 for vault
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
generated or exported source. See [agent policy](AGENTS.md#local-nix-source-membership).

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

Follow the [Qualification ladder](AGENTS.md#qualification-ladder) for interactive
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
[M1M release qualification](review/M1M_RELEASE_AUTH_PERFORMANCE_QUALIFICATION_REPORT.md)
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
`AGENTS.md`, `README.md` and `review/` are excluded from Android qualification inputs;
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
is documented in [Android releases](release/README.md); the public production signing identity is committed; protected GitHub environment
provisioning is human-reported. Debug signing keys are local generated
development state.

Push/PR and manual-dispatch CI run one package-inclusive flake check on Linux,
followed by an unchanged-input check. The SHA-pinned Nix installer explicitly
accepts the repository's reviewed flake cache configuration; cache URLs, trusted
keys and signature policy are unchanged. Nix owns the build environment and
qualification; CI does not separately provision Java, Android or Gradle, refresh
dependency state or publish releases. The revised workflow has not yet run remotely.

The two release-specific Nix apps are `prepare-release` before human review/commit
and `release-tag` after pushing main. `release-tag` validates, creates/pushes the
annotated tag and prints the exact source commit and protected workflow inputs. See [the release procedure](release/README.md) for
consumed-tag code allocation, validation and the deliberate commit/approval boundaries.

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
Join and Initialize remain deliberate setup operations under **Manage sync folder**;
Join is also reachable during initial setup. Join opens the vault and triggers ordinary
Sync. Initialize creates the provider VAULT/directory only; ordinary **Sync** then handles
token publication. No ordinary Sync auto-joins, bootstraps or replaces a vault.
A different provider VaultId blocks publication with a fixed message; use **Manage sync
folder** to choose the intended folder. No replacement or merge is offered.
No bootstrap runs on startup, unlock, folder selection or access restoration.
Historical M3D reports and checkpoints record the earlier explicit Import/Publish UX
and remain unchanged. The daily-driver milestone's current validation and outstanding
human gates are recorded in [the daily-driver UX report](review/ANDROID_DAILY_DRIVER_UX_REPORT.md).
There is no background sync, Syncthing integration, biometric unlock, inactivity lock timer,
field-by-field conflict combination, DI, AndroidX, Compose, service or WorkManager dependency. Production signing
and universal Android filesystem/runtime or interoperability qualification are not claimed.
JVM/build evidence complements the isolated physical-device qualification recorded in the report.

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

## A-MV1 local vault storage

New installations use a local-only versioned registry in
`noBackupFilesDir/totipo-local-v1/registry`. Every vault has a generated opaque
local ID and its own `roots/<local-id>/store` beneath that directory. Local IDs
are independent of protocol VaultIds; registry metadata never enters a protocol
store or Sync folder. A durable pending reservation precedes allocation and a
durable creation-attempt marker precedes Create/Join. Authentication promotes an
entry to established. Uncertain attempts, missing established data, unsafe paths,
and corrupt metadata require explicit recovery; the app never replaces them.
Preserve the entire application data before attempting recovery. There is no
in-app metadata repair or automatic cleanup.

This version targets **fresh application data**. An old `totipo-vault` root,
global Sync preferences/retry hints, or global biometric credential/key blocks
startup with an incompatible-installation diagnostic. No legacy adoption or
migration is supported, and no old files, grants or keys are changed. Before a
manual application-data reset, use the previous application to preserve or
synchronize every local vault and verify the other copy. Resetting permanently
loses unsynchronized local vaults. The app never clears its own data.

A-MV1 keeps one immutable selected controller for the process lifetime. The
registry supports multiple entries, but a registry with multiple entries is
preserved and requires the later selection UI; no switching or inactive-vault
Sync is exposed. Sync preferences use the selected local ID; comprehensive
per-entry grant/retry management is deferred to A-MV3. Production unlock is
password-only until A-MV4 provides per-entry biometric records and keys. The
existing biometric cryptography remains unchanged and global credentials are
never reused. A-MV2 must retire all controller/provider work before changing
selection; releasing a local session lease alone does not permit switching.

The debug disposable-create probe is disabled while A-MV1 holds application
selection. Use the isolated registry qualification harness for synthetic creation;
a diagnostic must not admit a second session beside the selected controller.
