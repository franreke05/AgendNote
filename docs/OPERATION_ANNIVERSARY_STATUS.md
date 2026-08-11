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
Ninguno. Los 3 de la ronda relanzada ya entregaron - ver COMPLETED (10), (11), (12).

## COMPLETED (11) — investigación Liquid Glass + contrato de tokens Glass completo
Hallazgo técnico central, con fuentes verificadas: Liquid Glass real de Apple (WWDC25/iOS 26)
es **exclusivamente** una API nativa SwiftUI (`.glassEffect()`)/UIKit (`UIGlassEffect`) - cero
ruta de Kotlin/Native salvo interop manual completo. **Corrección real sobre la investigación
anterior de esta misma operación**: `UIKitInteropProperties(placedAsOverlay=true)` ya está en
una release **estable** de Compose Multiplatform (1.10.0, no solo beta) desde enero 2026 - dato
nuevo que la primera pasada no tenía. Aun así, **no se persigue esta semana**: el proyecto está
en 1.9.3, nunca ha compilado para iOS, y el propio patrón tiene limitaciones reales documentadas
(z-order con Dialogs/Snackbars Compose, sin sincronía de scroll) - subir de versión de framework
sin haber compilado nunca para iOS es un riesgo que no se justifica a 4 días del plazo.
Contrato de tokens completo especificado (`GlassDepth`, `GlassOpacity`, `GlassBorder`,
`GlassTint`, `GlassBlurPolicy`, `GlassShadow`, `GlassHighlight`, `GlassSpacing`, `GlassMotion`
extendido, `GlassButtonStyle` de 4 variantes, `GlassFloatingActionButton`, tab bar con 2
opciones (A: floating real invirtiendo el layout de `AppNavHost`, mayor riesgo; B: solo visual,
sin invertir - **recomendada para el 13**)). Decisión de arquitectura explícita del propio
informe: `GlassButton.Primary/Secondary` deben ser una fachada nueva que **reutiliza** el cuerpo
de `GlassActionButton` ya existente (54 call sites), no un reemplazo - cero riesgo de romperlos.

## COMPLETED (12) — arquitectura de "Mensajes para ella" + notificaciones con voz
Especificación completa (SQL, Edge Function, capa Kotlin, integración de notificaciones,
reproductor Glass), sin implementar. Decisiones clave:
- Audio **empaquetado en el cliente** (no Supabase Storage) para v1, en ambos modos - es la
  única opción viable para `notification_clip` de todas formas (restricción real de
  plataforma, verificada: iOS/Android exigen el sonido de una notificación local en el propio
  bundle/APK, no se puede streamear). Columna `audio_source` reservada para migrar
  `voice_message` a Storage en el futuro sin otra migración de esquema.
- **Confirmado con fuente oficial Android**: el sonido de un canal de notificación se fija en
  su primera creación y no se puede cambiar - por eso cada clip de audio necesita su propio
  canal (`channelId` derivado del asset), no se puede reutilizar el canal de recordatorios de
  tareas ya existente.
- Convención de nombres de assets ya definida para que sustituir por audio real no toque
  código: `audio_asset_key` (sin extensión) → Android `res/raw/<key>.wav`, iOS
  `Sounds/<key>.caf`.
- Placeholder honesto: cero binarios de audio generados (correcto, no se pidió); toda la
  demo es ejercitable con `audio_mode = NONE` (cae al sonido por defecto del sistema).
- Pieza nueva de verdad no anticipada: iOS necesita un `UNUserNotificationCenterDelegate`
  (no existe hoy en el proyecto) para capturar el toque en modo "Voice Message" y abrir el
  reproductor - no es una extensión de código existente.
- Tamaño total: M-L. Recomendación explícita del propio informe: arquitectura + notificación
  funcional con `audio_mode=NONE` es razonable para el 13; canal-por-asset con audio real y
  Storage quedan como fase 2, sin presión de fecha.

