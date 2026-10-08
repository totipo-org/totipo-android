# M1K — Android authentication performance investigation

Status: **M1K complete** — physical-device attribution, agent validation and human-reported Nix validation passed. **Primary conclusion: A. ARGON2/BC/DEVICE DOMINATED.** All investigation changes remain unstaged/uncommitted.

## 1. Starting state

Before editing, the agent ran `git branch --show-current`, `git rev-parse HEAD`, and `git status --short`.
Branch: `main`. HEAD: `a8b756cdd30ea71149108ce01803c67c2323c0c0` (committed M1J). Starting tree: clean.
No Nix commands were run. No totipo-java checkout was modified.

## 2. M1J latency finding

M1J physical evidence recorded successful Java open at 20,305 ms, remaining initial-observation wait at 4 ms, and coordinator total at 20,311 ms. Two wrong-password Java calls were 20,072 ms each (coordinator totals 20,073 and 20,074 ms). These are historical baseline measurements, not M1K measurements. They localize the delay to the released Java call but do not identify its internal cause.

## 3. Released Java authentication-path inspection

Inspected the exact Maven 0.1.5 source artifacts, already retained locally in `.gradle/m1f-inspection`, and resolved binary artifacts. Inspection extraction and bytecode remain in ignored `.gradle/m1k-inspection`. Core source hash agrees with the released Gradle module's source SHA-256; core binary hash agrees with the actual dependency resolved by Android.

| Artifact | SHA-256 |
| --- | --- |
| totipo-core-0.1.5-sources.jar | `d797c0854db469e8288b787dcbeb3dd3eb8e83062bea5c42b0013082b2cccdc4` |
| totipo-core-0.1.5.jar | `e99609e59db1d9f52f80c446060e63ce7dde17ad69252fa87d397d0cb1577f81` |
| totipo-storage-nio-0.1.5-sources.jar | `bee2a5c9f941360b9c499f1518e3b2201e1e94405a34a8d2709033419e2ed67f` |
| totipo-storage-nio-0.1.5.jar | `9e559ec75fb09af068f876751f32d696c50c40988a16d28419dfa05d1d1c4dae` |
| bcprov-jdk18on-1.86.jar | `2af190b300cbb0b35e248ccf5f4a06b6072030aeb3da7a98ec73abe5b4cb371f` |

The path is:

1. `Totipo.open(TotipoStore, char[])` delegates to `ApplicationVaults.open`. A `StoreAdapter` wraps the coordinated session view; the password is strictly UTF-8 encoded.
2. `VaultLifecycle.open` calls `StoreAdapter.openCanonicalRead`, which calls `store.readVault(87)`. Android serializes that call through `CoordinatedPrivateStore`; NIO checks the directory and exact direct child, reads no-follow through an input stream, consumes at most 88 content bytes, and accepts exactly 87.
3. `BoundedRead.Present` makes a defensive copy and its accessor returns another copy. The adapter wraps the bytes in a `ByteArrayInputStream`; lifecycle `readNBytes(88)` returns the 87-byte record. `VaultBootstrap.parse` verifies length, clones 87 bytes, checks ten-byte magic and version 1. There is no TLV/token parser in this bootstrap path.
4. `PasswordBytes.valid` verifies encoded UTF-8 before unwrap. `VaultUnlocker.unwrap` obtains a 16-byte salt copy and calls `BouncyCastleArgon2idKdf.derive` once.
5. The helper builds Argon2id v0x13, memory 65,536 KiB, iterations 3, lanes 4, 16-byte salt, empty secret and additional data, default block pool. It creates a fresh `Argon2BytesGenerator`, calls `init(parameters)`, then `generateBytes(byte[], byte[32])` once. Resolved core bytecode confirms these constants and this overload.
6. After KDF, JCA `AES/GCM/NoPadding` unwraps the 32-byte root using a 12-byte nonce, 39-byte authenticated header, and 48-byte ciphertext/tag. A bad tag returns `AuthenticationFailed`; success clones the 32-byte root into `VaultUnlockResult`.
7. `ApplicationVaults` copies the recovered root and constructs `ApplicationSession`, which clones it, constructs an HMAC-SHA256 vault fingerprint, creates empty initial state and publisher, and requests asynchronous observation. It returns `OpenResult.Opened`. Temporary root copies and encoded password are wiped; failed open closes the transferred store facade through adapter cleanup.
8. The observer is a single-thread executor. `requestRefresh()` queues discovery/token read/parsing/graph work; it does not synchronously enumerate objects before open returns. That worker can begin before open returns and contend with the calling thread. Android's coordinator separately awaits a finished observation before exposing OPEN.

