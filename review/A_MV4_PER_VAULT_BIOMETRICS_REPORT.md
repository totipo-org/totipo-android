# A-MV4 — per-vault biometric credentials

## Status and starting evidence

Implementation, host/artifact, required physical qualification and final human-operated
`nix flake check` PASS. All required A-MV4 milestone gates are complete. No physical biometric success is inferred from
JVM cryptography or successful harness compilation.

Classification before editing: PRODUCT_CODE, LIFECYCLE_SECURITY,
TEST_OR_QUALIFICATION_TOOLING; UI_ONLY added for selected-entry enrollment/replacement,
cleanup and diagnostic controls. No token row/layout change. Started from clean `main`,
HEAD `6eb19bece67d6ef4f8643949124e3951ca8f9a26` (committed A-MV3 baseline).
`git branch --show-current` returned `main`, `git rev-parse HEAD` returned that hash,
and `git status --short` was empty. No prior uncommitted work needed preservation.

Read AGENTS, README, Java dependency provenance, A-MV0/1/2/3 architecture/qualification
reports, biometric/inactivity, token lifecycle and stale-subscriber reports, relevant
controller/coordinator/registry/Keystore/Activity code, JVM tests, device tools, CI,
package.nix and flake.nix. Downloaded the exact read-only Java guidance at
`f0a028676c1801a10b2d8d2650bf9c24357d1fa1`; SHA-256
`338837e3563446f8ec11f7bb47c0e4f3ff5502b7608e397f9b8c4d75372715fd`.
Operation classes/state semantics, replay/threading, observation ordering, deterministic
bases, merge freshness, persistence and blocking/close were reviewed. No Java/spec,
protocol, dependency, lock, SDK, manifest, permission or build configuration change.
Java runtime remains 0.2.0, released source `d6310c177ae930df188fd4f5798622c935698b2e`.

## Existing ownership findings

A-MV3 production used PendingVaultBiometricCredentials and password-only unlock. The
retained Android implementation had one fixed alias and no-backup record, and deleted
its old key/record before preparing replacement enrollment. BiometricUnlock automatically
deleted credentials on preparation/crypto errors and checked only OPEN for enrollment.
Its local prompt generation prevented many late callbacks, but it lacked an explicit
controller request capability that remained revocable during admitted password-open.

The controller already compared authenticated `sessionVaultId()` against the biometric
expected identity before calling opened(), subscribing or requesting automatic Sync.
A-MV2 supplied immutable local/activation IDs, complete retirement and one application
selection admission. These existing boundaries were retained and strengthened.
Legacy global key/file detection remains read-only incompatible-installation detection.
No legacy key is used, decrypted, migrated, adopted or automatically removed.

## Per-entry keys, storage and envelope

AndroidBiometricCredentials is immutable in local entry ID, derived alias and separate
BiometricCredentialStorage. Application constructs it only for its captured selected
controller. BiometricUnlock also rejects a credentials/controller local-ID mismatch.
Unavailable private storage gets an explicitly unavailable adapter whose deletion
throws; inability to inspect storage cannot become a false cleanup success.

Alias: `org.totipo.biometric-password.v2.<32-lowercase-hex-local-id>`. The strict ID
mapping is injective, deterministic and independent of labels, paths, SAF or VaultId.
Each alias holds a separate non-exportable AES-256 AndroidKeyStore key, GCM/NoPadding,
randomized encryption, user authentication required, zero-second auth-per-use
`AUTH_BIOMETRIC_STRONG`, and enrollment invalidation. Platform Keys is a small test seam;
its production requirements are unchanged. No export, time window, weak biometrics,
device credential, success flag, master credential or shared password cache is added.

Storage is `totipo-local-v1/roots/<local-id>/biometric-v2` below canonical private
no-backup storage, beside `store/`. Registry lookup validates membership; all directory
ancestors and each record/marker/temp path reject symlinks and wrong kinds. No metadata
is written into canonical protocol storage or provider folders. Registry schema is
unchanged and contains no credential/VaultId. Reads stop at 2049 bytes and reject >2048.

Envelope schema 2, in order:

