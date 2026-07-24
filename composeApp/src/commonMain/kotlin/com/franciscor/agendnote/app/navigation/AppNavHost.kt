package com.franciscor.agendnote.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.franciscor.agendnote.app.di.AppServices
import com.franciscor.agendnote.core.ui.components.GlassBackground
import com.franciscor.agendnote.core.ui.layout.AppLayout
import com.franciscor.agendnote.feature.agenda.presentation.controller.AgendaController
import com.franciscor.agendnote.feature.agenda.presentation.view.AgendaScreen
import com.franciscor.agendnote.feature.agenda.presentation.viewmodel.AgendaViewModel
import com.franciscor.agendnote.feature.labels.presentation.controller.LabelsController
import com.franciscor.agendnote.feature.labels.presentation.model.LabelsAction
import com.franciscor.agendnote.feature.labels.presentation.view.LabelsScreen
import com.franciscor.agendnote.feature.labels.presentation.viewmodel.LabelsViewModel
import com.franciscor.agendnote.feature.settings.presentation.controller.SettingsController
import com.franciscor.agendnote.feature.settings.presentation.view.SettingsScreen
import com.franciscor.agendnote.feature.settings.presentation.viewmodel.SettingsViewModel

@Composable
fun AppNavHost(
    settingsViewModel: SettingsViewModel,
    settingsController: SettingsController,
) {
    val navController = rememberNavController()
    val agendaViewModel = remember { AgendaViewModel(AppServices.agendaTaskRepository) }
    val agendaController = remember(agendaViewModel) { AgendaController(agendaViewModel) }
    val labelsViewModel = remember { LabelsViewModel(AppServices.labelRepository) }
    val labelsController = remember(labelsViewModel) { LabelsController(labelsViewModel) }
    val layout = AppLayout.metrics
    val currentBackStackEntry = navController.currentBackStackEntryAsState()
    val selectedTab = MainTab.fromRoute(currentBackStackEntry.value?.destination?.route) ?: MainTab.AGENDA

    LaunchedEffect(labelsController) {
        labelsController.handle(LabelsAction.Load)
    }

    val navigateToMainTab: (MainTab) -> Unit = { tab ->
        navController.navigate(tab.route.route) {
            popUpTo(AppRoute.Agenda.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Small, scale-aware outer margin. Kept close to the previous 5dp literal on phones (via the
    // `min`) but now grows with widthScale on larger windows instead of staying a fixed literal.
    val contentHorizontalMargin = layout.width(8.dp, 6.dp)
    // Caps the main content/bottom bar width so wide windows (tablet/desktop/landscape) don't
    // stretch the UI edge-to-edge; content stays centered instead.
    val contentMaxWidth = 480.dp

    Box(modifier = Modifier.fillMaxSize()) {
        GlassBackground(
            modifier = Modifier.fillMaxSize(),
            imageUrl = settingsViewModel.uiState.backgroundUrl.ifBlank { null },
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                // Keeps interactive content clear of notches/system cutouts, matching the
                // pattern already used in CalendarOverlay (feature/agenda).
                .safeContentPadding()
                .padding(
                    horizontal = contentHorizontalMargin,
                    vertical = layout.height(16.dp, 12.dp),
                ),
            contentAlignment = Alignment.TopCenter,
        ) {
            NavHost(
                navController = navController,
                startDestination = AppRoute.Agenda.route,
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = contentMaxWidth),
            ) {
                composable(AppRoute.Agenda.route) {
                    AgendaRoute(
                        agendaViewModel = agendaViewModel,
                        agendaController = agendaController,
                        labelsViewModel = labelsViewModel,
                        labelsController = labelsController,
                    )
                }
                composable(AppRoute.Labels.route) {
                    LabelsRoute(
                        labelsViewModel = labelsViewModel,
                        labelsController = labelsController,
                        agendaController = agendaController,
                    )
                }
                composable(AppRoute.Settings.route) {
                    SettingsRoute(
                        settingsViewModel = settingsViewModel,
                        settingsController = settingsController,
                        onDeleteAllNotes = { agendaController.deleteAllTasks() },
                        onDeleteAllLabels = {
                            val success = labelsController.deleteAllLabels()
                            if (success) {
                                agendaController.clearLabelsFromTasks()
                            }
                            success
                        },
                    )
                }
            }
        }

        BottomBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // Same safe-area protection as the main content container above.
                .safeContentPadding()
                .padding(
                    horizontal = contentHorizontalMargin,
                    vertical = layout.height(16.dp, 14.dp),
                )
                .widthIn(max = contentMaxWidth),
            selectedTab = selectedTab,
            onSelect = navigateToMainTab,
        )
    }
}

@Composable
private fun AgendaRoute(
    agendaViewModel: AgendaViewModel,
    agendaController: AgendaController,
    labelsViewModel: LabelsViewModel,
    labelsController: LabelsController,
) {
    AgendaScreen(
        viewModel = agendaViewModel,
        controller = agendaController,
        labels = labelsViewModel.uiState.labels,
        onCreateLabel = { name, colorHex ->
            labelsController.createLabel(name, colorHex)
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun LabelsRoute(
    labelsViewModel: LabelsViewModel,
    labelsController: LabelsController,
    agendaController: AgendaController,
) {
    LabelsScreen(
        viewModel = labelsViewModel,
        controller = labelsController,
        onDeleteLabel = { label ->
            val success = labelsController.deleteLabel(label)
            if (success) {
                agendaController.removeLabelFromTasks(label.id)
            }
            success
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun SettingsRoute(
    settingsViewModel: SettingsViewModel,
    settingsController: SettingsController,
    onDeleteAllNotes: suspend () -> Boolean,
    onDeleteAllLabels: suspend () -> Boolean,
) {
    SettingsScreen(
        viewModel = settingsViewModel,
        controller = settingsController,
        onDeleteAllNotes = onDeleteAllNotes,
        onDeleteAllLabels = onDeleteAllLabels,
        modifier = Modifier.fillMaxSize(),
    )
}
