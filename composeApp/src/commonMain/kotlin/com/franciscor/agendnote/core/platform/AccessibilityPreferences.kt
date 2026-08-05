package com.franciscor.agendnote.core.platform

import androidx.compose.runtime.Composable

/**
 * Live OS-level "reduce motion" accessibility preference (Android's "Remove animations"
 * developer/accessibility setting, iOS's "Reduce Motion"). Re-composes if the user toggles it
 * while the app is in the foreground, so screens should read this instead of caching a value.
 */
@Composable
expect fun rememberReduceMotionEnabled(): Boolean

/**
 * Live OS-level "reduce transparency" accessibility preference. Android has no direct public
 * equivalent (see the `androidMain` actual for the rationale), so this always returns `false`
 * there today.
 */
@Composable
expect fun rememberReduceTransparencyEnabled(): Boolean
