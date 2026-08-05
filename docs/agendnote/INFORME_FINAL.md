# Informe final — Profesionalización de AgendNote (prompt maestro, 2026-08-04)

Rama: `agent/finish-audit-notifications`. 35 commits en esta sesión (`27b222a`..`HEAD`), sobre
los 5 commits previos de la auditoría del 27 de julio. Este documento resume; el detalle
completo de cada decisión está en `docs/agendnote/` (`SCREEN_INVENTORY.md`,
`DESIGN_AUDIT.md`, `ARCHITECTURE_AUDIT.md`, `SECURITY_AUDIT.md`, `FASE4_PROPUESTA.md`,
`FASE7_EVALUACION.md`, `IMPLEMENTATION_LOG.md`) y en `docs/AI_DECISION_LOG.md`.

## Resumen ejecutivo

- **Resultado**: se ejecutaron las 8 fases del plan (`IMPLEMENTATION_PLAN.md`) dentro del
  alcance que este entorno puede sostener con evidencia real. Se conservó la identidad Glass
  sin cambios; se corrigió una fuga de seguridad real (excepciones crudas del backend
  llegando al cliente); se amplió el modelo de tarea (deadline, recordatorios múltiples,
  subtareas) de punta a punta; se robusteció la recurrencia (fin por fecha/número); se
  agregaron captura rápida NLP y listas inteligentes; se evaluaron y, donde fue responsable,
  se implementaron funcionalidades "después" (plantillas, exportación).
- **Estado Android**: verificado por compilación real y build de APK
  (`androidApp:assembleDebug`) repetidamente a lo largo de toda la sesión, no solo al final.
  `BUILD SUCCESSFUL` en la última pasada.
- **Estado iOS**: **no compilado en ningún momento de esta sesión** — no hay macOS/Xcode en
  este entorno. Todo el código `iosMain` tocado (accesibilidad, recordatorios) está escrito
  con el mismo cuidado que el código Android equivalente, pero es código sin ejecutar.
- **Estado tests**: 90/90 en verde, `:composeApp:testDebugUnitTest` y
  `:composeApp:testReleaseUnitTest` (empezó la sesión en 39/39). Prácticamente todo el
  incremento (51 tests nuevos) siguió TDD real: test escrito antes que la implementación,
  RED confirmado (por fallo de aserción o error de compilación), luego GREEN.
- **Riesgos abiertos**: ningún hallazgo de seguridad crítico o alto sigue abierto. La deuda
  real es de **verificación**, no de diseño: nada de lo construido esta sesión se vio en un
  emulador o dispositivo físico. Ver "Deuda restante" al final.

## Sistema Glass

- **Se mantuvo**: gradiente, manchas de luz, grano, Manrope, acento coral, todos los
  componentes `Glass*` existentes. No se rediseñó nada.
- **Se profesionalizó**: reduce motion (el fade de imagen de fondo respeta la preferencia del
  sistema en Android, verificado por compilación) y reduce transparency (las manchas de luz y
  el grano se ocultan cuando está activo — solo en iOS hoy, Android no tiene equivalente
  público). Undo con snackbar al completar una tarea, antes silencioso.
- **Componentes nuevos**: `GlassSnackbar` (primer snackbar del sistema), `GlassEmptyState`
  (extracción de una duplicación real entre Agenda y Etiquetas). Ningún componente nuevo sin
  reutilización comprobada.

## Pantallas

| Pantalla | Antes | Después | Commits | Tests | Deuda |
|---|---|---|---|---|---|
| Agenda (crear tarea) | Sin deadline/recordatorios/subtareas/plantillas; completar era silencioso | Deadline, recordatorios múltiples (uno programado hoy), subtareas, sugerencia NLP, plantillas, undo con snackbar | ~15 | +30 aprox. | Sin QA visual |
| Agenda (detalle) | Solo título/hora/etiquetas, solo lectura | + deadline, subtareas (lectura), sin edición todavía (gap preexistente) | 3 | 0 (UI pura) | Sin QA visual; sin edición de tarea existente |
| Agenda (header) | Navegación de día únicamente | + botón de Listas inteligentes | 1 | 0 | Sin QA visual |
| Listas inteligentes (nueva) | No existía | Overlay con 5 vistas (Atrasadas/Próximos 7/Sin hora/Con recordatorio/Recurrentes) | 2 | 6 | Sin QA visual; solo ve datos ya cargados |
| Recurrencia (creación) | Sin fin, sin verificación de DST | Fin por fecha o número de repeticiones; DST/bisiesto/cruce de año verificados sin bugs | 3 | 15 | "Editar esta y las siguientes" bloqueada (sin edición de tarea) |
| Ajustes | Sin exportación | + sección "Datos" con exportar a portapapeles | 1 | 3 | Sin QA visual; alcance reducido a portapapeles |
| Edge Functions (todas) | Devolvían excepciones crudas | Mensajes genéricos siempre | 1 | 1 (lado Kotlin) | Sin Deno para compilar/probar |

