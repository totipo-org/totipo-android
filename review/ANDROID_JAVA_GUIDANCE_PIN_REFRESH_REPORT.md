# Android Java guidance pin refresh and F08 disposition

## 1. Starting HEAD/state

2026-10-10: clean `main`, HEAD
`ad5511811f2cabcb866b274aa0cda1e1c2bd2b91`; `git status --short` was empty.
Read AGENTS.md first, then README, Java provenance, package.nix, flake.nix,
CI, relevant verifier/boundary wiring and subsystem evidence. Required committed
prerequisites: operation-model audit `11d5c1215217470beff62093e0249817b1f8f2cd`,
F09 ownership fix `ad5511811f2cabcb866b274aa0cda1e1c2bd2b91`, qualification ladder
`ac5e7893ff0e1a876d496a6d34bd12c2a4592427`. Runtime remains Java 0.2.0 / v1/r19.

## 2. Classification

**DOCS_ONLY**, plus **provenance / architecture follow-up**, classified before
editing. No production behavior, tests, tools, build, package or CI changes.

## 3. Previous guidance pin

`b03f5b22f367723ce4a3bddf0a56b159a06f32cf` retained the operation model and
subscriber-threading clarification. The historical
[architecture audit](ANDROID_JAVA_OPERATION_MODEL_AUDIT.md) remains unchanged.

## 4. New exact Java guidance commit

**`f0a028676c1801a10b2d8d2650bf9c24357d1fa1`**,
[Clarify Java observation composition guarantees][commit], from canonical
`totipo-org/totipo-java`. Resolved via read-only remote discovery and a bare
Git evidence clone outside the Android workspace; no Java working tree was edited.
Git ancestry confirms the previous guidance pin is an ancestor. The selected
commit's parent is `8b24b8a1de1a20f834028b2b536f33d6a8f2e7f5`.

The commit contains exactly API_DESIGN.md, VaultSession.java, PublicApiTest.java
and review/JAVA_OBSERVATION_COMPOSITION_CLARIFICATION_REPORT.md changes.
Its [committed report][java-report], sections 24–26, records 405 JUnit tests with
zero failures/errors/skips, final Java/publication/consumer checks, and one
human-reported final `nix flake check path:.` PASS after the input freeze.
This is committed upstream qualification evidence, not Java qualification run here.
The report's prospective wording about an eventual commit and its unstaged final
state describe the upstream work before it was committed; Git history independently
establishes that the finalized files and human PASS are now in this exact commit.
No SHA was inferred from that prospective wording or an uncommitted report.

## 5. Why the pin advances

The [observation-composition section][composition] supplies the missing public
contract behind Android's two-observation barrier. It specifies pass installation
before later pass reads, distinct completed-pass references and ordered latest
replay, with coordinated-store assumptions and explicit local-only limits.
Operation classes, valid immutable observations rather than currentness leases,
local TOTP semantics, absence of generic state-emission cancellation/global BUSY
requirements, and synchronous onSubscribe followed by asynchronous serialized
per-subscription callbacks remain present in the exact guidance.

## 6. Runtime artifact/source pins unchanged

Only reviewed application-operation guidance changes:

| Identity | Pin |
| --- | --- |
| Runtime artifact | `org.totipo:totipo-storage-nio:0.2.0` → `org.totipo:totipo-core:0.2.0` |
| Released runtime/source implementation | `d6310c177ae930df188fd4f5798622c935698b2e` |
| Reviewed application-operation guidance | `f0a028676c1801a10b2d8d2650bf9c24357d1fa1` |

No Maven/JAR hashes or released source provenance changed. Published 0.2.0
Javadoc bytes remain unchanged; agents read the later repository guidance separately.

## 7. VERSION/spec applicability and executable invariance

Read exact committed [VERSION][version]: **0.2.0**. [SPEC_PIN.md][spec] remains
**v1/r19**, specification commit `cdb4e91be1c6d3704874b2b92457ffe7be5e9084`;
its bytes equal the released source pin's SPEC_PIN.md.

Reviewed the complete milestone diff: production changes are confined to public
VaultSession Javadoc. API_DESIGN adds the contract; PublicApiTest adds two controlled
ordering/equal-content parameter cases and one coordinated external-import test.
No executable semantic or signature change. Compared all **94 production Java
files** under core/src/main/java and storage-nio/src/main/java from release to
new guidance: token sequences match after removing comments and insignificant
whitespace while preserving string/character literals. ApplicationSession and
ApplicationStates implementations are unchanged. No Android runtime correction
or dependency upgrade is required by this clarification.

