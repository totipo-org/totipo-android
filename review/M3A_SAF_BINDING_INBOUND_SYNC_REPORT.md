# M3A — SAF folder binding and inbound immutable objects

Sections 1–32 retain the original M3A qualification history, with superseded architecture wording updated. Section 33 records the current hardening pass and its fresh validation; earlier APK hashes/Nix results are historical.

## 1. Starting state

Agent ran `git branch --show-current`, `git rev-parse HEAD`, `git status --short` before editing. Clean `main`, committed M2D, HEAD `75a17ae2f62b847f2dba90bc77c3000888effd26` (`Add zero-dependency otpauth enrollment`). No applicable AGENTS.md was found. No staging, commit, tag, release, push, remote CI trigger or agent Nix command.

## 2. Milestone scope

One persistent SAF tree binding, explicit manual inbound immutable-object import, and fixed user-visible status. No outbound publication, provider mutation, VAULT adoption/reconciliation, automatic import, background synchronization or polling. This feature does not promise full or two-way synchronization.

## 3. NixOS / no-Google-Drive strategy

Actual `command -v` results identify Nix-managed adb, JDK 17 java/javac and Python 3.14.7. Existing project SDK, Gradle wrapper, Maven verification and patched AAPT2 are used; no FHS Android paths, host package installation or Nix configuration changes. JVM tests run without adb. Required physical qualification uses the Pixel 6a and Android's local DocumentsProvider tree at `Documents/Totipo-M3A-Test`. No Drive mount, browser login, credentials, account change, Play Services, cloud CLI or provider SDK.

## 4. Existing M1 architecture reused

Inspected current ProviderTreeReader, ProviderSnapshot, ProviderTraversal, BoundedProviderRead, ImmutableCandidateClassifier, ImmutableCandidateImporter, CoordinatedPrivateStore, LocalReplicaOwner, ForegroundVaultCoordinator, AndroidVaultController, TotipoApplication, MainActivity, existing tests and M1E/M1F/M1I reports. Current production ingress already uses the persistent coordinated store and live session; the historical close/reopen importer remains test-only. No ingress/storage/protocol implementation was recreated or altered.

## 5. SAF binding model

`SyncFolderBinding` owns the worker-confined policy with `NOT_CONFIGURED`, `CHECKING`, `READY`, `ACCESS_LOST`, `UNAVAILABLE` and read/write capability projection. `AndroidSyncFolderPort` supplies platform SharedPreferences, persisted grant inspection, lightweight root query and existing ProviderTreeReader scan. MainActivity launches `ACTION_OPEN_DOCUMENT_TREE`; selection is the security boundary. There is one active remembered tree, no silent root switching and no URI-to-filesystem conversion.

## 6. Persisted URI permission model

Picker requests READ, WRITE, PERSISTABLE and PREFIX flags. Successful result must contain READ. Only returned READ/WRITE bits are passed to `takePersistableUriPermission`; actual persisted READ is verified before committing the binding. WRITE, if present, is recorded but never exercised. Read-only trees are READY and supported. Cancellation/null/malformed/no-read/failing persistence cannot activate a new root. Failed replacement retains the previous binding and releases newly acquired capabilities best effort without revoking pre-existing grants.

## 7. Stored binding metadata

App-private platform preferences `sync_binding_v1`: `schema=1`, `tree_uri`, `read`, `write`. No password, key, plaintext, document IDs, paths, enumeration cache, display-name authority or provider-package metadata. Binding is separate from no-backup cryptographic storage; existing app `allowBackup=false` remains. Stored value's diagnostic string conceals configuration. UI projection contains status and capabilities only.

## 8. Startup restoration

Application injects the binding into the existing controller before discovery. Its vault worker loads preferences, checks tree URI shape and inspects Android's actual persisted permissions. A dedicated provider-I/O lane asynchronously queries the root's directory MIME plus availability extras while local discovery proceeds. CHECKING does not affirm accessibility or disable local vault use. No candidate scan or import occurs at startup. Missing/malformed/stale grants never become READY. Root query reads at most its first row and closes the cursor; provider IPC can still block, as with existing M1 transport.

## 9. Permission loss

