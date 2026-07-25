# Vista de calendario/mes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Agregar un tab "Calendario" de primer nivel en AgendNote con un grid mensual de tareas por día (heatmap), con datos siempre precisos gracias a un fetch por rango de fechas, reemplazando el overlay modal actual que solo mostraba días ya visitados individualmente.

**Architecture:** Todo vive dentro de `feature/agenda` — el calendario reutiliza `AgendaViewModel`/`AgendaController` y el mismo caché `tasksByDate`, evitando una segunda fuente de verdad. Se agrega un método de repositorio para traer tareas de un rango de fechas de una sola vez, un método de ViewModel que carga un mes completo (con caché por mes ya visitado), y una pantalla nueva que reutiliza el grid visual ya existente (extraído del overlay actual).

**Tech Stack:** Kotlin Multiplatform (Compose Multiplatform), kotlinx-datetime, Ktor client, Jetpack Navigation Compose.

## Global Constraints

- El endpoint de backend `GET /api-tasks?from=X&to=Y` ya existe y no se toca — solo se agrega el cliente Kotlin que lo consume.
- El merge de un fetch por rango en `tasksByDate` siempre sobrescribe las entradas de los días devueltos (mismo criterio que el fetch de un solo día ya existente).
- Tocar un día en el calendario navega al tab Agenda mostrando ese día completo — no hay lista de tareas inline en el tab Calendario.
- El overlay modal `CalendarOverlay` y el botón que lo abría se eliminan por completo — no quedan dos calendarios en paralelo.
- Orden de tabs en la barra inferior: Agenda → Calendario → Etiquetas → Ajustes.
- Sin vista semanal, sin prefetch de meses adyacentes, sin filtro de tareas completadas en el heatmap (fuera de alcance según el spec).

---

### Task 1: Fetch de tareas por rango de fechas (cliente de red + repositorio)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/core/network/AgendaApiClient.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/domain/AgendaTaskRepository.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/data/SupabaseAgendaTaskRepository.kt`
- Modify: `composeApp/src/commonTest/kotlin/com/franciscor/agendnote/AgendaViewModelTest.kt`
- Modify: `composeApp/src/commonTest/kotlin/com/franciscor/agendnote/SeriesMaterializerTest.kt`

**Interfaces:**
- Produces: `AgendaApiClient.fetchTasksInRange(from: String, to: String): List<TaskDto>`; `AgendaTaskRepository.fetchTasksInRange(from: LocalDate, to: LocalDate): Map<LocalDate, List<TaskItem>>` (mapa disperso: solo los días que tuvieron al menos una tarea son claves). Task 2 consume este método de repositorio.

- [ ] **Step 1: Agregar `fetchTasksInRange` a `AgendaApiClient.kt`**

Agregar al final de la clase `AgendaApiClient`, justo antes de la última `}` que cierra la clase (después de `deleteTaskSeries`):

```kotlin

    suspend fun fetchTasksInRange(from: String, to: String): List<TaskDto> {
        val response: TasksResponse = client
            .get("$normalizedBaseUrl/api-tasks") {
                withAuth(this)
                parameter("from", from)
                parameter("to", to)
            }
            .body()
        return response.tasks
    }
```

- [ ] **Step 2: Agregar el método a la interfaz `AgendaTaskRepository.kt`**

Reemplazar:

```kotlin
interface AgendaTaskRepository {
    suspend fun fetchTasks(date: LocalDate): List<TaskItem>

    suspend fun createTask(date: LocalDate, draft: TaskDraft): TaskItem

    suspend fun updateTaskDone(id: String, isDone: Boolean): TaskItem

    suspend fun deleteTask(id: String): Boolean

    suspend fun deleteAllTasks(): Boolean
}
```

por:

```kotlin
interface AgendaTaskRepository {
    suspend fun fetchTasks(date: LocalDate): List<TaskItem>

    suspend fun fetchTasksInRange(from: LocalDate, to: LocalDate): Map<LocalDate, List<TaskItem>>

    suspend fun createTask(date: LocalDate, draft: TaskDraft): TaskItem

    suspend fun updateTaskDone(id: String, isDone: Boolean): TaskItem

    suspend fun deleteTask(id: String): Boolean

    suspend fun deleteAllTasks(): Boolean
}
```

- [ ] **Step 3: Implementar el método en `SupabaseAgendaTaskRepository.kt`**

Reemplazar:

```kotlin
    override suspend fun fetchTasks(date: LocalDate): List<TaskItem> {
        return api.fetchTasks(date.toString()).map { it.toTaskItem(timeZone) }
    }
```

por:

