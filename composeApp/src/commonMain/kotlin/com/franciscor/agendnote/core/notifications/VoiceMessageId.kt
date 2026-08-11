package com.franciscor.agendnote.core.notifications

/**
 * Typed contract for a long-form voice message played back inside AgendNote (not a notification
 * sound - see [NotificationSoundId] for those). Same "domain holds IDs, platform resolves assets"
 * split - [com.franciscor.agendnote.core.model.PersonalMessage.voiceMessageId] stores one of
 * these, never a filename.
 */
enum class VoiceMessageId {
    ANNIVERSARY,
    ENCOURAGEMENT,
    ALWAYS,
}
