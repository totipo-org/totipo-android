# M2D production otpauth enrollment

**M2D complete: implementation, agent validation, production device qualification and corrected-worktree human Nix checks PASS.** Both camera paths reached the same production confirmation on the final signed non-debug APK. Cancel, acknowledged GrapheneOS Share authorship, concealed-token persistence, locked discard and rotation expiration passed. M2D remains unstaged/uncommitted for review.

## 1. Starting state

Agent ran `git branch --show-current`, `git rev-parse HEAD`, and `git status --short` before implementation:

- Branch: `main`.
- HEAD: `a32e9ce7a0a4ff16232351bf42174b921c5de113`, `Qualify zero-dependency otpauth camera handoff`.
- Status: empty; M2C/M2C2 changes were committed separately from M2D.

The M2C report's historical “uncommitted” descriptions refer to its original evidence snapshot, not this clean committed starting state. No applicable AGENTS.md was found in the workspace or inspected ancestors. No agents were delegated.

## 2. M2C/M2C2 qualified transport baseline

Committed evidence is in `review/M2C_NATIVE_OTPAUTH_CAMERA_QUALIFICATION_REPORT.md`. On the previous Pixel 6a/Android 17/API 37/GrapheneOS setup:

- Pixel Camera: external STILL_IMAGE_CAMERA launch, QR scan, result tap, ACTION_VIEW to the debug probe.
- GrapheneOS Camera v93: QR scan, Share, Totipo target, ACTION_SEND text/plain; one String EXTRA_TEXT and one matching framework text-only ClipData mirror, without attachments.
- M2C2 exact public-fixture identity, negative transport checks, standalone framework checks, and human Nix results were recorded separately.

These are scoped, historical transport observations. M2D does not claim universal camera compatibility or reuse those observations as production confirmation/authorship evidence.

## 3. Production architecture

`OtpAuthEnrollmentActivity` → `OtpAuthTransport` shape extraction → one `OtpAuthUriParser` → Activity-instance confirmation draft → existing `AddTokenRequest` → existing `AndroidVaultController.addToken` → existing `ForegroundVaultCoordinator.addToken` → existing public Java live `VaultSession` authorship.

External parsing is absent from MainActivity. The Activity has no Java editor/session/save access. A controller `canAddToken()` observation exposes the same OPEN-and-not-operating admission predicate; `addToken` still rechecks it atomically. No vault reopen, KDF, secret singleton or second authoring path is added to enrollment.

## 4. Production manifest surface

Both APK variants have one exported `org.totipo.android.OtpAuthEnrollmentActivity`, labeled **Add to Totipo**, excluded from Recents, with two separate filters:

| Filter | Action | Categories | Data |
| --- | --- | --- | --- |
| VIEW | android.intent.action.VIEW | DEFAULT, BROWSABLE | scheme otpauth, host totp |
| SEND | android.intent.action.SEND | DEFAULT | MIME text/plain |

No HOTP, migration, SEND_MULTIPLE, wildcard MIME, image MIME or generic URI/file registration is added. The obsolete probe Activity and its debug filters are removed. Other existing debug diagnostics retain their prior roles.

## 5. Transport extraction rules

Filters are routing hints. Explicit callers undergo the same runtime validation.

VIEW requires action VIEW, a present hierarchical Uri, scheme otpauth, exactly encoded authority totp (ASCII canonical case variants accepted), no fragment, no selector/grants, no MIME/ClipData/payload extras, and at most 8192 UTF-16 units. Exact authority excludes userinfo, port and percent-encoded authority tricks. Raw label/query semantics are validated only by the shared parser.

SEND requires action SEND, MIME exactly text/plain, no data or selector, no URI grant flags, optional DEFAULT category only, and exactly one EXTRA_TEXT value of nonempty CharSequence type, at most 8192 UTF-16 units. Spans, arrays/lists, stream/HTML/unknown extras, whitespace/prose wrapping, and unsupported intent structures are rejected. Optional ClipData must have exactly one text/plain text item matching EXTRA_TEXT code-unit-for-code-unit, without URI, nested Intent, HTML or spans. This is the qualified mirror exception, not an attachment import feature. Both transports use one shared hierarchy/scheme/raw-authority/fragment envelope predicate before any vault admission check; HOTP/altered authority therefore receive fixed unsupported rejection even while locked or without a vault.

An unrelated share is rejected by a cheap URI-prefix check before controller observation/draft construction. Both accepted transport outputs feed the same semantic parser.

