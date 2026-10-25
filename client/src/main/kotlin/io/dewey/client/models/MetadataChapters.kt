package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class MetadataChapters(
    val chapters: List<ChapterMark>,
    val runtimeMs: Long,
)
