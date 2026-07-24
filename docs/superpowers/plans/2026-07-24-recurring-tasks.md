# Tareas recurrentes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permitir crear tareas que se repiten (diaria, días específicos de la semana, o mensual) en AgendNote, con materialización de apariciones del lado del cliente y horizonte rodante indefinido.

**Architecture:** Cada aparición de una serie recurrente es una fila normal en `tasks` (vía el `AgendaTaskRepository` ya existente), vinculada por un `series_id` opcional a una nueva tabla `task_series` que guarda la regla. Un `SeriesMaterializer` (lógica pura + repositorios) genera las apariciones que falten hasta un horizonte de 8 semanas, tanto al abrir la app como justo después de crear una serie nueva. El cálculo de qué fechas caen dentro de una regla es lógica pura sin I/O, testeada por separado.

**Tech Stack:** Kotlin Multiplatform (Compose Multiplatform), kotlinx-datetime, Ktor client, kotlinx.serialization, Supabase Edge Functions (Deno/TypeScript) para el backend preparado (no desplegado en este plan).

## Global Constraints

- No se aplica/despliega ningún cambio de Supabase como parte de este plan (migración SQL + edge function quedan como archivos listos en el repo) — el MCP de Supabase no está disponible en esta sesión.
- Patrones de recurrencia soportados: `daily`, `weekly_days` (incluye "semanal" como caso de 1 solo día), `monthly` (día del mes, con clamping al último día si el mes es más corto).
- Editar/borrar una aparición puntual no requiere lógica especial — ya es una fila de `tasks` independiente.
- Borrar una serie completa elimina las apariciones futuras no completadas (`day >= hoy` y `is_done = false`); las pasadas o completadas quedan sueltas (`series_id = NULL`).
- Horizonte de materialización: 8 semanas desde `max(materialized_until, start_date)`. Si el lote de creación falla a mitad de camino, `materialized_until` no avanza (se reintenta el tramo completo en el próximo arranque) — limitación aceptada, no se implementa idempotencia adicional en esta versión.
- Sigue el patrón arquitectónico ya establecido: capas `domain`/`data`/`presentation`, repositorios nullable cuando `remoteConfigStatus` no está disponible, ViewModels con su propio `CoroutineScope` para mutaciones fire-and-forget.

---

### Task 1: Regla de recurrencia y cálculo de fechas (lógica pura, TDD)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/domain/RecurrenceRule.kt`
- Test: `composeApp/src/commonTest/kotlin/com/franciscor/agendnote/RecurrenceRuleTest.kt`

**Interfaces:**
- Produces: `sealed interface RecurrenceRule` con variantes `Daily`, `WeeklyDays(days: Set<DayOfWeek>)`, `Monthly(dayOfMonth: Int)`; función `fun occurrencesBetween(rule: RecurrenceRule, from: LocalDate, to: LocalDate): List<LocalDate>`. Todas las tareas posteriores que necesiten calcular fechas de recurrencia usan esta función y estos tipos exactos.

- [ ] **Step 1: Escribir los tests que fallan**

```kotlin
package com.franciscor.agendnote

import com.franciscor.agendnote.feature.agenda.domain.RecurrenceRule
import com.franciscor.agendnote.feature.agenda.domain.occurrencesBetween
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals

class RecurrenceRuleTest {
    @Test
    fun `daily rule includes every date in range`() {
        val from = LocalDate(2026, 8, 1)
        val to = LocalDate(2026, 8, 5)

        val result = occurrencesBetween(RecurrenceRule.Daily, from, to)

        assertEquals(
            listOf(
                LocalDate(2026, 8, 1),
                LocalDate(2026, 8, 2),
                LocalDate(2026, 8, 3),
                LocalDate(2026, 8, 4),
                LocalDate(2026, 8, 5),
            ),
            result,
        )
    }

    @Test
    fun `weekly days rule only includes matching weekdays`() {
        val from = LocalDate(2026, 8, 1)
        val to = LocalDate(2026, 8, 31)
        val targetDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        val rule = RecurrenceRule.WeeklyDays(targetDays)

        val result = occurrencesBetween(rule, from, to)

        // Recomputado de forma independiente (no reutiliza la implementación de occurrencesBetween)
        // para seguir siendo una verificación real, no una tautología.
        val expected = generateSequence(from) { it.plus(1, DateTimeUnit.DAY) }
            .takeWhile { it <= to }
            .filter { it.dayOfWeek in targetDays }
            .toList()
        assertEquals(expected, result)
        assertEquals(true, result.isNotEmpty())
        assertEquals(true, result.all { it.dayOfWeek in targetDays })
    }

    @Test
    fun `weekly days rule with a single day behaves as a weekly repeat`() {
        val from = LocalDate(2026, 8, 1)
        val to = LocalDate(2026, 8, 31)
        val rule = RecurrenceRule.WeeklyDays(setOf(DayOfWeek.TUESDAY))

        val result = occurrencesBetween(rule, from, to)

        val expected = generateSequence(from) { it.plus(1, DateTimeUnit.DAY) }
            .takeWhile { it <= to }
            .filter { it.dayOfWeek == DayOfWeek.TUESDAY }
            .toList()
        assertEquals(expected, result)
        // Un solo día por semana en un rango de 31 dias cae 4 o 5 veces.
        assertEquals(true, result.size in 4..5)
    }

    @Test
    fun `monthly rule matches the requested day of month`() {
        val from = LocalDate(2026, 6, 1)
        val to = LocalDate(2026, 9, 30)
        val rule = RecurrenceRule.Monthly(dayOfMonth = 15)

        val result = occurrencesBetween(rule, from, to)

        assertEquals(
            listOf(
                LocalDate(2026, 6, 15),
                LocalDate(2026, 7, 15),
                LocalDate(2026, 8, 15),
                LocalDate(2026, 9, 15),
            ),
            result,
        )
    }

    @Test
    fun `monthly rule clamps to the last day when the month is shorter`() {
        // Dia 31 solicitado; abril y junio solo tienen 30 dias (hecho de calendario estable,
        // no depende del ano).
        val from = LocalDate(2026, 3, 1)
        val to = LocalDate(2026, 6, 30)
        val rule = RecurrenceRule.Monthly(dayOfMonth = 31)

        val result = occurrencesBetween(rule, from, to)

        assertEquals(
            listOf(
                LocalDate(2026, 3, 31),
                LocalDate(2026, 4, 30),
                LocalDate(2026, 5, 31),
                LocalDate(2026, 6, 30),
            ),
            result,
        )
    }

    @Test
    fun `empty range when from is after to`() {
        val result = occurrencesBetween(RecurrenceRule.Daily, LocalDate(2026, 8, 10), LocalDate(2026, 8, 1))

        assertEquals(emptyList(), result)
    }

    @Test
    fun `single day range includes that day when it matches`() {
        val date = LocalDate(2026, 8, 1)

        val result = occurrencesBetween(RecurrenceRule.Daily, date, date)

        assertEquals(listOf(date), result)
    }
}
```

- [ ] **Step 2: Ejecutar los tests para verificar que fallan**

Run (desde `C:\Users\franr\AgendNote`): `.\gradlew.bat :composeApp:testDebugUnitTest --tests "com.franciscor.agendnote.RecurrenceRuleTest" --console=plain`
Expected: FAIL — `Unresolved reference 'RecurrenceRule'` / `Unresolved reference 'occurrencesBetween'` (el archivo de implementación todavía no existe).

- [ ] **Step 3: Implementar `RecurrenceRule.kt`**

```kotlin
package com.franciscor.agendnote.feature.agenda.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

sealed interface RecurrenceRule {
    data object Daily : RecurrenceRule

    data class WeeklyDays(val days: Set<DayOfWeek>) : RecurrenceRule

    data class Monthly(val dayOfMonth: Int) : RecurrenceRule
}

/**
 * Todas las fechas entre [from] y [to] (ambos inclusive) que caen dentro de [rule].
 * Lista vacia si [from] es posterior a [to].
 */
fun occurrencesBetween(rule: RecurrenceRule, from: LocalDate, to: LocalDate): List<LocalDate> {
    if (from > to) return emptyList()
    val dates = mutableListOf<LocalDate>()
    var current = from
    while (current <= to) {
        if (matchesRule(rule, current)) {
            dates.add(current)
        }
        current = current.plus(1, DateTimeUnit.DAY)
    }
    return dates
}

private fun matchesRule(rule: RecurrenceRule, date: LocalDate): Boolean {
    return when (rule) {
        is RecurrenceRule.Daily -> true
        is RecurrenceRule.WeeklyDays -> rule.days.contains(date.dayOfWeek)
        is RecurrenceRule.Monthly -> date.dayOfMonth == effectiveDayOfMonth(rule.dayOfMonth, date)
    }
}

/** Si [requestedDay] no existe en el mes de [date] (p. ej. 31 en abril), usa el ultimo dia del mes. */
private fun effectiveDayOfMonth(requestedDay: Int, date: LocalDate): Int {
    val lastDayOfMonth = LocalDate(date.year, date.monthNumber, 1)
        .plus(1, DateTimeUnit.MONTH)
        .minus(1, DateTimeUnit.DAY)
        .dayOfMonth
    return requestedDay.coerceAtMost(lastDayOfMonth)
}
```