1. big-endian integer version;
2. canonical Java DataOutput UTF local ID, VaultId and expected derived alias;
3. exactly 12 nonce bytes;
4. big-endian ciphertext length and exact ciphertext/tag, 16–1040 bytes.

Expected local ID and alias are supplied by selection, never selected from a record.
Local ID is exactly 32 lowercase hex; protocol identity is exactly 64 lowercase hex.
Canonical decode/reencode equality rejects alternate modified-UTF spellings as well as
unsupported versions, invalid identities, wrong alias, truncation, oversize and trailing
bytes. Ciphertext permits at most the Java API's 1024-byte UTF-8 password plus 16-byte
tag. AAD uses canonical length-prefixed UTF fields and an integer version: fixed
application context, schema version, expected derived alias, exact local ID, exact
VaultId. No delimiter-dependent concatenation is used. updateAAD and doFinal occur
only following authorization of the actual fresh Cipher in the framework CryptoObject.

## Enrollment and replacement lifecycle

Explicit password unlock alone creates a bounded mutable enrollment copy. A controller
Enrollment contains local ID, activation ID, session generation, actual authenticated
session VaultId and owned PasswordBuffer. The password is not retained for future
settings. Controller enrollment epoch prevents Activity interruption during KDF/open
from producing a later enrollment offer. Expiry remains 60 seconds.

Before preparing an enrollment operation and again at completion, the controller checks
selection/retirement/Lock, OPEN state, local/activation identity, session generation,
exact owned enrollment buffer, coordinator identity and the actual immutable session
VaultId captured on the vault worker. This avoids calling coordinator methods on main
while Sync temporarily occupies IMPORTING/REQUESTING_REFRESH; a new session, Lock or
retirement clears this authority. The operation's VaultId
must also equal the captured enrollment identity. Completion is linearized against
Lock/retirement under the controller monitor, including the bounded credential write;
there are no provider, Java session or Java store calls in that write. Encryption and small credential
filesystem operations follow the prior synchronous prompt-completion model. They can
briefly delay lifecycle admission while the authorized durable transaction completes.

Replacement uses the existing entry key with a new Cipher, random nonce and separate
strong-biometric authorization. Until encryption is authorized no record is changed.
Cancel preserves the old key and record. A missing key beside a record is never recreated;
an invalidated key's exception is preserved as an unusable enrollment requiring Disable
and a new explicit enrollment. A new key beside no record is an unenrolled orphan until
authorization/persistence succeeds; cancellation/process death can leave only that one
bounded alias for later enrollment or explicit cleanup.

Durability sequence: create/force `.pending`, create-new/write/force `.next`, atomic
rename over record (no non-atomic fallback), force parent, bounded exact readback,
remove pending and force parent. Only successful return publishes enrollment success.
Any failure poisons that storage instance and retains/restores the persistent blocker
where possible. A surviving pending/temp file blocks unlock across restart. An
interrupted replacement conservatively requires Disable even when old record bytes
remain. No speculative rollback or automatic cleanup occurs. As with registry writes,
filesystem/device failures and crash-before-acknowledgement cannot provide rollback
resistance or perfect knowledge; uncertain state never receives a success message.

## Unlock and authenticated-open identity boundary

Selected locked entry + current strong-biometric availability + valid bounded record
and existing own key are required to offer unlock. The controller issues a capability
containing its original object identity and biometric epoch. Credentials prepare a fresh
single-use Cipher for the fixed selected alias. Framework success must return that exact
Cipher, with no device-credential fallback. Helper checks the capability before decrypt;
the controller checks it again on password admission.

Decrypted char[] transfers into the existing normal Java password-open path and is
wiped on rejection, failure and success. That path compares actual authenticated
sessionVaultId against the authenticated record's bound VaultId before opened(). It
then retains the existing canonical snapshot read outside the controller monitor, and
rechecks the capability after that read before subscription, OPEN presentation,
inactivity start or automatic Sync. Ownership installation and exposure are serialized
with cancellation. No token observation or automatic Sync can precede those checks.

