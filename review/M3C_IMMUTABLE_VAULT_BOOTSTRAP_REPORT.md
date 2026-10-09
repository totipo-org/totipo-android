# M3C — immutable VAULT bootstrap / device enrollment

Status: complete. Implementation, non-Nix validation, physical qualification and human-reported Nix checks passed.
All changes remain unstaged/uncommitted. No Nix or remote CI is run by the agent.

## 1. Starting state / exact HEAD

Initial branch `main`, HEAD `a5e0d8324b3933e0a3921980974bc35b49b4edc6`.
Initial `git status --short` was empty. This is the committed Java 0.2.0/r19 reconciliation.
The starting architecture uses one canonical private store, the coordinated public NIO
composition, existing owner/coordinator, persisted SAF binding, and the single provider lane.

## 2. Baseline validation

Before edits, `./gradlew check :app:assembleDebug :app:assembleRelease` passed (1m39s).
The requested strict offline/no-daemon/no-configuration-cache/no-build-cache/rerun-tasks
clean build passed (1m28s, 92 tasks executed). **217 tests; zero failures/errors/skips**.
Wrapper, debug `--debug-probe`, unsigned release `--unsigned --no-debug-probe`, and
`git diff --check` passed. APK hashes after the baseline builds:

- Debug: `2cf5e60866f4659f5f1fe3d193007f7cc64f8a19cc6e53f57646a15719423c7f`.
- Unsigned release: `6ef1416ac62f2200afa440dd1409feeab4a1c42db0d74b3b5c7a5c785218b99a`.

Actual graph: NIO 0.2.0 → core 0.2.0 → runtime BC 1.86, strict locks; compile excludes BC.
Release has no permissions, services, receivers or providers. Its exported boundaries remain
MainActivity launcher and OtpAuthEnrollmentActivity with VIEW otpauth/totp and SEND text/plain.
Debug retains existing diagnostic Activities only. `allowBackup=false`; applicationId remains
org.totipo.android. Canonical adaptive/round/density icon verification passed.
Package-deps SHA-256: `74eb72a9ffb5f9f364f02f9f64014f260f4baf610c1cefdb9b09ed6846eb42b1`.

## 3. Exact Java 0.2.0 API inspection

Inspected published source and Javadoc JARs for Totipo, VaultSession, VaultId,
CreateVaultResult, TotipoStore, VaultCreate, BoundedRead, ObjectScan, VaultLifecycle,
NioStoreComposition and NioTotipoStore. All 82 core and 12 NIO source files match the
released source JARs byte for byte. Release source commit is
`d6310c177ae930df188fd4f5798622c935698b2e`, spec r19
`cdb4e91be1c6d3704874b2b92457ffe7be5e9084`; provenance remains in TOTIPO_JAVA_DEPENDENCY.md.
Core JAR SHA-256: `4f1fb4bb1ab5f0a78c0f2d9a1ed3146413c94f631e7f9b95477968a66968520f`.
NIO JAR SHA-256: `776068249e689e94136748fb8ffd86c837e0af8bbb6cec8ae5e785c4eca4ba93`.

There is no dedicated facade method authenticating externally supplied VAULT bytes.
The narrow supported public path is `Totipo.open(TotipoStore, char[])` with the candidate
exposed through `readVault(87)`. The facade/SPI document ownership transfer on every outcome,
including invalid password input; a successful VaultSession owns the store until close.
VaultId is structural SHA-256 identity only. VaultLifecycle confirms password validation,
Java-owned unwrap, cleanup on failure, and root ownership transfer to ApplicationSession.
ApplicationSession may schedule initial observation; the transient store supplies empty
complete coverage safely and session close serializes store cleanup and secret wiping.
No Android call to the format implementation bridge or root-returning crypto API is introduced.
NioStoreComposition requires whole-root external coordination; ordinary shared NIO open
remains unused. VaultCreate is opaque create-only publication knowledge: Created still
requires fresh canonical verification; Failed/AlreadyPresent establish no mutation by that
call; Uncertain permits installed state and forbids repair/removal assumptions.

## 4. Candidate authentication design decision

