package com.hebi.homecrew

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.hebi.homecrew.ui.setup.HomeCrewSetupFlow
import com.hebi.homecrew.ui.setup.LocalHouseholdStore
import com.hebi.homecrew.ui.screens.HomeCrewMainScreens
import com.hebi.homecrew.ui.theme.HomeCrewTheme
import com.hebi.homecrew.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val localStore = remember { LocalHouseholdStore(applicationContext) }
            var household by remember { mutableStateOf(localStore.load()) }
            var selectedTheme by rememberSaveable { mutableStateOf(ThemeMode.SYSTEM) }
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
                if (household == null) {
                    HomeCrewSetupFlow(onFinished = { setup ->
                        val saved = localStore.save(setup)
                        if (saved) household = setup
                        saved
                    })
                } else {
                    HomeCrewMainScreens(
                        themeMode = selectedTheme,
                        onThemeModeChange = { selectedTheme = it },
                        householdSetup = household
                    )
                }
            }
        }
    }
}
