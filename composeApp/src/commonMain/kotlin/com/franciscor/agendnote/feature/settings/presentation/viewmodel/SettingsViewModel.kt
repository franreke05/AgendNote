package com.franciscor.agendnote.feature.settings.presentation.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.franciscor.agendnote.core.network.AppConfig
import com.franciscor.agendnote.core.platform.ThemeModeStore
import com.franciscor.agendnote.core.platform.createThemeModeStore
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
    private val themeModeStore: ThemeModeStore = createThemeModeStore(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val hasRemoteAccess = repository != null
    private val remoteErrorMessage = remoteUnavailableMessage
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: "Configuración remota incompleta. No se puede conectar con la BD."

    private val storedThemeMode = runCatching { themeModeStore.read() }.getOrNull()

    var uiState by mutableStateOf(
        SettingsUiState(
            isDarkMode = storedThemeMode ?: false,
            backgroundUrl = fallbackBackgroundUrl,
            isRemoteAvailable = hasRemoteAccess,
        ),
    )
        private set

    fun loadSettings() {
        uiState = uiState.copy(
            isDarkMode = storedThemeMode ?: uiState.isDarkMode,
            isLoading = true,
            errorMessage = null,
        )

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
                // A choice already made on this device must not be silently replaced by a stale
                // remote value after a restart. The server only seeds the preference once.
                isDarkMode = storedThemeMode ?: themeModeResult.getOrNull() ?: uiState.isDarkMode,
                backgroundUrl = backgroundUrlResult.getOrNull()?.takeUnless { it.isBlank() } ?: uiState.backgroundUrl,
                isLoading = false,
                errorMessage = if (themeModeResult.isFailure || backgroundUrlResult.isFailure) {
                    "No se pudo cargar la configuración"
                } else {
                    null
                },
            )

            if (storedThemeMode == null) {
                themeModeResult.getOrNull()?.let { remoteTheme ->
                    runCatching { themeModeStore.write(remoteTheme) }
                }
            }
        }
    }

    fun setTheme(isDark: Boolean) {
        val previousTheme = uiState.isDarkMode
        if (previousTheme == isDark) return
        uiState = uiState.copy(
            isDarkMode = isDark,
            errorMessage = if (repository == null) remoteErrorMessage else null,
        )

        scope.launch {
            val localResult = runCatching { themeModeStore.write(isDark) }
            val remoteResult = repository?.let { settingsRepository ->
                runCatching { settingsRepository.updateThemeMode(isDark) }
            }
            if (localResult.isFailure && uiState.isDarkMode == isDark) {
                uiState = uiState.copy(
                    isDarkMode = previousTheme,
                    errorMessage = "No se pudo guardar el tema en este dispositivo",
                )
            } else if (remoteResult?.isFailure == true && uiState.isDarkMode == isDark) {
                uiState = uiState.copy(
                    errorMessage = "Tema guardado en este dispositivo; no se pudo sincronizar",
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
