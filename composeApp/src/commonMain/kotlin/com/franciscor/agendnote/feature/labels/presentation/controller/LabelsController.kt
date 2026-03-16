package com.franciscor.agendnote.feature.labels.presentation.controller

import com.franciscor.agendnote.core.model.LabelTag
import com.franciscor.agendnote.feature.labels.presentation.model.LabelsAction
import com.franciscor.agendnote.feature.labels.presentation.viewmodel.LabelsViewModel

class LabelsController(
    private val viewModel: LabelsViewModel,
) {
    suspend fun handle(action: LabelsAction) {
        when (action) {
            LabelsAction.Load -> viewModel.loadLabels()
            LabelsAction.DismissError -> viewModel.dismissError()
        }
    }

    suspend fun createLabel(name: String, colorHex: String): LabelTag? {
        return viewModel.createLabel(name, colorHex)
    }

    suspend fun deleteLabel(label: LabelTag): Boolean {
        return viewModel.deleteLabel(label)
    }

    suspend fun deleteAllLabels(): Boolean {
        return viewModel.deleteAllLabels()
    }
}
