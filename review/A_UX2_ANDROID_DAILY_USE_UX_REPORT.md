# A-UX2 — Android daily-use UX refinement

Status: **COMPLETE** — implementation, final host/artifact/runtime gates, required
physical case evidence and final human `nix flake check` PASS. All194 frozen inputs
independently match after the human result. No commit, tag, release or push occurred.

## Starting state and classification

Started on clean `main`, HEAD `2f3f570f9ad07d1d18ea04451f0df1c650395455`.
`git branch --show-current` returned `main`; `git rev-parse HEAD` returned that hash;
`git status --short` was empty. A-UX1 was already committed. No prior work needed
preservation. Classification before edits: **PRODUCT_CODE, UI_ONLY,
LIFECYCLE_SECURITY, TEST_OR_QUALIFICATION_TOOLING, DOCS_ONLY**. Added
**BUILD_DEPENDENCY** for the verified metadata-cache repair after the first human Nix
failure; no application/build library or toolchain version changed.

Reviewed AGENTS.md, README.md, MULTI_VAULT.md, package.nix, flake.nix, CI,
A-MV2–6, A-UX1, daily-driver/row/Sync-layout design and qualification evidence,
and Activity, Application, registry/selection, token adapter, biometric and diagnostics
implementation and relevant tests/tools. Exact Java guidance was downloaded read-only
at `f0a028676c1801a10b2d8d2650bf9c24357d1fa1` into ignored evidence (SHA-256
`338837e3563446f8ec11f7bb47c0e4f3ff5502b7608e397f9b8c4d75372715fd`). Java remains
released NIO/core 0.2.0; no Java/spec repository, dependency, protocol, cryptography,
manifest, VERSION, VERSION_CODE or release metadata change. The sole later cache
response-hash refresh is documented below.

## Findings and design

The old shell repeated a large Totipo title above a smaller vault label. Selection
and management duplicated entry lists; each management row offered prominent buttons.
Fallbacks were ordinal Vault N names, and duplicate assigned labels appended ordinal
context. Create/Join did not ask for names. Biometric unlock always required a button.
The adapter preserved projection order, and Diagnostics fabricated `0.0.0-dev`.

Application still owns the sole VaultSelection, shared admission and registry. Each
Activity captures its immutable controller/activation. No controller is created for
listing. No navigation framework, extra session, authentication mechanism, password
cache, provider compatibility claim or Sync algorithm was introduced.

## Automatic biometric policy

The retained controller owns `automaticBiometricAttempted`. A new controller begins
eligible; only a successful authenticated open rearms a future lock episode. A claim
requires authoritative selection, LOCKED state, no Lock request and free local command
admission. The claim is consumed before testing availability/enrollment or preparing
crypto, so temporary unavailability cannot become a render-driven retry loop. Explicit
biometric admission also consumes the episode. Failed authentication/discovery does not
rearm it. Lock completion delivers an admission-dependent snapshot after releasing the
worker slot, avoiding a missed offer if the first LOCKED delivery arrived too early.

MainActivity launches only on its unlock surface while resumed, focused, attached,
not replacing, finishing or destroyed, with the captured controller still authoritative.
Both rendering and focus arrival can trigger the same one-shot claim. No selector or
folder-management render can prompt. Rotation and ordinary foreground callbacks retain
the consumed controller flag. Process restart establishes a new cold locked episode.

Cancel, framework authentication error/non-match, preparation failure or deadline
returns to password unlock. Explicit Use biometrics remains available when usable.
Framework non-match cancels the current prompt rather than starting another. Every
success still needs a fresh strong-biometric auth-per-use CryptoObject. Local entry,
activation, request epoch, authenticated VaultId, stale callback refusal and password/
enrollment-buffer retirement remain unchanged. No success flag authorizes a vault.

## Headings and names

The shell uses Vaults, Create local vault, Join existing vault, Sync folder or Add
token as appropriate. Locked and normal token screens use the selected vault name at
28sp, with an accessibility heading and bounded wrapping. No repeated application
header or prominent Rename button appears beside daily token access. Rename Vault
and Change Vault remain in overflow. Diagnostics retains its named dialog.

One VaultUiModel function supplies names everywhere through Application.localName /
selectedVaultLabel, including prompts, rows, confirmation and accessibility context.
Assigned labels appear alone, including duplicates. Unnamed entries use the first six
uppercase hexadecimal characters of immutable **local registry entry ID**, followed
by ` · Unnamed`. A common-prefix scan across all registered entries expands the prefix
just enough to distinguish collisions, independent of registry order. No protocol
VaultId participates, no ID changes, and no fallback is persisted. Full local ID is
available in controller Diagnostics alongside the existing public protocol identity.

