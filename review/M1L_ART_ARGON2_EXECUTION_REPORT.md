# M1L — ART package compilation and BC Argon2 execution

**Result: A. ART PACKAGE COMPILATION EXPLAINS MOST OF THE LATENCY.** Strong runtime-compilation effect: on one fixed non-debuggable diagnostic APK, package reset/verify → speed changes median total **5,724.975548 → 269.902791 ms**, **21.211250× faster**, **95.285521% reduction**, with identical synthetic outputs. An ordinary streamed non-debug installation naturally received speed compilation and took **265.846069 ms** (76.988584× faster than M1K). The original debug probe's ~20-second latency is an artificially pessimistic execution configuration on this device. Debuggability is a separate measured factor; the entire historical reduction is not attributed to package compilation alone.

Agent, physical-device and human-reported final Nix validations **PASS**. M1L is complete. No cryptographic implementation, parameters, protocol, release behavior or global runtime setting was changed.

## 1. Starting state

Agent directly ran the required Git commands before editing: branch **main**, HEAD **29a89c87da1e9978a9c8b58e8852649a6bfc27b7**, clean tree. M1K is committed. No AGENTS.md instructions were found. No agents were delegated and no Nix commands were executed.

Initial instructions said jailed USB/ADB access was unavailable. Human first supplied runtime/package help and thermal/power output; after the human stated ADB was available, the agent directly confirmed a reachable physical Pixel 6a and performed the remaining ordinary ADB inspection/measurements.

The installed APK was pulled read-only and matched M1K's measured APK SHA-256 `2b6ed0c090c528d2bfc408ec6d526d23d08adfb9da38cebd8ef618bc43f3ea3f`. Its signing certificate `9e6e35114eefee0edd0fcadc5a7a1d52e9a5ff4525d37f016653c6f95886f186` differed from both the fresh jailed debug key and the human's host key. Initially an update was blocked by this signing boundary. The **human then explicitly reported manually uninstalling the app and requested reinstall**. Agent did not uninstall or clear app data. A fresh output-checking debug baseline was measured after the authorized reinstall, rather than substituting the historical installation for a controlled baseline.

This user action occurred before all controlled series. There were **no uninstalls or data clears between compilation-state measurements**. Each controlled APK remained fixed within its own comparison. All probes were synthetic; the agent did not read/authenticate a real vault.

## 2. M1K baseline

M1K physical total median **20,467.112518 ms**, generate median **20,467.002004 ms**. Correct open ~20.493 s; wrong open ~21.131 s; local VAULT read ~0.328 ms; remaining observation ~1.400 ms. Primary attribution was ARGON2/BC/DEVICE DOMINATED. `dalvik.vm.usejit=false` was observed, but hot-method machine-code state was unknown.

The fresh M1L debug current-state total median is **20,498.048675 ms**, only 0.151151% slower than the historical baseline. This is adequate reproduction, not a demand for identical timing.

## 3. Android runtime properties

Human initial listing and agent direct filtered `getprop` inspection agree. Only present properties are assigned values:

| Property | Observed value |
| --- | --- |
| dalvik.vm.usejit | false |
| dalvik.vm.dex2oat-Xms | 64m |
| dalvik.vm.dex2oat-Xmx | 512m |
| dalvik.vm.dex2oat-cpu-set | 0,1,2,3,4,5,6,7 |
| dalvik.vm.dex2oat-max-image-block-size | 524288 |
| dalvik.vm.dex2oat-minidebuginfo | true |
| dalvik.vm.dex2oat-resolve-startup-strings | true |
| dalvik.vm.dex2oat-threads | 8 |
| dalvik.vm.dex2oat64.enabled | true |
| dalvik.vm.image-dex2oat-Xms | 64m |
| dalvik.vm.image-dex2oat-Xmx | 512m |
| ro.build.version.release | 17 |
| ro.build.version.sdk | 37 |
| ro.product.model | Pixel 6a |
| ro.product.cpu.abilist | arm64-v8a,armeabi-v7a,armeabi |

