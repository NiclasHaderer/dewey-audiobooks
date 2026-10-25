package io.dewey.models

import kotlinx.serialization.Serializable

@Serializable
data class UserPermissions(
    val isAdmin: Boolean,
    val libraries: List<LibraryPermissions>,
)
