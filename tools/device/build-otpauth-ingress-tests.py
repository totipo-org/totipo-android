#!/usr/bin/env python3
"""Build standalone, framework-only production ingress instrumentation. Does not sign/install/run it."""
from pathlib import Path
from android_harness import AndroidToolchain, compile_java, dex_classes, link_manifest, append_entries

workspace = Path(__file__).resolve().parents[2]
tc = AndroidToolchain.discover()
platform = tc.platform
output = workspace / '.gradle/m2d-ingress'
classes = output / 'classes'
dex = output / 'dex'
classes.mkdir(parents=True, exist_ok=True)
dex.mkdir(parents=True, exist_ok=True)
sources = [workspace / 'tools/device' / (name + '.java')
           for name in ('OtpAuthIngressRegression', 'OtpAuthUiObserver')]
class_files = compile_java(tc, sources, classes, [platform],
                           ['org/totipo/ingresstest/' + name for name in ('OtpAuthIngressRegression', 'OtpAuthUiObserver')])
dex_classes(tc, class_files, dex)
manifest = output / 'AndroidManifest.xml'
manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.totipo.ingresstest">
  <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="37" />
  <application android:label="Totipo public ingress regression tests" android:debuggable="true" />
  <instrumentation android:name="org.totipo.ingresstest.OtpAuthIngressRegression" android:targetPackage="org.totipo.android" />
  <instrumentation android:name="org.totipo.ingresstest.OtpAuthUiObserver" android:targetPackage="org.totipo.ingresstest" />
</manifest>
''')
apk = output / 'tests-unsigned.apk'
link_manifest(tc, manifest, apk)
append_entries(apk, [(dex / 'classes.dex', 'classes.dex')])
print('Standalone production ingress test APK built using pinned Android SDK only: ' + str(apk.relative_to(workspace)))
