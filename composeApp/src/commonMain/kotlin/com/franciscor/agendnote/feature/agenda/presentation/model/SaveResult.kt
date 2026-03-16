package com.franciscor.agendnote.feature.agenda.presentation.model

data class SaveResult(
    val success: Boolean,
    val errorMessage: String? = null,
)
