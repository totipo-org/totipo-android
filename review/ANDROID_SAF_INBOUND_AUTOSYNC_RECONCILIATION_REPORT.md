# SAF inbound automatic-Sync reconciliation

**Status: INCOMPLETE / STOPPED after the sole physical inbound run failed at the unchanged reveal assertion.**
The automatic-Sync contract checks passed; a complete inbound PASS is not claimed.
Classification: **TEST_OR_QUALIFICATION_TOOLING — physical regression semantics / qualification-contract correction**.
No PRODUCT_CODE, BUILD_DEPENDENCY, protocol, language migration or release/signing work.

## 1. Starting HEAD and dirty-tree verification

Started on main at `d21c15dd4fbfca799ad2b354690e86b118a4aaa0`; VERSION `0.1.2`,
VERSION_CODE `3`. Recorded branch, HEAD, status, diff stat/name-status and both versions
before inspection/editing. Exactly eleven tracked modified files, two untracked support
sources and six accumulated untracked reports matched the reviewed Wave 2A state:
221 insertions / 252 deletions. No unrelated modifications. The user's required dirty
tree overrides AGENTS' ordinary clean-main prerequisite. No reset, stash, reconstruction,
stage or commit.

Read AGENTS.md, README.md, all six requested reports, full inbound Java/builder,
android_harness.py and InboundFixture. Inspected controller open, persisted binding,
Sync admission/drain/completion, relevant current/historical tests and reports,
package.nix, flake.nix and CI. Starting hashes and diff are preserved under ignored
`.gradle/inbound-autosync-reconciliation/{starting-files.json,starting-diff.patch}`.
Earlier reports and all inherited executable changes remain byte-identical.

## 2. Classification and selected validation

Standalone device harness correction only. Host inbound construction, payload/class
comparison, eight mechanical tests, whitespace and one supported inbound physical
procedure selected. Device Java is not a Gradle product/JVM-test input. No product
Gradle rebuild, strict-clean or unrelated row/security/M3D suite is required here.
`tools/**` is an effective Nix input; Nix is expressly deferred by this authorization.

## 3. Historical origin of isolated_replica_initially_empty

`git log --all -- tools/device/SafInboundRegression.java` identifies introduction
`e69c4f2d3d93547afb72ceace7e9e1c28a82449d` (Add SAF binding and isolated inbound sync).
At that commit the sequence is: create a fresh temporary root with only the fixture
VAULT; construct the bound controller; unlock; waitIdle; assert `tokens()==0` as
`isolated_replica_initially_empty`; explicitly import; wait for three tokens; compare
coordinator/session/store/owner; check honest first-import status; reveal, retry/conceal,
lock and delete. The r19 update `a5e0d8324b3933e0a3921980974bc35b49b4edc6` only renames
wrapper to canonicalVault in this Java; it leaves the post-unlock/manual-first-import
sequence intact. Its builder now supplies matching canonical VAULT rather than the
original deliberately invalid provider VAULT. The assertion predates automatic Sync.

## 4. Historical inbound Import/open contract

Decisive evidence is historical, not inferred solely from today's harness:

- [M3A report](M3A_SAF_BINDING_INBOUND_SYNC_REPORT.md), section 2, explicitly scopes
  the feature to manual inbound import and excludes automatic import.
- Section 33, “Startup accessibility,” explicitly says no automatic candidate scan/import
  occurs at startup or unlock. This statement is also present at introduction `e69c4f2`.
- Sections 16 and 22 record same-session import and the physical 14-check sequence:
  isolated empty replica, explicit import/three-token live observation, unchanged VAULT,
  honest import status, reveal/conceal, exact retry, closure and unchanged provider hashes.
- Introduction-era `AndroidVaultControllerTest.manualProviderImportUsesWorkerSameSessionAndHonestCoverage`
  unlocks the bound controller, then explicitly invokes Import and checks three-token
  state, one open/zero closes, preserved wrapper and later no-change retry.
- Introduction-era README says no product Sync button or automatic provider synchronization.

**B was an intentional former product interaction requirement:** unlock/open stayed
empty until the explicit first Import. **A is a separate durable setup invariant:**
the isolated replica starts with VAULT only and no imported object state. It would be
incorrect to describe the historical post-open assertion as only A misplaced in time.

## 5. Current automatic-Sync authority

Current README and [daily-driver report](ANDROID_DAILY_DRIVER_UX_REPORT.md), sections
7–12, define automatic Sync after successful unlock/open, foreground return and local
mutation; normal UI has no Import/Publish decision. Daily-driver commit
`38b251e9bbd7d9ede69571e3ca16d15bac8e939e` introduced the current UX.

