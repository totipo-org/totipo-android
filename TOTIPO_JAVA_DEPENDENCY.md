# Totipo Java released dependency

Android consumes `org.totipo:totipo-storage-nio:0.1.5` as its single direct
production Totipo dependency from Maven Central. NIO requires exactly
`org.totipo:totipo-core:0.1.5`, which adds `org.bouncycastle:bcprov-jdk18on:1.86`
at runtime. Both debug and release package core/NIO/BC. No redundant direct core
edge is needed: NIO exposes core transitively on compile and runtime classpaths.

The qualified local-replica mode is `NioTotipoStore.openPrivate(...)`, with default
durability, exclusively app-controlled writers and root-wide serialization.
`LocalReplicaOwner` resolves `getApplicationContext().getNoBackupFilesDir()/totipo-vault`
and gates all production handles/sessions and future bridge imports with an
exclusive lease. It stores no unlocked session. The SAF tree is transport candidate
state; it is never the canonical store used for local application operations.
Shared/default `open(...)` remains unsuitable for the tested Android private
filesystem: its hard-link publication path was denied. Private-mode qualification
applies only to the tested private deployment assumptions, not every Android
filesystem/API level or power-loss behavior. No SAF/provider reconciliation
qualification follows from local-store qualification.

Upstream: https://github.com/totipo-org/totipo-java, source tag `v0.1.5`.
Release commit: `67c1326a2ce433921a947ba4ffd6843403f4a7a3`.
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
single direct NIO edge, NIO's exact core edge, and core's BC runtime edge. It rejects
project/file artifacts, obsolete coordinates, unexpected modules, and mixed Totipo
requests even if Gradle would resolve them to 0.1.5. `verifyReleaseApkBoundary`, also
in `check`, requires core/NIO/BC and excludes all debug probe and helper classes
and test classes. Repository declarations are centralized; Totipo is excluded
from Google Maven so all Totipo resolution uses Central. Maven Local is not configured.

Nix `package-deps.json` separately pins package downloads. The human-regenerated
0.1.5 cache was reviewed against actual Central bytes, Gradle verification metadata
and publisher POM bytes. All six Totipo hashes match; no 0.1.4 Totipo artifact entries
remain. BC and unrelated cache entries are unchanged. Totipo publisher metadata
advances NIO to 0.1.5 and drops unused core metadata. Final human Nix check status
is recorded in the M1F report. No Nix command is run by the agent.

## 0.1.5 published provenance and candidate validation

