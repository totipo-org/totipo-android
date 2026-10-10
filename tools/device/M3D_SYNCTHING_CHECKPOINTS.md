# M3D real transport checkpoints

This is qualification-only tooling. It never controls Syncthing, copies protocol files,
authors tokens, resets app data, or deletes provider fixtures. Production remains unchanged.
Use only human-confirmed disposable local state and the real SAF-selected Syncthing folder.
The Syncthing parent may be `totipo-test-sync`; select its `Totipo-M3D-Test` child in Totipo.
Use `Totipo-M3D-Android-First` for the second vault. Never rename/move granted roots.

## Prepare and keep the observer alive

For a fresh lifecycle-baseline resume run, pass
`--evidence-dir .gradle/m3d-syncthing-resume` to **every** runner command below,
including `serve`, `begin`, `finish`, and `capture`. Keep historical evidence and its
failure latch in `.gradle/m3d-syncthing/` untouched. The committed lifecycle observer
records Alternative status and separates current tombstones (`deleted_tokens`,
`deleted_count`) from the live list. Use `--deleted` plus explicit history/delta
inspection for deletion; absence from the live list alone is insufficient.
The coverage-limit section below describes the historical run only; the lifecycle
baseline now supplies Edit, Delete, and whole-Alternative Resolve. New results belong
in a separate resume report with a new HEAD/APK/session identity.

Run normal and strict builds first, then:

```sh
python3 tools/device/test-syncthing-e2e.py
python3 tools/device/qualify-syncthing-e2e.py build
python3 tools/device/qualify-syncthing-e2e.py sign
python3 tools/device/qualify-syncthing-e2e.py install
python3 tools/device/qualify-syncthing-e2e.py serve --folder Totipo-M3D-Test
```

`sign --observer-only` signs the observer without reading production build outputs.
`sign` requires the existing local M3C qualification signer; it checks the certificate
against the historical M3C signed release. `install` independently checks the installed
certificate before updating. No install fallback, uninstall/reinstall or data reset occurs.
Keep `serve` running in its own terminal or agent execution session. It launches the real
MainActivity in a fresh instrumentation process **once**, before the first checkpoint.
Human UI actions then use the actual application's ordinary controller/session.
Starting new instrumentation for each snapshot would invalidate same-session evidence.

First capture requires the correct persisted folder binding. Select the disposable child
through the ordinary picker. Root name, persisted grant and exact binding continuity are
checked before each inventory. The controller worker serializes local observation with
product work; OPEN snapshots use existing coordinator/bridge SPI. Closed snapshots use the
exclusive owner lease. No separate store handle is opened during a live session.

## Checkpoint protocol

The agent runs `begin`, presents its printed HUMAN ACTION, waits for explicit human completion,
then runs `finish` with the actual observation and expectations. Example:

```sh
python3 tools/device/qualify-syncthing-e2e.py begin --label A-sync \
  --action 'Desktop: Create in Totipo-M3D-Test, add public Totipo M3D / desktop-first@example.test token; wait for Syncthing up-to-date on BOTH endpoints.'
# Human explicitly confirms completion; no password or secret enters these arguments.
python3 tools/device/qualify-syncthing-e2e.py finish --label A-sync \
  --human 'Desktop created disposable vault and first token.' \
  --transport up-to-date-both --layout objects --local-empty --writable
```

Before desktop Create, first complete an `A-empty` checkpoint with `--layout empty
--local-empty --writable`. Do not create the desktop vault before this empty baseline.
Human passwords are entered only in product UI. Use `Totipo M3D` issuer prefixes and public
`@example.test` accounts; the observer refuses to persist other token metadata except the
exact human-identified public fixture typo `android@example.tes` used in this run.

The runner writes sorted sanitized provider inventories, byte sizes, SHA-256, local facts,
human observations and expectation results under ignored `.gradle/m3d-syncthing/`.
It rejects overwritten checkpoint labels. `FAILED.json` stops further scenario actions;
only observation/build commands remain usable for diagnosis. Never delete the latch to
continue a failed suite. A capture failure is an evidence failure, not proof of a product defect.
`CAPTURED_ONLY` is not a scenario PASS. The agent must inspect object deltas/history and the
human desktop result in addition to assertions. Transport status is always human-reported.

## Current baseline coverage limit

