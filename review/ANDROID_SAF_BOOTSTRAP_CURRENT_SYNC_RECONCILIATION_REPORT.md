# SAF bootstrap current-Sync qualification-contract reconciliation

Status: **COMPLETE for this narrow milestone.** Host construction, mechanical tests,
full normal physical bootstrap and independent restoration checks PASS. Wave 2A
completion remains separate; Nix deferred.

## 1. Starting HEAD and required dirty tree

Recorded branch, HEAD, status, diff stat/name-status and versions before editing:
main, `d21c15dd4fbfca799ad2b354690e86b118a4aaa0`, VERSION 0.1.2, VERSION_CODE 3.
Exactly ten tracked executable modifications (185 additions / 246 removals), two
untracked support sources and four untracked accumulated reports matched the user's
allowlist. No unrelated modification existed. The explicit dirty-tree instruction
supersedes AGENTS' ordinary clean-main prerequisite. No reset, stash, stage or commit.

Starting diff and SHA-256 inventory are retained in ignored
`.gradle/bootstrap-current-sync/starting-diff.patch` and `preserved-files.json`.
All preexisting dirty sources and reports were compared after editing and remained
byte-identical. Read AGENTS, README, package/flake/CI and all seven requested reports;
inspected the full bootstrap Java, builder, qualifier, LocalStoreQualification and
InboundFixture, relevant controller paths, daily-driver tests/documentation, and
reviewed Java guidance at `f0a028676c1801a10b2d8d2650bf9c24357d1fa1` retained by the
prior read-only inspection. No runtime dependency or guidance pin changed.

## 2. Classification

**TEST_OR_QUALIFICATION_TOOLING — qualification-contract update for superseded UX
behavior.** No PRODUCT_CODE, BUILD_DEPENDENCY, protocol, release/signing policy,
bootstrap feature or automatic-Sync redesign. The only Java changed by this milestone
is `tools/device/SafBootstrapRegression.java`.

## 3. Historical M3C contract

[M3C evidence](M3C_IMMUTABLE_VAULT_BOOTSTRAP_REPORT.md), sections 21 and 30,
intentionally required successful Join to open an empty session and Import to remain
a separate user action. `join_no_automatic_import` tested that former requirement;
it was not a misplaced emptiness check. The earlier
[bootstrap intent report](ANDROID_SAF_BOOTSTRAP_AUTOSYNC_RECONCILIATION_REPORT.md)
correctly stopped without changing it. All historical evidence remains untouched.

## 4. Current daily-driver authority

README and [daily-driver report](ANDROID_DAILY_DRIVER_UX_REPORT.md), sections 7–12,
define ordinary automatic Sync after successful Join/open. `beginJoin` calls
`opened()`, which establishes observation and calls `requestAutomaticSync()`.
`syncPending`/`syncDrainQueued` defer/coalesce admission; `startSync` imports and waits
for Java observation before publication. `finishOutbound` records verified completion.
`requestRefresh()` returning is not observation completion or acknowledgement.

Inspected dailyUnlockAndForegroundAutoSyncAreCoalescedAndLockedForegroundDoesNothing,
dailyAddCommitsOfflineDoesNotLoopAndManualSyncRetries, blocking observation and
import-before-publication controller tests. The older
joinWrongPasswordThenExactEnrollmentThenManualImportOnly test deliberately suppresses
automatic requests in its test dispatcher. It does not define current production UX.
No controller tests were changed or rerun for this standalone harness update.

## 5. Explicit decision before editing

**RETIRED PRODUCT REQUIREMENT:** successful Join opens empty and Import remains a
separate user action. This was explicitly recorded before editing. It is superseded
because the current product deliberately triggers ordinary automatic Sync on Join/open.
No assertion retains a renamed equivalent or timing-based post-Join emptiness window.
The source comment names the retired M3C assertion and contract explicitly.

## 6. Durable invariants

