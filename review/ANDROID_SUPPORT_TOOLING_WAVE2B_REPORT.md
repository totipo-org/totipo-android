# Android support-tooling Wave 2B

Status: **COMPLETE**, with local checks and final human-reported
`nix flake check path:.` PASS. The host Gradle executable is available; offline Android plugin resolution
is unavailable. No separate host normal/strict PASS is claimed. All 174 frozen inputs were
rechecked after the human result. Everything is unstaged/uncommitted.

## 1. Clean starting state

Initial inspection established clean `main` at the exact requested committed
Wave 2A boundary, `89348a9b85ad30e6cee876994c20c6a411e4fe49`. `git status --short`
was empty; VERSION was `0.1.2`, VERSION_CODE `3`. The committed Wave 2A final
completion report and implementation were present. No untracked abandoned draft
was present at that first boundary.

The initial preflight stopped for missing host cache. Following the user's explicit
qualification correction, the sole untracked stopped-attempt report created by
this agent was removed, and clean main/same HEAD/metadata were re-established
before implementation. This new report supersedes that stopped report. No other
work was discarded; no abandoned uncommitted report supplied implementation facts.

## 2. Classification and qualification correction

RELEASE_TOOLING and TEST_OR_QUALIFICATION_TOOLING; also BUILD_DEPENDENCY solely
because the reviewed digest data is consumed by the JVM supply-chain guard. No
product code, build-script, dependency version, lock, protocol or language change.

The user's later Wave 2B correction overrides the earlier host-cache stop rule:
human Nix is the required hermetic JVM/build/package boundary. Record
**HOST_GRADLE_NOT_AVAILABLE** was the implementation-time qualification label,
not a claim that the Gradle executable was absent. At initial inspection,
GRADLE_USER_HOME was unset and `/home/niki/.gradle` was absent, including its wrapper
distribution and mutable dependency cache. No host Gradle command was run during
implementation; no separate Gradle PASS is claimed. No cache-preparation, recovery,
dependency-population or redesign was performed. A later user-requested direct
Gradle invocation confirmed executable availability and the offline plugin-resolution
limitation; see the post-completion clarification below. Existing local pinned JAR bytes were read directly for an independent
JVM guard run, without creating a Gradle user home.

## 3. Reconstructed successful 0.1.2 release contract

The source of truth was the committed instructions, root/release/signing READMEs,
support audit, Wave 2A final completion evidence, signed machinery, preparation,
version/provenance, publication fix and Actions/release UX reports, and the actual
current flake, package, workflow and release sources. Historical reports describe
older stages; current committed source takes precedence over removed recovery or
four-command interfaces.

Normal human UX remains exactly:

```sh
nix run path:.#prepare-release -- <VERSION>
# Human reviews, commits and pushes main normally.
nix run path:.#release-tag -- <VERSION>
```

Deliberate workflow dispatch follows separately, with printed version/source_commit.
`check-release` remains an optional internal diagnostic, never a required third app.
The flake wrappers provide Python/Git/OpenSSH/JDK/coreutils and CA paths, execute
store-resident release.py without changing cwd, and operate on the caller worktree.
Neither command builds, signs, publishes, dispatches, commits or pushes main.

The existing workflow graph is qualify → protected sign → no-secret verify →
publish. Its operational trust phases are qualification → protected signing →
no-secret artifact verification → new draft/upload → hosted download/reverification
→ publication. Four jobs and their permissions/environments remain unchanged.

Qualify requires deliberate main dispatch, exact full source SHA, full history,
annotated tag/source/remote identity and clean source/public signer. Nix qualifies
the same Android derivation exposed by checks.android and packages.default.
Preparation compares those drvPaths, materializes that package, checks the actual
unsigned APK and writes its original qualification bundle. Sign revalidates after
approval, resolves SDK/JDK before secrets, signs only those bytes and rechecks the
unsigned input after secrets end. Verify finalizes the candidate, retaining original
unsigned/provenance evidence. Publish performs the refusal/download/reverification
sequence described below. Public asset names remain
`totipo-android-<VERSION>.apk`, its `.sha256`, and `release-provenance.json`.

## 4. Old dependency/context graph

