package io.dewey.server.api

import kotlinx.serialization.Serializable

@Serializable
data class ProgressUpdate(
    val positionMs: Long,
)

@Serializable
data class SetFinished(
    val finished: Boolean,
)

@Serializable
data class SetDismissed(
    val dismissed: Boolean,
)
