package com.franciscor.agendnote.feature.agenda.presentation.view

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.pointer.pointerInput
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
    val contentInset = layout.width(24.dp, 20.dp)
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
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = layout.width(72.dp, 56.dp)
    val swipeEdgeGuard = layout.width(24.dp, 18.dp)

    LaunchedEffect(Unit) {
        controller.handleAsync(AgendaAction.RefreshSelectedDate)
    }

    val filteredTasks = remember(sourceTasks, searchQuery) {
        filterTasks(sourceTasks, searchQuery)
    }
    val blurRadius = if (showTaskSheet || showTaskDetails != null) layout.size(18.dp, 14.dp) else 0.dp

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = contentInset,
                    vertical = layout.height(12.dp, 10.dp),
                )
                .blur(blurRadius),
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
                selectedDate = selectedDate,
                tasks = filteredTasks,
                hasSourceTasks = dayUiState.hasCachedTasks,
                isLoading = dayUiState.isLoading,
                errorMessage = dayUiState.errorMessage,
                searchQuery = searchQuery,
                isEditingEnabled = isEditingEnabled,
                onToggleDone = { task, done ->
                    controller.toggleTaskDoneAsync(selectedDate, task, done)
                },
                onRequestDelete = { task ->
                    pendingDeleteTaskId = task.id
                },
                onTaskSelected = { task ->
                    showTaskDetailsTaskId = task.id
                },
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(selectedDate, swipeThreshold, swipeEdgeGuard) {
                        var allowSwipe = false
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                val edge = swipeEdgeGuard.toPx()
                                allowSwipe = offset.x in edge..(size.width - edge)
                                dragOffset = 0f
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                if (allowSwipe) {
                                    dragOffset += dragAmount
                                }
                            },
                            onDragEnd = {
                                when {
                                    dragOffset > swipeThreshold.toPx() ->
                                        controller.handleAsync(AgendaAction.MoveDay(-1))

                                    dragOffset < -swipeThreshold.toPx() ->
                                        controller.handleAsync(AgendaAction.MoveDay(1))
                                }
                                dragOffset = 0f
                                allowSwipe = false
                            },
                            onDragCancel = {
                                dragOffset = 0f
                                allowSwipe = false
                            },
                        )
                    },
            )
        }

        FloatingAddButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = layout.width(20.dp, 18.dp) + contentInset,
                    bottom = layout.height(98.dp, 88.dp),
                ),
            enabled = isEditingEnabled,
            onClick = { showTaskSheet = true },
        )

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
