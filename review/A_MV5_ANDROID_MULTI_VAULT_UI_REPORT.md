# A-MV5 — Android multi-vault selection and management

Status: implementation, host/artifact gates, relevant disposable physical journeys
and final frozen human `nix flake check` PASS. The final Nix result is user-reported;
no terminal output or duration was supplied. All 185 frozen inputs were verified
unchanged after that result. The earlier human PASS was superseded by harness fixes.

## Starting evidence and scope

Started from clean `main` at `beb24a22ed24ea3b1e566a3ae610ddb89b00003e`, committed
A-MV4. `git branch --show-current` returned main; `git rev-parse HEAD` returned
that hash; `git status --short` was empty. No prior work needed preservation.
Classification before edits: PRODUCT_CODE, UI_ONLY, LIFECYCLE_SECURITY,
TEST_OR_QUALIFICATION_TOOLING. No dependency, protocol, cryptography, build,
manifest, version, signing or other-repository change.

Read AGENTS, README, dependency provenance, A-MV0–4 ownership/storage/Sync/biometric
reports, relevant daily-driver, token lifecycle, SAF/Create/Join, security and OTP
ingress evidence, actual production/test/device code, package.nix, flake.nix and CI.
Read exact Java guidance f0a028676c1801a10b2d8d2650bf9c24357d1fa1, fetched read-only;
SHA-256 `338837e3563446f8ec11f7bb47c0e4f3ff5502b7608e397f9b8c4d75372715fd` matches
provenance. Java remains 0.2.0 and the pinned released source/protocol.

Baseline command: `gradle check :app:assembleDebug :app:assembleRelease`, pinned
existing Nix/direnv Gradle/JDK17/SDK environment, PASS in 1m32s, 89 tasks (12 executed,
77 up to date), before source edits. Agent Nix invocations: zero. Local logs/evidence
are ignored under `.qualification/amv5/`.

## Application and Activity integration

`TotipoApplication` owns the existing `VaultSelection`, immutable per-controller
root/activation and shared admission. `VaultUiModel` is a small non-secret startup/
name policy, not another session owner. MainActivity observes Application selection;
activation replacement clears presentation and recreates the Activity. The existing
controller states remain authoritative for discovery, authentication and token work.

Initial setup retains A-MV1's first durable unused reservation and waits for explicit
Create/Join. Exactly one established non-detached entry with a valid matching last
selection receives its normal locked controller. Multiple, pending or invalid-last
cases require explicit selection. A valid last-selected established entry is marked,
never implicitly authenticated. Authentication requires password or Use biometrics.
No inactive controller, session, biometric helper or provider probe populates the list.
Summaries inspect bounded local metadata/grants and say Local only, Sync folder
configured, Folder access needed or an appropriate recovery condition.

Change Vault synchronously clears passwords, token forms/search/reveal presentation,
clipboard ownership and prompt/enrollment authority, invalidates the picker capability,
then requests existing complete A-MV2 retirement and renders its neutral snapshot.
The selector appears only after Application success, including actual provider drain
and completion handoffs. Failed close retains selection/resources and safe retry;
a stuck provider may require process restart. Back from selection exits; it never
restores an old controller or bypasses authentication. Rotation keeps the Application
session but discards sensitive setup/prompt authority. Management presentation may
return to selection on recreation; cleanup itself belongs to Application.

Important source locations (current implementation):

| Decision | Source |
| --- | --- |
| Startup policy and fallback names | `VaultUiModel.java:8` |
| Metadata summaries; no inactive sessions | `TotipoApplication.java:34` |
| UI activation and allocation | `TotipoApplication.java:55`, `:69` |
| Immutable controller factory | `TotipoApplication.java:172` |
| Selection observer and surface | `MainActivity.java:33`, `:49` |
| Sensitive retirement and Back | `MainActivity.java:105`, `:521` |
| Existing picker activation guard | `MainActivity.java:507` |
| Success-only replacement and maintenance | `VaultSelection.java:21`, `:39`, `:57` |
| Recoverable Forget transaction | `VaultForget.java:10`, `TotipoApplication.java:95` |
| Scoped credential cleanup | `TotipoApplication.java:210` |
| Schema/detachment/duplicate recognition | `VaultRegistry.java:18`, `:188`, `:200` |
| Invalid navigation-hint refusal | `VaultRegistry.java:294` |
| Authenticated duplicate-Join refusal | `AndroidVaultController.java:709` |

