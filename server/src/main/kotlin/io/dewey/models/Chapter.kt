package io.dewey.models

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class Chapter(
    val title: String?,
    val startMs: Long,
    val endMs: Long,
    val trackId: Uuid,
)