## COMPLETED (10) — inventario completo de popups + plan de rediseño de Listas Inteligentes
Investigación pura, sin código todavía. Hallazgos clave:
- Inventario exhaustivo: cero `AlertDialog`/`ModalBottomSheet` en todo el proyecto - **todo**
  overlay está construido a mano sobre `Dialog()` + `GlassSurface`. 9 componentes catalogados.
- `ConfirmDeleteDialog`/`GlassConfirmDialog` (ALERT, 5 call sites) es **el que mejor encaja ya**
  con su categoría - cambio menor.
- `TimePickerOverlay` es el más cercano al lenguaje nativo de iPhone hoy (wheel picker +
  Cancelar/Listo, ya anclado abajo) - cambio menor.
- `CalendarPopover` (el que integré yo mismo hace un momento): confirmado que hoy es
  indistinguible de un diálogo a pantalla completa - necesita aligerarse (P1, no P0).
- `DatePickerOverlay`: hallazgo nuevo real - al vivir anidado dentro del `Dialog` de
  `NewTaskSheet`, se apilan **dos scrims** (doble oscurecimiento) - defecto visual real, no
  solo de estilo.
- **Plan completo de rediseño de `SmartListsOverlay`** (P0 explícito del usuario): sheet de
  altura parcial anclada abajo, esquinas solo superiores, grabber, dos niveles (resumen de 5
  filas con icono+conteo → drill-down a la lista de tareas), cierre por swipe/tap-fuera/×
  discreto (se elimina el botón "Cerrar" de ancho completo actual). Contrato público sin
  cambios (`tasksByDate`, `today`, `onSelectDate`, `onDismiss` iguales). Usa únicamente tokens
  ya existentes (`GlassRadius`, `GlassElevation`, `GlassTheme.tokens`) - no bloquea con la
  investigación de tokens en curso.
Implementación: empiezo ahora mismo (self-contenida, no depende de los otros 2 agentes).

## CORRECCIÓN a una investigación previa (verificado por mí, no confiado a ciegas)
La investigación "Glass design spec" del primer batch de esta operación afirmó que
`.blur(sheetBlur)` en `NewTaskSheet` "blurrea un scrim de color sólido, sin efecto visible".
**Falso, verificado con conteo de llaves preciso (no solo lectura visual) antes de actuar**: el
`Box` que lleva el `.blur()` envuelve TANTO el scrim COMO el `GlassSurface` completo del
formulario (cierra en la línea ~1670, no justo después del scrim) - el blur sí tiene efecto
real: difumina todo el sheet de fondo cuando se abre el selector de hora encima. No se ha
tocado ni se va a tocar este código. Lección aplicada: no ejecutar una "corrección" de un
hallazgo de subagente sin releer el código real primero, incluso cuando el hallazgo viene con
mucho detalle - por eso no se llegó a commitear el error.

## COMPLETED (9) — reestructura de navegación + Día + popover de calendario
Commit `6198d74`. Hecho directamente (sin subagente, mientras el límite de sesión estaba caído):
- Nav: Agenda / **Día** / Etiquetas / Ajustes (ya no hay pestaña Calendario independiente).
- `DayScreen.kt` nuevo: timeline por horas, reutilizando `DayHourAgenda` - un composable que
  llevaba en el repo desde el 25 de julio **sin ningún call site**, confirmado por grep antes de
  reusarlo. Añadido: línea de hora actual, tap-tarea→detalle, tap-hora-vacía→crear (fecha
  precargada; hora exacta diferida, ver comentario en el archivo).
- Calendario mensual: reubicado a un popover (`CalendarPopover` en `AgendaOverlays.kt`) desde un
  botón nuevo en la cabecera de Agenda - `CalendarMonthView` no se tocó por dentro salvo quitar
  el tachado de días pasados.
- **Corregido el hallazgo visual del propio usuario**: tachado de días pasados en el calendario
  eliminado (se leía como "cancelado"). La atenuación de opacidad + `stateDescription`
  "Fecha pasada" ya comunican correctamente que es un día pasado.
