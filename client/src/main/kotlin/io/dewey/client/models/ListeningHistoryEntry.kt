package io.dewey.client.models

import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class ListeningHistoryEntry(
    val at: Instant,
    val book: Book,
    val id: Uuid,
    val positionMs: Long,
)
