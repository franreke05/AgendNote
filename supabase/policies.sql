-- RLS: block direct client access; use Edge Functions with service role.

alter table notes enable row level security;
alter table tasks enable row level security;
alter table labels enable row level security;
alter table task_labels enable row level security;
alter table devices enable row level security;
alter table settings enable row level security;

-- No policies created for anon/auth. All access goes through Edge Functions.
