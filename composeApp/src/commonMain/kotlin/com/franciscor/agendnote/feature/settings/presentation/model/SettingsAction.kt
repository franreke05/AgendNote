package com.franciscor.agendnote.feature.settings.presentation.model

sealed interface SettingsAction {
    data object Load : SettingsAction

    data class SetTheme(val isDark: Boolean) : SettingsAction

    data class RequestBulkAction(val action: SettingsBulkAction) : SettingsAction

    data object DismissBulkAction : SettingsAction

    data class CompleteBulkAction(
        val success: Boolean,
        val message: String? = null,
    ) : SettingsAction

    class ConfirmBulkAction(val execute: suspend () -> Boolean) : SettingsAction

    data object DismissError : SettingsAction
}
