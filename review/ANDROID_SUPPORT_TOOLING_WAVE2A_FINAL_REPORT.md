# Wave 2A final qualification attempt — stopped at inbound semantics

Status: **INCOMPLETE / STOPPED. Wave 2A is not complete.**
Classification: **TEST_OR_QUALIFICATION_TOOLING — final Wave 2A qualification/completion**.
No further consolidation, Java changes, language migration, product, release/signing
or build/dependency changes were performed in this attempt.

## 1. Starting state

Recorded branch, HEAD, status, diff stat/name-status and versions before other work:
main, `d21c15dd4fbfca799ad2b354690e86b118a4aaa0`, VERSION 0.1.2, VERSION_CODE 3.
The required dirty tree matched exactly: eleven modified executable files, two
untracked support sources and five untracked historical reports. No unrelated edit.
Tracked diff: 221 additions / 252 removals. Explicit user authorization supersedes
the ordinary clean-main prerequisite. Nothing was reset, stashed, staged or committed.

## 2. History and timeline

1. [Audit](ANDROID_SUPPORT_TOOLING_AUDIT.md): read-only architecture recommendations.
2. [Wave 1](ANDROID_SUPPORT_TOOLING_WAVE1_REPORT.md): superseded lifecycle launcher
   removal, detached lifecycle and human Nix PASS; committed before this starting HEAD.
3. [Initial Wave 2A](ANDROID_SUPPORT_TOOLING_WAVE2A_REPORT.md): mechanical sharing,
   construction equivalence and lifecycle PASS; stopped on outbound assumptions.
4. [Outbound reconciliation](ANDROID_SAF_OUTBOUND_AUTOSYNC_RECONCILIATION_REPORT.md):
   separately authorized current-Sync Java correction and complete physical PASS.
5. [First completion attempt](ANDROID_SUPPORT_TOOLING_WAVE2A_COMPLETION_REPORT.md):
   stopped statically at bootstrap post-Join emptiness.
6. [Bootstrap intent attempt](ANDROID_SAF_BOOTSTRAP_AUTOSYNC_RECONCILIATION_REPORT.md):
   stopped because history established intentional former Join/Import separation.
7. [Bootstrap current-Sync reconciliation](ANDROID_SAF_BOOTSTRAP_CURRENT_SYNC_RECONCILIATION_REPORT.md):
   explicitly retired that former requirement; host and full physical PASS.
8. This attempt: current source/evidence inspection, ingress host build PASS, then
   mandatory stop on inbound's stale post-unlock emptiness assumption. No physical run.

All earlier reports remain unchanged; failures and superseded assumptions are retained.

## 3. Final executable diff

This attempt changes no executable. Accumulated modifications remain the nine Python
callers listed below and exactly SafOutboundRegression.java / SafBootstrapRegression.java.
New support sources remain android_harness.py and test_android_harness.py.

| Existing caller under tools/device | Added | Removed |
| --- | ---: | ---: |
| build-otpauth-ingress-tests.py | 8 | 24 |
| build-saf-bootstrap-tests.py | 13 | 21 |
| build-saf-inbound-tests.py | 13 | 20 |
| build-saf-outbound-tests.py | 13 | 21 |
| check-row-reveal.py | 12 | 23 |
| qualify-saf-bootstrap.py | 27 | 48 |
| qualify-saf-outbound.py | 40 | 52 |
| qualify-security.py | 18 | 20 |
| qualify-syncthing-e2e.py | 7 | 12 |

## 4. Shared-layer architecture

The existing internal standard-library module owns pinned tool discovery, explicit
fresh class-family compilation, D8, manifest linking, explicit ZIP entry assembly,
detached test signing/certificate checks and designated-provider backup/restoration.
Callers retain manifests, dependencies, fixtures, scenario commands and identities.
No redesign or additional consolidation occurred.

## 5. Isolation boundaries

Ingress targets production with an isolated cache controller/vault; its separate
observer self-targets. SAF uses isolated local roots and real persisted provider
grants. Row/security remain detached packages. M3D remains observation-only with
human transport evidence separate from app/provider facts. These are inspected
responsibilities, not new physical PASS claims.

## 6. Outbound inherited evidence

Not rerun or invalidated: permissions PASS; matching PASS checks=47; different,
invalid and missing VAULT each PASS checks=25; exact whole-provider restoration,
instrumentation removal and retained product identity. See the unchanged report.

## 7. Bootstrap inherited evidence

Not rerun or invalidated: permissions PASS; Join/current automatic-Sync PASS
checks=28; Initialize PASS checks=17; Orphan PASS checks=12; Different PASS checks=16;
restoration/removal/relaunch PASS. No packaging input changed in this attempt.

