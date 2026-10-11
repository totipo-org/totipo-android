# Totipo Android support-tooling Wave 2A final completion

Status: **COMPLETE. Required host/physical qualification PASS; frozen-input human Nix PASS.**
Everything remains unstaged/uncommitted.
Classification: **TEST_OR_QUALIFICATION_TOOLING — final Wave 2A qualification/completion**.
No PRODUCT_CODE, BUILD_DEPENDENCY, protocol, release/signing implementation, language
migration or further consolidation. This milestone makes **zero executable changes**.

## 1. Starting HEAD and accumulated dirty state

Before other work recorded branch, HEAD, status, diff stat/name-status and versions:
`main`, `d21c15dd4fbfca799ad2b354690e86b118a4aaa0`, VERSION `0.1.2`, VERSION_CODE `3`.
The intentionally dirty tree matched exactly: twelve modified tracked files, eight
untracked historical reports and two untracked support sources. No unrelated change.
Explicit user authorization supersedes the ordinary clean-main prerequisite.
Nothing was reset, stashed, reconstructed, staged or committed.

Starting hashes of every dirty/untracked source and historical report are retained in
`.gradle/wave2a-final-completion/starting-hashes.json`. All remain byte-identical.
This report is new; no prior report was overwritten. Temporary orchestration, builds,
logs and device evidence are under ignored `.gradle/wave2a-final-completion/`.

## 2. Complete Wave 2A timeline

1. [Support audit](ANDROID_SUPPORT_TOOLING_AUDIT.md): read-only architecture inventory.
2. [Wave 1](ANDROID_SUPPORT_TOOLING_WAVE1_REPORT.md): superseded lifecycle launcher
   removal, detached lifecycle qualification and human Nix, committed before this HEAD.
3. [Initial Wave 2A](ANDROID_SUPPORT_TOOLING_WAVE2A_REPORT.md): shared mechanics,
   seven-path before/after equivalence and detached lifecycle PASS; stopped on outbound.
4. [Outbound reconciliation](ANDROID_SAF_OUTBOUND_AUTOSYNC_RECONCILIATION_REPORT.md):
   separately authorized current automatic-Sync contract correction; complete physical PASS.
5. [Completion attempt](ANDROID_SUPPORT_TOOLING_WAVE2A_COMPLETION_REPORT.md): static
   stop at bootstrap's historical post-Join emptiness requirement.
6. [Bootstrap intent attempt](ANDROID_SAF_BOOTSTRAP_AUTOSYNC_RECONCILIATION_REPORT.md):
   history established intentional former Join/Import separation; stopped without editing.
7. [Bootstrap current-Sync reconciliation](ANDROID_SAF_BOOTSTRAP_CURRENT_SYNC_RECONCILIATION_REPORT.md):
   separately authorized retirement of that requirement; full physical PASS.
8. [Final attempt](ANDROID_SUPPORT_TOOLING_WAVE2A_FINAL_REPORT.md): ingress host build;
   stopped statically at inbound's former manual-first-Import requirement.
9. [Inbound automatic-Sync reconciliation](ANDROID_SAF_INBOUND_AUTOSYNC_RECONCILIATION_REPORT.md):
   authorized current-contract correction; 27 checks passed, then reveal sampling failed.
10. [Inbound reveal completion](ANDROID_SAF_INBOUND_REVEAL_COMPLETION_REPORT.md):
    authorized reveal-specific completion boundary; full M3A PASS checks=34 and restoration.
11. This milestone: unchanged accumulated implementation; ingress physical qualification,
    narrow M3D observer packaging/execution, final host/construction checks and input freeze.

Earlier failures, stops and superseded requirements remain in their original reports.
Their historical claims are not rewritten as current passes.

## 3. Final executable diff

Only these existing files differ from HEAD (all under tools/device):

