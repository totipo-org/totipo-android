# Java 0.2.0 / v1/r19 Android reconciliation

Status: complete; agent validation, physical qualification, branding confirmation and human Nix checks passed.
Work is unstaged/uncommitted. No remote CI is dispatched.

## 1. Starting state / exact HEAD

`main`, HEAD `be2690344305c4042ca2c6813dabd7ec348fd62e`; initial `git status --short`
was empty. M3B outbound publication is committed. Reviewed README, dependency provenance,
root/app Gradle builds, locks, verification metadata, package cache/Nix files, main/debug
manifests, APK verifier and production/test/device-harness sources before editing.
Actual Gradle 9.8.0 / AGP 9.4.0 / SDK 37 / minSdk 26 / Build Tools 36.0.0 / Java 17.

## 2. Baseline validation

Before edits, normal `./gradlew check :app:assembleDebug :app:assembleRelease` passed
(1m40s). Required strict offline/no-daemon/no-configuration-cache/no-build-cache/rerun-tasks
clean build passed (1m37s; 92 tasks executed). **215 tests; 0 failures/errors/skips**.
Wrapper verification, debug `--debug-probe`, release `--unsigned --no-debug-probe` APK
verification and `git diff --check` passed.
Final baseline debug SHA-256: `3c8fd6b08e7b87917a1e1f767f7f589c061f69e75097a7ddcace78067f358f92`.
Final baseline unsigned release SHA-256: `b6c615e5ab53e0b8f5d99864d826c4cbc7cbbaaeee3ede331de8bfc2249fa190`.
Earlier pre-build APK bytes were replaced by the baseline builds; these are the built hashes.
Release permissions: none. Components: TotipoApplication, exported launcher MainActivity,
exported OtpAuthEnrollmentActivity with exact VIEW otpauth/totp and SEND text/plain filters.
Debug adds only existing fixture/storage/NIO/auth-performance Activities and backup exclusions.
No services, receivers or providers. Application icon was platform sym_def_app_icon.

## 3. Actual pre-change Java dependency graph / API inventory

One direct production edge, in both debug/release:

```text
org.totipo:totipo-storage-nio:0.1.5
  org.totipo:totipo-core:0.1.5
    org.bouncycastle:bcprov-jdk18on:1.86 (runtime only)
```

Compile classpaths contain NIO/core, not BC. Test edges remain JUnit 4.13.2 / Hamcrest 1.3.
Package cache has 190 entries; core/NIO each have JAR/module/POM hashes at 0.1.5;
BC 1.86 is pinned. Publisher metadata reports NIO 0.1.5. It is human-generated.
Whole tracked-tree pre-change inventory (requested search terms, case-insensitive):

| Classification | Files | Matches | Disposition |
| --- | ---: | ---: | --- |
| CURRENT PRODUCTION (including debug) | 12 | 100 | Simplify store/session API; update local probe |
| CURRENT TEST (including standalone harnesses) | 24 | 178 | Adapt SPI/identity fixtures, remove password-only tests |
| CURRENT BUILD (including verifiers/wrapper) | 12 | 46 | Totipo-only repin; retain unrelated inputs |
| CURRENT DOC | 2 | 66 | Update README/dependency document |
| HISTORICAL REPORT | 24 | 410 | Preserve unchanged |

Main production dependencies were CoordinatedPrivateStore.openPrivate/stage registry,
ForegroundVaultCoordinator fingerprint/changePassword, and LocalReplicaOwner exclusivity.
Tests used staged proxies, password-change adversaries, fixture wrapper terminology and
hard-coded supply-chain digests. Runtime owner remains canonical, not provider transport.

## 4. Java 0.2.0 provenance

Canonical Maven Central downloaded independently; both primary JAR hashes equal supplied
expectations. Released annotated tag `d24e3d0ae71ea7fe318261519a9b5d08657a0e03` resolves to
`d6310c177ae930df188fd4f5798622c935698b2e`. Released SPEC_PIN confirms v1/r19 spec
`cdb4e91be1c6d3704874b2b92457ffe7be5e9084`. All 82 core / 12 NIO published Java sources
match the committed release byte for byte. Inspected exact sources and downloaded Javadocs
for TotipoStore, VaultCreate, CreateVaultResult, VaultId, Totipo, VaultSession,
NioStoreComposition, StorageDurability and NioTotipoStore. All JAR classes are major 61.
Module variants and POMs retain sole runtime BC 1.86. Exact ten artifact hashes are in
TOTIPO_JAVA_DEPENDENCY.md. No artifact/source fallback is used.

