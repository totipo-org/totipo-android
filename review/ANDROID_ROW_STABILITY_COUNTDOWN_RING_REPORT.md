# Android row stability and countdown ring report

Status: the scoped row-geometry/countdown-ring milestone is qualified.
Corrected automated gates, human Nix, and all six focused phone smoke checks PASSED.
Human results are user-reported. The separate screen-wide repaint on reveal
remains documented and unresolved at the agreed controller/Sync scope boundary.
Changes remain unstaged/uncommitted.

## 1. Starting HEAD/state

Clean `main`, HEAD `9264e32075631bdfdc422d41a6792cdd0b6cb131`.
Ran `git branch --show-current`, `git rev-parse HEAD`, `git status --short`
before editing. Committed prerequisites: daily-driver UX `38b251e` (plus test fix
`b801eef`), row reveal/copy `9264e32`, Nix/CI convention `f8d5d45`.
Java 0.2.0 / Vault Format v1/r19 remains pinned. All work is in `totipo-android`.
Inspected the actual adapter, row hierarchy, presentation, detached revealed record,
tick scheduling, accessibility, existing tests, device harness and dimension code.
No Nix, stage, commit, tag, release, push or CI dispatch was performed.

## 2. Baseline validation

All baseline gates passed before tracked edits:

```sh
./gradlew check :app:assembleDebug :app:assembleRelease
./gradlew --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug :app:assembleRelease
python3 tools/verify-wrapper.py
python3 tools/verify-apk.py app/build/outputs/apk/debug/app-debug.apk --debug-probe
python3 tools/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk --unsigned --no-debug-probe
python3 tools/device/check-row-reveal.py
git diff --check
```

Normal: 1m37s, 90 tasks (13 executed, 77 up-to-date).
Strict offline clean: 1m59s, 92 tasks executed.
**288 JVM tests in 31 suites; zero failures, errors or skips.**
Original detached harness: **212 assertions** passed on Pixel 6a
`37311JEGR05916`, Android API 37, physical density 420dpi.
An ignored copy of that harness additionally measured baseline heights without
changing tracked files: 266 assertions and the full 27-case measurement matrix.

All 6/7/8-digit baseline cases produced these heights at each of 280/320/640dp:

| Font scale | Concealed px | Revealed px |
| --- | ---: | ---: |
| 1.0 | 148 | 152 |
| 1.5 | 200 | 200 |
| 2.0 | 232 | 232 |

Baseline SHA-256:

| APK | SHA-256 |
| --- | --- |
| Debug | `7874d1e2d28d8f2f9b08afa4689a0a16edd2491cb9f73a7e8203e8b4cae8defe` |
| Unsigned release | `3c674da6366f665fd1916d45c189c6ef9950417159680986f6a28b47a97cfeb2` |

Both baseline runtime dependency graphs: NIO 0.2.0 → core 0.2.0 →
BC `bcprov-jdk18on` 1.86, with existing strict version constraints.
Packaged boundary: zero permissions; six debug/two release Activities;
zero services, receivers or providers. Main launcher and exported otpauth
VIEW/SEND ingress retain their existing registration.

## 3. Root cause of prior UI jump

Human feedback exposed a second cause that the original detached tests missed:
`showCode` publishes BUSY / “Generating code…”. MainActivity previously rendered
that transient operation message in its main status header. On reveal it added a
line (or an entire visible header), then removed it at OPEN, moving search and the
whole list despite equal row heights. A new representative whole-screen View
sequence reproduced the failure at the generation step before the fix.
The correction excludes only this existing transient code-generation label from
main-header presentation. Controller states, scheduling, admission, Sync status,
other BUSY messages and error presentation are unchanged. The helper is shared
with MainActivity so detached tests exercise the actual header text decision.
This is a presentation correction, not a ListView/controller lifecycle change.


The concealed body only had a 56dp minimum. Revealing the stacked 24sp code and
12sp countdown could exceed the concealed identity's height. Centering the body
and measuring ListView rows at wrap-content height consequently moved following
rows. The old regression allowed an 8dp difference instead of requiring equality.
The expanded checks also exposed a one-pixel code-height change at scale 2.0:
fitting from the previously fitted text size introduced floating-point drift
between measurement passes. Preferred font metrics now provide a stable source.

## 4. Stable-row geometry design

Ordinary rows reserve the same vertical allowance in both states: the greater of
56dp or 16dp existing body padding plus preferred 24sp code font spacing plus the
greater of countdown font spacing and the compact 18dp ring. Identity text can
still determine a larger natural height. The allowance is independent of reveal,
code length, fitted code size and countdown seconds. Concealed values remain
`GONE`: no code width is reserved horizontally. Conflict minimum/layout policy
is unchanged. No animation or manual scroll restoration was added.

