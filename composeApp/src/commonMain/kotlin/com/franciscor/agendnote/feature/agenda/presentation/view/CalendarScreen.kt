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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.franciscor.agendnote.app.navigation.SectionHeader
import com.franciscor.agendnote.core.model.TaskItem
import com.franciscor.agendnote.core.platform.currentTimeMillis
import com.franciscor.agendnote.core.ui.components.GlassIconButton
import com.franciscor.agendnote.core.ui.components.GlassSurface
import com.franciscor.agendnote.core.ui.components.colorFromHex
import com.franciscor.agendnote.core.ui.layout.AppLayout
import com.franciscor.agendnote.core.ui.theme.GlassTheme
import com.franciscor.agendnote.feature.agenda.presentation.controller.AgendaController
import com.franciscor.agendnote.feature.agenda.presentation.model.AgendaAction
import com.franciscor.agendnote.feature.agenda.presentation.viewmodel.AgendaViewModel
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun CalendarScreen(
    viewModel: AgendaViewModel,
    controller: AgendaController,
    onOpenInAgenda: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = AppLayout.metrics
    val contentInset = layout.width(16.dp, 14.dp)
    val uiState = viewModel.uiState
    // Toggles between the month grid (see task accumulation across the month) and a single day
    // broken down by hour (see hallazgo original request: keep the month view, add an hourly
    // breakdown of a specific day instead of jumping to the flat Agenda list).
    var showDayView by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        controller.handleAsync(AgendaAction.LoadMonth(uiState.visibleMonth))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                horizontal = contentInset,
                vertical = layout.height(12.dp, 10.dp),
            ),
        verticalArrangement = Arrangement.spacedBy(layout.height(12.dp, 10.dp)),
    ) {
        if (showDayView) {
            val dayUiState = viewModel.selectedDayUiState()
            DayHourAgenda(
                selectedDate = uiState.selectedDate,
                tasks = dayUiState.tasks,
                isLoading = dayUiState.isLoading,
                isToday = uiState.selectedDate == viewModel.today(),
                onBack = { showDayView = false },
                onPreviousDay = { controller.handleAsync(AgendaAction.MoveDay(-1)) },
                onNextDay = { controller.handleAsync(AgendaAction.MoveDay(1)) },
                onOpenInAgenda = onOpenInAgenda,
                modifier = Modifier.weight(1f),
            )
        } else {
            SectionHeader("Calendario", "Consulta y navega tus tareas por mes")
            if (uiState.monthErrorMessage != null) {
                Text(
                    text = uiState.monthErrorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassTheme.tokens.error,
                )
            } else if (uiState.isMonthLoading) {
                Text(
                    text = "Cargando...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassTheme.tokens.textSecondary,
                )
            }
            CalendarMonthView(
                selectedDate = uiState.selectedDate,
                visibleMonth = uiState.visibleMonth,
                tasksByDate = uiState.tasksByDate,
                onSelectDate = { date ->
                    controller.handleAsync(AgendaAction.SelectDate(date))
                    showDayView = true
                },
                onVisibleMonthChange = { month ->
                    controller.handleAsync(AgendaAction.LoadMonth(month))
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Hour-by-hour breakdown of a single day, styled like a paper day planner: a ruled column of
 * hours down the left with tasks slotted into the hour they start at. Complements (does not
 * replace) [CalendarMonthView] — the month grid answers "which days are busy", this answers
 * "what does this specific day look like".
 */
@Composable
private fun DayHourAgenda(
    selectedDate: LocalDate,
    tasks: List<TaskItem>,
    isLoading: Boolean,
    isToday: Boolean,
    onBack: () -> Unit,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onOpenInAgenda: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = AppLayout.metrics
    val timeZone = TimeZone.currentSystemDefault()
    val timedTasks = tasks.filter { it.time != null }.sortedBy { it.time }
    val untimedTasks = tasks.filter { it.time == null }
    val tasksByHour = timedTasks.groupBy { it.time!!.hour }
    val listState = rememberLazyListState()
    val currentHour = if (isToday) {
        Instant.fromEpochMilliseconds(currentTimeMillis()).toLocalDateTime(timeZone).hour
    } else {
        null
    }

    // Land on the first task of the day (or "now" for today) instead of forcing a scroll from
    // midnight every time a day with an hourly schedule is opened.
    LaunchedEffect(selectedDate, tasks) {
        val targetHour = timedTasks.firstOrNull()?.time?.hour ?: currentHour ?: 8
        listState.scrollToItem((targetHour - 1).coerceIn(0, 23))
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(layout.width(10.dp, 8.dp)),
            ) {
                GlassIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Volver al mes",
                    onClick = onBack,
                )
                Column {
                    Text(
                        text = dayName(selectedDate.dayOfWeek),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = layout.text(20.sp, 18.sp),
                        ),
                        color = GlassTheme.tokens.textPrimary,
                    )
                    Text(
                        text = formatFullDate(selectedDate),
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTheme.tokens.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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

        Spacer(modifier = Modifier.height(layout.height(10.dp, 8.dp)))

        if (untimedTasks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(layout.width(8.dp, 6.dp)),
            ) {
                untimedTasks.forEach { task ->
                    UntimedTaskChip(task = task)
                }
            }
            Spacer(modifier = Modifier.height(layout.height(10.dp, 8.dp)))
        }

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = layout.height(24.dp, 20.dp)),
            ) {
                items(24) { hour ->
                    HourRow(
                        hour = hour,
                        tasks = tasksByHour[hour].orEmpty(),
                        isCurrentHour = hour == currentHour,
                    )
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

        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                // The bottom nav bar is a floating overlay (see AppNavHost) that doesn't reserve
                // layout space of its own — every screen is responsible for clearing it, same as
                // the contentPadding(bottom = ...) used by the other tabs' scrollable lists.
                .padding(bottom = layout.height(90.dp, 80.dp))
                .clip(RoundedCornerShape(layout.size(16.dp, 14.dp)))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenInAgenda,
                ),
            shape = RoundedCornerShape(layout.size(16.dp, 14.dp)),
            tint = GlassTheme.tokens.glassFill,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = layout.width(16.dp, 14.dp),
                        vertical = layout.height(12.dp, 10.dp),
                    ),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.EventAvailable,
                    contentDescription = null,
                    tint = GlassTheme.tokens.textSecondary,
                    modifier = Modifier.size(layout.size(18.dp, 16.dp)),
                )
                Spacer(modifier = Modifier.width(layout.width(8.dp, 6.dp)))
                Text(
                    text = "Ver este día en Agenda",
                    style = MaterialTheme.typography.labelMedium,
                    color = GlassTheme.tokens.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun HourRow(
    hour: Int,
    tasks: List<TaskItem>,
    isCurrentHour: Boolean,
) {
    val layout = AppLayout.metrics
    val rowBackground = if (isCurrentHour) GlassTheme.tokens.glassFill else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = layout.height(60.dp, 52.dp))
            .background(rowBackground),
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
            tasks.forEach { task ->
                HourTaskCard(task = task)
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
}

@Composable
private fun HourTaskCard(task: TaskItem) {
    val layout = AppLayout.metrics
    val alpha = if (task.isDone) 0.55f else 1f
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha),
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
                        .size(layout.size(8.dp, 7.dp))
                        .clip(CircleShape)
                        .background(colorFromHex(label.colorHex)),
                )
            }
        }
    }
}

@Composable
private fun UntimedTaskChip(task: TaskItem) {
    val layout = AppLayout.metrics
    GlassSurface(
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
