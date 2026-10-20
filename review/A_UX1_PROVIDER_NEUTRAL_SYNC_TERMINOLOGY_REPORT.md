# A-UX1 — Provider-neutral synchronization terminology

Status: implementation, host/artifact/runtime gates and focused native device checks
PASS. Final human `nix flake check` PASS (user-reported); all 190 frozen
included/evaluator input hashes independently verified unchanged afterward.

## Starting state and scope

Started on clean `main`, HEAD `eebbf8bcb29caa62c136381345fdf149ccf67aca`.
`git branch --show-current` returned `main`, `git rev-parse HEAD` returned that
hash, and `git status --short` was empty. Work is confined to totipo-android.
Classification before edits: **UI_ONLY, DOCS_ONLY**; expanded to
**TEST_OR_QUALIFICATION_TOOLING** for focused native View harness coverage.
Production changes are string literals only, including controller presentation
literals; no new status, operation, confirmation step or scheduling policy.

Read AGENTS.md, README.md, MULTI_VAULT.md, package.nix, flake.nix, CI,
A-MV5/A-MV6 reports and relevant daily-driver, Sync-layout, SAF and per-vault Sync
evidence, production call sites, JVM tests and device harnesses. Read Java guidance
at exact commit `f0a028676c1801a10b2d8d2650bf9c24357d1fa1`, fetched read-only
into ignored evidence; SHA-256
`338837e3563446f8ec11f7bb47c0e4f3ff5502b7608e397f9b8c4d75372715fd`.
No other repository was edited. Dependencies, protocol, crypto, manifests, storage,
lifecycle ownership, biometric credentials and token-management logic are unchanged.

## Terminology inventory and findings

Audited production main/debug/release source, resources, accessibility descriptions,
transient-message call sites and relevant tests for Syncthing, Dropbox, Google
Drive, Cloud, Remote, Server, Upload/Download, Replication, Sync complete /
Synchronized, Local only and Sync folder. Java `synchronized`, fixture variables,
provider implementation identifiers and protocol terminology are not UI strings.
Resources contain branding/runtime/OTP-enrollment text; synchronization presentation
uses existing Java literals. This milestone retains that convention.

| Surface / call sites | Existing terminology and disposition |
| --- | --- |
| Initial setup / selection, `MainActivity.renderSelection` | Create Local Vault / Join Existing Vault and local-create instructions already accurate; retained. No mandatory external service. |
| Create, `MainActivity.build` | “Create a new independent local-only vault. A Sync folder can be configured after creation.” Accurate; retained. |
| Join setup / password form | Existing-vault checking/authentication retained; introductory instruction clarified to require a folder containing an existing Totipo vault. No empty-folder recovery claim. |
| Selection / management, `TotipoApplication.entrySummary` | Local only, Sync folder configured, Folder access needed, interrupted/recovery summaries already accurate; retained. No inactive controller or provider operation introduced. |
| Folder management, `MainActivity.buildFolderManagement` / `render` | Syncthing-specific instruction removed. Choose/Change/Disconnect now identify the sync folder. Join and Initialize remain separate actions. |
| Binding detail, `AndroidVaultController.updateBinding` | “Connected” could imply a service connection or synchronization; replaced with association-only wording. Read-only, checking and unavailable distinctions retained. |
| Permission recovery, `dailySyncStatus` / `updateBinding` | Access-needed wording aligned with existing selector vocabulary and retains reselection guidance; association is preserved. |
| Initialize | Success now points to the existing Sync action. Already-initialized, partial directory initialization, uncertain, blocked, cancellation and identity messages preserved. |
| Manual / automatic Sync | Compact “Sync”, active “Syncing…” and accessible “Sync” / “Syncing” retained. No unnecessary toolbar/layout change to enforce “Sync now.” |
| Publication | “Local changes published” / “No local changes to publish” already exact and folder-scoped; retained. Main success stays quiet. |
| Import / observation | “Changes imported; refresh requested.” / “No new objects.” retain their precise local meaning. No requestRefresh completion claim. |
| Errors / warnings | Identity mismatch, missing/unverifiable VAULT, read-only folder, provider unavailable/incomplete, integrity, blocked candidates, capacity, incomplete/uncertain writes and cancellation details preserved. “Provider” describes the generic SAF implementation, not a service recommendation. |
| A-MV6 refusal feedback | Independent folderConfigurationNotice presentation and persistence across Sync/cancellation retained; existing failed-choice and failed-disconnect regressions rerun. |
| Disconnect | Existing direct action retained; no confirmation dialog exists to rewrite. Management help now explains retained local vault and folder files. No new flow. |
| Delete confirmation | Historical revisions can remain locally as well as in a sync folder; clarified rather than implying synchronized-only storage. |
| Forget confirmation / interrupted operations | “Sync binding” becomes “sync folder association”; private-data/recovery/app-data-loss warnings unchanged. Interrupted Create/Join/Forget, picker-unavailable and uncertain-write messages remain precise. |
| Accessibility / transient messages | Buttons expose native text; renamed folder actions checked on-device. Existing polite actionable status and quiet Sync accessibility preserved. Toasts concern code copy only; no synchronization notification or success Toast claims peer delivery. |
| Diagnostics | Public Vault ID, configured URI, generic provider status, bound-folder verification and last-operation details retained. Raw user/provider names and paths are not rewritten. |
| Current documentation | README’s mandatory-sounding Syncthing workflow replaced with optional folder exchange. MULTI_VAULT’s general operation model is folder-neutral. Named-provider historical evidence remains explicitly scoped. |

