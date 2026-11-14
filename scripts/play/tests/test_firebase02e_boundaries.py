"""Offline FIREBASE-02-E auth/local-data and release-boundary regression checks."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]
AUTH = ROOT / "app/src/firebaseAuth/java/com/hebi/homecrew/data/auth/GoogleSignInProvider.kt"
ACCOUNT = ROOT / "app/src/main/java/com/hebi/homecrew/ui/screens/HomeCrewGoogleAccountSetting.kt"
LINKS = ROOT / "app/src/main/java/com/hebi/homecrew/ui/screens/HomeCrewPrivacyLinks.kt"


class Firebase02EReleaseBoundaryTests(unittest.TestCase):
    def test_firebase_auth_provider_does_not_read_or_mutate_household_data(self):
        source = AUTH.read_text(encoding="utf-8")
        self.assertIn("auth.signOut()", source)
        self.assertIn("user.delete().await()", source)
        self.assertIn("user.reauthenticate(", source)
        for forbidden in (
            "HomeCrewDatabase", "HomeCrewRoomRepository",
            "LocalHouseholdStore", "SharedPreferences",
            "FirebaseFirestore", "setDoc(", "addDocument(",
        ):
            with self.subTest(forbidden=forbidden):
                self.assertNotIn(forbidden, source)

    def test_account_ui_does_not_trigger_server_or_local_deletion_on_creation(self):
        source = ACCOUNT.read_text(encoding="utf-8")
        self.assertIn('onDeleteAccount = { confirmingDeletion = true }', source)
        self.assertIn('runAccountAction(HomeCrewAccountAction.DELETE_ACCOUNT)', source)
        self.assertIn("if (confirmingDeletion && session is HomeCrewAuthSession.SignedIn)", source)
        self.assertNotIn("HomeCrewDatabase", source)
        self.assertNotIn("LocalHouseholdStore", source)

    def test_web_urls_have_staged_documents_and_are_not_auto_published(self):
        links = LINKS.read_text(encoding="utf-8")
        workflow = (ROOT / ".github/workflows/privacy-pages.yml").read_text(encoding="utf-8")
        for name in ("privacy-policy.html", "delete-account.html"):
            with self.subTest(document=name):
                self.assertTrue((ROOT / "docs" / name).is_file())
                self.assertIn("https://hebi10.github.io/HomeCrew/" + name, links)
        self.assertIn("workflow_dispatch:", workflow)
        self.assertNotIn("\n  push:", workflow)
        self.assertIn("HOMECREW_PRIVACY_PAGES_APPROVED", workflow)

    def test_local_only_app_build_runs_without_firebase_secret(self):
        workflow = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")
        self.assertIn("  local-only:", workflow)
        self.assertIn("Assert local-only build has no Firebase configuration", workflow)
        self.assertIn("test ! -f app/google-services.json", workflow)
        self.assertIn("testDebugUnitTest lintDebug assembleDebug", workflow)

    def test_google_play_mutation_remains_manual_after_auth_qa(self):
        workflow = (ROOT / ".github/workflows/play-internal.yml").read_text(encoding="utf-8")
        self.assertIn("workflow_dispatch:", workflow)
        self.assertNotIn("\n  workflow_run:", workflow)
        self.assertNotIn("\n  issue_comment:", workflow)
        self.assertIn("HOMECREW_FINAL_RELEASE_APPROVED", workflow)
        self.assertIn("inputs.final_release_approval", workflow)


if __name__ == "__main__":
    unittest.main()