Published [GitHub release](https://github.com/totipo-org/totipo-java/releases/tag/v0.1.5)
is neither draft nor prerelease. Annotated tag object
`8c0c1d45e934df08c0910cf4b2b439d70d918e5a` resolves to source commit
`67c1326a2ce433921a947ba4ffd6843403f4a7a3`. `SPEC_PIN.md` at that commit
confirms the unchanged v1/r18 spec pin above. Both Central module variants target
Java 17. Core runtime remains BC 1.86. All 93 core and 15 NIO published Java
sources match this release commit exactly; sources are inspection evidence only.
NIO JAR and sources JAR bytes are identical to 0.1.4. No SAF/provider qualification
is supplied by Java 0.1.5; historical Android private-mode evidence remains below.

`VaultSession.validateObject(RevisionId, byte[])` validates externally observed
immutable objects under an already open authenticated session, entering its normal
serialization/lifecycle gate. No store access, import, state update, KDF/password
re-entry or root export occurs. Results are `ObjectCandidateValidation.Invalid`
or `Valid`; Valid defensively owns the canonical ID and exact 1024-byte ciphertext.
No parsed TOKEN, secret or metadata is returned. Valid values compare ID and bytes;
only session-returned values establish authentication, under that session's root.
Default external implementations may report unsupported validation; closing/closed
library sessions throw `SessionClosedException`.

Android's `ImmutableCandidateClassifier` applies this public API only to M1E exact
1024-byte candidates with canonical lowercase-hex names. Transport partial/overflow/
unavailable states remain distinct from Invalid; unavailable sessions remain distinct
as well. One scan uses one supplied session for all duplicate comparisons. Unequal
Valid values for one ID report an integrity contradiction without selection. Full
provider provenance and completeness remain available. The synchronous caller must
use a worker thread and keep its existing session/root ownership for the operation.
No second NIO store, local mutation, provider mutation, VAULT validation, persistence
or UI is added. NIO private mode remains the production local-store boundary.

SHA-256 calculated from actual Maven Central bytes; JAR hashes also match module
metadata. Both POM/module/JAR sets returned HTTP 200 before repinning.

| Artifact | SHA-256 |
| --- | --- |
| [totipo-core-0.1.5.jar](https://repo.maven.apache.org/maven2/org/totipo/totipo-core/0.1.5/totipo-core-0.1.5.jar) | `e99609e59db1d9f52f80c446060e63ce7dde17ad69252fa87d397d0cb1577f81` |
| [totipo-core-0.1.5.module](https://repo.maven.apache.org/maven2/org/totipo/totipo-core/0.1.5/totipo-core-0.1.5.module) | `8a3bd815956d59f470f2639cfd95697496abac8c24b6b848579855c05f40bbe5` |
| [totipo-core-0.1.5.pom](https://repo.maven.apache.org/maven2/org/totipo/totipo-core/0.1.5/totipo-core-0.1.5.pom) | `886ae205c99b23aef40332102b7c31393e88a8b3a513f21f5f18a6a93307e3a2` |
| [totipo-storage-nio-0.1.5.jar](https://repo.maven.apache.org/maven2/org/totipo/totipo-storage-nio/0.1.5/totipo-storage-nio-0.1.5.jar) | `9e559ec75fb09af068f876751f32d696c50c40988a16d28419dfa05d1d1c4dae` |
| [totipo-storage-nio-0.1.5.module](https://repo.maven.apache.org/maven2/org/totipo/totipo-storage-nio/0.1.5/totipo-storage-nio-0.1.5.module) | `4339f56533be8af2fa42b283b7a8513b51bae9de2d7f9aca3294dfec7b03dd88` |
| [totipo-storage-nio-0.1.5.pom](https://repo.maven.apache.org/maven2/org/totipo/totipo-storage-nio/0.1.5/totipo-storage-nio-0.1.5.pom) | `1065a65031308327d660e2290213b5489ceeb13a5f11507b13b45ed4d926bb3d` |

Published core sources JAR SHA-256:
`d797c0854db469e8288b787dcbeb3dd3eb8e83062bea5c42b0013082b2cccdc4`.
Published NIO sources JAR SHA-256:
`bee2a5c9f941360b9c499f1518e3b2201e1e94405a34a8d2709033419e2ed67f`.

## Historical 0.1.4 published provenance and Android qualification

The 0.1.4 release commit was `44332d459e0cbec74e93ea5fb77280596c505732`.

The annotated tag object `6d59303fc2e3cdae9f57032b0d889841421cbb96` resolves
to the 0.1.4 release commit above. The published GitHub release is neither draft nor
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

The historical M1C probe run used the released `NioTotipoStore.openPrivate(Path)`
factory with default durability. It exclusively owns a disposable app-private
no-backup root and serializes all runs/handles. No independent writer or sync
process mutates that root. Physical API-37 private-mode qualification passed the
complete exercised workflow; see `review/TOTIPO_JAVA_0_1_4_ANDROID_REPIN_REPORT.md`.
M1D promotes the same released artifact to production without altering NIO storage
behavior. See `review/M1D_PRODUCTION_LOCAL_REPLICA_DESIGN_REPORT.md` for ownership,
reconciliation design and its review limits. This establishes no Android-wide or
power-loss support.

Historical 0.1.3 qualification evidence, including its failed shared-mode device
run, remains in `review/M1C_LOCAL_REPLICA_RECONCILIATION_REPORT.md`.
