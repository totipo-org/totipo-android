# M3B foreground outbound immutable-object publication

## 1. Starting state

Agent ran the required Git commands before editing. Branch: `main`; HEAD:
`e69c4f2d3d93547afb72ceace7e9e1c28a82449d` (`Add SAF binding and isolated inbound sync`).
The hardened M3A implementation was committed and the worktree was clean.

## 2. Scope and VAULT exclusion

M3B adds explicit **Publish local changes**, LOCAL → PROVIDER, for exact immutable
`objects-v1` representations. **Import changes** remains independently available.
The UI explains that token changes are exchanged and vault password-wrapper
synchronization is not enabled. This is not full vault synchronization. No VAULT
publication, adoption, reconciliation, background work, polling, provider repair,
bootstrap, cloud integration, or automatic retry was added.

## 3. Reused M3A architecture

Inspected the current ProviderIoLane, SyncFolderBinding, AndroidSyncFolderPort,
ProviderTreeReader, ProviderSnapshot, ProviderTraversal, BoundedProviderRead,
ImmutableCandidateClassifier, CoordinatedPrivateStore, LocalReplicaOwner,
ForegroundVaultCoordinator, AndroidVaultController, released SPI/NIO APIs, UI,
M3A harness and hardening tests. Publication uses the same controller vault worker,
owner lease, live session, coordinated store domain and sole ProviderIoLane.
No KDF, second private NIO store, close/reopen, or provider VAULT adoption occurs.

## 4–6. Exact local snapshot, validation and bounds

`CoordinatedPrivateStore.Bridge.snapshotObjects()` calls the existing delegate's
`scanObjects()` and bounded `readObject()` under the existing fair store gate.
It requires a complete SPI enumeration and reads canonical lowercase object names.
No private filesystem traversal or direct file read bypass exists in production.
The bridge releases before `ForegroundVaultCoordinator.outboundSnapshot()` calls
public Java `session.validateObject(id, exactBytes)` for every captured object.
Invalid evidence stops the entire batch with a fixed local-integrity status.
Validation never runs under the coordinated-store gate.

The detached set is bounded to **512 representations**, **1,024 bytes each**, and
**524,288 aggregate bytes**. The representation bound reuses the production
ImmutableCandidateClassifier constant; the same value controls production scan
and read-back. It cannot publish objects rejected by the current inbound path for
size. Count/aggregate overflow aborts the batch with a fixed capacity limitation;
there is no silent truncation. The existing SPI returns an enumeration list;
these limits bound detached ciphertext copying, without replacing NIO enumeration.
Detached byte arrays are defensively copied and exact-size checked.

## 7–9. Permissions, existing namespace and complete preflight

Button admission requires OPEN, inbound READY, persisted WRITE and no active
provider operation. Persisted READ-only folders remain READY and usable for
Import, with “Folder is read-only for Totipo.” Provider support for child creation
is separately established from `FLAG_DIR_SUPPORTS_CREATE` on the exact selected
`objects-v1` directory during preflight, without extra startup enumeration.
Absent create support does not downgrade inbound READY.

A fresh provider scan precedes every outbound plan. A complete view must contain
exactly one exact directory named `objects-v1`, with usable current metadata in
the bound tree. Zero directories disables publication; duplicate directories
block it. M3B never creates this directory: immutable objects alone cannot
bootstrap a usable vault while VAULT transport remains absent.

Incomplete, loading, unavailable or resource-limited coverage writes nothing.
Inbound can independently authenticate a safe object from incomplete coverage;
outbound must establish canonical-name absence before creating. This deliberate
asymmetry preserves hardened M3A admission and honest incomplete-view reporting.
Local-source failure can stop an action before any provider scan or mutation.

## 10–11. Planner and candidate semantics

`OutboundImmutablePlanner` performs no I/O. It consumes validated detached local
objects and the existing Java/classifier provider evidence, sorts by object ID,
and classifies ALREADY_PRESENT_EXACT, MISSING_SAFE_TO_CREATE,
BLOCKED_EXISTING_CANONICAL_CANDIDATE, INTEGRITY_CONTRADICTION and
VALIDATION_UNAVAILABLE.

