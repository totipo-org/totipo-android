# Android build, signing and qualification simplification audit

Design only; 2026-10-14. Classification: **DOCS_ONLY**. Recommendations are not
implementation authorization. This report is the only changed file, uncommitted.

## 1. Executive summary

Android can use only Nix-provided Gradle for its supported command-line operations.
The package already does so with the same 9.8.0 version as the wrapper. AGP 9.4.0,
JDK 17, API 37 and Build Tools 36.0.0 are compatible on paper and have historical
package qualification evidence. Wrapper removal still needs a real development,
dependency-refresh and device-builder qualification pass; this audit ran no builds.

Recommend **Option B, bounded to build/release support**, retaining device behavior
harnesses. Delete the duplicate Gradle acquisition path, bespoke release preparation
and tag controller, reviewed-source digest inventory and restricted Actions YAML
parser. Keep Gradle dependency locks and strict artifact verification, Nix's pinned
environment/download cache, one small final-APK policy check, native apksigner
verification, exact signed-file hashes, and the small unsigned/signed payload
comparator. Keep protected signing separated from qualification and publication.

The current checkout has **25 Python files, 3,896 physical lines**, including tests;
the historical 22-file count is obsolete. Most device Python is orchestration, but
its isolation, grant retention and restoration requirements make wholesale deletion
premature. Production signing already delegates to SDK apksigner; it is not a custom
cryptographic implementation. The excessive work is around that boundary.

Five Python files can disappear in the recommended option: verify-wrapper.py,
test-build-metadata.py, source.py, test_source.py and test_policy.py, after transferring
the small necessary identity/parser tests into the retained release tests. This is
not a proposal to port the release controller into Java or Bash.

Security simplification must improve the final-artifact policy: verify-apk.py checks
permissions and ingress, but **does not explicitly verify allowBackup, release
debuggable/testOnly, or a complete exported-component inventory**. Historical prose
describing an APK “backup boundary” is broader than the actual verifier. Replace its
large product-class inventory with a smaller, more complete platform policy.

No cryptographic, vault-format, protocol, controller or Android behavior test is
proposed for deletion. Reduced LOC is a maintenance estimate, not security evidence.

## 2. Starting HEAD, version and build identity

Recorded before any write:

```text
git branch --show-current: main
git rev-parse HEAD: bd2671fa37c2e87042fd8699b0cc8f4a63d9daa4
git status --short: empty
VERSION: 0.1.2
VERSION_CODE: 3
```

Read AGENTS.md completely. Inspected README, release/signing docs, actual Gradle,
Nix, workflows, all current Python tools, shell tools, device Java harness subjects,
application security adapters and their tests. Qualification/signing context includes
the qualification ladder, biometric report, signed-release machinery, production
identity, publication fix, release preparation/Actions reports, previous support
audit/Wave 2A/2B and completed qualification-signing migration. Historical reports
are evidence/context, not immutable architecture requirements. No sibling repository,
other worktree or sub-agent was inspected or used. No Java guidance fetch was needed:
no application-operation behavior is being changed.

| Property | Actual checkout / observation |
| --- | --- |
| Application ID / namespace | org.totipo.android |
| Gradle | wrapper 9.8.0; Nix gradle_9 asserted 9.8.0 in package.nix |
| AGP | com.android.application 9.4.0 |
| JDK | Java 17 language/toolchain; active Nix OpenJDK 17.0.20.1+1-nixos, observed with java -version |
| SDK | compile/target 37; platform android-37.0, package revision 2, extension 22 |
| Minimum install SDK | 26; actual vault operations and strong biometric path require API 30+ |
| SDK tools | build-tools 36.0.0; platform-tools 37.0.1; command-line tools 22.0 |
| Runtime graph | direct totipo-storage-nio 0.2.0; core 0.2.0; BC bcprov-jdk18on 1.86 |
| Host qualification/package | x86_64-linux only; CI ubuntu-24.04 |
| Build variants | debug signed locally by AGP; release unminified and unsigned; no flavors, AAB/Play or native-library build configured |
| Tasks | check, assembleDebug, assembleRelease, installDebug; custom Maven/build-classpath/APK verification tasks |
| Environment | .envrc uses flake; default and ci shells share Android/JDK/Gradle/Python; default additionally supplies jailed Codex |

Active java and gradle executables resolve to Nix store paths. JAVA_HOME and both SDK
environment variables resolve to the Nix-managed installations. SDK source.properties
were read directly, not through Nix evaluation. No supported host-installed Gradle,
host SDK or different per-job JDK path appears in source. Release publication uses
runner-provided gh and ordinary shell utilities; these are a remaining external-tool
pinning gap, not Android Gradle differences.

Nix lock identities, transcribed from flake.lock (revision and narHash):

| Node | Revision | narHash |
| --- | --- | --- |
| nixpkgs_3 (root Android environment) | 39ad350a0602fa0a58a544344e3e9187526ea45c | sha256-o0N4g6uSOIppU7CGToNvGlMf5vC+B6O6ukFHKY/TKOY= |
| flake-utils / flake-utils_2 | 11707dc2f618dd54ca8739b309ec4fc024de578b | sha256-l0KFg5HjrsfsO/JpG+r7fRrqm12kzFHyUHqHCVpMMbI= |
| jailed-agents | 82b9d99d454bd752ae028e8a4bf0c90daf1c23ef | sha256-/nKW6wc9bUj/E0Sa1QebsIYwmXruIkuDe6KVOl9loIo= |
| llm-agents | 4bb57cff45b5554a02dba0cf7a8c7f4f010a864d | sha256-Q6VbwYvayJgN/7x4jI7KnSkd5NGyUwfUw57Dho4jJAE= |
| nixpkgs (jailed-agents) | a799d3e3886da994fa307f817a6bc705ae538eeb | sha256-3av0pIjlOWQ6rDbNOmpUSvbNnJkGORQKKjb4LtCZsIY= |
| nixpkgs_2 (llm-agents) | f45c6f04c2f013f004bf94e284e95d72898d9393 | sha256-6k3n7X2UsZ6tAlWTz99RimiZab+17tMUrw9sRUcELiA= |
| jail-nix | 42b355c38ca63dab4904acc5c0d95f17954a8c9b | sha256-h4MGNbOo7L3RHi4uNFmsg5g17/DHXEfnv/xiG6BrNFQ= |
| bun2nix | 07a5bfc8ac36c5370199343fa620b69f63186e33 | sha256-1/SLBjSYBzDdDhvLQ83wrzBakyS/xNGgtxHGNuveOEQ= |
| flake-parts | 024633cd702b10285db5cb19b40ad48d2399ba60 | sha256-zTwuwezD0w3hpga6a6P8emidzAZFyWqlNeRBlpWOOl8= |
| treefmt-nix | 4cfe8d7065cc8ca91c0a50361b6dd74920eeb1f5 | sha256-qScE7Wz6Zg7eg8ItUpnkmKgst8weWschGe0E8jM4rj4= |
| systems / systems_2 | da67096a3b9bf56a91d16901293e51ba5b49a27e | sha256-Vy1rq5AaRuLzOxct8nz4T6wlgyUR7zLU309k9mBC768= |
| systems_3 | d36eeed142f88b5a636f2e3856a53c61654cf29b | sha256-uuanbkFk8a313Zfrxdcz8Us/yQtaxNnKJtX8v0ZPpd8= |

