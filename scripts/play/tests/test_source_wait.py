import contextlib
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).parents[1]))
import source

SHA = 'a' * 40


class SourceWaitTests(unittest.TestCase):
    def run_source(self, states, manual=False, stale_after_wait=False, assert_current=False, android_state='success'):
        clock = [0]
        sleeps = []
        qa_reads = [0]
        ci = {'id': 10, 'head_sha': SHA, 'head_branch': 'main', 'event': 'push',
              'name': 'Android CI', 'path': '.github/workflows/android.yml',
              'conclusion': 'success', 'status': 'completed',
              'head_repository': {'full_name': source.REPO}}

        def api(path):
            if path.endswith('/git/ref/heads/main'):
                return {'object': {'sha': 'b' * 40 if stale_after_wait and sleeps else SHA}}
            if '/workflows/android.yml/runs?' in path:
                return {'workflow_runs': [dict(ci, status='in_progress' if android_state == 'pending' else 'completed',
                    conclusion=None if android_state == 'pending' else android_state)]}
            if '/workflows/screenshot-qa.yml/runs?' in path:
                state = states[min(qa_reads[0], len(states) - 1)]
                qa_reads[0] += 1
                if state == 'missing':
                    return {'workflow_runs': []}
                return {'workflow_runs': [dict(ci, id=20, path='.github/workflows/screenshot-qa.yml',
                    status='in_progress' if state in ('pending', 'failed_job') else 'completed',
                    conclusion=None if state in ('pending', 'failed_job') else state)]}
            if '/actions/runs/10/jobs?' in path:
                return {'total_count': 1, 'jobs': [{'name': 'verify',
                    'conclusion': None if android_state == 'pending' else android_state}]}
            if '/actions/runs/20/jobs?' in path:
                state = states[min(qa_reads[0] - 1, len(states) - 1)]
                return {'total_count': 1, 'jobs': [{'name': 'screenshots',
                    'conclusion': 'failure' if state == 'failed_job' else None if state == 'pending' else state}]}
            raise AssertionError(path)

        def sleep(seconds):
            sleeps.append(seconds)
            clock[0] += seconds

        with tempfile.TemporaryDirectory() as directory:
            event = Path(directory) / 'event.json'
            event.write_text(json.dumps({'workflow_run': ci}))
            output = Path(directory) / 'output'
            env = {'GITHUB_REPOSITORY': source.REPO, 'GITHUB_EVENT_NAME': 'workflow_dispatch' if manual else 'workflow_run',
                   'GITHUB_REF': 'refs/heads/main', 'GITHUB_SHA': SHA, 'GITHUB_EVENT_PATH': str(event),
                   'GITHUB_OUTPUT': str(output), 'PLAY_REQUESTED_MODE': 'check'}
            errors = io.StringIO()
            with mock.patch.dict(os.environ, env), mock.patch.object(source, 'gh', side_effect=api), \
                 mock.patch.object(source, 'required_workflows', return_value=['android.yml', 'screenshot-qa.yml']), \
                 mock.patch.object(source, 'CI_WAIT_TIMEOUT', 60, create=True), \
                 mock.patch('time.monotonic', side_effect=lambda: clock[0]), mock.patch('time.sleep', side_effect=sleep), \
                 mock.patch('sys.argv', ['source.py'] + (['--assert-current', SHA] if assert_current else [])), \
                 mock.patch.object(source.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0, stdout='ordinary')), \
                 contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(errors):
                result = source.main()
            return result, errors.getvalue(), sleeps, output.read_text() if output.exists() else ''

    def test_automatic_pending_or_missing_qa_can_finish_before_deploy(self):
        for first in ('pending', 'missing'):
            with self.subTest(first=first):
                result, error, sleeps, output = self.run_source([first, 'success'])
                self.assertEqual(result, 0, error)
                self.assertEqual(len(sleeps), 1)
                self.assertIn('release_mode=deploy', output)

    def test_automatic_qa_wait_times_out_without_release_output(self):
        result, error, sleeps, output = self.run_source(['pending'])
        self.assertEqual(result, 1)
        self.assertIn('Timed out', error)
        self.assertEqual(sum(sleeps), 60)
        self.assertEqual(output, '')

    def test_terminal_failure_skipped_or_failed_job_never_waits(self):
        for state in ('failure', 'skipped', 'cancelled', 'failed_job'):
            with self.subTest(state=state):
                result, _, sleeps, output = self.run_source([state])
                self.assertEqual(result, 1)
                self.assertEqual(sleeps, [])
                self.assertEqual(output, '')

    def test_manual_pending_or_missing_qa_fails_immediately(self):
        for state in ('pending', 'missing'):
            result, _, sleeps, output = self.run_source([state], manual=True)
            self.assertEqual(result, 1)
            self.assertEqual(sleeps, [])
            self.assertEqual(output, '')

    def test_main_becoming_stale_during_wait_aborts_before_success(self):
        result, error, sleeps, output = self.run_source(['pending', 'success'], stale_after_wait=True)
        self.assertEqual(result, 1)
        self.assertIn('stale', error)
        self.assertEqual(len(sleeps), 1)
        self.assertEqual(output, '')

    def test_only_one_automatic_workflow_trigger_can_submit_a_release(self):
        workflow = (Path(__file__).parents[3] / '.github/workflows/play-internal.yml').read_text()
        automatic = workflow.split('  workflow_run:', 1)[1].split('  workflow_dispatch:', 1)[0]
        self.assertEqual(automatic.count('workflows:'), 1)
        self.assertIn('workflows: ["Android CI"]', automatic)
        self.assertNotIn('HomeCrew screen QA', automatic)

    def test_preupload_recheck_blocks_latest_pending_failed_or_skipped_ci_rerun(self):
        for state in ('pending', 'failure', 'skipped', 'cancelled'):
            for target in ('android', 'qa'):
                with self.subTest(state=state, target=target):
                    result, _, sleeps, output = self.run_source(
                        [state if target == 'qa' else 'success'], assert_current=True,
                        android_state=state if target == 'android' else 'success')
                    self.assertEqual(result, 1)
                    self.assertEqual(sleeps, [])
                    self.assertEqual(output, '')

    def test_preupload_recheck_accepts_exact_current_success_without_waiting(self):
        result, error, sleeps, output = self.run_source(['success'], assert_current=True)
        self.assertEqual(result, 0, error)
        self.assertEqual(sleeps, [])
        self.assertEqual(output, '')

    def test_latest_attempt_of_older_run_cannot_fall_back_to_a_newer_id_success(self):
        successful = {'id': 20, 'run_attempt': 1, 'run_started_at': '2026-11-10T10:00:00Z',
                      'head_sha': SHA, 'head_branch': 'main', 'event': 'push', 'conclusion': 'success',
                      'status': 'completed', 'path': '.github/workflows/android.yml'}
        rerun = dict(successful, id=10, run_attempt=2, run_started_at='2026-11-10T11:00:00Z',
                     conclusion=None, status='in_progress')

        def api(path):
            if path.endswith('/git/ref/heads/main'):
                return {'object': {'sha': SHA}}
            if '/workflows/android.yml/runs?' in path:
                return {'workflow_runs': [successful, rerun]}
            return {'total_count': 1, 'jobs': [{'name': 'verify', 'conclusion':
                None if '/runs/10/jobs?' in path else 'success'}]}

        with mock.patch.object(source, 'gh', side_effect=api):
            with self.assertRaises(source.SourceError):
                source.require_workflow(SHA, 'android.yml')


if __name__ == '__main__':
    unittest.main()
