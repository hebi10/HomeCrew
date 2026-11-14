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
import org.junit.Assert.assertNull
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
        var node = device.wait(Until.findObject(By.desc("화면 제목: $title")), 12_000)
        if (node == null && device.dismissEmulatorLauncherAnrIfShown()) {
            node = device.wait(Until.findObject(By.desc("화면 제목: $title")), 8_000)
        }
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
        device.dismissEmulatorLauncherAnrIfShown()
        val start = device.wait(Until.findObject(By.text("시작하기")), 3_000)
        if (start == null) return
        assertNull("과거 시연 안내가 표시되면 안 됩니다.", device.findObject(By.textContains("현재 시연용")))
        screenshot("00-setup-intro")
        start.click()
        awaitText("우리 집 만들기")
        screenshot("00-setup-household")
        tapButton("다음")
        awaitText("함께할 사람 등록")
        screenshot("00-setup-members")
        tapButton("다음")
        awaitText("집안일을 선택하세요")
        assertNull("템플릿의 오래된 시연 문구를 제거해야 합니다.", device.findObject(By.textContains("시연 데이터")))
        screenshot("00-setup-templates")
        tapButton("선택한 집안일로 시작")
        awaitScreenTitle("우리 집")
    }

    @Test
    fun captureMainTabsAndBasicInteractions() {
        device.waitForIdle()
        completeSetupIfShown()
        awaitScreenTitle("우리 집")
        assertNull("개발용 갤러리는 사용자 홈에 표시하면 안 됩니다.", device.findObject(By.desc("1-A 디자인 시스템 보기")))
        assertNull("홈에 중복 저장 안내를 표시하면 안 됩니다.", device.findObject(By.textContains("다른 기기와 동기화되지 않습니다")))
        awaitText("오늘의 진행률")
        awaitText("집안일 추가")
        screenshot("01-home-light")

        tapTab("일정")
        awaitScreenTitle("일정")
        awaitText("구성원별 일정")
        screenshot("02-schedule-light")

        tapButton("월 보기")
        screenshot("02-schedule-month-light")
        tapButton("일 보기")
        screenshot("02-schedule-day-light")
        tapButton("다음 기간")
        screenshot("02-schedule-next-day")
        tapButton("선택 날짜에 집안일 추가")
        awaitScreenTitle("집안일 추가")

        val selectedDate = java.time.LocalDate.now().plusDays(1)
        val startDateLabel = "시작일: ${selectedDate}"
        var prefilledDate = device.wait(Until.findObject(By.text(startDateLabel)), 1_000)
        repeat(6) {
            if (prefilledDate == null) {
                device.swipe(
                    device.displayWidth / 2,
                    device.displayHeight * 3 / 4,
                    device.displayWidth / 2,
                    device.displayHeight / 3,
                    18
                )
                device.waitForIdle()
                prefilledDate = device.wait(Until.findObject(By.text(startDateLabel)), 1_000)
            }
        }
        assertNotNull("선택 날짜를 새 집안일의 시작일로 반영해야 합니다.", prefilledDate)
        screenshot("02-schedule-date-prefilled-add")
        device.pressBack()
        awaitScreenTitle("일정")
        tapButton("오늘")
        tapButton("주 보기")

        tapTab("집안일")
        awaitScreenTitle("집안일")
        awaitText("등록된 집안일")
        awaitText("필터 및 정렬")
        screenshot("03-chores-light")
        tapButton("필터 및 정렬")
        awaitText("카테고리")
        screenshot("03-chores-filters-expanded")
        tapButton("필터 및 정렬")
        tapButton("완료")
        awaitText("필터 초기화")
        screenshot("03-chores-status-filter")
        tapButton("필터 초기화")
        awaitText("등록된 집안일")

        tapTab("통계")
        awaitScreenTitle("통계")
        screenshot("04-stats-light")

        tapTab("홈")
        awaitScreenTitle("우리 집")
        val complete = device.wait(Until.findObject(By.desc("완료 처리")), 10_000)
        if (complete == null) dumpFailedUi("완료 처리")
        requireNotNull(complete) { "완료 버튼을 찾을 수 없음" }.click()
        awaitScreenTitle("우리 집")
        screenshot("05-home-after-completion")

        tapTab("통계")
        awaitScreenTitle("통계")
        awaitText("완료율")
        screenshot("06-stats-after-completion")

        tapTab("홈")
        awaitScreenTitle("우리 집")
        val themeOrder = listOf("시스템", "라이트", "다크")
        repeat(themeOrder.size) {
            if (device.wait(Until.findObject(By.desc("테마 전환 · 현재 다크")), 700) == null) {
                val currentMode = themeOrder.firstOrNull { mode ->
                    device.findObject(By.desc("테마 전환 · 현재 $mode")) != null
                }
                if (currentMode == null) dumpFailedUi("테마 현재 상태")
                requireNotNull(currentMode) { "현재 테마를 확인하지 못했습니다." }

                val nextMode = themeOrder[(themeOrder.indexOf(currentMode) + 1) % themeOrder.size]
                val button = device.wait(
                    Until.findObject(By.desc("테마 전환 · 현재 $currentMode")), 5_000
                )
                if (button == null) dumpFailedUi("테마 전환 버튼")
                requireNotNull(button) { "테마 전환 버튼을 찾을 수 없습니다." }.click()

                val updated = device.wait(
                    Until.findObject(By.desc("테마 전환 · 현재 $nextMode")), 10_000
                )
                if (updated == null) dumpFailedUi("테마 전환 상태")
                requireNotNull(updated) { "테마가 $nextMode 상태로 전환되지 않았습니다." }
                device.waitForIdle()
            }
        }
        val dark = device.wait(
            Until.findObject(By.desc("테마 전환 · 현재 다크")), 5_000
        )
        if (dark == null) dumpFailedUi("다크 모드")
        requireNotNull(dark) { "다크 모드 적용을 확인하지 못했습니다." }
        Thread.sleep(900)
        screenshot("07-home-dark")

        val settings = device.wait(Until.findObject(By.desc("집 및 앱 설정")), 10_000)
        if (settings == null) dumpFailedUi("집 및 앱 설정")
        requireNotNull(settings) { "설정 진입 버튼을 찾을 수 없습니다." }.click()
        awaitText("설정")
        Thread.sleep(900)
        screenshot("13-settings-dark")

        var lightOption = device.wait(Until.findObject(By.text("라이트")), 800)
        repeat(12) {
            if (lightOption == null) {
                device.swipe(
                    device.displayWidth / 2, device.displayHeight * 3 / 4,
                    device.displayWidth / 2, device.displayHeight / 3, 18
                )
                device.waitForIdle()
                lightOption = device.wait(Until.findObject(By.text("라이트")), 800)
            }
        }
        if (lightOption == null) dumpFailedUi("설정 라이트 테마")
        requireNotNull(lightOption) { "설정 화면에서 라이트 테마를 찾을 수 없습니다." }.click()
        device.waitForIdle()
        Thread.sleep(900)
        screenshot("13-settings-light-theme")

        var settingsTitle = device.wait(Until.findObject(By.text("설정")), 700)
        repeat(12) {
            if (settingsTitle == null) {
                device.swipe(
                    device.displayWidth / 2, device.displayHeight / 3,
                    device.displayWidth / 2, device.displayHeight * 3 / 4, 18
                )
                device.waitForIdle()
                settingsTitle = device.wait(Until.findObject(By.text("설정")), 700)
            }
        }
        if (settingsTitle == null) dumpFailedUi("설정 라이트 상단")
        requireNotNull(settingsTitle) { "설정 화면 상단으로 복귀하지 못했습니다." }
        screenshot("13-settings-light")

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val oauthResource = context.resources.getIdentifier(
            "default_web_client_id", "string", context.packageName
        )
        if (oauthResource != 0) {
            var accountHeader = device.wait(Until.findObject(By.text("계정")), 800)
            repeat(16) {
                if (accountHeader == null) {
                    device.swipe(
                        device.displayWidth / 2, device.displayHeight * 3 / 4,
                        device.displayWidth / 2, device.displayHeight / 3, 18
                    )
                    device.waitForIdle()
                    accountHeader = device.wait(Until.findObject(By.text("계정")), 800)
                }
            }
            if (accountHeader == null) dumpFailedUi("계정 상태")
            requireNotNull(accountHeader) { "Google 계정 영역을 찾지 못했습니다." }

            val local = device.wait(Until.findObject(By.text("로그인 없이 사용 중")), 4_000)
            val signedIn = device.wait(Until.findObject(By.text("Google 계정 연결됨")), 1_000)
            if (local == null && signedIn == null) dumpFailedUi("계정 인증 상태")
            assertTrue(
                "계정의 비로그인 또는 로그인 완료 상태가 표시되어야 합니다.",
                local != null || signedIn != null
            )
            screenshot("14-account-section-light")
        }
    }
}
