# Deslizar entre pantallas (swipe de tabs) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permitir cambiar entre las 4 pantallas principales (Agenda/Calendario/Etiquetas/Ajustes) deslizando el dedo desde los bordes de la pantalla, además de tocar la barra inferior, con una transición visual de deslizamiento.

**Architecture:** El gesto se detecta en dos franjas invisibles pegadas a los bordes izquierdo/derecho del área de contenido en `AppNavHost`, y dispara la navegación de tab **ya existente** (`navigateToMainTab`) — no se reemplaza `NavHost` por un `HorizontalPager`. La dirección de la transición visual del `NavHost` se calcula comparando el índice de la pestaña saliente contra la entrante en el orden fijo de `MainTab.entries`.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform 1.9.3, Navigation Compose 2.9.0-beta03, `androidx.compose.animation` (transiciones), `androidx.compose.foundation.gestures.detectHorizontalDragGestures` (ya usado en el proyecto para el swipe de las tarjetas de tarea).

## Global Constraints

- Spec de referencia: `docs/superpowers/specs/2026-08-05-swipe-tab-navigation-design.md`.
- No se reemplaza `NavHost` por un `HorizontalPager` (decisión 2 del spec) — el gesto solo dispara `navigateToMainTab`, no hay seguimiento del dedo en tiempo real.
- El gesto solo se reconoce si empieza dentro de una franja de `layout.width(24.dp, 20.dp)` pegada al borde izquierdo o derecho (decisión 3 del spec) — nunca sobre el resto de la pantalla, para no chocar con el swipe de las tarjetas de tarea en Agenda (ver comentario existente en `AgendaScreen.kt:126-128`).
- Umbral de arrastre para disparar el cambio de tab: `layout.width(64.dp, 56.dp)`.
- Todo el código nuevo de UI vive en `app/navigation/AppNavHost.kt` (no se crea un archivo nuevo solo para las franjas de swipe, dado su tamaño pequeño y uso único) salvo la lógica pura de orden de pestañas, que vive en `app/navigation/MainTab.kt` junto al resto de lo relacionado con `MainTab`.
- Sin tests de gestos táctiles reales (no hay infraestructura de UI tests en el proyecto). Verificación: `./gradlew test` + `:androidApp:assembleDebug` en cada tarea, y prueba manual del usuario en su Mac (build iOS) al final.
- Ancho del popup "Listas inteligentes" y blur con Haze quedan fuera de este plan (spec/plan aparte).

---

## Task 1: Orden de pestañas — helpers puros y testeables

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/app/navigation/MainTab.kt`
- Test: `composeApp/src/commonTest/kotlin/com/franciscor/agendnote/MainTabNavigationTest.kt`

**Interfaces:**
- Produces: `enum class SwipeDirection { NEXT, PREVIOUS }` (paquete `com.franciscor.agendnote.app.navigation`).
- Produces: `fun MainTab.next(): MainTab?` — siguiente pestaña en `MainTab.entries`, o `null` si ya es la última (`SETTINGS`).
- Produces: `fun MainTab.previous(): MainTab?` — pestaña anterior en `MainTab.entries`, o `null` si ya es la primera (`AGENDA`).
- Produces: `fun tabSlideDirection(fromRoute: String?, toRoute: String?): SwipeDirection?` — `NEXT` si `toRoute` está a la derecha de `fromRoute` en `MainTab.entries`, `PREVIOUS` si está a la izquierda, `null` si alguna ruta no es de una pestaña conocida o son la misma.

Esta lógica es pura (sin Compose, sin plataforma) — la usan Task 2 (transición del `NavHost`) y Task 3 (gesto de borde), que solo consumen estas funciones y no necesitan reimplementar el cálculo de orden.

- [ ] **Step 1: Escribir los tests (deben fallar — las funciones no existen todavía)**

Crear `composeApp/src/commonTest/kotlin/com/franciscor/agendnote/MainTabNavigationTest.kt`:

```kotlin
package com.franciscor.agendnote