AndroidVaultController.opened() establishes observation and requests automatic Sync.
requestAutomaticSync sets syncPending; queueSyncDrain posts/coalesces admission;
startSync imports, awaits Java observation, then performs publication preflight;
finishOutbound sets lastSyncResult to “Verified against bound shared folder” only for
successful unified Sync. Binding restoration performs accessibility checking, and
open uses the persisted selected root. Current controller test
`dailyUnlockAndForegroundAutoSyncAreCoalescedAndLockedForegroundDoesNothing` explicitly
exercises unlock-triggered Sync. Older internal Import/Publish tests deliberately clear
syncPending in their test dispatcher unless automaticSync is enabled; this is not a
production switch or current UX requirement.

Read exact Java guidance at `f0a028676c1801a10b2d8d2650bf9c24357d1fa1`, including operation
classes, stream/observation ordering, editing, merge, persistence and close. Retained
read-only copy in the ignored evidence directory. requestRefresh returning is not
completion or acknowledgement; product observeForSync supplies its documented local
barrier. No runtime/guidance pin changed.

## 6. Evidence-based decision

**CASE 2: RETIRED FORMER PRODUCT REQUIREMENT.** The historical manual-first-Import
separation is intentionally superseded by current automatic Sync. Removed post-unlock
zero-token requirement and explicit first Import; no renamed timing-equivalent window
remains. Independently preserve A at a stable boundary before owner/controller/session
construction: exactly one local entry named vault, exact asset bytes and absent
objects-v1. This is a qualification-contract correction, not an automatic-Sync redesign.

## 7. Durable inbound invariants

Persisted grant and designated root; released-Java-authored public fixture; fresh isolated
local root; local VAULT immutable and intended VaultId; exact imported objects and token
identities; retained live coordinator/session/store/owner; complete provider nonmutation;
truthful retry; secret reveal/conceal; explicit lock, shutdown and temporary-root deletion.
No app-private real user vault is authenticated by this scenario. Provider fixture
preparation/restoration preserves the selected root and complete original inventory.

## 8. Assertion/action mapping

| Old assertion/action | New assertion/action | Disposition |
| --- | --- | --- |
| Fresh VAULT-only local setup | Exact sole VAULT entry/bytes, absent objects-v1 before controller construction | DURABLE INVARIANT MOVED: A is independently explicit before observation |
| Post-unlock `isolated_replica_initially_empty`, tokens()==0 | Removed; source explicitly records former manual-Import separation | RETIRED FORMER PRODUCT REQUIREMENT: B, not reinterpreted as A |
| Explicit first importProviderChanges | Removed; unlock-triggered automatic Sync is first import | RETIRED FORMER PRODUCT REQUIREMENT |
| Three-flag waitIdle | Five flags under controller monitor | STRENGTHENED CURRENT-CONTRACT ASSERTION |
| Three-token observation | Same exact count plus issuers/accounts/single alternatives/no conflicts and exact object name/hash map | STRENGTHENED CURRENT-CONTRACT ASSERTION |
| import_status_honest expecting “Changes imported” | automatic_open_sync_verified expecting exact successful unified Sync result | STRENGTHENED CURRENT-CONTRACT ASSERTION: first-import detail is no longer final Sync status |
| stored_binding_present / persisted_read_grant | Preserved; additionally exact designated URI and persisted WRITE needed for successful unified Sync | PRESERVED UNCHANGED plus STRENGTHENED CURRENT-CONTRACT ASSERTION |
| restored_requires_accessibility_check / application_startup_ready / locked_import_disabled | All same checks | PRESERVED UNCHANGED |
| same_session_owner_and_store | Same equality; capture identities before first automatic drain dispatch and compare after completion; compare again after retry | STRENGTHENED CURRENT-CONTRACT ASSERTION |
| provider_vault_not_adopted | Same byte equality; add exact intended sessionVaultId via public coordinator accessor | PRESERVED UNCHANGED plus STRENGTHENED CURRENT-CONTRACT ASSERTION |
| External provider hash comparison | Complete recursive SAF inventory before/after automatic Sync and retry plus external before/after/restoration | STRENGTHENED CURRENT-CONTRACT ASSERTION |
| Show code / isolated_code_revealed | Same action/check | PRESERVED UNCHANGED |
| Later explicit Import / import_conceals_code / exact_retry_no_new_objects | Same action and exact “No new objects.” result; explicitly labeled idempotent conceal/retry | PRESERVED UNCHANGED |
| Lock / isolated_session_closed_for_cleanup | Same action/check; shutdown/delete before reporting PASS and assert temporary root absent | PRESERVED UNCHANGED plus STRENGTHENED CURRENT-CONTRACT ASSERTION |

Raw check count is not an equivalence target. No durable assertion was removed to gain PASS.

## 9. waitIdle and ownership observation

Under `synchronized (isolated)`, require operating, viewQueued, providerActive,
syncPending and syncDrainQueued all false, matching outbound/bootstrap discipline.
The existing 120-second/20ms bounded polling observes completion, not an elapsed
emptiness window. No arbitrary correctness sleep added. Verified result and exact
fixture checks reject drained failures.

