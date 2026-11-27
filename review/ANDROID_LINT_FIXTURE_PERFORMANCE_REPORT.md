# Lint analysis and security fixture performance

Classification: BUILD_DEPENDENCY and TEST_OR_QUALIFICATION_TOOLING.
Starting branch `main`, HEAD `9ca91a2338ccf076b76dc297e2d564ec72c30805`.
This continues the user's authorized performance work. Starting modifications
were package.nix, docs/DEVELOPMENT.md and the intent-to-add single-Gradle report
from this session, plus the user's untracked totipo-nix-build.log. Preserve them;
no staged contents, commits, resets or unrelated edits. No ordinary product
baseline is required for this build/test-only scope.

## Investigation and change

Inspected AGENTS.md, README, development documentation, flake.nix, package.nix,
CI, root/app Gradle configuration, existing qualification/performance reports,
the supplied Nix log and the diagnostic Gradle profile. The profiled Nix build
took 222.93 seconds: JVM tests 127.10 seconds, debug lint 36.55 seconds,
release fatal lint analysis 24.898 seconds, BC DEX transform 33.674 seconds,
configuration 20.532 seconds. Durations overlap; their sum is not wall time.
No repeated test-suite execution was found.

The exact installed AGP 9.4.0 bytecode confirms LintTaskManager disables the
fatal report when global/full lint is requested, leaving its analysis dependency
scheduled. app/build.gradle.kts adds an execution predicate to
lintVitalAnalyzeRelease following lintVitalReportRelease.enabled. It skips work
whose report AGP has already disabled, without changing AGP's acceptance policy.
Standalone release assembly still runs fatal analysis and reporting. This is
deliberately tied to task names/behavior of the pinned AGP; review it on upgrade.
No lint issue, scope, severity, abort policy or standalone check is disabled.
Configuration cache remains disabled as before.

SecurityControllerTest previously created a random vault before each of 27 cases.
It now creates one encrypted bootstrap through released NioTotipo per class,
closes that seed session, and copies ciphertext using CREATE_NEW into each
case's fresh temporary root. Only private ciphertext bytes are shared; writable
roots, registries/local IDs, controllers, sessions, password arrays, platform
keys and credential records remain independent. The protocol identity repeats
across these isolated cases; these tests do not require distinct per-case vault
identities. No live session or decrypted material is reused.

Every setup performs real controller discovery and password unlock using the
unchanged Unicode password and ordinary KDF, and asserts input wiping. Reopen
counters start after authenticated setup, preserving their previous test-body
meaning. All 27 @Test method bodies are byte-identical to the saved starting
source. Creation-specific controller tests and biometric enrollment, wrong-password,
reopen, cancellation, close, identity, inactivity and late-result operations in
the test bodies retain their real implementations and assertions.

Reviewed the exact guidance at f0a028676c1801a10b2d8d2650bf9c24357d1fa1 and
released 0.3.2 API_DESIGN/docs/BACKUP_ARCHIVE at
af11553659dd8bd4157064c7e3de27bed8980ac5. Compared the read local copies against
those exact remote revisions byte-for-byte. Setup open/create/close is class G
work with separate owners; no product scheduling, admission, observation or
provider policy changes. In particular, refresh return is never observation
completion, and no historical state/reference is transferred between sessions.

Other fixtures were reviewed but not broadly converted: TOTP controller tests
assert real creation/open counts, and retirement/multiple-vault cases depend on
their independent protocol identities and lifecycle transitions. This milestone
does not replace those relevant operations to obtain a timing reduction.

## Focused validation

Evidence and temporary init scripts are ignored under
`.qualification/lint-fixtures/`; no test fault is in included source.

- First combined-lint attempt failed in offline dependency resolution before
  lint execution: Totipo 0.3.2 was not cached in the active Gradle home. Normal
  online retry resolved strictly locked dependencies and passed in 26 seconds.
  The log explicitly shows fatal analysis skipped by the new predicate, full
  debug/test-source lint retained, and AGP's fatal report disabled.
- Standalone lintVitalRelease with an injected release manifest MissingClass
  fatal issue ran analysis/reporting and failed as expected (2.193 seconds).
- Full lint plus fatal lint with the injected issue on debug failed in lintDebug
  as expected (8.573 seconds); unused release analysis was skipped. This proves
  the full lint gate still detects a real error with the optimization active.
- Standalone assembleRelease with the release fault failed at lintVitalRelease
  as expected (29.494 seconds). Its analysis was UP-TO-DATE from the preceding
  release fault, not disabled; fatal reporting still rejected the assembly.
- Fresh focused SecurityControllerTest before: 27 PASS, task 34.534 seconds.
  After: 27 PASS, task 23.201 seconds (command wall 27.622 seconds, including
  compilation). The task measurement includes seed generation and setup. This
  single local before/after observation is 32.8% shorter, not a statistical
  estimate of overall Nix performance. Both used the same temporary init script
  to disable test-task up-to-date/cache reuse only; no clean/rerun-tasks.
- Compared all security test bodies byte-for-byte and saved the full starting
  535-method test inventory for final qualification comparison.

## Final qualification and input freeze

Final normal Gradle PASS in 125.103 seconds (89 actionable tasks: 19 executed,
three from cache, 67 up-to-date). All 535 tests in 57 classes passed with zero
failures/errors/skips; complete class/method inventory matches the starting
inventory exactly. This normal build restores source-set models/manifests after
the temporary fault injections, so it is not a controlled timing comparison.
Strict offline clean Gradle PASS in 154.752 seconds, all 91 actionable tasks
executed. All 535 tests in 57 classes passed again, with zero failures/errors/skips
and the exact starting inventory. Unused fatal analysis was skipped; ordinary
lint, tests, dependency verification and both assemblies ran. No failed or
invalidated final gate was repeated.

