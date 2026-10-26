package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class DeweyModifyPermissions<PERMISSIONS>(
    val permissions: PERMISSIONS,
)
