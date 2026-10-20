# Android multi-vault UI ownership

`TotipoApplication` owns the existing `VaultSelection`, its shared admission authority,
registry and SAF grant owner. MainActivity observes selection snapshots and captures
one immutable controller/activation for its lifetime. A selection change clears its
presentation and recreates the Activity. Activity-local flags select presentation
only; they cannot activate a vault or recover a session. There is no selector Activity,
second session authority or inactive-vault coordinator.

Startup policy is the pure `VaultUiModel.startupSelection`: exactly one non-detached
entry, established and matching last selection, may receive a controller. Otherwise
selection is explicit. A fresh unused reservation remains unselected until Create or
Join. Incomplete metadata never becomes fresh-install permission. Selection snapshots
also notify unselected presentation after bounded registry-management changes.

Java operation classes remain A/B/C for projection/construction, D for observation,
E/F for publication, G for Create/open/close and H for detached Join authentication.
The existing observation and publication barriers are unchanged. Change Vault is an
application lifecycle action, stronger than Lock: `VaultSelection.retire` releases
only after A-MV2 success, including actual provider drain and completion handoffs.
MainActivity synchronously clears sensitive widgets, cancels prompts/forms, requests
retirement and renders its neutral state. Failure retains the prior selection.
Back from selection exits; it never reconstructs an old activation implicitly.

## Metadata lifecycle

The bounded checksummed atomic registry keeps its layout and ordered immutable
entries. Schema 2 adds two explicit enum values after schema-1 PENDING/ESTABLISHED:

| State | Ordinary selection | Meaning |
| --- | --- | --- |
| PENDING | Explicit resume/open | Durable reservation; attempted flag forbids blind Create/Join retry |
| ESTABLISHED | Select, or single-entry startup | Promoted only after authenticated Java success |
| FORGETTING | Cleanup retry only | Durable intent; scoped cleanup may be partially complete |
| DETACHED | Explicit recovery only | Hidden; root/ID remain reserved and in SAF inventory |

Schema 1 reads without rewriting; the next successful metadata update writes schema 2.
Unsupported versions and invalid states remain fail-closed. A stale last-selection
hint is discarded in memory without rewriting metadata; it never authorizes another
entry. Checksums, entry membership, paths and root inventory still fail closed. The unchanged registry
transaction verifies committed bytes, forces a create-new temporary, atomically renames,
forces its parent and verifies exact readback. Uncertainty poisons the writer and
retains evidence. No schema upgrade changes canonical bytes, Keystore format or Sync
preferences. IDs are never removed from metadata or recycled.

Create/Join use A-MV1 reservation and attempted-creation ordering. The sole initial
unused reservation is deliberately reused. Cancel retires the controller and retains
its exact pending entry; no rollback deletes local evidence. Join's transient candidate
session closes before normal enrollment, and provider bytes are freshly rechecked.
`LocalReplicaOwner.refusePossibleDuplicate` runs after candidate authentication and
before enrollment. UI activation also refuses possible duplicates. Bounded reads of
known roots stop at 88 bytes, use no-follow paths and Java structural recognition.
They grant no authority, open no sessions and create no identity cache. All retained
roots participate. Duplicate histories remain untouched for explicit recovery.

## Forget transaction

Management is reached after complete retirement; selected entries use Change Vault
first. Application serializes UI management admission. `VaultForget` reserves the
existing selection authority for short-lived scoped maintenance without holding its
monitor across I/O. Snapshot reads remain responsive; activation is refused. Nested
biometric cleanup is allowed only on the same thread and entry. This reservation
is not a session or a second controller domain.

1. Verify the entry is fully retired, then persist FORGETTING.
2. Run A-MV4's Application credential cleanup for that exact entry.
3. Retire its binding with A-MV3's journaled `save(null)` and shared grant owner.
4. Persist DETACHED only after these operations affirm success.
5. Keep grant cleanup records in the same complete inventory. Shared/uncertain
   grants remain retained, visible and retryable without speculative revocation.