A: the isolated local replica is empty before controller/session observation.
B: wrong authentication creates no local VAULT/objects and mutates no provider bytes.
C/G: exact intended canonical VAULT, VaultId and live coordinator/session identity.
D/E: successful Join performs automatic Sync and imports the exact matching fixture.
F: import leaves the complete provider inventory/hashes unchanged.
H: Initialize behavior and all existing assertions unchanged.
I: orphan object evidence vetoes initialization without mutation or deletion.
J: foreign-vault initialization/import/publication refuse without rebinding.
K: qualifier backup, restoration, removal and cleanup remain unchanged.

## 7. Complete changed Join assertion/action mapping

| Old assertion/action | New current-contract assertion/action | Disposition |
| --- | --- | --- |
| Fresh isolated root; controller-discovered `empty_local` | Retain `empty_local`; add direct empty directory and absent VAULT/objects-v1 assertions before owner/controller construction | MOVED DURABLE SAFETY INVARIANT: pre-observation emptiness is made explicit at a stable boundary; the historical post-Join requirement has a separate disposition below |
| Three wrong-password filesystem/provider checks | All retained verbatim; additionally assert NO_LOCAL_VAULT and null coordinator | Durable authentication safety preserved and strengthened; no accidental session success |
| Successful Join followed by three-flag waitIdle and `joined_open` | Same Join and OPEN check; waitIdle now includes both Sync flags | Durable successful open plus actual queued/active Sync drain |
| `tokens()==0`, `join_no_automatic_import` | Remove assertion; document retirement and require verified automatic Sync plus exact imported fixture | RETIRED PRODUCT REQUIREMENT: manual-Import separation, intentionally superseded |
| `exact_local_provider_vault_hash` | Retained verbatim | Durable exact provider/local VAULT identity |
| `joined_vault_id` reflecting through session implementation | Same exact comparison via public coordinator `sessionVaultId()` | Durable identity equality preserved; follows corrected outbound's public accessor without implementation-class access |
| Local VAULT inventory logging | Retained verbatim | Exact public identity evidence |
| Explicit first `importProviderChanges`; waitIdle; waitTokens(3) | Remove explicit first Import; waitTokens(3), waitIdle and `join_automatic_sync_verified` | Current automatic Sync is the first import; completion additionally requires `lastSyncResult` verified against bound folder |
| Only three-token observation after explicit Import | Exact three local canonical object names/SHA-256 ciphertext map equal provider fixture; exact issuer, accounts test 0/1/2, one alternative each and no conflicts | Durable matching-state invariant strengthened beyond count-only acceptance |
| `join_import_same_session` | `join_automatic_sync_same_session`, also requiring unchanged controller coordinator reference | Same live coordinator/session across post-drain checks; no claim of an independently instrumented callback trace |
| `join_import_provider_unchanged` | `join_automatic_sync_provider_unchanged`, identical complete inventory/hash equality | Durable zero provider mutation under current first-import path |

No manual Import retry is retained in Join. No explicit Import is used to manufacture
first-import success. Other scenarios retain their existing internal operations.

## 8. waitIdle

Checks operating, viewQueued, providerActive, syncPending and syncDrainQueued together
under `synchronized (isolated)`, matching corrected outbound's synchronization discipline.
The existing 120-second bound and 20ms polling observe asynchronous completion; no new
sleep or elapsed empty interval is a correctness condition. `providerActive` spans the
unified import/observation/publication operation. Exact fixture and verified-result
assertions additionally reject drained failure or token-count-only success.

## 9. Exact Java diff

The following is the complete milestone Java diff against unchanged starting Java.

