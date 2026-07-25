# Vista de calendario/mes — Design

## Contexto

Pieza 3 del roadmap de 6 para AgendNote. Piezas ya completas: 1) quitar espejo de reservas ✅, 2) tareas recurrentes ✅. Roadmap completo: 1) quitar bookings ✅, 2) tareas recurrentes ✅, 3) **vista de calendario/mes (esta pieza)**, 4) notificaciones push, 5) widget de pantalla de inicio, 6) pasada de rendimiento.

**Descubrimiento clave durante la exploración**: ya existe un composable `CalendarOverlay` (en `feature/agenda/presentation/view/AgendaOverlays.kt`) bastante completo — grid mensual con navegación por flechas y swipe horizontal, celdas con heatmap de color según cantidad de tareas por día, botón "Hoy", marca visual de días pasados. Se abre como modal (`Box` con scrim) desde un botón "Abrir calendario" en la vista de Agenda diaria (`AgendaScreen.kt`), y al tocar un día llama a `onSelectDate` que cierra el modal y navega ese día en el mismo tab.

La limitación real de ese overlay: el conteo de tareas por día (`AgendaUiState.tasksByDate: Map<LocalDate, List<TaskItem>>`) es un caché que solo se llena a medida que el usuario navega día por día (`AgendaViewModel.setTasks(date, tasks)` por cada día visitado individualmente) — no existe ningún fetch por rango de fechas en el cliente Kotlin. El edge function de Supabase (`api-tasks`) ya soporta `from`/`to` como query params (`query.gte("day", from).lte("day", to)`), pero `AgendaApiClient`/`AgendaTaskRepository` solo exponen fetch de un día a la vez. Resultado: abrir el calendario de un mes no visitado día a día muestra la mayoría de los días en 0 aunque tengan tareas.

## Decisiones acordadas con el usuario

1. **Alcance**: arreglar el problema de datos (fetch por rango) Y promover el calendario de modal a vista de primer nivel navegable — ambas cosas, no una sola.
2. **Ubicación en la navegación**: nuevo cuarto tab "Calendario" en la barra inferior, al mismo nivel que Agenda/Etiquetas/Ajustes (no un toggle dentro del tab Agenda). Orden de tabs: Agenda → Calendario → Etiquetas → Ajustes.
3. **Interacción al tocar un día**: navega al tab Agenda mostrando ese día completo (mismo comportamiento que el overlay actual), no una lista inline en el propio tab Calendario.
4. **El overlay actual (`CalendarOverlay`) se elimina** junto con el botón "Abrir calendario" que lo invoca desde `AgendaScreen.kt` — el tab nuevo lo reemplaza por completo, sin mantener dos calendarios en paralelo.

## Arquitectura

El calendario se implementa **dentro de `feature/agenda`**, no como un feature nuevo separado (`feature/calendar`). Calendario y agenda diaria comparten el mismo concepto — "qué tareas hay en qué día" — así que comparten `AgendaViewModel`/`AgendaController` y el mismo caché `tasksByDate`. Esto evita tener dos fuentes de verdad que se puedan desincronizar: completar o borrar una tarea en el tab Agenda debe reflejarse en el heatmap del tab Calendario sin lógica de invalidación cruzada adicional, porque ambos leen el mismo estado.

### Capa de red y repositorio

- `AgendaApiClient`: nuevo método `fetchTasksInRange(from: String, to: String): List<TaskDto>`, llamando `GET /api-tasks?from=X&to=Y` (contrato ya soportado por el edge function existente, sin cambios de backend).
- `AgendaTaskRepository` (interfaz de dominio): nuevo método `fetchTasksInRange(from: LocalDate, to: LocalDate): Map<LocalDate, List<TaskItem>>`. Devuelve un mapa (no una lista plana) porque `TaskItem` no lleva su propia fecha — el agrupamiento por día debe hacerse en el repositorio a partir del campo `day` del DTO, antes de descartarlo al mapear a `TaskItem`.
- `SupabaseAgendaTaskRepository`: implementa agrupando los `TaskDto` devueltos por fecha (`LocalDate.parse(dto.day)`) y mapeando cada grupo con el mapper `toTaskItem` ya existente.

