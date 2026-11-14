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
class ChoreEditorScreenshotTest {
    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private fun outputDirectory(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = InstrumentationRegistry.getArguments()
            .getString("additionalTestOutputDir")?.takeIf { it.isNotBlank() }
            ?.let(::File) ?: requireNotNull(context.externalMediaDirs.firstOrNull())
        return File(dir, "screen-qa").also { assertTrue(it.isDirectory || it.mkdirs()) }
    }

    private fun screenshot(name: String) {
        device.waitForIdle()
        Thread.sleep(250)
        assertTrue(device.takeScreenshot(File(outputDirectory(), "$name.png")))
    }

    private fun press(text: String) {
        var node = device.wait(Until.findObject(By.text(text)), 4_000)
            ?: device.wait(Until.findObject(By.desc(text)), 2_000)
        repeat(4) {
            if (node == null) {
                device.swipe(
                    device.displayWidth / 2, device.displayHeight * 3 / 4,
                    device.displayWidth / 2, device.displayHeight / 3, 18
                )
                device.waitForIdle()
                node = device.wait(Until.findObject(By.text(text)), 1_500)
                    ?: device.wait(Until.findObject(By.desc(text)), 1_000)
            }
        }
        if (node == null) {
            screenshot("error-1d-button")
            device.dumpWindowHierarchy(File(outputDirectory(), "error-1d.xml"))
        }
        requireNotNull(node) { "버튼을 찾을 수 없음: $text" }.click()
        device.waitForIdle()
    }

    private fun selectBalancedAssignment() {
        val selected = By.desc("선택됨: 담당 방식 업무량 균형")
        val explanation = By.textContains("기준 업무량:")
        val available = By.desc("담당 방식: 업무량 균형")

        repeat(4) {
            if (device.wait(Until.findObject(selected), 1_000) != null ||
                device.wait(Until.findObject(explanation), 1_000) != null
            ) return

            val option = device.wait(Until.findObject(available), 2_000)
                ?: device.wait(Until.findObject(By.text("업무량 균형")), 1_000)
            if (option != null) {
                try {
                    val bounds = option.visibleBounds
                    device.click(bounds.centerX(), bounds.centerY())
                    device.waitForIdle()
                } catch (_: androidx.test.uiautomator.StaleObjectException) {
                    device.waitForIdle()
                }
            }
        }

        screenshot("error-1d-assignment-balance")
        device.dumpWindowHierarchy(File(outputDirectory(), "error-1d-assignment-balance.xml"))
        throw AssertionError("업무량 균형 선택 상태가 반영되지 않았습니다.")
    }

    private fun awaitScreen(title: String) {
        var node = device.wait(Until.findObject(By.desc("화면 제목: $title")), 12_000)
        if (node == null && device.dismissEmulatorLauncherAnrIfShown()) {
            node = device.wait(Until.findObject(By.desc("화면 제목: $title")), 8_000)
        }
        if (node == null) {
            screenshot("error-1d-screen")
            device.dumpWindowHierarchy(File(outputDirectory(), "error-1d-hierarchy.xml"))
        }
        requireNotNull(node) { "화면 전환 실패: $title" }
        device.waitForIdle()
    }

    private fun openEditorSubpage(description: String, title: String) {
        val finder = By.desc(description)
        var button = device.wait(Until.findObject(finder), 2_500)
        if (button == null) {
            device.swipe(
                device.displayWidth / 2, device.displayHeight * 3 / 4,
                device.displayWidth / 2, device.displayHeight / 3, 20
            )
            device.waitForIdle()
            button = device.wait(Until.findObject(finder), 8_000)
        }
        requireNotNull(button) { "하위 화면 버튼 없음: $description" }
        device.waitForIdle()
        repeat(3) {
            val candidate = device.wait(Until.findObject(finder), 3_000)
                ?: return@repeat
            val bounds = candidate.visibleBounds
            device.click(bounds.centerX(), bounds.centerY())
            device.waitForIdle()
            val titleNode = device.wait(
                Until.findObject(By.desc("화면 제목: $title")), 4_000
            )
            if (titleNode != null) {
                device.waitForIdle()
                return
            }
        }
        screenshot("error-1d-navigation")
        device.dumpWindowHierarchy(File(outputDirectory(), "error-1d-hierarchy.xml"))
        error("하위 화면 전환 실패: $title")
    }

    private fun completeSetupIfNeeded() {
        device.dismissEmulatorLauncherAnrIfShown()
        if (device.wait(Until.findObject(By.text("시작하기")), 2_000) != null) {
            press("시작하기")
            press("다음")
            press("다음")
            press("선택한 집안일로 시작")
        }
        awaitScreen("우리 집")
    }

