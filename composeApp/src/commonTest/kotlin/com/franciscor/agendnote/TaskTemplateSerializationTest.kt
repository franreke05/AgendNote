package com.franciscor.agendnote

import com.franciscor.agendnote.core.model.TaskTemplate
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regresion: encodeToString/decodeFromString sin el import correcto
 * (kotlinx.serialization.encodeToString/decodeFromString) resuelven al overload de dos
 * argumentos (serializer, value) en vez del reificado de un argumento, y el proyecto no
 * compila con un error confuso ("Cannot infer type for type parameter"). Encontrado en vivo
 * mientras se implementaba SupabaseSettingsRepository.fetchTaskTemplates/saveTaskTemplates.
 */
class TaskTemplateSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a list of templates round-trips through JSON unchanged`() {
        val templates = listOf(
            TaskTemplate(
                id = "t-1",
                name = "Cierre mensual",
                title = "Cerrar el mes",
                details = "Revisar gastos",
                labelIds = listOf("l-1", "l-2"),
                reminderOffsetMinutes = listOf(0L, 60L),
                subtaskTitles = listOf("Revisar facturas", "Enviar informe"),
            ),
            TaskTemplate(id = "t-2", name = "Rutina simple", title = "Estiramientos"),
        )

        val encoded = json.encodeToString(templates)
        val decoded = json.decodeFromString<List<TaskTemplate>>(encoded)

        assertEquals(templates, decoded)
    }

    @Test
    fun `an empty list round-trips as an empty list`() {
        val encoded = json.encodeToString(emptyList<TaskTemplate>())
        val decoded = json.decodeFromString<List<TaskTemplate>>(encoded)

        assertEquals(emptyList(), decoded)
    }
}
