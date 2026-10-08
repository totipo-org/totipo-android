# M1G — validated immutable canonical import

M1G implementation, agent validation and final human Nix validation are complete. Changes remain unstaged/uncommitted. This milestone imports one positive
immutable fact, without claiming synchronization or product session integration.

## 1. Starting state

- Branch: `main`.
- HEAD: `aff634bb7e279358f00f47dea746fc0faf7b6f11` (committed M1F).
- Before editing, ran branch, HEAD and short status checks: clean worktree.
- Dependencies remain released Java core/NIO 0.1.5 and BC 1.86; Java 17,
  Gradle/AGP/SDK, locks and verification metadata unchanged.
- No AGENTS.md present in the workspace. No agents delegated.

## 2. Scope

```text
provider read + candidate authentication + local immutable publication
```

New production boundary: `org.totipo.android.reconcile.ImmutableCandidateImporter`.
It imports one requested canonical RevisionId from a captured scan. No provider
export/write, journal, VAULT reconciliation, automatic repair, background work,
password storage, MainActivity/unlock UI or long-lived product session lifecycle.

## 3. Existing boundaries reused

Inspected M1C/M1D/M1F reports, M1D LocalReplicaOwner, M1E ProviderSnapshot/bounded
reader/traversal and source guard, and M1F classifier/result/group types.
Provider production sources and their safety test remain unchanged.

Inspected released 0.1.5 source JARs for ObjectCandidateValidation.Valid,
VaultSession, TotipoStore, ObjectWrite, ObjectName, StoreFailure, BoundedRead and
NioTotipoStore. Inspected exact release commit
`67c1326a2ce433921a947ba4ffd6843403f4a7a3`, including NioPrivateStoreTest,
PrivateNioWorkflowTest, store ownership/closure and missing-reference tests.
Ignored `.gradle/m1g-inspection` holds inspection material and validation logs,
not build dependencies. No Java implementation is copied into Android.

M1D root owner supplies exclusive application ownership; M1E supplies bounded
transport evidence, M1F groups actual Java authentication results, and private
NIO provides publication with default configured durability. The earlier failed
M1C shared-mode qualification is not reused as private-mode success: private
0.1.4 was subsequently physically qualified, and NIO 0.1.5 implementation bytes
are unchanged, as recorded in M1D/M1F and dependency provenance.

## 4. Lease/ownership sequencing

```text
caller acquires lease (no preexisting session/store)
  → importOne opens private NIO and transfers ownership to Totipo.open
  → normal authenticated session
  → classify entire captured scan under that session
  → select one eligible group; defensively retain exact ID/ciphertext
  → synchronous session.close (including core-owned store)
  → SAME lease, never released/reacquired
  → open private bridge store using lease.root()
  → publishObject once
  → close bridge store
  → return evidence/outcome
  → optional test-only normal session reopen and observation on SAME lease
  → caller releases lease
```

The component never calls owner.acquire or lease.close. It requires a worker
thread and exclusive, serialized use of the caller-held lease throughout.
Callers must not already own a session/store, close the lease concurrently or
open unrelated handles through it. This is the existing application ownership
contract, not a new OS-wide or multi-process lock.

The internal Transition gate rejects publication until session.close returns;
a thrown close never grants publication. Private selection/publication entry
points are inaccessible outside the reconciliation package. The public flow
accepts transport scan/selected ID/credential, never an arbitrary Valid or Group.
It performs actual session classification itself. Tests verify core closure calls
its owned store's close, another owner acquisition is rejected in the handoff,
publication before closure fails without creating the target, and normal reopen
occurs after publication store closure. No concurrent second store is opened.

Credentials are supplied transiently for initial normal authentication. No
production reopen, password re-entry mechanism or credential retention is added.
Future product integration must decide how to pause a long-lived unlocked session.

## 5. Import eligibility

- One authenticated representation: selectable.
- Exact duplicate Valid siblings: one selected ciphertext and one publication.
- SHORT, OVERSIZED, Invalid and transport-unavailable siblings: preserve evidence;
  they do not erase the completed authenticated fact.
