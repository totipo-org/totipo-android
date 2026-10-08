# M2B manual token enrollment

**Complete:** agent validation, human Nix checks and human-reported focused physical smoke passed. Changes remain unstaged/uncommitted for review.

## 1. Starting state

Branch `main`; HEAD `e473a27992fe916031b28fdfe69bae1768039c57` (committed M2A).
Agent ran `git branch --show-current`, `git rev-parse HEAD`, and `git status --short` before edits. Worktree was clean. No AGENTS.md was found in the local project/parent tree.

## 2. Released Java authorship API inspection

Inspected `.gradle/m1f-inspection/totipo-core-0.1.5-sources.jar` and released binary (javap confirms public symbols), current Android coordinator/controller/Activity, M2A tests/debug fixture, and locally available desktop `TokenEditorPanel`, `Base32`, and token draft/TokenWrites/TokenWriteController precedent. The inspected binary SHA-256 `e99609e59db1d9f52f80c446060e63ce7dde17ad69252fa87d397d0cb1577f81` matches gradle/verification-metadata.xml. The source JAR SHA-256 is `d797c0854db469e8288b787dcbeb3dd3eb8e83062bea5c42b0013082b2cccdc4`.

Supported public path: existing `VaultSession.state()` → `VaultState.createToken()` → `CreateToken` / `TokenEditor<CreateToken>` setters → `NewSecret.copyOf(byte[])` → `save()` → `SaveResult`. Editors and NewSecret are AutoCloseable. The coordinator owns and closes both locally. `TokenId` and `TokenDescriptor` are descriptive public values. `TokenValue` is an internal format model, inspected for semantics only; Android never imports it.

Java Create defaults: ACTIVE, empty issuer/account, SHA1, 6 digits, 30 seconds. Issuer/account are exact strict UTF-8, individually at most 256 bytes, including rejection of malformed UTF-16; empty values are valid. Algorithms SHA1/SHA256/SHA512; digits 6–8; period integral seconds 1–4294967295; secret 1–128 bytes. Setters validate fields. Java owns domain validation, semantic freeze, immutable object identity, encryption and configured-store publication. No Java release is needed.

`ApplicationSession.Editor.save()` freezes semantics, closes the editor before publication, and returns Java's result. `Frozen.attempt()` conservatively marks publication uncertain before provider entry. Any I/O/runtime publication failure yields PublicationUncertain, even when the SPI returns Failed. A durability acknowledgement yields Saved and automatically calls requestRefresh. New tokens get independent Java-generated identity; equal issuer/account is not a duplicate constraint.

## 3. Manual-add product model

OPEN screen has explicit Add token. Platform form: Issuer; Account / label; masked Base32 secret; labeled SHA1/SHA256/SHA512 spinner; labeled 6/7/8 digits spinner; numeric period seconds. UI defaults match Java and desktop: SHA1/6/30. ACTIVE and empty client metadata remain Java defaults. No unsupported fields or dependencies added.

## 4. Secret-entry format

Core 0.1.5 has no public Base32 parser and accepts NewSecret raw bytes. Android's small `Base32` decoder matches the locally inspected desktop decoder. This is ingress decoding, with no OTP crypto.

Accepted syntax: ASCII A–Z or a–z and 2–7; ignore ASCII space, tab, CR, LF, and hyphen anywhere; optional exact RFC 4648 trailing `=` padding (0/6/4/3/1 for symbol-count residues 0/2/4/5/7 modulo 8). Reject all other residues/characters, symbols after padding, incorrect padding count, nonzero unused trailing bits, and decoded length outside 1–128 bytes. No Unicode whitespace normalization or byte-changing guesswork. Input never appears in errors.

## 5. Input validation

Activity catches missing secret and malformed/out-of-range integral period with safe widget errors. Discrete selectors constrain algorithm/digits. The worker rejects malformed Base32 before editor creation/save. Java setters remain authoritative for semantic field validation; issuer/account are passed exactly without trimming or truncation. Java exception text is never shown. Java semantic rejection receives a safe product message with field bounds.

## 6. Credential and secret handling

Activity owns unsent widgets only. At submission it copies Editable text directly into a char[] (no toString for secret), clears the widget, and transfers exclusive buffer ownership in AddTokenRequest. Rejected admission wipes the request too. Coordinator decodes into a local byte[], uses/ closes NewSecret and editor, and wipes decoded bytes and request chars in finally. Controller retains no secret/form request in persistent state after completion. No Bundle, preferences, file, log, report or exception-text secret persistence. No JVM memory-erasure guarantee is claimed; Android widget/IME and Java runtime copies cannot be proven erased.