`source.py → release.py` was an import and backward access to ROOT, CERT, FP,
sha, guard and public identity. `release.py → source.py` used runtime imports for
code parsing and source CLI execution. This formed a cycle. Executing release.py
as `__main__` could import it again as `release` through source.py. worktree()
mutated release's ROOT/CERT/FP. Temporary tests changed cwd and restored globals.
Python readiness extracted Java `Map.entry` syntax as an accidental cross-language API.

## 5. New dependency/context graph

CLI boundary `source.discover()` → immutable `source.Repository(absolute_path)` →
explicit source/history/readiness/identity and release orchestration functions.
`release.py → source.py → payload.py`; signed verifier → release/source/payload.
Payload remains independent. There is no source → release import/backward edge.

Repository has one path field, derived certificate/fingerprint properties, and
Git/text-command methods. Construction performs no discovery or filesystem read;
relative paths fail. Discovery is invoked at the CLI boundary, with Nix-store
worktrees refused. Artifact metadata observation is repository-independent SDK
output; verifier executable locations derive from implementation paths, whereas
expected public identity and source policy derive from the explicit caller context.

## 6. Mutable-global/cycle removal

ROOT/CERT/FP context globals are removed. Reusable operations require a repository
argument; no default repository or cwd mutation remains. Source tests pass their
own temporary contexts, including race hooks on context command methods. Tests
interleave two repositories, check immutable ownership and unchanged cwd, and
import modules outside Git with discovery/file-read methods set to fail. Importing
source does not import release; neither import inspects a developer checkout.

CLI source-command orchestration/output moved to release.py. Source/history policy,
public identity, reviewed inputs and actions live in source.py. No DI framework or
new implementation module was introduced.

## 7. Version/versionCode policy ownership

`source.version()` is the sole neighboring Python canonical stable release policy.
Preparation, tag/history, dispatch identity and provenance call it. Prior release.py
request/provenance regexes accepted some leading-zero/prerelease forms contrary to
the documented stable contract; those neighboring inconsistencies now fail closed.
This deliberately does not preserve unsupported syntax as a differential invariant.

APK badging extraction remains observation, not release policy. Gradle/Nix build
parsing and shallow early workflow dispatch syntax rejection remain independent
boundaries. No stable syntax was broadened. VERSION and VERSION_CODE remain the
canonical metadata; no versionCode authority returned to app/build.gradle.kts.

Every eligible annotated historical tag consumes version/code, even for failed or
aborted publication. Preparation consumes neither. Next code is max consumed + 1;
malformed/nonannotated/missing/duplicate/stale history and overflow fail closed.
The historical-only unambiguous literal-code fallback remains bounded to tags
without VERSION_CODE. Invalid new-format metadata never falls back. Target must
numerically exceed all consumed versions. Allocation behavior was not changed.

## 8. Reviewed source inventory before/after

Before: twelve expected digests in Java source, parsed by Python with regex.
After: `tools/release/reviewed-inputs.sha256` is the single digest data authority.
All twelve values were copied exactly and compared against the starting Java map;
none was regenerated from current bytes. VERSION/VERSION_CODE remain outside it.

Grammar: sorted lines of lowercase 64-hex SHA-256, two spaces, allowlisted path,
one LF. Java and Python independently parse and reject malformed, missing,
duplicate, unknown or unsorted entries and compare actual file bytes. Both own
explicit path allowlists, not expected digest constants. No parser dependency.
Java retains the three established optional evaluator/cache-file omissions in
package.nix's filtered Gradle source; Python readiness requires the full checkout.
Adversarial grammar tests run in both languages. The data is included through the
unchanged tools/ source filter; no build/filter configuration was changed.

## 9. Action policy before/after

Before: readiness checked immutable-ref syntax; test_release.py alone owned the
semantic reviewed action table and full-declaration checks. Workflows did not invoke
that reusable semantic policy before Nix installation.

After: source.py owns the reviewed SHA/version/runtime tuples and no-network
restricted workflow declaration policy. Readiness, tests and the `action-policy`
CLI share it. CI and each release job invoke it after checkout and before Nix
installation. Both workflows and recursive .github YAML/action declarations are
covered; unknown workflows/vendors/actions, floating/short/uppercase SHA refs,
wrong reviewed version associations, runtime overrides, quoted/escaped/flow/
alias/anchor/merge/multiline uses declarations and unsupported document grammar
fail closed. Literal/folded shell scalar bodies are not treated as YAML actions.
No YAML library or live action metadata lookup was added.

