#!/usr/bin/env bash
set -euo pipefail

: "${JAVA_HOME:?Enter the repository Nix development shell}"
: "${ANDROID_HOME:?Enter the repository Nix development shell}"
: "${ANDROID_SDK_ROOT:?Enter the repository Nix development shell}"
case "$JAVA_HOME" in /nix/store/*) ;; *) echo 'JAVA_HOME is not Nix-managed' >&2; exit 1 ;; esac
case "$ANDROID_HOME" in /nix/store/*/libexec/android-sdk) ;; *) echo 'ANDROID_HOME is not Nix-managed' >&2; exit 1 ;; esac
test "$ANDROID_HOME" = "$ANDROID_SDK_ROOT"
printf 'JAVA_HOME=%s\nANDROID_HOME=%s\nANDROID_SDK_ROOT=%s\n' "$JAVA_HOME" "$ANDROID_HOME" "$ANDROID_SDK_ROOT"
java -version
"$JAVA_HOME/bin/java" -version
java -XshowSettings:properties -version 2>&1 | python3 -c '
import sys
text = sys.stdin.read()
print(text)
assert "java.specification.version = 17" in text, "Active JDK must be 17"
'
gradle --version
python3 - <<'PY'
import os
from pathlib import Path
sdk = Path(os.environ['ANDROID_HOME'])
platforms = list((sdk / 'platforms').glob('*/source.properties'))
print('Platform properties:', [str(p) for p in platforms])
assert len(platforms) == 1, 'Expected exactly one Android platform'
properties = dict(line.split('=', 1) for line in platforms[0].read_text().splitlines() if '=' in line)
assert properties.get('AndroidVersion.ApiLevel', '').strip() in ('37', '37.0'), properties
assert (platforms[0].parent / 'android.jar').is_file()
for subdir, revision in [('build-tools/36.0.0', '36.0.0'), ('platform-tools', '37.0.1')]:
    path = sdk / subdir / 'source.properties'
    text = path.read_text()
    print(path, '\n' + text)
    props = dict(line.split('=', 1) for line in text.splitlines() if '=' in line)
    assert props['Pkg.Revision'].strip() == revision, props
assert (sdk / 'build-tools/36.0.0/aapt2').is_file()
assert (sdk / 'platform-tools/adb').is_file()
for forbidden in ['emulator', 'system-images', 'ndk', 'ndk-bundle', 'cmake']:
    assert not (sdk / forbidden).exists(), f'Unexpected SDK component: {forbidden}'
PY
"$ANDROID_HOME/platform-tools/adb" version
test ! -e local.properties
printf 'M0 toolchain checks passed.\n'