- `CalendarScreen.kt` eliminado (superfluo tras el cambio).
- **Deuda honesta**: la cabecera de Agenda tiene ahora 4 botones de icono (listas inteligentes,
  calendario, día anterior, día siguiente) - exactamente lo que el usuario pidió evitar. Estado
  funcional temporal, documentado in-line, pendiente del rediseño visual real (BATCH A/B).
Verificado: `BUILD SUCCESSFUL`, 101/101 tests.

## COMPLETED (7-8) — corrección post-revisión y build final

7. **Fix — 3 bugs de la revisión adversarial**. Commit `acfd26d`. Verificado por mí releyendo el diff real (no solo el resumen del agente): el flag `remindersTouched` corta exactamente el camino de pérdida de datos (si es `false`, `reminders` viaja como `null`, Ktor lo omite del JSON por `explicitNulls=false`, la Edge Function nunca ve la clave `reminders` y no toca la columna). Templates ahora con el mismo guard `mode is TaskSheetMode.Create` que ya usaban "Repetir"/"Guardar como plantilla". `editingTask` en `AgendaScreen.kt` ahora es `remember(editingTaskId)`, no se recalcula en cada recomposición de `sourceTasks`. Verificado: `BUILD SUCCESSFUL`, incluye 2 tests de regresión nuevos que fuerzan `remindersTouched=false → reminders=null` incluso con un draft no vacío.
8. **Build final de verificación**: `:androidApp:assembleDebug` → `BUILD SUCCESSFUL`, APK generada con absolutamente todo lo de esta operación integrado (tokens Glass, iOS P0 quick wins, edición de tarea completa + su corrección, aviso de recordatorios).

## COMPLETED (13) — FAB coral translúcido + sistema de botones nombrado
Commit `e284a8a`. P0 visual explícito del usuario. `FloatingAddButton` (compartido por Agenda y
Día) deja de ser un círculo blanco plano: tinte coral translúcido (`accent@22%`, `@34%` al
pulsar), borde `accentOnLight`, press-scale. Nuevo `core/ui/components/GlassButtons.kt`:
`GlassButton.Primary/Secondary` y `GlassDestructiveButton`, envolviendo (no reemplazando)
`GlassActionButton` ya existente - decisión explícita de la investigación de tokens para no
arriesgar los 54 call sites actuales a 4 días del plazo. Verificado: `BUILD SUCCESSFUL`, 101/101.
Build final consolidado: `androidApp:assembleDebug` → `BUILD SUCCESSFUL`, APK con absolutamente
todo lo de esta directiva ampliada integrado.

## COMPLETED (14) — pasada de UI/popups: base compartida congelada ("Design System Freeze V1")
Commit `d03dff6`. Nueva directiva del usuario: unificar TODOS los popups/sheets/overlays de la
app bajo una base común, con inventario reaprovechado de COMPLETED (10). Nuevo archivo
`core/ui/components/GlassPresentation.kt`, único writer, congelado antes de que nadie lo
consuma: `GlassScrim` (alpha por peso: alert > sheet > popover, derivado siempre de
`GlassTheme.tokens.scrim`, nunca un color nuevo por popup), `GlassScrimLayer`, `GlassGrabber`,
`GlassSheetScaffold` (categoría SHEET: anclado abajo, esquinas solo arriba, grabber,
drag-to-dismiss - extraído del patrón ya probado en el rediseño de Listas Inteligentes),
`GlassPopover` (categoría POPOVER: compacto, ajustado a contenido, scrim más ligero -
**documentado honestamente que NO es un popover anclado de verdad**, sin posición medida del
control que lo abre, eso es una feature mayor no abordada esta semana). `GlassConfirmDialog`
gana ancho máximo (340dp, antes se estiraba sin límite) + alias `GlassAlert` (ya era la
implementación correcta de ALERT según el inventario previo, solo le faltaba el nombre).
Verificado: `BUILD SUCCESSFUL`, 101/101. **Aviso honesto**: nada de esto se ha visto en pantalla,
solo compila y pasa tests - no hay herramienta de captura de pantalla en este entorno.

