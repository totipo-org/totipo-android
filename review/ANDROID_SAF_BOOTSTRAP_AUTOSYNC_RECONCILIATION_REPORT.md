# SAF bootstrap automatic-Sync reconciliation — stopped at intent prerequisite

Status: **INCOMPLETE / STOPPED before Java editing or physical qualification.**
Classification: **TEST_OR_QUALIFICATION_TOOLING — physical regression semantics correction**.
No PRODUCT_CODE or BUILD_DEPENDENCY work. This report records a mandated stop,
not a completed reconciliation or bootstrap PASS.

## 1. Starting HEAD and authorized dirty state

Before editing, recorded branch, HEAD, status, diff stat/name-status and versions:

```text
main
d21c15dd4fbfca799ad2b354690e86b118a4aaa0
VERSION 0.1.2
VERSION_CODE 3
10 tracked modified files; 185 insertions / 246 deletions
```

The dirty tree exactly matched the ten authorized executable changes, two untracked
support sources and three untracked reports. No unrelated modifications were found.
The user's explicit dirty-tree instruction supersedes AGENTS' ordinary clean-main
prerequisite. No reset, stash, stage, commit or rewriting of existing work occurred.

The three starting reports remain unchanged:
[Wave 2A](ANDROID_SUPPORT_TOOLING_WAVE2A_REPORT.md),
[outbound reconciliation](ANDROID_SAF_OUTBOUND_AUTOSYNC_RECONCILIATION_REPORT.md),
[completion attempt](ANDROID_SUPPORT_TOOLING_WAVE2A_COMPLETION_REPORT.md).
Starting tracked diff and preserved-file hashes are retained under ignored
`.gradle/bootstrap-autosync-reconciliation/` as `starting-diff.patch` and
`preserved-files.json`. These were captured before creating this report; no existing
source/report was edited during inspection.

## 2. Exact stale assertion and decisive historical evidence

Current `tools/device/SafBootstrapRegression.java:158–160` performs successful
Join, waits, checks OPEN, then checks:

```java
check(tokens()==0,"join_no_automatic_import");
```

`git log --follow` identifies introduction commit `d4aaee0` (Add immutable VAULT
bootstrap and device enrollment). Its Java contains this same assertion separately
from `empty_local`, followed by explicit `importProviderChanges` and `waitTokens(3)`.

The [original M3C report](M3C_IMMUTABLE_VAULT_BOOTSTRAP_REPORT.md), section 21,
explicitly states:

> Join produces an ordinary empty OPEN local session; Import remains a separate user action.

It further says higher-level controller tests and the physical harness assert that
separation. Section 30 records the historical physical sequence: correct Join opened
an empty session; explicit Import subsequently produced three tokens in that session.
Section 6 separately describes local absence/orphan safety before enrollment.

**Thus the post-Join assertion originally tested intentional separation of Join and
Import under the former product contract. History does not support claiming it was
only invariant A (pre-observation local emptiness) misplaced after C/D.** This triggers
the user's section 4 instruction: “If history/source does not support that
interpretation: STOP and report.” No Java correction was made.

## 3. Original and current Join phases

| Phase | Original scenario and current source evidence | Disposition |
| --- | --- | --- |
| A: isolated root | Fresh temporary root; `empty_local` checks NO_LOCAL_VAULT after controller discovery. Distinct from the later zero-token assertion. | Preserved; no new filesystem assertion added. |
| B: wrong password | Prepare candidate, submit wrong password; local VAULT and objects namespace remain absent; provider inventory remains equal. | Preserved byte-for-byte. |
| C: successful Join/open | Authenticate/recheck exact candidate, install canonical VAULT and open normally; assert OPEN. | Current product remains authority. |
| D: automatic Sync | Former contract expressly separated Import. Current `beginJoin` calls `opened()`, which subscribes/renders and requests automatic Sync. | Former post-Join emptiness expectation is superseded. |
| E: matching objects | Former harness explicitly imports, waits for three tokens and retains the session. Current automatic Sync imports/observes before publishing. | No updated runtime assertion implemented. |
| F: identity | Exact provider/local VAULT hash and VaultId; subsequent same-session and unchanged-provider checks. | Existing checks preserved; no fresh physical evidence. |
| G: other scenarios | Initialize, orphan veto, foreign-vault refusal and cleanup follow in the normal qualifier. | Preserved; not executed here. |

The meaningful pre-observation emptiness invariant exists independently, but cannot
be substituted for the historical no-automatic-import requirement while claiming
the two assertions had identical original intent.

## 4. Current automatic-Sync authority

`AndroidVaultController.beginJoin` calls `opened()` after successful enrollment
(line 650). `opened()` (448–457) establishes session observation, renders, and calls
`requestAutomaticSync()`. `requestAutomaticSync/queueSyncDrain` (791 onward) use
`syncPending` and `syncDrainQueued` to coalesce and defer admission until operation,
view and provider work allow it. `startSync()` imports, calls `observeForSync`, then
publishes with the existing identity/preflight/readback/postflight path.

