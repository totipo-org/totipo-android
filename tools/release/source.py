"""Release source operations shared by the release.py CLI and Nix apps.

Only annotated tags consume codes. Remote/local tag equality is checked without
changing worktree refs; target remote validation uses an isolated object database.
Unavailable/stale history objects fail closed. Any v-prefixed
tag must obey the stable Android tag contract, rather than being silently skipped.
"""
import os
import json
from pathlib import Path
import re
import subprocess
import tempfile
import release
from payload import require

MAX_CODE = 2100000000
VERSION_RE = r'(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)'
METADATA = {'VERSION', 'VERSION_CODE'}
CODE_WIRING = 'versionCode = releaseVersionCode'
NAME_WIRING = 'versionName = rootProject.version.toString()'


def git(*args):
    return subprocess.check_output(['git', *args], cwd=release.ROOT)


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


def blob(ref, name):
    return git('show', f'{ref}:{name}')


def historical_code(ref):
    files = git('ls-tree', '--name-only', ref).decode().splitlines()
    if 'VERSION_CODE' in files:
        require(git('ls-tree', ref, 'VERSION_CODE').startswith(b'100644 '),
                'Nonregular historical VERSION_CODE')
        return code(blob(ref, 'VERSION_CODE'))
    # Compatibility only for tagged legacy sources; never for the worktree.
    script = blob(ref, 'app/build.gradle.kts').decode()
    matches = re.findall(r'^\s*versionCode\s*=\s*([1-9][0-9]*)\s*$', script, re.M)
    require(len(matches) == 1 and len(re.findall(r'\bversionCode\s*=', script)) == 1,
            'Missing/ambiguous legacy versionCode at ' + ref)
    return code((matches[0] + '\n').encode())


def remote_refs():
    refs = {}
    for line in git('ls-remote', 'origin', 'refs/heads/main', 'refs/tags/*').decode().splitlines():
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


def local_refs():
    return dict(line.split(' ', 1)[::-1] for line in
                git('for-each-ref', '--format=%(objectname) %(refname)', 'refs/tags').decode().splitlines())


def history(remote, exclude=None):
    local = release_refs(local_refs())
    remote = release_refs(remote)
    if exclude:
        local.pop(exclude, None)
        remote.pop(exclude, None)
        remote.pop(exclude + '^{}', None)
    require(local == {r: oid for r, oid in remote.items() if not r.endswith('^{}')},
            'Local/remote release tags differ; synchronize reviewed tag objects before retrying')
    consumed = []
    for ref in sorted(local):
        require(git('cat-file', '-t', ref).strip() == b'tag', 'Annotated release tag required: ' + ref)
        commit = git('rev-parse', ref + '^{commit}').decode().strip()
        require(remote.get(ref + '^{}') == commit, 'Remote tag peel differs: ' + ref)
        target = ref.removeprefix('refs/tags/v')
        require(git('ls-tree', commit, 'VERSION').startswith(b'100644 '), 'Nonregular tagged VERSION')
        require(blob(commit, 'VERSION') == (target + '\n').encode(), 'Tag VERSION differs: ' + ref)
        consumed.append((version(target), historical_code(commit)))
    require(len({c for _, c in consumed}) == len(consumed), 'Duplicate consumed versionCodes')
    return consumed


def next_code(target, consumed):
    target = version(target)
    require(all(target > v for v, _ in consumed), 'Target must be newer than every consumed release')
    value = max((c for _, c in consumed), default=0) + 1
    require(value <= MAX_CODE, 'Next versionCode overflow')
    return value


def source_pins():
    # Reuse the JVM guard's reviewed digest inventory, without a second pin list.
    test = release.ROOT / 'app/src/test/java/org/totipo/android/OtpAuthSupplyChainGuardTest.java'
    pins = re.findall(r'Map.entry\("([^"]+)", "([0-9a-f]{64})"\)', test.read_text())
    require(len(pins) >= 12, 'Reviewed source guard inventory missing')
    for name, digest in pins:
        require(release.sha(release.ROOT / name) == digest, 'Reviewed supply-chain pin differs: ' + name)



