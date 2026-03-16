-- Upgrade existing AgendNote tasks so booking mirror payloads can store full reservation data.
-- Safe to run multiple times in Supabase SQL Editor.

alter table if exists tasks
  add column if not exists source text not null default 'manual',
  add column if not exists booking_status text,
  add column if not exists client_name text,
  add column if not exists client_email text,
  add column if not exists client_phone text,
  add column if not exists slot_end_at timestamptz;

do $$
declare
  appointment_id_type text;
begin
  if not exists (
    select 1
    from information_schema.tables
    where table_schema = 'public'
      and table_name = 'tasks'
  ) then
    raise exception 'tasks table does not exist. Run supabase/schema.sql first.';
  end if;

  select c.data_type
    into appointment_id_type
  from information_schema.columns c
  where c.table_schema = 'public'
    and c.table_name = 'tasks'
    and c.column_name = 'appointment_id';

  if appointment_id_type is null then
    alter table tasks add column appointment_id text;
  elsif appointment_id_type <> 'text' then
    drop index if exists idx_tasks_appointment_id;
    alter table tasks
      alter column appointment_id type text
      using appointment_id::text;
  end if;
end $$;

create unique index if not exists idx_tasks_appointment_id
  on tasks(appointment_id)
  where appointment_id is not null;
