# Tareas recurrentes — Design

## Contexto

Pieza 2 del roadmap de 6 para hacer de AgendNote una agenda 100% personal más completa (pieza 1, "quitar espejo de reservas", ya está mergeada y pusheada). El objetivo: que el usuario pueda crear una tarea que se repita (diario, ciertos días de la semana, o mensual) sin tener que crearla a mano cada vez.

Roadmap completo: 1) quitar bookings ✅, 2) **tareas recurrentes (esta pieza)**, 3) vista de calendario/mes, 4) notificaciones push, 5) widget de pantalla de inicio, 6) pasada de rendimiento.

**Nota de contexto importante**: al preparar esta pieza se descubrió que `origin/main` tenía un commit no fusionado con notificaciones locales de iOS ya implementadas (`NotificationServiceProvider`, `IosNotificationService`) — fusionado en la pieza 1. Cuando se llegue a la pieza 4 del roadmap (notificaciones), hay que revisar esa implementación existente antes de diseñar desde cero.

**Estado del MCP de Supabase**: no está disponible en esta sesión (se registró en otra sesión/ventana). Esta pieza prepara la migración SQL y el código del edge function listos en el repo, para aplicar manualmente o desde una sesión con el MCP conectado — no se aplican cambios en la base de datos real como parte de este plan.

## Decisiones acordadas con el usuario

1. **Patrones de recurrencia**: diaria, días específicos de la semana (incluye "semanal" como caso de un solo día seleccionado — se unifican en la UI para no duplicar), mensual (mismo día del mes).
2. **Edición/borrado de una aparición puntual**: afecta solo esa fecha; el resto de la serie no se toca. Esto es gratis en el modelo elegido porque cada aparición es una fila normal de `tasks`.
3. **Fin de la serie**: indefinida, con horizonte rodante — la app genera automáticamente las próximas apariciones a medida que pasa el tiempo, sin fecha de fin ni conteo que el usuario tenga que gestionar.
4. **Arquitectura de materialización**: del lado del cliente (no un cron server-side), porque es una app personal que se abre seguido. Un edge function con cron queda como posible mejora futura sobre el mismo schema, no bloquea esta pieza.

## Modelo de datos

### Tabla nueva `task_series` (Supabase)

| Columna | Tipo | Notas |
|---|---|---|
| `id` | uuid, PK | |
| `title` | text | Plantilla del título de cada aparición |
| `body` | text, nullable | Plantilla de detalles |
| `time` | time, nullable | Hora de cada aparición (null = sin hora fija) |
| `recurrence_type` | text | `'daily'` \| `'weekly_days'` \| `'monthly'` |
| `days_of_week` | smallint[], nullable | Solo para `weekly_days`. Valores 0(domingo)-6(sábado). Un solo valor = "semanal" |
| `day_of_month` | smallint, nullable | Solo para `monthly`. 1-31 |
| `label_ids` | uuid[], nullable | Etiquetas aplicadas a cada aparición generada |
| `is_active` | boolean, default true | false = serie detenida, no se generan más apariciones |
| `materialized_until` | date | Cursor: hasta qué fecha ya se generaron apariciones |
| `created_at` | timestamptz, default now() | |

### `tasks` (existente): nueva columna

- `series_id` uuid, nullable, `REFERENCES task_series(id) ON DELETE SET NULL`

Cada aparición de una serie es una fila normal en `tasks`. No hay tabla de "excepciones" ni lógica especial de lectura — el modelo de lectura actual (`GET /api-tasks?day=X`) no cambia en absoluto.

### Borrado de una serie

- Se borran las filas de `tasks` con ese `series_id` donde `is_done = false` y `day >= hoy` (apariciones futuras no completadas).
- Las apariciones pasadas o ya completadas conservan su fila pero pierden el vínculo (`series_id` queda `NULL` vía `ON DELETE SET NULL`) — se convierten en tareas sueltas normales, preservando el historial.

## Backend (preparado, no aplicado en esta sesión)

