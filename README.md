# Totipo Android

Current source version is recorded in `VERSION`; Android versionCode is recorded
in `VERSION_CODE`. Totipo creates, unlocks and explicitly locks an
app-private local vault in no-backup storage. After setup, the main screen contains
**Sync**, search, tokens and **Add token**, with **Lock**, **Manage sync folder** and
**Diagnostics**, **Rename Vault** and **Change Vault** in overflow. The selected
vault name is the main heading. Tokens sort by issuer, account and stable token ID;
missing issuers sort by account, and conflicts retain neutral deterministic ordering
without choosing an alternative. Search preserves this order. Show code and Copy code retain their conceal/expiry
behavior. Diagnostics shows public vault/provider/build information, never passwords,
setup secrets, root keys or raw exception dumps. The App version comes from the
installed package metadata (version name and version code), with an honest unavailable
value if Android cannot supply it.

Vaults work fully **local only**. Create, password unlock, Add, Edit, Delete, Resolve,
local observation, Lock/reopen and restart use the coordinated private canonical
store without a storage provider, SAF grant or network. The neutral **Local only**
status does not mark successful local saves as unsynced or create transport retry hints.
Synchronization is optional and belongs independently to each local registry entry.

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

Local authoring succeeds independently of the subsequent Sync attempt. For a configured
binding, an unavailable provider leaves the valid local change committed and shows **Changes not synced**.
A persisted local retry hint survives restart; immutable local evidence is the source
of publication decisions. A later foreground event, mutation or manual Sync retries.
Requests coalesce to one active Sync plus one pending request; failures never start a
retry loop. Successful Sync is quiet and confirms only the bound shared folder, never
receipt by other devices. External replication between devices is managed separately.

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

