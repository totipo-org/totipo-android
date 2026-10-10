# Optional strong-biometric unlock and 15-minute inactivity Lock

Status: COMPLETE. Implementation, automated/artifact gates, physical qualification,
and final human Nix gate passed. No commit, staging,
tag, release, push, CI dispatch, or agent-run Nix command.

## 1. Starting HEAD/state

Clean `main`, `edcf32b50f6b7d0d8de29ae6c9a0d3ab4f871f00`.
Prerequisites are committed: Daily Driver UX `38b251e`, row reveal/copy `9264e32`,
row stability/countdown ring `24ffe50`, reveal-repaint correction `f8371f5`, Sync
layout correction `edcf32b`. Java 0.2.0, Bouncy Castle 1.86, Vault Format v1/r19
remain unchanged. Initial `git diff --check` passed.

## 2. Security/threat model

The vault password remains canonical. Biometric convenience stores a device-encrypted
reusable representation of that password. The AndroidKeyStore key is non-exportable
and authentication-gated, but successful biometric authorization lets Totipo transiently
obtain the password required by the existing Java API. This deliberately trades some
device-local attack surface for usability; it is not equivalent to protecting solely
with the vault password. No root/session key, TOTP secret, or biometric data is stored.

## 3. Supported API/framework decision

Inspected compileSdk 37, targetSdk 37, minSdk 26. Existing released Java Flow use
already restricts vault operation to API 30+. Framework biometric convenience also
requires API 30+, which supports explicit strong-only prompt and key authentication
parameters. Older runtimes retain their existing unsupported-vault-runtime message.
No SDK level changes.

## 4. Why no AndroidX dependency

Framework `android.hardware.biometrics.BiometricPrompt`, `BiometricManager`,
AndroidKeyStore, `AtomicFile`, Handler and elapsedRealtime provide the required model.
No AndroidX Biometric or storage/scheduler dependency is necessary or added.

## 5. Strong-biometric-only policy

Availability and the prompt select `BIOMETRIC_STRONG`. The key selects
`AUTH_BIOMETRIC_STRONG` exclusively. No weak biometric or device credential fallback.
The negative prompt action is **Use vault password**, leaving Totipo password input
available. Non-match never opens the vault.

## 6. Keystore key configuration

Fixed alias `org.totipo.biometric-password.v1`: AES-256, encrypt/decrypt purposes,
GCM/NoPadding, randomized encryption required, user authentication required,
`setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG)`, and enrollment invalidation.
Zero is auth-per-use, not a 15-minute authentication-validity window. The prompt wraps
the exact initialized Cipher; success verifies that the returned CryptoObject contains
that Cipher. AAD is supplied only after that authorization, before doFinal: Keystore
updateAAD itself can consume operation authorization. Key bytes are never requested/exported.

## 7. Encrypted credential record format/location

`getNoBackupFilesDir()/biometric-password-v1`, app-private, `AtomicFile` crash-safe
write. Binary envelope: integer version 1; UTF VaultId; UTF fixed alias/key version;
12-byte IV; ciphertext length; GCM authenticated ciphertext/tag. Strict bounded parsing
rejects unsupported versions/aliases, malformed identities, truncation, oversize,
invalid sizes, or trailing bytes. Vault Format is untouched. Existing `allowBackup=false`
remains. Failed enrollment may leave an unusable orphan key after process death; next
explicit enrollment deletes the fixed alias before creating its replacement.

## 8. VaultId binding/AAD

AES-GCM AAD binds the fixed context `Totipo device-local biometric vault password`,
version, fixed alias, and exact 64-hex-character VaultId. Record input cannot choose a
different key alias. After ordinary password open, the controller compares the live
`VaultSession.vaultId()` to the credential VaultId before calling `opened()`. A mismatch
deletes the credential and closes the opened coordinator/session; no automatic identity
switch or transient OPEN snapshot is accepted.

## 9. Password buffer hygiene

No password-to-String conversion. Strict UTF-8 encode/decode uses owned byte/char
arrays and wipes temporary buffers in finally, including malformed input paths.
Decrypted bytes are wiped; recovered char[] transfers exclusively to the existing
controller authentication path and is wiped even on rejection. The existing coordinator
consumes/wipes its input before returning, so explicit enrollment alone makes a mutable
copy immediately before ordinary open. Failed open wipes it. Successful open transfers
it only to the immediate enrollment transaction, with a 60-second expiry; interruption,
Lock, cancellation, failure, or successful encryption wipes it. No password is retained
for future Settings actions. Mutable JVM erasure is best effort, as before.

## 10. Enrollment UX

