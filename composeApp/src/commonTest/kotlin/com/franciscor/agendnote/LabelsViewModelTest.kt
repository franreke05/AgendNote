package com.franciscor.agendnote

import com.franciscor.agendnote.core.model.LabelTag
import com.franciscor.agendnote.feature.labels.domain.LabelRepository
import com.franciscor.agendnote.feature.labels.presentation.viewmodel.LabelsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class LabelsViewModelTest {
    // LabelsViewModel.createLabel/deleteLabel now hop onto the ViewModel's own CoroutineScope
    // (Dispatchers.Main.immediate) via `scope.async {}.await()`, so Main needs a TestDispatcher
    // installed. Sharing this exact dispatcher with runTest(...) below keeps them on the same
    // virtual-time scheduler, which is required for the suspend calls below to resolve.
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
    fun `loadLabels fills the ui state`() = runTest(testDispatcher) {
        val expected = listOf(LabelTag("l-1", "Trabajo", "#123456"))
        val viewModel = LabelsViewModel(
            repository = FakeLabelRepository(fetchLabelsResult = expected),
        )

        viewModel.loadLabels()

        assertEquals(expected, viewModel.uiState.labels)
        assertFalse(viewModel.uiState.isLoading)
        assertNull(viewModel.uiState.errorMessage)
    }

    @Test
    fun `createLabel appends a new label`() = runTest(testDispatcher) {
        val repository = FakeLabelRepository(
            createLabelHandler = { name, color -> LabelTag("l-2", name, color) },
        )
        val viewModel = LabelsViewModel(repository)

        viewModel.createLabel("Importante", "#FFAA00")

        assertEquals(listOf(LabelTag("l-2", "Importante", "#FFAA00")), viewModel.uiState.labels)
    }

    @Test
    fun `deleteLabel removes the requested label`() = runTest(testDispatcher) {
        val initial = listOf(
            LabelTag("l-1", "Uno", "#111111"),
            LabelTag("l-2", "Dos", "#222222"),
        )
        val repository = FakeLabelRepository(fetchLabelsResult = initial)
        val viewModel = LabelsViewModel(repository)
        viewModel.loadLabels()

        viewModel.deleteLabel(initial.first())

        assertEquals(listOf(initial.last()), viewModel.uiState.labels)
    }

    @Test
    fun `deleteAllLabels clears the state`() = runTest(testDispatcher) {
        val initial = listOf(LabelTag("l-1", "Uno", "#111111"))
        val repository = FakeLabelRepository(fetchLabelsResult = initial)
        val viewModel = LabelsViewModel(repository)
        viewModel.loadLabels()

        viewModel.deleteAllLabels()

        assertEquals(emptyList(), viewModel.uiState.labels)
        assertNull(viewModel.uiState.errorMessage)
    }
}

private class FakeLabelRepository(
    private val fetchLabelsResult: List<LabelTag> = emptyList(),
    private val createLabelHandler: suspend (String, String) -> LabelTag = { name, color ->
        LabelTag("generated", name, color)
    },
) : LabelRepository {
    override suspend fun fetchLabels(): List<LabelTag> = fetchLabelsResult

    override suspend fun createLabel(name: String, colorHex: String): LabelTag {
        return createLabelHandler(name, colorHex)
    }

    override suspend fun deleteLabel(id: String): Boolean = true

    override suspend fun deleteAllLabels(): Boolean = true
}
