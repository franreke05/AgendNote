package com.franciscor.agendnote.feature.agenda.presentation.view

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.franciscor.agendnote.core.model.TaskItem
import com.franciscor.agendnote.core.ui.components.GlassIconButton
import com.franciscor.agendnote.core.ui.components.GlassSearchBar
import com.franciscor.agendnote.core.ui.components.GlassSurface
import com.franciscor.agendnote.core.ui.components.colorFromHex
import com.franciscor.agendnote.core.ui.layout.AppLayout
import com.franciscor.agendnote.core.ui.theme.GlassTheme
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun AgendaHeader(
    selectedDate: LocalDate,
    isToday: Boolean,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
) {
    val layout = AppLayout.metrics
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = "Agenda",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = layout.text(36.sp, 32.sp),
                    lineHeight = layout.text(38.sp, 34.sp),
                ),
                color = GlassTheme.tokens.textPrimary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatFullDate(selectedDate),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = layout.text(16.sp, 15.sp),
                    ),
                    color = GlassTheme.tokens.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isToday) {
                    Spacer(modifier = Modifier.width(layout.width(10.dp, 8.dp)))
                    GlassSurface(
                        shape = RoundedCornerShape(layout.size(16.dp, 14.dp)),
                        tint = GlassTheme.tokens.glassFillStrong,
                        modifier = Modifier.padding(top = layout.height(2.dp, 2.dp)),
                    ) {
                        Text(
                            text = "Hoy",
                            style = MaterialTheme.typography.labelMedium,
                            color = GlassTheme.tokens.textPrimary,
                            modifier = Modifier.padding(
                                horizontal = layout.width(14.dp, 12.dp),
                                vertical = layout.height(7.dp, 6.dp),
                            ),
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(layout.width(10.dp, 8.dp))) {
            GlassIconButton(
                icon = Icons.Rounded.ChevronLeft,
                contentDescription = "Dia anterior",
                onClick = onPreviousDay,
            )
            GlassIconButton(
                icon = Icons.Rounded.ChevronRight,
                contentDescription = "Dia siguiente",
                onClick = onNextDay,
            )
        }
    }
}

@Composable
internal fun AgendaSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSearchBar(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
    )
}