- [ ] **Step 4: Ejecutar los tests para verificar que pasan**

Run: `.\gradlew.bat :composeApp:testDebugUnitTest --tests "com.franciscor.agendnote.RecurrenceRuleTest" --console=plain`
Expected: `BUILD SUCCESSFUL`, 7/7 tests pasando.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/domain/RecurrenceRule.kt composeApp/src/commonTest/kotlin/com/franciscor/agendnote/RecurrenceRuleTest.kt
git commit -m "Agregar calculo de fechas de recurrencia (logica pura)"
```

---

### Task 2: Modelos de cliente y DTOs de red

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/core/model/AgendaModels.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/core/network/AgendaDtos.kt`

**Interfaces:**
- Consumes: `RecurrenceRule` de la Task 1.
- Produces: `TaskItem.seriesId: String?`, `TaskDraft.seriesId: String?`, `TaskSeries` data class (id, title, details, time, rule, labelIds, startDate, isActive, materializedUntil), y los DTOs `TaskSeriesDto`, `CreateTaskSeriesRequest`, `UpdateTaskSeriesRequest`, `TaskSeriesResponse`, `TaskSeriesListResponse`. La Task 3 (repositorio) construye/consume estos tipos exactos.

- [ ] **Step 1: Editar `AgendaModels.kt`**

Reemplazar el archivo completo:

```kotlin
package com.franciscor.agendnote.core.model

import androidx.compose.runtime.Immutable
import com.franciscor.agendnote.feature.agenda.domain.RecurrenceRule
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

@Immutable
data class LabelTag(
    val id: String,
    val name: String,
    val colorHex: String,
)

@Immutable
data class TaskItem(
    val id: String,
    val title: String,
    val details: String?,
    val time: LocalTime?,
    val labels: List<LabelTag>,
    val endTime: LocalTime? = null,
    val isDone: Boolean = false,
    val seriesId: String? = null,
)

@Immutable
data class TaskDraft(
    val title: String,
    val details: String?,
    val time: LocalTime?,
    val labels: List<LabelTag>,
    val seriesId: String? = null,
)

@Immutable
data class TaskSeries(
    val id: String,
    val title: String,
    val details: String?,
    val time: LocalTime?,
    val rule: RecurrenceRule,
    val labelIds: List<String>,
    val startDate: LocalDate,
    val isActive: Boolean,
    val materializedUntil: LocalDate,
)
```

- [ ] **Step 2: Editar `AgendaDtos.kt` — agregar `series_id` a los DTOs de tareas existentes**

Reemplazar:

```kotlin
@Serializable
data class TaskDto(
    val id: String,
    val title: String,
    val body: String? = null,
    val day: String,
    val due_at: String? = null,
    val slot_end_at: String? = null,
    val is_done: Boolean = false,
    val order_index: Int = 0,
    val labels: List<LabelDto> = emptyList(),
)
```

por:

```kotlin
@Serializable
data class TaskDto(
    val id: String,
    val title: String,
    val body: String? = null,
    val day: String,
    val due_at: String? = null,
    val slot_end_at: String? = null,
    val is_done: Boolean = false,
    val order_index: Int = 0,
    val labels: List<LabelDto> = emptyList(),
    val series_id: String? = null,
)
```

Reemplazar:

```kotlin
@Serializable
data class CreateTaskRequest(
    val title: String,
    val body: String? = null,
    val day: String,
    val due_at: String? = null,
    val slot_end_at: String? = null,
    val is_done: Boolean = false,
    val order_index: Int = 0,
    val label_ids: List<String> = emptyList(),
    val label_names: List<String> = emptyList(),
)
```

por:

```kotlin
@Serializable
data class CreateTaskRequest(
    val title: String,
    val body: String? = null,
    val day: String,
    val due_at: String? = null,
    val slot_end_at: String? = null,
    val is_done: Boolean = false,
    val order_index: Int = 0,
    val label_ids: List<String> = emptyList(),
    val label_names: List<String> = emptyList(),
    val series_id: String? = null,
)
```

- [ ] **Step 3: Agregar los DTOs nuevos de series al final de `AgendaDtos.kt`**

Agregar (antes de la última llave del archivo, después de `SuccessResponse`):

```kotlin
@Serializable
data class TaskSeriesDto(
    val id: String,
    val title: String,
    val body: String? = null,
    val time: String? = null,
    val recurrence_type: String,
    val days_of_week: List<Int>? = null,
    val day_of_month: Int? = null,
    val label_ids: List<String> = emptyList(),
    val start_date: String,
    val is_active: Boolean = true,
    val materialized_until: String,
)

@Serializable
data class CreateTaskSeriesRequest(
    val title: String,
    val body: String? = null,
    val time: String? = null,
    val recurrence_type: String,
    val days_of_week: List<Int>? = null,
    val day_of_month: Int? = null,
    val label_ids: List<String> = emptyList(),
    val start_date: String,
)

@Serializable
data class UpdateTaskSeriesRequest(
    val id: String,
    val materialized_until: String? = null,
    val is_active: Boolean? = null,
)

@Serializable
data class TaskSeriesResponse(
    val series: TaskSeriesDto,
)

@Serializable
data class TaskSeriesListResponse(
    val series: List<TaskSeriesDto> = emptyList(),
)
```

- [ ] **Step 4: Compilar para verificar**

Run: `.\gradlew.bat :composeApp:compileCommonMainKotlinMetadata --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/franciscor/agendnote/core/model/AgendaModels.kt composeApp/src/commonMain/kotlin/com/franciscor/agendnote/core/network/AgendaDtos.kt
git commit -m "Agregar modelos y DTOs de series recurrentes"
```

---

