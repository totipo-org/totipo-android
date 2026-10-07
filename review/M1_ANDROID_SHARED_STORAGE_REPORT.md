# M1 Android shared-storage investigation

Investigation date: 2026-10-06. **Decision: no generic Android store yet.**
Outcome C: SAF provides portable access and observation mechanisms, but its public
contract does not establish all required publication and persistence semantics.
Stop for design review before production implementation or provider mutation tests.
M1 end-to-end qualification is **not complete**. No real-provider success is claimed.

## Starting state

- Branch: `main`.
- Exact HEAD: `cdcd2a2b534d0803da9ac4845bb5ac9f7500154e`.
- Initial `git status --short`: empty (clean); recorded before any writes.
- M0 baseline: single Java `:app`, min API 26, compile/target API 37,
  platform bootstrap Activity, released `org.totipo:totipo-core:0.1.3`, BC 1.86.
- Read `review/M0_ANDROID_BOOTSTRAP_REPORT.md`,
  `review/POST_M0_CI_SIMPLIFICATION_REPORT.md`, `README.md`,
  `TOTIPO_JAVA_DEPENDENCY.md`, and `app/build.gradle.kts`.
- M0 human evidence establishes bootstrap rendering only, without a recorded
  device API/provider. Post-M0 report leaves human Nix and remote CI pending;
  this investigation does not upgrade those claims.
- No applicable `AGENTS.md` found in the source parent tree outside cached archives.

## Sources inspected

### Totipo releases and exact revisions