## Unified Vaults architecture

Your vaults contains established entries. The main row is the selection action; its
text includes the concise existing local-only/configured/access/recovery summary.
Last selected remains highlighted without activation at multi-vault startup. A trailing
named overflow owns Rename and Forget for exactly that entry. Add vault is separated
by space and a heading, with Create Local Vault and Join Existing Vault actions.
Conditional Recovery contains retained/incomplete entries and contextual Recover,
Resume Create/Join, allocation recovery, interrupted Forget and retained-permission
cleanup actions. No empty recovery section or separate management mode remains.

Zero/one/multiple startup policy and all pending/missing/detached/corrupt distinctions
remain unchanged. Change Vault still clears sensitive presentation and waits for
actual controller/session/provider/handoff retirement before unselected presentation.
Back never reconstructs an old activation. Forget still uses its journaled scoped
cleanup and keeps canonical encrypted data; recovery still needs password authentication.
Selected-vault Rename is a guarded metadata-only write; Forget still requires retirement.

## Create and Join naming

The existing forms include an initially empty optional Vault name, example placeholder
and device-only explanation. Non-secret draft text and its manual-edit bit belong to
Application by activation, not a credential Bundle. Fresh Create/Join starts empty;
explicitly resumed named allocations retain their existing assigned label. Whitespace-
only input becomes null; nonempty text uses the unchanged registry Unicode and 128
UTF-16-unit validation. Names need not be unique and are never paths/aliases/identity.

Immediately before Create/Join admission, Application validates the captured selected
controller and persists the name with registry.rename on the **existing reservation**.
Failure stops submission before handing over credentials. No name failure allocates a
replacement entry, retries uncertain creation or removes evidence. Existing reservation,
attempted-creation, authentication, duplicate and uncertain-promotion semantics remain.

Join asks the provider for COLUMN_DISPLAY_NAME and directory MIME metadata using
DocumentsContract's tree-document identity. It never parses opaque URI path segments.
Missing/invalid/oversized metadata supplies no suggestion. The existing metadata worker
performs the read; a main-thread callback checks captured controller and draft identity,
then notifies the existing selection presentation observers. This survives Activity
recreation without retaining its view. Manual edits, including an explicit clear, win
against both late results and repeated suggestions. The draft is retired on Change Vault.
A provider metadata call can delay other queued metadata work if that provider hangs;
no timeout is treated as proof that provider work completed. Process restart remains
available, and no session/selection ownership is released based on a timeout.

## Token ordering and installed version

Only detached display items are sorted: issuer lowercased with Locale.ROOT, then account,
then full stable token ID. Empty issuer uses account as its primary available value;
empty values have a deterministic position. Case-only/duplicate combinations use ID
as tie-breaker. Conflicts and multi-alternative items use neutral empty display keys
and ID, never an arbitrarily chosen alternative. Existing conflict/needs-attention
presentation, tombstone filtering, search/reveal/copy and operation authority remain.
Filtering composes with the same comparator; projection/history/storage ordering is
untouched. No configurable sorting was added.

Diagnostics appends Android PackageManager metadata at the shell boundary: installed
versionName and long versionCode (legacy code fallback on older Android). Missing name
or package metadata is honestly Unavailable. The controller has no package API or
hardcoded app version. Source VERSION/VERSION_CODE remain unchanged.

## Lifecycle and security preservation

Java classes remain A for immutable display sorting/projection, B for unchanged TOTP,
C/E/F for unchanged authoring/publication, D for unchanged observation, G for existing
open/Create/Lock/retirement, and H for existing independent Join authentication.
Automatic prompting changes Android presentation policy only. New VaultState arrival
is not a cancellation signal. No global BUSY expansion, rebasing, observation barrier,
protocol store mutation or transport ownership change is introduced.

The exact owning controller still receives every old callback. Retirement revokes
prompt/request authority and waits for actual resource closure; no second controller
or reusable biometric authorization appears. Inactivity remains 900000 monotonic ms;
background time counts. Password fallback, independent equal-password credentials,
SAF result activation checks, grant inventory, duplicate recognition and recovery
transactions retain their existing implementations and regressions.

## Deterministic validation

