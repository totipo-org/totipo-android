# Android persistent local qualification-signing migration

Status: **COMPLETE — REAL MIGRATION, HOST CHECKS, PHYSICAL CONTINUITY AND FINAL FROZEN-SOURCE HUMAN NIX PASS.**
Final Nix PASS is human-reported after the ordered freeze request. All 185 frozen
input contents/modes independently reverified unchanged afterward. Only this
excluded report finalized after that result; agent Nix executions remain zero.

## 1. Starting state

Clean `main`, HEAD `e41b29e09fa1d7f0d97c64509f15c4538a6b44d2`.
`git status --short` empty; `git diff --cached --quiet` PASS. VERSION 0.1.2,
VERSION_CODE 3. Wave 2B committed as `7b900e6`; Git-aware Nix policy committed
as starting HEAD. No unrelated untracked files. Read AGENTS, README, ignore,
package/flake, CI, Wave 2A final completion and Wave 2B reports and relevant tools.
No committed root signing audit is available; current facts independently inspected.

## 2. Classification

**TEST_OR_QUALIFICATION_TOOLING / LOCAL_QUALIFICATION_SIGNING**.
No product, protocol, production release-signing or dependency migration.

## 3. Old identity layout

Explicit old operational sources:
`.gradle/m1l-inspection/m1k-debug.keystore` and
`.gradle/m1m-inspection/signing-password`. Both PRESENT at baseline, current-user
owned regular files, modes 0644 and 0600 respectively. No historical directory,
APK, script or report deleted/rewritten.

## 4. Verified starting public identity

Pinned JDK 17.0.20.1+1, SDK build-tools 36.0.0 via AndroidToolchain.
PKCS12, one PrivateKeyEntry, alias `wave2a-qualification`.
Certificate SHA-256:
`d862bbf279d306e7062440d299440d54ecdf594e1cf8ac3046cdf5d3a0ed7f49`.
Keystore file SHA-256:
`34c68a024a297ce915379227db3fab0c18efd01fdfe024790d976da5e5ba956e`.
Public profile: CN=Disposable Wave 2A Qualification; RSA 2048; SHA256withRSA;
valid 2026-10-11 through 2036-10-08. Public metadata only recorded.
No password value/hash or private-key encoding output.

## 5. Target layout

`.qualification/signing/{keystore.p12,password,identity.json}`.
Both directories 0700; all three files 0600; current-user ownership; no symlink
files/components. Root ignore `/.qualification/`. Local state survives ordinary
Gradle/direnv cleanup but **git clean -fdx deletes it**.

## 6. Metadata contract

Exactly schema (integer 1), purpose
`totipo-android-local-qualification-signing`, nonempty alias and lowercase 64-hex
certificateSha256. Unknown/missing/duplicate fields and malformed JSON fail.
Every load exports the actual keystore certificate and compares alias/digest;
metadata is no independent signing authority. No path, password, timestamp or
production metadata is stored in identity.json.

## 7. Migration implementation

One owner: `tools/device/qualification_signing.py`, using existing AndroidToolchain.
Explicit `migrate SOURCE_KEYSTORE SOURCE_PASSWORD_FILE`; no historical search.
Requires one PKCS12 private entry, exact Wave 2A certificate and alias. Copies
keystore/password bytes into a 0700 staging directory, 0600 files; writes strict
metadata; fsyncs files/directory; fully reloads; reserves destination exclusively;
atomically renames the verified directory over the empty reservation; fsyncs parent;
reloads and compares both byte sequences before retiring sources.

## 8. Failure/interruption semantics

Existing/partial destination, missing files, unsafe modes/ownership, symlinks,
metadata mismatch, ambiguous entries, wrong password/format and tool failures
refuse operation. No key generation or fallback. Before publication the old
source remains authoritative. A crash at reservation leaves partial canonical
state that fails closed; a complete publication may coexist with old private
copies until deliberate cleanup. Retirement failure keeps valid destination and
explicitly reports duplicates; it does not claim success or choose another key.
Tool failures redact subprocess output. Concurrent modification/use of identity
state is unsupported; documentation explicitly requires serialized operator use.

