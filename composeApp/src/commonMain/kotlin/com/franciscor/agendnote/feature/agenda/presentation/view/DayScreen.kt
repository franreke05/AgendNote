package com.franciscor.agendnote.feature.agenda.presentation.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.franciscor.agendnote.core.model.LabelTag
import com.franciscor.agendnote.core.model.TaskItem
import com.franciscor.agendnote.core.model.TaskTemplate
import com.franciscor.agendnote.core.platform.currentTimeMillis
import com.franciscor.agendnote.core.ui.components.GlassSnackbar
import com.franciscor.agendnote.core.ui.components.GlassSurface
import com.franciscor.agendnote.core.ui.components.colorFromHex
import com.franciscor.agendnote.core.ui.layout.AppLayout
import com.franciscor.agendnote.core.ui.theme.ControlHeight
import com.franciscor.agendnote.core.ui.theme.GlassTheme
import com.franciscor.agendnote.feature.agenda.presentation.controller.AgendaController
import com.franciscor.agendnote.feature.agenda.presentation.model.AgendaAction
import com.franciscor.agendnote.feature.agenda.presentation.model.PendingDelete
import com.franciscor.agendnote.feature.agenda.presentation.viewmodel.AgendaViewModel
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

/**
 * "DÃ­a" tab (OperaciÃ³n Aniversario, replaces the old standalone Calendario tab - see
 * docs/OPERATION_ANNIVERSARY_STATUS.md). Shows the same selected day as Agenda
 * ([AgendaViewModel.uiState.selectedDate] is shared), but as an hour-by-hour timeline instead of
 * a task-card list. Reuses the exact same overlay-hosting pattern as [AgendaScreen] (task sheet
 * modes, pending-delete confirmation, task detail, undo snackbar) rather than sharing code with
 * it directly - the two screens' headers and middle content differ enough (no search bar, no
 * smart lists here; hourly grid instead of a card list) that a generic slot-based abstraction
 * would have cost more review risk under deadline than the small amount of duplicated wiring.
 *
 * Tapping an empty hour opens task creation prefilled with the selected day and that hour;
 * tapping the FAB opens the same creation sheet without a preset time.
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
    val contentInset = layout.width(4.dp, 4.dp)
    val uiState = viewModel.uiState
    val dayUiState = viewModel.selectedDayUiState()
    val selectedDate = uiState.selectedDate
    var deviceToday by remember { mutableStateOf(currentDeviceDate()) }
    val sourceTasks = dayUiState.tasks
    var showTaskSheet by rememberSaveable { mutableStateOf(false) }
    var requestedTaskTime by rememberSaveable { mutableStateOf<LocalTime?>(null) }
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
        showTaskSheet -> TaskSheetMode.Create(selectedDate, requestedTaskTime)
        else -> null
    }

    LaunchedEffect(Unit) {
        controller.handleAsync(AgendaAction.RefreshSelectedDate)
    }

    LaunchedEffect(Unit) {
        while (true) {
            val currentDate = currentDeviceDate()
            if (currentDate != deviceToday) {
                deviceToday = currentDate
            }
            delay(60_000)
        }
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
                isToday = selectedDate == deviceToday,
                onPreviousDay = { controller.handleAsync(AgendaAction.MoveDay(-1)) },
                onNextDay = { controller.handleAsync(AgendaAction.MoveDay(1)) },
                onGoToToday = {
                    controller.handleAsync(AgendaAction.SelectDate(deviceToday))
                },
            )

            DayTimeline(
                selectedDate = selectedDate,
                tasks = sourceTasks,
                isLoading = dayUiState.isLoading,
                isToday = selectedDate == deviceToday,
                onTaskSelected = { task -> showTaskDetailsTaskId = task.id },
                onCreateRequested = { time ->
                    requestedTaskTime = time
                    showTaskSheet = true
                },
                // Reserve a visual dock below the timeline so the FAB floats on the screen,
                // rather than appearing embedded in the temporal surface.
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = layout.height(68.dp, 60.dp)),
            )
        }

        if (!showTaskSheet && showTaskDetails == null && editingTask == null) {
            DayFloatingAddButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .zIndex(2f)
                    .padding(
                        end = layout.width(16.dp, 14.dp) + contentInset,
                        bottom = layout.height(16.dp, 12.dp),
                    ),
                enabled = true,
                onClick = {
                    requestedTaskTime = null
                    showTaskSheet = true
                },
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
                    requestedTaskTime = null
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
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = "Día",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = layout.text(28.sp, 26.sp),
                    lineHeight = layout.text(32.sp, 30.sp),
                ),
                color = GlassTheme.tokens.textPrimary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatFullDate(selectedDate),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = layout.text(14.sp, 13.sp),
                        lineHeight = layout.text(18.sp, 17.sp),
                    ),
                    color = GlassTheme.tokens.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!isToday) {
                    Spacer(modifier = Modifier.width(8.dp))
                    GlassSurface(
                        modifier = Modifier
                            .defaultMinSize(minHeight = ControlHeight.standard())
                            .clickable(
                                role = Role.Button,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onGoToToday,
                            ),
                        shape = RoundedCornerShape(12.dp),
                        tint = GlassTheme.tokens.glassFillStrong,
                    ) {
                        Text(
                            text = "Hoy",
                            style = MaterialTheme.typography.labelMedium,
                            color = GlassTheme.tokens.textPrimary,
                            modifier = Modifier.padding(
                                    horizontal = 10.dp,
                                    vertical = 4.dp,
                                ),
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(layout.width(8.dp, 6.dp))) {
            DayNavigationButton(
                icon = Icons.Rounded.ChevronLeft,
                contentDescription = "Día anterior",
                onClick = onPreviousDay,
            )
            DayNavigationButton(
                icon = Icons.Rounded.ChevronRight,
                contentDescription = "Día siguiente",
                onClick = onNextDay,
            )
        }
    }
}

@Composable
private fun DayNavigationButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .zIndex(2f)
            .clickable(
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(15.dp),
            tint = GlassTheme.tokens.glassFillStrong,
            strokeColor = GlassTheme.tokens.glassStroke,
            shadowElevation = 2.dp,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = GlassTheme.tokens.textPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun DayFloatingAddButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    GlassSurface(
        modifier = modifier
            .size(56.dp)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(18.dp),
        tint = if (enabled) {
            GlassTheme.tokens.accent.copy(alpha = 0.18f)
        } else {
            GlassTheme.tokens.glassFillDisabled
        },
        strokeColor = if (enabled) {
            GlassTheme.tokens.accent.copy(alpha = 0.42f)
        } else {
            GlassTheme.tokens.glassStroke
        },
        shadowElevation = 2.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "Nueva tarea",
                tint = if (enabled) GlassTheme.tokens.accent else GlassTheme.tokens.textSecondary,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/**
 * Time-based day canvas. The vertical axis is derived from real time:
 *
 *     y = topPadding + minutesFromMidnight * dpPerMinute
 *
 * The day is therefore never compressed to fit the viewport. The viewport scrolls over a
 * stable 24-hour surface, which keeps hour markers, the current-time indicator and task cards
 * on the same coordinate system.
 */
