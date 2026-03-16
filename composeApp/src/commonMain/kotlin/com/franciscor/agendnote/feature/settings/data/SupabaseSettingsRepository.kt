package com.franciscor.agendnote.feature.settings.data

import com.franciscor.agendnote.core.network.AgendaApiClient
import com.franciscor.agendnote.feature.settings.domain.SettingsRepository

class SupabaseSettingsRepository(
    private val api: AgendaApiClient,
) : SettingsRepository {
    override suspend fun fetchBackgroundUrl(): String? = api.fetchSetting("background_url")

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
}
