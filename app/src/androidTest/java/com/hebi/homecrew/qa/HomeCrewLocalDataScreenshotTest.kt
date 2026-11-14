package com.hebi.homecrew.qa

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.hebi.homecrew.MainActivity
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeCrewLocalDataScreenshotTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun localBackupScreenCanBeOpenedWithoutChangingData() {
        device.dismissEmulatorLauncherAnrIfShown()
        device.waitForIdle()
        if (device.wait(Until.findObject(By.text("시작하기")), 2_000) != null) {
            listOf("시작하기", "다음", "다음", "선택한 집안일로 시작").forEach { text ->
                requireNotNull(device.wait(Until.findObject(By.text(text)), 8_000)).click()
                device.waitForIdle()
            }
        }
        requireNotNull(device.wait(Until.findObject(By.desc("집안일 탭")), 12_000)).click()
        requireNotNull(device.wait(Until.findObject(By.desc("화면 제목: 집안일")), 12_000))
        var button = device.wait(Until.findObject(By.text("데이터 백업·복원")), 2_000)
        repeat(10) {
            if (button == null) {
                device.swipe(device.displayWidth / 2, device.displayHeight * 4 / 5,
                    device.displayWidth / 2, device.displayHeight / 3, 20)
                button = device.wait(Until.findObject(By.text("데이터 백업·복원")), 1_000)
            }
        }
        requireNotNull(button) { "데이터 관리 진입 버튼이 없습니다." }.click()
        requireNotNull(device.wait(Until.findObject(By.desc("화면 제목: 로컬 데이터 관리")), 12_000))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = InstrumentationRegistry.getArguments()
            .getString("additionalTestOutputDir")?.takeIf(String::isNotBlank)
            ?.let(::File) ?: requireNotNull(context.externalMediaDirs.firstOrNull())
        val output = File(dir, "screen-qa").also { assertTrue(it.exists() || it.mkdirs()) }
        assertTrue(device.takeScreenshot(File(output, "13-local-backup-restore.png")))
        requireNotNull(device.wait(Until.findObject(By.text("이전")), 4_000)).click()
        requireNotNull(device.wait(Until.findObject(By.desc("집안일 탭")), 12_000)) {
            "데이터 관리에서 목록으로 복귀하지 못했습니다."
        }
    }
}
