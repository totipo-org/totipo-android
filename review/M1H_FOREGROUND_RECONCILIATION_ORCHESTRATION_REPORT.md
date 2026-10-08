# M1H foreground immutable reconciliation orchestration

M1H is complete within the qualified foreground orchestration scope. Agent
validation and human-reported Nix validation pass. No device run is required for
this application orchestration scope. Changes remain uncommitted.

## 1. Starting state

The agent ran the requested Git commands before editing:

```text
branch: main
HEAD: a64f1be25c8202f8919702b80f102a1e9d1710e6
status --short: empty
```

Dependency baseline: production storage-nio 0.1.5 → core 0.1.5 → BC 1.86;
AGP 9.4.0, wrapper Gradle 9.8.0, compile/target SDK 37, min SDK 26,
Build Tools 36.0.0. No dependency/version/configuration changes were made.
Locks, verification metadata, package-deps.json and flake.lock remain unchanged.

Inspected LocalReplicaOwner, all M1E snapshot/read/traversal classes, M1F
classification, M1G importer and tests, owner closure tests, source guards,
MainActivity/build configuration, and M1D–M1G reports. Also inspected locally
available released Java 0.1.5 source at upstream commit
67c1326a2ce433921a947ba4ffd6843403f4a7a3: VaultSession, Totipo/OpenResult,
state/observation APIs, ApplicationSession closure and ApplicationVaults ownership,
NioTotipoStore publication/closure and actual StoreFailure reasons. No upstream
or lower-layer source was changed.

## 2. Scope

Added `ForegroundVaultCoordinator`, a synchronous worker-thread application
lifecycle wrapper, and 20 deterministic JVM tests. It accepts one already captured
M1E Scan, authenticates immutable candidates against its real active session,
plans eligible objects, suspends the session, imports sequentially, and restores
an ordinarily observed session when safe.

MainActivity remains the bootstrap. No final unlock UI, Activity lifecycle policy,
worker/executor, DI, Android Service, provider selection/grant handling, provider
export, journal, background sync, VAULT reconciliation, biometrics or inactivity
policy was added. No scan is repeated automatically. No remote absence deletes
local facts. M1E/M1F/M1G and the existing provider source guard are unchanged.

## 3. Ownership model

The caller owns one coordinator lifecycle wrapper. The coordinator alone owns the
lease and, when present, its one VaultSession. Public methods expose neither
lease, root Path, store, raw session, VaultState editor nor session-scoped token
handles. `view()` copies descriptors, revision IDs, unresolved references and
normal observation diagnostics into a descriptive projection. It includes
accumulated integrity warnings, which a subsequent partial scan cannot clear.

| Phase | Lease owner | Session/store owner |
| --- | --- | --- |
| Opening | Coordinator | Totipo takes ownership of the private store; coordinator receives successful session |
| Open/classification/password change | Coordinator | Coordinator owns exactly one session; session owns its store |
| Closing for import | Coordinator | Coordinator retains session until synchronous close returns |
| Importing | Coordinator | No session; one scoped private store per sequential publication |
| Reopening/observation | Coordinator | Totipo takes new store; coordinator owns successful session before waiting for observation |
| Failed closed | Coordinator | No usable foreground access; possibly retained session if closure failed |
| Final close | Coordinator until all closure succeeds | Session closes before lease release |
| Closed | Nobody | No live session/store |

`Opening` returns the owned wrapper even on authentication/observation failure.
The caller must close that wrapper as well. Acquire failure throws without handing
off a lease. Failed-close ownership is retained for explicit retry, never lost or
released based on an assumption. `FAILED_CLOSED` describes the access gate: it
does not assert that a failed session close finished.

The foreground session stays inside the wrapper after reconciliation. Returning
a report transfers no raw session; the caller resumes gated use of the same
wrapper after the operation finishes. This avoids shared session ownership.

## 4. State machine

```text
acquire → OPENING → ordinary observed OPEN
OPEN → RECONCILING_READ_ONLY → OPEN (no plan or pre-close cancellation)
OPEN → RECONCILING_READ_ONLY → CLOSING_FOR_IMPORT → IMPORTING
     → REOPENING → ordinary observed OPEN
OPEN → CHANGING_PASSWORD → OPEN
OPEN/FAILED_CLOSED → CLOSING → CLOSED
failure → FAILED_CLOSED
```

