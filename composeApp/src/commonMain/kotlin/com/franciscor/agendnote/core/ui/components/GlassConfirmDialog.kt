package com.franciscor.agendnote.core.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.franciscor.agendnote.core.ui.layout.AppLayout
import com.franciscor.agendnote.core.ui.theme.GlassTheme

/**
 * Generic glass-styled confirmation dialog: a full-screen dismiss-on-tap scrim behind a centered
 * card with a title, a message and cancel/confirm buttons.
 *
 * This replaces three near-identical duplicated implementations that used to live separately in
 * `app/navigation` (as `ConfirmBulkDialog`), `feature/agenda` and `feature/settings`
 * (`SettingsConfirmDialog`). Callers in those features should migrate to this component instead
 * of keeping their own copy.
 *
 * @param confirmTint background tint for the confirm button. Defaults to [GlassTokens.error],
 * appropriate for destructive actions (delete/bulk-delete); pass a different tint for
 * non-destructive confirmations.
 * @param confirmTextColor text color for the confirm button. Defaults to [GlassTokens.onError].
 */
@Composable
fun GlassConfirmDialog(
    visible: Boolean,
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "Confirmar",
    cancelText: String = "Cancelar",
    confirmTint: Color = GlassTheme.tokens.error,
    confirmTextColor: Color = GlassTheme.tokens.onError,
) {
    if (!visible) return

    val layout = AppLayout.metrics
    val indication = LocalIndication.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = indication,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            modifier = Modifier
                .padding(horizontal = layout.width(24.dp, 20.dp))
                .fillMaxWidth(),
            shape = RoundedCornerShape(layout.size(28.dp, 24.dp)),
            tint = GlassTheme.tokens.glassFillStrong,
        ) {
            Column(
                modifier = Modifier.padding(layout.size(20.dp, 16.dp)),
                verticalArrangement = Arrangement.spacedBy(layout.height(14.dp, 12.dp)),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = GlassTheme.tokens.textPrimary,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassTheme.tokens.textSecondary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(layout.width(10.dp, 8.dp)),
                ) {
                    GlassConfirmDialogButton(
                        text = cancelText,
                        tint = GlassTheme.tokens.glassFill,
                        textColor = GlassTheme.tokens.textPrimary,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    GlassConfirmDialogButton(
                        text = confirmText,
                        tint = confirmTint,
                        textColor = confirmTextColor,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassConfirmDialogButton(
    text: String,
    tint: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = AppLayout.metrics
    val indication = LocalIndication.current
    GlassSurface(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = indication,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(layout.size(16.dp, 16.dp)),
        tint = tint,
        strokeColor = tint.copy(alpha = 0.5f),
    ) {
        Box(
            modifier = Modifier.padding(vertical = layout.height(10.dp, 10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
