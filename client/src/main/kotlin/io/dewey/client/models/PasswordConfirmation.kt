package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class PasswordConfirmation(
    val password: String,
)
