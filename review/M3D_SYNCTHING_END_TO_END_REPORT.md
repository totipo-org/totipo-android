# M3D — real Syncthing desktop ↔ Android end-to-end qualification

Status: **INCOMPLETE — A–D passed; stopped at E because Android Edit is unavailable. F–M not run.**
Production-code diff: **NONE**. This is the final report of this partial qualification run,
not M3D acceptance. Evidence classes: **AGENT-VERIFIED**, **HUMAN-OBSERVED** and
**HUMAN-REPORTED TRANSPORT**. The capability gap is not a demonstrated Syncthing defect.

## 1. Starting Android HEAD

**AGENT-VERIFIED:** branch `main`, exact starting HEAD
`d4aaee0ad273da6a0827be10afa4a56612293ed8`, commit “Add immutable VAULT bootstrap and device enrollment”.
Initial `git status --short` was empty. Committed M3C includes M3A Import, M3B Publish,
Join, Initialize, provider/local VaultId gates, canonical icon and physical tooling.
Java 0.2.0 / Vault Format v1/r19 remains pinned. Starting commands were executed before edits.

## 2. Desktop commit supplied by human

**HUMAN-OBSERVED:** supplied desktop commit
`5fb43c2685bc728b259015b268f81881c44d7e95`. No independent desktop build/source inspection performed.

## 3. Android build/artifact identity

**AGENT-VERIFIED:** initial normal build passed (1m37s); strict offline/no-daemon/no-configuration-cache/
no-build-cache/rerun-tasks/strict-verification clean build passed (1m40s; 92 tasks executed).
245 Android JVM tests; zero failures/errors/skips. Wrapper and debug/release APK verification passed.

| Artifact | SHA-256 |
| --- | --- |
| Debug APK | `95be42447c10f92aa968ca4b86ae44ae31f88c698236f6d3c91f3913bf102a63` |
| Unsigned release APK | `13b022247411f45b443564c69d48f41dbb2f9477130d89d76a973f4f40622609` |
| Signed qualification release | `925274d9d27da966f4e800cef59160f75967bf013a6294a9a830af278a10552c` |

Qualification certificate SHA-256:
`1f14855aface333061eb0bb545251756a214aa02c31607735f003689eecf9005`.
Signed release and separate observer were aligned and certificate-checked against the retained
M3C qualification release. Physical installation passed on the connected device. Independently pulled installed APK
exactly matches the signed qualification release and passed `--no-debug-probe` verification.
Evidence: ignored `.gradle/m3d-syncthing/baseline-*.log`, `baseline-apk-sha256.txt`,
`baseline-tests.json`, `signer.txt`.

## 4. Device / Android / provider environment

**AGENT-VERIFIED:** physical Pixel 6a, Android 17, SDK 37. ADB authorization succeeded.
Signed non-debug release was installed and independently pulled/hashed/verified. Actual SAF-selected
`Totipo-M3D-Test` has persisted READ/WRITE, accessible root and complete readable inventories.
**HUMAN-OBSERVED:** ordinary folder picker selected the dedicated child under `totipo-test-sync`.
Observer checks exact bound-tree continuity internally; no URI/document ID is emitted. Provider
authority is not exposed in the current sanitized output, so it is not independently named here.
Current device evidence: ignored `environment.txt`, `installed-apk-verification.txt`.

## 5. Syncthing setup as human-described external transport

**HUMAN-REPORTED TRANSPORT:** Syncthing was configured externally between desktop and Android
using disposable base `totipo-test-sync`. Human confirmed up-to-date status for A, B, C and D.
No Syncthing internals/API/status were agent-verified. **AGENT-VERIFIED:** protocol-file appearance
and immutable deltas were observed through the actual selected Android SAF tree, with complete
byte hashes. No protocol file was copied between endpoints as a transport substitute. No Syncthing
SDK, dependency, installation or configuration by the agent.

## 6. Qualification fixture safety boundary