## 7. Same-session authorship

Activity → AndroidVaultController.addToken(request) → ForegroundVaultCoordinator.addToken(request) → existing session.state().createToken(). Existing bounded worker and admission only. No new executor, store, root export, password prompt, KDF, open or close on add. Tests compare real session/coordinator identity and authentication counters.

## 8. Publication and result semantics

Detached AddTokenOutcome retains ADDED, INVALID_SECRET, INVALID_FIELDS, CONFLICT, FAILED (including actual SaveResult.Reason), PUBLICATION_UNCERTAIN, SESSION_UNAVAILABLE and BUSY. Controller admission returns acceptance, not success; asynchronous snapshot carries outcome. Create cannot normally return AdditionalConflict in released Java, but its independently owned resolution is closed internally and conflict is represented separately if encountered.

PublicationRetry never crosses the product boundary. On uncertainty its exact-publication capability is closed without retry, the form closes and message says publication uncertain, with explicit Refresh offered. User must inspect observation before deciding to author again. Android does not regenerate or automatically retry content. Unexpected unchecked failures after entering save are conservatively uncertain. Definite Failed retains Java reason. Resource cleanup and a later observation error cannot downgrade acknowledged Saved to failure: outcome and safe message survive.

## 9. Refresh and state integration

Acknowledged Java save already requests refresh; Android issues none redundantly. The existing session publisher signals the existing controller worker to read a detached View. No descriptor is inserted into Android state or mutation cache. Replay/current state can precede observation of the new row; success means publication acknowledgement, not observation completion. Uncertainty permits an explicit existing Refresh.

## 10. M2A interaction

Entering Add calls hideCode. Submission and ordinary state replacement also clear reveal via existing controller rules. New rows arrive through existing TokenListAdapter with Show code; no automatic reveal. Existing reveal/copy rules remain. Tests verify concealment, RFC SHA1 output at time 59 and real Java reveal; all supported algorithm/digit combinations generate Java-backed codes.

## 11. Activity lifecycle

Form lives only in Activity. Cancel clears secret and returns to the list without a vault command. Back cancels when idle (platform predictive-back callback on API 33+, legacy callback on API 30–32); busy Back leaves serialized work running. onStop clears unsent secret; reconstruction starts at the safe list and keeps application-owned vault open. No form/secret Bundle persistence; saved state disabled on form hierarchy and fields. Failed submission keeps non-secret fields only; submitted secret was already cleared. Recreation during an operation can still receive the detached completion through application controller observation.

## 12. Accessibility and security

Platform widgets, labels linked with labelFor, explicit Add/Cancel, password masking, saved text disabled, autofill disabled, no secret contentDescription or live secret announcement. FLAG_SECURE retained. No AndroidX/Compose/Material. Focused form/rotation behavior passed human smoke on the Pixel 6a release build. Specialized assistive-technology coverage and Back behavior across all supported runtimes were not separately tested.

## 13. Tests

New automated cases cover desktop Base32 normalization and padding; RFC decoding; malformed/empty input and trailing bits; byte bounds and buffer clearing; real default add; unchanged session identity and no reopen; ordinary observed row and initial concealment; RFC Java TOTP and existing reveal; duplicate labels; all nine SHA1/SHA256/SHA512 × 6/7/8 configurations with custom 45-second period; Java UTF-8 issuer/account limits, malformed Unicode, exact whitespace preservation; period limits and unsupported selections; uncertain SPI failure before persistence; uncertain return after actual persistence plus explicit same-session refresh; all definite Java failure reasons; busy duplicate/Lock/Refresh/Sync rejection; Lock taking ownership before add admission; safe Activity wiring and source/APK exclusions.

Cancel and recreation have source-boundary regression checks plus human-reported physical smoke PASS on the Pixel 6a release build. They do not have an executed automated Android widget instrumentation test. M2A tests are retained, including crypto/presentation/clipboard/expiry tests. One later rerun exposed an existing await-helper race: it checked idle before pumping callbacks, then asserted idle after a callback could schedule an observation read. Both controller test helpers now pump before testing the condition and return on the observed condition; no scenario assertion was removed. Test-only fixtures use public RFC secrets and public Java authorship; fault injection remains under src/test.

Required scenario coverage (automated results refer to the final 163-test run):