@Composable
internal fun DayAgenda(
    selectedDate: LocalDate,
    tasks: List<TaskItem>,
    hasSourceTasks: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    searchQuery: String,
    isEditingEnabled: Boolean,
    onToggleDone: (TaskItem, Boolean) -> Unit,
    onRequestDelete: (TaskItem) -> Unit,
    onTaskSelected: (TaskItem) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val layout = AppLayout.metrics
    val showRefreshing = isLoading && hasSourceTasks
    val showInitialLoader = isLoading && !hasSourceTasks
    val showEmptyState = !isLoading && tasks.isEmpty() && errorMessage == null

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(layout.height(12.dp, 10.dp)),
        contentPadding = PaddingValues(bottom = layout.height(128.dp, 108.dp)),
    ) {
        item {
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = layout.height(84.dp, 74.dp)),
                shape = RoundedCornerShape(layout.size(28.dp, 24.dp)),
                tint = GlassTheme.tokens.glassFillStrong,
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = layout.width(20.dp, 16.dp),
                        vertical = layout.height(16.dp, 14.dp),
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = formatDayTitle(selectedDate),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = layout.text(22.sp, 19.sp),
                            ),
                            color = GlassTheme.tokens.textPrimary,
                        )
                        Text(
                            text = formatShortDate(selectedDate),
                            style = MaterialTheme.typography.bodyMedium,
                            color = GlassTheme.tokens.textSecondary,
                        )
                    }
                }
            }
        }

        if (showInitialLoader) {
            item {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(layout.size(20.dp, 18.dp)),
                ) {
                    Text(
                        text = "Cargando tareas...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTheme.tokens.textSecondary,
                        modifier = Modifier.padding(
                            horizontal = layout.width(16.dp, 14.dp),
                            vertical = layout.height(14.dp, 12.dp),
                        ),
                    )
                }
            }
        }

        if (errorMessage != null && !showInitialLoader) {
            item {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(layout.size(20.dp, 18.dp)),
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTheme.tokens.error,
                        modifier = Modifier.padding(
                            horizontal = layout.width(16.dp, 14.dp),
                            vertical = layout.height(14.dp, 12.dp),
                        ),
                    )
                }
            }
        }

        if (showRefreshing) {
            item {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(layout.size(20.dp, 18.dp)),
                ) {
                    Text(
                        text = "Actualizando tareas...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTheme.tokens.textSecondary,
                        modifier = Modifier.padding(
                            horizontal = layout.width(16.dp, 14.dp),
                            vertical = layout.height(14.dp, 12.dp),
                        ),
                    )
                }
            }
        }

        if (showEmptyState) {
            item {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(layout.size(20.dp, 18.dp)),
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) "Sin tareas para este dia" else "Sin resultados",
                        style = MaterialTheme.typography.bodyLarge,
                        color = GlassTheme.tokens.textSecondary,
                        modifier = Modifier.padding(
                            horizontal = layout.width(16.dp, 14.dp),
                            vertical = layout.height(18.dp, 14.dp),
                        ),
                    )
                }
            }
        } else {
            items(tasks, key = { it.id }) { task ->
                SwipeableTaskCard(
                    task = task,
                    isEditingEnabled = isEditingEnabled,
                    onRequestDelete = { onRequestDelete(task) },
                    onToggleDone = { done -> onToggleDone(task, done) },
                    onTaskSelected = { onTaskSelected(task) },
                )
            }
        }
    }
}

@Composable
private fun SwipeableTaskCard(
    task: TaskItem,
    isEditingEnabled: Boolean,
    onRequestDelete: () -> Unit,
    onToggleDone: (Boolean) -> Unit,
    onTaskSelected: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val layout = AppLayout.metrics
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val maxOffset = with(density) { layout.width(108.dp, 88.dp).toPx() }
    val threshold = with(density) { layout.width(72.dp, 60.dp).toPx() }
    var offsetX by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var isPerformingAction by remember { mutableStateOf(false) }

    val animatedOffset by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = if (isDragging) {
            tween(durationMillis = 0)
        } else {
            tween(durationMillis = 180, easing = FastOutSlowInEasing)
        },
        label = "taskSwipeOffset",
    )
    val isSwipeRight = animatedOffset >= 0f
    val progress = (abs(animatedOffset) / maxOffset).coerceIn(0f, 1f)

    val swipeModifier = if (isEditingEnabled) {
        Modifier.pointerInput(task.id, isPerformingAction) {
            if (isPerformingAction) return@pointerInput
            detectHorizontalDragGestures(
                onHorizontalDrag = { _, dragAmount ->
                    if (isPerformingAction) return@detectHorizontalDragGestures
                    isDragging = true
                    offsetX = (offsetX + dragAmount).coerceIn(-maxOffset, maxOffset)
                },
                onDragEnd = {
                    if (isPerformingAction) return@detectHorizontalDragGestures
                    isDragging = false
                    when {
                        offsetX > threshold -> {
                            if (task.isDone) {
                                offsetX = 0f
                            } else {
                                offsetX = maxOffset
                                scope.launch {
                                    isPerformingAction = true
                                    onToggleDone(true)
                                    isPerformingAction = false
                                    offsetX = 0f
                                }
                            }
                        }

                        offsetX < -threshold -> {
                            offsetX = 0f
                            onRequestDelete()
                        }

                        else -> offsetX = 0f
                    }
                },
                onDragCancel = {
                    if (isPerformingAction) return@detectHorizontalDragGestures
                    isDragging = false
                    offsetX = 0f
                },
            )
        }
    } else {
        Modifier
    }

    Box(modifier = modifier.fillMaxWidth()) {
        SwipeActionBackground(
            isSwipeRight = isSwipeRight,
            progress = progress,
            modifier = Modifier.fillMaxWidth(),
        )
        TaskCard(
            task = task,
            onToggleDone = onToggleDone,
            onRequestDelete = onRequestDelete,
            onTaskSelected = onTaskSelected,
            modifier = Modifier
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .then(swipeModifier),
        )
    }
}

