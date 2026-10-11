# Android support/tooling architecture audit

Status: complete, read-only architecture inspection; recommendations only.
Classification: **DOCS_ONLY — architecture / support-surface audit**.
Executable behavior is unchanged. This is not product, dependency, packaging/release,
or qualification-tooling implementation work.

The minimum justified system is a pinned build environment, Gradle's build/graph
checks, an external artifact boundary verifier, one cohesive no-secret release
implementation, a protected signing boundary, and selective physical regression
harnesses. The largest simplification opportunity is shared device packaging and
release context/validation, not deleting independent artifact or physical evidence.
There is no evidence for a wholesale Python-to-Java port before consolidation.

## 1. Starting HEAD/state

Inspection began with these commands, in this repository only:

```text
git branch --show-current  => main
git rev-parse HEAD         => 33eb9a79b3826d9c34c7679003823570369691d6
git status --short         => empty
cat VERSION               => 0.1.2
cat VERSION_CODE          => 3
```

Prerequisites are met. Preparation automation is committed at `252b7ee`; release
metadata at `f763008`; two-command UX and Actions maintenance at `33eb9a7`.
The committed [Actions/UX report](ANDROID_ACTIONS_RELEASE_UX_REPORT.md), section 2,
records the first successful published 0.1.2: release ID 409339487, publication
2026-10-11T02:10:31Z, source `f763008a1564359bb16c79ee08d236a1eb0f7b91`, annotated
tag object `f0c2803ed42386dbb8098da57484ebaa1a802c3e`, and three public assets.
That report's sections 3 and 11 establish the committed pins and two-command UX.
The [preparation report](ANDROID_RELEASE_PREPARATION_AUTOMATION_REPORT.md), section
10, records removal of recovery workflow/program/tests; none is in the current
tracked file list. Historical recovery evidence remains.

These are committed historical results, not fresh release, hosted-byte or build
verification performed by this audit. The successful release preceded the current
Actions upgrade; current workflow source and its committed qualification evidence
are used alongside the successful 0.1.2 phase behavior, not conflated with its run.

## 2. Scope and methodology

Used Git history/tracked paths, source reads and `rg` references; inspected both
workflows, all 22 tracked Python files, all three committed `.sh` files, wrapper
scripts, Nix/Gradle configuration, release data/docs, device Java instrumentation,
debug-only support adapters, support tests, and relevant milestone reports.
`.github/scripts/` does not exist. No generated audit program was added.

Callers below are actual imports, subprocesses, task dependencies, manifests,
workflow steps or documented human commands. A test mentioning a filename is not
an operational caller. Human invocations in historical reports are labeled as
such. Absence of an automatic caller does not make a physical harness dead.
External/private operator scripts are outside the available evidence.

Support is distinguishable: `tools/`, workflows, build configuration and explicit
debug probes are support; `app/src/main`/`release` runtime behavior is product.
The debug `ApplicationVaultBackend` participates in the debug runtime and is
therefore an integration seam, not a freely deletable script. Ordinary controller,
security, provider, reconciliation and view tests remain product evidence. Tests
are included here only where the subject is tooling or an explicit mixed boundary.

Approximate LOC counts physical nonblank lines excluding full-line comments and
Java block-comment lines; inline comments, Python docstring lines, embedded XML,
and inline Python in shell count with their owning file. This is an intentionally
simple cognitive-surface measure, not statements or exact language-token LOC.
Generated binaries, lock/data files and documentation are separately inventoried.

## 3. Support-language inventory

| Language/form | Files | LOC approx | Entry points | Support test files | Required understanding |
| --- | ---: | ---: | --- | --- | --- |
| Java | 26 | 3903 | 9 device Instrumentation classes, host InboundFixture main, 4 debug Activities; internal helpers | OtpAuthSupplyChainGuardTest, AuthPerfSafetyTest, ProbeLogicTest | Build pins; physical/security/UI changes; debug diagnostics. Not required for every release operation |
| Kotlin / Gradle Kotlin DSL | 3 | 183 | root/app tasks and settings | Parser tested by Python temporary Gradle harness; JVM supply-chain guard | Android builds, dependency updates and qualification |
| Python | 22 | 2588 | 19 CLI-capable files (including direct test runners); source.py and two tests are import/discovery-only | test_release.py, test_publication.py, test_source.py, test-build-metadata.py, test-syncthing-e2e.py | Release implementation and host device orchestration; no external Python packages |
| POSIX/Bash shell | 5 | 245 | bootstrap-m0.sh, verify-m0-toolchain.sh, signing-smoke.sh, gradlew, .envrc | No dedicated shell test file; release static guards and signing smoke | Setup/refresh, signing trust boundary; wrapper normally treated as vendor code |
| Nix | 2 | 219 | dev shells, apps, checks, package, formatter | checks.release-apps; Python source-wrapper shape assertions | Hermetic build/cache/toolchain and release app wiring |
| GitHub Actions YAML | 2 | 211 | CI and manual protected release (4 jobs) | test_release.py static contract | CI maintenance and release authority boundaries |
| Other: Windows batch | 1 | 57 | gradlew.bat | No dedicated test | Optional Windows wrapper; supported qualification target remains x86_64-linux |
| Other: debug XML | 2 | 27 | debug manifest registration and extraction rules | Source/boundary tests | Debug-only Activity/backup boundaries |

Total counted code/config: **63 files, approximately 7433 lines**. Java comprises
11 device sources (2275), 12 debug implementation sources (1395), and 3 tooling
tests (233). Python comprises device tooling/tests (12 files, 980), release
implementation/tests (8 files, 1444), and external APK/wrapper verifiers (2, 164).
The 19 CLI-capable Python files are the 12 device files, release.py,
verify-signed-apk.py, payload.py, test-build-metadata.py, test_release.py and the two
external verifiers. source.py is an imported library; test_source.py and
test_publication.py are discovery-only suites. CLI capability is not public UX.

Other support data: VERSION, VERSION_CODE, branding-provenance.json, flake.lock,
package-deps.json, gradle.properties, settings/repository policy, root/app dependency
locks, Gradle verification XML, wrapper properties/JAR, public certificate/digest,
LICENSE, `.envrc`, `.gitignore`, `.gitattributes`. These are authorities/inputs, not
new programming languages. There are 47 tracked historical `review/` artifacts at
starting HEAD (45 Markdown, two JSON); this report adds one Markdown artifact.

## 4. Executable entry-point inventory

Here R = repository/artifact read-only (may create temporary files or reports),
M = mutates build outputs, source/refs, device or remote state. Security-sensitive
means it affects trusted inputs, secret handling, artifact identity, or test/device
isolation. Release-sensitive means part of release correctness or its qualified
build. Documentation: L = live README/AGENTS/checkpoint doc; H = historical report;
I = source-only/internal. Flags describe capabilities, not actions run in this audit.

| Entry point | Implementation / actual caller | Visibility | R/M | Security | Release | Docs |
| --- | --- | --- | --- | --- | --- | --- |
| nix develop default / ci; direnv | flake.nix; human/.envrc | Human | M environment | yes | yes | L |
| nix flake check (android, release-apps) | flake/package; human, CI, release qualify | Human/CI | M outputs | yes | yes | L |
| nix build default | same androidPackage; human inspection, release.py prepare | Human/internal | M outputs | yes | yes | L |
| nix run prepare-release | wrapper → release.py → source.execute; human | Human | M VERSION files | yes | yes | L |
| nix run release-tag [--json] | wrapper → same CLI/source; human | Human | M local/remote tag | yes | yes | L |
| nix run update-package-deps | nixpkgs fetchDeps updateScript; human | Human | M cache inventory | yes | yes | L |
| nix fmt | flake formatter nixpkgs-fmt; conventional human entry | Human | M Nix formatting | no | indirectly | I |
| ./bootstrap-m0.sh [--refresh-dependencies] | human README; wrapper/toolchain checks → Gradle | Human | M outputs; refresh trusted metadata | yes | yes | L |
| tools/verify-m0-toolchain.sh | bootstrap | Internal/diagnostic | R | yes | yes | L via bootstrap |
| gradlew / gradlew.bat | human, bootstrap, metadata/lifecycle builder | Human/internal | M task-dependent | yes | yes | L / Windows I |
| check, :app:check | Gradle lifecycle → app tests/lint/Maven/APK and root build graph | Human/CI/Nix | M reports/APKs | yes | yes | L |
| assemble, assembleDebug, assembleRelease, clean | Gradle lifecycle wiring; human/package | Human/internal | M outputs | yes | yes | L |
| testDebugUnitTest [--tests], compileDebugJavaWithJavac | AGENTS human iteration | Human | M outputs | yes | yes | L |
| lint, dependencies/releaseRuntimeClasspath, buildEnvironment | Gradle builtins; check/refresh/human | Human/internal | M outputs/cache | yes | yes | L |
| verifyBuildDependencies | root check | Internal/human | R graph | yes | yes | I |
| :app:verifyMavenBoundary | app check, human final boundary | Internal/human | R graph | yes | yes | L |
| :app:verifyReleaseApkBoundary | app check → assembleRelease → verify-apk | Internal | M assembly, R verify | yes | yes | L AGENTS |
| wrapper | Gradle configured vendor generation task; intentional human update | Human | M wrapper files | yes | yes | L prose |
| installDebug | Gradle builtin; human README | Human | M device | yes | no | L |
| :app:bundleReleaseClassesToCompileJar | fallback in lifecycle builder | Internal | M classes | no | indirectly | I |
| verify-apk.py [--unsigned/--debug-probe/--no-debug-probe] | Gradle, package, release, device wrappers, human AGENTS | Both | R | yes | yes | L |
| verify-wrapper.py | bootstrap; human final ladder | Both | R | yes | yes | L |
| release.py check-release | diagnostic source validation; human/tests | Human diagnostic | R with isolated Git objects | yes | yes | L |
| release.py identity / guard | workflow qualify / documented local guard; tests | Internal/diagnostic | R | yes | yes | L |
| release.py prepare / bundle / finalize / publish | release jobs; prepare builds bundle, bundle reads, finalize copies, publish GitHub | Internal | M / R / M / M | yes | yes | L phase table |
| verify-signed-apk.py | release verify_signed; signing smoke; tests import | Internal/diagnostic | R | yes | yes | L responsibility |
| payload.py unsigned signed | release import compare; signing smoke CLI; tests | Internal/diagnostic | R | yes | yes | L responsibility |
| signing-smoke.sh unsigned.apk | human release README | Human | M disposable keys/artifacts | yes | yes | L |
| unittest discover tools/release test_*.py; test_release.py direct runner | workflow qualify, package installCheck, human | Both | M temporary fixtures | yes | yes | L |
| test-build-metadata.py → temporary verifyMetadata | human release README only | Human | M temporary Gradle outputs | yes | yes | L |
| device build-*.py (5 files individually below) | human, matching qualify wrappers | Human/internal | M test APK/fixtures | yes | no production release | H |
| check-row-reveal.py | human row/layout reports | Human | M disposable test package | yes | no | H |
| qualify-security.py --build-only/--prepare/--update-harness/--phase lifecycle,first,restart/--remove | human biometric/F09 evidence | Human | M test package/keys/evidence | yes | no | H |
| qualify-token-lifecycle.py | human older lifecycle report | Human legacy | M installed app/tests/settings | yes | no | H |
| qualify-saf-bootstrap.py / qualify-saf-outbound.py | human M3C/M3B reports | Human | M app/tests/disposable provider tree | yes | no | H |
| qualify-syncthing-e2e.py build/sign/install/serve/capture/begin/finish/restart | human checkpoint document | Human | M artifacts/app/evidence; observes provider | yes | no | L |
| test-syncthing-e2e.py | human checkpoint doc → unittest oracle | Human | R oracle/temp memory | no | no | L |
| InboundFixture.main | three SAF builders; outbound qualifier extra different-vault fixture | Internal | M public vault files | yes isolation | no | H |
| device Instrumentation.onStart entries (9) | generated manifests → adb am instrument, detailed below | Human/internal | M fixture/device state except observers | yes | no | H/L |
| debug Activity entries (4) | committed debug manifest → launcher/adb human; backend/helpers below | Human debug | M fixtures/probes, timing | yes | no | H |
| CI workflow | push, pull_request, workflow_dispatch → qualification | Human/event | M build outputs | yes | yes | L |
| Release workflow | deliberate main workflow_dispatch → qualify/sign/verify/publish | Human | M signing/GitHub | yes | yes | L |

