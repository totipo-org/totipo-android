#!/usr/bin/env python3
"""Run detached row and MainActivity Sync layout assertions in a temporary self-targeted Android test package.
Requires the normal debug build, existing SDK/dependency cache, and one authorized adb device.
Does not install/launch Totipo or read its data. Installs/removes only its own test package.
Optional baseline comparison: ROW_REVEAL_APP_APK=<retained debug APK>,
SYNC_LAYOUT_BASELINE=1; SYNC_LAYOUT_ONLY=1 skips the established row suite.
SYNC_TERMINOLOGY_ONLY=1 runs only detached folder-management wording/layout checks.
"""
import os
from pathlib import Path
import subprocess
import zipfile
from android_harness import AndroidToolchain, compile_java, dex_classes, link_manifest, append_entries, copy_apk_entries, test_sign

root = Path(__file__).resolve().parents[2]
out = root / '.gradle/row-reveal-device'
classes, dex = out / 'classes', out / 'dex'
classes.mkdir(parents=True, exist_ok=True)
dex.mkdir(exist_ok=True)
tc = AndroidToolchain.discover()
sdk, bt, platform, java = tc.sdk, tc.bt, tc.platform, tc.java
cache = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle'))) / 'caches/modules-2/files-2.1'
core = list((cache / 'org.totipo/totipo-core/0.2.0').glob('*/*.jar'))
assert len(core) == 1
app = root / 'app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes'
app_apk = Path(os.environ.get('ROW_REVEAL_APP_APK', str(root / 'app/build/outputs/apk/debug/app-debug.apk')))
assert app.exists() and app_apk.exists(), 'Run normal debug build first'
class_files = compile_java(tc, [root / 'tools/device/RowRevealRegression.java'], classes,
                           [platform, app, core[0]], ['org/totipo/android/RowRevealRegression'])
dex_classes(tc, class_files, dex, classpath=[app, core[0]])
package = 'org.totipo.rowrevealqualification'
manifest = out / 'AndroidManifest.xml'
manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.totipo.rowrevealqualification">
<uses-sdk android:minSdkVersion="26" android:targetSdkVersion="37" />
<application android:label="Totipo detached row tests" android:debuggable="true" />
<instrumentation android:name="org.totipo.android.RowRevealRegression" android:targetPackage="org.totipo.rowrevealqualification" />
</manifest>''')
unsigned = out / 'unsigned.apk'
link_manifest(tc, manifest, unsigned)
with zipfile.ZipFile(app_apk) as source:
    app_dex = [n for n in source.namelist() if n.startswith('classes') and n.endswith('.dex')]
copy_apk_entries(app_apk, unsigned, app_dex)
append_entries(unsigned, [(dex / 'classes.dex', 'classes' + str(len(app_dex) + 1) + '.dex')])
aligned, signed = out / 'aligned.apk', out / 'tests.apk'
test_sign(tc, unsigned, aligned, signed, out / 'test-only.p12',
          alias='row-tests', dname='CN=Detached Row Tests')
adb = str(sdk / 'platform-tools/adb')
# Refuse to replace an existing package; only remove a package installed by this run.
existing = subprocess.run([adb, 'shell', 'pm', 'path', package], text=True, capture_output=True)
assert existing.returncode in (0, 1) and not existing.stderr.strip(), existing.stderr
assert not existing.stdout.strip(), 'Test package already exists; inspect/remove it explicitly before running'
subprocess.run([adb, 'install', '--no-incremental', str(signed)], check=True)
try:
    result = subprocess.run([adb, 'shell', 'am', 'instrument', '-w',
                             *(['-e', 'syncBaseline', 'true'] if os.environ.get('SYNC_LAYOUT_BASELINE') == '1' else []),
                             *(['-e', 'terminologyOnly', 'true'] if os.environ.get('SYNC_TERMINOLOGY_ONLY') == '1' else []),
                             *(['-e', 'syncOnly', 'true'] if os.environ.get('SYNC_LAYOUT_ONLY') == '1' else []),
                             package + '/org.totipo.android.RowRevealRegression'],
                            text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=420)
    print(result.stdout, end='', flush=True)
    assert result.returncode == 0 and 'ROW_REVEAL_PASS' in result.stdout, 'Android view assertions failed'
finally:
    subprocess.run([adb, 'uninstall', package], check=True)