@Composable
private fun SwipeActionBackground(
    isSwipeRight: Boolean,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    if (progress <= 0.01f) return
    val layout = AppLayout.metrics
    val actionColor = if (isSwipeRight) Color(0xFF43B87B) else Color(0xFFE06B6B)
    val label = if (isSwipeRight) "Hecha" else "Eliminar"
    val icon = if (isSwipeRight) Icons.Rounded.Check else Icons.Rounded.Delete
    val eased = progress.coerceIn(0f, 1f)
    val fillAlpha = 0.65f * eased
    val strokeAlpha = 0.85f * eased
    val contentAlpha = 0.2f + (0.8f * eased)

    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(layout.size(30.dp, 24.dp)),
        tint = actionColor.copy(alpha = fillAlpha),
        strokeColor = actionColor.copy(alpha = strokeAlpha),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = layout.width(18.dp, 14.dp),
                    vertical = layout.height(14.dp, 12.dp),
                ),
            horizontalArrangement = if (isSwipeRight) Arrangement.Start else Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSwipeRight) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color.White.copy(alpha = contentAlpha),
                )
                Spacer(modifier = Modifier.width(layout.width(8.dp, 6.dp)))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = contentAlpha),
                )
            } else {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = contentAlpha),
                )
                Spacer(modifier = Modifier.width(layout.width(8.dp, 6.dp)))
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color.White.copy(alpha = contentAlpha),
                )
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: TaskItem,
    onToggleDone: (Boolean) -> Unit,
    onRequestDelete: () -> Unit,
    onTaskSelected: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val layout = AppLayout.metrics
    val alpha = if (task.isDone) 0.6f else 1f

    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = layout.height(112.dp, 96.dp))
            .alpha(alpha)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTaskSelected,
            ),
        shape = RoundedCornerShape(layout.size(28.dp, 24.dp)),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = layout.width(18.dp, 16.dp),
                vertical = layout.height(14.dp, 12.dp),
            ),
            verticalArrangement = Arrangement.spacedBy(layout.height(10.dp, 8.dp)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(layout.width(6.dp, 4.dp)),
                ) {
                    TimeChip(startTime = task.time, endTime = task.endTime)
                    if (task.seriesId != null) {
                        Icon(
                            imageVector = Icons.Rounded.Repeat,
                            contentDescription = "Tarea recurrente",
                            tint = GlassTheme.tokens.textSecondary,
                            modifier = Modifier.size(layout.size(16.dp, 14.dp)),
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(layout.width(6.dp, 4.dp)),
                ) {
                    task.labels.forEach { label ->
                        LabelChip(label = label)
                    }
                }
            }

            Text(
                text = task.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = layout.text(19.sp, 17.sp),
                    lineHeight = layout.text(21.sp, 19.sp),
                ),
                color = GlassTheme.tokens.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (!task.details.isNullOrBlank()) {
                Text(
                    text = task.details,
                    style = MaterialTheme.typography.bodyLarge,
                    color = GlassTheme.tokens.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Always-visible, non-gestural alternative to the swipe-to-complete /
            // swipe-to-delete actions above, for screen reader users and anyone who
            // cannot reliably perform a drag gesture.
            TaskCardActions(
                isDone = task.isDone,
                onToggleDone = onToggleDone,
                onRequestDelete = onRequestDelete,
            )
        }
    }
}

