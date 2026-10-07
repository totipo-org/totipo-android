# Post-M0 CI simplification

## Starting state

- Branch: `main`.
- HEAD: `fc0027e83c139b846d7346d4e1ca57398f4dbac8`.
- `git status --short`: empty; clean before edits.
- Push/PR CI entered the default shell four times, ran the normal Gradle
  build, repeated it offline with task/build caches disabled, then ran
  `nix flake check`, `nix build` and `nix build --rebuild`.

## Problem

Default `nix develop` includes jailed-codex and agent tooling. Ordinary Android
CI was realizing an unnecessarily large environment and repeating M0
qualification work on every push/PR.

## Changes

- Added `devShells.ci`, sharing packages and environment with the default shell:
  JDK 17, composed Android SDK, Python and Nix Gradle. Gradle is retained because
  `tools/verify-m0-toolchain.sh` calls `gradle --version`; builds use `./gradlew`.
- Shared `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT` and AAPT2 `GRADLE_OPTS`.
  SDK composition is unchanged. Package-only writable `ANDROID_USER_HOME`
  handling in `package.nix` is unchanged.
- Only the default shell extends these packages with `makeJailedCodex`.
  Its jailed configuration, extra packages and forwarded environment remain
  unchanged. The CI shell has no jailed-agents package output or LLM executables
  in its package list; closure validation remains pending human evidence.
- Ordinary push/PR builds use one `nix develop path:.#ci` invocation for
  bootstrap and both APK inspections, followed by the dirty-tree check.
- A separate job runs `nix flake check path:.` and `nix build path:.` only on
  main pushes or manual workflow dispatch. No scheduled jobs or publication.
- Removed per-commit forced offline Gradle build and forced Nix rebuild.
  Manual qualification commands remain documented. Historical M0 evidence
  has not been rewritten.

## Preserved guarantees

Ordinary CI still invokes the unchanged bootstrap: wrapper checksums/pins,
Nix JDK/SDK checks, existing locks/metadata, strict dependency verification,
root build-classpath verification, Maven/Totipo boundary checks, unit tests,
lint, and debug/release assembly. Both APK content checks remain, including
unsigned release verification. The final `git diff --check` and full tracked/
untracked clean-tree assertion protect reproducibility inputs.

Actions retain their commit SHA pins, checkout disables persisted credentials,
and contents permission is read-only. No dependency refresh, new cache,
toolchain version change or package frozen-cache change was introduced.

## Validation

### Agent non-Nix validation

- `git branch --show-current`, `git rev-parse HEAD`, `git status --short`:
  starting state recorded above.
- `python3 tools/verify-wrapper.py`: PASS; Gradle 9.8.0 wrapper JAR and
  distribution pins verified.
- `bash -n bootstrap-m0.sh tools/verify-m0-toolchain.sh`: PASS.
- Python `subprocess.run(['bash', '-n'], ...)` on every workflow `run: |`
  block: PASS. Focused Python assertions confirmed one `#ci` invocation,
  no offline/rebuild flags, and credentials/dirty-tree checks in both jobs.
- Complete flake/workflow/README diff inspected. `actionlint` and PyYAML are
  unavailable; no full YAML parser or GitHub Actions execution is claimed.
- Python SHA-256 snapshot/comparison against
  `/tmp/totipo-post-m0-trusted-inputs.json`: PASS for trusted inputs below.
- `git diff --check`: PASS; no output. No Nix commands or host JDK/SDK builds
  were run by the agent.

### Human Nix validation

Pending. No commands/results have yet been reported for this cleanup.
Requested checks:

```sh
nix develop path:.#ci --command bash -euc '
  ./bootstrap-m0.sh
  python3 tools/verify-apk.py app/build/outputs/apk/debug/app-debug.apk
  python3 tools/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk --unsigned
'
nix develop path:. --command bash -euc 'command -v jailed-codex'
```

The first exercises the exact normal CI build/APK path. The second proves the
default shell exposes jailed-codex without starting an interactive agent.
Post-human-validation diff/hash checks remain pending.

### Remote CI

not yet run

## Trusted-input changes

| Input | Changed? |
| --- | --- |
| `flake.lock` | No; SHA-256 unchanged |
| `package-deps.json` | No; SHA-256 unchanged |
| Gradle locks | No; SHA-256 unchanged |
| `gradle/verification-metadata.xml` | No; SHA-256 unchanged |
| Wrapper pins, JAR and scripts | No; SHA-256 unchanged |

## Final Git state

Checkpoint before human Nix validation; completion is pending.

`git status --short`:

```text
 M .github/workflows/ci.yml
 M README.md
 M flake.nix
?? review/POST_M0_CI_SIMPLIFICATION_REPORT.md
```

`git diff --stat` (tracked files; excludes this untracked report):

```text
 .github/workflows/ci.yml | 33 ++++++++++++++++++++++++---------
 README.md                | 19 +++++++++++++++----
 flake.nix                | 33 +++++++++++++++------------------
 3 files changed, 54 insertions(+), 31 deletions(-)
```

`git diff --check`: PASS, no output. `git diff --cached --stat`: empty.
Nothing staged or committed; no push, tag or release. Branch and HEAD unchanged.
