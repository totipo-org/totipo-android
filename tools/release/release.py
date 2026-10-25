#!/usr/bin/env python3
"""No-secret source, public identity, bundle and publication operations."""
import argparse
import importlib.util
import hashlib
from functools import cache
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
from urllib.parse import quote
from payload import require

import source
from source import discover, sha, version, identity, guard


@cache
def apk_policy():
    # Source-only Nix apps contain tools/release, and never inspect an APK.
    spec = importlib.util.spec_from_file_location('apk_policy', Path(__file__).resolve().parents[1] / 'verify-apk.py')
    policy = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(policy)
    return policy


def unsigned_check(apk):
    subprocess.run(['python3', '-B', str(Path(__file__).resolve().parents[1] / 'verify-apk.py'), str(apk),
                    '--unsigned', '--no-debug-probe'], check=True)


# One schema, shared by qualification, bundle validation and signed verification.
# No schema-version field existed in the machinery format; retain its field names.
APPLICATION_ID = 'org.totipo.android'
PROVENANCE_FIELDS = {
    'version', 'source_commit', 'tag', 'unsigned_sha256', 'application_id',
    'version_code', 'version_name', 'nix_derivation', 'nix_output',
    'certificate_sha256', 'java_runtime', 'protocol',
}


def validate_provenance(p):
    require(type(p) is dict and set(p) == PROVENANCE_FIELDS, 'Provenance fields differ')
    require(all(type(p[k]) is str for k in PROVENANCE_FIELDS - {'version_code'}),
            'Provenance string field type differs')
    version(p['version'])
    require(re.fullmatch('[0-9a-f]{40}', p['source_commit']), 'Invalid provenance source commit')
    require(p['tag'] == 'v' + p['version'], 'Invalid provenance tag')
    require(p['application_id'] == APPLICATION_ID, 'Provenance applicationId differs')
    require(type(p['version_code']) is int and 1 <= p['version_code'] <= 2100000000,
            'Invalid provenance versionCode')
    require(p['version_name'] == p['version'],
            'Invalid provenance versionName')
    for field in ('unsigned_sha256', 'certificate_sha256'):
        require(re.fullmatch('[0-9a-f]{64}', p[field]), 'Invalid provenance ' + field)
    for field in ('nix_derivation', 'nix_output'):
        require(re.fullmatch(r'/nix/store/[0-9a-z]{32}-[A-Za-z0-9+._?=-]+', p[field]),
                'Invalid provenance ' + field)
    require(p['nix_derivation'].endswith('.drv') and not p['nix_output'].endswith('.drv'),
            'Invalid provenance Nix paths')
    require(p['java_runtime'] == '0.2.0' and p['protocol'] == 'v1/r19',
            'Provenance runtime/protocol differs')
    return p


def load_provenance(path):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result, 'Duplicate provenance field: ' + key)
            result[key] = value
        return result
    return validate_provenance(json.loads(Path(path).read_text(), object_pairs_hook=unique))


def apk_metadata(apk):
    # One native manifest representation and one reviewed source metadata parser.
    policy = apk_policy()
    root = policy.manifest(apk)
    return policy.require_identity(root, policy.reviewed_metadata())


def require_metadata(apk, p):
    actual = apk_metadata(apk)
    for field, label in (('application_id', 'applicationId'), ('version_code', 'versionCode'),
                         ('version_name', 'versionName')):
        require(actual[field] == p[field], label + ' mismatch: APK differs from provenance')


def bind_unsigned(apk, p):
    require(sha(apk) == p['unsigned_sha256'], 'Qualified unsigned APK changed (SHA-256 mismatch)')
    require_metadata(apk, p)


def qualified_provenance(apk, version, commit, cert, drv, output):
    # The authenticated Nix package installCheck already accepted the full policy.
    metadata = apk_metadata(apk)
    # VERSION intentionally supplies Android versionName in the Gradle build.
    require(metadata['version_name'] == version, 'Unsigned versionName differs from VERSION')
    return validate_provenance(dict(version=version, source_commit=commit, tag='v' + version,
        unsigned_sha256=sha(apk), **metadata, nix_derivation=drv, nix_output=output,
        certificate_sha256=cert, java_runtime='0.2.0', protocol='v1/r19'))


