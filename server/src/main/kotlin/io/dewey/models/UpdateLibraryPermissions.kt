package io.dewey.models

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class UpdateLibraryPermissions(
    val id: Uuid,
    val permissions: LibraryPermissionLevel,
)
