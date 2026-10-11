# Protected Android releases

Release 0.1.2 (versionCode 3) is the first successfully published release and remains
unchanged. Its permanent public signing identity is committed; see
[public identity and provisioning status](signing/README.md). No Android production
key is shared with Totipo Java.

Release source has two mutable authorities: `VERSION` supplies versionName and
`VERSION_CODE` supplies Android versionCode. Both are canonical files ending in one
LF; VERSION_CODE is a decimal integer in 1..2100000000, without spaces, signs or
leading zeros. Gradle fails on missing/malformed values without a default. Stable
reviewed build/dependency scripts remain fully supply-chain pinned; metadata edits
need no pin update. VERSION_CODE is included in the Nix Android source filter.

Use exactly two release-specific commands from the repository root, with the next
reviewed canonical stable version substituted for `<VERSION>`:

```sh
nix run path:.#prepare-release -- <VERSION>
```

Review the diff, commit normally, push main normally, and wait for ordinary CI as
desired. Then run:

```sh
nix run path:.#release-tag -- <VERSION>
```

`prepare-release` prints the resulting VERSION and VERSION_CODE and leaves their
exact diff unstaged/uncommitted. `release-tag` independently performs all readiness
checks, creates the unsigned annotated `v<VERSION>` tag if needed with exact message
`Totipo Android <VERSION>`, pushes only that tag without force/follow-tags, and
revalidates the remote object, peeled commit, source metadata and live main.
It prints version, versionCode, tag, the full 40-character `source_commit`, and
copy-ready GitHub Release workflow inputs (`version` and `source_commit`). Optional
`--json` prints deterministic version/version_code/tag/source_commit coordinates.

Manually run the normal protected Release workflow on main with those printed
inputs, then approve the `android-release` environment. These apps never commit,
push main, dispatch workflows, sign APKs or create GitHub Releases. Do not rerun
preparation for the published 0.1.2 release.

Every valid annotated Android release tag consumes its semantic version and code,
even if publication subsequently fails. Preparation alone consumes nothing: an
abandoned untagged preparation may be reset. The next code is the maximum consumed
code plus one, rejecting duplicates and overflow; it never comes from the working
file, a patch number, an operator code or successful GitHub releases alone.

History accepts only canonical numeric stable `vX.Y.Z` annotated tags peeling to
commits whose VERSION exactly matches. Any v-prefixed tag with unsupported syntax
fails rather than being skipped. VERSION_CODE is read for new-format tagged sources;
only legacy tagged sources use an unambiguous literal `versionCode = <integer>`
from app/build.gradle.kts. Current source always requires VERSION_CODE. Target
X.Y.Z must be strictly newer numerically than every consumed tag; prerelease,
build metadata and leading-zero forms are unsupported.

Each command reads origin with `git ls-remote` and compares release tag object IDs
and peels with local objects. No fetch or ref mutation is performed to inspect
history. Target remote tags are fetched only into a disposable isolated Git object
database for full validation, leaving worktree refs unchanged. Missing/stale/extra local release tags fail: synchronize and review the
repository's tags before retrying. Network/authentication errors fail closed. The
Nix wrappers include Python, Git, OpenSSH, JDK/keytool and CA certificates; normal
Git/SSH credentials and configuration still belong to the caller. No SDK is needed.
The executable comes from the Nix store but discovers the caller's Git worktree;
store worktrees are explicitly refused.

Prepare requires main and a clean tree/index, or the exact unstaged metadata diff
already expected for the same target. It validates history, source wiring, stable
supply-chain pins and the public signer before atomically replacing each changed
metadata file, checks the result, and leaves everything unstaged/uncommitted.
Running it again reports already prepared without incrementing the code. Any
unrelated dirty/untracked or staged path is rejected. File replacements are atomic
individually; interruption between replacements leaves a partial diff which must
be reviewed/reset before retrying.

`release-tag` requires clean committed main, both live remote main and local
origin/main equal to HEAD, matching source VERSION/code, coherent consumed history,
canonical Gradle wiring, application ID, stable supply-chain inventory, valid public
signer and the private-material guard. It rejects multiple/different push endpoints.
A correct local-only target tag resumes by pushing it; a correct local/remote pair
is a successful no-op with the same printed coordinates. Wrong/lightweight/signed/
misnamed/message-mismatched/source-mismatched tags fail without moving/deleting
anything. A remote-only target is rejected until the human synchronizes and reviews
its local object. No tag is automatically adopted or overwritten.

