# Android Phase A: Nix-first build simplification

2026-10-14. Classification: **BUILD_DEPENDENCY / TEST_OR_QUALIFICATION_TOOLING**.
Bounded tooling migration only; no application behavior or release UX change.
Implementation remains uncommitted. Human Nix qualification is **PASS, human-reported**.

## 1. Starting repository and evidence

Branch `main`; HEAD `2595c265d1df389086ea61d5a880a3629db2662f`.
Initial `git status --short` was empty. The audit is committed at this HEAD;
its earlier baseline was `bd2671fa37c2e87042fd8699b0cc8f4a63d9daa4`.
VERSION `0.1.2`, VERSION_CODE `3`, both unchanged.

Read AGENTS completely, the simplification audit, README, Gradle/Nix definitions,
both workflows, release/signing documentation, reviewed-input consumers and tests,
and relevant historical qualification evidence. Prior biometric normal/strict/APK
and human Nix passes, and the signing migration's final human Nix/device evidence,
are historical evidence only. The audit itself ran no builds. No sibling repository,
worktree or sub-agent was inspected or used. No Nix command was executed by the agent.

## 2. Previous Gradle acquisition paths

The package already used Nix Gradle. Development, CI and jailed agents received it
too, but supported local commands used the separately pinned wrapper distribution.
Bootstrap verified wrapper JAR/properties, ran textual toolchain probes, optionally
wrote locks/verification metadata, then invoked wrapper builds. CI already used
the Nix package, with no wrapper acquisition.

## 3. Final shared Nix selection

All four environments retain the actual common bindings in flake.nix:
`jdk = pkgs.jdk17_headless`; `gradle = pkgs.gradle_9.override { java = jdk; }`.
The package receives these bindings through callPackage; both shells use
androidPackages; jailed-codex retains those extraPkgs and forwarded environment.
Only a stale flake comment changes. No agent-specific flake input is merged or removed.

package.nix still asserts Gradle 9.8.0, uses its existing patched Gradle/fetchDeps
integration and fixed package-deps.json cache. SDK license acceptance, selected
unfree allowlist, package selection, both SDK variables, JAVA_HOME, and patched
Build Tools AAPT2 override remain unchanged. Nix/direnv is the supported CLI
prerequisite; direct `gradle` runs inside that active environment.

## 4. Removed files

Seven deletions:

```text
gradlew
gradlew.bat
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper.properties
bootstrap-m0.sh
tools/verify-wrapper.py
tools/verify-m0-toolchain.sh
```

Root Wrapper task configuration and obsolete wrapper .gitattributes entries are
removed. Gradle's built-in wrapper task may remain available; no repository wrapper
distribution is supported. No replacement launcher, bootstrap or validator is added.

## 5. Bootstrap/probe responsibility accounting

Nix already supplies the supported JDK/Gradle/SDK versions and SDK package shape.
AGP/native Gradle resolve the plugin, compile against the selected SDK, and build
both variants. Strict dependency locking/verification and disabled SDK/JDK downloads
remain. No independent safety requirement requiring a replacement probe was found.
Unsupported host SDK/JDK/local.properties use remains prohibited in active docs;
the deleted textual probe no longer independently checks unsupported environment
shape. The supported environment is Nix, rather than a bootstrap acceptance test.

README now documents both native Gradle lock/metadata update invocations, followed
by review and qualification, and the existing human `nix run .#update-package-deps`
procedure. None of these dependency-update commands was executed in Phase A.

## 6. Remaining Gradle callers

AGENTS and README use direct Gradle for baseline/final, focused tests/compilation,
strict-clean, runtime boundary and installDebug commands. Intentional dependency
refresh examples use direct Gradle. The app lockfile's command comment is updated.
tools/release/test-build-metadata.py now executes `gradle` from PATH in its temporary
project. package.nix retains native Gradle build/check/update tasks. CI/release
workflows still invoke the Nix package and ci shell. Device harness code has no
wrapper caller and remains unchanged. Historical review reports remain untouched.

## 7. Release-controller compatibility

