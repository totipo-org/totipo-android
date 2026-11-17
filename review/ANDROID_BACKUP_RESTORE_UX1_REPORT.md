# A-BR-UX1 — Android backup and restore UX simplification

Implementation, host qualification, human Nix and targeted API37 physical UX
smoke are complete. Remaining platform/provider/accessibility limits are recorded below. No release preparation, version change, commit, tag, push or publish.
Work is confined to totipo-android.

## Scope and baseline

Classification: **PRODUCT_CODE, UI_ONLY, LIFECYCLE_SECURITY,
TEST_OR_QUALIFICATION_TOOLING**, with supporting documentation. Started on clean
`main` at `e94028ade6ae5b6650d559ae04e825f614e82ecd`:

```text
git branch --show-current: main
git rev-parse HEAD: e94028ade6ae5b6650d559ae04e825f614e82ecd
git status --short: <empty>
```

Reviewed AGENTS, README, development/user documentation, UX audit, R4A/R4B/R4D
reports, MainActivity, export/restore/Application, registry/selection/root ownership,
coordinated store and normal opening, relevant tests and device procedures,
package.nix, flake.nix and CI. Read the local exact pinned Java guidance
f0a028676c1801a10b2d8d2650bf9c24357d1fa1 (SHA-256
`338837e3563446f8ec11f7bb47c0e4f3ff5502b7608e397f9b8c4d75372715fd`),
including operation classes, replay/observation ordering, editing/merge, persistence
and close; also released 0.3.2 API_DESIGN and BACKUP_ARCHIVE at
af11553659dd8bd4157064c7e3de27bed8980ac5. Archive document SHA-256:
`6886c99a5e87f8ebfc3716c77aadb378e6c2cb24a51f110063ba2cceecbd9918`.
No Java or Desktop source was changed. Android stays **0.2.4 / versionCode 9**;
Java stays **0.3.2**, archive profile **1**.

Baseline `gradle check :app:assembleDebug :app:assembleRelease`: **PASS**, 1m49s,
90 tasks (29 executed, 61 up-to-date), using the existing Nix-managed Gradle/JDK17/
Android SDK. No agent Nix invocation. Logs, XML snapshots and freeze evidence are
local ignored files under `.qualification/ux1/`.

## Before/after screens and submissions

Counts exclude menus, transient progress and provider-specific picker subpages.
A screen is an input/review/navigation surface, including ordinary unlock.

| Journey | Before | After |
| --- | --- | --- |
| Backup after menu action | Introduction/confirmation + Android save picker (2 surfaces) | Android save picker (1 surface), after frozen preparation |
| Backup confirmations / success dialogs | 1 / 0 | 0 / 0 |
| Ordinary restore from ZIP selection | Selection page + Android open picker + password + generic confirmation/name + completion + normal unlock (6 surfaces) | Selection page + Android open picker + password/name + normal unlock (4 surfaces) |
| Ordinary restore submissions after ZIP, before normal unlock | Authenticate backup, Confirm restore, Open restored vault (3) | Restore (1) |
| Missing-history restore | Generic confirmation also discloses gaps | One conditional authenticated review, Cancel / Restore anyway; 5 surfaces total |

Backup now invokes hideCode followed by existing beginBackup directly. Capture,
freeze, digest check and READY admission still precede Android save picker launch.
No success dialog existed before, and none was added. Results remain in vault
status. Cancellation before output has no additional dialog.

The restore form has Original vault password and Vault name (optional) together.
Restore validates the name before consuming PASSWORD, authenticates the entire
frozen archive, checks conservative identity evidence, and continues once to
installation. Only an authenticated plan summary can require history review;
structural inspection and untrusted manifest claims never grant that decision.
Retry omits the ineffective name input and keeps the existing label.

## Exact backup messages

