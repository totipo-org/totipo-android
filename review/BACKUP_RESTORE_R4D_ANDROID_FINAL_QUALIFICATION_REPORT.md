# BACKUP_RESTORE_R4D — Android final qualification

**IMPLEMENTED, DEVICE QUALIFICATION PENDING.** Local host qualification passes on
published Java 0.3.2. Scoped physical Android17/API37 and local SAF backup/restore
checks pass as recorded below. **API30 remains NOT EXECUTED / BLOCKED** because
no API30 environment is available; remaining physical coverage is listed explicitly.
Final human `nix flake check`: **human-reported PASS** on 2026-11-16.
This is not ready for release preparation. No Android version bump, commit, tag,
push, production signing or publication was performed.

## Scope and starting evidence

Classifications: **BUILD_DEPENDENCY, PACKAGING_RELEASE,
TEST_OR_QUALIFICATION_TOOLING, PRODUCT_CODE, DOCS_ONLY**. PRODUCT_CODE covers the
runtime diagnostic string and included debug qualification source. No new product
feature, Java modification, cryptography, protocol/archive change or history UI.

Started on clean `main` at **569986718d2f4eeb55d8d381cb42ea37a7404903**.
`git branch --show-current`: main; `git rev-parse HEAD`: that SHA;
`git status --short`: empty. Final branch/HEAD remain unchanged.
All work/downloads were confined to totipo-android; no other checkout was modified.

Read AGENTS, README, MULTI_VAULT, Java provenance, ARCHITECTURE, USER_GUIDE,
DEVELOPMENT, R0/R4A/R4B reports, both backup device procedures, package.nix,
flake.nix, CI/release workflows and affected tests/tools. Read pinned Java
f0a028676c1801a10b2d8d2650bf9c24357d1fa1 operation/observation guidance, released
0.3.2 API_DESIGN and frozen BACKUP_ARCHIVE, committed API30 compatibility,
preparation report and release notes. Guidance SHA-256 remains
`338837e3563446f8ec11f7bb47c0e4f3ff5502b7608e397f9b8c4d75372715fd`.

## Released artifact/source provenance