source.py changes only REVIEWED_PATHS membership: remove the two deleted wrapper
inputs. release.py, source readiness/authorization logic, automatic allocation,
tag logic, Actions parser and every existing Python release test remain intact.
The JVM supply-chain guard removes the same two paths; all four guard tests remain.
No production Java source, manifest, resource or other app test changes.

## 8. Reviewed-input migration

Inventory has ten retained inputs. Removed only wrapper JAR/properties entries.
Changed exactly three retained expected digests:

- build.gradle.kts: delete only Wrapper configuration.
- flake.nix: replace only the obsolete acquisition-path comment.
- app/gradle.lockfile: replace only its regeneration-command comment.

Every other retained digest is unchanged. Both consumers still reject malformed,
missing, duplicate, unknown and unsorted entries and validate retained bytes.
Reviewed the exact diffs before qualification; no blanket hash regeneration.
Direct source_ready validation passed on the real changed checkout without invoking
preparation, contacting release remotes or mutating metadata/tags.

Final retained inventory (all hashes validated against current file bytes):

```text
eb6dbefbbdef74771d5d428969248dd936b40955c0b8286adcb07ff8db41e98c  app/build.gradle.kts
386571264bf427250cc301c85a43475c0320bc568453263027440ad440b495ec  app/gradle.lockfile
3b0aa45af4a0cca158ead62f9ea77e8f9aca257d17b5af5dbf8639d6948ef620  build.gradle.kts
22efaa36cdbc4212cb62beb29837199812865f2b303caaef124b07e9915fc1a0  buildscript-gradle.lockfile
44728fcfd8529462cb415b186ee4ce3400343dd911d325b6a965bd386b9f7749  flake.lock
d944df40fa472176eca93957e4f56891fbd96388d16a1f03f2243c1f8ddb48da  flake.nix
3db21dde187dd9e9cba8400b07e91374f69131754994c004b2810949be97c27b  gradle.properties
0492327c6a5445cec7c140630598f97853074573a4acf2e2d8565ce481cbfba4  gradle/verification-metadata.xml
8d4ac6dd7fc08cc3793ae46f21cb885cf1300ccef992fe9f399fe6982f89c133  package-deps.json
948017ca7cb553ecfd91a9f3eab56a1db4870c7eb4c0086cb2f4527503f89141  settings.gradle.kts
```

## 9. Automatic versionCode allocation

Unchanged: canonical VERSION/CODE parsing, max consumed annotated-tag code plus
one, duplicate/overflow refusal, legacy historical code parsing and canonical
new-source metadata authority. Disposable release tests exercise these paths.
Real VERSION/CODE and real tag/ref state were not mutated.

## 10. Human-reviewed source before CI

Local prepare-release and release-tag Nix apps, human review/commit/push ordering,
exact source SHA binding, annotated tag creation/resumption and dispatch interface
are unchanged. Phase A does not invoke live preparation, tagging or release jobs.

## 11. CI source policy

Both workflow files are byte-identical to starting HEAD. CI never edits or commits
source. Triggers, permissions, full-SHA Actions, Nix installation, action-policy,
unchanged-input check, four release jobs, concurrency/refusals, protected signing
and same-run artifact handoffs are preserved. No workflow/settings dispatch or
remote qualification is claimed.

## 12. Signing and artifact invariants

Production signing identity/configuration/algorithms, native apksigner and zipalign,
fingerprint/scheme checks, signed hashes, payload comparator, provenance and
publication behavior are unchanged. APK verifier is byte-identical to starting HEAD;
no new manifest policy or class-inventory simplification. No production signing
credentials were read or used. Existing disposable signing smoke is retained.

## 13. Observed version identities

| Input | Identity |
| --- | --- |
| Gradle | 9.8.0; Nix executable /nix/store/rm902prah3x4ziil8hpyyffzappk5wjz-gradle-9.8.0/bin/gradle |
| JDK | 17.0.20.1+1-nixos; language/toolchain 17 |
| AGP | 9.4.0, unchanged root declaration |
| Application | org.totipo.android; 0.1.2 / code 3 |
| Compile / target / minimum SDK | 37 / 37 / 26 |
| SDK platform | android-37.0; revision 2, extension 22 |
| Build Tools / platform tools / command-line tools | 36.0.0 / 37.0.1 / 22.0 |

