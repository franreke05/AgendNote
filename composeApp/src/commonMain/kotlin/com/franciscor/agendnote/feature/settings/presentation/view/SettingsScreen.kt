package com.franciscor.agendnote.feature.settings.presentation.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.franciscor.agendnote.core.model.TaskSeries
import com.franciscor.agendnote.core.ui.components.GlassActionButton
import com.franciscor.agendnote.core.ui.components.GlassConfirmDialog
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

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    controller: SettingsController,
    onDeleteAllNotes: suspend () -> Boolean,
    onDeleteAllLabels: suspend () -> Boolean,
    seriesList: List<TaskSeries>,
    onDeleteSeries: (TaskSeries) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = AppLayout.metrics
    val contentInset = layout.width(24.dp, 20.dp)
    val uiState = viewModel.uiState
    val isEditingEnabled = uiState.isRemoteAvailable
    var seriesPendingDelete by remember { mutableStateOf<TaskSeries?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = contentInset),
        verticalArrangement = Arrangement.spacedBy(layout.height(12.dp, 10.dp)),
        contentPadding = PaddingValues(
            bottom = layout.height(140.dp, 110.dp),
            top = layout.height(12.dp, 10.dp),
        ),
    ) {
        item {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(layout.size(24.dp, 20.dp)),
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.padding(layout.size(16.dp, 14.dp)),
                    verticalArrangement = Arrangement.spacedBy(layout.height(12.dp, 10.dp)),
                ) {
                    Text(
                        text = "Modo de color",
                        style = MaterialTheme.typography.titleMedium,
                        color = GlassTheme.tokens.textPrimary,
                    )
                    if (uiState.errorMessage != null) {
                        Text(
                            text = uiState.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = GlassTheme.tokens.error,
                        )
                    }
                    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(layout.width(10.dp, 8.dp))) {
                        ModeToggleButton(
                            text = "Claro",
                            selected = !uiState.isDarkMode,
                            enabled = isEditingEnabled,
                            onClick = { controller.handle(SettingsAction.SetTheme(false)) },
                            modifier = Modifier.weight(1f),
                        )
                        ModeToggleButton(
                            text = "Oscuro",
                            selected = uiState.isDarkMode,
                            enabled = isEditingEnabled,
                            onClick = { controller.handle(SettingsAction.SetTheme(true)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        item {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(layout.size(24.dp, 20.dp)),
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.padding(layout.size(16.dp, 14.dp)),
                    verticalArrangement = Arrangement.spacedBy(layout.height(12.dp, 10.dp)),
                ) {
                    Text(
                        text = "Acciones",
                        style = MaterialTheme.typography.titleMedium,
                        color = GlassTheme.tokens.textPrimary,
                    )
                    Text(
                        text = "Estas acciones borran datos en la nube.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTheme.tokens.textSecondary,
                    )
                    GlassActionButton(
                        text = "Borrar todas las notas",
                        enabled = isEditingEnabled,
                        tint = Color(0xFFE06B6B),
                        onClick = {
                            controller.handle(
                                SettingsAction.RequestBulkAction(SettingsBulkAction.DELETE_NOTES),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    GlassActionButton(
                        text = "Borrar todas las etiquetas",
                        enabled = isEditingEnabled,
                        tint = Color(0xFFE06B6B),
                        onClick = {
                            controller.handle(
                                SettingsAction.RequestBulkAction(SettingsBulkAction.DELETE_LABELS),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        if (seriesList.isNotEmpty()) {
            item {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(layout.size(24.dp, 20.dp)),
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.padding(layout.size(16.dp, 14.dp)),
                        verticalArrangement = Arrangement.spacedBy(layout.height(12.dp, 10.dp)),
                    ) {
                        Text(
                            text = "Tareas recurrentes",
                            style = MaterialTheme.typography.titleMedium,
                            color = GlassTheme.tokens.textPrimary,
                        )
                        seriesList.forEach { series ->
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = series.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = GlassTheme.tokens.textPrimary,
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
                                    tint = Color(0xFFE06B6B),
                                    onClick = { seriesPendingDelete = series },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val pendingBulkAction = uiState.pendingBulkAction
    GlassConfirmDialog(
        visible = pendingBulkAction != null,
        title = if (pendingBulkAction == SettingsBulkAction.DELETE_NOTES) {
            "Borrar todas las notas?"
        } else {
            "Borrar todas las etiquetas?"
        },
        message = if (pendingBulkAction == SettingsBulkAction.DELETE_NOTES) {
            "Esta accion elimina todas las notas guardadas."
        } else {
            "Esta accion elimina todas las etiquetas creadas."
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
            title = "Borrar serie recurrente?",
            message = "Se eliminaran las apariciones futuras de \"${series.title}\" que no esten completadas. Las pasadas se conservan.",
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
private fun ModeToggleButton(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = AppLayout.metrics
    val tint = when {
        !enabled -> GlassTheme.tokens.glassFill
        selected -> GlassTheme.tokens.accent
        else -> GlassTheme.tokens.glassFillStrong
    }
    val textColor = when {
        !enabled -> GlassTheme.tokens.textSecondary
        selected -> Color.White
        else -> GlassTheme.tokens.textPrimary
    }
    GlassSurface(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(layout.size(16.dp, 16.dp)))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(layout.size(16.dp, 16.dp)),
        tint = tint,
        strokeColor = if (selected) tint.copy(alpha = 0.6f) else GlassTheme.tokens.glassStroke,
    ) {
        Box(
            modifier = Modifier.padding(vertical = layout.height(10.dp, 10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun describeRecurrence(rule: RecurrenceRule): String {
    return when (rule) {
        is RecurrenceRule.Daily -> "Todos los dias"
        is RecurrenceRule.WeeklyDays -> {
            val names = rule.days.sortedBy { it.isoDayNumber }.joinToString(", ") { day ->
                when (day) {
                    DayOfWeek.MONDAY -> "lunes"
                    DayOfWeek.TUESDAY -> "martes"
                    DayOfWeek.WEDNESDAY -> "miercoles"
                    DayOfWeek.THURSDAY -> "jueves"
                    DayOfWeek.FRIDAY -> "viernes"
                    DayOfWeek.SATURDAY -> "sabado"
                    DayOfWeek.SUNDAY -> "domingo"
                    else -> day.name.lowercase()
                }
            }
            "Cada $names"
        }
        is RecurrenceRule.Monthly -> "El dia ${rule.dayOfMonth} de cada mes"
    }
}

