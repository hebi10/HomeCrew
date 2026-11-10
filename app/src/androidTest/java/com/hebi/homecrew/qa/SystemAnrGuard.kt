package com.hebi.homecrew.qa

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

internal fun UiDevice.dismissLauncherAnr() {
    repeat(3) {
        wait(Until.findObject(By.textContains("isn't responding")), 600) ?: return
        val waitButton = wait(Until.findObject(By.text("Wait")), 2_000) ?: return
        waitButton.click()
        waitForIdle()
    }
}
