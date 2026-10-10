# Android reveal repaint fix

## 1. Starting HEAD/state

Started on `main`, HEAD `24ffe50a2bbf26fab8a168acd42f42148a1354d4`, clean index/worktree.
Committed prerequisites: Daily Driver UX `38b251e`, row reveal/copy `9264e32`,
row stability/countdown ring `24ffe50`. External Java 0.2.0 / v1/r19 remains unchanged.
Work is confined to totipo-android. No Nix, stage, commit, tag, push, release or CI dispatch.

## 2. Baseline validation

Both requested Gradle gates passed before production edits:

```sh
./gradlew check :app:assembleDebug :app:assembleRelease
./gradlew --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug :app:assembleRelease
```

Normal: 90 actionable tasks; strict clean: 92 executed tasks. Exact baseline JVM count:
291 tests across 31 suites, no failures/errors/skips. Wrapper verification, both APK
verification commands, device runner and `git diff --check` passed. Detached/device:
7,056 assertions. Geometry stayed stable, but baseline tests did not assert enabled states.

Baseline hashes after strict clean:

| APK | SHA-256 |
| --- | --- |
| Debug | `35f70a32f8aecef16208460037329965e48d624674eeabff068cd17f0a6a2a90` |
| Unsigned release | `16ea3f3c4006488afe35453fcb653375b3ef8da2e81bf113676c6b653ae19dbb` |

Production graph verification passed: external NIO/core 0.2.0 and BC 1.86. Manifests
request no permissions; existing release Activities and debug-only probes unchanged.
Baseline strict log: ignored external `/tmp/totipo-baseline-strict.log`.

## 3. Exact pre-fix reveal transition

Before production editing, a latch-controlled `revealScreenSequence` test ran against
unchanged production code and passed its assertions: concealed OPEN with Add admitted;
showCode admitted; while the generator was held, BUSY and exact message
`Generating code…`, Add false, Sync false (unconfigured fixture), same detached token
view; after release, OPEN, Add true, code available. Existing BUSY/admission tests
also passed in the clean baseline. Probe log: `/tmp/totipo-prefix-sequence.log`.

Source trace establishes configured Sync also becomes false: canSync requires OPEN
and !operating. Generic submit sets operating before publishing BUSY. MainActivity
renders every delivery, sets Add/Sync enabled, and passes canAddToken to the adapter.
The adapter disables row bodies/overflow, then reverses this on completion. Every
replace calls notifyDataSetChanged, including worker-completion deliveries.
Search remains enabled. The previous milestone already suppresses this exact BUSY
message in mainStatus, preserving toolbar/search/list bounds and status visibility;
the generation message exists in Snapshot but is not displayed by the existing header.
Result publication resets the message to Vault open or an unavailable message;
submit finally clears operating and delivers again to re-enable controls.

## 4. Root cause

Presentation generation shared exclusive product-operation admission with real vault
work. This changed unrelated control state, even though it did not mutate the vault.
MainActivity additionally replaced/notified the entire adapter for reveal, ticks,
copy and expiry. Copy also changed the global message and delivered a global render.

## 5. Final reveal admission model

showCode validates OPEN, current detached usable token, no pending token change,
no real operating admission, no queued observation read and no existing reveal job.
It owns one presentation epoch and captured session generation/coordinator. It uses
the same one-thread executor with its existing one-slot bounded queue. No executor,
thread pool, coroutine, dependency or core/storage edit was added.

A second reveal while generation is pending is rejected at admission; it cannot
replace ownership accidentally. Once complete, revealing another token retires the
old presentation as before. Unexpectedly slow generation leaves the target concealed.

Observation signals still retire presentation immediately. Their worker reads defer
while reveal runs, preserving the pending slot for a real command. A queued reveal
is removed before generic command or main Sync admission, so it cannot occupy that
command's pending slot. A running reveal completes on the lane before queued work.

## 6. Why global BUSY is no longer used

Reveal never calls submit and never sets operating or changes global state/message.
Failures leave the row concealed without a global banner/spinner/loading label.
Real create/unlock/Add/change/Lock and other existing exclusive operations retain
submit and their normal admission and BUSY/state presentation.

## 7. Session/generation safety

Before generation and before applying its result, the controller checks presentation
epoch, session generation, coordinator identity, OPEN state, current detached token
basis (including revision heads), no observation failure/dirty view, no operating
command, no unified Sync, and no pending token change. Backend generation retains
its existing authoritative session/state/expected-token checks. The code's original
wall/elapsed expiry checks remain in unchanged TotpPresentation.

