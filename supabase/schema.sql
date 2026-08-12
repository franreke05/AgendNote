-- AgendNote schema
-- Notes, tasks, labels, and settings.

create extension if not exists "pgcrypto";

create table if not exists notes (
  id uuid primary key default gen_random_uuid(),
  title text not null check (char_length(title) <= 200),
  body text check (body is null or char_length(body) <= 8000),
  pinned boolean not null default false,
  order_index integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists tasks (
  id uuid primary key default gen_random_uuid(),
  title text not null check (char_length(title) <= 200),
  body text check (body is null or char_length(body) <= 8000),
  day date not null,
  due_at timestamptz,
  slot_end_at timestamptz,
  notified_at timestamptz,
  is_done boolean not null default false,
  order_index integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists labels (
  id uuid primary key default gen_random_uuid(),
  name text not null check (char_length(name) <= 64),
  color_hex text not null check (color_hex ~ '^#[0-9A-Fa-f]{6}$'),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists devices (
  id uuid primary key default gen_random_uuid(),
  platform text not null,
  token text not null,
  last_seen_at timestamptz not null default now(),
  unique (token)
);

create table if not exists task_labels (
  id uuid primary key default gen_random_uuid(),
  task_id uuid not null references tasks(id) on delete cascade,
  label_id uuid not null references labels(id) on delete cascade,
  unique (task_id, label_id)
);

create table if not exists settings (
  id uuid primary key default gen_random_uuid(),
  key text not null unique,
  value text not null check (char_length(key) <= 64 and char_length(value) <= 32768)
);

create index if not exists idx_tasks_day on tasks(day);
create index if not exists idx_tasks_due_at on tasks(due_at);

create or replace function set_updated_at()
returns trigger as $$
begin
  new.updated_at = now();
  return new;
end;
$$ language plpgsql;

do $$
begin
  if not exists (
    select 1 from pg_trigger where tgname = 'trg_notes_updated_at'
  ) then
    create trigger trg_notes_updated_at
      before update on notes
      for each row execute function set_updated_at();
  end if;

  if not exists (
    select 1 from pg_trigger where tgname = 'trg_tasks_updated_at'
  ) then
    create trigger trg_tasks_updated_at
      before update on tasks
      for each row execute function set_updated_at();
  end if;

  if not exists (
    select 1 from pg_trigger where tgname = 'trg_labels_updated_at'
  ) then
    create trigger trg_labels_updated_at
      before update on labels
      for each row execute function set_updated_at();
  end if;
end $$;

-- Kept as an additive, idempotent statement (not folded into the CREATE TABLE above) for
-- deployments that already had `tasks` before `slot_end_at` existed - same pattern the booking
-- columns used to follow here before they were removed (2026-08-11, see
-- supabase/migrations/20260811_remove_booking_portfolio_system.sql for the cleanup migration
-- that drops them from an already-deployed database; this file only describes a fresh setup).
alter table if exists tasks
  add column if not exists slot_end_at timestamptz;
