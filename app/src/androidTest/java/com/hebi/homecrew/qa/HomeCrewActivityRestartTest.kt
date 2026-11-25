package com.hebi.homecrew.qa

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.hebi.homecrew.MainActivity
import com.hebi.homecrew.data.local.HomeCrewDatabase
import com.hebi.homecrew.data.local.HomeCrewRoomRepository
import com.hebi.homecrew.ui.model.ChoreStatus
import com.hebi.homecrew.ui.screens.ChoreAssignment
import com.hebi.homecrew.ui.screens.ChoreEditorDraft
import com.hebi.homecrew.ui.screens.ChoreRepeat
import com.hebi.homecrew.ui.screens.ChoreRepeatMode
import com.hebi.homecrew.ui.setup.HouseholdSetup
import com.hebi.homecrew.ui.setup.LocalHouseholdStore
import com.hebi.homecrew.ui.setup.suggestedTemplateIds
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeCrewActivityRestartTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private fun taskVisibleAfterActivityStart(
        taskName: String,
        householdName: String,
        expectedStatus: ChoreStatus
    ) {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            // The Compose test clock must advance during initial Room recovery and
            // ViewModel configuration; use the actual saved household name.
            awaitDescription("화면 제목: $householdName")
            awaitDescription("집안일 탭")
            compose.onNodeWithContentDescription("집안일 탭")
                .assertIsEnabled().performClick()
            awaitDescription("화면 제목: 집안일")
            compose.waitUntil(timeoutMillis = 15_000) {
                compose.onAllNodesWithTag("homecrew-chores-list")
                    .fetchSemanticsNodes().size == 1
            }

            val description = "집안일 상세: $taskName"
            compose.onNodeWithTag("homecrew-chores-list")
                .performScrollToNode(hasContentDescription(description))
            compose.onNodeWithContentDescription(description)
                .assertIsDisplayed()
                .assertTextContains(taskName)
                .assertTextContains(expectedStatus.label)
        } catch (error: Throwable) {
            captureSettingsDiagnostic("error-restart-task-" + expectedStatus.name.lowercase())
            throw error
        } finally {
            scenario.close()
        }
    }

    private fun waitForKeyboardHidden(scenario: ActivityScenario<MainActivity>) {
        val deadline = SystemClock.elapsedRealtime() + 8_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var hidden = false
            scenario.onActivity { activity ->
                hidden = ViewCompat.getRootWindowInsets(activity.window.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) == false
            }
            if (hidden) return
            SystemClock.sleep(100)
        }
        captureSettingsDiagnostic("error-settings-ime-not-hidden")
        throw AssertionError("키보드가 닫히지 않았습니다.")
    }

    private fun awaitDescription(description: String) {
        try {
            compose.waitUntil(timeoutMillis = 30_000) {
                device.dismissEmulatorLauncherAnrIfShown()
                compose.onAllNodesWithContentDescription(description)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription(description).assertIsDisplayed()
        } catch (error: Throwable) {
            captureSettingsDiagnostic("error-await-screen-" + description.hashCode())
            throw error
        }
    }

    private fun awaitSettings() {
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithTag("homecrew-settings-list")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun settingsControl(tag: String): androidx.compose.ui.test.SemanticsNodeInteraction {
        compose.onNodeWithTag("homecrew-settings-list").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
    }

    private fun captureSettingsDiagnostic(name: String) {
        try {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val base = InstrumentationRegistry.getArguments()
                .getString("additionalTestOutputDir")
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
                ?: requireNotNull(context.externalMediaDirs.firstOrNull())
            val output = File(base, "screen-qa").apply { mkdirs() }
            device.takeScreenshot(File(output, "$name.png"))
            device.dumpWindowHierarchy(File(output, "$name.xml"))
        } catch (_: Exception) {
        }
    }

    @Test
    fun householdSettingsPersistAfterActivityRestart() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = LocalHouseholdStore(context)
        val original = store.load()
        val seeded = original ?: HouseholdSetup(
            homeName = "우리 집",
            members = listOf("나", "지민"),
            defaultMember = "나",
            selectedTemplateIds = suggestedTemplateIds
        )
        val base = if (seeded.members.size == 1) {
            seeded.copy(members = seeded.members + "QA보조")
        } else seeded
        assertTrue(store.save(base))
        val unique = System.currentTimeMillis().toString().takeLast(7)
        val homeName = "QA집" + unique
        val member = "QA" + unique
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            awaitDescription("화면 제목: ${base.homeName}")
            compose.onNodeWithContentDescription("집 및 앱 설정").assertIsDisplayed().performClick()
            awaitSettings()
            captureSettingsDiagnostic("P27-settings-hub")
            settingsControl("homecrew-settings-household").performClick()
            awaitDescription("화면 제목: 집 설정")
            settingsControl("homecrew-home-name").performTextReplacement(homeName)
            settingsControl("homecrew-save-home").assertIsEnabled().performClick()
            var homeNameSaved = store.load()?.homeName == homeName
            repeat(15) {
                if (!homeNameSaved) {
                    Thread.sleep(200)
                    homeNameSaved = store.load()?.homeName == homeName
                }
            }
            if (!homeNameSaved) captureSettingsDiagnostic("error-settings-home-name-not-saved")
            assertTrue("집 이름이 저장되지 않았습니다.", homeNameSaved)
            settingsControl("homecrew-settings-back").performClick()
            awaitDescription("화면 제목: 설정")
            settingsControl("homecrew-settings-members").performClick()
            awaitDescription("화면 제목: 구성원 관리")
            val newDefault = base.members.first { it != base.defaultMember }
            settingsControl("homecrew-default-member-$newDefault").assertIsEnabled().performClick()
            assertTrue("기본 구성원 변경이 저장되지 않았습니다.", store.load()?.defaultMember == newDefault)
            settingsControl("homecrew-member-input").performTextReplacement(member)
            settingsControl("homecrew-member-input").assertTextContains(member)
            captureSettingsDiagnostic("13-settings-new-member-input")
            scenario.onActivity { activity ->
                WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                    .hide(WindowInsetsCompat.Type.ime())
            }
            waitForKeyboardHidden(scenario)
            settingsControl("homecrew-member-input").assertTextContains(member)
            captureSettingsDiagnostic("13-settings-member-keyboard-dismissed")
            settingsControl("homecrew-member-add").assertIsEnabled().performClick()
            var memberSaved = member in (store.load()?.members ?: emptyList())
            repeat(15) {
                if (!memberSaved) {
                    Thread.sleep(200)
                    memberSaved = member in (store.load()?.members ?: emptyList())
                }
            }
            if (!memberSaved) {
                captureSettingsDiagnostic("error-settings-member-not-saved")
            }
            assertTrue("구성원 추가가 저장되지 않았습니다.", memberSaved)
            settingsControl("homecrew-settings-back").performClick()
            awaitDescription("화면 제목: 설정")
            settingsControl("homecrew-settings-members").performClick()
            awaitDescription("화면 제목: 구성원 관리")
            captureSettingsDiagnostic("P15-local-members")
            settingsControl("homecrew-rename-member-$member").performClick()
            awaitDescription("화면 제목: 구성원 이름 수정")
            val renamed = member + "R"
            settingsControl("homecrew-member-rename-input").performTextReplacement(renamed)
            scenario.onActivity { activity ->
                WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                    .hide(WindowInsetsCompat.Type.ime())
            }
            waitForKeyboardHidden(scenario)
            captureSettingsDiagnostic("P16-member-edit")
            settingsControl("homecrew-member-rename-confirm").assertIsEnabled().performClick()
            awaitDescription("화면 제목: 구성원 관리")
            assertTrue("구성원 이름 수정이 저장되지 않았습니다.", renamed in store.load()!!.members && member !in store.load()!!.members)
            settingsControl("homecrew-settings-back").performClick()
            awaitDescription("화면 제목: 설정")
            settingsControl("homecrew-settings-back").performClick()
            awaitDescription("화면 제목: $homeName")
            scenario.recreate()
            awaitDescription("화면 제목: $homeName")
            assertTrue("재시작 후 구성원이 유지되지 않았습니다.", renamed in store.load()!!.members)
        } catch (error: Throwable) {
            captureSettingsDiagnostic("error-settings-persistence-final")
            throw error
        } finally {
            scenario.close()
            if (original != null) store.save(original) else store.save(base)
        }
    }

    @Test
    fun taskAndCompletionRemainAfterNewActivityAndViewModel() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = LocalHouseholdStore(context)
        val household = store.load() ?: HouseholdSetup(
            homeName = "우리 집",
            members = listOf("나", "지민"),
            defaultMember = "나",
            selectedTemplateIds = suggestedTemplateIds
        )
        assertTrue(store.save(household))

        val today = LocalDate.now()
        val repository = HomeCrewRoomRepository(HomeCrewDatabase.get(context).chores())
        repository.initializeIfNeeded(household, today)
        val name = "RestartProof${System.currentTimeMillis()}"
        val id = repository.save(
            ChoreEditorDraft(
                name = name,
                category = "청소",
                startDate = today,
                repeat = ChoreRepeat(ChoreRepeatMode.DAILY),
                assignment = ChoreAssignment(members = listOf(household.members.first()))
            ),
            household.members,
            repository.snapshot().chores,
            today
        )
        try {
            taskVisibleAfterActivityStart(name, household.homeName, ChoreStatus.PLANNED)
            val occurrence = repository.snapshot().chores.first {
                it.definition.id == id && it.date == today
            }
            repository.toggleCompleted(occurrence.id, household.defaultMember)
            taskVisibleAfterActivityStart(name, household.homeName, ChoreStatus.COMPLETED)
            val restored = repository.snapshot().chores.single { it.id == occurrence.id }
            assertTrue("재시작 후 완료 기록이 유지되지 않았습니다.", restored.completed)
        } finally {
            repository.delete(id)
        }
    }
}