## 8. API_DESIGN integrity

Canonical exact URL: [API_DESIGN.md at the guidance SHA][guidance].
Fetched exact [raw bytes][raw-guidance] independently and compared them with the
committed Git blob: identical. SHA-256:

```text
338837e3563446f8ec11f7bb47c0e4f3ff5502b7608e397f9b8c4d75372715fd
```

[VaultSession source/Javadoc][java-session] Git blob identity (SHA-1):
`9bb8733adb91c84bec6e94fa49d15a83158a960f`.
All new Java provenance links use immutable exact revisions, never main.

## 9. Observation-composition decision

The first accepted Finished is installed after the post-import baseline read,
although its pass may have begun before import. The second baseline is captured
after that acceptance and can be that state or a later one. A different Finished
then represents a later pass; that pass cannot start reading before the first
accepted state was installed, hence starts after import completion. Skipped
intermediate states only advance this ordering. Unrelated merge/save observations
obey the same order and may satisfy a wait. A subsequent same-session state() read
can advance further. This establishes local ordering, subject to the assumptions
below; it promises no progress deadline or remote result.

## 10. F08 disposition

**F08: CLOSED / SUPPORTED_WITH_ASSUMPTIONS.**
No Android runtime correction is required by the committed Java clarification.
This report prospectively supersedes only the guidance pin and F08 disposition.
It does not rewrite the architecture audit or claim a new Android adversarial
scheduling/device qualification.

## 11. Exact assumptions Android relies on

1. The same live library-created Java 0.2.0 session owns the entire barrier;
   stale, closed or replaced session results cannot authorize publication.
2. Successful immutable local import completes before the first baseline capture;
   failed or uncertain import cannot qualify.
3. Android releases its bridge before calling Java observation APIs.
4. Import and every Java store call, session, writer and handle share the same
   root-wide coordinated domain, supplying synchronization and storage visibility.
   Ordinary storage availability/errors remain relevant; imported immutable data
   is not removed or replaced before the relevant observation.
5. No independent writer bypasses that coordination.
6. Provider/store callbacks do not reenter semantic session operations.
7. Each wait has positive subscription demand.
8. Each wait rejects its captured baseline reference; baseline capture and
   acceptance/wakeup are correctly synchronized.
9. The second baseline is captured after the first accepted observation.
10. Terminal/failure/cancellation/closure/timeout never authorize outbound
    publication. A terminal wakeup itself is not Finished evidence.
11. The final authorization state is Finished and separately passes Android's
    existing diagnostic/integrity/transport policy.

These are the pinned contract's scope, not broader remote or arbitrary-provider
assurances. Whole-pass atomic store snapshots do not follow from per-call coordination.

## 12. Explicit non-guarantees

The barrier does not prove:

- Syncthing/cloud freshness;
- remote receipt;
- another device has observed the publication;
- remote history completeness;
- rollback resistance;
- current global heads;
- Java merge freshness;
- one observation pass per requestRefresh;
- one callback per request;
- correlation between a request and the first following Finished;
- delivery of every intermediate state.

This is a **local observation-ordering guarantee only**. Finished may contain
diagnostics; even diagnostic-free Finished does not prove absent unknown or
unavailable history beyond the pass's actual evidence. SAF/provider publication
policy remains Android's separate responsibility.

## 13. VaultState identity contract

Each separately completed observation pass in a **library-created Java 0.2.0
session** installs a distinct VaultState reference, even when semantic content is
identical. Reading/replaying the same reference is not evidence of another pass.
This does not generalize to arbitrary third-party VaultSession implementations,
create a public generation/freshness token, or preclude shared immutable internals.

## 14. requestRefresh is not an acknowledgement

`requestRefresh()` returning is not observation completion. It remains nonblocking,
coalescible and without request/result correlation. Close/fatal failure can prevent
pending work. Android derives the two-observation construction's safety from pass
ordering and distinct completed-pass states, not refresh acknowledgements.
Replay-latest can skip intermediate states without reversing pass order.

## 15. Read-only Android source inspection

Inspected current production at starting HEAD; these files remain untouched.

