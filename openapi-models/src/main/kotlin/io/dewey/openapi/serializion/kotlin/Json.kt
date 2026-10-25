package io.dewey.openapi.serializion.kotlin

import kotlinx.serialization.json.Json

// The schema marks every key but a Patch as required, so every default is written
val OpenApiJson =
    Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
