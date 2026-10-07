# M1B Android storage-provider probe

## Status / executive summary

M1B is **complete for the probe/evidence scope of this milestone**. Human-operated
physical-device observations demonstrate materially different namespace behavior
across ExternalStorageProvider and Google Drive, exact complete private-stage
materialization on both, and retained Drive tree/document access and incomplete
materialization after force-stop. Proton Drive was visible in Files but not
selectable as a tree in this picker flow.

These observations support proceeding to provider-independent local-replica and
reconciliation design. They establish no universal SAF guarantee and no Totipo
protocol/storage-provider conformance. No storage architecture is implemented;
no TotipoStore, vault, TOKEN object, protocol/spec change, or upstream source change
is included. M1C has not begun.

## Starting state

The original implementation started on `main` at
`03505efd9f42d26f252202c4399f71fab5d0df31` with a clean working tree.

Before this evidence-completion edit, branch and exact HEAD were recorded again:

- Branch: `main`.
- Exact HEAD: `03505efd9f42d26f252202c4399f71fab5d0df31`.
- `git status --short`:

```text
 M tools/verify-apk.py
?? app/src/debug/
?? app/src/testDebug/
?? review/M1B_ANDROID_PROVIDER_PROBE_REPORT.md
?? totipo-debug-1.txt
?? totipo-debug-2.txt
```

All existing M1B implementation and diagnostic artifacts were preserved. This
completion edits only this report. No Nix command executed. Existing SDK/JDK
environment used; no dependency, lockfile, toolchain, or Gradle build configuration
change.

## Probe implementation

Debug-only files:

- `app/src/debug/AndroidManifest.xml`: separate exported launcher Activity,
  clearly labeled disposable storage probe. Existing bootstrap launcher remains.
  Backup disabled in debug; modern cloud/device-transfer rules exclude preferences
  so local URI/ownership state is not restored elsewhere.
- `app/src/debug/res/xml/storage_probe_extraction_rules.xml`: debug-only preference
  exclusions for Android 12+ cloud backup and device transfer.
- `app/src/debug/java/org/totipo/android/debug/StorageProbeActivity.java`:
  platform widgets, explicit disposable-tree confirmation, serial background
  provider operations, copied text report, private staging, persisted grant and
  exact-identity journal, explicit cleanup confirmation.
- `app/src/debug/java/org/totipo/android/debug/ProbeDocuments.java`: platform
  ContentResolver/DocumentsContract direct-child queries; no recursion or path
  inference. Null cursors fail as unavailable/unknown. Missing metadata remains
  unknown. Loading/error extras and a 10,000-row cap mark incomplete observations
  and prevent creation. Provider results can change between independent calls.
- `app/src/debug/java/org/totipo/android/debug/ProbeLogic.java`: pure deterministic
  pattern, SHA-256, flag decoding, exact-name counting, identity checks, formatting.
- `app/src/testDebug/java/org/totipo/android/debug/ProbeLogicTest.java`: JVM tests
  for these pure decisions; no ContentResolver mocks.
- `tools/verify-apk.py`: optional `--no-debug-probe` DEX exclusion assertion.

No release/main source or manifest changed. No broad storage permission added.
The debug Activity relies exclusively on picker-selected tree permissions and
provider-owned authentication UI. No provider brand or vendor-specific API in
logic. Package/version lookup is best-effort; Android package visibility may
prevent it. Optional installed-provider discovery was not needed.

Raw selected URI and exact document IDs/URIs are retained only in app-local debug
preferences to support restart access and cleanup. Report IDs use full SHA-256
aliases so equality remains comparable without exporting account/path identifiers.
Tree display name is similarly redacted. Generated requested names and conservative
numeric/space suffixes can appear verbatim; other returned names are hashed. No
unrelated sibling names/IDs are included: enumeration reports aggregate counts
and metadata for recorded probe-owned identities only. Exception class is reported,
but provider exception messages/error strings are omitted because they may expose
paths/account information. Reports are retained across restart, bounded to 180,000
characters (older observations can be truncated). Copy each meaningful batch.

Selecting a different tree starts a new report/ownership journal; copy evidence
first. Previous artifacts then require manual deletion of that disposable test
directory. No prefix-based cleanup exists. The same selected tree retains its
journal across Activity/process restart. Clearing app data/uninstalling loses it.

## Experiment semantics

