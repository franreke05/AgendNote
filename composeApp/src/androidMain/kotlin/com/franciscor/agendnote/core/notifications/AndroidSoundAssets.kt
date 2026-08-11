package com.franciscor.agendnote.core.notifications

import android.content.Context

/**
 * Android counterpart to iosMain's `IosSoundAssets` - same "domain holds IDs, platform resolves
 * assets" split (Operación Aniversario, "para pruebas implementalo tambien en android", 2026-08-11).
 * Resolves by name via `Resources.getIdentifier` (returns 0 if not found) rather than generated
 * `R.raw.*` references, so a missing asset degrades the same documented way as iOS - a null/0
 * result the caller falls back on, not a compile-time dependency on every asset existing.
 *
 * Filenames match the directive exactly (`voice_reminder_general`, `message_anniversary`, etc.)
 * with the `.wav`/format-specific extension stripped, since Android resource identifiers can't
 * contain dots - see docs/agendnote/IOS_AUDIO_ASSETS_SETUP.md for why the bundled files are
 * actually `.m4a` despite the directive naming them `.wav` (a real mismatch in what was
 * delivered, not a decision made here). `message_anniversary` specifically has no resource file
 * at all as of this pass - exercises the MISSING/fallback path for real, not just in theory.
 */
internal object AndroidSoundAssets {
    private val notificationSoundResourceNames = mapOf(
        NotificationSoundId.REMINDER_GENERAL to "voice_reminder_general",
        NotificationSoundId.REMINDER_NOW to "voice_reminder_now",
        NotificationSoundId.MORNING to "voice_morning",
        NotificationSoundId.DEADLINE to "voice_deadline",
        NotificationSoundId.PERSONAL_MESSAGE to "voice_personal_message",
    )

    private val voiceMessageResourceNames = mapOf(
        VoiceMessageId.ANNIVERSARY to "message_anniversary",
        VoiceMessageId.ENCOURAGEMENT to "message_encouragement",
        VoiceMessageId.ALWAYS to "message_always",
    )

    /** `0` if [id] has no bundled `res/raw` entry - never throws. */
    fun resolveNotificationSoundRawRes(context: Context, id: NotificationSoundId): Int {
        val name = notificationSoundResourceNames[id] ?: return 0
        return rawResourceId(context, name)
    }

    /** `0` if [id] has no bundled `res/raw` entry - never throws. */
    fun resolveVoiceMessageRawRes(context: Context, id: VoiceMessageId): Int {
        val name = voiceMessageResourceNames[id] ?: return 0
        return rawResourceId(context, name)
    }

    private fun rawResourceId(context: Context, name: String): Int {
        return context.resources.getIdentifier(name, "raw", context.packageName)
    }
}