Definitively absent persisted READ produces ACCESS_LOST, fixed `Access needed. Choose folder again.` and no provider access. Remembered URI may remain a picker `EXTRA_INITIAL_URI` hint, with no authority. Manual Retry access can recognize restored grants. No settings navigation or second human picker is required merely to exercise this behavior; deterministic policy tests cover loss.

## 10. Provider unavailable

Query exceptions, missing roots, error/loading root extras and unavailable scans preserve remembered binding. UNAVAILABLE differs from access loss; `Retry access` runs a fresh lightweight check. An unavailable provider does not lock the vault or close its session. A scan's original coverage remains in coordinator evidence, including useful individually safe candidates captured before failure.

## 11. Product UI/status

Small Sync folder section: Connected, Not configured, Access needed, Unavailable, Importing, Changes imported; refresh requested, No new objects, Provider view incomplete, Integrity problem, Local publication failed. Generic folder status is used instead of an optional display name. No raw URI, document ID, provider filename, ciphertext or exception is rendered. Explanatory line: `Totipo currently imports token changes from this folder.` Choose/Change folder, Import changes, Retry access and Disconnect controls. Locked UI says `Unlock Totipo to import changes.` Import admission requires READY and OPEN with no incompatible operation. Integrity attention remains visible for the controller's process lifetime, including subsequent access retries. This is not an integrity-repair workflow.

## 12. Inbound reconciliation path

`importProviderChanges()` is a credential-free controller command. The vault worker admits the request and captures binding/session generations; the dedicated provider lane invokes `ProviderTreeReader.snapshot(1024)`. Detached results return to the vault worker for admission recheck and the existing `vault.sync(scan)`. Provider waits do not hold global BUSY admission. Activity receives no scan or session. Import begins by concealing revealed TOTP through the existing publication policy; observation signals also conceal. Worker completion delivers an admission-state update so controls re-enable reliably. Folder management renders the current live view after its worker operation.

## 13. Candidate classification reuse

Existing ImmutableCandidateClassifier calls `session.validateObject` against the current root. Names, IDs, metadata sizes, ordering and timestamps are transport evidence only. Malformed names/nonobjects bypass validation; short/oversized/unavailable reads and Java-invalid objects cannot be materialized. UI maps captured outcomes, never creates candidate plans. Complete scans with invalid/unavailable candidates report ignored candidates; incomplete views remain explicitly qualified.

## 14. CoordinatedPrivateStore publication

Unchanged ForegroundVaultCoordinator selects authenticated exact immutable representations and publishes through exclusive bridge control into its existing persistent NioTotipoStore domain. No direct canonical writes, second canonical store, new owner, close/reopen, password or KDF on import. Written, AlreadyPresentExact, ExistingDifferent, Uncertain and Failed retain their prior semantics. Local failure/uncertainty stops the batch and retains ownership for explicit Lock.

## 15. Refresh ordering

Existing coordinator releases its bridge scope before `operations.refresh(session, store)` / `session.requestRefresh()`. Retained M1I tests assert `exclusiveHeldByCurrentThread()==false` at refresh and exercise concurrent Java save/password operations. No session API is called inside bridge scope. Product says `refresh requested`; it never associates Java's coalesced request with a completion generation or waits for one.

## 16. Same-session evidence

Retained M1I tracked operations assert the same session, one open, zero closes at refresh, and later observed graph content. New product import test proves worker dispatch, same session, one open/zero closes, and later listener-visible three-token state. Standalone device harness compares coordinator, VaultSession, private store and LocalReplicaOwner identities before/after import and waits for actual three-token observation. Test setup authentication/fixture KDF is isolated and separate from the import operation.

## 17. Incomplete-provider behavior

Actual current policy permits individually safe validated objects even with INCOMPLETE_LOADING, INCOMPLETE or UNAVAILABLE coverage. Per-ID validation-unavailable siblings defer that ID; unrelated safe IDs remain eligible. This M1 policy is unchanged. Product prefixes incomplete/unavailable coverage to actual admission outcome; coverage is never upgraded to complete. The hardened product test imports three objects from a production row-bound INCOMPLETE snapshot, retains incomplete status, and verifies the existing INCOMPLETE_LOADING projection on retry.

## 18. Duplicate/integrity behavior

