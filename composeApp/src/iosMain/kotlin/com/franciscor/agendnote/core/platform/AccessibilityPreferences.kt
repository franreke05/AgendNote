package com.franciscor.agendnote.core.platform

import androidx.compose.runtime.Composable
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIAccessibilityIsReduceTransparencyEnabled

// NOTE: one-shot reads, not live-observed like the androidMain actual (which reacts to the
// setting changing mid-session via a ContentObserver). Live-observing these on iOS would need
// NSNotificationCenter interop (UIAccessibilityReduceMotionStatusDidChangeNotification /
// UIAccessibilityReduceTransparencyStatusDidChangeNotification) that could not be compiled or
// verified in this environment - there is no macOS/Xcode available here. A user who toggles
// either setting mid-session sees the new value next time this composable recomposes for another
// reason (e.g. leaving and returning to the screen), not instantly. Revisit once this can be
// built and tested on a Mac.

@Composable
actual fun rememberReduceMotionEnabled(): Boolean = UIAccessibilityIsReduceMotionEnabled()

@Composable
actual fun rememberReduceTransparencyEnabled(): Boolean = UIAccessibilityIsReduceTransparencyEnabled()