```diff
diff --git a/tools/device/SafBootstrapRegression.java b/tools/device/SafBootstrapRegression.java
index 649e6cc..d178bcf 100644
--- a/tools/device/SafBootstrapRegression.java
+++ b/tools/device/SafBootstrapRegression.java
@@ -32,7 +32,8 @@ public final class SafBootstrapRegression extends Instrumentation {
     private void waitIdle() throws Exception {
         long end = SystemClock.uptimeMillis() + 120000;
         while (SystemClock.uptimeMillis() < end) {
-            synchronized (isolated) { if (!(Boolean)field(isolated, "operating") && !(Boolean)field(isolated, "viewQueued") && !(Boolean)field(isolated, "providerActive")) return; }
+            synchronized (isolated) { if (!(Boolean)field(isolated, "operating") && !(Boolean)field(isolated, "viewQueued") && !(Boolean)field(isolated, "providerActive")
+                    && !(Boolean)field(isolated, "syncPending") && !(Boolean)field(isolated, "syncDrainQueued")) return; }
             SystemClock.sleep(20);
         }
         throw new AssertionError("Worker timed out");
@@ -124,6 +125,14 @@ public final class SafBootstrapRegression extends Instrumentation {
             Class<?> ownerClass = loader.loadClass("org.totipo.android.LocalReplicaOwner");
             directory = Files.createTempDirectory(getTargetContext().getCacheDir().toPath(), "m3a-isolated-");
             Path root = directory.resolve("totipo-vault"); Files.createDirectories(root);
+            if (mode.equals("join")) {
+                // Durable empty-replica safety is checked before controller/session observation.
+                try (var entries = Files.list(root)) {
+                    check(entries.findAny().isEmpty(), "join_local_empty_before_observation");
+                }
+                check(!Files.exists(root.resolve("vault")) && !Files.exists(root.resolve("objects-v1")),
+                        "join_local_vault_and_objects_absent_before_observation");
+            }
             var ownerConstructor = ownerClass.getDeclaredConstructor(Path.class); ownerConstructor.setAccessible(true);
             Object owner = ownerConstructor.newInstance(directory);
             Class<?> controllerClass = loader.loadClass("org.totipo.android.AndroidVaultController");
@@ -155,18 +164,39 @@ public final class SafBootstrapRegression extends Instrumentation {
                 check(!Files.exists(root.resolve("vault")),"wrong_password_local_vault_absent");
                 check(!Files.exists(root.resolve("objects-v1")),"wrong_password_local_objects_unchanged");
                 check(before.equals(inventory(uri)),"wrong_password_provider_unchanged");
+                check(call(call(isolated,"snapshot"),"state").toString().equals("NO_LOCAL_VAULT")
+                        && field(isolated,"vault")==null,"wrong_password_no_session");
                 command("joinExistingVault",new Class<?>[]{char[].class},new char[0]); waitIdle();
                 check(call(call(isolated,"snapshot"),"state").toString().equals("OPEN"),"joined_open");
-                check(tokens()==0,"join_no_automatic_import");
+                // RETIRED M3C product requirement: join_no_automatic_import deliberately
+                // required Join/Import separation. Current Join/open triggers ordinary Sync.
+                // Never require even transient post-Join emptiness or an explicit first Import.
                 Object coordinator=field(isolated,"vault"),session=field(coordinator,"session");
                 byte[] local=Files.readAllBytes(root.resolve("vault"));
                 String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(local));
                 check(hash.equals(before.get("vault")),"exact_local_provider_vault_hash");
-                check(call(session,"vaultId").equals(loader.loadClass("org.totipo.Totipo").getMethod("vaultId",byte[].class).invoke(null,local)),"joined_vault_id");
+                check(call(coordinator,"sessionVaultId").equals(loader.loadClass("org.totipo.Totipo").getMethod("vaultId",byte[].class).invoke(null,local)),"joined_vault_id");
                 recordInventory("local",Map.of("vault",hash));
-                command("importProviderChanges",new Class<?>[0]);waitIdle();waitTokens(3);
-                check(field(coordinator,"session")==session,"join_import_same_session");
-                check(before.equals(inventory(uri)),"join_import_provider_unchanged");
+                waitTokens(3); waitIdle();
+                check("Verified against bound shared folder".equals(field(isolated,"lastSyncResult")),"join_automatic_sync_verified");
+                Map<String,String> imported=new TreeMap<>(),expectedImported=new TreeMap<>(before);
+                expectedImported.keySet().removeIf(name -> !name.matches("objects-v1/[0-9a-f]{64}"));
+                try(var files=Files.list(root.resolve("objects-v1"))) {
+                    for(Path path:files.toList()) imported.put("objects-v1/"+path.getFileName(),java.util.HexFormat.of().formatHex(
+                            java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
+                }
+                check(expectedImported.size()==3 && imported.equals(expectedImported),"join_exact_provider_objects_imported");
+                Set<String> accounts=new TreeSet<>();
+                for(Object token:(List<?>)call(call(call(isolated,"snapshot"),"view"),"tokens")) {
+                    List<?> alternatives=(List<?>)call(token,"alternatives");
+                    check(!(Boolean)call(token,"conflict") && alternatives.size()==1,"join_fixture_single_alternative");
+                    Object descriptor=alternatives.get(0);
+                    check("M3A public fixture".equals(call(descriptor,"issuer")),"join_fixture_issuer");
+                    accounts.add((String)call(descriptor,"account"));
+                }
+                check(tokens()==3 && accounts.equals(Set.of("test 0","test 1","test 2")),"join_exact_fixture_accounts");
+                check(field(isolated,"vault")==coordinator && field(coordinator,"session")==session,"join_automatic_sync_same_session");
+                check(before.equals(inventory(uri)),"join_automatic_sync_provider_unchanged");
             } else {
                 command("create",new Class<?>[]{char[].class},new char[0]);waitIdle();
                 check(call(call(isolated,"snapshot"),"state").toString().equals("OPEN"),"local_created_open");
```

