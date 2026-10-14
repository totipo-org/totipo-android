# Android Phase B: release tooling simplification

2026-10-14. Classification: **RELEASE_TOOLING / PACKAGING_RELEASE /
TEST_OR_QUALIFICATION_TOOLING**. Bounded release implementation, preserving local
allocation/tag automation and the four-job workflow. No Phase C work.
Implementation remains uncommitted. Human Nix gate is **PASS, human-reported**.

## 1. Starting source and inspection

Branch main, full HEAD `055a771f61a2dd1ce564e330d68ac8ca165ae4f2`;
initial `git status --short` empty. Phase A is committed at `e206b48`, followed by
package fix `055a771`. VERSION `0.1.2` and VERSION_CODE `3` remain unchanged.
No Gradle wrapper/bootstrap acquisition files remain tracked. Read AGENTS in full,
the simplification audit and Phase A report, current README/release/signing docs,
all release Python tests/controller/helpers, both workflows, flake/package/build
configuration, and supply-chain guard. Historical reports were not edited.
No sibling repository, other worktree or sub-agent was used. No Java application
operation changes or Java guidance update is involved.

Baseline release discovery: **58 tests PASS**, 11.831s, no skips. Direct real
`source_ready(discover())`: PASS without release preparation or remote operations.
Evidence logs are ignored local files in `.gradle/phase-b-evidence/`.
No baseline full Gradle invocation: this is standalone release/test tooling;
the ladder's ordinary product/UI/lifecycle baseline does not apply. Changed JVM
checks still require final normal/strict Gradle and artifact gates.

## 2. Actual preparation and dispatch flow

Public preparation stays `nix run .#prepare-release -- <VERSION>`. It inspects
consumed annotated release tags, allocates code automatically, atomically replaces
each intended metadata file, and leaves the exact metadata diff unstaged. The
maintainer reviews, commits and pushes main. `nix run .#release-tag -- <VERSION>`
then checks clean source/history/live main, prepares and pushes the annotated tag,
and prints coordinates; optional `--json` remains. Internal read-only diagnostic
`python3 -B tools/release/release.py check-release <VERSION>` remains.

Manual workflow_dispatch selects main with unchanged required string inputs
`version` and `source_commit` (full SHA). No workflow source edit/commit/tag step
is added. Existing tag creation still precedes dispatch and qualification. CI
qualifies, materializes the same qualified derivation, transfers unsigned evidence,
requests protected signing approval, verifies independently, then publishes.
No live command above was invoked in this milestone.

## 3. Controller responsibilities and test classification

| Responsibility / tests | Classification and disposition |
| --- | --- |
| Preparation, atomic metadata updates, idempotence, dirty/ref/transport failures (test_source) | A/B: retain operational behavior |
| Canonical version/code, history, exact SHA/tag and caller repository ownership (test_source; former policy tests) | B: retain; consolidate reusable-source tests into test_source |
| Public signer/fingerprint, APK metadata/provenance, payload, schemes/alignment, bundle/finalize (test_release) | C: retain meaningful positive/negative cases |
| HTTP absence, existing draft/release, pagination, downstream failures, hosted-byte mismatch (test_publication; test_release) | A/C: retain fake transport and no-adoption behavior |
| Duplicate fingerprint grammar / malformed identity checks | D: consolidate into existing test_release coverage |
| Flake wrapper source-string assertions | D: remove; existing release-apps exercises actual packaged commands |
| Workflow step/permission/text counts and Actions syntax fixtures | E: remove; review actual workflow configuration |
| Digest inventory grammar/hashing and synthetic hash tests | E: remove with both implementations |
| Standalone exact Gradle metadata parser harness | B: retain; tests missing/malformed metadata with real offline Gradle |

`source.py` remains the owner of repository context, allocation/history,
source-readiness, public identity, preparation helpers and tag operations.
`release.py` remains orchestration for source commands, artifacts and gh publication.
No language migration, replacement controller, recovery/state database, approval
file, source-hash registry or general test runner is introduced.

## 4. Removed workflow policy machinery