The live run stopped at E: this committed M3C baseline has Add and Show code, but no Android
Edit/Delete/Resolve UI/controller commands. A–D passed; E was NOT_EXECUTABLE. F–M were not
run. The table below describes the requested full qualification protocol, not available
product capabilities or completed coverage. Do not bypass missing UI with test-only semantic
mutations. A separate product milestone is required before completing this protocol.
The live Android-authored account is `android@example.tes` (public fixture typo), replacing
the planned `android-added@example.test` in D. Observer reload for that exact metadata
exception ended session continuity; A–C continuity and post-unlock D continuity are separate.

## Required sequence and Android assertions

For every row, use a new `begin` / `finish` pair; multiple rows intentionally separate
transport from explicit local Import/Publish. All successful OPEN checkpoints should use
`--match --state OPEN --conflicts 0 --unresolved 0` except actual conflict checkpoints and L.
After Join, use `--same-session --vault-unchanged` where no deliberate lock/restart/vault
switch occurs. `--provider unchanged` compares all names, kinds, sizes and hashes.
Use `--transport up-to-date-both` only after explicit human confirmation, including both ends.

| Checkpoint | One human action | Required Android checks / finish flags |
| --- | --- | --- |
| A-empty | Confirm dedicated provider child is empty and local vault absent | `--layout empty --local-empty --writable` |
| A-sync | Desktop Create + first public token, wait for real sync | `--layout objects --local-empty`; inspect exact 87-byte VAULT, one namespace, all canonical objects 1024 bytes |
| A-join | Android Join, enter password | `--tokens 0 --provider unchanged --match --state OPEN`; exact bytes and both session identity equality results |
| A-import | Android Import changes | `--tokens 1 --account desktop-first@example.test --provider unchanged --same-session --vault-unchanged --match` |
| B-sync | Desktop add second token; sync | `--provider gains --semantic-unchanged --local-unchanged --vault-unchanged --same-session` |
| B-import | Android Import | `--tokens 2 --account desktop-first@example.test --account desktop-second@example.test --provider unchanged --same-session --vault-unchanged --match` |
| C-sync | Desktop edit first account to desktop-edited@example.test; sync | `--provider gains --semantic-unchanged --vault-unchanged` |
| C-import | Android Import | `--account desktop-edited@example.test --absent-account desktop-first@example.test --conflicts 0 --provider unchanged --same-session --vault-unchanged --match`; inspect immutable local history |
| D-local | Android add android-added@example.test | `--account android-added@example.test --provider unchanged --same-session --vault-unchanged`; identify local object absent remotely |
| D-publish | Android Publish | `--provider gains --match --vault-unchanged --same-session` |
| D-idempotent | Immediate second Publish | `--provider unchanged --local-unchanged --semantic-unchanged --match --same-session` |
| D-desktop | Wait for sync; desktop refresh/reopen | `--provider unchanged --semantic-unchanged --desktop 'actual observation'`; desktop token appearance is HUMAN-OBSERVED |
| E-local | Android edit to android-edited@example.test | `--account android-edited@example.test --provider unchanged --vault-unchanged` |
| E-publish | Android Publish | `--provider gains --match --vault-unchanged` |
| E-desktop | Sync; desktop observes edit | `--provider unchanged --desktop 'actual observation'` |
| F-pause | Confirm both endpoints agree, pause BOTH | `--provider unchanged --semantic-unchanged --transport paused-both` |
| F-local | Desktop edits same starting token to desktop-conflict@example.test; Android edits it to android-conflict@example.test | `--provider unchanged --account android-conflict@example.test --transport paused-both` |
| F-publish | Android Publish while paused | `--provider gains --match --vault-unchanged --transport paused-both` |
| F-sync | Resume BOTH; wait for sync | Record real full inventory, including natural conflict siblings; `--semantic-unchanged --vault-unchanged` |
| F-import | Android Import; desktop reports its state | `--conflicts 1 --provider unchanged --same-session --vault-unchanged --match --desktop 'actual conflict state'`; inspect both alternative accounts |
| G-sync | Desktop Resolve to chosen whole value; sync | `--provider gains --semantic-unchanged --vault-unchanged` |
| G-import | Android Import | `--conflicts 0 --unresolved 0 --account <chosen-public-account> --provider unchanged --same-session --vault-unchanged --match` |
| H-conflict | Repeat F as separate checkpoints with a second independent conflict | Same assertions, new labels and public accounts; no shortcut manufacture |
| H-resolve | Android actual Resolve UI | `--conflicts 0 --provider unchanged --same-session --vault-unchanged` |
| H-publish | Android Publish resolution | `--provider gains --match --vault-unchanged` |
| H-desktop | Sync; desktop reports convergence | `--provider unchanged --desktop 'actual chosen whole value'` |
| I-desktop-sync | Desktop delete first disposable token; sync | `--provider gains --semantic-unchanged --vault-unchanged` |
| I-import | Android Import | `--absent-account <deleted-account> --provider unchanged --vault-unchanged --same-session --match`; inspect tombstone/current semantics and history |
| I-local | Android delete another token | `--absent-account <deleted-account> --provider unchanged --vault-unchanged` |
| I-publish | Android Publish deletion | `--provider gains --vault-unchanged --match` |
| I-desktop | Sync; desktop reports deletion | `--provider unchanged --desktop 'actual deletion state'`; retain old immutable objects |
| J-before | Confirm fully synced conflict-free state | Capture full projection/inventory with `--conflicts 0 --unresolved 0 --match` |
| J-restart | Human restarts both Syncthing endpoints and desktop; agent force-stops/relaunches app | Runner `restart`, then restart `serve`; do not reset data or choose folder again |
| J-unlock | Human normal unlock; confirm sync | Compare saved J-before vs new evidence for binding READY, VAULT hashes, tokens/history. Session must be new; never assert same-session across process death |
| J-import / J-publish | Explicit identity-gated Import / Publish | Separate checkpoints, `--provider unchanged --match --vault-unchanged --semantic-unchanged` |
| K-empty | Confirm SECOND child selected and empty; fresh disposable local vault has Android-authored token | Restart observer for `Totipo-M3D-Android-First`, capture `--layout empty --tokens 1` |
| K-initialize | Android Initialize | `--layout initialized --match --provider gains`; exact VAULT/namespace and zero token objects |
| K-publish | Android Publish | `--layout objects --provider gains --match --vault-unchanged` |
| K-desktop | Sync; desktop Open and password entry | `--provider unchanged --desktop 'actual Android-authored token shown'` |
| L-import / L-publish / L-initialize | Bind local vault A to the other disposable synchronized vault B; attempt each action separately | Restart observer for B; each `--different-vault --provider unchanged --same-session --local-unchanged`; preserve both VAULT hashes |
| M-sync | In desktop disposable fixture, add unrelated.tmp, vault.sync-conflict-manual-test and an invalid object sibling; real sync | Record names/hash/size; explicitly MANUALLY INTRODUCED, never call these natural conflict artifacts |
| M-import / M-publish | Android Import / Publish separately | `--provider unchanged --semantic-unchanged --match --vault-unchanged --same-session`; inspect canonical-state invariance |

