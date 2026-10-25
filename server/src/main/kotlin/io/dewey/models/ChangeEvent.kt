package io.dewey.models

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

enum class ChangedEntity {
    LIBRARY,
    BOOK,
    AUTHOR,
    SERIES,
}

enum class ChangeType {
    CREATED,
    UPDATED,
    DELETED,
}

@Serializable
data class ChangeEvent(
    val entity: ChangedEntity,
    val type: ChangeType,
    val id: Uuid,
    val libraryId: Uuid,
)

@Serializable
data class ChangeEventBatch(
    val events: List<ChangeEvent>,
    val missedPreviousEvents: Boolean = false,
)
