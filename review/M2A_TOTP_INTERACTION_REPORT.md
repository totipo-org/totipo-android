# M2A — token list and live TOTP interaction

**M2A complete.** Agent validation, human-reported Nix validation, and focused
physical-device smoke passed. Release is restored on the Pixel 6a with the disposable
RFC token retained. All repository changes remain unstaged/uncommitted. The stopped
investigation's baseline results are separate from this completed qualification.

## 1. Starting state

Branch `main`; HEAD `7273cb2ca1c3b8a097fbf1c7de044ef2bd07c392` (committed M1M).
Initial commands were `git branch --show-current`, `git rev-parse HEAD`, and
`git status --short`. The only preexisting change was the allowed untracked draft:
`?? review/M2A_TOTP_INTERACTION_REPORT.md`. That draft is replaced by this report.
No unrelated source/build change existed. No staging, commit, tag, release, push,
Nix execution, dependency/version change, or remote CI trigger occurred.

## 2. Design-review resolution

### Token list

The initial investigation stopped on unbounded permanent widget allocation.
The user's resumed design resolves this with platform `ListView` and `BaseAdapter`
recycling. The complete authoritative detached token list remains intact. There
is no pagination, arbitrary token limit, or truncation. Each row has exactly two
children, regardless of alternative count. No Java change/release was required.

### Clipboard

The resumed design accepts best-effort conditional cleanup after observing both
Totipo's per-copy marker and exact textual code. Inability to establish ownership
causes cleanup to be skipped. Android's comparison and clear operations are not
atomic; concurrent external replacement remains an accepted platform limitation.

## 3. Java TOTP API

Used released `totipo-core` 0.1.5 unchanged:

```java
TotpCode VaultState.generateTotp(TokenAlternative alternative, Instant time);
```

`TotpCode` provides `code`, `validFrom`, and `validUntil` with half-open validity.
It contains no secret/session. `TokenDescriptor` supplies status, issuer, account,
algorithm, digits, and period. `TokenId` is a canonical 256-bit identity represented
as 64 lowercase hex characters. `TokenAlternative` stays inside the coordinator.

The released JAR signatures were inspected using `javap`; source archives and SDK
37 stubs were inspected locally. Inspected core/NIO binary SHA-256 values match
repository dependency verification metadata:

- core: `e99609e59db1d9f52f80c446060e63ce7dde17ad69252fa87d397d0cb1577f81`
- NIO: `9e559ec75fb09af068f876751f32d696c50c40988a16d28419dfa05d1d1c4dae`

Cached desktop usage also invokes this Java method on its worker. No desktop crypto
or TOTP implementation was copied. Android contains no HOTP/TOTP cryptography.

## 4. Token eligibility

`ForegroundVaultCoordinator.generateTotp(TokenId, Instant)` requires OPEN and enters
its existing operation gate as GENERATING_TOTP. It captures `session.state()` and
resolves the ID in that current state. It requires finished observation, no vault
observation diagnostics or recorded integrity problems, an existing token with no
conflict/unresolved references, exactly one alternative, and ACTIVE status.
TOMBSTONED and ambiguous entries refuse without invoking generation. This deliberately
refuses even an otherwise usable token when the overall observed state has diagnostics;
M2A does not attempt diagnostic attribution or conflict resolution.

The production command also supplies its detached row basis; changed descriptor or
head revision IDs refuse as STALE even before a delayed observation callback arrives.
The sole current alternative is passed directly to Java. The result must be valid
for the requested Instant and contain the descriptor's number of decimal digits.
The captured state must still be identical to `session.state()` after generation;
otherwise the result is STALE. No alternative is retained. Unexpected generation
failures return FAILED with no exception text/code; the existing session remains
owned and OPEN unless existing observation/lifecycle handling reports a failure.

## 5. Detached reveal model

`RevealedTotp` contains only token ID, code, valid-from/until instants, and digits.
Its `toString()` is redacted. `TotpResult.toString()` includes status only;
controller `Snapshot.toString()` includes product state/error only.

