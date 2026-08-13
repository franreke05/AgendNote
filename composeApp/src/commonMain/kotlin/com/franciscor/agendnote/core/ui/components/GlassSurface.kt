package com.franciscor.agendnote.core.ui.components

import androidx.compose.foundation.LocalIndication
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.franciscor.agendnote.core.ui.layout.AppLayout
import com.franciscor.agendnote.core.ui.theme.GlassElevation
import com.franciscor.agendnote.core.ui.theme.GlassTheme

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    tint: Color = GlassTheme.tokens.glassFill,
    strokeColor: Color = GlassTheme.tokens.glassStroke,
    shadowElevation: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val layout = AppLayout.metrics
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
                elevation = layout.size(shadowElevation, 0.dp),
                shape = shape,
                clip = false,
                ambientColor = GlassTheme.tokens.shadowAmbient,
                spotColor = GlassTheme.tokens.shadowSpot,
            )
            .clip(shape)
            .background(tint)
            .border(layout.size(1.dp, 1.dp), strokeColor, shape)
            .drawWithContent {
                // REVIEW: the highlight belongs to the material, behind its children. Drawing it
                // after content washed out text/icons and flattened hierarchy across the app.
                drawRect(highlightBrush, alpha = 0.22f)
                drawContent()
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
    val layout = AppLayout.metrics
    val buttonSize = layout.size(50.dp, 48.dp)
    val iconSize = layout.size(24.dp, 20.dp)
    val indication = LocalIndication.current
    GlassSurface(
        modifier = modifier
            .size(buttonSize)
            .clickable(
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = indication,
                onClick = onClick,
            ),
        shape = CircleShape,
        tint = GlassTheme.tokens.glassFillStrong,
        strokeColor = GlassTheme.tokens.glassStroke,
        shadowElevation = GlassElevation.floating,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = GlassTheme.tokens.textPrimary,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
