"""No-network policy checks, including unsupported declaration refusal."""
import ast
from dataclasses import FrozenInstanceError
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import release
import source


class PolicyTests(unittest.TestCase):
    def test_canonical_version_owner(self):
        for value in ('0.1.2', '0.0.0', '12.34.56'):
            self.assertEqual(source.version(value), tuple(map(int, value.split('.'))))
        for value in ('01.1.2', '1.01.2', '1.2.03', '1.2.3-rc1', '1.2.3+build',
                      '1.2.3\n', ' 1.2.3', '1.2', None, 123):
            with self.subTest(value=value), self.assertRaises(ValueError): source.version(value)
        # Dispatch and provenance must call this same policy, not neighboring regexes.
        with patch.object(source, 'version', side_effect=ValueError('canonical policy')):
            with self.assertRaisesRegex(ValueError, 'canonical policy'):
                source.identity(source.Repository(Path('/unused')), '1.2.3', 'a' * 40)
        with patch.object(release, 'version', side_effect=ValueError('canonical policy')):
            p = {k: 'fixture' for k in release.PROVENANCE_FIELDS}; p['version_code'] = 1
            with self.assertRaisesRegex(ValueError, 'canonical policy'): release.validate_provenance(p)

    def test_context_is_immutable_and_repositories_do_not_leak(self):
        with tempfile.TemporaryDirectory() as tmp:
            roots = [Path(tmp) / name for name in ('one', 'two')]
            repos = [source.Repository(p) for p in roots]
            for n, repo in enumerate(repos):
                repo.root.mkdir()
                repo.git('init', '-q', '-b', 'main')
                (repo.root / 'VERSION_CODE').write_text(f'{n + 1}\n')
            before = Path.cwd()
            for repo in (repos[1], repos[0], repos[1]):
                self.assertEqual(repo.run('git', 'rev-parse', '--show-toplevel'), str(repo.root))
                self.assertEqual(repo.cert, repo.root / 'release/signing/android-release-cert.pem')
            self.assertEqual(Path.cwd(), before)
            with self.assertRaises(FrozenInstanceError): repos[0].root = repos[1].root
            with self.assertRaises(ValueError): source.Repository(Path('relative'))

    def test_import_has_no_repository_discovery_or_read(self):
        directory = str(Path(__file__).resolve().parent)
        program = '''
from pathlib import Path
import subprocess
from unittest.mock import patch
with patch.object(subprocess, 'check_output', side_effect=AssertionError('import discovery')), \\
     patch.object(Path, 'read_text', side_effect=AssertionError('import read')), \\
     patch.object(Path, 'read_bytes', side_effect=AssertionError('import read')):
    import source
    assert 'release' not in __import__('sys').modules
    import release
    assert not any(hasattr(release, name) for name in ('ROOT', 'CERT', 'FP'))
'''
        with tempfile.TemporaryDirectory() as tmp:
            subprocess.run([sys.executable, '-B', '-c', program], cwd=tmp,
                           env={**os.environ, 'PYTHONPATH': directory}, check=True)
        tree = ast.parse(Path(source.__file__).read_text())
        self.assertFalse(any(isinstance(n, ast.Import) and any(a.name == 'release' for a in n.names)
                             for n in ast.walk(tree)))

    def test_action_associations_and_unsupported_declarations(self):
        for action, (digest, tag, runtime) in source.REVIEWED_ACTIONS.items():
            good = f'      - uses: {action}@{digest} # {tag}'
            self.assertEqual(source.workflow_actions(good), [action])
            self.assertIn(runtime, ('node24', 'composite'))
            cases = [good.replace(digest, '0' * 40), good.replace(tag, 'v0.0.0'),
                     good.replace(action, 'unknown/action'), good.replace(digest, tag),
                     good.replace(digest, digest[:-1]), good.replace(digest, digest.upper()),
                     good.replace('uses:', '"uses":'), good.replace('uses:', "'uses':"),
                     good.replace('uses:', '{ uses:') + ' }', good.replace('uses: ', 'uses: >\n        '),
                     good.replace('uses: ', 'uses: &pin '), '      - uses: *pin',
                     '      <<: *defaults', good + '\nACTIONS_ALLOW_USE_UNSECURE_NODE_VERSION: true',
                     good.replace('uses:', '\tuses:'), good.replace('uses:', 'USES:'),
                     r'- "\u0075ses": actions/checkout@main',
                     r'- {"\u0075ses": actions/checkout@main}',
                     'steps: [{uses: actions/checkout@main}]', '---']
            for bad in cases:
                with self.subTest(bad=bad), self.assertRaises(ValueError): source.workflow_actions(bad)

    def test_literal_shell_text_is_not_an_action_declaration(self):
        action, (digest, tag, _) = next(iter(source.REVIEWED_ACTIONS.items()))
        text = f'- uses: {action}@{digest} # {tag}\n- run: |\n    echo "uses: literal shell text"\n'
        self.assertEqual(source.workflow_actions(text), [action])

    def test_full_action_inventory_includes_local_yaml(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = source.Repository(Path(tmp))
            workflows = repo.root / '.github/workflows'; workflows.mkdir(parents=True)
            declarations = [f'- uses: {a}@{sha} # {tag}\n'
                            for a, (sha, tag, _) in source.REVIEWED_ACTIONS.items()]
            (workflows / 'ci.yml').write_text(declarations[0])
            (workflows / 'release.yml').write_text(''.join(declarations))
            source.action_policy(repo)
            extra = workflows / 'extra.yml'; extra.write_text(declarations[0])
            with self.assertRaisesRegex(ValueError, 'inventory'): source.action_policy(repo)
            extra.unlink()
            local = repo.root / '.github/actions/local/action.yml'; local.parent.mkdir(parents=True)
            local.write_text('runs:\n  steps:\n    - uses: unknown/vendor@' + 'a' * 40 + ' # v1.0.0\n')
            with self.assertRaisesRegex(ValueError, 'Unreviewed action'): source.action_policy(repo)
            local.write_text('runs:\n  steps:\n    - uses: actions/checkout@main\n')
            with self.assertRaisesRegex(ValueError, 'Unsupported'): source.action_policy(repo)

    def test_fingerprint_file_grammar(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'fingerprint'
            path.write_bytes(b'a' * 64 + b'\n')
            self.assertEqual(source.fingerprint(path), 'a' * 64)
            for data in (b'a' * 64, b'a' * 64 + b'\r\n', b' ' + b'a' * 64 + b'\n',
                         b'a' * 64 + b'\n\n', b'A' * 64 + b'\n', b'\xff' * 64 + b'\n'):
                path.write_bytes(data)
                with self.subTest(data=data), self.assertRaises(ValueError): source.fingerprint(path)
