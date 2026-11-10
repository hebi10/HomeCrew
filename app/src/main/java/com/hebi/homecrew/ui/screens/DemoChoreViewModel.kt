package com.hebi.homecrew.ui.screens

import androidx.lifecycle.ViewModel
import com.hebi.homecrew.ui.setup.HouseholdSetup
import com.hebi.homecrew.ui.setup.previewChoresForSetup
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class DemoChoreViewModel : ViewModel() {
    val today: LocalDate = LocalDate.now()

    private val _chores = MutableStateFlow(sampleChores(today))
    val chores: StateFlow<List<DemoChore>> = _chores.asStateFlow()

    private val _definitions = MutableStateFlow(choreDefinitions())
    val definitions: StateFlow<List<ChoreDefinition>> = _definitions.asStateFlow()

    private val _editor = MutableStateFlow<ChoreEditorDraft?>(null)
    val editor: StateFlow<ChoreEditorDraft?> = _editor.asStateFlow()

    private val _configuredHousehold = MutableStateFlow<HouseholdSetup?>(null)
    val configuredHousehold: StateFlow<HouseholdSetup?> = _configuredHousehold.asStateFlow()

    fun configureHousehold(setup: HouseholdSetup) {
        if (_configuredHousehold.value == setup) return
        val initial = previewChoresForSetup(setup, today)
        _chores.value = initial
        _definitions.value = initial.map { it.definition }.distinctBy { it.id }
        _configuredHousehold.value = setup
    }

    fun openEditor(definition: ChoreDefinition?, members: List<String>) {
        _editor.value = if (definition == null) {
            ChoreEditorDraft(
                startDate = today,
                assignment = ChoreAssignment(members = listOf(members.firstOrNull() ?: "나"))
            )
        } else {
            draftFromDefinition(definition, today, members)
        }
    }

    fun editDraft(change: (ChoreEditorDraft) -> ChoreEditorDraft) {
        _editor.update { current -> current?.let(change) }
    }

    fun discardDraft() {
        _editor.value = null
    }

    fun saveEditor(members: List<String>): String? {
        val draft = _editor.value ?: return null
        if (validationError(draft, members) != null) return null
        val id = draft.editingId ?: "custom-${UUID.randomUUID()}"
        val existing = _chores.value
        val baseline = workloadBaseline(existing, draft, today)
        val instances = previewDraftInstances(draft.copy(editingId = id), today, baseline)
        val retained = existing.filter { chore ->
            chore.definition.id != id || chore.completed || chore.date.isBefore(today)
        }
        val retainedIds = retained.map { it.id }.toSet()
        _chores.value = (retained + instances.filterNot { it.id in retainedIds })
            .sortedWith(compareBy({ it.date }, { it.definition.time }, { it.id }))
        val definition = instances.firstOrNull()?.definition ?: ChoreDefinition(
            id = id, name = draft.name.trim(), category = draft.category,
            owner = draft.assignment.members.first(),
            repeat = repeatDescription(draft.repeat), points = draft.points,
            time = draft.time, description = draft.description.trim(),
            recurrence = draft.repeat, assignment = draft.assignment, startDate = draft.startDate
        )
        _definitions.update { current ->
            if (current.any { it.id == id }) current.map { if (it.id == id) definition else it }
            else current + definition
        }
        discardDraft()
        return id
    }

    fun deleteDefinition(id: String) {
        _definitions.update { current -> current.filterNot { it.id == id } }
        _chores.update { current -> current.filterNot { it.definition.id == id } }
    }

    fun toggleCompleted(id: String) {
        _chores.update { current ->
            current.map { chore ->
                if (chore.id == id) chore.copy(completed = !chore.completed) else chore
            }
        }
    }
}
