# M1D production local replica and reconciliation design

Production NIO promotion and the local root owner are implemented. Reconciliation
below is a proposed provider-independent design, not an implemented or qualified
bridge. Stop for review of the orphan repair and VAULT/interoperability limits
before implementing transport writes. Agent validation and human-reported Nix
validation pass. M1D is complete within this implementation/design scope; the
bridge remains unimplemented and subject to the review limits below.

## 1. Starting state

Branch `main`, HEAD `3e5b7302b8c1515bde9be21eb5af71a90a6310c6`.
The agent ran `git branch --show-current`, `git rev-parse HEAD`, and
`git status --short` before editing; status was empty.

Baseline: app directly declared core 0.1.4 in production and NIO 0.1.4 in debug.
Release runtime contained core 0.1.4 → BC 1.86; debug additionally contained
NIO 0.1.4 → core 0.1.4. Strict locks, verification metadata, reviewed hashes and
`package-deps.json` already included both 0.1.4 artifacts. Release DEX explicitly
excluded NIO. No Maven Local, project/file/composite substitution was configured.

Inspected app Gradle configuration and runtime resolution, all lockfiles,
verification metadata, reviewed hashes, package cache/derivation, APK checker,
CI, bootstrap Activity/manifest, debug M1B/M1C helpers and tests, and committed
[M1B](M1B_ANDROID_PROVIDER_PROBE_REPORT.md),
[M1C](M1C_LOCAL_REPLICA_RECONCILIATION_REPORT.md), and
[0.1.4 repin](TOTIPO_JAVA_0_1_4_ANDROID_REPIN_REPORT.md) reports.
The baseline Gradle dependencies invocation specified the task twice with two
configurations; Gradle emitted only the last debug configuration. Release baseline
is established by committed declarations, locks and repin evidence; final graphs
were inspected separately.

