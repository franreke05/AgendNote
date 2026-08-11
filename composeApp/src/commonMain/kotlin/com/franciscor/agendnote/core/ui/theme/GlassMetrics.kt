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

/**
 * Named spacing scale (Operación Aniversario, "P0 VISUAL" fix, 2026-08-09/11). Before this,
 * padding/gap values across the app were ad-hoc dp literals chosen per call site (13.dp, 17.dp,
 * 21.dp, 9.dp with no relationship to each other) - this names the handful of gaps that already
 * recur constantly so new code picks a level instead of inventing another one-off number. Not a
 * retroactive migration of every existing literal (a large, unverifiable-without-screenshots
 * change on its own) - new/touched components should prefer these; existing call sites migrate
 * opportunistically when actually touched for another reason.
 */
object Spacing {
    @Composable fun xs(): Dp = AppLayout.metrics.size(6.dp, 5.dp)
    @Composable fun s(): Dp = AppLayout.metrics.size(10.dp, 8.dp)
    @Composable fun m(): Dp = AppLayout.metrics.size(16.dp, 14.dp)
    @Composable fun l(): Dp = AppLayout.metrics.size(20.dp, 16.dp)
    @Composable fun xl(): Dp = AppLayout.metrics.size(28.dp, 24.dp)
}

/**
 * Named minimum touch-target heights (Operación Aniversario, "P0 VISUAL" fix, 2026-08-11) -
 * fixes real drift found across button call sites (Añadir 52dp, Cancelar 43dp, Cambiar 38dp,
 * Guardar 47dp, no shared reason for any of those specific numbers). [standard] is the default
 * for anything a thumb taps - never go below it without a specific reason (e.g. a dense inline
 * chip where the whole row, not each chip, is the practical touch target).
 */
object ControlHeight {
    @Composable fun small(): Dp = AppLayout.metrics.size(40.dp, 36.dp)
    @Composable fun standard(): Dp = AppLayout.metrics.size(48.dp, 44.dp)
    @Composable fun large(): Dp = AppLayout.metrics.size(56.dp, 52.dp)
}