DetachedVaultAuthentication implements only public TotipoStore SPI. It clones the exact
candidate, offers defensive bounded reads, empty complete object scan, absent object reads,
rejects both mutation methods and wipes its owned candidate on idempotent close. It performs
no filesystem/provider I/O and creates no persistent store. Totipo.open owns it on every
outcome. An opened temporary session is immediately closed and used solely as authentication
proof. No root/key/secret escapes. This small composition avoids a Java API change and any
Android protocol parser, KDF or unwrap implementation.

## 5. Enrollment, not migration

VAULT stays immutable for its lifetime. Join installs the exact authenticated provider
representation into an empty local canonical store. Initialize transports the exact local
representation into a provider root where VAULT is positively absent. Neither copies
semantic state across roots or reconciles different VaultIds.

## 6. Join preconditions

Explicit foreground admission requires NO_LOCAL_VAULT, available local ownership, no
session/incompatible operation, READY readable binding and a free logical provider operation.
Preparation checks actual local absence, complete object coverage and orphan-name veto using
the coordinated SPI; installation repeats this under the gate. Uncertain/wrong-kind local
VAULT is never treated as absent. Failed closure retains the coordinator/lease for recovery.

## 7. Provider VAULT preflight

Fresh root evidence uses existing bounded traversal on ProviderIoLane. Complete root coverage
and exactly one usable exact-name non-directory vault row are required. The bounded stream
consumes at most 88 bytes; only exact 87-byte PRESENT evidence proceeds. Duplicates, loading,
malformed metadata, wrong length, missing/unavailable reads and grant/binding changes block.
Size/timestamps/document identifiers are never identity authority. Totipo.vaultId executes
on the vault worker, before the UI presents a password form; identity is never rendered.

## 8. Authentication / password ownership

The product first prepares the candidate, then reuses the shell password field for Join.
Empty password retains explicit disclosure/confirmation; no strength requirement is added.
Controller admission transfers a char buffer to a worker-owned JoinCredential. Provider
requests/callbacks carry no credential buffer. Invalid input and wrong password leave local
VAULT and objects untouched. Terminal results/cancellation discard and wipe the buffer;
shutdown queues worker cleanup. Activity fields are unsaved and cleared on submission,
cancellation/destruction. No String conversion, preferences, Bundle, logs or error detail.

## 9. Fresh candidate recheck

At submission, fresh captured bytes must still equal the prepared candidate before Java
authentication. After successful temporary-session closure, a second fresh provider read
requires the same current binding/grant/controller generation and exact byte equality.
Changed/disappeared/ambiguous evidence is stale; replacement is not automatically authenticated.

## 10. Exact local VAULT installation

The owner supplies one coordinated storage domain. Bridge.enrollVault positively checks
absence and complete orphan-safe object evidence, then invokes delegate.createVault(exact)
under the fair gate. There is no local path write, new vault generation or persistent
candidate store. Failed/Uncertain quarantine and stop; no retry/removal. AlreadyPresent
requires fresh exact canonical equality before normal open, including late identical evidence.

## 11. Post-install normal open

The same domain transfers its ordinary session-facing facade to Totipo.open with the supplied
password after bridge release. Fresh canonical bytes must equal the intended candidate;
normal authentication must succeed and session.vaultId must equal Totipo.vaultId(candidate).
No special long-lived Join session exists. Closure/ownership recovery follows existing policy.

## 12. Join result / status UI

No-local shell retains normal Create Vault and folder selection, adding Join existing vault.
Preparation precedes password presentation. Fixed messages include joined success, could not
unlock, missing/unverifiable provider, changed folder, and local token-evidence veto. No
VaultId, URI, document ID, object name, exception message or ciphertext is rendered.
Cancel Join revokes the operation and queues worker credential wiping without freeing a hung lane.

## 13. Initialize preconditions

Explicit Initialize sync folder is offered for OPEN local vault with READY readable/writable
transport, no incompatible local operation and no logical provider operation. The action
always performs fresh semantic preflight; it never caches remote absence as authorization.
READ-only bindings can prepare/Join but cannot initialize.

## 14. Provider orphan-object veto

Before creating absent VAULT, exact objects-v1 evidence must be positively absent or one
usable directory with complete direct-child listing. Duplicate/conflicting/wrong-kind or
incomplete namespace evidence blocks. Any plausible 64-lowercase-hex child name, including
a wrong-kind child, vetoes creation without claiming authentication or vault membership.
No bypass, deletion or overwrite exists. Existing empty complete directories are retained.

