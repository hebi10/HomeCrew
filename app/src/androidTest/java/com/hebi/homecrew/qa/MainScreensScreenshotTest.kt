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

    private fun qaDirectory(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val outputDir = InstrumentationRegistry.getArguments()
            .getString("additionalTestOutputDir")
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?: requireNotNull(context.externalMediaDirs.firstOrNull())
        return File(outputDir, "screen-qa").also {
            assertTrue(it.isDirectory || it.mkdirs())
        }
    }

    private fun dumpFailedUi(label: String) {
        val safeLabel = label.replace(Regex("[^A-Za-z0-9가-힣]"), "_")
        device.takeScreenshot(File(qaDirectory(), "error-${safeLabel}.png"))
        device.dumpWindowHierarchy(File(qaDirectory(), "hierarchy-${safeLabel}.xml"))
    }

    private fun tapTab(label: String) {
        val tab = device.wait(Until.findObject(By.desc("${label} 탭")), 8_000)
            ?: device.wait(Until.findObject(By.text(label)), 2_000)
        if (tab == null) dumpFailedUi(label)
        requireNotNull(tab) { "하단 탐색 탭을 찾을 수 없음: $label" }.click()
        device.waitForIdle()
    }

    private fun tapButton(label: String) {
        val button = device.wait(Until.findObject(By.text(label)), 5_000)
            ?: device.wait(Until.findObject(By.desc(label)), 5_000)
        if (button == null) dumpFailedUi(label)
        requireNotNull(button) { "버튼을 찾을 수 없음: $label" }.click()
        device.waitForIdle()
    }

    private fun awaitText(label: String) {
        val node = device.wait(Until.findObject(By.text(label)), 12_000)
        if (node == null) dumpFailedUi(label)
        requireNotNull(node) { "화면에서 텍스트가 표시되지 않음: $label" }
        device.waitForIdle()
    }

    private fun awaitScreenTitle(title: String) {
        device.dismissLauncherAnr()
        val node = device.wait(
            Until.findObject(By.desc("화면 제목: $title")),
            12_000
        )
        if (node == null) dumpFailedUi(title)
        requireNotNull(node) { "화면 전환이 완료되지 않음: $title" }
        device.waitForIdle()
    }

    private fun screenshot(name: String) {
        val directory = qaDirectory()
        device.waitForIdle()
        assertTrue("화면 캡처 실패: $name", device.takeScreenshot(File(directory, "$name.png")))
    }

    private fun completeSetupIfShown() {
        device.dismissLauncherAnr()
        val start = device.wait(Until.findObject(By.text("시작하기")), 3_000)
        if (start == null) return
        screenshot("00-setup-intro")
        start.click()
        awaitText("우리 집 만들기")
        screenshot("00-setup-household")
        tapButton("다음")
        awaitText("함께할 사람 등록")
        screenshot("00-setup-members")
        tapButton("다음")
        awaitText("집안일을 선택하세요")
        screenshot("00-setup-templates")
        tapButton("선택한 집안일로 시작")
        awaitScreenTitle("우리 집")
    }

    @Test
    fun captureMainTabsAndBasicInteractions() {
        device.waitForIdle()
        completeSetupIfShown()
        awaitScreenTitle("우리 집")
        screenshot("01-home-light")

        tapTab("일정")
        awaitScreenTitle("일정")
        screenshot("02-schedule-light")

        tapTab("집안일")
        awaitScreenTitle("집안일")
        screenshot("03-chores-light")

        tapTab("통계")
        awaitScreenTitle("통계")
        screenshot("04-stats-light")

        tapTab("홈")
        awaitScreenTitle("우리 집")
        val complete = device.wait(Until.findObject(By.desc("완료 처리")), 10_000)
        if (complete == null) dumpFailedUi("완료 처리")
        requireNotNull(complete) { "완료 버튼을 찾을 수 없음" }.click()
        awaitText("1 / 4")
        screenshot("05-home-after-completion")

        tapTab("통계")
        awaitScreenTitle("통계")
        awaitText("25%")
        screenshot("06-stats-after-completion")

        tapTab("홈")
        awaitScreenTitle("우리 집")
        repeat(2) {
            val theme = device.wait(Until.findObject(By.descContains("테마 전환")), 10_000)
            if (theme == null) dumpFailedUi("테마 전환")
            requireNotNull(theme) { "테마 버튼을 찾을 수 없음" }.click()
            device.waitForIdle()
        }
        val dark = device.wait(
            Until.findObject(By.desc("테마 전환 · 현재 다크")),
            10_000
        )
        if (dark == null) dumpFailedUi("다크 모드")
        requireNotNull(dark) { "다크 모드 적용을 확인하지 못했습니다." }
        screenshot("07-home-dark")
    }
}
