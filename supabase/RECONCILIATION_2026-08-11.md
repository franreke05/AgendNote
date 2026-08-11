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

## Actualización 2026-08-11 (tarde): acceso real confirmado al proyecto `pdcxxhnybykfbbvnnzki`

Todo lo anterior de este documento se escribió comparando una fotografía pegada por el
propietario contra el código. Esta sección documenta la **verificación directa** contra la base
de datos real vía Supabase MCP (proyecto `pdcxxhnybykfbbvnnzki`, "AgendNotes", ACTIVE_HEALTHY),
hecha de forma exclusivamente de lectura (`list_tables`, `list_migrations`, `get_advisors`,
`execute_sql` de solo SELECT contra catálogos del sistema - ningún `apply_migration` ejecutado).

**Confirmado, sin sorpresas:**
- Las 8 tablas (`notes`, `tasks`, `labels`, `task_labels`, `devices`, `settings`,
  `appointments`, `portfolio_labels`) coinciden exactamente con la fotografía pegada.
- Las 8 tablas tienen **0 filas** - no hay datos reales en riesgo en ninguna de ellas, incluidas
  `appointments`/`portfolio_labels`. El paso de backup del procedimiento sigue siendo buena
  práctica, pero no hay pérdida de datos real posible en este momento.
- `list_migrations` devuelve **vacío** - cero migraciones registradas en el historial de
  Supabase, consistente con "nunca se aplicó nada de `supabase/migrations/`".

**Hallazgo nuevo (no estaba en la comparación original porque no está en ningún archivo del
repo): 6 funciones Postgres viven solo en la base de datos real.**

`get_advisors(type="security")` señaló, además de los INFO esperados de "RLS habilitado sin
políticas" (diseño intencional, deny-all vía Edge Functions), varios WARN sobre funciones con
`search_path` mutable y exposición pública. Se investigó cada una leyendo su definición completa
(`pg_get_functiondef`) y sus grants (`information_schema.routine_privileges`):

| Función | `SECURITY DEFINER` | Grant a `anon`/`authenticated`/`PUBLIC` | Relación con booking | Otro objeto la usa |
|---|---|---|---|---|
| `create_portfolio_appointment(...)` | **Sí** | **Sí, incluida `PUBLIC`** | Es el propio flujo de reserva: valida slot, usa advisory locks, inserta en `appointments` y en `tasks` con los campos de booking | No |
| `build_portfolio_booking_body(...)` | No | Sí (anon/authenticated) | Helper interno, solo lo llama la anterior | No |
| `normalize_email(...)` | No | Sí (anon/authenticated) | Helper de validación de email para el formulario de reserva | No (verificado por búsqueda de texto contra el código fuente de todas las funciones de `public` y contra `column_default`/`generation_expression` de `tasks`/`appointments`) |
| `is_email_valid(...)` | No | Sí (anon/authenticated) | Igual que la anterior | No (igual verificación) |
| `create_task(title, body, day, due_at)` | No | **Sí, incluida `PUBLIC`** | Ninguna - inserción genérica de tareas, sin campos de booking | No |
| `get_tasks_by_time(from, to)` | No | **Sí, incluida `PUBLIC`** | Ninguna - lectura genérica por rango de fechas | No |

**Por qué importa de verdad:** las 6 funciones tienen `GRANT EXECUTE` a `anon`/`authenticated`
(y las 3 más sensibles también a `PUBLIC`), lo que Supabase expone automáticamente como
endpoints REST vía PostgREST (`POST /rest/v1/rpc/<nombre>`), accesibles con la anon key (pública
por diseño de Supabase, ya sea embebida en un cliente o simplemente conocida). Ninguna pasa por
`api-tasks` ni por el header `x-app-secret` que protege el resto de la API - es un bypass total
del modelo de seguridad Edge-Function-only que ya se documentó y endureció en fases anteriores
de esta operación. AgendNote no usa la API RPC de PostgREST en ningún punto del código Kotlin
(todo pasa por HTTPS a las Edge Functions), así que esta superficie no tiene ningún consumidor
legítimo conocido - solo riesgo.

**Trigger duplicado descubierto de paso:** `tasks` tiene dos triggers `BEFORE UPDATE` que hacen
lo mismo (`set_tasks_updated_at` → función legada `set_current_timestamp_updated_at()`, y
`trg_tasks_updated_at` → `set_updated_at()`, la que sí crea `schema.sql` de este repo).
Inofensivo pero redundante; `appointments` tiene el mismo patrón con su propio trigger, que
desaparece solo al borrar la tabla.

**Cambios ya escritos (no aplicados) como consecuencia:**
1. `supabase/migrations/20260811_remove_booking_portfolio_system.sql` (ya existía) - ampliada
   para borrar también `create_portfolio_appointment`, `build_portfolio_booking_body`,
   `normalize_email`, `is_email_valid` (las 4 son booking-only, confirmado sin otros
   consumidores) y el trigger/función `set_current_timestamp_updated_at` duplicados.
2. `supabase/migrations/20260811_harden_public_rpc_exposure.sql` (nueva, **no destructiva**) -
   `REVOKE EXECUTE` sobre `create_task`/`get_tasks_by_time` para `public`/`anon`/`authenticated`.
   Deliberadamente separada de la migración de booking porque es una decisión distinta (higiene
   de seguridad general de superficie RPC, no retirada de una feature de producto ya decidida) -
   pendiente de aprobación explícita del propietario igual que el resto, pero revocar un GRANT
   es trivialmente reversible (no borra nada), a diferencia de las migraciones DROP.