Review discovered that `-storetype PKCS12` plus a similarly named system property
still permits JKS through JDK compatibility delegation. Corrected before freeze:
a temporary **public-only** JDK security-properties append sets
`keystore.type.compat=false` while preserving pinned providers. A direct actual
JKS inspection negative now exercises refusal, independently of migration's
fingerprint/alias restrictions. The migrated store passes the corrected loader;
its bytes did not change. Real migration was not repeated.

## 9. Fresh-provision disposition

Deferred. Existing RSA-2048/SHA256withRSA and ten-year certificate profile inspected.
This preservation milestone adopts no future-environment certificate subject,
lifetime or provisioning/reprovisioning policy. A future explicit provision command
can reuse the same owner, but must require deliberate new disposable-environment
setup, generate a strong password and never overwrite/repair missing state.
There is no provision command or generation fallback now.

## 10. Loader/preflight contract

Only per-checkout `.qualification/signing` is loaded. No HOME/XDG/debug-keystore,
production-variable, historical-key or retained-APK authority. Exact POSIX modes
and owner checked; hosts without POSIX semantics fail. Public certificate inspection
uses the pinned JDK and checks actual PKCS12. Sign reloads its identity before use.
Status reports only validity, canonical directory, alias and public fingerprint.
All three supported callers' actual load expressions independently evaluated
against the migrated identity without executing their device/scenario bodies.

## 11. Production-signing separation

Forbidden signer derives from committed
`release/signing/android-release-cert.sha256`; no duplicate production constant,
production secret access or private fallback. Device tooling runs in the full
checkout where that public file exists; it is not invoked by package installCheck.
No source-filter change is needed. Production PEM/digest, release executable code,
protected workflow and provenance/publication logic are byte-identical to HEAD.
Only signing README wording clarified that production private keys remain forbidden
anywhere in the repository/Nix inputs; ignored non-production state is a narrow exception.

## 12. Detached/smoke-signing separation

android_harness.test_sign, row/security tooling and ephemeral release signing-smoke
remain byte-identical. No detached keys adopted/moved. Release signing-smoke not run.

## 13. Current caller changes

SAF outbound/bootstrap and M3D use load, Identity.sign and Identity.verify.
No private paths copied into callers. Installed product independently checked
against the local expected identity. Both SAF candidates now finish signing and
verification before installation; release-first install ordering retained.
No manifests, packages, provider backup/restore, fixtures, scenarios or evidence
oracles changed. Alignment -P 16 and disabled v1/v4 preserved; v2/v3 verified.

## 14. Inbound/ingress supported signer

`python3 -B tools/device/qualification_signing.py sign INPUT.apk --aligned ALIGNED.apk --out SIGNED.apk`.
Generic APK adapter only; builders retain unsigned construction and procedures
retain install/run/cleanup. No ignored sign-install script or private-path copying
needed. It never invokes adb.

## 15. M3D expected identity

Removed retained M3C signed-APK expected-signer authority. Local expectation comes
from validated identity; observed installed signer and every candidate signer are
verified separately before any install. Retained historical evidence remains.

## 16. Unit/mechanical tests

Baseline: shared harness 8 PASS; M3D oracle 10 PASS.
Latest post-device combined suite: **29 PASS** (8 harness + 21 identity/caller),
23.044s, `/tmp/totipo-qualification-signing-post-device-tests.log`; oracle 10 PASS.
All tracked tools Python AST, current-caller old-authority search, protected-input
non-change, secret-exclusion, index and git diff --check PASS. No shell changed.
Covers missing/partial/no fallback, all metadata failures, actual alias/fingerprint,
real JKS rejection, multiple entries, symlinks/modes/ownership/POSIX refusal,
byte/password preservation, wrong migration identity, existing destination,
staging failure and retirement failure, production refusal, actual disposable-key
APK signing/schemes/alignment, exact invocation, generic CLI and actual orchestration
mismatch-before-install for all three callers. Real qualification key never copied
to test fixtures. Disposable keys generated in temporary directories only.

