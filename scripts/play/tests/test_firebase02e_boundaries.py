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
        # Clicking the entry only opens a review or a confirmation; it never
        # directly dispatches an account deletion.
        entry = source.split("onDeleteAccount = {", 1)[1].split("onOpenDeletionWeb =", 1)[0]
        self.assertIn("if (cloudPreviewConfigured) showDeletionReview = true", entry)
        self.assertIn("else confirmingDeletionUid = accountUid", entry)
        self.assertNotIn("runAccountAction(", entry)
        self.assertEqual(source.count("runAccountAction(HomeCrewAccountAction.DELETE_ACCOUNT)"), 1)
        self.assertIn("confirmingDeletionUid == accountUid", source)
        self.assertIn("!cloudPreviewConfigured && session is HomeCrewAuthSession.SignedIn", source)
        confirmation = source.split('title = { Text("Firebase 로그인 계정을 삭제할까요?") }', 1)[1]
        self.assertIn("confirmButton = {", confirmation)
        self.assertIn("if (confirmedUid != null && confirmedUid == accountUid)", confirmation)
        self.assertIn("runAccountAction(HomeCrewAccountAction.DELETE_ACCOUNT)", confirmation)

    def test_shared_preview_blocks_auth_only_deletion_before_provider_mutation(self):
        source = AUTH.read_text(encoding="utf-8")
        deletion = source.split("override suspend fun deleteAccount(", 1)[1]
        guard = "if (activity.resources.getBoolean(com.hebi.homecrew.R.bool.homecrew_shared_households_preview))"
        self.assertIn(guard, deletion)
        self.assertIn("return HomeCrewAccountActionResult.CLOUD_CLEANUP_REQUIRED", deletion)
        self.assertLess(deletion.index(guard), deletion.index("user.delete().await()"))
        review = (ACCOUNT.parent / "HomeCrewAccountDeletionReviewDialog.kt").read_text(encoding="utf-8")
        self.assertIn("gateway.deletionPreflight()", review)
        self.assertNotIn("deleteAccount(", review)
        self.assertNotIn("runAccountAction(", review)
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