`dalvik.vm.usejitprofiles` is absent from both filtered listings; no default is assumed. Also observed: release_or_codename=17, release_or_preview_display=17, sdk_full=37.0, abilist32=armeabi-v7a,armeabi, abilist64=arm64-v8a. The Activity independently logged api=37/android=17/abi=arm64-v8a. Compilation CPU-set/thread properties concern the compiler, not BC lane execution.

Additional read-only package defaults: `pm.dexopt.install=speed`, `pm.dexopt.cmdline=speed`, `pm.dexopt.bg-dexopt=speed`; reported install-bulk/secondary/downgraded/install-fast defaults were also speed. **No property was changed.**

## 4. Installed package compilation state

Initial installed M1K package: versionCode=1, versionName=0.0.0-dev, minSdk=26, targetSdk=37, splits=[base], flags **DEBUGGABLE HAS_CODE ALLOW_CLEAR_USER_DATA**. CPU ABI fields in package dump were null; the ART artifact diagnostic separately reported arm64. The flag ALLOW_CLEAR_USER_DATA is not an operation used in this experiment.

Both package dump and package-local ART dump initially showed:

```text
arm64: [status=run-from-apk] [reason=unknown] [primary-abi]
  [location is error]
```

The fresh output-checking debug install had the same state. Requested speed changed it to:

```text
actualCompilerFilter=verify, status=PERFORMED
arm64: [status=verify] [reason=cmdline] [primary-abi]
```

The non-debuggable diagnostic had no DEBUGGABLE package flag. Ordinary streamed install immediately produced:

```text
arm64: [status=speed] [reason=install] [primary-abi]
  [location is .../oat/arm64/base.odex]
```

Package reset then reported verify/reason install; explicit speed compilation reported speed/reason cmdline. Before/after each series, ART dump retained that series' state. No observed vdex/dependency-mismatch fallback occurred in the non-debug speed series.

These are **observed package/artifact states**, not per-method instruction-pointer evidence. No public diagnostic used here proves the entrypoint of every BC hot method. The fixed-APK timing reversal gives direct evidence of a package-state performance effect without that stronger claim.

## 5. Supported compilation controls

Actual Android 17 `cmd package help` was inspected before mutation (human supplied it; agent also captured it). It explicitly lists `speed`, `speed-profile`, `verify`, `-f`, `-v`, `--primary-dex`, `--reset`, and `art dump [PACKAGE_NAME]`. It warns that the actual filter can differ from the request. Reset clears current/reference package runtime profiles and returns primary dex to verification without compilation; external profiles are retained. No app vault/data is cleared by that command.

Selected controls, derived from actual help:

```sh
adb shell cmd package art dump org.totipo.android
adb shell cmd package compile --primary-dex -m speed -f -v org.totipo.android
adb shell cmd package compile --reset org.totipo.android
```

Explicit `--primary-dex` avoids default inclusion of dependency packages; BC is embedded in Totipo's base DEX. No `-a`, include-dependencies, full scope, background job, root/system operation or global configuration was used. Profile-guided compilation/profile export was skipped: it was unnecessary for the controlled full-compilation result; local profiling is not assumed merely because usejitprofiles was absent.

Verbose results:

| Request | Actual filter | Result | dex2oat wall / CPU ms | Artifact bytes before → after |
| --- | --- | --- | --- | --- |
| Debug speed | verify | PERFORMED | 742 / 810 | 0 → 6,939,448 |
| Non-debug reset | verify (ART dump) | Success | Not reported | Not reported |
| Non-debug speed | speed | PERFORMED | 1,765 / 6,090 | 6,939,448 → 22,647,820 |

