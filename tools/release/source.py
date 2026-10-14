"""Release source operations shared by the release.py CLI and Nix apps.

Only annotated tags consume codes. Remote/local tag equality is checked without
changing worktree refs; target remote validation uses an isolated object database.
Unavailable/stale history objects fail closed. Any v-prefixed
tag must obey the stable Android tag contract, rather than being silently skipped.
"""
import base64
from dataclasses import dataclass
import hashlib
import os
from pathlib import Path
import re
import subprocess
import tempfile
from payload import require

MAX_CODE = 2100000000
VERSION_RE = r'(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)'
METADATA = {'VERSION', 'VERSION_CODE'}


@dataclass(frozen=True)
class Repository:
    """An explicit caller worktree; construction does no discovery or I/O."""
    root: Path

    def __post_init__(self):
        require(isinstance(self.root, Path) and self.root.is_absolute(), 'Absolute repository path required')

    @property
    def cert(self):
        return self.root / 'release/signing/android-release-cert.pem'

    @property
    def fingerprint(self):
        return self.root / 'release/signing/android-release-cert.sha256'

    def git(self, *args):
        return subprocess.check_output(['git', *args], cwd=self.root)

    def run(self, *args):
        return subprocess.check_output(args, text=True, cwd=self.root).strip()


def discover():
    # CLI boundary only: the executable may live in the store.
    root = Path(subprocess.check_output(['git', 'rev-parse', '--show-toplevel'], text=True).strip()).resolve()
    require(not root.is_relative_to('/nix/store'), 'Refusing a Nix-store worktree')
    return Repository(root)


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def fingerprint(path):
    data = Path(path).read_bytes()
    require(re.fullmatch(rb'[0-9a-f]{64}\n', data),
            'Expected fingerprint must be 64 lowercase SHA-256 hex characters and LF')
    return data.decode().removesuffix('\n')


def expected_identity(repo):
    require(repo.cert.is_file() and repo.fingerprint.is_file(),
            'Production identity unprovisioned: public android-release-cert.pem/.sha256 required')
    value = fingerprint(repo.fingerprint)
    pem = repo.cert.read_text()
    match = re.fullmatch(r'-----BEGIN CERTIFICATE-----\n([A-Za-z0-9+/=\n]+)-----END CERTIFICATE-----\n?', pem)
    require(match, 'Expected exactly one PEM certificate')
    der = base64.b64decode(match[1].replace('\n', ''), validate=True)
    require(hashlib.sha256(der).hexdigest() == value, 'Certificate/fingerprint mismatch')
    repo.run(str(Path(os.environ['JAVA_HOME']) / 'bin/keytool'), '-printcert', '-file', str(repo.cert))
    return value


def guard(repo):
    tracked = repo.git('ls-files', '-z', 'release/signing').decode().split('\0')
    allowed = {'release/signing/README.md', 'release/signing/android-release-cert.pem',
               'release/signing/android-release-cert.sha256'}
    require(all(not p or p in allowed for p in tracked), 'Unexpected tracked signing file')
    for path in (repo.root / 'release/signing').iterdir():
        require(path.name in {Path(p).name for p in allowed}, 'Unexpected signing material on disk')


def version(value):
    require(type(value) is str and re.fullmatch(VERSION_RE, value), 'Expected canonical X.Y.Z version')
    return tuple(map(int, value.split('.')))


def code(data):
    require(re.fullmatch(rb'[1-9][0-9]*\n', data), 'Malformed VERSION_CODE: canonical decimal and LF required')
    # Bound length before int conversion, including hostile very long input.
    require(len(data) <= 11, 'VERSION_CODE overflow')
    value = int(data)
    require(1 <= value <= MAX_CODE, 'VERSION_CODE out of Android range')
    return value


def blob(repo, ref, name):
    return repo.git('show', f'{ref}:{name}')