Pins remain checkout v7.0.1/node24, install-nix v31.11.1/composite, upload
v7.0.2/node24, download v8.0.2/node24, at their original immutable 40-hex SHAs.
The complete existing set of 16 action invocations and all full-history release
checkouts are unchanged. The shallow early dispatch guard remains deliberately
separate; it supplies no semantic release-policy authority.

## 10. Production signing invariance

Production fingerprint remains
`74fd4c313856e1773fd174fba4ecd2a12cc4fde85ae34a3d8a3e986353b01632`.
Public certificate/digest bytes are unchanged and independently checked by
certificate DER hash and JDK keytool parsing.

The complete protected secret-bearing step, including env and shell, is
byte-identical to starting HEAD. Extracted-body SHA-256:
`839e8551c7e23141308ef8154d37eb115f4f4fd66bebf49f9258e0e3d67b829e`.
Protected android-release, one narrow shell step/four secret expressions,
restricted PATH, JVM injection removal, temporary key permissions/lifetime/traps,
explicit v1/v4 off and v2/v3 on, alignment preservation and deletion remain.
No repository Python/Gradle/Nix/gh executes while private material exists.
New action checks run before SDK resolution/key injection. No production credential
was needed; no production signing or remote workflow was run.

## 11. Provenance invariance and public fingerprint parser

Qualification chain remains source/build → qualified unsigned APK → observed
applicationId/versionName/versionCode/SHA/source → provenance → signed candidate.
Exact schema/field/type/range checks, duplicate JSON-key refusal, applicationId pin,
unsigned SHA binding, source/tag identity and observed metadata authority remain.
VERSION/versionName is checked at qualification; VERSION_CODE remains the build
input and code history authority. Signed metadata is never its expected authority.
Original unsigned/checksum/provenance bytes are carried through phases unchanged.

One strict source.fingerprint parser replaces two neighboring strip/regex parsers.
It requires exactly the documented 64 lowercase hex bytes plus LF; formerly silent
whitespace/no-LF acceptance is closed. Committed public identity is unchanged.
Actual signer remains independently observed from apksigner on the candidate;
expected signer still comes from the committed certificate/digest, never candidate
bytes. Schema fields and successful 0.1.2 provenance are unchanged; only unsupported
release syntax is consistently refused as described in section 7.

## 12. Trust-transition revalidation retained

No cached PASS replaces fresh observation. Source/bundle/unsigned checks remain
at qualification, after protected approval, after the signing step, on the no-secret
verify worker, at publication entry, after hosted download and at publication source
recheck. Shared parser/verification code does not remove any invocation. Signed APK
checks still bind original unsigned evidence, verify signer/schemes/alignment/
release boundaries and metadata; orchestration still proves payload equivalence.

## 13. Publication safety retained

Order remains: refuse published release; refuse matching draft; create NEW draft
with exactly three intended assets; locate it through authenticated paginated draft-
capable listing; download hosted bytes; compare all exact hosted hashes; reverify
hosted artifact and signer; recheck source; PATCH publication by release ID.
No recovery/adoption, overwrite, deletion or implicit dispatch was introduced.
Only well-formed HTTP 404 with consistent CLI status permits published absence;
auth/network/rate/server/JSON/HTTP/list failures remain fatal. Publication code
changed only to pass explicit repository context and distinguish GitHub repo names.

## 14. Signing-smoke disposition

Existing smoke already uses shared qualified-provenance and verifier/payload code;
its separate shell owns disposable key/tool lifetime. No useful JSON/ZIP/APK parser
was moved into Bash, and production signing was not altered for sharing. Only its
no-secret Git source observation changed to explicit CLI-discovered context. The
existing public-test-fingerprint substitution while disposable keys exist is
unchanged; no repository code runs until both test keystores are erased.

## 15. Build-metadata harness disposition

Retained as an independent focused harness: one offline minimal Gradle project runs
the actual Kotlin-DSL VERSION_CODE/VERSION parser with valid code, thirteen malformed
cases and a missing-file/no-default case. No nested Gradle invocation was added to
the Python suite. Only its stale version-specific docstring was corrected.
Execution was not attempted with the absent wrapper/cache:
HOST_GRADLE_NOT_AVAILABLE. This is not a metadata-harness PASS. Normal metadata
parsing/build integration and the changed JVM guard are qualified by the final
human-reported hermetic Nix PASS. The standalone negative harness was not run.
The independent negative harness remains available for a supported offline host.

