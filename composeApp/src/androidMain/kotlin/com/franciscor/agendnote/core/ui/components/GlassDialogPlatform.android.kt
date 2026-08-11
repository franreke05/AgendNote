package com.franciscor.agendnote.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Android's `Dialog` composable renders inside a real platform `android.view.Window`, and that
 * window's theme applies its own background dim (`windowIsFloating`'s default) independently of
 * whatever Compose content is drawn inside it - forcing it to 0 here so [GlassScrimLayer] is the
 * only source of darkening. `view.parent` is a [DialogWindowProvider] for exactly this content
 * (Compose's own `AndroidDialog_androidKt` sets it up that way) - null-safe in case that internal
 * wiring ever changes, in which case this silently becomes a no-op instead of crashing.
 */
@Composable
actual fun DisableDialogPlatformDim() {
    val view = LocalView.current
    SideEffect {
        (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
    }
}