Mismatch closes the candidate through closeOwned/discovery, keeps failed-close selection
ownership and uncertain storage evidence, and never rewrites registry or credential
identity. The record remains for explicit recovery rather than automatic deletion or
replacement. A wrong reusable password returns the existing ordinary authentication
failure. Manual password authentication remains independent and can still open the vault.

Java operation classification is unchanged: open/create/close G, session identity and
immutable descriptive projections A, observation request D, existing local authoring and
publication their existing C/E/F phases. Device credential crypto is Android-local policy.
No generic cancellation from new VaultState, new global BUSY policy or Sync algorithm
change is introduced.

## Controller, Activity, inactivity and process ownership

No biometric callback looks up a later current controller. Original helper/controller,
local ID, activation, prompt generation and controller capability remain captured.
Retirement synchronously invalidates the capability and enrollment ownership; late
success during provider drain or after B selection cannot decrypt/open B or write B's
namespace. Selection still waits for real close/provider/handoff/worker retirement.
Failed close retains the selected owner; no second session is admitted.

Activity pause/stop cancels its prompt and capability and wipes transient enrollment.
A canceled prompt submitted into an in-flight KDF is also rejected at the authenticated
open boundary. Recreated Activity may offer a new operation; it inherits no biometric
authorization, password or CryptoObject through saved state. Already-open sessions
survive ordinary rotation. Application background invalidates biometric epochs while
background time still counts toward the unchanged fixed 900000-ms inactivity deadline.
Lock retires reveal/clipboard/forms and real Java session using the existing lifecycle.
Process restart begins locked and requires a fresh strong-biometric operation.

## Disable, invalidation, fallback and future cleanup

Disable closes the helper request and cancels controller enrollment, then durably creates
`.disabled`. It attempts record/temp/pending deletion and own-alias key deletion, verifies
both, and removes the blocker only after complete cleanup. Record-deletion failure still
attempts key deletion. Either failure returns false, keeps a bounded retryable blocker,
and displays incomplete-cleanup wording. No other entry key/path is enumerated or deleted.
The open selected vault always offers Disable, including no-record/incomplete cases.

Application.cleanupRetiredEntryBiometrics is the narrow A-MV5 hook. Under the selection
lock it rejects an entry still selected, incompletely retired, failed-close or partially
constructed. Registry membership is required. It deletes only biometric material, never
canonical store data. A different selected entry may continue normally. Forget UI,
registry removal and ID recycling are not implemented.

| Outcome | Behavior |
| --- | --- |
| No record, optionally an initial orphan key | No unlock offered; explicit password/enrollment or Disable |
| Missing key beside record | No unlock/new key recreation; password, Disable then enroll |
| Invalidated/unsupported key | Preparation fails closed; password, Disable then enroll |
| Malformed/version/alias/entry mismatch or bad tag/encoding | No accepted biometric open; explicit recovery or reenrollment, never another entry fallback |
| Pending replacement/deletion marker | Locked credential across restart; explicit bounded Disable retry |
| Temporary hardware failure, lockout, non-match, cancel | Password remains; record/key retained; a new prompt can retry |
| Authenticated session VaultId mismatch | No exposed OPEN/subscription/Sync; existing candidate-close semantics; identity preserved |
| Unsafe/unavailable private paths | Unavailable adapter; no deletion success claim; recover private storage explicitly |

All entries share the same implementation regardless of provider configuration.
Attach/disconnect/rebind/retry never touches credentials. Existing provider identity,
import/publication/observation/SAF algorithms and single-session admission are unchanged.
Production startup still handles one entry and preserves the existing multiple-entry
restriction. Labels appear only as prompt context. No Change Vault UI, inactive-entry
credential management or inactive-vault Sync is added.

## Deterministic coverage

Tests use the production AndroidBiometricCredentials transaction/envelope and checked
NIO storage with only its platform key/availability port replaced by host JCE. No
production key authentication policy is weakened. Controlled callbacks, latches,
blocking UI queues and fake clocks establish cancellation ordering. New negative
assertions do not use elapsed sleep as proof of cancellation.