Active JAVA_HOME is `/nix/store/zanji5hpfg98zl65a1j8lxy1na04rhmx-openjdk-headless-17.0.20.1+1/lib/openjdk`.
Both SDK variables equal `/nix/store/x9gvmw0icgnpqz4wbvfr8dnnnlyan8sk-androidsdk/libexec/android-sdk`.
Observed java/gradle versions and SDK source.properties directly, without Nix
evaluation. Focused javaToolchains reports Java 17 from JAVA_HOME and disabled
auto-detection/download. No Java 25, AGP, SDK or runtime-library upgrade.

## 14. Dependency-input accounting

All lock resolution entries are identical to HEAD; app lockfile changes only a
comment. Other lockfiles, verification XML, package-deps.json, flake.lock,
settings.gradle.kts, gradle.properties and app/build.gradle.kts remain byte-identical.
No lock regeneration, checksum-metadata writing, dependency refresh or cache update.
Runtime remains NIO/core 0.2.0 and BC 1.86. Repository selection and variants remain.

## 15. APK comparison

Baseline APK copies and aapt2 badging are retained in ignored
`.gradle/phase-a-evidence/`. Baseline SHA-256:

```text
debug    2dfad74e408a1366da99bc39be7859afc4129752522463e28259931ea3375092
unsigned 7b8bccb2412043502af41a02a79e12b8759d207cb86f0a452d275191ee93abf3
```

Final strict-clean APKs have exactly the same two SHA-256 values above. Full-byte
comparisons against both baseline copies returned equal, independently of hashes.
Both final aapt2 badging records retain application ID org.totipo.android, versionName
0.1.2, versionCode 3, compile/target SDK 37 and minSdk 26. Both existing APK verifier
commands passed. No unexpected artifact change or device-relevant output difference.
This is one local baseline/fresh-build comparison, not a universal reproducibility claim.

## 16. Host Gradle and artifact results

Evidence logs are ignored local files under `.gradle/phase-a-evidence/`.

| Check | Result |
| --- | --- |
| Baseline `gradle check :app:assembleDebug :app:assembleRelease` | PASS, 3m30s; 90 actionable tasks, 28 executed / 62 up-to-date; 339 tests, zero failures/errors/skips |
| Focused offline javaToolchains + OtpAuthSupplyChainGuardTest | PASS, 3s; 23 actionable, 3 executed / 20 up-to-date; four tests |
| Offline standalone metadata parser | PASS, 10s; one executed task; valid code/version accepted, 13 malformed codes and missing file rejected |
| Final normal checkpoint | PASS, 2m; 90 actionable, 7 executed / 83 up-to-date; 339 tests, zero failures/errors/skips |
| Final strict offline clean checkpoint | PASS, 2m41s; 92 actionable / 92 executed, zero up-to-date; 339 tests, zero failures/errors/skips |
| Final debug / unsigned-release external APK verification | PASS, one direct command per variant against strict-clean outputs |
| Offline releaseRuntimeClasspath / Maven boundary | PASS, 2s; two tasks executed; NIO/core 0.2.0 + BC 1.86 |

Baseline and full gates retain embedded verifyReleaseApkBoundary. Wrapper verifier
alone is obsolete and removed; every other existing mandatory gate is retained.
No intermediate external APK-verification set was run. An early attempt to read
identity hashes while baseline packaging was unfinished found no APK outputs yet;
copies/hashes were recorded after baseline PASS. No build/test gate failed.

Active documentation local links and `git diff --check`: PASS. The non-historical
Git caller scan has zero gradlew/wrapper/bootstrap/probe path references; release
documentation only describes the intentional inventory migration. Gradle resolves
directly from the Nix store; no wrapper files remain to download a distribution.
The Nix package internally names its launcher gradlew in JVM properties; that is
upstream Nix Gradle internals, not a repository wrapper acquisition path.

## 17. Release tooling and negative cases

