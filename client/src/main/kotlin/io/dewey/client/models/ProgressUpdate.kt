package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class ProgressUpdate(
    val positionMs: Long,
)
