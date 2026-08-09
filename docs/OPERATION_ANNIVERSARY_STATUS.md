# Operación Aniversario — estado operativo

Documento vivo. Cada agente lo consulta antes de trabajar y lo actualiza al terminar un batch.
Sustituye a una narrativa dispersa en el chat: esta es la fuente de verdad de estado, no una
copia — `docs/AI_CONTEXT_MAP.md`/`AI_TOKEN_INDEX.md` siguen siendo el mapa de arquitectura.

## CURRENT_PHASE — pivote 2026-08-09 tarde: directiva visual y de producto definitiva
El usuario envió capturas reales del emulador (Agenda/Calendario/Etiquetas/Ajustes) y, tras
mi lectura propia de ellas, una directiva nueva y mucho más amplia que "pulir lo existente":

1. **Rediseño visual completo** hacia cristal iPhone premium (transparencia real como lenguaje
   principal, no decoración) - la preferencia visual #1 de la usuaria final es "le encanta lo
   transparente".
2. **Reestructura de navegación**: Agenda / Día / Etiquetas / Ajustes (se elimina la pestaña
   Calendario como tal; el calendario mensual pasa a un popover Glass desde Agenda; el slot de
   la pestaña se reutiliza para una vista "Día" nueva - timeline por horas).
3. **Rediseño de Listas inteligentes** (hoy el componente visualmente más débil, según el
   propio usuario - Dialog centrado con scrim casi negro, nada de "glass").
4. **Simplificación de Etiquetas** (nombre + color en un selector, no 16 swatches permanentes).
5. **Ajustes**: quitar "Fondo/URL de imagen" de la UI (verificar consumidores backend antes de
   tocar nada del lado servidor), reestructurar en secciones reales.
6. **2 features nuevas deliberadamente personales**: "Mensajes para ella" (mensajes
   programados por fecha) y notificaciones con voz real pregrabada (clip corto como sonido de
   notificación vía `UNNotificationSound`, mensaje largo reproducible dentro de la app) - solo
   arquitectura/placeholders, el usuario aportará los audios reales después.
7. **Sistema de tokens Glass definitivo** (`GlassDepth/Opacity/Border/Tint/BlurPolicy/Radius/
   Shadow/Highlight/Spacing/Motion/ButtonStyle`) antes de tocar ninguna pantalla.
8. **Revisión visual obligatoria por pantalla** (ejecutar, capturar, comparar antes/después,
   Red Team visual) - no declarar una pantalla terminada solo por compilar.

Deadline sin cambios: 13 de agosto de 2026. Prioridades del propio usuario (sección 27 de su
directiva): P0 = design system Glass, botones transparentes, popups/sheets, Agenda, pantalla
Día, validación iPhone, estabilidad funcional básica. P1 = Etiquetas, Ajustes, edición de
tarea (ya hecha), recordatorios (ya resuelto), mensajes personales, base de notificaciones
con voz. P2 = motion/haptics adicionales. P3 = cualquier cosa no directamente relacionada.

**Restricción técnica real que condiciona todo lo demás, a verificar por el primer batch**:
el "Liquid Glass" real de Apple (materiales del sistema, blur de verdad) es una API nativa
UIKit/SwiftUI; Compose Multiplatform no tiene acceso directo a ella salvo interop nativo
(`UIKitInteropProperties(placedAsOverlay=true)`, disponible desde CMP 1.10.0-beta01 según la
investigación de la sesión anterior - el proyecto está en 1.9.3). Hasta que el primer agente
de investigación confirme el estado real, "cristal fuerte" se construye con las técnicas ya
validadas hoy (transparencia por capas, borde, highlight, blur real acotado a superficies
estáticas sin scroll) - no asumir que se puede invocar `.ultraThinMaterial` de SwiftUI desde
Kotlin sin más.

Batches del propio usuario, en orden: A (design system) → B (Agenda) → C (Día) → D (modals) →
E (Etiquetas+Ajustes) → F (personal/voz) → G (QA/polish). En paralelo cuando no compartan
archivo.

