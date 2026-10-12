#!/usr/bin/env python3
"""Persistent LOCAL NON-PRODUCTION qualification identity. Never discovers keys.

No device operations. Tool output is redacted on failure. Migration copies bytes,
validates before publication, and retires only the two explicitly supplied files.
"""
import argparse
from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import ssl
import stat
import tempfile

from android_harness import AndroidToolchain, run, verify_signer

PURPOSE = 'totipo-android-local-qualification-signing'
WAVE2A = 'd862bbf279d306e7062440d299440d54ecdf594e1cf8ac3046cdf5d3a0ed7f49'
ROOT = Path(__file__).absolute().parents[2]
FIELDS = {'schema', 'purpose', 'alias', 'certificateSha256'}


def refuse(message):
    raise SystemExit('Qualification signing: ' + message)


def components(path):
    path = Path(os.path.abspath(path))
    for component in [*reversed(path.parents), path]:
        if component.is_symlink():
            refuse('symlink path refused')
    return path


def require_posix():
    if os.name != 'posix' or not hasattr(os, 'getuid'):
        refuse('meaningful POSIX privacy semantics required')


def regular(path, mode=None, directory=False):
    require_posix()
    path = components(path)
    try:
        info = path.lstat()
    except OSError:
        refuse('missing or unreadable identity input')
    kind = stat.S_ISDIR if directory else stat.S_ISREG
    if not kind(info.st_mode) or info.st_uid != os.getuid():
        refuse('require current-user-owned regular identity paths')
    if mode is not None and stat.S_IMODE(info.st_mode) != mode:
        refuse('unsafe identity permissions')
    return path


def metadata(path):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError('duplicate field')
            result[key] = value
        return result
    try:
        value = json.loads(path.read_text(encoding='utf-8'), object_pairs_hook=unique)
        if (not isinstance(value, dict) or set(value) != FIELDS
                or type(value['schema']) is not int or value['schema'] != 1
                or value['purpose'] != PURPOSE
                or not isinstance(value['alias'], str) or not value['alias'].strip()
                or not isinstance(value['certificateSha256'], str)
                or not re.fullmatch('[0-9a-f]{64}', value['certificateSha256'])):
            raise ValueError('schema')
    except (OSError, ValueError, UnicodeError):
        refuse('invalid identity metadata')
    return value


def inspect(tc, key, password):
    # This is a JDK *security* property, not a -D system property. Append to the
    # pinned JDK security configuration without replacing its providers. The
    # temporary file contains public parser policy only, never key/password data.
    with tempfile.NamedTemporaryFile(mode='w', encoding='ascii', prefix='qualification-keytool-') as policy:
        policy.write('keystore.type.compat=false\n')
        policy.flush()
        return _inspect(tc, key, password, policy.name)


def _inspect(tc, key, password, policy):
    args = [tc.tool('keytool'), '-J-Duser.language=en', '-J-Duser.country=US',
            '-J-Djava.security.properties=' + policy, '-keystore', key,
            '-storetype', 'PKCS12', '-storepass:file', password]
    output = run([*args, '-list', '-v'])
    aliases = re.findall(r'^Alias name: (.+)$', output, re.MULTILINE)
    entries = re.findall(r'^Entry type: (.+)$', output, re.MULTILINE)
    if len(aliases) != 1 or entries != ['PrivateKeyEntry'] or 'Keystore type: PKCS12' not in output:
        refuse('require exactly one PKCS12 private-key entry')
    exported = run([*args, '-exportcert', '-rfc', '-alias', aliases[0]])
    certs = re.findall(r'-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----', exported, re.DOTALL)
    if len(certs) != 1:
        refuse('require one public signing certificate')
    try:
        digest = hashlib.sha256(ssl.PEM_cert_to_DER_cert(certs[0])).hexdigest()
    except ValueError:
        refuse('invalid exported certificate')
    return aliases[0], digest


def production_fingerprint(root):
    # Read the committed public authority, never production secret variables.
    path = root / 'release/signing/android-release-cert.sha256'
    try:
        value = path.read_text(encoding='ascii')
    except (OSError, UnicodeError):
        refuse('production public identity unavailable')
    if not re.fullmatch('[0-9a-f]{64}\n', value):
        refuse('invalid production public identity')
    return value.strip()


@dataclass(frozen=True)
class Identity:
    directory: Path
    alias: str
    fingerprint: str

    @property
    def keystore(self):
        return self.directory / 'keystore.p12'

    @property
    def password(self):
        return self.directory / 'password'

    def sign(self, tc, source, aligned, signed):
        # Reload on every signing use, including callers holding an earlier snapshot.
        current = _load(self.directory, tc, production_fingerprint(self.directory.parents[1]))
        if current != self:
            refuse('identity changed since preflight')
        paths = tuple(map(lambda p: Path(os.path.abspath(p)), (source, aligned, signed)))
        if len(set(paths)) != 3:
            refuse('require distinct signing paths')
        for path in paths[1:]:
            components(path)
            if self.directory.parent in path.parents or (path.exists() and not path.is_file()):
                refuse('unsafe signing output')
        if not paths[0].is_file():
            refuse('missing unsigned APK')
        run([tc.tool('zipalign'), '-f', '-P', '16', '4', source, aligned])
        run([tc.tool('apksigner'), 'sign', '--ks', self.keystore, '--ks-key-alias', self.alias,
             '--ks-pass', 'file:' + str(self.password), '--v1-signing-enabled', 'false',
             '--v4-signing-enabled', 'false', '--out', signed, aligned])
        self.verify(tc, signed)
        run([tc.tool('zipalign'), '-c', '-P', '16', '4', signed])

    def verify(self, tc, apk):
        verify_signer(tc.tool('apksigner'), apk, self.fingerprint)


