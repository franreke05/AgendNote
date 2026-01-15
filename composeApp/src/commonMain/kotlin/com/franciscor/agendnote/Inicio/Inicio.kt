package com.franciscor.agendnote.Inicio

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.franciscor.agendnote.ui.components.GlassBackground
import com.franciscor.agendnote.ui.components.GlassActionButton
import com.franciscor.agendnote.ui.components.GlassIconButton
import com.franciscor.agendnote.ui.components.GlassSurface
import com.franciscor.agendnote.ui.components.GlassSearchBar
import com.franciscor.agendnote.ui.components.GlassTextField
import com.franciscor.agendnote.ui.theme.GlassTheme
import com.franciscor.agendnote.data.AppConfig
import com.franciscor.agendnote.data.AgendaApiClient
import com.franciscor.agendnote.data.SupabaseAgendaRepository
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Clock

private enum class HomeTab {
    AGENDA,
    LABELS,
    SETTINGS,
}

private data class PendingDelete(
    val date: LocalDate,
    val task: TaskItem,
)

private data class SaveResult(
    val success: Boolean,
    val errorMessage: String? = null,
)

private enum class BulkAction {
    DELETE_NOTES,
    DELETE_LABELS,
}

@Composable
fun InicioScreen(
    isDarkMode: Boolean = false,
    onThemeChange: (Boolean) -> Unit = {},
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val baseDensity = LocalDensity.current
        val scale = remember(maxWidth) { uiScaleForWidth(maxWidth) }
        CompositionLocalProvider(
            LocalDensity provides Density(
                density = baseDensity.density * scale,
                fontScale = baseDensity.fontScale * scale,
            ),
        ) {
            InicioContent(
                isDarkMode = isDarkMode,
                onThemeChange = onThemeChange,
            )
        }
    }
}

private fun uiScaleForWidth(width: Dp): Float {
    return when {
        width < 380.dp -> 0.94f
        width < 430.dp -> 1f
        width < 700.dp -> 1.08f
        else -> 1.25f
    }
}

