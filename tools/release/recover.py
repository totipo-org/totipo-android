#!/usr/bin/env python3
"""Deliberate recovery of an existing protected-run candidate; never signs/builds."""
import argparse
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import zipfile
import release
from payload import compare, require

ROOT = release.ROOT
WORKFLOW = '.github/workflows/release.yml'
ARTIFACT = 'verified-release'
RECEIPT = 'recovery-evidence.json'


def inputs(version, commit, run_id):
    release.validate_request(version, commit)
    require(type(run_id) is str and re.fullmatch(r'[1-9][0-9]*', run_id), 'Invalid failed_run_id')


def recovery_identity(version, commit):
    release.validate_request(version, commit)
    require(os.environ['GITHUB_REF'] == 'refs/heads/main', 'Recovery tooling must run from main')
    tooling = os.environ['GITHUB_SHA']
    require(re.fullmatch('[0-9a-f]{40}', tooling), 'Invalid tooling SHA')
    require(release.run('git', 'rev-parse', 'HEAD') == tooling, 'Recovery tooling checkout differs')
    require(not release.run('git', 'status', '--porcelain', '--untracked-files=all'), 'Checkout mutated')
    remote = dict(line.split()[::-1] for line in release.run('git', 'ls-remote', 'origin',
                  'refs/heads/main').splitlines())
    require(remote.get('refs/heads/main') == tooling, 'Recovery tooling no longer current main')
    release.guard()
    cert = release.expected_identity()
    target_identity(version, commit)
    return cert


def target_identity(version, commit):
    """Existing local/remote annotated tag object, exact old source and identity."""
    release.validate_request(version, commit)
    tag = 'refs/tags/v' + version
    require(release.run('git', 'cat-file', '-t', tag) == 'tag', 'Annotated target tag required')
    require(release.run('git', 'cat-file', '-t', commit) == 'commit', 'Target source commit required')
    require(release.run('git', 'rev-parse', tag + '^{commit}') == commit, 'Target tag/source mismatch')
    obj = release.run('git', 'rev-parse', tag)
    remote = dict(line.split()[::-1] for line in release.run('git', 'ls-remote', 'origin',
                  tag, tag + '^{}').splitlines())
    require(remote.get(tag) == obj and remote.get(tag + '^{}') == commit, 'Remote target tag differs')
    # run() strips surrounding whitespace; inspect exact source blob bytes instead.
    for name, expected in [('VERSION', (version + '\n').encode()),
                           ('release/signing/android-release-cert.pem', release.CERT.read_bytes()),
                           ('release/signing/android-release-cert.sha256', release.FP.read_bytes())]:
        actual = subprocess.check_output(['git', 'show', commit + ':' + name], cwd=ROOT)
        require(actual == expected, 'Target source VERSION/public identity differs: ' + name)
    return obj


def api(path):
    repo = os.environ['GITHUB_REPOSITORY']
    return json.loads(release.run('gh', 'api', f'repos/{repo}/{path}'))


def pages(path, key):
    repo = os.environ['GITHUB_REPOSITORY']
    values = json.loads(release.run('gh', 'api', '--paginate', '--slurp',
                                    f'repos/{repo}/{path}?per_page=100'))
    require(type(values) is list and values and
            all(type(p) is dict and type(p.get(key)) is list for p in values), 'Malformed API pages')
    return [item for p in values for item in p[key]]


def validate_run(record, workflow, repo, commit, run_id):
    require(record.get('id') == int(run_id), 'Wrong run ID')
    require(record.get('repository', {}).get('full_name') == repo and
            record.get('head_repository', {}).get('full_name') == repo, 'Wrong run repository')
    require(workflow.get('path') == WORKFLOW and record.get('path') == WORKFLOW and
            type(workflow.get('id')) is int and record.get('workflow_id') == workflow['id'],
            'Wrong release workflow')
    require(record.get('head_sha') == commit, 'Wrong run source SHA')
    require(record.get('head_branch') == 'main', 'Wrong run branch')
    require(record.get('event') == 'workflow_dispatch', 'Wrong run event')
    require(record.get('status') == 'completed' and record.get('conclusion') == 'failure',
            'Expected completed failed run')
    # Rerun artifact/job selection needs a separate attempt-aware policy.
    require(record.get('run_attempt') == 1, 'Only original failed run attempt supported')


def select_artifact(records, repo_id, commit, run_id):
    require(all(type(a) is dict for a in records), 'Malformed artifact list')
    matching = [a for a in records if a.get('name') == ARTIFACT]
    require(len(matching) == 1, 'Missing/ambiguous verified-release artifact')
    a = matching[0]
    require(a.get('expired') is False and type(a.get('id')) is int and a['id'] > 0,
            'Expired/unavailable artifact')
    owner = a.get('workflow_run', {})
    require(owner.get('id') == int(run_id) and owner.get('repository_id') == repo_id and
            owner.get('head_repository_id') == repo_id and owner.get('head_sha') == commit and
            owner.get('head_branch') == 'main', 'Artifact run/source/repository differs')
    require(re.fullmatch(r'sha256:[0-9a-f]{64}', a.get('digest', '')), 'Missing artifact digest')
    return a


def names(version):
    apk = f'totipo-android-{version}.apk'
    return {apk, apk + '.sha256', 'release-provenance.json',
            'unsigned-release.apk', 'unsigned-release.apk.sha256'}