Initial combined development run: 28 tests, two test-fixture errors (mock intercepted
keytool reload; os.name mock affected pathlib before platform check). Corrected;
next 28 PASS. Added SAF orchestration check: pre-migration 29 PASS. Independent
JKS probe exposed the compatibility gap above; strengthened direct format negative
and hardened parser: latest 29 PASS. No product/build gate failure.

## 17. Real migration result

**PASS; explicit migration command executed exactly once.**
Evidence `.gradle/qualification-signing-migration/starting-public-identity.json`,
`migration.log`, `migration-result.json`. Subsequent normal status PASS.

## 18. Certificate/alias/byte preservation

Starting and final certificate/alias exactly as section 4. Keystore file SHA-256
unchanged. Both keystore and password compared byte-for-byte before source
retirement in migration and independently against in-memory starting bytes after
command completion. Password comparison boolean only recorded; no password hash.
Corrected strict-format loader subsequently confirms actual PKCS12 unchanged.

## 19. Old private-copy retirement

Both specified old private files ABSENT. No other old files removed. Valid new
identity retained. There are no migration-created copies outside canonical state;
temporary staging directory removed/published. Historical APKs remain evidence.

## 20. Destination permissions

Verified .qualification 0700, signing 0700, keystore.p12/password/identity.json 0600,
current uid 1000; no symlinks. Modes recorded in migration-result.json.

## 21. Non-device signing smoke

**PASS**, final hardened owner. Fresh copied unsigned ingress and existing aligned
unsigned qualification product signed through new CLI; zipalign and apksigner
PASS; certificate exactly local expected; package/target inspected; every ZIP entry
and payload byte unchanged. Product verifier PASS with --no-debug-probe.
Evidence `.gradle/qualification-signing-migration/final-smoke/`.
Copied SAF outbound helper also signed/verified via the same owner and manifest
confirmed product-targeted. Existing historical APKs not overwritten.

Initial two-APK smoke repeated after strict-format loader hardening; no APK/signing
behavior change. Final product SHA-256
`0f262717e1a8ffa3e7d6925e15f50940366035f6af73826a44ddd605c285be30`;
final ingress SHA-256
`03ab58000b1b8161bab4a671a204eb353cc6020fdc19ceeb0ebdc727a65fb49f`.
These are observations, not historical APK-byte acceptance criteria. Product input
is retained unsigned evidence because app/build output is absent; no fresh build claimed.

## 22. Device/environment identity

**PASS.** User confirmed “device connected/unlocked” and “data is disposable”.
Retention is the explicitly requested scope of this smoke; no real vault/data used.
Pixel 6a, serial `37311JEGR05916`, API 37, fingerprint
`google/bluejay/bluejay:17/CP3A.261005.002.A1/2026100601:user/release-keys`.
Installed org.totipo.android APK SHA-256
`0f262717e1a8ffa3e7d6925e15f50940366035f6af73826a44ddd605c285be30`;
certificate d862…7f49, independently verified against canonical identity before
any mutation. All five SAF/bootstrap/inbound/ingress/M3D helpers absent.
Product UID 10257; dataDir /data/user/0/org.totipo.android; firstInstallTime
2026-10-11 09:16:27. Persisted READ/WRITE grant (0x3) for
content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FTotipo-M3A-Test,
persistedCreate 1791729893376. Full evidence under
`.gradle/qualification-signing-migration/device/`.

Initial read-only capture runner treated pm path's empty exit-1 response for absent
helpers as a command error. Corrected the external runner to recognize that exact
absence result before mutation. No product/device failure or tracked-input change.

## 23. Targeted in-place update

**PASS**, exactly one `adb -s 37311JEGR05916 install --no-incremental -r`
using final-smoke/product-signed.apk signed through the canonical CLI.
Streamed install returned Success. Product never uninstalled, reset or rekeyed.
Package appId/dataDir/firstInstallTime/version and normalized persisted grants
matched the captured baseline immediately afterward. Installed APK was pulled
again after cleanup and independently signer-verified.
Existing product and new signed output happen to share APK bytes; successful
replacement installation, not historical hash equality, is the continuity proof.