| File | Added | Removed |
| --- | ---: | ---: |
| SafOutboundRegression.java | 34 | 5 |
| SafBootstrapRegression.java | 36 | 6 |
| SafInboundRegression.java | 121 | 8 |
| build-otpauth-ingress-tests.py | 8 | 24 |
| build-saf-bootstrap-tests.py | 13 | 21 |
| build-saf-inbound-tests.py | 13 | 20 |
| build-saf-outbound-tests.py | 13 | 21 |
| check-row-reveal.py | 12 | 23 |
| qualify-saf-bootstrap.py | 27 | 48 |
| qualify-saf-outbound.py | 40 | 52 |
| qualify-security.py | 18 | 20 |
| qualify-syncthing-e2e.py | 7 | 12 |

Tracked total: **342 additions / 260 removals**, twelve modified files. Untracked
android_harness.py and test_android_harness.py add 320 physical lines. The three SAF
Java diffs are inherited authorized reconciliations, frozen throughout this milestone.
No other device Java differs from HEAD; no executable edit occurred now.

## 4. Shared helper architecture

android_harness.py is an internal standard-library module. AndroidToolchain discovers
caller-supplied pinned JDK/SDK tools. compile_java uses fresh temporary output, removes
stale members of explicit class families and returns only selected authored/nested
classes. Independently compiled host fixtures are never implicitly dexed.
Other functions perform D8, caller-manifest linking, explicit ZIP assembly/copy,
duplicate-input rejection, detached test signing/alignment/certificate validation,
and caller-designated provider backup/restoration with whole-file hash comparison.
Tool failures redact potentially sensitive subprocess output.

Callers retain dependency coordinates, fixture authorship, manifests, packages,
scenario selection, product installation and physical evidence policy. There is no
release key, repository-root global, package policy or protocol implementation in the
shared module. No redesign or further consolidation was performed here.

## 5. Retained package and isolation boundaries

Ingress targets org.totipo.android using an isolated cache controller/vault; its
separate public-label observer self-targets org.totipo.ingresstest. SAF targets the
real product/provider grants with isolated local roots. Host InboundFixture is
excluded from every instrumentation DEX. Row is self-targeted with copied debug DEX.
Security is self-targeted, uses copied release DEX/resources and a detached test key.
M3D is production-targeted observation only, using the normal controller and exclusive
store ownership; it authors no tokens and writes no protocol files.

## 6. Outbound final evidence — inherited

No rerun or invalidation. Persisted permissions PASS; matching **PASS checks=47**;
different, invalid and missing VAULT each **blocked PASS checks=25**; exact complete
provider restoration, package removal, setting restoration and product retention PASS.
The matching path retains exact fixture/ciphertext identity, automatic open/Add Sync,
publication readback/postflight, unchanged old inventory, explicit retry, session and
canonical VAULT invariants. See the outbound reconciliation report for actual logs.

## 7. Bootstrap final evidence — inherited

No rerun or invalidation. Persisted permissions PASS; Join/current automatic Sync
**PASS checks=28**; Initialize **PASS checks=17**; Orphan **PASS checks=12**;
Different **PASS checks=16**; restoration/removal/relaunch PASS. Former intentional
Join/Import separation was expressly retired in its separately authorized milestone.
Initialize, orphan and foreign-vault safety semantics remain intact.

## 8. Inbound final evidence — inherited

Complete fresh **M3A PASS checks=34** after the current helper consolidation and both
authorized semantic reconciliations. Exact current automatic-Sync import state,
actual reveal, explicit retry conceal/no-change, same session/store/owner/provider
nonmutation, Lock/session closure and explicit temporary-root deletion all passed.
Complete provider/device restoration and package removal passed. The earlier 27-check
failure remains historical evidence; it is not presented as a complete inbound pass.

## 9. LocalStoreQualification inherited execution

SafOutboundRegression actually invokes `LocalStoreQualification.run(directory)` before
`r19_local_coordinated_store_and_orphan_veto`. The inherited 47-check matching PASS
crossed that assertion. This is inherited physical execution of coordinated-store and
orphan-veto checks, not merely class packaging. No additional invocation is needed.
Bootstrap packages the class but does not invoke it; inbound neither packages nor
executes it. No bootstrap/inbound execution claim is made.

## 10. Ingress responsibilities and construction

