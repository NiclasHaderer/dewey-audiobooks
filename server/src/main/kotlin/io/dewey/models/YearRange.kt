package io.dewey.models

import kotlinx.serialization.Serializable

@Serializable
data class YearRange(
    val start: Int,
    val end: Int,
)