| Condition | User-facing message |
| --- | --- |
| Preparation | Preparing backup… |
| Ready/picker | Choose where to save the backup. |
| Output/readback | Saving and checking backup… |
| Verified | Backup saved. |
| Written but unverified | Backup saved, but couldn't be verified. Check the file before relying on it. |
| Failed output/readback | Backup failed. The file may be incomplete. |
| Canceled before output admission | Backup canceled. |
| Canceled after output admission | Backup canceled. A partial file may remain. Check the chosen location; the file may be empty, incomplete or complete. |
| Known gaps, appended | Some earlier history is missing from this backup. |
| Journal cleanup failure, appended | The interruption notice could not be cleared and may appear again after restart. |
| Interrupted-output marker on startup | A backup was interrupted. Check the chosen location; the file may be empty, incomplete or complete. Start a new backup if needed. |

Capture capacity/source/authentication failures retain existing bounded technical
reason distinctions. No capture, SAF grant, borrowed stream, full readback, journal,
cancellation, session or Sync behavior changed. The guide retains password,
historical/deleted entry, finite capture, independent copy, metadata and provider/
cloud verification explanations outside the mandatory flow.

## Restore continuation and lifecycle

New RestoreJourney extracts Application orchestration into an Android-independent
queued worker/main boundary. Application still owns the sole process journey,
metadata worker, registry and selection authority. Activity owns no plan, session
or canonical store and initiates no navigation from a rendering callback. Detached
archive authentication is operation class H; exact frozen publication is F, with
allocation/close/normal opening in G. This does not reinterpret state arrival as
cancellation or add a generic Java global BUSY requirement.

1. Main admission validates the optional local name and atomically consumes the
   form stage once. Rejected credentials are wiped immediately; the accepted task
   owns its submitted array only until detached authentication's finally wipe.
2. The authentication worker freezes/verifies all encrypted objects and checks all
   retained identities. Its main completion rechecks origin, foreground authorization,
   cancellation, deadline and unselected admission. No-gap/no-duplicate plans proceed
   directly to installation. Gaps wait for explicit Restore anyway; duplicates never
   allocate a second root.
3. Installation keeps the existing maintenance reservation and coordinated store.
   COMPLETE must be acknowledged by the durable INSTALLED registry update. Plan,
   gated facade, delegate and maintenance close/release before worker return.
4. Main queues final disposal while retaining management admission. Only after that
   task actually returns does its main completion recheck the originating flow,
   intent, generation, cancellation, fixed deadline, foreground and selection state.
   It consumes continuation once, removes the journey, and uses ordinary locked
   selection. Duplicate/stale worker or completion deliveries cannot consume it twice.
5. A non-secret activation-bound notice explains ordinary original-password unlock.
   Normal opening remains responsible for completed observation, authenticated
   installed inventory validation and atomic ESTABLISHED promotion. Only controller
   OPEN then displays Vault restored. for six seconds in the existing polite status
   region. No archive password is retained, cached, saved, reused or passed to unlock.

A started-Activity foreground authorization remains valid across the temporary zero-
Activity gap during configuration recreation. A real final stop revokes it and
cancels non-picker active work or pending successful handoff. Picker backgrounding
retains the original allowed behavior. Rotation never owns/restarts continuation.
The fixed 15-minute journey deadline also covers DONE awaiting handoff; the ordinary
15-minute inactivity policy is unchanged. Entered I/O/close still retains admission
until actual return. Expiry/cancel/background/stale admission cannot auto-open later.
Process restart never reconstructs an automatic continuation; INSTALLED stays in
Recovery. Existing System Back still exits the Activity, with lifecycle cancellation;
no wizard Back or picker auto-relaunch was introduced.

If continuation admission fails after installation, the installed vault is retained
for explicit Recovery → Finish restoring. No safety blocker required retaining the
old successful completion button. No registry schema/order, cryptography,
create-only/no-overwrite, exact-ZIP, operation grants, biometric or Sync rule changed.

## Recovery and error presentation

Recovery says: “Vault data kept on this installation. Choose an action to continue.”
Actions are **Recover retained vault**, **Resume restore from backup**,
**Finish restoring**, and **Open existing vault**, according to the existing state.
An unfinished entry says: “Restore unfinished. Keep the app’s data and original
backup. Choose the exact original ZIP and enter its password to continue.”
INSTALLED says: “Backup installed. Enter the original password to finish restoring.”
Retained ordinary data says: “Encrypted vault data is kept on this device. You’ll
need the vault password.” Existing scoped Forget/permission cleanup remains separate.