Existing traversal retains duplicate provider documents/directories. Equal validated representations collapse for publication while siblings remain observable. Unequal validated representations for one identity exclude that identity and accumulate sticky coordinator integrity problems. ExistingDifferent local publication preserves local bytes and allows unrelated safe imports. Product maps both contradiction types to fixed integrity attention. Existing tests cover exact duplicates, symbolic authenticated contradiction selection and real invalid/transport siblings; symbolic contradiction evidence does not claim the fixture authored a cryptographic collision.

## 19. VAULT explicitly not imported

Traversal descends only exact `objects-v1` directory candidates. Root `vault` is retained as transport metadata but is never read/validated/adopted by M3A. New coordinator regression supplies a visible provider vault row, imports three immutable objects, compares local wrapper bytes, and verifies one session open/zero closes. Device tree includes a deliberately invalid 87-byte `vault`; device harness compares isolated local wrapper before/after. No prepareVault, password change, provider-root adoption or KDF reconciliation occurs.

## 20. No-provider-write evidence

Production SAF adapter and reader use root/children queries and `openInputStream` only, plus grant take/release. Source guards reject create/delete/rename documents, output streams, file descriptors, path extraction and provider-specific assumptions. Tests/fixture authorship write only disposable private host/device roots; adb prepares the public test tree. Provider content hashes are compared before/after the device import. Retained writable grant is capability only. No observer, receiver, service, timer, WorkManager, scheduler or polling was added for sync.

## 21. Automated fixture strategy

Reuse existing M1E traversal/bounded-stream fixtures, M1F released-Java candidate fixtures, M1I coordinated publication tests and ProductControllerFixtures. New binding tests use a pure in-memory platform port covering cancellation/null/no-read/take failure/unverified grant/save failure/replacement/disconnect/release failure/restart/access-loss/unavailability/read-only/read-write cases. Controller tests cover locked/busy admission, complete empty tree, incomplete positive import, exact retry, unavailable recovery, sticky integrity and local failure. Existing tests cover all byte-read bounds, validation, duplicates, publication and gate ordering. Standalone `tools/device/build-saf-inbound-tests.py` uses framework SDK tools and already-cached pinned Java artifacts to build SafInboundRegression outside production source sets. InboundFixture authors three public tokens in a disposable host root. No standalone DocumentsProvider APK was needed.

## 22. Device-local SAF qualification

Pixel 6a connected with adb, SDK 37. Agent prepared only its new Documents/Totipo-M3A-Test subtree. Signed non-debug release and standalone instrumentation copies use the existing installed signer; `adb install --no-incremental -r` preserves app data. Original debug/unsigned release outputs are not modified. Physical result: **PASS**. Human confirmed Connected after one production picker interaction. Installed signer SHA-256 `1f14855aface333061eb0bb545251756a214aa02c31607735f003689eecf9005`. The signed release copy uses the exact unsigned release artifact reproduced by the final offline build. Standalone instrumentation passed **14 checks**: stored binding, persisted READ, restored root accessibility, application startup READY, locked import disabled, isolated empty replica, three-token live observation, same session/owner/store, VAULT exclusion, honest import status, revealed code, concealment on import, exact retry and isolated session closure. Provider's four fixture files had identical hashes before/after. Harness package was uninstalled. Production app and its binding/disposable test tree remain; no real-vault change was made.

## 23. Process-restart persistence

After the single successful picker grant, agent force-stops and relaunches Totipo without clearing data. Harness verifies remembered binding, actual persisted READ, root accessibility and application's restored READY state without another picker. Physical result: **PASS**. Agent force-stop/relaunch after the grant, followed by instrumentation startup, recognized persisted READ and READY. Final production UI independently showed Connected, concealed the URI, and disabled Import changes while locked. No second picker, data clear or device reboot. Pure-policy recreation/restoration tests: PASS.

## 24. Human interaction required

1. Connect/unlock Pixel as needed (phone initially absent; it subsequently auto-locked while builds ran).
2. One production folder-picker interaction choosing Documents/Totipo-M3A-Test and accepting Android's tree access prompt.
3. Run the two Nix commands in section 29 — completed successfully, as reported by the human.

Agent handles fixture generation/copying, Git, Gradle, Python, signing, APK checks, adb, process restart and isolated import harness. No human file construction, object-ID handling, second writer, manual sync-scenario navigation, Drive or reboot. Temporary USB stay-awake setting was restored to its original `0`; no rotation or other account/device settings changed.