These agent-environment inputs are not multiple Android toolchains. Keep their
evaluation separate from decisions about the supported Android environment.

## 3. Actual build and signing architecture

Development: Nix/direnv supplies JDK/SDK/Python plus Gradle, but README/bootstrap use
./gradlew, which independently acquires the Gradle distribution. Wrapper JAR and
distribution are pinned. Gradle/AGP compile Java, merge manifests, compile resources,
D8 and package APKs; no production signingConfig is present.

Package: package.nix uses Nix Gradle, fetchDeps MITM download cache from
package-deps.json, strict dependency verification and selected source filtering.
Build phase assembles both variants; check phase runs root/app checks; installCheck
runs release Python unit tests and both APK verifications. Only unsigned release
is installed. checks.android and packages.default are the same androidPackage.
checks.release-apps additionally tests help/incomplete-caller handling of two Nix apps.

Release: source preparation allocates versionCode from all consumed annotated tags;
the tag command creates/pushes an exact-shaped annotated tag. Manual main dispatch
binds version and full SHA. Workflow stages are qualify → protected sign → verify →
publish. Three same-run artifact transfers carry qualified-unsigned, signed-candidate
and verified-release. Preparation materializes the already-qualified derivation;
this is not a second independent APK construction.

Signing step resolves Nix SDK/JDK first, restricts secret scope, decodes PKCS12 in a
private temporary directory, invokes native apksigner with a separate output file,
deletes the key and has exit/signal cleanup. No Gradle/Nix/repository Python executes
in that secret-bearing step. Signing enables v2/v3, disables v1/v4 and preserves
alignment. Verify uses native apksigner/zipalign plus policy/provenance/payload checks.
Publish uploads three assets to a draft, downloads and compares them before publication.

## 4. Tooling inventory by language and LOC

Counts are physical lines including blanks/comments/docstrings/embedded XML, from
current Git-known paths. They differ intentionally from earlier nonblank counts.
Generated caches, binaries, secrets, lock/data files and report prose are not code LOC.

| Surface | Files | Lines | Interpretation |
| --- | ---: | ---: | --- |
| Python support, including tests | 25 | 3,896 | device 14 files / 1,793 lines; release 9 / 1,931; top-level verifiers 2 / 172 |
| Custom shell .sh | 3 | 162 | bootstrap 27, toolchain verifier 43, disposable signing smoke 92 |
| Vendor wrapper scripts | 2 | 360 | gradlew 248; gradlew.bat 112; wrapper JAR counted separately as binary |
| Nix | 2 | 233 | flake 137; package 96 |
| Gradle Kotlin DSL | 3 | 197 | root, settings, app; no product Kotlin |
| Workflow YAML | 2 | 235 | ci 32, release 203 |
| Device Java support | 11 | 2,511 | real platform assertions, observer and fixture code |
| App main/debug/release Java | 55 | 6,867 | includes debug diagnostics; not build/release scripts |
| App JVM test Java | 40 | 6,129 | includes fixtures and source guards; not 40 independent test suites |

Other support inputs: VERSION/CODE, branding-provenance.json, dependency locks,
verification XML, package-deps.json, flake.lock, gradle.properties, wrapper properties,
reviewed-inputs.sha256 (12 lines), public certificate/digest, .envrc, .gitignore and
.gitattributes. Documentation includes live READMEs/AGENTS/device checkpoints and
historical review evidence. None is a new tool language.

## 5. Gradle, Nix and AGP compatibility

