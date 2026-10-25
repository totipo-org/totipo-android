"""Deterministic GitHub HTTP/transport fixtures; no credentials or network."""
import copy
import json
import os
from pathlib import Path
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import release
from source import Repository


def response(code=200, body=None, returncode=None):
    data = body if isinstance(body, bytes) else json.dumps(body).encode()
    return SimpleNamespace(stdout=f'HTTP/2.0 {code} fixture\r\nx-fixture: yes\r\n\r\n'.encode() + data,
                           returncode=(0 if code in (200, 201) else 1) if returncode is None else returncode)


class PublicationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.repo = Repository(self.root)
        bundle = self.root / 'bundle'; bundle.mkdir()
        apk = bundle / 'totipo-android-1.0.0.apk'; apk.write_bytes(b'signed\r\n\r\n\x00\xff')
        (bundle / (apk.name + '.sha256')).write_text(release.sha(apk) + '  ' + apk.name + '\n')
        (bundle / 'release-provenance.json').write_text('{}')
        self.args = SimpleNamespace(bundle=bundle, version='1.0.0', commit='a' * 40)
        self.record = dict(id=42, tag_name='v1.0.0', draft=True)
        self.assets = []
        self.contents = {}
        self.created = False
        self.calls = []
        self.hook = lambda method, endpoint, cmd: None
        self.events = []
        for name, value in (
            ('bundle_check', {'tag': 'v1.0.0', 'certificate_sha256': 'b' * 64}),
            ('verify_signed', None)):
            mock = patch.object(release, name, return_value=value).start()
            self.addCleanup(patch.stopall)
            setattr(self, name, mock)
        self.identity = patch.object(release, 'identity', side_effect=lambda *a: self.events.append('identity')).start()
        self.sleep = patch.object(release.time, 'sleep').start()
        patch.dict(os.environ, GITHUB_REPOSITORY='example/repo').start()
        patch.object(release.subprocess, 'run', side_effect=self.run_cli).start()

    def run_cli(self, cmd, **kwargs):
        self.assertFalse(kwargs['check'])
        self.assertEqual(kwargs['timeout'], 60)
        method = cmd[cmd.index('--method') + 1]
        endpoint = cmd[5]
        self.calls.append((method, endpoint, cmd))
        self.events.append((method, endpoint))
        override = self.hook(method, endpoint, cmd)
        if override is not None:
            return override
        if '/releases/tags/' in endpoint:
            return response(404, {'message': 'Not Found', 'status': '404'})
        if '/releases?' in endpoint:
            # Deliberately never exposes the newly created draft in listings.
            return response(body=[])
        if method == 'POST' and endpoint == 'repos/example/repo/releases':
            self.created = True
            return response(201, self.record)
        if method == 'POST' and endpoint.startswith('https://uploads.github.com/'):
            local = Path(cmd[cmd.index('--input') + 1])
            record = dict(id=101 + len(self.assets), name=local.name,
                          state='uploaded', size=local.stat().st_size)
            self.assets.append(record)
            self.contents[record['id']] = local.read_bytes()
            return response(201, record)
        if endpoint.endswith('/assets?per_page=100'):
            return response(body=self.assets)
        if '/releases/assets/' in endpoint:
            asset_id = int(endpoint.rsplit('/', 1)[1])
            if 'Accept: application/octet-stream' in cmd:
                return response(body=self.contents[asset_id])
            return response(body=next(r for r in self.assets if r['id'] == asset_id))
        self.assertIn(endpoint, ('repos/example/repo/releases/42', 'repos/example/repo/releases/43'))
        if method == 'PATCH':
            self.record['draft'] = False
        return response(body=self.record)

    def publish(self):
        release.publish(self.repo, self.args)

    def mutations(self, method):
        return [c for c in self.calls if c[0] == method]

    def test_success_despite_invisible_draft_in_list(self):
        self.publish()
        self.assertEqual(len(self.mutations('POST')), 4)
        self.assertEqual(len(self.mutations('PATCH')), 1)
        self.assertEqual(sum('/releases?' in c[1] for c in self.calls), 1)
        self.assertEqual(sum('/releases/tags/' in c[1] for c in self.calls), 1)
        self.assertEqual(sum('Accept: application/octet-stream' in c[2] for c in self.calls), 6)
        self.assertFalse(self.record['draft'])
        self.assertEqual(self.identity.call_count, 2)
        self.verify_signed.assert_called_once()
        self.sleep.assert_not_called()
        patch_index = next(i for i, event in enumerate(self.events) if isinstance(event, tuple) and event[0] == 'PATCH')
        self.assertEqual(self.events[patch_index - 2], 'identity')
        create = self.mutations('POST')[0][2]
        self.assertIn('target_commitish=' + self.args.commit, create)
        self.assertIn('draft=true', create)
        self.assertTrue(all('/42/assets?' in c[1] for c in self.mutations('POST')[1:]))

    def test_transient_read_and_download_retry(self):
        failed = set()
        def hook(method, endpoint, cmd):
            key = (endpoint, 'Accept: application/octet-stream' in cmd)
            if method == 'GET' and key not in failed:
                failed.add(key)
                if 'Accept: application/octet-stream' in cmd:
                    return response(body=b'interrupted', returncode=1)
                return response(503, {'message': 'temporary'})
        self.hook = hook
        self.publish()
        self.assertGreater(self.sleep.call_count, 0)
        self.assertEqual(len(self.mutations('POST')), 4)
        self.assertEqual(len(self.mutations('PATCH')), 1)

    def test_known_release_and_asset_temporarily_missing(self):
        failed = set()
        def hook(method, endpoint, cmd):
            if method == 'GET' and ('/releases/42' in endpoint or '/releases/assets/' in endpoint) and endpoint not in failed:
                failed.add(endpoint)
                if endpoint.endswith('/assets?per_page=100'):
                    return response(body=self.assets[:1])
                return response(404, {'message': 'Not Found'})
        self.hook = hook
        self.publish()
        self.assertEqual(self.sleep.call_count, 5)

    def test_creation_ambiguity_never_adopts_or_retries(self):
        for result in (response(502, {}), response(201, b'not-json'),
                       SimpleNamespace(stdout=b'', returncode=1), subprocess.TimeoutExpired('gh', 60)):
            with self.subTest(result=result):
                self.calls.clear()
                def hook(method, endpoint, cmd):
                    if method == 'POST':
                        self.created = True
                        if isinstance(result, Exception): raise result
                        return result
                self.hook = hook
                with self.assertRaises(ValueError): self.publish()
                self.assertTrue(self.created)
                self.assertEqual(len(self.mutations('POST')), 1)
                self.assertFalse(self.mutations('PATCH'))

    def test_preexisting_and_duplicate_rejection(self):
        for where in ('tag', 'draft', 'server'):
            with self.subTest(where=where):
                self.calls.clear()
                def hook(method, endpoint, cmd):
                    if where == 'tag' and '/tags/' in endpoint:
                        return response(body=dict(self.record, draft=False))
                    if where == 'draft' and '/releases?' in endpoint:
                        return response(body=[self.record])
                    if where == 'server' and method == 'POST':
                        return response(422, {'message': 'already_exists'})
                self.hook = hook
                with self.assertRaises(ValueError): self.publish()
                self.assertEqual(len(self.mutations('POST')), int(where == 'server'))
                self.assertFalse(self.mutations('PATCH'))

    def test_failed_run_never_resumes_an_observable_draft(self):
        def hook(method, endpoint, cmd):
            if method == 'POST' and len(self.assets) == 1:
                return response(502, {})
            if '/releases?' in endpoint and self.created:
                return response(body=[self.record])
        self.hook = hook
        with self.assertRaises(ValueError): self.publish()
        with self.assertRaisesRegex(ValueError, 'already exists'): self.publish()
        self.assertEqual(len(self.mutations('POST')), 3)
        self.assertFalse(self.mutations('PATCH'))

    def test_partial_or_ambiguous_upload_stops(self):
        def hook(method, endpoint, cmd):
            if method == 'POST' and len(self.assets) == 1:
                return response(502, {'message': 'upload failed'})
        self.hook = hook
        with self.assertRaises(ValueError): self.publish()
        self.assertEqual(len(self.assets), 1)
        self.assertEqual(len(self.mutations('POST')), 3)
        self.assertFalse(self.mutations('PATCH'))
        self.sleep.assert_not_called()

    def test_missing_corrupt_extra_or_wrong_asset(self):
        for kind in ('missing', 'corrupt', 'extra', 'id', 'name', 'state', 'size', 'duplicate'):
            with self.subTest(kind=kind):
                self.calls.clear(); self.assets.clear(); self.record['draft'] = True
                def hook(method, endpoint, cmd):
                    if method != 'GET': return
                    if endpoint.endswith('/assets?per_page=100'):
                        records = copy.deepcopy(self.assets)
                        if kind == 'missing': records.pop()
                        if kind == 'extra': records.append(dict(id=999, name='extra'))
                        if kind == 'duplicate': records.append(records[0])
                        if kind in ('id', 'name', 'state', 'size'):
                            records[0][kind] = {'id':999, 'name':'wrong', 'state':'starter', 'size':0}[kind]
                        return response(body=records)
                    if kind == 'corrupt' and 'Accept: application/octet-stream' in cmd:
                        return response(body=b'corrupt')
                self.hook = hook
                with self.assertRaises(ValueError): self.publish()
                self.assertFalse(self.mutations('PATCH'))

    def test_incorrect_release_identity_or_draft_state(self):
        for phase in ('create', 'before-upload', 'before-publish', 'final'):
            for field, value in (('id', 43), ('tag_name', 'v2.0.0'), ('draft', None), ('draft', phase == 'final')):
                with self.subTest(phase=phase, field=field):
                    self.calls.clear(); self.assets.clear(); self.record['draft'] = True
                    def hook(method, endpoint, cmd):
                        hit = ((phase == 'create' and method == 'POST' and endpoint.endswith('/releases')) or
                               (phase == 'before-upload' and method == 'GET' and endpoint.endswith('/42') and not self.assets) or
                               (phase == 'before-publish' and method == 'GET' and endpoint.endswith('/42') and len(self.assets) == 3) or
                               (phase == 'final' and method == 'GET' and not self.record['draft']))
                        if hit:
                            return response(201 if method == 'POST' else 200, dict(self.record, **{field:value}))
                    self.hook = hook
                    with self.assertRaises(ValueError): self.publish()
                    self.assertEqual(len(self.mutations('PATCH')), int(phase == 'final'))

    def test_source_drift_prevents_publication(self):
        self.identity.side_effect = [None, ValueError('source drift')]
        with self.assertRaisesRegex(ValueError, 'source drift'): self.publish()
        self.assertFalse(self.mutations('PATCH'))

    def test_publication_ambiguity_reconciles_without_repeat(self):
        for published in (True, False):
            with self.subTest(published=published):
                self.calls.clear(); self.assets.clear(); self.record['draft'] = True
                def hook(method, endpoint, cmd):
                    if method == 'PATCH':
                        self.record['draft'] = not published
                        raise subprocess.TimeoutExpired(cmd, 60)
                self.hook = hook
                if published:
                    self.publish()
                else:
                    with self.assertRaisesRegex(ValueError, 'draft state'): self.publish()
                self.assertEqual(len(self.mutations('PATCH')), 1)

    def test_final_verification_detects_asset_replacement(self):
        def hook(method, endpoint, cmd):
            if method == 'GET' and not self.record['draft'] and endpoint.endswith('/assets?per_page=100'):
                records = copy.deepcopy(self.assets); records[0]['id'] = 999
                return response(body=records)
        self.hook = hook
        with self.assertRaisesRegex(ValueError, 'Asset identity'): self.publish()
        self.assertEqual(len(self.mutations('PATCH')), 1)

    def test_publication_ambiguous_json_or_http_response(self):
        for result in (response(502, {}), response(200, b'invalid-json'),
                       SimpleNamespace(stdout=b'', returncode=1)):
            with self.subTest(result=result):
                self.calls.clear(); self.assets.clear(); self.record['draft'] = True
                def hook(method, endpoint, cmd):
                    if method == 'PATCH':
                        self.record['draft'] = False
                        return result
                self.hook = hook
                self.publish()
                self.assertEqual(len(self.mutations('PATCH')), 1)

    def test_exhausted_known_release_read_leaves_draft(self):
        def hook(method, endpoint, cmd):
            if method == 'GET' and endpoint.endswith('/42'):
                return response(503, {})
        self.hook = hook
        with self.assertRaises(ValueError): self.publish()
        self.assertEqual(len(self.mutations('POST')), 1)
        self.assertFalse(self.mutations('PATCH'))
        self.assertEqual([c.args[0] for c in self.sleep.call_args_list], [1, 2])

    def test_invalid_patch_response_identity_stops(self):
        def hook(method, endpoint, cmd):
            if method == 'PATCH': return response(body=dict(self.record, id=999, draft=False))
        self.hook = hook
        with self.assertRaisesRegex(ValueError, 'Release ID'): self.publish()
        self.assertEqual(len(self.mutations('PATCH')), 1)


