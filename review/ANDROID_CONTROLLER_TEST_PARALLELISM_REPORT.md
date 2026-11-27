# Controller test redistribution and heap measurement

Redistributed 91 controller tests into seven independent concrete classes. The
final-source isolated suite took 91.641 seconds versus the earlier unsplit
112.130-second observation (18.3% shorter locally). All 535 tests remain.
One-gigabyte test heaps showed no repeatable benefit; the default heap and four
workers are unchanged. Three fixture scheduling assumptions exposed during
qualification were corrected without changing product code or APK bytes.
Final normal/strict/artifact gates pass; the user reported final human Nix PASS.

## Scope and starting state

Classification: TEST_OR_QUALIFICATION_TOOLING. Heap changes are measurement-only
unless explicitly recorded otherwise; no product operation policy changes.
Started on `main` at `7385b855a70db9276d6a6e1a7f42927b63d218d2`.
The existing intent-to-add `review/ANDROID_BUILD_PERFORMANCE_PROFILE_REPORT.md`
is the report from the immediately preceding investigation; the user's request
continues that work. Preserve it, with no staged content or commits.

Inspected AGENTS.md, README, docs/DEVELOPMENT.md, package.nix, flake.nix, CI,
Gradle configuration, prior qualification evidence and profiling results.
Reviewed the pinned Java operation/observation guidance and released API design
from the saved exact-revision copies used in the previous qualification.
No session, observation, admission, lifecycle or provider behavior is changed.

No ordinary product baseline: this changes test organization only, and the
immediately preceding investigation freshly passed all 535 tests and the ordinary
full build on the same starting production/test sources. Changes to JVM test
inputs still require final normal, strict, artifact and human Nix gates.

## Implementation

The former 91-method AndroidVaultControllerTest becomes seven concrete classes:

| Class | Tests |
| --- | ---: |
| AndroidVaultControllerTest | 19 |
| AndroidVaultControllerInboundTest | 14 |
| AndroidVaultControllerPublicationTest | 9 |
| AndroidVaultControllerAuthoringTest | 10 |
| AndroidVaultControllerTokenLifecycleTest | 18 |
| AndroidVaultControllerSyncRetirementTest | 6 |
| AndroidVaultControllerAutoSyncTest | 15 |

AndroidVaultControllerFixture is abstract, with no @Test methods. It contains the
existing instance-owned fields, TemporaryFolder rule, @Before/@After methods,
real backend and helpers. Top-level private fixture members become protected for
subclass access; bodies and defaults remain unchanged. Each JUnit method still
gets a fresh concrete instance, temporary directory, backend and controller.
The only static helper is the existing stateless latch helper; no shared live
session, mutable vault root, or reusable secret state is introduced.

The three timeout tests that invoke daily retirement cases stay in the same
concrete class as those cases. No inherited @Test methods exist to duplicate
coverage. At initial extraction, all 91 original test method declarations and bodies were mechanically
verified byte-for-byte exactly once in their new destination, with an explicit
method-to-class map saved in ignored local evidence.

No production, resource, manifest, dependency, package, CI or external harness source
changes. Four Gradle test workers remain configured. No cryptographic shortcuts,
removed cases, reduced timeouts, or weakened assertions.

## Validation and measurement plan

Local evidence and experimental scripts: `.qualification/controller-test-split/`.
These remain ignored and outside Git/package input membership.

1. Focused seven-class run: PASS, 91 tests, Gradle reports 35 seconds;
   compilation and execution passed after redistribution.
2. Complete fresh-suite heap experiment in ABBA order: 512 MiB, 1 GiB, 1 GiB,
   512 MiB, all with four forks and identical GC logging/listeners. Only local
   init scripts override task cache/up-to-date reuse and heap size. No clean,
   rerun-tasks or JFR during measurement. Fresh test JVMs for each run.