`Snapshot` adds optional `revealedCode` and remaining seconds beside the existing
unchanged detached `View`. No raw `VaultState`, `VaultSession`, `TokenAlternative`,
secret, editor, root/store/path, or provider identity reaches the Activity. The
code is ephemeral process-scoped presentation, never durable descriptor/protocol data.

## 6. List implementation

`TokenListAdapter` references the current immutable detached list; `getCount()`
returns its full size. `getView()` creates a row only when `convertView == null`,
then rebinds the supplied row otherwise. Each row contains a descriptor TextView
and an explicit Show code button. List widgets are allocated for the viewport
and recycling pool, not once permanently per token. No permanent per-token child
layout, pages, or new dependency is introduced.

Exactly one descriptor shows issuer/account and Active or Needs attention. Multiple
or absent alternatives show a constant-size Needs attention summary. Unusable rows
have disabled Show code actions. Vault-wide diagnostic refusal is also enforced
inside the coordinator even if a row's own descriptive status appears active.

Released `TokenGraph.evaluate` sorts logical identities using unsigned token-ID
bytes before constructing an insertion-ordered map. `VaultState` and the existing
Android projection preserve that order. M2A preserves it without sorting by mutable
issuer/account. Adapter long IDs are positional and not advertised as stable:
compressing 256-bit IDs into stable Android long IDs would introduce collisions.
Command identity remains the full TokenId captured while rebinding the row.

The ListView has a platform empty view reading "No tokens yet". A separate fixed-size
reveal panel contains selected descriptor text, grouped code, seconds, Hide code,
and Copy code. Code grouping preserves all underlying digits (6: 3+3; 7: 4+3; 8: 4+4).
Reveal and vault action buttons use horizontal groups to preserve space on rotation.

## 7. Reveal lifecycle

`showCode(TokenId)` requires OPEN/idle admission, immediately conceals any existing
code, and submits generation through the existing one-worker/one-pending-slot executor.
Opening a vault never reveals automatically. Admission rejects duplicate/busy commands.
No credential, session reopen, or KDF is needed for reveal.

The controller checks its presentation epoch and pending/failed observation signals
before accepting the result. Hide and state signals revoke an in-flight reveal.
Coordinator stale-state checks and controller revocation checks cover state replacement
before publication. Later authoritative signals conceal any already published reveal.

One accepted reveal schedules local countdown work. Hide immediately clears code,
lifetime, timer, and clipboard ownership after scheduling conditional cleanup. The
vault stays open. Expiry clears the same state and stops the timer. No subsequent
period code is generated/revealed without another explicit Show action. Cancelled
callbacks carry a generation check and cannot resurrect or duplicate a newer timer.

## 8. Time semantics

Production wall time is `Instant.now()`; monotonic time is
`SystemClock.elapsedRealtime()`. At accepted reveal, the remaining wall interval is
rounded down to milliseconds and establishes a maximum elapsed deadline. Visibility
requires `validFrom <= now < validUntil` and elapsed time before that deadline.
Forward wall expiry conceals; backward movement cannot extend the original elapsed
lifetime. Movement before validFrom also conceals conservatively.

Countdown derives remaining time from the minimum of absolute wall and monotonic
remaining durations, rounding display seconds up. No decrement-only authority exists.
A single application-main Handler delayed callback runs at a one-second cadence,
or earlier for the final validity boundary. UI publication is marshalled to main.
Reattachment/snapshot reads and Copy also recheck expiry, including when callbacks
were delayed by suspension. There is no WorkManager, service, or per-tick crypto.

Java supports SHA1/SHA256/SHA512, 6–8 digits, and integral periods from 1 through
4294967295 seconds. Java computes the code/interval; Android assumes none of those
parameters and never computes a counter or HMAC. JVM orchestration tests inject only
wall/elapsed sources and scheduling, never modify Java crypto/system time.

## 9. Activity lifecycle