def prepare(repo, args):
    cert = identity(repo, args.version, args.commit)
    check_drv = repo.run('nix', 'eval', '--raw', '.#checks.x86_64-linux.android.drvPath')
    package_drv = repo.run('nix', 'eval', '--raw', '.#packages.x86_64-linux.default.drvPath')
    require(check_drv == package_drv, 'checks.android/packages.default derivations differ')
    output = repo.run('nix', 'build', '--no-link', '--print-out-paths', '.#packages.x86_64-linux.default')
    require(output.startswith('/nix/store/') and '\n' not in output, 'Unexpected package output')
    apk = Path(output) / f'share/totipo-android/totipo-android-{args.version}-unsigned.apk'
    provenance = qualified_provenance(apk, args.version, args.commit, cert, check_drv, output)
    args.bundle.mkdir()
    shutil.copyfile(apk, args.bundle / 'unsigned-release.apk')
    (args.bundle / 'release-provenance.json').write_text(json.dumps(provenance, sort_keys=True, indent=2) + '\n')
    (args.bundle / 'unsigned-release.apk.sha256').write_text(sha(apk) + '  unsigned-release.apk\n')
    identity(repo, args.version, args.commit)


def bundle_check(repo, args):
    cert = identity(repo, args.version, args.commit)
    p = load_provenance(args.bundle / 'release-provenance.json')
    require(p['version'] == args.version and p['source_commit'] == args.commit and
            p['certificate_sha256'] == cert, 'Provenance identity differs')
    unsigned = args.bundle / 'unsigned-release.apk'
    bind_unsigned(unsigned, p)
    require((args.bundle / 'unsigned-release.apk.sha256').read_text() ==
            p['unsigned_sha256'] + '  unsigned-release.apk\n', 'Unsigned checksum record differs')
    return p


def verify_signed(repo, apk, bundle):
    subprocess.run(['python3', '-B', str(Path(__file__).with_name('verify-signed-apk.py')), str(apk),
                    '--fingerprint', str(repo.fingerprint), '--provenance', str(bundle / 'release-provenance.json'),
                    '--unsigned', str(bundle / 'unsigned-release.apk')], check=True)


def finalize(repo, args):
    p = bundle_check(repo, args)
    apk = args.bundle / f'totipo-android-{args.version}.apk'
    verify_signed(repo, apk, args.bundle)
    require(sha(args.bundle / 'unsigned-release.apk') == p['unsigned_sha256'], 'Unsigned input mutated')
    args.output.mkdir()
    shutil.copyfile(apk, args.output / apk.name)
    (args.output / (apk.name + '.sha256')).write_text(sha(apk) + '  ' + apk.name + '\n')
    for name in ('release-provenance.json', 'unsigned-release.apk', 'unsigned-release.apk.sha256'):
        shutil.copyfile(args.bundle / name, args.output / name)


def release_record(record, tag):
    require(type(record) is dict and type(record.get('id')) is int and record['id'] > 0
            and record.get('tag_name') == tag and type(record.get('draft')) is bool,
            'Malformed release response')
    return record


class GitHubFailure(ValueError):
    """An HTTP/transport failure, with retry eligibility only for reads."""
    def __init__(self, message, transient=False):
        super().__init__(message)
        self.transient = transient


def github_request(repo, endpoint, method='GET', fields=(), missing=False, binary=False, known=False):
    """One mutation attempt; at most three read attempts with 1s/2s backoff.

    No stderr matching, generic subprocess retries, or retries of invalid data.
    Capturing bytes discards incomplete downloads before the next GET attempt.
    """
    cmd = ['gh', 'api', '--include', '--method', method, endpoint,
           '-H', 'Accept: ' + ('application/octet-stream' if binary else 'application/vnd.github+json')]
    cmd.extend(fields)
    for attempt in range(3 if method == 'GET' else 1):
        try:
            result = subprocess.run(cmd, cwd=repo.root, capture_output=True, check=False, timeout=60)
        except subprocess.TimeoutExpired as error:
            failure = GitHubFailure('GitHub request timed out: ' + endpoint, transient=True)
            failure.__cause__ = error
        else:
            # gh follows binary download redirects and includes the final response.
            raw = result.stdout
            separator = re.search(rb'\r?\n\r?\n', raw)
            headers = raw[:separator.start()] if separator else raw
            body = raw[separator.end():] if separator else b''
            lines = headers.replace(b'\r\n', b'\n').split(b'\n')
            status = re.fullmatch(rb'HTTP/(?:1\.[01]|2(?:\.0)?) (\d{3})(?: [^\n]*)?', lines[0])
            if not separator or not status:
                require(result.returncode != 0, 'Missing/malformed GitHub HTTP response')
                failure = GitHubFailure('GitHub transport/CLI failed: ' + endpoint,
                                        transient=result.returncode == 1)
            else:
                require(all(b':' in line for line in lines[1:]), 'Malformed GitHub HTTP headers')
                code = int(status[1])
                if code == 404 and missing:
                    record = json.loads(body)
                    require(result.returncode == 1 and type(record) is dict and
                            type(record.get('message')) is str and bool(record['message']) and
                            record.get('status', '404') == '404', 'Malformed GitHub 404 response')
                    return None
                if code == (201 if method == 'POST' else 200) and result.returncode == 0:
                    return body if binary else json.loads(body)
                # Auth/permission failures (including secondary-limit 403) fail immediately.
                failure = GitHubFailure(f'GitHub {method} failed: HTTP {code}: {endpoint}',
                                        transient=(code == 200 and result.returncode != 0) or
                                        code in (408, 429, 500, 502, 503, 504) or
                                        (code == 404 and method == 'GET' and known))
        if method != 'GET' or not failure.transient or attempt == 2:
            raise failure
        time.sleep(attempt + 1)


