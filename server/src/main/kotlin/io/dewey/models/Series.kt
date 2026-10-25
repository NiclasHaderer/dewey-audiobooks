package io.dewey.models

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
open class Series(
    val id: Uuid,
    val libraryId: Uuid,
    val authors: List<NamedId>,
    val title: String,
    val provider: String?,
    val providerID: String?,
    val totalBooks: Int?,
    val primaryWorks: Int?,
    val coverID: Uuid?,
    val description: String?,
    val genres: List<String>,
    val bookCoverIDs: List<Uuid>,
)
