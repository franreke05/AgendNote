package com.franciscor.agendnote.app

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.franciscor.agendnote.app.di.AppServices
import com.franciscor.agendnote.app.navigation.AppNavHost
import com.franciscor.agendnote.core.notifications.NotificationServiceProvider
import com.franciscor.agendnote.core.ui.layout.ProvideAppLayoutMetrics
import com.franciscor.agendnote.core.ui.layout.rememberAppLayoutMetrics
import com.franciscor.agendnote.core.ui.theme.AgendNoteTheme
import com.franciscor.agendnote.feature.settings.presentation.controller.SettingsController
import com.franciscor.agendnote.feature.settings.presentation.model.SettingsAction
import com.franciscor.agendnote.feature.settings.presentation.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun App() {
    val remoteConfigStatus = AppServices.remoteConfigStatus
    val settingsViewModel = remember(remoteConfigStatus) {
        SettingsViewModel(
            repository = AppServices.settingsRepository,
            remoteUnavailableMessage = remoteConfigStatus.message,
        )
    }
    val settingsController = remember(settingsViewModel) { SettingsController(settingsViewModel) }
    val notificationService = remember { NotificationServiceProvider.getNotificationService() }

    LaunchedEffect(settingsController) {
        settingsController.handle(SettingsAction.Load)
        // Request notification permissions on app start
        launch {
            notificationService.requestPermissions()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val layoutMetrics = rememberAppLayoutMetrics(
            maxWidth = maxWidth,
            maxHeight = maxHeight,
        )
        ProvideAppLayoutMetrics(metrics = layoutMetrics) {
            AgendNoteTheme(isDark = settingsViewModel.uiState.isDarkMode) {
                AppNavHost(
                    settingsViewModel = settingsViewModel,
                    settingsController = settingsController,
                    remoteConfigStatus = remoteConfigStatus,
                )
            }
        }
    }
}