**Invocation counts:** valid-format successful open: one Argon2 derivation; valid-format wrong password: one identical-parameter derivation. Bad format/absent/invalid password input can reject before KDF, so those outcomes are excluded from password comparisons. Both correct and wrong passwords intentionally derive before GCM authentication. There is no cached password, derived key, reopen token or retry derivation in open.

**Create differs:** `ApplicationVaults.create` performs three derivations on a successful fresh create: `VaultBootstrapWriter.encode`, `valid(candidate,...)`, then `validStage(...)` authenticating actual staged readback. It is therefore inappropriate to expect create to equal one open call. No changes were made to this behavior.

**Input/output and surrounding allocations:** password input is strict UTF-8, at most 1,024 bytes, actual user size unobserved/unlogged. Encoding uses a 1,024-byte buffer and exact-size output; validity checking uses a 1,024-character buffer. The helper allocates the 32-byte output, builder, parameters, generator and empty secret/additional arrays. Builder and parameters clone salt/secret/additional and clear their owned arrays. Bootstrap accessor copies are 16/12/39/48 bytes. JCA key/nonce specifications and root result/session copies are fixed-size. Helper clears builder/parameters, unlocker clears salt, derived key and plaintext root, and application clears encoded password and temporary root copies. These surrounding allocations are bounded independently of token count. There is no second 64 MiB allocation in Totipo's wrapper code; BC's model is below.

**Providers:** the KDF is BC lightweight API, with no JCA BC registration or provider object. AES-GCM and HMAC/SHA use operation-local JCA lookups; Totipo does not pin/log provider identity. `VaultLifecycle` also constructs writer/unlocker/entropy helpers, but constructing them does not derive. No provider IDs are captured.

## 4. BC Argon2 implementation inspection

Inspected the actual resolved BC 1.86 generator bytecode with `javap -private -c`, plus the matching Maven 1.86 sources JAR downloaded for inspection only. No build dependency or verification metadata was changed. The source and bytecode agree on `init`, `allocateMemory`, `generateBytes`, `reset`, and the nested pass/slice/lane loops.

`init` computes segment and lane sizes and creates the default `FixedBlockPool(memoryBlocks)`. At production settings there are 65,536 1-KiB primary blocks: 4 lanes of 16,384 blocks, 4,096 blocks per segment. Pool initialization allocates an `ArrayList` backing reference array, not the 64 MiB block payload.

`generateBytes` allocates a 1,024-byte temporary block and `Block[65536]`, obtains all primary blocks from the initially empty pool, initializes using Blake2b, fills memory, hashes the final block, and resets. Each primary `Block` owns a `long[128]`: **64 MiB of payload plus approximately 131,072 primary Block/array objects**, reference arrays and runtime-dependent object headers/alignment. There are four additional scratch blocks in `FillBlock` and small hash/prehash buffers. This is not four separate 64 MiB memories.

`reset` zeroes and returns blocks to the fixed pool, then nulls the primary reference array. The operation-local generator still retains the pool of wiped blocks until unreachable and collected. Each subsequent Totipo open creates a new generator/pool. The synthetic benchmark deliberately does likewise; it does not reuse the pool or force GC. The default pool uses synchronized list access for each block allocate/deallocate, but **does not launch threads**.

`fillMemoryBlocks` loops passes, four slices, then lanes, and calls `fillSegment` synchronously for each lane. Four lanes are algorithmic partitions, not four parallel CPU threads in this implementation. BC Argon2 and its block arithmetic are Java code. ART/D8 versus HotSpot/JVM execution, device CPU and memory management can affect this code materially; source inspection alone cannot quantify any of those effects. No architecture-equivalence claim or upgrade recommendation is justified yet.

## 5. Debug instrumentation

All instrumentation is in `src/debug`; safety tests are in `src/testDebug`. Production `main`/`release` sources, Gradle files, lockfiles and BC pin are unchanged.

