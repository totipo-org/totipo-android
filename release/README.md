# Protected Android releases

The machinery is implemented but production signing is intentionally unprovisioned.
See [public identity and future provisioning](signing/README.md). No Android
production key is shared with Totipo Java.

Before cutting an Android release, intentionally update VERSION (which supplies
Android versionName) and increment versionCode in app/build.gradle.kts according
to Android update requirements. Review the source on main and prepare an annotated
`v<VERSION>` tag pointing to that exact source. Qualify the resulting unsigned APK.
Its observed versionName/versionCode then flow through provenance and signing;
changing either value requires no second verifier edit. VERSION must be a release
version, not a development version. The workflow never creates or pushes tags.

Application ID `org.totipo.android` is independently pinned application identity.
Mutable versionName/versionCode are extracted with pinned aapt2 from the qualified
unsigned APK. Qualification checks versionName against VERSION once. Downstream
verification compares both APKs against validated provenance and rehashes the
unsigned input. The signed APK never supplies its own expected version.

Dispatch `.github/workflows/release.yml` on main with VERSION and the full reviewed
current main commit SHA. Every job revalidates source, VERSION, annotated local and
remote tag, current remote main, clean checkout and committed public signing identity.
Main moving during approval fails closed. Release concurrency is serialized.

The four jobs are:

| Job | Authority and output |
| --- | --- |
| qualify | contents read, no environment or signing secrets; `nix flake check --print-build-logs path:.`; compare checks.android/default drvPath, materialize default, verify unsigned APK, upload unsigned bundle |
| sign | contents read, protected android-release; verify bundle and resolve pinned SDK/JDK before one secret-bearing shell step; output separate signed candidate |
| verify | contents read, no signing secrets; verify single pinned certificate and v2/v3 signatures, ordinary release boundaries, payload equivalence and original unsigned hash; output verified assets plus internal unsigned evidence |
| publish | contents write, no Android key; verify inputs, create draft for existing tag, upload, download all assets, compare bytes and reverify downloaded certificate/boundaries before final publication |

`checks.android` and `packages.default` must resolve to the same derivation.
Materialization is `nix build --no-link --print-out-paths`, after qualification,
not another qualification gate or a parallel Gradle build. The package output is
`share/totipo-android/totipo-android-<VERSION>-unsigned.apk`; debug APKs are not
installed into it. The original unsigned file is never signed in place.

The prepared bundle consists of `unsigned-release.apk`, its SHA-256 file, and
deterministic `release-provenance.json`. Provenance records source/tag/version,
application ID/versionName/versionCode, unsigned hash, Nix derivation/output, expected public
certificate digest, Java 0.2.0 and protocol v1/r19. Provenance remains byte-identical
from qualification through publication; the signed hash lives in the APK checksum file.
The internal verified bundle retains unsigned APK/checksum for publish revalidation;
only the three assets below are uploaded to the release.
The JSON schema requires exactly the existing fields, rejecting unknown/duplicate
keys, missing fields, invalid types/ranges, hashes, source/tag/version and Nix paths.
There is no schema-version field. Android versionCode is an integer in 1..2100000000.
The public certificate remains pinned in the exact checked-out source.

The stable published assets are `totipo-android-<VERSION>.apk`,
`totipo-android-<VERSION>.apk.sha256`, and `release-provenance.json`.
Release notes identify these bytes, source/tag, certificate, and Java/protocol.
There is no new changelog system. GitHub's runner-provided `gh` CLI/API performs
release mutation outside the signing job; no third-party signing/release action
is used. Checkout/Nix setup and first-party artifact actions use immutable SHAs.
Artifact transport is confined to this workflow run.

Secrets appear only in the signing step's environment. That shell resolves no
Nix paths and invokes no repository code: restrictive temp directory, base64
decode, trusted SDK apksigner, immediate removal with EXIT/INT/TERM cleanup.
Passwords use apksigner `env:` input, not literal command-line values. No secrets
or key bytes are placed in provenance, artifacts or Nix store inputs. Normal
shell cleanup cannot promise execution after SIGKILL/runner destruction; private
temp state stays on the ephemeral signing runner and is never uploaded.

Build Tools 36.0.0 supplies apksigner and zipalign from the existing locked SDK;
JDK 17 supplies keytool. Signing explicitly disables v1/v4 and enables v2/v3, with
`--alignment-preserved true` to retain the qualified ZIP local headers.
minSdk 26 needs no v1. Verification checks alignment without rewriting the APK.
The ZIP comparator compares every payload entry, order, central metadata, raw
local headers, compressed bytes and uncompressed SHA-256. It permits signing-block
insertion and changed central-directory offsets; it does not claim whole-file
identity. Downloaded APK whole-file SHA equality also preserves this payload proof.

A failed remote verification leaves a draft unpublished. An existing release or
draft fails closed; recovery is a human review task, not automatic overwrite.
The workflow requires environment protections to be configured by the future
human provisioning milestone; YAML cannot itself enforce required reviewers.

## Local qualification

Use the pinned development environment. Fast standard-library tests:

```sh
python3 -B -m unittest discover -s tools/release -p 'test_*.py'
python3 -B tools/release/release.py guard
```

The existing Nix package install check also runs the fast tests. After the final
normal and strict Gradle gates and existing artifact verification, run:

```sh
bash tools/release/signing-smoke.sh app/build/outputs/apk/release/app-release-unsigned.apk
```

This uses temporary TEST-only RSA PKCS#12 keys outside source; it deletes both
keys before repository verifiers run and removes the whole temporary directory on
exit. It checks the intended signer/schemes, release boundaries, payload equality,
unsigned immutability and six negative cases. It never installs an APK or uses a
production identity. No device installation is needed for this tooling milestone.
