-- Fase 5 (docs/agendnote/IMPLEMENTATION_PLAN.md): fin de serie por fecha o por numero de
-- apariciones. Aditivo - las series existentes quedan con end_type = 'never' (comportamiento
-- actual: repetir para siempre), sin necesidad de backfill.

alter table task_series
  add column if not exists end_type text not null default 'never'
    check (end_type in ('never', 'on_date', 'after_occurrences')),
  add column if not exists end_date date,
  add column if not exists end_occurrences integer check (end_occurrences is null or end_occurrences > 0);

-- Coherencia minima a nivel de fila: on_date siempre trae end_date y nunca end_occurrences,
-- after_occurrences al reves, never no trae ninguno de los dos. Protege contra un bug futuro
-- que deje datos inconsistentes, no solo contra la Edge Function actual (que ya valida esto).
alter table task_series drop constraint if exists task_series_end_fields_consistent;
alter table task_series add constraint task_series_end_fields_consistent check (
  (end_type = 'never' and end_date is null and end_occurrences is null)
  or (end_type = 'on_date' and end_date is not null and end_occurrences is null)
  or (end_type = 'after_occurrences' and end_date is null and end_occurrences is not null)
);
