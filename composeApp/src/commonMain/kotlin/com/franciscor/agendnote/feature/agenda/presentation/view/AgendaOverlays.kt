package com.franciscor.agendnote.feature.agenda.presentation.view

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.franciscor.agendnote.core.model.LabelTag
import com.franciscor.agendnote.core.model.TaskDraft
import com.franciscor.agendnote.core.model.TaskItem
import com.franciscor.agendnote.core.ui.components.GlassActionButton
import com.franciscor.agendnote.core.ui.components.GlassConfirmDialog
import com.franciscor.agendnote.core.ui.components.GlassIconButton
import com.franciscor.agendnote.core.ui.components.GlassSurface
import com.franciscor.agendnote.core.ui.components.GlassTextField
import com.franciscor.agendnote.core.ui.components.colorFromHex
import com.franciscor.agendnote.core.ui.components.labelColorPalette
import com.franciscor.agendnote.core.ui.layout.AppLayout
import com.franciscor.agendnote.core.ui.theme.GlassTheme
import com.franciscor.agendnote.feature.agenda.presentation.model.SaveResult
import kotlin.math.abs
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

@Composable
internal fun ConfirmDeleteDialog(
    task: TaskItem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassConfirmDialog(
        visible = true,
        title = "Eliminar tarea?",
        message = "Se borrara \"${task.title}\"",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        confirmText = "Eliminar",
    )
}

