package io.dewey.auth.models

import kotlinx.serialization.Serializable

@Serializable
open class DeweyJWKs(
    open val keys: List<JWK>,
)
