package com.franciscor.agendnote.feature.agenda.presentation.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.franciscor.agendnote.core.model.LabelTag
import com.franciscor.agendnote.core.model.TaskItem
import com.franciscor.agendnote.core.model.TaskTemplate
import com.franciscor.agendnote.core.platform.currentTimeMillis
import com.franciscor.agendnote.core.ui.components.GlassIconButton
import com.franciscor.agendnote.core.ui.components.GlassSnackbar
import com.franciscor.agendnote.core.ui.components.GlassSurface
import com.franciscor.agendnote.core.ui.components.colorFromHex
import com.franciscor.agendnote.core.ui.layout.AppLayout
import com.franciscor.agendnote.core.ui.theme.GlassTheme
import com.franciscor.agendnote.feature.agenda.presentation.controller.AgendaController
import com.franciscor.agendnote.feature.agenda.presentation.model.AgendaAction
import com.franciscor.agendnote.feature.agenda.presentation.model.PendingDelete
import com.franciscor.agendnote.feature.agenda.presentation.viewmodel.AgendaViewModel
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * "Día" tab (Operación Aniversario, replaces the old standalone Calendario tab - see
 * docs/OPERATION_ANNIVERSARY_STATUS.md). Shows the same selected day as Agenda
 * ([AgendaViewModel.uiState.selectedDate] is shared), but as an hour-by-hour timeline instead of
 * a task-card list. Reuses the exact same overlay-hosting pattern as [AgendaScreen] (task sheet
 * modes, pending-delete confirmation, task detail, undo snackbar) rather than sharing code with
 * it directly - the two screens' headers and middle content differ enough (no search bar, no
 * smart lists here; hourly grid instead of a card list) that a generic slot-based abstraction
 * would have cost more review risk under deadline than the small amount of duplicated wiring.
 *
 * Known scope cut, documented rather than hidden: tapping an empty hour opens task creation
 * prefilled with the day only, not that exact hour yet - extending [TaskSheetMode.Create] to
 * carry a preset time touches the same large, already-heavily-reviewed `NewTaskSheet` this
 * session; deferred to avoid stacking more risk on that file in the same pass.
 */
