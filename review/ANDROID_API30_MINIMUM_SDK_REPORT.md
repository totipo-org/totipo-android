# Android 11 minimum SDK cleanup

## Scope and starting state

Milestone classifications: **PRODUCT_CODE**, **BUILD_DEPENDENCY** (SDK build
policy only), **TEST_OR_QUALIFICATION_TOOLING**, **PACKAGING_RELEASE** (APK
compatibility policy; no release preparation).

Starting branch: `main`. Starting HEAD:
`2de5486f3bb1d9fa051d4640103db34f6bc8ba91`.
`git status --short` was empty before work. No pre-existing work required handling.
All work is confined to totipo-android. AGENTS.md, README, current development,
user and release documentation, manifests, Gradle configuration, package.nix,
flake.nix, CI/release workflows, verifier fixtures, existing JVM tests and device
harnesses were inspected. Historical biometric, multi-vault and signing reports
were consulted without modifying their evidence.

The exact Java guidance at `f0a028676c1801a10b2d8d2650bf9c24357d1fa1`
was read, including operation classes, replay-latest/observation ordering,
deterministic causal bases, merge freshness, persistence handles and close/threading.
It was fetched read-only into ignored local evidence. No Java dependency change
or operation scheduling change is needed.

Builds use the active pinned environment: Nix Gradle 9.8.0, JDK 17, SDK 37.0,
Build Tools 36.0.0 and the shell's patched AAPT2 override. Agent runs no Nix.
Local command logs and decoded artifacts are retained under ignored
`.qualification/api30/`; these are local evidence, not package inputs.

## Exact changed files and rationale

| File | Change |
| --- | --- |
| `app/build.gradle.kts` | Set minSdk 26 → 30; compile/target remain 37. |
| `app/src/main/java/org/totipo/android/TotipoApplication.java` | Remove API 30 initialization wrapper and unused Build import; retain initialization, recovery and lifecycle callback bodies. |
| `app/src/main/java/org/totipo/android/MainActivity.java` | Remove unsupported-runtime screen; make API 30 insets and API 28 heading/version-code operations unconditional. |
| `app/src/main/res/values/strings.xml` | Remove unused unsupported_runtime resource. |
| `app/src/main/java/org/totipo/android/OtpAuthEnrollmentActivity.java` | Make API 30 insets listener unconditional. |
| `app/src/main/java/org/totipo/android/AndroidBiometricCredentials.java` | Remove impossible pre-30 unavailable branch and unused Build import. Strong-biometric hardware/enrollment check remains. |
| `app/src/main/java/org/totipo/android/PlatformCodeClipboard.java` | Remove redundant API 28 guard around ownership-checked clipboard clearing. |
| `app/src/main/java/org/totipo/android/sync/AndroidProviderVaultPort.java` | Remove impossible pre-29 exception; preserve all provider relationship and creation checks. |
| `app/src/main/java/org/totipo/android/sync/AndroidSyncFolderPort.java` | Remove impossible pre-29 exception; preserve all provider relationship and creation checks. |
| `app/src/main/java/org/totipo/android/reconcile/ForegroundVaultCoordinator.java` | Correct obsolete minimum-SDK comment only. |
| `app/src/debug/java/org/totipo/android/debug/AuthPerfActivity.java` | Remove redundant API 27 guards around diagnostic screen flags. |
| `tools/verify-apk.py` | Require exact min/target 30/37 in shared identity policy and full approved manifest; preserve all other policies. |
| `app/src/test/java/org/totipo/android/AndroidVaultControllerTest.java` | Wait for public binding READY before checking the configured message; repair the deterministic completion condition exposed by strict qualification. |
| `tools/release/test_apk.py` | Update independent decoded manifest fixture; add explicit minimum 26/29/31 rejection regression and approved-30 acceptance. |
| `README.md` | State Android 11/API 30+ install requirement. |
| `docs/USER_GUIDE.md` | State current app's Android 11/API 30+ requirement and remove obsolete install/runtime split. |
| `docs/DEVELOPMENT.md` | Correct SDK setup and APK policy descriptions to 30/37. |
| `docs/ARCHITECTURE.md` | Replace obsolete unsupported-runtime architecture explanation. |
| `release/README.md` | Correct minimum SDK in existing v2/v3 signing explanation; no signing policy change. |
| `review/ANDROID_API30_MINIMUM_SDK_REPORT.md` | Milestone evidence and qualification accounting. |

