# Android qualification ladder milestone

Status: COMPLETE. Documentation/process only; Android product behavior, build inputs,
tools and CI are unchanged. All changes remain unstaged/uncommitted.

## Starting state and classification

Before editing, recorded:

```text
$ git branch --show-current
main
$ git rev-parse HEAD
45a7006345fb755fae11181e3ace74b1d7b0f390
$ git status --short
(empty)
```

Classification: **DOCS_ONLY**, established before editing. This policy describes
qualification tooling but does not change that tooling. No production, JVM test,
Gradle, package, dependency or device harness inputs change.

Inspected README.md, package.nix, flake.nix, .github/workflows/ci.yml, tools/ inventory,
wrapper/APK/toolchain verifier implementations, security harness implementation,
app/build.gradle.kts and test input references, and the
[biometric report](ANDROID_BIOMETRIC_INACTIVITY_LOCK_REPORT.md). No existing AGENTS.md
was present in the repository or ancestor directories. Historical reports are untouched.

## Current qualification paths

- Local wrapper `check` includes JVM tests, lint, debug/release Maven boundaries and
  release APK boundary verification; both APK assemblies are retained at full gates.
- The strict offline clean gate proves fresh task execution with strict dependencies.
- External wrapper/debug/release APK verifiers check pins, ZIP/manifest/branding/DEX,
  diagnostic and unsigned release boundaries. Runtime graph inspection and
  `:app:verifyMavenBoundary` remain part of final evidence.
- Standalone tools cover row/reveal, token lifecycle, biometric security, SAF
  bootstrap/inbound/outbound, otpauth ingress and M3D Syncthing. Select affected
  subsystems; do not automatically repeat every physical suite.
- `checks.android` and `packages.default` share the same Android package derivation.
  CI runs `nix flake check --print-build-logs path:.` followed by an unchanged-input
  check. CI behavior is unchanged; the interactive human command remains exactly
  `nix flake check path:.`.

## Final ladder and classifications