Ignored logs/receipts: `.qualification/a-ux2/`; disposable runner artifacts:
`.gradle/ux2-security-qualification/`. All builds use pinned Nix/direnv Gradle9.8.0,
JDK17 and SDK API37 / Build Tools36.0.0. The agent has run no Nix.

- Baseline ordinary full PASS, 1m28s, 89 tasks (9 executed / 80 up to date), before edits.
- Focused standalone compile PASS, 3s.
- Focused run1: 57 tests, one source-guard failure. A new AlertDialog `.create()`
  matched the existing prohibition against vault creation in overflow. Dialog construction
  changed to `.show()`; the guard was preserved. All other cases passed.
- Focused run2 PASS, 197 tests, 2m04s: SecurityControllerTest, PerVaultBiometricCredentialsTest,
  ControllerRetirementTest, AndroidVaultControllerTest, RowRevealCopyTest, VaultManagementTest,
  DailyUseUxTest, MultiVaultUiBoundaryTest, DailyDriverUxTest, BiometricSourceBoundaryTest.
- Focused run3 PASS, 1s: UX/name/metadata and shell guards after delivering suggestions
  through Application observers.
- Focused run4 PASS, 1s: UX/name/shell guards after older-API compatibility review and
  adding name-write uncertainty/recovery association coverage.
- New deterministic cases cover one automatic claim, unavailable/unenrolled consumption,
  cancellation/explicit retry/new lock, preparation/authentication failure, helper recreation,
  foreground/inactivity and retirement with late success. Existing per-entry, stale callback,
  credential corruption/VaultId, buffer retirement and security/Sync suites are retained.
- New name/order tests cover prefix collisions and list-order invariance, duplicate labels,
  Unicode/UTF-16 validation, manual suggestion/clear ownership, lost rename acknowledgement,
  attempted Create association, case/duplicate/missing/Unicode sort keys, neutral conflicts,
  filtering composition and unchanged source list. Shell guards retain source boundaries.
- Device-tool host suite PASS, 29 tests, 32.859s.

Final normal PASS, 3m02s, 89 tasks (21 executed / 68 up to date),
461 JVM tests / 43 suites with zero failures, errors or skips. Normal host source
hashes and suite receipts are recorded before strict-clean. Strict offline clean PASS, 3m27s, all 91 tasks executed; 461 tests / 43 suites,
zero failures/errors/skips. Both external APK policies PASS. Offline release runtime /
Maven boundary PASS, 1s; only NIO/core0.2.0 and BC1.86 plus locked constraints.
Manifest remains biometric-only with unchanged components, backup, SDK and variant
boundaries. VERSION / VERSION_CODE remain 0.2.0 / 5.

Final strict APK SHA-256:

- Debug: `1be2fad44960ec3c3bf5fa75af326fcaec9942a5974211bc311a8d5ddb301199`.
- Unsigned release: `828ed981b0e701eec450da4e0694c2d0ffa2a893f26b1e3b045d2d9bedebbd07`.

Every production/JVM hash still matches the final normal checkpoint. No Gradle/APK
pass has been invalidated by later harness-only work. Documentation: 15 local links
resolve, and git diff --check passes.

## Physical qualification

The connected Pixel6a is serial `37311JEGR05916`. Existing
`org.totipo.securityqualification` was found installed and preserved. The existing
qualifier now accepts `--ux2` to use a separate fresh **org.totipo.ux2qualification**
UID and artifact directory. It refuses an existing package on prepare, copies exact
production DEX/resources and records hashes. The disposable manifest carries reviewed
source versionName/versionCode so the actual installed metadata can be tested.
An attached Activity-only 1.5 font/night-mode override supports human readability
review without changing device-wide settings; detached real-Activity rendering also
covers 320/640dp, fonts1.0/1.5/2.0 and both system-night configurations.
No owner application installation, private data, SAF grant, biometric credential,
security policy or existing external folder is modified.

New DailyUseUxRegression uses the actual production Activity/Application and native
menus/dialogs; strong biometric success always requires the human's platform prompt.
Phases cover fresh named Create/enrollment, cold single-vault automatic offer/cancel,
repeated recreation/foreground without loops, explicit retry, new lock episodes,
independent A/B selection and prompt retirement, token sorting/filtering, Diagnostics,
font/width/light-theme layout, real SAF folder metadata suggestion with manual edit/
clear, authenticated Join on the same reservation, contextual Forget and password
recovery. Host-authored public fixture uses released Java through existing InboundFixture;
only a new uniquely named disposable folder is exported, with no peer-replication claim.
Historical selector/biometric/retirement harness controls were adapted to unified entry
rows/menus; underlying assertions remain. No broad UI automation framework was added.