@Composable
internal fun NewTaskSheet(
    date: LocalDate,
    labels: List<LabelTag>,
    onCreateLabel: suspend (String, String) -> LabelTag?,
    onDismiss: () -> Unit,
    onSave: suspend (LocalDate, TaskDraft) -> SaveResult,
) {
    val layout = AppLayout.metrics
    val timeZone = remember { TimeZone.currentSystemDefault() }
    val today = remember { kotlin.time.Clock.System.todayIn(timeZone) }
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
    val selectedLabelIds = remember { mutableStateListOf<String>() }
    var newLabelName by remember { mutableStateOf("") }
    var isCreatingLabel by remember { mutableStateOf(false) }
    var selectedColor by remember { mutableStateOf(colorOptions.first()) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(date, today, colorOptions) {
        title = ""
        details = ""
        selectedTime = null
        errorText = null
        selectedDate = if (date < today) today else date
        showDatePicker = false
        showTimePicker = false
        selectedLabelIds.clear()
        newLabelName = ""
        isCreatingLabel = false
        selectedColor = colorOptions.first()
        isSaving = false
    }

    LaunchedEffect(colorOptions) {
        if (!colorOptions.contains(selectedColor)) {
            selectedColor = colorOptions.first()
        }
    }

    val isPastSelected = selectedDate < today
    val sheetBlur = if (showTimePicker) layout.size(14.dp, 10.dp) else 0.dp

    Box(modifier = Modifier.fillMaxSize()) {
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
                        bottom = layout.height(108.dp, 96.dp),
                    )
                    .fillMaxWidth(),
                shape = RoundedCornerShape(layout.size(32.dp, 28.dp)),
                tint = GlassTheme.tokens.glassFillStrong,
            ) {
                Column(
                    modifier = Modifier
                        .padding(layout.size(16.dp, 14.dp))
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(layout.height(12.dp, 10.dp)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(layout.width(12.dp, 10.dp)),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
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
                                    .height(layout.height(44.dp, 40.dp))
                                    .clip(RoundedCornerShape(layout.size(16.dp, 14.dp)))
                                    .clickable(
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
                            horizontalArrangement = Arrangement.spacedBy(layout.width(8.dp, 6.dp)),
                            verticalAlignment = Alignment.Top,
                        ) {
                            GlassActionButton(
                                modifier = Modifier
                                    .width(layout.width(92.dp, 84.dp))
                                    .height(layout.height(50.dp, 44.dp)),
                                text = "Cancelar",
                                tint = GlassTheme.tokens.glassFillStrong,
                                textColor = GlassTheme.tokens.textPrimary,
                                onClick = onDismiss,
                            )
                            GlassActionButton(
                                modifier = Modifier
                                    .width(layout.width(104.dp, 94.dp))
                                    .height(layout.height(50.dp, 44.dp)),
                                text = if (isSaving) "Guardando..." else "Guardar",
                                enabled = title.isNotBlank() && !isSaving && !isPastSelected,
                                onClick = {
                                    val trimmedTitle = title.trim()
                                    if (trimmedTitle.isEmpty()) {
                                        errorText = "Titulo requerido"
                                        return@GlassActionButton
                                    }
                                    if (selectedDate < today) {
                                        errorText = "No se pueden crear tareas en fechas pasadas"
                                        return@GlassActionButton
                                    }

                                    val chosenLabels = labels.filter { selectedLabelIds.contains(it.id) }
                                    val draft = TaskDraft(
                                        title = trimmedTitle,
                                        details = details.trim().ifBlank { null },
                                        time = selectedTime,
                                        labels = chosenLabels,
                                    )
                                    scope.launch {
                                        isSaving = true
                                        val result = onSave(selectedDate, draft)
                                        isSaving = false
                                        if (result.success) {
                                            onDismiss()
                                        } else {
                                            errorText = result.errorMessage ?: "No se pudo guardar"
                                        }
                                    }
                                },
                            )
                        }
                    }

                    GlassTextField(
                        value = title,
                        onValueChange = {
                            title = it
                            if (errorText != null) errorText = null
                        },
                        placeholder = "Titulo de la tarea",
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.titleLarge.copy(
                            fontSize = layout.text(18.sp, 16.sp),
                        ),
                    )

                    GlassTextField(
                        value = details,
                        onValueChange = { details = it },
                        placeholder = "Notas (opcional)",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 4,
                        textStyle = MaterialTheme.typography.bodyMedium,
                    )

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
                            color = GlassTheme.tokens.error,
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
                                modifier = Modifier.weight(1f),
                            )
                            GlassActionButton(
                                modifier = Modifier
                                    .width(layout.width(112.dp, 98.dp))
                                    .height(layout.height(52.dp, 46.dp)),
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
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(layout.width(10.dp, 8.dp)),
                        ) {
                            colorOptions.forEach { hex ->
                                ColorSwatch(
                                    color = colorFromHex(hex),
                                    selected = hex == selectedColor,
                                    onClick = { selectedColor = hex },
                                )
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
    }
}

@Composable
private fun DatePickerOverlay(
    selectedDate: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val layout = AppLayout.metrics
    val timeZone = remember { TimeZone.currentSystemDefault() }
    val today = remember { kotlin.time.Clock.System.todayIn(timeZone) }
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
            tint = GlassTheme.tokens.glassFillStrong,
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
        val base = initialTime ?: kotlin.time.Clock.System.now().toLocalDateTime(timeZone).time
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
                    bottom = layout.height(120.dp, 102.dp),
                )
                .fillMaxWidth(),
            shape = RoundedCornerShape(layout.size(28.dp, 24.dp)),
            tint = GlassTheme.tokens.glassFillStrong,
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
                            .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                            .clickable(
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
                            .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                            .clickable(
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
                        onIndexSelected = { minuteIndex = it },
                        modifier = Modifier.weight(1f),
                    )
                }

                Text(
                    text = "Sin hora",
                    style = MaterialTheme.typography.labelMedium,
                    color = GlassTheme.tokens.textSecondary,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onConfirm(null) },
                        ),
                )
            }
        }
    }
}