- `AuthPerfBenchmark`: non-secret fixed ASCII password, fresh synthetic salt bytes 0..15, 32-byte output, and exactly the released BC type/version/parameters/empty secret/additional/default pool. It resolves BC public constructors/methods once before timing because BC is a runtime-only transitive dependency. One reflective call invokes the exact `generateBytes(byte[], byte[])` method; there is no reflection inside BC's work loop. Per-invocation setup includes synthetic inputs, builder/parameter construction, generator construction and init. Generate includes BC primary allocation, derivation and block reset/wipe. Total additionally includes clearing the synthetic inputs/output/parameters. These differences from Totipo's small-array ownership are explicitly accounted for; no materially different KDF path is used.
- `AuthPerfActivity`: fixed ADB actions `production`, `read`, `matrix`, `create`; default is production. One warmup (iteration 0) and three measured production calls. The Activity handles repeated ADB intents, displays only its non-secret screen over the keyguard, and keeps the display on only during the probe; it clears those display requests afterward. A process-local running flag prevents overlapping diagnostic activities. Do not unlock in the normal UI during a probe. It runs on a dedicated worker, without modifying MainActivity.
- Optional matrix: four additional calls, 16/3/4, 32/3/4, 64/1/4 and 64/3/1, compared with measured production 64/3/4. Those lower settings are diagnostic configurations, not protocol candidates. No arbitrary parameters are accepted.
- `DebugVaultTiming.readVault`: five independent owner leases, the same coordinated private store, and its `observeVault()` / `readVault(87)` boundary; records store-open, bounded read and close-inclusive total. It neither authenticates nor examines/logs returned bytes. Fixed numeric `present` indicates whether a real VAULT was read.
- Existing actual product open/create timing now uses `SystemClock.elapsedRealtimeNanos()`, records Java result categories as fixed correct/wrong/create labels, retains store-open/coordinator total and remaining observation wait. There is no credential input through ADB.
- `DebugDisposableCreation`: creates once under a fresh exclusively owned temporary cache directory, uses a synthetic password, closes the coordinator and deletes only that directory. It does not clear/create/overwrite the real vault.

Logs contain fixed labels and numeric configuration/timing/status only; the sole environment line contains standard model/API/version/ABI properties. No passwords, salts, bytes, keys, fingerprint, provider IDs, token metadata, paths, object IDs or exception text are logged. Release verification rejects diagnostic class names and both `TotipoAuthPerf` and `TotipoVaultTiming` strings.

## 6. Device environment

Read directly with standard ADB `getprop` (non-sensitive properties only): Pixel 6a; Android 17; API 37; ABI list arm64-v8a, armeabi-v7a, armeabi. The running probe reported arm64-v8a as its primary ABI. No device identifier is retained in this report.

