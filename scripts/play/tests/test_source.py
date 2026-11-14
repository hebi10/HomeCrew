import importlib.util
import pathlib
import tempfile
import unittest

SCRIPT = pathlib.Path(__file__).parents[1] / 'source.py'
spec = importlib.util.spec_from_file_location('source', SCRIPT)
source = importlib.util.module_from_spec(spec)
spec.loader.exec_module(source)
SHA = 'a' * 40
REPO = 'hebi10/HomeCrew'


class SourceTests(unittest.TestCase):
    def test_screenshot_qa_is_required_when_present_on_current_main(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            workflows = root / '.github/workflows'
            workflows.mkdir(parents=True)
            self.assertEqual(source.required_workflows(root), ['android.yml'])
            (workflows / 'screenshot-qa.yml').write_text('name: Screenshot QA\n')
            self.assertEqual(source.required_workflows(root), ['android.yml', 'screenshot-qa.yml'])

    def test_exact_source_screenshot_ci_rejects_pr_old_sha_and_incomplete_jobs(self):
        run = {'head_sha': SHA, 'head_branch': 'main', 'event': 'workflow_dispatch',
               'conclusion': 'success', 'path': '.github/workflows/screenshot-qa.yml'}
        jobs = [{'name': 'device-qa', 'conclusion': 'success'}]
        source.verify_ci(SHA, run, jobs, workflow='screenshot-qa.yml')
        for update in ({'event': 'pull_request'}, {'head_sha': 'b' * 40}, {'head_branch': 'feature'}):
            with self.assertRaises(source.SourceError):
                source.verify_ci(SHA, dict(run, **update), jobs, workflow='screenshot-qa.yml')
        for invalid in ([], [{'name': 'device-qa', 'conclusion': 'skipped'}]):
            with self.assertRaises(source.SourceError):
                source.verify_ci(SHA, run, invalid, workflow='screenshot-qa.yml')

    def test_initialize_mode_requires_explicit_manual_input_or_trusted_main_subject_marker(self):
        manual = {'GITHUB_REPOSITORY': REPO, 'GITHUB_EVENT_NAME': 'workflow_dispatch', 'GITHUB_REF': 'refs/heads/main', 'GITHUB_SHA': SHA}
        self.assertEqual(source.release_mode(dict(manual, PLAY_REQUESTED_MODE='initialize'), {}, 'ordinary'), 'initialize')
        self.assertEqual(source.release_mode(dict(manual, PLAY_REQUESTED_MODE='check'), {}, '[initialize-play]'), 'check')
        run = {'name': 'Android CI', 'path': '.github/workflows/android.yml', 'event': 'push', 'conclusion': 'success', 'head_branch': 'main', 'head_sha': SHA, 'head_repository': {'full_name': REPO}}
        env = {'GITHUB_REPOSITORY': REPO, 'GITHUB_EVENT_NAME': 'workflow_run'}
        self.assertEqual(source.release_mode(env, {'workflow_run': run}, 'Initial upload [initialize-play] (#20)'), 'initialize')
        self.assertEqual(source.release_mode(env, {'workflow_run': run}, 'CI-only preview workflow [skip-play] (#29)'), 'skip')
        self.assertEqual(source.release_mode(env, {'workflow_run': run}, 'CI-only [skip-play]\n[initialize-play]'), 'skip')
        self.assertEqual(source.release_mode(env, {'workflow_run': run}, '[skip-play]-unsafe'), 'deploy')
        self.assertEqual(source.release_mode(dict(manual, PLAY_REQUESTED_MODE='deploy'), {}, '[skip-play]'), 'deploy')
        for subject in ('ordinary', 'ordinary\n[initialize-play]', '[initialize-play]-example', 'x[initialize-play]'):
            self.assertEqual(source.release_mode(env, {'workflow_run': run}, subject), 'deploy')
        for key, value in (('event', 'pull_request'), ('head_branch', 'feature'), ('conclusion', 'failure'), ('head_repository', {'full_name': 'attacker/fork'})):
            with self.assertRaises(source.SourceError):
                source.release_mode(env, {'workflow_run': dict(run, **{key: value})}, '[initialize-play]')
        for ref in ('refs/tags/v1', 'refs/heads/feature'):
            with self.assertRaises(source.SourceError):
                source.release_mode(dict(manual, GITHUB_REF=ref, PLAY_REQUESTED_MODE='initialize'), {}, '[initialize-play]')

    def test_manual_dispatch_only_accepts_main(self):
        for ref in ('refs/heads/feature', 'refs/tags/v1'):
            with self.assertRaises(source.SourceError):
                source.resolve({'GITHUB_REPOSITORY': REPO, 'GITHUB_EVENT_NAME': 'workflow_dispatch', 'GITHUB_REF': ref, 'GITHUB_SHA': SHA}, {})
        self.assertEqual(source.resolve({'GITHUB_REPOSITORY': REPO, 'GITHUB_EVENT_NAME': 'workflow_dispatch', 'GITHUB_REF': 'refs/heads/main', 'GITHUB_SHA': SHA}, {}), SHA)

    def test_automatic_source_must_be_successful_same_repo_main_push(self):
        run = {'name': 'Android CI', 'path': '.github/workflows/android.yml', 'event': 'push', 'conclusion': 'success',
               'head_branch': 'main', 'head_sha': SHA, 'head_repository': {'full_name': REPO}}
        env = {'GITHUB_REPOSITORY': REPO, 'GITHUB_EVENT_NAME': 'workflow_run'}
        self.assertEqual(source.resolve(env, {'workflow_run': run}), SHA)
        for key, value in [('event', 'pull_request'), ('head_branch', 'feature'), ('conclusion', 'failure'), ('path', 'other.yml')]:
            invalid = dict(run, **{key: value})
            with self.assertRaises(source.SourceError):
                source.resolve(env, {'workflow_run': invalid})
        run['head_repository'] = {'full_name': 'attacker/fork'}
        with self.assertRaises(source.SourceError):
            source.resolve(env, {'workflow_run': run})

    def test_stale_main_source_fails_before_deployment(self):
        source.require_current_main(SHA, SHA)
        with self.assertRaisesRegex(source.SourceError, 'stale'):
            source.require_current_main(SHA, 'b' * 40)

    def test_deploy_requires_every_completed_verify_job_on_exact_sha(self):
        run = {'head_sha': SHA, 'head_branch': 'main', 'event': 'push', 'conclusion': 'success', 'path': '.github/workflows/android.yml'}
        jobs = [{'name': 'verify', 'conclusion': 'success'}, {'name': 'future-instrumentation', 'conclusion': 'success'}]
        source.verify_ci(SHA, run, jobs)
        source.verify_ci(SHA, run, jobs[:1])
        for invalid_jobs in ([], jobs[1:], [jobs[0], dict(jobs[1], conclusion='skipped')], [dict(jobs[0], conclusion='failure'), jobs[1]]):
            with self.assertRaises(source.SourceError):
                source.verify_ci(SHA, run, invalid_jobs)
        with self.assertRaises(source.SourceError):
            source.verify_ci('b' * 40, run, jobs)


if __name__ == '__main__':
    unittest.main()
