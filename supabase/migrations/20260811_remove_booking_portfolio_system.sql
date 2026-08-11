-- Elimina por completo el sistema legado de citas/reservas/portfolio (decision de producto
-- explicita del dueno de AgendNote, 2026-08-11): AgendNote es y sera siempre una agenda
-- personal de un solo inquilino; la integracion externa de "portfolio" (espejo de reservas de
-- un sitio web externo hacia esta base de datos) queda retirada, sin mantener compatibilidad.
--
-- Historial: docs/superpowers/plans/2026-07-24-remove-booking-mirror.md ya elimino toda la UI/
-- modelo de booking del cliente Kotlin (2026-07-24). Esta migracion completa ese trabajo en el
-- backend (schema + Edge Function api-tasks), que hasta ahora seguia aceptando y devolviendo
-- los campos de booking por compatibilidad hacia atras. Ver tambien
-- docs/agendnote/SECURITY_AUDIT.md, que documentaba este resto como riesgo/deuda conocida.
--
-- DESTRUCTIVA - borra tablas y columnas. No se ha aplicado contra ninguna base de datos real
-- desde este entorno (sin acceso al proyecto Supabase real de AgendNote en esta sesion). Antes
-- de ejecutar contra produccion:
--   1. Confirma que `appointments`/`portfolio_labels` y las columnas de booking de `tasks` no
--      tienen datos que todavia necesites (o haz un backup/export si los tienen).
--   2. Confirma que el sitio "portfolio" externo ya no escribe contra estas tablas/endpoints -
--      si sigue escribiendo, esta migracion rompera esas peticiones (columnas inexistentes).
--   3. Despliega la version actualizada de supabase/functions/api-tasks/index.ts ANTES o A LA
--      VEZ que esta migracion - la version anterior de la Edge Function sigue intentando leer/
--      escribir estas columnas (TASK_SELECT las incluia), y fallaria contra un esquema ya
--      migrado si se despliega despues.

drop table if exists public.appointments;
drop table if exists public.portfolio_labels;

drop index if exists idx_tasks_appointment_id;

alter table if exists public.tasks
  drop column if exists booking_status,
  drop column if exists appointment_id,
  drop column if exists client_name,
  drop column if exists client_email,
  drop column if exists client_phone,
  drop column if exists source;
