package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class Narrator(
    val bookCount: Int,
    val name: String,
)
