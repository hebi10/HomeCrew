"""Offline Play listing-only verification and no-accidental-release tests."""
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import listing_ko


class FakePlay:
    def __init__(self, ko):
        self.ko = dict(ko)
        self.en = {"language": "en-US", "title": "Other language", "shortDescription": "Keep", "fullDescription": "Keep", "video": "example"}
        self.calls = []

    def __call__(self, method, path, body=None, content_type=None):
        self.calls.append((method, path, body))
        if method == "POST" and path == "/edits":
            return {"id": "123"}
        if method == "DELETE":
            return {}
        if method == "GET" and path.endswith("/listings/ko-KR"):
            return dict(self.ko)
        if method == "GET" and path.endswith("/listings"):
            return {"listings": [dict(self.ko), dict(self.en)]}
        if method == "GET" and path.endswith("/bundles"):
            return {"bundles": [{"versionCode": 2}]}
        if method == "GET" and path.endswith("/apks"):
            return {"apks": []}
        if method == "GET" and path.endswith("/tracks"):
            return {"tracks": [{"track": "internal", "releases": [{"versionCodes": ["2"], "status": "completed"}]}]}
        if method == "PATCH" and path.endswith("/listings/ko-KR"):
            self.ko.update(body)
            return dict(self.ko)
        if method == "POST" and (path.endswith(":validate") or ":commit" in path):
            return {}
        raise AssertionError((method, path))


class ListingTests(unittest.TestCase):
    def test_real_copy_validates(self):
        data = listing_ko.load_listing()
        self.assertLessEqual(len(data["shortDescription"]), 80)
        self.assertIn("저장", data["fullDescription"])
        self.assertIn("동기화", data["fullDescription"])

    def test_stale_claim_rejected(self):
        data = listing_ko.load_listing()
        data["fullDescription"] = "프로세스 종료 후 초기화됩니다."
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "bad.json"
            path.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
            with self.assertRaises(ValueError):
                listing_ko.load_listing(path)

    def test_check_never_writes(self):
        target = listing_ko.load_listing()
        target["shortDescription"] = "old"
        fake = FakePlay(target)
        with patch.object(listing_ko, "HttpTransport", return_value=fake):
            result = listing_ko.run("check", token="test")
        self.assertEqual(result["status"], "read_only_listing_verified")
        self.assertFalse(any(m == "PATCH" or ":commit" in p for m, p, _ in fake.calls))

    def test_apply_preserves_title_video_other_languages_and_tracks(self):
        target = listing_ko.load_listing()
        target["shortDescription"] = "old"
        target["fullDescription"] = "old full"
        target["video"] = "example"
        fake = FakePlay(target)
        with patch.dict(os.environ, {"GITHUB_REF": "refs/heads/main"}), patch.object(listing_ko, "HttpTransport", return_value=fake):
            result = listing_ko.run("apply", token="test")
        self.assertEqual(result["status"], "api_committed_and_fresh_readback_verified")
        self.assertEqual(fake.ko["video"], "example")
        self.assertEqual(fake.en["shortDescription"], "Keep")
        self.assertEqual(len([1 for m, p, _ in fake.calls if m == "PATCH"]), 1)
        self.assertFalse(any(m == "PUT" for m, p, _ in fake.calls))
        self.assertTrue(any("changesInReviewBehavior=ERROR_IF_IN_REVIEW" in p
                            for m, p, _ in fake.calls if ":commit" in p))

    def test_idempotent_already_matched(self):
        fake = FakePlay(listing_ko.load_listing())
        with patch.dict(os.environ, {"GITHUB_REF": "refs/heads/main"}), patch.object(listing_ko, "HttpTransport", return_value=fake):
            self.assertEqual(listing_ko.run("apply", token="test")["status"], "already_matches_no_commit")
        self.assertFalse(any(":commit" in p for m, p, _ in fake.calls))

    def test_apply_requires_main(self):
        with patch.dict(os.environ, {"GITHUB_REF": "refs/heads/other"}):
            with self.assertRaises(listing_ko.ReleaseError):
                listing_ko.run("apply", token="test")


if __name__ == "__main__":
    unittest.main()
