package com.franciscor.agendnote.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.Font

import agendnote.composeapp.generated.resources.Res
import agendnote.composeapp.generated.resources.manrope_regular
import agendnote.composeapp.generated.resources.manrope_semibold
import com.franciscor.agendnote.core.ui.layout.AppLayout

@Immutable
data class GlassTokens(
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val glassFill: Color,
    val glassFillStrong: Color,
    val modalFill: Color,
    val glassStroke: Color,
    val glassHighlight: Color,
    val accent: Color,
    /**
     * Darker accent variant that keeps the same hue but meets WCAG AA (>=4.5:1) contrast when
     * white/light text sits on top of it (e.g. [GlassActionButton]'s filled background).
     * Do not use this in place of [accent] for contexts where [accent] already has adequate
     * contrast (icons, borders, accent text on light glass surfaces, etc).
     */
    val accentOnLight: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val shadowAmbient: Color,
    val shadowSpot: Color,
    val error: Color,
    val errorContent: Color,
    val onError: Color,
    val success: Color,
    val onSuccess: Color,
    val scrim: Color,
    /**
     * Border color for a focused input on a Glass surface (e.g. [GlassTextField]). Kept as its
     * own token (rather than reusing [accentOnLight] inline at every call site) so a future pass
     * can retune focus contrast independently of the accent color used elsewhere.
     */
    val focusStroke: Color,
    /** Container tint for a disabled Glass surface - roughly half the alpha of [glassFill]. */
    val glassFillDisabled: Color,
    /** Text color on a disabled Glass surface - [textSecondary] at ~0.45 alpha. */
    val textDisabled: Color,
)

private val LightGlassTokens = GlassTokens(
    backgroundTop = Color(0xFFEAF1F8),
    backgroundBottom = Color(0xFFD6E4F2),
    glassFill = Color(0x66FFFFFF),
    glassFillStrong = Color(0x88FFFFFF),
    modalFill = Color(0xFFF4F8FC),
    glassStroke = Color(0x80FFFFFF),
    glassHighlight = Color(0xCCFFFFFF),
    accent = Color(0xFFFF8A5B),
    accentOnLight = Color(0xFFB34A1E),
    textPrimary = Color(0xFF142230),
    textSecondary = Color(0xFF516173),
    divider = Color(0x33FFFFFF),
    shadowAmbient = Color(0x22000000),
    shadowSpot = Color(0x33000000),
    error = Color(0xFFB53B3B),
    errorContent = Color(0xFFB53B3B),
    onError = Color.White,
    success = Color(0xFF43B87B),
    onSuccess = Color.White,
    scrim = Color(0x59000000),
    focusStroke = Color(0xFFB34A1E),
    glassFillDisabled = Color(0x33FFFFFF),
    textDisabled = Color(0x73516173),
)

private val DarkGlassTokens = GlassTokens(
    backgroundTop = Color(0xFF0E141B),
    backgroundBottom = Color(0xFF0B1118),
    glassFill = Color(0x1AFFFFFF),
    glassFillStrong = Color(0x2BFFFFFF),
    modalFill = Color(0xFF18222C),
    glassStroke = Color(0x3AFFFFFF),
    glassHighlight = Color(0x2FFFFFFF),
    accent = Color(0xFFFF8A5B),
    accentOnLight = Color(0xFFB34A1E),
    textPrimary = Color(0xFFF3F6FB),
    textSecondary = Color(0xFFB6C1CE),
    divider = Color(0x26FFFFFF),
    shadowAmbient = Color(0x66000000),
    shadowSpot = Color(0x88000000),
    error = Color(0xFFCC3333),
    errorContent = Color(0xFFFF9A9A),
    onError = Color.White,
    success = Color(0xFF43B87B),
    onSuccess = Color.White,
    scrim = Color(0x660B1117),
    focusStroke = Color(0xFFB34A1E),
    glassFillDisabled = Color(0x0DFFFFFF),
    textDisabled = Color(0x73B6C1CE),
)

/**
 * Backing CompositionLocal for [GlassTheme.tokens]. Using `staticCompositionLocalOf` instead of a
 * top-level `var` makes theme switches trigger a proper recomposition of every reader (e.g.
 * LazyColumn rows) instead of leaving them painted with stale colors.
 */
private val LocalGlassTokens = staticCompositionLocalOf { LightGlassTokens }

object GlassTheme {
    val tokens: GlassTokens
        @Composable get() = LocalGlassTokens.current
}

@OptIn(ExperimentalResourceApi::class)
@Composable
fun AgendNoteTheme(
    isDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val layout = AppLayout.metrics
    val tokens = if (isDark) DarkGlassTokens else LightGlassTokens
    val manrope = FontFamily(
        Font(Res.font.manrope_regular, FontWeight.Normal),
        Font(Res.font.manrope_semibold, FontWeight.SemiBold),
    )

    val typography = Typography(
        displayLarge = TextStyle(
            fontFamily = manrope,
            fontWeight = FontWeight.SemiBold,
            fontSize = layout.text(32.sp, 28.sp),
            letterSpacing = (-0.5f * layout.contentScale).sp,
        ),
        titleLarge = TextStyle(
            fontFamily = manrope,
            fontWeight = FontWeight.SemiBold,
            fontSize = layout.text(20.sp, 18.sp),
            letterSpacing = (-0.2f * layout.contentScale).sp,
        ),
        titleMedium = TextStyle(
            fontFamily = manrope,
            fontWeight = FontWeight.SemiBold,
            fontSize = layout.text(16.sp, 15.sp),
        ),
        titleSmall = TextStyle(
            fontFamily = manrope,
            fontWeight = FontWeight.SemiBold,
            fontSize = layout.text(14.sp, 13.sp),
        ),
        bodyLarge = TextStyle(
            fontFamily = manrope,
            fontWeight = FontWeight.Normal,
            fontSize = layout.text(15.sp, 14.sp),
        ),
        bodyMedium = TextStyle(
            fontFamily = manrope,
            fontWeight = FontWeight.Normal,
            fontSize = layout.text(13.sp, 12.sp),
        ),
        bodySmall = TextStyle(
            fontFamily = manrope,
            fontWeight = FontWeight.Normal,
            fontSize = layout.text(12.sp, 11.sp),
        ),
        labelMedium = TextStyle(
            fontFamily = manrope,
            fontWeight = FontWeight.SemiBold,
            fontSize = layout.text(12.sp, 11.sp),
            letterSpacing = (0.2f * layout.contentScale).sp,
        ),
        labelSmall = TextStyle(
            fontFamily = manrope,
            fontWeight = FontWeight.SemiBold,
            // REVIEW: navigation labels were effectively 9 sp on compact phones, below the
            // readable floor from the mobile UI guide.
            fontSize = layout.text(11.sp, 10.sp),
            letterSpacing = (0.2f * layout.contentScale).sp,
        ),
    )

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = tokens.accent,
            onPrimary = Color.White,
            background = tokens.backgroundBottom,
            onBackground = tokens.textPrimary,
            surface = tokens.glassFill,
            onSurface = tokens.textPrimary,
        )
    } else {
        lightColorScheme(
            primary = tokens.accent,
            onPrimary = Color.White,
            background = tokens.backgroundBottom,
            onBackground = tokens.textPrimary,
            surface = tokens.glassFill,
            onSurface = tokens.textPrimary,
        )
    }

    CompositionLocalProvider(LocalGlassTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content,
        )
    }
}