3. Compare the complete discovered method inventory with saved starting XML,
   normalizing only the 91 moved methods' class names. Require all 535 tests,
   exactly once, with zero failures/errors/skips for every successful full run.
4. Final normal checkpoint; final strict offline clean checkpoint; both external
   APK verifiers against strict outputs; offline runtime graph/Maven boundary.
5. No physical device qualification: no product behavior/artifact boundary edit.
   Human `nix flake check` only after all included inputs freeze and are Git-known.

Benchmark metrics: task and process wall time, per-worker GC pause counts/sums,
and 200 ms sampled aggregate RSS for test JVMs and all Gradle JVMs. RSS sums
count shared pages more than once, sampling may miss brief peaks, and other host
work/CPU contention can affect results. The previous unsplit 112.130-second task
is a single historical comparison; no guarantee for hosted CI is inferred.

## Review and configuration decision

The extraction verifier also compared all remaining fixture code with the
original, allowing only the class declaration, private-to-protected access,
fixture Javadoc and whitespace. No helper logic changed. The saved original
matches the exact HEAD version. There are no class-name reflection dependencies
on the old concrete test class; reflection continues to inspect product classes.

The first 1 GiB run measured 85.607 seconds, but the second took 97.844 seconds
versus the first 512 MiB run's 88.662 seconds. This does not show a consistent
benefit. Retain the existing default test heap and four-worker build settings;
no Gradle/package/dependency input changes are warranted by this experiment.
Full repeated results and final gates follow below.

## Failure diagnosis during the fourth measurement

The final ABBA 512 MiB run failed: 535 tests executed, one failure in unchanged
`HistoryImportControllerTest.partialUncertainAndExplicitReselectionRecoveryNeverPublishPrematurely`,
original line 139 (`assertNotNull(wrong)` after `beginHistoryImport()`). The
process took 100.700 seconds; it is not a passing performance sample. Logs, XML,
and sampled memory were preserved in `a2-512m/` before focused reproduction.
No final normal/strict gate had started; the conditional qualification launcher
refused to proceed on the failure.

One original-method focused reproduction passed in 1 second. Do not claim the
failure reproduced in isolation. Source inspection found a readiness/admission
race consistent with the observed null: `canAddHistory()` checks `!viewQueued`,
but a released Java state callback can schedule presentation after the readiness
check and before `beginHistoryImport()` rechecks admission.

The history-import fixture now obtains positive admission under the controller
monitor: await readiness, then assert a single non-null begin call in the same
critical section. It waits only while readiness is false; a rejected begin when
ready still fails immediately. All six intended-positive begin call sites use
this helper. Deliberate duplicate/rejection assertions and apply/publication
calls remain unchanged; no admitted operation is retried, no callback is
suppressed, and no timeout increased. Real import, recovery, observation and
retirement work remains exercised.

The fixed entire HistoryImportControllerTest class passed in 4 seconds (13 tests).
The earlier 91-method extraction/fixture-equivalence proof still holds for the
controller split. The history-import fixture is the separately documented test
logic adjustment; do not claim every repository test body remained byte-identical.

This JVM-test change makes the prior three passing timing samples historical
exploratory evidence rather than measurements of the final exact inputs. The
heap benefit was inconsistent already, so keep the default heap; do not repeat
all heap experiments solely to chase a speedup. Run one fresh default-heap full
suite after the focused fix, then the final normal/strict/artifact gates once.
The final source must pass every gate; performance is not a substitute.

Extracted-method fingerprint (concatenated exact declarations/bodies sorted by
method name): SHA-256 `85c813a92e3613e60e21e082c28693156e9b800485d1e40359a2645d6cfe2629`. The equivalence check still
passes after the separate history-import fixture adjustment.

## Measurement results

