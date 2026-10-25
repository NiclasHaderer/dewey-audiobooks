package io.dewey.models

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class User(
    val id: Uuid,
    val username: String,
    val admin: Boolean,
)