- **Migración SQL** (`supabase/migrations/`): crea `task_series`, añade `series_id` a `tasks`, políticas RLS equivalentes a las de `tasks`.
- **Edge function nuevo** (`supabase/functions/api-task-series/`): `POST` (crear serie), `GET` (listar series activas), `PATCH` (pausar/activar), `DELETE` (borrar con la semántica de arriba). Sigue el mismo patrón de autenticación (`x-app-secret`) que `api-tasks`.
- `api-tasks` (existente) no necesita cambios de contrato — ya acepta crear tareas sueltas; las apariciones materializadas se crean con el mismo `POST /api-tasks`, solo que ahora ese payload incluye `series_id`.

## Materialización (cliente)

Al arrancar la app (mismo punto donde hoy se hace `LaunchedEffect(Unit) { controller.handleAsync(AgendaAction.RefreshSelectedDate) }` en `AgendaScreen.kt`, o un `LaunchedEffect` equivalente a nivel de `AppNavHost`):

1. Obtener las series activas (`GET /api-task-series`).
2. Por cada serie: calcular qué fechas caen dentro de la regla de recurrencia entre `max(materialized_until, hoy)` y `hoy + 8 semanas`.
3. Crear una tarea (`POST /api-tasks`) por cada fecha, con `series_id`, `title`/`body`/`time`/`label_ids` copiados de la plantilla de la serie.
4. Si todas las creaciones del lote tuvieron éxito, actualizar `materialized_until` de la serie a `hoy + 8 semanas` (`PATCH /api-task-series`).

**Limitación conocida (aceptada, no sobre-diseñada)**: si el lote falla a mitad de camino (p. ej. se pierde la conexión), `materialized_until` no avanza, así que el próximo arranque reintenta desde el mismo punto — puede generar algún duplicado ocasional en el peor caso. Dado el volumen bajo de una app personal, no se implementa una comprobación de idempotencia más robusta (p. ej. un índice único `(series_id, day)`) en esta primera versión; queda como mejora futura si se observa el problema en la práctica.

El cálculo de "qué fechas caen dentro de la regla" es lógica pura (sin red), vive en el cliente (Kotlin) para poder testearla directamente.

## UI

- **Crear**: `NewTaskSheet` (en `feature/agenda/presentation/view/AgendaOverlays.kt`) gana un selector "Repetir" con 4 opciones: Ninguna / Diaria / Días de la semana (chips L M X J V S D, selección múltiple) / Mensual (selector del día 1-31, por defecto el día de la fecha elegida).
- **Indicador visual**: `TaskCard` (en `AgendaDayComponents.kt`) muestra un icono pequeño de "repetir" (`Icons.Rounded.Repeat` o similar) cuando `task.seriesId != null`.
- **Gestionar series**: nueva sección "Tareas recurrentes" en `SettingsScreen.kt`, lista las series activas (título + patrón en texto legible, p. ej. "Cada lunes, miércoles y viernes") con un botón de borrar por serie (con confirmación, reutilizando `GlassConfirmDialog`).
- **Fuera de alcance de esta pieza** (YAGNI, se puede agregar después si hace falta): pausar/reanudar una serie sin borrarla, editar la plantilla de una serie ya creada (cambiar hora/título futuro).

## Testing

- Tests unitarios puros para el cálculo de fechas de cada `recurrence_type` dado un rango de fechas (sin red, sin Supabase) — caso más importante a cubrir porque es la lógica con más superficie de bugs (límites de mes, fin de semana, etc.).
- Test para la semántica de borrado de serie: apariciones futuras no completadas se eliminan, pasadas/completadas quedan sueltas.

## Fuera de alcance

- Aplicar la migración SQL real a Supabase (requiere MCP conectado o aplicación manual por el usuario).
- Materialización server-side con cron (mejora futura sobre el mismo schema).
- Pausar/reanudar una serie, editar su plantilla.
- Notificaciones para tareas recurrentes (pieza 4 del roadmap, aparte).
- Vista de calendario/mes (pieza 3 del roadmap, aparte — aunque el indicador de "serie" en `TaskCard` es reutilizable ahí).
