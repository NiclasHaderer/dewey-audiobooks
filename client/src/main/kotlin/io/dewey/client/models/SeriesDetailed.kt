package io.dewey.client.models

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class SeriesDetailed(
    val authors: List<NamedId>,
    val bookCoverIDs: List<Uuid>,
    val books: List<Book>,
    val coverID: Uuid? = null,
    val description: String? = null,
    val genres: List<String>,
    val id: Uuid,
    val libraryId: Uuid,
    val locked: List<SeriesField>,
    val narrators: List<String>,
    val primaryWorks: Int? = null,
    val provider: String? = null,
    val providerID: String? = null,
    val title: String,
    val totalBooks: Int? = null,
    val yearRange: YearRange? = null,
)
