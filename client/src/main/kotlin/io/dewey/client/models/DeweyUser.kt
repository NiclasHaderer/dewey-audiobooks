package io.dewey.client.models

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class DeweyUser(
    val id: Uuid,
    val username: String,
)