Biometric unlock is optional and belongs independently to the selected local vault.
On an eligible locked-screen entry, an enrolled available credential automatically
offers one strong-biometric prompt. Cancellation, non-match, errors, rotation and
routine foreground transitions do not restart it; **Use biometrics** explicitly
retries. A successful open rearms the next lock episode. No prompt runs in Vaults.
Password unlock remains available. After password unlock, explicit opt-in authorizes
an auth-per-use strong-biometric AES-GCM operation storing that entry's reusable
password in private no-backup storage. Every biometric unlock requires a fresh
framework CryptoObject authorization, then uses the normal password-open path.
The authenticated session's exact VaultId must match the credential before OPEN,
token observation or automatic Sync. See [per-vault biometrics](#per-vault-biometric-credentials)
and the [A-MV4 report](review/A_MV4_PER_VAULT_BIOMETRICS_REPORT.md) for qualification status.
Passwords remain mutable caller-owned buffers and are wiped on completion as best
effort rather than guaranteed JVM erasure. Empty-password Create/unlock/Join requires
explicit confirmation.

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
There is no background sync or embedded external replication service,
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

A-MV2 keeps one Application selection authority and an immutable activation/root
binding for each controller. Ordinary Lock closes secrets/session and retains that
selection, including the existing 15-minute inactivity and background policy.
Internal complete retirement permanently rejects new commands, clears sensitive
presentation, finishes admitted local work, closes the Java session/facade/store
and lease, and drains entered provider work and completion handoffs before releasing
selection. Only then can the next controller be constructed. A failed close retains
the existing resources for explicit retry. A blocked provider retains selection in
a neutral waiting state even after the local session closes; arbitrary SAF/Binder
I/O cannot be proved cancelled by interruption, Future cancellation, executor
shutdown or a timeout. Process restart may be required. Application shutdown remains
best effort and never releases selection on the strength of executor shutdown.

Activities observe selection and recreate their presentation for a new activation;
old controller, observer, enrollment and picker callbacks retain their originating
activation. Only the non-secret picker activation is retained in memory across configuration
recreation; process death drops it and rejects the outstanding result.
The registry supports multiple remembered entries through the A-MV5 selection
surface described below. Optional biometric credentials are scoped to the selected
entry; global credentials are never reused. Inactive vaults never synchronize.

The debug disposable-create probe remains disabled while the Application holds
selection. Use the isolated registry qualification harness for synthetic creation;
a diagnostic must not admit a second session beside the selected controller.


## A-MV3 optional per-vault Sync

Each immutable controller receives its entry's `AndroidSyncFolderPort`. The existing
`sync_binding_mv1_<local-id>` namespace owns URI, remembered read/write metadata,
retry hint and binding-update recovery/permission-cleanup records. The registry
contains no second copy. Binding generations, request ownership, session generations,
Sync status and last result belong to that activation; callbacks never look up a
replacement controller. Inactive entries are inspected only for configuration metadata.

**Manage sync folder → Choose folder** attaches synchronization to an existing local
vault without changing its cryptographic identity. Use **Initialize sync folder** only
for an intended new provider replica; ordinary Sync requires matching provider VAULT
and performs fresh import, Java observation and publication assessment regardless of
retry hints. A different or ambiguous provider VAULT blocks publication. **Join** still
authenticates and enrolls the provider's exact VAULT; choosing a folder cannot replace Join.

**Disconnect** durably removes only that entry's binding and transport hint, preserving
all private and provider data. It cancels future work logically; an entered provider call
retains its grant until it actually returns. Local-only status follows the durable
configuration change. Rebinding performs a fresh complete Sync assessment. Failed or
interrupted configuration writes preserve the previous binding in recovery metadata;
choose the intended folder again or retry Disconnect. No successful disconnection is
reported for a failed preference write.

A remembered URI with missing/revoked SAF permission stays configured and shows
**Access needed. Choose folder again.** It is distinct from intentional local-only mode.
Local saves remain available after a failed transport attempt and retain their scoped
retry hint. The existing bounded active-Sync admission policy remains unchanged.

The Application serializes binding changes with SAF authority admission and cleanup.
Cleanup freshly validates the entire registry and examines every scoped binding,
recovery record and admitted provider use without constructing inactive controllers.
Obvious same-folder references, including equivalent decoded tree identifiers, are
refused during folder selection. Historical duplicate references still retain access.
Grant release is deliberately conservative: any remembered binding, active use,
uncertain inventory/write or different persisted platform grant prevents revocation.
Textually different SAF trees are not assumed independent. Cleanup records remain
visible in folder management when release fails or is deferred. Some unused grants
may therefore remain until explicit recovery; no broad startup cleanup is performed.

A-MV2's complete retirement and single-active-session guarantees remain in force.
A-MV5 exposes public selection and management while retaining these per-entry boundaries.
See the [A-MV3 report](review/A_MV3_PER_VAULT_SYNC_REPORT.md) for qualification evidence
and conservative cleanup limitations.


## Per-vault biometric credentials

A local entry ID is the registry's immutable generated 32-character lowercase hex
storage identity. It differs from the protocol VaultId, which identifies the exact
canonical VAULT and may be equal across separate local replicas. Neither labels,
SAF URIs nor record-supplied metadata choose keys or paths. Schema-v2 aliases are
`org.totipo.biometric-password.v2.<local-id>`. Each entry has its own non-exportable
AndroidKeyStore AES-256 key: GCM/NoPadding, randomized encryption, authentication
required for each operation, strong biometrics only, and biometric-enrollment
invalidation. No device-credential fallback, cached authorization or shared key exists.

Records live at `noBackupFilesDir/totipo-local-v1/roots/<local-id>/biometric-v2`,
beside `store/`, never inside VAULT, objects-v1 or a synchronized folder. The bounded
binary envelope contains version, exact local ID, authenticated session VaultId,
expected alias, 12-byte nonce and ciphertext with a 128-bit tag. Canonical
length-prefixed AAD binds a fixed application context, version, derived alias,
local ID and VaultId. Strict parsing rejects unexpected identity/alias/version,
invalid encodings, truncation, oversize and trailing bytes. Path validation checks
every ancestor without following symlinks. Registry metadata contains no password.

Enrollment follows explicit password authentication, captures the entry, activation,
session generation, actual session VaultId and owned password buffer, and rechecks
those values at the durable write boundary. Replacement uses the same entry's key
with a fresh operation and nonce; cancellation preserves the prior working record.
Writes force a create-new temporary file, atomically replace the record, force the
parent and check exact readback. A durable `.pending` marker blocks use until success.
An interrupted or failed replacement conservatively requires explicit Disable and
reenrollment, even if old bytes remain; no failure is reported as successful enrollment.
An unused key after canceled initial enrollment is bounded to the entry's one alias
and may be reused by a later explicit enrollment or removed by Disable.

Unlock callbacks retain their original controller and request. Lock, controller
retirement, Activity pause/stop, background transitions, cancellation and the bounded
prompt deadline revoke the relevant ownership. If a callback has already submitted
password-open work, cancellation is rechecked after authentication and after the
existing canonical read, before subscription or OPEN exposure. A VaultId mismatch
closes the candidate through the existing lifecycle, preserves uncertain store
and failed-close evidence, and never reassigns the credential or registry identity.
Normal rotation preserves an already authenticated session, never prompt authority
or decrypted passwords. Process restart requires a new biometric operation.
The 15-minute inactivity policy is unchanged.

The locked password form offers enrollment or replacement; the open selected vault's
overflow offers **Disable biometric unlock**, also for incomplete cleanup. Disable
revokes the Activity request and enrollment ownership, durably marks `.disabled`,
removes that entry's record/temporary metadata and its own key, and checks completion.
Partial cleanup reports failure, keeps a blocker and can be retried. A-MV5 has a
small Application cleanup hook that refuses a still-selected or incompletely retired
entry; it never deletes the canonical encrypted store. A-MV5 uses it during recoverable Forget.

Missing/malformed/incompatible records, missing or invalidated keys and tag failures
leave password authentication available. Temporary hardware unavailability, lockout,
non-match or prompt cancellation never automatically delete credentials. Explicit
reenrollment can replace malformed ciphertext with a usable existing key; missing
or invalidated keys and interrupted persistence require Disable first. No key is
silently regenerated beside an old record. A failure to validate private storage
uses an unavailable adapter that cannot claim successful deletion.

Local-only and synchronized entries use the same credential implementation. Provider
attachment/disconnection, inaccessible folders and Sync retries never change key or
record ownership. There is no inactive-vault authentication or background Sync.
Legacy global files/aliases remain incompatible-installation evidence: no adoption,
decryption, migration or automatic deletion is performed.

Keystore non-exportability and auth-per-use protect device-local key use under the
platform's security model; they do not protect a reusable password after authorized
decryption from a compromised application process. JVM buffer wiping is best effort.
Application-directed cleanup of A never touches B, but platform security changes,
including changes to enrolled biometrics, may invalidate several keys. There is no
rollback resistance, secure filesystem erasure or guarantee against a compromised
app UID concurrently replacing private files. Physical qualification status and
remaining hardware scenarios are recorded explicitly in the A-MV4 report.


## Multi-vault selection and management

A fresh installation shows **Vaults** with **Create Local Vault** and
**Join Existing Vault**. Create reuses the initial durable unused reservation and
creates an independent, local-only vault. It needs no folder permission, external
replication or biometric enrollment. Choose a password independently for each vault; equal
passwords do not make vaults the same identity. **Join Existing Vault** reserves a
new private replica, asks for a folder containing existing Totipo vault data, recognizes
and authenticates its exact VAULT with the existing password, rechecks provider evidence and installs
create-only. Successful Join uses the existing automatic Sync flow.

With one established entry and a valid last selection, startup shows its locked
screen. Multiple remembered entries show the unified **Vaults** screen, with a valid
last-selected established entry marked **Last selected**. Highlighting never
activates or authenticates it. Missing last selection, incomplete entries or
recovery evidence never authorize choosing another entry silently. An enrolled,
available credential gets at most one automatic biometric offer per lock episode
when the selected locked screen is resumed and focused. Password unlock and explicit
**Use biometrics** retry remain available; cancellation never starts a prompt loop.

**More options → Change Vault** is available from locked and unlocked screens,
including a single remembered vault. It clears forms, search, revealed codes,
clipboard ownership, biometric and enrollment authority, and permanently retires
the selected controller. The neutral closing screen remains until local/session/
store closure, admitted provider operations and their handoffs have actually
finished. Another controller cannot be constructed earlier. Failed closure offers
its existing retry; a stuck provider can require process restart. No forced timeout
release exists. Back from selection exits normally and never restores a retired
controller or an unlocked session. Rotation preserves a valid authenticated session;
setup secrets and biometric authorization do not survive Activity recreation.

The selected vault retains **Manage sync folder**, optional independent biometric
enrollment/disable, password fallback and the fixed 15-minute inactivity policy.
Background time counts. A local-only vault can attach a folder later; Disconnect
removes only its binding and retains local encrypted history and identity. Selector
summaries distinguish **Local only**, **Sync folder configured**, **Folder access
needed** and configuration recovery. A configured folder is not evidence that
publication completed or another device received anything. No inactive controller,
biometric helper or provider session is constructed just to render the selector.

**Vaults** combines selection and management. Tap a remembered row under **Your
vaults** to open its locked screen. Each trailing **⋮** menu scopes Rename and Forget
to that entry. **Add vault** separates Create/Join from existing vaults; a compact
**Recovery** section appears only when retained or interrupted entries need it.

Create and Join offer an initially empty optional **Vault name**. Join suggests the
selected folder's provider display name when available; the suggestion remains
editable, manual edits (including clearing it) win, and no opaque URI is parsed as
a filesystem path. Names identify the vault only on this device. Names can repeat,
contain at most 128 UTF-16 code units, and contain no controls or malformed Unicode.
Empty/whitespace-only input leaves the entry unnamed. Unnamed entries display, for
example, **8F2A6C · Unnamed**, from the immutable local registry ID, with a longer
prefix when necessary to distinguish registered entries. This generated fallback is
never saved as a name. Assigned names display alone. Rename is also available in
the selected vault's overflow, without another control beside token access. Names
never change IDs, roots, credentials, cryptographic identity or Sync configuration.

**Forget does not delete encrypted local data.** After complete retirement it
persists cleanup intent, removes only that entry's biometric record/key, retires
its Sync binding through the shared SAF permission owner, and atomically marks it
forgotten. The ID and root remain reserved in registry schema 2; schema 1 remains
readable. Forgotten entries stay hidden from normal selection after restart.
**Vaults → Recovery → ⋮ → Recover** selects the retained entry for normal password unlock;
only successful authentication reestablishes it. Recovery requires the app-private
data on this installation and the vault password. **Clearing app data or uninstalling
Totipo can permanently destroy local-only and forgotten vaults.** No recovery after
uninstall is promised. Shared, ambiguous or failed-release SAF grants retain their
existing durable cleanup records; management exposes deferred cleanup and a retry.

Interrupted Forget remains an explicit **Retry Forget cleanup** entry. Credential
cleanup failure never counts as success; an uncertain registry write requires
restart and explicit metadata recovery. No canonical files are deleted or merged.
Pending Create/Join entries with retained VAULT data can use normal password-open;
an attempted creation without usable VAULT cannot be retried as fresh Create.
An unused, unattempted empty reservation has an explicit allocation recovery action.
Missing roots, corrupt metadata, unknown roots and unsafe paths preserve evidence;
there is no automatic metadata repair, filesystem import, overwrite or broad scan.
Possible duplicate VAULT identities conservatively block activation/Join, including
retained roots. Structural recognition is not authentication. Preserve both histories
for explicit storage recovery rather than merging or deleting them.

External OTP enrollment cannot select or unlock a vault. Without an already open
selected controller, scan/share must be repeated after explicit unlock. Selection
changes discard old drafts and reject old picker/prompt callbacks. No OTP secret is
stored in the registry, saved Activity state or navigation state.

The clean-slate pre-A-MV1 upgrade policy remains: preserve/synchronize existing vaults
with the prior app before any deliberate data reset. No legacy adoption or credential
migration is provided. Platform UID/Keystore assumptions, best-effort JVM wiping,
clipboard limitations, process-compromise limits and lack of rollback resistance
remain unchanged. See [multi-vault architecture](MULTI_VAULT.md) and the
[A-MV5 report](review/A_MV5_ANDROID_MULTI_VAULT_UI_REPORT.md) for exact qualification
status; uncompleted physical or human Nix gates are not claimed as passing.

## Optional Sync folder

Totipo maintains each canonical vault in app-private no-backup storage. Local-only
vaults are fully functional. A **Sync folder** is optional: Totipo reads and writes
encrypted vault files in a folder chosen through Android's Storage Access Framework
(SAF). The selected folder may be local and may not be replicated anywhere.
External replication between devices is managed separately. Configuring a folder
does not guarantee replication, delivery to another device or backup.

To join an existing vault, choose **Join Existing Vault**, then choose a folder
containing an existing Totipo vault. Totipo reads encrypted vault data from the
selected folder and authenticates its existing identity with its password into a
new private local root. An empty folder cannot recover or join an existing vault.
**Create Local Vault** makes an independent identity and cannot substitute for Join.

To configure a folder later for an Android-created local vault, use **Manage sync
folder → Choose sync folder**. For an empty folder, explicitly **Initialize sync
folder**, then use **Sync** to publish local changes. Choosing a folder establishes
configuration; Initialize sets up this vault's encrypted replica there. Neither
step alone establishes token publication or receipt elsewhere. Use a separate
folder for each independent vault. Never point another writer at Android's private
store.

**Sync folder configured** indicates an association, not a publication or delivery
result. **Folder access needed** retains that association and requires access
recovery; it does not mean **Local only**. **Local changes published** and **No local
changes to publish** describe only Totipo's workflow against the configured folder.
Successful Sync stays quiet on the main screen; folder management and Diagnostics
retain operation details. Local save, folder publication, external replication and
observation by another device are separate events.

Use **Change sync folder** to select a different folder or recover missing SAF
access. Local saves remain valid; ordinary Sync retries after access restoration.
**Disconnect sync folder** removes the association and returns the vault to local-only
use without deleting its local data or the files in the former folder. Inactive
vaults never synchronize automatically.

Change Vault waits for complete retirement, then requires independent authentication.
Biometric enrollment is optional for each entry. Forget retires that entry's
convenience credentials and Sync binding while retaining its encrypted data;
Recover requires its password and retained private data. App-data removal/uninstall
can permanently destroy local-only and forgotten vaults. Preserve and independently
verify another usable synchronized copy before any deliberate reset.

The [A-MV6 qualification report](review/A_MV6_INTEGRATED_MULTI_VAULT_QUALIFICATION_REPORT.md)
separates current integrated results from A-MV1–5 and historical M3D evidence.
Physical desktop-created Join and bidirectional Add/update/delete through real
Syncthing now have captured evidence, including conflict resolution and independent
local-only and synchronized identities. Final corrected-APK Join, bidirectional
delivery, hardware biometrics, retained
recovery and focused UI checks pass. The human Nix gate passed on frozen inputs;
A-MV6 qualification is complete for the documented environment and ready for a
subsequent release decision. This does not authorize publishing a release.
Its tested environment and documented limitations govern interoperability claims;
connected peers or a configured folder are insufficient evidence. Arbitrary SAF
providers, OEMs and accessibility services are not universally qualified. The
current native Activity theme is explicitly light; a dark system setting does
not imply a dedicated dark application theme.
