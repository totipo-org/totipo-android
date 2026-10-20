# A-MV6 — Android integrated multi-vault qualification

**COMPLETE — qualification acceptance satisfied for the documented environment.**
Ready for a subsequent human release decision; no release authorization.
Real physical Desktop→Android Join and bidirectional Syncthing convergence pass on
the final corrected APK. D01 configuration feedback is fixed and physically verified.
Host, artifact, biometric, isolation, Forget/recovery and focused UI checks pass.

## 1. Starting source and qualification scope

Started 2026-10-20 on clean `main`, HEAD
`1e67659d688eb586553d8cd58c208751a83813f1` (committed A-MV5), empty
`git status --short`. Changes remain unstaged; new report and observer have only
intent-to-add membership. No existing work was discarded.

Classification: **TEST_OR_QUALIFICATION_TOOLING, LIFECYCLE_SECURITY, UI_ONLY,
DOCS_ONLY**, updated to include **PRODUCT_CODE** for D01. No architecture,
protocol, dependency, cryptography, registry design or Sync algorithm changes.
Read AGENTS, README, MULTI_VAULT, dependency provenance, A-MV0–5 reports,
security/release policies, package/flake/CI and relevant Sync/Join/device harnesses.
Pinned Java API guidance was read at exact reviewed commit
`f0a028676c1801a10b2d8d2650bf9c24357d1fa1`, SHA-256
`338837e3563446f8ec11f7bb47c0e4f3ff5502b7608e397f9b8c4d75372715fd`.
Its operation classes, observation ordering, snapshot semantics and close contract
remain unchanged. No other repository was modified; no local desktop checkout
was available. Desktop revision below is human-supplied, not a source audit.

## 2. Prior A-MV1–5 evidence inventory

Before implementation, all 138 A-MV5 qualified host hashes and all 185 final
frozen hashes independently match this checkout. Comparison receipt:
ignored `.qualification/amv6/prior-input-comparison.json`. Historical results
below retain their source/artifact scope and are not counted as new runs.

| Journey/boundary | Deterministic coverage already present | Physical / actual SAF / biometric evidence | Outstanding A-MV6 journey |
| --- | --- | --- | --- |
| Independent roots, Create and interrupted allocation | A-MV1 registry/owner/enrollment tests; A-MV5 management | A-MV1 real private FS/restart/recovery; A-MV5 Create/Lock/cold startup | Integrated A/B/C usage |
| Complete retirement and late callbacks | A-MV2 controller/provider latches, failed-close and handoff tests | A-MV5 actual Change Vault with held provider, rotation/forms/clipboard | Actual-provider interruption during integrated usage; Binder hang remains controlled evidence |
| Local-only and per-entry binding/retry | A-MV3 zero-provider authoring, scoped restart/hint/grant tests | A-MV3 actual external-storage SAF, two identities, revoke/regrant, mismatch and conservative shared grants | Two separately synchronized identities with real peers |
| Authenticated Join and duplicates | Controller/enrollment tests with real Java; exact identity/create-only/refusal | A-MV5 Join UI setup/cancel; actual SAF Initialize and Sync | Successful desktop-created physical Join; real propagated immutable evidence |
| Bidirectional edits/deletion/conflicts | Controller/Java tests and controlled file transport | A-MV5 lifecycle 53 checks; historical M3D real Syncthing on older source | Current multi-vault artifacts through actual Syncthing, logical convergence |
| Independent biometric credentials | A-MV4 envelope/AAD/transplant/callback/fault tests | A-MV5 106 + 25 + 32 checks, actual Keystore/CryptoObjects/human fingerprints, distinct and equal passwords, actual SAF attachment | Reuse unchanged hardware evidence; integrated A/B use where available |
| Forget and Recover | A-MV5 ordering, failed metadata/cleanup, retained roots, password recovery | A-MV5 native rename/Forget/cold detached startup/Recover | Confirm integrated selected/unselected lifecycle as applicable |
| Selector/accessibility/layout | A-MV5 model/source guards | A-MV5 native nodes, focus, 48dp controls, fonts 1.0/1.3 | Actual dark-system behavior, narrow/scroll/keyboard/TalkBack review |

