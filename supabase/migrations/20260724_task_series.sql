-- Tareas recurrentes: tabla de series + columna de vinculo en tasks.

create table if not exists task_series (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  body text,
  time time,
  recurrence_type text not null check (recurrence_type in ('daily', 'weekly_days', 'monthly')),
  days_of_week smallint[],
  day_of_month smallint check (day_of_month between 1 and 31),
  label_ids uuid[] not null default '{}',
  start_date date not null,
  is_active boolean not null default true,
  materialized_until date not null,
  created_at timestamptz not null default now()
);

alter table tasks
  add column if not exists series_id uuid references task_series(id) on delete set null;

create index if not exists idx_tasks_series_id on tasks(series_id);
create index if not exists idx_task_series_is_active on task_series(is_active);

alter table task_series enable row level security;
-- Sin policies para anon/auth, igual que el resto de las tablas: todo el acceso pasa
-- por Edge Functions con la service role key (ver supabase/policies.sql).
