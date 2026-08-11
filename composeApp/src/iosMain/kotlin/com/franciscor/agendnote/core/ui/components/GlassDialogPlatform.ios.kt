package com.franciscor.agendnote.core.ui.components

import androidx.compose.runtime.Composable

// No-op on iOS: Compose Multiplatform's iOS `Dialog` implementation does not present through a
// themed platform window the way Android's does (there is no equivalent "windowIsFloating" dim
// applied underneath Compose's own content), so there is no known second scrim to cancel out
// here. Not independently verified against a real dialog on-device - there is no macOS/Xcode
// available in this environment. If a future build on real iOS hardware shows a similar double-
// dim artifact behind sheets/popovers, implement the equivalent fix here first before assuming
// the shared GlassScrim math is wrong.
@Composable
actual fun DisableDialogPlatformDim() {
}
