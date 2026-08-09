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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.EventAvailable
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
import com.franciscor.agendnote.core.model.Subtask
import com.franciscor.agendnote.core.model.TaskDraft
import com.franciscor.agendnote.core.model.TaskItem
import com.franciscor.agendnote.core.model.TaskTemplate
import com.franciscor.agendnote.core.nlp.QuickCaptureResult
import com.franciscor.agendnote.core.nlp.parseQuickCapture
import com.franciscor.agendnote.core.platform.currentTimeMillis
import com.franciscor.agendnote.core.ui.components.ColorSwatch
import com.franciscor.agendnote.core.ui.components.GlassActionButton
import com.franciscor.agendnote.core.ui.components.GlassConfirmDialog
import com.franciscor.agendnote.core.ui.components.GlassEmptyState
import com.franciscor.agendnote.core.ui.components.GlassIconButton
import com.franciscor.agendnote.core.ui.components.GlassSurface
import com.franciscor.agendnote.core.ui.components.GlassTextField
import com.franciscor.agendnote.core.ui.components.colorFromHex
import com.franciscor.agendnote.core.ui.components.labelColorPalette
import com.franciscor.agendnote.core.ui.components.labelColorName
import com.franciscor.agendnote.core.ui.layout.AppLayout
import com.franciscor.agendnote.core.ui.theme.GlassTheme
import com.franciscor.agendnote.feature.agenda.domain.RecurrenceEnd
import com.franciscor.agendnote.feature.agenda.domain.RecurrenceRule
import com.franciscor.agendnote.feature.agenda.domain.SmartList
import com.franciscor.agendnote.feature.agenda.domain.smartListTasks
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
    // Borrar una tarea materializada de una serie ya actúa como "saltar esta ocurrencia": el
    // generador de series nunca vuelve a intentar una fecha que su cursor ya dejó atrás (ver
    // SeriesMaterializer/planMaterialization). Deja explícito que las demás apariciones no se
    // tocan, en vez de que el usuario tenga que asumirlo.
    val message = if (task.seriesId != null) {
        "Se borrará \"${task.title}\". Es parte de una tarea recurrente: las demás apariciones de la serie no se ven afectadas."
    } else {
        "Se borrará \"${task.title}\""
    }
    GlassConfirmDialog(
        visible = true,
        title = "¿Eliminar tarea?",
        message = message,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        confirmText = "Eliminar",
    )
}

private val SMART_LIST_LABELS: List<Pair<SmartList, String>> = listOf(
    SmartList.Overdue to "Atrasadas",
    SmartList.Next7Days to "Próximos 7 días",
    SmartList.WithoutTime to "Sin hora",
    SmartList.WithReminder to "Con recordatorio",
    SmartList.Recurring to "Recurrentes",
)

/**
 * Vista de solo lectura sobre lo que ya está cargado en `tasksByDate` (ver [smartListTasks] -
 * no es una consulta al backend, así que una tarea en un mes nunca visitado no aparece aquí
 * todavía).
 */