Exact authenticated copies, including equal duplicates, cause no writes. A valid
provider representation different from the authenticated local representation,
or unequal authenticated provider siblings, is an integrity contradiction.
Any observed contradiction stops the whole batch. Existing invalid/unavailable
canonical evidence blocks its ID; unrelated missing IDs may proceed only with
complete provider coverage and no global contradiction. Canonical-name directory
rows also block creation, even though the existing immutable classifier excludes
directories from authentication. Exact plus invalid siblings preserves M1 rules.

## 12–15. Create-only transport and verification

`ProviderObjectWriter` separates mutation policy from the Android adapter. Only
ProviderIoLane invokes it. It receives detached encrypted bytes, current request
identity, current target metadata and a cancellation flag, with no session/store
capability. The matching persisted WRITE grant is rechecked before each create
and output open.

The adapter calls `DocumentsContract.createDocument` only for a RevisionId
filename and `application/octet-stream` beneath the existing directory. Returned
URI authority/tree identity and child relationship are verified using platform
APIs. Relationship verification requires API 29; lower platforms refuse mutation
before create (existing live-core qualification already requires newer Android).
Metadata is queried immediately, requiring exact canonical display name,
non-directory type and matching document/tree identities. A changed or suffixed
name receives no ciphertext, stops the batch and remains uncertain even if
another writer's exact object becomes visible. No automatic suffix retry occurs.

Only the newly returned, operation-local created document is opened for output,
with normal mode `w`. No scanned existing URI is opened for output. Exact bytes
are written without re-encoding or envelope; close and all IPC remain on the
provider lane. Existing bounded read logic performs same-URI exact read-back.
Failures stop further object mutations. No fsync/durability claim is made.

A separate fresh ProviderTreeReader postflight runs after attempted mutations
when the request remains current and provider scanning is practical. Immediate
read-back and API return values are not final publication proof.

## 16–17. Results and retry

Back on the vault worker, binding/session generations and current grants are
checked before postflight authentication through the same live session. Per-ID
verification distinguishes VERIFIED, INTEGRITY_CONTRADICTION, NOT_VERIFIED,
NOT_OBSERVED (complete absence), and UNCERTAIN (incomplete absence).
Only exact canonical authenticated postflight observations establish success.
Equal duplicates remain verified; contradictory valid representations do not.

“All local changes published” is never inferred from create/write/close returns.
The UI says “Local changes published” only when every planned missing object is
verified and no local ID remains blocked or relevant contradiction exists.
Partial batches preserve honest blocked/uncertain wording. An apparent write or
close exception may be superseded by exact authenticated final namespace evidence.
An explicit same-URI read-back mismatch/unavailability remains uncertain even if
postflight later contains exact bytes; a fresh manual preflight can then avoid
duplication. Unconfirmed mutation yields “Publication uncertain. Check again before retrying.”

No retry queue or automatic retry exists. Every later user Publish action captures
a new local set and runs fresh preflight. A previously uncertain object now exact
on the provider becomes ALREADY_PRESENT_EXACT, so no duplicate is created.

## 18–20. Lock, folder changes and lane bounds

Lock, disconnect, change-folder and shutdown set the outbound cancellation flag;
session and binding generations reject stale completions. Cancellation is checked
before each create, after create, before output and before another object starts.
Cancellation also skips unnecessary read-back/postflight work. Lock never awaits
provider IPC and stale completion never enters a closed/replacement session.
Folder replacement remains authoritative; its accessibility check can wait for
the occupied lane without blocking the configuration operation itself.