| Restore phase/result | Exact message |
| --- | --- |
| ZIP selection | Choose a Totipo backup ZIP. You’ll need its original vault password. |
| Reading | Reading backup… |
| Form context | Restore as a local vault on this device. The vault keeps its password. Sync and biometric unlock can be set up afterward. |
| Name helper | This name is only used on this device. |
| Invalid name | Use a vault name of at most 128 characters without control characters. |
| Authenticating | Checking backup… |
| Checked, before continuation | Backup checked. |
| Conditional review heading | Some history is missing |
| Conditional review explanation | This backup is missing some earlier history. Restoring it won't recover that history. |
| Conditional actions | Cancel / Restore anyway |
| Installing | Restoring vault… Keep Totipo open. |
| Installed / ordinary unlock context | Backup installed. Enter the original vault password to finish restoring. |
| Open, after verified promotion | Vault restored. |
| Cancel while entered work retires | Stopping restore… Waiting for storage to finish. Keep the app’s data and original backup. |
| Canceled | Restore canceled. Storage work already started may finish. Keep the app’s data and original backup. Use Recovery to continue an unfinished restore. |
| Unreadable ZIP | Could not read this backup ZIP. Choose a complete supported copy. Keep the app’s data and original backup. No new vault was created. |
| Authentication failure | Could not unlock the backup. Check the original password or try another copy. Keep the app’s data and original backup. No new vault was created. |
| Exact ZIP mismatch | This is a different backup. Choose the exact original ZIP to resume. Keep the app’s data and original backup. |
| Identity evidence unavailable/ambiguous | Existing vault data could not be checked safely. Keep the app’s data and original backup. Check Recovery before trying again. |
| Established duplicate | This vault already exists. Open the existing vault; no history has been added. |
| Retained duplicate | This vault’s data is already kept on this device. Cancel restore and use its Recovery entry. |
| Installation exception | Restore could not finish safely. Keep the app’s data and original backup. Use Recovery to resume with the exact original ZIP and password. If that fails, keep the data for recovery; do not clear it or uninstall. |

Incomplete typed outcomes keep distinct plain explanations:

- PARTIAL: “Only part of the backup was restored.”
- UNCERTAIN: “Totipo could not confirm that the backup was fully saved.”
- REJECTED: “The backup could not be installed safely.”

Each appends: “Keep the app’s data and original backup. Use Recovery → Resume
restore from backup with the exact original ZIP and password.” No enum name,
provider intent or root/allocation diagnostic appears in these messages. Existing
bounded result types drive them; no generic error framework was added. Failed
outcome notices are retained when backgrounding, rather than overwritten as success
or generic cancellation. Missing/torn storage evidence remains a refusal with
preservation and Recovery guidance, never an automatic repair or overwrite.

## Accessibility and remaining UX limits

Both inputs have persistent visible TextView labels associated via generated view
IDs and labelFor. Password widget saving/freezing/autofill stay disabled; stop,
destroy and phase rebuild clear credentials. Accepted optional name stays only in
process memory through conditional review and handoff. An unsubmitted optional name
can clear on Activity recreation; it grants no restore authority.

Unchanged phase/busy/cancellation renders preserve the existing form and focus.
Picker return builds the password form with password focus, without forcing the
keyboard; accepted submission hides the IME. Restore progress/errors use the existing
polite status view; identical status across phase rebuilds suppresses another live
announcement. Backup and restored success use changed-text status updates, with no
toast-only success. Native scroll/wrapping, IME/window insets, themed widgets and
System Back remain in use. No color-only outcome or new fixed-size form was added. The manifest retains its
existing Material Light theme; this milestone adds no system-dark theme redesign.

The later physical follow-up below records actual API37 picker, enlarged-font,
light/dark-system view rendering, keyboard, Back and framework Activity recreation
results. Source and queue tests remain separate from that device evidence. Spoken
TalkBack and other unexecuted cases remain pending. Both included device procedures
describe these cases and the simplified screens. Initial ZIP selection and all-files fallback remain manual;
ordinary unlock still requires the password again; Add Missing History remains out
of scope. Existing backup size/history/provider/platform qualification limits remain.

