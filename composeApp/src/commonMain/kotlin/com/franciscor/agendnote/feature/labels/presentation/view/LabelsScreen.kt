package com.franciscor.agendnote.feature.labels.presentation.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.franciscor.agendnote.app.navigation.SectionHeader
import com.franciscor.agendnote.core.model.LabelTag
import com.franciscor.agendnote.core.ui.components.ColorSwatch
import com.franciscor.agendnote.core.ui.components.GlassActionButton
import com.franciscor.agendnote.core.ui.components.GlassConfirmDialog
import com.franciscor.agendnote.core.ui.components.GlassSurface
import com.franciscor.agendnote.core.ui.components.GlassTextField
import com.franciscor.agendnote.core.ui.components.colorFromHex
import com.franciscor.agendnote.core.ui.components.labelColorPalette
import com.franciscor.agendnote.core.ui.components.labelColorName
import com.franciscor.agendnote.core.ui.layout.AppLayout
import com.franciscor.agendnote.core.ui.theme.GlassTheme
import com.franciscor.agendnote.feature.labels.presentation.controller.LabelsController
import com.franciscor.agendnote.feature.labels.presentation.model.LabelsAction
import com.franciscor.agendnote.feature.labels.presentation.viewmodel.LabelsViewModel

@Composable
fun LabelsScreen(
    viewModel: LabelsViewModel,
    controller: LabelsController,
    onDeleteLabel: (LabelTag) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = AppLayout.metrics
    val contentInset = layout.width(16.dp, 14.dp)
    val uiState = viewModel.uiState
    val isEditingEnabled = uiState.isRemoteAvailable
    val palette = labelColorPalette()
    val usedColors = uiState.labels.map { it.colorHex.lowercase() }.toSet()
    val colorOptions = palette
        .filterNot { usedColors.contains(it.lowercase()) }
        .distinct()
        .ifEmpty { palette.distinct() }
    var newLabelName by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(colorOptions.first()) }
    var localError by remember { mutableStateOf<String?>(null) }
    var labelPendingDelete by remember { mutableStateOf<LabelTag?>(null) }

    LaunchedEffect(colorOptions) {
        if (!colorOptions.contains(selectedColor)) {
            selectedColor = colorOptions.first()
        }
    }

    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = contentInset),
            verticalArrangement = Arrangement.spacedBy(layout.height(16.dp, 14.dp)),
            contentPadding = PaddingValues(
                bottom = layout.height(24.dp, 20.dp),
                top = layout.height(12.dp, 10.dp),
            ),
        ) {
            item {
                SectionHeader("Etiquetas", "Crea y organiza las etiquetas de tus tareas")
            }

            item {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(layout.size(24.dp, 20.dp)),
                ) {
                    Column(
                        modifier = Modifier.padding(layout.size(20.dp, 18.dp)),
                        verticalArrangement = Arrangement.spacedBy(layout.height(14.dp, 12.dp)),
                    ) {
                        Text(
                            text = "Crear etiqueta",
                            style = MaterialTheme.typography.titleMedium,
                            color = GlassTheme.tokens.textPrimary,
                        )
                        Text(
                            text = "Nombre de la etiqueta",
                            style = MaterialTheme.typography.labelMedium,
                            color = GlassTheme.tokens.textSecondary,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(layout.width(10.dp, 8.dp))) {
                            GlassTextField(
                                value = newLabelName,
                                onValueChange = { newLabelName = it },
                                placeholder = "Nombre",
                                label = "Nombre de la etiqueta",
                                modifier = Modifier.weight(1f),
                            )
                            GlassActionButton(
                                text = "Agregar",
                                enabled = isEditingEnabled && newLabelName.isNotBlank() && !uiState.isLoading,
                                tint = GlassTheme.tokens.glassFillStrong,
                                textColor = GlassTheme.tokens.textPrimary,
                                onClick = {
                                    val name = newLabelName.trim()
                                    if (name.isEmpty()) return@GlassActionButton
                                    controller.createLabel(name, selectedColor) { created ->
                                        if (created != null) {
                                            newLabelName = ""
                                            localError = null
                                        } else {
                                            localError = "No se pudo crear la etiqueta"
                                        }
                                    }
                                },
                            )
                        }
                        // REVIEW: the old horizontal strip clipped colors and exposed 26 dp
                        // targets. Four predictable rows keep all 16 colors visible at 48 dp.
                        Column(
                            modifier = Modifier.selectableGroup(),
                            verticalArrangement = Arrangement.spacedBy(layout.height(4.dp, 4.dp)),
                        ) {
                            colorOptions.chunked(4).forEach { rowColors ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    rowColors.forEach { hex ->
                                        ColorSwatch(
                                            color = colorFromHex(hex),
                                            contentDescription = labelColorName(hex),
                                            selected = hex == selectedColor,
                                            onClick = { selectedColor = hex },
                                        )
                                    }
                                }
                            }
                        }
                        if (localError != null) {
                            Text(
                                text = localError.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = GlassTheme.tokens.errorContent,
                            )
                        }
                    }
                }
            }

            // Suppressed when the remote is unavailable: that error text is always identical to
            // the app-wide RemoteStatusBanner already shown above the tab content (AppNavHost),
            // so repeating it here would just be redundant.
            if (uiState.errorMessage != null && isEditingEnabled) {
                item {
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(layout.size(20.dp, 18.dp)),
                    ) {
                        Column(
                            modifier = Modifier.padding(
                                horizontal = layout.width(16.dp, 14.dp),
                                vertical = layout.height(14.dp, 12.dp),
                            ),
                            verticalArrangement = Arrangement.spacedBy(layout.height(10.dp, 8.dp)),
                        ) {
                            Text(
                                text = uiState.errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = GlassTheme.tokens.errorContent,
                            )
                            GlassActionButton(
                                text = "Reintentar",
                                onClick = {
                                    localError = null
                                    controller.handleAsync(LabelsAction.Load)
                                },
                                tint = GlassTheme.tokens.glassFillStrong,
                                textColor = GlassTheme.tokens.textPrimary,
                            )
                        }
                    }
                }
            }

            if (uiState.isLoading && uiState.labels.isEmpty()) {
                item {
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(layout.size(24.dp, 20.dp)),
                    ) {
                        Text(
                            text = "Cargando etiquetas...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = GlassTheme.tokens.textSecondary,
                            modifier = Modifier.padding(
                                horizontal = layout.width(16.dp, 14.dp),
                                vertical = layout.height(18.dp, 14.dp),
                            ),
                        )
                    }
                }
            } else if (uiState.labels.isEmpty()) {
                item {
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(layout.size(24.dp, 20.dp)),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = layout.width(16.dp, 14.dp),
                                    vertical = layout.height(28.dp, 22.dp),
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(layout.height(10.dp, 8.dp)),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Label,
                                contentDescription = null,
                                tint = GlassTheme.tokens.textSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(layout.size(56.dp, 48.dp)),
                            )
                            Text(
                                text = "Sin etiquetas creadas",
                                style = MaterialTheme.typography.bodyLarge,
                                color = GlassTheme.tokens.textSecondary,
                            )
                            Text(
                                text = "Elige un color y crea la primera arriba",
                                style = MaterialTheme.typography.bodyMedium,
                                color = GlassTheme.tokens.textSecondary.copy(alpha = 0.75f),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            } else {
                items(uiState.labels, key = { it.id }) { label ->
                    LabelRow(
                        label = label,
                        enabled = isEditingEnabled,
                        onDelete = { labelPendingDelete = label },
                    )
                }
            }
        }

        GlassConfirmDialog(
            visible = labelPendingDelete != null,
            title = "Eliminar etiqueta",
            message = "Se eliminará la etiqueta \"${labelPendingDelete?.name.orEmpty()}\" y se quitará de todas las tareas que la tengan asignada.",
            confirmText = "Eliminar",
            onConfirm = {
                val label = labelPendingDelete
                labelPendingDelete = null
                if (label != null) {
                    onDeleteLabel(label)
                }
            },
            onDismiss = { labelPendingDelete = null },
        )
    }
}