Platform references reviewed:
[DocumentsContract](https://developer.android.com/reference/android/provider/DocumentsContract)
and [Document metadata/flags](https://developer.android.com/reference/android/provider/DocumentsContract.Document).
The probe measures observations separately from the advertised API capabilities.

### Select and show tree/provider

`ACTION_OPEN_DOCUMENT_TREE` requests read/write/persistable/prefix access. Returned
read/write flags are recorded; persistable permission is taken only when offered.
The stored tree is loaded on launch without contacting the provider automatically.
Info records authority, redacted tree URI and root ID/name, MIME, actual process
read/write permission checks, matching persisted grant state, observable package
and version, and root document flags. Optional API-37 content-sync-state flags are queried
separately on root and owned reads, decoded if supplied, and unknown/unsupported
if not; they remain provider-advertised state, not cloud durability evidence. All 18 public document flag bits (including trash/restore) through
API 37 are decoded; unknown bits are retained in hex. Every flag block says
**provider-advertised capability**. Flags do not establish atomicity, exclusivity,
name uniqueness, durability, remote availability, or synchronization completion.

### Direct children

A single root child query observes rows without recursing. Each known owned child
reports redacted ID, sanitized display name, MIME, optional size and flags. Unknown
metadata, loading/error extras, null cursor and exceptions remain unknown or
unavailable. Exact name comparison is case-sensitive and permits duplicates.
Counts refer to cursor rows; distinct IDs are compared separately. Enumeration
and subsequent operations are not a transaction. The selected directory must be
disposable, quiet, and dedicated to these experiments. Missing/changing children
cannot by themselves establish corruption or causation.

### Basic create

A fresh `totipo-probe-create-<UUID>` name must be absent in a complete pre-query.
`createDocument` is called with `application/octet-stream`; the returned ID, actual
name, MIME and flags are queried, and the parent queried again. The probe records
name equality, exact-name match count, new-ID observation, returned-ID row count,
and whether prior IDs remain. This demonstrates behavior in this observation,
**not atomic create-new semantics or a universal provider guarantee**. The initial
document is intentionally empty; reading it cannot establish preservation of
nonempty original contents. The conflict experiment never writes to it.

A returned existing ID/different authority, vanished prior ID, mismatched metadata
identity, or duplicate rows for the returned ID halts mutations for design review.
These can represent reuse/replacement or changing/ambiguous evidence; no workaround
is attempted. If the new ID cannot be observed as a direct child, or metadata/query
fails before ownership is journaled, no write/delete is attempted; manually inspect
and delete the disposable directory if needed. A failed create call may have had
side effects: errors are not proof that nothing was created.

### Same-name conflict

Requires the original created ID still present and its actual name equal to the
requested name. Queries and bounded reads capture original metadata/content where
possible. Calls create again with exactly that requested name, with **no intentional
write to the existing original**. Re-enumerates, counts exact matches, lists matching
redacted IDs, compares new/old IDs and re-queries/re-reads the original. Records
rejection/error/null result, altered name, distinct identical-name document, or
reuse/replacement/unknown. Provider rejection and an availability failure can be
indistinguishable; exception class is evidence, not automatic classification.

If original identity/name/content changes or disappears, mutation is halted for
review. External changes/provider refresh may confound causation. Two distinct IDs
with identical names are detectable in that snapshot; this does not prove reliable
future canonical resolution under all races/pagination/offline conditions. No
outcome is automatically labeled safe/unsafe.

### Complete private-stage materialization

Writes the deterministic 1024-byte representation in app-private no-backup storage,
flushes, attempts local FD sync, closes, re-reads locally, checks bytes and computes
SHA-256 **before any provider destination is created**. After absence observation,
creates a destination and only writes if a new direct-child ID is established,
ownership persisted, and actual name equals requested. Copies bytes from the closed
private stage using a fresh private input stream, closes provider output, reopens
provider input, reads boundedly to EOF, hashes and compares exact bytes.

Provider FD sync is attempted and success/failure recorded without making it a
prerequisite. Failure can reflect a pipe/virtual descriptor or another I/O error;
the probe does not invent a more precise explanation. **FileDescriptor.sync !=
cloud sync completion**, remote durability, peer delivery, crash consistency, or
power-loss survival. Reading back measures provider-visible bytes at that time.
Reads have a 4096-byte bound and fail explicitly if exceeded; no unbounded content
export. Provider operations may block; there is no fabricated timeout guarantee.

### Controlled incomplete materialization

Stages the same complete private representation first. Creates a separate fresh
`totipo-probe-partial-<UUID>` document, writes only 256 bytes, then intentionally
closes. Enumerates and reads/hash-compares against both the known prefix and full
stage. Relaunch + reopen repeats metadata/enumeration/reads of recorded artifacts.
This shows how a deliberately incomplete new document appears now and after a
restart. It is **not a crash or power-loss test**. No process-kill simulation is
implemented. The human force-stop recorded below **!= power loss**.

### Persisted access and provider lock/unavailability

The explicit reopen action reloads preferences, checks grants/provider metadata,
enumerates root, and boundedly re-reads each journaled probe artifact without the
picker. Reports include process-session UUID and timestamp; relaunch alone need
not create a new process, while a force-stop followed by launch does. Persisted
grant presence, provider availability, and document accessibility are independent
observations. Grant state can remain valid while a provider is unavailable.

The same harmless action can be rerun with provider lock enabled then unlocked.
No vendor APIs/security weakening are required. A root/query failure can end that
operation before document reads; record the failing operation and exception class.
**Provider unavailability != vault corruption**. Lock-related attribution requires
human confirmation of the provider's lock state and paired observations.

### Cleanup

An explicit confirmation deletes only exact URI/ID pairs recorded after creation
of a newly observed direct-child identity in this invocation. Metadata identity
must still match; directories/root are refused and delete must be provider-advertised. Unsupported/missing capability
or failed deletion leaves the disposable tree for manual deletion. Never deletes
selected tree, unowned siblings, or files based on prefix. Suspicious replacement
halts cleanup too, to preserve evidence. Bookkeeping cannot protect against a
malicious provider reassigning opaque IDs; no universal identity guarantee claimed.

## Provider results

Evidence label for every result below: **Human-operated physical-device observation**.
The agent incorporated supplied observations; it did not operate or inspect the
device. The probe reported SDK 37 after restart. Device model and Android OS version
were not supplied and are not inferred. These are finite provider observations,
not universal platform or provider guarantees.

### ExternalStorageProvider

Authority: `com.android.externalstorage.documents`.
Package: `com.android.externalstorage`. Provider version was not supplied.
The human selected an ordinary/shared local directory. `ACTION_OPEN_DOCUMENT_TREE`
succeeded with read/write and persistable grants; the grant was successfully
persisted. The root supported child creation. No broad storage permission was used.

**Absent-name create:** a complete enumeration showed the requested name absent.
`createDocument()` returned a new unique document ID with exactly the requested
display name. The following enumeration contained exactly one exact-name match,
retained existing IDs, and the returned document was readable.

**Complete private-stage materialization:** the full 1024-byte private stage was
written and its `sync()` succeeded before provider creation. Its SHA-256 was
`8d7e566766f6bd1bb4cac87cadfde681197f9243f4d2692a0fd12674092212a7`.
The provider created a new unique document with the exact requested name; 1024
bytes were written and provider fd `sync()` succeeded. Reopening returned exactly
1024 bytes with the same SHA-256. Exact equality and full-stage equality were true.
This is provider-visible byte equality, not remote/cloud durability proof.

**Same-name conflict:** with one document already under the requested name, a
second create returned a new unique ID with display name `<requested name> (1)`.
Requested and actual new names differed; exactly one exact-name match remained.
The original document ID remained present and its bytes unchanged. Observed outcome:
`different display name returned`. This is filesystem-like conflict handling in
this observation: the existing exact name was preserved and the new provider
document received a distinct/suffixed name. It is not a universal
ExternalStorageProvider guarantee.

Controlled incomplete materialization and persisted document identity after
force-stop were not tested on this provider. Loading/transient incomplete views
were not observed.

### Google Drive

Authority: `com.google.android.apps.docs.storage`.
Package: `com.google.android.apps.docs`.
Observed installed provider version: `2.26.397.0.all.alldpi`,
`versionCode 214669724`. This is evidence metadata, not a project dependency.

**Tree grant:** Google Drive appeared in `ACTION_OPEN_DOCUMENT_TREE` and a disposable
Drive directory was selectable. Read/write and persistable read/write grants
succeeded. The root advertised create/rename/move/delete capabilities; these are
provider-advertised capabilities, not atomicity or durability guarantees.

**Initial loading:** on the first create attempt, direct-child enumeration returned
no children with `loading=true`; the create operation resulted in `IOException`.
A later enumeration had `loading=false` and the operation succeeded. No more
precise cause of the IOException is established. A loading/incomplete provider
view must not be treated as authoritative absence.

**Absent-name create:** the successful attempt had zero exact-name matches before
creation. `createDocument()` returned a new unique ID with the exact requested
name; after enumeration had one exact-name match and retained existing IDs.
The returned document was accessible and initially empty. Immediately after
creation its content sync state reported `availableLocally=true` and
`uploadProgress=true`; these are advisory provider state only.

**Same-name conflict:** with one existing document under the requested display name,
the second create returned a different new unique ID with exactly the same
requested display name. Enumeration then contained two exact-name matches with
different IDs. The original ID remained present and its bytes unchanged. Observed
outcome: `second distinct ID with identical display name`. This is a valid observed
namespace behavior, not corruption or a provider error. The observed namespace is
`display-name -> set of provider document identities`, rather than a filename-unique
mapping. **The replication layer cannot use display name alone as persistent
provider identity.** This does not establish permanent behavior for all Drive
directories or provider versions.

**Complete private-stage materialization:** a complete 1024-byte private stage
existed and private-stage `sync()` succeeded before destination creation. SHA-256:
`8d7e566766f6bd1bb4cac87cadfde681197f9243f4d2692a0fd12674092212a7`.
Drive returned a new unique document ID with requested name equal to actual name.
All 1024 bytes were written; provider fd sync succeeded and output closed.
Reopening succeeded and returned 1024 bytes with the exact private-stage SHA-256;
`exactExpectedEquality=true` and `fullStageEquality=true`.
Observed sync state included `availableLocally=true`, `localChanges=true`, and
`uploadProgress=true`. This establishes no cloud durability or remote convergence.

**Controlled incomplete materialization:** the complete 1024-byte private stage
existed first. A new Drive document was created under the exact requested name,
but only the first 256 bytes were written. Provider fd sync succeeded and output
was intentionally closed. Immediate bounded read returned exactly 256 bytes,
matching the expected prefix; full-stage equality was false. An immediate document
metadata read reported `size=0`, while later parent enumeration reported the same
document at `size=256`. Provider size metadata must not be treated as authoritative
content validity; actual bounded reads are required. The partial object remained
a normal exact-name document. This was an intentional incomplete write, not a
crash or power-loss test.

**Force-stop / persisted-access recovery:** after the partial test the human
force-stopped and relaunched the application. No tree picker was used again. The
stored tree URI loaded, persisted read/write grants remained valid, and the Drive
directory reopened successfully. The probe reported SDK 37 after restart.

- Both distinct same-display-name IDs remained separately present and readable.
- The complete document retained its recorded provider identity, remained
  accessible, and returned 1024 bytes with SHA-256
  `8d7e566766f6bd1bb4cac87cadfde681197f9243f4d2692a0fd12674092212a7`.
- The journaled partial document retained its identity and remained present.
  Enumeration reported size 256; a bounded read returned exactly 256 bytes with
  expected prefix SHA-256
  `c8c6e02d597fa6c407a5fec30c981c7bbad08972240eea89841b8f37e2fbf32c`.
  `exactExpectedEquality=true`; `fullStageEquality=false`.
- Later partial-document sync state was `availableLocally=true`,
  `localChanges=false`, `uploadProgress=false`, `downloadProgress=false`,
  `uploadError=false`, and `downloadError=false`.

Persisted SAF grant plus locally retained provider document identity could
rediscover/read the same incomplete provider object after process restart. This
is empirical recovery evidence, not crash-consistency proof. Force-stop is not
power loss; the later sync flags prove neither remote peer observation nor cloud
durability.

### Proton Drive

Proton Drive was visible in Android Files browsing but did not appear as a
selectable provider/location in the Totipo `ACTION_OPEN_DOCUMENT_TREE` flow.
This is solely a negative tree-selection observation: Proton Drive was not
currently usable through this persistent-tree bridge path in this observation.
No Proton mutations were performed; no further provider behavior is inferred.

## Cross-provider comparison

All entries describe the human-operated observations above.

| Property | ExternalStorageProvider | Google Drive |
| --- | --- | --- |
| Tree selectable | yes | yes |
| Persisted R/W grant | yes | yes |
| Create absent name | exact requested name | exact requested name |
| New unique provider ID | yes | yes |
| Existing same-name preserved | yes | yes |
| Conflict behavior | suffixed new name | duplicate exact display name |
| Display-name uniqueness | observed unique | explicitly observed false |
| Complete private-stage materialization | exact 1024-byte equality | exact 1024-byte equality |
| Provider fd sync | succeeded | succeeded |
| Incomplete exact-name document observable | not tested | yes, 256 bytes |
| Persisted identity after force-stop | not tested | yes |
| Provider loading/transient incomplete view | not observed | observed |
| Universal guarantee established | no | no |

Proton Drive: visible in Android Files; not selectable in
`ACTION_OPEN_DOCUMENT_TREE` in this observation; therefore not currently usable
through this persistent-tree bridge path.

## Architecture implications

M1B provides empirical support for further investigation of **app-private
canonical replica + provider replication/materialization bridge**. It does not
justify implementing SAF as the authoritative `TotipoStore`. The architecture
has not been implemented and no protocol/spec changes are proposed.

Provider namespaces must be treated as candidate collections, not necessarily
unique filename maps. **Display name != stable unique provider identity**: Drive
permitted two distinct documents with identical display names. Provider document
IDs/URIs may be useful local journal identities, subject to provider validity and
grant revocation; they must not become protocol identity. Enumeration and create
remain separate operations, with no universal/racing create-new guarantee.

### Immutable-object direction

The evidence supports investigating a bridge that may:

1. Construct and retain complete canonical Totipo representations privately.
2. Create a new provider document.
3. Retain the returned provider identity locally.
4. Materialize exact bytes.
5. Verify by bounded read.
6. Treat incomplete materializations as invalid/unavailable transport candidates.
7. Enumerate all exact display-name matches where duplicates are permitted.

Duplicate exact-name entries need not imply duplicate protocol objects. Actual
bytes must be validated; multiple identical valid transport copies could
potentially collapse locally. Distinct authenticated content under the same
Totipo identity remains a serious integrity condition. These are design directions,
not final reconciliation semantics; M1C must design them.

### Orphan creation window

Recovery was demonstrated when provider identity had been journaled before
interruption. A theoretical unresolved window remains:

```text
createDocument succeeds
provider identity returned
process dies before local journal is durably recorded
```

The experiment did not test that exact timing. An unjournaled incomplete provider
candidate must not automatically be deleted or rewritten. M1B does not solve this;
it is an M1C design question.

### VAULT

M1B did **not** test Totipo `vault`. Immutable-object transport rules cannot be
assumed to apply unchanged to VAULT. Drive's observed namespace permits a scenario
such as `ID A -> "vault"` and `ID B -> "vault"`, which could contain semantically
different valid bootstrap representations; no such Totipo representations were
tested here. M1C must separately design initial VAULT transport, replacement,
multiple provider candidates, same-root password rewrap versus different-root
candidates, and import/reconciliation into one private canonical local replica.

### Provider sync-state interpretation

`DocumentsContract` content sync-state flags are useful diagnostics/status hints
only. They are not protocol validity, durability proof, remote convergence proof,
peer observation proof, or authorization to import malformed/incomplete bytes.
Upload progress while an empty/new document was visible, upload/local-change state
during complete and partial materialization, and later locally available/stable
state after restart show that provider synchronization lifecycle and Totipo
validity are separate.

## Unresolved design questions

M1C must address these questions separately; no design or implementation begins here:

- Provider duplicate exact names and complete candidate enumeration.
- Incomplete candidate handling and byte validation despite stale metadata.
- Orphan create-before-journal window and ownership uncertainty.
- Export resume/retry strategy.
- Immutable candidate reconciliation, identical copies, and integrity conflicts.
- VAULT candidate/replacement reconciliation, including same-root rewrap versus
  different-root bootstrap candidates.
- Provider acknowledgement semantics distinct from local readback, remote
  convergence, and peer observation.

Provider lock behavior can remain a future usability test; it is not required for
M1B completion. Universal SAF guarantees, crash/power-loss behavior, remote/cloud
durability, sync completion, peer visibility, and protocol fitness remain unproven.

The provider test directories/files are disposable and can now be deleted manually
by the user. The probe's constrained cleanup remains available but is not required
for completion. No automatic provider cleanup was performed for this report.

## Validation

### Agent non-device

Final evidence-completion validation rerun in the existing environment:

- `./gradlew check :app:assembleDebug :app:assembleRelease`: PASS;
  `BUILD SUCCESSFUL`, 89 actionable tasks (5 executed, 84 up-to-date).
- Probe JVM and core smoke test tasks were up-to-date; their XML results contain
  seven tests (six probe, one core), zero failures, errors or skips. Includes
  API-37 sync-state decoding.
- Android lint: PASS; no baseline/config relaxation. Synchronous preferences are
  intentional on the worker before mutation. Optional sync-state uses an inlined
  column name, not an API-37 runtime method; unsupported query is caught separately.
- Debug/release Maven boundary and locked build dependency verification: PASS.
- `python3 tools/verify-apk.py app/build/outputs/apk/debug/app-debug.apk`: PASS.
- `python3 tools/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk
  --unsigned --no-debug-probe`: PASS. Release SHA-256:
  `fc67b48e63db8effbb12a97daab0a313b0829385b85704dd33fb85e2743c59ca`.
  The release DEX still excludes all `org/totipo/android/debug/` diagnostic class
  descriptors, including the probe Activity and helpers.
- `git diff --check`: PASS, empty. Untracked report/debug sources/tests were
  also checked directly for trailing whitespace and conflict markers: PASS.

Previously recorded implementation verification, retained here:

- AAPT2 inspection of packaged manifests: debug has both launcher Activities;
  release has only `org.totipo.android.MainActivity`. Neither declares permissions.
  Debug extraction resource is present only in debug APK; all diagnostic class
  descriptors are absent from release DEX. Release remains the bootstrap product.
- Installed `android-37.0/android.jar` constants inspected with `javap`: 18 document
  capability bits and six optional content-sync-state bits verified.
- Source review: no main/release/upstream/Gradle dependency changes, broad permission,
  filesystem-path inference, recursive traversal, vendor API, or vault API calls.

Provider behavior and physical-device UI evidence is human-operated, as recorded
above. Gradle emits its existing experimental AAPT2 override/deprecation notices;
no toolchain change made. The wrapper fetched its pinned Gradle distribution in
the existing environment; no Nix command or project configuration change was
needed. Only this report was edited during evidence completion.

### Human device

Human-operated physical-device observation: ExternalStorageProvider tree/grants,
absent-name create, same-name conflict, and complete private-stage materialization;
Google Drive tree/grants, initial loading failure and later success, absent-name
create, duplicate exact-name conflict, complete and controlled incomplete
materialization, and force-stop/relaunch persisted tree/document recovery;
Proton Drive negative tree-selection observation. Details and limits are recorded
in Provider results. No agent-executed device tests or device inspection claimed.
Provider lock/unlock and power-loss tests were not performed.

### Human Nix

None requested or recorded. Agent executed no Nix command.

### Remote CI

Not run. No commit/push to trigger it.

## Final Git state

`git status --short`:

```text
 M tools/verify-apk.py
?? app/src/debug/
?? app/src/testDebug/
?? review/M1B_ANDROID_PROVIDER_PROBE_REPORT.md
?? totipo-debug-1.txt
?? totipo-debug-2.txt
```

`git diff --stat`:

```text
 tools/verify-apk.py | 3 +++
 1 file changed, 3 insertions(+)
```

Untracked new sources/tests/report are not counted by ordinary diff stat.
`git diff --check`: PASS, empty. New untracked files also checked directly for
trailing whitespace and conflict markers. `git diff --cached --stat`: empty.
Final branch: `main`. Final exact HEAD:
`03505efd9f42d26f252202c4399f71fab5d0df31` (unchanged).
Nothing staged, committed, tagged, released or pushed.

M1B is complete. The debug probe demonstrated materially different namespace
behavior across a filesystem-backed Android provider and Google Drive, successful
complete private-stage materialization on both, persistent Drive tree/document
identity across force-stop, and persistent visibility of incomplete materialization.
Proton Drive was visible in Files but not selectable as a tree. These observations
support proceeding to a provider-independent local-replica/reconciliation design,
while establishing no universal SAF guarantee and no Totipo protocol/storage-provider
conformance. Stop here; M1C has not begun.
