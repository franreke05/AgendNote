package com.franciscor.agendnote

import com.franciscor.agendnote.core.platform.ThemeModeStore
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
        val viewModel = SettingsViewModel(
            repository,
            fallbackBackgroundUrl = "",
            themeModeStore = FakeThemeModeStore(),
        )

        viewModel.loadSettings()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.isDarkMode)
        assertEquals("https://cdn.example.com/fondo.png", viewModel.uiState.backgroundUrl)
        assertFalse(viewModel.uiState.isLoading)
    }

    @Test
    fun `local theme choice wins over stale remote value`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(
            repository = FakeSettingsRepository(background = "", theme = true),
            fallbackBackgroundUrl = "",
            themeModeStore = FakeThemeModeStore(value = false),
        )

        viewModel.loadSettings()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.isDarkMode)
    }

    @Test
    fun `setTheme updates the state and persists it`() = runTest(testDispatcher) {
        val repository = FakeSettingsRepository(background = "", theme = false)
        val themeStore = FakeThemeModeStore()
        val viewModel = SettingsViewModel(
            repository,
            fallbackBackgroundUrl = "",
            themeModeStore = themeStore,
        )

        viewModel.setTheme(true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.isDarkMode)
        assertTrue(repository.savedThemes.contains(true))
        assertEquals(true, themeStore.value)
        assertNull(viewModel.uiState.errorMessage)
    }

    @Test
    fun `setTheme keeps local choice when remote persistence fails`() = runTest(testDispatcher) {
        val repository = FakeSettingsRepository(
            background = "",
            theme = false,
            failThemeSave = true,
        )
        val viewModel = SettingsViewModel(
            repository,
            fallbackBackgroundUrl = "",
            themeModeStore = FakeThemeModeStore(),
        )

        viewModel.setTheme(true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.isDarkMode)
        assertEquals("Tema guardado en este dispositivo; no se pudo sincronizar", viewModel.uiState.errorMessage)
    }

    @Test
    fun `setBackgroundUrl rolls back optimistic state when persistence fails`() = runTest(testDispatcher) {
        val repository = FakeSettingsRepository(
            background = "https://cdn.example.com/original.png",
            theme = false,
            failBackgroundSave = true,
        )
        val viewModel = SettingsViewModel(
            repository = repository,
            fallbackBackgroundUrl = "https://cdn.example.com/original.png",
        )

        viewModel.setBackgroundUrl("https://cdn.example.com/new.png")
        advanceUntilIdle()

        assertEquals("https://cdn.example.com/original.png", viewModel.uiState.backgroundUrl)
        assertEquals("No se pudo guardar el fondo", viewModel.uiState.errorMessage)
    }

    @Test
    fun `fallback background remains when remote background is missing`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(
            repository = FakeSettingsRepository(background = null, theme = null),
            fallbackBackgroundUrl = "fallback-background",
            themeModeStore = FakeThemeModeStore(),
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
    fun `setTheme without remote repository persists locally`() = runTest(testDispatcher) {
        val themeStore = FakeThemeModeStore()
        val viewModel = SettingsViewModel(
            repository = null,
            fallbackBackgroundUrl = "",
            remoteUnavailableMessage = "Falta APP_SECRET",
            themeModeStore = themeStore,
        )

        viewModel.setTheme(true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.isDarkMode)
        assertEquals(true, themeStore.value)
        assertEquals("Falta APP_SECRET", viewModel.uiState.errorMessage)
    }
}

private class FakeThemeModeStore(
    var value: Boolean? = null,
) : ThemeModeStore {
    override fun read(): Boolean? = value

    override fun write(isDark: Boolean) {
        value = isDark
    }
}

private class FakeSettingsRepository(
    private val background: String?,
    private val theme: Boolean?,
    private val failThemeSave: Boolean = false,
    private val failBackgroundSave: Boolean = false,
) : SettingsRepository {
    val savedThemes = mutableListOf<Boolean>()
    val savedBackgrounds = mutableListOf<String>()

    override suspend fun fetchBackgroundUrl(): String? = background

    override suspend fun fetchThemeMode(): Boolean? = theme

    override suspend fun updateBackgroundUrl(url: String) {
        if (failBackgroundSave) error("background save failed")
        savedBackgrounds += url
    }

    override suspend fun updateThemeMode(isDark: Boolean) {
        if (failThemeSave) error("theme save failed")
        savedThemes += isDark
    }

    override suspend fun fetchTaskTemplates(): List<com.franciscor.agendnote.core.model.TaskTemplate> = emptyList()

    override suspend fun saveTaskTemplates(
        templates: List<com.franciscor.agendnote.core.model.TaskTemplate>,
    ): Boolean = true

}
