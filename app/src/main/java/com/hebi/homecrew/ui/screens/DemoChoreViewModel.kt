package com.hebi.homecrew.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hebi.homecrew.data.local.HomeCrewDatabase
import com.hebi.homecrew.data.local.HomeCrewRoomRepository
import com.hebi.homecrew.data.local.HomeCrewSnapshot
import com.hebi.homecrew.ui.setup.HouseholdSetup
import com.hebi.homecrew.ui.setup.previewChoresForSetup
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface EditorSaveState {
    data object Idle : EditorSaveState
    data object Saving : EditorSaveState
    data class Success(val id: String) : EditorSaveState
    data class Failure(val message: String) : EditorSaveState
}

class DemoChoreViewModel(
    private val roomRepository: HomeCrewRoomRepository? = null
) : ViewModel() {
    val today: LocalDate = LocalDate.now()

    private val _chores = MutableStateFlow(
        if (roomRepository == null) sampleChores(today) else emptyList()
    )
    val chores: StateFlow<List<DemoChore>> = _chores.asStateFlow()

    private val _definitions = MutableStateFlow(
        if (roomRepository == null) choreDefinitions() else emptyList()
    )
    val definitions: StateFlow<List<ChoreDefinition>> = _definitions.asStateFlow()

    private val _editor = MutableStateFlow<ChoreEditorDraft?>(null)
    val editor: StateFlow<ChoreEditorDraft?> = _editor.asStateFlow()

    private val _configuredHousehold = MutableStateFlow<HouseholdSetup?>(null)
    val configuredHousehold: StateFlow<HouseholdSetup?> = _configuredHousehold.asStateFlow()

    private val _saveState = MutableStateFlow<EditorSaveState>(EditorSaveState.Idle)
    val saveState: StateFlow<EditorSaveState> = _saveState.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    private val _operationError = MutableStateFlow<String?>(null)
    val operationError: StateFlow<String?> = _operationError.asStateFlow()

    private var loadingHousehold: HouseholdSetup? = null
    private var observeJob: Job? = null
    private val pendingToggles = mutableSetOf<String>()
    private var committedSaveId: String? = null

    fun configureHousehold(setup: HouseholdSetup) {
        if (_configuredHousehold.value == setup || loadingHousehold == setup) return
        val repository = roomRepository
        if (repository == null) {
            val initial = previewChoresForSetup(setup, today)
            _chores.value = initial
            _definitions.value = initial.map { it.definition }.distinctBy { it.id }
            _configuredHousehold.value = setup
            return
        }
        loadingHousehold = setup
        _loadError.value = null
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            try {
                repository.initializeIfNeeded(setup, today)
                applySnapshot(repository.snapshot())
                _configuredHousehold.value = setup
                loadingHousehold = null
                repository.observe().collect(::applySnapshot)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                loadingHousehold = null
                _loadError.value = "저장된 집안일을 불러오지 못했습니다. 다시 시도해 주세요."
            }
        }
    }

    fun retryConfigureHousehold(setup: HouseholdSetup) {
        _configuredHousehold.value = null
        loadingHousehold = null
        configureHousehold(setup)
    }

    private fun applySnapshot(snapshot: HomeCrewSnapshot) {
        _definitions.value = snapshot.definitions
        _chores.value = snapshot.chores
    }

    fun dismissOperationError() {
        _operationError.value = null
    }

    fun openEditor(
        definition: ChoreDefinition?,
        members: List<String>,
        startDate: LocalDate? = null
    ) {
        _saveState.value = EditorSaveState.Idle
        committedSaveId = null
        _editor.value = if (definition == null) {
            ChoreEditorDraft(
                startDate = startDate ?: today,
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
        committedSaveId = null
        _saveState.value = EditorSaveState.Idle
    }

    fun saveEditorPersisted(members: List<String>) {
        if (_saveState.value is EditorSaveState.Saving ||
            _saveState.value is EditorSaveState.Success
        ) return
        val draft = _editor.value ?: return
        val error = validationError(draft, members)
        if (error != null) {
            _saveState.value = EditorSaveState.Failure(error)
            return
        }
        _saveState.value = EditorSaveState.Saving
        viewModelScope.launch {
            try {
                val repository = roomRepository
                val id = if (repository == null) {
                    saveEditor(members)
                } else {
                    val stableDraft = draft.copy(editingId = committedSaveId ?: draft.editingId)
                    repository.save(stableDraft, members, _chores.value, today).also {
                        committedSaveId = it
                    }
                }
                if (id == null) {
                    _saveState.value = EditorSaveState.Failure("집안일을 저장하지 못했습니다.")
                } else {
                    if (repository != null) applySnapshot(repository.snapshot())
                    _saveState.value = EditorSaveState.Success(id)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _saveState.value = EditorSaveState.Failure(
                    "기기 저장에 실패했습니다. 내용을 유지했으니 다시 시도해 주세요."
                )
            }
        }
    }

    fun acknowledgeEditorSave() {
        if (_saveState.value is EditorSaveState.Success) {
            discardDraft()
        } else if (_saveState.value is EditorSaveState.Failure) {
            _saveState.value = EditorSaveState.Idle
        }
    }

    suspend fun deleteDefinitionPersisted(id: String) {
        val repository = roomRepository
        if (repository == null) {
            deleteDefinition(id)
            return
        }
        repository.delete(id)
        applySnapshot(repository.snapshot())
    }

    fun saveEditor(members: List<String>): String? {
        check(roomRepository == null) { "로컬 저장 모드에서는 saveEditorPersisted를 사용해야 합니다." }
        val draft = _editor.value ?: return null
        if (validationError(draft, members) != null) return null
        val orderedDraft = draft.copy(assignment = draft.assignment.copy(
            members = orderedParticipants(draft.assignment.members, members)
        ))
        val id = draft.editingId ?: "custom-${UUID.randomUUID()}"
        val existing = _chores.value
        val baseline = workloadBaseline(existing, draft, today)
        val instances = previewDraftInstances(orderedDraft.copy(editingId = id), today, baseline)
        val retained = existing.filter { chore ->
            chore.definition.id != id || chore.completed || chore.date.isBefore(today)
        }
        val retainedIds = retained.map { it.id }.toSet()
        _chores.value = (retained + instances.filterNot { it.id in retainedIds })
            .sortedWith(compareBy({ it.date }, { it.definition.time }, { it.id }))
        val definition = instances.firstOrNull()?.definition ?: ChoreDefinition(
            id = id, name = draft.name.trim(), category = draft.category,
            owner = orderedDraft.assignment.members.first(),
            repeat = repeatDescription(draft.repeat), points = draft.points,
            time = draft.time, description = draft.description.trim(),
            recurrence = draft.repeat, assignment = orderedDraft.assignment, startDate = draft.startDate
        )
        _definitions.update { current ->
            if (current.any { it.id == id }) current.map { if (it.id == id) definition else it }
            else current + definition
        }
        discardDraft()
        return id
    }

    fun deleteDefinition(id: String) {
        check(roomRepository == null) { "로컬 저장 모드에서는 deleteDefinitionPersisted를 사용해야 합니다." }
        _definitions.update { current -> current.filterNot { it.id == id } }
        _chores.update { current -> current.filterNot { it.definition.id == id } }
    }

    fun toggleCompleted(id: String, actingMember: String = "나") {
        val repository = roomRepository
        if (repository == null) {
            _chores.update { current ->
                current.map { chore ->
                    if (chore.id == id) {
                        val nextCompleted = !chore.completed
                        chore.copy(
                            completed = nextCompleted,
                            completedAtEpochMillis = if (nextCompleted) System.currentTimeMillis() else null,
                            completedBy = if (nextCompleted) actingMember else null
                        )
                    } else chore
                }
            }
            return
        }
        if (!pendingToggles.add(id)) return
        viewModelScope.launch {
            try {
                repository.toggleCompleted(id, actingMember)
                applySnapshot(repository.snapshot())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _operationError.value = "완료 상태를 저장하지 못했습니다. 다시 시도해 주세요."
            } finally {
                pendingToggles.remove(id)
            }
        }
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DemoChoreViewModel::class.java))
            return DemoChoreViewModel(
                HomeCrewRoomRepository(HomeCrewDatabase.get(appContext).chores())
            ) as T
        }
    }
}