Inspected current builder, OtpAuthIngressRegression and OtpAuthUiObserver before
execution. Retained responsibilities: hostile/malformed VIEW/SEND/envelope/ClipData
handling; accepted review metadata; clean retained Intent; onNewIntent; no authorship
before Add; Cancel/Back/rejected-Add secret wiping; marker-only recreation and expiry;
locked/no-vault discard and unsupported HOTP rejection; isolated controller/cache/vault
ownership and restoration; one concealed Add and lock/unlock persistence. Observer
skips password fields and reads only fixed public-fixture/product label matches.
No scenario was added, removed or changed.

`python3 -B tools/device/build-otpauth-ingress-tests.py` PASS in the supplied pinned
Nix-managed JDK 17, SDK platform 37.0, Build Tools 36.0.0 environment. Fresh binary
manifest inspection confirms org.totipo.ingresstest; regression targets org.totipo.android;
observer targets its own test package; min SDK 26 / target SDK 37. DEX definitions
contain exactly OtpAuthIngressRegression and OtpAuthUiObserver authored families plus
their legitimate D8 synthetics. No stale/unrelated authored classes. ZIP contains
only AndroidManifest.xml, resources.arsc and classes.dex. Manifest source/binary,
entries and every payload byte equal retained `.gradle/wave2a/after/m2d-ingress/`.
Intended unsigned output remains `.gradle/m2d-ingress/tests-unsigned.apk`.

## 11. Ingress physical procedure and result

Reconstructed from current source, README and historical M2D evidence. There is no
standalone ingress qualifier interface: build, same-signer test installation, direct
regression instrumentation, and separately self-targeted UI observation are distinct.
Historical camera scan/manual Add evidence is separate from packaging qualification;
no camera or human vault authentication is required for the isolated regression.

Fresh device: Pixel 6a, serial `37311JEGR05916`, API 37, fingerprint
`google/bluejay/bluejay:17/CP3A.261005.002.A1/2026100601:user/release-keys`.
Installed product SHA-256 `0f262717e1a8ffa3e7d6925e15f50940366035f6af73826a44ddd605c285be30`;
CN=Disposable Wave 2A Qualification, certificate SHA-256
`d862bbf279d306e7062440d299440d54ecdf594e1cf8ac3046cdf5d3a0ed7f49`.
Both ingress/M3D helper packages were absent. Human explicitly confirmed
“Confirmed: disposable fixtures only”, then “Device unlocked; Totipo vault remains locked”.
No real vault was authenticated. Regression constructs a fresh cache root using public
fixture/empty disposable password and restores the original application controller.

Unsigned ingress SHA-256 `1246c66fb1bc94194d6ac1440288f476d2a6767d25384e528eb5eff41b36a222`;
physically installed signed test SHA-256
`e154d6689f8a01d12bd9a803c1749c3f32e505c0f2dddb84850ff134c7ba356e`.
Signer matched installed product; alignment verified. Product was not reinstalled.

Executed once:
`adb shell am instrument -w org.totipo.ingresstest/org.totipo.ingresstest.OtpAuthIngressRegression`.
**M2D_TESTS PASS count=49**, all normal automated checks PASS. Then explicit public
VIEW ingress while product remained locked, followed by
`adb shell am instrument -w org.totipo.ingresstest/org.totipo.ingresstest.OtpAuthUiObserver`.
Actual result: `totipo=true confirmation=false public_metadata=false public_token=false
added=false revealed=false locked=false unlock_retry=true expired=false show=false`.
This honestly exercises the observer and independently observes locked discard.

Evidence: ingress-build.log, ingress-construction.json, ingress-artifacts.json,
ingress-signer.txt, ingress-instrumentation.txt, ingress-ui-observer.txt,
ingress-commands.json and ingress-physical.log in the milestone evidence directory.
The helper was removed; product force-stopped/relaunched; stay-awake restored 0→3→0.
No camera, manual Add/Cancel, visual fidelity or fresh human reveal evidence is claimed.

## 12. M3D changed boundary and scope decision