## 5. Direct repin

The single direct NIO edge advances to 0.2.0; core stays its exact transitive dependency.
Gradle's four-configuration boundary rejects mixed versions or non-Maven artifacts.

## 6. Locks / verification metadata

Used the deliberate `bash bootstrap-m0.sh --refresh-dependencies` workflow. Only core/NIO
lock versions change. Reviewed new JAR/module hashes against canonical bytes and module
metadata; independently reviewed POM bytes are recorded too. Retired Totipo trust entries
are removed. Strict verification remains enabled. Buildscript lock, wrapper, AGP, SDK,
JUnit, BC and unrelated verification components remain unchanged. The supply-chain source
guard is deliberately rebaselined for changed trusted inputs.
Initial iteration failures (obsolete fixtures/guards, PNG text scanning, manifest edited
while resource collection had already run, API-34 Stream.toList) were corrected without
weakening dependency verification, disabling lint or bypassing baseline validation.

## 7. Nix package-deps

Human ran `nix run path:.#update-package-deps`; first run failed because package.nix's
source filter omitted branding-provenance.json. Added that file to the filter, and the
human reported the rerun done. Reviewed regenerated cache: exactly core/NIO 0.2.0
JAR/module/POM entries, all six matching independently downloaded canonical Central bytes.
No 0.1.x Totipo artifact entries remain; BC 1.86 and all unrelated artifact hashes unchanged.
NIO publisher metadata now reports 0.2.0 at lastUpdated 20261009184737. The only unrelated
change is Gradle tooling-api publisher lastUpdated 20261007170937 → 20261008100315;
its release remains 9.9.0-milestone-2 and all other metadata fields are unchanged.
package-deps.json SHA-256: `74eb72a9ffb5f9f364f02f9f64014f260f4baf610c1cefdb9b09ed6846eb42b1`.
The agent ran no Nix command and fabricated no Nix hash. package.nix selects 0.2.0
coordinates and includes the branding provenance required by APK verification;
flake.nix/flake.lock have no changes.

## 8. Final dependency graph

Both actual debugRuntimeClasspath and releaseRuntimeClasspath reports show only
NIO 0.2.0 → core 0.2.0 → BC 1.86, plus corresponding strict lock constraints.
verifyMavenBoundary passed all compile/runtime configurations. Module/POM and JAR
bytecode audits confirm the Java 17 boundary and unchanged sole BC dependency.
No new production dependency is declared.

## 9. openPrivate removal

Current production/test/tool invocations removed. Ordinary shared open is not selected.
The APK verifier forbids retired API descriptors and openPrivate-specific packaged strings.

## 10. NioStoreComposition integration

Exact consumer-smoke public factory: `NioStoreComposition.coordinatedDelegate(root,
new NioDurability())`, returning TotipoStore. No reflection/internal class construction.
Java owns staging, move publication and directory/file durability. Android enforces whole-root
serialization through one persistent domain, not a per-call delegate.

## 11. CoordinatedPrivateStore SPI simplification

readVault, createVault, scanObjects, readObject, publishObject each enter one fair gate.
createVault is one complete delegate call. Bridge batches exclude ordinary calls. Logical
facade closure does not physically close the domain; session closure precedes final delegate
close. Failed final close retains ownership for retry. No calls after successful closure.
Bridge uncertain/failed publication still quarantines the domain; no direct filesystem publication.

## 12. Removed PreparedVault / replacement assumptions

Stage/resource registry, prepare/read-back/install/replace/close wrappers are removed.
No Android emulation of Java's retired SPI is introduced. Creation fault fixtures now inject
current VaultCreate outcomes. Historical test-only importer compiles with current SPI;
historical milestone reports are untouched.

## 13. Removed password-change coordination / tests

Coordinator changePassword and CHANGING_PASSWORD state removed; three password-only tests
removed. Remaining real token Save versus bridge, observation/refresh, import, close/lock
and outbound tests retain coordination evidence. No product password-change UI is added.

## 14. Local create OBJECT_DATA_OBSERVED

Fixed presentation: “Totipo found existing token data but no usable vault. A new vault was
not created.” No paths/filenames or bypass. Real Java veto test proves absent VAULT and
preserved candidate bytes. Clean create, reopen identity and empty-password tests retained.

## 15. Local VaultId

The open session's vaultId is authoritative; no root-derived identity or root key export.
Identity stays out of the UI View projection and is not rendered to users. Provider comparison occurs outside the Android store gate.

