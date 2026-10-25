package io.dewey.auth.models

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

// User models which will be returned by the auth api

@Serializable
open class DeweyUser(
    open val id: Uuid,
    open val username: String,
)

@Serializable
open class DeweyUserWithPermissions<PERMISSIONS : Any>(
    open val id: Uuid,
    open val username: String,
    open val permissions: PERMISSIONS,
)