The only helper-dependent M3D function is `build()`: AndroidToolchain.discover,
compile_java, dex_classes, link_manifest and append_entries. Its `build` CLI command
selects this function. The import also loads when oracle/other commands start, but
sign/install/serve/capture/begin/finish/restart/validate mechanics and semantics remain
unchanged. Syncthing transport, host assertions, fail latch and human checkpoint
model were not intentionally changed. Java and oracle have zero diff.

Existing `build`, `sign --observer-only`, `serve` and `capture` support crossing the
changed packaging boundary without running the full convergence checkpoint sequence.
Used ordinary adb to install only the verified same-signer observer. The existing
runner `install` updates both release and observer; it was unnecessary for this narrow
scope, and no replacement interface/bypass flag was added. Product bytes were retained.
Scope is **M3D observer packaging/execution**, not full M3D qualification.

## 13. M3D build and physical result

Normal build PASS; all three observer APK payloads equal retained Wave 2A evidence.
Normal `sign --observer-only` PASS with existing disposable qualification identity;
its historical M3C signer check and alignment verification remain in force. Artifact
hashes: unsigned `0c76021ee04ce6c428571824e663414c86769aa2bfb346f29f269c6975d0bf10`; signed `f04bfee7b57492886d052ecf7ae6ee6a27cea28f75b899920856ffc283bb6a7a`
(m3d-artifacts.json). Package org.totipo.syncthingqualification;
registration targets org.totipo.android; min SDK 26 / target 37. Exact authored
family SyncthingE2eRegression plus nested receiver and legitimate D8 synthetics.

Human selected existing `totipo-test-sync/Totipo-M3D-Test` through ordinary folder
management and confirmed “M3D child selected; vault remains locked”. The actual normal
`serve --folder Totipo-M3D-Test` instrumentation started once and emitted ready=true.
Two normal `capture` commands, labels packaging-before and packaging-after, produced
valid failure-free envelopes from the same observer PID, both provider-stable.
The observer checked designated folder/grant identity, complete readable provider
inventory and normal closed-state local ownership. No unlock/Join/Initialize/Sync ran.

Used the existing unchanged `validate(before, after, args)` host API, the same API used
by test-syncthing-e2e.py, against the two real capture envelopes: writable, layout objects,
state LOCKED, vault-unchanged and provider unchanged. **Failures=[]**. This is a host
assertion result on actual app/provider observations, separate from human UI and
transport evidence. No `finish` scenario or synthetic human transport report was created.
Ten oracle tests PASS at this physical boundary, then PASS again in the required final suite.

Normal STOP broadcast ended instrumentation; serve exited 0. Observer uninstall PASS;
stay-awake restored to 0; product relaunched. Ready/captures/validation/serve/sign/install
logs are retained under `.gradle/wave2a-final-completion/` and its m3d child.

## 14. Explicit M3D nonclaims

No full M3D PASS, Syncthing convergence, remote propagation, tombstone propagation,
complete end-to-end synchronization, desktop result, live transfer window, OPEN
session continuity or human transport status is claimed. A ready/capture envelope is
not convergence. The small host oracle pass does not supply missing transport or
human UI evidence. Historical full-checkpoint evidence remains separately scoped.

## 15. Inbound no-rerun disposition

**Separate additional inbound physical run NOT REQUIRED.** Inbound already completed
full fresh M3A PASS checks=34 after current helper consolidation and semantic
reconciliations. No new shared-helper or Java edit occurred here. Final construction
revalidation does not replace or invalidate that inherited physical evidence.
Outbound/bootstrap/security paths likewise were not replayed for confidence.

## 16. Final seven-builder construction sweep

After required physical PASS, real pinned compilation, D8/link/ZIP and applicable
signing ran for all seven paths. Ignored orchestration stopped row/security at their
first adb command after packaging, rather than running unrelated physical suites.
No device action was simulated as a physical PASS.