- INTEGRITY_CONTRADICTION: refuse, no selected ID/bytes, no publication.
- Observed VALIDATION_UNAVAILABLE exact sibling: defer, no canonical write;
  classify the scan again under a supported live session before import.
- COMPLETE, INCOMPLETE, INCOMPLETE_LOADING and UNAVAILABLE coverage: preserved
  verbatim, including unavailable coverage in other scopes. Import never upgrades it.
- No Valid or no matching group: NOTHING_TO_IMPORT; never delete/alter local state.

Selection rechecks non-null ID, 1024-byte representation, group/Valid identity,
matching provider display names, an actually selected Valid sibling, exact
transport/Valid byte equality and bound/state, all Valid sibling equality, and
absence of validation-unavailable siblings. Publication rechecks the retained
ID/length/eligibility; bytes are private defensive snapshots. These checks do not
cryptographically authenticate fabricated values; package-internal callers are TCB.

## 6. Publication API

Gate PASS: released public `TotipoStore.publishObject(ObjectName, byte[])` accepts
the exact opaque representation. `new ObjectName(selectedRevisionId.hex())`
supplies the canonical direct-child identity; no Android object parser exists.
`NioTotipoStore.openPrivate(lease.root())` supplies the qualified private mode and
default durability. No direct canonical filesystem writes, Files.move, reflection,
internal Java access, TOKEN save/update reconstruction or VAULT preparation occurs
in production. TOKEN authoring and wrapper copying occur only in test fixtures.

Public bounded `readObject(ObjectName, 1024)` returns BoundedRead.Present with
independently owned bytes; tests use it to capture core-authored fixtures and
verify local exactness. Its Absent/Undersized/Oversized/WrongKind/Unavailable
outcomes are not treated as cryptographic classification.

## 7. Publication outcomes

| Actual Java 0.1.5 ObjectWrite | Android status | Evidence |
| --- | --- | --- |
| Written | IMPORTED | Newly published, positively acknowledged |
| AlreadyPresentExact | ALREADY_PRESENT | Exact-existing acknowledgement, idempotent success |
| ExistingDifferent | BLOCKED_EXISTING_DIFFERENT | Preserve existing target and selected provider evidence |
| Uncertain(reason) | IMPORT_UNCERTAIN | Mutation may have occurred without acknowledgement |
| Failed(reason) | STORAGE_FAILED | This invocation did not change the target |

The Result retains the actual ObjectWrite unchanged, including every StoreFailure:
UNAVAILABLE, UNSAFE_NAMESPACE and UNSUPPORTED. Store-open IOException propagates;
it is not converted into provider Invalid or successful import. Non-opened core
OpenResult is preserved as AUTHENTICATION_UNAVAILABLE; ownership is already handled
by the normal facade. Unexpected integration errors propagate conservatively.

## 8. ExistingDifferent

Never overwrite, delete, rename or install the candidate elsewhere in the canonical
namespace. Tests prepopulate the same local ID through public SPI with different
bytes, authenticate the genuine remote candidate, and verify exact old bytes plus
namespace entry count remain unchanged. Provider ciphertext/evidence remain in
Result. There is no active session to authenticate local bytes during publication;
this is a repair/integrity obstruction, not automatically an authenticated
contradiction. Later reviewed handling can distinguish invalid local obstruction
from authenticated contradiction.

## 9. Uncertainty/retry

No automatic retry. Uncertain retains the exact outcome/reason, even if bytes may
now exist. Released NIO tests show deliberate exact retries can yield Written or
AlreadyPresentExact after recoverable failures, but exact-existing acknowledgement
can itself be uncertain and a partial target can return ExistingDifferent.
M1G chooses the smallest conservative policy: one publication invocation.
Any future reviewed retry must retain the identical RevisionId and 1024 ciphertext
bytes; never re-encode. Mapping tests inject every failure/uncertainty reason and
require one call, exact bytes, unchanged outcome and no falsely reported success.

## 10. State neutrality outside target object

Every real successful/local-obstruction integration test snapshots and compares
the exact local VAULT wrapper and an unrelated core-authored immutable object.
Both remain byte-identical. No production API can reconcile VAULT here.

