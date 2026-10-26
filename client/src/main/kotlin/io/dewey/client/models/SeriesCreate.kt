package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class SeriesCreate(
    val title: String,
)
