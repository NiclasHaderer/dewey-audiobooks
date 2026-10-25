package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class YearRange(
    val end: Int,
    val start: Int,
)
