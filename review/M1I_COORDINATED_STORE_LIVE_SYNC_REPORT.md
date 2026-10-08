# M1I — coordinated private store and live-session inbound immutable sync

## 1. Starting state

Started on `main`, HEAD `77ebd2efa5376a0ff07eaedf48b041d27d5ea500` (committed M1H).
Ran `git branch --show-current`, `git rev-parse HEAD`, and `git status --short`
myself before editing; worktree was clean. No AGENTS.md was found in the workspace.
No Nix commands, dependency changes, device actions, staging, commits, tags,
releases, pushes, or remote CI triggers were performed.

## 2. Architecture correction

M1H's close → publish → reopen lifecycle qualified exact-byte publication and
normal Java observation while conservatively excluding concurrent stores. It
was qualification scaffolding, not the desired unlocked product architecture.
It unnecessarily destroyed the authenticated session and required another
credential/KDF operation. Production now exposes `ForegroundVaultCoordinator.sync(scan)`
and `sync(scan, cancellation)`, with no credential argument. The already-owned
session validates; a separate bridge control view publishes; that same session
receives `requestRefresh()` after exclusive access is released.

## 3. Java API inspection

Inspected actual released 0.1.5 sources and their embedded Javadocs before design.
Inspection inputs were the published sources JARs retained under
`.gradle/m1f-inspection`, checked byte for byte against release commit
`67c1326a2ce433921a947ba4ffd6843403f4a7a3` under `.gradle/m1g-inspection`:
all **93 core** and **15 NIO** Java files match exactly. They are inspection
inputs only; Gradle continues consuming external Maven artifacts.