| Run | Heap per JVM | Test task (s) | Command (s) | Peak test JVM RSS (MiB, summed) | Peak all Gradle RSS (MiB, summed) | Result |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| a1-512m | 512m | 88.662 | 89.584 | 2208.5 | 4558.1 | PASS |
| b1-1g | 1g | 85.607 | 86.550 | 2481.1 | 4841.7 | PASS |
| b2-1g | 1g | 97.844 | 98.814 | 2487.1 | 4851.9 | PASS |
| a2-512m | 512m | — | 100.700 | 2193.2 | 4545.9 | FAIL (history admission) |
| a3-fixed-512m | 512m | 87.756 | 88.655 | 2255.2 | 4612.5 | PASS |

The post-fix fresh suite passed all 535 tests in 57 classes with the complete
normalized inventory unchanged. Its 87.756-second test task is 21.7% shorter than
the prior unsplit 112.130-second observation; the first split observation was
88.662 seconds (20.9% shorter). These are local observations on the same host,
not a controlled CI speedup guarantee. The post-fix default-heap run is outside
the original ABBA sequence, and the failed run is excluded from passing timing
comparisons. Do not represent this as two successful default-heap ABBA samples.

The 1 GiB runs (85.607 and 97.844 seconds) do not demonstrate a repeatable gain.
Peak aggregate test RSS was approximately 2.42–2.43 GiB, versus 2.16–2.20 GiB
for passing 512 MiB runs. Retain the default heap. The larger cap also permits
more memory use than observed here; no hosted-CI memory or performance claim.

Aggregate GC stop-the-world pause sums across workers were 13.378 seconds in
a1, 10.335 seconds in b1, 8.580 seconds in b2 and 13.006 seconds in a3. These
worker pause totals overlap and cannot be subtracted from build wall time.
Less measured GC with 1 GiB did not consistently reduce task duration.

At the default-heap a1 observation, the seven controller classes took 5.268 to
20.311 seconds each, replacing the prior 76.762-second indivisible class. Worker
finish times were 48.75, 59.51, 74.55 and 87.92 seconds from first class start,
versus the prior unsplit 30.09, 41.19, 58.11 and 111.31 seconds. Imbalance remains;
SecurityControllerTest (approximately 36–40 seconds) is now the biggest candidate
for a separate future split. No further test classes are changed in this milestone.

## Strict-gate failure and deterministic manual-provider fixture

The first final normal gate passed in 91.803 seconds (90 tasks: six executed,
84 up-to-date). The first strict offline clean gate then failed in 132.897
seconds (39 tasks executed) with one failure among the 535 tests:
`AndroidVaultControllerInboundTest.manualProviderImportUsesWorkerSameSessionAndHonestCoverage`,
line 63, expected UNAVAILABLE but observed READY after the offline provider call.
The command, XML and preceding normal result are preserved under
`strict-first-failure/`. No artifact checks ran after that failed strict gate.

One unmodified-method focused reproduction passed in 2 seconds; the exact
interleaving was not reproduced narrowly. Source inspection shows a compatible
path: an unrelated Java observation projection can occupy the bounded worker
queue; provider completion may then be rejected, retaining cached READY metadata.
This is the existing controller busy-result policy, not shared temporary-file
state. Tests run in separate JVMs/instances; the legacy core fixture creates a
unique temporary directory. Do not claim the failure proves a new production
regression or a specific observed dispatch-rejection trace.

This one manual transport outcome test now uses the existing observation seam
to defer changed application callbacks while asserting provider coverage/status.
The real Java subscription still delivers into the test's queue; failed/terminal
callbacks are never deferred. After the transport assertions, the test requires
at least one real changed callback, replays all retained callbacks under a gate,
then resumes normal delivery without dropping a raced callback. It waits for
provider/worker completion and additionally checks the same session and all
three observed rows. The finally block always resumes callbacks.

All original assertions in that method remain. Ninety of the original controller
test methods remain byte-identical; this one method adds fixture ordering and
positive replay checks. The initial 91-method fingerprint above is initial
extraction evidence, superseded by this explicit final method exception.
No controller behavior, provider API, timeout or result assertion was weakened.
The separate real asynchronous projection, observation ownership, lifecycle,
provider blocking and bounded-queue tests remain unchanged.

