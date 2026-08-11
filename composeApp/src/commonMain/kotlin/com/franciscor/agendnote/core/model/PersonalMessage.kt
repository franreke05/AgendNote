package com.franciscor.agendnote.core.model

import androidx.compose.runtime.Immutable
import com.franciscor.agendnote.core.notifications.NotificationSoundId
import com.franciscor.agendnote.core.notifications.VoiceMessageId
import kotlinx.datetime.Instant

/**
 * A single scheduled personal message (Operación Aniversario, "Sprint Final" directive, item 7).
 * Deliberately minimal per the directive's own explicit boundary: no sender, no thread, no
 * replies, no likes, no multi-user sync target beyond what already exists (this app has exactly
 * one user) - this is a one-way scheduled note-to-self-from-the-author, not a chat feature.
 *
 * Persisted client-side as JSON under a single `api-settings` key
 * (`SettingsRepository.fetchPersonalMessages`/`savePersonalMessages`) - same "no new backend
 * table" pattern [TaskTemplate] already established, not a new Supabase migration.
 *
 * @param notificationSoundId Almost always [NotificationSoundId.PERSONAL_MESSAGE] in practice -
 * kept as a real field (not hardcoded at the call site) so a future message type isn't blocked
 * on a schema change.
 * @param voiceMessageId If set, [PersonalMessageDetailOverlay] shows the long-audio player for
 * this id; if null, the message is text-only.
 */
@Immutable
data class PersonalMessage(
    val id: String,
    val title: String?,
    val body: String,
    val scheduledAt: Instant,
    val notificationSoundId: NotificationSoundId? = NotificationSoundId.PERSONAL_MESSAGE,
    val voiceMessageId: VoiceMessageId? = null,
    val seen: Boolean = false,
)
