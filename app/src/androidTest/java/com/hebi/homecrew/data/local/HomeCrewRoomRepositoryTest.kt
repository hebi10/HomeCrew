package com.hebi.homecrew.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hebi.homecrew.ui.screens.ChoreAssignment
import com.hebi.homecrew.ui.screens.ChoreAssignmentMode
import com.hebi.homecrew.ui.screens.ChoreEditorDraft
import com.hebi.homecrew.ui.screens.ChoreRepeat
import com.hebi.homecrew.ui.screens.ChoreRepeatMode
import com.hebi.homecrew.ui.screens.DemoChoreViewModel
import com.hebi.homecrew.ui.screens.EditorSaveState
import com.hebi.homecrew.ui.setup.HouseholdSetup
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeCrewRoomRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fileName = "homecrew-2a02-reopen-test.db"
    private val today = LocalDate.of(2026, 11, 10)
    private val members = listOf("나", "지민", "민수")
    private lateinit var database: HomeCrewDatabase
    private lateinit var repository: HomeCrewRoomRepository

    @Before
    fun setUp() {
        context.deleteDatabase(fileName)
        openDatabase()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(fileName)
    }

    private fun openDatabase() {
        database = Room.databaseBuilder(context, HomeCrewDatabase::class.java, fileName).build()
        repository = HomeCrewRoomRepository(database.chores()) { 12345L }
    }

    private fun reopenDatabase() {
        database.close()
        openDatabase()
    }

    private fun setup(templates: List<String>) = HouseholdSetup(
        homeName = "우리 집",
        members = members,
        defaultMember = "나",
        selectedTemplateIds = templates
    )

    @Test
    fun seedRunsOnceAndCompletionSurvivesDatabaseReopen() = runBlocking {
        val household = setup(listOf("dishes", "recycle"))
        repository.initializeIfNeeded(household, today)
        val initial = repository.snapshot()
        assertEquals(2, initial.definitions.size)
        assertTrue(initial.chores.size > 2)

        val dishes = initial.chores.first { it.definition.id == "dishes" && it.date == today }
        repository.toggleCompleted(dishes.id, "지민")
        reopenDatabase()
        repository.initializeIfNeeded(household, today)

        val restored = repository.snapshot()
        assertEquals(2, restored.definitions.size)
        assertEquals(initial.chores.size, restored.chores.size)
        val completedOccurrence = restored.chores.single { it.id == dishes.id }
        assertTrue(completedOccurrence.completed)
        assertEquals("지민", completedOccurrence.completedBy)
        assertNotNull(completedOccurrence.completedAtEpochMillis)
        assertEquals("지민", database.chores().findOccurrence(dishes.id)?.completedBy)
        assertNotNull(database.chores().findOccurrence(dishes.id)?.completedAtEpochMillis)

        repository.toggleCompleted(dishes.id, "지민")
        reopenDatabase()
        val resetOccurrence = repository.snapshot().chores.single { it.id == dishes.id }
        assertFalse(resetOccurrence.completed)
        assertNull(resetOccurrence.completedBy)
        assertNull(resetOccurrence.completedAtEpochMillis)
        assertNull(database.chores().findOccurrence(dishes.id)?.completedBy)
    }

    @Test
    fun newTaskRotationAndEditedHistorySurviveRestarts() = runBlocking {
        val household = setup(emptyList())
        repository.initializeIfNeeded(household, today)
        val draft = ChoreEditorDraft(
            name = "복원 확인",
            category = "주방",
            startDate = today,
            repeat = ChoreRepeat(ChoreRepeatMode.DAILY),
            assignment = ChoreAssignment(
                ChoreAssignmentMode.ROUND_ROBIN,
                listOf("민수", "지민")
            )
        )
        val id = repository.save(draft, members, repository.snapshot().chores, today)

        reopenDatabase()
        var restored = repository.snapshot()
        assertEquals(listOf("지민", "민수"), restored.definitions.single().assignment?.members)
        assertEquals(
            listOf("지민", "민수", "지민"),
            (0L..2L).map { offset ->
                restored.chores.single {
                    it.definition.id == id && it.date == today.plusDays(offset)
                }.definition.owner
            }
        )

        val completedId = restored.chores.first { it.date == today }.id
        repository.toggleCompleted(completedId, "민수")
        val updated = draft.copy(editingId = id, name = "복원 확인 수정", points = 5)
        repository.save(updated, members, repository.snapshot().chores, today)

        reopenDatabase()
        restored = repository.snapshot()
        assertEquals("복원 확인 수정", restored.definitions.single().name)
        assertTrue(restored.chores.single { it.id == completedId }.completed)
        assertEquals(2, restored.chores.single { it.id == completedId }.definition.points)
        assertEquals(5, restored.chores.single { it.date == today.plusDays(1) }.definition.points)
        assertEquals("민수", database.chores().findOccurrence(completedId)?.completedBy)

        repository.delete(id)
        reopenDatabase()
        repository.initializeIfNeeded(household, today)
        assertTrue(repository.snapshot().definitions.isEmpty())
        assertTrue(repository.snapshot().chores.isEmpty())
    }

    @Test
    fun restartAfterInitialHorizonExtendsRotationWithoutOverwritingCompletion() = runBlocking {
        val household = setup(emptyList())
        repository.initializeIfNeeded(household, today)
        val id = repository.save(
            ChoreEditorDraft(
                name = "매일 순번",
                category = "청소",
                startDate = today,
                repeat = ChoreRepeat(ChoreRepeatMode.DAILY),
                assignment = ChoreAssignment(
                    ChoreAssignmentMode.ROUND_ROBIN, listOf("민수", "지민")
                )
            ),
            members,
            emptyList(),
            today
        )
        val dayZero = repository.snapshot().chores.first { it.definition.id == id && it.date == today }
        repository.toggleCompleted(dayZero.id, "나")

        reopenDatabase()
        repository.initializeIfNeeded(household, today.plusDays(75))
        val restored = repository.snapshot().chores.filter { it.definition.id == id }
        assertTrue(restored.any { it.date == today.plusDays(137) })
        assertEquals(
            "민수",
            restored.single { it.date == today.plusDays(75) }.definition.owner
        )
        assertTrue(restored.single { it.id == dayZero.id }.completed)
        assertEquals("나", database.chores().findOccurrence(dayZero.id)?.completedBy)
    }

    @Test
    fun farFutureOneTimeTaskAppearsWhenRestartEntersPlanningHorizon() = runBlocking {
        val household = setup(emptyList())
        repository.initializeIfNeeded(household, today)
        val futureDate = today.plusDays(80)
        val id = repository.save(
            ChoreEditorDraft(
                name = "80일 후",
                category = "생활",
                startDate = futureDate,
                assignment = ChoreAssignment(members = listOf("나"))
            ),
            members,
            emptyList(),
            today
        )
        assertTrue(repository.snapshot().chores.none { it.definition.id == id })

        reopenDatabase()
        repository.initializeIfNeeded(household, today.plusDays(30))
        val occurrences = repository.snapshot().chores.filter { it.definition.id == id }
        assertEquals(listOf(futureDate), occurrences.map { it.date })
    }

    @Test
    fun viewModelOwnsOneSaveWhileUiRecreates() = runBlocking {
        val household = setup(emptyList())
        val first = DemoChoreViewModel(repository)
        first.configureHousehold(household)
        withTimeout(10_000) { first.configuredHousehold.filterNotNull().first() }
        first.openEditor(null, members)
        first.editDraft {
            it.copy(
                name = "ViewModel 복원",
                category = "청소",
                repeat = ChoreRepeat(ChoreRepeatMode.DAILY)
            )
        }

        first.saveEditorPersisted(members)
        first.saveEditorPersisted(members)
        val saved = withTimeout(20_000) {
            first.saveState.filterIsInstance<EditorSaveState.Success>().first()
        }
        assertEquals(1, repository.snapshot().definitions.count { it.id == saved.id })

        val recreated = DemoChoreViewModel(repository)
        recreated.configureHousehold(household)
        withTimeout(10_000) { recreated.configuredHousehold.filterNotNull().first() }
        assertEquals(1, recreated.definitions.value.count { it.id == saved.id })
        assertEquals(1, recreated.chores.value.count { it.date == first.today && it.definition.id == saved.id })

        first.acknowledgeEditorSave()
        assertNull(first.editor.value)
        assertTrue(first.saveState.value is EditorSaveState.Idle)
    }


    @Test
    fun skippedOccurrenceSurvivesEditAndDatabaseReopen() = runBlocking {
        val household = setup(emptyList())
        repository.initializeIfNeeded(household, today)
        val id = repository.save(
            ChoreEditorDraft(
                name = "쉬는 날",
                startDate = today,
                repeat = ChoreRepeat(ChoreRepeatMode.DAILY),
                assignment = ChoreAssignment(members = listOf("나"))
            ),
            members,
            emptyList(),
            today
        )
        val instance = repository.snapshot().chores.first { it.definition.id == id }
        repository.toggleSkipped(instance.id, "출장")
        assertTrue(repository.snapshot().chores.single { it.id == instance.id }.skipped)
        repository.save(
            ChoreEditorDraft(
                editingId = id,
                name = "수정된 쉬는 날",
                startDate = today,
                repeat = ChoreRepeat(ChoreRepeatMode.DAILY),
                assignment = ChoreAssignment(members = listOf("나"))
            ),
            members,
            repository.snapshot().chores,
            today
        )
        reopenDatabase()
        val restored = repository.snapshot().chores.single { it.id == instance.id }
        assertTrue(restored.skipped)
        assertEquals("출장", restored.skipReason)
        assertFalse(restored.completed)
        repository.toggleSkipped(instance.id, null)
        assertFalse(repository.snapshot().chores.single { it.id == instance.id }.skipped)
        repository.toggleSkipped(instance.id, "다음에")
        repository.toggleCompleted(instance.id, "나")
        val completed = repository.snapshot().chores.single { it.id == instance.id }
        assertTrue(completed.completed)
        assertFalse(completed.skipped)
        assertNull(completed.skipReason)
    }

    @Test
    fun dateRolloverExtendsHorizonWithoutRestart() = runBlocking {
        val household = setup(listOf("dishes"))
        val vm = DemoChoreViewModel(repository)
        vm.configureHousehold(household)
        withTimeout(10_000) { vm.configuredHousehold.filterNotNull().first() }
        val next = vm.today.plusDays(40)
        vm.refreshDate(next)
        withTimeout(10_000) { vm.todayState.first { it == next } }
        assertTrue(repository.snapshot().chores.any {
            it.definition.id == "dishes" && it.date == next.plusDays(62)
        })
    }

    @Test
    fun monthlyLeapDayAndOneTimeSeedMarkerPersist() = runBlocking {
        val household = setup(emptyList())
        val anchor = LocalDate.of(2028, 1, 31)
        repository.initializeIfNeeded(household, anchor)
        val id = repository.save(
            ChoreEditorDraft(
                name = "월말 정리",
                category = "생활",
                startDate = anchor,
                repeat = ChoreRepeat(ChoreRepeatMode.MONTHLY),
                assignment = ChoreAssignment(members = listOf("나"))
            ),
            members,
            emptyList(),
            anchor
        )
        reopenDatabase()

        val restored = repository.snapshot()
        assertEquals(
            listOf(LocalDate.of(2028, 1, 31), LocalDate.of(2028, 2, 29), LocalDate.of(2028, 3, 31)),
            restored.chores.filter { it.definition.id == id }.take(3).map { it.date }
        )
        repository.delete(id)
        reopenDatabase()
        repository.initializeIfNeeded(setup(listOf("dishes")), anchor)
        assertTrue(repository.snapshot().definitions.isEmpty())
    }
}
