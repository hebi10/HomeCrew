from pathlib import Path
import unittest

ROOT = Path(__file__).parents[1]


class ReleaseWorkflowTests(unittest.TestCase):
    def test_internal_release_preserves_current_toolchain_and_rejects_unsigned_outputs(self):
        path = ROOT / '.github/workflows/play-internal.yml'
        self.assertTrue(path.exists(), 'Internal workflow is required')
        workflow = path.read_text()
        for expected in ('workflow_dispatch:', 'final_release_approval:', 'HOMECREW_FINAL_RELEASE_APPROVED',
                         'Require full-project release authorization before any Play mutation',
                         'group: play-com-hebi-homecrew',
                         'source_sha: ${{ needs.source.outputs.sha }}', 'environment: google-play',
                         "gradle-version: '8.13'", 'platforms;android-36', 'build-tools;35.0.0',
                         'artifacts.py --bundle', 'preflight.py verify-bundle', 'release_signing.py verify-apk',
                         'verify_native.py --bundle', '--assert-current', 'if: always()',
                         'rm -f -- "$RUNNER_TEMP/homecrew-release.jks"'):
            self.assertIn(expected, workflow)
        triggers = workflow.split('on:', 1)[1].split('\npermissions:', 1)[0]
        self.assertNotIn('workflow_run:', triggers)
        self.assertNotIn('issue_comment:', triggers)
        self.assertNotIn('push:', triggers)
        self.assertNotIn('firebase02a-approved-preview:', workflow)
        self.assertIn("if: github.event_name == 'workflow_dispatch'", workflow)
        self.assertNotIn('PLAY_SERVICE_ACCOUNT_JSON', workflow)
        self.assertNotIn('tracks/alpha', workflow)
        self.assertFalse((ROOT / '.github/workflows/play-promote.yml').exists())
        self.assertLess(workflow.index('Reject stale source immediately before upload'),
                        workflow.index('Submit first internal draft'))

    def test_gradle_release_tasks_require_provisioned_signer_without_affecting_debug(self):
        gradle = (ROOT / 'app/build.gradle.kts').read_text()
        self.assertIn('versionCode = releaseVersionCode', gradle)
        self.assertIn('if (name == "preReleaseBuild") dependsOn(verifyReleaseSigning)', gradle)
        self.assertIn('"verify-keystore"', gradle)
        self.assertNotIn('signingConfigs.getByName("debug")', gradle)
        self.assertIn('compileSdk = 36', gradle)


if __name__ == '__main__':
    unittest.main()