Physical attempt1 stopped before UX assertions: the instrumentation thread read
Application.registry before Application.onCreate completed. It now synchronizes with
main first, matching the existing harness pattern. Only qualification code changed;
the same fresh reservation is retained and no production/JVM/APK pass is invalidated.
Output is preserved in device-first-1.log; corrected package update succeeded. Attempt2 demonstrated fresh Create, optional
name and recreation, then did not advance after a native popup tap. It was explicitly
stopped without touching canonical data. The harness now waits for native menu idle
and refreshes its node, matching existing tools. Attempt3 resumes only that exact
single named unenrolled fixture; it never allocates or recreates data. Lock and the
unenrolled password fallback then passed, reaching the actual enrollment prompt.
Attempt3 passed strong enrollment and automatic prompting after Lock, then Android
refused app-targeted Instrumentation touch injection into the system biometric window
(IllegalArgumentException). Native menu/cancel touches now use the existing UiAutomation
input API, with pointer-down/up acknowledgement; this never injects biometric success.
Attempt4 resumes the exact enrolled fixture and PASSes 16 checks including native
platform cancellation and password fallback. Fresh Create/name/rotation evidence from
attempt2 and strong enrollment evidence from attempt3 use the same final production
artifact; no data was recreated to obtain a pass. Cold phase PASS, 76 checks on its first attempt, with actual human strong-biometric
unlocks for independent A/B, explicit retry, new Lock, selected-vault entry and an
expired-inactivity foreground return. Three locked Activity recreations and a routine
foreground return did not restart prompting; Application retirement canceled an
outstanding A platform prompt before selecting/authenticating B.

UI attempt1 passed actual token ordering/filtering, then its test-only font override
crashed in onActivityPreCreated because resources were already read. The override now
runs in Instrumentation.newActivity before attachment, still returning the actual
production MainActivity. The same four public tokens are reused without duplicate
creation on retry. No production or JVM input changed. UI attempt2 passed actual
attached 1.5 font. Human review: “Its all readable, but maybe a bit too big.” This
was the deliberate accessibility test override; the harness restores ordinary sizing
before continuing. No global font/night setting changes or production typography edits
were made in response to that enlarged-size observation.
UI attempt2 subsequently passed actual reveal/copy, conditional owned clipboard
retirement on Lock and human enlarged-font review. It initially queried Diagnostics
before the dialog text appeared. The corrected wait and an independent shell-version /
PackageManager comparison passed on attempt3, displaying **App: 0.2.0 (5)**.
Attempt3 also passed all 12 detached width/font/night configurations and actual SAF
folder-name prefill, manual-edit retention and manual clear. Its tap then hit a
nonclickable Join heading sharing the action's text. Click lookup now requires a
clickable enabled control. Attempt4 resumes the exact pending entry and existing
grant (no new reservation), and authenticated Join persisted **Joined UX2** on that
same entry. Its immediate token assertion preceded initial Sync/observation delivery.

A narrow **ux2-finish** continuation now waits for the positive three-token delivery
instead of equating OPEN with observation completion. It reopens the exact authenticated
Join fixture and **PASSes 67 checks**, including joined heading/tokens, contextual
Forget, byte-for-byte canonical preservation, recovery without a biometric credential,
password authentication, scoped entry actions, selected-vault Rename and contextual
Rename. Renaming removes the fallback and preserves all four B tokens/identity.
These are cumulative passing case-level results on the same final production artifact;
UI attempts1–4 are preserved diagnostic runs, not claimed as whole-phase PASSes.

Accepted phase markers: ux2-first16, ux2-cold76, ux2-finish67. Additional passing
fresh-Create/enrollment, font/normal-UI, clipboard, Diagnostics and real SAF naming
assertions are recorded in their earlier partial runs. The focused continuation avoids
recreating established fixtures or repeating unrelated biometric/UI setup just to turn
a diagnostic whole-phase marker green. All requested device cases have positive
assertion evidence or the explicit human visual result; forced late callbacks and
hardware-unavailability faults retain deterministic-test scope.