def extract(archive, bundle, version, digest):
    require(release.sha(archive) == digest.removeprefix('sha256:'), 'Artifact archive digest differs')
    with zipfile.ZipFile(archive) as z:
        entries = z.infolist()
        require(len(entries) == len(names(version)) and {e.filename for e in entries} == names(version),
                'Unexpected archive contents')
        require(sum(e.file_size for e in entries) <= 256 * 1024 * 1024, 'Oversized artifact')
        require(all(stat.S_IFMT(e.external_attr >> 16) in (0, stat.S_IFREG) for e in entries),
                'Nonregular archive entry')
        require(z.testzip() is None, 'Corrupt artifact ZIP')
        bundle.mkdir()
        for e in entries:
            (bundle / e.filename).write_bytes(z.read(e))


def verify_bundle(args, receipt=False):
    expected = names(args.version) | ({RECEIPT} if receipt else set())
    require({p.name for p in args.bundle.iterdir()} == expected and
            all(p.is_file() and not p.is_symlink() for p in args.bundle.iterdir()),
            'Unexpected recovery bundle contents')
    p = release.bundle_check(args, identity_check=recovery_identity)
    require(p['version_name'] == args.version, 'Provenance versionName differs from target VERSION')
    apk = args.bundle / f'totipo-android-{args.version}.apk'
    require((args.bundle / (apk.name + '.sha256')).read_text() ==
            release.sha(apk) + '  ' + apk.name + '\n', 'Signed checksum differs')
    release.verify_signed(apk, args.bundle)
    compare(args.bundle / 'unsigned-release.apk', apk)
    print('Recovered unsigned SHA-256:', release.sha(args.bundle / 'unsigned-release.apk'))
    print('Recovered signed SHA-256:', release.sha(apk))


def acquire(args):
    inputs(args.version, args.commit, args.failed_run_id)
    recovery_identity(args.version, args.commit)
    tag_object = target_identity(args.version, args.commit)
    repo = os.environ['GITHUB_REPOSITORY']
    workflow = api('actions/workflows/release.yml')
    record = api('actions/runs/' + args.failed_run_id)
    validate_run(record, workflow, repo, args.commit, args.failed_run_id)
    jobs = pages('actions/runs/' + args.failed_run_id + '/jobs', 'jobs')
    for name, conclusion in [('qualify', 'success'), ('sign', 'success'),
                             ('verify', 'success'), ('publish', 'failure')]:
        matching = [j for j in jobs if j.get('name') == name]
        require(len(matching) == 1 and matching[0].get('conclusion') == conclusion,
                'Unexpected original job evidence: ' + name)
    artifact = select_artifact(pages('actions/runs/' + args.failed_run_id + '/artifacts', 'artifacts'),
                               record['repository']['id'], args.commit, args.failed_run_id)
    archive = args.bundle.parent / 'original-verified-release.zip'
    with archive.open('xb') as out:
        subprocess.run(['gh', 'api', f"repos/{repo}/actions/artifacts/{artifact['id']}/zip"],
                       cwd=ROOT, stdout=out, check=True, timeout=120)
    extract(archive, args.bundle, args.version, artifact['digest'])
    verify_bundle(args)
    # Same-run artifact boundary transports this receipt and unchanged candidate.
    evidence = dict(tooling_commit=os.environ['GITHUB_SHA'], source_commit=args.commit,
                    version=args.version, failed_run_id=args.failed_run_id, tag_object=tag_object,
                    artifact_id=artifact['id'], artifact_digest=artifact['digest'])
    (args.bundle / RECEIPT).write_text(json.dumps(evidence, sort_keys=True, indent=2) + '\n')
    recovery_identity(args.version, args.commit)


def publish(args):
    inputs(args.version, args.commit, args.failed_run_id)
    recovery_identity(args.version, args.commit)
    evidence = json.loads((args.bundle / RECEIPT).read_text())
    require(type(evidence) is dict and set(evidence) == {'tooling_commit', 'source_commit', 'version',
            'failed_run_id', 'tag_object', 'artifact_id', 'artifact_digest'}, 'Malformed recovery receipt')
    require(evidence['tooling_commit'] == os.environ['GITHUB_SHA'] and
            evidence['source_commit'] == args.commit and evidence['version'] == args.version and
            evidence['failed_run_id'] == args.failed_run_id and
            evidence['tag_object'] == target_identity(args.version, args.commit), 'Recovery receipt identity differs')
    require(type(evidence['artifact_id']) is int and evidence['artifact_id'] > 0 and
            re.fullmatch(r'sha256:[0-9a-f]{64}', evidence['artifact_digest']), 'Invalid recovery artifact receipt')
    verify_bundle(args, receipt=True)
    def final_identity(version, commit):
        cert = recovery_identity(version, commit)
        require(target_identity(version, commit) == evidence['tag_object'], 'Target tag object changed')
        return cert
    release.publish(args, identity_check=final_identity)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['identity', 'acquire', 'publish'])
    parser.add_argument('--version', default=os.environ.get('REQUESTED_VERSION'))
    parser.add_argument('--commit', default=os.environ.get('REQUESTED_COMMIT'))
    parser.add_argument('--failed-run-id', default=os.environ.get('FAILED_RUN_ID'))
    parser.add_argument('--bundle', type=Path)
    args = parser.parse_args()
    inputs(args.version, args.commit, args.failed_run_id)
    if args.command == 'identity':
        recovery_identity(args.version, args.commit)
    else:
        {'acquire': acquire, 'publish': publish}[args.command](args)
