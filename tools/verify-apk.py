#!/usr/bin/env python3
"""Check APK ZIP integrity, bootstrap/NIO/core/BC DEX presence and optional unsigned status."""
import argparse
import hashlib
import json
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
parser.add_argument('--debug-probe', action='store_true', help='Require existing debug diagnostic classes')
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
for permission in ('android.permission.CAMERA', 'android.permission.INTERNET', 'android.permission.READ_EXTERNAL_STORAGE',
                   'android.permission.WRITE_EXTERNAL_STORAGE', 'android.permission.MANAGE_EXTERNAL_STORAGE'):
    assert permission not in manifest, f'Forbidden APK permission: {permission}'
resources = subprocess.check_output([
    str(Path(sdk) / 'build-tools/36.0.0/aapt2'), 'dump', 'resources', str(args.apk)], text=True)
for attribute, resource in [('icon', 'ic_launcher'), ('roundIcon', 'ic_launcher_round')]:
    match = re.search(r'resource (0x[0-9a-f]+) (?:org.totipo.android:)?mipmap/' + resource + r'\b', resources)
    assert match, f'Missing Totipo launcher resource: {resource}'
    assert re.search(r'android:' + attribute + r'\([^)]*\)=@' + match[1] + r'\b', manifest), 'Wrong application branding'
root = Path(__file__).resolve().parents[1]
branding = json.loads((root / 'branding-provenance.json').read_text())
assert branding['commit'] == 'cdb4e91be1c6d3704874b2b92457ffe7be5e9084'
for path, provenance in branding['assets'].items():
    assert hashlib.sha256((root / path).read_bytes()).hexdigest() == provenance['sha256'], f'Changed canonical source: {path}'
    assert provenance['source'] == 'design/icons/platforms/android/res/' + path.removeprefix('app/src/main/res/')
enrollment_name = 'org.totipo.android.OtpAuthEnrollmentActivity'
assert 'OtpAuthIntentProbeActivity' not in manifest, 'Obsolete qualification handler'
assert len(re.findall(r'android:scheme\([^)]*\)="otpauth"', manifest)) == 1, 'Duplicate/aliased otpauth filters'
for forbidden in ('android.intent.action.SEND_MULTIPLE', '*/*', 'image/*', 'otpauth-migration'):
    assert forbidden not in manifest, f'Forbidden enrollment registration: {forbidden}'
# Every APK has the exact production ingress boundary.
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
assert len([activity for activity in activities if 'otpauth' in activity]) == 1, 'Duplicate otpauth handler'
assert len([activity for activity in activities if 'android.intent.action.SEND' in activity]) == 1, 'Duplicate SEND handler'
enrollment = [activity for activity in activities if enrollment_name in activity]
assert len(enrollment) == 1, 'Missing/duplicate production enrollment Activity'
enrollment = enrollment[0]
assert re.search(r'android:exported\([^)]*\)=true', enrollment), 'Enrollment must be exported'
assert enrollment.count('E: intent-filter ') == 2, 'Unexpected enrollment filters'
filters = re.split(r'^ +E: intent-filter .*$', enrollment, flags=re.MULTILINE)[1:]
assert len(filters) == 2
view, send = filters
for value in ('android.intent.action.VIEW', 'android.intent.category.DEFAULT',
              'android.intent.category.BROWSABLE'):
    assert f'="{value}"' in view, f'Missing VIEW boundary: {value}'
assert view.count('E: action ') == 1 and view.count('E: category ') == 2 and view.count('E: data ') == 1
for attribute, value in (('scheme', 'otpauth'), ('host', 'totp')):
    assert re.search(r'android:' + attribute + r'\([^)]*\)="' + value + '"', view), f'Wrong VIEW {attribute}'
assert send.count('E: action ') == 1 and send.count('E: category ') == 1 and send.count('E: data ') == 1
for value in ('android.intent.action.SEND', 'android.intent.category.DEFAULT'):
    assert f'="{value}"' in send, f'Missing SEND boundary: {value}'
