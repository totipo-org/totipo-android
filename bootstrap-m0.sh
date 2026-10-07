#!/bin/sh
# jailed-codex exposes /bin/sh and bash on PATH, but no /usr/bin/env.
if [ -z "${BASH_VERSION:-}" ]; then exec bash "$0" "$@"; fi
set -euo pipefail
cd "$(dirname "$0")"
mode="${1:-}"
if [[ $# -gt 1 || ( "$mode" != "" && "$mode" != --refresh-dependencies ) ]]; then
    echo "Usage: $0 [--refresh-dependencies]" >&2
    exit 1
fi
python3 tools/verify-wrapper.py
bash tools/verify-m0-toolchain.sh
flags=(--no-daemon --no-configuration-cache --dependency-verification=strict --warning-mode=all "-Pandroid.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/36.0.0/aapt2")
tasks=(check :app:assembleDebug :app:assembleRelease)
if [[ "$mode" == --refresh-dependencies ]]; then
    echo 'Intentionally refreshing dependency locks and SHA-256 verification metadata.'
    ./gradlew "${flags[@]}" buildEnvironment :app:dependencies --write-locks --write-verification-metadata sha256
    ./gradlew "${flags[@]}" "${tasks[@]}" --write-locks --write-verification-metadata sha256
    echo 'REVIEW REQUIRED: Gradle lockfiles and gradle/verification-metadata.xml.'
    git status --short
    git diff --stat -- '*lockfile' gradle/verification-metadata.xml
else
    test -s buildscript-gradle.lockfile
    test -s app/gradle.lockfile
    test -s gradle/verification-metadata.xml
fi
./gradlew "${flags[@]}" "${tasks[@]}"