[Java v0.3.2](https://github.com/totipo-org/totipo-java/releases/tag/v0.3.2) was
published 2026-11-16T05:59:39Z. The remote tag resolves exactly to
**af11553659dd8bd4157064c7e3de27bed8980ac5**, matching appended published provenance.
Spec stays **cdb4e91be1c6d3704874b2b92457ffe7be5e9084**, v1/r19; the published
portable corpus statement is 92/92, not an Android execution result.

Downloaded ten actual artifacts from canonical Maven Central and the exact release
source archive over HTTPS. All **98 core and 13 NIO** published Java source files
match that commit byte for byte. Every binary class has major **61**; module
variants require JVM **17**. Binary/source/Javadoc hashes match published module
metadata. POM/module bytes were independently hashed. The six Gradle-resolved
JAR/POM/module files also match these actual downloads. Source/Javadoc archives
are inspection only, never build substitutions. This is HTTPS/hash/source
provenance, not a new independent signing-key ownership claim.

| Published artifact | SHA-256 |
| --- | --- |
| `totipo-core-0.3.2-javadoc.jar` | `f41f0322247bd87fcb88d399fca446dc90b7b0e3e93e87cf26273d99f0d5685f` |
| `totipo-core-0.3.2-sources.jar` | `044ea95e2d6eafe2b34f2accb2c959a12a5bdd008d350c9841fb75c9bac6c0d0` |
| `totipo-core-0.3.2.jar` | `db35f01b51ba1777b3411306824559126179d868199c40281534b8b02da6bd98` |
| `totipo-core-0.3.2.module` | `a46eb72f8c00060244f67667dcd462bc4f3a557a9fa02eb687b2eeee1a681f74` |
| `totipo-core-0.3.2.pom` | `3f873b517e72f70bbcd5d47ee7df8c102ac0f1dc05ab23aaf23d42c481802cd7` |
| `totipo-storage-nio-0.3.2-javadoc.jar` | `56aa65e7447bce979d19f0a1c7eb12e5616fc87f858d0578913c2f836d52aaa3` |
| `totipo-storage-nio-0.3.2-sources.jar` | `14a691e74bcde1f5cddd5a5b70783b53bc9f85aa36890dd877e5126d607a6968` |
| `totipo-storage-nio-0.3.2.jar` | `c680ac4b09d68b2635b7bd4bfc3fb77b2519634df57c91b57a7b63045ea458bb` |
| `totipo-storage-nio-0.3.2.module` | `a9962bde6e871ad8aba84f193cddf5a44954dd415c388bd776ad503ce089f86b` |
| `totipo-storage-nio-0.3.2.pom` | `7bf48a433e23ba97a5db72586a0fa10bb1b297ae2a2254ade80054c198fd4ce9` |

Both POM and module metadata, strict locks and actual final release graph agree:

```text
org.totipo:totipo-storage-nio:0.3.2
  -> org.totipo:totipo-core:0.3.2
     -> org.bouncycastle:bcprov-jdk18on:1.86
```

Only NIO is directly declared. BC is core's sole runtime dependency. No new
production dependency, Maven Local, vendoring, composite/source substitution,
desugaring dependency/configuration or Android runtime fallback was introduced.

Frozen BACKUP_ARCHIVE documentation is byte-identical to 0.3.1, SHA-256
`6886c99a5e87f8ebfc3716c77aadb378e6c2cb24a51f110063ba2cceecbd9918`.
All three existing Android golden ZIPs match released 0.3.2 source exactly:

| Fixture | SHA-256 |
| --- | --- |
| empty.zip | `603eb0dd84f67678bab82c78ee684dc22592b5e4de37f24d16f5694c382fa425` |
| history.zip | `369b77c872a32f2474d867e0653e39b66befdfc6cbd1244da486505ba8bca95b` |
| missing-parent.zip | `6991ee0b764409728a10ee6612d6eced56207dbefa58a35611ea3d6112c25e58` |

Their historical 0.3.1 origin record and prior milestone reports remain unchanged.

## Active dependency, cache and documentation changes

Updated Gradle's direct dependency and all Maven boundary expectations; both
Totipo lock entries; six verification hashes/origins; package.nix readiness
coordinates; controller diagnostics and its existing assertion; active device
Python classpaths; release-tooling production/fixture expectations; and active
provenance, AGENTS, architecture and release documentation. Other locks, BC,
build dependencies, SDKs, permissions, filters and flake.lock are unchanged.

The human-owned `nix run .#update-package-deps` was requested. The externally
refreshed cache has only the two 0.3.1 artifact records removed, two 0.3.2 records
added, and NIO release metadata advanced from 20261116005802/0.3.1 to
20261116055818/0.3.2. All six SRI hashes match the independent downloads and Gradle
verification pins. No unrelated origin, dependency, Google index or lint snapshot
changed. User replied “run update-package-deps”; no explicit command PASS log
was supplied. Cache bytes are reviewed evidence; no agent Nix execution or inferred
human command success is claimed.

Android remains **0.2.3 / versionCode 8**, minSdk **30**, compile/target **37**,
JDK17, Gradle9.8.2, AGP9.4.0, Build Tools36.0.0 in the existing pinned managed shell.

README remains concise. The user guide now replaces the old known HexFormat
implementation blocker with pending actual device qualification, explicitly covers
fresh-install/device-move restoration, independent storage, original password,
local-only restore, duplicates, exact interrupted retry, finite history/capacity
and backup versus Sync. Corrected canonical folder spelling to lowercase `vault`.
No Add Missing History instructions were added. Updated both device procedures
to record R4D and connect normal create/use, frozen export, fresh-profile restore,
exact encrypted-history/TOTP comparison and actual interrupted recovery.

## API30 compatibility review and its limits

Reviewed [the committed Java compatibility report](https://github.com/totipo-org/totipo-java/blob/af11553659dd8bd4157064c7e3de27bed8980ac5/review/JAVA_ANDROID_API30_COMPAT_REPORT.md)
and actual released code. Unsupported HexFormat, unsigned-array comparison,
Stream.toList, readNBytes, nullOutputStream, writeBytes and setTimeLocal calls were
replaced upstream. Collection/Optional backports and records/lambdas rely on D8
lowering. Android still uses minSdk30 without a compatibility dependency.

Authentication retains lightweight BC Argon2id v1.3 (64 MiB, three iterations,
parallelism four, 32-byte output); no BC JCA provider registration. Platform
AES/GCM/NoPadding, SHA-256 and HMAC remain unchanged. Object/VaultId/archive hex
uses shared internal ASCII Hex. Backup/restore use shared bounded reads and the
unchanged profile-1 writer/inspector/authenticator. ZIP setTime preserves the
frozen DOS timestamp under host golden tests; Android ZIP/timezone behavior needs
actual execution. NIO retains coordinated create-only ordinary-move publication,
no-follow checks and canonical file/directory/parent force barriers. No overwrite,
hard-link fallback or relaxed acknowledgement/promotion was introduced.

Complete final packaged DEX tables were inspected against AOSP API30 public
android.jar SHA-256
`2f0e6e4ec44e95beefe0c041541afcec4f55f8b964b33599a987efb6e972f990`:

| Final packaged variant | DEX files | Program classes | Standard platform method IDs | Missing java.* methods |
| --- | ---: | ---: | ---: | ---: |
| Release | 2 | 6587 | 1886 | 0 |
| Debug | 7 | 7189 | 2398 | 0 |

The member-table inspection includes app/core/NIO/BC/generated support and inherited
lookup. It is bounded static signature evidence, not reachability, reflection,
crypto-provider/filesystem capability or Android execution proof. Remaining
unavailable references are SDK>=33 guarded predictive-back APIs in both Activities,
DEX implementation annotations absent from public stubs, unused BC javax.naming
LDAP/CRL utilities, and a debug optional BC NamedParameterSpec type. Totipo's
lightweight Argon2 path does not register/invoke those asymmetric/LDAP provider
utilities. No claim that the whole BC distribution has zero unavailable references.

Post-D8 packaged output comes from the reviewed Maven JAR hashes, unchanged Java17
and minSdk30 AGP configuration. Final APK policy independently confirms minSdk30
and identity/variant boundaries. No packaged Android API30 execution occurred;
**Android 11 compatibility is not certified** by this review or host passes.

## Host results, failures and invalidations

Baseline full Gradle: PASS, 1m52s, 90 tasks (12 executed/78 up-to-date); baseline
XML 493 tests, zero failures/errors/skips. No clean/rerun/cache bypass at baseline.

The first affected focused selection ran 334 tests: one failure, zero errors/skips.
BackupControllerTest's scan-fault test failed at first beginBackup admission,
before capture. Its unchanged single-method reproduction passed in 1s. Inspection
showed asynchronous initial observation can occupy the sole pending worker slot
after readiness is observed. The test now suppresses unrelated presentation through
its existing observation callback seam; it still requires real Java/NIO incomplete
scan and missing-object capture to refuse output. No product scheduling policy or
assertion was weakened. Corrected backup/controller/restore selection: PASS, 17s,
23 tests (10 BackupController, 10 BackupRestoreFlow, 3 CoordinatedRestore), zero
failures/errors/skips. Initial regression selection also covered multi-vault/root
ownership/retirement, registry/recovery, local/Sync workflows, password/biometric,
inactivity, reveal/copy/TOTP and Activity/source boundaries. Final full tests cover
all existing suites.

First normal/strict gates passed (493 tests each), and their first artifact set
passed. Complete-package static inspection then found the **debug NIO primitive
probe** still calling API33 readNBytes. Corrected this Android-owned qualification
primitive to read exactly three bytes using API30 read(byte[],offset,length),
require progress, then retain fourth-byte/EOF assertions. Updated its stale 0.2.0
boundary label to 0.3.2. Shared Java runtime paths were not replaced or patched.
This included debug-source edit **invalidated both initial full checkpoints,
artifacts and freeze**. It is the sole reason for the extra normal/strict/artifact
set; no final evidence was waived. Initial probe compile failed on an unqualified
IOException name; corrected to java.io.IOException and focused compilation passed.

Definitive final normal: **PASS, 3m 48s**, 90 actionable tasks: 16 executed, 1 from cache, 73 up-to-date.
Definitive strict offline clean: **PASS, 3m 44s**, 92 actionable tasks: 92 executed.
Each full XML snapshot: **493 tests, zero failures/errors/skips**. Lint and strict
build/Maven boundaries pass. Offline final graph/boundary: PASS. No included edit
followed these definitive checkpoints. Initial normal 3m35s/strict3m58s passes and
first APK hashes are retained as superseded evidence, not counted as final.

Release tooling: **80 tests PASS**, 17.326s. Device-tooling/signing host suite:
**29 tests PASS**, 36.020s. Python syntax, relative documentation targets and
`git diff --check`: PASS. These are host checks, not Android/SAF evidence.

Ignored `.qualification/r4d/` preserves downloads, provenance/cache/contract reviews,
logs, full XML and static helpers/results. XML folders: baseline-xml,
focused-failed-xml, reproduce-xml, focused-fixed-xml, final-normal-superseded-xml,
strict-superseded-xml, final-normal-xml, strict-xml. First compile failure and
superseded full logs remain preserved. An initial temporary cache-review helper
raised IndexError on the semantic Maven metadata record; corrected exact-record
assertions pass. Inspection-only failure caused no source or broad gate rerun.

### Exact executed qualification commands

```sh
gradle check :app:assembleDebug :app:assembleRelease
gradle :app:testDebugUnitTest --tests '*Backup*Test' --tests '*CoordinatedRestoreTest' --tests '*VaultRegistryTest' --tests '*VaultManagementTest' --tests '*LocalReplicaOwnerTest' --tests '*ControllerRetirementTest' --tests '*AndroidVaultControllerTest' --tests '*SecurityControllerTest' --tests '*PerVaultBiometricCredentialsTest' --tests '*Biometric*Test' --tests '*InactivityLockTest' --tests '*TotpControllerTest' --tests '*TotpCoordinatorTest' --tests '*RowRevealCopyTest' --tests '*ForegroundVaultCoordinatorTest' --tests '*VaultEnrollmentTest' --tests '*DetachedVaultAuthenticationTest' --tests '*MultiVaultUiBoundaryTest' --tests '*Daily*Test' :app:verifyMavenBoundary
gradle :app:testDebugUnitTest --tests 'org.totipo.android.BackupControllerTest.incompleteEnumerationAndMissingSupportedObjectRefuseCapture'
gradle :app:testDebugUnitTest --tests 'org.totipo.android.BackupControllerTest' --tests 'org.totipo.android.BackupRestoreFlowTest' --tests 'org.totipo.android.reconcile.CoordinatedRestoreTest'
gradle :app:compileDebugJavaWithJavac
# Normal full command repeated at stable boundaries per accounting below.
gradle --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug :app:assembleRelease
python3 tools/verify-apk.py app/build/outputs/apk/debug/app-debug.apk --debug-probe
python3 tools/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk --unsigned --no-debug-probe
gradle --offline :app:dependencies --configuration releaseRuntimeClasspath :app:verifyMavenBoundary
python3 -B -m unittest discover -s tools/release -p 'test_*.py'
python3 -B -m unittest discover -s tools/device -p 'test_*.py'
```

| Invocation category | Count / evidence |
| --- | --- |
| Baseline ordinary full Gradle | 1 PASS |
| Focused Gradle tests | 3: first 334-test run FAIL, one-test reproduction PASS, corrected 23-test selection PASS |
| Focused compilation | 2: missing qualification-probe exception qualification FAIL; corrected compile PASS |
| Normal final checkpoint attempts | 2 PASS; first invalidated by debug primitive fix |
| Strict offline clean | 2 PASS; first invalidated for the same reason |
| External artifact verifier | 2 sets / 4 commands; first superseded, second definitive |
| Embedded full APK verifier in local Gradle | 0; check uses JVM/lint/Maven boundaries |
| Explicit offline graph/boundary | 2 PASS, one per strict artifact set; also boundary in first focused invocation |
| Release/device-tooling host suites | 1 each, 80/29 PASS |
| Physical/emulator harness phases or installs | 20 physical phase attempts: 10 full PASS, remaining failures/incomplete evidence below; 16 isolated installs/updates; 0 emulators |
| Agent Nix | 0 |
| Human cache refresh | One requested; external generated diff reviewed, explicit PASS not supplied |
| Final human nix flake check | 1 human-reported PASS on definitive frozen inputs |
| Commit/tag/push/production release | 0 |

## Definitive strict-clean artifacts

| APK | Policy | SHA-256 |
| --- | --- | --- |
| app-debug.apk | --debug-probe PASS | `ccd9050bbc1e9a9df82c5b6fb5b22a659a09f43284ebbf412b5867f184547b0d` |
| app-release-unsigned.apk | --unsigned --no-debug-probe PASS | `06634633435d6a743548f80a4e4ce6e26d29cf0b58611f3001719982c4fb4114` |

Both match reviewed Android 0.2.3/8, minSdk30/target37, package org.totipo.android,
branding and complete component/export/intent-filter policy. Main permission stays
USE_BIOMETRIC only; allowBackup=false. No network/storage permission or new archive
intent handler. Release excludes debug probes. Debug is locally debug-signed;
release build output is unsigned. No production release signing occurred.

A non-debug candidate was prepared with the existing validated LOCAL NON-PRODUCTION
identity `wave2a-qualification`, certificate SHA-256
`d862bbf279d306e7062440d299440d54ecdf594e1cf8ac3046cdf5d3a0ed7f49`, under ignored
`.gradle/r4d-qualified/`. Signature/alignment and exact ZIP-payload equivalence
are checked separately; no installation or device execution is implied. The first
signing output path under .qualification was refused by its existing identity-parent
protection; corrected ignored output path required no source/key change.
Candidate SHA-256: `c21dd46645768e05168d16b32c2db0427c46d7818d9bc63528ccf70ad83c6ce7`.
Every ZIP entry was rechecked against the definitive release and is byte-identical;
the definitive unsigned release hash is also unchanged from the signed input.
Successful preparation command:

```sh
python3 -B tools/device/qualification_signing.py sign app/build/outputs/apk/release/app-release-unsigned.apk --aligned .gradle/r4d-qualified/release-aligned.apk --out .gradle/r4d-qualified/release-local-qualification.apk
```

## Physical Android17/API37 qualification

**Executed on a physical device, not an emulator.** Pixel6a serial
`37311JEGR05916`, Android17/API37, arm64-v8a, fingerprint
`google/bluejay/bluejay:17/CP3A.261005.002.A1/2026111301:user/release-keys`, Owner
user0. The human unlocked the device and supplied strong biometric authorization.
The human explicitly confirmed **no API30 environment is available**. The pinned
SDK has includeEmulator=false/includeSystemImages=false; no emulator or /dev/kvm
is available in this session. Thus API30 acceptance remains NOT EXECUTED.

Initial empty ADB discovery was superseded by the working host ADB server. The
USB device mount remains absent inside the jail, but the host server now exposes
the physical device correctly. No agent Nix invocation was needed.

### Isolation, installed bytes and providers

Existing owner package org.totipo.android/versionCode5 was never updated, cleared,
uninstalled or used for test vaults. Its public APK SHA-256 before/after is exactly
`362295c33714b98712316ca48c1152550010b4dd1d6d63746ccc23a633b00872`.
No owner vault data or credential was inspected. Three new disposable application
UIDs were installed; updates were confined to those same test packages with the
existing local non-production certificate. Their actual installed APKs were pulled
and signer/payload verified. Final installed wrappers:

| Disposable package | Installed APK SHA-256 |
| --- | --- |
| org.totipo.r4dsource | `d3ca06c6a4479ebf058bdca0703ba59d1740aa41089d9f4f49f11b365ea8245e` |
| org.totipo.r4drestore | `5a870fc8e599d5c75f53daf1e0031c772fde8755e8c33356d7f0af976dadec4a` |
| org.totipo.r4dfaults | `3a804a8c989eac11cd1f77bb36f808038b259066a72ce5fd4f247f854ee09330` |

Every wrapper contains **all 17 selected production DEX/resource ZIP entries
byte-identical** to final strict release APK
`06634633435d6a743548f80a4e4ce6e26d29cf0b58611f3001719982c4fb4114`.
Added content is a test manifest, separate instrumentation DEX and public golden
fixture assets. Manifest uses actual TotipoApplication/MainActivity, non-debug,
testOnly, Android0.2.3/8, minSdk30/target37 and USE_BIOMETRIC only. It substitutes
package/label and instrumentation components for isolation; this is **not an
installation or identity qualification of the production org.totipo.android APK**.
No production signing key, added app dependency or modified product DEX is used.
Signer certificate is the existing LOCAL NON-PRODUCTION identity
`d862bbf279d306e7062440d299440d54ecdf594e1cf8ac3046cdf5d3a0ed7f49`.

Actual system picker: com.android.documentsui, versionName17/versionCode37.
Backup/open provider: **com.android.providers.downloads.documents**, Downloads,
MIME application/zip, provider package com.android.providers.downloads version17/37.
Sync tree provider: **com.android.externalstorage.documents**, package
com.android.externalstorage version17/37, only newly created
`Documents/Totipo-R4D-3f9a5805d994-Sync`. No Syncthing peer/cloud replication is claimed.
Cloud/removable providers were not exercised; no upload/durability guarantee.

Generated fixture source, APKs and raw phase logs are preserved under ignored
`.qualification/r4d/device/` and `.gradle/r4d-device/`. R4DRegression.java and
build.py reuse existing tools/device AndroidToolchain/packaging/signing owners.
These are standalone local execution fixtures, not intended repository/check
inputs and not covered by the human Nix check. Their compilation/iteration does
not alter the frozen production/JVM/build/cache/included-tools inputs. The
fixture is qualified here for API37; its test convenience APIs must be reviewed
before any API30 reuse. Production minSdk30 configuration is not execution proof.

### Actual backup/restore evidence

The production UI/controller created a local-only vault with public disposable
password and dummy TOTP secret. Added two tokens; edited the live token twice and
deleted the other, producing five encrypted objects. Real Show/Copy paths matched
an independent HMAC-SHA1 computation for the returned validity interval and the
actual Android clipboard. Lock, password reopen, a cold-process reopen, another
update and Activity recreation passed. No Sync was required.

The app saved the first backup using ACTION_CREATE_DOCUMENT and real provider
output/close/readback; it reported **Backup saved and verified**. Independent
published Java0.3.2 prepareRestore authenticated every archived object on the host.
VaultId throughout is
`1f0b34ea95af68c3edf36b4b33eb1bae5b2a2f364a3f5a9402d2637938a33d29`.
A controlled later source edit added a sixth object. The original independently
stored ZIP retained exactly its five objects and exact bootstrap; the second ZIP
retained every original ciphertext and added exactly one object.

A separate fresh disposable UID simulated loss of local application data without
clearing any installation. Through actual ACTION_OPEN_DOCUMENT, the app read the
original ZIP, authenticated the original password, installed COMPLETE, kept the
entry RESTORING until ordinary password open/observation, then established it.
Displayed live account remained **revision two**, not the source's later **after
backup** value; deleted history remained observed. TOTP/copy passed. Local-only
status, no Sync URI/grant/configuration, absent biometric record and empty fresh
Keystore were verified **before** later explicit biometric opt-in.

Actual re-export through the system save picker produced a **byte-for-byte
identical ZIP**, not merely a successfully parsed or authenticated bootstrap.
Independent Java authentication and exact ciphertext membership comparison passed.

| Independently stored archive | Objects | SHA-256 |
| --- | ---: | --- |
| Original local-only backup | 5 | `384bc8db6b6194462537d217ff144601a79fa2752d00d2cace22d69d683ae218` |
| Later changed source | 6 | `be6fdd86d4448fc5e18ebe114203710e0bbceb394a72bcddc552ab9fac067f4b` |
| Fresh-install restored/re-exported | 5 | `384bc8db6b6194462537d217ff144601a79fa2752d00d2cace22d69d683ae218` |
| Configured Sync source backup | 6 | `be6fdd86d4448fc5e18ebe114203710e0bbceb394a72bcddc552ab9fac067f4b` |
| Interrupted golden recovery/re-export | profile1 history | `369b77c872a32f2474d867e0653e39b66befdfc6cbd1244da486505ba8bca95b` |

For the synchronized source, actual folder picking bound the exact new test tree,
explicit create-only Initialize passed, and Sync returned **Verified against bound
shared folder**. Independent readback of that new folder matched exact vault plus
six objects. Backup then passed without altering the binding. Its ZIP is identical
to the earlier six-object local capture, demonstrating that Sync configuration is
not serialized. No external peer convergence campaign was substituted or repeated.

Actual duplicate established identity offered Open existing vault with no second
allocation. Wrong password and a separately corrupted ZIP allocated nothing beyond
the initial unused PENDING reservation. Native picker cancellation allocated no
vault. Rotation while the original-password form was filled cleared password widget
contents and retained only the journey. A separate ZIP-filter run recreated the
Activity with the actual system picker outstanding: its real result reached the
same retained journey. After canceling that journey, a deliberately injected late
onActivityResult using its original request/URI was rejected by the actual Activity;
this last callback is a synthetic delivery test, not a claim about arbitrary OS
late-result scheduling. Both ZIP MIME and Browse all files paths were exercised.

### Biometrics, faults and interrupted restoration

The first enrollment attempt timed out without a record despite user-reported
fingerprint success; retain it as non-PASS. Diagnostic delegation on the disposable
prompt/credential interfaces then recorded **framework authorization → current
controller guard → real encrypt → persisted configured credential**, using the
actual Android CryptoObject and Keystore. It did not mock authorization or replace
cipher/storage semantics. A subsequent immediate unlock was canceled. After a
cold process restart with unwrapped production classes, human strong biometric
unlock opened the same restored vault; TOTP/copy passed. Explicit Disable removed
only the disposable record/key, leaving its Keystore empty. No device biometric
enrollment/template or owner-app key was changed. The first untraced timing outcome
is not explained by a newly proven crypto/storage defect; it remains a qualification
caveat, not a hidden PASS or a universal biometric reliability claim.

On actual Android private storage, deterministic durability wrappers performed the
real NioDurability barrier and then injected lost acknowledgements at location,
bootstrap and first/middle/final object boundaries. All five outcomes remained
UNCERTAIN. Registry reconstruction refused ordinary selection; explicit original-ZIP
reauthentication/retry completed, preserved every ciphertext and required normal
opening before promotion. A conflicting occupied object yielded actual
BackupRestoreFlow FAILED/PARTIAL presentation, remained byte-identical and never
became an ordinary established vault. Six registry prewrite/lost-ack cuts across
reservation, ATTEMPTED and INSTALLED preserved recovery ordering; missing attempted
roots were not recreated. These are deterministic device harness faults, not claims
of spontaneous media failure, power-loss durability or every possible interruption.

A separate actual-application case reserved an entry and durably installed only its
bootstrap/intent. After explicit `am force-stop` of **only the disposable UID**, cold
startup exposed Recovery and refused selection/promotion. The original golden ZIP
was selected through real DocumentsUI, reauthenticated, retried in the same root,
completed and normally opened. Its Android re-export exactly equals the frozen
Java history.zip bytes/digest above. Bootstrap-only opening was never accepted as
completed restoration.

Real local SAF streams also exercised injected output-close failure (FAILED),
injected unavailable readback-open (WRITTEN_UNVERIFIED) and an entered write held by
a deterministic latch. In the last case, actual Home backgrounding canceled further
work, Lock closed the session, and retirement retained ownership until the real
provider call returned. Final outcome was CANCELED, never verified success. These
fault wrappers are explicit instrumentation at the stream boundary; they do not
claim a real provider spontaneously failed its close or remote upload.

### Environment/acceptance matrix

| Environment or scoped requirement | Result | Evidence / limit |
| --- | --- | --- |
| API30/Android11 | NOT EXECUTED / BLOCKED | Human confirmed no environment; required before Android11 compatibility claim |
| Physical Pixel6a Android17/API37 normal crypto/storage/create/edit/delete/TOTP/Lock/reopen | PASS | Actual non-debug packaged production payload in disposable UID |
| Real Downloads save/open/close/readback; local-only backup | PASS | Source/re-export archives authenticated; byte equality above |
| Configured local SAF Sync source backup | PASS | Exact new tree, explicit Initialize, verified Sync, six-object identical ZIP |
| Fresh-install ZIP restore identity/history/normal TOTP | PASS | Separate UID, original password, exact five-object re-export |
| No imported Sync/biometric credentials | PASS | Checked before explicit later biometric opt-in |
| Strong biometric opt-in/cold unlock/explicit scoped disable | PASS, scoped | Earlier timeout/canceled attempts retained separately |
| Wrong password, corrupt ZIP, duplicate established identity, picker cancel | PASS | Actual app/real provider; no second allocation |
| Activity/password recreation and outstanding real picker | PASS | Actual framework recreation/result; synthetic stale delivery distinguished |
| PARTIAL/UNCERTAIN and six registry cuts | PASS, injected | Actual Android NIO plus deterministic failure seams |
| Bootstrap-only process interruption and original-ZIP Recovery | PASS | Force-stop/cold app/actual picker/default NIO replay/golden equality |
| Output close/readback fault and background/Lock/entered-I/O retirement | PASS, injected | Real provider streams; no verified-success misclassification |
| Full 15-minute real inactivity expiry / every preparation cancellation boundary | NOT EXECUTED | Deterministic host coverage remains separate |
| Physical duplicate recognition in every retained state; all torn-intent/final-inventory fault permutations | NOT EXECUTED | Broader host matrix is not promoted to physical coverage |
| Cloud/removable providers, disconnect/storage-full/provider death | NOT EXECUTED | No upload/durability or provider-universality claim |
| Original debug APK / debug NIO probe on a device | NOT EXECUTED | Host compilation/artifact/static checks only; executed payload is non-debug release |
| TalkBack, enlarged fonts and dark-system visual campaign | NOT EXECUTED | Control/node assertions are not those campaigns |

### Every physical phase attempt and preserved failures

20 `am instrument` phase attempts, **10 full PASS**; incomplete/failed attempts are
listed individually below. No failing transport return was mistaken for a scenario
PASS. Across three disposable packages there were 16 install/update commands; no
owner update/uninstall/data reset. Standalone fixture builds: 18 attempts, 17
completed and one javac failure from an overly broad checked exception in a fixture
lambda (narrowed to IOException); a direct compiler diagnostic reproduced that
same error. These are not additional Gradle invocations.

| Raw log stem | Result | Scope / diagnosis |
| --- | --- | --- |
| source-1 | FAIL | Harness expected an empty registry; app intentionally reserves one unused PENDING entry. |
| source-2 | INCOMPLETE / stopped | Create/add/edit/delete/TOTP checks passed; aborted at unbounded instrumentation idle wait. |
| source-3 | PASS (54 checks) | Retained-data cold open, Lock/reopen, TOTP/copy, recreation, two real SAF exports and independent later mutation. |
| restore-1 | FAIL | Picker cancellation passed; pointer selection timed out before authentication/allocation. |
| restore-2 | INCOMPLETE / stopped | Incomplete launch/retained picker task; no scenario PASS. Corrected cold task startup. |
| restore-3 | PASS (85 checks) | Fresh installation restore, wrong/corrupt rejection, rotation, normal opening, exact re-export, no imported Sync/biometrics, duplicate refusal. |
| faults-1 | FAIL | Fixture used platform /data/user/0 alias; registry correctly refused noncanonical ancestry. |
| faults-2 | PASS (142 checks) | Trusted-parent canonicalization; five physical lost-acknowledgement cases, exact retry, PARTIAL/no-overwrite and six registry cuts. |
| biometric-1 | FAIL | Untraced enrollment timeout despite user-reported authentication; retained as non-PASS. |
| biometric-diagnostic-1 | INCOMPLETE / stopped | Framework authorization, current guard, actual encryption and persisted credential PASS; subsequent unlock canceled; stopped incomplete phase. |
| biometric-unlock-1 | PASS (10 checks) | Unwrapped cold-process strong biometric unlock, TOTP/copy and explicit credential/key deletion. |
| interrupted-prepare-1 | PASS (4 checks) | Actual application registry ATTEMPTED; durable bootstrap and no history; normal selection refused. |
| interrupted-recover-1 | PASS (37 checks) | After force-stop: Recovery, exact original ZIP through picker, COMPLETE, normal password promotion and exact golden re-export. |
| zip-filter-1 | PASS (13 checks) | ZIP MIME path, actual picker result across Activity recreation, canceled journey and synthetic late original callback rejection. |
| provider-outputs-1 | INCOMPLETE / stopped | Real SAF stream plus injected close failure reported failed; later fixture handoff retained old picker reference, so phase stopped. |
| provider-outputs-2 | PASS (38 checks) | Corrected fixture clears both handoff fields; real SAF readback unavailable and background/Lock/retirement while I/O entered. |
| sync-backup-1 | FAIL | Fixture rejected broader Documents tree before initialization/publication; explicit cleanup followed. |
| sync-cleanup-1 | PASS (9 checks) | Actual Disconnect restored local-only and released the disposable UID's persisted URI grant. |
| sync-backup-2 | FAIL | Fixture stopped before grant: first matching Pixel 6a node was not an actionable drawer entry. |
| sync-backup-3 | PASS (38 checks) | All matching visible semantic nodes; exact new subfolder grant, create-only initialization, verified Sync and backup. |

Fixture corrections were confined to ignored local code: initial reservation
expectation, bounded main-thread barriers instead of unbounded idle waiting,
CLEAR_TASK startup, refreshed semantic document/folder actions, trusted-parent
canonicalization, checked exception and complete output-picker handoff. The first
Sync attempt briefly granted the disposable UID the broader Documents tree; its
explicit harness guard stopped before Initialize/publication. Actual Disconnect
released that grant before the successful exact-child retry. No owner grant or
existing document was deleted or overwritten. Earlier biometric outcomes remain
explicitly untraced/canceled rather than retroactively attributed to these fixes.

Relevant actual commands (full per-phase parameters in R4DRegression.java/build.py,
files.json and raw logs):

```sh
python3 .qualification/r4d/device/build.py
adb install -t --no-incremental .gradle/r4d-device/<new-test-package>/device.apk
adb install -r -t --no-incremental .gradle/r4d-device/<existing-test-package>/device.apk
adb shell am force-stop <disposable-test-package>
adb shell am instrument -w -r -e phase <phase> [phase-specific extras] <disposable-test-package>/org.totipo.android.R4DRegression
java -cp <published-0.3.2-inspection-classpath> InspectBackup <independently-pulled-test-zips>
```

The existing owner's binary identity was rechecked after all runs. Disposable
applications, encrypted fault residues, independent Downloads ZIPs and the new Sync
tree are retained for review; only the three test processes were force-stopped.
USB stay-awake was enabled for execution and disabled afterward (`settings` reads0).
Its initial value was not captured because an early setup helper stopped on the
expected absent-package pm exit status; do not claim an exact original-setting
restoration. No other global display/security setting was changed.

## Freeze, human Nix and shortest safe completion path

Reinspected actual package.nix selection: app/src, gradle, tools and explicit
Gradle/lock/version/branding/license inputs; excluded .git/.gradle/build/.direnv
components and symlinks. package.nix/flake.nix/flake.lock/package-deps.json also
participate. flake.nix's separate release-tooling fileset adds public signing and
release workflow inputs (unchanged). AGENTS/README/MULTI_VAULT/provenance/docs/
release README/review are outside effective checks. Report-only updates do not
invalidate Gradle/artifacts/Nix.

- **Production source stable:** diagnostic pin and corrected debug primitive are
  final; definitive normal/strict use these exact inputs.
- **JVM tests stable:** diagnostic assertion and isolated scan-fault regression
  finalized before definitive 493-test normal/strict passes.
- **Build/package configuration stable:** reviewed 0.3.2 pins, locks, hashes,
  readiness and human-refreshed cache agree; other build/release identities stay fixed.
- **Qualification harness/tooling stable:** active version pins and both procedures
  finalized; host suites pass. A later included device harness fix requires renewed
  affected checks and refrozen human Nix.

All new intended files (only this report) are Git-known through intent-to-add;
actual staged-content diff is empty. Temporary inspection/build/cache/signing
state and disposable data remain outside Git source membership.
Final included-input diff SHA-256:
`d965f1ab8ab8a3f1f750bc7408db174157c75e3220b29e6db9b846fcfba1433d`.
Freeze covers selected Gradle/tool inputs plus package/evaluator/cache inputs and
is rechecked after artifact verification. Final human command is exactly:

```sh
nix flake check
```

**Human-reported PASS, 2026-11-16.** User reported `nix flake check` passed.
Rechecked starting HEAD and included-input digest: both match the definitive freeze.
Agent Nix executions remain zero. Only excluded report/README/user-guide changes
follow the recorded result; no Gradle, artifact or Nix rerun is required. The PASS is user-reported,
not inferred from host checks or cache generation.

Shortest safe path: provision an actual API30 device/emulator outside this jail
and run the prepared normal-use and real SAF backup/restore/recovery procedures
on the final product payload in fresh disposable data. The current pinned SDK
deliberately excludes emulator/images; do not infer availability from adb. Review
any standalone instrumentation for API30 library availability before reuse. The
Pixel API37 PASS cannot substitute for API30. Complete the separately unexecuted
physical cases in the matrix and reconcile the first untraced biometric timing
outcome before a release-readiness decision. No Android dependency or protocol
change is justified solely by missing qualification infrastructure.

No shared Java compatibility/crypto/storage defect was observed in the executed
API37 paths. No API30 execution result exists. No production source fix was needed
for these device runs; harness failures and biometric timing caveats remain
explicit above. Host checks remain passed on the same frozen inputs.
API30 and the separately unexecuted device/provider cases remain pending; the
human Nix gate has passed on the frozen inputs. If API30 execution reveals a shared defect,
preserve the smallest reproducer and stop that path for an upstream published fix;
do not patch/vendor Java or lower guarantees. **Not ready for release preparation.**

## Full definitive XML accounting

Both definitive full checkpoints account for the same complete 493-test set;
all failure/error/skip totals are zero. XML is preserved independently of clean.

| Suite (org.totipo.android prefix omitted) | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| AndroidVaultControllerTest | 91 | 0 | 0 | 0 |
| BackupControllerTest | 10 | 0 | 0 | 0 |
| BackupExportTest | 7 | 0 | 0 | 0 |
| BackupRestoreFlowTest | 10 | 0 | 0 | 0 |
| Base32Test | 3 | 0 | 0 | 0 |
| BiometricRecordTest | 4 | 0 | 0 | 0 |
| BiometricSourceBoundaryTest | 2 | 0 | 0 | 0 |
| ControllerRetirementTest | 26 | 0 | 0 | 0 |
| CoreDependencySmokeTest | 1 | 0 | 0 | 0 |
| DailyDriverUxTest | 8 | 0 | 0 | 0 |
| DailyUseUxTest | 7 | 0 | 0 | 0 |
| EnrollmentSourceGuardTest | 2 | 0 | 0 | 0 |
| InactivityLockTest | 6 | 0 | 0 | 0 |
| LocalReplicaOwnerTest | 2 | 0 | 0 | 0 |
| MultiVaultUiBoundaryTest | 3 | 0 | 0 | 0 |
| OtpAuthEnrollmentSourceGuardTest | 2 | 0 | 0 | 0 |
| OtpAuthSupplyChainGuardTest | 1 | 0 | 0 | 0 |
| OtpAuthUriParserTest | 7 | 0 | 0 | 0 |
| PerVaultBiometricCredentialsTest | 16 | 0 | 0 | 0 |
| RowRevealCopyTest | 8 | 0 | 0 | 0 |
| SecurityControllerTest | 27 | 0 | 0 | 0 |
| SyncGrantOwnershipTest | 10 | 0 | 0 | 0 |
| TokenLifecycleSourceGuardTest | 3 | 0 | 0 | 0 |
| TotpControllerTest | 29 | 0 | 0 | 0 |
| TotpPresentationTest | 16 | 0 | 0 | 0 |
| TotpSourceGuardTest | 5 | 0 | 0 | 0 |
| VaultManagementTest | 11 | 0 | 0 | 0 |
| VaultRegistryTest | 20 | 0 | 0 | 0 |
| debug.AuthPerfSafetyTest | 8 | 0 | 0 | 0 |
| debug.ProbeLogicTest | 6 | 0 | 0 | 0 |
| provider.ImmutableCandidateClassifierTest | 20 | 0 | 0 | 0 |
| provider.ProviderSnapshotTest | 12 | 0 | 0 | 0 |
| reconcile.CoordinatedPrivateStoreTest | 7 | 0 | 0 | 0 |
| reconcile.CoordinatedRestoreTest | 3 | 0 | 0 | 0 |
| reconcile.CoordinationSourceGuardTest | 2 | 0 | 0 | 0 |
| reconcile.DetachedVaultAuthenticationTest | 4 | 0 | 0 | 0 |
| reconcile.ForegroundVaultCoordinatorTest | 23 | 0 | 0 | 0 |
| reconcile.ImmutableCandidateImporterTest | 12 | 0 | 0 | 0 |
| reconcile.TotpCoordinatorTest | 12 | 0 | 0 | 0 |
| reconcile.VaultEnrollmentTest | 5 | 0 | 0 | 0 |
| sync.BootstrapSourceGuardTest | 1 | 0 | 0 | 0 |
| sync.OutboundImmutablePlannerTest | 6 | 0 | 0 | 0 |
| sync.ProviderIoRetirementTest | 5 | 0 | 0 | 0 |
| sync.ProviderObjectWriterTest | 5 | 0 | 0 | 0 |
| sync.SyncFolderBindingTest | 11 | 0 | 0 | 0 |
| sync.SyncSourceGuardTest | 2 | 0 | 0 | 0 |
| sync.VaultBootstrapTest | 12 | 0 | 0 | 0 |
| **TOTAL** | **493** | **0** | **0** | **0** |

## Exact final Git state

Branch: `main`. HEAD: `569986718d2f4eeb55d8d381cb42ea37a7404903`.
All changes uncommitted; existing modifications unstaged, new report intent-to-add
only. `git diff --cached --quiet`: PASS; no staged file contents.

`git status --short`:

```text
 M AGENTS.md
 M README.md
 M TOTIPO_JAVA_DEPENDENCY.md
 M app/build.gradle.kts
 M app/gradle.lockfile
 M app/src/debug/java/org/totipo/android/debug/LocalNioQualification.java
 M app/src/main/java/org/totipo/android/AndroidVaultController.java
 M app/src/test/java/org/totipo/android/BackupControllerTest.java
 M app/src/test/java/org/totipo/android/DailyDriverUxTest.java
 M docs/ARCHITECTURE.md
 M docs/USER_GUIDE.md
 M gradle/verification-metadata.xml
 M package-deps.json
 M package.nix
 M release/README.md
 A review/BACKUP_RESTORE_R4D_ANDROID_FINAL_QUALIFICATION_REPORT.md
 M tools/device/BACKUP_EXPORT_QUALIFICATION.md
 M tools/device/BACKUP_RESTORE_QUALIFICATION.md
 M tools/device/build-saf-bootstrap-tests.py
 M tools/device/build-saf-inbound-tests.py
 M tools/device/build-saf-outbound-tests.py
 M tools/device/check-row-reveal.py
 M tools/device/qualify-saf-outbound.py
 M tools/device/qualify-security.py
 M tools/release/release.py
 M tools/release/test_release.py
```

`git diff --name-only` (all changed intended files):

```text
AGENTS.md
README.md
TOTIPO_JAVA_DEPENDENCY.md
app/build.gradle.kts
app/gradle.lockfile
app/src/debug/java/org/totipo/android/debug/LocalNioQualification.java
app/src/main/java/org/totipo/android/AndroidVaultController.java
app/src/test/java/org/totipo/android/BackupControllerTest.java
app/src/test/java/org/totipo/android/DailyDriverUxTest.java
docs/ARCHITECTURE.md
docs/USER_GUIDE.md
gradle/verification-metadata.xml
package-deps.json
package.nix
release/README.md
review/BACKUP_RESTORE_R4D_ANDROID_FINAL_QUALIFICATION_REPORT.md
tools/device/BACKUP_EXPORT_QUALIFICATION.md
tools/device/BACKUP_RESTORE_QUALIFICATION.md
tools/device/build-saf-bootstrap-tests.py
tools/device/build-saf-inbound-tests.py
tools/device/build-saf-outbound-tests.py
tools/device/check-row-reveal.py
tools/device/qualify-saf-outbound.py
tools/device/qualify-security.py
tools/release/release.py
tools/release/test_release.py
```

`git diff --cached --name-only`:

```text

```
