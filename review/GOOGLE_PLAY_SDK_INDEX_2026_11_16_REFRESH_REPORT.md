# Google Play SDK Index refresh — 2026-11-16

Classification: BUILD_DEPENDENCY (Nix response-cache metadata only).
Starting branch: clean `main`; starting HEAD:
`82e91c7ac298743deb131b5e724d47dfd67832f4`.
Recorded branch, HEAD and empty `git status --short` before editing.

## Failure and correction

User-supplied CI failure: fixed-output derivation
`/nix/store/w12sxb15zg0fwgj9dw0vds37bx2575hf-snapshot.gz.drv` expected
`sha256-3IRF1v2J+2WOdNM98rOA0BWnXO+ZCINzeJnuK0PbQdI=` and received
`sha256-Qse+oZofJsXmNTRotYFUVZciaMCH4VVA298wlGcYBTM=`.

Two independent HTTPS downloads of
https://dl.google.com/play-sdk/index/snapshot.gz matched the received hash
and each other byte for byte. Both returned HTTP 200, `application/gzip`,
Last-Modified `Mon, 16 Nov 2026 17:24:27 GMT`, and 111,444 compressed bytes.
Gzip integrity passes; 634,444 decompressed bytes contain 289 Google Play SDK
detail URLs. This is the mutable lint metadata response documented in
DEVELOPMENT.md and the previous Google Play SDK Index refresh report.

Updated only `https://dl.google.com` → `play-sdk/index/snapshot` → `gz`
in package-deps.json. JSON parsing and comparison against HEAD prove that this
is the only semantic cache change. All library artifact entries remain identical.
Final package-deps.json SHA-256:
`b678ef8ff9e5d655e96d2f31372498e27d7732421860bd7d1bebe0c38efe21b2`.
Google can replace this unversioned response again; this refresh does not make
the upstream URL immutable.

## Validation scope and freeze

Inspected AGENTS.md, README, package.nix, flake.nix, flake.lock, CI, development
documentation, cache consumers and the prior index refresh evidence.
package-deps.json is consumed by Nix `gradle.fetchDeps`, outside the filtered
Gradle source. The separate release-tooling and release-app checks do not
consume it. No production, JVM test, Gradle, dependency lock, verification
metadata or qualification tool input changed. Under the package-only
invalidation rule, local Gradle, APK and device reruns are unnecessary;
the final human package-inclusive Nix gate is required for the changed cache.
This report is excluded from effective package/check inputs; later report-only
evidence edits do not invalidate Nix.

Selected checks: repeated HTTPS download/hash, gzip integrity, expected metadata
content, JSON parse, exact semantic diff, `git diff --check` and staged-content
checks. All passed before the human gate.

Input freeze:

- Production source stable: unchanged from starting HEAD.
- JVM tests stable: unchanged from starting HEAD.
- Build/package configuration stable: only the independently verified cache
  leaf changed; final cache digest recorded above. No further input edits planned.
- Qualification harness/tooling stable: unchanged from starting HEAD.

Human final `nix flake check`: pending. Agent did not run Nix or dispatch CI.
No passing gate was invalidated during this milestone.

Execution counts: baseline full Gradle 0; focused Gradle 0; final normal full 0;
strict-clean 0; external APK verifier 0; embedded Gradle APK verifier 0;
physical/device harness 0; human local Nix 0 so far; HTTPS snapshot downloads 2.
Supplied remote CI evidence: 1 failure excerpt, no run URL or complete log supplied.
No repeated full gates.

Changed files: package-deps.json and this report.
Final Git state before the human gate: main at starting HEAD;
package-deps.json modified, this report intent-added only. Status, diff names
and cached names recorded; no staged-content diff. No commit or push.