When strong biometrics are currently available and no credential exists, the locked
password form offers **Enable biometric unlock on this device**. It is unchecked and
unsaved. Only successful real password open leads to a crypto enrollment prompt.
Cancellation/failure keeps the normally opened vault available and stores no credential.
Create and Join retain their existing workflows; enrollment is offered at subsequent
password unlock, avoiding a second authentication semantics path.

## 11. Locked-screen unlock UX

An active locked MainActivity offers the prompt once when a configured credential and
strong biometric are available. Password input remains available. Cancellation does
not immediately reprompt. **Use biometrics** retries explicitly. Activity interruption
cancels the outstanding prompt and discards enrollment buffers. A new locked foreground
activation may offer once again. Prompt transactions also expire after 60 seconds.

## 12. Invalidation/error/fallback behavior

Missing/invalidated keys, corrupt records, authenticated-decryption failures, and malformed
UTF-8 fail closed and retire unusable credentials. Temporary biometric unavailability,
non-match, cancellation, and lockout leave the vault locked with password fallback.
No raw framework exception/error text is displayed/logged. The normal controller retains
its existing fixed password/open failure wording. Corrupt credential removal refreshes
the password form so explicit enrollment becomes available again.

## 13. Disable behavior

Open-vault overflow offers **Disable biometric unlock**. It cancels pending biometric
work, deletes the fixed Keystore key and encrypted record, and leaves the current session,
vault, and password unchanged. Record deletion is attempted even if key deletion throws.
A disable failure uses fixed product text and can be retried; it is not reported as success.

## 14. Inactivity policy

Exactly `15 * 60 * 1000L`, no setting. Successful Create/Join/password/biometric open
starts the deadline through the common `opened()` path. Explicit Lock remains immediate.
Timeout invokes the same real `lock()` operation as manual Lock.

## 15. Monotonic clock/scheduling

Production uses the existing Application-supplied `SystemClock.elapsedRealtime()` clock.
One bounded Handler deadline is scheduled; no one-second inactivity polling. Explicit
user interaction cancels/replaces that deadline. Generation guards reject cancelled stale
timer callbacks. Wall time remains only the TOTP clock and cannot extend session lifetime.
Controller teardown cancels the deadline and requests real session closure.

## 16. Foreground/background semantics

Inspected Application lifecycle ownership and foreground auto-Sync. Backgrounding alone
does not immediately lock. Background elapsed time counts; the deadline may execute in
background. Application foreground transitions and both relevant Activities synchronously
recheck before rendering/using a resumed snapshot. A suspended process returning after
expiry gets LOCKING/locked UI before content is exposed. Short background returns keep
the session. Rotation does not constitute new unlock or reset inactivity.

## 17. Sync/reveal/editor Lock interactions

Activity `onUserInteraction` covers ordinary touch/key/navigation. Dialog Window.Callback
input dispatch also forwards user interaction because dialog input bypasses that Activity
hook. Sync, observation, provider callbacks, code ticks and clipboard expiry never reset
inactivity. `lock()` immediately clears reveal/owned clipboard presentation, invalidates
session generations, retires pending changes and cancels provider publication. Queued
commands skip authoring and release owned secret buffers. An already in-flight local
command finishes its ownership boundary; the existing worker's afterExecute then calls
the existing `closeOwned()`/`VaultSession.close()` before later worker work. Provider IPC
is never waited on. Publication guards reject retired-session callbacks.

Sensitive Add drafts are wiped; Edit/Delete/Resolve dialogs are dismissed on retirement,
and late confirmations fail controller admission. FLAG_SECURE and no-autofill/no-saved
password/code/setup policies remain; sensitive dialogs also explicitly use FLAG_SECURE.
Existing failed-close handling retains ownership and exposes only Retry Lock, never
pretending key retirement succeeded when closure failed.

## 18. Process death

No live session or session-derived secret is persisted/reconstructed. Restart begins
locked. The device-local encrypted record can recover the password after a new strong-biometric crypto
operation, then use the same password-open path and normal foreground/automatic Sync.

## 19. Automated crypto/controller/lifecycle tests

Baseline: 302 JVM tests; final suite: 334 tests. New deterministic clock/scheduler, credential-operation and
prompt seams avoid AndroidKeyStore/BiometricPrompt construction in JVM tests. Host JCE
AES-GCM tests cover non-plaintext envelope storage, fresh IVs, ciphertext/VaultId/context
modification, corrupt/truncated records and strict UTF-8/wipe behavior. Real Java/NIO
controller tests cover manual password without enrollment, explicit enrollment, cancel,
bounded expiration, ordinary biometric password-open, bad password, VaultId mismatch,
invalidated/corrupt credential cleanup, disable, unsupported/weak-only availability,
deadline reset/expiry/background checks and shutdown, including shutdown while password open is in flight.