### ViewModel

`AgendaViewModel` gana:
- Una acción nueva `AgendaAction.LoadMonth(month: LocalDate)` (siguiendo el mismo patrón que las acciones existentes `SelectDate`/`MoveDay`/`RefreshSelectedDate`), manejada como fire-and-forget vía `handleAsync`.
- Un conjunto de meses ya cargados en la sesión (clave: primer día del mes) para no volver a pedir un mes que ya se cargó — mismo espíritu que el caché por día ya existente (`hasCachedTasks`).
- Al cargar un mes: calcula el primer y último día de ese mes, si no está en el set de cargados llama a `fetchTasksInRange`, mergea el resultado en `tasksByDate` (cada día que devuelve el fetch sobrescribe la entrada existente en el mapa para esa fecha, igual que ya hace `setTasks` para un día individual — es un fetch fresco del servidor, así que siempre gana) y agrega el mes al set.
- Un `monthErrorMessage: String?` en el estado — solo uno, no por-mes, porque en la UI solo hay un mes visible a la vez. Se limpia al iniciar una carga nueva o al tener éxito.

### UI

- Se extrae el grid/heatmap/swipe del `CalendarOverlay` actual a un composable reutilizable (mismo diseño visual: título de mes/año, flechas prev/next, swipe horizontal, header de días de la semana, grid de celdas con heatmap por densidad de tareas, marca de días pasados, botón "Hoy").
- Nueva pantalla `CalendarScreen.kt` en `feature/agenda/presentation/view/`: monta ese grid a pantalla completa (sin modal/scrim), dispara `LoadMonth` cuando cambia el mes visible, y al tocar un día llama a `SelectDate` + navega al tab Agenda.
- Se elimina `CalendarOverlay`, el estado `showCalendar` y el botón "Abrir calendario" de `AgendaScreen.kt` — código muerto una vez que el tab nuevo lo reemplaza.
- Nuevo `AppRoute.Calendar` + entrada en el enum que gobierna los tabs de la barra inferior, con el mismo ícono ya usado para el selector de fecha (`Icons.Rounded.CalendarToday`).

## Errores y estado de carga

Si `fetchTasksInRange` falla (sin conexión, error de red), se muestra `monthErrorMessage` como un texto/banner simple dentro del tab Calendario — no bloquea ni rompe la app, mismo espíritu que los banners de error ya existentes en otras pantallas. Si no hay acceso remoto (`remoteConfigStatus` deshabilitado), mismo patrón ya establecido en toda la app: repositorio nulo → guard clause → mensaje de "no disponible".

## Testing

Tests de `AgendaViewModel` con un repositorio fake que implemente `fetchTasksInRange`, verificando:
- Cargar un mes puebla `tasksByDate` correctamente para cada día del mes que tuvo tareas.
- Cargar un mes ya cargado no repite el fetch (no se llama de nuevo al repositorio).
- Un fetch que falla deja `monthErrorMessage` con contenido y no rompe el estado existente de `tasksByDate`.

Mismo estilo que los tests ya existentes en `AgendaViewModelTest.kt` (repositorio fake, sin red real).

## Fuera de alcance

- Vista semanal (solo mensual, como pidió el roadmap).
- Lista de tareas inline debajo del grid del mes (tocar un día navega al tab Agenda en vez de mostrar la lista ahí mismo).
- Prefetch de meses adyacentes para hacer el swipe más fluido — se puede agregar después si el salto entre meses se siente lento; por ahora cada cambio de mes dispara su propio fetch bajo demanda.
- Cambios en cómo se cuentan las tareas para el heatmap (se sigue contando tareas completadas y pendientes juntas, igual que el overlay actual — no se introduce un filtro nuevo).
- Cualquier cambio al backend/edge function — el endpoint `from`/`to` ya existe y no se toca.
