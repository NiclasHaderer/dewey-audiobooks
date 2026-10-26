package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class Genre(
    val bookCount: Int,
    val name: String,
)