```kotlin
    override suspend fun fetchTasks(date: LocalDate): List<TaskItem> {
        return api.fetchTasks(date.toString()).map { it.toTaskItem(timeZone) }
    }

    override suspend fun fetchTasksInRange(from: LocalDate, to: LocalDate): Map<LocalDate, List<TaskItem>> {
        return api.fetchTasksInRange(from.toString(), to.toString())
            .groupBy({ LocalDate.parse(it.day) }, { it.toTaskItem(timeZone) })
    }
```

- [ ] **Step 4: Compilar para verificar**

Run (desde `C:\Users\franr\AgendNote`): `.\gradlew.bat :composeApp:compileCommonMainKotlinMetadata --console=plain`
Expected: FAIL — los dos fakes de `AgendaTaskRepository` en los tests todavía no implementan `fetchTasksInRange`, así que `commonTest` no compila. Esto es esperado en este punto; se corrige en el siguiente paso.

- [ ] **Step 5: Agregar `fetchTasksInRange` al fake de `AgendaViewModelTest.kt`**

Reemplazar:

```kotlin
private class FakeAgendaTaskRepository(
    private val fetchTasksHandler: suspend (LocalDate) -> List<TaskItem> = { emptyList() },
) : AgendaTaskRepository {
    override suspend fun fetchTasks(date: LocalDate): List<TaskItem> = fetchTasksHandler(date)
```

por:

```kotlin
private class FakeAgendaTaskRepository(
    private val fetchTasksHandler: suspend (LocalDate) -> List<TaskItem> = { emptyList() },
    private val fetchTasksInRangeHandler: suspend (LocalDate, LocalDate) -> Map<LocalDate, List<TaskItem>> =
        { _, _ -> emptyMap() },
) : AgendaTaskRepository {
    override suspend fun fetchTasks(date: LocalDate): List<TaskItem> = fetchTasksHandler(date)

    override suspend fun fetchTasksInRange(from: LocalDate, to: LocalDate): Map<LocalDate, List<TaskItem>> =
        fetchTasksInRangeHandler(from, to)
```

- [ ] **Step 6: Agregar `fetchTasksInRange` al fake de `SeriesMaterializerTest.kt`**

Reemplazar:

```kotlin
private class FakeAgendaTaskRepositoryForMaterializer : AgendaTaskRepository {
    val createdDrafts = mutableListOf<Pair<LocalDate, TaskDraft>>()
    var failAfter: Int = Int.MAX_VALUE

    override suspend fun fetchTasks(date: LocalDate): List<TaskItem> = emptyList()
```

por:

```kotlin
private class FakeAgendaTaskRepositoryForMaterializer : AgendaTaskRepository {
    val createdDrafts = mutableListOf<Pair<LocalDate, TaskDraft>>()
    var failAfter: Int = Int.MAX_VALUE

    override suspend fun fetchTasks(date: LocalDate): List<TaskItem> = emptyList()

    override suspend fun fetchTasksInRange(from: LocalDate, to: LocalDate): Map<LocalDate, List<TaskItem>> {
        error("not used in this test")
    }
```

- [ ] **Step 7: Compilar y correr el test suite completo para verificar**

Run: `.\gradlew.bat :composeApp:compileCommonMainKotlinMetadata --console=plain`
Expected: `BUILD SUCCESSFUL`.

Run: `.\gradlew.bat :composeApp:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`, sin regresiones (los tests existentes de `AgendaViewModelTest`/`SeriesMaterializerTest` siguen pasando igual que antes).

- [ ] **Step 8: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/franciscor/agendnote/core/network/AgendaApiClient.kt composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/domain/AgendaTaskRepository.kt composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/data/SupabaseAgendaTaskRepository.kt composeApp/src/commonTest/kotlin/com/franciscor/agendnote/AgendaViewModelTest.kt composeApp/src/commonTest/kotlin/com/franciscor/agendnote/SeriesMaterializerTest.kt
git commit -m "Agregar fetch de tareas por rango de fechas al repositorio de agenda"
```

---

### Task 2: Carga de un mes completo en AgendaViewModel (TDD)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/presentation/model/AgendaUiState.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/presentation/model/AgendaAction.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/presentation/viewmodel/AgendaViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/presentation/controller/AgendaController.kt`
- Test: `composeApp/src/commonTest/kotlin/com/franciscor/agendnote/AgendaViewModelTest.kt`

