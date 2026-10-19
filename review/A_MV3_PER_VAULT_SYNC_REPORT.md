# A-MV3 — per-vault Sync and local-only mode

## Starting state and scope

Started on clean `main`, HEAD `cfb6d5854858ee9d70ee1b961e1209aa5f4fe42e`.
`git branch --show-current` returned main; `git rev-parse HEAD` returned that hash;
`git status --short` was empty. A-MV2 is committed at this HEAD; no uncommitted
prior milestone needed preservation. No commit, tag, release or push is authorized.

Classification recorded before edits: PRODUCT_CODE, LIFECYCLE_SECURITY, UI_ONLY,
TEST_OR_QUALIFICATION_TOOLING. No build/dependency or protocol change is intended.
Baseline `gradle check :app:assembleDebug :app:assembleRelease` PASS, 1m36s,
89 tasks (12 executed, 77 up to date), without clean or cache forcing.
Logs are under ignored `.gradle/amv3-qualification/`.

Read AGENTS, README, TOTIPO_JAVA_DEPENDENCY, A-MV0/1/2 reports, relevant SAF,
publication, automatic Sync, Java operation/observation and lifecycle evidence,
CI, package.nix, flake.nix and device tooling. Read the exact Java API_DESIGN at
f0a028676c1801a10b2d8d2650bf9c24357d1fa1; downloaded blob SHA-256 matches
`338837e3563446f8ec11f7bb47c0e4f3ff5502b7608e397f9b8c4d75372715fd`.
No sibling Java checkout was available. Java 0.2.0, runtime source/protocol pins,
crypto, dependency versions and Android permissions remain unchanged.

## Existing ownership findings before edits

1. A-MV1 already provides independent canonical roots and the scoped preference
   namespace `sync_binding_mv1_<local-id>`. Application constructs the port with
   `replicaOwner.localId()`, once per selected controller. The registry stores only
   local identity, lifecycle metadata, labels and last selection, never provider URI
   or protocol VaultId. No migration is added.
2. URI/schema/read/write flags and pending-publication hint are persisted. Actual
   platform grants, accessibility, binding/request generations, cancellation state,
   provider lane, session generation, status and last result are reconstructed for
   each activation. Remembered access metadata never proves current permission.
3. Application has no global Sync port remaining after A-MV1. The global assumption
   is in binding cleanup: choose/disconnect releases previous grants without looking
   at other entries. Controller localMutationSaved also unconditionally marks a
   transport hint and warning even without a binding.
4. MainActivity requests ACTION_OPEN_DOCUMENT_TREE with READ/WRITE/PERSISTABLE/PREFIX.
   Its A-MV2 picker activation capability rejects stale results. Worker-side choose
   takes persistable permission, verifies actual permission, saves metadata and
   releases the previous grant. Failed choose previously released newly acquired
   flags directly; disconnect saved null then released the old grant.
5. Successful Add/Edit/Delete/Resolve call localMutationSaved after canonical local
   save. A-MV2 persists hints on the worker, outside the controller monitor. Hint
   state is not used by immutable publication planning.
6. An absent binding restores NOT_CONFIGURED and starts no probe/scan. Automatic
   requests already require a URI. The defect is the subsequent local-save warning
   and hint, not the underlying Java local-storage path.
7. A remembered inaccessible binding retains its URI and produces ACCESS_LOST or
   UNAVAILABLE, with retry/reselection guidance. Transport failure leaves local
   authoring intact after the existing active-operation admission boundary clears.
8. Provider results retain their controller, request, session/cancellation identity;
   worker/main handoffs are counted by A-MV2. Old picker results and Java callbacks
   retain activation ownership. Binding generations previously lacked a separate
   binding-owner token. Old operations can complete against old provider bytes,
   but retirement drains them and handoffs before Application releases selection.

