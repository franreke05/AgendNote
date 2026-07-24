package com.franciscor.agendnote.feature.agenda.presentation.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.franciscor.agendnote.core.model.TaskDraft
import com.franciscor.agendnote.core.model.TaskItem
import com.franciscor.agendnote.core.notifications.NotificationServiceProvider
import com.franciscor.agendnote.core.platform.currentTimeMillis
import com.franciscor.agendnote.feature.agenda.domain.AgendaTaskRepository
import com.franciscor.agendnote.feature.agenda.presentation.model.AgendaDayUiState
import com.franciscor.agendnote.feature.agenda.presentation.model.AgendaUiState
import com.franciscor.agendnote.feature.agenda.presentation.model.SaveResult
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class AgendaViewModel(
    private val repository: AgendaTaskRepository?,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val remoteUnavailableMessage: String? = null,
    initialDate: LocalDate = currentDate(timeZone),
) {
    private val hasRemoteAccess = repository != null
    private val remoteErrorMessage = remoteUnavailableMessage
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: "Configuracion remota incompleta. No se puede conectar con la BD."
    private val notificationService = NotificationServiceProvider.getNotificationService()

    var uiState by mutableStateOf(
        AgendaUiState(
            selectedDate = initialDate,
            isRemoteAvailable = hasRemoteAccess,
        ),
    )
        private set

    private var nextLoadToken: Long = 0
    private val activeLoadTokenByDate = mutableMapOf<LocalDate, Long>()

    /**
     * Own coroutine scope for fire-and-forget mutations (save/delete/toggle/day navigation).
     *
     * Previously the screen composables launched these on `rememberCoroutineScope()`, which is
     * tied to the composable's own lifetime. Switching tabs (or any recomposition that removes
     * the agenda screen from composition) silently cancelled in-flight requests, leaving
     * [uiState] out of sync with the backend (e.g. a delete that "didn't happen" after the user
     * comes back). [SupervisorJob] means one failed mutation doesn't cancel the others, and
     * [Dispatchers.Main.immediate] keeps state updates on the UI thread like the rest of this
     * class. This scope is intentionally never cancelled: this ViewModel instance lives for the
     * whole app session (created once in `AppNavHost`), so there is no owner lifecycle to tie it
     * to.
     */
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun moveDay(delta: Int): LocalDate {
        val date = uiState.selectedDate.plus(delta, DateTimeUnit.DAY)
        selectDate(date)
        return date
    }

    fun selectDate(date: LocalDate) {
        uiState = uiState.copy(selectedDate = date)
    }

    fun selectedDayUiState(): AgendaDayUiState = dayUiState(uiState.selectedDate)

    fun today(): LocalDate = currentDate(timeZone)

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
        val orderedTasks = orderTasks(tasks)
        uiState = uiState.copy(tasksByDate = uiState.tasksByDate + (date to orderedTasks))
        
        // Schedule notifications for tasks with time
        for (task in orderedTasks) {
            if (task.time != null) {
                try {
                    scheduleNotificationAsync(task, date)
                } catch (e: Exception) {
                    println("Error scheduling notification: ${e.message}")
                }
            }
        }
    }
    
    private fun scheduleNotificationAsync(task: TaskItem, date: LocalDate) {
        // Note: This runs synchronously on iOS, which is fine for local notifications
        try {
            kotlinx.coroutines.runBlocking {
                notificationService.scheduleTaskNotification(task, date)
            }
        } catch (e: Exception) {
            println("Error in notification scheduling: ${e.message}")
        }
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
        val repository = repository ?: run {
            setError(date, remoteErrorMessage)
            return
        }

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
        val repository = repository ?: run {
            setError(date, remoteErrorMessage)
            return SaveResult(false, remoteErrorMessage)
        }

        return runCatching { repository.createTask(date, draft.copy(title = trimmedTitle)) }
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

    suspend fun toggleTaskDone(date: LocalDate, task: TaskItem, isDone: Boolean): Boolean {
        val repository = repository ?: run {
            setError(date, remoteErrorMessage)
            return false
        }
        return runCatching { repository.updateTaskDone(task.id, isDone) }
            .onSuccess { updated ->
                replaceTask(date, updated)
                setError(date, null)
            }
            .onFailure {
                setError(date, "No se pudo actualizar la tarea")
            }
            .isSuccess
    }

    suspend fun deleteTask(date: LocalDate, task: TaskItem): Boolean {
        val repository = repository ?: run {
            setError(date, remoteErrorMessage)
            return false
        }
        return runCatching { repository.deleteTask(task.id) }
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

    suspend fun deleteAllTasks(): Boolean {
        val selectedDate = uiState.selectedDate
        val repository = repository ?: run {
            setError(selectedDate, remoteErrorMessage)
            return false
        }
        return runCatching { repository.deleteAllTasks() }
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

    // --- Fire-and-forget wrappers -------------------------------------------------------
    // Launch on [viewModelScope] instead of relying on the caller's own coroutine scope, so the
    // mutation always runs to completion and updates [uiState] even if the calling composable
    // (e.g. AgendaScreen) has already left composition. Screen code should call these instead of
    // wrapping the suspend functions above in `rememberCoroutineScope().launch { }`.

    fun moveDayAndLoad(delta: Int) {
        val target = moveDay(delta)
        viewModelScope.launch { loadTasksForDate(target) }
    }

    fun selectDateAndLoad(date: LocalDate) {
        selectDate(date)
        viewModelScope.launch { loadTasksForDate(date) }
    }

    fun refreshSelectedDateAsync() {
        viewModelScope.launch { loadTasksForDate(uiState.selectedDate) }
    }

    fun saveTaskAsync(date: LocalDate, draft: TaskDraft, onResult: (SaveResult) -> Unit = {}) {
        viewModelScope.launch { onResult(saveTask(date, draft)) }
    }

    fun toggleTaskDoneAsync(
        date: LocalDate,
        task: TaskItem,
        isDone: Boolean,
        onResult: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch { onResult(toggleTaskDone(date, task, isDone)) }
    }

    fun deleteTaskAsync(date: LocalDate, task: TaskItem, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch { onResult(deleteTask(date, task)) }
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

private fun currentDate(timeZone: TimeZone): LocalDate {
    return Instant
        .fromEpochMilliseconds(currentTimeMillis())
        .toLocalDateTime(timeZone)
        .date
}

private fun orderTasks(tasks: List<TaskItem>): List<TaskItem> {
    return tasks.sortedWith(
        compareBy<TaskItem> { it.time == null }
            .thenBy { it.time?.hour ?: 24 }
            .thenBy { it.time?.minute ?: 0 },
    )
}
