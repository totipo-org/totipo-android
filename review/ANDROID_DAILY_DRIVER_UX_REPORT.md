# Android daily-driver UX report

Status: implementation and non-Nix automated qualification PASSED. Human Nix PASSED
(user-reported); short phone smoke PASSED (user-reported). Detailed scenario-by-scenario
Syncthing smoke coverage was not supplied and is not claimed. Development/pre-release
status is unchanged. Nothing has been staged, committed, tagged, released, pushed or
submitted to remote CI. Agent has not run Nix.

## 1. Starting HEAD/state

Clean `main`, HEAD `f8d5d4503a68b003c710c92bf587f99ffe14a56b`.
The required branch/HEAD/status commands ran before editing; status was empty.
Committed history includes M3D qualification (`994433b`), Android Edit/Delete/Resolve
(`ca2918c`) and Nix/CI unification (`f8d5d45`). The committed unification report records
the prior human Nix pass. Actual source consumes released Java 0.2.0, format v1/r19,
and canonical r19 branding. Work is confined to `totipo-android`.

## 2. Baseline qualification

Before editing, all commands passed:

```sh
./gradlew check :app:assembleDebug :app:assembleRelease
./gradlew --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug :app:assembleRelease
python3 tools/verify-wrapper.py
python3 tools/verify-apk.py app/build/outputs/apk/debug/app-debug.apk --debug-probe
python3 tools/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk --unsigned --no-debug-probe
git diff --check
./gradlew --offline :app:dependencies --configuration releaseRuntimeClasspath
```

Normal build: 1m36s, 90 tasks (13 executed). Strict offline: 1m52s, all 92 tasks
executed. **259 JVM tests; zero failures, errors or skips**.

| Baseline APK | SHA-256 |
| --- | --- |
| Debug | `7f1eddb84280a3db0b75a90e282e30f7dcce68a88f34486502c4b03027359e6d` |
| Unsigned release | `8ae164f17daf91e1e81cb546be42e1af3d7d2b23d261c29a5aa05d7671289d04` |

Runtime graph: app → `org.totipo:totipo-storage-nio:0.2.0` →
`org.totipo:totipo-core:0.2.0` → `org.bouncycastle:bcprov-jdk18on:1.86`, with strict
lock constraints. Maven-boundary checks passed for debug/release compile/runtime.
Production manifest declares no permissions. Exported components are the launcher
`MainActivity` and `OtpAuthEnrollmentActivity` (VIEW `otpauth://totp`, SEND
`text/plain`). Debug-only qualification Activities remain debug-scoped.

Baseline logs are local, ignored evidence in `.gradle/daily-driver-baseline-offline.log`
and `.gradle/daily-driver-baseline-runtime.log`; the normal build was captured in the
session. APK hashes reflect this session's signing/build environment, not prior reports.

## 3. Current UI/controller inventory

| Surface/ownership | Actual owner at starting HEAD |
| --- | --- |
| Unlock/create/password confirmation | `MainActivity.authenticate`, `AndroidVaultController.authenticate`, `ForegroundVaultCoordinator.open/create`, `LocalReplicaOwner` |
| Lock/session retirement | `AndroidVaultController.lock/closeOwned`, session generation and cancellation; MainActivity Lock button |
| Token list | `MainActivity.build/render`, detached `TokenListAdapter`, coordinator `View/ObservedToken` |
| Search | Absent at starting HEAD; added to the existing MainActivity/adapter |
| Add | MainActivity Add form, `AddTokenRequest`, controller `addToken`, coordinator `addToken` |
| Edit/Delete/Resolve | MainActivity `openTokenChange`, adapter overflow/Resolve, `TokenChange`, controller `beginTokenChange/confirmTokenChange`, coordinator `changeToken` |
| Reveal/copy/conceal/expiry | Controller `showCode/copyShownCode/hideCode`, coordinator `generateTotp`, `TotpPresentation`, `RevealedTotp`, `PlatformCodeClipboard` |
| Import changes | Controller `importProviderChanges/startProvider/providerReturned`, `ProviderIoLane`, coordinator `sync`, `ImmutableCandidateImporter` |
| Publish local changes | Controller `publishLocalChanges/outboundDispatch/finishOutbound`, coordinator outbound snapshot/plan/confirmation, `OutboundImmutablePlanner`, `ProviderObjectWriter` |
| Join | Controller `prepareJoin/joinExistingVault/beginJoin`, `VaultBootstrapEvidence`, `DetachedVaultAuthentication`, coordinator `join` |
| Initialize | Controller `initializeSyncFolder`, coordinator `snapshotVault`, `ProviderVaultWriter`, `AndroidProviderVaultPort` |
| Folder binding/identity | `SyncFolderBinding`, `AndroidSyncFolderPort`, `ProviderVaultIdentity`, `ProviderTreeReader` |
| Sync/debug status | MainActivity permanent status + folder section, controller `Snapshot/SyncView/updateBinding/importMessage` |
| Diagnostics/protocol | Coordinator detached observation/integrity data, fixed controller details; no dedicated Diagnostics screen existed |
| Foreground/lifecycle | MainActivity onStart attach/onStop detach; `TotipoApplication` owns controller; no foreground transport Sync existed |
| Serialization/admission | Controller monitor, `operating`, `providerActive`, `pendingChange`, one bounded vault worker; separate existing bounded provider lane; session/binding generations, outbound cancellation |
| Pending publication persistence | Immutable objects persist in the canonical private store; no separate persisted product pending hint existed |

