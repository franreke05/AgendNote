package com.franciscor.agendnote.feature.agenda.presentation.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.franciscor.agendnote.core.model.LabelTag
import com.franciscor.agendnote.core.model.TaskDraft
import com.franciscor.agendnote.core.model.TaskItem
import com.franciscor.agendnote.core.platform.currentTimeMillis
import com.franciscor.agendnote.core.ui.components.ColorSwatch
import com.franciscor.agendnote.core.ui.components.GlassActionButton
import com.franciscor.agendnote.core.ui.components.GlassConfirmDialog
import com.franciscor.agendnote.core.ui.components.GlassIconButton
import com.franciscor.agendnote.core.ui.components.GlassSurface
import com.franciscor.agendnote.core.ui.components.GlassTextField
import com.franciscor.agendnote.core.ui.components.colorFromHex
import com.franciscor.agendnote.core.ui.components.labelColorPalette
import com.franciscor.agendnote.core.ui.components.labelColorName
import com.franciscor.agendnote.core.ui.layout.AppLayout
import com.franciscor.agendnote.core.ui.theme.GlassTheme
import com.franciscor.agendnote.feature.agenda.domain.RecurrenceRule
import com.franciscor.agendnote.feature.agenda.presentation.model.SaveResult
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs

@Composable
internal fun ConfirmDeleteDialog(
    task: TaskItem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassConfirmDialog(
        visible = true,
        title = "¿Eliminar tarea?",
        message = "Se borrará \"${task.title}\"",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        confirmText = "Eliminar",
    )
}

private enum class RecurrenceOption {
    None, Daily, WeeklyDays, Monthly
}

