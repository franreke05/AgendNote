package com.franciscor.agendnote.app.di

import com.franciscor.agendnote.core.network.AgendaApiClient
import com.franciscor.agendnote.core.network.AppConfig
import com.franciscor.agendnote.feature.agenda.data.SupabaseAgendaTaskRepository
import com.franciscor.agendnote.feature.agenda.domain.AgendaTaskRepository
import com.franciscor.agendnote.feature.labels.data.SupabaseLabelRepository
import com.franciscor.agendnote.feature.labels.domain.LabelRepository
import com.franciscor.agendnote.feature.settings.data.SupabaseSettingsRepository
import com.franciscor.agendnote.feature.settings.domain.SettingsRepository

object AppServices {
    private val useRemote = AppConfig.API_BASE_URL.isNotBlank() && AppConfig.APP_SECRET.isNotBlank()

    private val apiClient: AgendaApiClient by lazy(LazyThreadSafetyMode.NONE) {
        AgendaApiClient(AppConfig.API_BASE_URL, AppConfig.APP_SECRET)
    }

    val agendaTaskRepository: AgendaTaskRepository? by lazy(LazyThreadSafetyMode.NONE) {
        if (useRemote) SupabaseAgendaTaskRepository(apiClient) else null
    }

    val labelRepository: LabelRepository? by lazy(LazyThreadSafetyMode.NONE) {
        if (useRemote) SupabaseLabelRepository(apiClient) else null
    }

    val settingsRepository: SettingsRepository? by lazy(LazyThreadSafetyMode.NONE) {
        if (useRemote) SupabaseSettingsRepository(apiClient) else null
    }
}
