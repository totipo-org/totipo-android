# Totipo Java released dependency

Android consumes `org.totipo:totipo-storage-nio:0.3.2` from Maven Central. NIO
exposes exactly `org.totipo:totipo-core:0.3.2` transitively. Core's sole runtime
dependency is `org.bouncycastle:bcprov-jdk18on:1.86`. Both modules target Java 17
(class major 61, module JVM 17). No direct redundant core edge, local artifact,
source substitution, composite build or vendored implementation is used.

## Runtime and guidance identities

| Identity | Exact pin |
| --- | --- |
| Published runtime | Java **0.3.2**, both NIO and core |
| Released implementation | `af11553659dd8bd4157064c7e3de27bed8980ac5` ([v0.3.2](https://github.com/totipo-org/totipo-java/releases/tag/v0.3.2)) |
| Existing operation/observation guidance | `f0a028676c1801a10b2d8d2650bf9c24357d1fa1` ([exact API_DESIGN](https://github.com/totipo-org/totipo-java/blob/f0a028676c1801a10b2d8d2650bf9c24357d1fa1/API_DESIGN.md)) |
| Backup APIs and contract | [0.3.2 API_DESIGN](https://github.com/totipo-org/totipo-java/blob/af11553659dd8bd4157064c7e3de27bed8980ac5/API_DESIGN.md), [profile 1](https://github.com/totipo-org/totipo-java/blob/af11553659dd8bd4157064c7e3de27bed8980ac5/docs/BACKUP_ARCHIVE.md) |
| Protocol | Totipo Vault Format v1/r19, spec `cdb4e91be1c6d3704874b2b92457ffe7be5e9084` |

The older reviewed guidance retains the operation classes, replay-latest delivery,
deterministic bases, publication handles, observation ordering and close assumptions
used by existing Android functionality. Its exact UTF-8 SHA-256 remains
`338837e3563446f8ec11f7bb47c0e4f3ff5502b7608e397f9b8c4d75372715fd`.
Read it together with the released 0.3.2 backup contract. Capture is
blocking authenticated provider work, outside the prior A–H taxonomy; detached
ZIP output is stream work. Neither introduces a generic state-emission cancellation
rule or a requirement to put every Java operation behind Android global BUSY.
`requestRefresh()` remains nonblocking, coalescible and uncorrelated with results.

0.3.1 introduced required session capture/import methods, validated parent projections
and opt-in bounded store enumeration; `StoreFailure` adds CAPACITY. Android forwards
`scanObjects(int)` through its coordinated facade and updates the JVM stub.
Android implements export, new-local-vault restore and authenticated additive
history import using the released APIs. No new runtime dependency or protocol is introduced.

0.3.2 replaces unsupported newer standard-library calls without changing application APIs,
the v1/r19 format or frozen profile-1 ZIP bytes. The committed
[API 30 compatibility report](https://github.com/totipo-org/totipo-java/blob/af11553659dd8bd4157064c7e3de27bed8980ac5/review/JAVA_ANDROID_API30_COMPAT_REPORT.md)
is bounded static/JVM evidence; actual Android API 30 and current-device/SAF
qualification remains required. No desugaring dependency or Android workaround is added.

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
All 98 core and 13 NIO published Java source files match the exact release commit
byte for byte. Binary/source/Javadoc JAR SHA-256 values match published module
metadata; POM and module bytes were independently downloaded and pinned below.
All production classes have major 61. This is HTTPS/hash/source provenance, not a
new claim of independently authenticated signing-key ownership.

`verifyMavenBoundary` checks all four production configurations, direct NIO edge,
exact matching 0.3.2 NIO/core versions and BC 1.86. Strict locking and SHA-256
verification remain enabled. Sources/Javadocs are inspection evidence, not build
inputs. The human-owned dependency cache refresh and final Android qualification
are tracked in [R4D](review/BACKUP_RESTORE_R4D_ANDROID_FINAL_QUALIFICATION_REPORT.md).

| Artifact | SHA-256 |
| --- | --- |
| `totipo-core-0.3.2.jar` | `db35f01b51ba1777b3411306824559126179d868199c40281534b8b02da6bd98` |
| `totipo-core-0.3.2.module` | `a46eb72f8c00060244f67667dcd462bc4f3a557a9fa02eb687b2eeee1a681f74` |
| `totipo-core-0.3.2.pom` | `3f873b517e72f70bbcd5d47ee7df8c102ac0f1dc05ab23aaf23d42c481802cd7` |
| `totipo-core-0.3.2-sources.jar` | `044ea95e2d6eafe2b34f2accb2c959a12a5bdd008d350c9841fb75c9bac6c0d0` |
| `totipo-core-0.3.2-javadoc.jar` | `f41f0322247bd87fcb88d399fca446dc90b7b0e3e93e87cf26273d99f0d5685f` |
| `totipo-storage-nio-0.3.2.jar` | `c680ac4b09d68b2635b7bd4bfc3fb77b2519634df57c91b57a7b63045ea458bb` |
| `totipo-storage-nio-0.3.2.module` | `a9962bde6e871ad8aba84f193cddf5a44954dd415c388bd776ad503ce089f86b` |
| `totipo-storage-nio-0.3.2.pom` | `7bf48a433e23ba97a5db72586a0fa10bb1b297ae2a2254ade80054c198fd4ce9` |
| `totipo-storage-nio-0.3.2-sources.jar` | `14a691e74bcde1f5cddd5a5b70783b53bc9f85aa36890dd877e5126d607a6968` |
| `totipo-storage-nio-0.3.2-javadoc.jar` | `56aa65e7447bce979d19f0a1c7eb12e5616fc87f858d0578913c2f836d52aaa3` |

The previous Java 0.2.0 pin was released source
`d6310c177ae930df188fd4f5798622c935698b2e`. Its prior Android qualification remains
historical evidence in the [0.2.0 reconciliation report](review/JAVA_0_2_0_R19_ANDROID_RECONCILIATION_REPORT.md),
[operation audit](review/ANDROID_JAVA_OPERATION_MODEL_AUDIT.md) and
[guidance follow-up](review/ANDROID_JAVA_GUIDANCE_PIN_REFRESH_REPORT.md).
These do not certify physical SAF ZIP behavior on the upgraded runtime.
