package com.franciscor.agendnote.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.franciscor.agendnote.core.ui.layout.AppLayout
import com.franciscor.agendnote.core.ui.theme.GlassElevation
import com.franciscor.agendnote.core.ui.theme.GlassRadius
import com.franciscor.agendnote.core.ui.theme.GlassTheme
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Shared chrome for every transitory presentation in AgendNote (Operación Aniversario, "pasada
 * completa de UI/popups iOS Glass" - see docs/OPERATION_ANNIVERSARY_STATUS.md). Before this file,
 * every sheet/popover/alert built its own `Dialog` + scrim + `GlassSurface` combination by hand,
 * with slightly different corner shapes, scrim alpha and dismiss behavior each time - this is the
 * "Design System Freeze V1" foundation everything else migrates onto.
 *
 * DESIGN SYSTEM FREEZE V1: this file is the single writer for shared presentation chrome. Feature
 * code should consume [GlassSheetScaffold]/[GlassPopover]/[GlassScrim]/[GlassGrabber], not
 * hand-roll a new `Dialog` + scrim combination.
 */

/**
 * Scrim opacity by presentation weight, all derived from the single [GlassTheme.tokens.scrim]
 * color so light/dark mode stays consistent - never a new hardcoded color per popup. Ordered
 * Alert > Sheet > Popover, per the product brief: an alert is the most disruptive interruption
 * (needs the strongest separation from context), a popover the lightest (a quick contextual
 * choice that should still feel anchored to the screen behind it).
 */
object GlassScrim {
    val popover: Color
        @Composable get() = GlassTheme.tokens.scrim.let { it.copy(alpha = it.alpha * 0.6f) }
    val sheet: Color
        @Composable get() = GlassTheme.tokens.scrim
    val alert: Color
        @Composable get() = GlassTheme.tokens.scrim.let { it.copy(alpha = (it.alpha * 1.3f).coerceAtMost(1f)) }
}

/** The tap-outside-to-dismiss backdrop layer shared by every presentation below. */
@Composable
fun GlassScrimLayer(
    color: Color,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(color)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    )
}

/**
 * Whether any Glass sheet/popover/alert is currently on screen - tracked centrally by
 * [GlassDialogHost] so callers elsewhere in the app (chiefly `AppNavHost`'s tab-swipe gesture,
 * see item 17 of the "P0 VISUAL" fix, 2026-08-11: "mientras un Sheet/Popover/Alert esté abierto,
 * DISABLE MAIN SCREEN HORIZONTAL SWIPE") don't need every individual screen to separately report
 * its own overlay-visible booleans upward. Cheap to add correctly today specifically because
 * every presentation already funnels through one host after this same fix's consolidation - a
 * screen with its own hand-rolled `Dialog` would not be tracked here.
 */
object GlassModalState {
    private val openCount = mutableIntStateOf(0)
    private val backdropBlurCount = mutableIntStateOf(0)
    val isAnyModalOpen: Boolean
        @Composable get() = openCount.intValue > 0
    val isBackdropBlurred: Boolean
        @Composable get() = backdropBlurCount.intValue > 0
    internal fun increment(blurBehind: Boolean) {
        openCount.intValue += 1
        if (blurBehind) backdropBlurCount.intValue += 1
    }
    internal fun decrement(blurBehind: Boolean) {
        openCount.intValue = (openCount.intValue - 1).coerceAtLeast(0)
        if (blurBehind) backdropBlurCount.intValue = (backdropBlurCount.intValue - 1).coerceAtLeast(0)
    }
}

/**
 * Single mount point for every Glass presentation's `Dialog` + scrim, replacing what used to be
 * three near-duplicate copies ([GlassSheetScaffold], [GlassPopover], and - before this fix -
 * [GlassConfirmDialog], which had no scrim of its own at all and relied entirely on whatever the
 * platform's default `Dialog` dim happened to look like). Fixes two root causes found by
 * inspecting real screenshots (Operación Aniversario, "P0 VISUAL", 2026-08-11), not just the
 * symptom on one screen:
 *
 * 1. **Double scrim**: a platform `Dialog` window dims its own background by default, on top of
 *    whichever [GlassScrimLayer] a presentation drew inside it - two stacked dark layers with
 *    different colors/alphas read as a harsh, mismatched "rectangle" instead of one deliberate
 *    dim. [DisableDialogPlatformDim] cancels the platform layer so [GlassScrim] is the only one.
 * 2. **Scrim not covering the real window**: [GlassScrimLayer] used to live inside the same
 *    `Modifier.fillMaxSize().safeContentPadding()` container as the modal surface itself, so the
 *    scrim was *also* inset by safe-area padding - on a device with system bars/notch, that left
 *    a visible unscrimmed strip and a "rectangle edge" exactly where the padding stopped. The
 *    scrim here fills the **unpadded** window; only the surface content callers place inside
 *    [content] should opt into safe-area padding for its own positioning.
 */
@Composable
internal fun GlassDialogHost(
    onDismiss: () -> Unit,
    scrimColor: Color,
    blurBehind: Boolean = false,
    content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    DisposableEffect(blurBehind) {
        GlassModalState.increment(blurBehind)
        onDispose { GlassModalState.decrement(blurBehind) }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        DisableDialogPlatformDim()
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            GlassScrimLayer(color = scrimColor, onDismiss = onDismiss)
            content()
        }
    }
}

