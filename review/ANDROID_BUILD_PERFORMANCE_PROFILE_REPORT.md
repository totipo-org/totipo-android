# Android build performance investigation

## Scope and starting state

- Classification: DOCS_ONLY deliverable; read-only build/test profiling.
- Starting branch: `main`; HEAD: `7385b855a70db9276d6a6e1a7f42927b63d218d2`.
- Starting `git status --short`: empty.
- Inspected AGENTS.md, README, docs/DEVELOPMENT.md, root/app Gradle scripts,
  gradle.properties, settings, package.nix, flake.nix, CI, the previous
  TWO_TEST_WORKERS_REPORT, test XML, and affected fixture/test implementations.
- The committed setting is `maxParallelForks = 4`, with JUnit 4.13.2.
  This is test process parallelism; `org.gradle.parallel` is not enabled.
- No production, test, build, dependency, or qualification-tool source edits.
  The only intended Git change is this excluded review report.

## Method and limitations

Used the active pinned Nix-provided Gradle 9.8.2, JDK 17.0.21 and Android SDK.
Machine: Intel i5-1135G7, four cores/eight logical CPUs, approximately 31 GiB RAM.
No Nix commands, clean, rerun-tasks, dependency refresh, or verification-policy
changes. Existing test XML was copied before execution.

Ignored local evidence is in `.qualification/build-profile/`. Two local init
scripts disable only the debug test task's up-to-date/cache reuse so the suite
actually executes. Other tasks retain normal incremental/cache behavior. Neither
script is an included package input or intended commit content.

1. Initial offline full-profile attempt failed before task execution: pinned
   AGP 9.4.0 plugin resolution was unavailable to the offline invocation.
   Preserved `build.log` and `run.json` (13.70 seconds). This is environment
   resolution failure, not a test failure.
2. Retried normal online resolution with `gradle --profile --console=plain
   -I .qualification/build-profile/profile.init.gradle check
   :app:assembleDebug :app:assembleRelease`. PASS in 203.94 seconds;
   90 actionable tasks, 13 executed/77 up-to-date. Attached 30-second JFR
   `settings=profile` recordings to all four active test JVMs. These timings
   include instrumentation, partly cold configuration/transforms, and lint
   contention; they are not a clean-build or controlled before/after benchmark.
3. Ran the fresh debug suite separately, offline, with the timing init script
   and `--profile`; no JFR attached. This separates the test timing from the
   first run's profiling and lint work. The listener logs class start/end,
   executor assignment, count and duration.

Gradle HTML profiles are in `build/reports/profile/`. Task durations can overlap;
their sum is not build wall time. An UP-TO-DATE task can still spend time on input
resolution, transforms and fingerprinting. No cold compilation/clean-package
speed claim is made from these incremental builds. No CI benchmark was performed.

## Full-build profile

| Task | Seconds | Outcome |
| --- | ---: | --- |
| :app:testDebugUnitTest | 131.080 | executed, PASS |
| :app:lintAnalyzeDebug | 39.630 | executed |
| :app:lintAnalyzeDebugUnitTest | 24.957 | executed |
| :app:mergeExtDexDebug | 18.261 | UP-TO-DATE |
| :app:packageDebug | 12.717 | executed |
| :app:mergeExtDexRelease | 12.326 | UP-TO-DATE |
| :app:lintAnalyzeDebugAndroidTest | 8.459 | executed |
| :app:compileDebugJavaWithJavac | 1.377 | UP-TO-DATE |
| :app:lintVitalAnalyzeRelease | 1.061 | executed |

Project configuration: 28.587 seconds. Aggregate artifact transforms: 39.764
seconds; aggregate task execution: 252.65 seconds, including overlapping tasks.
The test task alone occupies 64.3% of this run's wall time, and is the largest
individual task. Lint is the next substantial executed work. Preserve all lint
scopes and Maven/build-classpath checks.

## CPU and allocation evidence

Across four 30-second JFR recordings, 7,979 of 8,328 execution samples (95.81%)
contain Argon2 code. The per-JVM proportions are 94.2–98.5%. Hot frames are
Bouncy Castle `Argon2BytesGenerator` block mixing, segment filling and block
allocation. This is sampled runnable execution, not 95.81% of total elapsed
wall time or a measurement of the number of authentications.

The fixture calls real `ForegroundVaultCoordinator.create/open`; its operations
call the released Java implementation. Repeated create/unlock/authenticate work
therefore repeatedly performs real password derivation. The profile supports
crypto execution as the primary CPU cost; it does not support blaming the 5 ms
condition polling for most of the suite duration.

