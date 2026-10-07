# M1C local replica qualification and reconciliation

Gate A failed on the supplied physical API-37 run. Reconciliation design is
stopped for design review. Agent checks and final human Nix validation pass.
This final report closes the qualification work with a negative reuse verdict;
M1C reconciliation/completion criteria remain unmet because Gate A failed.
No Android-wide filesystem-support claim follows.

## 1. Starting state

- Branch: `main`.
- Exact HEAD: `da2e6506af973b3b39889355d5b95916dbc37ca4` (committed M1B).
- `git status --short`: empty before any change.
- Consumed Java: `org.totipo:totipo-core:0.1.3`, source commit
  `e2326aca5f5aac661d74925a30fcc57cd91014e8`, tag `v0.1.3`.
- Spec: v1/r18, committed pin `4623a7e1718e23504903096c92332597057bd8f0`;
  no r18 release tag asserted.
- Upstream inspection uses existing ignored commit-specific archives under
  `.gradle/m1-inspection/`. Neither upstream is modified. Compared 46 Java
  storage/test/review/design files to a fresh exact GitHub commit archive.

## 2. NIO dependency qualification boundary

`debugImplementation("org.totipo:totipo-storage-nio:0.1.3")` is solely a physical-device
qualification dependency. It is not a release dependency or production architecture
commitment. `verifyMavenBoundary` requires precisely core 0.1.3 in release compile/runtime,
and precisely core plus NIO 0.1.3 in debug compile/runtime, with direct external module
edges. Artifact inspection rejects file/project JARs and project substitution.
Centralized repositories exclude Totipo from Google Maven; no Maven Local or composite
build is configured. No source copied from upstream supplies Android storage.

`verifyReleaseApkBoundary` is wired to `check`, builds release and asserts no
`org/totipo/storage/nio/` or debug diagnostic descriptors in DEX. The APK checker
retains its default NIO exclusion; only explicit `--debug-nio` requires NIO and the
local probe in debug. CI uses that flag for debug; release remains strict.