A transient transfer window may be observed with a few named `capture` requests while the
human confirms active synchronization. If practical, one controlled UI operation may be
attempted with before/after capture. Never corrupt canonical files or repeatedly race actions.
If transfer is too fast or no window is available, record NOT OBSERVED.

## Review limits and restoration

The observer checks actual session object identity inside one process; UUIDs describe the
observation session only, not protocol IDs. Identity equality is reported as booleans and
exact VAULT byte comparison, not raw VaultId. No secret/TOTP rendering is accessed.
`refresh_requested` is honestly NOT_EXPOSED_BY_CONTROLLER: fixed UI status and finished
semantic projection are observable, internal operation report enums are not retained by the
current controller. Do not infer unavailable internal outcomes.

J requires explicit comparison across observer lifetimes; generic finish assertions alone
cannot prove restart persistence. Provider inventories are bounded and sampled, not atomic
filesystem snapshots. Duplicate names/loading/cycles/capacity/read failures block complete
qualification. Provider stability is checked by full inventory/hash observations bracketing
local capture; it does not establish Syncthing convergence.

At suite end retain disposable folders and evidence unless human requests fixture cleanup.
Do not reset app data, delete conflict siblings or rename roots for cleanup. Record exact
restoration/retention decision. Remove only the standalone observer APK when no longer needed:
`adb uninstall org.totipo.syncthingqualification`. Keep the signed production release installed.
Human runs `nix flake check path:.` and `nix build path:.`; agent never runs Nix. Final report
must distinguish AGENT-VERIFIED, HUMAN-OBSERVED and HUMAN-REPORTED TRANSPORT, and remain
INCOMPLETE until every required live scenario and human Nix check has evidence.