| Assumptions | Current source evidence |
| --- | --- |
| 1, 10 | [AndroidVaultController](../app/src/main/java/org/totipo/android/AndroidVaultController.java): startSync captures sessionGeneration and cancellation; syncObservationCurrent checks session, binding, OPEN state and grants before outbound. requestLock cancels outbound and advances generation before worker closure. publishOnWorker/outboundCurrent recheck ownership/cancellation and transport. Closed Java secret-backed validation refuses use. |
| 2, 3 | [ForegroundVaultCoordinator](../app/src/main/java/org/totipo/android/reconcile/ForegroundVaultCoordinator.java): sync imports the complete admitted immutable batch inside try-with-resources Bridge, releases it, then calls refresh. Controller startSync calls safeToPublishAfterImport before observeForSync. Failed/uncertain writes mark storage unsafe; incomplete, failed, cancelled and contradictory imports veto outbound. Operations.refresh explicitly rejects a held bridge gate. |
| 4, 5 | [LocalReplicaOwner](../app/src/main/java/org/totipo/android/LocalReplicaOwner.java) owns one process-wide app-private root lease; [CoordinatedPrivateStore](../app/src/main/java/org/totipo/android/reconcile/CoordinatedPrivateStore.java) uses one persistent NIO coordinatedDelegate, one fair gate for every SessionView SPI call and full bridge batch. No second delegate/independent writer is allowed. [ImmutableCandidateImporter](../app/src/main/java/org/totipo/android/reconcile/ImmutableCandidateImporter.java) publishes exact immutable objects create-only; this path removes/replaces none. |
| 6 | CoordinatedPrivateStore's SessionView and Bridge delegate to ordinary NIO storage without semantic session callbacks. Provider I/O returns detached scans to the vault worker; no session operation is called while holding the bridge. |
| 7–9 | ForegroundVaultCoordinator.observeForSync loops twice. Each iteration captures session.state(), establishes Long.MAX_VALUE demand in synchronous onSubscribe, and counts down only for a nonbaseline Finished in onNext. CountDownLatch/AtomicReference synchronize wakeup/failure; worker post-wakeup reads reject baseline and unfinished states. The next iteration captures its baseline only after first acceptance checks complete. |
| 10, 11 | observeForSync checks cancellation, failures, baseline identity and Finished after wakeup; timeout/interruption refuse success and finally cancels each subscription. Terminal callbacks only wake the wait, not manufacture a state. Final diagnostics/integrity must be empty. Controller's owner-guarded terminal signal records observationFailed; render gates the controller as ERROR_OPEN. Session/binding/cancellation and provider policy are rechecked before publishing; closed-session validation cannot authorize. |
| 11 | Controller safeToPublishAfterImport requires complete provider evidence, valid candidates and no deferred validation/contradictions; writable/readable grants, same-vault checks, detached authenticated local objects, fresh outbound preflight/readback/postflight remain separate gates. |

The proof uses this full controller/coordinator/store ownership composition, not
an isolated claim that onComplete can certify observation or that a callback pins
the latest state. Source inspection establishes compatibility with the contract;
it does not add new empirical race coverage.

## 16. F09 unchanged

The committed F09 correction guards subscriber callbacks at the controller mutation
boundary with the exact ForegroundVaultCoordinator owner identity. Its implementation,
tests and [historical report](ANDROID_STALE_SUBSCRIBER_OWNERSHIP_FIX_REPORT.md)
remain unchanged. Cancellation still cannot retract an entered callback.

## 17. F02 unchanged

Generic state advancement concealing/rejecting TOTP presentation remains a separate
product-policy question. No F02 disposition, presentation code or tests changed.

## 18. Dependency/runtime invariance

Read-only inspection of app/build.gradle.kts, app/gradle.lockfile,
gradle/verification-metadata.xml and package-deps.json confirms the unchanged
app → storage-nio 0.2.0 → core 0.2.0 → BC 1.86 graph pins. Existing
verifyMavenBoundary enforces the direct NIO edge, transitive core and BC relation.
The prior qualified runtime graph remains applicable; no fresh Gradle resolution
is claimed. No locks, metadata, package-deps or Maven artifacts were regenerated.

## 19. Changed files

- [AGENTS.md](../AGENTS.md): exact new guidance pin and compact F08 section-reading rule.
- [TOTIPO_JAVA_DEPENDENCY.md](../TOTIPO_JAVA_DEPENDENCY.md): reviewed-guidance section only; exact URL/hash and applicability.
- This follow-up report.

