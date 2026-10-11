# SAF inbound reveal-completion reconciliation

Status: **COMPLETE for this narrow milestone.** Full inbound physical PASS, 34 checks;
provider/device restoration PASS. Wave 2A remains incomplete. Nix deferred.

## 1. Starting HEAD and dirty-tree verification

main at `d21c15dd4fbfca799ad2b354690e86b118a4aaa0`, VERSION `0.1.2`, VERSION_CODE `3`.
Before inspection/editing, recorded branch, HEAD, status, diff stat/name-status and versions.
Exactly twelve modified tracked files (321 additions / 259 removals), seven untracked
accumulated reports and two untracked Python support sources match the previous inbound
report's final state. No unrelated modification. Explicit dirty-tree authorization
supersedes the ordinary clean-main prerequisite. No reset, stash, stage or commit.

Read the [prior inbound reconciliation](ANDROID_SAF_INBOUND_AUTOSYNC_RECONCILIATION_REPORT.md)
and its earlier Wave 2A, outbound, completion/final-attempt and two bootstrap reports;
preserved them unchanged. Inspected AGENTS, README, package.nix, flake.nix, CI, inbound
builder/harness and shared module. Starting hashes, complete inherited diff, prior Java,
manifest and unsigned harness APK are retained in ignored `.gradle/inbound-reveal-completion/`.

## 2. Classification and selected checks