A-MV0 is architecture evidence, not runtime qualification; its legacy-adoption
proposal was superseded by A-MV1's clean-slate policy. Historical M3D desktop
revision `5fb43c2685bc728b259015b268f81881c44d7e95` and Android revisions
are not the current integrated topology. Its Import/Publish UX and observer target
the owner package and older API assumptions. Do not run that install command as
an A-MV6 substitute. Controlled provider delivery, real SAF initialization,
Syncthing file arrival and authenticated end-to-end Join remain distinct evidence.

## 3. Test topology and environments

Linux 6.18.53 x86_64 host, pinned Nix-shell Gradle 9.8.0, JDK 17.0.20.1+1,
Android SDK API37 / Build Tools36.0.0 / platform tools37.0.1. No agent Nix.
Physical Pixel6a serial `37311JEGR05916`, Android17/API37, fingerprint
`google/bluejay/bluejay:17/CP3A.261005.002.A1/2026101601:user/release-keys`.
1080×2400, density420; font initially1.0, system night mode no, stay-awake0.
Host EDT and device America/New_York; sampled clocks aligned sufficiently for
human same-step OTP comparison. No OTP codes or reusable credentials logged.

Human authorized a separate disposable package and newly named test folders.
Owner package `org.totipo.android` was not installed, reset or uninstalled.
Qualification package is `org.totipo.securityqualification`, initially absent,
using existing test-only signing key and independently verified installed signer
before each retained-data update. Production DEX/resources are copied exactly
from qualified release outputs; instrumentation is separate test-only DEX.

Real topology: Desktop filesystem → existing Syncthing peer replication →
`/storage/emulated/0/totipo-test-sync` → external-storage DocumentsProvider SAF →
app-private no-backup canonical root. Host share is ignored repo directory
`totipo-test-sync`; old vaults, `.stfolder`, `.stversions`, existing peers and
folder configuration were preserved. No direct protocol copying replaced delivery. D additionally has independently
verified87-byte VAULT and1024-byte initial object arrival on final-source campaign
(`D-final-prejoin-arrival`); normal final-APK Join now PASS.
A public nonprotocol probe independently demonstrated real file arrival first.
Initially created unused `/sdcard/Documents/Totipo-AMV6-20261020-{A,C}`
directories remain unbound; they were not the actual shared root.

| Vault | Origin/configuration | Password relationship | Provider child |
| --- | --- | --- | --- |
| A | Desktop-created, Android Join, synchronized | Different from B | `Totipo-AMV6-20261020-A` |
| B | Android-created, local-only | Different from A | None |
| C | Android-created, separately synchronized | Equal to A | `Totipo-AMV6-20261020-C` |
| D | Desktop-created, final-APK authenticated Join PASS | Human-controlled | `Totipo-AMV6-20261020-D` |

A/C are independent child bindings within the existing shared Syncthing root,
not newly configured peer/folder relationships. No claim of separately configured
Syncthing folder IDs is made. This arrangement exercises distinct identities and
SAF grants without modifying existing Syncthing configuration.

## 4. Exact client/dependency versions

Android source **0.1.3 / versionCode4**. Java NIO/core **0.2.0**, released source
`d6310c177ae930df188fd4f5798622c935698b2e`; BC1.86. Protocol **v1/r19**,
spec revision `cdb4e91be1c6d3704874b2b92457ffe7be5e9084`.
Desktop revision **`d8c8ba3f09a806cc6bd854aa26d2cbe09366867e`**, supplied by
human. Syncthing **2.0.x**, human supplied; exact patch versions unspecified,
independent version verification explicitly declined by the human. Desktop OS **Linux/NixOS**, explicitly confirmed by human.
SAF authority `com.android.externalstorage.documents`, document IDs
`primary:totipo-test-sync/Totipo-AMV6-20261020-{A,C}`.

## 5. Successful Join and refusal results

**PASS — real current-client physical Join of desktop-created A.** Human created
A and synthetic `Totipo AMV6 A / desktop-first@example.test` on Desktop. Host and
phone independently matched the 87-byte VAULT and initial 1024-byte object by
SHA-256 after actual Syncthing delivery. Normal Android Join Existing Vault and
system picker authenticated A into a newly allocated private canonical store.
Registry became ESTABLISHED, existing protocol VaultId retained, exact initial
object and logical token imported, only intended A binding configured.