A synchronized state gate admits exactly one operation. Work executes outside the
monitor so concurrent callers get busy rejection instead of queuing. View access,
reconciliation, password change and close reject non-OPEN/busy states. Final close
also admits FAILED_CLOSED. The gate stays busy through result construction and
credential cleanup, including the final post-reopen cancellation check.

There are no lifecycle boolean combinations; local variables only track operation
progress/result assembly. Core serialization remains an additional inner gate.

## 5. Classification and import planning

Production always invokes M1F `classify(scan, ownedSession)`. Public callers cannot
submit classification results or an import plan for mutation. The internal plan
has a private constructor and immutable lists: canonical IDs, M1G's defensive
1024-byte ciphertext selections, provider/group document provenance, original
scan epoch and original provider coverage. It contains no TOKEN plaintext,
credential or root material. Provider locators are reporting provenance only;
publication receives only canonical ID and validated bytes.

M1G selection is reused unchanged. One representation, no contradiction and no
validation-unavailable exact sibling are required. Exact Valid duplicates collapse
to one publication. Invalid, SHORT, OVERSIZED and transport-unavailable siblings
remain evidence without vetoing a positive fact. No eligible selection means no
session close or publication.

Contradictions exclude their ID, not independent eligible IDs. Java's immutable
validation establishes each fact without graph completeness/current-head authority;
normal reopened observation handles unresolved ancestry. Importing independent
facts is therefore safe. Neither planning nor NIO selects or overwrites a
contradictory representation. Reports retain the contradiction, and the wrapper
retains integrity warnings in subsequent views rather than silently clearing them
when a later scan omits the ID. This is not a contradiction repair/resolution UX.

Valid + validation-unavailable remains deferred in selection/report semantics.
In this particular coordinator every validation-unavailable result from its owned
active session means unsupported validation or unexpected closure, so the whole
operation fails closed without imports or a validation retry. A safe unrelated
group is not imported through a failing active-session contract. Transport
unavailability remains distinct and does not trigger that rule.

## 6. Session suspension

Beginning reconciliation prevents public session-backed use. Only descriptive
copies already returned remain available. Once the immutable plan exists and
cancellation has been checked, the state becomes CLOSING_FOR_IMPORT.
`VaultSession.close()` completes synchronously before the session reference is
cleared and before any publication store is opened. Java's close gate rejects
entrants, waits for owned mutation certainty, wipes secrets and terminates states.

If close throws, there are zero publications and no second store. The wrapper
retains the same session and lease in FAILED_CLOSED. A later explicit wrapper
close retries closure; lease release occurs only after it succeeds.

## 7. Batch import policy

Eligible IDs are sorted lexically by RevisionId.hex(). Ordering is operational
determinism only, with no freshness/graph authority. Each selected object uses
M1G `publishExact` in a scoped `NioTotipoStore.openPrivate` under the original
lease. The store closes before the next object or any normal reopen.

| Actual outcome | Policy |
| --- | --- |
| Written / IMPORTED | Record and continue |
| AlreadyPresentExact / ALREADY_PRESENT | Record idempotence and continue |
| ExistingDifferent / BLOCKED_EXISTING_DIFFERENT | Preserve local bytes, report obstruction, continue unrelated IDs |
| Uncertain / IMPORT_UNCERTAIN | Record actual reason, abort remaining IDs, no automatic reopen |
| Failed / STORAGE_FAILED | Record actual reason, abort remaining IDs, no automatic reopen |

Items retain the actual ObjectWrite, not a boolean. `count(status)`, contradiction
and deferred-ID lists, full scan evidence, unattempted IDs and independent reopen
status permit presentation of partial outcomes. An unexpected publication/open
exception records a separate `publicationFailureId`, failure and truly unattempted
remaining IDs; it invents neither an SPI acknowledgment nor success.

## 8. Failure policy

ExistingDifferent is a local unvalidated obstruction, not an authenticated
contradiction. It never overwrites the blocked bytes or blocks unrelated imports.

StoreFailure has only UNAVAILABLE, UNSAFE_NAMESPACE and UNSUPPORTED. None
establishes an object-local failure with a usable root. This implementation
therefore stops for all three, preserving the exact reason. It also stops for
all three Uncertain reasons. Both leave the gate FAILED_CLOSED and lease held;
explicit close ends ownership before a future recovery/open operation. No retry
or automatic reinterpretation of uncertainty occurs.

