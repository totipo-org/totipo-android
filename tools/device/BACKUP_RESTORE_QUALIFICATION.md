# R4B disposable-device restore qualification

Procedure only: no device execution is claimed. Run on API 30 and a current
supported Android release, with final strict-clean APKs. Java 0.3.1 uses
java.util.HexFormat (API 34+): API 30–33 compatibility is an explicit release
blocker. Record actual failure if encountered; do not increase minSdk or add an
Android hex/crypto workaround. Host passes cannot qualify either device.

Use only a disposable device/profile and disposable vaults, never production
secrets or existing personal app data. Record source HEAD + working-tree diff,
APK SHA-256/version, Android API/build/model, provider versions and every outcome.
Keep exact fixture ZIP hashes from app/src/test/resources/backup-profile-1/README.md.
Public fixture password is `fixture password`. Prepare independent copies outside
app data; preserve the exact original ZIP for every retry. Do not clear app data
as a recovery step. A separate fresh disposable profile can test another fixture
with the same protocol identity without deleting a retained root as a shortcut.

1. On each API, local-only fresh profile: Vaults → Add vault → Restore from backup.
   Select empty.zip from local Documents, authenticate, name and confirm. Confirm
   completion remains in Recovery until ordinary password opening; then Lock and
   reopen. Confirm Local only, no Sync folder/grant, no biometric enrollment record,
   no network/broad-storage permission. Test packaged release as well as debug.
2. In independent profiles restore history.zip and missing-parent.zip. Confirm
   exact encrypted bootstrap/object bytes through disposable debug app-private
   inspection or an instrumented harness under the same authority. Do not open a
   second canonical store alongside the app. History includes historical assertions,
   conflict branches and tombstone; inspect conflict/deleted alternatives. Confirm
   missing-ancestry warning, explicit limited-history confirmation and normal open.
   Re-export through shared Java and independently compare canonical membership and
   representations; a whole-ZIP digest comparison only applies to canonical writer
   bytes, because legal original timestamps may differ.
3. Try wrong password, truncated/corrupt ZIP, structurally legal invalid encrypted
   object, oversized archive, repacked retry ZIP and failed/zero-progress reads.
   No authenticated-invalid archive may allocate a root. Authentication diagnostics
   must not contain raw provider exceptions, filenames or passwords.
4. Attempt same-identity restore with ESTABLISHED, DETACHED, FORGETTING, ordinary
   attempted PENDING and RESTORING retained entries. Use deterministic harness seams
   to prepare states where necessary. Established offers Open existing vault;
   retained recovery keeps its meaning. Missing/unavailable/malformed/ambiguous
   identity blocks allocation. No second root, merge, overwrite or cleanup.
5. Cancel picker; rotate with picker/password/confirmation displayed; return late
   picker results after cancellation and after process kill/restart. Passwords must
   not return from widget/Bundle state. Pick with ZIP and all-files filters. Background
   during reading/authentication/installation, leave the picker beyond 15 minutes,
   and test fixed journey expiry with no interaction. No late result starts another
   journey or vault. During entered blocking I/O, selection stays unavailable until
   real retirement; never release authority merely because a timeout elapsed.
6. Using disposable fault instrumentation, stop at each durable boundary: registry
   RESERVED, ATTEMPTED, container allocation, provider marker creation/force,
   bootstrap publication/force, first/middle/final object, final inventory and
   location barriers, INSTALLED update, normal open/observation and atomic promotion.
   Inject before-write failures and lost acknowledgements separately. Restart:
   no automatic install/open/promotion, Create/Join, new root or rollback. Explicit
   exact-ZIP reauthentication retries the original allocation. PARTIAL/UNCERTAIN
   cannot appear under Your vaults. A complete bootstrap with missing objects must
   not establish the vault. Missing/torn intent and incompatible occupied objects
   remain preserved for investigation. A registry.next requires metadata recovery.
7. Exercise actual local and available removable/cloud SAF providers, read revocation,
   stalled reads, provider death, low private storage and close errors. A provider
   read/close failure must not admit authentication of a prefix. For canonical I/O,
   use real coordinated NIO fault seams for bootstrap/object/location failures;
   SAF errors alone do not qualify canonical durability. Retain PARTIAL, UNCERTAIN
   and REJECTED evidence independently, including no-write directory residues.
8. After successful ordinary opening, exercise explicit Lock, inactivity lock,
   Change Vault, process restart and subsequent normal biometric opt-in. Existing
   enrollment stays absent until normal explicit opt-in. Automatic publication
   remains absent without a new, separately configured Sync association. Restore
   must work with no Syncthing or Sync provider at all.
9. Inspect accessibility labels, TalkBack, large fonts and dark-system rendering
   for actual restore forms and Recovery actions. Report observations separately
   from node/layout inspection. Never claim physical fault coverage based on JVM
   tests or this procedure.

Attach logs without secrets, outcome/registry phase inventory and preservation
checks. Report API 30 failures, unexecuted phases, unavailable provider types and
unsupported fault instrumentation explicitly. Qualification requires resolving
compatibility through a qualified published shared Java release and normal pin,
cache/provenance review; this procedure authorizes no release or reset.