def historical_code(repo, ref):
    files = repo.git('ls-tree', '--name-only', ref).decode().splitlines()
    if 'VERSION_CODE' in files:
        require(repo.git('ls-tree', ref, 'VERSION_CODE').startswith(b'100644 '),
                'Nonregular historical VERSION_CODE')
        return code(blob(repo, ref, 'VERSION_CODE'))
    # Compatibility only for tagged legacy sources; never for the worktree.
    script = blob(repo, ref, 'app/build.gradle.kts').decode()
    matches = re.findall(r'^\s*versionCode\s*=\s*([1-9][0-9]*)\s*$', script, re.M)
    require(len(matches) == 1 and len(re.findall(r'\bversionCode\s*=', script)) == 1,
            'Missing/ambiguous legacy versionCode at ' + ref)
    return code((matches[0] + '\n').encode())


def remote_refs(repo):
    refs = {}
    for line in repo.git('ls-remote', 'origin', 'refs/heads/main', 'refs/tags/*').decode().splitlines():
        parts = line.split('\t')
        require(len(parts) == 2 and re.fullmatch('[0-9a-f]{40}', parts[0]) and parts[1] not in refs,
                'Malformed/ambiguous remote refs')
        refs[parts[1]] = parts[0]
    require('refs/heads/main' in refs, 'Remote main missing')
    return refs


def release_refs(refs):
    result = {}
    for ref, oid in refs.items():
        if ref.startswith('refs/tags/v'):
            name = ref.removeprefix('refs/tags/v').removesuffix('^{}')
            version(name)  # Reject malformed/unsupported release candidates.
            result[ref] = oid
    return result


def local_refs(repo):
    return dict(line.split(' ', 1)[::-1] for line in
                repo.git('for-each-ref', '--format=%(objectname) %(refname)', 'refs/tags').decode().splitlines())


def history(repo, remote, exclude=None):
    local = release_refs(local_refs(repo))
    remote = release_refs(remote)
    if exclude:
        local.pop(exclude, None)
        remote.pop(exclude, None)
        remote.pop(exclude + '^{}', None)
    require(local == {r: oid for r, oid in remote.items() if not r.endswith('^{}')},
            'Local/remote release tags differ; synchronize reviewed tag objects before retrying')
    consumed = []
    for ref in sorted(local):
        require(repo.git('cat-file', '-t', ref).strip() == b'tag', 'Annotated release tag required: ' + ref)
        commit = repo.git('rev-parse', ref + '^{commit}').decode().strip()
        require(remote.get(ref + '^{}') == commit, 'Remote tag peel differs: ' + ref)
        target = ref.removeprefix('refs/tags/v')
        require(repo.git('ls-tree', commit, 'VERSION').startswith(b'100644 '), 'Nonregular tagged VERSION')
        require(blob(repo, commit, 'VERSION') == (target + '\n').encode(), 'Tag VERSION differs: ' + ref)
        consumed.append((version(target), historical_code(repo, commit)))
    require(len({c for _, c in consumed}) == len(consumed), 'Duplicate consumed versionCodes')
    return consumed


def next_code(target, consumed):
    target = version(target)
    require(all(target > v for v, _ in consumed), 'Target must be newer than every consumed release')
    value = max((c for _, c in consumed), default=0) + 1
    require(value <= MAX_CODE, 'Next versionCode overflow')
    return value


def machinery_ready(repo):
    root = repo.root
    for name in ('tools/release/release.py', 'tools/release/source.py', 'tools/release/payload.py',
                 'tools/release/verify-signed-apk.py', 'tools/verify-apk.py', '.github/workflows/release.yml',
                 'release/signing/android-release-cert.pem', 'release/signing/android-release-cert.sha256'):
        require((root / name).is_file() and not (root / name).is_symlink(), 'Release prerequisite missing: ' + name)
        repo.git('ls-files', '--error-unmatch', name)


