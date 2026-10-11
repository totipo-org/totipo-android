# Wave 2A completion attempt — stopped before physical execution

Status: **INCOMPLETE / STOPPED at bootstrap source-inspection prerequisite.**
Classification: **TEST_OR_QUALIFICATION_TOOLING**. No product, release/signing,
dependency, language migration or further consolidation work performed.
This is a completion-attempt record, not evidence that Wave 2A is complete.

## Starting state and relationship to earlier records

Started on main at `d21c15dd4fbfca799ad2b354690e86b118a4aaa0`, VERSION `0.1.2`,
VERSION_CODE `3`. Recorded branch, HEAD, status, diff stat/name-status and versions
before other work. The intentionally dirty tree exactly matched the authorized
ten tracked executable modifications, two untracked support sources and two
untracked historical reports. No unrelated changes were present.

The [stopped Wave 2A report](ANDROID_SUPPORT_TOOLING_WAVE2A_REPORT.md) and
[outbound reconciliation report](ANDROID_SAF_OUTBOUND_AUTOSYNC_RECONCILIATION_REPORT.md)
remain unchanged. The latter supplements the former; historical outbound failures
are not erased. This attempt adds only this excluded report.

## Bootstrap preflight: stop condition found

Inspected the current bootstrap builder, qualifier, SafBootstrapRegression,
LocalStoreQualification and InboundFixture. Bootstrap Java and both supporting
Java sources have zero diff against HEAD. The consolidation changes packaging
and orchestration, not bootstrap Java scenario semantics.

`SafBootstrapRegression.java:158–160` performs successful Join, waits, then asserts
`tokens()==0` as `join_no_automatic_import`. Current production
`AndroidVaultController.java:650` calls `opened()` after successful Join;
`opened()` at lines 448–457 establishes observation and requests automatic Sync.
The pending/drain path at lines 791–810 schedules that Sync. Bootstrap waitIdle
at lines 30–37 checks operating/viewQueued/providerActive but omits
syncPending/syncDrainQueued. Therefore a post-Join zero-token assertion has no
stable causal boundary: automatic Sync may import the three provider fixture
tokens before the assertion. This is the analogous stale scenario assumption
explicitly named in the user's stop rule, discovered statically, not a newly
observed physical failure or a claim of a production defect.

No Java assertion was edited. No attempt was made to reconcile this scenario,
disable automatic Sync, race the assertion or execute until green. The report
does not infer physical outcomes for any bootstrap phase.

The current durable bootstrap assertions include:

- Join: empty isolated local state; wrong password leaves local VAULT/objects
  absent and provider unchanged; successful OPEN; the stale zero-token assertion;
  exact provider/local VAULT hash and VaultId; subsequent three-token observation,
  retained session and unchanged provider inventory.
- Initialize: newly created isolated vault and one authored public token; exact
  provider VAULT and objects directory; exactly two entries before explicit
  publication; repeat initialization unchanged; one published object; immutable
  VAULT and idempotent repeat publication.
- Orphan: existing object evidence without VAULT vetoes initialization, truthfully,
  with zero provider mutation.
- Different vault: initialization, import and publication refuse the other vault,
  with truthful messages and zero provider mutation; canonical local VAULT and
  live session remain unchanged.
- Cleanup: isolated controller lock/shutdown and reverse-order root deletion;
  qualifier backs up/restores designated fixture children with hash comparison,
  force-stops product before restoration, removes instrumentation and relaunches.

LocalStoreQualification is packaged by the bootstrap builder but is **not invoked
by SafBootstrapRegression**; its separate coordinated-store assertions must not be
claimed as executed bootstrap coverage. InboundFixture remains a host-only author
of three public-data tokens; it is not included in instrumentation DEX.

## Physical scope and disposition

No device command, build, sign, install, fixture mutation, setting change or physical
phase ran in this attempt. Current device identity, installed hash/signer, binding
and app state were not freshly inspected. No cleanup was necessary for this attempt;
current device/provider restoration is not newly claimed.

Outbound evidence is inherited, not rerun: persisted permissions PASS; matching
PASS checks=47; different/invalid/missing VAULT each PASS checks=25; exact six-file
provider restoration; instrumentation absent and stay-awake restored to 0. The
reconciliation records the locally signed qualification product retained with
disposable data, hash `0f262717e1a8ffa3e7d6925e15f50940366035f6af73826a44ddd605c285be30`,
certificate `d862bbf279d306e7062440d299440d54ecdf594e1cf8ac3046cdf5d3a0ed7f49`.
These are historical observations, not current-device verification.