## BASELINE (2026-08-09, confirmado antes de tocar nada)
- Branch de trabajo: `agent/operacion-aniversario` (creada desde `main`@`dd1c159`).
- `main` estaba limpio salvo `.worktrees/` (untracked, no es código de producto).
- Sin diffs pendientes desde la auditoría forense del 9 de agosto.
- Tests: 90/90 verdes (última ejecución real, cacheada por Gradle como `UP-TO-DATE`, sin cambios de código desde entonces).
- Entorno: Windows, sin Xcode/macOS (`xcodebuild`/`xcrun` no encontrados) → **iOS: BLOQUEADO POR ENTORNO**, nunca compilado en el historial del repo.
- Android SDK platform-tools presente (`adb.exe`), `ANDROID_HOME` sin configurar, `adb devices` sin dispositivos/emuladores activos → **QA en dispositivo Android: sin evidencia disponible hasta que se lance un emulador o se conecte un dispositivo**.
- Supabase: MCP conectado en esta sesión apunta a un proyecto ajeno (`oposibots-ui`), no al backend real de AgendNote.

## ACTIVE_AGENTS
| Agente | Rol | Modo | Archivos | Estado |
|---|---|---|---|---|
| Software Architect | Implementar edición de tarea, pasos 1-7 (dominio/repo/ViewModel/Controller + tests) | Producer, ejecuta Gradle | `AgendaTaskRepository.kt`, `SupabaseAgendaTaskRepository.kt`, `AgendaViewModel.kt`, `AgendaController.kt` + 3 tests | En curso |
Ninguno en este momento.

## COMPLETED (7-9) — corrección post-revisión y build final

7. **Fix — 3 bugs de la revisión adversarial**. Commit `acfd26d`. Verificado por mí releyendo el diff real (no solo el resumen del agente): el flag `remindersTouched` corta exactamente el camino de pérdida de datos (si es `false`, `reminders` viaja como `null`, Ktor lo omite del JSON por `explicitNulls=false`, la Edge Function nunca ve la clave `reminders` y no toca la columna). Templates ahora con el mismo guard `mode is TaskSheetMode.Create` que ya usaban "Repetir"/"Guardar como plantilla". `editingTask` en `AgendaScreen.kt` ahora es `remember(editingTaskId)`, no se recalcula en cada recomposición de `sourceTasks`. Verificado: `BUILD SUCCESSFUL`, incluye 2 tests de regresión nuevos que fuerzan `remindersTouched=false → reminders=null` incluso con un draft no vacío.
8. **Build final de verificación**: `:androidApp:assembleDebug` → `BUILD SUCCESSFUL`, APK generada con absolutamente todo lo de esta operación integrado (tokens Glass, iOS P0 quick wins, edición de tarea completa + su corrección, aviso de recordatorios).

## Gate B (core usability) — código-completo, revisado, **pendiente de QA en dispositivo**
No se declara "cerrado" en sentido estricto (regla del propio prompt de operación: no declarar validación sin haberla hecho). Falta el recorrido manual real - ver `docs/agendnote/IOS_VERIFICATION_CHECKLIST.md` para iOS; falta un equivalente Android, pendiente de que el usuario lance un emulador (este entorno no tiene herramientas de interacción con UI de Android/iOS, solo puede compilar y ejecutar tests).

## REVISIÓN ADVERSARIAL — edición de tareas (commits `3062db9`/`bc2ebd2`)

**NO aprobado en primera pasada.** Un reviewer independiente (no el mismo agente que implementó) encontró, verificado por mí leyendo el código actual:

- 🔴 **Bug 1 (crítico, pérdida de datos real)**: guardar una edición sin tocar la sección "Recordatorios" puede borrar recordatorios existentes en silencio. `toUpdateTaskRequest` siempre envía `reminders` (nunca omitido); la Edge Function borra-y-reemplaza todos los recordatorios en cuanto esa clave está presente; el prefill de qué presets estaban activos es un heurístico de comparación de milisegundos que puede fallar (cambio de huso horario, datos no originados en estos mismos presets) sin ningún aviso al usuario, y el guardado reporta éxito igualmente.
- 🟡 **Bug 2 (medio)**: los chips de "aplicar plantilla" no tienen guard de modo (a diferencia de "Repetir"/"Guardar como plantilla", que sí lo tienen) - tocar uno en modo Edit sobrescribe la tarea completa, incluido el estado `isDone` de subtareas ya hechas, sin confirmación.
- 🟡 **Bug 3 (medio)**: si la tarea desaparece de la lista cacheada mientras se edita, el sheet se cierra solo sin avisar y sin preservar lo tecleado.
- Verificado como correcto por la revisión (no requieren cambio): sentinel `""`, protección contra desincronizar una serie recurrente, comportamiento de `moveTask` con día no cacheado (solo hueco visual, datos ya guardados bien), protección contra doble-guardado, manejo de error que preserva el formulario, flujo de creación intacto, subtareas ida-y-vuelta correctas.

Batch de corrección en curso (ver ACTIVE_AGENTS). **El Gate B (core usability) no se considera cerrado hasta que este batch de corrección verifique en verde y, si el tiempo lo permite, reciba una segunda pasada de revisión sobre el diff de la corrección.**

## COMPLETED
1. **Investigación — Plan de edición de tarea** (Software Architect). Hallazgos clave:
   - **Sentinel `""` para borrar campos opcionales**: con `explicitNulls=false` en el `Json` del Ktor client, un campo Kotlin en `null` se omite del body en vez de viajar como `"campo": null`, y `hasField()` en la Edge Function decide por presencia de clave. Enviar `null` para "borrar hora/deadline/notas" NO los borra en el backend - hay que enviar `""`, que `normalizeOptionalString` ya convierte a `null` server-side. Sin esto, editar para quitar una hora fallaría en silencio.
   - Reprogramar notificaciones al editar sale gratis SI el nuevo código pasa por `AgendaViewModel.setTasks()`/`replaceTask()` (que ya reconcilia notificaciones) - regla explícita: nunca mutar `uiState` a mano fuera de esas funciones.
   - `replaceTask()` no soporta mover una tarea de día - hace falta un `moveTask()` nuevo o una tarea editada que cambia de fecha queda duplicada/fantasma.
   - Plan de 11 pasos, orden dominio→repo→tests→viewmodel→tests→controller→UI (`NewTaskSheet` en modo `Edit`, tamaño L, mayor riesgo)→`TaskDetailsOverlay`→wiring. Recurrencia sigue expresamente fuera de alcance (oculta en modo edición, no bloqueada).
   - Archivos: `AgendaTaskRepository.kt`, `SupabaseAgendaTaskRepository.kt`, `AgendaViewModel.kt`, `AgendaController.kt`, `AgendaOverlays.kt`, `AgendaScreen.kt` + 3 archivos de test. Cero cambios propuestos en DTOs/ApiClient/Edge Function.
2. **Investigación — Especificación del sistema Glass** (UI Designer). Hallazgos clave:
   - Blur de fondo real: en Android requiere API 31+ (proyecto tiene minSdk 24, no-op silencioso por debajo); en iOS/Skia no tiene gate de versión pero solo puede desenfocar lo que Compose ya dibuja, no un backdrop real detrás de un `Dialog` - por eso el `.blur(sheetBlur)` ya existente en `AgendaOverlays.kt` no tiene ningún efecto visible hoy (blurrea un scrim de color sólido).
   - Blur nativo real (`UIVisualEffectView` vía overlay UIKit) solo disponible desde CMP 1.10.0-beta01; el proyecto está en 1.9.3 - no perseguir esta vía antes del 13.
   - Gramática Glass formalizada en 4 niveles (L0 fondo, L1 fundido, L2 flotante, L3 modal) con tokens de radio/elevación nombrados, sin cambiar ningún valor de color existente.
   - Gaps reales: `GlassTextField` no distinguía enfocado/deshabilitado; `GlassActionButton` (CTA primario) flotaba a elevación 0 por defecto (bug de inconsistencia); `GlassConfirmDialog` sin icono para reforzar semántica destructiva.
   - Propuesta de sheet más nativo: esquinas solo superiores, edge-to-edge, grabber, drag-to-dismiss - `ModalBottomSheet` de Material3 desaconsejado por ahora (issue conocido de animación rota en iOS, no verificable sin Xcode).