@Composable
fun DayScreen(
    viewModel: AgendaViewModel,
    controller: AgendaController,
    labels: List<LabelTag>,
    onCreateLabel: suspend (String, String) -> LabelTag?,
    templates: List<TaskTemplate> = emptyList(),
    onSaveTemplate: suspend (TaskTemplate) -> Boolean = { false },
    modifier: Modifier = Modifier,
) {
    val layout = AppLayout.metrics
    val contentInset = layout.width(16.dp, 14.dp)
    val uiState = viewModel.uiState
    val isEditingEnabled = uiState.isRemoteAvailable
    val dayUiState = viewModel.selectedDayUiState()
    val selectedDate = uiState.selectedDate
    val sourceTasks = dayUiState.tasks
    var showTaskSheet by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var showTaskDetailsTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingDelete = pendingDeleteTaskId?.let { id ->
        sourceTasks.find { it.id == id }?.let { task -> PendingDelete(selectedDate, task) }
    }
    val showTaskDetails = showTaskDetailsTaskId?.let { id -> sourceTasks.find { it.id == id } }
    // Same Bug-3-fix snapshot pattern as AgendaScreen: freeze the task being edited at the
    // moment the sheet opens instead of re-resolving it against sourceTasks on every
    // recomposition, so a background refresh can't close the sheet out from under the user.
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

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = contentInset,
                    vertical = layout.height(12.dp, 10.dp),
                ),
            verticalArrangement = Arrangement.spacedBy(layout.height(12.dp, 10.dp)),
        ) {
            DayScreenHeader(
                selectedDate = selectedDate,
                isToday = selectedDate == viewModel.today(),
                onPreviousDay = { controller.handleAsync(AgendaAction.MoveDay(-1)) },
                onNextDay = { controller.handleAsync(AgendaAction.MoveDay(1)) },
                onGoToToday = { controller.handleAsync(AgendaAction.SelectDate(viewModel.today())) },
            )

            DayTimeline(
                selectedDate = selectedDate,
                tasks = sourceTasks,
                isLoading = dayUiState.isLoading,
                isToday = selectedDate == viewModel.today(),
                onTaskSelected = { task -> showTaskDetailsTaskId = task.id },
                onCreateRequested = { showTaskSheet = true },
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

        uiState.pendingUndo?.let { pending ->
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

@Composable
private fun DayScreenHeader(
    selectedDate: LocalDate,
    isToday: Boolean,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onGoToToday: () -> Unit,
) {
    val layout = AppLayout.metrics
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = "Día",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = layout.text(32.sp, 28.sp),
                ),
                color = GlassTheme.tokens.textPrimary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatFullDate(selectedDate),
                    style = MaterialTheme.typography.bodyLarge,
                    color = GlassTheme.tokens.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!isToday) {
                    Spacer(modifier = Modifier.width(layout.width(10.dp, 8.dp)))
                    GlassSurface(
                        shape = RoundedCornerShape(layout.size(16.dp, 14.dp)),
                        tint = GlassTheme.tokens.glassFillStrong,
                    ) {
                        Text(
                            text = "Hoy",
                            style = MaterialTheme.typography.labelMedium,
                            color = GlassTheme.tokens.textPrimary,
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onGoToToday,
                                )
                                .padding(
                                    horizontal = layout.width(14.dp, 12.dp),
                                    vertical = layout.height(7.dp, 6.dp),
                                ),
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(layout.width(8.dp, 6.dp))) {
            GlassIconButton(
                icon = Icons.Rounded.ChevronLeft,
                contentDescription = "Día anterior",
                onClick = onPreviousDay,
            )
            GlassIconButton(
                icon = Icons.Rounded.ChevronRight,
                contentDescription = "Día siguiente",
                onClick = onNextDay,
            )
        }
    }
}

/**
 * Hour-by-hour breakdown of [selectedDate], one row per hour with tasks slotted into the hour
 * they start at, plus a thin current-time indicator when [isToday]. Adapted from a previously
 * unused `DayHourAgenda` composable that lived in CalendarScreen.kt (added 2026-07-25, never
 * wired to any navigation - confirmed by a zero-result grep before reusing it here).
 */
@Composable
private fun DayTimeline(
    selectedDate: LocalDate,
    tasks: List<TaskItem>,
    isLoading: Boolean,
    isToday: Boolean,
    onTaskSelected: (TaskItem) -> Unit,
    onCreateRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = AppLayout.metrics
    val timeZone = TimeZone.currentSystemDefault()
    val timedTasks = tasks.filter { it.time != null }.sortedBy { it.time }
    val untimedTasks = tasks.filter { it.time == null }
    val tasksByHour = timedTasks.groupBy { it.time!!.hour }
    val listState = rememberLazyListState()
    val now = if (isToday) {
        Instant.fromEpochMilliseconds(currentTimeMillis()).toLocalDateTime(timeZone)
    } else {
        null
    }
    val currentHour = now?.hour
    val currentMinuteFraction = now?.let { it.minute / 60f }

    // Land on the first task of the day (or "now" for today) instead of forcing a scroll from
    // midnight every time this day is opened.
    LaunchedEffect(selectedDate, tasks) {
        val targetHour = timedTasks.firstOrNull()?.time?.hour ?: currentHour ?: 8
        listState.scrollToItem((targetHour - 1).coerceIn(0, 23))
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (untimedTasks.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(layout.width(8.dp, 6.dp)),
                ) {
                    untimedTasks.forEach { task ->
                        UntimedTaskChip(task = task, onClick = { onTaskSelected(task) })
                    }
                }
                Spacer(modifier = Modifier.height(layout.height(10.dp, 8.dp)))
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = layout.height(88.dp, 76.dp)),
            ) {
                items(24) { hour ->
                    DayHourRow(
                        hour = hour,
                        tasks = tasksByHour[hour].orEmpty(),
                        isCurrentHour = hour == currentHour,
                        currentMinuteFraction = currentMinuteFraction.takeIf { hour == currentHour },
                        onTaskSelected = onTaskSelected,
                        onCreateRequested = onCreateRequested,
                    )
                }
            }
        }
        if (isLoading) {
            GlassSurface(
                modifier = Modifier.align(Alignment.TopCenter),
                shape = RoundedCornerShape(layout.size(14.dp, 12.dp)),
                tint = GlassTheme.tokens.glassFillStrong,
            ) {
                Text(
                    text = "Cargando...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassTheme.tokens.textSecondary,
                    modifier = Modifier.padding(
                        horizontal = layout.width(14.dp, 12.dp),
                        vertical = layout.height(8.dp, 7.dp),
                    ),
                )
            }
        }
    }
}

private val DayRowMinHeight = 60.dp
private val DayRowMinHeightCompact = 52.dp

