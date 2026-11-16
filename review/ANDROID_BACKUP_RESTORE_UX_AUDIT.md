# A-BR-UX0 — Android backup/restore UX audit

Date: 2026-11-16. Classification: **DOCS_ONLY**; research and report only.

Starting branch: `main`. Starting HEAD:
`3009a4bfa82de1f2404ffea45d7e72b53cbdb309`. Starting
`git status --short`: empty. Source metadata is Android 0.2.4 / version code 9,
using released Totipo Java 0.3.2. This audits the implementation in this checkout;
it does not claim that a newly installed or publicly distributed APK was tested.

## Recommendation

Remove Backup's preliminary explanation dialog. Keep preparation before the
system save picker, followed by existing write/readback verification and a persistent,
concise outcome. There is already no backup completion dialog to remove.

For Restore, collect the optional local name alongside the original password and
use **Restore** to authorize authentication followed by installation. Show a separate
confirmation only when authenticated evidence reports missing history. After a
complete installation, continue directly to ordinary password opening; remove the
intermediate **Open restored vault** button. Keep the second password entry and
all normal-opening observation/registration checks. This is a presentation and
foreground continuation change, not permission to reuse the archive password or
promote a vault sooner.

The applied product principles are action before explanation, information at the
point of use, warnings for meaningful consequences, and completion where the user
can use the result. They reflect the requested Desktop simplification principles;
no Desktop repository or implementation was inspected. Android retains its native
SAF picker, Vaults navigation, exclusive selection ownership and lifecycle policy.
**Add Missing History remains outside this milestone.**

## Evidence and current qualification limits

