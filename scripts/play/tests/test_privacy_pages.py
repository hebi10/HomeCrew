"""Offline regression tests for HomeCrew's account deletion web publishing gate."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]


class HomeCrewPrivacyPagesPolicyTests(unittest.TestCase):
    def test_privacy_pages_require_explicit_manual_approval(self):
        workflow = (ROOT / ".github/workflows/privacy-pages.yml").read_text(encoding="utf-8")
        self.assertIn("workflow_dispatch:", workflow)
        self.assertNotIn("\n  push:", workflow)
        self.assertIn("inputs.publish_approval == 'HOMECREW_PRIVACY_PAGES_APPROVED'", workflow)
        self.assertIn("cp docs/privacy-policy.html _site/privacy-policy.html", workflow)
        self.assertIn("cp docs/delete-account.html _site/delete-account.html", workflow)

    def test_account_deletion_page_is_actionable_and_limits_claims(self):
        deletion = (ROOT / "docs/delete-account.html").read_text(encoding="utf-8")
        policy = (ROOT / "docs/privacy-policy.html").read_text(encoding="utf-8")
        self.assertIn('mailto:asdlkj0104@gmail.com?subject=', deletion)
        self.assertIn("HomeCrew", deletion)
        self.assertIn("Firebase Authentication", deletion)
        self.assertIn("Firebase Authentication", policy)
        self.assertIn('href="delete-account.html"', policy)
        self.assertNotIn("로그인 기능이 없습니다", policy)


if __name__ == "__main__":
    unittest.main()