A local ID `71ad97c1a7984788883491f2ec1f59c8`; VaultId
`d249206f629955de72e2e4864a496d284869b1e72af869ff1c004e9259c01dfe`.
Canonical suffix `roots/<local-id>/store` under disposable private
`no_backup/totipo-local-v1`, never SAF. Join is authenticated import, not Create.
One intentionally incorrect password produced an error **human-observed**; the
human then entered the correct password before an intermediate capture. The
refusal is not claimed as an independently captured intermediate checkpoint.
Evidence: `A-host-created`, `A-android-arrival`, `A-joined`, `A-join-validation`.

Wrong/malformed/conflicting VAULT, duplicate identity, canceled Join, unavailable
provider and revoked permission have existing deterministic/physical scopes in
prior reports; they are **NOT RUN anew** in this live journey. Controller Join
passes and SAF initialization are not substituted for the real Join above.

**PASS — repeated real Join on final qualified production APK.** D arrived by
actual Syncthing before Join; normal UI/system picker/password authentication opens
`desktop-final@example.test` into new private local ID
`a415998b81e64470bc05860a30db222b`, canonical root under that entry. Authenticated
VaultId `e084c9649d2775eb807f507f88f7c5451dab167e84e1242d7031bb5cbd7934f1`
equals Desktop/provider VAULT hash; exact initial object, ESTABLISHED registry,
only D binding and fresh session. No inherited C notice. A/B/C identities unchanged.
Evidence: `D-final-joined`, `D-final-join-validation`. This removes final-artifact
ambiguity after D01 without repeating the entire earlier A campaign.

## 6. Bidirectional synchronization and convergence

**PASS — actual Syncthing, not synthetic transport.** Each checkpoint compares
Desktop filesystem, Android shared provider path and coordinated private snapshots
by object name/size/hash and protocol identity. Human independently checks Desktop
logical state after ordinary refresh/reopen. Android captures describe the actual
selected session, not a second test session. Human OTP result is match; no code
recorded. Exact propagation timing is not asserted.

| Scenario | Captured result |
| --- | --- |
| Desktop Add → Android | `desktop-added@example.test` arrives; 2 objects; human same-step OTP match |
| Android Add → Desktop | `android-added@example.test` arrives; 3 objects; human Desktop confirms |
| Different-token metadata edits | `desktop-updated` and `android-updated` converge, no conflicts; 5 objects |
| Test-share replication paused on both ends | Both local edits save; Android SAF publication completes; 6 objects per side, 5 common + one distinct branch each |
| Replication resumed | Both immutable branches arrive; 7 objects, two-head conflict on both clients |
| Whole-alternative Android resolution | Choose `android-branch@example.test`; 8 objects, no conflict on both clients |
| Desktop delete + Android delete | 10 retained objects; both updated tokens TOMBSTONED; only `android-branch@example.test` live on both |

All A authoring/conflict/deletion checkpoints occurred in the same application
session `30e60e01-d1aa-4938-8a44-054b799903b3`. No conflict semantics changed.

**PASS — bidirectional Add repeated on final qualified APK in D.** Human added
`android-final@example.test` on Android and `desktop-extra@example.test` on Desktop;
confirms delivery both ways. Independent capture in same D session verifies all
three live accounts, no conflict/diagnostics, no retry hint, and exact equal VAULT
and3-object sets across host filesystem, phone provider path and private canonical
snapshot. Evidence: `D-final-converged`, `D-final-convergence-validation`. No direct
protocol copy. Final human reports “I didn’t notice any mismatch between android and desktop”.
Record this as cross-client observation; no OTP values captured. Earlier explicitly
confirmed same-step A OTP match remains the precise TOTP comparison evidence.
Paused peers explicitly separate local save, provider publication and delayed
peer delivery; `Local changes published` is not remote receipt. Subsequent equal
object sets and both logical views establish convergence. Evidence is retained as
`A-desktop-added`, `A-android-added`, `A-independent-updates`, `A-paused-branches`,
`A-conflict`, `A-resolved`, `A-deletions` and corresponding validation receipts.

## 7. Local-only and multiple synchronized identities