Provenance checked directly against [Maven Central module metadata](https://repo.maven.apache.org/maven2/org/totipo/totipo-storage-nio/0.1.3/totipo-storage-nio-0.1.3.module)
and [POM](https://repo.maven.apache.org/maven2/org/totipo/totipo-storage-nio/0.1.3/totipo-storage-nio-0.1.3.pom).
Both declare only core 0.1.3 as the NIO dependency. Module variants declare Java 17.
The downloaded JAR is 38,663 bytes and matches the module's SHA-256.
All 14 published sources match the exact released source inspection byte-for-byte.
The annotated tag object `f2cc94f426bdcdffb59c12440c47d660f248e39d` resolves to the exact
Java commit above. The [release provenance](https://github.com/totipo-org/totipo-java/releases/tag/v0.1.3)
records the same Java/spec revisions as Android's existing core pin.

| Download | SHA-256 |
| --- | --- |
| NIO JAR | `691b56c8831f71dafb7d5cb6f7d68ee59c9f42c5bb19c29c9455e1925237334e` |
| NIO module | `61761b8105c7839a8fe43e47b26cf84bad395d5e5c6fac6ba63c0d387aae790b` |
| NIO POM (inspection only) | `7cc142e9e3464512c5df2836f1615f0b9a457f645c3f9fa1ed60a5367ed2d932` |
| NIO sources JAR (inspection only) | `038d223dc45aa6d853731da3e73bb54de88b1b3aebffb6708195ea2c3a8c0b5d` |

Inspected `bootstrap-m0.sh` before running its explicit `--refresh-dependencies`
mode. That path runs wrapper/toolchain verification and direct Gradle lock/SHA-256
refresh, with no Nix invocation. Existing Nix-managed SDK/JDK used, no alternate
installed toolchain. Generated changes: one NIO 0.1.3 lock entry across debug/test
configurations, one NIO component containing JAR/module hashes in verification
metadata, two matching reviewed hash entries. No other dependency/version changes.
Core remains 0.1.3; buildscript locks unchanged. POM/sources are not added to the
resolved build graph. Human-regenerated `package-deps.json` has been inspected: only NIO 0.1.3
JAR/module/POM and its publisher metadata were added. All existing cache entries
are unchanged; all three downloaded artifact hashes match actual Central bytes.
The cache update is required because
Nix `gradleCheckTask = "check"` now resolves the debug dependency. `package.nix`
requires its NIO JAR and metadata alongside core and BC before a frozen build.

## 3. NIO filesystem requirement matrix

Inspected released `storage-nio/build.gradle.kts`, all 14 production classes,
publication/vault/durability/SPI/exact-name tests, `SPI_DESIGN.md`, `API_DESIGN.md`,
README qualification, and STORAGE_SPI/PUBLIC_API_FACADE/release/publication reports.
Entry points: `NioTotipo.create/open` delegate to `NioTotipoStore` and core;
`Totipo.create/open(NioTotipoStore.open(root), credential)` exposes the store-open
exception boundary without reaching private internals. Legacy discovery/publication/
bootstrap adapters share helpers but have some distinct acknowledgement/replacement
contracts. M1C exercises the current cohesive store and facade.

| Facility | Why released NIO uses it | Required for operation? | Existing fallback? | Android qualification needed / evidence |
| --- | --- | --- | --- | --- |
| `Files.createLink` | Exclusive immutable object and initial VAULT installation from forced temporary representation | Yes for new objects and vaults | None; no overwrite/copy workaround | FAILED: composite link/collision check raised AccessDeniedException; core create returned Uncertain; save not reached |
| Regular-file observation | Reject directory/link/other content; scan kind diagnostics | Yes for reads, existing acknowledgement and replacement | None | SUPPORTED + succeeded: BasicFileAttributes regular-file fixture; full object read not reached |
| No-follow behavior | Observe real root/namespaces; reject final symlinks on reads/file force/directory force | Yes; same-privilege namespace remains trusted | None | SUPPORTED + succeeded: attributes/stream/channel primitives; symlink open rejected with IOException |
| Bounded actual-byte reads | `readNBytes(expected)` plus one EOF/extra byte; existing comparisons bounded to bytes+1 | Yes; metadata size never decides validity | None | SUPPORTED + succeeded: bounded stream read; released object read NOT EXERCISED |
| `FileChannel.force(true)` | Force staged bytes, post-link object, existing exact retry | Yes for acknowledged mutation | None; failure is not durability | SUPPORTED + succeeded: primitive write/force; actual create Uncertain; save/exact retry NOT EXERCISED |
| Directory open READ/NOFOLLOW and force | Containing-directory namespace persistence via default `NioDurability` | Yes for acknowledged writes | None; wrapped IOException retains cause | SUPPORTED + succeeded: raw directory and default NioDurability primitives; full workflow durability not qualified |
| Root-directory persistence | Every new object acknowledges `objects-v1` namespace before target mutation; exact retry forces object directory then root; VAULT install/replace forces root | Yes; not a cached once-per-process claim | None | SUPPORTED + succeeded: released root sync primitive; workflow reopen NOT EXERCISED. Existing configured root required; provider does not create/persist its parent |
| `Files.move` replacement | Make the verified staged VAULT canonical | Yes for rewrap | Regular replacement move if atomic unsupported in current store | SUPPORTED + succeeded: direct replacement move; core rewrap NOT EXERCISED |
| `ATOMIC_MOVE` | Preferred VAULT replacement with `REPLACE_EXISTING` | Optional for current store; required only by legacy low-level adapter | Catches precisely `AtomicMoveNotSupportedException`; other failures do not trigger fallback | SUPPORTED + succeeded: primitive atomic replacement; actual default replacement NOT EXERCISED |
| Replace fallback | Same prepared stage moved with `REPLACE_EXISTING`; errors after entry uncertain | Available current-store path, not CAS | Built in; no Android fallback added | SUPPORTED + succeeded: underlying regular move; released fallback selection NOT EXERCISED |
| Temporary files | Noncanonical `.totipo-object-*.tmp` / `.totipo-vault-*.tmp`; stage actual bytes then validate | Yes for new writes | None | SUPPORTED + succeeded: temp primitive; core initial staging reached install attempt; no publication/replacement success |
| Directory creation | Lazy exact `objects-v1` on publication; store open itself read-only | Yes on first publication | Reobserve an exactly spelled concurrent winner on collision | SUPPORTED + succeeded: mkdir; initial released namespace scan Complete/empty; objects-v1 creation NOT EXERCISED |
| Exact filenames/direct-child enumeration | Reject lookup aliases, preserve exact `vault`, `objects-v1`, object names; no recursion | Yes; ordinary filename-unique local namespace | No name normalization | SUPPORTED + succeeded: case-distinct names; initial released empty scan; object scans/reads NOT EXERCISED |
| Permissions/type observations | Basic attributes without follow; require usable local read/write/create/force access | Type checks/access required; POSIX chmod/owner queries are not used | Fail unavailable/unsafe/unsupported conservatively | AccessDeniedException in hard-link check; exact permission/policy cause not isolated. No POSIX mode mutation test; upstream POSIX/mkfifo/socket fixtures are not operational requirements |
| Cleanup | Delete temporary representation on close/finally | Best effort; abandoned noncanonical files carry no authority | Ignores cleanup IOException/SecurityException | SUPPORTED + succeeded: temporary-file fixture delete; initial store close completed; session close/reopen NOT EXERCISED |

These are implementation facilities, not a declaration that hard links, POSIX
permissions, or atomic move are normative protocol mechanisms. SPI replacement is
explicitly not CAS and permits weaker replacement; the current NIO fallback must
retain uncertain outcomes. Upstream evidence is case-sensitive local Linux,
accepted force calls, reopen, and injected failures. It does not qualify Android,
other filesystems, global crash safety, or universal power-loss durability.

## 4. Physical-device local-store qualification

Human supplied one completed diagnostic run (API 37); run ID
`058fbefc-4b1c-4dfd-97b0-969cbb6c6afa`. Device model/OS build were not supplied.
Separate debug `LocalNioProbeActivity` with platform UI only, one
**Run local NIO qualification** action and **Copy diagnostic report**.
The initial device attempt found the run button overlapped the Android system
bar and could not be tapped. The Activity now applies system window insets plus
12dp padding to all four sides, matching the existing SAF probe approach; the subsequent run produced the evidence below.

The runner uses a new UUID directory under
`Context.getNoBackupFilesDir()/m1c-nio-qualification/` on a worker thread.
No real vault, provider tree, storage permission, password input or product UI.
Disposable directories are retained for evidence; no automatic broad cleanup.

No-backup storage is investigated for ordinary app-private filesystem access,
no broad permission, independence from cloud/provider availability, exclusion
from Android backup/restore, and suitability as a possible canonical replica.
Passing this probe does not choose the final product location.

Primitive output uses SUPPORTED + succeeded, SUPPORTED + failed with exact class,
UNSUPPORTED for an explicit UnsupportedOperationException or
AtomicMoveNotSupportedException, and NOT EXERCISED.
A supported-but-failed line means an invocation was attempted, not proof of a
working platform facility. IOException/linkage failures are retained without
exception-message parsing. Optional symlink creation is fixture construction,
not a released production requirement. Force-call acceptance is bounded evidence.

Harness workflow sequence (the supplied run stopped at step 2):

1. Default `NioTotipoStore.open`, observe absent VAULT/namespace, close.
2. `Totipo.create` with a visibly test-only non-secret credential; real core
   VAULT staging/read-back/link/force; close and authenticate reopen.
3. `session.state().createToken()` with fixed disposable test secret and save:
   require `SaveResult.Saved` and one revision. No fabricated envelope bytes.
4. Close session; released SPI bounded-read the core-authored 1024-byte object;
   republish those exact bytes and require `ObjectWrite.AlreadyPresentExact`.
   This separately qualifies the public SPI exact-existing acknowledgement;
   it does not claim a facade builder supports resaving an already-saved operation.
5. Reopen; wait up to 30 seconds for the public state publisher's initial
   Finished observation, report errors, require no diagnostics and same token/revision.
6. `changePassword(initial, replacement)` requires CHANGED. Close; old credential
   requires AuthenticationFailed; new credential must authenticate same fingerprint
   and observe the same token. Clean final close.

Result/exception classes and SPI failure reasons are preserved. Default
`NioDurability` is never overridden. A dependent failure stops the workflow;
remaining dependent steps are NOT EXERCISED. Exception messages/absolute private
paths are omitted. Linkage failures count as qualification evidence; no added
Java compatibility code/desugaring dependency conceals them. The qualification
runner intentionally suppresses lint's newer-library API warnings to test actual
runtime availability on the reported physical API level.

Human commands, from the repository's existing development shell:

```sh
bash bootstrap-m0.sh
"$ANDROID_HOME/platform-tools/adb" install -r app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_HOME/platform-tools/adb" shell am start -W -n org.totipo.android/org.totipo.android.debug.LocalNioProbeActivity
```

Tap **Run local NIO qualification** once; wait until the report ends with
`Gate-A verdict=INCONCLUSIVE until physical evidence is reviewed` (a successful
harness does not independently issue a milestone verdict). Copy with the explicit
button, or retrieve the stable private report:

```sh
"$ANDROID_HOME/platform-tools/adb" shell run-as org.totipo.android cat no_backup/m1c-nio-qualification/latest-report.txt > /tmp/totipo-m1c-local-nio.txt
cat /tmp/totipo-m1c-local-nio.txt
```

Send that output with the physical device model/OS version. No USB access is
assumed by the agent. No M1B experiment repeated.

## 5. Gate-A verdict

**FAIL**

Released storage-nio 0.1.3 did not operate correctly unchanged in this physical
device's disposable no-backup app-private directory. Store construction and
initial observation succeeded, but actual released core/NIO initial VAULT creation
returned `org.totipo.CreateVaultResult$Uncertain`. No successful created session
exists in the evidence. The hard-link primitive raised
`java.nio.file.AccessDeniedException`; hard links have no existing fallback for
initial VAULT installation or new immutable objects. API availability and success
of force/move primitives do not compensate for failed creation.

Scope and diagnosis limits:

- Exact failed public operation: `Totipo.create(NioTotipoStore.open(root), initial)`.
  Core had reached the initial canonical installation attempt: a preparation
  failure would return Failed, not Uncertain. Uncertain also permits a runtime
  failure during subsequent session construction; the public result alone does
  not prove which inner operation failed. Released `NioVaultStorage.Stage`
  initial installation calls `Files.createLink(root.resolve("vault"), temp)`
  then default `NioDurability.syncDirectory(root)`. That mutation boundary is
  marked before the link call, so a denied link may produce Uncertain even when
  no VAULT was installed. The result must not be reclassified as definite absence.
- The primitive wraps initial createLink, isSameFile, collision createLink,
  source deletion and read-back in one check. There is no success marker within
  it in the supplied run. Thus it records a hard-link workflow access denial,
  without conclusively isolating which call threw. The public facade also does
  not expose the underlying initial-install exception. An exact OS/SELinux/
  filesystem policy cause or matching exception inside NIO is not established.
  The failed composite primitive and failed released creation are corroborating
  evidence, not an exception trace connecting the two.
- Root/directory force primitives succeeded, making the hard-link installation
  path the leading portability concern, but accepted force calls do not exclude
  a later barrier failure or prove physical durability. No Android-wide hard-link
  prohibition is claimed from this single run.
- `java.lang.IllegalStateException` is the harness's expected-result check after
  receiving Uncertain; it is not an additional released NIO filesystem exception.
- Authenticate/reopen, actual immutable publication/read/exact duplicate,
  objects-v1 creation, VAULT replacement, old/new credential behavior and same-root
  persistence are all **NOT EXERCISED**. Do not claim their success from primitives.

The raw harness's final INCONCLUSIVE line intentionally defers the milestone
verdict to human evidence review. With supplied evidence, the report verdict is
FAIL: required end-to-end creation failed. Incomplete root-cause isolation does
not make unchanged NIO qualify. The candidate app-private location is not adopted.

Primary follow-up classification: **totipo-java portability work**. Review the
released exclusive-install hard-link dependency and its behavior in Android
app-private execution before choosing any replacement mechanism. If a portable
provider cannot supply the required no-replace/durability semantics, review an
**Android-specific local store design** separately. Neither is implemented here.
Optional diagnostic refinement can isolate each hard-link call and the initial
install cause; it must not change operations or hide failures. No further device
run is required to record this failure, and no fix is presumed possible.

Return for design review. Gate B remains closed: no provider candidate model,
import/export/journal state machine, VAULT reconciliation, transport-layout
verdict, desktop/iOS design, or bridge security claims are produced. Existing M1B
evidence is not re-experimented or used to bypass this gate. No NIO code is copied,
forked or modified, no durability requirement weakened, and no production SAF
bridge, product UI, protocol change or fallback is added.

## 14. Validation

### Agent non-device

PASS: `bash bootstrap-m0.sh --refresh-dependencies`, followed by strict normal
`bash bootstrap-m0.sh` after final probe edits. Seven existing JVM tests pass
(1 core smoke, 6 M1B pure logic), none failed/skipped; lint passes; debug/release
compile/runtime Maven boundaries pass; both APKs build. After the system-bar overlap fix, strict `bash bootstrap-m0.sh` passed again
(builds, lint, JVM tests and boundary checks). The release APK content
assertion passes through `check`. Separate debug `--debug-nio` and release
`--unsigned --no-debug-probe` content checks pass. Released NIO storage success is
not mocked on JVM. `git diff --check` passes and the index is empty. Published
artifact/source/tag/spec provenance checked as recorded above. No device evidence
inferred from JVM/filesystem mocks; no Robolectric/instrumentation dependency.

### Human Android device

Received one physical API-37 report reproduced below. Primitive successes are
separate from failed released creation and unexercised dependent workflows.
The system-bar placement fix allowed the run. No power-loss/crash qualification,
force-stop/restart persistence, other devices/API levels or final canonical
location are established.

```text
M1C local NIO qualification v1
API level=37
Java boundary=core 0.1.3 + storage-nio 0.1.3; default NioDurability
Storage=noBackupFilesDir/m1c-nio-qualification/<run-id>; disposable only
Accepted force and reopen do not prove physical power-loss survival.
Run ID=058fbefc-4b1c-4dfd-97b0-969cbb6c6afa
Primitive directory creation and no-follow type observation=SUPPORTED + succeeded
Primitive temporary file and regular-file observation=SUPPORTED + succeeded
Primitive bounded actual-byte read with NOFOLLOW_LINKS=SUPPORTED + succeeded
  rejected open=java.io.IOException
Primitive no-follow symlink observation/rejection (optional fixture)=SUPPORTED + succeeded
Primitive exact direct-child filenames=SUPPORTED + succeeded
Primitive FileChannel write loop and force(true)=SUPPORTED + succeeded
Primitive hard link and exclusive collision=SUPPORTED + failed (invocation attempted)
  exception: java.nio.file.AccessDeniedException
Primitive directory open READ/NOFOLLOW and force(true)=SUPPORTED + succeeded
Primitive released NioDurability root-directory persistence request=SUPPORTED + succeeded
Primitive ATOMIC_MOVE + REPLACE_EXISTING=SUPPORTED + succeeded
Primitive replacement move fallback facility (direct exercise)=SUPPORTED + succeeded
Released fallback selection=NOT EXERCISED unless actual VAULT replacement encounters AtomicMoveNotSupportedException; no injection
POSIX mode/owner mutation=NOT EXERCISED (not used by released implementation)
Workflow initial vault observation=org.totipo.spi.BoundedRead$Absent
Workflow initial namespace observation=org.totipo.spi.ObjectScan$Complete
Workflow store open/close=completed; namespace remains absent until publication
Workflow create vault=org.totipo.CreateVaultResult$Uncertain
Qualification interrupted at create vault (stage/force/read-back/link/root force): java.lang.IllegalStateException
Remaining dependent workflows=NOT EXERCISED
Harness outcome=NOT ALL REQUIRED CHECKS SUCCEEDED; human Gate-A review required
Gate-A verdict=INCONCLUSIVE until physical evidence is reviewed
```

### Human Nix

No Nix command executed by agent. The current flake exports
`packages.x86_64-linux.default` and app `update-package-deps`, whose program is
`androidPackage.mitmCache.updateScript`. Human cache regeneration checkpoint (generated file now reviewed):

```sh
nix run path:.#update-package-deps
```

Expected tracked change: `package-deps.json`, adding NIO 0.1.3 JAR/module (and
POM only if fetched). The update build runs release assembly plus `check`, which
resolves debug NIO and keeps release free of it. Expected core stays 0.1.3,
BC stays 1.86, no unrelated graph/hash updates. Agent inspection passed: JAR/module/POM match freshly downloaded Central bytes
and the resolved JAR/module match Gradle verification metadata. Only NIO entries
were added; core remains 0.1.3 and no existing entry changed. No manually fabricated cache hashes.

After cache inspection and the physical failure review, the human reported
success for both final commands:

```sh
nix flake check path:.
nix build path:.
```

**PASS — human-reported:** `nix flake check path:.` and `nix build path:.`.
No `--rebuild` requested or required. `checks.x86_64-linux.android` and
`packages.x86_64-linux.default` both reference `androidPackage`, so flake check
builds and checks the same app derivation as the default build; the second command
can reuse that result. These checks validate build/cache inputs and APK boundaries,
not physical Android NIO workflows. They do not overturn Gate-A FAIL.

### Remote CI

Not run for these unstaged changes. Ordinary existing CI retained; debug content
assertion adjusted for the qualification boundary, release exclusion remains.
No forced reproducibility rebuild introduced.

## 15. Final Git state

Final unstaged state after agent validation, physical failure review and
human-reported passing Nix checks:

```text
$ git status --short
 M .github/workflows/ci.yml
 M TOTIPO_JAVA_DEPENDENCY.md
 M app/build.gradle.kts
 M app/gradle.lockfile
 M app/src/debug/AndroidManifest.xml
 M gradle/verification-metadata.xml
 M package-deps.json
 M package.nix
 M review/M0_REVIEWED_DEPENDENCY_HASHES.json
 M tools/verify-apk.py
?? app/src/debug/java/org/totipo/android/debug/LocalNioProbeActivity.java
?? app/src/debug/java/org/totipo/android/debug/LocalNioQualification.java
?? review/M1C_LOCAL_REPLICA_RECONCILIATION_REPORT.md
$ git diff --stat
 .github/workflows/ci.yml                  |  2 +-
 TOTIPO_JAVA_DEPENDENCY.md                 | 11 ++++++++---
 app/build.gradle.kts                      | 30 ++++++++++++++++++++++++------
 app/gradle.lockfile                       |  1 +
 app/src/debug/AndroidManifest.xml         |  2 ++
 gradle/verification-metadata.xml          |  8 ++++++++
 package-deps.json                         | 12 ++++++++++++
 package.nix                               |  4 +++-
 review/M0_REVIEWED_DEPENDENCY_HASHES.json |  8 ++++++++
 tools/verify-apk.py                       |  8 +++++++-
 10 files changed, 74 insertions(+), 12 deletions(-)
$ git diff --check
(empty)
$ git diff --cached --stat
(empty)
```

Untracked source/report files are excluded from ordinary diff stat. All three new
files were also checked for trailing whitespace. Nothing staged, committed,
tagged, released or pushed. Required available validation is recorded; design
remains stopped at Gate-A FAIL for review. Remote CI was not run.
