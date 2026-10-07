# Totipo Java released dependency

Android directly consumes `org.totipo:totipo-core:0.1.4` from Maven Central.
Core requires Java 17 and adds `org.bouncycastle:bcprov-jdk18on:1.86` at runtime.
The M1C qualification harness consumes `org.totipo:totipo-storage-nio:0.1.4`
through `debugImplementation`
only for physical-device qualification in disposable no-backup app-private storage.
Release compile/runtime and the release APK exclude NIO. This is not a production
architecture commitment; core never operates against SAF/provider transport.

Upstream: https://github.com/totipo-org/totipo-java, source tag `v0.1.4`.
Release commit: `44332d459e0cbec74e93ea5fb77280596c505732`.
Target: Totipo Vault Format v1/r18, spec commit
`4623a7e1718e23504903096c92332597057bd8f0` (committed revision, not an r18 release tag).

Maven Central is the Android consumer boundary. No local source checkout,
composite substitution, file JAR, Maven Local, snapshot, copied protocol source
or conformance corpus supplies Totipo. The provider and application responsibilities
remain separate from Java's operation-scoped qualification. Consuming core does
not establish Android platform, storage, runtime or application conformance.

The direct version is pinned in `app/build.gradle.kts`. Strict dependency locks
and SHA-256 verification metadata pin the resolved graph; `verifyMavenBoundary`,
wired into `check`, checks debug/release compile/runtime external modules, the
direct core edge and core's BC runtime edge, rejects project/file artifacts and
permits exactly NIO 0.1.4 as a direct debug-only external module and requires
its external NIO → core 0.1.4 edge, including the requested version. Mixed Totipo
requests are rejected even if Gradle would resolve them to 0.1.4.
`verifyReleaseApkBoundary`, also in `check`, asserts that release DEX contains no
NIO or debug probe classes. Repository declarations are centralized; Totipo is excluded
from Google Maven so all Totipo resolution uses Central.

Nix `package-deps.json` separately pins package downloads. The human-regenerated
0.1.4 cache was reviewed against actual Central bytes, Gradle verification metadata
and publisher POM bytes: no current 0.1.3 entry remains; BC and unrelated artifact
hashes are unchanged. The only unrelated change is an inert Gradle publisher
metadata timestamp, with the same release value. M0 does not
implement `TotipoStore` or choose Android storage.

## 0.1.4 published provenance

The annotated tag object `6d59303fc2e3cdae9f57032b0d889841421cbb96` resolves
to the release commit above. The published GitHub release is neither draft nor
prerelease. `SPEC_PIN.md` at that commit confirms the unchanged 0.1.3 v1/r18
spec pin. Both Central module variants target Java 17; every production class
in both JARs has class major version 61. Core runtime remains BC 1.86.
All 15 published NIO Java source files match the release commit exactly.

SHA-256 below was calculated from actual Maven Central bytes; JAR hashes also
match the published module metadata. POMs and sources are inspection evidence,
not local build inputs.

| Artifact | SHA-256 |
| --- | --- |
| [totipo-core-0.1.4.jar](https://repo.maven.apache.org/maven2/org/totipo/totipo-core/0.1.4/totipo-core-0.1.4.jar) | `1e3db6ca15273941549dfa821b9f7dc9a00c6ad81e7e66642a1f09c114eeee19` |
| [totipo-core-0.1.4.module](https://repo.maven.apache.org/maven2/org/totipo/totipo-core/0.1.4/totipo-core-0.1.4.module) | `6dc1052306ed1e25dd06c7484a0378e7e3f19c74607f5bc5b7ab3be54802c564` |
| [totipo-core-0.1.4.pom](https://repo.maven.apache.org/maven2/org/totipo/totipo-core/0.1.4/totipo-core-0.1.4.pom) | `498abaca7446d84fbb0da3bd92246424fe2eabe839ff5c02c1f5d7b2b7e54bc8` |
| [totipo-storage-nio-0.1.4.jar](https://repo.maven.apache.org/maven2/org/totipo/totipo-storage-nio/0.1.4/totipo-storage-nio-0.1.4.jar) | `9e559ec75fb09af068f876751f32d696c50c40988a16d28419dfa05d1d1c4dae` |
| [totipo-storage-nio-0.1.4.module](https://repo.maven.apache.org/maven2/org/totipo/totipo-storage-nio/0.1.4/totipo-storage-nio-0.1.4.module) | `2d9b9e6bf66e21a7258eeeecc85550f11dc4071896e847c2d57d7319f3c2af24` |
| [totipo-storage-nio-0.1.4.pom](https://repo.maven.apache.org/maven2/org/totipo/totipo-storage-nio/0.1.4/totipo-storage-nio-0.1.4.pom) | `f954ae9643d0a33b3e2ee8c0024a62e4b5fc555c6c233795a986eb6c12a48b4c` |

Published NIO sources JAR SHA-256: `bee2a5c9f941360b9c499f1518e3b2201e1e94405a34a8d2709033419e2ed67f`.

The existing M1C probe now calls the released `NioTotipoStore.openPrivate(Path)`
factory with default durability. It exclusively owns a disposable app-private
no-backup root and serializes all runs/handles. No independent writer or sync
process mutates that root. Physical API-37 private-mode qualification passed the
complete exercised workflow; see `review/TOTIPO_JAVA_0_1_4_ANDROID_REPIN_REPORT.md`.
Storage-NIO remains debug-only pending a later reviewed production-dependency
decision. This does not establish Android-wide or power-loss support.

Historical 0.1.3 qualification evidence, including its failed shared-mode device
run, remains in `review/M1C_LOCAL_REPLICA_RECONCILIATION_REPORT.md`.
