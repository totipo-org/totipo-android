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

root = Path(__file__).resolve().parents[2]
out = root / '.gradle/security-qualification'
out.mkdir(parents=True, exist_ok=True)
sdk = Path(os.environ['ANDROID_HOME'])
bt = sdk / 'build-tools/36.0.0'
platform = sdk / 'platforms/android-37.0/android.jar'
java = Path(os.environ['JAVA_HOME']) / 'bin'
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
    run([java / 'javac', '-source', '17', '-target', '17', '-classpath', os.pathsep.join(map(str, [platform, app, *jars])), '-d', classes,
         root / 'tools/device/TokenLifecycleRegression.java', root / 'tools/device/SecurityRegression.java'])
    if args.prepare or args.update_harness:
        run([bt / 'd8', '--min-api', '30', '--lib', platform, '--classpath', app,
             *sum([['--classpath', str(jar)] for jar in jars], []), '--output', dex, *classes.rglob('*.class')])
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
        run([bt / 'aapt2', 'link', '-I', platform, '--manifest', manifest, '-o', manifest_apk])
        source_apk = root / 'app/build/outputs/apk/release/app-release-unsigned.apk'
        with zipfile.ZipFile(unsigned, 'w') as target, zipfile.ZipFile(source_apk) as source, zipfile.ZipFile(manifest_apk) as linked:
            target.writestr('AndroidManifest.xml', linked.read('AndroidManifest.xml'))
            app_dex = [n for n in source.namelist() if n.startswith('classes') and n.endswith('.dex')]
            for name in source.namelist():
                if name in app_dex or name == 'resources.arsc' or name.startswith('res/'):
                    target.writestr(name, source.read(name))
            target.write(dex / 'classes.dex', 'classes' + str(len(app_dex) + 1) + '.dex')
        run([bt / 'zipalign', '-f', '-P', '16', '4', unsigned, aligned])
        key = out / 'test-only.p12'
        if not key.exists():
            run([java / 'keytool', '-genkeypair', '-keystore', key, '-storepass', 'test-only', '-keypass', 'test-only', '-alias', 'security-tests', '-dname', 'CN=Disposable Security Tests', '-keyalg', 'RSA', '-validity', '3650'])
        run([bt / 'apksigner', 'sign', '--ks', key, '--ks-pass', 'pass:test-only', '--out', signed, aligned])
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
