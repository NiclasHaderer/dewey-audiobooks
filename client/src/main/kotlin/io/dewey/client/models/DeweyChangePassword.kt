package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class DeweyChangePassword(
    val currentPassword: String,
    val newPassword: String,
)