The harness Dispatcher observes coordinator/session/store when opened() first posts
its pending automatic drain, before enqueueing that runnable. It forwards the same
runnable to main without cancelling, suppressing or changing admission policy. This
captures pre-Sync ownership, strengthening the old before/after explicit Import
comparison for the new automatic path. No product hook or session implementation-class
method reflection is introduced; intended identity uses coordinator.sessionVaultId().

## 10. Exact Java diff

Only SafInboundRegression.java changes in this milestone; it was unchanged at start.

```diff
diff --git a/tools/device/SafInboundRegression.java b/tools/device/SafInboundRegression.java
index 529a081..7a95f87 100644
--- a/tools/device/SafInboundRegression.java
+++ b/tools/device/SafInboundRegression.java
@@ -10,6 +10,7 @@ import java.util.*;
 public final class SafInboundRegression extends Instrumentation {
     private Object isolated;
     private Path directory;
+    private Object openedCoordinator, openedSession, openedStore;
     private String step = "setup";
     private int checks;
     @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
@@ -27,7 +28,8 @@ public final class SafInboundRegression extends Instrumentation {
     private void waitIdle() throws Exception {
         long end = SystemClock.uptimeMillis() + 120000;
         while (SystemClock.uptimeMillis() < end) {
-            synchronized (isolated) { if (!(Boolean)field(isolated, "operating") && !(Boolean)field(isolated, "viewQueued") && !(Boolean)field(isolated, "providerActive")) return; }
+            synchronized (isolated) { if (!(Boolean)field(isolated, "operating") && !(Boolean)field(isolated, "viewQueued") && !(Boolean)field(isolated, "providerActive")
+                    && !(Boolean)field(isolated, "syncPending") && !(Boolean)field(isolated, "syncDrainQueued")) return; }
             SystemClock.sleep(20);
         }
         throw new AssertionError("Worker timed out");
@@ -46,6 +48,35 @@ public final class SafInboundRegression extends Instrumentation {
         while (tokens() != expected && SystemClock.uptimeMillis() < end) SystemClock.sleep(20);
         check(tokens() == expected, "live_session_observed_" + expected);
     }
+    private Map<String, String> inventory(String uri) throws Exception {
+        var resolver = getTargetContext().getContentResolver();
+        android.net.Uri tree = android.net.Uri.parse(uri);
+        Map<String, String> result = new TreeMap<>();
+        inventoryChildren(resolver, tree, android.provider.DocumentsContract.getTreeDocumentId(tree), "", result);
+        return result;
+    }
+    private void inventoryChildren(android.content.ContentResolver resolver, android.net.Uri tree,
+            String parent, String prefix, Map<String, String> result) throws Exception {
+        try (var cursor = resolver.query(android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent),
+                new String[]{"document_id", "_display_name", "mime_type"}, null, null, null)) {
+            if (cursor == null) throw new AssertionError("Inventory unavailable");
+            while (cursor.moveToNext()) {
+                String id = cursor.getString(0), name = prefix + cursor.getString(1);
+                if ("vnd.android.document/directory".equals(cursor.getString(2))) {
+                    if (result.put(name + "/", "directory") != null) throw new AssertionError("Duplicate inventory");
+                    inventoryChildren(resolver, tree, id, name + "/", result);
+                } else {
+                    byte[] bytes;
+                    try (var input = resolver.openInputStream(android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, id))) {
+                        bytes = input.readNBytes(65537);
+                    }
+                    if (bytes.length > 65536) throw new AssertionError("Inventory bound");
+                    String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
+                    if (result.put(name, hash) != null) throw new AssertionError("Duplicate inventory");
+                }
+            }
+        }
+    }
     @Override public void onStart() {
         Bundle result = new Bundle();
         int outcome = 0;
@@ -60,6 +91,14 @@ public final class SafInboundRegression extends Instrumentation {
             String uri = (String)call(stored, "uri");
             Object grants = call(port, "grants", new Class<?>[]{String.class}, uri);
             check((Boolean)call(grants, "read"), "persisted_read_grant");
+            check(uri.equals("content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FTotipo-M3A-Test"),
+                    "designated_disposable_provider_tree");
+            check((Boolean)call(grants, "write"), "persisted_write_grant_for_automatic_sync");
+            Map<String, String> before = inventory(uri);
+            Map<String, String> expectedObjects = new TreeMap<>(before);
+            expectedObjects.keySet().removeIf(name -> !name.matches("objects-v1/[0-9a-f]{64}"));
+            check(expectedObjects.size() == 3 && before.keySet().stream().filter(n -> n.startsWith("objects-v1/")
+                    && !n.equals("objects-v1/")).count() == 3, "exact_three_object_provider_fixture");
             Object binding = bindingClass.getConstructor(portType).newInstance(port);
             check(call(call(binding, "restore"), "status").toString().equals("CHECKING"), "restored_requires_accessibility_check");
             Object original = call(getTargetContext().getApplicationContext(), "vaultController");
@@ -73,6 +112,14 @@ public final class SafInboundRegression extends Instrumentation {
             byte[] canonicalVault;
             try (var input = getContext().getAssets().open("vault")) { canonicalVault = input.readAllBytes(); }
             Files.write(root.resolve("vault"), canonicalVault);
+            try (var entries = Files.list(root)) {
+                check(entries.map(path -> path.getFileName().toString()).toList().equals(List.of("vault"))
+                        && !Files.exists(root.resolve("objects-v1"))
+                        && Arrays.equals(canonicalVault, Files.readAllBytes(root.resolve("vault"))),
+                        "isolated_replica_empty_before_observation");
+            }
+            check(before.get("vault").equals(java.util.HexFormat.of().formatHex(
+                    java.security.MessageDigest.getInstance("SHA-256").digest(canonicalVault))), "matching_fixture_vault");
             var ownerConstructor = ownerClass.getDeclaredConstructor(Path.class); ownerConstructor.setAccessible(true);
             Object owner = ownerConstructor.newInstance(directory);
             Class<?> controllerClass = loader.loadClass("org.totipo.android.AndroidVaultController");
@@ -83,7 +130,19 @@ public final class SafInboundRegression extends Instrumentation {
             Handler main = new Handler(Looper.getMainLooper());
             Object dispatcher = Proxy.newProxyInstance(loader, new Class<?>[]{dispatcherClass}, (proxy, method, args) -> {
                 switch (method.getName()) {
-                    case "post": main.post((Runnable)args[0]); return null;
+                    case "post":
+                        // Capture ownership when opened() queues its first automatic drain,
+                        // before dispatching it. Observe only; do not suppress or delay Sync.
+                        if (isolated != null) synchronized (isolated) {
+                            if (openedCoordinator == null && (Boolean)field(isolated, "syncPending")) {
+                                openedCoordinator = field(isolated, "vault");
+                                if (openedCoordinator != null) {
+                                    openedSession = field(openedCoordinator, "session");
+                                    openedStore = field(openedCoordinator, "store");
+                                }
+                            }
+                        }
+                        main.post((Runnable)args[0]); return null;
                     case "after": Runnable action = (Runnable)args[1]; main.postDelayed(action, (Long)args[0]); return (Runnable)() -> main.removeCallbacks(action);
                     case "assertDispatchThread": if (Looper.myLooper() != Looper.getMainLooper()) throw new AssertionError("Dispatch thread"); return null;
                     case "assertWorkerThread": if (Looper.myLooper() == Looper.getMainLooper()) throw new AssertionError("Worker thread"); return null;
@@ -98,22 +157,56 @@ public final class SafInboundRegression extends Instrumentation {
             isolated = constructor.newInstance(owner, dispatcher, backendConstructor.newInstance(), time, null, binding);
             waitIdle(); check(!(Boolean)call(isolated, "canImportProviderChanges"), "locked_import_disabled");
             command("unlock", new Class<?>[]{char[].class}, new char[0]); waitIdle();
-            Object coordinator = field(isolated, "vault"), session = field(coordinator, "session"), store = field(coordinator, "store");
-            check(tokens() == 0, "isolated_replica_initially_empty");
-            command("importProviderChanges", new Class<?>[0]); waitIdle(); waitTokens(3);
+            check(call(call(isolated, "snapshot"), "state").toString().equals("OPEN"), "isolated_unlock_open");
+            // RETIRED M3A requirement: remain empty after unlock until manual Import.
+            // Current product first imports through automatic Sync; local emptiness is
+            // independently established above, before controller/session observation.
+            waitTokens(3); waitIdle();
+            Object coordinator = openedCoordinator, session = openedSession, store = openedStore;
+            check(coordinator != null && session != null && store != null, "automatic_sync_open_ownership_captured");
             check(field(isolated, "vault") == coordinator && field(coordinator, "session") == session
                     && field(coordinator, "store") == store && field(isolated, "owner") == owner, "same_session_owner_and_store");
             check(Arrays.equals(canonicalVault, Files.readAllBytes(root.resolve("vault"))), "provider_vault_not_adopted");
-            check(((String)call(call(isolated, "syncView"), "message")).contains("Changes imported"), "import_status_honest");
+            check(call(call(coordinator, "sessionVaultId"), "hex").equals(java.util.HexFormat.of().formatHex(
+                    java.security.MessageDigest.getInstance("SHA-256").digest(canonicalVault))), "matching_session_vault_identity");
+            check(uri.equals(call(isolated, "initialTreeUri")) && uri.equals(call(call(port, "load"), "uri"))
+                    && call(call(call(isolated, "syncView"), "binding"), "status").toString().equals("READY"),
+                    "intended_binding_remains_ready");
+            check("Verified against bound shared folder".equals(field(isolated, "lastSyncResult")), "automatic_open_sync_verified");
+            Map<String, String> imported = new TreeMap<>();
+            try (var files = Files.list(root.resolve("objects-v1"))) {
+                for (Path path : files.toList()) imported.put("objects-v1/" + path.getFileName(), java.util.HexFormat.of().formatHex(
+                        java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
+            }
+            check(imported.equals(expectedObjects), "exact_matching_fixture_objects_imported");
+            Set<String> accounts = new TreeSet<>();
+            for (Object token : (List<?>)call(call(call(isolated, "snapshot"), "view"), "tokens")) {
+                List<?> alternatives = (List<?>)call(token, "alternatives");
+                check(!(Boolean)call(token, "conflict") && alternatives.size() == 1, "matching_fixture_single_alternative");
+                Object descriptor = alternatives.get(0);
+                check("M3A public fixture".equals(call(descriptor, "issuer")), "matching_fixture_issuer");
+                accounts.add((String)call(descriptor, "account"));
+            }
+            check(tokens() == 3 && accounts.equals(Set.of("test 0", "test 1", "test 2")), "exact_matching_fixture_accounts");
+            check(before.equals(inventory(uri)), "automatic_open_sync_zero_provider_mutation");
             Object firstToken = ((List<?>)call(call(call(isolated, "snapshot"), "view"), "tokens")).get(0);
             Object id = call(firstToken, "id");
             command("showCode", new Class<?>[]{loader.loadClass("org.totipo.TokenId")}, id); waitIdle();
             check(call(call(isolated, "snapshot"), "revealedCode") != null, "isolated_code_revealed");
+            // Explicit Import now proves concealment and idempotent no-change retry only.
             command("importProviderChanges", new Class<?>[0]); waitIdle();
             check(call(call(isolated, "snapshot"), "revealedCode") == null, "import_conceals_code");
             check(call(call(isolated, "syncView"), "message").equals("No new objects."), "exact_retry_no_new_objects");
+            check(field(isolated, "vault") == coordinator && field(coordinator, "session") == session
+                    && field(coordinator, "store") == store && field(isolated, "owner") == owner, "retry_same_session_owner_and_store");
+            check(before.equals(inventory(uri)), "explicit_import_retry_zero_provider_mutation");
             command("lock", new Class<?>[0]); waitIdle();
             check(field(isolated, "vault") == null, "isolated_session_closed_for_cleanup");
+            call(isolated, "shutdown"); isolated = null;
+            try (var paths = Files.walk(directory)) {
+                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
+            }
+            check(!Files.exists(directory), "isolated_temporary_vault_deleted");
             result.putString("stream", "M3A PASS checks=" + checks + "\n"); outcome = -1;
         } catch (Throwable failure) {
             result.putString("stream", "M3A FAIL step=" + step + " checks=" + checks + " category=" + failure.getClass().getSimpleName() + "\n");
@@ -121,7 +214,7 @@ public final class SafInboundRegression extends Instrumentation {
             try {
                 if (isolated != null && field(isolated, "vault") != null) { command("lock", new Class<?>[0]); waitIdle(); }
                 if (isolated != null) call(isolated, "shutdown");
-                if (directory != null) try (var paths = Files.walk(directory)) {
+                if (directory != null && Files.exists(directory)) try (var paths = Files.walk(directory)) {
                     for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
                 }
             } catch (Exception ignored) { }
```

