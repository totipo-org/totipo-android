# M1E — Read-only provider transport snapshots

## 1. Starting state

Started on `main` at `66923d77390f9613a3e0e88b7b2c2377721ce260` (committed M1D).
Ran `git branch --show-current`, `git rev-parse HEAD`, and `git status --short`
before editing; working tree was clean. No AGENTS.md guidance was found.

## 2. Scope

Production read-only SAF transport observation only. The private NIO local
replica established by M1D remains canonical. No reconciliation writes/import,
provider mutation, export, journal, background sync, authentication, or product
picker/UI. An already selected/authorized tree is supplied by the caller;
selection, persisted grants and their lifecycle remain future product work.
Debug probes are unchanged and are not reused as production APIs.

## 3. Production classes/model

All new production classes are in `org.totipo.android.provider`:

- `ProviderTreeReader`: framework boundary accepting ContentResolver and tree Uri;
  `snapshot(expectedMaximum)` and generic `readCandidate(epoch, document, maximum)`.
- `ProviderSnapshot`: immutable Tree, Document, Listing, Bytes, Directory, Scan
  records and explicit state/issue enums. Lists are copied and unmodifiable;
  byte arrays are copied on construction and access.
- `ProviderTraversal`: package-private pure traversal policy and Source test seam.
- `BoundedProviderRead`: package-private stream ownership and bounded byte capture.

Tree authority/URI/root ID and document URI/ID/queried parent ID remain opaque.
Display name, MIME, optional reported size and flags are observed metadata.
No ID parsing into paths, name normalization, timestamp authority or grant storage.
Missing metadata remains null and makes enumeration incomplete.

## 4. Observation/completeness semantics

`COMPLETE`, `INCOMPLETE_LOADING`, `INCOMPLETE`, `UNAVAILABLE` have conservative
combination precedence in that order. Issues separately retain loading, provider
error, null cursor, exception, malformed row, resource exhaustion, repeated
identity, and interruption. A complete listing describes enumeration only;
it does not authenticate its rows or establish protocol absence.

A scan has a fresh UUID operation epoch shared by all its listings/read attempts.
Same epoch != atomic provider snapshot. Concurrent provider changes may produce
mixed observations. Failed child reads make aggregate coverage incomplete while
other captured rows/bytes remain accessible. An unavailable child listing can
coexist with a complete root listing. Aggregate state never erases local states.
Scan-level issues record traversal/read-budget exhaustion; individual listing
and byte issues remain attached to their observations.

## 5. `objects-v1` duplicate-directory traversal

Only exact `objects-v1` directory MIME rows are descended. All root rows are
retained within the root bound, including same-name regular-file obstructions
and directory candidates beyond the descent bound. Distinct provider IDs with
the same display name are independently traversed. Repeated directory IDs or
an alias of the root are retained as directory evidence with an incomplete
REPEATED_DIRECTORY child observation and are not queried again.

Traversal is root -> exact directory candidates -> direct children, with no
further descent. Reaching the directory cap with another eligible row makes the
scan incomplete. There is no canonical provider directory choice.

## 6. Candidate enumeration

Child listings preserve every observed row, including duplicate display names,
unknown metadata, noise, and nested directories. The small pure predicate accepts
only exactly 64 lowercase ASCII hex characters. Matching nondirectory names
identify byte-read candidates only; they do not establish valid OBJECT_IDs or
valid objects. Nested directories are never read as objects. Child metadata,
including names that are not candidates, is retained under the hard row bound.
There is no document deduplication by name (or even by identity); repeated rows
remain evidence and consume budget. Directory candidates contain independent
read results, representing A/object-X, A/object-X and B/object-X without collapse.

## 7. Bounded reads/resource limits

| Resource | Hard limit |
| --- | ---: |
| Root rows retained | 256 |
| Exact directory observations/descent slots | 8 |
| Child rows per directory retained | 1,024 |
| Total eager candidate read-result slots | 512 |
| Requested maximum per read | 65,536 bytes |
| Overflow probe | 1 byte |
| Total eager scan byte reservation | 1,048,576 bytes |

Each listing probes one additional row to distinguish exact-limit EOF from
exhaustion. There are at most 9 queries and 8,448 retained metadata rows. Extra
eligible directories/candidates produce explicit incomplete scan coverage;
rows remain available in their bounded listings. For eager reads, maximum + 1
is reserved before each attempt, even if a short read returns less. Budget
exhaustion yields UNAVAILABLE/RESOURCE_LIMIT for remaining candidate slots.
No allocation uses reported size or provider row count. Framework cursor and
metadata-string allocations are provider/framework managed; the application
bounds retained rows and byte buffers, not a provider process's internal memory.

`PRESENT` means exactly the requested maximum followed by EOF; `SHORT` means EOF
before that maximum; `OVERSIZED` preserves maximum + 1 observed bytes and stops.
`MISSING` means opening raised FileNotFoundException, not proof of protocol
absence. `UNAVAILABLE` represents null streams, read/close/security/provider
failures or interruption and preserves the successfully counted prefix.
Reported size never changes reading or allocation. Streams and cursors use
try-with-resources. Zero-returning streams fall back to a single-byte read.
Interruption is checked before opening/querying and between reads/rows.

`snapshot(1024)` supports immutable object transport. A root row can be passed
to `readCandidate(scan.epoch(), row, 87)` for later VAULT work. Standalone reads
have the per-read cap; caller scheduling controls aggregate standalone work.
Only eager snapshot reads share the scan reservation. Defensive copies incur
bounded transient overhead beyond the retained-byte reservation. Provider calls
can block: these bounds do not constitute a wall-clock deadline. Worker-thread
use is documented; no scheduling architecture is introduced.