No production UI Dropbox, Google Drive, cloud-account, remote-server, upload/download,
external replication completion, backup guarantee or peer-receipt claim was found.
The single named-provider instruction was in MainActivity’s folder-management help.
Debug-only storage probes retain their explicit non-guarantees about cloud/remote
durability and raw provider-advertised upload/download metadata keys. Debug local
qualification explicitly prohibits independent writers, including named processes;
that is a test-root safety instruction, not a compatibility claim or setup advice.
These qualification diagnostics are excluded from release and remain unchanged.
Legacy registry upgrade text uses “unsynchronized local vaults” as a data-loss warning;
it names no provider and remains unchanged with its existing recovery semantics.

## Exact wording changes

| Before | After |
| --- | --- |
| Choose folder | Choose sync folder |
| Change folder | Change sync folder |
| Disconnect | Disconnect sync folder |
| Connected | Sync folder configured |
| Access needed. Choose folder again. | Folder access needed. Choose sync folder again. |
| Choose its Sync folder, then check the existing vault before entering its password. | Choose a folder containing an existing Totipo vault, then check the vault before entering its password. |
| Sync folder initialized. Publish local changes explicitly. | Sync folder initialized. Use Sync to publish local changes. |
| its Sync binding (within Forget confirmation) | its sync folder association |
| Historical encrypted revisions may remain in synchronized storage. | Historical encrypted revisions may remain in local vault storage and the sync folder. |

Old folder help:

> Choose the shared folder used by Syncthing. Join opens an existing vault on this device. Initialize sets up an empty shared folder for this vault. These setup actions never replace an existing vault.

New folder help:

> Totipo reads and writes encrypted vault data in the selected folder. Join opens an existing vault on this device. Initialize sets up an empty sync folder for this vault; use Sync to publish local changes. These setup actions never replace an existing vault. Disconnect removes the folder association and keeps the local vault and folder files.

Affected screens: Join setup, folder management (also displayed in Join), access
recovery/status, Initialize outcome, Delete and Forget confirmations. Create,
selection summaries, toolbar Sync and publication success vocabulary are preserved.

## Documentation and semantic boundaries

Current README and MULTI_VAULT explain app-private canonical storage, optional Sync
folders, encrypted file exchange through SAF, separate external replication, no
replication/backup guarantee, fully functional local-only vaults and possible permanent
loss when Android application data is cleared. README gives separate Join and
Create/Initialize instructions, configuration/access/publication distinctions and
non-destructive Disconnect behavior. Historical A-MV/M3 reports are untouched;
named test environments in current evidence summaries remain historical evidence,
not compatibility advertisements. No external synchronization product is recommended
or qualified, and no provider compatibility claim is added.

Controller diff changes only three distinct presentation strings across four call sites.
A lexical comparison replacing Java string literals with placeholders proves both
changed production files otherwise byte-identical to HEAD (`string-only-invariance.json`).
All control flow, operation admission, ordering, publication success recognition,
identity validation, SAF grants, retry hints, binding persistence, retirement,
registry, biometric and token-operation behavior is unchanged. Local only still
means no binding; access loss still retains a binding. Local publication never
means external peer delivery. No material semantic change was needed.