## Regression, artifacts and execution counts

Final normal evidence: **PASS**, 5m48s, 90 tasks (21 executed, 69 up-to-date).
All **508 tests passed**, zero failures/errors/skips; lint and both APK builds passed.
Full XML is preserved in `.qualification/ux1/final-normal-xml`.
Strict offline clean: **PASS**, 3m35s, all 92 tasks executed; the same **508 tests**
passed with zero failures/errors/skips. Full strict XML is preserved separately in
`.qualification/ux1/strict-xml`. No included edit followed either final checkpoint.
Both external verifiers passed on these strict-clean APKs:

| Artifact | Policy | SHA-256 |
| --- | --- | --- |
| Debug | --debug-probe PASS | `05d10e5b4dc13a534ad88f9700aa214903984fcab856e2063af993e80870c99e` |
| Unsigned release | --unsigned --no-debug-probe PASS | `83a02b42e1c57241424469d13f103b436fa238143527139ba633d7a65cb80cc8` |

Offline releaseRuntimeClasspath and verifyMavenBoundary: **PASS**, 1s. Graph remains
NIO 0.3.2 → core 0.3.2 → BC 1.86, with strict locks. Reviewed main/debug manifests
and packaged policy: org.totipo.android, 0.2.4/9, minSdk30/target37, USE_BIOMETRIC
only, allowBackup=false, existing launcher/enrollment filters, debug-only probes
excluded from release, branding/identity/exports/variant boundaries all pass.
Changed Markdown local-link validation (9 targets) and git diff --check: **PASS**.
No Gradle, strict, artifact or Nix evidence was invalidated after the freeze. Focused results:

- focused-1: FAIL, three BackupControllerTest assertions still expected old verified/
  cancellation copy. Compilation passed; raw log/XML preserved.
- focused-2: 52 tests, one FAIL: remaining old “unresolved ancestry” wording assertion
  in golden backup coverage. Byte identity checks remained intact; corrected to the
  new missing-history warning. All 11 then-new orchestration/Activity boundary tests
  passed. Raw log/XML preserved.
- focused-3: PASS, narrow failed method plus new journey/UI boundaries.
- focused-4: PASS, expanded affected regression selection, 1m25s.
- focused-5: PASS, journey/UI boundaries after stale form-click guard and maintenance
  release assertions, 7s.
- focused-6: PASS, false untrusted manifest gap claims cannot suppress or trigger
  conditional review; shared authentication recomputes the actual gaps, 1s.

New deterministic tests execute real Java archives, registry and coordinated storage
through queued production RestoreJourney: name validation before consumption,
rejected/accepted password wipes, duplicate submit/confirmation/completion, no-gap
install, authenticated gap review/cancel, four continuation cut points × cancellation/
background/expiry/lost admission, false manifest gap claims, presentation recreation and stale origin, exact retry
with retained label, failed INSTALLED acknowledgement, conflicting bytes/PARTIAL and
restart Recovery, and separate ordinary password opening/promotion. Activity source
checks cover direct backup/hideCode, READY-before-picker, labeled combined form,
no retry name, no old confirmation/completion screens, quiet statuses and picker
origin consumption. They are explicitly source boundary checks, not Activity tests.

Existing tests preserve golden backup byte identity, session/store ownership,
provider open/write/flush/close/readback faults, cancellation/journal, all retained
identity states, registry write cuts, blocked ingress, inventory loss/promotion
failure, immutable no-overwrite, uncertainty barriers and exact original-ZIP retry.

| Execution category | Count/result |
| --- | --- |
| Baseline ordinary full Gradle | 1 PASS |
| Focused Gradle test / separate compile | 6 / 0 (compilation included in test tasks) |
| Final normal full Gradle | 1 PASS |
| Strict offline clean Gradle | 1 PASS |
| External APK verifiers | 1 final set: 2 commands, both PASS |
| Embedded full APK verifiers in Gradle | 0 |
| Explicit offline graph/Maven boundary | 1 PASS |
| Physical runs / installs | 12 instrumentation attempts (7 PASS, 5 harness FAIL) + 1 Recovery UI run PASS / 17 isolated installs or updates |
| Human Nix / agent Nix | 1 human-reported PASS / 0 |