Lock publishes LOCKING/clears presentation immediately; closure stays serialized
behind a running generator. Shutdown increments session generation as before.
Neither a delayed result nor queued listener delivery can restore a retired code:
deliveries read the current snapshot and check listener attachment at dispatch time.

## 8. Reveal vs Add/Edit/Delete/Resolve

Reveal alone leaves canAddToken unchanged. Add can reserve normal admission and
queue behind running generation; its BUSY publication retires the reveal. Editing,
deletion and resolution reserve the existing pending-change slot and hide the code;
confirmation retains its normal serialized mutation path. Canceling a change does
not resurrect a retired reveal. Conflicted tokens are rejected before generation.
Resolve's conflict/freshness/publication semantics are unchanged.

## 9. Reveal vs Sync

Reveal alone leaves configured canSync true (and unconfigured canSync false).
Manual/automatic main Sync keeps its existing provider admission and serialization.
A Sync admitted during generation sets its existing unifiedSync/providerActive
state, making the pending reveal result ineligible. It executes on the same worker
and provider lane after generation; only a queued, unstarted reveal is removed to
free the bounded slot. Already-running main Sync continues to block reveal as before.

The only added main Sync line retires queued presentation work before existing
admission. Import-before-publish, automatic triggers, provider identity gates,
pending publication, different-vault blocking, cancellation and provider behavior
are untouched. Existing Sync regression tests remain part of the full gate.

## 10. Row-local presentation path

Snapshot's existing detached revealedCode/remainingSeconds fields are reused.
Listener has a small default revealChanged callback for compatible existing
listeners; MainActivity overrides it to update only the adapter presentation.
Normal attachments and real controller/Sync changes still use normal render.

The adapter keeps weak references to bound Row/token pairs. updateRevealPresentation
changes only bound rows for the previous/new full token ID. Off-screen presentation
is retained in the existing detached fields and rendered on normal bind. No vault
state is duplicated. Every bind replaces the Row/token mapping and clears secrets
for a different token. No row measurement/layout/ring algorithm changed.

## 11. Tick/copy/expiry

Unchanged TotpPresentation schedules authoritative ticks and original expiry.
Ticks use revealChanged and update the current owner's countdown/ring only. Expiry
conceals only that owner. Copy keeps the original code, ring and deadline; the
existing brief Activity Toast remains. It changes no global message and normally
sends no delivery. If the timer delivery was delayed, copy can catch up the detached
remaining seconds through the presentation callback without a global/list rebind.

## 12. Adapter invalidation

Presentation updates never call notifyDataSetChanged. Identical token-list/enabled
replacements also skip notification. Structural/filter/token-data or real operation
enabled-state changes retain normal BaseAdapter notification. BaseAdapter/ListView
remain in use. Weak binding references do not retain retired Activities/rows.

## 13. Lock/filter/recycling safety

Deterministic tests hold an already-generated result while Lock, Add, Edit, Delete,
Sync, authoritative replacement or conflict invalidates it. Filter retains the
previous policy: a search during pending generation retires its epoch; filtering
out a shown token clears its bound widgets. Clearing search cannot resurrect retired
output. A rebound row's full token mapping prevents injection into recycled views.
Application-owned controller lifetime and rotation reattachment remain unchanged;
reattachment binds current presentation without another generation or BUSY state.

## 14. Accessibility

Existing Reveal code / Copy code / Resolve actions remain. The action delegate reads
current detached presentation, so a direct row update also changes its action label.
No generating announcement, live region, global code/status, saved code, or loading
control was added. Countdown/ring remain quiet/decorative; FLAG_SECURE is unchanged.

## 15. Automated controller tests

TotpControllerTest extends the existing injectable Backend generator with a latch
held after actual generation. Tests inspect genuinely pending completion, without
timing sleeps to catch BUSY. Coverage includes OPEN/message/Add/Sync stability,
configured/unconfigured Sync, serialized Add and Sync admission, real Add BUSY,
Lock-before-completion with no code/copy, Edit/Delete, authoritative replacement,
conflict, hide/ownership, rotation, copy without delivery, expiry/original deadline,
no crypto on ticks, generation failure, and a queued reveal yielding to real Add.
Three conflict-resolution fixture expectations now assert upfront reveal rejection
instead of admission followed by unavailable output. Resolution checks stay intact.
Source guards require no generation status string in the controller and retain
normal BUSY messages. Shared test idle helpers include presentation work.

## 16. Whole-screen detached/device regression

The original scale/width/code-length/period geometry matrix remains, including
arbitrary uint32 periods and list anchor tests. The screen sequence now asserts
control/overflow enabled state, status, neighboring view identity/bounds, no adapter
notifications and no neighboring text writes/rebinds through presentation events.