@Composable
internal fun NewTaskSheet(
    date: LocalDate,
    labels: List<LabelTag>,
    onCreateLabel: suspend (String, String) -> LabelTag?,
    onDismiss: () -> Unit,
    onSave: (LocalDate, TaskDraft, (SaveResult) -> Unit) -> Unit,
    onSaveRecurring: (LocalDate, TaskDraft, RecurrenceRule, (SaveResult) -> Unit) -> Unit,
) {
    val layout = AppLayout.metrics
    val timeZone = remember { TimeZone.currentSystemDefault() }
    val today = remember { currentDate(timeZone) }
    val palette = remember { labelColorPalette() }
    val usedColors = labels.map { it.colorHex.lowercase() }.toSet()
    val colorOptions = palette
        .filterNot { usedColors.contains(it.lowercase()) }
        .distinct()
        .ifEmpty { palette.distinct() }

    var title by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var selectedTime by remember { mutableStateOf<LocalTime?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var selectedDate by remember(date, today) { mutableStateOf(if (date < today) today else date) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var deadlineDate by remember { mutableStateOf<LocalDate?>(null) }
    var showDeadlinePicker by remember { mutableStateOf(false) }
    val selectedLabelIds = remember { mutableStateListOf<String>() }
    var newLabelName by remember { mutableStateOf("") }
    var isCreatingLabel by remember { mutableStateOf(false) }
    var selectedColor by remember { mutableStateOf(colorOptions.first()) }
    var isSaving by remember { mutableStateOf(false) }
    var selectedRecurrence by remember { mutableStateOf<RecurrenceOption>(RecurrenceOption.None) }
    val selectedWeekDays = remember { mutableStateListOf<DayOfWeek>() }
    var monthDay by remember(date, today) {
        mutableStateOf((if (date < today) today else date).dayOfMonth)
    }
    val scope = rememberCoroutineScope()

    // REVIEW: do not reset the draft when labels/colors change. Creating an inline label updates
    // colorOptions while this sheet is open; the previous effect erased title, notes, time,
    // recurrence and date at exactly that moment.
    LaunchedEffect(colorOptions) {
        if (!colorOptions.contains(selectedColor)) {
            selectedColor = colorOptions.first()
        }
    }

    val isPastSelected = selectedDate < today
    val sheetBlur = if (showTimePicker) layout.size(14.dp, 10.dp) else 0.dp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize().safeContentPadding()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(sheetBlur),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(GlassTheme.tokens.scrim)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss,
                        ),
                )

                GlassSurface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            start = layout.width(18.dp, 16.dp),
                            end = layout.width(18.dp, 16.dp),
                            top = layout.height(16.dp, 14.dp),
                            bottom = layout.height(16.dp, 14.dp),
                        )
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(layout.size(32.dp, 28.dp)),
                    // REVIEW: regular glass is intentionally translucent, but a long form needs
                    // an almost-opaque modal material so underlying task copy stays unreadable.
                    tint = GlassTheme.tokens.modalFill,
                ) {
                    Column(
                        modifier = Modifier
                            .padding(layout.size(16.dp, 14.dp))
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(layout.height(12.dp, 10.dp)),
                    ) {
                        // REVIEW: the previous single horizontal header gave the title less than
                        // 90 dp after reserving two fixed-width buttons, so it wrapped one letter
                        // per line. Stacking the identity/date above a full-width action row is
                        // stable down to compact phones.
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(layout.height(6.dp, 5.dp)),
                        ) {
                            Text(
                                text = "Nueva tarea",
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontSize = layout.text(30.sp, 27.sp),
                                    lineHeight = layout.text(32.sp, 29.sp),
                                ),
                                color = GlassTheme.tokens.textPrimary,
                            )
                            GlassSurface(
                                modifier = Modifier
                                    .width(layout.width(128.dp, 114.dp))
                                    .height(layout.height(48.dp, 48.dp))
                                    .clip(RoundedCornerShape(layout.size(16.dp, 14.dp)))
                                    .clickable(
                                        role = Role.Button,
                                        onClickLabel = "Cambiar fecha",
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { showDatePicker = true },
                                    ),
                                shape = RoundedCornerShape(layout.size(16.dp, 14.dp)),
                                tint = GlassTheme.tokens.glassFill,
                                shadowElevation = 0.dp,
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = layout.width(12.dp, 10.dp),
                                        vertical = layout.height(8.dp, 7.dp),
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(layout.width(6.dp, 5.dp)),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CalendarToday,
                                        contentDescription = "Seleccionar fecha",
                                        tint = GlassTheme.tokens.textPrimary,
                                        modifier = Modifier.size(layout.size(18.dp, 16.dp)),
                                    )
                                    Text(
                                        text = formatShortDateWithYear(selectedDate),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontSize = layout.text(13.sp, 12.sp),
                                        ),
                                        color = GlassTheme.tokens.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(layout.width(8.dp, 6.dp)),
                            verticalAlignment = Alignment.Top,
                        ) {
                            GlassActionButton(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(layout.height(50.dp, 48.dp)),
                                text = "Cancelar",
                                tint = GlassTheme.tokens.glassFillStrong,
                                textColor = GlassTheme.tokens.textPrimary,
                                onClick = onDismiss,
                            )
                            GlassActionButton(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(layout.height(50.dp, 48.dp)),
                                text = if (isSaving) "Guardando..." else "Guardar",
                                enabled = title.isNotBlank() && !isSaving && !isPastSelected,
                                onClick = {
                                    val trimmedTitle = title.trim()
                                    if (trimmedTitle.isEmpty()) {
                                        errorText = "Título requerido"
                                        return@GlassActionButton
                                    }
                                    if (selectedDate < today) {
                                        errorText = "No se pueden crear tareas en fechas pasadas"
                                        return@GlassActionButton
                                    }
                                    if (selectedRecurrence == RecurrenceOption.WeeklyDays && selectedWeekDays.isEmpty()) {
                                        errorText = "Elegí al menos un día de la semana"
                                        return@GlassActionButton
                                    }

                                    val chosenLabels = labels.filter { selectedLabelIds.contains(it.id) }
                                    // End-of-day deadline: the user only picks a date (see the
                                    // "Fecha límite" field below), not a time - a deadline of
                                    // "today" should still be reachable at any point today.
                                    val deadlineInstant = deadlineDate?.let {
                                        LocalDateTime(it, LocalTime(23, 59, 59)).toInstant(timeZone)
                                    }
                                    val draft = TaskDraft(
                                        title = trimmedTitle,
                                        details = details.trim().ifBlank { null },
                                        time = selectedTime,
                                        labels = chosenLabels,
                                        deadline = deadlineInstant,
                                    )
                                    val rule = when (selectedRecurrence) {
                                        RecurrenceOption.None -> null
                                        RecurrenceOption.Daily -> RecurrenceRule.Daily
                                        RecurrenceOption.WeeklyDays -> RecurrenceRule.WeeklyDays(selectedWeekDays.toSet())
                                        RecurrenceOption.Monthly -> RecurrenceRule.Monthly(monthDay)
                                    }
                                    isSaving = true
                                    val onResult: (SaveResult) -> Unit = { result ->
                                        isSaving = false
                                        if (result.success) {
                                            onDismiss()
                                        } else {
                                            errorText = result.errorMessage ?: "No se pudo guardar"
                                        }
                                    }
                                    if (rule != null) {
                                        onSaveRecurring(selectedDate, draft, rule, onResult)
                                    } else {
                                        onSave(selectedDate, draft, onResult)
                                    }
                                },
                            )
                        }

                    Column(verticalArrangement = Arrangement.spacedBy(layout.height(6.dp, 5.dp))) {
                        Text(
                            text = "Título",
                            style = MaterialTheme.typography.labelMedium,
                            color = GlassTheme.tokens.textSecondary,
                        )
                        GlassTextField(
                            value = title,
                            onValueChange = {
                                title = it
                                if (errorText != null) errorText = null
                            },
                            placeholder = "Título de la tarea",
                            label = "Título de la tarea",
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.titleLarge.copy(
                                fontSize = layout.text(18.sp, 16.sp),
                            ),
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(layout.height(6.dp, 5.dp))) {
                        Text(
                            text = "Notas (opcional)",
                            style = MaterialTheme.typography.labelMedium,
                            color = GlassTheme.tokens.textSecondary,
                        )
                        GlassTextField(
                            value = details,
                            onValueChange = { details = it },
                            placeholder = "Añade contexto",
                            label = "Notas de la tarea",
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = false,
                            maxLines = 4,
                            textStyle = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(layout.height(8.dp, 6.dp))) {
                        Text(
                            text = "Hora (opcional)",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = layout.text(15.sp, 14.sp),
                            ),
                            color = GlassTheme.tokens.textSecondary,
                        )
                        GlassSurface(
                            shape = RoundedCornerShape(layout.size(18.dp, 16.dp)),
                            tint = GlassTheme.tokens.glassFill,
                            shadowElevation = 0.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(layout.height(56.dp, 50.dp))
                                .clip(RoundedCornerShape(layout.size(18.dp, 16.dp)))
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = "Seleccionar hora",
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { showTimePicker = true },
                                ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = layout.width(14.dp, 12.dp),
                                        vertical = layout.height(10.dp, 8.dp),
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(layout.width(8.dp, 6.dp)),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Schedule,
                                        contentDescription = "Seleccionar hora",
                                        tint = GlassTheme.tokens.textPrimary,
                                        modifier = Modifier.size(layout.size(20.dp, 18.dp)),
                                    )
                                    Text(
                                        text = selectedTime?.let(::formatTime) ?: "Sin hora",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontSize = layout.text(15.sp, 14.sp),
                                        ),
                                        color = if (selectedTime == null) {
                                            GlassTheme.tokens.textSecondary
                                        } else {
                                            GlassTheme.tokens.textPrimary
                                        },
                                    )
                                }
                                Text(
                                    text = "Cambiar",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontSize = layout.text(14.sp, 13.sp),
                                    ),
                                    color = GlassTheme.tokens.textSecondary,
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(layout.height(8.dp, 6.dp))) {
                        Text(
                            text = "Fecha límite (opcional)",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = layout.text(15.sp, 14.sp),
                            ),
                            color = GlassTheme.tokens.textSecondary,
                        )
                        GlassSurface(
                            shape = RoundedCornerShape(layout.size(18.dp, 16.dp)),
                            tint = GlassTheme.tokens.glassFill,
                            shadowElevation = 0.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(layout.height(56.dp, 50.dp))
                                .clip(RoundedCornerShape(layout.size(18.dp, 16.dp)))
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = "Seleccionar fecha límite",
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { showDeadlinePicker = true },
                                ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = layout.width(14.dp, 12.dp),
                                        vertical = layout.height(10.dp, 8.dp),
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(layout.width(8.dp, 6.dp)),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CalendarToday,
                                        contentDescription = "Seleccionar fecha límite",
                                        tint = GlassTheme.tokens.textPrimary,
                                        modifier = Modifier.size(layout.size(20.dp, 18.dp)),
                                    )
                                    Text(
                                        text = deadlineDate?.let(::formatShortDateWithYear) ?: "Sin fecha límite",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontSize = layout.text(15.sp, 14.sp),
                                        ),
                                        color = if (deadlineDate == null) {
                                            GlassTheme.tokens.textSecondary
                                        } else {
                                            GlassTheme.tokens.textPrimary
                                        },
                                    )
                                }
                                Text(
                                    text = "Cambiar",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontSize = layout.text(14.sp, 13.sp),
                                    ),
                                    color = GlassTheme.tokens.textSecondary,
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(layout.height(8.dp, 6.dp))) {
                        Text(
                            text = "Repetir",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = layout.text(15.sp, 14.sp),
                            ),
                            color = GlassTheme.tokens.textSecondary,
                        )
                        // REVIEW: all recurrence modes must be discoverable without a hidden
                        // horizontal scroll. A 2x2 radio grid also gives long labels room to fit.
                        Column(
                            modifier = Modifier.selectableGroup(),
                            verticalArrangement = Arrangement.spacedBy(layout.height(6.dp, 5.dp)),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(layout.width(8.dp, 6.dp)),
                            ) {
                                RecurrenceOptionChip(
                                    text = "Ninguna",
                                    selected = selectedRecurrence == RecurrenceOption.None,
                                    modifier = Modifier.weight(1f),
                                    onClick = { selectedRecurrence = RecurrenceOption.None },
                                )
                                RecurrenceOptionChip(
                                    text = "Diaria",
                                    selected = selectedRecurrence == RecurrenceOption.Daily,
                                    modifier = Modifier.weight(1f),
                                    onClick = { selectedRecurrence = RecurrenceOption.Daily },
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(layout.width(8.dp, 6.dp)),
                            ) {
                                RecurrenceOptionChip(
                                    text = "Días de la semana",
                                    selected = selectedRecurrence == RecurrenceOption.WeeklyDays,
                                    modifier = Modifier.weight(1f),
                                    onClick = { selectedRecurrence = RecurrenceOption.WeeklyDays },
                                )
                                RecurrenceOptionChip(
                                    text = "Mensual",
                                    selected = selectedRecurrence == RecurrenceOption.Monthly,
                                    modifier = Modifier.weight(1f),
                                    onClick = { selectedRecurrence = RecurrenceOption.Monthly },
                                )
                            }
                        }
                        if (selectedRecurrence == RecurrenceOption.WeeklyDays) {
                            Row(
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(layout.width(6.dp, 5.dp)),
                            ) {
                                weekDayOptions().forEach { (day, label) ->
                                    val selected = selectedWeekDays.contains(day)
                                    RecurrenceOptionChip(
                                        text = label,
                                        selected = selected,
                                        role = Role.Checkbox,
                                        onClick = {
                                            if (selected) {
                                                selectedWeekDays.remove(day)
                                            } else {
                                                selectedWeekDays.add(day)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                        if (selectedRecurrence == RecurrenceOption.Monthly) {
                            Text(
                                text = "Día $monthDay de cada mes",
                                style = MaterialTheme.typography.bodyMedium,
                                color = GlassTheme.tokens.textSecondary,
                            )
                            Row(
                                modifier = Modifier
                                    .selectableGroup()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(layout.width(6.dp, 5.dp)),
                            ) {
                                (1..31).forEach { day ->
                                    RecurrenceOptionChip(
                                        text = day.toString(),
                                        selected = monthDay == day,
                                        onClick = { monthDay = day },
                                    )
                                }
                            }
                        }
                    }

                    if (isPastSelected) {
                        Text(
                            text = "No se pueden crear tareas en fechas pasadas.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GlassTheme.tokens.textSecondary,
                        )
                    }

                    if (errorText != null) {
                        Text(
                            text = errorText.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = GlassTheme.tokens.errorContent,
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(layout.height(8.dp, 6.dp))) {
                        Text(
                            text = "Etiquetas",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = layout.text(15.sp, 14.sp),
                            ),
                            color = GlassTheme.tokens.textSecondary,
                        )
                        if (labels.isEmpty()) {
                            Text(
                                text = "Sin etiquetas creadas",
                                style = MaterialTheme.typography.bodyMedium,
                                color = GlassTheme.tokens.textSecondary,
                            )
                        } else {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(layout.width(8.dp, 6.dp)),
                            ) {
                                labels.forEach { label ->
                                    val selected = selectedLabelIds.contains(label.id)
                                    LabelSelectableChip(
                                        label = label,
                                        selected = selected,
                                        onToggle = { isSelected ->
                                            if (isSelected) {
                                                if (!selectedLabelIds.contains(label.id)) {
                                                    selectedLabelIds.add(label.id)
                                                }
                                            } else {
                                                selectedLabelIds.remove(label.id)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(layout.height(8.dp, 6.dp))) {
                        Text(
                            text = "Crear etiqueta",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = layout.text(15.sp, 14.sp),
                            ),
                            color = GlassTheme.tokens.textSecondary,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(layout.width(12.dp, 8.dp)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GlassTextField(
                                value = newLabelName,
                                onValueChange = { newLabelName = it },
                                placeholder = "Nombre",
                                label = "Nombre de la nueva etiqueta",
                                modifier = Modifier.weight(1f),
                            )
                            GlassActionButton(
                                modifier = Modifier
                                    .width(layout.width(112.dp, 98.dp))
                                    .height(layout.height(52.dp, 48.dp)),
                                text = "Agregar",
                                enabled = newLabelName.isNotBlank() && !isCreatingLabel,
                                tint = GlassTheme.tokens.glassFillStrong,
                                textColor = GlassTheme.tokens.textPrimary,
                                onClick = {
                                    val labelName = newLabelName.trim()
                                    if (labelName.isEmpty()) return@GlassActionButton
                                    scope.launch {
                                        isCreatingLabel = true
                                        val created = onCreateLabel(labelName, selectedColor)
                                        isCreatingLabel = false
                                        if (created != null) {
                                            if (!selectedLabelIds.contains(created.id)) {
                                                selectedLabelIds.add(created.id)
                                            }
                                            newLabelName = ""
                                            errorText = null
                                        } else {
                                            errorText = "No se pudo crear la etiqueta"
                                        }
                                    }
                                },
                            )
                        }
                        // REVIEW: keep the complete palette visible. The previous horizontal row
                        // ended with a clipped swatch and hid valid choices without any cue.
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectableGroup(),
                            verticalArrangement = Arrangement.spacedBy(layout.height(4.dp, 4.dp)),
                        ) {
                            colorOptions.chunked(4).forEach { rowColors ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    rowColors.forEach { hex ->
                                        ColorSwatch(
                                            color = colorFromHex(hex),
                                            contentDescription = labelColorName(hex),
                                            selected = hex == selectedColor,
                                            onClick = { selectedColor = hex },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }

            if (showDatePicker) {
                DatePickerOverlay(
                    selectedDate = selectedDate,
                    onSelect = {
                        selectedDate = it
                        monthDay = it.dayOfMonth
                        showDatePicker = false
                    },
                    onDismiss = { showDatePicker = false },
                )
            }

            if (showTimePicker) {
                TimePickerOverlay(
                    initialTime = selectedTime,
                    onConfirm = {
                        selectedTime = it
                        showTimePicker = false
                    },
                    onDismiss = { showTimePicker = false },
                )
            }

            if (showDeadlinePicker) {
                DatePickerOverlay(
                    selectedDate = deadlineDate ?: selectedDate,
                    onSelect = {
                        deadlineDate = it
                        showDeadlinePicker = false
                    },
                    onClear = { deadlineDate = null },
                    onDismiss = { showDeadlinePicker = false },
                )
            }
        }
    }
}

@Composable
private fun DatePickerOverlay(
    selectedDate: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    /** When non-null, renders a "Sin fecha" button that calls this instead of [onSelect]. */
    onClear: (() -> Unit)? = null,
) {
    val layout = AppLayout.metrics
    val timeZone = remember { TimeZone.currentSystemDefault() }
    val today = remember { currentDate(timeZone) }
    var visibleMonth by remember(selectedDate) {
        mutableStateOf(LocalDate(selectedDate.year, selectedDate.monthNumber, 1))
    }

    val firstDay = LocalDate(visibleMonth.year, visibleMonth.monthNumber, 1)
    val monthDays = daysInMonth(visibleMonth.year, visibleMonth.month)
    val startOffset = firstDay.dayOfWeek.ordinal
    val totalCells = ((startOffset + monthDays + 6) / 7) * 7
    val dayCells = (0 until totalCells).map { index ->
        val dayNumber = index - startOffset + 1
        if (dayNumber in 1..monthDays) {
            LocalDate(visibleMonth.year, visibleMonth.monthNumber, dayNumber)
        } else {
            null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(GlassTheme.tokens.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )

        GlassSurface(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = layout.width(22.dp, 18.dp))
                .fillMaxWidth(),
            shape = RoundedCornerShape(layout.size(28.dp, 24.dp)),
            tint = GlassTheme.tokens.modalFill,
        ) {
            Column(
                modifier = Modifier.padding(layout.size(18.dp, 16.dp)),
                verticalArrangement = Arrangement.spacedBy(layout.height(12.dp, 10.dp)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassIconButton(
                        icon = Icons.Rounded.ChevronLeft,
                        contentDescription = "Mes anterior",
                        onClick = { visibleMonth = visibleMonth.plus(-1, DateTimeUnit.MONTH) },
                    )
                    Text(
                        text = "${monthName(visibleMonth.month)} ${visibleMonth.year}",
                        style = MaterialTheme.typography.titleMedium,
                        color = GlassTheme.tokens.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    GlassIconButton(
                        icon = Icons.Rounded.ChevronRight,
                        contentDescription = "Mes siguiente",
                        onClick = { visibleMonth = visibleMonth.plus(1, DateTimeUnit.MONTH) },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(layout.width(6.dp, 4.dp)),
                ) {
                    weekDayLabels().forEach { label ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = layout.height(2.dp, 2.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = GlassTheme.tokens.textSecondary,
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(layout.height(6.dp, 4.dp))) {
                    dayCells.chunked(7).forEach { week ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(layout.width(6.dp, 4.dp)),
                        ) {
                            week.forEach { day ->
                                if (day == null) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f),
                                    )
                                } else {
                                    DatePickerDayCell(
                                        date = day,
                                        selectedDate = selectedDate,
                                        today = today,
                                        enabled = day >= today,
                                        onSelect = onSelect,
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f),
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassActionButton(
                        text = "Hoy",
                        tint = GlassTheme.tokens.glassFill,
                        textColor = GlassTheme.tokens.textPrimary,
                        onClick = {
                            onSelect(today)
                            onDismiss()
                        },
                    )
                    if (onClear != null) {
                        GlassActionButton(
                            text = "Sin fecha",
                            tint = GlassTheme.tokens.glassFill,
                            textColor = GlassTheme.tokens.textPrimary,
                            onClick = {
                                onClear()
                                onDismiss()
                            },
                        )
                    }
                    GlassActionButton(
                        text = "Cerrar",
                        tint = GlassTheme.tokens.glassFillStrong,
                        textColor = GlassTheme.tokens.textPrimary,
                        onClick = onDismiss,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimePickerOverlay(
    initialTime: LocalTime?,
    onConfirm: (LocalTime?) -> Unit,
    onDismiss: () -> Unit,
) {
    val layout = AppLayout.metrics
    val timeZone = remember { TimeZone.currentSystemDefault() }
    val hours = remember { (0..23).map { it.toString().padStart(2, '0') } }
    val minutes = remember { (0..59).map { it.toString().padStart(2, '0') } }
    var hourIndex by remember { mutableStateOf(0) }
    var minuteIndex by remember { mutableStateOf(0) }

    LaunchedEffect(initialTime) {
        val base = initialTime ?: currentTime(timeZone)
        hourIndex = base.hour
        minuteIndex = base.minute
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(GlassTheme.tokens.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )

        GlassSurface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = layout.width(18.dp, 16.dp),
                    end = layout.width(18.dp, 16.dp),
                    top = layout.height(16.dp, 14.dp),
                    bottom = layout.height(16.dp, 14.dp),
                )
                .fillMaxWidth(),
            shape = RoundedCornerShape(layout.size(28.dp, 24.dp)),
            tint = GlassTheme.tokens.modalFill,
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(layout.size(18.dp, 16.dp)),
                verticalArrangement = Arrangement.spacedBy(layout.height(16.dp, 14.dp)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .clickable(
                                role = Role.Button,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onDismiss,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Cancelar",
                            style = MaterialTheme.typography.labelMedium,
                            color = GlassTheme.tokens.textSecondary,
                        )
                    }
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Hora",
                            style = MaterialTheme.typography.titleMedium,
                            color = GlassTheme.tokens.textPrimary,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .clickable(
                                role = Role.Button,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onConfirm(LocalTime(hourIndex, minuteIndex)) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Listo",
                            style = MaterialTheme.typography.labelMedium,
                            color = GlassTheme.tokens.textPrimary,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WheelPicker(
                        entries = hours,
                        selectedIndex = hourIndex,
                        label = "Hora",
                        onIndexSelected = { hourIndex = it },
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = ":",
                        style = MaterialTheme.typography.titleLarge,
                        color = GlassTheme.tokens.textSecondary,
                        modifier = Modifier.padding(horizontal = layout.width(6.dp, 4.dp)),
                    )
                    WheelPicker(
                        entries = minutes,
                        selectedIndex = minuteIndex,
                        label = "Minutos",
                        onIndexSelected = { minuteIndex = it },
                        modifier = Modifier.weight(1f),
                    )
                }

                GlassActionButton(
                    text = "Sin hora",
                    tint = GlassTheme.tokens.glassFillStrong,
                    textColor = GlassTheme.tokens.textPrimary,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally),
                    onClick = { onConfirm(null) },
                )
            }
        }
    }
}

@Composable
private fun WheelPicker(
    entries: List<String>,
    selectedIndex: Int,
    label: String,
    onIndexSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = AppLayout.metrics
    val itemHeight = layout.height(38.dp, 32.dp)
    val visibleCount = 5
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val itemHeightPx = with(LocalDensity.current) { itemHeight.roundToPx() }
    val centerIndex by remember {
        derivedStateOf {
            val index = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            val adjust = if (offset > itemHeightPx / 2) 1 else 0
            (index + adjust).coerceIn(0, entries.lastIndex)
        }
    }

    LaunchedEffect(selectedIndex) {
        listState.scrollToItem(selectedIndex)
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val target = centerIndex
            if (listState.firstVisibleItemIndex != target || listState.firstVisibleItemScrollOffset != 0) {
                listState.animateScrollToItem(target)
            }
            if (target != selectedIndex) {
                onIndexSelected(target)
            }
        }
    }

    Box(
        modifier = modifier
            .height(itemHeight * visibleCount)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                stateDescription = entries[selectedIndex]
            },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = itemHeight * (visibleCount / 2)),
        ) {
            items(entries.size) { index ->
                val distance = abs(index - centerIndex)
                val alpha = when (distance) {
                    0 -> 1f
                    1 -> 0.6f
                    else -> 0.35f
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .alpha(alpha),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = entries[index],
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = if (distance == 0) {
                                layout.text(20.sp, 18.sp)
                            } else {
                                layout.text(16.sp, 14.sp)
                            },
                        ),
                        color = if (distance == 0) {
                            GlassTheme.tokens.textPrimary
                        } else {
                            GlassTheme.tokens.textSecondary
                        },
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(itemHeight)
                .clip(RoundedCornerShape(layout.size(14.dp, 12.dp)))
                .background(GlassTheme.tokens.glassFillStrong.copy(alpha = 0.35f))
                .border(
                    width = layout.size(1.dp, 1.dp),
                    color = GlassTheme.tokens.glassStroke,
                    shape = RoundedCornerShape(layout.size(14.dp, 12.dp)),
                ),
        )
    }
}

@Composable
private fun DatePickerDayCell(
    date: LocalDate,
    selectedDate: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val layout = AppLayout.metrics
    val isSelected = date == selectedDate
    val isToday = date == today
    val tint = when {
        !enabled -> GlassTheme.tokens.glassFill.copy(alpha = 0.35f)
        isSelected -> GlassTheme.tokens.glassFillStrong
        else -> GlassTheme.tokens.glassFill
    }
    val stroke = when {
        !enabled -> GlassTheme.tokens.glassStroke.copy(alpha = 0.3f)
        isSelected -> GlassTheme.tokens.glassHighlight
        isToday -> GlassTheme.tokens.glassHighlight.copy(alpha = 0.6f)
        else -> GlassTheme.tokens.glassStroke
    }
    val textColor = when {
        !enabled -> GlassTheme.tokens.textSecondary.copy(alpha = 0.5f)
        isSelected -> GlassTheme.tokens.textPrimary
        else -> GlassTheme.tokens.textSecondary
    }

    GlassSurface(
        shape = RoundedCornerShape(layout.size(8.dp, 8.dp)),
        tint = tint,
        strokeColor = stroke,
        shadowElevation = 0.dp,
        modifier = modifier
            .semantics(mergeDescendants = true) { contentDescription = formatFullDate(date) }
            .selectable(
                selected = isSelected,
                enabled = enabled,
                role = Role.RadioButton,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onSelect(date) },
            ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
            )
        }
    }
}

@Composable
internal fun CalendarMonthView(
    selectedDate: LocalDate,
    visibleMonth: LocalDate,
    tasksByDate: Map<LocalDate, List<TaskItem>>,
    onSelectDate: (LocalDate) -> Unit,
    onVisibleMonthChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = AppLayout.metrics
    val timeZone = remember { TimeZone.currentSystemDefault() }
    val today = remember { currentDate(timeZone) }
    val swipeThreshold = layout.width(72.dp, 56.dp)
    val swipeEdgeGuard = layout.width(24.dp, 18.dp)
    var dragTotal by remember { mutableStateOf(0f) }
    var allowSwipe by remember { mutableStateOf(false) }

    val firstDay = LocalDate(visibleMonth.year, visibleMonth.monthNumber, 1)
    val monthDays = daysInMonth(visibleMonth.year, visibleMonth.month)
    val startOffset = firstDay.dayOfWeek.ordinal
    val totalCells = ((startOffset + monthDays + 6) / 7) * 7
    val dayCells = (0 until totalCells).map { index ->
        val dayNumber = index - startOffset + 1
        if (dayNumber in 1..monthDays) {
            LocalDate(visibleMonth.year, visibleMonth.monthNumber, dayNumber)
        } else {
            null
        }
    }

    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(layout.size(28.dp, 24.dp)),
        tint = GlassTheme.tokens.glassFillStrong,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = layout.height(16.dp, 14.dp)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = layout.width(18.dp, 16.dp)),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(layout.height(2.dp, 2.dp)),
                ) {
                    Text(
                        text = monthName(visibleMonth.month),
                        style = MaterialTheme.typography.titleLarge,
                        color = GlassTheme.tokens.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = visibleMonth.year.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTheme.tokens.textSecondary,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(layout.width(4.dp, 4.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .clickable(
                                role = Role.Button,
                                onClickLabel = "Ir a hoy",
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    onVisibleMonthChange(LocalDate(today.year, today.monthNumber, 1))
                                    onSelectDate(today)
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Hoy",
                            style = MaterialTheme.typography.labelMedium,
                            color = GlassTheme.tokens.textSecondary,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable(
                                role = Role.Button,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onVisibleMonthChange(visibleMonth.plus(-1, DateTimeUnit.MONTH)) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronLeft,
                            contentDescription = "Mes anterior",
                            tint = GlassTheme.tokens.textSecondary,
                            modifier = Modifier.size(layout.size(22.dp, 20.dp)),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable(
                                role = Role.Button,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onVisibleMonthChange(visibleMonth.plus(1, DateTimeUnit.MONTH)) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = "Mes siguiente",
                            tint = GlassTheme.tokens.textSecondary,
                            modifier = Modifier.size(layout.size(22.dp, 20.dp)),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(layout.height(12.dp, 10.dp)))

            val daySpacing = layout.width(4.dp, 3.dp)
            val gridInset = layout.width(12.dp, 10.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = gridInset),
                horizontalArrangement = Arrangement.spacedBy(daySpacing),
            ) {
                weekDayLabels().forEach { label ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = layout.text(11.sp, 10.sp),
                            ),
                            color = GlassTheme.tokens.textSecondary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(layout.height(6.dp, 4.dp)))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(visibleMonth) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                val edge = swipeEdgeGuard.toPx()
                                allowSwipe = offset.x in edge..(size.width - edge)
                                dragTotal = 0f
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                if (allowSwipe) {
                                    dragTotal += dragAmount
                                }
                            },
                            onDragEnd = {
                                if (allowSwipe) {
                                    when {
                                        dragTotal > swipeThreshold.toPx() -> {
                                            onVisibleMonthChange(visibleMonth.plus(-1, DateTimeUnit.MONTH))
                                        }

                                        dragTotal < -swipeThreshold.toPx() -> {
                                            onVisibleMonthChange(visibleMonth.plus(1, DateTimeUnit.MONTH))
                                        }
                                    }
                                }
                                dragTotal = 0f
                                allowSwipe = false
                            },
                            onDragCancel = {
                                dragTotal = 0f
                                allowSwipe = false
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    val weeks = dayCells.chunked(7)
                    val availableWidth = maxWidth - gridInset * 2
                    val maxCellWidth = (availableWidth - daySpacing * 6) / 7

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = gridInset),
                        verticalArrangement = Arrangement.spacedBy(daySpacing),
                    ) {
                        weeks.forEach { week ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(maxCellWidth),
                                horizontalArrangement = Arrangement.spacedBy(daySpacing),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                week.forEach { day ->
                                    if (day == null) {
                                        Box(modifier = Modifier.size(maxCellWidth))
                                    } else {
                                        CalendarDayCell(
                                            date = day,
                                            selectedDate = selectedDate,
                                            today = today,
                                            noteCount = tasksByDate[day]?.size ?: 0,
                                            onSelect = onSelectDate,
                                            modifier = Modifier.size(maxCellWidth),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate,
    selectedDate: LocalDate,
    today: LocalDate,
    noteCount: Int,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = AppLayout.metrics
    val isSelected = date == selectedDate
    val isToday = date == today
    val isPast = date < today
    val tint = when {
        noteCount <= 0 -> GlassTheme.tokens.glassFill
        noteCount <= 3 -> GlassTheme.tokens.success.copy(alpha = 0.24f)
        noteCount <= 7 -> GlassTheme.tokens.accent.copy(alpha = 0.24f)
        else -> GlassTheme.tokens.error.copy(alpha = 0.24f)
    }
    val stroke = when {
        isSelected -> GlassTheme.tokens.glassHighlight
        isToday -> GlassTheme.tokens.glassHighlight.copy(alpha = 0.6f)
        else -> GlassTheme.tokens.glassStroke
    }
    val dayColor = if (noteCount == 0 && !isToday && !isSelected) {
        GlassTheme.tokens.textSecondary
    } else {
        GlassTheme.tokens.textPrimary
    }
    val contentAlpha = if (isPast && !isSelected) 0.78f else 1f
    val dayDescription = buildString {
        append(formatFullDate(date))
        if (isToday) append(", hoy")
        append(
            when (noteCount) {
                0 -> ", sin tareas"
                1 -> ", una tarea"
                else -> ", $noteCount tareas"
            },
        )
    }

    GlassSurface(
        shape = RoundedCornerShape(layout.size(12.dp, 10.dp)),
        tint = tint,
        strokeColor = stroke,
        shadowElevation = 0.dp,
        modifier = modifier
            .alpha(contentAlpha)
            .semantics(mergeDescendants = true) {
                contentDescription = dayDescription
                selected = isSelected
                if (isPast) stateDescription = "Fecha pasada"
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = { onSelect(date) },
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = layout.height(4.dp, 3.dp)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = layout.text(15.sp, 13.sp),
                    lineHeight = layout.text(18.sp, 16.sp),
                ),
                color = dayColor,
                textDecoration = if (isPast) TextDecoration.LineThrough else TextDecoration.None,
            )
            if (noteCount > 0) {
                Spacer(modifier = Modifier.height(layout.height(2.dp, 2.dp)))
                Text(
                    text = noteCount.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = layout.text(10.sp, 10.sp),
                        lineHeight = layout.text(12.sp, 11.sp),
                    ),
                    color = GlassTheme.tokens.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun LabelSelectableChip(
    label: LabelTag,
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val layout = AppLayout.metrics
    val color = colorFromHex(label.colorHex)
    val tint = if (selected) color.copy(alpha = 0.18f) else GlassTheme.tokens.glassFill
    val stroke = if (selected) color.copy(alpha = 0.6f) else GlassTheme.tokens.glassStroke

    GlassSurface(
        shape = RoundedCornerShape(layout.size(16.dp, 14.dp)),
        tint = tint,
        strokeColor = stroke,
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .selectable(
                selected = selected,
                role = Role.Checkbox,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onToggle(!selected) },
            ),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = layout.width(12.dp, 10.dp),
                vertical = layout.height(8.dp, 7.dp),
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
                color = if (selected) GlassTheme.tokens.textPrimary else GlassTheme.tokens.textSecondary,
            )
        }
    }
}

@Composable
internal fun TaskDetailsOverlay(
    task: TaskItem,
    onDismiss: () -> Unit,
    onToggleDone: (Boolean) -> Unit,
    onRequestDelete: () -> Unit,
) {
    val layout = AppLayout.metrics
    val timeZone = remember { TimeZone.currentSystemDefault() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeContentPadding()
                .background(GlassTheme.tokens.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            GlassSurface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = layout.width(18.dp, 16.dp),
                        end = layout.width(18.dp, 16.dp),
                        top = layout.height(16.dp, 14.dp),
                        bottom = layout.height(16.dp, 14.dp),
                    )
                    .fillMaxWidth(),
                shape = RoundedCornerShape(layout.size(28.dp, 24.dp)),
                tint = GlassTheme.tokens.modalFill,
                shadowElevation = 12.dp,
            ) {
                Column(
                    modifier = Modifier
                        .padding(layout.size(18.dp, 16.dp))
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(layout.height(12.dp, 10.dp)),
                ) {
                    Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = layout.text(20.sp, 18.sp),
                    ),
                    color = GlassTheme.tokens.textPrimary,
                    )

                if (!task.details.isNullOrBlank()) {
                    Text(
                        text = task.details,
                        style = MaterialTheme.typography.bodyLarge,
                        color = GlassTheme.tokens.textSecondary,
                    )
                }

                val taskTime = task.time
                if (taskTime != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(layout.width(8.dp, 6.dp)),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = "Hora",
                            tint = GlassTheme.tokens.textSecondary,
                            modifier = Modifier.size(layout.size(18.dp, 16.dp)),
                        )
                        val timeText = task.endTime?.let { endTime ->
                            "${formatTime(taskTime)}-${formatTime(endTime)}"
                        } ?: formatTime(taskTime)
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = GlassTheme.tokens.textSecondary,
                        )
                    }
                }

                val taskDeadline = task.deadline
                if (taskDeadline != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(layout.width(8.dp, 6.dp)),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarToday,
                            contentDescription = "Fecha límite",
                            tint = GlassTheme.tokens.error,
                            modifier = Modifier.size(layout.size(18.dp, 16.dp)),
                        )
                        Text(
                            text = "Límite: " + formatFullDate(taskDeadline.toLocalDateTime(timeZone).date),
                            style = MaterialTheme.typography.bodyMedium,
                            color = GlassTheme.tokens.errorContent,
                        )
                    }
                }

                if (task.labels.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(layout.height(6.dp, 4.dp))) {
                        Text(
                            text = "Etiquetas",
                            style = MaterialTheme.typography.labelMedium,
                            color = GlassTheme.tokens.textSecondary,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(layout.width(6.dp, 4.dp)),
                        ) {
                            task.labels.forEach { label ->
                                GlassSurface(
                                    modifier = Modifier.padding(vertical = layout.height(2.dp, 2.dp)),
                                    shape = RoundedCornerShape(layout.size(14.dp, 12.dp)),
                                    tint = colorFromHex(label.colorHex).copy(alpha = 0.18f),
                                    strokeColor = colorFromHex(label.colorHex).copy(alpha = 0.42f),
                                ) {
                                    Text(
                                        text = label.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = GlassTheme.tokens.textPrimary,
                                        modifier = Modifier.padding(
                                            horizontal = layout.width(10.dp, 8.dp),
                                            vertical = layout.height(6.dp, 6.dp),
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(layout.height(12.dp, 10.dp)))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(layout.width(10.dp, 8.dp)),
                ) {
                    GlassActionButton(
                        text = if (task.isDone) "Marcar como pendiente" else "Marcar como hecho",
                        modifier = Modifier.weight(1f),
                        tint = GlassTheme.tokens.glassFill,
                        textColor = GlassTheme.tokens.textPrimary,
                        onClick = { onToggleDone(!task.isDone) },
                    )
                    GlassActionButton(
                        text = "Eliminar",
                        modifier = Modifier.weight(1f),
                        tint = GlassTheme.tokens.error.copy(alpha = 0.18f),
                        textColor = GlassTheme.tokens.errorContent,
                        onClick = onRequestDelete,
                    )
                }

                    GlassActionButton(
                        text = "Cerrar",
                        modifier = Modifier.fillMaxWidth(),
                        tint = GlassTheme.tokens.glassFillStrong,
                        textColor = GlassTheme.tokens.textPrimary,
                        onClick = onDismiss,
                    )
                }
            }
        }
    }
}

private fun currentDate(timeZone: TimeZone): LocalDate {
    return Instant
        .fromEpochMilliseconds(currentTimeMillis())
        .toLocalDateTime(timeZone)
        .date
}

private fun currentTime(timeZone: TimeZone): LocalTime {
    return Instant
        .fromEpochMilliseconds(currentTimeMillis())
        .toLocalDateTime(timeZone)
        .time
}

private fun formatShortDateWithYear(date: LocalDate): String {
    return "${date.dayOfMonth} ${monthName(date.month, short = true)} ${date.year}"
}

private fun weekDayLabels(): List<String> {
    return listOf("L", "M", "X", "J", "V", "S", "D")
}

@Composable
private fun RecurrenceOptionChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    role: Role = Role.RadioButton,
    onClick: () -> Unit,
) {
    val layout = AppLayout.metrics
    GlassSurface(
        shape = RoundedCornerShape(layout.size(14.dp, 12.dp)),
        tint = if (selected) GlassTheme.tokens.accentOnLight else GlassTheme.tokens.glassFill,
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(layout.size(14.dp, 12.dp)))
            .selectable(
                selected = selected,
                role = role,
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = layout.width(12.dp, 10.dp),
                    vertical = layout.height(8.dp, 7.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) GlassTheme.tokens.onError else GlassTheme.tokens.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun weekDayOptions(): List<Pair<DayOfWeek, String>> {
    return listOf(
        DayOfWeek.MONDAY to "L",
        DayOfWeek.TUESDAY to "M",
        DayOfWeek.WEDNESDAY to "X",
        DayOfWeek.THURSDAY to "J",
        DayOfWeek.FRIDAY to "V",
        DayOfWeek.SATURDAY to "S",
        DayOfWeek.SUNDAY to "D",
    )
}
