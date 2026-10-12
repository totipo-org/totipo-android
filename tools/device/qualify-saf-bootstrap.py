#!/usr/bin/env python3
"""Sign/install with the existing qualified signer; retain app data and the persisted tree.
All token authoring is isolated. Only the disposable provider fixture gets new objects.
No picker, Drive, or user's canonical vault modification.
"""
from pathlib import Path
from android_harness import AndroidToolchain, provider_backup
from qualification_signing import load
import subprocess

workspace = Path(__file__).resolve().parents[2]
output = workspace / '.gradle/m3c-saf'
tc = AndroidToolchain.discover()
identity = load(workspace, tc)


def run(args):
    result = subprocess.run([str(arg) for arg in args], cwd=workspace, capture_output=True, text=True)
    if result.returncode:
        raise SystemExit('Qualification command failed: ' + Path(str(args[0])).name)
    return result.stdout + result.stderr


run(['python3', workspace / 'tools/device/build-saf-bootstrap-tests.py'])
installed = run(['adb', 'shell', 'pm', 'path', 'org.totipo.android']).splitlines()[0].removeprefix('package:')
run(['adb', 'pull', installed, output / 'installed-before.apk'])
identity.verify(tc, output / 'installed-before.apk')
for source, name in [(workspace / 'app/build/outputs/apk/release/app-release-unsigned.apk', 'release'),
                     (output / 'tests-unsigned.apk', 'tests')]:
    aligned = output / (name + '-aligned.apk')
    signed = output / (name + '-signed.apk')
    identity.sign(tc, source, aligned, signed)
    if name == 'release':
        run(['python3', workspace / 'tools/verify-apk.py', signed, '--no-debug-probe'])
# Validate both candidates before any device mutation. Preserve release-first install.
for name in ('release', 'tests'):
    signed = output / (name + '-signed.apk')
    identity.verify(tc, signed)
for name in ('release', 'tests'):
    signed = output / (name + '-signed.apk')
    if 'Success' not in run(['adb', 'install', '--no-incremental', '-r', signed]):
        raise SystemExit('Install did not succeed')
    print(name + ': signature/alignment/install PASS; app data retained', flush=True)
component = 'org.totipo.safqualification/org.totipo.safqualification.SafBootstrapRegression'
inspection = run(['adb', 'shell', 'am', 'instrument', '-w', '-e', 'inspectOnly', 'true', component])
(output / 'permission-inspection.txt').write_text(inspection)
print(inspection, flush=True)
if 'persisted READ + WRITE inspection PASS' not in inspection:
    raise SystemExit('Persisted permission qualification failed; no fixture mutation attempted')
# Only the historically designated disposable fixture is temporarily replaced.
# Preserve and restore it byte-for-byte; production app binding/data is untouched.
fixture_remote = '/sdcard/Documents/Totipo-M3A-Test'
try:
    with provider_backup(run, fixture_remote, output, ['vault', 'objects-v1']):
        # Root remains present so the persisted grant retains its identity.
        run(['adb', 'shell', 'rm', '-rf', fixture_remote + '/objects-v1'])
        try:
            run(['adb', 'shell', 'mkdir', '-p', fixture_remote])
            run(['adb', 'push', output / 'fixture/vault', fixture_remote + '/vault'])
            run(['adb', 'push', output / 'fixture/objects-v1', fixture_remote + '/objects-v1'])
            def qualify(mode):
                qualification = run(['adb', 'shell', 'am', 'instrument', '-w', '-e', 'mode', mode, component])
                (output / (mode + '-device-tests.txt')).write_text(qualification)
                print(qualification, flush=True)
                if 'M3C ' + mode + ' PASS checks=' not in qualification:
                    raise SystemExit('M3C device qualification failed: ' + mode)
            qualify('join')
            run(['adb', 'shell', 'rm', '-f', fixture_remote + '/vault'])
            run(['adb', 'shell', 'rm', '-rf', fixture_remote + '/objects-v1'])
            qualify('initialize')
            run(['adb', 'shell', 'rm', '-f', fixture_remote + '/vault'])
            qualify('orphan')
            run(['adb', 'push', output / 'fixture/vault', fixture_remote + '/vault'])
            qualify('different')
        finally:
            run(['adb', 'shell', 'am', 'force-stop', 'org.totipo.android'])
finally:
    run(['adb', 'uninstall', 'org.totipo.safqualification'])
    run(['adb', 'shell', 'am', 'start', '-n', 'org.totipo.android/.MainActivity'])
print('Original disposable fixture restored; isolated roots/harness removed; signed release retained', flush=True)