Provider Scan is retained by identity; its defensive ciphertext and sibling document
identities are unchanged. No resolver/provider handle enters the component, and a
new reconciliation source guard rejects provider mutation/resolver APIs, direct
filesystem mutation, Java internals and VAULT preparation. The original provider
read-only guard still runs unchanged through check.

## 11. Core observation after import

Real core creates vault A and an unrelated TOKEN. Tests copy only A's VAULT wrapper
into disposable B, authenticate B, and author a remote TOKEN through public core.
Public bounded SPI captures its exact ciphertext. On a fresh disposable local
replica, actual Java validation returns Valid, the session closes, the same lease
publishes with actual Written, and normal core reopen observes the TOKEN/revision
with no diagnostics. Exact repeated import returns actual AlreadyPresentExact.

A real authored child whose parent is initially absent also authenticates/imports.
Normal core exposes the child head with one unresolved reference. Importing its
parent later resolves ordinary observation and exposes the child head with no
diagnostics. Missing ancestry does not make immutable ciphertext Invalid.

## 12. Tests

Twelve new reconciliation JVM tests cover all requested scenarios:

1. New real same-root import, session closure, exact ciphertext and normal reopen.
2. Repeated exact import: actual AlreadyPresentExact, unchanged canonical bytes.
3. Duplicate provider identities: single selection/SPI call, unchanged evidence.
4. Valid + partial + Invalid + oversized + unavailable: import under every coverage
   enum; preserve individual classification and original coverage.
5. Prepopulated local exact object: acknowledged idempotence.
6. Prepopulated different local object: blocked, old bytes/namespace preserved.
7. Same lease across validation/closure/publication/reopen; other acquisition denied;
   tracking real store closed; live-session publication rejected before NIO open.
8. Symbolic contradiction (not an honest crypto collision): no selection, zero SPI
   calls; inconsistent ONE_REPRESENTATION label also rejected; local state neutral.
9. Real Valid + actual no-session unavailable sibling: deferred, zero publication,
   absent local target; reclassification through live public flow imports.
10. Empty/no-Valid scans: no authoritative absence or deletion.
11. Real missing-parent child import and later parent resolution.
12. All five ObjectWrite types/all three reasons, no retry, exact defensive inputs;
    structural ID/transport mismatch rejection and reconciliation source safety.

These are twelve test methods with multiple cases (including all coverage and
failure enum values), plus the unchanged 39 existing tests. Crypto is never mocked;
only package-internal publication/result mapping uses a fault seam. Symbolic Valid
values are used only for contradiction/structural/mapping tests. Disposable fixture
construction and TestReplicaOwners are JVM-test-only and excluded from APKs.

## 13. Security analysis

Valid is a descriptive public value, not an unforgeable capability. The reviewed
public path obtains it exclusively from actual VaultSession.validateObject via
M1F, under a single session/root; no public publication entry accepts forged Valid.
Internal structural checks add defense against integration errors without replacing
crypto. Package-private seams remain application TCB and must not become product
interfaces for independently supplied values.

Ciphertext is already bounded to exactly 1024; selection and SPI input are copied.
No candidate bytes are logged; no plaintext TOKEN, root key or password is stored.
Only normal core owns credential-derived secrets and destroys them at closure.
Evidence contains opaque provider identities; they carry no protocol authority.

One continuously held lease protects validation through publication. Session
closure precedes any bridge store. Local immutable targets are never overwritten;
uncertainty and provider incompleteness remain visible. No absent-provider inference,
provider writes, persistent bridge journal, VAULT mutation or automatic repair.
The same-process exclusive-root deployment assumptions from M1D still apply.

## 14. Validation

### Agent

PASS:

- `./gradlew check :app:assembleDebug :app:assembleRelease`: 90 actionable tasks,
  26 executed, 64 up-to-date; final tests/lint/builds passed.
- `./gradlew --offline --no-daemon --no-configuration-cache --no-build-cache
  --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug
  :app:assembleRelease`: all 92 tasks executed, passed.
- 51 JVM tests (12 new importer + 39 existing): zero failures/errors/skips in
  both final normal and forced offline builds. Existing provider source safety and
  new reconciliation source safety are included in these checks.
