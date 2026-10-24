#!/usr/bin/env python3
"""Totipo final APK policy. SDK tools decode manifests/resources; no binary XML parser."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import struct
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parent / 'release'))
from payload import require
from source import code, version

ROOT = Path(__file__).resolve().parents[1]
APP = 'org.totipo.android'
ANDROID = '{http://schemas.android.com/apk/res/android}'


def reviewed_metadata(root=ROOT):
    raw = (root / 'VERSION').read_bytes()
    name = raw.decode('ascii').removesuffix('\n')
    version(name)
    require(raw == (name + '\n').encode(), 'Malformed VERSION: canonical version and LF required')
    return dict(application_id=APP, version_name=name,
                version_code=code((root / 'VERSION_CODE').read_bytes()))


def sdk_output(tool, *args):
    sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    require(sdk, 'Pinned Android SDK required')
    directory = 'cmdline-tools/22.0/bin' if tool == 'apkanalyzer' else 'build-tools/36.0.0'
    return subprocess.check_output([str(Path(sdk) / directory / tool), *map(str, args)], text=True)


def xml(text):
    require('<!' not in text, 'Unsupported XML declaration')
    root = ET.fromstring(text)
    require(all(not (e.text or '').strip() and not (e.tail or '').strip() for e in root.iter()),
            'Unexpected XML text')
    return root


def manifest(apk):
    require(Path(apk).is_file(), 'Missing APK')
    root = xml(sdk_output('apkanalyzer', 'manifest', 'print', apk))
    require(root.tag == 'manifest', 'Missing manifest root')
    return root


def metadata(root):
    require(root.get('package') == APP, 'APK applicationId differs')
    raw = root.attrib[ANDROID + 'versionCode']
    value = code((raw + '\n').encode('ascii'))
    name = root.attrib[ANDROID + 'versionName']
    require(bool(name), 'Missing versionName')
    return dict(application_id=APP, version_code=value, version_name=name)


def require_identity(root, expected):
    actual = metadata(root)
    for key in actual:
        require(actual[key] == expected[key], f'APK {key} differs from reviewed source')
    require(root.find('uses-sdk') is not None, 'Missing uses-sdk')
    require(root.find('uses-sdk').attrib == {ANDROID + 'minSdkVersion': '30',
            ANDROID + 'targetSdkVersion': '37'}, 'Unexpected SDK policy')
    return actual


def shape(element):
    # Order is not Android policy; duplicate declarations remain visible in the tuple.
    return element.tag, tuple(sorted(element.attrib.items())), tuple(sorted(map(shape, element)))


def require_manifest(root, expected, debug, refs):
    require_identity(root, expected)
    app = root.find('application')
    require(app is not None, 'Missing application')
    # Android defaults: enabled=true, process=package, debuggable/testOnly=false.
    # Accept explicit defaults, but never resource-valued booleans or a new process.
    for node in [app, *app.findall('activity')]:
        for key, default in (('enabled', 'true'), ('process', APP)):
            if node.get(ANDROID + key) == default:
                del node.attrib[ANDROID + key]
    for key in ('testOnly', 'debuggable'):
        if app.get(ANDROID + key) == 'false':
            del app.attrib[ANDROID + key]
    launcher = '''<intent-filter><action android:name="android.intent.action.MAIN"/>
        <category android:name="android.intent.category.LAUNCHER"/></intent-filter>'''
    probes = ''
    if debug:
        for name, label, filters in (
            ('DebugTotpFixtureActivity', 'Totipo disposable TOTP fixture', ''),
            ('debug.StorageProbeActivity', 'Totipo disposable storage probe', launcher),
            ('debug.LocalNioProbeActivity', 'Totipo local NIO qualification', ''),
            ('debug.AuthPerfActivity', 'Totipo authentication diagnostics', '')):
            probes += f'<activity android:name="{APP}.{name}" android:label="{label}" android:exported="true">{filters}</activity>'
    flags = (f'android:debuggable="true" android:fullBackupContent="false" '
             f'android:dataExtractionRules="{refs["xml/storage_probe_extraction_rules"]}"' if debug else '')
    approved = xml(f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android"
        package="{APP}" android:versionName="{expected['version_name']}" android:versionCode="{expected['version_code']}"
        android:compileSdkVersion="37" android:compileSdkVersionCodename="17"
        platformBuildVersionCode="37" platformBuildVersionName="17">
      <uses-sdk android:minSdkVersion="30" android:targetSdkVersion="37"/>
      <uses-permission android:name="android.permission.USE_BIOMETRIC"/>
      <application android:name="{APP}.TotipoApplication" android:allowBackup="false"
          android:extractNativeLibs="false" android:theme="@ref/0x1030241"
          android:label="{refs['string/app_name']}" android:icon="{refs['mipmap/ic_launcher']}"
          android:roundIcon="{refs['mipmap/ic_launcher_round']}" {flags}>
        <activity android:name="{APP}.MainActivity" android:exported="true">{launcher}</activity>
        <activity android:name="{APP}.OtpAuthEnrollmentActivity" android:exported="true"
            android:label="{refs['string/enrollment_label']}" android:excludeFromRecents="true">
          <intent-filter><action android:name="android.intent.action.VIEW"/>
            <category android:name="android.intent.category.DEFAULT"/>
            <category android:name="android.intent.category.BROWSABLE"/>
            <data android:scheme="otpauth" android:host="totp"/></intent-filter>
          <intent-filter><action android:name="android.intent.action.SEND"/>
            <category android:name="android.intent.category.DEFAULT"/>
            <data android:mimeType="text/plain"/></intent-filter>
        </activity>{probes}
      </application></manifest>''')
    require(shape(root) == shape(approved), 'Final manifest differs from reviewed policy')


def require_resources(output, names, debug):
    require(output.startswith('Binary APK\nPackage name=' + APP + ' id=7f\n'),
            'Unsupported resource output')
    blocks = re.findall(r'^    resource (0x[0-9a-f]+) (\S+)\n(.*?)(?=^    resource |^  type |\Z)',
                        output, re.M | re.S)
    require(blocks and len(blocks) == len({key for _, key, _ in blocks}) and
            len(blocks) == len({rid for rid, _, _ in blocks}), 'Missing/duplicate resource records')
    resources = {key: (rid, body) for rid, key, body in blocks}
    essential = ['mipmap/ic_launcher', 'mipmap/ic_launcher_round',
                 'drawable/ic_launcher_background', 'drawable/ic_launcher_foreground',
                 'string/app_name', 'string/enrollment_label']
    if debug:
        essential.append('xml/storage_probe_extraction_rules')
    xml_paths = {}
    for key in essential:
        require(key in resources, 'Missing essential resource: ' + key)
        body = resources[key][1]
        files = re.findall(r'^      \(([^)]*)\) \(file\) (res/\S+) type=(PNG|XML)$', body, re.M)
        if key.startswith(('mipmap/', 'drawable/', 'xml/')):
            qualifiers = ({'mdpi', 'hdpi', 'xhdpi', 'xxhdpi', 'xxxhdpi', 'anydpi'}
                          if key.startswith('mipmap/') else {''})
            require(len(files) == len(qualifiers) and {q for q, _, _ in files} == qualifiers,
                    'Missing/ambiguous resource configurations: ' + key)
            require(all(path in names for _, path, _ in files), 'Missing packaged resource: ' + key)
            xml_paths.update({key: path for _, path, kind in files if kind == 'XML'})
        else:
            label = 'Totipo' if key == 'string/app_name' else 'Add to Totipo'
            require(body.strip() == f'() "{label}"', 'Wrong application label: ' + key)
    return {key: '@ref/' + rid for key, (rid, _) in resources.items()}, xml_paths


def verify(apk, debug=False, unsigned=False):
    data = Path(apk).read_bytes()
    with zipfile.ZipFile(apk) as archive:
        require(archive.testzip() is None, 'Corrupt APK entry')
        names = archive.namelist()
        require(len(names) == len(set(names)), 'Duplicate ZIP entry')
        require({'AndroidManifest.xml', 'resources.arsc', 'classes.dex'} <= set(names), 'Missing APK payload')
        refs, xml_paths = require_resources(sdk_output('aapt2', 'dump', 'resources', apk), names, debug)
        for key, path in xml_paths.items():
            decoded = xml(sdk_output('apkanalyzer', 'resources', 'xml', '--file', path, apk))
            if key.startswith('mipmap/'):
                approved = xml(f'<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">'
                    f'<background android:drawable="{refs["drawable/ic_launcher_background"]}"/>'
                    f'<foreground android:drawable="{refs["drawable/ic_launcher_foreground"]}"/></adaptive-icon>')
            else:
                approved = xml('<data-extraction-rules><cloud-backup><exclude domain="sharedpref" path="."/>'
                    '</cloud-backup><device-transfer><exclude domain="sharedpref" path="."/>'
                    '</device-transfer></data-extraction-rules>')
            require(shape(decoded) == shape(approved), 'Unexpected packaged XML resource: ' + key)
        require_manifest(manifest(apk), reviewed_metadata(), debug, refs)
        dex = b''.join(archive.read(n) for n in names if re.fullmatch(r'classes[0-9]*\.dex', n))
        require(dex.startswith(b'dex\n'), 'Missing DEX')
        # Small security exclusions; ordinary production-class presence belongs to tests.
        forbidden = [b'Lorg/junit/', b'Lorg/totipo/qualification/', b'Lorg/totipo/ingresstest/',
                     b'Lorg/totipo/safqualification/', b'Lorg/totipo/syncthingqualification/',
                     b'Lcom/google/api/services/drive/', b'Lcom/google/android/apps/docs/',
                     b'Lcom/nutomic/syncthingandroid/', b'Landroidx/work/', b'Landroidx/biometric/',
                     b'Landroidx/documentfile/', b'Lcom/google/zxing/', b'Landroidx/camera/',
                     b'Lcom/google/mlkit/', b'Lcom/google/android/gms/']
        if not debug:
            forbidden += [b'Lorg/totipo/android/debug/', b'DebugTotpFixture',
                          b'DebugDisposableCreation', b'DebugVaultTiming', b'TotipoVaultTiming']
        require(not any(value in dex for value in forbidden) and not
                re.search(rb'Lorg/totipo/android/[A-Za-z0-9_/$]*(?:Test|Tests)(?:[$;])', dex),
                'Debug/test material in APK')
        if unsigned:
            require(not any(n.upper().startswith('META-INF/') and n.upper().endswith(
                ('.RSA', '.DSA', '.EC', '.SF')) for n in names), 'JAR signature present')
            eocd = data.rfind(b'PK\x05\x06')
            require(eocd >= 0 and eocd + 22 <= len(data), 'Missing ZIP end record')
            central = struct.unpack_from('<I', data, eocd + 16)[0]
            require(data[max(0, central - 16):central] != b'APK Sig Block 42', 'APK signing block present')
    print(f'{apk}: final {"debug" if debug else "release"} APK policy PASS; SHA-256 {hashlib.sha256(data).hexdigest()}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--unsigned', action='store_true')
    variants = parser.add_mutually_exclusive_group(required=True)
    variants.add_argument('--debug-probe', action='store_true', help='Require debug manifest policy')
    variants.add_argument('--no-debug-probe', action='store_true', help='Require release manifest policy')
    args = parser.parse_args()
    require(not (args.debug_probe and args.unsigned), 'Conflicting APK boundaries')
    verify(args.apk, args.debug_probe, args.unsigned)
