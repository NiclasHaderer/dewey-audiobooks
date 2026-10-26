package io.dewey.client.models

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class ChangeEvent(
    val entity: ChangedEntity,
    val id: Uuid,
    val libraryId: Uuid,
    val type: ChangeType,
)