All paths above are under `app/src/main/java/org/totipo/android/`.

## Create, Join and duplicate identity

Create reserves a new managed entry, except that the sole initial unused reservation
is explicitly reused. Existing A-MV1 reservation-before-allocation and durable attempt
marker precede Java Create. New creation has no Sync binding and needs no provider,
permission, Syncthing or biometric credential. Existing authenticated promotion opens
the normal session; independent roots/passwords/identities remain separate.

Join enters a distinct setup presentation with SAF folder selection and authenticated
existing-vault recognition. It cannot fall through to Create when canceled or
recreated. Existing transient-session admission, exact candidate equality, fresh
provider recheck, create-only enrollment and normal open/Sync remain unchanged.
Cancel performs complete retirement and retains the pending root/entry and any
scoped metadata. Attempted/uncertain creation or enrollment never retries as fresh
Create. Successful data with failed metadata acknowledgement is retained for recovery.

After detached candidate authentication and before local enrollment, the controller
asks its immutable owner to refuse possible duplicates. UI activation performs the
same conservative recognition against other known roots. Reads stop at 88 bytes,
reject unsafe paths and use Java structural recognition only, with no session,
provider scan or protocol parsing in Android. Recognition grants no authenticated
identity authority. All roots, including forgotten ones, participate. Matching roots
are preserved and require explicit storage recovery; histories are never merged.
No persistent protocol-identity cache or additional credential format was introduced.

## Labels, Forget and retained-root recovery

Rename uses the existing registry text rules: optional, at most 128 UTF-16 code units,
no controls or malformed Unicode. Labels do not affect any ID/path/key/binding.
Unnamed entries use ordered Vault N names; duplicate labels include Local entry N.

Schema 2 appends FORGETTING and DETACHED to schema-1 PENDING/ESTABLISHED. Schema 1
remains readable and is written as schema 2 only on the next actual metadata change.
The existing bounded checksum/atomic-force-readback transaction is unchanged.
Every ID/root remains reserved forever and remains in complete grant inventory.
Unknown-root checks are unchanged; forgetting never deletes canonical data.

Management requires no selected controller; a selected vault first uses Change Vault.
`VaultForget` reserves scoped maintenance admission, verifies full retirement,
persists FORGETTING, runs A-MV4's scoped Application credential cleanup, retires the
A-MV3 binding and then persists DETACHED. Selection snapshots remain responsive
because no selection monitor is held across filesystem/Keystore work. New activation
is refused during maintenance. Nested cleanup requires the same worker and entry.

Failure before intent runs no cleanup. Failure after intent retains exact retry
association, including a crash after biometric deletion and before detachment.
Missing/unsafe credential storage cannot claim cleanup success. Failed binding writes
retain their existing journal/recovery authority. Deferred shared/ambiguous or failed-
release grants retain durable records and are visible/retryable on detached entries;
no speculative revocation is performed. Lost final registry acknowledgement can
leave DETACHED on disk only after scoped cleanup completed; restart preserves the
same root. Uncertain registry writes poison the instance and require explicit recovery.

Forgotten entries remain hidden across restart. Manage Vaults → Recover selects their
retained root for ordinary password authentication. Selection alone leaves DETACHED;
only existing authenticated promotion returns ESTABLISHED. Create is forbidden there.
Biometrics remain removed and Sync disconnected. Recovery depends on private data
remaining on this installation and the password. App-data removal/uninstall can
permanently destroy local-only or forgotten vaults; no post-uninstall recovery claim.

## Sync, biometrics, ingress and recovery

