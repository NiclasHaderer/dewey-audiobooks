package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class LibrarySearchResult(
    val authors: List<Author>,
    val books: List<Book>,
    val series: List<Series>,
)