## 11. Imported fixture identity assertions

InboundFixture remains unchanged and host-only: released cached core/NIO 0.2.0 and
BC 1.86; fixture authors three public tokens with issuer “M3A public fixture”, accounts
“test 0”, “test 1”, “test 2”. Builder reuses the existing authored fixture/asset rather
than generating a different VAULT. Harness requires exactly three canonical provider
objects, exact local object filenames/SHA-256 ciphertext equal those provider bytes,
exact three tokens/accounts/issuers, no conflicts and one alternative per token.
Prepared provider bytes are additionally compared to the entire host fixture externally.

## 12. Provider nonmutation assertions

Recursive SAF inventory records each directory and file ciphertext SHA-256, rejecting
duplicate paths and oversized fixture files. Compare complete maps before/after
automatic Sync and after retained explicit Import retry. External folder pulls compare
complete file path/hash and directory inventories before/after the scenario and again
after original-provider restoration. Matching first Sync has no local changes to publish;
current production is still permitted to execute its ordinary publication checks.

## 13. Session / VaultId / binding

Before drain capture live coordinator, session and store; after Sync and retry compare
those plus LocalReplicaOwner identity. Exact canonical local VAULT remains byte-identical.
Public coordinator.sessionVaultId().hex() equals canonical VAULT SHA-256. Controller
initialTreeUri and freshly loaded persisted URI equal the exact designated URI, and
binding remains READY. Success requires OPEN and exact verified Sync result.