The controller and its sole reveal deadline belong to `TotipoApplication`. Activity
stop detaches a listener without concealment, locking, reauthentication, or timer
restart. Activity start synchronously renders the current snapshot before attaching asynchronous
updates, so returning from background checks expiry before reusing old widgets.
Recreation may render a still-valid controller-held code; expiry during
absence yields concealed state. No code is restored from saved UI state.

Hierarchy saved state is disabled for the content subtree, rows/list, and reveal
panel. Code/descriptor/countdown TextViews explicitly disable saved text and autofill.
Old widgets are cleared on surface replacement/destruction, without clearing the
controller's valid reveal on rotation. No Bundle/preferences/file stores code.

## 10. Refresh/sync/lock interaction

Every authoritative observation signal and detached View replacement conceals.
Refresh/sync admission also conceals immediately. An unchanged-token refresh still
conceals; there is no semantic-equality preservation optimization.

Lock admission immediately publishes LOCKING with no View/reveal, cancels the timer,
and forgets ownership after requesting conditional cleanup. FAILED_CLOSE never
restores a code or descriptor reference. Existing busy admission remains: Lock is
rejected while another command is running; after Lock is admitted, Reveal/Copy cannot
be admitted or publish. A busy reveal can be revoked with Hide. No pending-lock or
inactivity policy is introduced.

## 11. Clipboard