**HUMAN-OBSERVED:** existing Android local vault was explicitly confirmed disposable and reset
authorized. **AGENT-VERIFIED:** after signed installation, `pm clear` succeeded, then ordinary SAF
selection and empty local/provider evidence were captured before desktop Create. Prepared only
empty `Totipo-M3D-Test` and `Totipo-M3D-Android-First` children in the supplied desktop base.
No protocol bytes were authored/copied by tooling. Each capture checks the expected fixture
name and exact binding continuity before reading the tree. No granted provider root was moved,
renamed or removed. No real user vault was opened or mutated. At stop, retain the qualification
vault/objects and evidence for follow-up; do not restore the initial empty state by deleting
synchronized history. The second child was not used. Standalone observer removed and signed app relaunched; no second app-data reset or provider
cleanup performed. Teardown/retention status is recorded in `restoration.txt`.

## 7. Desktop-first bootstrap

**AGENT-VERIFIED:** `A-empty-03` PASS: local `NO_LOCAL_VAULT`, canonical VAULT absent,
zero local object entries, binding READY, persisted READ/WRITE, complete empty provider inventory,
provider stable across capture. Before/after JSON and `scenario-A-empty-03.json` retained.
**HUMAN-OBSERVED:** human selected the child through ordinary Android picker and granted access.
`A-sync` PASS: exactly one canonical 87-byte vault, one usable objects-v1 directory and one
1024-byte canonical object. Complete SHA-256 inventory retained in `A-sync-after.json` and
`provider-after-A-sync.txt`. Android remains NO_LOCAL_VAULT with zero local objects.
Provider VAULT SHA-256: `b78bcbfc3922f5a928f1bcfb8146203d476d7596b35c5e7a7f2126d23c75ebd5`.
Object `3be19e3e29fa5a0d5a35b80e5f20072eb95185ea91face961a5a69b4d5cb1b29` SHA-256:
`0ae6ae2332e3829f6e35f8d1a08d82a9456d2f611b5c29e9a02ceae8ab34ca87`.
**HUMAN-OBSERVED:** desktop vault and one public token created; files visible on phone.
**HUMAN-REPORTED TRANSPORT:** human explicitly confirmed both endpoints report up to date.
**AGENT-VERIFIED:** explicit `A-import` PASS: desktop-first@example.test is the sole token,
with no conflict/unresolved reference, finished observation and zero diagnostics. The local
canonical object name/size/SHA-256 matches the provider object exactly. Provider inventory
and local canonical VAULT are unchanged by Import. **HUMAN-OBSERVED:** human confirms token
appeared. Desktop-first Create → real Syncthing → Join → explicit Import is qualified for
this disposable fixture, with the transport confirmation separately attributed above.

## 8. Join evidence

**AGENT-VERIFIED:** `A-join` PASS. Ordinary controller state OPEN, finished observation,
zero tokens and zero local objects. Exact provider/local VAULT byte equality is true;
session/local and session/provider VaultId equality are true. Local/provider VAULT SHA-256:
`b78bcbfc3922f5a928f1bcfb8146203d476d7596b35c5e7a7f2126d23c75ebd5`.
Complete provider inventory/hashes unchanged by Join; persisted READ/WRITE and binding READY.
Evidence: `A-join-before.json`, `A-join-after.json`, `scenario-A-join.json`.
**HUMAN-OBSERVED:** human reports Android Join completed.
Explicit Import passed in the same actual session object as Join: observer UUID
`216d11d8-9f2d-403e-9791-cd2d7449be55`, process continuity retained. UUID is a qualification
correlation value, not a protocol identity. Import fixed UI status: “Changes imported;
refresh requested.” The internal refresh report enum is not exposed; finished observation
and the resulting semantic projection were independently captured. Evidence:
`A-import-before.json`, `A-import-after.json`, `scenario-A-import.json`.

## 9. Desktop → Android add

