# Agent development policy

## Qualification ladder

**Full qualification at stable boundaries. Focused validation during iteration.**
This controls interactive agent work; CI continues to run the package-inclusive
`nix flake check` and its unchanged-input check. Do not weaken final evidence to
reduce invocation counts. Use the pinned environment described in README.

### Classify before editing

Start from clean `main` and record `git branch --show-current`, `git rev-parse HEAD`,
and `git status --short`. If this prerequisite is not met, resolve it with the user
without discarding existing work. Inspect current instructions, README, package.nix,
flake.nix, CI, relevant tools, and subsystem qualification evidence.

Before editing, classify the milestone as one or more of the following and record
the classification in its report. Update it if scope changes.

| Classification | Focused validation selection |
| --- | --- |
| DOCS_ONLY | Documentation links/consistency and `git diff --check`; inspect whether docs are package/check inputs |
| TEST_OR_QUALIFICATION_TOOLING | Affected JVM tests, verifier or standalone harness checks |
| PRODUCT_CODE | Affected compilation and controller/unit tests |
| UI_ONLY | Affected row/view tests and focused UI/device smoke |
| LIFECYCLE_SECURITY | Deterministic lifecycle/security tests and relevant device harness |
| BUILD_DEPENDENCY | Dependency, lock, toolchain and Maven-boundary checks |
| PACKAGING_RELEASE | Package, APK, signing/release boundary checks appropriate to scope |

Classification selects focused checks; it never waives the final gates for changed
production, test or build inputs. A UI edit to production source still requires them.
For tooling-only work, use the invalidation matrix below to determine affected gates.

### Starting baseline

For ordinary PRODUCT_CODE, UI_ONLY or LIFECYCLE_SECURITY work, run once:

```sh
./gradlew check :app:assembleDebug :app:assembleRelease
```

Do not use `clean`, `--rerun-tasks` or `--no-build-cache` at baseline. The starting
commit is already qualified; the baseline detects a broken checkout rather than
reproducing prior release evidence. DOCS_ONLY work outside package/source/check
inputs uses the narrowest applicable validation, without assembling APKs.
BUILD_DEPENDENCY or PACKAGING_RELEASE milestone instructions may deliberately
require a stronger baseline.

### Implementation and failure diagnosis

During iteration, use no `clean`, no `--rerun-tasks`, no full APK verification after
each edit, and no human Nix. Prefer compilation and focused tests, for example:

```sh
./gradlew :app:compileDebugJavaWithJavac
./gradlew :app:testDebugUnitTest --tests 'org.totipo.android.SomeFocusedTest'
```

The test class above is a placeholder; select the real affected tests. A row change
calls for row/view regressions; Sync changes call for Sync controller tests;
lifecycle/security changes call for lifecycle/security tests and harnesses. With
no row change, do not automatically run the 8k+ row harness during iteration.
Use deterministic latches, clocks and fakes to reproduce failures instead of
repeating broad runs. Run only affected device harnesses. Do not repeatedly install
on a physical device unless needed to diagnose a physical-only failure.

If a full gate fails, preserve command/output and failure evidence, reproduce with
the narrowest deterministic command, then fix using focused tests. Do not immediately
rerun every gate. Return to the full gate only when the focused failure is stable.
Record why each extra full invocation was necessary, including invalidated passes.

### Final Gradle and artifact gates

When production/tests are believed stable, run the final normal checkpoint once:

```sh
./gradlew check :app:assembleDebug :app:assembleRelease
```

Any subsequent production/test/build input change invalidates that checkpoint.
After final normal PASS, run the fresh/hermetic strict gate once:

```sh
./gradlew \
  --offline \
  --no-daemon \
  --no-configuration-cache \
  --no-build-cache \
  --rerun-tasks \
  --dependency-verification=strict \
  clean check :app:assembleDebug :app:assembleRelease
```

The strict-clean command belongs only at this stable boundary. Against its outputs,
run one final external artifact-verification set:

```sh
python3 tools/verify-wrapper.py
python3 tools/verify-apk.py \
  app/build/outputs/apk/debug/app-debug.apk \
  --debug-probe
python3 tools/verify-apk.py \
  app/build/outputs/apk/release/app-release-unsigned.apk \
  --unsigned --no-debug-probe
./gradlew --offline :app:dependencies --configuration releaseRuntimeClasspath :app:verifyMavenBoundary
```

