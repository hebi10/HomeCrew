package com.hebi.homecrew

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.hebi.homecrew.ui.gallery.HomeCrewDesignSystemGallery
import com.hebi.homecrew.ui.setup.HomeCrewSetupFlow
import com.hebi.homecrew.ui.setup.LocalHouseholdStore
import com.hebi.homecrew.ui.screens.HomeCrewMainScreens
import com.hebi.homecrew.ui.theme.HomeCrewTheme
import com.hebi.homecrew.ui.theme.LocalHomeCrewColors
import com.hebi.homecrew.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val localStore = remember { LocalHouseholdStore(applicationContext) }
            var household by remember { mutableStateOf(localStore.load()) }
            var selectedTheme by rememberSaveable { mutableStateOf(ThemeMode.SYSTEM) }
            var showingDesignSystem by rememberSaveable { mutableStateOf(false) }
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
                if (showingDesignSystem) {
                    val palette = LocalHomeCrewColors.current
                    BackHandler { showingDesignSystem = false }
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(palette.surface)
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            TextButton(onClick = { showingDesignSystem = false }) {
                                Text("← 메인 화면으로")
                            }
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            HomeCrewDesignSystemGallery(
                                themeMode = selectedTheme,
                                onThemeModeChange = { selectedTheme = it }
                            )
                        }
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
                        onThemeModeChange = { selectedTheme = it },
                        onOpenDesignSystem = { showingDesignSystem = true },
                        householdSetup = household
                    )
                }
            }
        }
    }
}