**AGENT-VERIFIED:** `B-sync` PASS: provider gained immutable canonical object evidence while
all prior entries and canonical VAULT stayed identical. Complete inventory remains readable;
all canonical objects are 1024 bytes. Before explicit Import, Android still has one token
(desktop-first@example.test); desktop-second@example.test is absent, local objects and VAULT
are unchanged, and the actual session is retained. Evidence: `B-sync-before.json`,
`B-sync-after.json`, `scenario-B-sync.json`, `provider-delta-B-sync.txt`.
**HUMAN-REPORTED TRANSPORT:** human reports Syncthing up to date in response to the requested
second desktop token/sync checkpoint. **HUMAN-OBSERVED:** desktop UI details were not separately
reported; token metadata was verified on Android after Import.

**AGENT-VERIFIED:** `B-import` PASS: two tokens with the expected public accounts, zero
conflicts/unresolved references, the same actual session as A, unchanged canonical VAULT
and complete provider inventory/hashes. Local canonical inventory now matches both provider
objects exactly (names, 1024-byte sizes, SHA-256). **HUMAN-OBSERVED:** human confirms both
accounts appear after Import. Evidence: `B-import-before.json`, `B-import-after.json`,
`scenario-B-import.json`. Ordinary desktop → Android add is qualified for this fixture.

## 10. Desktop → Android edit

**AGENT-VERIFIED:** `C-sync` PASS: provider gained canonical immutable object evidence while
all previous provider entries and VAULT stayed identical. All canonical objects are readable
and 1024 bytes. Before Android Import, both original accounts remain current; the edited
account is absent, local objects/VAULT are unchanged and the same session continues.
Evidence: `C-sync-before.json`, `C-sync-after.json`, `scenario-C-sync.json`,
`provider-delta-C-sync.txt`. **HUMAN-REPORTED TRANSPORT:** human reports Syncthing up to date
in response to the requested desktop edit and both-endpoint convergence checkpoint.
**HUMAN-OBSERVED:** desktop UI details not separately reported.

**AGENT-VERIFIED:** `C-import` PASS: two current tokens, first account now
`desktop-edited@example.test`, original account absent, second account preserved. Zero false
conflicts/unresolved references; same actual session and unchanged VAULT/provider inventory.
Three local canonical objects match the provider exactly by name/size/hash, preserving both
previous objects unchanged as immutable history. **HUMAN-OBSERVED:** human confirms Android
value updated. Evidence: `C-import-before.json`, `C-import-after.json`, `scenario-C-import.json`.
Desktop → Android metadata edit is qualified for this fixture.

## 11. Android → desktop add

**HUMAN-OBSERVED:** Android token added with public account typo `android@example.tes`.
Qualification metadata uses that exact account from this point; no token replacement/edit attempted.
**AGENT-VERIFIED:** original observer refused this non-`@example.test` metadata during local
capture (`D-typo-observer-refusal.json`). Added a qualification-only exact fixture exception;
rebuilt/signed/reinstalled/reloaded only the observer. No production behavior changed, no
provider mutation/app-data reset occurred. After reload, LOCKED local VAULT hash and complete
provider inventory/hashes match `D-local-before.json`; the local root has four object entries.
Normal unlock passed; all three public tokens present, including `android@example.tes`. This observer reload ends session continuity; A–C same-session
proof remains valid, but D will not claim continuity across the reload. D Publish and desktop observation are recorded below.

**AGENT-VERIFIED:** `D-local` PASS after normal unlock: three current tokens, no conflicts or
unresolved references. Four local canonical objects preserve all previous history; exactly
one new 1024-byte local object is absent from the provider, which still has the preceding
three objects. Canonical VAULT byte equality/identities hold and hash is unchanged. Evidence:
`D-local-before.json`, `D-local-after.json`, `scenario-D-local.json`, `local-delta-D-add.txt`.
Provider inventory/hashes unchanged by Add and the observer reload. Session continuity
across the reload is explicitly not asserted.

