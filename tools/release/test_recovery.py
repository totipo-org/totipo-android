"""Deterministic release transport/recovery tests: no live mutation, SDK or keys."""
import copy
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import zipfile
import recover
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


class RecoveryTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.version = '1.0.0'; self.commit = 'a'*40; self.run_id = '42'
        self.repo = 'example/repo'
        self.workflow = {'id':12, 'path':recover.WORKFLOW}
        self.record = dict(id=42, repository={'id':7,'full_name':self.repo},
                           head_repository={'full_name':self.repo}, workflow_id=12,
                           path=recover.WORKFLOW, head_sha=self.commit, head_branch='main',
                           event='workflow_dispatch', status='completed', conclusion='failure', run_attempt=1)
        self.artifact = dict(id=99, name='verified-release', expired=False, digest='sha256:'+'b'*64,
                             workflow_run=dict(id=42, repository_id=7, head_repository_id=7,
                                               head_sha=self.commit, head_branch='main'))

    def test_run_identity_and_negatives(self):
        recover.validate_run(self.record, self.workflow, self.repo, self.commit, self.run_id)
        for changes in [dict(repository={'full_name':'other/repo'}), dict(head_repository={'full_name':'other/repo'}),
                        dict(workflow_id=13), dict(path='.github/workflows/other.yml'),
                        dict(head_sha='b'*40), dict(head_branch='other'), dict(event='push'),
                        dict(conclusion='success'), dict(status='in_progress'), dict(id=43), dict(run_attempt=2)]:
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                recover.validate_run(self.record | changes, self.workflow, self.repo, self.commit, self.run_id)
        with self.assertRaises(ValueError):
            recover.validate_run(self.record, self.workflow | {'path':'wrong'}, self.repo, self.commit, self.run_id)

    def test_artifact_selection_and_negatives(self):
        self.assertEqual(recover.select_artifact([self.artifact], 7, self.commit, self.run_id), self.artifact)
        for records in [[], [self.artifact]*2, [self.artifact | {'name':'signed-candidate'}],
                        [self.artifact | {'expired':True}], [self.artifact | {'digest':''}],
                        [self.artifact | {'id':0}]]:
            with self.subTest(records=records), self.assertRaises(ValueError):
                recover.select_artifact(records, 7, self.commit, self.run_id)
        for field, value in [('id',43), ('head_sha','b'*40), ('head_branch','other'),
                             ('repository_id',8), ('head_repository_id',8)]:
            a = copy.deepcopy(self.artifact); a['workflow_run'][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                recover.select_artifact([a], 7, self.commit, self.run_id)

    def test_dispatch_input_validation(self):
        recover.inputs(self.version, self.commit, self.run_id)
        for values in [('x',self.commit,'42'), ('1.0.0-dev',self.commit,'42'),
                       (self.version,'main','42'), (self.version,self.commit,'0'),
                       (self.version,self.commit,'42;cmd'), (self.version,self.commit,'')]:
            with self.assertRaises(ValueError): recover.inputs(*values)

    def target(self, kind='tag', peeled=None, remote_object=None):
        tag = 'refs/tags/v' + self.version
        def fake(*cmd):
            if cmd[:3] == ('git','cat-file','-t'): return kind if cmd[-1] == tag else 'commit'
            if cmd[:2] == ('git','rev-parse'): return (peeled or self.commit) if cmd[-1].endswith('^{commit}') else 'c'*40
            if cmd[:2] == ('git','ls-remote'):
                return f"{remote_object or 'c'*40}\t{tag}\n{self.commit}\t{tag}^{{}}"
            raise AssertionError(cmd)
        return fake

    def test_target_tag_source_and_public_identity(self):
        cert = self.root / 'cert'; cert.write_bytes(b'public cert')
        fp = self.root / 'fp'; fp.write_bytes(b'public digest')
        expected = [(self.version+'\n').encode(), cert.read_bytes(), fp.read_bytes()]
        for kind, peeled, remote in [('tag',None,None), ('commit',None,None),
                                      ('tag','b'*40,None), ('tag',None,'b'*40)]:
            with patch.object(release,'CERT',cert), patch.object(release,'FP',fp), patch.object(release,'run',side_effect=self.target(kind,peeled,remote)), patch.object(recover.subprocess,'check_output',side_effect=expected):
                if kind == 'tag' and peeled is None and remote is None:
                    self.assertEqual(recover.target_identity(self.version,self.commit), 'c'*40)
                else:
                    with self.assertRaises(ValueError): recover.target_identity(self.version,self.commit)
        for index in range(3):
            blobs = expected.copy(); blobs[index] = b'disagreement'
            with patch.object(release,'CERT',cert), patch.object(release,'FP',fp), patch.object(release,'run',side_effect=self.target()), patch.object(recover.subprocess,'check_output',side_effect=blobs):
                with self.assertRaisesRegex(ValueError,'VERSION/public identity'):
                    recover.target_identity(self.version,self.commit)

    def test_current_tooling_and_old_source_are_separate(self):
        tooling = 'd'*40
        def fake(*cmd):
            if cmd[:2] == ('git','rev-parse'): return tooling
            if cmd[:2] == ('git','status'): return ''
            if cmd[:2] == ('git','ls-remote'): return tooling+'\trefs/heads/main'
            raise AssertionError(cmd)
        with patch.dict(os.environ,GITHUB_REF='refs/heads/main',GITHUB_SHA=tooling), patch.object(release,'run',side_effect=fake), patch.object(release,'guard'), patch.object(release,'expected_identity',return_value='b'*64), patch.object(recover,'target_identity') as target:
            self.assertEqual(recover.recovery_identity(self.version,self.commit),'b'*64)
            target.assert_called_once_with(self.version,self.commit)
            for ref,sha in [('refs/tags/v1.0.0',tooling),('refs/heads/main',self.commit)]:
                with patch.dict(os.environ,GITHUB_REF=ref,GITHUB_SHA=sha), self.assertRaises(ValueError):
                    recover.recovery_identity(self.version,self.commit)

    def test_archive_allowlist_digest_and_tampering(self):
        archive = self.root/'artifact.zip'
        for entries in [recover.names(self.version), recover.names(self.version) | {'../escape'},
                        recover.names(self.version) - {'unsigned-release.apk'}]:
            with zipfile.ZipFile(archive,'w') as z:
                for name in entries: z.writestr(name,b'fixture')
            target = self.root/'bundle'
            if entries == recover.names(self.version):
                recover.extract(archive,target,self.version,'sha256:'+release.sha(archive))
                self.assertEqual({p.name for p in target.iterdir()},entries)
            else:
                with self.assertRaises(ValueError): recover.extract(archive,target,self.version,'sha256:'+release.sha(archive))
        with self.assertRaisesRegex(ValueError,'digest'):
            recover.extract(archive,self.root/'other',self.version,'sha256:'+'0'*64)

    def fixture(self):
        bundle=self.root/'bundle'; bundle.mkdir(exist_ok=True)
        unsigned=bundle/'unsigned-release.apk'
        with zipfile.ZipFile(unsigned,'w') as z: z.writestr('payload',b'original')
        apk=bundle/f'totipo-android-{self.version}.apk'; apk.write_bytes(unsigned.read_bytes())
        p=dict(version=self.version, source_commit=self.commit, tag='v'+self.version,
               unsigned_sha256=release.sha(unsigned), application_id=release.APPLICATION_ID,
               version_code=42,version_name=self.version,nix_derivation='/nix/store/'+'a'*32+'-fixture.drv',
               nix_output='/nix/store/'+'b'*32+'-fixture',certificate_sha256='b'*64,
               java_runtime='0.2.0',protocol='v1/r19')
        (bundle/'release-provenance.json').write_text(json.dumps(p))
        (bundle/'unsigned-release.apk.sha256').write_text(release.sha(unsigned)+'  unsigned-release.apk\n')
        (bundle/(apk.name+'.sha256')).write_text(release.sha(apk)+'  '+apk.name+'\n')
        return SimpleNamespace(bundle=bundle,version=self.version,commit=self.commit),p,apk,unsigned

    def test_reverification_provenance_and_tampering(self):
        for case in ('valid','source','version','cert','signed','unsigned','payload','schema'):
            args,p,apk,unsigned=self.fixture()
            if case=='source': p['source_commit']='c'*40
            if case=='version': p['version']='2.0.0'; p['tag']='v2.0.0'
            if case=='cert': p['certificate_sha256']='c'*64
            if case=='schema': p['extra']='bad'
            if case=='unsigned': unsigned.write_bytes(b'tampered')
            if case in ('signed','payload'):
                with zipfile.ZipFile(apk,'w') as z: z.writestr('payload',b'tampered')
                if case=='payload': (args.bundle/(apk.name+'.sha256')).write_text(release.sha(apk)+'  '+apk.name+'\n')
            (args.bundle/'release-provenance.json').write_text(json.dumps(p))
            with self.subTest(case=case), patch.object(recover,'recovery_identity',return_value='b'*64), patch.object(release,'apk_metadata',return_value={k:p[k] for k in ('application_id','version_code','version_name')}), patch.object(release,'unsigned_check'), patch.object(release,'verify_signed'):
                if case=='valid': recover.verify_bundle(args)
                else:
                    with self.assertRaises(ValueError): recover.verify_bundle(args)

    def test_acquire_run_artifact_and_same_run_receipt(self):
        args,p,apk,unsigned=self.fixture()
        archive=self.root/'source.zip'
        with zipfile.ZipFile(archive,'w') as z:
            for path in args.bundle.iterdir(): z.write(path,path.name)
        original={path.name:path.read_bytes() for path in args.bundle.iterdir()}
        import shutil
        shutil.rmtree(args.bundle)
        args.failed_run_id=self.run_id
        self.artifact['digest']='sha256:'+release.sha(archive)
        def download(cmd, **kwargs):
            self.assertEqual(cmd[-1],'repos/example/repo/actions/artifacts/99/zip')
            kwargs['stdout'].write(archive.read_bytes())
        def listing(path,key):
            if key=='artifacts': return [self.artifact]
            return [dict(name=n,conclusion=c) for n,c in
                    [('qualify','success'),('sign','success'),('verify','success'),('publish','failure')]]
        with patch.dict(os.environ,GITHUB_REPOSITORY=self.repo,GITHUB_SHA='d'*40), patch.object(recover,'recovery_identity',return_value='b'*64), patch.object(recover,'target_identity',return_value='c'*40), patch.object(recover,'api',side_effect=[self.workflow,self.record]), patch.object(recover,'pages',side_effect=listing), patch.object(recover.subprocess,'run',side_effect=download), patch.object(release,'apk_metadata',return_value={k:p[k] for k in ('application_id','version_code','version_name')}), patch.object(release,'unsigned_check'), patch.object(release,'verify_signed'):
            recover.acquire(args)
        for name,data in original.items(): self.assertEqual((args.bundle/name).read_bytes(),data)
        receipt=json.loads((args.bundle/recover.RECEIPT).read_text())
        self.assertEqual(receipt['artifact_id'],99)
        self.assertEqual(receipt['tag_object'],'c'*40)
        self.assertEqual(receipt['tooling_commit'],'d'*40)

    def test_publish_receipt_negatives_and_common_publication(self):
        args,p,apk,unsigned=self.fixture(); args.failed_run_id=self.run_id
        original=dict(tooling_commit='d'*40,source_commit=self.commit,version=self.version,
                      failed_run_id=self.run_id,tag_object='c'*40,artifact_id=99,
                      artifact_digest='sha256:'+'b'*64)
        for changes in [{}, {'source_commit':'b'*40}, {'version':'2.0.0'},
                        {'failed_run_id':'43'}, {'tag_object':'b'*40},
                        {'tooling_commit':'b'*40}, {'artifact_id':0}, {'extra':'bad'}]:
            (args.bundle/recover.RECEIPT).write_text(json.dumps(original | changes))
            with self.subTest(changes=changes), patch.dict(os.environ,GITHUB_SHA='d'*40), patch.object(recover,'recovery_identity',return_value='b'*64), patch.object(recover,'target_identity',return_value='c'*40), patch.object(recover,'verify_bundle') as verify, patch.object(release,'publish') as common:
                if changes:
                    with self.assertRaises(ValueError): recover.publish(args)
                    common.assert_not_called()
                else:
                    recover.publish(args)
                    verify.assert_called_once_with(args,receipt=True)
                    common.assert_called_once()
                    self.assertEqual(common.call_args.kwargs['identity_check'](self.version,self.commit),'b'*64)

    def test_recovery_static_security_contract(self):
        path=release.ROOT/'.github/workflows/recover-release.yml'
        if not path.exists(): self.skipTest('Workflows excluded from Android package source')
        text=path.read_text()
        for forbidden in ('ANDROID_RELEASE_', 'android-release\n    steps:', 'environment:',
                          'secrets.', 'apksigner', 'keytool', 'assemble', 'workflow_run:',
                          'pull_request_target', 'git push', 'git tag', 'release delete'):
            self.assertNotIn(forbidden,text)
        actions=re.findall(r'uses: ([^\s]+)',text)
        self.assertTrue(actions)
        self.assertTrue(all(re.fullmatch(r'[\w/-]+@[0-9a-f]{40}',a) for a in actions))
        verify,publish=text.split('  recover-publish:\n')
        self.assertIn('actions: read',verify)
        self.assertNotIn('contents: write',verify)
        self.assertEqual(text.count('contents: write'),1)
        self.assertNotIn('actions: read',publish)
        self.assertIn('needs: recover-verify',publish)
        self.assertIn('name: recovery-verified',publish)
        for forbidden in ('run-id:', 'github-token:', 'artifact-ids:', 'recover.py acquire'):
            self.assertNotIn(forbidden,publish)
        self.assertEqual(text.count('ref: ${{ github.sha }}'),2)
        self.assertIn('REQUESTED_COMMIT: ${{ inputs.source_commit }}',text)
        self.assertIn('FAILED_RUN_ID: ${{ inputs.failed_run_id }}',text)
        self.assertIn('"$FAILED_RUN_ID" =~ ^[1-9][0-9]*$',text)
        self.assertIn('"$REQUESTED_COMMIT" =~ ^[0-9a-f]{40}$',text)
        self.assertNotIn('"$GITHUB_SHA" == "$REQUESTED_COMMIT"',text)
        source=(release.ROOT/'tools/release/recover.py').read_text()
        self.assertIn('release.publish(args, identity_check=final_identity)',source)
        self.assertNotIn("'apksigner', 'sign'",source)
        self.assertNotIn("'push'",source)


if __name__=='__main__': unittest.main()