JFR observed 128/147/186/115 GC events respectively, with aggregate pauses of
1.425/2.102/2.089/1.581 seconds in each JVM's recording and maximum individual
pauses below 56 ms. These are overlapping JVM intervals, not additive build-wall
seconds. `jcmd VM.flags` confirmed a 512 MiB maximum test heap. GC is measurable
but secondary to Argon2 execution. Four larger heaps require evaluating total
RSS alongside Gradle/lint, particularly on hosted CI.

## Fresh suite without JFR

PASS: 113.27 seconds command wall time; test task **112.130 seconds**; 51 classes,
535 tests, zero failures/errors/skips. Exact `(class, method)` test inventory,
including multiplicities, matches the saved pre-investigation XML. Warm project
configuration took 0.194 seconds and transforms 0 seconds. Do not attribute the
entire difference from the first run to JFR: lint, cache/JIT state and concurrency
also differ.

| Class | Tests | Seconds |
| --- | ---: | ---: |
| AndroidVaultControllerTest | 91 | 76.762 |
| SecurityControllerTest | 27 | 38.044 |
| ControllerRetirementTest | 26 | 27.551 |
| TotpControllerTest | 29 | 21.162 |
| BackupRestoreFlowTest | 10 | 11.253 |
| RestoreJourneyTest | 11 | 7.658 |
| sync.VaultBootstrapTest | 12 | 7.616 |
| reconcile.TotpCoordinatorTest | 12 | 7.586 |
| reconcile.ForegroundVaultCoordinatorTest | 23 | 7.379 |
| reconcile.ImmutableCandidateImporterTest | 12 | 5.533 |

The listener confirms the worker imbalance; times are relative to first class
start, and include small dispatch gaps:

| Executor | Classes | Sum of class durations (s) | Last class finished (s) |
| --- | ---: | ---: | ---: |
| 5 | 13 | 30.046 | 30.086 |
| 6 | 13 | 41.142 | 41.185 |
| 7 | 13 | 111.245 | 111.312 |
| 8 | 12 | 58.047 | 58.105 |

Executor 7 ran `AndroidVaultControllerTest` for 76.763 seconds and then
`ControllerRetirementTest` for 27.551 seconds, plus smaller classes. During the
last approximately 53 seconds, only this worker still had assigned test classes.
These are test-worker lifetimes, not continuous CPU utilization. Class count is
roughly balanced; duration is not. The instrumented and saved pre-investigation
runs show the same large-controller-then-retirement pattern.

Summed XML suite duration is 240.469 seconds. A simplified fixed-duration model
would have an ideal four-worker floor of 60.117 seconds, versus a current largest
indivisible class of 76.762 seconds and a measured task duration of 112.130
seconds. Splitting and balancing therefore has substantial headroom (tens of
seconds). This is not a forecast of a 60-second run: duration changes with
contention, JIT/GC, setup and task overhead, and redistribution needs benchmarking.

Slowest individual methods:

| Class.method | Seconds |
| --- | ---: |
| AndroidVaultControllerTest.outboundBlockedCreateWriteLockAndBindingChangeRemainResponsiveAndBounded | 5.328 |
| AndroidVaultControllerTest.dailySyncBlockingObservationNeverPublishes | 5.189 |
| RestoreJourneyTest.cancellationBackgroundExpiryAndLostAdmissionAtEachContinuationNeverOpen | 3.668 |
| ControllerRetirementTest.platformGrantAndHintIoCannotHoldTheMonitorNeededForImmediateRetirement | 3.253 |
| ControllerRetirementTest.providerPublicationPhasesRemainAsOperationsUntilActualReturnBeforeB | 3.228 |
| BackupRestoreFlowTest.everyRegistryWriteBoundarySurvivesPrewriteAndLostAcknowledgementInterruption | 2.642 |

The slowest outbound method covers six create/write × lock/disconnect/replace
combinations with real setup and blocked publication latches. Keep every
combination; its duration is not evidence of a redundant sleep to delete.

## Improvements that retain assurances

1. **Split the oversized controller test class into independent classes by
   subsystem**, retaining every test body, assertion, real backend, per-test
   TemporaryFolder, setup/cleanup and latch. Aim initially for several classes
   around 15–25 seconds each rather than one 90+ second class. Keep shared fixture
   helpers in a non-test base/helper; prevent inherited test duplication. This
   exposes existing work to the four process workers without changing product
   scheduling or cryptographic work. Consider splitting SecurityControllerTest
   next if it becomes the longest indivisible unit. Existing instances use
   separate temporary roots and mostly instance-owned state; audit all shared
   state and resource retirement before claiming independence.
