package io.dewey.models

import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class Track(
    val id: Uuid,
    val title: String,
    val trackNr: Int,
    val durationMs: Long,
    val fileModifiedAt: Instant,
    val book: TitledId,
)
