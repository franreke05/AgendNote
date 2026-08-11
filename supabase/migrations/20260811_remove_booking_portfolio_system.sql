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
-- DESTRUCTIVA - borra tablas, columnas y funciones. No se ha aplicado contra ninguna base de
-- datos real desde este entorno. Antes de ejecutar contra produccion:
--   1. Confirma que `appointments`/`portfolio_labels` y las columnas de booking de `tasks` no
--      tienen datos que todavia necesites (o haz un backup/export si los tienen).
--   2. Confirma que el sitio "portfolio" externo ya no escribe contra estas tablas/endpoints -
--      si sigue escribiendo, esta migracion rompera esas peticiones (columnas/funciones
--      inexistentes).
--   3. Despliega la version actualizada de supabase/functions/api-tasks/index.ts ANTES o A LA
--      VEZ que esta migracion - la version anterior de la Edge Function sigue intentando leer/
--      escribir estas columnas (TASK_SELECT las incluia), y fallaria contra un esquema ya
--      migrado si se despliega despues.
--
-- 2026-08-11 (verificacion contra BD real via MCP): se detectaron ademas 4 funciones Postgres
-- que existen SOLO en la base de datos real (no estan en ningun archivo del repo) y que forman
-- parte exclusivamente del sistema de booking/portfolio:
--   - `create_portfolio_appointment(...)`: SECURITY DEFINER, y ademas GRANT a PUBLIC + anon +
--     authenticated - es decir, cualquiera con la anon key (publica por diseno de Supabase)
--     puede llamarla directamente via POST /rest/v1/rpc/create_portfolio_appointment, sin pasar
--     por api-tasks ni por el header x-app-secret. Bypass total del modelo de seguridad de
--     Edge Functions. Crea filas en `appointments` y en `tasks` (con los campos de booking).
--   - `build_portfolio_booking_body(...)`: helper interno, solo lo llama la funcion anterior.
--   - `normalize_email(...)` / `is_email_valid(...)`: helpers de validacion de email para el
--     formulario de reserva, solo los llama `create_portfolio_appointment`. Verificado por
--     busqueda de texto contra el codigo fuente de TODAS las funciones de public y contra los
--     column_default/generation_expression de tasks/appointments: ningun otro objeto de la BD
--     las referencia.
-- Las 4 se eliminan aqui junto con las tablas que las originaron.
--
-- Ademas, `tasks` y `appointments` tenian un trigger BEFORE UPDATE duplicado para mantener
-- `updated_at`: uno (`set_tasks_updated_at` / `set_appointments_updated_at`) usa la funcion
-- legada `set_current_timestamp_updated_at()`, y otro (`trg_tasks_updated_at`, la que sí crea
-- `schema.sql` de este repo) usa `set_updated_at()`. Ambos hacen lo mismo (NEW.updated_at :=
-- now()) asi que ejecutar los dos en cada UPDATE es inofensivo pero redundante. Como
-- `appointments` desaparece en esta misma migracion (su trigger se va con la tabla) y el
-- trigger duplicado de `tasks` no aporta nada que `trg_tasks_updated_at` no haga ya, se elimina
-- el trigger duplicado de `tasks` y, con el, la funcion legada `set_current_timestamp_updated_at`
-- (verificado: tras quitar `set_tasks_updated_at`, ningun trigger vivo la sigue usando).

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

drop trigger if exists set_tasks_updated_at on public.tasks;

drop function if exists public.create_portfolio_appointment(text, text, text, text, date, text, text);
drop function if exists public.build_portfolio_booking_body(text, text, text);
drop function if exists public.normalize_email(text);
drop function if exists public.is_email_valid(text);
drop function if exists public.set_current_timestamp_updated_at();
