package com.franciscor.agendnote.feature.agenda.presentation.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.franciscor.agendnote.core.model.TaskDraft
import com.franciscor.agendnote.core.model.TaskItem
import com.franciscor.agendnote.core.notifications.NotificationServiceProvider
import com.franciscor.agendnote.core.platform.currentTimeMillis
import com.franciscor.agendnote.feature.agenda.domain.AgendaTaskRepository
import com.franciscor.agendnote.feature.agenda.domain.RecurrenceEnd
import com.franciscor.agendnote.feature.agenda.domain.RecurrenceRule
import com.franciscor.agendnote.feature.agenda.domain.SeriesMaterializer
import com.franciscor.agendnote.feature.agenda.domain.TaskSeriesRepository
import com.franciscor.agendnote.feature.agenda.presentation.model.AgendaDayUiState
import com.franciscor.agendnote.feature.agenda.presentation.model.AgendaUiState
import com.franciscor.agendnote.feature.agenda.presentation.model.PendingUndo
import com.franciscor.agendnote.feature.agenda.presentation.model.SaveResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class AgendaViewModel(
    private val repository: AgendaTaskRepository?,
    private val taskSeriesRepository: TaskSeriesRepository? = null,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val remoteUnavailableMessage: String? = null,
    initialDate: LocalDate = currentDate(timeZone),
) {
    private val hasRemoteAccess = repository != null
    private val remoteErrorMessage = remoteUnavailableMessage
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: "Configuración remota incompleta. No se puede conectar con la BD."
    private val notificationService = NotificationServiceProvider.getNotificationService()
    private val materializer = if (repository != null && taskSeriesRepository != null) {
        SeriesMaterializer(taskSeriesRepository, repository)
    } else {
        null
    }

    var uiState by mutableStateOf(
        AgendaUiState(
            selectedDate = initialDate,
            visibleMonth = LocalDate(initialDate.year, initialDate.monthNumber, 1),
            isRemoteAvailable = hasRemoteAccess,
        ),
    )
        private set

    private val loadedMonths = mutableSetOf<LocalDate>()

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
    // Notification APIs are platform work and must never block the Compose/UI dispatcher.
    private val notificationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val notificationCommands = Channel<NotificationCommand>(Channel.UNLIMITED)

    init {
        // REVIEW: one consumer gives notification mutations strict FIFO ordering without reading
        // Compose state from a background thread. It also keeps JVM tests independent of Main.
        notificationScope.launch {
            for (command in notificationCommands) {
                when (command) {
                    is NotificationCommand.ReconcileDay -> reconcileDayNotifications(command)
                    NotificationCommand.CancelAll -> {
                        runCatching { notificationService.cancelAllTaskNotifications() }
                    }
                }
            }
        }
    }

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
        val previousTasks = uiState.tasksByDate[date].orEmpty()
        val orderedTasks = orderTasks(tasks)
        uiState = uiState.copy(tasksByDate = uiState.tasksByDate + (date to orderedTasks))

        // REVIEW: reconciling the whole day also removes alarms for tasks deleted remotely,
        // completed, or changed to "Sin hora". The channel preserves mutation order.
        notificationCommands.trySend(
            NotificationCommand.ReconcileDay(
                date = date,
                previousTasks = previousTasks,
                currentTasks = orderedTasks,
            ),
        )
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

    suspend fun loadMonth(month: LocalDate) {
        val monthStart = LocalDate(month.year, month.monthNumber, 1)
        uiState = uiState.copy(visibleMonth = monthStart, monthErrorMessage = null)

        if (loadedMonths.contains(monthStart)) return

        val repository = repository ?: run {
            setMonthError(remoteErrorMessage)
            return
        }

        setMonthLoading(true)
        setMonthError(null)

        val monthEnd = monthStart.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
        val result = runCatching { repository.fetchTasksInRange(monthStart, monthEnd) }

        result
            .onSuccess { fetched ->
                val allDays = generateSequence(monthStart) { it.plus(1, DateTimeUnit.DAY) }
                    .takeWhile { it <= monthEnd }
                val fullMonth = allDays.associateWith { day -> fetched[day].orEmpty() }
                setTasksForMonth(fullMonth)
                loadedMonths.add(monthStart)
            }
            .onFailure {
                setMonthError("No se pudieron cargar las tareas del mes")
            }

        setMonthLoading(false)
    }

    private fun setTasksForMonth(tasksByDate: Map<LocalDate, List<TaskItem>>) {
        // No pasa por setTasks() a propósito: setTasks() re-programa notificaciones locales por
        // cada tarea con hora, y llamarlo una vez por día del mes (hasta 31 veces) reprogramaría
        // notificaciones ya programadas sin necesidad cada vez que se abre el calendario.
        val ordered = tasksByDate.mapValues { (_, tasks) -> orderTasks(tasks) }
        uiState = uiState.copy(tasksByDate = uiState.tasksByDate + ordered)
    }

    private fun setMonthLoading(isLoading: Boolean) {
        uiState = uiState.copy(isMonthLoading = isLoading)
    }

    private fun setMonthError(message: String?) {
        uiState = uiState.copy(monthErrorMessage = message)
    }

    suspend fun saveTask(date: LocalDate, draft: TaskDraft): SaveResult {
        val trimmedTitle = draft.title.trim()
        if (trimmedTitle.isEmpty()) return SaveResult(false, "Título requerido")
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

    /**
     * Updates an existing task. [originalDate] is the day the task is currently filed under in
     * [uiState.tasksByDate] and [targetDate] is the day [draft] wants it filed under - they
     * differ when the user changed the task's day while editing. Always goes through
     * [moveTask]/[setTasks]/[replaceTask], never a direct `uiState.copy(...)`, so that local
     * notifications stay reconciled with the new state (see [setTasks]'s doc comment).
     */
    suspend fun updateTask(originalDate: LocalDate, id: String, targetDate: LocalDate, draft: TaskDraft): SaveResult {
        val trimmedTitle = draft.title.trim()
        if (trimmedTitle.isEmpty()) return SaveResult(false, "Título requerido")
        val repository = repository ?: run {
            setError(originalDate, remoteErrorMessage)
            return SaveResult(false, remoteErrorMessage)
        }

        return runCatching { repository.updateTask(id, targetDate, draft.copy(title = trimmedTitle)) }
            .onSuccess { updated ->
                moveTask(originalDate, targetDate, updated)
                setError(originalDate, null)
            }
            .onFailure { error ->
                setError(originalDate, resolveServerError(error))
            }
            .fold(
                onSuccess = { SaveResult(true) },
                onFailure = { SaveResult(false, resolveServerError(it)) },
            )
    }

    suspend fun saveRecurringTask(
        date: LocalDate,
        draft: TaskDraft,
        rule: RecurrenceRule,
        end: RecurrenceEnd = RecurrenceEnd.Never,
    ): SaveResult {
        val trimmedTitle = draft.title.trim()
        if (trimmedTitle.isEmpty()) return SaveResult(false, "Título requerido")
        val taskSeriesRepository = taskSeriesRepository ?: run {
            setError(date, remoteErrorMessage)
            return SaveResult(false, remoteErrorMessage)
        }
        val materializer = materializer ?: run {
            setError(date, remoteErrorMessage)
            return SaveResult(false, remoteErrorMessage)
        }

        val seriesResult = runCatching {
            taskSeriesRepository.createSeries(
                title = trimmedTitle,
                details = draft.details,
                time = draft.time,
                rule = rule,
                labels = draft.labels,
                startDate = date,
                end = end,
            )
        }

        val series = seriesResult.getOrElse { error ->
            val message = resolveServerError(error, fallback = "No se pudo crear la serie recurrente")
            setError(date, message)
            return SaveResult(false, message)
        }

        val materialized = runCatching { materializer.materializeSeries(series, date) }
            .getOrElse { false }
        if (!materialized) {
            setError(date, "La serie se creo pero no se pudieron generar todas las tareas")
            return SaveResult(false, "La serie se creo pero no se pudieron generar todas las tareas")
        }

        loadTasksForDate(date)
        return SaveResult(true)
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
                // Offer "Deshacer" only for the transition that just completed a task, and only
                // for that exact task - unmarking (isDone = false), whether via undo or any other
                // path, always clears it instead of leaving a stale snackbar target.
                uiState = uiState.copy(
                    pendingUndo = if (isDone) {
                        PendingUndo(date, updated)
                    } else if (uiState.pendingUndo?.task?.id == task.id) {
                        null
                    } else {
                        uiState.pendingUndo
                    },
                )
            }
            .onFailure {
                setError(date, "No se pudo actualizar la tarea")
            }
            .isSuccess
    }

    /** Dismisses the pending "Deshacer" snackbar without changing any task's done state. */
    fun dismissPendingUndo() {
        uiState = uiState.copy(pendingUndo = null)
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

    fun loadMonthAsync(month: LocalDate) {
        viewModelScope.launch { loadMonth(month) }
    }

    fun saveTaskAsync(date: LocalDate, draft: TaskDraft, onResult: (SaveResult) -> Unit = {}) {
        viewModelScope.launch { onResult(saveTask(date, draft)) }
    }

    fun updateTaskAsync(
        originalDate: LocalDate,
        id: String,
        targetDate: LocalDate,
        draft: TaskDraft,
        onResult: (SaveResult) -> Unit = {},
    ) {
        viewModelScope.launch { onResult(updateTask(originalDate, id, targetDate, draft)) }
    }

    fun saveRecurringTaskAsync(
        date: LocalDate,
        draft: TaskDraft,
        rule: RecurrenceRule,
        end: RecurrenceEnd = RecurrenceEnd.Never,
        onResult: (SaveResult) -> Unit = {},
    ) {
        viewModelScope.launch { onResult(saveRecurringTask(date, draft, rule, end)) }
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
        notificationCommands.trySend(NotificationCommand.CancelAll)
    }

    private suspend fun reconcileDayNotifications(command: NotificationCommand.ReconcileDay) {
        // A task can now request a native alarm/notification either via an explicit reminder
        // (task.reminders, see ReminderResolution.earliestReminderInstant) or, for tasks that
        // predate that model, via a plain planned time (task.time). Either is enough to need
        // scheduling; neither is enough to cancel.
        fun hasSchedulableReminder(task: TaskItem) = task.time != null || task.reminders.isNotEmpty()

        val currentById = command.currentTasks.associateBy { it.id }
        command.previousTasks.forEach { previous ->
            val current = currentById[previous.id]
            if (current == null || current.isDone || !hasSchedulableReminder(current)) {
                runCatching { notificationService.cancelTaskNotification(previous.id) }
            }
        }
        command.currentTasks
            .filter { !it.isDone && hasSchedulableReminder(it) }
            .forEach { task ->
                runCatching { notificationService.scheduleTaskNotification(task, command.date) }
            }
    }

    private sealed interface NotificationCommand {
        data class ReconcileDay(
            val date: LocalDate,
            val previousTasks: List<TaskItem>,
            val currentTasks: List<TaskItem>,
        ) : NotificationCommand

        data object CancelAll : NotificationCommand
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

    /** Moves [task] from [fromDate] to [toDate] in [uiState.tasksByDate], going through
     * [setTasks] on both ends so notifications stay reconciled. If [toDate] was never cached
     * (e.g. the user has not visited that day yet), it is deliberately left uncached rather than
     * seeded with just this one task. */
    private fun moveTask(fromDate: LocalDate, toDate: LocalDate, task: TaskItem) {
        if (fromDate == toDate) {
            replaceTask(fromDate, task)
            return
        }
        setTasks(fromDate, tasksFor(fromDate).filterNot { it.id == task.id })
        if (uiState.tasksByDate.containsKey(toDate)) {
            setTasks(toDate, tasksFor(toDate) + task)
        }
    }
}

/**
 * Always a fixed, human, Spanish message - never [error]'s own text. `ResponseException` and
 * the Postgres/Edge Function errors it wraps carry raw HTTP body text (constraint names, table
 * names, English diagnostic strings); surfacing that directly in the UI leaks internal schema
 * details and reads as a bug even when the underlying failure is mundane (see
 * docs/agendnote/SECURITY_AUDIT.md). If per-failure detail is ever needed, log [error] to a
 * developer-only channel instead of returning it to the caller - this project has none today.
 */
@Suppress("UNUSED_PARAMETER")
private fun resolveServerError(error: Throwable, fallback: String = "No se pudo guardar la tarea"): String {
    return fallback
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
