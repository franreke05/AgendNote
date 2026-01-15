package com.franciscor.agendnote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.franciscor.agendnote.ui.theme.GlassTheme

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    tint: Color = GlassTheme.tokens.glassFill,
    strokeColor: Color = GlassTheme.tokens.glassStroke,
    shadowElevation: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val highlightBrush = Brush.linearGradient(
        colors = listOf(
            GlassTheme.tokens.glassHighlight.copy(alpha = 0.6f),
            Color.Transparent,
        ),
        start = Offset.Zero,
        end = Offset(420f, 420f),
    )

    Box(
        modifier = modifier
            .shadow(
                elevation = shadowElevation,
                shape = shape,
                clip = false,
                ambientColor = GlassTheme.tokens.shadowAmbient,
                spotColor = GlassTheme.tokens.shadowSpot,
            )
            .clip(shape)
            .background(tint)
            .border(1.dp, strokeColor, shape)
            .drawWithContent {
                drawContent()
                drawRect(highlightBrush, alpha = 0.22f)
            },
        content = content,
    )
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    GlassSurface(
        modifier = modifier
            .size(40.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        shape = CircleShape,
        tint = GlassTheme.tokens.glassFillStrong,
        strokeColor = GlassTheme.tokens.glassStroke,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = GlassTheme.tokens.textPrimary,
            )
        }
    }
}