**Interfaces:**
- Consumes: `AgendaTaskRepository.fetchTasksInRange(from, to): Map<LocalDate, List<TaskItem>>` de la Task 1.
- Produces: `AgendaUiState.visibleMonth: LocalDate`, `AgendaUiState.isMonthLoading: Boolean`, `AgendaUiState.monthErrorMessage: String?`; `AgendaAction.LoadMonth(month: LocalDate)`; `AgendaViewModel.loadMonth(month: LocalDate)` (suspend) y `loadMonthAsync(month: LocalDate)` (fire-and-forget); `AgendaController.handle`/`handleAsync` despachan `LoadMonth`. La Task 4 (pantalla nueva) consume `AgendaAction.LoadMonth` vía `controller.handleAsync` y lee `uiState.visibleMonth`/`isMonthLoading`/`monthErrorMessage`.

- [ ] **Step 1: Escribir los tests que fallan**

Agregar a `composeApp/src/commonTest/kotlin/com/franciscor/agendnote/AgendaViewModelTest.kt`, justo antes del método privado `task(...)` (antes de la línea `private fun task(`):

```kotlin
    @Test
    fun `loadMonth populates tasksByDate for every day in the month`() = runTest {
        val month = LocalDate(2026, 3, 1)
        val taskOnDay5 = task("t-1", "Reunion")
        val repository = FakeAgendaTaskRepository(
            fetchTasksInRangeHandler = { from, to ->
                assertEquals(LocalDate(2026, 3, 1), from)
                assertEquals(LocalDate(2026, 3, 31), to)
                mapOf(LocalDate(2026, 3, 5) to listOf(taskOnDay5))
            },
        )
        val viewModel = AgendaViewModel(repository, timeZone = timeZone, initialDate = baseDate)

        viewModel.loadMonth(month)

        assertEquals(month, viewModel.uiState.visibleMonth)
        assertEquals(listOf(taskOnDay5), viewModel.uiState.tasksByDate[LocalDate(2026, 3, 5)])
        assertEquals(emptyList(), viewModel.uiState.tasksByDate[LocalDate(2026, 3, 1)])
        assertEquals(emptyList(), viewModel.uiState.tasksByDate[LocalDate(2026, 3, 31)])
        assertNull(viewModel.uiState.monthErrorMessage)
        assertFalse(viewModel.uiState.isMonthLoading)
    }

    @Test
    fun `loadMonth does not refetch an already loaded month`() = runTest {
        var callCount = 0
        val repository = FakeAgendaTaskRepository(
            fetchTasksInRangeHandler = { _, _ ->
                callCount += 1
                emptyMap()
            },
        )
        val viewModel = AgendaViewModel(repository, timeZone = timeZone, initialDate = baseDate)

        viewModel.loadMonth(LocalDate(2026, 3, 1))
        viewModel.loadMonth(LocalDate(2026, 3, 1))
        viewModel.loadMonth(LocalDate(2026, 3, 20))

        assertEquals(1, callCount)
    }

    @Test
    fun `loadMonth failure sets monthErrorMessage without touching cached days`() = runTest {
        val cachedTask = task("t-1", "Ya cacheada")
        val repository = FakeAgendaTaskRepository(
            fetchTasksHandler = { listOf(cachedTask) },
            fetchTasksInRangeHandler = { _, _ -> error("boom") },
        )
        val viewModel = AgendaViewModel(repository, timeZone = timeZone, initialDate = baseDate)
        viewModel.loadTasksForDate(baseDate)

        viewModel.loadMonth(LocalDate(2026, 4, 1))

        assertEquals("No se pudieron cargar las tareas del mes", viewModel.uiState.monthErrorMessage)
        assertEquals(listOf(cachedTask), viewModel.uiState.tasksByDate[baseDate])
    }

    @Test
    fun `loadMonth without remote repository exposes config error`() = runTest {
        val viewModel = AgendaViewModel(
            repository = null,
            timeZone = timeZone,
            remoteUnavailableMessage = "Falta APP_SECRET",
            initialDate = baseDate,
        )

        viewModel.loadMonth(LocalDate(2026, 3, 1))

        assertEquals("Falta APP_SECRET", viewModel.uiState.monthErrorMessage)
    }

```

- [ ] **Step 2: Ejecutar los tests para verificar que fallan**

Run: `.\gradlew.bat :composeApp:testDebugUnitTest --tests "com.franciscor.agendnote.AgendaViewModelTest" --console=plain`
Expected: FAIL con errores de compilación — `loadMonth`, `AgendaUiState.visibleMonth`/`isMonthLoading`/`monthErrorMessage` no existen todavía.

- [ ] **Step 3: Agregar los campos nuevos a `AgendaUiState.kt`**

Reemplazar:

```kotlin
data class AgendaUiState(
    val selectedDate: LocalDate,
    val tasksByDate: Map<LocalDate, List<TaskItem>> = emptyMap(),
    val loadingByDate: Map<LocalDate, Boolean> = emptyMap(),
    val errorByDate: Map<LocalDate, String?> = emptyMap(),
    val isRemoteAvailable: Boolean = true,
)
```