    @Test
    fun captureFourChorePagesAndValidation() {
        completeSetupIfNeeded()
        val choresTab = device.wait(Until.findObject(By.desc("집안일 탭")), 12_000)
        requireNotNull(choresTab) { "집안일 하단 탭을 찾을 수 없습니다." }.click()
        awaitScreen("집안일")
        press("집안일 추가")
        awaitScreen("집안일 추가")
        screenshot("08-chore-add")

        press("집안일 저장")
        val validation = device.wait(
            Until.findObject(By.text("집안일 이름을 입력해 주세요.")), 8_000
        )
        requireNotNull(validation) { "필수 이름 검증이 동작하지 않습니다." }
        screenshot("08-chore-add-error")

        openEditorSubpage("반복 설정 열기", "반복 설정")
        screenshot("09-chore-repeat")
        press("반복 방식: 매주")
        val selectedWeekly = device.wait(
            Until.findObject(By.desc("선택됨: 반복 방식 매주")), 8_000
        )
        requireNotNull(selectedWeekly) { "주간 반복 선택이 적용되지 않았습니다." }
        screenshot("09-chore-repeat-weekly")
        press("반복 설정 적용")
        awaitScreen("집안일 추가")

        openEditorSubpage("담당 방식 설정 열기", "담당 방식")
        screenshot("10-chore-assignment")
        press("담당 방식: 순번제")
        requireNotNull(
            device.wait(Until.findObject(By.desc("선택됨: 담당 방식 순번제")), 8_000)
        ) { "순번제 선택이 적용되지 않았습니다." }
        screenshot("10-chore-assignment-round-robin")
        selectBalancedAssignment()
        screenshot("10-chore-assignment-balanced")
        press("담당 방식 적용")
        awaitScreen("집안일 추가")

        press("이전")
        val discard = device.wait(Until.findObject(By.text("변경사항 버리기")), 2_000)
        if (discard != null) {
            discard.click()
            device.waitForIdle()
        }
        awaitScreen("집안일")
        press("설거지")
        awaitScreen("설거지")
        screenshot("11-chore-detail")

        val originallyCompleted = device.wait(
            Until.findObject(By.text("이 회차 완료 취소")), 1_000
        ) != null
        if (originallyCompleted) press("이 회차 완료 취소")
        press("이 회차 완료 처리")
        val undo = device.wait(Until.findObject(By.text("이 회차 완료 취소")), 8_000)
        requireNotNull(undo) { "선택한 회차 완료 상태가 반영되지 않습니다." }
        screenshot("11-chore-detail-completed")

        var history = device.wait(Until.findObject(By.text("최근 완료 기록")), 1_000)
        repeat(6) {
            if (history == null) {
                device.swipe(
                    device.displayWidth / 2, device.displayHeight * 3 / 4,
                    device.displayWidth / 2, device.displayHeight / 3, 18
                )
                device.waitForIdle()
                history = device.wait(Until.findObject(By.text("최근 완료 기록")), 1_000)
            }
        }
        requireNotNull(history) { "최근 완료 기록 목록이 표시되지 않습니다." }
        screenshot("11-chore-recent-completions")

        val completionDate = java.time.LocalDate.now()
        val historyDescription = "회차 선택: 완료 · $completionDate"
        var selectedHistory = device.wait(Until.findObject(By.desc(historyDescription)), 1_000)
        repeat(12) {
            if (selectedHistory == null) {
                device.swipe(
                    device.displayWidth / 2,
                    device.displayHeight * 3 / 4,
                    device.displayWidth / 2,
                    device.displayHeight / 3,
                    18
                )
                device.waitForIdle()
                selectedHistory = device.wait(Until.findObject(By.desc(historyDescription)), 1_000)
            }
        }
        if (selectedHistory == null) {
            screenshot("error-1d-recent-completion")
            device.dumpWindowHierarchy(File(outputDirectory(), "error-1d-recent-completion.xml"))
        }
        requireNotNull(selectedHistory) {
            "최근 완료 회차를 선택할 수 없습니다: $historyDescription"
        }.click()
        device.waitForIdle()
        val undoForSelectedHistory = device.wait(
            Until.findObject(By.text("이 회차 완료 취소")), 8_000
        )
        requireNotNull(undoForSelectedHistory) {
            "선택한 완료 회차로 이동하지 못했습니다."
        }
        screenshot("11-chore-selected-completion")
        press("이 회차 완료 취소")
        press("이 회차 건너뛰기")
        press("건너뛰기")
        requireNotNull(device.wait(Until.findObject(By.text("건너뜀")), 8_000)) {
            "선택한 회차의 건너뛰기 상태가 반영되지 않습니다."
        }
        screenshot("11-chore-detail-skipped")
        press("건너뛰기 취소")
        if (originallyCompleted) press("이 회차 완료 처리")
        press("집안일 수정")
        awaitScreen("집안일 수정")
        screenshot("12-chore-edit")
    }
}
