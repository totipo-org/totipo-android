# SAF outbound automatic-Sync reconciliation

Status: **complete for this narrow reconciliation**. Host construction and the full
normal outbound physical procedure PASS. Wave 2A remains incomplete; Nix deferred.

Classification: **TEST_OR_QUALIFICATION_TOOLING — physical regression semantics correction**.
No PRODUCT_CODE or BUILD_DEPENDENCY work. Nix is deliberately deferred to resumed Wave 2A.

## Starting state and scope

Started on main at `d21c15dd4fbfca799ad2b354690e86b118a4aaa0`, VERSION 0.1.2,
VERSION_CODE 3. The explicitly authorized dirty-tree prerequisite overrides AGENTS'
ordinary clean-main prerequisite. Status/stat/name-status matched exactly the nine
Python callers, two untracked shared-module/test files, and untracked Wave 2A report
recorded in [Wave 2A](ANDROID_SUPPORT_TOOLING_WAVE2A_REPORT.md). No unrelated edits.
Initial file hashes retained in `.gradle/saf-autosync-reconciliation/starting-files.json`.

## Scenario reconstruction before Java changes

Source/history: outbound introduction `a688438`, r19 update `a5e0d83`, current
SafOutboundRegression, InboundFixture and LocalStoreQualification. The isolated
root is freshly created and receives only the asset VAULT. The local-store
regression uses sibling roots. The old `isolated_replica_initially_empty` assertion
(`tokens() == 0`) follows unlock, immediately before explicit import. Its purpose
was A, not an intentional delay of automatic Sync:

| Phase | Old scenario intent | Current lifecycle boundary |
| --- | --- | --- |
| A | Empty isolated local replica, canonical fixture VAULT only | Verify filesystem before constructing controller/open/observation |
| B | Unlock same local vault/session | opened() subscribes, renders and requests automatic Sync |
| C | Explicitly import matching provider objects into same session | Automatic Sync imports/observes; await exact fixture state/bytes and identity |
| D | Add through isolated writer, explicitly publish immutable local objects | Add requests automatic Sync; await verified publication, retain explicit retry |
| E | Existing provider inventory/hashes unchanged; only exact new canonical objects | Preserve all inventory/hash/create-only/retry assertions |
| F | Different/invalid/missing VAULT blocks import and publish without mutation | Preserve separate blocked branches and exact expected messages |
| G | Close/delete isolated roots, restore provider fixture, remove harness/relaunch | Preserve Java and qualifier cleanup; verify external restoration evidence |

Current authority: README automatic Sync contract; AndroidVaultController.opened(),
foregroundChanged(), localMutationSaved(), startSync()/finishOutbound(); controller
unit tests dailyUnlockAndForegroundAutoSyncAreCoalescedAndLockedForegroundDoesNothing,
dailyAddCommitsOfflineDoesNotLoopAndManualSyncRetries, daily import-before-publish
and blocked identity cases. Reviewed coordinator/provider identity/import/publication
paths and the exact pinned Java API_DESIGN at
`f0a028676c1801a10b2d8d2650bf9c24357d1fa1` (including observation ordering).
requestRefresh is asynchronous and not an acknowledgement. Android's Sync explicitly
waits for local observation before publishing. Post-open emptiness and explicit
first-import status are race-dependent historical assumptions; automatic publication
after Add likewise precedes a later explicit publication call. Product behavior is
the authority. No polling/background Sync or test-only product switch is introduced.

## Java correction and assertion mapping

Only `tools/device/SafOutboundRegression.java` changes executable semantics here.
No shared-helper/Python redesign and no other Java edits.

| Old assertion/action | New assertion/action and rationale |
| --- | --- |
| post-unlock `isolated_replica_initially_empty` | `isolated_replica_empty_before_observation`: exact sole VAULT entry and bytes before controller construction; empty object namespace cannot race observation |
| initial explicit import + token count | unlock-triggered Sync + bounded exact-count observation; exact local object names/hashes equal provider fixture, exactly three known issuer/accounts, single alternatives, intended VaultId |
| `import_status_honest` expecting first-import message | `automatic_open_sync_verified` checks completed unified Sync result; unified Sync's final status describes publication, not its earlier import phase |
| waitIdle only operating/viewQueued/providerActive | also await syncPending/syncDrainQueued, under the controller monitor, so a queued automatic attempt cannot be mistaken for completion |
| Add, then first explicit publication | Add-triggered automatic publication; unchanged `production_postflight_verified_publication` and all subsequent provider byte/inventory assertions; explicit publish retry remains |

