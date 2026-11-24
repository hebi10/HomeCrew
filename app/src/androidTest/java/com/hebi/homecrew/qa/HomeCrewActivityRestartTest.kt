package com.hebi.homecrew.qa

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
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
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.hebi.homecrew.MainActivity
import com.hebi.homecrew.data.local.HomeCrewDatabase
import com.hebi.homecrew.data.local.HomeCrewRoomRepository
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
import org.junit.Assert.assertNotNull
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

    private fun taskVisibleAfterActivityStart(taskName: String) {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            var home = device.wait(Until.findObject(By.desc("화면 제목: 우리 집")), 15_000)
            if (home == null && device.dismissEmulatorLauncherAnrIfShown()) {
                home = device.wait(Until.findObject(By.desc("화면 제목: 우리 집")), 8_000)
            }
            assertNotNull("홈 화면에 진입하지 못했습니다.", home)
            val choresTab = device.wait(Until.findObject(By.desc("집안일 탭")), 10_000)
            assertNotNull("집안일 탭이 보이지 않습니다.", choresTab)
            requireNotNull(choresTab).click()
            assertNotNull(
                "집안일 화면이 열리지 않았습니다.",
                device.wait(Until.findObject(By.desc("화면 제목: 집안일")), 12_000)
            )
            var row = device.wait(Until.findObject(By.text(taskName)), 3_000)
            repeat(12) {
                if (row != null) return@repeat
                device.swipe(
                    device.displayWidth / 2, device.displayHeight * 3 / 4,
                    device.displayWidth / 2, device.displayHeight / 3, 18
                )
                row = device.wait(Until.findObject(By.text(taskName)), 600)
            }
            assertNotNull("앱 재시작 후 저장된 집안일이 목록에 없습니다.", row)
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
            awaitSettings()
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
            compose.onNodeWithTag("homecrew-settings-list").performScrollToNode(
                androidx.compose.ui.test.hasText("뒤로")
            )
            compose.onNodeWithText("뒤로").performScrollTo().assertIsDisplayed().performClick()
            awaitDescription("화면 제목: $homeName")
            scenario.recreate()
            awaitDescription("화면 제목: $homeName")
            assertTrue("재시작 후 구성원이 유지되지 않았습니다.", member in store.load()!!.members)
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
            taskVisibleAfterActivityStart(name)
            val occurrence = repository.snapshot().chores.first {
                it.definition.id == id && it.date == today
            }
            repository.toggleCompleted(occurrence.id, household.defaultMember)
            taskVisibleAfterActivityStart(name)
            assertTrue(repository.snapshot().chores.single { it.id == occurrence.id }.completed)
        } finally {
            repository.delete(id)
        }
    }
}