No full gate has been repeated for diagnosis; failures were narrowed during iteration.
No final checkpoint has been invalidated so far. At the initial host handoff, `adb devices -l` returned only its header on both
checks. The user subsequently connected/unlocked a device; the new UX1 physical
results are recorded below. Historical R4D API37 passes are not substituted for them.
API30 and the separately unexecuted cases remain pending.

## Effective source membership and freeze

Reinspected package filtering: app/src, gradle and tools plus exact Gradle/lock/
version/license/branding files in package.nix; excludes .git/.gradle/build/.direnv
path components and symlinks. package.nix, flake.nix, flake.lock, package-deps.json
also affect evaluation/cache. Flake release fileset remains unchanged. USER_GUIDE,
README, AGENTS and review are outside effective Android/release checks; report-only
finalization does not invalidate Gradle, artifacts or Nix. The two changed device
procedure files are included tools inputs, so are frozen before human Nix.

All four inputs were frozen before final normal qualification:

- **Production source stable:** export copy, combined form, authenticated conditional
  review and guarded retired-work continuation are final; focused checks pass.
- **JVM tests stable:** existing assertion updates and both new suites are final,
  including false manifest claims, stale click/completion and maintenance release.
- **Build/package configuration stable:** unchanged from starting HEAD, including
  Java/BC/toolchain, locks, versions, manifests, package/evaluator/cache definitions.
- **Qualification harness/tooling stable:** both device procedures finalized before
  freeze; no included harness executable or APK verifier changed. Later ad hoc physical
  fixtures remain ignored, outside package/evaluator inputs.

All four new intended files (coordinator, two test suites, report) are Git-known
through intent-to-add only. No staged content. Ignored caches/build/evidence stay
outside membership. Effective included-input diff SHA-256:
`a2015141b4f893b827584be6278828b661c0dcd77a68b35f8be62689939cb139`.
This covers selected source/tool inputs plus evaluator/cache files. Only excluded
report edits follow the freeze. Post-artifact comparison matches this digest and
starting HEAD; all four freeze statements still hold.
No staged file contents, commits or version/dependency/build configuration changes.

Final human-owned gate is exactly `nix flake check`, requested only after host
qualification. Agent Nix executions stay zero. User reported **nix flake check PASS** in the follow-up. Rechecked starting HEAD
and effective input digest: both match the freeze. This is a human-reported result,
not inferred from Gradle. Only excluded report and ignored device-harness files
have changed since; human Nix remains valid. Targeted API37 device execution is
complete as scoped below after the user connected and unlocked the Pixel 6a.
The exact final Git inventory follows.



## Physical API37 follow-up after human Nix PASS

The user reported `nix flake check` passed, then connected/unlocked the Pixel 6a.
HEAD and effective input digest match the recorded freeze. Agent Nix remains zero;
no included source/test/build/tool input changed afterward. No renewed Gradle,
artifact or human Nix gate is needed for these excluded report/ignored fixture edits.

Environment: Pixel 6a / bluejay, Android 17 / API37, fingerprint
`google/bluejay/bluejay:17/CP3A.261005.002.A1/2026111301:user/release-keys`.
DocumentsUI is `com.android.documentsui`, versionName17/versionCode37. The real
selected provider reported `com.android.providers.downloads.documents`, MIME
`application/zip`. API30/emulator/cloud/removable execution was not performed.

### Isolation and actual executed payload

