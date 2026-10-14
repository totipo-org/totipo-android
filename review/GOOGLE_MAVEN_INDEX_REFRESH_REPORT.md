# Google Maven index refresh — 2026-10-14

Classification: BUILD_DEPENDENCY (Nix response-cache metadata and reviewed inventory),
with README documentation. Starting branch: clean `main`; starting HEAD:
`e206b48b889caa9634ece02c587bd21696be77ee`.

## Failure and change

CI reported a fixed-output mismatch for `master-index.xml`: expected
`sha256-g3pgwRb08TSHEO+jqm/M7Ck1iyMu/jcTNmxJnnwSfHs=`, received
`sha256-TY5pdjdTh6YY8xjvPuyvu051HkN2jO/njbNfYIjGibY=`.
An independent HTTPS download from `https://maven.google.com/master-index.xml`
redirected to `https://dl.google.com/dl/android/maven2/master-index.xml` and
returned application/xml, 10,277 bytes, XML root `metadata`, 335 group entries.
Python hashlib SHA-256 matched CI's received hash exactly.
This establishes metadata drift; it does not establish that any released library
artifact changed or why Google changed the index.

Initially updated only that response hash and its reviewed checksum. The user
then ran `nix run .#update-package-deps` before their successful `nix flake check`.
Review of the regenerated cache against HEAD confirms exactly two semantic changes:
the independently verified Google index hash and removal of the Gradle tooling-api
Maven metadata response under `https://repo.gradle.org`. All other entries,
artifact hashes, versions, locks and Gradle verification metadata are unchanged.
Preserved the regenerated cache and updated its reviewed whole-file SHA-256 to
`e809a7b1bd4714a7cc56d9d43227d4ad0c2a25cc400d41458af218a1c954ce63`.
README documents the equivalent narrow metadata-refresh procedure.

## Validation and scope

- Independent download, XML parse and hash comparison: PASS.
- `sha256sum --check tools/release/reviewed-inputs.sha256`: all ten pins PASS.
- `gradle :app:testDebugUnitTest --tests 'org.totipo.android.OtpAuthSupplyChainGuardTest'`:
  PASS, four tests, zero failures/errors/skips; pinned environment Gradle 9.8.0/JDK 17.
- `python3 -B -m unittest discover -s tools/release -p 'test_*.py'`:
  PASS, 58 tests; rejection diagnostics are expected negative-test fixtures.
- `git diff --check`: PASS.

Inspected README, AGENTS.md, package.nix, flake.nix/lock, CI workflows,
review/M0_NIX_CACHE_REVIEW.json and reviewed-inventory consumers. package.nix reads
package-deps.json through fetchDeps. Its Gradle source excludes that JSON and includes
`tools/`, so the inventory changes a check input. The inventory is read by the
focused JVM guard and release-tool source-pin validation; it does not change Android
compilation, dependencies, resources or packaging. Apply the package-only and tooling
invalidation rules: focused consumers checked, no APK rebuild or device run solely
for metadata/inventory data. Existing normal/strict APK evidence is not re-executed;
Nix qualification is required for the changed response-cache/check inputs.
README and review/ are excluded from effective Android check inputs under the
inspected filter; subsequent report-only updates do not invalidate Nix.

## Freeze and human gate

- Production source stable: no production file change from starting HEAD.
- JVM tests stable: no test-source change; focused supply-chain guard PASS.
- Build/package configuration stable: final cache hash and matching reviewed inventory;
  all other evaluator/build/lock inputs unchanged.
- Qualification harness/tooling stable: only reviewed inventory data changed;
  focused consumers PASS, no further tool edits planned.

Human reports `nix flake check` PASS after running `update-package-deps`.
The subsequent agent review found the reviewed whole-file checksum still referred
to the earlier index-only refresh; checksum validation failed for package-deps.json.
Updated the inventory to the regenerated cache digest above, then revalidated all
ten digests successfully. This included tooling-data edit invalidates the previous
Nix pass under AGENTS.md. The four freeze statements above now apply to these
final inputs. The user subsequently reported the second `nix flake check` PASS.
Post-pass verification confirmed all ten reviewed digests match and `git diff --check`
passes. Qualification is complete. Agent ran no Nix commands. Only this excluded
report was updated afterward; this does not invalidate the final Nix result.

After the inventory correction, the full focused JVM class invocation was
UP-TO-DATE: Gradle does not track the external inventory as a task input.
Ran specifically `gradle :app:testDebugUnitTest --tests
'org.totipo.android.OtpAuthSupplyChainGuardTest.reviewedSupplyChainInputsRemainPinned'`
to execute the affected digest guard against the current files: PASS.
Release-tool suite repeated after correction: 58 tests PASS.

## Counts and Git state

Baseline full Gradle: 0; focused Gradle: 3 test invocations (one UP-TO-DATE); final normal full: 0;
strict-clean: 0; external APK verifier: 0; embedded Gradle APK verifier: 0;
physical/device phases: 0; human Nix: 2 reported PASS; first invalidated by inventory correction, second final.
Human cache regeneration: 1 reported run; its internal task counts/logs not supplied.
Release-tool suite: 2 agent runs, 58 tests each.
The required repeated Nix gate is explained above. Counts reflect the metadata/inventory-only scope above.

Changed files: package-deps.json, tools/release/reviewed-inputs.sha256, README.md,
and this report. Final branch remains main at starting HEAD. All changes unstaged;
report intent-to-add establishes Git source membership only. No staged content,
commit, push or release. Final status/name-only/cached-content checks recorded
before the human gate and rechecked afterward; only these four paths changed.
