package com.hebi.homecrew.ui.screens

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

class DemoChoreViewModel : ViewModel() {
    val today: LocalDate = LocalDate.now()

    private val _chores = MutableStateFlow(sampleChores(today))
    val chores: StateFlow<List<DemoChore>> = _chores.asStateFlow()

    fun toggleCompleted(id: String) {
        _chores.update { current ->
            current.map { chore ->
                if (chore.id == id) chore.copy(completed = !chore.completed) else chore
            }
        }
    }
}
