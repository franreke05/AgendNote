package com.franciscor.agendnote.core.notifications

import platform.Foundation.NSBundle

/**
 * The one place `NotificationSoundId`/`VoiceMessageId` -> actual bundled filename lives on iOS
 * (Operación Aniversario, "Sprint Final" directive item 2: "el dominio guarda IDs, la plataforma
 * resuelve IDs -> assets"). Filenames come directly from the directive - do not rename.
 *
 * All 8 files are expected as plain WAV at the bundle root (Xcode "Copy Bundle Resources" - see
 * `supabase docs, docs/OPERATION_ANNIVERSARY_STATUS.md` for the exact manual steps this
 * environment could not perform itself: no Xcode/macOS available here to add them to
 * `project.pbxproj`, and the files themselves are not in the repo yet as of this pass).
 */
internal object IosSoundAssets {
    private const val EXTENSION = "wav"

    private val notificationSoundFilenames = mapOf(
        NotificationSoundId.REMINDER_GENERAL to "voice_reminder_general",
        NotificationSoundId.REMINDER_NOW to "voice_reminder_now",
        NotificationSoundId.MORNING to "voice_morning",
        NotificationSoundId.DEADLINE to "voice_deadline",
        NotificationSoundId.PERSONAL_MESSAGE to "voice_personal_message",
    )

    private val voiceMessageFilenames = mapOf(
        VoiceMessageId.ANNIVERSARY to "message_anniversary",
        VoiceMessageId.ENCOURAGEMENT to "message_encouragement",
        VoiceMessageId.ALWAYS to "message_always",
    )

    /**
     * The bundle filename (without extension) for a notification sound, or `null` if it isn't
     * actually present in the app bundle right now - callers must fall back to
     * `UNNotificationSound.defaultSound()` rather than pass a name `UNNotificationSound` cannot
     * resolve (per Apple's docs, an unresolvable custom sound name silently falls back to the
     * default system sound anyway, but resolving it explicitly here means the fallback is a
     * deliberate, logged decision instead of an invisible one).
     */
    fun resolveNotificationSoundFilename(id: NotificationSoundId): String? {
        val name = notificationSoundFilenames[id] ?: return null
        return name.takeIf { bundleHasResource(it) }
    }

    /** The full playable file path for a long voice message, or `null` if it's not bundled. */
    fun resolveVoiceMessagePath(id: VoiceMessageId): String? {
        val name = voiceMessageFilenames[id] ?: return null
        return NSBundle.mainBundle.pathForResource(name, EXTENSION)
    }

    private fun bundleHasResource(name: String): Boolean {
        return NSBundle.mainBundle.pathForResource(name, EXTENSION) != null
    }
}
