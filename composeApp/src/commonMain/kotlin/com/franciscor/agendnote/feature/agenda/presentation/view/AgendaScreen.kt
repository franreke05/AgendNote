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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

@Composable
fun AgendaScreen(
    viewModel: AgendaViewModel,
    controller: AgendaController,
    labels: List<LabelTag>,
    onCreateLabel: suspend (String, String) -> LabelTag?,
    modifier: Modifier = Modifier,
) {
    val layout = AppLayout.metrics
    val scope = rememberCoroutineScope()
    val uiState = viewModel.uiState
    val dayUiState = viewModel.selectedDayUiState()
    val selectedDate = uiState.selectedDate
    val sourceTasks = dayUiState.tasks
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showCalendar by remember { mutableStateOf(false) }
    var showTaskSheet by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = layout.width(72.dp, 56.dp)
    val swipeEdgeGuard = layout.width(24.dp, 18.dp)

    LaunchedEffect(Unit) {
        controller.handle(AgendaAction.RefreshSelectedDate)
    }

    val filteredTasks = remember(sourceTasks, searchQuery) {
        filterTasks(sourceTasks, searchQuery)
    }
    val blurRadius = if (showTaskSheet || showCalendar) layout.size(18.dp, 14.dp) else 0.dp

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .blur(blurRadius),
            verticalArrangement = Arrangement.spacedBy(layout.height(16.dp, 14.dp)),
        ) {
            AgendaHeader(
                selectedDate = selectedDate,
                isToday = selectedDate == viewModel.today(),
                onPreviousDay = {
                    scope.launch { controller.handle(AgendaAction.MoveDay(-1)) }
                },
                onNextDay = {
                    scope.launch { controller.handle(AgendaAction.MoveDay(1)) }
                },
                onOpenCalendar = { showCalendar = true },
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
                onToggleDone = { task, done ->
                    scope.launch {
                        controller.toggleTaskDone(selectedDate, task, done)
                    }
                },
                onRequestDelete = { task ->
                    pendingDelete = PendingDelete(selectedDate, task)
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
                                    dragOffset > swipeThreshold.toPx() -> scope.launch {
                                        controller.handle(AgendaAction.MoveDay(-1))
                                    }

                                    dragOffset < -swipeThreshold.toPx() -> scope.launch {
                                        controller.handle(AgendaAction.MoveDay(1))
                                    }
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
                    end = layout.width(20.dp, 18.dp),
                    bottom = layout.height(98.dp, 88.dp),
                ),
            onClick = { showTaskSheet = true },
        )

        if (showCalendar) {
            CalendarOverlay(
                selectedDate = selectedDate,
                tasksByDate = uiState.tasksByDate,
                onDismiss = { showCalendar = false },
                onSelectDate = { date ->
                    showCalendar = false
                    scope.launch { controller.handle(AgendaAction.SelectDate(date)) }
                },
            )
        }

        if (showTaskSheet) {
            NewTaskSheet(
                date = selectedDate,
                labels = labels,
                onCreateLabel = onCreateLabel,
                onDismiss = { showTaskSheet = false },
                onSave = { targetDate, draft ->
                    controller.saveTask(targetDate, draft).also { result ->
                        if (result.success) {
                            showTaskSheet = false
                        }
                    }
                },
            )
        }

        pendingDelete?.let { state ->
            ConfirmDeleteDialog(
                taskTitle = state.task.title,
                onDismiss = { pendingDelete = null },
                onConfirm = {
                    val target = pendingDelete ?: return@ConfirmDeleteDialog
                    pendingDelete = null
                    scope.launch {
                        controller.deleteTask(target.date, target.task)
                    }
                },
            )
        }
    }
}
