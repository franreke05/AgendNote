package com.franciscor.agendnote.feature.labels.presentation.model

sealed interface LabelsAction {
    data object Load : LabelsAction

    data object DismissError : LabelsAction
}