An already-started create/write/close may finish after Lock or disconnect. Remote
side effects cannot be rolled back, and a cancelled create can leave empty or
partial canonical evidence that M3B deliberately does not repair. No new object
mutation starts after cancellation observation. Correctness does not depend on
interrupting Binder. ProviderIoLane remains one worker with one bounded handoff
slot and one logical operation admitted at a time; Import, Publish and Retry
cannot accumulate while it is occupied. A permanently hung provider can require
process restart for further provider work, while local operations remain usable.

## 21–22. Local state and revealed TOTP

Outbound never requests a local refresh and never replaces authoritative local
state. It changes sync-specific status while global vault state stays OPEN.
Publish does not clear/re-render the current TOTP presentation. Hide, expiry,
Lock and actual local state replacement retain their normal behavior. Local token
authorship remains available during blocked provider work. Later objects are
outside the detached batch and are picked up by the next explicit Publish.

## 23–25. Hard mutation exclusions

M3B never writes provider `vault`, never creates `objects-v1`, and never opens a
pre-existing scanned document for output. No overwrite/repair, delete, rename,
move, remove, copy, temp filename, cleanup or rename-based commit was added.
Source audit guards pin the sole create/output platform adapter, canonical
RevisionId names, created-handle output, forbidden mutation APIs, absence of
outbound refresh/presentation replacement and honest UI wording.

## 26. Automated tests

Retains existing M1/M2/M3A tests. New tests cover empty/already-exact/missing sets,
equal duplicate and exact-plus-invalid semantics, invalid/unavailable/validation-
unavailable blockers, contradictions, incomplete/loading/unavailable coverage,
missing/duplicate/unsupported directories and read-only grants. Real coordinator
and controller tests exercise exact local validation, invalid canonical local
bytes, same store/session/owner domain, gate release before validation, explicit
snapshot bounds and defensive exact bytes.

Mutation tests cover null/throwing create, wrong/suffixed name without output,
output-open, partial-write and close failure, exact/different/unavailable read-back,
sequential stop on second failure, cancellation after create/inside write and grant
loss. Postflight tests cover exact/equal-duplicate/exact-plus-invalid verification,
contradiction, invalid canonical evidence, complete/incomplete absence and
postflight unavailability. Controller latches prohibit success before postflight.

Retry tests include an uncertain-but-persisted single object followed by fresh
preflight with zero new creates, and apparent close failure independently verified
through final evidence. Deterministic create/write latches exercise Lock (including
replacement-session unlock), disconnect and folder replacement, no second create,
no local refresh, local authorship during blocked IPC, repeated Publish/Import/
Retry rejection, one worker and zero accumulated queued tasks. Contradiction
fixtures use symbolic Valid values, following existing M1 tests; actual successful
publication/validation uses released Java-authored representations.

## 27–29. Device qualification, exact retry and cross-replica proof

Standalone `SafOutboundRegression`, built/signed/installed by
`tools/device/qualify-saf-outbound.py`, reuses the persisted local SAF test tree.
It first inspects actual persisted READ/WRITE grants. It records sanitized provider
inventory/hashes, imports the existing disposable baseline into an isolated reader,
clones that private baseline into another isolated writer and authors one public
test token through the production controller. The real user's vault is not opened
or modified to manufacture publication.

The production writer publishes its missing object(s), and assertions compare
provider bytes to exact writer private-store hashes, preserve every existing entry
and provider VAULT hash, permit only expected canonical additions, and exclude
suffix/temp/rename artifacts. Second Publish must report “No local changes to
publish” with unchanged inventory/hashes. The reader remains unchanged until
production M3A Import, then observes the writer token through the same live reader
session, without wrapper transport. Both isolated sessions are closed and private
fixtures removed. No intentional physical partial-write/corruption test is used.

Device execution results are recorded in the validation appendix below.

## 30–32. Drive, Syncthing and human interaction

Google Drive: **not tested; not required**. No installation, sign-in, credentials,
API, browser authentication or cloud SDK. Syncthing: no API integration, detection,
installation or qualification gate; a selected SAF directory may independently be
synchronized by it. Existing persisted READ + WRITE was confirmed on the connected
phone, so no folder reselection is required. Physical qualification requests no
manual fixture preparation and no real-vault mutation.

