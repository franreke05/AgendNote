package com.franciscor.agendnote.feature.labels.presentation.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.franciscor.agendnote.core.model.LabelTag
import com.franciscor.agendnote.core.ui.components.GlassActionButton
import com.franciscor.agendnote.core.ui.components.GlassSurface
import com.franciscor.agendnote.core.ui.components.GlassTextField
import com.franciscor.agendnote.core.ui.components.colorFromHex
import com.franciscor.agendnote.core.ui.components.labelColorPalette
import com.franciscor.agendnote.core.ui.layout.AppLayout
import com.franciscor.agendnote.core.ui.theme.GlassTheme
import com.franciscor.agendnote.feature.labels.presentation.controller.LabelsController
import com.franciscor.agendnote.feature.labels.presentation.viewmodel.LabelsViewModel
import kotlinx.coroutines.launch

@Composable
fun LabelsScreen(
    viewModel: LabelsViewModel,
    controller: LabelsController,
    onDeleteLabel: suspend (LabelTag) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val layout = AppLayout.metrics
    val uiState = viewModel.uiState
    val palette = labelColorPalette()
    val usedColors = uiState.labels.map { it.colorHex.lowercase() }.toSet()
    val colorOptions = palette
        .filterNot { usedColors.contains(it.lowercase()) }
        .distinct()
        .ifEmpty { palette.distinct() }
    var newLabelName by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(colorOptions.first()) }
    var localError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(colorOptions) {
        if (!colorOptions.contains(selectedColor)) {
            selectedColor = colorOptions.first()
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(layout.height(12.dp, 10.dp)),
        contentPadding = PaddingValues(bottom = layout.height(140.dp, 110.dp)),
    ) {
        item {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(layout.size(24.dp, 20.dp)),
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.padding(layout.size(16.dp, 14.dp)),
                    verticalArrangement = Arrangement.spacedBy(layout.height(12.dp, 10.dp)),
                ) {
                    Text(
                        text = "Crear etiqueta",
                        style = MaterialTheme.typography.titleMedium,
                        color = GlassTheme.tokens.textPrimary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(layout.width(10.dp, 8.dp))) {
                        GlassTextField(
                            value = newLabelName,
                            onValueChange = { newLabelName = it },
                            placeholder = "Nombre",
                            modifier = Modifier.weight(1f),
                        )
                        GlassActionButton(
                            text = "Agregar",
                            enabled = newLabelName.isNotBlank() && !uiState.isLoading,
                            tint = GlassTheme.tokens.glassFillStrong,
                            textColor = GlassTheme.tokens.textPrimary,
                            onClick = {
                                val name = newLabelName.trim()
                                if (name.isEmpty()) return@GlassActionButton
                                scope.launch {
                                    val created = controller.createLabel(name, selectedColor)
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
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(layout.width(10.dp, 8.dp)),
                    ) {
                        colorOptions.forEach { hex ->
                            val color = colorFromHex(hex)
                            ColorSwatch(
                                color = color,
                                selected = hex == selectedColor,
                                onClick = { selectedColor = hex },
                            )
                        }
                    }
                    if (localError != null) {
                        Text(
                            text = localError.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFB53B3B),
                        )
                    }
                }
            }
        }

        if (uiState.errorMessage != null) {
            item {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(layout.size(20.dp, 18.dp)),
                ) {
                    Text(
                        text = uiState.errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB53B3B),
                        modifier = Modifier.padding(
                            horizontal = layout.width(16.dp, 14.dp),
                            vertical = layout.height(14.dp, 12.dp),
                        ),
                    )
                }
            }
        }

        if (uiState.labels.isEmpty()) {
            item {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(layout.size(24.dp, 20.dp)),
                ) {
                    Text(
                        text = "Sin etiquetas creadas",
                        style = MaterialTheme.typography.bodyLarge,
                        color = GlassTheme.tokens.textSecondary,
                        modifier = Modifier.padding(
                            horizontal = layout.width(16.dp, 14.dp),
                            vertical = layout.height(18.dp, 14.dp),
                        ),
                    )
                }
            }
        } else {
            items(uiState.labels, key = { it.id }) { label ->
                LabelRow(
                    label = label,
                    onDelete = {
                        scope.launch {
                            val success = onDeleteLabel(label)
                            localError = if (success) null else "No se pudo eliminar la etiqueta"
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun LabelRow(
    label: LabelTag,
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
                horizontal = layout.width(16.dp, 14.dp),
                vertical = layout.height(14.dp, 12.dp),
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(layout.width(10.dp, 8.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .size(layout.size(10.dp, 8.dp))
                        .clip(CircleShape)
                        .background(color),
                )
                Text(
                    text = label.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = GlassTheme.tokens.textPrimary,
                )
            }
            GlassSurface(
                shape = RoundedCornerShape(layout.size(14.dp, 12.dp)),
                tint = GlassTheme.tokens.glassFill,
                modifier = Modifier
                    .clip(RoundedCornerShape(layout.size(14.dp, 12.dp)))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDelete,
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = layout.width(10.dp, 8.dp),
                        vertical = layout.height(6.dp, 6.dp),
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(layout.width(6.dp, 4.dp)),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Eliminar",
                        tint = GlassTheme.tokens.textSecondary,
                    )
                    Text(
                        text = "Eliminar",
                        style = MaterialTheme.typography.labelMedium,
                        color = GlassTheme.tokens.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val layout = AppLayout.metrics
    Box(
        modifier = Modifier
            .size(layout.size(26.dp, 22.dp))
            .clip(CircleShape)
            .background(color)
            .border(
                width = layout.size(2.dp, 1.dp),
                color = if (selected) GlassTheme.tokens.glassHighlight else Color.Transparent,
                shape = CircleShape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    )
}
