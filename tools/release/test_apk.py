"""Decoded-policy tests, not a binary Android XML decoder or device qualification."""
import copy
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

import release

policy = release.apk_policy()
A = policy.ANDROID
EXPECTED = dict(application_id='org.totipo.android', version_name='0.1.2', version_code=3)
REFS = {'string/app_name': '@ref/0x7f030000', 'string/enrollment_label': '@ref/0x7f030006',
        'mipmap/ic_launcher': '@ref/0x7f020000', 'mipmap/ic_launcher_round': '@ref/0x7f020001'}
# Independently written, narrow decoded release fixture. Real APKs run in installCheck.
MANIFEST = '''<manifest xmlns:android="http://schemas.android.com/apk/res/android"
 package="org.totipo.android" android:versionCode="3" android:versionName="0.1.2"
 android:compileSdkVersion="37" android:compileSdkVersionCodename="17"
 platformBuildVersionCode="37" platformBuildVersionName="17">
 <uses-sdk android:minSdkVersion="30" android:targetSdkVersion="37"/>
 <uses-permission android:name="android.permission.USE_BIOMETRIC"/>
 <application android:name="org.totipo.android.TotipoApplication" android:allowBackup="false"
 android:theme="@ref/0x1030241" android:label="@ref/0x7f030000" android:icon="@ref/0x7f020000"
 android:roundIcon="@ref/0x7f020001" android:extractNativeLibs="false">
 <activity android:name="org.totipo.android.MainActivity" android:exported="true">
 <intent-filter><action android:name="android.intent.action.MAIN"/>
 <category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity>
 <activity android:name="org.totipo.android.OtpAuthEnrollmentActivity" android:exported="true"
 android:label="@ref/0x7f030006" android:excludeFromRecents="true">
 <intent-filter><action android:name="android.intent.action.VIEW"/>
 <category android:name="android.intent.category.DEFAULT"/>
 <category android:name="android.intent.category.BROWSABLE"/>
 <data android:scheme="otpauth" android:host="totp"/></intent-filter>
 <intent-filter><action android:name="android.intent.action.SEND"/>
 <category android:name="android.intent.category.DEFAULT"/>
 <data android:mimeType="text/plain"/></intent-filter></activity></application></manifest>'''


