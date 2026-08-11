package com.franciscor.agendnote.feature.settings.data

import com.franciscor.agendnote.core.model.PersonalMessage
import com.franciscor.agendnote.core.model.TaskTemplate
import com.franciscor.agendnote.core.network.AgendaApiClient
import com.franciscor.agendnote.core.notifications.NotificationSoundId
import com.franciscor.agendnote.core.notifications.VoiceMessageId
import com.franciscor.agendnote.feature.settings.domain.SettingsRepository
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TASK_TEMPLATES_KEY = "task_templates"
private const val PERSONAL_MESSAGES_KEY = "personal_messages"
private val templatesJson = Json { ignoreUnknownKeys = true }

/**
 * JSON-on-the-wire shape for [PersonalMessage]. Kept separate from the domain type (which uses
 * [Instant] and the typed [NotificationSoundId]/[VoiceMessageId] enums directly) so persistence
 * doesn't depend on kotlinx-datetime/kotlinx-serialization shipping a built-in [Instant]
 * serializer in whatever version this project pins - same "String on the wire, typed value in the
 * domain layer, a small mapper in between" split already used for every other Instant field in
 * this codebase (see AgendaDtos.kt).
 */
@Serializable
private data class PersonalMessageDto(
    val id: String,
    val title: String? = null,
    val body: String,
    val scheduledAtIso: String,
    val notificationSoundId: String? = null,
    val voiceMessageId: String? = null,
    val seen: Boolean = false,
) {
    fun toDomain(): PersonalMessage? {
        val scheduledAt = runCatching { Instant.parse(scheduledAtIso) }.getOrNull() ?: return null
        return PersonalMessage(
            id = id,
            title = title,
            body = body,
            scheduledAt = scheduledAt,
            notificationSoundId = notificationSoundId?.let { raw ->
                runCatching { NotificationSoundId.valueOf(raw) }.getOrNull()
            },
            voiceMessageId = voiceMessageId?.let { raw ->
                runCatching { VoiceMessageId.valueOf(raw) }.getOrNull()
            },
            seen = seen,
        )
    }

    companion object {
        fun fromDomain(message: PersonalMessage) = PersonalMessageDto(
            id = message.id,
            title = message.title,
            body = message.body,
            scheduledAtIso = message.scheduledAt.toString(),
            notificationSoundId = message.notificationSoundId?.name,
            voiceMessageId = message.voiceMessageId?.name,
            seen = message.seen,
        )
    }
}

class SupabaseSettingsRepository(
    private val api: AgendaApiClient,
) : SettingsRepository {
    override suspend fun fetchBackgroundUrl(): String? = api.fetchSetting("background_url")

    override suspend fun updateBackgroundUrl(url: String) {
        api.updateSetting("background_url", url)
    }

    override suspend fun fetchThemeMode(): Boolean? {
        val value = api.fetchSetting("theme_mode")?.trim()?.lowercase()
        return when (value) {
            "dark", "oscuro", "true" -> true
            "light", "claro", "false" -> false
            else -> null
        }
    }

    override suspend fun updateThemeMode(isDark: Boolean) {
        val value = if (isDark) "dark" else "light"
        api.updateSetting("theme_mode", value)
    }

    override suspend fun fetchTaskTemplates(): List<TaskTemplate> {
        val raw = api.fetchSetting(TASK_TEMPLATES_KEY)?.takeIf { it.isNotBlank() } ?: return emptyList()
        return runCatching { templatesJson.decodeFromString<List<TaskTemplate>>(raw) }.getOrDefault(emptyList())
    }

    override suspend fun saveTaskTemplates(templates: List<TaskTemplate>): Boolean {
        return runCatching {
            val encoded = templatesJson.encodeToString(templates)
            api.updateSetting(TASK_TEMPLATES_KEY, encoded)
        }.isSuccess
    }

    override suspend fun fetchPersonalMessages(): List<PersonalMessage> {
        val raw = api.fetchSetting(PERSONAL_MESSAGES_KEY)?.takeIf { it.isNotBlank() } ?: return emptyList()
        val dtos = runCatching { templatesJson.decodeFromString<List<PersonalMessageDto>>(raw) }
            .getOrDefault(emptyList())
        // A DTO with an unparseable scheduledAtIso is dropped, not surfaced as a crash or a
        // broken row - same "never let one bad row wedge fetching everything else" posture as
        // the rest of this repository's runCatching-wrapped decode calls.
        return dtos.mapNotNull { it.toDomain() }
    }

    override suspend fun savePersonalMessages(messages: List<PersonalMessage>): Boolean {
        return runCatching {
            val encoded = templatesJson.encodeToString(messages.map { PersonalMessageDto.fromDomain(it) })
            api.updateSetting(PERSONAL_MESSAGES_KEY, encoded)
        }.isSuccess
    }
}
