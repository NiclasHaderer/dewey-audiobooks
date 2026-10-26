package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class SetDismissed(
    val dismissed: Boolean,
)
