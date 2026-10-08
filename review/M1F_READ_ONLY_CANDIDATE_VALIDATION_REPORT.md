# M1F — Java 0.1.5 and read-only immutable candidate validation

## 1. Starting state

Started on `main` at `b084fd09d6c009c5397980a9ead0abe1f490c4d1`, committed M1E.
The agent ran `git branch --show-current`, `git rev-parse HEAD`, and
`git status --short` before editing. Starting tree was clean. No AGENTS.md found.

Inspected app Gradle configuration/Maven boundary, all locks, verification
metadata, reviewed dependency hashes, package cache/derivation, flake, refresh
script, dependency provenance, LocalReplicaOwner, all four M1E production classes,
M1D/M1E reports, debug provider/NIO probes, APK verification and CI. No Nix command,
staging, commit, tag, release, push or remote CI trigger was performed.

## 2. Java 0.1.5 provenance

Before repinning, all six intended Central POM/module/JAR URLs returned HTTP 200.
No local JAR/source checkout, Maven Local, substitution, snapshot or composite
build was used. Downloads under ignored `.gradle/m1f-inspection` are inspection
only; Gradle resolves production modules from Maven Central.

Published [v0.1.5 release](https://github.com/totipo-org/totipo-java/releases/tag/v0.1.5)
is neither draft nor prerelease, published `2026-10-07T23:45:10Z`.
Annotated tag object `8c0c1d45e934df08c0910cf4b2b439d70d918e5a` points to release
commit `67c1326a2ce433921a947ba4ffd6843403f4a7a3`.
[Release SPEC_PIN.md](https://github.com/totipo-org/totipo-java/blob/67c1326a2ce433921a947ba4ffd6843403f4a7a3/SPEC_PIN.md)
confirms protocol v1/r18, unchanged spec commit
`4623a7e1718e23504903096c92332597057bd8f0` and requirements profile SHA-256
`4c7954cd2b59aa0afbbe3c22080cadddf72135d884b186a671266c29d58418df`.
This remains a committed spec revision, not an r18 release tag.

Actual Maven Central SHA-256:

| Artifact | SHA-256 |
| --- | --- |
| core 0.1.5 JAR | `e99609e59db1d9f52f80c446060e63ce7dde17ad69252fa87d397d0cb1577f81` |
| core 0.1.5 module | `8a3bd815956d59f470f2639cfd95697496abac8c24b6b848579855c05f40bbe5` |
| core 0.1.5 POM | `886ae205c99b23aef40332102b7c31393e88a8b3a513f21f5f18a6a93307e3a2` |
| NIO 0.1.5 JAR | `9e559ec75fb09af068f876751f32d696c50c40988a16d28419dfa05d1d1c4dae` |
| NIO 0.1.5 module | `4339f56533be8af2fa42b283b7a8513b51bae9de2d7f9aca3294dfec7b03dd88` |
| NIO 0.1.5 POM | `1065a65031308327d660e2290213b5489ceeb13a5f11507b13b45ed4d926bb3d` |

JAR hashes match the published module metadata. Both modules target Java 17;
all 180 core and 27 NIO production classes have class major version 61.
All 93 core and 15 NIO published source files match the tagged release commit
exactly. Core sources SHA-256:
`d797c0854db469e8288b787dcbeb3dd3eb8e83062bea5c42b0013082b2cccdc4`.
NIO sources SHA-256:
`bee2a5c9f941360b9c499f1518e3b2201e1e94405a34a8d2709033419e2ed67f`.
NIO JAR/sources bytes are unchanged from 0.1.4.

POMs and module metadata both establish the intended graph:

```text
app
  -> org.totipo:totipo-storage-nio:0.1.5
      -> org.totipo:totipo-core:0.1.5
          -> org.bouncycastle:bcprov-jdk18on:1.86 (runtime)
```

Historical Android 0.1.4 private-mode/provider qualification remains in the prior
reports and dependency document. Java 0.1.5 supplies no SAF/provider qualification.

## 3. Dependency repin

Single direct production NIO edge and transitive core advance 0.1.4 → 0.1.5.
No direct core declaration. Maven checks require exact requested AND selected
0.1.5 versions, exactly the two expected modules, the direct NIO and NIO→core
edges, external Maven artifacts, and core→BC runtime. Old/mixed requests are
rejected even if Gradle could select 0.1.5; project/file substitution and unexpected
Totipo modules remain rejected across four production configurations.

Ran repository's explicit `bash bootstrap-m0.sh --refresh-dependencies` path.
All three Gradle phases passed. Reviewed generated changes; removed obsolete
0.1.4 verification components. App lock diff is exactly the two Totipo version
changes, with identical configuration scopes. Unrelated verification components
are byte-for-byte unchanged. All four new verified JAR/module hashes and six
reviewed JSON hashes match actual Central downloads. BC/AGP/JUnit/other pins,
buildscript lock, wrapper, build/settings/toolchain configuration and flake.lock
are unchanged. Debug NIO probe's version label advances; its behavior is unchanged.

`package.nix` readiness checks now require core/NIO 0.1.5. The existing flake
exposes `packages.default.mitmCache.updateScript` as `update-package-deps`, so
human cache regeneration command is `nix run path:.#update-package-deps`.
The regenerated package-deps.json was inspected: core/NIO 0.1.5 JAR/module/POM
entries exactly match all six actual Central SHA-256 values above. No 0.1.4 Totipo
artifact entry remains. BC 1.86 cache JAR/POM hashes are unchanged and independently
match fresh Central downloads. All unrelated cache entries are identical to HEAD.
Totipo publisher metadata advances NIO release to 0.1.5 (timestamp
`20261007234340`) and drops unused core metadata. No unrelated hash drift.
Human reported `nix run path:.#update-package-deps` completed successfully.

## 4. Existing M1E snapshot boundary

ProviderTreeReader, ProviderSnapshot, ProviderTraversal and BoundedProviderRead
are unchanged. Query/openInputStream reads, traversal/name predicate, hard bounds,
EOF/overflow observation, loading/error/interrupt handling, opaque provider IDs,
defensive bytes/lists, grants and caller scheduling remain the M1E contract.

Classifier consumes existing `Scan` / `Bytes` evidence. It reuses the exact M1E
64-lowercase-hex predicate and constructs Java's public `RevisionId`; no manual
OBJECT_ID decoder, parser, crypto or copied Java implementation is introduced.
No new framework behavior, provider action or UI is added.

## 5. Candidate-classification model

`ImmutableCandidateClassifier` is a small production layer in the provider package.
`classify(Bytes, session)` obtains display name from the immutable document evidence.
`classify(Scan, session)` validates and groups all captured candidate observations.

| Outcome | Meaning | Java validation |
| --- | --- | --- |
| NONCANDIDATE | Bad/case-changed/suffixed name, vault or directory | Never |
| TRANSPORT_UNAVAILABLE | MISSING/UNAVAILABLE, retaining local issue/prefix | Never |
| TRANSPORT_SHORT | EOF before 1024 (including zero) | Never |
| TRANSPORT_OVERSIZED | 1025-byte overflow probe | Never |
| VALIDATION_UNAVAILABLE | No session, closing/closed session, unsupported implementation | No completed validation |
| INVALID | Exact 1024 bytes; released Java returns Invalid | Yes |
| VALID | Exact 1024 bytes; released Java returns defensive Valid | Yes |

Wrong read bound/inconsistent synthetic evidence is an integration error. Java ID
construction errors after the transport predicate and unexpected validation errors
propagate; they do not become Invalid. Returned Valid ID/bytes must match the
supplied candidate, otherwise the classifier raises an integration error.

Candidate retains transport provenance separately from outcome, unavailability
reason, and optional Java Valid. Valid retains only opaque defensive ciphertext/ID;
no parsed TOKEN secrets/metadata, root or password. Results/lists are immutable,
operation-scoped in-memory observations. Public result constructors are descriptive;
authentication originates only in a successful session call.

## 6. Session integration

Caller supplies its already open authenticated session and retains the existing
root ownership for the unlocked operation. The classifier stores no session,
validator closure, credential/root material or local path. It acquires no owner,
opens no store, and calls only `VaultSession.validateObject` for cryptographic work.
The released Java implementation enters its normal gate, authenticates a snapshot,
and performs no store I/O/state update. It cleans its temporary decoded secret.

Null session → NO_SESSION. SessionClosedException → SESSION_CLOSED. Default
external unsupported capability → UNSUPPORTED. These are validation-unavailable,
never Invalid. A close racing a validation call is resolved by Java's gate. A
completed Valid fact remains descriptive evidence if subsequent siblings encounter
closure; no new validation succeeds after closing starts.

API is synchronous: provider I/O and this classification/crypto must be called on
a worker thread. No coroutine dependency, WorkManager, background sync, scheduler
or second NIO store is added. No root/password export is needed.

## 7. Duplicate-group reconciliation

Read-only analysis only; no canonical import or publication. Scan classification
uses exactly one supplied session for every sibling, groups canonical RevisionId
values, and retains the entire source scan and individual provider identities.
The comparison helper is package-private; production reaches it only after this
single-session classification. It is not an API for mixing facts from other roots.

One Valid → ONE_REPRESENTATION. Equal Valid values collapse to one representation,
while every sibling remains diagnostic evidence. Invalid, partial, oversized or
unavailable siblings do not erase Valid. No Valid → NONE_OBSERVED with no selected
representation; this states no authenticated fact observed, never protocol absence.

## 8. Integrity contradiction handling

Two unequal Valid values for the same RevisionId → INTEGRITY_CONTRADICTION.
No representation is selected, including if later siblings equal an earlier one.
Both/all source observations remain available for review. Nothing is imported.
Comparison uses released Valid.equals (ID and exact ciphertext), under the same
session/root. No arbitrary winner, graph update or durable journal is produced.

The contradiction test uses publicly constructed descriptive Valid values to
exercise application comparison. It is explicitly symbolic, not a cryptographic
fixture or claim that honest deterministic same-ID unequal representations can be
constructed. Actual valid/invalid cases use real released Java authentication.

## 9. Observation completeness

Every Group retains the exact M1E scan State, including INCOMPLETE_LOADING rather
than collapsing it. Result retains the whole Scan and its local listing/read issues.
Valid from incomplete/unavailable coverage is an authenticated observed fact, with
incompleteness intact. It proves neither uniqueness, absence of contradictions
elsewhere, exhaustive history nor freshness. Empty/no-Valid groups and scans remain
observations, never authoritative absence. Even COMPLETE enumeration is not an
atomic/authenticated provider snapshot or a freshness guarantee.

## 10. VAULT exclusion

Exact `vault` is NONCANDIDATE and never reaches validateObject. Existing generic
M1E VAULT byte reads remain transport observations only. No password wrapper/root
comparison, adoption, replacement, VAULT reconciliation or product UI is added.

## 11. Tests

20 new classifier JVM tests; real released Java sessions with disposable JVM-only
private NIO fixtures author valid TOKEN objects. Fake M1E observations and one pure
traversal Source provide deterministic transport evidence. Fixture creation/save
occurs only during test setup, outside classification, under one store/session per
root; it does not touch Android's canonical local replica or a real provider.

Scenarios and assertions:

1. SHORT 0 bytes: transport short; no Java call.
2. SHORT 256 bytes: transport short; no Java call.
3. OVERSIZED 1025 bytes: transport oversized; no Java call.
4. Exact malformed 1024 bytes: real Java Invalid.
5. Core-authored object: real Java Valid; bytes/ID exact and defensively copied.
6. Object validated under a different authenticated root: real Java Invalid.
7. Two exact valid copies: one representation; both provider identities retained.
8. Valid + partial: authenticated fact preserved.
9. Valid + Invalid: authenticated fact preserved.
10. Symbolic unequal Valid values: contradiction, no selection, either order, later duplicate.
11. Incomplete/loading/unavailable coverage + Valid: coverage retained.
12. No Valid / empty scan + incomplete coverage: NONE_OBSERVED/empty, no absence claim.
13. No session, real closed session and close at call entry: validation unavailable.
14. State neutrality: exact local directory/file bytes, same VaultState graph/tokens/heads,
    same provider rows/bytes; storage spy rejects ANY SPI I/O during classification.
15. Missing/unavailable/interrupted evidence: transport unavailable, no Java call.
16. Valid + missing: fact and incomplete coverage retained.
17. Unsupported external session and unexpected integration error behavior.
18. Malformed names/vault/directories bypass Java; bad bounds fail as integration bugs.
19. Production M1E traversal + bounded reads across duplicate directories collapses copies.
20. Real session closes between siblings: completed Valid preserved, next unavailable.

Every real crypto test uses a guarded real NIO store after initial observation;
validation touching read/scan/write/prepare/close SPI would fail. Existing M1E source
safety guard also covers the new production class, rejecting provider mutation,
local-owner/storage and Java-internal parser references. Classifier has no resolver
or grant APIs. No physical provider qualification or crypto mock is needed.

## 12. Validation

### Agent

- Explicit dependency refresh: PASS.
- `./gradlew check :app:assembleDebug :app:assembleRelease`: PASS; 90 actionable
  tasks (8 executed, 82 up-to-date), with final classifier tests.
- `./gradlew --offline --no-daemon --no-configuration-cache --no-build-cache
  --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug
  :app:assembleRelease`: PASS; 92/92 actionable tasks executed.
- JVM tests: 39 total (20 classifier, 10 M1E, 6 debug logic, 2 owner, 1 core smoke),
  zero failures/errors/skips in normal and forced offline validation.
- `python3 tools/verify-wrapper.py`: PASS.
- `python3 tools/verify-apk.py app/build/outputs/apk/debug/app-debug.apk`: PASS.
- `python3 tools/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk
  --unsigned --no-debug-probe`: PASS.
- Debug APK SHA-256: `0287cacf87733b2f6a842715a6060b4a4ed406d81428a9f18f6443db7f94c327`.
- Release APK SHA-256: `3dd67c0d03bf2f4e267d3b7f4bce0391074e5f420980f18b0553977f0623d8bd`.
- `git diff --check`: PASS; new source/test/report also checked for trailing
  whitespace/conflict markers. `git diff --cached --stat`: empty.
- Dependency review: expected Totipo-only locks/hashes; unrelated dependencies and
  toolchain unchanged. Java 17 bytecode and release/source provenance verified.

APK verifier now also requires Java Valid/Invalid, production M1E reader and
classifier classes. It excludes classifier/M1E tests in addition to existing test
and debug boundaries. Release excludes all debug machinery. CI already invokes
this verifier and `check`; workflow changes and remote triggers are unnecessary.
Existing AAPT2/deprecation notices are unchanged; no new framework/device behavior.

### Human Nix

Human reported cache regeneration PASS; agent cache inspection PASS (section 3).
Human also reported both final checks completed successfully:

- `nix flake check path:.`: PASS.
- `nix build path:.`: PASS.

These are human-operated results. Agent executed no Nix commands. No duplicate
manual Gradle rerun through Nix was requested.

### Human device

Not required/performed. M1F introduces no framework behavior beyond M1B/M1E.
Historical physical provider/private-mode evidence remains scoped to those reports.

### Remote CI

Not run for these uncommitted changes. No push or CI trigger performed.

## 13. Qualified scope

This milestone validates read-only immutable candidate classification only.
It establishes no SAF direct-store support, canonical import, synchronization,
provider mutation, VAULT semantics, durable journal, UI, exhaustive/fresh graph,
power-loss guarantee or broader Android filesystem/provider qualification.
M1F is complete with agent validation and the human Nix/cache checkpoint recorded.
Stop here for review; canonical import remains a separate milestone.

## 14. Recommended next milestone

After review: immutable canonical import under LocalReplicaOwner, using the same
root ownership as the active session and preserving authenticated contradictions
and transport incompleteness. No implementation of that milestone is included.

## 15. Final Git state

Agent repeated branch/HEAD/status/diff checks after recording the human Nix PASS
results. Branch
`main`; HEAD `b084fd09d6c009c5397980a9ead0abe1f490c4d1` unchanged.

`git status --short`:

```text
 M README.md
 M TOTIPO_JAVA_DEPENDENCY.md
 M app/build.gradle.kts
 M app/gradle.lockfile
 M app/src/debug/java/org/totipo/android/debug/LocalNioQualification.java
 M gradle/verification-metadata.xml
 M package-deps.json
 M package.nix
 M review/M0_REVIEWED_DEPENDENCY_HASHES.json
 M tools/verify-apk.py
?? app/src/main/java/org/totipo/android/provider/ImmutableCandidateClassifier.java
?? app/src/test/java/org/totipo/android/provider/ImmutableCandidateClassifierTest.java
?? review/M1F_READ_ONLY_CANDIDATE_VALIDATION_REPORT.md
```

`git diff --stat`: 10 tracked files, 116 insertions, 70 deletions; untracked
classifier/test/report are additional files, intentionally not counted by Git.
`git diff --check`: PASS. `git diff --cached --stat`: empty.
All changes remain unstaged/uncommitted; nothing staged, committed, tagged,
released or pushed. M1F is complete; stop for review before canonical import.