## Arquitectura

- Sin migración de arquitectura. Se mantuvo Clean/MVVM por feature, `mutableStateOf` como
  estado de pantalla, `Channel` FIFO para comandos de notificación (patrón ya existente,
  reutilizado para nada nuevo).
- Nuevas funciones puras y testeables añadidas al dominio: `planMaterialization`,
  `smartListTasks`, `buildTaskExportJson`, `earliestReminderInstant`, `parseQuickCapture` —
  todas sin dependencia de Compose ni de red, todas con tests reales.
- **Hallazgo arquitectónico documentado, no corregido**: no existe ningún método para editar
  una tarea ya creada en todo el repositorio (`AgendaTaskRepository` solo tiene
  `createTask`/`updateTaskDone`/`deleteTask`). Preexistente a esta sesión; limita el alcance
  de deadline/recordatorios/subtareas a la creación, y bloquea "editar esta y las siguientes"
  en recurrencia.

## Seguridad

| Hallazgo | Severidad | Solución | Evidencia | Estado |
|---|---|---|---|---|
| Las 6 Edge Functions devolvían `error.message` crudo de Postgres/PostgREST al cliente | Media | `internalErrorResponse()` compartido, mensaje fijo siempre | `git diff` completo revisado a mano; sin Deno para compilar | Corregido, no verificado en vivo |
| `resolveServerError` (Kotlin) reenviaba texto crudo de `ResponseException` a la UI | Media | Mensaje fijo en español siempre | Test TDD real, RED por fallo de aserción → GREEN | Corregido y verificado |
| `APP_SECRET` embebido en el binario cliente (APK/IPA) | Aceptado, no un hallazgo a "arreglar" | N/A — inherente al modelo mono-usuario que el usuario confirmó mantener | Documentado en `SECURITY_AUDIT.md` | Riesgo residual aceptado |
| Rate limiting ausente en Edge Functions | Baja | No implementado | — | Diferido, documentado |
| Idempotencia ante pérdida de respuesta post-éxito | Baja | No implementado | — | Diferido, documentado |
| Límites de longitud de payload | Baja | No implementado | — | Diferido, documentado |

No se hizo ninguna auditoría en vivo contra el Supabase real de AgendNote — el conector MCP de
este entorno apunta a un proyecto no relacionado (`oposibots-ui`), confirmado y luego
descartado sin seguir explorándolo (ver `SECURITY_AUDIT.md`).

## Recurrencia

- **Modelo**: `RecurrenceRule` (Daily/WeeklyDays/Monthly, sin cambios) + `RecurrenceEnd`
  nuevo (Never/OnDate/AfterOccurrences).
- **Política**: los offsets de recordatorio se calculan sobre un instante de referencia (hora
  planificada, si no fecha límite a las 23:59:59 hora local) — decisión de producto tomada
  según la propia recomendación de `FASE4_PROPUESTA.md`, delegada por el usuario.
- **DST**: verificado, no corregido — `occurrencesBetween` opera solo sobre `LocalDate`, sin
  superficie de bug posible. 5 tests contra las dos transiciones de horario de España en 2026
  y años bisiestos, todos en verde al primer intento.
- **Excepciones**: borrar una ocurrencia materializada ya actúa como excepción (el cursor de
  materialización nunca reintenta una fecha pasada) — comportamiento preexistente, ahora
  explicado en la UI.
- **Idempotencia**: sin cambios — no se tocó el materializador más allá de añadir el fin de
  serie; su comportamiento de reintento seguro (documentado en tests previos a esta sesión)
  no se alteró.

## Funcionalidades

- **Implementadas**: captura rápida NLP es-ES (confirmable), listas inteligentes (5 vistas),
  deadline separado, recordatorios múltiples (guardados; solo el más próximo se programa como
  notificación real — ver deuda), subtareas, fin de serie recurrente, plantillas, exportación
  a portapapeles.
