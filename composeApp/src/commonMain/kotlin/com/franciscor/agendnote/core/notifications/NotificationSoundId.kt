package com.franciscor.agendnote.core.notifications

/**
 * Typed contract for a short notification sound (Operación Aniversario, "Sprint Final: iOS
 * notificaciones + audios", 2026-08-11). The domain/UI layer picks an ID; only the platform layer
 * (`iosMain`'s `IosSoundAssets`) knows the actual bundled filename, so a filename can change or
 * gain platform-specific variants without touching anything above this.
 *
 * Not user-configurable per task today - see [resolveTaskReminderSoundId] for how a task's
 * reminder picks one of [REMINDER_GENERAL]/[REMINDER_NOW]/[DEADLINE] automatically. [MORNING] is
 * defined here (the product's audio set includes `voice_morning.wav`) but has no trigger point
 * yet - there is no "daily morning summary" notification in the app today. Reserved, not wired,
 * documented rather than silently ignored.
 */
enum class NotificationSoundId {
    REMINDER_GENERAL,
    REMINDER_NOW,
    MORNING,
    DEADLINE,
    PERSONAL_MESSAGE,
}