PerVaultBiometricCredentialsTest covers independent aliases/keys/records for equal
passwords AND equal protocol IDs; file swaps; rewritten local/alias bindings with both
foreign and deliberately equal key material; changed VaultId; arbitrary alias; fresh
single-use operations; canceled replacement; missing keys without recreation; temporary
hardware/key failures without deletion; prewrite/postrename/lost-final-ack failures;
persistent restart blockers; independent key/record deletion failures and retry;
symlink/traversal/oversize/malformed storage; and idempotent cleanup.

SecurityControllerTest adds authenticated-enrollment admission and exact identity,
foreign context refusal, same-VaultId old-session refusal, canceled replacement,
failed persistence without success, Activity/background cancellation, cancellation
while actual password-open is held inside readVault, and Activity cancellation before
an enrollment offer. Existing password, timeout, wrong password, mismatch, unavailable,
cancel, shutdown and buffer-wipe tests remain. Expected automatic deletion assertions
were changed to the explicit conservative recovery policy, not removed.

ControllerRetirementTest adds actual A/B selection with equal passwords, held provider
drain and old prompt delivery during retirement, late enrollment after replacement,
cleanup denial until complete retirement, independent B reopen after A cleanup, and
VaultId mismatch with no OPEN callback, observation owner, view or automatic scan.
Existing canonical-read responsiveness, failed close and grant/drain tests remain.
Source guards retain strong/auth-per-use/no-secret boundaries; the old AtomicFile
implementation assertion now checks stronger forced atomic storage/no-symlink/blocker
properties. Additional guards require selection-bound factory and mutation checks.

## Physical strong-biometric qualification

ADB initially had no connected device. The owner connected/unlocked the test Pixel 6a
and personally authorized the actual strong-biometric prompts. Device serial
`37311JEGR05916`, Android 17/API 37, fingerprint
`google/bluejay/bluejay:17/CP3A.261005.002.A1/2026101601:user/release-keys`.
Only `org.totipo.securityqualification` was installed. The original
`org.totipo.android` package path was recorded before testing; no owner application,
local vault, biometric enrollment or security policy was modified. No device settings
were changed; the test Activity uses only its own window KEEP_SCREEN_ON flag.

| Physical phase | Result | Evidence under `.qualification/amv4/` |
| --- | --- | --- |
| mv4-first | PASS, 68 checks | device-mv4-first-1.log |
| mv4-restart | PASS, 13 checks | device-mv4-restart-1.log |
| mv4-sync | PASS, 15 checks | device-mv4-sync-1.log |
| Existing lifecycle | PASS, 53 checks | device-lifecycle-1.log |
| Existing retirement | PASS, 26 checks on renewed run | device-retirement-2.log |

The three new biometric phases all passed on their first run with actual Keystore and strong-biometric
CryptoObjects. Cold restart force-stops the disposable process and uses the same
persisted three entries. The installed disposable APK was independently pulled and
compared with the strict unsigned release: both production DEX files and all 15 resource
entries match exactly, 17 entries total. Installed receipt:
`installed-payload-receipt.json`. Qualifier preparation also writes a signed-package
receipt in `.gradle/security-qualification/device-payload-receipt.json`. Source APK
SHA-256 is the strict release hash recorded below. No production artifact change
occurred during device qualification.

New standalone PerVaultBiometricRegression uses actual Application.selectLocalEntry
and production Activity/Keystore/CryptoObject prompts, not a second controller authority.
The qualifier compiles it into the existing non-debuggable disposable security package.
It records exact signed-package production DEX/resource hashes against the qualified
unsigned release when prepared. Historical global first/restart phases stay rejected;
SecurityRegression is retained only as historical reference and compile-compatible.

Reproduction after strict artifacts and a connected, unlocked test device:

```sh
python3 -B tools/device/qualify-security.py --serial SERIAL --prepare
python3 -B tools/device/qualify-security.py --serial SERIAL --phase mv4-first
python3 -B tools/device/qualify-security.py --serial SERIAL --phase mv4-restart
python3 -B tools/device/qualify-security.py --serial SERIAL --phase mv4-sync
python3 -B tools/device/qualify-security.py --serial SERIAL --remove
```

