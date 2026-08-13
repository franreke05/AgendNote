package com.franciscor.agendnote.feature.settings.presentation.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.franciscor.agendnote.app.navigation.SectionHeader
import com.franciscor.agendnote.core.model.TaskSeries
import com.franciscor.agendnote.core.network.AppConfig
import com.franciscor.agendnote.core.ui.components.GlassActionButton
import com.franciscor.agendnote.core.ui.components.GlassConfirmDialog
import com.franciscor.agendnote.core.ui.components.GlassIconButton
import com.franciscor.agendnote.core.ui.components.GlassSelectableChip
import com.franciscor.agendnote.core.ui.components.GlassSurface
import com.franciscor.agendnote.core.ui.layout.AppLayout
import com.franciscor.agendnote.core.ui.theme.GlassTheme
import com.franciscor.agendnote.feature.agenda.domain.RecurrenceRule
import com.franciscor.agendnote.feature.settings.presentation.controller.SettingsController
import com.franciscor.agendnote.feature.settings.presentation.model.SettingsAction
import com.franciscor.agendnote.feature.settings.presentation.model.SettingsBulkAction
import com.franciscor.agendnote.feature.settings.presentation.viewmodel.SettingsViewModel
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber

private enum class SettingsPage {
    HOME,
    APPEARANCE,
    DATA,
    RECURRING,
    DANGER,
    ABOUT,
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    controller: SettingsController,
    onExportRequested: () -> String = { "" },
    onDeleteAllNotes: suspend () -> Boolean,
    onDeleteAllLabels: suspend () -> Boolean,
    seriesList: List<TaskSeries>,
    onDeleteSeries: (TaskSeries) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState = viewModel.uiState
    val isEditingEnabled = uiState.isRemoteAvailable
    var page by remember { mutableStateOf(SettingsPage.HOME) }
    var seriesPendingDelete by remember { mutableStateOf<TaskSeries?>(null) }
    var exportCopiedMessage by remember { mutableStateOf<String?>(null) }
    val clipboardManager = LocalClipboardManager.current

    val openPage: (SettingsPage) -> Unit = { target -> page = target }
    val goHome: () -> Unit = { page = SettingsPage.HOME }

    when (page) {
        SettingsPage.HOME -> SettingsHomePage(
            uiState = uiState,
            seriesCount = seriesList.size,
            onOpenPage = openPage,
            modifier = modifier,
        )

        SettingsPage.APPEARANCE -> SettingsAppearancePage(
            uiState = uiState,
            isEditingEnabled = isEditingEnabled,
            onBack = goHome,
            onSetTheme = { isDark -> controller.handle(SettingsAction.SetTheme(isDark)) },
            modifier = modifier,
        )

        SettingsPage.DATA -> SettingsDataPage(
            onBack = goHome,
            copiedMessage = exportCopiedMessage,
            onExport = {
                clipboardManager.setText(AnnotatedString(onExportRequested()))
                exportCopiedMessage = "Copiado al portapapeles"
            },
            modifier = modifier,
        )

        SettingsPage.RECURRING -> SettingsRecurringPage(
            seriesList = seriesList,
            isEditingEnabled = isEditingEnabled,
            onBack = goHome,
            onDeleteSeries = { seriesPendingDelete = it },
            modifier = modifier,
        )

        SettingsPage.DANGER -> SettingsDangerPage(
            isEditingEnabled = isEditingEnabled,
            onBack = goHome,
            onDeleteAllNotes = {
                controller.handle(SettingsAction.RequestBulkAction(SettingsBulkAction.DELETE_NOTES))
            },
            onDeleteAllLabels = {
                controller.handle(SettingsAction.RequestBulkAction(SettingsBulkAction.DELETE_LABELS))
            },
            modifier = modifier,
        )

        SettingsPage.ABOUT -> SettingsAboutPage(
            onBack = goHome,
            modifier = modifier,
        )
    }

