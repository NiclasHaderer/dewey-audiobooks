package io.dewey.models

import kotlinx.serialization.Serializable

@Serializable
data class Genre(
    val name: String,
    val bookCount: Int,
)

@Serializable
data class GenreDetailed(
    val name: String,
    val books: List<Book>,
)
