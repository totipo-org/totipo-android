# Android token lifecycle — Edit, Delete and Resolve

Status: milestone PASS — implementation, final agent validation, physical qualification and human-reported Nix checks complete.
All work remains unstaged/uncommitted. This report does not complete the historical M3D run.

## 1. Starting state

Branch `main`; exact starting HEAD `f93fc383123f29663e28020a04ac0d39b6f79e2a`.
The required branch/HEAD/status commands ran before edits; initial worktree was clean.
History includes committed M3C `d4aaee0` and partial M3D tooling/report `2525098`,
followed by `f93fc38` (ignore tmp). Released Java NIO/core 0.2.0, v1/r19,
M3A Import, M3B Publish, M3C Join/Initialize and canonical branding were present.

## 2. Capability gap confirmed in current production source

Inspected MainActivity, AndroidVaultController, ForegroundVaultCoordinator, TokenListAdapter,
AddTokenRequest/AddTokenOutcome, manual/otpauth enrollment, detached View/ObservedToken,
state callbacks, foreground admission, Import/Publish and provider lifecycle.
The original adapter exposed only Show code. Add existed; no controller Edit/Delete/Resolve
command existed. Conflicts were summarized as Needs attention and could not generate code.
The partial M3D report was corroborating history, not the authority for this finding.

## 3. Baseline validation

Before production edits:

- `./gradlew check :app:assembleDebug :app:assembleRelease`: PASS, 1m42s.
- Required strict offline/no-daemon/no-configuration-cache/no-build-cache/rerun-tasks clean
  build: PASS, 1m42s, 92 executed tasks.
- 245 JVM tests, zero failures/errors/skips.
- `verify-wrapper.py`: PASS, Gradle 9.8.0 pins.
- Debug `verify-apk.py --debug-probe`: PASS.
- Unsigned release `verify-apk.py --unsigned --no-debug-probe`: PASS.
- `git diff --check`: PASS.

Baseline debug SHA-256: `edb3b21b3bca070379ccc8fca7b0d8dc0f73c2a2ad4f5dc5ce7a2c05c2db6752`.
Baseline unsigned release SHA-256: `80f648a617419930f58e976c1e343f5a5ef37cf359a4f16f646535331f92344b`.
Dependency graph: NIO 0.2.0 → core 0.2.0 → BC 1.86 runtime; verified four production configurations.
Package-deps SHA-256: `74eb72a9ffb5f9f364f02f9f64014f260f4baf610c1cefdb9b09ed6846eb42b1`.
No manifest permissions; release has MainActivity and OtpAuthEnrollmentActivity only;
debug adds the four existing qualification Activities. No service/receiver/provider.

## 4. Exact released Java APIs and ownership