Selected-folder management, local-only saves, optional attachment, disconnect, retry,
provider identity, observation and publication algorithms remain unchanged. Selection
metadata does not claim synchronization success. A-MV3's shared SyncGrantOwnership
continues to protect all remembered/recovery/admitted references. Picker results
retain original activation; they cannot configure another selected controller.

Only the selected credential helper is constructed. A-MV4 key/record cryptography,
strong/auth-per-use operation and ownership guards remain unchanged. Password fallback
and selected-entry disable/reenrollment remain available. Prompts are explicit;
rotation/background/retirement revoke authorization and never transfer passwords.
No process-compromise protection beyond A-MV4's platform trust model is claimed.

External enrollment retains its original controller and unchanged mutation-boundary
checks. Selection/setup/retirement/recovery cannot create an ingress session or choose
a vault; a new scan/share after explicit unlock is required. Old drafts expire and
are never saved in metadata or Activity state. The 15-minute inactivity policy and
background accounting remain unchanged.

Recovery distinguishes registry corruption/unknown roots, missing storage, pending or
attempted creation/Join, incomplete Forget, biometric convenience failure, Sync
configuration/grant failure and retirement failure. Only unused unattempted allocation
has an automated directory recovery action. Pending retained VAULT can authenticate;
attempted absence, duplicate histories and corrupt metadata require evidence-preserving
manual recovery. No automatic deletion, adoption, overwrite, merge or broad scan.

## Deterministic and device evidence

Focused run 1: compile PASS; 53 tests, two diagnostic failures from now-obsolete schema
version and setup-folder placement assertions. Updated unsupported-version test to
reject schema 3; changed the UX assertion to Join/setup because Create is local-only.
Focused run 2: PASS, 62 tests, 30s. Focused run 3: 210 tests, one new source-tripwire
failure because it incorrectly prohibited the word password in recovery instructions;
209 other tests PASS. The tripwire now prohibits password fields/buffers, preserving
accurate password recovery guidance. Focused run 4: PASS, 211 tests, 2m02s, including
final maintenance admission/responsiveness and authenticated duplicate-Join checks.
No full gate was repeated for these focused failures. Focused run 5: PASS, 34 tests,
7s, for the final stale-last-selection correction. A validly checksummed but stale
last-selection hint is discarded in memory without rewriting registry bytes or
selecting a replacement. Entry/checksum/root validation remains unchanged.

Final normal attempt 1 PASS (3m10s, 89 tasks, 21 executed/68 up to date), superseded
by this late review correction and explicit cross-thread visibility on management
presentation fields. Those source changes require focused registry/UI checks and a
renewed final normal checkpoint before strict-clean. No strict, APK or physical pass
was invalidated. Final normal attempt 2 PASS (2m47s, 89 tasks, 17 executed/72 up to date),
446 JVM tests and zero failures/errors/skips. Strict-clean PASS (3m09s, all 91 tasks executed), 446 tests with zero
failures/errors/skips against the same stable corrected production/test/build inputs.

VaultManagementTest covers startup policy, schema-1 reading/schema-2 writing, names,
rename identity/bytes, detached restart, password-authenticated recovery, no ID reuse,
cleanup ordering/failures, pre-intent and final-write failures, lost acknowledgement,
root inventory, missing/unknown roots, duplicate refusal, and held cleanup admission
without a blocked selection monitor. ControllerRetirementTest extends its held provider
case to prove Forget cannot mutate metadata or run cleanup before drain.
AndroidVaultControllerTest authenticates a duplicate Join and proves no enrollment or
creation marker, preserving exact prior root bytes and wiped credential input.
MultiVaultUiBoundaryTest adds presentation/admission/no-auto-auth/source tripwires.
Existing controller/security/biometric/Sync/OTP source guards remain intact.

Standalone build-only harness development compiles: seven PASS. All accepted device
preparations compiled successfully; one diagnostic build used a nonpublic Android
method and failed javac before installation, then used public UiAutomation.waitForIdle. Device-tool unit suite PASS,
29 tests in 28.298s. MultiVaultUiRegression adds
real selector/Create/Join-cancel/switch/rename/Forget/recovery controls on the disposable
package. ControllerRetirementRegression uses actual Change Vault while provider I/O
is held and verifies the selector only after drain. PerVaultBiometricRegression uses
explicit Use biometrics and production selection UI, with Application-initiated
retirement for the modal outstanding-prompt case; no authorization injection exists.
Accepted physical results and remaining gates appear below; final host/artifact checks PASS.

