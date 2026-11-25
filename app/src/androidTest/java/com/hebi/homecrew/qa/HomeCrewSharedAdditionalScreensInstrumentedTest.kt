package com.hebi.homecrew.qa

import androidx.activity.compose.setContent
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.hebi.homecrew.MainActivity
import com.hebi.homecrew.data.cloud.*
import com.hebi.homecrew.ui.screens.HomeCrewAccountDeletionReviewDialog
import com.hebi.homecrew.ui.screens.HomeCrewSharedChorePreviewDialog
import com.hebi.homecrew.ui.screens.HomeCrewSharedMemberManager
import com.hebi.homecrew.ui.screens.HomeCrewStagedMigrationManager
import com.hebi.homecrew.ui.screens.sharedChoreToday
import com.hebi.homecrew.ui.theme.HomeCrewTheme
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeCrewSharedAdditionalScreensInstrumentedTest {
    private val compose = createEmptyComposeRule()
    private val activityRule = ActivityScenarioRule(MainActivity::class.java)
    @get:Rule val rules: RuleChain = RuleChain.outerRule(compose).around(activityRule)
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private enum class Page { OWNER, MEMBER, CHORES, STAGE, DELETION }
    private val owner = RemoteHouseholdMember("qa-owner", true, "QA 소유자")
    private val member = RemoteHouseholdMember("qa-member", false, "QA 구성원")
    private val members = listOf(owner, member)
    private val house = "house-fixture-r14"

    private class ReadOnlyRemote(
        val snapshots: MutableStateFlow<RemoteSnapshot>,
        private val writes: AtomicInteger
    ) : RemoteHouseholdChoreGateway {
        private fun forbidden(): Nothing {
            writes.incrementAndGet()
            error("Fixture forbids writes")
        }
        override fun observe(householdId: String) = snapshots
        override suspend fun migrationStage(householdId: String) = RemoteMigrationStage(
            sessionId = "6dfdfbc0-3cc0-4db9-8cb3-1eb24cfc9c10", digest = "a".repeat(64),
            state = "PUBLISHING", nextIndex = 5, chunkCount = 5,
            receivedDefinitions = 20, definitionCount = 20,
            receivedOccurrences = 300, occurrenceCount = 300,
            publishIndex = 2, publishedDefinitions = 20, publishedOccurrences = 100
        )
        override suspend fun migrationReceipt(householdId: String, digest: String): RemoteMigrationReceipt? = null
        override suspend fun createChore(householdId: String, choreId: String, name: String, category: String,
            description: String, points: Int, assigneeUid: String): Long = forbidden()
        override suspend fun configureSchedule(householdId: String, choreId: String, expectedRevision: Long,
            schedule: RemoteSchedule): Long = forbidden()
        override suspend fun generateSchedule(householdId: String, choreId: String, expectedRevision: Long,
            fromEpochDay: Long, toEpochDay: Long): RemoteGenerationResult = forbidden()
        override suspend fun previewFutureSchedule(householdId: String, choreId: String,
            expectedRevision: Long): RemoteFutureSchedulePreview = error("Unexpected preview")
        override suspend fun applyFutureSchedule(householdId: String, choreId: String, expectedRevision: Long,
            previewDigest: String): RemoteFutureSchedulePreview = forbidden()
        override suspend fun importLocalManifest(manifest: LocalMigrationManifest): RemoteMigrationReceipt = forbidden()
        override suspend fun beginMigrationStage(upload: StagedMigrationUpload): RemoteMigrationStage = forbidden()
        override suspend fun appendMigrationChunk(householdId: String, sessionId: String,
            chunk: MigrationUploadChunk): RemoteMigrationStage = forbidden()
        override suspend fun sealMigrationStage(householdId: String, sessionId: String): RemoteMigrationStage = forbidden()
        override suspend fun publishMigrationChunk(householdId: String, stage: RemoteMigrationStage) { forbidden() }
        override suspend fun rollbackMigrationPublication(householdId: String, sessionId: String) { forbidden() }
        override suspend fun cancelMigrationStage(householdId: String, sessionId: String) { forbidden() }
        override suspend fun cleanupMigrationStage(householdId: String, sessionId: String) { forbidden() }
        override suspend fun recordCompletion(householdId: String, command: RemoteCompletionCommand): RemoteCompletionResult = forbidden()
    }

    private class ReadOnlyHouseholds(
        private val remote: ReadOnlyRemote,
        private val completion: HomeCrewCompletionJournal,
        private val migration: HomeCrewLocalMigrationJournal,
        private val members: List<RemoteHouseholdMember>,
        private val writes: AtomicInteger
    ) : HomeCrewSharedHouseholdGateway {
        @Volatile var preflightFails = false
        private fun forbidden(): Nothing {
            writes.incrementAndGet()
            error("Fixture forbids writes")
        }
        override fun choreGateway(): RemoteHouseholdChoreGateway = remote
        override fun completionJournal(householdId: String) = completion
        override fun migrationJournal(householdId: String) = migration
        override suspend fun roster(householdId: String) = SharedHouseholdRoster(members, "b".repeat(64), false)
        override suspend fun deletionPreflight(): AccountDeletionPreflight {
            check(!preflightFails)
            return AccountDeletionPreflight(
                listOf(AccountDeletionSpace("house-fixture-r14", "QA 우리 집", "OWNER"),
                    AccountDeletionSpace("house-other", "QA 참여 공간", "MEMBER")),
                ownerCount = 1, memberCount = 1, indexIssues = 1, lockedSpaces = 1,
                dataCategories = listOf("집안일", "완료 기록")
            )
        }
        override suspend fun list() = listOf(SharedHouseholdSummary("house-fixture-r14", "QA 우리 집", 2, true))
        override suspend fun create(request: SharedHouseholdCreateRequest): String = forbidden()
        override suspend fun invite(householdId: String): HomeCrewCloudInvite = forbidden()
        override suspend fun join(code: String): String = forbidden()
        override suspend fun renameMember(householdId: String, memberUid: String, displayName: String,
            expectedRosterDigest: String) { forbidden() }
        override suspend fun changeMembership(householdId: String, memberUid: String, action: SharedMembershipAction,
            expectedRosterDigest: String) { forbidden() }
    }

    private fun evidenceDirectory(): File {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.takeIf { it.isNotBlank() }?.let(::File)
            ?: requireNotNull(instrumentation.targetContext.externalMediaDirs.firstOrNull())
        return File(output, "screen-qa-fake-gateway").also { assertTrue(it.isDirectory || it.mkdirs()) }
    }

    private fun capture(name: String) {
        device.waitForIdle()
        Thread.sleep(250)
        assertTrue(device.takeScreenshot(File(evidenceDirectory(), "$name.png")))
    }

    private fun find(label: String, scroll: Boolean = false): UiObject2 {
        val selector = By.text(label)
        var node = device.wait(Until.findObject(selector), if (scroll) 1_000 else 10_000)
        if (scroll) {
            repeat(12) {
                if (node == null) {
                    device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
                        device.displayWidth / 2, device.displayHeight / 3, 18)
                    device.waitForIdle()
                    node = device.wait(Until.findObject(selector), 800)
                }
            }
        }
        if (node == null) {
            capture("error-shared-additional")
            device.dumpWindowHierarchy(File(evidenceDirectory(), "hierarchy-shared-additional.xml"))
        }
        return requireNotNull(node) { "Missing control: $label" }
    }

    private fun tap(label: String, scroll: Boolean = false) {
        val bounds = find(label, scroll).visibleBounds
        assertTrue(device.click(bounds.centerX(), bounds.centerY()))
        device.waitForIdle()
    }

    private fun assertMigrationReadEnabled(expected: Boolean) {
        val label = "로컬 자료 읽기"
        try {
            if (expected) {
                compose.waitUntil(timeoutMillis = 10_000) {
                    compose.onAllNodes(hasText(label) and isEnabled()).fetchSemanticsNodes().size == 1
                }
                compose.onNodeWithText(label).assertIsEnabled()
            } else {
                compose.onNodeWithText(label).assertIsNotEnabled()
            }
        } catch (failure: Throwable) {
            capture("error-migration-read-state")
            device.dumpWindowHierarchy(File(evidenceDirectory(), "hierarchy-migration-read-state.xml"))
            throw failure
        }
    }

    @Test
    fun captureRemainingSharedPagesWithoutWrites() {
        val writes = AtomicInteger()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val completion = HomeCrewCompletionJournal(object : CompletionJournalStorage {
            override fun read(key: String): String? = null
            override fun write(key: String, value: String?): Boolean {
                writes.incrementAndGet()
                error("Fixture forbids journal writes")
            }
        }, "qa-r14-fixtures", owner.uid, house)
        val migration = HomeCrewLocalMigrationJournal(context, "qa-r14-fixtures", owner.uid, house)
        val snapshot = RemoteSnapshot(
            listOf(RemoteChore("qa-chore", "QA 설거지", "주방", 2, owner.uid, 1)),
            listOf(RemoteOccurrence("qa-occurrence", "qa-chore", sharedChoreToday().toEpochDay(),
                owner.uid, false, null, 1)),
            false, members
        )
        val remote = ReadOnlyRemote(MutableStateFlow(snapshot), writes)
        val households = ReadOnlyHouseholds(remote, completion, migration, members, writes)
        val page = mutableStateOf(Page.OWNER)
        fun show(next: Page) {
            activityRule.scenario.onActivity { page.value = next }
            device.waitForIdle()
        }
        activityRule.scenario.onActivity { activity ->
            activity.setContent {
                HomeCrewTheme(darkTheme = false) {
                    key(page.value) {
                        when (page.value) {
                            Page.OWNER -> HomeCrewSharedMemberManager(households, house, "QA 우리 집", owner.uid) {}
                            Page.MEMBER -> HomeCrewSharedMemberManager(households, house, "QA 우리 집", member.uid) {}
                            Page.CHORES -> HomeCrewSharedChorePreviewDialog(remote, completion, migration, house, "QA 우리 집", owner.uid) {}
                            Page.STAGE -> HomeCrewStagedMigrationManager(remote, migration, house, "QA 우리 집") {}
                            Page.DELETION -> HomeCrewAccountDeletionReviewDialog(households) {}
                        }
                    }
                }
            }
        }
        device.dismissEmulatorLauncherAnrIfShown()
        find("구성원 2/8명")
        find("QA 소유자 (나)")
        find("소유권 이전", scroll = true)
        capture("P19-roster-fake")
        tap("소유권 이전")
        assertNotNull(device.wait(Until.findObject(By.textContains("초대 코드는 무효화됩니다")), 8_000))
        capture("P19-transfer-confirm-fake")
        tap("취소")
        assertEquals(0, writes.get())

        show(Page.MEMBER)
        find("QA 구성원 (나)", scroll = true)
        find("공동 공간 탈퇴", scroll = true)
        assertNull(device.findObject(By.text("소유권 이전")))
        assertNull(device.findObject(By.text("구성원 제거")))
        capture("P19-member-fake")

        show(Page.CHORES)
        find("실시간 연결")
        find("QA 설거지", scroll = true)
        capture("P20-shared-chore-fake")
        tap("로컬 자료 이전 준비", scroll = true)
        find("로컬 자료 이전")
        find("기존 공동 집안일이 있는 공간은 이전 대상에서 제외합니다.")
        assertMigrationReadEnabled(expected = false)
        capture("P21-migration-blocked-fake")
        remote.snapshots.value = snapshot.copy(chores = emptyList(), occurrences = emptyList())
        assertTrue(device.wait(Until.gone(By.text("기존 공동 집안일이 있는 공간은 이전 대상에서 제외합니다.")), 10_000))
        assertMigrationReadEnabled(expected = true)
        capture("P21-migration-ready-fake")
        tap("뒤로")
        find("실시간 연결")
        remote.snapshots.value = snapshot
        find("QA 설거지", scroll = true)
        remote.snapshots.value = snapshot.copy(mayBeStale = true)
        find("연결을 확인하고 있습니다. 서버에서 확인되지 않은 집안일은 표시하지 않습니다.")
        compose.onNodeWithText("QA 설거지").assertDoesNotExist()
        capture("P20-offline-fake")

        show(Page.STAGE)
        find("반영 중 · 조회 잠김")
        find("분할 저장")
        find("최종 반영")
        capture("P22-publishing-fake")
        tap("숨겨진 반영분 되돌리기", scroll = true)
        assertNotNull(device.wait(Until.findObject(By.textContains("로컬 원본은 삭제하지 않습니다")), 8_000))
        capture("P22-rollback-confirm-fake")
        tap("취소")
        assertEquals(0, writes.get())

        show(Page.DELETION)
        find("소유 1개 · 참여 1개")
        find("아직 삭제하지 않았습니다")
        capture("P25-deletion-review-fake")
        households.preflightFails = true
        tap("서버 상태 다시 확인", scroll = true)
        find("자료 범위를 확인하지 못했습니다. 계정 삭제는 차단됩니다.")
        assertNull(device.findObject(By.text("계정 삭제")))
        capture("P25-deletion-blocked-fake")
        assertEquals("No destructive or remote write call may occur", 0, writes.get())
    }
}
