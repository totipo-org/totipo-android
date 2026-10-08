# M1M — Signed non-debug release-path authentication qualification

**Primary decision: A. RELEASE INSTALL PATH QUALIFIED — FAST NATURAL ART STATE.**

On the tested Pixel 6a, the actual release payload, signed with a local test key,
naturally received **speed / reason=install**. One real disposable-vault creation
took approximately **0.86 s**; three correct unlocks had median **0.30 s**; two
wrong-password attempts had median **0.31 s**. Reinstalling the identical signed
APK preserved the vault and again exposed speed before launch; one captured
post-reinstall correct unlock took approximately **0.35 s**. No shell compiler
intervention occurred in M1M.

These are external UI wall-clock estimates, with polling uncertainty documented
below. The performance investigation can close for this direct ADB installation
scope. **Agent, human device and human-reported Nix validation passed. M1M is complete.**

## 1. Starting state

Before editing, agent ran `git branch --show-current`, `git rev-parse HEAD`, and
`git status --short`: branch **main**, HEAD
`cdcd91306300d5b04b03f68f8e8a9a5fb55c3cbf`, clean worktree. M1L was committed.
No applicable AGENTS.md was found in the repository or its parent directories.

ADB was available directly; initially no device was attached. The human connected
the device. Agent then performed package inspection, APK retrieval, signing,
installations, ART inspection, and timing collection directly. The device is a
**Pixel 6a, Android 17 / API 37**, fingerprint
`google/bluejay/bluejay:17/CP3A.260905.009/2026100201:user/release-keys`.
Read-only `pm.dexopt.install` inspection returned `speed`.

The existing M1L debug package had run-from-apk/reason=unknown. Its signer matched
the available M1L test key. Phase A replaced that debug package in place with the
signed actual release, preserving data. This was the initial M1M non-debug release
installation, **not a package uninstall/fresh package install**. Both the observer
and human confirmed a fresh Create Vault screen; no existing vault was overwritten.
No uninstall or data clear was required or performed.

## 2. M1L baseline/conclusion

[M1L](M1L_ART_ARGON2_EXECUTION_REPORT.md) measured approximately **20.5 s** per
production-parameter KDF in an ordinary debuggable run-from-apk installation,
**5.7 s** in a non-debug verify state, and **0.27 s** on the same non-debug diagnostic
APK with speed compilation. Its ordinary non-debug streamed installation naturally
received speed/reason=install. M1L concluded **ART PACKAGE COMPILATION EXPLAINS MOST
OF THE LATENCY**, while distinguishing the additional debuggability effect.

M1M tests the actual release payload and real product authentication, extending
that diagnostic result without re-running synthetic benchmarks or optimizing crypto.

## 3. Actual release build properties

Inspected `app/build.gradle.kts`, main/debug manifests, the binary release manifest,
`tools/verify-apk.py`, `package.nix`, dependency locks, and README signing guidance.

- Application ID `org.totipo.android`; versionCode **1**, versionName **0.0.0-dev**.
- Release is unminified and **non-debuggable**: the binary manifest omits debuggable
  (default false); installed package flags exclude DEBUGGABLE.
- minSdk **26**, targetSdk/compileSdk **37**, build tools **36.0.0**.
- Application backup is disabled. No native libraries are packaged.
- External Maven NIO/core **0.1.5**, BC **1.86**, confirmed by Gradle boundary checks.
- No release signing configuration or production/Play signing infrastructure exists.
  `package.nix` builds, verifies, and packages the unsigned release APK.
- M1K/M1L diagnostic classes and timing markers are absent from release DEX.

Validated unsigned release SHA-256:

```text
70c1708b0e37dbf206dccaad3f8eb452da86e2627087b1a2560fcbcedbc2ae0a
```

The ordinary build and strict offline clean rebuild produced this same release
hash. The unsigned repository output was never signed or patched in place.

## 4. Temporary signing method

Reused the existing ignored **M1L development/test keystore**, solely for local
installation qualification. This is not a production signing key. Its signer was
verified against the previously installed debug APK before attempting an update.

Signer public certificate SHA-256:

```text
1f14855aface333061eb0bb545251756a214aa02c31607735f003689eecf9005
```

SDK apksigner wrote a separate copy at
`.gradle/m1m-inspection/m1m-release-signed.apk`, using v2/v3 block signing, with
v1/v4 disabled. Signature verification passed; ZIP alignment passed. Keystore and
test signing-password material remain ignored local state; no secret or signing configuration
was added to tracked files. The same identity and exact APK were used in both phases.

Signed APK SHA-256:

```text
44efc2c3b74636556442aadb6aa541c7e756eda6647c964c40fc107fbcad8e4e
```

## 5. Payload equivalence

Ignored agent-side Python inspection checked ZIP integrity, uniqueness and equality
of entry names, then compared **every entry's uncompressed bytes**, including all
META-INF entries. All **10 entries are identical**. Only signing-block metadata
differs. The binary manifest was not patched.

| Entry/category | Result |
| --- | --- |
| `classes.dex` (5,880,512 bytes) | Identical; SHA-256 `f1cf5e4963942f7e4d7c03424e57e4335307c117ccd8c7761d07cb80f1d85918` |
| `classes2.dex` (560 bytes) | Identical; SHA-256 `676a25a99d6169c90df59f47630cf0a60331eead5e9dc726e0edd46b888b4583` |
| Binary `AndroidManifest.xml` | Identical; non-debuggable |
| `resources.arsc` | Identical |
| Four META-INF entries | Identical, including build/version-control metadata |
| Two BC resource property files | Identical |
| Native libraries | None in either APK |

Full per-entry hashes are in ignored
`.gradle/m1m-inspection/payload-equivalence.json`. The signed copy also passed the
existing verifier's `--no-debug-probe` check without any verifier change. The normal
unsigned-release check still used `--unsigned --no-debug-probe` and passed.
Read-only pulls after **both installations** exactly matched the signed APK bytes.

## 6. Natural install ART state

Agent used the supported normal non-incremental command:

```sh
adb install --no-incremental -r .gradle/m1m-inspection/m1m-release-signed.apk
adb shell cmd package art dump org.totipo.android
```

Install reported **Performing Streamed Install / Success**. Immediately after
installation, before launching Totipo or requesting any compilation, ART reported:

```text
[org.totipo.android]
  path: /data/app/~~N41z0CEWs5cpJzfU_Ocs2w==/org.totipo.android-r3ES0dnMBoIIxYeiVz2PMg==/base.apk
    arm64: [status=speed] [reason=install] [primary-abi]
      [location is /data/app/~~N41z0CEWs5cpJzfU_Ocs2w==/org.totipo.android-r3ES0dnMBoIIxYeiVz2PMg==/oat/arm64/base.odex]
```

The same state remained after the phase-A series. No compile/reset/profile command
was issued anywhere in M1M. Prior M1L compiler experiments were on its diagnostic
payload; the pre-M1M debug package was run-from-apk, and this actual release
installation newly reported reason=install. No M1L speed artifact is substituted
for M1M natural-install evidence.

### External timing method and limits

Used **Option A: externally visible wall-clock operation timing**, without adding
instrumentation to Totipo. A separate, ignored SDK-built observer APK
(`org.totipo.m1m`) ran self-targeted Instrumentation/UiAutomation. It polled Totipo
accessibility status labels, sleeping 20 ms between scans. It skipped password and
EditText nodes and emitted only allowlisted fixed labels and monotonic device-uptime
timestamps. It did not enter credentials, invoke app actions, dump UI trees, capture
screens, or inspect app memory. Totipo's FLAG_SECURE remained intact.

Start is the visible **Creating vault… / Unlocking vault…** publication; completion
is **Vault open / Password did not unlock this vault.** These boundaries include
the real controller/worker/storage/session path and UI delivery. They are not
isolated KDF or method timings. Password typing and time between attempts are excluded.
Button-click callbacks yielded no timing records; all reported timings use status
polls consistently in both phases.

For each transition, the previous poll's start and current detection's end bracket
its observation. Duration intervals below subtract the latest observed start from
the earliest observed finish for the lower endpoint, and the earliest start from
latest finish for the upper endpoint. The reported estimate is the interval midpoint.
These are **polling-resolution intervals, not statistical confidence bounds or
guaranteed internal execution bounds**: accessibility caching/delivery and observer
overhead can add delay. The scale is trustworthy enough to distinguish hundreds
of milliseconds from tens of seconds; sub-millisecond precision is not claimed.
No hard performance threshold was encoded.

## 7. Fresh-create timing

Exactly **one** disposable real-product vault was created by the human.

| Estimate | Polling interval | First-detection timestamp difference |
| --- | --- | --- |
| **862 ms (~0.86 s)** | 799–925 ms | 841 ms |

Creation intentionally derives three times: encode, candidate validation, staged
readback validation, as established by released Java source and M1K. The estimate
is **2.92×** the correct-unlock median, consistent with three KDFs plus small
overhead. No repeated creation, source change, or semantic shortcut was used.

## 8. Correct-unlock timings

Each human-operated measured attempt followed Lock → correct Unlock.