## 16. Tests added/changed and failure evidence

Original 47 Python tests retained, with explicit repository arguments and shared
policy delegation replacing globals/cwd/test-owned semantic policy. Ten new tests
bring the full suite to 57: seven policy/context/import/fingerprint/action tests,
one pre-push source-drift test, one alignment-failure test, one downstream
publication-failure/retry test (five phases). The Java guard grows from three to
four tests with independent malformed-inventory coverage. Existing digest tests
now use the deliberate data authority.

First adaptation suite: 47 tests, two test-call errors for missing repository
arguments in source_ready/history. Corrected those call sites and remaining
identity fixtures; the next 47-test suite passed. Subsequent development suites
passed 53, 56 and 57 tests as scope/ownership/adversarial coverage was completed.
No full Gradle gate failed or was repeated.

Two external validation-runner preparation errors were fixed outside source: the
JVM runner initially omitted the verification XML namespace, and the filtered-tree
copy runner initially attempted an optional absent app/buildscript-gradle.lockfile.
Neither reached its test execution or revealed a product defect. The corrected
runners passed, without an included-input change or broad build retry.

## 17. Temporary Git and differential results

Real disposable worktrees and bare remotes remain the source/tag test substrate.
Tests preserve consumed/next-code history, malformed/lightweight/tree tags,
legacy/new metadata, duplicates/stale refs/overflow, exact annotated objects,
idempotence, wrong remote objects, no force/main/unrelated-ref pushes, push URL
constraints, pre/post-push drift/races and isolated remote-object verification.
Git writes/network-like pushes in tests target only temporary local remotes.

External differential runner `/tmp/totipo-wave2b-differential.py` loads starting
Python sources copied to `/tmp/totipo-wave2b-old` and current code in separate
processes, with deterministic fixture Git dates. Exact JSON result equality:
preparation plan/output/diff, history/next code, accept/refuse build-wiring readiness,
unsupported target refusals, raw annotated tag object/message/source/ref,
coordinates/repeat coordinates, remote refs, provenance/schema decisions,
published absence decisions and twenty supported action-policy decisions.
Readiness differential covers build wiring; full public/digest/workflow readiness
is additionally covered by real CLI fixtures and independent static checks, not
claimed equivalent for newly refused unsupported policy syntax.
Results: `/tmp/totipo-wave2b-differential-{old,new}.json`; exact diff PASS.
Old runner executed once; new twice, the latter against final source.

## 18. Adversarial publication results

Preserved 200/404/auth/rate/server/process/network/timeout/malformed HTTP/JSON,
paginated/ambiguous drafts, existing published/draft refusal, provenance/schema/
unsigned-hash/signer/schemes/metadata/payload/hosted-byte negatives.
Added fatal alignment failure and downstream failure phases: draft lookup after
creation, download, hosted verify, source recheck and publication request. Each
creates exactly one draft; retry refuses the existing draft rather than creating
another or adopting it. Failures before publication never issue PATCH. No real
GitHub mutation or live publication was needed.

## 19. Two-command UX result

Full actual CLI integration in temporary repositories passed prepare twice,
optional read-only check, commit/main push confined to fixtures, release-tag twice
and deterministic JSON coordinates. The script resides outside the caller fixture;
only caller metadata/refs change. Preparation is idempotent; tag repetition is a
safe no-op; exact one-ref remote delta excludes main and unrelated refs despite
adversarial Git settings. Nix wrapper code is unchanged; final wrapper execution
belongs to human checks.release-apps. No real checkout metadata/tag/remote mutation.

## 20. Workflow/security/static results

Shared action policy and full-checkout security tests PASS. Permissions, secret
scope, protected environment, four-job dependency graph, artifact retention/
archive/overwrite/same-run settings and signing body invariance PASS. No action,
external dependency, permission or secret scope was added.

All nine release Python files compile without bytecode output. `bash -n` passes
signing-smoke and all 18 workflow run bodies. `git diff --check` passes. Wrapper
JAR/distribution pin verifier PASS. Public identity/private-material/digest guards
PASS. Actionlint is not installed; no download was introduced. Static tests and
shell checks are the executed workflow evidence; no remote CI/dispatch is claimed.
External static runner/log: `/tmp/totipo-wave2b-final-static.py` / `.log`.

