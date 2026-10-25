package io.dewey.models

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class TitledId(
    val id: Uuid,
    val title: String,
)