## 5. Revealed two-line value layout

The existing horizontal row remains: weighted issuer/account region, content-sized
value column, independent 48dp overflow. The value has exactly two lines:
grouped code, then a horizontal countdown-text/gap/ring group. Code retains its
existing monospaced 24sp preference and narrow-width fitting. Identity retains
single-line ellipsis and flexible width. There is no third line or screen-level
reveal area.

## 6. Countdown-width reservation

Countdown uses the existing proportional TextView font, right-aligned inside a
pixel width measured with its Paint. The helper considers the actual period's
bounded decimal prefixes and widest measured digit suffixes, including ` s`.
This finds the widest valid numeric text for the framework decimal glyph advances
without iterating billions of seconds. Measurement work depends on digit count.
The width is cached per bound period. No literal padding spaces, 30-second constant
or monospaced countdown font is used. The value width is the greater of full fitted
code width and the reserved second-line width, plus existing padding. Tick text
cannot change that width. Code fitting always starts from preferred font metrics.

## 7. Arbitrary-period handling

The revealed interval `Duration.between(validFrom, validUntil)` is authoritative
for total period; adapter-supplied remaining seconds are already authoritative
from `TotpPresentation.display()`. No timing model or controller field was added.
Device cases include 30, 45, 60 and 4,294,967,295 seconds (the Java descriptor's
maximum supported period), across 6/7/8 digits, all widths and font scales.
Exhaustive measured-text comparisons for 30/45/60/120 seconds verify the width
matches the widest valid value. Maximum-period text also fits in every grid case.

## 8. Ring implementation

`CountdownRingView` is a small package-private framework View in tracked
`TokenListAdapter.java` with retained anti-aliased Paints
and RectF. Diameter is 18dp; stroke is density-aware 2dp; gap is 4dp. Paint uses the
existing countdown TextView's theme-derived foreground color, with a lower-alpha
track. Drawing respects padding, centers the circle and avoids allocations in
`onDraw`. Invalid/zero periods and negative remaining values safely render empty;
excess remaining values clamp to full. No dependency or new color system.

## 9. Ring timing semantics

The foreground is a clockwise arc starting at twelve o'clock. Sweep is
`360 * clamp(remainingSeconds / periodSeconds)`: full at period start, draining
as remaining seconds fall. Existing whole-second rounding is shared with the
text. There is no independent interpolation, timer, executor, expiry calculation
or retained animation. The existing presentation retires the code at expiry;
there is no automatic next-period reveal. A newly revealed full period renders
full; revealing mid-period renders that period's current fraction.

## 10. Portrait/landscape behavior

The same compact row-local hierarchy is measured at 280/320dp portrait-like
widths and 640dp landscape-like width, at font scales 1.0/1.5/2.0. Full code,
countdown and ring are contained and disjoint from identity/overflow in every
case. A rotation/visual phone smoke is still pending; detached measurements do
not claim to be an Activity rotation test.

## 11. Recycling/filter safety

Every bind explicitly sets/clears code text, countdown text, value visibility,
ring visibility/progress and existing listeners. Bound presentation retirement
immediately clears both text widgets and ring progress/visibility, including
single-owner switching, filtering, deletion and expiry. A recycled concealed
row has zero progress and no visible value; a rebound revealed token receives
its own interval and remaining value. Search retirement policy is unchanged.
Conflict rows remain non-revealable with no code/countdown/ring.

## 12. Copy/expiry invariance

MainActivity/controller/presentation/clipboard production code is unchanged.
The second tap still copies through the existing action and Toast. Existing
deterministic tests assert the same revealed object, scheduled callback and
absolute expiry after Copy; a fraction assertion now uses that same post-copy
remaining value/interval. Expiry still clears clipboard ownership and retires
the value. Detached geometry checks prove tick and expiry/conceal retain height,
neighbor positions and ListView anchor.

## 13. Accessibility

Ring is explicitly accessibility-unimportant, nonfocusable, nonclickable and has
no live region. Countdown remains textual, with its existing accessibility
importance NO and live region NONE; code remains accessible and quiet. Existing
named row actions remain Reveal code / Copy code / Resolve; no visible Tap-to
instruction is added. No per-second announcement or code-bearing action label.
A full TalkBack session is not claimed by the detached tests.

## 14. Automated layout tests

