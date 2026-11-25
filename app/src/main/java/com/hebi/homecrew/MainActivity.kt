package com.hebi.homecrew

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hebi.homecrew.data.local.HomeCrewLocalMemberCoordinator
import com.hebi.homecrew.data.local.LocalMemberChangeRejected
import com.hebi.homecrew.ui.setup.HouseholdSetup
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.hebi.homecrew.reminder.HomeCrewReminderScheduler
import com.hebi.homecrew.ui.setup.HomeCrewSetupFlow
import com.hebi.homecrew.ui.setup.LocalHouseholdStore
import com.hebi.homecrew.ui.screens.HomeCrewMainScreens
import com.hebi.homecrew.ui.theme.HomeCrewTheme
import com.hebi.homecrew.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HomeCrewReminderScheduler.apply(applicationContext)
        enableEdgeToEdge()

        setContent {
            val localStore = remember { LocalHouseholdStore(applicationContext) }
            val memberCoordinator = remember { HomeCrewLocalMemberCoordinator.create(applicationContext) }
            var household by remember { mutableStateOf<HouseholdSetup?>(null) }
            var ready by remember { mutableStateOf(false) }
            var recoveryError by remember { mutableStateOf(false) }
            var recoveryAttempt by remember { mutableStateOf(0) }
            var changingMember by remember { mutableStateOf(false) }
            LaunchedEffect(recoveryAttempt) {
                ready = false
                recoveryError = false
                try {
                    household = memberCoordinator.recoverAndLoad()
                    ready = true
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    recoveryError = true
                }
            }
            val themePreferences = remember {
                applicationContext.getSharedPreferences("homecrew_ui_settings", MODE_PRIVATE)
            }
            var selectedTheme by rememberSaveable {
                mutableStateOf(
                    ThemeMode.entries.firstOrNull {
                        it.name == themePreferences.getString("theme_mode", ThemeMode.SYSTEM.name)
                    } ?: ThemeMode.SYSTEM
                )
            }
            val darkTheme = when (selectedTheme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView)
                    .isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, window.decorView)
                    .isAppearanceLightNavigationBars = !darkTheme
            }

            HomeCrewTheme(darkTheme = darkTheme) {
                if (!ready) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (recoveryError) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("구성원 저장 상태를 복구하지 못했습니다. 기존 자료를 보호하기 위해 화면을 잠갔습니다.")
                            TextButton(onClick = { recoveryAttempt++ }) { Text("복구 다시 시도") }
                        } else CircularProgressIndicator()
                    }
                } else if (household == null) {
                    HomeCrewSetupFlow(onFinished = { setup ->
                        val saved = localStore.save(setup)
                        if (saved) household = setup
                        saved
                    })
                } else {
                    HomeCrewMainScreens(
                        themeMode = selectedTheme,
                        onThemeModeChange = { mode ->
                            selectedTheme = mode
                            themePreferences.edit().putString("theme_mode", mode.name).apply()
                        },
                        householdSetup = household,
                        onHouseholdChange = { changed ->
                            if (changingMember || !ready) false
                            else localStore.save(changed).also { saved -> if (saved) household = changed }
                        },
                        onLocalMemberChange = { member, replacement ->
                            if (changingMember || !ready) "다른 저장 작업을 확인해 주세요."
                            else {
                                changingMember = true
                                try {
                                    household = memberCoordinator.change(checkNotNull(household), member, replacement)
                                    null
                                } catch (error: LocalMemberChangeRejected) {
                                    error.userMessage
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Exception) {
                                    // A Room commit may already have happened. Re-enter the
                                    // fail-closed recovery screen before exposing the old setup.
                                    ready = false
                                    recoveryAttempt++
                                    "저장 상태를 다시 확인합니다."
                                } finally { changingMember = false }
                            }
                        }
                    )
                }
            }
        }
    }
}
