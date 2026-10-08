# M1J — Android local vault shell and unlock lifecycle

## 1. Starting state

Started on `main`, HEAD `6a71d7fa777d6a1c46c32f0130f9a4fc54ed89c8` (committed M1I).
Ran the required branch, HEAD and status commands before editing; the tree was clean.
No workspace AGENTS.md was found. No Java/spec/protocol, dependency/version, Gradle
lock/verification metadata or Nix input changes were made. No Nix command was executed.

Inspected MainActivity, manifest/build/application configuration, LocalReplicaOwner,
CoordinatedPrivateStore, ForegroundVaultCoordinator (view/open/password change/close),
M1I report/tests/source guards and released Java 0.1.5 Totipo/OpenResult/CreateVaultResult,
VaultState/TokenDescriptor/SPI and ownership implementation. The locally retained
release source checkout under `.gradle/m1g-inspection` is inspection-only; Gradle still
uses the released Maven artifacts. Local non-normative spec design guidance under
`.gradle/m1-inspection/totipo-spec-91…/docs/design/DESIGN.md`, especially create/unlock,
empty-password confirmation, Android presentation and locking, was consulted alongside
the retained desktop guidance. The requested milestone explicitly defers the design's
inactivity/biometric policy.

## 2. Product scope

Implemented app-private local discovery, Create Vault with confirmation, unlock,
detached open-vault presentation, explicit local Refresh and explicit Lock. Standard
platform Activity/widgets remain the only UI framework. No product provider setup,
Sync button, export, journal, transport VAULT adoption, background work, token editor,
code generation/reveal/copy or notification integration was added.

## 3. Application ownership

`TotipoApplication → AndroidVaultController → ForegroundVaultCoordinator →
CoordinatedPrivateStore → NioTotipoStore.openPrivate`, under the LocalReplicaOwner lease.
MainActivity sees only controller commands and immutable Snapshot/View values. It never
receives a session, store, root path/key or Java state editor. The Application constructs
one controller for its process. This is a lifecycle owner, not a dependency container.

One explicit ThreadPoolExecutor has one worker and an ArrayBlockingQueue of capacity 1.
Product admission rejects a second operation while one is pending/running. Detached
observation reads are coalesced into at most one queued task. No interruption/cancellation
of KDF or filesystem mutation is attempted. The application worker survives Lock for
later unlock; the closed session/store/domain/lease do not. Java's own asynchronous
observation remains inside the M1I coordinated domain.

Handler/Looper dispatch returns listener delivery to the main thread. Framework thread
assertions check dispatch and worker paths. Package-private operations/dispatcher
boundaries support JVM instrumentation without a public injection API. Test fault
implementations and fixtures live only in src/test and are excluded from APKs.

## 4. Lifecycle state machine

Exact states and principal transitions:

| State | Transition |
| --- | --- |
| STARTING | Bounded discovery → NO_LOCAL_VAULT / LOCKED / ERROR_LOCKED / FAILED_CLOSE |
| NO_LOCAL_VAULT | Create admission → CREATING |
| LOCKED | Unlock admission → UNLOCKING |
| CREATING | Affirmed Java creation/observation → OPEN; failure → rediscovered local state plus typed error; closure failure → FAILED_CLOSE |
| UNLOCKING | Authenticated Java open/observation → OPEN; failure → rediscovered local state plus typed error; closure failure → FAILED_CLOSE |
| OPEN | Refresh/sync admission → BUSY; explicit Lock → LOCKING; terminated observation → ERROR_OPEN |
| BUSY | Same-session refresh/sync → OPEN, or ERROR_OPEN; duplicate product commands rejected |
| ERROR_OPEN | Ownership retained; explicit Lock → LOCKING |
| ERROR_LOCKED | No unlocked ownership; Retry storage check → STARTING |
| LOCKING | Subscription/session/domain/lease close, then rediscovery → LOCKED (normally), NO_LOCAL_VAULT or ERROR_LOCKED; failure → FAILED_CLOSE |
| FAILED_CLOSE | Ownership retained; Retry Lock → LOCKING |

State is the primary lifecycle discriminator. Snapshot contains State, typed Error,
safe status text and an optional detached View. Command return values explicitly report
rejected admission; disabled widgets prevent repeated UI submissions. Error categories
are NONE, AUTHENTICATION_FAILED, LOCAL_VAULT_ABSENT, LOCAL_STORAGE_UNAVAILABLE,
LOCAL_STORAGE_UNSAFE, OPEN_FAILED, CREATE_FAILED, OBSERVATION_DIAGNOSTICS,
CLOSE_FAILED and BUSY.