After extraction, EXTRA_TEXT, the entire extras Bundle, data, ClipData and selector are cleared, and the stored Activity Intent becomes an empty Intent. Framework callbacks receive null saved state or an empty new Intent. No raw input is forwarded.

## 6. Strict parser semantics

The pure Java parser bounds input before constructing `java.net.URI`; it uses only raw authority/path/query components, never Android query APIs or URLDecoder.

- Scheme/authority are otpauth/totp, case-insensitively recognized and interpreted as canonical TOTP. Hierarchical URI, no fragment/userinfo/port, and exactly `/LABEL` with no additional literal slash are required. Encoded label slashes are permitted.
- Strict percent decoding requires two ASCII hexadecimal digits per escape. Percent octets and literal Unicode use REPORT-mode UTF-8 encoding/decoding. Malformed, overlong, surrogate or unmappable encoding is rejected; no replacement characters are inserted. Literal `+` remains `+`, including accounts and issuer values.
- A decoded label has zero or one colon. One colon requires nonempty issuer prefix and account, and permits ASCII spaces immediately after that separator. No general trimming occurs. Multiple decoded colons fail.
- Query issuer and label prefix must match exactly when both exist; there is no case folding, normalization or trimming. Absent issuer becomes empty. A present empty issuer fails. Issuer/account cannot contain colon, NUL, ISO controls, Unicode format controls or line/paragraph separators. Each is bounded to 256 UTF-8 bytes, without truncation.
- Raw query splits on literal `&`; each nonempty element requires a nonempty name and `=`. Splitting at the first `=` preserves padding. Names are strictly decoded before exact lowercase recognition. Duplicate recognized names, including encoded-name duplicates, fail. Unknown names and values are strictly decoded then ignored. Case variants are unknown and cannot satisfy the required secret.
- Secret is required and nonempty. The URI lexical gate permits ASCII Base32 letters, 2–7, and `=` only. M2B `Base32.decode` supplies all padding, trailing-bit and decoded 1–128-byte correctness checks. No manual convenience whitespace/hyphens are accepted and no second Base32 decoder exists.
- Default algorithm SHA1; ASCII case-insensitive SHA1/SHA256/SHA512 only.
- Default digits 6; explicit 6 or 8 only, never 7.
- Default period 30; explicit unsigned ASCII decimal 1–4294967295, using bounded arithmetic without overflow or clamping.
- HOTP and recognized counter parameters are unsupported. Unsupported/malformed failures expose fixed messages and fixed reason enums without source details.

## 7. Secret ownership/lifetime

Draft contains only issuer/account, algorithm/digits/period, and an owned mutable Base32 `char[]`. It retains no source URI/query/Intent/Android Uri. Its redacted toString exposes no secret. `close()` is idempotent and zeros the buffer and drops semantic references; `transfer()` creates one existing AddTokenRequest and invalidates the draft. Transfer after close/transfer fails with fixed non-secret wording.

Decoder scratch bytes/chars, encoded literal bytes, Base32 validation bytes and the owned percent-decoder byte buffer are cleared in finally blocks. Parser failure clears any accumulated secret. Parsed URI/query and immutable temporary substrings exist only during bounded parsing.

Cancel, Back, unavailable/busy admission, parse/UI exceptions, save-state, backgrounding and destruction discard pending ownership. Add hands it to the existing controller, which clears accepted and rejected requests; coordinator still clears its decoded bytes/NewSecret/editor resources. The Activity never closes an admitted worker-owned request. There is no saved secret, persistent draft, clipboard operation or forwarding Intent.

**Erasure limitation:** sender-owned data, Android/Binder/task copies, immutable Uri/String/CharSequence values and parsing substrings cannot be guaranteed erased. Explicit clearing covers mutable buffers Totipo owns and removes retained references; it is not a JVM/framework memory-erasure guarantee.

## 8. Locked/no-vault behavior

After cheap ingress classification, `canAddToken()` is checked before semantic draft construction. No-vault, locked, unavailable controller/session and other non-admissible states discard the input and display **Unlock Totipo, then scan or share the QR again.** Transient BUSY displays **Totipo is busy. Scan or share the QR again.** If state changes after confirmation, the listener drops the draft. Add rechecks admission through M2B and wipes rejected ownership. Open Totipo passes no input or secret; unlock cannot resurrect the discarded link.

## 9. Confirmation UX