import com.franciscor.agendnote.app.navigation.MainTab
import com.franciscor.agendnote.app.navigation.SwipeDirection
import com.franciscor.agendnote.app.navigation.next
import com.franciscor.agendnote.app.navigation.previous
import com.franciscor.agendnote.app.navigation.tabSlideDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MainTabNavigationTest {
    @Test
    fun `next steps through the bottom bar order`() {
        assertEquals(MainTab.CALENDAR, MainTab.AGENDA.next())
        assertEquals(MainTab.LABELS, MainTab.CALENDAR.next())
        assertEquals(MainTab.SETTINGS, MainTab.LABELS.next())
    }

    @Test
    fun `next returns null past the last tab`() {
        assertNull(MainTab.SETTINGS.next())
    }

    @Test
    fun `previous steps backwards through the bottom bar order`() {
        assertEquals(MainTab.LABELS, MainTab.SETTINGS.previous())
        assertEquals(MainTab.CALENDAR, MainTab.LABELS.previous())
        assertEquals(MainTab.AGENDA, MainTab.CALENDAR.previous())
    }

    @Test
    fun `previous returns null before the first tab`() {
        assertNull(MainTab.AGENDA.previous())
    }

    @Test
    fun `tabSlideDirection is NEXT when moving to a later tab`() {
        assertEquals(
            SwipeDirection.NEXT,
            tabSlideDirection(fromRoute = "agenda", toRoute = "labels"),
        )
    }

    @Test
    fun `tabSlideDirection is PREVIOUS when moving to an earlier tab`() {
        assertEquals(
            SwipeDirection.PREVIOUS,
            tabSlideDirection(fromRoute = "settings", toRoute = "calendar"),
        )
    }

    @Test
    fun `tabSlideDirection is null for the same tab`() {
        assertNull(tabSlideDirection(fromRoute = "agenda", toRoute = "agenda"))
    }

    @Test
    fun `tabSlideDirection is null when a route is unknown or missing`() {
        assertNull(tabSlideDirection(fromRoute = null, toRoute = "agenda"))
        assertNull(tabSlideDirection(fromRoute = "agenda", toRoute = "not-a-tab"))
    }
}
```

- [ ] **Step 2: Ejecutar los tests y comprobar que fallan por compilación**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.franciscor.agendnote.MainTabNavigationTest"`
Expected: FAIL — error de compilación, `next`/`previous`/`tabSlideDirection`/`SwipeDirection` no existen todavía en `com.franciscor.agendnote.app.navigation`.

- [ ] **Step 3: Implementar las funciones**

Modificar `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/app/navigation/MainTab.kt` (contenido completo del archivo tras el cambio):

```kotlin
package com.franciscor.agendnote.app.navigation

enum class MainTab(
    val label: String,
    val route: AppRoute,
) {
    AGENDA("Agenda", AppRoute.Agenda),
    CALENDAR("Calendario", AppRoute.Calendar),
    LABELS("Etiquetas", AppRoute.Labels),
    SETTINGS("Ajustes", AppRoute.Settings),
    ;

    companion object {
        fun fromRoute(route: String?): MainTab? {
            return entries.firstOrNull { it.route.route == route }
        }
    }
}

/**
 * Dirección de un cambio de pestaña respecto al orden fijo de [MainTab.entries] (el mismo orden
 * en que aparecen en `BottomBar`). Se usa tanto para el gesto de borde en `AppNavHost` como para
 * la dirección de la transición de deslizamiento del `NavHost` (ver [tabSlideDirection]).
 */
enum class SwipeDirection { NEXT, PREVIOUS }

/** Pestaña siguiente en el orden de [MainTab.entries], o `null` si ya es la última. */
fun MainTab.next(): MainTab? {
    val entries = MainTab.entries
    return entries.getOrNull(entries.indexOf(this) + 1)
}

/** Pestaña anterior en el orden de [MainTab.entries], o `null` si ya es la primera. */
fun MainTab.previous(): MainTab? {
    val entries = MainTab.entries
    return entries.getOrNull(entries.indexOf(this) - 1)
}

/**
 * Dirección del cambio entre dos rutas del `NavHost` en términos del orden fijo de pestañas
 * ([MainTab.entries]), o `null` si alguna ruta no corresponde a una pestaña conocida o ambas
 * rutas son la misma pestaña.
 */
fun tabSlideDirection(fromRoute: String?, toRoute: String?): SwipeDirection? {
    val from = MainTab.fromRoute(fromRoute) ?: return null
    val to = MainTab.fromRoute(toRoute) ?: return null
    val entries = MainTab.entries
    val fromIndex = entries.indexOf(from)
    val toIndex = entries.indexOf(to)
    return when {
        toIndex > fromIndex -> SwipeDirection.NEXT
        toIndex < fromIndex -> SwipeDirection.PREVIOUS
        else -> null
    }
}
```

