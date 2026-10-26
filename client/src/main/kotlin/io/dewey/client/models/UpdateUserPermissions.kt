package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class UpdateUserPermissions(
    val isAdmin: Boolean,
    val libraries: List<UpdateLibraryPermissions>,
)