Delete `REVIEWED_ACTIONS`, `workflow_actions` and `action_policy`, their restricted
plain-YAML grammar/runtime-comment associations, three dedicated YAML fixture tests,
and the large workflow static-contract test. Remove `action-policy` CLI dispatch
and its CI/all-four-release-job callers. Delete obsolete `test_policy.py` after
moving canonical-version, immutable repository and import-without-I/O behavior tests
into test_source. Remove source-string import conventions while retaining the
actual import/no-I/O test. No replacement YAML parser/scanner/action registry.

## 5. Source digest decision

The inventory offered accidental-change detection, not independent authorization:
its expected values and consumers were editable in the same reviewed Git commit.
Delete `tools/release/reviewed-inputs.sha256`, `REVIEWED_PATHS`, `INVENTORY`,
`reviewed_inputs`, `source_pins` and their readiness plumbing. Remove the Python
inventory fixture and the Java digest parser, inventory test, file-hash test and
synthetic release-metadata/hash test. Remove active documentation update instructions
and the obsolete Gradle comment. No replacement table or generated approval file.

The remaining `OtpAuthSupplyChainGuardTest.noQrDependencyDeclared` is unchanged;
it rejects existing QR/camera/ML/cloud-SDK spellings in app/root/settings build files.
Current verifyMavenBoundary rejects local/project/file/substituted Totipo inputs and
requires NIO/core 0.2.0 with BC 1.86, but does not forbid every unrelated Maven module.
The source dependency guard therefore remains; dependency policy is not weakened
or redesigned. Strict locks, verification metadata, repository restrictions and
Nix dependency response cache remain.

## 6. Preserved Git/source/version boundaries

Retain canonical numeric stable X.Y.Z and canonical positive LF-terminated code in
1..2100000000; current source has no legacy fallback. Retain source Gradle metadata
wiring/application ID readiness checks, tracked regular prerequisites, signing-file
guard and private-key source scan. Retain clean main/index/untracked checks and
only exact expected preparation metadata diffs. No generic source/tree verifier.

CI identity still binds requested full SHA to GITHUB_SHA, main dispatch ref, exact
checked-out HEAD, reviewed VERSION, parsed VERSION_CODE, annotated local tag/peel,
remote tag object/peel and current remote main. Public certificate DER digest is
checked against committed expected fingerprint and keytool. Main advancement during
approval still fails closed. No adoption of candidate source/tag authority.

A pre-existing distinction was identified: qualification compares observed
versionName against VERSION, while VERSION_CODE is parsed from source and delivered
through reviewed Gradle wiring, but has no direct APK/provenance-to-source equality
assertion. A narrow additional equality check was proposed to the owner; no change
to authorization/validation ordering will be made without the requested decision.
No approval was supplied during this turn, so existing behavior is preserved.
The additional direct assertion is deferred; no direct versionCode equality
guarantee is newly claimed. This is a pre-existing limitation, not a boundary
formerly established by the removed source hash table or workflow parser.

## 7. Allocation evidence

The actual invariant remains `max(consumed annotated release codes) + 1` (default
zero history), strictly newer numeric semantic version, duplicate-code and Android
integer-overflow refusal. Legacy tagged commits lacking VERSION_CODE still use the
single unambiguous literal app Gradle assignment. A present malformed/nonregular
VERSION_CODE never falls back. Tagged VERSION must match the tag exactly; lightweight,
malformed v-prefixed, stale/missing/inconsistent local/remote tags and noncommit
peels fail closed.

Disposable fixtures cover legacy codes 1/2 → preparation code 3; new-format next
code 4; MAX_CODE overflow; duplicate codes; malformed/missing/ambiguous metadata;
idempotent preparation and dirty/staged/untracked/wrong-branch rejection. New test
uses consumed codes 9 then 4 and worktree code 99: next code is 10. No actual code
was allocated or metadata changed in the real checkout.

## 8. Tags and uncertain mutations

Retain exact annotated target object/header/message, tag VERSION/code and HEAD
binding; live/tracking origin/main equality; single inspected/pushed endpoint;
pre/post mutation ref/cleanliness checks; isolated remote-object fetch validation;
no-force/no-follow-tags/single-tag push despite hostile Git config. Retain exact
local-only resumption and exact local/remote no-op. Reject remote-only tags until
human synchronization/review, wrong messages/source/shape/lightweight/signed tags,
remote races and unreadable/changed remote state.

