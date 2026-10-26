package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class NarratorDetailed(
    val books: List<Book>,
    val name: String,
)