- [ ] **Step 4: Ejecutar los tests y comprobar que pasan**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.franciscor.agendnote.MainTabNavigationTest"`
Expected: PASS — 8/8 tests verdes.

- [ ] **Step 5: Regresión completa y commit**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, sin regresiones en los tests existentes.

```bash
git add composeApp/src/commonMain/kotlin/com/franciscor/agendnote/app/navigation/MainTab.kt composeApp/src/commonTest/kotlin/com/franciscor/agendnote/MainTabNavigationTest.kt
git commit -m "Orden de pestanas: helpers next/previous/tabSlideDirection"
```

---

## Task 2: Transición de deslizamiento en el `NavHost`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/app/navigation/AppNavHost.kt`

**Interfaces:**
- Consumes: `fun tabSlideDirection(fromRoute: String?, toRoute: String?): SwipeDirection?` (Task 1).
- Consumes: `enum class SwipeDirection { NEXT, PREVIOUS }` (Task 1).
- Produces: el `NavHost` dentro de `AppNavHost` anima cada cambio de pestaña con un deslizamiento horizontal (izquierda↔derecha según el orden de `MainTab.entries`) en vez de un corte instantáneo. Nada nuevo queda expuesto a otros archivos — es un cambio de comportamiento visual interno a `AppNavHost`.

Sin test nuevo — es cableado de animación de Compose que no tiene infraestructura de UI test en este proyecto (ver Global Constraints). La lógica de dirección que consume (`tabSlideDirection`) ya quedó cubierta por los tests de Task 1. Verificación: compilación + regresión completa.

- [ ] **Step 1: Añadir los imports necesarios**

En `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/app/navigation/AppNavHost.kt`, añadir junto a los imports existentes (tras el bloque de imports de `androidx.compose.foundation.layout.*`, antes de `androidx.compose.runtime.Composable`):

```kotlin
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
```

Y junto a los imports de `androidx.navigation.compose.*` existentes, añadir:

```kotlin
import androidx.navigation.NavBackStackEntry
```

- [ ] **Step 2: Definir las funciones de transición**

Añadir estas dos funciones privadas a nivel de archivo en `AppNavHost.kt`, justo antes de `fun AppNavHost(` (antes de la línea `@Composable\nfun AppNavHost(`):

```kotlin
// REVIEW: se reutiliza la misma lógica para enter/popEnter y exit/popExit — la dirección se
// calcula a partir del orden fijo de pestañas (tabSlideDirection), no de si la navegación es un
// push o un pop, así que el resultado ya es correcto en ambos sentidos sin duplicar la lógica.
private fun tabEnterTransition(
    scope: AnimatedContentTransitionScope<NavBackStackEntry>,
): EnterTransition {
    return when (
        tabSlideDirection(
            fromRoute = scope.initialState.destination.route,
            toRoute = scope.targetState.destination.route,
        )
    ) {
        SwipeDirection.NEXT -> slideInHorizontally(initialOffsetX = { it }) + fadeIn()
        SwipeDirection.PREVIOUS -> slideInHorizontally(initialOffsetX = { -it }) + fadeIn()
        null -> fadeIn()
    }
}

private fun tabExitTransition(
    scope: AnimatedContentTransitionScope<NavBackStackEntry>,
): ExitTransition {
    return when (
        tabSlideDirection(
            fromRoute = scope.initialState.destination.route,
            toRoute = scope.targetState.destination.route,
        )
    ) {
        SwipeDirection.NEXT -> slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
        SwipeDirection.PREVIOUS -> slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        null -> fadeOut()
    }
}
```