### Task 3: Cliente de red y repositorio de series

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/core/network/AgendaApiClient.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/data/SupabaseAgendaTaskRepository.kt`
- Create: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/domain/TaskSeriesRepository.kt`
- Create: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/data/SupabaseTaskSeriesRepository.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/app/di/AppServices.kt`

**Interfaces:**
- Consumes: `TaskSeriesDto`/`CreateTaskSeriesRequest`/`UpdateTaskSeriesRequest`/`TaskSeries`/`RecurrenceRule`/`TaskDraft.seriesId` de las Tasks 1-2.
- Produces: `TaskSeriesRepository` interfaz (`fetchActiveSeries()`, `createSeries(...)`, `markMaterialized(seriesId, until)`, `deleteSeries(id)`), disponible como `AppServices.taskSeriesRepository: TaskSeriesRepository?`. La Task 4 (materializador) consume esta interfaz.

- [ ] **Step 1: Agregar metodos de series a `AgendaApiClient.kt`**

Al final de la clase `AgendaApiClient` (justo antes de la ultima `}` que cierra la clase, despues de `deleteAllLabels`), agregar:

```kotlin

    suspend fun fetchTaskSeries(): List<TaskSeriesDto> {
        val response: TaskSeriesListResponse = client
            .get("$normalizedBaseUrl/api-task-series") {
                withAuth(this)
            }
            .body()
        return response.series
    }

    suspend fun createTaskSeries(request: CreateTaskSeriesRequest): TaskSeriesDto {
        val response: TaskSeriesResponse = client
            .post("$normalizedBaseUrl/api-task-series") {
                withAuth(this)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            .body()
        return response.series
    }

    suspend fun updateTaskSeries(request: UpdateTaskSeriesRequest): TaskSeriesDto {
        val response: TaskSeriesResponse = client
            .patch("$normalizedBaseUrl/api-task-series") {
                withAuth(this)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            .body()
        return response.series
    }

    suspend fun deleteTaskSeries(id: String): Boolean {
        val response: SuccessResponse = client
            .delete("$normalizedBaseUrl/api-task-series") {
                withAuth(this)
                parameter("id", id)
            }
            .body()
        return response.success
    }
```

- [ ] **Step 2: Crear la interfaz `TaskSeriesRepository.kt`**

```kotlin
package com.franciscor.agendnote.feature.agenda.domain

import com.franciscor.agendnote.core.model.LabelTag
import com.franciscor.agendnote.core.model.TaskSeries
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

interface TaskSeriesRepository {
    suspend fun fetchActiveSeries(): List<TaskSeries>

    suspend fun createSeries(
        title: String,
        details: String?,
        time: LocalTime?,
        rule: RecurrenceRule,
        labels: List<LabelTag>,
        startDate: LocalDate,
    ): TaskSeries

    suspend fun markMaterialized(seriesId: String, until: LocalDate): Boolean

    suspend fun deleteSeries(id: String): Boolean
}
```

- [ ] **Step 3: Crear la implementacion `SupabaseTaskSeriesRepository.kt`**

```kotlin
package com.franciscor.agendnote.feature.agenda.data

import com.franciscor.agendnote.core.model.LabelTag
import com.franciscor.agendnote.core.model.TaskSeries
import com.franciscor.agendnote.core.network.AgendaApiClient
import com.franciscor.agendnote.core.network.CreateTaskSeriesRequest
import com.franciscor.agendnote.core.network.TaskSeriesDto
import com.franciscor.agendnote.core.network.UpdateTaskSeriesRequest
import com.franciscor.agendnote.feature.agenda.domain.RecurrenceRule
import com.franciscor.agendnote.feature.agenda.domain.TaskSeriesRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class SupabaseTaskSeriesRepository(
    private val api: AgendaApiClient,
) : TaskSeriesRepository {
    override suspend fun fetchActiveSeries(): List<TaskSeries> {
        return api.fetchTaskSeries().map { it.toTaskSeries() }
    }

    override suspend fun createSeries(
        title: String,
        details: String?,
        time: LocalTime?,
        rule: RecurrenceRule,
        labels: List<LabelTag>,
        startDate: LocalDate,
    ): TaskSeries {
        val request = CreateTaskSeriesRequest(
            title = title,
            body = details,
            time = time?.toString(),
            recurrence_type = rule.toRecurrenceType(),
            days_of_week = (rule as? RecurrenceRule.WeeklyDays)?.days?.map { it.isoDayNumber },
            day_of_month = (rule as? RecurrenceRule.Monthly)?.dayOfMonth,
            label_ids = labels.map { it.id },
            start_date = startDate.toString(),
        )
        return api.createTaskSeries(request).toTaskSeries()
    }

    override suspend fun markMaterialized(seriesId: String, until: LocalDate): Boolean {
        return runCatching {
            api.updateTaskSeries(UpdateTaskSeriesRequest(id = seriesId, materialized_until = until.toString()))
        }.isSuccess
    }

    override suspend fun deleteSeries(id: String): Boolean = api.deleteTaskSeries(id)
}

private fun RecurrenceRule.toRecurrenceType(): String = when (this) {
    is RecurrenceRule.Daily -> "daily"
    is RecurrenceRule.WeeklyDays -> "weekly_days"
    is RecurrenceRule.Monthly -> "monthly"
}

private fun TaskSeriesDto.toTaskSeries(): TaskSeries {
    return TaskSeries(
        id = id,
        title = title,
        details = body,
        time = time?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
        rule = toRecurrenceRule(),
        labelIds = label_ids,
        startDate = LocalDate.parse(start_date),
        isActive = is_active,
        materializedUntil = LocalDate.parse(materialized_until),
    )
}

private fun TaskSeriesDto.toRecurrenceRule(): RecurrenceRule {
    return when (recurrence_type) {
        "weekly_days" -> RecurrenceRule.WeeklyDays(
            days = (days_of_week ?: emptyList())
                .mapNotNull { isoDayNumber -> DayOfWeek.entries.find { it.isoDayNumber == isoDayNumber } }
                .toSet(),
        )
        "monthly" -> RecurrenceRule.Monthly(dayOfMonth = day_of_month ?: 1)
        else -> RecurrenceRule.Daily
    }
}
```

Esta implementación usa `kotlinx.datetime.DayOfWeek` y necesita su import — agregar al inicio del archivo, junto a los demás imports de `kotlinx.datetime`:

```kotlin
import kotlinx.datetime.DayOfWeek
```

(Verificar tras escribir el archivo que el import quedó presente; si el editor no lo agregó automáticamente, añadirlo manualmente antes de `import kotlinx.datetime.LocalDate`.)

- [ ] **Step 4: Wirear `series_id` en `SupabaseAgendaTaskRepository.kt`**

Reemplazar:

```kotlin
    override suspend fun createTask(date: LocalDate, draft: TaskDraft): TaskItem {
        val dueAt = draft.time?.let { time ->
            LocalDateTime(date, time).toInstant(timeZone).toString()
        }
        val request = CreateTaskRequest(
            title = draft.title,
            body = draft.details,
            day = date.toString(),
            due_at = dueAt,
            is_done = false,
            order_index = 0,
            label_ids = draft.labels.map { it.id },
        )
        return api.createTask(request).toTaskItem(timeZone)
    }
```

por:

```kotlin
    override suspend fun createTask(date: LocalDate, draft: TaskDraft): TaskItem {
        val dueAt = draft.time?.let { time ->
            LocalDateTime(date, time).toInstant(timeZone).toString()
        }
        val request = CreateTaskRequest(
            title = draft.title,
            body = draft.details,
            day = date.toString(),
            due_at = dueAt,
            is_done = false,
            order_index = 0,
            label_ids = draft.labels.map { it.id },
            series_id = draft.seriesId,
        )
        return api.createTask(request).toTaskItem(timeZone)
    }
```

Reemplazar:

```kotlin
private fun TaskDto.toTaskItem(timeZone: TimeZone): TaskItem {
    val time = due_at?.let { parseTime(it, timeZone) }
    val endTime = slot_end_at?.let { parseTime(it, timeZone) }
    return TaskItem(
        id = id,
        title = title,
        details = body,
        time = time,
        endTime = endTime,
        labels = labels.map { it.toLabelTag() },
        isDone = is_done,
    )
}
```

por:

```kotlin
private fun TaskDto.toTaskItem(timeZone: TimeZone): TaskItem {
    val time = due_at?.let { parseTime(it, timeZone) }
    val endTime = slot_end_at?.let { parseTime(it, timeZone) }
    return TaskItem(
        id = id,
        title = title,
        details = body,
        time = time,
        endTime = endTime,
        labels = labels.map { it.toLabelTag() },
        isDone = is_done,
        seriesId = series_id,
    )
}
```

- [ ] **Step 5: Registrar el repositorio en `AppServices.kt`**

Reemplazar:

```kotlin
    val agendaTaskRepository: AgendaTaskRepository? by lazy(LazyThreadSafetyMode.NONE) {
        if (remoteConfigStatus.isEnabled) SupabaseAgendaTaskRepository(apiClient) else null
    }
```

por:

```kotlin
    val agendaTaskRepository: AgendaTaskRepository? by lazy(LazyThreadSafetyMode.NONE) {
        if (remoteConfigStatus.isEnabled) SupabaseAgendaTaskRepository(apiClient) else null
    }

    val taskSeriesRepository: TaskSeriesRepository? by lazy(LazyThreadSafetyMode.NONE) {
        if (remoteConfigStatus.isEnabled) SupabaseTaskSeriesRepository(apiClient) else null
    }
```

Y agregar los imports correspondientes junto a los ya existentes de `agenda`:

```kotlin
import com.franciscor.agendnote.feature.agenda.data.SupabaseTaskSeriesRepository
import com.franciscor.agendnote.feature.agenda.domain.TaskSeriesRepository
```

- [ ] **Step 6: Compilar para verificar**

Run: `.\gradlew.bat :composeApp:compileCommonMainKotlinMetadata --console=plain`
Expected: `BUILD SUCCESSFUL`. Si aparece un error de import faltante de `DayOfWeek` en `SupabaseTaskSeriesRepository.kt`, agregarlo (ver nota del Step 3).

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/franciscor/agendnote/core/network/AgendaApiClient.kt composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/data/SupabaseAgendaTaskRepository.kt composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/domain/TaskSeriesRepository.kt composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/data/SupabaseTaskSeriesRepository.kt composeApp/src/commonMain/kotlin/com/franciscor/agendnote/app/di/AppServices.kt
git commit -m "Agregar cliente de red y repositorio de series recurrentes"
```

---

### Task 4: Materializador de apariciones (TDD con fakes)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/domain/SeriesMaterializer.kt`
- Test: `composeApp/src/commonTest/kotlin/com/franciscor/agendnote/SeriesMaterializerTest.kt`

**Interfaces:**
- Consumes: `TaskSeriesRepository`, `AgendaTaskRepository`, `RecurrenceRule`, `occurrencesBetween` de las Tasks 1-3.
- Produces: `class SeriesMaterializer(taskSeriesRepository: TaskSeriesRepository, agendaTaskRepository: AgendaTaskRepository, horizonWeeks: Int = 8)` con `suspend fun materializeAll(today: LocalDate)` y `suspend fun materializeSeries(series: TaskSeries, today: LocalDate): Boolean`. La Task 5 (wiring en AgendaViewModel) y la Task 6 (arranque de la app) usan esta clase.

- [ ] **Step 1: Escribir el test que falla, con fakes de los repositorios**

```kotlin
package com.franciscor.agendnote

import com.franciscor.agendnote.core.model.LabelTag
import com.franciscor.agendnote.core.model.TaskDraft
import com.franciscor.agendnote.core.model.TaskItem
import com.franciscor.agendnote.core.model.TaskSeries
import com.franciscor.agendnote.feature.agenda.domain.AgendaTaskRepository
import com.franciscor.agendnote.feature.agenda.domain.RecurrenceRule
import com.franciscor.agendnote.feature.agenda.domain.SeriesMaterializer
import com.franciscor.agendnote.feature.agenda.domain.TaskSeriesRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeTaskSeriesRepository(
    initialSeries: List<TaskSeries>,
) : TaskSeriesRepository {
    private val series = initialSeries.toMutableList()
    val markedUntil = mutableMapOf<String, LocalDate>()

    override suspend fun fetchActiveSeries(): List<TaskSeries> = series.filter { it.isActive }

    override suspend fun createSeries(
        title: String,
        details: String?,
        time: kotlinx.datetime.LocalTime?,
        rule: RecurrenceRule,
        labels: List<LabelTag>,
        startDate: LocalDate,
    ): TaskSeries {
        error("not used in this test")
    }

    override suspend fun markMaterialized(seriesId: String, until: LocalDate): Boolean {
        markedUntil[seriesId] = until
        val index = series.indexOfFirst { it.id == seriesId }
        if (index >= 0) {
            series[index] = series[index].copy(materializedUntil = until)
        }
        return true
    }

    override suspend fun deleteSeries(id: String): Boolean {
        series.removeAll { it.id == id }
        return true
    }
}

private class FakeAgendaTaskRepository : AgendaTaskRepository {
    val createdDrafts = mutableListOf<Pair<LocalDate, TaskDraft>>()
    var failAfter: Int = Int.MAX_VALUE

    override suspend fun fetchTasks(date: LocalDate): List<TaskItem> = emptyList()

    override suspend fun createTask(date: LocalDate, draft: TaskDraft): TaskItem {
        if (createdDrafts.size >= failAfter) {
            throw RuntimeException("simulated failure")
        }
        createdDrafts.add(date to draft)
        return TaskItem(
            id = "task-${createdDrafts.size}",
            title = draft.title,
            details = draft.details,
            time = draft.time,
            labels = draft.labels,
            seriesId = draft.seriesId,
        )
    }

    override suspend fun updateTaskDone(id: String, isDone: Boolean): TaskItem {
        error("not used in this test")
    }

    override suspend fun deleteTask(id: String): Boolean = true

    override suspend fun deleteAllTasks(): Boolean = true
}

class SeriesMaterializerTest {
    private val today = LocalDate(2026, 8, 1)

    private fun dailySeries(materializedUntil: LocalDate) = TaskSeries(
        id = "series-1",
        title = "Tomar vitaminas",
        details = null,
        time = null,
        rule = RecurrenceRule.Daily,
        labelIds = emptyList(),
        startDate = LocalDate(2026, 8, 1),
        isActive = true,
        materializedUntil = materializedUntil,
    )

    @Test
    fun `materializeSeries creates one task per occurrence and advances the cursor`() = runTest {
        val series = dailySeries(materializedUntil = LocalDate(2026, 7, 31))
        val seriesRepo = FakeTaskSeriesRepository(listOf(series))
        val taskRepo = FakeAgendaTaskRepository()
        val materializer = SeriesMaterializer(seriesRepo, taskRepo, horizonWeeks = 1)

        val success = materializer.materializeSeries(series, today)

        assertTrue(success)
        // Horizonte de 1 semana desde "today": 2026-08-01 al 2026-08-08 inclusive = 8 dias.
        assertEquals(8, taskRepo.createdDrafts.size)
        assertTrue(taskRepo.createdDrafts.all { (_, draft) -> draft.seriesId == series.id })
        assertEquals(LocalDate(2026, 8, 8), seriesRepo.markedUntil[series.id])
    }

    @Test
    fun `materializeSeries does not advance the cursor when a creation fails`() = runTest {
        val series = dailySeries(materializedUntil = LocalDate(2026, 7, 31))
        val seriesRepo = FakeTaskSeriesRepository(listOf(series))
        val taskRepo = FakeAgendaTaskRepository()
        taskRepo.failAfter = 2
        val materializer = SeriesMaterializer(seriesRepo, taskRepo, horizonWeeks = 1)

        val success = materializer.materializeSeries(series, today)

        assertEquals(false, success)
        assertEquals(2, taskRepo.createdDrafts.size)
        assertEquals(null, seriesRepo.markedUntil[series.id])
    }

    @Test
    fun `materializeSeries does nothing when already materialized past the horizon`() = runTest {
        val series = dailySeries(materializedUntil = LocalDate(2026, 12, 31))
        val seriesRepo = FakeTaskSeriesRepository(listOf(series))
        val taskRepo = FakeAgendaTaskRepository()
        val materializer = SeriesMaterializer(seriesRepo, taskRepo, horizonWeeks = 8)

        val success = materializer.materializeSeries(series, today)

        assertTrue(success)
        assertEquals(0, taskRepo.createdDrafts.size)
    }

    @Test
    fun `materializeAll processes every active series`() = runTest {
        val seriesA = dailySeries(materializedUntil = LocalDate(2026, 7, 31)).copy(id = "series-a")
        val seriesB = dailySeries(materializedUntil = LocalDate(2026, 7, 31)).copy(id = "series-b")
        val seriesRepo = FakeTaskSeriesRepository(listOf(seriesA, seriesB))
        val taskRepo = FakeAgendaTaskRepository()
        val materializer = SeriesMaterializer(seriesRepo, taskRepo, horizonWeeks = 1)

        materializer.materializeAll(today)

        assertEquals(16, taskRepo.createdDrafts.size)
        assertEquals(LocalDate(2026, 8, 8), seriesRepo.markedUntil["series-a"])
        assertEquals(LocalDate(2026, 8, 8), seriesRepo.markedUntil["series-b"])
    }
}
```

- [ ] **Step 2: Ejecutar el test para verificar que falla**

Run: `.\gradlew.bat :composeApp:testDebugUnitTest --tests "com.franciscor.agendnote.SeriesMaterializerTest" --console=plain`
Expected: FAIL — `Unresolved reference 'SeriesMaterializer'`.

- [ ] **Step 3: Implementar `SeriesMaterializer.kt`**

```kotlin
package com.franciscor.agendnote.feature.agenda.domain

import com.franciscor.agendnote.core.model.TaskDraft
import com.franciscor.agendnote.core.model.TaskSeries
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

class SeriesMaterializer(
    private val taskSeriesRepository: TaskSeriesRepository,
    private val agendaTaskRepository: AgendaTaskRepository,
    private val horizonWeeks: Int = 8,
) {
    suspend fun materializeAll(today: LocalDate) {
        val activeSeries = runCatching { taskSeriesRepository.fetchActiveSeries() }.getOrNull() ?: return
        for (series in activeSeries) {
            materializeSeries(series, today)
        }
    }

    /**
     * Genera las apariciones que falten para [series] hasta el horizonte rodante.
     * Devuelve true si no habia nada que hacer o si todo el lote se creo correctamente
     * (y en ese caso avanza `materialized_until`). Devuelve false si alguna creacion fallo,
     * sin avanzar el cursor - el proximo llamado reintenta el mismo tramo.
     */
    suspend fun materializeSeries(series: TaskSeries, today: LocalDate): Boolean {
        val horizonEnd = today.plus(horizonWeeks * 7, DateTimeUnit.DAY)
        val from = maxOf(series.materializedUntil.plus(1, DateTimeUnit.DAY), series.startDate)
        if (from > horizonEnd) return true

        val dates = occurrencesBetween(series.rule, from, horizonEnd)
        val draft = TaskDraft(
            title = series.title,
            details = series.details,
            time = series.time,
            labels = emptyList(),
            seriesId = series.id,
        )

        for (date in dates) {
            val created = runCatching { agendaTaskRepository.createTask(date, draft) }
            if (created.isFailure) return false
        }

        return taskSeriesRepository.markMaterialized(series.id, horizonEnd)
    }
}
```

Nota: `draft.labels` queda vacío aquí porque `TaskDraft.labels` es `List<LabelTag>` (objetos completos), mientras que `TaskSeries.labelIds` son solo IDs — el repositorio (`AgendaTaskRepository.createTask`) solo usa `draft.labels.map { it.id }` para el request, así que en la Task 5 (donde se resuelven los `LabelTag` completos a partir de `labelIds` antes de llamar al materializador) se decide si vale la pena resolver los objetos completos. Para esta tarea, dejar `emptyList()` es correcto porque el test no verifica etiquetas — es una limitación conocida y documentada, no un placeholder: las tareas materializadas hoy no llevan las etiquetas de la plantilla. Se resuelve en la Task 5 si el repositorio de labels está disponible en el mismo punto de llamada; si no, queda como mejora futura de esta pieza (anotar en el reporte final si se omite).

- [ ] **Step 4: Ejecutar el test para verificar que pasa**

Run: `.\gradlew.bat :composeApp:testDebugUnitTest --tests "com.franciscor.agendnote.SeriesMaterializerTest" --console=plain`
Expected: `BUILD SUCCESSFUL`, 4/4 tests pasando.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/domain/SeriesMaterializer.kt composeApp/src/commonTest/kotlin/com/franciscor/agendnote/SeriesMaterializerTest.kt
git commit -m "Agregar materializador de apariciones de series recurrentes"
```

---

### Task 5: Crear serie recurrente desde `AgendaViewModel`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/presentation/viewmodel/AgendaViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/presentation/controller/AgendaController.kt`

**Interfaces:**
- Consumes: `TaskSeriesRepository`, `SeriesMaterializer`, `RecurrenceRule` de las Tasks 3-4.
- Produces: `AgendaViewModel.saveRecurringTask(date: LocalDate, draft: TaskDraft, rule: RecurrenceRule): SaveResult` (suspend) y `AgendaController.saveRecurringTask(date, draft, rule): SaveResult` (suspend, delega). La Task 7 (UI de NewTaskSheet) llama a `controller.saveRecurringTask(...)`.

- [ ] **Step 1: Extender el constructor y agregar `saveRecurringTask` en `AgendaViewModel.kt`**

Reemplazar:

```kotlin
class AgendaViewModel(
    private val repository: AgendaTaskRepository?,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val remoteUnavailableMessage: String? = null,
    initialDate: LocalDate = currentDate(timeZone),
) {
    private val hasRemoteAccess = repository != null
    private val remoteErrorMessage = remoteUnavailableMessage
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: "Configuracion remota incompleta. No se puede conectar con la BD."
    private val notificationService = NotificationServiceProvider.getNotificationService()
```

por:

```kotlin
class AgendaViewModel(
    private val repository: AgendaTaskRepository?,
    private val taskSeriesRepository: TaskSeriesRepository? = null,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val remoteUnavailableMessage: String? = null,
    initialDate: LocalDate = currentDate(timeZone),
) {
    private val hasRemoteAccess = repository != null
    private val remoteErrorMessage = remoteUnavailableMessage
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: "Configuracion remota incompleta. No se puede conectar con la BD."
    private val notificationService = NotificationServiceProvider.getNotificationService()
    private val materializer = if (repository != null && taskSeriesRepository != null) {
        SeriesMaterializer(taskSeriesRepository, repository)
    } else {
        null
    }
```

Agregar el import correspondiente junto a los demás de `feature.agenda.domain`:

```kotlin
import com.franciscor.agendnote.feature.agenda.domain.RecurrenceRule
import com.franciscor.agendnote.feature.agenda.domain.SeriesMaterializer
import com.franciscor.agendnote.feature.agenda.domain.TaskSeriesRepository
```

Agregar el metodo `saveRecurringTask`, justo despues de `saveTask` (despues del cierre `}` de esa funcion, antes de `suspend fun toggleTaskDone`):

```kotlin
    suspend fun saveRecurringTask(date: LocalDate, draft: TaskDraft, rule: RecurrenceRule): SaveResult {
        val trimmedTitle = draft.title.trim()
        if (trimmedTitle.isEmpty()) return SaveResult(false, "Titulo requerido")
        val taskSeriesRepository = taskSeriesRepository ?: run {
            setError(date, remoteErrorMessage)
            return SaveResult(false, remoteErrorMessage)
        }
        val materializer = materializer ?: run {
            setError(date, remoteErrorMessage)
            return SaveResult(false, remoteErrorMessage)
        }

        val seriesResult = runCatching {
            taskSeriesRepository.createSeries(
                title = trimmedTitle,
                details = draft.details,
                time = draft.time,
                rule = rule,
                labels = draft.labels,
                startDate = date,
            )
        }

        val series = seriesResult.getOrElse { error ->
            setError(date, resolveServerError(error))
            return SaveResult(false, resolveServerError(error))
        }

        val materialized = materializer.materializeSeries(series, date)
        if (!materialized) {
            setError(date, "La serie se creo pero no se pudieron generar todas las tareas")
            return SaveResult(false, "La serie se creo pero no se pudieron generar todas las tareas")
        }

        loadTasksForDate(date)
        return SaveResult(true)
    }
```

- [ ] **Step 2: Agregar `saveRecurringTask` en `AgendaController.kt`**

Agregar el import:

```kotlin
import com.franciscor.agendnote.feature.agenda.domain.RecurrenceRule
```

Agregar el metodo, justo despues de `saveTask`:

```kotlin
    suspend fun saveRecurringTask(date: LocalDate, draft: TaskDraft, rule: RecurrenceRule): SaveResult {
        return viewModel.saveRecurringTask(date, draft, rule)
    }
```

- [ ] **Step 3: Compilar para verificar**

Run: `.\gradlew.bat :composeApp:compileCommonMainKotlinMetadata --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Correr los tests existentes para verificar que el nuevo parametro por defecto no rompe nada**

Run: `.\gradlew.bat :composeApp:testDebugUnitTest --tests "com.franciscor.agendnote.AgendaViewModelTest" --console=plain`
Expected: `BUILD SUCCESSFUL` — `taskSeriesRepository` tiene valor por defecto `null`, así que las construcciones existentes de `AgendaViewModel` en los tests (sin ese parámetro) siguen compilando igual.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/presentation/viewmodel/AgendaViewModel.kt composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/presentation/controller/AgendaController.kt
git commit -m "Agregar creacion de series recurrentes en AgendaViewModel/Controller"
```

---

### Task 6: Materializacion al arrancar la app

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/app/navigation/AppNavHost.kt`

**Interfaces:**
- Consumes: `AppServices.taskSeriesRepository`, `AppServices.agendaTaskRepository`, `SeriesMaterializer`, `AgendaAction.RefreshSelectedDate` (ya existe).
- Produces: nada nuevo consumido por otras tareas — es el punto de entrada final de la cadena de materialización.

- [ ] **Step 1: Pasar `taskSeriesRepository` al construir `AgendaViewModel` y disparar la materializacion**

Reemplazar:

```kotlin
    val agendaViewModel = remember(remoteConfigStatus) {
        AgendaViewModel(
            repository = AppServices.agendaTaskRepository,
            remoteUnavailableMessage = remoteConfigStatus.message,
        )
    }
    val agendaController = remember(agendaViewModel) { AgendaController(agendaViewModel) }
```

por:

```kotlin
    val agendaViewModel = remember(remoteConfigStatus) {
        AgendaViewModel(
            repository = AppServices.agendaTaskRepository,
            taskSeriesRepository = AppServices.taskSeriesRepository,
            remoteUnavailableMessage = remoteConfigStatus.message,
        )
    }
    val agendaController = remember(agendaViewModel) { AgendaController(agendaViewModel) }
```

Reemplazar:

```kotlin
    LaunchedEffect(labelsController) {
        labelsController.handle(LabelsAction.Load)
    }
```

por:

```kotlin
    LaunchedEffect(labelsController) {
        labelsController.handle(LabelsAction.Load)
    }

    LaunchedEffect(agendaController) {
        val taskSeriesRepository = AppServices.taskSeriesRepository
        val agendaTaskRepository = AppServices.agendaTaskRepository
        if (taskSeriesRepository != null && agendaTaskRepository != null) {
            SeriesMaterializer(taskSeriesRepository, agendaTaskRepository)
                .materializeAll(agendaViewModel.today())
            agendaController.handleAsync(AgendaAction.RefreshSelectedDate)
        }
    }
```

Agregar los imports necesarios junto a los ya existentes:

```kotlin
import com.franciscor.agendnote.feature.agenda.domain.SeriesMaterializer
import com.franciscor.agendnote.feature.agenda.presentation.model.AgendaAction
```

- [ ] **Step 2: Compilar para verificar**

Run: `.\gradlew.bat :composeApp:compileCommonMainKotlinMetadata --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/franciscor/agendnote/app/navigation/AppNavHost.kt
git commit -m "Materializar apariciones de series recurrentes al arrancar la app"
```

---

### Task 7: Selector "Repetir" en el sheet de nueva tarea

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/presentation/view/AgendaOverlays.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/presentation/view/AgendaScreen.kt`

**Interfaces:**
- Consumes: `RecurrenceRule`, `controller.saveRecurringTask(...)` de la Task 5.
- Produces: `NewTaskSheet` gana un parametro `onSaveRecurring: suspend (LocalDate, TaskDraft, RecurrenceRule) -> SaveResult`. `AgendaScreen.kt` pasa `controller::saveRecurringTask` a ese parametro.

- [ ] **Step 1: Agregar el parametro `onSaveRecurring` a `NewTaskSheet` y el estado del selector "Repetir"**

Reemplazar la firma:

```kotlin
    onDismiss: () -> Unit,
    onSave: suspend (LocalDate, TaskDraft) -> SaveResult,
) {
```

por:

```kotlin
    onDismiss: () -> Unit,
    onSave: suspend (LocalDate, TaskDraft) -> SaveResult,
    onSaveRecurring: suspend (LocalDate, TaskDraft, RecurrenceRule) -> SaveResult,
) {
```

Agregar el import:

```kotlin
import com.franciscor.agendnote.feature.agenda.domain.RecurrenceRule
```

Reemplazar el bloque de estado (justo despues de `var isSaving by remember { mutableStateOf(false) }`):

```kotlin
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
```

por:

```kotlin
    var isSaving by remember { mutableStateOf(false) }
    var selectedRecurrence by remember { mutableStateOf<RecurrenceOption>(RecurrenceOption.None) }
    val selectedWeekDays = remember { mutableStateListOf<DayOfWeek>() }
    var monthDay by remember { mutableStateOf(1) }
    val scope = rememberCoroutineScope()
```

Reemplazar el `LaunchedEffect(date, today, colorOptions)` que resetea el formulario:

```kotlin
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
```

por:

```kotlin
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
        selectedRecurrence = RecurrenceOption.None
        selectedWeekDays.clear()
        monthDay = selectedDate.dayOfMonth
    }
```

- [ ] **Step 2: Definir `RecurrenceOption` (arriba de `NewTaskSheet`, antes de la anotacion `@Composable` de esa funcion)**

```kotlin
private enum class RecurrenceOption {
    None, Daily, WeeklyDays, Monthly
}
```

- [ ] **Step 3: Agregar la seccion "Repetir" en el formulario**

La seccion "Hora (opcional)" queda intacta. Se inserta la nueva seccion "Repetir" justo despues, entre el cierre del `Column` de Hora y el `if (isPastSelected)`. Reemplazar:

```kotlin
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
```

por:

```kotlin
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
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(layout.width(8.dp, 6.dp)),
                        ) {
                            RecurrenceOptionChip(
                                text = "Ninguna",
                                selected = selectedRecurrence == RecurrenceOption.None,
                                onClick = { selectedRecurrence = RecurrenceOption.None },
                            )
                            RecurrenceOptionChip(
                                text = "Diaria",
                                selected = selectedRecurrence == RecurrenceOption.Daily,
                                onClick = { selectedRecurrence = RecurrenceOption.Daily },
                            )
                            RecurrenceOptionChip(
                                text = "Dias de la semana",
                                selected = selectedRecurrence == RecurrenceOption.WeeklyDays,
                                onClick = { selectedRecurrence = RecurrenceOption.WeeklyDays },
                            )
                            RecurrenceOptionChip(
                                text = "Mensual",
                                selected = selectedRecurrence == RecurrenceOption.Monthly,
                                onClick = { selectedRecurrence = RecurrenceOption.Monthly },
                            )
                        }
                        if (selectedRecurrence == RecurrenceOption.WeeklyDays) {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(layout.width(6.dp, 5.dp)),
                            ) {
                                weekDayOptions().forEach { (day, label) ->
                                    val selected = selectedWeekDays.contains(day)
                                    RecurrenceOptionChip(
                                        text = label,
                                        selected = selected,
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
                                text = "Dia $monthDay de cada mes",
                                style = MaterialTheme.typography.bodyMedium,
                                color = GlassTheme.tokens.textSecondary,
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
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
```

- [ ] **Step 4: Reemplazar el `onClick` del boton "Guardar" para bifurcar a `onSaveRecurring` cuando corresponda**

Reemplazar:

```kotlin
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
```

por:

```kotlin
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
                                    if (selectedRecurrence == RecurrenceOption.WeeklyDays && selectedWeekDays.isEmpty()) {
                                        errorText = "Elegi al menos un dia de la semana"
                                        return@GlassActionButton
                                    }

                                    val chosenLabels = labels.filter { selectedLabelIds.contains(it.id) }
                                    val draft = TaskDraft(
                                        title = trimmedTitle,
                                        details = details.trim().ifBlank { null },
                                        time = selectedTime,
                                        labels = chosenLabels,
                                    )
                                    val rule = when (selectedRecurrence) {
                                        RecurrenceOption.None -> null
                                        RecurrenceOption.Daily -> RecurrenceRule.Daily
                                        RecurrenceOption.WeeklyDays -> RecurrenceRule.WeeklyDays(selectedWeekDays.toSet())
                                        RecurrenceOption.Monthly -> RecurrenceRule.Monthly(monthDay)
                                    }
                                    scope.launch {
                                        isSaving = true
                                        val result = if (rule != null) {
                                            onSaveRecurring(selectedDate, draft, rule)
                                        } else {
                                            onSave(selectedDate, draft)
                                        }
                                        isSaving = false
                                        if (result.success) {
                                            onDismiss()
                                        } else {
                                            errorText = result.errorMessage ?: "No se pudo guardar"
                                        }
                                    }
                                },
```

- [ ] **Step 5: Agregar los composables auxiliares `RecurrenceOptionChip` y `weekDayOptions`**

Agregar al final del archivo (despues de la ultima funcion, p. ej. despues de `currentTime`):

```kotlin
@Composable
private fun RecurrenceOptionChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val layout = AppLayout.metrics
    GlassSurface(
        shape = RoundedCornerShape(layout.size(14.dp, 12.dp)),
        tint = if (selected) GlassTheme.tokens.accentOnLight else GlassTheme.tokens.glassFill,
        modifier = Modifier
            .clip(RoundedCornerShape(layout.size(14.dp, 12.dp)))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                onClick = onClick,
            ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) GlassTheme.tokens.onError else GlassTheme.tokens.textPrimary,
            modifier = Modifier.padding(
                horizontal = layout.width(12.dp, 10.dp),
                vertical = layout.height(8.dp, 7.dp),
            ),
        )
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
```

Agregar los imports necesarios (verificar cuales ya existen en el archivo antes de duplicar):

```kotlin
import androidx.compose.foundation.LocalIndication
import kotlinx.datetime.DayOfWeek
```

- [ ] **Step 6: Wirear `onSaveRecurring` desde `AgendaScreen.kt`**

Buscar la llamada a `NewTaskSheet(...)` en `AgendaScreen.kt` y reemplazar:

```kotlin
        if (showTaskSheet) {
            NewTaskSheet(
                date = selectedDate,
                labels = labels,
                onCreateLabel = onCreateLabel,
                onDismiss = { showTaskSheet = false },
                onSave = { targetDate, draft ->
                    controller.saveTask(targetDate, draft).also { result ->
                        if (result.success) {
                            showTaskSheet = false
                        }
                    }
                },
            )
        }
```

por:

```kotlin
        if (showTaskSheet) {
            NewTaskSheet(
                date = selectedDate,
                labels = labels,
                onCreateLabel = onCreateLabel,
                onDismiss = { showTaskSheet = false },
                onSave = { targetDate, draft ->
                    controller.saveTask(targetDate, draft).also { result ->
                        if (result.success) {
                            showTaskSheet = false
                        }
                    }
                },
                onSaveRecurring = { targetDate, draft, rule ->
                    controller.saveRecurringTask(targetDate, draft, rule).also { result ->
                        if (result.success) {
                            showTaskSheet = false
                        }
                    }
                },
            )
        }
```

- [ ] **Step 7: Compilar para verificar**

Run: `.\gradlew.bat :composeApp:compileCommonMainKotlinMetadata --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/presentation/view/AgendaOverlays.kt composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/presentation/view/AgendaScreen.kt
git commit -m "Agregar selector de repeticion al crear una tarea"
```

---

### Task 8: Indicador visual de tarea recurrente

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/presentation/view/AgendaDayComponents.kt`

**Interfaces:**
- Consumes: `TaskItem.seriesId` de la Task 2.
- Produces: nada consumido por otras tareas (cambio puramente visual).

- [ ] **Step 1: Agregar el icono de repeticion junto al `TimeChip`**

Reemplazar:

```kotlin
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimeChip(startTime = task.time, endTime = task.endTime)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(layout.width(6.dp, 4.dp)),
                ) {
                    task.labels.forEach { label ->
                        LabelChip(label = label)
                    }
                }
            }
