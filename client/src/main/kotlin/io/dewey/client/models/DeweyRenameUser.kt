package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class DeweyRenameUser(
    val username: String,
)