- [ ] **Step 3: Cablear las transiciones en la llamada a `NavHost`**

En `AppNavHost.kt`, la llamada actual es:

```kotlin
                    NavHost(
                        navController = navController,
                        startDestination = AppRoute.Agenda.route,
                        modifier = Modifier.fillMaxSize(),
                    ) {
```

Reemplazar por:

```kotlin
                    NavHost(
                        navController = navController,
                        startDestination = AppRoute.Agenda.route,
                        modifier = Modifier.fillMaxSize(),
                        enterTransition = { tabEnterTransition(this) },
                        exitTransition = { tabExitTransition(this) },
                        popEnterTransition = { tabEnterTransition(this) },
                        popExitTransition = { tabExitTransition(this) },
                    ) {
```

- [ ] **Step 4: Compilar y correr la regresión completa**

Run: `./gradlew test :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL — confirma que el código Compose nuevo compila (los tests JVM por sí solos no ejercitan las lambdas de transición, pero si no compilan, ninguna de las dos tareas termina en éxito) y que no hay regresiones.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/franciscor/agendnote/app/navigation/AppNavHost.kt
git commit -m "Transicion de deslizamiento entre pestanas en el NavHost"
```

---

## Task 3: Franjas de swipe en los bordes

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/franciscor/agendnote/app/navigation/AppNavHost.kt`

**Interfaces:**
- Consumes: `fun MainTab.next(): MainTab?`, `fun MainTab.previous(): MainTab?` (Task 1).
- Consumes: `selectedTab: MainTab` y `navigateToMainTab: (MainTab) -> Unit`, ambos ya definidos dentro de `AppNavHost` (líneas 77 y 120-126 del archivo actual).
- Produces: al arrastrar desde una franja de `layout.width(24.dp, 20.dp)` pegada al borde izquierdo o derecho del área de contenido, más allá del umbral `layout.width(64.dp, 56.dp)`, cambia a la pestaña siguiente/anterior. Nada queda expuesto fuera de `AppNavHost.kt`.

Sin test nuevo — gesto táctil real, sin infraestructura de UI test en el proyecto (ver Global Constraints). La lógica de a qué pestaña saltar (`next`/`previous`) ya quedó cubierta por los tests de Task 1. Verificación: compilación + regresión completa + prueba manual del usuario en su Mac.

- [ ] **Step 1: Añadir los imports necesarios**

En `AppNavHost.kt`, añadir:

```kotlin
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
```

- [ ] **Step 2: Añadir el composable `EdgeSwipeZone`**

Añadir esta función privada en `AppNavHost.kt`, después del cierre de la función `AppNavHost` (antes de `@Composable\nprivate fun AgendaRoute(`):

```kotlin
/**
 * Franja invisible pegada a un borde de la pantalla que reconoce un arrastre horizontal y
 * dispara [onSwipe] al soltar si el arrastre acumulado supera [thresholdPx] en esa dirección.
 * Deliberadamente angosta y solo en los bordes (ver Global Constraints del plan de swipe): las
 * tarjetas de tarea en Agenda ya tienen su propio gesto de arrastre horizontal, y un intento
 * previo de un gesto de pantalla completa chocó con él (ver AgendaScreen.kt).
 */
@Composable
private fun EdgeSwipeZone(
    onSwipe: (SwipeDirection) -> Unit,
    thresholdPx: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.pointerInput(thresholdPx) {
            var accumulatedDrag = 0f
            detectHorizontalDragGestures(
                onDragStart = { accumulatedDrag = 0f },
                onHorizontalDrag = { _, dragAmount ->
                    accumulatedDrag += dragAmount
                },
                onDragEnd = {
                    when {
                        accumulatedDrag <= -thresholdPx -> onSwipe(SwipeDirection.NEXT)
                        accumulatedDrag >= thresholdPx -> onSwipe(SwipeDirection.PREVIOUS)
                    }
                    accumulatedDrag = 0f
                },
                onDragCancel = { accumulatedDrag = 0f },
            )
        },
    )
}
```

- [ ] **Step 3: Insertar las dos franjas dentro del `Box` que envuelve el `NavHost`**

En `AppNavHost.kt`, el bloque actual es:

```kotlin
                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = AppRoute.Agenda.route,
                        modifier = Modifier.fillMaxSize(),
                        enterTransition = { tabEnterTransition(this) },
                        exitTransition = { tabExitTransition(this) },
                        popEnterTransition = { tabEnterTransition(this) },
                        popExitTransition = { tabExitTransition(this) },
                    ) {
                        composable(AppRoute.Agenda.route) {
                            ...
                        }
                        ...
                    }
                }
