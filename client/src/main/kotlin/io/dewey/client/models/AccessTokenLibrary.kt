package io.dewey.client.models

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class AccessTokenLibrary(
    val id: Uuid,
    val permissions: LibraryPermissionLevel,
)
