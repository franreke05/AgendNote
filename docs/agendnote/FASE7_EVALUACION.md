# Fase 7 — Fichas de evaluación de funcionalidades "después"

Tal como exige el prompt maestro ("Primero evalúa, no implementes todo"), cada candidata se
evalúa antes de decidir si se construye. No se implementa nada solo porque esté en la lista.

## Plantillas personales

- **Problema**: crear la misma tarea (rutina, cierre mensual, viaje) desde cero cada vez.
- **Valor**: alto para el uso personal que ya tiene la app.
- **Complejidad**: baja. Puramente del lado del cliente — guardar la forma de un
  `TaskDraft` (título, notas, hora, etiquetas, recordatorios relativos, subtareas) con un
  nombre, sin tocar el backend en absoluto.
- **Impacto backend/offline/seguridad/privacidad**: ninguno (no hay tabla nueva; se persiste
  como una `setting` más usando `api-settings`, que ya existe, serializando la lista de
  plantillas como JSON en un único valor).
- **Impacto iOS/Android**: ninguno específico de plataforma.
- **Recomendación**: **AHORA** — bajo riesgo, sin dependencias nuevas, se puede verificar por
  completo con tests + compilación en este entorno. Implementada en esta misma pasada (ver
  más abajo).

## Exportar datos (JSON)

- **Problema**: no hay forma de sacar los datos de la app si el usuario quiere respaldarlos o
  dejar de usarla.
- **Valor**: medio-alto — genera confianza, reduce el bloqueo del proveedor.
- **Complejidad**: la **serialización** es trivial (ya hay DTOs `@Serializable`). La parte
  arriesgada es la **entrega** del archivo: escribir a almacenamiento y abrir el selector de
  compartir nativo (`Intent.ACTION_SEND` en Android, `UIActivityViewController` en iOS) es
  código específico de plataforma que este entorno no puede verificar (sin emulador, sin
  Xcode).
- **Decisión de alcance**: implementar solo la generación del JSON + copiarlo al portapapeles
  (`LocalClipboardManager` de Compose, multiplataforma, sin código nativo) en vez de un
  selector de compartir de archivos. Cubre el caso de uso real ("quiero sacar mis datos") sin
  el riesgo de código de plataforma sin verificar.
- **Recomendación**: **AHORA, con alcance reducido** (portapapeles, no compartir archivo).
  Implementado en esta misma pasada.

## Bloqueo biométrico

- **Problema**: proteger la vista de la app en un dispositivo desbloqueado.
- **Valor**: medio — útil pero no crítico para una app sin datos financieros.
- **Complejidad**: alta en la práctica de esta sesión concreta: `BiometricPrompt` (Android) y
  `LocalAuthentication`/`LAContext` (iOS) son APIs de seguridad reales — un error sutil (por
  ejemplo, no manejar bien el caso de fallback a PIN, o un estado que deje la UI accesible tras
  un fallo) es exactamente el tipo de bug que no se debe introducir sin poder probarlo en un
  dispositivo real. Este entorno no tiene emulador Android corriendo ni Xcode/macOS.
- **Recomendación**: **DESPUÉS**, en una sesión con acceso a dispositivo real para verificar
  el flujo completo (éxito, fallo, cancelación, fallback a PIN) antes de confiar en él. No
  implementado aquí — el riesgo de un bypass no detectado supera el valor de tenerlo a medias.

## Deep links

- **Problema**: abrir una fecha o tarea concreta desde una notificación, widget o enlace
  externo.
- **Valor**: medio — mejora la integración con notificaciones ya existentes.
- **Complejidad**: media. Necesita `AndroidManifest.xml` (intent filter) y configuración de
  esquema de URL en iOS (`Info.plist`), además del parseo de la ruta en `AppNavHost`. Es
  verificable en parte (el parseo de ruta es testeable en `commonTest`), pero la integración
  real con el sistema operativo no se puede probar sin lanzar la app en un dispositivo.
- **Recomendación**: **DESPUÉS**. El código de parseo de rutas sería una buena pieza aislada
  para una fase futura, pero la integración de manifest/Info.plist no tiene sentido sin poder
  verificar que el SO realmente la dispara.

## Calendario de solo lectura (eventos del sistema)

- **Problema**: ver los eventos del calendario del dispositivo junto a las tareas para no
  sobreplanificar.
- **Valor**: alto, pero requiere permisos de calendario del sistema (`EventKit` en iOS,
  `CalendarContract` en Android) — superficie de plataforma grande y con implicaciones de
  privacidad (leer el calendario completo del usuario).
- **Complejidad**: alta. No es solo leer eventos: hay que fusionarlos visualmente con las
  tareas sin confundirlos, pedir permiso de forma justificada, y manejar la ausencia de
  permiso con gracia.
- **Recomendación**: **DESPUÉS**. Fuera de alcance de esta sesión — necesita su propio ciclo
  de diseño (¿qué franja horaria se muestra?, ¿todos los calendarios o uno elegido?) antes de
  escribir código.

## Time blocking (arrastrar tareas a una franja horaria)

- **Problema**: asignar hora y duración arrastrando tareas sobre una vista de agenda por
  horas.
- **Valor**: alto para quien plani­fica por bloques de tiempo.
- **Complejidad**: alta — una vista de calendario por horas con gestos de arrastrar y soltar
  es un componente de UI grande y nuevo, con mucho matiz de interacción (¿qué pasa al soltar
  fuera de rango?, ¿cómo se ve mientras se arrastra?) que normalmente se itera mirando la
  pantalla, no a ciegas.
- **Recomendación**: **DESPUÉS**. Candidata a un ciclo de diseño visual propio con capturas
  de pantalla reales, no algo para construir sin poder verlo.

## Widgets

- **Problema**: ver la tarea de hoy o crear una rápida desde la pantalla de inicio del
  dispositivo.
- **Valor**: alto — es contenido "de un vistazo" por definición.
- **Complejidad**: muy alta para este entorno específico: Android necesita
  `AppWidgetProvider`/Glance y un flujo de build completamente distinto; iOS necesita
  WidgetKit y una extensión de app separada compilada con Xcode. Ninguna de las dos partes se
  puede compilar ni probar aquí.
- **Recomendación**: **DESPUÉS**, con acceso a Android Studio real (para Glance) y Xcode
  (para WidgetKit). No se intenta en esta sesión.

## Resumen de decisiones

| Funcionalidad | Decisión | Implementada esta sesión |
|---|---|---|
| Plantillas | Ahora | Sí |
| Exportar (portapapeles JSON) | Ahora, alcance reducido | Sí |
| Bloqueo biométrico | Después | No — riesgo de seguridad sin poder verificar en dispositivo |
| Deep links | Después | No — integración de plataforma sin poder verificar |
| Calendario de solo lectura | Después | No — necesita su propio ciclo de diseño |
| Time blocking | Después | No — necesita iteración visual con capturas reales |
| Widgets | Después | No — requiere Android Studio/Xcode reales |