First phase creates A/B with distinct passwords and identities, enrolls/unlocks each,
checks password fallback, disables/reenrolls A while B remains enrolled, cancels
replacement and switches with a real prompt outstanding. C has A's password and a
different VaultId; ciphertext transplantation fails selected-entry validation. Safe
faults affect only disposable A's record/tag/key, followed by password recovery and
explicit cleanup/reenrollment. Rotation and background/foreground retain open-session
policy. Restart requires fresh real authorization independently for all three entries.
Sync phase uses a newly allocated disposable external folder and an exact system-picker
URI check, then initialization, biometric reopen and disconnect with unchanged credential
bytes. The owner must approve ONLY that unique fixture folder; no generic picker
location is accepted. Disposable external folders remain for review.

First-phase physical evidence includes both open-session rotation and fresh authorization
after locked-prompt rotation and background/foreground, actual bad-tag refusal after a
real biometric prompt, missing record/key password fallback, interrupted replacement
marker recovery and independent cleanup. Native switching cancels a real outstanding
prompt; JVM tests force exact late-success callback ordering. Existing lifecycle and
retirement runs provide additional timeout/form/clipboard/provider-drain evidence.

The real-SAF fixture is
`Documents/Totipo-AMV4-253daca06c1d46ba8588aa145ce53de2`. The owner approved that
exact system-picker folder, whose returned URI was checked before binding. Initialization,
biometric unlock while synchronized, disconnect and biometric local-only reopen all
PASS with unchanged credential bytes. The empty disposable encrypted provider fixture
remains for review; no provider deletion or live Syncthing/peer receipt is claimed.
Receipt: `.gradle/security-qualification/mv4-sync-fixture.json`.

Not safely induced: biometric-enrollment-change key invalidation and temporary sensor
lockout/unavailability. Doing so could affect the owner's device security/other apps;
no such policy/enrollment change was authorized or made. Deterministic invalidated-key
ports and actual physical missing-key recovery cover fallback, but are not presented
as sensor-enrollment invalidation evidence. These optional fault scenarios remain
explicit limits. No real physical authentication was mocked, bypassed or weakened.

## Host gates, execution counts and invalidations

Local evidence directory: ignored `.qualification/amv4/`. Baseline normal Gradle PASS,
1m33s, 89 tasks (12 executed, 77 up to date), before any source edit. Exact command:
`gradle check :app:assembleDebug :app:assembleRelease` using the pinned existing
Nix/direnv Gradle 9.8/JDK17/SDK environment. The agent has run no Nix command.

Focused run 1 PASS (17s): biometric/security/inactivity. Focused run 2 FAIL (56s):
existing canonical-read retirement test caught removal of the prior post-authentication
read, and the unavailable-adapter test still expected deletion success. The canonical
read was restored outside the monitor, and unavailable deletion now explicitly fails.
Run 2 is not accepted as qualification; tests evolved during that focused iteration.
Focused run 3 PASS (56s): ownership/isolation/source/retirement/registry suites.
Focused run 4 PASS (1m56s): broader controller suite added. Run 5 PASS (49s) checks source guards, key failure handling and final-ack recovery.
Run 6 PASS (54s) additionally holds a real coordinator refresh while enrollment
validates its immutable authenticated identity. Run 7 PASS (1s) checks final
bounded-read behavior, required field lengths and noncanonical text rejection.
No full host checkpoint was invalidated by this iteration.

Standalone harness build-only runs: 7 PASS, without packaging or installing an APK.
Device-tool unit suite: 1 PASS, 29 tests in 23.5s. Final normal PASS (2m56s),
431 JVM tests, zero failures/errors/skips, 89 tasks (21 executed, 68 up to date).
Strict offline clean PASS (3m41s), 431 tests, all 91 tasks executed, using:
`gradle --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug :app:assembleRelease`.
Independent debug `--debug-probe` and release `--unsigned --no-debug-probe` APK
verification both PASS against those strict outputs. Offline runtime/Maven boundary
PASS (1s), NIO/core 0.2.0 + BC 1.86 only. VERSION/VERSION_CODE remain 0.1.3 / 4;
packaged identity/SDK/permissions/components/backup/branding/variant/exclusion policies
all PASS. No build/dependency/manifest boundary changed.