## 15. Local canonical VAULT snapshot

Bridge.snapshotVault uses bounded delegate.readVault(87), requires exact presence and returns
an owned defensive copy. After releasing the gate the coordinator compares Totipo.vaultId
with session.vaultId. Mismatch writes nothing remotely. No retained mutable storage handle,
password or root crosses the bridge.

## 16. Provider VAULT create-only writer

ProviderVaultWriter is a dedicated semantic wrapper. AndroidProviderVaultPort exposes only
fixed root vault/octet-stream and objects-v1/directory creation. The platform validates the
returned URI authority/tree/root/child relationship, then queried metadata must have exact
name, kind, ID, parent and locator before any output. A suffix document receives no VAULT
bytes. Returned IDs and URIs are also rejected if already present in the scanned root.
Only the operation-local newly returned VAULT handle reaches vaultOutput.
No scanned existing document is overwritten, truncated, renamed, deleted or repaired.

## 17. objects-v1 creation

After positive VAULT verification, positively absent namespace may be created with the
Android directory MIME. Returned metadata and fresh exact single-directory root evidence
are required. No filesystem conversion, suffix retry or cleanup. An existing empty usable
directory is not recreated.

## 18. Immediate readback / postflight

Write exactly 87 bytes and close, then bounded-read the same operation-local document for
exact equality. Fresh root postflight must show one exact usable VAULT equal to local bytes.
Worker confirmation additionally requires Totipo.vaultId(postflight bytes) to equal the
current session.vaultId, after the store gate has been released.
Fresh namespace confirmation requires exactly one usable directory. Binding/grants/session
must remain current; output close alone is never success.

## 19. Partial initialization

Uncertain VAULT creation/write/readback stops before directory creation. Verified VAULT
followed by failed/uncertain directory creation is PARTIAL and remains installed. No rollback
or deletion. Explicit retry fresh-preflights and can create only the missing directory.

## 20. Idempotence

Join does not replace an existing local vault. Already-present local bytes are verified
exactly before proceeding. Fully initialized matching provider evidence yields no VAULT
writes and no duplicate directory creation. Every Initialize repeats fresh preflight.

## 21. No automatic Import / Publish

Join produces an ordinary empty OPEN local session; Import remains a separate user action.
Initialize writes identity/namespace only; Publish remains a separate user action.
Higher-level controller tests and physical harness assert this separation and same-session use.

## 22. Provider lane / lock ordering

All query/stream/create/output/readback calls use the existing single bounded ProviderIoLane.
It receives detached opaque bytes and transport metadata, never a password/session/root or
plaintext token. Authentication and local lifecycle execute on Totipo-vault. Session calls
occur after bridge release; local createVault occurs under the coordinated gate. No new
provider executor, queue, retry worker, timer, poller, service or background framework.

## 23. Cancellation / generations

Join uses binding request generation, session/controller generation and atomic cancellation.
Local creation, folder change/disconnect, Cancel Join and shutdown invalidate detached evidence.
Provider reads may finish; stale evidence cannot enroll. Initialize reuses M3B session/binding
checks and atomic cancellation. Lock/disconnect remain responsive during provider IPC;
an already-started remote mutation may finish and is never described as rolled back.
Stale completion cannot enter a replacement/closed session. Logical admission stays occupied
until the outstanding provider operation returns, avoiding accumulation behind hung IPC.

## 24. M3A invariance

Import still reads a fresh provider VAULT and requires its structural identity to match
its current open session before immutable-object validation/publication. Join authorization
is not cached for Import. Existing incomplete object coverage, contradiction, duplicate and
ExistingDifferent handling remain.

## 25. M3B invariance

Publish still uses its own fresh matching-VAULT identity gate, bounded object snapshot,
authentication, create-only names, immediate readback and fresh postflight. Initialize is
not cached authorization. No change to existing token object bounds or publication policy.

## 26. Exclusions

No password change, immutable VAULT replacement, root rotation, migration, cross-vault copy,
automatic synchronization or background work. No naming churn for LocalReplicaOwner.
Branding/resources/manifests/applicationId/backup policy remain unchanged.

## 27. Source guards