## 5. Local vault discovery

Coordinator discovery acquires the ordinary local owner lease, opens the coordinated
private domain and calls `observeVault()` under its gate. That delegates to SPI
`readVault(87)`, the released v1 VAULT representation length. It observes only bounded
presence/type/length; Android does not parse/encode/authenticate the representation.
No product `Files.exists(root.resolve("vault"))` authority is used.

Absent → NO_LOCAL_VAULT; exact-size Present → LOCKED. WrongKind, Undersized and
Oversized → LOCAL_STORAGE_UNSAFE. Unavailable is an explicit storage error; its
UNSAFE_NAMESPACE subtype remains unsafe. Format/authentication validation of exact-size
bytes still belongs to Totipo.open. A malformed record is never reinterpreted as absence.
Discovery closes its domain before releasing the lease. Failed discovery closure returns
the owned coordinator to the controller for explicit retry and publishes FAILED_CLOSE.

## 6. Creation

`ForegroundVaultCoordinator.create(owner, char[])` calls actual released
`Totipo.create(store.transferSessionView(), credential)`. Java receives the one coordinated
session facade, with one private NIO delegate and lease, just as for open. Created.session
becomes the existing coordinator's session; no second store/root owner or raw NIO path
is introduced. Initial Java observation is awaited on the worker before OPEN.

Both Totipo entry points own the facade on every outcome, including failure. AlreadyExists,
Failed, Uncertain and exceptions are not successful creation. Failure closes owned resources
and re-observes local presence: uncertain creation may have installed VAULT, so it can lead
to a locked existing vault, never an automatic open or a guessed absent state. Create form
has password, confirmation and mismatch error. Empty credentials remain protocol-valid,
with explicit warning/confirmation before submission, as required by local design guidance.

## 7. Unlock

Actual `Totipo.open` is used on the coordinated facade. AuthenticationFailed produces safe
password failure text and a locked vault; no session survives a failed authentication.
Absent, Unavailable, InvalidVault and other open failure/observation failure have separate
product error categories. Failed attempts deterministically close the owned coordinator
before rediscovery. Close failure instead keeps ownership and FAILED_CLOSE. Repeated
unlock while open or busy is rejected and cannot create another session/store.

## 8. Credential handling

EditText input is copied late, immediately before command submission, into an operation-owned
mutable char array. Ownership transfers exclusively to the controller; rejected buffers and
worker buffers are cleared in finally. Coordinator create/open also clear their input.
No credential is a controller field or saved in Bundle/preferences/files/logs/errors/report.
Password widgets disable saved state and autofill. Successful create/unlock replaces the
form and clears its fields; Lock and Activity destruction clear password widgets as well.
This is best-effort buffer clearing, not a JVM/Android memory-erasure guarantee. No credentials
are cached to preserve configuration changes or to sync.

## 9. Open-vault presentation

Totipo title, local-open status, token count and issuer/account/status descriptors come solely
from detached coordinator View. Conflict and unresolved counts are shown. Diagnostic and
retained integrity-warning counts have a visible needs-attention banner; filenames/provider
IDs/internal exceptions are not displayed. The UI states whether the latest observation is
finished or in progress; this describes a snapshot, not refresh request completion.

No view projection expansion was needed. The existing projection provides no ready-to-display
TOTP with a defined lifetime; rotating codes, timers, reveal/copy and editing are deferred.
FLAG_SECURE protects sensitive windows/task previews using ordinary Android behavior.

## 10. Refresh

Controller Refresh dispatches coordinator `requestRefresh()` on the worker without credentials,
close or reopen. It publishes “Refresh requested” and later detached observations. Java 0.1.5
has no public request generation/completion correlation. A replayed Finished is never used as
proof that this request finished. Locked/busy refresh is rejected. Unexpected refresh failure
retains ownership in ERROR_OPEN, with explicit Lock available.

## 11. Sync-ready integration

Controller `sync(ProviderSnapshot.Scan)` delegates to M1I same-session credential-free sync.
Validation remains in Java, exclusive bridge materialization remains under the Android gate,
and that gate is released before requesting Java refresh. No close/reopen or password is on
this path. The controller's later subscription update carries imported detached tokens to UI
listeners. No configured production scan source exists, so MainActivity has no Sync button and
does not adopt debug provider state as product configuration.

## 12. Explicit lock

Admission moves to LOCKING, gates new commands and clears published detached sensitive state.
The worker cancels the controller's subscription, closes the live session, finalizes transferred
facade/staged cleanup after Java relinquishes ownership, closes the backing coordinated domain,
and only then releases the lease. Java's cleanup is best effort; Android retries domain-owned
staged cleanup before backing-domain close rather than assuming it succeeded. A successful
close is followed by bounded local rediscovery, normally yielding LOCKED.