def lookup_release(repo, github_repo, tag):
    # Published-only endpoint: 404 is absence here, never for a known release ID.
    record = github_request(repo, f'repos/{github_repo}/releases/tags/{tag}', missing=True)
    return None if record is None else release_record(record, tag)


def listed_release(repo, github_repo, tag):
    # Preflight only. Draft absence is not guaranteed; creation can still reject it.
    matching = []
    page = 1
    while True:
        records = github_request(repo, f'repos/{github_repo}/releases?per_page=100&page={page}')
        require(type(records) is list and all(type(r) is dict and
                type(r.get('tag_name')) is str for r in records), 'Malformed release list')
        matching.extend(release_record(r, tag) for r in records if r['tag_name'] == tag)
        require(len(matching) <= 1, 'Ambiguous releases for target tag')
        if len(records) < 100:
            return matching[0] if matching else None
        page += 1


def exact_release(record, tag, release_id, draft):
    release_record(record, tag)
    require(record['id'] == release_id and record['draft'] is draft,
            'Release ID/draft state differs')
    return record


def asset_record(record, local, asset_id=None):
    require(type(record) is dict and type(record.get('id')) is int and record['id'] > 0 and
            (asset_id is None or record['id'] == asset_id) and record.get('name') == local.name and
            record.get('state') == 'uploaded' and type(record.get('size')) is int and
            record['size'] == local.stat().st_size, 'Asset identity/state/size differs')
    return record['id']


def verify_remote(repo, base, tag, release_id, assets, draft):
    endpoint = f'{base}/releases/{release_id}'
    exact_release(github_request(repo, endpoint, known=True), tag, release_id, draft)
    # Three approved assets fit on one page. Any extra entry fails immediately.
    expected = {local.name: (local, asset_id) for local, asset_id in assets}
    for attempt in range(3):
        records = github_request(repo, endpoint + '/assets?per_page=100', known=True)
        require(type(records) is list and all(type(r) is dict and
                type(r.get('name')) is str for r in records), 'Malformed remote asset set')
        names = [r['name'] for r in records]
        require(len(names) == len(set(names)) and set(names) <= set(expected),
                'Remote asset names differ')
        for record in records:
            local, asset_id = expected[record['name']]
            asset_record(record, local, asset_id)
        if set(names) == set(expected):
            break
        require(attempt < 2, 'Remote asset set incomplete')
        time.sleep(attempt + 1)
    for record in records:
        local, asset_id = expected[record['name']]
        asset_endpoint = f'{base}/releases/assets/{asset_id}'
        asset_record(github_request(repo, asset_endpoint, known=True), local, asset_id)
        data = github_request(repo, asset_endpoint, binary=True, known=True)
        require(hashlib.sha256(data).hexdigest() == sha(local), 'Remote release bytes differ')
    exact_release(github_request(repo, endpoint, known=True), tag, release_id, draft)


