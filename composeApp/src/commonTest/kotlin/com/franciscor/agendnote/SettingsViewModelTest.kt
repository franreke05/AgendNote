package com.franciscor.agendnote

import com.franciscor.agendnote.feature.settings.domain.SettingsRepository
import com.franciscor.agendnote.feature.settings.presentation.viewmodel.SettingsViewModel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsViewModelTest {
    @Test
    fun `loadSettings pulls theme and background into shared state`() = runTest {
        val repository = FakeSettingsRepository(
            background = "https://cdn.example.com/fondo.png",
            theme = true,
        )
        val viewModel = SettingsViewModel(repository, fallbackBackgroundUrl = "")

        viewModel.loadSettings()

        assertTrue(viewModel.uiState.isDarkMode)
        assertEquals("https://cdn.example.com/fondo.png", viewModel.uiState.backgroundUrl)
        assertFalse(viewModel.uiState.isLoading)
    }

    @Test
    fun `setTheme updates the state and persists it`() = runTest {
        val repository = FakeSettingsRepository(background = "", theme = false)
        val viewModel = SettingsViewModel(repository, fallbackBackgroundUrl = "")

        viewModel.setTheme(true)

        assertTrue(viewModel.uiState.isDarkMode)
        assertTrue(repository.savedThemes.contains(true))
        assertNull(viewModel.uiState.errorMessage)
    }

    @Test
    fun `fallback background remains when remote background is missing`() = runTest {
        val viewModel = SettingsViewModel(
            repository = FakeSettingsRepository(background = null, theme = null),
            fallbackBackgroundUrl = "fallback-background",
        )

        viewModel.loadSettings()

        assertEquals("fallback-background", viewModel.uiState.backgroundUrl)
    }
}

private class FakeSettingsRepository(
    private val background: String?,
    private val theme: Boolean?,
) : SettingsRepository {
    val savedThemes = mutableListOf<Boolean>()

    override suspend fun fetchBackgroundUrl(): String? = background

    override suspend fun fetchThemeMode(): Boolean? = theme

    override suspend fun updateThemeMode(isDark: Boolean) {
        savedThemes += isDark
    }
}