Baseline screens: create/unlock/status, open token list/reveal panel, Add form and
Edit/Delete/Resolve dialogs. Every screen carried a prominent folder/setup section,
explicit Import/Publish controls and lengthy bootstrap/protocol explanation. Open also
showed Refresh and Lock. Raw URI/VaultId were already excluded from baseline product
status; this milestone does not claim to have removed displays that did not exist.

## 4. Removed normal debug surface

Removed the permanent folder/status wall, protocol explanation, separate Import and
Publish controls, and local Refresh action from the daily token surface. Normal token
rendering does not receive provider URI, object counters, binding/controller states,
protocol/build strings or generation numbers. Lower-level diagnostic computation and
proven transport operations remain available internally; no advanced Import/Publish
controls were added as another product workflow.

## 5. Final main-screen structure

Framework UI and canonical styling remain: Totipo toolbar with **Sync** and named
**More options** overflow; simple issuer/account **Search tokens…**; token list;
revealed code panel when needed; **Add token**. Overflow contains **Manage sync folder**,
**Diagnostics** and **Lock**. Normal success has no persistent status text. The status
view is hidden when empty. Active rows show issuer/account without a redundant Active
label. Conflicts retain their explicit Resolve rows. No broad visual redesign occurred.

## 6. Unified Sync design

`AndroidVaultController.sync()` uses existing provider admission and `outboundDispatch`.
It checks binding availability, collects detached provider evidence through the existing
`ProviderIoLane`, invokes existing coordinator `sync`, waits for Java observation, then
enters `publishOnWorker`, extracted from the existing publication operation. Both the
internal Publish operation and unified Sync share that same publication implementation.
No second controller, executor, mutex, transport or merge algorithm was introduced.

## 7. Import-before-publish invariant

Provider identity/import precedes any outbound snapshot or publication. Java refresh is
asynchronous, so Android's coordinator adds `observeForSync`: two fresh completed state
emissions, each requested through the existing refresh boundary. A pass already in
flight at entry cannot alone authorize publication: the second completed pass must
start after the first finishes, after import. State-object identity filters initial Flow
replay; Android does not interpret revisions, token dominance, secrets or merge winners.
Each wait is bounded to 30 seconds and checks cancellation every 50ms; timeout/error
blocks publication. Java remains the semantic authority.

## 8. Blocking-before-publication invariant

`safeToPublishAfterImport` requires successful retained/refreshed completion, complete
provider coverage, no failed/unattempted import, no unsafe refresh, contradictions,
deferred validation, different-existing bytes or nonvalid classified candidates.
Identity exceptions stop the chain. Failed observation or local diagnostics also stop it.
Fresh outbound identity/preflight/readback/postflight checks remain. Unified Sync stops
the entire publication batch when the plan has blocked objects, retaining conservative
safety; internal lower-level Publish retains its prior behavior. Read-only folders may
import/observe remote evidence, then stop before publication. No automatic bootstrap,
Join, replacement, repair or best-effort publication follows a blocking condition.

## 9. Automatic unlock/foreground Sync

Successful `opened()` requests Sync when bound, including successful Join/open.
`TotipoApplication.ActivityLifecycleCallbacks` counts started Activities across the app,
including enrollment. Only background-to-active transitions request Sync; rotation is
coalesced using `isChangingConfigurations`. Locked foreground transitions cannot admit
Sync; successful unlock supplies the request once a usable session exists. Activity
recreation retains the existing Application/session ownership.

## 10. Mutation-triggered Sync

