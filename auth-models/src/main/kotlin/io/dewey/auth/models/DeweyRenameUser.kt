package io.dewey.auth.models

import kotlinx.serialization.Serializable

@Serializable
open class DeweyRenameUser(
    open val username: String,
)
