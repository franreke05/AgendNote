# Checklist de verificación iOS — Operación Aniversario

Para ejecutar en cuanto haya acceso a un Mac con Xcode, antes del 13 de agosto de 2026.
Generado a partir de la revisión estática iOS de esta operación (ver
`docs/OPERATION_ANNIVERSARY_STATUS.md`). Nada de esto se ha compilado ni ejecutado todavía —
todo el código listado abajo fue escrito y compilado solo para Android/JVM en un entorno sin
macOS.

## 1. Build

```bash
# Desde la raíz del repo
./gradlew :composeApp:embedAndSignAppleFrameworkForXcode -Pkotlin.native.cocoapods.platform=iphonesimulator

cd iosApp
xcodebuild -project iosApp.xcodeproj -scheme iosApp \
  -destination 'platform=iOS Simulator,name=iPhone 15,OS=latest' \
  -configuration Debug build
```

Si falla la compilación, **empezar por los archivos tocados en esta operación** (orden de
sospecha, de más a menos probable causa de un error de compilación no visto en Android/JVM):

1. `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/presentation/view/AgendaOverlays.kt` — el más grande, tocado dos veces (edición de tarea + aviso de recordatorios).
2. `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/feature/agenda/presentation/view/AgendaDayComponents.kt` — haptics (`LocalHapticFeedback`/`HapticFeedbackType`), guarda de swipe, semántica combinada.
3. `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/core/ui/components/GlassInputs.kt` — foco/disabled en `GlassTextField`.
4. `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/core/ui/theme/GlassMetrics.kt` (nuevo archivo).
5. Todo lo demás tocado por esta operación (ver `git log --oneline agent/operacion-aniversario` para la lista completa de commits).

Nada de lo anterior debería, en teoría, comportarse distinto en Kotlin/Native — es código común
sin ninguna API específica de plataforma nueva salvo `LocalHapticFeedback` (API común de
Compose, puenteada a iOS por JetBrains desde CMP ~1.7+, no cinterop manual). Si algo falla aquí,
es la primera señal real de que esa suposición estaba equivocada.

## 2. Lanzar y probar en simulador

```bash
xcrun simctl boot "iPhone 15"
xcrun simctl install booted <ruta al .app generado en DerivedData>
xcrun simctl launch booted com.franciscor.agendnote
```

## 3. Qué verificar, por funcionalidad de esta operación

### 3.1 Edición de tarea (P1, lo más nuevo y de mayor riesgo)
- Crear una tarea nueva con: título, notas, hora, deadline, 2 etiquetas, 2 recordatorios, 2 subtareas (una marcada como hecha después de crearla).
- Abrir su detalle → "Editar" → confirmar que TODO aparece precargado correctamente, incluida la subtarea marcada como hecha (no debe aparecer desmarcada).
- Cambiar solo el título, guardar → confirmar que hora/deadline/etiquetas/recordatorios/subtareas NO se perdieron.
- Editar una tarea y **borrar** su hora (dejarla en blanco) → guardar → volver a abrir el detalle → confirmar que la hora quedó realmente borrada (no que sigue apareciendo la vieja). Repetir para deadline y para notas.
- Editar una tarea y cambiarla de día → guardar → confirmar que desaparece del día original y aparece en el nuevo (puede requerir navegar al nuevo día para que se recargue, comportamiento esperado).
- Editar una tarea que pertenece a una serie recurrente → confirmar que aparece el aviso de "no afecta a las demás apariciones" y que solo esa ocurrencia cambia (ir al Calendario y confirmar que las demás fechas de la serie siguen con los datos originales).
- Editar una tarea ya atrasada (fecha pasada) sin cambiarle la fecha → confirmar que se puede guardar sin error de "fecha pasada".
- Crear una tarea nueva de cero (flujo normal, sin tocar edición) → confirmar que se sigue viendo y comportando exactamente igual que antes de esta operación (recurrencia visible, plantillas visibles, todo el formulario en blanco).

### 3.2 Recordatorios múltiples — aviso
- En "Nueva tarea", seleccionar 2+ recordatorios → confirmar que aparece el aviso "Por ahora solo se te avisará con el recordatorio más próximo...".
- Deseleccionar hasta quedar en 1 → el aviso desaparece.
- Aplicar una plantilla que traiga 2+ recordatorios → el aviso aparece sin interacción adicional.
- Confirmar (con paciencia, esperando la hora) que efectivamente solo se dispara UNA notificación por tarea, la más próxima — comportamiento ya esperado y documentado, no un bug nuevo.

### 3.3 Haptics
- Activar "Vibración de sistema" en Ajustes > Sonidos y hápticos del simulador/dispositivo (en dispositivo real; el simulador de Xcode no reproduce hápticos físicos, solo lanza `AVHapticFeedback` a Instruments — hace falta un iPhone físico para sentirlo de verdad).
- Completar una tarea (botón o swipe) → confirmar vibración.
- Borrar una tarea (botón o swipe) → confirmar vibración.
- Si es posible, usar Instruments (plantilla "Core Haptics" o consola) para confirmar qué `HapticFeedbackType` llegó realmente al hardware — anotar el resultado en este documento para futuras decisiones de diferenciar intensidad completar vs. borrar.

### 3.4 Guarda de borde en swipe
- Intentar iniciar un swipe sobre una tarjeta de tarea empezando literalmente en el borde izquierdo/derecho de la pantalla → confirmar que NO dispara completar/borrar accidentalmente.
- Confirmar que un swipe normal (empezando en el centro de la tarjeta) sigue funcionando igual que antes.

### 3.5 VoiceOver
- Ajustes > Accesibilidad > VoiceOver ON.
- Navegar la Agenda con swipes de VoiceOver → cada tarjeta debe anunciarse como una unidad coherente (título + hora + estado), no como fragmentos sueltos.
- Confirmar que completar/borrar siguen siendo alcanzables con VoiceOver activo (deberían aparecer como acciones personalizadas del nodo fusionado, o seguir siendo alcanzables como elementos propios — confirmar cuál de las dos cosas ocurre realmente y si es utilizable).
- Repetir en el nuevo botón "Editar" de `TaskDetailsOverlay`.

### 3.6 Accesibilidad de plataforma (no tocado en esta operación, solo confirmar que sigue como estaba)
- Ajustes > Accesibilidad > Movimiento → activar "Reducir movimiento" CON LA APP ABIERTA (no reiniciar) → confirmar si el fondo (`GlassBackground`) reacciona sin salir/volver a la pantalla, o si hace falta reabrir la app (comportamiento conocido: lectura puntual, no reactiva, en iOS — confirmar que sigue siendo así, no es un bug nuevo de esta operación).
- Repetir con "Reducir transparencia".

### 3.7 General
- Safe areas / notch / Dynamic Island: probar en iPhone 15 Pro (Dynamic Island) y en un modelo sin notch si está disponible, portrait y landscape si la app lo permite.
- Teclado: abrir "Nueva tarea", bajar hasta "Nueva subtarea"/"Crear etiqueta" (en Etiquetas), confirmar que el teclado no los tapa.
- Recorrido completo de las 4 pestañas (Agenda/Calendario/Etiquetas/Ajustes) sin errores visuales evidentes.

## 4. Al terminar

Anotar en `docs/OPERATION_ANNIVERSARY_STATUS.md` (`IOS_STATUS`): qué se compiló, qué se probó
de la lista de arriba, qué falló y se corrigió, y qué queda pendiente. No declarar iOS
"validado" salvo que se haya recorrido esta lista completa al menos una vez en un dispositivo
físico real (el simulador no sustituye la prueba de hápticos ni de rendimiento real).
