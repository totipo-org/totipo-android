# Android row reveal/copy UX report

Status: implementation, automated qualification, human Nix and focused phone smoke
PASSED. Human results are user-reported for this milestone. All changes remain
unstaged/uncommitted on `main`.

## Starting state and scope

Started on clean `main`, HEAD `b801eefdb50a3f6be81b3648d80327a76624f1ca`.
`git branch --show-current`, `git rev-parse HEAD`, and `git status --short` were
recorded before editing; status was empty. History contains the daily-driver UX
milestone `38b251e` and subsequent test correction `b801eef`.
Inspected actual `TokenListAdapter`, `MainActivity`, `AndroidVaultController`,
`TotpPresentation`, `RevealedTotp`, clipboard and existing tests before editing.
Work is confined to `totipo-android`. Agent has not run Nix, staged, committed,
pushed, tagged, released, or dispatched CI.

## Old interaction and final state machine

Previously, rows showed identity plus Show code/overflow buttons. Show code
populated a separate screen-level panel containing selected identity, code,
countdown, Hide and Copy buttons.

| State/event | Result |
| --- | --- |
| Concealed ordinary row / primary body tap | Existing controller reveal for that full TokenId |
| Revealed same row / primary body tap | Existing controller Copy, brief `Code copied` Toast |
| Copy succeeds | Same revealed value, timer and absolute expiry; no conceal/reveal call |
| Reveal another token | Existing single-owner controller replaces the previous reveal |
| Expiry | Controller retires code and clipboard ownership; bound code clears and row returns to identity-only |
| Overflow | Separate sibling hit target opens existing Edit/Delete menu; no body action |
| Conflict body or Resolve child | Existing Resolve callback; no reveal or copy |

There is no Show code, Tap to reveal, Tap to copy, or Copy button in normal rows.
The separate panel, duplicate identity/code widgets and Hide/Copy panel controls
are deleted. Each token has one visual representation in the list.

## Layout and hit targets

The existing framework `BaseAdapter`/`ListView` architecture remains. Each recycled
row is horizontal: weighted primary body plus an independent 48dp overflow.
Within the body, issuer/account have flexible width and separate single-line
ellipsized TextViews. A content-sized value column shows the full grouped,
monospaced code (24sp preferred) above a secondary 12sp `18 s` countdown.
At narrow widths/large font scales the code fits down while reserving 56dp of
identity width; digits are never ellipsized. Code/countdown omit extra font padding. This arrangement
keeps both code and countdown beside identity without adding a separate row or
screen-level vertical code area. Identity is never displaced below the code.
Concealed rows have a 56dp minimum body height; revealed rows stay compact.
No click listener uses coordinate checks. Overflow is a sibling of the body;
Resolve is a clickable child that consumes its own event. Alternative metadata
is plain text, never independently actionable.

Portrait and landscape use the same row-local layout. The list keeps its existing
weight and receives all space previously occupied by the separate revealed panel.
Layout checks use containment, disjoint regions, full text line width and compact
height assertions, rather than pixel screenshots.

## Accessibility

The body identity description contains issuer/account (or conflict metadata),
without codes or setup material. Its standard click accessibility action is named
`Reveal code`, `Copy code`, or `Resolve` according to state. No action instruction
is rendered as visible text. Code remains accessible as text, with no live region;
unchanged digits are not rewritten on ticks. Countdown is not an accessibility
focus target and is not a live region. Overflow retains `Token actions: Edit or
Delete`. Existing secure window, no saved code/widget text, no autofill and no
secret logging policies remain.

## Lifetime, filtering and recycling

Controller/presentation/clipboard production code is unchanged. The adapter holds
only the existing single detached revealed value and one bound-row reference,
never per-token retained decrypted codes. Bind compares the full TokenId and
requires an ordinary active unambiguous token. Every bind replaces identity,
listeners, visibility, code and countdown, clearing values when not matching.
Replacing/retiring the reveal clears the previously bound code widget immediately.
At filtering, a missing revealed identity invokes existing `hideCode`, including
clipboard cleanup and epoch revocation. Search preserves a still-visible reveal;
search while generation is pending revokes that generation through the same epoch
mechanism. Clearing the query cannot resurrect retired presentation.