## 33–35. Supply chain, APK and validation responsibility

No dependency, lock, verification metadata, wrapper, Java/BC pin, manifest permission
or Nix package-deps change. Updated verify-apk retains prior boundaries, requires
the outbound production classes and rejects outbound fake/standalone machinery.
Broad storage, INTERNET, cloud SDK, DocumentFile, Syncthing and background frameworks
remain excluded. Signing reuses the installed qualification certificate and
preserves app data; credentials are not printed.

Agent validation: normal and required strict offline clean builds, wrapper check,
final debug/unsigned/signed release APK checks, standalone device harness and Git
checks are recorded below. Human Nix: **PASS**, both requested commands succeeded according to the human
confirmation. The agent did not run Nix. Human device: no requested steps when existing grant suffices.
Remote CI: not triggered. Nothing staged, committed, tagged, released or pushed.

## 36. Limitations

- No mutable VAULT transport or complete vault synchronization.
- Cannot bootstrap an empty provider folder; existing `objects-v1` is required.
- Publication has no transaction and cannot roll back remote effects.
- Already-started writes may finish after Lock/disconnect; partial evidence can remain.
- Provider close or immediate read-back is not remote durability proof.
- Incomplete preflight blocks all outbound creation.
- Existing corrupt canonical candidates are observed and blocked, never repaired.
- Uncertain writes require explicit fresh manual retry/preflight.
- Current local snapshot/provider scan/resource bounds limit very large vaults.
- Local DocumentsProvider qualification does not prove universal cloud-provider behavior.
- Relationship verification is unavailable below API 29; no unsafe fallback is used.

## 37. Recommended M3C boundary

M3C needs an explicit mutable-VAULT design review: namespace bootstrap, provider
VAULT publication, multiple candidates, password-wrapper lineage, concurrent
password changes, rollback/stale wrappers, local/provider authority, crash/partial
publication and user-visible adoption/reconciliation choices. M3B resolves none
of those questions and must not be marketed as full vault sync.

## 38. Final validation and Git evidence

Final command outputs and qualification results follow after execution.

### Final agent results

- Normal `./gradlew check :app:assembleDebug :app:assembleRelease`: **PASS**,
  59 seconds, 90 tasks (25 executed, 65 up-to-date).
- `./gradlew --offline --no-daemon --no-configuration-cache --no-build-cache
  --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug
  :app:assembleRelease`: **PASS**, 1m18s, all 92 tasks executed.
- Final JVM suite: **215 tests, zero failures/errors/skips** (194 retained tests
  plus 21 added test methods, several with multiple deterministic scenarios).
- `python3 tools/verify-wrapper.py`: **PASS**, Gradle 9.8.0 JAR/distribution pins.
- Final debug (`--debug-probe`), unsigned release (`--unsigned --no-debug-probe`),
  and signed device release (`--no-debug-probe`) APK verification: **PASS**.
- Python harness/build/verifier syntax compilation: **PASS**.
- Dependency/lock/verification/wrapper/Nix/manifest diff audit: **empty**.
- Final `git diff --check`: **PASS**. Index diff: **empty**.

An initial compile/lint/test pass exposed Android API compatibility, a test-fixture
reset assumption, and an outbound local-failure status being replaced by a probe.
These were corrected before the final validations. Subsequent review explicitly
kept read-back failure uncertain and retained postflight integrity validation for
changed-name failures. Results above describe the final sources and artifacts,
not exploratory builds.

### Final physical qualification

Existing persisted **READ and WRITE** grants were inspected in the target process
before publication. The harness checked the exact disposable folder display name
`Totipo-M3A-Test` and required its existing fixture baseline. No picker or permission
fiction was used. Final signed-release qualification: **37 checks PASS** on the
connected Pixel 6a local Android DocumentsProvider. Sanitized evidence is retained
in ignored `.gradle/m3b-saf/permission-inspection.txt` and `device-tests.txt`; runner
output is `.gradle/m3b-device-run.log`.

