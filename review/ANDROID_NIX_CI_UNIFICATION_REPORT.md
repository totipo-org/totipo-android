# Android Nix / CI unification report

Status: COMPLETE — debug and release APK qualification share the single Android derivation; fresh non-Nix validation PASSED and human normal flake check PASSED. Earlier intermittent test failure remains unexplained and is recorded below. Remote CI: NOT RUN.

## 1. Starting HEAD/state

Started on clean `main` at `3493126a06aa6eeccb905b8f1bc87fc95b2f6717`.
`git status --short` was empty. Existing Android product/M3D work was committed;
recent history includes `994433b` (Complete Syncthing end-to-end qualification),
`d2c50f2` (fix CI build), and starting HEAD. Actual current files consume Java
0.2.0 / Vault Format v1/r19 and canonical r19 Totipo branding. No older desktop
audit SHA was used as the Android baseline.

## 2. Baseline Android validation

Before edits:

```sh
./gradlew check :app:assembleDebug :app:assembleRelease
./gradlew --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug :app:assembleRelease
python3 tools/verify-wrapper.py
python3 tools/verify-apk.py app/build/outputs/apk/debug/app-debug.apk --debug-probe
python3 tools/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk --unsigned --no-debug-probe
git diff --check
```

All passed. Normal: 3m, 90 tasks (16 executed, 74 up-to-date).
Strict offline: 1m48s, 92 tasks executed. XML results: **259 JVM tests,
zero failures/errors/skips**. Existing AAPT2 experimental-option and Gradle
deprecation warnings remain visible. No failure was bypassed.

Baseline hashes after the completed clean rebuild:

| Input/output | SHA-256 |
| --- | --- |
| Debug APK | `8735e6c62f137d046b6bd446c1b38ee268f680a6af4fdbfe5d85d6939be22fd1` |
| Unsigned release APK | `881c444e4833593e81b129c87529b6dac30ca78563a1bd0b05fb80f50fc17e67` |
| package-deps.json | `8d4ac6dd7fc08cc3793ae46f21cb885cf1300ccef992fe9f399fe6982f89c133` |

The offline `:app:dependencies --configuration releaseRuntimeClasspath` report
confirmed app → `org.totipo:totipo-storage-nio:0.2.0` →
`org.totipo:totipo-core:0.2.0` → `org.bouncycastle:bcprov-jdk18on:1.86`,
with strict lock constraints and no other runtime modules. Maven-boundary
validation covers both debug/release compile/runtime configurations.

Manifest boundary: main manifest declares no permissions; exported launcher
MainActivity and exported OtpAuthEnrollmentActivity (VIEW `otpauth://totp`,
SEND `text/plain`) remain. Debug-only probe components remain debug-scoped.
APK verifier rejects CAMERA, INTERNET and broad storage permissions, unexpected
otpauth/SEND ingress, camera/QR/cloud/worker libraries, and standalone fixtures.
No component, permission or branding change was made.

## 3. Existing flake/package architecture

Inspected actual `flake.nix`, `package.nix`, `package-deps.json`, `flake.lock`,
workflow, README, Java dependency provenance and live M3D checklist.
The flake constructs `androidPackage` through exactly one
`pkgs.callPackage ./package.nix { inherit jdk gradle androidSdk; }` expression
per system. Package/check/app outputs are intentionally restricted to
`x86_64-linux`, matching `package.nix` meta.platforms and Linux CI. Development
shells remain available through the existing eachDefaultSystem structure.

## 4. checks.android / packages.default identity

Both assignments use the same let-bound value:

```nix
packages = pkgs.lib.optionalAttrs (system == "x86_64-linux") {
  default = androidPackage;
};
checks = pkgs.lib.optionalAttrs (system == "x86_64-linux") {
  android = androidPackage;
};
```

There is no second package builder for checks. This is structural identity,
not an agent-run Nix evaluation. No redesign or flake comment was needed;
`flake.nix` remains byte-identical, also preserving its JVM supply-chain digest.

## 5. package.nix qualification coverage

The existing derivation uses nixpkgs Gradle 9.8.0 (asserted), JDK 17 and the
locked composed SDK: platform 37.0, Build Tools 36.0.0, platform tools 37.0.1,
command-line tools 22.0. SDK license/unfree allowlist and AAPT2 override remain.
It sets a temporary writable Android/Java home and uses Gradle fetchDeps with
pinned `package-deps.json` responses, strict dependency verification,
no configuration cache and no build cache.

