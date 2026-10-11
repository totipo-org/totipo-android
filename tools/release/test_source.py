"""Temporary Git repositories and bare remotes; no live refs or metadata touched."""
import contextlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import release
import source

REPO = Path(__file__).resolve().parents[2]


class SourceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.root = self.base / 'work'; self.root.mkdir()
        self.remote = self.base / 'remote.git'
        self.call('init', '--bare', str(self.remote))
        self.call('init', '-b', 'main')
        self.call('config', 'user.name', 'TEST release fixture')
        self.call('config', 'user.email', 'fixture@example.invalid')
        self.call('config', 'tag.gpgSign', 'true')  # Command must explicitly create an unsigned tag.
        self.call('remote', 'add', 'origin', str(self.remote))
        (self.root / 'app').mkdir()
        for v, c in [('0.1.0', 1), ('0.1.1', 2)]:
            self.metadata(v, c, legacy=True)
            self.commit()
            self.tag(v)
        self.metadata('0.1.1', 2)
        (self.root / 'build.gradle.kts').write_bytes((REPO / 'build.gradle.kts').read_bytes())
        self.commit()
        self.sync()
        self.previous = (release.ROOT, release.CERT, release.FP)
        self.addCleanup(self.restore)

    def restore(self):
        release.ROOT, release.CERT, release.FP = self.previous

    def call(self, *args):
        return subprocess.check_output(['git', *args], cwd=self.root, stderr=subprocess.DEVNULL).decode().strip()

    def metadata(self, v, c, legacy=False):
        (self.root / 'VERSION').write_text(v + '\n')
        if legacy:
            (self.root / 'app/build.gradle.kts').write_text(f'android {{\n versionCode = {c}\n}}\n')
        else:
            (self.root / 'VERSION_CODE').write_text(str(c) + '\n')
            (self.root / 'app/build.gradle.kts').write_bytes((REPO / 'app/build.gradle.kts').read_bytes())

    def commit(self):
        self.call('add', '.')
        self.call('commit', '-m', 'TEST fixture source')

    def tag(self, v):
        self.call('tag', '--no-sign', '-a', 'v' + v, '-m', 'Totipo Android ' + v)

    def sync(self):
        self.call('push', 'origin', 'main', '--tags')

    @contextlib.contextmanager
    def context(self, readiness=True):
        before = Path.cwd()
        os.chdir(self.root)
        try:
            with contextlib.ExitStack() as stack:
                if readiness:
                    stack.enter_context(patch.object(source, 'source_pins'))
                    stack.enter_context(patch.object(source, 'machinery_ready'))
                    stack.enter_context(patch.object(release, 'guard'))
                    stack.enter_context(patch.object(release, 'expected_identity', return_value='a' * 64))
                yield
        finally:
            os.chdir(before)

    def execute(self, command, target='0.1.2'):
        with self.context(): source.execute(command, target)

    def prepared(self, commit=False):
        self.execute('prepare-release')
        if commit:
            self.commit(); self.sync()

    def test_prepare_idempotent_exact_diff_no_git_side_effects(self):
        import io
        before = (self.call('rev-parse', 'HEAD'), self.call('show-ref'), self.call('ls-remote', 'origin'))
        output = io.StringIO()
        with contextlib.redirect_stdout(output): self.prepared()
        self.assertIn('VERSION=0.1.2; VERSION_CODE=3', output.getvalue())
        self.assertNotIn('check-release', output.getvalue())
        self.assertEqual((self.root / 'VERSION').read_bytes(), b'0.1.2\n')
        self.assertEqual((self.root / 'VERSION_CODE').read_bytes(), b'3\n')
        self.assertEqual(set(self.call('diff', '--name-only').splitlines()), source.METADATA)
        self.execute('prepare-release')
        self.assertEqual(before, (self.call('rev-parse', 'HEAD'), self.call('show-ref'), self.call('ls-remote', 'origin')))
        self.assertEqual(self.call('diff', '--cached', '--name-only'), '')
        snapshot = (self.call('status', '--porcelain'), self.call('show-ref'),
                    (self.root / 'VERSION').read_bytes(), (self.root / 'VERSION_CODE').read_bytes())
        self.execute('check-release')
        self.assertEqual(snapshot, (self.call('status', '--porcelain'), self.call('show-ref'),
                         (self.root / 'VERSION').read_bytes(), (self.root / 'VERSION_CODE').read_bytes()))
        self.commit()
        self.execute('check-release')

    def test_invalid_targets(self):
        for target in ('0.1.0', '0.1.1', '0.0.9', 'x', '01.1.2', '0.1.2-dev', '0.1.2+build', '0.1.2\n'):
            with self.subTest(target=target), self.assertRaises(ValueError): self.execute('prepare-release', target)
        self.assertEqual((self.root / 'VERSION').read_text(), '0.1.1\n')

    def test_dirty_and_staged_rejected(self):
        (self.root / 'other').write_text('unrelated')
        with self.assertRaises(ValueError): self.execute('prepare-release')
        (self.root / 'other').unlink()
        (self.root / 'build.gradle.kts').write_text('dirty')
        with self.assertRaises(ValueError): self.execute('prepare-release')
        self.call('restore', 'build.gradle.kts')
        self.prepared()
        self.call('add', 'VERSION')
        with self.assertRaisesRegex(ValueError, 'Staged'): self.execute('prepare-release')

    def test_check_wrong_metadata_dirty_and_missing_authority(self):
        self.prepared()
        for name, data in [('VERSION', b'0.1.3\n'), ('VERSION_CODE', b'4\n'), ('VERSION_CODE', b' 3\n')]:
            old = (self.root / name).read_bytes()
            (self.root / name).write_bytes(data)
            with self.assertRaises(ValueError): self.execute('check-release')
            (self.root / name).write_bytes(old)
        (self.root / 'VERSION_CODE').unlink()
        with self.assertRaises(ValueError): self.execute('check-release')
        (self.root / 'VERSION_CODE').write_text('3\n')
        (self.root / 'other').write_text('dirty')
        with self.assertRaises(ValueError): self.execute('check-release')

    def test_source_wiring_duplicate_authority_and_identity(self):
        self.prepared(commit=True)
        with self.context():
            source.worktree()
            source.source_ready()
            p = self.root / 'app/build.gradle.kts'
            original = p.read_text()
            for extra in ('\nversionCode = 99\n', '\nversionName = "other"\n', '\napplicationId = "other"\n'):
                p.write_text(original + extra)
                with self.assertRaises(ValueError): source.source_ready()
            p.write_text(original)
            with patch.object(release, 'expected_identity', side_effect=ValueError('Invalid public signer')):
                with self.assertRaisesRegex(ValueError, 'public signer'): source.source_ready()
        # Actual shared identity parser, no mock: missing and malformed certificate.
        with patch.object(release, 'CERT', self.root / 'cert'), patch.object(release, 'FP', self.root / 'fp'):
            with self.assertRaises(ValueError): release.expected_identity()
            release.CERT.write_text('bad'); release.FP.write_text('a' * 64)
            with self.assertRaises(ValueError): release.expected_identity()

    def test_history_legacy_new_format_and_overflow(self):
        with self.context():
            source.worktree()
            self.assertEqual(source.next_code('0.1.2', source.history(source.remote_refs())), 3)
        self.metadata('0.1.2', 3); self.commit(); self.tag('0.1.2'); self.sync()
        self.execute('prepare-release', '0.1.3')
        self.assertEqual((self.root / 'VERSION_CODE').read_text(), '4\n')
        self.call('restore', 'VERSION', 'VERSION_CODE')
        self.metadata('0.1.3', source.MAX_CODE); self.commit(); self.tag('0.1.3'); self.sync()
        with self.assertRaisesRegex(ValueError, 'overflow'): self.execute('prepare-release', '0.1.4')

    def test_invalid_tag_sources(self):
        for data in (b'bad\n', b'0.1.3\n', b'0.1.2', b'0.1.2\n\n'):
            (self.root / 'VERSION').write_bytes(data)
            (self.root / 'VERSION_CODE').write_text('3\n')
            self.commit(); self.tag('0.1.2'); self.sync()
            with self.assertRaisesRegex(ValueError, 'VERSION differs'): self.execute('prepare-release', '0.1.4')
            self.call('tag', '-d', 'v0.1.2'); self.call('push', 'origin', ':refs/tags/v0.1.2')

    def test_duplicate_lightweight_missing_ambiguous_code_and_malformed_tag(self):
        self.metadata('0.1.2', 2); self.commit(); self.tag('0.1.2'); self.sync()
        with self.assertRaisesRegex(ValueError, 'Duplicate'): self.execute('prepare-release', '0.1.3')
        self.call('tag', '-d', 'v0.1.2'); self.call('push', 'origin', ':refs/tags/v0.1.2')
        self.call('tag', '--no-sign', 'v0.1.2'); self.sync()
        with self.assertRaisesRegex(ValueError, 'Annotated'): self.execute('prepare-release', '0.1.3')
        self.call('tag', '-d', 'v0.1.2'); self.call('push', 'origin', ':refs/tags/v0.1.2')
        for script in ('nothing', 'versionCode = 3\nversionCode = 4\n'):
            (self.root / 'VERSION_CODE').unlink(missing_ok=True)
            (self.root / 'app/build.gradle.kts').write_text(script)
            self.commit(); self.tag('0.1.2'); self.sync()
            # Migrate current source so the test reaches the historical parser.
            self.metadata('0.1.2', 3); self.commit(); self.sync()
            with self.assertRaisesRegex(ValueError, 'legacy'): self.execute('prepare-release', '0.1.3')
            self.call('tag', '-d', 'v0.1.2'); self.call('push', 'origin', ':refs/tags/v0.1.2')
        self.call('tag', '--no-sign', '-a', 'v0.1.2-dev', '-m', 'bad'); self.sync()
        with self.assertRaisesRegex(ValueError, 'canonical'): self.execute('prepare-release', '0.1.3')

    def test_new_tag_invalid_code_never_uses_legacy_fallback(self):
        self.metadata('0.1.2', 3)
        (self.root / 'app/build.gradle.kts').write_text('versionCode = 3\n')
        (self.root / 'VERSION_CODE').write_bytes(b' 3\n')
        self.commit(); self.tag('0.1.2'); self.sync()
        self.metadata('0.1.2', 3); self.commit(); self.sync_main_only()
        with self.assertRaisesRegex(ValueError, 'Malformed VERSION_CODE'):
            self.execute('prepare-release', '0.1.3')

    def test_tag_must_peel_to_commit(self):
        tree = self.call('rev-parse', 'HEAD^{tree}')
        self.call('tag', '--no-sign', '-a', 'v0.1.2', tree, '-m', 'Invalid tree release')
        self.sync()
        with self.assertRaises(subprocess.CalledProcessError):
            self.execute('prepare-release', '0.1.3')

    def test_stale_missing_and_changed_remote_tags(self):
        self.call('tag', '-d', 'v0.1.1')
        with self.assertRaisesRegex(ValueError, 'tags differ'): self.execute('prepare-release')
        self.call('fetch', 'origin', 'refs/tags/v0.1.1:refs/tags/v0.1.1')
        self.call('tag', '-d', 'v0.1.1'); self.tag('0.1.1')
        with self.assertRaisesRegex(ValueError, 'tags differ'): self.execute('prepare-release')

    def coordinates(self, json_output=False):
        import io
        output = io.StringIO()
        with self.context(), contextlib.redirect_stdout(output):
            source.execute('release-tag', '0.1.2', json_output)
        return output.getvalue()

    def assert_reject_unchanged(self):
        before = (self.call('show-ref'), self.call('ls-remote', 'origin'))
        with self.assertRaises((ValueError, subprocess.CalledProcessError)):
            self.execute('release-tag')
        self.assertEqual(before, (self.call('show-ref'), self.call('ls-remote', 'origin')))

    def test_release_tag_create_push_coordinates_repeat_and_settings(self):
        import json
        self.prepared(commit=True)
        head = self.call('rev-parse', 'HEAD')
        before = set(self.call('ls-remote', 'origin').splitlines())
        self.call('tag', '--no-sign', '-a', 'unrelated-fixture-tag', '-m', 'Must stay local')
        self.call('config', 'push.followTags', 'true')
        self.call('config', 'remote.origin.mirror', 'true')
        self.call('config', 'remote.origin.push', '+refs/*:refs/*')
        output = self.coordinates()
        self.assertIn('source_commit: ' + head, output)
        self.assertIn('version: 0.1.2', output)
        self.assertIn('versionCode: 3', output)
        self.assertIn('tag: v0.1.2', output)
        self.assertIn('GitHub Release workflow inputs:', output)
        after = set(self.call('ls-remote', 'origin').splitlines())
        self.assertEqual(after - before, {
            self.call('rev-parse', 'v0.1.2') + '\trefs/tags/v0.1.2',
            head + '\trefs/tags/v0.1.2^{}'})
        self.assertFalse(before - after)
        self.assertEqual(self.call('cat-file', '-t', 'v0.1.2'), 'tag')
        self.assertEqual(self.call('for-each-ref', '--format=%(contents)', 'refs/tags/v0.1.2'),
                         'Totipo Android 0.1.2')
        refs = self.call('show-ref')
        self.assertEqual(output, self.coordinates())
        self.assertEqual(json.loads(self.coordinates(True)), dict(version='0.1.2', version_code=3,
                         tag='v0.1.2', source_commit=head))
        self.assertEqual(refs, self.call('show-ref'))
        self.assertEqual(after, set(self.call('ls-remote', 'origin').splitlines()))

    def test_release_tag_local_only_resumes(self):
        self.prepared(commit=True); self.tag('0.1.2')
        oid = self.call('rev-parse', 'v0.1.2')
        self.coordinates()
        self.assertIn(oid + '\trefs/tags/v0.1.2', self.call('ls-remote', 'origin'))

    def test_release_tag_dirty_staged_and_wrong_metadata(self):
        self.prepared(commit=True)
        (self.root / 'build.gradle.kts').write_text('dirty')
        self.assert_reject_unchanged()
        self.call('add', 'build.gradle.kts')
        self.assert_reject_unchanged()
        self.call('reset', '--hard', 'HEAD')
        for name, value in [('VERSION', '0.1.3\n'), ('VERSION_CODE', '4\n')]:
            old = (self.root / name).read_text()
            (self.root / name).write_text(value); self.commit(); self.sync_main_only()
            self.assert_reject_unchanged()
            (self.root / name).write_text(old); self.commit(); self.sync_main_only()

    def test_release_tag_tracking_and_live_main_mismatch(self):
        self.prepared(commit=True)
        head = self.call('rev-parse', 'HEAD')
        parent = self.call('rev-parse', 'HEAD^')
        self.call('update-ref', 'refs/remotes/origin/main', parent)
        self.assert_reject_unchanged()
        self.call('update-ref', 'refs/remotes/origin/main', head)
        subprocess.run(['git', '--git-dir=' + str(self.remote), 'update-ref', 'refs/heads/main', parent], check=True)
        self.assert_reject_unchanged()

    def test_release_tag_conflicting_tags_no_force(self):
        self.prepared(commit=True)
        head = self.call('rev-parse', 'HEAD')
        for args in [('--no-sign', 'v0.1.2'),
                     ('--no-sign', '-a', 'v0.1.2', '-m', 'Wrong message'),
                     ('--no-sign', '-a', 'v0.1.2', 'HEAD^', '-m', 'Totipo Android 0.1.2')]:
            self.call('tag', *args)
            self.assert_reject_unchanged()
            self.call('push', '--no-follow-tags', 'origin', 'refs/tags/v0.1.2:refs/tags/v0.1.2')
            self.assert_reject_unchanged()
            self.call('tag', '-d', 'v0.1.2')
            self.tag('0.1.2')  # Correct local tag must not overwrite bad remote.
            self.call('config', 'push.followTags', 'true')
            self.call('config', 'remote.origin.push', '+refs/tags/*:refs/tags/*')
            self.assert_reject_unchanged()
            self.call('tag', '-d', 'v0.1.2')
            self.assert_reject_unchanged()  # Remote-only is never silently adopted.
            self.call('push', '--no-follow-tags', 'origin', ':refs/tags/v0.1.2')
        self.assertEqual(self.call('rev-parse', 'HEAD'), head)

    def test_release_tag_remote_message_and_post_push_drift(self):
        self.prepared(commit=True)
        self.tag('0.1.2')
        with self.context():
            source.worktree()
            remote = source.remote_refs()
            oid = self.call('rev-parse', 'v0.1.2')
            head = self.call('rev-parse', 'HEAD')
            self.call('push', 'origin', 'refs/tags/v0.1.2:refs/tags/v0.1.2')
            remote = source.remote_refs()
            source.validate_remote_tag(remote, 'refs/tags/v0.1.2', '0.1.2', head, 3, oid)
            with self.assertRaisesRegex(ValueError, 'shape'):
                source.validate_remote_tag(remote, 'refs/tags/v0.1.2', '0.1.3', head, 3, oid)
            with self.assertRaisesRegex(ValueError, 'metadata'):
                source.validate_remote_tag(remote, 'refs/tags/v0.1.2', '0.1.2', head, 4, oid)
        self.call('push', 'origin', ':refs/tags/v0.1.2')
        original = source.git
        def drift(*args):
            result = original(*args)
            if 'push' in args:
                subprocess.run(['git', '--git-dir=' + str(self.remote), 'update-ref',
                                'refs/heads/main', head + '^'], check=True)
            return result
        with self.context(), patch.object(source, 'git', side_effect=drift):
            with self.assertRaisesRegex(ValueError, 'origin/main'):
                source.execute('release-tag', '0.1.2')

    def test_release_tag_readiness_and_push_endpoint_fail_before_mutation(self):
        self.prepared(commit=True)
        with self.context(), patch.object(source, 'source_ready', side_effect=ValueError('readiness failed')):
            self.assert_reject_unchanged()
        self.call('config', 'remote.origin.pushurl', str(self.base / 'elsewhere.git'))
        self.assert_reject_unchanged()

    def test_release_tag_remote_race_cannot_force_move_tag(self):
        self.prepared(commit=True)
        self.call('tag', '--no-sign', '-a', 'conflicting-fixture', 'HEAD^', '-m', 'Wrong source')
        wrong = self.call('rev-parse', 'conflicting-fixture')
        # Transfer the object without making it a release tag until the push race.
        self.call('push', 'origin', 'refs/tags/conflicting-fixture:refs/tags/conflicting-fixture')
        self.call('config', 'push.followTags', 'true')
        self.call('config', 'remote.origin.mirror', 'true')
        self.call('config', 'remote.origin.push', '+refs/*:refs/*')
        original = source.git
        def race(*args):
            if 'push' in args:
                subprocess.run(['git', '--git-dir=' + str(self.remote), 'update-ref',
                                'refs/tags/v0.1.2', wrong], check=True)
            return original(*args)
        with self.context(), patch.object(source, 'git', side_effect=race):
            with self.assertRaises(subprocess.CalledProcessError):
                source.execute('release-tag', '0.1.2')
        self.assertIn(wrong + '\trefs/tags/v0.1.2', self.call('ls-remote', 'origin'))
        self.assertNotEqual(wrong, self.call('rev-parse', 'v0.1.2'))

    def test_release_tag_unreadable_remote_object_fails_without_mutation(self):
        self.prepared(commit=True); self.coordinates()
        with patch.object(source, 'validate_remote_tag', side_effect=ValueError('Remote unavailable')):
            self.assert_reject_unchanged()

    def sync_main_only(self):
        self.call('push', '--no-follow-tags', 'origin', 'main')

    def test_caller_worktree_branch_and_store_boundary(self):
        self.call('switch', '-c', 'other')
        with self.assertRaisesRegex(ValueError, 'main'): self.execute('prepare-release')
        with patch.object(source.subprocess, 'check_output', return_value='/nix/store/fixture\n'):
            with self.assertRaisesRegex(ValueError, 'Nix-store'): source.worktree()
        # Executable source is outside the fixture; all preceding operations still
        # modified only the caller's temporary repository.
        self.assertNotEqual(self.root, Path(source.__file__).resolve().parents[2])
        flake = REPO / 'flake.nix'
        if flake.exists():
            text = flake.read_text()
            self.assertIn('exec python3 -B ${./tools/release}/release.py ${name} "$@"', text)
            self.assertIn('export PATH=${pkgs.lib.makeBinPath', text)
            self.assertNotIn('cd ${', text)
            self.assertIn('releaseCommands = [ "prepare-release" "release-tag" ];', text)

    def test_real_cli_outside_worktree_with_public_identity(self):
        import re
        import sys
        if not (REPO / 'release/signing/android-release-cert.pem').is_file():
            self.skipTest('Public identity/workflow/evaluator inputs excluded from Android package source')
        guard_path = 'app/src/test/java/org/totipo/android/OtpAuthSupplyChainGuardTest.java'
        guard_text = (REPO / guard_path).read_text()
        names = re.findall(r'Map.entry\("([^\"]+)", "[0-9a-f]{64}"\)', guard_text)
        names += [guard_path, '.github/workflows/release.yml',
                  'release/signing/README.md', 'release/signing/android-release-cert.pem',
                  'release/signing/android-release-cert.sha256', 'tools/verify-apk.py']
        names += [str(p.relative_to(REPO)) for p in (REPO / 'tools/release').glob('*.py')]
        for name in names:
            dest = self.root / name
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes((REPO / name).read_bytes())
        self.commit(); self.sync_main_only()
        before = (self.call('rev-parse', 'HEAD'), self.call('show-ref'), self.call('ls-remote', 'origin'))
        for command in ('prepare-release', 'prepare-release', 'check-release'):
            subprocess.run([sys.executable, '-B', str(REPO / 'tools/release/release.py'), command, '0.1.2'],
                           cwd=self.root, check=True, stdout=subprocess.DEVNULL)
        self.assertEqual((self.root / 'VERSION_CODE').read_bytes(), b'3\n')
        self.assertEqual(before, (self.call('rev-parse', 'HEAD'), self.call('show-ref'), self.call('ls-remote', 'origin')))
        self.assertEqual(set(self.call('diff', '--name-only').splitlines()), source.METADATA)
        self.commit(); self.sync_main_only()
        for command in ('release-tag', 'release-tag'):
            result = subprocess.run([sys.executable, '-B', str(REPO / 'tools/release/release.py'), command, '0.1.2'],
                                    cwd=self.root, check=True, capture_output=True, text=True)
            self.assertIn('source_commit: ' + self.call('rev-parse', 'HEAD'), result.stdout)
            self.assertIn('GitHub Release workflow inputs:', result.stdout)
        import json
        result = subprocess.run([sys.executable, '-B', str(REPO / 'tools/release/release.py'),
                                 'release-tag', '0.1.2', '--json'], cwd=self.root, check=True,
                                capture_output=True, text=True)
        self.assertEqual(json.loads(result.stdout)['source_commit'], self.call('rev-parse', 'HEAD'))
        self.assertIn('refs/tags/v0.1.2', self.call('ls-remote', 'origin'))

    def test_code_canonical_parser(self):
        self.assertEqual(source.code(b'2\n'), 2)
        for data in (b'', b'2', b' 2\n', b'2 \n', b'2\r\n', b'+2\n', b'-2\n', b'2.0\n',
                     b'02\n', b'0\n', b'2100000001\n', b'999999999999999999999999999\n'):
            with self.subTest(data=data), self.assertRaises(ValueError): source.code(data)


class SourcePinTests(unittest.TestCase):
    def test_shared_pin_inventory_boundary(self):
        import hashlib
        with tempfile.TemporaryDirectory() as tmp, patch.object(release, 'ROOT', Path(tmp)):
            root = Path(tmp)
            inventory = root / 'app/src/test/java/org/totipo/android/OtpAuthSupplyChainGuardTest.java'
            inventory.parent.mkdir(parents=True)
            names = ['app/build.gradle.kts'] + [f'protected-{n}' for n in range(11)]
            entries = []
            for name in names:
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(b'reviewed build/plugin/dependency/repository/security logic')
                entries.append('Map.entry("' + name + '", "' + release.sha(path) + '")')
            inventory.write_text('\n'.join(entries))
            for data in (b'2\n', b'3\n'):
                (root / 'VERSION_CODE').write_bytes(data)
                source.source_pins()
            for name in names:
                path = root / name
                original = path.read_bytes(); path.write_bytes(original + b'unreviewed change')
                with self.assertRaisesRegex(ValueError, 'pin differs'): source.source_pins()
                path.write_bytes(original)