def publish(repo, args):
    p = bundle_check(repo, args)
    apk = args.bundle / f'totipo-android-{args.version}.apk'
    checksum = args.bundle / (apk.name + '.sha256')
    require(checksum.read_text() == sha(apk) + '  ' + apk.name + '\n', 'Signed checksum differs')
    verify_signed(repo, apk, args.bundle)
    github_repo = os.environ['GITHUB_REPOSITORY']
    base = f'repos/{github_repo}'
    tag = p['tag']
    require(lookup_release(repo, github_repo, tag) is None, 'Release already exists')
    require(listed_release(repo, github_repo, tag) is None, 'Release/draft already exists')
    notes = (f"Totipo Android {args.version}\n\nSource: {args.commit}\nTag: {tag}\n"
             f"APK: {apk.name}\nSHA-256: {sha(apk)}\n"
             f"Signing certificate SHA-256: {p['certificate_sha256']}\n"
             "Java runtime: 0.2.0; protocol: v1/r19.\n")
    # REST does not have --verify-tag: identity() supplies the stronger annotated
    # local/remote tag-object and source checks immediately before this POST.
    identity(repo, args.version, args.commit)
    record = release_record(github_request(repo, base + '/releases', 'POST',
        ('-f', 'tag_name=' + tag, '-f', 'target_commitish=' + args.commit,
         '-F', 'draft=true', '-F', 'prerelease=false',
         '-f', 'name=Totipo Android ' + args.version, '-f', 'body=' + notes)), tag)
    release_id = record['id']
    exact_release(record, tag, release_id, True)
    print(f'Created draft release ID {release_id}', flush=True)
    endpoint = f'{base}/releases/{release_id}'
    exact_release(github_request(repo, endpoint, known=True), tag, release_id, True)
    assets = []
    for local in (apk, checksum, args.bundle / 'release-provenance.json'):
        # Fixed GitHub upload host/path, never an untrusted response URL. A failed
        # or ambiguous upload stops; no replacement/deletion/repeated POST.
        uploaded = github_request(repo,
            f'https://uploads.github.com/{endpoint}/assets?name={quote(local.name, safe="")}',
            'POST', ('-H', 'Content-Type: application/octet-stream',
                     '-H', 'Content-Length: ' + str(local.stat().st_size), '--input', str(local)))
        asset_id = asset_record(uploaded, local)
        require(asset_id not in [i for _, i in assets], 'Duplicate uploaded asset ID')
        assets.append((local, asset_id))
    verify_remote(repo, base, tag, release_id, assets, True)
    identity(repo, args.version, args.commit)
    exact_release(github_request(repo, endpoint, known=True), tag, release_id, True)
    try:
        published = github_request(repo, endpoint, 'PATCH', ('-F', 'draft=false'))
    except (GitHubFailure, json.JSONDecodeError) as error:
        # Never repeat PATCH. A transient/ambiguous result can be accepted only
        # after read-only reconciliation proves the exact published state/bytes.
        if isinstance(error, GitHubFailure) and not error.transient:
            raise
        print(f'Publication response ambiguous for release ID {release_id}; verifying state', flush=True)
    else:
        exact_release(published, tag, release_id, False)
    verify_remote(repo, base, tag, release_id, assets, False)
    print(f'Published and verified release ID {release_id}', flush=True)


def execute_source(repo, command, target, json_output=False):
    require(command in ('prepare-release', 'check-release', 'release-tag'), 'Unknown source command')
    source.version(target)
    root = source.worktree(repo)
    remote = source.remote_refs(repo)
    ref = 'refs/tags/v' + target
    tagging = command == 'release-tag'
    require(tagging or (ref not in remote and ref + '^{}' not in remote), 'Target remote tag already exists')
    local = source.local_refs(repo)
    require(tagging or ref not in local, 'Target local tag already exists')
    consumed = source.history(repo, remote, exclude=ref if tagging else None)
    expected = source.next_code(target, consumed)
    source.dirty_policy(repo, target, expected, clean=tagging)
    source.source_ready(repo)
    if command == 'prepare-release':
        current = (root / 'VERSION').read_bytes()
        source.version(current.decode().removesuffix('\n'))
        require(current.endswith(b'\n'), 'Malformed VERSION')
        changes = [(root / 'VERSION', (target + '\n').encode()),
                   (root / 'VERSION_CODE', f'{expected}\n'.encode())]
        changed = [p.name for p, data in changes if p.read_bytes() != data]
        for path, data in changes:
            if path.name in changed:
                source.atomic_write(path, data)
        # Focused post-write checks; no build, signing or Git mutation.
        source.dirty_policy(repo, target, expected)
        source.source_ready(repo)
        print(('Prepared' if changed else 'Already prepared') + f' VERSION={target}; VERSION_CODE={expected}')
        print('Changed paths: ' + (', '.join(changed) or 'none'))
        return
    require((root / 'VERSION').read_bytes() == (target + '\n').encode(), 'VERSION differs from target')
    require(source.code((root / 'VERSION_CODE').read_bytes()) == expected, 'VERSION_CODE differs from consumed history')
    if tagging:
        result = source.release_tag(repo, target, expected, remote, ref, local)
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


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['identity', 'guard', 'prepare', 'bundle', 'finalize', 'publish',
                        'prepare-release', 'check-release', 'release-tag'])
    parser.add_argument('target', nargs='?')
    parser.add_argument('--version', default=os.environ.get('REQUESTED_VERSION'))
    parser.add_argument('--commit', default=os.environ.get('REQUESTED_COMMIT'))
    parser.add_argument('--bundle', type=Path)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--json', action='store_true', help='Machine-readable release-tag coordinates')
    args = parser.parse_args()
    if args.json and args.command != 'release-tag':
        parser.error('--json requires release-tag')
    repo = discover()
    if args.command in ('prepare-release', 'check-release', 'release-tag'):
        execute_source(repo, args.command, args.target, args.json)
    elif args.command == 'identity':
        identity(repo, args.version, args.commit)
    elif args.command == 'guard':
        guard(repo)
    else:
        {'prepare': prepare, 'bundle': bundle_check, 'finalize': finalize, 'publish': publish}[args.command](repo, args)