## 16. Provider root VAULT collection

Fresh scan collects exact root display-name vault rows through ProviderIoLane. Document
rows/URIs only, no derived filesystem path. Non-directory documents use existing bounded
stream machinery with maximum 87 plus one overflow byte (at most 88). Malformed/directory
rows retain unusable detached evidence; no provider resources escape the lane.

## 17. Provider VaultId classification

Worker uses real Totipo.vaultId on exact 87-byte PRESENT candidates, comparing value equality
with session.vaultId. Structural SHA-256 recognition is not authentication/freshness/origin.
No password/KDF/session call on provider lane.

## 18. Duplicate / ambiguous policy

Conservatively block all multiple root vault rows, including byte-identical duplicates.
No row selection, suffix/timestamp/ID/size metadata identity. Existing 256-root-row cap bounds
VAULT reads to 256 × 88 bytes. Exact cap plus EOF can be complete; exhaustion is incomplete
and blocks identity. Root-loading/unavailable/malformed coverage blocks too.

## 19. Shared Import / Publish identity gate

requireMatchingVault delegates to ProviderVaultIdentity in both sync and outboundPlan.
Preflight precedes inbound publication and outbound provider mutation. Postflight identity
is rechecked before success. All callers, including internal controller.sync, obey the gate.

## 20. Folder READY

READY remains configured/persisted-permission/provider-accessibility state. Identity failure
is per-operation fixed status; folder remains accessible while locked or mismatched.

## 21. Sync UI / status

Missing: “Sync folder has no Totipo vault.” Unverifiable/ambiguous: “Sync folder vault cannot
be verified.” Different: “Sync folder belongs to a different Totipo vault.” Fixed enum-owned
text, no exception messages/URI/document ID/VaultId/ciphertext. Sync explanation describes
immutable objects, same existing immutable VAULT, read-only identity inspection and no adoption.
Launcher/status text only; existing shell/enrollment/list/reveal/copy/lock controls retained.

## 22. Inbound M3A invariance

After matching gate: same session, Java validateObject, exact ciphertext, incomplete object
coverage semantics, equal duplicate collapse, contradiction and ExistingDifferent handling,
one exclusive bridge, refresh after release, no credentials/KDF/provider write. Incomplete
root coverage now blocks identity; incomplete object-directory coverage retains useful evidence.

## 23. Outbound M3B invariance

512-object / 524288-byte local bounds unchanged. Explicit action, fresh complete preflight,
exact local authentication, create-only names, no overwrite, immediate read-back, fresh
postflight, authenticated exact final evidence, manual retry and cancellation remain.
No provider VAULT writes, queue or automatic retry.

## 24. Lock order / concurrency

Provider reads happen without local gate. VaultId/core calls occur outside bridge scope.
Existing session→Android gate order retained; no Android gate→session inversion.
Deterministic tests cover every SPI operation queued behind bridge and real Save/observation/
close/import/outbound overlap. Generation changes, lock, binding replacement and grant loss
still discard detached late results before publication.

## 25. Local-replica terminology

Current explanatory prose uses local canonical store. LocalReplicaOwner class name is retained
to avoid unrelated rename churn across controllers, tests and standalone classloader harnesses;
rename deferred. Root role and coordinated-delegate contract are explicit in its Javadoc.

## 26. Canonical icon source provenance

Source repository: totipo-spec. Protocol and branding source both happen to be exact commit
`cdb4e91be1c6d3704874b2b92457ffe7be5e9084`; inspected committed design/icons tree.
Canonical master `design/icons/totipo-app-icon.svg`; mark sources totipo-mark.svg and
totipo-mark-white.svg. Android-ready exports supplied at that commit; no reinterpretation.
Approved gradient: #46FB70, #08D267, #028B55, #026344. No semantic UI palette change.

## 27. Exact copied assets / hashes

Only canonical Android res assets are vendored. No master/report/whole-spec copy.
SHA-256 values below are copied source bytes; provenance also in branding-provenance.json.