## 21. Product/device/build non-change

`git diff --exit-code HEAD` passes for app/src/main, app/src/debug, tools/device,
release/signing, gradle, root/app build/settings/properties/lock inputs, package.nix,
flake.nix/lock, package-deps.json, VERSION/VERSION_CODE and payload.py. Untracked
paths are allowlisted new tooling/report files only; index is empty. Thus protected
product/device/build/dependency/version/certificate/payload bytes remain identical
to starting HEAD. No new production resource/manifest, version or dependency input.
The only app change is the test-only supply-chain guard. No physical qualification,
installation or historical SAF/M3D signer change is required or performed.

## 22. JVM and Gradle qualification

Independent focused JDK 17 javac/JUnit run: **PASS, 4 tests**. Existing local
JUnit 4.13.2 and Hamcrest core 1.3 JARs were checked against committed verification
metadata before use. Log `/tmp/totipo-wave2b-jvm.log`. No artifact was downloaded,
no Gradle cache was prepared, and no Gradle task was implied by this direct run.

Host final normal/strict Gradle: **NOT RUN**, under the explicit correction.
The implementation-time HOST_GRADLE_NOT_AVAILABLE label referred to offline build
qualification, not executable availability. The post-completion focused offline
attempt described below failed before tests at Android plugin resolution. Required complete JVM/lint/build/Maven/package boundaries
are qualified by human-reported `nix flake check path:.` PASS, not waived or
claimed as independently executed host Gradle gates.

## 23. Final suites and artifact evidence

Resumed clean baseline: **47 tests PASS**, 11.513s,
`/tmp/totipo-wave2b-restart-baseline.log`.
Final full checkout: **57 tests PASS**, 11.773s,
`/tmp/totipo-wave2b-final-python.log`.
Separate snapshot matching actual package source filtering: **57 tests PASS with
2 expected skips**, 8.863s, `/tmp/totipo-wave2b-filtered-tests.log`. The skips are
full-workflow and public-identity CLI fixtures whose inputs are deliberately
excluded from the Android package; full-checkout tests executed both without skips.

No fresh local host APK was built. The required artifact semantic checks are
part of the human Nix package's existing embedded release boundary and debug/release
install verifiers, covered by the final human-reported Nix PASS. The human supplied
no build log, artifact path or hash, so no fresh APK SHA or independently observed
build execution is claimed. Any exposed fresh hash is observational, never an
equality requirement against an older separately produced APK. Existing smoke
fixture metadata was independently observed as org.totipo.android / 0.1.2 / code 3;
it is separate from final hermetic package qualification.

## 24. Corrected APK model

**Historical raw APK-byte equality is NOT a Wave 2B invariant.** No comparison to
`b7050179...` or another historical artifact was used to accept/refuse this work.
Product/build/dependency/version bytes, semantic APK boundaries, provenance and
signer guarantees are the invariants. Intra-run unsigned immutability, hosted
byte equality and signed/unsigned payload proof remain required release safety
checks. No H1/W1/W2, ZIP-history differential, host-cache reconstruction or Nix
reproducibility experiment was run or requested.

## 25. Signing-smoke result

**PASS**, development and final runs, each using two disposable generated test
keys, three signatures and six expected rejections: wrong fingerprint, unrelated
signer, unsigned input, tampered signed bytes, changed payload despite a valid
signature, malformed fingerprint. Scheme negatives are exercised in Python;
actual positive alignment passes and the new fatal alignment regression passes.
Original unsigned/provenance hashes remain unchanged within each run; keys and
temporary directories are erased before repository verification.

Final command:
`bash tools/release/signing-smoke.sh .gradle/wave2a/release-aligned.apk`.
Final log `/tmp/totipo-wave2b-final-smoke.log`; preliminary log
`/tmp/totipo-wave2b-smoke.log`. Existing fixture SHA is an observation in the verifier
log, not a historical equality acceptance test. Final smoke was repeated because
release SDK-observation/CLI ownership changed after the preliminary run. No new
production key or physical installation. Final hermetic Nix APK checks are
separate and covered by the human-reported PASS.

## 26. Honest simplification metrics

