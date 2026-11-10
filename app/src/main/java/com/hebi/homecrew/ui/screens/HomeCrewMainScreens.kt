package com.hebi.homecrew.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hebi.homecrew.ui.components.HomeCrewBottomBar
import com.hebi.homecrew.ui.components.HomeTab
import com.hebi.homecrew.ui.setup.HouseholdSetup
import com.hebi.homecrew.ui.setup.previewChoresForSetup
import com.hebi.homecrew.ui.theme.LocalHomeCrewColors
import com.hebi.homecrew.ui.theme.ThemeMode
import kotlinx.coroutines.launch

@Composable
fun HomeCrewMainScreens(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenDesignSystem: () -> Unit,
    householdSetup: HouseholdSetup? = null,
    viewModel: DemoChoreViewModel = viewModel()
) {
    val palette = LocalHomeCrewColors.current
    val chores by viewModel.chores.collectAsState()
    val configuredHousehold by viewModel.configuredHousehold.collectAsState()
    val currentDate = viewModel.today
    val memberNames = householdSetup?.members ?: listOf("나", "지민")

    LaunchedEffect(householdSetup) {
        householdSetup?.let(viewModel::configureHousehold)
    }

    if (householdSetup != null && configuredHousehold != householdSetup) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val saveableStateHolder = rememberSaveableStateHolder()

    var tab by rememberSaveable { mutableStateOf(HomeTab.HOME) }
    var currentMember by rememberSaveable { mutableStateOf(householdSetup?.defaultMember ?: "나") }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }

    val showAddInfo = {
        scope.launch {
            snackbar.showSnackbar("집안일 추가·편집은 2-A 단계에서 연결됩니다.")
        }
        Unit
    }

    Scaffold(
        containerColor = palette.background,
        snackbarHost = { SnackbarHost(hostState = snackbar) },
        bottomBar = {
            HomeCrewBottomBar(
                selectedTab = tab,
                onTabSelected = { tab = it }
            )
        }
    ) { contentPadding ->
        Box(Modifier.fillMaxSize().padding(contentPadding)) {
            saveableStateHolder.SaveableStateProvider(tab.name) {
                when (tab) {
                    HomeTab.HOME -> HomeCrewHomeScreen(
                        today = currentDate,
                        chores = chores,
                        currentMember = currentMember,
                        themeMode = themeMode,
                        onMemberSelected = { currentMember = it },
                        onToggle = viewModel::toggleCompleted,
                        onDetails = { detailId = it.id },
                        onAdd = showAddInfo,
                        onOpenDesignSystem = onOpenDesignSystem,
                        onCycleTheme = {
                            onThemeModeChange(ThemeMode.entries[(themeMode.ordinal + 1) % ThemeMode.entries.size])
                        },
                        householdName = householdSetup?.homeName ?: "우리 집",
                        members = memberNames
                    )
                    HomeTab.SCHEDULE -> HomeCrewScheduleScreen(
                        today = currentDate,
                        chores = chores,
                        onToggle = viewModel::toggleCompleted,
                        onDetails = { detailId = it.id }
                    )
                    HomeTab.CHORES -> HomeCrewChoresScreen(
                        today = currentDate,
                        chores = chores,
                        onDetails = { detailId = it.id },
                        onAdd = showAddInfo
                    )
                    HomeTab.STATS -> HomeCrewStatsScreen(
                        today = currentDate,
                        chores = chores,
                        members = memberNames
                    )
                }
            }
        }
    }

    DemoChoreDetails(
        task = chores.firstOrNull { it.id == detailId },
        onDismiss = { detailId = null }
    )
}
