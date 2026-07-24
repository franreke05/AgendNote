package com.franciscor.agendnote.core.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

private const val BaseWidth = 390f
private const val BaseHeight = 844f

@Immutable
data class AppLayoutMetrics(
    val widthScale: Float,
    val heightScale: Float,
    val contentScale: Float,
) {
    val globalInset: Dp
        get() = size(5.dp, 4.dp)

    fun width(base: Dp, min: Dp = 0.dp): Dp = (base.value * widthScale).dp.coerceAtLeast(min)

    fun height(base: Dp, min: Dp = 0.dp): Dp = (base.value * heightScale).dp.coerceAtLeast(min)

    fun size(base: Dp, min: Dp = 0.dp): Dp = (base.value * contentScale).dp.coerceAtLeast(min)

    fun text(base: TextUnit, min: TextUnit = 0.sp): TextUnit {
        val scaledValue = base.value * contentScale
        return max(scaledValue, min.value).sp
    }
}

private val DefaultAppLayoutMetrics = AppLayoutMetrics(
    widthScale = 1f,
    heightScale = 1f,
    contentScale = 1f,
)

/**
 * Backing CompositionLocal for [AppLayout.metrics]. Using `staticCompositionLocalOf` instead of a
 * top-level `var` makes theme/rotation changes trigger a proper recomposition of every reader
 * (e.g. LazyColumn rows) instead of leaving them painted with stale sizes/colors.
 */
private val LocalAppLayoutMetrics = staticCompositionLocalOf { DefaultAppLayoutMetrics }

object AppLayout {
    val metrics: AppLayoutMetrics
        @Composable get() = LocalAppLayoutMetrics.current
}

@Composable
fun rememberAppLayoutMetrics(
    maxWidth: Dp,
    maxHeight: Dp,
): AppLayoutMetrics {
    return remember(maxWidth, maxHeight) {
        val widthScale = maxWidth.value / BaseWidth
        val heightScale = maxHeight.value / BaseHeight
        AppLayoutMetrics(
            widthScale = widthScale,
            heightScale = heightScale,
            // Cap growth so landscape/tablet windows don't distort content proportions or let
            // components overgrow far beyond their phone-sized design intent.
            contentScale = minOf(widthScale, heightScale).coerceAtMost(1.15f),
        )
    }
}

@Composable
fun ProvideAppLayoutMetrics(
    metrics: AppLayoutMetrics,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAppLayoutMetrics provides metrics) {
        content()
    }
}
