#!/usr/bin/env bash
# Disposable local qualification only. Never install these APKs.
set -euo pipefail
umask 077
export PATH="$JAVA_HOME/bin:$PATH"
unset JAVA_TOOL_OPTIONS _JAVA_OPTIONS JDK_JAVA_OPTIONS
unsigned=$(realpath "${1:?strict unsigned release APK required}")
sdk="$ANDROID_HOME/build-tools/36.0.0"
work=$(mktemp -d "${TMPDIR:-/tmp}/totipo-TEST-signing.XXXXXXXX")
trap 'rm -rf "$work"' EXIT
before=$(sha256sum "$unsigned")
# No-secret qualification fixture uses the same extractor/schema as production.
# Nix paths/source here describe a disposable local fixture, not a release claim.
python3 -B - "$unsigned" "$work/release-provenance.json" <<'PYCODE'
import json, sys
from pathlib import Path
sys.path.insert(0, 'tools/release')
import release
p = release.qualified_provenance(Path(sys.argv[1]), Path('VERSION').read_text().strip(),
    release.run('git', 'rev-parse', 'HEAD'), '0' * 64,
    '/nix/store/' + '0' * 32 + '-TEST-local.drv', '/nix/store/' + '0' * 32 + '-TEST-local')
Path(sys.argv[2]).write_text(json.dumps(p, sort_keys=True, indent=2) + '\n')
PYCODE
# Prepare a deliberate changed payload before any key exists.
python3 -B - "$unsigned" "$work/altered.apk" <<'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as source, zipfile.ZipFile(sys.argv[2], 'w') as target:
    for entry in source.infolist():
        target.writestr(entry, source.read(entry))
    target.writestr('TEST-ONLY-altered-payload', b'negative test')
PY
# Only trusted platform tools run from key generation through deletion.
export TEST_STORE_PASSWORD=disposable-local-test-only
for name in first second; do
    "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$work/$name.p12" -storetype PKCS12 \
        -storepass:env TEST_STORE_PASSWORD -keypass:env TEST_STORE_PASSWORD -alias test \
        -keyalg RSA -keysize 2048 -validity 1 -dname 'CN=TEST ONLY Disposable Totipo Qualification' >/dev/null 2>&1
    "$JAVA_HOME/bin/keytool" -exportcert -keystore "$work/$name.p12" \
        -storepass:env TEST_STORE_PASSWORD -alias test -file "$work/$name.der" >/dev/null 2>&1
done
sha256sum "$work/first.der" | cut -d ' ' -f 1 > "$work/expected.sha256"
# Bind the disposable public certificate before signing; version expectations stay
# exactly as qualified above. Only shell/core/platform tools run while keys exist.
fixture_fp=$(cat "$work/expected.sha256")
sed -i "s/\"certificate_sha256\": \"0*\"/\"certificate_sha256\": \"$fixture_fp\"/" "$work/release-provenance.json"
provenance_before=$(sha256sum "$work/release-provenance.json")
for kind in first second altered; do
    key=first
    input="$unsigned"
    if [[ "$kind" == second ]]; then key=second; fi
    if [[ "$kind" == altered ]]; then input="$work/altered.apk"; fi
    "$sdk/apksigner" sign --ks "$work/$key.p12" --ks-type PKCS12 --ks-key-alias test \
        --ks-pass env:TEST_STORE_PASSWORD --key-pass env:TEST_STORE_PASSWORD \
        --alignment-preserved true --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true \
        --v4-signing-enabled false --out "$work/$kind-signed.apk" "$input"
done
rm -f "$work/first.p12" "$work/second.p12"
unset TEST_STORE_PASSWORD
# No private key remains when repository verifiers resume.
test ! -e "$work/first.p12" && test ! -e "$work/second.p12"
verify=(python3 -B tools/release/verify-signed-apk.py --provenance "$work/release-provenance.json" --unsigned "$unsigned")
"${verify[@]}" "$work/first-signed.apk" --fingerprint "$work/expected.sha256"
python3 -B tools/release/payload.py "$unsigned" "$work/first-signed.apk"
expect_failure() {
    if "$@" > "$work/negative.log" 2>&1; then
        echo 'Negative case unexpectedly passed' >&2; exit 1
    fi
    echo "Expected rejection: $*"
}
printf '%064d\n' 0 > "$work/wrong.sha256"
printf 'malformed\n' > "$work/malformed.sha256"
expect_failure "${verify[@]}" "$work/first-signed.apk" --fingerprint "$work/wrong.sha256"
expect_failure "${verify[@]}" "$work/second-signed.apk" --fingerprint "$work/expected.sha256"
expect_failure "${verify[@]}" "$unsigned" --fingerprint "$work/expected.sha256"
python3 -B - "$work/first-signed.apk" "$work/tampered.apk" <<'PY'
import sys, zipfile
from pathlib import Path
p = Path(sys.argv[1]); data = bytearray(p.read_bytes())
with zipfile.ZipFile(p) as z:
    e = z.getinfo('classes.dex')
    offset = e.header_offset + 30 + len(e.filename.encode()) + len(e.extra) + 10
    data[offset] ^= 1
Path(sys.argv[2]).write_bytes(data)
PY
expect_failure "${verify[@]}" "$work/tampered.apk" --fingerprint "$work/expected.sha256"
# Valid signature under the right key still cannot authorize changed payload.
"$sdk/apksigner" verify "$work/altered-signed.apk"
expect_failure python3 -B tools/release/payload.py "$unsigned" "$work/altered-signed.apk"
expect_failure "${verify[@]}" "$work/first-signed.apk" --fingerprint "$work/malformed.sha256"
test "$before" = "$(sha256sum "$unsigned")"
test "$provenance_before" = "$(sha256sum "$work/release-provenance.json")"
echo 'PASS: disposable signing, six negative cases, original unsigned SHA unchanged; temporary keys erased'