```

por:

```kotlin
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
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(layout.width(6.dp, 4.dp)),
                ) {
                    task.labels.forEach { label ->
                        LabelChip(label = label)
                    }
                }
            }
```

Agregar el import (verificar que no exista ya en el archivo):

```kotlin
import androidx.compose.material.icons.rounded.Repeat
```

- [ ] **Step 2: Compilar para verificar**

Run: `.\gradlew.bat :composeApp:compileCommonMainKotlinMetadata --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/presentation/view/AgendaDayComponents.kt
git commit -m "Mostrar icono de repeticion en tareas de una serie recurrente"
```

---

### Task 9: Gestionar series recurrentes desde Ajustes

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/settings/presentation/view/SettingsScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/app/navigation/AppNavHost.kt`

**Interfaces:**
- Consumes: `TaskSeries`, `AppServices.taskSeriesRepository` de la Task 3.
- Produces: nada consumido por otras tareas.

- [ ] **Step 1: Agregar parametros `seriesList`/`onDeleteSeries` a `SettingsScreen`**

Reemplazar la firma:

```kotlin
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    controller: SettingsController,
    onDeleteAllNotes: suspend () -> Boolean,
    onDeleteAllLabels: suspend () -> Boolean,
    modifier: Modifier = Modifier,
) {
```

