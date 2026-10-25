package io.dewey.models

import kotlinx.serialization.Serializable

@Serializable
data class ChapterMark(
    val title: String?,
    val startMs: Long,
)
