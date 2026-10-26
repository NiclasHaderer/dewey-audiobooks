package io.dewey.client.models

import kotlinx.serialization.Serializable

@Serializable
data class ThirdPartyLicense(
    val license: String,
    val licenseUrl: String? = null,
    val name: String,
    val repository: String? = null,
    val text: String? = null,
    val version: String,
)