## 14. LocalStoreQualification out of scope

Not edited, packaged by this inbound builder or executed here. Inherited outbound
matching PASS (47 checks) executed LocalStoreQualification.run and crossed
r19_local_coordinated_store_and_orphan_veto. That is inherited outbound evidence;
no inbound execution claim or rerun.

## 15. Host construction

```sh
GRADLE_USER_HOME="$PWD/.gradle/wave1-gradle-home" python3 -B tools/device/build-saf-inbound-tests.py
```

One invocation PASS. Current consolidated builder explicitly compiles SafInboundRegression
against android.jar; D8 input exactly org/totipo/safqualification/SafInboundRegression.class.
InboundFixture is separately host-compiled, excluded from D8. Packaged DEX definitions
are only the selected inbound family and its legitimate D8 synthetic backport/lambda
helpers. No stale/unrelated authored class. Compared against
.gradle/wave2a/after/m3a-saf/tests-unsigned.apk: identical entry names/order, manifest source,
manifest binary, asset VAULT and all non-DEX payloads; only classes.dex differs.
Package org.totipo.safqualification; target org.totipo.android; min SDK 26 / target 37.
Evidence: construction-validation.json under the ignored milestone directory.

## 16. Mechanical tests

`python3 -B -m unittest discover -s tools/device -p 'test_android_harness.py'`:
one invocation, eight tests PASS. `git diff --check` PASS. No unrelated suites.