@Composable
private fun InicioContent(
    isDarkMode: Boolean,
    onThemeChange: (Boolean) -> Unit,
) {
    val timeZone = remember { TimeZone.currentSystemDefault() }
    val today = remember { Clock.System.todayIn(timeZone) }
    var selectedDate by remember { mutableStateOf(today) }
    var direction by remember { mutableStateOf(1) }
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val useRemote = AppConfig.API_BASE_URL.isNotBlank() && AppConfig.APP_SECRET.isNotBlank()
    var backgroundUrl by remember {
        mutableStateOf(AppConfig.BACKGROUND_URL.trim())
    }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(HomeTab.AGENDA) }
    var showCalendar by remember { mutableStateOf(false) }
    val repository = remember(useRemote) {
        if (useRemote) SupabaseAgendaRepository(AgendaApiClient(AppConfig.API_BASE_URL, AppConfig.APP_SECRET)) else null
    }
    val labels = remember(useRemote) { mutableStateListOf<LabelTag>() }
    val tasksByDate = remember { mutableStateMapOf<LocalDate, List<TaskItem>>() }
    var showTaskSheet by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }
    var pendingBulkAction by remember { mutableStateOf<BulkAction?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var taskErrorMessage by remember { mutableStateOf<String?>(null) }
    var labelsErrorMessage by remember { mutableStateOf<String?>(null) }

    fun tasksFor(date: LocalDate): List<TaskItem> {
        return tasksByDate[date] ?: emptyList()
    }

    fun moveDay(delta: Int) {
        direction = delta
        selectedDate = selectedDate.plus(delta, DateTimeUnit.DAY)
    }

    LaunchedEffect(selectedDate) {
        rotation.snapTo(if (direction > 0) -10f else 10f)
        rotation.animateTo(0f, tween(durationMillis = 520, easing = FastOutSlowInEasing))
    }

    LaunchedEffect(useRemote) {
        if (!useRemote || repository == null) return@LaunchedEffect
        runCatching {
            repository.fetchLabels()
        }.onSuccess { remoteLabels ->
            labels.clear()
            labels.addAll(remoteLabels)
            labelsErrorMessage = null
        }.onFailure {
            labelsErrorMessage = "No se pudieron cargar las etiquetas"
        }
    }

    LaunchedEffect(useRemote) {
        if (!useRemote || repository == null) return@LaunchedEffect
        if (backgroundUrl.isNotBlank()) return@LaunchedEffect
        runCatching {
            repository.fetchBackgroundUrl()
        }.onSuccess { url ->
            if (!url.isNullOrBlank()) {
                backgroundUrl = url
            }
        }
    }

    LaunchedEffect(selectedDate, useRemote, selectedTab) {
        if (!useRemote || repository == null) return@LaunchedEffect
        if (selectedTab != HomeTab.AGENDA) return@LaunchedEffect
        if (tasksByDate.containsKey(selectedDate)) return@LaunchedEffect
        isLoading = true
        runCatching {
            repository.fetchTasks(selectedDate)
        }.onSuccess { tasks ->
            tasksByDate[selectedDate] = orderTasks(tasks)
            taskErrorMessage = null
        }.onFailure {
            tasksByDate[selectedDate] = emptyList()
            taskErrorMessage = "No se pudieron cargar las tareas"
        }
        isLoading = false
    }

    val swipeThreshold = 72.dp
    val swipeEdgeGuard = 24.dp
    val swipeModifier = Modifier.pointerInput(selectedDate) {
        var dragTotal = 0f
        var allowSwipe = false
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
                    if (dragTotal > swipeThreshold.toPx()) {
                        moveDay(-1)
                    } else if (dragTotal < -swipeThreshold.toPx()) {
                        moveDay(1)
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
    }

    val createLabel: suspend (String, String) -> LabelTag? = label@{ name, colorHex ->
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@label null
        if (!useRemote || repository == null) {
            val label = LabelTag(
                id = "label-${Clock.System.now().toEpochMilliseconds()}",
                name = trimmed,
                colorHex = colorHex,
            )
            labels.add(label)
            label
        } else {
            runCatching { repository.createLabel(trimmed, colorHex) }
                .onSuccess {
                    labels.add(it)
                }
                .getOrNull()
        }
    }

    val saveTask: suspend (LocalDate, TaskDraft) -> SaveResult = save@{ targetDate, draft ->
        val trimmedTitle = draft.title.trim()
        if (trimmedTitle.isEmpty()) return@save SaveResult(false, "Titulo requerido")
        if (!useRemote || repository == null) {
            val task = TaskItem(
                id = "task-${Clock.System.now().toEpochMilliseconds()}",
                title = trimmedTitle,
                details = draft.details?.trim()?.ifBlank { null },
                time = draft.time,
                labels = draft.labels,
            )
            val base = tasksByDate[targetDate] ?: emptyList()
            tasksByDate[targetDate] = orderTasks(base + task)
            SaveResult(true)
        } else {
            try {
                val created = repository.createTask(targetDate, draft.copy(title = trimmedTitle))
                val base = tasksByDate[targetDate] ?: emptyList()
                tasksByDate[targetDate] = orderTasks(base + created)
                SaveResult(true)
            } catch (error: Throwable) {
                val message = resolveServerError(error)
                taskErrorMessage = message
                SaveResult(false, message)
            }
        }
    }

    val deleteLabel: suspend (LabelTag) -> Boolean = delete@{ label ->
        val removeLocal = {
            labels.removeAll { it.id == label.id }
            removeLabelFromTasks(tasksByDate, label.id)
        }

        if (!useRemote || repository == null) {
            removeLocal()
            return@delete true
        }

        return@delete runCatching { repository.deleteLabel(label.id) }
            .onSuccess { success ->
                if (success) removeLocal()
            }
            .getOrDefault(false)
    }

    val toggleTaskDone: suspend (LocalDate, TaskItem, Boolean) -> Boolean = toggle@{ date, task, isDone ->
        if (!useRemote || repository == null) {
            val updated = task.copy(isDone = isDone)
            val base = tasksByDate[date] ?: emptyList()
            tasksByDate[date] = base.map { if (it.id == task.id) updated else it }
            return@toggle true
        }

        return@toggle runCatching { repository.updateTaskDone(task.id, isDone) }
            .onSuccess { updated ->
                val base = tasksByDate[date] ?: emptyList()
                tasksByDate[date] = base.map { if (it.id == task.id) updated else it }
                taskErrorMessage = null
            }
            .onFailure {
                taskErrorMessage = "No se pudo actualizar la tarea"
            }
            .isSuccess
    }

    val deleteTask: suspend (LocalDate, TaskItem) -> Boolean = delete@{ date, task ->
        val removeLocal = {
            val base = tasksByDate[date] ?: emptyList()
            tasksByDate[date] = base.filterNot { it.id == task.id }
        }

        if (!useRemote || repository == null) {
            removeLocal()
            return@delete true
        }

        return@delete runCatching { repository.deleteTask(task.id) }
            .onSuccess { success ->
                if (success) {
                    removeLocal()
                    taskErrorMessage = null
                } else {
                    taskErrorMessage = "No se pudo eliminar la tarea"
                }
            }
            .onFailure {
                taskErrorMessage = "No se pudo eliminar la tarea"
            }
            .getOrDefault(false)
    }

    val deleteAllNotes: suspend () -> Boolean = deleteAll@{
        val removeLocal = {
            tasksByDate.clear()
        }

        if (!useRemote || repository == null) {
            removeLocal()
            return@deleteAll true
        }

        return@deleteAll runCatching { repository.deleteAllTasks() }
            .onSuccess { success ->
                if (success) {
                    removeLocal()
                    taskErrorMessage = null
                } else {
                    taskErrorMessage = "No se pudieron borrar las notas"
                }
            }
            .onFailure {
                taskErrorMessage = "No se pudieron borrar las notas"
            }
            .getOrDefault(false)
    }

    val deleteAllLabels: suspend () -> Boolean = deleteAll@{
        val removeLocal = {
            labels.clear()
            clearLabelsFromTasks(tasksByDate)
        }

        if (!useRemote || repository == null) {
            removeLocal()
            return@deleteAll true
        }

        return@deleteAll runCatching { repository.deleteAllLabels() }
            .onSuccess { success ->
                if (success) {
                    removeLocal()
                    labelsErrorMessage = null
                } else {
                    labelsErrorMessage = "No se pudieron borrar las etiquetas"
                }
            }
            .onFailure {
                labelsErrorMessage = "No se pudieron borrar las etiquetas"
            }
            .getOrDefault(false)
    }

    val blurRadius = if (showTaskSheet || showCalendar || pendingBulkAction != null) 18.dp else 0.dp

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(blurRadius),
        ) {
            GlassBackground(
                modifier = Modifier.fillMaxSize(),
                imageUrl = backgroundUrl.ifBlank { null },
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeContentPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                when (selectedTab) {
                    HomeTab.AGENDA -> {
                        AgendaHeader(
                            date = selectedDate,
                            isToday = selectedDate == today,
                            onPrevious = { moveDay(-1) },
                            onNext = { moveDay(1) },
                            onCalendarClick = { showCalendar = true },
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        GlassSearchBar(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            AnimatedContent(
                                targetState = selectedDate,
                                transitionSpec = {
                                    val forward = targetState > initialState
                                    val directionSign = if (forward) 1 else -1
                                    val enterOrigin = if (forward) TransformOrigin(0f, 0.5f) else TransformOrigin(1f, 0.5f)
                                    val exitOrigin = if (forward) TransformOrigin(1f, 0.5f) else TransformOrigin(0f, 0.5f)

                                    val enter = slideInHorizontally(
                                        animationSpec = tween(420, easing = FastOutSlowInEasing),
                                    ) { directionSign * (it / 2) } + fadeIn(tween(320, easing = FastOutSlowInEasing)) + scaleIn(
                                        initialScale = 0.97f,
                                        transformOrigin = enterOrigin,
                                        animationSpec = tween(420, easing = FastOutSlowInEasing),
                                    )
                                    val exit = slideOutHorizontally(
                                        animationSpec = tween(420, easing = FastOutSlowInEasing),
                                    ) { -directionSign * (it / 2) } + fadeOut(tween(260, easing = FastOutSlowInEasing)) + scaleOut(
                                        targetScale = 1.02f,
                                        transformOrigin = exitOrigin,
                                        animationSpec = tween(420, easing = FastOutSlowInEasing),
                                    )

                                    enter togetherWith exit using SizeTransform(clip = false)
                                },
                                modifier = Modifier.fillMaxSize(),
                                label = "glassDayTransition",
                            ) { date ->
                                val baseTasks = orderTasks(tasksFor(date))
                                val filteredTasks = filterTasks(baseTasks, searchQuery)
                                DayAgenda(
                                    date = date,
                                    isToday = date == today,
                                    tasks = filteredTasks,
                                    rotation = rotation.value,
                                    isLoading = isLoading,
                                    errorMessage = taskErrorMessage,
                                    searchQuery = searchQuery,
                                    swipeModifier = swipeModifier,
                                    onRequestDelete = { task -> pendingDelete = PendingDelete(date, task) },
                                    onToggleDone = { task, isDone -> toggleTaskDone(date, task, isDone) },
                                )
                            }
                        }
                    }

                    HomeTab.LABELS -> {
                        SectionHeader(
                            title = "Etiquetas",
                            subtitle = "Organiza por color",
                        )
                        LabelsScreen(
                            labels = labels,
                            errorMessage = labelsErrorMessage,
                            onCreateLabel = createLabel,
                            onDeleteLabel = deleteLabel,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    HomeTab.SETTINGS -> {
                        SectionHeader(
                            title = "Ajustes",
                            subtitle = "Personaliza la agenda",
                        )
                        SettingsScreen(
                            isDarkMode = isDarkMode,
                            onThemeChange = onThemeChange,
                            onDeleteAllNotes = { pendingBulkAction = BulkAction.DELETE_NOTES },
                            onDeleteAllLabels = { pendingBulkAction = BulkAction.DELETE_LABELS },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = selectedTab == HomeTab.AGENDA,
                enter = fadeIn(tween(180, easing = FastOutSlowInEasing)) + slideInVertically(
                    tween(200, easing = FastOutSlowInEasing),
                ) { it / 2 },
                exit = fadeOut(tween(160, easing = FastOutSlowInEasing)) + slideOutVertically(
                    tween(180, easing = FastOutSlowInEasing),
                ) { it / 2 },
                modifier = Modifier.align(Alignment.BottomEnd),
            ) {
                GlassSurface(
                    modifier = Modifier
                        .padding(end = 22.dp, bottom = 96.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { showTaskSheet = true },
                        ),
                    shape = CircleShape,
                    tint = GlassTheme.tokens.glassFillStrong,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(GlassTheme.tokens.glassFillStrong)
                            .padding(14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Nueva tarea",
                            tint = GlassTheme.tokens.textPrimary,
                        )
                    }
                }
            }

            BottomBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 18.dp, vertical = 22.dp),
                selectedTab = selectedTab,
                onSelect = { selectedTab = it },
            )
        }

        NewTaskSheet(
            visible = showTaskSheet,
            date = selectedDate,
            labels = labels,
            onCreateLabel = createLabel,
            onDismiss = { showTaskSheet = false },
            onSave = saveTask,
        )

        CalendarOverlay(
            visible = showCalendar,
            selectedDate = selectedDate,
            today = today,
            tasksByDate = tasksByDate,
            onSelectDate = { date ->
                selectedDate = date
                showCalendar = false
            },
            onDismiss = { showCalendar = false },
        )

        val pending = pendingDelete
        ConfirmDeleteDialog(
            visible = pending != null,
            taskTitle = pending?.task?.title.orEmpty(),
            onConfirm = {
                val target = pendingDelete
                pendingDelete = null
                if (target != null) {
                    scope.launch {
                        deleteTask(target.date, target.task)
                    }
                }
            },
            onDismiss = { pendingDelete = null },
        )

        val bulkAction = pendingBulkAction
        ConfirmBulkDialog(
            visible = bulkAction != null,
            title = if (bulkAction == BulkAction.DELETE_NOTES) "Borrar todas las notas?" else "Borrar todas las etiquetas?",
            message = if (bulkAction == BulkAction.DELETE_NOTES) {
                "Esta accion elimina todas las notas guardadas."
            } else {
                "Esta accion elimina todas las etiquetas creadas."
            },
            onConfirm = {
                val action = pendingBulkAction
                pendingBulkAction = null
                if (action != null) {
                    scope.launch {
                        if (action == BulkAction.DELETE_NOTES) {
                            deleteAllNotes()
                        } else {
                            deleteAllLabels()
                        }
                    }
                }
            },
            onDismiss = { pendingBulkAction = null },
        )
    }
}

@Composable
private fun AgendaHeader(
    date: LocalDate,
    isToday: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCalendarClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Agenda",
                    style = MaterialTheme.typography.displayLarge,
                    color = GlassTheme.tokens.textPrimary,
                )
                GlassSurface(
                    shape = RoundedCornerShape(14.dp),
                    tint = GlassTheme.tokens.glassFillStrong,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onCalendarClick,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CalendarToday,
                        contentDescription = "Calendario",
                        tint = GlassTheme.tokens.textPrimary,
                        modifier = Modifier.padding(6.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatFullDate(date),
                    style = MaterialTheme.typography.bodyLarge,
                    color = GlassTheme.tokens.textSecondary,
                )
                if (isToday) {
                    Spacer(modifier = Modifier.width(10.dp))
                    GlassSurface(
                        shape = RoundedCornerShape(12.dp),
                        tint = GlassTheme.tokens.glassFillStrong,
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        Text(
                            text = "Hoy",
                            style = MaterialTheme.typography.labelMedium,
                            color = GlassTheme.tokens.textPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassIconButton(
                icon = Icons.Rounded.ChevronLeft,
                contentDescription = "Dia anterior",
                onClick = onPrevious,
            )
            GlassIconButton(
                icon = Icons.Rounded.ChevronRight,
                contentDescription = "Dia siguiente",
                onClick = onNext,
            )
        }
    }
}

@Composable
private fun DayAgenda(
    date: LocalDate,
    isToday: Boolean,
    tasks: List<TaskItem>,
    rotation: Float,
    isLoading: Boolean,
    errorMessage: String?,
    searchQuery: String,
    swipeModifier: Modifier,
    onRequestDelete: (TaskItem) -> Unit,
    onToggleDone: suspend (TaskItem, Boolean) -> Boolean,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .then(swipeModifier)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12 * density
            },
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 140.dp),
    ) {
        item {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                tint = GlassTheme.tokens.glassFillStrong,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = formatDayTitle(date),
                            style = MaterialTheme.typography.titleLarge,
                            color = GlassTheme.tokens.textPrimary,
                        )
                        Text(
                            text = formatShortDate(date),
                            style = MaterialTheme.typography.bodyMedium,
                            color = GlassTheme.tokens.textSecondary,
                        )
                    }
                }
            }
        }

        if (isLoading) {
            item {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text(
                        text = "Cargando tareas...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTheme.tokens.textSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
            }
        }

        if (errorMessage != null && !isLoading) {
            item {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB53B3B),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
            }
        }

        if (tasks.isEmpty()) {
            item {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) "Sin tareas para este dia" else "Sin resultados",
                        style = MaterialTheme.typography.bodyLarge,
                        color = GlassTheme.tokens.textSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                    )
                }
            }
        } else {
            items(tasks, key = { it.id }) { task ->
                SwipeableTaskCard(
                    task = task,
                    onRequestDelete = { onRequestDelete(task) },
                    onToggleDone = { isDone -> onToggleDone(task, isDone) },
                )
            }
        }
    }
}

