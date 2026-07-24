package com.franciscor.agendnote

import com.franciscor.agendnote.feature.settings.domain.SettingsRepository
import com.franciscor.agendnote.feature.settings.presentation.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsViewModelTest {
    // SettingsViewModel.loadSettings/setTheme are now fire-and-forget: they launch on the
    // ViewModel's own CoroutineScope (Dispatchers.Main.immediate) instead of suspending the
    // caller. Main needs a TestDispatcher installed so that scope can dispatch at all, and it
    // must be the same instance passed to runTest(...) below so advanceUntilIdle() below can
    // actually drive it.
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadSettings pulls theme and background into shared state`() = runTest(testDispatcher) {
        val repository = FakeSettingsRepository(
            background = "https://cdn.example.com/fondo.png",
            theme = true,
        )
        val viewModel = SettingsViewModel(repository, fallbackBackgroundUrl = "")

        viewModel.loadSettings()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.isDarkMode)
        assertEquals("https://cdn.example.com/fondo.png", viewModel.uiState.backgroundUrl)
        assertFalse(viewModel.uiState.isLoading)
    }

    @Test
    fun `setTheme updates the state and persists it`() = runTest(testDispatcher) {
        val repository = FakeSettingsRepository(background = "", theme = false)
        val viewModel = SettingsViewModel(repository, fallbackBackgroundUrl = "")

        viewModel.setTheme(true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.isDarkMode)
        assertTrue(repository.savedThemes.contains(true))
        assertNull(viewModel.uiState.errorMessage)
    }

    @Test
    fun `fallback background remains when remote background is missing`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(
            repository = FakeSettingsRepository(background = null, theme = null),
            fallbackBackgroundUrl = "fallback-background",
        )

        viewModel.loadSettings()
        advanceUntilIdle()

        assertEquals("fallback-background", viewModel.uiState.backgroundUrl)
    }

    @Test
    fun `loadSettings without remote repository exposes config error`() = runTest {
        val viewModel = SettingsViewModel(
            repository = null,
            fallbackBackgroundUrl = "",
            remoteUnavailableMessage = "Falta APP_SECRET",
        )

        viewModel.loadSettings()

        assertEquals("Falta APP_SECRET", viewModel.uiState.errorMessage)
        assertFalse(viewModel.uiState.isRemoteAvailable)
    }

    @Test
    fun `setTheme without remote repository does not mutate theme`() = runTest {
        val viewModel = SettingsViewModel(
            repository = null,
            fallbackBackgroundUrl = "",
            remoteUnavailableMessage = "Falta APP_SECRET",
        )

        viewModel.setTheme(true)

        assertFalse(viewModel.uiState.isDarkMode)
        assertEquals("Falta APP_SECRET", viewModel.uiState.errorMessage)
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
