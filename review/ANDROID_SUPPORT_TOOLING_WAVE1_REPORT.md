# Android support-tooling pruning Wave 1

Status: complete. Pruning, focused host validation, detached physical lifecycle
qualification and final human-reported Nix PASS. Frozen executable inputs remain
unchanged. All changes are unstaged/uncommitted.

## 1. Starting HEAD/state

Initial commands, before editing:

```text
git branch --show-current  => main
git rev-parse HEAD         => 09811056680bb04d835e4a42812abd75bd84a1ac
git status --short         => empty
cat VERSION               => 0.1.2
cat VERSION_CODE          => 3
```

The support-tooling audit is tracked and committed at starting HEAD. Current
release-tooling maintenance is committed at `33eb9a7`; its
[report](ANDROID_ACTIONS_RELEASE_UX_REPORT.md) records successful 0.1.2 publication
and subsequent tooling qualification. These are committed prior results, not
fresh release verification in this milestone. Both current Java regressions and
the security qualifier's lifecycle phase existed at the start.

Read AGENTS.md, README.md, the M3D checkpoint document and support-tooling audit;
inspected the removed pair, retained qualifier/Java sources, relevant F09 evidence,
package.nix, flake.nix, Gradle consumers, CI/release workflow consumers and tests.

## 2. Classification

**TEST_OR_QUALIFICATION_TOOLING — support-tool cleanup.** No product behavior,
Java regression, build/dependency configuration or release/signing change.
The device-harness row of the AGENTS invalidation matrix applies: focused harness
validation and final human Nix, without rebuilding unchanged product artifacts.

## 3. Deleted files

- `tools/device/qualify-token-lifecycle.py`
- `tools/device/build-token-lifecycle-tests.py`

Exactly this superseded Python pair was deleted. No replacement wrapper was added.

## 4. Caller analysis before deletion

Repository-wide `rg` searches covered tracked/live text and hidden repository
configuration, excluding Git internals and generated build/cache directories.
Searches included both filename stems, `TokenLifecycleRegression` and
`qualify-security`; inspected source construction and generated manifests as well
as literal names.

The old launcher was named only in the historical token-lifecycle report and
support-tooling audit. Its sole executable dependency was its subprocess call to
the old builder. No live README, AGENTS, checkpoint, workflow, Gradle, Nix or other
tool required either old command. The old builder compiled only
TokenLifecycleRegression and registered it against `org.totipo.android`.

The retained qualifier explicitly compiles TokenLifecycleRegression together
with SecurityRegression. Its generated manifest registers both classes against
`org.totipo.securityqualification`. Its lifecycle branch invokes
TokenLifecycleRegression; first/restart invoke SecurityRegression. SecurityRegression
also uses TokenLifecycleRegression.FixturePort. Historical reports are evidence,
not executable callers; external/private operator scripts were not inspected.

## 5. Proof of supersession

Both routes compile the exact same `tools/device/TokenLifecycleRegression.java`;
the old pair contains no extra lifecycle Java, assertion, phase, or scenario
argument. The retained scenario exercises actual UI/controller Edit/Delete/Resolve,
real Java-authored conflict/selection, same-session behavior, concealment and
automatic publication with immutable history checks, using isolated file transport.
The scenario source was not changed. Existing coverage limits are unchanged.

The old launcher additionally checked lockscreen state, extended/restored the
screen timeout, pulled the installed production APK/certificate, signed the release
and instrumentation with `.gradle/m1l-inspection/m1k-debug.keystore` and the password
file `.gradle/m1m-inspection/signing-password`, checked signer equality/alignment,
verified and replaced the installed production app, then compared installed bytes
and recorded device/artifact identity. Cleanup removed its instrumentation package
and relaunched production. These are obsolete installation/signing procedures and
identity for that route, not unique lifecycle assertions.

The detached route preserves the scenario and final-artifact evidence obligation:
`--prepare` copies production DEX/resources directly from the final unsigned release
APK, appends harness DEX, uses an isolated test-only key/package and records the
source APK SHA-256. It does not replace the production installation. No unique
current scenario was found; no stop condition was triggered.

## 6. Retained lifecycle path

Unchanged `qualify-security.py` resolves the same source at line 56, registers it
at line 68, and selects it for lifecycle at line 93. F09's
[physical evidence](ANDROID_STALE_SUBSCRIBER_OWNERSHIP_FIX_REPORT.md), section 25,
already used this path (historical 51-check PASS, not rerun here).