**AGENT-VERIFIED:** `D-publish` PASS: exactly one canonical 1024-byte object added remotely,
with name/size/SHA-256 equal to the previously local-only object. Complete local/provider
inventories now match at four canonical objects. Existing provider entries and local/provider
VAULT hashes/bytes/identities unchanged; same actual session retained since normal unlock.
**HUMAN-OBSERVED:** Android displayed “local changes published”. Evidence:
`D-publish-before.json`, `D-publish-after.json`, `scenario-D-publish.json`,
`provider-delta-D-publish.txt`. **AGENT-VERIFIED:** `D-idempotent` PASS: immediate second Publish preserved the entire
provider inventory/hashes, local object inventory, public semantic projection, session
identity and canonical VAULT. **HUMAN-OBSERVED:** Android displayed “No local changes to
publish”. Evidence: `D-idempotent-before.json`, `D-idempotent-after.json`,
`scenario-D-idempotent.json`. **AGENT-VERIFIED:** `D-desktop` PASS for Android invariance: provider/local objects,
semantic state, VAULT and the post-unlock session remain unchanged.
**HUMAN-REPORTED TRANSPORT:** human reports Syncthing up to date in response to the request
specifying both endpoints. **HUMAN-OBSERVED:** `android@example.tes` appeared on desktop
after manual refresh; human reports desktop has no auto-refresh yet. This is desktop UI
observation, not Android-derived proof of desktop internals. Scenario D passed with these
three evidence classes separately attributed. Evidence: `scenario-D-desktop.json`.

## 12. Android → desktop edit

**AGENT-VERIFIED — NOT_EXECUTABLE; suite stopped here.** Scenario E requires editing the
Android-authored token through the actual current Android UI. Committed M3C provides Add and
Show code, but no token Edit UI/controller command. Evidence boundaries:

- `MainActivity.java:201`: token-row callback invokes Show code; Add is separate.
- `TokenListAdapter.java:49`: sole row action is Show code.
- `AndroidVaultController.java:355`: Add command; no Edit/Delete/Resolve command.
- `ForegroundVaultCoordinator.java:268`: token authoring creates a new token only.

Before/after `E-unavailable` captures show identical full provider and local state, including
three current tokens, four canonical objects, session and VAULT. No semantic edit attempted,
no provider transport delta generated, no production code changed. A live UI enumeration did
not expose target-app nodes, so `E-visible-actions.json` is inconclusive and is not proof of
UI absence; source inspection establishes the capability boundary.
**HUMAN-OBSERVED:** no Scenario E edit result; last desktop observation is D's token appearance
after manual refresh. **HUMAN-REPORTED TRANSPORT:** last confirmation is D's up-to-date status;
no E transfer attempted. Likely boundary: Android product UI/controller token lifecycle,
not an established Syncthing/data-integrity defect. Evidence: `scenario-E-unavailable.json`,
`FAILED.json`, `E-unavailable-before.json`, `E-unavailable-after.json`.

## 13. Concurrent conflict

NOT RUN — suite stopped at E. No paused-endpoint concurrent edits or semantic conflict
qualification. No filesystem timestamp/name/order selection claim is made.

## 14. Natural Syncthing conflict artifacts

NOT OBSERVED — concurrent scenario was not reached. No natural Syncthing conflict artifact
captured or deleted, and no Syncthing-style conflict filename manufactured.

## 15. Desktop-side conflict resolution

NOT RUN — no real conflict was created before the E stop. Desktop Resolve interoperability
remains unqualified.

## 16. Android-side conflict resolution

NOT RUN — no real conflict created. Source preflight also finds no Android Resolve
UI/controller action in this baseline; a separate product milestone is required.

## 17. Deletion/tombstone interoperability

NOT RUN — neither deletion direction exercised. No physical historical-object deletion
performed. Source preflight also finds no Android Delete UI/controller action in this baseline.

## 18. Process/Syncthing restart

NOT RUN — full Scenario J, including human Syncthing restarts on both endpoints and desktop
restart, was not reached. The observer reload during D preserved VAULT/provider hashes and
normal Android unlock restored the three tokens; this is limited setup-recovery evidence,
not a substitute for J.

## 19. Android-first Initialize/Publish/Open

NOT RUN — second fresh child prepared but no new local vault, Initialize, Publish or desktop
Open attempted. Existing M3C unit/device results do not qualify real transport Scenario K.

## 20. Different-vault protection