Activity surface replacement/destruction clears adapter widgets without changing
the established session/rotation lifetime. Lock supplies a null-code/empty-list
snapshot and clears bound presentation. Existing controller admission and authoritative
observation policies still cover edit, deletion, conflict transitions and in-flight
results. Codes are not added to saved instance state. No timeout or clipboard policy
changed.

## Tests and automated results

All final commands passed:

```sh
./gradlew check :app:assembleDebug :app:assembleRelease
./gradlew --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug :app:assembleRelease
python3 tools/verify-wrapper.py
python3 tools/verify-apk.py app/build/outputs/apk/debug/app-debug.apk --debug-probe
python3 tools/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk --unsigned --no-debug-probe
python3 tools/device/check-row-reveal.py
./gradlew --offline :app:dependencies --configuration releaseRuntimeClasspath
./gradlew --offline :app:dependencies --configuration debugRuntimeClasspath
git diff --check
```

Normal gate: **1m42s**, 90 tasks (21 executed). Strict offline clean gate:
**2m14s**, all 92 tasks executed. **288 JVM tests across 31 suites; zero failures,
errors or skips.** Final physical detached-view run: **212 assertions passed** on
connected Pixel 6a `37311JEGR05916`; temporary test package successfully removed.
All required gates were rerun after the final production layout adjustment. Existing
AAPT2 experimental-option and Gradle deprecation warnings remain unsuppressed.

Ignored local evidence: `.gradle/row-reveal-final-normal.log`,
`.gradle/row-reveal-final-offline.log`, `.gradle/row-reveal-device.log`,
`.gradle/row-reveal-release-runtime.log`, `.gradle/row-reveal-debug-runtime.log`,
`.gradle/row-reveal-{debug,release}-manifest.txt`, and
`.gradle/row-reveal-invariance.json`.

New `RowRevealCopyTest` covers detached identity matching,
conflict exclusions, grouping lengths, row-local/no-panel wiring, body/overflow
routing, filtering retirement, safe accessibility and no visible instructions.
New deterministic presentation and real-controller tests verify that Copy preserves
the same revealed object, scheduled timer and original absolute expiry, with normal
clipboard cleanup at expiry. Existing reveal switching, lock, rotation, replacement,
conflict, authoring and clipboard ownership tests remain in the full suite.

`tools/device/RowRevealRegression.java` and `check-row-reveal.py` exercise actual
Android view creation, binding, accessibility nodes, body/Resolve clicks, overflow
menu callbacks, measured 6/7/8 digit layouts at 280/320/640dp available row widths and font scales
1.0/1.5/2.0,
recycling, filtering, expiry snapshots, conflict and lock presentation. They run
in an isolated self-targeted instrumentation package using the built APK classes.
The temporary test package has no permissions and is removed after execution.
Totipo is not installed/replaced/launched and no vault is read. These detached view tests supplement the human phone
smoke; they do not claim full Activity rotation or a TalkBack session.

Coverage of the requested interaction checks:

| Check | Automated evidence |
| --- | --- |
| 1. No concealed Show code/instructions | Actual row text traversal + source boundary |
| 2. First body tap reveals | Actual body callback + MainActivity reveal wiring + controller generation tests |
| 3. Code in same row | Actual child hierarchy, text and visibility |
| 4. No separate panel | MainActivity source boundary |
| 5. Second tap copies | Actual body callback + current-identity Copy wiring + controller Copy tests |
| 6. Copy keeps reveal/deadline | Deterministic presentation and real-controller object/timer/expiry assertions |
| 7. Expiry conceals | Presentation/controller expiry + actual bound-widget retirement |
| 8–9. Independent overflow/Edit/Delete | Actual overflow click and both menu callbacks; body count unchanged |
| 10. Conflict only resolves | Actual body/Resolve child callbacks; no code/overflow; detached/controller conflict guards |
| 11–12. Full digits/narrow layout | Measured 6/7/8 digits, 280/320dp, disjoint contained regions and full text width |
| 13. Landscape vertical space | 640dp layout + source removal of panel from weighted list screen; human rotation included in the reported focused smoke |
| 14. A/B ownership | Actual rebind/switch checks + controller single-owner tests |
| 15. Filtering/recycling | Actual matching/missing/restored query and recycled-row checks |
| 16. Lock retirement | Actual null-code/empty-list snapshot + real-controller lock/failed-close tests |
| 17. Accessible actions | Actual named ACTION_CLICK nodes, safe identity description and no visible Reveal/Copy instruction |
| 18. Quiet countdown | Actual live-region NONE and accessibility importance NO; unchanged code text guard |