**PASS — B local-only CRUD alongside synchronized A.** B independently Created
with a different password; Add/Edit/Delete/Add leaves only
`Totipo AMV6 B / local-restored@example.test` live, 4 immutable objects including
tombstone. No binding, pending-publication hint, provider request or unsynchronized
error. A evidence unchanged. B local ID `79b426ff3a2c47a8998a6b74d6eec125`,
VaultId `4eb7ef174083510d05cef9ceec723ed6c26967b6074028c8984bce5a3a518c24`.

**PASS — C separately synchronized with real Desktop observation.** C independently
Created on Android using the same password as A; explicit SAF selection,
Initialize and Sync publish one synthetic `separate-sync@example.test` token.
Host/provider/private VAULT and object hashes match; human opened C on Desktop
and confirmed C-only token. C local ID `f575155a470641d89bbef02ed774e698`,
VaultId `c933b76b344700d6a869aac2c3d4e4d4267d1f78f0a992ed1b88be5b677f9091`.
A/B/C IDs, private roots and protocol identities differ.

**PASS — cross-binding guard; FAIL — original visible refusal (D01).** Human
selected A's folder for C twice. Captures prove C binding/session/data remained
C, A/C provider bytes unchanged. User still saw only C tokens. The ordinary Sync
message hid the refusal; narrowly corrected below. **PASS — final corrected APK
retest**, independently captured in `C-final-folder-refusal` and its validation
receipt: persistent nonempty refusal notice alongside ordinary `No local changes
to publish`; C entry/root/VaultId/binding/token/object unchanged. Host and Android
shared copies of all10 A objects and one C object unchanged. Human confirms visible
notice. An earlier observer3 host connection exited255 before capture; preserve that
evidence interruption separately. Detached observer4 supplied the successful capture
(PID13399), without changing included source/tool inputs. Registry additionally
contains an unattempted PENDING reservation, not an established protocol identity.
Evidence: `B-local-created`, `B-local-crud`, `C-synchronized`,
`C-synchronized-validation`, `C-wrong-folder-attempt`.

## 8. Retirement and interruption scenarios

**PASS — A→B→A→B** normal Change Vault, new activations/sessions, independent
authentication, exact per-entry token lists/bindings, human-confirmed empty search
on replacement. No late callbacks or second session observed. Source/latch tests
supply exact ordering evidence unavailable from UI alone. Paused Syncthing peers
allowed local save and provider publication; that is not SAF-unavailable coverage.

**PASS — corrected APK process restart:** PID10851, all three established entries
retained and no selected controller/session. **PASS — A retains its own binding
and Sync access after C Disconnect**, with unchanged root/VaultId/10 objects/
logical state, no inherited C notice or hint (`A-final-after-C-disconnect`).
Human performed password fallback, selected-only biometric reenrollment, Lock and
fresh biometric unlock on final APK. C Retry access and Disconnect now
**PASS** on final APK: notice cleared, binding absent, hint false, Local only; same
activation/session/root/VaultId/token/object. Conservative unused-grant retention
message is preserved, and no external data removed. Evidence:
`C-final-disconnected` and validation receipt. Existing actual SAF revoke/regrant and
controlled blocked-provider/failed-close evidence remain separately scoped.
A true third-party Binder hang was not manufactured. No timeout admission release,
new retry protocol or background scheduler introduced.

## 9. Biometric integration

**PASS — actual human fingerprint hardware:** A password opt-in enrollment,
Lock and fresh biometric unlock; same private identity and 10-object state with
new session. B initially had no biometric action; A password rejected. B enrolled
using B password and real fingerprint; fresh biometric unlock reopened B-only data.
A→B→A→B used independent real prompts. Disabling A biometric credential left
B biometric unlock working. C equal-password identity remained independent.

Prior A-MV4/A-MV5 exact credential/AAD/transplant, late callback, rotation,
restart and fault tests are retained with their source scope; renewed tests also
cover these unchanged mechanisms. No device enrollment/security policy changed;
lockout or sensor-enrollment invalidation not induced. Final corrected APK A password fallback, reenrollment and fresh biometric unlock
**PASS**, independently captured same A identity/data after C Disconnect. Final B
fresh biometric check after A reenrollment **PASS**, independently captured same
B root/VaultId/4 objects/token state, still Local only with no hint or inherited
notice (`B-final-biometric` and validation receipt). No claim that equal passwords
alone prove physical credential-transplant refusal; deterministic tests do that.

## 10. Create, Forget and recovery