Existing controller/reveal/lifecycle tests were run when touched. New interaction tests
cover visible and pending reveal, provider observation, local Sync import/Java observation,
and Edit/Delete/Resolve retirement with no late authoring. The prior busy-Add test now
asserts immediate Lock admission and closure after the in-flight save. The reveal fixture
separates its reveal timer from the independent inactivity deadline.

## 20. Physical biometric qualification

Confirmed test phone: Pixel 6a, ADB serial `37311JEGR05916`, Android 17 / API 37;
explicitly authorized by the user, not the primary phone. Actual strong-biometric availability,
Keystore-backed enrollment, manual Lock/open, and process-restart biometric open passed.
Eight successful human fingerprint authorizations were used: six through the original
first/restart sequence, then two to obtain a complete passing first-phase result after
correcting its conflict-status assertion.

Final focused results: lifecycle **51 checks PASS**, security first phase **39 checks PASS**,
security restart phase **29 checks PASS**. Logs: `.gradle/security-qualification/lifecycle.log`,
`first.log`, `restart.log`. Both final security phases used the same corrected harness APK
and unchanged strict-qualified release DEX/resources. The restart phase preserved the
original disposable credential across process death and a harness-only APK update.

`org.totipo.securityqualification` was an isolated, non-debuggable, test-only package with
a disposable private vault, real framework biometric crypto, and file-backed Sync transport.
It never replaced/read the real app vault. Its private deadline reflection override and
fixed TOTP wall clock exist only in the harness; production stays exactly 15 minutes.
Android speed compilation was verified for the final test package. The package was removed
after qualification; its original USB stay-awake setting (`0`) was restored and verified.

## 21. Permission/manifest changes

Only intentional addition: `android.permission.USE_BIOMETRIC`, the narrow official
framework permission. No INTERNET, storage, service, worker, camera, or background
permission is added. Existing Activity ingress/backup boundaries remain unchanged.

## 22. APK/dependency invariance

APK verifier now requires the exact biometric permission and production biometric/timer
classes, rejects AndroidX Biometric and services, and excludes security test/harness classes
from release. Existing INTERNET/storage/cloud/QR/worker/debug fixture exclusions remain.
Gradle dependencies, locks, verification metadata, package-deps.json, flake.lock and
package.nix are unchanged. Final runtime/manifest/APK checks passed (section 24).

## 23. Build-validation execution counts/timings

Ordinary full Gradle invocations: **5**:

| Gate | Result | Duration | Tests / reason |
| --- | --- | --- | --- |
| Baseline | PASS | 1m38s | 302 tests |
| Final attempt 1 | FAIL | 3m5s | 333 tests; three existing source guards needed lifecycle/framework-boundary updates |
| Final attempt 2 | PASS, then invalidated | 2m36s | 334 tests; subsequent AAD authorization-order correction required requalification |
| Final attempt 3 | FAIL | 2m1s | 334 tests; existing Delete reveal fixture used real wall time and could race a TOTP rollover |
| Final attempt 4 | PASS | 2m2s | 334 tests; fixture clock made deterministic; both APKs |

Strict offline clean invocations: **1**, PASS in **2m16s**, 92/92 tasks executed,
334 tests, zero failures/errors. Extra full passes were necessary for the recorded failures
and source correction; the process target of two ordinary gates was not met.

Focused Gradle test invocations: **12** (one aborted after obsolete fixture failures,
one compile failure for ambiguous test Error, one failing ownership run, nine passing
focused runs including the isolated Delete diagnosis and 95-test final controller check).
Standalone focused Gradle compilation: **1**. Additional release classpath-JAR packaging:
**1**; the harness was then corrected to use the strict-compiled class directory directly.
One offline runtime dependency/graph invocation. No inner-loop clean or rerun-tasks.

Development harness javac invocations: **3** (one stale compiled return-type failure,
two passes). Final harness preparation: one missing-classpath abort, then **8** successful
javac compiles. Harness signing attempts: **8** (one duplicate-ZIP failure, seven successes).
Isolated test APK installs/updates: **6**, all containing unchanged strict-qualified release
DEX; replacements were required by actual physical-only harness failures. No production
APK was signed, installed over the existing app, or released.

