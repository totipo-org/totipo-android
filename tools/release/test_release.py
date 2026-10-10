"""Fast standard-library tests; synthetic ZIPs require no keys or SDK."""
import importlib.util
from pathlib import Path
import tempfile
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
                 application_id='org.totipo.android', version_code=1, version_name='1.0.0',
                 java_runtime='0.2.0', protocol='v1/r19', unsigned_sha256=release.sha(apk))
        provenance = self.root / 'release-provenance.json'
        provenance.write_text(json.dumps(p))
        (self.root / 'unsigned-release.apk.sha256').write_text(p['unsigned_sha256'] + '  unsigned-release.apk\n')
        with patch.object(release, 'identity', return_value='b' * 64), patch.object(release, 'unsigned_check'):
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
                 valid.replace('v2): true', 'v2): false'), valid.replace('v3): true', 'v3): false')]
        for output in cases:
            with patch.dict('os.environ', {'ANDROID_HOME': '/pinned/sdk'}), patch.object(signed.subprocess, 'run', return_value=SimpleNamespace(stdout=output)):
                with self.assertRaises(ValueError): signed.verify('test.apk', p, '1.0.0')

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
        with patch.dict('os.environ', {'GITHUB_REPOSITORY': 'example/repo'}), patch.object(release, 'bundle_check', return_value=dict(signed_sha256=release.sha(apk), tag='v1.0.0', certificate_sha256='b' * 64)), patch.object(release, 'verify_signed'), patch.object(release, 'run', side_effect=fake_run):
            with self.assertRaisesRegex(ValueError, 'Remote release bytes'): release.publish(args)
        self.assertTrue(any(c[:3] == ('gh', 'release', 'create') and '--draft' in c for c in commands))
        self.assertFalse(any('PATCH' in c for c in commands))


if __name__ == '__main__':
    unittest.main()
