# Supabase Setup (AgendNote)

## 0) Seguridad (importante)
- No compartas `service_role` ni passwords en el chat.
- Si ya los compartiste, regenera las keys en Supabase.

## 1) Crear tablas y RLS
1. Supabase Dashboard -> SQL Editor -> New query.
2. Pega y ejecuta `supabase/schema.sql`.
3. Pega y ejecuta `supabase/policies.sql`.
4. Ejecuta las migraciones de `supabase/migrations/` **en este orden exacto** (todas son
   aditivas e idempotentes salvo la ultima):
   1. `20260724_task_series.sql` (tareas recurrentes)
   2. `20260804_task_deadline_reminders_subtasks.sql` (deadline, recordatorios, subtareas)
   3. `20260804_task_series_end_condition.sql` (fin de serie por fecha/numero de ocurrencias)
   4. Si tu base de datos todavia tiene las tablas/columnas del antiguo sistema de reservas
      (`appointments`, `portfolio_labels`, o las columnas `booking_status`/`appointment_id`/
      `client_name`/`client_email`/`client_phone`/`source` en `tasks`):
      `20260811_remove_booking_portfolio_system.sql` - **destructiva**, lee sus notas antes de
      correrla, y haz backup primero.
5. Si no sabes con certeza que migraciones ya corrieron contra tu base de datos, consulta
   `supabase/RECONCILIATION_2026-08-11.md` primero - documenta un caso real encontrado el
   2026-08-11 donde 3 migraciones llevaban semanas escritas sin haberse aplicado nunca, dejando
   funcionalidad ya implementada en el cliente (deadline, recordatorios, subtareas, series
   recurrentes) fallando en silencio contra la base de datos real.

## 2) Storage para fondos
1. Dashboard -> Storage -> Create bucket -> `backgrounds`.
2. Marca el bucket como **public** (para usar URL directa).
3. Sube una imagen y copia su URL publica.

## 3) Secrets para Edge Functions
Dashboard -> Edge Functions -> Secrets -> Add:
- `SB_URL` = `https://pdcxxhnybykfbbvnnzki.supabase.co` (si no tienes `SUPABASE_URL` disponible)
- `SB_SERVICE_ROLE_KEY` = (tu service_role)
- `APP_SECRET` = (cadena que elijas)

## 4) Desplegar Functions (CLI)
1. Instala CLI si no lo tienes: https://supabase.com/docs/guides/cli
2. Login: `supabase login`
3. Link: `supabase link --project-ref pdcxxhnybykfbbvnnzki`
4. Deploy:
   - `supabase functions deploy api-labels --no-verify-jwt`
   - `supabase functions deploy api-tasks --no-verify-jwt`
   - `supabase functions deploy api-settings --no-verify-jwt`
   - Alternativa rapida macOS/Linux: `./supabase/deploy.sh`
   - Alternativa rapida Windows PowerShell: `.\supabase\deploy.ps1`

## 4.1) Contrato de tareas
- `GET /api-tasks?day=YYYY-MM-DD` es la via que usa la agenda para traer tareas ya creadas por dia.
- `POST /api-tasks` acepta `title`, `body`, `day`, `due_at`, `slot_end_at`, `deadline_at`, `is_done`, `order_index`, `series_id`, `label_ids`/`label_names`, `reminders` y `subtasks`.
- Supabase genera `tasks.id` al insertar la fila.
- `POST /api-tasks` y `PATCH /api-tasks` devuelven la tarea completa con etiquetas, recordatorios y subtareas.
- No hay integracion con sistemas externos de reservas/citas - AgendNote es una agenda personal de un solo inquilino (ver `docs/agendnote/SECURITY_AUDIT.md` y `supabase/migrations/20260811_remove_booking_portfolio_system.sql`).

## 5) App config
En `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/core/network/AppConfig.kt`:
- `API_BASE_URL` = `https://pdcxxhnybykfbbvnnzki.functions.supabase.co`
- `BACKGROUND_URL` = URL publica de la imagen (opcional).

APP_SECRET se lee por plataforma:
- Android: agrega `APP_SECRET=tu_valor` en `local.properties`.
- iOS: copia `iosApp/Configuration/Config.local.xcconfig.example` a `iosApp/Configuration/Config.local.xcconfig` y agrega ahi `APP_SECRET=tu_valor` (Info.plist lo usa via `Config.xcconfig`).
- No dejes `APP_SECRET` hardcodeado en archivos versionados antes de desplegar.