Physical B/C independent Create and local save pass. Prior A-MV1/A-MV5 deterministic
reservation-before-data, incomplete allocation, uncertain persistence, missing root,
corrupt registry, unknown root, cleanup fault and interrupted Forget evidence remains
applicable to unchanged components and final rerun tests. Forget retains encrypted
evidence, reserved ID/root and DETACHED metadata; explicit Recover authenticates
with password. Final-APK normal UI retirement from selected B, followed by Forget B and unselected
C, **PASS**: both DETACHED, A remains ESTABLISHED, no selected controller. Cold
process restart PID13399→14099 retains exact IDs/states; neither entry resurrects
(`B-C-forgotten`, `B-C-forgotten-cold-restart`, validation receipt). This exercises
normal UI retirement before management; direct selected-controller Forget ordering
and interrupted metadata/cleanup remain deterministic evidence. **PASS — B password Recover** on final APK: same reserved entry/root/VaultId,
exact4 immutable objects and logical token/tombstone state; fresh session and
ESTABLISHED state. Human confirms biometric action absent and Local only
(`B-final-recovered`, validation receipt). Biometric removal follows Forget, not
local-only mode. **PASS — C password Recover**, same reserved entry/root/VaultId/token and exact
immutable object; fresh session. C stays Local only without resurrecting old binding
or retry hints (`C-final-recovered`, validation receipt).
No data wipe, automatic repair or destructive fault injection on the only copy.

## 11. UI, accessibility and theme review

Human exercised dedicated selector, Join/picker/authentication error, local Create,
Add/Edit/Delete/Resolve, Lock, Change Vault, Sync management and independent biometric
actions on the physical Pixel. Search cleared across switches. D01 is a concrete
feedback defect, not a broad UI redesign. Final physical layout **PASS** in three phases,12 checks each: font1.0/light,
font1.3/light, font1.3/system-dark. Native selector actions have48dp touch targets,
focusability and no text ellipsis at this Pixel width. Original font1.0/night-no
settings restored in finally; `final-layout-completed-results.json` and
`layout-final-*.log`. An initial attempt waited at locked keyguard and timed out
without exercising layout; original settings were restored (`final-layout-results.json`).
After human unlock, affected phases passed; no source/build change or gate invalidation.
Actual keyboard **PASS**: injected TAB advances native focus through Select Vault1,
2 and3 buttons; only safe selector focus metadata retained (`keyboard-selector`). Actual TalkBack **NOT RUN**;
node inspection alone will not be described as a TalkBack success. The app explicitly
uses the native light Activity theme; system dark mode does not imply dark app styling.

## 12. Defects and minimal corrections

**D01 — rejected folder choice can lose its visible outcome.** Reproduction:
C open → Manage sync folder → Change folder → select A → return. Original binding
safely remains C, but foreground Sync overwrites transient SyncView refusal with
`No local changes to publish`. No security invariant violation or wrong publication
was observed. A/C object/VAULT bytes stayed unchanged.

Minimal correction: per-controller, nonpersistent `folderConfigurationNotice`,
rendered through the existing status/liveness presentation. Ordinary Sync and picker
cancellation preserve it; successful configuration or explicit Retry access clears
it. Failed Disconnect also preserves a visible notice. No admission, provider,
cryptography, storage, grant ownership or protocol semantics changed.

Regression tests: `failedFolderChoiceRemainsVisibleAfterSyncAndCancellationUntilSuccessfulChoice`
and `failedDisconnectNoticeSurvivesSyncUntilExplicitRecovery`. Initial focused2
PASS (6s); broader controller/retirement/binding/grant focused144 tests PASS (1m36s),
including unchanged provider bytes/session and old-binding preservation.

Invalidation: initial final-normal/strict/APK passes are superseded for the corrected
source. Renewed final-normal PASS (2m57s, 89 tasks,21 executed/68 up-to-date).
Renewed strict/artifact and affected final device checks **PASS**. Earlier A/B/C
full conflict journey retains pre-fix artifact scope. Final-artifact refusal,
Join, actual bidirectional Add, independent A/B biometric use and B/C retained
Forget/recovery pass without resetting fixtures.

## 13. Deterministic and physical evidence / security invariants