Every removed condition is constant true or false for installable API 30+ devices.
On those devices, the same initialization, storage recovery, biometric, provider,
clipboard, layout and lifecycle statements execute in the same order. Startup
failure still returns with its original recovery diagnostic; lifecycle callbacks
are still registered only after successful registry initialization. No vault
format, admission, publication, observation, authentication or synchronization
algorithm changes. Java operation classes and Android ownership policies remain
unchanged.

## Complete active minimum SDK and runtime-guard audit

Repository searches covered minSdk/minSdkVersion, min_api/--min-api, SDK_INT,
VERSION_CODES, Android 8–10, API 26/29, install minimum, and unsupported-runtime
references. Historical `review/` reports remain historical and unchanged. Numeric
26/29 unrelated to Android compatibility (ZIP offsets, HTTP status codes, TOTP
time, string bounds, hashes and dates) are not installation policy.

| Active reference | Disposition |
| --- | --- |
| `app/build.gradle.kts` | Product installation authority: 30. |
| `tools/verify-apk.py` shared identity and approved full manifest | Both require exact 30/37; used by unsigned/debug and signed-release validation. |
| `tools/release/test_apk.py` | Approved fixture 30/37; negative minimums 25, 26, 29, 31 and target 36 are deliberate rejection cases. |
| README, USER_GUIDE, DEVELOPMENT, ARCHITECTURE, release README | Current requirements corrected; release download links and version metadata remain unchanged. |
| TotipoApplication, MainActivity, biometric credentials, two provider adapters, clipboard, enrollment UI, debug AuthPerfActivity | All redundant pre-30 runtime branches removed as listed above. |
| ForegroundVaultCoordinator | Obsolete minSdk-26 comment corrected; observation implementation unchanged. |
| MainActivity and OtpAuthEnrollmentActivity API 33 checks | Retained predictive-back feature selection. |
| PlatformCodeClipboard API 33 check | Retained sensitive-clipboard constant compatibility. |
| Debug runtime SDK logging; API 30 TargetApi annotations | Retained diagnostics/annotations; these do not permit older product installation. |
| `tools/device/check-row-reveal.py` | API 26 declaration belongs to detached, self-targeted row-test package, not product. Unchanged. |
| `tools/device/build-saf-bootstrap-tests.py` | API 26 declaration belongs to isolated instrumentation helper. Unchanged. |
| `tools/device/build-saf-inbound-tests.py` | API 26 declaration belongs to isolated instrumentation helper. Unchanged. |
| `tools/device/build-saf-outbound-tests.py` | API 26 declaration belongs to isolated instrumentation helper. Unchanged. |
| `tools/device/build-otpauth-ingress-tests.py` | API 26 declaration belongs to isolated instrumentation helper. Unchanged. |
| `tools/device/qualify-syncthing-e2e.py` | API 26 declaration belongs to isolated instrumentation helper. Unchanged. |
| `tools/device/test_qualification_signing.py` | API 26 declaration belongs to disposable signing fixture. Unchanged. |
| `tools/device/android_harness.py` | D8 min_api=26 default governs helper bytecode generation, not product manifest or support policy. Unchanged. |
| `tools/device/qualify-security.py` | Already uses helper manifest/D8 API 30. Unchanged. |
| package.nix / flake.nix / CI / release tooling | No independent hardcoded product minSdk override; consume Gradle outputs and shared verifier. No tooling change needed. |

