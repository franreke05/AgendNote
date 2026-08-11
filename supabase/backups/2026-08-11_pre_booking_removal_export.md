# Backup pre-eliminación de booking/portfolio — 2026-08-11

Export completo (no solo schema) de las tablas/columnas que la migración
`20260811_remove_booking_portfolio_system.sql` borra, tomado del proyecto real
`pdcxxhnybykfbbvnnzki` inmediatamente antes de ejecutarla, vía `execute_sql` de solo lectura.

**Corrección importante sobre un dato reportado antes en esta operación**: en una verificación
anterior de esta misma sesión se afirmó "las 8 tablas tienen 0 filas". Esa cifra venía de metadata
de catálogo (estimación de `list_tables`, que usa `reltuples`/estadísticas cacheadas de Postgres,
no un `COUNT(*)` real) y **era incorrecta**. Un `SELECT COUNT(*)` real, hecho justo antes de este
backup, da: `appointments` = 12 filas, `portfolio_labels` = 6 filas, `tasks` = 2 filas (de las
cuales 2 tienen columnas de booking rellenas). Se corrige aquí explícitamente en vez de dejarlo
pasar.

**Evaluación de riesgo real de esos datos** (por qué se procedió tras verlos, no se abortó):
todas las filas de `appointments` son evidentemente pruebas de desarrollo (nombres como
"Test Codex", "PACO", "Paco Putero", "vvv", "que funcione", "44444444444"; mensajes como "Prueba
controlada", "Porfa funciona") hechas entre el 13 de marzo y el 8 de abril de 2026, **cero
actividad en los últimos 4 meses** — consistente con que el propietario confirmó que el sitio
portfolio externo ya no escribe. `portfolio_labels` son 6 filas creadas en el mismo segundo
(`2026-03-16 13:46:39`), es decir, un seed inicial, no datos orgánicos. Las 2 filas de `tasks`
con columnas de booking son la tarea espejo de una de esas pruebas ("Cita con Mario") y una tarea
manual de prueba ("prueba para Arturo"). No hay indicios de un cliente real afectado por este
borrado. Aun así, se exporta todo abajo para poder recuperarlo si hiciera falta.

## `public.appointments` (12 filas)

```json
[
  {"id":"e65d3028-2d0f-422c-b3b1-0d83fb92ecdc","client_name":"Test Codex","client_email":"test-codex-booking@example.com","client_phone":"+34 600 000 000","message":"Prueba controlada","day":"2026-03-20","starts_at":"2026-03-20 14:00:00+00","status":"pending","mirrored_task_id":"b68fc5b6-66c1-4ec4-a542-1d24237a141d","created_at":"2026-03-13 15:57:23.849009+00"},
  {"id":"3f040834-a006-4310-a8e9-65921a5cf7ed","client_name":"PACO","client_email":"pac@gmail.com","client_phone":null,"message":"Contexto portfolio:\nOrigen: portfolio","day":"2026-03-13","starts_at":"2026-03-13 16:00:00+00","status":"pending","mirrored_task_id":"3e30ace9-fe2a-47ab-b039-c4a83fbf76f9","created_at":"2026-03-13 15:58:34.363223+00"},
  {"id":"1f76c249-cdc7-4358-9314-95a3b889b962","client_name":"Francisco Requena","client_email":"franreke506@gmail.com","client_phone":"+34 642 95 75 72","message":"Es una prueba si funciona coronamos.\n\nContexto portfolio:\nOrigen: portfolio","day":"2026-03-14","starts_at":"2026-03-14 06:00:00+00","status":"pending","mirrored_task_id":"1bc35baa-cbb7-459c-9819-f63c1f63e45a","created_at":"2026-03-13 16:08:40.435241+00"},
  {"id":"08b57fb3-0875-49ec-9d74-523f3d5ec536","client_name":"Maider","client_email":"maiderz2003@gmail.com","client_phone":"644347480","message":"Hola guapo lapapaa\n\nEtiquetas de la llamada: llamada\n\nContexto portfolio:\nOrigen: portfolio","day":"2026-03-14","starts_at":"2026-03-14 08:00:00+00","status":"pending","mirrored_task_id":"d7576ff9-5e21-4732-b83d-9ba2dfd40e1e","created_at":"2026-03-13 17:31:02.079912+00"},
  {"id":"23adea51-f0e3-4043-a45d-14577c6f5e66","client_name":"Paco Putero","client_email":"putero69@gmail.com","client_phone":"111111111","message":"Putero\n\nContexto portfolio:\nOrigen: portfolio","day":"2026-03-14","starts_at":"2026-03-14 10:00:00+00","status":"pending","mirrored_task_id":"8d92d7a5-a39c-4f98-8da0-4010c94b5772","created_at":"2026-03-14 00:04:21.363689+00"},
  {"id":"2d6e8812-3987-45e5-87be-106acec2fd4a","client_name":"Paco Requena","client_email":"franreke606@gmail.com","client_phone":"+34642957572","message":"Ejemplo\n\nContexto portfolio:\nOrigen: portfolio","day":"2026-03-14","starts_at":"2026-03-14 14:00:00+00","status":"pending","mirrored_task_id":"e6a7ac53-ae7e-4e81-8183-440cd326fda0","created_at":"2026-03-14 10:38:23.757634+00"},
  {"id":"bd1087cb-7e75-4949-8d6e-5ece75e144da","client_name":"vvv","client_email":"vvv@gmail.com","client_phone":"+34 642957572","message":"Pruebaaaa\n\nContexto portfolio:\nOrigen: portfolio","day":"2026-03-16","starts_at":"2026-03-16 14:00:00+00","status":"pending","mirrored_task_id":"7a17c802-f644-4729-9e90-ba4e7f7534e6","created_at":"2026-03-16 12:22:51.539958+00"},
  {"id":"d1ecada0-04cc-4c25-b7f1-54f48e930adf","client_name":"emilioi","client_email":"emio@gmail.com","client_phone":null,"message":"Necesito una ayudita para conseguir una MVP\n\nContexto portfolio:\nOrigen: portfolio","day":"2026-03-17","starts_at":"2026-03-17 06:00:00+00","status":"pending","mirrored_task_id":"48e96aa2-f999-4e41-aca6-73245fc63685","created_at":"2026-03-16 13:15:11.132876+00"},
  {"id":"1c84f116-4779-47eb-9ad8-a121821585ce","client_name":"que funcione","client_email":"micorreo@gmail.com","client_phone":null,"message":"Porfa funciona\n\nContexto portfolio:\nOrigen: portfolio","day":"2026-03-16","starts_at":"2026-03-16 16:00:00+00","status":"pending","mirrored_task_id":"b9f2565e-53ca-4213-bda4-42e525ce9240","created_at":"2026-03-16 13:27:37.650428+00"},
  {"id":"b85adfaa-a3f1-48d3-9cac-fdbe98ba5154","client_name":"grgre","client_email":"ffffff@gmail.com","client_phone":"22222","message":"231fewfewfew\n\nContexto portfolio:\nOrigen: portfolio","day":"2026-03-17","starts_at":"2026-03-17 08:00:00+00","status":"pending","mirrored_task_id":"6c7748a5-b86e-4818-b0b1-49fdb99dd52c","created_at":"2026-03-16 13:29:25.43675+00"},
  {"id":"710aa9e2-b980-422c-86c9-7380d77a5782","client_name":"Mario","client_email":"mmaestro@madesoft.es","client_phone":"+34616723001","message":"Es para el proyecto con Screen Time API\n\nContexto portfolio:\nOrigen: portfolio","day":"2026-03-19","starts_at":"2026-03-19 16:00:00+00","status":"pending","mirrored_task_id":"ee2f6fc3-b943-4597-b503-69e3bb68ce58","created_at":"2026-03-17 17:28:56.334024+00"},
  {"id":"1bf7842d-5c42-4efb-8e98-8e3a98523193","client_name":"44444444444","client_email":"oposibots@gmail.com","client_phone":"44444444","message":"444444444444444444\n\nContexto portfolio:\nOrigen: portfolio","day":"2026-04-08","starts_at":"2026-04-08 15:00:00+00","status":"pending","mirrored_task_id":"53e0647b-b515-425d-98a4-c4033e7d3f53","created_at":"2026-04-08 14:07:12.856444+00"}
]
```

## `public.portfolio_labels` (6 filas)

```json
[
  {"id":"78e85565-bd7f-4c7f-9ef1-bf5e2644583b","name":"App","color_hex":"#6C5CE7"},
  {"id":"efad2ec6-2ea4-4d7b-b406-8106b8cd331c","name":"Mvp","color_hex":"#3DA9FC"},
  {"id":"e47071af-ca72-499c-b197-8893556754c3","name":"Kotlin","color_hex":"#FF7A59"},
  {"id":"91aaff3c-6951-4d78-a571-3b4e362b5ffb","name":"Flutter","color_hex":"#39D98A"},
  {"id":"d07a1e8e-0d0a-4561-bd72-09fb4e114aec","name":"Swift","color_hex":"#A17CFF"},
  {"id":"82b82c20-fcc6-4fdc-88cc-8d4f7856bd57","name":"Revisión","color_hex":"#FF6B6B"}
]
```
(las 6 con `created_at = updated_at = 2026-03-16 13:46:39.908956+00` — seed inicial, no orgánico)

## `public.tasks` — filas con columnas de booking rellenas (2 de 2 filas totales de `tasks`)

```json
[
  {"id":"ee2f6fc3-b943-4597-b503-69e3bb68ce58","title":"Cita con Mario","day":"2026-03-19","due_at":"2026-03-19 16:00:00+00","is_done":false,"source":"portfolio_booking","booking_status":"pending","appointment_id":"710aa9e2-b980-422c-86c9-7380d77a5782","client_name":"Mario","client_email":"mmaestro@madesoft.es","client_phone":"+34616723001","created_at":"2026-03-17 17:28:56.334024+00","updated_at":"2026-04-01 15:06:27.838843+00"},
  {"id":"4f5f2190-2a49-4b65-99ac-af19d5597bb2","title":"prueba para Arturo","body":"holaaa","day":"2026-03-21","due_at":"2026-03-21 15:59:00+00","is_done":true,"source":"manual","booking_status":null,"appointment_id":null,"client_name":null,"client_email":null,"client_phone":null,"created_at":"2026-03-21 13:00:02.679761+00","updated_at":"2026-03-21 13:03:39.99624+00"}
]
```

## Hallazgo adicional durante este backup: infraestructura no versionada dependiente de booking

Al listar las Edge Functions reales del proyecto (`list_edge_functions`), aparecieron 3 funciones
desplegadas que **no existen en ningún archivo del repo**:

- **`create-booking`** (slug, ACTIVE, version 10, `verify_jwt=false`) — Edge Function completa
  (Luxon, validación de slots fijos, CORS con whitelist de origen) que llama internamente a
  `POST /rest/v1/rpc/create_portfolio_appointment` usando la `service_role` key. Es, con toda
  probabilidad, el endpoint real que el sitio portfolio externo llamaba. Al no invocarse desde
  ningún origen en los logs recientes y no tener `appointments` filas después del 8 de abril,
  coincide con "el sitio ya no escribe" - pero sigue desplegada y sigue funcionando si algo la
  llama, hasta que se borre desde el Dashboard (no hay tool de borrado de Edge Functions en el
  MCP usado esta sesión).
- **`agendnote-create-task`** y **`agendnote-get-tasks`** (`verify_jwt=true`, version 1) — wrappers
  finos sobre las RPC `create_task`/`get_tasks_by_time`. Requieren un JWT de Supabase Auth válido
  (AgendNote no usa Supabase Auth), así que no los puede llamar la app actual; origen/propósito
  desconocido, no relacionados con booking. Se revoca el acceso público a las RPC subyacentes
  (ver `20260811_harden_public_rpc_exposure.sql`) sin tocar estas Edge Functions, que siguen
  funcionando porque usan la `service_role` key internamente (no afectada por REVOKE a
  anon/authenticated/PUBLIC).

Estas 3 funciones **no se han borrado ni modificado** en esta sesión - documentado para que el
propietario decida si también las retira desde el Dashboard de Supabase (Edge Functions → Delete).
