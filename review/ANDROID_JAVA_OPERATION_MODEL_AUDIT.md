# Android Java operation-model architecture audit

## 1. Starting HEAD/state and scope

Classification before editing: **DOCS_ONLY**, plus **architecture audit / provenance**.
Audit date: 2026-10-10. Starting branch `main`; starting HEAD
`ac5e7893ff0e1a876d496a6d34bd12c2a4592427`; `git status --short` was empty.
No Android production, behavior-test, tooling, build, dependency, package, Nix or
CI changes are authorized or made. Findings below are review evidence, not fixes.

Required committed prerequisites were verified with `git log`:

| Prerequisite | Commit |
| --- | --- |
| Daily-driver UX | `38b251e` |
| Row reveal/copy | `9264e32` |
| Stable rows/countdown ring | `24ffe50` |
| Reveal repaint correction | `f8371f5` |
| Sync-status layout correction | `edcf32b` |
| Biometric unlock/inactivity Lock | `45a7006` |
| Qualification ladder | `ac5e789` |

Read AGENTS.md, README.md, package.nix, flake.nix, CI, Maven-boundary wiring,
verification/locks, relevant source/tests/tools and the subsystem reports cited below.
Java was fetched from GitHub into `/tmp/totipo-java-audit.o7nImV/java`, with an
independent released-source archive at `/tmp/totipo-java-audit.o7nImV/released`.
These temporary inspection files are outside Android and are not build inputs.
No Java build, local artifact installation or publication was performed.

## 2. Runtime artifact identity

The resolved Android release runtime graph remains:

```text
app
  -> org.totipo:totipo-storage-nio:0.2.0
     -> org.totipo:totipo-core:0.2.0
        -> org.bouncycastle:bcprov-jdk18on:1.86
```

Strict runtime constraints select those exact versions. The direct edge is only
storage-nio. [Maven-boundary wiring](../app/build.gradle.kts) verifies all four
production configurations and forbids source/project/file/substitution and mixed
Totipo versions. [Strict verification](../gradle.properties),
[locks](../app/gradle.lockfile), and [verification metadata](../gradle/verification-metadata.xml)
remain unchanged. BC 1.80.2 in the lint-tool configuration is not the app runtime.

## 3. Released Java source pin

Runtime truth is released source **`d6310c177ae930df188fd4f5798622c935698b2e`**.
The annotated `v0.2.0` tag object `d24e3d0ae71ea7fe318261519a9b5d08657a0e03`
peels to exactly that commit. [Release source][release] and
[release SPEC_PIN][spec] identify Java 0.2.0 / Vault Format v1/r19 at specification
commit `cdb4e91be1c6d3704874b2b92457ffe7be5e9084`.

Fresh downloads from canonical Maven Central of each module's POM, module metadata
and sources JAR match all six existing hashes in
[TOTIPO_JAVA_DEPENDENCY.md](../TOTIPO_JAVA_DEPENDENCY.md). All **82 core + 12 NIO**
Java source entries match the release archive byte for byte. This rechecks the
source-to-published-artifact relationship; it does not rebuild or replace artifacts.

## 4. Reviewed guidance pin

Reviewed application-operation guidance:
**`b03f5b22f367723ce4a3bddf0a56b159a06f32cf`**.

[Exact API_DESIGN requested URL][guidance-requested]; [canonical URL][guidance].
GitHub's repository API and clone redirect the historical `totipo-dev/totipo-java`
name to `totipo-org/totipo-java`. Both URLs identify the same repository/commit.
The API_DESIGN UTF-8 bytes fetched at the requested exact SHA equal the Git blob;
SHA-256 is
`054c2f432420e9b7a39f5f661bde2b9ae773d2d2ccb29f3adfa949078ee44c0d`.

The pin is the latest committed, qualified documentation revision in the fetched
canonical history, not a floating branch. API_DESIGN last changed at
`ce00f0c6db8fb1c81322a7b9e02132d276f63a33`; the selected descendant retains
that exact blob and adds reviewed qualification-process documentation. It contains
both the operation model introduced at `3b24b54becde0c93479c1fbd80ea0fbd2026e2a8`
and the later subscriber-threading correction. Pinning only `3b24b54` would omit
the correction. Selection evidence is the committed [ladder report][java-ladder],
[threading correction report][java-threading], and [operation-model report][java-model].

## 5. Why the pins differ

**Artifact/source pin = implementation Android actually executes.**
**Guidance pin = later reviewed clarification of how clients should use that implementation.**
The guidance is not an Android dependency upgrade. Published 0.2.0 Javadoc bytes
remain unchanged; the repository clarification is read separately. New tests in
the guidance lineage are corroborating evidence, never misrepresented as tests
shipped at the release source pin.

## 6. Guidance integrity and applicability

Verified `VERSION` is `0.2.0` at the release, operation-model, threading-correction
and selected guidance commits. `SPEC_PIN.md` is byte-identical between release
and guidance and still pins v1/r19. The operation section explicitly describes
existing 0.2.0 semantics. The clarification reports record normal Java/publication/
consumer qualification and human-reported Nix PASS (400 then 402 JUnit tests).
The later ladder report records its narrower excluded-Markdown qualification.
These are committed upstream evidence, not Java checks executed in this milestone.

Independently compared all **94 production Java files** between release and
selected guidance after removing block comments and whitespace: identical.
The release-to-guidance production diff touches six public API files only in
comments; ApplicationSession, ApplicationStates, storage implementation and API
signatures are unchanged. PublicApiTest adds the two subscriber-threading cases;
no existing expectation is weakened. Earlier intervening Java Nix/CI changes do
not alter the runtime operation contracts. Therefore no runtime/API implementation
change is required for Android to use this guidance.

Evidence keys used throughout the matrix:

| Key | Exact evidence and relevant entry points |
| --- | --- |
| J1 | Released [ApplicationSession][session], lines 44–191: initial state, state/states, local/provider locks, requestRefresh, observePass, validateObject, terminate. |
| J2 | Released [ApplicationSession][session], lines 258–320: immutable State, receiving-state update/merge bases, local generateTotp. |
| J3 | Released [ApplicationSession][session], lines 321–492: Editor.save, Merge.keep/additional; lines 495–594: Frozen, Retry, Partial. |
| J4 | Released [ApplicationStates][publisher]: synchronous subscribe establishment, common-pool signal/drain, replay-latest, cancellation and terminals. |
| J5 | Released [VaultLifecycle][java-lifecycle]: openSession/createSession, VaultId, cleanup; [NioStoreComposition][composition] coordinated-delegate contract. |
| JT1 | Released [PublicApiTest][java-tests]: historical TOTP (196), blocked-provider local work (207/210), receiving-state bases (287–330), foreign references (332), whole-value keep (399–506). |
| JT2 | Released [PublicApiTest][java-tests]: merge additional/failure (530–588), exact retries (589–614), streams (647–733), close/publication/partial races (734–836), read-only NIO open (837–875). |
| JT3 | Released [ObjectCandidateValidationTest][java-validation-tests]: independent ciphertext validation, no state/import/I/O effects, defensive ownership and closure races. |
| D1 | Reviewed [operation model][model]: valid observation, not currentness lease; taxonomy and presentation relevance. |
| D2 | Reviewed [stream contract][stream]: synchronous onSubscribe, asynchronous serialized later callbacks, replay/coalescing. |
| D3 | Reviewed [editing][editing], [merge freshness][merge], [persistence handles][handles], [blocking/close][blocking]. |
| AC | [AndroidVaultController](../app/src/main/java/org/totipo/android/AndroidVaultController.java): product admission, worker, callbacks, generations and Sync phases. |
| FC | [ForegroundVaultCoordinator](../app/src/main/java/org/totipo/android/reconcile/ForegroundVaultCoordinator.java): all live Java ownership, observation, local authoring and TOTP. |
| AT | [AndroidVaultControllerTest](../app/src/test/java/org/totipo/android/AndroidVaultControllerTest.java), referenced by exact test names below. |
| TT | [TotpControllerTest](../app/src/test/java/org/totipo/android/TotpControllerTest.java) and [TotpCoordinatorTest](../app/src/test/java/org/totipo/android/reconcile/TotpCoordinatorTest.java). |

## 7. Android thread/lane map

| Lane | Actual work and Java operation placement |
| --- | --- |
| M: Android main | Activity commands, admission, detached snapshots, row binding, copy and UI timers. No Java open/create/save/close/validateObject or generateTotp here. AC may read cached metadata. |
| W: `Totipo-vault` worker | One thread plus one pending slot. Java G open/create/close; A view/state/VaultId; B TOTP; C factories/setters/keep; E normal merge gate; F local save publication and capability close; D refresh and waits; H candidate validation. Join detached authentication also runs here. |
| P: ProviderIoLane | One `Totipo-provider-io` thread plus one pending slot. SAF listing/reads, create-only provider writes, readback/postflight. No live Java session ownership or builder calls. Initialize copies exact VAULT, not a Java create operation. |
| O: Java observer | `totipo-observation` single executor. Configured local store scans/reads under Java provider gate, then local projection/emission. Merge-save observation instead runs on W under the same Java gate. |
| S: Java subscriber drain | ForkJoin common pool; serialized onNext/error/complete per subscriber, possibly different threads across deliveries. FC signals AC; AC monitor protects fields and dispatcher posts listener delivery to M. No Activity subscribes directly. |
| Subscription establishment | onSubscribe runs inline on W, where FC calls subscribe; atomic subscription storage and demand precede draining. It is not a common-pool callback. |
| L: Application/Activity lifecycle | Main-thread started/stopped counts and foreground transitions; MainActivity checks inactivity before displaying state on create/start/resume. Activity stop detaches, closes prompt/form inputs; does not close the Java session. |
| B: biometric prompt callbacks | Framework main executor, generation/deadline checks, credential decryption/transfer; ordinary Java open then runs on W. |
| T: inactivity Handler/deadline | Main Handler with elapsedRealtime, fixed 15 minutes. Only successful open/user interaction reset it. Timeout retires UI immediately and requests worker-side close. |
| C: clipboard/countdown callbacks | Main clipboard copy and posted ownership-checked clear; wall+monotonic expiry, presentation-only ticks/ring. No Java call or TOTP regeneration per tick. |

