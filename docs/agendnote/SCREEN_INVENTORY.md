# Inventario de pantallas — AgendNote

Generado durante el Bucle de Descubrimiento del 2026-08-04, a partir del código real en
`agent/finish-audit-notifications`. No se ha inventado ninguna pantalla: lo que no existe
en el código se marca explícitamente como "no implementada" en la sección final.

Contexto: hay una auditoría UI/UX previa completa (2026-07-27, mismo branch, PR
[#1](https://github.com/franreke05/AgendNote/pull/1)) documentada en
[`docs/FINAL_UI_QA_REPORT.md`](../FINAL_UI_QA_REPORT.md). Ese trabajo ya cubrió gran parte
de lo que el prompt maestro pide para "Bucle de sistema de diseño" y "Bucle por pantalla"
(Glass, jerarquía, contraste, 48dp, modales opacos, navegación, recordatorios). Este
inventario no repite esas correcciones; las referencia y se centra en lo que sigue abierto.

## Navegación real

4 pestañas (`MainTab.kt`), sin quinta pestaña ni pantalla de arranque/onboarding separada:

| Tab | Ruta (`AppRoute`) | Entry point | State holder |
|---|---|---|---|
| Agenda | `AppRoute.Agenda` | `feature/agenda/presentation/view/AgendaScreen.kt` | `AgendaViewModel` |
| Calendario | `AppRoute.Calendar` | `feature/agenda/presentation/view/CalendarScreen.kt` | `AgendaViewModel` (compartido) |
| Etiquetas | `AppRoute.Labels` | `feature/labels/presentation/view/LabelsScreen.kt` | `LabelsViewModel` |
| Ajustes | `AppRoute.Settings` | `feature/settings/presentation/view/SettingsScreen.kt` | `SettingsViewModel` |

Shell: `app/navigation/AppNavHost.kt` + `NavigationComponents.kt` (bottom bar, remote-config
banner). No hay pantalla de login/registro ni splash propio: la app no tiene autenticación
por usuario (ver `SECURITY_AUDIT.md`).

## Agenda

- Ruta: `AppRoute.Agenda`. Archivo: `AgendaDayComponents.kt`, `AgendaOverlays.kt`, `AgendaScreen.kt`.
- State holder: `AgendaViewModel` + `AgendaController` (traduce acciones UI a llamadas al ViewModel).
- Acción primaria: crear tarea (FAB → `NewTaskSheet`, diálogo opaco desplazable).
- Estados existentes: loading, contenido, vacío (icono + texto + subtítulo, corregido en
  `plan_mejora_codex.md` hallazgo 2), error con `Reintentar`, offline con caché visible.
- Dependencias: `AgendaTaskRepository` (interfaz) → `SupabaseAgendaTaskRepository`,
  `TaskSeriesRepository` → `SupabaseTaskSeriesRepository`, `RecurrenceRule`/`SeriesMaterializer`.
- Componentes Glass: `GlassSurface`, `GlassTextField`, paleta de etiquetas 4×4, selector de
  fecha/hora modal, grid de recurrencia 2×2, `SwipeableTaskCard` (swipe completar/borrar).
- Problemas conocidos (no cubiertos por la auditoría de julio):
  - **UX**: no separa "planificada para" de "deadline"; una sola fecha/hora por tarea.
  - **UX**: sin subtareas/checklist.
  - **UX**: un único recordatorio implícito (`due_at`), no recordatorios múltiples ni aviso anticipado.
  - **Lógico**: `RecurrenceRule` (ver `ARCHITECTURE_AUDIT.md`) no soporta fin por fecha/número
    de ocurrencias, ni excepciones, ni "editar esta y las siguientes".
  - **Seguridad**: ninguna — la pantalla no distingue usuarios (app de un solo inquilino).
- Tests: `AgendaViewModelTest.kt`, `RecurrenceRuleTest.kt`, `SeriesMaterializerTest.kt`.
- Prioridad de mejora: **alta** (es la pantalla ancla del prompt maestro — "Hoy").

## Calendario

- Ruta: `AppRoute.Calendar`. Archivo: `CalendarScreen.kt` (`CalendarMonthView` reutilizable).
- State holder: comparte `AgendaViewModel` (carga por rango de mes).
- Acción primaria: tocar un día → selecciona fecha y navega a Agenda (confirmado en
  `FINAL_UI_QA_REPORT.md`).
- Estados: loading por mes, error con reintento, mes cacheado.
- Problemas conocidos: solo muestra tareas propias; el prompt maestro pide combinar eventos
  de calendario externo de solo lectura — **no implementado, no existe integración de
  calendario del sistema**.
- Tests: ninguno específico de `CalendarScreen` (cubierto indirectamente por `AgendaViewModelTest`).
- Prioridad: media (funcional, pero sin integración externa).

## Etiquetas

- Ruta: `AppRoute.Labels`. Archivo: `LabelsScreen.kt`, función `LabelRow`.
- State holder: `LabelsViewModel`.
- Acción primaria: crear etiqueta (nombre + color, paleta 16 colores en 4×4, corregida en la
  auditoría previa).
- Estados: loading, vacío con orientación, error con reintento.
- Problemas conocidos: sin fusión de etiquetas, sin filtro "N tareas con esta etiqueta"
  navegable (el conteo se muestra pero no es una acción).
- Tests: `LabelsViewModelTest.kt`.
- Prioridad: baja–media.

## Ajustes

- Ruta: `AppRoute.Settings`. Archivo: `SettingsScreen.kt`.
- State holder: `SettingsViewModel`.
- Acción primaria: depende de la sección (tema, recordatorios, zona de peligro).
- Estados: toggles con estado optimista + rollback ante fallo de red (confirmado en
  `FINAL_UI_QA_REPORT.md`, tabla "Estados remotos").
- Contenido actual: tema claro/oscuro (manual, no sigue el sistema — backlog conocido,
  hallazgo 8 de `plan_mejora_codex.md`), fondo, `Configurar recordatorios`
  (permisos Android bajo demanda), zona destructiva (borrar notas/etiquetas), lista de
  series recurrentes.
- Problemas conocidos:
  - No hay agrupación explícita Cuenta/Agenda/Notificaciones/Apariencia/Accesibilidad/
    Seguridad/Datos/Acerca de que pide el prompt maestro — es una lista más plana.
  - No hay bloqueo biométrico, ni exportación/importación, ni gestión de sesiones (no aplica
    sin autenticación por usuario).
- Tests: `SettingsViewModelTest.kt`, `RemoteConfigStatusTest.kt`.
- Prioridad: media.

## Pantallas que el prompt maestro describe y **no existen** en el código actual

No se han implementado ni deben asumirse como presentes:

| Pantalla/flujo | Estado |
|---|---|
| Arranque/splash con estados sesión válida/expirada/offline | No existe — no hay sesión de usuario |
| Onboarding | No existe |
| Registro / inicio de sesión | No existe — app de un solo inquilino, sin Supabase Auth |
| Bloqueo local biométrico | No existe |
| Creación rápida con lenguaje natural (es-ES) | No existe — el formulario es campo por campo |
| Búsqueda dedicada (recientes, filtros sugeridos, agrupado) | No existe — no hay pantalla ni caja de búsqueda dedicada |
| Filtros / listas inteligentes guardadas | No existe |
| Plantillas de tareas | No existe |
| Papelera/archivo con retención | No existe — el borrado es permanente e inmediato |
| Widgets | No existe |
| Exportación/importación (JSON/CSV/iCalendar) | No existe |
| Modo foco / temporizador | No existe |
| Hábitos / matriz de Eisenhower | No existe (correctamente fuera de alcance por ahora) |

Estas filas son **recomendaciones a evaluar**, no trabajo pendiente de implementar
automáticamente — ver priorización en `IMPLEMENTATION_PLAN.md`.
