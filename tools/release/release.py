#!/usr/bin/env python3
"""No-secret source, public identity, bundle and publication operations."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
from payload import compare, require

ROOT = Path(__file__).resolve().parents[2]
CERT = ROOT / 'release/signing/android-release-cert.pem'
FP = ROOT / 'release/signing/android-release-cert.sha256'


def run(*args):
    return subprocess.check_output(args, text=True, cwd=ROOT).strip()


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def expected_identity():
    require(CERT.is_file() and FP.is_file(),
            'Production identity unprovisioned: public android-release-cert.pem/.sha256 required')
    value = FP.read_text().strip()
    require(re.fullmatch('[0-9a-f]{64}', value), 'Malformed certificate fingerprint')
    pem = CERT.read_text()
    match = re.fullmatch(r'-----BEGIN CERTIFICATE-----\n([A-Za-z0-9+/=\n]+)-----END CERTIFICATE-----\n?', pem)
    require(match, 'Expected exactly one PEM certificate')
    der = base64.b64decode(match[1].replace('\n', ''), validate=True)
    require(hashlib.sha256(der).hexdigest() == value, 'Certificate/fingerprint mismatch')
    # Parse certificate with pinned JDK, not merely base64 syntax.
    run(str(Path(os.environ['JAVA_HOME']) / 'bin/keytool'), '-printcert', '-file', str(CERT))
    return value


def guard():
    tracked = run('git', 'ls-files', '-z', 'release/signing').split('\0')
    allowed = {'release/signing/README.md', 'release/signing/android-release-cert.pem',
               'release/signing/android-release-cert.sha256'}
    require(all(not p or p in allowed for p in tracked), 'Unexpected tracked signing file')
    for path in (ROOT / 'release/signing').iterdir():
        require(path.name in {Path(p).name for p in allowed}, 'Unexpected signing material on disk')


def identity(version, commit):
    require(re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*)?', version),
            'Invalid version')
    require(not version.endswith(('-dev', '-SNAPSHOT')), 'Development version cannot release')
    require(re.fullmatch('[0-9a-f]{40}', commit), 'Full reviewed commit required')
    require(os.environ['GITHUB_REF'] == 'refs/heads/main', 'Dispatch must select main')
    require(os.environ['GITHUB_SHA'] == commit, 'Dispatch SHA must equal reviewed commit')
    require(run('git', 'rev-parse', 'HEAD') == commit, 'Checkout differs from reviewed source')
    require((ROOT / 'VERSION').read_text() == version + '\n', 'VERSION differs')
    require(not run('git', 'status', '--porcelain', '--untracked-files=all'), 'Checkout mutated')
    tag = 'refs/tags/v' + version
    require(run('git', 'cat-file', '-t', tag) == 'tag', 'Existing annotated tag required')
    require(run('git', 'rev-parse', tag + '^{commit}') == commit, 'Tag source differs')
    remote = dict(line.split()[::-1] for line in run('git', 'ls-remote', 'origin',
                  'refs/heads/main', tag, tag + '^{}').splitlines())
    require(remote.get('refs/heads/main') == commit, 'Reviewed source is no longer current main')
    require(remote.get(tag) == run('git', 'rev-parse', tag) and
            remote.get(tag + '^{}') == commit, 'Remote annotated tag differs')
    guard()
    return expected_identity()


def unsigned_check(apk):
    subprocess.run(['python3', '-B', str(ROOT / 'tools/verify-apk.py'), str(apk),
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
    require(re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*)?', p['version']),
            'Invalid provenance version')
    require(re.fullmatch('[0-9a-f]{40}', p['source_commit']), 'Invalid provenance source commit')
    require(p['tag'] == 'v' + p['version'], 'Invalid provenance tag')
    require(p['application_id'] == APPLICATION_ID, 'Provenance applicationId differs')
    require(type(p['version_code']) is int and 1 <= p['version_code'] <= 2100000000,
            'Invalid provenance versionCode')
    require(bool(p['version_name']) and not any(ord(c) < 32 for c in p['version_name']),
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
    sdk = Path(os.environ['ANDROID_HOME']) / 'build-tools/36.0.0'
    badging = run(str(sdk / 'aapt2'), 'dump', 'badging', str(apk))
    matches = re.findall(r"^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'(?: |$)",
                         badging, re.M)
    require(len(matches) == 1, 'Expected exactly one APK package metadata record')
    app, code, name = matches[0]
    require(app == APPLICATION_ID, 'APK applicationId differs')
    require(1 <= int(code) <= 2100000000, 'Invalid APK versionCode')
    return dict(application_id=app, version_code=int(code), version_name=name)


def require_metadata(apk, p):
    actual = apk_metadata(apk)
    for field, label in (('application_id', 'applicationId'), ('version_code', 'versionCode'),
                         ('version_name', 'versionName')):
        require(actual[field] == p[field], label + ' mismatch: APK differs from provenance')


def bind_unsigned(apk, p):
    require(sha(apk) == p['unsigned_sha256'], 'Qualified unsigned APK changed (SHA-256 mismatch)')
    require_metadata(apk, p)


def qualified_provenance(apk, version, commit, cert, drv, output):
    unsigned_check(apk)
    metadata = apk_metadata(apk)
    # VERSION intentionally supplies Android versionName in the Gradle build.
    require(metadata['version_name'] == version, 'Unsigned versionName differs from VERSION')
    return validate_provenance(dict(version=version, source_commit=commit, tag='v' + version,
        unsigned_sha256=sha(apk), **metadata, nix_derivation=drv, nix_output=output,
        certificate_sha256=cert, java_runtime='0.2.0', protocol='v1/r19'))


def prepare(args):
    cert = identity(args.version, args.commit)
    check_drv = run('nix', 'eval', '--raw', 'path:.#checks.x86_64-linux.android.drvPath')
    package_drv = run('nix', 'eval', '--raw', 'path:.#packages.x86_64-linux.default.drvPath')
    require(check_drv == package_drv, 'checks.android/packages.default derivations differ')
    output = run('nix', 'build', '--no-link', '--print-out-paths', 'path:.#packages.x86_64-linux.default')
    require(output.startswith('/nix/store/') and '\n' not in output, 'Unexpected package output')
    apk = Path(output) / f'share/totipo-android/totipo-android-{args.version}-unsigned.apk'
    provenance = qualified_provenance(apk, args.version, args.commit, cert, check_drv, output)
    args.bundle.mkdir()
    shutil.copyfile(apk, args.bundle / 'unsigned-release.apk')
    (args.bundle / 'release-provenance.json').write_text(json.dumps(provenance, sort_keys=True, indent=2) + '\n')
    (args.bundle / 'unsigned-release.apk.sha256').write_text(sha(apk) + '  unsigned-release.apk\n')
    identity(args.version, args.commit)


def bundle_check(args):
    cert = identity(args.version, args.commit)
    p = load_provenance(args.bundle / 'release-provenance.json')
    require(p['version'] == args.version and p['source_commit'] == args.commit and
            p['certificate_sha256'] == cert, 'Provenance identity differs')
    unsigned = args.bundle / 'unsigned-release.apk'
    bind_unsigned(unsigned, p)
    require((args.bundle / 'unsigned-release.apk.sha256').read_text() ==
            p['unsigned_sha256'] + '  unsigned-release.apk\n', 'Unsigned checksum record differs')
    unsigned_check(unsigned)
    return p


def verify_signed(apk, bundle):
    subprocess.run(['python3', '-B', str(ROOT / 'tools/release/verify-signed-apk.py'), str(apk),
                    '--fingerprint', str(FP), '--provenance', str(bundle / 'release-provenance.json'),
                    '--unsigned', str(bundle / 'unsigned-release.apk')], check=True)


def finalize(args):
    p = bundle_check(args)
    apk = args.bundle / f'totipo-android-{args.version}.apk'
    verify_signed(apk, args.bundle)
    compare(args.bundle / 'unsigned-release.apk', apk)
    require(sha(args.bundle / 'unsigned-release.apk') == p['unsigned_sha256'], 'Unsigned input mutated')
    args.output.mkdir()
    shutil.copyfile(apk, args.output / apk.name)
    (args.output / (apk.name + '.sha256')).write_text(sha(apk) + '  ' + apk.name + '\n')
    for name in ('release-provenance.json', 'unsigned-release.apk', 'unsigned-release.apk.sha256'):
        shutil.copyfile(args.bundle / name, args.output / name)


def publish(args):
    p = bundle_check(args)
    apk = args.bundle / f'totipo-android-{args.version}.apk'
    checksum = args.bundle / (apk.name + '.sha256')
    require(checksum.read_text() == sha(apk) + '  ' + apk.name + '\n', 'Signed checksum differs')
    verify_signed(apk, args.bundle)
    repo = os.environ['GITHUB_REPOSITORY']
    tag = p['tag']
    # Fail on existing release, including drafts. No overwrite/resume ambiguity.
    releases = json.loads(run('gh', 'api', f'repos/{repo}/releases?per_page=100'))
    require(not any(r['tag_name'] == tag for r in releases), 'Release already exists')
    notes = args.bundle.parent / 'release-notes.txt'
    notes.write_text(f"Totipo Android {args.version}\n\nSource: {args.commit}\nTag: {tag}\n"
                     f"APK: {apk.name}\nSHA-256: {sha(apk)}\n"
                     f"Signing certificate SHA-256: {p['certificate_sha256']}\n"
                     "Java runtime: 0.2.0; protocol: v1/r19.\n")
    run('gh', 'release', 'create', tag, '--repo', repo, '--verify-tag', '--draft',
        '--title', f'Totipo Android {args.version}', '--notes-file', str(notes),
        str(apk), str(checksum), str(args.bundle / 'release-provenance.json'))
    record = json.loads(run('gh', 'api', f'repos/{repo}/releases/tags/{tag}'))
    require(record['draft'] and record['tag_name'] == tag, 'Expected draft release')
    downloaded = args.bundle.parent / 'remote-release'
    downloaded.mkdir()
    run('gh', 'release', 'download', tag, '--repo', repo, '--dir', str(downloaded))
    for local in (apk, checksum, args.bundle / 'release-provenance.json'):
        require(sha(downloaded / local.name) == sha(local), 'Remote release bytes differ')
    verify_signed(downloaded / apk.name, args.bundle)
    # Exact bytes imply the payload equivalence already proved in verify job.
    identity(args.version, args.commit)
    run('gh', 'api', '--method', 'PATCH', f"repos/{repo}/releases/{record['id']}",
        '-F', 'draft=false')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['identity', 'guard', 'prepare', 'bundle', 'finalize', 'publish'])
    parser.add_argument('--version', default=os.environ.get('REQUESTED_VERSION'))
    parser.add_argument('--commit', default=os.environ.get('REQUESTED_COMMIT'))
    parser.add_argument('--bundle', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    if args.command == 'identity':
        identity(args.version, args.commit)
    elif args.command == 'guard':
        guard()
    else:
        {'prepare': prepare, 'bundle': bundle_check, 'finalize': finalize, 'publish': publish}[args.command](args)
