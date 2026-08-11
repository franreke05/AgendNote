package com.franciscor.agendnote.core.platform

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

/** `Platform.isDebugBinary` reflects whether Kotlin/Native compiled this binary in debug mode -
 * true for Xcode's Debug configuration/scheme, false for Release/TestFlight/App Store builds. */
@OptIn(ExperimentalNativeApi::class)
actual val isDebugBuild: Boolean = Platform.isDebugBinary