@Composable
internal fun SmartListsOverlay(
    tasksByDate: Map<LocalDate, List<TaskItem>>,
    today: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val layout = AppLayout.metrics
    var selectedList by remember { mutableStateOf(SmartList.Overdue) }
    val results = remember(selectedList, tasksByDate, today) {
        smartListTasks(selectedList, tasksByDate, today)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize().safeContentPadding()) {
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
                    .padding(horizontal = layout.width(18.dp, 16.dp))
                    .fillMaxWidth()
                    .heightIn(max = layout.height(560.dp, 520.dp)),
                shape = RoundedCornerShape(layout.size(28.dp, 24.dp)),
                tint = GlassTheme.tokens.modalFill,
            ) {
                Column(
                    modifier = Modifier
                        .padding(layout.size(18.dp, 16.dp))
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(layout.height(12.dp, 10.dp)),
                ) {
                    Text(
                        text = "Listas inteligentes",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = layout.text(22.sp, 20.sp),
                        ),
                        color = GlassTheme.tokens.textPrimary,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectableGroup()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(layout.width(6.dp, 5.dp)),
                    ) {
                        SMART_LIST_LABELS.forEach { (list, label) ->
                            RecurrenceOptionChip(
                                text = label,
                                selected = selectedList == list,
                                onClick = { selectedList = list },
                            )
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        if (results.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                GlassEmptyState(
                                    icon = Icons.Rounded.EventAvailable,
                                    title = "Nada por aquí",
                                    subtitle = "No hay tareas en esta vista por ahora.",
                                )
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(layout.height(8.dp, 6.dp)),
                            ) {
                                items(results, key = { (date, task) -> "$date:${task.id}" }) { (date, task) ->
                                    SmartListRow(
                                        date = date,
                                        task = task,
                                        onClick = {
                                            onSelectDate(date)
                                            onDismiss()
                                        },
                                    )
                                }
                            }
                        }
                    }
                    GlassActionButton(
                        text = "Cerrar",
                        tint = GlassTheme.tokens.glassFillStrong,
                        textColor = GlassTheme.tokens.textPrimary,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .height(layout.height(48.dp, 46.dp)),
                        onClick = onDismiss,
                    )
                }
            }
        }
    }
}

@Composable
private fun SmartListRow(
    date: LocalDate,
    task: TaskItem,
    onClick: () -> Unit,
) {
    val layout = AppLayout.metrics
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = layout.height(56.dp, 52.dp))
            .clickable(
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(layout.size(16.dp, 14.dp)),
        tint = GlassTheme.tokens.glassFill,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = layout.width(14.dp, 12.dp), vertical = layout.height(10.dp, 8.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(layout.width(10.dp, 8.dp)),
        ) {
            Text(
                text = formatShortDateWithYear(date),
                style = MaterialTheme.typography.labelMedium,
                color = GlassTheme.tokens.textSecondary,
                maxLines = 1,
            )
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium,
                color = GlassTheme.tokens.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            task.time?.let {
                Text(
                    text = formatTime(it),
                    style = MaterialTheme.typography.labelMedium,
                    color = GlassTheme.tokens.textSecondary,
                )
            }
        }
    }
}

private enum class RecurrenceOption {
    None, Daily, WeeklyDays, Monthly
}

private enum class RecurrenceEndOption {
    Never, OnDate, AfterOccurrences
}

/**
 * Distinguishes "creating a brand-new task" from "editing one that already exists" for
 * [NewTaskSheet]. Every branch below must switch on this sealed type explicitly - never infer
 * edit-vs-create from whether a [TaskItem] happens to be null, so the creation flow can never be
 * accidentally affected by edit-only logic.
 */
internal sealed interface TaskSheetMode {
    data class Create(val date: LocalDate) : TaskSheetMode
    data class Edit(val task: TaskItem, val originalDate: LocalDate) : TaskSheetMode
}

/**
 * Local, UI-only mirror of a subtask being edited in the sheet. Unlike the plain
 * `List<String>` this replaces, it keeps [isDone] so that editing an existing task's subtasks
 * does not silently reset already-completed ones back to pending on save (see [TaskSheetMode.Edit]
 * prefill below).
 */
private data class TaskSheetSubtask(val title: String, val isDone: Boolean = false)

