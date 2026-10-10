# Totipo Java released dependency

Android directly consumes `org.totipo:totipo-storage-nio:0.2.0` from Maven Central.
NIO exposes exactly `org.totipo:totipo-core:0.2.0` transitively on compile/runtime
classpaths; core's sole runtime dependency remains `org.bouncycastle:bcprov-jdk18on:1.86`.
No redundant direct core edge is introduced. All production classes in both released
JARs have Java 17 class major 61; module variants specify JVM 17.

Source: [totipo-java v0.2.0](https://github.com/totipo-org/totipo-java/releases/tag/v0.2.0).
Annotated tag `d24e3d0ae71ea7fe318261519a9b5d08657a0e03` resolves to release commit
`d6310c177ae930df188fd4f5798622c935698b2e`.
Protocol: Totipo Vault Format v1/r19 at spec commit
`cdb4e91be1c6d3704874b2b92457ffe7be5e9084`, independently confirmed in release `SPEC_PIN.md`.
All 82 core and 12 NIO published source files match that release commit byte for byte.
Sources/Javadocs are inspection evidence only, never Android build inputs.

## Runtime and reviewed guidance identities

| Identity | Exact pin / meaning |
| --- | --- |
| Runtime artifact | Totipo Java **0.2.0**: `org.totipo:totipo-storage-nio:0.2.0` → `org.totipo:totipo-core:0.2.0`; runtime BC remains 1.86. |
| Released source implementation | `d6310c177ae930df188fd4f5798622c935698b2e`, the source Android actually executes. |
| Reviewed application-operation guidance | `f0a028676c1801a10b2d8d2650bf9c24357d1fa1`, a later reviewed clarification of how clients use that implementation. |
| Applies to | Java **0.2.0** semantics, Totipo Vault Format **v1/r19**. |

API_DESIGN:
[exact reviewed revision](https://github.com/totipo-org/totipo-java/blob/f0a028676c1801a10b2d8d2650bf9c24357d1fa1/API_DESIGN.md).
Exact UTF-8 blob SHA-256:
`338837e3563446f8ec11f7bb47c0e4f3ff5502b7608e397f9b8c4d75372715fd`.

The guidance includes both **Operation classes and state-snapshot semantics**
(introduced by `3b24b54becde0c93479c1fbd80ea0fbd2026e2a8`) and the later
subscriber-threading clarification (`ce00f0c6db8fb1c81322a7b9e02132d276f63a33`):
onSubscribe is synchronous on the subscribing thread; subsequent onNext/onError/
onComplete use the asynchronous serialized common-pool drain. The selected later
observation-composition revision adds **Observation-pass ordering and composition**:
ordered completed local passes, distinct per-pass VaultState references, coordinated
external-import assumptions, and explicit local-only limits. It still describes
released Java 0.2.0 behavior; refresh return is not an acknowledgement. Its committed
[clarification report](https://github.com/totipo-org/totipo-java/blob/f0a028676c1801a10b2d8d2650bf9c24357d1fa1/review/JAVA_OBSERVATION_COMPOSITION_CLARIFICATION_REPORT.md)
records final Java/publication/consumer qualification and human Nix PASS.

Artifact/source pin = implementation Android actually executes. Guidance pin =
later reviewed clarification of how clients should use that implementation.
These identities intentionally differ. VERSION remains 0.2.0, SPEC_PIN remains
v1/r19, and production Java differs only in comments in this lineage; no runtime
or public API implementation upgrade is required to consume the guidance. Published
0.2.0 Javadoc bytes remain unchanged. Existing Maven/JAR provenance below is retained.
See [the historical architecture audit](review/ANDROID_JAVA_OPERATION_MODEL_AUDIT.md)
for earlier evidence and [the guidance-pin/F08 follow-up](review/ANDROID_JAVA_GUIDANCE_PIN_REFRESH_REPORT.md)
for this prospective disposition and exact assumptions.

## Android composition and Maven provenance

`CoordinatedPrivateStore` uses the exact public construction in Java 0.2.0's
`publishing/consumer-smoke/src/main/java/ConsumerSmoke.java`:

```java
NioStoreComposition.coordinatedDelegate(root, new NioDurability())
```

This returns one persistent `TotipoStore` delegate using Java-owned complete-stage
ordinary-move publication and durability. `openPrivate` is absent from Java 0.2.0.
Ordinary shared `NioTotipoStore.open` uses hard-link publication and remains unsuitable
for the previously tested Android app-private filesystem. There is no fallback to it.

The Application-owned `LocalReplicaOwner` leases the single app-private local canonical
store in no-backup storage. `CoordinatedPrivateStore` enforces the composition contract:
one process, one root owner, one persistent delegate, every call serialized, and exclusive
bridge batches excluding session SPI operations. Close the logical session facade before
the physical domain; final delegate close happens once. No independent writer or second
delegate may access this root. Java session serialization alone is insufficient.
The retained owner class name is historical; its current role is canonical store ownership.

The r19 `TotipoStore` consists of readVault/createVault/scanObjects/readObject/publishObject/close.
There are no public PreparedVault, staging/replacement handles, password change or root
fingerprint APIs. VAULT is immutable and create-once. Android presents Java's
`OBJECT_DATA_OBSERVED` creation veto without a bypass or an Android implementation of
protocol parsing or crypto. Join also checks orphan-name evidence through the public SPI
before exact enrollment; it never parses or unwraps VAULT in Android.

Every explicit Import/Publish compares detached provider VAULT using `Totipo.vaultId`
with the authoritative open `session.vaultId()`, outside the Android store gate.
Recognition is structural validation plus SHA-256, without authentication, password,
Argon2, freshness or origin evidence. Only a complete root listing and one exact valid
matching 87-byte document allow object mutation. Multiple candidates conservatively
block, even identical duplicates. Import/Publish never write or adopt VAULT.
M3C's separate explicit Join authenticates a detached exact provider VAULT with
`Totipo.open` on a transient read-only public-SPI store, closes that session, fresh-rechecks
the provider bytes and installs create-only through the coordinated SPI before normal open.
Separate explicit Initialize snapshots canonical local VAULT under the bridge, compares
identity after gate release, and creates the exact provider VAULT plus objects-v1.
Neither operation replaces, repairs or migrates VAULT, changes its password or exports secrets.
Folder READY describes accessibility and persisted permission, independently of identity.

`verifyMavenBoundary` checks all four production configurations, the direct NIO edge,
exact NIO/core versions and runtime BC edge. Locks and strict SHA-256 verification
remain enabled. There is no source/project/file/Maven-local/composite fallback or mixed
Totipo version. `package-deps.json` is generated only by the human-supported Nix updater;
cache and final human checks are recorded in the reconciliation report.

Independently downloaded bytes from
[canonical Maven Central](https://repo.maven.apache.org/maven2/org/totipo/).
JAR/source/Javadoc hashes match published module metadata.

| Artifact | SHA-256 |
| --- | --- |
| `totipo-core-0.2.0.jar` | `4f1fb4bb1ab5f0a78c0f2d9a1ed3146413c94f631e7f9b95477968a66968520f` |
| `totipo-core-0.2.0.module` | `e344cca2fe0297cb06f74acba63800fb85c69a24bc146f1165143a232984338e` |
| `totipo-core-0.2.0.pom` | `1a6bbd5b4d82c079cb621d66c89b446c09e2df33efe5e0e89dd5abfd35d7c136` |
| `totipo-core-0.2.0-sources.jar` | `e2661770e0e59ca733959b4931689a9253988352605087c390bfc3a045dd9b6c` |
| `totipo-core-0.2.0-javadoc.jar` | `863f8b31f9a66461cf60ec56785f112eaa6e327985c0c1b4bc5ef3a5edbb795c` |
| `totipo-storage-nio-0.2.0.jar` | `776068249e689e94136748fb8ffd86c837e0af8bbb6cec8ae5e785c4eca4ba93` |
| `totipo-storage-nio-0.2.0.module` | `33a431575523bb545b57876b09fd04255e04b60b2c64541d72b2cda5f05c7afa` |
| `totipo-storage-nio-0.2.0.pom` | `4acf1725ac7bb5cd7a4299f729d5eceb82aa3ea524dca7c9d5a9b56616db87b2` |
| `totipo-storage-nio-0.2.0-sources.jar` | `bb690c98837f938b1cce75cdfc06b44b47b066c4cb937a4ec461f2dc82f5b04b` |
| `totipo-storage-nio-0.2.0-javadoc.jar` | `ef1b8cf0a278eb64e0cb9073e627debeba298f0fd400378fadfdbf8cd9c7ac7c` |

Android platform/storage qualification is separate from Java's operation-scoped
qualification. See [the reconciliation report](review/JAVA_0_2_0_R19_ANDROID_RECONCILIATION_REPORT.md)
for current validation. Historical M1/M3 reports are preserved unchanged.