@Composable
private fun WheelPicker(
    entries: List<String>,
    selectedIndex: Int,
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

    Box(modifier = modifier.height(itemHeight * visibleCount)) {
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
        modifier = modifier.clickable(
            enabled = enabled,
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
internal fun CalendarOverlay(
    selectedDate: LocalDate,
    tasksByDate: Map<LocalDate, List<TaskItem>>,
    onSelectDate: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val layout = AppLayout.metrics
    val timeZone = remember { TimeZone.currentSystemDefault() }
    val today = remember { kotlin.time.Clock.System.todayIn(timeZone) }
    var visibleMonth by remember(selectedDate) {
        mutableStateOf(LocalDate(selectedDate.year, selectedDate.monthNumber, 1))
    }
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeContentPadding(),
        ) {
            GlassSurface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(
                        horizontal = layout.width(18.dp, 16.dp),
                        vertical = layout.height(24.dp, 18.dp),
                    )
                    .fillMaxWidth(),
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
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onSelectDate(today) },
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
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Calendario",
                                style = MaterialTheme.typography.titleMedium,
                                color = GlassTheme.tokens.textPrimary,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onDismiss,
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

                    Spacer(modifier = Modifier.height(layout.height(14.dp, 12.dp)))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = layout.width(18.dp, 16.dp)),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(layout.height(2.dp, 2.dp))) {
                            Text(
                                text = monthName(visibleMonth.month),
                                style = MaterialTheme.typography.titleLarge,
                                color = GlassTheme.tokens.textPrimary,
                            )
                            Text(
                                text = visibleMonth.year.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = GlassTheme.tokens.textSecondary,
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(layout.width(12.dp, 10.dp)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ChevronLeft,
                                contentDescription = "Mes anterior",
                                tint = GlassTheme.tokens.textSecondary,
                                modifier = Modifier
                                    .size(layout.size(22.dp, 20.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { visibleMonth = visibleMonth.plus(-1, DateTimeUnit.MONTH) },
                                    ),
                            )
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = "Mes siguiente",
                                tint = GlassTheme.tokens.textSecondary,
                                modifier = Modifier
                                    .size(layout.size(22.dp, 20.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { visibleMonth = visibleMonth.plus(1, DateTimeUnit.MONTH) },
                                    ),
                            )
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
                                                    visibleMonth = visibleMonth.plus(-1, DateTimeUnit.MONTH)
                                                }

                                                dragTotal < -swipeThreshold.toPx() -> {
                                                    visibleMonth = visibleMonth.plus(1, DateTimeUnit.MONTH)
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
        noteCount <= 3 -> Color(0xFF34C759).copy(alpha = 0.26f)
        noteCount <= 7 -> Color(0xFFFFCC00).copy(alpha = 0.26f)
        else -> Color(0xFFFF3B30).copy(alpha = 0.26f)
    }
    val stroke = when {
        isSelected -> GlassTheme.tokens.glassHighlight
        isToday -> GlassTheme.tokens.glassHighlight.copy(alpha = 0.6f)
        else -> GlassTheme.tokens.glassStroke
    }
    val dayColor = if (noteCount == 0) GlassTheme.tokens.textSecondary else GlassTheme.tokens.textPrimary
    val countColor = if (noteCount == 0) {
        GlassTheme.tokens.textSecondary.copy(alpha = 0.7f)
    } else {
        GlassTheme.tokens.textPrimary
    }
    val pastStrokeColor = GlassTheme.tokens.glassHighlight.copy(alpha = 0.45f)

    GlassSurface(
        shape = RoundedCornerShape(layout.size(12.dp, 10.dp)),
        tint = tint,
        strokeColor = stroke,
        shadowElevation = 0.dp,
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { onSelect(date) },
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isPast) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(layout.size(6.dp, 4.dp)),
                ) {
                    val strokeWidth = size.minDimension * 0.08f
                    drawLine(
                        color = pastStrokeColor,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height),
                        strokeWidth = strokeWidth,
                    )
                    drawLine(
                        color = pastStrokeColor,
                        start = Offset(size.width, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = strokeWidth,
                    )
                }
            }

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
                )
                Spacer(modifier = Modifier.height(layout.height(2.dp, 2.dp)))
                Text(
                    text = noteCount.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = layout.text(10.sp, 10.sp),
                        lineHeight = layout.text(12.sp, 11.sp),
                    ),
                    color = countColor,
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
        modifier = Modifier.clickable(
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
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val layout = AppLayout.metrics
    Box(
        modifier = Modifier
            .size(layout.size(30.dp, 28.dp))
            .clip(CircleShape)
            .background(color)
            .border(
                width = layout.size(2.dp, 1.dp),
                color = if (selected) GlassTheme.tokens.glassHighlight else Color.Transparent,
                shape = CircleShape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    )
}

private fun formatShortDateWithYear(date: LocalDate): String {
    return "${date.dayOfMonth} ${monthName(date.month, short = true)} ${date.year}"
}

private fun weekDayLabels(): List<String> {
    return listOf("L", "M", "X", "J", "V", "S", "D")
}