## 24. Targeted instrumentation

**PASS**, one helper install and one instrumentation execution:
`adb -s 37311JEGR05916 shell am instrument -w -e inspectOnly true org.totipo.safqualification/org.totipo.safqualification.SafOutboundRegression`.
Actual exit 0 and output:

```text
M3B stored_binding_present PASS
M3B persisted_read_grant PASS
M3B persisted_write_grant PASS
M3B persisted READ + WRITE inspection PASS
```

This supported path returns before provider queries, isolated-vault construction
or scenario work. It is materially smaller than ingress's 49 checks/controller
substitution and directly observes retained binding/grants. No full SAF/M3D/ingress
scenario rerun. Product-targeted manifest and signer independently checked.

Temporary external runner falsely required an INSTRUMENTATION_CODE footer that
Android omitted when delivering the finish stream. The actual four PASS lines and
exit 0 were preserved, inspected and accepted; instrumentation and installations
were **not rerun**, and no source/artifact/device recovery change was made. Continued
with cleanup only. No signature refusal, state loss or actual instrumentation
failure occurred.

## 25. Retained-state/grant evidence

**PASS within external observation limits.** Baseline, immediately post-update and
final appId/dataDir/firstInstallTime/version identical. URI, target provider/package,
persisted flags 0x3 and persistedCreate identical. Actual product-context inspection
confirmed stored binding plus persisted READ/WRITE. Private vault contents were not
opened/authenticated, exported or compared; no claim of every private byte preserved.
Evidence package-before/after-update/final.txt, uri-grants-before/after-update/final.txt,
inspection.txt and device/result.json.

## 26. Device cleanup

**PASS.** Helper uninstall Success; subsequent pm path confirmed absence. Product
retained and final APK signer independently confirmed d862…7f49; final APK SHA-256
0f262717e1a8ffa3e7d6925e15f50940366035f6af73826a44ddd605c285be30.
Settings never changed; stay_on_while_plugged_in remained 0. Product force-stopped
and cold-launched via MainActivity (Status ok, LaunchState COLD), retiring any
in-memory session without data clear or authentication. Persisted grants still match.
Optional UI hierarchy observation saw only system keyguard and no labels, so no
independent visual unlock-screen claim is made. No provider fixture writes occurred.

## 27. Product/release non-change

Read-only git diff checks PASS: app main/debug/tests, runtime/protocol, Gradle
versions/locks, VERSION/CODE, production certificate/digest, workflows, release
executable tools and publication/provenance; android_harness, row/security unchanged.
No Gradle inputs changed. Under qualification invalidation matrix, host device
Python changes require affected harness/host checks and human Nix, no Gradle rebuild
solely for harness code/prose. No full normal/strict Gradle run claimed or required
for these unconsumed host-only edits. No new external dependency.

## 28. Git/Nix secret exclusion

`git check-ignore .qualification/signing/keystore.p12` PASS;
`git ls-files .qualification` empty. Canonical state ignored, never intent-added.
All intended new source/test/report files made Git-known using intent-to-add only.
No staged file contents. Existing tracked modifications remain unstaged.
Package selects tools/app/src/gradle and enumerated build inputs; .qualification
matches no selection. Git-aware flake also excludes ignored state. Root README,
AGENTS and review excluded/unconsumed; release/signing README not a package input.
No package/evaluator/cache/source-filter edit.

## 29. Qualification accounting so far

| Category | Count/result |
| --- | --- |
| Full/focused Gradle, strict-clean | 0; host-only unconsumed qualification tools |
| Shared-harness baseline | 1 × 8 PASS |
| Combined identity/harness suites | 5: 28 with 2 fixture errors; 28 PASS; 29 PASS; 29 PASS; post-device 29 PASS |
| M3D oracle suites | 4 × 10 PASS |
| Standalone actual JKS probe | 1 exposed compatibility acceptance; corrected direct negative in latest suite |
| Real migration | 1 PASS; never repeated |
| Persistent APK smoke sets | 2 × product+ingress; second after parser correction |
| Prepared read-only SAF helper | 1 sign/verify PASS |
| Product APK boundary checks | 3: retained unsigned once; initial signed once; final signed once |
| Wrapper/debug APK external final set | 0; no build inputs/artifacts changed |
| Physical runs/installs | 1 product update; 1 helper install; 1 read-only instrumentation PASS; 1 helper uninstall |
| Human Nix | 2 human-reported PASS: early unsolicited result and final ordered gate |
| Agent Nix | 0 |