Initial shell-process harness setup failed because the Android runtime was not
initialized as an app; the final self-targeted instrumentation harness replaces it.
Measured checks caught extra code/countdown font padding, which was removed. A test
that incorrectly prohibited the required visible conflict Resolve control was narrowed
to the ordinary Reveal/Copy action labels. No required check was waived.

## APK, dependency and permission invariance

Wrapper and both APK verifiers passed their branding, DEX/dependency, ingress,
permission, fixture exclusion and release unsigned/debug-exclusion checks.

| Final APK | SHA-256 |
| --- | --- |
| Debug | `95df9fbcb97410e44954d89ac4aa3ffcc1bef9d8a1b2eb999fd15bb021ed68b4` |
| Unsigned release | `6dd0be61af70dc2f0c031ba7418d897da91475f8d62afca97933da81b76034ff` |

Both runtime graphs remain NIO **0.2.0 → core 0.2.0 → BC 1.86**, with existing
strict constraints. Maven-boundary verification passed both gates. Packaged binary
manifest inspection confirms **zero permissions**, six debug/two release Activities,
and zero services, receivers or providers. **28 pinned input/manifest/branding files
were byte-compared to starting HEAD and remain unchanged.** `package-deps.json`
SHA-256 remains `8d4ac6dd7fc08cc3793ae46f21cb885cf1300ccef992fe9f399fe6982f89c133`.

Production changes are limited to MainActivity and
the row adapter. The debug fixture changes only for the adapter constructor.
Sync/auto-Sync/provider code, Add/Edit/Delete/Resolve semantics, Java 0.2.0, protocol
r19, branding, dependencies, permissions, manifests, Gradle lock/verification inputs,
`package-deps.json`, `flake.lock`, and `package.nix` are unchanged.

## Installation on connected phone

At the user's explicit request, installed the validated release on Pixel 6a
`37311JEGR05916` using `adb install --no-incremental -r`, preserving app data.
The installed certificate differs from the debug certificate, so a separate ignored
release copy was signed with the existing local development/test key. Certificate
SHA-256 matches the installed app:
`1f14855aface333061eb0bb545251756a214aa02c31607735f003689eecf9005`.
The initial signing attempt supplied the same single-line password file twice and
failed before installation; signing with the standard shared store/key password
handling succeeded. No password contents were printed.

Signed installed APK SHA-256:
`b156e854ac80b868768b9b0fbc23b3df2cc662f80d811d34d092851ba0d1f982`.
Signature, ZIP alignment, APK verification and payload equality with the qualified
unsigned release passed. Pulling the installed APK confirmed exact signed bytes.
No uninstall, data clear, app launch or vault operation was performed. This is an
installation check, not the human phone smoke. Evidence is in ignored
`.gradle/row-reveal-install/`. Subsequent human Nix and focused smoke passes are
recorded below.

## Human Nix result

PASSED — the user reported “nix flake check succeded” after automated qualification
and installation. The requested command was:

```sh
nix flake check path:.
```

This is user-reported evidence; no command output/log was supplied. Agent has not
run Nix.

## Focused phone smoke

PASSED — the user reported “human smoke of the changes - succeded” after
installation. This records the focused milestone smoke as user-reported success;
no individual scenario results or screenshots were supplied. The scope was:

1. Portrait: tap row; code appears in that row.
2. Tap same revealed row; code copies, remains visible and keeps original expiry.
3. Overflow opens Edit/Delete without revealing/copying.
4. Wait for expiry; row conceals normally.
5. Rotate/use landscape; no separate code panel and materially more vertical room.
6. Conflict row only offers resolution behavior.

Complete Syncthing qualification is not requested; Sync was not changed.

## Final Git state

Audited on `main`, HEAD still `b801eefdb50a3f6be81b3648d80327a76624f1ca`.
Index is empty (`git diff --cached --name-only` has no output). All changes remain
unstaged/uncommitted: two production Java files, the debug fixture constructor,
four existing test files, new `RowRevealCopyTest.java`, two standalone view-test
tools, and this report. `git diff --check` passes. Protected inputs/manifests are
unchanged as recorded above. No Nix run, stage, commit, push, tag, release or remote
CI dispatch. Human Nix and focused product phone smoke are recorded as
user-reported passes above, separately from automated detached-row execution.