FC builders are created, set, saved and closed on W without crossing to P or M.
[LocalReplicaOwner](../app/src/main/java/org/totipo/android/LocalReplicaOwner.java)
leases one root/domain. [CoordinatedPrivateStore](../app/src/main/java/org/totipo/android/reconcile/CoordinatedPrivateStore.java)
serializes every SPI call and complete bridge batch through its own fair gate.
Java observer O also calls this domain; Java's per-session gate alone cannot
coordinate Android's direct bridge mutations. Never enter a Java session API
while holding the Android bridge. Source/tests preserve that order.

## 8. Operation inventory and taxonomy

A = immutable/descriptive projection; B = local secret-backed projection;
C = local state construction; D = observation request; E = freshness-gated
publication; F = frozen publication/continuation; G = session/storage lifecycle;
H = independent candidate validation. These letters classify Java calls, not
all Android transport code. A product action can sequence multiple classes.

Inventory was derived from production searches for VaultState/VaultSession,
states, requestRefresh, generateTotp, createToken, update, merge, save,
PublicationRetry, PartialResolution, AdditionalConflict, validateObject,
Totipo.open/create/vaultId, close, and controller/provider/lifecycle orchestration.
The two joined matrix tables below cover every required field for every row.
Rows 28–29 are retained internal/qualification entry points, not routine UI actions.
Rows 30–32 record absent API workflows without inventing them.

## 9. Full audit matrix

### 9.1 Semantic and implementation matrix

“I/O” means actual configured-store/provider work for the composite action;
local A/B/C calls alone do none. “Block” states Java's contract, even if Android
chooses a worker for nonblocking work. Evidence keys resolve to pinned Java files
or current Android files above, with detailed methods/tests in sections 10–25.

| ID / Android action | Exact Java API calls/types | Class | Captured state/reference | Provider/store I/O | May block: Java contract | Documented expectation / released evidence |
| --- | --- | --- | --- | --- | --- | --- |
| 01 Password open | Totipo.open(SessionView, char[]); OpenResult; states; state | G → A | Newly owned session; wait Finished | Local reads/KDF | Yes G; A no | D3 lifecycle; J1/J5; JT2 creation/open and close cases |
| 02 Biometric open | Same Totipo.open; session.vaultId | G → A | Recovered password; authenticated returned session ID | Local reads/KDF | Yes G | D3 lifecycle/identity; J5; no biometric Java API |
| 03 Create local vault | Totipo.create; CreateVaultResult; states/state | G → A | New session only on Created | Local scan/create/readback | Yes G | D3 create-once/orphan veto; J5; JT2 initialCreationAndOpenTaxonomies |
| 04 Join existing provider vault | Totipo.vaultId; detached Totipo.open/close; SPI createVault; ordinary Totipo.open; session.vaultId | A/G/A, storage G | Exact authenticated candidate, fresh byte recheck; no transferred session references | SAF + local install/read | Yes open/close; A no | D3 G, structural ID ≠ auth; J5; Android bootstrap policy |
| 05 Initialize sync folder | session.vaultId; Totipo.vaultId; exact SPI readVault | A, storage G; provider policy | Exact local 87-byte VAULT snapshot | Local read + SAF create/readback | A no; SPI may block | D3 immutable VAULT; J5; no Java vault-format rewrite |
| 06 Manual Lock | observer cancel; session.close; session-view/domain close | G | Existing owned session/domain | Local cleanup/waits | Yes | D3 close; J1 terminate; JT2 close races |
| 07 15-minute inactivity Lock | Same real close as 06 | G | Monotonic deadline, same session | As 06 | Yes | D3 G; timeout duration is Android policy |
| 08 Foreground/background/rotation | No Java call for transition itself; may start Sync | A/D/H/G in resulting action | Application-owned session survives Activity recreation | None for event; Sync separately | Event not a Java blocking op | D1 ownership; J1/J4 |
| 09 State subscribe/render | session.states().subscribe; Subscription.request/cancel; session.state; tokens/descriptors/heads | A + publisher | Current immutable state read on W; callbacks ignore supplied state | None for A | A no; async drain | D1/D2; J1/J2/J4; JT2 replay/terminal tests |
| 10 Local Refresh | session.requestRefresh; state/view | D → A | No completion correlation; immediate latest view | Request none; O reads local | Request nonblocking | D1/D2; J1 refresh flag/executor |
| 11 Sync provider evidence/identity | Totipo.vaultId; session.vaultId | A | Detached scan epoch, exact provider VAULT, authoritative session ID | SAF first on P | IDs local/no blocking I/O | D3 identity; J5; complete-root rules are Android |
| 12 Sync import | session.validateObject; SPI readObject/publishObject through bridge; requestRefresh | H → storage G → D | Detached encrypted candidates, same authenticated root | Validation none; import local I/O | H may wait gate; SPI yes; D no | D1 H/D; J1 validate; JT3; not MergeToken.save |
| 13 Sync post-import observation | state; states.subscribe; requestRefresh (twice); ObservationProgress.Finished/diagnostics | A/D | Baseline identity per pass, then two fresh Finished emissions | O local reads | D no; Android wait up to 30s/pass | D1/D2; J1/J4; F08 clarification |
| 14 Sync outbound publication | session.validateObject on local and provider bytes; session.vaultId; Totipo.vaultId; provider writer | A/H; provider transport | Exact detached immutable local objects; fresh provider preflight/postflight | Local snapshot + SAF | H may wait gate; transport yes | J1/J5/JT3; not Java E/F semantic save or retry |
| 15 Foreground/unlock auto-Sync | Same 11–14 after opened/foregroundChanged | A/H/D + storage/transport | Current session + binding request | As Sync | As Sync | D1 has no automatic remote sync mandate |
| 16 Post-mutation auto-Sync | Same 11–14 after Saved | A/H/D + storage/transport | Local save already committed; persistent pending hint | As Sync | As Sync | D3 Saved independent of later sync; J3 Frozen.attempt |
| 17 Reveal TOTP | state.token; alternative.descriptor/heads; state.generateTotp | A → B | Fresh captured state; expected detached token basis; owning session | None | Local lock/CPU; no provider wait | D1 B validity vs presentation; J2/JT1 |
| 18 Countdown/ring | No additional Java call; detached TotpCode interval | A-derived presentation | Shown code/validFrom/Until; monotonic deadline | None | No Java call | D1 own time interval; J2 TotpCode |
| 19 Copy | No additional Java call | A-derived presentation | Existing shown code, original expiry | Clipboard only | No Java call | D1 application relevance; Java is not clipboard owner |
| 20 Add | state.createToken; NewSecret.copyOf; issuer/account/algorithm/digits/period/secret; save | C → F | State at worker execution; new logical token | Local publication; later O scan | C local; F may block | D1/D3 no merge gate; J2/J3; JT1/JT2 |
| 21 Edit | state.token; captured.merge(id).keep(selected).issuer.account.save | A → C → E → F if accepted | Exact detached form basis must equal fresh projection; selected complete Alternative | Fresh local observation + write | Save may block | D3 merge captures frontier/no rebase; J2/J3; JT1 keep/JT2 freshness |
| 22 Delete/tombstone | captured.merge(id).keep(selected).status(TOMBSTONED).save | A → C → E → F | As Edit; whole secret retained, status changed | As 21 | Save may block | D3 tombstone/keep; J3; JT1 whole-value keep |
| 23 Resolve | captured.merge(id).keep(selected).save | A → C → E → F | Whole chosen Alternative within current exact captured conflict basis | As 21 | Save may block | D3 normal merge gate; J3 additional; JT2 |
| 24 AdditionalConflict handling | SaveResult.AdditionalConflict.resolution().close | F capability abandonment | Frozen original resolution; latest state not silently adopted | No new publication; gate wait possible | close may wait provider gate | D3 explicit partial choice optional; J3 Partial.close/JT2 |
| 25 Uncertain-save handling | SaveResult.PublicationUncertain.retry().close | F capability abandonment | Exact frozen publication capability discarded | No further publication; gate wait possible | close may wait provider gate | D3 abandonment ≠ rollback; J3 Retry.close/JT2 |
| 26 Provider candidate validation | session.validateObject(RevisionId, byte[]) | H | Exact bounded 1024-byte ciphertext, same root | None in validation | May wait Java gate | D1 H/D3; J1/JT3 |
| 27 Shutdown/process ownership | subscription cancel; session.close; domain close; executor shutdown | G | Application root lease; no Activity Java ownership | Local waits; SAF shutdown does not await IPC | close may block W | D3; J1/J4/J5; process death distinct from graceful close |
| 28 Internal detached sync(scan) / explicit import | validateObject, SPI import, requestRefresh | H/storage G/D | Detached scan; same live session | Local import; explicit import also SAF | H/SPI may block; D no | J1/JT3; refresh report deliberately not an acknowledgement |
| 29 Internal explicit publish | validateObject, IDs; provider exact copy | A/H + transport | Exact detached local evidence, fresh provider plan | Local + SAF | H/transport may block | J1/J5/JT3; no Java semantic save |
| 30 UpdateToken path | state.update not called by production | C/F if used | None | Not used | Hypothetical save may block | D3 receiving-state update semantics; JT1; NOT_USED |
| 31 Retry publication workflow | PublicationRetry.retryPublication not called | F if used | Handles only closed in 25 | Not used | Hypothetical retry may block | J3/JT2; NOT_USED for continuation, ownership audited in 25 |
| 32 Partial publication workflow | PartialResolution.save not called | F if used | Handles only closed in 24 | Not used | Hypothetical save may block | J3/JT2; NOT_USED for continuation, ownership audited in 24 |

### 9.2 Orchestration, state arrival, ownership and disposition matrix

“Generation” is Android session/request/presentation ownership, never a recreated
Java protocol sequence. Finding IDs below carry complete non-ALIGNED evidence.
ALIGNED means no discrepancy found within this review, not universal qualification.