An additional isolated real-controller/Android ListView test creates only test data
inside the self-targeted package, holds actual generation completion with a latch,
and checks in-flight and completion state, bound target update, ticks, copy, expiry,
no global render, no notify, no neighbor rebind, and pending filter/recycling safety.
No installed Totipo data is accessed. The test package is removed by the runner.

Initial new-harness attempts timed out during production vault creation before
reveal. The runner now allows 180 s instead of 60 s and setup awaits allow 120 s,
consistent with the existing lifecycle-device harness. Generation uses explicit
latches. An intermediate run passed 8,011 assertions before the extra pending-filter
case. Final result is recorded below.

## 17. Dependency/permission invariance

Production edits are limited to AndroidVaultController, MainActivity and
TokenListAdapter. No Java core/storage module, build script, Gradle lock,
verification metadata, package-deps.json, flake.lock, package.nix, resource or
manifest changed. No permission/component added. No Material/Compose/coroutine.
Runtime graph remains app -> totipo-storage-nio:0.2.0 -> totipo-core:0.2.0 ->
bcprov-jdk18on:1.86, all external Maven artifacts; protocol remains v1/r19.

## 18. Final Gradle/APK validation

Final normal Gradle gate PASSED: 90 actionable tasks (8 executed, 82 up-to-date),
1m 49s. Final strict offline clean gate PASSED: all 92 actionable tasks executed,
2m 37s. Exact JVM total: 301 tests across 31 suites, zero failures/errors/skips;
TotpControllerTest contains 27 tests. Both gates use the exact commands in section 2.
Wrapper verification PASSED. APK verification PASSED with the requested debug-probe
and unsigned/no-debug-probe flags. External Maven graph check PASSED: NIO/core
0.2.0, BC 1.86. APK badging confirms no requested permissions in either variant.
Existing debug-only components remain excluded from release by APK verification.

Final rebuilt APK hashes after strict clean:

| APK | SHA-256 |
| --- | --- |
| Debug | `801e64983d6cc8b015a987ee118a4aeb9409dedae9ab6afeaed186659791b10c` |
| Unsigned release | `b878eae180a76a80976297504db9930fc3dc6d7f8392c43732c24b2bb7b97f6f` |

Normal-build debug hash recorded before strict clean was
`57b29a9de45aec4b5ec32080131061910954229bee591b8b269f19a13d218e9c`;
unsigned release was the same as above. These are validation artifact hashes,
not a claim of reproducibility across incremental vs clean packaging.
Final device rerun PASSED: `ROW_REVEAL_PASS checks=8018`, including real delayed
controller generation, exact target countdown/ring ticks, zero global renders/list
notifications/neighbor rebinds, copy, expiry, pending filtering and recycling.
Test package removal PASSED. git diff --check PASSED.
Logs: `/tmp/totipo-final-normal.log`, `/tmp/totipo-final-strict.log`,
`/tmp/totipo-final-device.log` (external/ignored validation evidence).

### Requested phone installation

At the user's subsequent explicit request, installed the qualified release on
Pixel 6a `37311JEGR05916` using `adb install --no-incremental -r`, preserving app
data. Signed an ignored copy with the existing local development/test key and
verified its certificate matches the installed application:
`1f14855aface333061eb0bb545251756a214aa02c31607735f003689eecf9005`.
Signed/installed APK SHA-256:
`8581a81505447ffe676c0d96d8c1239aa588cd780753f578fc582f1d39539aeb`.
Signature, alignment, release APK verification, all ZIP payload equality with the
qualified unsigned release, installation and pulled installed-byte equality PASSED.
No uninstall, data clear, app launch or vault operation was performed. Evidence:
ignored `.gradle/reveal-repaint-install/`. Installation does not establish the
pending human Nix or focused phone smoke results.

### Test-only observation synchronization correction

A subsequent human run reported 301 tests with one failure in
`addAdmittedDuringGenerationKeepsRealBusyAndRejectsReveal` at line 230. That line
combined the no-stale-code assertion with an immediate three-token count assertion;
the supplied excerpt did not include expected/actual values. Source inspection
identified the invalid count timing assumption: ADDED/worker idle can precede the
asynchronous session observation and its rendered inventory. The existing enrollment
test in this class already waits for this separate observation milestone.

The corrected regression checks no stale reveal immediately after Add completes,
awaits the three-token snapshot, asserts the exact count, and checks no stale reveal
again. Admission, real BUSY, successful save, inventory and stale-code assertions
are retained. No production code or behavior changed for this correction.

