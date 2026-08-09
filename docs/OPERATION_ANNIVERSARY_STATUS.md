# Operación Aniversario — estado operativo

Documento vivo. Cada agente lo consulta antes de trabajar y lo actualiza al terminar un batch.
Sustituye a una narrativa dispersa en el chat: esta es la fuente de verdad de estado, no una
copia — `docs/AI_CONTEXT_MAP.md`/`AI_TOKEN_INDEX.md` siguen siendo el mapa de arquitectura.

## CURRENT_PHASE
FASE 0 → FASE 1 (baseline confirmado, arrancando lanes de investigación en paralelo).

## BASELINE (2026-08-09, confirmado antes de tocar nada)
- Branch de trabajo: `agent/operacion-aniversario` (creada desde `main`@`dd1c159`).
- `main` estaba limpio salvo `.worktrees/` (untracked, no es código de producto).
- Sin diffs pendientes desde la auditoría forense del 9 de agosto.
- Tests: 90/90 verdes (última ejecución real, cacheada por Gradle como `UP-TO-DATE`, sin cambios de código desde entonces).
- Entorno: Windows, sin Xcode/macOS (`xcodebuild`/`xcrun` no encontrados) → **iOS: BLOQUEADO POR ENTORNO**, nunca compilado en el historial del repo.
- Android SDK platform-tools presente (`adb.exe`), `ANDROID_HOME` sin configurar, `adb devices` sin dispositivos/emuladores activos → **QA en dispositivo Android: sin evidencia disponible hasta que se lance un emulador o se conecte un dispositivo**.
- Supabase: MCP conectado en esta sesión apunta a un proyecto ajeno (`oposibots-ui`), no al backend real de AgendNote.

## ACTIVE_AGENTS
| Agente | Rol | Modo | Estado |
|---|---|---|---|
| Software Architect | Plan de edición completa de tarea (P1) | Investigación, sin escritura | En curso |
| UI Designer | Especificación formal del sistema Glass | Investigación, sin escritura | En curso |
| Mobile App Builder | Revisión estática iOS/iPhone UX | Investigación, sin escritura | En curso |

## ACTIVE_TASKS / COMPLETED / BLOCKED / FAILED
Vacío al iniciar. Se rellena por batch.

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