Any session/staged/domain close failure publishes FAILED_CLOSE/CLOSE_FAILED, retains the
coordinator and lease, and offers Retry Lock. No failed close claims LOCKED or releases an
assumed close. Retry can finish domain closure without closing an already closed session again.

## 13. Activity/configuration lifecycle

MainActivity attaches a listener in onStart and detaches in onStop. It does not close on stop
or destruction. A new Activity obtains the same Application controller and receives the current
snapshot; no Totipo.open or credential is involved. Multiple UI observers are deliberately
supported and receive detached values. Already-posted deliveries check current listener membership
and deliver current state, avoiding stale observer ownership. Java Flow subscription remains
inside coordinator/controller, with only change signals crossing that internal boundary.

JVM tests detach/reattach observers, verify OPEN throughout, verify identical coordinator/session,
and verify no extra open/create/close. Physical rotation/widget behavior remains a required human
smoke rather than a claim established by JVM tests. Fresh-process analogue tests instantiate a new
controller after closure and discover LOCKED without automatic authentication. Actual process death
naturally destroys the in-memory session; no OS teardown cleanup guarantee is claimed.

## 14. Background/inactivity policy

Only explicit unlock, explicit Lock and process-death lock are implemented. Backgrounding,
onStop and rotation do not lock. Inactivity/background timeout and biometrics are intentionally
deferred; no timer, service or WorkManager policy was invented.

## 15. Tests

Controller tests use real released Java 0.1.5 and private NIO with a queued main-dispatcher seam.
Coverage includes fresh discovery with no leaked owner, real create, fresh locked controller,
wrong/correct password and wiped buffers, repeated unlock, deterministic Lock, session-close and
backing-domain-close retry, discovery-close retry, observer detach/reattach and multiple observers,
busy admission, worker/main dispatch, empty credentials, already-existing and uncertain creation,
unsafe type/size, exact-size invalid format, unavailable SPI and unsafe namespace, refresh open/locked,
terminal subscription handling, immutable detached state and credential-free same-session sync.

The product sync regression reuses the M1I real encrypted fixture, imports three TOKEN candidates,
checks later detached controller/listener content, and checks identical coordinator/session plus
one open and zero close/create during sync. Source guards retain provider read-only behavior,
no Android crypto/parser/direct filesystem publication, store gate ordering, no callbacks into Java
under exclusive bridge gate, and no credential/reopen on controller sync. Existing M1I tests also
cover missing parents, partial evidence, integrity retention and replayed Finished refresh semantics.

## 16. Validation

### Agent

**PASS.** Final ordinary build/check, including the debug timing follow-up, passed (28 seconds); strict offline/no-daemon,
no-configuration-cache/no-build-cache, rerun-tasks, strict-verification clean build/check
passed (47 seconds, 92 tasks executed). All **97 tests** passed, including **16 controller
tests**, with zero failures/errors/skips. Lint: zero errors, five warnings (two TargetApi
versus RequiresApi suggestions at the Flow boundary and three existing debug-probe
hardcoded-string warnings). No AndroidX dependency was added to address those suggestions.
Wrapper pins and both APK inspections passed; debug inspection with --debug-probe also
requires the timing probe, and release inspection excludes its class and log tag. Source guards run through ordinary check.
The final unsigned release SHA-256 matches the ordinary and forced offline builds.

| Final APK | SHA-256 |
| --- | --- |
| Debug | `811f85c57fce75d9c58c685ebf4047296f46048f8b55ca53482c8d3305c5d1e9` |
| Unsigned release | `57df2002579747a0b0c6dbcdaf8f5d23665aafbfb6d49d751d01615b5b422549` |

APK guards require MainActivity, TotipoApplication, AndroidVaultController, coordinated
store/coordinator, core/NIO and BC; they exclude debug machinery from release and test/
fault fixtures/historical scaffolding from both APKs. Maven guards require Totipo core/NIO
0.1.5 and BC 1.86. Release remains unsigned. Final diff check and cached diff stat are empty.

Required commands were run by the agent, without Nix:

```sh
./gradlew check :app:assembleDebug :app:assembleRelease
./gradlew --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug :app:assembleRelease
python3 tools/verify-wrapper.py
python3 tools/verify-apk.py app/build/outputs/apk/debug/app-debug.apk
python3 tools/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk --unsigned --no-debug-probe
git diff --check
git status --short
git diff --stat
git diff --cached --stat
```

### Human Nix