por:

```kotlin
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    controller: SettingsController,
    onDeleteAllNotes: suspend () -> Boolean,
    onDeleteAllLabels: suspend () -> Boolean,
    seriesList: List<TaskSeries>,
    onDeleteSeries: suspend (TaskSeries) -> Boolean,
    modifier: Modifier = Modifier,
) {
```

Agregar el import:

```kotlin
import androidx.compose.foundation.layout.weight
import com.franciscor.agendnote.core.model.TaskSeries
import com.franciscor.agendnote.feature.agenda.domain.RecurrenceRule
import kotlinx.coroutines.launch
```

- [ ] **Step 2: Agregar la seccion "Tareas recurrentes" y el estado de confirmacion de borrado**

Reemplazar el `val layout = AppLayout.metrics` inicial (justo despues de la firma de la funcion):

```kotlin
    val layout = AppLayout.metrics
    val contentInset = layout.width(24.dp, 20.dp)
    val uiState = viewModel.uiState
    val isEditingEnabled = uiState.isRemoteAvailable
```

por:

```kotlin
    val layout = AppLayout.metrics
    val contentInset = layout.width(24.dp, 20.dp)
    val uiState = viewModel.uiState
    val isEditingEnabled = uiState.isRemoteAvailable
    var seriesPendingDelete by remember { mutableStateOf<TaskSeries?>(null) }
    val scope = rememberCoroutineScope()
```

