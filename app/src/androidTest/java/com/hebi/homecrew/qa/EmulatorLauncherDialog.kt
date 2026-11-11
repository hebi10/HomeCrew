package com.hebi.homecrew.qa

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

internal fun UiDevice.dismissEmulatorLauncherAnrIfShown(): Boolean {
    val launcherError = findObject(By.textContains("Pixel Launcher isn't responding"))
        ?: return false
    val close = wait(Until.findObject(By.text("Close app")), 3_000)
        ?: return false
    close.click()
    waitForIdle()
    return launcherError.text.isNotBlank()
}