**PASS — human reported both commands succeeded for the initial M1J implementation,
and subsequently reported success again after the debug timing follow-up.** Neither
command was run by the agent. No dependency cache regeneration was needed:

```sh
nix flake check path:.
nix build path:.
```

### Human device smoke

**PASS — human confirmed the required lifecycle smoke on a Pixel 6a, including fresh-data Create Vault → open.** Human reports vault opening takes approximately
12 seconds on a Pixel 6a, including a repeated Lock → Unlock attempt. The UI remains responsive
throughout, as reported by the human. The human subsequently reported exercising Refresh,
rotation and wrong-password behavior with no failures reported. Device is a Pixel 6a;
Fresh-data creation and opening were subsequently explicitly confirmed as passed. Android/API
version was not reported. Record this as an unresolved performance
finding rather than a full device pass.

Source inspection confirms normal unlock invokes Totipo.open once, then awaits initial local
observation before publishing OPEN. Released Java's BC Argon2id uses 65,536 KiB (64 MiB),
three iterations and four lanes. Password derivation is a likely contributor, but no device
phase timings have been measured; the reported duration cannot yet be attributed entirely
to the KDF rather than initial observation or runtime warmup. No Java/protocol/KDF change was
made in response to this observation.

A subsequent debug-only phase probe times storage open, the released Java create/open call,
initial observation wait and coordinator total on the actual application path. It logs only
fixed operation/phase labels and elapsed milliseconds under `TotipoVaultTiming`; no credentials,
paths, token/provider IDs, descriptors or exception text are logged. Release selects the ordinary
backend and APK verification explicitly excludes the timing class/tag. Java-call duration includes
KDF and other Java work; initial-observation duration measures the remaining wait, since Java can
start observation before its open call returns. Human supplied two unlock timing sequences:

```text
unlock phase=storage_open elapsed_ms=0
unlock phase=java_call elapsed_ms=20072
unlock phase=coordinator_total elapsed_ms=20073
unlock phase=storage_open elapsed_ms=0
unlock phase=java_call elapsed_ms=20072
unlock phase=coordinator_total elapsed_ms=20074
```

These attempts spend approximately 20 seconds in the released Totipo.open boundary.
Neither sequence includes initial_observation_wait; the instrumented coordinator emits
that phase whenever successful open reaches observation, even if that observation throws.
Thus these sequences indicate unsuccessful open (a non-Opened result or an exception),
not a completed successful-open scan. The human confirmed these two attempts used the wrong password.
The Java boundary includes bounded VAULT reading, KDF and authentication; these timings
localize the delay to that boundary but do not isolate KDF alone. The earlier approximate
12-second successful-opening report remains separate human evidence, not a measured
successful-open phase result.

The human then supplied a successful correct-password attempt:

```text
unlock phase=storage_open elapsed_ms=0
unlock phase=java_call elapsed_ms=20305
unlock phase=initial_observation_wait elapsed_ms=4
unlock phase=coordinator_total elapsed_ms=20311
```

Successful unlock spends 20.305 seconds in Totipo.open, with only 4 ms of remaining
initial-observation wait and a 20.311-second coordinator total. Wrong-password attempts
also spend about 20 seconds inside that same Java boundary. Source inspection shows a
single Argon2id derivation on both authentication paths (64 MiB, three iterations,
four lanes); this is the leading explanation, though internal KDF-only time has not
been directly profiled. The Android controller introduces neither a duplicate open nor
a long initial-observation wait in this sample. UI responsiveness, explicit Lock followed
by repeated unlock, wrong-password failure and correct-password open now have partial
human device evidence. The human subsequently reported Refresh, rotation and wrong-password smoke as exercised,
with no failures reported. Fresh-data Create Vault → open was subsequently explicitly confirmed as passed. Android/API
version was not reported.

This is a significant unresolved product latency limitation. Address it with released
Java/BC-on-Android profiling and implementation optimization that preserves protocol KDF
parameters, as a separate scoped effort. No Java, BC version, KDF parameters or protocol
were changed in M1J. Do not lower derivation parameters or cache passwords as a shell fix.

Install the updated debug APK without resetting the existing disposable test vault, then watch:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n org.totipo.android/org.totipo.android.MainActivity
adb logcat -v brief -s TotipoVaultTiming:I '*:S'
```

Use Lock → Unlock while logcat is running and report the four `unlock` timing lines.

On an Android 11+ physical device, use a disposable
test vault/credential. Commands derive from the final applicationId and exported manifest Activity:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n org.totipo.android/org.totipo.android.MainActivity
```

For fresh-data creation smoke, **the following destructive command deletes the app's local test
vault and all app data. Use it only for disposable test data:**

