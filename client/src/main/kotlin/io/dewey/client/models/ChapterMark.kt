package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class ChapterMark(
    val startMs: Long,
    val title: String? = null,
)
