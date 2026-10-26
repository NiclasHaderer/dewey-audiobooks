package io.dewey.client.models

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class Chapter(
    val endMs: Long,
    val startMs: Long,
    val title: String? = null,
    val trackId: Uuid,
)
