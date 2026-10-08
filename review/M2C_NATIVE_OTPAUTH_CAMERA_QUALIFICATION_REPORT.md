# M2C native-camera / otpauth qualification

Sections 1–16 are the prior M2C evidence snapshot. The current uncommitted M2C2 continuation is recorded below; its validation and verdict supersede the earlier snapshot for the extended probe.

## 1. Starting state

Agent inspection on 2026-10-08: branch `main`, HEAD `2dbfe4f4a83a8dba6bb598fd74f4462b88253ab6` (committed M2B). Initial `git status --short` was empty. No applicable AGENTS.md was found in the workspace or its inspected ancestors. Work is intentionally unstaged/uncommitted.

## 2. Goal / supply-chain rationale

Qualify whether the Pixel 6a's normal GrapheneOS camera recognizes a public disposable `otpauth://totp` QR and offers an Android ACTION_VIEW route into Totipo. Recognition and decoding belong outside Totipo in this experiment. Success could avoid a decoder dependency, camera permission, frame handling and image persistence. This is a transport gate; no production URI parser, QR button or token creation is implemented.

## 3. Android ACTION_VIEW inspection

Application ID and namespace are `org.totipo.android` in both variants (no debug suffix). Compile/target SDK 37, minimum SDK 26; pinned build tools 36.0.0. Main manifest exports only MainActivity, with MAIN/LAUNCHER. Existing debug manifest also exports DebugTotpFixtureActivity, StorageProbeActivity (launcher), LocalNioProbeActivity and AuthPerfActivity. The new exported probe exists exclusively in debug.

MainActivity is presentation over TotipoApplication's process-scoped AndroidVaultController. M2B remains MainActivity → AddTokenRequest → AndroidVaultController → ForegroundVaultCoordinator → public Java VaultSession authorship. MainActivity, TotipoApplication and every production authorship source remain unchanged.

Official Android documentation inspected:

- [Intents and intent filters](https://developer.android.com/guide/components/intents-filters): VIEW identifies the viewing action; implicit Activity launches require a DEFAULT filter category. Intent action/category/data matching is distinct from an explicit component launch, which bypasses filters.
- [Create deep links](https://developer.android.com/training/app-links/create-deeplinks): custom schemes use manifest filters; BROWSABLE permits browser-originated entry. Multiple matching apps can produce a chooser or an existing default can win. Custom schemes are not verified domain ownership.
- [MediaStore](https://developer.android.com/reference/android/provider/MediaStore#INTENT_ACTION_STILL_IMAGE_CAMERA): STILL_IMAGE_CAMERA opens still-image camera mode. IMAGE_CAPTURE captures an image and returns image data; it is not a generic decoded-QR return contract.

These establish standard Android intent routing, **not** the installed camera's willingness to dispatch arbitrary custom-scheme QR content. Camera and separate system-scanner behavior must be observed independently on the physical device. No documented generic QR result contract was established from these inspected camera actions.

## 4. Debug probe design

`app/src/debug/java/org/totipo/android/debug/OtpAuthIntentProbeActivity.java` and the debug manifest register exactly VIEW, DEFAULT, BROWSABLE, scheme `otpauth`, host `totp`, exported=true. No HOTP registration. The Activity is excluded from Recents and marks stateNotNeeded=true.

Validation reads only action, data presence, hierarchy, exact scheme/host and exact encoded authority. It rejects missing data, non-VIEW action, wrong scheme, HOTP, opaque URIs and altered authorities (userinfo/port/encoded authority variants). It does not inspect enrollment fields or deeply validate path/query syntax: a malformed enrollment payload with a valid transport envelope can pass, deliberately, because no production parser exists yet. Runtime validation failures expose no details.

Only a boolean survives validation. The UI displays `otpauth TOTP link received` or `Unsupported link`. No raw URI, account, issuer, path, query or secret is displayed/logged. The debug-only camera action can show the fixed `Native camera unavailable` label on ActivityNotFoundException/SecurityException.

The original Intent has data/extras/ClipData cleared and the Activity's stored Intent is replaced with an empty Intent before framework onCreate/onNewIntent processing. URI locals are confined to the validation scope. onCreate receives null state; save/restore callbacks skip framework/view saving; fixed widgets disable saving. No URI is forwarded to the camera.

## 5. Release invariance

No production source or main manifest change. Comparing the freshly built unsigned release ZIP with the existing local M2B signed artifact found only signing entries and META-INF/version-control-info.textproto differences; all shared manifest/DEX/resource entries match byte-for-byte. Overall APK hashes therefore differ; release invariance is not a byte-identical ZIP claim. The release APK verifier rejects probe classes/labels, debug machinery and any otpauth manifest registration. Both APK variants are checked for absence of CAMERA and INTERNET permissions using the merged binary manifest, decoded by the existing pinned SDK aapt2 tool.

No dependency, lockfile, verification metadata, version, flake or package-deps input change. SHA-256 baseline of twelve build/dependency inputs is stored locally in ignored `.gradle/m2c-native-camera/input-hashes.json`; inputs were compared byte-for-byte with HEAD. A JVM guard pins these twelve baseline digests; the three evaluator/cache files deliberately omitted by package.nix are checked when present in the workspace. The other nine must exist even in the filtered Nix build tree. Existing Gradle dependency graph verification remains authoritative. No QR library or new production dependency was added. Ordinary Gradle execution may populate ignored build/distribution caches; no tracked cache inputs are regenerated.

## 6. Direct ACTION_VIEW qualification

**PASS for physical implicit resolution and Activity launch.** On the connected Pixel 6a (`37311JEGR05916`), Android 17/API 37, GrapheneOS build `2026100601`, agent queried VIEW+BROWSABLE resolution using the public URI and launched it with `am start -W`. No package/component was specified for the positive launch. The selected component was `org.totipo.android/.debug.OtpAuthIntentProbeActivity`, and launch returned Status: ok. No chooser was reported in this direct launch. Raw ADB launch output was held only in subprocess memory and not printed/saved; evidence records fixed status and component only. In-memory UI hierarchy attempts using `/dev/tty` and `/proc/self/fd/1` did not establish a fixed-label observation for the initial direct launch. Subsequently, the human explicitly confirmed the same probe’s success label after a Pixel Camera result tap, as recorded in section 8. No screenshot or raw UI dump was saved.

The initial debug APK certificate differed from the installed development certificate, so the agent stopped before installation. Existing ignored `.gradle/m1l-inspection/m1k-debug.keystore` matched the installed certificate. Agent signed an ignored copy `.gradle/m2c-native-camera/m2c-debug-signed.apk`, verified its certificate and APK boundary, checked 16 KB alignment, and installed it using `adb install --no-incremental -r`. Existing app data was preserved; no uninstall/data clear. Original validated build outputs are untouched.

Installed signing certificate SHA-256: `1f14855aface333061eb0bb545251756a214aa02c31607735f003689eecf9005`.

Signed debug copy SHA-256: `c9be185501b30b7f56138b5239cd83e3b785a6bdbe3844140e360831314f570e`.

Physical package queries restricted to Totipo confirm wrong scheme (`https://totp/`), HOTP and opaque `otpauth:totp` shapes do not resolve to the probe. Explicit negative Activity invocation/label checks remain unobserved. Sanitized evidence is stored in ignored `.gradle/m2c-native-camera/device-sanitized-results.json`. Direct resolution establishes routing, not camera behavior.

## 7. QR fixture

Public disposable test URI (one line):

```text
otpauth://totp/Totipo%20Camera%20Test:test@example.invalid?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=Totipo%20Camera%20Test&algorithm=SHA1&digits=6&period=30
```

The secret encodes the public ASCII test value `12345678901234567890`, used for SHA1 in [RFC 6238 Appendix B](https://www.rfc-editor.org/rfc/rfc6238#appendix-B). This is not a personal account or credential. No token is authored.

Ignored `.gradle/m2c-native-camera/public-test-uri.txt` exists. `command -v qrencode` found no command, Python importlib found no qrcode package, and no suitable installed QR renderer was located. The human was asked to render the exact URI using a trusted offline tool and display it on another screen. The human subsequently reported scanning the otpauth QR image, establishing that a displayed image was available. Its rendering method, exact payload equivalence and an ignored local image copy have not been independently confirmed. `.gradle/m2c-native-camera/public-test-qr.png` is not present. The agent used no online generator or package installation. Only the public disposable fixture was requested; no personal URI or token authorship was requested.

## 8. Native camera results (kept separate)

**Pixel Camera: recognizes otpauth QR; a user tap launches Totipo through the otpauth probe, without a chooser.** Human-reported physical evidence: Pixel Camera recognized the scanned URI and opened Totipo. In response to a focused follow-up, the human selected “Tapped result → success screen, no chooser,” explicitly confirming `otpauth TOTP link received`. This qualifies recognition and delivery of a VIEW/otpauth/totp transport envelope: the probe validates those properties before displaying that label. Automatic launch was neither observed nor required. No token was authored.

Agent inspected the installed Pixel Camera package `com.google.android.GoogleCamera`, versionName `10.4.117.936816638.14`, versionCode `69481630`, on the Pixel 6a running Android 17/API 37, GrapheneOS build `2026100601`. The human explicitly clarified that Pixel Camera was installed after the initial camera test. It was added during qualification, not established as part of the starting GrapheneOS system-camera setup. The agent did not install it or record its installer/source. It is an external installed camera app distinct from the GrapheneOS system/default camera. The human performed the scan and result tap; the agent did not capture camera frames, screenshots or raw decoded content. The positive verdict relies on the explicit human observation, not on an automated capture of the incoming Intent action.

**GrapheneOS Camera: recognizes/decodes the QR, but offers Copy/Share only; no clickable dispatch to Totipo.** The earlier human observation reported decoded content that could not be clicked, with Copy and Share available. No Open action, Totipo offer, chooser or camera-originated Totipo launch was reported for that camera. No copying or sharing was requested or performed by the agent. Package `app.grapheneos.camera`, versionName/versionCode 93, was inspected separately. Its behavior remains a negative result for the required handoff even though Pixel Camera works.

Before the earlier scan, the agent reopened the debug probe without data so the Open native camera button was available after the human accidentally restarted the app. After installing Pixel Camera, the human clarified that the debug button offered a choice of camera apps. Read together with the reported Pixel Camera scan/result tap/success screen, this establishes the chooser-mediated round-trip described in section 10. The positive Pixel Camera result must not be attributed to the GrapheneOS camera.

[GrapheneOS Camera privacy policy](https://grapheneos.org/camera-privacy-policy) states that camera does not make network connections and does not automatically open scanned URLs. That policy applies to GrapheneOS Camera, not Pixel Camera. No network/privacy guarantees for Pixel Camera are inferred from this public-fixture transport test.

## 9. Optional system QR scanner result

Not tested. Availability of a separate Quick Settings/system scanner is unknown. Any future result must be recorded independently of the normal camera result.

## 10. Totipo → native camera round-trip result

**QUALIFIED after the human installed Pixel Camera, via the camera chooser — human-reported across the clarified test sequence.** The debug probe’s Open native camera button offered a choice of installed camera apps. The human used Pixel Camera, scanned the otpauth QR, tapped the recognized result, and explicitly confirmed Totipo’s `otpauth TOTP link received` screen with no chooser on that incoming link. The chooser occurred at camera selection, not at otpauth dispatch.

The debug button uses `startActivity(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))`, without URI/extras, image capture, hardware ownership or Activity result. No capture/result contract, forced camera package or vendor-private API is used. It catches missing/inaccessible camera Activities with a fixed label.

The earlier agent package-resolution query returned `app.grapheneos.camera/.ui.activities.CameraLauncher` for STILL_IMAGE_CAMERA and `app.grapheneos.camera/.ui.activities.CaptureActivity` for IMAGE_CAPTURE. That query did not establish the actual outgoing launch UI. The human’s explicit observation of a camera chooser after installing Pixel Camera is authoritative for that later button experience. Camera availability changed during testing: the earlier resolution query and the later chooser observation must not be treated as observations of an unchanged installed-camera set.

The qualified usability path is Totipo debug button → camera chooser → Pixel Camera → QR recognition → user result tap → Totipo probe success screen. Selecting GrapheneOS Camera instead retains its independently observed Copy/Share-only limitation. Camera-choice UI and defaults may differ on other devices/configurations; no universal chooser behavior is claimed.

## 11. Security/privacy analysis

The probe adds a debug external Activity entry surface. Filters constrain implicit routing; explicit callers can bypass them, so Activity validation is mandatory. It invokes no vault operation, decoder, authoring request, clipboard or storage API. It neither saves nor forwards transport data. FLAG_SECURE protects the fixed screen; exclusion from Recents reduces unnecessary probe restoration.

Immutable Android URI/String objects cannot be reliably wiped. Clearing references and Intent data reduces application retention but does not erase framework/Binder/task or sender-owned copies, nor control the camera's treatment of a decoded URI. The external Activity boundary must be reassessed before shipping production handling. Only the public test fixture is deliberately written outside product state; no incoming URI is written there. Source guards check forbidden logging, persistence, forwarding and authorship APIs; packaged APK guards check release exclusion. These are guards, not a proof against all possible future source changes.

## 12. Primary result

**A. NATIVE CAMERA PATH QUALIFIED — conditionally, using Pixel Camera installed by the human during testing.** Pixel Camera recognizes the otpauth QR and, after the user taps its result, dispatches to Totipo’s debug handler. The human confirmed the fixed success label and absence of a chooser. The probe can show that label only for VIEW with data present, hierarchical URI, exact otpauth scheme and totp host/authority. Direct implicit ACTION_VIEW resolution and launch also passed independently.

This result qualifies a compatible installed Android camera using the standard camera/VIEW intent path; it does not qualify the built-in GrapheneOS camera experience. Pixel Camera was an added external-app prerequisite, not a pre-existing system component. For the original built-in GrapheneOS-only setup, the required handoff is not available in the observed test. This is not an unconditional guarantee about Android cameras or the default GrapheneOS camera: the latter decoded the same type of QR but exposed Copy/Share only. A separate system QR scanner was not tested. The debug camera-button round-trip is qualified through the human-observed camera chooser and successful Pixel Camera path. Both requested Nix checks passed according to the human. QR-generation method/exact fixture provenance and an ignored local image copy remain unconfirmed in the recorded evidence; a displayed QR image and camera scan are human-reported. The URI text fixture is available locally and uses only a public disposable secret.

## 13. Product recommendation

**Recommend the zero-dependency production transport architecture for users of a qualified external camera:** ACTION_VIEW otpauth handler → strict parser → confirmation → existing M2B AddTokenRequest → existing public Java authorship. Do not add a QR-decoding dependency based on this qualification. This recommendation is for the next milestone; no production handler, parser, QR button or authorship mapping is implemented here.

Product UX must explain or otherwise account for camera support and availability: the tested Pixel Camera works, while the tested GrapheneOS default camera does not offer the handoff. Standard Android custom-scheme intent support alone cannot promise every camera will work. Treat relying on an installed compatible camera as an explicit product decision. On the tested setup, the positive route required the human to add Pixel Camera; it is therefore zero new Totipo/Gradle dependencies, but not zero additional software for that original setup. This result does not justify advertising universal or built-in GrapheneOS camera enrollment. The experiment does not qualify Pixel Camera’s distribution/supply-chain/privacy properties or instruct users to install it as a universal product requirement.

An optional production Open camera convenience action is supported by the qualified chooser-mediated round-trip: use the documented STILL_IMAGE_CAMERA launch without passing URI data or expecting a QR Activity result, then accept the separate incoming ACTION_VIEW. Camera selection must account for compatible-camera support; choosing GrapheneOS Camera does not provide the handoff in this test. Do not force a vendor package or assume every device exposes the same chooser/default behavior. M2B remains authoritative for token creation, and a confirmation boundary remains necessary for external input.

## 14. Validation

Agent: **PASS**, final source validated with both requested builds:

```text
./gradlew check :app:assembleDebug :app:assembleRelease
BUILD SUCCESSFUL in 43s; 90 actionable tasks

./gradlew --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug :app:assembleRelease
BUILD SUCCESSFUL in 59s; 92 actionable tasks, all executed

python3 tools/verify-wrapper.py
PASS: Gradle 9.8.0 wrapper JAR and distribution pins

python3 tools/verify-apk.py app/build/outputs/apk/debug/app-debug.apk --debug-probe
PASS

python3 tools/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk --unsigned --no-debug-probe
PASS
```

167 JVM tests, zero failures/errors/skips, including four new probe guards. Source/manifest guards cover exact debug registration, permissions, release source absence, sensitive-data lifetime, absence of declared QR dependencies and supply-chain digest pins. APK guards cover actual packaged manifests and DEX boundaries. Existing Maven graph checks pass; final byte comparisons confirm all twelve dependency/cache inputs unchanged from HEAD. Normal/offline build logs are ignored at `.gradle/m2c-native-camera/normal-validation.log` and `offline-validation.log`. Existing AGP experimental-option/Gradle-deprecation notices did not fail checks.

Final debug APK SHA-256: `54d0bce638c00392d90d132db8b63ac84ec4f86040d83ccc86dc9edcdb27dab6`.

Final unsigned release APK SHA-256: `dc77ea134d6ad3d875102dd80e5df6fc947522b20116f0e95bfd73267711b628`.

Final release comparison against the existing local M2B signed artifact again confirmed only signature files and version-control metadata differ. No CAMERA, INTERNET, otpauth handler or debug probe appears in release. Initial ADB inspections returned an empty device list. After the human connected the phone, physical direct resolution and Activity launch passed as described in section 6. No camera success is inferred from the build or direct test. Signed debug-copy APK verification and alignment also passed.

Human Nix: **PASS — human-reported** for both `nix flake check path:.` and `nix build path:.`. After the agent requested those two commands, the human reported “they passed.” No command output was supplied, so this is explicitly human-reported evidence. The agent ran no Nix commands; no Gradle-through-Nix duplication was requested.

Human device: **Pixel Camera handoff PASS — explicitly human-reported result tap → Totipo success screen, no chooser. GrapheneOS Camera handoff NOT QUALIFIED — separately human-reported decoded result with Copy/Share only.** Human connected the phone and scanned the otpauth QR. The human additionally confirmed Pixel Camera was installed during testing and that the button then offered a camera-app chooser, completing the clarified Pixel Camera round-trip evidence. Fixture-generation provenance/local-copy evidence remain unrecorded. Separate system scanner was not tested. Agent performed safe ADB signing/install/resolution/launch work. No Git or Gradle work was requested from the human.

Remote CI: not triggered or run for this milestone.

## 15. Qualified scope

The debug probe implementation and artifact boundaries can be qualified locally. Physical Android implicit resolution and Activity launch are qualified on this device. The tested installed Pixel Camera’s result tap → Totipo success-screen transport is qualified by explicit human observation. The tested GrapheneOS Camera’s required handoff is not qualified. Qualification requires a compatible camera; Pixel Camera was added by the human during testing. It is not an Android-wide/default-camera or built-in GrapheneOS guarantee. The outgoing debug button → camera chooser → Pixel Camera → Totipo round-trip is qualified by the clarified human observations, scoped to this device/configuration. No claim applies to other cameras, GrapheneOS versions, devices or system scanners. No parser/authorship correctness claim is made for external input. M2B remains authoritative for token creation.

## 16. Final Git state

`git status --short` (exit 0):

```text
 M app/src/debug/AndroidManifest.xml
 M tools/verify-apk.py
?? app/src/debug/java/org/totipo/android/debug/OtpAuthIntentProbeActivity.java
?? app/src/test/java/org/totipo/android/OtpAuthProbeSourceGuardTest.java
?? review/M2C_NATIVE_OTPAUTH_CAMERA_QUALIFICATION_REPORT.md
```

`git diff --stat` (exit 0):

```text
 app/src/debug/AndroidManifest.xml | 10 ++++++++++
 tools/verify-apk.py               | 42 +++++++++++++++++++++++++++++++++++++++
 2 files changed, 52 insertions(+)
```

`git diff --check` (exit 0):

```text
(no output)
```

`git diff --cached --stat` (exit 0):

```text
(no output)
```

Diff stat describes tracked changes only; the new Activity, source guard and this report are untracked. Fixture/input-hash/build-log artifacts are ignored under .gradle. Nothing staged, committed, tagged, released or pushed. No CI trigger and no Nix commands run by the agent.

# M2C2 GrapheneOS Camera Share → ACTION_SEND continuation

## 1. Starting state

Agent ran `git branch --show-current`, `git rev-parse HEAD`, and `git status --short` on 2026-10-08. Branch `main`, HEAD `2dbfe4f4a83a8dba6bb598fd74f4462b88253ab6`. Starting changes were the debug manifest and APK verifier, plus untracked OtpAuthIntentProbeActivity, OtpAuthProbeSourceGuardTest, and this M2C report. No applicable AGENTS.md was found. This continues uncommitted M2C, rather than creating a separate committed milestone/report.

## 2. Prior evidence

Direct implicit ACTION_VIEW resolves to Totipo. Pixel Camera recognized the public test QR and the human confirmed its result tap → Totipo fixed VIEW success label. GrapheneOS Camera recognized the QR but offered Copy/Share only. Those observations remain separately scoped to the earlier physical device/configuration; SEND success must not be inferred from them. The existing VIEW filter and validation remain in place, with a positive device regression test.

## 3. Goal / supply-chain rationale

Determine whether GrapheneOS Camera can hand decoded URI text to Totipo through Share → ACTION_SEND text/plain. The potential architecture remains external recognition/decoding → external-input validation → future strict otpauth parser → confirmation → existing M2B AddTokenRequest authorship. Only debug transport qualification is implemented. No production parser, token creation, QR dependency, camera permission, clipboard action, network permission, dependency/cache regeneration or production share target is added.

## 4. Debug ACTION_SEND probe

The same debug-only exported OtpAuthIntentProbeActivity has two **separate** intent filters: unchanged VIEW/DEFAULT/BROWSABLE/otpauth/totp, and SEND/DEFAULT/text/plain. There is no SEND_MULTIPLE or broad MIME registration. Release has neither filter nor Activity.

Explicit callers bypass filters. The runtime SEND gate requires ACTION_SEND, exact non-null text/plain, one EXTRA_TEXT entry containing nonempty CharSequence text, at most 8192 UTF-16 code units, and no spans. It rejects arrays/lists/non-text values, unknown extras (including subject/HTML), data URI, selector, unexpected categories, URI-grant flags, and any stream extra. Only absent categories or DEFAULT alone are allowed. Scheme, hierarchical shape, host and encoded authority must be exactly otpauth/totp; no account, issuer, secret, algorithm, digits or period is parsed. Literal whitespace/control characters are rejected. No URI extraction from prose or normalization is performed.

**ClipData distinction:** a single text-only item identical to EXTRA_TEXT is accepted as one logical payload represented twice. ClipData presence alone does not imply an attachment. Multiple items, a different text item, URI, nested Intent, HTML, wrong ClipData MIME, or spans are rejected. This preserves the requested attachment/multiple-payload rejection while supporting standard Android text mirroring. [AOSP Intent.migrateExtraStreamToClipData](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/content/Intent.java) constructs this mirror for SEND text, including within CHOOSER. Direct and physical observations below independently establish its occurrence on this device. This is a transport representation exception, not permission to import attachments.

The SEND UI shows only `otpauth TOTP share received` or `Unsupported shared content`. The VIEW labels and native-camera debug button remain separately available for VIEW. No production enrollment semantics are inferred from a success label.

## 5. Privacy / intent lifetime

No incoming text/URI/query/secret/issuer/account is logged, displayed, persisted, copied to/from clipboard, forwarded, included in exceptions or written to a report. The only URI definition in this report remains the already-public fixture in M2C section 7. Exact fixture equality is debug-only evidence, not production parsing.

After consumption, EXTRA_TEXT is removed if practical; malformed-extra removal exceptions are swallowed without details. The entire extras Bundle, data, ClipData and selector are then cleared, and the stored Activity Intent is replaced with an empty Intent before framework callbacks. Only booleans/fixed enums survive. Temporary character copies are bounded and their mutable array is cleared. Immutable String/CharSequence/Uri memory, Binder/framework/task copies and sender-owned copies **cannot be guaranteed erased**; this limitation remains explicit.

FLAG_SECURE, exclusion from Recents, stateNotNeeded, null initial saved state, disabled view saving and omitted state saving/restoration preserve the prior privacy boundary. No incoming content is placed in saved state.

A debug Activity.dump override emits only fixed enums/booleans, and deliberately skips the framework super.dump implementation. It records action/MIME categories, EXTRA_TEXT/ClipData/stream presence, a safe payload type category, matching-text-mirror status, clean shape, public-fixture equality classification and accepted result. No payload length is retained because it was unnecessary. STRING denotes actual java.lang.String; CHAR_SEQUENCE denotes another CharSequence without recording arbitrary class names. ADB dump output is captured only in subprocess memory, reduced to this fixed diagnostic line, and only those sanitized facts are retained. No screenshot, camera frame, raw UI dump, clipboard inspection or Logcat capture is used.

## 6. Release invariance

Merged APK guards verify both variants lack CAMERA/INTERNET. Debug guards require the two exact filters and the exported probe. Release guards reject the probe, any otpauth/SEND manifest surface, probe labels/classes, and standalone device-test classes. Source guards cover debug-only registration, privacy/lifetime APIs, absent production authorship/clipboard/network, and unchanged supply-chain inputs. Standalone tests are outside the app source sets and are not packaged in Totipo. The temporary org.totipo.qualification helper package was removed after qualification; Totipo remains installed and app data was preserved.

Release SHA-256 remains `dc77ea134d6ad3d875102dd80e5df6fc947522b20116f0e95bfd73267711b628`, identical to the previous unsigned M2C build. Comparison with the existing signed M2B APK again found only three JAR-signature entries and META-INF/version-control-info.textproto differences; shared manifest/DEX/resources are identical. All twelve pinned build/dependency/lock/verification/flake/cache inputs are byte-identical to HEAD. No Nix command or cache regeneration was run by the agent.

## 7. Direct ACTION_SEND qualification

**PASS on physical Pixel 6a.** Agent signed an ignored debug copy with the existing matching development key, verified signer/alignment, and installed with `adb install --no-incremental -r`. No Totipo uninstall or app-data clear. Certificate SHA-256 remains `1f14855aface333061eb0bb545251756a214aa02c31607735f003689eecf9005`.

Positive ADB invocation used implicit `am start -W -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT` with the exact section-7 fixture, **without package/component**. Totipo was included in the queried candidates. Android launched android/com.android.internal.app.ResolverActivity; a framework-only qualification observer selected Totipo and observed the exact fixed success label. The first observer permitted fixed Totipo and Just once labels; the observed destination/success facts confirm it selected Totipo. No forwarding/network target was selected. Its final source was subsequently hardened to permit Just once only after a successful Totipo selection. No default is set deliberately.

Sanitized received facts:

```text
action=SEND mime=TEXT_PLAIN extra_text_present=true clip_present=true
stream_present=false payload_type=STRING clip_text_mirror=true clean_shape=true
fixture_shape=EXACT exact_public_fixture_match=true accepted=true
```

Signed debug copy SHA-256: `b8bb0b58e91d8a572db5b652d032dc4bd8aa70acdf22e6c57adc38364736d55a`. Original build outputs are untouched by signing. Ignored first successful evidence: `.gradle/m2c2-share/direct-first-qualified-sanitized.json`, preserved from the original successful tool output. A supplementary final observer attempt is separately recorded in `direct-sanitized.json`: Android again resolved to its resolver, but selection/label observation was unavailable because the phone had entered Dozing with keyguard showing and deviceLocked=1. No fixed probe fact was retrieved for that supplementary attempt. It is not counted as a second routing pass or a handler failure, and does not replace the earlier positive qualification or exact-fixture camera evidence.

## 8. Negative SEND tests

**PASS.** Eleven explicit ADB launches bypassing filters safely rejected ordinary public text, https://example.com, HOTP, missing EXTRA_TEXT, SEND_MULTIPLE, text/html, missing MIME, an integer EXTRA_TEXT, multiple text values, a stream URI, and VIEW with the SEND text-only shape. Each returned launch Status: ok, without a launch crash, and the probe's sanitized accepted=false fact. Fresh test tasks (`NEW_TASK | CLEAR_TASK`) isolate ADB test results without clearing app data. Earlier repeated top-Activity launches left stale observations, so those were not counted as negative qualification; isolated launches were used instead.

Additionally, the standalone framework-only instrumentation in `tools/device/OtpAuthShareQualification.java` passed **34 checks**. These verify actual sanitized UI labels and empty stored Activity Intent, including String/CharSequence positives; null/empty/missing/oversized text; MIME negatives; arrays/lists; unknown extras/data/category/URI grants; preserved VIEW behavior; whitespace/prose rejection; matching ClipData text mirror acceptance; URI/HTML/Intent/differing-text/multiple-item ClipData rejection; and positive/negative SEND plus VIEW onNewIntent processing. No exception messages/input are emitted. A harness launch flag error was corrected before the successful run; it was not a probe rejection/crash.

`python3 tools/device/build-otpauth-share-tests.py` builds the ignored standalone test APK with the existing pinned SDK (javac, d8, aapt2), no Gradle graph changes or test-library dependencies. It was signed with the matching local test identity and installed separately as org.totipo.qualification. The final observer was rebuilt after restricting Just once selection to follow a successful Totipo selection. Its supplementary rerun stopped at the subsequently locked device, as explicitly separated from the earlier PASS in section 7. The framework instrumentation command was `adb shell am instrument -w org.totipo.qualification/org.totipo.qualification.OtpAuthShareQualification`. Evidence is `.gradle/m2c2-share/device-tests-sanitized.txt` and `direct-negatives-sanitized.json`. No product or vault operation is called.

## 9. GrapheneOS Camera Sharesheet behavior

**Human-observed: Totipo shown directly → selected → `otpauth TOTP share received`.** The human scanned the public test QR in GrapheneOS Camera, tapped Share, saw Totipo directly, selected it and left the screen open. Expanded-target availability was unnecessary; absence is disproved for this setup. No network/cloud forwarding was requested or used.

Agent independently inspected only sanitized probe facts after that observation. Device: Pixel 6a, Android 17/API 37, GrapheneOS build 2026100601; GrapheneOS Camera package app.grapheneos.camera, versionName/versionCode 93.

## 10. Received payload structure

First camera observation, with a QR later confirmed by the human to be different from the documented fixture:

```text
action=SEND mime=TEXT_PLAIN extra_text_present=true clip_present=true
stream_present=false payload_type=STRING clip_text_mirror=true clean_shape=true
fixture_shape=OTHER exact_public_fixture_match=false accepted=true
```

This establishes a bounded, single logical otpauth/TOTP URI text payload, with no literal whitespace/control characters or prose prefix. It **does not** establish equality to the exact documented public fixture. No raw text was read out to identify the difference. The earlier QR's exact payload/provenance had already been unconfirmed in M2C section 7. The human explicitly confirmed scanning a different QR and asked how to render this fixture. The agent found no installed offline renderer, then generated a 600×600 PNG from the exact documented **public disposable fixture only** using the [goQR image API](https://goqr.me/api/doc/create-qr-code/). No QR code/library/dependency was installed or added to the project. Ignored fixture image: `.gradle/m2c2-share/public-test-qr.png`, SHA-256 `6d4e70693e1069543964f80b3227728ee4bd11b13261f301d22e2a9cedb67ebf`. The image was displayed/provided for the successful physical retest recorded below. This network request was fixture preparation on the development machine, not Camera Share forwarding or a product enrollment requirement. No incoming share or real authenticator URI was transmitted to the renderer. The successful retest established exact identity through the probe’s internal equality comparison, without recording incoming content. A second non-SEND fact from an older explicit VIEW-negative task was present in the same dump and excluded from the camera-share observation.

**Exact documented fixture retest: PASS.** The human scanned the displayed fixture PNG in GrapheneOS Camera, saw Totipo directly in Share, selected it, and explicitly reported the fixed success label. Agent immediately retrieved:

```text
action=SEND mime=TEXT_PLAIN extra_text_present=true clip_present=true
stream_present=false payload_type=STRING clip_text_mirror=true clean_shape=true
fixture_shape=EXACT exact_public_fixture_match=true accepted=true
```

Only this SEND fact was present on the retest dump. It exactly matches the documented fixture and demonstrates no surrounding whitespace, newline wrapper or prose. The text-only ClipData mirror is identical, with no attachment. Payload decision: **A. EXACT OTPAUTH TEXT**. Sanitized first-attempt and retest evidence are retained separately in `.gradle/m2c2-share/camera-share-sanitized.json`. The different initial QR is not used as exact-fixture evidence, and its content was never inspected or recorded. No parsing around prose or changed fixture assumptions is introduced.

## 11. Primary result A–E

**A. GRAPHENEOS CAMERA ACTION_SEND QUALIFIED.** On this Pixel 6a/GrapheneOS/Camera version, Camera Share directly exposed Totipo, and selection delivered clean ACTION_SEND text/plain containing the exact documented public otpauth/TOTP URI. The human confirmed the fixed success label, and agent-retrieved sanitized facts confirmed exact equality, String type, matching text-only ClipData mirror, and no stream. Direct implicit SEND routing and negative checks passed independently. Both requested Nix commands passed according to the human. The different initial QR was superseded by an exact-fixture physical retest. This is the sole current M2C2 primary result; the earlier M2C verdict applies to its separate VIEW scope.

## 12. Product UX/security analysis

| Future input | UX | Privacy / surface tradeoff |
| --- | --- | --- |
| ACTION_VIEW otpauth | Qualified compatible scanner result tap routes by URI scheme/host. | Narrower routing surface, but custom schemes are not verified ownership and all external input needs strict validation/confirmation. |
| ACTION_SEND text/plain | GrapheneOS Camera Share can offer Totipo; potentially avoids clipboard use. | Totipo would also appear for unrelated text shares; MIME filters cannot select only otpauth payloads. A future handler must reject unrelated text, bound input, reject attachments, minimize lifetime and confirm before authorship. Sharesheet previews/sender copies remain outside Totipo's control. |
| Explicit Copy → Paste-import | User copies in Camera, then opens a deliberate Totipo paste action. | Avoids a generic production share target and reads clipboard only after a user action, but introduces extra steps and clipboard exposure/lifetime outside Totipo's control. |

Option 1 is production SEND/text/plain registration with strict rejection and confirmation. It would broaden Totipo's appearance across generic text shares; that UX/security surface needs a deliberate review before shipping. Option 2 is no production SEND registration, with a future explicit Paste authenticator URI action. Neither is implemented or approved for production here. [Android's receiving guide](https://developer.android.com/develop/ui/compose/sharing/receive) documents action/MIME registration and receiver-side validation; the broad text-share exposure is the consequence of that matching model.

A future paste path could be Camera Copy → Totipo explicit user action → one clipboard read → strict parser → confirmation → M2B authorship. It should never poll/read clipboard automatically or write secrets to the clipboard from this probe. Android documents clipboard previews, access notifications and sensitive-content controls; Totipo cannot assume a third-party Camera marks its copy sensitive. See [Android copy/paste documentation](https://developer.android.com/develop/ui/views/touch-and-input/copy-paste). Paste import could preserve zero Totipo decoder dependencies, with a different UX/privacy cost. It is analysis only.

## 13. Supply-chain conclusion

No QR library is justified by the evidence so far: Android VIEW and SEND transport can deliver decoded URI envelopes without a Totipo decoder. Native-camera recognition remains external, with compatibility scoped to installed camera/device versions. Exact-fixture camera qualification is now established on this setup; deliberate generic share-target review remains a gate before shipping production SEND. No ZXing, CameraX, ML Kit, Play Services, other barcode dependency, lock/verification/cache change or CAMERA permission is added.

## 14. Validation

Agent: **PASS** for the requested builds/verifiers and physical direct tests:

```text
./gradlew check :app:assembleDebug :app:assembleRelease
BUILD SUCCESSFUL in 1s; 90 tasks (final normal run; first cold run 2m 25s)

./gradlew --offline --no-daemon --no-configuration-cache --no-build-cache --rerun-tasks --dependency-verification=strict clean check :app:assembleDebug :app:assembleRelease
BUILD SUCCESSFUL in 1m 8s; 92 tasks, all executed

python3 tools/verify-wrapper.py
PASS
python3 tools/verify-apk.py app/build/outputs/apk/debug/app-debug.apk --debug-probe
PASS
python3 tools/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk --unsigned --no-debug-probe
PASS
```

167 JVM tests, zero failures/errors/skips. 34 standalone physical framework tests passed. Eleven explicit ADB negative cases passed. Implicit ADB SEND routing, resolver selection and sanitized success-label observation passed. Debug APK SHA-256: `00e168775b7ca4ad0961a6d2e35f240d816d274db50f1d0084f02f5371999df1`. Release hash is in section 6. Ignored normal/offline logs are `.gradle/m2c-native-camera/m2c2-normal-validation.log` and `m2c2-offline-validation.log`. Existing AGP/Gradle deprecation notices did not fail validation. Final git whitespace/index checks are recorded below.

Human Nix: **PASS — explicitly human-reported for the current M2C2 changes.** The human selected “Both Nix commands passed” after being asked for `nix flake check path:.` and `nix build path:.`. No output was supplied, so this is human-reported evidence. No Gradle-through-Nix duplication was requested. Agent ran no Nix commands.

Human device: first camera SHARE → Totipo target directly visible → fixed success label **PASS**, explicitly human-reported. Payload structure independently retrieved via safe ADB. Exact fixture identity **PASS on the subsequent displayed-fixture retest**, independently established by the probe’s exact-public-fixture-match boolean. Human performed scan/tap/selection only; Git/Gradle/sign/install/direct tests were performed by the agent.

Remote CI: not triggered or run. No staging, commit, tag, release, push or publication.

## 15. Qualified scope

Physical SEND routing into the debug probe is qualified on this Pixel 6a/configuration, independently of camera behavior. GrapheneOS Camera target availability and clean URI-envelope share delivery are observed. The first camera payload came from a different QR; the subsequent exact-public-fixture retest qualifies clean GrapheneOS Camera SEND delivery. This is scoped to the observed physical Sharesheet and installed versions, with Totipo directly visible; expanded-only availability on other configurations is not claimed. No enrollment parsing, semantic validity, issuer/account fidelity, authorship, universal scanner compatibility, production share-target acceptance, clipboard workflow or external-app privacy guarantee is qualified. VIEW remains separately qualified.

## 16. Product recommendation

**Recommend a zero-new-Totipo-dependency compatibility matrix:**

| Qualified camera / scanner | Future transport |
| --- | --- |
| Tested Pixel Camera / compatible scanners | ACTION_VIEW otpauth://… |
| Tested GrapheneOS Camera | Share → ACTION_SEND text/plain |

Both transports should converge on the same future strict otpauth parser, confirmation boundary and existing M2B AddTokenRequest path. Do not add a QR library. No production parser/enrollment handler or authorship mapping is implemented here. Separately review whether broad generic text/plain share-target exposure is acceptable before shipping SEND. If the product chooses to avoid that exposure, evaluate explicit Copy → user-initiated Paste-import as a zero-dependency fallback first. Camera/scanner compatibility remains device/version dependent.

## 17. Final Git state

`git status --short` (exit 0):

```text
M app/src/debug/AndroidManifest.xml
 M tools/verify-apk.py
?? app/src/debug/java/org/totipo/android/debug/OtpAuthIntentProbeActivity.java
?? app/src/test/java/org/totipo/android/OtpAuthProbeSourceGuardTest.java
?? review/M2C_NATIVE_OTPAUTH_CAMERA_QUALIFICATION_REPORT.md
?? tools/device/
```

`git diff --stat` (exit 0):

```text
app/src/debug/AndroidManifest.xml | 15 +++++++++++
 tools/verify-apk.py               | 55 +++++++++++++++++++++++++++++++++++++++
 2 files changed, 70 insertions(+)
```

`git diff --check` (exit 0):

```text
(no output)
```

`git diff --cached --stat` (exit 0):

```text
(no output)
```

Tracked diff stat excludes the untracked Activity, source guard, device harness/build script and this report. The tools/device directory contains only the standalone framework instrumentation/observer and its build script.

All changes remain unstaged/uncommitted. Fixture constants and test APK/build/evidence outputs contain only the already-public fixture or sanitized structural facts. Local signing material remains ignored. No dependency/cache input was regenerated. No stage/commit/tag/release/push/CI or agent Nix command was performed.