ForegroundVaultCoordinator and ProviderVaultIdentity know one coordinated root and
one session, never the registry. Import validation, outbound planning, two completed
Java observation waits, safeToPublishAfterImport, readback/postflight and uncertainty
policies are preserved. Java operation classes remain A/B/C for local projection and
construction; local saves/publication retain their existing E/F rules; observation
is D and lifecycle G. SAF admission is an Android ownership boundary. No broader
Java BUSY requirement is asserted or introduced.

## Design and changed components

Application owns one SyncGrantOwnership; each controller still receives a final,
entry-bound AndroidSyncFolderPort. Registry bindingEntries validates current registry
bytes, health and inventory before exposing metadata for grant decisions; registry
persistent schema is unchanged. No inactive controller, session or root is opened.

SyncFolderBinding serializes choose/disconnect with this owner, and adds a distinct
request-owner token to generation/URI. Controller activation/local ID, captured session
generation, original lane/worker and completion guards retain the other ownership
axes. No callback uses a late current-controller lookup. Equal request generations
across activations cannot match.

Local-only saves no longer set pending-publication or schedule Sync. The UI and folder
management show Local only through the existing status surface. Disconnect clears the
hint only after successful persistence. Missing grants retain configured/access-needed
semantics and retry information. Retry-hint commit outcomes are checked; dirty hints
remain eligible for persistence at the existing worker boundary. They never authorize
publication, nor classify a local save as incomplete.

The scoped preference namespace additionally stores a binding recovery checkpoint
(previous URI, permissions and pending hint) and retained-grant cleanup set. Before
taking permission, ownership is durably recorded. Before retiring a historical binding,
its cleanup record must also persist. A checkpoint precedes binding replacement or
removal. SharedPreferences can change memory while reporting a disk failure; the
recovery marker preserves the previous binding and hint in that case. Explicit successful
Choose/Disconnect resolves it. A crash before update leaves the checkpoint; a complete
durable update is independently usable on restart. Failure never triggers speculative
permission revocation. A retained candidate grant can outlive an interrupted picker
handoff; configuration/cleanup metadata makes this explicit.

ProviderIoLane checks scoped permission ownership on its provider worker, immediately
before provider entry. Queued work with a retired binding is refused without transport
access. Entered work retains a counted grant use through actual return and stream cleanup,
including after logical cancellation or retirement. Rejected ownership gives the existing
operation a conservative unavailable/refused result. Metadata I/O does not run under the
controller monitor. No interruption or cancelled Future proves completion.

Grant cleanup uses the same owner lock as configuration mutation/admission. It refuses
revocation while any registered binding, uncertain recovery state or admitted use remains.
The platform additionally refuses independence when any different persisted grant exists;
textual URI inequality is never used to prove independence. Obvious duplicate bindings
compare authority plus decoded tree ID. Failed releases keep their durable record, and
successful release is checked against actual platform permission inventory. Registry
corruption, incomplete enumeration and malformed metadata retain grants. Cleanup is
attempted after disconnect/replacement or last provider-use return, never a broad startup
sweep. This intentionally may retain unrelated unused grants indefinitely; folder
management exposes the retained-permission notice. No external data is deleted or reset.

A-MV2 selection is held until session/domain/lease closure and complete provider/handoff
drain. Ordinary Lock remains reopenable. Startup still selects one entry or preserves
multiple entries with the established diagnostic. Controlled restart qualification uses
the same Application selection factory; there is no debug selection path or public
multi-entry navigation. Production biometrics remain disabled pending A-MV4.

## Qualification execution

Focused run 1 and run 2 accidentally overlapped on shared Gradle test output. Run 1
failed with EOFException and run 2 with missing in-progress-results-generic.bin.
Neither is accepted evidence. This was execution orchestration, not a product failure;
all subsequent Gradle runs are serialized. Run 3 PASS (1m31s), including Sync/controller,
registry, new grant-ownership and retirement tests. Review then moved grant admission
metadata checks to the provider worker to preserve A-MV2 main responsiveness. Run 4
PASS (23s) for provider/owner/retirement cases. Run 5 covers the final focused surface
including subsequent port cleanup-record and release-readback corrections.