@Composable
private fun TaskCardActions(
    isDone: Boolean,
    onToggleDone: (Boolean) -> Unit,
    onRequestDelete: () -> Unit,
) {
    val layout = AppLayout.metrics
    val controlSize = layout.size(36.dp, 32.dp)
    val doneColor = if (isDone) Color(0xFF43B87B) else GlassTheme.tokens.textSecondary
    val doneDescription = if (isDone) "Marcar como pendiente" else "Marcar como hecha"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(controlSize)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onToggleDone(!isDone) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = doneDescription,
                tint = doneColor,
                modifier = Modifier.size(layout.size(20.dp, 18.dp)),
            )
        }
        Spacer(modifier = Modifier.width(layout.width(4.dp, 4.dp)))
        Box(
            modifier = Modifier
                .size(controlSize)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onRequestDelete,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = "Eliminar tarea",
                tint = GlassTheme.tokens.error,
                modifier = Modifier.size(layout.size(20.dp, 18.dp)),
            )
        }
    }
}

@Composable
private fun TimeChip(startTime: LocalTime?, endTime: LocalTime?) {
    val layout = AppLayout.metrics
    val text = when {
        startTime != null && endTime != null -> "${formatTime(startTime)}-${formatTime(endTime)}"
        startTime != null -> formatTime(startTime)
        else -> "Sin hora"
    }
    val tint = GlassTheme.tokens.glassFillStrong
    GlassSurface(
        shape = RoundedCornerShape(layout.size(16.dp, 14.dp)),
        tint = tint,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = GlassTheme.tokens.textPrimary,
            modifier = Modifier.padding(
                horizontal = layout.width(14.dp, 12.dp),
                vertical = layout.height(8.dp, 7.dp),
            ),
        )
    }
}

@Composable
private fun LabelChip(label: com.franciscor.agendnote.core.model.LabelTag) {
    val layout = AppLayout.metrics
    val color = colorFromHex(label.colorHex)
    GlassSurface(
        shape = RoundedCornerShape(layout.size(16.dp, 14.dp)),
        tint = GlassTheme.tokens.glassFillStrong,
        strokeColor = GlassTheme.tokens.glassStroke,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = layout.width(12.dp, 10.dp),
                vertical = layout.height(7.dp, 6.dp),
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(layout.width(6.dp, 5.dp)),
        ) {
            Box(
                modifier = Modifier
                    .size(layout.size(8.dp, 7.dp))
                    .clip(CircleShape)
                    .background(color),
            )
            Text(
                text = label.name,
                style = MaterialTheme.typography.labelMedium,
                color = GlassTheme.tokens.textPrimary,
            )
        }
    }
}

@Composable
internal fun FloatingAddButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val layout = AppLayout.metrics
    val tint = if (enabled) GlassTheme.tokens.glassFillStrong else GlassTheme.tokens.glassFill
    val iconTint = if (enabled) GlassTheme.tokens.textPrimary else GlassTheme.tokens.textSecondary
    GlassSurface(
        modifier = modifier
            .size(layout.size(64.dp, 58.dp))
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        shape = CircleShape,
        tint = tint,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(tint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "Nueva tarea",
                tint = iconTint,
                modifier = Modifier.size(layout.size(28.dp, 24.dp)),
            )
        }
    }
}

private fun formatFullDate(date: LocalDate): String {
    return "${dayName(date.dayOfWeek)}, ${date.dayOfMonth} de ${monthName(date.month)}"
}

private fun formatDayTitle(date: LocalDate): String = dayName(date.dayOfWeek)

private fun formatShortDate(date: LocalDate): String {
    return "${date.dayOfMonth} ${monthName(date.month, short = true)}"
}

internal fun filterTasks(tasks: List<TaskItem>, query: String): List<TaskItem> {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return tasks
    val needle = trimmed.lowercase()
    return tasks.filter { task ->
        task.title.lowercase().contains(needle) ||
            (task.details?.lowercase()?.contains(needle) == true) ||
            task.labels.any { it.name.lowercase().contains(needle) }
    }
}