[Daily-driver report](ANDROID_DAILY_DRIVER_UX_REPORT.md), section 9, explicitly
includes successful Join/open in automatic Sync. Section 12 explains coalescing and
the special isolation of older lower-level controller tests. README likewise says
Join opens and triggers ordinary Sync. This change was introduced by daily-driver
commit `38b251e9bbd7d9ede69571e3ca16d15bac8e939e`.

`AndroidVaultControllerTest.joinWrongPasswordThenExactEnrollmentThenManualImportOnly`
still asserts empty post-Join state, but the test dispatcher clears `syncPending`
unless `automaticSync` is enabled (lines 58–68). It is lower-level isolated coverage,
not evidence that production Join must remain empty. Current daily-driver tests,
including `dailyUnlockAndForegroundAutoSyncAreCoalescedAndLockedForegroundDoesNothing`
and `dailyAddCommitsOfflineDoesNotLoopAndManualSyncRetries`, exercise automatic
behavior. No tests were rerun or modified in this stopped milestone.

Post-Join emptiness is therefore invalid as a stable current-product expectation;
it can race automatic import. This is a demonstrably superseded historical
requirement, rather than evidence of a product bug or an ambiguous current contract.
The exact pinned Java guidance was fetched read-only to the ignored evidence directory;
no dependency/source pin changed. `requestRefresh()` remains an asynchronous request,
not completion or acknowledgement.

## 5. Assertion/action mapping and waitIdle disposition

**Implemented old → new mapping: none. No assertion or action changed.**
`join_no_automatic_import`, the explicit first import and all other Java remain
exactly as at starting HEAD. No assertion count was preserved artificially or weakened.

Bootstrap `waitIdle` currently checks operating, viewQueued and providerActive under
the controller monitor, omitting syncPending and syncDrainQueued. The corrected
outbound harness checks both additional flags under the same monitor. This is a
confirmed bootstrap completion-boundary gap, but it was not patched after the
section 4 stop. No race-sensitive briefly-empty wait, product switch or arbitrary
sleep condition was introduced.

A future expressly authorized correction can retire the superseded no-auto-import
requirement, assert actual empty local storage before controller construction, await
all automatic Sync flags, and verify exact three-token issuer/account/single-alternative
state, ciphertext inventory, session identity and provider non-mutation. That would
be a deliberate updated-contract test, not a relocation justified by original intent.
It has not been implemented, built or physically qualified here.

## 6. Preserved safety assertions

Wrong password: unchanged checks for absent local VAULT, absent local objects-v1
and byte-identical provider inventory. The current physical Java does not separately
assert the failure message or binding/session transition; no such extra runtime
coverage is claimed. Controller source returns “Could not unlock this vault.” on
failed detached authentication; the lower-level controller test checks NO_LOCAL_VAULT.

Initialize: unchanged isolated create/OPEN, one authored public token, exact provider
VAULT and objects directory, two entries before explicit publication, repeat
initialization without mutation, one published object, immutable VAULT, idempotent
repeat publication, canonical local VAULT and retained session. No separate stale
initialize assumption was investigated through physical execution or corrected.

Orphan: unchanged object evidence without VAULT veto, truthful “will not initialize”
message, zero provider mutation and no deletion/garbage treatment.

Different vault: unchanged initialization/import/publication refusal, exact truthful
messages, zero provider mutation, canonical local VAULT and same live session.
Automatic Sync was not used to relax any safety check.

All of the above are source-preservation statements, **not fresh scenario PASSes**.

## 7. LocalStoreQualification and host construction

The current Wave 2A bootstrap builder explicitly compiles SafBootstrapRegression
and LocalStoreQualification and passes their selected classes to D8. InboundFixture
is separately compiled/executed on the host to author the three public tokens and
is excluded from instrumentation DEX. Manifest package remains
org.totipo.safqualification, target org.totipo.android, min SDK 26, target SDK 37;
asset VAULT comes from the existing fixture.

SafBootstrapRegression does **not invoke LocalStoreQualification.run**. Packaging
is not bootstrap runtime coverage. No invocation was added and no separate local
store physical PASS is claimed. Any independently required local-store execution
remains a distinct qualification responsibility.

No affected builder was run because inspection triggered the pre-edit stop.
Consequently no fresh D8-input, manifest-binary, asset-identity or APK equivalence
PASS is claimed. Prior Wave 2A construction comparison remains historical evidence.
No mechanical host suite, syntax suite or unrelated row/security/M3D suite was run.
The final lightweight report/scope/hash and `git diff --check` checks are recorded below.

## 8. Physical preconditions, phases and restoration

One read-only `adb devices -l` discovery found serial `37311JEGR05916`, model
Pixel_6a, product/device bluejay. No physical qualifier, instrumentation, install,
signing command, app launch, provider pull/mutation or device setting change ran.
API/fingerprint, installed APK SHA/signer, current binding and local-vault state
were **not freshly established**. No disposable-environment confirmation was requested
because the earlier source prerequisite failed. Historical outbound device/app identity
must not be presented as current precondition evidence.

