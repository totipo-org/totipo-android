#!/usr/bin/env python3
"""Fail-closed single-signer verification plus existing release APK boundaries."""
import argparse
import os
from pathlib import Path
import re
import subprocess
from payload import compare, require
from source import fingerprint
from release import load_provenance, bind_unsigned, require_metadata, unsigned_check



def verify(apk, expected, provenance, unsigned):
    p = load_provenance(provenance)
    require(p['certificate_sha256'] == fingerprint(expected), 'Provenance certificate mismatch')
    bind_unsigned(unsigned, p)
    unsigned_check(unsigned)
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
    require_metadata(apk, p)
    subprocess.run([str(sdk / 'zipalign'), '-c', '-P', '16', '4', str(apk)], check=True,
                   stdout=subprocess.DEVNULL)
    # Complete payload equality preserves the independently checked unsigned
    # manifest/resources/DEX policy; signed identity is also inspected directly.
    compare(unsigned, apk)
    print(f'Verified one pinned signer, v2/v3, release boundaries: {apk}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--fingerprint', required=True, type=Path)
    parser.add_argument('--provenance', required=True, type=Path)
    parser.add_argument('--unsigned', required=True, type=Path)
    args = parser.parse_args()
    # Validate public input before invoking tools.
    fingerprint(args.fingerprint)
    verify(args.apk, args.fingerprint, args.provenance, args.unsigned)
