package com.franciscor.agendnote.feature.settings.domain

import com.franciscor.agendnote.core.model.PersonalMessage
import com.franciscor.agendnote.core.model.TaskTemplate

interface SettingsRepository {
    suspend fun fetchBackgroundUrl(): String?

    suspend fun updateBackgroundUrl(url: String)

    suspend fun fetchThemeMode(): Boolean?

    suspend fun updateThemeMode(isDark: Boolean)

    suspend fun fetchTaskTemplates(): List<TaskTemplate>

    /** Replaces the whole list - read-modify-write, same pattern as every other setting here. */
    suspend fun saveTaskTemplates(templates: List<TaskTemplate>): Boolean

    suspend fun fetchPersonalMessages(): List<PersonalMessage>

    /** Replaces the whole list - same read-modify-write pattern as [saveTaskTemplates]. */
    suspend fun savePersonalMessages(messages: List<PersonalMessage>): Boolean
}