NOT RUN — no different-vault real-transport attempt. Existing gates/dependencies/production
source remain unchanged, but M3C results do not substitute for Scenario L.

## 21. Noncanonical sibling behavior

NOT RUN — no manual noncanonical siblings introduced and no repair/overwrite/delete attempted.
Manual sibling and natural conflict artifact coverage remain distinct and unqualified.

## 22. Partial/transient transport observations

**AGENT-VERIFIED:** NOT OBSERVED. Captures were taken at human-confirmed converged checkpoints;
no controlled active-transfer window was observed. No canonical corruption, manufactured
transfer race or repeated Import/Publish hammering performed.

## 23. Same-session evidence

**AGENT-VERIFIED:** actual VaultSession object identity, via long-lived observer identity-map,
was retained from Join through A-import, B and C. Local snapshots execute on the existing
vault worker; OPEN reads use coordinator/bridge APIs and never a second live NIO delegate.
An observer-only metadata exception/reload during D ended that session; normal unlock then
established a new session. D Publish, second Publish, desktop-observation checkpoint and E
before/after retain this new session. No continuity across that reload is claimed. Qualification
UUIDs are correlation values, not protocol VaultIds. Compilation alone is not continuity proof.

## 24. VAULT identity invariance

**AGENT-VERIFIED:** once desktop-created VAULT arrived, all observed provider VAULT hashes
through E stayed `b78bcbfc3922f5a928f1bcfb8146203d476d7596b35c5e7a7f2126d23c75ebd5`.
Join installed exact bytes. All successful OPEN captures report provider/local byte equality,
session/local VaultId equality and session/provider VaultId equality. No provider VAULT rewrite
observed during Join, Imports, local Add, Publish or second Publish. Raw VaultId/VAULT bytes
are not emitted. Later unexecuted scenarios have no invariance qualification.

## 25. Provider before/after inventories

**AGENT-VERIFIED:** complete sorted SAF inventories exist before/after every executed
checkpoint. Rows contain relative logical name, kind, exact observed stream length, SHA-256
and availability. Provider observations bracket local capture and remained stable; no timestamps,
row order or document-ID ordering used as authority. Loading/errors/duplicate names/cycles/
capacity failures block complete evidence. These are bounded samples, not atomic filesystem snapshots.
Evidence: `*-before.json`, `*-after.json`, provider delta text files under ignored
`.gradle/m3d-syncthing/`. Final stop-point inventory follows:

| Relative name | Kind | Bytes | SHA-256 |
| --- | --- | --- | --- |
| `objects-v1` | directory | — | — |
| `objects-v1/3be19e3e29fa5a0d5a35b80e5f20072eb95185ea91face961a5a69b4d5cb1b29` | file | 1024 | 0ae6ae2332e3829f6e35f8d1a08d82a9456d2f611b5c29e9a02ceae8ab34ca87 |
| `objects-v1/97ce8618ab8676be4d5131112ae7c7edd1a5a7700939c71dc4143b063a0b5558` | file | 1024 | 353581623cdbc4959bc7e50f5533aa3ec1c034b1753095e56e0faf68dbac2b0a |
| `objects-v1/b4a7e091a779eab0add62ae62dd27fc0e0f3995d85a4e03958ee7b6b87294c87` | file | 1024 | 52dae3dd0c121d15196ad1bc8a10ed20058be1037e8539e6ad5f963814d887ba |
| `objects-v1/d90b61519f71835337419a48b22f4a2ee67fbef76c69e19361da30d439b0487a` | file | 1024 | 5a433569acdbb575e0c8426d4d299a826a2dd3811bd60047fee80ce3a884d574 |
| `vault` | file | 87 | b78bcbfc3922f5a928f1bcfb8146203d476d7596b35c5e7a7f2126d23c75ebd5 |


## 26. Android local-state invariance