@Composable
private fun SwipeableTaskCard(
    task: TaskItem,
    onRequestDelete: () -> Unit,
    onToggleDone: suspend (Boolean) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val maxOffset = with(density) { 108.dp.toPx() }
    val threshold = with(density) { 72.dp.toPx() }
    var offsetX by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var isPerformingAction by remember { mutableStateOf(false) }

    val animatedOffset by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = if (isDragging) tween(durationMillis = 0) else tween(
            durationMillis = 180,
            easing = FastOutSlowInEasing,
        ),
        label = "taskSwipeOffset",
    )
    val isSwipeRight = animatedOffset >= 0f
    val progress = (abs(animatedOffset) / maxOffset).coerceIn(0f, 1f)

    Box(modifier = modifier.fillMaxWidth()) {
        SwipeActionBackground(
            isSwipeRight = isSwipeRight,
            progress = progress,
            modifier = Modifier.fillMaxWidth(),
        )
        TaskCard(
            task = task,
            modifier = Modifier
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .pointerInput(task.id, isPerformingAction) {
                    if (isPerformingAction) return@pointerInput
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            if (isPerformingAction) return@detectHorizontalDragGestures
                            isDragging = true
                            val newOffset = (offsetX + dragAmount).coerceIn(-maxOffset, maxOffset)
                            offsetX = newOffset
                            change.consume()
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
                                            val success = onToggleDone(true)
                                            isPerformingAction = false
                                            if (success) {
                                                offsetX = 0f
                                            } else {
                                                offsetX = 0f
                                            }
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
                },
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
    val actionColor = if (isSwipeRight) Color(0xFF43B87B) else Color(0xFFE06B6B)
    val label = if (isSwipeRight) "Hecha" else "Eliminar"
    val icon = if (isSwipeRight) Icons.Rounded.Check else Icons.Rounded.Delete
    val eased = progress.coerceIn(0f, 1f)
    val fillAlpha = 0.65f * eased
    val strokeAlpha = 0.85f * eased
    val contentAlpha = 0.2f + (0.8f * eased)

    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(26.dp),
        tint = actionColor.copy(alpha = fillAlpha),
        strokeColor = actionColor.copy(alpha = strokeAlpha),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = if (isSwipeRight) Arrangement.Start else Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSwipeRight) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color.White.copy(alpha = contentAlpha),
                )
                Spacer(modifier = Modifier.width(8.dp))
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
                Spacer(modifier = Modifier.width(8.dp))
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
private fun ConfirmDeleteDialog(
    visible: Boolean,
    taskTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160, easing = FastOutSlowInEasing)) + scaleIn(tween(180, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(140, easing = FastOutSlowInEasing)) + scaleOut(tween(160, easing = FastOutSlowInEasing)),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
            GlassSurface(
                shape = RoundedCornerShape(26.dp),
                tint = GlassTheme.tokens.glassFillStrong,
                strokeColor = GlassTheme.tokens.glassStroke,
                modifier = Modifier
                    .padding(horizontal = 26.dp)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Eliminar tarea?",
                        style = MaterialTheme.typography.titleMedium,
                        color = GlassTheme.tokens.textPrimary,
                    )
                    Text(
                        text = "Se borrara \"$taskTitle\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTheme.tokens.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        GlassActionButton(
                            text = "Cancelar",
                            modifier = Modifier.weight(1f),
                            tint = GlassTheme.tokens.glassFillStrong,
                            textColor = GlassTheme.tokens.textPrimary,
                            onClick = onDismiss,
                        )
                        GlassActionButton(
                            text = "Eliminar",
                            modifier = Modifier.weight(1f),
                            tint = Color(0xFFE06B6B),
                            onClick = onConfirm,
                        )
                    }
                }
            }
        }
    }
}