## Physical-device journeys and evidence

Pixel 6a, serial 37311JEGR05916, Android 17 / API 37, fingerprint
`google/bluejay/bluejay:17/CP3A.261005.002.A1/2026101601:user/release-keys`.
Only disposable `org.totipo.securityqualification` was installed, updated or removed.
The owner package path, USB stay-awake 0 and font scale 1.0 match their initial
receipts. No owner vault, folder, security setting or biometric enrollment was changed.
The disposable package was removed after final qualification. The two unique external
fixture folders remain for review; no owner directory was used or removed.

All accepted phases used the same strict-clean release production bytes:

| Phase | Checks | Evidence under ignored `.qualification/amv5/` |
| --- | ---: | --- |
| Native-menu diagnostic | 6 | device-mv5-menu-1.log |
| Initial setup, independent local Create, token, Lock/unlock, switch, Join cancel | 47 | device-mv5-first-4.log |
| Cold startup, highlighted last selection, Back, ingress refusal, passwords, rename, Forget | 45 | device-mv5-restart-2.log |
| Detached cold startup, explicit password recovery, unused allocation, missing root | 21 | device-mv5-recover-1.log |
| Native text/touch/focus/no ellipsis, font 1.0 | 12 | device-mv5-layout-normal.log |
| Same controls, font 1.3, restored afterward | 12 | device-mv5-layout-enlarged.log |
| Actual Change Vault with latched provider, rotation, clipboard, stale callbacks/SAF | 30 | device-retirement-3-pass.log |
| Edit/Delete/Resolve, same session, controlled transport | 53 | device-lifecycle-pass.log |
| Real human strong biometrics for A/B/C, cancellation, swap/corruption, rotation/background | 106 | device-mv4-first-6-pass.log |
| Cold biometric credentials and explicit selection | 25 | device-mv4-restart-pass.log |
| Actual SAF attachment, Initialize/Sync, biometric reopen, Disconnect | 32 | device-mv4-sync-2-pass.log |

Total: 11 accepted phase runs, 389 checks including the six-check diagnostic.
The 106-check biometric phase uses actual framework CryptoObjects, auth-per-use
Keystore operations and human fingerprints, with no success injection. A/B have
independent passwords; A/C share a password while retaining distinct identities and
credentials. Disabling A leaves B usable. A prompt outstanding during retirement
cannot open B; rotation/background cancel old authorization; fresh prompts succeed.

SAF folders were exactly validated against the picker result before binding:
`Documents/Totipo-AMV4-2fd34f29b7714f8284868d4a9833b115` (first interrupted run) and
`Documents/Totipo-AMV4-3f9740fed4ee43f8b0766e939c4a5cc9` (accepted run).
B's initialize/explicit Sync was quiet, A/C remained unbound, credential bytes survived
attachment/disconnect, and B unlocked with biometrics both configured and local-only.
This proves on-device SAF/provider behavior and controlled transport; no live Syncthing
peer campaign or universal provider coverage is claimed. Successful authenticated Join
and duplicate refusal have deterministic controller coverage; physical UI covers Join
setup/cancellation, not a successful two-device Join. Actual TalkBack service activation
and a new dark-theme campaign were not run; native accessibility nodes, enabled states,
focusability, 48dp targets and normal/enlarged text were checked.

Failed/precondition phases are retained, not counted as PASS:

- MV5 first attempts 1–3: locked-device wait; unsupported native accessibility click;
  then coordinates sampled an animating popup. Native pointer delivery now waits for
  public accessibility idle, refreshes bounds and checks enabled state. The narrow
  menu probe and complete first journey passed afterward.
- MV5 restart attempt 1: harness awaited case-sensitive Save. Native buttons expose
  transformed capitalization; exact case-insensitive whole-label lookup fixed it.
  The same retained fixture then passed rename and Forget/recovery.