New failure injection executes real temporary tag creation or push, then loses
its acknowledgement. Both operations fail without reporting success and without
an automatic retry. A later explicit invocation validates the exact surviving
local/remote pair. All Git mutations are confined to disposable repositories/bare
fake remotes. Existing ambiguous/failing mutation coverage is retained.

## 9. Controller changes

Remove only redundant source-digest and workflow-policy support from source.py and
remove the obsolete CLI branch from release.py. Keep readiness prerequisite presence
and source/version/public signer semantics; no successful release UX is removed.
Post-write source readiness is retained because preparation mutates metadata.
Pre/post qualification identity checks and per-job bundle/source validation remain
because qualification, approval and publication are separate temporal boundaries.
These are not duplicate immutable-source assertions at the same boundary.

## 10. Four-job workflow and authorization review

Reviewed actual final workflow source, not a replacement YAML test. Manual inputs,
main/GITHUB_SHA checks, exact commit checkout, fetch-depth 0 and nonpersisted
credentials remain. Full-SHA checkout/Nix/upload/download Action references unchanged.
Global contents read remains; sign explicitly read; only publish has contents write.
No id-token grant, PR-target trigger or workflow-writing credential is introduced.
`qualify → sign → verify → publish` remains via needs; signing still uses protected
`android-release`; concurrency group android-release/cancel-in-progress false remains.
Verification has no private-key environment; publication cannot run before verify.

Workflow diff removes policy steps and the separate duplicate Python suite only.
The source-aware flake check supplies that suite during authoritative qualification.
CI still runs package-inclusive check and unchanged-input validation. No CI source
commit/edit, new tag/release overwrite/adoption or automatic uncertain retry route.

## 11. Signing-secret isolation

Signing step is unchanged: resolve Nix SDK/JDK before secrets, then one shell step
receives four environment-only signing secrets. Restrict PATH to trusted JDK/system
utilities, unset Java option injection, require nonempty credentials, umask 077,
private mktemp directory/600 PKCS12, native apksigner env-password inputs, separate
signed output, EXIT/INT/TERM cleanup and immediate private directory deletion.
No Gradle, Nix, repository Python or gh executes while production secrets are in
scope. Subsequent bundle checks run after secret step ends. No signing key is
transferred to verify/publish. SIGKILL/runner destruction remains the existing
cleanup limit, without a claim of guaranteed trap execution.

## 12. APK/payload/signer contracts

verify-apk.py, payload.py, verify-signed-apk.py and signing-smoke.sh remain byte-identical.
Permanent fingerprint remains
`74fd4c313856e1773fd174fba4ecd2a12cc4fde85ae34a3d8a3e986353b01632`.
Native valid sole-certificate verification, v2/v3 enabled and v1/v4 disabled,
alignment-preserved signing and native alignment check remain. No algorithm,
key ownership, certificate, application ID, resources or manifests change.

Retain unsigned SHA and metadata/provenance binding, exact provenance schema/types/
field/ref/hash checks, signed artifact SHA, unsigned/signed payload entry/order/
metadata/local-header/compressed-byte equality, and hosted whole-file equality plus
independent re-verification. Existing permission/ingress/branding/DEX exclusions
remain as implemented; no Phase C final-manifest claims or redesign. Three
same-run archive artifact handoffs still refuse overwrite and hidden inputs.
No independent APK reproduction experiment is required or claimed here.

## 13. Publication failure coverage

No production publication implementation changed. Fake gh transports exercise
well-formed 404-only absence; malformed HTTP/JSON and 401/403/429/5xx/process/network/
timeout failure; paginated drafts/ambiguity; existing published/draft no mutation;
create draft before asset download; changed hosted bytes leave draft unpublished;
failed listing/download/hosted verification/source validation/PATCH leave refusal
state; explicit retry never adopts an existing draft. No automatic retry,
overwrite, unconditional adoption or synthetic recovery receipt.

## 14. Nix/CI qualification wiring

Add narrow `checks.release-tooling` using existing stdlib unittest discovery and
Git/JDK17. Its fileset selects tools/release, tools/verify-apk.py, root/app Gradle
scripts, release/signing and release.yml prerequisite. Fixtures need these actual
source/public identity inputs. Workflow text is not interpreted; only prerequisite
presence is retained. Check copies sources to a writable disposable directory and
uses private HOME. Required certificate absence now fails instead of skipping.
Remove wrapper source-string assertions and obsolete workflow test/skip.

