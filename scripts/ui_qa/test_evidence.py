import json
import struct
import tempfile
import unittest
from pathlib import Path

from evidence import collect


SHA = "a" * 40


def sample_png(path):
    path.parent.mkdir(parents=True, exist_ok=True)
    # A minimal IHDR header for testing the collector's header/size classification.
    path.write_bytes(b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR" + struct.pack(">II", 1080, 2400))


class EvidenceTest(unittest.TestCase):
    def test_classification_preserves_fixture_and_diagnostic_separation(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source, output = root / "retained", root / "qa"
            for name in ["01-home-light.png", "error-home.png", "invalid.png"]:
                sample_png(source / "device" / "screen-qa" / name)
            (source / "device" / "screen-qa" / "invalid.png").write_bytes(b"broken")
            sample_png(source / "device" / "screen-qa-fake-gateway" / "P17-shared-list-fake.png")
            sample_png(source / "device" / "screen-qa-fake-gateway" / "P20-owner-settings-fake.png")
            sample_png(source / "unknown" / "other.png")
            report = collect(source, output, SHA, "api35-phone", "1.0", 0)
            self.assertEqual(report["app_capture_count"], 1)
            self.assertEqual(report["fixture_capture_count"], 2)
            self.assertEqual(report["diagnostic_count"], 2)
            self.assertEqual(report["unclassified_count"], 1)
            self.assertEqual(len(report["pages"]), 27)
            by_page = {p["id"]: p for p in report["pages"]}
            self.assertEqual(by_page["P05"]["status"], "app_capture")
            self.assertEqual(by_page["P17"]["status"], "fixture_only")
            self.assertEqual(by_page["P20"]["status"], "not_captured")
            self.assertEqual(len(by_page["P20"]["related_only"]), 1)
            self.assertEqual(by_page["P05"]["visual_review"], "pending")
            self.assertTrue((output / "01-home-light.png").is_file())
            self.assertTrue((output / "fixture" / "P17-shared-list-fake.png").is_file())
            self.assertEqual(json.loads((output / "manifest.json").read_text())["source_sha"], SHA)

    def test_remaining_cloud_pages_are_fixture_only(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "retained"
            for stem in ["P19-roster-fake", "P20-shared-chore-fake",
                         "P21-migration-blocked-fake", "P22-publishing-fake",
                         "P25-deletion-review-fake"]:
                sample_png(source / "screen-qa-fake-gateway" / (stem + ".png"))
            report = collect(source, root / "qa", SHA, "api35-phone", "1.0", 0)
            self.assertEqual(report["app_capture_count"], 0)
            self.assertEqual(report["fixture_capture_count"], 5)
            for page in report["pages"]:
                if page["id"] in {"P19", "P20", "P21", "P22", "P25"}:
                    self.assertEqual(page["status"], "fixture_only")
                    self.assertTrue(page["requires_nonproduction_server_qa"])
                    self.assertEqual(page["visual_review"], "pending")

    def test_failed_run_does_not_certify_capture(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            sample_png(root / "retained" / "screen-qa" / "01-home-light.png")
            report = collect(root / "retained", root / "qa", SHA, "api35-phone", "1.0", 1)
            self.assertEqual(report["app_capture_count"], 0)
            self.assertEqual(report["diagnostic_count"], 1)
            self.assertFalse(report["instrumentation_passed"])

    def test_duplicates_are_preserved_and_previous_output_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for device in ["one", "two"]:
                sample_png(root / "retained" / device / "screen-qa" / "01-home-light.png")
            report = collect(root / "retained", root / "qa", SHA, "api35-phone", "1.0", 0)
            self.assertEqual(report["app_capture_count"], 2)
            self.assertEqual(len(list((root / "qa").glob("*.png"))), 2)
            with self.assertRaises(ValueError):
                collect(root / "retained", root / "qa", SHA, "api35-phone", "1.0", 0)


if __name__ == "__main__":
    unittest.main()
