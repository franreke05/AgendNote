package com.franciscor.agendnote.feature.agenda.presentation.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.franciscor.agendnote.core.model.TaskDraft
import com.franciscor.agendnote.core.model.TaskItem
import com.franciscor.agendnote.feature.agenda.domain.AgendaTaskRepository
import com.franciscor.agendnote.feature.agenda.presentation.model.AgendaDayUiState
import com.franciscor.agendnote.feature.agenda.presentation.model.AgendaUiState
import com.franciscor.agendnote.feature.agenda.presentation.model.SaveResult
import io.ktor.client.plugins.ResponseException
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

class AgendaViewModel(
    private val repository: AgendaTaskRepository?,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    initialDate: LocalDate = Clock.System.todayIn(timeZone),
) {
    var uiState by mutableStateOf(AgendaUiState(selectedDate = initialDate))
        private set

    private var nextLoadToken: Long = 0
    private val activeLoadTokenByDate = mutableMapOf<LocalDate, Long>()

    fun moveDay(delta: Int): LocalDate {
        val date = uiState.selectedDate.plus(delta, DateTimeUnit.DAY)
        selectDate(date)
        return date
    }

    fun selectDate(date: LocalDate) {
        uiState = uiState.copy(selectedDate = date)
    }

    fun selectedDayUiState(): AgendaDayUiState = dayUiState(uiState.selectedDate)

    fun today(): LocalDate = Clock.System.todayIn(timeZone)

    fun dayUiState(date: LocalDate): AgendaDayUiState {
        return AgendaDayUiState(
            date = date,
            tasks = uiState.tasksByDate[date].orEmpty(),
            hasCachedTasks = uiState.tasksByDate.containsKey(date),
            isLoading = uiState.loadingByDate[date] == true,
            errorMessage = uiState.errorByDate[date],
        )
    }

    fun setTasks(date: LocalDate, tasks: List<TaskItem>) {
        uiState = uiState.copy(tasksByDate = uiState.tasksByDate + (date to orderTasks(tasks)))
    }

    fun clearTasks(date: LocalDate) {
        uiState = uiState.copy(tasksByDate = uiState.tasksByDate - date)
    }

    fun setLoading(date: LocalDate, isLoading: Boolean) {
        uiState = if (isLoading) {
            uiState.copy(loadingByDate = uiState.loadingByDate + (date to true))
        } else {
            uiState.copy(loadingByDate = uiState.loadingByDate - date)
        }
    }

    fun setError(date: LocalDate, message: String?) {
        uiState = if (message == null) {
            uiState.copy(errorByDate = uiState.errorByDate - date)
        } else {
            uiState.copy(errorByDate = uiState.errorByDate + (date to message))
        }
    }

    suspend fun loadTasksForDate(date: LocalDate) {
        val repository = repository ?: return

        val targetDate = date
        val token = ++nextLoadToken
        val hasCachedTasks = uiState.tasksByDate.containsKey(targetDate)
        activeLoadTokenByDate[targetDate] = token
        setLoading(targetDate, true)
        setError(targetDate, null)

        val result = runCatching { repository.fetchTasks(targetDate) }
        if (activeLoadTokenByDate[targetDate] != token) return

        result
            .onSuccess { tasks ->
                setTasks(targetDate, tasks)
                setError(targetDate, null)
            }
            .onFailure {
                if (!hasCachedTasks) {
                    clearTasks(targetDate)
                }
                setError(targetDate, "No se pudieron cargar las tareas")
            }

        if (activeLoadTokenByDate[targetDate] == token) {
            setLoading(targetDate, false)
        }
    }

    suspend fun saveTask(date: LocalDate, draft: TaskDraft): SaveResult {
        val trimmedTitle = draft.title.trim()
        if (trimmedTitle.isEmpty()) return SaveResult(false, "Titulo requerido")
        val repository = repository

        return if (repository == null) {
            val task = TaskItem(
                id = "task-${Clock.System.now().toEpochMilliseconds()}",
                title = trimmedTitle,
                details = draft.details?.trim()?.ifBlank { null },
                time = draft.time,
                labels = draft.labels,
            )
            setTasks(date, tasksFor(date) + task)
            setError(date, null)
            SaveResult(true)
        } else {
            runCatching { repository.createTask(date, draft.copy(title = trimmedTitle)) }
                .onSuccess { created ->
                    setTasks(date, tasksFor(date) + created)
                    setError(date, null)
                }
                .onFailure { error ->
                    setError(date, resolveServerError(error))
                }
                .fold(
                    onSuccess = { SaveResult(true) },
                    onFailure = { SaveResult(false, resolveServerError(it)) },
                )
        }
    }

    suspend fun toggleTaskDone(date: LocalDate, task: TaskItem, isDone: Boolean): Boolean {
        val repository = repository
        return if (repository == null) {
            replaceTask(date, task.copy(isDone = isDone))
            setError(date, null)
            true
        } else {
            runCatching { repository.updateTaskDone(task.id, isDone) }
                .onSuccess { updated ->
                    replaceTask(date, updated)
                    setError(date, null)
                }
                .onFailure {
                    setError(date, "No se pudo actualizar la tarea")
                }
                .isSuccess
        }
    }

    suspend fun deleteTask(date: LocalDate, task: TaskItem): Boolean {
        val repository = repository
        return if (repository == null) {
            removeTask(date, task.id)
            setError(date, null)
            true
        } else {
            runCatching { repository.deleteTask(task.id) }
                .onSuccess { success ->
                    if (success) {
                        removeTask(date, task.id)
                        setError(date, null)
                    } else {
                        setError(date, "No se pudo eliminar la tarea")
                    }
                }
                .onFailure {
                    setError(date, "No se pudo eliminar la tarea")
                }
                .getOrDefault(false)
        }
    }

    suspend fun deleteAllTasks(): Boolean {
        val selectedDate = uiState.selectedDate
        val repository = repository
        return if (repository == null) {
            clearAllTasks()
            true
        } else {
            runCatching { repository.deleteAllTasks() }
                .onSuccess { success ->
                    if (success) {
                        clearAllTasks()
                    } else {
                        setError(selectedDate, "No se pudieron borrar las notas")
                    }
                }
                .onFailure {
                    setError(selectedDate, "No se pudieron borrar las notas")
                }
                .getOrDefault(false)
        }
    }

    fun clearAllTasks() {
        uiState = uiState.copy(
            tasksByDate = emptyMap(),
            loadingByDate = emptyMap(),
            errorByDate = emptyMap(),
        )
        activeLoadTokenByDate.clear()
    }

    fun removeLabelFromTasks(labelId: String) {
        val updated = uiState.tasksByDate.mapValues { (_, tasks) ->
            tasks.map { task ->
                task.copy(labels = task.labels.filterNot { it.id == labelId })
            }
        }
        uiState = uiState.copy(tasksByDate = updated)
    }

    fun clearLabelsFromTasks() {
        val updated = uiState.tasksByDate.mapValues { (_, tasks) ->
            tasks.map { task -> task.copy(labels = emptyList()) }
        }
        uiState = uiState.copy(tasksByDate = updated)
    }

    private fun tasksFor(date: LocalDate): List<TaskItem> = uiState.tasksByDate[date] ?: emptyList()

    private fun replaceTask(date: LocalDate, task: TaskItem) {
        setTasks(
            date,
            tasksFor(date).map { current ->
                if (current.id == task.id) task else current
            },
        )
    }

    private fun removeTask(date: LocalDate, taskId: String) {
        setTasks(date, tasksFor(date).filterNot { it.id == taskId })
    }
}

private fun resolveServerError(error: Throwable): String {
    if (error is ResponseException) {
        error.message?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return error.message?.takeIf { it.isNotBlank() } ?: "No se pudo guardar la tarea"
}

private fun orderTasks(tasks: List<TaskItem>): List<TaskItem> {
    return tasks.sortedWith(
        compareBy<TaskItem> { it.time == null }
            .thenBy { it.time?.hour ?: 24 }
            .thenBy { it.time?.minute ?: 0 },
    )
}