Strict-clean debug SHA-256:
`3d9cd052d5b55abd2250351dc93cd456cb7680b6236bced488a9ac8538d41735`.
Unsigned release SHA-256:
`d82137f4268e09c0f39541ce5618e0cec69beb741cb1eefa676cc253fb3453fc`.

Host counts: baseline full 1, focused Gradle tests 7 (six PASS, one diagnostic FAIL),
standalone Gradle compile 0, final normal full 1, strict-clean 1, external APK commands
2 (one final set), runtime/boundary 1, embedded Gradle APK verifiers 0. No full gate
was repeated. Later edits to the standalone physical harness/qualifier do not invalidate
Gradle/APKs: actual source searches found no Gradle or JVM consumer of tools/device.
They do affect the final human Nix freeze because tools/ is packaged.
Physical phase attempts: 6 (five accepted PASS: 68 + 13 + 15 + 53 + 26 = 175 checks;
one failed retirement attempt). Four prepare/package/install operations and four
disposable removals PASS. Seven standalone build-only and four preparation compiles
succeeded. Human Nix: 1 final routine `nix flake check`, owner-reported PASS after
freeze. Agent Nix: 0. No intermediate APK verifier was run for this milestone.

Retirement attempt 1 crashed on its main-thread `owned_clipboard_retired` assertion;
exact scoped crash and clipboard evidence are retained as
`device-retirement-crash-1.log` and `device-retirement-clipboard-1.log`. Android's
ClipboardService recorded denial because the test app was not in focus. The prior
harness accepted the API copy return without checking actual clipboard ownership
across its recreation focus gap. ControllerRetirementRegression now waits for focus
and verifies the actual platform marker/text against the controller's owned copy
before testing cleanup. The original no-primary-clip assertion remains unchanged.
A fresh disposable install was required because the failed run had already allocated
its A/B registry fixture. Renewed retirement PASS, 26 checks. This harness-only repair
requires renewed retirement and final Nix, but no Gradle/APK rebuild; no production,
JVM or build input changed. Earlier biometric/lifecycle evidence remains valid.

Final cleanup receipt: `device-cleanup-receipt.json`. Disposable package absent,
owner package path exactly unchanged, USB stay-awake remains its original `0`.
No device security, biometric enrollment or persistent display setting was changed.

## Source freeze and human Nix

Actual package.nix/flake/CI consumers were inspected. app/src (including tests), gradle,
tools and the exact listed build/lock/version/license/branding files affect Android
qualification. Package/evaluator/cache and release-tooling inputs also affect Nix.
.git/.gradle/build/.direnv components and symlinks are excluded by package filtering.
README, AGENTS and review prose are not consumed by package/check inputs. Report-only
updates therefore do not invalidate Gradle/Nix under current rules.

Final freeze after the affected physical harness correction, 2026-10-19:

- **Production source stable.** Normal + strict-clean + both independent APK policies
  and all five applicable passing physical phases use the same unchanged production
  source/artifacts. Installed production DEX/resource equality was independently checked.
- **JVM tests stable.** All 431 pass at the strict boundary, zero failures/errors/skips;
  no later JVM edit. Host-source hash inventory still matches before and after device work.
- **Build/package configuration stable.** No build, dependency, lock, evaluator,
  manifest, SDK, version or signing configuration change; runtime/packaged boundaries PASS.
- **Qualification harness/tooling stable.** Final scripts/harnesses compile; new
  biometric first/restart/Sync and existing lifecycle/renewed retirement all PASS.
  The final retirement correction changes only its standalone harness input.

Inventory `.qualification/amv4/final-frozen-inputs.json`: 190 Git-known files, a
conservative superset of included source/evaluator/check inputs, SHA-256
`78707a34d83c03f00fa2f7338e6fb627febb41a738b2dfffa3c96b3e9e301af7`.
All five new intended files were made Git-known with intent-to-add only before the
host checkpoint. Ignored cache/build/qualification/signing state was never added.
Only three standalone tools differ from the pre-normal broad inventory; actual Gradle
consumer searches and the unchanged host-input inventory confirm no Gradle invalidation.
Those tools were finalized and physically checked before this complete freeze.

