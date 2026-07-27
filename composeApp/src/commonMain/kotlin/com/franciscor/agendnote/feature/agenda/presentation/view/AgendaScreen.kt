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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.franciscor.agendnote.core.model.LabelTag
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
    var pendingDeleteTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var showTaskDetailsTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingDelete = pendingDeleteTaskId?.let { id ->
        sourceTasks.find { it.id == id }?.let { task -> PendingDelete(selectedDate, task) }
    }
    val showTaskDetails = showTaskDetailsTaskId?.let { id -> sourceTasks.find { it.id == id } }
    LaunchedEffect(Unit) {
        controller.handleAsync(AgendaAction.RefreshSelectedDate)
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
                isEditingEnabled = isEditingEnabled,
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
                // REVIEW: day navigation stays on the explicit header controls. A parent-level
                // horizontal drag competed with each task card's swipe actions and made both
                // gestures unreliable, especially for TalkBack and compact screens.
                modifier = Modifier.weight(1f),
            )
        }

        if (!showTaskSheet && showTaskDetails == null) {
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

        if (showTaskSheet) {
            NewTaskSheet(
                date = selectedDate,
                labels = labels,
                onCreateLabel = onCreateLabel,
                onDismiss = { showTaskSheet = false },
                onSave = { targetDate, draft, onResult ->
                    controller.saveTaskAsync(targetDate, draft, onResult)
                },
                onSaveRecurring = { targetDate, draft, rule, onResult ->
                    controller.saveRecurringTaskAsync(targetDate, draft, rule, onResult)
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
            )
        }
    }
}
