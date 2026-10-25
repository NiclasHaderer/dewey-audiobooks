package io.dewey.server.plugins

import io.dewey.openapi.serializion.kotlin.OpenApiJson
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation

fun Application.configureSerialization() {
    install(ContentNegotiation) { json(OpenApiJson) }
}
