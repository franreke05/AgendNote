# Propuesta — Fase 4: Modelo de tarea ampliado

Propuesta escrita antes de implementar, tal como exige el prompt maestro para cualquier
cambio que toque el esquema de datos. No se ha escrito código de esta fase todavía.

## Qué se mantiene

- `tasks.day` (fecha, obligatoria) y `tasks.due_at`/`tasks.slot_end_at` (`timestamptz`,
  opcionales) **no se renombran ni se eliminan**. Ya representan correctamente "planificada
  para" (hora de inicio/fin de un bloque en el día) — confirmado leyendo
  `SupabaseAgendaTaskRepository.kt`: `due_at`/`slot_end_at` se guardan como
  `LocalDateTime(date, time).toInstant(TimeZone.currentSystemDefault())` y se leen de vuelta
  con la misma zona horaria del sistema. Es decir, el manejo de zona horaria de estos dos
  campos ya es correcto (usa IANA `TimeZone`, no un offset fijo) — no hace falta arreglar
  nada ahí, solo añadir lo que falta al lado.
- El patrón de sincronización de listas ya usado para etiquetas (`label_ids`/`label_names` en
  el body de `POST`/`PATCH` de `api-tasks`, `syncTaskLabels` + `attachLabels` en el Edge
  Function) se reutiliza tal cual para recordatorios y subtareas — no se inventa un patrón
  nuevo.
- RLS deny-all + Edge Functions con `service_role` (el modelo de seguridad ya documentado en
  `SECURITY_AUDIT.md`) — las tablas nuevas siguen exactamente el mismo patrón.

## Qué cambia

### Esquema (aditivo, no destructivo)

```sql
-- Deadline explicito, distinto de due_at/slot_end_at (planificacion).
alter table tasks add column if not exists deadline_at timestamptz;

create table if not exists task_reminders (
  id uuid primary key default gen_random_uuid(),
  task_id uuid not null references tasks(id) on delete cascade,
  remind_at timestamptz not null,
  created_at timestamptz not null default now()
);
create index if not exists idx_task_reminders_task_id on task_reminders(task_id);
create index if not exists idx_task_reminders_remind_at on task_reminders(remind_at);
alter table task_reminders enable row level security;

create table if not exists task_subtasks (
  id uuid primary key default gen_random_uuid(),
  task_id uuid not null references tasks(id) on delete cascade,
  title text not null,
  is_done boolean not null default false,
  order_index integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists idx_task_subtasks_task_id on task_subtasks(task_id);
alter table task_subtasks enable row level security;
```

**Migración de datos**: las tareas existentes con `due_at` no nulo y `notified_at` nulo hoy
disparan exactamente un recordatorio implícito. Para no perder ese comportamiento al migrar,
backfillear una fila en `task_reminders` (`remind_at = due_at`) por cada tarea en ese estado,
una única vez, como parte de la migración.

### API (Edge Functions, `api-tasks`)

- `TaskDto`/`CreateTaskRequest`/`UpdateTaskRequest` (Kotlin) y su espejo TypeScript ganan:
  `deadline_at: string?`, `reminders: string[]?` (instantes ISO), `subtasks: SubtaskDto[]?`.
- `attachLabels` gana hermanos `attachReminders`/`attachSubtasks` (mismo patrón: una query
  `.in("task_id", ids)` y agrupar en un `Map`).
- `syncTaskLabels` gana hermanos `syncTaskReminders`/`syncTaskSubtasks` (mismo patrón:
  borrar todo lo asociado a `task_id` y reinsertar el array recibido) — se activan con
  `hasField(body, "reminders")`/`hasField(body, "subtasks")`, igual que `hasLabelSync`.

### Dominio Kotlin

```kotlin
data class Subtask(val id: String, val title: String, val isDone: Boolean, val orderIndex: Int)

data class TaskItem(
    // ...campos existentes...
    val deadline: Instant? = null,
    val reminders: List<Instant> = emptyList(),
    val subtasks: List<Subtask> = emptyList(),
)
```

`TaskDraft` gana los mismos tres campos opcionales. `SupabaseAgendaTaskRepository` extiende
sus mappers DTO↔dominio.

### Notificaciones (el cambio más delicado)

Hoy `NotificationService.scheduleTaskNotification(task, date)` programa **una** alarma desde
`task.time`. Con recordatorios múltiples pasa a programar **N** alarmas, una por cada
`Instant` en `task.reminders`, cada una con un identificador propio (`"${task.id}#$index"`)
para que `AlarmManager` (Android) y `UNNotificationRequest` (iOS) puedan direccionarlas y
cancelarlas individualmente. Afecta:

- `AndroidReminderScheduler` (persistencia `StoredReminder`, restauración tras reinicio).
- `IosNotificationService` — **no verificable en este entorno** (sin Xcode/macOS), como el
  resto del código iOS tocado en esta sesión.