No step deletes canonical encrypted files. Failure before intent runs no cleanup;
after intent, restart exposes cleanup retry. Biometric deletion is idempotent and
uses its own persistent blockers. A failure after credentials are removed does not
orphan data: FORGETTING still names the exact root. Failure before final rename keeps
FORGETTING; final rename with lost acknowledgement can leave DETACHED on disk, with
cleanup already complete and the same root accounted for. Registry uncertainty still
requires restart. Interrupted registry temporaries require manual metadata recovery.

Recover selects a detached root for normal authentication and cannot Create there.
The existing lease's authenticated promotion reestablishes it only after password
success. Failed authentication leaves DETACHED. Sync remains disconnected and the old
biometric convenience credential remains removed. Forgotten roots remain subject to
unknown-root, missing-root and duplicate-recognition checks. There is no arbitrary
filesystem adoption, general browser, automatic merge or data-erasure feature.

## Callback and platform limits

Pickers retain their originating activation only across in-memory configuration
recreation; process death drops it. Prompts and external drafts retain the original
controller. Controller retirement rejects late admission, clears presentation/session
generations and drains entered callbacks before replacement. No old callback looks up
a replacement controller. Unselected ingress is rejected before parsing an OTP draft.
Normal rotation retains the Application session; a prompt requires a fresh operation.

The 15-minute monotonic inactivity policy and background accounting are unchanged.
Keystore isolation protects per-entry convenience credentials under Android's model;
it does not protect decrypted reusable passwords against process compromise. Private
registry/data do not survive guaranteed app-data removal or uninstall. There is no
rollback resistance, secure erasure or forced cancellation of arbitrary provider I/O.
Physical coverage and remaining gates are tracked in the A-MV5 review report.

## Integrated operation and qualification

The [A-MV6 integrated qualification report](review/A_MV6_INTEGRATED_MULTI_VAULT_QUALIFICATION_REPORT.md)
records the current environment, evidence matrix and remaining integration gates.
A-MV6 has demonstrated physical desktop-created Join and bidirectional real
Syncthing convergence, alongside local-only and independently synchronized vaults.
Final corrected-APK integration, biometric, recovery and focused UI checks pass;
the human Nix gate passed on frozen inputs. A-MV6 qualification is complete for
the documented environment and ready for a subsequent release decision, without
authorizing publication.
A-MV5's successful controller Join tests and actual SAF initialization are distinct
from successful desktop-created Join after real Syncthing propagation. Historical
M3D passes retain their older source/client scope. None establishes the current
three-vault journey by itself.

Each synchronized identity needs its own provider folder and binding. Desktop writes
its filesystem store; Syncthing replicates immutable evidence into Android's shared
folder; SAF imports into Android's private canonical root. Android publishes back
through SAF, followed by Syncthing delivery and desktop observation. Local save,
provider publication, peer propagation and logical convergence need separate evidence.
Provider readiness and registry metadata are never protocol identity authority.
A rejected folder choice keeps the previous binding. Its configuration notice
remains visible across ordinary Sync until a successful configuration action or
explicit Retry access; cancellation alone does not acknowledge the notice.

An independent local-only entry has no folder, permission or transport retry state.
It uses the same local authoring, password, biometric and retirement implementation.
Equal passwords never grant cross-entry authority. Selection retires the previous
controller completely and never schedules Sync for an inactive entry. A hung
third-party provider can hold retirement until process restart; no timeout releases
its ownership. Revoked access remains configured until explicit reselection or
Disconnect, and conservative shared-grant retention can leave deferred cleanup.

Forget retains reserved IDs/roots and encrypted history. Recovery depends on that
installation's private data and ordinary password authentication. Corrupt registry,
unknown/missing roots and uncertain Create/Join/Forget writes require explicit
recovery while preserving evidence. App-data removal/uninstall is not recovery.
No destructive cleanup, automatic repair, legacy migration, password change,
inactive background scheduler or cross-vault merge is supplied.

The Activity uses an explicit light native theme. Actual dark-system rendering and
TalkBack must be reported separately from accessibility node/layout inspection.
Device/process compromise, best-effort JVM wiping, conditional clipboard clearing,
lack of rollback protection and unbounded third-party SAF calls retain their
documented limits. Passing host gates is not integrated release qualification.