Valid open-vault enrollment shows **Add to Totipo**, with separate labeled Issuer, Account, Algorithm, Digits and Period controls. Issuer/account values are single-line. Secret, raw URI and query are never displayed. Add and Cancel are explicit; ingress does not author automatically. FLAG_SECURE, disabled view saving/autofill and a fixed Activity.dump protect presentation surfaces.

Pending ownership is Activity-instance-only. Save-state writes only a boolean consumed marker and omits framework/view state saving. Any recreated saved-state launch clears its Intent without extracting/parsing it and shows **Enrollment expired. Scan or share the QR again.** Backgrounding also expires confirmation deliberately.

## 10. Existing M2B authorship reuse

The actual current AddTokenRequest/Base32/controller/coordinator/MainActivity/application APIs were inspected before implementation. The draft uses mutable Base32 characters because that is the existing request representation; parser validation does not introduce a decoded-secret authoring API. The live coordinator/session remains the sole Java author. Existing public core 0.1.5 APIs suffice, with no upstream release or dependency change.

JVM integration tests verify no authoring merely from parsing/cancelling, one successful transferred request, same Java session, unchanged creation/open counts, concealed new token, and wiping on locked admission rejection.

## 11. SaveResult behavior

Existing controller/coordinator mapping and publication wording are preserved. Acknowledged Saved yields ADDED and opens the normal token list. PUBLICATION_UNCERTAIN retains the existing honest controller message and offers Open Totipo for the existing Refresh behavior. Definite failure/conflict/session failures display that same controller-supplied message. No enrollment-specific SaveResult reinterpretation, automatic retry or regenerated publication exists. No Add retry remains on the result screen. The existing M2B SaveResult tests remain part of check.

## 12. Scan QR external-camera UX

MainActivity's existing Add-token UI now offers **Scan QR with camera** plus concise Open/Share helper text. It starts an empty `MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA` Intent, without forced vendor/package, vault data, capture/result API or permission. It clears manual secret text before launching. Missing/inaccessible camera handling shows a fixed friendly message and leaves manual entry available. Scan and other form actions follow the existing busy-state enablement.

## 13. Qualification-probe cleanup

Removed debug OtpAuthIntentProbeActivity/filter and obsolete probe-specific JVM assertions. Preserved the exact supply-chain digest guards under production naming. Ported manifest/privacy guards to production.

Retained/refactored the standalone framework harness as `tools/device/OtpAuthIngressRegression.java` and `build-otpauth-ingress-tests.py`; replaced the obsolete resolver observer with `OtpAuthUiObserver`, which reports only fixed product/public-fixture label matches for production smoke evidence. The harness targets the production Activity, exercises transport and lifecycle behavior through Android framework objects and an isolated disposable cache vault, uses only the public fixture/empty disposable vault password, restores the process controller, and never touches the user's local vault root. Lifecycle ownership assertions use reflection only in the external test APK. These are black-box ingress/visible behavior tests with focused internal ownership checks; no test seam is added to the production Activity.

The harness is outside every app source set, builds with pinned javac/d8/aapt2 only, and is rejected from both Totipo APKs by the APK verifier. It emits fixed test identifiers/results, never exception text or UI dumps. Its build and 49-check physical execution passed on the connected Pixel 6a. It is useful as repeatable production regression tooling, not a qualification-only matching handler.

## 14. Test coverage

Deterministic JVM coverage includes defaults; both issuer sources and equality; literal/encoded colon and post-separator spaces; UTF-8; literal plus; algorithms/case; 6/8 digits; period endpoints/default/45; order permutations; unknown fields; lowercase/padded Base32; exact global/field/secret boundaries; malformed URI/authority/path/query; HOTP/migration/counter; empty/duplicate/encoded-name fields; percent/UTF-8/surrogate errors; issuer mismatch; invalid Base32/padding/trailing bits; BMP and supplementary Unicode controls; and one-shot draft ownership/wiping. Parser permutations execute many cases within each deterministic test method.

Existing M2B/controller/coordinator tests plus new parsed-draft integration cases exercise the live authoring path and admission wiping. Source guards assert exact main/debug manifest surfaces, absence of alternate authoring/persistence/logging APIs, and consumed state. Static tripwires are not a formal proof of information flow or erasure.

Standalone framework harness contains positive VIEW/SEND/matching mirror checks and negatives for unsupported text/scheme/HOTP/malformed URI, missing/wrong payload/MIME/action, arrays/lists/streams/unknown extras/grants/categories/selector, whitespace/prose, oversized text and URI/HTML/Intent/differing/multiple ClipData. It covers new-intent handling, no authoring before Add, Cancel/Back wiping, no-vault/locked discard, marker-only recreation/no reconsumption, rejected Add wiping, one successful concealed Add and lock/unlock persistence. All 49 standalone framework checks passed on the signed non-debug release APK; this automated device evidence remains separate from human camera scan/tap evidence.