3. **Investigación — Revisión estática iOS** (Mobile App Builder). Hallazgos clave, priorizados P0:
   - **Haptics ausentes en toda la app** (grep sin resultados) - vía recomendada: `LocalHapticFeedback` común de Compose (puenteado a `UIFeedbackGenerator` desde CMP ~1.7+, cero cinterop manual a iosMain).
   - **Swipe de tarjeta sin guarda de borde** - inconsistente con `DatePickerOverlay`, que sí la tiene; copiar el mismo patrón (XS, bajo riesgo).
   - **Sin `imePadding()` en `NewTaskSheet`** - riesgo real de que el teclado tape "Nueva subtarea"/"Crear etiqueta" en el formulario largo.
   - P1: sheet como `Dialog` centrado en vez de hoja nativa (mismo hallazgo que el UI Designer); `TaskCard` sin `semantics(mergeDescendants)`.
   - P2 (no tocar ahora): reduce motion/transparency de iOS no reactivo (requiere `NSNotificationCenter`, único punto que sí requeriría tocar `iosMain` sin poder compilar); doble-tap-scroll-arriba del tab bar.
   - Entregó un checklist exacto de comandos `xcodebuild`/`xcrun simctl` para cuando haya Mac.
4. **Batch de código — Fundamentos de tokens Glass (XS+S)**. Commit `1c679d4`. Aditivo, sin tocar `AgendaOverlays.kt`/`AgendaDayComponents.kt`. Cambios: `GlassTokens` gana `focusStroke`/`glassFillDisabled`/`textDisabled`; nuevo `GlassRadius`/`GlassElevation` en `core/ui/theme/GlassMetrics.kt`; `GlassTextField` distingue enfocado/deshabilitado; `GlassActionButton` gana elevación explícita (bug fix: el CTA primario flotaba a elevación 0); `GlassConfirmDialog` gana `icon` opcional; radios tokenizados sin cambiar valores. Verificado: `BUILD SUCCESSFUL`, 90/90.
5. **Batch de código — iOS P0 quick wins**. Commit (ver `git log`, mensaje `feat(agenda): edge-guard swipe, haptics, and merged a11y for task cards`). Solo `AgendaDayComponents.kt`. Guarda de borde en swipe (copiado del patrón de `DatePickerOverlay`), haptics vía `LocalHapticFeedback` común (completar/borrar, swipe + botones), `TaskCard` con `semantics(mergeDescendants=true)`. Verificado: `BUILD SUCCESSFUL`, 90/90. **No verificado en dispositivo/simulador real** (revisión estática únicamente - pendiente de Xcode).
6. **Decisión — Recordatorios múltiples: Opción B** (ajustar la UI a la realidad, no reescribir el scheduler). Evidencia clave: esta misma discrepancia ya fue evaluada y diferida el 2026-08-04 por las mismas restricciones de entorno (sin Gradle/emulador entonces tampoco); `AndroidReminderStore` usa una única clave `taskId` sin noción de índice de recordatorio - extenderlo a N alarmas reales toca el componente de notificaciones ya probado en dispositivo real en julio (mayor riesgo del proyecto) sin forma de re-verificarlo aquí. iOS sería mecánicamente más simple (S) pero cero historial de compilación. **Cambio aplicado** (pendiente de verificar por build, ver ACTIVE_TASKS): aviso de una línea en `NewTaskSheet` (`AgendaOverlays.kt`, sección "Recordatorios") cuando el usuario selecciona 2+ recordatorios, explicando honestamente que solo se dispara el más próximo. Cero cambios en `androidMain`/`iosMain`/backend. Retirar el aviso cuando exista multi-notificación real verificada en dispositivo.

