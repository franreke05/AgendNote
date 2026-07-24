# Quitar el espejo de reservas (cliente) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminar del código cliente de AgendNote (Kotlin Multiplatform) toda la lógica y UI específica del espejo de reservas del portfolio externo (`task.source == "portfolio_booking"`), dejando la app como una agenda 100% personal.

**Architecture:** Es un refactor de eliminación en tres capas coordinadas: (1) la UI deja de leer/ramificar sobre los campos de booking, (2) el modelo de dominio y los DTOs de red dejan de tener esos campos, (3) limpieza de comentarios/copy/docs que referencian bookings. El orden importa: la UI se limpia primero (mientras el modelo todavía tiene los campos, sin romper nada), y solo después se borran los campos del modelo — así cada tarea deja el proyecto compilando.

**Tech Stack:** Kotlin Multiplatform (Compose Multiplatform), Ktor client, kotlinx.serialization, Gradle.

## Global Constraints

- No se toca el backend de Supabase (schema, políticas, edge functions) en este plan — queda para cuando se conecte el MCP de Supabase (Fase 3 del roadmap de auditoría). El cliente simplemente deja de enviar/leer los campos de booking; son opcionales en el contrato actual, así que esto es compatible con el backend tal cual está.
- No usar la herramienta Bash dentro de subagentes en background para comandos de Gradle — en esta misma sesión, varios subagentes se quedaron colgados 600s ejecutando Gradle en background sin supervisión. Si se ejecuta este plan con subagentes, los comandos de verificación (`./gradlew.bat ...`) deben correr con salida visible/bloqueante, no en background silencioso.
- Los pasos de "escribir el test que falla" de esta plantilla no aplican literalmente aquí: este plan elimina campos y ramas de comportamiento existentes, no añade comportamiento nuevo. La verificación de cada tarea es "compila y los tests existentes siguen pasando" (regresión), no un test nuevo escrito primero.
- Mantener el estilo de mensajes de commit ya usado en el repo (frases cortas en modo imperativo, sin prefijos tipo `feat:`/`fix:` — ver `git log --oneline`).

---

### Task 1: Quitar la ramificación de booking en la capa de UI de Agenda

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/presentation/view/AgendaDayComponents.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/presentation/view/AgendaOverlays.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/presentation/view/AgendaScreen.kt`

**Interfaces:**
- Consumes: `TaskItem` (de `core/model/AgendaModels.kt`) — en esta tarea el modelo TODAVÍA tiene los campos `source`/`bookingStatus`/`appointmentId`/`clientName`/`clientEmail`/`clientPhone` (se borran en la Task 2); esta tarea simplemente deja de leerlos.
- Produces: `DayAgenda(...)` sin los parámetros `onRequestDeleteBooking`/`onRequestToggleBooking`; `ConfirmDeleteDialog(task, onConfirm, onDismiss)` con un único mensaje genérico. Estas son las firmas que la Task 2 y verificaciones posteriores asumen.

- [ ] **Step 1: Editar `AgendaDayComponents.kt` — firma de `DayAgenda`**

Reemplazar:

```kotlin
@Composable
internal fun DayAgenda(
    selectedDate: LocalDate,
    tasks: List<TaskItem>,
    hasSourceTasks: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    searchQuery: String,
    onToggleDone: (TaskItem, Boolean) -> Unit,
    onRequestDelete: (TaskItem) -> Unit,
    // Tasks mirrored from the external booking system (task.source == "portfolio_booking")
    // must not be deleted/toggled directly: the containing screen should show a distinct
    // confirmation before proceeding. Defaults delegate to the normal action so existing
    // call sites that don't pass these explicitly keep compiling and behaving as before.
    // TODO(AgendaScreen.kt): pass real implementations that show a GlassConfirmDialog with
    // booking-specific copy (e.g. "Esta cita proviene de una reserva. ¿Eliminarla igualmente?")
    // instead of relying on these defaults.
    onRequestDeleteBooking: (TaskItem) -> Unit = onRequestDelete,
    onRequestToggleBooking: (TaskItem, Boolean) -> Unit = onToggleDone,
    modifier: Modifier = Modifier,
) {
```

por:

```kotlin
@Composable
internal fun DayAgenda(
    selectedDate: LocalDate,
    tasks: List<TaskItem>,
    hasSourceTasks: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    searchQuery: String,
    onToggleDone: (TaskItem, Boolean) -> Unit,
    onRequestDelete: (TaskItem) -> Unit,
    modifier: Modifier = Modifier,
) {
```

- [ ] **Step 2: Editar `AgendaDayComponents.kt` — bloque `items(tasks, ...)`**

Reemplazar:

```kotlin
        } else {
            items(tasks, key = { it.id }) { task ->
                val isBookingTask = task.source == "portfolio_booking"
                SwipeableTaskCard(
                    task = task,
                    onRequestDelete = {
                        if (isBookingTask) onRequestDeleteBooking(task) else onRequestDelete(task)
                    },
                    onToggleDone = { done ->
                        if (isBookingTask) onRequestToggleBooking(task, done) else onToggleDone(task, done)
                    },
                )
            }
        }
```

por:

```kotlin
        } else {
            items(tasks, key = { it.id }) { task ->
                SwipeableTaskCard(
                    task = task,
                    onRequestDelete = { onRequestDelete(task) },
                    onToggleDone = { done -> onToggleDone(task, done) },
                )
            }
        }