Remove release Python discovery from Android package installCheck and the release
workflow. Within flake qualification the substantive suite now runs once, alongside
unchanged package-inclusive Android qualification and existing release-app wrappers
check. Package APK checks remain intact. No JDK/Gradle/AGP/SDK/AAPT2/cache/lock change.
Agent runs no Nix. Source-aware host fixture uses the same selected trees/files to
exercise the qualification membership separately from full-checkout discovery;
that check is not a claimed Nix derivation/evaluator PASS.

## 15. Test reductions and retained behavior

Baseline 58 Python tests. Removed six discovery tests: three Actions grammar/inventory,
one duplicate fingerprint policy test, one workflow static contract, one source hash
inventory. Retain the three reusable-source policy tests in test_source. Add three
behavior tests: max-code authority, exact dispatch/source/tag identity and ambiguous
mutation acknowledgements. Result: **55 substantive Python tests**, no skips.
JVM guard shrinks four tests to the one unchanged no-QR policy test (three digest-only
tests removed). Other application tests and all device harnesses remain unchanged.
Consolidate duplicated malformed-identity checks; no useful payload/signature test
is deleted. Standalone Gradle metadata negative harness remains.

## 16. Host qualification and failures

One newly added dispatch
fixture initially expected checkout rejection while supplying a mismatched dispatch
SHA: the earlier dispatch check correctly rejected first. Fix the fixture to match
its test SHA and exercise checkout identity; focused retry PASS.

The first focused offline JVM invocation failed before tasks because this host
cache lacked the AGP 9.4.0 plugin marker. Normal focused invocation with strict
verification then PASS, 33s, 22 actionable (2 executed / 20 up-to-date), one test.
No lock/metadata repair. Offline standalone metadata harness PASS, 8s, one task
executed: valid version/code, 13 malformed codes and missing file rejected.
Baseline release suite PASS; initial policy-removal 52-test suite PASS; expanded
55-test suite found the fixture failure above; focused fix/contract tests PASS.
Intermediate 55-test full-checkout and source-aware suites PASS without skips.
Last duplicated identity fixture reduction and unused import cleanup subsequently
invalidated those standalone suite results only; focused surviving identity tests
PASS. Final frozen full-checkout suite **55/55 PASS**, 12.044s; final source-aware
fixture **55/55 PASS**, 11.218s, both zero skips. Frozen direct source readiness PASS.
Gradle does not consume these Python test modules, so these edits do not invalidate
Android Gradle/artifact evidence. No failed/repeated full Gradle gate.

| Stable host gate | Result |
| --- | --- |
| `gradle check :app:assembleDebug :app:assembleRelease` | PASS, 2m33s; 90 actionable, 18 executed / 72 up-to-date |
| `gradle --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug :app:assembleRelease` | PASS, 2m12s; 92 actionable / 92 executed, no up-to-date reuse |
| JVM totals at both checkpoints | 336 tests, zero failures/errors/skips |
| Final external debug / unsigned-release APK verification | PASS, two commands against strict outputs |
| Offline releaseRuntimeClasspath / verifyMavenBoundary | PASS, 739ms; two executed tasks; only NIO/core 0.2.0 + BC 1.86 |
| Documentation local file links / obsolete active policy-reference scan / diff whitespace | PASS |

Existing visible AAPT2 experimental, deprecated Java API and Gradle deprecation
warnings remain. No warning suppression, dependency-cache repair or lock regeneration.
Final strict APK SHA-256 values (no independent reproduction claim):

```text
debug: 47ab261228f8482af4b45e485574d5efece39f13c93fe1cdabab266d6febca64
unsigned release: f7dda5dd5c5eb002ea61317229131847d9b6b743dedc1b374bd3de3be212b1ec
```

## 17. Disposable signing and devices

**PASS**, one unchanged signing-smoke invocation against the strict unsigned APK.
Native TEST-only signing verified the sole expected test certificate, required
v2/v3 schemes, alignment and release boundaries. ZIP payload equality, unsigned
SHA/provenance immutability and six expected rejections passed: wrong fingerprint,
wrong signer, unsigned input, tampered signed payload, valid-signature altered
payload, malformed fingerprint. Temporary PKCS12 files were erased before repository
Python verification resumed; EXIT cleanup removed the disposable directory.
No production credentials, persistent local qualification key, device install or
live release used.
Physical harness runs: zero; no changed application/device subsystem calls for them.