por:

```kotlin
data class AgendaUiState(
    val selectedDate: LocalDate,
    val visibleMonth: LocalDate,
    val tasksByDate: Map<LocalDate, List<TaskItem>> = emptyMap(),
    val loadingByDate: Map<LocalDate, Boolean> = emptyMap(),
    val errorByDate: Map<LocalDate, String?> = emptyMap(),
    val isRemoteAvailable: Boolean = true,
    val isMonthLoading: Boolean = false,
    val monthErrorMessage: String? = null,
)
```

- [ ] **Step 4: Agregar la acción `LoadMonth` en `AgendaAction.kt`**

Reemplazar:

```kotlin
sealed interface AgendaAction {
    data class SelectDate(val date: LocalDate) : AgendaAction

    data class MoveDay(val delta: Int) : AgendaAction

    data object RefreshSelectedDate : AgendaAction
}
```

por:

```kotlin
sealed interface AgendaAction {
    data class SelectDate(val date: LocalDate) : AgendaAction

    data class MoveDay(val delta: Int) : AgendaAction

    data object RefreshSelectedDate : AgendaAction

    data class LoadMonth(val month: LocalDate) : AgendaAction
}
```

- [ ] **Step 5: Implementar `loadMonth` en `AgendaViewModel.kt`**

Reemplazar la construcción inicial de `uiState`:

```kotlin
    var uiState by mutableStateOf(
        AgendaUiState(
            selectedDate = initialDate,
            isRemoteAvailable = hasRemoteAccess,
        ),
    )
        private set
```

por:

```kotlin
    var uiState by mutableStateOf(
        AgendaUiState(
            selectedDate = initialDate,
            visibleMonth = LocalDate(initialDate.year, initialDate.monthNumber, 1),
            isRemoteAvailable = hasRemoteAccess,
        ),
    )
        private set

    private val loadedMonths = mutableSetOf<LocalDate>()
```

Agregar el import de `minus` (kotlinx-datetime), junto al `plus` ya existente:

```kotlin
import kotlinx.datetime.minus
```

Agregar el método `loadMonth` justo después de `loadTasksForDate` (después del cierre `}` de esa función, antes de `suspend fun saveTask`):

```kotlin
    suspend fun loadMonth(month: LocalDate) {
        val monthStart = LocalDate(month.year, month.monthNumber, 1)
        uiState = uiState.copy(visibleMonth = monthStart)

        if (loadedMonths.contains(monthStart)) return

        val repository = repository ?: run {
            setMonthError(remoteErrorMessage)
            return
        }

        setMonthLoading(true)
        setMonthError(null)

        val monthEnd = monthStart.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
        val result = runCatching { repository.fetchTasksInRange(monthStart, monthEnd) }

        result
            .onSuccess { fetched ->
                val allDays = generateSequence(monthStart) { it.plus(1, DateTimeUnit.DAY) }
                    .takeWhile { it <= monthEnd }
                val fullMonth = allDays.associateWith { day -> fetched[day].orEmpty() }
                setTasksForMonth(fullMonth)
                loadedMonths.add(monthStart)
            }
            .onFailure {
                setMonthError("No se pudieron cargar las tareas del mes")
            }

        setMonthLoading(false)
    }

    private fun setTasksForMonth(tasksByDate: Map<LocalDate, List<TaskItem>>) {
        // No pasa por setTasks() a propósito: setTasks() re-programa notificaciones locales por
        // cada tarea con hora, y llamarlo una vez por día del mes (hasta 31 veces) reprogramaría
        // notificaciones ya programadas sin necesidad cada vez que se abre el calendario.
        val ordered = tasksByDate.mapValues { (_, tasks) -> orderTasks(tasks) }
        uiState = uiState.copy(tasksByDate = uiState.tasksByDate + ordered)
    }

    private fun setMonthLoading(isLoading: Boolean) {
        uiState = uiState.copy(isMonthLoading = isLoading)
    }

    private fun setMonthError(message: String?) {
        uiState = uiState.copy(monthErrorMessage = message)
    }
```

Agregar el wrapper fire-and-forget junto a los demás (después de `refreshSelectedDateAsync`, antes de `saveTaskAsync`):

```kotlin
    fun loadMonthAsync(month: LocalDate) {
        viewModelScope.launch { loadMonth(month) }
    }
```

- [ ] **Step 6: Ejecutar los tests para verificar que pasan**