## ACTIVE_AGENTS
| Agente | Rol | Modo | Archivos | Estado |
|---|---|---|---|---|
| Code Reviewer | Revisión adversarial del commit `f411398` (migración de 6 presentaciones a la base Glass) | Solo lectura | `AgendaOverlays.kt` | En curso |

Ninguno.

## COMPLETED (15) — migración de 6 presentaciones a la base compartida, revisada y corregida
Commits `f411398` + `f10ceb8`. `NewTaskSheet`, `TaskDetailsOverlay`, `SmartListsOverlay` →
`GlassSheetScaffold`; `DatePickerOverlay`, `TimePickerOverlay`, `CalendarPopover` →
`GlassPopover`. Revisión adversarial independiente con chequeo automatizado de balance de
llaves por función (no solo lectura del diff) dado el historial de esta sesión: **sin
hallazgos críticos** - la lógica de negocio de `NewTaskSheet` (Create/Edit, `remindersTouched`,
validaciones, routing de guardado) quedó intacta, y el doble-scrim de los pickers
(estaban anidados sin `Dialog` propio dentro del de `NewTaskSheet`) se resolvió
estructuralmente, no solo cosméticamente. 2 hallazgos medios corregidos:
- `NewTaskSheet` heredó `dragToDismissEnabled=true` del scaffold por defecto - un formulario
  largo no debe poder cerrarse con un arrastre accidental sin aviso. Desactivado solo ahí
  (`TaskDetailsOverlay`, de solo lectura, lo conserva - sin riesgo de pérdida de datos).
- `CalendarPopover`: el padding de `GlassPopover` se apilaba con el de `CalendarMonthView`,
  estrechando la cuadrícula de 7 columnas. Compensado ensanchando el popover - **sin verificar
  visualmente**, marcado explícitamente para revisar en un teléfono compacto.
Verificado: `BUILD SUCCESSFUL`, 101/101 tests, ambas veces.

**Lo que queda de la directiva de popups, sin hacer todavía**: selector de color de Etiquetas
como `GlassPopover` (depende de simplificar Etiquetas primero, sin tocar); haptics en
selección de fecha/color (sección 13 de la directiva); unificación de motion de entrada/salida
de sheet/popover (sección 14); QA de teclado/safe areas real (solo verificable en
dispositivo); Visual Red Team real (requiere ver la app corriendo, no soy capaz de hacerlo yo
mismo en este entorno).

