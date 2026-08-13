package com.franciscor.agendnote.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.franciscor.agendnote.core.ui.layout.AppLayout
import com.franciscor.agendnote.core.ui.theme.GlassTheme

/**
 * Icon + title + optional subtitle, centered - the empty-state pattern shared by Agenda's
 * "sin tareas" day and Etiquetas' "sin etiquetas creadas" list. Callers own the outer container
 * (a bare centered `Box` for Agenda, a `GlassSurface` card for Etiquetas) since that differs by
 * screen; this composable only owns the part that was previously duplicated verbatim.
 */
@Composable
fun GlassEmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val layout = AppLayout.metrics
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(layout.height(10.dp, 8.dp)),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = GlassTheme.tokens.textSecondary.copy(alpha = 0.58f),
            modifier = Modifier.size(layout.size(72.dp, 60.dp)),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = GlassTheme.tokens.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = GlassTheme.tokens.textSecondary.copy(alpha = 0.82f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