Observer is test-only, read-only and allowlisted to synthetic metadata and intended
folders. It never authenticates, reads credential material, generates codes, writes
tokens, performs provider transport or opens another session. Local reads run through
the selected worker/coordinated owner. Duplicate/capture failures latch; no passing
scenario is inferred from a capture alone. Human outcomes are labeled separately.
Raw evidence and logs are ignored `.qualification/amv6/`, not Git source inputs.

| Invariant | Current source and deterministic evidence |
| --- | --- |
| 1. At most one application session | VaultSelection/LocalReplicaOwner Admission; ControllerRetirementTest including Join candidate/enrollment and concurrent selection |
| 2. Immutable selected identity/root | LocalReplicaOwner final registry/id/root/activation and bound controller; registry/owner tests |
| 3. Retirement before replacement | VaultSelection success-only release; ControllerRetirementTest closure, failed-close and provider handoff cases |
| 4. Provider work stays with A | ProviderIoLane admission/drain and captured controller/port; ProviderIoRetirementTest |
| 5. Entry-specific Sync/hints | AndroidSyncFolderPort local-ID namespace; controller local-only/restart tests |
| 6. Grant release protects consumers | Shared SyncGrantOwnership and journaled binding metadata; ownership tests including admitted use and uncertainty |
| 7. Entry and exact VaultId biometric binding | Derived alias/path, BiometricRecord AAD; PerVaultBiometricCredentialsTest |
| 8. Java-authenticated biometric identity | Controller actual sessionVaultId check before opened/subscription/Sync; SecurityControllerTest and retirement mismatch tests |
| 9. No inactive automatic Sync | Only selected controller constructed by Application; MultiVaultUiBoundaryTest |
| 10. Local-only needs no provider | Absent binding admission and localMutationSaved policy; zero-provider controller authoring/Resolve tests |
| 11. Registry is not protocol authority | Registry local metadata only; Java recognition/authenticated promotion and duplicate refusal |
| 12. Forget retains encrypted data | VaultForget scoped cleanup and DETACHED transition; VaultManagementTest retained hashes/restart/recovery |
| 13. Uncertain evidence never replaced | Registry allocation/attempt marker, coordinator discovery and create-only enrollment; registry/enrollment fault tests |
| 14. Picker/ingress cannot choose B | MainActivity originating pickerActivation; captured enrollment controller and mutation guards; retirement/OTP/source tests |
| 15. Sensitive lifecycle retirement | Controller/InactivityLock/TotpPresentation/Activity clearing; security/inactivity/retirement suites and unchanged physical A-MV5 evidence |

## 14. Gradle, APK, runtime and human Nix gates

Baseline ordinary full PASS (1m34s,89 tasks12 executed). Initial final normal PASS
(1s), strict offline clean PASS (3m21s,91 executed), 42 suites/446 tests zero
fail/errors/skips. Both APK verifiers and offline runtime/Maven boundary PASS.
These initial final gates were invalidated by D01; baseline remains valid.
Initial APKs retained as `pre-feedback-fix-{debug,release}.apk`.

Commands are exactly AGENTS:

```sh
gradle check :app:assembleDebug :app:assembleRelease
gradle --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug :app:assembleRelease
python3 tools/verify-apk.py app/build/outputs/apk/debug/app-debug.apk --debug-probe
python3 tools/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk --unsigned --no-debug-probe
gradle --offline :app:dependencies --configuration releaseRuntimeClasspath :app:verifyMavenBoundary
```

Device-tool host suite29 tests PASS twice; standalone observer build-only PASS.
Initial qualified production17DEX/resource entries matched prior A-MV5 installed
payload exactly; included tool updates are separately invalidating Nix inputs.
Renewed strict offline clean **PASS**, 3m28s,91 tasks all executed; **448 tests,
42 suites, zero failures/errors/skips**. Both renewed external APK policies and
offline runtime/Maven boundary **PASS** (runtime command1s). Runtime remains only
NIO/core0.2.0 and BC1.86, manifest/variant/version/SDK/resources/components/exports/
backup/permissions boundaries unchanged. Logs: `strict-clean-feedback.log`,
`strict-tests-feedback.json`, `apk-{debug,release}-feedback.log`,
`runtime-boundary-feedback.log`.

| Final strict APK | SHA-256 |
| --- | --- |
| Debug | `7d4f8a34d718a41b1e3fe781503fec8b809f42902f4dbfb0d47b19a095f2e98e` |
| Unsigned release | `6073da972888154126000bfd3c830efc82a9d7b61d290e1d30831e91f9625889` |