**TEST_OR_QUALIFICATION_TOOLING — physical harness completion-boundary correction.**
No PRODUCT_CODE, BUILD_DEPENDENCY, protocol or release/signing change. Selected one
inbound build, payload/class construction checks, mechanical suite, whitespace,
one supported physical inbound attempt and independent restoration verification.
Standalone device Java is outside Gradle source/test consumers. No full Gradle or
unrelated row/security/M3D checks required. tools/** is a future Nix input.

## 3. Prior failure

The previous sole attempt passed 27 checks through automatic-Sync/provider/ownership
assertions, then failed `isolated_code_revealed`. Later retry/conceal, lock and explicit
deletion assertions were not reached. That evidence remains unchanged. Accepted
showCode plus the old waitIdle did not prove generation completion.

## 4. Current showCode execution model

AndroidVaultController.showCode (253–284) admits only a usable row in OPEN with no
conflicting controller admission, clears earlier presentation, captures epoch/session
and coordinator, sets revealing=true under the controller monitor, then submits to
the bounded worker. Returning true establishes admission. Worker generation occurs
later, outside that monitor, through Backend/ForegroundVaultCoordinator.generateTotp.
It checks presentation/session ownership before and after generation and publishes
only an AVAILABLE result that remains relevant.

Coordinator generation (326–357) captures the live session state, checks active
single-alternative token/basis, invokes Java generation, validates code/interval,
and returns AVAILABLE/UNAVAILABLE_NEEDS_ATTENTION/STALE/FAILED. Current Android's
additional relevance checks are application policy and remain untouched.

Read the retained exact reviewed API_DESIGN from guidance commit
`f0a028676c1801a10b2d8d2650bf9c24357d1fa1`, including operation classes, replay stream,
causal bases, merge freshness, persistence handles, blocking/threading/close and
observation ordering. Java TOTP is class B local secret-backed projection: ordinary
asynchronous work, same-session references, session must remain open, and its time
interval independently limits presentation. No dependency or guidance pin changed.

## 5. revealing / revealedCode semantics and lifecycle evidence

revealing starts before worker execution and remains true through generation and
presentation publication. Worker finally clears it under the monitor, clears revealTask,
and schedules view/Sync drain work. Rejected admission returns false; a queued reveal
can also be retired without generation. Generation failure, stale relevance, hide,
lock/session replacement or expired interval can legitimately leave revealedCode null.
Thus revealing=false alone is insufficient evidence of success.

TotpControllerTest.idle explicitly includes revealing; its show helper awaits idle AND
non-null revealedCode. Latch-based revealScreenSequence establishes OPEN/unchanged
screen state while generation is blocked, then actual revealed code after release.
syncEnabledAndAdmittedDuringGenerationUsesExistingWorkerOrdering establishes independent
Sync admission during generation. safeGenerationFailureRetainsOpenSession establishes
completed-but-null failure. revealOwnershipAndHideRevokeInflightReveal,
stateReplacementBeforeControllerPublicationRejectsReveal, queuedRevealYieldsPendingSlotToRealAdd
and lock tests establish retirement. syncAdmissionConcealsWithoutReopeningSession
establishes concealment; explicit importProviderChanges itself calls clearPresentation.

TotpPresentation.reveal publishes detached code through presentationChanged while the
controller monitor is owned. snapshot rechecks expiry. Timer uses the original absolute
validUntil and monotonic deadline, at most one-second ticks; it never extends exposure
or generates the next code. TotpPresentationTest covers actual remaining interval,
exact expiry, past/future interval rejection, wall movement, copy and original deadline.
These existing tests were inspected, not rerun or edited.

## 6. Proof old wait boundary was insufficient

waitIdle checks operating/viewQueued/providerActive/syncPending/syncDrainQueued under
the monitor. showCode does not set those five flags when starting local generation.
They may all be false with revealing=true and no published code. Source and delayed
JVM tests expressly permit this OPEN asynchronous interval. This is a harness sampling
gap; source does not promise non-null code at the old boundary. The physical rerun
now passes, without any product change.

## 7. Selected completion condition

After accepted showCode, wait specifically for revealing=false AND revealedCode!=null,
with the captured coordinator/session still owned and snapshot state OPEN. Evaluate
under the controller monitor, matching product ownership. Completion with null fails
immediately as failed/retired/expired; session retirement fails immediately; generation
has a 30-second monotonic bound. The existing 20ms polling interval merely samples
state; elapsed sleep is never accepted as completion. No retry, clock manipulation,
interval extension or arbitrary correctness sleep added. Snapshot/error outcomes do
not expose an exact internal generation failure cause; diagnostics deliberately name
the possible null outcomes rather than inventing one.

## 8. Exact Java change

Only tools/device/SafInboundRegression.java changes executable bytes relative to the
accumulated starting tree: add reveal-specific waitReveal; replace only showCode's
waitIdle with waitReveal. All other waitIdle uses retain operation/Sync quiescence
semantics (construction, unlock, observation, explicit retry, lock/finally cleanup).
No general helper, other Java or Python source changed.

```diff
--- Wave2A/SafInboundRegression.java
+++ reveal-completion/SafInboundRegression.java
@@ -33,6 +33,26 @@
             SystemClock.sleep(20);
         }
         throw new AssertionError("Worker timed out");
+    }
+    private void waitReveal() throws Exception {
+        step = "isolated_reveal_completion";
+        long end = SystemClock.uptimeMillis() + 30000;
+        while (SystemClock.uptimeMillis() < end) {
+            synchronized (isolated) {
+                Object snapshot = call(isolated, "snapshot");
+                if (field(isolated, "vault") != openedCoordinator
+                        || field(openedCoordinator, "session") != openedSession
+                        || !call(snapshot, "state").toString().equals("OPEN"))
+                    throw new AssertionError("Reveal session retired");
+                if (!(Boolean)field(isolated, "revealing")) {
+                    if (call(snapshot, "revealedCode") == null)
+                        throw new AssertionError("Reveal completed without a code (failed, retired or expired)");
+                    return;
+                }
+            }
+            SystemClock.sleep(20);
+        }
+        throw new AssertionError("Reveal generation timed out");
     }
     private void command(String name, Class<?>[] types, Object... args) {
         runOnMainSync(() -> {
@@ -191,7 +211,7 @@
             check(before.equals(inventory(uri)), "automatic_open_sync_zero_provider_mutation");
             Object firstToken = ((List<?>)call(call(call(isolated, "snapshot"), "view"), "tokens")).get(0);
             Object id = call(firstToken, "id");
-            command("showCode", new Class<?>[]{loader.loadClass("org.totipo.TokenId")}, id); waitIdle();
+            command("showCode", new Class<?>[]{loader.loadClass("org.totipo.TokenId")}, id); waitReveal();
             check(call(call(isolated, "snapshot"), "revealedCode") != null, "isolated_code_revealed");
             // Explicit Import now proves concealment and idempotent no-change retry only.
             command("importProviderChanges", new Class<?>[0]); waitIdle();
```

## 9. Durable reveal assertion

The original `revealedCode != null` assertion and its name remain verbatim, following
the successful completion helper. It still requires the actual current observable
TOTP reveal; accepted command or completed work alone cannot pass. No code literal
or weaker UI/admission proxy substituted. Existing real-time expiry remains enforced.

## 10. Automatic-Sync assertions preserved

All prior 27 checks are byte-identical and passed again: persisted grants/exact root,
exact fixture, pre-observation VAULT-only isolation, matching VAULT, locked refusal,
OPEN, three tokens, captured and retained coordinator/session/store/owner, intended
VaultId/binding, verified automatic Sync, exact ciphertext map, single alternatives,
issuer/accounts and zero provider mutation. No manual-first-Import behavior restored.

## 11. Explicit retry/conceal preserved

Explicit importProviderChanges and all subsequent assertions remain verbatim and
passed: code concealed, exact `No new objects.`, identical coordinator/session/store/
owner and complete unchanged provider inventory. The initial import stays automatic.

## 12. Lock and temporary-root cleanup preserved

Normal Lock, null coordinator/session-closure assertion, explicit shutdown and
reverse-order deletion followed by `!Files.exists(directory)` remain unchanged and
passed physically. Deletion evidence comes from the explicit scenario assertion,
not solely its failure-finally cleanup. No lifecycle semantics changed.

## 13. Host construction

One invocation PASS:

```sh
GRADLE_USER_HOME="$PWD/.gradle/wave1-gradle-home" python3 -B tools/device/build-saf-inbound-tests.py
```

Supplied pinned JDK 17 / SDK platform 37.0 / Build Tools 36.0.0 and existing qualified
cache used. Explicit authored instrumentation class remains SafInboundRegression only;
D8 selection is that exact family. Parsed packaged DEX class definitions: authored
class plus only its legitimate ExternalSynthetic helpers; no stale/unrelated authored
class or host InboundFixture. Fixture remains released-Java-authored and unchanged.
Prior/current ZIP entry names/order and all payloads equal except classes.dex.
Manifest source/binary, assets/vault unchanged; package org.totipo.safqualification,
target org.totipo.android, min SDK 26 / target 37 unchanged.
Evidence: construction-validation.json and before-* copies in ignored evidence directory.
Unsigned harness SHA-256 `5910c7b8e0eeb9b9c93831794789dacf1eda33466d68364b142c5ac9ecb20b50`.

## 14. Mechanical tests

`python3 -B -m unittest discover -s tools/device -p 'test_android_harness.py'`:
one invocation, eight tests PASS. `git diff --check` PASS. No unrelated suites.

## 15. Fresh device/environment identity

Serial 37311JEGR05916, Pixel 6a, Android 17/API 37, fingerprint
`google/bluejay/bluejay:17/CP3A.261005.002.A1/2026100601:user/release-keys`.
Fresh installed product SHA-256
`0f262717e1a8ffa3e7d6925e15f50940366035f6af73826a44ddd605c285be30`;
qualification signer CN=Disposable Wave 2A Qualification, SHA-256
`d862bbf279d306e7062440d299440d54ecdf594e1cf8ac3046cdf5d3a0ed7f49`.
Persisted READ/WRITE (persisted=0x3), exact URI
`content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FTotipo-M3A-Test`.
Designated root `/sdcard/Documents/Totipo-M3A-Test`; freshly pulled complete inventory
contains VAULT plus five objects and only objects-v1 directory. Every preflight path/hash
is in preflight.json; unchanged original VAULT SHA-256
`69a4d78bd3ff7e598bea7faf184809c5881d459e36dccbf1ddef161499c3e7b5`.
Human freshly confirmed: “Confirmed: unlocked, same disposable environment” in response
to the question naming device, product/signer, exact root and no real user vault/data.
No real user vault opened; scenario uses its own fresh private cache root.

## 16. Physical inbound result

Reused the prior reconstructed historical sign-install/direct instrumentation procedure,
not a new qualifier interface. Existing same-signer copies installed with data retained;
exact matching fixture prepared and compared before normal instrumentation:

```sh
adb shell am instrument -w org.totipo.safqualification/org.totipo.safqualification.SafInboundRegression
```

Exactly one attempt, **M3A PASS checks=34**, procedure exit 0. All 27 prior checks,
actual reveal, explicit retry conceal/no-change, identity/nonmutation, Lock/session
closure and explicit temporary-root deletion passed. Evidence physical.log and
inbound-device-tests.txt. Signed harness SHA-256
`86c0fc412c3c7700db3e3caa1278ffefb6825f4a879c123831582c0d0aa3354a`,
same qualification signer as product. No physical retry.

## 17. Provider/device restoration

PASS: prepared complete four-file fixture equals host fixture before execution and
is unchanged afterward (provider-scenario-before/after.json). Existing provider_backup
restored original vault/objects-v1 without moving root. Independent complete designated
folder comparison proves all six original file paths/hashes AND directory inventory
identical after restoration. restoration-validation.json retains before/after maps.
Instrumentation uninstall succeeded; pm path exit 1 with empty output confirms absence.
Stay-awake restored exactly 0 → 3 → 0. Force-stop before restoration and product relaunch
completed. Pulled final product bytes equal starting APK byte-for-byte; final signer
reverified equal. Fresh final permission dump retains intended persisted READ/WRITE root.
Provider backups, scenario pulls, cleanup commands and final APK evidence retained.

## 18. Failures, retries and execution counts

No host, physical or restoration failure in this milestone; no physical retry or
post-physical executable change. Ignored orchestration reuses prior procedure and
accounts for the previously documented normal pm-path exit 1 on absent instrumentation.
This changes no tracked/shared helper or qualifier interface. Earlier failures remain
in unchanged historical reports.

| Category | This milestone |
| --- | --- |
| Baseline / final normal / strict-clean Gradle | 0 / 0 / 0 |
| Focused Gradle compile / tests / boundary | 0 / 0 / 0 |
| Host inbound build | 1 PASS |
| Mechanical suite | 1 invocation / 8 PASS |
| Physical procedure / inbound instrumentation | 1 / 1 PASS (34 checks) |
| Physical retries | 0 |
| Qualification-copy signing / installs | 2 / 2 |
| Existing signed-product verifier inside sign-install | 1 PASS |
| Final external artifact-verifier set / embedded Gradle verifiers | 0 / 0 |
| Provider restore / instrumentation removal / setting restore | 1 / 1 / 1 PASS |
| Human / agent Nix | 0 / 0 |
| Stage / commit / reset / stash / release operations | 0 |

No repeated or invalidated full Gradle gate. Qualification signing uses existing
ignored disposable material only; protected signing material unnecessary and untouched.

## 19. Product/build/release non-change and input stability

All starting dirty/untracked hashes except authorized inbound Java remain identical.
Zero product/JVM/build/dependency/release change: app/**, Gradle files/locks, gradle/**,
VERSION/VERSION_CODE, package.nix/flake.nix/flake.lock/package-deps.json, workflows,
release/** and tools/release/** unchanged. Unsigned product release SHA-256 remains
`b70501790d32ff383665c8f7c1a599d750036b91a3b1fb0b804f706b8341e012`;
installed bytes unchanged. No product behavior, reveal lifetime/security policy,
automatic Sync, SAF protocol, dependency or fixture changes.

Production source stable and unchanged; JVM tests stable and unchanged; build/package
configuration stable and unchanged. This inbound harness stable after host/full physical
PASS. Overall Wave 2A qualification tooling is not finally frozen by this milestone.
Actual package filtering includes tools/** but excludes review/** and generated evidence;
no inspected evaluator/check consumes this report. Report finalization invalidates no
product/device pass. Included harness change affects eventual combined Nix inputs.

## 20. Nix explicitly deferred

NOT REQUESTED / NOT RUN. Final human Nix belongs to eventual Wave 2A completion.
No ingress, M3D, final Wave 2A host freeze or Wave 2B work performed.

## 21. Layered diff

Inherited mechanical nine Python callers/shared module/tests; outbound reconciliation;
bootstrap reconciliations; previous inbound automatic-Sync reconciliation; and seven
accumulated reports remain intact. Relative to that accumulated starting tree this
milestone adds **20 Java lines and replaces one wait call** (+21/-1), plus this new
excluded report. No other Java/source changes. Exact layered diff is embedded above
and retained as milestone-java.diff; complete inherited diff retained separately.

## 22. Wave 2A resume instructions

Inbound now has complete 34-check physical PASS and restored environment. Preserve
this helper, all automatic-Sync/retry/cleanup assertions and previous evidence.
Resume Wave 2A separately for remaining ingress physical qualification, applicable
M3D packaging/observer qualification, final affected host checks and all included-input
freeze before its eventual single human Nix. This report does not claim those gates,
full Syncthing convergence or overall Wave 2A completion. Do not proceed to Wave 2B here.

## 23. Final Git state

main at starting HEAD, VERSION 0.1.2/code 3; index empty. All work remains unstaged/
uncommitted. Twelve tracked modified files remain; this report is the eighth accumulated
untracked report, alongside the two untracked support Python files. Only inbound Java
and this report differ from milestone start. Final preservation/scope/whitespace PASS.

```text
 M tools/device/SafBootstrapRegression.java
 M tools/device/SafInboundRegression.java
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
?? review/ANDROID_SAF_BOOTSTRAP_CURRENT_SYNC_RECONCILIATION_REPORT.md
?? review/ANDROID_SAF_INBOUND_AUTOSYNC_RECONCILIATION_REPORT.md
?? review/ANDROID_SAF_OUTBOUND_AUTOSYNC_RECONCILIATION_REPORT.md
?? review/ANDROID_SUPPORT_TOOLING_WAVE2A_COMPLETION_REPORT.md
?? review/ANDROID_SUPPORT_TOOLING_WAVE2A_FINAL_REPORT.md
?? review/ANDROID_SUPPORT_TOOLING_WAVE2A_REPORT.md
?? tools/device/android_harness.py
?? tools/device/test_android_harness.py
?? review/ANDROID_SAF_INBOUND_REVEAL_COMPLETION_REPORT.md
```
