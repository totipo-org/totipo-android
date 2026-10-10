#!/usr/bin/env python3
"""Build standalone physical product qualification with existing SDK/dependencies."""
import os
from pathlib import Path
import subprocess
import zipfile

root = Path(__file__).resolve().parents[2]
out = root / '.gradle/token-lifecycle'
out.mkdir(exist_ok=True)
sdk = Path(os.environ['ANDROID_HOME'])
bt = sdk / 'build-tools/36.0.0'
platform = sdk / 'platforms/android-37.0/android.jar'
java = Path(os.environ['JAVA_HOME']) / 'bin'
cache = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle'))) / 'caches/modules-2/files-2.1'
jars = []
for group, module, version in [('org.totipo', 'totipo-core', '0.2.0'), ('org.totipo', 'totipo-storage-nio', '0.2.0'), ('org.bouncycastle', 'bcprov-jdk18on', '1.86')]:
    candidates = list((cache / group / module / version).glob('*/*.jar'))
    assert len(candidates) == 1
    jars.extend(candidates)
app = root / 'app/build/intermediates/compile_app_classes_jar/release/bundleReleaseClassesToCompileJar/classes.jar'
if not app.exists():
    subprocess.run([str(root / 'gradlew'), '--offline', ':app:bundleReleaseClassesToCompileJar'], cwd=root, check=True, stdout=subprocess.DEVNULL)
assert app.exists(), 'Run the normal release build first'
classes, dex = out / 'device-classes', out / 'device-dex'
classes.mkdir(exist_ok=True)
dex.mkdir(exist_ok=True)
subprocess.run([str(java / 'javac'), '-source', '17', '-target', '17', '-classpath', os.pathsep.join(map(str, [platform, app, *jars])), '-d', str(classes), str(root / 'tools/device/TokenLifecycleRegression.java')], check=True)
subprocess.run([str(bt / 'd8'), '--min-api', '26', '--lib', str(platform), '--classpath', str(app), *sum([['--classpath', str(jar)] for jar in jars], []), '--output', str(dex), *map(str, classes.rglob('*.class'))], check=True)
manifest = out / 'device-manifest.xml'
manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.totipo.lifecyclequalification">
<uses-sdk android:minSdkVersion="30" android:targetSdkVersion="37" />
<application android:label="Totipo public lifecycle qualification" android:debuggable="true" />
<instrumentation android:name="org.totipo.android.TokenLifecycleRegression" android:targetPackage="org.totipo.android" />
</manifest>''')
apk = out / 'tests-unsigned.apk'
subprocess.run([str(bt / 'aapt2'), 'link', '-I', str(platform), '--manifest', str(manifest), '-o', str(apk)], check=True)
with zipfile.ZipFile(apk, 'a') as package:
    package.write(dex / 'classes.dex', 'classes.dex')
print('Standalone lifecycle test APK built in .gradle/token-lifecycle')