Agregar los imports:

```kotlin
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
```

(verificar cuales de estos tres ya estan importados antes de duplicar).

Insertar una nueva `item { ... }` en la `LazyColumn`, justo despues del bloque `item { ... }` de "Acciones" y antes del cierre `}` de la `LazyColumn`:

```kotlin
        if (seriesList.isNotEmpty()) {
            item {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(layout.size(24.dp, 20.dp)),
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.padding(layout.size(16.dp, 14.dp)),
                        verticalArrangement = Arrangement.spacedBy(layout.height(12.dp, 10.dp)),
                    ) {
                        Text(
                            text = "Tareas recurrentes",
                            style = MaterialTheme.typography.titleMedium,
                            color = GlassTheme.tokens.textPrimary,
                        )
                        seriesList.forEach { series ->
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = series.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = GlassTheme.tokens.textPrimary,
                                    )
                                    Text(
                                        text = describeRecurrence(series.rule),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GlassTheme.tokens.textSecondary,
                                    )
                                }
                                GlassActionButton(
                                    text = "Borrar",
                                    enabled = isEditingEnabled,
                                    tint = Color(0xFFE06B6B),
                                    onClick = { seriesPendingDelete = series },
                                )
                            }
                        }
                    }
                }
            }
        }
```

- [ ] **Step 3: Agregar el dialogo de confirmacion de borrado de serie y la funcion `describeRecurrence`**