Run: `.\gradlew.bat :composeApp:testDebugUnitTest --tests "com.franciscor.agendnote.AgendaViewModelTest" --console=plain`
Expected: `BUILD SUCCESSFUL`, los 4 tests nuevos y todos los existentes pasan.

- [ ] **Step 7: Wirear la acción en `AgendaController.kt`**

Reemplazar:

```kotlin
    suspend fun handle(action: AgendaAction) {
        when (action) {
            is AgendaAction.MoveDay -> {
                val targetDate = viewModel.moveDay(action.delta)
                viewModel.loadTasksForDate(targetDate)
            }

            is AgendaAction.SelectDate -> {
                viewModel.selectDate(action.date)
                viewModel.loadTasksForDate(action.date)
            }

            AgendaAction.RefreshSelectedDate -> {
                viewModel.loadTasksForDate(viewModel.uiState.selectedDate)
            }
        }
    }
```

por:

```kotlin
    suspend fun handle(action: AgendaAction) {
        when (action) {
            is AgendaAction.MoveDay -> {
                val targetDate = viewModel.moveDay(action.delta)
                viewModel.loadTasksForDate(targetDate)
            }

            is AgendaAction.SelectDate -> {
                viewModel.selectDate(action.date)
                viewModel.loadTasksForDate(action.date)
            }

            AgendaAction.RefreshSelectedDate -> {
                viewModel.loadTasksForDate(viewModel.uiState.selectedDate)
            }

            is AgendaAction.LoadMonth -> {
                viewModel.loadMonth(action.month)
            }
        }
    }
```

Reemplazar:

```kotlin
    fun handleAsync(action: AgendaAction) {
        when (action) {
            is AgendaAction.MoveDay -> viewModel.moveDayAndLoad(action.delta)
            is AgendaAction.SelectDate -> viewModel.selectDateAndLoad(action.date)
            AgendaAction.RefreshSelectedDate -> viewModel.refreshSelectedDateAsync()
        }
    }
```

por:

```kotlin
    fun handleAsync(action: AgendaAction) {
        when (action) {
            is AgendaAction.MoveDay -> viewModel.moveDayAndLoad(action.delta)
            is AgendaAction.SelectDate -> viewModel.selectDateAndLoad(action.date)
            AgendaAction.RefreshSelectedDate -> viewModel.refreshSelectedDateAsync()
            is AgendaAction.LoadMonth -> viewModel.loadMonthAsync(action.month)
        }
    }
```

- [ ] **Step 8: Compilar y correr el test suite completo**

Run: `.\gradlew.bat :composeApp:compileCommonMainKotlinMetadata --console=plain`
Expected: `BUILD SUCCESSFUL`.

Run: `.\gradlew.bat :composeApp:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`, sin regresiones.

- [ ] **Step 9: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/presentation/model/AgendaUiState.kt composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/presentation/model/AgendaAction.kt composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/presentation/viewmodel/AgendaViewModel.kt composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/presentation/controller/AgendaController.kt composeApp/src/commonTest/kotlin/com/franciscor/agendnote/AgendaViewModelTest.kt
git commit -m "Agregar carga de un mes completo en AgendaViewModel"
```

---

### Task 3: Extraer CalendarMonthView reutilizable y quitar el overlay modal

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/presentation/view/AgendaOverlays.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/presentation/view/AgendaScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/presentation/view/AgendaDayComponents.kt`

**Interfaces:**
- Produces: `internal fun CalendarMonthView(selectedDate: LocalDate, visibleMonth: LocalDate, tasksByDate: Map<LocalDate, List<TaskItem>>, onSelectDate: (LocalDate) -> Unit, onVisibleMonthChange: (LocalDate) -> Unit, modifier: Modifier = Modifier)` en `AgendaOverlays.kt`. La Task 4 (pantalla nueva) consume este composable.

- [ ] **Step 1: Reemplazar `CalendarOverlay` por `CalendarMonthView` en `AgendaOverlays.kt`**

Reemplazar el composable completo (desde `@Composable` que precede a `internal fun CalendarOverlay` hasta su cierre `}` final, justo antes de `@Composable private fun CalendarDayCell`):

```kotlin
@Composable
internal fun CalendarOverlay(
    selectedDate: LocalDate,
    tasksByDate: Map<LocalDate, List<TaskItem>>,
    onSelectDate: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val layout = AppLayout.metrics
    val timeZone = remember { TimeZone.currentSystemDefault() }
    val today = remember { currentDate(timeZone) }
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
```

por:

```kotlin
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
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                        .clickable(
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
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Calendario",
                        style = MaterialTheme.typography.titleMedium,
                        color = GlassTheme.tokens.textPrimary,
                    )
                }
                Box(modifier = Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp))
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
                                onClick = { onVisibleMonthChange(visibleMonth.plus(-1, DateTimeUnit.MONTH)) },
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
                                onClick = { onVisibleMonthChange(visibleMonth.plus(1, DateTimeUnit.MONTH)) },
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
```

- [ ] **Step 2: Quitar el import ahora no usado en `AgendaOverlays.kt`**

`safeContentPadding` ya no se usa en este archivo (era solo del wrapper modal eliminado). Quitar:

```kotlin
import androidx.compose.foundation.layout.safeContentPadding
```

- [ ] **Step 3: Quitar `onOpenCalendar` de `AgendaHeader` en `AgendaDayComponents.kt`**

Reemplazar:

```kotlin
internal fun AgendaHeader(
    selectedDate: LocalDate,
    isToday: Boolean,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onOpenCalendar: () -> Unit,
) {
```

por:

```kotlin
internal fun AgendaHeader(
    selectedDate: LocalDate,
    isToday: Boolean,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
) {
```

Reemplazar:

```kotlin
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(layout.width(14.dp, 10.dp)),
            ) {
                Text(
                    text = "Agenda",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = layout.text(36.sp, 32.sp),
                        lineHeight = layout.text(38.sp, 34.sp),
                    ),
                    color = GlassTheme.tokens.textPrimary,
                )
                GlassIconButton(
                    icon = Icons.Rounded.CalendarToday,
                    contentDescription = "Calendario",
                    onClick = onOpenCalendar,
                )
            }
```

por:

```kotlin
            Text(
                text = "Agenda",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = layout.text(36.sp, 32.sp),
                    lineHeight = layout.text(38.sp, 34.sp),
                ),
                color = GlassTheme.tokens.textPrimary,
            )
```

Quitar el import ahora no usado en este archivo (era solo del botón eliminado):

```kotlin
import androidx.compose.material.icons.rounded.CalendarToday
```

- [ ] **Step 4: Quitar el uso del overlay en `AgendaScreen.kt`**

Reemplazar:

```kotlin
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    var showTaskSheet by rememberSaveable { mutableStateOf(false) }
```

por:

```kotlin
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showTaskSheet by rememberSaveable { mutableStateOf(false) }
```

Reemplazar:

```kotlin
    val blurRadius = if (showTaskSheet || showCalendar || showTaskDetails != null) layout.size(18.dp, 14.dp) else 0.dp
```

por:

```kotlin
    val blurRadius = if (showTaskSheet || showTaskDetails != null) layout.size(18.dp, 14.dp) else 0.dp
```

Reemplazar:

```kotlin
            AgendaHeader(
                selectedDate = selectedDate,
                isToday = selectedDate == viewModel.today(),
                onPreviousDay = {
                    controller.handleAsync(AgendaAction.MoveDay(-1))
                },
                onNextDay = {
                    controller.handleAsync(AgendaAction.MoveDay(1))
                },
                onOpenCalendar = { showCalendar = true },
            )
```

por:

```kotlin
            AgendaHeader(
                selectedDate = selectedDate,
                isToday = selectedDate == viewModel.today(),
                onPreviousDay = {
                    controller.handleAsync(AgendaAction.MoveDay(-1))
                },
                onNextDay = {
                    controller.handleAsync(AgendaAction.MoveDay(1))
                },
            )
```

Reemplazar:

```kotlin
        if (showCalendar) {
            CalendarOverlay(
                selectedDate = selectedDate,
                tasksByDate = uiState.tasksByDate,
                onDismiss = { showCalendar = false },
                onSelectDate = { date ->
                    showCalendar = false
                    controller.handleAsync(AgendaAction.SelectDate(date))
                },
            )
        }

        if (showTaskSheet) {
```

por:

```kotlin
        if (showTaskSheet) {
```

- [ ] **Step 5: Compilar en los 3 targets para verificar**

Run: `.\gradlew.bat :composeApp:compileCommonMainKotlinMetadata --console=plain`
Expected: `BUILD SUCCESSFUL`.

Run: `.\gradlew.bat :composeApp:compileDebugKotlinAndroid --console=plain`
Expected: `BUILD SUCCESSFUL`.

Run: `.\gradlew.bat :composeApp:compileKotlinIosSimulatorArm64 --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/presentation/view/AgendaOverlays.kt composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/presentation/view/AgendaScreen.kt composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/presentation/view/AgendaDayComponents.kt
git commit -m "Extraer CalendarMonthView reutilizable y quitar el overlay modal de calendario"
```

---