Physical harness invocations: lifecycle **6** (lock-screen timeout, startActivitySync hang
terminated during diagnosis, reveal fixture failure, another lock-screen timeout, incorrect
conflict-admission assertion, then PASS); security first **3** (Application startup race,
incorrect quiet-Sync expectation for the deliberate conflict fixture, then PASS); restart
**1**, PASS. Physical corrections: fresh Activity launch, deterministic/retryable reveal
fixtures, expected rejection of conflict reveal, startup serialization, conflict-aware Sync
assertion, and non-debuggable test-only packaging for release-like crypto execution.

Human Nix results: **4** reported results (one source-guard failure, three passes).
The final PASS followed all physical harness fixes because `tools/` is included in
package.nix. Human Nix durations were not provided. Agent has not run Nix.

## 24. Final Gradle/APK gates

Final normal gate PASS (2m2s). Strict offline clean gate PASS (2m16s), **334 tests**,
zero failures/errors. External wrapper verifier and debug/release APK verifiers PASS once
against strict-clean outputs. Offline releaseRuntimeClasspath listing and verifyMavenBoundary
PASS; graph remains NIO/core 0.2.0 + BC 1.86. APK permission/service/ingress/backup and
release test/debug exclusions PASS via the APK verifier. Final `git diff --check` PASS.

Strict-clean APK SHA-256 (checked unchanged after phone qualification):

- Debug: `485062e07c058f1023251c0fd788895f35ed48ea7d01bcf1202ce1992eeb743a`
- Unsigned release: `263012e4d31b2e4a48a639ec0f406685406672e055c1346d7c1ebf68669db82c`

`.gradle/security-qualification/qualification-inputs.json` records the exact original final
production/test/config/tool bytes; `qualification.diff` records tracked changes and the new app source/test files at that
boundary; `final-harness.diff` records the final three physical harness files. All app production/test/config hashes still match. Only these later physical
harness inputs differ: tools/device/SecurityRegression.java, TokenLifecycleRegression.java,
and qualify-security.py. `harness-inputs.json` records their final hashes. These files do not
enter the production APK or Gradle tests; their physical checks now pass, and their inclusion
in the Nix source was qualified by the final human Nix PASS. Report-only edits do not invalidate
build evidence. Logs/checkpoints remain under `.gradle/security-qualification/`.

## 25. Human Nix

The final human **`nix flake check path:.` PASS** was reported after all physical
harness fixes and passing device runs. Exact response: “flake check passed.” Earlier
results were one source-guard failure and two passes before later inputs changed.
No nix build was requested. No Nix command was run by the agent; results are human-reported.

## 26. Focused phone smoke

PASS on the confirmed test phone through the isolated final-release-code harness:
password open; explicit opt-in; actual strong-biometric enrollment; manual Lock/biometric
open; real prompt cancellation through CancellationSignal followed by password fallback;
process restart and biometric reopen; common automatic Sync; short background return
(300 ms) retains the same session; background timeout override retires the real session
owner before re-entry; biometric reopen; disable leaves the open session intact, removes the
record/key, and restores password-only unlock; re-enrollment; safely corrupted disposable
record fails closed, then password/re-enrollment works.

Timeout also passed with visible reveal, Add secret draft, Edit/Delete dialogs and a real
conflict Resolve dialog. Sensitive snapshots disappeared, coordinator ownership became
null, dialogs/drafts retired, and stale confirmations could not author. Host tests separately
cover pending reveal and active/blocked Sync. The production timeout was asserted as
900,000 ms; no human had to wait 15 minutes and no override exists in release output.

Biometric-open Sync scans were observed through the common path. The intentional conflict
fixture retains appropriate attention/unsynced status; the separate 51-check lifecycle run
proved quiet automatic Sync/publication after normal Edit/Delete/Resolve operations.

Diagnostics omitted the fixture password. Scoped app-UID logcat (317 lines) contained no
fixture password or setup metadata. Secret-log source guards passed. Device biometric
enrollment was not changed; safe corrupt-record behavior was tested physically and key
invalidation fallback deterministically. Disposable package removed; phone settings restored.

## 27. Deferred settings/release work

No configurable/never-lock setting, device credential/weak biometric fallback, WorkManager,
alarm/service, credential transfer, remote enrollment, vault-format/Java API change,
production signing, release workflow, tags, or publication. Detailed settings remain deferred.

## 28. Final Git state

Main and starting HEAD remain unchanged:
`edcf32b50f6b7d0d8de29ae6c9a0d3ab4f871f00`. All implementation, documentation, test,
and harness changes remain unstaged/uncommitted; no staged diff. No tag, release, push,
CI dispatch, production signing or Java/protocol change. Automated/artifact, physical,
and final human Nix qualification are complete. The disposable phone package is removed
and the original phone stay-awake setting is restored.
