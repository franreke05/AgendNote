package com.franciscor.agendnote.feature.labels.presentation.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.franciscor.agendnote.core.model.LabelTag
import com.franciscor.agendnote.feature.labels.domain.LabelRepository
import com.franciscor.agendnote.feature.labels.presentation.model.LabelsUiState
import kotlinx.datetime.Clock

class LabelsViewModel(
    private val repository: LabelRepository?,
) {
    var uiState by mutableStateOf(LabelsUiState())
        private set

    suspend fun loadLabels() {
        uiState = uiState.copy(isLoading = true, errorMessage = null)

        val repository = repository ?: run {
            uiState = uiState.copy(isLoading = false)
            return
        }

        runCatching { repository.fetchLabels() }
            .onSuccess {
                uiState = uiState.copy(
                    labels = it,
                    isLoading = false,
                    errorMessage = null,
                )
            }
            .onFailure {
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = "No se pudieron cargar las etiquetas",
                )
            }
    }

    suspend fun createLabel(name: String, colorHex: String): LabelTag? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        val repository = repository

        return if (repository == null) {
            LabelTag(
                id = "label-${Clock.System.now().toEpochMilliseconds()}",
                name = trimmed,
                colorHex = colorHex,
            ).also {
                uiState = uiState.copy(
                    labels = uiState.labels + it,
                    isLoading = false,
                    errorMessage = null,
                )
            }
        } else {
            runCatching { repository.createLabel(trimmed, colorHex) }
                .onSuccess {
                    uiState = uiState.copy(
                        labels = uiState.labels + it,
                        isLoading = false,
                        errorMessage = null,
                    )
                }
                .onFailure {
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = "No se pudo crear la etiqueta",
                    )
                }
                .getOrNull()
        }
    }

    suspend fun deleteLabel(label: LabelTag): Boolean {
        val updated = uiState.labels.filterNot { it.id == label.id }
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        val repository = repository
        return if (repository == null) {
            uiState = uiState.copy(
                labels = updated,
                isLoading = false,
                errorMessage = null,
            )
            true
        } else {
            runCatching { repository.deleteLabel(label.id) }
                .onSuccess { success ->
                    if (success) {
                        uiState = uiState.copy(
                            labels = updated,
                            isLoading = false,
                            errorMessage = null,
                        )
                    } else {
                        uiState = uiState.copy(
                            isLoading = false,
                            errorMessage = "No se pudo eliminar la etiqueta",
                        )
                    }
                }
                .onFailure {
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = "No se pudo eliminar la etiqueta",
                    )
                }
                .getOrDefault(false)
        }
    }

    suspend fun deleteAllLabels(): Boolean {
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        val repository = repository
        return if (repository == null) {
            uiState = uiState.copy(
                labels = emptyList(),
                isLoading = false,
                errorMessage = null,
            )
            true
        } else {
            runCatching { repository.deleteAllLabels() }
                .onSuccess { success ->
                    if (success) {
                        uiState = uiState.copy(
                            labels = emptyList(),
                            isLoading = false,
                            errorMessage = null,
                        )
                    } else {
                        uiState = uiState.copy(
                            isLoading = false,
                            errorMessage = "No se pudieron borrar las etiquetas",
                        )
                    }
                }
                .onFailure {
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = "No se pudieron borrar las etiquetas",
                    )
                }
                .getOrDefault(false)
        }
    }

    fun dismissError() {
        uiState = uiState.copy(errorMessage = null)
    }
}
