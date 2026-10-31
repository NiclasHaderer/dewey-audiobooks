package io.dewey.models

import io.dewey.auth.utils.DeweyPermissions
import kotlinx.serialization.Serializable

@Serializable
data class UserPermissions(
    override val isAdmin: Boolean,
    val libraries: List<LibraryPermissions>,
) : DeweyPermissions