@Composable
private fun DayHourRow(
    hour: Int,
    tasks: List<TaskItem>,
    isCurrentHour: Boolean,
    currentMinuteFraction: Float?,
    onTaskSelected: (TaskItem) -> Unit,
    onCreateRequested: () -> Unit,
) {
    val layout = AppLayout.metrics
    val rowMinHeight = layout.height(DayRowMinHeight, DayRowMinHeightCompact)
    val rowBackground = if (isCurrentHour) GlassTheme.tokens.glassFill else Color.Transparent

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = rowMinHeight)
                .background(rowBackground)
                .let { base ->
                    // An hour with no tasks is a tap target for "create a task around this
                    // time" - one with tasks is not (tapping a task card below opens its own
                    // detail instead; adding a second create affordance in the same row would
                    // be ambiguous about which the user meant).
                    if (tasks.isEmpty()) {
                        base.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onCreateRequested,
                        )
                    } else {
                        base
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .width(layout.width(52.dp, 46.dp))
                    .padding(top = layout.height(6.dp, 5.dp)),
                contentAlignment = Alignment.TopCenter,
            ) {
                Text(
                    text = "${hour.toString().padStart(2, '0')}:00",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCurrentHour) GlassTheme.tokens.textPrimary else GlassTheme.tokens.textSecondary,
                )
            }
            Box(
                modifier = Modifier
                    .width(layout.size(1.dp, 1.dp))
                    .fillMaxHeight()
                    .background(GlassTheme.tokens.divider),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = layout.width(12.dp, 10.dp),
                        top = layout.height(6.dp, 5.dp),
                        bottom = layout.height(6.dp, 5.dp),
                        end = layout.width(4.dp, 4.dp),
                    ),
                verticalArrangement = Arrangement.spacedBy(layout.height(6.dp, 5.dp)),
            ) {
                if (tasks.isEmpty()) {
                    // Deliberately no placeholder text/icon here (e.g. "+ Agregar") - the whole
                    // row already carries the create affordance, and a hint on every one of the
                    // ~20 empty hours in a typical day would be more visual noise than help.
                    Spacer(modifier = Modifier.height(layout.height(1.dp, 1.dp)))
                } else {
                    tasks.forEach { task ->
                        DayHourTaskCard(task = task, onClick = { onTaskSelected(task) })
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(layout.size(1.dp, 1.dp))
                .padding(start = layout.width(52.dp, 46.dp))
                .background(GlassTheme.tokens.divider.copy(alpha = 0.5f)),
        )
        if (currentMinuteFraction != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = layout.width(52.dp, 46.dp))
                    .height(layout.size(2.dp, 2.dp))
                    .align(Alignment.TopStart)
                    .padding(top = rowMinHeight * currentMinuteFraction)
                    .background(GlassTheme.tokens.accent)
                    .semantics { contentDescription = "Hora actual" },
            )
        }
    }
}

@Composable
private fun DayHourTaskCard(task: TaskItem, onClick: () -> Unit) {
    val layout = AppLayout.metrics
    val alpha = if (task.isDone) 0.55f else 1f
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(layout.size(14.dp, 12.dp)),
        tint = GlassTheme.tokens.glassFillStrong,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = layout.width(12.dp, 10.dp),
                vertical = layout.height(8.dp, 7.dp),
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(layout.width(8.dp, 6.dp)),
        ) {
            Text(
                text = formatTime(task.time!!),
                style = MaterialTheme.typography.labelMedium,
                color = GlassTheme.tokens.textSecondary,
            )
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium,
                color = GlassTheme.tokens.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            task.labels.firstOrNull()?.let { label ->
                Box(
                    modifier = Modifier
                        .padding(start = layout.width(2.dp, 2.dp)),
                ) {
                    LabelDot(colorHex = label.colorHex)
                }
            }
        }
    }
}

@Composable
private fun LabelDot(colorHex: String) {
    val layout = AppLayout.metrics
    Box(
        modifier = Modifier
            .padding(top = layout.height(1.dp, 1.dp))
            .background(colorFromHex(colorHex), RoundedCornerShape(50)),
    ) {
        Spacer(modifier = Modifier.width(layout.size(8.dp, 7.dp)).height(layout.size(8.dp, 7.dp)))
    }
}

@Composable
private fun UntimedTaskChip(task: TaskItem, onClick: () -> Unit) {
    val layout = AppLayout.metrics
    GlassSurface(
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
        shape = RoundedCornerShape(layout.size(14.dp, 12.dp)),
        tint = GlassTheme.tokens.glassFill,
        strokeColor = GlassTheme.tokens.glassStroke,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = layout.width(12.dp, 10.dp),
                vertical = layout.height(8.dp, 7.dp),
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(layout.width(6.dp, 5.dp)),
        ) {
            Text(
                text = "Sin hora",
                style = MaterialTheme.typography.labelSmall,
                color = GlassTheme.tokens.textSecondary,
            )
            Text(
                text = task.title,
                style = MaterialTheme.typography.labelMedium,
                color = GlassTheme.tokens.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
