"""Disposable keys only; real pinned keytool/apksigner plus focused failure injection."""
import ast
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import shutil
import tempfile
import unittest
from unittest.mock import patch

import android_harness as h
import qualification_signing as q


class IdentityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tc = h.AndroidToolchain.discover()
        cls.keys = tempfile.TemporaryDirectory()
        cls.fixture = Path(cls.keys.name)
        cls.key = cls.fixture / 'disposable.p12'
        cls.pw = cls.fixture / 'password'
        cls.pw.write_bytes(b'disposable-test-password\n')
        h.run([cls.tc.tool('keytool'), '-genkeypair', '-storetype', 'PKCS12',
               '-keystore', cls.key, '-storepass:file', cls.pw, '-keypass:file', cls.pw,
               '-alias', 'wave2a-qualification', '-keyalg', 'RSA', '-keysize', '2048',
               '-sigalg', 'SHA256withRSA', '-validity', '2', '-dname', 'CN=Disposable signing test'])
        cls.alias, cls.digest = q.inspect(cls.tc, cls.key, cls.pw)

    @classmethod
    def tearDownClass(cls):
        cls.keys.cleanup()

    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        public = self.root / 'release/signing'
        public.mkdir(parents=True)
        (public / 'android-release-cert.sha256').write_text('0' * 64 + '\n')
        self.directory = self.root / '.qualification/signing'
        self.source = self.root / 'old.p12'
        self.password = self.root / 'old-password'
        shutil.copyfile(self.key, self.source)
        shutil.copyfile(self.pw, self.password)

    def adopt(self):
        # Test-only expected certificate substitution; fixture is disposable.
        with patch.object(q, 'WAVE2A', self.digest):
            return q.migrate(self.source, self.password, self.root, self.tc)

    def load(self):
        return q.load(self.root, self.tc)

    def change_metadata(self, **changes):
        p = self.directory / 'identity.json'
        value = json.loads(p.read_text())
        value.update(changes)
        p.write_text(json.dumps(value))

    def test_migration_exact_bytes_password_modes_retirement_and_status(self):
        key, password = self.source.read_bytes(), self.password.read_bytes()
        output = io.StringIO()
        with contextlib.redirect_stdout(output), contextlib.redirect_stderr(output):
            identity = self.adopt()
        self.assertNotIn(password.decode().strip(), output.getvalue())
        self.assertEqual(key, identity.keystore.read_bytes())
        self.assertEqual(password, identity.password.read_bytes())
        self.assertFalse(self.source.exists())
        self.assertFalse(self.password.exists())
        self.assertEqual(identity, self.load())
        self.assertEqual(self.digest, identity.fingerprint)
        for p in [self.directory.parent, self.directory]:
            self.assertEqual(0o700, p.stat().st_mode & 0o7777)
        for p in self.directory.iterdir():
            self.assertEqual(0o600, p.stat().st_mode & 0o7777)

    def test_missing_and_partial_never_fall_back(self):
        historical = self.root / '.gradle/m1l-inspection'
        historical.mkdir(parents=True)
        shutil.copyfile(self.key, historical / 'm1k-debug.keystore')
        home = self.root / 'home/.android'
        home.mkdir(parents=True)
        shutil.copyfile(self.key, home / 'debug.keystore')
        with patch.dict(os.environ, {'HOME': str(home.parent), 'XDG_CONFIG_HOME': str(home.parent)}):
            with self.assertRaises(SystemExit): self.load()
            self.directory.mkdir(parents=True, mode=0o700)
            self.directory.parent.chmod(0o700)
            with self.assertRaises(SystemExit): self.load()

    def test_each_missing_file(self):
        self.adopt()
        for name in ('password', 'keystore.p12', 'identity.json'):
            p = self.directory / name
            data = p.read_bytes()
            p.unlink()
            with self.subTest(name=name), self.assertRaises(SystemExit): self.load()
            p.write_bytes(data)
            p.chmod(0o600)

    def test_strict_json(self):
        self.adopt()
        p = self.directory / 'identity.json'
        original = p.read_text()
        value = json.loads(original)
        cases = ['{', '[]', original[:-2] + ', "schema":1}',
                 json.dumps({**value, 'extra': 1}),
                 *[json.dumps({k: v for k, v in value.items() if k != field}) for field in value],
                 *[json.dumps({**value, 'schema': v}) for v in (True, 2, '1')],
                 json.dumps({**value, 'purpose': 'production'}),
                 *[json.dumps({**value, 'alias': v}) for v in ('', ' ', None)],
                 *[json.dumps({**value, 'certificateSha256': v}) for v in ('a'*63, 'A'*64, 1, 'z'*64)]]
        for text in cases:
            p.write_text(text)
            with self.subTest(text=text), self.assertRaises(SystemExit): self.load()
        p.write_text(original)
        self.load()

    def test_fingerprint_and_alias_mismatch(self):
        self.adopt()
        for changes in ({'alias': 'wrong'}, {'certificateSha256': 'a'*64}):
            self.change_metadata(alias=self.alias, certificateSha256=self.digest)
            self.change_metadata(**changes)
            with self.subTest(changes=changes), self.assertRaises(SystemExit): self.load()

    def test_symlink_files_and_components(self):
        self.adopt()
        for p in [*self.directory.iterdir(), self.directory, self.directory.parent]:
            moved = p.with_name(p.name + '-moved')
            p.rename(moved)
            p.symlink_to(moved, target_is_directory=moved.is_dir())
            with self.subTest(path=p), self.assertRaises(SystemExit): self.load()
            p.unlink()
            moved.rename(p)
        self.load()

    def test_unsafe_modes_and_owner(self):
        self.adopt()
        for p in [*self.directory.iterdir(), self.directory, self.directory.parent]:
            original = p.stat().st_mode & 0o7777
            p.chmod(0o755 if p.is_dir() else 0o644)
            with self.subTest(path=p), self.assertRaises(SystemExit): self.load()
            p.chmod(original)
        with patch.object(q.os, 'getuid', return_value=os.getuid()+1), self.assertRaises(SystemExit):
            self.load()
        with patch.object(q.os, 'name', 'nt'), self.assertRaises(SystemExit): self.load()

    def test_destination_exists_including_partial(self):
        self.directory.mkdir(parents=True)
        with self.assertRaises(SystemExit): self.adopt()
        self.assertTrue(self.source.exists())
        self.assertTrue(self.password.exists())

    def test_wrong_migration_certificate_and_alias(self):
        with self.assertRaises(SystemExit): q.migrate(self.source, self.password, self.root, self.tc)
        with patch.object(q, 'inspect', return_value=('wrong', self.digest)), self.assertRaises(SystemExit): self.adopt()
        self.assertFalse(self.directory.exists())
        self.assertTrue(self.source.exists())

    def test_source_symlinks_and_unreadable(self):
        self.source.unlink()
        self.source.symlink_to(self.key)
        with self.assertRaises(SystemExit): self.adopt()
        self.source.unlink()
        with self.assertRaises(SystemExit): self.adopt()

    def test_wrong_password_and_non_pkcs12(self):
        self.password.write_text('incorrect')
        with self.assertRaises(SystemExit): self.adopt()
        self.assertTrue(self.source.exists())
        self.password.write_bytes(self.pw.read_bytes())
        self.source.unlink()
        h.run([self.tc.tool('keytool'), '-genkeypair', '-storetype', 'JKS', '-keystore', self.source,
               '-storepass:file', self.password, '-keypass:file', self.password, '-alias', 'test',
               '-keyalg', 'RSA', '-validity', '2', '-dname', 'CN=Disposable JKS'])
        with self.assertRaises(SystemExit): q.inspect(self.tc, self.source, self.password)
        with self.assertRaises(SystemExit): self.adopt()

    def test_multiple_entries(self):
        h.run([self.tc.tool('keytool'), '-genkeypair', '-storetype', 'PKCS12', '-keystore', self.source,
               '-storepass:file', self.password, '-keypass:file', self.password, '-alias', 'second',
               '-keyalg', 'RSA', '-validity', '2', '-dname', 'CN=Disposable second'])
        with self.assertRaises(SystemExit): self.adopt()

    def test_production_fingerprint_refusal(self):
        self.adopt()
        (self.root / 'release/signing/android-release-cert.sha256').write_text(self.digest+'\n')
        with self.assertRaisesRegex(SystemExit, 'production certificate forbidden'): self.load()

    def test_failed_stage_keeps_old_authority(self):
        with patch.object(q, '_load', side_effect=SystemExit('test failure')), self.assertRaises(SystemExit): self.adopt()
        self.assertFalse(self.directory.exists())
        self.assertTrue(self.source.exists())
        self.assertTrue(self.password.exists())

    def test_retirement_failure_keeps_valid_destination(self):
        original = Path.unlink
        def fail(p, *args, **kw):
            if p == self.password: raise PermissionError('injected')
            return original(p, *args, **kw)
        with patch.object(Path, 'unlink', fail), self.assertRaisesRegex(SystemExit, 'duplicate old private material'):
            self.adopt()
        self.load()
        self.assertFalse(self.source.exists())
        self.assertTrue(self.password.exists())
        with self.assertRaises(SystemExit): self.adopt()

    def test_actual_signing_and_alignment(self):
        identity = self.adopt()
        manifest = self.root / 'AndroidManifest.xml'
        manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.totipo.disposablesigningtest"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="37"/><application /></manifest>')
        unsigned, aligned, signed = [self.root / (s+'.apk') for s in ('unsigned', 'aligned', 'signed')]
        h.link_manifest(self.tc, manifest, unsigned)
        identity.sign(self.tc, unsigned, aligned, signed)
        identity.verify(self.tc, signed)
        info = h.run([self.tc.tool('apksigner'), 'verify', '--verbose', signed])
        self.assertIn('Verified using v1 scheme (JAR signing): false', info)
        self.assertIn('Verified using v2 scheme (APK Signature Scheme v2): true', info)
        self.assertIn('Verified using v3 scheme (APK Signature Scheme v3): true', info)
        self.assertFalse(signed.with_suffix('.apk.idsig').exists())
        with self.assertRaises(SystemExit): h.verify_signer(self.tc.tool('apksigner'), signed, 'a'*64)

    def test_invocation_and_changed_identity(self):
        identity = self.adopt()
        source, aligned, signed = [self.root / (s+'.apk') for s in ('unsigned','aligned','signed')]
        source.write_bytes(b'fake apk')
        with patch.object(q, 'run', side_effect=lambda args: h.run(args) if Path(args[0]).name == 'keytool' else '') as execute, patch.object(q.Identity, 'verify') as verify:
            identity.sign(self.tc, source, aligned, signed)
            calls = [c.args[0] for c in execute.call_args_list]
            signing = next(c for c in calls if 'sign' in c)
            self.assertEqual(identity.alias, signing[signing.index('--ks-key-alias')+1])
            self.assertEqual('file:'+str(identity.password), signing[signing.index('--ks-pass')+1])
            self.assertEqual('false', signing[signing.index('--v1-signing-enabled')+1])
            self.assertEqual('false', signing[signing.index('--v4-signing-enabled')+1])
            self.assertEqual(['-f', '-P', '16', '4'], calls[-3][1:5])
            self.assertEqual(['-c', '-P', '16', '4'], calls[-1][1:5])
            verify.assert_called_once_with(self.tc, signed)
        self.change_metadata(alias='other')
        with self.assertRaises(SystemExit): identity.sign(self.tc, source, aligned, signed)