Physical LOC includes blanks/comments. Nonblank/noncomment omits blank and
comment-only lines, includes docstrings. Python tests/harness count includes
standalone metadata harness; Bash and Java are recorded separately.

| Metric | Before | After |
| --- | ---: | ---: |
| Implementation Python physical LOC | 745 | 847 |
| Implementation Python nonblank/noncomment LOC | 620 | 706 |
| Python test/harness physical LOC | 911 | 1068 |
| Python test/harness nonblank/noncomment LOC | 824 | 969 |
| Implementation Python modules/scripts | 4 | 4 |
| Python test/harness files | 4 | 5 |
| Signing-smoke Bash physical LOC | 92 | 92 |
| Supply-chain Java test physical LOC | 73 | 100 |
| Shared mutable repository context globals | 3 | 0 |
| Distinct release/source import edges | 2 | 1 |
| Cycles / backward source→release edges | 1 / 1 | 0 / 0 |
| Neighboring canonical release-version validators | 3 | 1 |
| Reviewed source-digest authorities | 1, Java syntax | 1, deliberate data |
| Reviewed action semantic authorities | 1, test-only | 1, reusable policy |
| Independent shallow readiness action validator | 1 | 0 |
| Neighboring public fingerprint parsers | 2 | 1 |
| Normal human release commands | 2 | 2 |
| Release jobs / trust phases | 4 / 6 | 4 / 6 |
| Action invocations / action projects | 16 / 4 | 16 / 4 |

Implementation LOC grows by 102 and Python tests/harness by 157. New data has
12 lines. New files are one data file, one test module and this report. No shrink
claim: simplification is explicit ownership, removed cycle/context mutation and
fewer parser/policy authorities, with stronger refusal coverage.

## 27. Qualification accounting

| Category | Execution count/result |
| --- | --- |
| Baseline full Gradle | 0 |
| Focused Gradle | 0 during implementation; 1 post-completion offline attempt failed before tests (AGP resolution) |
| Final normal full Gradle | 0, HOST_GRADLE_NOT_AVAILABLE |
| Strict/offline clean Gradle | 0, HOST_GRADLE_NOT_AVAILABLE |
| Independent javac/JUnit guard | 1 PASS, 4 tests |
| Resumed release Python suites | 8: baseline; 5 iterations; final; filtered |
| Earlier stopped-preflight Python suite | 1 PASS, 47 tests |
| Differential fixture executions | 3: old once/new twice, comparisons PASS |
| Build-metadata Gradle harness | 0, retained, host cache unavailable |
| External final APK-verifier set | 0 local; package verification covered by human Nix PASS |
| Standalone wrapper verifier | 1 PASS |
| Embedded host Gradle APK verifier | 0 |
| Disposable signing smoke | 2 PASS, final repeat explained above |
| Physical/device runs or installs | 0 |
| Human Nix | 1 final routine check; human-reported PASS |
| Agent Nix | 0 |

The earlier preflight baseline was repeated only after the user corrected the
qualification rule and clean starting state was re-established. Five implementation
suites track actual edits/added coverage; the only failing one had the two explicit-
context test-call errors recorded above. No expensive full build was repeated.
Filtered-snapshot, differential and static runner preparation failures are recorded
separately, not as product/build failures. No input has changed after freeze.

## 28. Frozen-input inventory

All five freeze statements established after final local checks:

1. **Production source stable:** app/src/main and app/src/debug byte-identical to HEAD.
2. **JVM/product tests stable:** supply-chain guard/data migration complete; direct
   JUnit and full/filtered Python suites PASS; no further test edit planned.
3. **Build/package/dependency/version inputs stable:** zero diff, 0.1.2/code 3 retained.
4. **Release/source tooling and qualification harnesses stable:** final Python,
   differential, signing-smoke, syntax and policy checks PASS.
5. **Workflow/security policy stable:** early shared guards finalized; pinned actions,
   permissions, secrets and protected signing step preserved.

External inventory `/tmp/totipo-wave2b-final-inputs.jsonl`: **174 files**, canonical
sorted UTF-8 JSONL of relative path, permission mode and SHA-256, sorted keys,
compact separators and LF. SHA-256:
`aae01c50e335423932972bf4248f74151c89551718af4e87d2532e1c34c4dbb6`.
Summary `/tmp/totipo-wave2b-freeze.json`. Covers 168 effective package/evaluator/cache
files plus 6 supplemental workflow/public-signing/release-doc files.

