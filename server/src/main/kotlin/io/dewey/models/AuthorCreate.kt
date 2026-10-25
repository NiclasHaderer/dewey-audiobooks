package io.dewey.models

import kotlinx.serialization.Serializable

@Serializable
data class AuthorCreate(
    val name: String,
)