Extended reviewed createDocument/output allowlist only for the fixed bootstrap adapter.
New guards require public-SPI authentication, read-only transient store, no format/crypto/
reflection/path implementation in authentication, and only newly created VAULT output.
Bridge review now allows its two deliberate bounded read/create SPI operations while
retaining the no-session/no-crypto/no-filesystem boundary. Forbidden delete/rename/move/copy
and arbitrary provider mutation APIs remain prohibited.

## 28. Dependencies / supply chain

No production dependency/input/cache/lock/verification/branding change. NIO/core 0.2.0 and
BC 1.86 remain the entire runtime graph; test dependencies remain JUnit/Hamcrest. Wrapper,
AGP, SDK and Java pins unchanged. package-deps hash remains the baseline hash above.
No package-deps regeneration is needed. No INTERNET or broad storage permission is added.

## 29. Test totals / final builds

Final normal `./gradlew check :app:assembleDebug :app:assembleRelease` passed (1m17s).
Final strict command passed (1m41s, all 92 tasks executed):

```sh
./gradlew --offline --no-daemon --no-configuration-cache --no-build-cache \
  --rerun-tasks --dependency-verification=strict \
  clean check :app:assembleDebug :app:assembleRelease
```

**245 tests; zero failures/errors/skips**, retaining 217 baseline tests and adding 28.
Wrapper verification, both APK checks, dependency graph inspection and diff checks passed.
Iteration found a malformed regex in a new source guard and an overlap between two Gradle
runs that collided in test-result output. Both were corrected; final builds ran sequentially.
No failure or verification gate was bypassed. Tests exercise actual released Java
authentication/closure, exact enrollment, gate/domain ownership, local create outcomes and
mismatch, provider mutation faults, cancellation and manual Import/Publish separation.
Session proxies additionally assert vaultId is never called under the bridge gate.

## 30. Physical Join qualification

Passed on the connected Pixel 6a (SDK 37), with the existing qualification signer, isolated
app-private local roots and disposable children of Documents/Totipo-M3A-Test. Real Java
authored a VAULT and three TOKEN objects using public test credentials. Wrong password left
local VAULT and object namespace absent and provider unchanged. Correct Join opened the
ordinary empty session, with matching identity and exact canonical bytes; explicit Import
then produced three tokens in the same live session, without provider mutation.

Final local/provider VAULT SHA-256:
`6a52198a5f740d5e7febc2497c0eea623ef6cdda42d01dad05dd53b2e2b5bd83`.
Join passed 17 assertions on the final qualified source build.
Provider TOKEN hashes before and after Join/Import were identical:

| Object name | SHA-256 |
| --- | --- |
| a5dfd76e2bfbc11b4916e845cbdaef27cdf60196d33b288f61b0a8b3a72755ab | 12f5b12b88ac467d1b4f3365b08f26c66f8719aeb91c9dcdc00c098fa0c8b9c3 |
| d091b234e6ef3b35a03b9d0facc2fee4234964b45fbf7401877770e813eaa9de | b67679745ae043390bd5aa0b2ce7bf92ca9c4f8ca5496d214a3d992383f75764 |
| fe6aa9b3fb4546f7283d0175da0ac25e42d4b51acda883a816990cfd605afd8d | 66fe93a73ec18cabfb1179cc336dc7381283480e2fc16612d5ac724cf1817c56 |

## 31. Physical Initialize qualification

Passed 17 assertions. Provider inventory began empty. Initialize created exact VAULT and
objects-v1, with no token publication. A second Initialize created nothing. Explicit Publish
then created one immutable object; second Publish was idempotent. Local and provider VAULT
remained byte-identical throughout, SHA-256:
`b68a3ff345513e853dd3afeb1fd8b39f5b9a406da22c72d924ee4d9f0b746c9d`.
Published object name:
`800549c6c80e16d37cac5df3554f7fb200a9e1f586fd402e8c34fb4671194705`;
SHA-256 `ee3802d095843bcd0694ef79867ed824863aadab88187e132f8cc5a9c0a8bb94`.
Partial/create/write uncertainty was tested with deterministic standalone fakes, without
intentionally damaging the physical DocumentsProvider.

## 32. Provider orphan veto qualification

Passed 12 assertions. Provider VAULT was absent and objects-v1 retained the preceding
plausible TOKEN. Initialize blocked, created no VAULT and preserved complete provider
inventory/hashes. Local canonical VAULT remained unchanged, SHA-256:
`a27f8590ac02cd7f635bd62da541ac6bcab1411018a478b89cc27f19b692685d`.