## 25. Optional Google Drive

Google Drive: **not tested**. Neither availability nor host integration is a completion prerequisite.

## 26. Syncthing relevance

No integration or dependency. An external tool may synchronize an ordinary Android directory; Totipo reads that directory's conforming SAF provider view without knowing the external tool. This qualification uses locally prepared provider documents, not Syncthing.

## 27. Supply-chain verification

No production dependencies, locks, verification metadata, Java/BC pins, wrapper, SDK/Gradle setup, flake or package-deps changed. Normal Gradle check verifies external Maven NIO/core 0.1.5 and BC 1.86. Strict offline clean rebuild exercises existing cache only. Wrapper verifier: PASS. Standalone framework-only harness and fixture author are never packaged into Totipo.

## 28. APK/permission verification

Verifier retains all M2D otpauth boundaries, core/NIO/BC requirements and release diagnostic exclusions. Added required production binding classes and rejection of broad storage permissions, Drive package/SDK classes, Syncthing integration, WorkManager, DocumentFile and standalone SAF qualification classes. Existing INTERNET prohibition retained. Manifest is unchanged; no permission was added. Final debug/release verification: **PASS** (debug with `--debug-probe`, unsigned release with `--unsigned --no-debug-probe`, signed device release with `--no-debug-probe`). Final SHA-256:

| Artifact | SHA-256 |
| --- | --- |
| Debug APK | `47b9ef6437fd586d1b702c679d72ad1dd8246bbdb3b2bbcf0228a85908c5c500` |
| Unsigned release APK | `0eae7f91c759830b48ca2d9042a30d8a3c4bd9e739184ecd5b15a9716be7e8a0` |

## 29. Validation

Agent normal `./gradlew check :app:assembleDebug :app:assembleRelease`: **PASS**, 191 JVM tests, zero failures/errors. Includes Android lint, Maven boundary, unsigned release verifier. Final forced repeat:

```sh
./gradlew --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug :app:assembleRelease
python3 tools/verify-wrapper.py
python3 tools/verify-apk.py app/build/outputs/apk/debug/app-debug.apk --debug-probe
python3 tools/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk --unsigned --no-debug-probe
```

Offline clean strict rebuild: **PASS**, 92 tasks executed, 191 tests, zero failures/errors/skips. Wrapper, both final APKs and signed device APK: **PASS**. Standalone framework harness build/execution: **PASS**, 14 device checks. Local ignored evidence: `.gradle/m3a-saf/normal-build.log`, `offline-build.log`, `device-tests-sanitized.txt`, `ui-check-sanitized.txt`, provider before/after hashes and installed certificate. Human device picker: **PASS**, “it is showing it now.” Agent production UI assertion initially compared the button label case-sensitively; Android rendered its all-caps button label. Case-folded label check then passed; no production change was needed. Human Nix: **PASS**, reported by the human: “both nix commands succeeded.” Both commands below succeeded on the M3A worktree. The agent did not run Nix:

```sh
nix flake check path:.
nix build path:.
```

Remote CI: **NOT RUN**, deliberately not triggered. Those human Nix results apply to the pre-hardening worktree. Fresh human Nix checks for the hardened worktree also passed, as recorded in section 33.

## 30. Limitations

Inbound immutable objects only; no provider VAULT or outbound semantics, automatic/background work or coverage/freshness guarantee. SAF IPC may block despite bounded query/read resources. Generic status replaces optional provider label. Integrity attention persists in the current controller process; it is not an integrity repair mechanism or persisted integrity journal. Failed grant release is best effort and may leave an unused Android grant. Android/platform storage failure cannot guarantee durable configuration writes. Physical qualification is one local provider/device, not universal provider qualification; unavailable/loading/duplicates/short reads are deterministic JVM coverage. No Drive smoke. Pre-hardening human Nix checks passed as reported; hardening Nix checks also passed as reported by the human. Remote CI was deliberately not run.

## 31. Recommended M3B boundary

Explicit foreground outbound immutable-object publication with independently verified WRITE capability, exact representations, provider uncertainty/duplicate semantics and coordinated lock order. Design VAULT transport/adoption/reconciliation separately before any full-sync claim. Background policy follows qualified foreground two-way behavior.

## 32. Final Git state

