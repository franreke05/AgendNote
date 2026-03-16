package com.franciscor.agendnote.feature.labels.domain

import com.franciscor.agendnote.core.model.LabelTag

interface LabelRepository {
    suspend fun fetchLabels(): List<LabelTag>

    suspend fun createLabel(name: String, colorHex: String): LabelTag

    suspend fun deleteLabel(id: String): Boolean

    suspend fun deleteAllLabels(): Boolean
}