Close failure retains the possibly incompletely closed session and lease, with
no second store. Reopen authentication failure leaves acknowledged immutable
imports in place and returns
RECONCILIATION_APPLIED_BUT_SESSION_REOPEN_FAILED. Observation failure after open
closes the new session (or retains it if that close fails), preserves lease
ownership and reports the same applied-but-failed state. If no new publication
was acknowledged, the distinct SESSION_REOPEN_FAILED result avoids claiming an
import happened. Neither result rolls back monotonic facts.

An initial open failure likewise returns a FAILED_CLOSED owned wrapper with the
actual OpenResult/failure. No failed wrapper presents a usable foreground view.

## 9. Session reopen

Reopening uses only the current foreground credential supplied for that operation.
There is no session cloning/root-key export or hidden resume token. After all
publication handles close, normal Totipo.open takes the new private store under
the same lease. The new session remains gated in REOPENING until its replay-latest
states publisher reaches ObservationProgress.Finished. Finished with diagnostics
is an ordinary completed observation, not a fabricated clean graph.

Publisher error/completion before Finished is a failure. Subscription cleanup is
deterministic. Waiting preserves interruption and does not expose partially
observed state. No arbitrary timeout or background framework was introduced.

Observation uses the existing Java Flow boundary already exercised in M1C's
API-37 qualification, with a narrow TargetApi(30) annotation on that method.
New coordinator collection operations use compatible collectors. No dependency,
desugaring or SDK change was introduced. Manifest minSdk 26 remains a packaging
floor, not a claim that this released Java graph works on all older Android
runtimes. Future actual unlock UX must respect supported/qualified runtime scope.

## 10. Cancellation

Use a fast, thread-safe, nonthrowing cancellation flag. Before classification or
before closure, cancellation returns without mutation and retains the same
session. The finite scan is not rescanned.

After closure, cancellation is checked before each object. It never interrupts an
object publication; a current operation finishes and its exact result is retained.
A known outcome allows stopping before the next object and normal observed reopen.
Cancellation during reopen is deferred through Finished and then reported. An
uncertain/storage-failed outcome takes precedence and forbids automatic reopen.

Do not cancel a mutation worker using Thread.interrupt/future interruption:
interrupting NIO may itself change the outcome. An already observed interrupt is
handled as cancellation at boundaries, and observation waiting restores the flag,
but the wrapper cannot make hostile external interruption of filesystem I/O safe.
No interrupting cancellation API or unbounded queue is installed.

## 11. Provider completeness semantics

Every report with classification retains the original Scan identity, epoch,
coverage, issues, directories and all sibling evidence unchanged. Incomplete
coverage does not prevent individually authenticated positive imports. COMPLETE
is finite evidence, not a live transaction, freshness promise or exhaustive
synchronization guarantee. Cancellation before classification reports no invented
authenticated evidence. No result says fully synchronized; remote VAULT is not
validated/adopted, and absence never removes local facts.

## 12. Credential/security analysis

The caller transfers an exclusively owned mutable char buffer for the operation;
open/reconcile/changePassword clear supplied buffers in finally, including busy
rejection. No persistent coordinator/operation field, Bundle, preference, file,
report or background task retains the credential. No JVM memory-erasure guarantee
is claimed. Java continues to own root secrets; no root-key extraction exists.

Only one coordinator owns one lease; no public handle escape permits concurrent
NIO stores or mutation. The same lease spans read-only validation, suspension,
publication and reopen. Password change uses the same gate and delegates unchanged
core semantics. Its UNCERTAIN outcome closes foreground access rather than
continuing with unestablished wrapper state. A captured scan can still validate
immutable objects after a password change, but reopen needs the new password.

Partial imports, contradictions, incompleteness, blocked local names and storage
uncertainty are separate evidence. A failed foreground reopen does not undo an
acknowledged import. The future UI must present these states, including integrity
warnings; this milestone does not implement repair/export or silent sync.

## 13. Tests

`ForegroundVaultCoordinatorTest`: 20 tests, zero failures/errors/skips. Fixtures
are authored by real Java create/save operations under a disposable shared root
wrapper, not fabricated crypto. Core success paths use real validation, private
NIO, normal reopen and observation. Faults use package-private Operations and
cancellation seams. Test-only proxies simulate unsupported session validation.