## 8. Ingress construction

Ran `python3 -B tools/device/build-otpauth-ingress-tests.py` once in the supplied
pinned environment: exit 0. Output `.gradle/m2d-ingress/tests-unsigned.apk`.
Current source explicitly compiles OtpAuthIngressRegression and OtpAuthUiObserver,
registers regression against org.totipo.android and observer against
org.totipo.ingresstest, package org.totipo.ingresstest, min SDK 26 / target 37.
Historical old/new equivalence remains in `.gradle/wave2a/{before,after}`.
Fresh binary-manifest, DEX-membership and retained-output differential validation
was not completed before the mandatory stop; build success alone is insufficient.

## 9. Ingress physical procedure and result

Current regression covers hostile/malformed Intent and ClipData shapes, accepted
VIEW/SEND review metadata, clean retained Intents, onNewIntent, cancel/back wiping,
marker-only recreation, rejected Add wiping, locked/no-vault discard, single concealed
authorship and persistence. It substitutes an isolated application controller and
restores it, then locks/deletes its temporary vault. The separate UI observer emits
only fixed public-fixture/product label booleans and skips password fields.
Historical M2D describes same-signer standalone installation and distinct human
camera/UI observations. No camera or manual visual evidence was exercised here.

Physical qualification **NOT RUN**. No test artifact was signed or installed; no
instrumentation phases ran. Thus no fresh physical artifact SHA/signer/PASS is claimed.

## 10. M3D scope decision

Current supported build/sign/serve/capture commands can cross the observer packaging
boundary without claiming complete convergence. The existing observer-only sign
option, designated-folder guard, ready envelope and capture protocol are retained.
The install command additionally updates the qualification product; it requires
matching installed signer and retained release-signed output. A supported safe
physical sequence and current binding were not established before stopping.
No new smoke command or evidence bypass was added.

## 11. M3D physical result

NOT RUN: no M3D build, signing, installation, serve, capture or host validation of
fresh capture evidence in this attempt. Packaging/observer smoke remains unqualified.

## 12. M3D nonclaims

No packaging/observer smoke PASS, full M3D PASS, Syncthing convergence, remote
propagation, tombstone propagation or complete end-to-end Sync is claimed.
Human UI, human transport, host and observer evidence remain distinct.

## 13. Inbound disposition — mandatory stop

Current SafInboundRegression.java constructs its isolated controller with the
persisted binding (lines 96–98), unlocks and waits (100), then requires zero tokens
as `isolated_replica_initially_empty` (102), before explicit first import (103).
Its waitIdle (30–37) omits syncPending and syncDrainQueued.

Current AndroidVaultController.opened() (448–457) requests automatic Sync.
requestAutomaticSync/queueSyncDrain (793–810) schedule import/observation/publication
when a binding exists. README expressly defines automatic Sync after unlock/open.
Therefore post-unlock emptiness has no stable current-contract boundary; matching
provider objects may already be imported. This is a statically demonstrated stale
scenario assumption, not a newly observed physical failure or a product defect.

The user's sections 5 and 25 require stopping when inbound inspection exposes stale
Java semantics. The conditional permission to omit a distinct inbound run does not
override that explicit stop. No NOT REQUIRED disposition is asserted to conceal it.
Both inbound files are preserved. No Java repair or physical execution occurred.
Separate authorization must resolve the inbound qualification contract/disposition
before this completion can resume.

## 14. LocalStoreQualification disposition

Actual caller SafOutboundRegression.java:134 invokes LocalStoreQualification.run
before `r19_local_coordinated_store_and_orphan_veto` at 135. The inherited successful
matching run (47 checks) executed past this assertion. This establishes inherited
physical execution, not mere packaging. No rerun is needed for unchanged inputs.
Bootstrap packages the class but does not invoke it; no bootstrap execution claim.

## 15. Host results

Ingress builder PASS; `git diff --check` PASS. Final mechanical suite, M3D oracle,
all-Python parse/compile and repository-wide final caller sweep were NOT RUN:
the required physical-before-final-host boundary was not reached. Initial Wave 2A
host/construction results remain historical, not fresh final qualification.
No unrelated release suite, row matrix or security phase ran.

## 16. Product/release/build non-change