**AGENT-VERIFIED:** final stop-point OPEN state has three public fixture tokens,
zero conflicts/unresolved references, finished observation and zero diagnostics. Four local
canonical objects exactly match provider names/sizes/SHA-256. Local VAULT unchanged after
Join. Provider-only arrivals did not alter Android semantic state until explicit Import.
Imports did not mutate provider; local Add did not publish remotely until explicit Publish;
second Publish changed nothing. E before/after local and provider evidence is identical.
No password, TOTP secret or code rendering is accessed/persisted. Metadata allowlist includes
only public M3D accounts and the exact human-identified typo `android@example.tes`.
Internal operation-report enums are not retained by controller. Fixed statuses were recorded;
refresh field is honestly `NOT_EXPOSED_BY_CONTROLLER`, with completed semantic observation
verified separately. No production instrumentation seam added.

## 27. Human desktop observations

**HUMAN-OBSERVED:** desktop vault and first public token created; Android Join completed;
first token appeared after Import; both appeared after B; first updated after C; Android token
added with account typo; normal unlock completed; Publish and second Publish displayed their
reported statuses. Desktop showed `android@example.tes` after manual refresh; human reports
no desktop auto-refresh yet. All statements are recorded in `human-observations.txt` and
scenario JSON. No desktop conflict/resolve/deletion/restart result reported. Android hashes
do not establish desktop internals.

## 28. Human-reported Syncthing convergence checkpoints

**HUMAN-REPORTED TRANSPORT:** A-sync explicitly confirmed BOTH endpoints up to date;
B-sync and C-sync reported up to date in direct response to requests specifying both endpoints;
D-desktop reported up to date in response to the same requirement. These are human statements,
not agent inspection of Syncthing internals. No pause/resume/restart status recorded. Provider
appearance/hashes were separately agent-verified and never substituted for convergence confirmation.

## 29. Dependencies / permissions / supply chain

**AGENT-VERIFIED:** production graph remains direct NIO 0.2.0 → core 0.2.0 → runtime
BC 1.86; compile excludes BC. All four graph boundaries checked by Gradle. Locks and strict
verification unchanged. Java release source commit `d6310c177ae930df188fd4f5798622c935698b2e`;
r19 spec `cdb4e91be1c6d3704874b2b92457ffe7be5e9084` per existing provenance document.
Package-deps SHA-256:
`74eb72a9ffb5f9f364f02f9f64014f260f4baf610c1cefdb9b09ed6846eb42b1`.
Release APK verifier confirms zero permissions/services/receivers/providers, allowBackup=false,
existing MainActivity launcher and OtpAuthEnrollmentActivity VIEW/SEND boundaries, canonical
adaptive/round/density icons, NIO/core/BC and exclusion of existing debug/qualification classes.
Additional direct DEX check excludes M3D observer package/class. No Syncthing library or INTERNET
permission added. Separate observer is outside all production source sets.

## 30. Final Android build/APK verification

**AGENT-VERIFIED:** final post-tooling normal build passed (1s); final strict offline clean
build passed (1m39s; 92 tasks executed):

```sh
./gradlew check :app:assembleDebug :app:assembleRelease
./gradlew --offline --no-daemon --no-configuration-cache --no-build-cache \
  --rerun-tasks --dependency-verification=strict \
  clean check :app:assembleDebug :app:assembleRelease
```

245 Android JVM tests; zero failures/errors/skips. All eight host evidence-oracle tests passed.
Standalone observer javac/d8/aapt2, alignment/signature, installation and live capture passed
after qualification-only setup corrections. Wrapper, debug `--debug-probe`, unsigned release
`--unsigned --no-debug-probe`, and signed release `--no-debug-probe` verification passed.
Final debug/unsigned release/signed release/package-deps hashes reproduce section 3 exactly.
Independent final pull of installed APK after observer teardown equals the signed release.
Production source/resource/test/build/dependency input hashes are unchanged; release DEX
excludes M3D observer class/package. No permissions/icon/dependency changes.
Evidence: `final-normal.log`, `final-strict.log`, `final-tests.json`, `apk-sha256.txt`,
`production-invariance.txt`, `installed-final-verification.txt` under ignored evidence root.
These checks passed; M3D overall remains incomplete because E–M were not qualified.

## 31. Human Nix