@Composable
internal fun NewTaskSheet(
    mode: TaskSheetMode,
    labels: List<LabelTag>,
    onCreateLabel: suspend (String, String) -> LabelTag?,
    onDismiss: () -> Unit,
    onSave: (LocalDate, TaskDraft, (SaveResult) -> Unit) -> Unit,
    onSaveRecurring: (LocalDate, TaskDraft, RecurrenceRule, RecurrenceEnd, (SaveResult) -> Unit) -> Unit,
    onSaveEdit: (String, LocalDate, TaskDraft, (SaveResult) -> Unit) -> Unit = { _, _, _, _ -> },
    templates: List<TaskTemplate> = emptyList(),
    onSaveTemplate: suspend (TaskTemplate) -> Boolean = { false },
) {
    val layout = AppLayout.metrics
    val timeZone = remember { TimeZone.currentSystemDefault() }
    val today = remember { currentDate(timeZone) }
    val date = when (mode) {
        is TaskSheetMode.Create -> mode.date
        is TaskSheetMode.Edit -> mode.originalDate
    }
    val editTask = (mode as? TaskSheetMode.Edit)?.task
    val palette = remember { labelColorPalette() }
    val usedColors = labels.map { it.colorHex.lowercase() }.toSet()
    val colorOptions = palette
        .filterNot { usedColors.contains(it.lowercase()) }
        .distinct()
        .ifEmpty { palette.distinct() }

    var title by remember(mode) { mutableStateOf(editTask?.title ?: "") }
    var details by remember(mode) { mutableStateOf(editTask?.details ?: "") }
    var selectedTime by remember(mode) { mutableStateOf(editTask?.time) }
    var errorText by remember { mutableStateOf<String?>(null) }
    // Editing never clamps to today: an already-overdue task must stay editable on its own date
    // without the sheet silently moving it forward (see [TaskSheetMode.Edit] and the past-date
    // validation below, which only blocks *actively* picking a different past date).
    var selectedDate by remember(mode) {
        mutableStateOf(
            when (mode) {
                is TaskSheetMode.Create -> if (mode.date < today) today else mode.date
                is TaskSheetMode.Edit -> mode.originalDate
            },
        )
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var deadlineDate by remember(mode) {
        mutableStateOf(editTask?.deadline?.toLocalDateTime(timeZone)?.date)
    }
    var showDeadlinePicker by remember { mutableStateOf(false) }
    val selectedReminderOffsetMinutes = remember(mode) { mutableStateListOf<Long>() }
    // Preserves isDone on prefill (see TaskSheetSubtask) - ordered by orderIndex like
    // TaskDetailsOverlay does, since the drafted list order becomes the new orderIndex on save.
    val subtaskItems = remember(mode) {
        mutableStateListOf<TaskSheetSubtask>().apply {
            editTask?.subtasks?.sortedBy { it.orderIndex }?.forEach { subtask ->
                add(TaskSheetSubtask(title = subtask.title, isDone = subtask.isDone))
            }
        }
    }
    var newSubtaskTitle by remember { mutableStateOf("") }
    var isSavingTemplate by remember { mutableStateOf(false) }
    val selectedLabelIds = remember(mode) {
        mutableStateListOf<String>().apply {
            editTask?.labels?.forEach { add(it.id) }
        }
    }
    var newLabelName by remember { mutableStateOf("") }
    var isCreatingLabel by remember { mutableStateOf(false) }
    var selectedColor by remember { mutableStateOf(colorOptions.first()) }
    var isSaving by remember { mutableStateOf(false) }
    var selectedRecurrence by remember { mutableStateOf<RecurrenceOption>(RecurrenceOption.None) }
    var selectedRecurrenceEndOption by remember { mutableStateOf(RecurrenceEndOption.Never) }
    var recurrenceEndDate by remember { mutableStateOf<LocalDate?>(null) }
    var showRecurrenceEndDatePicker by remember { mutableStateOf(false) }
    var recurrenceEndOccurrencesText by remember { mutableStateOf("") }
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

    // Creating is blocked on any past date. Editing only blocks *actively moving* the task to a
    // different past date - a task that was already overdue on [originalDate] must stay
    // editable without the sheet force-blocking the save just because nothing moved.
    val isPastSelected = when (mode) {
        is TaskSheetMode.Create -> selectedDate < today
        is TaskSheetMode.Edit -> selectedDate < today && selectedDate != mode.originalDate
    }
    val sheetBlur = if (showTimePicker) layout.size(14.dp, 10.dp) else 0.dp
    val pastDateBannerText = if (mode is TaskSheetMode.Edit) {
        "No se pueden mover tareas a fechas pasadas."
    } else {
        "No se pueden crear tareas en fechas pasadas."
    }
    val pastDateErrorText = if (mode is TaskSheetMode.Edit) {
        "No se pueden mover tareas a fechas pasadas"
    } else {
        "No se pueden crear tareas en fechas pasadas"
    }

    // Sugerencia de captura rapida: se recalcula en cada cambio de titulo, pero nunca aplica
    // nada por si sola - el usuario tiene que tocar "Aplicar" (ver docs/agendnote/
    // IMPLEMENTATION_PLAN.md, Fase 6). Al aplicarla, el titulo queda limpio y la sugerencia
    // desaparece sola porque el texto resultante ya no tiene nada que reconocer.
    val quickCaptureSuggestion = remember(title, today) {
        parseQuickCapture(title, today).takeIf { it.date != null || it.time != null }
    }

    // Recordatorios se calculan como offsets sobre un instante de referencia: la hora
    // planificada si hay una, si no el fin del día límite. Sin ninguna de las dos no hay nada
    // sobre lo que anclar un recordatorio, así que la sección queda oculta (ver
    // docs/agendnote/FASE4_PROPUESTA.md).
    val reminderReferenceInstant = remember(selectedDate, selectedTime, deadlineDate, timeZone) {
        val time = selectedTime
        val deadline = deadlineDate
        when {
            time != null -> LocalDateTime(selectedDate, time).toInstant(timeZone)
            deadline != null -> LocalDateTime(deadline, LocalTime(23, 59, 59)).toInstant(timeZone)
            else -> null
        }
    }
    // Los offsets elegidos no se limpian si la referencia cambia (p. ej. el usuario quita la
    // hora y deja solo el deadline) - siguen siendo válidos relativos a la nueva referencia.

    // Preserva el comportamiento previo a esta fase: una tarea con hora siempre generaba un
    // aviso a esa hora, sin que el usuario tuviera que pensar en "recordatorios" como concepto
    // aparte. Si hay hora y todavía no se eligió ningún preset, se preselecciona "En el
    // momento" (visible y desmarcable, no un valor oculto que solo aparece al guardar).
    LaunchedEffect(selectedTime) {
        if (mode is TaskSheetMode.Create && selectedTime != null && selectedReminderOffsetMinutes.isEmpty()) {
            selectedReminderOffsetMinutes.add(0L)
        }
    }

    // Edit prefill: reconstruct which presets were chosen by comparing the task's saved
    // reminder instants against what each preset would produce for the reference computed above
    // (see reminderReferenceInstant). Runs once per sheet instance (keyed on mode, which does not
    // change while this sheet stays open) so it never fights with the user unselecting a preset
    // afterwards.
    LaunchedEffect(mode) {
        if (mode is TaskSheetMode.Edit) {
            val reference = reminderReferenceInstant
            if (reference != null && selectedReminderOffsetMinutes.isEmpty()) {
                val matchedPresets = reminderOffsetPresets().mapNotNull { (minutes, _) ->
                    val expectedMillis = reference.toEpochMilliseconds() - minutes * 60_000L
                    val matches = mode.task.reminders.any { it.toEpochMilliseconds() == expectedMillis }
                    minutes.takeIf { matches }
                }
                selectedReminderOffsetMinutes.addAll(matchedPresets)
            }
        }
    }

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
                                text = if (mode is TaskSheetMode.Edit) "Editar tarea" else "Nueva tarea",
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

                        // Non-blocking notice, same tone as ConfirmDeleteDialog's series warning:
                        // editing a materialized occurrence never touches the rest of the series
                        // (recurrence editing itself stays out of scope for this batch - see the
                        // hidden "Repetir" section below).
                        if (mode is TaskSheetMode.Edit && mode.task.seriesId != null) {
                            Text(
                                text = "Es parte de una tarea recurrente: editar esta aparición " +
                                    "no afecta a las demás.",
                                style = MaterialTheme.typography.labelSmall,
                                color = GlassTheme.tokens.textSecondary,
                            )
                        }

                        if (templates.isNotEmpty()) {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(layout.width(6.dp, 5.dp)),
                            ) {
                                templates.forEach { template ->
                                    RecurrenceOptionChip(
                                        text = template.name,
                                        selected = false,
                                        onClick = {
                                            title = template.title
                                            details = template.details.orEmpty()
                                            selectedLabelIds.clear()
                                            selectedLabelIds.addAll(
                                                template.labelIds.filter { id -> labels.any { it.id == id } },
                                            )
                                            selectedReminderOffsetMinutes.clear()
                                            selectedReminderOffsetMinutes.addAll(template.reminderOffsetMinutes)
                                            subtaskItems.clear()
                                            subtaskItems.addAll(template.subtaskTitles.map { TaskSheetSubtask(title = it) })
                                        },
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
                                    if (isPastSelected) {
                                        errorText = pastDateErrorText
                                        return@GlassActionButton
                                    }
                                    if (selectedRecurrence == RecurrenceOption.WeeklyDays && selectedWeekDays.isEmpty()) {
                                        errorText = "Elegí al menos un día de la semana"
                                        return@GlassActionButton
                                    }
                                    if (selectedRecurrence != RecurrenceOption.None &&
                                        selectedRecurrenceEndOption == RecurrenceEndOption.OnDate &&
                                        recurrenceEndDate == null
                                    ) {
                                        errorText = "Elegí la fecha en la que termina la serie"
                                        return@GlassActionButton
                                    }
                                    if (selectedRecurrence != RecurrenceOption.None &&
                                        selectedRecurrenceEndOption == RecurrenceEndOption.AfterOccurrences &&
                                        recurrenceEndOccurrencesText.toIntOrNull()?.let { it > 0 } != true
                                    ) {
                                        errorText = "Indicá cuántas veces se repite"
                                        return@GlassActionButton
                                    }

                                    val chosenLabels = labels.filter { selectedLabelIds.contains(it.id) }
                                    // End-of-day deadline: the user only picks a date (see the
                                    // "Fecha límite" field below), not a time - a deadline of
                                    // "today" should still be reachable at any point today.
                                    val deadlineInstant = deadlineDate?.let {
                                        LocalDateTime(it, LocalTime(23, 59, 59)).toInstant(timeZone)
                                    }
                                    val reminderInstants = reminderReferenceInstant?.let { reference ->
                                        selectedReminderOffsetMinutes.map { minutes ->
                                            Instant.fromEpochMilliseconds(
                                                reference.toEpochMilliseconds() - minutes * 60_000L,
                                            )
                                        }
                                    } ?: emptyList()
                                    val draft = TaskDraft(
                                        title = trimmedTitle,
                                        details = details.trim().ifBlank { null },
                                        time = selectedTime,
                                        labels = chosenLabels,
                                        deadline = deadlineInstant,
                                        reminders = reminderInstants,
                                        subtasks = subtaskItems.mapIndexed { index, item ->
                                            Subtask(title = item.title, isDone = item.isDone, orderIndex = index)
                                        },
                                    )
                                    val rule = when (selectedRecurrence) {
                                        RecurrenceOption.None -> null
                                        RecurrenceOption.Daily -> RecurrenceRule.Daily
                                        RecurrenceOption.WeeklyDays -> RecurrenceRule.WeeklyDays(selectedWeekDays.toSet())
                                        RecurrenceOption.Monthly -> RecurrenceRule.Monthly(monthDay)
                                    }
                                    val recurrenceEnd = when (selectedRecurrenceEndOption) {
                                        RecurrenceEndOption.Never -> RecurrenceEnd.Never
                                        RecurrenceEndOption.OnDate -> recurrenceEndDate
                                            ?.let { RecurrenceEnd.OnDate(it) }
                                            ?: RecurrenceEnd.Never
                                        RecurrenceEndOption.AfterOccurrences -> recurrenceEndOccurrencesText
                                            .toIntOrNull()
                                            ?.let { RecurrenceEnd.AfterOccurrences(it) }
                                            ?: RecurrenceEnd.Never
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
                                    when (mode) {
                                        is TaskSheetMode.Edit -> onSaveEdit(mode.task.id, selectedDate, draft, onResult)
                                        is TaskSheetMode.Create -> {
                                            if (rule != null) {
                                                onSaveRecurring(selectedDate, draft, rule, recurrenceEnd, onResult)
                                            } else {
                                                onSave(selectedDate, draft, onResult)
                                            }
                                        }
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
                        if (quickCaptureSuggestion != null) {
                            GlassSurface(
                                shape = RoundedCornerShape(layout.size(14.dp, 12.dp)),
                                tint = GlassTheme.tokens.glassFillStrong,
                                shadowElevation = 0.dp,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = layout.width(12.dp, 10.dp),
                                            vertical = layout.height(8.dp, 6.dp),
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "Detecté: " + describeQuickCaptureSuggestion(quickCaptureSuggestion),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = GlassTheme.tokens.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                            .clickable(
                                                role = Role.Button,
                                                onClickLabel = "Aplicar fecha y hora detectadas",
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = {
                                                    title = quickCaptureSuggestion.title
                                                    quickCaptureSuggestion.date?.let {
                                                        if (it >= today) selectedDate = it
                                                    }
                                                    quickCaptureSuggestion.time?.let { selectedTime = it }
                                                },
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "Aplicar",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = GlassTheme.tokens.accentOnLight,
                                        )
                                    }
                                }
                            }
                        }
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

                    if (reminderReferenceInstant != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(layout.height(8.dp, 6.dp))) {
                            Text(
                                text = "Recordatorios",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = layout.text(15.sp, 14.sp),
                                ),
                                color = GlassTheme.tokens.textSecondary,
                            )
                            Row(
                                modifier = Modifier.selectableGroup(),
                                horizontalArrangement = Arrangement.spacedBy(layout.width(8.dp, 6.dp)),
                            ) {
                                reminderOffsetPresets().forEach { (minutes, text) ->
                                    val selected = selectedReminderOffsetMinutes.contains(minutes)
                                    RecurrenceOptionChip(
                                        text = text,
                                        selected = selected,
                                        role = Role.Checkbox,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            if (selected) {
                                                selectedReminderOffsetMinutes.remove(minutes)
                                            } else {
                                                selectedReminderOffsetMinutes.add(minutes)
                                            }
                                        },
                                    )
                                }
                            }
                            // The scheduler only fires the single nearest reminder per task today
                            // (see core/notifications/ReminderResolution.kt) even though all
                            // selected offsets are saved - decision documented in
                            // docs/OPERATION_ANNIVERSARY_STATUS.md (Operación Aniversario,
                            // reminders lane): tell the user the truth instead of letting the UI
                            // promise more than the system does. Remove this notice once
                            // multi-notification scheduling is implemented and device-verified.
                            if (selectedReminderOffsetMinutes.size > 1) {
                                Text(
                                    text = "Por ahora solo se te avisará con el recordatorio " +
                                        "más próximo; los demás quedan guardados pero no se " +
                                        "disparan todavía.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GlassTheme.tokens.textSecondary,
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(layout.height(8.dp, 6.dp))) {
                        Text(
                            text = "Subtareas (opcional)",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = layout.text(15.sp, 14.sp),
                            ),
                            color = GlassTheme.tokens.textSecondary,
                        )
                        subtaskItems.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(layout.width(8.dp, 6.dp)),
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (item.isDone) {
                                        GlassTheme.tokens.textSecondary
                                    } else {
                                        GlassTheme.tokens.textPrimary
                                    },
                                    textDecoration = if (item.isDone) TextDecoration.LineThrough else TextDecoration.None,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Box(
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                        .clickable(
                                            role = Role.Button,
                                            onClickLabel = "Quitar subtarea",
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { subtaskItems.removeAt(index) },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "Quitar",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = GlassTheme.tokens.errorContent,
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(layout.width(12.dp, 8.dp)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GlassTextField(
                                value = newSubtaskTitle,
                                onValueChange = { newSubtaskTitle = it },
                                placeholder = "Nueva subtarea",
                                label = "Título de la subtarea",
                                modifier = Modifier.weight(1f),
                            )
                            GlassActionButton(
                                modifier = Modifier
                                    .width(layout.width(112.dp, 98.dp))
                                    .height(layout.height(52.dp, 48.dp)),
                                text = "Añadir",
                                enabled = newSubtaskTitle.isNotBlank(),
                                tint = GlassTheme.tokens.glassFillStrong,
                                textColor = GlassTheme.tokens.textPrimary,
                                onClick = {
                                    val trimmed = newSubtaskTitle.trim()
                                    if (trimmed.isEmpty()) return@GlassActionButton
                                    subtaskItems.add(TaskSheetSubtask(title = trimmed))
                                    newSubtaskTitle = ""
                                },
                            )
                        }
                        // Saving as a template applies to freshly-composed drafts, not to a task
                        // that already exists - out of scope for this batch, same as recurrence.
                        if (mode is TaskSheetMode.Create) {
                            GlassActionButton(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(layout.height(46.dp, 44.dp)),
                                text = if (isSavingTemplate) "Guardando plantilla..." else "Guardar como plantilla",
                                enabled = title.isNotBlank() && !isSavingTemplate,
                                tint = GlassTheme.tokens.glassFill,
                                textColor = GlassTheme.tokens.textSecondary,
                                onClick = {
                                    val trimmedTitle = title.trim()
                                    if (trimmedTitle.isEmpty()) return@GlassActionButton
                                    isSavingTemplate = true
                                    scope.launch {
                                        val template = TaskTemplate(
                                            id = "",
                                            name = trimmedTitle,
                                            title = trimmedTitle,
                                            details = details.trim().ifBlank { null },
                                            labelIds = labels.filter { selectedLabelIds.contains(it.id) }.map { it.id },
                                            reminderOffsetMinutes = selectedReminderOffsetMinutes.toList(),
                                            subtaskTitles = subtaskItems.map { it.title },
                                        )
                                        val success = onSaveTemplate(template)
                                        isSavingTemplate = false
                                        if (!success) errorText = "No se pudo guardar la plantilla"
                                    }
                                },
                            )
                        }
                    }

                    // Changing an already-materialized task's recurrence, or applying it
                    // retroactively, is out of scope for this batch (see TaskSheetMode doc) -
                    // hidden rather than adapted so there is no half-working "edit this and the
                    // following" affordance to trip over.
                    if (mode is TaskSheetMode.Create) {
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

                        if (selectedRecurrence != RecurrenceOption.None) {
                            Text(
                                text = "Termina",
                                style = MaterialTheme.typography.bodyMedium,
                                color = GlassTheme.tokens.textSecondary,
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectableGroup(),
                                horizontalArrangement = Arrangement.spacedBy(layout.width(8.dp, 6.dp)),
                            ) {
                                RecurrenceOptionChip(
                                    text = "Nunca",
                                    selected = selectedRecurrenceEndOption == RecurrenceEndOption.Never,
                                    modifier = Modifier.weight(1f),
                                    onClick = { selectedRecurrenceEndOption = RecurrenceEndOption.Never },
                                )
                                RecurrenceOptionChip(
                                    text = "En fecha",
                                    selected = selectedRecurrenceEndOption == RecurrenceEndOption.OnDate,
                                    modifier = Modifier.weight(1f),
                                    onClick = { selectedRecurrenceEndOption = RecurrenceEndOption.OnDate },
                                )
                                RecurrenceOptionChip(
                                    text = "Después de N",
                                    selected = selectedRecurrenceEndOption == RecurrenceEndOption.AfterOccurrences,
                                    modifier = Modifier.weight(1f),
                                    onClick = { selectedRecurrenceEndOption = RecurrenceEndOption.AfterOccurrences },
                                )
                            }
                            if (selectedRecurrenceEndOption == RecurrenceEndOption.OnDate) {
                                GlassSurface(
                                    shape = RoundedCornerShape(layout.size(16.dp, 14.dp)),
                                    tint = GlassTheme.tokens.glassFill,
                                    shadowElevation = 0.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(layout.height(48.dp, 46.dp))
                                        .clip(RoundedCornerShape(layout.size(16.dp, 14.dp)))
                                        .clickable(
                                            role = Role.Button,
                                            onClickLabel = "Seleccionar fecha de fin de la serie",
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { showRecurrenceEndDatePicker = true },
                                        ),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = layout.width(14.dp, 12.dp)),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        Text(
                                            text = recurrenceEndDate?.let(::formatShortDateWithYear)
                                                ?: "Elegí la última fecha",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (recurrenceEndDate == null) {
                                                GlassTheme.tokens.textSecondary
                                            } else {
                                                GlassTheme.tokens.textPrimary
                                            },
                                        )
                                    }
                                }
                            }
                            if (selectedRecurrenceEndOption == RecurrenceEndOption.AfterOccurrences) {
                                GlassTextField(
                                    value = recurrenceEndOccurrencesText,
                                    onValueChange = { input ->
                                        recurrenceEndOccurrencesText = input.filter { it.isDigit() }.take(4)
                                    },
                                    placeholder = "Número de repeticiones",
                                    label = "Número de repeticiones",
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    }

                    if (isPastSelected) {
                        Text(
                            text = pastDateBannerText,
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

            if (showRecurrenceEndDatePicker) {
                DatePickerOverlay(
                    selectedDate = recurrenceEndDate ?: selectedDate,
                    onSelect = {
                        recurrenceEndDate = it
                        showRecurrenceEndDatePicker = false
                    },
                    onClear = { recurrenceEndDate = null },
                    onDismiss = { showRecurrenceEndDatePicker = false },
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
    onRequestEdit: () -> Unit,
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

                if (task.subtasks.isNotEmpty()) {
                    val doneCount = task.subtasks.count { it.isDone }
                    Column(verticalArrangement = Arrangement.spacedBy(layout.height(6.dp, 4.dp))) {
                        Text(
                            text = "Subtareas ($doneCount/${task.subtasks.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = GlassTheme.tokens.textSecondary,
                        )
                        task.subtasks.sortedBy { it.orderIndex }.forEach { subtask ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(layout.width(8.dp, 6.dp)),
                            ) {
                                Text(
                                    text = if (subtask.isDone) "☑" else "☐",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = GlassTheme.tokens.textSecondary,
                                )
                                Text(
                                    text = subtask.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (subtask.isDone) {
                                        GlassTheme.tokens.textSecondary
                                    } else {
                                        GlassTheme.tokens.textPrimary
                                    },
                                    textDecoration = if (subtask.isDone) TextDecoration.LineThrough else TextDecoration.None,
                                )
                            }
                        }
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

                // REVIEW: "Marcar como pendiente"/"Marcar como hecho" is the longest label in
                // this overlay - cramming it into a third of the row alongside "Editar"/
                // "Eliminar" (as a naive 3-way weight(1f) row would) wraps or truncates it on
                // compact phones (same class of issue as the header split above). Short-label
                // actions share a row; the long one gets full width on its own.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(layout.width(10.dp, 8.dp)),
                ) {
                    GlassActionButton(
                        text = "Editar",
                        modifier = Modifier.weight(1f),
                        tint = GlassTheme.tokens.glassFill,
                        textColor = GlassTheme.tokens.textPrimary,
                        onClick = onRequestEdit,
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
                        text = if (task.isDone) "Marcar como pendiente" else "Marcar como hecho",
                        modifier = Modifier.fillMaxWidth(),
                        tint = GlassTheme.tokens.glassFill,
                        textColor = GlassTheme.tokens.textPrimary,
                        onClick = { onToggleDone(!task.isDone) },
                    )

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

private fun describeQuickCaptureSuggestion(suggestion: QuickCaptureResult): String {
    val parts = listOfNotNull(
        suggestion.date?.let(::formatShortDateWithYear),
        suggestion.time?.let(::formatTime),
    )
    return parts.joinToString(", ")
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

/**
 * Presets de recordatorio como minutos de antelación sobre la hora planificada o el deadline
 * (ver [reminderReferenceInstant] en [NewTaskSheet]). No incluye una opción personalizada
 * todavía - ver docs/agendnote/FASE4_PROPUESTA.md.
 */
private fun reminderOffsetPresets(): List<Pair<Long, String>> {
    return listOf(
        0L to "En el momento",
        10L to "10 min antes",
        60L to "1 hora antes",
        1440L to "1 día antes",
    )
}
