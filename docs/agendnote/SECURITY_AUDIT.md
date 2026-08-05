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

## Preguntas abiertas para el usuario — respondidas 2026-08-04

1. ¿AgendNote es y seguirá siendo de un solo inquilino? → **Sí, siempre.**
2. ¿Conectar el MCP de Supabase al proyecto real? → **No, auditoría solo con el repo.**

## Fase 2 — resultado (2026-08-04)

### Corregido

- **Fuga de errores internos del backend al cliente (hallazgo confirmado, severidad media).**
  Las 6 Edge Functions devolvían `error.message` de Postgres/PostgREST crudo en cualquier
  fallo de base de datos (`if (error) return errorResponse(error.message, 500)`) y en el
  `catch` de nivel superior. Corregido con un helper compartido
  `internalErrorResponse(error)` (`_shared/response.ts`) que siempre devuelve un mensaje fijo
  y genérico, nunca el texto real de la excepción — commit `11b5c26`.
- **El mismo problema existía en el cliente Kotlin.** `resolveServerError()` reenviaba
  `ResponseException.message` (texto crudo de Ktor/HTTP) directamente a la UI de creación de
  tareas. Corregido para devolver siempre un mensaje fijo en español — commit `acf4b10`, con
  test TDD real (RED por fallo de aserción real, no de compilación).
- **No verificado en el sentido estricto**: el cambio de Edge Functions no se pudo
  compilar/ejecutar (no hay Deno instalado en este entorno) — es una sustitución mecánica de
  patrón, revisada línea por línea vía `git diff`, pero sigue siendo código sin ejecutar.

### Revisado y ya correcto (sin cambios necesarios)

- **Doble tap al guardar una tarea**: el botón "Guardar" de `NewTaskSheet` ya se deshabilita
  mientras `isSaving == true` (`AgendaOverlays.kt`) — un segundo tap no dispara una segunda
  petición.
- **Reintentos automáticos duplicando peticiones**: `AgendaApiClient` no instala el plugin
  `HttpRequestRetry` de Ktor — no hay reintento automático a nivel de cliente HTTP que pueda
  duplicar un `POST`.
- **Log de secretos/contenido privado**: no hay ningún `console.log`/`console.error`/etc. en
  ninguna Edge Function (`grep` sin resultados) — no hay fuga de `title`/`body`/`client_email`
  ni del `APP_SECRET` vía logs de plataforma por logging propio del código.
- **CORS y comparación del secreto**: ya endurecidos en un commit anterior (`2345097`),
  confirmado leyendo `_shared/auth.ts` y `_shared/cors.ts`.

### Confirmado como riesgo residual aceptado (no una tarea pendiente de "arreglar")

- **`APP_SECRET` embebido en el binario cliente.** Android lo expone vía
  `BuildConfig.APP_SECRET` (constante de compilación en el APK); iOS vía `Info.plist`. Ambos
  son extraíbles por cualquiera con el APK/IPA. Esto es inherente a la arquitectura de un solo
  secreto compartido sin autenticación por usuario que el usuario confirmó mantener
  ("Un solo usuario, siempre") — no tiene una solución incremental; solo un rediseño a
  Supabase Auth lo cambiaría, y eso está fuera de alcance por decisión explícita.

### Pendiente, documentado, no implementado en esta pasada (severidad baja para un solo
inquilino; no crítico ni alto)

- **Rate limiting** en los endpoints de Edge Functions: no existe. Requiere almacenamiento de
  estado (KV/tabla) y una decisión de límites concretos; se pospone por no ser crítico cuando
  el único llamante legítimo es el propio dueño de la app.
- **Idempotencia ante "éxito en el servidor pero fallo de red en el cliente"** (no el doble
  tap, que ya está cubierto): si un `POST /api-tasks` tiene éxito pero la respuesta se pierde
  antes de llegar al cliente, un reintento manual del usuario podría crear una tarea
  duplicada. Necesitaría una idempotency key generada por el cliente y deduplicación en el
  Edge Function; no implementado.
- **Límites de longitud de payload** (`title`, `body`, `client_email`, etc. en `api-tasks`):
  `normalizeOptionalString`/`normalizeRequiredString` validan presencia pero no longitud
  máxima. Defensa en profundidad de prioridad baja, no una vulnerabilidad activa.
