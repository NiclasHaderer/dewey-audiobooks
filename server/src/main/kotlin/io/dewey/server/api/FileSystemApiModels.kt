package io.dewey.server.api

import kotlinx.serialization.Serializable

@Serializable
class FileSystemItem(
    val name: String,
    val path: String,
    val parent: String?,
)
