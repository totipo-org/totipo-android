#!/usr/bin/env python3
"""Check APK ZIP integrity, bootstrap/NIO/core/BC DEX presence and optional unsigned status."""
import argparse
import hashlib
from pathlib import Path
import struct
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('apk', type=Path)
parser.add_argument('--unsigned', action='store_true')
parser.add_argument('--no-debug-probe', action='store_true', help='Require release DEX to exclude diagnostic classes')
parser.add_argument('--debug-probe', action='store_true', help='Require the debug qualification Activity')
args = parser.parse_args()
assert not (args.debug_probe and (args.unsigned or args.no_debug_probe)), 'Conflicting APK boundaries'
data = args.apk.read_bytes()
with zipfile.ZipFile(args.apk) as apk:
    assert apk.testzip() is None, 'Corrupt APK entry'
    names = apk.namelist()
    assert len(names) == len(set(names)), 'Duplicate ZIP entry'
    assert 'AndroidManifest.xml' in names and 'resources.arsc' in names
    dex = b''.join(apk.read(name) for name in names if name.startswith('classes') and name.endswith('.dex'))
    assert dex.startswith(b'dex\n'), 'Missing DEX'
    for descriptor in [b'Lorg/totipo/android/MainActivity;', b'Lorg/totipo/VaultSession;', b'Lorg/totipo/ObjectCandidateValidation$Valid;', b'Lorg/totipo/ObjectCandidateValidation$Invalid;', b'Lorg/totipo/android/provider/ProviderTreeReader;', b'Lorg/totipo/android/provider/ImmutableCandidateClassifier;', b'Lorg/totipo/android/reconcile/ImmutableCandidateImporter;', b'Lorg/totipo/android/reconcile/ForegroundVaultCoordinator;', b'Lorg/totipo/storage/nio/NioTotipoStore;', b'Lorg/bouncycastle/crypto/generators/Argon2BytesGenerator;']:
        assert descriptor in dex, f'Missing packaged class: {descriptor!r}'
    if args.debug_probe:
        for descriptor in [b'Lorg/totipo/android/debug/LocalNioProbeActivity;']:
            assert descriptor in dex, f'Missing debug qualification class: {descriptor!r}'
    if args.no_debug_probe:
        assert b'Lorg/totipo/android/debug/' not in dex, 'Debug diagnostic machinery in release DEX'
    assert b'Lorg/totipo/android/reconcile/ForegroundVaultCoordinatorTest' not in dex, 'Foreground tests/fault fixtures in APK'
    assert b'Lorg/totipo/android/reconcile/ImmutableCandidateImporterTest' not in dex and b'Lorg/totipo/android/TestReplicaOwners;' not in dex, 'Import tests/fixtures in APK'
    assert b'Lorg/junit/' not in dex and b'Lorg/totipo/android/CoreDependencySmokeTest;' not in dex and b'Lorg/totipo/android/LocalReplicaOwnerTest;' not in dex and b'Lorg/totipo/android/provider/ImmutableCandidateClassifierTest' not in dex and b'Lorg/totipo/android/provider/ProviderSnapshotTest;' not in dex, 'Test classes in APK'
    if args.unsigned:
        assert not any(name.upper().startswith('META-INF/') and name.upper().endswith(('.RSA', '.DSA', '.EC', '.SF')) for name in names), 'JAR signature present'
        # An APK v2/v3 signing block sits immediately before the ZIP central directory.
        eocd = data.rfind(b'PK\x05\x06')
        assert eocd >= 0
        central_offset = struct.unpack_from('<I', data, eocd + 16)[0]
        assert data[max(0, central_offset - 16):central_offset] != b'APK Sig Block 42', 'APK signing block present'
print(f'{args.apk}: valid APK; bootstrap/NIO/core/BC present; SHA-256 {hashlib.sha256(data).hexdigest()}')