## 10. Wrong-password coverage

Existing checks remain verbatim: absent local VAULT, absent objects-v1 and complete
unchanged provider inventory/hash map. Added explicit NO_LOCAL_VAULT/null coordinator
assertion. Failed authentication cannot be mistaken for Join/session success.

## 11. Successful Join coverage

The normal Join command triggers the product's automatic Sync. No first manual Import
is issued. OPEN, exact VAULT hash, intended VaultId, verified Sync completion, exactly
three expected issuer/accounts with one alternative each, exact provider-derived local
object names/ciphertext hashes, same coordinator/session and unchanged complete provider
inventory are required. Filesystem emptiness precedes controller construction, with
no post-Join empty-state assertion. Fixture VAULT SHA-256:
`6a52198a5f740d5e7febc2497c0eea623ef6cdda42d01dad05dd53b2e2b5bd83`.

## 12. Initialize unchanged

The entire non-Join branch is byte-identical. Retains isolated create/OPEN, one authored
token, exact VAULT/directory, two provider entries before publication, second Initialize
no mutation, one explicitly published object, immutable VAULT, idempotent second
publication, canonical local VAULT and same live session. Shared waitIdle drains
automatic work but no Initialize assertion/action was changed.

## 13. Orphan unchanged

Object evidence without VAULT must veto Initialize, with truthful “will not initialize”
message and zero provider mutation. No deletion or garbage interpretation is permitted.
The original assertion/action code is unchanged.

## 14. Different-vault unchanged

Initialize, explicit internal Import and explicit internal Publish each refuse the
foreign vault; exact truthful message and zero provider mutation remain required.
Canonical local VAULT and same live session are retained. No automatic-Sync bypass or
silent rebinding is introduced; the original assertion/action code is unchanged.

## 15. LocalStoreQualification nonclaim

The bootstrap builder packages LocalStoreQualification but SafBootstrapRegression
does not invoke it. Bootstrap physical success does not establish its coordinated-store
regressions. No invocation was added and no independently run local-store PASS is claimed.
InboundFixture remains host-only and unchanged.

## 16. Host construction validation

