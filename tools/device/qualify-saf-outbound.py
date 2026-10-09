#!/usr/bin/env python3
"""Sign/install with the existing qualified signer; retain app data and the persisted tree.
All token authoring is isolated. Only the disposable provider fixture gets new objects.
No picker, Drive, or user's canonical vault modification.
"""
import os
from pathlib import Path
import re
import subprocess

workspace = Path(__file__).resolve().parents[2]
output = workspace / '.gradle/m3b-saf'
bt = Path(os.environ['ANDROID_HOME']) / 'build-tools/36.0.0'


def run(args):
    result = subprocess.run([str(arg) for arg in args], cwd=workspace, capture_output=True, text=True)
    if result.returncode:
        raise SystemExit('Qualification command failed: ' + Path(str(args[0])).name)
    return result.stdout + result.stderr


def cert(apk):
    result = run([bt / 'apksigner', 'verify', '--print-certs', apk])
    match = re.search(r'certificate SHA-256 digest: ([0-9a-f]+)', result)
    if not match:
        raise SystemExit('Certificate unavailable')
    return match[1]


run(['python3', workspace / 'tools/device/build-saf-outbound-tests.py'])
installed = run(['adb', 'shell', 'pm', 'path', 'org.totipo.android']).splitlines()[0].removeprefix('package:')
run(['adb', 'pull', installed, output / 'installed-before.apk'])
expected = cert(output / 'installed-before.apk')
for source, name in [(workspace / 'app/build/outputs/apk/release/app-release-unsigned.apk', 'release'),
                     (output / 'tests-unsigned.apk', 'tests')]:
    aligned = output / (name + '-aligned.apk')
    signed = output / (name + '-signed.apk')
    run([bt / 'zipalign', '-f', '-P', '16', '4', source, aligned])
    run([bt / 'apksigner', 'sign', '--ks', workspace / '.gradle/m1l-inspection/m1k-debug.keystore',
         '--ks-pass', 'file:' + str(workspace / '.gradle/m1m-inspection/signing-password'),
         '--v1-signing-enabled', 'false', '--v4-signing-enabled', 'false', '--out', signed, aligned])
    if cert(signed) != expected:
        raise SystemExit('Signer mismatch; installation blocked')
    run([bt / 'zipalign', '-c', '-P', '16', '4', signed])
    if name == 'release':
        run(['python3', workspace / 'tools/verify-apk.py', signed, '--no-debug-probe'])
    if 'Success' not in run(['adb', 'install', '--no-incremental', '-r', signed]):
        raise SystemExit('Install did not succeed')
    print(name + ': signature/alignment/install PASS; app data retained', flush=True)
component = 'org.totipo.safqualification/org.totipo.safqualification.SafOutboundRegression'
inspection = run(['adb', 'shell', 'am', 'instrument', '-w', '-e', 'inspectOnly', 'true', component])
(output / 'permission-inspection.txt').write_text(inspection)
print(inspection, flush=True)
if 'persisted READ + WRITE inspection PASS' not in inspection:
    raise SystemExit('Persisted permission qualification failed; no publication attempted')
qualification = run(['adb', 'shell', 'am', 'instrument', '-w', component])
(output / 'device-tests.txt').write_text(qualification)
print(qualification, flush=True)
if 'M3B PASS checks=' not in qualification:
    raise SystemExit('Device qualification failed')
run(['adb', 'uninstall', 'org.totipo.safqualification'])
print('Standalone harness removed; signed release retained', flush=True)