Retained SHA-256 identities, equal to starting HEAD:

```text
qualify-security.py
a366ecf3f4510327ea21f6d3e03de88d5789679d152ab8d2905346333f994742
TokenLifecycleRegression.java
949f50dc4645acbcc51cb518ca77c6cef32c0a100cfa2b14d917fb519e3ab9c8
SecurityRegression.java
fbe50b629a46e90f49b687d2682af0f7f7fb1228faafff1a88640b1902a7bb24
```

## 7. Live documentation

No live documentation required the old entry points, so none was changed.
Historical review reports remain unchanged and may continue to name old commands.
No invalid live implementation-shape reference needed removal.

## 8. Focused validation commands/results

`python3 tools/device/qualify-security.py --help` confirmed supported flags;
source inspection confirmed `--build-only` compiles both Java regressions without
packaging, signing, installing or invoking adb.

The first `python3 tools/device/qualify-security.py --build-only` failed before
compilation at the dependency candidate assertion: the default
`/home/niki/.gradle` cache is absent. An initial cache-preparation check failed
because its temporary XML query omitted the verification-metadata namespace;
the accompanying second build-only attempt again failed at the same empty-cache
assertion. Correcting that read-only query found all three expected checksums.
No repository executable was changed to recover.

Existing ignored JARs were checked against committed
`gradle/verification-metadata.xml` SHA-256 values and copied into the expected
layout under `.gradle/wave1-gradle-home/caches/modules-2/files-2.1`:

- `.gradle/r19-inspection/totipo-core-0.2.0.jar`
- `.gradle/r19-inspection/totipo-storage-nio-0.2.0.jar`
- `.gradle/m1l-inspection/bcprov-jdk18on-1.86.jar`

Final supported command, in the existing pinned Nix-managed JDK 17/Android SDK
environment, **PASS**, exit 0:

```sh
GRADLE_USER_HOME="$PWD/.gradle/wave1-gradle-home" python3 tools/device/qualify-security.py --build-only
```

Output log: `.gradle/security-qualification/wave1-build-only.log`. Confirmed compiled
TokenLifecycleRegression.class, TokenLifecycleRegression$FixturePort.class and
SecurityRegression.class exist. Build-only proves harness compilation; packaging
membership is established by unchanged source inspection, not a fresh packaged APK.
No existing focused host test directly guards this security harness packaging;
none was added. Unrelated release/provider suites were not run.

Repeated repository-wide reference search **PASS**: deleted names occur only in
review evidence; no live executable caller remains. Retained source resolution,
manifest registration and phase dispatch assertions **PASS**. Retained qualifier
and both Java files are byte-identical to HEAD. `git diff --check` **PASS**.

## 9. Physical evidence disposition

The user authorized agent-operated physical qualification. Initial read-only adb
preflight found no attached device; after the user connected it, serial
`37311JEGR05916` was available: Pixel 6a, Android 17, API 37. The disposable
security package was initially absent.

**Fresh physical PASS: 51 checks, one lifecycle phase run, one test installation.**
Executed the existing qualifier in the pinned environment with the checksum-verified
isolated dependency cache from section 8:

```sh
GRADLE_USER_HOME="$PWD/.gradle/wave1-gradle-home" python3 tools/device/qualify-security.py --prepare --phase lifecycle
```

The phone locked before the test Activity gained focus. The user unlocked it on
request; the same run continued and passed without retry or harness modification.
No screen-timeout setting was changed. Log:
`.gradle/security-qualification/lifecycle.log`, ending
`TOKEN LIFECYCLE PASS checks=51`; process exit 0.

Qualified unsigned release APK SHA-256:
`b70501790d32ff383665c8f7c1a599d750036b91a3b1fb0b804f706b8341e012`.
The qualifier's `qualified-source.sha256` matches that APK. All 17 selected
production DEX/resource entries are byte-identical between that APK and the
prepared detached package. Detached `tests.apk` SHA-256:
`634eafef168b3ec219eab6dea487e429c05f16e9c0ae4ac3f4623c62150b0c3f`.
Pulled the installed detached APK to
`.gradle/security-qualification/wave1-installed.apk` and confirmed full byte
identity with `tests.apk`. Product artifacts were not rebuilt for this pruning.