| # | Scenario | Evidence / qualification |
|---|---|---|
| 1 | OPEN Add action | EnrollmentSourceGuardTest wiring passes; human device PASS |
| 2 | Cancel clears secret, stays OPEN | Source wiring passes; human device PASS |
| 3 | Default real token | manualDefaultSameSessionObservedConcealedAndJavaTotp passes |
| 4 | SHA256/SHA512 | supportedAlgorithmsDigitsAndCustomPeriodThroughRealAuthoring passes |
| 5 | 6/7/8 digits | Same nine-configuration test passes |
| 6 | Custom period | Real 45-second authoring/reveal test passes |
| 7 | Invalid Base32 | Base32Test + malformedSecretNeverReachesEditorSaveAndWipesInput pass |
| 8 | Empty/malformed secret | Same decoder/controller tests pass |
| 9 | Field bounds/no truncation | javaFieldBoundsNoTruncationOrNormalization passes |
| 10 | Java semantic rejection | Bounds/null algorithm tests and all definite reasons pass; safe messages/source guard |
| 11 | Same live session | Identity and create/open/close counters pass |
| 12 | Ordinary state observation | Content-based observation waits pass; no insertion cache |
| 13 | Concealed added row | Controller snapshots have no revealedCode after add |
| 14 | Java-backed reveal | RFC 287082 at time 59; controller Show and nine configurations pass |
| 15 | Add conceals existing reveal | Real reveal followed by add is concealed; entry wiring guard passes |
| 16 | Busy duplicate | Gated real publication permits one save; rejected chars wiped |
| 17 | Lock/add race | Both admission orders pass; no save after Lock ownership |
| 18 | Activity recreation | No Bundle/state restoration + onStop clear source guards; human rotation smoke PASS |
| 19 | Failed add no invented row | Definite reasons/malformed input/fault tests pass |
| 20 | Failure/uncertainty | Real SPI Failed becomes uncertain; uncertainty before/after persistence + Refresh pass |
| 21 | Duplicate labels | Real same issuer/account creates two distinct IDs |
| 22 | Secret source/logging | Enrollment/M2A guards pass |
| 23 | Debug/test exclusion | Final release APK verifier passes |
| 24 | Existing 149 M2A tests | Retained and passing with 14 new tests (163 total) |

## 14. Validation

Agent final results: **PASS**. Final normal run: BUILD SUCCESSFUL in 42s (90 tasks, 19 executed). Offline clean rerun: BUILD SUCCESSFUL in 1m (92 tasks, all executed). Each final suite: **163 tests, 0 failures, 0 errors, 0 skipped** (149 existing + 14 new). Lint and locked production dependency/APK boundary checks pass. Gradle reports existing deprecation/experimental Android toolchain warnings; no lint baseline or dependency changes.

Exact commands run by agent:

```sh
./gradlew check :app:assembleDebug :app:assembleRelease
./gradlew \
  --offline \
  --no-daemon \
  --no-configuration-cache \
  --no-build-cache \
  --rerun-tasks \
  --dependency-verification=strict \
  clean check :app:assembleDebug :app:assembleRelease
python3 tools/verify-wrapper.py
python3 tools/verify-apk.py app/build/outputs/apk/debug/app-debug.apk --debug-probe
python3 tools/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk --unsigned --no-debug-probe
git diff --check
git status --short
git diff --stat
git diff --cached --stat
```

Wrapper PASS (Gradle 9.8.0 distribution/JAR pins). Debug APK PASS, SHA-256 `d1392c32d16dc063c1420108f0015325a07fc435650942c5a24d1dcfa1aaf548`. Unsigned release APK PASS, SHA-256 `29736d2f69e18f348d794ce54eb2b13b49fc96c17b9fc04fdc9f1a363230b55e`. Python verifier compilation passed. Production enrollment classes are required in APKs; RFC fixture literals, debug seed/probe machinery and tests are excluded from release.

Earlier development checks caught predictive-back lint (fixed with platform callback), an incorrect test assumption that the second duplicate-label token always sorted last (fixed to check distinct IDs), and the M2A await-helper race described above. Final normal and offline runs pass after corrections.

Human Nix: **PASS — human reported** that both `nix flake check path:.` and `nix build path:.` passed. Agent did not run Nix.

