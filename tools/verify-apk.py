#!/usr/bin/env python3
"""Check APK ZIP integrity, bootstrap/core/BC DEX presence and optional unsigned status."""
import argparse
import hashlib
from pathlib import Path
import struct
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('apk', type=Path)
parser.add_argument('--unsigned', action='store_true')
args = parser.parse_args()
data = args.apk.read_bytes()
with zipfile.ZipFile(args.apk) as apk:
    assert apk.testzip() is None, 'Corrupt APK entry'
    names = apk.namelist()
    assert len(names) == len(set(names)), 'Duplicate ZIP entry'
    assert 'AndroidManifest.xml' in names and 'resources.arsc' in names
    dex = b''.join(apk.read(name) for name in names if name.startswith('classes') and name.endswith('.dex'))
    assert dex.startswith(b'dex\n'), 'Missing DEX'
    for descriptor in [b'Lorg/totipo/android/MainActivity;', b'Lorg/totipo/VaultSession;', b'Lorg/bouncycastle/crypto/generators/Argon2BytesGenerator;']:
        assert descriptor in dex, f'Missing packaged class: {descriptor!r}'
    assert b'Lorg/totipo/storage/nio/' not in dex, 'Unexpected NIO provider'
    if args.unsigned:
        assert not any(name.upper().startswith('META-INF/') and name.upper().endswith(('.RSA', '.DSA', '.EC', '.SF')) for name in names), 'JAR signature present'
        # An APK v2/v3 signing block sits immediately before the ZIP central directory.
        eocd = data.rfind(b'PK\x05\x06')
        assert eocd >= 0
        central_offset = struct.unpack_from('<I', data, eocd + 16)[0]
        assert data[max(0, central_offset - 16):central_offset] != b'APK Sig Block 42', 'APK signing block present'
print(f'{args.apk}: valid APK; bootstrap/core/BC present; SHA-256 {hashlib.sha256(data).hexdigest()}')