- **Evaluadas y recomendadas para después** (`FASE7_EVALUACION.md`): bloqueo biométrico, deep
  links, calendario de solo lectura, time blocking, widgets — las cinco requieren APIs de
  plataforma o iteración visual que este entorno no puede verificar de forma responsable.
- **Descartadas**: ninguna funcionalidad de las evaluadas se descartó por completo; todas
  quedaron como "después" con una razón concreta, no un "no" genérico.

## Verificación

Comandos ejecutados realmente, repetidos después de cada slice a lo largo de toda la sesión
(no solo al final):

```powershell
.\gradlew.bat :composeApp:testDebugUnitTest :composeApp:testReleaseUnitTest :androidApp:assembleDebug --no-daemon --max-workers=2 "-Dorg.gradle.jvmargs=-Xmx1536m"
```

Resultado de la última pasada: `BUILD SUCCESSFUL`, 90/90 tests en debug y 90/90 en release,
APK de debug generado (`androidApp/build/outputs/apk/debug/androidApp-debug.apk`, ~22 MB).

**No ejecutado, y no se afirma lo contrario en ningún documento de esta sesión**:
- Ninguna compilación ni link de iOS (`iosSimulatorArm64`/Xcode) — sin macOS.
- Ningún `deno check`/`deno test` sobre las Edge Functions — sin Deno instalado.
- Ninguna aplicación real de las dos migraciones SQL nuevas contra una base de datos — sin
  acceso en vivo al Supabase real de AgendNote (decisión del usuario: auditoría solo-repo).
- Ninguna instalación en emulador o dispositivo físico — no se lanzó ninguno esta sesión.
- Ningún lector de pantalla (TalkBack/VoiceOver) real.

## Archivos modificados

65 archivos, ~4200 líneas insertadas. Por área:
- `docs/agendnote/*.md`, `docs/AI_*.md`: documentación de descubrimiento, auditorías, plan,
  propuestas, registro de implementación, decisiones.
- `composeApp/src/commonMain/.../feature/agenda/`: dominio (recurrencia, listas inteligentes,
  exportación, materializador), presentación (ViewModel, controller, `NewTaskSheet`,
  `AgendaScreen`, `AgendaDayComponents`), datos (repositorios).
- `composeApp/src/commonMain/.../core/`: modelo (`TaskItem`/`TaskDraft`/`Subtask`/
  `TaskTemplate`), notificaciones (`ReminderResolution`), NLP (`QuickCaptureParser`), UI
  (`GlassSnackbar`, `GlassEmptyState`, `AccessibilityPreferences`, `ReduceMotion`).
- `composeApp/src/androidMain/`: notificaciones, accesibilidad — compilado y verificado.
- `composeApp/src/iosMain/`: notificaciones, accesibilidad — **no compilado**.
- `composeApp/src/commonTest/`: 12 archivos de test nuevos o extendidos, 51 tests nuevos.
- `supabase/functions/`: las 6 Edge Functions (sanitización de errores) + `api-tasks` +
  `api-task-series` (deadline/recordatorios/subtareas/fin de serie) — **no compilado**.
- `supabase/migrations/`: 2 migraciones SQL nuevas, aditivas — **no aplicado**.
- `composeApp/src/commonMain/.../feature/settings/`: plantillas, exportación.

## Deuda restante

Ninguna de estas se oculta; todas están también documentadas en su fase correspondiente:

1. **QA visual/funcional en dispositivo real**: cero verificación con emulador o dispositivo
   en toda la sesión. Es la deuda más importante — todo lo demás depende de esto para pasar
   de "compila y los tests pasan" a "funciona de verdad".
2. **iOS**: ningún archivo `iosMain` tocado esta sesión se compiló.
3. **SQL y Edge Functions**: escritas y revisadas a mano, nunca ejecutadas.
4. **Multi-notificación real**: los recordatorios múltiples se guardan y se muestran, pero
   solo se programa una alarma/notificación por tarea (la más próxima) — decisión deliberada
   para no arriesgar el sistema de `AlarmManager` ya probado sin poder verificar una
   reescritura.
5. **Edición de tareas existentes**: no existe en ningún lugar de la app (hallazgo
   preexistente). Bloquea "editar esta y las siguientes" en recurrencia y limita
   deadline/recordatorios/subtareas a la creación.
6. **Rate limiting, idempotencia de red, límites de payload**: documentados como pendientes
   de baja prioridad en `SECURITY_AUDIT.md`.
7. **Funcionalidades "después"**: bloqueo biométrico, deep links, calendario de solo lectura,
   time blocking, widgets — evaluadas, no implementadas, razón documentada por cada una.
