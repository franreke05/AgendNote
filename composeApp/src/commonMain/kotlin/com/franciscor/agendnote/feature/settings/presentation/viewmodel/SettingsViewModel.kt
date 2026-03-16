package com.franciscor.agendnote.feature.settings.presentation.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.franciscor.agendnote.core.network.AppConfig
import com.franciscor.agendnote.feature.settings.domain.SettingsRepository
import com.franciscor.agendnote.feature.settings.presentation.model.SettingsBulkAction
import com.franciscor.agendnote.feature.settings.presentation.model.SettingsUiState

class SettingsViewModel(
    private val repository: SettingsRepository?,
    private val fallbackBackgroundUrl: String = AppConfig.BACKGROUND_URL.trim(),
    private val remoteUnavailableMessage: String? = null,
) {
    private val hasRemoteAccess = repository != null
    private val remoteErrorMessage = remoteUnavailableMessage
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: "Configuracion remota incompleta. No se puede conectar con la BD."

    var uiState by mutableStateOf(
        SettingsUiState(
            backgroundUrl = fallbackBackgroundUrl,
            isRemoteAvailable = hasRemoteAccess,
        ),
    )
        private set

    suspend fun loadSettings() {
        uiState = uiState.copy(isLoading = true, errorMessage = null)

        val repository = repository ?: run {
            uiState = uiState.copy(
                isLoading = false,
                errorMessage = remoteErrorMessage,
            )
            return
        }

        val themeMode = runCatching { repository.fetchThemeMode() }.getOrNull()
        val backgroundUrl = runCatching { repository.fetchBackgroundUrl() }.getOrNull()

        uiState = uiState.copy(
            isDarkMode = themeMode ?: uiState.isDarkMode,
            backgroundUrl = backgroundUrl?.takeUnless { it.isBlank() } ?: uiState.backgroundUrl,
            isLoading = false,
            errorMessage = null,
        )
    }

    suspend fun setTheme(isDark: Boolean) {
        val repository = repository ?: run {
            uiState = uiState.copy(errorMessage = remoteErrorMessage)
            return
        }

        uiState = uiState.copy(isDarkMode = isDark, errorMessage = null)

        val result = runCatching { repository.updateThemeMode(isDark) }
        if (result.isFailure) {
            uiState = uiState.copy(errorMessage = "No se pudo guardar el tema")
        }
    }

    fun requestBulkAction(action: SettingsBulkAction) {
        if (repository == null) {
            uiState = uiState.copy(
                pendingBulkAction = null,
                errorMessage = remoteErrorMessage,
            )
            return
        }
        uiState = uiState.copy(pendingBulkAction = action, errorMessage = null)
    }

    fun dismissBulkAction() {
        uiState = uiState.copy(pendingBulkAction = null)
    }

    fun completeBulkAction(success: Boolean, message: String?) {
        uiState = uiState.copy(
            pendingBulkAction = null,
            errorMessage = if (success) null else message ?: "No se pudo completar la accion",
        )
    }

    fun dismissError() {
        uiState = uiState.copy(errorMessage = null)
    }
}
