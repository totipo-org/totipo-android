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


def verify_signatures(apk, expected, sdk):
    # Keep the manifest-derived supported range: a diagnostic lower-API check
    # alone is not acceptance on the platforms where the app will be installed.
    # Since API 28, a valid v3 signature can cause apksigner to skip v2. Force
    # a separate v2-only platform range, and pin the signer in BOTH results.
    for label, options, required_scheme in (
        ('supported API range', [], 3),
        ('v2 API range', ['--min-sdk-version', '24', '--max-sdk-version', '27'], 2),
    ):
        result = subprocess.run([str(sdk / 'apksigner'), 'verify', '--verbose', '--print-certs',
                                 *options, str(apk)], text=True, capture_output=True, check=True)
        output = result.stdout
        for scheme in (1, 2, 3, 4):
            values = re.findall(rf'^Verified using v{scheme} scheme .*: (true|false)$', output, re.M)
            require(len(values) == 1, f'Missing/ambiguous v{scheme} verification result ({label})')
            if scheme in (1, 4) or scheme == required_scheme:
                wanted = 'true' if scheme == required_scheme else 'false'
                require(values == [wanted], f'Unexpected v{scheme} signature scheme ({label})')
        require(re.findall(r'^Number of signers: (\d+)$', output, re.M) == ['1'],
                f'Expected exactly one signer ({label})')
        signers = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)$', output, re.M)
        require(signers == [expected], f'Signing certificate mismatch ({label})')


def verify(apk, expected, provenance, unsigned):
    p = load_provenance(provenance)
    require(p['certificate_sha256'] == fingerprint(expected), 'Provenance certificate mismatch')
    bind_unsigned(unsigned, p)
    unsigned_check(unsigned)
    sdk = Path(os.environ['ANDROID_HOME']) / 'build-tools/36.0.0'
    verify_signatures(apk, fingerprint(expected), sdk)
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
