-- RLS: block direct client access; use Edge Functions with service role.

alter table notes enable row level security;
alter table tasks enable row level security;
alter table labels enable row level security;
alter table task_labels enable row level security;
alter table devices enable row level security;
alter table settings enable row level security;
-- Added by supabase/migrations/20260724_task_series.sql (was missing here - documentation
-- drift fixed alongside the Fase 4 migration below, no schema change in this line).
alter table task_series enable row level security;
-- Added by supabase/migrations/20260804_task_deadline_reminders_subtasks.sql.
alter table task_reminders enable row level security;
alter table task_subtasks enable row level security;

-- No policies created for anon/auth. All access goes through Edge Functions.