`PlatformCodeClipboard` uses Android `ClipboardManager` only on main. Copy is deliberate,
uses unformatted digits and generic "Totipo code" label, and marks the clip sensitive.
SDK 37 exposes `ClipDescription.EXTRA_IS_SENSITIVE`; API 33+ uses it, earlier platforms
use the documented `android.content.extra.IS_SENSITIVE` key. No minSdk change.
See official [copy/paste guidance](https://developer.android.com/develop/ui/views/touch-and-input/copy-paste)
and [ClipboardManager reference](https://developer.android.com/reference/android/content/ClipboardManager).

Each successful copy gets a UUID marker in description extras. The controller retains
only marker, exact text, and expiry in a redacted ephemeral ownership record. Copy
rechecks wall and elapsed validity before calling the platform adapter. Expired Copy
conceals and does not copy. Copy failure exposes only a generic message; successful
Copy reports "Code copied" without speaking/logging the digits.

Expiry, Hide, Lock, replacement, and another reveal forget ownership and attempt
cleanup. The platform adapter accepts only one plain-text item, matching marker and
exact text, with no URI/Intent/HTML. It never coerces other clipboard data to text.
Unavailable/null clipboard, different marker/text/shape, or runtime denial skips
cleanup. `clearPrimaryClip()` is guarded at API 28; the app's vault runtime already
requires API 30. If background focus restrictions prevent comparison, cleanup is
best effort and the record is discarded; no deletion or retry success is claimed.

**Guarantee:** Totipo only intentionally requests a clear after observing its own
marker and exact copied value. It preserves observed external replacements. Android
provides no atomic compare-and-clear; external replacement between comparison and
clear can race. There is no global clipboard lock, history deletion, restoration,
accessibility service, or unconditional clear.

## 12. Accessibility/security

FLAG_SECURE remains enabled. Concealed rows contain no code text/content description.
Intentionally revealed ordinary text is available to enabled accessibility services,
as normal visible content; no custom secret-announcement machinery is added. Code
and countdown live regions are NONE. Code text is not rewritten on unchanged ticks.
Show code / Hide code / Copy code are explicit button labels.

Production source guards reject logging/printing APIs, Android crypto implementations,
fixture literals, and raw Java ownership in Activity/list/presentation classes. Source
guards run in normal check. No derived dynamic codes are logged, placed in exception
messages, screenshots, or golden fixtures. Fixed RFC expected values appear only in
necessary deterministic test assertions. Immutable Java code strings cannot be wiped;
Android drops its current presentation references rather than claiming memory erasure.

## 13. Tests

Added four JVM suites plus a debug-only real-framework check:

- `TotpCoordinatorTest`: 12 cases; real Java-authored RFC SHA1/256/512 vectors,
  6-digit/custom-period projection, missing token, tombstone, real divergent heads,
  real missing-parent state, state replacement during generation, safe failure,
  closed gate, superseded detached-row refusal before crypto, and deterministic ID order.
- `TotpPresentationTest`: 15 cases; single reveal, replacing/cancelled callbacks,
  Hide, exact expiry/no next reveal, forward/backward wall movement, invalid intervals,
  expired Copy before timer delivery, exact-digit Copy/fresh marker, mismatching
  marker/text, unavailable/complex clipboard, ownership forgetting, absolute countdown,
  and redacted values.
- `TotpControllerTest`: 14 cases; actual worker/session generation and one crypto
  call despite countdown ticks, one-at-a-time/Hide, reattach/original deadline,
  detached expiry, failed-close Lock admission, external clipboard on Lock,
  expired Copy/no next-period generation, unchanged refresh, sync admission,
  authoritative token replacement, busy/revoked generation, replacement before
  publication, safe backend failure, and detached/redacted snapshots.
- `TotpSourceGuardTest`: 5 cases; detached row fields/conflict summary, full count
  and convertView/empty-state wiring and grouping, logging/ownership/crypto boundary,
  secure window/saved-state/accessibility, clipboard marking/conditional shape.
- `DebugTotpFixtureActivity.checkAdapter`: real Android assertions for zero/full
  count (512 detached fixtures), exactly two row children, reuse of the same View
  across every rebind, and production sensitive/ownership clip metadata. This is
  executed and passed on the Pixel 6a. Agent captured only the fixed "Adapter checks
  passed" result label, then removed the temporary hierarchy dump. Framework Views
  were not instantiated using JVM mock Android stubs or a new testing dependency.

The 33 requested scenarios are covered as follows:

| Requested scenarios | Evidence |
| --- | --- |
| 1–4 empty/descriptors/recycling/full list | JVM row/source checks and physical Android adapter assertion pass |
| 5–10 Java generation/secrecy/usable/conflict/unresolved/tombstone | Real coordinator vectors/refusals and controller detached-field checks |
| 11–17 one-at-a-time/Hide/expiry/no-next/no-crypto/forward/backward | Presentation and controller deterministic clocks/counters |
| 18–20 stale/refresh-sync/same-session | Coordinator state-replacement hook and controller integration/counters |
| 21–24 reattach/detached-expiry/Lock/busy race | Controller integration with original deadline and failed-close fixture |
| 25–26 deliberate Copy/sensitive metadata | Fake-copy integration, source guards and physical production clip-metadata assertion pass |
| 27–33 expired Copy/matching/mismatching/unavailable/external/forgetting | Shared production match predicate and fake clipboard orchestration |

Fixtures use public RFC 6238 Appendix B test secrets/expected values only, authored
through Java public editors. No Android test recomputes TOTP crypto. A SHA-512 fixture
length typo initially failed its RFC assertion and was corrected to the RFC's 64-byte
key before qualification; no Java change was needed. Existing 103 JVM tests remain.

## 14. Validation

### Agent

Both required builds passed against the final production sources. No Nix commands
were run by the agent. Exact commands:

```sh
./gradlew check :app:assembleDebug :app:assembleRelease
./gradlew --offline --no-daemon --no-configuration-cache --no-build-cache \
  --rerun-tasks --dependency-verification=strict \
  clean check :app:assembleDebug :app:assembleRelease
python3 tools/verify-wrapper.py
python3 tools/verify-apk.py app/build/outputs/apk/debug/app-debug.apk --debug-probe
python3 tools/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk \
  --unsigned --no-debug-probe
```

Final results:

- Normal build: BUILD SUCCESSFUL in 41s; 90 actionable tasks, 26 executed and
  64 up-to-date.
- Strict offline clean build: BUILD SUCCESSFUL in 58s; 92 actionable tasks,
  all 92 executed. No cache/dependency metadata regeneration.
- 15 JVM suites: **149 tests, zero failures/errors/skips**. This includes 46 new
  M2A cases and the original 103 cases. Source-safety guards ran in check.
- Lint passed: 11 warnings (two existing UseRequiresApi and nine SetTextI18n).
  Existing AAPT2 override/Gradle deprecation warnings remain; no lint errors.
- Gradle 9.8.0 wrapper pin verification passed.
- Debug/release APK guards passed after the strict clean build. Debug SHA-256:
  `3777949b3fb004382d3057ca6c66ba0be236a531e81042ec4a542ead5d7f84d5`.
  Unsigned release SHA-256:
  `faffd30a2fbb2f53ceaf84264d4f9b65f29c3fc0d2b0f201ad36fc761adcbce7`.
  Normal and strict offline builds produced the same unsigned release hash.
- Git whitespace/staging checks passed. Untracked new sources/report were also
  checked explicitly with `git diff --no-index --check /dev/null <file>`.

APK guards require all four new production classes and reject debug fixture helpers,
public smoke-secret literals, and fake/test classes in release. Ordinary debug builds
still include existing approved debug probes; release does not.

### Human Nix

PASS — the user reported both commands succeeded for the implemented M2A worktree:

```sh
nix flake check path:.
nix build path:.
```

This new user-reported pass qualifies the resumed worktree separately from the
earlier stopped-draft baseline run. No Nix commands were executed by the agent and
no cache regeneration was needed.

### Human device

**PASS — human-reported focused M2A smoke on the Pixel 6a release build**, plus an
agent-observed physical Android adapter/clipboard-metadata assertion pass.

Preparation and installation:

- Agent installed the signed M2A release with `adb install --no-incremental -r`
  and verified installed APK readback. At the user's request, temporarily installed
  the prepared debug APK and launched DebugTotpFixtureActivity.
- The user explicitly armed the fixture and unlocked through the normal UI, then
  confirmed the RFC token row appeared. No credential was supplied through ADB.
  Agent restored release, verified readback, and launched MainActivity.
- The user then reported all requested interaction checks were good: Show with
  decreasing countdown, automatic conceal at expiry and no automatic next-period
  reveal; deliberate Copy followed by manual clipboard replacement preserved through
  Hide/Lock; rotation without reauthentication or added lifetime; immediate code
  disappearance on Lock. These are human-reported results, not automated code-value
  captures. No dynamic codes were recorded or logged.
- Agent temporarily reinstalled debug to capture the helper's framework test status.
  An Android update reboot interrupted the first capture. After reconnection and
  device unlock, the fixed "Adapter checks passed" label was observed: zero/full
  adapter count (512 detached fixtures), reuse of the same row View across rebinds,
  two children per row, and production sensitive/ownership clipboard metadata.
  This probe did not unlock the vault, re-arm seeding, or alter any token.
- Agent restored release again (Success), verified APK readback, and launched
  MainActivity. The phone is left on the M2A release build. No uninstall/data clear
  occurred; the disposable vault/token remains available. Package updates end the
  process, so the vault must be unlocked again when returning to the app.

Installed signed release SHA-256:
`2607ae082171e1014f254b9706d8be98382f055f5c5aca30476ade4117319644`.
Separate signed local copies are at `.gradle/m2a-inspection/m2a-debug-signed.apk`
and `.gradle/m2a-inspection/m2a-release-signed.apk`; signature/alignment checks passed.
Both use the existing development/test certificate SHA-256
`1f14855aface333061eb0bb545251756a214aa02c31607735f003689eecf9005`.
Validated build outputs were not signed or modified in place. No performance
profiling or Argon2 weakening occurred.

Fixture source is the public RFC 6238 SHA-1 test secret, authored via Java editors.
The debug helper consumes an explicit one-shot request on the next unlock/create;
it accepts no credential Intent and exposes no production enrollment action. It and
its fixed fixture data are excluded from release. A wrong-password attempt consumes
the request; repeated explicit arming can author another disposable token.

The human interaction pass occurred before the Android update reboot; the framework
probe pass and final release restoration occurred after it. The reboot itself was
not treated as a new authentication-performance qualification.

### Remote CI

not run. No push or CI trigger.

## 15. Qualified scope

Read/use-only token list, Java-backed one-token reveal/lifetime, Hide, and deliberate
Copy. No enrollment/QR/manual-secret UI, edit/delete, conflict resolution, provider
configuration/export, biometric/inactivity policy, or authentication-performance
claim. Debug public-fixture authorship is excluded from release and product flows.
Agent, Nix, human interaction smoke, and physical framework assertions passed.
Release is restored. This qualifies M2A's read/use-only scope with the documented
clipboard, accessibility, and scalability limitations.

## 16. Scalability note

M2A bounds Android row View allocation through ListView recycling and constant-size
rows. It does **not** bound Java's full object scan, graph construction,
`VaultState.tokens()`, or the existing detached controller projection. Those full
lists remain authoritative, complete, and unchanged; broader observation/projection
resource hardening is deferred. No arbitrary protocol/product token limit is added.

## 17. Product architecture verdict

**YES WITH LIMITATIONS.** Java-generated TOTP and ephemeral presentation remain behind
the existing controller/coordinator boundary without exposing token secrets or raw
Java session ownership to Activities. Existing Java 0.1.5 suffices. Limitations are
best-effort/non-atomic clipboard cleanup, ordinary accessibility-service access to
intentionally visible content, and deferred full-observation scalability. Nix passed;
human-reported focused physical interaction checks passed as recorded above.

## 18. Recommended next milestone

**A. manual token enrollment.** M2A qualification is complete. The read/use path now has
bounded UI allocation and detached generation/lifetime semantics; the smallest useful
next product addition is deliberate Java-backed authorship with field validation.
Keep QR parsing, provider configuration, edit/delete, and locking policy separate.
No enrollment implementation is included in M2A.

## 19. Final Git state

Final commands ran successfully (ordinary diff statistics omit untracked new files):

```text
$ git status --short
 M app/src/debug/AndroidManifest.xml
 M app/src/debug/java/org/totipo/android/ApplicationVaultBackend.java
 M app/src/main/java/org/totipo/android/AndroidVaultController.java
 M app/src/main/java/org/totipo/android/MainActivity.java
 M app/src/main/java/org/totipo/android/TotipoApplication.java
 M app/src/main/java/org/totipo/android/reconcile/ForegroundVaultCoordinator.java
 M tools/verify-apk.py
?? app/src/debug/java/org/totipo/android/DebugTotpFixtureActivity.java
?? app/src/debug/java/org/totipo/android/reconcile/DebugTotpFixture.java
?? app/src/main/java/org/totipo/android/PlatformCodeClipboard.java
?? app/src/main/java/org/totipo/android/RevealedTotp.java
?? app/src/main/java/org/totipo/android/TokenListAdapter.java
?? app/src/main/java/org/totipo/android/TotpPresentation.java
?? app/src/test/java/org/totipo/android/TotpControllerTest.java
?? app/src/test/java/org/totipo/android/TotpPresentationTest.java
?? app/src/test/java/org/totipo/android/TotpSourceGuardTest.java
?? app/src/test/java/org/totipo/android/reconcile/TotpCoordinatorTest.java
?? review/M2A_TOTP_INTERACTION_REPORT.md
$ git diff --stat
 app/src/debug/AndroidManifest.xml                  |  2 +
 .../totipo/android/ApplicationVaultBackend.java    |  3 +
 .../org/totipo/android/AndroidVaultController.java | 98 +++++++++++++++++++++-
 .../main/java/org/totipo/android/MainActivity.java | 98 +++++++++++++++-------
 .../java/org/totipo/android/TotipoApplication.java | 11 ++-
 .../reconcile/ForegroundVaultCoordinator.java      | 47 ++++++++++-
 tools/verify-apk.py                                |  8 +-
 7 files changed, 229 insertions(+), 38 deletions(-)
$ git diff --check
(empty; success)
$ git diff --cached --stat
(empty)
```

All tracked edits remain unstaged; all listed new sources/tests/report remain
untracked. Nothing committed, tagged, released, pushed, or staged. Dependencies,
versions, lockfiles, verification metadata, and build configuration are unchanged.
The signed APK copies are ignored local qualification artifacts only.
