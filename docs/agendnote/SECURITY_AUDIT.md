# Auditoría de seguridad — AgendNote

## Corrección importante al prompt maestro

El prompt maestro asume un modelo Supabase Auth + RLS multi-usuario (`auth.uid() = user_id`
en cada policy). **Ese modelo no existe en AgendNote.** El código real es:

- Ninguna tabla (`notes`, `tasks`, `labels`, `task_labels`, `devices`, `settings`) tiene
  columna `user_id` (`supabase/schema.sql`).
- No hay Supabase Auth: no hay login, no hay JWT de usuario, no hay `auth.uid()`.
- `supabase/policies.sql` habilita RLS en las 6 tablas **sin crear ninguna policy** — es
  decir, deniega todo acceso directo desde `anon`/`authenticated`. El comentario del propio
  archivo lo deja explícito: *"block direct client access; use Edge Functions with service
  role"*.
- Las Edge Functions (`supabase/functions/api-*`) usan la `service_role` key (bypassa RLS por
  diseño) y autentican al llamante con **un único secreto compartido estático**
  (`requireAppSecret` en `_shared/auth.ts`, header `x-app-secret`), no con identidad de
  usuario.

Esto es un patrón válido para una **app de un solo inquilino** (el propio desarrollador es el
único usuario), pero cambia por completo qué significa "seguro" aquí:
- No hay aislamiento de datos entre usuarios porque no hay usuarios — todo el que tenga el
  `APP_SECRET` tiene acceso total a todos los datos.
- El "modelo de amenazas" real es: (a) filtración del `APP_SECRET` (en el binario de la app,
  en logs, en un repo), (b) compromiso del propio dispositivo, (c) abuso de un endpoint sin
  límite de tasa.
- Si en algún momento el producto pasa a ser multi-usuario, **todo este modelo debe
  rediseñarse** (añadir `user_id`, Supabase Auth, políticas RLS reales) — no es un ajuste
  incremental, es un cambio de arquitectura de datos.

**Esta es la primera decisión de producto que hay que confirmar con el usuario antes de
tocar nada de seguridad** (ver pregunta al final de este documento / en el chat).

## Lo que ya está bien (verificado en código, commit `2345097`)

- Comparación del `APP_SECRET` en tiempo constante (`timingSafeEqual`), evita side-channel
  por timing.
- CORS restringido por defecto (`CORS_ALLOWED_ORIGIN` vacío bloquea a todo origen navegador;
  solo se abre si se define el secret explícitamente). El cliente real es la app móvil vía
  Ktor, que no aplica CORS, así que este es un endurecimiento correcto y no rompe nada.
- `.mcp.json` ignorado en git (evita filtrar configuración/tokens locales de MCP).
- Claves (`SUPABASE_SERVICE_ROLE_KEY`/`SB_SERVICE_ROLE_KEY`) leídas de variables de entorno,
  no hardcodeadas en el código de las Edge Functions.

## Gaps y verificaciones pendientes (no confirmados en esta pasada, no asumir ninguno de los dos lados)

- **Rate limiting**: no se ha encontrado lógica de límite de tasa en `_shared/` ni en las
  funciones individuales. Pendiente de confirmar si existe a nivel de plataforma Supabase o
  si hay que añadirlo.
- **Idempotencia**: no verificado si `api-tasks`/`api-task-series` son seguros ante reintento
  duplicado (p. ej. doble tap creando dos tareas, o el push de recordatorios duplicando
  notificaciones). Relevante para el caso "doble tap offline" que pide el prompt maestro.
- **Validación de payload**: `api-tasks/index.ts` sí normaliza y valida campos básicos
  (`normalizeRequiredString`, `parseDateParam` con regex `YYYY-MM-DD`) — validación mínima
  presente, pero no se ha auditado el 100% de los campos ni los límites de tamaño
  (`title`/`body` sin límite de longitud visible en el fragmento revisado).
- **Logs**: no se ha confirmado si algún log de Edge Function o de cliente incluye contenido
  de tareas/notas (`title`, `body`, `client_email`, `client_phone` son especialmente
  sensibles porque el esquema incluye datos de reservas/citas de terceros, no solo del
  dueño de la app).
- **Almacenamiento local del secreto**: no verificado dónde/cómo guarda el cliente Kotlin el
  `APP_SECRET` (Keychain/Keystore vs. config en claro) — pendiente de revisar
  `core/network/` en detalle.

## Conector Supabase MCP conectado a esta sesión: proyecto equivocado

El MCP de Supabase disponible en este entorno está autenticado contra
**`ndiooyyqtaeysnedywer` ("oposibots-ui's Project")**, que **no es el backend de AgendNote**:
sus tablas (`usuario`, `examenes`, `preguntas`, `suscripciones`, `ligas`, `conversaciones_soporte`,
etc., ~85 tablas) pertenecen a otro producto (una app de preparación de oposiciones con
exámenes, rachas, ligas y suscripciones). Ninguna tabla de AgendNote (`notes`, `tasks`,
`labels`, `task_labels`, `devices`, `settings`) aparece en ese proyecto.

Por eso esta auditoría de seguridad se basa **exclusivamente en los archivos versionados**
(`supabase/schema.sql`, `policies.sql`, `migrations/`, `functions/`) y no en una consulta en
vivo (`get_advisors`, `execute_sql`) contra la base de datos real de AgendNote — no tengo
acceso a ese proyecto desde aquí. No he seguido inspeccionando el proyecto
`oposibots-ui` más allá de listar tablas y advisories (información ya visible en el panel de
Supabase del propio usuario) precisamente porque no es el objetivo de esta tarea.

Si se quiere una auditoría en vivo real (RLS efectivamente desplegado vs. lo que dice
`policies.sql`, advisories de seguridad/rendimiento reales, logs), hace falta conectar el
MCP al proyecto Supabase correcto de AgendNote.

## Preguntas abiertas para el usuario

1. ¿AgendNote es y seguirá siendo de un solo inquilino (solo tú), o hay intención de
   soportar más de un usuario en el futuro? Cambia por completo el diseño de seguridad
   recomendado.
2. ¿Quieres que conecte el MCP de Supabase al proyecto real de AgendNote para poder hacer
   auditoría en vivo (advisories, RLS desplegado, logs), o prefieres que me limite a lo que
   ya está en el repo?