    val pendingBulkAction = uiState.pendingBulkAction
    GlassConfirmDialog(
        visible = pendingBulkAction != null,
        title = if (pendingBulkAction == SettingsBulkAction.DELETE_NOTES) {
            "¿Borrar todas las tareas?"
        } else {
            "¿Borrar todas las etiquetas?"
        },
        message = if (pendingBulkAction == SettingsBulkAction.DELETE_NOTES) {
            "Esta acción elimina todas las tareas guardadas."
        } else {
            "Esta acción elimina todas las etiquetas creadas."
        },
        confirmText = "Borrar",
        onConfirm = {
            if (pendingBulkAction != null) {
                controller.handle(
                    SettingsAction.ConfirmBulkAction {
                        if (pendingBulkAction == SettingsBulkAction.DELETE_NOTES) {
                            onDeleteAllNotes()
                        } else {
                            onDeleteAllLabels()
                        }
                    },
                )
            }
        },
        onDismiss = { controller.handle(SettingsAction.DismissBulkAction) },
    )

    seriesPendingDelete?.let { series ->
        GlassConfirmDialog(
            visible = true,
            title = "¿Borrar serie recurrente?",
            message = "Se eliminarán las apariciones futuras de \"${series.title}\" que no estén completadas. Las pasadas se conservan.",
            confirmText = "Borrar",
            onConfirm = {
                seriesPendingDelete = null
                onDeleteSeries(series)
            },
            onDismiss = { seriesPendingDelete = null },
        )
    }
}

