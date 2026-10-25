package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class ApiVersion(
    val version: String,
)
