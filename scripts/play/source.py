#!/usr/bin/env python3
"""Resolve an immutable main source and require its completed verification run."""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time

REPO = 'hebi10/HomeCrew'
WORKFLOW = '.github/workflows/android.yml'
REQUIRED_JOBS = {'verify'}
CI_WAIT_TIMEOUT = 3000
CI_POLL_INTERVAL = 30


class SourceError(RuntimeError):
    pass


def resolve(env, event):
    if env.get('GITHUB_REPOSITORY') != REPO:
        raise SourceError('Release workflow is restricted to ' + REPO)
    if env.get('GITHUB_EVENT_NAME') == 'workflow_dispatch':
        if env.get('GITHUB_REF') != 'refs/heads/main':
            raise SourceError('Manual Play workflows must run from main')
        sha = env.get('GITHUB_SHA', '')
    elif env.get('GITHUB_EVENT_NAME') == 'workflow_run':
        run = event.get('workflow_run', {})
        if (run.get('name') != 'Android CI' or run.get('path') != WORKFLOW or run.get('event') != 'push'
                or run.get('conclusion') != 'success' or run.get('head_branch') != 'main'
                or run.get('head_repository', {}).get('full_name') != REPO):
            raise SourceError('Automatic deploy requires a successful same-repository main push verification')
        sha = run.get('head_sha', '')
    else:
        raise SourceError('Unsupported release event')
    if not re.fullmatch(r'[a-f0-9]{40}', sha):
        raise SourceError('Release source must be a complete immutable commit SHA')
    return sha


def require_current_main(sha, current_main):
    if not re.fullmatch(r'[a-f0-9]{40}', sha) or sha != current_main:
        raise SourceError('Release source is stale; rerun from the current verified main commit')


def release_mode(env, event, commit_subject):
    resolve(env, event)
    if env.get('GITHUB_EVENT_NAME') == 'workflow_dispatch':
        mode = env.get('PLAY_REQUESTED_MODE', 'check')
        if mode not in ('check', 'build', 'deploy', 'initialize'):
            raise SourceError('Unsupported Play release mode')
        return mode
    subject = commit_subject.splitlines()[0] if commit_subject else ''
    if re.search(r'(?<!\S)\[skip-play\](?!\S)', subject):
        return 'skip'
    return 'initialize' if re.search(r'(?<!\S)\[initialize-play\](?!\S)', subject) else 'deploy'


def required_workflows(root=Path('.')):
    workflows = ['android.yml']
    if (Path(root) / '.github/workflows/screenshot-qa.yml').is_file():
        workflows.append('screenshot-qa.yml')
    return workflows


def verify_ci(sha, run, jobs, workflow='android.yml'):
    if (run.get('head_sha') != sha or run.get('head_branch') != 'main' or run.get('conclusion') != 'success'
            or run.get('status', 'completed') != 'completed'
            or run.get('event') not in ('push', 'workflow_dispatch') or run.get('path') != '.github/workflows/' + workflow):
        raise SourceError('Required main verification has not succeeded for this exact source SHA')
    successful = {job.get('name') for job in jobs if job.get('conclusion') == 'success'}
    if (not jobs or any(job.get('conclusion') != 'success' for job in jobs)
            or (workflow == 'android.yml' and not REQUIRED_JOBS.issubset(successful))):
        raise SourceError('All required Android build and instrumentation jobs must succeed')


def gh(path):
    result = subprocess.run(['gh', 'api', path], capture_output=True, check=False, text=True)
    if result.returncode:
        raise SourceError('Could not read required GitHub verification metadata')
    try:
        return json.loads(result.stdout)
    except ValueError:
        raise SourceError('Invalid GitHub verification metadata') from None


def run_order(run):
    try:
        run_id, attempt = run['id'], run['run_attempt']
        if type(run_id) is not int or type(attempt) is not int or run_id <= 0 or attempt <= 0:
            raise ValueError
        started = run.get('run_started_at')
        if not started and attempt == 1:
            started = run.get('created_at')
        if not isinstance(started, str) or not re.fullmatch(
                r'\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})', started):
            raise ValueError
        timestamp = datetime.fromisoformat(started.replace('Z', '+00:00')).astimezone(timezone.utc)
        return timestamp, run_id, attempt
    except (KeyError, TypeError, ValueError):
        raise SourceError('Required CI run ordering metadata is missing or invalid') from None