Successful Add and successful Edit/Delete/Resolve mark pending local publication and
request post-write Sync. Authoring stays in the existing local coordinator commands;
provider work starts after local command admission is released. Token results remain
ADDED/SAVED regardless of a later provider failure. Stale/invalid/failed authoring does
not trigger publication as a successful mutation. Separate detached outcome messages
are reset when starting another mutation or opening a session.

## 11. Local-first failure behavior

Valid local objects/history persist independently of provider access. Sync failure never
rolls back Add/Edit/Delete/Resolve. Known pending local changes show **Changes not synced**;
manual Sync, another mutation or a later foreground transition retries. `pending_publication`
is an Android preferences UI/retry hint in the existing binding preferences, retained
across folder changes/disconnect and loaded after restart. Its persistence errors cannot
invalidate a successful local write. It is not a publication authority or journal:
canonical immutable evidence is always rechecked, and every bound unlock retries even
if a hint was lost during process death or preferences failure.

## 12. Coalescing/admission behavior

One active unified operation holds `providerActive` across import, observation and
publication. Add/Edit/Delete/Resolve cannot race it. Lock and binding changes retain
their existing cancellation/retirement route. One `syncPending` bit and one dispatcher
drain flag coalesce requests while busy; completion/admission-release hooks drain it.
Observation display jobs are held during unified Sync to preserve bounded worker
handoff capacity for retirement. Requests arriving during active Sync are retained as
one following attempt. Failures create no requests, polling loop or timer retry.
Legacy lower-level tests isolate scheduled automatic requests in their test dispatcher;
there is no production switch to disable automatic Sync. New daily-driver tests exercise
automatic dispatch end to end.

## 13. Sync-status model

Normal success is quiet. Fixed visible categories include **Syncing…**, **Changes not
synced**, **Sync folder unavailable**, **Sync folder needs attention**, **Sync failed**,
and **This sync folder belongs to a different Totipo vault.** Different-vault status
wins over pending transport status; overflow supplies the management route. Conflicts
remain visible in token rows and show **Conflict needs attention** after successful Sync.
No status claims network convergence or receipt by other devices. Diagnostic success is
scoped to verification against the bound shared folder. No modal Sync success dialogs.

## 14. Diagnostics surface

Overflow → Diagnostics opens a simple framework dialog over a cached public projection:
vault ID (Locked after closure), bound URI, provider state, binding generation, last Sync
result, fixed operation detail, pending publication hint, conflict count, app version,
Java/core 0.2.0 and format v1/r19. Opening it performs no vault/provider work. It contains
no setup material, token secrets, root keys, passwords or raw exception/stack dumps.
The controller retains only fixed sanitized operation messages in this projection.
No elaborate settings framework or advanced transport controls were added.

## 15. Setup action relocation

Choose/change folder, Retry access, Disconnect, Join and Initialize live on Manage sync
folder; initial create/Join setup can also reach appropriate controls. Join remains
available only for an empty local canonical store; Initialize remains deliberate setup
of an absent provider VAULT/directory. Neither dominates configured daily use. Ordinary
Sync never changes local identity or selects a foreign VAULT automatically. Recovery
through folder management and Diagnostics remains reachable while locked.

## 16. Conflict behavior

Java authenticates/merges evidence. Real independent-session concurrent edits are tested:
the remote branch is imported, the two Alternatives are visible before the first outbound
create, publication succeeds, and the conflict remains explicit. Resolve still chooses
one complete Alternative; automatic post-resolution Sync publishes the authored result.
Deleted choices, stale bases, cancellation and local history retention remain covered.
A conflict alone is valid synchronized state and does not block publication.

## 17. Lifecycle/Lock behavior

Explicit lock/process death policy is unchanged; no biometrics or timeout policy was
added. Lock cancels the active Sync flag immediately and retires session generation
through the existing worker lifecycle. Every provider continuation checks cancellation,
session and binding identity. Tests lock while provider observation, local import and
Java observation waiting are active, with zero later outbound publication and no restored
unlocked view. Existing blocked-create/write tests exercise the shared publication
cancellation guarantees: an already-owned provider write may finish, but no following
object publication starts from the retired batch. No forced IPC/thread interruption.

## 18. Accessibility

Sync has accessible name **Sync**; overflow has **More options** and named navigation
entries. Search is named. Status uses a polite live region, updates only when text
changes, disappears when empty and does not rely on color. Existing token action
names/content descriptions remain. Codes are still excluded from live announcements,
saved widget state and autofill. The revealed code stays prominent (32sp); secondary
countdown now reads **Expires in … s**. Copy uses a concise framework Toast confirmation;
conceal/expiry/clipboard ownership logic is unchanged.

