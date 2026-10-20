#!/usr/bin/env python3
"""Final-only isolated security qualification from qualified production DEX.

--build-only compiles the harness without packaging/installing an app.
--prepare packages final strict-clean release DEX/resources, signs with a test-only key,
and installs ONE isolated test package. It never replaces org.totipo.android.
--phase mv3-first and mv3-restart exercise local-only operations, per-entry SAF
configuration, grants, retirement and cold-process restoration; only newly allocated
external fixture folders are used and retained for review.
--phase retirement exercises A -> B through the Application authority with a blocked
provider lane, rotation, callback/picker isolation and independent passwords.
--phase lifecycle runs the existing narrow token lifecycle regression.
--phase registry checks managed registry persistence and sequential Java create/open.
--phase mv1-* runs the ordered cold-process password/UI/recovery/scoped-metadata
checks on a fresh synthetic fixture in this isolated package; run all seven in order.
--phase mv5-first, mv5-restart and mv5-recover exercise production selector/Create/Join
cancellation, password isolation, rename, Forget and retained-root recovery in order.
--phase mv4-first and mv4-restart require human strong biometric authorization
for three independent entries, retirement, equal passwords and cold restoration.
Run them in order on a fresh disposable package. --phase mv4-sync then checks
real SAF attachment, biometric reopen and disconnect; the human must select only
the newly allocated disposable folder.
--phase mv6-observe keeps one read-only observer alive for human integrated UI work.
Send AMV6_CAPTURE broadcasts to this disposable package with unique non-secret label
extras; STOP ends the observer. Captures are evidence only, never scenario PASS.
Only the exact totipo-test-sync/Totipo-AMV6-20261020-A/C/D trees are accepted.
--phase first and --phase restart are historical global-biometric phases, unavailable
in the A-MV1 production UI until per-vault biometric support is implemented.
--remove removes only the isolated disposable test package, including its Keystore keys.
The private deadline override exists only in SecurityRegression, never release DEX.
"""
import argparse
import os
from pathlib import Path
import subprocess
import shlex
import zipfile
from android_harness import AndroidToolchain, compile_java, dex_classes, link_manifest, append_entries, copy_apk_entries, test_sign

root = Path(__file__).resolve().parents[2]
out = root / '.gradle/security-qualification'
out.mkdir(parents=True, exist_ok=True)
tc = AndroidToolchain.discover()
sdk, bt, platform, java = tc.sdk, tc.bt, tc.platform, tc.java
package = 'org.totipo.securityqualification'
parser = argparse.ArgumentParser()
parser.add_argument('--serial', default='37311JEGR05916')
parser.add_argument('--build-only', action='store_true')
parser.add_argument('--prepare', action='store_true')
parser.add_argument('--update-harness', action='store_true', help='Update only the isolated test package, preserving disposable restart evidence')
parser.add_argument('--phase', choices=['retirement', 'registry', 'lifecycle', 'first', 'restart', 'mv1-first', 'mv1-restart', 'mv1-missing', 'mv1-corrupt', 'mv1-restore', 'mv1-legacy', 'mv1-finish', 'mv3-first', 'mv3-restart', 'mv4-first', 'mv4-restart', 'mv4-sync', 'mv5-first', 'mv5-restart', 'mv5-recover', 'mv5-layout', 'mv5-menu', 'mv6-observe'])
parser.add_argument('--remove', action='store_true')
args = parser.parse_args()
if args.phase in ('first', 'restart'):
    parser.error('A-MV1 production biometrics are disabled; historical global-credential phases require replacement in A-MV4')

def run(command, **kwargs):
    kwargs.setdefault('check', True)
    return subprocess.run(list(map(str, command)), cwd=root, **kwargs)

def adb(*command, **kwargs):
    return run([sdk / 'platform-tools/adb', '-s', args.serial, *command], **kwargs)

if args.build_only or args.prepare or args.update_harness:
    cache = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle'))) / 'caches/modules-2/files-2.1'
    jars = []
    for group, module, version in [('org.totipo', 'totipo-core', '0.2.0'), ('org.totipo', 'totipo-storage-nio', '0.2.0'), ('org.bouncycastle', 'bcprov-jdk18on', '1.86')]:
        candidates = list((cache / group / module / version).glob('*/*.jar'))
        assert len(candidates) == 1
        jars.extend(candidates)
    # Existing compiled sources are sufficient for a build-only development check.
    app = root / ('app/build/intermediates/javac/release/compileReleaseJavaWithJavac/classes' if args.prepare or args.update_harness else 'app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes')
    assert app.exists(), 'Run the relevant compile/final gate first'
    classes, dex = out / 'classes', out / 'dex'
    classes.mkdir(exist_ok=True)
    dex.mkdir(exist_ok=True)
    names = ['TokenLifecycleRegression', 'SecurityRegression', 'VaultRegistryRegression', 'VaultRegistryUiRegression', 'ControllerRetirementRegression', 'PerVaultSyncRegression', 'PerVaultBiometricRegression', 'MultiVaultUiRegression', 'IntegratedMultiVaultObserver']
    class_files = compile_java(tc, [root / 'tools/device' / (name + '.java') for name in names],
                               classes, [platform, app, *jars],
                               ['org/totipo/android/' + name for name in names])
    if args.prepare or args.update_harness:
        dex_classes(tc, class_files, dex, min_api=30, classpath=[app, *jars])
        manifest = out / 'AndroidManifest.xml'
        manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.totipo.securityqualification">
