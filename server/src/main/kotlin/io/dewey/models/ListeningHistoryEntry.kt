package io.dewey.models

import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class ListeningHistoryEntry(
    val id: Uuid,
    val book: Book,
    val positionMs: Long,
    val at: Instant,
)