private suspend fun resolveServerError(error: Throwable): String {
    val defaultMessage = error.message?.takeIf { it.isNotBlank() } ?: "No se pudo guardar"
    val response = (error as? ResponseException)?.response ?: return defaultMessage
    val payload = runCatching { response.bodyAsText() }.getOrNull()?.trim().orEmpty()
    if (payload.isBlank()) return "${response.status.value} ${response.status.description}".trim().ifBlank { defaultMessage }

    val jsonMessage = runCatching {
        val json = Json.parseToJsonElement(payload).jsonObject
        json["error"]?.jsonPrimitive?.contentOrNull
            ?: json["message"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    return jsonMessage?.takeIf { it.isNotBlank() } ?: payload
}

@Composable
private fun ConfirmBulkDialog(
    visible: Boolean,
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160, easing = FastOutSlowInEasing)) + scaleIn(tween(180, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(140, easing = FastOutSlowInEasing)) + scaleOut(tween(160, easing = FastOutSlowInEasing)),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
            GlassSurface(
                shape = RoundedCornerShape(26.dp),
                tint = GlassTheme.tokens.glassFillStrong,
                strokeColor = GlassTheme.tokens.glassStroke,
                modifier = Modifier
                    .padding(horizontal = 26.dp)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = GlassTheme.tokens.textPrimary,
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTheme.tokens.textSecondary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        GlassActionButton(
                            text = "Cancelar",
                            modifier = Modifier.weight(1f),
                            tint = GlassTheme.tokens.glassFillStrong,
                            textColor = GlassTheme.tokens.textPrimary,
                            onClick = onDismiss,
                        )
                        GlassActionButton(
                            text = "Borrar",
                            modifier = Modifier.weight(1f),
                            tint = Color(0xFFE06B6B),
                            onClick = onConfirm,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: TaskItem,
    modifier: Modifier = Modifier,
) {
    val alpha = if (task.isDone) 0.6f else 1f
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha),
        shape = RoundedCornerShape(26.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimeChip(time = task.time)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    task.labels.forEach { label ->
                        LabelChip(label = label)
                    }
                }
            }

            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium,
                color = GlassTheme.tokens.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (!task.details.isNullOrBlank()) {
                Text(
                    text = task.details,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassTheme.tokens.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.displayLarge,
            color = GlassTheme.tokens.textPrimary,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = GlassTheme.tokens.textSecondary,
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun LabelsScreen(
    labels: List<LabelTag>,
    errorMessage: String?,
    onCreateLabel: suspend (String, String) -> LabelTag?,
    onDeleteLabel: suspend (LabelTag) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = labelColorPalette()
    val usedColors = labels.map { it.colorHex.lowercase() }.toSet()
    val colorOptions = palette
        .filterNot { usedColors.contains(it.lowercase()) }
        .distinct()
        .ifEmpty { palette.distinct() }
    var newLabelName by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(colorOptions.first()) }
    var localError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(colorOptions) {
        if (!colorOptions.contains(selectedColor)) {
            selectedColor = colorOptions.first()
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 140.dp),
    ) {
        item {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Crear etiqueta",
                        style = MaterialTheme.typography.titleMedium,
                        color = GlassTheme.tokens.textPrimary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassTextField(
                            value = newLabelName,
                            onValueChange = { newLabelName = it },
                            placeholder = "Nombre",
                            modifier = Modifier.weight(1f),
                        )
                        GlassActionButton(
                            text = "Agregar",
                            enabled = newLabelName.isNotBlank(),
                            onClick = {
                                val name = newLabelName.trim()
                                if (name.isEmpty()) return@GlassActionButton
                                scope.launch {
                                    val created = onCreateLabel(name, selectedColor)
                                    if (created != null) {
                                        newLabelName = ""
                                        localError = null
                                    } else {
                                        localError = "No se pudo crear la etiqueta"
                                    }
                                }
                            },
                        )
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        colorOptions.forEach { hex ->
                            val color = colorFromHex(hex)
                            ColorSwatch(
                                color = color,
                                selected = hex == selectedColor,
                                onClick = { selectedColor = hex },
                            )
                        }
                    }

                    if (localError != null) {
                        Text(
                            text = localError ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFB53B3B),
                        )
                    }
                }
            }
        }

        if (errorMessage != null) {
            item {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB53B3B),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
            }
        }

        if (labels.isEmpty()) {
            item {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Text(
                        text = "Sin etiquetas creadas",
                        style = MaterialTheme.typography.bodyLarge,
                        color = GlassTheme.tokens.textSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                    )
                }
            }
        } else {
            items(labels, key = { it.id }) { label ->
                LabelRow(
                    label = label,
                    onDelete = {
                        scope.launch {
                            val success = onDeleteLabel(label)
                            localError = if (success) null else "No se pudo eliminar la etiqueta"
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun LabelRow(
    label: LabelTag,
    onDelete: () -> Unit,
) {
    val color = colorFromHex(label.colorHex)
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color),
                )
                Text(
                    text = label.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = GlassTheme.tokens.textPrimary,
                )
            }
            GlassSurface(
                shape = RoundedCornerShape(14.dp),
                tint = GlassTheme.tokens.glassFill,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDelete,
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Eliminar",
                        tint = GlassTheme.tokens.textSecondary,
                    )
                    Text(
                        text = "Eliminar",
                        style = MaterialTheme.typography.labelMedium,
                        color = GlassTheme.tokens.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    isDarkMode: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onDeleteAllNotes: () -> Unit,
    onDeleteAllLabels: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 140.dp),
    ) {
        item {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Modo de color",
                        style = MaterialTheme.typography.titleMedium,
                        color = GlassTheme.tokens.textPrimary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModeToggleButton(
                            text = "Claro",
                            selected = !isDarkMode,
                            onClick = { onThemeChange(false) },
                            modifier = Modifier.weight(1f),
                        )
                        ModeToggleButton(
                            text = "Oscuro",
                            selected = isDarkMode,
                            onClick = { onThemeChange(true) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        item {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Acciones",
                        style = MaterialTheme.typography.titleMedium,
                        color = GlassTheme.tokens.textPrimary,
                    )
                    Text(
                        text = "Estas acciones borran datos en la nube.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTheme.tokens.textSecondary,
                    )
                    GlassActionButton(
                        text = "Borrar todas las notas",
                        tint = Color(0xFFE06B6B),
                        onClick = onDeleteAllNotes,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    GlassActionButton(
                        text = "Borrar todas las etiquetas",
                        tint = Color(0xFFE06B6B),
                        onClick = onDeleteAllLabels,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeToggleButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (selected) GlassTheme.tokens.accent else GlassTheme.tokens.glassFillStrong
    val textColor = if (selected) Color.White else GlassTheme.tokens.textPrimary
    GlassSurface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(16.dp),
        tint = tint,
        strokeColor = if (selected) tint.copy(alpha = 0.6f) else GlassTheme.tokens.glassStroke,
    ) {
        Box(
            modifier = Modifier.padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BottomBar(
    modifier: Modifier = Modifier,
    selectedTab: HomeTab,
    onSelect: (HomeTab) -> Unit,
) {
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(26.dp),
        tint = GlassTheme.tokens.glassFillStrong,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomBarItem(
                icon = Icons.Rounded.CalendarToday,
                label = "Agenda",
                selected = selectedTab == HomeTab.AGENDA,
                onClick = { onSelect(HomeTab.AGENDA) },
            )
            BottomBarItem(
                icon = Icons.Rounded.Label,
                label = "Etiquetas",
                selected = selectedTab == HomeTab.LABELS,
                onClick = { onSelect(HomeTab.LABELS) },
            )
            BottomBarItem(
                icon = Icons.Rounded.Settings,
                label = "Ajustes",
                selected = selectedTab == HomeTab.SETTINGS,
                onClick = { onSelect(HomeTab.SETTINGS) },
            )
        }
    }
}

@Composable
private fun BottomBarItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit = {},
) {
    val color = if (selected) GlassTheme.tokens.textPrimary else GlassTheme.tokens.textSecondary
    Column(
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
private fun NewTaskSheet(
    visible: Boolean,
    date: LocalDate,
    labels: List<LabelTag>,
    onCreateLabel: suspend (String, String) -> LabelTag?,
    onDismiss: () -> Unit,
    onSave: suspend (LocalDate, TaskDraft) -> SaveResult,
) {
    val timeZone = remember { TimeZone.currentSystemDefault() }
    val today = remember { Clock.System.todayIn(timeZone) }
    val palette = labelColorPalette()
    val usedColors = labels.map { it.colorHex.lowercase() }.toSet()
    val colorOptions = palette
        .filterNot { usedColors.contains(it.lowercase()) }
        .distinct()
        .ifEmpty { palette.distinct() }
    var title by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var selectedTime by remember { mutableStateOf<LocalTime?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var selectedDate by remember { mutableStateOf(date) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val selectedLabelIds = remember { mutableStateListOf<String>() }
    var newLabelName by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(colorOptions.first()) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(visible) {
        if (visible) {
            title = ""
            details = ""
            selectedTime = null
            errorText = null
            selectedDate = if (date < today) today else date
            showDatePicker = false
            showTimePicker = false
            selectedLabelIds.clear()
            newLabelName = ""
            selectedColor = colorOptions.first()
        }
    }

    LaunchedEffect(colorOptions) {
        if (!colorOptions.contains(selectedColor)) {
            selectedColor = colorOptions.first()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + slideInVertically(tween(200)) { it / 2 },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(180)) { it / 2 },
    ) {
        val isPastSelected = selectedDate < today
        val sheetBlur = if (showTimePicker) 14.dp else 0.dp
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(sheetBlur),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x660B1117))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss,
                        ),
                )

                GlassSurface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 120.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    tint = GlassTheme.tokens.glassFillStrong,
                ) {
                    Column(
                        modifier = Modifier
                            .padding(18.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Nueva tarea",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = GlassTheme.tokens.textPrimary,
                                )
                                GlassSurface(
                                    shape = RoundedCornerShape(14.dp),
                                    tint = GlassTheme.tokens.glassFill,
                                    shadowElevation = 0.dp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { showDatePicker = true },
                                        ),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CalendarToday,
                                            contentDescription = "Seleccionar fecha",
                                            tint = GlassTheme.tokens.textPrimary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                            text = formatShortDateWithYear(selectedDate),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = GlassTheme.tokens.textSecondary,
                                        )
                                    }
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                GlassActionButton(
                                    text = "Cancelar",
                                    tint = GlassTheme.tokens.glassFillStrong,
                                    textColor = GlassTheme.tokens.textPrimary,
                                    onClick = onDismiss,
                                )
                            GlassActionButton(
                                text = "Guardar",
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

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Hora (opcional)",
                                style = MaterialTheme.typography.labelMedium,
                                color = GlassTheme.tokens.textSecondary,
                            )
                            GlassSurface(
                                shape = RoundedCornerShape(16.dp),
                                tint = GlassTheme.tokens.glassFill,
                                shadowElevation = 0.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { showTimePicker = true },
                                    ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Schedule,
                                            contentDescription = "Seleccionar hora",
                                            tint = GlassTheme.tokens.textPrimary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Text(
                                            text = selectedTime?.let { formatTime(it) } ?: "Sin hora",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (selectedTime == null) {
                                                GlassTheme.tokens.textSecondary
                                            } else {
                                                GlassTheme.tokens.textPrimary
                                            },
                                        )
                                    }
                                    Text(
                                        text = "Cambiar",
                                        style = MaterialTheme.typography.labelMedium,
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
                                text = errorText ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFB53B3B),
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Etiquetas",
                                style = MaterialTheme.typography.labelMedium,
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
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Crear etiqueta",
                                style = MaterialTheme.typography.labelMedium,
                                color = GlassTheme.tokens.textSecondary,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                GlassTextField(
                                    value = newLabelName,
                                    onValueChange = { newLabelName = it },
                                    placeholder = "Nombre",
                                    modifier = Modifier.weight(1f),
                                )
                                GlassActionButton(
                                    text = "Agregar",
                                    enabled = newLabelName.isNotBlank(),
                                    onClick = {
                                        val labelName = newLabelName.trim()
                                        if (labelName.isEmpty()) return@GlassActionButton
                                        scope.launch {
                                            val created = onCreateLabel(labelName, selectedColor)
                                            if (created != null) {
                                                selectedLabelIds.add(created.id)
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
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                colorOptions.forEach { hex ->
                                    val color = colorFromHex(hex)
                                    ColorSwatch(
                                        color = color,
                                        selected = hex == selectedColor,
                                        onClick = { selectedColor = hex },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            DatePickerOverlay(
                visible = showDatePicker,
                selectedDate = selectedDate,
                onSelect = {
                    selectedDate = it
                    showDatePicker = false
                },
                onDismiss = { showDatePicker = false },
            )

            TimePickerOverlay(
                visible = showTimePicker,
                initialTime = selectedTime,
                onConfirm = { time ->
                    selectedTime = time
                    showTimePicker = false
                },
                onDismiss = { showTimePicker = false },
            )
        }
    }
}

@Composable
private fun DatePickerOverlay(
    visible: Boolean,
    selectedDate: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val timeZone = remember { TimeZone.currentSystemDefault() }
    val today = remember { Clock.System.todayIn(timeZone) }
    var visibleMonth by remember {
        mutableStateOf(LocalDate(selectedDate.year, selectedDate.month, 1))
    }

    LaunchedEffect(visible, selectedDate) {
        if (visible) {
            visibleMonth = LocalDate(selectedDate.year, selectedDate.month, 1)
        }
    }

    val firstDay = LocalDate(visibleMonth.year, visibleMonth.month, 1)
    val monthDays = daysInMonth(visibleMonth.year, visibleMonth.month)
    val startOffset = firstDay.dayOfWeek.ordinal
    val totalCells = ((startOffset + monthDays + 6) / 7) * 7
    val dayCells = (0 until totalCells).map { index ->
        val dayNumber = index - startOffset + 1
        if (dayNumber in 1..monthDays) {
            LocalDate(visibleMonth.year, visibleMonth.month, dayNumber)
        } else {
            null
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + scaleIn(tween(200)) + slideInVertically(tween(180)) { it / 6 },
        exit = fadeOut(tween(160)) + scaleOut(tween(180)) + slideOutVertically(tween(160)) { it / 6 },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x660B1117))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )

            GlassSurface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 22.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                tint = GlassTheme.tokens.glassFillStrong,
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
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
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        weekDayLabels().forEach { label ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 2.dp),
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

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        dayCells.chunked(7).forEach { week ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                week.forEach { day ->
                                    if (day == null) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f),
                                        )
                                    } else {
                                        val isEnabled = day >= today
                                        DatePickerDayCell(
                                            date = day,
                                            selectedDate = selectedDate,
                                            today = today,
                                            enabled = isEnabled,
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
}

@Composable
private fun TimePickerOverlay(
    visible: Boolean,
    initialTime: LocalTime?,
    onConfirm: (LocalTime?) -> Unit,
    onDismiss: () -> Unit,
) {
    val timeZone = remember { TimeZone.currentSystemDefault() }
    val hours = remember { (0..23).map { it.toString().padStart(2, '0') } }
    val minutes = remember { (0..59).map { it.toString().padStart(2, '0') } }
    var hourIndex by remember { mutableStateOf(0) }
    var minuteIndex by remember { mutableStateOf(0) }

    LaunchedEffect(visible, initialTime) {
        if (visible) {
            val base = initialTime ?: Clock.System.now().toLocalDateTime(timeZone).time
            hourIndex = base.hour
            minuteIndex = base.minute
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + slideInVertically(tween(200)) { it / 3 },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(180)) { it / 3 },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x660B1117))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )

            GlassSurface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 120.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                tint = GlassTheme.tokens.glassFillStrong,
                shadowElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Cancelar",
                            style = MaterialTheme.typography.labelMedium,
                            color = GlassTheme.tokens.textSecondary,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onDismiss,
                            ),
                        )
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
                        Text(
                            text = "Listo",
                            style = MaterialTheme.typography.labelMedium,
                            color = GlassTheme.tokens.textPrimary,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onConfirm(LocalTime(hourIndex, minuteIndex)) },
                            ),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WheelPicker(
                            items = hours,
                            selectedIndex = hourIndex,
                            onIndexSelected = { hourIndex = it },
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = ":",
                            style = MaterialTheme.typography.titleLarge,
                            color = GlassTheme.tokens.textSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        )
                        WheelPicker(
                            items = minutes,
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
}

@Composable
private fun WheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onIndexSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemHeight = 38.dp
    val visibleCount = 5
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val itemHeightPx = with(LocalDensity.current) { itemHeight.roundToPx() }
    val centerIndex by remember {
        derivedStateOf {
            val index = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            val adjust = if (offset > itemHeightPx / 2) 1 else 0
            (index + adjust).coerceIn(0, items.lastIndex)
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
        modifier = modifier.height(itemHeight * visibleCount),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = itemHeight * (visibleCount / 2)),
        ) {
            items(items.size) { index ->
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
                        text = items[index],
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = if (distance == 0) 20.sp else 16.sp,
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
                .clip(RoundedCornerShape(14.dp))
                .background(GlassTheme.tokens.glassFillStrong.copy(alpha = 0.35f))
                .border(1.dp, GlassTheme.tokens.glassStroke, RoundedCornerShape(14.dp)),
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
        shape = RoundedCornerShape(8.dp),
        tint = tint,
        strokeColor = stroke,
        shadowElevation = 0.dp,
        modifier = modifier
            .clickable(
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
                text = date.day.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
            )
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
        shape = RoundedCornerShape(12.dp),
        tint = tint,
        strokeColor = stroke,
        shadowElevation = 0.dp,
        modifier = modifier
            .clickable(
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
                        .padding(6.dp),
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
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = date.day.toString(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        lineHeight = 18.sp,
                    ),
                    color = dayColor,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = noteCount.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                    ),
                    color = countColor,
                )
            }
        }
    }
}

@Composable
private fun CalendarOverlay(
    visible: Boolean,
    selectedDate: LocalDate,
    today: LocalDate,
    tasksByDate: Map<LocalDate, List<TaskItem>>,
    onSelectDate: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var visibleMonth by remember {
        mutableStateOf(LocalDate(selectedDate.year, selectedDate.month, 1))
    }
    val swipeThreshold = 72.dp
    val swipeEdgeGuard = 24.dp
    var dragTotal by remember { mutableStateOf(0f) }
    var allowSwipe by remember { mutableStateOf(false) }

    LaunchedEffect(visible, selectedDate) {
        if (visible) {
            visibleMonth = LocalDate(selectedDate.year, selectedDate.month, 1)
        }
    }

    val firstDay = LocalDate(visibleMonth.year, visibleMonth.month, 1)
    val monthDays = daysInMonth(visibleMonth.year, visibleMonth.month)
    val startOffset = firstDay.dayOfWeek.ordinal
    val totalCells = ((startOffset + monthDays + 6) / 7) * 7
    val dayCells = (0 until totalCells).map { index ->
        val dayNumber = index - startOffset + 1
        if (dayNumber in 1..monthDays) {
            LocalDate(visibleMonth.year, visibleMonth.month, dayNumber)
        } else {
            null
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 6 },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(180)) { -it / 6 },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x660B1117))
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
                        .padding(horizontal = 18.dp, vertical = 24.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    tint = GlassTheme.tokens.glassFillStrong,
                    shadowElevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Hoy",
                                style = MaterialTheme.typography.labelMedium,
                                color = GlassTheme.tokens.textSecondary,
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onSelectDate(today) },
                                ),
                            )
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
                            Text(
                                text = "Listo",
                                style = MaterialTheme.typography.labelMedium,
                                color = GlassTheme.tokens.textPrimary,
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onDismiss,
                                ),
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ChevronLeft,
                                    contentDescription = "Mes anterior",
                                    tint = GlassTheme.tokens.textSecondary,
                                    modifier = Modifier
                                        .size(22.dp)
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
                                        .size(22.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { visibleMonth = visibleMonth.plus(1, DateTimeUnit.MONTH) },
                                        ),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val daySpacing = 4.dp
                        val gridInset = 12.dp

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
                                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                                        color = GlassTheme.tokens.textSecondary,
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

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
                                                if (dragTotal > swipeThreshold.toPx()) {
                                                    visibleMonth = visibleMonth.plus(-1, DateTimeUnit.MONTH)
                                                } else if (dragTotal < -swipeThreshold.toPx()) {
                                                    visibleMonth = visibleMonth.plus(1, DateTimeUnit.MONTH)
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
                                val cellSize = maxCellWidth

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
                                                .height(cellSize),
                                            horizontalArrangement = Arrangement.spacedBy(daySpacing),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            week.forEach { day ->
                                                if (day == null) {
                                                    Box(modifier = Modifier.size(cellSize))
                                                } else {
                                                    val noteCount = tasksByDate[day]?.size ?: 0
                                                    CalendarDayCell(
                                                        date = day,
                                                        selectedDate = selectedDate,
                                                        today = today,
                                                        noteCount = noteCount,
                                                        onSelect = onSelectDate,
                                                        modifier = Modifier.size(cellSize),
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
}

@Composable
private fun LabelSelectableChip(
    label: LabelTag,
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val color = colorFromHex(label.colorHex)
    val tint = if (selected) color.copy(alpha = 0.18f) else GlassTheme.tokens.glassFill
    val stroke = if (selected) color.copy(alpha = 0.6f) else GlassTheme.tokens.glassStroke

    GlassSurface(
        shape = RoundedCornerShape(14.dp),
        tint = tint,
        strokeColor = stroke,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { onToggle(!selected) },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = 2.dp,
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

@Composable
private fun TimeChip(time: LocalTime?) {
    val text = time?.let { formatTime(it) } ?: "Sin hora"
    val tint = if (time == null) GlassTheme.tokens.glassFill else GlassTheme.tokens.glassFillStrong
    GlassSurface(
        shape = RoundedCornerShape(14.dp),
        tint = tint,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = GlassTheme.tokens.textPrimary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun LabelChip(label: LabelTag) {
    val color = colorFromHex(label.colorHex)
    GlassSurface(
        shape = RoundedCornerShape(14.dp),
        tint = GlassTheme.tokens.glassFill,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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

private fun formatTime(time: LocalTime): String {
    val hour = time.hour.toString().padStart(2, '0')
    val minute = time.minute.toString().padStart(2, '0')
    return "$hour:$minute"
}

private fun orderTasks(tasks: List<TaskItem>): List<TaskItem> {
    return tasks.sortedWith(
        compareBy<TaskItem> { it.time == null }
            .thenBy { it.time?.hour ?: 24 }
            .thenBy { it.time?.minute ?: 0 },
    )
}

private fun filterTasks(tasks: List<TaskItem>, query: String): List<TaskItem> {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return tasks
    val needle = trimmed.lowercase()
    return tasks.filter { task ->
        task.title.lowercase().contains(needle) ||
            (task.details?.lowercase()?.contains(needle) == true) ||
            task.labels.any { it.name.lowercase().contains(needle) }
    }
}

private fun removeLabelFromTasks(
    tasksByDate: MutableMap<LocalDate, List<TaskItem>>,
    labelId: String,
) {
    val dates = tasksByDate.keys.toList()
    dates.forEach { date ->
        val updated = tasksByDate[date]?.map { task ->
            task.copy(labels = task.labels.filterNot { it.id == labelId })
        } ?: emptyList()
        tasksByDate[date] = updated
    }
}

private fun clearLabelsFromTasks(
    tasksByDate: MutableMap<LocalDate, List<TaskItem>>,
) {
    val dates = tasksByDate.keys.toList()
    dates.forEach { date ->
        val updated = tasksByDate[date]?.map { task ->
            task.copy(labels = emptyList())
        } ?: emptyList()
        tasksByDate[date] = updated
    }
}

private fun formatFullDate(date: LocalDate): String {
    return "${dayName(date.dayOfWeek)}, ${date.day} de ${monthName(date.month)}"
}

private fun formatDayTitle(date: LocalDate): String = dayName(date.dayOfWeek)

private fun formatShortDate(date: LocalDate): String {
    return "${date.day} ${monthName(date.month, short = true)}"
}

private fun formatShortDateWithYear(date: LocalDate): String {
    return "${date.day} ${monthName(date.month, short = true)} ${date.year}"
}

private fun weekDayLabels(): List<String> {
    return listOf("L", "M", "X", "J", "V", "S", "D")
}

private fun daysInMonth(year: Int, month: Month): Int = when (month) {
    Month.JANUARY -> 31
    Month.FEBRUARY -> if (isLeapYear(year)) 29 else 28
    Month.MARCH -> 31
    Month.APRIL -> 30
    Month.MAY -> 31
    Month.JUNE -> 30
    Month.JULY -> 31
    Month.AUGUST -> 31
    Month.SEPTEMBER -> 30
    Month.OCTOBER -> 31
    Month.NOVEMBER -> 30
    Month.DECEMBER -> 31
}

private fun isLeapYear(year: Int): Boolean {
    return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
}

private fun dayName(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "Lunes"
    DayOfWeek.TUESDAY -> "Martes"
    DayOfWeek.WEDNESDAY -> "Miercoles"
    DayOfWeek.THURSDAY -> "Jueves"
    DayOfWeek.FRIDAY -> "Viernes"
    DayOfWeek.SATURDAY -> "Sabado"
    DayOfWeek.SUNDAY -> "Domingo"
}

private fun monthName(month: Month, short: Boolean = false): String = when (month) {
    Month.JANUARY -> if (short) "ene" else "enero"
    Month.FEBRUARY -> if (short) "feb" else "febrero"
    Month.MARCH -> if (short) "mar" else "marzo"
    Month.APRIL -> if (short) "abr" else "abril"
    Month.MAY -> if (short) "may" else "mayo"
    Month.JUNE -> if (short) "jun" else "junio"
    Month.JULY -> if (short) "jul" else "julio"
    Month.AUGUST -> if (short) "ago" else "agosto"
    Month.SEPTEMBER -> if (short) "sep" else "septiembre"
    Month.OCTOBER -> if (short) "oct" else "octubre"
    Month.NOVEMBER -> if (short) "nov" else "noviembre"
    Month.DECEMBER -> if (short) "dic" else "diciembre"
}

private fun labelColorPalette(): List<String> {
    return listOf(
        "#3DA9FC",
        "#FF7A59",
        "#39D98A",
        "#A17CFF",
        "#FFC857",
        "#5FD3BC",
        "#FF5D8F",
        "#6C5CE7",
        "#00B8A9",
        "#FDCB6E",
        "#74B9FF",
        "#55EFC4",
        "#FF6B6B",
        "#B388FF",
        "#F78FB3",
        "#4ECDC4",
    )
}

private fun colorFromHex(hex: String): Color {
    val cleaned = hex.removePrefix("#").trim()
    val value = cleaned.toLongOrNull(16) ?: return Color(0xFF9AA4B2)
    val argb = when (cleaned.length) {
        6 -> 0xFF000000L or value
        8 -> value
        else -> return Color(0xFF9AA4B2)
    }
    return Color(argb)
}