## 19. Automated Sync tests

New real Java/controller tests cover manual import/observation/publication ordering,
real concurrent branches visible at first provider create, preserved conflicts, blocking
imports, wrong VaultId, unavailable/incomplete/duplicate/missing/orphan/invalid provider
evidence, local import failure, observation failure, read-only import without publication,
unlock/foreground/locked-foreground, automatic Add/Edit/Delete/Resolve, local success
when offline, pending status/persistence/manual retry, no self-retry, bounded repeated
requests and mutation exclusion, and lock/binding retirement.

The first implementation runs exposed overly broad admission blocking in legacy
provider-only operations, outdated UI assertions, and two new fixture assertions. These
were corrected; no failing gate or safety assertion was waived. Legacy create-only,
provider identity and blocked-IPC tests remain in the complete suite.

## 20. Provider safety regressions

Provider identity/classification, immutable planner/writers, orphan bootstrap veto,
multiple VAULT rows (including byte-identical duplicates), inaccessible/changed binding,
readback/postflight, unsupported names, local integrity failures and zero mutation on
blocked operations retain their existing tests. New unified tests apply blocking cases
to the combined operation. No provider safety code or Java/protocol production code
outside this Android repository changed.

## 21. UI tests

`DailyDriverUxTest` adds framework source-boundary assertions for the configured daily
surface, removed normal Import/Publish/debug content, named overflow/Diagnostics/setup,
read-only/sanitized diagnostics, status accessibility and foreground/rotation wiring.
Pure search tests exercise issuer/account and conflict Alternatives with locale-independent
case matching. Existing row/conceal/secret-source guards are adapted to the new UI.
These are JVM/source checks, not a claimed emulator or physical UI pass.
The standalone `TokenLifecycleRegression` fixture is updated to await and verify automatic
publication and invoke unified Sync for concurrent remote evidence; historical reports
are untouched. Compile/physical execution status is recorded below.

## 22. Dependency/permission invariance

No libraries, Android permissions or components were added. No WorkManager, service,
coroutines migration, background transport, cloud SDK or analytics. Java pins, format,
immutable-object/conflict/cross-vault semantics and canonical branding are unchanged.
`flake.lock`, `package-deps.json`, Gradle locks, verification metadata, manifests,
Nix/package/CI configuration and branding assets remain unchanged. Final graph/APK
verification and byte comparisons are recorded with final validation below.

## 23. Final Gradle/APK validation

All required final commands passed:

```sh
./gradlew check :app:assembleDebug :app:assembleRelease
./gradlew --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug :app:assembleRelease
python3 tools/verify-wrapper.py
python3 tools/verify-apk.py app/build/outputs/apk/debug/app-debug.apk --debug-probe
python3 tools/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk --unsigned --no-debug-probe
./gradlew --offline :app:dependencies --configuration releaseRuntimeClasspath
./gradlew --offline :app:dependencies --configuration debugRuntimeClasspath
git diff --check
```

Normal: 4m14s, 90 tasks (25 executed, 65 up-to-date). Strict offline clean repeat:
1m54s, all 92 tasks executed. Final XML totals: **280 JVM tests; zero failures, errors
or skips** (baseline 259; 16 daily-driver controller integration tests and five UI/search/
lifecycle source/pure tests added). All existing suites remain included.

| Final APK | SHA-256 |
| --- | --- |
| Debug | `cae1f434d0a2ddad95154a6feaa5c2ef4e43108fd6dc168ecf94dfee57bd8728` |
| Unsigned release | `4c6e7d0e72e5cf6623c4f320e0aeee7962e659b6cbc4d13c632ab63eb142fdb9` |

Both APK verifiers passed all packaged branding, DEX/dependency, ingress, permission,
fixture exclusion and release unsigned/debug-exclusion checks. Debug and release
runtime graphs each contain only NIO 0.2.0 → core 0.2.0 → BC 1.86, plus strict lock
constraints. No permissions/components were added; source manifests and all pinned
inputs were byte-compared against starting HEAD. Packaged manifest dumps confirm zero
permissions, six debug Activities/two release Activities, and zero services, receivers
or providers in both variants. `package-deps.json` SHA-256 remains
`8d4ac6dd7fc08cc3793ae46f21cb885cf1300ccef992fe9f399fe6982f89c133`.

