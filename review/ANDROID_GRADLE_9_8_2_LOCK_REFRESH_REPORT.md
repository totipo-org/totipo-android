# Gradle 9.8.2 lock refresh

Classification: BUILD_DEPENDENCY. COMPLETE: local qualification PASS and final
human `nix flake check` PASS. Earlier pending statements below describe the
qualification sequence and are superseded by the final result.

Starting branch: `main`; HEAD: `2ae7eadc17cba49681dc46ff5897ee965221c7e4`.
Initial status contained only modified `flake.lock`. The user confirmed this
intentional lock refresh and that Gradle 9.8.2 is expected; preserve it.
No staged content or commits are authorized.

The updated nixpkgs supplies Gradle 9.8.2, while package.nix asserted 9.8.0.
Updated that exact assertion and the three active development-documentation
references. Historical qualification reports retain their original versions.
The user's lock refresh also updates other flake inputs; this change preserves
all of those updates without claiming independent editor or agent-tool smoke.

Inspected AGENTS.md, README.md, docs/DEVELOPMENT.md, flake.nix, package.nix,
CI, Gradle boundary tasks, manifests, APK verifier and the previous Nix/CI
unification report. The managed shell reports Gradle 9.8.2 and JDK 17.0.21.
VERSION/VERSION_CODE remain 0.2.3/8. No production, JVM test, Gradle build script,
Maven lock, verification metadata, package-deps or qualification-tool edits.

Selected validation: final normal Gradle checkpoint, strict offline clean
checkpoint, both external APK verifiers, offline release runtime graph and
Maven boundary, documentation consistency and diff whitespace checks, followed
by final human `nix flake check`. A separate product baseline is not applicable
to this dependency refresh, which arrived with an already-updated lock.
No affected application subsystem requires a device harness.

Local command logs are ignored under `.qualification/gradle-9.8.2/`.
The initial human Nix failure is the supplied 9.8.2/9.8.0 assertion mismatch;
it did not qualify the updated package. The agent runs no Nix.

Package source membership remains the exact files and app/src, gradle, tools
trees selected in package.nix. flake.lock and package.nix affect evaluation;
docs/ and review/ are excluded from effective package/check inputs. Report-only
updates after final qualification do not invalidate it. New report membership
will use intent-to-add only, with no staged-content diff.

Final local results (2026-11-15):

- `gradle check :app:assembleDebug :app:assembleRelease`: PASS, 4m55s;
  89 tasks, 85 executed and 4 from cache.
- `gradle --offline --no-daemon --no-configuration-cache --no-build-cache
  --rerun-tasks --dependency-verification=strict clean check
  :app:assembleDebug :app:assembleRelease`: PASS, 4m; all 91 tasks executed.
  XML totals: 462 tests in 43 suites, zero failures/errors/skips.
- Debug and unsigned release external APK verifiers: both PASS, using the
  strict-clean outputs and the policy flags specified in AGENTS.md.
  Packaged identity/SDK, manifest, resources, variant flags and exclusions pass;
  metadata matches reviewed VERSION/VERSION_CODE 0.2.3/8.
- `gradle --offline :app:dependencies --configuration releaseRuntimeClasspath
  :app:verifyMavenBoundary`: PASS. Runtime graph is NIO 0.2.0 -> core 0.2.0 ->
  BC 1.86, with strict locks. Main manifest retains only USE_BIOMETRIC;
  launcher/enrollment components and debug-only probes retain existing policy.
- Local development-documentation link targets, three active version references
  and the exact package assertion: PASS. `git diff --check`: PASS.

Existing experimental AAPT2-option, Java deprecated API and Gradle 10
compatibility warnings remain visible. No check failed or was bypassed.

Strict-clean APK SHA-256:

| Variant | SHA-256 |
| --- | --- |
| Debug | `6d21ea59f45afea427b0e3a2f63c17461fb99b09dd9d8e8a5bfa685b1eb93dc2` |
| Unsigned release | `11ebfdefb36e59e2171b2436239168801d3a7c76e698cd9fc7cce4846cfa266d` |

Input freeze for final human Nix:

- Production source stable: no changes under app/src; strict build and APK checks PASS.
- JVM tests stable: no test changes; 462 tests PASS in strict rebuild.
- Build/package configuration stable: intentional user flake.lock refresh retained;
  package assertion fixed before both full gates. Gradle build files, Maven locks,
  verification metadata and package-deps are unchanged.
- Qualification harness/tooling stable: no changes under tools; final APK checks PASS.

