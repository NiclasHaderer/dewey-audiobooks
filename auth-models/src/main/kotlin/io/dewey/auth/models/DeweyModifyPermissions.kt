package io.dewey.auth.models

import kotlinx.serialization.Serializable

@Serializable
open class DeweyModifyPermissions<PERMISSIONS>(
    open val permissions: PERMISSIONS,
)
