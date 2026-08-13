-- The app now schedules standard local notifications on each device.
-- The old remote push pipeline is no longer consumed by Android or iOS.
-- Destructive by design: remove only the obsolete push storage, not task data.

drop table if exists public.devices;

alter table if exists public.tasks
  drop column if exists notified_at;
