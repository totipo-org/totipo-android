# Android Phase C: final APK verification simplification

2026-10-14. Classification: **TEST_OR_QUALIFICATION_TOOLING / PACKAGING_RELEASE**.
Implementation remains uncommitted. All required host qualification passes; human Nix
qualification is **PASS, human-reported**, with zero agent Nix executions. No live release.

## 1. Starting source and environment

Clean committed `main`, HEAD `d8bfe34028007abd06e8fe0e3b393f96a92843c5`;
initial `git status --short` empty. Phase A is committed at `e206b48` with package
fix `055a771`; Phase B is committed at starting HEAD. VERSION `0.1.2`, VERSION_CODE
`3`, application ID `org.totipo.android`, all unchanged.
Read AGENTS completely, all three requested reports, current build/package/flake,
CI/release workflows, release/signing documentation, verifier/payload/controller,
release tests and relevant application/device qualification code. No sibling
repository, other worktree or sub-agent was used.

Active Nix-managed identities: Gradle 9.8.0; AGP 9.4.0; JDK 17.0.20.1+1-nixos;
platform android-37.0 revision 2, API37/extension22 (platform name Android17);
Build Tools36.0.0; platform-tools37.0.1; command-line tools22.0. JAVA_HOME is
`/nix/store/zanji5hpfg98zl65a1j8lxy1na04rhmx-openjdk-headless-17.0.20.1+1/lib/openjdk`;
both SDK variables point to
`/nix/store/x9gvmw0icgnpqz4wbvfr8dnnnlyan8sk-androidsdk/libexec/android-sdk`.
Gradle executable is
`/nix/store/rm902prah3x4ziil8hpyyffzappk5wjz-gradle-9.8.0/bin/gradle`.
AAPT2 reports 2.20-13193326; apksigner reports 0.9; zipalign supports native
`-c -P 16 4`. No toolchain, acquisition, lock or dependency upgrade.

## 2. Actual verifier callers before change

| Caller | Before | Decision |
| --- | --- | --- |
| Gradle app check | verifyReleaseApkBoundary Exec assembles and fully verifies release | REMOVE duplicate task; assembly remains explicit |
| Nix installCheck | Full debug policy and full installed unsigned release policy | KEEP authoritative package boundary |
| Host AGENTS final commands | Full debug and unsigned release after strict-clean | KEEP; different host qualification claim |
| release.prepare / qualified_provenance | Full unsigned check after authenticated package qualification, then metadata | SIMPLIFY to reviewed metadata and hash binding |
| release.bundle_check | Full unsigned policy at each sign pre/post, finalize and publish handoff | SIMPLIFY to authenticated source, SHA and metadata binding |
| signed verifier | bind unsigned, full unsigned policy, native signature, metadata, alignment, full signed policy | KEEP independent unsigned policy; preserve signed platform policy by complete payload equality |
| release.finalize / publish | Signed verifier then separate payload comparison | SIMPLIFY: comparison now inside signed verifier |
| release.publish hosted download | Whole-file equality then another signed verifier | REMOVE repeat inspection after identical whole-file hashes |
| signing-smoke | Provenance/full unsigned; signed verifier; separate positive payload comparison; signing/payload negatives | KEEP meaningful tests; remove repeated positive payload comparison |
| SAF bootstrap/outbound and Syncthing qualification sign helpers | Full signed release policy before device install | KEEP existing explicit --no-debug-probe callers, byte-identical harnesses |

CI calls package-inclusive flake check. Release retains qualify → sign → verify →
publish, with three same-run artifact transfers and the protected signing environment.
Other device signing/security tools retain their own native signature and device
assertions; they are not duplicate full Totipo APK-policy invocations.

## 3. Baseline evidence and effective manifests

One pre-edit full Gradle checkpoint PASS: 1m34s; 90 actionable, 13 executed,
77 up-to-date. All 336 JVM tests PASS, zero failures/errors/skips. Baseline release
suite: 55 tests PASS, 16.330s, no skips. Existing verifier accepted both available
APKs; the baseline Gradle task also accepted its rebuilt release output.