def machinery_ready():
    root = release.ROOT
    for name in ('tools/release/release.py', 'tools/release/source.py', 'tools/release/payload.py',
                 'tools/release/verify-signed-apk.py', 'tools/verify-apk.py', '.github/workflows/release.yml',
                 'release/signing/android-release-cert.pem', 'release/signing/android-release-cert.sha256'):
        require((root / name).is_file() and not (root / name).is_symlink(), 'Release prerequisite missing: ' + name)
        git('ls-files', '--error-unmatch', name)
    workflow = (root / '.github/workflows/release.yml').read_text()
    require('workflow_dispatch:' in workflow and 'environment: android-release' in workflow,
            'Normal protected release workflow missing')
    require(all(re.fullmatch(r'[\w/-]+@[0-9a-f]{40}', action)
                for action in re.findall(r'uses: ([^\s]+)', workflow)), 'Unpinned release workflow action')


def source_ready():
    root = release.ROOT
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
    source_pins()
    machinery_ready()
    release.guard()
    release.expected_identity()
    for name in git('ls-files', '-z').decode().split('\0'):
        if not name:
            continue
        path = root / name
        require(not re.search(r'\.(?:p12|pfx|jks|keystore|key)$', name, re.I),
                'Private signing material path: ' + name)
        if path.is_file() and not path.is_symlink():
            require(not re.search(rb'-----BEGIN (?:RSA |EC |ENCRYPTED |OPENSSH )?PRIVATE KEY-----',
                                  path.read_bytes()), 'Private key in source: ' + name)


def worktree():
    # The executable may live in the store. Resolve the caller, never __file__.
    root = Path(subprocess.check_output(['git', 'rev-parse', '--show-toplevel'], text=True).strip()).resolve()
    require(not root.is_relative_to('/nix/store'), 'Refusing a Nix-store worktree')
    release.ROOT = root
    release.CERT = root / 'release/signing/android-release-cert.pem'
    release.FP = root / 'release/signing/android-release-cert.sha256'
    require(git('branch', '--show-current').strip() == b'main', 'Release source requires main')
    git('rev-parse', '--verify', 'HEAD^{commit}')
    for name in METADATA:
        require((root / name).is_file() and not (root / name).is_symlink(), 'Missing/nonregular ' + name)
        require(git('ls-files', '--error-unmatch', name), 'Untracked release metadata')
    return root


def dirty_policy(target, expected, clean=False):
    require(not git('diff', '--cached', '--name-only', '-z'), 'Staged changes forbidden')
    require(not git('ls-files', '--others', '--exclude-standard', '-z'), 'Untracked paths forbidden')
    dirty = set(filter(None, git('diff', '--name-only', '-z').decode().split('\0')))
    require(not dirty if clean else dirty <= METADATA, 'Unrelated/dirty source forbidden')
    if dirty:
        require((release.ROOT / 'VERSION').read_bytes() == (target + '\n').encode() and
                (release.ROOT / 'VERSION_CODE').read_bytes() == f'{expected}\n'.encode(),
                'Only exact expected prepared metadata diff is permitted')
        for name in dirty:
            require(git('ls-tree', 'HEAD', name).startswith(b'100644 '), 'Noncanonical metadata mode')
            require((release.ROOT / name).stat().st_mode & 0o777 == 0o644, 'Metadata mode changed')
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


def validate_tag(oid, target, head, expected, read=git):
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


def validate_remote_tag(remote, ref, target, head, expected, oid):
    require(remote.get(ref) == oid and remote.get(ref + '^{}') == head,
            'Remote target tag object/peel differs')
    # Fetch into an isolated object database: never adopt/move a worktree tag.
    with tempfile.TemporaryDirectory(prefix='totipo-tag-') as tmp:
        def read(*args):
            return subprocess.check_output(['git', '--git-dir=' + tmp, *args])
        read('init', '--bare', '--quiet')
        url = git('remote', 'get-url', 'origin').decode().strip()
        read('-c', 'gc.auto=0', 'fetch', '--quiet', '--no-tags', '--no-write-fetch-head',
             '--no-auto-maintenance', url, ref)
        validate_tag(oid, target, head, expected, read)


