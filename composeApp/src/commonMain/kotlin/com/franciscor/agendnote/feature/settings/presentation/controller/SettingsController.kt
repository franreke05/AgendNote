package com.franciscor.agendnote.feature.settings.presentation.controller

import com.franciscor.agendnote.feature.settings.presentation.model.SettingsAction
import com.franciscor.agendnote.feature.settings.presentation.viewmodel.SettingsViewModel

class SettingsController(
    private val viewModel: SettingsViewModel,
) {
    fun handle(action: SettingsAction) {
        when (action) {
            SettingsAction.Load -> viewModel.loadSettings()
            is SettingsAction.SetTheme -> viewModel.setTheme(action.isDark)
            is SettingsAction.RequestBulkAction -> viewModel.requestBulkAction(action.action)
            SettingsAction.DismissBulkAction -> viewModel.dismissBulkAction()
            is SettingsAction.CompleteBulkAction -> viewModel.completeBulkAction(action.success, action.message)
            is SettingsAction.ConfirmBulkAction -> viewModel.confirmBulkAction(action.execute)
            SettingsAction.DismissError -> viewModel.dismissError()
        }
    }
}
