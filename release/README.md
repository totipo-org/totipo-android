# Protected Android releases

The permanent public signing identity is installed for review and commit.
See [public identity and provisioning status](signing/README.md). Private-key,
recovery and protected-environment setup are human-reported only; no production
signing or release has occurred in this milestone. No Android production key is
shared with Totipo Java.

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
Normal-release artifact transport is confined to that workflow run.

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

A failed remote verification leaves a draft unpublished. Any existing release or
draft for the target tag fails closed; neither path adopts, overwrites or deletes it.
The initial published-release lookup uses documented `gh api --include` HTTP status
and headers, not human stderr. Only a well-formed HTTP 404 permits proceeding;
401/403/429/5xx, malformed JSON/HTTP and process/transport failures remain fatal.
A separate fully paginated release list also rejects drafts. After creating the
draft, publication reads that draft from the list, never from the published-only
tag endpoint. Hosted bytes and signature/boundaries must pass before publication.
The workflow requires human-configured environment protections. Their setup is
human-reported only; YAML cannot itself enforce required reviewers.

## Recovery after a verified publish failure

Normal release uses reviewed current main/source/tag -> protected sign -> verify
-> publish. It still requires dispatch SHA = checkout = remote main = annotated
tag target = release source. Never rerun signing merely to repair publication.

If a normal release fails after verify and before successful publication, retain
its Actions run ID. A later reviewed tooling revision can publish the exact signed
candidate without rebuilding, re-signing, changing VERSION/versionCode, changing
the tag, or changing the signing identity. Recovery never changes the release
artifact or signing identity. It is a deliberate `workflow_dispatch`; no failed
run automatically triggers publication.

After reviewing, committing, pushing and qualifying the tooling, dispatch
`.github/workflows/recover-release.yml` **from current main** with:

| Input | Meaning |
| --- | --- |
| `version` | Exact old release VERSION, with existing annotated `v<VERSION>` tag |
| `source_commit` | Full old tagged release source SHA |
| `failed_run_id` | Original failed normal protected-release run ID |

Recovery tooling source is current committed main. Release artifact source is the
old exact tagged source. They are checked separately; current main need not equal
release source. Main drift, dirty checkout, tag object/peel drift, target VERSION
disagreement, or any difference between target/current public certificate and
fingerprint files fails closed. Current certificate is parsed with pinned JDK and
checked against its DER fingerprint. No tag is created, pushed, moved or deleted.

`recover-verify` has only contents read and actions read. It checks same repository
and head repository, workflow ID/file `.github/workflows/release.yml`, manual event,
main head branch, exact head SHA, completed failure, and successful qualify/sign/
verify jobs followed by failed publish. This initial implementation accepts only
`run_attempt == 1`; rerun-attempt selection requires a separate design. GitHub's
workflow-run REST response exposes these identities, but **does not expose original
workflow_dispatch input values**. Tag/source/VERSION validation plus provenance
bind the requested version/source instead; no input correlation is invented.

The job selects exactly one unexpired `verified-release` artifact belonging to
that run/repository/head SHA. Authenticated GitHub CLI artifact retrieval uses
only actions read; arbitrary URLs/names alone are insufficient. The downloaded
archive must match GitHub's recorded SHA-256 digest and contain exactly the signed
APK/checksum, original provenance, and original unsigned APK/checksum. Missing,
ambiguous, expired, unavailable, unexpected-path, duplicate, symlink, oversized or
corrupt archives fail closed.

Current verifiers independently check the exact existing provenance schema (no
schema-version field was present), version/source/tag/app identity, versionName/
versionCode, production fingerprint, unsigned hash/checksum/metadata/boundaries,
signed hash/checksum/metadata, one production signer, v1/v4 disabled and v2/v3
enabled, alignment, release boundaries, and signed-vs-unsigned ZIP payload equality.
Provenance remains byte-identical. Actual APK hashes are logged as evidence, never
hard-coded into recovery tooling.

Only this reverified bundle plus a recovery receipt identifying tooling/source/tag
object/run/artifact is uploaded as **same-run `recovery-verified`**.
`recover-publish` has contents write, no cross-run acquisition and no actions-read
grant. It downloads only that same-run artifact, validates the receipt and current
tooling/target again, reruns bundle/APK verification, then calls the same normal
publication function: reject existing release/draft -> create draft/upload three
public assets -> redownload -> compare bytes -> reverify signer/boundaries ->
revalidate source/tag -> publish. Neither recovery job has an Android signing
environment, signing secret, signing/key-generation command or APK build step.

Recovery remains possible only while the original Actions artifact is retained
(normal retention is seven days), all provenance/source/tag/signer checks pass,
and no release or draft exists. If a prior failure already left a draft, **stop**:
this workflow deliberately does not resume or delete it. A published-tag 404 alone
does not prove draft absence; the authenticated list is mandatory. The 0.1.0
traceback actually failed at the tag lookup *after* draft creation returned success,
so operators must check draft state even when the public Releases page is empty.

## HTTP interface references

The runner supplies GitHub CLI, outside Android secret scope. No third-party Python
dependency was added. [`gh api`](https://cli.github.com/manual/gh_api) documents
`--include`, `--paginate` and `--slurp`. GitHub documents
[release-by-tag](https://docs.github.com/en/rest/releases/releases#get-a-release-by-tag-name)
as a published-release lookup and
[list releases](https://docs.github.com/en/rest/releases/releases#list-releases)
as including drafts for callers with push authority. The implementation therefore
uses different endpoints for published absence and draft retrieval.

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