Corrected disposable update **PASS**, retaining data (no reset). Independently
pulled installed APK matches all17 final production DEX/resource entries. Signer
independently matched existing test certificate before updating; evidence
`feedback-update-signer.json`, `installed-feedback-final-payload.json`.

Human `nix flake check`: **PASS**, one final routine invocation, human-operated.
Human reports exactly “nix flake check passed”; recorded2026-10-20 16:56:02 UTC
in `final-human-nix.json`. Agent did not run Nix and did not independently capture
its terminal output. All195 frozen input hashes independently match after that
report. No included source, test, build, evaluator or tooling input changed after
freeze or after human PASS. Only excluded docs/report result text was finalized;
this does not invalidate the check under the inspected source/dependency filter.

## 15. Input freeze, invalidation and execution counts

Production/JVM changes are limited to D01 plus its two regressions. Build/package,
dependency pins and evaluator inputs unchanged. Included tools comprise observer
and qualification phase/manifest/payload receipt integration. Corrected qualified input inventory records195 conservative included/evaluator files,
with five changed inputs (controller, Activity, regression tests, observer, runner).
All other hashes unchanged. Final source/test/build/tool freeze is recorded below.
Docs/report excluded from effective package/check
inputs under inspected `package.nix`; tools including prose are included. New files
are intent-added before freeze; cached content must remain empty.

Freeze **2026-10-20 16:49:26 UTC**, `final-freeze-record.json` and
`final-frozen-inputs.json`,195 conservative included/evaluator files, digest
`77430b1ab6756aca0ae75adcae6b5969f89cfc5cd9ce622644c213a98497953a`:

- **Production source stable**: final qualified APK inputs unchanged; final device evidence matches them.
- **JVM tests stable**: final448 tests pass, unchanged test hashes.
- **Build/package configuration stable**: dependency/evaluator hashes unchanged from starting source; normal/strict/APK/runtime gates pass.
- **Qualification harness/tooling stable**: included observer/runner hashes unchanged across final device phases; no further included edits planned.

Git state at freeze: main/starting HEAD unchanged, exactly8 changed paths listed
in section18; two new intent-added files, no staged-content diff.
`git diff --cached --name-only` empty; `git diff --cached --quiet` and
`git diff --check` PASS. Changed-doc local links PASS. Ignored build/cache/fixtures
remain excluded. Subsequent report/README/MULTI_VAULT-only result edits do not
invalidate included inputs under the inspected filter; recheck hashes after human Nix.

| Invocation category | Final count | Reason |
| --- | --- | --- |
| Baseline ordinary full | 1 | Starting committed checkout |
| Focused Gradle tests | 2 | D01 two tests, then broader144-test regression |
| Final ordinary full | 2 | Initial pass invalidated by demonstrated D01 |
| Strict offline clean | 2 | Required renewal after production/test edit |
| External APK verifier commands | 4 (2 sets) | Both strict APK variants |
| Offline runtime/Maven commands | 2 (initial + renewed) | Current runtime graph/boundaries |
| Embedded Gradle APK verifier | 0 | External policy by design |
| Tool host tests | 2 |29 tests each; observer/runner additions |
| Observer build-only | 1 | Compile new test-only observer |
| Physical prepare / retained-data update | 1 / 2 | Initial install, shared-root allowlist, D01 corrected production payload |
| Physical observer phases | 6 | Phases1/2/4/5/6 stopped cleanly; phase3 host connection exited255 |
| Physical layout phases | 4 | 1 keyguard-blocked timeout; 3 completed passes ×12 checks |
| Physical keyboard phase | 1 | 3 native TAB focus transitions |
| Human Nix | 1 | Human-reported PASS on frozen included inputs |

No repeated full gate was used to diagnose D01; focused regressions stabilized first.
No historical full physical campaign automatically repeated.

## 16. Remaining limitations and risks