This test-input adjustment invalidates the first final normal checkpoint and
requires a renewed normal and strict gate after focused validation. Repeating
those gates is required by AGENTS.md; the failed strict result cannot qualify the
final inputs. Do not repeat heap experiments: the inconsistent heap comparison
remains exploratory, and the production/default test heap stays unchanged.

Focused fixture iteration: the first whole-method deferral experiment failed the
14-test inbound class in 42 seconds because it also withheld the asynchronous
projection required for the first imported three rows (30-second existing await
expired). That was a test-fixture error, not another product failure; preserved
XML is under `inbound-deferred-failure/`. No full gate was rerun for diagnosis.

The corrected ordering replays change callbacks immediately after the first
import's provider/worker completion, and retains the original asynchronous
three-row assertion. It then defers changes for the remaining no-op/offline
transport cases. A real requestRefresh plus receipt of an actual changed callback
establishes the deferred test phase; returning from requestRefresh is never
treated as observation completion. Existing worker work drains before the next
transport admission. Retained callbacks are replayed after recovery and normal
callback delivery resumes atomically under the fixture gate. The corrected
single method passed in 2 seconds; validate its full class before final gates.

The final full inbound class passed in 12 seconds: all 14 tests passed.
Renewed final normal (with --profile only for timing), strict offline clean,
artifact and runtime/boundary gates now run against frozen final test inputs.

## Second gate results (superseded by backup fixture fix)

| Gate | Result | Seconds |
| --- | --- | ---: |
| final-normal | PASS | 113.919 |
| strict-clean | PASS | 128.796 |
| verify-debug | PASS | 2.462 |
| verify-release | PASS | 1.868 |
| runtime-boundary | PASS | 0.661 |

The renewed normal gate executed 55 tasks, restored three from cache and reused
32 up-to-date tasks. The renewed strict gate executed all 92 tasks. Each gate
freshly passed all 535 tests in 57 classes with zero failures/errors/skips and
the normalized inventory matching the starting 535 methods exactly. Test input
SHA-256 values remained unchanged across the final normal/strict/artifact set.

The optional normal HTML profile was generated, then removed by strict clean;
normal command logs, per-class XML and input hashes were preserved. XML spans
103.033 seconds from first class start to last class finish in that normal build,
which overlapped lint/rebuild work. Use the final isolated measurement below for
task timing; no extra full qualification run was made to regenerate the HTML.

Reviewed runtime graph: storage-nio 0.3.2 -> core 0.3.2 -> BC 1.86 with matching
strict lock constraints and external Maven identities. Reviewed source metadata:
VERSION 0.2.5, VERSION_CODE 10. Manifest boundaries remain biometric permission,
backup disabled, exported launcher and otpauth/text ingress components. Both
external APK verifiers passed against the final strict-clean artifacts.

Final APK SHA-256 values are byte-for-byte identical to the saved pre-change
artifacts:

- Debug: `1088e4d526c555e535af0a823c0fdddcc9e42de108b63d0cd1972f992c26bbe4`.
- Release: `6e8b509b5d09d302087bd744c537d8ab37982d927df7819f483771beb9241b4b`.

A final isolated 512 MiB suite run follows the artifact checks to measure the
exact final fixture inputs with the same experimental logging/sampling as earlier.
It edits no included input and does not rebuild or change either strict APK.
The earlier a3 benchmark predates the last inbound fixture control; do not present
it alone as the final exact-source measurement.

## Additional startup-admission failure exposed by final timing

The post-gate isolated a4 measurement failed in 88.511 seconds (process wall)
with one failure among 535 tests in unchanged
`BackupControllerTest.independentSelectionRejectsOriginatingPickerAfterCompleteRetirement`,
line 179: the single positive `next.create(...)` assertion. The initial commentary
misidentified this as backup admission; the actual failure is creation immediately
after discovery emits NO_LOCAL_VAULT. XML/logs are preserved in
`a4-final-512m/failed-results` and `a4-final-512m/gradle.log`.

