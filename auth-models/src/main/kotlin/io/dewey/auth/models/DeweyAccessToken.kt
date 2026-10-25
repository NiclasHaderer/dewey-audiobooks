package io.dewey.auth.models

import kotlinx.serialization.Serializable

@Serializable
open class DeweyAccessToken(
    open val accessToken: String,
)
