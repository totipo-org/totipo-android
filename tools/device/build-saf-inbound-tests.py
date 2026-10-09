#!/usr/bin/env python3
"""Build isolated public-data SAF instrumentation with existing SDK and pinned cached Java jars."""
import os
from pathlib import Path
import subprocess
import zipfile

workspace = Path(__file__).resolve().parents[2]
sdk = Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT'])
bt = sdk / 'build-tools/36.0.0'
java = Path(os.environ['JAVA_HOME']) / 'bin'
platform = sdk / 'platforms/android-37.0/android.jar'
output = workspace / '.gradle/m3a-saf'
output.mkdir(parents=True, exist_ok=True)
classes = output / 'classes'
dex = output / 'dex'
classes.mkdir(exist_ok=True)
dex.mkdir(exist_ok=True)

def run(args):
    result = subprocess.run([str(arg) for arg in args], capture_output=True, text=True)
    if result.returncode:
        raise SystemExit('SAF fixture build failed at ' + Path(args[0]).name)

cache = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle'))) / 'caches/modules-2/files-2.1'
jars = []
for group, module, version in [('org.totipo', 'totipo-core', '0.2.0'),
                              ('org.totipo', 'totipo-storage-nio', '0.2.0'),
                              ('org.bouncycastle', 'bcprov-jdk18on', '1.86')]:
    candidates = list((cache / group / module / version).glob('*/*.jar'))
    assert len(candidates) == 1, 'Use the already-qualified Gradle cache'
    jars.extend(candidates)
classpath = os.pathsep.join(str(jar) for jar in jars)
fixture = output / 'fixture'
# Reuse the authored disposable fixture for repeatable imports and matching test assets.
run([java / 'javac', '-classpath', classpath, '-d', classes, workspace / 'tools/device/InboundFixture.java'])
if not fixture.exists():
    run([java / 'java', '-classpath', str(classes) + os.pathsep + classpath,
         'org.totipo.safqualification.InboundFixture', fixture])
assert (fixture / 'vault').stat().st_size == 87
objects = list((fixture / 'objects-v1').iterdir())
assert len(objects) == 3 and all(obj.stat().st_size == 1024 for obj in objects)
run([java / 'javac', '-source', '17', '-target', '17', '-classpath', platform,
     '-d', classes, workspace / 'tools/device/SafInboundRegression.java'])
run([bt / 'd8', '--min-api', '26', '--lib', platform, '--output', dex,
     *sorted(classes.glob('org/totipo/safqualification/SafInboundRegression*.class'))])
manifest = output / 'AndroidManifest.xml'
manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.totipo.safqualification">
  <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="37" />
  <application android:label="Totipo public SAF qualification" android:debuggable="true" />
  <instrumentation android:name="org.totipo.safqualification.SafInboundRegression" android:targetPackage="org.totipo.android" />
</manifest>
''')
apk = output / 'tests-unsigned.apk'
run([bt / 'aapt2', 'link', '-I', platform, '--manifest', manifest, '-o', apk])
with zipfile.ZipFile(apk, 'a') as package:
    package.write(dex / 'classes.dex', 'classes.dex')
    package.write(fixture / 'vault', 'assets/vault')
(output / 'remote-vault').write_bytes((fixture / 'vault').read_bytes())
print('SAF test APK and disposable public fixture built: .gradle/m3a-saf')