The first `adb install -r` was rejected with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`: the sandbox-generated debug key differed from the installed M1J app. The agent did not uninstall or clear data. The human then reported uninstalling M1J; the agent retried and installation succeeded. The historical vault is no longer available. The human then created one fresh real-app vault for this comparison; the agent retrieved the resulting create, two correct opens, two wrong opens and five successful bounded VAULT reads directly through filtered ADB logcat. No repeated app-data clears or repeated real-app creations were requested.

Additional non-sensitive platform observations: `dalvik.vm.usejit=false`; global `low_power=0`; powered, battery 100%. These settings were observed, not changed. The property value is relevant to a later ART compilation investigation, but no per-method interpreter/AOT/JIT execution state has been measured.

## 7. VAULT read timings

Five independent reads of the actual fresh canonical local VAULT used by product unlock all returned `present=1`. Each used the real owner lease, coordinated NIO domain, `readVault(87)`, and normal closure, without authentication.

| Iteration | Store open ms | Bounded read ms | Close-inclusive total ms |
| --- | ---: | ---: | ---: |
| 1 | 0.087931 | 0.694458 | 1.136434 |
| 2 | 0.061727 | 0.273641 | 1.658081 |
| 3 | 0.064249 | 0.331665 | 0.600179 |
| 4 | 0.056071 | 0.328043 | 0.539307 |
| 5 | 0.054809 | 0.257853 | 0.475057 |
| Median | **0.061727** | **0.328043** | **0.600179** |

The first read was 0.694 ms; later reads were 0.258–0.332 ms. Even the largest close-inclusive total was below 2 ms. The measured median read contributes **0.001601%** of correct Java-open time and **0.001552%** of wrong Java-open time. These are small warm local-storage measurements, not a cold-cache or arbitrary-directory-size guarantee; they decisively exclude storage as the source of this ~20-second baseline.

## 8. Synthetic production Argon2 timings

The first device series was interrupted and is **excluded from the final median**: warmup total 26,295.773084 ms (setup 0.540283 ms, generate 26,295.047987 ms), first measured total 20,094.187225 ms (setup 0.096883 ms, generate 20,094.074269 ms). During the second measured invocation the device entered Dozing; wall time grew far beyond the ~20-second baseline while the worker remained CPU-active. The agent stopped that synthetic-only process instead of treating the uncontrolled series as reliable. No app vault/session existed at that point.

A second preliminary series resumed automatically when the device was unlocked. Its warmup total was 19,957.045054 ms and its first measured total 34,719.346534 ms; MainActivity was inadvertently foregrounded during an ADB reuse of the old task, then the screen entered Dozing again. That series was also stopped and excluded. Those discarded attempts are documented so they cannot be mistaken for extra reliable repetitions.

The final debug Activity handles new ADB intents, keeps its own diagnostic screen visible even over the keyguard, requests the screen on (API 27+), and uses `FLAG_KEEP_SCREEN_ON` during each probe. It clears these display requests in its UI-thread finally callback before admitting another probe. It neither dismisses the keyguard nor accepts real credentials; only the non-secret diagnostic screen is displayed. Safety tests require the display flags' setup/cleanup and intent handling. No production source, system power setting or ART property was changed.

The final controlled series ran on the physical Pixel 6a after a process restart, with the probe in the foreground and all four periodic power samples reporting Awake:

| Device invocation | Setup ms | Generate ms | Total ms |
| --- | ---: | ---: | ---: |
| Warmup 0 | 0.606812 | 19,929.762705 | 19,930.529103 |
| Measured 1 | 0.092855 | 20,168.166880 | 20,168.276174 |
| Measured 2 | 0.094279 | 20,467.002004 | 20,467.112518 |
| Measured 3 | 0.105835 | 21,056.054454 | 21,056.177786 |

**Physical measured median total: 20,467.112518 ms; median generate: 20,467.002004 ms; median setup: 0.094279 ms.** Generate accounts for approximately 99.9995% of the median invocation. Setup is negligible. Measured totals vary by 4.4% from minimum to maximum; no subsequent-call speedup is evident. Raw fixed-label output is retained in ignored `.gradle/m1k-inspection/device-production-controlled.txt`; numeric platform samples are retained separately there.

Optional host comparison executed by the agent using this exact synthetic probe and resolved BC 1.86, OpenJDK 17.0.20.1, Linux amd64, reported CPU Intel Core i5-1135G7 at 2.40 GHz. This sandbox shares host resources; CPU clock/load was not controlled. The temporary driver/output are in ignored `.gradle/m1k-inspection`.

| Host invocation | Setup ms | Generate ms | Total ms |
| --- | ---: | ---: | ---: |
| Warmup 0 | 4.260830 | 237.347543 | 241.934832 |
| Measured 1 | 0.068514 | 199.388187 | 199.493541 |
| Measured 2 | 0.190205 | 188.692767 | 188.910446 |
| Measured 3 | 0.052056 | 183.619981 | 183.708234 |

Physical/host measured total-median ratio is approximately **108.34×** under these different devices/runtimes/configurations; this is descriptive, not an architecture-equivalence or normal-Android claim.

Host measured median total: **188.910446 ms**; median generate: **188.692767 ms**; median setup: **0.068514 ms**. Do not substitute the desktop median for Android KDF time or divide it by M1J open to attribute Android latency.

## 9. Parameter-scaling observations

After the controlled production series and a short cooling pause, the agent ran the four-call synthetic matrix in the same process, using the same bytes/BC overload/version/output length. The probe screen was foreground and Awake at launch.

| Synthetic parameters (MiB / passes / lanes) | Setup ms | Generate ms | Total ms |
| --- | ---: | ---: | ---: |
| 16 / 3 / 4 | 0.085693 | 4,862.256716 | 4,862.358441 |
| 32 / 3 / 4 | 0.110067 | 9,926.039067 | 9,926.167566 |
| 64 / 1 / 4 | 0.117391 | 7,043.438725 | 7,043.573246 |
| 64 / 3 / 1 | 0.118123 | 20,335.393321 | 20,335.531585 |
| Production 64 / 3 / 4 (measured median) | 0.094279 | 20,467.002004 | 20,467.112518 |

Doubling memory from 16 to 32 MiB approximately doubles time (2.04×); 32 to production 64 MiB is 2.06×. Production three passes take 2.91× the one-pass call. One versus four lanes differs by about 0.64%, less than production-series variation; there is no measured four-thread speedup. This agrees with the inspected sequential lane loop. Those one-sample ratios demonstrate approximate memory/pass scaling, not statistically precise performance estimates. No lower setting is interpreted as acceptable protocol behavior; production parameters remain unchanged.

## 10. Totipo.open timings

The human exercised MainActivity with one fresh real-app creation, then two correct unlocks separated by Lock and two wrong-password attempts while locked. The agent retrieved fixed-label output directly; no user password or VAULT cryptographic material was captured. The same process/BC 1.86 APK was used for synthetic and real-path tests. The ordinary MainActivity display/credential/lifecycle behavior remained unchanged; the human was instructed to keep the screen awake during creation and wait for each completed operation.

| Actual product operation | Java call ms (`TotipoAuthPerf`) | Domain setup ms | Remaining observation wait ms | Coordinator total ms |
| --- | ---: | ---: | ---: | ---: |
| Fresh create, once | 60,969.581614 | 0.136312 | 7.536377 | 60,977.950510 |
| Correct open 1 | 20,265.230235 | 0.177206 | 1.497680 | 20,267.869272 |
| Correct open 2 | 20,720.629689 | 0.099568 | 1.301514 | 20,722.804697 |
| Wrong open 1 | 20,367.680552 | 0.137736 | Not applicable | 20,368.373260 |
| Wrong open 2 | 21,894.270233 | 0.080241 | Not applicable | 21,894.804332 |

Correct-open median: **20,492.929962 ms**. Wrong-open median: **21,130.975393 ms**. Correct coordinator median: **20,495.336984 ms**; wrong coordinator median: **21,131.588796 ms**. Median domain setup was 0.138387 ms for correct opens and 0.108989 ms for wrong opens.

The retained M1J-style `java_call` line is emitted just after the result-category log and therefore includes a small logging increment (~0.09–0.12 ms here). This report consistently uses the earlier `TotipoAuthPerf` Java-call measurement as its open/create denominator.

Fresh create took **60.970 seconds**, approximately **2.979×** the standalone production BC median and within 0.70% of three BC derivations (61.401 seconds). Source inspection established exactly three intended create derivations (encode, candidate validation, actual staged readback validation), rather than an unexpected open retry. The optional separate cache-directory create probe was not run, since one real fresh creation was already measured.

Both successful and failed opens remain around 20 seconds after the synthetic series and repeated real attempts. Wrong-open median is 3.11% greater than correct-open median, but the two wrong samples differ by 7.50% and the production BC samples by 4.40%. With only two opens per branch and no CPU-frequency control during human UI tests, this does not establish a stable success/failure overhead difference or timing oracle. Source proves equivalent KDF parameters/count for both. No failure-path optimization was performed.

## 11. Initial observation timings

The two successful opens had **1.497680 ms** and **1.301514 ms** of remaining initial-observation wait: median **1.399597 ms**, approximately **0.006829%** of the correct coordinator median (and 0.006830% of Java-open median). Fresh creation's remaining wait was 7.536377 ms. Wrong passwords create no session and have no initial observation.

This measures remaining post-open wait, not the full observation duration: the released session schedules asynchronous observation before open returns, so any concurrent work that finishes earlier overlaps open time. Do not add a presumed full observation duration to the measured Java interval. The test vault was freshly created, with no token population exercised; this result is scoped to the initial empty local vault workload.

## 12. CPU/memory/warmup observations

During the excluded first series, one `top -H` sample showed the `totipo-auth-perf` worker at 96.1% CPU and other displayed app threads at 0%. A later Dozing sample showed the same worker at 103%. These are consistent with one busy KDF calling thread; they are not precise physical-core scheduling traces. Source inspection independently establishes sequential lane processing.

During the first warmup, `dumpsys meminfo` reported process PSS 154,941 KiB, RSS 278,844 KiB, Dalvik heap allocation 69,686 KiB. A later sample reported PSS 148,960 KiB, RSS 277,032 KiB, Dalvik allocation 69,598 KiB. Those are snapshots, not peaks; no pre-KDF baseline was captured in this interrupted series. The approximately 68 MiB Dalvik allocation is consistent with one 64 MiB payload plus BC/runtime overhead, rather than four simultaneous 64 MiB buffers.

Filtered process GC messages included one young collection freeing 67 MiB in 120.655 ms (pauses 0.186/1.877 ms), another young collection taking 317.688 ms (pauses 0.335/5.011 ms), and an explicit collection taking 269.405 ms (pauses 0.442/4.022 ms). This demonstrates allocation/collection during the series; individual GC wall times are not summed into KDF latency and do not explain the observed tens of seconds. No heap contents were collected. The reliable controlled-series baseline/during/after samples follow below; the preliminary figures above are not used for final medians.

The final controlled series included numeric before/during/after platform samples. Baseline was MainActivity before the synthetic KDF, in the same freshly restarted process; differences in native/graphics Activity state make total-process deltas less precise than the Dalvik allocation comparison.

| Sample | PSS KiB | RSS KiB | Dalvik allocated KiB | Dalvik PSS KiB | Benchmark thread CPU | Awake |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| Before KDF | 80,403 | 207,416 | 1,695 | 5,732 | Not sampled | Not sampled at baseline |
| During, sampler +5.5 s | 154,724 | 282,008 | 69,698 | 70,436 | 103% | Yes |
| During, +25.2 s | 222,440 | 349,500 | 69,694 | 138,272 | 107% | Yes |
| During, +45.9 s | 153,320 | 280,380 | 69,694 | 70,192 | 103% | Yes |
| During, +65.6 s | 222,653 | 349,712 | 69,694 | 138,284 | 107% | Yes |
| After series / automatic GC | 144,174 | 271,248 | 1,590 | 70,324 | Not sampled | Not sampled after |

`top` percentages are approximate samples, consistent with one busy CPU thread, not a count of physical cores or a precise utilization trace. Source separately proves no threaded lane parallelism. During this controlled series, live allocated Dalvik memory increased by about 66.4 MiB over baseline, consistent with the 64 MiB payload plus object overhead. PSS/RSS temporarily increased between calls while the new invocation's memory and previous committed/uncollected heap pages coexisted. Those snapshots cannot count exact live block pools; they are not evidence that Totipo derives twice per open. After collection, reported allocation returned to 1.6 MiB while committed/resident heap pages remained higher. These are sampled values, not true peaks.

Controlled-series GC messages included collections freeing 66 MiB. Maximum logged collection wall time was 135.649 ms; logged stop-the-world pauses were at most 2.489 ms for an individual pause. Some collection work is concurrent, so it cannot simply be summed/subtracted from KDF wall time. The available logs do not indicate GC costs comparable to a 20-second derivation. No heap contents were collected and no GC was forced by the probe.

At series completion the battery reported 31.9 °C, powered at 100%, and platform Thermal Status was 0. CPU temperature and frequency were not measured, so the modest rising invocation times cannot be assigned to heat or clock changes. The controlled warmup total was 19.931 seconds versus a subsequent median of 20.467 seconds: warmup did not materially reduce device KDF time. Host warmup effects are separate.

## 13. Attribution

The matched physical comparison, with both interrupted screen-Dozing series excluded, is:

| Quantity | Physical measurement / calculation |
| --- | ---: |
| BC production total median (three measured invocations) | **20,467.112518 ms** |
| BC generate median, including block allocation and reset | **20,467.002004 ms** |
| BC setup median | **0.094279 ms** |
| Correct Java-open median (two invocations) | **20,492.929962 ms** |
| Wrong Java-open median (two invocations) | **21,130.975393 ms** |
| Standalone BC / correct open | **99.874018%** |
| Standalone BC / wrong open | **96.858342%** |
| Bounded VAULT-read median | **0.328043 ms** |
| VAULT read / correct open | **0.001601%** |
| VAULT read / wrong open | **0.001552%** |
| Remaining observation-wait median | **1.399597 ms** |
| Remaining observation / correct coordinator total | **0.006829%** |
| Correct coordinator median minus Java-open median | **2.407022 ms** |
| Correct-open − BC − read (residual estimate) | **25.489401 ms** |
| Wrong-open − BC − read (residual estimate) | **663.534832 ms** |

**Localization:** the standalone benchmark has no Totipo application/session/parser or product credential/storage work, yet reproduces approximately the whole Java-open interval. Its setup is negligible; the expense is inside the actual BC `generateBytes(byte[], byte[])` operation on this device/runtime. Source proves one derivation and one primary block allocation per open/KDF invocation. Correct/wrong paths use the same parameters; fresh creation's three intended KDF calls explain its roughly 61-second duration. Fixed-size surrounding UTF-8/framing/AES-GCM/root/fingerprint/session work and local NIO/remaining observation cannot account for the baseline 20 seconds.

**Post-KDF localization limit:** released public boundaries do not expose a direct AES-GCM/parse/root/session phase timer. Totipo and protocol code were not modified and no second VAULT decoder was introduced. The residual estimates also include pre-KDF encoding/framing, cleanup, reflective synthetic-wrapper differences and differing run conditions. They are differences between independent medians, **not measured post-KDF intervals** or precise per-open upper bounds. The ~25 ms correct residual and ~664 ms wrong residual fall within observed KDF/open variability; they cannot be assigned specifically to authentication/decryption. This is adequate to identify the dominant operation, not to optimize its microcomponents.

**BC versus allocation/work:** BC's generation interval includes initial block allocation, fill/hash work and final clear/pool return. Those components are not separately timed internally. The fixed-memory one-pass call is 7.044 seconds versus three-pass median 20.467 seconds, despite the same one-time 64 MiB allocation; this is strong evidence that extra pass work, rather than repeated Totipo derivation or allocation per pass, drives scaling. Source places `allocateMemory()` once before the pass loop. Heap/GC observations are consistent with the expected payload and collection between fresh generators, not an unexplained multiplication by lanes. Sampling cannot establish precise allocation-versus-arithmetic CPU shares.

**Runtime scope:** the observed device property `dalvik.vm.usejit=false`, single-threaded BC lane implementation and host/device gap merit follow-up. M1K did not establish the actual per-method ART interpreter/AOT/JIT state or isolate processor speed from runtime/compiler effects. The conclusion is about BC's exact invocation on the measured physical-device configuration, not an assertion that all Android devices necessarily take 20 seconds.

## 14. Primary conclusion

**A. ARGON2/BC/DEVICE DOMINATED**

Production-parameter standalone BC Argon2 takes a median **20.467 seconds**, compared with **20.493 seconds** for correct open and **21.131 seconds** for wrong open. It explains approximately **99.87%** and **96.86%** of those independent medians within the observed run-to-run variation. Local VAULT read is **0.328 ms** and remaining observation wait **1.400 ms**. The bottleneck is the BC generation path on this device/runtime configuration; surrounding Totipo Java and Android storage/observation are not dominant.

## 15. Security/protocol invariance

No protocol KDF memory/iterations/lanes, salt format, derived-key size, VAULT format, Java public API, BC 1.86 pin, credential semantics, password/key caching, reopen token, unlock lifecycle or Android release behavior changed. No optimization was implemented. Product source and dependency declarations remain unchanged. Lower diagnostic matrix parameters never affect Totipo or protocol tests. No totipo-java modifications, stages, commits, tags, releases or pushes occurred.

## 16. Recommended next milestone

Run a narrow **totipo-java/BC performance investigation preserving exact Argon2 parameters and output semantics**. Its smallest first experiment should establish this device's ART compilation configuration for the BC hot methods, given the measured `dalvik.vm.usejit=false`, then repeat the same synthetic production invocation under a controlled standard-runtime/compilation configuration. Validate identical derived output with public synthetic test vectors, never user credentials. Compare the existing configuration and controlled configuration, without repinning BC or lowering parameters.

This runtime/compilation check should precede choosing another Argon2 implementation: M1K identifies BC/device execution as dominant but does not establish whether BC arithmetic, ART interpretation/compilation, CPU speed, or object/pool overhead is the most useful optimization target. If the controlled runtime still shows unacceptable performance, compare BC implementation options, lane parallelism or an audited alternative using exact parameters and output-equivalence tests. A BC-version experiment may be considered in that later milestone after inspecting relevant changes; no newer-version performance claim was investigated here.

No runtime setting, KDF parameter, dependency, caching policy or implementation optimization was changed in M1K. No Android/NIO optimization or second VAULT decoder is indicated by these measurements.

## 17. Validation

### Agent

Normal `./gradlew check :app:assembleDebug :app:assembleRelease`: PASS after fixing a debug cleanup API compatibility error detected by lint. Safety tests check exact production parameter values via actual BC getters without running the KDF, synthetic inputs, fixed log sources, absence of credential/storage dependencies in the benchmark, and absence of diagnostic dependencies from main/release.

Forced offline clean validation: **PASS**, 92 tasks executed in 48 seconds, using the exact requested `--offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug :app:assembleRelease` invocation. The latest normal validation completed in 27 seconds, after completing the debug-only display/intent handling. All 101 debug JVM tests passed with zero failures/errors/skips, including the four new probe safety tests; release test tasks have no enabled unit test executions in this repository.

Post-clean wrapper and debug/release APK checks: **PASS**. Release SHA-256 is unchanged between the normal and forced offline runs; diagnostic code is absent.

| Final APK | SHA-256 |
| --- | --- |
| Debug | `f2b1d6d1db5844558d9399055aba73c1c9f72268dfe25e9e3eb710a2e0a75718` |
| Unsigned release | `600660af2bfcda0e1fb4856aab0486a1cf3d1a01b740dc76cc664976863ff3d3` |

The device runs used the normal-build debug APK SHA-256 `2b6ed0c090c528d2bfc408ec6d526d23d08adfb9da38cebd8ef618bc43f3ea3f`. Its DEX entries are byte-identical to the final forced-offline debug APK; container/signing differences account for distinct APK hashes.

The agent ran all requested checks:

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

Final diff check passed; cached diff stat is empty.

No timing thresholds, profiling dependency, dependency upgrade or cache/lockfile edits were introduced. Inspection source downloads and ordinary Gradle dependency resolution are not changes to the repository's pinned Nix download cache. APK verifier requires performance classes in debug and rejects performance classes/log strings in release, including retained M1J timing.

### Human Nix

**PASS — the human reported both final commands succeeded.** The agent did not run Nix or request Gradle-through-Nix duplication. This is a human-reported validation result; no detailed Nix output was supplied.

```sh
nix flake check path:.
nix build path:.
```

### Human device

**PASS — all required physical evidence captured.** The agent measured one warmup plus three controlled production BC invocations, the four-call synthetic matrix, and CPU/memory/GC/system-property observations. The human completed one fresh app Create, two correct Unlocks and two wrong-password attempts, then launched the read probe and reported `done=1`. The agent retrieved all corresponding fixed-label logs directly through ADB, including five `present=1` coordinated reads. The real password was entered only in MainActivity.

The actual entrypoint is `org.totipo.android/org.totipo.android.debug.AuthPerfActivity`. For reproducibility, these ordinary-terminal commands use an explicit new/cleared task flag (`0x10008000`) to keep the intended probe Activity in the foreground. Run probes sequentially and wait for each `done=1`; do not authenticate in MainActivity while a synthetic probe is running. The diagnostic screen manages its own display requests and exposes no secrets over the keyguard.

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop org.totipo.android
adb logcat -c
adb logcat -v brief -s TotipoAuthPerf:I TotipoVaultTiming:I '*:S'
```