<uses-sdk android:minSdkVersion="30" android:targetSdkVersion="37" />
<uses-permission android:name="android.permission.USE_BIOMETRIC" />
<application android:name="org.totipo.android.TotipoApplication" android:label="Totipo disposable security tests" android:allowBackup="false" android:debuggable="false" android:testOnly="true" android:theme="@android:style/Theme.Material.Light.NoActionBar">
<activity android:name="org.totipo.android.MainActivity" android:exported="true" />
<activity android:name="org.totipo.android.MultiVaultUiRegression$IdleActivity" android:exported="true" />
<activity android:name="org.totipo.android.PerVaultSyncRegression$GrantActivity" android:exported="false" />
<activity android:name="org.totipo.android.OtpAuthEnrollmentActivity" android:exported="false" />
</application>
<instrumentation android:name="org.totipo.android.MultiVaultUiRegression" android:targetPackage="org.totipo.securityqualification" />
<instrumentation android:name="org.totipo.android.IntegratedMultiVaultObserver" android:targetPackage="org.totipo.securityqualification" />
<instrumentation android:name="org.totipo.android.PerVaultBiometricRegression" android:targetPackage="org.totipo.securityqualification" />
<instrumentation android:name="org.totipo.android.PerVaultSyncRegression" android:targetPackage="org.totipo.securityqualification" />
<instrumentation android:name="org.totipo.android.ControllerRetirementRegression" android:targetPackage="org.totipo.securityqualification" />
<instrumentation android:name="org.totipo.android.VaultRegistryUiRegression" android:targetPackage="org.totipo.securityqualification" />
<instrumentation android:name="org.totipo.android.VaultRegistryRegression" android:targetPackage="org.totipo.securityqualification" />
<instrumentation android:name="org.totipo.android.SecurityRegression" android:targetPackage="org.totipo.securityqualification" />
<instrumentation android:name="org.totipo.android.TokenLifecycleRegression" android:targetPackage="org.totipo.securityqualification" />
</manifest>''')
        unsigned, aligned, signed = out / 'unsigned.apk', out / 'aligned.apk', out / 'tests.apk'
        manifest_apk = out / 'manifest.apk'
        link_manifest(tc, manifest, manifest_apk)
        source_apk = root / 'app/build/outputs/apk/release/app-release-unsigned.apk'
        with zipfile.ZipFile(unsigned, 'w'):
            pass
        copy_apk_entries(manifest_apk, unsigned, ['AndroidManifest.xml'])
        with zipfile.ZipFile(source_apk) as source:
            app_dex = [n for n in source.namelist() if n.startswith('classes') and n.endswith('.dex')]
            selected = [n for n in source.namelist() if n in app_dex or n == 'resources.arsc' or n.startswith('res/')]
        copy_apk_entries(source_apk, unsigned, selected)
        append_entries(unsigned, [(dex / 'classes.dex', 'classes' + str(len(app_dex) + 1) + '.dex')])
        test_sign(tc, unsigned, aligned, signed, out / 'test-only.p12',
                  alias='security-tests', dname='CN=Disposable Security Tests', page_align=True)
        # Exact production payload receipt; signing/manifest substitution must preserve
        # every qualified DEX/resource byte in the disposable package.
        import hashlib
        import json
        with zipfile.ZipFile(source_apk) as source, zipfile.ZipFile(signed) as packaged:
            payload = {name: hashlib.sha256(source.read(name)).hexdigest() for name in selected}
            assert all(hashlib.sha256(packaged.read(name)).hexdigest() == digest
                       for name, digest in payload.items()), 'Production payload changed during qualification packaging'
        (out / 'device-payload-receipt.json').write_text(json.dumps({
            'source_sha256': hashlib.sha256(source_apk.read_bytes()).hexdigest(),
            'disposable_apk_sha256': hashlib.sha256(signed.read_bytes()).hexdigest(),
            'production_entries': payload,
        }, sort_keys=True, indent=2) + '\n')
        existing = adb('shell', 'pm', 'path', package, capture_output=True, text=True, check=False)
        assert existing.returncode in (0, 1), 'Unable to inspect installed test package'
        assert bool(existing.stdout.strip()) == args.update_harness, 'Use --prepare for a new isolated package or --update-harness for a physical harness correction'
        if args.prepare and (out / 'mv3-fixture.json').exists():
            import time
            (out / 'mv3-fixture.json').rename(out / ('mv3-fixture-' + str(time.time_ns()) + '.json'))
        adb('install', *(['-r'] if args.update_harness else []), '-t', '--no-incremental', signed)
        (out / 'qualified-source.sha256').write_text(__import__('hashlib').sha256(source_apk.read_bytes()).hexdigest() + '\n')
        adb('shell', 'am', 'start', '-n', shlex.quote(package + '/org.totipo.android.MultiVaultUiRegression$IdleActivity'), capture_output=True, text=True)
if args.phase:
    adb('shell', 'am', 'force-stop', package)
    extra = []
    if args.phase == 'mv4-sync':
        import json
        import uuid
        from urllib.parse import quote
        folder = 'Documents/Totipo-AMV4-' + uuid.uuid4().hex
        adb('shell', 'mkdir', '-p', '/sdcard/' + folder)
        uri = 'content://com.android.externalstorage.documents/tree/' + quote('primary:' + folder, safe='')
        (out / 'mv4-sync-fixture.json').write_text(json.dumps({'folder': folder, 'uri': uri, 'serial': args.serial}) + '\n')
        extra.extend(['-e', 'folder', uri])
        print('Disposable sync folder retained for review: ' + folder, flush=True)
    if args.phase.startswith('mv3-'):
        import json
        import uuid
        receipt = out / 'mv3-fixture.json'
        if args.phase == 'mv3-first':
            assert not receipt.exists(), 'Preserve existing fixture receipt; use a separate fresh disposable preparation'
            fixture_id = uuid.uuid4().hex
            fixture = 'Documents/Totipo-AMV3-' + fixture_id
            folders = [fixture + '/A-' + fixture_id, fixture + '/B-' + fixture_id]
            for folder in folders:
                adb('shell', 'mkdir', '-p', '/sdcard/' + folder)
            receipt.write_text(json.dumps({'folders': folders, 'serial': args.serial}) + '\n')
        evidence = json.loads(receipt.read_text())
        assert evidence['serial'] == args.serial
        from urllib.parse import quote
        for key, folder in zip(('folder_a', 'folder_b'), evidence['folders']):
            extra.extend(['-e', key, 'content://com.android.externalstorage.documents/tree/' + quote('primary:' + folder, safe='')])
        print('Disposable external folders retained for review: ' + ', '.join(evidence['folders']), flush=True)
    name = 'IntegratedMultiVaultObserver' if args.phase == 'mv6-observe' else 'MultiVaultUiRegression' if args.phase.startswith('mv5-') else 'PerVaultBiometricRegression' if args.phase.startswith('mv4-') else 'PerVaultSyncRegression' if args.phase.startswith('mv3-') else 'VaultRegistryUiRegression' if args.phase.startswith('mv1-') else {'retirement': 'ControllerRetirementRegression', 'registry': 'VaultRegistryRegression', 'lifecycle': 'TokenLifecycleRegression'}.get(args.phase, 'SecurityRegression')
    instrument = ['shell', 'am', 'instrument', '-w', '-e', 'phase', args.phase, *extra, package + '/org.totipo.android.' + name]
    if args.phase.startswith(('mv4-', 'mv5-', 'mv6-')):
        # Let the human see each real authentication step while preserving exact output.
        command = ['adb', '-s', args.serial, *map(str, instrument)]
        lines = []
        log_name = args.phase + ('-' + str(__import__('time').time_ns()) if args.phase == 'mv6-observe' else '') + '.log'
        with (out / log_name).open('w') as log:
            with subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True) as process:
                for line in process.stdout:
                    lines.append(line); log.write(line); log.flush(); print(line, end='', flush=True)
                result = subprocess.CompletedProcess(command, process.wait(), ''.join(lines), '')
        result.check_returncode()
    else:
        result = adb(*instrument, capture_output=True, text=True)
        (out / (args.phase + '.log')).write_text(result.stdout + result.stderr)
        print(result.stdout, end='', flush=True)
    # A plain test-only window holds no session/prompt authority between phases.
    adb('shell', 'am', 'start', '-n', shlex.quote(package + '/org.totipo.android.MultiVaultUiRegression$IdleActivity'), capture_output=True, text=True)
    expected = 'AMV6_OBSERVER_STOPPED' if args.phase == 'mv6-observe' else 'MULTI_VAULT_UI_PASS phase=' + args.phase if args.phase.startswith('mv5-') else 'PER_VAULT_BIOMETRIC_PASS phase=' + args.phase if args.phase.startswith('mv4-') else 'PER_VAULT_SYNC_PASS phase=' + args.phase if args.phase.startswith('mv3-') else 'MV1_UI_PASS phase=' + args.phase if args.phase.startswith('mv1-') else {'retirement': 'CONTROLLER_RETIREMENT_PASS', 'registry': 'VAULT_REGISTRY_PASS', 'lifecycle': 'TOKEN LIFECYCLE PASS'}.get(args.phase, 'SECURITY_PASS')
    assert expected in result.stdout, 'Physical qualification failed; inspect the phase log'
if args.remove:
    adb('uninstall', package)
