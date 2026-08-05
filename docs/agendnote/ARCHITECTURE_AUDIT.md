# Auditoría de arquitectura — AgendNote

## Estructura confirmada

Clean/MVVM por feature, tal como documenta `docs/AI_CONTEXT_MAP.md`:

```
app/navigation/   → AppNavHost, AppRoute, MainTab, NavigationComponents
app/di/           → AppServices.kt (wiring manual, sin framework DI)
core/ui/          → theme, components (Glass*), layout (AppLayoutMetrics)
core/network/     → AgendaApiClient (Ktor), DTOs
core/notifications/ → contrato común NotificationService
feature/agenda/   → presentation/{controller,model,view,viewmodel}, domain, data
feature/labels/   → mismo patrón
feature/settings/ → mismo patrón
androidMain/…/core/notifications/ → AlarmManager, boot restore, cola FIFO
iosMain/…/core/notifications/     → UserNotifications
```

Patrón por pantalla: `Screen` (composable) → `Controller` (traduce acciones UI) →
`ViewModel` (`StateFlow` de UI state) → `Repository` (interfaz) → implementación Supabase.
Esto ya es coherente con lo que pide `kotlin-project-architecture-review` y
`compose-state-holder-ui-split`; **no se recomienda una migración de arquitectura**.

## Modelo de dominio de tareas (`core/model/AgendaModels.kt`)

```kotlin
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
```

Gaps frente al prompt maestro (ver también `docs/agendnote/SCREEN_INVENTORY.md`):
- Un solo `time`/`endTime` — no distingue **planificada para** vs. **deadline** vs.
  **recordatorio**, los tres conceptos que el prompt maestro pide separar explícitamente.
- Sin subtareas (`List<Subtask>` no existe).
- Sin múltiples recordatorios (`due_at` es un único instante en el esquema SQL — ver
  `schema.sql`).
- `seriesId` enlaza con `TaskSeries`, pero `RecurrenceRule` (ver abajo) es intencionalmente
  simple.

## Recurrencia (`feature/agenda/domain/RecurrenceRule.kt`)

```kotlin
sealed interface RecurrenceRule {
    data object Daily : RecurrenceRule
    data class WeeklyDays(val days: Set<DayOfWeek>) : RecurrenceRule
    data class Monthly(val dayOfMonth: Int) : RecurrenceRule
}
```

- Opera enteramente sobre `kotlinx.datetime.LocalDate` (no `Instant`/`TimeZone`), así que
  **no hay riesgo de bugs de UtcOffset fijo** — el prompt maestro advierte contra sumar
  offsets fijos, y este código no lo hace porque no usa offsets en absoluto.
- `effectiveDayOfMonth` ya resuelve correctamente meses de 28–31 días (clamp al último día).
- **Gaps reales**: sin fin por fecha/número de ocurrencias (`materializedUntil` en
  `TaskSeries` es una ventana de materialización, no una regla de fin de usuario), sin
  excepciones (saltar una ocurrencia puntual), sin "editar esta y las siguientes" (edición
  hoy afecta a toda la serie o hay que romperla manualmente — **verificar el comportamiento
  exacto en `SeriesMaterializer.kt` y `AgendaViewModel` antes de tocar esto**, no asumido
  aquí porque no se ha leído ese archivo en detalle en esta pasada).
- No hay lógica de zona horaria explícita porque las horas (`LocalTime`) no llevan
  `TimeZone`; esto es razonable para una app de un solo dispositivo/usuario pero se vuelve un
  problema real si la app cambia de zona horaria (viaje) mientras hay recordatorios
  programados — pendiente de test explícito, no confirmado ni descartado en esta pasada.
- Advertencia ya documentada en `FINAL_UI_QA_REPORT.md`: migración pendiente de
  `kotlinx.datetime` a `kotlin.time.Instant` y estado beta de `expect`/`actual` — no bloquea
  hoy, pero hay que revisarla antes de invertir mucho en el modelo de recurrencia.

## Gap encontrado el 2026-08-04 (durante Fase 4): no existe edición de tarea existente

`AgendaTaskRepository` solo expone `createTask`, `updateTaskDone` (toggle) y `deleteTask` —
no hay ningún método para cambiar título, hora, etiquetas, etc. de una tarea ya creada, y
`TaskDetailsOverlay` no ofrece esa acción tampoco (confirmado por ausencia total de una acción
de edición en `AgendaOverlays.kt`). Hoy, la única forma de "editar" una tarea es borrarla y
crearla de nuevo. Esto es anterior a esta sesión, no una regresión introducida por Fase 4 —
pero limita el alcance de deadline/recordatorios/subtareas (sub-fase 2 de
`FASE4_PROPUESTA.md`): por ahora solo se pueden fijar al crear la tarea, no editar después.
Un `updateTask(id, draft)` genérico en el repositorio es candidato natural para una fase
futura, fuera del alcance actual.

## Notificaciones

Ya auditado y verificado en julio (`FINAL_UI_QA_REPORT.md`): cola FIFO común para
programar/cancelar, permisos bajo demanda, restauración tras reinicio en Android, fallback de
alarma inexacta. **No requiere trabajo adicional salvo que se amplíe el modelo de tarea**
(recordatorios múltiples exigiría extender el contrato `NotificationService`).

## Tests compartidos

`composeApp/src/commonTest/kotlin/com/franciscor/agendnote/`:
`AgendaViewModelTest`, `LabelsViewModelTest`, `RecurrenceRuleTest`, `RemoteConfigStatusTest`,
`SeriesMaterializerTest`, `SettingsViewModelTest`. 39 tests pasando a fecha del último informe
(2026-07-27); **no re-ejecutados en esta pasada de descubrimiento** — deben volver a
ejecutarse antes de dar por buena cualquier corrección de este ciclo.

## Dependency injection

`app/di/AppServices.kt` — wiring manual, sin Koin/Hilt. Correcto para el tamaño actual del
proyecto; no se recomienda introducir un framework DI solo por este trabajo.