The failing call is not proof that creation or backup ran. Discovery publishes its
snapshot before the enclosing worker task releases admission, so the test could
call create while that startup task is still returning. One unmodified narrow
method reproduction passed in 2 seconds; no claim of isolated reproduction.

The test now waits for that controller's worker active count to become zero and
its queue to empty after the absence snapshot, before the original single create
assertion. This selected controller has no live session or configured provider,
so no observation/provider callback can insert new work after discovery finishes.
All independent-vault identity, old/new picker rejection, no-output, retirement
and real creation/backup assertions remain unchanged. No credential operation is
retried and no timeout increased. The fixed complete BackupControllerTest class
passed in 4 seconds (10 tests).

This test edit invalidates the second normal/strict/artifact qualification set,
even though its APKs were unchanged. Its full evidence is preserved under
`second-qualified-inputs/`. A fresh isolated default-heap measurement now runs
BEFORE renewed final normal/strict/artifact gates. No more post-gate timing
experiments are planned. Repeated qualification remains necessary for the
actual final JVM-test inputs; it is not waived for a performance task.

## Final-source timing before qualification

The a5 isolated default-heap run passed all 535 tests in 57 classes, with zero
failures/errors/skips and the complete normalized starting inventory unchanged.
Test task: **91.641 seconds**; process wall: **92.538 seconds**. Peak sampled
aggregate test RSS: **2,239.4 MiB**; aggregate visible Gradle RSS: **4,865.6 MiB**.
Aggregate worker GC pause sum: **13.457 seconds**, overlapping across JVMs.

This final-source task observation is **18.3% shorter** than the prior unsplit
112.130-second task. The comparison is local and exploratory, not a statistical
speedup estimate or CI guarantee. The earlier approximately 22% observation
predates the final fixture adjustments; use 91.641 seconds for the final source.
The heap experiment still supplies no repeatable reason to change the default.

The earlier a4 run failed and is not a passing timing sample. No tests were
removed or skipped to obtain the passing a5 result. Ten changed JVM-source files
are now frozen for renewed normal/strict/artifact qualification. There are no
production/build/harness input edits planned and no more timing runs after these
gates. Report-only updates remain excluded from qualification inputs.

## Final qualification and input freeze

The final a5 timing run was followed by the following gates on identical frozen
inputs. No source edit or timing experiment followed them.

| Gate | Result | Seconds |
| --- | --- | ---: |
| final-normal | PASS | 93.153 |
| strict-clean | PASS | 130.839 |
| verify-debug | PASS | 2.378 |
| verify-release | PASS | 1.993 |
| runtime-boundary | PASS | 0.634 |

Final normal: 90 tasks, seven executed, three from cache, 80 up-to-date. Its
preserved HTML profile records testDebugUnitTest at **92.160 seconds**. Final
strict clean: all 92 tasks executed. Both final gates freshly passed 535 tests
in 57 classes with zero failures/errors/skips; normalized class/method inventory
matches the starting 535 exactly. The final strict APK hashes equal the pre-change
hashes above. Runtime graph, metadata and manifest review remain unchanged.

Freeze evidence:

- Production source stable: no production/resource/manifest change, and both
  verified strict APKs are byte-identical to the saved starting outputs.
- JVM tests stable: the ten changed test/fixture source files have unchanged
  recorded SHA-256 values across the final normal/strict/artifact gates. All 535
  methods passed the pre-final timing run and both final gates.
- Build/package configuration stable: no Gradle, package, dependency, lock,
  SDK/JDK, CI or evaluator changes; four test forks and the default heap retained.
- Qualification harness/tooling stable: no included tools/harness/verifier edits.
  Experimental scripts, logs, XML copies and GC recordings stay under ignored
  `.qualification/` and generated `build/` directories.