This inventories repo-native entry points and invoked Gradle lifecycle/qualification
tasks, not every dynamically generated AGP task or every underlying Git/coreutils
subcommand. Helpers without a CLI are inventoried with their callers below.

## 5. Caller/call graph

```text
README human / AGENTS
  bootstrap-m0.sh
    verify-wrapper.py
    verify-m0-toolchain.sh -> java, Nix gradle, inline Python, adb
    gradlew -> check + assembleDebug + assembleRelease
  final normal / strict Gradle -> root check
    verifyBuildDependencies -> locked buildscript resolution
    app:check -> testDebugUnitTest + lint + verifyMavenBoundary
              -> verifyReleaseApkBoundary -> assembleRelease -> verify-apk.py
  final external -> verify-wrapper.py + verify-apk.py(debug/release) + Maven graph
  affected physical checks -> device paths below

flake check / CI qualification / release qualify
  checks.android == packages.default -> package.nix
    fetchDeps(package-deps.json), pinned toolchain, strict build/check
    install unsigned APK
    installCheck -> unittest discover test_*.py + verify-apk.py(debug/installed release)
  checks.release-apps -> generated app --help + incomplete-caller refusal

flake apps prepare-release / release-tag
  store release.py -> source.py (imports release.py) -> caller Git worktree
    remote/local history + source/pin/public identity readiness
    prepare -> VERSION/VERSION_CODE; tag -> exact annotated object + single-ref push

release workflow qualify
  release.py identity -> Git source/tag + expected_identity + guard
  Python tests; flake check
  release.py prepare -> identity + same drv evaluation/materialization
    -> qualified_provenance -> unsigned_check -> verify-apk.py
                           -> apk_metadata(aapt2), sha
sign job
  release.py bundle -> identity + schema + unsigned SHA/metadata + verify-apk.py
  trusted SDK/JDK resolution (before secrets)
  shell-only secret step -> apksigner (no repository helper)
  release.py bundle (after secrets) -> signed-candidate transport
verify job
  release.py finalize -> bundle_check + verify_signed -> verify-signed-apk.py
    -> shared provenance/bind_unsigned/metadata + verify-apk.py unsigned
    -> apksigner + zipalign + verify-apk.py signed
  -> payload.compare -> verified bundle
publish job
  release.py publish -> bundle_check + verify_signed + payload.compare
  -> published absence + paginated draft absence -> gh draft/create/upload
  -> draft lookup via list -> gh download -> all asset SHA equality
  -> verify_signed(hosted APK) -> final identity -> PATCH draft=false
```

Device edges:

```text
build-otpauth-ingress-tests -> OtpAuthIngressRegression + OtpAuthUiObserver
  -> generated two instrumentation registrations; manual signing/install/adb
build-saf-inbound-tests -> InboundFixture -> SafInboundRegression
qualify-saf-outbound -> build-saf-outbound-tests
  -> InboundFixture + SafOutboundRegression + LocalStoreQualification
qualify-saf-bootstrap -> build-saf-bootstrap-tests
  -> InboundFixture + SafBootstrapRegression + LocalStoreQualification
qualify-token-lifecycle -> build-token-lifecycle-tests -> TokenLifecycleRegression
qualify-security -> TokenLifecycleRegression + SecurityRegression
  -> detached final release DEX/resources, self-targeted instrumentation
check-row-reveal -> RowRevealRegression + debug APK DEX -> detached instrumentation
qualify-syncthing-e2e build -> SyncthingE2eRegression
  -> generated production-target observer -> serve/broadcast capture
  -> validate host oracle <- test-syncthing-e2e imports via importlib
```

Debug edges: debug manifest registers StorageProbeActivity → ProbeDocuments/
ProbeLogic, LocalNioProbeActivity → LocalNioQualification, AuthPerfActivity →
AuthPerfBenchmark/DebugDisposableCreation/DebugVaultTiming, DebugTotpFixtureActivity
→ DebugTotpFixture → debug ApplicationVaultBackend. Backend also wraps normal
open/create with DebugVaultTiming; product Application/controller calls the
variant-selected backend. JUnit invokes the three support-test classes. Release
source_pins reads the JVM test's Map.entry inventory, a real data dependency.

No `.github/scripts` edges exist. `test-build-metadata.py` and device oracle tests
are **not** selected by release `test_*.py` discovery (hyphen names); they are
explicit human commands. Python source tests and static workflow tests skip some
full-source assertions when signing/workflow/evaluator files are absent in the
filtered package. Thus package tests are not proof of every full-repository guard.

## 6. Component disposition table

Each row has exactly one primary disposition. Grouped data/doc rows enumerate
cohesive non-executable inputs; executable files are individually listed in the
Python/device/debug tables. Origin abbreviations in tables expand exactly to:
D = DURABLE_DESIGN_REQUIREMENT; R = REGRESSION_FROM_REAL_FAILURE;
T = TEMPORARY_DIAGNOSTIC_THAT_BECAME_PERMANENT;
L = LEGACY_FROM_OLDER_WORKFLOW; C = CONVENIENCE; U = UNKNOWN.
An origin can be inferred from named milestone evidence without implying a
previous exploit. Confidence concerns the recommendation, not a fresh test pass.

