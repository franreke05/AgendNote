package com.franciscor.agendnote

import com.franciscor.agendnote.core.model.LabelTag
import com.franciscor.agendnote.core.model.TaskDraft
import com.franciscor.agendnote.core.model.TaskItem
import com.franciscor.agendnote.feature.agenda.domain.AgendaTaskRepository
import com.franciscor.agendnote.feature.agenda.presentation.viewmodel.AgendaViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AgendaViewModelTest {
    private val timeZone = TimeZone.UTC
    private val baseDate = LocalDate(2026, 3, 11)

    @Test
    fun `loadTasksForDate writes results into the requested date`() = runTest {
        val targetDate = LocalDate(2026, 3, 12)
        val expected = listOf(task("t-1", "Revision"))
        val repository = FakeAgendaTaskRepository(
            fetchTasksHandler = { date ->
                assertEquals(targetDate, date)
                expected
            },
        )
        val viewModel = AgendaViewModel(repository, timeZone = timeZone, initialDate = baseDate)

        viewModel.selectDate(targetDate)
        viewModel.loadTasksForDate(targetDate)

        assertEquals(expected, viewModel.uiState.tasksByDate[targetDate])
        assertNull(viewModel.uiState.errorByDate[targetDate])
        assertFalse(viewModel.dayUiState(targetDate).isLoading)
    }

    @Test
    fun `loading a visited date keeps cache visible while refreshing`() = runTest {
        val refreshedTasks = listOf(task("t-2", "Sincronizada", hour = 10))
        val secondFetch = CompletableDeferred<List<TaskItem>>()
        var callCount = 0
        val repository = FakeAgendaTaskRepository(
            fetchTasksHandler = {
                callCount += 1
                if (callCount == 1) {
                    listOf(task("t-1", "Cache local", hour = 9))
                } else {
                    secondFetch.await()
                }
            },
        )
        val viewModel = AgendaViewModel(repository, timeZone = timeZone, initialDate = baseDate)

        viewModel.loadTasksForDate(baseDate)
        val refreshJob = async { viewModel.loadTasksForDate(baseDate) }
        runCurrent()

        val refreshingState = viewModel.dayUiState(baseDate)
        assertTrue(refreshingState.hasCachedTasks)
        assertTrue(refreshingState.shouldShowRefreshing)
        assertEquals(listOf(task("t-1", "Cache local", hour = 9)), refreshingState.tasks)

        secondFetch.complete(refreshedTasks)
        advanceUntilIdle()
        refreshJob.await()

        assertEquals(refreshedTasks, viewModel.uiState.tasksByDate[baseDate])
        assertFalse(viewModel.dayUiState(baseDate).isLoading)
    }

    @Test
    fun `fast day switches do not mix date buckets`() = runTest {
        val nextDate = LocalDate(2026, 3, 12)
        val firstDayDeferred = CompletableDeferred<List<TaskItem>>()
        val secondDayDeferred = CompletableDeferred<List<TaskItem>>()
        val repository = FakeAgendaTaskRepository(
            fetchTasksHandler = { date ->
                when (date) {
                    baseDate -> firstDayDeferred.await()
                    nextDate -> secondDayDeferred.await()
                    else -> emptyList()
                }
            },
        )
        val viewModel = AgendaViewModel(repository, timeZone = timeZone, initialDate = baseDate)

        val firstLoad = async { viewModel.loadTasksForDate(baseDate) }
        runCurrent()
        viewModel.selectDate(nextDate)
        val secondLoad = async { viewModel.loadTasksForDate(nextDate) }
        runCurrent()

        secondDayDeferred.complete(listOf(task("t-2", "Segundo dia", hour = 12)))
        firstDayDeferred.complete(listOf(task("t-1", "Primer dia", hour = 8)))
        advanceUntilIdle()
        firstLoad.await()
        secondLoad.await()

        assertEquals(nextDate, viewModel.uiState.selectedDate)
        assertEquals(listOf(task("t-1", "Primer dia", hour = 8)), viewModel.uiState.tasksByDate[baseDate])
        assertEquals(listOf(task("t-2", "Segundo dia", hour = 12)), viewModel.uiState.tasksByDate[nextDate])
    }

    @Test
    fun `error with cache keeps previous tasks and marks the day error`() = runTest {
        var shouldFail = false
        val cachedTasks = listOf(task("t-1", "Cache", hour = 9))
        val repository = FakeAgendaTaskRepository(
            fetchTasksHandler = {
                if (shouldFail) error("boom")
                cachedTasks
            },
        )
        val viewModel = AgendaViewModel(repository, timeZone = timeZone, initialDate = baseDate)

        viewModel.loadTasksForDate(baseDate)
        shouldFail = true
        viewModel.loadTasksForDate(baseDate)

        val dayState = viewModel.dayUiState(baseDate)
        assertEquals(cachedTasks, dayState.tasks)
        assertEquals("No se pudieron cargar las tareas", dayState.errorMessage)
        assertFalse(dayState.shouldShowEmptyState)
    }

    @Test
    fun `error without cache never exposes empty state during loading or error`() = runTest {
        val deferred = CompletableDeferred<List<TaskItem>>()
        val repository = FakeAgendaTaskRepository(fetchTasksHandler = { deferred.await() })
        val viewModel = AgendaViewModel(repository, timeZone = timeZone, initialDate = baseDate)

        val loadJob = async { viewModel.loadTasksForDate(baseDate) }
        runCurrent()

        val loadingState = viewModel.dayUiState(baseDate)
        assertTrue(loadingState.shouldShowInitialLoader)
        assertFalse(loadingState.shouldShowEmptyState)

        deferred.completeExceptionally(IllegalStateException("boom"))
        advanceUntilIdle()
        loadJob.await()

        val errorState = viewModel.dayUiState(baseDate)
        assertEquals("No se pudieron cargar las tareas", errorState.errorMessage)
        assertFalse(errorState.shouldShowEmptyState)
    }

    @Test
    fun `latest concurrent refresh wins for the same date`() = runTest {
        val firstResponse = CompletableDeferred<List<TaskItem>>()
        val secondResponse = CompletableDeferred<List<TaskItem>>()
        var callCount = 0
        val repository = FakeAgendaTaskRepository(
            fetchTasksHandler = {
                callCount += 1
                if (callCount == 1) firstResponse.await() else secondResponse.await()
            },
        )
        val viewModel = AgendaViewModel(repository, timeZone = timeZone, initialDate = baseDate)

        val firstLoad = async { viewModel.loadTasksForDate(baseDate) }
        runCurrent()
        val secondLoad = async { viewModel.loadTasksForDate(baseDate) }
        runCurrent()

        firstResponse.complete(listOf(task("t-1", "Respuesta antigua", hour = 9)))
        runCurrent()
        secondResponse.complete(listOf(task("t-2", "Respuesta vigente", hour = 10)))
        advanceUntilIdle()
        firstLoad.await()
        secondLoad.await()

        assertEquals(
            listOf(task("t-2", "Respuesta vigente", hour = 10)),
            viewModel.uiState.tasksByDate[baseDate],
        )
        assertFalse(viewModel.dayUiState(baseDate).isLoading)
    }

    @Test
    fun `clearLabelsFromTasks removes embedded labels from cached agenda`() = runTest {
        val labeledTask = task(
            id = "t-1",
            title = "Con etiqueta",
            labels = listOf(LabelTag("l-1", "Urgente", "#FF0000")),
        )
        val repository = FakeAgendaTaskRepository(fetchTasksHandler = { listOf(labeledTask) })
        val viewModel = AgendaViewModel(repository, timeZone = timeZone, initialDate = baseDate)

        viewModel.loadTasksForDate(baseDate)
        viewModel.clearLabelsFromTasks()

        assertTrue(viewModel.uiState.tasksByDate[baseDate].orEmpty().all { it.labels.isEmpty() })
    }

    @Test
    fun `loadTasksForDate without remote repository exposes config error`() = runTest {
        val viewModel = AgendaViewModel(
            repository = null,
            timeZone = timeZone,
            remoteUnavailableMessage = "Falta APP_SECRET",
            initialDate = baseDate,
        )

        viewModel.loadTasksForDate(baseDate)

        assertEquals("Falta APP_SECRET", viewModel.dayUiState(baseDate).errorMessage)
        assertFalse(viewModel.uiState.isRemoteAvailable)
    }

    @Test
    fun `saveTask without remote repository does not mutate local state`() = runTest {
        val viewModel = AgendaViewModel(
            repository = null,
            timeZone = timeZone,
            remoteUnavailableMessage = "Falta APP_SECRET",
            initialDate = baseDate,
        )

        val result = viewModel.saveTask(
            date = baseDate,
            draft = TaskDraft(title = "Nueva", details = null, time = null, labels = emptyList()),
        )

        assertFalse(result.success)
        assertEquals("Falta APP_SECRET", result.errorMessage)
        assertTrue(viewModel.uiState.tasksByDate[baseDate].isNullOrEmpty())
    }

    private fun task(
        id: String,
        title: String,
        hour: Int? = null,
        labels: List<LabelTag> = emptyList(),
    ): TaskItem {
        return TaskItem(
            id = id,
            title = title,
            details = null,
            time = hour?.let { LocalTime(it, 0) },
            labels = labels,
        )
    }
}

