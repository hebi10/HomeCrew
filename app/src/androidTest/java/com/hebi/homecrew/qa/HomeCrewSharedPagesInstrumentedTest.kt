package com.hebi.homecrew.qa

import androidx.activity.compose.setContent
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.hebi.homecrew.MainActivity
import com.hebi.homecrew.data.cloud.*
import com.hebi.homecrew.ui.screens.HomeCrewSharedHouseholdsDialog
import com.hebi.homecrew.ui.theme.HomeCrewTheme
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeCrewSharedPagesInstrumentedTest {
    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private class FakeGateway : HomeCrewSharedHouseholdGateway {
        private val homes = CopyOnWriteArrayList(listOf(
            SharedHouseholdSummary("house-owner", "QA 소유 공간", 2, true),
            SharedHouseholdSummary("house-member", "QA 참여 공간", 2, false)
        ))
        val calls = CopyOnWriteArrayList<String>()
        override suspend fun list() = homes.toList()
        override suspend fun create(request: SharedHouseholdCreateRequest): String {
            calls.add("create:${request.name}")
            homes.add(SharedHouseholdSummary("house-created", request.name, 1, true))
            return "house-created"
        }
        override suspend fun invite(householdId: String): HomeCrewCloudInvite {
            check(householdId == "house-owner")
            calls.add("invite:$householdId")
            return HomeCrewCloudInvite("0123456789ABCDEF", householdId, System.currentTimeMillis() + 3_600_000L)
        }
        override suspend fun join(code: String): String {
            check(code == "FEDCBA9876543210")
            calls.add("join")
            homes.add(SharedHouseholdSummary("house-joined", "QA 새 참여 공간", 2, false))
            return "house-joined"
        }
        override fun choreGateway(): RemoteHouseholdChoreGateway = error("Unexpected chore gateway")
        override fun completionJournal(householdId: String): HomeCrewCompletionJournal = error("Unexpected journal")
        override fun migrationJournal(householdId: String): HomeCrewLocalMigrationJournal = error("Unexpected journal")
        override suspend fun deletionPreflight(): AccountDeletionPreflight = error("Unexpected deletion")
        override suspend fun roster(householdId: String): SharedHouseholdRoster = error("Unexpected roster")
        override suspend fun renameMember(householdId: String, memberUid: String, displayName: String, expectedRosterDigest: String) {
            error("Unexpected rename")
        }
        override suspend fun changeMembership(householdId: String, memberUid: String, action: SharedMembershipAction, expectedRosterDigest: String) {
            error("Unexpected membership change")
        }
    }

    private fun await(label: String) {
        assertNotNull("Missing text: $label", device.wait(Until.findObject(By.text(label)), 10_000))
    }
    private fun tap(label: String) {
        val node = device.wait(Until.findObject(By.text(label)), 10_000)
            ?: device.wait(Until.findObject(By.desc(label)), 2_000)
        assertNotNull("Missing control: $label", node)
        val bounds = requireNotNull(node).visibleBounds
        assertTrue(device.click(bounds.centerX(), bounds.centerY()))
        device.waitForIdle()
    }
    private fun input(value: String) {
        val field = requireNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 8_000))
        field.click()
        field.text = value
        device.pressBack()
        device.waitForIdle()
    }
    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.takeIf { it.isNotBlank() }?.let(::File)
            ?: requireNotNull(instrumentation.targetContext.externalMediaDirs.firstOrNull())
        val directory = File(output, "screen-qa-fake-gateway")
        assertTrue(directory.isDirectory || directory.mkdirs())
        device.waitForIdle()
        assertTrue(device.takeScreenshot(File(directory, "$name.png")))
    }

    @Test
    fun sharedSpaceNavigationAndOwnerGate() {
        val gateway = FakeGateway()
        activityRule.scenario.onActivity { activity ->
            activity.setContent {
                HomeCrewTheme(darkTheme = false) {
                    HomeCrewSharedHouseholdsDialog(gateway, "qa-owner") {}
                }
            }
        }
        await("공동 공간 관리")
        await("QA 소유 공간")
        capture("P17-shared-list-fake")
        assertNull(device.findObject(By.text("공동 공간 이름")))
        tap("공동 공간: QA 참여 공간")
        await("공동 공간 설정")
        await("구성원 관리")
        assertNull(device.findObject(By.text("가족 초대")))
        assertNull(device.findObject(By.text("대용량 이전 관리")))
        tap("뒤로")
        tap("공동 공간: QA 소유 공간")
        await("가족 초대")
        await("대용량 이전 관리")
        capture("P20-owner-settings-fake")
        tap("가족 초대")
        await("공동 공간 초대·참여")
        tap("가족 초대 코드 발급")
        await("0123456789ABCDEF")
        capture("P18-invite-fake")
        assertEquals(listOf("invite:house-owner"), gateway.calls.toList())
        tap("뒤로")
        await("공동 공간 설정")
        tap("뒤로")
        await("공동 공간 관리")
        assertNull(device.findObject(By.text("0123456789ABCDEF")))
        tap("공동 공간 만들기")
        await("새 공동 공간")
        assertNull(device.findObject(By.text("QA 소유 공간")))
        input("QA 생성 공간")
        tap("공동 공간 만들기")
        await("QA 생성 공간")
        tap("초대 코드로 참여하기")
        await("공동 공간 초대·참여")
        assertNull(device.findObject(By.text("가족 초대 코드 발급")))
        input("FEDCBA9876543210")
        tap("초대 수락")
        await("QA 새 참여 공간")
        assertEquals(listOf("invite:house-owner", "create:QA 생성 공간", "join"), gateway.calls.toList())
    }
}