Six new disposable package UIDs were used: org.totipo.ux1normal,
org.totipo.ux1normalfinal, org.totipo.ux1gaps, org.totipo.ux1gapsfinal,
org.totipo.ux1recovery and org.totipo.ux1lifecycle. They contain the **exact
strict-clean release DEX and resources**, copied byte for byte from release SHA-256
`83a02b42e1c57241424469d13f103b436fa238143527139ba633d7a65cb80cc8`, plus
standalone instrumentation/public fixture assets and a separate non-debug testOnly
manifest. They do not claim execution of the original production package/manifest
or debug probe. The persistent LOCAL NON-PRODUCTION signing identity was reused:
`d862bbf279d306e7062440d299440d54ecdf594e1cf8ac3046cdf5d3a0ed7f49`.
No key regeneration, production signing, owner-app update/reset/uninstall or secret
inspection occurred. No disposable data was cleared to bypass retained identity.

Per-entry production byte comparisons and source/test APK/certificate hashes are
in `.qualification/ux1/device/executed-payloads.json` and each local payload receipt.
Standalone fixtures/build scripts, logs, public ZIPs, view captures and JSON evidence
are ignored local artifacts under `.qualification/ux1/device/` and
`.gradle/ux1-device*`, not intended committed or package/check source inputs.
The versioned device procedures remain the instructions for future execution.

### Results and precise scope

- **Ordinary journey: PASS, 96 checks.** Real system-picker cancel allocated no
  vault; wrong password allocated none. One screen displayed persistent associated
  password/name labels and Restore. Invalid name was rejected before consumption;
  actual Activity recreation cleared the password and retained the same journey.
  A stale second click on the removed form was harmless. A deterministic wrapper
  around the original metadata executor held final disposal after COMPLETE and
  INSTALLED: no selection occurred while held. Actual `Activity.recreate()` during
  this handoff retained ownership; release continued to ordinary locked opening
  with an empty password widget, no generic confirmation/completion screen, and
  the submitted name. Original-password unlock completed normal observation and
  ESTABLISHED promotion, displayed Vault restored., and remained local only with
  no imported biometric record. Established duplicate restore offered Open existing
  vault without another allocation.
- **Direct backup: PASS.** Actual overflow action reached the system save picker
  without an explanation dialog; code presentation remained hidden. Real local SAF
  output/close/readback reported Backup saved. in non-modal vault status, with no
  success dialog. Re-exported exact bytes equal the Java history fixture.
- **Missing history: PASS, 61 checks.** Authenticated missing-parent fixture showed
  the exact conditional title/explanation and Cancel / Restore anyway before any
  allocation. Cancel left no restore entry. A separate submission plus Restore
  anyway installed, automatically handed off, normally opened, and exported the
  exact missing-parent fixture. The successful status retained its appended
  missing-history warning.
- **Background handoff: PASS, 20 checks.** The real Home action while final disposal
  was held revoked continuation. Admission stayed busy until the held worker
  returned; no controller was selected. The entry stayed INSTALLED in Recovery.
  Returning to the app did not open it automatically.
- **Interrupted exact-ZIP retry: PASS, 3 preparation + 51 recovery checks.** A
  disposable deterministic fixture durably installed only the bootstrap/intent
  under the actual Application registry. After force-stopping only that UID,
  cold startup exposed Recovery and did not select the attempted restore. The
  actual Resume restore from backup menu action selected the exact original ZIP
  through DocumentsUI. Retry had only the password field, retained the existing
  allocation and name, completed, handed off and normally promoted; re-export
  exactly matched history.zip. This is a controlled bootstrap-only interruption,
  not a spontaneous power-loss or media-fault claim.
- **Recovery handoff action: PASS, separate direct UI run.** UIAutomator inspection
  of the actual retained INSTALLED entry exposed Finish restoring and Resume
  restore from backup. A real tap on Finish restoring reached the ordinary locked
  screen with an empty password field and Unlock. No password was auto-submitted.
- **Accessibility/keyboard/Back: PASS as scoped, 28 + 28 checks.** At actual system
  font scale1.5, both light and dark-system modes exercised the combined form,
  persistent label associations, focus, polite status and unchanged-render view/
  focus retention. Real password-field touch showed the IME; accepted Restore
  hid it. System Back exited the non-picker journey and actual Activity stop
  canceled it; return exposed the canceled journey rather than an automatic
  selection. These are framework UI/recreation tests, not sensor-driven landscape
  rotation or a spoken TalkBack campaign. The captured native view at 1080×2400
  keeps labels, inputs and actions visible without clipping. The manifest's
  fixed Material Light theme renders identically in both system modes; no new
  DayNight theme is claimed. Captures draw the actual disposable Activity view,
  rather than bypassing FLAG_SECURE or claiming compositor screenshots.