SDK-decoded baseline manifests have package org.totipo.android, versionName0.1.2,
versionCode3, min26/target37, compile37/platform version17. Both have only
USE_BIOMETRIC; no service, receiver, provider, alias, instrumentation or queries.
Both application manifests have TotipoApplication, allowBackup=false,
extractNativeLibs=false, canonical icon/roundIcon and application label.
Release has exactly MainActivity and OtpAuthEnrollmentActivity; debug additionally
has DebugTotpFixtureActivity, debug.StorageProbeActivity, debug.LocalNioProbeActivity,
and debug.AuthPerfActivity. All are exported and effectively enabled, using the
normal package process. Only MainActivity and the debug storage probe have launcher
filters. Enrollment has exactly the reviewed VIEW and SEND filters below.
Release debuggable and testOnly are absent, using false defaults. Debug has
debuggable=true, testOnly absent, fullBackupContent=false and the existing
storage_probe_extraction_rules reference. Native decoding of that resource confirms
sharedpref/path-dot exclusions in both cloud-backup and device-transfer.

No baseline permission/component/version discrepancy was found. Pre-build APKs
were cached outputs from the preceding milestone; the actual completed baseline
copies/hashes are recorded in section 19, not those earlier cached bytes.

## 4. Final policy and check classification

KEEP final identity/SDK, ZIP integrity/duplicate rejection, actual permissions,
platform exposure, unsigned envelope absence, essential launcher resources and small
debug/test exclusions. KEEP native signing/certificate/schemes/alignment, exact
unsigned/signed payload, unsigned hash, signed checksum and hosted equality.
SIMPLIFY manifest inspection to one SDK-decoded XML representation with an explicit
approved tree; source branding to retained maintenance provenance; duplicate calls
to the authoritative boundary graph below.
REMOVE ordinary production-class presence lists, retired Java API spellings,
source artwork hashing on every artifact, long individual test/fake/history marker
inventories and repeated identical full-policy executions. Compilation/D8 are AGP's
responsibility; tests establish application behavior. The new policy checks what
Android can instantiate and expose, rather than how Java classes are organized.

| Existing check family | Evidence / guarantee | Disposition |
| --- | --- | --- |
| ZIP integrity, required manifest/resources/DEX, duplicate entry refusal | Actual final APK structural integrity; AGP construction alone does not inspect transferred bytes | KEEP |
| Five forbidden permission strings, regex exact permission set, service absence | Actual final manifest platform policy; old regex/tree indentation logic incomplete | SIMPLIFY to complete exact native-XML policy |
| Enrollment filter counts and forbidden ingress strings | Actual Android entry-point policy, not compiler correctness | SIMPLIFY to exact component/filter tree |
| Icon resource IDs/configurations/adaptive layers | Actual essential branding resources and references | KEEP native resource inspection |
| Branding provenance commit, copied source paths and repeated source hashes | Source-level maintenance evidence, not final pixel comparison | REMOVE from artifact path; retain source provenance |
| Ordinary production/enrollment/NIO/core/BC class lists and retired API markers | Final DEX implementation convention, largely compilation/locked dependency/test overlap | REMOVE |
| Individual old fake/test/historical descriptors, diagnostic strings and fixture hashes | Mixed obsolete implementation inventories and debug/test risk | SIMPLIFY to small package/test/debug exclusions; no exhaustive history-marker guarantee |
| Forbidden external QR/cloud/background SDK namespaces | Final dependency exclusion beyond the existing partial Maven graph policy | KEEP smallest existing negative list |
| Required debug helper classes | Internal helper layout convention | REMOVE; debug manifest probes and actual tests remain |
| Unsigned JAR/APK envelope absence | Actual pre-signing input status, not apksigner failure interpreted as unsigned | KEEP small envelope check |
| Native signer validity, sole pin, schemes, alignment | Actual signed candidate security and installability; AGP alone cannot authenticate it | KEEP |
| Complete accepted payload comparison | Actual qualified construction retained through signing, no equivalent SDK size comparison | KEEP |
| Unsigned/signed/hosted hashes and provenance | Actual identity across source, credential and transfer boundaries | KEEP; remove inspections only after same-byte proof |

## 5. Reviewed VERSION and VERSION_CODE binding