private class FakeAgendaTaskRepository(
    private val fetchTasksHandler: suspend (LocalDate) -> List<TaskItem> = { emptyList() },
    private val fetchTasksInRangeHandler: suspend (LocalDate, LocalDate) -> Map<LocalDate, List<TaskItem>> =
        { _, _ -> emptyMap() },
) : AgendaTaskRepository {
    override suspend fun fetchTasks(date: LocalDate): List<TaskItem> = fetchTasksHandler(date)

    override suspend fun fetchTasksInRange(from: LocalDate, to: LocalDate): Map<LocalDate, List<TaskItem>> =
        fetchTasksInRangeHandler(from, to)

    override suspend fun createTask(date: LocalDate, draft: TaskDraft): TaskItem {
        return TaskItem(
            id = "created-${date.dayOfMonth}",
            title = draft.title,
            details = draft.details,
            time = draft.time,
            labels = draft.labels,
        )
    }

    override suspend fun updateTaskDone(id: String, isDone: Boolean): TaskItem {
        return TaskItem(
            id = id,
            title = "updated",
            details = null,
            time = null,
            labels = emptyList(),
            isDone = isDone,
        )
    }

    override suspend fun deleteTask(id: String): Boolean = true

    override suspend fun deleteAllTasks(): Boolean = true
}
