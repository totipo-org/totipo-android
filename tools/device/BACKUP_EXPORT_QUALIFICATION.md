# R4A physical SAF qualification

This is an execution procedure, **not a recorded device PASS**. Record actual
R4D results in `review/BACKUP_RESTORE_R4D_ANDROID_FINAL_QUALIFICATION_REPORT.md`. JVM fake-provider
and controller passes do not establish physical SAF behavior. Use a disposable
device/profile/installation with only public test vaults. Never reset, uninstall,
overwrite or inspect a user's real vault. No Android release is authorized.

Use the final strict-clean debug/release APKs after `tools/verify-apk.py` passes.
For non-debug packaged behavior, sign the final unsigned release with the existing
local qualification identity via `qualification_signing.py`; check its actual
certificate against that identity and the installed disposable product before
any update. Follow `docs/DEVELOPMENT.md#persistent-local-qualification-signing`.
Do not generate a replacement key or reuse a production key. Record source HEAD,
included-input diff digest, APK SHA-256, signing certificate, Android/API/build,
provider name/version, storage type and every executed phase/run. The agent must
not infer a device pass from successful packaging or SDK tool availability.

Run on API 30 and a current supported Android release where available. Record
unavailable platforms/providers separately. Use public fixtures or manually created
disposable test entries; do not capture real passwords, seeds or codes in logs.

1. **Local-only empty and populated:** create two independent vaults with distinct
   passwords, no folder or tree grant. Back up each through More options. Confirm
   the native explanation, ZIP MIME picker, neutral UTC timestamped name, native
   cancel, accessible menu/button labels, large fonts and TalkBack where available.
   Export an empty vault, then one containing edits and deleted entries. Confirm
   no Sync/configuration request appears. Repeat after ordinary biometric unlock
   if that disposable entry has already been enrolled; do not change real keys.
2. **Real local DocumentsProvider:** save to new uniquely named disposable files.
   Require the saved/verified message and independently compare the full exported
   bytes/digest with a second export made without source changes. Inspect through
   shared Java `BackupArchives.inspect` (or shared client tooling), not another
   archive writer. Confirm exact lowercase `vault`, supported encrypted objects,
   manifest and no registry/biometric/private metadata. With a shared golden fixture
   source, require its exact fixed SHA-256, not merely a valid ZIP signature.
3. **Frozen selection:** while the picker is open, return/cancel as the platform
   permits. Exercise a local mutation before delivering a retained request through
   a controlled instrumented environment; the already prepared archive must remain
   identical. A new explicit backup may include the new history. Do not present
   a synthetic controller test as proof of a real system-picker scheduling branch.
4. **Lifecycle:** rotate during preparation, picker waiting, result delivery,
   output and readback. The same retained operation may proceed once after resume;
   it must never launch twice. Test Lock, 15-minute expiry (a controlled elapsed
   clock only in separate instrumentation), Change Vault, Home and return. During
   picker waiting, ordinary backgrounding alone may retain the request. Lock or
   selection/session retirement must reject its result. Backgrounding during
   output must cancel further work and report uncertain effects if open was
   admitted. Confirm token reveal/clipboard and existing biometric policies still
   behave as before; the backup task is not user activity.
5. **Failure and interruption:** with a disposable removable/cloud provider where
   available, revoke access/disconnect/fill storage during output and readback.
   Record output-open, write, flush, close and readback behavior actually induced;
   do not claim branches the provider cannot expose. Missing readback-open support
   must report written/unverified; truncated/changed/read-failed data must refuse
   verified success. Cancel during output. Inspect any remaining file manually;
   Totipo must not delete unrelated destination files. A hung provider must retain
   selection retirement until actual return or process restart.
6. **Process interruption:** kill only the disposable process during a slowed
   provider output. On restart require an interrupted-output notice, locked
   authentication and no automatic reopen/retry/write. Inspect the destination
   independently; it may be empty, partial or complete. Also restart while the
   picker is outstanding: its old result must not authorize a write. The system
   picker/provider may already have created an empty document before app output.
7. **Provider scope:** repeat suitable phases with a second real provider, and an
   available cloud/removable provider. Record unavailable providers and their
   durability/upload limitations. Successful readback is matching returned bytes,
   never proof of cloud upload, durable remote storage or peer receipt.

For R4D, continue with [restore qualification](BACKUP_RESTORE_QUALIFICATION.md)
using the saved independent ZIP and original password. Additive history import
is outside scope. Both procedures require actual execution before release readiness.