Run 5 PASS (1m39s), 181 tests with zero failures/errors/skips. Normal full attempt 1
emitted PASS (3m48s), but was invalidated while running by the reviewed port exception/
malformed-notice correction and the interrupted-registry-inventory check. The concurrent
source edit also caused a lint quick-fix offset warning; lint/check itself passed. No
attempt is made to use its mixed-input APKs as evidence. Focused run 6 PASS (8s) for
registry/ownership/binding/provider/source guards. Renewed normal attempt 2 PASS (3m28s), 402 JVM tests with zero failures/errors/skips.
It is the stable host checkpoint. This extra full invocation is required by those included input changes;
strict-clean has not yet been run. Host device-tool suite PASS: 29 tests in 24.6s.
Four standalone device build-only checks PASS; they do not install or exercise a device.

Strict offline clean PASS (3m20s), all 91 tasks executed; 402 tests, zero failures,
errors or skips. Both independent external APK policies PASS against those outputs.
Offline runtime/Maven-boundary PASS (1s): NIO/core 0.2.0 plus runtime BC 1.86 only.
VERSION/VERSION_CODE remain 0.1.3 / 4. Packaged manifest permission, component/export,
backup, SDK, identity, variant, branding and debug/release exclusion checks PASS.
No embedded Gradle APK-verifier invocation occurs under current policy.

Strict-clean APK SHA-256:

- Debug: `39e238bb1641434868a5963b0e086d9e6c143e103669f5c83e2f70b7fe21c488`.
- Release unsigned: `e3db171b31917057df6a9ced8a3e4da00cccc30bef9f7f8dabe6936c9688a6e6`.

Physical first phase PASS, 96 checks; genuine cold-process restart phase PASS, 16
checks. Both use the exact strict-clean release DEX/resources. The installed disposable
APK was pulled and independently compared: both production DEX files and all 15 resource
entries match the qualified release bytes exactly (17 entries total). Receipt:
`.gradle/amv3-qualification/device-payload-receipt.json`. Source SHA-256 recorded by the
package builder equals the qualified release hash. No live Syncthing/peer claim is made.
Existing physical lifecycle PASS (53 checks) and retirement PASS (26 checks), each on a separate fresh disposable installation. The owner reported final `nix flake check` PASS on 2026-10-19.
The complete input freeze and successful human gate are recorded below. All required A-MV3 qualification gates are complete.

## Deterministic coverage

| Requirement | Evidence |
| --- | --- |
| Fresh unbound authoring, no provider work/hint, neutral status, Lock/reopen/restart | AndroidVaultControllerTest.localOnlyAuthoringNeverTouchesProviderOrCreatesTransportHintAndReopens; real Java/store, probe/scan fail hooks and exact zero counters |
| Local-only Resolve after disconnect | disconnectClearsTransportHintAndLocalOnlyResolveDoesNotRecreateIt; real conflict and successful canonical resolution |
| Later attachment uses evidence despite absent hint | bindingAfterLocalOnlySavesUsesFreshEvidenceDespiteAbsentRetryHint; complete ordinary Sync publishes the unbound edit |
| Revoked grant differs from unbound; offline local authoring succeeds | revokedBindingStaysConfiguredAndLocalChangesRetainRetryHint; binding remains, access-needed status and scoped pending hint |
| A/B entry/configuration/activation isolation | ControllerRetirementTest.entryBoundPortsSurviveSwitchRestartAndOldCompletionCannotRetargetB; actual registry/selection/coordinator, held provider entry, denial of B until complete retirement, independent password reopen |
| Equal generation/URI does not match another binding | SyncFolderBindingTest.equalGenerationsAndUrisFromDifferentActivationsNeverMatch |
| Shared reference, admitted use, actual lane drain | SyncGrantOwnershipTest.sharedGrantRetainedUntilLastRegisteredConsumerDisconnects, admissionRetainsGrantUntilActualProviderReturnEvenAfterDisconnect, actualProviderLaneHoldsSharedPermissionThroughRetirementDrain |
| Aliases/uncertainty/failed ownership writes | aliasesAndUnknownOverlapRetainDespiteDifferentUriStrings, failedOrUncertainPersistenceRetainsOldAndCandidateAuthority, explicitRecoveryMayReplaceOwnUncertainBindingButNeverAnotherEntries |
| Failed grant release remains recorded/retryable | failedReleaseLeavesDurableVisibleCleanupRecordAndRetryWorks |
| Corrupt/interrupted registry cannot trigger cleanup | registryCorruptionNeverRevokesEvenWhenCachedEntriesLookEmpty, interruptedRegistryWriteNeverTriggersCleanup |
| Configuration/admission linearization | configurationAndAdmissionShareOneCoherentBoundary, using a held owner monitor and latches |
| Existing Sync/protocol/lifecycle safety | Unchanged controller, identity/import/planner/writer/bootstrap, Java observation, coordinated-store and security regression suites; no source guard weakened |