**Procedimiento actualizado:** el paso 6 del procedimiento de despliegue de más arriba pasa a
incluir también las 4 funciones y el trigger/función duplicados (ya están en el mismo archivo
`.sql`, no hace falta un paso nuevo). El `REVOKE` de `create_task`/`get_tasks_by_time` es
opcional/independiente - puede aplicarse en cualquier momento, antes o después del resto, sin
afectar a ninguna funcionalidad de la app.

**Todavía no aplicado nada de esto contra la base de datos real** - sigue pendiente de
confirmación explícita del propietario antes de ejecutar cualquier `apply_migration`.

## EJECUTADO contra producción — 2026-08-11, autorización explícita del propietario

Todo lo de arriba se aplicó, en este orden, contra `pdcxxhnybykfbbvnnzki` real, con autorización
explícita y confirmación de gates repetida por el propietario. Migraciones registradas en el
historial de Supabase (`list_migrations`, las 6 aparecen con nombre y versión):

1. `20260811_harden_public_rpc_exposure` — REVOKE de `create_task`, `get_tasks_by_time`,
   `create_portfolio_appointment`, `build_portfolio_booking_body`, `normalize_email`,
   `is_email_valid` para `PUBLIC`/`anon`/`authenticated`.
2. `20260724_task_series`, `20260804_task_deadline_reminders_subtasks`,
   `20260804_task_series_end_condition` — las 3 aditivas, aplicadas sin incidentes.
3. `20260811_add_missing_slot_end_at` — gap nuevo encontrado en el Check P0 (`slot_end_at` sí lo
   usa el código, no existía en producción): columna añadida.
4. `20260811_remove_booking_portfolio_system` — DROP final, dentro de transacción, con
   `RESTRICT` explícito (sin `CASCADE`, ninguna dependencia desconocida bloqueó el DROP).

**Edge Functions redesplegadas** con el código actual del repo: `api-tasks` (v8), `api-labels`
(v7), `api-settings` (v7), y `api-task-series` desplegada por primera vez (v1 - nunca había
existido en producción, coincide con los `404` reales vistos en logs). De paso se corrigió que
la versión previamente desplegada de `api-tasks` era mucho más antigua que el repo: sin
`internalErrorResponse` (filtraba errores de Postgres al cliente), CORS abierto a `*`, y
comparación de `x-app-secret` no segura contra timing attacks - las 4 funciones quedan con el
`_shared/{cors,response,auth}.ts` actual del repo.

**Incidente propio durante el despliegue**: el primer intento de desplegar `api-tasks` quedó con
`verify_jwt: true` (default de la tool de deploy), lo que habría roto la app (AgendNote no usa
Supabase Auth). Detectado y corregido antes de que importara, redesplegando con
`verify_jwt: false` explícito - aplicado también a los otros 3 despliegues desde el principio.

**Verificación post-DROP, todo verde**: `appointments`/`portfolio_labels` ya no existen (`to_regclass`
= null); 0 columnas de booking en `tasks`; 0 funciones de booking; trigger legado
`set_tasks_updated_at` eliminado, trigger moderno `trg_tasks_updated_at` presente; `task_series`/
`task_reminders`/`task_subtasks`/`deadline_at`/`slot_end_at`/`series_id` todos presentes;
`api-tasks` y `api-task-series` con `verify_jwt=false`. Smoke test real contra la BD (create →
read → update-solo-título → delete) con una tarea que tiene deadline + reminder + subtask:
preservación correcta tras el update, 0 huérfanos tras el delete. Tests de Kotlin: `BUILD
SUCCESSFUL`, sin regresiones (sin cambios de código Kotlin esta sesión).

**Corrección sobre un dato reportado antes en esta misma operación**: se había afirmado "las 8
tablas tienen 0 filas" - era una estimación de catálogo, no un `COUNT(*)` real. El recuento real
antes del DROP era `appointments`=12, `portfolio_labels`=6, `tasks`=2 (con campos de booking) -
todo pruebas de desarrollo de marzo-abril 2026, sin actividad real de cliente y sin actividad
alguna desde el 8 de abril. Exportado completo a
`supabase/backups/2026-08-11_pre_booking_removal_export.md` antes del DROP.

**Pendiente, fuera del alcance de este MCP - acción manual del propietario**:

```
MANUAL_DELETE_REQUIRED:
- create-booking       (Edge Function activa, v10, no versionada en el repo - llama a
                         create_portfolio_appointment con service_role; ya no puede funcionar
                         tras el DROP, pero sigue desplegada y aceptando requests)
- agendnote-create-task (Edge Function activa, v1, no versionada en el repo, requiere JWT de
                         Supabase Auth que la app no usa - origen desconocido)
- agendnote-get-tasks   (idem)
```
Bórralas desde el Dashboard de Supabase → Edge Functions → (cada una) → Delete. El MCP usado
esta sesión no expone una tool de borrado de Edge Functions.

## Qué NO se ha resuelto en esta sesión (fuera de alcance de esta reconciliación)

- No se ha verificado si hay **datos reales** en `appointments`/`portfolio_labels`/columnas de
  booking que el propietario quiera exportar antes de borrarlos - el paso 1 (backup) cubre esto
  como red de seguridad, pero no se ha hecho una exportación selectiva.
- No se ha determinado el destino de la tabla `notes` (sin consumidor Kotlin confirmado en
  ninguna auditoría de esta operación) - se deja tal cual, no se toca.
- `devices`/`register-device`/`push-due-tasks`: infraestructura de push sin conectar desde el
  cliente (hallazgo de la auditoría original) - no forma parte de esta reconciliación porque no
  hay gap de esquema ahí (la tabla `devices` ya existe y coincide).
