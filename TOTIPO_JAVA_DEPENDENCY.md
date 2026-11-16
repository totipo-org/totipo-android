# Totipo Java released dependency

Android consumes `org.totipo:totipo-storage-nio:0.3.1` from Maven Central. NIO
exposes exactly `org.totipo:totipo-core:0.3.1` transitively. Core's sole runtime
dependency is `org.bouncycastle:bcprov-jdk18on:1.86`. Both modules target Java 17
(class major 61, module JVM 17). No direct redundant core edge, local artifact,
source substitution, composite build or vendored implementation is used.

## Runtime and guidance identities

| Identity | Exact pin |
| --- | --- |
| Published runtime | Java **0.3.1**, both NIO and core |
| Released implementation | `27cb53a1a5f2cc3d5f221ef790f694b26b90f8a8` ([v0.3.1](https://github.com/totipo-org/totipo-java/releases/tag/v0.3.1)) |
| Existing operation/observation guidance | `f0a028676c1801a10b2d8d2650bf9c24357d1fa1` ([exact API_DESIGN](https://github.com/totipo-org/totipo-java/blob/f0a028676c1801a10b2d8d2650bf9c24357d1fa1/API_DESIGN.md)) |
| Backup APIs and contract | [0.3.1 API_DESIGN](https://github.com/totipo-org/totipo-java/blob/27cb53a1a5f2cc3d5f221ef790f694b26b90f8a8/API_DESIGN.md), [profile 1](https://github.com/totipo-org/totipo-java/blob/27cb53a1a5f2cc3d5f221ef790f694b26b90f8a8/docs/BACKUP_ARCHIVE.md) |
| Protocol | Totipo Vault Format v1/r19, spec `cdb4e91be1c6d3704874b2b92457ffe7be5e9084` |

The older reviewed guidance retains the operation classes, replay-latest delivery,
deterministic bases, publication handles, observation ordering and close assumptions
used by existing Android functionality. Its exact UTF-8 SHA-256 remains
`338837e3563446f8ec11f7bb47c0e4f3ff5502b7608e397f9b8c4d75372715fd`.
Read it together with the released 0.3.1 additions for backup. Capture is new
blocking authenticated provider work, outside the prior A–H taxonomy; detached
ZIP output is stream work. Neither introduces a generic state-emission cancellation
rule or a requirement to put every Java operation behind Android global BUSY.
`requestRefresh()` remains nonblocking, coalescible and uncorrelated with results.

0.3.1 adds required session capture/import methods, validated parent projections
and opt-in bounded store enumeration; `StoreFailure` adds CAPACITY. Android forwards
`scanObjects(int)` through its coordinated facade and updates the JVM stub.
Android implements export and new-local-vault restore using the released APIs;
additive history import remains absent. No new runtime dependency or protocol is introduced.

## Android composition and ownership

`CoordinatedPrivateStore` retains one persistent public NIO construction:

```java
NioStoreComposition.coordinatedDelegate(root, new NioDurability())
```

Java owns ordinary-move publication and durability. Ordinary shared
`NioTotipoStore.open` uses hard-link publication and remains unsuitable for the
previously tested Android private filesystem. There is no fallback to it.

The Application-owned `LocalReplicaOwner` leases the selected app-private canonical
root in no-backup storage. One process, one root owner and one persistent delegate
serialize every SPI call and bridge batch. Close the logical session facade before
the physical domain; final delegate close happens once. No independent writer or
second delegate accesses that root. Session serialization alone is insufficient.

Backup reserves controller admission while capturing, excludes local authoring and
Sync bridge publication, and calls `session.captureBackup(BackupLimits.DEFAULT)`
**outside** the bridge lock. The session gate precedes the store gate. Java's
read-only observer can run under those same gates. The resulting detached capture
contains exact encrypted bytes, all supported locally observed history and explicit
ancestry-gap diagnostics. Android freezes the complete ZIP in bounded private memory
before the picker. Capture and archive buffers never touch canonical disk roots.
The Sync 512-object batch is not used for backup; the shared 4,096-object limit
fails explicitly rather than truncating.

Existing provider identity checks, authenticated Join, create-only enrollment,
Initialize and import-before-publish observation barriers remain separate. Every
Import/Publish compares detached provider VAULT with the authoritative open
session's VaultId outside the store gate. READY remains transport accessibility,
not identity, authentication, freshness or delivery. Join's transient authenticated
candidate closes before ordinary canonical enrollment/open. Only Initialize writes
a previously absent provider VAULT. No backup action invokes Sync, acquires a tree
grant, exports credentials or changes Java Keystore/password behavior.

## Maven provenance and qualification

Downloaded from [canonical Maven Central](https://repo.maven.apache.org/maven2/org/totipo/).
All 96 core and 13 NIO published Java source files match the exact release commit
byte for byte. Binary/source/Javadoc JAR SHA-256 values match published module
metadata; POM and module bytes were independently downloaded and pinned below.
All production classes have major 61. This is HTTPS/hash/source provenance, not a
new claim of independently authenticated signing-key ownership.

`verifyMavenBoundary` checks all four production configurations, direct NIO edge,
exact matching 0.3.1 NIO/core versions and BC 1.86. Strict locking and SHA-256
verification remain enabled. Sources/Javadocs are inspection evidence, not build
inputs. The human-owned dependency cache refresh and final Android qualification
are tracked in [R4A](review/BACKUP_RESTORE_R4A_ANDROID_EXPORT_REPORT.md).

| Artifact | SHA-256 |
| --- | --- |
| `totipo-core-0.3.1.jar` | `37db31e950f1e38e5d7a33d7f8a49f1c8162812d753bd9a7d332393ed5960c1b` |
| `totipo-core-0.3.1.module` | `a53574f14a50df5d123f8bbcd8b54f93863d43e4003acd55795613ca8a691440` |
| `totipo-core-0.3.1.pom` | `30468a84a246cb52a988711fcf44c4234b0fa34a859f6c80b6d3275b775cbe9b` |
| `totipo-core-0.3.1-sources.jar` | `d335377024d66d43c9a6e0a40dfec7deec7f8c6d91a56e0285c8012763249257` |
| `totipo-core-0.3.1-javadoc.jar` | `de4311a6c11fb12149fea4cc324ad60c182c1a8d5944190595d8f898f39958e3` |
| `totipo-storage-nio-0.3.1.jar` | `f88ddd5d635b36c04aa97bf541f2ce5eeb16ce049fd7f8113c3a803f39a1a813` |
| `totipo-storage-nio-0.3.1.module` | `ec56805f69015274c03a27b6bad08473d8edaad392d5f499e223f43316070255` |
| `totipo-storage-nio-0.3.1.pom` | `441df906fc7d97b21d0c14f5ced87524c70ce5cc77ded02130fcf253c1294bf9` |
| `totipo-storage-nio-0.3.1-sources.jar` | `da6dfc3f058d38847d86e55ff3301a057317e1eb96f45945bda88ee9af2b6298` |
| `totipo-storage-nio-0.3.1-javadoc.jar` | `79b3a856bee9df58d8b4c626cc8f21097e94a06e1f87a21aa586f9ed1bd3c96e` |

The previous Java 0.2.0 pin was released source
`d6310c177ae930df188fd4f5798622c935698b2e`. Its prior Android qualification remains
historical evidence in the [0.2.0 reconciliation report](review/JAVA_0_2_0_R19_ANDROID_RECONCILIATION_REPORT.md),
[operation audit](review/ANDROID_JAVA_OPERATION_MODEL_AUDIT.md) and
[guidance follow-up](review/ANDROID_JAVA_GUIDANCE_PIN_REFRESH_REPORT.md).
These do not certify physical SAF ZIP behavior on the upgraded runtime.