## 17. Physical procedure reconstructed before execution

No tracked standalone inbound qualifier exists. The supported historical direct
instrumentation sequence is documented by M3A sections 21–24/33, current inbound
builder/scenario, r19 report section 33 and retained .gradle/m3a-saf/sign-install.py.
It uses the same-signer release/harness installation followed by one normal
`adb shell am instrument -w org.totipo.safqualification/org.totipo.safqualification.SafInboundRegression`.
Current r19 requires exact matching VAULT, so prepare the builder's matching authored
fixture. Preserve the selected root; back up its original vault/objects-v1 and restore
through the existing shared provider_backup used by current SAF qualifiers. No new
qualifier interface, scenario phase, bypass or shared-helper redesign.

Exact ordered procedure retained as physical-procedure.txt before execution:
confirm identity; install signed copies through retained inbound sign-install;
force-stop; prepare/pull/compare fixture; run normal inbound once; independently pull
and compare nonmutation; finally force-stop/restore/uninstall/restore setting/relaunch;
compare entire original folder and retained product APK. Ignored run-physical.py
orchestrates this sequence and physical.log captures it.

## 18. Device / environment identity

Serial 37311JEGR05916, Pixel 6a, Android 17/API 37, fingerprint
`google/bluejay/bluejay:17/CP3A.261005.002.A1/2026100601:user/release-keys`.
Fresh pulled installed product SHA-256
`0f262717e1a8ffa3e7d6925e15f50940366035f6af73826a44ddd605c285be30`.
Signer CN=Disposable Wave 2A Qualification, SHA-256
`d862bbf279d306e7062440d299440d54ecdf594e1cf8ac3046cdf5d3a0ed7f49`.
Unchanged unsigned release SHA-256
`b70501790d32ff383665c8f7c1a599d750036b91a3b1fb0b804f706b8341e012`.
No instrumentation package installed at preflight. Fresh Android permission dump
shows persisted READ/WRITE for the exact Documents/Totipo-M3A-Test URI. Provider
preflight pulled all six files (VAULT plus five objects). Product's private canonical
files are not shell-readable through the non-debuggable app. Human explicitly confirmed
“Confirmed: unlocked, same disposable environment” for the question naming device,
product hash/signer, binding and disposable local/provider data with no real user vault.
Immediate window inspection showed MainActivity and mDreamingLockscreen=false.
Scenario creates its own fresh cache vault rather than opening the installed user root.

## 19. Physical inbound result

Executed the reconstructed normal inbound procedure exactly once. Product and harness
signer/alignment/installation checks passed; existing signed-product verifier passed.
Harness signed APK SHA-256:
`2cd86e9809bdb353b21c47920967571e0163c53288f81e8ae3d4b0816b2f45bc`.
Signer is the same qualification identity in section 18. Unsigned harness SHA-256:
`652abbe52bd57832cbd74b857817209c2666c0d3122793601d0794344430b39d`.

**Result: `M3A FAIL step=isolated_code_revealed checks=27 category=AssertionError`.**
Evidence: physical.log and inbound-device-tests.txt. Instrumentation was not retried.

All 27 preceding checks passed: persisted binding/grants/designated root, exact
three-object provider fixture, restored accessibility/READY, stable pre-observation
VAULT-only isolation, matching VAULT, locked import refusal, successful OPEN, three
observed tokens, pre-drain ownership capture and same coordinator/session/store/owner,
unchanged canonical VAULT, intended VaultId/binding, exact verified automatic Sync,
exact imported object ciphertext map, all three issuer/single-alternative/no-conflict
checks, exact accounts and zero provider mutation.

