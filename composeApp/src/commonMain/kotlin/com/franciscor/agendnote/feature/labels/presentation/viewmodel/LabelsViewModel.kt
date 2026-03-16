package com.franciscor.agendnote.feature.labels.presentation.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.franciscor.agendnote.core.model.LabelTag
import com.franciscor.agendnote.feature.labels.domain.LabelRepository
import com.franciscor.agendnote.feature.labels.presentation.model.LabelsUiState

class LabelsViewModel(
    private val repository: LabelRepository?,
    private val remoteUnavailableMessage: String? = null,
) {
    private val hasRemoteAccess = repository != null
    private val remoteErrorMessage = remoteUnavailableMessage
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: "Configuracion remota incompleta. No se puede conectar con la BD."

    var uiState by mutableStateOf(LabelsUiState(isRemoteAvailable = hasRemoteAccess))
        private set

    suspend fun loadLabels() {
        uiState = uiState.copy(isLoading = true, errorMessage = null)

        val repository = repository ?: run {
            uiState = uiState.copy(isLoading = false, errorMessage = remoteErrorMessage)
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
        val repository = repository ?: run {
            uiState = uiState.copy(
                isLoading = false,
                errorMessage = remoteErrorMessage,
            )
            return null
        }

        return runCatching { repository.createLabel(trimmed, colorHex) }
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

    suspend fun deleteLabel(label: LabelTag): Boolean {
        val updated = uiState.labels.filterNot { it.id == label.id }
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        val repository = repository ?: run {
            uiState = uiState.copy(
                isLoading = false,
                errorMessage = remoteErrorMessage,
            )
            return false
        }
        return runCatching { repository.deleteLabel(label.id) }
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

    suspend fun deleteAllLabels(): Boolean {
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        val repository = repository ?: run {
            uiState = uiState.copy(
                isLoading = false,
                errorMessage = remoteErrorMessage,
            )
            return false
        }
        return runCatching { repository.deleteAllLabels() }
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

    fun dismissError() {
        uiState = uiState.copy(errorMessage = null)
    }
}