No unsupported-runtime screen/resource remains in active application sources.
Android 11 follows the ordinary supported initialization and vault paths; it is
not diverted to an unsupported-runtime screen. Native execution evidence on an
Android 11 runtime is separately limited below.

## Validation and artifact evidence

Baseline before edits: `gradle check :app:assembleDebug :app:assembleRelease`
**PASS**, one invocation, 1m 36s, 89 tasks (29 executed, 60 up-to-date).

Focused Python: `python3 -B -m unittest discover -s tools/release -p 'test_*.py'`
**PASS**, 63 tests, no skips. Includes explicit minimum 26 and 29 rejection,
approved 30 acceptance, wrong-target rejection, strict manifest/identity/resource
negative cases, and release behavior in disposable fixture repositories. No real
repository tag, release preparation, push or publication was performed.

Focused Gradle: one `:app:testDebugUnitTest` invocation selecting
`AndroidVaultControllerTest`, `SecurityControllerTest`,
`PerVaultBiometricCredentialsTest`, `BiometricSourceBoundaryTest`,
`sync.SyncSourceGuardTest`, `sync.VaultBootstrapTest`, `TotpPresentationTest`.
**PASS**, 165 tests, zero failures/errors/skips, 1m 53s. This selection covers create/password
unlock, token operations/reveal/copy, optional biometrics and lifecycle,
provider/bootstrap boundaries and existing Sync guards.

Final normal: `gradle check :app:assembleDebug :app:assembleRelease` **PASS**,
3m 36s, 89 tasks (46 executed, 43 up-to-date). Full JVM results: 461 tests in
43 suites, zero failures/errors/skips; lint, locked build classpath, Maven
boundaries and debug/release assembly pass.

First strict-clean attempt **FAILED** in 3m 31s, 36 executed tasks: one
failure out of 461 tests. Exact command:

```sh
gradle --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug :app:assembleRelease
```

Failure: `AndroidVaultControllerTest.revokedBindingStaysConfiguredAndLocalChangesRetainRetryHint`,
line 1343: expected `Sync folder configured`, actual `Checking sync folder…`.
Full log: `.qualification/api30/strict-clean.log`; complete XML results preserved
in `.qualification/api30/strict-failure-results/` before any rerun.

Narrow reproduction, without source changes:
`gradle --offline :app:testDebugUnitTest --tests 'org.totipo.android.AndroidVaultControllerTest.revokedBindingStaysConfiguredAndLocalChangesRetainRetryHint'`
**PASS**, one test, 2s. The unchanged test had also passed the initial focused
and final normal runs. Source diagnosis identified an asynchronous completion
window: `providerReturned` clears providerActive before checking and publishing
the final binding status. The test's providerDone predicate can therefore return
while its cached presentation still says CHECKING. This is not an SDK branch
failure. The test now waits for public binding READY as well as providerDone,
then retains its original message and access-revocation assertions. No controller
or Sync behavior was changed. This directly fixes the incomplete wait condition
without adding delays or changing global helper semantics.

The test edit invalidates the earlier normal PASS and requires renewed normal
and strict gates. Focused fix verification: the same single-test command **PASS**,
2s (2 executed tasks, 20 up-to-date).

Renewed final normal checkpoint **PASS**, 3m 6s, 89 tasks (34 executed, 26 from
cache, 29 up-to-date). All 461 tests in 43 suites pass with no failures/errors/skips,
lint and both APK assemblies pass. This supersedes the invalidated first normal
PASS.

Renewed strict-clean gate **PASS**, 3m 42s, all 91 tasks executed, all 461 JVM
tests / 43 suites PASS with zero failures/errors/skips. Lint completed with zero
errors and 41 warnings. Existing deprecation and patched-AAPT2 experimental-option
warnings remain visible. Final evidence is from this renewed strict-clean run;
no production/test/build input changed afterward.

External final artifact set, exactly two policy invocations, both **PASS**:

```sh
python3 tools/verify-apk.py app/build/outputs/apk/debug/app-debug.apk --debug-probe
python3 tools/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk --unsigned --no-debug-probe
```