The durable [Qualification ladder](../AGENTS.md#qualification-ladder) requires
classification before editing: DOCS_ONLY, TEST_OR_QUALIFICATION_TOOLING, PRODUCT_CODE,
UI_ONLY, LIFECYCLE_SECURITY, BUILD_DEPENDENCY and/or PACKAGING_RELEASE. Classification
selects focused checks without waiving final gates for changed production/build inputs.

For a clean product milestone the sequence is:

1. One ordinary full baseline, without clean/rerun/cache suppression.
2. Focused compilation, tests and affected device diagnostics during implementation.
3. One final normal full checkpoint after production/tests stabilize.
4. One strict offline clean full checkpoint after normal PASS.
5. One external final wrapper/debug/release verification set against strict outputs,
   plus runtime graph and manifest/Maven boundaries.
6. Relevant final physical/device qualification, with harness fixes completed.
7. Freeze every effective Nix source/evaluator/cache input; request one final human
   `nix flake check path:.`.

Full failures retain evidence, narrow to deterministic reproduction and focused fixes,
and return to full qualification only after the focused failure is stable. Every extra
full invocation needs a reason. No inner-loop clean, rerun-tasks, broad APK verification,
human Nix or repeated installation except for physical-only diagnosis.

## Invalidation matrix and Nix source freeze

The actual `package.nix` filter selects `app/src`, `gradle`, `tools` and an explicit
file allowlist (build scripts/properties/locks, VERSION, LICENSE, branding provenance).
It excludes symlinks and `.git`, `.gradle`, `build`, `.direnv` path components.
`flake.nix`, `flake.lock`, `package.nix` and `package-deps.json` are additionally
effective evaluator/cache inputs. See AGENTS.md for the exact allowlist.

| Later change | Gradle/artifact/device response | Human Nix invalidated? |
| --- | --- | --- |
| Production source/resources/manifest | Renew normal + strict gates, final verifiers and affected device checks | Yes |
| JVM tests/Gradle inputs | Renew normal + strict gates and final artifact checks; device checks if affected | Yes |
| Build/package/dependency config | Renew affected final gates according to actual consumers | Yes |
| Standalone device harness in tools/ | Rerun affected harness; no Gradle solely for harness edits outside its sources | Yes |
| APK verifier in tools/ | Rerun against final APKs; rebuild only if build/APK inputs require it | Yes |
| AGENTS.md, README.md, review report | Documentation validation and diff check | No, under current rules |

All included paths, including harness code/prose, must freeze before human Nix.
Record production source stable, JVM tests stable, build/package configuration stable,
and qualification harness/tooling stable. Later included-input edits invalidate Nix
even if APK bytes remain unchanged. Reinspect filtering/dependencies rather than guessing.

For this milestone all four groups remain unchanged from starting HEAD and stable.
AGENTS.md, README.md and review/ are excluded from the filtered Android source and
not otherwise consumed by the package/check or inspected Gradle/test inputs. Although
the raw `path:.` snapshot may contain these docs, their edits do not alter effective
qualification inputs. This report-only edit does **not** invalidate Nix. No new human
Nix result is required or claimed; the agent ran no Nix command.

## Historical biometric evidence and expected savings

The unchanged biometric report, sections 23–25, records:

| Execution category | Historical evidence |
| --- | --- |
| Baseline full Gradle | 1 PASS, 1m38s |
| Final normal full attempts | 4: FAIL 3m5s; PASS then invalidated 2m36s; FAIL 2m1s; PASS 2m2s |
| Focused Gradle | 12 test invocations + 1 standalone compile; additionally 1 release classpath-JAR packaging and 1 offline runtime/graph invocation |
| Strict-clean | 1 PASS, 2m16s; 334 tests, 92/92 tasks executed |
| External artifact verification | 1 final set: wrapper and both APK verifiers PASS |
| Physical harness | 10 phases: lifecycle 6, security first 3, restart 1 |
| Human Nix | 4 reported results: 1 failure, 3 passes; last PASS after all harness fixes |

The ordinary full runs totaled 11m22s. Baseline plus final successful ordinary gate
totaled 3m40s; the three intervening attempts consumed 7m42s. Moving source guards,
AAD authorization-order diagnosis and the wall-clock race to focused checks offers
that amount of gross full-gate time reduction on a comparable run. This is an
opportunity, not a guaranteed net saving: focused diagnostics still cost time and
real subsequent source changes still require renewed final gates.

Freezing tools before Nix targets one final human check instead of four reported
results. No Nix durations were recorded, so no elapsed-time saving is claimed there.
Physical repeats were driven by real harness failures; early deterministic harness
development can reduce them without skipping relevant final physical evidence.

Final evidence remains at least as strong: full tests/lint/both assemblies, strict
fresh offline proof, dependency/runtime and manifest boundaries, both APK verifications,
relevant real-device evidence, and the package-inclusive final human Nix check for
changed effective inputs. The count targets never override actual invalidation.

## This milestone's execution counts and validation

| Execution category | Count |
| --- | --- |
| Baseline full Gradle invocations | 0 |
| Focused Gradle invocations | 0 |
| Final normal full invocations | 0 |
| Strict-clean invocations | 0 |
| Artifact-verifier invocations | 0 |
| Physical harness invocations | 0 |
| Human Nix invocations | 0; not required for excluded docs |

No repeated full gates. Lightweight validation: local Markdown links/anchors,
balanced fences, required ladder classifications/commands/report coverage, changed-file
scope and historical/build-input preservation checks PASS; `git diff --check` PASS.
No Android builds were needed for these excluded documentation changes.

## Changed files and final Git state

- AGENTS.md: new permanent qualification ladder and reporting/invalidation rules.
- README.md: minimal current build/Nix guidance alignment and link to the ladder.
- review/ANDROID_QUALIFICATION_LADDER_REPORT.md: this milestone's evidence.

Final branch `main`; HEAD remains `45a7006345fb755fae11181e3ace74b1d7b0f390`.
Final short status:

```text
 M README.md
?? AGENTS.md
?? review/ANDROID_QUALIFICATION_LADDER_REPORT.md
```

Staged diff empty. No staging, commit, push, tag, release, CI dispatch, Android
product change, or agent-run Nix. Existing historical qualification evidence is preserved.