Agregar antes del cierre `}` de la funcion `SettingsScreen` (despues del `GlassConfirmDialog` ya existente para `pendingBulkAction`):

```kotlin
    seriesPendingDelete?.let { series ->
        GlassConfirmDialog(
            visible = true,
            title = "Borrar serie recurrente?",
            message = "Se eliminaran las apariciones futuras de \"${series.title}\" que no esten completadas. Las pasadas se conservan.",
            confirmText = "Borrar",
            onConfirm = {
                seriesPendingDelete = null
                scope.launch { onDeleteSeries(series) }
            },
            onDismiss = { seriesPendingDelete = null },
        )
    }
```

Agregar la funcion `describeRecurrence` al final del archivo:

```kotlin
private fun describeRecurrence(rule: RecurrenceRule): String {
    return when (rule) {
        is RecurrenceRule.Daily -> "Todos los dias"
        is RecurrenceRule.WeeklyDays -> {
            val names = rule.days.sortedBy { it.isoDayNumber }.joinToString(", ") { day ->
                when (day) {
                    DayOfWeek.MONDAY -> "lunes"
                    DayOfWeek.TUESDAY -> "martes"
                    DayOfWeek.WEDNESDAY -> "miercoles"
                    DayOfWeek.THURSDAY -> "jueves"
                    DayOfWeek.FRIDAY -> "viernes"
                    DayOfWeek.SATURDAY -> "sabado"
                    DayOfWeek.SUNDAY -> "domingo"
                    else -> day.name.lowercase()
                }
            }
            "Cada $names"
        }
        is RecurrenceRule.Monthly -> "El dia ${rule.dayOfMonth} de cada mes"
    }
}
```

Agregar el import:

```kotlin
import kotlinx.datetime.DayOfWeek
```

- [ ] **Step 4: Wirear `seriesList`/`onDeleteSeries` desde `AppNavHost.kt`**

Reemplazar:

```kotlin
    LaunchedEffect(agendaController) {
        val taskSeriesRepository = AppServices.taskSeriesRepository
        val agendaTaskRepository = AppServices.agendaTaskRepository
        if (taskSeriesRepository != null && agendaTaskRepository != null) {
            SeriesMaterializer(taskSeriesRepository, agendaTaskRepository)
                .materializeAll(agendaViewModel.today())
            agendaController.handleAsync(AgendaAction.RefreshSelectedDate)
        }
    }
```

por:

```kotlin
    var recurringSeries by remember { mutableStateOf<List<TaskSeries>>(emptyList()) }

    suspend fun refreshRecurringSeries() {
        recurringSeries = AppServices.taskSeriesRepository?.fetchActiveSeries() ?: emptyList()
    }

    LaunchedEffect(agendaController) {
        val taskSeriesRepository = AppServices.taskSeriesRepository
        val agendaTaskRepository = AppServices.agendaTaskRepository
        if (taskSeriesRepository != null && agendaTaskRepository != null) {
            SeriesMaterializer(taskSeriesRepository, agendaTaskRepository)
                .materializeAll(agendaViewModel.today())
            agendaController.handleAsync(AgendaAction.RefreshSelectedDate)
        }
        refreshRecurringSeries()
    }
```

Agregar el import:

```kotlin
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.franciscor.agendnote.core.model.TaskSeries
```

(verificar cuales ya estan presentes antes de duplicar).

Reemplazar la llamada a `SettingsRoute` dentro del `NavHost`:

```kotlin
                        composable(AppRoute.Settings.route) {
                            SettingsRoute(
                                settingsViewModel = settingsViewModel,
                                settingsController = settingsController,
                                onDeleteAllNotes = { agendaController.deleteAllTasks() },
                                onDeleteAllLabels = {
                                    val success = labelsController.deleteAllLabels()
                                    if (success) {
                                        agendaController.clearLabelsFromTasks()
                                    }
                                    success
                                },
                            )
                        }
```

por:

```kotlin
                        composable(AppRoute.Settings.route) {
                            SettingsRoute(
                                settingsViewModel = settingsViewModel,
                                settingsController = settingsController,
                                onDeleteAllNotes = { agendaController.deleteAllTasks() },
                                onDeleteAllLabels = {
                                    val success = labelsController.deleteAllLabels()
                                    if (success) {
                                        agendaController.clearLabelsFromTasks()
                                    }
                                    success
                                },
                                seriesList = recurringSeries,
                                onDeleteSeries = { series ->
                                    val success = AppServices.taskSeriesRepository?.deleteSeries(series.id) ?: false
                                    if (success) {
                                        refreshRecurringSeries()
                                        agendaController.handleAsync(AgendaAction.RefreshSelectedDate)
                                    }
                                    success
                                },
                            )
                        }
```

Reemplazar la firma y el cuerpo de `SettingsRoute`:

```kotlin
@Composable
private fun SettingsRoute(
    settingsViewModel: SettingsViewModel,
    settingsController: SettingsController,
    onDeleteAllNotes: suspend () -> Boolean,
    onDeleteAllLabels: suspend () -> Boolean,
) {
    SettingsScreen(
        viewModel = settingsViewModel,
        controller = settingsController,
        onDeleteAllNotes = onDeleteAllNotes,
        onDeleteAllLabels = onDeleteAllLabels,
        modifier = Modifier.fillMaxSize(),
    )
}
```

por:

```kotlin
@Composable
private fun SettingsRoute(
    settingsViewModel: SettingsViewModel,
    settingsController: SettingsController,
    onDeleteAllNotes: suspend () -> Boolean,
    onDeleteAllLabels: suspend () -> Boolean,
    seriesList: List<TaskSeries>,
    onDeleteSeries: suspend (TaskSeries) -> Boolean,
) {
    SettingsScreen(
        viewModel = settingsViewModel,
        controller = settingsController,
        onDeleteAllNotes = onDeleteAllNotes,
        onDeleteAllLabels = onDeleteAllLabels,
        seriesList = seriesList,
        onDeleteSeries = onDeleteSeries,
        modifier = Modifier.fillMaxSize(),
    )
}
```

- [ ] **Step 5: Compilar para verificar**

Run: `.\gradlew.bat :composeApp:compileCommonMainKotlinMetadata --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/settings/presentation/view/SettingsScreen.kt composeApp/src/commonMain/kotlin/com/franciscor/agendnote/app/navigation/AppNavHost.kt
git commit -m "Agregar gestion de series recurrentes en Ajustes"
```

---

### Task 10: Artefactos de backend (SQL + edge function, sin desplegar)

**Files:**
- Create: `supabase/migrations/20260724_task_series.sql`
- Create: `supabase/functions/api-task-series/index.ts`
- Modify: `supabase/functions/api-tasks/index.ts`

**Interfaces:**
- Ninguna — estos archivos no se compilan ni se ejecutan como parte de la app cliente; quedan listos para aplicar/desplegar manualmente o desde una sesion con el MCP de Supabase conectado.

- [ ] **Step 1: Crear la migracion SQL**

```sql
-- Tareas recurrentes: tabla de series + columna de vinculo en tasks.

create table if not exists task_series (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  body text,
  time time,
  recurrence_type text not null check (recurrence_type in ('daily', 'weekly_days', 'monthly')),
  days_of_week smallint[],
  day_of_month smallint check (day_of_month between 1 and 31),
  label_ids uuid[] not null default '{}',
  start_date date not null,
  is_active boolean not null default true,
  materialized_until date not null,
  created_at timestamptz not null default now()
);

alter table tasks
  add column if not exists series_id uuid references task_series(id) on delete set null;

create index if not exists idx_tasks_series_id on tasks(series_id);
create index if not exists idx_task_series_is_active on task_series(is_active);

alter table task_series enable row level security;
-- Sin policies para anon/auth, igual que el resto de las tablas: todo el acceso pasa
-- por Edge Functions con la service role key (ver supabase/policies.sql).
```

- [ ] **Step 2: Crear el edge function `api-task-series`**