- `python3 tools/verify-wrapper.py`: passed.
- `python3 tools/verify-apk.py app/build/outputs/apk/debug/app-debug.apk`: passed.
- `python3 tools/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk
  --unsigned --no-debug-probe`: passed.
- Release DEX separately inspected: no createDocument/deleteDocument/renameDocument/
  moveDocument/removeDocument/openOutputStream names. Production importer present;
  tests/fixture helpers/debug probes excluded by APK checks.
- Debug APK SHA-256:
  `340b8337e2f82749b8be12b21e3d4c5494e919a94bd2f102fa7bba1b976dd000`.
- Release APK SHA-256:
  `7ebbc4ed41ffcde1c0e623925a7e7995f8f6797d5ad9281d62f2d48919faf2cc`.
- Toolchain verification passed using existing pinned JDK/SDK; no Nix invocation.
- Final Git/whitespace checks passed; no dependency/toolchain drift.

Initial test iterations corrected a test import-name ambiguity and the missing-parent
expectation (ordinary core exposes a valid head with unresolved ancestry). Both final
full builds passed after these corrections.

No Nix commands executed. No dependency/cache inputs changed; package-deps.json
was not regenerated. Existing source guards are wired into testDebugUnitTest/check.

### Human Nix

The human reported both final commands passed:

```sh
nix flake check path:.
nix build path:.
```

Both results are human-operated PASS reports; the agent executed no Nix commands.
No cache regeneration and no duplicate manual Gradle run requested.

### Human device

Not required. No new Android provider/framework behavior. Released private NIO
0.1.4 was physically qualified, and NIO 0.1.5 implementation bytes are unchanged.
M1G orchestration has deterministic real-NIO JVM coverage. No new Android-specific
filesystem behavior was discovered; no broad smoke test requested/performed.

### Remote CI

Not run for these uncommitted changes. No push or trigger.

## 15. Qualified scope

Controlled publication of one positively authenticated immutable representation
into the private canonical replica. This is not full reconciliation/sync, provider
freshness/exhaustiveness, export, graph repair, VAULT reconciliation or product UX.
Agent validation and the final human Nix checkpoint have passed. M1G is complete
within this qualified scope.

## 16. Recommended next milestone

**A. Foreground immutable reconciliation orchestration/product session integration.**

The released SPI supports the required boundary, and real-NIO tests verify the
handoff. Next decide how a foreground product operation coordinates a long-lived
unlocked session, deferred validation, preserved incomplete evidence, uncertain
publication and local obstruction reporting. Keep explicit session/store ownership
and closure under one lease. Provider export/journal is not the automatic next
step; no Java API enhancement was required for this bounded milestone.

## 17. Final Git state

Agent ran `git status --short`, `git diff --stat`, `git diff --check` and
`git diff --cached --stat` after artifact verification and repeated them after
finishing this report. Branch `main`; HEAD remains
`aff634bb7e279358f00f47dea746fc0faf7b6f11`.

`git status --short`:

```text
 M README.md
 M tools/verify-apk.py
?? app/src/main/java/org/totipo/android/reconcile/
?? app/src/test/java/org/totipo/android/TestReplicaOwners.java
?? app/src/test/java/org/totipo/android/reconcile/
?? review/M1G_VALIDATED_IMMUTABLE_IMPORT_REPORT.md
```

`git diff --stat`:

```text
 README.md           | 8 +++++---
 tools/verify-apk.py | 3 ++-
 2 files changed, 7 insertions(+), 4 deletions(-)
```

Git stat excludes untracked importer, tests/fixture helper and this report.
`git diff --check`: PASS. New files separately checked for whitespace/conflict
markers. `git diff --cached --stat`: empty. Provider sources/guard, all dependency
inputs and package-deps.json unchanged.

Nothing staged, committed, tagged, released or pushed. After recording the human
Nix PASS results, the agent repeated branch/HEAD/status/stat/whitespace/cached-stat
checks; the recorded state remains unchanged. Stop for review; no duplicate Gradle or physical-device run is requested.