```

- [ ] **Step 3: Editar `AgendaDayComponents.kt` — composable `TaskCard`**

Reemplazar el composable completo:

```kotlin
@Composable
private fun TaskCard(
    task: TaskItem,
    onToggleDone: (Boolean) -> Unit,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = AppLayout.metrics
    val alpha = if (task.isDone) 0.6f else 1f
    val isPortfolioBooking = task.source == "portfolio_booking"
    val primaryTitle = if (isPortfolioBooking) {
        task.clientName?.takeIf { it.isNotBlank() } ?: task.title
    } else {
        task.title
    }
    val secondaryTitle = if (isPortfolioBooking) {
        task.title.takeIf { it.isNotBlank() && it != primaryTitle }
    } else {
        null
    }
    val clientLine = listOfNotNull(
        task.clientEmail?.takeIf { it.isNotBlank() },
        task.clientPhone?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")

    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = layout.height(112.dp, 96.dp))
            .alpha(alpha),
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

            if (isPortfolioBooking) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(layout.width(8.dp, 6.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BookingMetaChip(
                        text = "Cita cliente",
                        color = Color(0xFF3DA9FC),
                    )
                    BookingMetaChip(
                        text = bookingStatusLabel(task.bookingStatus),
                        color = bookingStatusColor(task.bookingStatus),
                    )
                }
                if (clientLine.isNotBlank()) {
                    Text(
                        text = clientLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassTheme.tokens.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Text(
                text = primaryTitle,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = layout.text(19.sp, 17.sp),
                    lineHeight = layout.text(21.sp, 19.sp),
                ),
                color = GlassTheme.tokens.textPrimary,
                maxLines = if (isPortfolioBooking) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (!secondaryTitle.isNullOrBlank()) {
                Text(
                    text = secondaryTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassTheme.tokens.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (!task.details.isNullOrBlank()) {
                Text(
                    text = task.details,
                    style = MaterialTheme.typography.bodyLarge,
                    color = GlassTheme.tokens.textSecondary,
                    maxLines = if (isPortfolioBooking) 3 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Always-visible, non-gestural alternative to the swipe-to-complete /
            // swipe-to-delete actions above, for screen reader users and anyone who
            // cannot reliably perform a drag gesture. Mirrors the same callbacks the
            // swipe gestures use, so booking-task guarding (see DayAgenda) applies here too.
            TaskCardActions(
                isDone = task.isDone,
                onToggleDone = onToggleDone,
                onRequestDelete = onRequestDelete,
            )
        }
    }
}
```

por:

```kotlin
@Composable
private fun TaskCard(
    task: TaskItem,
    onToggleDone: (Boolean) -> Unit,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = AppLayout.metrics
    val alpha = if (task.isDone) 0.6f else 1f

    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = layout.height(112.dp, 96.dp))
            .alpha(alpha),
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
```

- [ ] **Step 4: Editar `AgendaDayComponents.kt` — borrar `BookingMetaChip`**

Buscar y borrar por completo este composable (queda sin ningún caller tras el Step 3):

```kotlin
@Composable
private fun BookingMetaChip(
    text: String,
    color: Color,
) {
    val layout = AppLayout.metrics
    GlassSurface(
        shape = RoundedCornerShape(layout.size(14.dp, 12.dp)),
        tint = color.copy(alpha = 0.18f),
        strokeColor = color.copy(alpha = 0.42f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = GlassTheme.tokens.textPrimary,
            modifier = Modifier.padding(
                horizontal = layout.width(10.dp, 8.dp),
                vertical = layout.height(6.dp, 6.dp),
            ),
        )
    }
}
```

- [ ] **Step 5: Editar `AgendaDayComponents.kt` — borrar `bookingStatusLabel`/`bookingStatusColor`**

Buscar y borrar por completo (quedan sin ningún caller tras el Step 3):

```kotlin
private fun bookingStatusLabel(status: String?): String = when (status?.lowercase()) {
    "confirmed" -> "Confirmada"
    "cancelled", "canceled" -> "Cancelada"
    else -> "Pendiente"
}

private fun bookingStatusColor(status: String?): Color = when (status?.lowercase()) {
    "confirmed" -> Color(0xFF39D98A)
    "cancelled", "canceled" -> Color(0xFFE06B6B)
    else -> Color(0xFFFFC857)
}
```

- [ ] **Step 6: Editar `AgendaDayComponents.kt` — simplificar `filterTasks`**

Reemplazar:

```kotlin
internal fun filterTasks(tasks: List<TaskItem>, query: String): List<TaskItem> {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return tasks
    val needle = trimmed.lowercase()
    return tasks.filter { task ->
        task.title.lowercase().contains(needle) ||
            (task.details?.lowercase()?.contains(needle) == true) ||
            (task.clientName?.lowercase()?.contains(needle) == true) ||
            (task.clientEmail?.lowercase()?.contains(needle) == true) ||
            (task.clientPhone?.lowercase()?.contains(needle) == true) ||
            (task.bookingStatus?.lowercase()?.contains(needle) == true) ||
            task.labels.any { it.name.lowercase().contains(needle) }
    }
}
```

por:

```kotlin
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
```

- [ ] **Step 7: Editar `AgendaOverlays.kt` — simplificar `ConfirmDeleteDialog`**

Reemplazar:

```kotlin
@Composable
internal fun ConfirmDeleteDialog(
    task: TaskItem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isSyncedBooking = task.source == "portfolio_booking"
    GlassConfirmDialog(
        visible = true,
        title = "Eliminar tarea?",
        message = if (isSyncedBooking) {
            "\"${task.title}\" es una cita sincronizada desde tu sistema de reservas. " +
                "Eliminarla aqui no cancela la reserva externa."
        } else {
            "Se borrara \"${task.title}\""
        },
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        confirmText = "Eliminar",
    )
}
```

por:

```kotlin
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
```

- [ ] **Step 8: Editar `AgendaScreen.kt` — quitar argumentos de booking en la llamada a `DayAgenda`**

Reemplazar:

```kotlin
            DayAgenda(
                selectedDate = selectedDate,
                tasks = filteredTasks,
                hasSourceTasks = dayUiState.hasCachedTasks,
                isLoading = dayUiState.isLoading,
                errorMessage = dayUiState.errorMessage,
                searchQuery = searchQuery,
                onToggleDone = { task, done ->
                    controller.toggleTaskDoneAsync(selectedDate, task, done)
                },
                onRequestDelete = { task ->
                    pendingDeleteTaskId = task.id
                },
                onRequestDeleteBooking = { task ->
                    // Reuse the same pending-delete state: ConfirmDeleteDialog already
                    // tailors its message when task.source == "portfolio_booking", so a
                    // second parallel state/dialog would just duplicate that logic.
                    pendingDeleteTaskId = task.id
                },
                onRequestToggleBooking = { task, done ->
                    // Toggling completion on a synced booking is lower-risk than deleting
                    // it (no data loss, easily reversible), so we apply it directly instead
                    // of adding a second confirmation flow. The delete-confirmation fix
                    // above covers the actual data-loss risk.
                    controller.toggleTaskDoneAsync(selectedDate, task, done)
                },
                modifier = Modifier
```

por:

```kotlin
            DayAgenda(
                selectedDate = selectedDate,
                tasks = filteredTasks,
                hasSourceTasks = dayUiState.hasCachedTasks,
                isLoading = dayUiState.isLoading,
                errorMessage = dayUiState.errorMessage,
                searchQuery = searchQuery,
                onToggleDone = { task, done ->
                    controller.toggleTaskDoneAsync(selectedDate, task, done)
                },
                onRequestDelete = { task ->
                    pendingDeleteTaskId = task.id
                },
                modifier = Modifier
```

- [ ] **Step 9: Compilar para verificar que no queda nada roto**

Run: `./gradlew.bat :composeApp:compileCommonMainKotlinMetadata --console=plain` (desde `C:\Users\franr\AgendNote`)
Expected: `BUILD SUCCESSFUL`. El modelo `TaskItem` todavía tiene los campos de booking en este punto (se borran en la Task 2), así que no debe haber ningún error de referencia rota.

- [ ] **Step 10: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/presentation/view/AgendaDayComponents.kt composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/presentation/view/AgendaOverlays.kt composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/presentation/view/AgendaScreen.kt
git commit -m "$(cat <<'EOF'
Quitar la UI de espejo de reservas de la agenda

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Quitar los campos de booking del modelo y de la red

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/core/model/AgendaModels.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/core/network/AgendaDtos.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/data/SupabaseAgendaTaskRepository.kt`

**Interfaces:**
- Consumes: la Task 1 ya dejó de leer estos campos en toda la UI, así que borrarlos aquí no rompe ningún consumidor restante.
- Produces: `TaskItem(id, title, details, time, labels, endTime, isDone)` — sin campos de booking. Cualquier tarea futura del roadmap (recurrencia, calendario, notificaciones) parte de este `TaskItem` limpio.

- [ ] **Step 1: Editar `AgendaModels.kt` — quitar campos de booking de `TaskItem`**

Reemplazar:

```kotlin
@Immutable
data class TaskItem(
    val id: String,
    val title: String,
    val details: String?,
    val time: LocalTime?,
    val labels: List<LabelTag>,
    val endTime: LocalTime? = null,
    val isDone: Boolean = false,
    val source: String? = null,
    val bookingStatus: String? = null,
    val appointmentId: String? = null,
    val clientName: String? = null,
    val clientEmail: String? = null,
    val clientPhone: String? = null,
)
```

por:

```kotlin
@Immutable
data class TaskItem(
    val id: String,
    val title: String,
    val details: String?,
    val time: LocalTime?,
    val labels: List<LabelTag>,
    val endTime: LocalTime? = null,
    val isDone: Boolean = false,
)
```

- [ ] **Step 2: Editar `AgendaDtos.kt` — quitar campos de booking de `TaskDto`**

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
    val source: String? = null,
    val booking_status: String? = null,
    val appointment_id: String? = null,
    val client_name: String? = null,
    val client_email: String? = null,
    val client_phone: String? = null,
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
)
```

- [ ] **Step 3: Editar `AgendaDtos.kt` — quitar campos de booking de `CreateTaskRequest`**

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
    val source: String? = null,
    val booking_status: String? = null,
    val appointment_id: String? = null,
    val client_name: String? = null,
    val client_email: String? = null,
    val client_phone: String? = null,
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
)
```

- [ ] **Step 4: Editar `AgendaDtos.kt` — quitar campos de booking de `UpdateTaskRequest`**

Reemplazar:

```kotlin
@Serializable
data class UpdateTaskRequest(
    val id: String,
    val title: String? = null,
    val body: String? = null,
    val day: String? = null,
    val due_at: String? = null,
    val slot_end_at: String? = null,
    val is_done: Boolean? = null,
    val order_index: Int? = null,
    val label_ids: List<String>? = null,
    val label_names: List<String>? = null,
    val source: String? = null,
    val booking_status: String? = null,
    val appointment_id: String? = null,
    val client_name: String? = null,
    val client_email: String? = null,
    val client_phone: String? = null,
)
```

por:

```kotlin
@Serializable
data class UpdateTaskRequest(
    val id: String,
    val title: String? = null,
    val body: String? = null,
    val day: String? = null,
    val due_at: String? = null,
    val slot_end_at: String? = null,
    val is_done: Boolean? = null,
    val order_index: Int? = null,
    val label_ids: List<String>? = null,
    val label_names: List<String>? = null,
)
```

- [ ] **Step 5: Editar `SupabaseAgendaTaskRepository.kt` — quitar mapeo de booking en `toTaskItem()`**

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
        source = source,
        bookingStatus = booking_status,
        appointmentId = appointment_id,
        clientName = client_name,
        clientEmail = client_email,
        clientPhone = client_phone,
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
    )
}
```

- [ ] **Step 6: Compilar para verificar**

Run: `./gradlew.bat :composeApp:compileCommonMainKotlinMetadata --console=plain` (desde `C:\Users\franr\AgendNote`)
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Correr los tests existentes**

Run: `./gradlew.bat :composeApp:testDebugUnitTest --console=plain` (desde `C:\Users\franr\AgendNote`)
Expected: `BUILD SUCCESSFUL` (los 3 archivos de test — `AgendaViewModelTest.kt`, `LabelsViewModelTest.kt`, `SettingsViewModelTest.kt` — no referencian campos de booking, así que deberían seguir pasando sin cambios).

- [ ] **Step 8: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/franciscor/agendnote/core/model/AgendaModels.kt composeApp/src/commonMain/kotlin/com/franciscor/agendnote/core/network/AgendaDtos.kt composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/data/SupabaseAgendaTaskRepository.kt
git commit -m "$(cat <<'EOF'
Quitar los campos de booking del modelo y de los DTOs de red

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Limpiar comentarios, copy y documentación que referencian bookings

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/presentation/model/PendingDelete.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/settings/presentation/view/SettingsScreen.kt`
- Modify: `README.md`

**Interfaces:**
- Consumes: nada nuevo — esta tarea es limpieza de texto/dead code, no cambia ninguna firma pública que otras tareas del roadmap vayan a usar.
- Produces: `PendingDelete(date, task)` sin el campo `completing` (confirmado sin ningún uso en todo el código antes de borrarlo).

- [ ] **Step 1: Editar `PendingDelete.kt` — quitar el campo `completing` y su KDoc referido a bookings**

Reemplazar el archivo completo:

```kotlin
package com.franciscor.agendnote.feature.agenda.presentation.model

import com.franciscor.agendnote.core.model.TaskItem
import kotlinx.datetime.LocalDate

/**
 * A task mutation awaiting user confirmation (shown via the shared `GlassConfirmDialog`).
 *
 * Used both for delete confirmations (always required) and for "mark as done" confirmations on
 * portfolio-booking tasks ([completing] = true), since completing a synced booking task does not
 * cancel the underlying appointment and the user should not be able to do that by accident.
 */
data class PendingDelete(
    val date: LocalDate,
    val task: TaskItem,
    val completing: Boolean = false,
)
```

por:

```kotlin
package com.franciscor.agendnote.feature.agenda.presentation.model

import com.franciscor.agendnote.core.model.TaskItem
import kotlinx.datetime.LocalDate

/**
 * A task delete mutation awaiting user confirmation (shown via the shared `GlassConfirmDialog`).
 */
data class PendingDelete(
    val date: LocalDate,
    val task: TaskItem,
)
```

- [ ] **Step 2: Editar `SettingsScreen.kt` — simplificar el mensaje de "Borrar todas las notas"**

Reemplazar:

```kotlin
        message = if (pendingBulkAction == SettingsBulkAction.DELETE_NOTES) {
            "Esta accion elimina todas las notas guardadas, incluidas las tareas o citas " +
                "sincronizadas desde el sistema de reservas, si las hay."
        } else {
            "Esta accion elimina todas las etiquetas creadas."
        },
```

por:

```kotlin
        message = if (pendingBulkAction == SettingsBulkAction.DELETE_NOTES) {
            "Esta accion elimina todas las notas guardadas."
        } else {
            "Esta accion elimina todas las etiquetas creadas."
        },
```

- [ ] **Step 3: Editar `README.md` — quitar la sección "Portfolio task contract"**

Reemplazar:

```markdown
This is a Kotlin Multiplatform project targeting Android, iOS.

## Portfolio task contract

- The agenda loads daily tasks through `GET /api-tasks?day=YYYY-MM-DD`.
- `POST /api-tasks` still accepts the legacy payload `title`, `body`, and `day`.
- Booking mirrors can also send `source`, `booking_status`, `appointment_id`, `client_name`, `client_email`, `client_phone`, `due_at`, `slot_end_at`, `label_names`, and `label_ids`.
- `POST /api-tasks` is idempotent when `appointment_id` is provided: it updates the mirrored task instead of creating duplicates.
- Supabase generates `tasks.id`; the portfolio does not send that id on create.
- `POST /api-tasks` and `PATCH /api-tasks` return the full task payload, including booking metadata and labels.
- The portfolio stores the returned id as `mirrored_task_id`, and AgendNote shows that task later through `fetchTasks(day)`.

* [/composeApp](./composeApp/src) is for code that will be shared across your Compose Multiplatform applications.
```

por:

```markdown
This is a Kotlin Multiplatform project targeting Android, iOS.

* [/composeApp](./composeApp/src) is for code that will be shared across your Compose Multiplatform applications.
```

- [ ] **Step 4: Compilar para verificar**

Run: `./gradlew.bat :composeApp:compileCommonMainKotlinMetadata --console=plain` (desde `C:\Users\franr\AgendNote`)
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/presentation/model/PendingDelete.kt composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/settings/presentation/view/SettingsScreen.kt README.md
git commit -m "$(cat <<'EOF'
Quitar comentarios y copy que referencian el sistema de reservas

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Verificación final de los 3 targets

**Files:** ninguno (solo verificación, sin cambios de código).

**Interfaces:**
- Consumes: el resultado de las Tasks 1–3.
- Produces: confirmación de que `commonMain`, Android e iOS compilan y los tests pasan — mismo criterio de salida usado al cerrar la Fase 2 de la auditoría previa.

- [ ] **Step 1: Compilar commonMain**

Run: `./gradlew.bat :composeApp:compileCommonMainKotlinMetadata --console=plain` (desde `C:\Users\franr\AgendNote`)
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Compilar Android**

Run: `./gradlew.bat :composeApp:compileDebugKotlinAndroid --console=plain` (desde `C:\Users\franr\AgendNote`)
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Compilar iOS (simulador arm64)**

Run: `./gradlew.bat :composeApp:compileKotlinIosSimulatorArm64 --console=plain` (desde `C:\Users\franr\AgendNote`)
Expected: `BUILD SUCCESSFUL`. Nota: la primera vez puede tardar más (descarga del toolchain de Kotlin/Native si no está en caché).

- [ ] **Step 4: Correr el test suite completo**

Run: `./gradlew.bat :composeApp:testDebugUnitTest --console=plain` (desde `C:\Users\franr\AgendNote`)
Expected: `BUILD SUCCESSFUL`, sin fallos.

No hay commit en esta tarea — es un gate de verificación, no produce cambios de código.

---

## Fuera de alcance de este plan

- Cambios en el schema/políticas/edge functions de Supabase (`supabase/` en la raíz del repo) — Fase 3 del roadmap de auditoría, pendiente de que el usuario conecte el MCP de Supabase.
- Cualquier funcionalidad nueva del roadmap (tareas recurrentes, vista de calendario, notificaciones push, widget, pasada de rendimiento) — son las piezas 2–6, cada una con su propio spec/plan.