Final independent public ZIP comparisons (all bytes, not only container validity):

| Export | Fixture | SHA-256 |
| --- | --- | --- |
| normal-final.zip | history.zip | `369b77c872a32f2474d867e0653e39b66befdfc6cbd1244da486505ba8bca95b` |
| limited-final.zip | missing-parent.zip | `6991ee0b764409728a10ee6612d6eced56207dbefa58a35611ea3d6112c25e58` |
| recovered.zip | history.zip | `369b77c872a32f2474d867e0653e39b66befdfc6cbd1244da486505ba8bca95b` |

Earlier normal/limited exports from non-PASS harness attempts also match their
fixtures; those attempts remain non-PASS as listed next.

### Every physical attempt, harness correction and execution count

12 instrumentation phase attempts: **7 full PASS, 5 FAIL**. One additional direct
Recovery UI inspection/tap run passed. 17 isolated package install/update commands;
seven standalone fixture build attempts (six successful, one javac failure), plus
one direct javac diagnostic reproduction. These are not Gradle builds or repeated
full product gates. No product-source defect or included-input fix was required.

| Log stem | Result | Evidence / correction |
| --- | --- | --- |
| normal-1 | FAIL | System picker cancel passed; new adb-copied public ZIP was not yet indexed in Downloads. No restore allocation. Touched/scanned only those new test files. |
| normal-2 | FAIL | Harness incorrectly required EditText.getFreezesText() to report false. Physical flags are save=false and parent=false while getter reports true; corrected to check saving disabled and actual password clearing on recreation. No guarantee was replaced by that getter. |
| normal-3 | FAIL | Core ordinary flow, handoff/recreation/open and real verified backup passed; final status assertion expected an isolated exact TextView label without waiting for render. Corrected to await Backup saved. within the combined non-modal status. |
| normal-4 | PASS, 96 checks | Fresh disposable UID; complete corrected ordinary scenario and duplicate refusal. |
| gaps-1 | FAIL | Conditional cancel/restore/open and actual export passed; harness exact outcome comparison omitted the expected appended missing-history warning. File equals missing-parent.zip. |
| gaps-2 | PASS, 61 checks | Fresh disposable UID; exact expected warning/status and complete conditional scenario. |
| lifecycle-1 | PASS, 20 checks | Real Home at held final disposal, retirement drain and persistent INSTALLED Recovery. |
| prepare-1 | PASS, 3 checks | Controlled actual-registry bootstrap-only interrupted restore fixture. |
| recovery-1 | PASS, 51 checks | Cold process, exact original ZIP, no retry name, same root/name, normal open and matching re-export. |
| layout-light-1 | FAIL | Labels, scale1.5, live-region retention and IME checks passed; harness checked cancellation at Activity.isFinishing before onStop delivered. |
| layout-light-2 | PASS, 28 checks | Waited for actual lifecycle cancellation. Log shows finishing with PASSWORD/canceled=false/started=1, then cancellation after stop. |
| layout-dark-1 | PASS, 28 checks | Same full layout/IME/Back scenario with system night=yes. |
| Recovery direct UI run | PASS | Actual menu labels and Finish restoring → locked empty-password Unlock screen. |

The first standalone javac attempt referred to an omitted old Back helper;
its direct diagnostic is preserved, then the fixture used the platform action
constant. Later builds add the scoped view/IME/Back diagnostics. All fixture fixes
are ignored local code. The listed failures are retained, not retroactively
counted as scenario PASS or hidden by successful adb transport exit codes.

Representative actual commands (precise per-run arguments in logs/script):