## 15. APK/manifest verification

Updated `tools/verify-apk.py` requires the same exact production boundary for every APK, independent of variant flags. It rejects obsolete/duplicate otpauth/SEND handlers, HOTP/migration/SEND_MULTIPLE/wildcard/image filters, CAMERA/INTERNET, QR/camera/Play Services DEX families, and standalone test packages. Existing debug/release machinery exclusions, public Java/NIO/BC presence and unsigned release checks remain.

Final supported invocations:

```sh
python3 tools/verify-apk.py app/build/outputs/apk/debug/app-debug.apk --debug-probe
python3 tools/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk --unsigned --no-debug-probe
```

Both checks passed. The pulled final installed APK also passed the release boundary verifier and its SHA-256 exactly matched the signed copy below. Installed package resolution returned exactly one production Totipo Activity for the public VIEW fixture and SEND text/plain, and none for HOTP/https VIEW (`installed-boundary-sanitized.json`). Results and final APK hashes are recorded in section 21.

## 16. Supply-chain verification

No new production/test Gradle dependency, QR library, CAMERA or INTERNET permission. Dependency declarations, lockfiles, verification metadata, wrapper, Gradle properties, Java 0.1.5/BC 1.86 pins, Nix/cache inputs and package-deps remain unchanged relative to committed inputs (including the prescribed CRLF checkout of gradlew.bat). Inherited twelve-input SHA-256 JVM guards pass; existing Maven boundary verification remains enabled. No update-package-deps, agent Nix execution or dependency-cache regeneration was performed. Human Nix reruns passed after the test correction, as recorded in section 21. Ignored Gradle/SDK/test build artifacts are not tracked supply-chain input changes.

## 17. Physical Pixel Camera smoke

**PASS on the final installed release build.** Human unlocked the test vault, used Add token → Scan QR with camera → Pixel Camera → public fixture result tap → production confirmation, verified all five values, and cancelled. Human explicitly reported “Pixel Cancel passed; GrapheneOS confirmation shows expected metadata.” No token was authored by Pixel Cancel.

An earlier production-build pass also independently matched fixed metadata labels (`pixel-first-confirmation-sanitized.txt`) and human reported an unchanged list after Cancel. Final-build qualification uses the repeated human observation, not the earlier artifact.

## 18. Physical GrapheneOS Camera Share smoke

**PASS on the final installed release build, including reveal and persistence.** Human used Scan QR → GrapheneOS Camera → public fixture → Share → Totipo/Add to Totipo and reported the same production confirmation with expected metadata. Fixed-label observer independently matched `confirmation=true public_metadata=true added=false revealed=false` (`graphene-confirmation-sanitized.txt`).

Human tapped Add once and reported “Token added; new token is concealed in the list.” Observer independently matched `totipo=true public_token=true added=true revealed=false confirmation=false` (`graphene-added-concealed-sanitized.txt`). Observer uses bounded root polling, skips password fields and records only fixed label-match booleans, without codes or UI dumps. Human then explicitly confirmed that the existing Show action revealed the public token once, lock/unlock preserved it and returned it concealed, and the vault was locked again for the locked-ingress smoke. No code or password was requested, captured or recorded.

## 19. Locked-vault physical smoke

**PASS on the final installed release build.** Human used GrapheneOS Camera → public QR → Share → Totipo while the vault was locked and confirmed **Unlock Totipo, then scan or share the QR again.** without confirmation. Observer independently matched `unlock_retry=true confirmation=false public_metadata=false public_token=false` (`locked-camera-ingress-sanitized.txt`). Human then used Open Totipo, unlocked normally and confirmed no automatic enrollment appeared and the existing test token remained concealed. A fresh scan was required to reach confirmation again.

An earlier adb explicit SEND launch returned to MainActivity; the human/observer saw only the locked vault screen. That attempt is not counted as ingress evidence. The camera-origin retry above qualifies the production locked behavior.

## 20. Rotation/recreation smoke

