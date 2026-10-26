package io.dewey.models

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class AccessTokenPermissions(
    val isAdmin: Boolean,
    val libraries: List<AccessTokenLibrary>?,
)

@Serializable
data class AccessTokenLibrary(
    val id: Uuid,
    val permissions: LibraryPermissionLevel,
)
