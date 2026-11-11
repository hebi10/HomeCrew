package com.hebi.homecrew.qa

import androidx.test.core.app.ActivityScenario
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
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeCrewActivityRestartTest {
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

    private fun tapFresh(label: String) {
        repeat(5) {
            try {
                val button = device.wait(Until.findObject(By.text(label)), 3_000)
                if (button != null) {
                    button.click()
                    device.waitForIdle()
                    return
                }
            } catch (_: androidx.test.uiautomator.StaleObjectException) {
                device.waitForIdle()
            }
        }
        throw AssertionError("동작 버튼을 찾거나 누르지 못했습니다: $label")
    }

    private fun enterFreshText(index: Int, value: String) {
        repeat(5) {
            try {
                val fields = device.findObjects(By.clazz("android.widget.EditText"))
                if (index >= 0 && index < fields.size) {
                    fields[index].text = value
                    device.waitForIdle()
                    return
                }
            } catch (_: androidx.test.uiautomator.StaleObjectException) {
                device.waitForIdle()
            }
        }
        throw AssertionError("입력 필드에 텍스트를 입력하지 못했습니다: $index")
    }

    private fun scrollToAction(label: String, down: Boolean = true) {
        val x = device.displayWidth / 2
        val upper = device.displayHeight / 3
        val lower = device.displayHeight * 3 / 4
        repeat(12) {
            if (device.wait(Until.findObject(By.text(label)), 700) != null) return
            device.swipe(x, if (down) lower else upper, x, if (down) upper else lower, 18)
            device.waitForIdle()
        }
        throw AssertionError("화면을 스크롤해도 동작을 찾지 못했습니다: $label")
    }

    @Test
    fun householdSettingsPersistAfterActivityRestart() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = LocalHouseholdStore(context)
        val original = store.load()
        val base = original ?: HouseholdSetup(
            homeName = "우리 집",
            members = listOf("나", "지민"),
            defaultMember = "나",
            selectedTemplateIds = suggestedTemplateIds
        )
        assertTrue(store.save(base))
        val unique = System.currentTimeMillis().toString().takeLast(7)
        val homeName = "QA집" + unique
        val member = "QA" + unique
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            assertNotNull(
                "홈 화면 진입 실패",
                device.wait(Until.findObject(By.desc("화면 제목: ${base.homeName}")), 20_000)
            )
            val settings = device.wait(Until.findObject(By.desc("집 및 앱 설정")), 10_000)
            assertNotNull("설정 진입 버튼이 없습니다.", settings)
            requireNotNull(settings).click()
            assertNotNull("설정 화면 진입 실패", device.wait(Until.findObject(By.text("설정")), 8_000))
            enterFreshText(0, homeName)
            tapFresh("집 이름 저장")
            assertTrue("집 이름이 저장되지 않았습니다.", store.load()?.homeName == homeName)
            scrollToAction("기본으로 설정")
            tapFresh("기본으로 설정")
            assertTrue("기본 구성원 변경이 저장되지 않았습니다.", store.load()?.defaultMember != base.defaultMember)
            scrollToAction("구성원 추가")
            val visibleFields = device.findObjects(By.clazz("android.widget.EditText"))
            assertTrue("구성원 추가 입력 필드가 보이지 않습니다.", visibleFields.isNotEmpty())
            enterFreshText(visibleFields.lastIndex, member)
            tapFresh("구성원 추가")
            assertTrue("구성원 추가가 저장되지 않았습니다.", member in (store.load()?.members ?: emptyList()))
            device.pressBack()
            if (device.wait(Until.findObject(By.desc("화면 제목: $homeName")), 2_000) == null) {
                scrollToAction("뒤로", down = false)
                tapFresh("뒤로")
            }
            assertNotNull(
                "설정 종료 후 홈 화면 복귀 실패",
                device.wait(Until.findObject(By.desc("화면 제목: $homeName")), 10_000)
            )
            scenario.recreate()
            assertNotNull(
                "재시작 후 집 이름이 반영되지 않았습니다.",
                device.wait(Until.findObject(By.desc("화면 제목: $homeName")), 20_000)
            )
            assertTrue("재시작 후 구성원이 유지되지 않았습니다.", member in store.load()!!.members)
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
