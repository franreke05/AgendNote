package com.franciscor.agendnote.feature.agenda.presentation.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.franciscor.agendnote.core.ui.layout.AppLayout
import com.franciscor.agendnote.core.ui.theme.GlassTheme
import com.franciscor.agendnote.feature.agenda.presentation.controller.AgendaController
import com.franciscor.agendnote.feature.agenda.presentation.model.AgendaAction
import com.franciscor.agendnote.feature.agenda.presentation.viewmodel.AgendaViewModel
import kotlinx.datetime.LocalDate

@Composable
fun CalendarScreen(
    viewModel: AgendaViewModel,
    controller: AgendaController,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = AppLayout.metrics
    val contentInset = layout.width(24.dp, 20.dp)
    val uiState = viewModel.uiState

    LaunchedEffect(Unit) {
        controller.handleAsync(AgendaAction.LoadMonth(uiState.visibleMonth))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                horizontal = contentInset,
                vertical = layout.height(12.dp, 10.dp),
            ),
        verticalArrangement = Arrangement.spacedBy(layout.height(12.dp, 10.dp)),
    ) {
        if (uiState.monthErrorMessage != null) {
            Text(
                text = uiState.monthErrorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = GlassTheme.tokens.error,
            )
        } else if (uiState.isMonthLoading) {
            Text(
                text = "Cargando...",
                style = MaterialTheme.typography.bodyMedium,
                color = GlassTheme.tokens.textSecondary,
            )
        }
        CalendarMonthView(
            selectedDate = uiState.selectedDate,
            visibleMonth = uiState.visibleMonth,
            tasksByDate = uiState.tasksByDate,
            onSelectDate = onSelectDate,
            onVisibleMonthChange = { month ->
                controller.handleAsync(AgendaAction.LoadMonth(month))
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
