package io.dewey.client.models

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class NamedId(
    val id: Uuid,
    val name: String,
)
