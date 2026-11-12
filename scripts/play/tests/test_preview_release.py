"""Trusted-only preview release gates; no Play API requests or credentials."""
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest import mock

SCRIPT = Path(__file__).parents[1] / "preview_release.py"
spec = importlib.util.spec_from_file_location("preview_release", SCRIPT)
preview = importlib.util.module_from_spec(spec)
spec.loader.exec_module(preview)
SOURCE = "a" * 40
RUN_ID = "123456789"


def data():
    env = {
        "GITHUB_REPOSITORY": preview.REPO, "GITHUB_REF": "refs/heads/main",
        "GITHUB_EVENT_NAME": "issue_comment", "GITHUB_SHA": "b" * 40
    }
    event = {
        "issue": {"number": 28, "pull_request": {"url": "PR28"}},
        "comment": {"user": {"login": "hebi10"},
                    "body": f"/publish-homecrew-firebase02a-v14 {SOURCE} {RUN_ID}"}
    }
    pr = {
        "state": "open", "merged": False, "base": {"ref": "main"},
        "head": {"ref": preview.PR_BRANCH, "repo": {"full_name": preview.REPO}, "sha": SOURCE}
    }
    run = {
        "name": preview.UNSIGNED_NAME, "path": preview.UNSIGNED_WORKFLOW,
        "event": "pull_request", "status": "completed", "conclusion": "success",
        "head_sha": SOURCE, "head_branch": preview.PR_BRANCH,
        "head_repository": {"full_name": preview.REPO}
    }
    checks = [
        {"name": name, "head_sha": SOURCE, "status": "completed",
         "conclusion": "success", "completed_at": "2026-11-12T08:00:00Z"}
        for name in sorted(preview.REQUIRED_CHECKS)
    ]
    objects = {
        f"repos/{preview.REPO}/git/ref/heads/main": {"object": {"sha": "b" * 40}},
        f"repos/{preview.REPO}/pulls/28": pr,
        f"repos/{preview.REPO}/actions/runs/{RUN_ID}": run,
        f"repos/{preview.REPO}/commits/{SOURCE}/check-runs?per_page=100": {
            "total_count": len(checks), "check_runs": checks
        }
    }
    return env, event, objects


class PreviewApprovalTests(unittest.TestCase):
    def approve(self, env, event, objects):
        return preview.require_approved_source(env, event, objects.__getitem__)

    def test_only_owner_comment_exact_sha_and_successful_ci_can_approve(self):
        env, event, objects = data()
        result = self.approve(env, event, objects)
        self.assertEqual(result["source_sha"], SOURCE)
        self.assertEqual(result["run_id"], RUN_ID)
        self.assertEqual(result["artifact_name"], f"homecrew-firebase02a-unsigned-v14-{SOURCE}")
        self.assertEqual(result["version_code"], 14)

    def test_fork_actor_main_moved_and_changed_pr_are_blocked(self):
        for reason in ("attacker", "fork", "wrong_ref", "moved_main", "changed_head"):
            env, event, objects = data()
            if reason == "attacker":
                event["comment"]["user"]["login"] = "other"
            elif reason == "fork":
                objects[f"repos/{preview.REPO}/pulls/28"]["head"]["repo"]["full_name"] = "other/HomeCrew"
            elif reason == "wrong_ref":
                env["GITHUB_REF"] = "refs/pull/28/merge"
            elif reason == "moved_main":
                objects[f"repos/{preview.REPO}/git/ref/heads/main"]["object"]["sha"] = "c" * 40
            else:
                objects[f"repos/{preview.REPO}/pulls/28"]["head"]["sha"] = "c" * 40
            with self.subTest(reason=reason), self.assertRaises(preview.PreviewError):
                self.approve(env, event, objects)

    def test_invalid_command_failed_unsigned_run_and_failed_checks_are_blocked(self):
        for reason in ("command", "run_event", "run_result", "qa_failed", "missing_qa", "incomplete"):
            env, event, objects = data()
            run_key = f"repos/{preview.REPO}/actions/runs/{RUN_ID}"
            checks_key = f"repos/{preview.REPO}/commits/{SOURCE}/check-runs?per_page=100"
            if reason == "command":
                event["comment"]["body"] = "please deploy latest"
            elif reason == "run_event":
                objects[run_key]["event"] = "push"
            elif reason == "run_result":
                objects[run_key]["conclusion"] = "failure"
            elif reason == "qa_failed":
                objects[checks_key]["check_runs"][0]["conclusion"] = "failure"
            elif reason == "missing_qa":
                objects[checks_key]["check_runs"].pop()
                objects[checks_key]["total_count"] -= 1
            else:
                objects[checks_key]["total_count"] += 1
            with self.subTest(reason=reason), self.assertRaises(preview.PreviewError):
                self.approve(env, event, objects)

    def test_verify_preview_aab_manifest_version_and_package(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "test.aab"
            path.write_bytes(b"unsigned-bundle-fixture")
            tool = Path(directory) / "bundletool.jar"
            manifest = (
                '<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
                'package="com.hebi.homecrew" android:versionName="0.1.0" android:versionCode="14"/>'
            )
            with mock.patch.object(preview.subprocess, "run",
                    return_value=subprocess.CompletedProcess([], 0, stdout=manifest)):
                self.assertEqual(preview.inspect_bundle(path, tool)[2], "14")
            with mock.patch.object(preview.subprocess, "run",
                    return_value=subprocess.CompletedProcess([], 0, stdout=manifest.replace('versionCode="14"', 'versionCode="13"'))):
                with self.assertRaises(preview.PreviewError):
                    preview.inspect_bundle(path, tool)


    def test_preview_signing_requires_trusted_main_environment_and_owner_comment(self):
        workflow = (Path(__file__).parents[3] / ".github/workflows/play-internal.yml").read_text(encoding="utf-8")
        self.assertIn("  issue_comment:\\n    types: [created]", workflow)
        preview_job = workflow.split("  firebase02a-approved-preview:", 1)[1]
        self.assertIn("environment: google-play", preview_job)
        self.assertIn("github.event.comment.user.login == 'hebi10'", preview_job)
        self.assertIn("github.event.issue.number == 28", preview_job)
        self.assertIn("scripts/play/preview_release.py preflight", preview_job)
        self.assertIn("gh run download", preview_job)
        self.assertIn("ref: ${{ github.sha }}", preview_job)
        self.assertIn("source_sha", preview_job)
        self.assertIn("scripts/play/publisher.py --report output/preview-release.json deploy", preview_job)
        self.assertNotIn("./gradlew", preview_job)
        self.assertNotIn("ref: ${{ github.event.pull_request.head.sha }}", preview_job)


if __name__ == "__main__":
    unittest.main()
