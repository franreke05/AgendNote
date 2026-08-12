-- Storage guardrails for the single-tenant app.
-- These limits prevent accidental oversized payloads from turning text/settings fields
-- into an unbounded source of database growth. NOT VALID keeps existing legacy rows
-- deployable while enforcing the limits on every new or updated row.

drop index if exists public.idx_notes_updated_at;
drop index if exists public.idx_tasks_updated_at;
drop index if exists public.idx_task_reminders_remind_at;

do $$
begin
  if not exists (select 1 from pg_constraint where conname = 'tasks_title_max_length') then
    alter table public.tasks
      add constraint tasks_title_max_length
      check (char_length(title) <= 200) not valid;
  end if;

  if not exists (select 1 from pg_constraint where conname = 'tasks_body_max_length') then
    alter table public.tasks
      add constraint tasks_body_max_length
      check (body is null or char_length(body) <= 8000) not valid;
  end if;

  if not exists (select 1 from pg_constraint where conname = 'labels_name_max_length') then
    alter table public.labels
      add constraint labels_name_max_length
      check (char_length(name) <= 64) not valid;
  end if;

  if not exists (select 1 from pg_constraint where conname = 'labels_color_hex_format') then
    alter table public.labels
      add constraint labels_color_hex_format
      check (color_hex ~ '^#[0-9A-Fa-f]{6}$') not valid;
  end if;

  if not exists (select 1 from pg_constraint where conname = 'task_subtasks_title_max_length') then
    alter table public.task_subtasks
      add constraint task_subtasks_title_max_length
      check (char_length(title) <= 200) not valid;
  end if;

  if not exists (select 1 from pg_constraint where conname = 'task_series_title_max_length') then
    alter table public.task_series
      add constraint task_series_title_max_length
      check (char_length(title) <= 200) not valid;
  end if;

  if not exists (select 1 from pg_constraint where conname = 'task_series_body_max_length') then
    alter table public.task_series
      add constraint task_series_body_max_length
      check (body is null or char_length(body) <= 8000) not valid;
  end if;

  if not exists (select 1 from pg_constraint where conname = 'settings_key_max_length') then
    alter table public.settings
      add constraint settings_key_max_length
      check (char_length(key) <= 64) not valid;
  end if;

  if not exists (select 1 from pg_constraint where conname = 'settings_value_max_length') then
    alter table public.settings
      add constraint settings_value_max_length
      check (char_length(value) <= 32768) not valid;
  end if;
end $$;
