#!/usr/bin/env python3
"""Final-only isolated security qualification from qualified production DEX.

--build-only compiles the harness without packaging/installing an app.
--prepare packages final strict-clean release DEX/resources, signs with a test-only key,
and installs ONE isolated test package. It never replaces org.totipo.android.
--phase lifecycle runs the existing narrow token lifecycle regression.
--phase first and --phase restart require human strong biometric interactions.
--remove removes only the isolated disposable test package, including its Keystore keys.
The private deadline override exists only in SecurityRegression, never release DEX.
"""
import argparse
import os
from pathlib import Path
import subprocess
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
parser.add_argument('--phase', choices=['lifecycle', 'first', 'restart'])
parser.add_argument('--remove', action='store_true')
args = parser.parse_args()

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
    names = ['TokenLifecycleRegression', 'SecurityRegression']
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
</application>
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
        existing = adb('shell', 'pm', 'path', package, capture_output=True, text=True, check=False)
        assert existing.returncode in (0, 1), 'Unable to inspect installed test package'
        assert bool(existing.stdout.strip()) == args.update_harness, 'Use --prepare for a new isolated package or --update-harness for a physical harness correction'
        adb('install', *(['-r'] if args.update_harness else []), '-t', '--no-incremental', signed)
        (out / 'qualified-source.sha256').write_text(__import__('hashlib').sha256(source_apk.read_bytes()).hexdigest() + '\n')
if args.phase:
    adb('shell', 'am', 'force-stop', package)
    name = 'TokenLifecycleRegression' if args.phase == 'lifecycle' else 'SecurityRegression'
    result = adb('shell', 'am', 'instrument', '-w', '-e', 'phase', args.phase, package + '/org.totipo.android.' + name,
                 capture_output=True, text=True)
    (out / (args.phase + '.log')).write_text(result.stdout + result.stderr)
    print(result.stdout, end='', flush=True)
    expected = 'TOKEN LIFECYCLE PASS' if args.phase == 'lifecycle' else 'SECURITY_PASS'
    assert expected in result.stdout, 'Physical qualification failed; inspect the phase log'
if args.remove:
    adb('uninstall', package)