| Attempt | Estimate | Polling interval | First-detection difference |
| --- | --- | --- | --- |
| 1 | 330 ms | 292–368 ms | 321 ms |
| 2 | 293 ms | 257–329 ms | 289 ms |
| 3 | 295.5 ms | 262–329 ms | 287 ms |
| **Median** | **295.5 ms (~0.30 s)** | — | **289 ms** |

All completed with Vault open. Correct unlock performs one unchanged production KDF.

## 9. Wrong-password timings

Two human-operated attempts while locked completed with the fixed authentication
failure status; no session was opened.

| Attempt | Estimate | Polling interval | First-detection difference |
| --- | --- | --- | --- |
| 1 | 306.5 ms | 259–354 ms | 297 ms |
| 2 | 320.5 ms | 282–359 ms | 319 ms |
| **Median** | **313.5 ms (~0.31 s)** | — | **308 ms** |

Wrong passwords also perform one KDF. The estimated median difference is 18 ms
(~6.1%), smaller than the observation intervals and insufficient to infer a stable
branch difference or timing oracle. No failure-path optimization was attempted.

## 10. UI responsiveness

Human explicitly confirmed **vault preserved and all UI checks passed** for both
phases: busy status visible, UI responsive, duplicate Create/Unlock actions disabled
or rejected. The observer independently captured busy and terminal statuses.
No UI redesign or production MainActivity timing change was made.

Post-open local observation was not separately instrumented or timed: no suitable
release internal boundary existed, and M1K profiling was not duplicated. Successful
open timing includes initial observation setup/view delivery; it does not establish
the time to final observation completion.

Read-only environmental samples: USB powered, battery 100%, low_power=0, thermal
status=0. Battery temperature was 29.2 °C before phase A, 29.9 °C afterward, and
30.6 °C after phase B; post-series wakefulness was Awake. These are coarse contextual
samples, not CPU normalization or continuous thermal traces.

## 11. Update/reinstall behavior

Agent reinstalled the **identical signed APK** with the same
`adb install --no-incremental -r` command. Android accepted it without a version
bump, rebuild, uninstall or data clear. Same APK SHA-256, source, dependencies,
versionCode and signer throughout; no artificial product-code change.

Immediately after reinstall, before agent launch/force-stop, ART reported:

```text
[org.totipo.android]
  path: /data/app/~~4Aj_cuZbxHDBZKnnOllzIg==/org.totipo.android-ofjS8UM5WdNRrj95Vm1Uuw==/base.apk
    arm64: [status=speed] [reason=install] [primary-abi]
      [location is /data/app/~~4Aj_cuZbxHDBZKnnOllzIg==/org.totipo.android-ofjS8UM5WdNRrj95Vm1Uuw==/oat/arm64/base.odex]
```

The new installation path had suitable compilation **before first launch**.
Snapshots show speed before and after reinstall; they cannot distinguish artifact
reuse from transient invalidation/recompilation inside the install transaction.
There was no observed post-install slow state or need for manual recovery.

Agent then force-stopped and launched Totipo to establish a clean process. The
observer reported Local vault locked; the human confirmed the same password opened
the existing disposable vault and all UI checks passed. Human reported three
post-reinstall unlocks; **only one had a captured timing pair**, so no three-sample
post-update median is claimed and uncaptured attempts are not timing evidence.

| Captured post-reinstall correct unlock | Estimate | Polling interval | First-detection difference |
| --- | --- | --- | --- |
| First, clean process | **346.5 ms (~0.35 s)** | **306–387 ms** | **337 ms** |

This remains in the same hundreds-of-milliseconds range. Its interval overlaps the
phase-A first unlock's interval. A ~51 ms difference from phase-A median, with a
single post-update sample, cold-process work and coarse polling, does not establish
a persistent slowdown. ART remained speed/reason=install after the series.

The external observer was force-stopped after each series; its Instrumentation
runner's generic `Process crashed` termination message reflects those intentional
collector stops, not an observed Totipo crash. The release remains installed with
the disposable vault; the human ended with Lock. No debug APK restoration or app
data destruction was performed.

## 12. Debug versus release comparison

M1K/M1L ordinary debug: approximately **20.5 s per KDF**; M1K real create approximately
**60.97 s**. M1M actual signed non-debug release: **~0.86 s real create**, **~0.30 s
correct unlock**, **~0.31 s wrong unlock**, **~0.35 s captured post-reinstall unlock**.
These are different timing boundaries and samples; they show the practical scale
change rather than a controlled per-method speedup calculation.

