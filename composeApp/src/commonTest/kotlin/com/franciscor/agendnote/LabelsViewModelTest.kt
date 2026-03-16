package com.franciscor.agendnote

import com.franciscor.agendnote.core.model.LabelTag
import com.franciscor.agendnote.feature.labels.domain.LabelRepository
import com.franciscor.agendnote.feature.labels.presentation.viewmodel.LabelsViewModel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LabelsViewModelTest {
    @Test
    fun `loadLabels fills the ui state`() = runTest {
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
    fun `createLabel appends a new label`() = runTest {
        val repository = FakeLabelRepository(
            createLabelHandler = { name, color -> LabelTag("l-2", name, color) },
        )
        val viewModel = LabelsViewModel(repository)

        viewModel.createLabel("Importante", "#FFAA00")

        assertEquals(listOf(LabelTag("l-2", "Importante", "#FFAA00")), viewModel.uiState.labels)
    }

    @Test
    fun `deleteLabel removes the requested label`() = runTest {
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
    fun `deleteAllLabels clears the state`() = runTest {
        val initial = listOf(LabelTag("l-1", "Uno", "#111111"))
        val repository = FakeLabelRepository(fetchLabelsResult = initial)
        val viewModel = LabelsViewModel(repository)
        viewModel.loadLabels()

        viewModel.deleteAllLabels()

        assertEquals(emptyList(), viewModel.uiState.labels)
        assertNull(viewModel.uiState.errorMessage)
    }

    @Test
    fun `loadLabels without remote repository exposes config error`() = runTest {
        val viewModel = LabelsViewModel(
            repository = null,
            remoteUnavailableMessage = "Falta APP_SECRET",
        )

        viewModel.loadLabels()

        assertEquals("Falta APP_SECRET", viewModel.uiState.errorMessage)
        assertFalse(viewModel.uiState.isRemoteAvailable)
    }

    @Test
    fun `createLabel without remote repository does not append labels`() = runTest {
        val viewModel = LabelsViewModel(
            repository = null,
            remoteUnavailableMessage = "Falta APP_SECRET",
        )

        val created = viewModel.createLabel("Importante", "#FFAA00")

        assertEquals(null, created)
        assertTrue(viewModel.uiState.labels.isEmpty())
        assertEquals("Falta APP_SECRET", viewModel.uiState.errorMessage)
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