```sh
python3 .qualification/ux1/device/build.py
adb install [-r] -t --no-incremental .gradle/ux1-device*/<disposable-package>/device.apk
adb shell am instrument -w -r -e phase <normal|gaps|lifecycle|prepare|recovery|layout> ... <disposable-package>/org.totipo.android.UX1Regression
adb shell am force-stop <disposable-package>
adb pull /sdcard/Download/totipo-ux1-a2015141-<export>.zip .qualification/ux1/device/<export>.zip
adb shell uiautomator dump /data/local/tmp/totipo-ux1-<scope>.xml
```

### Device preservation and remaining limits

At the user's request, USB stay-awake was enabled during testing, with original
value **0** recorded first. Final setting is restored to **0**. Font scale is
restored to **1.0**, system night to **no**; each settings-run used finally cleanup.
All six disposable processes were force-stopped after qualification. Their data
and independent public test ZIPs are retained for review. No owner data or existing
file was removed. Owner package APK SHA-256 before/after remains
`362295c33714b98712316ca48c1152550010b4dd1d6d63746ccc23a633b00872`.
Preservation evidence: `.qualification/ux1/device/final-preservation.json`.

Remaining unexecuted cases include API30, spoken TalkBack/announcement repetition,
sensor/landscape rotation, OS late-picker delivery permutations, real full 15-minute
expiry, cloud/removable providers, and new physical PARTIAL/UNCERTAIN/storage-fault
campaigns. Deterministic host coverage and historical R4D evidence stay distinct.
This scoped UX smoke does not establish general backup/restore release readiness.
The existing backup, registry, lifecycle, cryptographic and storage invariants
needed no product change or weakened fallback. Human Nix remains PASS on the
same frozen inputs, and all work remains uncommitted.


## Exact final Git state

Branch remains `main`; HEAD remains
`e94028ade6ae5b6650d559ae04e825f614e82ecd`. All 13 paths are uncommitted.
Existing changes are unstaged; four new files have intent-to-add only. No staged
content, commit, version bump, tag, publication, push or other repository edits.
README remains concise and unchanged. Documentation/report-only updates after this
snapshot do not affect included inputs or the host qualification above.

`git status --short`:

```text
 M app/src/main/java/org/totipo/android/BackupExport.java
 M app/src/main/java/org/totipo/android/BackupRestoreFlow.java
 M app/src/main/java/org/totipo/android/MainActivity.java
 A app/src/main/java/org/totipo/android/RestoreJourney.java
 M app/src/main/java/org/totipo/android/TotipoApplication.java
 M app/src/test/java/org/totipo/android/BackupControllerTest.java
 M app/src/test/java/org/totipo/android/BackupExportTest.java
 A app/src/test/java/org/totipo/android/BackupRestoreUxBoundaryTest.java
 A app/src/test/java/org/totipo/android/RestoreJourneyTest.java
 M docs/USER_GUIDE.md
 A review/ANDROID_BACKUP_RESTORE_UX1_REPORT.md
 M tools/device/BACKUP_EXPORT_QUALIFICATION.md
 M tools/device/BACKUP_RESTORE_QUALIFICATION.md
```

`git diff --name-only`:

```text
app/src/main/java/org/totipo/android/BackupExport.java
app/src/main/java/org/totipo/android/BackupRestoreFlow.java
app/src/main/java/org/totipo/android/MainActivity.java
app/src/main/java/org/totipo/android/RestoreJourney.java
app/src/main/java/org/totipo/android/TotipoApplication.java
app/src/test/java/org/totipo/android/BackupControllerTest.java
app/src/test/java/org/totipo/android/BackupExportTest.java
app/src/test/java/org/totipo/android/BackupRestoreUxBoundaryTest.java
app/src/test/java/org/totipo/android/RestoreJourneyTest.java
docs/USER_GUIDE.md
review/ANDROID_BACKUP_RESTORE_UX1_REPORT.md
tools/device/BACKUP_EXPORT_QUALIFICATION.md
tools/device/BACKUP_RESTORE_QUALIFICATION.md
```

`git diff --cached --name-only`:

```text
```

`git diff --cached --quiet`: PASS. Final `git diff --check`: PASS.
All intended new milestone files remain Git-known; no ignored local state is included.
