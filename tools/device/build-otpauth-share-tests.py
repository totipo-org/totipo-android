#!/usr/bin/env python3
"""Build standalone, framework-only M2C2 instrumentation. Does not sign/install/run it."""
import os
from pathlib import Path
import subprocess
import zipfile

workspace = Path(__file__).resolve().parents[2]
sdk = Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT'])
java = Path(os.environ['JAVA_HOME']) / 'bin/javac'
platform = sdk / 'platforms/android-37.0/android.jar'
build_tools = sdk / 'build-tools/36.0.0'
output = workspace / '.gradle/m2c2-share'
classes = output / 'classes'
dex = output / 'dex'
classes.mkdir(parents=True, exist_ok=True)
dex.mkdir(parents=True, exist_ok=True)
# Only this harness's known class files; do not package arbitrary contents of the output directory.
for name in ('OtpAuthShareQualification', 'OtpAuthResolverObserver'):
    for previous in classes.glob('org/totipo/qualification/' + name + '*.class'):
        previous.unlink()

def run(args):
    result = subprocess.run([str(arg) for arg in args], capture_output=True, text=True)
    if result.returncode:
        # Never dump tool output (e.g. a javac source excerpt with the fixture).
        raise SystemExit('Standalone qualification build failed at ' + Path(args[0]).name)

sources = [workspace / 'tools/device' / (name + '.java')
           for name in ('OtpAuthShareQualification', 'OtpAuthResolverObserver')]
run([java, '-source', '17', '-target', '17', '-classpath', platform, '-d', classes, *sources])
class_files = sorted(classes.glob('org/totipo/qualification/OtpAuth*.class'))
run([build_tools / 'd8', '--min-api', '26', '--lib', platform, '--output', dex, *class_files])
manifest = output / 'AndroidManifest.xml'
manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.totipo.qualification">
  <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="37" />
  <application android:label="Totipo public share qualification tests" android:debuggable="true" />
  <instrumentation android:name="org.totipo.qualification.OtpAuthShareQualification" android:targetPackage="org.totipo.android" />
  <instrumentation android:name="org.totipo.qualification.OtpAuthResolverObserver" android:targetPackage="org.totipo.qualification" />
</manifest>
''')
apk = output / 'tests-unsigned.apk'
run([build_tools / 'aapt2', 'link', '-I', platform, '--manifest', manifest, '-o', apk])
with zipfile.ZipFile(apk, 'a') as package:
    package.write(dex / 'classes.dex', 'classes.dex')
print('Standalone M2C2 test APK built using pinned Android SDK only: ' + str(apk.relative_to(workspace)))
