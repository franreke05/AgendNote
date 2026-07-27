# Informe final de revisión UI, arquitectura y QA

Fecha: 27 de julio de 2026
Rama de trabajo: `agent/finish-audit-notifications`

## Resultado

Se han revisado y corregido las cuatro pantallas principales, sus estados de carga/error/vacío, los diálogos de creación y detalle, los selectores de fecha y hora, la navegación global y el flujo de recordatorios. Se ha conservado la identidad Glass y se han hecho cambios localizados; no se ha reemplazado la aplicación ni su arquitectura.

El trabajo se contrastó con la documentación funcional existente y con tres revisiones paralelas:

- UI/UX y arquitectura: jerarquía, densidad, contraste, accesibilidad y estructura Compose.
- QA: estados asíncronos, regresiones, persistencia, permisos y pruebas.
- Documentación: navegación prevista, recurrencia, etiquetas, comportamiento de calendario y restricciones del producto.

Los comentarios `// REVIEW:` del código señalan decisiones no obvias para facilitar la revisión manual posterior.

## Cambios pantalla por pantalla

| Área | Qué estaba mal | Cómo se corrigió | Por qué |
|---|---|---|---|
| Navegación global | La barra inferior flotaba encima de cada pantalla y tapaba contenido, especialmente la zona de peligro de Ajustes. Cada pantalla compensaba con márgenes distintos. | La barra ahora forma parte de una `Column` del shell y reserva su propio espacio. Se retiraron los rellenos artificiales de cada destino. | Una sola regla de layout evita solapes en todos los tamaños y reduce código duplicado. |
| Barra inferior | Objetivos táctiles y semántica débiles; el estado seleccionado dependía demasiado del color. | Cada pestaña usa `selectable`, `Role.Tab`, área amplia, etiqueta visible y estado seleccionado no basado solo en color. | Mejora TalkBack, navegación asistida y precisión táctil. |
| Agenda | Había una tarjeta de día redundante, acciones pequeñas, gesto horizontal en el contenedor que competía con el scroll y errores sin recuperación. | Se simplificó la cabecera, se eliminaron gestos conflictivos, las acciones son de 48 dp, los títulos largos se limitan con elipsis y los errores incluyen `Reintentar`. | Reduce ruido visual, evita cambios de día accidentales y permite recuperarse sin reiniciar. |
| Tarjetas de tarea | Completar y borrar tenían blancos táctiles pequeños; el color de borrado y algunos textos perdían contraste. | Se amplió cada acción a 48 dp, se añadieron descripciones accesibles y se usaron tokens de contraste específicos. | Cumple el mínimo móvil y hace distinguibles las acciones normales y destructivas. |
| Nueva tarea | El fondo translúcido mezclaba el formulario con Agenda, algunos campos solo tenían placeholder, el borrador podía reiniciarse, varias opciones quedaban ocultas horizontalmente y el FAB seguía activo detrás. | Se creó un diálogo opaco y desplazable; se añadieron etiquetas persistentes, botones de 48 dp, recurrencia 2×2, paleta 4×4, selectores modales de fecha/hora y ocultación del FAB mientras hay un overlay. | Mantiene contexto sin sacrificar legibilidad, evita pérdida de datos y hace visibles todas las opciones principales. |
| Fecha y hora | Controles pequeños, semántica incompleta y fechas pasadas poco claras. | Flechas y acciones son de 48 dp, las celdas exponen fecha/selección, las fechas pasadas tienen tratamiento visual adicional y el selector de hora anuncia sus valores. | Mejora accesibilidad y reduce errores de selección. |
| Detalle y borrado | Los overlays no tenían una separación modal consistente y las acciones de error/destrucción podían confundirse. | Se unificaron con `Dialog`, scrim, superficie opaca, botones grandes y copia explícita. | Asegura foco modal y evita acciones accidentales. |
| Calendario | El panel ocupaba casi toda la pantalla con espacio vacío; los días dependían mucho del color y seleccionar una fecha no llevaba directamente a su agenda. | Se compactó el mes, se reforzaron selección/hoy/pasado/conteo y tocar un día selecciona la fecha y abre Agenda. Se añadió reintento de carga. | Coincide con el flujo documentado y convierte el calendario en navegación útil, no en una vista aislada. |
| Etiquetas | La paleta horizontal cortaba colores y ofrecía objetivos de unos 26 dp; el campo no tenía etiqueta persistente, el vacío daba poca orientación y nombres largos rompían filas. | La paleta muestra 16 colores en 4×4 con 48 dp y nombre accesible; se etiquetó el campo, se mejoró el vacío, se limitaron nombres y se amplió el control de borrado. | Todas las opciones son visibles y utilizables sin depender del color ni del desplazamiento oculto. |
| Ajustes | La barra tapaba acciones, los modos de color no eran radios semánticos, una URL inválida podía enviarse y la zona destructiva no tenía jerarquía clara. | Se corrigió el viewport, se usaron radios accesibles, validación `http/https`, botones con estados correctos, texto preciso de recordatorios y una zona de peligro contrastada. | Evita configuraciones inválidas y comunica claramente las consecuencias. |
| Estados remotos | Varias mutaciones optimistas podían dejar la UI desincronizada si el backend fallaba. | Ajustes restaura el valor anterior ante fallo; Agenda y Etiquetas conservan caché, muestran error y permiten reintentar. | El usuario no debe ver como guardado un cambio rechazado por servidor. |