Actual package filtering is unchanged: app/src, gradle, tools and exact enumerated
build/lock/version/license/branding files, excluding symlinks and any .git/.gradle/
build/.direnv component. Evaluator/cache inputs package.nix, flake.nix, flake.lock,
package-deps.json are frozen too; flake release-app sources include tools/release.
New data/test files are included without staging. review reports, root README and
AGENTS are excluded and not otherwise consumed by inspected checks. Only this
excluded report may be finalized after human Nix; an included/supplemental input
change invalidates the freeze and requires the applicable renewed evidence.

## 29. Human Nix result

**PASS, human-reported.** After the single final request against the frozen
inputs, the user replied: “nix flake check passed”. The required command was:

```sh
nix flake check path:.
```

One human invocation is recorded; no Nix build/rebuild request, host-cache
prerequisite or historical APK equality condition was imposed. The agent ran no
Nix. This human-reported result supplies the required hermetic JVM/build/package
and packaged release-app wrapper qualification for Wave 2B. No Nix log was supplied
or inspected, and no fresh artifact path/hash was exposed.

Post-result read-only verification confirmed all 174 frozen file contents and modes,
the inventory SHA, original main/HEAD, empty index and protected product/build/device/
version/signer/payload paths. Only this excluded report was finalized afterward;
no included or supplemental frozen input changed and no qualification was invalidated.

## 30. Remaining candidates and limits

Independent Gradle metadata negatives remain a focused harness for an available
offline host; no nested Gradle suite was introduced. Historical device qualification
signer provisioning remains a later candidate and unchanged. General YAML support,
production key rotation, recovery/adoption, new release schemas and dependency
upgrades are outside scope. No live publication or environment-protection check was
performed; mocked/static/local tests preserve the trusted workflow contract.

## 31. Changed files and final Git state

Main remains at starting HEAD; empty index; all modifications unstaged/uncommitted.
Changed tracked files:

- .github/workflows/ci.yml and release.yml (early policy invocation only).
- app/src/test/java/org/totipo/android/OtpAuthSupplyChainGuardTest.java.
- release/README.md.
- tools/release/release.py, source.py, verify-signed-apk.py.
- tools/release/signing-smoke.sh and test-build-metadata.py.
- tools/release/test_source.py, test_release.py, test_publication.py.

New: tools/release/reviewed-inputs.sha256, tools/release/test_policy.py and this report.
No stage/commit/push/tag/release/workflow dispatch or production signing occurred.
No product/device/dependency/version/certificate byte changed. Final human Nix
PASS and post-result inventory recheck are complete. This excluded report is the
only post-Nix edit; effective package/check inputs remain unchanged. Wave 2B is complete.

## 32. Suggested commit subject

`Consolidate Android release support tooling`


## Post-completion clarification: host Gradle executable versus dependencies

After completion, the user correctly challenged the broad availability label and
requested trying `gradle` directly. `command -v gradle` resolves to the pinned
Nix-provided Gradle 9.8.0 executable, and `gradle --version` **PASS**, reporting
Gradle 9.8.0 and JDK 17. No system-wide Gradle installation is required. The repo
wrapper is a separate launcher, not the Android plugin/dependency distribution.

One focused command was then actually attempted:

```sh
gradle --offline --no-daemon --no-configuration-cache :app:testDebugUnitTest --tests org.totipo.android.OtpAuthSupplyChainGuardTest
```

**FAIL before tests**, 11 seconds, at root build.gradle.kts line 1: cannot resolve
`com.android.application:com.android.application.gradle.plugin:9.4.0` from the
configured plugin repositories in offline mode. Log:
`/tmp/totipo-wave2b-direct-gradle.log`. No online dependency resolution, cache
preparation or build-input edit was performed. Gradle's ordinary invocation may
initialize its own runtime directories; that is separate from populating missing
Android dependencies. The precise status is: **Gradle executable available;
host offline Android plugin/dependency resolution unavailable**.

The earlier label and assumption were too broad; direct executable availability
should have been distinguished and tested initially. This follow-up supplies
observed failure evidence without claiming a host Gradle PASS. The required human
Nix PASS remains valid: only this excluded report was edited, and all 174 frozen
input contents/modes were rechecked unchanged after the direct attempt.
