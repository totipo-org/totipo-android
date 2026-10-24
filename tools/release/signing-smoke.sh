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
    release.discover().run('git', 'rev-parse', 'HEAD'), '0' * 64,
    '/nix/store/' + '0' * 32 + '-TEST-local.drv', '/nix/store/' + '0' * 32 + '-TEST-local')
Path(sys.argv[2]).write_text(json.dumps(p, sort_keys=True, indent=2) + '\n')
PYCODE
# Prepare a deliberate changed payload before any key exists.
python3 -B - "$unsigned" "$work/altered-unaligned.apk" <<'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as source, zipfile.ZipFile(sys.argv[2], 'w') as target:
    for entry in source.infolist():
        target.writestr(entry, source.read(entry))
    target.writestr('TEST-ONLY-altered-payload', b'negative test')
PY
# The negative must pass alignment to exercise payload rejection specifically.
"$sdk/zipalign" -P 16 4 "$work/altered-unaligned.apk" "$work/altered.apk"
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
for kind in first second altered v2-only v3-only; do
    key=first
    input="$unsigned"
    if [[ "$kind" == second ]]; then key=second; fi
    if [[ "$kind" == altered ]]; then input="$work/altered.apk"; fi
    v2=true
    v3=true
    if [[ "$kind" == v2-only ]]; then v3=false; fi
    if [[ "$kind" == v3-only ]]; then v2=false; fi
    "$sdk/apksigner" sign --ks "$work/$key.p12" --ks-type PKCS12 --ks-key-alias test \
        --ks-pass env:TEST_STORE_PASSWORD --key-pass env:TEST_STORE_PASSWORD \
        --alignment-preserved true --v1-signing-enabled false --v2-signing-enabled "$v2" --v3-signing-enabled "$v3" \
        --v4-signing-enabled false --out "$work/$kind-signed.apk" "$input"
done
rm -f "$work/first.p12" "$work/second.p12"
unset TEST_STORE_PASSWORD
# No private key remains when repository verifiers resume.
test ! -e "$work/first.p12" && test ! -e "$work/second.p12"
# Mutate only a cryptographic signature value in each scheme's signing block.
# This is fixture corruption, not a verifier: the SDK decides signature validity.
python3 -B - "$work" <<'PY'
from pathlib import Path
import struct, sys
work = Path(sys.argv[1])
original = (work / 'first-signed.apk').read_bytes()
eocd = original.rfind(b'PK\x05\x06')
central = struct.unpack_from('<I', original, eocd + 16)[0]
assert original[central - 16:central] == b'APK Sig Block 42'
size = struct.unpack_from('<Q', original, central - 24)[0]
start = central - size - 8
assert struct.unpack_from('<Q', original, start)[0] == size

def lp(data, offset):
    size = struct.unpack_from('<I', data, offset)[0]
    return offset + 4, offset + 4 + size

found = set()
offset = start + 8
while offset < central - 24:
    size, ident = struct.unpack_from('<QI', original, offset)
    if ident in (0x7109871a, 0xf05368c0):
        scheme = 2 if ident == 0x7109871a else 3
        signers, _ = lp(original, offset + 12)
        signer, _ = lp(original, signers)
        _, after_data = lp(original, signer)
        signatures, _ = lp(original, after_data + (8 if scheme == 3 else 0))
        signature_record, _ = lp(original, signatures)
        signature, end = lp(original, signature_record + 4)  # skip algorithm ID
        assert end > signature
        changed = bytearray(original)
        changed[signature] ^= 1
        (work / f'invalid-v{scheme}.apk').write_bytes(changed)
        found.add(scheme)
    offset += 8 + size
assert found == {2, 3}
PY
verify=(python3 -B tools/release/verify-signed-apk.py --provenance "$work/release-provenance.json" --unsigned "$unsigned")
# Record the complete scheme/certificate output, including the skipped-v2 result.
"$sdk/apksigner" verify --verbose --print-certs "$work/first-signed.apk"
"$sdk/apksigner" verify --verbose --print-certs --min-sdk-version 24 "$work/first-signed.apk"
"${verify[@]}" "$work/first-signed.apk" --fingerprint "$work/expected.sha256"
sha256sum "$work/first-signed.apk"
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
for kind in v2-only v3-only; do
    # Both are platform-valid at minSdk 30, but violate the two-scheme policy.
    "$sdk/apksigner" verify "$work/$kind-signed.apk"
    expect_failure "${verify[@]}" "$work/$kind-signed.apk" --fingerprint "$work/expected.sha256"
done
# A corrupted v2 can hide behind a valid v3 in the default range, and vice versa
# in the v2-only range. Each independent SDK check must still reject corruption.
"$sdk/apksigner" verify "$work/invalid-v2.apk"
"$sdk/apksigner" verify --min-sdk-version 24 --max-sdk-version 27 "$work/invalid-v3.apk"
expect_failure "$sdk/apksigner" verify --min-sdk-version 24 --max-sdk-version 27 "$work/invalid-v2.apk"
cat "$work/negative.log"
grep -q 'APK Signature Scheme v2 signer #1: .* signature over signed-data did not verify' "$work/negative.log"
expect_failure "$sdk/apksigner" verify "$work/invalid-v3.apk"
cat "$work/negative.log"
grep -q 'APK Signature Scheme v3 signer #1: .* signature over signed-data did not verify' "$work/negative.log"
for scheme in 2 3; do
    expect_failure "${verify[@]}" "$work/invalid-v$scheme.apk" --fingerprint "$work/expected.sha256"
done
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
expect_failure "${verify[@]}" "$work/altered-signed.apk" --fingerprint "$work/expected.sha256"
grep -q 'APK ZIP payload differs' "$work/negative.log"
expect_failure "${verify[@]}" "$work/first-signed.apk" --fingerprint "$work/malformed.sha256"
test "$before" = "$(sha256sum "$unsigned")"
test "$provenance_before" = "$(sha256sum "$work/release-provenance.json")"
echo 'PASS: disposable signing, ten verifier negatives plus two SDK corruption checks, original unsigned SHA unchanged; temporary keys erased'