## 8. Provider loading/error handling

Cursor extras are inspected before and after enumeration. A loading flag remains
incomplete even if later cleared during that query. EXTRA_ERROR, null cursor,
and query/iteration/extras/close exceptions result in unavailable observations,
never empty-directory success. Previously appended rows survive later failures.
Provider error text and exception messages are not propagated into diagnostics.
The loading/error interpretation follows the Android
[DocumentsContract API reference](https://developer.android.com/reference/android/provider/DocumentsContract).

M1B evidence is preserved without repeated experiments: ExternalStorageProvider
allowed tree grants and full private staging, with exact-create conflict suffixing
and preservation of the existing document. Google Drive allowed tree grants,
reported loading, retained duplicate names and partial 256-byte documents,
materialized complete 1024-byte bytes, showed lagging size metadata, and retained
IDs after force-stop; sync-state flags were advisory. Proton Drive was visible
in Files but not tree-selectable in the observed configuration. This layer makes
no provider-brand assumptions, uses no sync-state authority, and performs no writes.

## 9. Java-validation integration seam

Pending parallel Java candidate-validation result. No CandidateValidator stub,
parser, crypto, or authentication is implemented. Future integration can pass
bounded candidate bytes and opaque provenance to the released Java API. The
transport model imposes no dependency on its final signature. Short/oversized
or unavailable observations must remain evidence rather than fabricated validity.

## 10. Security analysis

Only framework query and openInputStream read access is used. Document read URIs
are rebuilt using the selected tree and opaque ID rather than following a
caller-supplied locator. The tree association is checked. Provider metadata is
untrusted and size/flags are advisory. No mutation API, write descriptor, owner
acquisition, local store call, third-party dependency, or protocol implementation
is introduced. A JVM source guard checks this production package for forbidden
mutation entry points, file-descriptor access, local owner/storage and parser
references. It is a regression guard, not a proof against arbitrary future code.
Grant revocation/disappearance remains structured unavailability. The caller
must protect snapshot contents and schedule operations appropriately; this layer
does not log bytes, provider error text, or credentials.

## 11. Tests

Ten production JVM tests cover lowercase-hex naming; duplicate directories and
documents, obstructions and nonrecursion; immutable lists/bytes; state combination,
loading and local failures; repeated-ID/root aliases; all hard budgets and exact
limit completion; epochs; actual byte EOF/overflow for 0/87/1024 maxima; prefix
preservation, missing/null streams, interruption and deterministic closure;
zero-returning streams, close failure; and the source safety guard.

The package-private Source seam supplies bounded synthetic observations for
pure traversal. Framework query construction, projection, row/extras extraction,
partial-row retention and closure are small code-review boundaries, compiled and
linted with Android SDK. No ContentResolver mocks, Robolectric, instrumentation,
new dependency or new physical-provider qualification requirement.

## 12. Validation

### Agent

Passed all requested ordinary checks:

```sh
./gradlew check :app:assembleDebug :app:assembleRelease
./gradlew --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug :app:assembleRelease
python3 tools/verify-wrapper.py
python3 tools/verify-apk.py app/build/outputs/apk/debug/app-debug.apk
python3 tools/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk --unsigned --no-debug-probe
git diff --check
git status --short
git diff --stat
git diff --cached --stat
```

Normal build succeeded in 2m 18s; forced offline rebuild succeeded in 29s
(92 tasks executed). All 19 JVM tests passed: 10 new transport tests and 9
existing tests, with zero failures/errors/skips. Lint passed with four warnings
in unchanged files (wrapper update notice and three debug-probe text literals);
no warning refers to new production code. Existing AAPT2 override and Gradle
deprecation notices remain. APK checks confirm NIO/core/BC boundaries and
unsigned release/debug-probe exclusion. Wrapper pins passed. Additional
no-index whitespace checks covered every new untracked file (exit 1 means a
file differs from /dev/null, not a whitespace failure).

Final APK SHA-256:

- Debug: `d3a6c2e4e3a51854564ced910b2b7ff47362f6dc2ffaeeef896ba17ce1bd3f63`.
- Release: `8b289e2b8a77019be9e4493ff3825e81f01097cb145273ecb11c5cf893ae152e`.

### Human Nix

Human reported both `nix flake check path:.` and `nix build path:.` passed
on 2026-10-07. No Nix command executed by the agent. Dependency graph, locks,
verification metadata and package-cache inputs are unchanged; no dependency
cache regeneration warranted. No manual Gradle rerun requested.

### Human device

No new run required. Existing M1B provider evidence supports the small read-only
framework boundary; this milestone adds deterministic tests rather than repeating
qualification. No claim that the new production path has been exercised on device.

### Remote CI

Not run for this unstaged/uncommitted review candidate; no push or release.

## 13. Recommended next milestone

Integrate the released Java candidate-validation API for immutable read-only
validation of bounded snapshots, preserving provider ambiguity and incomplete
coverage. Reconciliation/import/export and grant-selection UI remain later work.

## 14. Final Git state

Final state on `main`, HEAD `66923d77390f9613a3e0e88b7b2c2377721ce260`:

```text
 M README.md
?? app/src/main/java/org/totipo/android/provider/
?? app/src/test/java/org/totipo/android/provider/
?? review/M1E_READ_ONLY_PROVIDER_SNAPSHOT_REPORT.md
```

README tracked diff: 6 insertions, 3 deletions. Git diff --stat excludes the
six untracked files: four production classes, one test class and this report.
Git diff --cached --stat is empty. Nothing staged, committed, tagged, released
or pushed. Dependency/cache inputs and debug probes remain unchanged.