Review the current runtime graph and manifest boundaries. `check` already includes
JVM tests, lint, `verifyMavenBoundary` for both variants and
`verifyReleaseApkBoundary`; retain these existing checks. The external final set
explicitly verifies both strict-clean APKs, including packaged manifest permissions,
services, ingress/backup, branding, DEX and release/debug exclusions. Do not verify
intermediate APKs except to diagnose an APK-specific failure.

### Device qualification and Nix source freeze

Run only physical/device qualification relevant to changed subsystems: focused
UI/device smoke for UI changes, security/lifecycle harness for biometric/lifecycle
changes, and focused transport/provider smoke for Sync changes. Do not automatically
repeat full M3D Syncthing qualification for unrelated UI work. Develop and fix any
required physical harness before the final Nix gate if it is a Nix source input.
Final device evidence must correspond to the final qualified production artifacts.

Inspect actual source filtering and evaluator/check dependencies; never infer input
membership from file type. Currently `package.nix` selects trees `app/src`, `gradle`,
and `tools`, plus these exact files:

```text
build.gradle.kts  settings.gradle.kts  gradle.properties
app/build.gradle.kts  app/gradle.lockfile
buildscript-gradle.lockfile  app/buildscript-gradle.lockfile
VERSION  LICENSE  branding-provenance.json
```

The filter excludes any `.git`, `.gradle`, `build` or `.direnv` path component and
symlinks. JVM tests and all selected `tools/` files (including harness prose) are
inputs. `package.nix`, `flake.nix`, `flake.lock` and `package-deps.json` also affect
qualification through evaluation/cache inputs, even though absent from the filtered
Gradle source. `AGENTS.md`, `README.md` and `review/` are currently excluded and are
not otherwise consumed by the package/check. A raw `path:.` flake snapshot can contain
those docs; their edits do not change the effective Android check inputs. Reinspect
these rules for each milestone, especially after build/filter changes.

Before asking the human for Nix, record all four freeze statements with evidence:

- Production source stable.
- JVM tests stable.
- Build/package configuration stable.
- Qualification harness/tooling stable.

Freeze every included source and evaluator/cache input, including qualification
tools. Only then request exactly `nix flake check path:.`, one final routine time.
The agent does not run Nix. Do not request `nix build` or `nix build --rebuild` as
routine gates. A successful check is invalidated by any later included-input edit;
repeat only after refreezing and record the reason. Excluded report-only edits do
not invalidate Nix; record that determination in the report.

### Invalidation matrix

Apply actual dependencies if they differ from today's layout. After the strict
gate, use this matrix; a real invalidation overrides the invocation-count target.

| Later edit | Required response |
| --- | --- |
| Production source/resources/manifest | Focused checks, renewed final normal and strict Gradle gates, artifact verification, affected device qualification, final human Nix |
| JVM tests or Gradle build inputs | Focused checks, renewed final normal and strict Gradle gates and final artifact checks; affected device checks if behavior/artifacts changed; final human Nix |
| Build/package/dependency configuration | Rerun affected final Gradle/artifact gates; package-only inputs need no Gradle rerun unless consumed by build/tests; final human Nix |
| Device harness outside Gradle source | Rerun affected harness; no Gradle rebuild solely for harness code/prose unless packaged/tested by Gradle; final human Nix because `tools/` is included |
| APK verifier | Rerun verifier against final APKs; rebuild only if APK/build inputs changed or the failure requires it; final human Nix because `tools/` is included |
| AGENTS.md / README.md / report-only | Documentation validation and `git diff --check`; no Gradle or Nix invalidation under current rules |
| Any other included flake source or evaluator/check input | Invalidate Nix; determine Gradle/device effects from actual consumers |

### Reports and execution counts

Every substantial milestone report records classification, starting HEAD, selected
checks, results/failure evidence, input freeze and invalidations, changed files,
human results where required, final Git state, and these execution counts:

| Count | Clean product milestone target |
| --- | --- |
| Baseline full Gradle invocations | 1 ordinary full |
| Focused Gradle invocations | As needed; separate test, compile and boundary checks |
| Final normal full invocations | 1 ordinary full |
| Strict-clean invocations | 1 offline clean |
| Artifact-verifier invocations | 1 final set: wrapper + debug APK + release APK (3 external commands); record embedded Gradle verifier executions separately |
| Physical harness invocations | Relevant subsystems only; count each phase/run |
| Human Nix invocations | 1 final routine check |