```

Insertar las dos franjas justo después del cierre del `NavHost` (la llave `}` que cierra su bloque de rutas) y antes del cierre del `Box`:

```kotlin
                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = AppRoute.Agenda.route,
                        modifier = Modifier.fillMaxSize(),
                        enterTransition = { tabEnterTransition(this) },
                        exitTransition = { tabExitTransition(this) },
                        popEnterTransition = { tabEnterTransition(this) },
                        popExitTransition = { tabExitTransition(this) },
                    ) {
                        composable(AppRoute.Agenda.route) {
                            ...
                        }
                        ...
                    }

                    // REVIEW: franjas de solo-borde, no un gesto de pantalla completa — ver el
                    // comentario en EdgeSwipeZone y AgendaScreen.kt:126-128 sobre por que un
                    // swipe libre choca con el swipe de las tarjetas de tarea en Agenda.
                    val edgeSwipeWidth = layout.width(24.dp, 20.dp)
                    val edgeSwipeThresholdPx = with(LocalDensity.current) {
                        layout.width(64.dp, 56.dp).toPx()
                    }
                    val onEdgeSwipe: (SwipeDirection) -> Unit = { direction ->
                        val target = when (direction) {
                            SwipeDirection.NEXT -> selectedTab.next()
                            SwipeDirection.PREVIOUS -> selectedTab.previous()
                        }
                        target?.let { navigateToMainTab(it) }
                    }
                    EdgeSwipeZone(
                        onSwipe = onEdgeSwipe,
                        thresholdPx = edgeSwipeThresholdPx,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(edgeSwipeWidth),
                    )
                    EdgeSwipeZone(
                        onSwipe = onEdgeSwipe,
                        thresholdPx = edgeSwipeThresholdPx,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(edgeSwipeWidth),
                    )
                }
```

(Los `composable(...)` internos del `NavHost` no cambian — se muestran acortados con `...` aquí solo para ubicar el punto de inserción; dejar su contenido tal cual está en el archivo.)

- [ ] **Step 4: Compilar y correr la regresión completa**

Run: `./gradlew test :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/franciscor/agendnote/app/navigation/AppNavHost.kt
git commit -m "Deslizar entre pestanas desde los bordes de la pantalla"
```

- [ ] **Step 6: Verificación manual (usuario, en Mac/iOS)**

Sin automatizar (ver Global Constraints). Antes de dar la tarea por cerrada, el usuario debe confirmar en un build iOS real:
1. Deslizar desde el borde izquierdo o derecho cambia de pestaña en el orden Agenda→Calendario→Etiquetas→Ajustes (y al revés).
2. En Agenda, deslizar sobre una tarjeta de tarea (no en el borde) sigue completando/eliminando la tarea como hoy — el gesto de borde no lo interfiere.
3. En los extremos (Agenda deslizando hacia atrás, Ajustes deslizando hacia adelante) no pasa nada — no da la vuelta.
4. La sensación del umbral (`64dp`/`56dp`) no se siente ni demasiado sensible ni demasiado dura; si hace falta, ajustar el valor en `EdgeSwipeZone` (Task 3) en una iteración siguiente.