`RowRevealCopyTest` includes two pure JVM ring tests for full/half/near-empty/empty
fractions at actual periods and safe invalid/out-of-range clamping.
Existing copy/presentation tests remain; the Copy timing test additionally
checks fraction from the preserved interval. Source wiring expectations were
updated for the second-line group, and an old blanket prohibition on adapter
loops was narrowed to row binding (bounded digit-width measurement uses loops).

Actual Android geometry assertions use relative bounds, containment and height
equality, not hard-coded device pixel positions. They cover 18→9 and explicit
20→19, 10→9, 2→1 transitions, maximum text fit, value/identity/code/ring/overflow
bounds, code visibility/grouping, accessibility, and conceal/reveal equality.

## 15. Detached device-view regression

Extended `tools/device/RowRevealRegression.java` through the existing
`tools/device/check-row-reveal.py` runner; no second standalone harness.
Initial clean-build qualification: **6,876 assertions passed**, which missed the
transient header. Corrected final clean-build qualification: **7,056 assertions passed**.
The expanded screen sequence checks toolbar/search/list/Add bounds and ListView
anchor through BUSY generation, reveal, tick, Copy presentation and expiry, with
both empty and nonempty Sync status, at all tested widths/font scales. Every font scale also
checks a three-row vertical sequence and a real ListView with a scrolled anchor:
reveal middle row, tick 18→9, conceal; neighboring bounds, first-visible item and
top offset remain unchanged. No manual restoration during transitions.
The temporary self-targeted instrumentation package was removed after each run;
Totipo was not installed/replaced/launched and no vault was touched.

Measured final-design heights (all 6/7/8 digits and all four periods equal):

| Font scale | Widths dp | Concealed px | Revealed px |
| --- | --- | ---: | ---: |
| 1.0 | 280 / 320 / 640 | 163 | 163 |
| 1.5 | 280 / 320 / 640 | 200 | 200 |
| 2.0 | 280 / 320 / 640 | 232 | 232 |

The added allowance at normal font scale fits the preferred two-line value in
both states; large-font row heights remain unchanged from baseline. The ring
stays fixed at 18dp regardless of font scale.

## 16. Dependency/permission invariance

Ten tracked build/lock/verification/Nix/dependency/manifest inputs are hash-compared
against baseline. All unchanged. No dependency, permission or component change.
Java 0.2.0 / r19 pins unchanged. Production edits are confined to TokenListAdapter (including the renderer) and
MainActivity's transient code-generation header presentation. No Sync, provider,
controller, vault lifecycle, biometric, lock timeout or Add/Edit/Delete/Resolve
semantic change. All production Java and JVM test edits are now in existing
tracked files, avoiding omission of an untracked renderer/test by source filtering.
No Nix input or staging workaround is required.

## 17. Final Gradle/APK results

Repeated both required Gradle commands, wrapper/APK verifiers and detached runner
against the corrected clean outputs. All passed, as did `git diff --check`.

- Corrected normal gate: **2m33s**, 90 tasks (22 executed, 68 up-to-date).
- Corrected strict offline clean gate: **2m27s**, all 92 tasks executed.
- **291 JVM tests in 31 suites; zero failures, errors or skips**, in each gate.
  The pure ring tests are now in existing RowRevealCopyTest; the additional
  header-status regression is in existing DailyDriverUxTest.
- Final detached run: **7,056 assertions**, temporary package removed successfully.
- Wrapper pins verified; both APK verifiers passed including release unsigned and
  no-debug-probe checks.
- Corrected debug/release runtime dependency edges match baseline exactly.
- Both packaged manifest dumps are byte-for-byte identical to baseline: zero
  permissions; six debug/two release Activities; zero services/receivers/providers.
- All ten protected input/manifest hashes remain unchanged.
- A separate copy containing only tracked working-tree files compiled production
  and JVM tests offline with strict dependency verification: **38s, 20 tasks**.
  This checks the omitted-source failure without invoking Nix or staging files.

| Corrected final APK | SHA-256 |
| --- | --- |
| Debug | `02e3913f85d15e26b62647697009e949f56faf38085c91775695d8386c0f1129` |
| Unsigned release | `ea243463f887d3b60e5eafb3c369a3cf9f5e97186d69e97d07e82b56e4b47f0b` |

Ignored evidence under `.gradle/row-stability/`: `corrected-normal.log`,
`corrected-offline.log`, `corrected-final-device.log`, corrected runtime dependency
reports, corrected packaged manifest dumps and `corrected-results.json`;
`tracked-source-compile.log` records compilation using tracked files only.
`header-repro-device.log` records the failing pre-correction screen regression;
`header-fix-device.log` records the passing corrected one. Baseline/initial
qualification logs remain available. Existing AAPT2 experimental-option and Gradle
deprecation warnings remain unchanged.

