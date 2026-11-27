# Google Play SDK Index refresh — 2026-11-26

Classification: BUILD_DEPENDENCY (Nix response-cache metadata only).
Starting branch: clean main; starting HEAD:
`946958bc8f64442b91d5b9948cc932db71afda2c`.
Branch, HEAD and empty `git status --short` recorded before edits.

The user reported fixed-output derivation
`/nix/store/mzir61f2sxsa1xb16j3354gb459v6502-snapshot.gz.drv` failing:

- Specified: `sha256-Qse+oZofJsXmNTRotYFUVZciaMCH4VVA298wlGcYBTM=`.
- Received: `sha256-8kN8VpZFeRy8AGCGUedqHc3XDeCny1eoqASAINfErr4=`.

Two independent HTTPS downloads of
`https://dl.google.com/play-sdk/index/snapshot.gz` matched the received hash.
Both returned HTTP 200, application/gzip, 112,181 compressed bytes,
Last-Modified Thu, 26 Nov 2026 17:24:46 GMT. Both passed gzip decompression,
producing 636,317 bytes containing SDK coordinates and Google Play SDK detail
URLs. This is the mutable lint metadata response described in DEVELOPMENT.md.

Changed only `https://dl.google.com` → `play-sdk/index/snapshot` → `gz` in
package-deps.json. Parsed JSON equality against HEAD after replacing that leaf
proves all other cache entries remain unchanged. Final package-deps.json SHA-256:
`16b118f6af816554cb301433fe2b3a28a9afeaca3a3b151ae6ff4da3b1eafee4`.

Inspected AGENTS.md, README, development documentation, package.nix, flake.nix,
CI and previous metadata-refresh qualification evidence. Searches found no
package-deps references in app/src/test or tools. package-deps.json feeds Nix
fetchDeps and is outside the filtered Gradle source. No production, JVM test,
Gradle input, library version, lock, verification metadata or tooling changed.
Under the actual-dependency invalidation matrix, this package-only cache edit
requires human Nix qualification, with no Gradle/APK/device rerun. The report is
excluded from effective package/check inputs; report-only result updates do not
invalidate Nix.

Validation: repeated HTTPS download/hash, gzip integrity, metadata content,
JSON parse and exact semantic comparison PASS. `git diff --check` and
`git diff --cached --quiet` PASS before requesting the human gate.

Input freeze:

- Production source stable: unchanged from starting HEAD.
- JVM tests stable: unchanged from starting HEAD.
- Build/package configuration stable: only the verified cache leaf changed;
  final digest recorded above. No further included-input edits planned.
- Qualification harness/tooling stable: unchanged from starting HEAD.

Human final Nix check: user reported PASS on the diagnostic retry requested as
`nix flake check --keep-failed --print-build-logs`. The preceding check failed
with a disappeared Gradle daemon and a reported `hs_err_pid585.log`. That crash
file was unavailable; the saved `.gradle/nix-crash.log` stopped during build
tasks without the fatal-error details. The JVM crash cause remains undetermined.
The extra full invocation was requested to retain crash evidence; it passed
without an intervening agent source/configuration change. No speculative JVM or
build workaround was applied. Agent Nix invocations: zero, per policy.
No successful milestone gate has been invalidated.

Execution counts: baseline full Gradle 0; focused Gradle 0; final normal full 0;
strict-clean 0; external APK verifier 0; embedded Gradle APK verifier 0;
physical/device harness 0; human Nix 2 reported after the cache repair
(one daemon-crash failure, one successful diagnostic retry);
independent HTTPS snapshot downloads 2. One supplied failed Nix attempt prompted
the original cache repair; its originating command/environment was not supplied.
Nix-internal Gradle/verifier execution counts were not independently inspected.

Final Git state before human gate: main at starting HEAD; package-deps.json
modified and this report intent-added only. Recorded short status, unstaged diff
names and empty staged-content diff names. No staged content, commit or push.

Result-recording boundary: the user subsequently committed the cache refresh and
report as `24a2f652ab0b68bca1bc21ac6b10c66c2a1362d0` (`update package deps`).
Before this result update, main was clean at that commit and package-deps.json
still matched the frozen digest above. Final result update changes only this
excluded report; source filtering and check inputs remain unchanged, so the
reported Nix PASS is not invalidated. `git diff --check` and
`git diff --cached --quiet` PASS. Final Git state: main at that commit, this
report modified, no staged contents. The agent did not commit or push.