During focused validation, `syncAdmissionConcealsWithoutReopeningSession` also
exposed an initial-fixture observation replay race: setup could report OPEN/idle
before the initial controller subscription replay had drained, and that legitimate
signal could retire a reveal started immediately by the test. The fixture now uses
its existing Dispatcher seam and a latch to wait for the first worker observation
read after session creation, then awaits idle. No timing sleep, production guard
change, retry of reveal, or assertion suppression was added.

Focused controller suite: 27 tests PASSED. Direct JUnit method repetitions with the
final fixture: 20 Add cases plus 20 Sync-conceal cases, 40/40 PASSED. An initial
offline attempt stopped before test compilation because this session's cache lacked
the Android Gradle plugin; a normal Gradle run populated the cache with unchanged
strict verification/pins.

Corrected full normal gate PASSED: 301 tests, 31 suites, zero failures/errors/skips;
90 actionable tasks (13 executed, 77 up-to-date), 2m 44s. Corrected strict offline
clean gate PASSED with the exact section-2 command: 301 tests, 31 suites, zero
failures/errors/skips; all 92 tasks executed, 3m 11s. Wrapper and both APK verifiers
PASSED; git diff --check PASSED. Corrected validation logs:
`/tmp/totipo-observation-fix-focused.log`, `/tmp/totipo-observation-fix-normal.log`,
`/tmp/totipo-observation-fix-strict.log`.

Corrected strict-clean artifacts:

| APK | SHA-256 |
| --- | --- |
| Debug | `ca344e2f31eb3d0bc1faf0e79224d8edd449029fd210766357259eb03dd4302d` |
| Unsigned release | `b878eae180a76a80976297504db9930fc3dc6d7f8392c43732c24b2bb7b97f6f` |

All 151 production class files match their pre-correction bytes exactly. The release
APK hash is also unchanged. No reinstall or repeat device/phone qualification is
needed for this test-only correction: prior 8,018 device assertions and phone checks
1–4 apply to unchanged production. Only TotpControllerTest and this report changed
in response to the test-failure feedback. Branch/HEAD remain unchanged and the index
is empty; all changes remain unstaged/uncommitted. Human Nix recheck subsequently
PASSED, as reported by the user; see section 19.

## 19. Human Nix

PASSED after the test-only correction — the user reported “nix flake check passed”.
Requested command: `nix flake check path:.`. This is user-reported evidence;
no command output/log was supplied. The earlier reported pass and subsequent
301-test failure remain recorded in the correction history above.
Agent has not run Nix; no nix build requested.

## 20. Focused phone smoke

PARTIAL HUMAN PASS — the user reported “the controls move when I sync, 1-4 are ok”.
Checks 1–4 below are user-reported PASSED. Check 5 (Sync/Add admission during a
practically observable pending reveal) was not explicitly confirmed; automated
latch-controlled coverage passed. No screenshots or individual smoke logs supplied.

The reported movement during actual Sync is recorded as a separate unresolved
normal-screen UX issue. Read-only source tracing shows dailySyncStatus returns
`Syncing…` during unified Sync, mainStatus includes that text, and render toggles
the status TextView between GONE and VISIBLE when its message is empty/nonempty.
That can change header height and move the controls/list. This identifies a likely
mechanism; it is not a measured reproduction of the user's Sync movement.
No further production edits were made: real Sync status/layout behavior is outside
this reveal-only milestone, whose existing real-operation presentation is retained.

Requested checks:

1. Tap a concealed row: code appears; toolbar/search/Add/Sync/other rows do not repaint/gray/re-enable.
2. Watch several ticks: only code/countdown/ring changes; no screen-wide repaint.
3. Tap revealed row: only Copy Toast; no global repaint.
4. Wait for expiry: row conceals; no global repaint.
5. If practically observable, try Sync/Add during reveal: prior control state stays stable until a real operation is admitted under the policy above.

No Syncthing qualification requested; Sync semantics are unchanged.

## 21. Final Git state

Final audit: `main`, HEAD `24ffe50a2bbf26fab8a168acd42f42148a1354d4` unchanged;
empty index. Nine tracked files modified, all unstaged: the three production files,
AndroidVaultControllerTest, TotpControllerTest, DailyDriverUxTest, RowRevealCopyTest,
RowRevealRegression.java and its Python runner. This report is the sole untracked
file. git diff --check PASSED. No stage/commit/tag/push/release/CI dispatch or Nix.
Human Nix recheck is user-reported PASSED after the test-only fix.
Focused phone checks 1–4 remain user-reported PASSED for unchanged production. Optional in-flight
Sync/Add phone check 5 remains unconfirmed; its automated regression passed.
Movement during actual Sync is a separately reported unresolved UX issue.
All changes remain unstaged/uncommitted; only test synchronization and this report
changed after the smoke feedback. No additional production change was made.
