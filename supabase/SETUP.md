# Supabase Setup (AgendNote)

## 0) Seguridad (importante)
- No compartas `service_role` ni passwords en el chat.
- Si ya los compartiste, regenera las keys en Supabase.

## 1) Crear tablas y RLS
1. Supabase Dashboard -> SQL Editor -> New query.
2. Pega y ejecuta `supabase/schema.sql`.
3. Pega y ejecuta `supabase/policies.sql`.
4. Si ya tenias la tabla `tasks` creada, ejecuta tambien `supabase/sql-editor-booking-mirror.sql`.

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

## 4.1) Contrato de tareas con Portfolio
- `GET /api-tasks?day=YYYY-MM-DD` es la via que usa la agenda para traer tareas ya creadas por dia.
- `POST /api-tasks` sigue aceptando el payload minimo `title`, `body` y `day`.
- Para reservas espejo tambien acepta `source`, `booking_status`, `appointment_id`, `client_name`, `client_email`, `client_phone`, `due_at`, `slot_end_at`, `label_names` y `label_ids`.
- Si llega `appointment_id`, `POST /api-tasks` actualiza la tarea espejo existente en vez de duplicarla.
- Supabase genera `tasks.id` al insertar la fila; `portfolio` no envia ese `id`.
- `POST /api-tasks` y `PATCH /api-tasks` devuelven la tarea completa con metadatos de reserva y etiquetas.
- El portfolio guarda ese `task.id` en `mirrored_task_id` y AgendNote vuelve a leer la tarea con `fetchTasks(day)`.

## 5) App config
En `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/core/network/AppConfig.kt`:
- `API_BASE_URL` = `https://pdcxxhnybykfbbvnnzki.functions.supabase.co`
- `BACKGROUND_URL` = URL publica de la imagen (opcional).

APP_SECRET se lee por plataforma:
- Android: agrega `APP_SECRET=tu_valor` en `local.properties`.
- iOS: agrega `APP_SECRET=tu_valor` en `iosApp/Configuration/Config.xcconfig` (Info.plist lo usa).
- No dejes `APP_SECRET` hardcodeado en archivos versionados antes de desplegar.
