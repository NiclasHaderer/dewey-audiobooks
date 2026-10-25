package io.dewey.models

import kotlinx.serialization.Serializable

@Serializable
data class SeriesCreate(
    val title: String,
)
