package com.franciscor.agendnote.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.franciscor.agendnote.core.ui.layout.AppLayout
import com.franciscor.agendnote.core.ui.theme.GlassTheme

/**
 * A single-line confirmation with an optional action ("Deshacer", "Reintentar"...), styled as an
 * opaque Glass surface so the message stays legible over [GlassBackground] instead of competing
 * with it like transparent surfaces do. Callers own visibility/auto-dismiss timing (see
 * `AgendaScreen`'s use for undoing a task completion) - this composable only renders one snack
 * bar; it does not queue multiple messages.
 */
@Composable
fun GlassSnackbar(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val layout = AppLayout.metrics
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(layout.size(18.dp, 16.dp)),
        tint = GlassTheme.tokens.modalFill,
        strokeColor = GlassTheme.tokens.glassStroke,
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = layout.width(16.dp, 14.dp),
                    end = layout.width(8.dp, 6.dp),
                    top = layout.height(4.dp, 4.dp),
                    bottom = layout.height(4.dp, 4.dp),
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = GlassTheme.tokens.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (actionLabel != null && onAction != null) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(
                            minWidth = layout.size(48.dp, 48.dp),
                            minHeight = layout.size(48.dp, 48.dp),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onAction,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = GlassTheme.tokens.accentOnLight,
                    )
                }
            }
        }
    }
}