Updated standalone lifecycle fixture compiles with pinned JDK 17 against the final
release classes, Android 37 SDK and existing Maven jars into an isolated ignored
`.gradle/daily-driver-device` directory. It was not installed or executed. The initial
manual compilation referenced a release compile JAR not produced by the default gates;
using the actual final release class directory corrected the classpath. Historical
`.gradle/token-lifecycle` and M3D artifacts were not overwritten.

Final logs are ignored local evidence in `.gradle/daily-driver-final-normal.log`,
`.gradle/daily-driver-final-offline.log`, `.gradle/daily-driver-final-runtime.log` and
`.gradle/daily-driver-final-debug-runtime.log`. Existing AAPT2 experimental-option and
Gradle deprecation warnings remain; none were suppressed.

## 24. Human Nix result

PASSED — the user reported “nix flake check passed” after the requested
`nix flake check path:.` gate for this working tree. This is human-reported evidence;
no command output/log was supplied. Agent did not run Nix. No ordinary or forced Nix
build was requested.

## 25. Physical Syncthing daily-driver smoke

SHORT SMOKE PASSED — after installation, the user reported “short smoke test success”
alongside the human Nix pass. This records the user's result without assuming coverage
of provider outage/recovery, concurrent conflicts, different-vault blocking, or a
particular disposable Syncthing fixture. No per-scenario results or transport evidence
were supplied; the full focused checklist below is not marked individually passed.

At the user's explicit request, the current qualified release was installed on connected Pixel 6a `37311JEGR05916` with
`adb install --no-incremental -r`, retaining app data. An initial debug update was rejected
with INSTALL_FAILED_UPDATE_INCOMPATIBLE and made no change. The release was then signed
as a separate ignored copy using the existing local M1L development/test key; its
certificate was verified against the installed app before updating. Signed APK SHA-256:
`7fe9c8ce25657baeb6a1a13838e1b25c65680156cf94c70542747446a5e11fbe`.
ZIP alignment, signature, production APK boundary verification and payload equivalence
to the qualified unsigned release passed. Pulling the installed APK confirmed exact bytes.
The agent performed no uninstall, data clear, app launch, vault operation or physical
smoke. Installation verification is separate from the later user-reported smoke result.
Installation evidence is in ignored `.gradle/daily-driver-install/`.

The full focused Syncthing smoke uses a disposable vault/provider fixture, never the
user's real vault. Coverage of the following individual scenarios remains unverified
by the supplied short-smoke report:

1. Unlock; token-focused screen, no debug/protocol wall.
2. No normal Import/Publish controls.
3. One named Sync action.
4. Desktop change arrives after Android foreground without manual Import.
5. Android edit appears locally immediately and reaches desktop through automatic
   publication plus Syncthing transport and desktop normal refresh.
6. Unavailable provider: local edit survives; Changes not synced; vault remains usable.
7. Restore provider and Sync; import precedes publication, pending error clears.
8. Real concurrent edits remain a conflict; explicit Resolve still works.
9. Different-vault provider blocks publication; compare inventories for zero mutation.
10. Diagnostics is useful and contains no secrets/raw exception dumps.

Transport convergence is observed externally for the fixture, never asserted by Android
status. Passing this smoke does not make the application release-qualified.

## 26. Remote CI status

NOT RUN / NOT DISPATCHED. No push or remote workflow dispatch. Existing Nix/CI
architecture remains unchanged. Local qualification is not represented as remote CI.

## 27. Explicit deferred UX

Periodic/background sync, WorkManager, notifications, automatic conflict resolution,
cloud transport, QR scanner changes, setup-secret editing, detailed settings/preferences,
theme/tablet redesign, widgets, quick settings, backup/export and cross-vault migration
remain deferred. Production signing/release qualification is outside this milestone.

## 28. Final Git state

Audited on `main`, HEAD still `f8d5d4503a68b003c710c92bf587f99ffe14a56b`.
Index is unchanged (`git diff --cached --name-only` empty). All changes remain unstaged
and uncommitted: README; seven existing main Java files; five existing test files;
standalone lifecycle fixture; new `DailyDriverUxTest.java`; and this new report.
`git diff --check` passes. Pinned inputs, manifests, Nix/package/CI files, wrapper,
branding and resources were compared byte-for-byte to HEAD (28 files unchanged).
Historical M3D reports/checkpoints remain unchanged. No stage/commit/tag/release/push/
CI dispatch or agent-run Nix. Human Nix and the short phone smoke are recorded above
as user-reported passes. Full scenario-by-scenario Syncthing smoke coverage remains
unverified; no release qualification or broader transport coverage is claimed.