def require_workflow(sha, workflow, wait=False):
    deadline = time.monotonic() + CI_WAIT_TIMEOUT
    while True:
        require_current_main(sha, gh(f'repos/{REPO}/git/ref/heads/main')['object']['sha'])
        runs = gh(f'repos/{REPO}/actions/workflows/{workflow}/runs?head_sha={sha}&branch=main&per_page=100')['workflow_runs']
        candidates = [run for run in runs if run.get('event') in ('push', 'workflow_dispatch')]
        if candidates:
            run = max(candidates, key=run_order)
            payload = gh(f'repos/{REPO}/actions/runs/{run["id"]}/jobs?per_page=100')
            jobs = payload['jobs']
            if payload.get('total_count', len(jobs)) != len(jobs):
                raise SourceError('Required CI job list is incomplete')
            if any(job.get('conclusion') not in (None, 'success') for job in jobs):
                raise SourceError('A required CI job failed or was skipped; release is blocked')
            if run.get('conclusion') is not None or run.get('status') == 'completed':
                verify_ci(sha, run, jobs, workflow)
                return str(run['id'])
        if not wait:
            raise SourceError('Required ' + workflow + ' is missing or pending for the exact main source SHA')
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise SourceError('Timed out waiting for exact-main ' + workflow + '; release is blocked')
        print('Waiting for exact-main ' + workflow + '; no release has started', flush=True)
        time.sleep(min(CI_POLL_INTERVAL, remaining))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--assert-current', metavar='SHA')
    args = parser.parse_args()
    try:
        if args.assert_current:
            current = gh(f'repos/{REPO}/git/ref/heads/main')['object']['sha']
            require_current_main(args.assert_current, current)
            for workflow in required_workflows():
                require_workflow(args.assert_current, workflow)
            print('Release source still matches current main and latest required CI/QA passed')
            return 0
        event = json.loads(Path(os.environ['GITHUB_EVENT_PATH']).read_text(encoding='utf-8'))
        sha = resolve(os.environ, event)
        current = gh(f'repos/{REPO}/git/ref/heads/main')['object']['sha']
        require_current_main(sha, current)
        ancestry = subprocess.run(['git', 'merge-base', '--is-ancestor', sha, 'origin/main'], capture_output=True, check=False)
        if ancestry.returncode:
            raise SourceError('Release source is not an ancestor of the trusted main branch')
        subject = subprocess.run(['git', 'show', '-s', '--format=%s', sha], capture_output=True, check=False, text=True)
        if subject.returncode:
            raise SourceError('Could not inspect the trusted release commit subject')
        mode = release_mode(os.environ, event, subject.stdout)
        if mode == 'skip':
            # A main CI-only workflow change must not allocate or upload a new app binary.
            # The triggering workflow_run was already proven to be a successful main push.
            with open(os.environ['GITHUB_OUTPUT'], 'a', encoding='utf-8') as output:
                output.write(f'sha={sha}\nrelease_mode=skip\n')
            print('Trusted main CI-only commit: Play binary submission intentionally skipped')
            return 0
        run_ids = []
        for workflow in required_workflows():
            wait = os.environ.get('GITHUB_EVENT_NAME') == 'workflow_run' and workflow != 'android.yml'
            run_ids.append(require_workflow(sha, workflow, wait=wait))
        with open(os.environ['GITHUB_OUTPUT'], 'a', encoding='utf-8') as output:
            output.write(f'sha={sha}\nverify_run_id={run_ids[0]}\nrelease_mode={mode}\n')
        print(f'Trusted main source {sha}; required CI runs {", ".join(run_ids)} passed')
        return 0

    except (SourceError, KeyError, OSError, ValueError) as error:
        message = str(error) if isinstance(error, SourceError) else 'Source metadata is missing or invalid'
        print('Release source check failed: ' + message, file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
