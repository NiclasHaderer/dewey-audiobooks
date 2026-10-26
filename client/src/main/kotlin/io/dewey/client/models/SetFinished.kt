package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class SetFinished(
    val finished: Boolean,
)
