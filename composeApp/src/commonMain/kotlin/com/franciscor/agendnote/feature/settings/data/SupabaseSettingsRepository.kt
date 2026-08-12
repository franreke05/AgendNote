package com.franciscor.agendnote.feature.settings.data

import com.franciscor.agendnote.core.model.TaskTemplate
import com.franciscor.agendnote.core.network.AgendaApiClient
import com.franciscor.agendnote.feature.settings.domain.SettingsRepository
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TASK_TEMPLATES_KEY = "task_templates"
private val templatesJson = Json { ignoreUnknownKeys = true }

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
        api.updateSetting("theme_mode", if (isDark) "dark" else "light")
    }

    override suspend fun fetchTaskTemplates(): List<TaskTemplate> {
        val raw = api.fetchSetting(TASK_TEMPLATES_KEY)?.takeIf { it.isNotBlank() } ?: return emptyList()
        return runCatching {
            templatesJson.decodeFromString<List<TaskTemplate>>(raw)
        }.getOrDefault(emptyList())
    }

    override suspend fun saveTaskTemplates(templates: List<TaskTemplate>): Boolean {
        return runCatching {
            api.updateSetting(TASK_TEMPLATES_KEY, templatesJson.encodeToString(templates))
        }.isSuccess
    }
}