In a second terminal:

```sh
adb shell am start -W -f 0x10008000 -n org.totipo.android/org.totipo.android.debug.AuthPerfActivity --es probe production
```

Optional four-call synthetic matrix after cooling and completion:

```sh
adb shell am start -W -f 0x10008000 -n org.totipo.android/org.totipo.android.debug.AuthPerfActivity --es probe matrix
```

For the real vault path:

```sh
adb shell am start -W -f 0x10008000 -n org.totipo.android/.MainActivity
```

Use an existing vault when available. Only for fresh disposable app data, Create once and wait for OPEN; do not clear/recreate an existing vault. Keep MainActivity's display awake during Create (which performs three KDF calls). Lock → correct Unlock twice, then Lock → two wrong-password attempts. Wait for each result and leave the app locked. No credential is put in terminal commands.

Then measure the real VAULT read:

```sh
adb shell am start -W -f 0x10008000 -n org.totipo.android/org.totipo.android.debug.AuthPerfActivity --es probe read
```

Each diagnostic action emits model/API/version/ABI. The optional cache-directory `--es probe create` action was not needed or run in this investigation because fresh real-app creation was measured once. Only filtered timing output is needed from the human; no Git/Gradle commands, screenshots, heap contents or credentials were requested.

The initial signature mismatch was resolved by the human's reported uninstall. The agent never uninstalled/cleared the app; future updates should retain a matching debug key to preserve a real vault.

