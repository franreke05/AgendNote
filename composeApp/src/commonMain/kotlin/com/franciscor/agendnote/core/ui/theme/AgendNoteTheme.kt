package com.franciscor.agendnote.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
    val glassStroke: Color,
    val glassHighlight: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val shadowAmbient: Color,
    val shadowSpot: Color,
)

private val LightGlassTokens = GlassTokens(
    backgroundTop = Color(0xFFEAF1F8),
    backgroundBottom = Color(0xFFD6E4F2),
    glassFill = Color(0x66FFFFFF),
    glassFillStrong = Color(0x88FFFFFF),
    glassStroke = Color(0x80FFFFFF),
    glassHighlight = Color(0xCCFFFFFF),
    accent = Color(0xFFFF8A5B),
    textPrimary = Color(0xFF142230),
    textSecondary = Color(0xFF516173),
    divider = Color(0x33FFFFFF),
    shadowAmbient = Color(0x22000000),
    shadowSpot = Color(0x33000000),
)

private val DarkGlassTokens = GlassTokens(
    backgroundTop = Color(0xFF0E141B),
    backgroundBottom = Color(0xFF0B1118),
    glassFill = Color(0x1AFFFFFF),
    glassFillStrong = Color(0x2BFFFFFF),
    glassStroke = Color(0x3AFFFFFF),
    glassHighlight = Color(0x2FFFFFFF),
    accent = Color(0xFFFF8A5B),
    textPrimary = Color(0xFFF3F6FB),
    textSecondary = Color(0xFFB6C1CE),
    divider = Color(0x26FFFFFF),
    shadowAmbient = Color(0x66000000),
    shadowSpot = Color(0x88000000),
)

private var currentGlassTokens: GlassTokens = LightGlassTokens

object GlassTheme {
    val tokens: GlassTokens
        @Composable get() = currentGlassTokens
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
        labelMedium = TextStyle(
            fontFamily = manrope,
            fontWeight = FontWeight.SemiBold,
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

    currentGlassTokens = tokens
    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content,
    )
}
