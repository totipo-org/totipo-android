# Android Sync status layout report

Status: milestone complete. All automated gates passed; the user reported `nix flake check path:.` passed and focused phone smoke steps 1–5 worked fine on the installed updated build. Optional provider-unavailable phone check 6 was not reported; automated actionable-failure coverage passed. No Nix command has been run by the agent. Everything remains unstaged/uncommitted.

## 1. Starting state

Working directory: `/home/niki/Sources/totipo-android`.

```text
git branch --show-current
main
git rev-parse HEAD
f8371f54b394884d8f2733f1eb71804b73c4a417
git status --short
(empty)
```

The required reveal repaint fix is HEAD (`f8371f5`, Make Android TOTP reveal presentation-local). Daily-driver Sync is committed in `38b251e` (Make Android usable for daily vault synchronization). No commits, staging, pushes, tags, releases, or CI dispatches were performed.

## 2. Pre-fix movement

Baseline normal build passed in 1m48s (90 tasks; 13 executed). Strict offline clean gate passed in 2m49s (92 executed tasks). Baseline: **301 JVM tests, zero failures/errors/skips**. Wrapper and both APK checks passed.

Baseline debug SHA-256: `f66e43289e86cc148f35be1b165a1428e8406865625f528abc16b3811f967f76`.
Baseline unsigned release SHA-256: `b61803c5a71c83ae1a590ec9bd54929dc8ee828af569d89a33a9519e3a9fe853`.

Retained baseline debug APK tested with the deterministic held-provider extension: **PASS, 96 assertions** on Pixel 6a / Android 17, at 320×640dp and 640×360dp for font scales 1.0, 1.5, and 2.0. Search/list top moved by **57px, 81px, and 98px** respectively on entering Sync and returned on success. The toolbar and bottom Add control already stayed fixed; the weighted list absorbed the extra row by shrinking.

Representative native bounds (left:top:right:bottom, parent-relative), 320dp / font scale 1.0:

| View | Before | Held Sync | After success |
| --- | --- | --- | --- |
| Toolbar | 52:52:788:178 | 52:52:788:178 | 52:52:788:178 |
| Search | 52:178:788:296 | 52:235:788:353 | 52:178:788:296 |
| List | 52:296:788:1502 | 52:353:788:1502 | 52:296:788:1502 |
| Add | 52:1502:788:1628 | 52:1502:788:1628 | 52:1502:788:1628 |
| Sync (toolbar-relative) | 274:1:505:127 | 274:1:505:127 | 274:1:505:127 |

First visible index remained 0, row top relative to the list stayed −119px, and its root-relative top moved 177→234→177px. Full before/during/after bounds for all six configurations and both success/failure transitions are in `.gradle/sync-layout/baseline-device.log`. Local evidence is under ignored `.gradle/sync-layout/`. The original baseline device/view regression also passed all **8,018 assertions** using the retained baseline debug APK; evidence: `.gradle/sync-layout/baseline-row-device.log`.

## 3. Exact root cause

`AndroidVaultController.dailySyncStatus()` returns `Syncing…` while unified Sync is active. `MainActivity.mainStatus()` previously appended it to the main status. `render()` therefore changed the status TextView from GONE to VISIBLE on entry and back on successful completion. The new status row changed header height and displaced search and the list top/visible rows, shrinking the weighted list above Add. The toolbar itself is above that row.

## 4. Final transient Sync presentation

Only `MainActivity` production code changes. The existing toolbar text button displays `Syncing…` while the controller's existing daily status is active and returns to `Sync` afterward. Its enabled state still comes from `controller.canSync()`. No controller seam, operation, dependency, spinner, success message, Toast, or dialog was added.

## 5. Stable Sync-control geometry

