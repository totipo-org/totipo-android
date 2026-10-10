#!/usr/bin/env python3
"""Install signed current release and isolated lifecycle instrumentation; retain app data.
No SAF tree, real vault, M3D evidence, or external transport is modified.
"""
import hashlib
import os
from pathlib import Path
import re
import subprocess

root = Path(__file__).resolve().parents[2]
out = root / '.gradle/token-lifecycle'
bt = Path(os.environ['ANDROID_HOME']) / 'build-tools/36.0.0'
def run(args):
    value = subprocess.run(list(map(str, args)), cwd=root, capture_output=True, text=True)
    if value.returncode:
        raise RuntimeError('Qualification command failed: ' + str(args[0]) + '\n' + value.stdout + value.stderr)
    return value.stdout + value.stderr

def cert(apk):
    value = run([bt / 'apksigner', 'verify', '--print-certs', apk])
    return re.search(r'certificate SHA-256 digest: ([0-9a-f]+)', value).group(1)

window = run(['adb', 'shell', 'dumpsys', 'window'])
if 'mDreamingLockscreen=true' in window or 'mShowingLockscreen=true' in window:
    raise SystemExit('Unlock the connected Pixel before physical UI qualification; no installation attempted.')
original_timeout = run(['adb', 'shell', 'settings', 'get', 'system', 'screen_off_timeout']).strip()
assert original_timeout.isdigit(), 'Require a restorable screen timeout'
run(['adb', 'shell', 'settings', 'put', 'system', 'screen_off_timeout', '600000'])
try:
    print(run(['python3', root / 'tools/device/build-token-lifecycle-tests.py']), flush=True)
    installed = run(['adb', 'shell', 'pm', 'path', 'org.totipo.android']).strip().removeprefix('package:')
    run(['adb', 'pull', installed, out / 'installed-before.apk'])
    expected = cert(out / 'installed-before.apk')
    for source, name in [(root / 'app/build/outputs/apk/release/app-release-unsigned.apk', 'release'), (out / 'tests-unsigned.apk', 'tests')]:
        aligned, signed = out / (name + '-aligned.apk'), out / (name + '-signed.apk')
        run([bt / 'zipalign', '-f', '-P', '16', '4', source, aligned])
        run([bt / 'apksigner', 'sign', '--ks', root / '.gradle/m1l-inspection/m1k-debug.keystore', '--ks-pass', 'file:' + str(root / '.gradle/m1m-inspection/signing-password'), '--v1-signing-enabled', 'false', '--v4-signing-enabled', 'false', '--out', signed, aligned])
        assert cert(signed) == expected, 'Signer mismatch; installation blocked'
        run([bt / 'zipalign', '-c', '-P', '16', '4', signed])
        if name == 'release': print(run(['python3', root / 'tools/verify-apk.py', signed, '--no-debug-probe']), flush=True)
        assert 'Success' in run(['adb', 'install', '--no-incremental', '-r', signed])
    try:
        value = run(['adb', 'shell', 'am', 'instrument', '-w', 'org.totipo.lifecyclequalification/org.totipo.android.TokenLifecycleRegression'])
        (out / 'physical-qualification.txt').write_text(value)
        print(value, flush=True)
        assert 'TOKEN LIFECYCLE PASS checks=' in value, 'Physical qualification failed'
        installed = run(['adb', 'shell', 'pm', 'path', 'org.totipo.android']).strip().removeprefix('package:')
        run(['adb', 'pull', installed, out / 'installed-after.apk'])
        assert (out / 'installed-after.apk').read_bytes() == (out / 'release-signed.apk').read_bytes()
        (out / 'physical-identity.txt').write_text('signed release SHA-256=' + hashlib.sha256((out / 'installed-after.apk').read_bytes()).hexdigest() + '\ncertificate SHA-256=' + expected + '\n' + run(['adb', 'shell', 'getprop', 'ro.product.model']) + run(['adb', 'shell', 'getprop', 'ro.build.version.release']) + run(['adb', 'shell', 'getprop', 'ro.build.version.sdk']))
    finally:
        run(['adb', 'uninstall', 'org.totipo.lifecyclequalification'])
        run(['adb', 'shell', 'am', 'force-stop', 'org.totipo.android'])
        run(['adb', 'shell', 'am', 'start', '-n', 'org.totipo.android/.MainActivity'])
finally:
    run(['adb', 'shell', 'settings', 'put', 'system', 'screen_off_timeout', original_timeout])