Final agent-run command output follows. All M3A changes remain unstaged/uncommitted on starting main/HEAD. Tracked diff statistics omit new untracked binding classes, tests, report and device harness sources. No staging, commit, tag, release, push or CI operation.

```text
$ git status --short
 M app/src/main/java/org/totipo/android/AndroidVaultController.java
 M app/src/main/java/org/totipo/android/MainActivity.java
 M app/src/main/java/org/totipo/android/TotipoApplication.java
 M app/src/test/java/org/totipo/android/AndroidVaultControllerTest.java
 M app/src/test/java/org/totipo/android/reconcile/ForegroundVaultCoordinatorTest.java
 M tools/verify-apk.py
?? app/src/main/java/org/totipo/android/sync/
?? app/src/test/java/org/totipo/android/sync/
?? review/M3A_SAF_BINDING_INBOUND_SYNC_REPORT.md
?? tools/device/InboundFixture.java
?? tools/device/SafInboundRegression.java
?? tools/device/build-saf-inbound-tests.py
```

```text
$ git diff --stat
 .../org/totipo/android/AndroidVaultController.java | 113 ++++++++++++++++++++-
 .../main/java/org/totipo/android/MainActivity.java |  44 ++++++++
 .../java/org/totipo/android/TotipoApplication.java |   3 +-
 .../totipo/android/AndroidVaultControllerTest.java |  84 +++++++++++++++
 .../reconcile/ForegroundVaultCoordinatorTest.java  |  19 ++++
 tools/verify-apk.py                                |   7 +-
 6 files changed, 264 insertions(+), 6 deletions(-)
```

```text
$ git diff --check
(empty output)
```

```text
$ git diff --cached --stat
(empty output)
```


## 33. M3A hardening — provider isolation and scan exhaustion

### Problem and execution architecture

Previously startup root queries, Retry access and full eager provider scans ran inside the vault/controller serialization worker. Import held global BUSY admission, making Lock unreachable while provider IPC waited. `ProviderIoLane` now owns transport calls only: root query, child queries and bounded encrypted-candidate stream reads. It receives a platform transport port, immutable generation/tree request and import/check selector. It receives no coordinator, VaultSession, private store, owner, password, key, AddTokenRequest, plaintext secret or token authorship capability. All cursors and streams close on the provider lane before a data-only `Result`/`Scan` is dispatched. MainActivity remains presentation-only.

Vault admission, Java validation/classification, coordinated private publication and refresh remain on the existing vault worker. No change to the classifier, publication bridge or bridge-release-before-refresh order. Local session/store/owner remain live; no import KDF or close/reopen. Persisted binding schema and SAF tree identity are unchanged. No provider writes, dependency, permission, background work, Drive or Syncthing integration were added.

### Provider bounds and stale-result policy

One named `Totipo-provider-io` thread, a JDK fixed single-worker executor with one bounded handoff slot, and controller admission allowing one logical provider request until handoff. There is no pending logical retry/import backlog. Repeated Import/Retry taps reject while the lane is occupied. Import status remains “Importing…” while blocked; global vault state remains OPEN and Lock stays enabled. Folder commands and ordinary local operations use their existing admission rules. The thread is reused, with provider failures converted to a fixed inaccessible result rather than escaping and killing the worker.

The in-memory binding generation changes on restoration, accepted selections (including same-tree replacement), disconnect and invalidating permission/projection changes. Requests capture generation and tree URI. The vault worker rejects mismatches before classification/publication/refresh. A changed folder remains authoritative; after old work returns, its new binding can receive its own root accessibility check. Persisted-permission metadata is rechecked at handoff; a grant revoked while IPC was blocked invalidates/discards the result and projects ACCESS_LOST. Session generation changes on Lock and controller shutdown, so even unlock before the old scan returns cannot resurrect it. Closed/non-OPEN sessions and incompatible admission discard results rather than retain them.

### Lock, disconnect and change-folder while blocked

Deterministic latch-based controller regressions hold scan IPC while 100 duplicate imports are rejected. Lock closes the session before releasing the provider latch; a subsequent unlock before releasing it still discards that scan. Disconnect clears metadata and completes while blocked; replacement installs B while A remains blocked. Released stale A results cause zero object publication and zero refresh, preserve the local file inventory and leave disconnected/B identity authoritative. A fourth blocked scenario revokes persisted READ and proves ACCESS_LOST with no publication/refresh. Thread identity is asserted for provider transport and local publication/refresh. No interrupt cancellation is needed.