Before the human request: branch, HEAD, `git status --short`, `git diff --name-only`
and cached names were recorded. Cached names are empty, `git diff --cached --quiet`
and `git diff --check` PASS. README/report local file links PASS. The only remaining
routine gate was human-operated **`nix flake check`**, exactly once on this checkout.
The owner reported **PASS**: “nix flake check passed”. This is human-reported evidence;
terminal output/duration were not supplied. AGENTS explicitly says the agent does not
run Nix, and no agent Nix command occurred. Post-result verification found all 190
frozen files and the inventory hash unchanged. Only this excluded report was updated
to record the result; no qualification gate was invalidated.

## Security limits, deferred work and Git state

Android UID isolation, Keystore and strong biometrics are platform trust assumptions.
Non-exportability does not prevent a compromised application from stealing the reusable
password after authorized decryption. Mutable-buffer erasure is best effort on JVM/ART.
Checks do not defend against a compromised same-UID process racing private filesystem
replacement. No rollback resistance, guaranteed physical erasure or cross-device
credential portability is claimed. Changing device biometrics may invalidate multiple
entry keys under platform policy even though app-directed cleanup never affects another
entry. Disk/Keystore failure can require explicit recovery. A hung provider can still
retain A-MV2 selection indefinitely until process restart.

All required physical scenarios passed; optional sensor-enrollment/temporary-hardware
faults not safely induced are explicitly limited above. No production authentication
bypass or security-policy weakening was introduced. Clipboard clearing retains its
existing conditional, best-effort foreground-access and no-CAS limitations. A-MV5 still owns final
selection/navigation, Forget and broader recovery UX. Public multi-entry switching is
not available. Legacy clean-slate behavior and password access remain independent.

Final branch remains `main`; HEAD remains
`6eb19bece67d6ef4f8643949124e3951ca8f9a26`. Twenty-four intended changed paths:

```text
 M README.md
 M app/src/main/java/org/totipo/android/AndroidBiometricCredentials.java
 M app/src/main/java/org/totipo/android/AndroidVaultController.java
 A app/src/main/java/org/totipo/android/BiometricCredentialStorage.java
 M app/src/main/java/org/totipo/android/BiometricCredentials.java
 M app/src/main/java/org/totipo/android/BiometricRecord.java
 M app/src/main/java/org/totipo/android/BiometricUnlock.java
 M app/src/main/java/org/totipo/android/FrameworkBiometricPrompt.java
 M app/src/main/java/org/totipo/android/MainActivity.java
 D app/src/main/java/org/totipo/android/PendingVaultBiometricCredentials.java
 M app/src/main/java/org/totipo/android/TotipoApplication.java
 A app/src/main/java/org/totipo/android/UnavailableVaultBiometricCredentials.java
 M app/src/main/java/org/totipo/android/VaultSelection.java
 M app/src/test/java/org/totipo/android/BiometricRecordTest.java
 M app/src/test/java/org/totipo/android/BiometricSourceBoundaryTest.java
 M app/src/test/java/org/totipo/android/ControllerRetirementTest.java
 A app/src/test/java/org/totipo/android/PerVaultBiometricCredentialsTest.java
 M app/src/test/java/org/totipo/android/SecurityControllerTest.java
 M app/src/test/java/org/totipo/android/VaultRegistryTest.java
 A review/A_MV4_PER_VAULT_BIOMETRICS_REPORT.md
 M tools/device/ControllerRetirementRegression.java
 A tools/device/PerVaultBiometricRegression.java
 M tools/device/SecurityRegression.java
 M tools/device/qualify-security.py
```

No staged content exists (`git diff --cached --quiet` PASS); new files are intent-to-add
only. No commit, tag, release, push or remote CI dispatch occurred. Changes remain
available for review. The final human Nix PASS completes all required milestone gates;
optional hardware-fault limits remain explicitly documented above.
