# Registro de implementación

Una entrada por slice, en el formato que pide el prompt maestro: pantalla, iteraciones,
cambios, tests, resultado, deuda restante.

## 2026-08-04 — Fase 3, slice 1: Deshacer al completar una tarea (Agenda)

- **Pantalla**: Agenda (afecta también al diálogo de detalle, que reutiliza el mismo camino).
- **Iteraciones**: 1 (RED confirmado por error de compilación → GREEN al primer intento, sin
  necesidad de una segunda vuelta).
- **Cambios**:
  - Nuevo modelo `PendingUndo` (`feature/agenda/presentation/model/PendingUndo.kt`).
  - `AgendaUiState.pendingUndo: PendingUndo?` (nuevo campo, default `null`).
  - `AgendaViewModel.toggleTaskDone` establece/limpia `pendingUndo`; nuevo
    `AgendaViewModel.dismissPendingUndo()`.
  - `AgendaController.dismissPendingUndo()` (passthrough síncrono, mismo patrón que
    `removeLabelFromTasks`).
  - Nuevo componente `GlassSnackbar` (`core/ui/components/GlassSnackbar.kt`) — superficie
    opaca (`modalFill`), acción con objetivo táctil ≥48dp.
  - `AgendaScreen` renderiza el snackbar cuando `uiState.pendingUndo != null`, con
    auto-descarte a los 4s (`LaunchedEffect(pending) { delay(4000) }`).
- **Tests**: 4 tests nuevos en `AgendaViewModelTest.kt` (marcar como hecha expone
  `pendingUndo`; desmarcar lo limpia; `dismissPendingUndo` lo limpia sin tocar tareas; un
  fallo de red no expone `pendingUndo`). Suite completa: 43/43 en verde
  (`:composeApp:testDebugUnitTest`, `BUILD SUCCESSFUL`).
- **Resultado**: Completado y verificado a nivel de lógica/compilación.
- **Deuda restante**: No se verificó visualmente en emulador/dispositivo (no se lanzó
  ningún emulador en esta pasada) — pendiente antes de dar el slice por cerrado en un
  sentido de QA visual. El mensaje del snackbar usa un literal en línea (no hay
  `strings.xml` en el proyecto — consistente con el resto del código existente).

## 2026-08-04 — Fase 3, slice 2: Reduced motion / reduced transparency (GlassBackground)

- **Pantalla**: transversal (`GlassBackground` es compartido por las 4 pestañas).
- **Iteraciones**: 1 para la parte testeable (`glassImageFadeDurationMillis`, RED por error
  de compilación → GREEN); el resto (señal de plataforma + cableado en `GlassBackground`) no
  tiene un ciclo RED/GREEN propio porque el repo no tiene tooling para probarlo
  automáticamente (sin Robolectric/instrumented tests para leer `Settings.Global` en Android
  ni un target de test iOS para UIKit).
- **Cambios**:
  - `core/platform/AccessibilityPreferences.kt` (commonMain): `@Composable expect fun
    rememberReduceMotionEnabled()` / `rememberReduceTransparencyEnabled()`.
  - Android `actual`: `ContentObserver` reactivo sobre
    `Settings.Global.ANIMATOR_DURATION_SCALE`; reduce transparency siempre `false` (Android no
    tiene equivalente público estable).
  - iOS `actual`: lectura puntual (no reactiva) de `UIAccessibilityIsReduceMotionEnabled()` /
    `UIAccessibilityIsReduceTransparencyEnabled()` — se evitó a propósito la variante reactiva
    con `NSNotificationCenter` porque no se pudo compilar ni verificar en este entorno.
  - `core/ui/motion/ReduceMotion.kt`: `glassImageFadeDurationMillis(reduceMotion)`, función
    pura, con test unitario real.
  - `GlassBackground`: el fade de imagen de fondo usa esa duración; las 3 manchas de luz
    decorativas + el grano se ocultan con reduce transparency.
- **Tests**: 2 tests nuevos (`ReduceMotionTest.kt`). Suite completa: 45/45 en verde,
  debug y release.
- **Resultado**: Completado en Android (compilado de verdad). **No completado/verificado en
  iOS** — sin macOS/Xcode en este entorno, es código no compilado.
- **Deuda restante**: aplicar reduce transparency a `GlassSurface` (tarjetas, diálogos,
  chips) — se dejó fuera para no tocar la opacidad de todo el sistema Glass sin QA visual.
  Reactividad de iOS a mitad de sesión (NSNotificationCenter) queda pendiente de una máquina
  con Xcode.

## 2026-08-04 — Fase 3, slice 3: GlassEmptyState compartido (Agenda + Etiquetas)

- **Pantallas**: Agenda, Etiquetas.
- **Iteraciones**: 1 (refactor puro, sin comportamiento nuevo).
- **Cambios**: nuevo `core/ui/components/GlassEmptyState.kt`; ambas pantallas lo consumen
  conservando su propio contenedor. Delta visual deliberado: opacidad del subtítulo unificada
  a 0.7 (Etiquetas usaba 0.75).
- **Tests**: ninguno nuevo (refactor sin lógica nueva); la suite existente (45 tests) actúa
  de red de regresión.
- **Resultado**: Completado y verificado por compilación (debug + release, ambos
  `BUILD SUCCESSFUL`).
- **Deuda restante**: sin verificación visual en dispositivo/emulador.

---

**Fase 3 completa.** 3 slices, 3 commits, 45/45 tests en verde en ambas variantes. Deuda
transversal: ninguno de los tres slices tuvo confirmación visual en emulador/dispositivo real
en esta pasada — queda como QA visual pendiente antes de considerar la fase 100% cerrada en
sentido estricto del prompt maestro ("no declares validación visual... si no la hiciste").

## 2026-08-04 — Fase 2: Endurecimiento de seguridad

- **Alcance**: ver `docs/agendnote/SECURITY_AUDIT.md`, sección "Fase 2 — resultado".
- **Iteraciones**: 1 por slice (lado Kotlin con TDD real; lado Edge Functions sin ciclo
  RED/GREEN posible — no hay Deno en este entorno).
- **Cambios**: `resolveServerError` (Kotlin) y `internalErrorResponse` (las 6 Edge Functions +
  `_shared/response.ts`) dejan de reenviar texto crudo de excepción al cliente/usuario.
- **Tests**: 1 test nuevo en Kotlin (`AgendaViewModelTest`), suite completa 46/46. Ningún
  test en el lado TypeScript — no hay tooling de test para Edge Functions en este proyecto.
- **Resultado**: Kotlin completado y verificado (compilación + test real). Edge Functions
  completado pero **no verificado** — cambio mecánico revisado a mano, sin compilar.
- **Deuda restante**: rate limiting, idempotencia ante pérdida de respuesta post-éxito, y
  límites de longitud de payload — documentados como pendientes de prioridad baja en
  `SECURITY_AUDIT.md`, no implementados en esta pasada. `APP_SECRET` embebido en el binario
  queda como riesgo residual aceptado de la arquitectura mono-usuario, no como tarea abierta.

---

**Fase 2 completa** para el alcance decidido (sin hallazgos críticos/altos abiertos). 5
commits totales en esta sesión sobre Fase 2+3. Antes de Fase 4 hace falta una propuesta
escrita (cambia `schema.sql` + Edge Functions + 4 capas de Kotlin) — se entrega por separado,
sin implementar código todavía.