## ACTIVE_TASKS / BLOCKED / FAILED
- El aviso de recordatorios múltiples (COMPLETED #6) está editado en `AgendaOverlays.kt` pero **sin verificar por build todavía** - deliberadamente diferido porque hay un Producer con Gradle activo (ver ACTIVE_AGENTS) y no se ejecuta Gradle en paralelo. Se verifica y commitea en cuanto ese Producer termine.

## PENDIENTE PARA CUANDO HAYA XCODE (usuario confirmó acceso antes del 13)
- Verificar visualmente los 2 batches de código ya commiteados (tokens Glass, iOS P0 quick wins) en simulador/dispositivo real - nada de esto se ha visto en pantalla todavía.
- Confirmar qué `HapticFeedbackType` están realmente puenteados en la versión de Compose Multiplatform del proyecto (1.9.3) antes de diferenciar intensidad completar vs. borrar.
- Ejecutar el checklist completo que entregó el agente de revisión iOS (comandos `xcodebuild`/`xcrun simctl`, guardado en el detalle de este documento más abajo si se solicita).

## P0 / P1 / P2 / P3
Ver sección E de la respuesta operativa del 9 de agosto en el chat — se traerá aquí en el primer batch de código.

## DECISIONS
- 2026-08-09 — Rama de trabajo dedicada `agent/operacion-aniversario`, sin push a remoto salvo instrucción explícita del usuario.
- 2026-08-09 — Booking/portfolio: no se toca schema/Edge Function hasta confirmar que el sitio externo está descontinuado (regla por defecto del prompt de operación).
- 2026-08-09 — `notes`: no se construye feature nueva sobre esa tabla sin evidencia de que debe existir.
- 2026-08-09 — Usuario confirma: SÍ habrá acceso a Mac/Xcode antes del 13 → el gate de iOS se planifica como compilación real (Fase 1/Lane A), no solo revisión estática. La revisión estática en curso (Agente Mobile App Builder) prepara el terreno para minimizar sorpresas en la primera compilación.
- 2026-08-09 — Usuario confirma: SÍ puede lanzar un emulador Android para QA cuando se le pida → el Agente 12 (QA funcional) podrá ejecutarse con evidencia real, no solo guiones.
- 2026-08-09 — Usuario confirma: todo el trabajo se queda en la rama local `agent/operacion-aniversario`; sin push a origin salvo petición explícita.

## FILES_LOCKED
Ninguno todavía — los agentes de este primer batch son de investigación (solo lectura), sin escritura de código.

## KNOWN_RISKS
1. iOS nunca compilado — mayor riesgo del proyecto.
2. Cero QA visual desde el 27 de julio sobre la mayoría de la superficie de producto actual.
3. Recordatorios múltiples: UI promete N, sistema dispara 1.
4. Estado real del portfolio externo no confirmado.

## LAST_TEST_RESULT
90/90 verdes, `:composeApp:testDebugUnitTest`/`testReleaseUnitTest`, `BUILD SUCCESSFUL` (cacheado, 2026-08-09).

## LAST_BUILD_RESULT
`BUILD SUCCESSFUL` (Android, cacheado, 2026-08-09). iOS: sin datos.

## IOS_STATUS
BLOQUEADO POR ENTORNO EN ESTA MÁQUINA (Windows, sin Xcode) — pero el usuario confirma acceso a Mac/Xcode antes del 13. Plan: revisión estática ahora (minimiza riesgo), compilación real cuando el usuario dé acceso o ejecute los comandos exactos que se le entregarán (Gate/Lane A). No se declarará iOS "validado" hasta que exista una compilación real reportada.

## BACKEND_STATUS
Sin acceso en vivo al Supabase real de AgendNote. Auditoría solo-repo (`schema.sql`/`policies.sql`/`functions/`).