def source_ready(repo):
    root = repo.root
    code((root / 'VERSION_CODE').read_bytes())  # No legacy current-source fallback.
    build = (root / 'app/build.gradle.kts').read_text()
    require(re.findall(r'^\s*versionCode\s*=\s*(.*)$', build, re.M) == ['releaseVersionCode'],
            'Duplicated/noncanonical versionCode authority')
    require(re.findall(r'^\s*versionName\s*=\s*(.*)$', build, re.M) == ['rootProject.version.toString()'],
            'Duplicated/noncanonical versionName authority')
    require(re.findall(r'^\s*applicationId\s*=\s*(.*)$', build, re.M) == ['"org.totipo.android"'],
            'Application ID differs')
    require('rootProject.layout.projectDirectory.file("VERSION_CODE")' in build,
            'VERSION_CODE wiring missing')
    root_build = (root / 'build.gradle.kts').read_text()
    require('layout.projectDirectory.file("VERSION")' in root_build and
            'version = versionText.removeSuffix("\\n")' in root_build, 'VERSION wiring missing')
    machinery_ready(repo)
    guard(repo)
    expected_identity(repo)
    for name in repo.git('ls-files', '-z').decode().split('\0'):
        if not name:
            continue
        path = root / name
        require(not re.search(r'\.(?:p12|pfx|jks|keystore|key)$', name, re.I),
                'Private signing material path: ' + name)
        if path.is_file() and not path.is_symlink():
            require(not re.search(rb'-----BEGIN (?:RSA |EC |ENCRYPTED |OPENSSH )?PRIVATE KEY-----',
                                  path.read_bytes()), 'Private key in source: ' + name)


def worktree(repo):
    root = repo.root
    require(repo.git('branch', '--show-current').strip() == b'main', 'Release source requires main')
    repo.git('rev-parse', '--verify', 'HEAD^{commit}')
    for name in METADATA:
        require((root / name).is_file() and not (root / name).is_symlink(), 'Missing/nonregular ' + name)
        require(repo.git('ls-files', '--error-unmatch', name), 'Untracked release metadata')
    return root


def dirty_policy(repo, target, expected, clean=False):
    require(not repo.git('diff', '--cached', '--name-only', '-z'), 'Staged changes forbidden')
    require(not repo.git('ls-files', '--others', '--exclude-standard', '-z'), 'Untracked paths forbidden')
    dirty = set(filter(None, repo.git('diff', '--name-only', '-z').decode().split('\0')))
    require(not dirty if clean else dirty <= METADATA, 'Unrelated/dirty source forbidden')
    if dirty:
        require((repo.root / 'VERSION').read_bytes() == (target + '\n').encode() and
                (repo.root / 'VERSION_CODE').read_bytes() == f'{expected}\n'.encode(),
                'Only exact expected prepared metadata diff is permitted')
        for name in dirty:
            require(repo.git('ls-tree', 'HEAD', name).startswith(b'100644 '), 'Noncanonical metadata mode')
            require((repo.root / name).stat().st_mode & 0o777 == 0o644, 'Metadata mode changed')
    return dirty


def atomic_write(path, data):
    fd, temporary = tempfile.mkstemp(prefix='.' + path.name + '-', dir=path.parent)
    try:
        with os.fdopen(fd, 'wb') as out:
            out.write(data)
            out.flush()
            os.fsync(out.fileno())
        os.chmod(temporary, 0o644)
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def validate_tag(repo, oid, target, head, expected, read=None):
    read = repo.git if read is None else read
    require(re.fullmatch('[0-9a-f]{40}', oid), 'Malformed target tag object')
    require(read('cat-file', '-t', oid).strip() == b'tag', 'Annotated target tag required')
    raw = read('cat-file', 'tag', oid)
    headers, message = raw.split(b'\n\n', 1)
    lines = headers.splitlines()
    require(len(lines) == 4 and lines[:3] == [b'object ' + head.encode(), b'type commit',
            b'tag v' + target.encode()] and re.fullmatch(rb'tagger .+ <[^<>\n]+> [0-9]+ [+-][0-9]{4}', lines[3]) and
            message == f'Totipo Android {target}\n'.encode(), 'Target tag message/source/shape differs')
    require(read('rev-parse', oid + '^{commit}').decode().strip() == head, 'Target tag peel differs')
    for name, value in [('VERSION', target), ('VERSION_CODE', str(expected))]:
        require(read('ls-tree', head, name).startswith(b'100644 ') and
                read('show', f'{head}:{name}') == (value + '\n').encode(), 'Target tag metadata differs')


def validate_remote_tag(repo, remote, ref, target, head, expected, oid):
    require(remote.get(ref) == oid and remote.get(ref + '^{}') == head,
            'Remote target tag object/peel differs')
    # Fetch into an isolated object database: never adopt/move a worktree tag.
    with tempfile.TemporaryDirectory(prefix='totipo-tag-') as tmp:
        def read(*args):
            return subprocess.check_output(['git', '--git-dir=' + tmp, *args])
        read('init', '--bare', '--quiet')
        url = repo.git('remote', 'get-url', 'origin').decode().strip()
        read('-c', 'gc.auto=0', 'fetch', '--quiet', '--no-tags', '--no-write-fetch-head',
             '--no-auto-maintenance', url, ref)
        validate_tag(repo, oid, target, head, expected, read)


