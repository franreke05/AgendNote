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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.franciscor.agendnote.core.model.TaskItem
import com.franciscor.agendnote.core.ui.components.GlassActionButton
import com.franciscor.agendnote.core.ui.components.GlassEmptyState
import com.franciscor.agendnote.core.ui.components.GlassSearchBar
import com.franciscor.agendnote.core.ui.components.GlassSurface
import com.franciscor.agendnote.core.ui.components.colorFromHex
import com.franciscor.agendnote.core.ui.layout.AppLayout
import com.franciscor.agendnote.core.ui.theme.ControlHeight
import com.franciscor.agendnote.core.ui.theme.GlassTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

@Composable
internal fun AgendaHeader(
    selectedDate: LocalDate,
    isToday: Boolean,
    onPreviousDay: () -> Unit = {},
    onNextDay: () -> Unit = {},
    onOpenSmartLists: () -> Unit = {},
    // Operación Aniversario: the month calendar moved out of its own tab into a popover reached
    // from here (see AgendaScreen's showCalendarPopover). The date is the primary context;
    // secondary tools and day navigation stay grouped in the right-side action grid.
    onOpenCalendar: () -> Unit = {},
) {
    val layout = AppLayout.metrics
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = layout.height(2.dp, 1.dp)),
                verticalArrangement = Arrangement.spacedBy(layout.height(6.dp, 4.dp)),
            ) {
                Text(
                    text = formatFullDate(selectedDate),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = layout.text(28.sp, 25.sp),
                        lineHeight = layout.text(32.sp, 29.sp),
                    ),
                    color = GlassTheme.tokens.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isToday) {
                    GlassSurface(
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                        tint = GlassTheme.tokens.glassFillStrong,
                    ) {
                        Text(
                            text = "Hoy",
                            style = MaterialTheme.typography.labelMedium,
                            color = GlassTheme.tokens.textPrimary,
                            modifier = Modifier.padding(
                                horizontal = layout.width(10.dp, 9.dp),
                                vertical = layout.height(4.dp, 3.dp),
                            ),
                        )
                    }
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(layout.height(8.dp, 6.dp)),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(layout.width(8.dp, 6.dp))) {
                    AgendaHeaderActionButton(
                        icon = Icons.Rounded.Checklist,
                        contentDescription = "Listas inteligentes",
                        onClick = onOpenSmartLists,
                    )
                    AgendaHeaderActionButton(
                        icon = Icons.Rounded.CalendarMonth,
                        contentDescription = "Ver mes",
                        onClick = onOpenCalendar,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(layout.width(8.dp, 6.dp))) {
                    AgendaHeaderActionButton(
                        icon = Icons.Rounded.ChevronLeft,
                        contentDescription = "Día anterior",
                        onClick = onPreviousDay,
                    )
                    AgendaHeaderActionButton(
                        icon = Icons.Rounded.ChevronRight,
                        contentDescription = "Día siguiente",
                        onClick = onNextDay,
                    )
                }
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
private fun AgendaHeaderActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(
                role = Role.Button,
                onClickLabel = contentDescription,
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
                Icon(
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
internal fun DayAgenda(
    tasks: List<TaskItem>,
    hasSourceTasks: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    searchQuery: String,
    onRetry: () -> Unit,
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
        contentPadding = PaddingValues(bottom = layout.height(88.dp, 76.dp)),
    ) {
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
                    Column(
                        modifier = Modifier.padding(
                            horizontal = layout.width(16.dp, 14.dp),
                            vertical = layout.height(14.dp, 12.dp),
                        ),
                        verticalArrangement = Arrangement.spacedBy(layout.height(10.dp, 8.dp)),
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = GlassTheme.tokens.errorContent,
                        )
                        GlassActionButton(
                            text = "Reintentar",
                            onClick = onRetry,
                            tint = GlassTheme.tokens.glassFillStrong,
                            textColor = GlassTheme.tokens.textPrimary,
                        )
                    }
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
                Box(
                    // Keep the empty state visually connected to the search field and the FAB
                    // instead of centering it in an oversized dead zone.
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillParentMaxHeight(0.5f),
                    contentAlignment = Alignment.Center,
                ) {
                    GlassEmptyState(
                        icon = Icons.Rounded.EventAvailable,
                        title = if (searchQuery.isBlank()) "Sin tareas para este día" else "Sin resultados",
                        subtitle = "Toca + para crear tu primera tarea de este día".takeIf { searchQuery.isBlank() },
                    )
                }
            }
        } else {
            items(tasks, key = { it.id }) { task ->
                TaskCard(
                    task = task,
                    onRequestDelete = { onRequestDelete(task) },
                    onToggleDone = { done -> onToggleDone(task, done) },
                    onTaskSelected = { onTaskSelected(task) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// P0 UX fix (Operación Aniversario, 2026-08-11): horizontal swipe on a task row (complete right,
// delete left, via SwipeableTaskCard + SwipeActionBackground) is gone. Product decision: ALL
// horizontal drag in AgendNote is reserved for tab navigation (Agenda <-> Día <-> Etiquetas <->
// Ajustes) - a task row must never compete for that gesture. Completing/deleting a task now only
// happens through the explicit, always-visible check/delete controls in TaskCardActions below
// (unchanged by this fix - it already existed alongside the swipe gesture, not introduced by
// this fix), or from TaskDetailsOverlay. See docs/OPERATION_ANNIVERSARY_STATUS.md.

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
    // Merges the card's scattered child nodes (time chip, label chips, title, details) into one
    // VoiceOver/TalkBack stop with a coherent description, instead of reading each fragment
    // separately. TaskCardActions' own clickables (complete/delete) surface as accessibility
    // custom actions on this merged node rather than disappearing - the standard Compose pattern
    // for a row with a primary tap target plus secondary actions.
    val accessibilityDescription = buildString {
        append(task.title)
        task.time?.let { append(", ${formatTime(it)}") }
        if (task.isDone) append(", completada")
    }

    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTaskSelected,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityDescription
            },
        shape = RoundedCornerShape(20.dp),
        tint = GlassTheme.tokens.glassFill,
        strokeColor = GlassTheme.tokens.glassStroke,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TimeChip(startTime = task.time, endTime = task.endTime)
                    if (task.seriesId != null) {
                        Icon(
                            imageVector = Icons.Rounded.Repeat,
                            contentDescription = "Tarea recurrente",
                            tint = GlassTheme.tokens.textSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                if (task.labels.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        task.labels.forEachIndexed { index, label ->
                            if (index > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            LabelChip(label = label)
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = layout.text(16.sp, 15.sp),
                            lineHeight = layout.text(20.sp, 19.sp),
                        ),
                        color = GlassTheme.tokens.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    TaskCardActions(
                        isDone = task.isDone,
                        onToggleDone = onToggleDone,
                        onRequestDelete = onRequestDelete,
                    )
                }

                if (!task.details.isNullOrBlank()) {
                    Text(
                        text = task.details,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = layout.text(14.sp, 13.sp),
                            lineHeight = layout.text(18.sp, 17.sp),
                        ),
                        color = GlassTheme.tokens.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (task.subtasks.isNotEmpty()) {
                    val doneCount = task.subtasks.count { it.isDone }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            contentDescription = "$doneCount de ${task.subtasks.size} subtareas hechas"
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = GlassTheme.tokens.textSecondary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "$doneCount/${task.subtasks.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = GlassTheme.tokens.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCardActions(
    isDone: Boolean,
    onToggleDone: (Boolean) -> Unit,
    onRequestDelete: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    // Keep the icon visually quiet while preserving the mobile accessibility target.
    val controlSize = ControlHeight.standard()
    val doneColor = if (isDone) GlassTheme.tokens.success else GlassTheme.tokens.textSecondary
    val doneDescription = if (isDone) "Marcar como pendiente" else "Marcar como hecha"

    Row(
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
                    onClick = {
                        // Only HapticFeedbackType.LongPress is confirmed bridged to iOS's
                        // UIFeedbackGenerator by Compose Multiplatform as of this project's
                        // version (see docs/OPERATION_ANNIVERSARY_STATUS.md) - using it uniformly
                        // for both actions here rather than guessing at differentiated
                        // intensity types that can't be verified without a device.
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleDone(!isDone)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = doneDescription,
                tint = doneColor,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(controlSize)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRequestDelete()
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = "Eliminar tarea",
                tint = GlassTheme.tokens.errorContent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun TimeChip(startTime: LocalTime?, endTime: LocalTime?) {
    val text = when {
        startTime != null && endTime != null -> "${formatTime(startTime)}-${formatTime(endTime)}"
        startTime != null -> formatTime(startTime)
        else -> "Sin hora"
    }
    val tint = GlassTheme.tokens.glassFillStrong
    GlassSurface(
        shape = RoundedCornerShape(14.dp),
        tint = tint,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = GlassTheme.tokens.textPrimary,
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = 6.dp,
            ),
        )
    }
}

@Composable
private fun LabelChip(label: com.franciscor.agendnote.core.model.LabelTag) {
    val color = colorFromHex(label.colorHex)
    GlassSurface(
        shape = RoundedCornerShape(13.dp),
        tint = GlassTheme.tokens.glassFillStrong,
        strokeColor = GlassTheme.tokens.glassStroke,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 10.dp,
                vertical = 5.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
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
    val interactionSource = remember { MutableInteractionSource() }
    // Operación Aniversario, prioridad P0 visual explícita: el FAB es la acción primaria de
    // toda la app y hasta ahora era un círculo blanco plano indistinguible de un botón
    // secundario. Pasa a cristal translúcido con tinte coral (en vez de glassFillStrong neutro)
    // + borde + un ligero press-scale - ver el contrato de tokens Glass en
    // docs/OPERATION_ANNIVERSARY_STATUS.md (GlassFloatingActionButton).
    GlassSurface(
        modifier = modifier
            .size(56.dp)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
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
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "Nueva tarea",
                tint = if (enabled) GlassTheme.tokens.accent else GlassTheme.tokens.textSecondary,
                modifier = Modifier.size(28.dp),
            )
        }
    }
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
