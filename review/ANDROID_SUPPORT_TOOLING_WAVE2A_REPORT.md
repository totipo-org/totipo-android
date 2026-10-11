# Android support-tooling consolidation Wave 2A

Status: **implementation available for review; qualification incomplete; stopped at the
unchanged SAF Java regression boundary.** Host tests and differential construction
checks pass. Detached physical lifecycle passes. Outbound physical qualification
fails; bootstrap, ingress and M3D physical checks remain unrun. No human Nix request
has been made. Nothing is staged or committed.

## 1. Starting state

Before edits:

```text
git branch --show-current  main
git rev-parse HEAD         d21c15dd4fbfca799ad2b354690e86b118a4aaa0
git status --short         empty
cat VERSION               0.1.2
cat VERSION_CODE          3
```

The audit is committed at `0981105`; Wave 1 pruning is committed at starting HEAD.
Current 0.1.2 release tooling, release maintenance `33eb9a7`, and all current Java
physical regressions are committed. Read AGENTS.md, README.md, the
[support audit](ANDROID_SUPPORT_TOOLING_AUDIT.md),
[Wave 1 report](ANDROID_SUPPORT_TOOLING_WAVE1_REPORT.md), and
[M3D procedure](../tools/device/M3D_SYNCTHING_CHECKPOINTS.md). Inspected package.nix,
flake.nix, CI, the affected Python scripts and their qualification evidence.
No other AGENTS.md was found under this checkout.

## 2. Classification

