package io.dewey.auth.models

import kotlinx.serialization.Serializable

@Serializable
open class DeweyChangePassword(
    open val currentPassword: String,
    open val newPassword: String,
)