Checks precede mutation and remote state is rechecked immediately before and after
push. Git cannot transact main and a separate tag across client/network failures:
a push followed by a failed verification can leave the exact local/remote tag in
place. Resolve the reported condition and repeat; the command validates that state
without recreation. It never reports success on unverified remote state.

For diagnostic / developer use only, the internal read-only CLI
`python3 -B tools/release/release.py check-release <VERSION>` permits the exact
prepared diff or clean committed source. It is not a public Nix app or a required
release step.

Qualify the unsigned APK from the committed source. Its observed versionName and
versionCode remain authoritative in provenance through signing; changing source
metadata requires no signed-verifier version edit. The workflow never creates or
pushes tags.

Application ID `org.totipo.android` is independently pinned application identity.
Mutable versionName/versionCode are extracted with pinned aapt2 from the qualified
unsigned APK. Qualification checks versionName against VERSION once. Downstream
verification compares both APKs against validated provenance and rehashes the
unsigned input. The signed APK never supplies its own expected version.

Dispatch `.github/workflows/release.yml` on main with the printed `version` and `source_commit` (full reviewed
current main commit SHA). Every job revalidates source, VERSION, annotated local and
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

## HTTP interface references

The runner supplies GitHub CLI, outside Android secret scope. No third-party Python
dependency was added. [`gh api`](https://cli.github.com/manual/gh_api) documents
`--include`, `--paginate` and `--slurp`. GitHub documents
[release-by-tag](https://docs.github.com/en/rest/releases/releases#get-a-release-by-tag-name)
as a published-release lookup and
[list releases](https://docs.github.com/en/rest/releases/releases#list-releases)
as including drafts for callers with push authority. The implementation therefore
uses different endpoints for published absence and draft retrieval.

## Support-tooling policy

The CLI discovers the caller worktree once and passes an immutable
`source.Repository` to reusable source/history/readiness and publication functions.
Imports do not discover a repository. `source.py` owns canonical stable release
syntax, consumed-history policy and public identity; `release.py` owns command,
artifact/provenance and publication orchestration. Payload and signed-APK verifiers
remain separate boundaries. Dispatch and provenance use the same stable X.Y.Z
policy as preparation; APK metadata extraction observes the artifact.

Reviewed digests live in [`reviewed-inputs.sha256`](../tools/release/reviewed-inputs.sha256),
not Java source syntax. Each sorted line is a lowercase SHA-256, two spaces, an
allowlisted repository path and one LF. Both Java and Python independently reject
missing, duplicate, unknown, unsorted or malformed entries and compare file bytes.
The twelve reviewed values were copied unchanged; updating them requires review,
not regeneration to match an unreviewed checkout. The public signer fingerprint
likewise requires exactly 64 lowercase hex characters and LF.

`source.py` also owns the no-network reviewed action SHA/version/runtime table and
restricted plain-line `uses:` grammar. Readiness, tests and the workflow's
`action-policy` check share it. The workflow check runs after checkout and before
Nix installation; it requires the complete workflow declaration inventory. This
is an internal guard, not a third human release command. Unsupported declaration
syntax, unknown actions and runtime overrides fail closed. No YAML library or
live action metadata discovery is used.

## Local qualification

Use the pinned development environment. Fast standard-library tests:

```sh
python3 -B -m unittest discover -s tools/release -p 'test_*.py'
python3 -B tools/release/release.py guard
python3 -B tools/release/release.py action-policy
python3 -B tools/release/test-build-metadata.py
```

The existing Nix package install check also runs the fast Python tests using explicit
Git input. `checks.release-apps` executes every packaged app help entry point and
checks that incomplete disposable caller worktrees fail without metadata writes.
The standalone metadata harness uses one offline Gradle invocation in a temporary
project; it runs the exact parser text and checks valid, malformed and missing files. After the final
normal and strict Gradle gates and existing artifact verification, run:

```sh
bash tools/release/signing-smoke.sh app/build/outputs/apk/release/app-release-unsigned.apk
```

This uses temporary TEST-only RSA PKCS#12 keys outside source; it deletes both
keys before repository verifiers run and removes the whole temporary directory on
exit. It checks the intended signer/schemes, release boundaries, payload equality,
unsigned immutability and six negative cases. It never installs an APK or uses a
production identity. No device installation is needed for this tooling milestone.