Final external artifact set PASS against strict-clean outputs:

- Debug verifier: 4.542 seconds; SHA-256
  `5e53a71a56a02854016ca1bd5bf5697873c8846eaaa8c402073b2e46c18c411e`.
- Unsigned release verifier: 3.851 seconds; SHA-256
  `6e2a906463534103e052c1de57b524a6a6b99c8415b257d51cd14783cbe2b140`.
- Offline runtime graph/Maven boundary: 1.912 seconds. Reviewed graph is external
  storage-nio 0.3.2 -> core 0.3.2 -> BC 1.86 with matching strict locks.

Both verifiers checked identity against VERSION 0.2.5 and VERSION_CODE 10, SDK
30/37, complete manifests/components/exports/intent filters, variant flags,
backup/branding policy and packaged exclusions. Reviewed manifests retain
USE_BIOMETRIC, backup disabled, launcher and otpauth/text ingress activities,
with debug probes confined to debug. No injected manifest entry survived.

All included code is stable; frozen input hashes are recorded in
`.qualification/lint-fixtures/frozen-inputs.json` before these gates and verified
unchanged after normal, strict and artifact gates.
No device run is selected: product code and device harnesses are unchanged.

Source filtering is unchanged: app/src, app/build.gradle.kts and package.nix are
qualification inputs. The app Gradle file also feeds the separate release-tooling
check. docs/ and review/ are excluded from both package/check source selections;
excluded report updates do not invalidate Nix. The user build log was never
added to source membership. The new report is intent-to-add only.

Freeze evidence before the human gate:

- Production source stable: no production source/resource/manifest edit; both
  strict-clean APKs passed the complete external policy.
- JVM tests stable: only SecurityControllerTest setup/helpers changed; all 27
  method bodies remain byte-identical and both full gates passed the exact
  535-method inventory. The frozen fixture hash remains unchanged.
- Build/package configuration stable: the sole new included build edit is the
  app Gradle predicate, qualified by positive/negative focused lint checks and
  both final gates. The prior package change remains byte-identical to its
  saved starting hash; dependency, lock, SDK/JDK and flake inputs are unchanged.
- Qualification harness/tooling stable: no included tool/harness changes. All
  diagnostic scripts, faults and profiles remain ignored local evidence.

## Execution counts and Git handoff

| Category | Invocations in this follow-up |
| --- | --- |
| Baseline full Gradle | 0 (build/test-only scope) |
| Focused security-test Gradle | 2 PASS (before/after, 27 tests each) |
| Focused lint/assembly Gradle | 5: offline resolution failure, online combined PASS, three expected fault rejections |
| Focused compile-only | 0 |
| Final normal full Gradle | 1 PASS |
| Strict offline clean | 1 PASS |
| Final external APK verifiers | 1 set / 2 commands, both PASS |
| Embedded Gradle full APK verifier | 0 |
| Offline runtime graph / boundary | 1 PASS |
| Physical/device harness | 0 (no changed product/device subsystem) |
| Agent Nix | 0 |
| Human Nix | 1 user-reported final routine PASS; 1 subsequent forced rebuild measurement |

The one online lint retry was needed to resolve previously uncached, pinned
dependencies. The three deliberate fatal fixtures each failed at the expected
lint boundary; these are successful negative checks, not unresolved regressions.
No full gate was used for diagnosis, and no frozen input changed after final
normal/strict qualification. Test-task cache reuse was disabled only in the two
temporary focused measurement commands. No extra full suite timing run followed
the stable gates. No whole-Nix speedup is claimed until measured by the human.

New changes: app/build.gradle.kts, SecurityControllerTest.java,
docs/DEVELOPMENT.md and this report. Prior package.nix and single-Gradle report
edits are preserved. Final branch/HEAD remain unchanged. Status/unstaged names:

```text
 M app/build.gradle.kts
 M app/src/test/java/org/totipo/android/SecurityControllerTest.java
 M docs/DEVELOPMENT.md
 M package.nix
 A review/ANDROID_LINT_FIXTURE_PERFORMANCE_REPORT.md
 A review/ANDROID_SINGLE_GRADLE_NIX_REPORT.md
```

Both reports retain intent-to-add only. `git diff --cached --name-only` is empty;
`git diff --cached --quiet` and `git diff --check` pass. Local development-guide
links resolve. The user log was present initially and is absent at final status;
no agent command modified or removed it. No staged contents or commits.
Report-only edits after the human
result will not invalidate qualified inputs under the rechecked filters.

The earlier human Nix PASS qualifies the single-invocation package change only;
the new Gradle/test edits require renewed final human qualification. All intended
new milestone files are Git-known and inputs are frozen. Requested final human
gate: exactly `nix flake check`; the user subsequently reported PASS.

## Human qualification and timing result

The user reported the final `nix flake check` PASS and a subsequent forced rebuild
of 181.87 seconds. This completes the required human qualification gate. Compared
with the recent pre-lint/fixture 223.62-second forced rebuild, the observation is
41.75 seconds (18.67%) shorter. Compared with the original 200.49-second
two-invocation measurement, it is 18.62 seconds (9.29%) shorter. Earlier same-input
measurements varied, so these are individual observed comparisons, not a
statistical speedup estimate or attribution of every saved second to these changes.
The extra forced rebuild was the user's performance measurement, not an additional
requested routine qualification gate.

Rechecked branch, HEAD, full status and frozen Gradle/test/package hashes after
the reported PASS: unchanged. No additional modified/untracked paths appeared.
Only this excluded report was updated to record the human result, so no Gradle,
artifact or Nix PASS is invalidated. `git diff --check` and
`git diff --cached --quiet` pass; no staged contents or commits.