Ingress construction/physical qualification, M3D scope determination and physical
packaging smoke, inbound physical-run disposition and final host regressions are
**NOT PERFORMED in this attempt**, because the bootstrap prerequisite required an
immediate stop. Prior construction equivalence remains historical evidence.
No ingress, camera, manual UI, Syncthing convergence, remote transport, tombstone,
end-to-end synchronization or fresh M3D physical PASS is claimed.

## Diff, non-change proof and qualification accounting

Final combined tracked executable diff remains exactly:

| Path under tools/device | Added | Removed |
| --- | ---: | ---: |
| SafOutboundRegression.java | 34 | 5 |
| build-otpauth-ingress-tests.py | 8 | 24 |
| build-saf-bootstrap-tests.py | 13 | 21 |
| build-saf-inbound-tests.py | 13 | 20 |
| build-saf-outbound-tests.py | 13 | 21 |
| check-row-reveal.py | 12 | 23 |
| qualify-saf-bootstrap.py | 27 | 48 |
| qualify-saf-outbound.py | 40 | 52 |
| qualify-security.py | 18 | 20 |
| qualify-syncthing-e2e.py | 7 | 12 |

Tracked total: 185 additions / 246 removals across ten files. Untracked support
sources add android_harness.py (195 physical lines) and test_android_harness.py
(125): combined executable/test net physical-line delta **+259**, file delta **+2**,
no deletions. Three new excluded reports are counted separately. These physical
line counts are not a language-token LOC metric.

Zero diff verified under app/**, tools/release/**, release/**, .github/**, gradle/**,
root Gradle build/settings/properties, package.nix, flake.nix, flake.lock,
package-deps.json, VERSION and VERSION_CODE. All device Java other than the
authorized SafOutboundRegression remain unchanged. No executable edit occurred.
`git diff --check` PASS. Final mechanical tests, M3D oracle, Python parse and
caller/reference sweep were not rerun: their required post-physical boundary was
not reached. No unrelated release suites ran.

| Stage | Evidence and counts |
| --- | --- |
| Original Wave 2A | Lifecycle PASS 51 checks; outbound inspect twice PASS, matching twice FAIL; 24 build-path invocations; mechanical suite 4 × 8 PASS, M3D oracle 3 × 10 PASS; Nix 0. See unchanged stage report for installs and cleanup. |
| Outbound reconciliation | Two host outbound builds plus one physical-qualifier build; mechanical suite 2 × 8 PASS; one physical procedure, five phases PASS; two installs; one restoration/removal; Nix deliberately deferred, 0. |
| Completion attempt | Static preflight only; bootstrap/ingress/M3D/inbound physical phases 0; builders 0; host test suites 0; Gradle baseline/focused/final/strict 0; artifact verifiers 0; human/agent Nix 0; diff check PASS. |

No full gate was repeated or invalidated by this attempt. No protected signing
material, real vault or new dependency was accessed. No staging, commit, reset,
stash, push, tag, release operation or agent Nix occurred.

## Freeze, limitations and final Git state

1. Production source stable: unchanged against starting HEAD.
2. JVM/product tests stable: unchanged app tree.
3. Build/package/dependency configuration stable: zero diff.
4. Qualification tooling/harness: executable bytes preserved, but **NOT qualified
   or finally frozen**; bootstrap semantic prerequisite blocks completion.

package.nix selects tools/**; final human Nix remains required after separately
authorized resolution and all remaining qualification. Its filter excludes review/;
this report is not an effective Android package/check input. Report-only addition
invalidates no existing gate. **Human Nix NOT REQUESTED / NOT RUN.**

Wave 2A remains incomplete. Resolving the bootstrap assumption requires separate
authorization; remaining ingress/M3D/inbound disposition and final host checks then
remain necessary before freeze and the single human `nix flake check path:.`.
Wave 2B remains future work: release.py/source.py context coupling, signing smoke,
source/action-pin consolidation and historical qualification signer provisioning.
No Wave 2B work was started.

Final state: main, original HEAD and versions, index empty, all original dirty
changes retained unstaged/uncommitted, plus this one untracked completion-attempt
report. Suggested eventual commit subject (not ready to commit):
`Consolidate Android physical qualification tooling`.