| Source → Android resource | SHA-256 |
| --- | --- |
| `design/icons/platforms/android/res/drawable/ic_launcher_background.png` → `app/src/main/res/drawable/ic_launcher_background.png` | `6ae7cd043b5b368fc1c8bfa9c001d25d8f852232d0f2ba474182fb00a296fc74` |
| `design/icons/platforms/android/res/drawable/ic_launcher_foreground.png` → `app/src/main/res/drawable/ic_launcher_foreground.png` | `79b9787c52564958adf55fee5444e6fd9cb91da3c19d53f92f0532d37efc88b5` |
| `design/icons/platforms/android/res/mipmap-anydpi-v26/ic_launcher.xml` → `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | `1c832bf194d8eecd439a92bcced0df144c9f566b934b3d7865e983d8ee8dbdeb` |
| `design/icons/platforms/android/res/mipmap-anydpi-v26/ic_launcher_round.xml` → `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` | `1c832bf194d8eecd439a92bcced0df144c9f566b934b3d7865e983d8ee8dbdeb` |
| `design/icons/platforms/android/res/mipmap-hdpi/ic_launcher.png` → `app/src/main/res/mipmap-hdpi/ic_launcher.png` | `f60c874f7ffb85791603a90a5d3d8ea3449fb278e731638a3de72b6cb6862b97` |
| `design/icons/platforms/android/res/mipmap-hdpi/ic_launcher_round.png` → `app/src/main/res/mipmap-hdpi/ic_launcher_round.png` | `f60c874f7ffb85791603a90a5d3d8ea3449fb278e731638a3de72b6cb6862b97` |
| `design/icons/platforms/android/res/mipmap-mdpi/ic_launcher.png` → `app/src/main/res/mipmap-mdpi/ic_launcher.png` | `ec5d895a062847f98d38c77dcfe1100385b74bb5e804cc5c2a25a6c52fa67919` |
| `design/icons/platforms/android/res/mipmap-mdpi/ic_launcher_round.png` → `app/src/main/res/mipmap-mdpi/ic_launcher_round.png` | `ec5d895a062847f98d38c77dcfe1100385b74bb5e804cc5c2a25a6c52fa67919` |
| `design/icons/platforms/android/res/mipmap-xhdpi/ic_launcher.png` → `app/src/main/res/mipmap-xhdpi/ic_launcher.png` | `38af8457b71de6f49215516b29077111f18cd1608cb930a680f74b9f811bd950` |
| `design/icons/platforms/android/res/mipmap-xhdpi/ic_launcher_round.png` → `app/src/main/res/mipmap-xhdpi/ic_launcher_round.png` | `38af8457b71de6f49215516b29077111f18cd1608cb930a680f74b9f811bd950` |
| `design/icons/platforms/android/res/mipmap-xxhdpi/ic_launcher.png` → `app/src/main/res/mipmap-xxhdpi/ic_launcher.png` | `05049a57194e60820186b1ac4912378c64c877a1620c06fb1ef76a093cd3433d` |
| `design/icons/platforms/android/res/mipmap-xxhdpi/ic_launcher_round.png` → `app/src/main/res/mipmap-xxhdpi/ic_launcher_round.png` | `05049a57194e60820186b1ac4912378c64c877a1620c06fb1ef76a093cd3433d` |
| `design/icons/platforms/android/res/mipmap-xxxhdpi/ic_launcher.png` → `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png` | `6361d5cbf5350ba29fc2f5e517b95adb23893ebd5e44aaf423fd9eb35e3331d3` |
| `design/icons/platforms/android/res/mipmap-xxxhdpi/ic_launcher_round.png` → `app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png` | `6361d5cbf5350ba29fc2f5e517b95adb23893ebd5e44aaf423fd9eb35e3331d3` |

## 28. Icon resources / manifest

Canonical adaptive foreground/background and API-26 launcher/round XML, plus density-specific
launcher/round PNGs. main application icon @mipmap/ic_launcher and roundIcon
@mipmap/ic_launcher_round; same resources debug/release. No supplied monochrome variant,
none invented. applicationId/signing/permissions/backup/components unchanged by branding.

## 29. APK icon verification

Verifier resolves exact packaged resource IDs with pinned AAPT2 and compares application
icon/roundIcon attributes; verifies required resource files in each APK and source hashes.
AAPT may optimize PNG encodings, so no pixel recognition or source-to-packaged PNG byte
identity assumption. Old platform icon cannot satisfy repository resource-ID checks.

## 30. Supply-chain boundary

No Internet/broad storage permissions, cloud SDK, Syncthing, WorkManager, AndroidX/Material,
icon tooling or new runtime dependency. Retired Java classes absent; VaultId/NioStoreComposition
required under existing unminified APK conventions. Production signing policy unchanged.

## 31. Test totals / final agent validation

Before: 215 tests. After: **217 tests, 0 failures/errors/skips**. Three obsolete password-only
tests removed; added four r19 creation/identity tests and one root-resource-bound test.
Final normal build after cache review/rebaseline passed (59s; 90 tasks, 8 executed).
Required strict offline/no-daemon/no-configuration-cache/no-build-cache/rerun-tasks clean
repeat passed (1m19s; all 92 tasks executed), again 217 tests with no failures/errors/skips.
Wrapper verification, debug `--debug-probe`, unsigned release `--unsigned --no-debug-probe`,
and `git diff --check` passed. Python harness/verifier syntax checks passed.
Final debug SHA-256: `dccf46657a467bf423e7481c160decbfe7b70120bd3b68b609c9bf01541f8b6b`.
Final unsigned release SHA-256: `8bc93c15db7b83806294f9138ffbe4ec7204b86d5ded29693d2444cb6fd00e56`.
Existing Gradle deprecation warnings remain.

## 32. Local canonical-store device qualification

Connected Pixel 6a (`37311JEGR05916`, bluejay) requalified the public coordinated delegate
with NioDurability in isolated app-private cache roots. Standalone LocalStoreQualification
passed absent observation, clean create/close/reopen, authoritative VaultId stability,
real TOKEN save, complete scan/exact 1024-byte read, exact retry, ExistingDifferent byte
preservation, close/reopen state and no abandoned stages/partials. Real orphan-object
creation veto passed with absent VAULT and candidate ciphertext unchanged. No password change.
Direct public API construction (no reflective NIO internals) is compiled against released
0.2.0 jars and loaded with the signed non-debug target. The harness alone owns each root
and uses delegates sequentially. Accepted durability/reopen is not power-loss evidence.

## 33. Matching / mismatching provider VAULT device qualification

Passed on the connected Pixel with fresh disposable r19 fixtures in the designated
Documents/Totipo-M3A-Test tree and exact matching isolated local copies. Matching case:
37 checks passed, including Import, Publish, same live session, unchanged provider VAULT
hash and idempotent second Publish. Different real structurally valid VAULT, invalid
87-byte VAULT and absent VAULT each passed 24 checks: both actions blocked with the
expected fixed status, zero provider mutation, zero inbound local object publication,
unchanged local canonical VAULT/objects and same live session.
Test-only exact VAULT fixture construction is not production provider publication.
No user's real vault was opened or mutated. The former deliberately invalid M3A fixture
was not used for matching qualification; the current inbound builder now also copies
its exact authored VAULT representation.
An initial runner attempt moved the disposable root and thereby revoked Android's grant;
it stopped before qualification. The runner was corrected to preserve the root while
replacing only fixture contents, the original tree was restored, and the human selected
it once again. The successful complete run restored all six original fixture files;
an independent adb pull confirmed exact pre/post restoration hashes. Isolated roots and
the standalone instrumentation package were removed; signed release retained and relaunched.

## 34. Icon mechanical / visual qualification

Signed non-debug APK installed with the existing qualification signer; certificate checked
against the installed app before installation, app data retained. Pulled installed APK passed
canonical resource-ID/adaptive-layer/source-provenance and supply-chain verification;
package remains org.totipo.android and debuggable is absent. Installed signed SHA-256:
`48338f4b59c921d7f068166a3807616ae8e82e57ce415466d304f3f9ea1e5b9b`.
Human confirmed: “Canonical Totipo icon is visible.” This is separate from protocol qualification.

## 35. Human Nix

Human cache regeneration and review passed (§7). After all agent checks passed, the
human ran `nix flake check path:.` and `nix build path:.` and reported:
“flake check/build passed.” Both are recorded as human-reported passes; full terminal
logs were not supplied. The agent executed no Nix commands.

## 36. Remote CI

Not dispatched; no remote result claimed.

## 37. Deferred migration / provider VAULT publication

Migration/cross-vault copy/provider VAULT bootstrap/publication remain unimplemented.
VAULT is immutable. The historical M3B mutable-M3C recommendation is superseded by this
current milestone; the historical report is preserved, not rewritten.

## 38. Final Git state

Final audit: branch main and HEAD remain `be2690344305c4042ca2c6813dabd7ec348fd62e`.
All changes are unstaged; index diff is empty. Historical review reports, flake inputs,
root build, wrapper and buildscript locks are unchanged. `git diff --check` passes.
New report, identity classifier, standalone local qualification and 14 icon resources/
provenance are untracked. No stage/commit/tag/release/push/CI/Nix by agent.
