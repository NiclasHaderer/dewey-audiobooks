package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class PaginatedResponse<T>(
    val items: List<T>,
    val limit: Int,
    val offset: Long,
    val total: Long,
)
