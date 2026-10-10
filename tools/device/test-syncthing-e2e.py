#!/usr/bin/env python3
"""Host checks of the evidence oracle; these do not qualify transport or the device."""
import copy
import sys
sys.dont_write_bytecode = True
import importlib.util
from pathlib import Path
from types import SimpleNamespace
import unittest

spec = importlib.util.spec_from_file_location('m3d', Path(__file__).with_name('qualify-syncthing-e2e.py'))
m3d = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m3d)


class EvidenceOracleTest(unittest.TestCase):
    def setUp(self):
        self.args = SimpleNamespace(writable=True, layout='objects', tokens=1, account=[], absent_account=[],
                                    conflicts=0, unresolved=0, state='OPEN', local_empty=False,
                                    same_session=True, vault_unchanged=True, match=True, provider='unchanged',
                                    different_vault=False, semantic_unchanged=False, local_unchanged=False)
        self.before = {'provider': {'fixture': 'Totipo-M3D-Test', 'persisted_read': True, 'persisted_write': True,
                                   'coverage': 'COMPLETE', 'inventory': [
                                       {'name': 'vault', 'kind': 'file', 'size': 87, 'sha256': 'v', 'status': 'READABLE'},
                                       {'name': 'objects-v1', 'kind': 'directory', 'status': 'READABLE'},
                                       {'name': 'objects-v1/' + 'a'*64, 'kind': 'file', 'size': 1024,
                                        'sha256': 'a', 'status': 'READABLE'}]},
                       'local': {'state': 'OPEN', 'binding': 'READY', 'token_count': 1,
                                 'tokens': [{'conflict': False, 'unresolved': 0, 'heads': 1, 'alternatives': [
                                     {'issuer': 'Totipo M3D', 'account': 'public@example.test'}]}],
                                 'session': 'session-one', 'vault_sha256': 'v', 'session_local_vault_id_equal': True,
                                 'provider_local_vault_bytes_equal': True, 'session_provider_vault_id_equal': True,
                                 'objects': ['a']}}
        self.after = copy.deepcopy(self.before)

    def failures(self):
        return m3d.validate(self.before, self.after, self.args)

    def test_matching_evidence(self):
        self.assertEqual([], self.failures())

    def test_changed_vault_is_not_transport_success(self):
        self.after['provider']['inventory'][0]['sha256'] = 'replacement'
        self.assertIn('provider VAULT invariant', self.failures())
        self.assertIn('exact provider/local VAULT and session identity', self.failures())

    def test_reopened_session_does_not_prove_same_session(self):
        self.after['local']['session'] = 'reopened'
        self.assertIn('same live session', self.failures())

    def test_immutable_gain_rejects_rewrite(self):
        self.args.provider = 'gains'
        self.after['provider']['inventory'].append({'name': 'objects-v1/' + 'b'*64, 'kind': 'file',
                                                   'size': 1024, 'sha256': 'b', 'status': 'READABLE'})
        self.assertEqual([], self.failures())
        self.after['provider']['inventory'][2]['sha256'] = 'rewritten'
        self.assertIn('immutable provider gain', self.failures())

    def test_wrong_vault_gate_rejects_local_import(self):
        self.args.match = False
        self.args.different_vault = True
        self.before['provider']['inventory'][0]['sha256'] = 'other-vault'
        self.after['provider']['inventory'][0]['sha256'] = 'other-vault'
        self.after['local']['session_provider_vault_id_equal'] = False
        self.after['local']['sync_message'] = 'Sync folder belongs to a different Totipo vault.'
        self.assertEqual([], self.failures())
        self.after['local']['objects'].append('unexpected')
        self.assertIn('different-vault zero local import', self.failures())

    def test_expected_conflict_cannot_pass_by_token_count(self):
        self.args.conflicts = 1
        self.assertIn('conflict count', self.failures())

    def test_unreadable_inventory_and_fixture_change_block(self):
        self.after['provider']['inventory'][2]['status'] = 'UNVERIFIABLE'
        self.after['provider']['fixture'] = 'Totipo-M3D-Android-First'
        self.assertIn('all provider bytes readable', self.failures())
        self.assertIn('same disposable fixture', self.failures())

    def test_deleted_metadata_must_be_absent(self):
        self.args.absent_account = ['public@example.test']
        self.assertIn('deleted/absent account public@example.test', self.failures())

    def test_absent_live_row_alone_does_not_prove_tombstone(self):
        self.args.deleted = 1
        self.assertIn('tombstone count', self.failures())
        self.after['local']['deleted_count'] = 1
        self.assertEqual([], self.failures())


if __name__ == '__main__':
    unittest.main()