Explain every repeated full gate. These targets do not permit skipping invalidated
gates. DOCS_ONLY reports may record zero builds/verifiers/device/Nix runs with the
source-filter justification and lightweight validation results.

The empirical basis is the unchanged
[biometric milestone report](review/ANDROID_BIOMETRIC_INACTIVITY_LOCK_REPORT.md),
sections 23–25: five ordinary full Gradle runs, one strict-clean run, twelve focused
test runs plus one compile, ten physical phase runs, and four human Nix results.
Its final Nix PASS followed harness fixes because `tools/` is included. Preserve its
final evidence strength while moving diagnosis and tooling fixes before frozen gates.

## Totipo Java application-operation model

Before changing scheduling/concurrency or code involving VaultState, state subscriptions, asynchronous
projection, TOTP reveal/generation, operation admission, global BUSY/enabled-state
policy, Sync, requestRefresh/observation, Add/Edit/Delete/Resolve, save/publication,
retry/partial publication, Join/open/create, biometric/password open, Lock/inactivity
timeout, process/activity lifecycle, session replacement, or provider/store scheduling,
read the exact reviewed Java guidance at commit `f0a028676c1801a10b2d8d2650bf9c24357d1fa1`:

[API_DESIGN.md](https://github.com/totipo-org/totipo-java/blob/f0a028676c1801a10b2d8d2650bf9c24357d1fa1/API_DESIGN.md),
especially **Operation classes and state-snapshot semantics**, **Replay-latest stream**,
**Editing and deterministic causal bases**, **Merge freshness and partial resolution**,
**Persistence knowledge and handles**, and **Blocking, threading and close**.
Agents changing Sync/observation behavior must also read **Observation-pass ordering
and composition** at that exact revision. Android's import-before-publish observation
barrier is supported only under the assumptions documented in that pinned contract;
`requestRefresh()` returning is not observation completion or an acknowledgement.
It remains nonblocking, coalescible and without request/result correlation.
See [the F08 follow-up](review/ANDROID_JAVA_GUIDANCE_PIN_REFRESH_REPORT.md) for
Android's assumptions and the local-only guarantee.
If Java is unavailable locally, fetch/read this exact revision read-only from GitHub.
Do not substitute Java `main` or only the earlier operation-model commit.

The runtime artifact/source pin and guidance pin serve different purposes:
Android executes Totipo Java 0.2.0 released source
`d6310c177ae930df188fd4f5798622c935698b2e`; the guidance is a later reviewed
clarification of those same semantics. A docs-only clarification requires no Java
dependency upgrade. See [Java dependency provenance](TOTIPO_JAVA_DEPENDENCY.md)
and [the Android operation audit](review/ANDROID_JAVA_OPERATION_MODEL_AUDIT.md).

**A newer VaultState is not, by itself, a generic cancellation signal.**
State emission alone does not require cancelling local projection, rejecting
historical same-session references, serializing every operation, disabling the
whole UI, or rebasing builders. Use session/lifecycle ownership, explicit
request/presentation generations, token identity, operation-specific freshness
rules, and Java result types as appropriate. Distinguish local projection validity
from presentation relevance; descriptive reads survive close, secret-backed work
requires its owning session to remain open.

**Do not map every Totipo Java operation onto one Android global BUSY state.**
First classify the Java operation, including each phase of a composite action:

| Class | Java operation |
| --- | --- |
| A | Immutable/descriptive projection |
| B | Local secret-backed projection |
| C | Local state construction |
| D | Observation request |
| E | Freshness-gated publication |
| F | Frozen publication/continuation |
| G | Session/storage lifecycle |
| H | Independent candidate validation, where used |

Then apply Android controller/lifecycle/UI policy. A stricter Android policy is
allowed, but document it as an application choice, not as Java correctness.
Provider transport and coordinated local-store ownership have their own safety
requirements; neither turns every Java call into a freshness-gated operation.

Subscriber threading has two distinct phases:

```text
states().subscribe(...)
  -> onSubscribe synchronously on the subscribing thread
later onNext/onError/onComplete
  -> asynchronous serialized publisher/common-pool drain per subscription
```

Different subscribers can execute concurrently. Asynchronous delivery can begin
before subscribe returns; it promises no fixed worker or latency. Android must
not assume every callback arrives from the same thread category. Establish demand
and subscription ownership safely in onSubscribe; marshal presentation callbacks
to Android main. Review controller/coordinator ownership and Activity delivery
separately from Java callback delivery.