No empty-state duration or callback ordering is assumed. Existing bounded waits
remain asynchronous qualification bounds, not fixed sleep correctness conditions.
The new exact ciphertext map prevents token-count-only acceptance, and exact
issuer/account/single-alternative checks reject unexpected semantic state. Existing
owner/store/session retention, immutable canonical VAULT, reveal/conceal import retry,
writer session, expected new objects, exact provider representations, unchanged old
entries, canonical names only, exact publication retry, reader import and closure
assertions remain. Initial automatic Sync additionally proves zero provider mutation.
The protocol fixture remains unchanged: InboundFixture authors the three public
`M3A public fixture` / `test 0..2` tokens through released Java.

Blocked branches retain both explicit import and explicit publish, their exact
messages for different/invalid/missing VAULT, one authored local token, exact local
object inventory/bytes, zero provider mutation, unchanged canonical VAULT and the
same live session. Their wait now also drains automatic attempts after open/Add;
none of their safety assertions is removed or relaxed.

## Host validation

Pinned environment: Nix-managed JDK 17 and Android SDK API 37.0 / Build Tools
36.0.0 already supplied by the shell. Used existing checksum-qualified
`.gradle/wave1-gradle-home` cache; no dependency fetch or new dependency.

`GRADLE_USER_HOME="$PWD/.gradle/wave1-gradle-home" python3 -B tools/device/build-saf-outbound-tests.py`
passes. Two host builds: initial compilation, then final construction capture after
using public coordinator.sessionVaultId() for identity (avoids reflecting through a
Java implementation class). Final builder runs the unchanged Wave 2A module via an
ignored runpy capture that records actual commands without replacing tool execution.

Evidence in `.gradle/saf-autosync-reconciliation/`:
`builder-commands.json`, `construction-validation.txt`, `wave2a-preservation.txt`.
Explicit javac sources remain SafOutboundRegression and LocalStoreQualification;
D8 inputs match the prior Wave 2A comparison exactly:

```text
org/totipo/safqualification/LocalStoreQualification.class
org/totipo/safqualification/SafOutboundRegression.class
```

InboundFixture remains host-only, not in DEX. Manifest source/binary, ZIP entry
names/order and all non-DEX payloads match `.gradle/wave2a/after/r19-saf` exactly.
Only classes.dex differs, as expected from changed scenario code. Package remains
org.totipo.safqualification, instrumentation targets org.totipo.android, min SDK
26, target 37, unchanged asset VAULT. All eight existing mechanical host tests pass
(two invocations); all device Python files parse; git diff --check passes.
No unrelated row/security/M3D suite was rerun.

## Physical precondition

Pixel 6a, serial `37311JEGR05916`, Android 17/API 37; fingerprint
`google/bluejay/bluejay:17/CP3A.261005.002.A1/2026100601:user/release-keys`.
The human explicitly confirmed: "Unlocked; same disposable qualification environment".
Window inspection then showed MainActivity focused and no dreaming lockscreen.
The designated provider fixture was pulled before mutation to
`.gradle/saf-autosync-reconciliation/provider-preflight`.

Pulled installed app before run: SHA-256
`0f262717e1a8ffa3e7d6925e15f50940366035f6af73826a44ddd605c285be30`,
matching Wave 2A. Certificate DN `CN=Disposable Wave 2A Qualification`, SHA-256
`d862bbf279d306e7062440d299440d54ecdf594e1cf8ac3046cdf5d3a0ed7f49`.
Unchanged unsigned release SHA-256
`b70501790d32ff383665c8f7c1a599d750036b91a3b1fb0b804f706b8341e012`.
The existing qualifier signs ignored copies using the existing local qualification
key, checks installed-signer equality, verifies alignment and signed release
boundaries, and installs with app data retained. No protected signer is used.

## Product non-change proof and two-layer diff

Layer A is the untouched starting Wave 2A work:

```text
tools/device/build-otpauth-ingress-tests.py
tools/device/build-saf-bootstrap-tests.py
tools/device/build-saf-inbound-tests.py
tools/device/build-saf-outbound-tests.py
tools/device/check-row-reveal.py
tools/device/qualify-saf-bootstrap.py
tools/device/qualify-saf-outbound.py
tools/device/qualify-security.py
tools/device/qualify-syncthing-e2e.py
tools/device/android_harness.py                    (untracked)
tools/device/test_android_harness.py               (untracked)
review/ANDROID_SUPPORT_TOOLING_WAVE2A_REPORT.md     (untracked)
```

All twelve remain byte-identical to their recorded starting hashes. The Wave 2A
report was not overwritten. Its nine tracked-file diff remains 151 insertions /
241 deletions; its shared module/tests/report are separate untracked additions.

Layer B, this milestone only:

```text
tools/device/SafOutboundRegression.java            (+34 / -5)
review/ANDROID_SAF_OUTBOUND_AUTOSYNC_RECONCILIATION_REPORT.md (new)
```

Git diff is empty for app/ (including product and JVM tests), tools/release/,
release/, .github/, VERSION, VERSION_CODE, gradle/, Gradle build/settings/properties/
locks, package.nix, flake.nix, flake.lock and package-deps.json. Other device Java
sources remain byte-identical to HEAD. No product behavior, synchronization policy,
provider transport, scheduling, release artifact source or protocol fixture changed.
The updated harness DEX is the intended semantic delta; the previous Wave 2A
claim of unchanged Java/DEX applies only to its earlier mechanical milestone.

## Nix deferral and resume instructions