Rechecked actual source consumers: package.nix includes app/src, so every new
Java fixture/class is Git-known through intent-to-add. Review reports are
excluded by its filter; the separate flake release checks select explicit
release/tool/build/signing/workflow inputs and do not consume review/. Final
report-only edits do not invalidate Gradle, APK or Nix qualification. No ignored
build/cache/secret files were added to Git source membership.

## Execution counts and repeated-gate reasons

These counts cover this implementation turn, excluding the preceding profiling
investigation recorded in the preserved performance report.

| Category | Invocations |
| --- | --- |
| Ordinary product baseline | 0 (test-tooling scope) |
| Focused Gradle test commands | 16 total: nine focused class/method commands and seven complete-suite timing attempts |
| Focused compile-only commands | 0 (compilation included in test commands) |
| Final normal full gates | 3 PASS; first two superseded by later fixture fixes |
| Strict offline clean gates | 3: first FAIL; second PASS superseded by backup fixture fix; final PASS |
| External APK verifier sets | 2 sets / 4 commands; first superseded, final set PASS |
| Embedded Gradle APK verifier | 0 |
| Offline runtime graph / boundary | 2 PASS; first superseded, final PASS |
| Physical harness | 0 (no changed product subsystem) |
| Human Nix | 1 user-reported PASS for the final routine check |

Focused test commands include three failures: a2's history admission assertion,
the first deferred inbound fixture's projection timeout, and a4's startup create
assertion. All failures and relevant old gate evidence were preserved. The first
strict failure was diagnosed with the narrow method and fixture class, then the
inbound fixture fix required the second normal/strict set. The later backup test
failure and fixture edit required the third normal/strict/artifact set. The last
fresh timing run moved before those renewed gates. No failed/invalidation gate
was waived, and no strict/full gate was repeated solely to collect another speed
sample. The 1 GiB configuration was not committed or adopted.

## Final Git state and human handoff

Branch remains `main`; HEAD remains
`7385b855a70db9276d6a6e1a7f42927b63d218d2`. Existing profiling report preserved;
no staged content and no commits. New intended files retain intent-to-add.
`git diff --check` and `git diff --cached --quiet` pass.

`git status --short`:

```text
 A app/src/test/java/org/totipo/android/AndroidVaultControllerAuthoringTest.java
 A app/src/test/java/org/totipo/android/AndroidVaultControllerAutoSyncTest.java
 A app/src/test/java/org/totipo/android/AndroidVaultControllerFixture.java
 A app/src/test/java/org/totipo/android/AndroidVaultControllerInboundTest.java
 A app/src/test/java/org/totipo/android/AndroidVaultControllerPublicationTest.java
 A app/src/test/java/org/totipo/android/AndroidVaultControllerSyncRetirementTest.java
 M app/src/test/java/org/totipo/android/AndroidVaultControllerTest.java
 A app/src/test/java/org/totipo/android/AndroidVaultControllerTokenLifecycleTest.java
 M app/src/test/java/org/totipo/android/BackupControllerTest.java
 M app/src/test/java/org/totipo/android/HistoryImportControllerTest.java
 A review/ANDROID_BUILD_PERFORMANCE_PROFILE_REPORT.md
 A review/ANDROID_CONTROLLER_TEST_PARALLELISM_REPORT.md
```

`git diff --name-only` lists the same twelve paths. `git diff --cached --name-only`
is empty. All intended source/report files are Git-known; no included inputs
changed after the final gates. The sole remaining qualification step is the
repository-required human command, exactly:

```sh
nix flake check
```

The user subsequently reported **`nix flake check` PASS**. Rechecked the ten
frozen JVM-source hashes and final changed-path set: included inputs remain
unchanged. This completes the milestone's final human qualification gate.
Only this excluded review report was updated after that PASS, so no Gradle,
artifact or Nix qualification is invalidated. No staged content or commits;
intent-to-add entries remain for review. Hosted-CI performance remains unmeasured.