def release_tag(target, expected, remote, ref, local):
    head = git('rev-parse', '--verify', 'HEAD^{commit}').decode().strip()
    require(re.fullmatch('[0-9a-f]{40}', head), 'Full source commit required')
    def main_ready(refs):
        require(refs['refs/heads/main'] == head and
                git('rev-parse', 'refs/remotes/origin/main').decode().strip() == head,
                'origin/main must resolve to HEAD (remote and tracking ref)')
    main_ready(remote)
    # Verify and push to the same single endpoint; multiple push URLs fail closed.
    url = git('remote', 'get-url', 'origin').decode().strip()
    require(git('remote', 'get-url', '--push', '--all', 'origin').decode().splitlines() == [url],
            'origin push endpoint differs from inspected origin')
    oid = local.get(ref)
    if oid:
        validate_tag(oid, target, head, expected)
    if ref in remote or ref + '^{}' in remote:
        require(oid is not None, 'Remote target exists without reviewed local tag')
        validate_remote_tag(remote, ref, target, head, expected, oid)
    # Recheck source cleanliness and remote state immediately before mutation.
    dirty_policy(target, expected, clean=True)
    require(git('rev-parse', 'HEAD').decode().strip() == head, 'HEAD changed during validation')
    current = remote_refs()
    require(current == remote, 'Remote refs changed during validation')
    main_ready(current)
    if oid is None:
        git('tag', '--no-sign', '-a', 'v' + target, '-m', 'Totipo Android ' + target, head)
        oid = git('rev-parse', ref).decode().strip()
    validate_tag(oid, target, head, expected)
    if ref not in remote:
        git('-c', 'remote.origin.mirror=false', 'push', '--no-follow-tags', '--no-force',
            url, oid + ':' + ref)
    final = remote_refs()
    main_ready(final)
    validate_remote_tag(final, ref, target, head, expected, oid)
    # Cover remote movement during isolated-object validation too.
    require(remote_refs() == final, 'Remote changed after tag verification')
    require(git('rev-parse', 'HEAD').decode().strip() == head and
            local_refs().get(ref) == oid, 'Local source/tag changed during verification')
    dirty_policy(target, expected, clean=True)
    return dict(version=target, version_code=expected, tag='v' + target, source_commit=head)


def execute(command, target, json_output=False):
    require(command in ('prepare-release', 'check-release', 'release-tag'), 'Unknown source command')
    version(target)
    root = worktree()
    remote = remote_refs()
    ref = 'refs/tags/v' + target
    tagging = command == 'release-tag'
    require(tagging or (ref not in remote and ref + '^{}' not in remote), 'Target remote tag already exists')
    local = local_refs()
    require(tagging or ref not in local, 'Target local tag already exists')
    consumed = history(remote, exclude=ref if tagging else None)
    expected = next_code(target, consumed)
    dirty_policy(target, expected, clean=tagging)
    source_ready()
    if command == 'prepare-release':
        current = (root / 'VERSION').read_bytes()
        version(current.decode().removesuffix('\n'))
        require(current.endswith(b'\n'), 'Malformed VERSION')
        changes = [(root / 'VERSION', (target + '\n').encode()),
                   (root / 'VERSION_CODE', f'{expected}\n'.encode())]
        changed = [p.name for p, data in changes if p.read_bytes() != data]
        for path, data in changes:
            if path.name in changed:
                atomic_write(path, data)
        # Focused post-write checks; no build, signing or Git mutation.
        dirty_policy(target, expected)
        source_ready()
        print(('Prepared' if changed else 'Already prepared') + f' VERSION={target}; VERSION_CODE={expected}')
        print('Changed paths: ' + (', '.join(changed) or 'none'))
        return
    require((root / 'VERSION').read_bytes() == (target + '\n').encode(), 'VERSION differs from target')
    require(code((root / 'VERSION_CODE').read_bytes()) == expected, 'VERSION_CODE differs from consumed history')
    if tagging:
        result = release_tag(target, expected, remote, ref, local)
        if json_output:
            print(json.dumps(result, sort_keys=True, indent=2))
        else:
            print('Release tag ready.\n')
            for key in ('version', 'version_code', 'tag', 'source_commit'):
                print(f'  {"versionCode" if key == "version_code" else key}: {result[key]}')
            print('\nGitHub Release workflow inputs:\n')
            print(f'  version: {target}\n  source_commit: {result["source_commit"]}')
        return
    print(f'{command}: {target}; VERSION_CODE={expected}; PASS')