| Source | Inspected revision and scope |
| --- | --- |
| [totipo-java](https://github.com/totipo-org/totipo-java/tree/e2326aca5f5aac661d74925a30fcc57cd91014e8) | `v0.1.3`, commit `e2326aca5f5aac661d74925a30fcc57cd91014e8`; also current upstream HEAD when checked through GitHub API |
| [Java SPI design](https://github.com/totipo-org/totipo-java/blob/e2326aca5f5aac661d74925a30fcc57cd91014e8/SPI_DESIGN.md) | Implemented released API contract, not normative wire specification |
| [Released SPI types](https://github.com/totipo-org/totipo-java/tree/e2326aca5f5aac661d74925a30fcc57cd91014e8/core/src/main/java/org/totipo/spi) | All types including `TotipoStore`, `PreparedVault`, read/scan/write results and failures |
| [Core store adapter](https://github.com/totipo-org/totipo-java/blob/e2326aca5f5aac661d74925a30fcc57cd91014e8/core/src/main/java/org/totipo/format/StoreAdapter.java) | Public `Totipo.create/open`, `ApplicationVaults`, lifecycle workflows and adapter inspected; provider boundary has no Path requirement |
| [NIO reference](https://github.com/totipo-org/totipo-java/tree/e2326aca5f5aac661d74925a30fcc57cd91014e8/storage-nio) | Store, reads, scan, namespace, object publication, vault staging and durability engines; reference only |
| Java qualification | `README.md`, `API_DESIGN.md`, `SPEC_PIN.md`, `review/STORAGE_SPI_REPORT.md`, `review/V1_R18_REPIN_REPORT.md`; historical report uses pre-migration package names, current source uses `org.totipo` |
| Java tests | `NioSpiTest`, `NioPublicationTest`, `NioVaultStorageTest`, `NioDurabilityTest`, `NioExactNameTest`, core storage/workflow tests; read as evidence of intended outcomes, not rerun or Android qualification |
| [Spec release-consumer pin](https://github.com/totipo-org/totipo-spec/tree/4623a7e1718e23504903096c92332597057bd8f0) | `4623a7e1718e23504903096c92332597057bd8f0`; v1/r18 committed pre-RC revision, no r18 release tag |
| [Current spec](https://github.com/totipo-org/totipo-spec/tree/91b6ed6bf01c3f45cb166082ffe72e92291be3a4) | `91b6ed6bf01c3f45cb166082ffe72e92291be3a4`, confirmed current upstream HEAD; normative §§2, 3, 7, 8, 18, 20, requirements profile and hardening report inspected |
| [Current native-platform guidance](https://github.com/totipo-org/totipo-spec/blob/91b6ed6bf01c3f45cb166082ffe72e92291be3a4/docs/design/DESIGN.md) | Non-normative design v0.9: native platform conventions, §§10–11 chooser/access reference; no SAF publication recipe or exception to protocol requirements |

Cached upstream archives were verified against GitHub recursive-tree Git blob
identities: all 313 Java and 167 current-spec files matched, with none missing.
Archives are inspection material under ignored `.gradle/m1-inspection`, not build
inputs or source substitution. The Maven Central [core sources JAR](https://repo.maven.apache.org/maven2/org/totipo/totipo-core/0.1.3/totipo-core-0.1.3-sources.jar)
has SHA-256 `f18bc8e9c006c9ee29da93874be791022ba60fbc151c91ceb63c32a8e0597984`;
all 92 Java source files matched the inspected release source byte-for-byte.
Normative spec SHA-256 at both spec commits is
`8a357e75f3ddd92efa954fde2ffc9af33f40a2c2396d6afbf1d5de5f00bc4f8a`.
No version, dependency, lock or specification pin was changed.

### Authoritative Android sources

Live official documentation inspected on 2026-10-06; these are retrieval dates,
not immutable platform releases. Current references include API-37 additions.
The permission guides below display last updated 2026-10-01 UTC.

| Reference | Use |
| --- | --- |
| [Shared documents guide](https://developer.android.com/training/data-storage/shared/documents-files) | Tree picker and durable grants |
| [DocumentsContract](https://developer.android.com/reference/android/provider/DocumentsContract) | Client tree/document operations and returned URIs |
| [DocumentsProvider](https://developer.android.com/reference/android/provider/DocumentsProvider) | Provider responsibilities and delegated behavior |
| [Document columns/flags](https://developer.android.com/reference/android/provider/DocumentsContract.Document) | Observable capabilities and document metadata |
| [ContentResolver](https://developer.android.com/reference/android/content/ContentResolver) | Raw descriptors, mode variability and cancellation |
| [ParcelFileDescriptor](https://developer.android.com/reference/android/os/ParcelFileDescriptor) and [FileDescriptor.sync](https://developer.android.com/reference/java/io/FileDescriptor#sync()) | Descriptor transport, close errors, local sync |
| [Scoped-storage enforcement](https://developer.android.com/about/versions/11/privacy/storage) | Ordinary application access restrictions |
| [App-specific storage](https://developer.android.com/training/data-storage/app-specific) | Isolation and uninstall lifecycle |
| [All-files access](https://developer.android.com/training/data-storage/manage-all-files) | Broader path capability and exclusions |
| [Play all-files policy](https://support.google.com/googleplay/android-developer/answer/10467955) | Restricted distribution permission model |
| [MediaStore guide](https://developer.android.com/training/data-storage/shared/media) | Media/download collection scope |

Additionally inspected AOSP `frameworks/base` main at exact commit
`1cdfff555f4a21f71ccc978290e2e212e2f8b168`: `DocumentsContract.java`,
`DocumentsProvider.java`, `ParcelFileDescriptor.java`, and
[FileSystemProvider.java](https://android.googlesource.com/platform/frameworks/base/+/1cdfff555f4a21f71ccc978290e2e212e2f8b168/core/java/com/android/internal/content/FileSystemProvider.java).
This is an implementation example, **not** a pin for the device or the repository's
API-37 SDK, and not a guarantee for all providers. Files were downloaded only
into ignored inspection storage; no Android implementation code was copied into app.

### Evidence categories

- **Totipo normative requirement:** v1/r18 specification.
- **TotipoStore API requirement:** released source/Javadoc and SPI_DESIGN.
- **Android documented behavior:** public platform API/permission contract.
- **Provider-advertised capability:** selected document's actual flags; none collected yet.
- **Empirical device observation:** human/device experiment; none collected for M1.
- **Implementation inspection:** AOSP or NIO source; not empirical provider evidence.

## Storage requirements matrix

SAF entries describe the public abstraction, not a measured provider. Direct
filesystem entries describe possible semantics **if the ordinary application has
legitimate path access**; they do not establish that such shared access exists.
Status is for accepting an arbitrary selected SAF tree as a writable store today.
Observation items marked with limitation are API mappings, not tested adapter code.

| Totipo operation | Requirement source | Required semantic guarantee | SAF | Direct filesystem | Evidence | Status |
| --- | --- | --- | --- | --- | --- | --- |
| Canonical `vault` access | Protocol/spec requirement §3; TotipoStore API requirement | Exact lowercase direct child, freshly observed regular content; no alias/fallback | Enumerate actual display names; MIME/virtual flags do not prove POSIX regular/no-follow identity | Exact directory-entry lookup and no-follow kind/read possible on accessible supported filesystems | §3; `readVault`, NIO reads; document metadata | SATISFIED WITH LIMITATION |
| `objects-v1` enumeration | TotipoStore API requirement; spec §3 | One direct-child attempt, no recursion; retain partial observations; Complete means attempt ended, not snapshot/freshness | Child query supports direct descendants; loading/error extras must prevent Complete/absence claims | Directory iteration, preserve partial results on failure | `ObjectScan`; provider child-query contract | SATISFIED WITH LIMITATION |
| Candidate name/type filtering | Core/application responsibility; spec §3 | Core recognizes exactly 64 lowercase hex names and authenticates bytes; fresh reads even when scan kind/size looks wrong | Adapter exposes names/kinds/optional lengths, no protocol filtering; unknown/virtual kind must not become regular automatically | Same division; no copied protocol grammar | `StoreAdapter.snapshot`, SPI_DESIGN | SATISFIED WITH LIMITATION |
| Bounded reads | TotipoStore API requirement; spec §§3, 6, 11 | At most expectedBytes + 1 bytes, bounded allocation, exact EOF/extra detection; no truncated Present | Client can bound raw stream; provider may block; MIME conversions/virtual exports unsuitable as raw canonical bytes | Bounded channel read with same result variants | `BoundedRead`; resolver raw-FD contract | SATISFIED WITH LIMITATION |
| Immutable publication | Protocol/spec requirement §18; TotipoStore API requirement | Construct/stage away from final; never deliberately fill/truncate canonical; success only for exact installed bytes and acknowledged work | Stage/write possible conditionally; no specified complete-stage exclusive-install primitive | Hard-link or equivalent exclusive installation if supported; NIO reference rejects unsupported links | §18; `NioObjectStorage`; SAF operation signatures | UNPROVEN |
| No-overwrite under publication races | TotipoStore API requirement; §18 preservation | Never overwrite existing different/unsuitable object; safe collision result under ordinary writers | No rename/move no-replace option or collision-semantic flag; a prior lookup is insufficient evidence | Exclusive link/create installation can arbitrate concurrent writers if filesystem supports it | `ObjectWrite`, NIO two-publisher tests; AOSP rename implementation | UNPROVEN |
| Duplicate-identical publication | Protocol/spec requirement §18; TotipoStore API requirement | Exact read-only byte comparison; acknowledge required configured durability; do not rewrite to reconfirm | Bounded comparison possible; acknowledgement policy missing | NIO SPI additionally forces file/namespace/root then confirms bytes | §18 vs SPI_DESIGN and `NioSpiTest` | UNPROVEN |
| Existing-different object | Protocol/spec requirement §18; TotipoStore API requirement | Leave entry untouched; report ExistingDifferent or conservative failure | Read-only comparison feasible; never open final for writing; later race still needs safe install | Reference returns different without replacement | `ObjectWrite`; §18 | SATISFIED WITH LIMITATION |
| Temporary/incomplete effects | Protocol/spec requirement §§3, 18; API staging | Noncanonical stage; temporary/conflict names have no authority; partial final never success | Rename/copy may expose intermediates; postcondition read alone cannot prove no clobber | Forced staging then installation; abandoned temp allowed | §§3, 18; SPI_DESIGN | UNPROVEN |
| Initial `vault` creation | Protocol/spec requirement §7; TotipoStore API requirement | Actual stage validated by core, install if absent; never overwrite existing canonical; positive acknowledgement | Create empty final then write is unsuitable; staged rename lacks generic exclusive semantics | No-replace stage link if supported | `PreparedVault.installCanonicalIfAbsent`, NIO SPI tests | UNPROVEN |
| `vault` replacement | Protocol/spec requirement §8; TotipoStore API requirement | Complete separate stage; same actual validated stage; no deliberate in-place truncate/rewrite; acknowledge replacement and namespace work | No replace primitive; possible non-atomic sequences need collision and persistence qualification | Strongest reasonable move, atomic preferred; non-atomic allowed by SPI | §8; `replacementUsesSameStageAndAllowsNonAtomicProvider` | UNPROVEN |
| Operation freshness / BASE comparison | Core/application responsibility; spec §8 | Core authenticates BASE, freshly reads CURRENT and compares exact bytes immediately before replacement; residual race allowed, no CAS required | Fresh queries/reads, no cached-name authority; cannot prove remote freshness | Same; no stronger transactional guarantee normative | Core vault workflow and SPI_DESIGN | SATISFIED WITH LIMITATION |
| Disappearance/replacement between scan/read | TotipoStore API requirement; spec §§2, 3, 14 | Re-resolve exact child, fresh kind/read; Absent only positively established; otherwise Unavailable | Stale ID or FileNotFound alone does not prove name absence; requery needed | Fresh lookup; kind/size observations not pins | `NioSpiTest.metadataIsNotPinnedAndSymlinksAreNotFollowed` | SATISFIED WITH LIMITATION |
| Ordinary sync races | Protocol/spec requirement §§2, 14, 18; API requirement | Incomplete/older view and conflict names safe; preserve valid observations; no false completion or overwrite | Notifications/cursors are not sync convergence or snapshot; publication collision remains unresolved | Local filesystem race protection does not serialize remote sync | §2; SPI scan/write outcomes | UNPROVEN |
| Durability/persistence | Protocol/spec requirement §§7, 8, 18; API success contract | Attempt file/storage and containing-namespace persistence; success reflects required work believed/positively acknowledged; ambiguous failure not success | Close/operation return not generic persistent-storage barrier; no namespace flush API/flag | fd/channel force plus directory barrier conditional on filesystem/provider | §18; `NioDurability`; descriptor docs | UNPROVEN |
| Directory creation | Protocol/spec requirement §3; API publication responsibility | Lazy exact `objects-v1` directory; no alias/wrong-type traversal; namespace persistence intent | Conditional create flag; provider can alter display name; freshly requery winner | mkdir/recheck race, directory persistence conditional | §3; `createDocument`; NIO namespace handling | UNPROVEN |
| Canonical object deletion | No API operation; core/application responsibility for tombstones | Deletion of token means publishing tombstone, not removing object history | Delete/remove not required for ordinary token operations | No canonical deletion method required | `TotipoStore` methods, spec state model | NOT REQUIRED |
| Stage cleanup | TotipoStore API requirement; implementation choice | Best effort; abandoned stages allowed; close never deletes/reverts canonical state | Delete/remove flags conditional; retain returned stage identity carefully | Unlink temp best effort | `PreparedVault.close`, SPI_DESIGN | SATISFIED WITH LIMITATION |
| Provider/storage error mapping | TotipoStore API requirement | UNAVAILABLE, UNSAFE_NAMESPACE, UNSUPPORTED; Failed proves no canonical effect, Uncertain admits possible effect | Client mapping possible; null/crash/permission/auth errors not absence; after mutation entry conservative uncertainty | NIO reference classifies explicit hazards/capability failures | SPI types; resolver/provider failure docs | SATISFIED WITH LIMITATION |
| Hard links, POSIX flags, directory-channel force | NIO implementation detail | These particular mechanisms are not protocol requirements | No generic equivalents promised | Available conditionally; shared Android filesystems need qualification | NIO README qualification and spec §§7, 8, 18 | NOT REQUIRED |

Minimum semantics are exact direct-child observation with bounded opaque reads,
separate complete staging, preservation of existing immutable bytes, safe initial
installation, replacement of the actual validated stage, and honest persistence
outcomes. No protocol grammar, crypto, authentication, graph, sync protocol, global
snapshot, rollback protection or POSIX syscall belongs in the Android adapter.

The API also fixes ownership: construction observes without mutation; input arrays
remain caller-owned; retained bytes require copying. Passing a store to core
create/open transfers ownership even on failure, and successful session close owns
store cleanup. Calls on a store/stage are serialized by core. Each prepared stage
permits exactly one canonical mutation attempt, then no further read-back; close
is idempotent and never reverts canonical state. A future adapter can hold an
explicit application `ContentResolver` and tree URI without DI or Activity lifetime
ownership. Synchronous provider calls need bounded content handling and cancellation
where available; provider responsiveness itself is not a guaranteed wall-clock bound.

## Android architecture candidates

### SAF/document tree — preferred access candidate, not yet accepted writable store

`ACTION_OPEN_DOCUMENT_TREE` delegates user selection to the platform. Use the
returned read/write grant flags with `takePersistableUriPermission`; retain the
tree URI as a local access reference. Persisted grants permit reopening across
restarts, subject to revocation and document movement/deletion. Selection gives URI
access, not path access. Restricted roots include primary storage, reliable SD
roots and Download; Android/data and Android/obb are excluded. A selectable test
subdirectory should be used. No broad storage permission is needed for ordinary
SAF use. These are documented mechanics, not device-tested here.
[Sources: shared documents guide](https://developer.android.com/training/data-storage/shared/documents-files),
[restriction rules](https://developer.android.com/about/versions/11/privacy/storage).

Use `ContentResolver` and `DocumentsContract` directly. No third-party abstraction
is needed. Keep document IDs opaque; never infer paths or construct a child ID by
appending a filename. Query direct children and compare actual display names.
Duplicate exact display names make exact resolution ambiguous, even with different
IDs; do not arbitrarily choose a winner. A null cursor or loading/error result
cannot prove absence. Raw document reads should avoid transformed virtual exports.
[Source: provider API](https://developer.android.com/reference/android/provider/DocumentsProvider).

### Direct filesystem — cannot serve the general selected shared-tree use case

Under modern scoped storage, a normal target-37 application does not gain arbitrary
non-media shared-folder path access by picking a SAF tree. Own app-specific paths
are accessible; documented direct shared-media access is not a structured encrypted
vault exception. Older OS/permission configurations are not a portable modern
architecture. No presently evidenced ordinary app-accessible shared path qualifies
here. This is not a claim that no specialized device configuration can expose one.
[Source: scoped storage](https://developer.android.com/about/versions/11/privacy/storage).

| Principal | What a successful path operation establishes |
| --- | --- |
| Totipo ordinary app UID | Relevant evidence of app access, still needs filesystem semantics qualification |
| adb/shell UID | Shell access only; does not grant Totipo path access |
| Document provider's UID | Provider access only; Totipo receives URI-mediated operations |
| Root/system UID | Privileged access; outside the application architecture |

A resolver-returned file descriptor can legitimately expose bytes to the app. It
does not confer authority to link/rename its containing path or sync that directory.
Do not turn opaque IDs into another application's private paths or use `/proc`
descriptor paths as a permission bypass. If a future supported ordinary shared
mount exists, filesystem staging/exclusive installation and directory persistence
should be compared empirically; no released storage-nio dependency is added here.

### App-private storage — excluded as general third-party synchronized vault

Internal files are inaccessible to other ordinary apps. External app-specific
directories are restricted on modern Android and removed on uninstall. A Totipo
DocumentsProvider or explicit file sharing could mediate access, but introduces
another interoperability architecture and does not make existing sync apps able to
synchronize private directories automatically. App-private storage may hold local
access configuration/stages; it is not the requested shared canonical vault.
[Source: app-specific storage](https://developer.android.com/training/data-storage/app-specific).

### All-files access — deferred fallback requiring explicit design acceptance

`MANAGE_EXTERNAL_STORAGE` enables broader shared-file path operations and removable
volume roots, with exclusions including other apps' app-specific storage. It
expands compromise impact beyond the selected vault. It could enable a filesystem
adapter for an accessible local synchronized directory, but cannot add filesystem
semantics to a cloud-only provider or prove exclusive installation/directory
persistence on emulated/removable filesystems. It does not strengthen SAF operations.
[Source: all-files access](https://developer.android.com/training/data-storage/manage-all-files).

Google Play restricts this permission to permitted core uses and requires
declaration/review; eligibility for Totipo is not established. No guarantee of Play
approval is inferred from encryption-related use cases. No broad permission was
added or requested; stop for design review if this becomes the only path.
[Source: Play policy](https://support.google.com/googleplay/android-developer/answer/10467955).

### MediaStore — unsuitable

Media/download collections do not provide a general user-selected non-media
directory tree containing an existing cross-client vault. MediaStore support for
app-owned downloads does not establish access to every arbitrary vault entry made
by another client, and introduces collection/index semantics rather than the
required namespace. Totipo bytes must not be disguised as media to acquire access.
[Source: MediaStore guide](https://developer.android.com/training/data-storage/shared/media).

## Provider-independence and capability model

Categories below concern the public operation, not proof that a particular provider
honors it correctly. Actual permissions and storage state can change after a check.

| Operation | Category | Evidence to collect / restriction |
| --- | --- | --- |
| Tree URI/grant mechanics | Guaranteed by SAF itself, conditional on returned grant | Read/write flags, persistable grant, reopen after restart; revocation still possible |
| Direct-child query interface | Guaranteed by SAF itself | Query children URI; provider contents/loading/errors remain provider-dependent |
| Exact child lookup | Client composition over SAF; empirically provider-dependent name uniqueness | Enumerate and compare exact display name; no generic POSIX name lookup; duplicate names reject |
| Raw read | Generic document operation; empirically provider-dependent availability/type | MIME, virtual/partial flags, raw descriptor, exact bounded bytes; no OS regular/no-follow proof |
| Directory/file create | Conditional on advertised provider capability | `FLAG_DIR_SUPPORTS_CREATE` = 8 on parent; exact returned name must be checked |
| Write | Conditional on advertised provider capability | `FLAG_SUPPORTS_WRITE` = 2; actual stream semantics and persistence still need evidence |
| Rename | Conditional on advertised provider capability | `FLAG_SUPPORTS_RENAME` = 64; returned URI/name/ID, collisions and visibility unqualified |
| Move | Conditional on advertised provider capability | `FLAG_SUPPORTS_MOVE` = 256; within same provider, no atomic replace guarantee |
| Delete | Conditional on advertised provider capability | `FLAG_SUPPORTS_DELETE` = 4; destructive, stage cleanup only unless separately justified |
| Remove from parent | Conditional on advertised provider capability | `FLAG_SUPPORTS_REMOVE` = 1024; distinct from delete, important with multiple parents |
| Exact staged no-overwrite installation | Not available as a generic SAF semantic contract | No corresponding operation option or capability bit |
| Replace canonical with validated stage | Empirically provider-dependent composition | Rename/move do not specify replacement collision/crash semantics |
| File/storage persistence acknowledgement | Empirically provider-dependent | Syncable raw FD may help locally; pipe/cache/upload behaviour can differ |
| Containing-namespace persistence acknowledgement | Not available through generic SAF | No directory persistence operation or equivalent documented barrier |

[Flags source](https://developer.android.com/reference/android/provider/DocumentsContract.Document).
MIME directory distinguishes documents from directories, not every underlying
special filesystem kind. Ordinary openable documents may be streams; virtual
documents and partial data must not be accepted as validated raw canonical records.
Metadata alone never authenticates Totipo bytes.

[Rename/remove source](https://developer.android.com/reference/android/provider/DocumentsContract):
rename may return a changed ID/URI and invalidate the original. Keep the returned
identity and freshly resolve canonical names; never persist every child URI as
permanent name authority. Remove detaches from a specified parent, whereas delete
can destroy the document. Move stays within a provider, not a cross-provider transfer.

Capability flags can reject a location lacking necessary create/write operations.
They cannot soundly accept it as a full Totipo store. A runtime state should keep
at least access, observation, exact naming, staging/install collision behavior,
replacement behavior and persistence evidence separate. Unknown semantic evidence
means **unqualified for writes**, not assumed sufficient. A one-time experiment
can falsify suitability but cannot prove every concurrent schedule or crash outcome.
No provider-name allowlist/denylist or service protocol is warranted by this finding.

## Publication semantics and precise blockers

### Immutable objects

A candidate sequence is: create uniquely named noncanonical stage; write complete
caller bytes; perform available storage persistence work; rename/move stage to
canonical name; establish canonical result and namespace acknowledgement. Core
supplies opaque bytes and names. The adapter does not calculate object identities.

Generic SAF cannot establish the installation step's no-overwrite semantics.
`createDocument` creates a document and may adjust the requested name; creating an
empty canonical document and then filling it deliberately violates §18. Generic
rename has no exclusive destination option. Querying absent, staging, requerying
absent, then renaming leaves a race with an ordinary sync/local writer. A reread
showing our bytes afterwards cannot prove we did not overwrite someone else's
different bytes. Returning Uncertain after detecting damage does not make
overwriting immutable state conformant.

AOSP's inspected `FileSystemProvider.createDocument` uses a unique candidate and
`createNewFile`, whereas `renameDocument` selects a unique name and then calls
`File.renameTo`; move checks existence then calls `renameTo`. These are different
algorithms. The rename/move source does not establish an indivisible no-replace
check across a concurrent writer. This is source evidence of a qualification gap,
not a device race result or a universal claim about providers. The unique-name
helpers can also yield noncanonical conflict names. A safe adapter must never
report such a sibling as canonical publication success.

Initial lookup of identical bytes may support a read-only exact comparison;
different or unsuitable targets must remain untouched. The released SPI additionally
requires configured durability acknowledgement for AlreadyPresentExact. NIO
implements this with extra force barriers. Spec §18 itself requires no fresh barrier
for read-only exact-existing success. **NIO's barriers are not normative Android
syscalls**, but the SPI's positive acknowledgement cannot be silently discarded.
An Android acknowledgement policy needs evidence and must reconcile both contracts.

### Initial VAULT creation

Core constructs the candidate, prepares it, reads back and authenticates the actual
stage, then asks `installCanonicalIfAbsent`. Exact existing `vault` is not absence;
uppercase aliases never authorize replacement. Generic staged rename has the same
collision gap as object installation. Creating and progressively filling canonical
`vault` bypasses the staged installation contract. No safe generic recipe is proven.

### VAULT replacement — atomic rename is not the requirement

Spec §8 requires a complete separately constructed replacement and forbids deliberate
canonical truncation/rewrite in place. Core owns authenticated BASE, validates the
actual stage, freshly reads CURRENT and compares exact bytes before mutation.
Different CURRENT is stale; unusable/unavailable CURRENT cannot justify success.
Residual races after comparison are accepted. Neither atomic rename nor CAS is
mandatory. `PreparedVault.replaceCanonical` has no stale result or BASE argument.

A possible weaker algorithm is to detach/delete old canonical and rename the
validated stage into the vacated name. This avoids deliberate in-place rewrite
and uses the same stage; it is **not rejected solely because it is non-atomic**.
However, interruption can leave no canonical vault, a competing writer can occupy
the name, rename can suffix or clobber, and persistence acknowledgements remain
missing. Undoing the operation automatically can overwrite a concurrent winner.
Only a qualified implementation with the strongest reasonable available strategy,
specified collision handling and honest Failed/Uncertain outcomes could justify
this algorithm. Generic SAF flags do not supply those facts.

Keeping several bootstrap names and selecting the newest, following conflict-copy
names automatically, adding a pointer/journal as canonical authority, or relying on
cloud versions would change canonical v1 behavior. None is implemented. Copying
the caller's original bytes again after stage validation violates same-stage SPI
semantics; direct final-stream truncation violates §8. No protocol change is proposed.

### Error certainty

Before canonical mutation, permission denial, unsupported operation or staging
failure can be Failed if canonical non-effect is established. Entering a provider
mutation requires conservative Uncertain on null result, provider crash, lost
acknowledgement or persistence failure unless definite non-effect is known.
Known wrong namespaces/unsafe aliases map to UNSAFE_NAMESPACE; lack of a required
operation to UNSUPPORTED; inability to observe/read to UNAVAILABLE. Unknown kind
does not authorize traversal or writes. Absence is an observation outcome, not
the default interpretation of an exception. No exception-message parsing is needed.

## Durability analysis

| Layer | Evidence available | What it does not establish |
| --- | --- | --- |
| Stream close completed | API resource closure; no M1 measurement | Provider commit, namespace persistence, remote upload or power survival |
| Provider operation returned | Provider acknowledged that API operation | Generic fsync-equivalent persistence contract |
| Raw FD sync | `FileDescriptor.sync` can request underlying descriptor synchronization where usable | Namespace persistence, cloud backing commit, or a pipe being syncable |
| Process restart survived | Not tested | Filesystem crash/power-loss durability even if later tested |
| Provider backing storage persisted | Not qualified | Other providers, remote peers, later rollback/retention |
| Filesystem crash qualification | Not performed | No physical crash claim |
| Power-loss qualification | Beyond current M1 evidence | No power-loss claim |

Resolver read/write-only descriptors can be pipes/sockets; `rw` implies seekable
disk storage but is not a documented flush of a cloud backend. Mode strings express
read/write/truncate/append, **not exclusive filename creation**. Provider-specific
mode behavior is documented. Error-aware close can communicate stream failure;
it is not a namespace transaction.
[Sources: ContentResolver](https://developer.android.com/reference/android/content/ContentResolver),
[ParcelFileDescriptor](https://developer.android.com/reference/android/os/ParcelFileDescriptor).

r18 requires attempting relevant file/storage and namespace persistence and reporting
belief in its required work honestly. It does not require a proof of universal
physical crash survival. Nevertheless, defining required namespace work as empty
merely because SAF has no barrier would hide the central unresolved requirement.
A documented provider equivalent might satisfy it, but no generic equivalent is
established. Successful local FD sync, close/reopen or sync convergence alone must
not be promoted to that guarantee.

## Real provider/device evidence

No M1 device/provider actions requested or performed. Android version/API, selected
authority, provider version, root URI, grants, capabilities and filesystem access
are unknown. No package/provider identity is inferred from Syncthing availability.
M0 rendering evidence is not reused as storage evidence.

No disposable tree was selected or mutated. No real vault was accessed. No
password, credential, token secret, provider key or account information was requested.
Stop occurs at generic-contract review, before Phase 4, because required semantics
are not established by the proposed abstraction.

## Experiments

### Completed agent evidence

| Setup / exact operation | Expected result | Observed result | Establishes / remaining limit |
| --- | --- | --- | --- |
| Repository: branch, exact HEAD, `git status --short` | Clean before writes | Clean main at recorded HEAD | Reproducible starting point; not build evidence |
| GitHub API `commits/HEAD` for Java/spec | Resolve current immutable revisions | Exact commits recorded above | Currentness at retrieval only |
| GitHub recursive Git trees; recompute blob SHA-1 over cached files | Cached sources equal upstream revisions | 313 Java / 167 spec identities matched | Inspection provenance; no release authenticity certification |
| Central sources JAR; compare each Java source to release checkout | Released source matches inspected contract | All 92 matched, SHA-256 above | Correct consumed release inspection; not Android execution |
| Hash normative spec at released pin and current HEAD | Determine normative drift | Identical SHA-256 | No newer normative storage requirement hidden by Java pin |
| Inspect released SPI/core/NIO tests and Android API/AOSP operations | Establish minimum semantics and possible mappings | Matrix and gaps above | Source evidence; no provider observation |

### Deferred disposable-tree experiments

These are derived qualification experiments, **not completed actions or instructions
to start now**. Resume only after design review establishes an acceptable semantic
qualification approach. Exact device commands should be supplied with the eventual
debug harness; fabricating a component name or guessing a provider is not useful.

| Setup | Exact operation to qualify | Expected result | Observed result / what remains unproven |
| --- | --- | --- | --- |
| Dedicated `totipo-android-m1-test` under selected shared/synced tree | Platform tree picker; record authority, sanitized URI, root MIME/flags; take granted persistable read/write permission | Selected disposable tree only, no broad access | NOT RUN; access/provider identity unknown |
| Same tree after force-stop/relaunch; later reboot if appropriate | Reopen saved tree reference without repicking; query root/children | Grant survives process restart; document availability reported honestly | NOT RUN; even success would not prove storage crash durability |
| Only disposable child names/data | Create unrelated, uppercase, temporary, nested and canonical-looking entries; query direct children | Exact names, no recursive discovery; record IDs/MIME/size/flags/loading/error extras | NOT RUN; underlying regular/no-follow semantics remain provider qualification |
| Synthetic known bytes, bounds 0/exact/short/long | Raw bounded read consuming at most bound + 1; remove/replace child after query then re-resolve/read | Correct distinct byte/absence/wrong-kind/unavailable outcomes | NOT RUN; a mock cannot certify provider behavior |
| Complete noncanonical stage, intended object final name | First install; exact retry without rewrite; existing-different attempt unchanged | Exact canonical publication only, truthful acknowledgement | NOT RUN; flags and a successful uncontended run do not prove no-overwrite |
| Two stages with distinct test bytes; final initially absent | Pause one writer after final absence check; other creates final; release first installation attempt | Winner never clobbered; suffix names not success | NOT RUN; selected-provider interleavings/source evidence needed |
| Disposable stage only | Interrupt write before close, interrupt installation, add unrelated/conflict siblings | Incomplete canonical effects never success; temps nonauthoritative | NOT RUN; power-loss not simulated by force-stop |
| Disposable bootstrap bytes | Initial absent install, then repeat with existing final | First exact stage installs; existing final unchanged | NOT RUN; integration with core follows storage qualification |
| Disposable real vault and test-only password | Core stage/read-back, stale CURRENT injection, successful replacement, interrupted rename/delete sequence | Same validated stage; stale prevents attempt; ambiguity Uncertain, no rollback | NOT RUN; recipe and namespace persistence unresolved |
| Provider advertises rename/move/delete/remove | Exercise actual operations on disposable stages; track returned IDs/names and parent memberships | Actual flags validated; no assumed POSIX rename | NOT RUN; does not certify atomicity/durability |
| Sync-backed disposable tree on Android/desktop | Desktop writes `desktop-to-android.txt`; Android observes exact bytes; Android writes separate `android-to-desktop.txt`; wait for human-reported convergence | Bidirectional ordinary file propagation | NOT RUN; proves sync scenario only |
| Qualified real store, disposable vault | Released core create/close/open/read; desktop writes core object then Android reads, and reverse where safe | Provider-independent cross-client operation | NOT RUN; no store exists yet |

For each future human result record exact action, question, result, and empirical
status; keep it distinct from platform guarantees. Request only OS/API, actual
selected authority/version and picker availability initially. Mutation requires
the dedicated test tree and a harness that limits names/depth/bytes and avoids
logging unrelated contents. A direct-path access test must run inside the app UID;
successful `adb shell ls` is not suitable evidence.

## Decision

**No generic Android store yet (Outcome C).** SAF remains the preferred portable
access mechanism, but a writable adapter cannot be justified solely by its public
operations and flags. The concrete blockers are:

1. Complete staged exact-name no-overwrite installation for immutable objects and
   initial `vault` is not specified by generic SAF; lookup then rename/move has an
   unresolved ordinary-writer collision gap.
2. File/storage plus containing-namespace persistence acknowledgement lacks a
   generic SAF contract. A successful close or post-write read is insufficient.
3. VAULT replacement can be non-atomic under r18, but its safe same-stage recipe,
   collision handling and required persistence still need provider qualification.

This finding does not prove that all document providers are incapable. It does
prove that advertised generic flags alone are insufficient to accept a selected
location. Missing required capabilities can reject a tree; unknown stronger
semantics must keep it unqualified. A selected provider with an independently
established safe implementation/acknowledgement contract might permit a generic
algorithm, without provider-specific sync protocols, but that evidence is absent.

Design review must settle how such semantic evidence can be obtained and retained
without treating finite probes or provider brands as guarantees. Alternatively,
explicitly review a broader local-filesystem permission model and its reduced
portability. No all-files permission, service workaround, protocol relaxation or
v1/r18 change is assumed authorized. There is no evidence for choosing Outcome A,
B or D now. Do not bypass this gate by implementing writes that always return
Uncertain or Unsupported and calling that a functional store.

## Implementation

Only this report is added. No production `TotipoStore`, diagnostic Activity,
instrumentation harness, module, UI framework, dependency, permission or preference
architecture is added. The generic analysis precedes all experimental code as
requested. A diagnostic probe can be the next scoped task after the semantic gate
is reviewed; it cannot supply a platform guarantee the API lacks.

Released core exposes `Totipo.create/open(TotipoStore, char[])` and internally
translates bounded streams/results without requiring NIO/path handles. Source
inspection found no path assumption at this boundary. This makes Android
integration architecturally plausible, **not runtime-qualified**. No Android
create/open/read proof or cryptographic runtime test against a store is claimed.

## Synchronization qualification

Not performed. Syncthing is a possible test infrastructure choice, not an adapter
architecture or required protocol. No Syncthing/Drive/Dropbox/Nextcloud API is used.
No bidirectional file propagation, core interoperability or peer observation is
claimed. Device/provider and disposable-tree results are prerequisites for that
later evidence, after a safe store is justified.

## Security impact

No manifest, runtime permission, grant or persisted configuration change.
Broad filesystem access remains avoided. No secrets or vault contents recorded.
Future SAF access would be limited by the user-selected tree grant and revocable
provider access; avoid storing passwords in configuration. Providers and sync
software remain trusted for storage operation behavior under the configured-store
model; their data, names and metadata remain unauthenticated until core validates
them. Totipo guarantees neither remote completeness nor rollback protection.

## Validation

### Agent

Source provenance checks and analyses above completed. Final diff/whitespace and
unstaged-state checks recorded below. No code/build inputs changed, so Gradle
builds, ContentResolver mocks and new JVM tests are not warranted for this report.
Upstream tests were inspected, not run; their historical results are not M1 results.

### Human device/provider

None for M1. No external terminal/USB operation requested because the generic
semantic stop condition is reached first. No device model, OS or authority guessed.

### Human Nix

None required for this documentation-only change. The agent executed no `nix`
command. No cache regeneration, flake validation, dependency or toolchain update.

### Remote CI

Not run by this task. Lightweight post-M0 CI unchanged; no emulator/provider/cloud
credential job introduced. No staging, commit or push to trigger CI.

## Known limitations and completion assessment

| Completion question | Evidence / remaining work |
| --- | --- |
| 1. Required TotipoStore guarantees | Derived from released source and exact r18; matrix above |
| 2. Best Android mechanism | SAF best portable access candidate, not yet a justified writable store |
| 3. Generic vs provider behavior | Separated above; installation/persistence not supplied by flags |
| 4. Capability-based accept/reject | Reject absent capabilities; cannot soundly accept unknown stronger semantics using flags alone |
| 5. Production store without weakening protocol | Not demonstrated; stop for design review |
| 6. Released core operates against it | Source boundary supports stores; no device integration proof |
| 7. Real shared/synchronized provider end-to-end | Not tested |
| 8. Unqualified durability/provider behavior | Close, acknowledgement, FD sync, restarts, backing storage, crash and power loss separated above |

No selected-provider type mapping, collision behavior, actual persistence policy,
restart grants, app-UID path accessibility, sync convergence or cross-client vault
operation is qualified. AOSP main is not the device implementation. Finite device
tests cannot alone prove universal race/crash semantics. M1 remains stopped at a
documented architectural gap, not marked end-to-end complete.

## Final Git state

Recorded after report creation; the report is untracked, so ordinary diff stat
does not count its contents.

`git status --short`:

```text
?? review/M1_ANDROID_SHARED_STORAGE_REPORT.md
```

`git diff --stat`: empty (no tracked edits).

`git diff --check`: PASS, no output. The untracked report is also checked directly
for trailing whitespace and conflict markers. `git diff --cached --stat`: empty.
Branch and HEAD unchanged. Nothing staged, committed, tagged, released or pushed.