`package.nix` actually selects tools/**, so this Java correction is an effective
qualification input. It is not a Gradle-consumed product/test input. No Gradle
rebuild is required solely for this device harness; no prior product Gradle pass
was invalidated. The report is excluded by the inspected source filter and is not
otherwise consumed by package/check or release-app wrappers. Report-only final
updates do not invalidate those gates.

- Production source stable: unchanged source and unchanged unsigned release hash.
- JVM tests stable: unchanged app/src/test.
- Build/package configuration stable: unchanged Gradle/dependency/evaluator/cache inputs.
- Outbound harness stable after host and physical qualification; overall Wave 2A
  tooling qualification remains incomplete until its remaining physical checks.

**Nix deliberately NOT REQUESTED / NOT RUN.** No final overall tooling freeze is
claimed. Resumed Wave 2A must preserve this corrected scenario, finish bootstrap,
ingress and applicable M3D physical checks, reconcile any stop condition under
appropriate scope, freeze all included tooling/evaluator inputs, and only then
request the single final human `nix flake check path:.`. Agent does not run Nix.
Do not restart unrelated lifecycle/row/biometric matrices or proceed to Wave 2B.
This separate report supplements the stopped Wave 2A record without rewriting it.

## Physical results and restoration

Executed the supported no-argument qualifier exactly once:

```sh
GRADLE_USER_HOME="$PWD/.gradle/wave1-gradle-home" python3 -B tools/device/qualify-saf-outbound.py
```

An ignored host wrapper captured output and restored the original stay-awake value
in finally. It did not change the qualifier interface or bypass any phase.
Physical log `.gradle/saf-autosync-reconciliation/physical.log`; exit **0**.
The same directory retains copied phase outputs and restoration evidence.

| Phase | Result |
| --- | --- |
| Persisted READ + WRITE inspection | PASS |
| Matching VAULT | `R19 MATCH PASS checks=47` |
| Different VAULT | `R19 BLOCKED PASS checks=25` |
| Invalid VAULT | `R19 BLOCKED PASS checks=25` |
| Missing VAULT | `R19 BLOCKED PASS checks=25` |

Matching demonstrates the revised sequence: empty private root before observation,
locked import disabled, automatic open Sync, exactly three matching fixture tokens
and ciphertext objects, unchanged canonical VAULT/intended VaultId and session,
no provider mutation on initial Sync, code reveal/conceal retry, isolated writer Add,
automatic postflight-confirmed publication, exactly one new object, five preexisting
provider inventory entries unchanged, exact ciphertext representation, no unrelated
artifacts, no-change publication retry, and same live reader importing the fourth token.

All three blocked phases separately demonstrate truthful rejection of both operations,
no imported extra tokens or objects, exact local bytes, unchanged provider inventory,
canonical VAULT and live session. No weaker matching-only success is claimed.

The shared qualifier's backup/restoration hashes passed. Independently pulled the
whole provider folder before and after and compared every file path/SHA-256:
**all six files match exactly**, including entries outside the replaced fixture
children. Evidence: `provider-restoration-validation.json`, `provider-preflight/`,
`provider-final/`, and `fixture-restoration.txt`. The granted root was never moved.

Instrumentation package absence verified by `pm path` returning no package.
Stay-awake was temporarily 0 → 3 and restored to **0**. Qualifier force-stop,
restore, uninstall and launch commands completed. Final window inspection found
the device back at keyguard; no cosmetic app-state changes were attempted.
Java's existing isolated-root lock/shutdown/reverse-order deletion finally paths
remain unchanged; matching explicitly passed isolated session closure. Private
root deletion is performed by that inherited cleanup, not independently inspected
through shell access to the non-debuggable product's private storage.

The locally signed qualification product remains installed, with existing disposable
binding/data retained. Pulled final APK equals the qualifier's signed copy byte-for-byte,
and is also byte-identical to the starting installed Wave 2A qualification APK:
SHA-256 `0f262717e1a8ffa3e7d6925e15f50940366035f6af73826a44ddd605c285be30`.
Certificate unchanged. Entire ZIP payload matches the unchanged unsigned release.
Evidence: `final-device-validation.json`, `installed-before.apk`, `installed-final.apk`.
No public production-signed release was reinstalled; no protected signing material
touched. All ignored logs, backups and fixture evidence remain retained.

## Failures, counts and invalidations

No failed host check or physical phase in this milestone; no physical retry or
second corrective edit after a physical failure. Before physical execution, the
identity check was adjusted to use the coordinator's public accessor and host
construction recaptured. Wave 2A's earlier failed attempts remain its own evidence,
not failures or retries attributed to this reconciliation.

| Execution category | Count/result for this milestone |
| --- | --- |
| Baseline/final normal/strict-clean full Gradle | 0 / 0 / 0; no Gradle-consumed inputs changed |
| Focused Gradle test/compile/boundary | 0 / 0 / 0 |
| Outbound host builds | 2 PASS; second captures final source/construction |
| Outbound builder invoked by normal physical qualifier | 1 PASS |
| Mechanical host suite | 2 invocations, 8 tests PASS each |
| Physical qualifier | 1 complete procedure, exit 0 |
| Physical instrumentation phases | 5: inspect, matching, different, invalid, missing; all PASS |
| Physical installs | 2: retained-data qualification product update + harness |
| Physical harness removal / provider restoration | 1 / 1 PASS |
| External final artifact-verifier set / embedded Gradle verifiers | 0 / 0 |
| Existing signed-product verifier inside qualifier | 1 PASS |
| Human Nix / agent Nix | 0 / 0; explicitly deferred |
| Stage / commit / push / tag / release operations | 0 |

No production/test/build checkpoint was invalidated; no full Gradle gate was repeated.
Only included harness tooling changes affect the future combined Wave 2A Nix input.
No source changes followed successful physical qualification; only this excluded
report was finalized afterward.

## Final Git state

main, HEAD `d21c15dd4fbfca799ad2b354690e86b118a4aaa0`, VERSION 0.1.2 / code 3.
Index empty, everything unstaged/uncommitted; git diff --check PASS.
Combined tracked diff is 185 insertions / 246 deletions across ten files: the
starting nine Python changes plus this Java file. Untracked files are additive
and are not included in that tracked diff statistic.

```text
 M tools/device/SafOutboundRegression.java
 M tools/device/build-otpauth-ingress-tests.py
 M tools/device/build-saf-bootstrap-tests.py
 M tools/device/build-saf-inbound-tests.py
 M tools/device/build-saf-outbound-tests.py
 M tools/device/check-row-reveal.py
 M tools/device/qualify-saf-bootstrap.py
 M tools/device/qualify-saf-outbound.py
 M tools/device/qualify-security.py
 M tools/device/qualify-syncthing-e2e.py
?? review/ANDROID_SAF_OUTBOUND_AUTOSYNC_RECONCILIATION_REPORT.md
?? review/ANDROID_SUPPORT_TOOLING_WAVE2A_REPORT.md
?? tools/device/android_harness.py
?? tools/device/test_android_harness.py
```

This milestone stops here. Remaining Wave 2A physical work and its eventual human
Nix gate are separate from this completed outbound reconciliation.