```typescript
import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { corsHeaders } from "../_shared/cors.ts";
import { errorResponse, jsonResponse } from "../_shared/response.ts";
import { requireAppSecret } from "../_shared/auth.ts";

const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? Deno.env.get("SB_URL");
const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? Deno.env.get("SB_SERVICE_ROLE_KEY");
const SERIES_SELECT = "id,title,body,time,recurrence_type,days_of_week,day_of_month,label_ids,start_date,is_active,materialized_until,created_at";

if (!supabaseUrl || !serviceKey) {
  throw new Error("Missing SUPABASE_URL/SB_URL or SUPABASE_SERVICE_ROLE_KEY/SB_SERVICE_ROLE_KEY");
}

const supabase = createClient(supabaseUrl, serviceKey, {
  auth: { persistSession: false },
});

function normalizeOptionalString(value: unknown) {
  if (value == null) return null;
  const trimmed = String(value).trim();
  return trimmed.length > 0 ? trimmed : null;
}

function normalizeRequiredString(value: unknown, field: string) {
  const normalized = normalizeOptionalString(value);
  if (!normalized) throw new Error(`${field} is required`);
  return normalized;
}

function normalizeDate(value: unknown, field: string) {
  const trimmed = String(value ?? "").trim();
  if (!/^\d{4}-\d{2}-\d{2}$/.test(trimmed)) throw new Error(`${field} must be YYYY-MM-DD`);
  return trimmed;
}

function normalizeStringArray(value: unknown) {
  if (!Array.isArray(value)) return [];
  return value.map((item) => String(item).trim()).filter((item) => item.length > 0);
}

/**
 * El cliente calcula ocurrencias desde `materialized_until + 1 dia`. Si inicializaramos
 * `materialized_until` en el propio `start_date`, la primera materializacion saltearia
 * el start_date aunque coincida con la regla. Por eso arranca un dia antes.
 */
function dayBefore(dateStr: string): string {
  const date = new Date(`${dateStr}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() - 1);
  return date.toISOString().slice(0, 10);
}

function buildInsertPayload(body: Record<string, unknown>) {
  const recurrenceType = normalizeRequiredString(body.recurrence_type, "recurrence_type");
  if (!["daily", "weekly_days", "monthly"].includes(recurrenceType)) {
    throw new Error("recurrence_type must be daily, weekly_days, or monthly");
  }
  const startDate = normalizeDate(body.start_date, "start_date");
  return {
    title: normalizeRequiredString(body.title, "title"),
    body: normalizeOptionalString(body.body),
    time: normalizeOptionalString(body.time),
    recurrence_type: recurrenceType,
    days_of_week: Array.isArray(body.days_of_week) ? body.days_of_week.map(Number) : null,
    day_of_month: body.day_of_month != null ? Number(body.day_of_month) : null,
    label_ids: normalizeStringArray(body.label_ids),
    start_date: startDate,
    is_active: true,
    materialized_until: dayBefore(startDate),
  };
}

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  const authError = requireAppSecret(req);
  if (authError) return authError;

  try {
    if (req.method === "GET") {
      const { data, error } = await supabase
        .from("task_series")
        .select(SERIES_SELECT)
        .eq("is_active", true)
        .order("created_at", { ascending: true });

      if (error) return errorResponse(error.message, 500);
      return jsonResponse({ series: data ?? [] });
    }

    if (req.method === "POST") {
      const body = await req.json();
      const insertPayload = buildInsertPayload(body);

      const { data, error } = await supabase
        .from("task_series")
        .insert(insertPayload)
        .select(SERIES_SELECT)
        .single();

      if (error) return errorResponse(error.message, 500);
      return jsonResponse({ series: data }, 201);
    }

    if (req.method === "PATCH") {
      const body = await req.json();
      const id = normalizeRequiredString(body?.id, "id");
      const updates: Record<string, unknown> = {};

      if (body?.materialized_until != null) {
        updates.materialized_until = normalizeDate(body.materialized_until, "materialized_until");
      }
      if (body?.is_active != null) {
        updates.is_active = Boolean(body.is_active);
      }

      const { data, error } = await supabase
        .from("task_series")
        .update(updates)
        .eq("id", id)
        .select(SERIES_SELECT)
        .single();

      if (error) return errorResponse(error.message, 500);
      return jsonResponse({ series: data });
    }

    if (req.method === "DELETE") {
      const url = new URL(req.url);
      const id = url.searchParams.get("id")?.trim();
      if (!id) return errorResponse("id is required", 400);

      const today = new Date().toISOString().slice(0, 10);

      const { error: deleteTasksError } = await supabase
        .from("tasks")
        .delete()
        .eq("series_id", id)
        .eq("is_done", false)
        .gte("day", today);

      if (deleteTasksError) return errorResponse(deleteTasksError.message, 500);

      const { error } = await supabase
        .from("task_series")
        .delete()
        .eq("id", id);

      if (error) return errorResponse(error.message, 500);
      return jsonResponse({ success: true });
    }

    return errorResponse("method not allowed", 405);
  } catch (error) {
    const message = error instanceof Error ? error.message : "unknown error";
    return errorResponse(message, 500);
  }
});
```

- [ ] **Step 3: Agregar `series_id` a `api-tasks/index.ts`**

Reemplazar:

```typescript
const TASK_SELECT = "id,title,body,day,due_at,slot_end_at,is_done,order_index,created_at,updated_at,notified_at,source,booking_status,appointment_id,client_name,client_email,client_phone";
```

por:

```typescript
const TASK_SELECT = "id,title,body,day,due_at,slot_end_at,is_done,order_index,created_at,updated_at,notified_at,source,booking_status,appointment_id,client_name,client_email,client_phone,series_id";
```

Reemplazar:

```typescript
function buildInsertPayload(body: Record<string, unknown>) {
  const appointmentId = normalizeOptionalString(body.appointment_id);
  return {
    title: normalizeRequiredString(body.title, "title"),
    body: normalizeOptionalString(body.body),
    day: normalizeRequiredDay(body.day),
    due_at: normalizeOptionalString(body.due_at),
    slot_end_at: normalizeOptionalString(body.slot_end_at),
    is_done: Boolean(body.is_done ?? false),
    order_index: Number(body.order_index ?? 0),
    source: resolveSource(body, appointmentId) ?? "manual",
    booking_status: normalizeOptionalString(body.booking_status),
    appointment_id: appointmentId,
    client_name: normalizeOptionalString(body.client_name),
    client_email: normalizeOptionalString(body.client_email),
    client_phone: normalizeOptionalString(body.client_phone),
  };
}
```

por:

```typescript
function buildInsertPayload(body: Record<string, unknown>) {
  const appointmentId = normalizeOptionalString(body.appointment_id);
  return {
    title: normalizeRequiredString(body.title, "title"),
    body: normalizeOptionalString(body.body),
    day: normalizeRequiredDay(body.day),
    due_at: normalizeOptionalString(body.due_at),
    slot_end_at: normalizeOptionalString(body.slot_end_at),
    is_done: Boolean(body.is_done ?? false),
    order_index: Number(body.order_index ?? 0),
    source: resolveSource(body, appointmentId) ?? "manual",
    booking_status: normalizeOptionalString(body.booking_status),
    appointment_id: appointmentId,
    client_name: normalizeOptionalString(body.client_name),
    client_email: normalizeOptionalString(body.client_email),
    client_phone: normalizeOptionalString(body.client_phone),
    series_id: normalizeOptionalString(body.series_id),
  };
}
```

- [ ] **Step 4: No aplicar/desplegar — solo verificar que los archivos quedaron bien formados**

Este paso no tiene comando de build (son archivos SQL/TypeScript fuera del proyecto Kotlin, no se compilan como parte de `./gradlew`). Releer los 3 archivos completos con la herramienta de lectura para confirmar que no quedaron placeholders ni sintaxis rota antes de commitear.

- [ ] **Step 5: Commit**

```bash
git add supabase/migrations/20260724_task_series.sql supabase/functions/api-task-series/index.ts supabase/functions/api-tasks/index.ts
git commit -m "Preparar migracion SQL y edge function de series recurrentes (sin desplegar)"
```

---

## Verificación final (tras las 10 tareas)

- [ ] Compilar los 3 targets: `.\gradlew.bat :composeApp:compileCommonMainKotlinMetadata --console=plain`, `.\gradlew.bat :composeApp:compileDebugKotlinAndroid --console=plain`, `.\gradlew.bat :composeApp:compileKotlinIosSimulatorArm64 --console=plain` — todos `BUILD SUCCESSFUL`.
- [ ] Correr el test suite completo: `.\gradlew.bat :composeApp:testDebugUnitTest --console=plain` — sin fallos, incluyendo los 11 tests nuevos (`RecurrenceRuleTest` + `SeriesMaterializerTest`).
- [ ] Confirmar que ningún test/archivo existente se rompió (grep rápido por `AgendaViewModel(` en `commonTest` para verificar que las construcciones sin `taskSeriesRepository` siguen compilando gracias al valor por defecto `null`).

## Fuera de alcance

- Aplicar la migración SQL / desplegar el edge function a Supabase real.
- Pausar/reanudar una serie sin borrarla, editar la plantilla de una serie ya creada.
- Materialización server-side con cron.
- Resolver `TaskSeries.labelIds` a objetos `LabelTag` completos antes de materializar (las tareas materializadas hoy no llevan las etiquetas de la plantilla — ver nota en Task 4, Step 3).