- Biometric first attempts 1–3: normal-unlock precondition, no credential operation.
  Attempt 4 reached A enrollment but the prompt expired without authorization; the
  operator reported it was not visible, and only that disposable fixture was restarted.
  Attempt 5 passed real A/B enrollment/unlock but asserted disable before input delivery
  completed. Waiting for the actual credential result fixed this; attempt 6 passed all
  106 assertions. No credential or authentication assertion was removed.
- Retirement attempt 1: unlock wait expired. Attempt 2 checked sensitive snapshot before
  injected input reached main. A main-thread idle barrier preserved the assertion;
  fresh attempt 3 passed all ownership/drain checks.
- SAF attempt 1: exact folder approved, then the harness clicked Initialize before the
  asynchronous management view/control was ready. Main-thread visible/enabled control
  polling fixed this; the preserved biometric fixture passed renewed SAF attempt 2.

The test-only IdleActivity uses KEEP_SCREEN_ON/SECURE between phases, owns no session
or credential, changes no device policy and cannot bypass keyguard. Its nested-class
component is shell-quoted so `$IdleActivity` is not expanded remotely. Device harness
helper changes remain outside production DEX. The final common tap helper was exercised
through the complete accepted SAF journey; earlier biometric assertions remain evidence
against identical production bytes. Controlled latches, not timeout release, drain the
retirement provider. Font-restoration and device-final receipts confirm settings.

## Gradle, APK, dependencies and Nix

Final normal attempt 2 and strict offline clean PASS as recorded above. Both external
APK commands PASS against strict outputs; offline runtime graph/Maven boundary PASS
in 1s. Runtime remains NIO/core 0.2.0 and BC 1.86 only. Packaged identity/SDK,
permissions/components/exports/filters/backup, branding and variant exclusions PASS.
VERSION / VERSION_CODE remain 0.1.3 / 4. Embedded Gradle APK-verifier executions: zero.

- Debug SHA-256: `29d725538fb443137140638ef05b0dc8677ced996d2b29828d157f3143c2f162`.
- Unsigned release SHA-256: `66b8f1452da945638965989693f53c6050a3791607c099f27b3ee37f3078bc54`.

Logs: final-normal-2.log, strict-clean.log, apk-debug.log, apk-release.log,
runtime-boundary.log and strict-results.json. Independent installed APK pulls before
and after physical testing match both production DEX files and all 15 resources,
17 entries total (installed-ui-payload.json and installed-final-payload.json).
Every prepare/update also checks exact production payload before installation.
All 138 qualified host inputs still match qualified-host-inputs.json. No production,
JVM test or build input changed after strict-clean; subsequent fixes were tool-only.

Actual package.nix/flake/CI filtering was inspected. app/src, gradle, tools, exact
build/lock/version/license/branding inputs and evaluator/cache inputs affect checks.
Device tools have no Gradle/JVM consumer, so their corrections require affected device
reruns and final Nix, without invalidating Gradle/APKs. README, MULTI_VAULT.md and review
prose are excluded from effective package/check inputs; later report-only edits do not
invalidate the frozen gate. No ignored build/cache/qualification/secret path is Git-known.

Execution counts:

| Gate | Count and outcome |
| --- | --- |
| Baseline ordinary full Gradle | 1 PASS |
| Focused Gradle | 5: three PASS, two diagnostic FAIL, explained above |
| Separate compile-only Gradle | 0 (compilation included in focused runs) |
| Final normal full Gradle | 2 PASS; first invalidated by stale-selection correction |
| Strict offline clean | 1 PASS |
| External artifact verifier | 2 commands, one final debug/release set, PASS |
| Runtime/Maven boundary | 1 PASS |
| Standalone build-only harness compiles | 7 PASS; prepare/update compilation additional |
| Device tool unit tests | 1 PASS, 29 tests, 28.298s |
| Physical phase invocations | 23: 11 accepted PASS, 12 diagnostic/precondition runs |
| Independent installed production comparisons | 2 PASS; prepare/update comparisons additional |
| Human Nix | 2 user-reported PASS: early superseded, final frozen PASS |
| Agent Nix | 0 |

