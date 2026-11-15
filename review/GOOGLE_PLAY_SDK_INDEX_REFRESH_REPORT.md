# Google Play SDK Index refresh — 2026-11-15

Classification: BUILD_DEPENDENCY (Nix response-cache metadata only).
Starting branch: clean `main`; starting HEAD:
`a67d09c5e308a46da3562e7de2a2df9181a77ee4`.
Recorded branch, HEAD and empty short status before editing.

CI reported a fixed-output mismatch for snapshot.gz:
expected `sha256-0BKlWxCAFyAcjX47XWXE7UB/Rahhl4SPxpNQi8nMpb8=`,
received `sha256-3IRF1v2J+2WOdNM98rOA0BWnXO+ZCINzeJnuK0PbQdI=`.
Two independent HTTPS downloads of
https://dl.google.com/play-sdk/index/snapshot.gz matched the received hash.
The first returned HTTP 200, application/gzip, 111,141 compressed bytes,
Last-Modified Fri, 13 Nov 2026 18:09:01 GMT. Gzip integrity passes;
633,480 decompressed bytes contain SDK coordinates and Google Play SDK detail
URLs. This is the mutable lint metadata response documented in DEVELOPMENT.md.
The adjacent totipo-core download line does not identify the failed derivation.

Updated only https://dl.google.com -> play-sdk/index/snapshot -> gz in
package-deps.json. Parsed comparison against HEAD proves that this is the only
semantic cache change; every library artifact entry is unchanged.
Final package-deps.json SHA-256:
`e9e8117f97bafa7db8da34a56b2c877aff528cc9f7e4a25d3078484f0798397d`.

Inspected AGENTS.md, README, development documentation, package.nix, flake.nix,
CI, cache consumers and previous index/Gradle qualification reports. The pinned
Gradle 9.8.2/JDK 17/Android SDK environment is present. package-deps.json is
consumed by Nix fetchDeps, outside the filtered Gradle source. No tools/test
consumer references package-deps or a reviewed-inputs inventory in this checkout.
No production, test, Gradle, lock, verification metadata or tool input changed.
Under the package-only invalidation rule, no local Gradle/APK/device rerun is
required. The earlier local Gradle/artifact evidence is not invalidated; Nix
must qualify this changed cache input. The report is excluded from effective
package/check inputs, so later report-only evidence edits do not invalidate Nix.

Validation: repeated download/hash, gzip integrity, expected metadata content,
JSON parse and exact semantic diff PASS. git diff --check and staged-content
checks PASS before the human gate.

Input freeze:

- Production source stable: unchanged from starting HEAD.
- JVM tests stable: unchanged from starting HEAD.
- Build/package configuration stable: only the independently verified cache
  leaf changed; final cache digest recorded above. No further input edits planned.
- Qualification harness/tooling stable: unchanged from starting HEAD.

Human final `nix flake check`: PASS, reported by the user on 2026-11-15.
No command output was supplied; this is human-reported package-inclusive gate
evidence. The frozen cache digest was rechecked and matches exactly; all other
included inputs remain unchanged. Only this excluded report was edited after
the result, so the Nix pass remains valid. No agent Nix invocation or remote
CI rerun. Qualification is complete.

Execution counts: baseline full Gradle 0; focused Gradle 0; final normal full 0;
strict-clean 0; external APK verifier 0; embedded Gradle APK verifier 0;
physical/device harness 0; human local Nix 1 reported PASS this milestone; independent HTTPS
snapshot downloads 2. Supplied remote CI evidence: 1 failed run. No repeated
full gates or invalidated passes during this milestone.

Final Git state: main at starting HEAD; package-deps.json modified and this
report intent-added only. Status, diff names and cached names recorded before
the human gate; no staged-content diff. No commit, push or CI dispatch.
