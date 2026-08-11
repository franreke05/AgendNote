-- Endurecimiento de superficie RPC publica - NO relacionado con booking/portfolio.
--
-- Hallazgo (2026-08-11, verificacion contra BD real via MCP): ademas de las funciones de
-- booking (ver 20260811_remove_booking_portfolio_system.sql), la BD real tiene dos funciones
-- Postgres mas que no existen en ningun archivo del repo y que Supabase expone automaticamente
-- como endpoints RPC de PostgREST porque tienen GRANT EXECUTE a PUBLIC/anon/authenticated:
--   - `create_task(p_title text, p_body text, p_day timestamptz, p_due_at timestamptz)`:
--     inserta directamente en `tasks`, sin pasar por api-tasks ni por x-app-secret.
--   - `get_tasks_by_time(p_from timestamptz, p_to timestamptz)`: lee tareas por rango de fecha
--     directamente, mismo bypass.
-- Ninguna de las dos es SECURITY DEFINER ni tiene relacion con el sistema de booking (se
-- verifico por busqueda de texto: ningun otro objeto de la BD las llama). AgendNote no usa la
-- API RPC de PostgREST en ningun punto del codigo (Kotlin llama siempre a las Edge Functions
-- via HTTPS con el header x-app-secret) - es decir, esta exposicion no tiene ningun consumidor
-- legitimo conocido, solo superficie de ataque.
--
-- Esta migracion es DELIBERADAMENTE NO DESTRUCTIVA: solo revoca el permiso de ejecucion publico
-- (reversible con un GRANT si algo las necesitara en el futuro). No borra las funciones ni
-- cambia datos. Se separa de la migracion de booking porque es una decision distinta (higiene
-- de seguridad general, no retirada de una feature de producto) y el propietario puede
-- aprobarla independientemente.
--
-- Si en el futuro se confirma que nada las necesita, se pueden borrar con:
--   drop function if exists public.create_task(text, text, timestamptz, timestamptz);
--   drop function if exists public.get_tasks_by_time(timestamptz, timestamptz);
--
-- Ademas, por la misma razon (cerrar la superficie de ataque cuanto antes, sin esperar al DROP
-- definitivo de la migracion de booking), se revoca aqui mismo el acceso publico a las 4 RPC de
-- booking. No rompe la Edge Function real que SI las usa legitimamente
-- (`create-booking`, desplegada, detectada el 2026-08-11, no versionada en este repo - ver
-- supabase/backups/2026-08-11_pre_booking_removal_export.md) porque esa funcion llama a estas
-- RPC con la service_role key via supabase-js, y REVOKE de PUBLIC/anon/authenticated no afecta a
-- service_role.

revoke execute on function public.create_task(text, text, timestamptz, timestamptz) from public, anon, authenticated;
revoke execute on function public.get_tasks_by_time(timestamptz, timestamptz) from public, anon, authenticated;
revoke execute on function public.create_portfolio_appointment(text, text, text, text, date, text, text) from public, anon, authenticated;
revoke execute on function public.build_portfolio_booking_body(text, text, text) from anon, authenticated;
revoke execute on function public.normalize_email(text) from anon, authenticated;
revoke execute on function public.is_email_valid(text) from anon, authenticated;
