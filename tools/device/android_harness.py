"""Internal framework-test packaging mechanics. Callers own all Android semantics.

No release keys, repository root, dependency coordinates or package policy live here.
Inputs are explicit; tool failures deliberately omit potentially sensitive output.
"""
from contextlib import contextmanager
from dataclasses import dataclass
import hashlib
import os
from pathlib import Path
import re
import shutil
import ssl
import subprocess
import tempfile
import uuid
import zipfile


def run(args):
    try:
        result = subprocess.run(list(map(str, args)), capture_output=True, text=True)
    except OSError:
        raise SystemExit('Harness tool unavailable: ' + Path(args[0]).name) from None
    if result.returncode:
        raise SystemExit('Harness command failed: ' + Path(args[0]).name)
    return result.stdout + result.stderr


def require_files(paths):
    paths = tuple(map(Path, paths))
    if not paths or any(not path.is_file() for path in paths):
        raise SystemExit('Missing explicit harness input')
    return paths


@dataclass(frozen=True)
class AndroidToolchain:
    sdk: Path
    java: Path

    @classmethod
    def discover(cls):
        sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
        java = os.environ.get('JAVA_HOME')
        if not sdk or not java:
            raise SystemExit('Harness requires ANDROID_HOME/ANDROID_SDK_ROOT and JAVA_HOME')
        return cls(Path(sdk), Path(java) / 'bin')

    @property
    def platform(self):
        return require_files([self.sdk / 'platforms/android-37.0/android.jar'])[0]

    @property
    def bt(self):
        return self.sdk / 'build-tools/36.0.0'

    def tool(self, name):
        path = (self.java if name in ('java', 'javac', 'keytool') else self.bt) / name
        if not path.is_file() or not os.access(path, os.X_OK):
            raise SystemExit('Harness tool unavailable: ' + name)
        return path


def compile_java(tc, sources, classes, classpath, stems, *, java17=True):
    """Compile fresh, select named top-level classes plus their nested classes only.

    Keep other separately compiled families (e.g. a host fixture) in classes, but
    never return or dex them implicitly. A fresh workspace prevents stale inners.
    """
    sources = require_files(sources)
    stems = tuple(stems)
    if not stems or len(set(stems)) != len(stems):
        raise SystemExit('Require unique explicit class stems')
    classes.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='javac-', dir=classes.parent) as temporary:
        compiled = Path(temporary)
        run([tc.tool('javac'), *(['-source', '17', '-target', '17'] if java17 else []),
             '-classpath', os.pathsep.join(map(str, classpath)), '-d', compiled, *sources])
        selected = []
        for stem in stems:
            top = compiled / (stem + '.class')
            require_files([top])
            family = [top, *sorted(top.parent.glob(top.stem + '$*.class'))]
            for previous in [classes / (stem + '.class'), *sorted((classes / stem).parent.glob(top.stem + '$*.class'))]:
                previous.unlink(missing_ok=True)
            for source in family:
                target = classes / source.relative_to(compiled)
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(source, target)
                selected.append(target)
    return tuple(selected)


def dex_classes(tc, inputs, output, *, min_api=26, classpath=()):
    inputs = require_files(inputs)
    output.mkdir(parents=True, exist_ok=True)
    run([tc.tool('d8'), '--min-api', str(min_api), '--lib', tc.platform,
         *[arg for path in classpath for arg in ('--classpath', path)], '--output', output, *inputs])
    return require_files([output / 'classes.dex'])[0]


def link_manifest(tc, manifest, apk):
    require_files([manifest])
    run([tc.tool('aapt2'), 'link', '-I', tc.platform, '--manifest', manifest, '-o', apk])


def append_entries(apk, entries):
    """Reject duplicate names before modifying the APK; preserve caller order."""
    entries = tuple(entries)
    require_files([path for path, name in entries])
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
    combined = names + [name for path, name in entries]
    if len(set(combined)) != len(combined):
        raise SystemExit('Duplicate harness APK entry')
    with zipfile.ZipFile(apk, 'a') as archive:
        for path, name in entries:
            archive.write(path, name)


def copy_apk_entries(source, target, names):
    """Copy exactly the caller-selected DEX/resources, rejecting ambiguous ZIPs."""
    names = tuple(names)
    with zipfile.ZipFile(source) as origin, zipfile.ZipFile(target) as destination:
        existing = destination.namelist()
        if len(set(origin.namelist())) != len(origin.namelist()) or len(set(existing + list(names))) != len(existing) + len(names):
            raise SystemExit('Duplicate harness APK entry')
        contents = [(name, origin.read(name)) for name in names]
    with zipfile.ZipFile(target, 'a') as destination:
        for name, content in contents:
            destination.writestr(name, content)


def test_sign(tc, unsigned, aligned, signed, key, *, alias, dname, page_align=False):
    """Detached packages only: fixed public test password and caller-owned test key."""
    require_files([unsigned])
    alignment = ['-P', '16'] if page_align else []
    run([tc.tool('zipalign'), '-f', *alignment, '4', unsigned, aligned])
    if not key.exists():
        run([tc.tool('keytool'), '-genkeypair', '-keystore', key, '-storepass', 'test-only',
             '-keypass', 'test-only', '-alias', alias, '-dname', dname, '-keyalg', 'RSA', '-validity', '3650'])
    run([tc.tool('apksigner'), 'sign', '--ks', key, '--ks-pass', 'pass:test-only', '--out', signed, aligned])
    exported = run([tc.tool('keytool'), '-exportcert', '-rfc', '-keystore', key,
                    '-storepass', 'test-only', '-alias', alias])
    match = re.search(r'-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----', exported, re.DOTALL)
    if not match:
        raise SystemExit('Test key certificate unavailable')
    expected = hashlib.sha256(ssl.PEM_cert_to_DER_cert(match[0])).hexdigest()
    verify_signer(tc.tool('apksigner'), signed, expected)
    run([tc.tool('zipalign'), '-c', *alignment, '4', signed])


def certificate_digest(apksigner, apk, execute=run):
    output = execute([apksigner, 'verify', '--print-certs', apk])
    digests = re.findall(r'certificate SHA-256 digest: ([0-9a-f]{64})\b', output)
    if len(digests) != 1:
        raise SystemExit('Require one harness APK signer')
    return digests[0]


def verify_signer(apksigner, apk, expected, execute=run):
    if certificate_digest(apksigner, apk, execute) != expected:
        raise SystemExit('Signer mismatch; installation blocked')


def file_hashes(root):
    return {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in sorted(root.rglob('*')) if p.is_file()}


@contextmanager
def provider_backup(execute, remote, output, entries):
    """Back up/restore a caller-designated disposable folder without moving its root.

    Scenario writes and app/package lifecycle remain the caller's responsibility.
    """
    backup = output / ('provider-before-' + uuid.uuid4().hex)
    execute(['adb', 'shell', 'test', '-d', remote])
    execute(['adb', 'pull', remote, backup])
    entries = tuple(entries)
    if not entries or any(not (backup / name).exists() for name in entries):
        raise SystemExit('Disposable folder backup incomplete')
    try:
        yield
    finally:
        for name in entries:
            if (backup / name).is_dir():
                execute(['adb', 'shell', 'rm', '-rf', remote + '/' + name])
            execute(['adb', 'push', backup / name, remote + '/' + name])
        restored = output / ('provider-restored-' + uuid.uuid4().hex)
        execute(['adb', 'pull', remote, restored])
        if file_hashes(backup) != file_hashes(restored):
            raise SystemExit('Fixture restoration mismatch')
        (output / 'fixture-restoration.txt').write_text(str(file_hashes(restored)) + '\n')
