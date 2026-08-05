# Auditoría de sistema de diseño — AgendNote

## Qué ya está resuelto (no repetir)

La auditoría del 2026-07-27 (`docs/FINAL_UI_QA_REPORT.md`) y el plan previo
(`plan_mejora_codex.md`, ya ejecutado en los commits `7c8e7db`…`edcab01`) ya cubrieron:

- Identidad Glass conservada (`GlassSurface`, `GlassBackground`, gradiente, manchas de luz,
  grano) — **no tocar**, es la decisión de producto vigente.
- Navegación inferior con espacio reservado en el shell (no compensación por pantalla).
- Objetivos táctiles ≥48dp en botones de icono, chevrons, paleta de etiquetas (4×4) y
  recurrencia (2×2).
- Diálogos con relleno modal opaco y `Dialog` de plataforma (crear, detalle, confirmación).
- Tokens `success`/`onSuccess` añadidos a `GlassTokens`; colores hardcodeados de éxito/error
  reemplazados por tokens.
- Ortografía y tildes corregidas en el copy es-ES existente.
- `SectionHeader` añadido a Calendario/Etiquetas/Ajustes.
- Iconos de navegación diferenciados (Agenda vs. Calendario ya no comparten icono).

## Gaps reales frente al prompt maestro (2026-08-04)

### 1. No hay roles tipográficos semánticos nombrados

`AgendNoteTheme.kt` usa los nombres estándar de Material (`displayLarge`, `titleMedium`,
`bodyLarge`…) con overrides puntuales vía `layout.text(base, min)`. Esto funciona pero no es
lo mismo que la escala `largeTitle/title/headline/body/subheadline/footnote/caption` que
describe el prompt maestro. **No se recomienda renombrar los estilos de Material** (sería un
cambio grande y de bajo valor); en su lugar, documentar el mapeo conceptual
(`displayLarge` ≈ `largeTitle`, `titleMedium` ≈ `headline`, etc.) para que el criterio de uso
sea consistente, y auditar que ninguna pantalla use un `.sp` suelto fuera de
`AppLayoutMetrics` (regla ya vigente y, según `plan_mejora_codex.md`, ya verificada en la
pasada anterior).

### 2. Accesibilidad de plataforma no verificada end-to-end

- Reduced motion: no se ha confirmado si las transiciones actuales respetan una preferencia
  de "reducir movimiento" del sistema (Android `Settings.Global.ANIMATOR_DURATION_SCALE` /
  iOS `UIAccessibility.isReduceMotionEnabled`). No hay código que lo consulte hoy.
  **Pendiente de verificar y, si falta, implementar.**
- Reduced transparency: `GlassSurface`/`GlassBackground` no tienen una variante de
  fallback para "reducir transparencia". Pendiente.
- Dynamic Type / fontScale grande: `AppLayoutMetrics` escala proporcionalmente por tamaño
  de pantalla, pero no hay evidencia de que se haya probado con `fontScale` del sistema al
  máximo (riesgo de truncamiento en textos largos en español, p. ej. "Miércoles, 30 de
  septiembre" ya fue un caso real corregido — hallazgo 1 de `plan_mejora_codex.md`).
- Modo oscuro sigue siendo 100% manual (no usa `isSystemInDarkTheme()`) — backlog conocido
  y explícitamente pospuesto (hallazgo 8 de `plan_mejora_codex.md`).

### 3. Sin capa de "estados de interfaz" reutilizable

No existe un `GlassEmptyState`/`GlassSnackbar` componentizado y compartido entre pantallas —
cada pantalla resuelve su propio vacío/error con composables locales (correcto en su
resultado visual, pero duplicado). Antes de crear estos componentes hay que confirmar que hay
reutilización real en ≥2 pantallas (regla del prompt maestro y de `compose-state-hoisting`);
Agenda y Etiquetas ya comparten el mismo patrón visual de vacío, así que **sí hay
justificación** para extraer un `GlassEmptyState` común.

### 4. Undo con snackbar

El prompt maestro pide que completar una tarea use snackbar con "Deshacer" en vez de acción
silenciosa. No se ha confirmado si `AgendaScreen` ya tiene esto — **pendiente de verificar en
el código antes de asumir que falta o que existe**.

## Recomendación de alcance para la siguiente pasada visual

No repetir el trabajo de julio. Si se retoma el sistema de diseño, priorizar en este orden:
1. Verificar/implementar reduced motion y reduced transparency (accesibilidad real, no solo
   visual).
2. Confirmar o añadir snackbar+undo al completar tarea.
3. Extraer `GlassEmptyState` compartido (Agenda + Etiquetas) solo si el punto 3 se confirma.
4. Dejar la migración de modo oscuro automático y el renombrado tipográfico como backlog
   explícito, no como trabajo de esta pasada.