| Builder/output under .gradle | Package → target | Result |
| --- | --- | --- |
| m2d-ingress/tests-unsigned.apk | org.totipo.ingresstest → product; observer → itself | PASS, 3 entries |
| m3a-saf/tests-unsigned.apk | org.totipo.safqualification → product | PASS, 4 entries |
| r19-saf/tests-unsigned.apk | org.totipo.safqualification → product | PASS, 4 entries |
| m3c-saf/tests-unsigned.apk | org.totipo.safqualification → product | PASS, 4 entries |
| row-reveal-device/unsigned.apk, tests.apk | org.totipo.rowrevealqualification → itself | PASS, 10 unsigned entries |
| security-qualification/unsigned.apk, tests.apk | org.totipo.securityqualification → itself | PASS, 19 unsigned entries |
| wave2a-final-completion/m3d-final-build/observer-unsigned.apk | org.totipo.syncthingqualification → product | PASS, 3 entries |

For each: exact D8 class-file input list equals retained construction evidence;
packaged authored class definitions equal that list, with only family-owned legitimate
synthetics; manifest source/binary and ZIP entry names/order match. Every payload
matches retained Wave 2A except classes.dex for the three reconciled SAF Java harnesses.
Their assets/vault remain exact. Host InboundFixture is excluded from D8.

Row copies selected debug DEX; security copies selected release DEX/resources, retaining
USE_BIOMETRIC, Application/Activity/theme, no-backup/testOnly/non-debuggable manifest
boundaries and min SDK 30 (all others 26); target SDK 37. Detached certificates equal
retained signed outputs and signing/alignment checks pass. No stale/unrelated authored
class or unexpected payload. Current ingress/M3D final payloads equal those physically
executed; ZIP timestamp differences do not change payload qualification.
Evidence: construction-sweep.log, seven-builder-construction.json and per-builder
actual command captures. The M3D evidence-dir option preserves physical artifacts.

## 17. Final affected host results

`python3 -B -m unittest discover -s tools/device -p 'test_android_harness.py'`:
**8 tests PASS**. `python3 -B tools/device/test-syncthing-e2e.py`: **10 tests PASS**.
Missing tool/input, stale-family, duplicate ZIP, redacted failure, signer refusal and
restoration-after-failure contracts remain exercised. No oracle change or unrelated
release Python suite. Device-only tools are not Gradle product/test consumers, so
no baseline/final/strict Gradle rebuild or external product artifact set was required.
No earlier product Gradle/artifact checkpoint was invalidated.

## 18. Python, caller and whitespace results

All **12** current tools/device Python sources compile using Python's compile(...,
'exec') without writing bytecode, covering every affected caller/helper/test.
Repository-wide rg sweep and AST import review confirm all nine intended callers
select android_harness. No obsolete lifecycle Python pair or accidental duplicate
compiler/D8/link selection remains. Caller-local SAF/M3D qualification signing and
M3D evidence mechanics are intentional retained boundaries. Row/security ZIP reads
select source entries; shared helpers perform copying/signing. All generated
__pycache__ removed (one found). `git diff --check` PASS. Current/historical references
are retained separately in caller-reference-sweep.txt, repository-wide-references.txt
and mechanical-reference-sweep.txt.

## 19. Product/build/release non-change