@Composable
private fun SettingsHomePage(
    uiState: com.franciscor.agendnote.feature.settings.presentation.model.SettingsUiState,
    seriesCount: Int,
    onOpenPage: (SettingsPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsListContainer(modifier = modifier) {
        item {
            SectionHeader("Ajustes", "Todo lo importante, en un solo lugar")
        }
        item {
            SettingsMenuCard {
                SettingsMenuRow(
                    icon = Icons.Rounded.Palette,
                    title = "Apariencia",
                    subtitle = "Tema ${if (uiState.isDarkMode) "oscuro" else "claro"}",
                    onClick = { onOpenPage(SettingsPage.APPEARANCE) },
                )
                SettingsMenuRow(
                    icon = Icons.Rounded.FileDownload,
                    title = "Datos",
                    subtitle = "Exporta una copia de tus tareas y etiquetas",
                    onClick = { onOpenPage(SettingsPage.DATA) },
                )
                if (seriesCount > 0) {
                    SettingsMenuRow(
                        icon = Icons.Rounded.Repeat,
                        title = "Tareas recurrentes",
                        subtitle = "$seriesCount ${if (seriesCount == 1) "serie activa" else "series activas"}",
                        onClick = { onOpenPage(SettingsPage.RECURRING) },
                    )
                }
                SettingsMenuRow(
                    icon = Icons.Rounded.DeleteSweep,
                    title = "Zona de peligro",
                    subtitle = "Acciones para borrar datos de la nube",
                    onClick = { onOpenPage(SettingsPage.DANGER) },
                )
                SettingsMenuRow(
                    icon = Icons.Rounded.Info,
                    title = "Acerca de",
                    subtitle = "Información de AgendNote",
                    onClick = { onOpenPage(SettingsPage.ABOUT) },
                    showDivider = false,
                )
            }
        }
    }
}

@Composable
private fun SettingsAppearancePage(
    uiState: com.franciscor.agendnote.feature.settings.presentation.model.SettingsUiState,
    isEditingEnabled: Boolean,
    onBack: () -> Unit,
    onSetTheme: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSubpageContainer(
        title = "Apariencia",
        subtitle = "Elige cómo quieres ver AgendNote",
        onBack = onBack,
        modifier = modifier,
    ) {
        item {
            SettingsCard {
                Text(
                    text = "Modo de color",
                    style = MaterialTheme.typography.titleMedium,
                    color = GlassTheme.tokens.textPrimary,
                )
                Text(
                    text = "La aplicación se adapta al estilo que prefieras.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassTheme.tokens.textSecondary,
                )
                if (uiState.errorMessage != null && isEditingEnabled) {
                    Text(
                        text = uiState.errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTheme.tokens.errorContent,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppLayout.metrics.width(10.dp, 8.dp)),
                ) {
                    GlassSelectableChip(
                        text = "Claro",
                        selected = !uiState.isDarkMode,
                        enabled = isEditingEnabled,
                        onClick = { onSetTheme(false) },
                        modifier = Modifier.weight(1f),
                    )
                    GlassSelectableChip(
                        text = "Oscuro",
                        selected = uiState.isDarkMode,
                        enabled = isEditingEnabled,
                        onClick = { onSetTheme(true) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsDataPage(
    onBack: () -> Unit,
    copiedMessage: String?,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSubpageContainer(
        title = "Datos",
        subtitle = "Lleva contigo una copia de tu información",
        onBack = onBack,
        modifier = modifier,
    ) {
        item {
            SettingsCard {
                Text(
                    text = "Exportar tus datos",
                    style = MaterialTheme.typography.titleMedium,
                    color = GlassTheme.tokens.textPrimary,
                )
                Text(
                    text = "Copia tus tareas y etiquetas visibles como JSON al portapapeles. Solo incluye lo que ya está cargado, no exporta la nube entera.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassTheme.tokens.textSecondary,
                )
                GlassActionButton(
                    text = "Exportar mis datos",
                    tint = GlassTheme.tokens.glassFillStrong,
                    textColor = GlassTheme.tokens.textPrimary,
                    onClick = onExport,
                    modifier = Modifier.fillMaxWidth(),
                )
                copiedMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelMedium,
                        color = GlassTheme.tokens.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsRecurringPage(
    seriesList: List<TaskSeries>,
    isEditingEnabled: Boolean,
    onBack: () -> Unit,
    onDeleteSeries: (TaskSeries) -> Unit,
    modifier: Modifier = Modifier,
) {
    val destructiveTint = if (isEditingEnabled) {
        GlassTheme.tokens.error.copy(alpha = 0.18f)
    } else {
        GlassTheme.tokens.glassFillDisabled
    }
    val destructiveTextColor = if (isEditingEnabled) {
        GlassTheme.tokens.errorContent
    } else {
        GlassTheme.tokens.textDisabled
    }
    SettingsSubpageContainer(
        title = "Tareas recurrentes",
        subtitle = "Revisa y organiza tus series activas",
        onBack = onBack,
        modifier = modifier,
    ) {
        item {
            SettingsCard {
                seriesList.forEachIndexed { index, series ->
                    if (index > 0) {
                        Spacer(modifier = Modifier.height(AppLayout.metrics.height(10.dp, 8.dp)))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppLayout.metrics.width(10.dp, 8.dp)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = series.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = GlassTheme.tokens.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = describeRecurrence(series.rule),
                                style = MaterialTheme.typography.bodySmall,
                                color = GlassTheme.tokens.textSecondary,
                            )
                        }
                        GlassActionButton(
                            text = "Borrar",
                            enabled = isEditingEnabled,
                            tint = destructiveTint,
                            textColor = destructiveTextColor,
                            onClick = { onDeleteSeries(series) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsDangerPage(
    isEditingEnabled: Boolean,
    onBack: () -> Unit,
    onDeleteAllNotes: () -> Unit,
    onDeleteAllLabels: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val destructiveTint = if (isEditingEnabled) {
        GlassTheme.tokens.error.copy(alpha = 0.18f)
    } else {
        GlassTheme.tokens.glassFillDisabled
    }
    val destructiveTextColor = if (isEditingEnabled) {
        GlassTheme.tokens.errorContent
    } else {
        GlassTheme.tokens.textDisabled
    }
    SettingsSubpageContainer(
        title = "Zona de peligro",
        subtitle = "Acciones permanentes sobre tus datos",
        onBack = onBack,
        modifier = modifier,
    ) {
        item {
            SettingsCard {
                Text(
                    text = "Borrar datos",
                    style = MaterialTheme.typography.titleMedium,
                    color = GlassTheme.tokens.textPrimary,
                )
                Text(
                    text = "Estas acciones eliminan información guardada en la nube y no se pueden deshacer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassTheme.tokens.textSecondary,
                )
                GlassActionButton(
                    text = "Borrar todas las tareas",
                    enabled = isEditingEnabled,
                    tint = destructiveTint,
                    textColor = destructiveTextColor,
                    onClick = onDeleteAllNotes,
                    modifier = Modifier.fillMaxWidth(),
                )
                GlassActionButton(
                    text = "Borrar todas las etiquetas",
                    enabled = isEditingEnabled,
                    tint = destructiveTint,
                    textColor = destructiveTextColor,
                    onClick = onDeleteAllLabels,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SettingsAboutPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSubpageContainer(
        title = "Acerca de",
        subtitle = "Conoce un poco más sobre AgendNote",
        onBack = onBack,
        modifier = modifier,
    ) {
        item {
            SettingsCard {
                Text(
                    text = "AgendNote · v${AppConfig.APP_VERSION}",
                    style = MaterialTheme.typography.titleMedium,
                    color = GlassTheme.tokens.textPrimary,
                )
                Text(
                    text = "Tu agenda de tareas con etiquetas y series recurrentes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassTheme.tokens.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun SettingsListContainer(
    modifier: Modifier,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val layout = AppLayout.metrics
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = layout.width(16.dp, 14.dp)),
        verticalArrangement = Arrangement.spacedBy(layout.height(12.dp, 10.dp)),
        contentPadding = PaddingValues(
            top = layout.height(12.dp, 10.dp),
            bottom = layout.height(24.dp, 20.dp),
        ),
        content = content,
    )
}

@Composable
private fun SettingsSubpageContainer(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    SettingsListContainer(modifier = modifier) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppLayout.metrics.width(10.dp, 8.dp)),
            ) {
                GlassIconButton(
                    icon = Icons.Rounded.ArrowBack,
                    contentDescription = "Volver a Ajustes",
                    onClick = onBack,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = GlassTheme.tokens.textPrimary,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTheme.tokens.textSecondary,
                    )
                }
            }
        }
        content()
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val layout = AppLayout.metrics
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(layout.size(24.dp, 20.dp)),
    ) {
        Column(
            modifier = Modifier.padding(layout.size(18.dp, 16.dp)),
            verticalArrangement = Arrangement.spacedBy(layout.height(12.dp, 10.dp)),
            content = content,
        )
    }
}

@Composable
private fun SettingsMenuCard(content: @Composable ColumnScope.() -> Unit) {
    val layout = AppLayout.metrics
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(layout.size(24.dp, 20.dp)),
    ) {
        Column(
            modifier = Modifier.padding(vertical = layout.height(4.dp, 2.dp)),
            content = content,
        )
    }
}

@Composable
private fun SettingsMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    showDivider: Boolean = true,
) {
    val layout = AppLayout.metrics
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    role = Role.Button,
                    onClick = onClick,
                )
                .semantics(mergeDescendants = true) {}
                .padding(
                    horizontal = layout.width(16.dp, 14.dp),
                    vertical = layout.height(12.dp, 10.dp),
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(layout.width(12.dp, 10.dp)),
        ) {
            Box(
                modifier = Modifier
                    .size(layout.size(42.dp, 38.dp))
                    .clip(CircleShape)
                    .background(GlassTheme.tokens.glassFillStrong),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = GlassTheme.tokens.textPrimary,
                    modifier = Modifier.size(layout.size(21.dp, 19.dp)),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = GlassTheme.tokens.textPrimary,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassTheme.tokens.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = GlassTheme.tokens.textSecondary,
                modifier = Modifier.size(layout.size(22.dp, 20.dp)),
            )
        }
        if (showDivider) {
            GlassSurfaceDivider()
        }
    }
}

@Composable
private fun GlassSurfaceDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppLayout.metrics.width(16.dp, 14.dp))
            .height(AppLayout.metrics.height(1.dp, 1.dp))
            .background(GlassTheme.tokens.glassStroke.copy(alpha = 0.55f)),
    )
}

private fun describeRecurrence(rule: RecurrenceRule): String {
    return when (rule) {
        is RecurrenceRule.Daily -> "Todos los días"
        is RecurrenceRule.WeeklyDays -> {
            val names = rule.days.sortedBy { it.isoDayNumber }.joinToString(", ") { day ->
                when (day) {
                    DayOfWeek.MONDAY -> "lunes"
                    DayOfWeek.TUESDAY -> "martes"
                    DayOfWeek.WEDNESDAY -> "miércoles"
                    DayOfWeek.THURSDAY -> "jueves"
                    DayOfWeek.FRIDAY -> "viernes"
                    DayOfWeek.SATURDAY -> "sábado"
                    DayOfWeek.SUNDAY -> "domingo"
                }
            }
            "Cada $names"
        }
        is RecurrenceRule.Monthly -> "El día ${rule.dayOfMonth} de cada mes"
    }
}
