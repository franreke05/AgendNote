# Reconciliación de esquema — 2026-08-11

## Hallazgo crítico

Comparando la base de datos real (fotografía pegada por el propietario, 2026-08-11) contra lo
que el código actual (Edge Functions + Kotlin) ya da por hecho que existe, la BD real **nunca
recibió** las migraciones `20260724_task_series.sql`, `20260804_task_deadline_reminders_subtasks.sql`
ni `20260804_task_series_end_condition.sql`. La BD real, tal como se pegó, tiene exactamente 7
tablas (`notes`, `tasks`, `labels`, `task_labels`, `devices`, `settings`, `appointments`,
`portfolio_labels`) y `tasks` **no tiene** `deadline_at` ni `slot_end_at`.

**Consecuencia práctica**: si la app real (compilada contra el código actual) ha intentado
alguna vez crear una tarea con deadline, recordatorios múltiples, subtareas, o crear/consultar
una serie recurrente, esas peticiones habrían fallado en el servidor (columna o tabla
inexistente → error 500 desde `internalErrorResponse`, mensaje genérico al cliente por el
endurecimiento de seguridad de la Fase 2 - es decir, un fallo silencioso desde el punto de
vista de la usuaria, sin pista de la causa real). Esto no es una suposición: es la comparación
directa columna por columna entre la BD real pegada y las tres migraciones citadas arriba, que
llevan escritas en el repo desde el 24 de julio y el 4 de agosto sin haberse aplicado nunca
(confirmado repetidamente en `docs/agendnote/IMPLEMENTATION_LOG.md`: "SQL... nunca se ha podido
ejecutar en este entorno, sin acceso a Supabase real").

## Comparación completa: BD real → estado que el código actual necesita

| Tabla/columna | BD real (2026-08-11) | Código actual la necesita | Gap |
|---|---|---|---|
| `notes` | Existe, completa | Sí (tabla, sin consumidor Kotlin - ver `docs/agendnote/` auditoría previa, fuera de alcance aquí) | Ninguno |
| `tasks` (columnas base) | `title,body,day,due_at,notified_at,id,is_done,order_index,created_at,updated_at` | Igual | Ninguno |
| `tasks.slot_end_at` | **No existe** | Sí (`api-tasks` `TASK_SELECT`/insert/update; Kotlin `TaskItem.endTime`) | **Falta** |
| `tasks.deadline_at` | **No existe** | Sí (`api-tasks`; Kotlin `TaskItem.deadline`) | **Falta** |
| `tasks.series_id` | **No existe** | Sí (`api-tasks`; recurrencia) | **Falta** |
| `tasks.booking_status/appointment_id/client_name/client_email/client_phone/source` | Existen (booking legado) | **No** (eliminado hoy mismo, commit `bf15e89`) | **Sobran** - los quita la migración `20260811_remove_booking_portfolio_system.sql` ya escrita |
| `labels`, `task_labels`, `devices`, `settings` | Existen, coinciden | Igual | Ninguno |
| `task_series` (tabla completa) | **No existe** | Sí (`api-task-series` completo; recurrencia diaria/semanal/mensual) | **Falta la tabla entera** |
| `task_series.end_type/end_date/end_occurrences` | N/A (tabla no existe) | Sí (`api-task-series` `buildEndFields`; fin de serie por fecha/nº ocurrencias) | **Falta** (depende de que exista `task_series` primero) |
| `task_reminders` (tabla completa) | **No existe** | Sí (`api-tasks` `attachReminders`/`syncTaskReminders`; recordatorios múltiples) | **Falta la tabla entera** |
| `task_subtasks` (tabla completa) | **No existe** | Sí (`api-tasks` `attachSubtasks`/`syncTaskSubtasks`; checklist de subtareas) | **Falta la tabla entera** |
| `appointments`, `portfolio_labels` | Existen (booking legado) | **No** (eliminado hoy) | **Sobran** - las quita la misma migración de arriba |

## Lo bueno: no hace falta escribir SQL nuevo para las tablas que faltan

Las tres migraciones que faltan por aplicar (`20260724_task_series.sql`,
`20260804_task_deadline_reminders_subtasks.sql`, `20260804_task_series_end_condition.sql`) ya
están escritas, y las tres son **idempotentes y aditivas** (`create table if not exists`,
`add column if not exists`, `create index if not exists`) - seguras de ejecutar contra
exactamente esta BD real tal como está hoy, sin necesitar ningún ajuste. Se verificaron leyendo
su contenido completo esta sesión, no se dan por buenas a ciegas.

La única pieza nueva de esta sesión es la migración de eliminación de booking
(`20260811_remove_booking_portfolio_system.sql`, ya escrita en el commit `bf15e89`), que sí es
destructiva.

## Procedimiento exacto de despliegue (en este orden, no en otro)

**No se ha ejecutado nada de esto todavía.** Sin acceso al Supabase real de AgendNote desde
este entorno (el MCP conectado en esta sesión apunta a un proyecto ajeno). El propietario debe
ejecutar esto manualmente (SQL Editor de Supabase o `supabase db push` con la CLI si prefiere).

1. **Backup** de la base de datos real antes de nada (Supabase Dashboard → Database → Backups,
   o `pg_dump` si tienes acceso directo). Es el paso más importante de todos - todo lo de abajo
   modifica el esquema real.
2. `supabase/migrations/20260724_task_series.sql` - crea `task_series`, añade `tasks.series_id`.
3. `supabase/migrations/20260804_task_deadline_reminders_subtasks.sql` - añade
   `tasks.deadline_at`, crea `task_reminders`/`task_subtasks`, hace backfill de recordatorios
   implícitos ya existentes (no destruye nada, solo añade filas nuevas).
4. `supabase/migrations/20260804_task_series_end_condition.sql` - añade
   `task_series.end_type/end_date/end_occurrences` (necesita que el paso 2 ya haya corrido).
5. **Confirma que el sitio "portfolio" externo ya no escribe** contra `appointments`,
   `tasks.appointment_id`, ni las demás columnas de booking - si sigue escribiendo, sus
   peticiones fallarán después del siguiente paso. Esta es una decisión de negocio, no técnica;
   ya la confirmó el propietario de AgendNote como "ya no forma parte del producto", pero
   confirma que el propio sitio portfolio ya no intenta escribir antes de proceder.
6. `supabase/migrations/20260811_remove_booking_portfolio_system.sql` - **destructiva**: borra
   `appointments`, `portfolio_labels`, y las 6 columnas de booking de `tasks`.
7. Despliega las Edge Functions actualizadas **a la vez** que el paso 6, no antes ni mucho
   después (`api-tasks` ya no espera los campos de booking desde el commit `bf15e89` - si se
   despliega antes de que la BD real pierda esas columnas, sigue funcionando igual, porque
   Postgres no exige que el `SELECT`/`INSERT` mencione todas las columnas existentes; el riesgo
   real es al revés: aplicar el paso 6 con la Edge Function VIEJA todavía desplegada, que seguía
   pidiendo esas columnas en `TASK_SELECT` y fallaría en cuanto se borren):
   ```powershell
   .\supabase\deploy.ps1
   ```
   o manualmente `supabase functions deploy api-tasks api-labels api-settings api-task-series --no-verify-jwt`.
8. Verificación post-despliegue (consultas de humo, no destructivas):
   ```sql
   select count(*) from task_series;
   select count(*) from task_reminders;
   select count(*) from task_subtasks;
   select column_name from information_schema.columns
     where table_name = 'tasks' and column_name in ('deadline_at', 'slot_end_at', 'series_id');
   select column_name from information_schema.columns
     where table_name = 'tasks' and column_name in
       ('booking_status','appointment_id','client_name','client_email','client_phone','source');
   -- La última consulta debe devolver 0 filas tras el paso 6.
   ```
9. Prueba funcional real desde la app (no solo SQL): crear una tarea con deadline + 2
   recordatorios + 1 subtarea, y crear una tarea recurrente semanal con fin "después de 3
   repeticiones". Si algo de esto fallaba antes por las tablas faltantes, es la prueba de que
   ahora funciona.

## Qué NO se ha resuelto en esta sesión (fuera de alcance de esta reconciliación)

- No se ha verificado si hay **datos reales** en `appointments`/`portfolio_labels`/columnas de
  booking que el propietario quiera exportar antes de borrarlos - el paso 1 (backup) cubre esto
  como red de seguridad, pero no se ha hecho una exportación selectiva.
- No se ha determinado el destino de la tabla `notes` (sin consumidor Kotlin confirmado en
  ninguna auditoría de esta operación) - se deja tal cual, no se toca.
- `devices`/`register-device`/`push-due-tasks`: infraestructura de push sin conectar desde el
  cliente (hallazgo de la auditoría original) - no forma parte de esta reconciliación porque no
  hay gap de esquema ahí (la tabla `devices` ya existe y coincide).
