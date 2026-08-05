# Deslizar entre pantallas (tabs) — Design

## Contexto

Parte de una petición más amplia de tres cambios sobre el popup "Listas inteligentes"
(`SmartListsOverlay` en `feature/agenda/presentation/view/AgendaOverlays.kt`) y la navegación
principal: 1) ensanchar el popup, 2) fondo difuminado real detrás del popup (librería Haze),
3) **deslizar para cambiar entre las 4 pantallas principales (esta pieza)**. El usuario quiere
priorizar y probar el swipe ahora en su Mac (build iOS); los puntos 1 y 2 quedan para una
iteración aparte.

La navegación principal (`AppNavHost.kt`) usa 4 rutas planas sin sub-navegación
(`AppRoute.Agenda/Calendar/Labels/Settings`, enum `MainTab`), conmutadas hoy solo por
`BottomBar` (tap) vía `navController.navigate(...)`.

**Restricción clave descubierta en el propio código**: cada `TaskCard` en la pantalla Agenda ya
tiene su propio gesto de arrastre horizontal (`detectHorizontalDragGestures` en
`AgendaDayComponents.kt`) para completar/eliminar por swipe. Un comentario existente en
`AgendaScreen.kt` (líneas 126-128) documenta que un intento previo de swipe horizontal a nivel
de pantalla completa (para cambiar de día) chocó con ese gesto de las tarjetas y se quitó por
poco fiable. Un swipe de pantalla completa para cambiar de tab sufriría el mismo problema
exactamente en el tab Agenda.

## Decisiones acordadas con el usuario

1. **Alcance de esta pieza**: solo el swipe entre las 4 pantallas principales. Ancho del popup y
   blur con Haze quedan fuera, para una iteración posterior.
2. **Enfoque de navegación**: el gesto dispara la navegación ya existente (`navigateToMainTab`
   dentro de `AppNavHost`), no se reemplaza `NavHost` por un `HorizontalPager`. Menor riesgo,
   conserva el guardado de estado por pestaña (`saveState`/`restoreState` ya configurado).
3. **Zona del gesto**: reconocido solo si el arrastre empieza dentro de una franja angosta
   pegada al borde izquierdo o derecho de la pantalla (no en cualquier punto), para evitar el
   conflicto ya documentado con el swipe de las tarjetas de tarea.

## Diseño

### Zonas de detección

Dos `Box` invisibles, de ancho `layout.width(12.dp, 10.dp)` y alto completo, superpuestos sobre
el contenido del `NavHost` dentro de `AppNavHost`, pegados al borde izquierdo y al borde derecho
del área de contenido (dentro del `Box(modifier = Modifier.weight(1f))` que ya envuelve el
`NavHost`, para quedar por encima de las pantallas pero no de la `BottomBar`).

Esta franja queda fuera del área donde las tarjetas de tarea son interactivas: `AgendaScreen`
aplica su propio `contentInset` (`16dp`/`14dp`) *dentro* de ese mismo `Box(weight(1f))` — el
`contentHorizontalMargin` de `AppNavHost` (`4dp`/`4dp`) se aplica en un `Box` exterior distinto y
no reduce más ese espacio compartido, así que no cuenta para este cálculo. Con ancho de franja
`24dp`/`20dp` (valor original de este documento) la franja se solapaba unos `8dp` con el borde de
las tarjetas — lo detectó la revisión final del plan de implementación y se corrigió a
`12dp`/`10dp`, que sí queda dentro del `contentInset` de `16dp`/`14dp` sin tocar las tarjetas.

Cada franja usa `Modifier.pointerInput` + `detectHorizontalDragGestures` para acumular el delta
horizontal del arrastre. Al soltar (`onDragEnd`):

- Si el arrastre acumulado supera un umbral (`layout.width(64.dp, 56.dp)`) hacia la izquierda →
  ir al tab siguiente en `MainTab.entries` (si existe; en `Settings`, el último, no hace nada).
- Si supera el umbral hacia la derecha → ir al tab anterior (si existe; en `Agenda`, el primero,
  no hace nada).
- Por debajo del umbral, o `onDragCancel`, no cambia de tab (evita disparos accidentales por un
  toque cerca del borde).

No hay seguimiento visual del dedo en tiempo real (eso sería un `HorizontalPager`, descartado en
la decisión 2) — el gesto solo decide *si* navegar; la transición visual la da el `NavHost`
(ver siguiente sección).

### Transición visual en el `NavHost`

`NavHost` en `AppNavHost.kt` gana `enterTransition`/`exitTransition`/`popEnterTransition`/
`popExitTransition`, calculando la dirección comparando el índice en `MainTab.entries` del tab
saliente contra el entrante (usando `initialState`/`targetState` de `AnimatedContentScope`, que
`NavHost` ya expone en esos parámetros): si el destino tiene índice mayor, desliza de derecha a
izquierda; si es menor, de izquierda a derecha. Mismo efecto tanto si el cambio de tab vino de
tocar la `BottomBar` como del gesto de borde — ambos caminos pasan por `navigateToMainTab`.

### Alcance de la implementación

Todo el código nuevo vive en `app/navigation/AppNavHost.kt` (las franjas de detección como un
composable privado en el mismo archivo, dado su tamaño pequeño y uso único — no se crea un
archivo nuevo). No se toca `AgendaDayComponents.kt` ni el gesto existente de las tarjetas.

## Errores y casos límite

- En los extremos (`Agenda` deslizando hacia atrás, `Settings` deslizando hacia adelante) el
  gesto simplemente no navega — no hay wrap-around ni ningún feedback de "límite alcanzado"
  (mismo comportamiento silencioso que tocar una `BottomBarItem` ya seleccionada).
- El gesto no interfiere con el botón flotante de añadir tarea (`FloatingAddButton`, alineado
  `BottomEnd`) porque las franjas son delgadas y están pegadas a los bordes, lejos de esa
  esquina.
- Accesibilidad: el gesto es un atajo adicional, no reemplaza ningún control — `BottomBar` (con
  sus roles `Tab` ya declarados) sigue siendo el camino principal para TalkBack/lectores de
  pantalla, igual que ya ocurre con el swipe de las tarjetas de tarea (atajo sobre botones
  explícitos existentes).

## Testing

Sin test automatizado de gestos táctiles reales (no hay infraestructura de UI tests en el
proyecto para esto — los tests existentes son unitarios sobre ViewModels/dominio). Verificación:
`./gradlew test` para confirmar que no se rompe nada existente, y prueba manual del usuario en
su Mac (build iOS) para validar la sensación del gesto y el umbral elegido.

## Fuera de alcance

- Ancho del popup "Listas inteligentes" y fondo difuminado con Haze — pieza aparte ya diseñada
  en la conversación, pendiente de spec propio.
- `HorizontalPager` con seguimiento del dedo en tiempo real — descartado explícitamente por el
  riesgo de gestos anidados sobre las tarjetas de Agenda.
- Cambiar el umbral/ancho de franja por plataforma (Android vs iOS) — se usa el mismo valor
  (`AppLayout.metrics`, ya adaptativo por tamaño de pantalla) en ambas; si en la prueba en Mac
  se siente mal calibrado, se ajusta en una iteración siguiente.