assert re.search(r'android:mimeType\([^)]*\)="text/plain"', send), 'Wrong SEND MIME'
assert 'android:scheme(' not in send and 'android:host(' not in send
assert 'android:mimeType(' not in view
assert 'android:path' not in enrollment and 'android:port(' not in enrollment
assert not re.search(r'android:host\([^)]*\)="hotp"', manifest)
assert 'android.intent.action.SEND_MULTIPLE' not in enrollment and '*/*' not in enrollment
with zipfile.ZipFile(args.apk) as apk:
    assert apk.testzip() is None, 'Corrupt APK entry'
    names = apk.namelist()
    assert len(names) == len(set(names)), 'Duplicate ZIP entry'
    assert 'AndroidManifest.xml' in names and 'resources.arsc' in names
    icon_blocks = {}
    for match in re.finditer(r'(?m)^ +resource (0x[0-9a-f]+) (?:org.totipo.android:)?((?:mipmap|drawable)/ic_launcher[^\s]*)\n', resources):
        end = re.search(r'(?m)^ +(?:resource |type )', resources[match.end():])
        icon_blocks[match[2]] = (match[1], resources[match.end():match.end() + end.start()] if end else resources[match.end():])
    for path in branding['assets']:
        folder, filename = path.removeprefix('app/src/main/res/').split('/')
        kind = folder.split('-')[0]
        qualifier = folder.split('-')[1] if '-' in folder else ''
        key = kind + '/' + filename.rsplit('.', 1)[0]
        assert key in icon_blocks, f'Missing packaged icon resource: {key}'
        block = icon_blocks[key][1]
        config = re.search(r'\(' + qualifier + r'\) \(file\) (res/[^\s]+)', block)
        assert config and config[1] in names, f'Missing packaged icon configuration: {path}'
        if filename.endswith('.xml'):
            adaptive = subprocess.check_output([str(Path(sdk) / 'build-tools/36.0.0/aapt2'),
                'dump', 'xmltree', str(args.apk), '--file', config[1]], text=True)
            assert 'E: adaptive-icon' in adaptive and 'E: foreground' in adaptive and 'E: background' in adaptive
            for layer in ('background', 'foreground'):
                assert '@' + icon_blocks['drawable/ic_launcher_' + layer][0] in adaptive, 'Wrong canonical adaptive layer'
    dex = b''.join(apk.read(name) for name in names if name.startswith('classes') and name.endswith('.dex'))
    assert dex.startswith(b'dex\n'), 'Missing DEX'
    for descriptor in [b'Lorg/totipo/android/sync/OutboundImmutablePlanner;', b'Lorg/totipo/android/sync/ProviderObjectWriter;', b'Lorg/totipo/android/sync/DetachedImmutableObject;', b'Lorg/totipo/android/sync/ProviderIoLane;', b'Lorg/totipo/android/sync/SyncFolderBinding;', b'Lorg/totipo/android/sync/AndroidSyncFolderPort;', b'Lorg/totipo/android/MainActivity;', b'Lorg/totipo/android/AddTokenRequest;', b'Lorg/totipo/android/AddTokenOutcome;', b'Lorg/totipo/android/Base32;', b'Lorg/totipo/android/TokenListAdapter;', b'Lorg/totipo/android/RevealedTotp;', b'Lorg/totipo/android/TotpPresentation;', b'Lorg/totipo/android/PlatformCodeClipboard;', b'Lorg/totipo/android/TotipoApplication;', b'Lorg/totipo/android/AndroidVaultController;', b'Lorg/totipo/VaultSession;', b'Lorg/totipo/ObjectCandidateValidation$Valid;', b'Lorg/totipo/ObjectCandidateValidation$Invalid;', b'Lorg/totipo/android/provider/ProviderTreeReader;', b'Lorg/totipo/android/provider/ImmutableCandidateClassifier;', b'Lorg/totipo/android/reconcile/ImmutableCandidateImporter;', b'Lorg/totipo/android/reconcile/ForegroundVaultCoordinator;', b'Lorg/totipo/android/reconcile/CoordinatedPrivateStore;', b'Lorg/totipo/storage/nio/NioTotipoStore;', b'Lorg/totipo/storage/nio/NioStoreComposition;', b'Lorg/totipo/VaultId;', b'Lorg/bouncycastle/crypto/generators/Argon2BytesGenerator;']:
        assert descriptor in dex, f'Missing packaged class: {descriptor!r}'
    for descriptor in (b'Lorg/totipo/android/OtpAuthEnrollmentActivity;', b'Lorg/totipo/android/OtpAuthUriParser;', b'Lorg/totipo/android/OtpAuthTransport;'):
        assert descriptor in dex, f'Missing production enrollment class: {descriptor!r}'
    for retired in (b'Lorg/totipo/PasswordChangeResult;', b'Lorg/totipo/VaultFingerprint;', b'Lorg/totipo/spi/PreparedVault;', b'Lorg/totipo/spi/VaultPrepare;', b'openPrivate'):
        assert retired not in dex, f'Retired Java API packaged: {retired!r}'
    assert b'OtpAuthIntentProbeActivity' not in dex, 'Obsolete probe in APK'
    for forbidden in (b'Lcom/google/api/services/drive/', b'Lcom/google/android/apps/docs/', b'Lcom/nutomic/syncthingandroid/', b'Landroidx/work/', b'Landroidx/documentfile/', b'Lorg/totipo/safqualification/', b'Lcom/google/zxing/', b'Landroidx/camera/', b'Lcom/google/mlkit/', b'Lcom/google/android/gms/'):
        assert forbidden not in dex, f'Forbidden QR/camera/service dependency: {forbidden!r}'
    if args.debug_probe:
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
    assert b'SafOutboundRegression' not in dex and b'OutboundImmutablePlannerTest' not in dex and b'ProviderObjectWriterTest' not in dex and b'PublicationPort' not in dex, 'Outbound standalone/fake machinery in APK'
    assert b'Lorg/totipo/qualification/' not in dex and b'Lorg/totipo/ingresstest/' not in dex, 'Standalone device tests in APK'
    assert b'Lorg/junit/' not in dex and b'Lorg/totipo/android/CoreDependencySmokeTest;' not in dex and b'Lorg/totipo/android/LocalReplicaOwnerTest;' not in dex and b'Lorg/totipo/android/provider/ImmutableCandidateClassifierTest' not in dex and b'Lorg/totipo/android/provider/ProviderSnapshotTest;' not in dex, 'Test classes in APK'
    if args.unsigned:
        assert not any(name.upper().startswith('META-INF/') and name.upper().endswith(('.RSA', '.DSA', '.EC', '.SF')) for name in names), 'JAR signature present'
        # An APK v2/v3 signing block sits immediately before the ZIP central directory.
        eocd = data.rfind(b'PK\x05\x06')
        assert eocd >= 0
        central_offset = struct.unpack_from('<I', data, eocd + 16)[0]
        assert data[max(0, central_offset - 16):central_offset] != b'APK Sig Block 42', 'APK signing block present'
print(f'{args.apk}: valid APK; bootstrap/NIO/core/BC present; SHA-256 {hashlib.sha256(data).hexdigest()}')