| Published source artifact | SHA-256 |
| --- | --- |
| [Core sources](https://repo.maven.apache.org/maven2/org/totipo/totipo-core/0.1.5/totipo-core-0.1.5-sources.jar) | `d797c0854db469e8288b787dcbeb3dd3eb8e83062bea5c42b0013082b2cccdc4` |
| [NIO sources](https://repo.maven.apache.org/maven2/org/totipo/totipo-storage-nio/0.1.5/totipo-storage-nio-0.1.5-sources.jar) | `bee2a5c9f941360b9c499f1518e3b2201e1e94405a34a8d2709033419e2ed67f` |

Exact findings from `VaultSession.java`, `Totipo.java`, `spi/TotipoStore.java`,
`spi/PreparedVault.java`, `format/ApplicationVaults.java`, `format/StoreAdapter.java`,
`format/ApplicationSession.java`, `format/ApplicationStates.java`,
`storage/nio/NioTotipoStore.java`, `NioObjectStorage.java`, and `NioVaultStorage.java`:

- `Totipo.open(TotipoStore, char[])` transfers the supplied store on every outcome,
  including invalid password input and authentication failure. Success owns it
  until session closure. The caller must neither use nor close that facade.
- `TotipoStore` has synchronous `readVault(int)`, `scanObjects()`,
  `readObject(ObjectName,int)`, `publishObject(ObjectName,byte[])`,
  `prepareVault(byte[])`, and idempotent `close()`. It is opaque layout/storage
  SPI. Core serializes calls within one session; delegates need not be concurrent.
  Close never removes canonical state; the SPI does not require a facade to close
  independently owned backing domains.
- `StoreAdapter` owns the transferred facade. Its bootstrap and publication views
  share one adapter; its close is idempotent and calls store close once.
  Scan entries get fresh bounded reads, and names are interpreted by Java.
- `VaultSession.validateObject(RevisionId,byte[])` enters Java's provider/lifecycle
  gate. It validates exact 1024-byte candidates against the authenticated root,
  returns defensively owned exact ciphertext, performs no store I/O, password/KDF,
  import, state change or root export. Closed sessions reject it.
- `state()` returns the latest emitted immutable state without I/O. `states()` is
  ordered replay-latest with independent bounded/coalescing subscriber demand.
  Internal sequence numbers exist, but are not public. Object identity is available
  to distinguish state instances; neither identity nor Finished correlates requests.
- `requestRefresh()` is public, nonblocking, and requests another local observation.
  Requests may coalesce. An atomic pending flag and one observer executor schedule
  observations under Java's provider gate. A running observation can precede a
  request, and a queued request can coalesce. No request ID/future/generation is
  returned. This implementation emits Finished after an object pass; it need not
  emit Enumerating/Processing for subsequent passes.
- Observation scans/reads objects, authenticates and evaluates the graph under the
  session's existing root, then emits a new state. It does **not** reread/unlock
  canonical `vault` or adopt a different root.
- Java save/update/merge and password change use the provider gate. Save freezes
  semantics, publishes ciphertext through SPI, and requests refresh after certain
  publication. Merge may observe before publishing. Password change stages,
  reads back, rereads CURRENT, and replaces through `PreparedVault`.
- Session close rejects entrants, shuts down observation, waits for the provider
  gate, cleans owned resources, wipes secrets and closes the adapter. Subscriber
  callbacks are asynchronous and close does not wait for callbacks.
- `NioTotipoStore.openPrivate(Path)` is read-only construction and requires
  exclusive application control plus serialization across **all** calls/writers/
  handles/sessions/bridge operations for that root. NIO retains layout helpers and
  staged VAULT resources, not a transaction holding an open canonical stream
  between ordinary calls. Its active flag rejects operations after close;
  close is idempotent, closes vault stages and object storage, and does not delete
  canonical objects. Thus a persistent independently owned delegate is viable.
- `publishObject` preserves Written, AlreadyPresentExact, ExistingDifferent,
  Uncertain and Failed. Mutation entry controls uncertainty; NIO owns publication,
  staging, durability and no-overwrite behavior. `PreparedVault` operations also
  touch this same domain and must be gated, including cleanup.

The pinned upstream API_DESIGN.md was also inspected for BASE/CURRENT and state
contracts. Its old “prepared and unreleased” prose is not release provenance;
the actual released artifacts above are the source boundary used here.

## 4. `openPrivate()` role

> `openPrivate()` selects the Android-qualified local filesystem publication strategy.
> It does not change Totipo object/reconciliation semantics.

The single delegate continues using default durability and the qualified
complete-stage move installer. Shared/default open still selects hard links,
which were denied on the physically tested app-private filesystem. This milestone
adds no filesystem implementation or new provider primitive; the existing
qualification's filesystem/runtime scope and limitations remain unchanged.

## 5. Coordinated store ownership model

Selected **Model A**, one persistent coordinator-owned private NIO delegate:

```text
LocalReplicaOwner lifetime lease
    → ForegroundVaultCoordinator
        → CoordinatedPrivateStore owns one private NIO delegate
            ├─ SessionView: transferred once to Totipo.open, owned/closed by Java
            └─ scoped Bridge: Android control authority, exact object publication only
```

The session facade is a real logical resource. Its close rejects all future facade
operations and cleans its outstanding staged VAULT handles, idempotently. It does
not close the independently coordinator-owned root domain. The coordinator never
invokes that transferred facade behind Java's back. Bridge calls address the private
delegate through their own narrow scoped authority, not the session facade.
Final domain close is rejected while Java still owns an open facade. Explicit
foreground close first closes Java, then the domain/delegate, then releases the
root lease. A failure retains ownership for retry. No second delegate is opened
in sync; no delegate is secretly reused after it has been closed.

Model B (scoped delegate handles) was rejected: it needs needless reopening and
stage/resource transfer coordination to avoid overlapping handles. The actual
NIO lifecycle supports the simpler persistent domain. A facade directly shared
between Java and Android was rejected because it violates transferred ownership.
A shared read lock was rejected because even read calls must not concurrently
use a non-concurrent SPI delegate. Manual filesystem publication and changes to
Java internals were neither needed nor used.

## 6. Store gate semantics

One fair `ReentrantLock` serializes ordinary SPI calls and exclusive bridge
batches. Ordinary calls take/release it for one complete delegate call. Bridge
access holds it across the selected batch. Fair admission blocks later ordinary
entrants behind a waiting bridge; admitted operations finish first. There is no
application task queue or task creation here. Foreground busy operations reject
rather than accumulating work. Java has its existing single observation worker;
future UI dispatch must be bounded and run off the UI thread.

All `PreparedVault` operations are wrapped: readBack, initial install, replacement
and close. Stages cannot escape the gate or outlive their logical facade. Domain
and facade finalization use the same gate. All acquisition paths release in
finally/scoped close; lifecycle faults retain ownership rather than release a
live root. Bridge scope is thread-confined and rejects use after release.

Uncertain/Failed bridge publication or unchecked publication failure quarantines
the domain before gate release. Ordinary SPI I/O is rejected; cleanup/final close
remains allowed. This is intentional storage safety, not a leaked lock.

## 7. Lock ordering

```text
Java provider/internal gate → session SPI facade → Android store gate → NIO
```

Candidate validation happens before bridge acquisition. Exclusive scope contains
only cancellation flag queries and exact opaque publication; no VaultSession
method, password operation, refresh, open or close occurs there. Refresh is called
after scoped release, with an executable held-gate assertion. Cancellation callbacks
are contracted to be fast, nonthrowing flag reads; they must not perform session/I/O
work. No Android store gate → Java session gate path is introduced.

Source guards check the exclusive block and forbid callbacks into Java. The
barrier regression test puts a real Java save under its internal gate while waiting
for Android's held bridge gate, then verifies gate release before a Java validation
callback and requestRefresh. Explicit queue/admission and held-gate assertions,
not timeout alone, establish ordering. Timeouts only bound failed test cleanup.

## 8. Inbound sync lifecycle

```text
provider traversal/read outside local gate
→ bounded Scan
→ same-session validateObject/classification
→ defensive exact selection and RevisionId-hex ordering
→ one exclusive bridge scope
→ publish selected ciphertext without re-encoding
→ release bridge scope
→ requestRefresh once
→ Java subsequently emits ordinary object state
```

Scan enumeration, provider streams and loading waits never run under the local
store gate. No credential is accepted by sync. Incomplete coverage and every
provider sibling/locator/epoch remain evidence, not protocol authority. Exact
valid duplicate representations collapse; contradiction excludes its ID. Full-size
validation-unavailable siblings defer that ID, including groups with no valid
representation; unrelated positive groups may proceed. An actually closed session
is a session failure and prevents publication. Integrity warnings accumulate
across partial scans; absence does not erase them.

Sync reports materialization, unattempted IDs, actual failure and refresh-request
status plus the latest available observation progress. `view()` exposes detached
normal Java token/head/unresolved/diagnostic projections. It does not expose raw
session, root, storage, editors or secrets. Explicit sync is not wired into UI.

## 9. Same-session evidence

Real Java 0.1.5/private NIO tests use a copied same-root remote wrapper fixture
and exact remotely authored TOKEN bytes in provider-style bounded Scan values.
Instrumentation captures the session returned by the sole `Totipo.open`, checks
reference identity at refresh and after sync, and asserts one open, zero closes
during sync. Java observes imported heads through the same object's state stream.
Exact repeat, batches, incomplete transport and later missing-parent resolution
keep that same session. Sync has no credential parameter/field, no Totipo.open,
close or KDF path; public validation's inspected implementation has no KDF.
Passwords are inputs only to initial open or explicit password change and cleared.
There is no root-key export API invocation or Android crypto/parser code.

## 10. Materialization outcomes

| Actual SPI outcome | Batch policy and report |
| --- | --- |
| Written | IMPORTED; continue |
| AlreadyPresentExact | ALREADY_PRESENT; continue |
| ExistingDifferent | BLOCKED_EXISTING_DIFFERENT; preserve bytes and continue unrelated IDs |
| Uncertain(reason) | IMPORT_UNCERTAIN with exact reason; stop, quarantine and skip refresh |
| Failed(reason) | STORAGE_FAILED with exact reason; stop, quarantine and skip refresh |
| Unchecked publication failure | Preserve earlier outcomes, failing ID and later unattempted IDs; quarantine and skip refresh |

All StoreFailure enum values are tested for Failed and Uncertain. Reasons are
coarse and do not establish root usability. In uncertainty the coordinator retains
the session object and lease, performs no automatic close/reopen and transitions
STORAGE_UNSAFE; product observation/mutations are blocked until explicit teardown
and recovery. Already-running Java observation may subsequently encounter the
quarantine and fatally terminate itself according to Java's contract; survival of
an unsafe store is **not promised**. Tests with a quiescent real session prove no
forced close or refresh, and validation remains usable until explicit closure.
No uncertain acknowledgement is retroactively upgraded based on later observation.

## 11. Refresh-completion semantics

**EXISTING REFRESH API SUFFICIENT** for this asynchronous inbound primitive and
for proving normal Java observation of imported content. No Java change is required
for materialization or observation. It is **not** sufficient for a generic promise
that a particular refresh request has completed.

Finished alone can be replayed from before the request. Even a different subsequent
Finished state could come from an in-flight observation begun before materialization;
the public API has no exposed generation/request correlation. No artificial
Enumerating transition or guaranteed request acknowledgement is claimed. The
report deliberately says REFRESH_REQUESTED/REQUESTED, and labels its progress as
latestObservation. It does not wait for or advertise refresh completion.

Tests subscribe through the real states API and wait for specific imported head
IDs/content (or resolved ancestry), not an old Finished value. This is positive
observation evidence; it does not turn every imported historical revision into a
head, infer provider completeness, or prove newest remote state. A deterministic
held-observation test proves the report can contain the exact pre-sync Finished
progress while the import has not yet been observed. After release Java emits a
new state containing the TOKEN. A future UX needing request-correlated completion
should separately evaluate a Java refresh-generation API; none is required here.

## 12. M1H migration

Removed production CLOSING_FOR_IMPORT/REOPENING transitions, reopen outcomes,
credential-bearing reconcile, per-object bridge NIO opening and restored-session
claims. Production `ImmutableCandidateImporter` now contains only selection and
opaque exact publication/result mapping. Its old isolated close/import scaffold
is retained as **HistoricalImmutableCandidateImporter in test sources**, excluded
from both APKs. The 12 lower-level importer tests retain useful historical proof;
the source guard now permits session-only staged VAULT forwarding while continuing
to forbid bridge VAULT mutation and filesystem publication.

Review of all **20 original foreground tests**:

| Original M1H test | Classification / M1I treatment |
| --- | --- |
| noEligibleKeepsSameSessionWithoutPublication | Still relevant unchanged behavior; calls credential-free sync |
| singleRealScanValidationPublicationReopenAndExactIdempotence | Rewritten: same session, one open, zero closes, refresh/content proof |
| multipleImportsSortedAndAllObserved | Rewritten: same session, one exclusive scope and one refresh |
| duplicatesPublishOnceAndRetainEverySibling | Still relevant unchanged behavior; live path |
| incompleteAndMalformedTransportDoNotBlockPositiveImports | Still relevant unchanged behavior; live path |
| existingDifferentPreservedAndUnrelatedImportsContinue | Rewritten refresh expectation; preservation/continue remains |
| everyUncertaintyReasonStopsWithoutReopen | Rewritten quarantine/no forced close/no refresh, retained ownership |
| everyStorageFailureReasonStopsWithoutFabricatedSuccess | Rewritten quarantine/no refresh, actual StoreFailure retained |
| closeFailureRetainsOwnershipAndNeverOpensAnotherStore | Historical import-close assumption removed; explicit final-close retry test replaces ownership proof |
| wrongReopenCredentialDoesNotUndoAcknowledgedImport | Historical reopen-only scaffold removed; sync accepts no credential |
| reopenObservationBoundaryAndFailureAreExplicit | Historical reopen-only scaffold removed; refresh replay/pending-state regression replaces boundary proof |
| sameLeaseAndSessionGateAtAllTransitionPhases | Historical close/reopen phase expectations removed; lease lifetime, gate barriers and final-close tests replace them |
| concurrentReconciliationPasswordChangeAndCloseRejected | Still relevant admission behavior; uses sync, no rejected sync credential |
| cancellationBeforeAndAfterClassificationPreservesSession | Still relevant behavior; credential-free sync |
| cancellationDuringPublicationFinishesObjectThenReopens | Rewritten: finishes object, releases gate and refreshes same session |
| symbolicContradictionAndDeferredPlansExcludeOnlyAffectedIds | Still relevant planning/exclusion proof; unrelated live import |
| activeUnsupportedSessionIsFailureNotValidationRetry | Rewritten: defer affected ID, preserve unrelated positives, no reauthentication |
| publicationExceptionRetainsFailedOwnershipAndActualPriorOutcomes | Rewritten: quarantine, prior evidence/failing ID retained, no close/reopen |
| passwordChangeBeforeScanReconciliationRequiresNewCredential | Rewritten: successful explicit password change then sync without any credential |
| credentialsNeverBecomePersistentFieldsOrProductStorage | Still relevant guard; retained and extended by dedicated architecture guards |

Additional live tests cover missing parent, concurrent real save in both directions,
real password change under the foreground coordinator and directly through the
real session SPI, deadlock sequencing, explicit close failure and refresh replay.
Historical reports are unchanged.

## 13. VAULT architecture analysis

The coordinated domain could eventually project a **chosen** provider VAULT
representation into the ordinary local filesystem while excluding all Java SPI
calls. Storage serialization is reusable. That alone does not solve VAULT transport
reconciliation, active-session adoption or user authorization.

Java's existing password change reads BASE from local `vault`, authenticates with
the supplied current password, compares its root with the session's root, prepares
and validates a rewrap, rereads CURRENT, and requires exact BASE/CURRENT equality
before replacement. Unavailable/invalid observations fail; root mismatch or changed
present CURRENT is STALE; uncertain replacement requires explicit recovery.
The gate excludes a bridge replacement during each SPI call, but per-call gating
alone must not be mistaken for transaction-wide BASE/CURRENT serialization. A
future VAULT projection must remain serialized with the whole foreground password
operation, as immutable sync is today, or have an equally explicit transaction policy.

A live session retains its authenticated root after local `vault` changes.
`requestRefresh()` observes objects under that retained root, not canonical wrapper
replacement. A same-root/different-wrapper projection would leave object semantics
unchanged, but the active session cannot publicly validate/classify arbitrary
provider VAULT wrappers. A different-root projection would not cause it to adopt
that root; observed objects might become invalid/unavailable under its retained root.
Password change can detect authenticated root mismatch as STALE when given a
password capable of opening the projected BASE. It is not automatic adoption.

SAF can have multiple `vault` candidates while the local representation has one
canonical file. Android must decide a transport projection (or defer) before Java's
existing single-canonical-file APIs can reason about that file. Provider IDs, latest
modified time or candidate order cannot establish semantic authority. Same-root
wrapper comparison generally requires authentication with candidate credentials;
different-root candidates need explicit intent and authentication, not implicit
selection. The public object validation API is explicitly immutable-object-only.
There is no public candidate-VAULT validation or active-session adoption API here.

Therefore the division remains Android candidate/projection mechanics and Java
VAULT validity, root identity and BASE/CURRENT semantics. Future user action/password
requirements and possibly a narrowly scoped Java API must be designed before
provider VAULT projection. It is not implemented or claimed solved in M1I. No
bridge `prepareVault`, replacement, adoption or provider `vault` materialization exists.
The wrapper's session-facing prepareVault forwarding preserves ordinary Java
creation/password semantics; it is not transport VAULT reconciliation.

## 14. Local/provider architecture

```text
SAF/cloud provider candidate collection
    ↕ Android transport/local projection bridge
coordinated ordinary local Totipo representation
    ↕ private NIO (filesystem publication strategy)
app-private filesystem replica
    ↕ Java local refresh/object observation and graph evaluation
live VaultSession
```

This is a synchronization layer on another synchronization boundary, analogous
to desktop filesystem changes observed by Java. Android does not gain a protocol
reconciliation engine; the local store remains ordinary Totipo layout/opaque SPI.
Provider document IDs are transport bookkeeping only. Eventual reverse direction
is local Totipo store → Android bridge → SAF/provider candidates, with M1D's
journal/orphan semantics. Export and durable journal remain future work; inbound
code is not coupled to export state. No completeness-to-absence deletion exists.

## 15. Security analysis

One lease and one delegate establish one local mutation authority in this
single-process private-root deployment. Every delegate/staged-handle call shares
one gate; no Java call overlaps bridge materialization. Remote ciphertext is
validated by Java and copied exactly through qualified NIO publication, never
re-encoded or written with Android Files.write/move/FileChannel.force code.
No overwrite, key export, password retention, Android parser/crypto, provider write,
background worker, persistent bridge journal or hidden independent store exists.

No session API is called under exclusive access; explicit deadlock barriers and
source guards enforce the lock order. Failure/closing paths release the gate and
retain honest ownership. Unsafe storage is quarantined with truthful outcomes;
no freshness, power-loss, completeness, uncertainty-recovery or universal Android
conformance claim follows from these JVM tests. Production UI threading remains
an explicit worker-thread contract for the future UI, not an installed dispatcher.

## 16. Tests

**81 tests passed, zero failures/errors:** 23 live foreground, 5 gate/ownership,
2 coordination source guards, 12 lower-level/historical importer, 20 classifier,
10 provider snapshot, 2 root owner, 6 debug probe logic and 1 dependency smoke.
Tests exercise
released external Java 0.1.5/private NIO, with symbolic evidence only where an honest
same-ID authenticated contradiction cannot practically be authored.

- Gate: ordinary forwarding, all staged-handle operations, bridge waiting behind
  admitted SPI, fair pause of later SPI, no overlap, exception release, logical
  facade close, physical delegate final close, cleanup/final-close failure retry,
  rejection of final close while Java still owns its facade and use-after-close.
- Live: real provider-style validation, exact TOKEN import/idempotence, one open/
  no close/no sync credential, same-object identity, deterministic multi-object
  batch/one scope/one refresh, every coarse failure reason, obstruction preservation,
  contradiction/defer exclusions, incomplete coverage and cancellation.
- Graph: child import with unresolved parent then parent import; ordinary Java
  ancestry resolves on a later explicit same-session refresh.
- Concurrency: held bridge versus real Java save, held real save versus sync,
  password replacement held at real staged NIO replacement versus foreground sync,
  and real session password change queued behind bridge access. Probe assertions
  reject overlap on every real NIO/stage delegate entry.
- Lock order: real Java save owns internal gate and queues at Android gate; bridge
  releases before refresh path/Java callback. Explicit admission and held-gate
  assertions prove the order independently of timeout-based liveness checks.
- Refresh: imported-head content waits and a held observation demonstrate pending
  refresh versus old replayed Finished. No public completion correlation is invented.
- Source: existing provider read-only guard retained; added coordination/publication/
  key/parser/provider-mutation and no session-callback-under-exclusive guards in
  normal unit tests/check. Historical close/import/reopen helper exists only in tests.

## 17. Validation

### Agent

Ran myself, without Nix or remote CI:

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

Normal check/build passed, including all 81 tests, lint, build/Maven boundary
checks and release APK verification. Strict offline clean rerun also passed
with every task executed. Lint: zero errors, four inherited warnings (existing
TargetApi/Flow qualification annotation and three debug-probe hardcoded labels).
No new lint error or runtime qualification is hidden by those warnings.
Wrapper verifier passed (Gradle 9.8.0 JAR/distribution pins). Both standalone
APK checks passed; the checker now also requires CoordinatedPrivateStore and
explicitly excludes historical import scaffolding and coordination test/probe
classes from **both** APKs. Release still excludes all debug machinery and signing.

| APK | SHA-256 |
| --- | --- |
| Debug | `a5ad97d5ccfdf1c54ec80749ba952dacd87fe35a9a07a46a6affe99ef6d8845d` |
| Unsigned release | `b1c7101f482574c2062f7fde0b2669769fd9bcfcab38e8b7b0a0b887264b8908` |

Dependency inputs are unchanged: NIO/core 0.1.5, BC 1.86, Gradle, AGP, SDK,
locks, verification metadata, package-deps.json and flake.lock. No Nix dependency
cache regeneration. Git whitespace check passed; cached diff is empty.

### Human Nix

**Passed — human-reported.** The user confirmed both commands completed
successfully after agent validation. The agent did not execute Nix; no dependency
cache regeneration was needed.

```sh
nix flake check path:.
nix build path:.
```

No duplicate human Gradle run was requested. With both human Nix checks passed,
M1I validation is complete within the qualified scope.

### Human device

**Not required.** No new SAF/provider primitive, filesystem strategy or Android
framework behavior. Private NIO/default durability and the already exercised Flow
boundary remain; API-37 runtime qualification is not expanded to every minSdk device.

### Remote CI

**Not run.** No push or workflow trigger.

## 18. Qualified scope

Inbound immutable **live-session** synchronization primitive only. No session close,
reopen, password/KDF re-entry or root-key export on ordinary sync. No provider writes,
export, durable journal, VAULT transport projection, background sync, final unlock
UI, conformance or complete synchronization claim. Storage-unsafe outcomes explicitly
limit continued session use; they are not success cases.

## 19. Product architecture verdict

**COORDINATED STORE ARCHITECTURE CONFIRMED WITH LIMITATIONS**

Ownership can be respected with separate logical session and bridge views over
one domain; no Java internals/API changes or new filesystem publication are needed.
Real same-session observation works and synchronization gates exclude delegate
races. Limits: no request-correlated refresh completion, conservative quarantine
on coarse storage failure, single-process/root exclusivity assumptions, existing
runtime qualification scope and future UI dispatch. Agent validation and both
human Nix checks have passed; M1I is complete within the qualified scope.

## 20. Recommended next milestone

**A. actual Android unlock/vault interface using live-session sync.**

Use an explicit bounded foreground worker, detached observations and truthful
refresh-request/observation presentation. Keep export + durable journal (B) and
VAULT transport projection design (C) distinct future work. A future generic
refresh-completion UX or authenticated VAULT-candidate projection may justify
D, but no Java API enhancement is required for this milestone's asynchronous
immutable storage coordination.

## 21. Final Git state

`git status --short`:

```text
 M README.md
 M TOTIPO_JAVA_DEPENDENCY.md
 M app/src/main/java/org/totipo/android/LocalReplicaOwner.java
 M app/src/main/java/org/totipo/android/reconcile/ForegroundVaultCoordinator.java
 M app/src/main/java/org/totipo/android/reconcile/ImmutableCandidateImporter.java
 M app/src/test/java/org/totipo/android/reconcile/ForegroundVaultCoordinatorTest.java
 M app/src/test/java/org/totipo/android/reconcile/ImmutableCandidateImporterTest.java
 M tools/verify-apk.py
?? app/src/main/java/org/totipo/android/reconcile/CoordinatedPrivateStore.java
?? app/src/test/java/org/totipo/android/reconcile/CoordinatedNioProbe.java
?? app/src/test/java/org/totipo/android/reconcile/CoordinatedPrivateStoreTest.java
?? app/src/test/java/org/totipo/android/reconcile/CoordinationSourceGuardTest.java
?? app/src/test/java/org/totipo/android/reconcile/HistoricalImmutableCandidateImporter.java
?? review/M1I_COORDINATED_STORE_LIVE_SYNC_REPORT.md
```

`git diff --stat`:

```text
 README.md                                          |  29 +-
 TOTIPO_JAVA_DEPENDENCY.md                          |  12 +-
 .../java/org/totipo/android/LocalReplicaOwner.java |   9 +-
 .../reconcile/ForegroundVaultCoordinator.java      | 197 +++++-----
 .../reconcile/ImmutableCandidateImporter.java      |  89 +----
 .../reconcile/ForegroundVaultCoordinatorTest.java  | 410 ++++++++++++++-------
 .../reconcile/ImmutableCandidateImporterTest.java  |   7 +-
 tools/verify-apk.py                                |   7 +-
 8 files changed, 416 insertions(+), 344 deletions(-)
```

`git diff --check`:

```text
(empty output)
```

`git diff --cached --stat`:

```text
(empty output)
```

The tracked diff stat excludes the six untracked additions listed above, including
the new domain, test helpers/guards and this report. HEAD remains the starting M1H
commit. Nothing staged, committed, tagged, released or pushed. Historical M1H
reports are unchanged; all changes are left for review.