| Strict-clean artifact | SHA-256 |
| --- | --- |
| Debug APK | `e4d90497eb2d46847783f1fb9a8dd35a89eebcb8f81787cf01612e889600d3ab` |
| Unsigned release APK | `fc229e3721d5edda4704f21b5253bb671cfd66f57fa6974a4b3c65e7e096b54b` |

Independent pinned-SDK `apkanalyzer manifest print` inspection of both final APKs
records exactly `minSdkVersion="30"`, `targetSdkVersion="37"`, compile SDK 37,
package `org.totipo.android`, versionName `0.2.1`, versionCode `6`. Decoded XML is
retained in `.qualification/api30/debug-manifest.xml` and `release-manifest.xml`.
The manifest policy matches the Gradle minimum and rejects unexpected SDK values;
Android's installer contract excludes API 26–29. No native API 29 install was needed.

Manifest review: only USE_BIOMETRIC permission; release has only the two approved
exported activities and their launcher/otpauth filters, debug additionally has the
four existing probes. Full verifier retains exact components, exports, filters,
backup/variant flags, identity, essential resources, signing exclusions and ZIP/DEX
policies. The approved SDK fields are the only verifier-policy changes.

Final dependency review:
`gradle --offline :app:dependencies --configuration releaseRuntimeClasspath :app:verifyMavenBoundary`
**PASS**, 1s, two tasks executed. Runtime graph remains external Maven
`totipo-storage-nio:0.2.0 → totipo-core:0.2.0 → bcprov-jdk18on:1.86`, with strict
locks and no local/source substitution. Boundary verification also ran inside each
full check; no dependency/cache refresh was performed.

Documentation: all 41 relative link targets in changed existing documentation
resolve. Binary-safe main-source scan confirms no unsupported_runtime resource or
reference. An initial ad-hoc text scan tried to decode PNG files and failed with
UnicodeDecodeError; corrected to a byte scan, which passed. This was a validation
script mistake, not a product/test failure; it required no source change or gate
rerun. Final diff/whitespace validation is recorded below.

Device availability: `adb devices -l` returned an empty device list. No suitable
Android 11 runtime or API 29 runtime is available; zero installs, smoke or harness
runs. No emulator dependency was introduced. Host JVM tests and Android lint are
not claimed as native Android 11, Keystore or SAF execution. Historical device
results retain their original artifacts and environments.

## Package membership, source freeze and invalidation

Actual package.nix filter re-inspected: selects `app/src`, `gradle`, `tools` and
its exact build/lock/version/license/branding file allowlist; excludes symlinks and
.git/.gradle/build/.direnv components. Changed included inputs are the Gradle
minimum SDK, main/debug Java, string resources, one JVM completion-wait fix,
APK verifier and verifier tests.
No lock, dependency, Java version, application ID, compile SDK, target SDK,
package-deps, flake or package configuration changes. VERSION remains `0.2.1`;
VERSION_CODE remains `6`.

flake.nix also evaluates package.nix/flake.lock/package-deps.json and uses an
explicit release test fileset containing tools/release, tools/verify-apk.py,
build files, release/signing and release workflow. The changed verifier/test/build
inputs therefore require final human Nix qualification. README, docs/, release
README and review/ are outside Android package inputs and outside the release
check fileset. Report-only updates do not invalidate frozen checks.

The initial freeze inventory covered 198 conservative Git-known input files,
including every package/evaluator/release-check input and extra non-documentation
tracked files. Its SHA-256 was
`9d10152a05469969ca6b2b40303d4ccf8449e7d2e7ab9da7aff413db20774775`;
it is retained as `.qualification/api30/initial-frozen-inputs.json`.