verify-apk reads reviewed VERSION/CODE from its source root, reusing source.py's
canonical stable version and positive LF-terminated code parsers/range. It requires
an exact final LF for VERSION too. Native final APK fields must equal those expected
values. Neither APK nor provenance can supply the expected code.
Release metadata uses the same decoded manifest and reviewed-source policy; it no
longer has a separate AAPT2 badging/version parser. Provenance version_name must
also equal version. Authenticated source/tag identity still precedes preparation
and each bundle check. bind_unsigned compares native APK metadata to provenance
and source, alongside the qualified unsigned SHA. Signed metadata is inspected
directly and checked against the same source and provenance. Wrong name/code and
consistent-but-source-disagreeing metadata are rejected by focused tests.

## 6. SDK tools and fail-closed parsing

Pinned apkanalyzer at cmdline-tools/22.0/bin supplies `manifest print` XML and
`resources xml --file` XML. AAPT2 `dump resources` supplies essential resource
names, IDs, configurations and packaged paths. Native apksigner remains the
signature authority; zipalign remains the alignment authority. Python uses only
stdlib ElementTree, ZIP, hashes and small policy comparisons. No binary XML,
cryptographic-signature decoder, dependency or generalized inspection framework.

Full tree comparison ignores declaration order, retains duplicates, and rejects
unknown elements/attributes, extra components/filters/permissions, nonwhitespace
XML text, DTD/unsupported declarations, malformed/incomplete output and missing
metadata. Native process failures and absent tools/files fail acceptance; they are
never interpreted as absent properties. SDK resource output must have the expected
package header, unique records and usable required configurations/paths. Pinned
SDK representation changes require review rather than silent fallback.
See [SDK apkanalyzer](https://developer.android.com/tools/apkanalyzer).

## 7. Components, export and intent policy

Release inventory is exact, including nonexported declarations: only activities
org.totipo.android.MainActivity and org.totipo.android.OtpAuthEnrollmentActivity.
Both exported=true and enabled=true, inherited ordinary package process. Explicit
enabled=true/process=package are accepted as equivalent defaults; other values fail.
No alias, service, receiver, provider, authority/grant policy, extra application
child or root-level security construct is approved.

Main: one MAIN action plus LAUNCHER category. Enrollment: exported, excluded from
recents, expected label, exactly VIEW with DEFAULT+BROWSABLE and one data declaration
scheme=otpauth/host=totp; exactly SEND with DEFAULT and MIME text/plain. No extra
filter attributes, paths, ports, schemes, categories, actions or MIME registrations.
Unknown security-relevant constructs fail review rather than being ignored.

## 8. Permissions

Exact single unqualified uses-permission USE_BIOMETRIC, with no attributes beyond
name. Additional requests, duplicate requests, permission definitions, SDK-qualified
variants and unknown declaration forms fail. This expected policy comes from the
reviewed manifest and established no-network/camera/broad-storage design, not from
adopting candidate output. SAF grant behavior remains separately tested.

## 9. Backup, release flags and process policy

Both variants require literal allowBackup=false. Release fullBackupContent and
dataExtractionRules remain absent, matching reviewed source; introducing either
requires review. Debug retains fullBackupContent=false and its exact packaged
extraction exclusions. This does not add a new backup configuration.
Release debuggable/testOnly must be false; absent attributes use Android defaults.
Debug requires debuggable=true and testOnly=false. Resource-valued booleans and
unknown flags (including profileable children) fail. Application enabled defaults
to true and process defaults to the package; changed values fail. Compile/platform
37/17, min26 and target37 remain exact. No custom framework default model beyond
these specific equivalences. The [AOSP package parser](https://android.googlesource.com/platform/frameworks/base/+/8c8c81dd9e53dfa76ccbb6868e627c470fb5a59c/core/java/com/android/internal/pm/pkg/parsing/ParsingPackageUtils.java)
also establishes false defaults for debuggable and testOnly.

[Android documents](https://developer.android.com/guide/topics/manifest/application-element)
manufacturer-specific device-transfer limits for allowBackup=false on Android12+.
No claim of universal OEM transfer prevention is made. Actual no-backup private
storage and historical device evidence remain separate; see section 25.

## 10. Debug and release distinction

Debug adds exactly four existing exported probe activities with their reviewed
labels. Only StorageProbeActivity adds MAIN/LAUNCHER. It retains debug-only flags
and extraction policy. Release cannot accept any of these components, even if
nonexported. No production/debug source, resource or manifest was modified.
Small DEX namespace/diagnostic exclusions and app Test/Tests descriptor exclusions
remain; compilation and real JVM/device tests replace exhaustive class inventories.

## 11. Removed implementation inventories

Remove the production bootstrap/controller/NIO/core/BC/enrollment class presence
lists, retired API/openPrivate checks, individually enumerated historical/fake/test
class spellings and internal diagnostic/output/hash strings. Ordinary implementation
presence is established by compilation and application tests; final manifest policy
establishes entry points. Retain DEX presence, JUnit/standalone qualification-package
exclusions, common application test descriptors, debug package and the few debug
fixtures/timing helpers that live outside that package. No replacement positive
class/package inventory or source-text scanner was introduced.
Existing no-QR source guard and Maven boundary remain unchanged. Remove only the
obsolete verifier-string assertions at the end of AuthPerfSafetyTest
diagnosticsStayInDebugWithNoCredentialStorageOrProductionDependency; all its source
isolation, no-credential-storage, benchmark, logging and other behavior checks remain. Retain the short existing forbidden external SDK namespace list (cloud/QR/camera,
background work and unapproved AndroidX adapters): the current Maven graph check
requires Totipo/BC edges but does not enforce an exact whole-runtime module allowlist.
Final permissions/components alone cannot replace that dependency exclusion. This
is a small negative dependency policy, not a production-class presence inventory.
Final review restored this proportionate protection before the final artifact gates;
focused policy and release/source-aware evidence are renewed for that verifier edit.
It does not change APK-producing inputs or invalidate Gradle checkpoints.

## 12. Branding and resources

Retain original resource/provenance bytes. Final verifier requires app/round icons,
all five density variants plus adaptive resources, both drawable layers, labels,
packaged paths and exact adaptive layer references. Debug extraction XML also must
exist and decode to the reviewed rules. Resource IDs are resolved from native output,
not pinned across builds. Source branding-provenance.json remains legitimate Git-bound
maintenance evidence; artifact transfer/signing no longer rehashes every source icon.
No new branding manifest, registry, artwork or pixel-perfect regression claim.

## 13. Signing, payload, hashes and provenance

Native apksigner verifies valid sole expected certificate, v2/v3 true and v1/v4 false;
zipalign checks final bytes. Public production digest remains
74fd4c313856e1773fd174fba4ecd2a12cc4fde85ae34a3d8a3e986353b01632.
payload.py is byte-identical: every ZIP entry/order, central metadata, local header,
compressed bytes and decoded hash must match the qualified unsigned payload.
Comparison now occurs inside standalone signed acceptance, so callers cannot omit it.
This proves signed manifest/resources/DEX preservation without rechecking complete
signed policy after complete unsigned policy; signed metadata also has direct native
inspection. Unsigned SHA and provenance remain immutable. Final signed checksum
and hosted whole-file equality preserve accepted identity across transfers.
Source commit/tag/version, Nix output/derivation, unsigned SHA, signer and signed
SHA remain bound by the existing provenance/checksum workflow, not a new authorization
format. Protected signing credentials, algorithm/order/PKCS12 handling and job scope
are unchanged. No production credential was accessed.

## 14. Before/after invocation graph

Before: package Gradle check full release → installCheck debug+release → prepare
full unsigned → sign pre/post full unsigned → finalize bundle full unsigned + signed
verifier full unsigned/full signed + external payload → publish bundle full unsigned
+ signed verifier full unsigned/full signed + external payload → hosted equality +
signed verifier full unsigned/full hosted signed.

After: package installCheck debug+installed release → prepare source/metadata/hash
binding → sign pre/post source/metadata/hash binding → independent finalize complete
unsigned policy + native signed signature/identity/alignment + payload → publish
independently repeats signed acceptance across its distinct credential/artifact
handoff → hosted whole-file equality, source revalidation, final publication.

For a successful routine release with a fresh package, full-policy command count
falls from 14 (including Gradle) to 4; signed native verification falls from 3 to 2;
payload comparison remains twice, now inside each acceptance. Metadata/hash checks
still occur at separate handoffs. The local normal/strict/Nix ladder is preserved;
local final host artifact checks are a distinct qualification claim, not added to
those release graph counts. No credentials or permission boundary is merged.

## 15. Nix, Gradle, CI and source membership

Remove only Gradle verifyReleaseApkBoundary and its check dependency. Gradle still
owns assembly, tests/lint, locked build classpath and both Maven boundaries. Nix
installCheck already verifies both variants and checks the installed release file
itself; its authoritative role is documented. Host commands remain explicit.
CI still invokes package-inclusive flake check and unchanged-input validation.
Release YAML changes only a display label to describe hosted-byte acceptance.

package.nix selects all tools (including new policy tests, imported source.py and
payload.py), app/src and Gradle/version metadata; evaluator/cache inputs remain.
flake release-tooling selects tools/release and tools/verify-apk.py plus actual
build/public-identity prerequisites. New tests execute via existing discovery,
without skips or new runner. Lazy APK-policy loading preserves source-only packaged
prepare-release/release-tag, whose Nix store layout contains only tools/release;
a focused installed-layout command test covers this constraint.
AGENTS/root README/review/release README are outside effective checks; signing README
and workflow remain existing release-tooling inputs. No missing policy data is skipped.

## 16. Focused tests and diagnostics

New test_apk.py has six focused tests with decoded release fixtures and parameterized
security mutations: identity/name/code, min/target/compile, permissions including
qualified/duplicate forms, all component kinds/export states, filters/ingress/process,
backup/debug/test flags, missing/duplicate declarations, source metadata authority,
missing tools/APKs, native failure, malformed XML and essential resources.
These are policy-logic tests, not independent binary XML validation or device tests.
Both actual APK variants also pass SDK-backed policy inspection during development.
Additional real-SDK negatives reject a non-APK native inspection failure, the actual
release APK against disposable wrong-version/wrong-code expectations, and a ZIP
fixture made from that APK with an essential icon file removed. No fixture was installed.
Existing signature-output, alignment, payload/ZIP, source/tag/allocation/publication
and refused-mutation regressions remain. Add installed-layout source-only CLI test.

The first strengthened signing smoke failed a new payload-specific assertion: its
existing Python-repacked negative APK was rejected by alignment before comparison.
Preserved failure log and a narrow native zipalign check identify BAD offsets.
Fix only the disposable negative fixture with native zipalign before generating keys;
then require the signed verifier specifically to report APK ZIP payload differs.
Focused corrected smoke passes all six negatives. This is a test-fixture correction,
not a product/artifact repair; no full Gradle gate was rerun for that diagnosis.
Intermediate release suite: 61 PASS,12.602s; later installed-layout test adds one.
Initial62-test full-checkout suite: **62/62 PASS**,15.837s, zero skips (before
the last retained-exclusion review; renewed final evidence is in section18). Matching the
actual flake release-tooling fileset in a temporary source directory with private HOME:
**62/62 PASS**,14.575s, zero skips,17 selected files. No Nix evaluator or derivation
PASS is claimed by that source-membership exercise.

The first strict run exposed one missed verifier consumer: the JVM AuthPerfSafetyTest
expected literal TotipoAuthPerf/AuthPerfBenchmark strings in the checker. Its other
security assertions passed; totals were336 tests, one failure, no errors/skips.
Normal had reused that test task because tools/verify-apk.py was not a declared test
input. Saved XML and command output, then reproduced with the single affected method:
FAIL in1s,22 actionable,1 executed/21 up-to-date. Removed only the obsolete string
inventory assertions under this milestone's authorized inventory removal. No
substantive behavior/security assertion was removed, and no product discrepancy
was found. Focused class result and renewed normal/strict gates follow below.
The JVM edit invalidates the earlier normal checkpoint and failed strict result;
release/source-aware suites do not consume this JVM source and remain valid.

## 17. Final Gradle and artifact qualification

Commands are exactly the normal and offline/no-daemon/no-configuration-cache/
no-build-cache/rerun-tasks/strict-verification clean gates in AGENTS.

| Gate | Result |
| --- | --- |
| First final normal (later invalidated by JVM guard edit) |PASS,9s;89 actionable,12 executed/77 up-to-date; test task reused|
| First strict (obsolete inventory assertion failure) |FAIL,2m20s;57 actionable/all57 executed;336 tests,1 failure,0 errors/skips|
| Single affected method, pre-fix |FAIL,1s;22 actionable,1 executed/21 up-to-date|
| Focused AuthPerfSafetyTest class, post-fix |PASS,2s;22 actionable,2 executed/20 up-to-date;6 tests,0 failures/errors/skips|
| Renewed final normal |PASS,1m57s;89 actionable,36 executed/3 from cache/50 up-to-date;336 tests,0 failures/errors/skips|
| Renewed strict offline clean |PASS,2m24s;91 actionable/all91 executed, no task reuse;336 tests,0 failures/errors/skips|
| Final debug and unsigned release policy |PASS,two external commands against those strict outputs|
| Offline releaseRuntimeClasspath and verifyMavenBoundary |PASS,853ms;2 tasks executed;NIO/core0.2.0 and BC1.86 only|
| Local documentation targets, shell syntax, whitespace |PASS|

The sole extra full cycle is required by the missed external-file source guard:
the JVM test edit invalidated the earlier normal result, and strict had failed.
Focused reproduction/fix preceded the renewed full gates. No product behavior test
failed. Restoring the small external dependency exclusion list affected only APK
verifier/Python evidence, with no Gradle rebuild invalidation.
No Gradle embedded APK verifier executes after the wiring change; baseline executed
it once. Existing AAPT2 experimental, deprecated Java API and Gradle deprecation
warnings remain visible. Logs are ignored local evidence under .qualification/phase-c,
never Git source inputs.

## 18. Release suite and disposable signing

Final frozen full-checkout release suite: **62/62 PASS**,20.639s,zero skips.
Final matching source-aware fileset: **62/62 PASS**,21.374s,zero skips,17 files.
These renew the earlier62-test suites after the last APK-verifier exclusion review.
Standalone offline Gradle metadata harness: PASS,12s,one executed task; valid
synthetic metadata,13 malformed code inputs and missing file handled as intended.
Direct final real source_ready and private-material guard PASS without remote calls
or real source/ref mutation. No policy test is skipped for absent fixture data/tools.

Final strict-output disposable signing smoke **PASS**: native sole disposable signer,
v2/v3 enabled,v1/v4 disabled, direct source/provenance metadata, alignment, complete
payload equality, unsigned/provenance immutability, and all six expected negatives
(wrong fingerprint, wrong key, unsigned input, signature tamper, aligned correctly
signed altered payload, malformed fingerprint). The altered-payload negative must
specifically report APK ZIP payload differs, so earlier alignment rejection cannot
masquerade as that proof. Accepted signed TEST APK SHA-256:
`a1579536119b728e8ba7b68d57036521e2cef15dd475a2eece37af8d721be821`.
That disposable signed artifact is erased with its temporary directory; no signed
reproducibility or production release claim. Two earlier focused smoke invocations:
one new assertion failure and one corrected PASS.
Disposable keys are removed before repository Python resumes, and temp directories
are erased on exit. No production or persistent local qualification key is used.

## 19. APK byte identities

Completed baseline SHA-256:

```text
debug 417e70e435312d2350bd4dd120d24cf9a016fc78028af68592ba92286af75c9c
unsigned release 48b0fd86b42f3cd85f0dc9af86ffbb551a2fa4ce26e193a0459512d268e0f640
```

Final strict-clean APK SHA-256 values are exactly the two hashes above; full-byte
comparisons against both completed baseline copies also return equal. No APK output
change needs explanation or device requalification. Pre-build cached hashes matched PhaseB's
historical outputs; baseline reran extractReleaseVersionControlInfo and packaging.
The completed baseline release contains AGP's META-INF/version-control-info.textproto
with starting HEADd8bfe340… as inspected.
The authoritative comparison uses the completed baseline, not stale pre-build bytes.
Baseline copies are ignored local evidence.
Source/version/build-producing behavior remains unchanged; no universal independent
reproducibility or signed reproducibility claim is made.

## 20. Human Nix

**PASS, human-reported.** After the final included-input freeze and host checks,
the owner reported `nix flake check passed`. One human invocation reported; agent
Nix executions remain zero. No log, duration or task/cache breakdown was supplied,
so no independent execution or per-check breakdown is claimed. The complete frozen
non-report diff and starting HEAD were verified unchanged after the result. Only
this excluded report is updated; that does not invalidate the Nix PASS.

## 21. Device evidence

Zero physical/device harness runs or installs. All production application source,
resources/manifests and device harnesses remain byte-identical. JVM tests retain all
336 test methods; only the redundant verifier-string assertions described above change.
No full SAF/biometric/Syncthing replay is required for tooling-only changes with
unchanged final payloads. Historical no-backup/SAF/Keystore/clipboard/lifecycle and
transport evidence remains historical; policy fixtures do not renew that evidence.
Final baseline/strict byte equality confirms no artifact difference requiring new device work.

## 22. Dependencies, locks and cache

No VERSION/CODE, Gradle/AGP/SDK/JDK, Maven dependency, lock, verification metadata,
flake.lock or package-deps.json change. No dependency cache regeneration, sdkmanager,
production signing identity change or local qualification identity migration.
No signing/release authorization format or settings modification.

## 23. LOC and maintenance accounting

Measured physical lines and bytes across changed implementation/configuration/tests,
excluding documentation and this report:

| Surface | Before lines | After lines | Net lines | Net bytes |
| --- | ---: | ---: | ---: | ---: |
| Implementation/configuration |1029|1076|+47|-3303|
| Python/JVM tests |544|701|+157|+9511|
| Total |1573|1777|+204|+6208|

verify-apk alone changes159→213 lines and14765→11944 bytes (2821 bytes fewer).
There is **no net total LOC reduction**: complete missing security policy and focused
negative coverage require additional readable code/tests. No reduction target was
used to remove a guarantee. Maintenance reduction is one manifest representation,
one version parser, removal of long implementation-name inventories, and14→4 full
policy executions per fresh successful release graph. No measured release-duration
or remote-CI speedup is claimed.

## 24. Withdrawn guarantees and introduced assumptions

No longer hash-check every source artwork asset during each APK transfer, assert
every ordinary production class, or blacklist every historical/internal marker.
Existing forbidden external SDK namespaces remain checked. No exhaustive final DEX implementation inventory is claimed.
Git review/provenance, AGP construction, application tests, essential final resources
and exact manifest platform policy take their respective places. Source branding
provenance remains intact, without a pixel equivalence claim.

Whole-file equality preserves prior native signed acceptance; exact payload comparison
preserves prior unsigned platform policy. These are explicit identity relationships,
not trust in the signing job's assertion. Independent unsigned acceptance remains in
both verification and publication contexts. Preparation trusts only the authenticated,
already-qualified Nix package output for full policy, then inspects source-bound metadata.
Pinned native SDK XML/resource representation is assumed usable; changes fail closed.
No stronger trust in a compromised signing key, authorized source editor, external
GitHub administrator or OEM backup implementation is claimed.

## 25. Platform findings and owner decisions

No unexpected effective permission/component/version or product manifest discrepancy
was found. Existing release source has no dataExtractionRules/fullBackupContent;
allowBackup=false plus no-backup storage is its reviewed policy. Android documents
OEM-dependent device-transfer limits. A universal transfer-prevention requirement
would need a separately authorized product/security assessment and affected physical
backup/restore/device-transfer evidence; Phase C neither changes behavior nor claims
that universal guarantee. Debug exclusions cover shared preferences, not a new global
transfer claim. Clipboard cleanup remains best effort and no manifest check proves it.
No owner authorization-policy change or live release is needed for this milestone.

## 26. Freeze, execution counts and final Git

Final stable freeze, after completed host qualification:

- **Production source stable:** main/debug/release Java, manifests/resources,
  VERSION/CODE are byte-identical to starting HEAD. Both strict APKs are byte-identical
  to completed baseline APKs. No product/device behavior or identity changes.
- **JVM tests stable:** only the obsolete verifier-string assertions change;
  all336 test methods remain, with focused6-test class and renewed normal/strict
 336-test passes. No subsequent JVM edit.
- **Build/package configuration stable:** obsolete Gradle APK Exec removed;
  Nix installCheck remains authoritative for both variants and installed release.
  All dependency/lock/toolchain/cache inputs unchanged. Renewed normal/strict,
  final artifact and runtime graph checks pass.
- **Qualification harness/tooling stable:** native-XML policy, release metadata,
  signed payload acceptance, hosted-byte optimization and disposable aligned negative
  fixture finalized. Final62-test full/source-aware suites and signing smoke pass.
  Device harnesses and payload.py remain byte-identical. New test/report Git-known
  through intent-to-add; no staged file contents. Only excluded report edits follow.

The complete non-report diff is retained in ignored local freeze evidence at
.qualification/phase-c/frozen-implementation.diff; SHA-256
`c3a647afabdfab219945315d700254545095c314e297a68848a1093699bae839`
identifies local qualification evidence, not a new release source registry or authorization format.
After the owner-reported `nix flake check` PASS, this frozen non-report diff was
verified unchanged. Included tools/evaluator inputs would invalidate Nix if changed;
only this excluded report changes, so the result remains valid. Agent Nix executions
zero; human Phase C Nix invocations/results one/PASS, human-reported. No package-deps
refresh or renewed qualification is required.

| Execution category | Count/result |
| --- | --- |
| Baseline full Gradle |1 ordinary PASS|
| Focused Gradle |2: obsolete guard method FAIL; six-method class PASS|
| Standalone metadata Gradle harness |1 offline PASS|
| Final normal full |2 PASS; first invalidated by necessary JVM guard edit|
| Strict-clean full |2: first sole obsolete assertion FAIL; renewed PASS|
| Runtime graph/boundary |1 final offline PASS|
| External final artifact set |2 commands, debug+unsigned release PASS|
| Embedded Gradle APK verifier |1 at baseline;0 after task removal|
| Earlier direct APK checks |2 old-verifier commands against available pre-build APKs;4 focused new-verifier commands across two implementation stages|
| Additional SDK negatives |native malformed-APK failure;2 real-APK wrong-metadata expectations;1 removed-icon APK policy rejection|
| Full-checkout release discovery |4: baseline55,intermediate61,initial62,final62;all PASS/no skips|
| Source-aware discovery |2: initial62 and final62;both PASS/no skips|
| Narrow Python suites |3 policy invocations(6 each),2 release invocations(20 then21),1 publication invocation(5);all PASS|
| Disposable signing smoke |3: new payload-specific fixture FAIL, corrected focused PASS, final strict-output PASS|
| Smoke nested full unsigned policy |5 per smoke,15 total including deliberate invalid-signed candidates; no duplicate full signed-policy invocation|
| Physical harness/install |0|
| Agent/human Nix |0/1;final PASS, human-reported|
| Production signing/live release/repository ref mutation |0|

Repeated full gates are solely the missed JVM verifier-string consumer correction;
focused diagnosis precedes renewal. Repeated release/source-aware suites follow the
new installed-layout test and last retained external-SDK exclusion review. The
corrected disposable fixture is diagnosed with native alignment before repeating
smoke. No unrelated full device matrix or additional routine Nix/build is requested.

Final branch `main`, HEAD`d8bfe34028007abd06e8fe0e3b393f96a92843c5` unchanged.
Complete status follows; git diff --name-only is exactly the same15 paths.
git diff --cached --name-only is empty; git diff --cached --quiet and git diff --check
pass. New files have intent-to-add only. Ignored builds, logs, caches and disposable
keys stay outside Git source membership.

All changes will remain unstaged in content and uncommitted, with new report/test
intent-to-add only. No commit/tag/push in this repository, workflow dispatch/publication, GitHub settings
change, production credential use or agent Nix execution is authorized or performed.
Existing Git behavior tests mutate only disposable repositories and bare fake remotes.

```text
 M .github/workflows/release.yml
 M AGENTS.md
 M README.md
 M app/build.gradle.kts
 M app/src/testDebug/java/org/totipo/android/debug/AuthPerfSafetyTest.java
 M package.nix
 M release/README.md
 A review/ANDROID_APK_VERIFICATION_SIMPLIFICATION_REPORT.md
 M tools/release/release.py
 M tools/release/signing-smoke.sh
 A tools/release/test_apk.py
 M tools/release/test_publication.py
 M tools/release/test_release.py
 M tools/release/verify-signed-apk.py
 M tools/verify-apk.py
```
