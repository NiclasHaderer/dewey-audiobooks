package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class GenreDetailed(
    val books: List<Book>,
    val name: String,
)