Inspected the [Maven Central sources](https://repo.maven.apache.org/maven2/org/totipo/totipo-core/0.2.0/totipo-core-0.2.0-sources.jar)
and matching published Javadocs, plus release API_DESIGN.md. Source JAR SHA-256
`e2661770e0e59ca733959b4931689a9253988352605087c390bfc3a045dd9b6c`.

Public APIs:

- `VaultSession.state()`, `states()`, `requestRefresh()`; `VaultState.token(TokenId)`.
- `VaultState.update(TokenAlternative)` / `update(TokenHead)` return `UpdateToken`,
  prefilled with the selected complete value. They deliberately accept historical references
  and do **not** enforce a stale-update gate.
- `VaultState.merge(TokenId)` returns `MergeToken`, capturing the complete frontier.
- `MergeToken.keep(TokenAlternative)` transfers all semantic fields, including hidden secret
  and TOMBSTONED status internally; it exports no credential buffer.
- `TokenEditor.issuer(String)`, `account(String)`, `status(TokenStatus)`, `save()`, `close()`.
- `TokenAlternative.descriptor()` / `heads()` and TokenState alternatives/heads/unresolved/conflict.
  TokenId and RevisionId are library value identities; Android constructs no protocol revisions.
- `SaveResult.Saved`, `Failed(Reason)`, `AdditionalConflict(latest, resolution)`,
  `PublicationUncertain(retry)` are handled explicitly.

All three lifecycle operations use `captured.merge(token.id()).keep(selected)` and then
appropriate issuer/account or TOMBSTONED setters. This uses supported ordinary semantic
assertion authoring with Java's prepublication freshness gate, including on a singleton
frontier. Using UpdateToken alone would fail the milestone's freshness requirement.
No package-private Java implementation class is used by production Android code.
Builders are worker-confined and closed in try-with-resources. No existing secret is exported.
Existing Add's NewSecret/decode buffers remain owned and wiped as before; metadata-only
Edit/Delete/Resolve introduce no transient secret/setup buffer to wipe.

## 5. Android architecture reuse and desktop parity

One existing vault worker, one existing canonical coordinated store, one existing VaultSession,
one existing provider lane, ordinary detached state observation. Activity/adapter receive
only immutable public descriptors and freshness identities, never session/Alternative ownership.
ObservedToken additionally associates each descriptor with its exact Alternative heads, so
secret-only conflicts with identical visible metadata cannot silently exchange choices.

Inspected current [desktop README](https://github.com/totipo-org/totipo-desktop/blob/5fb43c2685bc728b259015b268f81881c44d7e95/README.md)
at desktop HEAD `5fb43c2685bc728b259015b268f81881c44d7e95` for semantics only.
Edit retains hidden setup; deletion asserts a tombstone and retains history; resolution keeps
one complete semantic Alternative. No Swing code or architecture was copied.

## 6. Edit UX

Normal live rows retain Show code and add a small native overflow menu with Edit/Delete.
Edit is admitted only for an unambiguous ACTIVE value without unresolved references and
with a finished, usable observation. A native dialog seeds issuer/account from the exact
selected detached descriptor, using the same metadata field construction as Add.
It shows algorithm/digits/period as a safe setup summary and explicitly states that setup
is retained and Change setup is unavailable. No hidden trim/normalization or second parser.

## 7. Edit authoring and freshness

The controller reserves a TokenChange capability while the form is open. Confirmation
revalidates that capability, captures the current same-session token and compares the complete
public projection including Alternative-head mappings. Java merge save then freshly observes
canonical storage and rejects newly relevant heads through AdditionalConflict.
The partial-resolution capability is discarded, never saved. A changed basis returns
`This token changed. Review it and try again.` No rebase or semantic retry occurs.
Java's normal compare/check-to-publication race is preserved; this does not claim global CAS.
Success authors immutable local history, retains the session, and renders ordinary observed
state with code concealed. Issuer/account setters remain Java's validation authority.

## 8. Delete UX

Delete is available only for a current unambiguous live token. The overflow item opens a
separate `Delete this token?` confirmation with a destructive Delete button and Cancel.
Opening/cancelling does not author. Already-deleted values offer neither Delete nor Edit.

## 9. Tombstone semantics

Delete sets `TokenStatus.TOMBSTONED` on the complete retained semantic value and calls Java
save. It deletes no canonical/provider file and does not erase secrets or history.
Confirmation says the token leaves current state and historical encrypted revisions may
remain in synchronized storage. Ordinary live-list presentation filters tombstones;
controller authoring admission immediately conceals revealed code and selection is cleared.
Stale deletion follows the same review-required policy as Edit.

## 10. Conflict presentation

Conflicted rows explicitly say Token conflict and show safe metadata context. Resolve replaces
ordinary Show code. Unavailable/incomplete conflicts are not offered an authoring action.
Code generation still independently refuses conflicts at the coordinator boundary.
No arbitrary Alternative is chosen for display of a code.

## 11. Whole-Alternative chooser

A native single-choice dialog represents every complete current Alternative as Option N with
issuer/account and credential parameters, or Deleted for TOMBSTONED values. There is no
preselection; Resolve stays disabled until selection. No revision IDs, origin, ciphertext,
secret or timestamps are shown. Identical metadata can still represent distinct complete
Alternatives; exact head mappings bind the selected index to the captured complete value.
Java `keep` defensively transfers hidden fields internally. Stale conflict returns
`This conflict changed. Review it and try again.` Successful resolution is observed normally,
removing conflict presentation or removing the live row when Deleted is selected.

## 12. Detailed Combine and Change setup disposition

Deferred deliberately. Minimum M3D metadata Edit and whole-Alternative Resolve are provided.
Existing manual/otpauth Add paths remain; no duplicate parser or new secret ingress is added.
The Edit UI discloses Change setup deferral; README discloses both deferrals.

## 13. Local-only-before-Publish invariant

Lifecycle authoring calls only the existing live coordinator and Java canonical-store save.
It performs no provider writer call, ProviderIoLane mutation, provider Import, automatic
Publish, reopen or authentication. Source guards check both command and authoring paths;
real integration tests compare transport inventories/scans and explicitly Publish afterward.
Saved means Java's configured **local** durability acknowledgement, not remote transport.
No new executor/queue or background synchronization exists.

## 14. Lock, cancellation and recreation

One pending capability reserves the existing foreground admission slot. Add, Refresh, folder
changes, Import, Publish, Join/Initialize and additional lifecycle flows cannot enter that
slot. Lock cancels pending capability before normal close admission; shutdown invalidates it
and generation-checks admitted completion. Already-admitted work follows existing lifecycle;
no filesystem operation is interrupted. Dismiss/Cancel/Activity stop discard the form.
No lifecycle Alternative/secret/setup is saved to Bundle, preferences or widget state.
Rotation/process recreation requires reopening the flow. Session ownership remains Application-scoped.

## 15. Accessibility

Native text buttons/menu items name Edit, Delete, Resolve, Save and Cancel. Overflow has an
explicit Token actions: Edit or Delete description. Issuer/account use labelFor associations;
conflict and Deleted have textual indicators. Delete confirmation includes the history
warning; chooser uses platform radio selection with a disabled-until-selected Resolve action.
Show code/Add/sync accessibility and secure-window/code announcement policies remain.
Host guards cover the wiring; physical harness exercises actual native dialogs and buttons.

## 16. Error presentation

Only fixed Android product messages are shown. Java exception text/object IDs/storage paths
are never displayed. Failed save reports `Totipo could not save this change.` Uncertain save
uses existing Add policy: release frozen retry ownership, require explicit local Refresh and
never repeat semantic authoring automatically. Released Java conservatively reports
PublicationUncertain even for a declined SPI publication once publication has been entered;
tests assert actual Java outcomes rather than assuming SPI Failed implies SaveResult.Failed.

## 17. Real Java integration coverage

Production Android controller + released Java + coordinated canonical store tests cover:
metadata seeding/retained credential/code equivalence, exact text preservation, immutable old
objects/old descriptive values, same session, concealed code, Cancel/Lock rejection, invalid
input, stale Edit/Delete/Resolve, actual Java merge freshness against an unobserved legitimate
new head, incomplete prepublication observation, local publication uncertainty, tombstone
history, and later explicit Publish of each mutation.

Conflict fixtures clone legitimate encrypted history into an independent isolated Java
session/store, author a sibling through public update/save, independently Edit Android,
and explicitly Import both branches into knowledge. Production Resolve selects local, remote,
and Deleted complete Alternatives in separate tests. No bytes/revisions are forged.
Symbolic presentation tests additionally cover identical visible descriptors and Deleted labels.

## 18. Final validation and totals

Final `./gradlew check :app:assembleDebug :app:assembleRelease`: PASS, 1m20s,
90 tasks (19 executed, 71 up-to-date).
Final strict offline build: PASS, 2m1s, all 92 tasks executed:

```sh
./gradlew --offline --no-daemon --no-configuration-cache --no-build-cache \
  --rerun-tasks --dependency-verification=strict clean check \
  :app:assembleDebug :app:assembleRelease
```

259 JVM tests, zero failures/errors/skips (baseline 245; 11 lifecycle integration tests
and three lifecycle source guards added). Wrapper, debug/release APK verification and
`git diff --check` PASS; cached diff empty.
M3D evidence-oracle host checks: 9 tests PASS. Updated standalone observer builds successfully.

## 19. Physical Edit

PASS on connected Pixel 6a, Android 17 / API 37, using the current signed non-debug
release. The standalone physical suite passed **41 checks** across Edit/Delete/Resolve.
Actual MainActivity/native dialog/controller path: existing public token, seeded account,
metadata-only form with no secret field, edited account rendered, same session, no code
reveal, fixture provider inventory unchanged, then explicit Publish acknowledged and gained
objects. The harness invokes the production row action handler and clicks native dialog
controls; it does not qualify coordinate-based overflow discovery or TalkBack speech.

## 20. Physical Delete

PASS in the same complete run. Actual production confirmation was visible; opening it authored
nothing, Cancel authored nothing, confirmation saved a tombstone, revealed code was concealed,
and the live adapter stopped showing the deleted token. Same session and unchanged transport
were verified before explicit Publish; Publish then acknowledged and gained objects.
Earlier attempts were interrupted by the device's 30-second lock timeout and were not accepted.
The complete passing run temporarily extended that timeout; original `30000` ms was restored
and independently checked afterward.

## 21. Physical Resolve

PASS with a genuine conflict. On-device released Java authored a sibling in an independent
coordinated store/session from a legitimate shared snapshot. Android independently edited,
explicitly imported the branch, rendered conflict and refused arbitrary Show code. The native
chooser had no preselection and included the complete remote Alternative; selecting it
removed conflict and rendered its account. Same session and unchanged fixture transport were
verified before explicit Publish; Publish acknowledged and gained objects.

Reproduce with `python3 tools/device/qualify-token-lifecycle.py` after release build, with the
qualification signer files retained and the Pixel unlocked. Build helper is
`tools/device/build-token-lifecycle-tests.py`; instrumentation is `TokenLifecycleRegression.java`.
Local evidence: `.gradle/token-lifecycle/physical-qualification.txt` and `physical-identity.txt`.
Installed APK was pulled and compared byte-for-byte with the signed qualified artifact.
Temporary instrumentation was uninstalled and the original application relaunched afterward.

Harness transport is file-backed and cache-isolated; this does not claim SAF/Syncthing
qualification. Only disposable public tokens/vaults are used. No original app vault,
persisted SAF binding or retained M3D provider evidence is mutated. No object bytes or revision
identifiers are forged. Full Syncthing qualification remains the next milestone.

## 22. M3D evidence-tool compatibility

The observer retains the public-fixture metadata allowlist, adds Alternative status, explicit `--deleted` tombstone assertions and
deleted token evidence, and counts only normal live/conflict rows in token_count/tokens.
Thus edited metadata, conflict presence, chosen resolution and deletion can be observed without
mistaking retained tombstone metadata for a live account. Existing conflict/account expectations
and host oracle tests remain. Historical M3D report/evidence is unchanged; no old checkpoint is
regenerated or marked complete. Future captures must use new committed HEAD/APK identities.

## 23. Dependencies and supply chain

No dependency/build configuration/lock/verification/package-deps changes. NIO/core remain
0.2.0 and BC 1.86 runtime; no UI framework/library. Package-deps hash remains the baseline.
Canonical icon/assets and branding-provenance are unchanged.

## 24. APK and permission verification

Final SHA-256:

- Debug: `3a2a945ee73cb034f51aa10227d54555bb2b58b0b6717949b1fb5075150f7d3d`.
- Unsigned release: `a38658fb235b2154f2ea9fbadf71c0e3a240777bb84c16e2a15fddb0a3339a96`.
- Physically installed signed release: `c6d8feabd080ab4fa9785021cacbd660088317da853466f2b642d1de7d052569`.
- Retained qualification certificate: `1f14855aface333061eb0bb545251756a214aa02c31607735f003689eecf9005`.

`python3 tools/verify-wrapper.py`, debug `verify-apk.py --debug-probe`, unsigned release
`verify-apk.py --unsigned --no-debug-probe`, and signed release `verify-apk.py --no-debug-probe`
all PASS. No manifest changes; zero permissions, two release Activities, six debug Activities,
zero service/receiver/provider. Existing production enrollment, debug/release separation and
canonical branding guards pass. Package-deps SHA-256 remains
`74eb72a9ffb5f9f364f02f9f64014f260f4baf610c1cefdb9b09ed6846eb42b1`.
Qualification instrumentation is a separate temporary APK, never bundled in production.

## 25. Human Nix

Not run by agent. On 2026-10-09, after final agent/physical validation, the human reported
“nix passed” in response to the request to run both `nix flake check path:.` and
`nix build path:.`. Both are recorded as **human-reported PASS**; no agent Nix execution
or independently captured Nix log is claimed.

## 26. Remote CI

Not dispatched. No commit/tag/release/push/stage operation performed.

## 27. M3D resume readiness

M3D Scenario E is now executable.
M3D Scenario H/I Android actions are now executable.

Android Edit, Delete and whole-Alternative Resolve have real Java host coverage and physical
production UI/controller acceptance, including explicit subsequent Publish. Final agent builds
and human-reported Nix checks pass.
Next task after this milestone is committed: resume/rerun real Syncthing M3D from E onward
with the new committed Android HEAD/new APK hashes, retaining A–D as historical prior-run
evidence only and collecting fresh E–M evidence. Do not resume full M3D in this milestone.

## 28. Explicit deferred work

Change setup in Edit; detailed Combine; full real Syncthing E–M rerun; migration/password
change; background/automatic Import/Publish; unrelated UI redesign; universal device/provider
qualification. Existing explicit Add/Show code/Join/Initialize/Import/Publish remain.

## 29. Final Git state

Final inspection: branch `main`, HEAD unchanged at
`f93fc383123f29663e28020a04ac0d39b6f79e2a`. All milestone changes remain unstaged/uncommitted;
`git diff --cached --stat` is empty. Twelve tracked files modified; six new files untracked
(TokenChange, lifecycle source guards, this report, and three standalone physical tool files).
No stage/commit/tag/release/push/CI dispatch or agent-run Nix occurred. Local build/evidence
artifacts remain in ignored `.gradle`/build locations. Human-reported Nix checks pass.