| ID | Android lane/thread | Android admission / disabling | New VaultState behavior | Session-generation behavior | Android source/tests | Classification |
| --- | --- | --- | --- | --- | --- | --- |
| 01 | M → W; O/S initial wait | LOCKED → UNLOCKING; single command | Initial Finished required before open surface | Lock/shutdown retire; opened checks lockRequested | AC authenticate/opened; FC establish; AT password/open/failure tests | ALIGNED |
| 02 | B/M → W | Prompt generation + ordinary unlock admission | As 01 | Prompt active/generation; expected VaultId check; late open retired | SecurityControllerTest explicitEnrollmentAndBiometricReopenUseOrdinaryPasswordOpen | ALIGNED |
| 03 | M → W/O/S | NO_LOCAL_VAULT → CREATING | Wait initial Finished; no synthetic token state | Shutdown/close retain actual ownership | FC create; AT uncertainCreationNeverPublishesOpenAndDiscoversInstalledVault | ALIGNED |
| 04 | M → P → W → P → W | providerActive plus no local vault; exclusive installation | Existing-session state irrelevant before join | Session/binding/cancel flags rechecked across handoffs | AC beginJoin; VaultBootstrapTest; DetachedVaultAuthenticationTest; AT join tests | INTENTIONAL_CLIENT_POLICY F06 |
| 05 | W → P → W | providerActive, readable/writable binding | No semantic state rebase; exact VAULT stable | Session/binding/cancel checks before/after transport | AC initializeSyncFolder; ProviderVaultWriter; VaultBootstrapTest | INTENTIONAL_CLIENT_POLICY F06 |
| 06 | M retirement → W close | Retire immediately; no new command; FAILED_CLOSE retry allowed | Presentation irrelevant after Lock; terminal subscription cancelled | Increment generation immediately; resources null only after successful close | AC requestLock/closeOwned; AT close-failure tests; TT lock tests | INTENTIONAL_CLIENT_POLICY F07 (Java close itself ALIGNED) |
| 07 | T/L → W | Same 06; only user activity resets deadline | Sync/ticks/callbacks do not reset | Deadline generation and session retirement | InactivityLockTest; SecurityControllerTest; AT timeout tests | INTENTIONAL_CLIENT_POLICY F07 |
| 08 | L/M | Rotation keeps session; actual foreground return may queue Sync | Detached reattach, no generic Java protocol validity token | Application owner outlives Activity; stop retires prompt/forms | TotipoApplication/MainActivity; DailyDriverUxTest; TT rotation | ALIGNED |
| 09 | W onSubscribe; S signal; W view; M listener | Observations coalesce, view deferred during command/reveal/Sync | Every signal clears presentation and marks dirty; view uses latest state | Cancellation flag exists; signal lacks captured session/generation guard | FC observe; AC opened/signal/deliver; AT endedJavaSubscriptionProducesErrorAndGatesOperations | CLIENT_MISMATCH F09; presentation restriction also F02 |
| 10 | M → W request; O/S observation | Brief BUSY; return does not await observation | Conceal even if token unchanged; request ≠ completion | Admission blocks closed session; eventual signal caveat 09 | FC requestRefresh; AC refresh; TT refreshConcealsEvenWhenTokenUnchanged | INTENTIONAL_CLIENT_POLICY F01/F02 |
| 11 | P acquisition; W Java identity | providerActive/unifiedSync; no token changes | Identity independent of semantic state advancement | Captured session and binding generations rechecked | ProviderVaultIdentity; AT identityFailureStatusesKeepReadyAndBlockBothControllerDirections | INTENTIONAL_CLIENT_POLICY F06 |
| 12 | W H + exclusive local bridge; O afterwards | Unified Sync excludes authoring; bridge excludes SPI | No state used to authorize import; validated exact bytes | Cancellation between objects; no interrupt during write | FC sync; ForegroundVaultCoordinatorTest sorted/import/unsafe/cancel/lock-order cases | ALIGNED; broader admission F01 |
| 13 | W wait, O store pass, S latch callback | unifiedSync holds product admission; 2 × 30s bounds | Each pass rejects same baseline identity; requires fresh Finished; final diagnostics veto | Cancellation polled; session/binding checked before outbound | FC observeForSync; AT dailyFailedObservationBarrierCannotBeBypassedToPublish and observation timeout cases | NEEDS_JAVA_CLARIFICATION F08 |
| 14 | W snapshot/validation/plan, P write, W confirm | Unified Sync keeps exclusion; independent explicit publish less restrictive | Immutable bytes not rebased; subsequent emissions do not revoke bytes | Session/binding/read+write grants/cancel guards | FC outboundSnapshot/Plan/Confirmation; ProviderObjectWriter; AT outbound tests | ALIGNED Java H; INTENTIONAL_CLIENT_POLICY F06 transport |
| 15 | L/M request; W/P phases | One active + one pending bit; no failure retry loop | Never a generic observation-triggered remote loop | Locked events do nothing; binding changes cancel active operation | AC requestAutomaticSync/queueSyncDrain; AT dailyUnlockAndForegroundAutoSyncAreCoalescedAndLockedForegroundDoesNothing | INTENTIONAL_CLIENT_POLICY F01 |
| 16 | W Saved then M drain/W/P | Local success retained; pending hint persisted | Local observation may arrive later; Sync does not fabricate save success | Retirement/binding cancel Sync; durable local evidence retained | AT dailyAddCommitsOfflineDoesNotLoopAndManualSyncRetries, dailyEdit/Delete/Resolve tests | INTENTIONAL_CLIENT_POLICY F01 |
| 17 | M request → W Java → M reveal-only delivery | No controller BUSY for reveal; single reveal; commands may supersede it | FC rejects any changed state identity; AC dirty/epoch rejects every signal | Epoch, session generation, vault instance, expected token basis before/after work | FC generateTotp; AC showCode/revealCurrent; TT generation/race tests | INTENTIONAL_CLIENT_POLICY F02/F03; no Java invalidity claimed |
| 18 | T/C/M | Existing reveal only; no worker/admission reserved | Clear via presentation policy, not regenerate | Timer generation/expiry prevent stale tick revival | TotpPresentationTest; RowRevealCopyTest actualPeriodDefinesFraction | ALIGNED |
| 19 | C/M | Existing unexpired shown code; blocked during command/unified Sync/form | Cleared on generic signal through F02 | Owned clipboard marker/text; no copying after expiry/Lock | TT copyingKeepsRevealedIdentityAndOriginalDeadline, expiredCopyCannotBeatDelayedTimer | ALIGNED lifetime; admission F01 |
| 20 | W factory/set/save/close | BUSY during local Add; no Sync prerequisite | Builder not rebased; async post-save render reads latest | Request buffer always closed; Lock masks late render; afterExecute closes | FC addToken; AT add/uncertainty/admission tests | ALIGNED Java C/F; INTENTIONAL_CLIENT_POLICY F01/F05 |
| 21 | M form → W complete operation | Form reserves change slot; providerActive blocks Edit | Exact token basis mismatch rejected; unrelated emission alone does not invalidate form | Form identity + session generation; Lock/Activity stop retire | FC changeToken; AT lifecycleEditRetainsCredentialHistorySessionAndExplicitPublish, lifecycleStaleEditAndDeleteDoNotRetry | INTENTIONAL_CLIENT_POLICY F04 |
| 22 | M confirm → W | Same 21; user confirmation required | Same exact-basis check, no silent rebase | Same 21 | AT lifecycleDeleteIsTombstoneConcealsRetainsHistoryAndExplicitPublish | INTENTIONAL_CLIENT_POLICY F04 |
| 23 | M alternative choice → W | Current resolvable conflict; complete chosen Alternative | Basis mismatch or Java AdditionalConflict requires review | Form identity/session generation; no automatic winner/retry | AT lifecycleResolveCompleteLocalAlternative/RemoteAlternative/DeletedAlternative | INTENTIONAL_CLIENT_POLICY F04 |
| 24 | W result/capability close | Return STALE; form gone; user must choose again | Fresh Java conflict result not silently rebased | Handle never crosses owner/thread/session | AT lifecycleJavaFreshnessGateSeesUnobservedLegitimateRevision | INTENTIONAL_CLIENT_POLICY F05 |
| 25 | W result/capability close | Surface PUBLICATION_UNCERTAIN; no automatic retry/Sync-on-failure | Refresh can discover persistence; no assumed rollback | Handle closed once in operation boundary; frozen bytes not transferred | AT uncertaintyCanHavePersistedAndExplicitRefreshObservesSameSession; lifecycleActualFailedAndUncertainLocalWritesDoNotRetryOrPublish | INTENTIONAL_CLIENT_POLICY F05 |
| 26 | W | Within owning worker action; Java gate handles observer contention | Validation descriptive, no current-head claim | SessionClosed unavailable; valid detached ciphertext survives | ImmutableCandidateClassifierTest; ForegroundVaultCoordinatorTest; JT3 | ALIGNED |
| 27 | M request → W | shuttingDown; no replacement executor or session | Late state/presentation retired; F09 callback guard gap remains | Generation increment; deferred close; lease held until close | SecurityControllerTest shutdownCancelsDeadlineAndClosesSession / shutdownDuringPasswordOpenCannotStartSessionDeadlineOrEnrollment | ALIGNED ownership; F07 deferred retirement |
| 28 | W; explicit import adds P | Internal sync(scan) uses BUSY; explicit import conceals | report.latestObservation is not refresh-correlated | Provider requests session/binding guarded; Lock cancels transport | AT credentialFreeProductSyncImportsM1IFixtureAndPublishesLaterDetachedState; FC refreshReportDoesNotMistakeReplayedFinishedForImportCompletion | ALIGNED report semantics; policy F01 |
| 29 | W/P | providerActive; global OPEN and shown code preserved at admission | Frozen local evidence remains valid | Session/binding/grant/cancel checks on handoffs | AT outboundExactPostflightRetryPreservesSessionStoreAndRevealedCode | ALIGNED evidence; transport F06 |
| 30 | None | No UpdateToken workflow | Not applicable | Not applicable | Production search finds no .update call | NOT_USED |
| 31 | None (close on W in 25) | No retry capability transfer workflow | Not applicable | Not retained | Production search finds only retry().close | NOT_USED |
| 32 | None (close on W in 24) | No partial-save workflow | Not applicable | Not retained | Production search finds only resolution().close | NOT_USED |