Google's AGP 9.4 compatibility table specifies minimum Gradle 9.6.0, JDK 17,
Build Tools 36.0.0 and maximum API 37. This supports the selected 9.8.0/17/36/37
combination; it does not replace a real build. [AGP 9.4 release notes](https://developer.android.com/build/releases/agp-9-4-0-release-notes).

**Can Android safely use only Nix-provided Gradle? Yes, for the supported Nix CLI/CI
scope, after migration qualification.** Neither AGP nor SDK requires a wrapper.
The package already exercises this execution path. Windows/Android Studio outside
Nix are not documented qualification targets. Retaining the wrapper is reasonable
if the owner chooses to support those workflows; it is not necessary for Android
correctness. Wrapper checksum verification concerns its separate acquisition path.
[Gradle wrapper documentation](https://docs.gradle.org/current/userguide/gradle_wrapper.html).

Exact migration plan, not executed:

1. Retain flake's gradle_9 override to JDK17, package's 9.8.0 assertion, both SDK
   variables and patched AAPT2 override. Replace supported ./gradlew invocations
   with gradle in the Nix shell; use nix develop .#ci --command gradle when outside.
2. Qualify ordinary/strict commands, installDebug, intentional lock/metadata refresh
   on a disposable source fixture, and release metadata parsing through Nix Gradle.
   Check dependency/cache inputs and unsigned APK results; no production key needed.
3. Delete gradlew, gradlew.bat, gradle/wrapper/gradle-wrapper.jar,
   gradle/wrapper/gradle-wrapper.properties and tools/verify-wrapper.py. Remove the
   root Wrapper task configuration. **Retain gradle/verification-metadata.xml.**
4. Retire bootstrap/toolchain probes as below; update current README/AGENTS/release
   instructions and tests referencing wrapper paths. Retire the reviewed-source
   inventory in B; in A, deliberate wrapper removal would require updating its consumers.
5. Verify all supported callers, final Android/package gates, then one human Nix
   gate. Record the wrapper's non-Nix convenience guarantee being withdrawn.

Nix SDK license acceptance and selected unfree-package allowlist are explicit.
No sdkmanager population, local.properties, emulator/image/NDK/CMake acquisition
is configured. Keep those choices. AGP's Maven AAPT2 ELF is unsuitable in the current
Nix environment; retain the Nix-patched SDK AAPT2 selection and visible warning.

Ordinary Gradle build cache remains useful for iteration. Package disables it for
isolated qualification; strict local gate additionally disables daemon/configuration
cache and reruns tasks. Configuration cache is off because custom graph tasks resolve
configurations at execution. Removing those tasks is not required: make their inputs
and providers compatible only if a separately qualified change is worthwhile.
The pinned MITM response cache is not the Gradle task-output cache and is not redundant
with locks or verification metadata.

## 6. Every Python script and actual callers

Classification: **E** specialized validation; **O** orchestration of native tools;
**D** duplicate policy/provider behavior; **H** historical compatibility;
**T** temporary migration. Mixed labels reflect mixed responsibilities. Cost L/M/H
means relative maintenance exposure, not measured hours. All scripts use stdlib.

| Path (under tools/) | Lines / class / cost | Actual callers | Inputs → outputs; failure prevented; native overlap; tests |
| --- | --- | --- | --- |
| verify-wrapper.py | 13 D L | bootstrap; AGENTS final human procedure | JAR/properties → pin verdict; wrong wrapper bytes; overlaps distribution checksum but additionally pins executable JAR; no dedicated suite |
| verify-apk.py | 159 E/D H | Gradle release task; package installCheck; release unsigned/signed helpers; SAF/M3D; AGENTS | APK, SDK, branding sources → boundary verdict/hash; forbidden permissions/ingress/debug payload; aapt2 supplies decoding, ZIP/DEX lists are custom; actual APK gates/smoke, no dedicated parser fixture suite |
| release/payload.py | 52 E M | finalize/publish; signing smoke; test_release | unsigned/signed APKs → payload equivalence; authorized signer signing altered build payload; stdlib ZIP with raw compressed-byte/header comparison; synthetic ZIP negatives and disposable signed APK |
| release/release.py | 303 O/E H | two Nix apps; workflow identity/prepare/bundle/finalize/publish/action-policy; docs diagnostic | Git/source/SDK/bundles/gh → metadata/ref actions/provenance/releases; wrong source/artifact/publication; overlaps Git/gh/Nix orchestration; three release suites plus source suite/smoke |
| release/source.py | 446 O/D/H H | release.py; signed verifier fingerprint; release tests | Git refs/files/public identity → source/tag/code readiness; wrong tag/code, source mutation; custom version allocation, source hashes and YAML parser; test_source/test_policy/test_release |
| release/verify-signed-apk.py | 46 O/E M | release.verify_signed; signing smoke | APK/public digest/provenance/unsigned → verdict; wrong signer/schemes/metadata/alignment; delegates real signature verification to apksigner; mocked SDK negatives + real smoke |
| release/test-build-metadata.py | 37 D M | documented manual local check only | extracted Gradle parser/temporary files → offline Gradle verdict; malformed versionCode/defaults; duplicates parser assertions and final metadata check; itself a standalone harness, not unittest discovery |
| release/test_release.py | 356 E/D H | package and qualify-job unittest discovery; manual | synthetic ZIP/provenance/SDK/workflow fixtures → tests; payload/signer/version/handoff/source policy errors; includes brittle workflow text/count assertions; real smoke supplements mocks |
| release/test_source.py | 452 E/D/H H | same discovery/manual | temporary Git repositories/bare remotes → tests; controller races, tags/version history/atomic preparation; tests complexity of bespoke tag mutation rather than APK security; CLI fixture uses public identity when available |
| release/test_policy.py | 117 D M | same discovery/manual | malformed versions/fingerprints/actions/repository fixtures → tests; restricted grammar and ownership regressions; mostly custom workflow-parser contracts; no external fixtures |
| release/test_publication.py | 122 E M | same discovery/manual | mocked gh HTTP/draft/download failures → tests; false absence, adoption/overwrite and premature publication; native gh should handle transport, custom draft sequencing still needs focused tests |
| device/android_harness.py | 195 O/E M | all builders; row/security/SAF/M3D; signing owner | explicit SDK/sources/classes/APKs/fixture → dex/APK/signature/restoration; stale classes, ambiguous ZIP, wrong signer, damaged fixture; custom APK construction overlaps AGP; test_android_harness |
| device/qualification_signing.py | 280 E/O/T H | SAF outbound/bootstrap, M3D; generic inbound/ingress signing and status/migrate docs | explicit ignored identity or old key/password → validated identity/signed APK/migration; continuity failure, production-key use, unsafe local paths; keytool/apksigner are native, migration transaction is custom; 21 identity/caller tests |
| device/build-otpauth-ingress-tests.py | 30 O M | documented historical ingress procedure | two Java sources/SDK → unsigned standalone test APK; platform ingress test availability; overlaps AGP instrumentation packaging; shared helper tests + real ingress report |
| device/build-saf-inbound-tests.py | 53 O M | historical/current retained manual inbound procedure | Java/cache jars → fixture/unsigned instrumentation; isolated inbound execution; overlaps AGP and uses mutable-cache jar glob; shared tests + real SAF evidence |
| device/build-saf-outbound-tests.py | 53 O M | qualify-saf-outbound | Java/cache jars → fixture/unsigned instrumentation; outbound execution; same overlap; shared tests + outbound evidence |
| device/build-saf-bootstrap-tests.py | 53 O M | qualify-saf-bootstrap | Java/cache jars → fixture/unsigned instrumentation; Join/Initialize/orphan execution; same overlap; shared tests + bootstrap evidence |
| device/check-row-reveal.py | 61 O/E M | manual row/layout procedures | final debug classes/APK/Java → isolated self-target APK/install/run/removal; reveal/layout regression, replacement of existing test package; native build overlap but isolation differs; RowRevealRegression + physical reports |
| device/qualify-security.py | 99 O/E H | manual lifecycle/biometric procedure | final release DEX/resources plus Java → isolated package/phases/log/hash; security/lifecycle behavior; custom repackaging avoids production install/secret state; SecurityRegression/TokenLifecycleRegression + physical evidence |
| device/qualify-saf-bootstrap.py | 80 O/E M | manual bootstrap qualification | installed product/final unsigned/local identity/disposable provider → tests/logs/restoration; wrong signer and destructive fixture state; native tools cannot choose safe fixture; Java/shared tests/caller tests |
| device/qualify-saf-outbound.py | 95 O/E M | manual outbound qualification | same plus alternate/invalid VAULT fixtures → blocked/matching logs/restoration; wrong-vault mutation; Java/shared/caller tests |
| device/qualify-syncthing-e2e.py | 271 E/O H | M3D_SYNCTHING_CHECKPOINTS.md/manual | observer/local signer/checkpoint JSON/human observations → evidence/failure latch; capture mistaken for assertion, changed VAULT/session/objects; no native tool supplies this domain oracle; test-syncthing-e2e/caller tests |
| device/test-syncthing-e2e.py | 97 E L | checkpoint docs/manual direct runner | synthetic before/after evidence → 10 oracle tests; invalid transport/security conclusions; no SDK substitute |
| device/test_android_harness.py | 125 E M | manual host discovery | synthetic tools/classes/ZIP/disposable directory → 8 tests; missing tools, stale class selection, redaction/restoration; follows construction helper lifetime |
| device/test_qualification_signing.py | 301 E/T H | manual host discovery | temporary test keys/metadata/SDK/mock device commands → 21 tests; unsafe migration/identity and installs before signer check; actual SDK plus failure injection; must shrink with removed migration |

No current Python file can be labeled wholly unused on the available evidence.
Manual/historical documented device entry points are not automatic callers, but
that alone does not make their behavior tests dead. test-build-metadata is redundant,
not unreachable. Source legacy-tag parsing and qualification migration are historical
code inside still-used tools. No third-party Python dependency needs replacing.

## 7. Android platform-security checks

| Boundary | Implemented behavior / evidence | Disposition |
| --- | --- | --- |
| Permissions | Main manifest only USE_BIOMETRIC; verifier checks exact set and forbids camera/network/storage permissions | KEEP final merged-APK allowlist; source declarations alone cannot catch manifest-merger additions |
| Components / visibility / intents | Main launcher and OtpAuthEnrollmentActivity exported; exact VIEW otpauth://totp and SEND text/plain filters; no main service/provider/receiver/queries; debug has extra exported probe Activities | KEEP complete final component/alias/export/process policy and native intent qualification; verifier currently only checks service absence and enrollment, not every component |
| Private storage / backup | LocalReplicaOwner uses getNoBackupFilesDir/totipo-vault; biometric record also no-backup; main allowBackup=false; debug additionally sets extraction exclusions | KEEP actual path, ownership and platform behavior evidence; add explicit final backup policy assertion; do not imply textual check proves OEM transfer behavior |
| SAF grants | ContentResolver persisted READ/WRITE take/release, stored binding reconciled against actual grants, folder accessibility separate from VaultId | KEEP SyncFolderBinding tests, real SAF/provider tests and retained-grant update smoke; no broad storage permission substitute |
| File mutation | Single coordinated private-store owner; bounded provider traversal/read; create-only immutable publication with readback/postflight; wrong/missing/invalid/different VAULT blocks | KEEP provider/import/publication/coordinated-store Java tests and real SAF cases; hashes here express semantic invariants, not redundant build identity |
| Biometric / Keystore | API30 strong-only CryptoObject, AES256-GCM non-exportable AndroidKeyStore key, auth-per-use, enrollment invalidation, VaultId AAD; password fallback, no PIN/weak biometric fallback | KEEP controller/record tests and physical enrollment/cancel/lock/restart/invalidation evidence; JVM source strings cannot prove Keystore authorization |
| Secret/session lifetime | Manual and 15-minute monotonic inactivity lock; session close, pending editor/reveal retirement, transient buffer clearing; process starts locked; Activity rotation retains controller | KEEP deterministic lifecycle/controller tests and relevant device phases; mutable-buffer wiping is best effort, no guaranteed JVM erasure |
| Foreground/background | Background time counts toward inactivity; return checks expiration; no background service/Internet transport, foreground Sync only | KEEP real lifecycle tests; do not replace with manifest service absence alone |
| Clipboard / screen | FLAG_SECURE windows/dialogs; no widget saved setup; clipboard sensitive flag plus ownership/text check before cleanup (API28+ clear) | KEEP row/reveal/copy device tests; clipboard cleanup is best effort, no atomic compare-and-clear or universal secrecy claim |
| Logs / diagnostics | Reviewed main adapters use controlled diagnostics rather than raw exceptions; no Log./printStackTrace match found in main source search | KEEP focused secrecy tests/manual diagnostic review; neither grep nor DEX blacklist proves all library/OS crash output harmless |
| Signing / distribution | Permanent certificate pin; native valid signature plus signed-file identity | KEEP independently of application tests; a correct application source does not authenticate a downloaded APK |

Actual main implementation has biometric Keystore integration, not a hypothetical
feature. It deliberately stores an encrypted reusable password for convenience;
the existing security tradeoff remains. No claim of guaranteed StrongBox/hardware
attestation was found or added. Delete authors a tombstone, not secure erasure.

Device Java inventory (all KEEP in both bounded options):

| tools/device path | Lines | Inputs / outputs / caller; specialized failure prevented |
| --- | ---: | --- |
| InboundFixture.java | 31 | host main used by SAF builders; disposable Java-created vault/objects; avoids fake protocol fixtures |
| LocalStoreQualification.java | 77 | bootstrap/outbound harness helper; disposable root → ownership/orphan checks; private-store corruption/competing owner |
| OtpAuthIngressRegression.java | 284 | ingress builder/instrumentation; intents/activity/controller → assertions; malformed transport, saved-secret/redelivery/lifecycle authorship |
| OtpAuthUiObserver.java | 44 | ingress builder/self-target observer; framework UI → public transport observation; qualifies actual external interaction |
| RowRevealRegression.java | 658 | row runner/self-target package; views/events → reveal/copy/layout matrix; Android rendering/state leakage |
| SafBootstrapRegression.java | 250 | bootstrap builder/qualifier; disposable provider/local root → Join/Initialize/orphan/different-vault checks |
| SafInboundRegression.java | 244 | inbound builder/manual instrumentation; grant/provider/session → import/reveal/read-only assertions |
| SafOutboundRegression.java | 293 | outbound builder/qualifier; provider/session → create-only/identity blocking/readback checks |
| SecurityRegression.java | 204 | security runner; isolated package/human biometrics → authorization, lock, deadline and restart checks |
| TokenLifecycleRegression.java | 180 | security runner lifecycle phase; isolated UI/real Java fixture → Add/Edit/Delete/Resolve correctness |
| SyncthingE2eRegression.java | 246 | M3D observer; product/provider/human transport → sanitized checkpoint JSON; actual SAF/session evidence |

These are specialized tests, with real qualification reports and synthetic Java
fixtures as appropriate. Maintenance is medium/high because they reflect controller,
view and provider contracts; AGP can package them but cannot supply their assertions.
No dedicated app/src/androidTest or emulator/managed-device configuration exists.
The detached security package copies qualified release DEX but substitutes manifest
and package identity: useful release-code evidence, not proof of the installed
production APK's complete manifest, signer or update behavior.

## 8. Signing, certificate and artifact identity

Production public identity is release/signing/android-release-cert.pem and .sha256:

```text
74fd4c313856e1773fd174fba4ecd2a12cc4fde85ae34a3d8a3e986353b01632
```

The provisioning report records RSA4096/SHA256withRSA, CN=Totipo Android Release,
50-year validity. No private key, alias/password or production credential was opened
or used in this audit. No lineage/rotation configuration is proposed.

| Property | What establishes it | What it does not establish |
| --- | --- | --- |
| A. Unsigned reproducibility | Independent rebuild under frozen inputs and full unsigned APK SHA-256 comparison | Not automatically proved by a cache hit, Nix derivation ID, locks or one build |
| B. Signed reproducibility | Would require repeated controlled signing and complete signed-byte comparison | Current workflow/smoke does not qualify it; two signed identities or payload comparison are not this proof |
| C. Exact signed identity | SHA-256 of accepted signed APK and equality at transfer/download | Does not establish intended certificate or trusted construction by itself |
| D. Certificate continuity | Native apksigner verifies valid signatures and actual sole signer equals committed expected certificate digest; update installation evidence | Same certificate can sign different or malicious payloads; not unsigned-build equivalence |

Necessary release checks: application ID, versionName and versionCode against reviewed
VERSION/CODE; min/target SDK and release flags; valid native signature for supported
platforms; one expected signer with the permanent digest; current v2/v3 enabled and
v1/v4 disabled; native alignment check; unsigned input immutable; payload corresponds
to qualified construction; exact accepted signed bytes distributed. Expected identity
must come from reviewed source, never candidate APK/provenance alone. Intended
certificate comparison also rules out accidental debug-key release.

Native apksigner is already the signature authority. Keep its exit-status and
--verbose --print-certs output checks; no custom signature-block parser is needed
for signed acceptance. apksigner validates supported Android signature behavior;
changing the APK after signing invalidates signatures. [SDK apksigner](https://developer.android.com/tools/apksigner).

AGP constructs aligned APKs. Keep zipalign -c on final candidates, without rewriting
them; do not introduce a production ZIP-repacking/alignment phase. SDK docs explicitly
distinguish AGP's automatic alignment from verification. [SDK zipalign](https://developer.android.com/tools/zipalign).

payload.py is a justified 52-line gap: apksigner proves a signature over the candidate,
not that signing preserved the accepted unsigned ZIP payload. It compares entry
order, metadata, local headers, compressed and decoded bytes while allowing signing
envelope/offset differences. Keep it with relevant negative tests. SDK apkanalyzer
apk compare is a size comparison, not exact payload-equivalence proof.

The unsigned signing-block magic/EOCD check in verify-apk.py can be simplified only
without treating every apksigner failure as “unsigned”: corruption/tool failure also
fails verification. Either retain the small unsigned envelope check or withdraw the
strong no-existing-envelope assertion explicitly. Recommended B retains it; it is
not a homemade cryptographic signature validator.

Local qualification identity is different: completed migration preserved existing
wave2a-qualification certificate d862bbf279d306e7062440d299440d54ecdf594e1cf8ac3046cdf5d3a0ed7f49,
under ignored .qualification/signing. Continuity supports adb update and same-signer
product-targeted instrumentation without data reset. Keep no-fallback loading,
production-certificate refusal, private-path checks and native signer verification.
Delete only completed migrate/WAVE2A/transaction machinery and migration-only tests
after the owner accepts retiring that command. No regeneration, conversion or deletion
of the actual local key is part of simplification. Fresh provision remains unresolved.

## 9. Actual distribution mechanisms and supply chain

Current documented channel is **GitHub Releases**, with direct APK installation.
release/README.md and the Actions report record first published 0.1.2/versionCode3,
source f763008a1564359bb16c79ee08d236a1eb0f7b91 and three assets: versioned signed APK,
its .sha256, release-provenance.json. This is historical/documented evidence; no
hosted asset was downloaded or freshly verified in this audit. Syncthing is external
vault transport, not an APK release channel. No Play/AAB, F-Droid, Obtainium or Maven
application distribution authority is established by current source.

Settings enforce repository selection: Google (excluding org.totipo) and Maven
Central for dependencies; plugin repositories additionally include Gradle Plugin
Portal. Strict lockfiles and SHA256 verification metadata cover build/test/runtime
artifacts and metadata; artifact PGP signature verification is disabled. Generated
hashes are observations requiring independent publisher review, not self-authentication.
Keep the existing artifact identities and source-provenance documentation.

Nix pins JDK/SDK/Gradle and cached Maven responses. package-deps.json additionally
pins lint's unversioned Google Play SDK Index snapshot. That is a real reproducibility
input and recurring refresh cost. Do not add it back as a whole-file hash in JVM code
or remove its cache pin to make builds green. A future supported lint offline/index
policy could remove that network input only after showing retained lint coverage.

Nix store/cache signatures authenticate configured binary-cache trust, not an
independent proof that an authorized builder or signing key is uncompromised.
Source/provenance JSON is not a separate signed attestation. Keep identifying source,
toolchain/derivation, expected certificate and final hash; do not claim stronger proof.

GitHub environment reviewers, branch/tag rules, bypass permissions, immutable releases
and account protections are **not proven by YAML**. Provisioning is human-reported
in existing docs; live settings were not inspected. Native protection policy must be
confirmed before replacing source-policy guards with it. Full action SHAs remain
in YAML; a parser in the same mutable commit is not an independent trust anchor.
[GitHub secure use guidance](https://docs.github.com/en/actions/reference/security/secure-use).

Runner gh is not pinned by the flake. Prefer the root nixpkgs gh in a dedicated
publication command environment, outside secrets; do not add gh to the secret step.
This is a build-environment dependency change, not a new runtime dependency.

## 10. CI and qualification duplication

ci.yml is already small: push/PR/manual, read-only contents, pinned checkout/Nix
installer, action-policy, package-inclusive flake check and unchanged-tree check.
It does not separately acquire Java/SDK/Gradle or rebuild via the wrapper. Keep
unchanged-tree validation; it catches accidental lock/verification regeneration.

The release workflow has four jobs on the same supported image/environment. Four
checkout/Nix installations reflect permission and secret separation; treating them
as four duplicate APK builds is incorrect. Keep this graph for the bounded change.
A three-job verify/publish merge could save one transfer/setup, but would remove
verification's separate read-only token boundary; it is not recommended here.

| Repetition | Actual dependency / recommendation |
| --- | --- |
| Release APK verifier | check's Exec task runs it; Nix installCheck repeats it after copying output; local final external set repeats it after strict gate | Keep one authoritative final check per variant in package and one check of actual signed candidate; copy hash can bind output. Remove duplicated Gradle Exec/package invocation, not platform policy |
| Release Python suites | qualify workflow explicitly runs discovery; package installCheck repeats it | Run once in a dedicated source-aware tooling check/job; package currently excludes workflows/public identity and skips two integration/static tests |
| Action self-validation | ci and all four release jobs plus readiness/tests inspect same YAML/table | Remove bespoke parser; keep reviewed SHA pins/native GitHub protections and optional pinned actionlint for syntax. Runtime parser cannot stop checkout which already executed |
| Reviewed source digest inventory | Java and Python parse/hash same twelve files and negative grammar matrices | B removes inventory and both consumers; Git commit review + Nix/Gradle external-byte pins remain. This is a deliberate source-policy relaxation, not equivalent validation |
| Dependency graph | check already verifies four compile/runtime configurations and build classpath; final external dependencies/verifyMavenBoundary repeats | Keep compact graph policy once; retain dependencies report as diagnostic when graph changes, not every milestone gate |
| Local ordinary → strict → Nix | Ordinary provides feedback; strict proves cached dependency availability/fresh tasks; Nix proves isolated declared build/cache | Different claims, but three full final constructions are policy cost. Do not silently remove current gates. B may choose Nix as authoritative hermetic final gate with normal checkpoint; dropping strict offline proof requires owner approval |
| Baseline/iteration/device | ladder already focuses iteration and selects only relevant physical subsystem | Keep; historical retries were failures or invalidated inputs, not automatically redundant tests |
| Reproducibility | flake check/materialization may reuse build; README rebuild is optional deliberate evidence | Do not count a cache reuse as independent reproduction; one release-focused independent unsigned comparison suffices when making a reproducibility claim |

No portable conformance suite in a sibling project was consulted. Android tests use
the released Java artifact and real encrypted fixtures; preserve those cryptographic,
format and merge/store tests. Textual SourceGuard tests assert Android policy choices,
but do not replace platform behavior. In this bounded pass delete only the tooling
digest test; do not bulk-delete biometric, ingress, coordination or lifecycle guards.
Their later replacement needs behavior coverage, not a blanket “inherited policy” label.

No device/emulator test ran here. Real biometric authorization, SAF grants, native
picker/scanner sharing, clipboard behavior, update continuity and Syncthing transport
cannot be newly qualified by this static audit.

## 11. Native-tool versus custom-code boundaries

| Responsibility | Owner / smallest custom gap |
| --- | --- |
| Acquire supported tools | Nix lock, selected packages/licensing; no wrapper/host SDK path in B |
| Resolve external artifacts | Gradle strict locks/checksums + Nix fixed response cache; independent hash review |
| Compile/resources/manifest merge/D8/APK | Gradle/AGP; device standalone packaging remains a documented exception pending safe conversion |
| Describe final APK | SDK aapt2 or apkanalyzer; small policy comparison, not binary-manifest/DEX reimplementation |
| Sign/verify/align | SDK apksigner/zipalign; compare native signer output with reviewed public digest |
| Qualified versus signed payload | retained stdlib comparator; no native tool here proves this exact relationship |
| Exact accepted bytes | SHA256 across artifact handoffs and hosted download; no second signing/build |
| Android vault/security behavior | JVM tests + framework/device tests and human interaction |
| Reference/release permissions | Git/GitHub protected refs/environment; no custom tag-mutation controller in B |
| Distribution | gh draft/create/upload/download/publish; small failure sequencing if pre-publication hosted equality is retained |

SDK apkanalyzer is present at cmdline-tools/22.0/bin/apkanalyzer in the active pinned
SDK. It exposes manifest XML, app/version/SDK/debug flags and defined DEX packages.
Use those facilities, with Python ElementTree for a compact manifest policy if needed;
validate the Nix launcher against actual final APKs before switching. Its APK comparison
does not establish exact bytes. [SDK apkanalyzer](https://developer.android.com/tools/apkanalyzer).

## 12. KEEP / SIMPLIFY / REMOVE classification

Other custom support, in addition to the Python/Java tables:

| File/task | Lines / inputs and outputs / caller / prevented failure / tests and cost | B disposition |
| --- | --- | --- |
| bootstrap-m0.sh | 27; wrapper/env/locks → build or intentional regeneration; README human; missing trusted state/wrong setup; real historical gates; medium | REMOVE; document native Gradle refresh separately |
| tools/verify-m0-toolchain.sh | 43; environment/SDK props → textual checks; bootstrap; wrong host tools; historical M0 evidence, no dedicated suite; low but duplicated version constants | REMOVE under exclusive Nix environment |
| tools/release/signing-smoke.sh | 92; unsigned APK/test keys → native signing/negative cases; release docs; wrong identity/altered payload; itself integration evidence; medium | KEEP/SIMPLIFY only for changed release tooling |
| flake.nix | 137; lock/package/env/apps → shell/check/app outputs; direnv/Nix/CI; unpinned/wrong environment; release-app fixture + package; medium | KEEP Android shell/package, REMOVE two release apps and release-apps check |
| package.nix | 96; filtered source/cache/tools → checked unsigned APK; flake; undeclared downloads/build/packaging failure; actual package gate; medium | KEEP, narrow duplicated checks and host-tool source membership |
| build.gradle.kts verifyBuildDependencies | part of root DSL; locked build classpath → resolve verdict; check; unverified build plugin graph; Gradle strict metadata; low | KEEP; no second bespoke artifact hash parser |
| app/build.gradle.kts verifyMavenBoundary | most of app DSL; four graphs/artifacts → expected external NIO/core/BC verdict; check; project/file/substituted/mixed Totipo artifacts; real Gradle gate; medium | SIMPLIFY exact graph policy, keep substitution/file rejection |
| app/build.gradle.kts verifyReleaseApkBoundary | Exec task → Python verdict; check; final APK deviations; actual gates; low plumbing/duplicate execution | REMOVE task only after package final check remains authoritative |
| settings.gradle.kts | repository policy → resolution; Gradle; local/unapproved repository use; Gradle/native locks; low | KEEP |
| ci.yml / release.yml | 32/203; source/dispatch/artifacts → qualification/release; GitHub; privilege/artifact mistakes; custom static tests and historical actionlint/smoke; medium/high | KEEP permissions/job graph/pins, SIMPLIFY action-policy/test duplication |
| tools/release/reviewed-inputs.sha256 | 12 data lines; twelve source hashes → extra source freeze; Java/Python; changes without deliberate digest update; duplicate grammar matrices; high update coupling | REMOVE with consumers |
| OtpAuthSupplyChainGuardTest.java | 100; build text/digest inventory → JVM checks; testDebugUnitTest; accidental source/dependency changes; self-contained synthetic tests; medium | REMOVE after graph retains no QR/SDK additions; no cryptographic test loss |

KEEP VERSION/CODE, branding provenance and canonical resources, locks/verification
metadata, flake.lock/cache, public signing identity and ignore rules. KEEP all main,
release and debug application source in this bounded pass. Debug probes are a real
diagnostic support surface, but no unused probe was proven safe to delete merely
from LOC. KEEP historical reports unchanged; update only live docs during implementation.

## 13. Option A — conservative

Retain wrapper and its JAR checker, two release Nix commands, source.py/tag allocation,
reviewed-source inventory and all security/device tests. Retain all Python files
except tools/release/test-build-metadata.py. Remove tools/verify-m0-toolchain.sh and
its bootstrap call; Nix supplies the versions, AGP fails missing SDK inputs.

Exact retained code files: every file in sections 6/7/12 except those two removals.
Modify bootstrap-m0.sh, package.nix, app/build.gradle.kts, tools/verify-apk.py,
tools/release/release.py, tools/release/verify-signed-apk.py, tools/release/test_release.py,
.github/workflows/release.yml and live README/AGENTS/release docs to reduce duplicate
invocations. If retaining the digest inventory, consciously review/update affected
hash entries and consumers; never mechanically bless current bytes.

Move release Python tests to one source-aware location; keep their coverage. Verify
each package APK once; check copied release by hash. Verify signed platform/signature
once; publish compares hosted whole-file hashes, avoiding a second signer/DEX/manifest
parse after exact equality has already been established. Preserve payload comparator,
immutable unsigned handoff and existing refused-draft behavior.

Guarantees: same construction/signature/certificate/payload/source policy and platform
behavior evidence. Relax only redundant executions, parser-copy fixture, and explicit
SDK directory-shape probe; native build verifies required functionality. No runtime
dependency changes; optional actionlint/gh pin is a host-tool choice. Release UX,
source/tag allocation, credentials and authority unchanged. Risk low/medium: moving
checks must leave both variants and complete release boundary covered. Estimated
80 deleted file lines plus 100–250 duplicate orchestration/test lines; 180–330 net
lines, with a wrapper acquisition path still maintained.

## 14. Option B — minimal sufficient

Bounded B removes build/release policy scaffolding, keeps all device harnesses and
application behavior tests. Exact removals:

```text
gradlew
gradlew.bat
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper.properties
bootstrap-m0.sh
tools/verify-m0-toolchain.sh
tools/verify-wrapper.py
tools/release/test-build-metadata.py
tools/release/source.py
tools/release/test_source.py
tools/release/test_policy.py
tools/release/reviewed-inputs.sha256
app/src/test/java/org/totipo/android/OtpAuthSupplyChainGuardTest.java
```

Retain every other tools/device file in sections 6/7; retain tools/verify-apk.py,
tools/release/{release.py,payload.py,verify-signed-apk.py,test_release.py,
test_publication.py,signing-smoke.sh}. Retain all other app source/tests, manifests,
resources, Gradle/Nix/lock/cache/public-identity inputs and both workflows. File
removals require caller edits, not orphaned imports or moved hundreds of lines.

Modify flake.nix/package.nix/root/app DSL, ci.yml/release.yml, retained release helpers,
qualification_signing.py/test_qualification_signing.py and current documentation.
Retire completed migrate code while retaining current local identity use. Do not
move/update/delete the ignored key. No new release-controller module or permanent
helper is proposed.

Move only narrow fingerprint/public-identity, exact checked-out source/tag/version
validation into release.py (or simple native Git checks in workflow). Validate the
requested full SHA and tagged VERSION/CODE, expected public signer and clean source.
Drop automated version allocation, canonical tag-message/header shape checks,
remote-only object-database recovery and repeated requirement that live main never
advances during approval. Owner reviews/increments VERSION/CODE and uses normal Git
annotated tag commands; GitHub protects release refs. Retain a focused check of
positive versionCode and increasing code relative to the last released code, using
an explicitly reviewed prior release value rather than a custom all-tags allocator.
Do not let candidate APK metadata become its own expected version.

Delete custom Actions YAML grammar/table and source-digest mirrors; retain SHA-pinned
actions, strict external-byte pins and GitHub source/workflow review. Add no YAML
parser dependency. Optional pinned actionlint checks syntax once; it does not replace
permissions/protection review. Optionally pin gh via existing nixpkgs.

The retained compact runtime graph policy should require exactly the two released
Totipo modules and BC, with no extra runtime module/file/project substitutions. This
preserves the no-camera/QR/cloud dependency choice when deleting the source-text
noQrDependencyDeclared test; the current Maven task does not itself forbid every
unrelated extra module. A later intended runtime dependency requires explicit review.

Narrow verify-apk.py to complete final manifest/SDK/release-flag policy, debug/test
namespace exclusions and essential APK structural sanity. Remove long production
class-presence and retired-API/string lists: compilation, runtime graph and actual
Java behavior tests cover these more meaningfully. Move canonical branding source
hash validation to intentional branding review (retain provenance); do not hash every
source icon on every signing/transfer. Keep final launcher-resource presence/identity.
These are withdrawn checks, not an assertion that AGP guarantees approved branding.

Build/signing guarantees: controlled native construction, strict dependency identity,
native intended sole signer/v2/v3/alignment, accepted unsigned-payload equivalence,
signed SHA256/provenance and exact hosted bytes. Android checks in section 7 stay.
Release keeps four isolated jobs and original credentials/algorithms/key authority.
No new production dependency, SDK, Java library or signingConfig is necessary.

Relaxations: non-Nix wrapper convenience, all-consumed-tag code allocation, exact tag
shape policy, mandatory live-main equality after approval, duplicate reviewed-source
hash gate, action grammar/runtime association gate, exhaustive product-class/branding
source-byte checks. See section 17; GitHub settings are prerequisites, not presumed.
Qualification risk medium: source/CI policy changes and verifier coverage require
focused adversarial tests and final gates. Savings estimated in section 19.

AGP packaging for device harnesses is feasible in principle but **outside bounded B**.
Four unsigned builders and construction portions of android_harness could disappear
after framework instrumentation is packaged through Gradle/AGP using resolved
dependencies, preserving signatures, target packages, isolated roots and real SAF/
Keystore evidence. Detached row/security packages that copy qualified release DEX
are not automatically equivalent to connectedDebugAndroidTest. Do not delete their
runners or replace them with ordinary tests without demonstrating that distinction.

## 15. Recommended target architecture and release process

Choose bounded B, subject to owner decisions below. One pinned Nix Android environment;
Gradle/AGP own build and native test packaging where qualified; SDK owns inspection,
signing and signature validation; small retained Python handles policy/payload/oracle
gaps; GitHub owns protected release authority. No language elimination objective.

Simplest credible process for the actual APK/GitHub channel:

1. Review VERSION/CODE and source; commit through normal human workflow; create/push
   an annotated protected tag with ordinary Git. Dispatch the exact tagged commit.
2. Qualify once in Nix, materialize the same unsigned result, check final APK metadata/
   platform boundaries and record source/toolchain/unsigned hash/expected certificate.
   Perform independent unsigned reproduction only when claiming that release evidence.
3. Approve the protected signing job. Resolve pinned SDK/JDK before secrets; sign the
   accepted unsigned file to a separate file with existing production policy. Erase
   ephemeral keystore; never rebuild during or after signing.
4. In no-secret/read-only verification, check native signature/certificate/schemes,
   alignment, metadata and payload correspondence once. Compute final signed SHA256.
5. In contents-write/no-key publication, create a new draft for the existing tag,
   upload exactly APK/hash/provenance, download and compare complete files, then publish.
   Exact equality transfers already-established signer/platform/payload proof; no
   second APK generation or repeated parser matrix is needed.

Keep refusal to overwrite/adopt existing releases/drafts and honest error handling.
gh supports existing-tag enforcement and draft creation; custom code need only express
Totipo's additional sequencing policy. Immutable releases are an optional owner setting,
not established here. [gh release create](https://cli.github.com/manual/gh_release_create).
A single direct create/publish command is shorter but relinquishes pre-publication
hosted-byte verification; neither option recommends that relaxation.

## 16. Prioritized exact-file changes

This is one proposed implementation pass, not scheduled work or permission to start.

| Priority | Exact paths | Change / decision dependency |
| --- | --- | --- |
| 1 | flake.nix, build.gradle.kts, gradlew*, gradle/wrapper/*, tools/verify-wrapper.py, bootstrap-m0.sh, tools/verify-m0-toolchain.sh, README.md, AGENTS.md | exclusive Nix Gradle; delete redundant acquisition/probes; owner accepts supported CLI scope |
| 2 | tools/release/source.py, test_source.py, test_policy.py, reviewed-inputs.sha256; app/src/test/java/org/totipo/android/OtpAuthSupplyChainGuardTest.java; flake.nix; ci.yml/release.yml; release.py/test_release.py | retire bespoke source/tag/Actions controller and duplicate hashes; native protection/source review prerequisites; retain necessary identity/version tests |
| 3 | tools/verify-apk.py, app/build.gradle.kts, package.nix, verify-signed-apk.py, release.py/test_release.py | complete final platform policy and remove internal class-list duplication; one invocation per acceptance boundary |
| 4 | package.nix, flake.nix, .github/workflows/release.yml, tools/release/test_publication.py, release.py | run tooling tests once with full source; simplify repeated hosted signature parse after byte equality; optional Nix gh |
| 5 | tools/device/qualification_signing.py, test_qualification_signing.py; README.md/release/signing/README.md | retire completed migrate function/CLI/test scaffolding; retain operational identity and every supported caller |
| 6 | AGENTS.md, README.md, release/README.md, tools/device/M3D_SYNCTHING_CHECKPOINTS.md | qualification policy consistency; strict-local gate reduction only by explicit owner choice; historical reports unchanged |

Clear deletion candidates: standalone copied metadata-parser harness, M0 environment
shape probe, completed migration transaction, duplicate verifier/test invocations.
Wrapper deletion/source-controller retirement are policy choices with qualified native
replacements. Broad device packaging migration is deferred, not an automatic milestone.
Recovery scaffolding recover-release.yml/recover.py/test_recovery.py is **already
absent**; do not claim new savings or recreate it based on historical reports.

## 17. Guarantees lost and accepted risks

| Change | Actual guarantee withdrawn / mitigation |
| --- | --- |
| Wrapper removal | bootstrap/non-Nix/Windows convenience and separately pinned wrapper JAR; Nix becomes required and pins Gradle itself |
| Source digest inventory removal | accidental edits no longer fail a second manually maintained source-hash gate; reviewed Git SHA, protected workflow/source refs and actual external artifact hashes remain |
| Actions parser removal | no local rejection of alternate YAML spelling/unknown action/version-comment association; retain reviewed full SHAs and native review/protection policy; actionlint only syntax |
| Tag controller removal | no automatic max-consumed-code allocation or stale/local/remote tag-shape recovery; owner controls monotonically increasing codes and native no-force protected tags; focused final metadata/previous-code check |
| Drop live main polling | a reviewed tagged release may proceed while main advances; approval binds immutable exact SHA/tag, not latest main. This is often useful but explicitly changes current policy |
| Narrow DEX/source branding checks | no exhaustive assertion that every named implementation class or original icon source byte is present/unchanged; preserve namespace exclusions, runtime graph, branding provenance and behavior tests |
| Migration retirement | no built-in adoption of old historical key/password layout; canonical existing identity retained; missing-state recovery remains explicit owner operation |
| Optional strict-gate retirement | no separate proof that populated development cache builds fully offline with every task rerun; Nix proves declared/cache-controlled sandbox build, a different claim. Not included by default in savings/qualification changes |

Keep native signature verification, intended certificate comparison, payload comparison,
signed hash equality and platform behavior coverage. Losing any of those would require
a different security justification and is not recommended. A compromised production
signing key, authorized workflow editor or GitHub administrator remains a trust limit;
duplicate checks in their controlled source cannot provide independent protection.

## 18. Required implementation qualification

Future changes classify as BUILD_DEPENDENCY / PACKAGING_RELEASE /
TEST_OR_QUALIFICATION_TOOLING. No product behavior change is intended. Follow current
AGENTS until the owner explicitly approves a revised ladder; this report does not
waive gates. Required evidence for the bounded pass:

- Focused Gradle graph/metadata and host tests: wrong app/version, malformed inputs,
  wrong/multiple signer, required scheme absence, signature tamper, altered payload,
  changed unsigned input, debug/test package leakage, unexpected permission/component,
  backup/release flags, changed hosted bytes and draft failure. Native command failure
  must fail acceptance, not be interpreted as absence.
- Qualify Nix Gradle development and intentional refresh paths; strict existing
  locks/metadata/cache review. No arbitrary cache/verification repair.
- Final normal and strict offline Gradle gates under current policy; wrapper verifier
  goes away only with wrapper; debug/release final artifact policy and native signing
  smoke with disposable keys only. Retain meaningful negative cases, not huge syntax
  fixture matrices for deleted policies.
- Relevant local signing-owner tests and one disposable retained-state/grant update
  smoke if identity loader/sign behavior changes. No automatic full biometric/SAF/M3D
  replay for source/tag-only changes. If device packaging changes later, qualify every
  migrated behavior path, including physical strong biometrics and provider interaction.
- Freeze all actual included inputs/tools, then exactly one human nix flake check.
  Separate full-checkout release tests from Android filtered package so real workflow/
  public-input checks are not quietly skipped. Device inability is an explicit pending
  requirement, never a simulated PASS.

If package filtering is narrowed, retain app source/tests, Gradle metadata/build inputs,
final APK checker and branding data it consumes. Host release tools can be a separate
flake check; changed tools must still invalidate their actual check. Keep Git-aware
source membership and no staged contents. Do not weaken CI unchanged-input enforcement.

No live production signing, release dispatch or settings mutation is needed to prove
tooling changes locally. Production workflow execution remains a separate owner action.

## 19. Estimated maintenance savings

Measured deletion surface in bounded B: five Python files total **1,065 lines**, wrapper
scripts **360**, bootstrap/toolchain scripts **70**, source-inventory JVM test **100**,
plus wrapper properties/JAR and digest data. Gross code removal: **1,595 lines**, before
replacement identity checks, retained-test edits and orchestration reductions.

Retained release.py should gain only roughly 50–100 necessary source/public-identity
lines while losing source-command dispatch/repeated checks. Shrinking workflow static
tests, APK class lists and completed migration/tests offers roughly 150–300 additional
lines. Expect **about 1,400–1,800 net physical code lines** removed, approximately five
Python files (25 → 20), two acquisition paths → one, two release-controller apps → zero.
These are design estimates, not promised measured results. Python remains appropriate.

Device AGP migration could remove four builders (189 lines) and some construction
helper/self-test code, but Gradle modules/manifests and equivalent isolation have costs;
no net savings counted until designed. Do not count already-deleted recovery code or
delete the 2,511-line Android behavior evidence to reach a LOC target.

Primary maintenance gain is fewer independently edited source/action/version constants
and less tag-state machinery. Build-time savings: one duplicate release-suite execution
and repeated APK parsing per release/package; no measured duration promised. The four
permission-separated jobs remain. Full local ladder counts change only by owner policy.

## 20. Remaining owner decisions and direct answers

Decisions before implementation:

1. Adopt Nix-only supported Gradle CLI, or retain wrapper for explicitly supported
   non-Nix/IDE contributors? No technical Android need for the extra path was found.
2. Retire automated all-consumed-tag versionCode allocation and exact tag-message
   grammar? Select reviewed manual metadata + focused prior-release monotonicity check.
3. Permit a protected reviewed tag to release when main advances? Current code forbids it.
4. Confirm actual protected main/tag/workflow and android-release reviewer/bypass rules
   before relying on native GitHub policy. Immutable releases are optional, unverified.
5. Retire completed local migrate command while preserving canonical key continuity?
   Fresh disposable-environment provisioning remains a separate explicit decision.
6. Keep existing normal/strict/Nix final ladder or use Nix as sole hermetic final build?
   Recommended bounded pass keeps current ladder until that evidence tradeoff is approved.

Direct answers:

- **Only Nix Gradle?** Yes for supported CLI/CI operations; same version already builds
  the package. Validate migration callers and AAPT2/SDK integration before wrapper deletion.
- **Python that disappears entirely?** verify-wrapper.py, release/test-build-metadata.py,
  release/source.py, release/test_source.py, release/test_policy.py in B. source.py's
  small necessary identity functions remain in release.py; its controller/parser/hash
  machinery is deleted. Device builders are conditional later candidates, not deletions here.
- **Necessary signing checks?** Native valid signature, intended sole certificate,
  current v2/v3 scheme policy, alignment, intended app/version, payload binding, exact
  signed-artifact/distribution identity and protected key handling. Hash alone is insufficient.
- **Final APK/platform security checks?** Merged permissions, complete components/
  exports/intents/process and backup/release flags, release diagnostic exclusions; real
  SAF/Keystore/lifecycle/clipboard tests. Source wiring/class-name inventories are weaker.
- **Simplest credible release?** Qualify once, sign that artifact once in protected
  isolation, verify native signature/payload, hash, upload/download exact bytes, publish
  draft; normal Git metadata/tag operations replace a custom release controller.
- **Less complexity without weakening app security?** Yes: preserve application and
  platform tests, actual identity boundaries and controlled dependencies. Some additional
  source/release process policies are relaxed explicitly; this is not identical evidence
  under a smaller name, and requires owner acceptance.

## 21. Git and source-invariance evidence

This audit is DOCS_ONLY outside effective qualification inputs. package.nix selects
app/src, gradle, tools and exact build/lock/version/license/branding files; excludes
.git/.gradle/build/.direnv components and symlinks. flake consumes package/cache/evaluator
inputs and tools/release for app/check wrappers. Neither reads review/. Thus adding
this report to the Git-aware source does not change any Android/package/release-app
check input. No human Nix gate is necessary or requested.

Freeze statements: production source unchanged; JVM tests unchanged; build/package
configuration unchanged; qualification harness/tooling unchanged. All stable relative
to starting HEAD. No qualification pass was invalidated, and no fresh build/device
PASS is claimed. Historical PASS remains historical evidence.

Validation: initial clean main; one intended added report; no unrelated changes;
git diff --check PASS; git diff --cached --quiet PASS; cached name list empty.
New report has intent-to-add only, retained for review; no content staged.

Expected final evidence, verified after writing:

```text
branch: main
HEAD: bd2671fa37c2e87042fd8699b0cc8f4a63d9daa4
git status --short:
 A review/ANDROID_TOOLING_SIMPLIFICATION_AUDIT.md
git diff --name-only:
review/ANDROID_TOOLING_SIMPLIFICATION_AUDIT.md
git diff --cached --name-only: empty
git diff --cached --quiet: exit 0
git diff --check: exit 0
```

Execution counts: baseline full Gradle 0; focused Gradle 0; final normal 0;
strict-clean 0; artifact-verifier set 0; embedded Gradle verifier 0; physical/device
phases 0; human Nix 0; agent Nix 0; production/test signing 0; release operations 0.
Only read-only Git/filesystem inspection, active java version observation, public
official-tool documentation browsing and report validation were performed. No new
permanent tooling, dependency updates, commit, tag, push, dispatch or settings change.
Audit ends here; no implementation milestone starts automatically.
