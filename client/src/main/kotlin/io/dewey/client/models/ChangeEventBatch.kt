package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class ChangeEventBatch(
    val events: List<ChangeEvent>,
    val missedPreviousEvents: Boolean,
)