Used the existing pinned Nix-managed JDK 17, platform 37.0 and Build Tools 36.0.0,
with the already qualified Wave 1 cache; no new dependency or download.

```sh
GRADLE_USER_HOME="$PWD/.gradle/wave1-gradle-home" python3 -B tools/device/build-saf-bootstrap-tests.py
```

PASS. Explicit instrumentation sources and D8 class-file inputs remain exactly
SafBootstrapRegression.class and LocalStoreQualification.class. InboundFixture is
separately host compiled and excluded from D8. Inspected shared explicit-family
selection; parsed packaged DEX class definitions to confirm the two authored classes
plus only their D8-generated synthetic backport/lambda classes, with no host fixture.

Compared against `.gradle/wave2a/after/m3c-saf/tests-unsigned.apk`: identical ZIP entry
names/order, manifest binary, resources and asset VAULT; **only classes.dex differs**.
Manifest source matches the starting copy. Package org.totipo.safqualification;
instrumentation target org.totipo.android; min SDK 26 and target SDK 37 unchanged.
Evidence `.gradle/bootstrap-current-sync/construction-validation.json`, starting
manifest/APK copies and `.gradle/m3c-saf` outputs.

## 17. Mechanical tests and whitespace

```sh
python3 -B -m unittest discover -s tools/device -p 'test_android_harness.py'
git diff --check
```

Eight mechanical tests PASS; whitespace PASS. No row, security, ingress, M3D,
release or unrelated suites ran. No Gradle product compilation/assembly was required:
this standalone device Java is not a Gradle product/test input.

## 18. Fresh physical precondition

Serial `37311JEGR05916`, Pixel 6a, Android 17/API 37; fingerprint:
`google/bluejay/bluejay:17/CP3A.261005.002.A1/2026100601:user/release-keys`.
Installed app was freshly pulled before mutation. SHA-256:
`0f262717e1a8ffa3e7d6925e15f50940366035f6af73826a44ddd605c285be30`.
Signer DN `CN=Disposable Wave 2A Qualification`; SHA-256:
`d862bbf279d306e7062440d299440d54ecdf594e1cf8ac3046cdf5d3a0ed7f49`.
This is a locally signed qualification app, not the protected production distribution.
Its entire ZIP payload equals unchanged unsigned release SHA-256:
`b70501790d32ff383665c8f7c1a599d750036b91a3b1fb0b804f706b8341e012`.

Fresh persisted READ/WRITE grant evidence identified
`content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FTotipo-M3A-Test`.
The whole disposable provider root `/sdcard/Documents/Totipo-M3A-Test` was pulled before
mutation (six files: VAULT plus five objects). Baseline VAULT hash:
`69a4d78bd3ff7e598bea7faf184809c5881d459e36dccbf1ddef161499c3e7b5`.
UI after unlock showed Totipo's local vault locked/password-unlock screen; no app vault
unlock was performed. Private canonical files are not shell-readable in this
non-debuggable app, so exact current binding/local-data intent was also human confirmed.
The normal instrumentation later independently checks stored binding and exact selected
root. No physical scenario ran before that confirmation.

Human explicitly confirmed “Unlocked; same disposable qualification environment” for
this device/app/provider/local state. After it returned to keyguard during host work,
the human confirmed “Unlocked now”; immediate window inspection showed MainActivity
focused and mDreamingLockscreen=false. USB stay-awake was temporarily set 0 → 3 for
the run, with finally restoration. No protected signing material was used.

## 19. Full physical procedure

Ran the supported normal no-argument qualifier once, with no bypass flags:

```sh
GRADLE_USER_HOME="$PWD/.gradle/wave1-gradle-home" python3 -B tools/device/qualify-saf-bootstrap.py
```

An ignored host wrapper captures output and restores stay-awake in finally. It changes
no qualifier interface or phase. Evidence `.gradle/bootstrap-current-sync/physical.log`
and normal `.gradle/m3c-saf/*-device-tests.txt`/permission-inspection outputs.