`gradleBuildTask = ":app:assembleDebug :app:assembleRelease"`; `doCheck = true` and
`gradleCheckTask = "check"`. Root check resolves the locked build classpath and
app check; app check includes testDebugUnitTest, lint, verifyMavenBoundary and
verifyReleaseApkBoundary (which depends on assembleRelease). Therefore this is
a meaningful build/test/lint/security gate. At starting HEAD the package only
explicitly assembled/inspected release. At the user's follow-up request, the
same derivation now assembles **both debug and release** and installCheck
verifies debug with `--debug-probe` alongside the installed unsigned release
with `--unsigned --no-debug-probe`. The prior direct-Gradle CI job's debug APK
boundary is thus retained inside the single flake-check authority.

Cache readiness still requires NIO/core 0.2.0 JAR plus module/POM and BC 1.86 JAR,
except during intentional cache update. Install still copies **only** the unsigned
release APK to the same output path. Debug signing uses temporary sandbox-local
development state; no signing configuration, secret or output artifact is added.
Cache update remains a separate human operation; its task list now matches both
assemblies plus check for future intentional refreshes. No cache refresh was run,
and the existing fixed package-deps cache remains byte-identical.

Read-only inspection of the installed pinned Gradle 9.8.0 nix-support/setup-hook
confirmed gradleBuildPhase expands the space-separated gradleBuildTask into
multiple task arguments, and gradleCheckPhase still runs check. No independent
builder, supplemental derivation, new resolution mechanism or flake change was
needed. Human Nix realization is still required to verify the extended path.

## 6. Why check + ordinary build was redundant

Both commands realize the same derivation and reuse the same store result.
A successful normal flake check followed by ordinary build is not two
independent qualification results. Materialization/result-link creation remains
useful separately; no check is lost by removing the ordinary build command.

## 7. Final command contract

- Normal developer/human/CI gate: `nix flake check path:.`.
- Package materialization/result link/output inspection: `nix build path:.`.
- Deliberate release/reproducibility comparison only: `nix build --rebuild path:.`.

Agent does not run Nix. Do not request an additional ordinary build after the
human flake check. Forced rebuild is not a generic milestone gate and does not
prove universal reproducibility.

## 8. CI removal and trigger coverage

Replaced the previous push/PR shell-bootstrap job plus main/manual check+build
job with one `qualification` job on push, pull_request and workflow_dispatch.
It runs exactly `nix flake check --print-build-logs path:.`, followed by the
existing diff/clean-input guard. This also gives PRs the package-inclusive gate.
No ordinary or forced Nix build remains in CI.

## 9. CI environment authority / static audit

One `ubuntu-24.04` job, 45-minute timeout. No setup-java, setup-android,
setup-gradle, direct Gradle invocation, bootstrap shell invocation, nix develop,
publication, artifact upload or secret reference. Nix package remains the
build/test authority. Permissions remain `contents: read`; checkout retains
`persist-credentials: false`. Static assertions confirmed one flake-check
occurrence and two pinned action uses. Remote execution remains unverified.

The former separate shell job assembled/inspected debug APKs and checked the
shell toolchain. Debug APK assembly and verification now occur in androidPackage
itself. The package continues to enforce its pinned toolchain, wrapper input
integrity and release APK boundaries. No direct-Gradle fallback is restored.

## 10. GitHub Actions immutable pin audit

| Action | Existing intended version comment | Immutable SHA |
| --- | --- | --- |
| actions/checkout | v7.0.1, matches Totipo Java | `3d3c42e5aac5ba805825da76410c181273ba90b1` |
| cachix/install-nix-action | v31.11.1 | `13d8dd58da0234aa297dedd986986ccb8e7f3e24` |