### Remote CI

Not run; nothing pushed. Local normal/offline checks are not represented as remote CI results.

## 18. Final Git state

Final commands were run after source/report preparation: `git status --short`, `git diff --stat`, `git diff --check`, and `git diff --cached --stat`. Diff check is clean; cached diff stat is empty. Starting HEAD and branch are unchanged.

```text
 M app/src/debug/AndroidManifest.xml
 M app/src/debug/java/org/totipo/android/reconcile/DebugVaultTiming.java
 M tools/verify-apk.py
?? app/src/debug/java/org/totipo/android/DebugDisposableCreation.java
?? app/src/debug/java/org/totipo/android/debug/AuthPerfActivity.java
?? app/src/debug/java/org/totipo/android/debug/AuthPerfBenchmark.java
?? app/src/testDebug/java/org/totipo/android/debug/AuthPerfSafetyTest.java
?? review/M1K_ANDROID_AUTH_PERFORMANCE_REPORT.md
```

Tracked diff stat: 3 files changed, 47 insertions(+), 11 deletions(-). Untracked new files above are not included in `git diff --stat`. All changes remain unstaged and uncommitted. No stage/commit/tag/release/push commands were executed. Physical evidence, attribution and human-reported final Nix success are reconciled above. M1K is complete. Changes remain unstaged/uncommitted; no further work is required for this investigation milestone.