@Composable
private fun LabelRow(
    label: LabelTag,
    enabled: Boolean,
    onDelete: () -> Unit,
) {
    val layout = AppLayout.metrics
    val color = colorFromHex(label.colorHex)
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(layout.size(24.dp, 20.dp)),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = layout.width(18.dp, 16.dp),
                vertical = layout.height(18.dp, 16.dp),
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(layout.width(12.dp, 10.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .size(layout.size(12.dp, 10.dp))
                        .clip(CircleShape)
                        .background(color),
                )
                Text(
                    text = label.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = GlassTheme.tokens.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            GlassSurface(
                shape = RoundedCornerShape(layout.size(14.dp, 12.dp)),
                tint = GlassTheme.tokens.glassFill,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(layout.size(14.dp, 12.dp)))
                    .semantics(mergeDescendants = true) {}
                    .clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClickLabel = "Eliminar ${label.name}",
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDelete,
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = layout.width(12.dp, 10.dp),
                        vertical = layout.height(8.dp, 7.dp),
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(layout.width(6.dp, 4.dp)),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = if (enabled) GlassTheme.tokens.errorContent else GlassTheme.tokens.errorContent.copy(alpha = 0.55f),
                    )
                    Text(
                        text = "Eliminar",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (enabled) GlassTheme.tokens.errorContent else GlassTheme.tokens.errorContent.copy(alpha = 0.55f),
                    )
                }
            }
        }
    }
}
