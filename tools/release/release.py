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


def prepare(args):
    cert = identity(args.version, args.commit)
    check_drv = run('nix', 'eval', '--raw', 'path:.#checks.x86_64-linux.android.drvPath')
    package_drv = run('nix', 'eval', '--raw', 'path:.#packages.x86_64-linux.default.drvPath')
    require(check_drv == package_drv, 'checks.android/packages.default derivations differ')
    output = run('nix', 'build', '--no-link', '--print-out-paths', 'path:.#packages.x86_64-linux.default')
    require(output.startswith('/nix/store/') and '\n' not in output, 'Unexpected package output')
    apk = Path(output) / f'share/totipo-android/totipo-android-{args.version}-unsigned.apk'
    unsigned_check(apk)
    sdk = Path(os.environ['ANDROID_HOME']) / 'build-tools/36.0.0'
    badging = run(str(sdk / 'aapt2'), 'dump', 'badging', str(apk))
    require(re.search(r"^package: name='org.totipo.android' versionCode='1' versionName='" +
                      re.escape(args.version) + "'", badging, re.M), 'Unsigned package/version differs')
    args.bundle.mkdir()
    shutil.copyfile(apk, args.bundle / 'unsigned-release.apk')
    provenance = dict(version=args.version, source_commit=args.commit, tag='v' + args.version,
                      unsigned_sha256=sha(apk), application_id='org.totipo.android', version_code=1,
                      version_name=args.version, nix_derivation=check_drv, nix_output=output,
                      certificate_sha256=cert, java_runtime='0.2.0', protocol='v1/r19')
    (args.bundle / 'release-provenance.json').write_text(json.dumps(provenance, sort_keys=True, indent=2) + '\n')
    (args.bundle / 'unsigned-release.apk.sha256').write_text(sha(apk) + '  unsigned-release.apk\n')
    identity(args.version, args.commit)


def bundle_check(args, signed=False):
    cert = identity(args.version, args.commit)
    p = json.loads((args.bundle / 'release-provenance.json').read_text())
    require(p['version'] == args.version and p['source_commit'] == args.commit and
            p['tag'] == 'v' + args.version and p['certificate_sha256'] == cert and
            p['application_id'] == 'org.totipo.android' and p['version_code'] == 1 and
            p['version_name'] == args.version and p['java_runtime'] == '0.2.0' and
            p['protocol'] == 'v1/r19', 'Provenance identity differs')
    unsigned = args.bundle / 'unsigned-release.apk'
    if not signed:
        require(sha(unsigned) == p['unsigned_sha256'], 'Qualified unsigned APK changed')
        require((args.bundle / 'unsigned-release.apk.sha256').read_text() ==
                p['unsigned_sha256'] + '  unsigned-release.apk\n', 'Unsigned checksum record differs')
        unsigned_check(unsigned)
    return p


def verify_signed(apk, version):
    subprocess.run(['python3', '-B', str(ROOT / 'tools/release/verify-signed-apk.py'), str(apk),
                    '--fingerprint', str(FP), '--version', version], check=True)


def finalize(args):
    p = bundle_check(args)
    apk = args.bundle / f'totipo-android-{args.version}.apk'
    verify_signed(apk, args.version)
    compare(args.bundle / 'unsigned-release.apk', apk)
    require(sha(args.bundle / 'unsigned-release.apk') == p['unsigned_sha256'], 'Unsigned input mutated')
    p['signed_sha256'] = sha(apk)
    args.output.mkdir()
    shutil.copyfile(apk, args.output / apk.name)
    (args.output / (apk.name + '.sha256')).write_text(sha(apk) + '  ' + apk.name + '\n')
    (args.output / 'release-provenance.json').write_text(json.dumps(p, sort_keys=True, indent=2) + '\n')


def publish(args):
    p = bundle_check(args, signed=True)
    apk = args.bundle / f'totipo-android-{args.version}.apk'
    require(sha(apk) == p['signed_sha256'], 'Signed asset hash differs')
    checksum = args.bundle / (apk.name + '.sha256')
    require(checksum.read_text() == sha(apk) + '  ' + apk.name + '\n', 'Signed checksum differs')
    verify_signed(apk, args.version)
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
    verify_signed(downloaded / apk.name, args.version)
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