@Composable
private fun DayTimeline(
    selectedDate: LocalDate,
    tasks: List<TaskItem>,
    isLoading: Boolean,
    isToday: Boolean,
    onTaskSelected: (TaskItem) -> Unit,
    onCreateRequested: (LocalTime?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = AppLayout.metrics
    val timeZone = TimeZone.currentSystemDefault()
    val timedTasks = tasks.filter { it.time != null }.sortedBy { it.time }
    val untimedTasks = tasks.filter { it.time == null }
    val tasksByHour = timedTasks.groupBy { it.time!!.hour }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    // A stable temporal scale: the viewport shows a slice of the day instead of squeezing all
    // hours into whatever height happens to be available.
    val hourHeight = layout.height(78.dp, 70.dp)
    val minuteHeight = hourHeight / 60f
    val timeColumnWidth = layout.width(44.dp, 40.dp)
    val laneGap = layout.width(10.dp, 8.dp)
    val topPadding = layout.height(18.dp, 16.dp)
    val bottomPadding = layout.height(64.dp, 56.dp)
    val timelineHeight = topPadding + (hourHeight * 24) + bottomPadding

    val now = if (isToday) {
        Instant.fromEpochMilliseconds(currentTimeMillis()).toLocalDateTime(timeZone)
    } else {
        null
    }
    val currentMinutes = now?.let { it.hour * 60 + it.minute }

    // ScrollState.maxValue is 0 before the first measurement. Keying the effect with maxValue
    // makes the initial positioning happen once the 24-hour canvas has actually been measured.
    LaunchedEffect(selectedDate, tasks, isToday, scrollState.maxValue) {
        if (scrollState.maxValue > 0) {
            val targetHour = when {
                isToday && now != null -> now.hour
                timedTasks.isNotEmpty() -> timedTasks.first().time!!.hour
                else -> 8
            }
            val hourToPlaceNearTop = (targetHour - 1).coerceIn(0, 23)
            val targetDp = topPadding + (hourHeight * hourToPlaceNearTop)
            scrollState.scrollTo(
                with(density) {
                    targetDp.toPx().roundToInt()
                },
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        GlassSurface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(24.dp),
            // Keep the alpha defined by the active theme. Replacing it here makes
            // the dark-mode panel use a bright gray fill instead of a subtle glass surface.
            tint = GlassTheme.tokens.glassFill,
            strokeColor = GlassTheme.tokens.glassStroke,
            shadowElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = layout.width(12.dp, 10.dp),
                        end = layout.width(12.dp, 10.dp),
                        top = layout.height(10.dp, 8.dp),
                        bottom = layout.height(10.dp, 8.dp),
                    ),
            ) {
                if (untimedTasks.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        untimedTasks.forEach { task ->
                            UntimedTaskChip(
                                task = task,
                                onClick = { onTaskSelected(task) },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scrollState),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(timelineHeight),
                    ) {
                        // Layer 1: hour hit areas + hour labels + grid lines.
                        repeat(24) { hour ->
                            val hourY = topPadding + (hourHeight * hour)

                            if (tasksByHour[hour].isNullOrEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = timeColumnWidth + laneGap)
                                        .offset(y = hourY)
                                        .height(hourHeight)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { onCreateRequested(LocalTime(hour, 0)) },
                                        ),
                                )
                            }

                            DayHourMarker(
                                hour = hour,
                                isCurrentHour = now?.hour == hour,
                                timeColumnWidth = timeColumnWidth,
                                laneGap = laneGap,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset(y = hourY - 12.dp),
                            )
                        }

                        // Layer 2: current-time indicator. It shares the exact same temporal axis
                        // as the event cards and hour markers.
                        if (currentMinutes != null && now != null) {
                            val currentY = topPadding + (minuteHeight * currentMinutes)
                            DayCurrentTimeIndicator(
                                label = formatTime(now.time),
                                timeColumnWidth = timeColumnWidth,
                                laneGap = laneGap,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset(y = currentY - 10.dp),
                            )
                        }

                        // Layer 3: tasks. They are deliberately composed after grid/current-time
                        // layers so their glass surface remains readable and grid lines never cut
                        // through the content.
                        timedTasks.forEach { task ->
                            val start = task.time!!
                            val startMinutes = start.hour * 60 + start.minute
                            val taskY = topPadding + (minuteHeight * startMinutes)

                            val durationMinutes = task.endTime?.let { endTime ->
                                val end = endTime.hour * 60 + endTime.minute
                                (if (end > startMinutes) {
                                    end - startMinutes
                                } else {
                                    24 * 60 - startMinutes + end
                                }).coerceIn(15, 180)
                            }

                            val naturalHeight = durationMinutes?.let { minutes ->
                                minuteHeight * minutes
                            } ?: layout.height(44.dp, 42.dp)

                            val cardHeight = naturalHeight
                                .coerceAtLeast(layout.height(44.dp, 42.dp))
                                .coerceAtMost(hourHeight * 3)

                            DayHourTaskCard(
                                task = task,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = timeColumnWidth + laneGap,
                                        end = layout.width(4.dp, 3.dp),
                                    )
                                    .offset(y = taskY)
                                    .height(cardHeight),
                                onClick = { onTaskSelected(task) },
                            )
                        }
                    }
                }
            }
        }

        if (isLoading) {
            GlassSurface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
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

@Composable
private fun DayHourMarker(
    hour: Int,
    isCurrentHour: Boolean,
    timeColumnWidth: Dp,
    laneGap: Dp,
    modifier: Modifier = Modifier,
) {
    val layout = AppLayout.metrics

    Row(
        modifier = modifier.height(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(timeColumnWidth),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                text = "${hour.toString().padStart(2, '0')}:00",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isCurrentHour) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = layout.text(12.sp, 11.sp),
                ),
                color = if (isCurrentHour) {
                    GlassTheme.tokens.textPrimary
                } else {
                    GlassTheme.tokens.textSecondary
                },
            )
        }

        Spacer(modifier = Modifier.width(laneGap))

        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    GlassTheme.tokens.divider.copy(
                        alpha = if (isCurrentHour) 0.78f else 0.48f,
                    ),
                ),
        )
    }
}

