package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class AccessTokenPermissions(
    val isAdmin: Boolean,
    val libraries: List<AccessTokenLibrary>? = null,
)
