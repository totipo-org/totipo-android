#!/usr/bin/env python3
"""Build isolated public-data SAF instrumentation with existing SDK and pinned cached Java jars."""
import os
from pathlib import Path
from android_harness import AndroidToolchain, run, compile_java, dex_classes, link_manifest, append_entries

workspace = Path(__file__).resolve().parents[2]
tc = AndroidToolchain.discover()
java = tc.java
platform = tc.platform
output = workspace / '.gradle/r19-saf'
output.mkdir(parents=True, exist_ok=True)
classes = output / 'classes'
dex = output / 'dex'
classes.mkdir(exist_ok=True)
dex.mkdir(exist_ok=True)

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
# New disposable r19 fixture, independent of the historical invalid provider VAULT.
compile_java(tc, [workspace / 'tools/device/InboundFixture.java'], classes, jars,
             ['org/totipo/safqualification/InboundFixture'], java17=False)
if not fixture.exists():
    run([java / 'java', '-classpath', str(classes) + os.pathsep + classpath,
         'org.totipo.safqualification.InboundFixture', fixture])
assert (fixture / 'vault').stat().st_size == 87
objects = list((fixture / 'objects-v1').iterdir())
assert len(objects) == 3 and all(obj.stat().st_size == 1024 for obj in objects)
names = ['SafOutboundRegression', 'LocalStoreQualification']
class_files = compile_java(tc, [workspace / 'tools/device' / (name + '.java') for name in names],
                           classes, [platform, *jars],
                           ['org/totipo/safqualification/' + name for name in names])
dex_classes(tc, class_files, dex)
manifest = output / 'AndroidManifest.xml'
manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.totipo.safqualification">
  <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="37" />
  <application android:label="Totipo public SAF qualification" android:debuggable="true" />
  <instrumentation android:name="org.totipo.safqualification.SafOutboundRegression" android:targetPackage="org.totipo.android" />
</manifest>
''')
apk = output / 'tests-unsigned.apk'
link_manifest(tc, manifest, apk)
append_entries(apk, [(dex / 'classes.dex', 'classes.dex'), (fixture / 'vault', 'assets/vault')])
(output / 'remote-vault').write_bytes((fixture / 'vault').read_bytes())
print('SAF test APK and disposable public fixture built: .gradle/r19-saf')