Baseline release discovery: 58/58 PASS (15.435s). First post-migration suite:
58/58 PASS (13.806s). The later lockfile command-comment/digest edit required renewed
final source readiness and release-fixture validation; final results recorded below.
Existing suites cover source identity, allocation/preparation/idempotence/dirty
refusals, exact tags and source binding, Actions grammar, signing/provenance/payload
contracts and publication failures. Mutating Git operations occur exclusively in
temporary repositories/bare remotes; fake publication/signing tests use no live release.
Real guard, action-policy and source_ready commands passed.

Final frozen release suite: **58/58 PASS**, 11.008s, no skips. It reruns the real
CLI fixture with the final protected lock-comment digest and no wrapper inputs.
Disposable signing smoke: **PASS**, one unchanged script invocation against the
strict unsigned APK. Native TEST-only signing/verification, single certificate,
v2/v3, alignment, exact unsigned-to-signed ZIP payload, six expected rejections,
unsigned hash/provenance immutability and temporary-key deletion all passed. Keys
were generated in /tmp and deleted before repository verifiers resumed. No production
or persistent qualification key was used, and nothing was installed or published.

## 18. Device evidence

Zero physical/device qualification runs, installs or signing-identity migrations.
Device harnesses, qualification identity owner/migration functionality and production
app source remain unchanged. Existing SAF/strong-biometric/Keystore/lifecycle/clipboard/
Syncthing evidence is preserved as historical evidence, with no new device PASS claim.
Tooling-only change does not itself require the full device matrix. APK comparisons
below determine whether any device-relevant output difference needs further attention.

## 19. Human Nix

**PASS, human-reported.** After the final source-freeze request, the owner reported
`nix flake check passed`. Human Nix invocations: one; agent Nix executions: zero.
No log, duration or task/cache breakdown was supplied; no independent Nix execution
is claimed. Frozen included-input hashes and the implementation diff identity were
revalidated unchanged after the report. Only this excluded report is updated, so
the PASS is not invalidated and no renewed gate is required.
No independent routine Nix build or dependency-cache regeneration requested.
checks.android and packages.default still share androidPackage;
build/check/installCheck still assemble both APKs, execute tests/lint/dependency/APK
checks and release Python suites. The existing filtered-package skips for excluded public
identity/workflow integration tests are unchanged; full-checkout host discovery ran
all 58 tests without skips. checks.release-apps still exercises both app help
paths and disposable incomplete-worktree refusals. No qualification derivation removed.

## 20. LOC accounting

| Surface | Added | Removed | Net removed |
| --- | ---: | ---: | ---: |
| Code (Java/Python/shell/Nix/Gradle, including comments) | 2 | 454 | 452 |
| Configuration (.gitattributes, lock command comment, wrapper properties) | 1 | 13 | 12 |
| Total code/configuration | 3 | 467 | 464 |
| Reviewed inventory data | 3 | 5 | 2 |

Wrapper JAR removed: 47,623 binary bytes. Documentation/report diff is recorded in
final Git statistics separately; it is not implementation code reduction.
Physical added/deleted diff lines include comments/blanks; binary JAR is separate.
No savings from later Option B/release/APK/device simplification are counted.

## 21. Source freeze, inputs and invalidations

- Production source stable: unchanged app main/debug/release, manifests/resources.
- JVM tests stable: only two obsolete inventory paths removed from one guard.
- Build/package configuration stable: only Wrapper block deletion, flake comment and
  lock command comment; shared derivations and package.nix unchanged.
- Qualification harness/tooling stable: obsolete probes removed, metadata runner and
  source inventory migrated; device/signing/APK tools unchanged.

Included Gradle-source/evaluator/cache manifest contains 166 existing regular files,
recorded in ignored `frozen-inputs.json`; SHA-256 of its sorted JSON with final LF:
`aeabc785ccf90c5b9a7d92f1d430037ec47842f083b339eb4e9fb4ff15b6f5a9`.
It records actual file SHA-256 values, not only names or tool versions. Deleted paths
are accounted for in the Git diff. Protected-input migration assertion passed:
exactly two wrapper entries removed and exactly the three described digests changed.