## 10. State subscription audit

FC `establish`/`create` wait through `awaitObservation`; FC `observe` installs the
long-lived controller subscription; FC `observeForSync` installs temporary ones.
All call subscribe on W. onSubscribe stores the subscription atomically before
requesting Long.MAX_VALUE. Synchronous establishment is safe: there is no await
inside onSubscribe and no assumed preinitialized callback worker. Released J4
sets `started` only after onSubscribe, preventing reentrant demand from invoking
onNext inline. Draining can nevertheless start before subscribe returns.

For initial/Sync waits, S only updates an atomic failure and CountDownLatch;
W checks current state and cancels in finally. For the long-lived stream, S calls
AC `signal`, which uses the controller monitor, then posts UI listener work to M.
`deliver` tests listener attachment at delivery and reads the current detached
snapshot. MainActivity never sees Java editors or a session. No callback attempts
a blocking Java operation while holding the publisher monitor.

No hidden Java sequence is reflected into a protocol version. Two different
uses of identity need distinction: FC Sync's baseline comparison is an Android
observation policy (F08); FC TOTP's changed-state rejection is a conservative
presentation policy (F02). Both go beyond merely consuming descriptions.
The long-lived callback path has a session-ownership race: cancellation guards
are not an atomic controller-generation check. See suspected mismatch F09.

## 11. TOTP/reveal audit

FC `generateTotp` runs on W. It captures session.state, requires Finished, no
vault diagnostics/integrity problems, and a single active, nonconflicting token
without unresolved references. It compares expected detached descriptor/heads,
uses that state's same-session Alternative, calls Java B, validates code length
and interval, and returns only detached fields. Java J2/JT1 needs no provider
freshness and permits historical/conflicting/tombstoned alternatives; Android's
eligibility restriction is its own safety/UX policy (F03).

AC showCode leaves controller State.OPEN, preserves the ordinary message/view,
and reserves only `revealing`; it does not claim global BUSY. Real Add/Edit/Delete/
Resolve/Sync commands retire queued reveal or make an in-flight result irrelevant.
Before and after generation, epoch, session generation, vault identity, expected
token basis, dirty/failure state, operation/form and unified-Sync status gate display.
MainActivity listener.revealChanged updates rows only. Countdown/copy do not
publish a normal screen state or repeat Java crypto. TotpPresentation uses both
wall interval and elapsed deadline; copy retains original expiry and clears only
its own clipboard content.

The recent correction succeeds at removing reveal-created global BUSY. It does
**not** remove generic state-arrival invalidation: FC rejects `session.state() !=
captured`, AC signal always clears, and render/publish clears again. Thus local
projection validity and presentation relevance are separated at the Java call
boundary, but Android still deliberately chooses a broad relevance rule. The
unrelated-token rejection test proves this restriction is real (F02), not just
protection against a changed selected token. No further fix is made.

## 12. requestRefresh/observation audit

FC requestRefresh changes its own coordinator admission state briefly; Java
requestRefresh only queues/coalesces a local pass. AC refresh wraps even this
nonblocking request in worker/BUSY policy, then renders the currently available
view. It says “Refresh requested”, not “completed”. FC import Report explicitly
retains latestObservation without claiming correlation. The test
`refreshReportDoesNotMistakeReplayedFinishedForImportCompletion` blocks Java's
scan and proves report/latest state can remain the pre-import observation.

J1 refresh sets a pending atomic flag, enqueues a single-observer task, and clears
the flag after acquiring the provider gate before observing. Calls while queued
can coalesce; a call during an executing pass may schedule another pass. J4 is
replay-latest and can skip intermediate emissions even with unlimited demand if
its drain is delayed. Finished describes a completed pass, not complete history
or remote receipt. None of these are a Java per-request acknowledgement.

## 13. Unified Sync audit

The actual ordinary product pipeline is:

1. On P, probe/read detached SAF evidence; on W, validate exact provider VAULT
   identity against the open session. Accessibility READY is separate from identity.
2. On W, H validates candidates. A whole local bridge batch imports exact immutable
   objects; it excludes observer SPI calls, never enters a Java API under the bridge,
   and marks the domain unsafe on failed/uncertain local publication.
3. After bridge release, D requests observation when objects were imported.
   `safeToPublishAfterImport` separately requires complete transport coverage,
   no invalid/unavailable candidates/contradictions, no failure/unattempted objects
   and a safe coordinator outcome. Positive incomplete import can be useful at
   the internal boundary while ordinary unified Sync refuses outbound publication.
4. FC observeForSync performs **two** baseline/subscription/refresh/wait passes.
   Each requires a different Finished state; final diagnostics/integrity veto.
   W waits in 50 ms cancellation intervals, up to 30 seconds per pass.
5. On W, render the observed view and capture all exact local immutable ciphertext
   through the bridge, then release it before H validation. On P acquire fresh
   outbound provider evidence; W plans; P creates only missing immutable objects,
   reads back, freshly scans; W authenticates/confirms postflight and identity.

Phase 5 is **not MergeToken.save, PartialResolution.save or PublicationRetry**.
Java semantic publication F/E happened when authoring the local evidence. Sync
copies exact ciphertext through Android/provider transport; neither a new state
nor provider delay rebases that evidence. Freshness here is provider transport
safety and Android import-before-publish policy. Confirmation means verified
against this bound folder, not another device's receipt.

F08 separates an implementation argument for the two-pass barrier from its weaker
public-contract guarantee. No demonstrated false-publication trace was found.
Cancellation/diagnostic/time-limit failures refuse publication. No device or
stress run in this docs milestone is claimed to establish barrier correctness.

## 14. Auto-Sync audit

AC opened triggers ordinary automatic Sync for successful password/biometric open
and Join (create also follows opened). Application started/stopped counts suppress
rotation-only foreground requests. Meaningful foreground return requests Sync;
locked foreground events cannot start it. Saved Add/Edit/Delete/Resolve sets the
persistent pending-publication hint then requests Sync. Failed/uncertain save does
not manufacture Saved or initiate success-triggered Sync.

`syncPending` coalesces requests to one active + one pending; finish does not
self-enqueue a retry on failure. `providerActive`, `unifiedSync`, worker admission,
view queue and pending form serialize incompatible product work. Root SPI/bridge
coordination is required by Java's experimental coordinated-delegate contract;
one user-level Sync plus one pending and exclusion of authoring throughout SAF
IPC are Android ownership/product simplification. Java itself does not require
serializing A/B/C behind unrelated provider IPC (F01).

## 15. Password open audit

AC transfers/wipes the mutable credential on every admission/rejection path.
FC opens one leased coordinated domain and transfers its logical SessionView to
Totipo.open. Java alone handles Argon2/authentication, format and OpenResult.
FC waits for initial Finished; failed opening retains coordinator ownership for
close. AC re-discovers canonical VAULT after failure/uncertain creation rather
than assuming absence. Empty password remains protocol-valid at the controller
boundary; no normalization or new Android cryptographic parser is introduced.
W performs all Java blocking work. AuthenticationFailed is surfaced as failure
to unlock, not proof the user typed a wrong password. Existing AT tests cover
thread delivery, wrong credentials, unavailable/unsafe storage, empty password,
initial observation, failure ownership and Activity detach/reattach.

## 16. Biometric open audit

[BiometricUnlock](../app/src/main/java/org/totipo/android/BiometricUnlock.java)
and [FrameworkBiometricPrompt](../app/src/main/java/org/totipo/android/FrameworkBiometricPrompt.java)
use main-executor strong-biometric CryptoObject authorization to recover the vault
password. Generation/active/deadline checks reject late prompt results; stop closes
the transaction. Mutable recovered char[] ownership transfers to AC.unlockBiometric,
which uses the same backend.open/FC/Totipo.open G path. The authenticated resulting
session.vaultId must match the biometric record; mismatch deletes that record and
closes the newly opened session. No biometric route supplies a root key, bypasses
Java authentication, or replaces Java open semantics.

Enrollment is explicit and bounded to 60 seconds; its PasswordBuffer is wiped on
cancellation, expiry, Lock, backgrounding or rejected generation. Successful open
uses ordinary opened/inactivity/auto-Sync flow. SecurityControllerTest covers
ordinary-open counts, recovered-buffer wiping, wrong recovered password, different
authenticated VaultId, late/cancelled enrollment, and shutdown during open. Physical
security report evidence is inherited, not rerun. No finding requires biometric
production changes in this milestone.

## 17. Inactivity and manual Lock audit

[InactivityLock](../app/src/main/java/org/totipo/android/InactivityLock.java)
uses elapsedRealtime through the controller clock and a 15-minute deadline.
Only open and actual interaction reset it; automatic Sync/TOTP/observer work does
not. Background time counts; Activity create/start/resume and foreground return
check expiration before exposing snapshots. Manual/timeout Lock share requestLock.

Immediate retirement: stop deadline/enrollment, cancel outbound, clear form/pending
Sync, increment session generation, publish LOCKING without view/code. Completion:
after any running W operation returns, afterExecute invokes closeOwned, cancels the
subscription, closes real Java session, closes logical/physical domain, then releases
the lease and discovers locked state. A failed close retains vault/domain/lease,
shows FAILED_CLOSE and permits explicit retry; nulling does not precede affirmed
close. Java session close can wait for admitted local store work. SAF IPC owns only
detached ciphertext and can remain blocked without keeping a Java session alive.
An already entered provider write can still finish; Lock is not rollback.