Before first layout, measure both labels on the actual styled Button with unspecified measure specs, preserving Android's text transformation, padding, and normal minimums. Set its minimum width to the wider measured result, restore `Sync`, and limit it to one line while preserving its existing text transformation. There are no arbitrary pixel reservations. Qualification covers both labels and bounds at 320dp and 640dp with font scales 1.0, 1.5, and 2.0.

## 6. Main-status policy

Filter only the exact transient `Syncing…` value in `mainStatus()`. Preserve existing local operation/error composition and all actionable Sync strings: Changes not synced; Sync folder unavailable; Sync folder needs attention; Sync failed; This sync folder belongs to a different Totipo vault.; Conflict needs attention. Ordinary successful Sync continues to produce empty main status. Existing actionable-status visibility/live-region policy is retained.

## 7. Accessibility

The same Sync control exposes content description `Syncing` while active and `Sync` when complete. Only change text/content description when different. The button uses ACCESSIBILITY_LIVE_REGION_NONE; no explicit announcements or new focusable progress widget. The existing main status remains a polite live region for actionable information.

## 8. Automated layout regression

Extend `tools/device/RowRevealRegression.java` and its existing temporary, self-targeted APK runner. The extension constructs an actual detached MainActivity with a synthetic controller/store and existing SyncFolderBinding.Port seam; the real app is never installed/launched or read. Hold provider probe with CountDownLatch, pump deliveries through a blocking queue, inspect while held, release explicitly, and inspect completion. No sleeps release or simulate Sync in the extension.

Capture toolbar, all toolbar children including Sync and overflow, search, list, Add, first visible list index, and every visible row's bounds. Exercise idle → held Sync → success, then idle → held Sync → provider-unavailable error. The error must remain visible; its intentional layout change is separate from progress. Repeat at portrait-like 320×640dp and landscape-like 640×360dp for three font scales. Existing reveal/row regression remains included.

Final device/view run: **PASS, 8,204 assertions** = 8,018 existing row/reveal assertions + 186 Sync assertions. Pixel 6a / Android 17; final debug APK hash recorded below. All six width/font-scale configurations passed, including full label fit inside the styled Button reservation. Toolbar/search/list/Add/Sync and all visible row bounds plus first visible index remain identical before/held/after success. Main status stays GONE while active; active control is disabled, named Syncing, and has no live region. Repeated rendering remains quiet. Both successful and error completion preserve Sync/toolbar bounds; real provider-unavailable error remains visible.

Representative final bounds, 320dp / font scale 1.0:

| View | Before | Held Sync | After success |
| --- | --- | --- | --- |
| Toolbar | 52:52:788:178 | 52:52:788:178 | 52:52:788:178 |
| Search | 52:178:788:296 | 52:178:788:296 | 52:178:788:296 |
| List | 52:296:788:1502 | 52:296:788:1502 | 52:296:788:1502 |
| Add | 52:1502:788:1628 | 52:1502:788:1628 | 52:1502:788:1628 |
| Sync (toolbar-relative) | 261:1:505:127 | 261:1:505:127 | 261:1:505:127 |

Full captures: `.gradle/sync-layout/final-device.log`. These are actual Android Views in a detached MainActivity; attached-window phone observation remains a separate human gate.

One JVM test adds exact transient/actionable status policy, including preservation of local saving/error information. Device assertions verify control rendering, measured reservation, and quiet accessibility. Existing controller tests continue to cover import-before-publish, conflict, different vault, triggers, coalescing, publication failure/retry, cancellation, and serialization.

## 9. Sync semantic invariance

`AndroidVaultController`, all Sync/provider/reconciliation production code, and token-row/reveal/countdown/adapter implementation are unchanged. The existing Sync click command and `canSync()` admission remain unchanged. The view derives progress from the already-existing daily status. No operation ordering, pending-publication behavior, provider admission, auto trigger, error/retry policy, or different-vault handling is edited.

## 10. Dependency/permission invariance