```sh
adb shell pm clear org.totipo.android
adb shell am start -W -n org.totipo.android/org.totipo.android.MainActivity
```

1. Fresh data shows Create Vault with password/confirmation.
2. Create a disposable local vault; the open screen appears without a crash.
3. Confirm responsiveness during create/unlock; visible busy status and disabled submissions.
4. Rotate/recreate Activity if supported; it remains unlocked without a password prompt.
5. Refresh shows a request, preserves the unlocked session and needs no password.
6. Lock shows the unlock form.
7. Wrong password remains locked with a safe authentication error.
8. Correct password opens the existing vault.

No provider/sync smoke is required; product provider configuration is absent. No Git/Gradle
commands are delegated to the human. Human Nix and lifecycle smoke outcomes are recorded above. Device: Pixel 6a; Android/API
version was not reported. Fresh-data create/open, responsive create/unlock, explicit Lock,
wrong/correct password, Refresh and rotation smoke are recorded as passed from the human
confirmations in this session; no separate device instrumentation suite is claimed.

### Remote CI

**not run**. No push or CI trigger was performed.

## 17. Qualified scope

Implementation, agent validation (including debug timing follow-up) and repeated human Nix
validation passed. Human Refresh/rotation/wrong-password smoke and responsive correct unlock
are recorded. The human explicitly confirmed fresh-data Create Vault → open passed; required lifecycle
smoke is complete. **M1J completion criteria are satisfied**, with the authentication latency
qualified below. Measured successful
unlock takes approximately 20 seconds inside released Java and remains a performance limitation. API 30+ vault runtime support is gated
explicitly; minSdk 26 is not a vault-runtime qualification. No completed synchronization, provider
configuration/export, VAULT transport reconciliation or full authenticator UX is claimed.

## 18. Product architecture findings

**YES WITH LIMITATIONS.** Application → VaultController → ForegroundVaultCoordinator is suitable
for remaining Android product UX without exposing Java/store ownership to Activities. One process
owns the lifecycle, storage lease and serial dispatch; presentation attaches to immutable snapshots.
No stop condition required a design workaround. Remaining limitations are API 30+ Flow support,
uncorrelated asynchronous refresh, deliberately deferred lock/biometric policy, and future token/TOTP
commands needing equally deliberate detached lifetime semantics. Human reports exercising physical Refresh, rotation and wrong-password behavior, with no
failures reported; fresh-data creation/open was subsequently explicitly confirmed as passed. The Pixel 6a's measured successful authentication takes
about 20 seconds inside released Java, with a 4 ms remaining observation wait; this is an
unresolved practical product-performance limitation requiring a separately scoped Java/BC
profiling effort.

## 19. Recommended next milestone

**A. token/TOTP interaction UI**, after classifying the measured unlock latency. The local
lifecycle now supports a useful local-first next slice:
bounded enrollment/interaction commands and detached, lifetime-defined TOTP presentation through
this controller boundary. Keep provider configuration, export/journal and VAULT transport as separate
milestones. Do not add code generation directly to Activities or bypass session ownership.

## 20. Final Git state

Ran `git status --short`, `git diff --stat`, `git diff --check` and
`git diff --cached --stat` after agent validation. Diff check passed; cached diff stat
was empty. Tracked diff stat: 8 files, 290 insertions, 55 deletions (untracked files are
not included by git diff --stat). Final status:

```text
 M README.md
 M app/src/main/AndroidManifest.xml
 M app/src/main/java/org/totipo/android/MainActivity.java
 M app/src/main/java/org/totipo/android/reconcile/CoordinatedPrivateStore.java
 M app/src/main/java/org/totipo/android/reconcile/ForegroundVaultCoordinator.java
 M app/src/main/res/values/strings.xml
 M app/src/test/java/org/totipo/android/reconcile/ForegroundVaultCoordinatorTest.java
 M tools/verify-apk.py
?? app/src/debug/java/org/totipo/android/ApplicationVaultBackend.java
?? app/src/debug/java/org/totipo/android/reconcile/
?? app/src/main/java/org/totipo/android/AndroidVaultController.java
?? app/src/main/java/org/totipo/android/TotipoApplication.java
?? app/src/release/
?? app/src/test/java/org/totipo/android/AndroidVaultControllerTest.java
?? app/src/test/java/org/totipo/android/reconcile/ProductControllerFixtures.java
?? review/M1J_ANDROID_VAULT_SHELL_REPORT.md
```

All changes remain unstaged/uncommitted. Nothing was staged, committed, tagged,
released, pushed or remotely triggered.
