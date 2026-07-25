package com.franciscor.agendnote.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.franciscor.agendnote.app.di.AppServices
import com.franciscor.agendnote.core.model.TaskSeries
import com.franciscor.agendnote.core.network.RemoteConfigStatus
import com.franciscor.agendnote.core.ui.components.GlassBackground
import com.franciscor.agendnote.core.ui.layout.AppLayout
import com.franciscor.agendnote.feature.agenda.domain.SeriesMaterializer
import com.franciscor.agendnote.feature.agenda.presentation.controller.AgendaController
import com.franciscor.agendnote.feature.agenda.presentation.model.AgendaAction
import com.franciscor.agendnote.feature.agenda.presentation.view.AgendaScreen
import com.franciscor.agendnote.feature.agenda.presentation.view.CalendarScreen
import com.franciscor.agendnote.feature.agenda.presentation.viewmodel.AgendaViewModel
import com.franciscor.agendnote.feature.labels.presentation.controller.LabelsController
import com.franciscor.agendnote.feature.labels.presentation.model.LabelsAction
import com.franciscor.agendnote.feature.labels.presentation.view.LabelsScreen
import com.franciscor.agendnote.feature.labels.presentation.viewmodel.LabelsViewModel
import com.franciscor.agendnote.feature.settings.presentation.controller.SettingsController
import com.franciscor.agendnote.feature.settings.presentation.view.SettingsScreen
import com.franciscor.agendnote.feature.settings.presentation.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun AppNavHost(
    settingsViewModel: SettingsViewModel,
    settingsController: SettingsController,
    remoteConfigStatus: RemoteConfigStatus,
) {
    val navController = rememberNavController()
    val agendaViewModel = remember(remoteConfigStatus) {
        AgendaViewModel(
            repository = AppServices.agendaTaskRepository,
            taskSeriesRepository = AppServices.taskSeriesRepository,
            remoteUnavailableMessage = remoteConfigStatus.message,
        )
    }
    val agendaController = remember(agendaViewModel) { AgendaController(agendaViewModel) }
    val labelsViewModel = remember(remoteConfigStatus) {
        LabelsViewModel(
            repository = AppServices.labelRepository,
            remoteUnavailableMessage = remoteConfigStatus.message,
        )
    }
    val labelsController = remember(labelsViewModel) { LabelsController(labelsViewModel) }
    // Scoped to AppNavHost itself (not to any child route composable), so it survives bottom-nav
    // tab switches. Used for mutations like series deletion that must not be silently cancelled
    // when the Settings route leaves composition mid-request (see AgendaController's comment on
    // the same class of bug for label deletion).
    val navHostScope = rememberCoroutineScope()
    val layout = AppLayout.metrics
    val globalInset = layout.globalInset
    val currentBackStackEntry = navController.currentBackStackEntryAsState()
    val selectedTab = MainTab.fromRoute(currentBackStackEntry.value?.destination?.route) ?: MainTab.AGENDA

    LaunchedEffect(labelsController) {
        labelsController.handle(LabelsAction.Load)
    }

    var recurringSeries by remember { mutableStateOf<List<TaskSeries>>(emptyList()) }

    suspend fun refreshRecurringSeries() {
        recurringSeries = runCatching { AppServices.taskSeriesRepository?.fetchActiveSeries() }
            .getOrNull() ?: emptyList()
    }

    LaunchedEffect(agendaController) {
        val taskSeriesRepository = AppServices.taskSeriesRepository
        val agendaTaskRepository = AppServices.agendaTaskRepository
        if (taskSeriesRepository != null && agendaTaskRepository != null) {
            SeriesMaterializer(taskSeriesRepository, agendaTaskRepository)
                .materializeAll(agendaViewModel.today())
            agendaController.handleAsync(AgendaAction.RefreshSelectedDate)
        }
        refreshRecurringSeries()
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
                // pattern already used in CalendarMonthView (feature/agenda).
                .safeContentPadding()
                .padding(
                    horizontal = contentHorizontalMargin,
                    vertical = layout.height(16.dp, 12.dp),
                ),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = contentMaxWidth),
                verticalArrangement = Arrangement.spacedBy(layout.height(12.dp, 10.dp)),
            ) {
                remoteConfigStatus.message?.let { message ->
                    RemoteStatusBanner(message = message)
                }

                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = AppRoute.Agenda.route,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        composable(AppRoute.Agenda.route) {
                            AgendaRoute(
                                agendaViewModel = agendaViewModel,
                                agendaController = agendaController,
                                labelsViewModel = labelsViewModel,
                                labelsController = labelsController,
                            )
                        }
                        composable(AppRoute.Calendar.route) {
                            CalendarRoute(
                                agendaViewModel = agendaViewModel,
                                agendaController = agendaController,
                                onNavigateToAgenda = { navigateToMainTab(MainTab.AGENDA) },
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
                            LaunchedEffect(Unit) {
                                refreshRecurringSeries()
                            }
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
                                seriesList = recurringSeries,
                                onDeleteSeries = { series ->
                                    navHostScope.launch {
                                        val success = runCatching {
                                            AppServices.taskSeriesRepository?.deleteSeries(series.id)
                                        }.getOrNull() ?: false
                                        if (success) {
                                            refreshRecurringSeries()
                                            agendaController.handleAsync(AgendaAction.RefreshSelectedDate)
                                        }
                                    }
                                },
                            )
                        }
                    }
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
private fun CalendarRoute(
    agendaViewModel: AgendaViewModel,
    agendaController: AgendaController,
    onNavigateToAgenda: () -> Unit,
) {
    CalendarScreen(
        viewModel = agendaViewModel,
        controller = agendaController,
        onSelectDate = { date ->
            agendaController.handleAsync(AgendaAction.SelectDate(date))
            onNavigateToAgenda()
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
    seriesList: List<TaskSeries>,
    onDeleteSeries: (TaskSeries) -> Unit,
) {
    SettingsScreen(
        viewModel = settingsViewModel,
        controller = settingsController,
        onDeleteAllNotes = onDeleteAllNotes,
        onDeleteAllLabels = onDeleteAllLabels,
        seriesList = seriesList,
        onDeleteSeries = onDeleteSeries,
        modifier = Modifier.fillMaxSize(),
    )
}