Historical architecture/F09 reports are byte-identical to starting HEAD. No README
change was needed. No app source/tests, tools, dependency/build/package/CI edits.

## 20. Docs-only qualification and counts

Static/provenance validation PASS: exact committed lineage and four-file milestone;
VERSION/spec applicability; comment-only Java production semantics; exact guidance
raw-byte/Git equality and recorded SHA-256; retained operation/threading sections;
Android assumption inspection; runtime/source pins and Maven hashes unchanged;
changed-path allowlist; local Markdown links/anchors; exact remote URLs and section
anchors; balanced fenced blocks; empty index; `git diff --check`.
Two read-only inspection commands initially used an omitted Git path separator
and a wrong root-level source path; corrected queries passed. No qualification
gate failed, no input invalidation or repeated full gate occurred.

| Execution category | Count |
| --- | --- |
| Baseline full Gradle | 0 |
| Focused Gradle test / compile / boundary / dependencies | 0 / 0 / 0 / 0 |
| Final normal full Gradle | 0 |
| Strict-clean Gradle | 0 |
| External artifact verifiers / embedded Gradle verifiers | 0 / 0 |
| Physical/device harness phases | 0 |
| Human / agent Nix | 0 / 0 |

These zeros follow the excluded-docs qualification rule, not weakened product gates.

## 21. Input freeze and Nix disposition

Reinspected package.nix: only app/src, gradle, tools and its exact build/lock/version/
license/branding file list enter filtered Gradle source; excluded components and
symlinks remain excluded. AGENTS.md, TOTIPO_JAVA_DEPENDENCY.md and review/** are not
selected. flake.nix's Android check/package share package.nix; evaluator/cache
inputs (package.nix, flake.nix, flake.lock, package-deps.json) are unchanged and do
not read these docs. Inspected Gradle/tool/CI wiring for alternate consumption.
A raw path flake snapshot may contain docs without changing effective check inputs.

- Production source stable: identical to starting HEAD.
- JVM tests stable: identical to starting HEAD.
- Build/package configuration stable: identical to starting HEAD.
- Qualification harness/tooling stable: identical to starting HEAD.

All included inputs remain frozen and unchanged. These excluded documentation edits
invalidate neither prior Gradle/artifact/device evidence nor Nix. **Human Nix not
required.** No Nix command run or requested.

## 22. Remote CI/release disposition

No staging, commit, tag, release, push or CI dispatch. No remote publication,
Java installation or dependency upgrade. Remote CI was not run for this milestone.

## 23. Final Git state

Branch remains `main`, HEAD remains
`ad5511811f2cabcb866b274aa0cda1e1c2bd2b91`; index empty. Exactly:

```text
 M AGENTS.md
 M TOTIPO_JAVA_DEPENDENCY.md
?? review/ANDROID_JAVA_GUIDANCE_PIN_REFRESH_REPORT.md
```

Everything remains unstaged/uncommitted for review.

[commit]: https://github.com/totipo-org/totipo-java/commit/f0a028676c1801a10b2d8d2650bf9c24357d1fa1
[guidance]: https://github.com/totipo-org/totipo-java/blob/f0a028676c1801a10b2d8d2650bf9c24357d1fa1/API_DESIGN.md
[raw-guidance]: https://raw.githubusercontent.com/totipo-org/totipo-java/f0a028676c1801a10b2d8d2650bf9c24357d1fa1/API_DESIGN.md
[composition]: https://github.com/totipo-org/totipo-java/blob/f0a028676c1801a10b2d8d2650bf9c24357d1fa1/API_DESIGN.md#observation-pass-ordering-and-composition
[java-report]: https://github.com/totipo-org/totipo-java/blob/f0a028676c1801a10b2d8d2650bf9c24357d1fa1/review/JAVA_OBSERVATION_COMPOSITION_CLARIFICATION_REPORT.md
[java-session]: https://github.com/totipo-org/totipo-java/blob/f0a028676c1801a10b2d8d2650bf9c24357d1fa1/core/src/main/java/org/totipo/VaultSession.java
[version]: https://github.com/totipo-org/totipo-java/blob/f0a028676c1801a10b2d8d2650bf9c24357d1fa1/VERSION
[spec]: https://github.com/totipo-org/totipo-java/blob/f0a028676c1801a10b2d8d2650bf9c24357d1fa1/SPEC_PIN.md