package.nix still selects app/src, gradle, tools and its exact build/lock/version/
license/branding files, excluding .git/.gradle/build/.direnv components and symlinks.
flake/package/cache/lock evaluator inputs remain included in the freeze evidence.
AGENTS, README, release documentation and review report are outside the actual package
and release-app check consumers. This report is intent-added without staged contents
before requesting human Nix. Later excluded report-only result updates do not invalidate
the gates. No included-input change after final normal/strict checkpoints is planned.
The lock comment edit occurred before final normal, and did not invalidate any full
final gate. No failed/repeated full Gradle gate. Frozen manifest revalidated after
strict/signing/artifact checks and after report intent-to-add established membership;
no included input changed. The implementation diff
excluding this report is recorded in ignored implementation.diff; SHA-256:
`3187438e44e7ebfadb151d33dc023f4d0e951a8914ac9bc67097c81af4b3e9fe`.

## 22. Final Git inventory and execution counts

Final branch `main`, HEAD `2595c265d1df389086ea61d5a880a3629db2662f`.
All changes unstaged/uncommitted; report has intent-to-add membership only.
`git diff --cached --name-only` is empty and `git diff --cached --quiet` passes.
`git diff --name-only` contains exactly the nineteen paths in this complete status:

```text
 M .gitattributes
 M AGENTS.md
 M README.md
 M app/gradle.lockfile
 M app/src/test/java/org/totipo/android/OtpAuthSupplyChainGuardTest.java
 D bootstrap-m0.sh
 M build.gradle.kts
 M flake.nix
 D gradle/wrapper/gradle-wrapper.jar
 D gradle/wrapper/gradle-wrapper.properties
 D gradlew
 D gradlew.bat
 M release/README.md
 A review/ANDROID_NIX_FIRST_SIMPLIFICATION_REPORT.md
 M tools/release/reviewed-inputs.sha256
 M tools/release/source.py
 M tools/release/test-build-metadata.py
 D tools/verify-m0-toolchain.sh
 D tools/verify-wrapper.py
```

| Execution category | Count and result |
| --- | --- |
| Baseline full Gradle | 1 ordinary, PASS |
| Focused Gradle | 1 offline combined toolchain/four-test invocation, PASS |
| Standalone metadata harness Gradle | 1 offline temporary-project invocation, PASS |
| Final normal full Gradle | 1 ordinary, PASS |
| Strict-clean full Gradle | 1 offline fresh invocation, PASS |
| Final runtime graph/boundary Gradle | 1 offline two-task invocation, PASS |
| Gradle version observation | 1, no build tasks |
| Direct final artifact-verifier set | 2 external APK commands, both PASS; wrapper command removed |
| Embedded Gradle APK verifier | 3 executions: baseline, final normal, strict |
| Release Python discovery | 3 runs: baseline, initial migration, final lock-comment inventory; 58/58 PASS each |
| Disposable signing smoke | 1 PASS; six expected negative cases; six nested existing APK-verifier calls (three positive-path, three pre-rejection unsigned checks) |
| Physical harness / device installs | 0 |
| Agent / human Nix | 0 / 1; final gate PASS, human-reported |

The repeated release discovery is explained in section 17; no repeated full
Gradle gate or failed qualification required diagnosis. Frozen input hash is
unchanged after all checks. `git diff --check` and cached-content invariant PASS.
No generated/cache/build/secret paths entered Git; only the report is added.
No commit, tag, push, workflow/settings change, live preparation, production signing
or release/publication operation. Ignored local evidence remains available for review.

## 23. Remaining limits and later phases

Phase A is complete with host checks and the final human-reported Nix PASS.
All changes remain uncommitted, with no staged contents; included inputs remain frozen.
Native dependency update procedures are documented but deliberately not executed. No public-release
readiness claim. Source inventory, Actions parser, release controller, APK verifier,
device packaging and signing migration remain in scope for separate owner decisions;
Phase A does not begin their simplification. Unsupported non-Nix/Windows wrapper
convenience is withdrawn. Existing visible AAPT2 experimental and Gradle deprecation
warnings remain; no unrelated warning suppression or dependency repair.
