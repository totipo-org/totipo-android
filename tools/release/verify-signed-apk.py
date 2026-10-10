#!/usr/bin/env python3
"""Fail-closed single-signer verification plus existing release APK boundaries."""
import argparse
import os
from pathlib import Path
import re
import subprocess
from payload import require


def fingerprint(path):
    value = Path(path).read_text().strip()
    require(re.fullmatch(r'[0-9a-f]{64}', value) is not None,
            'Expected fingerprint must be 64 lowercase SHA-256 hex characters')
    return value


def verify(apk, expected, version):
    sdk = Path(os.environ['ANDROID_HOME']) / 'build-tools/36.0.0'
    result = subprocess.run([str(sdk / 'apksigner'), 'verify', '--verbose', '--print-certs',
                             str(apk)], text=True, capture_output=True, check=True)
    output = result.stdout
    for scheme, wanted in ((1, 'false'), (2, 'true'), (3, 'true'), (4, 'false')):
        require(re.search(rf'^Verified using v{scheme} scheme .*: {wanted}$', output, re.M),
                f'Unexpected v{scheme} signature scheme')
    require(re.search(r'^Number of signers: 1$', output, re.M), 'Expected exactly one signer')
    signers = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)$', output, re.M)
    require(signers == [fingerprint(expected)], 'Signing certificate mismatch')
    badging = subprocess.check_output([str(sdk / 'aapt2'), 'dump', 'badging', str(apk)], text=True)
    require(re.search(r"^package: name='org.totipo.android' versionCode='1' versionName='" +
                      re.escape(version) + "'", badging, re.M), 'Application/version mismatch')
    subprocess.run([str(sdk / 'zipalign'), '-c', '-P', '16', '4', str(apk)], check=True,
                   stdout=subprocess.DEVNULL)
    subprocess.run(['python3', '-B', str(Path(__file__).resolve().parents[1] / 'verify-apk.py'),
                    str(apk), '--no-debug-probe'], check=True)
    print(f'Verified one pinned signer, v2/v3, release boundaries: {apk}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--fingerprint', required=True, type=Path)
    parser.add_argument('--version', required=True)
    args = parser.parse_args()
    # Validate public input before invoking tools.
    fingerprint(args.fingerprint)
    verify(args.apk, args.fingerprint, args.version)