The existing Show code command was accepted; its immediately subsequent non-null
revealedCode assertion failed. The retained explicit Import/conceal/no-change retry,
after-retry identity/nonmutation, normal lock/closure and explicit temporary-directory
deletion assertions were **NOT REACHED**. Their source coverage is preserved, but there
is no fresh physical PASS for these later assertions. No complete inbound qualification
or successful cleanup assertion count is fabricated.

## 20. Provider restoration and cleanup

PASS for external provider/package/settings/product restoration, independently verified
after the failed scenario. Immediately after instrumentation, the full four-file prepared
provider fixture was byte-identical to before execution. Existing provider_backup restored
the original vault/objects-v1 while preserving root identity and verified file hashes.
Independent preflight/final pulls compare **all six original file paths/hashes and directory
paths exactly**. Both inventories have the sole directory objects-v1.

| Scenario fixture path | SHA-256 before = after |
| --- | --- |
| `objects-v1/41535114e794c54b7add76dda49753b394d60622459b0bad4bd70ab21e8e7b8e` | `4973f3efcf87664a15dc3f2cbf3c1be8bee16375e51036840107db5012daf559` |
| `objects-v1/8f417177a602b3ca3d0d3ec9589355fd24ddd06a9bcabb7bd7cc75be59f17ee1` | `728be5610c81155336b851837d7b6e80ac74db326a8405f903aeb12131fb6780` |
| `objects-v1/911c8f2b51c0041d480014c64fdcd0a071245b8be0af289bbd025611c5b782e3` | `32d6fb5fa84fe277b487e884664145d0585e16d650a8dc6a3af6b290b4867f0b` |
| `vault` | `0226eacfe2598166a5269e7b66ad66ea701a4069c19b0a48e6bc5929e79578c5` |

| Restored original path | SHA-256 before = after |
| --- | --- |
| `objects-v1/36d7395c5d57f999d50cdc69171cd6d4e315bd05a7037d070cac85252c5104fe` | `e04f39f2c61b374773a2669a17b283801e2bb7e3be5584c8311e2929238ced33` |
| `objects-v1/41535114e794c54b7add76dda49753b394d60622459b0bad4bd70ab21e8e7b8e` | `4973f3efcf87664a15dc3f2cbf3c1be8bee16375e51036840107db5012daf559` |
| `objects-v1/8f417177a602b3ca3d0d3ec9589355fd24ddd06a9bcabb7bd7cc75be59f17ee1` | `728be5610c81155336b851837d7b6e80ac74db326a8405f903aeb12131fb6780` |
| `objects-v1/911c8f2b51c0041d480014c64fdcd0a071245b8be0af289bbd025611c5b782e3` | `32d6fb5fa84fe277b487e884664145d0585e16d650a8dc6a3af6b290b4867f0b` |
| `objects-v1/f1ce43301558c8736a73b8fde1f95d8d8e86dbfdd3e0cddb18f1340852a57c9a` | `9ecd821cfc38938214c2360b83340a154d8f408be81186deb4fb3161effd335f` |
| `vault` | `69a4d78bd3ff7e598bea7faf184809c5881d459e36dccbf1ddef161499c3e7b5` |

Instrumentation uninstall returned Success; independent pm list packages and pm path
confirm absence. Stay-awake 0 → 3 → 0, verified. Production force-stop and MainActivity
relaunch completed. Final pulled product APK is byte-identical to starting installed
APK and signed release copy; SHA-256 and signer in section 18 unchanged. Final permission
dump still shows the same persisted READ/WRITE root. Existing disposable product app
and binding/data remain installed; no app-data clear or real-user-vault open occurred.

Java failure-finally retains its lock/shutdown/reverse-order temporary-root deletion
attempt. Because this inherited path suppresses exceptions and the normal explicit
closure/deletion assertions were not reached, private temporary-root removal is **not
independently proven** through the non-debuggable product. Do not claim complete
inbound cleanup qualification. No protected signing material touched.

Ignored evidence: provider-scenario-before/after.json, provider-preflight/final pulls,
fixture-restoration.txt, cleanup-commands.json, restoration-validation.json,
installed-before/final.apk and final-permissions.txt.

## 21. Failures, retries and execution counts

Host construction, mechanical suite and whitespace passed. One ignored construction
validator initially expected only one DEX definition and rejected legitimate
D8-generated synthetic helpers. Corrected validator permits only synthetics in the
selected family and verifies exact authored class selection. No source edit/rebuild
followed that validation-method correction. This is not a physical failure.
**Physical failure classified and mandatory stop honored.** The failed assertion is the
unchanged reveal lifecycle assertion, outside post-unlock/manual-first-Import semantics.
Read-only source diagnosis shows showCode admits asynchronous worker generation with
its own revealing flag; inherited waitIdle does not wait for revealing. Consequently
accepted Show code plus these five idle flags does not establish reveal completion.
This is a source-supported separate completion gap; this run alone does not prove
whether early sampling was the sole cause or establish a product TOTP defect. No Java
fix, assertion weakening, extra sleep or physical retry was made after failure. Any
further reveal diagnosis/correction needs separate authorization under the user's stop
policy. No provider mismatch, local isolation failure or mechanical packaging defect
was observed before the stop.

