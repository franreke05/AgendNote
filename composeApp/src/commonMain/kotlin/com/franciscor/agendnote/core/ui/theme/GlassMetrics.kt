package com.franciscor.agendnote.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.franciscor.agendnote.core.ui.layout.AppLayout

/**
 * Named corner-radius scale for the Glass design system (Operación Aniversario, 2026-08-09).
 * Values match what Glass* components already used ad hoc (GlassTextField/GlassActionButton/
 * GlassSearchBar/GlassSnackbar at [s], GlassConfirmDialog's card at [l]) - this only names the
 * scale so new components pick a level instead of inventing a new literal. Do not add a value
 * here that doesn't already match an existing call site without checking every consumer first;
 * changing an existing entry changes real screens.
 */
object GlassRadius {
    @Composable fun xs(): Dp = AppLayout.metrics.size(12.dp, 10.dp)
    @Composable fun s(): Dp = AppLayout.metrics.size(18.dp, 16.dp)
    @Composable fun m(): Dp = AppLayout.metrics.size(24.dp, 20.dp)
    @Composable fun l(): Dp = AppLayout.metrics.size(28.dp, 24.dp)
}

/**
 * Named elevation levels for [GlassSurface.shadowElevation]. Plain [Dp], not a layout-scaled
 * pair: [GlassSurface] already scales whatever it receives via `layout.size(shadowElevation,
 * 0.dp)` internally, so these are the pre-scale values only.
 *
 * Levels (see docs/OPERATION_ANNIVERSARY_STATUS.md for the full glass-level grammar):
 * - [fused]: surfaces meant to read as part of the background (inputs, search bar, list rows).
 * - [floating]: independent objects (icon buttons, primary CTAs, cards).
 * - [modal]: sheets/dialogs/confirmations, the topmost layer.
 */
object GlassElevation {
    val fused: Dp = 0.dp
    val floating: Dp = 8.dp
    val modal: Dp = 18.dp
}
