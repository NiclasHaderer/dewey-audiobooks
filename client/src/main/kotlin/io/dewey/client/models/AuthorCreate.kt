package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class AuthorCreate(
    val name: String,
)