The ignored orchestration cleanup checker initially treated pm path's exit 1 for an
absent package as a generic adb failure, after uninstall had already returned Success.
Independent read-only verification explicitly accepted exit 1 with empty output and
also checked pm list packages; absence and all restoration results passed. This host
verification-method correction did not modify tracked tooling, install anything or
rerun instrumentation. The original physical failure/traceback is retained.

| Execution category | Count / result |
| --- | --- |
| Baseline / final normal / strict-clean Gradle | 0 / 0 / 0 |
| Focused Gradle test / compile / boundary | 0 / 0 / 0 |
| Inbound host builder | 1 PASS |
| Mechanical suite | 1 invocation, 8 tests PASS |
| Physical procedure / instrumentation | 1 / 1 FAIL after 27 successful checks |
| Physical retry | 0 |
| Signed qualification copies / retained-data installs | 2 / 2 (product and harness) |
| Existing signed-product verifier in sign-install | 1 PASS |
| Final external artifact-verifier set / embedded Gradle verifier | 0 / 0 |
| Provider restoration / harness removal / setting restoration | 1 / 1 / 1 PASS |
| Human Nix / agent Nix | 0 / 0 |
| Stage / commit / reset / stash / push / tag / release operations | 0 |

No full Gradle invocation repeated or invalidated. No executable edits after the
single host build or physical failure. Earlier Wave 2A failures remain in unchanged reports.

## 22. Product / build / release non-change and freeze

Zero diff under app/** (production and JVM tests), release/**, tools/release/**,
.github/**, gradle/**, Gradle build/settings/properties/locks, VERSION/VERSION_CODE,
package.nix/flake.nix/flake.lock/package-deps.json. Earlier dirty sources/reports
compared byte-for-byte against starting hashes. Only inbound Java plus this report
are this milestone's changes. No signing-policy or release artifact source changed;
existing qualification-only key signs ignored copies through historical procedure.
No protected production signing material, new dependency or real user data touched.

Production source stable and unchanged; JVM tests stable and unchanged; build/package
configuration stable and unchanged. Qualification harness is **NOT fully qualified** because the sole physical run failed;
overall Wave 2A host freeze is expressly not performed here.
No Gradle product/test/build pass invalidated. tools/** is selected by actual package.nix;
this harness changes future effective Nix input. review/** and generated .gradle/build
paths are excluded and no inspected flake/check consumer reads this report.
Report-only finalization does not invalidate product/device evidence.

## 23. Nix explicitly deferred

Human Nix 0; agent Nix 0. NOT REQUESTED / NOT RUN. Final Nix belongs to separately
resumed Wave 2A completion, after remaining checks and included-input freeze.

## 24. Layered diff

A. Mechanical Wave 2A: nine Python callers, untracked android_harness.py and
 test_android_harness.py plus initial Wave 2A report. Preserved exactly.
B. Outbound reconciliation: SafOutboundRegression.java and its report. Preserved exactly.
C. Bootstrap: both bootstrap reports, SafBootstrapRegression.java/current-Sync correction,
 plus completion/final attempt reports. Preserved exactly.
D. This milestone: SafInboundRegression.java (+100/-7) and this new excluded report only.
No inherited file is overwritten, and no product/build/release input changes.

## 25. Wave 2A resume instructions

Do not treat this report as inbound PASS or resume physical completion past its failure.
First resolve the separate reveal completion failure under appropriate authorization;
then complete inbound qualification before separately resuming the existing Wave 2A task. Preserve this source/evidence plus outbound/bootstrap work. Remaining ingress
physical qualification, applicable M3D packaging/observer qualification, final affected
host checks/freeze and eventual single human Nix remain separate. Do not infer inbound
coverage of outbound/bootstrap/LocalStoreQualification or full Syncthing convergence.
No ingress, M3D, final Wave 2A host freeze, Nix or Wave 2B performed here.

## 26. Final Git state

main at starting HEAD; versions unchanged. All inherited dirty-file/report hashes,
empty index, exact embedded Java diff and product/build/release non-change verified.
Final git diff --check PASS. Combined tracked diff: twelve files, 321 insertions /
259 deletions. This milestone adds only SafInboundRegression.java (+100/-7) and this
new report; the Java remains uncommitted and the report untracked.

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
```

All work remains unstaged/uncommitted. The narrow milestone is **incomplete** because
its required full inbound physical PASS was not achieved. Work stops at the explicit
out-of-scope failure policy; no ingress/M3D/Nix/Wave 2B continuation.
