-- Check P0 de la reconciliacion 2026-08-11 (seccion 9 de la directiva "Backend Final"): antes de
-- ejecutar los pasos destructivos, se comprobo si el codigo actual sigue usando `slot_end_at`.
-- Resultado: SI - `api-tasks` lo incluye en TASK_SELECT/buildInsertPayload/buildUpdatePayload, y
-- 3 DTOs Kotlin (`composeApp/.../core/network/AgendaDtos.kt`) lo declaran como
-- `val slot_end_at: String? = null`. La BD real (verificada via MCP contra
-- `pdcxxhnybykfbbvnnzki`) no tenia esta columna pese a que `schema.sql` ya la documentaba desde
-- una sesion anterior. Aditiva, tipo exacto ya usado por api-tasks (ISO string -> timestamptz).
-- Aplicada contra produccion el 2026-08-11 - ver supabase/RECONCILIATION_2026-08-11.md.

alter table if exists public.tasks
  add column if not exists slot_end_at timestamptz;
