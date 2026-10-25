package io.dewey.models

import kotlinx.serialization.Serializable

@Serializable
data class Narrator(
    val name: String,
    val bookCount: Int,
)

@Serializable
data class NarratorDetailed(
    val name: String,
    val books: List<Book>,
)