## 18. Human Nix gate

**PASS, human-reported.** After the final included-input freeze, the owner reported
`nix flake check passed`. One human invocation reported; agent Nix invocations zero.
No Nix log, duration or task/cache breakdown was supplied, so no independent Nix
execution or per-check execution breakdown is claimed. The complete frozen
implementation diff was revalidated byte-identical after this result, with HEAD
unchanged and no staged contents. Only this excluded report is updated; the PASS
is not invalidated. No renewed Nix or dependency-cache update is required.
Affected inputs include selected tools/JVM tests, flake/package configuration and
the narrow tooling check's public identity/workflow prerequisites.

## 19. Dependencies and cache

Runtime stays external NIO/core 0.2.0 + BC 1.86. Gradle 9.8.0, AGP 9.4.0, JDK17,
API37, Build Tools36.0.0, platform-tools37.0.1 and cmdline-tools22.0 stay pinned.
All lockfiles, verification metadata, package-deps.json, flake.lock, settings and
Gradle properties unchanged. Jailed agents/shared development environment retained.
Root/app build behavior unchanged (app edit only obsolete-comment replacement).
No dependency refresh, hash update or Nix download-cache generation.

## 20. LOC accounting

| Surface | Added physical lines | Deleted physical lines | Net removed |
| --- | ---: | ---: | ---: |
| Python release code/tests | 119 | 416 | 297 |
| JVM source guard | 1 | 81 | 80 |
| Nix configuration | 22 | 1 | -21 |
| Gradle comment | 1 | 1 | 0 |
| Actions YAML | 0 | 12 | 12 |
| Code/config/tests total | 143 | 511 | 368 |
| Obsolete source digest data | 0 | 10 | 10 |

Two files deleted: obsolete test_policy and source digest data. Required policy
behavior tests were moved rather than counted as lost coverage. Documentation and
this report are excluded from implementation savings. No binary deletion.

## 21. Deliberately withdrawn guarantees

Accidental repository-controlled input changes no longer fail a second manually
maintained hash table; Git source authentication remains. Repository Python no
longer rejects alternate YAML grammar, new workflow inventory, unknown Action /
version-comment/runtime associations or fixed step names/counts. Actual full SHA
pins/job permissions/credential isolation remain reviewed source. No independent
authorization was supplied by either retired self-validation mechanism. No strict
Gradle/offline/package gate, signing, payload, version allocation or tag refusal
policy is withdrawn. No replacement parser or additional source approval registry.

## 22. External GitHub protections requiring owner confirmation

Before the next live release, confirm effective main branch protection (including
source/workflow review), required CI status checks, release tag protection against
unauthorized create/update/delete, effective Actions default/job token permissions,
android-release required reviewers/deployment ref restrictions/self-approval policy,
signing secrets scoped only to that environment, and all applicable administrator,
app and actor bypass permissions. Source workflow permissions do not prove these
settings. Existing provisioning is human-reported only; this phase did not inspect
or modify live GitHub settings. Neither retired parser could independently authorize
a modified copy of itself; removing it introduces no separate protection-setting
gap. These existing external trust prerequisites remain unverified for a live release.

## 23. Input freeze, final Git and execution counts

Freeze statements, after final host qualification and source-aware fixtures:

- **Production source stable:** app main/debug/release Java, manifests/resources,
  APK identity and VERSION/CODE byte-identical to starting HEAD. APK-producing
  build behavior unchanged; app/build.gradle.kts input changes only an obsolete
  comment. No independent APK reproduction experiment is performed or claimed.
- **JVM tests stable:** only three obsolete digest tests removed; no-QR test
  unchanged. Final normal and strict both pass 336 tests without skips.
- **Build/package configuration stable:** test relocation/fileset and obsolete
  suite caller removal finalized. Package-inclusive Android check intact; all
  toolchain/dependency/lock/cache identities unchanged.
- **Qualification harness/tooling stable:** source/release simplification and
  consolidated behavior tests finalized; full-checkout and source-aware suites
  pass. Existing APK/payload/signed verifier, signing smoke and device harnesses
  remain byte-identical; source readiness passes without the retired inventory.

