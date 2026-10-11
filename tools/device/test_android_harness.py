"""Behavioral host contracts; no Android scenario reimplementation."""
from pathlib import Path
import os
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import android_harness as h


class HarnessTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.tc = h.AndroidToolchain(self.root / 'sdk', self.root / 'jdk/bin')
        for path in [self.tc.sdk / 'platforms/android-37.0/android.jar',
                     *[self.tc.bt / name for name in ('d8', 'aapt2', 'zipalign', 'apksigner')],
                     *[self.tc.java / name for name in ('javac', 'keytool')]]:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text('fixture')
            path.chmod(0o755)
        self.source = self.root / 'A.java'
        self.source.write_text('class A {}')
        self.apk = self.root / 'test.apk'
        with zipfile.ZipFile(self.apk, 'w') as archive:
            archive.writestr('AndroidManifest.xml', b'manifest')

    def test_missing_environment_and_tools(self):
        with patch.dict(os.environ, {}, clear=True), self.assertRaises(SystemExit):
            h.AndroidToolchain.discover()
        for path, action in [(self.tc.java / 'javac', lambda: self.tc.tool('javac')),
                             (self.tc.bt / 'd8', lambda: self.tc.tool('d8')),
                             (self.tc.sdk / 'platforms/android-37.0/android.jar', lambda: self.tc.platform)]:
            path.unlink()
            with self.subTest(path=path), self.assertRaises(SystemExit):
                action()

    def test_missing_source_or_class_fails_before_subprocess(self):
        with patch.object(h.subprocess, 'run') as tool:
            with self.assertRaises(SystemExit):
                h.compile_java(self.tc, [self.root / 'missing.java'], self.root / 'classes', [], ['A'])
            with self.assertRaises(SystemExit):
                h.dex_classes(self.tc, [self.root / 'missing.class'], self.root / 'dex')
            tool.assert_not_called()

    def test_fresh_explicit_class_family(self):
        classes = self.root / 'classes'
        classes.mkdir()
        (classes / 'A$Stale.class').write_bytes(b'stale')
        (classes / 'Other.class').write_bytes(b'unrelated')
        def javac(args):
            output = Path(args[args.index('-d') + 1])
            (output / 'A.class').write_bytes(b'top')
            (output / 'A$Inner.class').write_bytes(b'inner')
            (output / 'Unselected.class').write_bytes(b'other')
        with patch.object(h, 'run', side_effect=javac):
            selected = h.compile_java(self.tc, [self.source], classes, [], ['A'])
        self.assertEqual(['A.class', 'A$Inner.class'], [p.name for p in selected])
        self.assertFalse((classes / 'A$Stale.class').exists())
        self.assertFalse((classes / 'Unselected.class').exists())
        self.assertTrue((classes / 'Other.class').exists())

    def test_missing_expected_compiler_output(self):
        with patch.object(h, 'run'), self.assertRaises(SystemExit):
            h.compile_java(self.tc, [self.source], self.root / 'classes', [], ['A'])

    def test_zip_inputs_and_duplicates(self):
        before = self.apk.read_bytes()
        for entries in [[(self.source, 'AndroidManifest.xml')], [(self.source, 'x'), (self.source, 'x')],
                        [(self.root / 'missing', 'x')]]:
            with self.subTest(entries=entries), self.assertRaises(SystemExit):
                h.append_entries(self.apk, entries)
            self.assertEqual(before, self.apk.read_bytes())
        h.append_entries(self.apk, [(self.source, 'assets/source')])
        with zipfile.ZipFile(self.apk) as archive:
            self.assertEqual(self.source.read_bytes(), archive.read('assets/source'))
        with self.assertRaises(SystemExit):
            h.copy_apk_entries(self.apk, self.apk, ['AndroidManifest.xml'])

    def test_failed_tools_redact_output(self):
        for name in ('d8', 'aapt2', 'apksigner', 'zipalign'):
            with self.subTest(name=name), patch.object(h.subprocess, 'run', return_value=subprocess.CompletedProcess([], 1, 'PRIVATE', 'PRIVATE')):
                with self.assertRaises(SystemExit) as caught:
                    h.run([self.tc.tool(name)])
                self.assertIn(name, str(caught.exception))
                self.assertNotIn('PRIVATE', str(caught.exception))

    def test_signer_verification(self):
        expected = 'a' * 64
        execute = lambda args: 'Signer #1 certificate SHA-256 digest: ' + expected + '\n'
        h.verify_signer('apksigner', self.apk, expected, execute)
        with self.assertRaises(SystemExit):
            h.verify_signer('apksigner', self.apk, 'b' * 64, execute)
        for text in ('no signer', execute(None) * 2):
            with self.assertRaises(SystemExit):
                h.certificate_digest('apksigner', self.apk, lambda args: text)

    def test_provider_restoration_after_scenario_failure(self):
        remote = self.root / 'remote'
        (remote / 'objects-v1').mkdir(parents=True)
        (remote / 'vault').write_bytes(b'original')
        (remote / 'objects-v1/object').write_bytes(b'object')
        import shutil
        def execute(args):
            if args[1] == 'pull': shutil.copytree(remote, args[3])
            elif args[1] == 'push':
                source, target = Path(args[2]), Path(args[3])
                if source.is_dir(): shutil.copytree(source, target)
                else: shutil.copyfile(source, target)
            elif args[2] == 'rm': shutil.rmtree(remote / 'objects-v1')
            return ''
        original = h.file_hashes(remote)
        with self.assertRaisesRegex(RuntimeError, 'scenario'):
            with h.provider_backup(execute, str(remote), self.root, ['vault', 'objects-v1']):
                (remote / 'vault').write_bytes(b'changed')
                raise RuntimeError('scenario')
        self.assertEqual(original, h.file_hashes(remote))
        self.assertTrue((self.root / 'fixture-restoration.txt').exists())


if __name__ == '__main__':
    unittest.main()
