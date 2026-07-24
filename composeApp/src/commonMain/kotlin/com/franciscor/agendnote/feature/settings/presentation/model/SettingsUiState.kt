package com.franciscor.agendnote.feature.settings.presentation.model

data class SettingsUiState(
    val isDarkMode: Boolean = false,
    val backgroundUrl: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val pendingBulkAction: SettingsBulkAction? = null,
    val isRemoteAvailable: Boolean = true,
)
