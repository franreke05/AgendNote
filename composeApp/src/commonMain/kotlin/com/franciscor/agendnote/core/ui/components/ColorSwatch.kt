package com.franciscor.agendnote.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = AppLayout.metrics.size(26.dp, 22.dp),
) {
    val darkCheck = Color(0xFF142230)
    val colorLuminance = color.luminance()
    val whiteContrast = 1.05f / (colorLuminance + 0.05f)
    val darkContrast = (colorLuminance + 0.05f) / (darkCheck.luminance() + 0.05f)
    val checkColor = if (darkContrast >= whiteContrast) darkCheck else Color.White

    Box(
        modifier = modifier
            // REVIEW: preserve the compact visual dot but expose an accessible touch target.
            .size(AppLayout.metrics.size(48.dp, 48.dp))
            .clip(CircleShape)
            .semantics { this.contentDescription = contentDescription }
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = AppLayout.metrics.size(2.dp, 1.dp),
                    color = if (selected) GlassTheme.tokens.glassHighlight else Color.Transparent,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = checkColor,
                    modifier = Modifier.size(AppLayout.metrics.size(16.dp, 14.dp)),
                )
            }
        }
    }
}