/** The small drag handle at the top of a bottom sheet - the universal "this can be swiped away"
 * affordance. Was duplicated inline in SmartListsOverlay before this file existed. */
@Composable
fun GlassGrabber(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 36.dp, height = 5.dp)
            .background(GlassTheme.tokens.glassStroke, RoundedCornerShape(50)),
    )
}

/**
 * Bottom-anchored Glass sheet: top corners only, edge-to-edge width, grabber, drag-to-dismiss +
 * tap-outside dismiss, scales its own max height to a fraction of the available screen so it
 * never fights [androidx.compose.foundation.layout.safeContentPadding]. Use for creation/edition/
 * detail/complex content (the product brief's SHEET category) - NewTaskSheet, TaskDetailsOverlay,
 * SmartListsOverlay.
 *
 * Does not itself scroll [content] - callers with content that can overflow (a long form, a long
 * list) must wrap their own content in a scrollable container, same as before this scaffold
 * existed. This scaffold only owns the outer chrome (Dialog/scrim/shape/grabber/drag), never the
 * internal layout of a specific sheet - each feature keeps full control of its own content.
 */
@Composable
fun GlassSheetScaffold(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    maxHeightFraction: Float = 0.9f,
    dragToDismissEnabled: Boolean = true,
    showGrabber: Boolean = true,
    blurBehind: Boolean = false,
    scrimColor: Color = GlassScrim.sheet,
    content: @Composable ColumnScope.() -> Unit,
) {
    val layout = AppLayout.metrics
    GlassDialogHost(
        onDismiss = onDismiss,
        scrimColor = scrimColor,
        blurBehind = blurBehind,
    ) {
        // Safe-area padding lives here, on the sheet's own positioning - NOT on the scrim drawn
        // by GlassDialogHost above, which must stay unpadded to cover the real window edge to
        // edge. maxHeight below still comes from the unpadded BoxWithConstraints (a few dp taller
        // than the safe area on a device with a home indicator/gesture bar) - heightIn(max=...)
        // only caps height, so this offset from the true safe space is harmless in practice.
        var dragOffset by remember { mutableStateOf(0f) }
        val density = LocalDensity.current
        val dismissThresholdPx = with(density) { layout.height(90.dp, 72.dp).toPx() }
        val dragModifier = if (dragToDismissEnabled) {
            Modifier.pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        dragOffset = (dragOffset + dragAmount).coerceAtLeast(0f)
                    },
                    onDragEnd = {
                        if (dragOffset > dismissThresholdPx) onDismiss() else dragOffset = 0f
                    },
                    onDragCancel = { dragOffset = 0f },
                )
            }
        } else {
            Modifier
        }

        GlassSurface(
            modifier = modifier
                .align(Alignment.BottomCenter)
                .safeContentPadding()
                .fillMaxWidth()
                .heightIn(max = maxHeight * maxHeightFraction)
                .offset { IntOffset(0, dragOffset.toInt().coerceAtLeast(0)) }
                .then(dragModifier),
            shape = RoundedCornerShape(
                topStart = GlassRadius.l(),
                topEnd = GlassRadius.l(),
                bottomStart = 0.dp,
                bottomEnd = 0.dp,
            ),
            tint = GlassTheme.tokens.modalFill,
            shadowElevation = GlassElevation.modal,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (showGrabber) {
                    GlassGrabber(
                        modifier = Modifier
                            .padding(top = layout.height(10.dp, 8.dp))
                            .align(Alignment.CenterHorizontally),
                    )
                }
                content()
            }
        }
    }
}

/**
 * Compact, size-to-content Glass presentation for brief contextual choices (the product brief's
 * POPOVER category) - month calendar, color swatch grid, date/time selection. Scrim is lighter
 * than [GlassSheetScaffold]'s (see [GlassScrim]) since a popover is a lighter interruption than a
 * sheet, and the surface is width-constrained instead of edge-to-edge so it doesn't read as a
 * full sheet.
 *
 * Honest limitation, not hidden: this is NOT a true anchored popover (no arrow/tail pointing at
 * the control that opened it, no measured-position-of-the-anchor logic) - that requires tracking
 * the anchor's on-screen coordinates via `onGloballyPositioned` and a positioned `Popup`, which is
 * a materially bigger feature than centralizing existing chrome and was not attempted under this
 * week's deadline. [alignment] lets a caller choose top/center/bottom placement as the closest
 * approximation available today.
 */
@Composable
fun GlassPopover(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    maxWidthFraction: Float = 0.9f,
    blurBehind: Boolean = false,
    scrimColor: Color = GlassScrim.popover,
    content: @Composable ColumnScope.() -> Unit,
) {
    val layout = AppLayout.metrics
    GlassDialogHost(
        onDismiss = onDismiss,
        scrimColor = scrimColor,
        blurBehind = blurBehind,
    ) {
        GlassSurface(
            modifier = modifier
                .align(alignment)
                .safeContentPadding()
                .padding(layout.width(20.dp, 16.dp))
                .widthIn(max = maxWidth * maxWidthFraction),
            shape = RoundedCornerShape(GlassRadius.l()),
            tint = GlassTheme.tokens.modalFill,
            shadowElevation = GlassElevation.modal,
        ) {
            Column(
                modifier = Modifier.padding(layout.size(16.dp, 14.dp)),
                content = content,
            )
        }
    }
}
