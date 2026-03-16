package com.franciscor.agendnote.feature.labels.presentation.model

import com.franciscor.agendnote.core.model.LabelTag

data class LabelsUiState(
    val labels: List<LabelTag> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isRemoteAvailable: Boolean = true,
)
