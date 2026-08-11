package com.franciscor.agendnote.feature.personal.presentation.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.franciscor.agendnote.core.audio.AudioPlayer
import com.franciscor.agendnote.core.audio.AudioPlayerState
import com.franciscor.agendnote.core.model.PersonalMessage
import com.franciscor.agendnote.core.ui.components.GlassButton
import com.franciscor.agendnote.core.ui.components.GlassIconButton
import com.franciscor.agendnote.core.ui.components.GlassSheetScaffold
import com.franciscor.agendnote.core.ui.layout.AppLayout
import com.franciscor.agendnote.core.ui.theme.GlassTheme
import com.franciscor.agendnote.core.ui.theme.Spacing

/**
 * The screen a personal-message notification opens (Operación Aniversario, "Sprint Final"
 * directive, item 8: "NO ABRAS SOLO AGENDA... debe abrir PersonalMessageDetail(messageId)").
 * Mounted at the `AppNavHost` level (not scoped to any tab) so it can open regardless of which
 * tab was active when the notification was tapped - see AppNavHost's `openPersonalMessageId`.
 *
 * Long-audio playback ([PersonalMessage.voiceMessageId]) uses [AudioPlayer] directly rather than
 * a shared player instance - only ever one of these overlays is on screen at a time (it's a
 * single Dialog-based sheet), so there is no coordination problem to solve.
 */
@Composable
fun PersonalMessageDetailOverlay(
    message: PersonalMessage,
    onDismiss: () -> Unit,
) {
    val layout = AppLayout.metrics
    val player = remember(message.id) { AudioPlayer() }
    val playerState by player.state.collectAsState()

    DisposableEffect(message.id) {
        onDispose { player.release() }
    }

    GlassSheetScaffold(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.l()),
            verticalArrangement = Arrangement.spacedBy(Spacing.m()),
        ) {
            Text(
                text = message.title ?: "Mensaje para ti",
                style = MaterialTheme.typography.titleLarge,
                color = GlassTheme.tokens.textPrimary,
            )
            Text(
                text = message.body,
                style = MaterialTheme.typography.bodyLarge,
                color = GlassTheme.tokens.textSecondary,
            )

            // Directive item 11: a missing long-audio asset hides the player entirely - the text
            // above stays fully visible either way. An ERROR (asset found but failed to load) is
            // the one state worth a short explicit message; MISSING/IDLE/anything else before the
            // user presses Play show no player-specific text at all.
            if (message.voiceMessageId != null && playerState != AudioPlayerState.MISSING) {
                if (playerState == AudioPlayerState.ERROR) {
                    Text(
                        text = "No se pudo reproducir este audio.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTheme.tokens.errorContent,
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlassIconButton(
                            icon = if (playerState == AudioPlayerState.PLAYING) {
                                Icons.Rounded.Pause
                            } else {
                                Icons.Rounded.PlayArrow
                            },
                            contentDescription = if (playerState == AudioPlayerState.PLAYING) {
                                "Pausar"
                            } else {
                                "Reproducir"
                            },
                            onClick = {
                                if (playerState == AudioPlayerState.PLAYING) {
                                    player.pause()
                                } else {
                                    player.play(message.voiceMessageId)
                                }
                            },
                        )
                        GlassIconButton(
                            icon = Icons.Rounded.Stop,
                            contentDescription = "Detener",
                            onClick = { player.stop() },
                        )
                        Text(
                            text = when (playerState) {
                                AudioPlayerState.LOADING -> "Cargando..."
                                AudioPlayerState.PLAYING -> "Reproduciendo"
                                AudioPlayerState.PAUSED -> "En pausa"
                                else -> "Audio"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = GlassTheme.tokens.textSecondary,
                        )
                    }
                }
            }

            GlassButton.Secondary(
                text = "Cerrar",
                modifier = Modifier.fillMaxWidth(),
                onClick = onDismiss,
            )
        }
    }
}
