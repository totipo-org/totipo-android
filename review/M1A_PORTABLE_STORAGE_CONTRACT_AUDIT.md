# M1A portable storage contract audit

Audit date: 2026-10-06. Design/contract analysis only. **Verdict: NO — the released SPI over-constrains portable implementations relative to r18.** This does not mean that all non-filesystem implementations are impossible, or that SAF is incompatible with Totipo.

## 1. Executive conclusion

The Android M1 blockers combine real provider qualification questions with stronger Java contracts and NIO qualification choices; they are not proof of a protocol-level SAF prohibition. r18 requires complete separate construction, preservation of observed different/unsuitable immutable targets, no knowingly destructive initial creation, strongest reasonable crash-safe publication/replacement, appropriate persistence attempts, and honest outcomes. It explicitly requires neither a particular atomic publication primitive nor a fresh persistence barrier for exact-existing objects. Released Java adds an unconditional no-overwrite contract for opaque publication/initial installation and a stronger exact-existing durability acknowledgement, inherited by application `Saved`; NIO implements these using exclusive hard links and file/directory/root force. Replacement and Failed/Uncertain already have portable shapes. The smallest next step is a **totipo-java contract review and subsequent release**, distinguishing a behavioral change to exact-existing success from portable documentation clarification. Any proposal to permit overwriting an unobserved raced-in different object needs explicit security review of §18 first. No Android writes or provider experiments should resume under a silently weakened reading of 0.1.3.

## 2. Sources and revisions

Starting Android branch `main`, exact HEAD `082baf75ccba279180861af0076e03e21371a2d0`; initial `git status --short` empty. No applicable AGENTS.md was found in the source parent tree. M1 is committed starting evidence, not a permitted dirty-tree exception. Its original conclusions are preserved unchanged; this report refines them.

