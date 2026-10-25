package io.dewey.models

import kotlinx.serialization.Serializable

@Serializable
data class LibrarySearchResult(
    val books: List<Book>,
    val series: List<Series>,
    val authors: List<Author>,
)
