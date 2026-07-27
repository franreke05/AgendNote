package com.franciscor.agendnote.feature.labels.presentation.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.franciscor.agendnote.core.model.LabelTag
import com.franciscor.agendnote.feature.labels.domain.LabelRepository
import com.franciscor.agendnote.feature.labels.presentation.model.LabelsUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class LabelsViewModel(
    private val repository: LabelRepository?,
    private val remoteUnavailableMessage: String? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val hasRemoteAccess = repository != null
    private val remoteErrorMessage = remoteUnavailableMessage
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: "Configuración remota incompleta. No se puede conectar con la BD."

    var uiState by mutableStateOf(LabelsUiState(isRemoteAvailable = hasRemoteAccess))
        private set

    fun loadLabelsAsync() {
        scope.launch { loadLabels() }
    }

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

    /**
     * Runs on this ViewModel's own [scope] and awaits the result, so the request and the
     * resulting [uiState] update complete even if the caller's coroutine is cancelled. This is
     * the suspend entry point used by callers that need the created label back directly (e.g.
     * the "create label inline" flow from the Agenda feature, wired up in AppNavHost).
     */
    suspend fun createLabel(name: String, colorHex: String): LabelTag? {
        return scope.async { createLabelAwait(name, colorHex) }.await()
    }

    /**
     * Fire-and-forget variant of [createLabel] for callers (like LabelsScreen) that don't need to
     * suspend for the result. Runs on this ViewModel's own [scope] instead of the caller's, so
     * the request survives even if the caller's coroutine scope (e.g. a screen's
     * `rememberCoroutineScope()`) is cancelled by navigation before it finishes.
     */
    fun createLabel(name: String, colorHex: String, onResult: (LabelTag?) -> Unit) {
        scope.launch {
            onResult(createLabelAwait(name, colorHex))
        }
    }

    private suspend fun createLabelAwait(name: String, colorHex: String): LabelTag? {
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

    /**
     * Runs on this ViewModel's own [scope] and awaits the result, so the request and the
     * resulting [uiState] update complete even if the caller's coroutine is cancelled (e.g. the
     * user navigates away from the Labels tab mid-request).
     */
    suspend fun deleteLabel(label: LabelTag): Boolean {
        return scope.async { deleteLabelAwait(label) }.await()
    }

    private suspend fun deleteLabelAwait(label: LabelTag): Boolean {
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
                    // Recompute from the current uiState.labels here (not before the suspend
                    // call above) so a concurrent mutation that completed while this request
                    // was in flight isn't clobbered by a stale snapshot.
                    uiState = uiState.copy(
                        labels = uiState.labels.filterNot { it.id == label.id },
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
