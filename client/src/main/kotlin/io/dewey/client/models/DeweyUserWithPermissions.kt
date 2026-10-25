package io.dewey.client.models

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class DeweyUserWithPermissions<PERMISSIONS : Any>(
    val id: Uuid,
    val permissions: PERMISSIONS,
    val username: String,
)
