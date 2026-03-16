package com.franciscor.agendnote.feature.settings.domain

interface SettingsRepository {
    suspend fun fetchBackgroundUrl(): String?

    suspend fun fetchThemeMode(): Boolean?

    suspend fun updateThemeMode(isDark: Boolean)
}
