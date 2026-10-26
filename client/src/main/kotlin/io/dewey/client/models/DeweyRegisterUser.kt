package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class DeweyRegisterUser(
    val password: String,
    val username: String,
)
