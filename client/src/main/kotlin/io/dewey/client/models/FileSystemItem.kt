package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class FileSystemItem(
    val name: String,
    val parent: String? = null,
    val path: String,
)