@Composable
private fun DayCurrentTimeIndicator(
    label: String,
    timeColumnWidth: Dp,
    laneGap: Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.height(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(timeColumnWidth + laneGap))

        GlassSurface(
            shape = RoundedCornerShape(8.dp),
            tint = GlassTheme.tokens.accent.copy(alpha = 0.18f),
            strokeColor = GlassTheme.tokens.accent.copy(alpha = 0.38f),
            shadowElevation = 0.dp,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = GlassTheme.tokens.accent,
                modifier = Modifier.padding(
                    horizontal = 5.dp,
                    vertical = 2.dp,
                ),
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(GlassTheme.tokens.accent.copy(alpha = 0.9f)),
        )
    }
}

@Composable
private fun DayHourTaskCard(
    task: TaskItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val layout = AppLayout.metrics
    val alpha = if (task.isDone) 0.55f else 1f
    val timeText = task.endTime?.let { endTime ->
        "${formatTime(task.time!!)}–${formatTime(endTime)}"
    } ?: formatTime(task.time!!)

    GlassSurface(
        modifier = Modifier
            .then(modifier)
            .alpha(alpha)
            .clickable(
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics {
                contentDescription = "$timeText, ${task.title}"
            },
        shape = RoundedCornerShape(13.dp),
        tint = GlassTheme.tokens.glassFillStrong,
        strokeColor = GlassTheme.tokens.glassStroke,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = layout.width(10.dp, 9.dp),
                    vertical = layout.height(5.dp, 4.dp),
                ),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = layout.text(14.sp, 13.sp),
                    lineHeight = layout.text(16.sp, 15.sp),
                ),
                color = GlassTheme.tokens.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = layout.text(11.sp, 10.sp),
                    ),
                    color = GlassTheme.tokens.textSecondary,
                    maxLines = 1,
                )

                task.labels.firstOrNull()?.let { label ->
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
        modifier = Modifier
            .defaultMinSize(minHeight = ControlHeight.standard())
            .clickable(
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

private fun currentDeviceDate(): LocalDate {
    return Instant
        .fromEpochMilliseconds(currentTimeMillis())
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
}