class QueryTests(unittest.TestCase):
    def setUp(self):
        self.repo = Repository(Path('/disposable-fixture'))

    def test_read_retry_exhaustion_and_authorization(self):
        for code in (401, 403, 408, 429, 500, 502, 503, 504, 404):
            with self.subTest(code=code), patch.object(release.subprocess, 'run', return_value=response(code, {})) as cli, patch.object(release.time, 'sleep') as sleep:
                with self.assertRaises(ValueError): release.github_request(self.repo, 'known-release', known=True)
                attempts = 1 if code in (401, 403) else 3
                self.assertEqual(cli.call_count, attempts)
                self.assertEqual([c.args[0] for c in sleep.call_args_list], list(range(1, attempts)))

    def test_timeout_and_interrupted_download(self):
        for failure in (subprocess.TimeoutExpired('gh', 60), SimpleNamespace(stdout=b'', returncode=1), response(body=b'partial', returncode=1)):
            with self.subTest(failure=failure), patch.object(release.subprocess, 'run', side_effect=[failure, response(body=b'complete\r\n\xff')]) as cli, patch.object(release.time, 'sleep'):
                # A SimpleNamespace in side_effect is a response, exceptions are raised.
                self.assertEqual(release.github_request(self.repo, 'asset', binary=True), b'complete\r\n\xff')
                self.assertEqual(cli.call_count, 2)

    def test_lf_headers_do_not_modify_binary_or_consume_body_delimiters(self):
        body = b'APK\r\n\r\n\xff\x00\n\nbytes'
        raw = b'HTTP/2.0 200 OK\nx-test: fixture\n\n' + body
        with patch.object(release.subprocess, 'run', return_value=SimpleNamespace(stdout=raw, returncode=0)):
            self.assertEqual(release.github_request(self.repo, 'asset', binary=True), body)

    def test_malformed_data_and_local_errors_are_not_retried(self):
        cases = [response(body=b'invalid-json'), SimpleNamespace(stdout=b'garbage', returncode=0),
                 SimpleNamespace(stdout=b'HTTP/2.0 200 OK\r\nbad-header\r\n\r\n{}', returncode=0),
                 FileNotFoundError('gh'), OSError('local failure')]
        for result in cases:
            with self.subTest(result=result), patch.object(release.subprocess, 'run', side_effect=[result]) as cli, patch.object(release.time, 'sleep') as sleep:
                with self.assertRaises((ValueError, OSError)): release.github_request(self.repo, 'asset')
                self.assertEqual(cli.call_count, 1)
                sleep.assert_not_called()

    def test_preflight_list_404_is_not_transient_absence(self):
        with patch.object(release.subprocess, 'run', return_value=response(404, {'message':'Not Found'})) as cli, patch.object(release.time, 'sleep') as sleep:
            with self.assertRaises(ValueError): release.listed_release(self.repo, 'example/repo', 'v1.0.0')
            self.assertEqual(cli.call_count, 1)
            sleep.assert_not_called()

    def test_cli_authentication_and_usage_failures_are_not_retried(self):
        for code in (2, 4):
            with self.subTest(code=code), patch.object(release.subprocess, 'run', return_value=SimpleNamespace(stdout=b'', returncode=code)) as cli, patch.object(release.time, 'sleep') as sleep:
                with self.assertRaises(ValueError): release.github_request(self.repo, 'asset')
                self.assertEqual(cli.call_count, 1)
                sleep.assert_not_called()

    def test_only_valid_tag_404_is_absence(self):
        for body in ([], {}, {'message':'x','status':'403'}):
            with patch.object(release.subprocess, 'run', return_value=response(404, body)):
                with self.assertRaises(ValueError): release.lookup_release(self.repo, 'example/repo', 'v1.0.0')
        with patch.object(release.subprocess, 'run', return_value=response(404, {'message':'Not Found','status':'404'})):
            self.assertIsNone(release.lookup_release(self.repo, 'example/repo', 'v1.0.0'))

    def test_paginated_preflight_and_ambiguous_duplicates(self):
        record = dict(id=42, tag_name='v1.0.0', draft=True)
        first = [dict(tag_name='unrelated')] * 100
        with patch.object(release, 'github_request', side_effect=[first, [record]]) as api:
            self.assertEqual(release.listed_release(self.repo, 'example/repo', 'v1.0.0'), record)
            self.assertTrue(api.call_args.args[1].endswith('page=2'))
        for records in ([record, record], [{}], [dict(record, id=True)]):
            with patch.object(release, 'github_request', return_value=records):
                with self.assertRaises(ValueError): release.listed_release(self.repo, 'example/repo', 'v1.0.0')