Human device: **PASS — human-reported focused M2B smoke on the Pixel 6a non-debug release build**. After installation and the supplied smoke checklist, the user reported “all good from smoke testing.” This covers default SHA1/6/30 enrollment and concealed row/Show/Hide, non-default SHA256/8/45 enrollment and reveal, rotation discarding an unsent secret, Cancel returning safely, and Lock/unlock retaining authored tokens. These are human-reported results, not automated UI/code captures. Initially no device was attached. On the user's subsequent request, agent detected the attached Pixel 6a and installed an update with `adb -s 37311JEGR05916 install --no-incremental -r`, preserving existing app data/disposable vault (no uninstall/data clear). A separate ignored local release copy at `.gradle/m2b-inspection/m2b-release-signed.apk` was signed with the same existing development/test certificate as M2A, SHA-256 `1f14855aface333061eb0bb545251756a214aa02c31607735f003689eecf9005`. APK signature verification and 16 KB alignment check passed; release exclusion verifier passed. Installed APK readback matches SHA-256 `d98b4f6a016482618c98df727d5fca75bf9724baaeba0a2e8e9b3b3b757a0f9e`. Installed package flags exclude DEBUGGABLE. MainActivity was launched. Validated build outputs remain untouched. The update ended the old process; smoke began by unlocking again. No password or dynamic code was captured. The checklist specified a disposable public RFC-style secret and used the installed signed non-debug release, with no Argon2 weakening. No QR/provider smoke.

Remote CI: **not run**. No push or trigger.

## 15. Qualified scope

Manual add only. No QR, otpauth URI, edit, delete, conflict resolution, SAF/provider selection/export, biometric or inactivity-locking claims. Existing dependencies unchanged: core/NIO 0.1.5, BC 1.86, AGP 9.4.0, Gradle 9.8.0. Agent checks, human-reported Nix PASS and human-reported focused physical smoke PASS satisfy M2B completion within this scope. Human results are distinguished from agent-run verification.

## 16. Product architecture verdict

**YES WITH LIMITATIONS.** Token authorship remains entirely behind AndroidVaultController → ForegroundVaultCoordinator → VaultSession. No raw session/state/editor/root/store/path or submitted secret is returned to Activity. The Activity necessarily owns unsent input; JVM/IME erasure cannot be guaranteed. Uncertainty is honestly retained, with explicit observation recovery but no exact-publication retry UI in this milestone. No Java/API design change is required.

## 17. Recommended next milestone

**A. QR / otpauth enrollment**. Manual enrollment establishes a reusable public authorship boundary; QR/URI would need its own parser/design and privacy review.

## 18. Final Git state

`git status --short` (exit 0):

```text
 M app/src/main/java/org/totipo/android/AndroidVaultController.java
 M app/src/main/java/org/totipo/android/MainActivity.java
 M app/src/main/java/org/totipo/android/reconcile/ForegroundVaultCoordinator.java
 M app/src/test/java/org/totipo/android/AndroidVaultControllerTest.java
 M app/src/test/java/org/totipo/android/TotpControllerTest.java
 M app/src/test/java/org/totipo/android/reconcile/ProductControllerFixtures.java
 M tools/verify-apk.py
?? app/src/main/java/org/totipo/android/AddTokenOutcome.java
?? app/src/main/java/org/totipo/android/AddTokenRequest.java
?? app/src/main/java/org/totipo/android/Base32.java
?? app/src/test/java/org/totipo/android/Base32Test.java
?? app/src/test/java/org/totipo/android/EnrollmentSourceGuardTest.java
?? review/M2B_MANUAL_TOKEN_ENROLLMENT_REPORT.md
```

`git diff --stat` (exit 0):

```text
 .../org/totipo/android/AndroidVaultController.java |  44 +++++-
 .../main/java/org/totipo/android/MainActivity.java |  89 ++++++++++++-
 .../reconcile/ForegroundVaultCoordinator.java      |  52 +++++++-
 .../totipo/android/AndroidVaultControllerTest.java | 148 ++++++++++++++++++++-
 .../org/totipo/android/TotpControllerTest.java     |   9 +-
 .../reconcile/ProductControllerFixtures.java       |  14 ++
 tools/verify-apk.py                                |   4 +-
 7 files changed, 344 insertions(+), 16 deletions(-)
```

`git diff --check` (exit 0):

```text
(no output)
```

`git diff --cached --stat` (exit 0):

```text
(no output)
```

Git diff --stat describes tracked changes only; new production classes, tests and this report remain untracked for review. Everything remains unstaged/uncommitted. Nothing staged, committed, tagged, released or pushed; no CI trigger. Ignored locally signed APK copies are device qualification artifacts only.