The strict failure required a JVM test edit, invalidating the initial normal
PASS. After focused fix PASS, all 198 inputs were refrozen before the renewed
normal checkpoint. Comparison found exactly one changed input since the initial
freeze: `app/src/test/java/org/totipo/android/AndroidVaultControllerTest.java`.
The final `.qualification/api30/frozen-inputs.json` inventory SHA-256 is
`747020fd15c8a81c04d1df64b44fdb15b59b55a635550af777d4652c1c889b77`.
All intended files are Git-known; only the report uses intent-to-add.

- **Production source stable:** only impossible pre-30 branches/comment and the
  unused resource removed; focused compilation and 165 tests pass, followed by
  renewed normal and strict gates and final APK policy PASS.
- **JVM tests stable:** one incomplete completion predicate fixed to wait for
  public binding READY; narrow post-fix and renewed normal/strict 461-test checkpoints pass.
- **Build/package configuration stable:** only minSdk changed to 30; compile,
  target, metadata, dependencies, locks, evaluator and cache inputs unchanged.
- **Qualification harness/tooling stable:** shared APK policy and independent
  fixture/regression frozen after all 63 Python tests pass; device harnesses unchanged.

No more included-input edits are planned after this replacement freeze. Repeated
normal and strict runs are necessary because of the recorded failure and test
fix, not routine repetition. The report is excluded from package and release
checks; subsequent report-only result updates do not invalidate them.

## Execution accounting

| Execution | Count / result |
| --- | --- |
| Baseline full Gradle | 1 PASS |
| Focused Gradle test | 3 PASS: 165 tests, single-test reproduction, single-test fix |
| Focused standalone compilation | 0; test invocation includes compilation |
| Focused Python release/verifier suite | 1 PASS, 63 tests |
| Final normal full Gradle | 2 PASS: initial invalidated; renewed 461 tests / 43 suites PASS |
| Strict-clean offline full Gradle | 2: first FAILED; renewed 461-test / 91-task PASS |
| External APK verifiers | 1 final set: 2 commands, debug + release PASS |
| Embedded Gradle APK verifier | 0; Gradle check does not invoke full APK verification |
| Final dependency graph / Maven boundary | 1 PASS; separate from checks embedded in full gates |
| Physical/emulator harness phases / installs | 0 / 0, no device available |
| Human Nix | 1 PASS, human-reported after final source freeze and local qualification |

## Final Git state and recommendation

Final branch remains `main`; HEAD remains
`2de5486f3bb1d9fa051d4640103db34f6bc8ba91`.
`git status --short` records 19 modified tracked files (` M`) and this report
(` A`, intent-to-add only), exactly the 20 files listed above. `git diff --name-only`
matches that inventory. `git diff --cached --name-only` is empty and
`git diff --cached --quiet` passes: no staged file contents. `git ls-files
.qualification` is empty. No other new files are intended for this milestone.
Final diff reviewed, including the isolated JVM readiness-wait fix;
`git diff --check` **PASS**. All 198 final frozen input hashes still match after
strict-clean, artifact verification and report updates.

No signing artifacts, VERSION/VERSION_CODE edit, release metadata edits, commit,
real tag, workflow dispatch, release preparation, push or publication performed.
Release 0.2.2 will be prepared separately after acceptance and commit.

**Human Nix qualification: PASS, human-reported.** After the final source-freeze
request, the user reported: “nix flake check passed”. One final routine human Nix
invocation is recorded; the agent did not run Nix or independently observe its log.
After this result, all 198 frozen input hashes were rechecked and still match.
Only this excluded report was updated to record the result; effective package/check
inputs remain unchanged, so no qualification gate is invalidated.

**Recommendation:** ready to commit upon acceptance and subsequently prepare
0.2.2 using the existing release tooling as a separate task. All required local
and human qualification gates pass. No unresolved implementation failure remains;
the earlier test completion race is repaired and renewed gates pass. Native
Android 11 smoke evidence remains unavailable because no suitable device/emulator
was connected. Host JVM/manifest/lint results do not claim universal device,
Keystore, SAF-provider or peer qualification. Historical reports are unchanged.
Changes remain uncommitted with no staged file contents.
