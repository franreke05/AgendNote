package com.franciscor.agendnote.core.ui.motion

/**
 * Duration for [com.franciscor.agendnote.core.ui.components.GlassBackground]'s custom-image
 * cross-fade. Kept as a pure function (instead of inlining the branch at the call site) so the
 * reduced-motion behavior is testable without a Compose runtime.
 */
fun glassImageFadeDurationMillis(reduceMotion: Boolean): Int = if (reduceMotion) 0 else 700