| Normal phase | Result |
| --- | --- |
| Persisted READ + WRITE permission inspection | PASS |
| Join: wrong password and successful current automatic-Sync path | `M3C join PASS checks=28` |
| Initialize, unchanged semantics/assertions | `M3C initialize PASS checks=17` |
| Orphan refusal, unchanged | `M3C orphan PASS checks=12` |
| Different-vault Initialize/Import/Publish refusal, unchanged | `M3C different PASS checks=16` |
| Qualifier restoration/removal/relaunch | PASS; procedure exit 0 |

Every Join assertion in section 11 passed, including the local empty precondition
before observation. No race-sensitive post-Join empty-state claim remains.
Initialize's original `initialize_no_automatic_publish` assertion passed unchanged;
no separate contract correction was needed. Orphan and Different-vault refusal
messages, zero mutation, canonical VAULT and same live session passed unchanged.

## 20. Provider restoration and cleanup

The normal qualifier force-stopped product before restoring the original fixture,
removed org.totipo.safqualification and relaunched MainActivity. Shared restoration
hash verification passed. Independently pulled the **entire** provider root before
and after: all six file paths/SHA-256 values and directory paths match exactly.
Root identity was retained. Evidence: `provider-before/`, `provider-after/`,
`provider-before-inventory.json`, `fixture-restoration.txt`, and
`restoration-validation.json` under `.gradle/bootstrap-current-sync/`.

Independent pm path confirmed instrumentation absent. Stay-awake returned from 3 to
its exact original value 0. Pulled final installed app equals both starting installed
bytes and the qualifier's retained signed release copy byte-for-byte. SHA-256 and
signer remain exactly those in section 18. Existing app data/binding were retained;
no user vault was opened or modified by these isolated scenarios. Java's inherited
lock/shutdown/reverse-order deletion cleanup is unchanged; private root deletion is
not separately shell-inspected through the non-debuggable app. The procedure does not
claim LocalStoreQualification execution or protected production-signing provenance.

## 21. Failures, retries and execution counts

No compiler, mechanical test, physical scenario or restoration failure occurred.
No physical retry, recursive scenario edits or post-physical executable change.
One ignored host construction validator initially expected only authored DEX class
definitions and omitted legitimate D8 synthetic backport/lambda classes. It failed;
corrected validation explicitly permits synthetics belonging only to the two selected
families and verifies exact authored class inclusion. This validation-method correction
changed no source or package and required no rebuild. Prior milestone failures remain
in their unchanged reports; they are not erased or counted as failures of this run.

| Execution category | This milestone |
| --- | ---: |
| Baseline / final normal / strict-clean full Gradle | 0 / 0 / 0 |
| Focused Gradle test / compile / boundary | 0 / 0 / 0 |
| Bootstrap host builder | 1 PASS |
| Builder within normal physical qualifier | 1 PASS |
| Mechanical host suite | 1 invocation, 8 tests PASS |
| Normal physical qualifier | 1 complete run, exit 0 |
| Instrumentation phases | 5, all PASS: inspection plus four scenarios |
| Physical installs | 2: retained-data product update and instrumentation |
| Provider restoration / harness removal | 1 / 1 PASS |
| Existing signed-product verifier within qualifier | 1 PASS |
| Final external artifact-verifier set / embedded Gradle verifiers | 0 / 0 |
| Human Nix / agent Nix | 0 / 0 |
| Stage / commit / push / tag / release operations | 0 |

No full Gradle gate repeated or invalidated: no Gradle-consumed source/test/build
input changed. Standard qualification-only signing of two ignored copies is the
unchanged supported procedure, not release/signing implementation work. No protected
signing material, new dependency, shared-helper redesign or real user vault was needed.

## 22. Product/build/release non-change and input disposition