Current non-normative [design guidance](https://github.com/totipo-org/totipo-spec/blob/fbae625f2cad6c89a1b5555de2f2d75d890bb682/docs/design/DESIGN.md)
was fetched at upstream HEAD `fbae625f2cad6c89a1b5555de2f2d75d890bb682`.
Its location-selection, password-change and lifecycle guidance does not establish
SAF as a store. Its password guidance requires current-password reauthentication
and acknowledges retained old wrappers. This milestone implements no product
navigation, unlock or session policy. The private root is never a user location
choice; a future transport selection needs its own presentation review.
Normative v1/r18 remains the Java pin
`4623a7e1718e23504903096c92332597057bd8f0`, especially sections 8 and 13.

## 2. Production dependency promotion

The single production declaration is:

```text
implementation org.totipo:totipo-storage-nio:0.1.4
app → totipo-storage-nio:0.1.4 → totipo-core:0.1.4 → bcprov-jdk18on:1.86 (runtime)
```

NIO exposes core on the compile classpath, so a second direct core declaration
adds no API boundary. Debug and release require the same Totipo graph.
`verifyMavenBoundary` checks exact requested/selected versions, the direct NIO
edge, NIO → core, core → runtime BC, and external module/artifact identities.
It rejects 0.1.3, mixed requests, extra Totipo modules and local substitution.
Central remains the only configured Totipo source; Maven Local remains absent.

Release intentionally packages core, NIO and BC. The checker now requires NIO in
both variants and retains the entire debug-package exclusion in release. It also
rejects unit-test/JUnit descriptors. `--debug-probe` verifies the qualification
Activity in debug; the old `--debug-nio` boundary is retired. Release checks in
Gradle, Nix installation and lightweight CI pass `--no-debug-probe`.
No historical report was rewritten to pretend its release contained NIO.

Lock regeneration changed only NIO's release compile/runtime/lint configuration
membership. No version, artifact, verification hash, reviewed hash or Nix download
cache change is needed: these exact artifacts were already resolved by check's
debug qualification build. No Nix cache regeneration was performed.

## 3. Local replica architecture

`LocalReplicaOwner.from(context)` resolves the application context's
`getNoBackupFilesDir()/totipo-vault`. The name deliberately identifies the one
local canonical vault, separate from disposable `m1c-nio-qualification` roots.
Acquisition creates the top directory if absent and rejects a non-directory or
symlink at that location. It provides the `Path` only through an active lease.
The directory is app-private, excluded from Android backup/restore by its location,
stable across ordinary restarts, and removed on normal uninstall. No broad storage
permission, provider path or external configuration is involved.

The process-wide singleton rejects a second acquisition, including a second flow
on another thread and reentrant acquisition. It has no public constructor; the
package-private path constructor is a JVM test seam, not a second production owner.
All production callers must use the singleton. The application currently declares
no secondary process; root access must remain single-process. This is an explicit
application TCB invariant, not an OS lock against arbitrary app code or compromise.

Acquire on a worker, open `NioTotipoStore.openPrivate(lease.root())` with default
durability, perform work, close every session/handle and await outstanding work,
then release. `Totipo.open/create` transfers store ownership even on failure;
a successful session owns it until close. The lease outlives that entire ownership.
No independent NIO handle can be opened while a session holds the lease. Future
imports acquire this same gate; they must await session closure or run through a
reviewed single owned-store integration, never open a second handle beside it.
Provider I/O can collect bounded snapshots outside the gate; local acceptance and
BASE/CURRENT checks happen under it. Busy means retry later, not bypass the owner.

The lease API cannot prevent trusted callers from retaining a raw Path/handle or
closing prematurely. Javadoc explicitly forbids both. Root confinement and lease
use are mandatory code-review constraints for every future call site. A future
multi-process root user would require a new ownership design before introduction.

The owner has no protocol/SAF/credential/UI logic and retains no unlocked session
or K_root. MainActivity remains the launch-only bootstrap. No root is opened at
startup, no vault is implicitly created, and no lifecycle/security policy is added.

## 4. Qualification inherited from previous milestone

The committed 0.1.4 repin report's human-operated physical API-37 private-mode
Gate result is PASS. Its disposable no-backup root used released
`NioTotipoStore.openPrivate` with default durability and exclusively serialized
writers. It exercised create, authenticate/reopen, core TOKEN publication,
1024-byte bounded read, AlreadyPresentExact, ExistingDifferent without mutation,
close/reopen fingerprint/state, password rewrap, old-password rejection,
new-password reopen and clean stage/canonical lifecycle.

This milestone changes dependency scope and resolves/gates a production root;
it does not change released storage algorithms or durability selection. No new
physical qualification was required or run. Inherited support is limited to the
tested private deployment assumptions; neither minSdk 26 nor arbitrary Android
filesystems are thereby physically qualified. Shared/default `open`'s hard-link
path was denied in the historical M1C run and remains unsuitable there. Local
qualification proves no SAF reconciliation, cloud durability or power-loss result.

## 5. Provider candidate abstraction

```text
private NIO canonical replica ↔ reconciliation bridge ↔ SAF transport candidates
```

Canonical means authoritative for local operations. Provider documents are
untrusted transport snapshots even when display names look canonical.
A generic candidate contains authority, selected tree identity, document identity
and URI/locator, display name, observed type/MIME, optional observed size/time/flags,
observation epoch/completeness, and bounded actual-byte result (complete snapshot,
missing, interrupted, oversized, unavailable). No provider-brand class is needed.
Identity is local bookkeeping, never OBJECT_ID, vault identity, authenticated
metadata or protocol authority. Bytes and observation status are distinct fields.

Model each directory as `display name → zero or more candidates`. M1B observed
ExternalStorageProvider preserving the existing file and suffixing a new one;
Drive preserved it and created another distinct ID with the identical name.
Complete enumeration of all relevant exact-name matches is required for absence
or exhaustive-candidate conclusions. One loading or failed directory query makes
that scope incomplete. Even a completed query is a finite view, not a transactional
snapshot or promise about hidden/cloud-future entries.

M1B also observed 1024-byte materialization, persistent 256-byte partial content,
stale size metadata, loading cursors, persisted grants and identities after
force-stop, and advisory sync flags. Proton's visibility in Files did not make it
selectable through ACTION_OPEN_DOCUMENT_TREE. No experiments are repeated here.

## 6. Immutable import

1. Enumerate all exact `objects-v1` directory candidates (section 11), then each
   relevant name matching the exact lowercase v1 OBJECT_ID grammar. Retain each
   candidate independently, with coverage status.
2. Read at most 1024 bytes plus an EOF/overflow probe from the actual stream into
   an owned snapshot. Exact representation length and successful EOF matter;
   reported size, MIME, flags and upload status do not. Interrupted/zero/256-byte
   reads do not contribute TOKEN state. Retry transient failures later.
3. When authentication capability is available, use existing core validation:
   filename/length, key/nonce/AAD, tag, semantic length, zero padding, keyed identity,
   exact TOKEN grammar and field rules, against the associated vault root. Locked
   candidates can be structurally filtered/staged privately but remain unauthenticated.
   No parser or cryptography is reimplemented in the bridge.
4. Group fully validated candidates by ID, retaining evidence of contradictory
   authenticated plaintext. Include already-local bytes in this comparison.
5. Under the root lease, re-observe local presence and publish the selected complete
   snapshot using private NIO's immutable publication contract. Only then request
   normal core observation on a safely owned/reopened session.

| Observed candidates | Local outcome |
| --- | --- |
| Zero fully authenticated valid TOKENs | Import no local fact; retain invalid/unavailable evidence |
| One valid TOKEN | Publish; exact-existing is idempotent |
| Multiple byte-identical valid copies | One object; no provider deletion required |
| Same authenticated canonical plaintext under same ID | One semantic object; preserve exact canonical representation |
| Distinct authenticated canonical plaintexts under same ID | Integrity/cryptographic contradiction; no arbitrary selection; exclude from normal evaluation |
| Valid plus malformed/partial siblings | Valid remains usable; preserve siblings |

With fixed root/ID, deterministic key/nonce/AAD and canonical padded plaintext,
v1's canonical AES-GCM representation is deterministic. Equal fully validated
canonical plaintext implies equal representation; semantic similarity of a
credential is insufficient (parents and optional metadata are part of exact P).
Unexpected byte inequality is not waved away using displayed credential equality.
`ExistingDifferent` alone does not prove a cryptographic contradiction: revalidate
local and remote snapshots. Never overwrite an immutable local target, including
an invalid one; surface a local repair condition if invalid canonical bytes block
publication. A genuine contradiction involving an already-local object requires
halting normal use of that identity/session and exposing the integrity condition,
not silently keeping the previously chosen copy. The integration must ensure
core does not continue presenting it as unchallenged valid state.

Incomplete enumeration cannot establish no valid candidates or no contradictions.
Individually validated facts can be imported monotonically from a partial view,
with observation explicitly incomplete; this does not promise an exhaustive or
fresh graph. Resource limits produce INCOMPLETE, not fabricated completeness.

**Released API integration boundary:** 0.1.4's public VaultSession does not expose
root keys or a standalone candidate-authentication/import method. Its parsers are
internal. A future read-only snapshot TotipoStore can present an isolated local
VAULT plus each candidate to existing `Totipo.open`, using a password supplied for
that foreground operation and waiting for finished core observation/diagnostics.
This store is a bounded private snapshot adapter, not a SAF-backed canonical store.
A candidate must be positively recognized as the matching valid revision, not
merely absence of an exception. Different-wrapper root checks similarly use
isolated opens and fingerprint comparison. Active-session validation without a
new password needs an upstream reviewed API or another reviewed integration;
never export K_root or retain a password to work around this. No such adapter/API
is implemented here. This is a next-milestone prerequisite, not a claim that
`publishObject` authenticates bytes.

## 7. Immutable export

Start with a complete local canonical object already accepted by core. Snapshot
its exact encrypted bytes under the lease; export never authors protocol state.

1. Observe the relevant directory collection and exact-name candidates completely
   enough for the conclusion. Boundedly compare actual bytes. Any exact copy of
   this known valid local representation suffices for EXPORTED_VERIFIED at that
   observation, even if another scope is unavailable; no claim of completeness
   follows. Absence requires complete relevant coverage. A known authenticated
   contradiction stops that identity's export rather than masking the conflict.
2. If no exact copy is observed and coverage is incomplete, remain EXPORT_PENDING
   or EXPORT_UNKNOWN and retry. If coverage is complete, durably journal an intent
   before calling createDocument for the canonical name in an eligible exact-name
   directory.
3. Persist the returned new document identity before writing whenever possible.
   Query identity/type/parent and actual resulting display name. If the name was
   suffixed or parent/name/type cannot be established, do not materialize. A
   suffixed object is outside the canonical transport grammar. Record blocked or
   unknown; do not write through a preexisting candidate instead.
4. For an established newly created exact-name candidate, stream the complete
   local bytes, attempt supported persistence/close, then boundedly reopen and
   compare the exact bytes including EOF. Drive-like duplicate exact names are
   allowed. A create response is not proof of atomic create-new or exclusive ID
   ownership; suspicious reuse/mutation aborts writes and cleanup.
5. Record EXPORTED_VERIFIED only after readback equals the snapshot. Exceptions,
   uncertain close, identity changes or interrupted verification yield
   EXPORT_UNKNOWN. Never infer success from metadata or sync flags.

Enumeration/create races can still produce duplicates or suffixes. No universal
SAF compare-and-swap, atomic replacement, create-new or durable fd sync is assumed.
Resume conservatively after rereading: a valid exact copy is done; a journaled
partial identity may be rewritten only with sufficient continuing ownership
confidence under the transport TCB assumptions. Otherwise create anew or stop
for repair. Never truncate a preexisting unowned document.

## 8. Journal and recovery

Design only: a minimal app-private no-backup bridge journal, outside the canonical
`totipo-vault` protocol directory, e.g. `totipo-bridge/`. It survives normal process
restart using a separately reviewed durable update scheme. Store a format version,
local association/fingerprint reference, tree identity, operation ID, canonical
name/ID, source encrypted-byte digest, intended parent, returned document identity,
actual name, state, materialization and verification milestones. VAULT operations
also record the exact source representation digest; a pending rewrap export must
not be confused with a later local wrapper. No password, root key or secret
plaintext; URIs/configuration stay private and never synchronize.

States: LOCAL_ONLY (no attempt), EXPORT_PENDING (work desired), EXPORTING (intent
or identified create in flight), EXPORTED_VERIFIED (exact readback observed),
EXPORT_UNKNOWN (ambiguous outcome). They are bridge bookkeeping, not protocol
states. Verification is historical evidence, not perpetual remote presence.
If source bytes have changed, do not silently resume with different bytes; either
retain the complete encrypted snapshot privately for the intended attempt or
mark it obsolete and create a new intent for the current local representation.

On restart validate journal structure and scope, then requery/re-read identities.
Missing journal records can often be reconstructed as byte-equality observations
from local state plus enumeration. Ownership cannot be reconstructed from names.
A digest is a convenience for snapshot tracking, not protocol authentication.
Corrupt/unknown journal states fail closed for mutation/cleanup and are recoverable
by re-observation. No provider mutation relies only on a recovered journal entry.

**Create-before-journal window:** an intent cannot include an ID not yet returned.
If create succeeds and death precedes durable identity recording, the resulting
candidate is unowned. If valid, authenticate/import it or recognize its exact
export bytes. If invalid/partial, preserve it. Drive-style duplicates permit a new
exact-name create and forward progress without cleanup. On a suffixing provider,
an unowned partial exact-name entry can permanently block canonical re-export.
Do not write the suffix, delete the blocker or claim ownership from name/digest
alone. Stop that export and require explicit user-directed repair of transport
state (move/remove the identified obstructing entry after review, or select a
new transport tree). The bridge provides no automatic unowned cleanup. A journal
may justify cleanup only under a trusted-provider identity continuity assumption;
ID reuse or inconsistent evidence removes that confidence.

**Review stop:** unattended forward progress on every filename-unique provider
is not achievable with these names and preservation rules. A unique attempt-name
layout could avoid this but is a transport extension requiring separate agreement,
consumer support and interoperability review. M1D proposes conservative explicit
repair, not that extension or a hidden garbage-deletion policy.

## 9. Provider observation states

| State | Meaning and allowed conclusion |
| --- | --- |
| COMPLETE | Successful relevant enumeration without loading; finite view only |
| INCOMPLETE_LOADING | Loading/limited/truncated view; cannot establish absence |
| UNAVAILABLE | Failed/null query, exception, locked provider or revoked grant; retry/reselect access later |

Candidate byte results independently distinguish complete exact-length snapshot,
oversized, short/invalid, interrupted, disappeared and unavailable. A directory
can be completely enumerated while a child read fails; that child remains unknown.
Do not classify an authentication failure as provider unavailability, or provider
unavailability as corruption. Revoked access affects transport only; locally
saved facts remain available subject to normal local unlock.

## 10. VAULT reconciliation

VAULT is a mutable password wrapper, not an immutable content-addressed object.
Each exact `vault` candidate is boundedly read at the v1 length (87 bytes plus
EOF probe). Structural validity is not successful authentication.

While locked: enumerate/read encrypted snapshots and compare exact bytes to the
local representation; identify transport byte duplicates and obvious length errors.
Do not infer roots or mark a well-formed but unopened wrapper invalid. No password
is retained solely for background reconciliation.

After the user successfully unlocks: the local session supplies expected stable
fingerprint association. An isolated candidate open with a user-supplied applicable
password can establish successful authentication and fingerprint. Failure with
one password cannot distinguish another password's valid wrapper from hostile
well-formed ciphertext. Label it authentication-unresolved, not different-root.
Multiple identical successfully opened bytes are transport duplicates. Multiple
successfully opened wrappers with the same fingerprint represent the same root,
subject to the protocol's negligible fingerprint-collision assumption. Fingerprint
recognizes association; it does not authenticate freshness or wrapper order.
For exact root equivalence where required, use a reviewed core capability rather
than exposing roots to the bridge.

During an active unlocked session: validated object import is possible only through
a reviewed core integration (section 6) and the same root gate. An unlocked root
alone does not necessarily decrypt another wrapper encrypted under an unknown
password. Local exact-byte matches need no extra password to be recognized as the
same representation. Session ownership does not authorize password-wrapper
replacement or bypass current-password reauthentication.

Successfully authenticated candidates with different fingerprints are different
vaults, never merged or automatically chosen. Keep the local association and stop
that tree's automatic reconciliation for association review; unknown wrappers
remain unknown. For first association with no local VAULT, a supplied password
may authenticate a candidate, but it cannot prove that all inaccessible siblings
share its root. User selection/confirmation of the recognized vault association is
required; creation is never implied by absence in an incomplete view.

The private local `vault` remains the representation used by local sessions.
No provider timestamp, ID, display order, sync state or version history replaces
it. Same-root remote wrappers can be presented as explicit recovery/adoption
choices after appropriate authentication and review, not automatic upgrades.
Any local adoption must compare exact local BASE/CURRENT immediately before
replacement under the owner, preserving the protocol replacement/persistence
contract. A fingerprint match cannot replace freshness comparison. Existing
0.1.4 APIs support password change, not arbitrary wrapper adoption; defer adoption
until that integration is reviewed. Authentication-unresolved entries never veto
use of a known local vault merely by looking plausible.

**VAULT export:** after successful local rewrap, journal/snapshot the new complete
local wrapper and create another candidate named `vault`, then materialize and
verify as above. Duplicate-capable transport can retain old and new wrappers.
This truthfully makes a new representation available but cannot establish a
unique latest password or revoke historical passwords. Clients that know only an
old password may still open an old wrapper. A suffixing provider cannot perform
this create-new export when `vault` already exists. Preserve the old entry; mark
rewrap export blocked for explicit repair. Never simulate provider atomic replacement.

V1 contains no wrapper generation/order or authenticated preference. Automatic
convergent wrapper selection is therefore deliberately undefined. If the product
requires automatic latest-wrapper propagation, this model alone is insufficient;
that requires a separately reviewed transport convention or protocol change.

## 11. objects-v1 directory handling

Exact-name directory candidates form another collection. Enumerate all direct
children of the selected root; include every exact `objects-v1` whose actual
provider type is directory. A same-name regular file is obstructing transport
evidence, not an objects directory. Query all exact directory identities and
union their object candidate collections. Do not select one for reads using ID
order, timestamp or first result. This handles duplicate directories safely
without protocol identity tied to a particular directory.

For export, an already verified exact copy in any directory satisfies availability.
For a new create, a locally remembered eligible directory can be preferred after
revalidation; otherwise choose a stable bridge-local ordering of observed eligible
identities. This is only placement policy; all clients still search all directories.
If no exact directory exists, only a complete root enumeration justifies creation.
Journal returned directory identity before further writes, verify actual exact
name/type/parent, then re-enumerate before concluding coverage. Loading means retry.
If creation returns a suffix, do not put canonical objects under it. Re-observe:
a concurrent exact directory may now exist. An unowned exact empty directory can
be searched/used as transport if genuinely a directory; its name does not imply
ownership or authorize deletion. Wrong-type blockers require explicit repair.

Bound traversal to root → exact directories → direct object candidates, with
visited identities to avoid aliases/cycles. Duplicate aliases can be deduplicated
by bridge identity for I/O, never for authentication. Resource budget exhaustion
marks incomplete coverage. Hostile duplicate floods can deny availability, not
justify missing-candidate assumptions. No provider-brand API is required.

## 12. Transport-layout verdict

**Immutable objects: EXISTING LAYOUT SUFFICIENT WITH BRIDGE INTERPRETATION.**
The names `objects-v1/<OBJECT_ID>` carry exact canonical representations through
candidate collections, including duplicate directories and objects. The bridge
projects authenticated facts into a single private canonical file per ID. Partial
siblings do not become protocol state. Suffixes remain outside this collection.
This is sufficient for safety and qualified progress, with explicit repair when
an unowned partial/wrong-type entry blocks exact-name export.

**VAULT: EXISTING LAYOUT SUFFICIENT WITH BRIDGE INTERPRETATION for authenticated
association and explicit wrapper selection only.** Multiple exact `vault` candidates
can carry same-root historical rewraps without changing v1 bytes. Each actual
TotipoStore still has exactly one canonical regular `vault`; the SAF collection
is not a conforming direct store. There is no automatic latest-wrapper convergence,
and suffixing providers block create-new rewrap export until explicit repair.

A shared candidate-transport contract must be reviewed/documented for participating
bridges. It need not alter normative v1 storage because only private replicas are
stores. If reviewers require all transports to be direct v1 stores, or transparent
interoperability with existing desktop cloud sync, this candidate collection is
insufficient. Unattended filename-unique recovery would need a TRANSPORT EXTENSION;
a cryptographically ordered/revoking wrapper model would need SPEC/STORAGE-LAYOUT
review. Neither is silently introduced here. These review gates prevent claiming
universal transport support from the physical private-mode PASS.

## 13. Desktop implications

Keep desktop NIO directly in its synchronized filesystem for services that present
the ordinary canonical filesystem layout, e.g. Syncthing. M1D does not require a
private-replica migration there. Existing publication and BASE/CURRENT contracts
still apply; conflict files and synchronization incompleteness remain external
storage observations, not new protocol semantics.

A Drive-like virtual provider collection is not necessarily representable as one
ordinary filename namespace. A desktop Drive mount/sync client may rename/drop
conflicting names or choose one `vault`. Existing desktop code will not discover
all mobile candidates or understand duplicate directory projection automatically.
Do not advertise this as transparent interoperability. Desktop access to such a
transport would require a compatible bridge or explicitly reviewed projection
contract. Immutable exact bytes can remain portable; wrapper selection and
namespace visibility are transport constraints. Mobile VAULT multi-candidate
export must remain disabled until that interoperability choice is reviewed.

## 14. iOS implications

The private canonical replica ↔ transport candidate architecture can generalize
to File Provider/security-scoped directories. Platform-specific bookmarks/locators,
access lifetime and observation status replace SAF IDs/grants in local bookkeeping.
Protocol correctness remains actual bytes, authentication and stable root
association; no Android identity is required. iOS would need its own private-store
filesystem/durability and provider qualification, serialization owner, bounded
reads, duplicate-name policy and recovery review. No implementation or platform
support is claimed here.

## 15. Security analysis

- The app-private replica and root owner are local TCB. Compromised app code, OS,
  root access or broken lease discipline can alter/delete canonical state or keys;
  private mode assumes such independent mutation does not occur. No sandbox can
  serialize compromised code voluntarily. No backup restoration continuity is claimed.
- Provider bytes are hostile. Bounded snapshots, strict framing and core
  authentication prevent malformed/partial content from becoming authenticated
  TOKEN facts. Valid siblings remain usable. No MIME, sync or metadata shortcut.
- Duplicate names/directories require exhaustive relevant queries for absence and
  contradictory-candidate analysis. Limits/loading/errors preserve uncertainty.
  A malicious provider can hide entries, equivocate, change bytes, repeat IDs,
  provide endless reads or floods and deny service. Bound reads/traversal and
  never translate observed completeness into guaranteed freshest history.
- Identity reuse makes journals unreliable ownership evidence. Recheck association,
  parent, name, type and bytes; distrust unexplained changes. Generic SAF cannot
  prove a malicious provider will honor identity or preserve others on create.
  Even journaled cleanup requires trusted identity continuity; preserving entries
  is safer when uncertain. This design cannot prevent provider-side destructive acts.
- Stale size/time/sync flags supply no validity or durability. Revoked grants and
  locked providers affect transport availability, not local vault integrity.
- Orphans/partial exports remain transport evidence; unowned state is preserved.
  Filename-unique obstruction is an explicit repair stop, not silent deletion.
- Different-root authenticated VAULTs are association conflicts, never merged.
  Locked authentication failures do not establish different roots or corruption.
- No new rollback/history protection: providers can omit/replay valid old objects
  and wrappers. Local remembered association detects different recognized roots,
  not latest wrappers or complete history. Old passwords may open retained wrappers;
  password change is not root rotation, revocation or compromise recovery.
- Private journal corruption is under local TCB assumptions. Defensive parsing and
  re-observation can fail closed/rebuild status, not restore authenticated authority
  or prove ownership. The journal never becomes synchronized protocol state.

Success meanings remain separate:

| Concept | Evidence |
| --- | --- |
| LOCAL_SAVE_SUCCEEDED | Core/private NIO local authoring acknowledgement |
| EXPORT_PENDING | Bridge has transport work remaining |
| PROVIDER_EXPORT_VERIFIED | Exact provider bounded readback at one observation |
| REMOTE_PROVIDER_SYNCING | Advisory provider report only |
| PEER_OBSERVED | Separate explicit peer evidence, absent here |

Only local save belongs to the authoring operation. Provider exceptions cannot
turn an acknowledged local save into failure. Future product messaging may say
“Saved” and “Sync pending”; no UI is implemented, no cloud/peer completion inferred.

## 16. Recommended next implementation milestone

Review the conservative repair requirement, VAULT selection limitations and desktop
transport contract first. The smallest safe next step is foreground, read-only
candidate enumeration/materialization into bounded private snapshots, including
all duplicate objects-v1 directories, with honest completeness reporting. Before
any canonical import, establish a reviewed released-core validation integration
that positively identifies valid candidates and propagates contradictions. Exercise
it with deterministic hostile/duplicate/partial fixtures. Then add immutable-only
imports through the same root lease. Keep VAULT replacement, provider writes,
journal persistence, background work and product screens outside that step.

No production ContentResolver bridge, provider journal persistence, scheduler,
WorkManager, picker, notifications, sync UI or long-lived unlocked session is added.

## 17. Validation

### Agent

- `./gradlew check :app:assembleDebug :app:assembleRelease`: PASS after restoring
  the unchanged buildscript lock; 90 actionable tasks (5 executed, 4 from cache,
  81 up-to-date).
- `./gradlew --offline --no-daemon --no-configuration-cache --no-build-cache
  --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug
  :app:assembleRelease`: PASS with the final locks; 92/92 tasks executed.
- Nine JVM tests, zero failures/errors/skips: one core smoke, six debug probe,
  two production-owner tests. Owner tests check stable path, rejection across
  threads, stale-lease closure, reacquisition and recovery after directory failure.
  No NIO filesystem semantics were mocked or requalified.
- Android lint and all four Maven-boundary configurations: PASS. Separate final
  debug/release runtime reports contain only NIO 0.1.4 → core 0.1.4 → BC 1.86
  plus lock constraints, with no redundant direct core edge.
- `python3 tools/verify-wrapper.py`: PASS.
- `python3 tools/verify-apk.py app/build/outputs/apk/debug/app-debug.apk`: PASS;
  extra `--debug-probe` check also PASS.
- `python3 tools/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk
  --unsigned --no-debug-probe`: PASS. NIO/core/BC present; debug helpers/Activities
  and test classes excluded; APK unsigned.
- Debug SHA-256: `21ec590292efe67204fbb45c1ed20bc3ed7ddd954e6d16a3f30ac2d1edafb492`.
  Release SHA-256: `4b845ee90de459f7c7f7819b4a62507746978102503b318f8379877aacd0d73f`.
- Existing core/NIO cache JAR/module/POM hashes match reviewed provenance;
  JAR/module hashes also match Gradle verification metadata. Those inputs remain
  byte-for-byte unchanged. No Nix command or cache refresh executed.
- `git diff --check`: PASS. New untracked files separately checked for whitespace
  and conflict markers. Cached diff empty.

Existing experimental AAPT2/deprecation notices remain. Lock regeneration initially
emptied an unrelated buildscript lock; it was restored from HEAD, then the full
strict offline check and ordinary check passed against final locks.

### Human Nix

The human reported both requested commands passed:

- `nix flake check path:.`: PASS.
- `nix build path:.`: PASS.

These are human-operated results; the agent executed no Nix command.
No manual Gradle rerun was required.

### Human device

None required or performed for M1D. Inherited finite API-37 physical qualification
is described in section 4; no new SAF or local-store qualification claim.

### Remote CI

Not triggered or run. Lightweight CI only updates APK expectations; no forced Nix
rebuild, duplicate offline Gradle, device tests or provider credentials added.

## 18. Final Git state

Final branch `main`; HEAD remains `3e5b7302b8c1515bde9be21eb5af71a90a6310c6`.
The agent ran all Git checks itself. `git status --short`:

```text
 M .github/workflows/ci.yml
 M README.md
 M TOTIPO_JAVA_DEPENDENCY.md
 M app/build.gradle.kts
 M app/gradle.lockfile
 M package.nix
 M tools/verify-apk.py
?? app/src/main/java/org/totipo/android/LocalReplicaOwner.java
?? app/src/test/java/org/totipo/android/LocalReplicaOwnerTest.java
?? review/M1D_PRODUCTION_LOCAL_REPLICA_DESIGN_REPORT.md
```

`git diff --stat` (untracked owner, test and report are not counted):

```text
 .github/workflows/ci.yml  |  4 ++--
 README.md                 | 21 +++++++++++--------
 TOTIPO_JAVA_DEPENDENCY.md | 47 ++++++++++++++++++++++++++----------------
 app/build.gradle.kts      | 52 +++++++++++++++++++++--------------------------
 app/gradle.lockfile       |  2 +-
 package.nix               |  2 +-
 tools/verify-apk.py       | 17 ++++++++--------
 7 files changed, 77 insertions(+), 68 deletions(-)
```

`git diff --check`: PASS. `git diff --cached --stat`: empty.
Nothing staged, committed, tagged, released or pushed. After recording the human
Nix PASS results, the agent repeated branch, HEAD, status, diff/check and cached
stat checks; the recorded Git state remains unchanged. M1D is complete; stop at
this report and design review before bridge implementation.