**TEST_OR_QUALIFICATION_TOOLING.** No production behavior or Java scenario changes.
The AGENTS device-harness invalidation row applies: focused host/device checks,
then final human Nix because tools/** is selected. No Gradle-consumed input changed,
so ordinary baseline/final/strict Gradle builds are not required for this scope.
The failed required physical gate prevents claiming completion or requesting Nix.

## 3. Duplication inventory and original construction contract

All builders use JDK 17, Android platform `android-37.0/android.jar`, SDK Build Tools
36.0.0, Java source/target 17 for instrumentation, and explicit framework manifests.
The SAF host fixture compile keeps its original default Java compiler options.
None introduces a pip dependency.

| Entry point | Output under .gradle | Explicit instrumentation sources/classes | Package and instrumentation target |
| --- | --- | --- | --- |
| build-otpauth-ingress-tests.py | m2d-ingress/tests-unsigned.apk | OtpAuthIngressRegression, OtpAuthUiObserver | org.totipo.ingresstest; regression → org.totipo.android, observer → its own package |
| build-saf-inbound-tests.py | m3a-saf/tests-unsigned.apk | SafInboundRegression | org.totipo.safqualification → org.totipo.android |
| build-saf-outbound-tests.py | r19-saf/tests-unsigned.apk | SafOutboundRegression, LocalStoreQualification | org.totipo.safqualification → org.totipo.android |
| build-saf-bootstrap-tests.py | m3c-saf/tests-unsigned.apk | SafBootstrapRegression, LocalStoreQualification | org.totipo.safqualification → org.totipo.android |
| check-row-reveal.py | row-reveal-device/unsigned.apk, tests.apk | RowRevealRegression and its nested classes | org.totipo.rowrevealqualification → itself |
| qualify-security.py | security-qualification/unsigned.apk, tests.apk | TokenLifecycleRegression, SecurityRegression and their nested classes | org.totipo.securityqualification → itself; both instrumentations registered |
| qualify-syncthing-e2e.py build | m3d-syncthing/observer-unsigned.apk | SyncthingE2eRegression and its nested class | org.totipo.syncthingqualification → org.totipo.android |

Source paths above are tools/device/<Class>.java. SAF builders separately compile
InboundFixture for host execution with cached core/NIO 0.2.0 and BC 1.86; its classes
never enter harness DEX. Outbound/bootstrap instrumentation also has these jars on
the compile classpath. Ingress/inbound/M3D compile against android.jar alone.
Row additionally compiles against debug app classes and core. Security compiles
against app classes and all three jars (debug for --build-only, release for packaging).
D8 uses min API 26 except security's 30, with original library/classpath arguments.

Manifest text remains in each caller, byte-identical to before consolidation.
Ingress retains two registrations. SAF retains its one scenario-specific registration
and assets/vault. Row copies product debug DEX only; security copies final release
DEX/resources and retains USE_BIOMETRIC, Application/Activity/theme, testOnly,
allowBackup=false and non-debuggable boundaries. Manifest target SDK remains 37.
M3D has only the observational instrumentation registration.

The four standalone builders remain unsigned. Row's local key is test-only.p12,
alias row-tests, CN=Detached Row Tests; security's is test-only.p12, alias
security-tests, CN=Disposable Security Tests. Both retain the fixed public test
password and original default SDK signing schemes. Row alignment remains 4;
security alignment remains 4 with -P 16. SAF/M3D historical qualification signing
paths, explicit scheme options, and installed-certificate checks remain caller-owned.
No protected production signer enters the module.

Repeated mechanics were SDK/JDK paths, compiler/D8/link commands, appending DEX and
assets, copying selected production entries, detached test-key setup/signing, and
SAF certificate extraction plus fixture backup/restore. Fixture authorship, dependency
coordinates, manifests, scenario commands, package identities, production installation
policy and M3D human transport/evidence semantics remain in callers.

## 4. Preserved isolation boundaries

Ingress still targets the real production Activity and has an independent observer.
Row remains detached and disposable, with debug DEX and no product data access.
Security remains detached, using final unsigned release DEX/resources and its own
key/package; lifecycle and biometric phases remain separate. SAF continues to test
the real production package/provider grant while using isolated local roots.
M3D build alone adopts mechanical helpers; sign/install/serve/checkpoint/oracle code
and its human transport model are unchanged. No convergence claim is made.
All Java regressions and fixtures are byte-identical to starting HEAD.

## 5. Shared layer

[android_harness.py](../tools/device/android_harness.py) is one internal standard-library
module with explicit arguments and no repository-root global or dependency/package
policy. AndroidToolchain supplies pinned SDK/JDK paths and checks tools on use.
compile_java builds in a disposable temporary directory, includes only caller-named
top-level class families and their nested classes, removes stale members of those
families, and preserves independently compiled host fixtures. Sources are explicit;
there is no recursive source discovery or arbitrary output-directory DEX inclusion.

Other helpers run D8, link caller manifests, append explicit file/name pairs, copy
explicit APK entries, reject duplicate names before modification, and test-sign
only detached packages. Test signing additionally checks alignment and verifies the
single APK certificate against the caller's test-key alias certificate. Commands
capture output and redact failure details, preserving ingress fixture confidentiality.
Tool lookup, missing inputs and subprocess errors fail closed.

SAF adopts certificate_digest/verify_signer and provider_backup. The latter accepts
the exact caller-designated remote folder and child names, retains the granted root,
backs up before mutation, restores in finally, and checks complete file hashes.
Outbound now gets the same restoration-hash evidence already present in bootstrap.
Scenario execution remains explicit, including matching/blocked outbound and
join/initialize/orphan/different bootstrap phases. Callers force-stop before restoration
and remove instrumentation/relaunch afterward even on scenario failure; a success
message is emitted only after successful completion.

## 6. Affected callers

The seven packaging entry points in section 3 plus qualify-saf-outbound.py and
qualify-saf-bootstrap.py: nine existing Python files. Added the shared module and
[test_android_harness.py](../tools/device/test_android_harness.py).
No other executable file changed.

## 7. Removed duplication

Removed individual SDK/JDK path construction from seven packaging paths; repeated
javac/D8/aapt2 assembly and ZIP-writing blocks; detached row/security key/sign blocks;
and SAF certificate parsing and provider-restoration blocks. No file was deleted.
SAF fixture validation and cached dependency selection remain caller-owned, as do
historical product signing/install procedures. M3D signing/certificate code stays
local to its distinct evidence workflow. These retained blocks are deliberate scope
boundaries, not alternate implementations accidentally used for shared packaging.

## 8. Retained human entry points

All existing filenames, output locations and interfaces remain. The four builders
still take no supported arguments. Row retains ROW_REVEAL_APP_APK,
SYNC_LAYOUT_BASELINE and SYNC_LAYOUT_ONLY. Security retains --serial (default
37311JEGR05916), --build-only, --prepare, --update-harness, --phase and --remove.
SAF qualifiers remain explicit no-argument procedures. M3D retains build/sign/install/
serve/capture/begin/finish/restart and all existing flags. No historical procedure or
live checkpoint document was rewritten.

## 9. Before/after construction comparison

Temporary evidence is under ignored .gradle/wave2a/{before,after}; commands.json
captures actual compiler/D8/link arguments, and copied manifests/APKs retain old/new
construction outputs. /tmp/wave2a-baseline.py captured the unmodified behavior before
edits. For row/security packaging only, subprocess interception simulated adb calls;
compilation, linking, ZIP construction, alignment and signing used real pinned tools.
Those simulated runs are **not physical qualification**. Other builders ran normally.

All seven comparisons PASS: identical manifest source, manifest binary, ZIP entry
names/order, each uncompressed payload entry (including DEX), and sorted explicit D8
class inputs. Entry counts: ingress 3, inbound/outbound/bootstrap 4 each, row 10,
security 19, observer 3. Detailed class lists are in comparison.json. No stale or
unrelated class was present in the baseline; fresh-directory selection preserves
its intended inclusion while preventing future stale-family leakage.

Row/security old/new APK certificates match. Raw signed APK bytes can differ because
ZIP timestamps change. An initial raw-byte assertion against a later regenerated
security APK failed; payload-by-payload and certificate comparisons then passed.
The actually installed/tested APK was pulled and retained as security-installed.apk
and tests.apk, with all 17 selected production entries equal to the unchanged release.
This is a metadata comparison correction, not an Android scenario failure.
The final comparison was repeated after adding test-key certificate verification;
no production/test/build inputs changed at any point.

## 10. Focused host tests and caller checks

Commands:

```sh
python3 -B -m unittest discover -s tools/device -p 'test_android_harness.py'
python3 -B tools/device/test-syncthing-e2e.py
```

Final results: **8 mechanical contract tests PASS**, **10 unchanged M3D oracle tests
PASS**. New behavioral cases cover missing SDK/JDK environment/tool/platform; missing
explicit source/class; missing compiler output; stale/nested class selection; missing
ZIP file and duplicate destination names; redacted failed d8/aapt2/apksigner/zipalign;
missing/multiple/unexpected signer; and fixture restoration after a scenario exception.
They do not reimplement framework scenarios. Real builder paths exercise signing,
manifest linking, compiler and D8 against pinned tools and checksum-verified Wave 1 cache.

All seven old/new packaging paths passed. Security --help passed. Repository-wide
rg caller searches found current callers selecting shared packaging; only intentional
SAF/M3D signing orchestration remains separate. Syntax compilation and git diff --check
passed. Generated tools/device/__pycache__ was removed before the final source review;
validation uses -B/PYTHONDONTWRITEBYTECODE and no temporary evidence is committed.

## 11. Physical evidence and stop condition

Device: serial **37311JEGR05916**, Pixel 6a, Android 17/API 37, fingerprint
`google/bluejay/bluejay:17/CP3A.261005.002.A1/2026100601:user/release-keys`.

**Detached lifecycle PASS**, one --prepare --phase lifecycle run, 51 checks, log
.gradle/wave2a/security-physical.log. Source release SHA-256:
`b70501790d32ff383665c8f7c1a599d750036b91a3b1fb0b804f706b8341e012`.
Pulled/tested detached APK SHA-256:
`c1d62ac43a5a4e798d11165a0ca3b9ed9b800a3bc908d81fd134fb8f952cd228`.
Detached certificate SHA-256:
`e708877ad3d07118c31c06c0972f8ff97f8ab376e8d7bde7fb4dc1e3596cff26`.
All 17 copied production DEX/resource entries match that release. The later signing
verification helper also passed against identical payload/certificate construction.
--remove passed and pm path confirmed the disposable security package absent.
No biometric first/restart phase or full row matrix ran.

The historical local qualification key was absent after release preparation. The
human confirmed it could not be restored and manually uninstalled the disposable
production app. Created a fresh **local qualification-only** key at the existing
ignored path, signed a copy of the unchanged release, and installed it only after
confirming the human uninstall. No protected production key was used, shared or read.
The human selected a folder and joined the disposable vault through normal UI.

**Outbound attempt 1 FAIL:** permission inspection passed, then
`designated_disposable_provider_tree` failed after three checks. The human corrected
the selected folder to exact Documents/Totipo-M3A-Test. Log:
.gradle/wave2a/saf-outbound-physical.log. Shared cleanup restored the designated
fixture byte-for-byte and removed the instrumentation package.

**Outbound attempt 2 FAIL:** inspection passed; matching scenario reached correct
folder/baseline, startup READY, local-store regression and locked-import checks, then
`isolated_replica_initially_empty` failed after nine checks. The device locked during
this run; the human unlocked it, and the same run continued without restart.
Log: .gradle/wave2a/saf-outbound-physical-final.log. Temporary USB/AC stay-awake was set
from 0 to 3 and restored to 0 before stopping. Fixture restoration hashes passed;
the granted root remained in place, instrumentation was removed, production relaunched.

Narrow source diagnosis: unchanged SafOutboundRegression.java checks tokens()==0
immediately after isolated unlock (line 191). Current unchanged controller opened()
requests automatic Sync (AndroidVaultController.java line 457). Thus the historical
explicit-import scenario can observe already-imported tokens. This is evidence of a
scenario/precondition mismatch, not proof of a new packaging defect; differential
DEX/payload comparisons passed. No Java assertion was weakened, scenario reduced,
auto-Sync disabled or product edited. Making this required physical gate pass would
require resolving that scenario/product interaction outside the authorized mechanical
scope. The user explicitly requires stopping at Java scenario/product changes, so
work stopped and no further physical suite or Nix gate was attempted.

Bootstrap, ingress and M3D physical checks are **NOT RUN due to stop**. Outbound
matching/blocked cases are **NOT QUALIFIED**; the latter never executed. Detached
lifecycle passes do not substitute for them. No Syncthing convergence, human transport
observation or completed Wave 2A qualification is claimed.

Installed final qualification product APK equals .gradle/r19-saf/release-signed.apk:
SHA-256 `0f262717e1a8ffa3e7d6925e15f50940366035f6af73826a44ddd605c285be30`;
certificate `d862bbf279d306e7062440d299440d54ecdf594e1cf8ac3046cdf5d3a0ed7f49`.
Its entire ZIP payload matches the unchanged unsigned release. It remains installed
with the human-selected disposable binding/local vault; fixtures/evidence are retained.
The installed app is now a locally signed qualification copy, not the protected
production-signed distribution. This followed the human's manual uninstall.

## 12. Product/release non-change proof

Git diff against HEAD is empty for app/, tools/release/, release/, .github/, VERSION,
VERSION_CODE, gradle/, Gradle build/settings/properties/locks, package.nix, flake.nix,
flake.lock and package-deps.json. All tools/device/*.java remain unchanged. VERSION
is 0.1.2; VERSION_CODE is 3. Existing debug APK SHA-256:
`cefd6f5afe73fbaae8f765dabdb2ea1a3ab6726b8c9bcbfe31cd6bf2e2ec9dd4`;
unsigned release hash is recorded above. Neither product APK was rebuilt or signed
in place. Only ignored copies were signed for existing physical qualification procedures.
No stage, commit, push, tag, workflow dispatch, publication or agent Nix occurred.

## 13. LOC/file-count delta

Nine existing callers: **890 → 800 physical lines (-90)**. Shared module: **+195**;
focused host tests: **+125**. Device Python total for this change: **+230 lines**, **+2
files**, no deletions. Executable implementation alone grows by 105 lines; tests add
125. This consolidates repeated mechanics and adds explicit-input/stale-class/ZIP/
signer/restoration checks, rather than achieving the audit's speculative net-LOC
reduction. Human entry points and scenario-owned fixture/dependency/signing rules
remain readable. This report adds one excluded Markdown file, counted separately.

## 14. Qualification accounting and invalidations

| Category | Count/result |
| --- | --- |
| Baseline/final normal/strict-clean Gradle | 0 / 0 / 0; no Gradle-consumed input changed |
| Focused Gradle compile/tests/boundary | 0 |
| New mechanical host suite | 4 invocations, 8 tests PASS each |
| Existing M3D oracle suite | 3 invocations, 10 tests PASS each |
| Old construction capture | 7 builder paths PASS; row/security adb simulated |
| Consolidated construction capture | 2 × 7 paths PASS; second after added signer checks |
| Additional physical builders | security prepare ×1, outbound builder ×2 |
| Total builder/build-path invocations | 24: ingress 3, inbound 3, outbound 5, bootstrap 3, row 3, security 4, M3D 3 |
| Physical instrumentation phases | 5: lifecycle PASS; outbound inspect ×2 PASS; outbound matching ×2 FAIL |
| Physical installs | 6: detached security 1; fresh qualification product 1; outbound product updates 2 and test installs 2 |
| Physical cleanup | security remove 1; SAF cleanup 2; both test packages absent; stay-awake restored |
| External artifact-verifier final set | 0; product build inputs unchanged |
| Existing signed product verifier inside SAF | 2 PASS before installs; no embedded Gradle verifier |
| Human Nix / agent Nix | 0 / 0; not requested because required physical gate fails |
| Stage/commit/push/tag/dispatch/publish | 0 |

No full Gradle gate was repeated or invalidated. An extra construction pass was
necessary after adding detached signer verification; final host/semantic checks
were rerun after stabilization. The second outbound attempt followed human correction
of its folder precondition, not a source change. No physical retry followed the
second failure because Java scenario changes are excluded.

package.nix selects tools/**, including this module and host tests; it excludes
.git/.gradle/build/.direnv components and symlinks. The tools are not consumed by
Gradle. package.nix/flake.nix/flake.lock/package-deps.json evaluator/cache inputs are
unchanged. review/, README and AGENTS are not consumed by the package/check or
release-app checks. Report-only updates therefore do not invalidate later Nix under
these inspected dependencies; executable tools edits do.

## 15. Input freeze and human Nix status

- Production source stable: no app/ or version changes, unchanged APK identities.
- JVM tests stable: no app/src/test or testDebug changes.
- Build/package configuration stable: no evaluator, cache, dependency or build edits.
- Qualification harness/tooling implementation stable for review: final host tests
  and seven-path construction comparison PASS, Java unchanged. **Required physical
  qualification remains incomplete**, so final qualification freeze/readiness is
  not declared.

Human Nix: **NOT REQUESTED / NOT RUN**. Request exactly `nix flake check path:.`
only after the failed physical prerequisite is resolved under an authorized scope
and all included inputs are refrozen. Agent has not run Nix. No Nix PASS exists to
invalidate; this report must not be read as a completed milestone.

## 16. Final Git state

Branch main; HEAD remains `d21c15dd4fbfca799ad2b354690e86b118a4aaa0`.
Index empty. Nine existing tools/device Python callers modified; android_harness.py,
test_android_harness.py and this report are untracked. All changes are unstaged and
uncommitted. git diff --check passes. No Java/product/release/build file changes.
Temporary APKs, keys, cache, logs and comparison scripts are outside tracked source.

## 17. Remaining work / Wave 2B candidates

First resolve the SAF scenario's explicit-import assumptions in a separately
authorized milestone; do not disguise that scenario change as this mechanical
consolidation. Then complete outbound/bootstrap, ingress and the relevant M3D packaging
smoke/evidence checks, refreeze and obtain human Nix before declaring Wave 2A complete.

Wave 2B remains separate: release.py/source.py authority/context coupling, release
signing smoke and source-shape tests, trusted signing-tool resolution and obsolete
historical qualification signer provisioning. Preserve protected signing custody,
installed-certificate boundaries, exact source/version policy, payload verification,
and M3D human transport evidence. No Wave 2B code was changed here.