### Startup and Retry isolation

Restore performs only configuration and persisted-permission metadata inspection. An honest sync-only CHECKING projection disables Import until the asynchronous root check succeeds. A blocked startup check does not block local discovery, vault creation or Add. Repeated Retry requests reject while blocked. Completion projects READY; query failure projects UNAVAILABLE while missing read permission remains ACCESS_LOST. No automatic candidate scan/import occurs at startup or unlock.

### Hung-provider limitation and lifecycle

A provider that never returns can occupy one provider thread until process exit. No retry spawns another worker and no recovery pool exists. Local vault operations remain available; provider work remains busy and new binding accessibility may remain CHECKING until that lane returns. Process restart may be required. Controller `shutdown()` requests executor shutdown without awaiting provider termination; Lock never shuts down or joins the provider lane. Cancellation correctness depends on generations/admission, not Binder interruption.

### Actual snapshot(1024) bounds and coverage

The argument is **maximum bytes per candidate**, with one overflow probe byte; it is not an object/document count. Existing independent caps remain: 256 root rows, 8 matching `objects-v1` directory listings, 1024 child rows per directory, 512 candidate read attempts, maximum 64 KiB per read, 1 MiB reserved scan bytes. Noise rows consume row budgets; only non-directory lowercase 64-hex names consume candidate attempts. A representation exceeding the per-candidate byte maximum remains OVERSIZED and is rejected under the existing M1 policy; that byte cap is distinct from omitted document coverage. At 1024 bytes, 512 reads reserve 524800 bytes, below the scan byte cap. VAULT is excluded from candidate reads.

Production `ProviderTreeReader.snapshot(1024)` tests use the production row collector and traversal with deterministic transport input. For 1023 and exactly 1024 child rows containing one candidate plus noise, proven EOF permits COMPLETE. The collector probes one additional row: 1025 rows retain 1024 and report INCOMPLETE/RESOURCE_LIMIT. Loading plus exhaustion never becomes COMPLETE (combined state is INCOMPLETE under existing enum precedence). Exactly 512 candidates with EOF may be COMPLETE; a 513th eligible candidate makes the scan INCOMPLETE and preserves the first 512 reads. Existing root/directory/byte-budget regressions remain.

A production bound-limited fixture snapshot retains safe candidate bytes collected before child-row exhaustion. Controller import still independently validates/imports those three fixture tokens using M1 policy and reports “Provider view incomplete. Changes imported; refresh requested.” Deferred/ambiguous policy remains unchanged. Exact complete retry still reports “No new objects.” No coverage redesign or bound increase.

### Device requalification, human interaction and validation

Hardening qualification results are recorded below after execution. The existing Documents/Totipo-M3A-Test grant is reused; no picker or manual provider-file manipulation is required. Human interaction target: none beyond connecting/unlocking the phone if necessary, plus the two final Nix commands. Historical sections above retain pre-hardening qualification evidence; their APK hashes and test totals do not describe the hardened build.

### Final Git state

The starting branch is main, HEAD `75a17ae2f62b847f2dba90bc77c3000888effd26` (committed M2D). All hardened M3A work remains unstaged/uncommitted. No M3B work, staging, commit, tag, release, push or CI trigger. Final verification output is appended after validation.


### Hardening qualification results (current worktree)