- La cola FIFO `notificationCommands`/`ReconcileDay` en `AgendaViewModel` — su forma cambia de
  "una reconciliación por tarea" a "una reconciliación por tarea con N recordatorios".

### UI

- `NewTaskSheet`: nuevo campo "Fecha límite" (deadline, opcional, selector aparte del de
  planificación) y una sección "Recordatorios" con presets (`"En el momento"`, `"1 hora
  antes"`, `"1 día antes"`, `"Personalizado"`) detrás de progressive disclosure (oculta hasta
  que el usuario la abre), tal como pide el prompt maestro para no saturar la creación
  rápida. Sección "Subtareas": lista simple con checkbox + texto + botón "Añadir".
- `TaskDetailsOverlay`: muestra deadline, lista de recordatorios y subtareas con toggle
  inline.
- `TaskCard`: chip opcional "2/5" cuando hay subtareas.

## Por qué (justificación)

Cierra la brecha #1 identificada en `SCREEN_INVENTORY.md`/`ARCHITECTURE_AUDIT.md`: el modelo
actual no distingue planificación/deadline/recordatorio ni admite subtareas o más de un
aviso — exactamente lo que el prompt maestro marca como prioridad "AHORA" en su bucle de
funcionalidades.

## Componentes reutilizados

`GlassTextField`, `GlassChip` (para presets de recordatorio), `GlassSurface`, el patrón de
selector de fecha/hora modal ya existente, `GlassIconButton`. No se crea ningún componente
visual nuevo que no exista ya, salvo una fila de subtarea simple (checkbox + texto), que sí
es genuinamente nueva.

## Impacto

- **Estado**: `TaskItem`/`TaskDraft` crecen; sin cambios de arquitectura de estado (sigue
  siendo `mutableStateOf` + `AgendaUiState`, no hace falta Flow nuevo).
- **Navegación**: ninguno — mismo sheet, más campos con divulgación progresiva.
- **Accesibilidad**: cada recordatorio/subtarea nueva debe cumplir el mínimo de 48dp y llevar
  etiqueta accesible, igual que el resto de `GlassX`.
- **Seguridad**: sin cambio de modelo de amenazas — mismo gate `APP_SECRET` + RLS deny-all.
- **Offline**: recordatorios/subtareas creadas offline deben entrar en el mismo camino de
  reintento/caché que ya existe para tareas; no se audita esto en profundidad en la propuesta,
  se revisa al implementar.
- **iOS**: la parte de notificaciones múltiples no se puede verificar en este entorno.

## Tests necesarios

- Mapeo DTO↔dominio de los tres campos nuevos (test nuevo, TDD).
- `AgendaViewModelTest`: crear/editar una tarea con recordatorios y subtareas actualiza
  `uiState` correctamente.
- Si la lógica de "generar N identificadores de alarma a partir de N recordatorios" se puede
  aislar en una función pura de `commonMain` (recomendado, en vez de mezclarla con las
  llamadas a `AlarmManager`/`UNNotificationCenter`), esa función lleva su propio test.
- Sin tooling para probar la migración SQL ni las Edge Functions en este entorno (igual que
  Fase 2) — probar manualmente contra una rama de desarrollo de Supabase antes de aplicar a
  producción.

## Plan de ejecución en sub-fases (no todo junto)

1. Esquema + DTOs (SQL + Kotlin DTOs, sin UI todavía) — la base para todo lo demás.
2. Dominio + repositorio (mappers, `TaskItem`/`TaskDraft` extendidos) + tests.
3. UI de creación/edición (deadline + recordatorios + subtareas en `NewTaskSheet`/detalle).
4. Notificaciones múltiples (la parte más riesgosa — Android verificable, iOS no).
5. Pulido: chip de progreso de subtareas en `TaskCard`, migración de datos existentes.

Cada sub-fase con su propio gate (compila, tests en verde, sin regresión) antes de pasar a la
siguiente — igual que Fase 3.

## Decisión de producto pendiente (no técnica, necesita tu respuesta)

¿Los recordatorios se definen como **offsets relativos** ("1h antes de la hora planificada o
del deadline") o como **instantes absolutos** que el usuario elige directamente en un
calendario? Recomiendo offsets relativos con presets — es más simple de programar, de migrar
si cambia la hora planificada, y de entender para quien lo usa — pero es tu decisión, no algo
que deba asumir.

## Riesgo y por qué esto se detiene aquí en vez de seguir implementando

Es un cambio de esquema (aditivo, no destructivo, pero real) que este entorno no puede aplicar
ni probar en vivo — decidiste mantener la auditoría solo-repo. Toca 4 capas a la vez si se
hace sin dividir. Antes de escribir la sub-fase 1 (SQL + DTOs), quiero tu confirmación sobre:
la pregunta de producto de arriba, y si prefieres que aplique la migración SQL como archivo en
`supabase/migrations/` para que tú la ejecutes, o si en algún momento quieres reconectar el
MCP de Supabase para que la aplique yo directamente.
