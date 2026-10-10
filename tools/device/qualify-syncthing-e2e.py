#!/usr/bin/env python3
"""M3D evidence checkpoints. No Syncthing integration and no protocol-file writes.

build/install: separate observer APK + signed non-debug release using existing signer.
serve: long-lived observer; run in a separate terminal/PTY across UI actions.
begin/finish: durable BEFORE, one HUMAN ACTION, AFTER, explicit expectations, fail latch.
No app-data reset or fixture cleanup is implemented: uncertain identity must stop work.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import zipfile

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / '.gradle/m3d-syncthing'
PACKAGE = 'org.totipo.syncthingqualification'
COMPONENT = PACKAGE + '/' + PACKAGE + '.SyncthingE2eRegression'
ACTION = PACKAGE + '.CAPTURE'
FOLDERS = ('Totipo-M3D-Test', 'Totipo-M3D-Android-First')


def run(args, **kw):
    result = subprocess.run([str(a) for a in args], cwd=ROOT, capture_output=True, text=True, **kw)
    if result.returncode:
        # Tool output may contain private paths/IDs; do not copy it into evidence.
        raise RuntimeError('Command failed: ' + Path(str(args[0])).name)
    return result.stdout + result.stderr


def save(path, value):
    temporary = path.with_suffix('.tmp')
    temporary.write_text(json.dumps(value, sort_keys=True, indent=2) + '\n')
    temporary.replace(path)


def build():
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT'])
    bt = sdk / 'build-tools/36.0.0'
    platform = sdk / 'platforms/android-37.0/android.jar'
    classes, dex = OUT / 'classes', OUT / 'dex'
    classes.mkdir(exist_ok=True); dex.mkdir(exist_ok=True)
    run([Path(os.environ['JAVA_HOME']) / 'bin/javac', '-source', '17', '-target', '17',
         '-classpath', platform, '-d', classes, ROOT / 'tools/device/SyncthingE2eRegression.java'])
    run([bt / 'd8', '--min-api', '26', '--lib', platform, '--output', dex,
         *sorted(classes.glob('org/totipo/syncthingqualification/*.class'))])
    manifest = OUT / 'AndroidManifest.xml'
    manifest.write_text(f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{PACKAGE}">
<uses-sdk android:minSdkVersion="26" android:targetSdkVersion="37" />
<application android:label="Totipo M3D evidence observer" android:debuggable="true" />
<instrumentation android:name="{PACKAGE}.SyncthingE2eRegression" android:targetPackage="org.totipo.android" />
</manifest>\n''')
    apk = OUT / 'observer-unsigned.apk'
    run([bt / 'aapt2', 'link', '-I', platform, '--manifest', manifest, '-o', apk])
    with zipfile.ZipFile(apk, 'a') as archive:
        archive.write(dex / 'classes.dex', 'classes.dex')
    print('Standalone observation APK built; production APK/source untouched.')


def sign(only_observer=False):
    bt = Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT']) / 'build-tools/36.0.0'
    def cert(apk):
        output = run([bt / 'apksigner', 'verify', '--print-certs', apk])
        match = re.search(r'certificate SHA-256 digest: ([0-9a-f]+)', output)
        if not match: raise RuntimeError('Signer unavailable')
        return match[1]
    expected = cert(ROOT / '.gradle/m3c-saf/release-signed.apk')
    sources = [(OUT / 'observer-unsigned.apk', 'observer')]
    if not only_observer:
        sources.insert(0, (ROOT / 'app/build/outputs/apk/release/app-release-unsigned.apk', 'release'))
    for source, name in sources:
        aligned, signed = OUT / (name + '-aligned.apk'), OUT / (name + '-signed.apk')
        run([bt / 'zipalign', '-f', '-P', '16', '4', source, aligned])
        run([bt / 'apksigner', 'sign', '--ks', ROOT / '.gradle/m1l-inspection/m1k-debug.keystore',
             '--ks-pass', 'file:' + str(ROOT / '.gradle/m1m-inspection/signing-password'),
             '--v1-signing-enabled', 'false', '--v4-signing-enabled', 'false', '--out', signed, aligned])
        if cert(signed) != expected: raise RuntimeError('Signer mismatch; installation blocked')
        run([bt / 'zipalign', '-c', '-P', '16', '4', signed])
        if name == 'release': run(['python3', ROOT / 'tools/verify-apk.py', signed, '--no-debug-probe'])

    (OUT / 'signer.txt').write_text('qualification certificate SHA-256=' + expected + '\n')
    print('Release and separate observer signed with the historical M3C qualification certificate.')


def install():
    bt = Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT']) / 'build-tools/36.0.0'
    installed = run(['adb', 'shell', 'pm', 'path', 'org.totipo.android']).splitlines()
    if len(installed) != 1 or not installed[0].startswith('package:'):
        raise RuntimeError('Require existing qualified application APK')
    run(['adb', 'pull', installed[0][8:], OUT / 'installed-before.apk'])
    def digest(apk):
        return re.search(r'certificate SHA-256 digest: ([0-9a-f]+)',
                         run([bt / 'apksigner', 'verify', '--print-certs', apk])).group(1)
    if digest(OUT / 'installed-before.apk') != digest(OUT / 'release-signed.apk'):
        raise RuntimeError('Installed signer mismatch; installation blocked')
    for name in ['release', 'observer']:
        if 'Success' not in run(['adb', 'install', '--no-incremental', '-r', OUT / (name + '-signed.apk')]):
            raise RuntimeError('Installation failed')
    print('Signed release and separate observer installed; app data retained.')


def serve(folder):
    # Instrumentation starts a new process: start this once before A. Keep alive until J.
    process = subprocess.Popen(['adb', 'shell', 'am', 'instrument', '-w', '-e', 'folder', folder, COMPONENT],
                               stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    try:
        for line in process.stdout:
            if 'M3D_JSON ' not in line: continue
            value = json.loads(line.split('M3D_JSON ', 1)[1])
            label = value.get('checkpoint', 'observer')
            if not re.fullmatch(r'[A-Za-z0-9_-]{1,80}', label): raise RuntimeError('Invalid evidence label')
            save(OUT / (label + '.json'), value)
            print(json.dumps({'checkpoint': label, 'failure': value.get('failure'), 'ready': value.get('ready')}), flush=True)
    finally:
        process.terminate()
    if process.wait(): raise RuntimeError('Observer disconnected')


def capture(label):
    path = OUT / (label + '.json')
    if path.exists(): raise RuntimeError('Evidence exists; choose a fresh checkpoint label')
    run(['adb', 'shell', 'am', 'broadcast', '-a', ACTION, '-p', 'org.totipo.android', '--es', 'label', label])
    deadline = time.monotonic() + 60
    while not path.exists() and time.monotonic() < deadline: time.sleep(.2)
    if not path.exists(): raise RuntimeError('No observer response; start serve and confirm disposable folder binding')
    value = json.loads(path.read_text())
    if 'failure' in value: raise RuntimeError('Observer refused capture: ' + value['failure'])
    if not value['provider_stable_during_capture']: raise RuntimeError('Provider changed during capture; preserve evidence and inspect transport')
    return value


def validate(before, after, args):
    failures = []
    def require(condition, fact):
        if not condition: failures.append(fact)
    provider = after['provider']; rows = provider['inventory']; local = after['local']
    require(provider['fixture'] == before['provider']['fixture'], 'same disposable fixture')
    require(local['binding'] == 'READY', 'transport binding READY')
    require(provider['persisted_read'], 'persisted READ')
    require(provider['coverage'] == 'COMPLETE', 'complete provider inventory')
    require(all(r['status'] == 'READABLE' for r in rows), 'all provider bytes readable')
    if args.writable: require(provider['persisted_write'], 'persisted WRITE')
    if args.layout == 'empty': require(rows == [], 'provider empty')
    if args.layout in ('vault', 'initialized', 'objects'):
        vaults = [r for r in rows if r['name'] == 'vault']
        namespaces = [r for r in rows if r['name'] == 'objects-v1']
        objects = [r for r in rows if re.fullmatch(r'objects-v1/[0-9a-f]{64}', r['name'])]
        require(len(vaults) == 1 and vaults[0].get('size') == 87, 'one canonical 87-byte vault')
        require(len(namespaces) == 1 and namespaces[0]['kind'] == 'directory', 'one objects-v1 directory')
        require(all(r.get('size') == 1024 for r in objects), 'canonical object sizes 1024')
        if args.layout == 'initialized': require(not objects, 'Initialize did not publish objects')
        if args.layout == 'objects': require(bool(objects), 'nonempty canonical objects')
    if args.tokens is not None: require(local.get('token_count') == args.tokens, 'token count')
    if getattr(args, 'deleted', None) is not None:
        require(local.get('deleted_count') == args.deleted, 'tombstone count')
    for account in args.account:
        require(any(a['account'] == account for t in local.get('tokens', []) for a in t['alternatives']), 'public account ' + account)
    for account in args.absent_account:
        require(all(a['account'] != account for t in local.get('tokens', []) for a in t['alternatives']), 'deleted/absent account ' + account)
    if args.semantic_unchanged:
        def projection(state):
            return {key: sorted(json.dumps(t, sort_keys=True) for t in state.get(key, []))
                    for key in ('tokens', 'deleted_tokens')}
        require(projection(local) == projection(before['local']), 'semantic projection unchanged')
    if args.local_unchanged:
        require(local.get('objects') == before['local'].get('objects'), 'local canonical objects unchanged')
    if args.unresolved is not None:
        require(sum(t['unresolved'] for t in local.get('tokens', [])) == args.unresolved, 'unresolved count')
    if args.conflicts is not None:
        require(sum(t['conflict'] for t in local.get('tokens', [])) == args.conflicts, 'conflict count')
    if args.local_empty:
        require(local.get('state') == 'NO_LOCAL_VAULT' and local.get('vault_present') is False
                and local.get('local_object_entries') == 0, 'no local vault or objects')
    if args.state: require(local['state'] == args.state, 'controller state')
    if args.same_session:
        require(bool(local.get('session')) and local.get('session') == before['local'].get('session'), 'same live session')
    if args.vault_unchanged:
        require(bool(local.get('vault_sha256')) and local.get('vault_sha256') == before['local'].get('vault_sha256'), 'local VAULT invariant')
        require([r for r in rows if r['name'] == 'vault'] ==
                [r for r in before['provider']['inventory'] if r['name'] == 'vault'], 'provider VAULT invariant')
    if args.match:
        hashes = [r.get('sha256') for r in rows if r['name'] == 'vault']
        require(hashes == [local.get('vault_sha256')] and local.get('session_local_vault_id_equal') is True
                and local.get('provider_local_vault_bytes_equal') is True and local.get('session_provider_vault_id_equal') is True,
                'exact provider/local VAULT and session identity')
    if args.provider == 'unchanged': require(rows == before['provider']['inventory'], 'provider unchanged')
    if args.provider == 'gains':
        old = {r['name']: r for r in before['provider']['inventory']}
        new = {r['name']: r for r in rows}
        require(old.keys() < new.keys() and all(new[k] == v for k, v in old.items()), 'immutable provider gain')
    if args.different_vault:
        provider_hashes = [r.get('sha256') for r in rows if r['name'] == 'vault']
        require(len(provider_hashes) == 1 and bool(local.get('vault_sha256'))
                and provider_hashes[0] != local['vault_sha256']
                and local.get('session_provider_vault_id_equal') is False, 'distinct provider/local vault identities')
        require(bool(local.get('session')) and local.get('session') == before['local'].get('session'), 'different-vault same session')
        require(local.get('tokens') == before['local'].get('tokens'), 'different-vault semantic projection unchanged')
        require(local['sync_message'] == 'Sync folder belongs to a different Totipo vault.', 'different-vault status')
        require(rows == before['provider']['inventory'], 'different-vault zero provider mutation')
        require(local.get('objects') == before['local'].get('objects'), 'different-vault zero local import')
        require(local.get('vault_sha256') == before['local'].get('vault_sha256'), 'different-vault local VAULT unchanged')
    return failures


def main():
    global OUT
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence-dir', type=Path, default=OUT,
                        help='Separate ignored evidence root for a new qualification run')
    parser.add_argument('command', choices=['build', 'sign', 'install', 'serve', 'capture', 'begin', 'finish', 'restart'])
    parser.add_argument('--observer-only', action='store_true', help='Sign only the standalone observer without reading production build outputs')
    parser.add_argument('--folder', choices=FOLDERS, default=FOLDERS[0])
    parser.add_argument('--label', default='inspection')
    parser.add_argument('--action')
    parser.add_argument('--human', help='Public human UI observation only; no passwords/secrets')
    parser.add_argument('--transport', choices=['up-to-date-both', 'paused-both', 'resumed-both', 'not-required'])
    parser.add_argument('--desktop', help='Public desktop UI observation; no internals inferred')
    parser.add_argument('--tokens', type=int)
    parser.add_argument('--deleted', type=int, help='Expected current unambiguous tombstones, outside the live list')
    parser.add_argument('--account', action='append', default=[])
    parser.add_argument('--conflicts', type=int)
    parser.add_argument('--unresolved', type=int)
    parser.add_argument('--absent-account', action='append', default=[])
    parser.add_argument('--state')
    parser.add_argument('--layout', choices=['empty', 'vault', 'initialized', 'objects'])
    parser.add_argument('--provider', choices=['unchanged', 'gains'])
    for flag in ['same-session', 'vault-unchanged', 'match', 'local-empty', 'writable', 'different-vault', 'semantic-unchanged', 'local-unchanged']:
        parser.add_argument('--' + flag, action='store_true')
    args = parser.parse_args()
    OUT = args.evidence_dir.resolve()
    if not OUT.is_relative_to((ROOT / '.gradle').resolve()):
        parser.error('Evidence directory must be inside ignored .gradle')
    OUT.mkdir(parents=True, exist_ok=True)
    if not re.fullmatch(r'[A-Za-z0-9_-]{1,64}', args.label): parser.error('Invalid label')
    if args.command == 'build': return build()
    if args.command == 'sign': return sign(args.observer_only)
    if args.command == 'install': return install()
    if args.command == 'serve': return serve(args.folder)
    if args.command == 'capture':
        capture(args.label); print('Captured sanitized Android/provider evidence.'); return
    if (OUT / 'FAILED.json').exists(): raise RuntimeError('Qualification stopped at substantive failure; preserve evidence')
    if args.command == 'restart':
        run(['adb', 'shell', 'am', 'force-stop', 'org.totipo.android'])
        run(['adb', 'shell', 'am', 'start', '-n', 'org.totipo.android/.MainActivity'])
        print('App restarted without data reset. Observer must be restarted; session continuity ends here.'); return
    pending = OUT / (args.label + '-pending.json')
    if args.command == 'begin':
        if not args.action: parser.error('begin requires --action')
        if pending.exists(): raise RuntimeError('Checkpoint already pending')
        before = capture(args.label + '-before')
        save(pending, {'before': before, 'action': args.action})
        print('HUMAN ACTION: ' + args.action); return
    if not args.human or not args.transport: parser.error('finish requires --human and explicit --transport')
    before = json.loads(pending.read_text())['before']
    after = capture(args.label + '-after')
    failures = validate(before, after, args)
    # Evidence collection alone is never an assertion PASS.
    asserted = any([args.tokens is not None, args.deleted is not None, args.conflicts is not None, args.state, args.layout, args.provider,
                    args.same_session, args.vault_unchanged, args.match, args.local_empty, args.different_vault,
                    args.semantic_unchanged, args.local_unchanged, args.unresolved is not None, args.absent_account])
    result = {'checkpoint': args.label, 'android_result': 'FAIL' if failures else ('PASS' if asserted else 'CAPTURED_ONLY'),
              'AGENT-VERIFIED': {'failures': failures, 'before': args.label + '-before.json',
                                 'after': args.label + '-after.json'},
              'failures': failures, 'expectations': {k: str(v) if isinstance(v, Path) else v
                                                    for k, v in vars(args).items()}, 'HUMAN-OBSERVED': args.human,
              'HUMAN-DESKTOP-OBSERVED': args.desktop,
              'HUMAN-REPORTED TRANSPORT': args.transport}
    save(OUT / ('scenario-' + args.label + '.json'), result)
    with (OUT / 'human-observations.txt').open('a') as stream:
        stream.write(json.dumps({k: v for k, v in result.items() if k.startswith('HUMAN') or k == 'checkpoint'}) + '\n')
    if failures:
        save(OUT / 'FAILED.json', result)
        raise RuntimeError('Scenario FAIL; suite stopped: ' + ', '.join(failures))
    print(result['android_result'] + ': ' + args.label)


if __name__ == '__main__':
    try: main()
    except (RuntimeError, OSError, ValueError) as failure:
        print(str(failure), file=sys.stderr); sys.exit(1)