Installed disposable payload was independently pulled and compared: **all17 production
DEX/resource entries match the strict release**, source SHA above. Installed test APK
SHA-256 `c786ea98d60a9bba4da8b3268fa640603e77c872c41808b759cb7d1aa155c2f7`.
Only this run's org.totipo.ux2qualification was removed afterward. The owner package
and prior org.totipo.securityqualification paths are exactly unchanged; font_scale1.0
and system night mode no are unchanged. No global device setting or owner credential,
vault, SAF grant or external folder was modified. Only the new public encrypted Join
fixture remains at `Documents/Totipo-UX2-d477e94626fd43899a812ac3ff6516d0/Work-TOTP`
for review. This demonstrates SAF naming/Join, not peer replication or compatibility.

Actual TalkBack speech and induced hardware lockout/enrollment-change invalidation were
not exercised. Native accessibility names, labels, focus and touch bounds were checked;
human readability was reviewed at1.5 scale. The normal Activity runs at the original
system scale, and detached layout checks include fonts1.0/1.5/2.0, 320/640dp, both
night configurations and the unchanged explicit light theme. A final fresh disposable **ux2-navigation** smoke PASSed19 checks: actual soft
keyboard/name focus, Back dismissing the IME without leaving setup, complete retirement,
Back from Vaults followed by relaunch without an old session, new locked activation,
and normal-size heading after rotation. The first navigation attempt stopped at the
unlocked-device prerequisite after the phone relocked between installations; the human
unlocked it and the unchanged rerun passed. Its independently pulled17 production
entries also match the same strict release. The second disposable package was removed;
owner/prior qualification package paths still match the starting receipts.

Physical inactivity uses
only the existing harness-style deadline override; the production15-minute constant
is independently asserted, not shortened. All platform biometric successes were human
authorizations of the real strong CryptoObject prompt. Actual sensor lockout/enrollment-change
faults were not induced because they affect device security outside this package.
No full external Syncthing campaign is needed for this milestone.

## Final qualification, freeze and counts

Actual package filtering includes app/src (including JVM tests), gradle and tools plus
the listed Gradle/metadata files; evaluator/cache and release-check inputs also matter.
Root README, MULTI_VAULT and review prose are excluded and not consumed by checks.
Report-only updates therefore do not invalidate Gradle/APK/device/Nix evidence.
All five new files are Git-known through intent-to-add only; git diff --cached --quiet
passes, with no staged-content diff. Ignored logs, cache and signing state are excluded.

Freeze recorded 2026-10-24T02:39:26.846534+00:00 in freeze.json and frozen-inputs.json:
**194 included/evaluator/release-check input hashes**, inventory SHA-256
`9fe2af8513653b5e6b074c827e469367d8bf2be9eeaf4492b94bfe3e7ffba15a`. Status, unstaged names and empty staged-content names
are recorded verbatim; git diff --cached --quiet and git diff --check PASS.

- **Production source stable.** Final normal/strict and both APK policies pass;
  all production hashes remain unchanged, and installed physical DEX/resources match.
- **JVM tests stable.** All461 pass, zero failures/errors/skips; unchanged since both
  final host gates, with the normal input inventory independently rechecked.
- **Build/package configuration stable.** No production build/dependency/metadata
  change; Maven graph, manifest, identity/version and artifact boundaries pass.
- **Qualification harness/tooling stable.** Corrected affected physical cases and
  final29-test host suite pass. All standalone tools are included in this freeze.

Only excluded report/result updates are planned after freeze. No included input may
change without the qualification ladder's actual-dependency invalidation response.
Human `nix flake check` attempt1 **FAIL**, user-reported: Google's unversioned
Maven group master-index XML changed from
`sha256-A50cGTtYF/oHm8+QxLqSWihc1xpXg6IUkYEozsBhD08=` to
`sha256-FGidVTJOBhR6YirTc06mZEsz6r9DREFsYskItxfH4r4=`. The human added
“probably the google thingy again.” Full supplied error is retained in human-nix-1.json.

Followed README's explicit metadata-response repair. Independently fetched both
`https://maven.google.com/master-index.xml` and its canonical
`https://dl.google.com/dl/android/maven2/master-index.xml`; bytes are identical,
10,337 bytes, expected metadata root with337 group entries, and the hash matches the
reported actual value. Updated exactly the one `master-index` → `xml` leaf in
package-deps.json; parsed-JSON equality proves every other cache leaf unchanged.
The other mutable Google Play SDK snapshot was also fetched read-only, validated as
gzip and independently matched its existing pinned hash. No further cache edit was
needed. No locks, verification metadata, dependency/toolchain versions or APK input changed.
This package/evaluator-only edit requires renewed human Nix, with no Gradle/APK/device
rerun under the actual-dependency invalidation matrix.