F07 documents that Java close is deferred until the worker yields, so key retirement
completion is not instantaneous at the UI deadline. The controller says LOCKING
rather than claiming completed close. Tests cover timeout during reveal, import,
Java observation and provider IPC; pending forms; failed physical-domain close;
and stale provider/reveal results. F09 separately concerns subscription signals.

## 18. Add audit

FC addToken decodes/wipes setup bytes, creates NewSecret and a fresh state.createToken
builder on W, sets issuer/account/algorithm/digits/period/secret, and saves once.
Create is C → F with no merge-only observation gate. Builder/secret/request remain
confined and closed. Saved initiates post-mutation auto-Sync; local success persists
on provider failure. Failed retains Java reason; PublicationUncertain closes its
retry and instructs observation; defensive unexpected AdditionalConflict closes
its partial capability without claiming success. No later state silently rebases
a builder. AT tests cover real authoring/algorithms/periods, malformed secret and
field limits, uncertainty with possible persistence, rejected/busy ownership and
Lock while a save is in flight. Retry abandonment is F05.

## 19. Edit audit

A form retains only detached ObservedToken basis, not a live Alternative/builder.
At confirmation FC reads a fresh captured state and requires exact equality with
the form basis, Finished/no diagnostics/integrity problems, and a live token.
It constructs `captured.merge(token.id()).keep(selected)` then overrides issuer/
account and saves. The builder is fresh for this explicit user operation and
captures Java's causal basis at construction; it is not silently rebased later.
Changes to an unrelated token alone do not alter the form's token-basis equality.

Choosing merge rather than update adds a real E freshness gate even for a single
value. Released J3 keep retains the complete semantic value including hidden
credential; later setters override only the selected fields. Client “issuer/account
metadata” here means semantic token labels; Android does not call editor.metadata
or promise preservation of per-revision ClientMetadata. The strategy is consistent
with Java 0.2.0, with stricter user-context preflight as F04. Tests check retained
secret/history/session, stale edit rejection, unobserved concurrent revision,
uncertain local write and post-success offline Sync.

## 20. Delete audit

Delete follows the same captured merge/keep path, overriding only status to
TOMBSTONED after explicit confirmation. It authors immutable encrypted history,
not erasure, and retains the hidden authenticator value according to whole-value
keep. Java still allows explicitly selecting a tombstoned Alternative for local
TOTP; Android hides that action as policy F03. Saved triggers automatic Sync of
the tombstone. AT lifecycleDeleteIsTombstoneConcealsRetainsHistoryAndExplicitPublish,
lifecycleStaleEditAndDeleteDoNotRetry and dailyDeleteAutomaticallyPublishesTombstone
supply direct evidence. No generic emission-based builder cancellation/rebase exists.

## 21. Resolve and AdditionalConflict audit

Resolve offers complete Alternative values, including Deleted; not field mixing,
majority selection or newest-wins. FC checks exact detached conflict context,
resolvable shape and selected index, captures merge(id), keeps the chosen value,
then saves. E observes fresh local evidence and checks causal containment of current
heads against captured frontier/ancestry. AdditionalConflict publishes nothing;
Android closes the independent frozen partial handle, returns STALE and requires
another explicit choice. It does not use `latest` as an automatic rebase and does
not call PartialResolution.save to bypass the gate. Successful resolution triggers
ordinary automatic Sync. F04/F05 classify the extra client preflight/abandonment.

Released tests include keepRetainsAdditionalConflictWithoutPublication (including
equal-valued new heads), mergeAdvancingDescendantIsAdditionalInformation,
unavailableMergeObservationIsDefiniteNoWriteAndBuilderRemainsEditable and captured
ancestry cases. AT lifecycleJavaFreshnessGateSeesUnobservedLegitimateRevision and
all three lifecycleResolveComplete/DeletedAlternative cases exercise the real Java
boundary. No Java docs/implementation contradiction was found here.

## 22. Retry and partial capabilities audit

Production **does receive and close** PublicationRetry/PartialResolution through
SaveResult; calling these capabilities entirely NOT_USED would be inaccurate.
Actual continuation calls retryPublication and PartialResolution.save are NOT_USED.
Ownership remains W-local; no handle is stored, transferred, persisted or retried.
Try-with-resources closes the editor independently of result-handle cleanup.

Released J3 and JT2 establish exact frozen bytes, independent capability transfer,
monotonic uncertainty and close/session invalidation. Abandonment does not undo
possible persistence. Android preserves uncertain status and offers local refresh
rather than a second semantic save. ProviderObjectWriter retry is a fresh Android
transport preflight/copy of immutable ciphertext, not Java PublicationRetry.
F05 is a deliberate product limitation with recovery/latency costs, not a Java
correctness violation. No new continuation behavior tests were added.

## 23. Join audit

AC prepares bounded detached provider evidence on P, authenticates the exact
candidate via DetachedVaultAuthentication's transient read-only SPI Totipo.open
on W, immediately closes that temporary session, then requires a fresh provider
byte-exact reread. It acquires the local root lease/domain, vetoes existing local
VAULT or plausible orphan object names/incomplete namespace, installs exact bytes
create-only through the bridge, rereads exact local bytes, and ordinarily opens.
Resulting session.vaultId must equal the candidate's structural identity.

Detached authentication/open/close is G, structural identity is A, and direct
SPI installation is storage/bootstrap G. It is **not H validateObject**, which is
for objects-v1 ciphertext against an already authenticated session root. Root
multiplicity, permission checks and byte rechecks are Android/provider bootstrap
policy F06. Authentication is never inferred from VaultId alone. No candidate
session reference crosses into the ordinary open session. Joined successful open
triggers regular Sync; Join itself does not silently copy token history or create
a different vault/password. AT join tests, DetachedVaultAuthenticationTest,
VaultEnrollmentTest and VaultBootstrapTest cover authentication, replacement,
orphan veto, cancellation and exact enrollment.

## 24. Initialize audit

W snapshots exact local canonical VAULT under the bridge, releases it, and compares
Totipo.vaultId with session.vaultId. P obtains complete provider evidence and uses
ProviderVaultWriter create-only publication/readback/fresh postflight, including
objects-v1 directory creation. Existing provider token-name evidence without VAULT
vetoes bootstrap. Exact existing VAULT is reused, never rewritten. Verified VAULT
is retained when directory creation is partial; retry may create the missing directory.
This is Android transport/bootstrap policy, not Totipo.create or a Java migration/
password change. No re-encryption, root export or format parsing is added. F06
classifies conservative root checks. AT initializeLockDuringBlockedCreatePreventsWriteAndStaleCompletion,
initializeThenManualPublishOnlyAndRepeatedInitializationNoWrites and
VaultBootstrapTest establish create-only and cancellation behavior. The retained
“Publish explicitly” diagnostic is historical wording; ordinary UI Sync performs
publication. It does not alter Java semantics and is not edited here.

## 25. Provider identity and validation audit

ProviderVaultIdentity requires complete root listing, one exact VAULT row and one
bounded candidate; duplicate VAULT rows block even if byte-identical. Locator,
parent/tree/epoch/type/length checks precede Totipo.vaultId comparison with the
open session. A different identity blocks import/publication, never adopts or
repairs another root. These are transport safety choices layered over Java's
structural identity API, which runs no authentication/KDF/freshness check.

ImmutableCandidateClassifier bounds reads to 1024 plus overflow probe, constructs
canonical RevisionId and calls session.validateObject on W. Results are owned
ciphertext observations, not trusted import capabilities. Same-root independent
validation precedes contradiction comparisons; local outbound representations
are separately validated after the bridge releases. Validation performs no store
I/O or state mutation but can wait behind Java's provider gate. Current-head
membership is neither assumed nor needed for immutable replication. Identity,
readback and postflight checks do not claim remote history completeness or rollback
resistance. F06 marks conservative SAF rules, not Java requirements.

## 26. Stricter-client-policy findings

Each finding below includes the required evidence dimensions. Recommendation
for every finding: **review only; no implementation now**.

### F01 — Product serialization beyond Java operation requirements

- **Android behavior:** AC submit uses one bounded W command; unified Sync excludes
  Add/Edit/Delete/Resolve/reveal/copy through providerActive/unifiedSync and form
  admission. D refresh briefly uses BUSY. Open/create waits for the first Finished
  before presenting OPEN; FC awaitObservation has no timeout and preserves interruption
  rather than abandoning the owned session. Reveal itself does not reserve global BUSY.
- **Java documented rule:** D1 separates A/B/C/D from E/F/G/H; Java per-session
  provider serialization does not require a global UI BUSY state.
- **Java 0.2.0 implementation evidence:** J1 local/provider locks; J2 TOTP/factories;
  JT1 localOperationsCompleteWhileObservationIsBlocked and WhilePublicationIsBlocked.
- **Existing Android test evidence:** AT dailyRequestsBoundedDuringActiveSyncAndLocalAdmissionCannotRace;
  TT addAdmittedDuringGenerationKeepsRealBusyAndRejectsReveal and
  syncEnabledAndAdmittedDuringGenerationUsesExistingWorkerOrdering; DailyDriverUxTest.
- **Classification:** INTENTIONAL_CLIENT_POLICY.
- **User-visible/correctness impact:** simplifies ownership and prevents competing
  provider operations; excludes valid local work during long Sync and adds latency.
  No Java-required serialization or deadlock guarantee should be inferred.
- **Candidate correction locus:** intentional Android policy / ANDROID if relaxed.
- **Recommendation:** review only; no implementation now. Keep store-gate safety
  separate from optional UI admission; decide per operation before later changes.

### F02 — Generic state advancement still retires local TOTP presentation

- **Android behavior:** FC generateTotp rejects any `session.state() != captured`;
  AC signal unconditionally clears presentation/advances epoch and marks dirty.
  revealCurrent rejects dirty or superseded epochs; render/publish conceals as well.
- **Java documented rule:** D1 says newer states alone do not invalidate B or old
  same-session references; display relevance remains application-owned.
- **Java 0.2.0 implementation evidence:** J2 generateTotp resolves the supplied
  same-session value under local lock, with no current-state identity check;
  JT1 totpIsDeterministicAndLocalIncludingHistoricalTombstone and blocked-I/O tests.