## 33. Different-vault qualification

Passed 16 assertions. Independently created local vault A had SHA-256
`455dea623b9dfdf054f0528dc74b6e6b1aea9bfcc61258996c97a5324d4aea4c`;
provider B had the Join fixture VAULT hash in section 30. Initialize, Import and Publish
all blocked with the fixed different-vault result. Provider bytes/inventory and local
canonical bytes/session remained unchanged. No migration or replacement occurred.

## 34. Fixture restoration

Three complete physical runs passed: 62 scenario assertions plus 3 permission-inspection
assertions per run, 195 total; the final exact-source run passed all 65. Final evidence is
in ignored `.gradle/m3c-saf/` and `/tmp/m3c-device-final-exact.log`.
The runner backed up disposable children without moving the granted root. Finally it restored
children, independently pulled the files and compared complete inventory/hashes, removed
standalone instrumentation and isolated roots, and relaunched the signed app. No real user
vault was opened or mutated; no app data/grant reset or device setting change occurred.
Independent restoration evidence is `.gradle/m3c-saf/fixture-restoration.txt`:

| Restored relative path | SHA-256 |
| --- | --- |
| vault | 69a4d78bd3ff7e598bea7faf184809c5881d459e36dccbf1ddef161499c3e7b5 |
| objects-v1/911c8f2b51c0041d480014c64fdcd0a071245b8be0af289bbd025611c5b782e3 | 32d6fb5fa84fe277b487e884664145d0585e16d650a8dc6a3af6b290b4867f0b |
| objects-v1/36d7395c5d57f999d50cdc69171cd6d4e315bd05a7037d070cac85252c5104fe | e04f39f2c61b374773a2669a17b283801e2bb7e3be5584c8311e2929238ced33 |
| objects-v1/41535114e794c54b7add76dda49753b394d60622459b0bad4bd70ab21e8e7b8e | 4973f3efcf87664a15dc3f2cbf3c1be8bee16375e51036840107db5012daf559 |
| objects-v1/f1ce43301558c8736a73b8fde1f95d8d8e86dbfdd3e0cddb18f1340852a57c9a | 9ecd821cfc38938214c2360b83340a154d8f408be81186deb4fb3161effd335f |
| objects-v1/8f417177a602b3ca3d0d3ec9589355fd24ddd06a9bcabb7bd7cc75be59f17ee1 | 728be5610c81155336b851837d7b6e80ac74db326a8405f903aeb12131fb6780 |

## 35. APK verification

Final debug `--debug-probe` and unsigned release `--unsigned --no-debug-probe` checks passed.
The verifier requires new production classes and excludes standalone harness/test classes
from release. Java/core/NIO/BC, permission/component inventory, retired API exclusions and
canonical icon verification passed. Final SHA-256 values:

- Debug: `024d2a21f5cf9efe5174590ac58c1d61d754cf52e8c225ee0d4bb14512482911`.
- Unsigned release: `9a671afee3205510f4f945fdddf52cd658bbed260ab51926e2f5543c81ca7781`.
- Signed qualification release: `227a1c0e89413d54f99f37455df2c219b4a7c0af8dabf7d438b71bbcb154ff16`.

Strict rebuild reproduced the physically qualified unsigned release. An independent adb
pull of the installed APK matched the signed artifact exactly and passed `--no-debug-probe`
verification. No standalone qualification class is packaged in release.

## 36. Human Nix

The human reported **“both passed”** for `nix flake check path:.` and `nix build path:.`
on 2026-10-09 after agent validation. These are human-reported results; command logs were
not supplied. The agent ran no Nix and requested no dependency cache regeneration.

## 37. Remote CI

Not dispatched. No remote result claimed; no stage/commit/tag/release/push.

## 38. Next milestone

M3D — real Syncthing desktop ↔ Android end-to-end qualification. Current README reflects
this next step. No M3D work or historical report rewrite is included.

## 39. Final Git state

Branch remains `main`; HEAD remains `a5e0d8324b3933e0a3921980974bc35b49b4edc6`.
All 14 modified tracked files and 12 new files remain unstaged/uncommitted. The index diff
is empty; final `git diff --check` passes. Dependency/build/cache inputs, manifests and
branding have no changes. No staging, commit, tag, release, push or CI dispatch occurred.