Directly affected host checks rerun after physical/cleanup stabilization and passed.
No unrelated release Python suites or release signing-smoke run. No full gate was
invalidated by an included-source edit after the early human Nix result; the final
Nix request is repeated solely to satisfy the explicitly requested physical →
final-host → freeze → human-Nix order, not to diagnose a failing Nix check.

## 30. Frozen-input inventory

**FROZEN after physical cleanup and final host checks.** All four statements:
production source stable (unchanged), JVM tests stable (unchanged), build/package
configuration stable (unchanged), qualification harness/tooling stable (latest tests
and physical evidence PASS). No further included-input edits planned.

Inventory `.gradle/qualification-signing-migration/frozen-inputs.jsonl`: 185 Git-known
files outside review/, with relative path, POSIX mode and SHA-256, sorted compact
JSONL. SHA-256 `9a098546d5bd32b6db93a217a94d91c3f77bba69e3d6b45c5da9bdeb6c0e17da`.
Covers all effective package/evaluator/cache inputs and supplemental tracked docs,
.gitignore, workflow and public signing files. All intended new code/tests/report
are Git-known through intent-to-add; no staged content. Existing tracked edits
remain unstaged. Summary freeze.json.

`.qualification/signing` is LOCAL SECRET STATE intentionally excluded from Git/Nix,
**not part of frozen source inventory**. No password/private file hash recorded in
that inventory. Only this excluded report may change after freeze; package.nix and
flake.nix do not consume review/. Included files unchanged since early Nix PASS.

## 31. Human Nix

**PASS, human-reported after final physical → final-host → source-freeze request.**
User replied “nix flake check passed” to the final request for exactly
`nix flake check` from repository root. No log/artifact path supplied; no fresh
artifact hash or independent Nix execution claimed. Agent Nix executions zero;
no path: source, build or rebuild request.

Earlier human PASS before physical/freeze is recorded separately. No included
source changed between the two results. The final routine request was made once;
repetition relative to the early unsolicited run enforces the explicitly specified
ordering, not diagnosis or invalidation.

Post-result verification: all 185 frozen files' contents/modes and inventory digest
unchanged; Git-known non-review inventory exactly matches; original main/HEAD,
no staged content, ignored/Git-unknown identity, actual alias/fingerprint, preserved
keystore SHA-256 and absent old private copies all PASS. Local result evidence
`.gradle/qualification-signing-migration/final-human-nix-result.json`.
Only this excluded report finalized afterward; package/flake do not consume review/.
No effective source or evaluator/cache input edited, no gate invalidated.

## 32. Limitations/recovery

All required gates and post-result inventory verification complete. Never
regenerate, uninstall or reset to fix continuity failure. Missing
key means unrelated replacement cannot update installed qualification product;
deliberately preserve/back up this ignored per-checkout identity. Another checkout
needs explicit same-identity transfer. No universal filesystem, private-state-byte,
visual unlock-screen or fresh-provision guarantee claimed. No actual certificate,
update, retained-state or instrumentation failure occurred.

## 33. Final Git state

Main remains starting HEAD. Changes uncommitted and content unstaged. Modified:
.gitignore, README.md, release/signing/README.md, three supported qualification
callers. New: qualification_signing.py, test_qualification_signing.py, this report.
No secrets tracked/staged. git diff --cached --name-only empty and cached --quiet
PASS; git ls-files .qualification empty; check-ignore PASS. All nine intended
changed files remain content-unstaged/uncommitted (new files have intent-to-add
only). No unrelated untracked files; no commit/stage/push performed. Only this
excluded report changed after final Nix PASS; frozen inputs remain unchanged.

## 34. Suggested commit subject

`Own local Android qualification signing identity`