- **Existing Android test evidence:** TotpCoordinatorTest.authoritativeStateChangesDuringGenerationRejectResult
  authors an **unrelated new token** during generation and expects STALE.
  TT refreshConcealsEvenWhenTokenUnchanged explicitly expects concealment;
  stateReplacementBeforeControllerPublicationRejectsReveal covers actual token change.
- **Classification:** INTENTIONAL_CLIENT_POLICY, with necessity/UX rationale to decide.
  Existing tests deliberately demand the restriction; no Java invalid-reference bug
  or automatic CLIENT_MISMATCH is inferred from stricter display policy.
- **User-visible/correctness impact:** unrelated refresh/import/authoring can hide
  a valid code or discard a valid computation, causing extra reveal effort/flicker.
  This is presentation conservatism; it supplies no provider freshness proof.
- **Candidate correction locus:** ANDROID / intentional Android policy. Java docs
  and released local-projection implementation agree on validity.
- **Recommendation:** review only; no implementation now. Decide whether unchanged
  selected-token basis plus ownership/epoch/time should suffice; document broad
  concealment explicitly if retained. The recent repaint fix does not settle this.

### F03 — Reveal eligibility requires a globally clean observation

- **Android behavior:** FC TOTP requires Finished, zero diagnostics/integrity
  problems and a single active nonconflicting fully resolved token. Tombstone,
  conflict/historical choice and unrelated vault diagnostics prevent reveal.
- **Java documented rule:** D1/D3 allow historical, conflicting and tombstoned
  Alternatives for B while the owning session is open; no provider freshness.
- **Java 0.2.0 implementation evidence:** J2 local generateTotp; JT1 deterministic
  historical/tombstone test and reference-scope tests.
- **Existing Android test evidence:** TotpCoordinatorTest tombstoneRefusesWithoutCrypto,
  twoRealCurrentAlternativesRefuseWithoutArbitraryChoice, missingParentRealJavaStateRefuses;
  TokenListAdapter.usable and RowRevealCopyTest enforce row policy. A focused test
  for an unrelated diagnostic preventing a healthy token reveal was not identified.
- **Classification:** INTENTIONAL_CLIENT_POLICY.
- **User-visible/correctness impact:** avoids arbitrary secret choice and confusing
  unhealthy-state display, at the cost of rejecting valid local projections. No
  evidence that Java requires every vault diagnostic to block every token.
- **Candidate correction locus:** intentional Android policy / ANDROID if narrowed.
- **Recommendation:** review only; no implementation now. Decide global vs token-local
  eligibility independently of Java reference validity.

### F04 — Exact user-context preflight and merge-backed metadata changes

- **Android behavior:** Edit/Delete/Resolve require exact detached token basis and
  clean Finished observation; Edit/Delete use captured.merge(id).keep(selected),
  not update. Changed context requires a fresh user choice; no silent retry/rebase.
- **Java documented rule:** D3 supports captured/historical construction, explicit
  receiving-state bases, whole-value keep, and merge-only freshness at save.
- **Java 0.2.0 implementation evidence:** J2 receiving-state factories; J3 keep,
  frontier/ancestry additional gate; JT1 updateUsesReceivingStateEvenWhenSessionHasAdvanced,
  keepAllowsLaterExplicitSettersAndRepeatedSelection; JT2 additional-information tests.
- **Existing Android test evidence:** AT lifecycleStaleEditAndDeleteDoNotRetry,
  lifecycleStaleConflictAndLockRejectChoice, lifecycleJavaFreshnessGateSeesUnobservedLegitimateRevision,
  edit/delete/whole-Alternative resolve tests.
- **Classification:** INTENTIONAL_CLIENT_POLICY; strategy is consistent with Java.
- **User-visible/correctness impact:** protects reviewed user context and adds fresh
  local observation cost; rejects some valid historical authoring. Unrelated state
  emission alone does not rebase an existing builder or invalidate a form basis.
- **Candidate correction locus:** intentional Android policy / ANDROID for future UX.
- **Recommendation:** review only; no implementation now. Preserve whole-value
  credential semantics and distinguish token labels from revision ClientMetadata.

### F05 — Frozen continuation capabilities are intentionally abandoned

- **Android behavior:** uncertain.retry().close and conflict.resolution().close;
  no retryPublication/PartialResolution.save. User must observe/review again.
- **Java documented rule:** D3 permits exact frozen retry/partial save, independent
  ownership/transfer; closing a capability does not undo possible persistence.
- **Java 0.2.0 implementation evidence:** J3 Frozen/Retry/Partial; JT2 exact retries,
  independent partial resolution, partial uncertainty and session invalidation.
- **Existing Android test evidence:** AT uncertainPublicationKeepsActualOutcomeNoAutomaticRetryOrFabricatedRow,
  uncertaintyCanHavePersistedAndExplicitRefreshObservesSameSession,
  lifecycleActualFailedAndUncertainLocalWritesDoNotRetryOrPublish; freshness-gate test.
- **Classification:** INTENTIONAL_CLIENT_POLICY. Continuation APIs themselves NOT_USED.
- **User-visible/correctness impact:** simpler ownership but poorer recovery for
  uncertain multi-stage writes; possible persistence remains and must be observed.
  Repeating Add/Edit as new semantics is not equivalent to exact frozen retry.
- **Candidate correction locus:** intentional Android policy / ANDROID if continuation UX added.
- **Recommendation:** review only; no implementation now. Decide capability UX and
  lifetime explicitly; do not automatically “retry” by reconstructing a builder.

### F06 — Conservative SAF identity/bootstrap/transport gates

- **Android behavior:** reject duplicate VAULT roots, incomplete listings and orphan
  names at bootstrap; exact provider rechecks, create-only/readback/postflight;
  wrong vault never adopted. Positive incomplete imports may be retained internally,
  while ordinary unified Sync blocks outbound on incomplete/invalid evidence.
- **Java documented rule:** D3 VaultId structural recognition is not authentication;
  H validates objects independently and does not import or certify freshness.
  Java immutable VAULT/create-once semantics do not define SAF document selection.
- **Java 0.2.0 implementation evidence:** J5 VAULT lifecycle/identity/composition;
  J1 validateObject and JT3 independence/defensive ownership; release SPEC_PIN r19.
- **Existing Android test evidence:** AT shared identity/fault/status and Join/Initialize
  tests; ForegroundVaultCoordinatorTest incompleteAndMalformedTransportDoNotBlockPositiveImports;
  VaultBootstrapTest, ProviderObjectWriterTest, OutboundImmutablePlannerTest,
  ImmutableCandidateClassifierTest; historical M3B/M3C/M3D device evidence.
- **Classification:** INTENTIONAL_CLIENT_POLICY (transport safety).
- **User-visible/correctness impact:** blocks ambiguous yet potentially usable provider
  folders, favoring no cross-vault/overwrite mutation. Freshness evidence is scoped
  to the observed folder, with no remote-receipt/rollback-resistance claim.
- **Candidate correction locus:** intentional Android provider policy / ANDROID.
- **Recommendation:** review only; no implementation now. Preserve provenance and
  distinguish SAF safety from Java's narrower cryptographic facts.

### F07 — Immediate UI retirement precedes Java close entry/completion

- **Android behavior:** Lock publishes LOCKING and increments generation immediately;
  real session.close runs on W after the current operation yields. Failed domain
  close retains ownership for retry. SAF IPC can finish detached work after retirement.
- **Java documented rule:** D3 session.close rejects entrants when Java marks closing,
  waits for provider work, and never downgrades Saved/uncertainty; UI retirement is
  not a Java close acknowledgement.
- **Java 0.2.0 implementation evidence:** J1 terminate lock order; JT2
  closeWaitsForPotentiallyPersistedWriteAndPreservesSavedOrUncertain and close races.
- **Existing Android test evidence:** AT busyAddRejectsDuplicateAndLockRetiresAfterInFlightSave,
  openDomainCloseFailureDoesNotReleaseLeaseAfterSessionClosed, timeout/daily Lock tests;
  TT lock/inactivity delayed reveal tests; SecurityControllerTest real deadline/shutdown.
- **Classification:** INTENTIONAL_CLIENT_POLICY, with Java close semantics ALIGNED.
- **User-visible/correctness impact:** sensitive UI/form results disappear immediately;
  key wiping can be delayed by local work. LOCKING/FAILED_CLOSE accurately avoid an
  unsupported completed-close claim. No fixed close-latency guarantee is established.
- **Candidate correction locus:** intentional Android lifecycle policy / ANDROID if revised.
- **Recommendation:** review only; no implementation now. Keep retirement and close
  completion distinct in later security/liveness decisions.

## 27. Suspected Android mismatches

### F09 — Long-lived subscriber signals lack controller session ownership

- **Android behavior:** AC opened installs `current.observe(() -> signal(false),
  () -> signal(true))`. Neither lambda captures/validates sessionGeneration or
  coordinator identity inside synchronized signal. FC observe checks a separate
  cancelled AtomicBoolean before calling the Runnable; cancellation can race after
  that check. signal clears presentation/marks dirty or observationFailed globally.
- **Java documented rule:** D1 says clients own lifecycle/late-result relevance;
  D2 serializes per subscription, not across old/new subscriptions. Cancellation
  abandons delivery; it cannot retract application code already executing.
- **Java 0.2.0 implementation evidence:** J4 Subscription.run obtains its callback
  outside the publisher monitor; cancel sets ended but does not await an entered
  callback. J1 close likewise does not await subscriber callbacks. JT2
  closeFromOnNextCompletesWithoutWaitingForItsOwnCallback corroborates that boundary.
- **Existing Android test evidence:** AT endedJavaSubscriptionProducesErrorAndGatesOperations
  verifies current-session terminal behavior; reattach/Lock tests verify ordinary
  retirement. Provider/reveal paths explicitly have generation tests. No focused
  test holds an old observation Runnable after FC's cancellation check, then releases
  it after Lock/reopen. That is an evidence gap, not a newly executed failing test.