| Required scenario | Evidence/result |
| --- | --- |
| 1. No eligible imports | Same real session remains active; one initial open, zero closes/publications |
| 2. Single eligible import | Real Scan → validation → closed session → NIO → reopened observed TOKEN |
| 3. Multiple independent imports | Three IDs in lexical order; three normally observed tokens |
| 4. Exact duplicates | One publication; both provider siblings retained unchanged |
| 5. AlreadyPresentExact | Second run reports idempotent acknowledgment |
| 6. Contradiction + safe object | Symbolic contradiction excluded from the same plan as real safe group; independent safe object imports and is observed |
| 7. Deferred group + safe object | Symbolic deferred group excluded while safe group remains eligible; actual Valid + unsupported sibling + safe group triggers required active-session failure with zero imports |
| 8. Incomplete scan | Positive import; INCOMPLETE_LOADING and epoch retained, alongside invalid/short/overflow/unavailable siblings |
| 9. ExistingDifferent | Local bytes unchanged; two other imports continue and normal reopen succeeds |
| 10. Uncertainty | All three actual StoreFailure reasons stop after one object; no auto-reopen; actual installed bytes may exist and a later explicit recovery observes them |
| 11. Storage failure | Every StoreFailure reason preserved; zero fabricated imports, two IDs unattempted, no auto-reopen |
| 12. Failed close | Zero imports/second store; same lease controlled even across failed final close, then released on successful retry |
| 13. Reopen auth failure | Wrong credential reports applied-but-reopen-failed; three objects remain and later real authentication observes them |
| 14. Reopen observation | Finished required on success; injected observation failure retains acknowledged imports and closes access |
| 15. Lease continuity | Competing acquisition fails during classification, close gap, publication and reopen |
| 16. Concurrent reconciliation | Second worker run rejected while first run is held at deterministic barrier |
| 17. Password mutation gate | Password change and close rejected during reconciliation; real earlier rewrap then reconciliation uses new credential |
| 18. Pre-close cancellation | Before/after classification: no publication/close and original session preserved |
| 19. Between-object cancellation | Request inside publication finishes current real import, stops two remaining IDs, and restores one-token observed session |
| 20. Provider neutrality | Original scan/sibling bytes retained; inherited provider guard plus reconciliation/new coordinator guards pass |
| 21. Credential retention | Consumed buffers cleared on successful and rejected calls; no persistent credential fields/storage references |

Contradiction evidence is symbolic: honestly producing two different authenticated
representations of the same keyed ID would require breaking the crypto contract.
The symbolic exclusion/plan test is explicitly distinct from the real
crypto/publication success path. No crypto success is mocked. Actual owned-session
validation unavailability is deliberately a whole-operation failure, not a retry
caused by closing the session.

The full check runs 71 JVM tests: smoke 1, owner 2, debug probe logic 6,
provider snapshot 10, candidate classifier 20, immutable importer 12, foreground
coordinator 20. All pass. Existing source-safety tests remain wired through
check → testDebugUnitTest. New plan lists are also tested for immutability.

## 14. Validation

### Agent

The agent ran all commands itself:

```sh
./gradlew check :app:assembleDebug :app:assembleRelease
./gradlew --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug :app:assembleRelease
python3 tools/verify-wrapper.py
python3 tools/verify-apk.py app/build/outputs/apk/debug/app-debug.apk
python3 tools/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk --unsigned --no-debug-probe
```

Final normal build: PASS, 20 seconds, 90 actionable tasks (8 executed,
82 up-to-date). Forced offline build: PASS, 37 seconds, 92 actionable tasks,
all 92 executed. Final XML results: 71 tests, zero failures/errors/skips.
Both source guards, lint, Maven boundary and release APK boundary run in check.

Wrapper verification: PASS, Gradle 9.8.0 JAR/distribution pins.
Debug APK verification: PASS, SHA-256
`28b1952a2aefb80181098ffe85cce7b365db55bab55906bc6ff7dd507d7fffb6`.
Unsigned release APK verification: PASS, SHA-256
`961569f27b334999a6f41769307abef40199c8fb88e3eb164c3977453170ed7e`.
Release contains the production coordinator/core/NIO/BC/provider/classifier/importer
and excludes debug probes and test/fault fixture classes. APK checks now require
the coordinator and reject its test class/nested fixtures.