class CallerTest(unittest.TestCase):
    def test_all_supported_callers_use_owner_without_historical_authority(self):
        for name in ('qualify-saf-outbound.py', 'qualify-saf-bootstrap.py', 'qualify-syncthing-e2e.py'):
            source = Path(__file__).with_name(name).read_text()
            tree = ast.parse(source)
            self.assertIn('from qualification_signing import load', source)
            self.assertNotIn('m1l-inspection', source)
            self.assertNotIn('m1m-inspection', source)
            self.assertNotIn('m3c-saf/release-signed.apk', source)
            self.assertNotIn("'--ks'", source)
            self.assertIn('identity.sign(tc, source, aligned, signed)', source)
            self.assertTrue(any(isinstance(n, ast.Call) and isinstance(n.func, ast.Name) and n.func.id == 'load' for n in ast.walk(tree)))
            self.assertLess(source.index('identity.verify(tc,'), source.index("'install'"))

    def test_m3d_mismatch_blocks_all_installs(self):
        spec = importlib.util.spec_from_file_location('m3d_signing_test', Path(__file__).with_name('qualify-syncthing-e2e.py'))
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        from unittest.mock import Mock
        identity = Mock()
        for failure_call in (1, 2, 3):
            identity.verify.side_effect = [None]*(failure_call-1)+[SystemExit('mismatch')]
            with patch.object(module, 'load', return_value=identity), patch.object(module, 'run', return_value='package:/data/app/base.apk') as execute:
                with self.assertRaises(SystemExit): module.install()
                self.assertFalse(any('install' in c.args[0] for c in execute.call_args_list))

    def test_saf_signer_mismatch_blocks_all_installs(self):
        from unittest.mock import Mock
        import subprocess
        for name in ('outbound', 'bootstrap'):
            path = Path(__file__).with_name('qualify-saf-' + name + '.py')
            tree = ast.parse(path.read_text())
            # Execute the actual orchestration through signing/install, stopping
            # before any scenario body. All device subprocesses are captured.
            cut = next(i for i, n in enumerate(tree.body)
                       if isinstance(n, ast.Assign) and any(isinstance(t, ast.Name) and t.id == 'component' for t in n.targets))
            tree.body = tree.body[:cut]
            for fail_sign in (1, 2):
                identity = Mock()
                identity.sign.side_effect = [None]*(fail_sign-1)+[SystemExit('mismatch')]
                with patch.object(q, 'load', return_value=identity), patch.object(subprocess, 'run', return_value=subprocess.CompletedProcess([], 0, 'package:/data/app/base.apk', '')) as execute:
                    with self.assertRaises(SystemExit):
                        exec(compile(tree, str(path), 'exec'), {'__file__': str(path.absolute())})
                    self.assertFalse(any('install' in c.args[0] for c in execute.call_args_list))

    def test_generic_cli_uses_same_owner(self):
        from unittest.mock import Mock
        identity = Mock()
        with patch('sys.argv', ['qualification_signing.py', 'sign', 'input.apk', '--aligned', 'aligned.apk', '--out', 'signed.apk']), patch.object(q, 'load', return_value=identity) as load, contextlib.redirect_stdout(io.StringIO()):
            q.main()
            load.assert_called_once()
            identity.sign.assert_called_once()
            self.assertEqual((Path('input.apk'), Path('aligned.apk'), Path('signed.apk')), identity.sign.call_args.args[1:])


if __name__ == '__main__':
    unittest.main()