Both preserved unchanged. Read-only `git ls-remote` against each official
repository confirmed the exact version tags resolve to those SHAs. No action
was added. Sources: [checkout](https://github.com/actions/checkout/tree/3d3c42e5aac5ba805825da76410c181273ba90b1),
[installer](https://github.com/cachix/install-nix-action/tree/13d8dd58da0234aa297dedd986986ccb8e7f3e24).

## 11. Explicit noninteractive flake-config acceptance

Added installer `with.extra_nix_config` containing `accept-flake-config = true`.
The [exact pinned action.yml](https://github.com/cachix/install-nix-action/blob/13d8dd58da0234aa297dedd986986ccb8e7f3e24/action.yml)
exposes this input and forwards it to install-nix.sh. The pinned install script
appends it to installer Nix configuration. This explicitly accepts the reviewed
repository flake policy in noninteractive CI. No signature bypass is needed.

## 12. Cache/trust policy invariance

Flake configuration remains exactly the existing extra substituter
`https://cache.numtide.com` and key
`niks3.numtide.com-1:DTx8wZduET09hRmMtKdQDxNNthLQETkc/yaX7M4qK0g=`.
No new cache, trusted key, require-sigs setting or trust weakening was added.

## 13. Wrapper integrity coverage

Package check runs OtpAuthSupplyChainGuardTest.reviewedSupplyChainInputsRemainPinned,
which requires exact SHA-256 of wrapper JAR and properties. Both are included by
package.nix's `gradle` source selection; neither has the optional evaluator-input
skip. This independently enforces wrapper pins even though Nix builds use
Nix Gradle instead of executing gradlew. Local tools/verify-wrapper.py also
passed, checking Gradle 9.8.0 distribution URL, checksum and URL validation.
Removing ordinary build loses no wrapper guard.

## 14. APK verification coverage

Both app check and package installCheck run release APK verification. It checks
ZIP integrity/duplicates, packaged manifest ingress/forbidden permissions,
canonical launcher resource provenance and adaptive layers, product/controller
and Java/core/NIO/BC DEX presence, retired API absence, forbidden libraries,
debug/standalone/test-fixture exclusion and unsigned JAR/APK signing boundaries.
Package installCheck now also verifies the built debug APK and requires existing
diagnostic classes via `--debug-probe`. Release install/output semantics are
unchanged. No APK verifier code or expectations changed.

## 15. Current documentation and occurrence classification

Tracked-tree search covered flake check/build/rebuild/develop, cache update,
package-deps, installer/setup-java and gradlew. Classification:

| Occurrences | Classification/disposition |
| --- | --- |
| .github/workflows/ci.yml | CURRENT CI; unified as above |
| README managed shell/local Gradle sections | CURRENT DEVELOPER GUIDANCE; local iteration distinguished from CI |
| README Nix gate/cache validation | CURRENT QUALIFICATION GUIDANCE; one normal command |
| tools/device/M3D_SYNCTHING_CHECKPOINTS.md final Nix instruction | CURRENT QUALIFICATION GUIDANCE; one normal command, evidence semantics unchanged |
| README forced rebuild instruction | CURRENT RELEASE/REPRODUCIBILITY GUIDANCE; explicitly scoped |
| Existing review/*.md command records | HISTORICAL REPORT; all unchanged |
| TOTIPO_JAVA_DEPENDENCY.md, package.nix updater diagnostic, lockfile regeneration comments | Dependency provenance/maintenance; unchanged |

README states shared derivation identity, real build/test/check coverage, no
second build requirement, materialization-only ordinary build and agent/human
responsibilities. No separate live release/contributor/agent document was found.
No repository AGENTS.md was found. The new report records current evidence.

## 16. Release/rebuild disposition

Retained forced rebuild only as an optional deliberate release/reproducibility
comparison in README. Removed it from generic cache-refresh qualification.
Existing historical M0 rebuild results remain untouched and are not evidence
that this workflow ran remotely or that all platforms reproduce universally.

## 17. flake.lock invariance

Unchanged bytes; SHA-256
`44728fcfd8529462cb415b186ee4ce3400343dd911d325b6a965bd386b9f7749`.
No Nix command or input update was run. Root nixpkgs stays
`39ad350a0602fa0a58a544344e3e9187526ea45c`.

## 18. package-deps invariance

Unchanged bytes; SHA-256
`8d4ac6dd7fc08cc3793ae46f21cb885cf1300ccef992fe9f399fe6982f89c133`.
No update-package-deps, lock writing or verification-metadata generation.

## 19. Narrowly authorized package.nix extension

Starting SHA-256:
`ec4c3b488b8db9b221eb00a5b44f44c6e4e2afefd6ae29dcdc97e81d400a3deb`.
User explicitly authorized extending the single package after the initial
cleanup. Changes are limited to adding assembleDebug to build/update task lists
and debug APK verification to installCheck. Existing check, cache, strict flags,
release copy and release verifier remain unchanged. Final SHA-256:
`a3fc9dea90ee2d98903cc62ab8690c2e8eaddf48425c266f3975de0159350f50`.
No package/dependency-cache architecture redesign.

## 20. Production/input invariance

All starting tracked files were SHA-256 inventoried before edits. Final byte
comparison PASSED after the package extension: only the four expected tracked
files below differ. Expected differences
only: workflow, README, package.nix, live checklist plus this new report. Production Java, manifests,
resources/branding, Gradle build scripts/locks, verification metadata, wrapper
inputs, flake and historical reports remain unchanged. No Android behavior,
sync/token lifecycle or M3D evidence-semantic change is intended.

## 21. Final non-Nix validation

Initial cleanup validation (before the debug package extension):

Normal command passed: 4s, 90 tasks (5 executed, 4 from cache, 81 up-to-date).
Strict offline command passed: 1m42s, 92 tasks executed. Final XML results:
**259 JVM tests, zero failures/errors/skips**. Wrapper verifier, debug APK verifier
(`--debug-probe`), unsigned release verifier (`--unsigned --no-debug-probe`)
and `git diff --check` passed. Final runtime graph is exactly the baseline
NIO 0.2.0 → core 0.2.0 → BC 1.86 graph with strict constraints.

Final debug APK SHA-256:
`8735e6c62f137d046b6bd446c1b38ee268f680a6af4fdbfe5d85d6939be22fd1`.
Final unsigned release APK SHA-256:
`881c444e4833593e81b129c87529b6dac30ca78563a1bd0b05fb80f50fc17e67`.
Final package-deps SHA-256:
`8d4ac6dd7fc08cc3793ae46f21cb885cf1300ccef992fe9f399fe6982f89c133`.
All exactly match the completed baseline clean rebuild. This is local evidence,
not a forced Nix rebuild or universal reproducibility claim.

Fresh validation after the debug package extension (2026-10-10): normal
Gradle command PASS (1s, 90 tasks: 5 executed, 5 from cache, 80 up-to-date).
Strict offline clean rebuild PASS (2m39s, 92 tasks executed); 259 JVM tests,
zero failures/errors/skips. Wrapper verification PASS; debug `--debug-probe`
and release `--unsigned --no-debug-probe` APK verification PASS. Runtime graph
remains NIO 0.2.0 → core 0.2.0 → BC 1.86 with strict lock constraints.

Fresh final hashes:

| Input/output | SHA-256 |
| --- | --- |
| Debug APK | `8735e6c62f137d046b6bd446c1b38ee268f680a6af4fdbfe5d85d6939be22fd1` |
| Unsigned release APK | `881c444e4833593e81b129c87529b6dac30ca78563a1bd0b05fb80f50fc17e67` |
| package-deps.json | `8d4ac6dd7fc08cc3793ae46f21cb885cf1300ccef992fe9f399fe6982f89c133` |

All match the original baseline. Diff whitespace and unchanged-index checks
PASS. Production/test Java, manifests/resources, branding, locks, wrapper,
verification metadata, flake.nix/flake.lock and historical reports remain
byte-identical. No test changes or bypasses; the earlier intermittent failure
remains recorded separately. No Nix, cache regeneration or remote CI run by agent.

## 22. Human one-command Nix result

**Initial attempt FAILED — human reported.** The requested `nix flake check path:.` reached
Android JVM tests and reported:

```text
AndroidVaultControllerTest > outboundBlockedCreateWriteLockAndBindingChangeRemainResponsiveAndBounded FAILED
    java.lang.AssertionError at AndroidVaultControllerTest.java:85
```

This demonstrates that the package-inclusive gate exercises the real Android
tests. Line 85 is shared `accept(BooleanSupplier)`: wait for idle, then assert
that operation admission returned true. The excerpt does not identify the
caller/operation or establish a root cause. Full failure trace requested.

A targeted agent Gradle run with offline/no-daemon/no-configuration-cache/
no-build-cache/rerun-tasks/strict-verification and `:app:testDebugUnitTest --tests
org.totipo.android.AndroidVaultControllerTest.outboundBlockedCreateWriteLockAndBindingChangeRemainResponsiveAndBounded`
passed (15s, 22 tasks executed). This single local pass does not supersede the
human Nix failure or prove flakiness. No test or production Java was edited.
The human confirmed that the two-line Gradle summary is the only failure detail
visible in the derivation output. A further bounded series of 20 targeted strict
offline/no-configuration-cache/no-build-cache/rerun-tasks/strict-verification
Gradle invocations all passed. No failures were suppressed or repaired. This does not establish the cause or invalidate the
Nix failure. Targeted runs replace the local test-result XML with the selected
test only; the earlier 259-test validation totals above remain historical results
of the full baseline/final runs, not totals from the reproduction runs.

The helper's idle observation and operation submission are separate steps;
concurrent admission changes are a possible explanation, not a confirmed finding.
No production/test/build inputs changed during this investigation. A diagnostic
rerun of the same flake-check gate with failed build-directory retention can
provide the XML stack trace if the failure recurs; it is not an extra ordinary
package build or a replacement qualification gate.

**Diagnostic rerun PASS — human reported on 2026-10-10.** In response to the
request for `nix flake check --keep-failed path:.`, the human reported “it passed
this time.” No successful-run output was supplied. This is the same
package-inclusive flake-check gate with diagnostic failure retention enabled;
no ordinary build or forced rebuild was requested or run by the agent.
The normal documented command remains `nix flake check path:.`.

Structural `checks.android == androidPackage` identity plus the human-reported
successful check provided evidence for the initial infrastructure cleanup, before
the debug package extension. It does not qualify the newly extended derivation.
The earlier failure also directly confirms that this gate reached Android JVM
tests. The failure's cause remains **UNRESOLVED**: subsequent passes do not prove
it fixed, and no failed-build XML was obtained. Track recurrence of the named
controller test separately; preserve failure artifacts for diagnosis. No
production behavior, test assertion, timeout or check was weakened to get a pass.
Agent has run no Nix. Remote CI remains unrun.

**Fresh human gate for debug/release extension: PASS — human reported on
2026-10-10.** In response to the request for only `nix flake check path:.`, the
human reported “nix flake check passed.” No command output was supplied, so this
is explicitly human-reported evidence. This result applies to the extended
single androidPackage that assembles both APKs, runs existing Gradle check and
verifies debug plus installed unsigned release APK boundaries. Structural identity
with checks.android establishes package inclusion. No additional ordinary build
or forced rebuild was requested. The earlier test failure remains unexplained;
this pass does not claim a test fix or universal reliability.

## 23. Remote CI

**Remote CI: NOT RUN.** No push or dispatch. After human review/commit/push,
inspect the exact committed GitHub Actions run separately.

## 24. Java/spec follow-ups

No Java/spec files edited. Their repositories are not available in this workspace,
so their present flake coverage was not independently confirmed here. The supplied
cross-repository audit describes Java's flake as development-environment-only
without meaningful checks, and spec's flake as lacking meaningful checks.
Follow up by verifying those states: Java should expose its actual Gradle,
publication/consumer and release-guardrail qualification through flake check;
spec should expose its actual Python/Go/conformance/integrity suite through
flake check. No implementation design or new claims about those repos are made.

## 25. Desktop convention comparison

Uses the user-supplied reviewed desktop convention: one normal flake check,
ordinary build for materialization, deliberate rebuild separately, explicit CI
flake-config acceptance. No desktop repository was edited or re-audited.
Android already shares the package derivation and needed no Nix redesign.

## 26. Final Git state

Final audit: branch `main`, HEAD `3493126a06aa6eeccb905b8f1bc87fc95b2f6717`;
index unchanged (`git diff --cached --quiet` passes), diff whitespace check passes.

```text
 M .github/workflows/ci.yml
 M README.md
 M package.nix
 M tools/device/M3D_SYNCTHING_CHECKPOINTS.md
?? review/ANDROID_NIX_CI_UNIFICATION_REPORT.md
```

All work remains unstaged/uncommitted on starting main/HEAD.
No staging, commit, tag, release, push, CI dispatch or agent Nix execution.