Under the user's existing installation authorization, replaced the first installed
milestone build with the corrected validated release on Pixel 6a
`37311JEGR05916`, using `adb install --no-incremental -r` and preserving app data.
An ignored release copy was signed with the existing local development/test key;
its certificate matches the installed app:
`1f14855aface333061eb0bb545251756a214aa02c31607735f003689eecf9005`.
Corrected signed installed APK SHA-256:
`0e3839dfeb2db5efb6a8059ceee36d6d8f9e6c5cb7e8fa2c59c854c57de9487b`.
Signature, alignment, APK verification and every ZIP entry's payload equality
with the qualified unsigned release passed. Pulling the installed APK confirmed
exact signed bytes. No uninstall, data clear, app launch or vault operation was
performed. Evidence: ignored `.gradle/row-stability-corrected-install/`.
Installation does not substitute for corrected human Nix or phone qualification.

## 18. Human Nix result

PASSED — the user reported “nix flake check passed” for the corrected build.
Requested command: `nix flake check path:.`. This is user-reported evidence;
no command output/log was supplied. Agent has not run Nix.

The initial user run failed because compileDebugJavaWithJavac could not find the
untracked CountdownRingView class in the filtered source. Correction places the
renderer in tracked TokenListAdapter.java and its JVM tests in tracked
RowRevealCopyTest. No staging or Nix input changes. A tracked-files-only copy also
compiled production and JVM tests successfully without Nix (20 tasks, 38s).

## 19. Focused phone smoke

Latest user feedback: “there is still a noticable repaint that goes throughout the
UI on token reveal, but it doesn't jump anymore”. This is user-reported evidence
that the vertical jump is gone. The subsequent user report “1-6 work as expected”
confirms all six focused smoke checks below passed.

Read-only tracing identifies the remaining global visual transition:
`AndroidVaultController.showCode` uses `submit(State.BUSY, "Generating code…", ...)`.
While BUSY/operating, `canAddToken()` and `canSync()` are false. MainActivity.render
applies those values to Add, Sync and the row adapter; row body/overflow controls
are disabled, then enabled when the operation completes. The adapter additionally
calls `notifyDataSetChanged` for every presentation update. These are actual global
control state changes, so a row-only invalidation optimization would not eliminate
the full observed transition.

No further production edits were made for this feedback. The original scope
excludes Main Sync/controller orchestration and requires stopping if a fix needs
changes beyond row layout. Removing the global generation transition requires a
separate decision about code-generation BUSY/admission presentation; keeping
controls active blindly could change command admission (including queued Sync).
No timer, controller, Sync, enabled-state policy or visual masking workaround was
introduced. The remaining repaint is explicitly unresolved as a separate issue;
it does not negate the subsequently reported pass of all six scoped smoke checks.


Initial phone smoke FAILED: user reported the whole screen jumped on tap to reveal. The previous
detached tests checked row/list geometry but missed the transient BUSY header.
The new screen-sequence regression reproduced this failure before the
presentation-only correction and passed afterwards (7,056 total assertions).

PASSED — after the human Nix pass, the user reported “1-6 work as expected”.
This records all six focused checks as user-reported success; no individual logs
or screenshots were supplied. The scoped checks were:

1. Portrait: reveal a middle token in a populated list; no vertical jump.
2. Watch 10 s → 9 s; code, ring and row do not shift.
3. Confirm code above countdown text followed by ring.
4. Tap revealed row; code copies and layout/ring/expiry do not reset.
5. Wait for expiry; conceal without a vertical jump.
6. Rotate to landscape; compact row-local reveal, no vertical jump, ring beside countdown.

No Syncthing qualification is required; Sync is unchanged.

## 20. Final Git state

Final corrective audit: `main`, HEAD remains
`9264e32075631bdfdc422d41a6792cdd0b6cb131`; empty index. Changes remain
unstaged/uncommitted: MainActivity, TokenListAdapter (including CountdownRingView),
DailyDriverUxTest, RowRevealCopyTest (including pure ring tests),
TotpPresentationTest, TotpSourceGuardTest, RowRevealRegression, and this report.
The Python runner is reused unchanged. No Nix run, stage, commit, tag, release,
push or CI dispatch. Corrected human Nix and all six focused phone smoke checks
are user-reported PASSES. The separate screen-wide repaint remains unresolved.
Final audit after recording the human results: branch and HEAD unchanged,
index empty, all eight changed files unstaged/uncommitted, `git diff --check` passes.
