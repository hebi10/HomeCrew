#!/usr/bin/env python3
"""Verify one explicitly approved Firebase-02-A PR preview before trusted Play signing.

Only trusted main source executes this module. PR contents are data, never executed
in the job that receives the upload signer and Play OAuth token.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

REPO = "hebi10/HomeCrew"
PR_NUMBER = 28
VERSION_CODE = 14
UNSIGNED_WORKFLOW = ".github/workflows/firebase-02-a-signed-preview.yml"
UNSIGNED_NAME = "FIREBASE-02-A unsigned preview AAB (no publish)"
PR_BRANCH = "feat/firebase-02-a-google-sign-in"
REQUEST = re.compile(r"^/publish-homecrew-firebase02a-v14 ([0-9a-f]{40}) ([1-9][0-9]*)$")
REQUIRED_CHECKS = {
    "verify",
    "screenshots",
    "Exact-source release automation checks",
    "unsigned-preview",
    "device-matrix (29, pixel_6, 1.0, api29-phone)",
    "device-matrix (35, pixel_6, 1.0, api35-phone)",
    "device-matrix (36, pixel_6, 1.0, api36-phone)",
    "device-matrix (35, pixel_6, 1.6, api35-large-font)",
    "device-matrix (35, pixel_tablet, 1.0, api35-tablet)",
}


class PreviewError(RuntimeError):
    pass


def gh(path):
    result = subprocess.run(["gh", "api", path], capture_output=True, text=True, check=False)
    if result.returncode:
        raise PreviewError("Cannot verify trusted GitHub release metadata")
    try:
        return json.loads(result.stdout)
    except ValueError:
        raise PreviewError("Invalid trusted GitHub release metadata") from None


def require_approved_source(env, event, request=gh):
    if (env.get("GITHUB_REPOSITORY") != REPO
            or env.get("GITHUB_EVENT_NAME") != "issue_comment"
            or env.get("GITHUB_REF") != "refs/heads/main"):
        raise PreviewError("Only the trusted main issue-comment flow can publish PR28 preview")
    issue, comment = event.get("issue", {}), event.get("comment", {})
    if (issue.get("number") != PR_NUMBER or not issue.get("pull_request")
            or comment.get("user", {}).get("login") != "hebi10"):
        raise PreviewError("Only the repository owner can approve the HomeCrew PR28 preview")
    match = REQUEST.fullmatch(comment.get("body", ""))
    if not match:
        raise PreviewError("Preview approval must specify an immutable commit and run ID")
    sha, run_id = match.groups()

    current = request(f"repos/{REPO}/git/ref/heads/main")["object"]["sha"]
    if env.get("GITHUB_SHA") != current:
        raise PreviewError("Main moved after the approval request")
    pr = request(f"repos/{REPO}/pulls/{PR_NUMBER}")
    if (pr.get("state") != "open" or pr.get("merged")
            or pr.get("base", {}).get("ref") != "main"
            or pr.get("head", {}).get("ref") != PR_BRANCH
            or pr.get("head", {}).get("repo", {}).get("full_name") != REPO
            or pr.get("head", {}).get("sha") != sha):
        raise PreviewError("PR28 changed or is no longer an approved unmerged source")

    run = request(f"repos/{REPO}/actions/runs/{run_id}")
    if (run.get("name") != UNSIGNED_NAME
            or run.get("path") != UNSIGNED_WORKFLOW
            or run.get("event") != "pull_request"
            or run.get("status") != "completed"
            or run.get("conclusion") != "success"
            or run.get("head_sha") != sha
            or run.get("head_branch") != PR_BRANCH
            or run.get("head_repository", {}).get("full_name") != REPO):
        raise PreviewError("Unsigned artifact has not passed the approved PR28 workflow")

    checks_payload = request(f"repos/{REPO}/commits/{sha}/check-runs?per_page=100")
    checks = checks_payload.get("check_runs", [])
    if checks_payload.get("total_count", len(checks)) > len(checks):
        raise PreviewError("Required check results are incomplete")
    latest = {}
    for check in checks:
        name = check.get("name")
        if name in REQUIRED_CHECKS and check.get("head_sha") == sha:
            if name not in latest or (check.get("completed_at") or "") > (latest[name].get("completed_at") or ""):
                latest[name] = check
    if any(name not in latest or latest[name].get("status") != "completed"
            or latest[name].get("conclusion") != "success" for name in REQUIRED_CHECKS):
        raise PreviewError("Latest exact-commit Android, UI, device or unsigned build checks have not all passed")

    return {
        "source_sha": sha,
        "run_id": run_id,
        "artifact_name": f"homecrew-firebase02a-unsigned-v14-{sha}",
        "version_code": VERSION_CODE,
    }


def inspect_bundle(bundle, bundletool):
    if not bundle.is_file() or not bundle.stat().st_size:
        raise PreviewError("Expected nonempty unsigned AAB was not downloaded")
    result = subprocess.run(
        ["java", "-jar", str(bundletool), "dump", "manifest",
         "--bundle=" + str(bundle), "--module=base"],
        capture_output=True, text=True, check=False,
    )
    if result.returncode:
        raise PreviewError("Cannot inspect HomeCrew unsigned AAB manifest")
    try:
        root = ET.fromstring(result.stdout)
    except ET.ParseError:
        raise PreviewError("Invalid HomeCrew bundle manifest") from None
    android = "{http://schemas.android.com/apk/res/android}"
    expected = ("com.hebi.homecrew", "0.1.0", str(VERSION_CODE))
    actual = (root.get("package"), root.get(android + "versionName"), root.get(android + "versionCode"))
    if actual != expected:
        raise PreviewError("Preview AAB package or version does not match the approved release")
    return actual


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("preflight")
    inspect = sub.add_parser("inspect-bundle")
    inspect.add_argument("--bundle", type=Path, required=True)
    inspect.add_argument("--bundletool", type=Path, required=True)
    args = parser.parse_args()
    try:
        if args.command == "preflight":
            event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
            approved = require_approved_source(os.environ, event)
            with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as out:
                for key, value in approved.items():
                    out.write(f"{key}={value}\n")
            print("Verified PR28 owner approval, exact source, unsigned artifact and all QA checks")
        else:
            inspect_bundle(args.bundle, args.bundletool)
            print("Verified unmerged HomeCrew AAB package and version before Play signing")
        return 0
    except (PreviewError, OSError, ValueError, KeyError) as error:
        safe_error = str(error) if isinstance(error, PreviewError) else "Untrusted preview metadata or artifact is invalid"
        print("PR28 preview release blocked: " + safe_error, file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
