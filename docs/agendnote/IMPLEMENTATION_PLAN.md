# Plan de implementación — AgendNote (adaptado a la realidad del repo)

Este plan reconcilia el prompt maestro del 2026-08-04 con el estado real del código
(`SCREEN_INVENTORY.md`, `DESIGN_AUDIT.md`, `ARCHITECTURE_AUDIT.md`, `SECURITY_AUDIT.md`).
No se ejecuta todavía ningún cambio de código — **el prompt maestro exige explícitamente no
tocar pantallas hasta cerrar este plan**, y varias fases dependen de decisiones de producto
que solo puede tomar el usuario.

## Por qué esto no se ejecuta de una sola vez

El prompt maestro describe, en la práctica, entre 5 y 8 semanas de trabajo de ingeniería
(sistema de diseño + accesibilidad, seguridad de backend, motor de recurrencia, roadmap de
funcionalidades nuevas, regresión completa). Ejecutarlo como un único paso sin checkpoints
sería exactamente lo que las reglas de ejecución del propio prompt prohíben ("cambios
pequeños y coherentes", "no combines una gran migración con un rediseño masivo", "no marques
un gate como superado sin evidencia"). Por eso se propone en fases independientes,
priorizadas, con un gate de confirmación entre cada una.

## Fases propuestas

| Fase | Alcance | Depende de | Riesgo |
|---|---|---|---|
| 0. Este documento | Discovery + auditorías (ya hecho) | — | Ninguno (solo documentación) |
| 1. Decisión de modelo de seguridad | Confirmar mono-usuario vs. multi-usuario futuro; decidir si se conecta el MCP de Supabase correcto | Respuesta del usuario | Ninguno |
| 2. Endurecimiento de seguridad acotado | Rate limiting, idempotencia, auditoría de logs/payloads en Edge Functions, revisión de almacenamiento del `APP_SECRET` en el cliente | Fase 1 | Medio (toca backend en producción) |
| 3. Accesibilidad y estados de interfaz | Reduced motion, reduced transparency, confirmar/añadir undo con snackbar, `GlassEmptyState` compartido | Ninguna (independiente) | Bajo |
| 4. Modelo de tarea ampliado | Separar planificada/deadline/recordatorio, subtareas, recordatorios múltiples (schema + Edge Function + dominio Kotlin + UI) | Fase 1 (toca schema) | Alto (cambio de datos + 4 capas) |
| 5. Recurrencia robusta | Fin por fecha/número, excepciones, "editar esta y las siguientes", tests de DST/fin de mes/zona horaria | Fase 4 (comparte modelo) | Alto |
| 6. Funcionalidades nuevas priorizadas "AHORA" | Captura rápida en lenguaje natural es-ES, listas inteligentes | Fase 4 | Medio |
| 7. Funcionalidades "DESPUÉS" | Time blocking, calendario de solo lectura, plantillas, widgets, deep links, exportación, biometría | Fases 4–6 | Variable, evaluar una por una |
| 8. Regresión final | Recorrido completo, tests, build Android + iOS (si hay acceso a macOS) | Todas las anteriores tocadas | — |

## Qué se recomienda hacer primero

**Fase 3 (accesibilidad/estados)** es la única que se puede ejecutar ya mismo sin esperar
ninguna decisión de producto, es de bajo riesgo, y cierra directamente los gaps reales
detectados en `DESIGN_AUDIT.md`. Es el candidato natural para la primera pantalla/slice si el
usuario quiere ver progreso inmediato.

**Fase 1** no es trabajo de ingeniería, es una pregunta — pero bloquea las fases 2, 4, 5 y 6,
que son las que tienen más valor de producto según el propio prompt maestro (captura rápida,
deadline, subtareas, recordatorios, recurrencia fiable). Sin resolverla, cualquier cambio de
schema o de Edge Functions se arriesga a rehacerse si luego se decide soportar multi-usuario.

## Criterios de aceptación por fase (aplican los gates del prompt maestro)

Para cada fase que sí toque código: ningún criterio en 0, al menos 17/20 en el rubro de
revisión, compilación correcta, tests relevantes en verde, sin regresiones conocidas, máximo
3 iteraciones antes de parar a revisar causa raíz en vez de seguir adivinando.

## Siguiente paso concreto

Este documento no elige una fase por el usuario — la Fase 1 requiere su respuesta directa
(ver preguntas en `SECURITY_AUDIT.md` y la pregunta de priorización planteada en el chat).