There were two successful qualification passes, each deliberately authoring one
additional public token in its own isolated writer. The first passed 36 checks;
after the final error-path adjustment, requalification passed 37 checks, using
four existing canonical objects as baseline and adding exactly one new 1,024-byte
canonical object. The provider now contains five disposable public fixture objects.
Every pre-existing file and the provider VAULT were unchanged in each pass. The
second Publish in each pass added zero documents and kept all inventory/hashes
unchanged. No suffix, temporary or rename artifact was observed; production source
and APK guards also exclude deletion/rename/overwrite paths.

The second isolated live reader initially observed the four baseline tokens;
after M3B publication it remained unchanged until explicit M3A Import, then observed
five tokens through the same session. Writer session identity and reader wrapper
bytes were retained. This demonstrates writer → SAF → M3A → reader, without
mutable VAULT transport. Isolated roots were closed/removed and the standalone
harness package was uninstalled. The installed signed Totipo release remains;
application data and persisted tree selection were preserved. No user's real
vault was used for token authorship.

Human device steps: **zero**. Google Drive: **not tested**. Syncthing: **not tested,
no integration**. Human Nix: **PASS**, the human confirmed that `nix flake check path:.` and
`nix build path:.` succeeded. These are human-reported results; the agent did not
run Nix. Remote CI: **not triggered**. M3B qualification is complete, including
the final human Nix checks. No staging, commit, tag, release,
push or CI action occurred.

Sanitized final provider observations (directory rows omitted):

| Phase | Canonical filename | SHA-256 |
| --- | --- | --- |
| before | `objects-v1/41535114e794c54b7add76dda49753b394d60622459b0bad4bd70ab21e8e7b8e` | `4973f3efcf87664a15dc3f2cbf3c1be8bee16375e51036840107db5012daf559` |
| before | `objects-v1/8f417177a602b3ca3d0d3ec9589355fd24ddd06a9bcabb7bd7cc75be59f17ee1` | `728be5610c81155336b851837d7b6e80ac74db326a8405f903aeb12131fb6780` |
| before | `objects-v1/911c8f2b51c0041d480014c64fdcd0a071245b8be0af289bbd025611c5b782e3` | `32d6fb5fa84fe277b487e884664145d0585e16d650a8dc6a3af6b290b4867f0b` |
| before | `objects-v1/f1ce43301558c8736a73b8fde1f95d8d8e86dbfdd3e0cddb18f1340852a57c9a` | `9ecd821cfc38938214c2360b83340a154d8f408be81186deb4fb3161effd335f` |
| before | `vault` | `69a4d78bd3ff7e598bea7faf184809c5881d459e36dccbf1ddef161499c3e7b5` |
| after | `objects-v1/36d7395c5d57f999d50cdc69171cd6d4e315bd05a7037d070cac85252c5104fe` | `e04f39f2c61b374773a2669a17b283801e2bb7e3be5584c8311e2929238ced33` |
| after | `objects-v1/41535114e794c54b7add76dda49753b394d60622459b0bad4bd70ab21e8e7b8e` | `4973f3efcf87664a15dc3f2cbf3c1be8bee16375e51036840107db5012daf559` |
| after | `objects-v1/8f417177a602b3ca3d0d3ec9589355fd24ddd06a9bcabb7bd7cc75be59f17ee1` | `728be5610c81155336b851837d7b6e80ac74db326a8405f903aeb12131fb6780` |
| after | `objects-v1/911c8f2b51c0041d480014c64fdcd0a071245b8be0af289bbd025611c5b782e3` | `32d6fb5fa84fe277b487e884664145d0585e16d650a8dc6a3af6b290b4867f0b` |
| after | `objects-v1/f1ce43301558c8736a73b8fde1f95d8d8e86dbfdd3e0cddb18f1340852a57c9a` | `9ecd821cfc38938214c2360b83340a154d8f408be81186deb4fb3161effd335f` |
| after | `vault` | `69a4d78bd3ff7e598bea7faf184809c5881d459e36dccbf1ddef161499c3e7b5` |