### Task 4: Pantalla CalendarScreen y tab de navegación

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/presentation/view/CalendarScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/app/navigation/AppRoute.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/app/navigation/MainTab.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/app/navigation/NavigationComponents.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/app/navigation/AppNavHost.kt`

**Interfaces:**
- Consumes: `CalendarMonthView` (Task 3), `AgendaAction.LoadMonth`/`AgendaUiState.visibleMonth`/`isMonthLoading`/`monthErrorMessage` (Task 2).
- Produces: `CalendarScreen(viewModel: AgendaViewModel, controller: AgendaController, onSelectDate: (LocalDate) -> Unit, modifier: Modifier = Modifier)`, `AppRoute.Calendar`, `MainTab.CALENDAR`. Nada más depende de esto — es el último eslabón de la cadena.

- [ ] **Step 1: Crear `CalendarScreen.kt`**

```kotlin
package com.franciscor.agendnote.feature.agenda.presentation.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.franciscor.agendnote.core.ui.layout.AppLayout
import com.franciscor.agendnote.core.ui.theme.GlassTheme
import com.franciscor.agendnote.feature.agenda.presentation.controller.AgendaController
import com.franciscor.agendnote.feature.agenda.presentation.model.AgendaAction
import com.franciscor.agendnote.feature.agenda.presentation.viewmodel.AgendaViewModel
import kotlinx.datetime.LocalDate