Cleanup `python3 tools/device/qualify-security.py --remove` passed. A read-only
`pm path` check confirmed the disposable package absent (exit 1, empty stdout).
No production app replacement or biometric first/restart phase occurred.

The retained procedure for future runs follows.
From the managed shell, with the disposable package absent and the device unlocked,
the exact existing F09 command is:

```sh
python3 tools/device/qualify-security.py --prepare --phase lifecycle
```

The existing default serial is `37311JEGR05916`; use supported `--serial SERIAL`
if selecting a different authorized device. Require `TOKEN LIFECYCLE PASS` in
`.gradle/security-qualification/lifecycle.log`. Record device identity, checks and
the qualified unsigned release APK hash; confirm
`.gradle/security-qualification/qualified-source.sha256` matches that final APK.
The detached package must contain those final release DEX/resources. Cleanup:

```sh
python3 tools/device/qualify-security.py --remove
```

Use the same serial for cleanup if overridden. Keep lifecycle physical evidence,
F09 deterministic ownership evidence, biometric first/restart phases and final
artifact identity requirements intact. No unrelated physical phases are requested.

## 10. Changed-file list

Two deletions above plus this new report:
`review/ANDROID_SUPPORT_TOOLING_WAVE1_REPORT.md`. Product, tests, versions,
release/signing tooling, workflows, dependencies and Gradle/Nix configuration are
unchanged. No historical report was edited.

## 11. Qualification accounting and freeze

| Execution category | Count/result |
| --- | --- |
| Baseline full Gradle | 0; tooling-only scope |
| Focused Gradle tests/compile/boundary | 0 |
| Security build-only | 3 attempts: 2 missing-cache failures, final PASS |
| Final normal full Gradle | 0; no Gradle-consumed input changed |
| Strict-clean Gradle | 0; no product/test/build invalidation |
| External artifact verifiers / embedded Gradle verifier | 0 / 0 |
| Host test suites / signing smoke | 0 / 0; none directly affected |
| Physical phase runs / installs | 1 lifecycle PASS / 1 detached test install; cleanup PASS |
| Human Nix / agent Nix | 1 human-reported PASS / 0 |
| Stage/commit/push/tag/dispatch | 0 |

No full gate was repeated or invalidated during this milestone. The applicable
non-Nix final gate is the retained harness build-only PASS plus caller/source,
scope and whitespace checks. The AGENTS device-harness invalidation row expressly
requires no Gradle rebuild solely for harness changes outside Gradle consumers.
No unrelated APK/signing/release qualification was repeated.

Actual package filtering selects `tools/**`, so deletion invalidates prior Nix
evidence even though Gradle does not consume these two files. Evaluator/cache
inputs are unchanged. `.gradle`, build outputs and `review/` are excluded; neither
package/check nor release-app wrappers consume this report.

All four freeze statements, before the human Nix request:

- Production source stable: no app source/resource/manifest edits; Git diff contains
  only the two tool deletions. VERSION 0.1.2 and VERSION_CODE 3 unchanged.
- JVM tests stable: no app/src/test edits.
- Build/package configuration stable: no Gradle, locks, verification metadata,
  package.nix, flake.nix, flake.lock or package-deps.json edits.
- Qualification harness/tooling stable: exact two deletions finalized; retained
  qualifier and regressions match HEAD; final build-only PASS and caller check.

All included source/evaluator/cache inputs are frozen. Any later included-input
edit invalidates subsequent Nix evidence and requires applicable requalification.
Report-only updates recording human results do not invalidate it under the inspected
filter and consumers.

## 12. Human Nix status

The user reported **PASS** for the final command after input freeze:

```sh
nix flake check path:.
```

This result is human-reported; the agent did not execute Nix or independently
inspect Nix logs. Frozen executable inputs remain unchanged. Only this excluded
report was updated to record the result, which does not invalidate Nix under the
inspected filter and consumers. Physical lifecycle evidence is recorded in section 9.

## 13. Final Git state

Branch `main`; HEAD remains `09811056680bb04d835e4a42812abd75bd84a1ac`.
Index empty; all changes unstaged/uncommitted:

```text
 D tools/device/build-token-lifecycle-tests.py
 D tools/device/qualify-token-lifecycle.py
?? review/ANDROID_SUPPORT_TOOLING_WAVE1_REPORT.md
```

No unrelated refactor, product change, release operation or Wave 2 work occurred.

## 14. Suggested commit subject

```text
Retire superseded Android lifecycle qualification launcher
```