PerVaultSyncRegression additionally uses the real Application factory, scoped Android
preferences/ports and actual platform picker/permissions. It is built only into the
disposable security-qualification package, never the production APK. Its passed checks
include local Create/Add/Edit/Delete/Resolve without ever configuring a provider, real
same-session Java conflict authoring, zero lane submissions/grants, UI status, fresh
initialization/Sync, independent A/B VaultIds/folders, duplicate and decoded-alias refusal,
failed/throwing preference commits, malformed cleanup notice, failed permission acquisition,
historical shared-reference cleanup, held A provider authority and complete retirement,
revoked/restored grants with successful local editing, foreign VAULT refusal with exact
unchanged provider inventory, and a genuine cold-process configuration/retry restoration.
The interrupted-checkpoint fixture is injected only into disposable scoped preferences.

All negative concurrency evidence uses held latches/monitors and actual completion/drain
conditions. Bounded polling waits for positive asynchronous observations; elapsed sleep
is not proof of cancellation or absence of work. Controlled held-call evidence does not
claim that an arbitrary third-party Binder call can be stopped. Actual external-storage
SAF checks do not prove Syncthing peer receipt.

## Physical evidence, cleanup and limits

Pixel 6a, serial `37311JEGR05916`, Android 17/API 37, fingerprint
`google/bluejay/bluejay:17/CP3A.261005.002.A1/2026101601:user/release-keys`.
The owner unlocked the device for qualification. The disposable package was confirmed
absent before prepare. Only `org.totipo.securityqualification` was installed, granted
new disposable-tree access, exercised and removed. The existing `org.totipo.android`
installation was never installed, updated, opened for credentials, reset or removed;
its original package path remained unchanged after qualification. No existing user
vault, folder, credential, key or permission was modified.

| Phase | Result / host log |
| --- | --- |
| mv3-first | PASS 96 checks; device-mv3-first-1.log |
| mv3-restart (force-stop/new process, same disposable data) | PASS 16 checks; device-mv3-restart-1.log |
| Existing token lifecycle, separate fresh installation | PASS 53 checks; device-lifecycle-1.log |
| Existing controller retirement, separate fresh installation | PASS 26 checks; device-retirement-1.log |

All logs above are under `.gradle/amv3-qualification/`. No physical phase failed or
needed retry; injected failures are successful fault-case assertions, not qualification
failures. Three prepare/install and three remove operations succeeded. Final
`pm path org.totipo.securityqualification` returned no package. The temporary keep-awake
setting changed from 0 to 3 and was restored and reread as exactly 0; receipt is
`device-setting-restored.json`. Original/retained package receipts and payload comparison
are in the same ignored evidence directory.

The new phase uses actual Android external-storage DocumentsProvider grants issued by
the system picker. Before approving, automation requires the unique fresh fixture leaf
in the picker header; the returned URI must match exactly before taking a persisted
grant. The disposable external root is
`Documents/Totipo-AMV3-b9af23628a174288be8676147dd708f5`, with independent uniquely named
A/B children. It was created for this run and remains for review; no provider deletion
or automatic cleanup was performed. Fixture receipt: `mv3-fixture.json` in the evidence
directory. Removal of the disposable application retires only its own Android grants.