Pendiente para después (mismo `AgendaOverlays.kt`, no en paralelo): nada más - es el único
archivo grande que quedaba. Pendiente en OTROS archivos, para una ronda posterior: selector de
color de Etiquetas como `GlassPopover` (depende de la simplificación de Etiquetas, todavía sin
hacer), QA de teclado/safe areas (checklist, no ejecutable en este entorno), Visual Red Team
(requiere ver la app corriendo - no soy capaz de hacerlo yo mismo en este entorno).

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
- 2026-08-09 — Booking/portfolio: no se toca schema/Edge Function hasta confirmar que el sitio externo está descontinuado (regla por defecto del prompt de operación). **SUPERADA el 2026-08-11** - ver más abajo: el usuario dio confirmación explícita y pegó el esquema real de producción, autorizando la eliminación completa.
- 2026-08-11 — **Reconciliación de backend: eliminación completa del sistema de booking/portfolio**, por instrucción explícita del usuario con el esquema real de producción como evidencia. El usuario pegó el schema real (con tablas `appointments`/`portfolio_labels` que ni siquiera estaban versionadas en `schema.sql` - deriva real confirmada entre lo desplegado y lo versionado). Se limpiaron: `schema.sql` (columnas de booking fuera de `tasks`, bloque de conversión de `appointment_id` eliminado), `supabase/functions/api-tasks/index.ts` (`TASK_SELECT`, `buildInsertPayload`/`buildUpdatePayload`, `resolveSource`, `fetchTaskByAppointmentId`, ramas de upsert-por-appointment en POST/PATCH), `supabase/SETUP.md` (sección "Contrato de tareas con Portfolio" reescrita), `supabase/sql-editor-booking-mirror.sql` eliminado (obsoleto, era para AÑADIR esas columnas). Nueva migración `supabase/migrations/20260811_remove_booking_portfolio_system.sql` (DROP de tablas/columnas), **NO aplicada** - sin acceso al Supabase real de AgendNote desde este entorno (el MCP conectado apunta a otro proyecto). El usuario debe aplicarla manualmente (o dar acceso real al MCP correcto) antes de desplegar la Edge Function actualizada - el propio archivo de migración documenta el orden correcto (Edge Function y migración deben desplegarse juntas, no la Edge Function sola primero contra un esquema todavía sin migrar). Sin cambios en Kotlin (ya estaba limpio desde el 24 de julio, confirmado por grep antes de tocar nada).
- 2026-08-09 — `notes`: no se construye feature nueva sobre esa tabla sin evidencia de que debe existir.
- 2026-08-09 — Usuario confirma: SÍ habrá acceso a Mac/Xcode antes del 13 → el gate de iOS se planifica como compilación real (Fase 1/Lane A), no solo revisión estática. La revisión estática en curso (Agente Mobile App Builder) prepara el terreno para minimizar sorpresas en la primera compilación.
- 2026-08-09 — Usuario confirma: SÍ puede lanzar un emulador Android para QA cuando se le pida → el Agente 12 (QA funcional) podrá ejecutarse con evidencia real, no solo guiones.
- 2026-08-09 — Usuario confirma: todo el trabajo se queda en la rama local `agent/operacion-aniversario`; sin push a origin salvo petición explícita.

## FILES_LOCKED
Ninguno todavía — los agentes de este primer batch son de investigación (solo lectura), sin escritura de código.

## KNOWN_RISKS
0. **[RESUELTO 2026-08-11] La BD real de producción nunca recibió
   3 migraciones ya escritas desde julio/agosto**: `task_series`, `task_reminders`,
   `task_subtasks` no existen como tablas; `tasks.deadline_at`/`slot_end_at`/`series_id` no
   existen como columnas. Verificado dos veces por fotografía pegada por el usuario, y una
   tercera vez **por acceso MCP real y en vivo** al proyecto `pdcxxhnybykfbbvnnzki`
   ("AgendNotes") el 2026-08-11 (`list_tables` + `list_migrations`, ambas de solo lectura):
   coincide exactamente, y `list_migrations` devuelve vacío (cero migraciones aplicadas nunca).
   Las 8 tablas reales tienen **0 filas** — no hay datos en riesgo. Consecuencia: deadline,
   recordatorios múltiples, subtareas y series recurrentes — funcionalidad ya implementada y
   testeada a nivel de Kotlin/Edge Function desde hace días — probablemente ha estado fallando
   en silencio contra la base de datos real todo este tiempo. Ver
   `supabase/RECONCILIATION_2026-08-11.md` para el análisis completo y el procedimiento exacto
   de despliegue. **Nada de esto se ha aplicado todavía** — acceso confirmado, pero se sigue
   esperando confirmación explícita del usuario antes de ejecutar cualquier `apply_migration`
   contra producción.
0b. **[RESUELTO 2026-08-11, salvo Edge Functions - ver MANUAL_DELETE_REQUIRED abajo]** La BD real tenía 6 funciones Postgres que no
   existen en ningún archivo del repo, expuestas automáticamente como endpoints RPC públicos de
   PostgREST (`POST /rest/v1/rpc/<nombre>`, llamables con la anon key sin pasar por
   `x-app-secret`): `create_portfolio_appointment` (`SECURITY DEFINER`, `GRANT` a `PUBLIC` —
   crea citas/tareas de booking directamente), `build_portfolio_booking_body`,
   `normalize_email`, `is_email_valid` (las 4 booking-only, sin otros consumidores verificado
   por búsqueda de texto), y además `create_task`/`get_tasks_by_time` (**no** relacionadas con
   booking, también con `GRANT` a `PUBLIC`, sin consumidor conocido — la app nunca usa la API
   RPC de PostgREST). Detalle completo en `supabase/RECONCILIATION_2026-08-11.md`. Ya escritas
   (no aplicadas): DROP de las 4 funciones de booking dentro de
   `20260811_remove_booking_portfolio_system.sql`, y `REVOKE EXECUTE` (no destructivo) de las
   otras 2 en la nueva `20260811_harden_public_rpc_exposure.sql`.
