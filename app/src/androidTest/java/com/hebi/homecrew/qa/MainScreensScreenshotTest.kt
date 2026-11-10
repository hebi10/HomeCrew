package com.hebi.homecrew.qa

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.hebi.homecrew.MainActivity
import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainScreensScreenshotTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private fun tapTab(label: String) {
        val tab = device.wait(Until.findObject(By.text(label)), 10_000)
        assertNotNull("탭을 찾을 수 없음: $label", tab)
        tab.click()
        device.waitForIdle()
    }

    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(requireNotNull(context.getExternalFilesDir(null)), "screen-qa")
        assertTrue(directory.isDirectory || directory.mkdirs())
        device.waitForIdle()
        assertTrue("화면 캡처 실패: $name", device.takeScreenshot(File(directory, "$name.png")))
    }

    @Test
    fun captureMainTabsAndBasicInteractions() {
        device.waitForIdle()
        screenshot("01-home-light")
        tapTab("일정")
        screenshot("02-schedule-light")
        tapTab("집안일")
        screenshot("03-chores-light")
        tapTab("통계")
        screenshot("04-stats-light")

        tapTab("홈")
        val complete = device.wait(Until.findObject(By.desc("완료 처리")), 10_000)
        assertNotNull("완료 버튼을 찾을 수 없음", complete)
        complete.click()
        screenshot("05-home-after-completion")

        tapTab("통계")
        screenshot("06-stats-after-completion")

        tapTab("홈")
        repeat(2) {
            val theme = device.wait(Until.findObject(By.descContains("테마 전환")), 10_000)
            assertNotNull("테마 버튼을 찾을 수 없음", theme)
            theme.click()
            device.waitForIdle()
        }
        screenshot("07-home-dark")
    }
}