## Validation and evidence

All builds use the active pinned Nix/direnv Gradle 9.8.0, JDK 17 and Android SDK.
Ignored logs/receipts live in `.qualification/a-ux1/`.

- Baseline: `gradle check :app:assembleDebug :app:assembleRelease` PASS,
  1m43s, 89 tasks (29 executed / 60 up-to-date), before production/test edits.
- Focused: DailyDriverUxTest, MultiVaultUiBoundaryTest, AndroidVaultControllerTest,
  SyncSourceGuardTest and SyncFolderBindingTest: PASS, 114 tests / 5 suites,
  no failures/errors/skips, 1m20s (`focused.log`, `focused-tests.json`).
- Existing controller tests cover local-only saves, published/no-publication results,
  incorrect-vault refusal and A-MV6 feedback persistence. Expanded checks cover
  configured/access-needed labels and Initialize success instructions. New source
  regression covers local Create, existing Join, folder actions, Disconnect help,
  absence of provider instructions and independent refusal rendering.
- Native harness adds `SYNC_TERMINOLOGY_ONLY=1` to the existing detached View runner.
  It skips unrelated row/Sync campaigns and checks actual management Button text,
  accessibility-node names, focus, touch targets and ellipsis at 320/640dp and font
  scales 1.0/1.5/2.0. It invokes no controller or folder action and reads no owner
  app/vault/folder/credential. Harness compilation PASS (`harness-compile.log`).
  Final device PASS: 176 assertions
  across all six width/font configurations on Pixel 6a / Android 17
  (`device-terminology.log`). All seven production DEX entries in the temporary
  test APK match final strict debug APK bytes (`device-payload.json`). The temporary
  package was removed; owner APK path unchanged. No owner app/vault/folder/credential
  operation or global device-setting change occurred.
- Device-tool host suite: PASS, 29 tests (`tool-tests.log`).
- Final normal: `gradle check :app:assembleDebug :app:assembleRelease` PASS,
  2m54s, 89 tasks (21 executed / 68 up-to-date), `final-normal.log`.
- Strict offline clean: PASS, 3m11s, all 91 tasks executed; **449 tests / 42 suites,
  zero failures/errors/skips** (`strict-clean.log`, `strict-tests.json`).
- Both final APK verifiers: PASS (`apk-debug.log`, `apk-release.log`). Source
  VERSION / VERSION_CODE remain **0.2.0 / 5**.
- Offline release runtime/Maven boundary: PASS in 755ms. Runtime remains only
  NIO/core 0.2.0 and Bouncy Castle 1.86 plus strict constraints. Manifest remains
  biometric-only, no network/storage permission, unchanged components/exports,
  backup and SDK/variant boundaries; both APK policies pass.
- Documentation local link targets (15) and `git diff --check`: PASS.
- First focused device attempt failed before UI assertions: new test Intent had no
  component, causing Activity.attach NullPointerException. Exact output preserved
  in `device-terminology-attempt1.log`. Corrected only the harness Intent to name
  MainActivity, matching the existing detached-Activity pattern; focused rerun PASS.
  tools/device is outside Gradle source and not consumed by Gradle check, so this
  does not invalidate normal/strict/APK passes. The changed tool must be refrozen
  before human Nix; no human Nix was requested or run before the correction.
- Human `nix flake check`: **PASS**, user reported “nix flake check passed”.
  Recorded 2026-10-20T18:59:19.709353+00:00 in `human-nix.json`.
  All 190 frozen included/evaluator hashes independently match after that report.
  No terminal output or duration was supplied; agent did not run Nix.

Final gate commands (normal PASS precedes strict; artifact checks use strict outputs):

```sh
gradle --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug :app:assembleRelease
python3 tools/verify-apk.py app/build/outputs/apk/debug/app-debug.apk --debug-probe
python3 tools/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk --unsigned --no-debug-probe
gradle --offline :app:dependencies --configuration releaseRuntimeClasspath :app:verifyMavenBoundary
SYNC_TERMINOLOGY_ONLY=1 python3 tools/device/check-row-reveal.py
```

Final strict APK SHA-256:

| Artifact | SHA-256 |
| --- | --- |
| Debug | `59c621303aaaa7948db2c0a783ae77f7db48230f8a614b017bd5b6dc76bf8df7` |
| Unsigned release | `395e4de732cfc75bc3e9d00bd9f8e8836f0472fafddebb9d4c4810605bac85c7` |