Zero Git diff under app/** (production and JVM tests), tools/release/**, release/**,
.github/**, gradle/**, root Gradle build/settings/properties, versions, package.nix,
flake.nix, flake.lock and package-deps.json. Unchanged unsigned release hash and exact
before/after installed APK equality substantiate product-artifact non-change. All
preexisting dirty-file/report hashes match the starting inventory. All other Java,
including prior outbound reconciliation, remains byte-identical to milestone start.
The entire bootstrap non-Join branch is unchanged; only shared waitIdle affects how
it waits for completion. No product behavior or protocol changed.

- Production source stable: unchanged, with same qualified unsigned release.
- JVM tests stable: unchanged app test trees.
- Build/package configuration stable: unchanged Gradle/dependency/evaluator/cache inputs.
- This bootstrap harness stable: host and full physical procedure PASS; no executable
  edits after qualification. Overall Wave 2A tooling is not finally frozen here.

Actual package.nix selects tools/**; this device harness is a future Nix input but
is not consumed by Gradle. review/** and generated .gradle/build paths are excluded;
flake checks/release-app wrappers do not otherwise consume this report. Report-only
finalization therefore does not invalidate qualified APK/device evidence. The included
Java change requires the eventual combined Wave 2A Nix check, with no existing current
combined Nix PASS claimed.

## 23. Nix explicitly deferred

No Nix was requested or run. This user-authorized narrow milestone ends at bootstrap
physical qualification. No ingress, M3D, final Wave 2A host freeze or Wave 2B work
proceeded. Final human `nix flake check path:.` remains a later Wave 2A completion gate.

## 24. Layered diff

A. Mechanical Wave 2A: nine modified Python callers plus untracked android_harness.py,
test_android_harness.py and Wave 2A report. Preserved exactly.

B. Outbound reconciliation: SafOutboundRegression.java and its report. Preserved exactly.
The excluded Wave 2A completion-attempt report is likewise preserved.

C. Failed bootstrap intent attempt: ANDROID_SAF_BOOTSTRAP_AUTOSYNC_RECONCILIATION_REPORT.md
only; it deliberately made no Java changes. Preserved exactly as stopped evidence.

D. This authorized current-contract update: SafBootstrapRegression.java (+36 / -6)
and this new report only. It intentionally retires the former separation requirement,
strengthens current-contract qualification, and does not masquerade as mechanical work.
Combined tracked diff: eleven files, 221 additions / 252 removals. Untracked reports
and support sources are additional and excluded from those tracked statistics.

## 25. Resume instructions for Wave 2A

Preserve this qualified bootstrap source and prior outbound reconciliation. Resume
Wave 2A completion separately: address remaining ingress, applicable M3D packaging
smoke/evidence and inbound/local-store disposition, then final affected host checks.
Do not infer LocalStoreQualification coverage from bootstrap packaging. Honor each
remaining scenario's safety and stop conditions rather than rewriting semantics under
mechanical scope. Freeze every included tooling/evaluator input before the eventual
single final human Nix. No unrelated row/biometric matrix or Wave 2B is authorized here.

## 26. Final Git state

main at `d21c15dd4fbfca799ad2b354690e86b118a4aaa0`; VERSION 0.1.2 / code 3.
Index empty; all accumulated work and this update remain unstaged/uncommitted.
All earlier reports and executable changes preserved exactly. Final whitespace,
report-link, exact embedded Java diff, unchanged non-Join branch, preservation hashes,
allowlist and product/build/release scope checks PASS. No source edits followed the
physical PASS. The narrow bootstrap reconciliation is complete; Wave 2A remains open.

```text
 M tools/device/SafBootstrapRegression.java
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
?? review/ANDROID_SAF_OUTBOUND_AUTOSYNC_RECONCILIATION_REPORT.md
?? review/ANDROID_SUPPORT_TOOLING_WAVE2A_COMPLETION_REPORT.md
?? review/ANDROID_SUPPORT_TOOLING_WAVE2A_REPORT.md
?? tools/device/android_harness.py
?? tools/device/test_android_harness.py
```
