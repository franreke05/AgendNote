package com.franciscor.agendnote.feature.agenda.domain

import com.franciscor.agendnote.core.model.LabelTag
import com.franciscor.agendnote.core.model.TaskItem
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Exporta lo que ya está cargado del lado del cliente (mismo alcance y misma limitación que
 * las listas inteligentes - ver SmartLists.kt) como JSON legible. Alcance deliberadamente
 * reducido a "generar el texto"; la entrega (compartir un archivo) se dejó fuera de esta
 * sesión porque requiere código de plataforma sin poder verificar (ver
 * docs/agendnote/FASE7_EVALUACION.md) - el llamador copia este texto al portapapeles.
 */
@Serializable
private data class ExportedTask(
    val date: String,
    val title: String,
    val details: String?,
    val time: String?,
    val is_done: Boolean,
    val labels: List<String>,
)

@Serializable
private data class ExportedLabel(
    val name: String,
    val color_hex: String,
)

@Serializable
private data class ExportPayload(
    val tasks: List<ExportedTask>,
    val labels: List<ExportedLabel>,
)

private val exportJson = Json { prettyPrint = true }

fun buildTaskExportJson(tasksByDate: Map<LocalDate, List<TaskItem>>, labels: List<LabelTag>): String {
    val exportedTasks = tasksByDate.entries
        .sortedBy { it.key }
        .flatMap { (date, tasks) ->
            tasks.map { task ->
                ExportedTask(
                    date = date.toString(),
                    title = task.title,
                    details = task.details,
                    time = task.time?.toString(),
                    is_done = task.isDone,
                    labels = task.labels.map { it.name },
                )
            }
        }
    val exportedLabels = labels.map { ExportedLabel(name = it.name, color_hex = it.colorHex) }
    return exportJson.encodeToString(ExportPayload(exportedTasks, exportedLabels))
}
