package com.franciscor.agendnote.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.franciscor.agendnote.core.ui.layout.AppLayout
import com.franciscor.agendnote.core.ui.theme.GlassTheme

/**
 * Selectable color circle used by color pickers (label creation, task color picker, ...).
 * Shows a highlighted border when [selected].
 */
@Composable
fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = AppLayout.metrics.size(26.dp, 22.dp),
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .border(
                width = AppLayout.metrics.size(2.dp, 1.dp),
                color = if (selected) GlassTheme.tokens.glassHighlight else Color.Transparent,
                shape = CircleShape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    )
}
