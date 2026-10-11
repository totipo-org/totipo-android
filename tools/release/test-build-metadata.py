#!/usr/bin/env python3
"""Exercise the exact Gradle metadata parser in a disposable minimal Gradle project.

One offline Gradle invocation; no source edits, Android build or nested package
build. Final APK qualification separately proves the Android DSL receives the canonical VERSION_CODE/VERSION.
"""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
app = (root / 'app/build.gradle.kts').read_text()
parser = app[app.index('val versionCodeText ='):app.index('\nandroid {')]
parser = parser.replace('file("VERSION_CODE")', 'file(name)')
version_parser = (root / 'build.gradle.kts').read_text().split('val versionText =', 1)[1].split('\ndependencyLocking', 1)[0]
invalid = [b'', b'2', b' 2\n', b'2 \n', b'2\r\n', b'\n2\n', b'+2\n', b'-2\n', b'2.0\n',
           b'02\n', b'0\n', b'2100000001\n', b'999999999999999999999999999999\n']
with tempfile.TemporaryDirectory(prefix='totipo-gradle-metadata-') as tmp:
    work = Path(tmp)
    (work / 'settings.gradle.kts').write_text('rootProject.name = "metadata-fixture"\n')
    (work / 'VERSION').write_bytes(b'9.8.7\n')
    (work / 'valid').write_bytes(b'2\n')
    for n, data in enumerate(invalid): (work / str(n)).write_bytes(data)
    (work / 'build.gradle.kts').write_text('''
fun parseCode(name: String): Int {
''' + parser + '''
    return releaseVersionCode
}
val versionText =''' + version_parser + '''
check(parseCode("valid") == 2)
check(rootProject.version.toString() == "9.8.7")
''' + '\n'.join(f'check(runCatching {{ parseCode("{n}") }}.isFailure) {{ "Accepted invalid case {n}" }}' for n in range(len(invalid))) + '''
check(runCatching { parseCode("missing") }.isFailure) { "Used a default" }
tasks.register("verifyMetadata") { doLast { println("Metadata parser: 2 / synthetic VERSION 9.8.7; 13 malformed inputs and missing file rejected") } }
''')
    subprocess.run([str(root / 'gradlew'), '--offline', '--no-configuration-cache', '-p', str(work),
                    'verifyMetadata'], cwd=root, check=True)