Final APK SHA-256:

| Artifact | SHA-256 |
| --- | --- |
| Debug | `ce151edd5c04ff7e06e4391b0f462185bd7860e2b3b7c08ca20afd4b09a6a9cb` |
| Unsigned release | `8d3831eb4109b60e45fd67d1aab4e98487f53e4e81f1e1f5cd63e5dd7c5ab490` |
| Signed device release | `277b39f5b9f9871e0bbbd47ce341f4bea25a8feec1f483a6503f33de08a0c754` |

### Final Git command outputs

Statistics below cover tracked modifications; new unstaged files are listed by status.

```text
$ git branch --show-current
main
```

```text
$ git rev-parse HEAD
e69c4f2d3d93547afb72ceace7e9e1c28a82449d
```

```text
$ git status --short
 M app/src/main/java/org/totipo/android/AndroidVaultController.java
 M app/src/main/java/org/totipo/android/MainActivity.java
 M app/src/main/java/org/totipo/android/provider/ImmutableCandidateClassifier.java
 M app/src/main/java/org/totipo/android/reconcile/CoordinatedPrivateStore.java
 M app/src/main/java/org/totipo/android/reconcile/ForegroundVaultCoordinator.java
 M app/src/main/java/org/totipo/android/sync/AndroidSyncFolderPort.java
 M app/src/main/java/org/totipo/android/sync/ProviderIoLane.java
 M app/src/test/java/org/totipo/android/AndroidVaultControllerTest.java
 M app/src/test/java/org/totipo/android/reconcile/CoordinatedPrivateStoreTest.java
 M app/src/test/java/org/totipo/android/reconcile/ForegroundVaultCoordinatorTest.java
 M app/src/test/java/org/totipo/android/sync/SyncSourceGuardTest.java
 M tools/verify-apk.py
?? app/src/main/java/org/totipo/android/sync/DetachedImmutableObject.java
?? app/src/main/java/org/totipo/android/sync/OutboundImmutablePlanner.java
?? app/src/main/java/org/totipo/android/sync/ProviderObjectWriter.java
?? app/src/test/java/org/totipo/android/sync/OutboundImmutablePlannerTest.java
?? app/src/test/java/org/totipo/android/sync/ProviderObjectWriterTest.java
?? app/src/test/java/org/totipo/android/sync/PublicationPort.java
?? review/M3B_OUTBOUND_IMMUTABLE_PUBLICATION_REPORT.md
?? tools/device/SafOutboundRegression.java
?? tools/device/build-saf-outbound-tests.py
?? tools/device/qualify-saf-outbound.py
```

```text
$ git diff --stat
 .../org/totipo/android/AndroidVaultController.java | 107 +++++++++++++++++-
 .../main/java/org/totipo/android/MainActivity.java |   5 +-
 .../provider/ImmutableCandidateClassifier.java     |   9 +-
 .../android/reconcile/CoordinatedPrivateStore.java |  23 ++++
 .../reconcile/ForegroundVaultCoordinator.java      |  32 ++++++
 .../totipo/android/sync/AndroidSyncFolderPort.java |  42 ++++++-
 .../org/totipo/android/sync/ProviderIoLane.java    |  10 ++
 .../totipo/android/AndroidVaultControllerTest.java | 122 +++++++++++++++++++++
 .../reconcile/CoordinatedPrivateStoreTest.java     |  29 +++++
 .../reconcile/ForegroundVaultCoordinatorTest.java  |  27 +++++
 .../totipo/android/sync/SyncSourceGuardTest.java   |  38 ++++++-
 tools/verify-apk.py                                |   3 +-
 12 files changed, 430 insertions(+), 17 deletions(-)
```

```text
$ git diff --check
(no output)
```

```text
$ git diff --cached --stat
(no output)
```
