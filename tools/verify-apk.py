#!/usr/bin/env python3
"""Check APK ZIP integrity, bootstrap/NIO/core/BC DEX presence and optional unsigned status."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import struct
import subprocess
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('apk', type=Path)
parser.add_argument('--unsigned', action='store_true')
parser.add_argument('--no-debug-probe', action='store_true', help='Require release DEX to exclude diagnostic classes')
parser.add_argument('--debug-probe', action='store_true', help='Require the debug qualification Activity')
args = parser.parse_args()
assert not (args.debug_probe and (args.unsigned or args.no_debug_probe)), 'Conflicting APK boundaries'
data = args.apk.read_bytes()
# Inspect the packaged (merged binary) manifest using the project's pinned SDK tool.
sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
assert sdk, 'ANDROID_HOME or ANDROID_SDK_ROOT required for packaged manifest inspection'
manifest = subprocess.check_output([
    str(Path(sdk) / 'build-tools/36.0.0/aapt2'), 'dump', 'xmltree',
    str(args.apk), '--file', 'AndroidManifest.xml'], text=True)
manifest = manifest.replace('http://schemas.android.com/apk/res/android:', 'android:')
for permission in ('android.permission.CAMERA', 'android.permission.INTERNET'):
    assert permission not in manifest, f'Forbidden APK permission: {permission}'
probe_name = 'org.totipo.android.debug.OtpAuthIntentProbeActivity'
if args.debug_probe:
    # xmltree nests attributes and filters beneath their owning Activity.
    activities = []
    lines = manifest.splitlines()
    for index, line in enumerate(lines):
        match = re.match(r'^( +)E: activity ', line)
        if match:
            depth = len(match[1])
            end = index + 1
            while end < len(lines):
                element = re.match(r'^( *)E:', lines[end])
                if element and len(element[1]) <= depth:
                    break
                end += 1
            activities.append('\n'.join(lines[index:end]))
    probe = [activity for activity in activities if probe_name in activity]
    assert len(probe) == 1, 'Missing/duplicate debug otpauth probe Activity'
    probe = probe[0]
    assert re.search(r'android:exported\([^)]*\)=true', probe), 'Probe must be exported'
    assert probe.count('E: intent-filter ') == 2, 'Unexpected probe intent filters'
    filters = re.split(r'^ +E: intent-filter .*$', probe, flags=re.MULTILINE)[1:]
    assert len(filters) == 2
    view, send = filters
    for value in ('android.intent.action.VIEW', 'android.intent.category.DEFAULT',
                  'android.intent.category.BROWSABLE'):
        assert f'="{value}"' in view, f'Missing debug VIEW boundary: {value}'
    assert view.count('E: action ') == 1 and view.count('E: category ') == 2 and view.count('E: data ') == 1
    for attribute, value in (('scheme', 'otpauth'), ('host', 'totp')):
        assert re.search(r'android:' + attribute + r'\([^)]*\)="' + value + '"', view), f'Wrong VIEW {attribute}'
    assert send.count('E: action ') == 1 and send.count('E: category ') == 1 and send.count('E: data ') == 1
    for value in ('android.intent.action.SEND', 'android.intent.category.DEFAULT'):
        assert f'="{value}"' in send, f'Missing debug SEND boundary: {value}'
    assert re.search(r'android:mimeType\([^)]*\)="text/plain"', send), 'Wrong SEND MIME'
    assert 'android:scheme(' not in send and 'android:host(' not in send
    assert 'android:mimeType(' not in view
    assert 'android.intent.action.SEND_MULTIPLE' not in probe and '*/*' not in probe
if args.no_debug_probe:
    assert probe_name not in manifest and 'otpauth' not in manifest, 'Debug otpauth surface in release manifest'
    assert 'android.intent.action.SEND' not in manifest, 'SEND enrollment surface in release manifest'
with zipfile.ZipFile(args.apk) as apk:
    assert apk.testzip() is None, 'Corrupt APK entry'
    names = apk.namelist()
    assert len(names) == len(set(names)), 'Duplicate ZIP entry'
    assert 'AndroidManifest.xml' in names and 'resources.arsc' in names
    dex = b''.join(apk.read(name) for name in names if name.startswith('classes') and name.endswith('.dex'))
    assert dex.startswith(b'dex\n'), 'Missing DEX'
    for descriptor in [b'Lorg/totipo/android/MainActivity;', b'Lorg/totipo/android/AddTokenRequest;', b'Lorg/totipo/android/AddTokenOutcome;', b'Lorg/totipo/android/Base32;', b'Lorg/totipo/android/TokenListAdapter;', b'Lorg/totipo/android/RevealedTotp;', b'Lorg/totipo/android/TotpPresentation;', b'Lorg/totipo/android/PlatformCodeClipboard;', b'Lorg/totipo/android/TotipoApplication;', b'Lorg/totipo/android/AndroidVaultController;', b'Lorg/totipo/VaultSession;', b'Lorg/totipo/ObjectCandidateValidation$Valid;', b'Lorg/totipo/ObjectCandidateValidation$Invalid;', b'Lorg/totipo/android/provider/ProviderTreeReader;', b'Lorg/totipo/android/provider/ImmutableCandidateClassifier;', b'Lorg/totipo/android/reconcile/ImmutableCandidateImporter;', b'Lorg/totipo/android/reconcile/ForegroundVaultCoordinator;', b'Lorg/totipo/android/reconcile/CoordinatedPrivateStore;', b'Lorg/totipo/storage/nio/NioTotipoStore;', b'Lorg/bouncycastle/crypto/generators/Argon2BytesGenerator;']:
        assert descriptor in dex, f'Missing packaged class: {descriptor!r}'
    if args.debug_probe:
        assert b'Lorg/totipo/android/debug/OtpAuthIntentProbeActivity;' in dex, 'Missing debug otpauth probe class'
        for descriptor in [b'Lorg/totipo/android/debug/LocalNioProbeActivity;', b'Lorg/totipo/android/reconcile/DebugVaultTiming;', b'Lorg/totipo/android/debug/AuthPerfActivity;', b'Lorg/totipo/android/debug/AuthPerfBenchmark;', b'Lorg/totipo/android/DebugDisposableCreation;', b'Lorg/totipo/android/DebugTotpFixtureActivity;', b'Lorg/totipo/android/reconcile/DebugTotpFixture;']:
            assert descriptor in dex, f'Missing debug qualification class: {descriptor!r}'
    if args.no_debug_probe:
        assert b'OtpAuthIntentProbe' not in dex and b'otpauth TOTP link received' not in dex and b'otpauth TOTP share received' not in dex, 'Otpauth probe in release DEX'
        for forbidden in [b'DebugTotpFixture', b'12345678901234567890', b'Public test fixture',
                          b'TotpPresentationTest', b'TotpControllerTest', b'TotpCoordinatorTest',
                          b'FakeClock', b'FakeClipboard', b'Base32Test', b'EnrollmentSourceGuardTest', b'GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ']:
            assert forbidden not in dex, f'M2A fixture/test seam in release DEX: {forbidden!r}'
        assert b'EXPECTED_SYNTHETIC_SHA256' not in dex and b'matchesSyntheticOutput' not in dex and b'output_match=1' not in dex and b'30eb8bf0a90f2cd624a1d00aa7093e2c8f11968586718195043150ca6ce50bb1' not in dex, 'M1L equivalence diagnostics in release DEX'
        assert b'TotipoAuthPerf' not in dex and b'AuthPerfBenchmark' not in dex and b'AuthPerfActivity' not in dex and b'DebugDisposableCreation' not in dex, 'Authentication performance diagnostics in release DEX'
        assert b'Lorg/totipo/android/debug/' not in dex, 'Debug diagnostic machinery in release DEX'
        assert b'Lorg/totipo/android/reconcile/DebugVaultTiming' not in dex and b'TotipoVaultTiming' not in dex, 'Unlock timing probe in release DEX'
    assert b'Lorg/totipo/android/reconcile/ForegroundVaultCoordinatorTest' not in dex, 'Foreground tests/fault fixtures in APK'
    assert b'Lorg/totipo/android/reconcile/ImmutableCandidateImporterTest' not in dex and b'Lorg/totipo/android/TestReplicaOwners;' not in dex, 'Import tests/fixtures in APK'
    for descriptor in [b'Lorg/totipo/android/reconcile/HistoricalImmutableCandidateImporter',
                       b'Lorg/totipo/android/reconcile/CoordinatedNioProbe',
                       b'Lorg/totipo/android/AndroidVaultControllerTest',
                       b'Lorg/totipo/android/reconcile/ProductControllerFixtures',
                       b'Lorg/totipo/android/reconcile/CoordinatedPrivateStoreTest',
                       b'Lorg/totipo/android/reconcile/CoordinationSourceGuardTest']:
        assert descriptor not in dex, f'Coordination tests/historical scaffolding in APK: {descriptor!r}'
    assert b'OtpAuthShareQualification' not in dex and b'OtpAuthResolverObserver' not in dex, 'Standalone device tests in APK'
    assert b'Lorg/junit/' not in dex and b'Lorg/totipo/android/CoreDependencySmokeTest;' not in dex and b'Lorg/totipo/android/LocalReplicaOwnerTest;' not in dex and b'Lorg/totipo/android/provider/ImmutableCandidateClassifierTest' not in dex and b'Lorg/totipo/android/provider/ProviderSnapshotTest;' not in dex, 'Test classes in APK'
    if args.unsigned:
        assert not any(name.upper().startswith('META-INF/') and name.upper().endswith(('.RSA', '.DSA', '.EC', '.SF')) for name in names), 'JAR signature present'
        # An APK v2/v3 signing block sits immediately before the ZIP central directory.
        eocd = data.rfind(b'PK\x05\x06')
        assert eocd >= 0
        central_offset = struct.unpack_from('<I', data, eocd + 16)[0]
        assert data[max(0, central_offset - 16):central_offset] != b'APK Sig Block 42', 'APK signing block present'
print(f'{args.apk}: valid APK; bootstrap/NIO/core/BC present; SHA-256 {hashlib.sha256(data).hexdigest()}')