External-storage SAF / one Pixel / human-supplied Desktop revision and Syncthing
2.0.x are bounded interoperability evidence, not all OEM/provider configurations.
TalkBack and real platform-enrollment invalidation not exercised. Paused-peer edits
do not establish unavailable-provider behavior. Arbitrary provider hangs cannot be
forcibly canceled safely. Existing conservative grant policy can retain grants.
No inactive background Sync; Syncthing delivery is asynchronous. App-data removal/
uninstall can lose private local-only and DETACHED evidence. Registry metadata is
not identity authority. Device/process compromise, rollback, JVM wiping and
conditional clipboard clearing retain documented limits. No reusable secret or
password belongs in this report or retained diagnostic logs.

## 17. Release-readiness assessment

**COMPLETE for the documented environment; ready for a release decision.**
Required real Join/Syncthing, local-only/separate synchronized identity,
retirement/authentication, actual per-vault biometrics, retained recovery and
relevant host/device checks have evidence. Human Nix PASS closes the final gate.
Prior evidence reuse remains explicitly scoped; no synthetic-provider result is
substituted for real Syncthing. No material safety blocker was discovered. D01 is a
visible-feedback defect whose minimal correction now has human-visible and
independent binding/data preservation verification on the final qualified APK.
A-MV6 acceptance criteria are satisfied using current integrated evidence and
permitted reuse of unchanged subsystem coverage. Daily-use suitability is supported
within this tested Pixel/external-storage SAF/Linux Desktop/Syncthing2.0.x topology.
No mandatory human UI/integration action remains. The limitations in section16,
including actual TalkBack NOT RUN, remain visible and are not universal platform
qualification. This report does not authorize publishing a release.

| Final integration scenario | Status | Evidence boundary |
| --- | --- | --- |
| Desktop-created physical SAF Join | PASS | A full journey; repeated D on final APK |
| Bidirectional actual Syncthing | PASS | A CRUD/conflict; repeated D Add on final APK |
| Local-only and independent synchronized identities | PASS | A/B/C distinct roots and VaultIds, equal A/C password |
| Complete switch retirement and authentication | PASS | Physical switches + final deterministic latch tests |
| Per-vault actual hardware biometrics | PASS | A/B independently; final reenrollment and scoped survival |
| Forget/restart/password Recover | PASS | Final B/C DETACHED then same encrypted identity/data restored |
| Original folder refusal feedback | FAIL, corrected | D01 reproduced, minimal correction and regression/device PASS |
| Applicable host/artifact/runtime/device gates | PASS | Final448-test/strict/APK plus bounded physical evidence |
| Human Nix | PASS | Human-operated, reported PASS;195 frozen hashes unchanged |
| Actual TalkBack / arbitrary-provider hang / sensor-enrollment change | NOT RUN | No node-only or controlled-provider substitution |


## 18. Changed files and final Git status

Eight paths: README.md, MULTI_VAULT.md, AndroidVaultController.java,
MainActivity.java, AndroidVaultControllerTest.java, this report,
tools/device/IntegratedMultiVaultObserver.java and tools/device/qualify-security.py.
Report/observer new with intent-to-add only; other edits unstaged. Final-device
preservation PASS: owner APK path unchanged, font1.0/night-no/stay-awake0 restored,
qualified disposable installed APK hash unchanged. No owner reset/uninstall/update.
Exact status, included input hashes and empty cached-content evidence are captured
in ignored final-freeze/final-git-state receipts. Final `main` HEAD is unchanged.
`git diff --cached --quiet` and `git diff --check` PASS; no staged file content.

```text
 M MULTI_VAULT.md
 M README.md
 M app/src/main/java/org/totipo/android/AndroidVaultController.java
 M app/src/main/java/org/totipo/android/MainActivity.java
 M app/src/test/java/org/totipo/android/AndroidVaultControllerTest.java
 A review/A_MV6_INTEGRATED_MULTI_VAULT_QUALIFICATION_REPORT.md
 A tools/device/IntegratedMultiVaultObserver.java
 M tools/device/qualify-security.py
```

The two ` A` entries are intent-to-add only. No ignored fixtures/build/cache/secret
state is Git-known.

## 19. Handoff and publication boundary

No commit, tag, release, push or remote CI dispatch. No agent Nix command.
Retain owner application, prior test folders and all new disposable evidence.
All required human qualification actions are complete, including the final Nix gate.
Final human cross-client observation records no mismatch. No separate final
layout-service assertion is inferred; actual TalkBack remains NOT RUN. Retained
disposable app, A/D bindings, B/C local-only recovered stores, unused reservation
and new/old external folders are left intact.