- Normal `./gradlew check :app:assembleDebug :app:assembleRelease`: **PASS**, 90 tasks (26 executed, 64 up-to-date).
- Required offline/no-daemon/no-configuration-cache/no-build-cache/rerun/strict-verification clean build: **PASS**, all 92 tasks executed.
- JVM suite: **194 tests**, zero failures/errors/skips; existing M3A/M1 tests retained, with focused hardening scenarios and production scan-bound coverage.
- `python3 tools/verify-wrapper.py`: **PASS**, Gradle 9.8.0 wrapper/distribution pins.
- Final debug APK (`--debug-probe`), unsigned release (`--unsigned --no-debug-probe`) and installed signed release copy (`--no-debug-probe`): **PASS**. Supply-chain, permission, SDK/integration and test-harness exclusions remain enforced. Dependency declarations, locks, verification metadata, wrapper, manifest and Nix inputs remain unchanged.
- Updated existing standalone SAF harness: **PASS**, 14 checks on Pixel 6a using the existing persisted Documents/Totipo-M3A-Test grant. Restoration first projects CHECKING; the production application asynchronously reaches READY. Inbound import yields three observed tokens in the same coordinator/session/private-store/owner, preserves local VAULT, conceals a previously revealed code, and exact retry reports no new objects. Isolated session closes for cleanup. All four provider hashes are identical before/after. Release signature/alignment/install preserves app data. The standalone harness package was uninstalled afterward.
- Human device interaction during this pass: **none**. Phone already connected; no folder picker, unlock request, Drive, Syncthing or manual provider-file manipulation was needed.
- Hung-provider thread isolation and stale-result behavior are deterministic JVM regressions; no artificial hung provider was introduced on the physical device.
- No remote CI, staging, commit, tag, release or push. Agent did not run Nix. **Fresh human Nix checks: PASS**, reported by the human: “nix checks passed.” This confirms `nix flake check path:.` and `nix build path:.` for the hardened worktree. No package-deps regeneration was requested. M3A hardening qualification is complete; no M3B work was started.

Final APK SHA-256:

| Artifact | SHA-256 |
| --- | --- |
| Debug | `c0dfaf801ad8b9347a1a0cd0da7febd051cc24dabf2ecbbe1a28e567ed9f4af7` |
| Unsigned release | `bf963b2a390007bbbbe3cb1e324f782f3cbbdfa7d2e1adecebd89c08a6e1de5e` |
| Signed device release | `f16f47681aa86f7c030ac9efd66240194f69ec26e154078421b0bbb06912d893` |

Ignored local evidence: `.gradle/m3a-saf/hardening-normal-build.log`, `hardening-offline-build.log`, `hardening-device-tests.txt`, `hardening-provider-before.sha256`, `hardening-provider-after.sha256`. Exploratory overlapping Gradle runs produced conflicting output errors; they were stopped. All qualification results above come from subsequent sequential normal/offline runs on the final production/test sources.

Final agent Git verification follows. Statistics cover tracked changes; untracked new M3A classes/tests/report/harness sources are listed separately by status.

```text
$ git branch --show-current
main
```

```text
$ git rev-parse HEAD
75a17ae2f62b847f2dba90bc77c3000888effd26
```

```text
$ git status --short
 M app/src/main/java/org/totipo/android/AndroidVaultController.java
 M app/src/main/java/org/totipo/android/MainActivity.java
 M app/src/main/java/org/totipo/android/TotipoApplication.java
 M app/src/main/java/org/totipo/android/provider/ProviderTreeReader.java
 M app/src/test/java/org/totipo/android/AndroidVaultControllerTest.java
 M app/src/test/java/org/totipo/android/provider/ProviderSnapshotTest.java
 M app/src/test/java/org/totipo/android/reconcile/ForegroundVaultCoordinatorTest.java
 M app/src/test/java/org/totipo/android/reconcile/ProductControllerFixtures.java
 M tools/verify-apk.py
?? app/src/main/java/org/totipo/android/sync/
?? app/src/test/java/org/totipo/android/sync/
?? review/M3A_SAF_BINDING_INBOUND_SYNC_REPORT.md
?? tools/device/InboundFixture.java
?? tools/device/SafInboundRegression.java
?? tools/device/build-saf-inbound-tests.py
```

```text
$ git diff --stat
 .../org/totipo/android/AndroidVaultController.java | 192 +++++++++++++++++++-
 .../main/java/org/totipo/android/MainActivity.java |  44 +++++
 .../java/org/totipo/android/TotipoApplication.java |   3 +-
 .../android/provider/ProviderTreeReader.java       |  77 +++++---
 .../totipo/android/AndroidVaultControllerTest.java | 198 +++++++++++++++++++++
 .../android/provider/ProviderSnapshotTest.java     |  63 ++++++-
 .../reconcile/ForegroundVaultCoordinatorTest.java  |  19 ++
 .../reconcile/ProductControllerFixtures.java       |   5 +-
 tools/verify-apk.py                                |   7 +-
 9 files changed, 571 insertions(+), 37 deletions(-)
```

```text
$ git diff --check
(empty output)
```

```text
$ git diff --cached --stat
(empty output)
```
