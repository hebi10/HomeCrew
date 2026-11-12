"""Firebase CI config validation without using real credentials or network."""
import base64
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock


ROOT = Path(__file__).parents[3]
SCRIPT = ROOT / "scripts" / "configure_firebase.py"
spec = importlib.util.spec_from_file_location("homecrew_configure_firebase", SCRIPT)
configure_firebase = importlib.util.module_from_spec(spec)
spec.loader.exec_module(configure_firebase)


def sample_config():
    return {
        "project_info": {"project_id": "homecrew-1b14b", "project_number": "123456"},
        "client": [{
            "client_info": {
                "android_client_info": {"package_name": "com.hebi.homecrew"},
                "mobilesdk_app_id": "1:123456:android:fake-test-id"
            },
            "api_key": [{"current_key": "FAKE_TEST_VALUE"}]
        }]
    }


class FirebaseConfigTests(unittest.TestCase):
    def run_installer(self, encoded, required=True):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "app").mkdir()
            arguments = ["configure_firebase.py"] + (["--required"] if required else [])
            env = {"HOMECREW_GOOGLE_SERVICES_JSON_BASE64": encoded}
            stdout, stderr = io.StringIO(), io.StringIO()
            with mock.patch.object(configure_firebase, "__file__", str(root / "scripts" / "configure_firebase.py")), \
                    mock.patch.dict(os.environ, env), \
                    mock.patch.object(sys, "argv", arguments), \
                    contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
                try:
                    configure_firebase.main()
                    exit_code = 0
                except SystemExit as error:
                    exit_code = error.code
            destination = root / "app" / "google-services.json"
            value = destination.read_bytes() if destination.exists() else None
            return exit_code, value, stdout.getvalue() + stderr.getvalue()

    def test_valid_registered_app_installed_without_printing_config(self):
        raw = json.dumps(sample_config(), ensure_ascii=False).encode("utf-8")
        result, written, messages = self.run_installer(base64.b64encode(raw).decode("ascii"))
        self.assertEqual(result, 0)
        self.assertEqual(written, raw)
        self.assertNotIn("FAKE_TEST_VALUE", messages)

    def test_required_missing_secret_fails_without_creating_file(self):
        result, written, _ = self.run_installer("")
        self.assertEqual(result, 1)
        self.assertIsNone(written)

    def test_local_optional_mode_still_supported(self):
        result, written, _ = self.run_installer("", required=False)
        self.assertEqual(result, 0)
        self.assertIsNone(written)

    def test_invalid_base64_and_other_app_are_rejected(self):
        bad = sample_config()
        bad["client"][0]["client_info"]["android_client_info"]["package_name"] = "com.other.app"
        configs = [
            "invalid:base64",
            base64.b64encode(json.dumps(bad).encode()).decode("ascii"),
        ]
        for encoded in configs:
            with self.subTest(encoded=encoded[:10]):
                result, written, messages = self.run_installer(encoded)
                self.assertEqual(result, 1)
                self.assertIsNone(written)
                self.assertNotIn("FAKE_TEST_VALUE", messages)

    def test_wrong_firebase_project_rejected(self):
        bad = sample_config()
        bad["project_info"]["project_id"] = "not-homecrew"
        result, written, _ = self.run_installer(
            base64.b64encode(json.dumps(bad).encode()).decode("ascii"))
        self.assertEqual(result, 1)
        self.assertIsNone(written)

    def test_all_trusted_build_workflows_require_firebase_configuration(self):
        paths = [
            ROOT / ".github/workflows/android.yml",
            ROOT / ".github/workflows/screenshot-qa.yml",
            ROOT / ".github/workflows/ui-r09-device-matrix.yml",
            ROOT / ".github/workflows/play-internal.yml",
        ]
        for workflow in paths:
            with self.subTest(workflow=workflow.name):
                text = workflow.read_text(encoding="utf-8")
                self.assertIn("HOMECREW_GOOGLE_SERVICES_JSON_BASE64", text)
                self.assertIn("scripts/configure_firebase.py", text)
                self.assertIn("--required", text)


if __name__ == "__main__":
    unittest.main()