Zero diff under app/** (including JVM/product tests), tools/release/**, release/**,
.github/**, gradle/**, root build.gradle.kts/settings.gradle.kts/gradle.properties,
package.nix/flake.nix/flake.lock/package-deps.json, VERSION and VERSION_CODE.
Only the three authorized SAF Java files differ from HEAD. Starting dirty sources
and all eight historical reports remain byte-identical. Existing unsigned product
release remains `b70501790d32ff383665c8f7c1a599d750036b91a3b1fb0b804f706b8341e012`.
No protected production signing material, new dependency, protocol edit or real user
data was required. Only disposable test APK copies were signed now.

## 20. Device/provider cleanup and restoration

Fresh independent before/after pulls prove exact equality of every file path/SHA-256
and directory inventory: Documents/Totipo-M3A-Test **6 files**, M3D-Test **14 files**.
Neither provider root was moved, rewritten or deleted. Existing fixtures retained.
Human confirmed “Original M3A folder restored; vault locked”. Fresh permission dump
shows original M3A persisted READ/WRITE (0x3); private stored binding URI is not
shell-inspected through the non-debuggable product. Human picker confirmation is
explicitly distinguished from that grant evidence.

All five packages absent by fresh pm path inspection: ingresstest,
syncthingqualification, safqualification, rowrevealqualification and
securityqualification (org.totipo prefix). Installed product pulled after cleanup
is byte-identical to starting product; signer reverified. Stay-awake exactly 0.
Product retained/relaunched with disposable data; no app-data reset. Ingress regression
performed its inherited lock/controller restoration/reverse-order cache-root deletion;
private ingress deletion is not independently shell-proven. Inbound's explicit deletion
assertion remains inherited evidence. cleanup-validation.json records final checks.

## 21. Complete line/file/entry-point accounting

Physical lines, including blank/comment lines; not language-token LOC:

| Original Python caller | Before | After | Net |
| --- | ---: | ---: | ---: |
| build-otpauth-ingress-tests.py | 46 | 30 | -16 |
| build-saf-bootstrap-tests.py | 61 | 53 | -8 |
| build-saf-inbound-tests.py | 60 | 53 | -7 |
| build-saf-outbound-tests.py | 61 | 53 | -8 |
| check-row-reveal.py | 72 | 61 | -11 |
| qualify-saf-bootstrap.py | 99 | 78 | -21 |
| qualify-saf-outbound.py | 105 | 93 | -12 |
| qualify-security.py | 101 | 99 | -2 |
| qualify-syncthing-e2e.py | 285 | 280 | -5 |
| Total existing callers | 890 | 800 | -90 |

android_harness.py **195** lines; test_android_harness.py **125**. Java:
outbound 264→293 (+29), bootstrap 220→250 (+30), inbound 131→244 (+113).
Combined executable/test net delta **+402 physical lines**, executable file delta
**+2**, no deletions. This consolidation increased LOC; no pruning/shrink claim.
Nine accumulated excluded reports including this one add nine files separately;
whole-tree net file delta is **+11** relative to HEAD.

Human operational CLI entry paths remain **9→9**. Counting M3D's eight subcommands
separately gives **16→16** command families; security's existing flag/phase choices
remain unchanged. Shared module adds no public operational command; the new host test
runner is test infrastructure. No new qualifier interface was introduced.

## 22. Qualification accounting across substages

| Stage | Builds | Mechanical suites | Oracle suites | Physical instrumentation / installs |
| --- | ---: | ---: | ---: | --- |
| Initial Wave 2A | 24 | 4×8 PASS | 3×10 PASS | 5 phases: lifecycle PASS 51, inspect twice PASS, matching twice FAIL; 6 installs |
| Outbound reconciliation | 3 | 2×8 PASS | 0 | 5 phases PASS; 2 installs |
| First completion attempt | 0 | 0 | 0 | static stop; 0 |
| Failed bootstrap intent attempt | 0 | 0 | 0 | static stop; 0 |
| Bootstrap current-Sync reconciliation | 2 | 1×8 PASS | 0 | 5 phases PASS; 2 installs |
| Earlier final attempt | 1 ingress | 0 | 0 | static inbound stop; 0 |
| Inbound automatic-Sync reconciliation | 1 | 1×8 PASS | 0 | 1 FAIL after 27 checks; 2 installs |
| Inbound reveal-completion reconciliation | 1 | 1×8 PASS | 0 | 1 PASS 34 checks; 2 installs |
| This qualification completion | 10 | 1×8 PASS | 2×10 PASS | ingress regression + UI observer + M3D serve, all PASS; 2 helper installs; 2 normal M3D captures |

Totals across Wave 2A: **42 build-path invocations**, **10 mechanical suite invocations
(80 test passes)**, **5 oracle suite invocations (50 test passes)**, **20 instrumentation
phase/run invocations**, **16 installs**. Earlier failures and their separately authorized
corrections remain explained in original reports. No outbound/bootstrap/inbound/lifecycle
physical rerun occurred now. This milestone's ten builds comprise two ingress preflight
builds (second retained a durable build log), one initial M3D build and seven final
construction paths. The duplicate preflight build changed no input and invalidated no pass.
The two oracle invocations separately serve physical-boundary and final-host requirements.

Across these substages baseline/full-normal/strict-clean/focused Gradle **0**;
external final artifact-verifier sets and embedded Gradle verifier executions **0**.
Existing signed-product verifier in historical qualification procedures: **6 PASS**
(initial 2, outbound 1, bootstrap 1, inbound 1, reveal completion 1); none now.
No repeated full Gradle gate or newly invalidated pass. Human Nix: **1 final invocation,
human-reported PASS**; earlier Wave 2A substages requested/ran none. Agent Nix,
staging, commits, reset/stash, push/tag/release and workflow dispatch all **0**.

## 23. Final input freeze inventory

All four freeze statements established after host/physical/cleanup PASS:

1. **Production source stable:** zero app diff; installed product exact before/after bytes.
2. **JVM/product tests stable:** zero app test/source diff.
3. **Build/package/dependency configuration stable:** all protected build/evaluator/cache
   paths unchanged; versions remain 0.1.2/3.
4. **Qualification tooling/harnesses stable:** no executable edit in this milestone;
   all accumulated hashes preserved, required physical/host/construction checks PASS.

External canonical inventory: `/tmp/totipo-wave2a-final-inputs.jsonl`, **166 files**,
including **36 tools/** files. UTF-8 JSONL, one record per selected regular file,
sorted by relative path; each record uses sorted keys, compact separators and LF,
with path, permission mode and content SHA-256. Covers actual package.nix source
selection plus package.nix, flake.nix, flake.lock and package-deps.json evaluator/cache
inputs. No symlinks or .git/.gradle/build/.direnv path components. Canonical inventory
SHA-256: **2129233b05f2906bae7ecefc333422f7439485ef77a8e7f580038b328066b439**.
Inventory is outside tracked source; summary retained as freeze.json in ignored evidence.

Actual source filtering selects app/src, gradle, tools and exact root/app build/lock/
version/license/branding files. flake release-app snapshots also select tools/release.
review/**, README and AGENTS are excluded and unconsumed by inspected package/check
and release-app checks. Report-only finalization after Nix invalidates no effective
check input. Any included executable/source/evaluator edit invalidates this freeze.

## 24. Human Nix result

**HUMAN-REPORTED PASS.** After the single request for `nix flake check path:.`
against the frozen dirty tree, the human replied: “nix flake check passed”.
No Nix log was supplied or inspected; this result is explicitly human-reported.
Human Nix invocation count: **1**. Agent Nix: **0**. No nix build/rebuild requested.

Post-result read-only verification confirmed the 166 frozen input contents/modes
and all starting dirty-file/historical-report hashes unchanged, with original
main/HEAD and empty index. Only this excluded report was finalized afterward;
no included-input edit or qualification invalidation occurred. Wave 2A is complete.

## 25. Remaining limitations

Evidence is scoped to the connected Pixel 6a and
existing disposable qualification signer/cache/provider fixtures. No universal
Android/runtime/camera/provider compatibility or protected production signer provenance.
M3D coverage is packaging/observer execution only; full transport checkpoints remain
separate. No fresh row matrix or biometric first/restart qualification. Ingress private
cleanup and final stored URI are bounded by the independent-access limits stated above.
Remote CI was not triggered. No implementation defect or semantic mismatch arose now.

## 26. Wave 2B candidates

Separate future scope: explicit release.py/source.py context/authority coupling,
reviewed pin/action policy consolidation, signing smoke/source-shape tests, trusted
signing-tool resolution and historical qualification signer provisioning. Preserve
protected key lifetime, artifact/payload identity and human M3D evidence boundaries.
No Wave 2B implementation started.

## 27. Final Git state

main at original HEAD; VERSION 0.1.2 / VERSION_CODE 3; index empty. Twelve accumulated
tracked modifications and two support sources retained; eight historical reports
unchanged; only this additional excluded final report added now. All unstaged and
uncommitted. Final preservation/scope/whitespace checks PASS. Included inputs frozen;
no post-freeze or post-Nix executable edit. Final human Nix PASS is human-reported;
this excluded report is the sole post-Nix edit. Wave 2A qualification is complete.

## 28. Suggested commit subject

`Consolidate Android physical qualification tooling`

All required Wave 2A qualification gates are complete. No commit was created.