## Input freeze, source membership and execution counts

Actual package.nix includes app/src (including tests), gradle, tools and its listed
build/metadata files. flake.nix also consumes release tooling/build/manifest inputs;
package/flake/cache evaluation inputs are frozen with these. README.md,
MULTI_VAULT.md and review/ are not selected by either check’s effective inputs.
Report-only edits after qualification do not invalidate Gradle/APK/device/Nix results.
No raw path source or ignored build/cache/secret state is included.

Freeze recorded **2026-10-20 18:45:43 UTC** in `freeze.json` and
`frozen-inputs.json`: 190 included/evaluator input hashes, inventory SHA-256
`bd7357e8219679c10ed9382ea0965ff97d8cb4054bcc16ea50cfd7e98a9769a4`.

- **Production source stable.** String-only diff proved; final normal/strict,
  449 tests, both APK policies and matching native device DEX evidence pass.
- **JVM tests stable.** Final 449 tests / 42 suites pass, no subsequent test edit.
- **Build/package configuration stable.** No changes from starting HEAD; pinned
  runtime, strict dependency and manifest/APK boundaries pass.
- **Qualification harness/tooling stable.** Corrected focused device run passes;
  final tool hash frozen after Intent correction. No further included edits planned.

Report is Git-known through intent-to-add only. `git status --short`,
`git diff --name-only` and `git diff --cached --name-only` are recorded verbatim
in `freeze.json`; the latter is empty and `git diff --cached --quiet` passes.
Only the native harness changed after strict, with the explicit no-Gradle-invalidation
reason above. No normal/strict/artifact pass was invalidated. Final human Nix PASS applies
to the frozen inputs, independently verified unchanged after the user result. Excluded report result updates do not invalidate the frozen inputs.

| Invocation category | Count so far |
| --- | --- |
| Baseline ordinary full Gradle | 1 PASS |
| Focused Gradle test invocations | 1 PASS |
| Focused Gradle compile | 0 |
| Final ordinary full Gradle | 1 PASS |
| Strict offline clean | 1 PASS |
| External APK verifier commands | 2 PASS (one final set) |
| Embedded Gradle APK verifier | 0 |
| Offline runtime/Maven boundary | 1 PASS |
| Harness compile-only | 1 PASS |
| Device tool host tests | 1 PASS (29 tests) |
| Physical focused harness | 2 (setup failure, corrected 176-assertion PASS) |
| Human Nix | 1 PASS (user-reported) |

## Remaining concerns and handoff

No provider-specific operational wording remains in the main UI. Generic technical
provider, identity, import/publication and uncertainty detail is retained where it
supports recovery. “Changes not synced” remains existing actionable status, scoped
by the documented folder model; no new operational priority was introduced. Native
View/node checks do not claim attached-window navigation, actual TalkBack,
external SAF compatibility, replication or backup qualification. No full Syncthing
peer-integration campaign is needed for string-only changes.

Changed files: README.md; MULTI_VAULT.md; MainActivity.java;
AndroidVaultController.java; AndroidVaultControllerTest.java; DailyDriverUxTest.java;
tools/device/RowRevealRegression.java; tools/device/check-row-reveal.py; this report.
Final Git state: `main`, starting HEAD unchanged; nine unstaged changes (report
intent-to-add only), no staged content. Exact status:

```text
 M MULTI_VAULT.md
 M README.md
 M app/src/main/java/org/totipo/android/AndroidVaultController.java
 M app/src/main/java/org/totipo/android/MainActivity.java
 M app/src/test/java/org/totipo/android/AndroidVaultControllerTest.java
 M app/src/test/java/org/totipo/android/DailyDriverUxTest.java
 A review/A_UX1_PROVIDER_NEUTRAL_SYNC_TERMINOLOGY_REPORT.md
 M tools/device/RowRevealRegression.java
 M tools/device/check-row-reveal.py
```

The ` A` report entry is intent-to-add, not staged content. Final artifact hashes
are recorded above. The required final human `nix flake check` passed on this frozen Git-aware
working tree. All required qualification is complete. Only this excluded report
and ignored result receipts changed after freeze; no included input changed.
No further human/device campaign or approval is required for this milestone.

No commit, tag, release, push,
owner-app update or agent Nix command occurred. Changes remain available for review.
