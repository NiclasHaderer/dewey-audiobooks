package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class DeweyLoginUser(
    val password: String,
    val username: String,
)
