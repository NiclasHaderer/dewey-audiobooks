package io.dewey.auth.models

import kotlinx.serialization.Serializable

@Serializable
open class DeweyRegisterUser(
    open val username: String,
    open val password: String,
)