| Normal bootstrap phase | This milestone |
| --- | --- |
| Permission inspection | NOT RUN |
| Join / wrong password / matching successful path | NOT RUN |
| Initialize | NOT RUN |
| Orphan refusal | NOT RUN |
| Different-vault refusal | NOT RUN |
| Qualifier restoration/removal/relaunch | NOT RUN; no mutation requiring cleanup |

The shared Wave 2A qualifier retains provider_backup over the designated
`/sdcard/Documents/Totipo-M3A-Test` children vault and objects-v1; it retains the
root, restores with hash checks, force-stops before restoration, removes harness
and relaunches product. Those mechanics remain unchanged. No fresh provider/local
restoration success, instrumentation absence or retained-app identity is claimed.
No protected signing material or real vault was accessed.

## 9. Failures, execution counts and invalidation

No host build/test or physical failure occurred; the stop is historical/source
contradiction of the required intent interpretation. No retries or corrective edits.
A read-only search initially used an incorrect coordinator path and was nonessential
to the decisive evidence; it changed no files.

| Execution category | Count |
| --- | ---: |
| Baseline/final normal/strict-clean Gradle | 0 / 0 / 0 |
| Focused Gradle compile/test/boundary | 0 / 0 / 0 |
| Bootstrap host builds / physical-qualifier builds | 0 / 0 |
| Mechanical host suite / syntax suites | 0 / 0 |
| Physical qualifier / instrumentation phases / installs | 0 / 0 / 0 |
| External artifact-verifier set / embedded Gradle verifiers | 0 / 0 |
| Human Nix / agent Nix | 0 / 0 |
| Signing / release / stage / commit / push | 0 / 0 / 0 / 0 / 0 |

No executable input changed and no gate was invalidated or repeated. Actual
package.nix filtering selects tools/**, excludes review/ and generated .gradle/build
paths; flake evaluation/cache dependencies are unchanged. This excluded report
invalidates neither Gradle nor Nix. **Nix explicitly deferred, not requested or run.**

Production source stable and unchanged; JVM tests stable and unchanged;
build/package configuration stable and unchanged; existing qualification-tooling
bytes stable but overall Wave 2A qualification remains incomplete. No final overall
host/source freeze or completed bootstrap qualification is claimed.

## 10. Three diff layers and resume instructions

A. Existing mechanical Wave 2A changes: nine modified Python callers plus untracked
android_harness.py/test_android_harness.py and its report. Preserved unchanged.

B. Existing outbound reconciliation: SafOutboundRegression.java and its report.
Preserved unchanged. The preexisting completion-attempt report is also preserved.

C. This bootstrap attempt: **only this new report**. SafBootstrapRegression.java
has zero diff; no other Java was modified by this attempt.

Product app/**, JVM tests, dependencies, Gradle/build/Nix configuration, versions,
release tooling/workflows and release artifact sources have zero diff against HEAD.
The ten starting tracked modifications remain exactly 185 additions / 246 removals;
the index is empty. Existing dirty/untracked file hashes and tracked diff were
compared after report creation and match the preserved snapshot.

Resume requires explicit handling of section 4's historical-intent stop: authorize
retiring the former Join/Import-separation requirement as superseded, rather than
claiming it originally meant only pre-observation emptiness. Then perform the narrow
Java-only bootstrap correction and required host construction/tests; freshly establish
device/app/provider/local identity and obtain the required disposable confirmation;
run the full supported no-argument bootstrap qualifier once and verify restoration.
Stop on any separate scenario/product/packaging mismatch under the original rules.

Only after bootstrap reconciliation is qualified may a separate resumed Wave 2A
completion proceed to ingress/M3D and remaining responsibilities, final host freeze
and eventual single human Nix. No ingress, M3D, Wave 2B or Nix work proceeded here.

## 11. Final Git state and validation

main at original HEAD d21c15dd4fbfca799ad2b354690e86b118a4aaa0; VERSION 0.1.2 /
code 3. All work remains unstaged/uncommitted. Final `git diff --check`, preserved-file
hash comparison, tracked-diff equality, empty-index check and local report-link
validation PASS.

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
?? review/ANDROID_SAF_BOOTSTRAP_AUTOSYNC_RECONCILIATION_REPORT.md
?? review/ANDROID_SAF_OUTBOUND_AUTOSYNC_RECONCILIATION_REPORT.md
?? review/ANDROID_SUPPORT_TOOLING_WAVE2A_COMPLETION_REPORT.md
?? review/ANDROID_SUPPORT_TOOLING_WAVE2A_REPORT.md
?? tools/device/android_harness.py
?? tools/device/test_android_harness.py
```

The milestone's completion criteria remain unmet. Work stops at the mandatory
intent prerequisite; no bootstrap physical PASS is claimed.
