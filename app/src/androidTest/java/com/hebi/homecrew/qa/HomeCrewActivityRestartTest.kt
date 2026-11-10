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
            assertNotNull(
                "홈 화면에 진입하지 못했습니다.",
                device.wait(Until.findObject(By.desc("화면 제목: 우리 집")), 15_000)
            )
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