- **Classification:** CLIENT_MISMATCH, **suspected static race**, not runtime-reproduced.
- **User-visible/correctness impact:** a permitted interleaving is old callback passes
  FC's cancelled check → is descheduled → Lock cancels/closes old owner → ordinary
  reopen installs a new subscription → old callback resumes into AC signal. An old
  onNext can conceal a new reveal; an old terminal can set observationFailed and
  gate a healthy new session. No foreign Java Alternative crosses sessions; the
  problem is Android callback relevance. Ordinary ordering usually masks it.
- **Candidate correction locus:** ANDROID. Released Java behavior matches reviewed
  threading/cancellation semantics; no reason found to demand Java wait for callbacks.
- **Recommendation:** review only; no implementation now. Later decide and deterministically
  test a coordinator/session-generation check at the actual controller mutation boundary.
  No production or behavior-test edit was made to explore this candidate.

F02 is a separate restrictive policy, not counted as another established client
mismatch. No other concrete Java contract violation was established in this review.

## 28. Suspected Java documentation issues

No JAVA_DOC_SUSPECT finding is established for historical references, local TOTP,
merge/keep, retry/partial capabilities, validation, lifecycle or subscriber threading.
Released J1–J5 and existing release tests support the reviewed rules. The original
common-pool wording was inaccurate for onSubscribe, but is already corrected in
the guidance pin; published 0.2.0 Javadoc still has its older wording. Use the pinned
repository clarification rather than misclassifying Android's safe onSubscribe as
wrong. F08 asks for a more precise observation-composition contract; it does not
assert the reviewed coalescing/replay rules are false.

## 29. Suspected Java implementation issues

No JAVA_IMPLEMENTATION_SUSPECT finding is established. The released implementation
matches the operation taxonomy in the inspected paths; no newer executable fix is
needed to consume the guidance. This audit is not a fresh Java suite run, exhaustive
formal proof, cryptographic review or general provider qualification. Source/test
comparison is scoped to Android's actual use and the questions above.

## 30. Ambiguous / needs-clarification findings

### F08 — Two-emission Sync barrier has an implementation argument, not a refresh acknowledgement

- **Android behavior:** FC observeForSync twice captures baseline state, subscribes,
  requests refresh, and accepts a different Finished emission. It comments that the
  second pass necessarily starts after the first completes, authorizing outbound
  only after final clean diagnostics. It does not correlate an emission to a request ID.
- **Java documented rule:** D1/D2: requestRefresh is nonblocking/coalescible and not
  a completion barrier; replay-latest may omit states. Finished is only a local pass
  ending. Observation/emission is serialized; state identities/sequences are not
  freshness leases or remote synchronization evidence.
- **Java 0.2.0 implementation evidence:** J1 observePass emits one Finished after
  complete local read/evaluation. One provider gate serializes observer passes and
  merge-save observation; refresh pending resets at pass entry. J4 suppresses old
  sequences and delivers latest only. All successful local bridge imports finish
  before the first baseline is captured.
- **Implementation argument:** if the first accepted state came from an observation
  begun before/during import, it still finishes after the captured baseline. A
  distinct subsequent emitted state cannot be that same completed pass. Because
  observation/emission is serialized in this release, its pass begins after the
  earlier one finishes, hence after bridge import. If replay skips passes, the
  accepted state is later rather than older. During unified Sync Android excludes
  local authoring; no independent local writer is permitted. Unrelated completed
  observations could satisfy a wait, but in this implementation they share that
  serialization. Request coalescing can delay or eliminate a particular requested
  pass, causing a conservative timeout; it does not demonstrate early authorization.
- **Contract limit:** this argument relies on what “serialized observation/emission”
  means for pass start/read/end and every emitter, plus this implementation's one
  Finished emission per pass and stable local domain. There is no public observation
  acknowledgement or per-request causal token. Counting two states must not be
  generalized to arbitrary future implementations, provider consistency, current
  heads, or Java merge freshness. A bounded Finished pass can still have incomplete
  history without diagnostics; no barrier creates remote completeness.
- **Existing Android test evidence:** AT dailyManualSyncImportsAndObservesRealConcurrentBranchBeforePublication
  verifies real conflict visible before outbound; dailyFailedObservationBarrierCannotBeBypassedToPublish,
  dailyLockDuringJavaObservationWaitCancelsBeforeOutboundPhase and timeout cases cover
  failure/cancellation. FC refreshReportDoesNotMistakeReplayedFinishedForImportCompletion
  proves a request alone is insufficient. No focused adversarial test exhausts
  coalescing, delayed replay, unrelated passes and a pre-import scan at both waits.
- **Classification:** NEEDS_JAVA_CLARIFICATION. No demonstrated Android false-publication
  bug, no Java implementation defect asserted, no automatic CLIENT_MISMATCH.
- **User-visible/correctness impact:** two local observations and up to 60 seconds
  of application wait before outbound; conservative failure can delay Sync. Wrongly
  broadening the barrier's guarantee would risk publishing before intended observation.
- **Candidate correction locus:** BOTH / NEEDS DECISION: JAVA DOCS for precise supported
  composition guarantees; ANDROID if the intended guarantee needs explicit content/
  acknowledgement checks. JAVA IMPLEMENTATION only if a stronger API is deliberately chosen.
- **Recommendation:** review only; no implementation now. Ask whether this exact
  two-Finished construction is supported for released 0.2.0 and which assumptions
  clients may rely on; specify adversarial evidence before changing either side.

No CLIENT_AND_DOC_AMBIGUOUS finding beyond the explicitly scoped F08 question was
needed. Classification vocabulary also permits ALIGNED, INTENTIONAL_CLIENT_POLICY,
CLIENT_MISMATCH, JAVA_DOC_SUSPECT, JAVA_IMPLEMENTATION_SUSPECT and NOT_USED; unused
classifications are not populated with invented defects.

## 31. Current test evidence and gaps

All tests/harnesses below were **read**, not rerun or modified. Qualification reports
are historical evidence for the committed prerequisites, not fresh results here.

| Area | Current Android evidence | Limits/gaps relevant to this audit |
| --- | --- | --- |
| Controller/ownership | AndroidVaultControllerTest, LocalReplicaOwnerTest, CoordinatedPrivateStoreTest, ForegroundVaultCoordinatorTest | Missing F09 delayed old-subscriber cross-session mutation test. |
| Daily-driver Sync | AT daily* cases; [DailyDriverUxTest](../app/src/test/java/org/totipo/android/DailyDriverUxTest.java) | Real import-before-publish and bounded requests tested; F08 adversarial scheduling proof absent. |
| Reveal/local projection | TT; [TotpPresentationTest](../app/src/test/java/org/totipo/android/TotpPresentationTest.java); [RowRevealCopyTest](../app/src/test/java/org/totipo/android/RowRevealCopyTest.java) | Tests prove F02 blanket restriction; do not establish its necessity or UX benefit. |
| Mutation/Resolve | AT lifecycle* cases; [TokenLifecycleSourceGuardTest](../app/src/test/java/org/totipo/android/TokenLifecycleSourceGuardTest.java) | Whole-value/freshness/staleness covered; no decided historical-edit or continuation UX to test. |
| Biometric/inactivity | [SecurityControllerTest](../app/src/test/java/org/totipo/android/SecurityControllerTest.java), [InactivityLockTest](../app/src/test/java/org/totipo/android/InactivityLockTest.java), BiometricRecordTest and BiometricSourceBoundaryTest | Deterministic prompt/clock seams; do not prove universal OEM behavior or fixed close latency. |
| Provider/bootstrap | [VaultBootstrapTest](../app/src/test/java/org/totipo/android/sync/VaultBootstrapTest.java), ProviderObjectWriterTest, OutboundImmutablePlannerTest, ImmutableCandidateClassifierTest, DetachedVaultAuthenticationTest, VaultEnrollmentTest | Scoped SAF/Java ownership facts; no generic remote freshness/rollback proof. |
| Physical reveal/UI | [row harness](../tools/device/RowRevealRegression.java), [repaint report](ANDROID_REVEAL_REPAINT_FIX_REPORT.md), [row stability report](ANDROID_ROW_STABILITY_COUNTDOWN_RING_REPORT.md), [layout report](ANDROID_SYNC_STATUS_LAYOUT_REPORT.md) | Prior final evidence; no new device run for audit prose. |
| Physical lifecycle/security | [SecurityRegression](../tools/device/SecurityRegression.java), [TokenLifecycleRegression](../tools/device/TokenLifecycleRegression.java), [biometric report](ANDROID_BIOMETRIC_INACTIVITY_LOCK_REPORT.md) §§19–26 | Report records final lifecycle 51, security first 39/restart 29 checks PASS; not rerun. |
| M3D/SAF transport | [M3D report](M3D_SYNCTHING_END_TO_END_REPORT.md), [resume report](M3D_SYNCTHING_END_TO_END_RESUME_REPORT.md), [daily-driver report](ANDROID_DAILY_DRIVER_UX_REPORT.md), SAF bootstrap/inbound/outbound harnesses | Scoped historical transport qualification, not proof of F08 public composition contract. |

A focused unrelated-diagnostic eligibility test (F03) was not identified. No focused
capability-transfer tests are required for continuation workflows Android does not
use; such work belongs to a later decided implementation. No new behavioral tests
were added for any undecided policy, and no physical/8k-row harness was run.

## 32. Recommended decisions, without implementation

1. Review F09 as an Android ownership candidate; decide on a generation/owner guard
   and deterministic delayed-callback reproduction before a separate fix milestone.
2. Resolve F08 with Java contract owners: is two distinct completed observations
   after local bridge import a supported 0.2.0 composition, and what are its exact
   assumptions? Choose docs clarification vs Android evidence vs a future API deliberately.
3. Decide whether F02's unrelated-state concealment/rejection is desired product
   behavior. If yes, document the policy; if no, separately design token-local relevance.
4. Keep F01/F03/F04/F05/F06/F07 as explicit client choices until reviewed. Account for
   latency, rejected valid operations, no retry/partial UX and delayed close completion.