**PASS on the final installed release build.** Human produced a fresh public-fixture confirmation without Add. Observer matched confirmation/all five metadata values. Agent changed the physical display rotation from its original `lock 0` to `lock 1`; the same release Activity recreated and observer matched `expired=true confirmation=false public_metadata=false public_token=false added=false` (`rotation-before-sanitized.txt`, `rotation-expired-sanitized.txt`). Original rotation setting was restored immediately. The standalone harness additionally verifies marker-only Bundle state, wiped draft buffers and no original Intent reconsumption. Human confirmed the expired message and that the list contained exactly one concealed public test token with no duplicate. Final observer matched `public_token=true revealed=false confirmation=false` (`final-concealed-list-sanitized.txt`).

## 21. Validation

Follow-up correction (2026-10-08): after the initial human success report, the human reported `TotpControllerTest.parsedEnrollmentUsesSameSessionAndOnlyAuthorsAfterTransfer` failing at line 108. Agent reproduced the original assertion failure on the first repeated targeted run: expected 3 visible tokens, observed 2, despite acknowledged ADDED. The test waited for controller OPEN/idle but Java session observation and the rendered token list update asynchronously after save acknowledgement. Existing M2B manual-add coverage already waits for the observed row. The new enrollment test now also waits for the exact expected token count using the existing bounded polling helper, without a fixed sleep or production policy change. Its immediate post-Add busy assertion was also removed: the worker may legitimately finish before that read; separate admission tests retain rejection coverage. It still verifies no authorship before transfer/cancel, acknowledged ADDED, exactly one added token, concealed presentation, and the same session with no reopen/KDF. Production code/APK behavior is unchanged. The corrected targeted test passed 20 consecutive `--rerun-tasks` executions (`.gradle/m2d-flake-fixed-repeated.log`); original failure evidence is retained in `.gradle/m2d-flake-reproduction.log`. Final follow-up full validation passed; the human subsequently confirmed both Nix reruns passed on this corrected worktree. Both rebuilt APK SHA-256 values exactly match the physically qualified production artifacts, so the test-only correction does not invalidate device evidence.

Agent validation: **PASS**.

```text
./gradlew check :app:assembleDebug :app:assembleRelease
BUILD SUCCESSFUL in 45s; 90 tasks (8 executed, 3 from cache, 79 up-to-date)

./gradlew --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug :app:assembleRelease
BUILD SUCCESSFUL in 1m 17s; 92 tasks, all executed

python3 tools/verify-wrapper.py
PASS
python3 tools/verify-apk.py app/build/outputs/apk/debug/app-debug.apk --debug-probe
PASS
python3 tools/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk --unsigned --no-debug-probe
PASS
```

176 JVM tests, zero failures/errors/skips. Existing Gradle deprecation notices do not fail validation. Final follow-up normal/offline logs: `.gradle/m2d-followup-normal-validation.log` and `.gradle/m2d-followup-offline-validation.log`. Original qualification logs remain in `.gradle/m2d-normal-validation.log` and `.gradle/m2d-offline-validation.log`. Wrapper and both APK verifiers also passed again after the test correction.

Final APK SHA-256:

- Debug: `da0c86ef1a80863556ef9b1fd8a12f8ce6ab8f4ac703345564ffb03e5084d6e8`.
- Unsigned release: `1bb7c67cda72bee4616111059437b3a327c6571cbd0ebee635d838243a8152c9`.
- Matching locally signed non-debug release copy: `404c3b8e9e533c25c86774211bd27a5f4472d1a8b49d10df0025ed5044701c58`.

The Pixel 6a was initially disconnected, then connected by the human. SDK 37, build CP3A.261005.002.A1; installed Pixel Camera version 10.4.117.936816638.14, GrapheneOS Camera 93. Installed signer was read from a pulled installed APK and matched certificate SHA-256 `1f14855aface333061eb0bb545251756a214aa02c31607735f003689eecf9005`. Agent signed/aligned ignored release and standalone test copies with the existing key, verified signer and production release APK boundary, and installed using `adb install --no-incremental -r`. No uninstall/app-data clear. Original validated debug/unsigned release outputs were not signed or modified.

Harness build: PASS using pinned SDK tools only. Initial physical execution passed no-vault discard but failed the first confirmation check while the phone was dozing with deviceLocked=1. This is not counted as a transport/confirmation pass. The human then unlocked the device itself; agent temporarily enabled stay-awake over USB for the rerun, with the original setting recorded for restoration. The unlocked-device rerun first passed 47 checks. After adding explicit locked/no-vault HOTP regression coverage and supplementary Unicode control rejection, the **final signed release APK passed all 49 checks**, including metadata, both transport shapes, lifecycle and actual public-fixture authorship/persistence in the isolated vault. Sanitized evidence is `.gradle/m2d-ingress/device-tests-sanitized.txt`; the initial locked-screen attempt is retained separately in `device-first-locked-screen-sanitized.txt`. This test harness creates/opens only an isolated disposable vault; its KDF work is test setup/persistence coverage and is not an additional enrollment KDF.

