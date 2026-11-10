package com.hebi.homecrew.ui.screens

import androidx.lifecycle.ViewModel
import com.hebi.homecrew.ui.setup.HouseholdSetup
import com.hebi.homecrew.ui.setup.previewChoresForSetup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

class DemoChoreViewModel : ViewModel() {
    val today: LocalDate = LocalDate.now()

    private val _chores = MutableStateFlow(sampleChores(today))
    val chores: StateFlow<List<DemoChore>> = _chores.asStateFlow()

    private val _configuredHousehold = MutableStateFlow<HouseholdSetup?>(null)
    val configuredHousehold: StateFlow<HouseholdSetup?> = _configuredHousehold.asStateFlow()

    fun configureHousehold(setup: HouseholdSetup) {
        if (_configuredHousehold.value == setup) return
        _chores.value = previewChoresForSetup(setup, today)
        _configuredHousehold.value = setup
    }

    fun toggleCompleted(id: String) {
        _chores.update { current ->
            current.map { chore ->
                if (chore.id == id) chore.copy(completed = !chore.completed) else chore
            }
        }
    }
}