Refreeze after this repair: **2026-10-24T02:42:08.418689+00:00**, same194 inputs, inventory SHA-256
`ab9db7fb4deb4d4d263e0f366707d8e74fbd35f9a3702c8d5cc11a38f1fc7f6f`. The other193 frozen hashes are identical. Previous freeze/evidence is
preserved in freeze-1.json/frozen-inputs-1.json. Production source, JVM tests and
qualification tooling remain stable with their recorded passes; build/package cache
configuration is stable after this one-leaf verified repair. Empty staged-content diff
and git diff --check PASS. Renewed human `nix flake check`: **PASS**, user reported “nix flake check passed”
at 2026-10-24T02:48:24.496108+00:00, recorded in human-nix-2.json. Terminal output and duration
were not supplied; this is human-reported gate evidence. All194 refrozen input hashes
were independently verified unchanged afterward. Agent Nix:0. Only this excluded
report and ignored receipts changed after refreeze, so the PASS remains valid.

| Invocation category | Count so far |
| --- | --- |
| Baseline full Gradle | 1 PASS |
| Focused Gradle compile | 1 PASS |
| Focused Gradle tests | 4 (first diagnostic FAIL; next three PASS) |
| Final normal full | 1 PASS |
| Strict offline clean | 1 PASS |
| External APK verification | 2 PASS (one debug/release set) |
| Embedded Gradle APK verifier | 0 |
| Runtime/Maven boundary | 1 PASS |
| Standalone harness build-only | 4 PASS |
| Disposable packaging/install compiles | 9 PASS (prepare2 +7 harness-only updates) |
| Device-tool host test suite | 4 PASS, 29 tests each; final23.051s |
| Physical phase runs | 12: first4, cold1, UI4, focused finish1, navigation2; diagnostics above |
| Human Nix | 2: first FAIL on mutable Google index; refrozen final PASS |

No final production/JVM/Gradle/APK pass was invalidated. The failed first Nix attempt
requires the explicit cache-only refreeze and repeated human check above. Qualification harness-only changes after Gradle
are outside its consumers, require affected physical reruns and final Nix freeze, and
do not require a Gradle rebuild solely for harness code.

## Remaining concerns and final Git state

No required qualification remains pending. Actual TalkBack speech remains outside the native node/layout
evidence. Human enlarged-font readability passed with the size preference noted above.
Explicit light native theme remains the application policy. Long names can wrap/ellipsize in the compact
header while retaining full accessible text. Duplicate assigned names intentionally
remain identical; full local IDs remain available in Diagnostics.

Files changed (23) and final status after human Nix PASS:

```text
 M MULTI_VAULT.md
 M README.md
 M app/src/main/java/org/totipo/android/AndroidVaultController.java
 M app/src/main/java/org/totipo/android/BiometricUnlock.java
 M app/src/main/java/org/totipo/android/FrameworkBiometricPrompt.java
 M app/src/main/java/org/totipo/android/MainActivity.java
 A app/src/main/java/org/totipo/android/SetupVaultName.java
 A app/src/main/java/org/totipo/android/TokenDisplayOrder.java
 M app/src/main/java/org/totipo/android/TokenListAdapter.java
 M app/src/main/java/org/totipo/android/TotipoApplication.java
 M app/src/main/java/org/totipo/android/VaultUiModel.java
 M app/src/test/java/org/totipo/android/DailyDriverUxTest.java
 A app/src/test/java/org/totipo/android/DailyUseUxTest.java
 M app/src/test/java/org/totipo/android/MultiVaultUiBoundaryTest.java
 M app/src/test/java/org/totipo/android/SecurityControllerTest.java
 M app/src/test/java/org/totipo/android/VaultManagementTest.java
 M package-deps.json
 A review/A_UX2_ANDROID_DAILY_USE_UX_REPORT.md
 M tools/device/ControllerRetirementRegression.java
 A tools/device/DailyUseUxRegression.java
 M tools/device/MultiVaultUiRegression.java
 M tools/device/PerVaultBiometricRegression.java
 M tools/device/qualify-security.py
```

Branch and HEAD remain unchanged. All file contents remain unstaged; the five ` A`
entries above are intent-to-add only.
No commit, tag, release, publication or push occurred. Changes remain for review with
no staged file content; intent-to-add establishes source membership only.

Final verification: all194 frozen inputs unchanged; git diff --check and
git diff --cached --quiet PASS. Final human Nix PASS applies to the current working
tree. All requested implementation and qualification work is complete.
