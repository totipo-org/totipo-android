#!/usr/bin/env python3
"""Run detached row assertions in a temporary self-targeted Android test package.
Requires the normal debug build, existing SDK/dependency cache, and one authorized adb device.
Does not install/launch Totipo or read its data. Installs/removes only its own test package.
"""
import os
from pathlib import Path
import subprocess
import zipfile

root = Path(__file__).resolve().parents[2]
out = root / '.gradle/row-reveal-device'
classes, dex = out / 'classes', out / 'dex'
classes.mkdir(parents=True, exist_ok=True)
dex.mkdir(exist_ok=True)
sdk = Path(os.environ['ANDROID_HOME'])
bt = sdk / 'build-tools/36.0.0'
platform = sdk / 'platforms/android-37.0/android.jar'
java = Path(os.environ['JAVA_HOME']) / 'bin'
cache = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle'))) / 'caches/modules-2/files-2.1'
core = list((cache / 'org.totipo/totipo-core/0.2.0').glob('*/*.jar'))
assert len(core) == 1
app = root / 'app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes'
app_apk = root / 'app/build/outputs/apk/debug/app-debug.apk'
assert app.exists() and app_apk.exists(), 'Run normal debug build first'
subprocess.run([str(java / 'javac'), '-source', '17', '-target', '17', '-classpath',
                os.pathsep.join(map(str, [platform, app, core[0]])), '-d', str(classes),
                str(root / 'tools/device/RowRevealRegression.java')], check=True)
subprocess.run([str(bt / 'd8'), '--min-api', '26', '--lib', str(platform),
                '--classpath', str(app), '--classpath', str(core[0]), '--output', str(dex),
                *map(str, classes.rglob('*.class'))], check=True)
package = 'org.totipo.rowrevealqualification'
manifest = out / 'AndroidManifest.xml'
manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.totipo.rowrevealqualification">
<uses-sdk android:minSdkVersion="26" android:targetSdkVersion="37" />
<application android:label="Totipo detached row tests" android:debuggable="true" />
<instrumentation android:name="org.totipo.android.RowRevealRegression" android:targetPackage="org.totipo.rowrevealqualification" />
</manifest>''')
unsigned = out / 'unsigned.apk'
subprocess.run([str(bt / 'aapt2'), 'link', '-I', str(platform), '--manifest', str(manifest), '-o', str(unsigned)], check=True)
with zipfile.ZipFile(unsigned, 'a') as target, zipfile.ZipFile(app_apk) as source:
    app_dex = [n for n in source.namelist() if n.startswith('classes') and n.endswith('.dex')]
    for name in app_dex:
        target.writestr(name, source.read(name))
    target.write(dex / 'classes.dex', 'classes' + str(len(app_dex) + 1) + '.dex')
aligned, signed = out / 'aligned.apk', out / 'tests.apk'
subprocess.run([str(bt / 'zipalign'), '-f', '4', str(unsigned), str(aligned)], check=True)
key = out / 'test-only.p12'
if not key.exists():
    subprocess.run([str(java / 'keytool'), '-genkeypair', '-keystore', str(key), '-storepass', 'test-only',
                    '-keypass', 'test-only', '-alias', 'row-tests', '-dname', 'CN=Detached Row Tests',
                    '-keyalg', 'RSA', '-validity', '3650'], check=True)
subprocess.run([str(bt / 'apksigner'), 'sign', '--ks', str(key), '--ks-pass', 'pass:test-only',
                '--out', str(signed), str(aligned)], check=True)
adb = str(sdk / 'platform-tools/adb')
# Refuse to replace an existing package; only remove a package installed by this run.
existing = subprocess.run([adb, 'shell', 'pm', 'path', package], text=True, capture_output=True)
assert existing.returncode in (0, 1) and not existing.stderr.strip(), existing.stderr
assert not existing.stdout.strip(), 'Test package already exists; inspect/remove it explicitly before running'
subprocess.run([adb, 'install', '--no-incremental', str(signed)], check=True)
try:
    result = subprocess.run([adb, 'shell', 'am', 'instrument', '-w',
                             package + '/org.totipo.android.RowRevealRegression'],
                            text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=60)
    print(result.stdout, end='', flush=True)
    assert result.returncode == 0 and 'ROW_REVEAL_PASS' in result.stdout, 'Android view assertions failed'
finally:
    subprocess.run([adb, 'uninstall', package], check=True)