Human Nix: **PASS on the corrected worktree**, reported by the human on 2026-10-08: “yep, both passed this time.” The initial report was corrected to the line-108 test failure documented above; both commands were then rerun successfully after the fix. The agent did not run Nix; no dependency update was needed. Successful human reruns:

```sh
nix flake check path:.
nix build path:.
```

Human device: **PASS**, sections 17–20. Standalone helper `org.totipo.ingresstest` was uninstalled; Totipo and its test vault/token remain installed. Original display rotation (`lock 0`) and USB stay-awake setting (`0`) were restored. Remote CI: **NOT RUN**, deliberately not triggered. No ordinary debug authentication timing is used as performance evidence.

## 22. Limitations

All required validation passed, including corrected-worktree human Nix checks, production physical checks and standalone harness execution; their evidence is scoped to this device/build/camera versions. Framework/camera/sender copies cannot be erased by Totipo; scanner support and Sharesheet routing vary by installed apps/device/version. Totipo intentionally appears for unrelated text/plain shares and rejects them cheaply. Background/rotation confirmation expiration requires rescanning; enrollment carries no secret through unlock. No claims of universal QR compatibility, formal static-proof secrecy or new performance benchmarks are made.

## 23. Final Git state

M2D remains unstaged/uncommitted on main at `a32e9ce7a0a4ff16232351bf42174b921c5de113`. No staging, commit, tag, release, push, remote CI or agent Nix action was performed. The final commands below are agent-run; tracked diff statistics omit the new untracked production classes, tests, report and standalone harness files.

```text
$ git status --short
 M app/src/debug/AndroidManifest.xml
 D app/src/debug/java/org/totipo/android/debug/OtpAuthIntentProbeActivity.java
 M app/src/main/AndroidManifest.xml
 M app/src/main/java/org/totipo/android/AndroidVaultController.java
 M app/src/main/java/org/totipo/android/MainActivity.java
 M app/src/main/res/values/strings.xml
 D app/src/test/java/org/totipo/android/OtpAuthProbeSourceGuardTest.java
 M app/src/test/java/org/totipo/android/TotpControllerTest.java
 D tools/device/OtpAuthResolverObserver.java
 D tools/device/OtpAuthShareQualification.java
 D tools/device/build-otpauth-share-tests.py
 M tools/verify-apk.py
?? app/src/main/java/org/totipo/android/OtpAuthEnrollmentActivity.java
?? app/src/main/java/org/totipo/android/OtpAuthTransport.java
?? app/src/main/java/org/totipo/android/OtpAuthUriParser.java
?? app/src/test/java/org/totipo/android/OtpAuthEnrollmentSourceGuardTest.java
?? app/src/test/java/org/totipo/android/OtpAuthSupplyChainGuardTest.java
?? app/src/test/java/org/totipo/android/OtpAuthUriParserTest.java
?? review/M2D_OTPAUTH_ENROLLMENT_REPORT.md
?? tools/device/OtpAuthIngressRegression.java
?? tools/device/OtpAuthUiObserver.java
?? tools/device/build-otpauth-ingress-tests.py
```

```text
$ git diff --stat
 app/src/debug/AndroidManifest.xml                  |  15 --
 .../android/debug/OtpAuthIntentProbeActivity.java  | 198 ---------------------
 app/src/main/AndroidManifest.xml                   |  14 ++
 .../org/totipo/android/AndroidVaultController.java |   4 +-
 .../main/java/org/totipo/android/MainActivity.java |  14 +-
 app/src/main/res/values/strings.xml                |  11 ++
 .../android/OtpAuthProbeSourceGuardTest.java       | 131 --------------
 .../org/totipo/android/TotpControllerTest.java     |  32 ++++
 tools/device/OtpAuthResolverObserver.java          |  64 -------
 tools/device/OtpAuthShareQualification.java        | 129 --------------
 tools/device/build-otpauth-share-tests.py          |  46 -----
 tools/verify-apk.py                                |  95 +++++-----
 12 files changed, 123 insertions(+), 630 deletions(-)
```

```text
$ git diff --check
(empty output)
```

```text
$ git diff --cached --stat
(empty output)
```