All intended inputs are tracked or intent-added before human Nix. No staged
contents, commit, push, real tag mutation, workflow dispatch, production signing
or publication. Ignored caches/build output, temporary Git fixtures and ephemeral
signing keys remain outside source membership. Source-filter determination:
AGENTS/root README/review are not consumed; release README also excluded. Signing
README and release.yml are now narrow tooling-check sources; their edits would
invalidate that Nix check. Excluded report-only results do not invalidate Android
or release-tooling checks. No included-input change after this freeze is planned.

Final branch main / HEAD `055a771f61a2dd1ce564e330d68ac8ca165ae4f2`.
After report intent-to-add, `git diff --cached --name-only` is empty and
`git diff --cached --quiet` passes; `git diff --check` passes. `git diff --name-only`
contains exactly the fifteen paths in this complete status, including the report:

```text
 M .github/workflows/ci.yml
 M .github/workflows/release.yml
 M README.md
 M app/build.gradle.kts
 M app/src/test/java/org/totipo/android/OtpAuthSupplyChainGuardTest.java
 M flake.nix
 M package.nix
 M release/README.md
 A review/ANDROID_RELEASE_TOOLING_SIMPLIFICATION_REPORT.md
 M tools/release/release.py
 D tools/release/reviewed-inputs.sha256
 M tools/release/source.py
 D tools/release/test_policy.py
 M tools/release/test_release.py
 M tools/release/test_source.py
```

No unexpected source, dependency, metadata or release-identity changes. Complete
implementation diff excluding this report is retained as ignored local freeze
evidence; SHA-256 `29b2825472ec8e6e260cd44d6e947e68a58ca5781735c5da0f6029b85033e43c`. This is qualification evidence, not a replacement
release source hash table, approval registry or runtime validation mechanism.
Report-only human-result updates do not invalidate included inputs. After the
owner-reported Nix PASS, the frozen implementation diff was revalidated identical;
only this excluded report changed.

| Execution category | Count / result |
| --- | --- |
| Baseline full Gradle | 0; standalone release/test milestone justification in section 1 |
| Focused JVM Gradle | 2: offline missing plugin cache failure, normal focused PASS |
| Standalone Gradle metadata harness | 1 offline PASS, 13 malformed codes plus missing file rejected |
| Final normal full | 1 PASS |
| Strict-clean full | 1 offline PASS |
| Final runtime graph/boundary | 1 offline PASS |
| External final artifact set | 2 APK commands, both PASS |
| Embedded Gradle APK verifier | 2 executions: final normal and strict |
| Full-checkout Python discovery | 5: baseline58 PASS, initial52 PASS, expanded55 fixture failure, intermediate55 PASS, frozen55 PASS |
| Source-aware Python discovery | 2: intermediate55 and frozen55 PASS, no skips; distinct source-membership qualification |
| Narrow Python unittest invocations | 3 PASS: dispatch fixture correction (1 test), repository/uncertain mutation contracts (4), surviving identity coverage after dedup (2) |
| Direct real source readiness | 3 PASS: baseline, post-retirement, frozen |
| Disposable signing smoke | 1; result recorded in section 17; six expected negatives, six nested existing APK-verifier commands |
| Physical harness / device installation | 0 |
| Agent / reported human Nix | 0 / 1; final gate PASS, human-reported |
| Production signing / live release operations | 0 |

Repeated standalone suites correspond to initial removal, new failure-coverage
review and later dedup/import cleanup invalidating Python evidence. Narrow tests
resolved the sole fixture failure before returning to full discovery. Source-aware
runs validate required Nix source membership rather than counting package skips as
full coverage. No Android full gate was repeated or invalidated. The host suite
repetitions are not wired as duplicate suites in the final Nix/CI qualification path.

## 24. Limits and deferred work

No live release was tested, no remote workflow execution or external settings PASS
claimed, and no new universal APK/signed reproducibility or device behavior claim.
Phase C final-manifest/APK verifier redesign, device harnesses, local identity
migration retirement, gh pinning, dependencies and release settings remain outside
Phase B. The proposed direct source-versionCode equality strengthening is deferred without
approval; existing metadata validation/wiring and workflow ordering remain.
Current qualification status is reported separately from future live readiness.