Reviewed [AGENTS](../AGENTS.md), [README](../README.md),
[user guide](../docs/USER_GUIDE.md#security-and-backup),
[development policy](../docs/DEVELOPMENT.md#managed-development-environment),
[package filter](../package.nix), [flake/check dependencies](../flake.nix),
[CI](../.github/workflows/ci.yml) and these implementation/evidence sources:

| Source | Relevant behavior |
| --- | --- |
| [MainActivity](../app/src/main/java/org/totipo/android/MainActivity.java) | `explainBackup`, `maybeLaunchBackupPicker`, `renderRestore`, `entryMenu`, picker callbacks, resume/stop, password fields and Back navigation |
| [BackupExport](../app/src/main/java/org/totipo/android/BackupExport.java) | Frozen ciphertext, phase/outcome copy, cancellation, exact readback |
| [BackupRestoreFlow](../app/src/main/java/org/totipo/android/BackupRestoreFlow.java) | ZIP ingress, authentication, duplicate check, confirmation, install, exact recovery and fixed deadline |
| [TotipoApplication](../app/src/main/java/org/totipo/android/TotipoApplication.java) | Unselected admission, worker drain, disposal/open handoff, Recovery summaries and lifecycle cancellation |
| [AndroidVaultController](../app/src/main/java/org/totipo/android/AndroidVaultController.java) | Export admission, live-session/foreground/inactivity guards, provider lane |
| [VaultRegistry](../app/src/main/java/org/totipo/android/VaultRegistry.java), [VaultSelection](../app/src/main/java/org/totipo/android/VaultSelection.java), [LocalReplicaOwner](../app/src/main/java/org/totipo/android/LocalReplicaOwner.java) | Duplicate refusal, restore phases, selection/maintenance authority and promotion |
| [CoordinatedPrivateStore](../app/src/main/java/org/totipo/android/reconcile/CoordinatedPrivateStore.java), [ForegroundVaultCoordinator](../app/src/main/java/org/totipo/android/reconcile/ForegroundVaultCoordinator.java) | Create-only reservation/recovery, plan/delegate closure, normal open/observation/full authenticated capture |
| [AndroidBackupDocument](../app/src/main/java/org/totipo/android/AndroidBackupDocument.java), [BackupExportJournal](../app/src/main/java/org/totipo/android/BackupExportJournal.java) | Borrowed SAF transport and non-resumable interrupted-output marker |
| [R4A](BACKUP_RESTORE_R4A_ANDROID_EXPORT_REPORT.md), [R4B](BACKUP_RESTORE_R4B_ANDROID_RESTORE_REPORT.md), [R4D](BACKUP_RESTORE_R4D_ANDROID_FINAL_QUALIFICATION_REPORT.md) | Design decisions, host evidence, scoped physical results and outstanding coverage |

Also reviewed the local R4D inspection copies of the pinned Java operation guidance
(`f0a028676c1801a10b2d8d2650bf9c24357d1fa1`) and released 0.3.2 API design/archive
contract (`af11553659dd8bd4157064c7e3de27bed8980ac5`), as identified by
[dependency provenance](../TOTIPO_JAVA_DEPENDENCY.md). In particular: operation
classes, replay/observation ordering, editing/merge, persistence knowledge, close,
finite capture and detached restoration. These are supporting contract reads;
this report changes no shared-library behavior.

R4A/R4B's earlier compatibility warnings are historical. R4D advances the shared
runtime to 0.3.2 and records scoped physical Pixel/API37 local-SAF success,
wrong/corrupt/duplicate rejection, picker recreation, exact interrupted replay and
injected storage faults. It still records API30 execution as blocked, unexecuted
cloud/removable-provider cases, full real inactivity coverage and accessibility
campaigns, plus biometric timing caveats. This audit supplies no new device or
release-readiness evidence and does not resolve those gaps.

## Current flows

### Backup

1. Open authenticated vault → More options → **Back up vault**.
2. `explainBackup()` hides any revealed code and shows **Back up vault**, a paragraph
   about encrypted historical/deleted entries, the existing password, independent
   storage and finite local history; **Cancel** / **Choose destination**.
3. Controller admission and **Preparing encrypted backup…**; shared capture freezes
   bounded ciphertext and Android checks the resulting ZIP digest.
4. Only when READY and foreground does **ACTION_CREATE_DOCUMENT** open, with
   CATEGORY_OPENABLE, ZIP MIME type and neutral UTC timestamped filename.
5. Returned destination waits for resume and the same authenticated session;
   **Saving encrypted backup…**, then output/flush/close and exact full readback.
6. The open-vault status presents verified, written/unverified, failed or canceled
   outcome. **Cancel backup** is available in overflow while active. No Done/OK step.

The first dialog collects no information and grants no distinct destructive
operation. The explicit menu action plus system save selection supplies export
intent. It is not a password check, history-selection step or independent-storage
verification. Removing it is justified. “Open the picker directly” should mean no
preliminary dialog: retain preparation before picker launch. Launching the picker
before capture would alter ordering, lifecycle handling and empty-document exposure
without improving this simplification.

### Ordinary restore

1. Fully retire any selected vault via Change Vault. Vaults → Add vault →
   **Restore from backup** opens a selection screen with technical introductory copy,
   **Choose backup ZIP**, **Browse all files**, **Cancel restore**.
2. ACTION_OPEN_DOCUMENT (ZIP or all files), read grant only. Freeze the complete
   bounded input and structurally inspect it before requesting the password.
3. **Original vault password** → **Authenticate backup**. The field clears;
   shared Java authenticates the bootstrap and every encrypted object. Registry
   identity evidence is checked before allowing a new allocation.
4. CONFIRM presents history-object count, unresolved ancestry count where present,
   unchanged password/local-only text, optional local name and **Confirm restore**
   or **Restore limited history**.
5. INSTALLING reserves and journals the private allocation and installs exact bytes.
6. DONE says “Installation complete” followed by authenticated observation,
   registration, Sync and biometric implementation details. **Open restored vault**
   continues to the selected locked vault; **Back to Vaults** defers opening.
7. Enter the original password again using normal unlock. Initial observation and
   full authenticated installed capture must pass before atomic ESTABLISHED promotion.

The common path has two app submissions after file selection before installation,
then a third navigation button before ordinary unlock. The optional name is useful
input; its own post-authentication page is unnecessary. The history-object count
is not a token count or completeness guarantee. Generic confirmation repeats
non-destructive internal guarantees. Known missing ancestry is different: it
changes the usable result and merits an explicit informed choice.

### Duplicates and interrupted restore

An established duplicate offers **Open existing vault** and explicitly adds no
history. A retained duplicate directs users to Recovery and refuses a second
allocation. Current copy also says “Close this journey” and “a second allocation
is refused,” followed by **Cancel restore**: implementation language obscures the
next action. Unknown or ambiguous retained identity fails closed.

Interrupted installations stay in Recovery with restore intent RESERVED, ATTEMPTED
or INSTALLED. The first two cannot be selected for normal opening; they offer
**Restore interrupted backup**. INSTALLED additionally offers **Finish restore:
open**. Exact original ZIP/password replay uses the owned allocation; name input
currently appears on retry even though `install()` keeps the existing allocation
and does not rename it. Remove that ineffective input from retry.

Current failure text includes raw outcome enum names (**PARTIAL**, **UNCERTAIN**,
**REJECTED**), “owned allocation,” “torn provider intent” and “storage investigation.”
The distinctions are essential to internal decisions and evidence, but the main
screen should express result, preservation and next action. The catch-all
`authenticate()` failure currently conflates bad credentials/corruption, exact-ZIP
mismatch and retained-identity uncertainty; wording alone cannot reliably separate
those causes without bounded internal failure categories.

## Prompt decisions

| Prompt/control | Decision and reason |
| --- | --- |
| Backup introduction / Choose destination | Remove; no unique input or loss prevention. Keep explanatory guidance outside the blocking path. |
| System save/open picker | Keep; selects and authorizes the external document. Provider-owned screens remain provider-owned. |
| Original archive password | Keep; authenticates all archived encrypted material, not just container checks. |
| Optional local name | Keep, beside password for a fresh restore; never imply password/identity change. Rename later remains available. |
| Generic Confirm restore | Remove by making the password form's **Restore** action explicitly authorize the composite operation. |
| Restore limited history | Keep conditionally after authenticated gap computation. No inference of completeness when gaps equal zero. |
| Open restored vault after COMPLETE | Remove in the same live foreground journey; hand off to ordinary locked opening after worker disposal. |
| Ordinary unlock password | Keep; archive authentication is detached from a session. Retaining/reusing credentials would expand scope and change the security contract. |
| Open existing vault | Keep as explicit duplicate navigation; never silently choose/open a match or import history. |
| Exact archive selection/password on interrupted replay | Keep; retained metadata is not replay authority. |
| Cancel | Keep, without a new confirmation dialog. After storage entry cancellation is not rollback. |
| Ordinary Forget and empty-password unlock confirmations | Preserve existing policy; these are outside this simplification. |

The removed confirmations do not protect an existing vault from deletion or
replacement: those operations are prohibited by the underlying restore contract.
Actual data-loss-relevant guidance remains at failed/interrupted output, unresolved
restore/storage conditions, and the existing Forget/private-storage boundaries.

## Proposed flow and exact copy

Quoted strings below are proposed UI copy, not current behavior. Braced placeholders
are substituted with validated local display values; use proper plurals. No raw
exception, URI, root path, digest, password or token content is shown.

### Backup

**Back up vault** → prepare → system save picker → save/check → outcome in the
existing vault screen. Preserve `hideCode()` before beginning the operation.

| State | Suggested copy |
| --- | --- |
| Preparing | “Preparing backup…” |
| Awaiting destination | “Choose where to save the backup.” |
| Writing/readback | “Saving and checking backup…” |
| Verified | “Backup saved and checked. If you use cloud storage, check that upload has finished.” |
| Output closed, readback unavailable | “Backup saved, but could not be checked. Verify the copy before relying on it.” |
| Failed or readback differs/fails | “Backup could not be saved and checked. The file may be incomplete or incorrect. Do not rely on it; try a new backup.” |
| Canceled before admitted output | “Backup canceled. Totipo did not start writing the file.” |
| Canceled after output admission | “Backup canceled. Check the chosen location; the file may be empty, incomplete or complete.” |
| Restart with output marker | “A backup was interrupted. Check the chosen location; the file may be empty, incomplete or complete. Start a new backup if needed.” |
| Marker cleanup fails, appended | “The interruption notice could not be cleared and may appear again after restart.” |
| Capture capacity | “This vault exceeds the backup limit. No backup was written. See backup limits in the user guide.” |
| Source unreadable/unsupported | “Totipo could not read the vault history. No backup was written. Check storage access and try again.” |
| Invalid encrypted history | “Vault history could not be verified. No backup was written. Keep the vault data and check another independent copy.” |
| Other capture failure | “Could not prepare a backup. No backup was written. Try again.” |
| Admission busy | “Another operation is running. Try Back up vault again when it finishes.” |
| Picker unavailable | “Could not open the file picker. Try Back up vault again.” |
| Known gaps, appended to outcome | “Some earlier history is missing from this backup.” |

“Checked” must retain exactly today's size/digest/EOF and successful close meaning;
it is not remote durability, cloud receipt or an authenticated restore rehearsal.
Unverified/failed results must remain visually and accessibly distinct from success.
The cancellation claim concerns Totipo output admission: the picker/provider may
already have created an empty document. Do not say “no file was created.”

Move the dialog's useful education into the existing backup user-guide section:
“Backups use your vault password, even if you normally unlock with biometrics.
Keep the password separately. Save backups outside Totipo. A backup includes
available history and deleted entries; it may not include changes on other devices.”
Retain guide details about empty passwords, exposed archive metadata, independent
copies, finite limits and qualification. Do not replace the dialog with a mandatory
help, checkbox or first-use confirmation. A nonblocking help link is optional only
if it has a working accessible destination.

### Fresh restore

Keep the current initial file-selection page and both filters. This avoids adding
automatic picker launch/relaunch behavior to the milestone.

| Screen/state | Suggested copy and controls |
| --- | --- |
| Selection | Title **Restore from backup**. “Choose a Totipo backup ZIP. You’ll need its original vault password.” Buttons **Choose backup ZIP**, **Browse all files**, **Cancel restore**. Remove the repeated retained-Recovery explanation here. |
| Password and name | “Restore as a local vault on this device. The vault keeps its password. Sync and biometric unlock can be set up afterward.” Fields **Original vault password**, **Vault name (optional)**. Helper: “This name is only used on this device.” Buttons **Restore**, **Cancel restore**. |
| Reading | “Reading backup…” |
| Authenticating | “Checking backup…” |
| Conditional missing-history review | Title **Some history is missing**. “This backup is missing some earlier history. Restoring it will not recover that history.” Buttons **Restore limited history**, **Cancel restore**. Optional details: “{count} links to earlier history could not be found.” |
| Installing | “Restoring vault… Keep Totipo open.” **Cancel restore**. |
| Waiting for entered work/closure after cancellation | “Stopping restore… Waiting for storage to finish.” No claim of immediate rollback or safe replacement. |
| Ordinary locked opening after complete install | Short context: “Backup installed. Enter the original vault password to finish restoring.” Existing **Password** field and **Unlock** action. |
| Open after successful promotion | Brief nonblocking “Vault restored.” Show the ordinary vault screen and its existing Local only status. |

After **Restore**, validate the optional name, consume the submit once, clear/wipe
credentials as today, authenticate the whole frozen candidate and check duplicates.
With no duplicate and no computed gaps, the submitted action permits installation
without another click. With gaps, retain the authenticated bounded plan until the
conditional user decision or cancellation/deadline. Do not decide from the
structural inspection's untrusted manifest summary. The name draft is non-secret,
in memory only; do not persist password or restore authority in saved state.

After COMPLETE and the acknowledged INSTALLED registry update, dispose/close all
restore work and hand off once to the existing normal locked selection. No Done/OK
screen. Reuse the ownership checks embodied in `finishRestoreSelection`, while
rechecking foreground/current journey/cancellation at the asynchronous handoff.
Do not navigate from `renderRestore()`: rendering, rotation and repeated state
notifications must not create additional selection requests. No automatic opening
on process restart, background return after cancellation or stale completion.
If the journey ends before handoff, keep the entry in Recovery as today.

The two password submissions remain deliberately distinct: detached archive
validation authorizes installation, normal unlock authenticates the installed
vault and validates observation/registry completion. Removing the latter is a
separate credential/session-lifetime redesign, not part of A-BR-UX0.

### Recovery, duplicates and failures

Keep the **Recovery** section. Suggested section explanation:
“Vault data kept on this installation. Choose an action to continue.”
Use state-specific descriptions/actions rather than a universal explanation of
ZIP restoration on every ordinary restore screen.

| Condition | Exact suggested copy / action |
| --- | --- |
| Ordinary forgotten/retained vault | “Encrypted vault data is kept on this device. You’ll need the vault password.” Action **Recover retained vault** (renames current **Recover**, same normal authentication). |
| Interrupted restore, not INSTALLED | “Restore unfinished. Choose the original backup ZIP and enter its password to continue.” Action **Resume restore from backup** (renames **Restore interrupted backup**). |
| INSTALLED restore | “Backup installed. Open with the original vault password to finish restoring.” Primary action **Finish restoring** (same normal opening); retain **Resume restore from backup** for subsequent missing-data recovery. |
| Explicit replay selection | Title **Resume restore from backup**. “Choose the exact original ZIP used for this restore. A different or repacked ZIP won’t work.” Same two picker filters. |
| Replay password | **Original vault password**, **Resume restore**, **Cancel restore**. No name field. The same conditional limited-history disclosure applies. |
| Established duplicate | “This vault is already on this device. Opening it won’t add history from this backup.” **Open existing vault**, **Back to Vaults**. |
| Retained duplicate | “This vault is already in Recovery. Return to Vaults and use its Recovery action.” **Back to Vaults**; identify the matching local label where unambiguous. No second allocation or automatic Recover. |
| Unreadable/unsupported ZIP | “Could not read a supported Totipo backup ZIP. Choose another copy. No vault was created.” **Choose another backup**, **Back to Vaults**. |
| Archive authentication failure | “Could not unlock and verify this backup. Check the original vault password or choose another copy.” **Try again**, **Choose another backup**, **Back to Vaults**. Never say “Incorrect password” as a proven cause. |
| Proven exact retry ZIP mismatch | “This is not the original ZIP for this restore. Choose the exact original file, without repacking it.” **Choose original backup**, **Back to Vaults**. |
| Uncertain/ambiguous retained identity | “Totipo could not safely identify existing vault data. Keep the app’s data. Resolve the Recovery entries before trying again.” **Back to Vaults**. Never offer another allocation as a workaround. |
| PARTIAL | “Restore is incomplete. Data written so far has been kept. In Recovery, resume with the exact original ZIP and password.” **Back to Vaults**. |
| UNCERTAIN | “Totipo could not confirm that all restore data was saved. Keep the app’s data. In Recovery, resume with the exact original ZIP and password.” **Back to Vaults**. |
| REJECTED / generic installation exception | “Restore did not finish. Keep the app’s data. Check Recovery for an unfinished restore; use its original ZIP and password.” **Back to Vaults**. |
| Pre-install cancellation, positively no reservation | “Restore canceled. No vault was created.” **Back to Vaults**. |
| Cancellation after reservation may have begun | “Restore canceled. Data may have been saved. Check Recovery to continue with the original ZIP and password.” **Back to Vaults**, after actual drain. |
| Fixed journey expiry | “Restore timed out. Check Recovery before starting again. An unfinished restore needs its original ZIP and password.” **Back to Vaults**, after actual drain. |
| Known storage/intent/registry corruption or missing attempted root | “Restore needs storage recovery. Keep the app’s data and the original backup. Do not clear app data or uninstall Totipo. See storage recovery in the user guide.” **Back to Vaults** only where registry health permits; otherwise retain the startup recovery screen. |
| Invalid local name | “Use a name of at most 128 UTF-16 code units, without control characters.” Field error; retain the name for correction. |

These messages require stage-aware, bounded failure categories. Current broad
catch blocks cannot safely support all of them by string replacement alone.
Until causes can be separated, prefer conservative generic text over a false
password diagnosis, a false “No vault was created,” or an inappropriate retry.
Keep raw enums/acknowledgement detail in test evidence, not the main user message.
Do not add exception-message parsing or leak provider diagnostics.

The proposed retry controls mean a new explicit candidate attempt, not automatic
retry of a canceled/failed plan. Archive bytes are currently cleared after
`authenticate()` even on failure. **Try again** must therefore obtain a fresh
explicit file selection before requesting the password (with context “Choose the
backup again to retry.”); it must not imply that the disposed candidate survives.
All routes wait for actual worker/plan retirement. The guide must provide the
promised storage-recovery explanation: preserve installation data and original
ZIP, stop retrying destructive work and seek diagnosis; do not invent an automated
repair or erase option. Ordinary retained Recovery depends on this installation's
private data; exact-ZIP replay is a separate restore allocation operation. Neither
restores private data erased by uninstall/clear-data.

## Safety and Android rules that must remain invariant

- **SAF and frozen bytes:** retain CREATE_DOCUMENT for export and OPEN_DOCUMENT
  for ingress, categories/MIME fallback/scoped flags, neutral export filename,
  Application resolver and no persisted ZIP grant. No tree permission, extraction,
  new network permission or canonical storage through SAF. Freeze before using the
  candidate; provider mutation later cannot substitute bytes.
- **Backup semantics:** authenticated bounded capture of all supported local
  history, no silent truncation or Sync prerequisite; frozen output does not change
  after a local edit. Keep the pre-output journal acknowledgement, output close,
  exact size/digest/EOF readback and distinct unverified/failure/cancel outcomes.
  No destination deletion, automatic retry or remote-durability promise.
- **Restore authentication/password:** authenticate every archived object and
  bootstrap before allocation. Preserve original identity/password/ciphertext and
  bounds. Missing ancestry may be disclosed and accepted; invalid objects cannot.
  Allow an existing empty password under current rules; add no new-password or
  password-confirmation form. Preserve normal unlock's existing empty-password
  confirmation and explicit biometric opt-in.
- **Registry and partial publication:** retain RESERVED before allocation,
  ATTEMPTED before provider work, shared exact COMPLETE receipt before INSTALLED,
  and normal observation plus authenticated installed inventory before atomic
  ESTABLISHED/journal completion. PARTIAL/UNCERTAIN/REJECTED are never success.
  Lost acknowledgement is not repaired by read equality or bootstrap opening.
- **Identity and recovery:** scan all retained states conservatively before
  reservation and again at install. Unknown/malformed/ambiguous evidence refuses
  new allocation. Never overwrite/delete/repurpose existing roots, use Create/Join
  to bypass a restore, or call a duplicate match an import. Retry authenticates
  exact original ZIP bytes/digest/identity/counts and the durable provider intent,
  using the same owned allocation. Missing attempted roots and torn intent remain
  preserved for investigation; do not suggest a fresh duplicate to evade them.
- **Ownership and cancellation:** restore remains unselected until complete install
  work and closure retire. Preserve Application metadata worker, management admission,
  fair root domain and delegate lifetime. Blocking calls/KDF stay off main.
  Cancel/background/deadline revoke future admission but do not undo entered I/O.
  Keep authority until real drain; no executor replacement, forced interruption,
  late callback adoption, timeout-based release or rollback assertion.
- **Lifecycle/navigation:** retain process-local unique request codes, consume-once
  callbacks, originating operation/session/activation checks and configuration-only
  picker retention. Export selection return must pass resume, foreground and
  inactivity checks in the same live session. Backup picker is the existing expected
  background exception; other preparation/output backgrounding cancels. Restore
  permits its picker but cancels active work outside it when backgrounded; keep its
  fixed 15-minute non-resetting deadline. No password in Bundle, URI journal,
  retained flow, saved widgets or logs. Normal selection/Lock/inactivity/biometric
  rules remain authoritative; callbacks do not count as user activity.

Android's normal-opening requirement is an application policy beyond detached
Java installation. This audit preserves it. A later state emission is not a generic
cancel signal, and `requestRefresh()` returning is not completion evidence. Neither
an extra confirmation nor its removal changes those contracts.

## Accessibility and navigation review

The current password field has a visible label, `labelFor`, password input type,
autofill exclusion and disabled saved state. Forms use a scroll view with IME/system
insets. Keep those properties. The optional restore name currently has a hint and
content description but no persistent visible label; give the merged form a visible
**Vault name (optional)** label and labelFor, not just a disappearing hint.

The existing `status` widget is a polite live region, but restore notices are
separate ordinary `label(flow.notice())` views. Do not assume restore progress or
errors are announced. Use a stable polite status region for phase changes and
completion/errors, avoid announcing repeated renders, and set accessible screen
headings/focus deliberately after picker return and ordinary unlock handoff. Preserve
reading order: context, password, optional name, primary action, Cancel. Do not
force the keyboard or move focus on every asynchronous notification. Success and
warnings must be understandable without color, and no toast-only result.

Recovery overflow controls currently announce “Manage {name}.” Preserve identifiable
vault labels and expose the state-specific action names to TalkBack. Keep scrollable,
wrapping text and reachable buttons at large fonts, with comfortable touch targets,
IME accommodation and light/dark contrast. Long technical paragraphs currently add
reading burden even where they wrap correctly.

System Back currently calls `finish()` when no controller exists, including restore;
application background callbacks then cancel active non-picker restore work. Keep
that navigation model for this milestone. Do not quietly redefine Back as an
internal wizard step or auto-select the last vault. Explicit **Cancel restore** and
**Back to Vaults** remain the in-app routes, with retirement before navigation.
Normal locked opening still supports existing Change Vault/Lock/biometric behavior;
there is no restore-specific automatic unlock or automatic enrollment.

These are source findings and acceptance requirements. TalkBack, large-font,
keyboard and visual behavior have not been exercised in this audit; R4D explicitly
leaves those campaigns unexecuted.

## Expected test changes

No tests were changed or executed for this report. Future implementation should
change expectations that assert old English copy while keeping behavioral checks:

| Existing suite/evidence | Required implementation coverage |
| --- | --- |
| [BackupExportTest](../app/src/test/java/org/totipo/android/BackupExportTest.java) | Replace outcome-prefix expectations; retain all open/write/flush/close/readback/size/digest/cancel/journal fault distinctions and residue checks. |
| [BackupControllerTest](../app/src/test/java/org/totipo/android/BackupControllerTest.java) | Direct menu initiation still requires authenticated admission; preserve capture exclusion, frozen history, mutation during picker, local-only behavior, old-session/selection rejection, inactivity/background and entered-I/O drain. |
| [BackupRestoreFlowTest](../app/src/test/java/org/totipo/android/BackupRestoreFlowTest.java) | Adapt common-path CONFIRM expectations if orchestration changes; authenticated no-gap submit installs once, gaps wait for explicit acceptance, name is validated before install, duplicate/unknown identity never installs, retry keeps its old label. Retain golden byte equality, all registry write cuts, exact retry, object loss, promotion failure, password wiping and blocked-ingress/deadline tests. |
| [CoordinatedRestoreTest](../app/src/test/java/org/totipo/android/reconcile/CoordinatedRestoreTest.java) | Preserve PARTIAL/UNCERTAIN/no-overwrite/intent/location/bootstrap/object acknowledgement cases unchanged in strength. |
| [MultiVaultUiBoundaryTest](../app/src/test/java/org/totipo/android/MultiVaultUiBoundaryTest.java), registry/retirement/security/inactivity suites | Keep Activity free of direct session/storage work and restore bound to selection/maintenance authority. Update lexical guards only for intentional presentation changes; retain ordinary recovery, Forget and biometric boundaries. |
| New focused orchestration tests | Deterministic worker/main handoff: submit once, auth→conditional review/install, cancellation/expiry/background between every continuation, rotation during continuation, stale/duplicate completion, disposal before selection, no selection from a render, no promotion on DONE alone, no automatic restart/retry/unlock. Include close still entered and registry promotion lost-ack cases. |
| [Export device procedure](../tools/device/BACKUP_EXPORT_QUALIFICATION.md), [restore device procedure](../tools/device/BACKUP_RESTORE_QUALIFICATION.md) | Update expected screen sequence/action names; actual system picker and all-files fallback, canceled/late results, recreation/password clearing, complete-to-normal-open handoff, exact original-ZIP retry, retained duplicate navigation, API30/current supported device, TalkBack/large-font/dark rendering. |

Current JVM flow tests exercise real shared archive/storage behavior; source guards
exercise source boundaries. Neither is an Activity UI test. R4D's actual API37
picker/restore scenarios are valuable historical device evidence, not coverage of
these proposed changes. Use deterministic latches/fakes for new continuation races;
then scoped real UI/device evidence with final artifacts. Do not add tests that
only mirror the wording or weaken fault assertions to make a shorter flow pass.

## Scoped implementation plan (future work, not performed)

1. **Presentation change:** remove `backupExplanation`/`explainBackup` dialog plumbing;
   invoke hideCode plus existing beginBackup from the overflow action. Keep the
   READY picker path. Revise outcome text without changing outcome semantics.
2. **Restore form and intent:** move optional name into the password form for fresh
   restore only; expose **Restore** / **Resume restore**. Validate the name before
   consuming the candidate. Add one explicit submit intent for auth→install;
   retain a conditional authenticated limited-history decision. Keep failure reason
   mapping bounded and independent from display text.
3. **Foreground handoff:** after COMPLETE/INSTALLED and worker closure, continue once
   to ordinary locked selection under the same originating intent checks. Preserve
   cancellation during handoff, recovery fallback, repeated password entry and
   ordinary promotion. Carry only non-secret presentation context for the locked
   screen; no credential caching, new session API or registry schema change.
4. **Recovery wording:** shorten state summaries and rename actions consistently;
   remove ineffective retry name input. Add explicit fresh-selection retry routes
   only after drain. Keep storage-investigation states conservative and actionable.
5. **Accessible delivery/documentation:** persistent labels/status announcements,
   focus and large-font handling; update USER_GUIDE and included device procedures
   to the resulting sequence and exact terminology. Preserve qualification caveats.
6. **Qualification:** classify that implementation as PRODUCT_CODE/UI_ONLY and
   LIFECYCLE_SECURITY, plus TEST_OR_QUALIFICATION_TOOLING where harnesses change.
   Follow AGENTS: ordinary baseline once, affected tests/compile during iteration,
   final normal and offline strict-clean gates, final debug/release artifact set
   and Maven boundary review, relevant physical backup/restore/recovery/lifecycle
   smoke, then freeze all included inputs before the final human gate. Do not repeat
   unrelated Sync or full row campaigns solely for this workflow change. That later
   milestone's human gate is separate from this audit, which requests no Nix run.

Keep this one UX milestone free of dependencies/version changes, archive format or
crypto changes, registry schema migration, credential reuse, automatic recovery,
new Sync behavior and Add Missing History. If continuation safety cannot be proven,
retain the existing explicit completion handoff temporarily and report the remaining
step; do not weaken registry promotion to remove a button.

## Audit validation, freeze and final state

Only `review/ANDROID_BACKUP_RESTORE_UX_AUDIT.md` is added. Actual package selection
is `app/src`, `gradle`, `tools` and the exact Gradle/lock/version/license/branding
files listed in package.nix. Evaluator/cache inputs and flake's separate release
fileset were also inspected. `review/` is excluded and not otherwise read by those
checks. This report changes no effective Android or release-check input and
invalidates no prior Gradle/artifact/device/Nix evidence.

- **Production source stable:** unchanged from starting HEAD.
- **JVM tests stable:** unchanged from starting HEAD.
- **Build/package configuration stable:** unchanged from starting HEAD.
- **Qualification harness/tooling stable:** unchanged from starting HEAD.

Selected checks: source/copy/contract consistency review, report relative-link
validation and `git diff --check`. All PASS. This is documentation validation,
not runtime qualification. No failures or repeated full gates occurred.

| Execution count | This audit |
| --- | ---: |
| Baseline full Gradle | 0 |
| Focused Gradle (test/compile/boundary) | 0 / 0 / 0 |
| Final normal full Gradle | 0 |
| Strict-clean Gradle | 0 |
| External APK verifiers / embedded verifier executions | 0 / 0 |
| Physical harness phases/runs | 0 |
| Human Nix invocations / agent Nix executions | 0 / 0 |

No human result is required for this excluded DOCS_ONLY report. No Nix request,
production/test/tooling edit, dependency change, version bump, commit, tag or release.
Final branch/HEAD remain `main` / starting HEAD above. The report is intent-to-add
only; no staged file content. Final `git status --short`:

```text
 A review/ANDROID_BACKUP_RESTORE_UX_AUDIT.md
```

`git diff --name-only` lists only that report. `git diff --cached --name-only` is
empty and `git diff --cached --quiet` passes. Intent-to-add is retained for review.
