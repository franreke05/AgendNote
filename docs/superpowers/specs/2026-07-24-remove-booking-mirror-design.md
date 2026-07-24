# Quitar el espejo de reservas (cliente) — Design

## Contexto

AgendNote está pasando de ser "agenda personal + espejo de reservas de un portfolio externo" a ser una app 100% personal (regalo para un uso individual). El espejo de citas (`source == "portfolio_booking"`) deja de tener sentido y se elimina del lado cliente. El backend de Supabase (schema, políticas, edge functions) **no se toca en esta pieza** — queda para cuando se conecte el MCP de Supabase (Fase 3 del roadmap de auditoría).

Esta es la primera de seis piezas del roadmap de nuevas funcionalidades acordado con el usuario:
1. **Quitar bookings (esta pieza)**
2. Tareas recurrentes
3. Vista de calendario/mes
4. Notificaciones push
5. Widget de pantalla de inicio
6. Pasada de rendimiento transversal

## Decisión de alcance

Limpieza completa (no solo ocultar en UI): se borran del código cliente los campos y ramas de lógica específicas de booking. Motivo: la app debe quedar lo más liviana posible, sin ramas muertas ni campos sin usar.

## Cambios por archivo

- **`core/model/AgendaModels.kt`**: `TaskItem` pierde `source`, `bookingStatus`, `appointmentId`, `clientName`, `clientEmail`, `clientPhone`. Se conservan `id`, `title`, `details`, `time`/`endTime`, `labels`, `isDone`.
- **`core/network/AgendaDtos.kt`**: el DTO deja de serializar/deserializar esos campos al crear/leer tareas.
- **`feature/agenda/data/SupabaseAgendaTaskRepository.kt`**: se quita el mapeo de esos campos en `toTaskItem()`/al construir el payload de creación.
- **`feature/agenda/presentation/view/AgendaDayComponents.kt`**: se quita el flag `isPortfolioBooking` y los chips de "Cita cliente"/estado de reserva en `TaskCard`.
- **`feature/agenda/presentation/view/AgendaOverlays.kt`**: `ConfirmDeleteDialog` vuelve a tener un único mensaje genérico (se quita la rama condicionada a `task.source`).
- **`feature/agenda/presentation/view/AgendaScreen.kt`**: se quitan los parámetros `onRequestDeleteBooking`/`onRequestToggleBooking` de la llamada a `DayAgenda`; vuelve a usar directamente `onRequestDelete`/`onToggleDone`.
- **`feature/agenda/presentation/viewmodel/AgendaViewModel.kt`** / **`AgendaController.kt`**: se revisa si hay lógica adicional atada a `source` (p. ej. en `saveTask`/mapeo) y se quita si existe.
- **`README.md`**: se elimina la sección "Portfolio task contract".

**Importante — qué NO se toca:** el trabajo de la auditoría anterior (scope propio por ViewModel, `rememberSaveable` para el borrador de tarea, alternativa accesible al swipe-only, migración a `GlassConfirmDialog`, tokens de color/scrim, `DateFormatting.kt` compartido, touch targets) permanece intacto. Solo se elimina la *rama* de comportamiento específica de booking que se apoyaba en esos mismos fixes — no se deshace ningún fix.

## Compatibilidad con el backend

Los campos de booking eran opcionales en el contrato original (`POST /api-tasks` los acepta pero no los requiere). Al dejar de enviarlos/leerlos, el cliente sigue siendo compatible con el backend actual: simplemente ignora esos campos si existen en la respuesta. No es necesario modificar Supabase para que esta pieza funcione.

## Testing

Los 3 tests de ViewModel existentes (`AgendaViewModelTest.kt`, `LabelsViewModelTest.kt`, `SettingsViewModelTest.kt`) no dependen de campos de booking. Se revisan por si algún fixture de test los referencia y se ajustan de ser necesario. Verificación final: recompilar los 3 targets (commonMain, Android, iOS) y correr el test suite, igual que se hizo al cerrar la Fase 2.

## Fuera de alcance

- Cambios en el schema/políticas/edge functions de Supabase (Fase 3, pendiente de MCP).
- Cualquier feature nueva (recurrencia, calendario, notificaciones, widget) — son piezas separadas del roadmap.