AOSP's current ART `Dexopter.adjustCompilerFilter` explicitly downgrades debuggable packages through `DexFile.getSafeModeCompilerFilter`, including shell requests. The device's actual downgrade agrees with this mechanism; upstream source alone was not treated as device proof. [AOSP Dexopter.java](https://android.googlesource.com/platform/art/+/main/libartservice/service/java/com/android/server/art/Dexopter.java).

## 6. BC hot-path inspection

Reused matching M1K sources and re-inspected actual BC 1.86 bytecode with `javap -private`. Binary JAR SHA-256 **2af190b300cbb0b35e248ccf5f4a06b6072030aeb3da7a98ec73abe5b4cb371f**, identical to repository verification metadata; inspection-only sources SHA-256 **34a8b7dcb030a08ddd5356d3d5be962f9299ca5275e1460273186f8fdbe44c34**. No BC modification or dependency change.

Actual hot-path names:

- `Argon2BytesGenerator.generateBytes(byte[], byte[])` delegates to its four-argument overload; allocation, initialization, filling, digest and reset are included.
- `fillMemoryBlocks()` loops passes, slices and lanes sequentially and calls `fillSegment(FillBlock, Position)`.
- `fillSegment()` uses `getPseudoRandom`, `getRefLane`, `getRefColumn`, then `FillBlock.fillBlock` or `FillBlock.fillBlockWithXor`.
- `FillBlock.applyBlake()` calls outer `roundFunction`, which repeatedly calls `G(long[], int, int, int, int)`: low-32-bit multiplication, additions, XOR, rotations, long-array loads/stores. Older F/quarterRound names in a source comment are not current 1.86 method names.
- `Block.xor`, both `xorWith` overloads, `copyBlock`, `clear`, `fromBytes`, `toBytes` cover block movement and wiping.
- `initialize`, `fillFirstBlocks`, `hash`, `digest`, `addByteString` use `Blake2bDigest.update`, `doFinal`, `compress`, `G`, `reset`, `initializeInternalState`.

These Java arithmetic/loop methods are plausible compilation beneficiaries. No per-method CPU share was measured, BC was not instrumented, lanes still execute sequentially, and there is still one 64 MiB primary block domain plus overhead.

## 7. Benchmark equivalence

The actual BC invocation remains equivalent to M1K:

| Field | Fixed value |
| --- | --- |
| BC | Existing verified/locked 1.86 runtime dependency |
| Type/version | Argon2id, 2 / 0x13 |
| Memory/iterations/lanes | 65,536 KiB / 3 / 4 |
| Password | UTF-8 of `M1K disposable synthetic password` |
| Salt | Fresh 16 bytes, 00 through 0f in increasing order |
| Output length | 32 bytes |
| Secret/additional | Empty byte arrays |
| Generator | Fresh default `Argon2BytesGenerator` each invocation |
| Pool | No override; actual parameter `getBlockPool()` is null |
| API | Same reflected init and generateBytes(byte[], byte[]) |

Debug-only changes add an equivalence field to Timing and `output_match=1` to production logs. After generate's end timestamp, a temporary 32-byte synthetic output copy is made; original input/key/parameter cleanup precedes total's end timestamp. Hash checking runs **after** that timestamp; the copy/digest are wiped. Total adds only the tiny copy, and generate's boundaries/BC work are unchanged. No output is reused or cached between calls. Mismatch produces fixed `failed=1` and prevents done=1; the derived key and its hash are never logged on device. Optional M1K matrix is untouched and was not run in M1L; it does not claim equivalence to the production reference.

Reference SHA-256 **30eb8bf0a90f2cd624a1d00aa7093e2c8f11968586718195043150ca6ce50bb1** was generated from a temporary copy of the ORIGINAL M1K probe against the checksum-verified BC JAR, hashing only its synthetic result after generate returned. All four reference invocations agreed. New JVM test runs actual production computation and checks it; zero/wrong-length outputs fail. No real credential or vault was involved.

Two separately controlled APKs were necessary because the debug package cannot achieve requested speed. Their signing certificate is the supplied host debug certificate `1f14855aface333061eb0bb545251756a214aa02c31607735f003689eecf9005`:

| Controlled APK | SHA-256 |
| --- | --- |
| Debug diagnostic | af0f3e52505721415d37a0255bb79866cfe687c3a1b134ce985119bb10375c67 |
| Non-debuggable ART diagnostic | 6beed73228cfe48a7109acdd8e3e6f16805f390a2fdbbb7eeaad52897ab3709f |

The non-debug diagnostic was prepared **only in ignored .gradle/m1l-inspection** from the validated debug payload: edit exactly the binary manifest's debuggable boolean (four value bytes true → false), align and re-sign. Every non-manifest/non-signature entry, including all DEX/resources, is byte-identical. No Gradle variant, dependency, source, release manifest or release signing configuration changed. It is release-equivalent for this ART debuggability policy; it is **not a production release APK** and deliberately retains debug-only probes for the experiment.

The non-debug APK's SHA-256 was checked on device before explicit speed compilation and after the final series; both match the same fixed hash above. No APK rebuild/reinstall occurred between its install-speed, reset-verify and command-speed series. The debug APK likewise stayed fixed across its two series. After comparisons finished, the ordinary debug diagnostic was restored in-place, with the same host signer and without clearing data. Final device flag is DEBUGGABLE; final package state is run-from-apk/unknown.

## 8. Baseline/current compilation timing

Each series force-stopped the application, waited 3 s, cleared logcat, launched the exact production probe foreground, ran one warmup and three measured calls, and waited for done=1. No authentication/unlock took place. Measured iteration 0 is excluded from statistics; spread means max minus min. All values are milliseconds.

| State | Warmup total ms | Measured total ms (1, 2, 3) |
| --- | ---: | --- |
| Debug current / run-from-apk | 20088.804168 | 20373.251190, 20498.048675, 20604.466278 |
| Debug requested speed / actual verify | 19851.560109 | 20132.158376, 20245.432586, 20450.695200 |
| Non-debug ordinary install / speed | 291.698567 | 265.846069, 299.084635, 263.717041 |
| Non-debug reset / verify | 5704.037763 | 5756.793744, 5711.238610, 5724.975548 |
| Non-debug requested speed / speed | 288.546631 | 269.902791, 294.969604, 263.817180 |

The initial fresh debug baseline reproduces M1K. The non-debug ordinary-install current state is already speed and fast; its reset series supplies the deliberately uncompiled comparator on that exact same APK.

## 9. Alternative package-compilation timing

**Generate interval** (includes BC allocation/fill/digest/reset):

| State | Median ms | Min ms | Max ms | Spread ms |
| --- | ---: | ---: | ---: | ---: |
| Debug current / run-from-apk | 20497.929738 | 20373.100718 | 20604.329030 | 231.228312 |
| Debug requested speed / actual verify | 20245.320933 | 20132.039154 | 20450.576996 | 318.537842 |
| Non-debug ordinary install / speed | 265.807739 | 263.674438 | 299.044068 | 35.369630 |
| Non-debug reset / verify | 5724.910200 | 5711.151492 | 5756.740847 | 45.589355 |
| Non-debug requested speed / speed | 269.867229 | 263.766723 | 294.921834 | 31.155111 |

**Cleanup-inclusive total interval**:

| State | Median ms | Min ms | Max ms | Spread ms |
| --- | ---: | ---: | ---: | ---: |
| Debug current / run-from-apk | 20498.048675 | 20373.251190 | 20604.466278 | 231.215088 |
| Debug requested speed / actual verify | 20245.432586 | 20132.158376 | 20450.695200 | 318.536824 |
| Non-debug ordinary install / speed | 265.846069 | 263.717041 | 299.084635 | 35.367594 |
| Non-debug reset / verify | 5724.975548 | 5711.238610 | 5756.793744 | 45.555134 |
| Non-debug requested speed / speed | 269.902791 | 263.817180 | 294.969604 | 31.152424 |

**Comparison with M1K total median 20,467.112518 ms**:

| State | Total median ms | Speedup vs M1K | Reduction vs M1K |
| --- | ---: | ---: | ---: | ---: |
| Debug current / run-from-apk | 20498.048675 | 0.998491× | -0.151151% |
| Debug requested speed / actual verify | 20245.432586 | 1.010950× | 1.083103% |
| Non-debug ordinary install / speed | 265.846069 | 76.988584× | 98.701106% |
| Non-debug reset / verify | 5724.975548 | 3.575057× | 72.028416% |
| Non-debug requested speed / speed | 269.902791 | 75.831422× | 98.681285% |

The causal package-only comparison is **non-debug reset/verify → non-debug command/speed**, with the SAME APK/DEX/code/inputs/debuggability: median total decreases **5,455.072757 ms**, speedup **21.211250×**, reduction **95.285521%**. Its generate reduction follows the same pattern. Both have battery 30.4 °C and thermal status 0 in all samples.

Debug current → requested speed (actual verify) improves total by only ~1.23%; it supplies **no evidence of full-AOT benefit on a debuggable package**. Comparing debug current with non-debug speed changes debuggability as well as artifact state; that cross-APK comparison is descriptive, not the package-only causal estimate. The 75.83–76.99× improvements versus M1K must not all be assigned solely to the compiler filter. No timing thresholds were placed in tests/CI.

## 10. Thermal/power observations

| Series | Battery °C range | Thermal status | Foreground samples |
| --- | --- | --- | --- |
| Debug current / run-from-apk | 28.0–28.1 | 0 | Awake |
| Debug requested speed / actual verify | 29.3–29.8 | 0 | Awake |
| Non-debug ordinary install / speed | 29.8–29.8 | 0 | Awake |
| Non-debug reset / verify | 30.4–30.4 | 0 | Awake |
| Non-debug requested speed / speed | 30.4–30.4 | 0 | Awake |

All before/during/after samples: USB powered true, battery status 5/level 100, low_power=0, thermal status 0. Every foreground invocation-start sample and every post-series sample reports Awake, device idle false, light idle false. Power/thermal were read only; no setting changes or frequency locks. Initial pre-experiment human sample was Dozing at 27.7 °C; it is not used as timing evidence.

There were 30-second cooling pauses between long series/transitions. Temperature rose from 28.0 to 30.4 °C across the overall session; a pause did not precisely normalize CPU temperature or clocks. Samples associated with begin logs can lag actual starts, especially in fast series. No CPU frequency trace or per-method CPU profile was collected. Thermal status stayed 0 and the same-APK verify/speed pair's battery readings were identical. These observations and the >21× reversal support material attribution; they do not establish exact CPU normalization. No measured series showed screen-off/Doze disruption.

## 11. Output-equivalence result

**PASS: all 20 physical invocations** (five series × warmup plus three measured calls) logged output_match=1; every series ended done=1, with no failed/unsupported/busy result. The fixed synthetic output is unchanged under every measured artifact state and under both diagnostic debuggability flags. Host/JVM checks also pass. No actual derived key was logged.

## 12. Attribution

**Strong runtime-compilation effect.** On a fixed non-debuggable APK, package-local verify → speed alone makes BC production Argon2 **21.21× faster**, removing **95.29%** of that verification-state cost. The exact same BC 1.86/64 MiB/3/4 computation therefore does not inherently require 20 seconds on this physical device.

The debug probe is materially pessimistic: its actual speed request becomes verify and remains ~20 seconds, while the non-debug verify APK takes ~5.7 seconds even before full compilation. Because DEX is identical, that difference is evidence of debuggability/execution-policy influence, not altered crypto. It does not prove which interpreter, debugging checks or hot-method entrypoints account for the difference. Full package compilation then reduces the non-debug computation to ~0.27 seconds. It is not valid to attribute the whole ~19.7-second historical difference exclusively to package-only AOT; debuggability also changed for that comparison.

Package diagnostics establish requested versus actual filters and usable reported artifact paths; the before/after timing reversal establishes an execution effect. No hot-method machine-code trace/OAT disassembly was obtained. AOSP describes speed as full-method AOT and verify as verification without AOT; that documentation supports filter meaning, not proof that every method's runtime entrypoint was compiled here. [AOSP Configure ART](https://source.android.com/docs/core/runtime/configure). ART also checks dependency compatibility when loading artifacts; no mismatch reason was observed. [AOSP ART Service](https://android.googlesource.com/platform/art/+/android16-qpr2-release/libartservice/service/README.md).

Ordinary `adb install --no-incremental -r` of the non-debug diagnostic received speed/reason install **without any compiler-mode request** and produced the same ~0.27-second class of result as the later explicit speed command. This agrees with the device's observed install=speed property. It establishes one release-equivalent install on this device, not normal behavior of every Android/GrapheneOS build, actual product releases or Play distribution. No profile installer or cloud/local profile was necessary for this observation; Play/profile-guided installation was not tested.

Host sanity check (OpenJDK 17.0.20.1): original-reference measured total median 192.568672 ms (189.331964–195.103503); updated probe median 267.259447 ms (265.405809–305.921191), all output checks passed. Updated probe ran concurrently with Gradle on shared host resources. These are equivalence/sanity observations, not controlled comparative timings or pass/fail thresholds. M1K historical host median was ~189 ms; the compiled physical result is now in the same order of magnitude, without claiming normalized hardware performance.

## 13. Primary conclusion

**A. ART PACKAGE COMPILATION EXPLAINS MOST OF THE LATENCY**

This classification rests on the **21.21× fixed-APK compilation-only effect**, the reproduced ~20-second debuggable baseline, the observed debug filter downgrade, and ~0.27-second release-equivalent normal-install/speed results. The practical latency diagnosis redirects to ART/debug/release installation state before any BC or KDF implementation change. The historical 20-second value includes a distinct debuggability effect; the classification is not a claim that executing a speed shell command on the original debug APK alone removes that latency.

## 14. Security/protocol invariance

No production main/release source, Java API, crypto, protocol, KDF parameters, salt/output lengths, BC version, vault format, authentication semantics, storage dependency or release behavior changed. No password/derived-key cache, native crypto, alternate BC/Argon2, worker lanes, lower production KDF settings or product performance workaround. Temporary public synthetic verification copies are cleared, never reused.

No global runtime/security/property mutation, root, system partition change, SELinux change, GrapheneOS setting change, pm clear or agent uninstall. Package controls affected only org.totipo.android's ART artifacts/profiles. The human's manual uninstall was explicitly reported before reinstall and all controlled series; no data deletion was used within a state comparison. Diagnostic signing/build artifacts stayed ignored and ordinary release verification stayed intact. The app contains no process/package-manager compilation or property-mutation code. No release diagnostics were added.

## 15. Recommended next milestone

Investigate whether **normal signed production release installation/distribution reliably reaches and retains comparable ART compilation** without special user/ADB compilation action. The single non-debug diagnostic normal install already reached speed naturally on this device; repeat that with the real release installation path, verify artifact state and measure actual disposable-vault open, keeping production release free of probes. Check behavior after updates and the relevant distribution path; distinguish this device's speed defaults from Play/profile-guided/default Android policies. No native crypto, BC upgrade, lower KDF settings or app shell compiler manipulation is indicated yet.

No additional per-method instrumentation is needed to establish this milestone's material package-state effect. If the next release investigation needs exact hot-method execution attribution, the minimum extra work is a safe code-execution trace/artifact inspection limited to the synthetic process; do not extract credential heaps or modify runtime settings.

## 16. Validation

### Agent

**PASS:** requested normal Gradle command (2m 11s, 90 actionable tasks, 23 executed). **PASS:** exact forced offline clean validation (51 s, 92 tasks executed). **103 JVM tests passed**, zero failures/errors/skips.

```sh
./gradlew check :app:assembleDebug :app:assembleRelease
./gradlew --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug :app:assembleRelease
python3 tools/verify-wrapper.py
python3 tools/verify-apk.py app/build/outputs/apk/debug/app-debug.apk --debug-probe
python3 tools/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk --unsigned --no-debug-probe
```

Wrapper and debug/release APK checks pass, including post-device checks. Release excludes AuthPerfActivity, AuthPerfBenchmark, TotipoAuthPerf, TotipoVaultTiming, and all new M1L equivalence identifiers. Diagnostic APKs additionally pass APK integrity/probe-presence and apksigner verification; manifest flag and exact DEX/payload equivalence were checked independently. Release verification is not applied to the intentionally probe-containing ignored diagnostic as though it were a shippable release.

| Offline clean output | SHA-256 |
| --- | --- |
| Gradle debug (fresh jailed signer) | fe1c5b8d5c1e15aeee8bedbb5b0d8832903485495606a4f56c39d157d170088f |
| Unsigned release | 1cbc2441408129142c6fd7980d9e86f67fb7893e829e8e7c21570dc4c0a91659 |

Safety/equivalence tests guard actual parameter getters/default pool, fixed synthetic fresh arrays, actual production expected output, no credential/storage dependencies, fresh generator/same generate API, source-set/release boundaries, and no app process/package/global-property controls. No timing thresholds. Dependency declarations, locks, verification metadata and pinned Nix cache files are unchanged.

Inspection/scripts/logs/diagnostic APKs are under ignored `.gradle/m1l-inspection`; the complete report preserves timing/parameter/compiler evidence without relying on committing those artifacts. No Nix commands ran in the agent.

### Human Nix

**PASS — human explicitly reported both final commands succeeded.** No detailed Nix output was supplied; this is a human-reported result. Agent did not execute Nix:

```sh
nix flake check path:.
nix build path:.
```

No Gradle-through-Nix rerun is requested.

### Human device

**PASS — physical evidence collected.** Human supplied initial help/properties/power/thermal information and host key, then reported manual uninstall/requested reinstall. Agent directly executed all controlled series after ADB access was confirmed. All five series completed with four matching outputs, fixed APK identity per comparison, clean process per series, Awake foreground samples, powered/low-power-off/thermal-0 readings and artifact states recorded before/after.

Actual help was retrieved first. Reproduction controls above are supported on this device. After installing a signing-compatible diagnostic **once** and recording its hash, each series uses:

```sh
adb shell am force-stop org.totipo.android
adb shell sleep 3
adb logcat -c
adb shell am start -W -f 0x10008000 \
  -n org.totipo.android/org.totipo.android.debug.AuthPerfActivity \
  --es probe production
adb logcat -v brief -s TotipoAuthPerf:I '*:S'
```

Immediately before each alternative series, use only the selected supported package-local command: reset for the verify series, or `compile --primary-dex -m speed -f -v` for the speed series; collect `art dump` before and after. Current/installed series has no compiler mutation. Keep the same APK installed throughout a comparison. Wait for **done=1**, stop streaming after it, record filtered thermal/battery/power state, and allow cooling between long series. Do not authenticate/unlock during probes. These compiler/force-stop/launch commands **do not delete vault/app data**; **do not use pm clear**. Only fixed-label benchmark/compiler-state and filtered numeric thermal/power output is needed; no credentials/heap contents.

Final device APK was restored to the ordinary host-key debug diagnostic after all comparisons, not during them. No further device action is needed for M1L.

### Remote CI

Not run; nothing pushed. Local normal/offline/Nix reports are not remote CI results.

## 17. Final Git state

Final status/stat/diff-check/cached-stat inspected after report preparation. Branch main and starting HEAD unchanged. `git diff --check` passes; cached diff stat is empty.

```text
 M app/src/debug/java/org/totipo/android/debug/AuthPerfActivity.java
 M app/src/debug/java/org/totipo/android/debug/AuthPerfBenchmark.java
 M app/src/testDebug/java/org/totipo/android/debug/AuthPerfSafetyTest.java
 M tools/verify-apk.py
?? review/M1L_ART_ARGON2_EXECUTION_REPORT.md
```

Tracked diff stat: **4 files changed, 58 insertions(+), 5 deletions(-)**; untracked report is not included in that stat. All investigation changes remain **unstaged/uncommitted**. No stage/commit/tag/release/push commands executed. Human-reported final Nix success is reconciled above. M1L is complete; all investigation changes remain unstaged/uncommitted, and no further work is required for this milestone.