Physical counts comprise ten MV5 phases, six biometric-first phases, one biometric
restart, two biometric-SAF phases, three retirement phases and one token-lifecycle
phase. An accidental combined --remove --prepare was rejected by the existing-package
guard before install/removal; separately ordered commands then prepared the fixture.
This is not an additional physical phase. All repeats and input invalidations are
accounted for above; no repeated full Gradle gate was used for harness diagnosis.

## Remaining limitations and A-MV6

All required qualification gates are complete. No known material implementation
safety blocker remains from host or accepted device testing. Conservative
possible-duplicate recognition can require manual recovery rather than activating two
retained histories; registry metadata is never cryptographic authority. Corrupt registry,
unknown roots and attempted creation without usable VAULT require preserving evidence;
no automatic repair/adoption/deletion is provided. Successful provider enrollment,
interrupted persistence and incomplete cleanup retain their existing uncertainty rules.

A-MV6 may broaden integrated peer/Syncthing/Join/provider and accessibility/theme/device
coverage. A stuck provider can require restart; there is no forced ownership release.
Existing platform enrollment-change/sensor limitations and UID/Keystore/process trust,
JVM wiping, rollback and clipboard limits remain. Biometrics do not protect against
complete application-process compromise. Private local-only/forgotten data can be lost
permanently on app-data removal/uninstall; no post-uninstall recovery is promised.

## Final input freeze and review state

Production source stable; JVM tests stable; build/package configuration stable;
qualification harness/tooling stable after accepted physical reruns. The conservative
Git-known input hash inventory contains 185 files (final-frozen-inputs.json); all
138 qualified host inputs are unchanged. Git status, changed-path list, empty staged
content list, cached-quiet PASS and diff-check PASS are recorded in final-freeze.json
before asking for human Nix. Later excluded report-only updates are permitted.
Final human `nix flake check` PASS was reported after this freeze. All 185 frozen
input hashes still match. Only this excluded report and ignored evidence receipts
were updated afterward; no qualification input was changed or gate invalidated.

Branch remains `main`; HEAD remains `beb24a22ed24ea3b1e566a3ae610ddb89b00003e`.
The following exact changed paths are available for review:

```text
 A MULTI_VAULT.md
 M README.md
 M app/src/main/java/org/totipo/android/AndroidVaultController.java
 M app/src/main/java/org/totipo/android/LocalReplicaOwner.java
 M app/src/main/java/org/totipo/android/MainActivity.java
 M app/src/main/java/org/totipo/android/TotipoApplication.java
 A app/src/main/java/org/totipo/android/VaultForget.java
 M app/src/main/java/org/totipo/android/VaultRegistry.java
 M app/src/main/java/org/totipo/android/VaultSelection.java
 A app/src/main/java/org/totipo/android/VaultUiModel.java
 M app/src/test/java/org/totipo/android/AndroidVaultControllerTest.java
 M app/src/test/java/org/totipo/android/ControllerRetirementTest.java
 M app/src/test/java/org/totipo/android/DailyDriverUxTest.java
 A app/src/test/java/org/totipo/android/MultiVaultUiBoundaryTest.java
 A app/src/test/java/org/totipo/android/VaultManagementTest.java
 M app/src/test/java/org/totipo/android/VaultRegistryTest.java
 A review/A_MV5_ANDROID_MULTI_VAULT_UI_REPORT.md
 M tools/device/ControllerRetirementRegression.java
 A tools/device/MultiVaultUiRegression.java
 M tools/device/PerVaultBiometricRegression.java
 M tools/device/TokenLifecycleRegression.java
 M tools/device/qualify-security.py
```

New paths are intent-to-add only. `git diff --check` and `git diff --cached --quiet`
PASS; no staged content exists. No commit, tag, release, push or remote CI dispatch
occurred. No totipo-java, totipo-spec, protocol, dependency or cryptographic semantics
were modified. The owner's installed app and real data were untouched.