New real-SAF evidence includes first initialization/Sync, revocation/regrant, per-entry
configuration, shared reference retention and identity mismatch refusal. The held
operation uses the actual provider lane, scoped Android port and counted grant use,
with a controlled latch after an actual SAF probe. It proves ownership/drain ordering,
not cancellation of a hung third-party Binder implementation. The existing lifecycle
harness uses isolated file transport; the retirement harness uses controlled blocked
transport. No live Syncthing/peer campaign was performed or claimed. No unrelated row,
biometric, camera or full M3D campaign was repeated.

Reproduction on a fresh disposable package, using the pinned environment:

```sh
python3 -B tools/device/qualify-security.py --serial SERIAL --prepare
python3 -B tools/device/qualify-security.py --serial SERIAL --phase mv3-first
python3 -B tools/device/qualify-security.py --serial SERIAL --phase mv3-restart
python3 -B tools/device/qualify-security.py --serial SERIAL --remove
```

The first phase allocates new external fixture names and records them; restart uses
that exact receipt. Prepare refuses an existing package. Lifecycle and retirement
require separate fresh prepare/remove pairs because they intentionally construct
independent registry fixtures. Never remove an unreviewed existing installation.

## Execution counts and invalidation disposition

| Check | Invocations / outcome |
| --- | --- |
| Baseline ordinary full Gradle | 1 PASS |
| Focused Gradle test/compile | 6: first two overlapped and failed reporting; four subsequent PASS; compilation included in each |
| Standalone focused Gradle compile | 0 |
| Final normal full Gradle | 2 PASS emitted; first invalidated by included source/test changes; second is final |
| Strict offline clean Gradle | 1 PASS, all 91 tasks executed |
| External final APK verifier | 2 commands, one debug/release set, both PASS |
| Embedded Gradle APK verifier | 0 under current policy |
| Final offline runtime/Maven boundary | 1 PASS |
| Device-tool unit suite | 2 invocations, 29 tests PASS each; second follows final qualifier changes |
| Device build-only | 4 PASS |
| Device prepare/package/install | 3 PASS, including final harness compilation |
| Physical phases | 4 PASS: 96 + 16 + 53 + 26 = 191 checks |
| Disposable removals | 3 PASS; final package absent |
| Human Nix | 1 final routine `nix flake check`, owner-reported PASS |
| Agent Nix | 0 |

The exact baseline/final-normal commands were
`gradle check :app:assembleDebug :app:assembleRelease`. Strict used
`gradle --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug :app:assembleRelease`.
Final verifiers used debug `--debug-probe` and release `--unsigned --no-debug-probe`.
Runtime/boundary used
`gradle --offline :app:dependencies --configuration releaseRuntimeClasspath :app:verifyMavenBoundary`.

Extra full normal invocation was necessary because the first checkpoint was invalidated
by commit-exception/malformed-notice hardening and interrupted-registry evidence checks.
Its PASS and lint quick-fix offset warning are retained, not substituted for final
qualification. No strict/artifact/device gate was invalidated or repeated. Final device
sources were completed before strict and all physical successes use unchanged qualified
production artifacts. Harness-only evolution during normal qualification did not
invalidate Gradle/APKs: no Gradle consumer reads these standalone tools. All `tools/`
inputs are nevertheless included in the final Nix freeze.

## Complete input freeze and human gate

Source filtering and evaluator consumers were inspected, rather than inferred from
extensions. package.nix includes app/src, gradle and tools plus its exact Gradle,
lock/version/license/branding files. It excludes .git/.gradle/build/.direnv components
and symlinks. Evaluator/cache inputs and flake release-tooling/public-signing inputs
are also inventoried. README, root AGENTS and review prose are excluded and not otherwise
consumed by the checks. Report-only updates after this freeze do not invalidate Nix.

Final freeze, 2026-10-19:

- **Production source stable.** Renewed normal, strict-clean, both artifact policies and
  physical runs qualify the same unchanged production sources. Installed production
  DEX/resources equal the qualified release bytes.
- **JVM tests stable.** All 402 pass at the strict-clean boundary, zero failures/errors/
  skips; no JVM edits afterward.
- **Build/package configuration stable.** No build, evaluator, lock, dependency, version
  or package configuration changed. Offline runtime/Maven and packaged boundaries PASS.
- **Qualification harness/tooling stable.** Final standalone harnesses compile and pass
  all four relevant physical phases; final affected host tool suite passes. No included
  tooling edit follows this freeze.

Inventory: `.gradle/amv3-qualification/final-frozen-inputs.json`, 181 included files,
SHA-256 `801bff6d6d7d883c7437f73fcb6a954cb6747896deb805f48427b89369e35dbd`.
Compared with the early, invalidated normal-1 inventory, differences are Registry,
AndroidSyncFolderPort, SyncGrantOwnershipTest and the two device tooling files;
all those changes precede their final affected qualifications. No frozen input changes
are pending. Four new milestone files are Git-known through intent-to-add only.
Ignored cache/build/signing/secret state was never added to source membership.

Before requesting the human gate, branch/HEAD/status, `git diff --name-only` and
`git diff --cached --name-only` were recorded. Cached names are empty,
`git diff --cached --quiet` PASS, and `git diff --check` PASS. The included inputs
were frozen before the final human-operated `nix flake check`. The owner reported
**PASS** on 2026-10-19. This is human-reported evidence; the agent did not execute Nix
or independently capture its terminal output. Post-result verification found all 181
frozen inputs unchanged, with the inventory SHA-256 still exactly as recorded above.
Only this excluded report was updated to record the result; no gate was invalidated.
All required milestone gates are complete.

## Remaining work and material limits

No unresolved cross-vault store/provider ownership or grant-revocation blocker was
found in the qualified paths. Conservative retention is intentional: an unrelated
remembered binding or another persisted grant can prevent automatic permission
release indefinitely. Metadata/disk corruption can require explicit recovery; there is
no broad permission sweep or general repair framework. A permanently hung provider
still holds its activation until process termination, under A-MV2's existing policy.

A-MV4 owns per-entry biometric records/keys and reenrollment. A-MV5 owns public zero/
one/multiple-entry selection, navigation and management/recovery UI. Both remain
deferred; production is password-only and refuses ambiguous multi-entry startup.
No simultaneous session, inactive-vault Sync, cross-vault token movement, dependency/
protocol/crypto change, legacy migration or provider-data deletion was introduced.

## Changed files and final Git state

Fifteen intended paths, all unstaged or intent-to-add only:

```text
 M README.md
 M app/src/main/java/org/totipo/android/AndroidVaultController.java
 M app/src/main/java/org/totipo/android/TotipoApplication.java
 M app/src/main/java/org/totipo/android/VaultRegistry.java
 M app/src/main/java/org/totipo/android/sync/AndroidSyncFolderPort.java
 M app/src/main/java/org/totipo/android/sync/ProviderIoLane.java
 M app/src/main/java/org/totipo/android/sync/SyncFolderBinding.java
 A app/src/main/java/org/totipo/android/sync/SyncGrantOwnership.java
 M app/src/test/java/org/totipo/android/AndroidVaultControllerTest.java
 M app/src/test/java/org/totipo/android/ControllerRetirementTest.java
 A app/src/test/java/org/totipo/android/SyncGrantOwnershipTest.java
 M app/src/test/java/org/totipo/android/sync/SyncFolderBindingTest.java
 A review/A_MV3_PER_VAULT_SYNC_REPORT.md
 A tools/device/PerVaultSyncRegression.java
 M tools/device/qualify-security.py
```

Final branch remains `main`, HEAD remains
`cfb6d5854858ee9d70ee1b961e1209aa5f4fe42e`. No staged contents exist. No commit,
tag, release or push occurred. Changes remain available for review. Implementation,
host/artifact, applicable physical and final human Nix qualification are complete.