@Composable
fun CalendarScreen(
    viewModel: AgendaViewModel,
    controller: AgendaController,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = AppLayout.metrics
    val contentInset = layout.width(24.dp, 20.dp)
    val uiState = viewModel.uiState

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
            onSelectDate = onSelectDate,
            onVisibleMonthChange = { month ->
                controller.handleAsync(AgendaAction.LoadMonth(month))
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
```

- [ ] **Step 2: Agregar `AppRoute.Calendar`**

Reemplazar:

```kotlin
sealed interface AppRoute {
    val route: String

    data object Agenda : AppRoute {
        override val route: String = "agenda"
    }

    data object Labels : AppRoute {
        override val route: String = "labels"
    }

    data object Settings : AppRoute {
        override val route: String = "settings"
    }
}
```

por:

```kotlin
sealed interface AppRoute {
    val route: String

    data object Agenda : AppRoute {
        override val route: String = "agenda"
    }

    data object Calendar : AppRoute {
        override val route: String = "calendar"
    }

    data object Labels : AppRoute {
        override val route: String = "labels"
    }

    data object Settings : AppRoute {
        override val route: String = "settings"
    }
}
```

- [ ] **Step 3: Agregar `MainTab.CALENDAR`**

Reemplazar:

```kotlin
enum class MainTab(
    val label: String,
    val route: AppRoute,
) {
    AGENDA("Agenda", AppRoute.Agenda),
    LABELS("Etiquetas", AppRoute.Labels),
    SETTINGS("Ajustes", AppRoute.Settings),
    ;
```

por:

```kotlin
enum class MainTab(
    val label: String,
    val route: AppRoute,
) {
    AGENDA("Agenda", AppRoute.Agenda),
    CALENDAR("Calendario", AppRoute.Calendar),
    LABELS("Etiquetas", AppRoute.Labels),
    SETTINGS("Ajustes", AppRoute.Settings),
    ;
```

- [ ] **Step 4: Agregar el ítem de Calendario a `BottomBar` en `NavigationComponents.kt`**

Reemplazar:

```kotlin
            BottomBarItem(
                icon = Icons.Rounded.CalendarToday,
                label = "Agenda",
                selected = selectedTab == MainTab.AGENDA,
                onClick = { onSelect(MainTab.AGENDA) },
            )
            BottomBarItem(
                icon = Icons.Rounded.Label,
                label = "Etiquetas",
                selected = selectedTab == MainTab.LABELS,
                onClick = { onSelect(MainTab.LABELS) },
            )
```

por:

```kotlin
            BottomBarItem(
                icon = Icons.Rounded.CalendarToday,
                label = "Agenda",
                selected = selectedTab == MainTab.AGENDA,
                onClick = { onSelect(MainTab.AGENDA) },
            )
            BottomBarItem(
                icon = Icons.Rounded.DateRange,
                label = "Calendario",
                selected = selectedTab == MainTab.CALENDAR,
                onClick = { onSelect(MainTab.CALENDAR) },
            )
            BottomBarItem(
                icon = Icons.Rounded.Label,
                label = "Etiquetas",
                selected = selectedTab == MainTab.LABELS,
                onClick = { onSelect(MainTab.LABELS) },
            )
```

Agregar el import junto a los demás íconos:

```kotlin
import androidx.compose.material.icons.rounded.DateRange
```

- [ ] **Step 5: Agregar la ruta de Calendario en `AppNavHost.kt`**

Reemplazar el comentario que todavia menciona `CalendarOverlay` (renombrado a `CalendarMonthView` en la Task 3):

```kotlin
                // Keeps interactive content clear of notches/system cutouts, matching the
                // pattern already used in CalendarOverlay (feature/agenda).
```

por:

```kotlin
                // Keeps interactive content clear of notches/system cutouts, matching the
                // pattern already used in CalendarMonthView (feature/agenda).
```

Reemplazar:

```kotlin
                        composable(AppRoute.Agenda.route) {
                            AgendaRoute(
                                agendaViewModel = agendaViewModel,
                                agendaController = agendaController,
                                labelsViewModel = labelsViewModel,
                                labelsController = labelsController,
                            )
                        }
                        composable(AppRoute.Labels.route) {
```

por:

```kotlin
                        composable(AppRoute.Agenda.route) {
                            AgendaRoute(
                                agendaViewModel = agendaViewModel,
                                agendaController = agendaController,
                                labelsViewModel = labelsViewModel,
                                labelsController = labelsController,
                            )
                        }
                        composable(AppRoute.Calendar.route) {
                            CalendarRoute(
                                agendaViewModel = agendaViewModel,
                                agendaController = agendaController,
                                onNavigateToAgenda = { navigateToMainTab(MainTab.AGENDA) },
                            )
                        }
                        composable(AppRoute.Labels.route) {
```

Agregar el import de `CalendarScreen`, junto a los demás imports de `feature.agenda.presentation.view`:

```kotlin
import com.franciscor.agendnote.feature.agenda.presentation.view.CalendarScreen
```

Agregar el composable `CalendarRoute` al final del archivo, después de `AgendaRoute` y antes de `LabelsRoute`:

```kotlin
@Composable
private fun CalendarRoute(
    agendaViewModel: AgendaViewModel,
    agendaController: AgendaController,
    onNavigateToAgenda: () -> Unit,
) {
    CalendarScreen(
        viewModel = agendaViewModel,
        controller = agendaController,
        onSelectDate = { date ->
            agendaController.handleAsync(AgendaAction.SelectDate(date))
            onNavigateToAgenda()
        },
        modifier = Modifier.fillMaxSize(),
    )
}
```

- [ ] **Step 6: Compilar en los 3 targets y correr el test suite completo**

Run: `.\gradlew.bat :composeApp:compileCommonMainKotlinMetadata --console=plain`
Expected: `BUILD SUCCESSFUL`.

Run: `.\gradlew.bat :composeApp:compileDebugKotlinAndroid --console=plain`
Expected: `BUILD SUCCESSFUL`.

Run: `.\gradlew.bat :composeApp:compileKotlinIosSimulatorArm64 --console=plain`
Expected: `BUILD SUCCESSFUL`.

Run: `.\gradlew.bat :composeApp:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`, sin regresiones.

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/presentation/view/CalendarScreen.kt composeApp/src/commonMain/kotlin/com/franciscor/agendnote/app/navigation/AppRoute.kt composeApp/src/commonMain/kotlin/com/franciscor/agendnote/app/navigation/MainTab.kt composeApp/src/commonMain/kotlin/com/franciscor/agendnote/app/navigation/NavigationComponents.kt composeApp/src/commonMain/kotlin/com/franciscor/agendnote/app/navigation/AppNavHost.kt
git commit -m "Agregar pantalla de calendario mensual como tab de navegacion"
```

---

## Verificación final (tras las 4 tareas)

- [ ] Compilar los 3 targets: `.\gradlew.bat :composeApp:compileCommonMainKotlinMetadata --console=plain`, `.\gradlew.bat :composeApp:compileDebugKotlinAndroid --console=plain`, `.\gradlew.bat :composeApp:compileKotlinIosSimulatorArm64 --console=plain` — todos `BUILD SUCCESSFUL`.
- [ ] Correr el test suite completo: `.\gradlew.bat :composeApp:testDebugUnitTest --console=plain` — sin fallos, incluyendo los 4 tests nuevos de `loadMonth`.
- [ ] Confirmar que no queda ninguna referencia a `CalendarOverlay`/`showCalendar`/`onOpenCalendar` en el árbol (`grep -rn "CalendarOverlay\|showCalendar\|onOpenCalendar" composeApp/src` debe devolver vacío).

## Fuera de alcance

- Vista semanal.
- Lista de tareas inline debajo del grid del mes en el propio tab Calendario.
- Prefetch de meses adyacentes.
- Cualquier cambio al backend/edge function — el endpoint `from`/`to` ya existe.
- Filtro de tareas completadas en el heatmap (se sigue contando todo, igual que el overlay original).
