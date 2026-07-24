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
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var uiState by mutableStateOf(
        SettingsUiState(backgroundUrl = fallbackBackgroundUrl),
    )
        private set

    fun loadSettings() {
        uiState = uiState.copy(isLoading = true, errorMessage = null)

        val repository = repository ?: run {
            uiState = uiState.copy(isLoading = false)
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
                    "No se pudo cargar la configuracion"
                } else {
                    null
                },
            )
        }
    }

    fun setTheme(isDark: Boolean) {
        uiState = uiState.copy(isDarkMode = isDark, errorMessage = null)

        val repository = repository ?: return

        scope.launch {
            val result = runCatching { repository.updateThemeMode(isDark) }
            if (result.isFailure) {
                uiState = uiState.copy(errorMessage = "No se pudo guardar el tema")
            }
        }
    }

    fun requestBulkAction(action: SettingsBulkAction) {
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

    fun confirmBulkAction(execute: suspend () -> Boolean) {
        scope.launch {
            val success = execute()
            completeBulkAction(
                success = success,
                message = if (success) null else "No se pudo completar la accion",
            )
        }
    }

    fun dismissError() {
        uiState = uiState.copy(errorMessage = null)
    }
}