def _load(directory, tc, forbidden):
    regular(directory.parent, 0o700, directory=True)
    regular(directory, 0o700, directory=True)
    for name in ('keystore.p12', 'password', 'identity.json'):
        regular(directory / name, 0o600)
    value = metadata(directory / 'identity.json')
    alias, digest = inspect(tc, directory / 'keystore.p12', directory / 'password')
    if alias != value['alias'] or digest != value['certificateSha256']:
        refuse('metadata/keystore identity mismatch')
    if digest == forbidden:
        refuse('production certificate forbidden for local qualification')
    return Identity(directory, alias, digest)


def load(root=ROOT, tc=None):
    require_posix()
    root = components(root)
    return _load(root / '.qualification/signing', tc or AndroidToolchain.discover(),
                 production_fingerprint(root))


def sync_directory(path):
    descriptor = os.open(path, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def migrate(source_key, source_password, root=ROOT, tc=None):
    """Explicit Wave 2A adoption only; never scans, converts or generates keys."""
    require_posix()
    root = components(root)
    tc = tc or AndroidToolchain.discover()
    parent = root / '.qualification'
    destination = parent / 'signing'
    components(destination)
    if destination.exists():
        refuse('destination already exists (including partial state)')
    key = regular(source_key)
    password = regular(source_password)
    if key == password or parent in key.parents or parent in password.parents:
        refuse('source must be two distinct files outside destination tree')
    alias, digest = inspect(tc, key, password)
    if digest != WAVE2A or alias != 'wave2a-qualification':
        refuse('source is not the expected Wave 2A identity')
    forbidden = production_fingerprint(root)
    if not parent.exists():
        parent.mkdir(mode=0o700)
    regular(parent, 0o700, directory=True)
    # An interrupted staging directory is deliberately left private and requires
    # explicit operator inspection; normal loading never adopts it.
    stage = Path(tempfile.mkdtemp(prefix='.migration-', dir=parent))
    try:
        for source, name in ((key, 'keystore.p12'), (password, 'password')):
            target = stage / name
            with target.open('xb') as output:
                os.chmod(target, 0o600)
                with source.open('rb') as input_file:
                    shutil.copyfileobj(input_file, output)
                output.flush()
                os.fsync(output.fileno())
        value = dict(schema=1, purpose=PURPOSE, alias=alias, certificateSha256=digest)
        with (stage / 'identity.json').open('x', encoding='utf-8') as output:
            os.chmod(stage / 'identity.json', 0o600)
            output.write(json.dumps(value, sort_keys=True, indent=2) + '\n')
            output.flush()
            os.fsync(output.fileno())
        _load(stage, tc, forbidden)
        sync_directory(stage)
        # mkdir provides exclusive publication reservation; a crash before rename
        # leaves a partial destination which normal loading refuses.
        destination.mkdir(mode=0o700)
        stage.replace(destination)
        sync_directory(parent)
        identity = load(root, tc)
        if key.read_bytes() != identity.keystore.read_bytes() or password.read_bytes() != identity.password.read_bytes():
            refuse('published bytes differ; old private material retained')
    finally:
        if stage.exists():
            shutil.rmtree(stage)
    try:
        key.unlink()
        password.unlink()
    except OSError:
        refuse('valid destination retained; duplicate old private material remains; cleanup required')
    if key.exists() or password.exists():
        refuse('old private material remains')
    return identity


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    sub.add_parser('status')
    migration = sub.add_parser('migrate')
    migration.add_argument('source_keystore', type=Path)
    migration.add_argument('source_password', type=Path)
    signing = sub.add_parser('sign', help='Generic inbound/ingress APK signing; no install/device operations')
    signing.add_argument('unsigned', type=Path)
    signing.add_argument('--aligned', type=Path, required=True)
    signing.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    tc = AndroidToolchain.discover()
    identity = (migrate(args.source_keystore, args.source_password, tc=tc)
                if args.command == 'migrate' else load(tc=tc))
    if args.command == 'sign':
        identity.sign(tc, args.unsigned, args.aligned, args.out)
    print('Valid LOCAL NON-PRODUCTION qualification identity')
    print('Directory:', identity.directory)
    print('Alias:', identity.alias)
    print('Certificate SHA-256:', identity.fingerprint)


if __name__ == '__main__':
    try:
        main()
    except OSError:
        refuse('filesystem operation failed; inspect local state before retrying')
