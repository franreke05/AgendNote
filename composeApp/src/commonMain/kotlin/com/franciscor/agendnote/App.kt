package com.franciscor.agendnote

import com.franciscor.agendnote.Inicio.InicioScreen
import com.franciscor.agendnote.data.AgendaApiClient
import com.franciscor.agendnote.data.AppConfig
import com.franciscor.agendnote.data.SupabaseAgendaRepository
import com.franciscor.agendnote.ui.theme.AgendNoteTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    var isDarkMode by rememberSaveable { mutableStateOf(false) }
    val useRemote = AppConfig.API_BASE_URL.isNotBlank() && AppConfig.APP_SECRET.isNotBlank()
    val repository = remember(useRemote) {
        if (useRemote) SupabaseAgendaRepository(AgendaApiClient(AppConfig.API_BASE_URL, AppConfig.APP_SECRET)) else null
    }
    val scope = rememberCoroutineScope()

    LaunchedEffect(repository) {
        val storedMode = repository?.let { runCatching { it.fetchThemeMode() }.getOrNull() }
        if (storedMode != null) {
            isDarkMode = storedMode
        }
    }

    AgendNoteTheme(isDark = isDarkMode) {
        InicioScreen(
            isDarkMode = isDarkMode,
            onThemeChange = { isDark ->
                isDarkMode = isDark
                if (repository != null) {
                    scope.launch {
                        runCatching { repository.updateThemeMode(isDark) }
                    }
                }
            },
        )
    }
}
