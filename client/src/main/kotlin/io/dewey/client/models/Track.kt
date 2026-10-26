package io.dewey.client.models

import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class Track(
    val book: TitledId,
    val durationMs: Long,
    val fileModifiedAt: Instant,
    val id: Uuid,
    val title: String,
    val trackNr: Int,
)
