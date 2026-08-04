-- Fase 4 (docs/agendnote/FASE4_PROPUESTA.md): deadline explicito, recordatorios multiples y
-- subtareas. Aditivo unicamente - no renombra ni elimina due_at/slot_end_at, que ya
-- representan correctamente "planificada para" (ver la propuesta para el porque).

alter table tasks
  add column if not exists deadline_at timestamptz;

create table if not exists task_reminders (
  id uuid primary key default gen_random_uuid(),
  task_id uuid not null references tasks(id) on delete cascade,
  remind_at timestamptz not null,
  created_at timestamptz not null default now()
);

create index if not exists idx_task_reminders_task_id on task_reminders(task_id);
create index if not exists idx_task_reminders_remind_at on task_reminders(remind_at);

alter table task_reminders enable row level security;
-- Sin policies para anon/auth, igual que el resto de las tablas: todo el acceso pasa por Edge
-- Functions con la service role key (ver supabase/policies.sql).

create table if not exists task_subtasks (
  id uuid primary key default gen_random_uuid(),
  task_id uuid not null references tasks(id) on delete cascade,
  title text not null,
  is_done boolean not null default false,
  order_index integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists idx_task_subtasks_task_id on task_subtasks(task_id);

alter table task_subtasks enable row level security;
-- Sin policies para anon/auth, mismo patron.

do $$
begin
  if not exists (
    select 1 from pg_trigger where tgname = 'trg_task_subtasks_updated_at'
  ) then
    create trigger trg_task_subtasks_updated_at
      before update on task_subtasks
      for each row execute function set_updated_at();
  end if;
end $$;

-- Backfill: una tarea con due_at definido y notified_at nulo hoy dispara exactamente un
-- recordatorio implicito (ver AndroidNotificationService.scheduleTaskNotification). Migrar
-- ese comportamiento a una fila explicita en task_reminders para no perder recordatorios ya
-- pendientes al desplegar esta migracion. Idempotente: no duplica si la tarea ya tiene
-- recordatorios (por si esta migracion se reaplica).
insert into task_reminders (task_id, remind_at)
select t.id, t.due_at
from tasks t
where t.due_at is not null
  and t.notified_at is null
  and not exists (
    select 1 from task_reminders tr where tr.task_id = t.id
  );