| Component | Language | LOC approx | Callers | Responsibility | Unique invariant | Origin | Disposition | Confidence |
| --- | --- | ---: | --- | --- | --- | --- | --- | --- |
| .github/workflows/ci.yml | Actions YAML | 27 | push/PR/manual | package qualification + unchanged checkout | ordinary CI qualifies same package and refuses generated trust-input drift | D; Nix CI unification | KEEP | HIGH |
| .github/workflows/release.yml | YAML/Bash | 184 | manual protected main dispatch | four authority-separated phases | signing secrets unavailable to repo helpers; publication token absent during signing; deliberate exact source dispatch | D/R; signed machinery/publication reports | KEEP_BUT_SIMPLIFY | HIGH |
| flake.nix | Nix | 127 | human/CI/release | pins toolchains, apps/checks/shells | package/check derivation identity and provisioned tools; apps execute in caller worktree | D/C | KEEP_BUT_SIMPLIFY | HIGH |
| package.nix | Nix/shell | 92 | flake package/check/cache update | filtered hermetic build/test/install | cached dependency responses, strict verified inputs and verified installed unsigned artifact | D/R; Nix unification/home fix | KEEP_BUT_SIMPLIFY | HIGH |
| build.gradle.kts | Gradle Kotlin DSL | 32 | Gradle | root metadata/task/plugin wiring | AGP graph strictly resolved; sole VERSION source; wrapper generation pins | D | KEEP | HIGH |
| settings.gradle.kts | Gradle Kotlin DSL | 16 | Gradle | repository/module policy | no per-project repositories; Totipo Maven authority; app inclusion | D | KEEP | HIGH |
| app/build.gradle.kts | Gradle Kotlin DSL | 135 | Gradle | Android build + Maven boundary + external APK wrapper | variant graph and selected dependency edges are verified where Gradle sees them; canonical code/SDK/build type | D/R; Java repin/version preparation | KEEP_BUT_SIMPLIFY | HIGH |
| bootstrap-m0.sh | Bash | 25 | human README | setup checks + optional trust-input refresh | needed refresh capability, but wrapper/toolchain/full-check orchestration overlaps | L/C; M0 | MERGE_CANDIDATE | HIGH |
| tools/verify-m0-toolchain.sh | Bash + inline Python | 41 | bootstrap | asserts active Nix SDK/JDK and no local.properties | useful environment inspection overlaps provisioned pins; merge into setup boundary | L; M0 | MERGE_CANDIDATE | HIGH |
| tools/release/signing-smoke.sh | Bash + inline Python | 82 | human release README | trusted-tool test signing and negative fixtures | actual SDK signing preserves qualified payload and rejects signer/tampering/scheme failures without production key | R/D; signed machinery/alignment | KEEP_BUT_SIMPLIFY | HIGH |
| gradlew | vendor POSIX shell | 96 | human/support scripts | wrapper launch | exact pinned Gradle executable reachable without host Gradle | D | KEEP | HIGH |
| gradlew.bat | vendor batch | 57 | optional Windows human; no CI caller | vendor wrapper counterpart | no distinct current Linux qualification invariant; upstream pair is cheap | C | UNKNOWN | MEDIUM |
| gradle/wrapper properties + JAR | properties/binary | data | gradlew, verifiers, JVM guard | distribution/JAR identity | wrapper bytes and download hash pinned | D | KEEP | HIGH |
| gradle.properties | properties | data | Gradle/JVM guard | strict verification, SDK/JDK download policy | no automatic SDK/toolchain downloads or unverified resolution | D | KEEP | HIGH |
| app/gradle.lockfile + buildscript-gradle.lockfile | data | data | Gradle, JVM guard, bootstrap | exact dependency versions | deterministic production/test/build graph resolution | D | KEEP | HIGH |
| gradle/verification-metadata.xml | XML data | data | Gradle/JVM guard | dependency artifact checksums | lock versions alone cannot authenticate dependency bytes | D | KEEP | HIGH |
| flake.lock | JSON data | data | Nix/JVM guard/source pins | evaluator/toolchain revisions | same toolchain/cache implementation from pinned inputs | D | KEEP | HIGH |
| package-deps.json | JSON data | data | package.nix/fetchDeps/JVM guard | Maven/lint response cache | cold sandbox build cannot substitute live dependency responses | D/R; SDK index failure documented in README | KEEP | HIGH |
| VERSION + VERSION_CODE | text data | 2 | Gradle/package/release source | sole mutable release metadata | consumed-code policy and canonical version inputs | R; failed 0.1.1 duplication/preparation report | KEEP | HIGH |
| branding-provenance.json | JSON data | data | verify-apk | reviewed canonical icon hashes/source | branding source identity, rather than only icon existence | D | KEEP | HIGH |
| release/signing/android-release-cert.pem + .sha256 | PEM/text | data | release expected_identity/signed verify/human | permanent public signer authority | APK signer must match production identity, not self-declared candidate | D | KEEP | HIGH |
| AGENTS.md | process Markdown | prose | agents/humans | qualification/invalidation ladder + Java contract | final evidence follows frozen inputs; focused iteration cannot waive final gates | D/R; biometric evidence | KEEP_BUT_SIMPLIFY | HIGH |
| README.md | live Markdown | prose | humans | build/development/refresh/qualification commands | contributors can reproduce intended environment and deliberate trust-input refresh | D/C | KEEP_BUT_SIMPLIFY | HIGH |
| release/README.md + release/signing/README.md | live Markdown | prose | humans | release commands, protected configuration, public identity/custody | two-command procedure and human-managed protected signer setup remain discoverable | D/R | KEEP | HIGH |
| TOTIPO_JAVA_DEPENDENCY.md | live provenance Markdown | prose | maintainers/AGENTS | exact released Java artifact/source provenance | dependency reviewers can trace runtime identity and distinguish guidance pin | D | KEEP | HIGH |
| LICENSE | text | prose | package/distribution | distribution license | license accompanies source qualification inputs | D | KEEP | HIGH |
| .envrc | shell | 1 | direnv human | enter default flake shell | convenience only; environment responsibility elsewhere | C | MERGE_CANDIDATE | LOW |
| .gitignore + .gitattributes | Git policy | data | Git/humans | generated/private exclusions and line endings | accidental private/generated staging and platform line-ending drift are reduced | D/C | KEEP | HIGH |
| app/src/test/java/org/totipo/android/OtpAuthSupplyChainGuardTest.java | Java | 62 | JUnit/check; source.py reads digest data | reviewed source inputs and metadata-independent stable pin | reviewed trust-input bytes cannot silently drift; data coupling can shrink | D/R | KEEP_BUT_SIMPLIFY | HIGH |
| app/src/testDebug/java/org/totipo/android/debug/AuthPerfSafetyTest.java | Java | 103 | JUnit debug/check | synthetic diagnostic equivalence/exclusion/log safety | retained diagnostics use non-secret synthetic inputs and stay out of release | T/D | KEEP_BUT_SIMPLIFY | MEDIUM |
| app/src/testDebug/java/org/totipo/android/debug/ProbeLogicTest.java | Java | 68 | JUnit debug/check | provider probe flags/redaction/owned cleanup | conditional on UNKNOWN probe need, not a separate reason to preserve it | T | UNKNOWN | MEDIUM |
| review/* at starting HEAD (47 artifacts) | Markdown/JSON | prose/data | humans; no effective build/check caller | historical qualification/provenance evidence | historical record, not live executable invariant | historical evidence | HISTORICAL_ONLY | HIGH |
| tools/device/M3D_SYNCTHING_CHECKPOINTS.md | process Markdown | prose | human M3D qualification | executable checkpoint procedure | distinct agent facts/human observations/transport reports cannot be collapsed into claimed convergence | D/R; M3D resume | KEEP_BUT_SIMPLIFY | HIGH |

## 7. Python inventory

All imports are standard library plus neighboring repository modules. No pip,
pytest, requests, YAML parser or other external Python package is required.
Complexity is qualitative: low = one narrow action; medium = SDK/fixture pipelines;
high = multiple mutable states, Git/history/network or evidence protocols.
S/R flags mean security/release critical. Tests/callers are traced, not inferred
from test filenames. The following is also the per-file disposition inventory.

| Component | Language | LOC approx | Callers | Responsibility | Unique invariant | Origin | Disposition | Confidence |
| --- | --- | ---: | --- | --- | --- | --- | --- | --- |
| tools/release/release.py | Python | 258 | workflow, flake apps, source, verifier, tests, smoke | source identity, provenance, bundle, publish; high; S/R | source/provenance/hosted-byte contracts required, but module/CLI/global ROOT boundaries should merge coherently | D/R; 0.1.0 publication + version fix | MERGE_CANDIDATE | HIGH |
| tools/release/source.py | Python | 276 | release.py lazy import; test_source | consumed history, preparation, exact tag/push; high; S/R | version/tag policy needed; circular release import and mutable ROOT should become explicit context | R/C; failed tagged 0.1.1 + preparation/two-command UX | MERGE_CANDIDATE | HIGH |
| tools/release/payload.py | Python | 43 | release import, smoke CLI, test_release | full ZIP payload comparison; medium; S/R | entry/order/header/compression equality across signing; keep distinct from signature validity | R/D; alignment/payload investigation | KEEP | HIGH |
| tools/release/verify-signed-apk.py | Python | 43 | release verify_signed, smoke, test_release importlib | signer/schemes/alignment + metadata/boundaries; medium; S/R | a valid signature must have exact expected signer/schemes and qualified metadata | D/R | KEEP_BUT_SIMPLIFY | HIGH |
| tools/verify-apk.py | Python | 153 | Gradle/package/release/device/human; AuthPerfSafety source assertion | manifest/branding/DEX/ZIP/unsigned boundary; high policy density; S/R | build success alone does not prove packaged permission/ingress/debug exclusion/payload boundaries | D/R; accumulated milestones | KEEP_BUT_SIMPLIFY | HIGH |
| tools/verify-wrapper.py | Python | 11 | bootstrap, AGENTS final, README | wrapper JAR/distribution pins; low; S/R | verify launcher identity before executing wrapper | D; M0 | KEEP | HIGH |
| tools/release/test_release.py | Python | 319 | unittest discovery human/workflow/package | schema/ZIP/signature/publication + workflow shape; high; S/R | behavioral adversarial cases and no-secret boundary must remain; shape assertions can shrink | D/R; version/publication/pin fixes | KEEP_BUT_SIMPLIFY | HIGH |
| tools/release/test_publication.py | Python | 62 | same discovery | HTTP status/draft/refusal behavior; medium; S/R | auth/network errors never mean absence; existing drafts never overwritten | R; failed 0.1.0 publication | KEEP | HIGH |
| tools/release/test_source.py | Python | 409 | same discovery | temporary repos/remotes, real CLI and wrapper shape; high; S/R | consumed tags, idempotence, no force/main/unrelated push, caller ownership | R/C; preparation/two-command UX | KEEP_BUT_SIMPLIFY | HIGH |
| tools/release/test-build-metadata.py | Python | 34 | human release README, not discovery/package | exact parser extracted into disposable Gradle; low/medium; S/R | malformed/missing code never defaults; extraction-by-source-shape can join build tests | R; failed 0.1.1 qualification/versionCode duplication | MERGE_CANDIDATE | MEDIUM |
| tools/device/build-otpauth-ingress-tests.py | Python | 40 | human M2D report | compile/D8/link isolated ingress/observer; medium; S, not R | packaging boilerplate overlaps other builders; preserve explicit class inclusion/redacted build failure | D; M2D replacing M2C | MERGE_CANDIDATE | HIGH |
| tools/device/build-saf-inbound-tests.py | Python | 55 | human M3A report only | cached-jar fixture + instrumentation APK; medium; S | overlap with bootstrap/outbound fixture/link pipeline | L; M3A | MERGE_CANDIDATE | HIGH |
| tools/device/build-saf-outbound-tests.py | Python | 56 | qualify-saf-outbound | same pipeline plus local storage regression; medium; S | distinct outbound scenarios live in Java, not this duplicated builder | L/D; M3B/r19 | MERGE_CANDIDATE | HIGH |
| tools/device/build-saf-bootstrap-tests.py | Python | 56 | qualify-saf-bootstrap | same pipeline plus bootstrap scenarios; medium; S | distinct Join/initialize behavior lives in Java | L/D; M3C | MERGE_CANDIDATE | HIGH |
| tools/device/build-token-lifecycle-tests.py | Python | 38 | qualify-token-lifecycle only | old production-target lifecycle APK; medium; S | no unique scenario: security qualifier already compiles same Java and exposes lifecycle phase | L; older lifecycle milestone | DELETE_CANDIDATE | HIGH |
| tools/device/qualify-token-lifecycle.py | Python | 53 | historical lifecycle human command only | signs/replaces installed app, changes/restores timeout; medium; S | detached security qualifier runs same lifecycle instrumentation without historical signer/app replacement | L; superseded by biometric/F09 qualification | DELETE_CANDIDATE | HIGH |
| tools/device/check-row-reveal.py | Python | 69 | row/stability/reveal/layout human reports | build/sign/install/run/remove detached package; medium; S | actual Android row geometry/repaint/Sync presentation cannot be established by JVM tests alone | R/D; repeated row/layout regressions | KEEP_BUT_SIMPLIFY | HIGH |
| tools/device/qualify-security.py | Python | 94 | biometric and F09 human reports; AGENTS relevant subsystem policy | detached release-Dex lifecycle/biometric phases; medium; S | actual framework crypto, restart/inactivity, retired modal behavior on final source artifact | D/R; biometric/F09 | KEEP_BUT_SIMPLIFY | HIGH |
| tools/device/qualify-saf-outbound.py | Python | 93 | historical M3B human procedure | sign/install, fixture backup/mutation/restore, scenarios; high; S | SAF immutable publication and wrong-vault refusal needed; orchestration duplicates bootstrap and old signer paths | D/L; M3B/r19 | MERGE_CANDIDATE | HIGH |
| tools/device/qualify-saf-bootstrap.py | Python | 88 | historical M3C human procedure | same orchestration + Join/initialize/orphan scenarios; high; S | bootstrap-specific provider/local safety needed; duplicate wrapper | D/L; M3C | MERGE_CANDIDATE | HIGH |
| tools/device/qualify-syncthing-e2e.py | Python | 258 | checkpoint doc/historical reports, oracle test imports | observe, record before/after, assertions/fail latch; high; S | transport claims need explicit real provider/local/human evidence, not captured-only PASS | D/R; M3D/resume | KEEP_BUT_SIMPLIFY | HIGH |
| tools/device/test-syncthing-e2e.py | Python | 80 | checkpoint doc human; unittest.main | host evidence-oracle negatives; medium | changed vault/session/rewrite/tombstone evidence cannot pass as convergence | R; M3D resume oracle fixes | KEEP | HIGH |

Overlap/test detail: release.py/source.py share source policy and identity helpers;
verify-signed reuses release schema/metadata rather than a separate parser already.
`test_release` covers payload, signer parsing, schema and publication; `test_source`
covers source operations and real CLI fixtures; `test_publication` focuses HTTP and
drafts. Device builders/qualifiers have no dedicated host unit suites except the
M3D oracle; their tests are the compiled device regressions and historical physical
evidence. verify-apk has source guards/integration gate callers, not a complete
standalone malicious-APK fixture suite. verify-wrapper has no dedicated unit test.
These gaps are limitations, not justification to invent tooling during this audit.

## 8. Shell inventory

All three tracked `.sh` files are listed in section 6. Bootstrap is reasonable
orchestration but still carries M0 naming and repeated full-task invocations in
refresh mode. Its refresh branch runs resolution, a metadata-writing full gate,
then another ordinary full gate: later consolidation should preserve deliberate
trust review while examining whether the final repetition supplies new evidence.
Toolchain verifier mixes shell environment checks with Python property parsing;
that is not a reason to move parsing into Bash. It also runs system `gradle
--version` in addition to wrapper provisioning, so setup depends on two Gradle
launch paths even though ordinary builds use the wrapper.

Signing smoke combines narrow trusted-tool/key lifetime orchestration with inline
Python fixture creation, metadata generation and ZIP tampering. Keep the trusted
key-present interval shell/platform-only; move/merge fixture logic with existing
behavioral tests only after equivalent actual signing negatives are retained.
Do not trade Python reduction for Bash JSON/Git/APK parsers.

`gradlew`/`gradlew.bat` are standard vendor launchers; don't hand-rewrite them.
`.envrc` is one-line convenience. Flake generated wrappers only set explicit tools,
JDK/certificates and exec Python; package phases compose Gradle/copy/check commands.
Actions CI has a narrow unchanged-checkout shell block. Release dispatch regexes
are an early syntactic guard, not a complete release-history implementation. Its
secret-bearing shell is an appropriate intentional boundary: only trusted JDK/SDK
and coreutils, restricted PATH, unset JVM injection variables, umask/temp/traps,
env passwords, explicit schemes/alignment, key deletion. It must not become a
repository CLI call while secrets are present.

## 9. Nix responsibility audit

[flake.nix](../flake.nix) owns SDK/JDK/Gradle/Python provisioning, shell environment,
repo apps, formatter and composition. [package.nix](../package.nix) owns selected
source inputs, cached Maven/lint responses, strict build/check and installed APK.
Release business logic stays in Python. Nix app caller tests are orchestration
contracts, not Git policy implemented in Nix. `cacheReady` knows three required
coordinates/metadata formats: this duplicates runtime/version knowledge but is a
small preflight diagnostic for an ungenerated cache, not Maven semantic authority.

Default shell also pulls jailed-agents/llm-agents; ci shell does not add that agent
program. These inputs are developer convenience, not Android security/release
requirements. Consider making agent provisioning independently optional; audit
transitive evaluation effects before claiming a smaller lock. No runtime app
requires agent tooling. fetchDeps updateScript is generated by pinned nixpkgs,
with provenance through flake.lock; it is not an unexplained generated tool.

Effective source filter selects app/src, gradle, tools and exact build/lock/version/
license/branding files, excluding .git/.gradle/build/.direnv components and symlinks.
Evaluator inputs include package.nix/flake.nix/flake.lock/package-deps.json.
Release app snapshot is `${./tools/release}`. release-apps check captures generated
wrappers and literal fixture commands. Neither reads review/README/AGENTS. Therefore
this report is outside effective Android and release-app check inputs, even though
raw `path:.` snapshots may contain it. No Nix invocation is needed or requested.

## 10. Gradle responsibility audit

| Task/configuration | Responsibility | Proper owner / overlap |
| --- | --- | --- |
| root verifyBuildDependencies | resolve locked buildscript classpath under strict verification | Intrinsic build graph; Gradle sees AGP dependencies |
| app verifyMavenBoundary | inspect four debug/release compile/runtime graphs, selected/requested Totipo edges, direct NIO declaration, NIO→core, runtime core→BC, reject file/project/obsolete Totipo | Intrinsic graph authority; APK DEX presence is complementary, not a substitute |
| app verifyReleaseApkBoundary | Exec Python after assembleRelease | Wrapper, **not embedded independent Kotlin APK verifier**; same verify-apk.py policy |
| check/assemble/clean wiring | compose lifecycle tasks | Appropriate; final strict task behavior must survive |
| wrapper configuration | version/distribution generation checksum | Appropriate; external JAR verification checks bytes before executing it |
| VERSION/code parser + Android DSL | canonical metadata and SDK/package/build type | Intrinsic build input; release policy should agree on accepted source versions |
| dependency locks/checksum metadata/repository mode | fail closed resolution | Intrinsic; Nix cached response boundary independently constrains available bytes |

The Maven task is specialized graph logic and justifies Gradle Kotlin DSL even
though app source is Java. It is larger than the actual Android DSL and deserves
focused maintenance, not a port that loses requested-vs-selected/artifact knowledge.
It does not on its own reject every possible unrelated production Maven module:
reviewed locks/build pins and DEX exclusions supply additional constraints.

## 11. APK verification overlap matrix

V = verifies directly; C = calls the same verifier/shared helper; E = exercises in
smoke fixtures; P = source/build policy, not direct artifact proof; — = absent.

| Property | Gradle release APK task | verify-apk.py | verify-signed-apk.py | payload.py | signing-smoke.sh | Nix package/installCheck | release.py |
| --- | --- | --- | --- | --- | --- | --- | --- |
| forbidden/exact permissions; no service | C | V | C signed + unsigned | equality only | E/C | C both variants | C unsigned/signed |
| exact ingress filters/handler | C | V | C | equality only | E/C | C | C |
| backup flags explicit assertion | — | — | — | equality only | — | P source manifest | — |
| exact applicationId | — | resource names/handler assumptions, not explicit package assertion | C require_metadata | equality only | C | P Android DSL | V via aapt2 badging |
| versionName/versionCode | — | — | C provenance compare | equality only | C qualified metadata | P Gradle inputs | V observe unsigned; compare downstream |
| debug/test classes absent / debug classes present | C release | V | C release | equality only | E/C | C release + debug | C |
| branding source hashes/resource wiring | C | V | C | equality only | E/C | C | C |
| DEX presence/required classes/retired exclusions | C | V | C | equality only | E/C | C | C |
| exact Maven requested/selected graph | — | presence/exclusions only | C same limitations | — | — | C Gradle Maven task | qualified package only |
| ZIP CRC/duplicate names | C | V | C | V independent inventory | E/C | C | C |
| unsigned (v1 files + v2/v3 block absence) | C | V optional | C unsigned input | v1 files rejected, not general unsigned proof | E/C | C release | C |
| expected signer identity + one signer | — | — | V apksigner + fingerprint/provenance | — | E + wrong signer negatives | — | C + certificate DER hash/keytool |
| v1/v4 off; v2/v3 on | — | — | V apksigner output | v1 payload rejected | E signed generation/verify | — | C |
| signed vs unsigned exact ZIP payload | — | — | **not compared here** | V order/central/local headers/compressed+plain hashes | E + altered payload negative | — | V calls compare finalize/publish |
| alignment | — | — | V zipalign check -P 16 4 | header equality constrains changes | E preserve then verify | — | C |
| whole-file SHA-256 | — | prints V, no expected artifact hash | C unsigned binding | per-entry hashes | V before/after inputs | output verified, not independent expected hash | V provenance/checksum/hosted equality |

Important accuracy limit: earlier reports describe backup boundaries as passed;
current verify-apk.py does not explicitly assert allowBackup/fullBackupContent/
dataExtractionRules. Source manifests and product source guards are other evidence;
this audit does **not** claim a direct packaged-backup assertion that is absent.
Similarly verify-apk.py is not the exact version/applicationId verifier; those
checks reside in release metadata helpers and build DSL. Future simplification
must specify desired guarantees precisely instead of inheriting broad labels.

Nix's release install check repeats the Gradle release verifier on the copied
installed artifact, which usefully checks packaging destination. Debug verification
is additional coverage. Neither repeated call is independent bug defense. Signed
verification checks a new artifact and authority boundary, so repeated semantics
there are justified. Payload identity is deliberately distinct from signature
validity: a correctly signed altered payload must still fail.

## 12. Release tooling audit

The 0.1.2 behavioral path is reconstructable: prepare metadata → reviewed commit/
main → annotated tag → deliberate dispatch → qualify same package derivation →
unsigned provenance → protected SDK-only signing → independent no-secret verify →
draft/upload/download/reverify → publish. Current human frontend has exactly two
commands, while optional check-release remains internal diagnostic, not a third
required command. Recovery is absent. Historical 0.1.0/code1 and 0.1.1/code2 tags
remain consumed even though publication/qualification failed.

Repeated logic is partly consolidated already: signed verifier imports the one
schema/metadata/bind_unsigned implementation; source pins reuse the JVM Map.entry
inventory. Remaining fragmentation is release.py ↔ source.py circular import,
global ROOT/CERT/FP reassignment for store-vs-worktree execution, repeated broad
bundle validation on the same phase inputs, multiple neighboring fingerprint
syntax checks and differing version regex policies. source.py accepts canonical
stable numeric X.Y.Z; workflow/release request/provenance/Gradle/Nix patterns permit
broader or differently constrained versions. This is existing policy mismatch,
not permission to change released behavior here.

Make one explicit release context/library with source/history, provenance/artifact,
and publication operations behind the existing public commands before considering
language migration. Phase entry points are not all accidental: bundles cross jobs,
approval, signing and hosted transport; each consumer needs validation even with
shared code. Do not cache validation across secret/runner/network boundaries merely
to reduce calls. `identity()` checks VERSION and VERSION_CODE syntax, but not the
full consumed-history policy implemented in source.execute; retain the stronger
preparation/tag contract when consolidating.

Origin evidence: [signed machinery](ANDROID_SIGNED_RELEASE_MACHINERY_REPORT.md),
[version provenance fix](ANDROID_RELEASE_VERSION_PROVENANCE_FIX_REPORT.md),
[publication/recovery fix](ANDROID_RELEASE_PUBLISH_RECOVERY_FIX_REPORT.md),
[preparation](ANDROID_RELEASE_PREPARATION_AUTOMATION_REPORT.md), and
[Actions/UX](ANDROID_ACTIONS_RELEASE_UX_REPORT.md). A failed publication justifies
HTTP/draft ordering regression tests; it does not justify resurrecting recover.py.
VersionCode duplication justifies one authority and parser tests, not multiple
standalone parsers. Alignment investigation justifies actual signing/payload tests,
not permanent duplication of SDK metadata extraction.

## 13. Qualification tooling audit

| Layer | Inner loop | Final boundary | Release only | Physical only |
| --- | --- | --- | --- | --- |
| AGENTS ladder | classification + affected tests/compile | normal then strict offline clean, external set, relevant device, frozen human Nix | signing additions when applicable | relevant subsystem selection |
| Gradle graph/check/tests/lint | focused tasks | full normal/strict; APK Exec included | no | no |
| external wrapper/APK verifiers | only APK diagnosis when needed | exact strict outputs, both variants | signed verifier extra | no |
| Nix android/release-apps | no repeated agent Nix | human frozen check; CI package check | same derivation used in release | no |
| release Python suite | fast synthetic/source/workflow regressions | package discovery; full-source workflow qualify | mainly | no |
| metadata parser harness | focused metadata changes | explicit human command, not auto check | build/release metadata | no |
| signing smoke | diagnose signer/alignment only as needed | actual disposable signing when signing/tooling affected | yes responsibility | no |
| detached row/security harnesses | focused build/phase where needed | affected final artifacts; no blanket M3D | no | yes |
| SAF provider / M3D harnesses | affected transport/provider work | relevant provider/device behavior only | no | yes |
| M0 environment inspection | setup | not a repeated stable milestone gate | no | no |

Never part of an automatic current qualification path: build-saf-inbound,
build-otpauth-ingress, SAF qualifiers, old lifecycle wrapper, row runner and security
runner; their real callers are humans/historical procedures, with AGENTS selecting
subsystem checks generically. M3D has a live checkpoint doc but is not universal.
This is a discoverability/harness registry problem, not proof that all are dead.

## 14. Device harness audit

AGENTS still requires relevant physical evidence but does not name a mandatory
file per subsystem. Current means a usable responsibility under that policy, not
a fresh claim that the historical harness passes on today's device.

| Component | Language | LOC approx | Callers | Responsibility | Unique invariant | Origin | Disposition | Confidence |
| --- | --- | ---: | --- | --- | --- | --- | --- | --- |
| tools/device/TokenLifecycleRegression.java | Java | 171 | old builder; qualify-security lifecycle; SecurityRegression FixturePort | actual Edit/Delete/Resolve, rotation/Lock, automatic publication | framework modal retirement and real Java-authored lifecycle behavior | D/R; lifecycle/F09 | KEEP | HIGH |
| tools/device/SecurityRegression.java | Java | 194 | qualify-security first/restart | strong biometric crypto, inactivity, record/key corruption/removal, pending modal retirement | framework/Keystore/restart security cannot be proved by JVM fakes | D/R; biometric | KEEP | HIGH |
| tools/device/RowRevealRegression.java | Java | 648 | check-row-reveal | reveal/layout/geometry/repaint/Sync activity state | measured Android layout and stale presentation regression under delays | R; row/repaint/layout failures | KEEP_BUT_SIMPLIFY | HIGH |
| tools/device/SafInboundRegression.java | Java | 128 | inbound builder/manual adb | persisted grant, isolated import/same session/no provider write | useful assertions overlap outbound/bootstrap and M3D; don't delete without scenario map | L/D; M3A | MERGE_CANDIDATE | MEDIUM |
| tools/device/SafOutboundRegression.java | Java | 261 | outbound builder/qualifier | matching import/publication, blocked vaults, immutable provider/local behavior | durable scenarios; reflection/inventory/setup duplicates bootstrap | D; M3B/r19 | MERGE_CANDIDATE | HIGH |
| tools/device/SafBootstrapRegression.java | Java | 217 | bootstrap builder/qualifier | Join/initialize/orphan/different vault | durable bootstrap safety; shared setup/inventory can merge | D; M3C | MERGE_CANDIDATE | HIGH |
| tools/device/LocalStoreQualification.java | Java | 74 | bootstrap/outbound compiled call | coordinated local storage regression on ART/filesystem | create/open/storage ownership behavior on Android, complementary JVM checks | D/R; r19 storage reconciliation | KEEP_BUT_SIMPLIFY | MEDIUM |
| tools/device/InboundFixture.java | Java | 28 | SAF builders/outbound different fixture | Java-authored public VAULT/objects | valid current-protocol fixtures produced by released Java, no handwritten encryption/protocol | D | KEEP | HIGH |
| tools/device/OtpAuthIngressRegression.java | Java | 279 | ingress builder/manual instrumentation | hostile Intent/ClipData, confirm/cancel/lifecycle/wipe | Android ingress/lifecycle and secret retirement beyond URI parser unit tests | D/R; M2D | KEEP_BUT_SIMPLIFY | HIGH |
| tools/device/OtpAuthUiObserver.java | Java | 41 | ingress builder/own instrumentation/manual smoke | fixed public-fixture UI facts | useful external observation; label-specific observer duplicates other evidence collection | C; M2D camera handoff | MERGE_CANDIDATE | MEDIUM |
| tools/device/SyncthingE2eRegression.java | Java | 234 | E2E builder/serve | provider inventory + local snapshot + stable capture envelope | live remote/local immutable identity/session evidence distinct from transport human claims | D/R; M3D/resume | KEEP_BUT_SIMPLIFY | HIGH |

Packages/isolation and current-vs-historical disposition:

- Security and row use self-targeted **disposable packages**
  `org.totipo.securityqualification` and `org.totipo.rowrevealqualification`, with
  copied final release/debug DEX respectively. Security lifecycle is current F09
  follow-up evidence; biometric phases require human interaction. Row supports
  `SYNC_LAYOUT_ONLY`; avoid running the entire large row matrix for unrelated work.
- Old lifecycle qualifier replaces `org.totipo.android` with an APK signed using
  `.gradle/m1l-inspection/m1k-debug.keystore` and `.gradle/m1m-inspection/signing-password`.
  Same lifecycle Java now runs through the detached security package, so its two
  old Python entry points are superseded. Preserve the Java class.
- SAF test APK is `org.totipo.safqualification`, targeting **org.totipo.android**;
  controllers/local roots are isolated but provider permission/app context is real.
  Bootstrap/outbound wrappers replace the installed app and temporarily change
  `/sdcard/Documents/Totipo-M3A-Test`, using backup/restore. They retain historical
  signer/password/grant dependencies. Their responsibility remains relevant to
  provider changes; their procedure is not a portable fresh-device command.
- Inbound is a manual M3A builder plus instrumentation, with no current automatic
  qualifier. Its methods still exist in current controller; do not infer obsolete
  API from former Import/Publish UI labels. Outbound/bootstrap use those APIs too.
- Ingress package `org.totipo.ingresstest` targets the production Activity for
  regression and self-targets its external UI observer. It temporarily substitutes
  an isolated controller/cache vault and restores it; no real user vault is intended.
  Historical M2D describes its human signing/install path; none is auto-dispatched.
- M3D observer package `org.totipo.syncthingqualification` targets the installed
  product-like package, uses human-driven Syncthing and designated disposable
  folders. Sign/install depends on historical M3C signer output as well as old key
  paths. Keep its evidence protocol/oracle; simplify provisioning and separate
  fixture scenario names from durable provider assertions. It is not required for
  every UI or lifecycle change.

The [F09 report](ANDROID_STALE_SUBSCRIBER_OWNERSHIP_FIX_REPORT.md), sections 23–24
near its physical evidence, uses qualify-security lifecycle, not a newly added
standalone race tool. Deterministic F09 controller tests remain product tests.
The [biometric report](ANDROID_BIOMETRIC_INACTIVITY_LOCK_REPORT.md) explains why
harness corrections precede final source freeze: tools are Nix inputs. Historical
M3D/M3A/B/C reports retain evidence without becoming mandatory routine procedures.

Debug-only support inventory (included to avoid hiding non-product Java under app):

| Component | Language | LOC approx | Callers | Responsibility | Unique invariant | Origin | Disposition | Confidence |
| --- | --- | ---: | --- | --- | --- | --- | --- | --- |
| debug StorageProbeActivity.java | Java | 486 | debug manifest/launcher human | provider flags/create/partial write/name/cleanup experiments | exploratory capability; current provider harnesses overlap but do not reproduce every probe | T; M1B | UNKNOWN | MEDIUM |
| debug ProbeDocuments.java | Java | 87 | StorageProbeActivity | provider queries/bounds | support for above; not product ProviderTreeReader | T | UNKNOWN | MEDIUM |
| debug ProbeLogic.java | Java | 61 | StorageProbeActivity/ProbeDocuments/ProbeLogicTest | redaction/flags/owned cleanup/public patterns | required only while probe retained; don't preserve helper independently | T | UNKNOWN | MEDIUM |
| debug LocalNioProbeActivity.java | Java | 64 | debug manifest/human | run local primitive and Java storage checks | overlaps durable local-store instrumentation with additional diagnostics | L; M1C | MERGE_CANDIDATE | MEDIUM |
| debug LocalNioQualification.java | Java | 294 | LocalNioProbeActivity | local NIO primitive/auth/storage suite | merge durable filesystem cases into local-store harness, retain unique primitive cases | L/D; M1C/r19 | MERGE_CANDIDATE | MEDIUM |
| debug AuthPerfActivity.java | Java | 84 | debug manifest/human | fixed synthetic KDF matrix/direct ART experiments | diagnostic only; no current routine performance qualification contract | T; M1K/L/M | UNKNOWN | MEDIUM |
| debug AuthPerfBenchmark.java | Java | 110 | AuthPerfActivity/AuthPerfSafetyTest | synthetic released KDF invocation/equivalence | useful when diagnosing ART; not required for normal release correctness | T | UNKNOWN | MEDIUM |
| debug DebugDisposableCreation.java | Java | 25 | AuthPerfActivity | fixed disposable create timing | historical diagnostic; assess with perf suite | T | UNKNOWN | MEDIUM |
| debug reconcile/DebugVaultTiming.java | Java | 80 | debug backend/AuthPerfActivity/AuthPerfSafetyTest | timings around real open/create/store reads | integrated diagnostic, not routine physical/security invariant | T | UNKNOWN | MEDIUM |
| debug DebugTotpFixtureActivity.java | Java | 56 | debug manifest/human | public token/adapter smoke | overlaps detached row and ingress fixture paths; merge before delete | C; M2A | MERGE_CANDIDATE | MEDIUM |
| debug reconcile/DebugTotpFixture.java | Java | 33 | activity/debug backend | one-shot public Java-authored fixture | no unique invariant beyond consuming debug request safely | C | MERGE_CANDIDATE | MEDIUM |
| debug ApplicationVaultBackend.java | Java | 15 | variant-selected product controller | debug backend/timing/one-shot fixture seam | debug build must still execute real backend while any retained diagnostics are isolated | C/D | KEEP_BUT_SIMPLIFY | HIGH |
| debug AndroidManifest.xml + res/xml/storage_probe_extraction_rules.xml | XML | 27 | manifest merge/Android | register debug entries, backup exclusions | debug diagnostics exposure/backup configuration must stay bounded while retained | D | KEEP_BUT_SIMPLIFY | HIGH |

No UNKNOWN probe is a low-risk delete candidate. These are known callers and
known provenance, with uncertain future need; this does not trigger the stop
condition about inability to identify callers or generated provenance.

## 15. Test-of-tooling audit

| Test/component | Subject / caller | Durable behavior versus implementation shape | Disposition |
| --- | --- | --- | --- |
| test_source.py | source CLI/Git; discovery | strong real temp-repo refusal/idempotence/one-ref push tests; some exact flake-text checks brittle | KEEP_BUT_SIMPLIFY (section 7) |
| test_release.py | artifacts/provenance/signature/publication/workflow; discovery | behavioral schema/ZIP/negative/signature/ordering tests durable; exact job names/counts/indentation/action comment regex/retention count are shape guards | KEEP_BUT_SIMPLIFY |
| test_publication.py | absence/drafts/errors; discovery | behavioral fail-closed HTTP/network/listed draft regression, including a real historical failure | KEEP |
| test-build-metadata.py | real extracted Gradle parser; manual | behavior durable; delimiters/exact variable names and stale docstring '2/0.1.1' are shape/history | MERGE_CANDIDATE |
| test-syncthing-e2e.py | evidence oracle; manual | behavioral negative provider/session/tombstone/immutability cases, not device qualification itself | KEEP |
| OtpAuthSupplyChainGuardTest.java (62 LOC) | locked supply-chain inputs; JUnit/check, also source.py data consumer | reviewed hash identity meaningful; regex extraction from Java by Python brittle; synthetic metadata test partly mirrors hash implementation | KEEP_BUT_SIMPLIFY, HIGH; D/R |
| AuthPerfSafetyTest.java (103 LOC) | synthetic KDF diagnostics and source exclusion/logging; JUnit debug | equivalence/fresh arrays useful while diagnostic retained; source strings/reflection/log-loop/verifier token checks bind implementation | KEEP_BUT_SIMPLIFY, MEDIUM; T/D |
| ProbeLogicTest.java (68 LOC) | provider probe helper; JUnit debug | flags/cleanup ownership/redaction behaviors useful only while probe retained | UNKNOWN, MEDIUM; T |

The three Java test rows are substantial support components: callers and LOC are
above; their responsibility is guarding the named tools. Unique invariant for the
two KEEP_BUT_SIMPLIFY rows: reviewed trust-input bytes cannot silently drift;
retained auth diagnostics must use synthetic non-secret inputs and remain excluded
from release respectively. ProbeLogic's fate follows the UNKNOWN probe, not a
standalone justification from its tests.

Mixed source guards (BiometricSourceBoundaryTest, EnrollmentSourceGuardTest,
OtpAuthEnrollmentSourceGuardTest, TokenLifecycleSourceGuardTest, TotpSourceGuardTest,
CoordinationSourceGuardTest, BootstrapSourceGuardTest, SyncSourceGuardTest) mainly
guard product lifecycle/manifest/threading behavior. Their mentions of debug seams,
source sets or retired probes are mixed support assertions to update only if those
components change; **KEEP product tests outside pruning scope**. CoreDependencySmokeTest
also exercises actual released Java behavior/graph use, not merely script shape.
DailyDriverUx/row/controller/security/inactivity/reconciliation tests are not
candidates because of their directory or use of source inspection alone.

A later rewrite should carry behavioral contracts forward and deliberately remove
implementation-shape assertions of deleted tools; test count is not an invariant.
Workflow source guards still need coverage for the protected secret interval and
permissions, even if exact textual assertions are replaced. No tests ran here.

## 16. Duplication matrix

| Responsibility | Actual overlapping components | Classification | Decision |
| --- | --- | --- | --- |
| Git/tag validation | source.history/validate_tag/release_tag vs release.identity vs early workflow syntax | SHARED_LIBRARY_CANDIDATE | share canonical parsing/context; preserve fresh phase/pre-push/post-push checks |
| VERSION/versionCode parsing | Gradle, source.py, release request/provenance, package version assert, workflow regex | SHARED_LIBRARY_CANDIDATE | one policy/spec + cross-boundary tests; Gradle still consumes own build inputs; legacy parsing only tagged history |
| application ID | Android DSL, source.source_ready, release.APPLICATION_ID/apk_metadata, verifier descriptor/resource assumptions | INTENTIONAL_INDEPENDENT_CHECK | expected source identity vs actual artifact metadata; centralize neighboring Python constant |
| signer fingerprint syntax | expected_identity, verify-signed fingerprint (twice), provenance schema, device cert regexes | SHARED_LIBRARY_CANDIDATE | public identity DER binding and actual signer comparison distinct; share syntax and host harness SDK extraction |
| APK metadata extraction | release.apk_metadata reused by signed verifier; smoke imports provenance; device SDK invocations | SHARED_LIBRARY_CANDIDATE | already shared release parser; do not invent three ports; device signer extraction differs from package badging |
| SHA-256 | release.sha, payload per-entry, verify-apk branding/whole-file, JVM source pins, device inventories, coreutils smoke | INTENTIONAL_INDEPENDENT_CHECK | different byte objects/trust layers; adjacent same-purpose helper sharing okay; secret step stays coreutils |
| provenance parsing | release.load/validate reused by bundle/signed verify | INTENTIONAL_INDEPENDENT_CHECK | mostly repeated **calls** on new boundary inputs, not duplicated schema implementations |
| ZIP structure/CRC/duplicates | verify-apk vs payload.inventory | INTENTIONAL_INDEPENDENT_CHECK | artifact policy versus signed/unsigned pair equality; independent small checks valuable |
| Maven/runtime boundary | Gradle graphs, locks/checksums, Nix cacheReady, DEX descriptors, JVM pin inventory | INTENTIONAL_INDEPENDENT_CHECK | source resolution, available bytes and packaged result answer different questions |
| repeated release APK verifier | Gradle Exec, package debug/release installCheck, release bundle/signed layers | ACCIDENTAL_DUPLICATION for same bytes within phase; boundary rechecks intentional | no claim of independent implementations; retain installed artifact and cross-job checks, review same-phase repetitions |
| action pin checking | test_release exact inventory/all YAML, source.machinery_ready release-only immutable syntax | SHARED_LIBRARY_CANDIDATE | source check is weaker subset; one reusable no-secret policy, still invoke at readiness and CI |
| private-key guard | .gitignore, release.guard directory allowlist, source.source_ready tracked path/PEM scan, static workflow guard | INTENTIONAL_INDEPENDENT_CHECK | ignore ≠ refusal ≠ secret-step isolation; retain layers, merge repeated source scans internally |
| source-file hash inventory | JVM Map.entry, Python source_pins regex, test_source fixtures | SHARED_LIBRARY_CANDIDATE | one reviewed data authority; avoid treating test source syntax as release API |
| SDK compile/D8/link/manifest/ZIP | five builders, row/security/E2E builders | SHARED_LIBRARY_CANDIDATE | one narrow framework-harness packaging service/config with explicit class allowlist |
| local test signing/install/cert/alignment | old lifecycle, SAF qualifiers, E2E, row/security | SHARED_LIBRARY_CANDIDATE | portable test identities/isolation; not production signer code reuse inside secrets |
| SAF traversal/hash/reflection/setup | inbound/outbound/bootstrap Java and M3D observer | SHARED_LIBRARY_CANDIDATE | keep observer nonmutating and scenario-specific assertions; shared bounded fixture inventory |
| diagnostic source guards | tests asserting tool strings and verify-apk forbidden names | ACCIDENTAL_DUPLICATION when purely implementation shape | remove shape-only clauses alongside removed component; keep release exclusion itself |

## 17. Intentional independent checks

Build and artifact verification must remain different responsibilities: Gradle
knows resolved requested/selected edges and compile artifacts; ZIP/DEX/manifest
inspection checks the result. Nix cached inputs constrain downloads; Gradle strict
checksums authenticate resolution bytes. Neither replaces the other.

Public expected certificate and candidate apksigner output must remain separate
inputs. Signature validity cannot prove intended source payload. Payload equality
cannot prove signer identity. Hosted whole-file equality connects previously
proved properties to uploaded bytes; downloaded signer/boundary revalidation is a
useful additional independent operational check.

Independence is weaker than file count suggests: verify-signed imports release
schema/metadata; Gradle/Nix execute the same APK Python program. Three identical
parses of a provenance object in neighboring helpers do not defend against one
parser bug. Revalidation after runner/approval/signing/transport transitions does
still defend against input substitution. Keep those boundaries even if the code
moves to one internal library. A future combined CLI must not turn the build's
self-report into the only artifact proof or allow candidate metadata to define
its own expected signer/version.

## 18. Supply-chain/runtime surface

| Support area | Runtime/tools | Added surface / invariant |
| --- | --- | --- |
| Python release/source | Python stdlib, Git, OpenSSH/CA, JDK keytool; artifact phases add aapt2/apksigner/zipalign; prepare uses Nix; publish uses runner gh/network | no pip packages; Git/history/schema/publish logic justified; circular helper structure adds complexity without another invariant |
| external wrapper verifier | Python stdlib | minimal identity check before wrapper launch |
| APK verifier/payload | Python stdlib; APK policy uses pinned aapt2 | semantic artifact checks and pair equality; no extra downloaded verifier library |
| Gradle | JDK17, pinned wrapper9.8.0, AGP9.4.0, SDK37/build-tools36, locked Maven build/test/lint graph | intrinsic Android build; JUnit and lint/build transitive tools are already pinned inputs |
| Nix build | Nix, pinned nixpkgs Gradle fetchDeps/MITM response cache, SDK/JDK/Python/Git, standard shell/coreutils | offline/cold sandbox reproducibility boundary; provenance of generated updateScript is pinned nixpkgs |
| Nix developer shell | above + jailed-agents/llm-agents/default agent program, direnv optional | convenience beyond minimum Android support; ci shell avoids agent program |
| Actions | Ubuntu24.04 runner/bash/coreutils/gh; four SHA-pinned action repositories (checkout/Nix install/upload/download); Node runtimes inside actions | runner/token/protected environment authority and artifact transport; protections configured outside source |
| signing shell/smoke | Bash/coreutils (base64/mktemp/rm/sha256sum/sed/cut), JDK keytool, SDK apksigner | trusted key lifetime boundary; smoke inline Python only outside key-present interval |
| device tooling | Python stdlib, JDK javac/java/keytool, SDK android.jar/d8/aapt2/zipalign/apksigner/adb, cached core/NIO/BC jars, real device | framework/provider/Keystore evidence; old local signer paths add operator state without unique test invariant |
| M3D physical transport | above + human-operated external Syncthing and desktop application | interoperability observation; not embedded app dependency, not inferred from host oracle |
| workflow lint maintenance | actionlint mentioned in Actions report | ad hoc prior validation tool, no committed dedicated guard/CI dependency; not mandatory every product loop |
| vendor wrapper Windows | cmd.exe/JDK | optional workflow outside Linux package qualification; no unique current Linux invariant |

No support component introduces a third-party Python package. Removing Python
would not remove a package ecosystem dependency; it would remove an interpreter
while adding Java CLI launch/compile/dependency packaging work. The larger current
avoidable surface is historical device operator state and optional agent provisioning.

## 19. Contributor cognitive surface

| Task | What must be understood | Languages/tool systems approx | Hotspot |
| --- | --- | --- | --- |
| A. normal Android product change | Java/product tests; AGENTS focused/final ladder; Gradle commands; external verifier results; final human Nix | 1 implementation language + 3–4 build/verification systems; 4–5 forms if editing guards | excessive coupling of source hash/DEX class lists to benign changes; no need to learn all release internals |
| B. dependency update | Gradle Kotlin DSL/locks/checksums, Nix cache/lock/toolchain, Java reviewed pins, Python release source readiness, shell refresh | 5 languages/forms + Maven/SDK | pin inventory in Java test parsed by Python; cache and build versions repeated |
| C. release | two Nix apps, Git reviewed commit/main, Actions protected approval, public signer/provenance/assets | operator 3–4 systems; maintainer up to 6 forms (Nix/Python/YAML/Bash/Gradle/Java pins) | two-command UX is already good; implementation/context/phase parsing is the hotspot |
| D. CI maintenance | YAML/action runtimes/pins, Nix orchestration, Python static tests, sometimes Java flake hash | 4 forms + GitHub/Nix | exact workflow text counts and hashed whole flake make orchestration edits ripple |
| E. physical security/lifecycle change | Java/JUnit/framework, Python runner, SDK/adb/test signing, Gradle artifacts, Nix freeze, human biometric phases | 4–5 forms + real-device systems | superseded signer paths and hand-built manifests; need selective scenarios and final artifact identity |

These are estimates of safe-maintenance knowledge, not a demand that every
contributor edit every language. Operator UX can stay simple while maintainer
complexity shrinks internally.

## 20. Dead/unreferenced tooling and documentation

There are no unexplained generated major tools or unreachable release phase
programs. `source.py` has no executable main because it is a library; discovery-only
release tests have real automatic callers. Missing filename references in a naive
search do not detect stem-built Java filenames or Python imports; these were
traced through source construction and generated manifests.

No current live doc names removed recover.py/recover-release.yml/test_recovery.py,
or the old tag-release/push-release-tag app interface. Historical reports do,
correctly. `check-release` remains documented diagnostic. README retains M0/M1
names and a historical authentication-performance caveat, not broken release
commands. Metadata harness docstring still says final APK proves 2/0.1.1 even
though its synthetic parser fixture is version-independent: obsolete prose inside
an included tool, to update only in a later qualification-tooling milestone.

Only historical human callers, no live named workflow/Gradle/Nix command:
old lifecycle Python pair, inbound builder, ingress builder, SAF qualifiers,
row/security runners and several debug probes. Of these, only the lifecycle
Python pair has a directly demonstrated current replacement with the **same Java
scenario class**. Others have distinct or not-yet-mapped assertions and cannot be
called dead solely from call frequency. M3D's checkpoint prose is live and included
in tools; historical review/ is excluded and should remain historical evidence.

## 21. These can likely be removed now with low risk

**HIGH confidence shortlist: exactly two superseded entry-point files.**

1. `tools/device/qualify-token-lifecycle.py` (53 approximate lines).
2. `tools/device/build-token-lifecycle-tests.py` (38 approximate lines), its sole
   operational script dependency.

Reason: qualify-security.py already compiles TokenLifecycleRegression, packages
final production DEX in a detached package, and exposes `--phase lifecycle`;
F09 used that path. The old pair adds historical signer/password/device timeout
and installed-app replacement procedures, not another lifecycle scenario.
Delete the pair together in a later PR, preserving TokenLifecycleRegression and
the security lifecycle phase. Required evidence: caller search after deletion,
focused harness compile, one applicable final-artifact detached lifecycle run,
normal input-invalidation qualification for tools changes, and docs redirect.
This audit does not authorize deleting anything now. Estimated reduction: 2 Python
files / 91 lines; no language eliminated. No other executable meets this level of
low-risk proof. Windows launcher and diagnostic Java probes are not on this list.

## 22. These responsibilities should probably be merged before language migration

1. Framework test packaging: three SAF builders plus ingress/lifecycle-style
   packaging duplicated in security/row/M3D. Share toolchain discovery, explicit
   input class lists, D8/link/ZIP construction and fixture identity while retaining
   isolation differences and observer-vs-mutator boundaries.
2. SAF outbound/bootstrap host setup/sign/install/backup/restore and Java bounded
   inventories. Consolidate scenarios, not just concatenate programs. Preserve
   inbound read-only/no-write, Join/orphan and wrong-vault distinctions.
3. Release context/readiness/history and phase artifact validation. Replace circular
   imports/global worktree mutation with one explicit context; preserve no-secret
   calls and stage-entry validation. One schema/metadata implementation already
   exists and should remain shared.
4. Reviewed source inventory and action-pin policy. Separate reviewed data from Java
   test syntax; let JVM/release consumers verify it without regex-coupled code.
5. Bootstrap/environment verification and deliberate dependency refresh interface.
   Keep pre-execution wrapper identity and explicit trust review; reduce repeated
   setup/full-task wiring and host-vs-wrapper Gradle confusion.
6. LocalNio diagnostics versus LocalStoreQualification; public TOTP debug fixture
   versus detached row/ingress fixtures. First enumerate unique scenarios, then
   retire superseded debug-only branches and their shape tests if equivalent.

None requires moving business logic into shell or Nix.

## 23. Migration candidates

No file currently merits primary **MIGRATION_CANDIDATE** on HIGH-confidence language
evidence alone. Python is already the primary support language, Java the product/
device language. After merges, consider migrating the compact source/release CLI
and artifact semantics to Java **only if** it demonstrably reduces contributors'
combined launch/build/maintenance burden without adding libraries or secret-scope
execution. The migrated unit would be responsibilities/contracts, not 22 literal
file-for-file ports. Risk is high for binary ZIP/APK, Git/tag races, gh HTTP semantics
and bootstrap-before-build execution. Python stdlib currently supplies these with
no external packages; retaining it is a defensible minimum architecture.

Gradle remains Kotlin DSL for intrinsic graph tasks; Nix remains provisioning/
composition; Actions remains authority orchestration; secret-step Bash remains
narrow. None should be migrated to Java simply to count fewer forms.

## 24. Non-negotiable regression contracts

- Tagged failed/aborted releases consume semantic versions/codes; never recycle
  0.1.0/code1 or 0.1.1/code2. Prepare consumes nothing; next=max consumed+1,
  duplicate/overflow/malformed/stale history fails. Legacy parser only for old tags.
- Preserve two human commands, prepare-release and release-tag, normal review/
  commit/main push between them, caller worktree ownership, exact diff/idempotence,
  printed coordinates and optional deterministic JSON. No forced/multi-ref/main
  push, implicit workflow dispatch or tag adoption/replacement.
- Clean reviewed current main/full SHA; exact local/remote annotated tag object,
  message and peel/source; endpoint checks and pre/post mutation drift detection.
- Permanent production certificate fingerprint:
  `74fd4c313856e1773fd174fba4ecd2a12cc4fde85ae34a3d8a3e986353b01632`.
  Public certificate/DER/digest must bind expected identity; candidate cannot
  choose it. Exactly one signer; v2/v3 enabled, v1/v4 disabled; alignment valid.
- No repository helpers, Gradle, Nix, Python or gh execute in the secret-bearing
  signing step. Restrict PATH/JVM options/key lifetime; no private bytes in source,
  store, logs, provenance, artifacts or publish job. Protected human approval stays.
- checks.android and packages.default are the same derivation. Provenance observes
  actual qualified unsigned APK metadata/hash, does not hardcode versionCode or
  take expected version from signed candidate. Exact schema/type/hash/source rules,
  no duplicate JSON keys; preserve original unsigned/provenance bytes.
- Signed/unsigned ZIP payload order, entry metadata, local headers, compressed and
  uncompressed bytes remain equal apart from signing envelope/offsets. Validly
  signed changed payload fails. Verify signing/alignment with actual pinned SDK.
- Publish only via new draft after fail-closed published/draft absence; correct
  paginated draft lookup; no overwrite/recovery adoption. Upload stable three
  assets, download/recheck hosted bytes/signer/boundaries, final source identity,
  then publish. Errors leave an unpublished draft.
- Wrapper identity before execution; locked/strict dependency and Maven graph
  boundaries; Nix cached input boundary; manifest/permissions/ingress/branding/DEX/
  debug-release exclusions verified against artifacts. Clarify packaged backup
  coverage rather than claiming current verifier checks it directly.
- Qualification ladder: focused iteration, final normal then fresh offline strict,
  final artifacts, relevant physical checks and all included inputs frozen before
  one final human Nix. Included tooling/harness edits invalidate Nix; excluded
  report edits do not. CI still qualifies the package and unchanged checkout.
- Physical framework biometric/Keystore/restart/inactivity/modal ownership evidence
  where applicable, with final artifact identity and disposable data. Provider
  tests preserve immutable VAULT/objects and zero mutation on blocked identity.
  M3D distinguishes observed app/provider facts, human UI and human transport
  reports; capture alone never becomes convergence PASS.

## 25. Proposed minimal future architecture

Retain Nix for pinned tools, dependency response cache, build/check composition and
the two public release app wrappers. Keep optional agent provisioning separate
from the Android CI/release minimum. Keep Gradle for Android builds, metadata
consumption, locked dependency resolution, lint/JVM tests and graph checks; its
APK task should remain a small external-verifier adapter.

Use one cohesive Python support implementation initially: a public release CLI
with explicit context, source/history policy, provenance/artifact helpers and
publication transport, plus an independently callable artifact verifier. Share
neighboring parsers/data; preserve actual build-versus-artifact and signer-versus-
payload responsibilities and phase-entry revalidation. A shared internal device
packaging runner should serve Java subsystem instrumentation without making
physical tests part of every ordinary build.

Actions should orchestrate qualification/approval/sign/verify/publish and transport
bundles, with the smallest trusted shell interval for secrets. Device regression
suites should be a short subsystem map: row/layout, ingress, lifecycle/biometric,
SAF/bootstrap, optional real transport. Historical debug experiments can be kept
or retired according to unique scenarios, outside the routine minimum. Keep
historical reports. A future Java support CLI remains a measured option after
this architecture is smaller, not the first wave.

## 26. Proposed pruning waves

| Wave | Files/components | Invariant preserved | Regression evidence required later | Risk | Expected reduction |
| --- | --- | --- | --- | --- | --- |
| 1 — retire proven superseded launcher | qualify-token-lifecycle.py + build-token-lifecycle-tests.py; redirect historical procedure via live docs without rewriting past evidence | same lifecycle Java through detached security phase | caller search, focused compile, detached lifecycle physical evidence and applicable tools/Nix final gates | LOW | 2 files / ~91 Python lines |
| 2a — unify device orchestration | three SAF build scripts, two SAF qualifiers, ingress packaging and repeated row/security/E2E packaging | exact test inputs, disposable identity, restore, real provider semantics, no real vault writes | builder input/output assertions; negative identity/isolation; focused relevant physical scenarios; frozen final gates | MEDIUM | estimated net 150–300 lines, 3–5 files after adding shared replacement |
| 2b — consolidate release internals | release.py/source.py/shared identity/pin policy; signed verifier adapters; metadata harness | all source/provenance/signature/publish contracts and same public commands | temporary Git/CLI tests, HTTP/draft cases, SDK signing smoke negatives, exact artifact metadata/payload, protected workflow static/semantic checks, final qualification | MEDIUM/HIGH | ~50–150 lines; 0–2 net files; no language reduction |
| 3 — trim old debug experiments after scenario mapping | LocalNioProbe/Qualification vs LocalStoreQualification; TOTP debug fixture vs detached suite; AuthPerf/StorageProbe UNKNOWN decisions deferred | unique filesystem/ART/provider assertions retained or explicitly retired as requirements | per-scenario coverage map, debug/release exclusion/source-boundary updates, relevant physical evidence | MEDIUM/HIGH; UNKNOWN probes not preapproved | perhaps 200–500 lines; not counted as committed savings |
| 4 — simplify qualification/docs | bootstrap/toolchain wrapper, live AGENTS/README/release docs, shape-only tests and optional agent shell wiring | final evidence strength, exact freeze/invalidation, two-command release, dependency review | actual input dependency review, docs link/command checks, applicable build/package gates; no routine tests just for prose | LOW for excluded prose; MEDIUM for executable changes | ~20–70 executable lines plus prose; 0–1 net script |
| 5 — optional language migration | consolidated release/artifact logic only | entire section 24 acceptance set | differential old/new synthetic + actual SDK artifacts, offline bootstrap/CLI launch, source/publish negatives; deliberate qualification | HIGH | Python removal not guaranteed; net LOC may increase; decide from measured maintenance burden |

Order device sub-waves independently of release sub-waves; avoid one large change
that simultaneously alters signing, graph checks and physical evidence collection.
Every executable wave is later implementation work with its own classification
and actual dependency-based final gates. Tools-only edits can invalidate Nix even
when APK rebuilds are not required; use AGENTS invalidation matrix, not blanket
full rebuilds for every script/prose edit.

## 27. Risk ordering

Lowest: excluded live documentation clarification and superseded lifecycle Python
pair with direct replacement evidence. Next: shared device compile/link boilerplate
and removal of exact implementation-shape assertions after equivalent behavioral
coverage. Then: SAF fixture restore/sign/install consolidation and shared reviewed
input data. Higher: release Git races/history/provenance/publication internals.
Highest: secret boundary changes, signed-payload parser rewrites, removal of
independent artifact checks or broad language migration. UNKNOWN diagnostics need
a decision about requirements before deletion, not an optimistic LOC target.

## 28. Estimated file/LOC/language reduction

Starting measured support surface is ~7433 code/config lines in 63 files, plus
trusted metadata and historical/live prose. Immediate evidence-backed deletion
potential is ~91 lines/two Python files (~1.2% of measured support code). Merges
and setup simplification could conservatively save another ~220–520 lines net,
including replacement code, for ~310–610 total lines (~4–8%) and ~5–10 net files.
These are planning ranges, not measurements of an implemented result; overlaps
between waves must not be counted twice. Deferred debug decisions could save more
but do not belong in the confident estimate.

Early waves remove **zero required languages**. The meaningful gain is fewer
interfaces/stateful parsers/operator prerequisites and clearer authority boundaries.
Removing Python after a Java rewrite might reduce one interpreter, but only if
all remaining Python verifiers/device orchestration disappear safely; that is
currently unproven and is not included in estimates. Gradle Kotlin/Nix/YAML/shell
serve distinct roles and will remain even if release business logic moves.

## 29. Open questions / unknowns

- Are Windows builds a supported contributor promise? No current Linux CI path
  invokes gradlew.bat; retaining the vendor pair is cheap pending that decision.
- Do maintainers still need broad manual StorageProbe and ART KDF matrix diagnostics,
  or only narrow durable regressions? Known callers are human/debug manifest;
  lack of routine runs is not enough to decide removal.
- Which inbound/bootstrap/local-filesystem cases are uniquely physical, and which
  can safely be covered by existing JVM/product tests plus combined provider suite?
  A scenario-level map is required before deleting those Java assertions.
- How should future desired packaged backup assertions be made explicit? Current
  verifier does not directly establish the broad backup claim in earlier reports.
- Which repeated same-artifact bundle/APK checks can be omitted within a single
  phase without losing any transition validation? Decide using an input-lifetime
  map, not file names or call count alone.
- Human-managed GitHub environment reviewer/secret configuration and historical
  test-key files cannot be established from source. This audit does not claim they
  were freshly inspected or that old device commands are portable today.
- Any untracked external/operator callers could change deletion risk. Repository
  evidence supports the shortlist; later removal should state the interface sunset.
- Actionlint is prior maintenance evidence, not a committed recurring check. Whether
  to standardize it is a future requirement choice, not a new tool in this audit.

These do not prevent reconstructing current release phases or determining current
repository callers. No stop condition required an executable edit or speculation
around missing major generated-source provenance.

## 30. Qualification disposition

Selected checks: documentation paths/fences/consistency, changed-path allowlist,
Git checkout byte/mode invariance against starting HEAD, unstaged status and whitespace.
No production, JVM test, build/package configuration or harness/tool bytes changed.
All four categories remain stable relative to starting HEAD; no new qualification
of those unchanged bytes is claimed. Source-filter/evaluator/app inspection in
section 9 proves report-only edits do not invalidate effective Gradle/Nix inputs.

| Execution count | This milestone |
| --- | ---: |
| Baseline full Gradle | 0 |
| Focused Gradle/test/compile/boundary | 0 |
| Final normal full Gradle | 0 |
| Strict-clean Gradle | 0 |
| External artifact verifiers / embedded Gradle APK verifier | 0 / 0 |
| Python tooling suites / signing smoke | 0 / 0 |
| Physical harness phases / device installs | 0 / 0 |
| Human Nix / agent Nix | 0 / 0 |
| Sign/publish/tag/push/dispatch/stage/commit | 0 |

Existing reports supply historical context only. This audit contains no fresh
build, test, APK, signer, hosted-byte or device PASS claim. No gate was invalidated
or repeated, and no human Nix was requested. Whitespace, local report links/fences,
changed-path allowlist and tracked checkout-byte/mode/index invariance checks passed.
An initial raw Git-blob comparison flagged gradlew.bat: its committed blob has LF,
while `.gitattributes` explicitly checks it out with CRLF (112 CRLF lines).
Comparison against `git cat-file --filters HEAD:gradlew.bat` confirmed exact
expected checkout bytes. Final byte checks use checkout-filtered HEAD for all
tracked files; no wrapper change occurred. This was a validation-method correction,
not a build/test failure or gate invalidation.

## 31. Final Git state

Branch `main`; HEAD remains `33eb9a79b3826d9c34c7679003823570369691d6`.
VERSION remains 0.1.2; VERSION_CODE remains 3. Index has no changes; every previously
tracked path, including executable files, has starting-HEAD checkout bytes/mode. The only
new repository file is this excluded report, left untracked/unstaged/uncommitted:

```text
?? review/ANDROID_SUPPORT_TOOLING_AUDIT.md
```

No pruning wave was performed. The report is the reviewable evidence for a later
simplification milestone.
