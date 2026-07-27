package com.franciscor.agendnote.feature.settings.presentation.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.franciscor.agendnote.core.network.AppConfig
import com.franciscor.agendnote.feature.settings.domain.SettingsRepository
import com.franciscor.agendnote.feature.settings.presentation.model.SettingsBulkAction
import com.franciscor.agendnote.feature.settings.presentation.model.SettingsUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository?,
    private val fallbackBackgroundUrl: String = AppConfig.BACKGROUND_URL.trim(),
    private val remoteUnavailableMessage: String? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val hasRemoteAccess = repository != null
    private val remoteErrorMessage = remoteUnavailableMessage
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: "Configuración remota incompleta. No se puede conectar con la BD."

    var uiState by mutableStateOf(
        SettingsUiState(
            backgroundUrl = fallbackBackgroundUrl,
            isRemoteAvailable = hasRemoteAccess,
        ),
    )
        private set

    fun loadSettings() {
        uiState = uiState.copy(isLoading = true, errorMessage = null)

        val repository = repository ?: run {
            uiState = uiState.copy(
                isLoading = false,
                errorMessage = remoteErrorMessage,
            )
            return
        }

        scope.launch {
            val (themeModeResult, backgroundUrlResult) = coroutineScope {
                val themeModeDeferred = async { runCatching { repository.fetchThemeMode() } }
                val backgroundUrlDeferred = async { runCatching { repository.fetchBackgroundUrl() } }
                themeModeDeferred.await() to backgroundUrlDeferred.await()
            }

            uiState = uiState.copy(
                isDarkMode = themeModeResult.getOrNull() ?: uiState.isDarkMode,
                backgroundUrl = backgroundUrlResult.getOrNull()?.takeUnless { it.isBlank() } ?: uiState.backgroundUrl,
                isLoading = false,
                errorMessage = if (themeModeResult.isFailure || backgroundUrlResult.isFailure) {
                    "No se pudo cargar la configuración"
                } else {
                    null
                },
            )
        }
    }

    fun setTheme(isDark: Boolean) {
        val repository = repository ?: run {
            uiState = uiState.copy(errorMessage = remoteErrorMessage)
            return
        }

        val previousTheme = uiState.isDarkMode
        if (previousTheme == isDark) return
        uiState = uiState.copy(isDarkMode = isDark, errorMessage = null)

        scope.launch {
            val result = runCatching { repository.updateThemeMode(isDark) }
            if (result.isFailure && uiState.isDarkMode == isDark) {
                // REVIEW: optimistic updates need rollback or the UI claims a setting was saved
                // even though the server rejected it.
                uiState = uiState.copy(
                    isDarkMode = previousTheme,
                    errorMessage = "No se pudo guardar el tema",
                )
            }
        }
    }

    fun setBackgroundUrl(url: String) {
        val repository = repository ?: run {
            uiState = uiState.copy(errorMessage = remoteErrorMessage)
            return
        }

        val trimmed = url.trim()
        val previousUrl = uiState.backgroundUrl
        if (previousUrl == trimmed) return
        uiState = uiState.copy(backgroundUrl = trimmed, errorMessage = null)

        scope.launch {
            val result = runCatching { repository.updateBackgroundUrl(trimmed) }
            if (result.isFailure && uiState.backgroundUrl == trimmed) {
                uiState = uiState.copy(
                    backgroundUrl = previousUrl,
                    errorMessage = "No se pudo guardar el fondo",
                )
            }
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
            errorMessage = if (success) null else message ?: "No se pudo completar la acción",
        )
    }

    fun confirmBulkAction(execute: suspend () -> Boolean) {
        scope.launch {
            val success = execute()
            completeBulkAction(
                success = success,
                message = if (success) null else "No se pudo completar la acción",
            )
        }
    }

    fun dismissError() {
        uiState = uiState.copy(errorMessage = null)
    }
}