## Componentes y arquitectura compartida

- `GlassSurface` dibuja el brillo antes del contenido y no aplica sombra por defecto a todas las superficies.
- Los diálogos usan un relleno modal opaco, por lo que el contenido de fondo no atraviesa el texto.
- `GlassTextField` admite etiqueta accesible y las acciones de búsqueda/limpieza tienen 48 dp.
- El tema incluye `errorContent` y colores modales legibles en claro y oscuro.
- Se eliminó un blur redundante de pantalla completa y la inicialización de red ya no bloquea el hilo UI.
- `AgendaApiClient` se crea de forma diferida fuera del dispatcher principal y define timeouts de conexión, socket y petición.
- Las mutaciones de Agenda viven en un scope de sesión, no en el scope efímero de una pantalla que desaparece al cambiar de pestaña.

## Recordatorios y permisos

Antes, Android podía pedir permisos al arrancar, las alarmas no sobrevivían a reinicio, faltaba una ruta completa de hora exacta y podían quedar avisos obsoletos.

Ahora:

- Los permisos se solicitan desde `Configurar recordatorios`, mediante Activity Result API.
- Android 12+ abre el permiso especial de alarmas exactas cuando hace falta; si no se concede, usa una alarma inexacta segura.
- Los recordatorios se guardan con el payload mínimo, se restauran tras reiniciar el dispositivo y se eliminan al dispararse.
- Completar, borrar, quitar la hora o borrar todas las tareas cancela las alarmas correspondientes.
- Una cola FIFO común serializa programación y cancelación sin bloquear Compose ni depender de `Dispatchers.Main` en tests.
- iOS también implementa la cancelación global de solicitudes pendientes.

## Evidencias de QA

- `:composeApp:testDebugUnitTest`: 39 tests, 0 fallos, 0 errores.
- `:composeApp:testReleaseUnitTest`: 39 tests, 0 fallos, 0 errores.
- `:androidApp:assembleDebug`: `BUILD SUCCESSFUL`.
- APK verificado: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.
- Instalación incremental correcta en emulador Android API 36.
- Recorrido visual final: Agenda, Calendario, Etiquetas, Ajustes, zona de peligro, nueva tarea, recurrencia, paleta, selector de fecha, selector de hora, detalle y confirmación de borrado.
- Prueba E2E de recordatorio: creación con hora, persistencia local, registro en `AlarmManager`, cancelación al borrar y limpieza de la tarea temporal. No quedan datos QA temporales.
- `git diff --check`: sin errores de espacios; solo avisos de conversión LF/CRLF del entorno Windows.

Comando de reproducción:

```powershell
.\gradlew.bat :composeApp:testDebugUnitTest :composeApp:testReleaseUnitTest :androidApp:assembleDebug --no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx1536m'
```

## Riesgos conocidos y siguiente validación

- Este equipo Windows no puede compilar ni firmar la aplicación iOS nativa. Debe ejecutarse el build de Xcode y probar permisos/notificaciones en un iPhone antes de publicar.
- El emulador, casi lleno y usando APK debug, midió arranques fríos de 7,5 s y 5,4 s; el primer arranque tras instalar fue aún más lento por verificación/JIT. Se retiraron bloqueos y blur evitables del código, pero falta medir una build release en hardware real.
- Quedan advertencias de migración futura de `kotlinx.datetime` a `kotlin.time.Instant` y del estado beta de clases `expect/actual`. No bloquean la compilación actual; migrarlas ahora ampliaría innecesariamente el riesgo de esta entrega.
- Las alarmas exactas dependen de una autorización especial del usuario en Android 12+. La aplicación conserva un fallback inexacto para no perder el recordatorio.
