package com.franciscor.agendnote.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.navigation.NavBackStackEntry
import com.franciscor.agendnote.app.di.AppServices
import com.franciscor.agendnote.core.model.TaskSeries
import com.franciscor.agendnote.core.model.TaskTemplate
import com.franciscor.agendnote.core.network.RemoteConfigStatus
import com.franciscor.agendnote.core.ui.components.GlassBackground
import com.franciscor.agendnote.core.ui.layout.AppLayout
import com.franciscor.agendnote.feature.agenda.domain.SeriesMaterializer
import com.franciscor.agendnote.feature.agenda.domain.buildTaskExportJson
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// REVIEW: se reutiliza la misma lógica para enter/popEnter y exit/popExit — la dirección se
// calcula a partir del orden fijo de pestañas (tabSlideDirection), no de si la navegación es un
// push o un pop, así que el resultado ya es correcto en ambos sentidos sin duplicar la lógica.
private fun tabEnterTransition(
    scope: AnimatedContentTransitionScope<NavBackStackEntry>,
): EnterTransition {
    return when (
        tabSlideDirection(
            fromRoute = scope.initialState.destination.route,
            toRoute = scope.targetState.destination.route,
        )
    ) {
        SwipeDirection.NEXT -> slideInHorizontally(initialOffsetX = { it }) + fadeIn()
        SwipeDirection.PREVIOUS -> slideInHorizontally(initialOffsetX = { -it }) + fadeIn()
        null -> fadeIn()
    }
}

private fun tabExitTransition(
    scope: AnimatedContentTransitionScope<NavBackStackEntry>,
): ExitTransition {
    return when (
        tabSlideDirection(
            fromRoute = scope.initialState.destination.route,
            toRoute = scope.targetState.destination.route,
        )
    ) {
        SwipeDirection.NEXT -> slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
        SwipeDirection.PREVIOUS -> slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        null -> fadeOut()
    }
}

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

    var taskTemplates by remember { mutableStateOf<List<TaskTemplate>>(emptyList()) }

    suspend fun refreshTaskTemplates() {
        taskTemplates = runCatching { AppServices.settingsRepository?.fetchTaskTemplates() }
            .getOrNull() ?: emptyList()
    }

    suspend fun saveTaskTemplate(template: TaskTemplate): Boolean {
        val settingsRepository = AppServices.settingsRepository ?: return false
        // Lectura-modificacion-escritura sobre la lista completa (mismo patron que el resto de
        // settings, que no son una tabla propia). Reemplaza una plantilla existente con el mismo
        // nombre en vez de duplicarla.
        val updated = taskTemplates.filterNot { it.name == template.name } + template
        val success = settingsRepository.saveTaskTemplates(updated)
        if (success) taskTemplates = updated
        return success
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
        refreshTaskTemplates()
    }

    val navigateToMainTab: (MainTab) -> Unit = { tab ->
        navController.navigate(tab.route.route) {
            popUpTo(AppRoute.Agenda.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Small, scale-aware outer margin — just enough to keep rounded card corners off the screen
    // edge. Kept deliberately thin: the real horizontal rhythm comes from each screen's own
    // contentInset (see AgendaScreen/CalendarScreen/LabelsScreen/SettingsScreen), so content uses
    // as much of the display width as possible instead of losing it to two stacked margins.
    val contentHorizontalMargin = layout.width(4.dp, 4.dp)
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
                        enterTransition = { tabEnterTransition(this) },
                        exitTransition = { tabExitTransition(this) },
                        popEnterTransition = { tabEnterTransition(this) },
                        popExitTransition = { tabExitTransition(this) },
                    ) {
                        composable(AppRoute.Agenda.route) {
                            AgendaRoute(
                                agendaViewModel = agendaViewModel,
                                agendaController = agendaController,
                                labelsViewModel = labelsViewModel,
                                labelsController = labelsController,
                                templates = taskTemplates,
                                onSaveTemplate = { template -> saveTaskTemplate(template) },
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
                                navHostScope = navHostScope,
                            )
                        }
                        composable(AppRoute.Settings.route) {
                            LaunchedEffect(Unit) {
                                refreshRecurringSeries()
                            }
                            SettingsRoute(
                                settingsViewModel = settingsViewModel,
                                settingsController = settingsController,
                                onExportRequested = {
                                    buildTaskExportJson(
                                        agendaViewModel.uiState.tasksByDate,
                                        labelsViewModel.uiState.labels,
                                    )
                                },
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

                // REVIEW: navigation participates in layout instead of floating over content.
                // Every destination now receives one reliable viewport, so cards and actions
                // cannot be hidden behind the bar on compact screens.
                BottomBar(
                    modifier = Modifier.fillMaxWidth(),
                    selectedTab = selectedTab,
                    onSelect = navigateToMainTab,
                )
            }
        }
    }
}

@Composable
private fun AgendaRoute(
    agendaViewModel: AgendaViewModel,
    agendaController: AgendaController,
    labelsViewModel: LabelsViewModel,
    labelsController: LabelsController,
    templates: List<TaskTemplate>,
    onSaveTemplate: suspend (TaskTemplate) -> Boolean,
) {
    AgendaScreen(
        viewModel = agendaViewModel,
        controller = agendaController,
        labels = labelsViewModel.uiState.labels,
        onCreateLabel = { name, colorHex ->
            labelsController.createLabel(name, colorHex)
        },
        templates = templates,
        onSaveTemplate = onSaveTemplate,
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
        onOpenInAgenda = onNavigateToAgenda,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun LabelsRoute(
    labelsViewModel: LabelsViewModel,
    labelsController: LabelsController,
    agendaController: AgendaController,
    navHostScope: CoroutineScope,
) {
    LabelsScreen(
        viewModel = labelsViewModel,
        controller = labelsController,
        // Fire-and-forget: runs on navHostScope (survives the Labels tab leaving composition)
        // instead of the screen's own rememberCoroutineScope(), so the cross-ViewModel cleanup
        // below always runs even if the user switches tabs mid-request. Same class of bug as
        // series deletion above (see navHostScope's doc comment).
        onDeleteLabel = { label ->
            navHostScope.launch {
                val success = labelsController.deleteLabel(label)
                if (success) {
                    agendaController.removeLabelFromTasks(label.id)
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun SettingsRoute(
    settingsViewModel: SettingsViewModel,
    settingsController: SettingsController,
    onExportRequested: () -> String,
    onDeleteAllNotes: suspend () -> Boolean,
    onDeleteAllLabels: suspend () -> Boolean,
    seriesList: List<TaskSeries>,
    onDeleteSeries: (TaskSeries) -> Unit,
) {
    SettingsScreen(
        viewModel = settingsViewModel,
        controller = settingsController,
        onExportRequested = onExportRequested,
        onDeleteAllNotes = onDeleteAllNotes,
        onDeleteAllLabels = onDeleteAllLabels,
        seriesList = seriesList,
        onDeleteSeries = onDeleteSeries,
        modifier = Modifier.fillMaxSize(),
    )
}