No build scripts, dependency locks, verification metadata, package-deps.json, flake.lock, package.nix, manifests, permissions, or resources change. Runtime graph remains external Maven NIO 0.2.0 → core 0.2.0 → Bouncy Castle bcprov-jdk18on 1.86, plus strict lock constraints. Both final debug/release runtime graphs were inspected and contain precisely these three Maven modules and strict lock constraints. All packaged manifests, resources.arsc, and res/ entries are byte-identical to the baseline for each APK variant; APK verification also checks the existing permission/component boundaries.

## 11. Final Gradle/APK validation

Commands:

```sh
./gradlew check :app:assembleDebug :app:assembleRelease
./gradlew --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug :app:assembleRelease
python3 tools/verify-wrapper.py
python3 tools/verify-apk.py app/build/outputs/apk/debug/app-debug.apk --debug-probe
python3 tools/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk --unsigned --no-debug-probe
python3 tools/device/check-row-reveal.py
git diff --check
```

Final normal gate: PASS, 2m, 90 tasks (25 executed, 65 up-to-date). Final strict offline clean gate: PASS, 2m11s, all 92 tasks executed. **302 JVM tests; zero failures, errors, or skips.** Existing Sync semantics/call-order tests pass. Wrapper and both APK guards: PASS. Runtime graph checks: PASS. `git diff --check`: PASS.

Final debug SHA-256: `5a6ed2b8e7dd016b2f0a7bd72b0f474d8c52775d4666abe9b3a133c83f3ed9dd`.
Final unsigned release SHA-256: `e03afc028c460dba89e922ee2af90ad77a90a7cc91075c35c72ae41ade55ce9a`.

Final device regression: **PASS, 8,204 assertions**, including all 8,018 existing row/reveal checks and 186 new Sync checks; `.gradle/sync-layout/final-device.log`. Local logs: `.gradle/sync-layout/final-{normal,strict,wrapper,debug-apk,release-apk,runtime-debug,runtime-release,invariance}.log` and `final-tests.json`.

## 12. Human Nix

PASS — user reported the following check passed:

```sh
nix flake check path:.
```

No nix build requested or run.

## 13. Phone smoke

Installed the updated signed release on Pixel 6a `37311JEGR05916` using `adb install --no-incremental -r`, preserving app data, then opened MainActivity. The existing certificate matched; the signed APK passed verification and the pulled installed APK was byte-identical. Installed SHA-256: `84e455473c67986bb759a36ceb51f9b5b31d91b0dfcd1777819663e3e139fef0`. Evidence: `.gradle/sync-layout/phone-install.txt`.

PASS — user reported steps 1–5 worked fine on the updated app:

1. Press Sync / trigger foreground auto-Sync.
2. Watch while Sync is active.
3. Confirm toolbar/search/token list/Add do not move.
4. Confirm the Sync control gives sufficient in-progress feedback.
5. Let Sync finish successfully; no movement.
6. Optional provider-unavailable failure check: not reported by the user. Automated held-Sync failure regression passed and confirmed the real error remains visible.

No full Syncthing E2E requalification requested.

## 14. Final Git state

HEAD remains `f8371f54b394884d8f2733f1eb71804b73c4a417`, branch main. `git diff --cached` is empty; `git diff --check` passes. Final `git status --short`:

```text
 M app/src/main/java/org/totipo/android/MainActivity.java
 M app/src/test/java/org/totipo/android/DailyDriverUxTest.java
 M tools/device/RowRevealRegression.java
 M tools/device/check-row-reveal.py
?? review/ANDROID_SYNC_STATUS_LAYOUT_REPORT.md
```

Only MainActivity production code changed. Controller/Sync/provider/reconciliation and token-row/reveal/countdown sources are byte-unchanged against HEAD. Everything is unstaged/uncommitted. No Nix, staging, commits, pushes, tags, releases, or CI dispatches were performed by the agent. Human Nix and focused phone smoke steps 1–5 passed, as reported by the user. The milestone is complete; optional phone failure check 6 remains unreported and does not block completion.