Verified zero diff under app/**, tools/release/**, release/**, .github/**, gradle/**,
build.gradle.kts, settings.gradle.kts, gradle.properties, package.nix, flake.nix,
flake.lock, package-deps.json, VERSION and VERSION_CODE. Only the two authorized
Java files differ from HEAD. No Gradle-consumed input changed; no Gradle rebuild
or strict/artifact gate was invalidated by this attempt.

## 17. Device/provider cleanup

Read-only adb discovery found serial 37311JEGR05916, Pixel_6a / bluejay.
Human confirmed: “Unlocked; same disposable environment; M3D folder exists.”
No current API/fingerprint, installed APK/signer or exact M3D binding was freshly
verified. Prior device identities remain historical. No install, launch, provider
mutation, app-data reset or setting change occurred here; no new device cleanup
was needed. Prior cleanup results are inherited, not independently reverified.

## 18. Combined physical-line/file accounting

Nine existing callers: 890 → 800 lines (-90). Shared module +195; new host tests
+125. Java reconciliation: outbound +34/-5 (net +29), bootstrap +36/-6 (net +30).
Combined executable/test delta **+289 physical lines**, **+2 files**, no deletions.
Human-facing operational entry points remain nine before/after; the discovery-only
test module and internal helper add no operational command. Six new excluded
reports including this one are separate from executable counts. No LOC reduction
is claimed. Ownership consolidation, not a line-count target, is the purpose.

## 19. Qualification accounting

| Stage | Recorded runs/results |
| --- | --- |
| Initial Wave 2A | 24 build paths; mechanical suite 4×8 PASS; oracle 3×10 PASS; lifecycle PASS 51; outbound inspect ×2 PASS and matching ×2 FAIL; 6 installs; Nix 0 |
| Outbound reconciliation | 2 host builds +1 qualifier build; mechanical suite 2×8 PASS; 1 physical procedure /5 phases PASS; 2 installs; restoration/removal PASS; Nix 0 |
| First completion attempt | Static stop; builds/suites/physical/Nix 0 |
| Failed bootstrap intent attempt | Static stop; read-only device discovery; builds/suites/physical/Nix 0 |
| Bootstrap current-Sync reconciliation | 1 host +1 qualifier build; mechanical suite 1×8 PASS; 1 physical procedure /5 phases PASS; 2 installs; restoration/removal PASS; Nix 0 |
| This final attempt | Ingress build 1 PASS; read-only adb discovery 1; physical/install/sign/suites/Nix 0; whitespace PASS |

Across these stages: Gradle baseline/final normal/strict/focused 0; external final
artifact-verifier sets and embedded Gradle verifier executions 0. Existing signed
product verifier inside SAF qualifiers: initial 2 + outbound 1 + bootstrap 1 PASS.
No repeated full gates or new invalidations in this attempt. Earlier extra builds,
failed preconditions and validation-method corrections remain explained in stage
reports. Agent Nix and stage/commit/push/tag/release operations remain 0.

## 20. Freeze evidence

Production source stable: unchanged. JVM/product tests stable: unchanged.
Build/package/dependency inputs stable: unchanged. Qualification tooling bytes
preserved, but **NOT finally qualified/frozen**, because inbound semantics blocks
completion and ingress/M3D physical/final host prerequisites remain outstanding.
No final checksum freeze inventory or readiness declaration is claimed.

Inspected package.nix: tools/** is included; .git/.gradle/build/.direnv components
and symlinks are excluded. Evaluator/cache inputs remain unchanged. review/** is
excluded and not consumed by the inspected flake checks. This report-only addition
invalidates no existing product or eventual effective-input qualification result.

## 21. Human Nix

**NOT REQUESTED / NOT RUN.** No combined Wave 2A Nix PASS exists. Request exactly
`nix flake check path:.` only after the stop is resolved, all remaining required
checks pass and included inputs freeze. Agent does not run Nix.

## 22. Remaining limitations

Inbound contract/disposition requires separate resolution. Fresh ingress construction
validation and physical qualification, M3D packaging/observer qualification, final
host regressions, checksum inventory/freeze and the single human Nix remain required.
Neither a completed Wave 2A nor a commit-ready qualified tree is claimed.

## 23. Wave 2B candidates

Deferred: release.py/source.py authority/context coupling, signing smoke/source-shape
tests, trusted signing-tool resolution and historical qualification signer provisioning.
No Wave 2B implementation is authorized or performed here.

## 24. Final Git state

main at starting HEAD; versions unchanged; index empty. All accumulated executable
changes and five historical reports remain unstaged/uncommitted. This attempt adds
only this untracked excluded report. Earlier reports are not rewritten.

## 25. Suggested eventual commit subject

`Consolidate Android physical qualification tooling`

Not ready to commit: mandatory qualification prerequisites remain unresolved.