def release_tag(repo, target, expected, remote, ref, local):
    head = repo.git('rev-parse', '--verify', 'HEAD^{commit}').decode().strip()
    require(re.fullmatch('[0-9a-f]{40}', head), 'Full source commit required')
    def main_ready(refs):
        require(refs['refs/heads/main'] == head and
                repo.git('rev-parse', 'refs/remotes/origin/main').decode().strip() == head,
                'origin/main must resolve to HEAD (remote and tracking ref)')
    main_ready(remote)
    # Verify and push to the same single endpoint; multiple push URLs fail closed.
    url = repo.git('remote', 'get-url', 'origin').decode().strip()
    require(repo.git('remote', 'get-url', '--push', '--all', 'origin').decode().splitlines() == [url],
            'origin push endpoint differs from inspected origin')
    oid = local.get(ref)
    if oid:
        validate_tag(repo, oid, target, head, expected)
    if ref in remote or ref + '^{}' in remote:
        require(oid is not None, 'Remote target exists without reviewed local tag')
        validate_remote_tag(repo, remote, ref, target, head, expected, oid)
    # Recheck source cleanliness and remote state immediately before mutation.
    dirty_policy(repo, target, expected, clean=True)
    require(repo.git('rev-parse', 'HEAD').decode().strip() == head, 'HEAD changed during validation')
    current = remote_refs(repo)
    require(current == remote, 'Remote refs changed during validation')
    main_ready(current)
    if oid is None:
        repo.git('tag', '--no-sign', '-a', 'v' + target, '-m', 'Totipo Android ' + target, head)
        oid = repo.git('rev-parse', ref).decode().strip()
    validate_tag(repo, oid, target, head, expected)
    if ref not in remote:
        repo.git('-c', 'remote.origin.mirror=false', 'push', '--no-follow-tags', '--no-force',
            url, oid + ':' + ref)
    final = remote_refs(repo)
    main_ready(final)
    validate_remote_tag(repo, final, ref, target, head, expected, oid)
    # Cover remote movement during isolated-object validation too.
    require(remote_refs(repo) == final, 'Remote changed after tag verification')
    require(repo.git('rev-parse', 'HEAD').decode().strip() == head and
            local_refs(repo).get(ref) == oid, 'Local source/tag changed during verification')
    dirty_policy(repo, target, expected, clean=True)
    return dict(version=target, version_code=expected, tag='v' + target, source_commit=head)


def identity(repo, target, commit):
    version(target)
    require(re.fullmatch('[0-9a-f]{40}', commit), 'Full reviewed commit required')
    require(os.environ['GITHUB_REF'] == 'refs/heads/main', 'Dispatch must select main')
    require(os.environ['GITHUB_SHA'] == commit, 'Dispatch SHA must equal reviewed commit')
    require(repo.run('git', 'rev-parse', 'HEAD') == commit, 'Checkout differs from reviewed source')
    require((repo.root / 'VERSION').read_text() == target + '\n', 'VERSION differs')
    require(not repo.run('git', 'status', '--porcelain', '--untracked-files=all'), 'Checkout mutated')
    tag = 'refs/tags/v' + target
    require(repo.run('git', 'cat-file', '-t', tag) == 'tag', 'Existing annotated tag required')
    require(repo.run('git', 'rev-parse', tag + '^{commit}') == commit, 'Tag source differs')
    remote = dict(line.split()[::-1] for line in repo.run('git', 'ls-remote', 'origin',
                  'refs/heads/main', tag, tag + '^{}').splitlines())
    require(remote.get('refs/heads/main') == commit, 'Reviewed source is no longer current main')
    require(remote.get(tag) == repo.run('git', 'rev-parse', tag) and
            remote.get(tag + '^{}') == commit, 'Remote annotated tag differs')
    code((repo.root / 'VERSION_CODE').read_bytes())
    guard(repo)
    return expected_identity(repo)