5. No Java implementation correction is justified by the evidence gathered here.
   No dependency upgrade is needed to adopt the exact guidance. Do not silently
   change tests or production behavior to make the audit mechanically “aligned”.

## 33. Dependency and effective-input invariance

Changed files are exactly AGENTS.md, TOTIPO_JAVA_DEPENDENCY.md and this report.
A path/content comparison of all **156 tracked effective filtered-source plus
evaluator/cache inputs** against HEAD passed; combined SHA-256 (sorted Git path,
NUL, raw per-file SHA-256) is
`55f6f349a3f803338e343e846a78ec884049eb5b183e64ae4ff33103f8949c20`.
Original Maven/JAR hash tables remain intact. No app/src/main, app/src/test,
tools, Gradle input, package-deps.json, lock/verification metadata, flake.lock,
package.nix, flake.nix or CI file changed. No Java checkout entered Android.

Actual [package.nix](../package.nix) source selection: trees `app/src`, `gradle`,
`tools`; exact build/lock/version/license/branding files listed there; excludes
.git/.gradle/build/.direnv components and symlinks. **Neither provenance Markdown,
AGENTS.md nor review/** is selected. [flake.nix](../flake.nix) consumes package.nix,
VERSION and package-deps through evaluation/package setup; it does not read these
changed docs. Build/CI/tool inspection found no executable read of these docs.
Raw path:. flake snapshots can include them without changing effective qualification
inputs. Therefore docs edits invalidate no Gradle/APK/device/Nix evidence.

Freeze statements for this docs-only boundary, supported by exact changed-path
allowlist/HEAD comparisons:

- **Production source stable:** app/src/main unchanged from starting HEAD.
- **JVM tests stable:** app/src/test unchanged; no new behavior expectation.
- **Build/package configuration stable:** all Gradle/lock/verification/package/Nix/CI inputs unchanged.
- **Qualification harness/tooling stable:** tools unchanged, including harness prose.

No included-input invalidation occurred. Later excluded report-only edits have no
Gradle/Nix effect under the inspected rules. No blanket future exclusion is inferred.

## 34. Qualification-ladder validation and execution counts

The docs-only profile applies. No broad Gradle, APK or device qualification is
needed. One narrow graph/boundary command was selected to verify actual runtime
resolution without regenerating locks/verification:

```sh
./gradlew --offline --dependency-verification=strict \
  :app:dependencies --configuration releaseRuntimeClasspath :app:verifyMavenBoundary
```

This failed before task execution because the active initially empty Gradle cache
could not resolve AGP 9.4.0 offline. The wrapper acquired its pinned 9.8.0 distribution;
`--offline` concerns dependency resolution, not wrapper bootstrap. Failure evidence
is preserved at `/tmp/android-java-audit-dependencies.log`. No build inputs changed.
The narrow follow-up allowed canonical dependency/cache acquisition, retaining
strict checks, locks, pinned JDK17/SDK and exactly the same task selection:

```sh
./gradlew --dependency-verification=strict \
  :app:dependencies --configuration releaseRuntimeClasspath :app:verifyMavenBoundary
```

**PASS**, 30 seconds, two tasks executed; log
`/tmp/android-java-audit-dependencies-online.log`. It resolved the exact graph in
section 2; Maven boundary reports external NIO/core 0.2.0 and runtime BC 1.86.
No compile/test/assemble task or APK verifier was invoked. Gradle emits its existing
experimental AAPT2/deprecation warnings; no upgrade or metadata regeneration follows.
JDK is the supplied Nix JDK 17.0.20.1; ANDROID_HOME is the supplied composed SDK.
No Nix command was used to enter or alter that existing environment.

Lightweight final validation:

- Exact changed-path allowlist and empty index: PASS.
- Production/test/tool/build/package/dependency/lock/verification/CI HEAD invariance: PASS.
- Maven Central source/POM/module hashes and 94 released source matches: PASS.
- Reviewed SHA, VERSION/r19 lineage, exact URL/blob hash and comment-only production comparison: PASS.
- Changed-document local links/heading anchors, pinned remote URLs/anchors, table field counts
  and balanced code fences: PASS.
- `git diff --check`: PASS.

| Invocation category | Count / disposition |
| --- | --- |
| Baseline full Gradle | 0 — excluded docs-only |
| Focused compile / test Gradle | 0 / 0 |
| Focused graph/boundary Gradle | 2 — first offline cache failure; one narrow online PASS |
| Final normal full / strict-clean Gradle | 0 / 0 — no changed executable inputs |
| External wrapper/APK verifiers | 0 — no artifact inputs changed |
| Embedded Gradle APK verifier | 0 — boundary task only; verifyReleaseApkBoundary not invoked |
| Maven-boundary task | 1 executed PASS; first invocation failed during configuration |
| Physical/device harnesses | 0, every phase |
| Human Nix / agent Nix | 0 / 0 |
| Remote CI dispatch / release actions | 0 / 0 |

No repeated full gate or invalidated full pass exists. The extra narrow invocation
was necessary solely to obtain an actual resolved graph from an unpopulated cache.

## 35. Nix disposition

**Human Nix not required.** All changed paths are excluded from effective package/
check inputs under the source filter and evaluator dependencies inspected in section
33. Prior final human evidence is not invalidated. No Nix was requested or run,
and no routine nix build or rebuild is proposed.

## 36. Remote CI status

No push, workflow dispatch, CI run or release was initiated. Read-only GitHub API
inspection found existing starting-HEAD Android [CI run 38078569182](https://github.com/totipo-org/totipo-android/actions/runs/38078569182)
completed **success** and selected-guidance Java [CI run 38078736333](https://github.com/totipo-org/totipo-java/actions/runs/38078736333)
completed **success**. These precede/qualify committed revisions, not the unstaged
Android docs. Remote CI for this milestone remains **unrun**.

## 37. Final Git state

Branch remains `main`; HEAD remains `ac5e7893ff0e1a876d496a6d34bd12c2a4592427`.
Index is empty. Exactly these paths remain unstaged/uncommitted:

```text
 M AGENTS.md
 M TOTIPO_JAVA_DEPENDENCY.md
?? review/ANDROID_JAVA_OPERATION_MODEL_AUDIT.md
```

No stage, commit, tag, release, push, CI dispatch or Nix action was performed.
Production/runtime/tests/tools and dependency/build/package inputs remain unchanged.
The report and pinned guidance are ready for review and a later correction-locus decision.

[release]: https://github.com/totipo-org/totipo-java/tree/d6310c177ae930df188fd4f5798622c935698b2e
[spec]: https://github.com/totipo-org/totipo-java/blob/d6310c177ae930df188fd4f5798622c935698b2e/SPEC_PIN.md
[guidance-requested]: https://github.com/totipo-dev/totipo-java/blob/b03f5b22f367723ce4a3bddf0a56b159a06f32cf/API_DESIGN.md
[guidance]: https://github.com/totipo-org/totipo-java/blob/b03f5b22f367723ce4a3bddf0a56b159a06f32cf/API_DESIGN.md
[model]: https://github.com/totipo-org/totipo-java/blob/b03f5b22f367723ce4a3bddf0a56b159a06f32cf/API_DESIGN.md#operation-classes-and-state-snapshot-semantics
[stream]: https://github.com/totipo-org/totipo-java/blob/b03f5b22f367723ce4a3bddf0a56b159a06f32cf/API_DESIGN.md#replay-latest-stream
[editing]: https://github.com/totipo-org/totipo-java/blob/b03f5b22f367723ce4a3bddf0a56b159a06f32cf/API_DESIGN.md#editing-and-deterministic-causal-bases
[merge]: https://github.com/totipo-org/totipo-java/blob/b03f5b22f367723ce4a3bddf0a56b159a06f32cf/API_DESIGN.md#merge-freshness-and-partial-resolution
[handles]: https://github.com/totipo-org/totipo-java/blob/b03f5b22f367723ce4a3bddf0a56b159a06f32cf/API_DESIGN.md#persistence-knowledge-and-handles
[blocking]: https://github.com/totipo-org/totipo-java/blob/b03f5b22f367723ce4a3bddf0a56b159a06f32cf/API_DESIGN.md#blocking-threading-and-close
[java-ladder]: https://github.com/totipo-org/totipo-java/blob/b03f5b22f367723ce4a3bddf0a56b159a06f32cf/review/JAVA_QUALIFICATION_LADDER_REPORT.md
[java-threading]: https://github.com/totipo-org/totipo-java/blob/ce00f0c6db8fb1c81322a7b9e02132d276f63a33/review/JAVA_SUBSCRIBER_THREADING_DOC_FIX_REPORT.md
[java-model]: https://github.com/totipo-org/totipo-java/blob/3b24b54becde0c93479c1fbd80ea0fbd2026e2a8/review/JAVA_OPERATION_MODEL_DOCUMENTATION_REPORT.md
[session]: https://github.com/totipo-org/totipo-java/blob/d6310c177ae930df188fd4f5798622c935698b2e/core/src/main/java/org/totipo/format/ApplicationSession.java
[publisher]: https://github.com/totipo-org/totipo-java/blob/d6310c177ae930df188fd4f5798622c935698b2e/core/src/main/java/org/totipo/format/ApplicationStates.java
[java-lifecycle]: https://github.com/totipo-org/totipo-java/blob/d6310c177ae930df188fd4f5798622c935698b2e/core/src/main/java/org/totipo/format/VaultLifecycle.java
[composition]: https://github.com/totipo-org/totipo-java/blob/d6310c177ae930df188fd4f5798622c935698b2e/storage-nio/src/main/java/org/totipo/storage/nio/NioStoreComposition.java
[java-tests]: https://github.com/totipo-org/totipo-java/blob/d6310c177ae930df188fd4f5798622c935698b2e/core/src/test/java/org/totipo/api/PublicApiTest.java
[java-validation-tests]: https://github.com/totipo-org/totipo-java/blob/d6310c177ae930df188fd4f5798622c935698b2e/core/src/test/java/org/totipo/api/ObjectCandidateValidationTest.java
