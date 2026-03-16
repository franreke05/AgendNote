package com.franciscor.agendnote.feature.labels.data

import com.franciscor.agendnote.core.model.LabelTag
import com.franciscor.agendnote.core.network.AgendaApiClient
import com.franciscor.agendnote.feature.labels.domain.LabelRepository

class SupabaseLabelRepository(
    private val api: AgendaApiClient,
) : LabelRepository {
    override suspend fun fetchLabels(): List<LabelTag> = api.fetchLabels().map { dto ->
        LabelTag(
            id = dto.id,
            name = dto.name,
            colorHex = dto.color_hex,
        )
    }

    override suspend fun createLabel(name: String, colorHex: String): LabelTag {
        val dto = api.createLabel(name, colorHex)
        return LabelTag(
            id = dto.id,
            name = dto.name,
            colorHex = dto.color_hex,
        )
    }

    override suspend fun deleteLabel(id: String): Boolean = api.deleteLabel(id)

    override suspend fun deleteAllLabels(): Boolean = api.deleteAllLabels()
}