Git whitespace check: PASS; cached stat empty. Additional Python checks of new
source/test/report files found no trailing whitespace or conflict markers.
`git diff --exit-code` over dependency/build/toolchain inputs, owner, M1E/M1F/M1G
and inherited provider tests: PASS, no drift. No cache regeneration.

An initial full check caught newly used Stream.toList Android compatibility errors
and the explicit Flow observation API boundary. These were corrected with
compatible collectors and the documented inherited observation qualification;
no SDK/dependency update or broad lint suppression was used. Existing lint warnings
(Gradle update and debug probe labels), AGP AAPT override warning and Gradle
future-deprecation notice remain; final lint has no errors.

### Human Nix

PASS — the user reported successful execution of both commands below. This is
human-reported evidence; no Nix command was executed by the agent. Dependencies
are unchanged, and no cache regeneration or manual Gradle duplication was needed:

```sh
nix flake check path:.
nix build path:.
```

### Human device

Not required. No new Android provider/framework/filesystem primitive or Activity
lifecycle behavior was introduced. Existing M1B/M1C and 0.1.5 qualification plus
deterministic JVM integration tests cover this bounded orchestration change.
This does not extend physical runtime/filesystem qualification beyond that
previous evidence.

### Remote CI

Not run. No push or CI trigger.

## 15. Qualified scope

M1H establishes an owned foreground lifecycle for monotonic immutable imports:
read-only classification without needless session churn, immutable authenticated
planning, same-lease closed-session publication, deterministic partial batches,
explicit failure/cancellation semantics, and ordinary observed restoration.
It does not establish background/automatic sync, provider export/durability,
VAULT reconciliation, final unlock/session UX or broad Android runtime support.
Agent qualification and human-reported Nix qualification pass.

## 16. Product/session design findings

**YES WITH PRODUCT CONSTRAINTS.** Current Java APIs are sufficient for a foreground
operation with the correct current credential supplied during that operation.
The wrapper can own a long-lived unlocked session; candidate validation itself
uses that session without KDF or password reauthentication. The reconciliation API
requires the current credential up front for a possible import/reopen, since
importing suspends the session and normal reopening needs authentication material. This design consumes the operation credential and never caches it.

Future unlock UX must accept explicit credential availability/re-entry for such
an import operation and resume through the wrapper, serialize all mutations,
present partial/integrity/uncertain outcomes, and own eventual close. If the
intended UX instead requires transparent resumption after close without fresh
authentication material, that is a separate Java session-resumption design review;
current APIs do not supply it. Root export or persisted passwords are not a
workaround. No such transparent-resumption requirement is implemented here.

## 17. Recommended next milestone

**A. actual unlock/session product flow.** Establish worker dispatch, lifecycle
ownership, runtime qualification gate, credential re-entry expectations and
presentation of failed restoration before choosing export/journal as a next step.
Reconciliation status/UI can follow that concrete lifecycle. A silent-resumption
requirement would redirect this recommendation to Java API/design review.

## 18. Final Git state

The agent ran and repeated these after finishing validation/reporting:

```sh
git status --short
git diff --stat
git diff --check
git diff --cached --stat
```

Branch remains `main`; HEAD remains
`a64f1be25c8202f8919702b80f102a1e9d1710e6`.

`git status --short`:

```text
 M README.md
 M tools/verify-apk.py
?? app/src/main/java/org/totipo/android/reconcile/ForegroundVaultCoordinator.java
?? app/src/test/java/org/totipo/android/reconcile/ForegroundVaultCoordinatorTest.java
?? review/M1H_FOREGROUND_RECONCILIATION_ORCHESTRATION_REPORT.md
```

`git diff --stat`:

```text
 README.md           | 17 ++++++++++-------
 tools/verify-apk.py |  3 ++-
 2 files changed, 12 insertions(+), 8 deletions(-)
```

`git diff --check`: PASS (empty). `git diff --cached --stat`: empty.
Git diff stat omits the untracked coordinator (336 lines), tests (431 lines) and
report. Those new files were separately checked for whitespace/conflict markers.
Lower layers and all dependency inputs remain unchanged. Nothing staged,
committed, tagged, released or pushed. After recording human-reported Nix PASS,
the agent repeated branch/HEAD/status/stat/whitespace/cached-stat checks; the
recorded Git state remains unchanged. Stop here for review.
