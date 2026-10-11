"""Normal publication HTTP and refusal regressions."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import release


class LookupTests(unittest.TestCase):
    def response(self, code=404, body=None, returncode=None):
        if body is None:
            body = ({'id': 1, 'draft': False, 'tag_name': 'v1.0.0'} if code == 200
                    else {'message': 'fixture', 'status': str(code)})
        return SimpleNamespace(stdout=f'HTTP/2.0 {code} fixture\r\nx-fixture: yes\r\n\r\n' + json.dumps(body),
                               returncode=(0 if code == 200 else 1) if returncode is None else returncode)

    def test_http_status_semantics(self):
        for code in (200, 404, 401, 403, 429, 500, 503):
            with self.subTest(code=code), patch.object(release.subprocess, 'run', return_value=self.response(code)) as cli:
                if code == 200:
                    self.assertEqual(release.lookup_release('example/repo', 'v1.0.0')['id'], 1)
                elif code == 404:
                    self.assertIsNone(release.lookup_release('example/repo', 'v1.0.0'))
                else:
                    with self.assertRaises(ValueError): release.lookup_release('example/repo', 'v1.0.0')
                self.assertIn('--include', cli.call_args.args[0])
                self.assertFalse(cli.call_args.kwargs['check'])

    def test_malformed_http_json_and_cli_failures(self):
        cases = [SimpleNamespace(stdout=s, returncode=rc) for s, rc in [
            ('', 1), ('Not Found', 1), ('HTTP/2.0 404 fixture\n\nnot-json', 1),
            ('HTTP/2.0 404 fixture\n\n[]', 1), ('HTTP/2.0 200 fixture\n\n{}', 0),
            ('HTTP/2.0 404 fixture\nbad-header\n\n{}', 1),
            ('HTTP/2.0 200 fixture\n\n{"id":true,"draft":false,"tag_name":"v1.0.0"}', 0)]]
        cases += [self.response(404, returncode=2), self.response(200, returncode=1),
                  self.response(404, body={'message':'fixture', 'status':'403'})]
        for result in cases:
            with self.subTest(result=result), patch.object(release.subprocess, 'run', return_value=result):
                with self.assertRaises((ValueError, json.JSONDecodeError)):
                    release.lookup_release('example/repo', 'v1.0.0')
        for error in (FileNotFoundError('gh'), OSError('network'), subprocess.TimeoutExpired('gh', 60)):
            with patch.object(release.subprocess, 'run', side_effect=error):
                with self.assertRaises(type(error)): release.lookup_release('example/repo', 'v1.0.0')

    def test_existing_release_never_mutates(self):
        with tempfile.TemporaryDirectory() as tmp:
            bundle = Path(tmp)
            apk = bundle / 'totipo-android-1.0.0.apk'; apk.write_bytes(b'signed')
            (bundle / (apk.name + '.sha256')).write_text(release.sha(apk) + '  ' + apk.name + '\n')
            args = SimpleNamespace(bundle=bundle, version='1.0.0', commit='a'*40)
            for draft in (False, True):
                with patch.dict(os.environ, GITHUB_REPOSITORY='example/repo'), patch.object(release, 'bundle_check', return_value={'tag':'v1.0.0'}), patch.object(release, 'verify_signed'), patch.object(release, 'compare'), patch.object(release, 'lookup_release', return_value=None if draft else {'id':1}), patch.object(release, 'listed_release', return_value={'draft':True}), patch.object(release, 'run') as mutation:
                    with self.assertRaisesRegex(ValueError, 'already exists'): release.publish(args)
                    mutation.assert_not_called()

    def test_paginated_draft_lookup(self):
        record = {'id':1, 'tag_name':'v1.0.0', 'draft':True}
        for pages, error in [([[], [record]], False), ([[record], [record]], True),
                             ({}, True), ([[{}]], True), ([[{'tag_name':'v1.0.0'}]], True)]:
            with patch.object(release, 'run', return_value=json.dumps(pages)) as cli:
                if error:
                    with self.assertRaises(ValueError): release.listed_release('example/repo', 'v1.0.0')
                else: self.assertEqual(release.listed_release('example/repo', 'v1.0.0'), record)
                self.assertIn('--paginate', cli.call_args.args)