**HUMAN-OBSERVED:** human replied “looks like these passed” to the request for both commands
below on 2026-10-09. Recorded as human-reported passes; no logs supplied. No dependency/build-input
changes made. Commands requested:

```sh
nix flake check path:.
nix build path:.
```

Agent has not run Nix. These are the current M3D human results; historical M3C results are separate.

## 32. Defects / UX friction observed

No synchronization/data-integrity defect observed in executed A–D. E is a required product
capability gap and prevents completion. No production fix performed.

| Scenario | Observed behavior | Expected/intuitive behavior | Severity | Correctness affected |
| --- | --- | --- | --- | --- |
| D | HUMAN-OBSERVED: desktop needed manual refresh to show token; no auto-refresh yet | Arriving synchronized changes become visible or clear refresh guidance appears | Low, manual workflow friction | No observed data-integrity issue; manual refresh succeeded |
| E | AGENT-VERIFIED: Android has no token Edit UI/controller command | Existing token can be edited before Publish | Qualification blocker | Required workflow unavailable; transport correctness not tested |
| H / I preflight | AGENT-VERIFIED: no Android Resolve/Delete UI/controller commands | Current UI supports resolution and deletion required by requested scenarios | Future coverage blocker | Not exercised; no transport defect inferred |
| D harness | Observer refused human-identified public account typo, requiring observer reload and normal unlock | Explicit public fixture metadata can be captured without session disruption | Qualification-tool friction | Session continuity break disclosed; canonical/provider invariance preserved |

Two initial empty-baseline captures failed inside the observer (null handling/startup order),
were retained, and were corrected solely in tooling before `A-empty-03` passed. An overlapping
clean build temporarily prevented signing from production outputs; observer-only signing
was added and the corrected observer was independently signed/verified/installed. These are
harness/setup issues, not product synchronization defects. No password/secret enters evidence.

## 33. Architecture conclusions

**AGENT-VERIFIED:** for this disposable fixture, real-provider arrivals plus explicit Import
support desktop-first bootstrap, ordinary add and metadata edit; local Add remains private
until create-only Publish; Publish preserved immutable history/VAULT and second Publish was
idempotent. **HUMAN-OBSERVED:** Android-authored token appears on desktop after manual refresh.
**HUMAN-REPORTED TRANSPORT:** the endpoints were up to date at corresponding checkpoints.
These separate facts support partial real Syncthing interoperability A–D. Concurrent conflicts,
resolution both ways, deletion, full restart, Android-first Initialize, wrong-vault real-transport
gates and noncanonical siblings remain unqualified. No complete M3D claim is justified.

## 34. Recommended next milestone

Create a separate Android token-lifecycle milestone for actual Edit, Resolve and Delete
UI/controller flows, with appropriate correctness tests. Do not change production in this
qualification run. After that milestone is committed, rerun/resume qualification against
explicit new APK/HEAD identities, including E–M and a real concurrent conflict. Preserve this
partial report and disposable fixtures as evidence; do not use injected test-only semantic
mutations to bypass missing product actions. [Checkpoint protocol](../tools/device/M3D_SYNCTHING_CHECKPOINTS.md).

## 35. Final Git state

**AGENT-VERIFIED:** branch `main`, HEAD remains
`d4aaee0ad273da6a0827be10afa4a56612293ed8`. No staging/commit/tag/push/release/CI dispatch.
Production tracked files unchanged. New qualification tooling, host tests, guide and report
remain unstaged/uncommitted. Ignored evidence/artifacts reside in `.gradle/m3d-syncthing/`.
Human-managed `totipo-test-sync/` is untracked and must not be staged. Final `git diff --check` and whitespace checks on all new files passed; index diff is empty.
Captured production input hashes remain identical. Stopped instrumentation, uninstalled only
`org.totipo.syncthingqualification`, relaunched signed production app, and independently
verified the installed APK hash. No second app-data reset or provider cleanup performed;
fixture contents/evidence retained. Stop-point semantic state was captured before teardown;
no semantic state is asserted from a post-teardown observer capture. Agent ran no Nix.