2. **Measure a 512 MiB versus 1 GiB test-heap experiment**, keeping four forks
   and the full test inventory fixed. Record wall time, GC and peak RSS over
   repeated comparable runs and on the actual CI runner. Accept only a repeatable
   improvement within the memory budget. The recordings suggest a smaller
   opportunity than class redistribution; increasing the Gradle daemon heap
   does not change the separate test JVM heap.
3. **After redistribution, profile redundant fixture setup**. A test that only
   needs an already-existing vault could copy immutable fixture bytes into its
   own fresh root and still perform real open/observation. This can avoid a
   setup-only create derivation, but requires reviewing each test's preconditions:
   fixture identity, tokens, persistence knowledge and mutable roots must remain
   independent. Creation, wrong-password, empty-password, biometric credential,
   wipe, backup authentication and failure/retirement tests must retain their
   relevant real operations. This is a candidate requiring proof, not an
   implemented or coverage-equivalent optimization established by this report.
4. **Keep local Gradle caches warm**, including dependency transforms, for normal
   iteration. Inspect configuration/transforms again under warm conditions before
   changing build logic. Configuration cache is explicitly disabled because the
   verification task resolves configurations at execution; enabling it requires
   making those tasks compatible and proving the same checks still run. It
   would not remove the crypto-heavy test task. Nix qualification remains fresh
   and retains its current cache/verification policy.

Do not weaken Argon2 parameters, mock all secret/session operations, reuse live
sessions or mutable vault directories across cases, drop cases/lint, shorten
race coverage, or replace latches with sleeps to obtain a lower number. More
forks on this four-core machine may add CPU/memory-bandwidth competition and
cannot divide one JUnit class. Method parallelism inside these mutable fixtures
would require a separate safety design; it is not a safe switch to flip.

Gradle documents that `maxParallelForks` starts processes executing classes,
subject to the worker limit, and warns about isolation and frequent JVM forks:
[Gradle Test API](https://docs.gradle.org/current/javadoc/org/gradle/api/tasks/testing/Test.html)
and [Java testing](https://docs.gradle.org/current/userguide/java_testing.html).
Keep `forkEvery` at its default unless a demonstrated leak justifies restarts.

For any follow-up implementation, compare the complete discovered test inventory
and zero failures/errors/skips, then run focused tests plus the unchanged final
normal/strict/artifact and human Nix gates required by AGENTS.md. Preserve the
real controller/session/store/lifecycle and admission assertions. No device gate
is needed solely for reorganizing JVM tests, unless behavior or artifacts change.

## Input freeze, qualification and Git

- Production source stable: unchanged.
- JVM tests stable: unchanged; profiling results recorded below.
- Build/package configuration stable: unchanged, including four test forks.
- Qualification harness/tooling stable: no included tooling changes; experimental
  scripts/recordings remain ignored local evidence.

`package.nix` selects app/src, gradle, tools and explicit build/metadata files;
its filter excludes review/. The separate flake release checks consume explicit
release/tool/build/signing/workflow inputs, not this report. Therefore this
report-only change does not invalidate prior Gradle/artifact/Nix evidence, and
requires no new final strict build, APK verification, device qualification or
human Nix invocation. The measured ordinary full build is investigative evidence,
not a claim to have repeated the entire release qualification ladder.

Execution counts: baseline full 0; profiling full 1 successful + 1 failed during
configuration; focused full debug-suite profiling 1; focused compile 0; final
normal qualification 0; strict-clean 0; artifact verifiers 0; embedded APK
verifiers 0; physical harness 0; human Nix 0. The extra invocation after the
configuration failure restored normal dependency resolution; the second fresh
suite isolates uninstrumented timing. No existing PASS was invalidated by a
source edit. No content staged and no commits created.

Final documentation validation: local Markdown links resolve; `git diff --check`
and `git diff --cached --quiet` pass. New report established as Git-known with
intent-to-add only. Final `git status --short` / `git diff --name-only` identify
only `review/ANDROID_BUILD_PERFORMANCE_PROFILE_REPORT.md`;
`git diff --cached --name-only` is empty. Local `.qualification/build-profile/`
and generated build profiles remain ignored. No source inputs or prior
qualification passes were invalidated. No human result is required for this
excluded report-only milestone.
