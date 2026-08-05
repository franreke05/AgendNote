package com.franciscor.agendnote

import com.franciscor.agendnote.core.model.LabelTag
import com.franciscor.agendnote.core.model.TaskItem
import com.franciscor.agendnote.feature.agenda.domain.buildTaskExportJson
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskExportTest {
    @Test
    fun `export includes one entry per task with its date and title`() {
        val tasksByDate = mapOf(
            LocalDate(2026, 8, 4) to listOf(
                TaskItem(id = "t-1", title = "Primera", details = null, time = null, labels = emptyList()),
            ),
            LocalDate(2026, 8, 5) to listOf(
                TaskItem(id = "t-2", title = "Segunda", details = null, time = null, labels = emptyList()),
            ),
        )

        val json = buildTaskExportJson(tasksByDate, labels = emptyList())
        val parsed = Json.parseToJsonElement(json).jsonObject
        val tasks = parsed["tasks"]!!.jsonArray

        assertEquals(2, tasks.size)
        val titles = tasks.map { it.jsonObject["title"]!!.jsonPrimitive.content }
        assertTrue(titles.containsAll(listOf("Primera", "Segunda")))
    }

    @Test
    fun `export is valid JSON even with no tasks or labels`() {
        val json = buildTaskExportJson(emptyMap(), emptyList())

        val parsed = Json.parseToJsonElement(json).jsonObject
        assertEquals(0, parsed["tasks"]!!.jsonArray.size)
        assertEquals(0, parsed["labels"]!!.jsonArray.size)
    }

    @Test
    fun `export includes labels by name and color`() {
        val json = buildTaskExportJson(
            emptyMap(),
            labels = listOf(LabelTag(id = "l-1", name = "Urgente", colorHex = "#FF0000")),
        )

        val parsed = Json.parseToJsonElement(json).jsonObject
        val label = parsed["labels"]!!.jsonArray.single().jsonObject
        assertEquals("Urgente", label["name"]!!.jsonPrimitive.content)
        assertEquals("#FF0000", label["color_hex"]!!.jsonPrimitive.content)
    }
}