class ApkPolicyTests(unittest.TestCase):
    def check(self, root):
        policy.require_manifest(root, EXPECTED, False, REFS)

    def test_install_minimum_is_exactly_android_11(self):
        # Identity policy is shared by debug, unsigned release and signed release.
        self.check(policy.xml(MANIFEST))
        for minimum in ('26', '29', '31'):
            root = policy.xml(MANIFEST)
            root.find('uses-sdk').set(A + 'minSdkVersion', minimum)
            with self.subTest(minimum=minimum), self.assertRaisesRegex(ValueError, 'SDK policy'):
                self.check(root)

    def test_approved_manifest_and_android_defaults(self):
        root = policy.xml(MANIFEST)
        self.check(copy.deepcopy(root))
        app = root.find('application')
        for node in [app, *app]:
            node.set(A + 'enabled', 'true')
            node.set(A + 'process', 'org.totipo.android')
        app.set(A + 'debuggable', 'false')
        app.set(A + 'testOnly', 'false')
        app[:] = reversed(app[:])
        self.check(root)

    def test_security_surface_changes_fail_closed(self):
        for selector, key, value in (
            ('.', 'package', 'org.attacker'), ('.', A+'versionCode', '4'),
            ('.', A+'versionName', '0.1.3'), ('.', A+'compileSdkVersion', '36'),
            ('uses-sdk', A+'minSdkVersion', '25'), ('uses-sdk', A+'targetSdkVersion', '36'),
            ('uses-permission', A+'name', 'android.permission.INTERNET'),
            ('uses-permission', A+'maxSdkVersion', '30'),
            ('application', A+'allowBackup', 'true'), ('application', A+'debuggable', 'true'),
            ('application', A+'testOnly', 'true'), ('application', A+'enabled', 'false'),
            ('application', A+'process', ':other'), ('application', A+'backupAgent', 'other'),
            ('application', A+'fullBackupContent', 'true'),
            ('application', A+'dataExtractionRules', '@ref/0x7f040000'),
            ('application', A+'usesCleartextTraffic', 'true'),
            ('application/activity', A+'process', ':other'),
            ('application/activity', A+'exported', 'false'),
            ('application/activity', A+'enabled', '@ref/0x7f010000'),
            ('application/activity', A+'name', 'org.totipo.android.debug.StorageProbeActivity'),
            ('application/activity/intent-filter', A+'priority', '100'),
            ('application/activity/intent-filter/action', A+'name', 'android.intent.action.SEND_MULTIPLE')):
            with self.subTest(selector=selector, key=key, value=value):
                root = policy.xml(MANIFEST)
                node = root if selector == '.' else root.find(selector)
                node.set(key, value)
                with self.assertRaises(ValueError): self.check(root)
        for tag in ('activity', 'activity-alias', 'service', 'receiver', 'provider', 'profileable', 'meta-data'):
            for exported in ('true', 'false'):
                with self.subTest(tag=tag, exported=exported):
                    root = policy.xml(MANIFEST)
                    ET.SubElement(root.find('application'), tag, {A+'name': 'org.attacker.Extra', A+'exported': exported})
                    with self.assertRaises(ValueError): self.check(root)
        for tag in ('uses-permission', 'uses-permission-sdk-23', 'uses-permission-sdk-m', 'permission', 'queries', 'instrumentation'):
            root = policy.xml(MANIFEST)
            ET.SubElement(root, tag, {A+'name': 'android.permission.CAMERA'})
            with self.subTest(tag=tag), self.assertRaises(ValueError): self.check(root)
        root = policy.xml(MANIFEST)
        root.find('uses-permission').tag = 'uses-permission-sdk-23'
        with self.assertRaises(ValueError): self.check(root)

    def test_missing_duplicate_and_filter_expansion(self):
        for selector in ('uses-sdk', 'uses-permission', 'application', 'application/activity',
                         'application/activity/intent-filter'):
            for duplicate in (True, False):
                root = policy.xml(MANIFEST)
                node = root.find(selector)
                parent = next(p for p in root.iter() if node in p)
                if duplicate: parent.append(copy.deepcopy(node))
                else: parent.remove(node)
                with self.subTest(selector=selector, duplicate=duplicate), self.assertRaises(ValueError): self.check(root)
        for key, value in (('scheme', 'https'), ('host', '*'), ('pathPrefix', '/'), ('mimeType', '*/*')):
            root = policy.xml(MANIFEST)
            root.findall('application/activity')[1].find('intent-filter/data').set(A+key, value)
            with self.subTest(key=key), self.assertRaises(ValueError): self.check(root)
        root = policy.xml(MANIFEST)
        del root.find('application').attrib[A+'allowBackup']
        with self.assertRaises(ValueError): self.check(root)

    def test_source_version_and_code_are_the_authority(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / 'VERSION').write_bytes(b'0.1.2\n')
            (root / 'VERSION_CODE').write_bytes(b'3\n')
            self.assertEqual(policy.reviewed_metadata(root), EXPECTED)
            for file, raw in (('VERSION_CODE', b'03\n'), ('VERSION_CODE', b'3'),
                              ('VERSION_CODE', b'2100000001\n'), ('VERSION', b'0.1.2\n\n')):
                path = root / file; original = path.read_bytes(); path.write_bytes(raw)
                with self.subTest(file=file, raw=raw), self.assertRaises(ValueError): policy.reviewed_metadata(root)
                path.write_bytes(original)
            # Even internally consistent APK/provenance metadata cannot override source.
            for field, value in (('version_code', 4), ('version_name', '0.1.3')):
                provenance = dict(EXPECTED, **{field: value})
                with patch.object(policy, 'manifest', return_value=policy.xml(MANIFEST)), \
                     patch.object(policy, 'reviewed_metadata', return_value=provenance):
                    with self.assertRaises(ValueError): release.apk_metadata('fixture')

    def test_native_failure_missing_and_malformed_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / 'fixture.apk'
            with self.assertRaises(ValueError): policy.manifest(apk)
            apk.touch()
            for failure in (FileNotFoundError('SDK tool'), subprocess.CalledProcessError(1, 'apkanalyzer')):
                with patch.object(policy, 'sdk_output', side_effect=failure):
                    with self.assertRaises(type(failure)): policy.manifest(apk)
            for output in ('', 'ERROR: native failure', '<manifest>', '<other/>',
                           '<manifest package="a" package="b"/>', '<!DOCTYPE manifest><manifest/>',
                           '<manifest>incomplete output</manifest>'):
                with self.subTest(output=output), patch.object(policy, 'sdk_output', return_value=output):
                    with self.assertRaises((ValueError, ET.ParseError)): policy.manifest(apk)
            with patch.dict('os.environ', {}, clear=True):
                with self.assertRaisesRegex(ValueError, 'SDK required'): policy.sdk_output('apkanalyzer')

    def test_missing_essential_resource_and_malformed_resource_output(self):
        for output in ('', 'ERROR: native failure', 'Binary APK\nPackage name=org.totipo.android id=7f\n',
                       'Binary APK\nPackage name=org.totipo.android id=7f\n'
                       '    resource 0x7f020000 mipmap/ic_launcher\n'
                       '      (mdpi) (file) res/missing.png type=PNG\n'):
            with self.subTest(output=output), self.assertRaises(ValueError):
                policy.require_resources(output, [], False)