Frozen evaluator/cache SHA-256:

| Input | SHA-256 |
| --- | --- |
| flake.lock | `f82d10777907e98b373626166b0898b2ad0d94668c560339c9d53710b76052fa` |
| package.nix | `fa8dbf13003fc1b3f458e3ea05843b65bff61af31a707cbc75307a1dcaede2dd` |
| package-deps.json | `00f1389d7235a194c08b8674222b548a3d12c84c7146e1061a229114d37997dd` |

No included-input edits after either final Gradle gate; no invalidations or
repeated full gates. Human `nix flake check` is pending, so package evaluation
and sandbox realization are not yet claimed as passing. Remote CI and editor
smoke were not run.

Execution counts: baseline full Gradle 0 (dependency-refresh scope); final normal
full 1; strict-clean 1; focused compile/test 0; final runtime/boundary 1;
external APK verification 2 commands (one final set); embedded Gradle APK
verification 0; physical harness 0; agent Nix 0. Human Nix: one initial reported
failure, zero final runs reported; request one routine final check after freeze.
`gradle --version` was run once to establish the active managed toolchain.

Final Git state: main at the starting HEAD, modified flake.lock (user's existing
update), package.nix and docs/DEVELOPMENT.md; new review report intent-added only.
No staged-content diff; no commit. Ignored qualification logs and build state
remain outside Git source membership. Later excluded report-only evidence edits
will not invalidate the frozen package/check inputs.

## Maven master-index follow-up

The first final human Nix attempt failed fetching `master-index.xml`:
expected `sha256-FGidVTJOBhR6YirTc06mZEsz6r9DREFsYskItxfH4r4=`,
received `sha256-bSHnsKAmpz5WAp1Ja3cXJLGrqMM4UEqX4MwQeItX0ZE=`.
This is Google's mutable group index, documented in DEVELOPMENT.md; it is not
an application artifact upgrade. No Nix PASS has been reported.

Fetched https://maven.google.com/master-index.xml read-only with curl, following
its HTTPS redirect to https://dl.google.com/dl/android/maven2/master-index.xml.
The final response is HTTP 200, application/xml, 10,422 bytes. Parsed successfully
with ElementTree: metadata root, 340 empty group entries, including
com.android.application, com.android.tools.build and androidx.activity. SHA-256
exactly matches the human-reported received hash. Response and headers are
preserved in the ignored qualification directory. Updated only this XML entry
in package-deps.json; all other cache entries remain unchanged.

Classification remains BUILD_DEPENDENCY. This cache-only change invalidates Nix;
it is not consumed by local Gradle or APK verification and changes no artifact,
Gradle lock or verification metadata, so the final normal/strict Gradle and APK
passes above remain valid under the invalidation matrix. No repeat local builds
or device harnesses are required. Parsed JSON comparison against HEAD confirms
only the intended master-index hash changed; whitespace/index checks PASS.

Refreeze: production source stable (unchanged app/src); JVM tests stable
(unchanged, 462 strict-gate tests PASS); build/package configuration stable
(version assertion and user lock unchanged, cache entry verified and now frozen);
qualification harness/tooling stable (unchanged tools and passing final APK
verification). The previous package-deps hash is superseded by
`08a3ed93bd630c8ba4b30fadce35c83f8841cea2344dc5fdfb8de69063b779b6`.
All other frozen input hashes remain unchanged. Request another `nix flake check`
because the verified cache correction invalidated the failed prior attempt.

Cumulative counts: human Nix 2 reported failures (initial version assertion,
then index hash); final successful human check pending. All other execution
counts above unchanged; one read-only XML fetch and validation added. Final Git
state additionally includes modified package-deps.json; main/HEAD unchanged,
report intent-added only and no staged content. No agent Nix or commit.

## Final human result

The user reported `nix flake check passed` on 2026-11-15 after the verified
master-index correction. This is human-reported evidence; no command output was
provided. The package-inclusive final gate is PASS. Frozen flake.lock,
package.nix and package-deps.json hashes were rechecked and exactly match the
final freeze above. No included inputs changed after that freeze.

Cumulative human Nix invocations: 3 reported, comprising the initial version
assertion failure, the mutable-index hash failure and the final PASS. Both
repeats were necessary after correcting the respective failing inputs. All
other counts remain unchanged. Only this excluded report was edited after the
passing result, so Nix and the final local evidence remain valid. Git remains
on the starting main/HEAD with the five paths listed above modified/intent-added;
no staged content or commit. Final whitespace and staged-content checks PASS.
