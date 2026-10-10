"""Fast standard-library tests; synthetic ZIPs require no keys or SDK."""
import importlib.util
from pathlib import Path
import tempfile
import json
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import zipfile
from payload import compare, inventory
import release

spec = importlib.util.spec_from_file_location('signed', Path(__file__).with_name('verify-signed-apk.py'))
signed = importlib.util.module_from_spec(spec)
spec.loader.exec_module(signed)


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def metadata(self, code=42, name='1.0.0', app='org.totipo.android'):
        return dict(application_id=app, version_code=code, version_name=name)

    def provenance(self, **changes):
        self.unsigned = self.root / 'unsigned.apk'
        self.unsigned.write_bytes(b'qualified fixture')
        p = dict(version='1.0.0', source_commit='a' * 40, tag='v1.0.0',
                 unsigned_sha256=release.sha(self.unsigned), **self.metadata(),
                 nix_derivation='/nix/store/' + 'a' * 32 + '-fixture.drv',
                 nix_output='/nix/store/' + 'b' * 32 + '-fixture',
                 certificate_sha256='a' * 64, java_runtime='0.2.0', protocol='v1/r19')
        p.update(changes)
        path = self.root / 'release-provenance.json'
        path.write_text(json.dumps(p))
        return path

    def signature(self):
        return (''.join(f'Verified using v{i} scheme (test): {v}\n' for i, v in
                       ((1, 'false'), (2, 'true'), (3, 'true'), (4, 'false'))) +
                'Number of signers: 1\nSigner #1 certificate SHA-256 digest: ' + 'a' * 64 + '\n')

    def test_non_one_signed_metadata_and_mismatches(self):
        provenance = self.provenance()
        fp = self.root / 'fp'; fp.write_text('a' * 64)
        # Exercise the verifier, with external SDK responses deterministically mocked.
        for actual, error in ((self.metadata(), None), (self.metadata(code=43), 'versionCode mismatch'),
                              (self.metadata(name='1.0.1'), 'versionName mismatch')):
            with patch.dict('os.environ', ANDROID_HOME='/pinned/sdk'), patch.object(signed.subprocess, 'run', return_value=SimpleNamespace(stdout=self.signature())), patch.object(release, 'apk_metadata', side_effect=[self.metadata(), actual]):
                if error:
                    with self.assertRaisesRegex(ValueError, error):
                        signed.verify('signed.apk', fp, provenance, self.unsigned)
                else:
                    signed.verify('signed.apk', fp, provenance, self.unsigned)

    def test_unsigned_metadata_and_sha_binding(self):
        for changes, actual, error in (
            ({}, self.metadata(code=43), 'versionCode mismatch'),
            ({}, self.metadata(name='other'), 'versionName mismatch'),
            ({'unsigned_sha256': 'b' * 64}, self.metadata(), 'SHA-256 mismatch')):
            p = release.load_provenance(self.provenance(**changes))
            with patch.object(release, 'apk_metadata', return_value=actual):
                with self.assertRaisesRegex(ValueError, error): release.bind_unsigned(self.unsigned, p)

    def test_schema_fail_closed(self):
        invalid = [('version_code', v) for v in (True, '42', 42.0, 0, -1, 2100000001)] + [
            ('version_name', ''), ('version_name', 'bad\nname'), ('version_name', 42),
            ('application_id', 'org.attacker'), ('source_commit', 'main'), ('tag', 'v2.0.0'),
            ('version', 'bad'), ('unsigned_sha256', 'bad'), ('certificate_sha256', 'bad'),
            ('nix_derivation', '/tmp/x'), ('nix_output', '/tmp/x'),
            ('java_runtime', 'other'), ('protocol', 'other'), ('schema_version', 2)]
        for field, value in invalid:
            with self.subTest(field=field, value=value):
                with self.assertRaises(ValueError): release.load_provenance(self.provenance(**{field: value}))
        path = self.provenance(); text = path.read_text()
        path.write_text(text[:-1] + ', "version_code": 43}')
        with self.assertRaisesRegex(ValueError, 'Duplicate'): release.load_provenance(path)
        path = self.provenance(); p = json.loads(path.read_text()); del p['tag']; path.write_text(json.dumps(p))
        with self.assertRaisesRegex(ValueError, 'fields'): release.load_provenance(path)

    def test_apk_metadata_pins_identity(self):
        for app in ('org.totipo.android', 'org.attacker'):
            badging = f"package: name='{app}' versionCode='42' versionName='1.0.0' platformBuildVersionName='test'\n"
            with patch.dict('os.environ', ANDROID_HOME='/pinned/sdk'), patch.object(release, 'run', return_value=badging):
                if app == release.APPLICATION_ID: self.assertEqual(release.apk_metadata('fixture'), self.metadata())
                else:
                    with self.assertRaisesRegex(ValueError, 'applicationId'): release.apk_metadata('fixture')

    def test_qualification_observes_metadata_and_checks_version(self):
        with patch.object(release, 'unsigned_check'), patch.object(release, 'apk_metadata', return_value=self.metadata()):
            self.provenance()
            p = release.qualified_provenance(self.unsigned, '1.0.0', 'a' * 40, 'a' * 64,
                '/nix/store/' + 'a' * 32 + '-fixture.drv', '/nix/store/' + 'b' * 32 + '-fixture')
            self.assertEqual(p['version_code'], 42)
            self.assertEqual(p['unsigned_sha256'], release.sha(self.unsigned))
            with self.assertRaisesRegex(ValueError, 'VERSION'):
                release.qualified_provenance(self.unsigned, '1.0.1', 'a' * 40, 'a' * 64, p['nix_derivation'], p['nix_output'])

    def test_finalize_preserves_qualification_provenance(self):
        import argparse
        provenance = self.provenance()
        original = provenance.read_bytes()
        (self.root / 'unsigned-release.apk').write_bytes(self.unsigned.read_bytes())
        (self.root / 'unsigned-release.apk.sha256').write_text('fixture checksum')
        (self.root / 'totipo-android-1.0.0.apk').write_bytes(b'signed')
        args = argparse.Namespace(version='1.0.0', bundle=self.root, output=self.root / 'verified')
        with patch.object(release, 'bundle_check', return_value=release.load_provenance(provenance)), patch.object(release, 'verify_signed'), patch.object(release, 'compare'):
            release.finalize(args)
        self.assertEqual((args.output / provenance.name).read_bytes(), original)
        self.assertEqual((args.output / 'unsigned-release.apk').read_bytes(), self.unsigned.read_bytes())

    def apk(self, name, entries):
        path = self.root / name
        with zipfile.ZipFile(path, 'w') as z:
            for key, value in entries:
                z.writestr(key, value)
        return path

    def test_identical(self):
        a = self.apk('a', [('manifest', b'x'), ('dex', b'y')])
        compare(a, a)

    def test_changed_content_order_entry_and_metadata(self):
        a = self.apk('a', [('manifest', b'x'), ('dex', b'y')])
        for entries in ([('manifest', b'z'), ('dex', b'y')], [('dex', b'y'), ('manifest', b'x')],
                        [('manifest', b'x')], [('manifest', b'x'), ('dex', b'y'), ('extra', b'')]):
            with self.assertRaises(ValueError):
                compare(a, self.apk('b', entries))
        b = self.root / 'b'
        with zipfile.ZipFile(b, 'w') as z:
            e = zipfile.ZipInfo('manifest'); e.comment = b'changed'
            z.writestr(e, b'x'); z.writestr('dex', b'y')
        with self.assertRaises(ValueError):
            compare(a, b)

    def test_duplicate_and_jar_signature(self):
        import warnings
        with warnings.catch_warnings():
            warnings.simplefilter('ignore')
            a = self.apk('a', [('dex', b'x'), ('dex', b'x')])
        with self.assertRaises(ValueError): inventory(a)
        with self.assertRaises(ValueError): inventory(self.apk('b', [('META-INF/KEY.SF', b'x')]))

    def test_fingerprint(self):
        p = self.root / 'fingerprint'
        for value in ('', 'a' * 63, 'A' * 64, 'gg' * 32, 'aa:' * 32, 'a' * 64 + '\nextra'):
            p.write_text(value)
            with self.assertRaises(ValueError): signed.fingerprint(p)
        p.write_text('a' * 64 + '\n')
        self.assertEqual(signed.fingerprint(p), 'a' * 64)

    def test_unprovisioned_and_malformed_identity(self):
        with patch.object(release, 'CERT', self.root / 'cert'), patch.object(release, 'FP', self.root / 'fp'):
            with self.assertRaisesRegex(ValueError, 'unprovisioned'): release.expected_identity()
            release.CERT.write_text('not a certificate')
            release.FP.write_text('a' * 64)
            with self.assertRaisesRegex(ValueError, 'PEM'): release.expected_identity()

    def test_source_fail_closed(self):
        for version, commit in (('x; echo bad', 'a' * 40), ('1.0.0-dev', 'a' * 40), ('1.0.0', 'main')):
            with self.assertRaises(ValueError): release.identity(version, commit)
        with patch.dict('os.environ', {'GITHUB_REF': 'refs/tags/v1.0.0'}):
            with self.assertRaisesRegex(ValueError, 'main'): release.identity('1.0.0', 'a' * 40)

    def test_private_tracking_guard(self):
        area = self.root / 'release/signing'
        area.mkdir(parents=True)
        (area / 'README.md').write_text('public')
        with patch.object(release, 'ROOT', self.root), patch.object(release, 'run', return_value='release/signing/private.p12'):
            with self.assertRaisesRegex(ValueError, 'tracked'): release.guard()
        with patch.object(release, 'ROOT', self.root), patch.object(release, 'run', return_value='release/signing/README.md'):
            release.guard()
            (area / 'private.key').write_text('TEST')
            with self.assertRaisesRegex(ValueError, 'material'): release.guard()

    def test_workflow_static_contract(self):
        path = Path(__file__).resolve().parents[2] / '.github/workflows/release.yml'
        if not path.exists():
            self.skipTest('Workflow intentionally outside filtered Android package source')
        text = path.read_text()
        import re
        uses = re.findall(r'uses: ([^\s]+)', text)
        self.assertTrue(uses)
        self.assertTrue(all(re.fullmatch(r'[\w/-]+@[0-9a-f]{40}', u) for u in uses))
        jobs = re.split(r'^  (qualify|sign|verify|publish):\n', text, flags=re.M)
        sections = dict(zip(jobs[1::2], jobs[2::2]))
        for job in ('qualify', 'verify', 'publish'):
            self.assertNotIn('secrets.', sections[job])
            self.assertNotIn('environment:', sections[job])
        self.assertNotIn('contents: write', sections['sign'])
        self.assertIn('environment: android-release', sections['sign'])
        self.assertIn('contents: write', sections['publish'])
        secret_steps = [step for step in sections['sign'].split('      - ') if 'secrets.' in step]
        self.assertEqual(len(secret_steps), 1)
        body = secret_steps[0].split('run: |', 1)[1]
        for forbidden in ('python', 'gradle', 'nix ', 'gh ', 'tools/'):
            self.assertNotIn(forbidden, body)
        self.assertIn('--alignment-preserved true', body)
        self.assertEqual(text.count('${{ secrets.'), 4)
        self.assertNotIn('pull_request_target', text)

    def test_prepare_refuses_parallel_derivation(self):
        import argparse
        args = argparse.Namespace(version='1.0.0', commit='a' * 40, bundle=self.root / 'bundle')
        with patch.object(release, 'identity', return_value='b' * 64), patch.object(release, 'run', side_effect=['/nix/store/check.drv', '/nix/store/other.drv']):
            with self.assertRaisesRegex(ValueError, 'derivations differ'): release.prepare(args)
        self.assertFalse(args.bundle.exists())

    def test_bundle_refuses_changed_unsigned_and_provenance(self):
        import argparse, json
        args = argparse.Namespace(version='1.0.0', commit='a' * 40, bundle=self.root)
        apk = self.root / 'unsigned-release.apk'; apk.write_bytes(b'unsigned')
        p = dict(version='1.0.0', source_commit='a' * 40, tag='v1.0.0', certificate_sha256='b' * 64,
                 application_id='org.totipo.android', version_code=42, version_name='1.0.0',
                 nix_derivation='/nix/store/' + 'a' * 32 + '-fixture.drv',
                 nix_output='/nix/store/' + 'b' * 32 + '-fixture',
                 java_runtime='0.2.0', protocol='v1/r19', unsigned_sha256=release.sha(apk))
        provenance = self.root / 'release-provenance.json'
        provenance.write_text(json.dumps(p))
        (self.root / 'unsigned-release.apk.sha256').write_text(p['unsigned_sha256'] + '  unsigned-release.apk\n')
        with patch.object(release, 'identity', return_value='b' * 64), patch.object(release, 'unsigned_check'), patch.object(release, 'apk_metadata', return_value=dict(application_id=p['application_id'], version_code=p['version_code'], version_name=p['version_name'])):
            release.bundle_check(args)
            apk.write_bytes(b'changed')
            with self.assertRaisesRegex(ValueError, 'changed'): release.bundle_check(args)
            p['source_commit'] = 'c' * 40; provenance.write_text(json.dumps(p))
            with self.assertRaisesRegex(ValueError, 'identity'): release.bundle_check(args)

    def test_signature_output_rejections(self):
        from types import SimpleNamespace
        p = self.root / 'fingerprint'; p.write_text('a' * 64)
        valid = ('Verified using v1 scheme (JAR signing): false\n'
                 'Verified using v2 scheme (APK Signature Scheme v2): true\n'
                 'Verified using v3 scheme (APK Signature Scheme v3): true\n'
                 'Verified using v4 scheme (APK Signature Scheme v4): false\n'
                 'Number of signers: 1\nSigner #1 certificate SHA-256 digest: ' + 'a' * 64 + '\n')
        cases = [valid.replace('signers: 1', 'signers: 2'), valid.replace('a' * 64, 'b' * 64),
                 valid.replace('v1 scheme (JAR signing): false', 'v1 scheme (JAR signing): true'),
                 valid.replace('v2): true', 'v2): false'), valid.replace('v3): true', 'v3): false'),
                 valid.replace('v4): false', 'v4): true')]
        for output in cases:
            with patch.dict('os.environ', {'ANDROID_HOME': '/pinned/sdk'}), patch.object(signed.subprocess, 'run', return_value=SimpleNamespace(stdout=output)), patch.object(release, 'apk_metadata', return_value=self.metadata()):
                with self.assertRaises(ValueError): signed.verify('test.apk', p, self.provenance(), self.unsigned)

    def test_remote_mismatch_leaves_draft(self):
        import argparse, json
        bundle = self.root / 'bundle'; bundle.mkdir()
        apk = bundle / 'totipo-android-1.0.0.apk'; apk.write_bytes(b'signed')
        (bundle / (apk.name + '.sha256')).write_text(release.sha(apk) + '  ' + apk.name + '\n')
        (bundle / 'release-provenance.json').write_text('{}')
        args = argparse.Namespace(version='1.0.0', commit='a' * 40, bundle=bundle)
        commands = []
        def fake_run(*cmd):
            commands.append(cmd)
            if cmd[:2] == ('gh', 'api'):
                if cmd[-1].endswith('?per_page=100'): return '[]'
                return json.dumps(dict(draft=True, tag_name='v1.0.0', id=42))
            if cmd[:3] == ('gh', 'release', 'download'):
                target = self.root / 'remote-release'
                (target / apk.name).write_bytes(b'tampered')
            return ''
        with patch.dict('os.environ', {'GITHUB_REPOSITORY': 'example/repo'}), patch.object(release, 'bundle_check', return_value=dict(tag='v1.0.0', certificate_sha256='b' * 64)), patch.object(release, 'verify_signed'), patch.object(release, 'run', side_effect=fake_run):
            with self.assertRaisesRegex(ValueError, 'Remote release bytes'): release.publish(args)
        self.assertTrue(any(c[:3] == ('gh', 'release', 'create') and '--draft' in c for c in commands))
        self.assertFalse(any('PATCH' in c for c in commands))


if __name__ == '__main__':
    unittest.main()