| Source | Exact inspected revision | Evidence |
| --- | --- | --- |
| Android | `082baf75ccba279180861af0076e03e21371a2d0` | `review/M1_ANDROID_SHARED_STORAGE_REPORT.md`, dependency pin context |
| [Java 0.1.3](https://github.com/totipo-org/totipo-java/tree/e2326aca5f5aac661d74925a30fcc57cd91014e8) | `e2326aca5f5aac661d74925a30fcc57cd91014e8` | Released boundary; also current upstream HEAD at audit time |
| [Consumed spec](https://github.com/totipo-org/totipo-spec/blob/4623a7e1718e23504903096c92332597057bd8f0/spec/totipo-vault-format-v1.md) | `4623a7e1718e23504903096c92332597057bd8f0`, v1/r18 | Exact Java pin, not an inferred release tag |
| [Current spec](https://github.com/totipo-org/totipo-spec/blob/91b6ed6bf01c3f45cb166082ffe72e92291be3a4/spec/totipo-vault-format-v1.md) | `91b6ed6bf01c3f45cb166082ffe72e92291be3a4` | Current upstream HEAD verified through GitHub API; normative text byte-identical to pin |

Existing ignored archives under `.gradle/m1-inspection` were used read-only. This audit independently compared every archived blob with the GitHub recursive trees: Java 313/313, pinned spec 164/164, current spec 167/167; no missing/mismatched blobs, no truncated trees. Normative text SHA-256 at both spec commits: `8a357e75f3ddd92efa954fde2ffc9af33f40a2c2396d6afbf1d5de5f00bc4f8a`. No later normative clarification resolves the deltas. Upstream API HEAD queries are observation timestamps, not moving dependencies.

Java files inspected (all paths relative to that Java commit):

- `SPI_DESIGN.md`, `API_DESIGN.md`, `README.md`, specification pin/report context.
- All `core/src/main/java/org/totipo/spi/*.java`: `TotipoStore`, `PreparedVault`, `ObjectWrite`, `VaultPrepare`, `VaultInstall`, `VaultReplace`, `BoundedRead`, `ObjectScan`, `ObjectName`, `ObjectEntry`, `EntryKind`, `StoreFailure`, and package contract.
- `core/src/main/java/org/totipo/format/StoreAdapter.java`, `ApplicationVaults.java`, `VaultLifecycle.java`, `ApplicationSession.java` publication/retry path, `V1ObjectPublicationStore.java`, `VaultBootstrapStorage.java`, `VaultBootstrapReplacementStorage.java`; `org/totipo/SaveResult.java` success wording.
- `storage-nio/src/main/java/org/totipo/storage/nio/`: `NioTotipoStore`, `NioObjectStorage`, `NioVaultStorage`, `NioDurability`, `NioReads`, `NioObjectScan`, `NioFiles`, publication wrapper paths including `NioV1ObjectPublicationStore` and `NioApplicationPublication`; legacy bootstrap adapter contract compared with SPI path.
- `storage-nio/src/test/java/org/totipo/storage/nio/NioPublicationTest.java`, `NioVaultStorageTest.java`, `NioDurabilityTest.java`, `NioSpiTest.java` (relevant publication, staging, replacement, read/scan, root barrier and outcome cases).
- `review/STORAGE_SPI_REPORT.md`, `review/V1_R18_REPIN_REPORT.md`, README qualification and API design persistence sections. Historical report package names are not current package names.

Spec files inspected: `spec/totipo-vault-format-v1.md` §§1–3, 7–8, 10–11, 13–14, 18, 20 at the pin and current HEAD; current `docs/design/DESIGN.md` native platform/storage chooser guidance and `review/V1_R16_STORAGE_OPERATION_DECISIONS.md` publication/durability decisions as informative corroboration. The normative document, not historical design prose or the Go reference, controls this audit. §20 explicitly preserves platform-neutral store requirements and says abstract workflow tests do not prove filesystem crash durability. Native chooser/access guidance supplies no new installation or flush contract.

Evidence labels below: **S** = normative r18 sections; **J** = released SPI source plus SPI_DESIGN (package Javadoc explicitly points there for the complete contract); **C** = core translation/workflows; **N** = NIO source; **T** = NIO tests/qualification. First appearance means first requirement in this chain, not historical authorship. No builds/tests were run; test assertions are inspected evidence, not new qualifications.

## 3. Contract-delta matrix

| Operation / guarantee | r18 requirement | Java SPI requirement | Core assumes it? | NIO mechanism | Portable necessity | Classification |
| --- | --- | --- | --- | --- | --- | --- |
| Bounded reads/allocation | S §§3, 6, 11: bounded reads, exact representation lengths, no metadata authentication | J BoundedRead: at most expectedBytes+1, observed EOF/extra, exact-sized Present, defensive copies | C adapter verifies lengths; readers authenticate | N NioReads bounded stream and separate extra byte; T exactReadsObserveEofOrExtraByteWithBoundedAllocation | Yes bounds/length; exact result shape first J | NORMATIVE; PORTABLE SPI REQUIREMENT |
| Exact-name/type observation | S §3: exact canonical/direct child, regular/directory kinds, no observed link following; no stable inode/syscall required | J fresh lookup, no case/normalization aliases, no metadata pin | C selects grammar then fresh reads including UNKNOWN scan kind | N enumerate exact string; NOFOLLOW_LINKS; T metadataIsNotPinnedAndSymlinksAreNotFollowed, exactNamesNeverAcceptCaseOrNormalizationAliases | Yes semantics; POSIX identity is unnecessary | NORMATIVE; NIO IMPLEMENTATION DETAIL |
| Partial scans | S §§2, 14: validate available subset, no global completeness/freshness guarantee | J Complete ends one attempt; Incomplete preserves entries; missing namespace empty; no recursion/duplicate exact names | C snapshot translates issue, retains candidates; no kind/size exclusion from scan | N collect/sort/deduplicate, UNKNOWN on metadata failure; T incompleteEnumerationKeepsAlreadySeenChildren | Yes honest incompleteness; sorting/result form first J | PORTABLE SPI REQUIREMENT |
| Immutable construction/staging | S §18: complete intended bytes built away from canonical; no deliberate progressive fill/truncate | J opaque publication; provider-owned temporary allowed, no protocol validation | C freezes complete envelopes; trusts provider publication | N snapshot, temp, write+force, link; T staged short writes | Separate complete construction yes; named forced file not mandatory | NORMATIVE; NIO IMPLEMENTATION DETAIL |
| Immutable no-overwrite | S §18: leave existing non-exact/unsuitable/unestablished-exact target untouched; no repair/replace; strongest reasonable mechanism, no mandated atomic primitive | J create-or-confirm-exact, never overwrites existing different bytes; accepts arbitrary opaque name/bytes | C relies on preservation, cannot enforce it itself | N exclusive hard link, no overwrite fallback; T twoPublishersNeverOverwrite(false) | Preservation yes; unconditional arbitrary-byte race exclusion is stronger than demonstrated normative baseline | SPI STRONGER THAN SPEC — QUESTIONABLE |
| Duplicate exact publication | S §18: read-only exact comparison gives idempotent success; forbid mutation merely to reconfirm | J AlreadyPresentExact only after equality AND required durability acknowledgement | C accepts either positive result; frozen retry may become Saved | N exact read plus stronger acknowledge path on SPI | Exact collapse required; added fresh barrier unnecessary for r18 | NORMATIVE; SPI STRONGER THAN SPEC — QUESTIONABLE |
| AlreadyPresentExact result shape | S §18 exact-existing success has distinct meaning, no new protocol pending state | J separate variant from Written and ExistingDifferent | C maps to ALREADY_PRESENT_EXACT | N boolean new/existing translated; T publicationCertaintyAndExactRetry | Useful portable shape, independent of added barrier | PORTABLE SPI REQUIREMENT |
| Durability for exact-existing bytes | S §18 explicitly no fresh fsync/other barrier required | J SPI_DESIGN says equality insufficient; exact retry must establish durability | C Saved documentation promises entire-operation durable acknowledgement | N open existing WRITE/no-follow, force, objects dir, root, reread; T exactExistingBarrierFailureCannotAcknowledgeSuccess | No fresh protocol barrier; valid stronger application policy but wrong as universal minimum | SPI STRONGER THAN SPEC — QUESTIONABLE |
| Object publication stage validation | S §18 no mandatory reread/decrypt/semantic self-validation at publication layer | J opaque bytes; no authentication by provider | C authors correct envelope; ordinary reads validate hostile bytes | N no stage crypto; stage force before link | Object stage self-authentication not needed | NORMATIVE |
| Initial VAULT separate candidate | S §7 root generated; complete representation constructed separately | J prepareVault never canonical; actual representation readBack | C validates candidate, actual stage equals candidate and authenticates root | N private temp, bounded read; T stagedReadBackFreshlyObservesActualRepresentation | Separate construction normative; mandatory storage readback first core/J | NORMATIVE; SPI STRONGER THAN SPEC — JUSTIFIED |
| Initial VAULT no-overwrite | S §7 never knowingly overwrite, wrong type not absence, no specific atomic no-replace syscall | J installCanonicalIfAbsent: installation never overwrites existing canonical | C checks absence then requests one install; AlreadyPresent/Failed distinguished | N exclusive link, exact collision detection; T initialInstallNeverOverwritesAndConsumesHandle | No knowingly destructive creation required; unconditional exclusion stronger | SPI STRONGER THAN SPEC — QUESTIONABLE |
| Same validated VAULT stage | S §§7–8 separately constructed correct representation; no actual-stage readback mandate in these sections | J mutations use actual read-back stage, never rebuild/re-upload input recipe | C compares and authenticates stage before install/replace | N retained temp handle; T replacementUsesSameStageAndAllowsNonAtomicProvider | Required for this core's validation assurance; no stable inode/CAS implied | SPI STRONGER THAN SPEC — JUSTIFIED |
| VAULT replacement | S §8 complete separate candidate, no deliberate in-place rewrite, strongest reasonable replacement/persistence | J same-stage replacement; no atomic move/CAS; permits non-atomic move/copy/delete implementations | C authenticates root; trusts outcome; no post-open mandatory | N atomic move then regular replacement fallback; T nonAtomicPartialReplacementIsUncertainAndNeverRolledBack | Yes semantics, provider mechanism flexible | NORMATIVE; PORTABLE SPI REQUIREMENT |
| Atomic vs non-atomic replacement | S §8 CAS unnecessary; residual race accepted | J explicitly neither atomicity nor transaction required | C requires neither | N strongest available fallback; legacy adapter remains atomic-only; T NioVaultStorageTest unsupportedAtomicMoveHasNoOverwriteFallback is legacy, not SPI minimum | Atomicity optional; do not apply legacy tests to Android | NIO IMPLEMENTATION DETAIL; QUALIFICATION DETAIL |
| Stale/CURRENT comparison | S §8 acceptable fresh CURRENT, exact BASE equality immediately before replacement; fingerprint insufficient | J core owns stale; provider receives no BASE/password | C ApplicationVaults/VaultLifecycle perform comparison | N rechecks kind/presence, no byte CAS | Normative core duty; residual race explicitly accepted | NORMATIVE |
| Failed/Uncertain mutation outcomes | S §§7–8, 18: ambiguous failure never success/absence/winner inference | J Failed proves invocation did not change canonical target; Uncertain admits unacknowledged possible mutation | C vault preserves distinction; object workflow conservatively maps all non-success to operation uncertainty | N mutation-entry flag, definite exclusive collision exceptions; T canonicalMutationAcknowledgementFailureIsUncertain | Yes honest certainty; transaction not required | PORTABLE SPI REQUIREMENT |
| File/storage persistence (new write) | S §§7–8, 18: required attempts, strongest reasonable crash-safe method, success based on believed required work | J positive configured provider acknowledgement, no universal survival claim | C success/result trusted, not physical durability measured | N FileChannel.force(true) on stage/installed object | Attempt relevant available content commit; fsync method not universal | NORMATIVE; NIO IMPLEMENTATION DETAIL |
| Containing-namespace persistence | S §§7–8, 18: attempt persistence; lazy namespace same intent | J successful writes acknowledge required work; no SPI syncDirectory method | C no namespace operation itself | N StorageDurability directory channels; T NioDurabilityTest tests accepted force vs unavailable | Relevant namespace commit duty yes, POSIX directory handle unnecessary | NORMATIVE; NIO IMPLEMENTATION DETAIL |
| Root force before every new object | S §18 lazy namespace durability intent; no stateless root-force ordering on every write prescribed | J SPI_DESIGN explicitly attributes this sequence to NIO | C no dependency on order or root channels | N unconditional pre-link root barrier, even reopened/pre-existing namespace; T reopenedProviderRequiresRootBarrierBeforeEveryNewObjectMutation | NIO recovery policy; a provider may commit parent/child together | NIO IMPLEMENTATION DETAIL; QUALIFICATION DETAIL |
| Stage cleanup | S §§3, 18 temps nonauthoritative; no required journal/pending state | J close best effort, no canonical deletion/revert, idempotent, cleanup cannot revoke acknowledged success | C finally closes stages; cleanup not gate | N unlink temp; T closedStoreCleansStagesAndNeverCanonical | Honesty/authority yes; cleanup mechanism optional | PORTABLE SPI REQUIREMENT; NIO IMPLEMENTATION DETAIL |
| Directory creation | S §3 lazy objects-v1 allowed, exact directory type; §18 same persistence intent | J observation does not create; publication may create namespace | C no emptiness prerequisite or orphan deletion | N mkdir, reobserve concurrent winner, root force | Exact logical container needed; mkdir syscall unnecessary | NORMATIVE; NIO IMPLEMENTATION DETAIL |
| Ordinary races | S §§2–3 trusted baseline/no inode proof; §8 residual replacement race; §18 intermediate visibility permitted | J observational reads/scans; replacement race allowed; stronger publication/creation promises | C no remote completeness/CAS dependency | N hard links arbitrate local publication; no-follow not adversarial namespace CAS | Don't confuse tolerated visibility/races with permission to knowingly clobber | AMBIGUOUS for raced-in different §18 target; otherwise NORMATIVE |
| Opaque ownership/lifetime | S no Java array/handle contract | J serialized calls, caller array ownership, store transfer, one attempt, close idempotent/read-only construction | C adapter/session owns close and lifecycle | N clones, stage tracking; T ownership/consumption tests | Portable API mechanics; no filesystem requirement | PORTABLE SPI REQUIREMENT |

## 4. Immutable publication analysis

### Honest same-ID concurrency

Under one vault root, §§10–11 define `OBJECT_ID = HMAC-SHA-256(K_id, P)`, object key from that ID, nonce from the ID, fixed AAD, canonical length/padding, and AES-GCM sealing. If the canonical plaintext P is equal, the entire intended 1024-byte envelope is equal: there is no per-publication random nonce. This includes metadata and parent fields; equal displayed values alone do not imply equal P or equal ID.

The converse uses the cryptographic assumption that distinct canonical plaintexts do not collide in keyed HMAC-SHA-256 for honest feasible executions. It is not mathematical injectivity. It also presupposes the same vault root, correct canonical encoders and the prescribed cryptographic construction. Different roots must not intentionally share objects-v1 (§3). A collision or broken primitive is not a concurrency repair case: §13 excludes distinct authenticated plaintexts validating under one ID as integrity/cryptographic failure. Invalid bytes at the name have no authenticated meaning.

Thus two ordinary honest writers independently publishing one valid same-root ID normally supply identical bytes. NioPublicationTest's `twoPublishersNeverOverwrite(false)` uses arbitrary unequal byte arrays for one synthetic ID: it tests the generic opaque Java preservation promise, not a constructible ordinary honest protocol race. Its `same=true` branch is the normal same-ID convergence case. Neither branch establishes universal provider durability.

### Observed corrupt/different target

§18 requires leaving an existing non-exact, unsuitable, or not-established-exact target untouched and failing publication. A read error is not absence. An exact regular target permits read-only idempotent success. No repair, delete, rename, chmod, touch or rewrite merely to reconfirm. These are obligations, not optional hardening. No result vocabulary can make deliberately destructive publication conforming by reporting Uncertain afterward.

### Residual races

§18 explicitly mandates no particular atomic primitive and permits intermediate effects on stores lacking atomic visibility despite reasonable complete-byte staging. §§2–3 do not demand malicious same-privilege race defenses or inode pinning. Therefore r18 does **not** demand an exclusive atomic filesystem no-replace primitive from every provider. A conditional provider operation, transactional object API, or other reasonable complete-stage mechanism is not excluded.

This does **not** make lookup-absent followed by arbitrary overwrite safe by definition. Initial VAULT's §7 uses knowledge-qualified preservation; replacement's §8 expressly accepts the compare-to-mutation race. §18 instead says an existing non-exact target must be left untouched. It does not expressly explain a different/corrupt entry appearing after an honest absence observation but before installation. Honest same-ID equality addresses honest writers; it does not authorize destruction of raced-in corrupt or independently rooted bytes. The strongest-method and trusted-race language supports a non-transactional interpretation, but is not evidence that any blind replace primitive meets it.

The exact unresolved edge is **whether a strongest reasonable separately staged strategy with fresh absence checks, but no conditional installation facility, may incidentally replace a not-yet-observed different canonical target**. This audit does not grant that permission. A proposed Android strategy relying on it needs a focused spec clarification/security review (D), rather than borrowing the explicit §8 replacement exception or claiming a collision can never happen. This boundary question does not erase the clear absence of a mandated syscall/atomic primitive.

### Required provider guarantee

At minimum: complete off-target construction; no deliberate progressive canonical write; exact safe naming/type observation; preserve observed/unestablished-exact existing entries; strongest reasonable crash-safe installation and relevant persistence attempts; truthful acknowledgement/uncertainty. A provider lacking defensible semantics for these must reject the operation/location. Released J goes further: create-or-confirm-exact for **arbitrary opaque name/bytes**, never overwriting existing different bytes. It cannot exploit keyed addressing internally and cannot downgrade that unconditional promise silently. No-overwrite semantics do not logically require atomic visibility of the whole object, but do require reliable exclusion/preservation under the SPI promise.

## 5. Initial VAULT analysis

The trace is: core freshly observes absence → generates root/salt/nonce → constructs/authenticates candidate → prepares noncanonical representation → reads actual stage, compares exact bytes and authenticates root → requests install-if-absent → accepts acknowledgement. Existing wrong-type/unreadable material is not permission to initialize.

§7 mandates separate complete construction, strongest reasonable crash-safe creation, no **knowingly** overwriting canonical vault, file/storage and namespace persistence attempts, and no success without establishing successful creation under that constraint. It explicitly disclaims a specific atomic no-replace primitive/syscall. Actual-stage readback/authentication and one-use PreparedVault are Java/core assurance choices, not additional normative steps stated in §7. They are justified: validation of a caller array cannot validate a different uploaded stage.

The SPI adds stronger wording: installation never overwrites an existing canonical target; the low-level bridge says no-replace semantics/equivalent exclusion. NIO hard-link installation provides that exclusion. Concurrent first creators usually generate different roots/wrappers; object same-ID reasoning cannot excuse one overwriting the other. r18 permits no inference of universal race freedom and does not mandate exclusive filesystem creation; nevertheless a weak API must supply a defensible strongest-available strategy and truthful outcome. A knowingly replacing operation is forbidden. Merely seeing one's bytes afterward does not establish how a concurrent canonical entry was treated. Ambiguous creation is Uncertain, not Created. Clarifying the knowledge/race scope in J is B only if maintainers confirm the absolute wording was not intended; deliberately relaxing an actual exclusion promise is C and requires a release/review.

## 6. VAULT replacement analysis

M1's main observation is confirmed. §8 requires a complete separately constructed wrapper, preservation of K_root, no deliberate truncate/rewrite of canonical in place, strongest reasonable replacement, persistence attempts, freshly observed acceptable CURRENT immediately before mutation, and exact CURRENT/BASE equality. Atomic CAS/rename is not mandatory; the residual race after comparison is explicitly accepted. A stale comparison prevents the attempt; fingerprint equality cannot replace byte equality.

Core's actual-stage equality/authentication and same-stage installation strengthen the construction assurance. J explicitly permits non-atomic move/copy/delete implementations; it has Replaced, Failed, Uncertain and intentionally no STALE because core owns that decision. NIO attempts atomic move then regular replacement move; legacy atomic-only adapters/tests are not requirements for PreparedVault.

“Copy” is not blanket permission for product code to truncate an existing canonical document and upload a recipe. A provider may implement a stage-based replacement with non-atomic intermediate effects; NioSpiTest models partial copy failure as Uncertain with no rollback. The adapter must operate from the same validated stage, avoid deliberate in-place reconstruction of the old canonical file, use the strongest reasonable mechanism, and acknowledge the actual required work. Unknown provider behavior cannot count as stronger crash safety.

SAF could **theoretically** support this contract without r18 changes if the selected API/capability supplies a defensible stage-based mutation/acknowledgement model. Atomic replacement and POSIX descriptors are not prerequisites. No SAF algorithm, generic capability proof, or real provider qualification is supplied here.

## 7. Persistence analysis

### Normative obligation versus SPI policy

§§7, 8, 18 use required persistence attempts and belief that required durability work succeeded, coupled to strongest reasonable crash-safe mechanisms. §2 expects retention under ordinary successful operation and separates it from perpetual retention, freshest history and remote synchronization. §20 does not impose a stronger atomic primitive and separates abstract evidence from filesystem durability proof. Historical V1_R16_STORAGE_OPERATION_DECISIONS describes durability facilities appropriate to the configured store; this corroborates the normative wording but cannot amend it.

The defensible portable reading is: identify the relevant content **and namespace** persistence/commit work the configured provider actually exposes; perform the strongest reasonable applicable operations before new-write success; accept success only after their successful acknowledgement; describe the provider's actual retention/commit contract without upgrading it to universal physical survival. A single provider commit can cover both content and namespace. This follows from platform-neutral storage wording and the express absence of mandated primitives, not from Android convenience.

This interpretation is conditional. “No exposed fsync” alone neither disqualifies a provider nor proves that close/rename is a durable commit. A provider operation may itself acknowledge durable logical storage, or may acknowledge only request acceptance/enqueueing. The latter does not automatically satisfy required persistence work. A vacuous configured policy with no evidence of ordinary retention is not a durable store qualification. If acknowledgement or its scope is unavailable/ambiguous, success is not established. The spec does not enumerate every opaque API's semantics; qualification must supply that evidence. If the only available contract makes success unknowable, report failure/uncertainty or unsupported capability, rather than invent durability.

| Concept | r18/SPI meaning and evidence limit |
| --- | --- |
| fsync file / FileChannel.force | NIO content persistence mechanism, not a universal API requirement; relevant exposed content commit must be attempted for new writes |
| fsync directory | NIO namespace persistence mechanism. r18 requires the persistence intent/attempt, not a POSIX directory fd. NioDurability itself says Java SE does not guarantee directory fsync semantics on every provider |
| Provider acknowledgement | J positive configured acknowledgement is stronger epistemic wording than r18 belief, but a reasonable portable assurance. Must identify what was acknowledged: content, name/container publication, or merely queued work |
| Remote/cloud commit | A provider's own authoritative durable commit may be relevant for a remote-backed store; completion on independent peers/replicas is not a Totipo prerequisite. Do not confuse provider-local success with external sync convergence |
| Process restart | Reopen can show data/access persistence in that scenario. Does not prove acknowledged crash barriers or power-loss survival; URI access grant persistence is a different matter |
| Power-loss survival | Neither universal survival nor every hardware/provider failure is promised. Accepted force/commit calls are evidence of attempted work, not physical crash proof |
| Already-existing exact object | S §18 requires read-only comparison, no fresh barrier. J equality-plus-durability and NIO file/objects/root force are a separate stronger recovery policy |
| Root force on new object | NIO stateless policy persists objects-v1 linkage before target mutation on every new write; r18 only mandates corresponding durability intent, not this order/channel mechanism |

SPI_DESIGN's persistence section already excludes remote replication, universal physical survival and rollback guarantees. `TotipoStore` exposes no fsync, Path, channel, root-force callback or POSIX metadata. The overly filesystem-specific assertions come from implementation/test policy and legacy contracts, not method signatures.

The **clear behavioral delta** is exact-existing success. r18 says no fresh barrier; SPI_DESIGN says equality is insufficient. NIO's SPI and application wrapper use `acknowledgeExisting=true`, while the low-level r18 publication wrapper uses the read-only path. `NioPublicationTest` has historical method names mentioning durability/post-barrier confirmation but its actual exact-existing assertions expect only `existing-read`; `NioSpiTest` requires `existing-force`, directory/root sync and reread, and rejects success on any failed retry barrier. Assertions control this conclusion, not test names.

This is not just a comment typo. `API_DESIGN.md` Saved/retry section, `SaveResult.Saved` documentation and `NioApplicationPublication` explicitly rely on stronger acknowledgement even after prior uncertainty. Core mechanically trusts the positive result, not particular force calls, but applications have a documented stronger meaning. Making read-only exact comparison sufficient changes that meaning and success/failure behavior. Stronger optional user-requested durability policy can be legitimate; imposing it as the sole portable protocol acknowledgement is questionable. If a force operation merely flushes without touching/changing the entry it need not violate §18's mutation prohibition; any reconfirmation strategy that touches metadata/bytes still violates it. Requiring WRITE access merely to acknowledge exact readable bytes is also stronger than r18.

M1's persistence classification is therefore refined: absence of a generic POSIX namespace barrier is not itself a Totipo incompatibility; what remains unproven is the relevant provider storage/namespace acknowledgement. Its generic-platform evidence does not qualify a provider, and its warning that close/post-read cannot *alone prove stronger barriers* remains valid. No M1 history rewrite is warranted.

## 8. SPI portability verdict and certainty model

**NO — released SPI over-constrains portable implementations**, principally the mandatory exact-existing recovery acknowledgement and absolute opaque no-overwrite/initial exclusion wording. A sufficiently capable non-filesystem object service/database could implement 0.1.3 honestly; “NO” means its universal minimum is stronger than the normative portable minimum, not that every non-filesystem store is impossible. SAF is not yet proved capable or incapable.

Reads, exact logical namespace observations, partial scans, opaque bytes, array ownership and stage lifetime are portable. Actual-stage validation is a justified stronger assurance. Replacement explicitly supports weaker providers. Persistence for new writes can be described portably without requiring fsync(directory). Exact-existing acknowledgement is a real stronger behavior, not resolved by relabeling NIO tests as optional. Exclusion promises must not be silently interpreted as best-effort checks.

| Mutating operation | Definite failure / no canonical effect | Ambiguous outcome | Positive outcome |
| --- | --- | --- | --- |
| publishObject | ObjectWrite.Failed if this invocation's target change is disproved; ExistingDifferent for observed different bytes left untouched | ObjectWrite.Uncertain once installation might have happened without required acknowledgement | Written for acknowledged new exact installation; AlreadyPresentExact under released stronger acknowledgement contract |
| prepareVault | VaultPrepare.Failed; canonical unchanged, temp artifacts possible | No separate status needed: staging may be ambiguous but has no canonical authority; fail preparation/abandon stage | Prepared, only a noncanonical handle; not publication success |
| installCanonicalIfAbsent | VaultInstall.Failed before canonical effect; AlreadyPresent for exact canonical existence without replacement, not success creating this vault | VaultInstall.Uncertain when request/effects/ack ambiguous | Installed after required work acknowledgement |
| replaceCanonical | VaultReplace.Failed when no canonical effect is established | VaultReplace.Uncertain; may include missing, old, new or partial current representation; never automatic rollback | Replaced after required work acknowledgement; stale belongs to core before invocation |
| lazy namespace/temp creation and close | Failed for main operation can coexist with namespace/temp effects; close cleanup best effort | Temp/cleanup ambiguity never proves canonical publication or revokes acknowledged success | No separate canonical success status; namespace persistence included in relevant new-write work |

These states can honestly express provider failure before canonical mutation, ambiguous mutation requests, and acknowledged required work. An exception is not proof of no effect. There is **no missing SAF-specific canonical outcome variant identified**. Uncertain is not a license for an unsafe algorithm. Coarse failure reasons need not encode provider brands, sync protocols or remote state. AlreadyPresent in VaultInstall is not an acknowledgement that our candidate was installed; clarify the shared Javadoc's blanket “Success” wording (B).

One existing peculiarity is that NIO marks `mutationEntered=true` during exact-existing force and returns Uncertain on barrier failure despite not rewriting bytes. This tracks its stronger recovery uncertainty; it does not imply a canonical rewrite happened. Separately, StoreAdapter collapses object Failed/ExistingDifferent/Uncertain to IOException and ApplicationSession's frozen-operation uncertainty is monotonic after call entry. Therefore a provider may return definite Failed while product reports PublicationUncertain conservatively. r18 forbids false success/absence, not conservative operation-level uncertainty. Vault adaptation preserves definite no-mutation evidence. This loss of precision is not a required API addition for portability.

## 9. Required follow-up by repository

Remedy categories: **A** no change; **B** Java documentation clarification without behavior/API change; **C** Java behavioral/API change and release; **D** spec clarification/revision. Recommendations below are review findings, not implemented changes.

| Repository | Change needed? | Type | Why |
| --- | --- | --- | --- |
| totipo-spec | No required wire/crypto revision. Focused clarification needed before accepting a strategy dependent on raced-in different-object replacement | D, clarification candidate for §18; semantic if it permits currently prohibited deliberate replacement | Explain scope of “existing target … leave it untouched” against ordinary unobserved installation races; preserve all explicit MUST/MUST NOTs. §§7–8 already disclaim mandatory atomic primitives; §§7–8, 18 already support provider-relative persistence intent |
| totipo-java | Yes, contract review; explicit behavior decision/release if adopting r18 exact-existing minimum | C for AlreadyPresentExact/Saved recovery policy; B for portable persistence and qualification guidance; B or C for exclusion wording depending on intended guarantee | Equality-insufficient is an intentional released promise. Don't weaken it by Javadoc reinterpretation alone; review source/behavior, wrappers/tests and application Saved meaning together. Explain strongest provider commit, no universal directory-force prerequisite, creation knowledge/race boundary and stage-based replacement limits |
| totipo-android | Only this report now; later qualify against resolved Java contract | A for production now; documentation refinement here; subsequent implementation strategy after review | M1 remains historical evidence. Do not require POSIX primitive names, but do require evidence of safe logical operations and actual acknowledgement scope |

Issue-level smallest remedies:

| Issue | Smallest remedy |
| --- | --- |
| Bounds, exact names/types, partial observations, hostile validation, cleanup, ownership | A: implement required portable observations later; document evidence without weakening semantics |
| Same-stage readback and validation | A: keep the Java assurance; a provider must expose its actual stage, not replay the input array |
| Replacement atomicity and legacy tests | B: emphasize SPI non-atomic permission and separate legacy/NIO qualifications; no new API/result |
| File/namespace/root force as universal prerequisites | B: make provider-relative content/namespace acknowledgement explicit; retain NIO force policy as NIO qualification |
| Exact-existing mandatory acknowledgement | C if aligning portable minimum to §18: change behavioral contract, NIO SPI/application policy and affected tests/documentation together; not just rename “persistence” |
| Unconditional publication/initial exclusion | B only if clarifying intended normative trusted-race scope; C if relaxing promised exclusion. For §18 raced-in different bytes also D review before accepting such a strategy |
| Generic AlreadyPresent success Javadoc | B: separate non-creation/no-replacement result from Installed durability success |
| Failed/Uncertain vocabulary | A: existing outcomes suffice; no new enum/sealed variant proposed |

A contract change with existing methods/records unchanged need not break Java source or binary linkage, but changes behavior and guarantees for existing providers/callers; it requires a new Java release and explicit compatibility notes. Adding a method/variant or policy parameter could have source/binary consequences (especially exhaustive sealed switches); this audit proposes none. If retain stronger Saved as an optional capability, its design/API compatibility must be reviewed separately, not invented in Android. Experimental/unfrozen SPI status is not permission to misrepresent released 0.1.3.

## 10. Recommended next milestone

**Java portable-storage contract decision, then a Java revision/release if the r18 minimum is adopted.** Review the exact-existing policy with Saved consumers and NIO SPI/application tests; clarify provider content/namespace acknowledgement and distinguish NIO qualifications from SPI requirements. Resolve creation exclusion wording, and obtain a narrow §18 spec clarification if a proposed weakest staged-install strategy depends on the unobserved different-target race.

This audit stops for review because the stronger acknowledgement is documented in core/application results and any relaxation changes the released behavioral contract. The security-critical §18 race edge is not assumed resolved in Android. There is no proposed API break or protocol semantic relaxation to implement here. After those decisions are recorded in an exact consumed Java release, Android provider qualification can resume against named semantic obligations, with acknowledgement and residual race evidence separated from finite probes. Product code must remain capability-based, without provider-brand exceptions. Experiments cannot alone establish universal race or physical durability guarantees.

## 11. Protocol impact

| Impact | Finding |
| --- | --- |
| Wire format | None: names, 87-byte bootstrap, 1024-byte envelopes and TOKEN grammar unchanged |
| Cryptography | None: no collision assumption weakened; root/identity/key/nonce rules unchanged |
| Protocol semantics | No proposed weakening needed for read-only exact success, non-atomic replacement or provider-relative persistence. Any new permission to deliberately replace different immutable bytes would be a semantic change and is outside this audit |
| Conformance vectors | No existing vector changes required by these findings. A clarification may justify added abstract race/provider workflow coverage; abstract cases do not qualify real providers |
| Java behavior | Exact-existing/Saved contract change requires release review even with unchanged source/binary signatures; NIO/SPI assertions may need revision after decision |
| Spec release revision | No automatic r18 change. A committed normative clarification should receive the repository's appropriate new revision/pin; semantic changes require explicit protocol review. Current HEAD supplies no later normative fix |
| Android dependencies/build | None changed. Consume any future approved release only in a separately scoped task |

Security intent remains: never deliberately rewrite immutable canonical objects; never knowingly replace corrupt/different existing canonical bytes; never treat unacknowledged mutation as definite success; never grant staging canonical authority early; authenticate hostile synchronized bytes in core; never invent provider guarantees. Portability means explicit appropriate semantics, not an empty durability policy.

## 12. Final Git state

Final commands recorded after report creation (the new report is untracked, so ordinary diff stats do not include it):

```sh
git status --short
# ?? review/M1A_PORTABLE_STORAGE_CONTRACT_AUDIT.md
git diff --stat
# empty
git diff --check
# PASS, no output
git diff --cached --stat
# empty
```

Untracked report separately checked for trailing whitespace and conflict markers. Branch remains `main`; HEAD remains `082baf75ccba279180861af0076e03e21371a2d0`. Only this review document added; M1 unchanged. Nothing staged, committed, tagged, released or pushed. No upstream files, application code, dependencies, locks, caches, flakes or Android configuration altered. No Nix commands, builds, provider mutation experiments, permission requests or external writes performed.
