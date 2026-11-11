package com.hebi.homecrew.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hebi.homecrew.ui.components.HomeCrewBottomBar
import com.hebi.homecrew.ui.components.HomeTab
import com.hebi.homecrew.ui.setup.HouseholdSetup
import com.hebi.homecrew.ui.theme.LocalHomeCrewColors
import com.hebi.homecrew.ui.theme.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class ChorePage { MAIN, DETAIL, FORM, REPEAT, ASSIGNMENT }

@Composable
fun HomeCrewMainScreens(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    householdSetup: HouseholdSetup? = null,
    viewModel: DemoChoreViewModel = viewModel(
        factory = DemoChoreViewModel.Factory(LocalContext.current.applicationContext)
    )
) {
    val palette = LocalHomeCrewColors.current
    val chores by viewModel.chores.collectAsState()
    val definitions by viewModel.definitions.collectAsState()
    val editor by viewModel.editor.collectAsState()
    val configuredHousehold by viewModel.configuredHousehold.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val operationError by viewModel.operationError.collectAsState()
    val saveState by viewModel.saveState.collectAsState()
    val currentDate = viewModel.today
    val memberNames = householdSetup?.members ?: listOf("나", "지민")

    LaunchedEffect(householdSetup) {
        householdSetup?.let(viewModel::configureHousehold)
    }
    if (householdSetup != null && configuredHousehold != householdSetup) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (loadError == null) {
                CircularProgressIndicator()
            } else {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(loadError.orEmpty())
                    TextButton(onClick = { viewModel.retryConfigureHousehold(householdSetup) }) {
                        Text("다시 시도")
                    }
                }
            }
        }
        return
    }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val saveableStateHolder = rememberSaveableStateHolder()

    var tab by rememberSaveable { mutableStateOf(HomeTab.HOME) }
    var currentMember by rememberSaveable {
        mutableStateOf(householdSetup?.defaultMember ?: "나")
    }
    var page by rememberSaveable { mutableStateOf(ChorePage.MAIN) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedOccurrenceId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorOrigin by rememberSaveable { mutableStateOf(ChorePage.MAIN) }
    var initialEditor by remember { mutableStateOf<ChoreEditorDraft?>(null) }
    var formError by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var pageError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(saveState) {
        when (val state = saveState) {
            is EditorSaveState.Success -> {
                detailId = state.id
                selectedOccurrenceId = null
                page = ChorePage.DETAIL
                formError = null
                viewModel.acknowledgeEditorSave()
            }
            is EditorSaveState.Failure -> {
                formError = state.message
                viewModel.acknowledgeEditorSave()
            }
            else -> Unit
        }
    }

    LaunchedEffect(page, editor) {
        if (page in listOf(ChorePage.FORM, ChorePage.REPEAT, ChorePage.ASSIGNMENT)
            && editor == null
        ) {
            page = ChorePage.MAIN
            formError = null
            confirmDiscard = false
        }
    }

    fun finishEditor() {
        viewModel.discardDraft()
        formError = null
        initialEditor = null
        page = editorOrigin
    }

    fun requestFormBack() {
        if (saveState is EditorSaveState.Saving) return
        if (editor != null && editor != initialEditor) confirmDiscard = true
        else finishEditor()
    }

    fun openAdd(startDate: java.time.LocalDate? = null, preferredMember: String? = null) {
        val member = preferredMember?.takeIf { it in memberNames } ?: currentMember
        viewModel.openEditor(
            null, listOf(member) + memberNames.filter { it != member }, startDate
        )
        initialEditor = viewModel.editor.value
        editorOrigin = ChorePage.MAIN
        formError = null
        page = ChorePage.FORM
    }

    fun openDetails(task: DemoChore) {
        detailId = task.definition.id
        selectedOccurrenceId = task.id
        page = ChorePage.DETAIL
    }

    fun openEdit() {
        val definition = definitions.firstOrNull { it.id == detailId } ?: return
        viewModel.openEditor(definition, memberNames)
        initialEditor = viewModel.editor.value
        editorOrigin = ChorePage.DETAIL
        formError = null
        page = ChorePage.FORM
    }

    fun back() {
        if (saveState is EditorSaveState.Saving) return
        when (page) {
            ChorePage.REPEAT, ChorePage.ASSIGNMENT -> page = ChorePage.FORM
            ChorePage.FORM -> requestFormBack()
            ChorePage.DETAIL -> page = ChorePage.MAIN
            ChorePage.MAIN -> Unit
        }
    }

    BackHandler(enabled = page != ChorePage.MAIN, onBack = ::back)

    when (page) {
        ChorePage.MAIN -> {
            Scaffold(
                containerColor = palette.background,
                snackbarHost = { SnackbarHost(hostState = snackbar) },
                bottomBar = {
                    HomeCrewBottomBar(selectedTab = tab, onTabSelected = { tab = it })
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
                                onToggle = { id -> viewModel.toggleCompleted(id, currentMember) },
                                onDetails = ::openDetails,
                                onAdd = { openAdd() },
                                onCycleTheme = {
                                    onThemeModeChange(
                                        ThemeMode.entries[(themeMode.ordinal + 1) % ThemeMode.entries.size]
                                    )
                                },
                                householdName = householdSetup?.homeName ?: "우리 집",
                                members = memberNames
                            )
                            HomeTab.SCHEDULE -> HomeCrewScheduleScreen(
                                today = currentDate,
                                chores = chores,
                                members = memberNames,
                                onToggle = { id -> viewModel.toggleCompleted(id, currentMember) },
                                onDetails = ::openDetails,
                                onAddForDate = { date, member -> openAdd(date, member) }
                            )
                            HomeTab.CHORES -> HomeCrewChoresScreen(
                                today = currentDate,
                                chores = chores,
                                onDetails = { definition, occurrenceId ->
                                    detailId = definition.id
                                    selectedOccurrenceId = occurrenceId
                                    page = ChorePage.DETAIL
                                },
                                onAdd = { openAdd() },
                                definitions = definitions,
                                members = memberNames
                            )
                            HomeTab.STATS -> HomeCrewStatsScreen(
                                today = currentDate, chores = chores, members = memberNames
                            )
                        }
                    }
                }
            }
        }
        ChorePage.DETAIL -> {
            val definition = definitions.firstOrNull { it.id == detailId }
            if (definition != null) {
                HomeCrewChoreDetailScreen(
                    definition = definition,
                    chores = chores,
                    today = currentDate,
                    selectedOccurrenceId = selectedOccurrenceId,
                    onBack = { page = ChorePage.MAIN },
                    onEdit = ::openEdit,
                    onToggle = { id -> viewModel.toggleCompleted(id, currentMember) },
                    onDelete = {
                        scope.launch {
                            try {
                                viewModel.deleteDefinitionPersisted(definition.id)
                                detailId = null
                                selectedOccurrenceId = null
                                tab = HomeTab.CHORES
                                page = ChorePage.MAIN
                                snackbar.showSnackbar("집안일과 관련 기록을 기기에서 삭제했습니다.")
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                pageError = "집안일을 삭제하지 못했습니다. 다시 시도해 주세요."
                            }
                        }
                    }
                )
            } else {
                TextButton(onClick = { page = ChorePage.MAIN }) {
                    Text("집안일을 찾을 수 없습니다. 목록으로 돌아가기")
                }
            }
        }
        ChorePage.FORM -> {
            val draft = editor
            if (draft != null) {
                HomeCrewChoreFormScreen(
                    draft = draft,
                    today = currentDate,
                    members = memberNames,
                    onChange = { updated ->
                        viewModel.editDraft { updated }
                        formError = null
                    },
                    onRepeat = { page = ChorePage.REPEAT },
                    onAssignment = { page = ChorePage.ASSIGNMENT },
                    onSave = {
                        if (saveState !is EditorSaveState.Saving) {
                            val error = validationError(draft, memberNames)
                            if (error != null) {
                                formError = error
                            } else {
                                formError = null
                                viewModel.saveEditorPersisted(memberNames)
                            }
                        }
                    },
                    onBack = ::requestFormBack,
                    error = formError,
                    baseline = workloadBaseline(chores, draft, currentDate)
                )
            }
        }
        ChorePage.REPEAT -> {
            val draft = editor
            if (draft != null) {
                HomeCrewRepeatScreen(
                    initial = draft.repeat,
                    startDate = draft.startDate,
                    onApply = { repeat ->
                        viewModel.editDraft { it.copy(repeat = repeat) }
                        formError = null
                        page = ChorePage.FORM
                    },
                    onBack = { page = ChorePage.FORM }
                )
            }
        }
        ChorePage.ASSIGNMENT -> {
            val draft = editor
            if (draft != null) {
                val baseline = workloadBaseline(chores, draft, currentDate)
                HomeCrewAssignmentScreen(
                    initial = draft.assignment,
                    members = memberNames,
                    points = draft.points,
                    baseline = baseline,
                    onApply = { assignment ->
                        viewModel.editDraft { it.copy(assignment = assignment) }
                        formError = null
                        page = ChorePage.FORM
                    },
                    onBack = { page = ChorePage.FORM }
                )
            }
        }
    }

    if (pageError != null || operationError != null) {
        AlertDialog(
            onDismissRequest = {
                pageError = null
                viewModel.dismissOperationError()
            },
            title = { Text("작업 처리 오류") },
            text = { Text(pageError ?: operationError.orEmpty()) },
            confirmButton = {
                TextButton(onClick = {
                    pageError = null
                    viewModel.dismissOperationError()
                }) { Text("확인") }
            }
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("작성 내용을 취소할까요?") },
            text = { Text("저장하지 않은 집안일 변경사항이 사라집니다.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    finishEditor()
                }) { Text("변경사항 버리기", color = palette.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("계속 작성") }
            }
        )
    }
}