Debug performance is **not representative on this runtime**. M1M agrees with M1L's
natural-install/speed KDF range using the actual product payload and vault path.
M1L separately established non-debug verify → speed causality; M1M does not claim
that the entire debug/release difference is solely package compilation.

README development guidance now states: **Do not evaluate authentication performance
using the ordinary debuggable APK on the tested Pixel 6a runtime.**

## 13. Security/protocol invariance

No production application source/resources, cryptographic behavior, password
semantics, unlock lifecycle, protocol/spec, dependency or build input changed.
Argon2id v1.3 remains **64 MiB / 3 iterations / 4 lanes**, 16-byte salt and 32-byte
output; BC 1.86 and Totipo Java 0.1.5 remain unchanged. Create still performs three
derivations; correct/wrong unlock still perform one.

No credential/key/root logging, benchmark output logging, heap dump, caching, native
Argon2, profile installer, app-triggered compilation, shell calls in app code,
production signing credentials, or device/global compilation-policy changes.
Signing and observer support exist only in ignored inspection state. No pm clear,
uninstall, shell compiler reset or speed command was executed in M1M.

## 14. Distribution-scope limitation

Qualified scope: **direct ADB signed non-debug install/reinstall on this Pixel 6a
runtime**, using the actual unsigned release payload with test-only signing.
One initial release installation and one same-APK reinstall were tested.
This does not qualify Play/store distribution, other devices/runtime policies,
changed-code updates, every Android installation mechanism, long-term artifact
eviction, or production signing. Store/distribution qualification remains separate
if that channel becomes relevant.

## 15. Primary decision

**A. RELEASE INSTALL PATH QUALIFIED — FAST NATURAL ART STATE.**

Both normal installations exposed speed/reason=install before agent launch, real
authentication was fast, and the same vault remained usable after reinstall without
manual compilation. Current direct-install authentication-performance investigation
can close. Repository validation also passed, including human-reported final Nix checks.

## 16. Recommended next milestone

Return to Android product work: **token/TOTP interaction UI**. The existing vault
shell already observes detached token descriptors but lacks token enrollment and
rotating-code interactions. Fast real authentication removes the immediate runtime
performance blocker. Provider tree configuration and biometric/inactivity lifecycle
remain later product milestones; no crypto optimization is indicated by M1M.

## 17. Validation

### Agent

**PASS**, executed directly without Nix:

```sh
./gradlew check :app:assembleDebug :app:assembleRelease
./gradlew --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug :app:assembleRelease
python3 tools/verify-wrapper.py
python3 tools/verify-apk.py app/build/outputs/apk/debug/app-debug.apk --debug-probe
python3 tools/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk --unsigned --no-debug-probe
git diff --check
git status --short
git diff --stat
git diff --cached --stat
```

Normal Gradle build: successful, 90 actionable tasks. Strict offline clean build:
successful, 92 actionable tasks, all executed. Unit tests, lint and dependency/APK
boundaries passed. Existing AGP override/deprecation notices were non-fatal.
Offline log is ignored `.gradle/m1m-inspection/offline-validation.log`.
Wrapper 9.8.0 JAR/distribution pins passed. Standalone debug APK SHA-256:
`fd0f834292c3c8da2390c4edfe272cd6f2b3abd963cb35e3581def9fbc3a0ba3`.
Additional signed verification, binary manifest, payload equivalence, device APK
identity and required timing-outcome checks passed. No product verifier was weakened.

### Human Nix

**PASS, human-reported**: the human stated “both nix commands passed” after final
README/report preparation. No Nix command was executed by the agent. The checks were:

```sh
nix flake check path:.
nix build path:.
```

No build/dependency input changed; dependency cache regeneration and a separate
Gradle-through-Nix rerun are unnecessary. Only README/report changes are tracked.

### Human device

**PASS**. Human connected/unlocked/authorized the device, confirmed fresh Create
Vault, entered credentials privately, created one disposable vault, performed three
correct and two wrong attempts before reinstall, and reported three correct attempts
after reinstall. Human explicitly confirmed vault preservation and all UI checks.
Agent captured all six initial operations and one post-update operation.

### Remote CI

Not run; nothing pushed. Local checks and human reports are not remote CI evidence.

## 18. Final Git state

Branch main and starting HEAD remain unchanged. Final status/stat/diff-check and
cached stat inspected. `git diff --check` passes; cached diff stat is empty.

```text
 M README.md
?? review/M1M_RELEASE_AUTH_PERFORMANCE_QUALIFICATION_REPORT.md
```

Tracked diff stat: **README.md | 6 insertions**. The untracked report is not included
in ordinary `git diff --stat`. All changes remain **unstaged/uncommitted**.
No stage, commit, tag, release or push action was performed.
