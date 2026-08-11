package com.franciscor.agendnote.feature.agenda.presentation.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.franciscor.agendnote.core.model.LabelTag
import com.franciscor.agendnote.core.model.TaskTemplate
import com.franciscor.agendnote.core.ui.components.GlassSnackbar
import com.franciscor.agendnote.core.ui.layout.AppLayout
import com.franciscor.agendnote.feature.agenda.presentation.controller.AgendaController
import com.franciscor.agendnote.feature.agenda.presentation.model.AgendaAction
import com.franciscor.agendnote.feature.agenda.presentation.model.PendingDelete
import com.franciscor.agendnote.feature.agenda.presentation.viewmodel.AgendaViewModel

@Composable
fun AgendaScreen(
    viewModel: AgendaViewModel,
    controller: AgendaController,
    labels: List<LabelTag>,
    onCreateLabel: suspend (String, String) -> LabelTag?,
    templates: List<TaskTemplate> = emptyList(),
    onSaveTemplate: suspend (TaskTemplate) -> Boolean = { false },
    // Set by AppNavHost when the user tapped a task-reminder notification (see
    // NotificationRouter/NotificationRoute.Task) - null the rest of the time. Consumed by the
    // LaunchedEffect below (selects that day, then opens the task's detail overlay once it's
    // loaded) and immediately reported back via onPendingTaskRouteConsumed so AppNavHost clears
    // NotificationRouter and this doesn't re-fire on the next recomposition/tab switch.
    pendingTaskRoute: com.franciscor.agendnote.core.notifications.NotificationRoute.Task? = null,
    onPendingTaskRouteConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val layout = AppLayout.metrics
    val contentInset = layout.width(16.dp, 14.dp)
    val uiState = viewModel.uiState
    val isEditingEnabled = uiState.isRemoteAvailable
    val dayUiState = viewModel.selectedDayUiState()
    val selectedDate = uiState.selectedDate
    val sourceTasks = dayUiState.tasks
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showTaskSheet by rememberSaveable { mutableStateOf(false) }
    var showSmartLists by rememberSaveable { mutableStateOf(false) }
    var showCalendarPopover by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var showTaskDetailsTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    // Same "store the id, look it up in sourceTasks" pattern as showTaskDetailsTaskId/
    // pendingDeleteTaskId above: it stays correct if the task's data changes underneath (e.g. a
    // toggle-done elsewhere) instead of freezing a stale snapshot. "Editar" is only reachable
    // from a task already in sourceTasks (the selected day), so its original date is always
    // selectedDate at lookup time.
    var editingTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingDelete = pendingDeleteTaskId?.let { id ->
        sourceTasks.find { it.id == id }?.let { task -> PendingDelete(selectedDate, task) }
    }
    val showTaskDetails = showTaskDetailsTaskId?.let { id -> sourceTasks.find { it.id == id } }
    // Bug 3 fix: snapshot the task being edited at the moment the sheet opens (keyed only on
    // editingTaskId, not on sourceTasks) instead of re-resolving it against sourceTasks on every
    // recomposition. sourceTasks can change while the sheet is open for reasons unrelated to this
    // task (a background refresh, another device's edit, a toggle-done elsewhere) - re-resolving
    // on every change meant the task briefly not being in the newly-fetched list (or the day's
    // cache reloading) closed the sheet out from under the user with no warning and no chance to
    // keep what they had typed.
    val editingTask = remember(editingTaskId) {
        editingTaskId?.let { id -> sourceTasks.find { it.id == id } }
    }
    val taskSheetMode = when {
        editingTask != null -> TaskSheetMode.Edit(editingTask, selectedDate)
        showTaskSheet -> TaskSheetMode.Create(selectedDate)
        else -> null
    }
    LaunchedEffect(Unit) {
        controller.handleAsync(AgendaAction.RefreshSelectedDate)
    }

    LaunchedEffect(pendingTaskRoute) {
        val route = pendingTaskRoute ?: return@LaunchedEffect
        // showTaskDetailsTaskId resolves against sourceTasks reactively (same pattern
        // pendingDeleteTaskId/editingTaskId already use) - it's fine to set it before this day's
        // tasks finish loading, TaskDetailsOverlay just won't show until sourceTasks catches up.
        controller.handleAsync(AgendaAction.SelectDate(route.day))
        showTaskDetailsTaskId = route.taskId
        onPendingTaskRouteConsumed()
    }

    val filteredTasks = remember(sourceTasks, searchQuery) {
        filterTasks(sourceTasks, searchQuery)
    }
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = contentInset,
                    vertical = layout.height(12.dp, 10.dp),
                ),
            verticalArrangement = Arrangement.spacedBy(layout.height(16.dp, 14.dp)),
        ) {
            AgendaHeader(
                selectedDate = selectedDate,
                isToday = selectedDate == viewModel.today(),
                onPreviousDay = {
                    controller.handleAsync(AgendaAction.MoveDay(-1))
                },
                onNextDay = {
                    controller.handleAsync(AgendaAction.MoveDay(1))
                },
                onOpenSmartLists = {
                    // Las listas inteligentes solo ven lo que ya esta cargado en tasksByDate
                    // (ver smartListTasks) - se piden el mes actual y el siguiente para que
                    // "Proximos 7 dias" tenga datos utiles aunque el usuario nunca haya abierto
                    // Calendario todavia.
                    val currentMonth = LocalDate(selectedDate.year, selectedDate.monthNumber, 1)
                    controller.handleAsync(AgendaAction.LoadMonth(currentMonth))
                    controller.handleAsync(AgendaAction.LoadMonth(currentMonth.plus(1, DateTimeUnit.MONTH)))
                    showSmartLists = true
                },
                onOpenCalendar = {
                    controller.handleAsync(AgendaAction.LoadMonth(uiState.visibleMonth))
                    showCalendarPopover = true
                },
            )

            AgendaSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
            )

            DayAgenda(
                tasks = filteredTasks,
                hasSourceTasks = dayUiState.hasCachedTasks,
                isLoading = dayUiState.isLoading,
                // When the remote is unavailable, dayUiState.errorMessage is always the same
                // "remote unavailable" text already shown by the app-wide RemoteStatusBanner
                // (AppNavHost) — showing it a second time here is pure redundancy. Suppressing it
                // in that case also lets the (nicer) empty state render instead of a red card.
                errorMessage = dayUiState.errorMessage.takeIf { isEditingEnabled },
                searchQuery = searchQuery,
                onRetry = {
                    controller.handleAsync(AgendaAction.RefreshSelectedDate)
                },
                onToggleDone = { task, done ->
                    controller.toggleTaskDoneAsync(selectedDate, task, done)
                },
                onRequestDelete = { task ->
                    pendingDeleteTaskId = task.id
                },
                onTaskSelected = { task ->
                    showTaskDetailsTaskId = task.id
                },
                modifier = Modifier.weight(1f),
            )
        }

        if (!showTaskSheet && showTaskDetails == null && editingTask == null) {
            FloatingAddButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = layout.width(20.dp, 18.dp) + contentInset,
                        bottom = layout.height(16.dp, 12.dp),
                    ),
                enabled = isEditingEnabled,
                onClick = { showTaskSheet = true },
            )
        }

        taskSheetMode?.let { mode ->
            NewTaskSheet(
                mode = mode,
                labels = labels,
                onCreateLabel = onCreateLabel,
                templates = templates,
                onSaveTemplate = onSaveTemplate,
                onDismiss = {
                    showTaskSheet = false
                    editingTaskId = null
                },
                onSave = { targetDate, draft, onResult ->
                    controller.saveTaskAsync(targetDate, draft, onResult)
                },
                onSaveRecurring = { targetDate, draft, rule, end, onResult ->
                    controller.saveRecurringTaskAsync(targetDate, draft, rule, end, onResult)
                },
                onSaveEdit = { id, targetDate, draft, remindersTouched, onResult ->
                    val originalDate = (mode as? TaskSheetMode.Edit)?.originalDate ?: selectedDate
                    controller.updateTaskAsync(originalDate, id, targetDate, draft, remindersTouched, onResult)
                },
            )
        }

        pendingDelete?.let { state ->
            ConfirmDeleteDialog(
                task = state.task,
                onDismiss = { pendingDeleteTaskId = null },
                onConfirm = {
                    pendingDeleteTaskId = null
                    controller.deleteTaskAsync(state.date, state.task)
                },
            )
        }

        showTaskDetails?.let { task ->
            TaskDetailsOverlay(
                task = task,
                onDismiss = { showTaskDetailsTaskId = null },
                onToggleDone = { done ->
                    showTaskDetailsTaskId = null
                    controller.toggleTaskDoneAsync(selectedDate, task, done)
                },
                onRequestDelete = {
                    showTaskDetailsTaskId = null
                    pendingDeleteTaskId = task.id
                },
                onRequestEdit = {
                    showTaskDetailsTaskId = null
                    editingTaskId = task.id
                },
            )
        }

        if (showCalendarPopover) {
            CalendarPopover(
                selectedDate = selectedDate,
                visibleMonth = uiState.visibleMonth,
                tasksByDate = uiState.tasksByDate,
                isLoading = uiState.isMonthLoading,
                errorMessage = uiState.monthErrorMessage,
                onSelectDate = { date -> controller.handleAsync(AgendaAction.SelectDate(date)) },
                onVisibleMonthChange = { month -> controller.handleAsync(AgendaAction.LoadMonth(month)) },
                onRetry = { controller.handleAsync(AgendaAction.LoadMonth(uiState.visibleMonth)) },
                onDismiss = { showCalendarPopover = false },
            )
        }

        if (showSmartLists) {
            SmartListsOverlay(
                tasksByDate = uiState.tasksByDate,
                today = viewModel.today(),
                onSelectDate = { date ->
                    controller.handleAsync(AgendaAction.SelectDate(date))
                },
                onDismiss = { showSmartLists = false },
            )
        }

        uiState.pendingUndo?.let { pending ->
            // Auto-dismiss so the "Deshacer" affordance does not linger forever if ignored.
            // Keyed on `pending` so a newer completion restarts the timer instead of the first
            // one closing the snackbar for the task the user just completed.
            LaunchedEffect(pending) {
                delay(4000)
                controller.dismissPendingUndo()
            }
            GlassSnackbar(
                message = "Tarea completada: \"${pending.task.title}\"",
                actionLabel = "Deshacer",
                onAction = {
                    controller.toggleTaskDoneAsync(pending.date, pending.task, false)
                    controller.dismissPendingUndo()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = contentInset,
                        end = contentInset,
                        bottom = layout.height(92.dp, 82.dp),
                    ),
            )
        }
    }
}
