# Decisiones — auditoría 2026-08-04 (prompt maestro de profesionalización)

## 2026-08-04 — Reutilizar la auditoría de julio en vez de repetirla
El branch `agent/finish-audit-notifications` ya contiene una auditoría UI/UX/QA completa
(2026-07-27, PR #1) que cubre gran parte del "Bucle de sistema de diseño" y "Bucle por
pantalla" del prompt maestro. Esta pasada no repite ese trabajo; lo referencia y se centra en
los gaps reales que quedan abiertos.

## 2026-08-04 — Corregir el modelo de seguridad asumido por el prompt maestro
El prompt maestro asume Supabase Auth + RLS multi-usuario (`auth.uid()`). El código real no
tiene usuarios, ni `user_id`, ni Supabase Auth: es una app de un solo inquilino protegida por
un secreto compartido estático delante de Edge Functions con `service_role`. Se documenta
esta diferencia en `SECURITY_AUDIT.md` en vez de aplicar el patrón del prompt maestro tal
cual, porque haría falta un rediseño de datos, no un ajuste de policies.

## 2026-08-04 — No usar el conector Supabase MCP de esta sesión para auditar AgendNote
El proyecto Supabase conectado (`ndiooyyqtaeysnedywer`, "oposibots-ui's Project") pertenece a
otro producto no relacionado. Se detuvo la exploración de ese proyecto tras confirmar que no
contiene ninguna tabla de AgendNote, y la auditoría de seguridad se hizo solo con los
archivos versionados en `supabase/`.

## 2026-08-04 — No implementar código todavía
El prompt maestro exige explícitamente completar descubrimiento + auditorías + plan antes de
tocar pantallas. Esta sesión se detiene ahí y espera dirección del usuario sobre qué fase de
`IMPLEMENTATION_PLAN.md` ejecutar primero, en particular la decisión mono-usuario vs.
multi-usuario que bloquea el trabajo de mayor valor (modelo de tarea, seguridad, recurrencia).