1. iOS nunca compilado — mayor riesgo del proyecto.
2. Cero QA visual desde el 27 de julio sobre la mayoría de la superficie de producto actual.
3. Recordatorios múltiples: UI promete N, sistema dispara 1 (y hasta que `task_reminders`
   exista de verdad en producción, N tampoco llega a guardarse — ver riesgo 0).
4. Sistema de booking/portfolio: **resuelto en código** el 2026-08-11, commit `bf15e89` —
   eliminado del schema, Edge Function y documentación; migración de borrado ampliada con las
   4 funciones descubiertas (riesgo 0b), escrita pero **no aplicada**.

## LAST_TEST_RESULT
90/90 verdes, `:composeApp:testDebugUnitTest`/`testReleaseUnitTest`, `BUILD SUCCESSFUL` (cacheado, 2026-08-09).

## LAST_BUILD_RESULT
`BUILD SUCCESSFUL` (Android, cacheado, 2026-08-09). iOS: sin datos.

## IOS_STATUS
BLOQUEADO POR ENTORNO EN ESTA MÁQUINA (Windows, sin Xcode) — pero el usuario confirma acceso a Mac/Xcode antes del 13. Plan: revisión estática ahora (minimiza riesgo), compilación real cuando el usuario dé acceso o ejecute los comandos exactos que se le entregarán (Gate/Lane A). No se declarará iOS "validado" hasta que exista una compilación real reportada.

## BACKEND_STATUS
**DEPLOYED_AND_VERIFIED (2026-08-11)** — reconciliación completa aplicada contra
`pdcxxhnybykfbbvnnzki` real, con autorización explícita del propietario. 6 migraciones en el
historial de Supabase (`list_migrations`): hardening RPC, las 3 aditivas (`task_series`,
`task_reminders`+`task_subtasks`+`deadline_at`, `end_type`/`end_date`/`end_occurrences`), gap
nuevo `slot_end_at` (encontrado en el Check P0, no estaba en producción pese a que el código sí
lo usa), y el DROP final de booking/portfolio (tablas, columnas, 4 funciones RPC, trigger
duplicado) - dentro de transacción, `RESTRICT` explícito, sin `CASCADE`. Edge Functions
`api-tasks`/`api-labels`/`api-settings` redesplegadas con el `_shared/{cors,response,auth}.ts`
actual (la versión previa en producción era más vieja: CORS abierto a `*`, sin
`internalErrorResponse`, comparación de secreto no segura contra timing); `api-task-series`
desplegada por primera vez (nunca había existido). Smoke tests reales contra la BD (create/read/
update-preserva-reminders-y-subtasks/delete-sin-huérfanos) y tests de Kotlin, todos verdes.

Corrección propia: se había afirmado antes "0 filas en las 8 tablas" - era una estimación de
catálogo, no un `COUNT(*)` real; el recuento real (`appointments`=12, `portfolio_labels`=6,
`tasks`=2) eran pruebas de desarrollo sin actividad desde abril, exportadas a
`supabase/backups/2026-08-11_pre_booking_removal_export.md` antes del DROP.

Pendiente de acción manual del propietario (el MCP no expone borrado de Edge Functions):
`create-booking`, `agendnote-create-task`, `agendnote-get-tasks` - 3 funciones activas no
versionadas en el repo, descubiertas esta sesión, ya inertes (dependen de tablas/RPC ya
borradas) pero siguen desplegadas. Ver `supabase/RECONCILIATION_2026-08-11.md`.
